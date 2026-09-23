package dev.mediasearch.trending

import dev.mediasearch.core.HttpTransport
import dev.mediasearch.core.Platform
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** The words returned by one platform's public trending endpoint. */
data class TrendingResult(
    val words: List<String>,
    val message: String? = null,
)

/**
 * Reads public trending words without using an account session.
 *
 * Results (including failures) are cached for the lifetime of this client.
 * Callers can explicitly bypass that cache with [load]'s [refresh] argument.
 */
class TrendingClient(private val transport: HttpTransport) {
    private val cache = ConcurrentHashMap<Platform, TrendingResult>()
    private val locks = ConcurrentHashMap<Platform, Mutex>()

    suspend fun load(platform: Platform, refresh: Boolean = false): TrendingResult {
        val lock = locks.computeIfAbsent(platform) { Mutex() }
        return lock.withLock {
            if (!refresh) {
                cache[platform]?.let { return@withLock it }
            }

            val result = fetch(platform)
            // Failures are cached deliberately: a failing endpoint should not
            // be retried on every recomposition or platform switch.
            cache[platform] = result
            result
        }
    }

    private suspend fun fetch(platform: Platform): TrendingResult {
        val endpoint = endpointFor(platform)
            ?: return TrendingResult(emptyList(), UNAVAILABLE_MESSAGE)

        val response = try {
            // withTimeoutOrNull distinguishes this timeout from cancellation of
            // the caller's scope, so normal coroutine cancellation still wins.
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                transport.get(endpoint.url, endpoint.headers)
            } ?: return TrendingResult(emptyList(), FAILED_MESSAGE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return TrendingResult(emptyList(), FAILED_MESSAGE)
        }

        if (response.status !in 200..299) {
            return TrendingResult(emptyList(), FAILED_MESSAGE)
        }

        return try {
            val words = when (endpoint.kind) {
                Kind.BILIBILI -> parseBilibili(response.body)
                Kind.ZHIHU -> parseZhihu(response.body)
                Kind.DOUYIN -> parseDouyin(response.body)
            }
            if (words.isEmpty()) TrendingResult(emptyList(), FAILED_MESSAGE)
            else TrendingResult(words)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TrendingResult(emptyList(), FAILED_MESSAGE)
        }
    }

    private fun parseBilibili(body: String): List<String> {
        val items = JSONObject(body)
            .optJSONObject("data")
            ?.optJSONObject("trending")
            ?.optJSONArray("list")
            ?: throw IllegalArgumentException("missing Bilibili trending list")
        return collectWords(items) { item ->
            firstString(item, "show_name", "keyword")
        }
    }

    private fun parseZhihu(body: String): List<String> {
        val items = JSONObject(body)
            .optJSONObject("top_search")
            ?.optJSONArray("words")
            ?: throw IllegalArgumentException("missing Zhihu trending list")
        return collectWords(items) { item ->
            firstString(item, "display_query", "query")
        }
    }

    private fun parseDouyin(body: String): List<String> {
        val items = JSONObject(body).optJSONArray("word_list")
            ?: throw IllegalArgumentException("missing Douyin trending list")
        return collectWords(items) { item ->
            firstString(item, "word")
        }
    }

    private fun collectWords(items: org.json.JSONArray, value: (JSONObject) -> String?): List<String> {
        val words = ArrayList<String>(minOf(items.length(), MAX_WORDS))
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val word = value(item)?.trim().orEmpty()
            if (word.isEmpty()) continue
            words += word
            if (words.size == MAX_WORDS) break
        }
        return words
    }

    private fun firstString(item: JSONObject, vararg names: String): String? {
        for (name in names) {
            val value = item.opt(name)
            if (value is String && value.isNotBlank()) return value
        }
        return null
    }

    private fun endpointFor(platform: Platform): Endpoint? {
        return when (platform) {
            Platform.BILIBILI -> Endpoint(
                Kind.BILIBILI,
                "https://api.bilibili.com/x/web-interface/search/square?limit=$MAX_WORDS",
                mapOf(
                    "Accept" to "application/json, text/plain, */*",
                    "Referer" to "https://www.bilibili.com/",
                    // An explicit empty value prevents a cookie-aware
                    // transport from attaching an account session.
                    "Cookie" to "",
                ),
            )
            Platform.ZHIHU -> Endpoint(
                Kind.ZHIHU,
                "https://www.zhihu.com/api/v4/search/top_search",
                mapOf(
                    "Accept" to "application/json, text/plain, */*",
                    "Referer" to "https://www.zhihu.com/",
                    "Cookie" to "",
                ),
            )
            Platform.XHS -> null
            else -> if (platform.name == "DOUYIN") {
                Endpoint(
                    Kind.DOUYIN,
                    "https://www.iesdouyin.com/web/api/v2/hotsearch/billboard/word/",
                    mapOf(
                        "Accept" to "application/json, text/plain, */*",
                        "Cookie" to "",
                    ),
                )
            } else {
                null
            }
        }
    }

    private data class Endpoint(
        val kind: Kind,
        val url: String,
        val headers: Map<String, String>,
    )

    private enum class Kind { BILIBILI, ZHIHU, DOUYIN }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val MAX_WORDS = 20
        const val UNAVAILABLE_MESSAGE = "该平台热搜暂未接入"
        const val FAILED_MESSAGE = "热搜暂时取不到，稍后再试"
    }
}
