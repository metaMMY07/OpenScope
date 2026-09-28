package dev.mediasearch

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.webkit.WebView
import dev.mediasearch.ui.BrowserImageSaver
import dev.mediasearch.ui.XhsImageDownloadBridge
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Offline WebView test of the exact Blob -> document-start bridge -> MediaStore route. */
class XhsImageDownloadProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { start() }

    override fun onStart() {
        val report = JSONObject()
        val image = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.RED)
                compress(Bitmap.CompressFormat.PNG, 100, output)
                recycle()
            }
            output.toByteArray()
        }
        val latch = CountDownLatch(1)
        var view: WebView? = null
        try {
            runOnMainSync {
                view = WebView(targetContext).apply {
                    settings.javaScriptEnabled = true
                    val script = targetContext.assets.open("browser/xhs-image-download.js")
                        .bufferedReader().use { it.readText() }
                    val installed = XhsImageDownloadBridge.install(this, script,
                        onImage = { received ->
                            Thread {
                                try {
                                    report.put("bytes_match", image.contentEquals(received))
                                    val uri = runBlocking { BrowserImageSaver.save(targetContext, received) }
                                    val stored = targetContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    report.put("album_bytes_match", image.contentEquals(stored))
                                    // The probe removes only its own specifically created image.
                                    targetContext.contentResolver.delete(uri, null, null)
                                } catch (error: Exception) {
                                    report.put("error", error.toString())
                                } finally { latch.countDown() }
                            }.start()
                        },
                        onError = { report.put("error", "page image conversion failed"); latch.countDown() })
                    report.put("bridge_installed", installed)
                    if (installed) {
                        val base64 = Base64.encodeToString(image, Base64.NO_WRAP)
                        val html = """<!doctype html><script>
                            const data = Uint8Array.from(atob('$base64'), c => c.charCodeAt(0));
                            const blob = new Blob([data], {type:'image/png'});
                            const link = document.createElement('a');
                            link.href = URL.createObjectURL(blob);
                            link.download = 'probe.png';
                            link.click();
                            URL.revokeObjectURL(link.href);
                        </script>"""
                        loadDataWithBaseURL("https://www.xiaohongshu.com/explore", html,
                            "text/html", "UTF-8", null)
                    } else latch.countDown()
                }
            }
            report.put("message_received", latch.await(15, TimeUnit.SECONDS))
        } catch (error: Exception) {
            report.put("error", error.toString())
        } finally {
            runOnMainSync { view?.destroy() }
        }
        val passed = report.optBoolean("bridge_installed") && report.optBoolean("message_received") &&
            report.optBoolean("bytes_match") && report.optBoolean("album_bytes_match")
        report.put("probe_completed", passed)
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("report", report.toString()) })
    }
}
