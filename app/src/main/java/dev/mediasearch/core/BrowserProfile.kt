package dev.mediasearch.core

import java.net.URI

/** Existing crawler/browser compatibility profile; visible content pages may choose another layout. */
object BrowserProfile {
    fun desktop(platform: Platform) = platform != Platform.ZHIHU

    fun desktopUserAgent(default: String): String = default
        .replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
        .replace(Regex("\\sVersion/\\S+"), "")
        .replace(" Mobile", "")

    fun userAgent(platform: Platform, default: String): String =
        if (desktop(platform)) desktopUserAgent(default) else default

    fun visibleUserAgent(default: String, desktop: Boolean): String =
        if (desktop) desktopUserAgent(default) else default

    /** Layout choice for visible content pages only; crawler requests retain their known profile. */
    fun visiblePageUrl(platform: Platform, url: String, desktop: Boolean): String {
        if (desktop) return pageUrl(platform, url)
        if (!allowed(platform, url)) return url
        val uri = URI(url)
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        return when {
            platform == Platform.BILIBILI && uri.host in setOf("www.bilibili.com", "bilibili.com") &&
                (path == "/" || path.startsWith("/video/")) ->
                "https://m.bilibili.com" + path +
                    uri.rawQuery?.let { "?$it" }.orEmpty() + uri.rawFragment?.let { "#$it" }.orEmpty()
            platform == Platform.XHS && path == "/" -> "https://www.xiaohongshu.com/explore"
            else -> url
        }
    }

    fun secureVisibleNavigationUrl(platform: Platform, url: String, desktop: Boolean): String? {
        val secure = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
        return secure.takeIf { allowed(platform, it) }?.let { visiblePageUrl(platform, it, desktop) }
    }

    /** Some official redirects downgrade to HTTP. Reissue as HTTPS without sending cleartext. */
    fun secureNavigationUrl(platform: Platform, url: String): String? {
        val secure = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
        return secure.takeIf { allowed(platform, it) }?.let { pageUrl(platform, it) }
    }

    fun allowed(platform: Platform, url: String): Boolean = runCatching {
        val uri = URI(url)
        val root = when (platform) {
            Platform.BILIBILI -> "bilibili.com"
            Platform.ZHIHU -> "zhihu.com"
            Platform.XHS -> "xiaohongshu.com"
            Platform.DOUYIN -> "douyin.com"
        }
        val host = uri.host?.lowercase() ?: return false
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            (host == root || host.endsWith(".$root"))
    }.getOrDefault(false)

    fun pageUrl(platform: Platform, url: String): String {
        if (!allowed(platform, url)) return url
        val uri = URI(url)
        return when {
            platform == Platform.BILIBILI && uri.host in setOf("m.bilibili.com", "bilibili.com") ->
                "https://www.bilibili.com" + uri.rawPath.orEmpty().ifEmpty { "/" } +
                    uri.rawQuery?.let { "?$it" }.orEmpty() + uri.rawFragment?.let { "#$it" }.orEmpty()
            platform == Platform.XHS && uri.path in setOf("", "/") -> "https://www.xiaohongshu.com/explore"
            else -> url
        }
    }
}
