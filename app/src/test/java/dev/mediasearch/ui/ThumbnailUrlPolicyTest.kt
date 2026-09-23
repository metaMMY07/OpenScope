package dev.mediasearch.ui

import dev.mediasearch.core.Platform
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThumbnailUrlPolicyTest {
    @Test
    fun acceptsOfficialHttpsCoversAndRejectsForeignOrCredentialUrls() {
        assertTrue(ThumbnailUrlPolicy.allowed(URL("https://i0.hdslb.com/bfs/archive/a.jpg"), Platform.BILIBILI))
        assertTrue(ThumbnailUrlPolicy.allowed(URL("https://sns-webpic-qc.xhscdn.com/a.webp"), Platform.XHS))
        assertFalse(ThumbnailUrlPolicy.allowed(URL("https://i0.hdslb.com.evil.example/a.jpg"), Platform.BILIBILI))
        assertFalse(ThumbnailUrlPolicy.allowed(URL("http://i0.hdslb.com/a.jpg"), Platform.BILIBILI))
        assertFalse(ThumbnailUrlPolicy.allowed(URL("https://user:pass@i0.hdslb.com/a.jpg"), Platform.BILIBILI))
        assertFalse(ThumbnailUrlPolicy.allowed(URL("https://i0.hdslb.com:8443/a.jpg"), Platform.BILIBILI))
    }
}
