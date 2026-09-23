package dev.mediasearch.douyin

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DouyinPageClientTest {
    @Test fun `only official numeric video links are returned`() {
        val data = JSONObject("""{"items":[
            {"url":"https://www.douyin.com/video/1234567890","title":"安卓入门","metric":"1.2万"},
            {"url":"https://www.douyin.com/video/1234567890","title":"duplicate"},
            {"url":"https://www.douyin.com.evil.test/video/1234567890","title":"bad"},
            {"url":"https://www.douyin.com/user/1234567890","title":"profile"},
            {"url":"http://www.douyin.com/video/1234567891","title":"http"}
        ]}""")
        val parsed = DouyinPageClient.parse(data)
        assertEquals(1, parsed.size)
        assertEquals("1234567890", parsed.single().id)
        assertEquals(12_000L, parsed.single().metricCounts["likes"])
    }
    @Test fun `query is path encoded without adding arbitrary parameters`() {
        val url = DouyinPageClient.searchUrl("安卓 & 评测/新机")
        assertTrue(url.startsWith("https://www.douyin.com/search/"))
        assertTrue(url.contains("%20%26%20"))
        assertTrue(url.contains("%2F"))
        assertTrue(url.endsWith("?type=video"))
    }
}
