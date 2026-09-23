package dev.mediasearch.bilibili

import dev.mediasearch.core.*
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Explicit, bounded, read-only import. Never mirrors deletions or sends platform mutations. */
class BilibiliFavorites(private val transport: HttpTransport) {
    suspend fun sync(save: suspend (List<SearchItem>) -> Unit): Int {
        val nav = get("/x/web-interface/nav").optJSONObject("data")
        if (nav?.optBoolean("isLogin") != true) throw PlatformException(FailureKind.LOGIN_REQUIRED, "请先登录哔哩哔哩")
        val uid = nav.optLong("mid", 0)
        if (uid <= 0) throw PlatformException(FailureKind.LOGIN_REQUIRED, "无法确认当前账号")
        delay(1000)
        val folders = get("/x/v3/fav/folder/created/list-all?up_mid=$uid").optJSONObject("data")?.optJSONArray("list") ?: return 0
        var count = 0
        var requests = 0
        val seen = mutableSetOf<String>()
        for (index in 0 until minOf(folders.length(), 10)) {
            val id = folders.optJSONObject(index)?.optLong("id", 0) ?: continue
            if (id <= 0) continue
            var page = 1
            do {
                if (count >= 100 || requests >= 10) return count
                delay(1500)
                // Bind every page to the original account; a concurrent logout never imports another user's data.
                val current = get("/x/web-interface/nav").optJSONObject("data")
                if (current?.optBoolean("isLogin") != true || current.optLong("mid") != uid) {
                    throw PlatformException(FailureKind.LOGIN_REQUIRED, "账号已变化，停止同步；已保存内容保留")
                }
                delay(1000)
                val data = get("/x/v3/fav/resource/list?media_id=$id&pn=$page&ps=20&order=mtime&platform=web").optJSONObject("data")
                    ?: throw PlatformException(FailureKind.PARSE, "收藏数据格式变化")
                requests++
                val batch = parse(data).filter { seen.add(it.id) }.take(100 - count)
                save(batch); count += batch.size; page++
            } while (data.optBoolean("has_more"))
        }
        return count
    }

    private suspend fun get(path: String): JSONObject {
        val response = transport.get("https://api.bilibili.com$path", mapOf("Referer" to "https://www.bilibili.com/"))
        if (response.status == 429 || response.status == 412) throw PlatformException(FailureKind.RATE_LIMITED, "平台限流，已停止同步")
        if (response.status == 403) throw PlatformException(FailureKind.CHALLENGE, "请在官方网页完成验证")
        if (response.status !in 200..299) throw PlatformException(FailureKind.NETWORK, "收藏请求未完成")
        val root = JSONObject(response.body)
        when (root.optInt("code", -1)) {
            0 -> return root
            -101 -> throw PlatformException(FailureKind.LOGIN_REQUIRED, "请重新登录哔哩哔哩")
            -352, -412 -> throw PlatformException(FailureKind.CHALLENGE, "平台要求验证，已停止同步")
            else -> throw PlatformException(FailureKind.PARSE, "收藏接口暂不可用")
        }
    }

    companion object {
        internal fun parse(data: JSONObject): List<SearchItem> {
            val rows = data.optJSONArray("medias") ?: return emptyList()
            return (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val id = row.optString("bvid")
                if (!Regex("BV[0-9A-Za-z]+").matches(id) || row.optString("title").isBlank()) return@mapNotNull null
                SearchItem(id, Platform.BILIBILI, row.optString("title"), row.optJSONObject("upper")?.optString("name").orEmpty(),
                    row.optString("intro"), "https://www.bilibili.com/video/$id", row.optString("cover"),
                    publishedAt = row.optLong("pubtime", 0).takeIf { it > 0 })
            }.distinctBy { it.id }
        }
    }
}
