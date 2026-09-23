package dev.mediasearch.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dev.mediasearch.core.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Public cover images only. No cookies, account data, or browser state enter these requests. */
internal object ThumbnailLoader {
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_DIMENSION = 8192
    private val slots = Semaphore(3)
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(rawUrl: String, platform: Platform): Bitmap? {
        synchronized(cache) { cache.get(rawUrl) }?.let { return it }
        return withContext(Dispatchers.IO) {
            slots.withPermit {
                synchronized(cache) { cache.get(rawUrl) }?.let { return@withPermit it }
                val bitmap = runCatching { download(rawUrl, platform) }.getOrNull()
                if (bitmap != null) synchronized(cache) { cache.put(rawUrl, bitmap) }
                bitmap
            }
        }
    }

    private fun download(rawUrl: String, platform: Platform): Bitmap? {
        var url = URL(rawUrl)
        repeat(3) {
            if (!ThumbnailUrlPolicy.allowed(url, platform)) return null
            val connection = (url.openConnection() as? HttpsURLConnection) ?: return null
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 3_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.8")
                connection.setRequestProperty("Referer", platform.homeUrl + "/")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    url = URL(url, location)
                    return@repeat
                }
                if (status != 200 || connection.contentLengthLong > MAX_IMAGE_BYTES) return null
                val contentType = connection.contentType.orEmpty().lowercase()
                if (!contentType.startsWith("image/")) return null
                val bytes = ByteArrayOutputStream().use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_IMAGE_BYTES) return null
                            output.write(buffer, 0, count)
                        }
                    }
                    output.toByteArray()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..MAX_DIMENSION || bounds.outHeight !in 1..MAX_DIMENSION) return null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample })
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

}
