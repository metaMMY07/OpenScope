package dev.mediasearch

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import dev.mediasearch.core.*
import dev.mediasearch.library.LocalLibrary
import dev.mediasearch.network.CronetTransport
import dev.mediasearch.session.SessionStore
import dev.mediasearch.trending.TrendingClient
import dev.mediasearch.douyin.DouyinPageClient
import kotlinx.coroutines.*
import org.json.JSONObject

/** Development-only fixture database; never modifies the user's library or sends credentials. */
class ParityProbe : Instrumentation() {
    private var live = false
    override fun onCreate(arguments: Bundle?) { live = arguments?.getString("live") == "true"; start() }
    override fun onStart() {
        val report = JSONObject()
        runBlocking {
            var library: LocalLibrary? = null
            var transport: CronetTransport? = null
            var douyin: DouyinPageClient? = null
            try {
                val name = "parity-probe-${System.currentTimeMillis()}.db"
                val store = LocalLibrary(targetContext, name)
                library = store
                val entry = SearchItem("fixture", Platform.BILIBILI, "离线测试内容", "测试作者", "", "https://www.bilibili.com/video/BV123")
                store.toggleLater(entry)
                check(store.state.value.entries.single().later && !store.state.value.entries.single().favorite)
                store.toggleFavorite(entry); store.addFolder("学习"); store.edit(entry, "学习", "测试备注")
                store.recordVisit(entry)
                val backup = store.exportBackup()
                check(store.importBackup(backup) == 0)
                check(store.state.value.entries.single().note == "测试备注")
                check(store.state.value.history.size == 1)
                store.close()
                val reopened = LocalLibrary(targetContext, name)
                library = reopened; reopened.load()
                check(reopened.state.value.entries.single().favorite)
                check(reopened.state.value.history.size == 1)
                try { reopened.importBackup("{bad-json}"); error("accepted bad import") } catch (_: IllegalArgumentException) { }
                check(reopened.state.value.entries.single().note == "测试备注")
                reopened.removeHistory(entry)
                check(reopened.state.value.entries.single().favorite)
                report.put("library_persistence_independence_backup", true)
                if (live) {
                    val client = withContext(Dispatchers.Main) { CronetTransport(targetContext, SessionStore(), useCookies = false) }
                    transport = client
                    val trends = TrendingClient(client)
                    val trendingReport = JSONObject()
                    for (platform in listOf(Platform.BILIBILI, Platform.ZHIHU, Platform.DOUYIN)) {
                        val result = trends.load(platform)
                        trendingReport.put(platform.name, JSONObject().put("count", result.words.size).put("available", result.words.isNotEmpty()))
                    }
                    report.put("public_trending", trendingReport)
                    val page = DouyinPageClient(targetContext); douyin = page
                    val started = android.os.SystemClock.elapsedRealtime()
                    try {
                        val result = page.search("Android", 1)
                        report.put("douyin", JSONObject().put("count", result.items.size).put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - started))
                    } catch (e: PlatformException) {
                        report.put("douyin", JSONObject().put("failure", e.kind.name).put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - started))
                    }
                }
                report.put("completed", true)
            } catch (e: Exception) { report.put("completed", false).put("error", e.javaClass.simpleName).put("frames", e.stackTrace.take(4).joinToString(" | ")) }
            finally { library?.close(); withContext(Dispatchers.Main) { douyin?.close() }; transport?.close() }
        }
        finish(if (report.optBoolean("completed")) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply { putString("report", report.toString()) })
    }
}
