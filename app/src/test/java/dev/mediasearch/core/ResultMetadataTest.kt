package dev.mediasearch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultMetadataTest {
    @Test
    fun labelsOnlyValuesActuallyReturnedByThePlatform() {
        val bili = SearchItem("BV1", Platform.BILIBILI, "视频", "作者", "", "https://www.bilibili.com/video/BV1",
            views = 123_456L, metricCounts = mapOf("likes" to 1200L, "favorites" to 34L))
        val facts = ResultMetadata.facts(bili)
        assertEquals(listOf("播放", "点赞", "收藏"), facts.map { it.label })
        assertEquals("12.3万", facts[0].value)
        assertFalse(facts.any { it.label == "评论" || it.label == "投币" })

        val zhihu = bili.copy(platform = Platform.ZHIHU, views = null, metricCounts = mapOf("likes" to 42L))
        assertEquals(listOf("赞同"), ResultMetadata.facts(zhihu).map { it.label })
        assertTrue(ResultMetadata.facts(zhihu.copy(metricCounts = emptyMap(), metric = "")).isEmpty())
    }
}
