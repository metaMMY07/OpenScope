package dev.mediasearch.douyin

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.mediasearch.core.*
import dev.mediasearch.session.evaluatePage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

/** Experimental public-page route; no private signer or automated challenge handling. */
class DouyinPageClient(private val context: Context) {
    private val lock = Mutex()
    private var view: WebView? = null
    private var query = ""
    private var loaded = false
    private var failure: PlatformException? = null
    private val delivered = mutableSetOf<String>()

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = BrowserProfile.userAgent(Platform.DOUYIN, WebSettings.getDefaultUserAgent(context))
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val version = Regex("Chrome/([0-9.]+)").find(settings.userAgentString)?.groupValues?.get(1)
            val metadata = UserAgentMetadata.Builder().setMobile(false).setPlatform("Linux").setPlatformVersion("")
                .setArchitecture("x86").setBitness(64).setModel("").setWow64(false)
            if (version != null) metadata.setFullVersion(version).setBrandVersionList(listOf("Chromium", "Google Chrome").map { brand ->
                UserAgentMetadata.BrandVersion.Builder().setBrand(brand).setMajorVersion(version.substringBefore('.')).setFullVersion(version).build()
            })
            WebSettingsCompat.setUserAgentMetadata(settings, metadata.build())
        }
        val viewport = context.assets.open("browser/desktop-viewport.js").bufferedReader().use { it.readText() }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, viewport, setOf("https://www.douyin.com"))
        }
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
        measure(android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY))
        layout(0, 0, 1200, 900)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String?) { loaded = true }
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return request.url.scheme != "https"
                val target = BrowserProfile.secureNavigationUrl(Platform.DOUYIN, request.url.toString())
                if (target == null) { failure = PlatformException(FailureKind.CHALLENGE, "抖音要求在官方网页继续验证"); return true }
                if (target != request.url.toString()) { v.loadUrl(target); return true }
                return false
            }
            override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); failure = PlatformException(FailureKind.NETWORK, "抖音网页证书验证失败")
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) failure = PlatformException(FailureKind.NETWORK, "抖音网页加载失败")
            }
            override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, response: WebResourceResponse) {
                if (r.isForMainFrame) failure = PlatformException(if (response.statusCode == 429) FailureKind.RATE_LIMITED else FailureKind.CHALLENGE, "抖音网页暂不可用，可在官方页面查看")
            }
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                failure = PlatformException(FailureKind.NETWORK, "抖音网页进程结束，请重新打开")
                v.destroy(); if (view === v) view = null
                return true
            }
        }
    }

    suspend fun search(term: String, page: Int): SearchPage = lock.withLock {
        withContext(Dispatchers.Main) {
            try {
                withTimeout(14_000) {
                    if (page == 1 || term != query || view == null) {
                        close(); query = term; loaded = false; failure = null; delivered.clear()
                        view = create()
                        view!!.loadUrl(searchUrl(term))
                    } else evaluatePage(view!!, "window.scrollTo(0,document.documentElement.scrollHeight)")
                    val script = context.assets.open("browser/douyin-results.js").bufferedReader().use { it.readText() }
                    while (true) {
                        failure?.let { throw it }
                        val web = view ?: throw PlatformException(FailureKind.NETWORK, "抖音网页已关闭")
                        if (Uri.parse(web.url.orEmpty()).lastPathSegment == term) {
                            val value = JSONTokener(evaluatePage(web, script)).nextValue()
                            val data = if (value is String) JSONObject(value) else value as? JSONObject ?: JSONObject()
                            if (data.optBoolean("challenge")) throw PlatformException(FailureKind.CHALLENGE, "疑似平台风控，请在抖音官方网页完成验证")
                            if (data.optBoolean("loginVisible")) throw PlatformException(FailureKind.LOGIN_REQUIRED, "请先在抖音官方网页完成登录")
                            val batch = parse(data).filterNot { it.id in delivered }
                            if (batch.isNotEmpty()) {
                                delivered.addAll(batch.map { it.id })
                                return@withTimeout SearchPage(batch, data.optBoolean("hasMore", true), page)
                            }
                            if (data.optBoolean("empty") || !data.optBoolean("hasMore", true)) return@withTimeout SearchPage(emptyList(), false, page)
                        }
                        delay(600)
                    }
                    @Suppress("UNREACHABLE_CODE") error("unreachable")
                }
            } catch (e: TimeoutCancellationException) {
                close()
                throw PlatformException(FailureKind.UNSUPPORTED, "抖音实验性读取未取得结果，可在 App 内官方网页继续搜索")
            } catch (e: CancellationException) { close(); throw e }
            catch (e: Exception) { close(); throw e }
        }
    }

    fun close() { view?.stopLoading(); view?.destroy(); view = null }

    companion object {
        fun searchUrl(term: String) = "https://www.douyin.com/search/${URLEncoder.encode(term, "UTF-8").replace("+", "%20")}?type=video"
        internal fun parse(data: JSONObject): List<SearchItem> {
            val items = data.optJSONArray("items") ?: return emptyList()
            return (0 until items.length()).mapNotNull { index ->
                val row = items.optJSONObject(index) ?: return@mapNotNull null
                val url = row.optString("url")
                if (!BrowserProfile.allowed(Platform.DOUYIN, url)) return@mapNotNull null
                val id = Regex("/video/([0-9]{8,30})/?$").find(java.net.URI(url).path)?.groupValues?.get(1) ?: return@mapNotNull null
                val title = row.optString("title").trim()
                if (title.isEmpty()) return@mapNotNull null
                val metric = row.optString("metric")
                val likes = displayCount(metric)
                SearchItem(id, Platform.DOUYIN, title, row.optString("author"), "", url,
                    row.optString("thumbnail"), metric, engagement = likes,
                    metricCounts = likes?.let { mapOf("likes" to it) }.orEmpty())
            }.distinctBy { it.id }
        }
    }
}
