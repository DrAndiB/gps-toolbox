package lu.andreasbraun.gpstoolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.core.Band
import lu.andreasbraun.gpstoolbox.core.Constellation
import lu.andreasbraun.gpstoolbox.core.Satellite
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkyScreen() {
    val gnss by GnssEngine.state.collectAsStateWithLifecycle()
    val sensors by SensorEngine.state.collectAsStateWithLifecycle()
    var headingUp by rememberSaveable { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(emptySet<Constellation>()) }
    var band by remember { mutableStateOf(Band.L1) }

    val tracked = gnss.satellites.filter { it.tracked }
    val present = tracked.map { it.constellation }.distinct().sortedBy { it.ordinal }
    val visible = tracked.filter { it.constellation !in hidden }
    val heading = trueHeading(sensors.headingMagnetic, gnss.fix)
    val bands = gnss.bandsInUse.ifEmpty { listOf(Band.L1) }
    val shownBand = if (band in bands) band else bands.first()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Sky & signals",
            subtitle = "${gnss.tracked} in view · ${gnss.used} in fix",
            actions = {
                Pill(if (headingUp) "Heading-up" else "North-up", selected = false, onClick = {
                    headingUp = !headingUp
                    Telemetry.event("sky_orientation", "heading_up" to headingUp)
                })
            },
        )
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (present.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in present) {
                        Pill(c.label, selected = c !in hidden, color = Color(c.argb), onClick = {
                            hidden = if (c in hidden) hidden - c else hidden + c
                            Telemetry.event("sky_filter", "system" to c.label, "shown" to (c !in hidden))
                        })
                    }
                }
            }

            Panel(radius = 20.dp, padding = 12.dp) {
                SkyPlot(
                    satellites = visible,
                    heading = heading,
                    headingUp = headingUp && heading != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Text(
                    if (headingUp && heading == null) "No compass reading, showing north-up" else "Filled = used in fix · ring = tracked only",
                    fontSize = 12.sp, color = C.Muted,
                )
            }

            Panel {
                val withBand = visible.mapNotNull { s -> s.cn0On(shownBand)?.let { s to it } }
                val avg = if (withBand.isEmpty()) null else withBand.map { it.second }.average()
                val best = withBand.maxOfOrNull { it.second }
                PanelTitle(
                    "C/N₀ (dB-Hz)",
                    if (avg != null) String.format(Locale.ROOT, "avg %.1f · best %.0f", avg, best) else null,
                )
                if (bands.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (b in bands) {
                            Pill(b.label, selected = b == shownBand, onClick = {
                                band = b
                                Telemetry.event("sky_band", "band" to b.name)
                            })
                        }
                    }
                }
                Cn0Bars(withBand, shownBand, Modifier.fillMaxWidth().height(140.dp))
                Text("Grid lines at 20, 30 and 40 dB-Hz. Above 35 is a strong signal.", fontSize = 12.sp, color = C.Muted)
            }

            Panel(spacing = 4.dp) {
                PanelTitle("Satellite list", "${visible.size} shown")
                SatelliteTable(visible)
            }
        }
    }
}

