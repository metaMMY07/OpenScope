package dev.mediasearch.trending

import dev.mediasearch.core.HttpResponse
import dev.mediasearch.core.HttpTransport
import dev.mediasearch.core.Platform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrendingClientTest {
    @Test
    fun parsesBilibiliZhihuAndDouyinFixtures() = runTest {
        val douyin = Platform.valueOf("DOUYIN")
        val transport = UrlTransport(
            mapOf(
                BILIBILI_URL to HttpResponse(
                    200,
                    """{"data":{"trending":{"list":[
                        {"show_name":" B站展示名 "}, {"keyword":"B站关键词"}, {"show_name":""}
                    ]}}}""",
                ),
                ZHIHU_URL to HttpResponse(
                    200,
                    """{"top_search":{"words":[
                        {"display_query":"知乎展示词"}, {"query":"知乎回退词"}
                    ]}}""",
                ),
                DOUYIN_URL to HttpResponse(
                    200,
                    """{"word_list":[{"word":"抖音热词一"},{"word":" 抖音热词二 "}]}""",
                ),
            ),
        )
        val client = TrendingClient(transport)

        assertEquals(listOf("B站展示名", "B站关键词"), client.load(Platform.BILIBILI).words)
        assertEquals(listOf("知乎展示词", "知乎回退词"), client.load(Platform.ZHIHU).words)
        assertEquals(listOf("抖音热词一", "抖音热词二"), client.load(douyin).words)
        assertTrue(transport.headers.all { headers -> headers["Cookie"] == "" })
    }

    @Test
    fun capsWordsAtTwentyAndSkipsMalformedEntries() = runTest {
        val items = (0..24).joinToString(",") { index ->
            if (index == 1) "{}" else "{\"keyword\":\"词$index\"}"
        }
        val transport = UrlTransport(
            mapOf(BILIBILI_URL to HttpResponse(200, "{\"data\":{\"trending\":{\"list\":[$items]}}}")),
        )

        val result = TrendingClient(transport).load(Platform.BILIBILI)

        assertEquals(20, result.words.size)
        assertEquals("词0", result.words.first())
        assertEquals("词20", result.words.last())
    }

    @Test
    fun malformedAndHttpFailuresAreSafeAndCachedUntilRefresh() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "not-json"),
            HttpResponse(200, "{\"top_search\":{}}"),
            HttpResponse(200, "{\"top_search\":{\"words\":[{\"query\":\"恢复\"}]}}"),
        )
        val client = TrendingClient(transport)

        val malformed = client.load(Platform.ZHIHU)
        assertEquals(emptyList(), malformed.words)
        assertEquals("热搜暂时取不到，稍后再试", malformed.message)
        assertEquals(malformed, client.load(Platform.ZHIHU))
        assertEquals(1, transport.calls)

        val recovered = client.load(Platform.ZHIHU, refresh = true)
        assertEquals(emptyList(), recovered.words)
        assertEquals(2, transport.calls)

        val stillCached = client.load(Platform.ZHIHU)
        assertEquals(recovered, stillCached)
        assertEquals(2, transport.calls)

        val afterRefresh = client.load(Platform.ZHIHU, refresh = true)
        assertEquals(listOf("恢复"), afterRefresh.words)
        assertEquals(3, transport.calls)
    }

    @Test
    fun successfulResultIsCachedAndRefreshesExplicitly() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "{\"data\":{\"trending\":{\"list\":[{\"keyword\":\"首次\"}]}}}"),
            HttpResponse(200, "{\"data\":{\"trending\":{\"list\":[{\"keyword\":\"刷新后\"}]}}}"),
        )
        val client = TrendingClient(transport)

        assertEquals(listOf("首次"), client.load(Platform.BILIBILI).words)
        assertEquals(listOf("首次"), client.load(Platform.BILIBILI).words)
        assertEquals(1, transport.calls)
        assertEquals(listOf("刷新后"), client.load(Platform.BILIBILI, refresh = true).words)
        assertEquals(2, transport.calls)
    }

    @Test
    fun xhsIsUnavailableWithoutARequest() = runTest {
        val transport = QueueTransport()
        val result = TrendingClient(transport).load(Platform.XHS)

        assertEquals(emptyList(), result.words)
        assertEquals("该平台热搜暂未接入", result.message)
        assertEquals(0, transport.calls)
    }

    private class QueueTransport(private vararg val responses: HttpResponse) : HttpTransport {
        var calls: Int = 0
            private set

        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
            calls++
            return responses.getOrNull(calls - 1) ?: error("unexpected request: $url")
        }
    }

    private class UrlTransport(private val responses: Map<String, HttpResponse>) : HttpTransport {
        val headers = mutableListOf<Map<String, String>>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
            this.headers += headers
            return responses[url] ?: error("unexpected request: $url")
        }
    }

    private companion object {
        const val BILIBILI_URL = "https://api.bilibili.com/x/web-interface/search/square?limit=20"
        const val ZHIHU_URL = "https://www.zhihu.com/api/v4/search/top_search"
        const val DOUYIN_URL = "https://www.iesdouyin.com/web/api/v2/hotsearch/billboard/word/"
    }
}
