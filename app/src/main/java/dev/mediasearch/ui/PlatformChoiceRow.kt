package dev.mediasearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mediasearch.core.Platform

/** Four equally sized choices remain visible in one row on narrow phones. */
@Composable
internal fun PlatformChoiceRow(selected: (Platform) -> Boolean, onClick: (Platform) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Platform.entries.forEach { platform ->
            val active = selected(platform)
            Surface(
                onClick = { onClick(platform) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { contentDescription = "${platform.label}${if (active) "，已选择" else "，未选择"}" },
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlatformLogo(platform, 18.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (platform == Platform.BILIBILI) "B站" else platform.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