private fun DrawScope.centeredText(tm: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val layout = tm.measure(text, style)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

@Composable
fun SkyPlot(satellites: List<Satellite>, heading: Float?, headingUp: Boolean, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    val cardinalStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
    val ringStyle = TextStyle(fontFamily = Mono, fontSize = 9.sp, color = C.Faint)
    Canvas(modifier) {
        val margin = 18.dp.toPx()
        val r = size.minDimension / 2 - margin
        val c = center
        val rot = if (headingUp && heading != null) -heading else 0f

        fun polar(az: Float, radius: Float): Offset {
            val a = Math.toRadians((az + rot).toDouble())
            return Offset(c.x + radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
        }
        fun pos(az: Float, el: Float): Offset = polar(az, r * (90f - el.coerceIn(0f, 90f)) / 90f)

        drawCircle(C.Deep, r, c)
        drawCircle(C.Grid, r, c, style = Stroke(1.dp.toPx()))
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        drawCircle(C.Grid, r * 2 / 3, c, style = Stroke(1.dp.toPx(), pathEffect = dash))
        drawCircle(C.Grid, r / 3, c, style = Stroke(1.dp.toPx(), pathEffect = dash))
        drawLine(C.Grid, pos(0f, 0f), pos(180f, 0f), 1.dp.toPx())
        drawLine(C.Grid, pos(90f, 0f), pos(270f, 0f), 1.dp.toPx())

        val labelR = r + margin / 2 + 2.dp.toPx()
        centeredText(tm, "N", polar(0f, labelR), cardinalStyle.copy(color = C.Red))
        centeredText(tm, "E", polar(90f, labelR), cardinalStyle.copy(color = C.Muted))
        centeredText(tm, "S", polar(180f, labelR), cardinalStyle.copy(color = C.Muted))
        centeredText(tm, "W", polar(270f, labelR), cardinalStyle.copy(color = C.Muted))
        centeredText(tm, "60°", Offset(c.x + 12.dp.toPx(), c.y - r / 3 + 7.dp.toPx()), ringStyle)
        centeredText(tm, "30°", Offset(c.x + 12.dp.toPx(), c.y - r * 2 / 3 + 7.dp.toPx()), ringStyle)

        if (!headingUp && heading != null) {
            drawLine(C.Text, c, polar(heading, r * 0.32f), 2.dp.toPx(), cap = StrokeCap.Round)
        }

        val dot = 11.dp.toPx()
        // Draw unused first so used satellites stay on top.
        for (s in satellites.sortedBy { it.usedInFix }) {
            if (s.elevation < 0f) continue
            val p = pos(s.azimuth, s.elevation)
            val color = Color(s.constellation.argb)
            if (s.usedInFix) {
                drawCircle(color, dot, p)
                centeredText(tm, s.label, p, labelStyle.copy(color = C.Bg))
            } else {
                drawCircle(C.Deep, dot, p)
                drawCircle(color, dot, p, style = Stroke(1.5.dp.toPx()))
                centeredText(tm, s.label, p, labelStyle.copy(color = color))
            }
        }
        drawCircle(C.Text, 4.dp.toPx(), c)
    }
}

@Composable
private fun Cn0Bars(entries: List<Pair<Satellite, Float>>, band: Band, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Mono, fontSize = 8.sp, color = C.Muted)
    Canvas(modifier) {
        val labelH = 14.dp.toPx()
        val plotH = size.height - labelH
        val maxCn0 = 55f
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        for (level in listOf(20f, 30f, 40f)) {
            val y = plotH - plotH * level / maxCn0
            drawLine(C.Grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
        }
        drawLine(C.Grid, Offset(0f, plotH), Offset(size.width, plotH), 1.dp.toPx())
        if (entries.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val n = entries.size
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtMost(28.dp.toPx())
        val showLabels = barW >= 14.dp.toPx()
        entries.forEachIndexed { i, (sat, cn0) ->
            val x = i * (barW + gap)
            val h = plotH * (cn0 / maxCn0).coerceIn(0.02f, 1f)
            val color = Color(sat.constellation.argb)
            val usedOnBand = sat.signals.any { it.band == band && it.usedInFix }
            val topLeft = Offset(x, plotH - h)
            val sz = androidx.compose.ui.geometry.Size(barW, h)
            if (usedOnBand) {
                drawRect(color, topLeft, sz)
            } else {
                val sw = 1.5.dp.toPx()
                drawRect(color, Offset(x + sw / 2, plotH - h + sw / 2), androidx.compose.ui.geometry.Size(barW - sw, h - sw / 2), style = Stroke(sw))
            }
            if (showLabels) centeredText(tm, sat.svid.toString(), Offset(x + barW / 2, plotH + labelH / 2 + 1.dp.toPx()), labelStyle)
        }
    }
}

@Composable
private fun SatelliteTable(sats: List<Satellite>) {
    val head = TextStyle(fontSize = 11.sp, color = C.Muted)
    val cell = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = C.TextSoft)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Sat", style = head, modifier = Modifier.weight(1.1f))
        Text("El", style = head, modifier = Modifier.weight(0.8f))
        Text("Az", style = head, modifier = Modifier.weight(0.9f))
        Text("L1", style = head, modifier = Modifier.weight(0.8f))
        Text("L5", style = head, modifier = Modifier.weight(0.8f))
        Text("Fix", style = head, modifier = Modifier.weight(0.7f))
        Text("Eph", style = head, modifier = Modifier.width(32.dp))
    }
    if (sats.isEmpty()) {
        Text("No satellites tracked.", fontSize = 13.sp, color = C.Muted)
    }
    for (s in sats) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(s.label, style = cell.copy(color = Color(s.constellation.argb), fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1.1f))
            Text(String.format(Locale.ROOT, "%.0f°", s.elevation), style = cell, modifier = Modifier.weight(0.8f))
            Text(String.format(Locale.ROOT, "%.0f°", s.azimuth), style = cell, modifier = Modifier.weight(0.9f))
            Text(s.cn0On(Band.L1)?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "–", style = cell, modifier = Modifier.weight(0.8f))
            Text(s.cn0On(Band.L5)?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "–", style = cell, modifier = Modifier.weight(0.8f))
            Text(if (s.usedInFix) "●" else "○", style = cell.copy(color = if (s.usedInFix) C.Accent else C.Faint), modifier = Modifier.weight(0.7f))
            Text(if (s.hasEphemeris) "✓" else "–", style = cell, modifier = Modifier.width(32.dp))
        }
    }
}
