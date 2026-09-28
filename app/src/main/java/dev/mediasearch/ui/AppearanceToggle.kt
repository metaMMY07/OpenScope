package dev.mediasearch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** One-tap appearance switch for both regular and minimal search homes. */
@Composable
internal fun AppearanceToggle(modifier: Modifier = Modifier) {
    val preferences = LocalThemePreferences.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (preferences.appearance) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val action = if (dark) "切换到浅色模式" else "切换到深色模式"
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = { preferences.chooseAppearance(if (dark) "light" else "dark") },
        modifier = modifier.semantics { contentDescription = action }
    ) {
        Canvas(Modifier.size(24.dp)) {
            if (dark) drawSun(color) else drawMoon(color)
        }
    }
}

private fun DrawScope.drawSun(color: androidx.compose.ui.graphics.Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.21f
    drawCircle(color, radius, center)
    for (ray in 0 until 8) {
        val angle = ray * Math.PI / 4.0
        val inner = size.minDimension * 0.34f
        val outer = size.minDimension * 0.46f
        drawLine(color,
            start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
            end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawMoon(color: androidx.compose.ui.graphics.Color) {
    val unit = size.minDimension / 24f
    val crescent = Path().apply {
        moveTo(19.4f * unit, 15.5f * unit)
        cubicTo(18.1f * unit, 16.1f * unit, 16.7f * unit, 16.3f * unit,
            15.3f * unit, 16.0f * unit)
        cubicTo(11.2f * unit, 15.1f * unit, 8.7f * unit, 11.3f * unit,
            9.6f * unit, 7.2f * unit)
        cubicTo(9.9f * unit, 5.8f * unit, 10.6f * unit, 4.5f * unit,
            11.5f * unit, 3.5f * unit)
        cubicTo(6.9f * unit, 3.8f * unit, 3.3f * unit, 7.7f * unit,
            3.3f * unit, 12.3f * unit)
        cubicTo(3.3f * unit, 17.3f * unit, 7.3f * unit, 21.0f * unit,
            12.1f * unit, 21.0f * unit)
        cubicTo(15.4f * unit, 21.0f * unit, 18.1f * unit, 19.0f * unit,
            19.4f * unit, 15.5f * unit)
        close()
    }
    drawPath(crescent, color)
}
