package dev.mediasearch.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ResultFact(val label: String, val value: String)

/** Only describes values actually supplied by a platform, never the combined sort score. */
object ResultMetadata {
    fun facts(item: SearchItem): List<ResultFact> = buildList {
        item.publishedAt?.let { raw ->
            val seconds = if (raw > 10_000_000_000L) raw / 1000L else raw
            if (seconds in 946684800L..4102444800L) {
                add(ResultFact("发布", SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(seconds * 1000L))))
            }
        }
        item.views?.takeIf { it >= 0 }?.let { add(ResultFact("播放", count(it))) }
        val labels = when (item.platform) {
            Platform.ZHIHU -> mapOf("likes" to "赞同", "comments" to "评论")
            else -> mapOf("likes" to "点赞", "favorites" to "收藏", "coins" to "投币",
                "comments" to "评论", "danmaku" to "弹幕", "shares" to "分享")
        }
        for ((key, label) in labels) item.metricCounts[key]?.takeIf { it >= 0 }?.let {
            add(ResultFact(label, count(it)))
        }
        if (size == 0 && item.metric.isNotBlank()) add(ResultFact("平台数据", item.metric))
    }

    fun count(number: Long): String = when {
        number >= 100_000_000 -> "%.1f亿".format(Locale.CHINA, number / 100_000_000.0)
        number >= 10_000 -> "%.1f万".format(Locale.CHINA, number / 10_000.0)
        else -> number.toString()
    }
}
