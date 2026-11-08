package lu.andreasbraun.gpstoolbox.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import lu.andreasbraun.gpstoolbox.ui.ActionButton
import lu.andreasbraun.gpstoolbox.ui.Metric
import lu.andreasbraun.gpstoolbox.ui.Panel
import lu.andreasbraun.gpstoolbox.ui.ScreenHeader
import lu.andreasbraun.gpstoolbox.ui.theme.C
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private const val RANGE_DEG = 10f
private const val RING_DEG = 2.5f

@Composable
fun LevelScreen(onBack: () -> Unit) {
    val sensors by SensorEngine.state.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val g = sensors.gravity

    // Roll: right edge down is negative. Pitch: top edge down is negative.
    val rawRoll = g?.let { Math.toDegrees(atan2(it.x.toDouble(), it.z.toDouble())).toFloat() }
    val rawPitch = g?.let { Math.toDegrees(atan2(it.y.toDouble(), it.z.toDouble())).toFloat() }
    val roll = rawRoll?.minus(settings.levelOffsetX)
    val pitch = rawPitch?.minus(settings.levelOffsetY)
    val level = roll != null && pitch != null && abs(roll) < 0.3f && abs(pitch) < 0.3f

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Level",
            subtitle = if (settings.levelOffsetX != 0f || settings.levelOffsetY != 0f) "Custom zero set" else "Lay the phone on the surface",
            onBack = onBack,
        )
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.fillMaxWidth(0.85f).aspectRatio(1f)) {
                val c = center
                val r = size.minDimension / 2 - 4.dp.toPx()
                val bubbleR = r * 0.16f
                drawCircle(C.Surface, r, c)
                drawCircle(if (level) C.Accent else C.Line, r, c, style = Stroke(2.dp.toPx()))
                // Rings at 2.5°, 5° and 7.5° of tilt.
                for (k in 1..3) drawCircle(C.Grid, (k * RING_DEG / RANGE_DEG) * (r - bubbleR), c, style = Stroke(1.dp.toPx()))
                drawLine(C.Grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
                drawLine(C.Grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
                if (roll != null && pitch != null) {
                    var dx = (roll / RANGE_DEG) * (r - bubbleR)
                    var dy = (-pitch / RANGE_DEG) * (r - bubbleR)
                    val d = hypot(dx, dy)
                    val maxD = r - bubbleR
                    if (d > maxD) { dx = dx / d * maxD; dy = dy / d * maxD }
                    val color = if (level) C.Accent else C.Amber
                    drawCircle(color.copy(alpha = 0.35f), bubbleR, Offset(c.x + dx, c.y + dy))
                    drawCircle(color, bubbleR, Offset(c.x + dx, c.y + dy), style = Stroke(2.dp.toPx()))
                }
                drawCircle(C.TextSoft, min(6.dp.toPx(), r * 0.04f), c)
            }
            Text(
                if (level) "Level" else "Rings every 2.5° of tilt",
                fontSize = 14.sp, color = if (level) C.Accent else C.Muted,
            )
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Metric("Roll (left / right)", roll?.let { String.format(Locale.ROOT, "%+.1f°", it) } ?: "—", Modifier.weight(1f))
                    Metric("Pitch (top / bottom)", pitch?.let { String.format(Locale.ROOT, "%+.1f°", it) } ?: "—", Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    "Set as zero",
                    enabled = rawRoll != null,
                    modifier = Modifier.weight(1f),
                    onClick = { Prefs.update { it.copy(levelOffsetX = rawRoll ?: 0f, levelOffsetY = rawPitch ?: 0f) } },
                )
                ActionButton(
                    "Reset zero",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = { Prefs.update { it.copy(levelOffsetX = 0f, levelOffsetY = 0f) } },
                )
            }
            Text(
                "Use \"Set as zero\" on a surface you know is level to cancel out a phone case or a camera bump.",
                fontSize = 12.sp, color = C.Faint,
            )
        }
    }
}
