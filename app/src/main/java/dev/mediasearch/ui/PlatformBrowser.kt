package dev.mediasearch.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.mediasearch.SearchViewModel
import dev.mediasearch.core.*
import dev.mediasearch.session.SessionStatus
import dev.mediasearch.session.PageSessionProbe
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.*

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformBrowser(platform: Platform, initialUrl: String, login: Boolean, model: SearchViewModel,
                    onClose: () -> Unit, onVerified: () -> Unit) {
    val preferences = LocalThemePreferences.current
    // Login keeps the platform-tested profile; the setting changes the content page viewport only.
    val wideLayout = if (login) BrowserProfile.desktop(platform) else preferences.desktopWebPages
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    val pageUrl = remember(platform, initialUrl) { BrowserProfile.visiblePageUrl(platform, initialUrl, wideLayout) }
    var host by remember { mutableStateOf(Uri.parse(pageUrl).host.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loginMessage by remember { mutableStateOf(if (BrowserProfile.desktop(platform)) "请使用已有账号的手机号或密码登录；可双指缩放页面" else "请使用已有账号，在平台官方页面登录") }
    var pageFinished by remember { mutableIntStateOf(0) }
    var loginRequested by remember { mutableStateOf(login && BrowserProfile.desktop(platform)) }
    val scope = rememberCoroutineScope()
    val currentOnVerified by rememberUpdatedState(onVerified)

    fun finish() {
        // Leave immediately; account refresh must survive disposal of this browser composable.
        model.persistSessions()
        model.refreshAccounts()
        onClose()
    }
    fun back() { val view = webView; if (view?.canGoBack() == true) view.goBack() else finish() }
    BackHandler { back() }

    LaunchedEffect(pageFinished, loginRequested) {
        if (!loginRequested || pageFinished == 0) return@LaunchedEffect
        // Open the official dialog only. Never submit it or read credential values.
        val selector = when (platform) {
            Platform.BILIBILI -> ".header-login-entry"
            Platform.DOUYIN -> "[data-e2e=top-login-button]"
            else -> ".side-bar-component button.login-btn"
        }
        repeat(8) {
            val view = webView ?: return@LaunchedEffect
            if (!BrowserProfile.allowed(platform, view.url.orEmpty())) return@LaunchedEffect
            val opened = suspendCancellableCoroutine<Boolean> { continuation ->
                view.evaluateJavascript("""(() => {
                    const visible = e => e.getBoundingClientRect().width > 0 && e.getBoundingClientRect().height > 0;
                    if ([...document.querySelectorAll('input[placeholder*=手机号],input[type=password]')].some(visible)) return true;
                    const button = [...document.querySelectorAll('$selector')].find(visible);
                    if (!button) return false;
                    button.click(); return true;
                })()""") { result -> if (continuation.isActive) continuation.resume(result == "true", onCancellation = { _, _, _ -> }) }
            }
            if (opened) { loginRequested = false; return@LaunchedEffect }
            delay(600)
        }
        loginRequested = false
    }

    LaunchedEffect(login, platform) {
        var attemptedCookie: String? = null
        while (isActive) {
            delay(1500)
            if (pageFinished == 0) continue
            try {
                val captured = model.sessions.scan(platform)
                val pageAuth = webView?.let { PageSessionProbe.authenticated(it, platform) }
                val cookie = model.sessions.cookies(platform.cookieUrl)
                val verified = if (pageAuth != null) pageAuth else if (captured && cookie != attemptedCookie) {
                    attemptedCookie = cookie
                    model.verifyLogin(platform)
                } else model.sessions.statuses.value[platform] == SessionStatus.VERIFIED
                if (verified && captured) {
                    model.sessions.mark(platform, SessionStatus.VERIFIED)
                    model.sessions.persist()
                    loginMessage = "已登录"
                    if (login) { delay(500); currentOnVerified(); break }
                } else {
                    if (pageAuth == false && (login || wideLayout)) model.sessions.mark(platform, SessionStatus.MISSING)
                    loginMessage = if (platform == Platform.DOUYIN && captured) "会话已保存，可关闭页面后尝试搜索；登录状态尚待确认" else "请在官方页面完成登录"
                }
            } catch (e: TimeoutCancellationException) {
                loginMessage = "验证超时；会话已保留，可返回后重试搜索"
            } catch (e: CancellationException) { throw e
            } catch (e: PlatformException) { loginMessage = e.message
            } catch (e: Exception) { loginMessage = "账号验证未完成，可返回后重试搜索" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Column {
                Text(if (login) "登录${platform.label}" else platform.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$host · ${if (login) "官方登录" else if (wideLayout) "平板适配" else "手机适配"}", style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } },
                navigationIcon = { TextButton(onClick = { back() }) { Text("返回") } },
                actions = {
                    TextButton(onClick = { finish() }) { Text("关闭") }
                    if (BrowserProfile.desktop(platform)) TextButton(onClick = { loginRequested = true }) { Text("登录") }
                    TextButton(onClick = { error = null; webView?.reload() }) { Text("刷新") }
                })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            if (login) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(loginMessage, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
            }
            error?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(message, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodySmall)
            } }
            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                WebView(context).apply {
                    // A wrap-content WebView can resolve CSS 100vh to zero inside AndroidView.
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
                    settings.userAgentString = if (login) BrowserProfile.userAgent(platform, defaultUserAgent)
                        else BrowserProfile.visibleUserAgent(defaultUserAgent, wideLayout)
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = wideLayout
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.textZoom = if (wideLayout) 100 else 115
                    val viewportAsset = if (wideLayout) "browser/desktop-viewport.js" else "browser/phone-viewport.js"
                    val viewportScript = context.assets.open(viewportAsset).bufferedReader().use { it.readText() }
                    val startScriptSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                    if (viewportScript != null && startScriptSupported) {
                        val root = when (platform) {
                            Platform.BILIBILI -> "bilibili.com"
                            Platform.ZHIHU -> "zhihu.com"
                            Platform.XHS -> "xiaohongshu.com"
                            Platform.DOUYIN -> "douyin.com"
                        }
                        WebViewCompat.addDocumentStartJavaScript(this, viewportScript, setOf("https://$root", "https://*.$root"))
                    }
                    if ((!login || BrowserProfile.desktop(platform)) && WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                        // Keep Chromium's installed version while requesting the desktop presentation.
                        val version = Regex("Chrome/([0-9.]+)").find(settings.userAgentString)?.groupValues?.get(1)
                        val metadata = UserAgentMetadata.Builder()
                            .setMobile(false).setPlatform("Linux").setPlatformVersion("")
                            .setArchitecture("x86").setBitness(64).setModel("").setWow64(false)
                        if (version != null) {
                            metadata.setFullVersion(version).setBrandVersionList(listOf(
                                UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium")
                                    .setMajorVersion(version.substringBefore('.')).setFullVersion(version).build(),
                                UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome")
                                    .setMajorVersion(version.substringBefore('.')).setFullVersion(version).build()
                            ))
                        }
                        WebSettingsCompat.setUserAgentMetadata(settings, metadata.build())
                    }
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    settings.mediaPlaybackRequiresUserGesture = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, login)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress / 100f }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            // Embedded HTTPS authentication frames use the browser's normal origin isolation.
                            // Only top-level navigation is restricted to this platform; there is no native bridge.
                            if (!request.isForMainFrame) return request.url.scheme != "https"
                            val targetUrl = BrowserProfile.secureVisibleNavigationUrl(platform, request.url.toString(), wideLayout)
                            if (targetUrl == null) {
                                if (dev.mediasearch.BuildConfig.DEBUG) android.util.Log.d("PageNavigation", "blocked ${request.url.scheme}://${request.url.host}${request.url.path}")
                                if (request.isForMainFrame) error = "该链接无法在此页面打开，请返回继续浏览"
                                return true
                            }
                            if (targetUrl != request.url.toString()) {
                                view.loadUrl(targetUrl)
                                return true
                            }
                            return false
                        }
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            host = url?.let { Uri.parse(it).host }.orEmpty()
                            error = null
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (viewportScript != null && !startScriptSupported && BrowserProfile.allowed(platform, url.orEmpty())) {
                                view.evaluateJavascript(viewportScript, null)
                            }
                            pageFinished++
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) {
                            if (request.isForMainFrame) error = "网页加载失败，请检查网络后刷新"
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) error = "平台返回 HTTP ${response.statusCode}，可稍后刷新"
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, e: SslError) {
                            handler.cancel()
                            error = "无法验证站点证书，已停止连接"
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            error = "网页进程已结束，请返回后重新打开"
                            view.destroy()
                            webView = null
                            return true
                        }
                    }
                    if (allowed(platform, Uri.parse(pageUrl))) loadUrl(pageUrl)
                    else error = "不支持的来源链接"
                }
            }, onRelease = { view -> webView = null; view.stopLoading(); view.destroy() })
        }
    }
}

private fun allowed(platform: Platform, uri: Uri): Boolean {
    return BrowserProfile.allowed(platform, uri.toString())
}
