package dev.mediasearch.bilibili

import dev.mediasearch.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BilibiliFavoritesTest {
    @Test fun `invalid videos are omitted and ids deduplicated`() {
        val rows = JSONObject("""{"medias":[{"bvid":"BV123","title":"内容","upper":{"name":"作者"},"pubtime":123}, {"bvid":"BV123","title":"重复"},{"bvid":"bad/link","title":"坏链接"}]}""")
        val items = BilibiliFavorites.parse(rows)
        assertEquals(1, items.size)
        assertEquals("https://www.bilibili.com/video/BV123", items.single().url)
        assertEquals(123L, items.single().publishedAt)
    }
    @Test fun `unauthenticated import never reads folders or saves anything`() = runBlocking {
        var calls = 0
        var saved = false
        val client = BilibiliFavorites(object : HttpTransport {
            override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
                calls++
                return HttpResponse(200, """{"code":0,"data":{"isLogin":false}}""")
            }
        })
        try { client.sync { saved = true }; fail("expected login failure") }
        catch (e: PlatformException) { assertEquals(FailureKind.LOGIN_REQUIRED, e.kind) }
        assertEquals(1, calls)
        assertFalse(saved)
    }
}
