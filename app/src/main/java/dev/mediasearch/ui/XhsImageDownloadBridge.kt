package dev.mediasearch.ui

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** Narrow, main-frame-only bridge for the XHS page's temporary image Blob download. */
internal object XhsImageDownloadBridge {
    fun install(view: WebView, script: String, onImage: (ByteArray) -> Unit, onError: () -> Unit): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)) return false

        var expectedImageBytes = 0
        WebViewCompat.addWebMessageListener(view, "OpenScopeImageSaver",
            setOf("https://www.xiaohongshu.com")) { sourceView, message, origin, isMainFrame, _ ->
            if (sourceView !== view || !isMainFrame || origin.scheme != "https" ||
                origin.host != "www.xiaohongshu.com" || origin.port !in setOf(-1, 443)) {
                expectedImageBytes = 0
                return@addWebMessageListener
            }
            when (message.type) {
                WebMessageCompat.TYPE_STRING -> {
                    val event = runCatching { JSONObject(message.data ?: "") }.getOrNull()
                    expectedImageBytes = if (event?.optString("type") == "start")
                        event.optInt("size").takeIf { it in 1..BrowserImageSaver.MAX_BYTES } ?: 0
                    else 0
                    if (event?.optString("type") == "error") onError()
                }
                WebMessageCompat.TYPE_ARRAY_BUFFER -> {
                    val bytes = message.arrayBuffer
                    val valid = expectedImageBytes > 0 && bytes.size == expectedImageBytes
                    expectedImageBytes = 0
                    if (valid) onImage(bytes)
                }
            }
        }
        WebViewCompat.addDocumentStartJavaScript(view, script, setOf("https://www.xiaohongshu.com"))
        return true
    }
}
