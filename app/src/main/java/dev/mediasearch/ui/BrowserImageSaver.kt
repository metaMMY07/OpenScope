package dev.mediasearch.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dev.mediasearch.core.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/** Saves images requested by the official XHS page into the user's Pictures collection. */
internal object BrowserImageSaver {
    const val MAX_BYTES = 20 * 1024 * 1024

    internal data class Format(val mime: String, val extension: String)

    internal fun imageFormat(bytes: ByteArray): Format? {
        if (bytes.size < 12 || bytes.size > MAX_BYTES) return null
        val ascii = { start: Int, value: String ->
            bytes.size >= start + value.length && value.indices.all { bytes[start + it].toInt() == value[it].code }
        }
        return when {
            bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> Format("image/jpeg", "jpg")
            bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte) -> Format("image/png", "png")
            ascii(0, "RIFF") && ascii(8, "WEBP") -> Format("image/webp", "webp")
            ascii(0, "GIF87a") || ascii(0, "GIF89a") -> Format("image/gif", "gif")
            ascii(4, "ftyp") && (ascii(8, "avif") || ascii(8, "avis")) -> Format("image/avif", "avif")
            else -> null
        }
    }

    suspend fun save(context: Context, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val format = imageFormat(bytes) ?: error("不是受支持的图片格式")
        val resolver = context.contentResolver
        val name = "OpenScope_XHS_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.${format.extension}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenScope")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册文件")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入相册文件")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1) {
                "无法完成相册写入"
            }
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    /** Direct image downloads use the same path as the page's Blob downloads. */
    suspend fun saveHttpImage(context: Context, rawUrl: String, userAgent: String, cookies: String?): Uri {
        val bytes = withContext(Dispatchers.IO) {
            var url = URL(rawUrl)
            repeat(4) {
                require(ThumbnailUrlPolicy.allowed(url, Platform.XHS)) { "图片来源不受支持" }
                val connection = url.openConnection() as HttpsURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Referer", "https://www.xiaohongshu.com/")
                    connection.setRequestProperty("Accept", "image/*")
                    if (url.host.endsWith("xiaohongshu.com") && !cookies.isNullOrEmpty()) {
                        connection.setRequestProperty("Cookie", cookies)
                    }
                    val status = connection.responseCode
                    if (status in 300..399) {
                        url = URL(url, connection.getHeaderField("Location") ?: error("下载重定向缺少地址"))
                        return@repeat
                    }
                    require(status == 200 && connection.contentLengthLong <= MAX_BYTES) { "图片下载失败" }
                    require(connection.contentType.orEmpty().startsWith("image/", ignoreCase = true)) { "下载内容不是图片" }
                    return@withContext ByteArrayOutputStream().use { output ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= MAX_BYTES) { "图片超过 20 MB" }
                                output.write(buffer, 0, count)
                            }
                        }
                        output.toByteArray()
                    }
                } finally {
                    connection.disconnect()
                }
            }
            error("图片重定向次数过多")
        }
        return save(context, bytes)
    }
}
