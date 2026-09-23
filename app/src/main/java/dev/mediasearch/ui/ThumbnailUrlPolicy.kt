package dev.mediasearch.ui

import dev.mediasearch.core.Platform
import java.net.URL

internal object ThumbnailUrlPolicy {
    fun allowed(url: URL, platform: Platform): Boolean {
        if (url.protocol != "https" || url.userInfo != null || url.port !in setOf(-1, 443)) return false
        val host = url.host.lowercase().trimEnd('.')
        val domains = when (platform) {
            Platform.BILIBILI -> listOf("hdslb.com", "bilibili.com")
            Platform.ZHIHU -> listOf("zhimg.com", "zhihu.com")
            Platform.XHS -> listOf("xhscdn.com", "xiaohongshu.com")
            Platform.DOUYIN -> listOf("douyinpic.com", "byteimg.com", "douyinstatic.com", "douyin.com")
        }
        return domains.any { host == it || host.endsWith(".$it") }
    }
}
