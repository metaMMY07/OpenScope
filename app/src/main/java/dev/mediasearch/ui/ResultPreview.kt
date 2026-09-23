package dev.mediasearch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.mediasearch.core.SearchItem

@Composable
internal fun ResultPreview(item: SearchItem) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.platform, item.thumbnailUrl) {
        value = if (item.thumbnailUrl.isBlank()) null else ThumbnailLoader.load(item.thumbnailUrl, item.platform)
    }
    Surface(
        modifier = Modifier.size(width = 112.dp, height = 84.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap == null) {
                Text("封面", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "${item.title}的缩略图",
                    modifier = Modifier.size(width = 112.dp, height = 84.dp), contentScale = ContentScale.Fit)
            }
        }
    }
}
