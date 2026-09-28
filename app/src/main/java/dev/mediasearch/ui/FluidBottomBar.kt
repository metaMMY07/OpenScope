package dev.mediasearch.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A lightweight Compose counterpart to LiteTale's fluid, translucent dock. */
@Composable
internal fun FluidBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val destinations = listOf(
        "搜索" to AppIcons.Explore,
        "内容库" to AppIcons.Bookmark,
        "账号" to AppIcons.Person,
        "设置" to AppIcons.Settings
    )
    val leftEdge = remember { Animatable(selectedTab.toFloat()) }
    val rightEdge = remember { Animatable(selectedTab.toFloat()) }
    LaunchedEffect(selectedTab) {
        val movingRight = selectedTab > (leftEdge.value + rightEdge.value) / 2f
        coroutineScope {
            launch {
                leftEdge.animateTo(selectedTab.toFloat(), tween(380,
                    easing = if (movingRight) FastOutSlowInEasing else LinearOutSlowInEasing))
            }
            launch {
                rightEdge.animateTo(selectedTab.toFloat(), tween(380,
                    easing = if (movingRight) LinearOutSlowInEasing else FastOutSlowInEasing))
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val indicator by animateColorAsState(colors.primaryContainer, label = "导航高亮颜色")
    Column(
        Modifier.fillMaxWidth().shadow(6.dp).background(
            Brush.verticalGradient(listOf(
                colors.surfaceContainerHigh.copy(alpha = 0.88f),
                colors.surface.copy(alpha = 0.78f)
            ))
        )
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant.copy(alpha = 0.6f))
        Box(Modifier.fillMaxWidth().height(72.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val slot = size.width / destinations.size
                val halfWidth = minOf(44.dp.toPx(), slot * 0.40f)
                val left = (leftEdge.value + 0.5f) * slot - halfWidth
                val right = (rightEdge.value + 0.5f) * slot + halfWidth
                drawRoundRect(
                    color = indicator,
                    topLeft = Offset(left, 9.dp.toPx()),
                    size = Size((right - left).coerceAtLeast(0f), 36.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx())
                )
            }
            Row(Modifier.fillMaxSize()) {
                destinations.forEachIndexed { index, (label, icon) ->
                    val isSelected = selectedTab == index
                    val iconScale by animateFloatAsState(if (isSelected) 1.08f else 1f,
                        animationSpec = tween(190), label = "导航图标缩放")
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .semantics { role = Role.Tab; selected = isSelected; contentDescription = label }
                            .clickable { onSelect(index) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null,
                                modifier = Modifier.size(24.dp).graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                                tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.onSurface else colors.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
