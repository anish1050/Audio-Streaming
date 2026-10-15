package com.anish1050.hires.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.anish1050.hires.ui.theme.CardBorder
import com.anish1050.hires.ui.theme.Graphite
import com.anish1050.hires.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

private const val TICKS = 48
private const val SWEEP_DEGREES = 150f

/**
 * The mockup's tuner dial: a fixed needle at the top and an arc of ticks that
 * turns with playback. Drag sideways to seek.
 */
@Composable
fun DialScrubber(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val shown = dragProgress ?: progress
    Canvas(
        modifier.fillMaxWidth().height(120.dp).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { dragProgress = progress },
                onDragEnd = { dragProgress?.let(onSeek); dragProgress = null },
                onDragCancel = { dragProgress = null },
                onHorizontalDrag = { _, dragAmount -> dragProgress = ((dragProgress ?: progress) - dragAmount / size.width).coerceIn(0f, 1f) },
            )
        },
    ) {
        val center = Offset(size.width / 2, size.height + size.width * 0.35f)
        val radius = size.width * 0.75f
        drawCircle(CardBorder, radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        // Ticks rotate left as the track plays; the one under the needle is "now".
        repeat(TICKS) { i ->
            val angle = Math.toRadians((-90.0 - SWEEP_DEGREES / 2 + SWEEP_DEGREES * (i.toFloat() / TICKS) - (shown - 0.5) * SWEEP_DEGREES))
            val isMajor = i % 6 == 0
            val inner = radius - (if (isMajor) 14 else 8).dp.toPx()
            drawLine(
                if (isMajor) TextPrimary.copy(alpha = 0.35f) else Graphite,
                Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat()),
                strokeWidth = 1.2.dp.toPx(),
            )
        }
        val top = center.y - radius
        drawLine(TextPrimary, Offset(center.x, top - 22.dp.toPx()), Offset(center.x, top - 4.dp.toPx()), strokeWidth = 2.5.dp.toPx())
        drawLine(TextPrimary.copy(alpha = 0.25f), Offset(center.x, top + 2.dp.toPx()), Offset(center.x, size.height), strokeWidth = 1.dp.toPx())
    }
}
