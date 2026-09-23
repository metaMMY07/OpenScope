package dev.mediasearch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultToolsTest {
    @Test
    fun `blank and multi word filters use all fields and preserve relevance order`() {
        val items = listOf(
            item("1", title = "Kotlin", author = "Alice", summary = "Android 基础"),
            item("2", title = "Android", author = "Bob", summary = "Kotlin 基础"),
            item("3", title = "Java", author = "Kotlin Team", summary = "语言")
        )
        assertEquals(items, ResultTools.filterAndSort(items, "  ", ResultSort.RELEVANCE))
        assertEquals(listOf("1", "2"), ResultTools.filterAndSort(items, "KOTLIN Android", ResultSort.RELEVANCE).map { it.id })
        assertEquals(listOf("3"), ResultTools.filterAndSort(items, "team", ResultSort.RELEVANCE).map { it.id })
    }

    @Test
    fun `latest and engagement sort descending with missing values last and stable ties`() {
        val items = listOf(
            item("old", publishedAt = 10, engagement = 3),
            item("tie", publishedAt = 20, engagement = 8),
            item("new", publishedAt = 30, engagement = 8),
            item("missing", publishedAt = null, engagement = null)
        )
        assertEquals(listOf("new", "tie", "old", "missing"), ResultTools.filterAndSort(items, "", ResultSort.LATEST).map { it.id })
        assertEquals(listOf("tie", "new", "old", "missing"), ResultTools.filterAndSort(items, "", ResultSort.ENGAGEMENT).map { it.id })
    }

    @Test
    fun `duplicate grouping is conservative and removes same platform IDs`() {
        val title = "这是一篇足够长的跨平台教程标题"
        val items = listOf(
            item("x1", Platform.XHS, title = "Ｐｙｔｈｏｎ： 这是一篇足够长的跨平台教程标题！", author = "作者"),
            item("x1", Platform.XHS, title = "重复版本", author = "其他"),
            item("b1", Platform.BILIBILI, title = "Python这是一篇足够长的跨平台教程标题", author = "作者"),
            item("short-x", Platform.XHS, title = "同一内容", author = "作者"),
            item("short-b", Platform.BILIBILI, title = "同一内容", author = "作者")
        )
        val groups = ResultTools.groupDuplicates(items)
        assertEquals(listOf(listOf("x1", "b1"), listOf("short-x"), listOf("short-b")), groups.map { it.map(SearchItem::id) })
        assertEquals(4, groups.flatten().size)
        assertTrue(groups.any { it.size == 2 })

        val exact = listOf(
            item("x2", Platform.XHS, title = title, author = "作者"),
            item("b2", Platform.BILIBILI, title = title, author = "作者")
        )
        assertEquals(listOf(listOf("x2", "b2")), ResultTools.groupDuplicates(exact).map { it.map(SearchItem::id) })
    }

    @Test
    fun `csv has BOM formula protection quotes and multiline fields`() {
        val value = item("1", title = " =SUM(1,2)", summary = "第一行\n第二行,\"引号\"")
        val csv = ResultTools.csv(listOf(value))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"' =SUM(1,2)\""))
        assertTrue(csv.contains("\"第一行\n第二行,\"\"引号\"\"\""))
    }

    @Test
    fun `links keep safe parameters and reject non official URLs`() {
        val good = item("good", url = "https://www.bilibili.com/video/BV1?p=2&token=a%2Bb#reply")
        val duplicate = good.copy(id = "copy")
        val bad = listOf(
            item("js", url = "javascript:alert(1)"),
            item("lookalike", url = "https://www.bilibili.com.evil.test/video"),
            item("credentials", url = "https://user:password@www.bilibili.com/video/BV1")
        )
        assertEquals(good.url, ResultTools.links(listOf(good, duplicate)))
        assertEquals("", ResultTools.links(bad))
    }

    @Test
    fun `markdown escapes user content and only emits safe links`() {
        val markdown = ResultTools.markdown(listOf(item("BV1", title = "[标题]<script>", author = "**作者**")))
        assertTrue(markdown.contains("\\[标题\\]\\<script\\>"))
        assertTrue(markdown.contains("作者：\\*\\*作者\\*\\*"))
        assertTrue(markdown.contains("[打开原文](<https://www.bilibili.com/video/BV1>)"))
        assertFalse(markdown.contains("<script>"))
    }

    private fun item(
        id: String,
        platform: Platform = Platform.BILIBILI,
        title: String = "标题 $id",
        author: String = "作者",
        summary: String = "摘要",
        url: String = "https://www.bilibili.com/video/$id",
        publishedAt: Long? = 1,
        engagement: Long? = 1,
        views: Long? = 1
    ) = SearchItem(
        id = id,
        platform = platform,
        title = title,
        author = author,
        summary = summary,
        url = url,
        publishedAt = publishedAt,
        engagement = engagement,
        views = views
    )
}
