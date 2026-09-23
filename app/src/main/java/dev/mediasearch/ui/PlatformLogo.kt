package dev.mediasearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mediasearch.R
import dev.mediasearch.core.Platform

/** Bundled platform-owned marks; source URLs are recorded in docs/brand-assets.md. */
@Composable
internal fun PlatformLogo(platform: Platform, size: Dp = 36.dp) {
    val resource = when (platform) {
        Platform.BILIBILI -> R.drawable.platform_bilibili
        Platform.ZHIHU -> R.drawable.platform_zhihu
        Platform.XHS -> R.drawable.platform_xhs
        Platform.DOUYIN -> R.drawable.platform_douyin
    }
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size / 3),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(resource),
                contentDescription = "${platform.label}图标",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
