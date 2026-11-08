package lu.andreasbraun.gpstoolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.core.AccuracySample
import lu.andreasbraun.gpstoolbox.core.Coordinates
import lu.andreasbraun.gpstoolbox.data.AvgResult
import lu.andreasbraun.gpstoolbox.data.Averager
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.data.WaypointStore
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono
import lu.andreasbraun.gpstoolbox.util.Share
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Grid spacing (metres) and half-width of the scatter plot for a given spread. */
fun scatterScale(result: AvgResult?): Pair<Double, Double> {
    val spread = max(result?.r95 ?: 0.0, 0.5) * 1.35
    val steps = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    val step = steps.firstOrNull { spread / it <= 4 } ?: 1000.0
    val half = ceil(spread / step) * step
    return step to half
}

@Composable
fun AccuracyScreen() {
    val context = LocalContext.current
    val avg by Averager.state.collectAsStateWithLifecycle()
    val history by GnssEngine.accuracyHistory.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val now by rememberElapsedMs(1000L)
    val result = avg.result
    val elapsed = (if (avg.running) now else (avg.stoppedElapsedMs ?: now)) - avg.startedElapsedMs

    val subtitle = when {
        avg.running -> "Averaging · ${Fmt.duration(elapsed)} running"
        result != null -> "${result.count} fixes over ${Fmt.duration(elapsed)}"
        else -> "Average many fixes for a precise point"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Accuracy",
            subtitle = subtitle,
            actions = {
                if (avg.running) {
                    ActionButton("Stop", onClick = { Averager.stop() }, color = C.Red, height = 40.dp)
                } else {
                    ActionButton(if (result != null) "Restart" else "Start", onClick = { Averager.start(context) }, height = 40.dp)
                }
            },
        )
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (step, _) = scatterScale(result)
            Panel(radius = 20.dp) {
                PanelTitle(
                    "Fix scatter",
                    if (result != null) "${result.count} fixes · grid ${Fmt.num(step, if (step < 1) 1 else 0)} m" else null,
                )
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    ScatterPlot(result, Modifier.fillMaxSize())
                    if (result == null) {
                        Text(
                            "Tap Start and keep the phone still.\nEach fix appears as a dot around the average.",
                            fontSize = 13.sp, color = C.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                if (avg.rejected > 0) {
                    Text("${avg.rejected} fixes worse than 30 m were ignored.", fontSize = 12.sp, color = C.Muted)
                }
            }

            Panel(spacing = 8.dp) {
                SectionLabel("Averaged position")
                Text(
                    result?.let { Coordinates.singleLine(it.lat, it.lon, settings.coordFormat) } ?: "—",
                    fontFamily = Mono, fontSize = 17.sp, color = C.Text,
                )
                Row(Modifier.fillMaxWidth()) {
                    Metric("CEP50", Fmt.meters(result?.cep50), Modifier.weight(1f))
                    Metric("R95", Fmt.meters(result?.r95), Modifier.weight(1f))
                    Metric("Altitude", result?.altitude?.let { Fmt.num(it, 1) + " m" } ?: "—", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Metric("Std. error*", result?.let { String.format(Locale.ROOT, "%.2f m", it.stdError) } ?: "—", Modifier.weight(1f))
                    Metric("Drift", Fmt.meters(result?.drift), Modifier.weight(1f))
                    Metric("Fixes", result?.count?.toString() ?: "0", Modifier.weight(1f))
                }
                Text(
                    "*Assumes independent fixes. Consecutive fixes are correlated, so the real uncertainty is larger; " +
                        "averaging for 10 minutes or more helps most.",
                    fontSize = 11.sp, color = C.Faint,
                )
            }

            Panel {
                val values = history.map { it.horizontal }
                PanelTitle(
                    "Horizontal accuracy · 10 min",
                    if (values.isNotEmpty()) String.format(Locale.ROOT, "min %.1f · max %.1f m", values.min(), values.max()) else null,
                )
                HistoryChart(history, now, Modifier.fillMaxWidth().height(80.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    "Save as waypoint",
                    enabled = result != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        result?.let {
                            WaypointStore.add("Avg ${WaypointStore.nextName()}", it.lat, it.lon, it.altitude, it.cep50.toFloat())
                            Share.toast(context, "Waypoint saved")
                        }
                    },
                )
                ActionButton(
                    "Export CSV",
                    primary = false,
                    enabled = result != null,
                    modifier = Modifier.weight(1f),
                    onClick = { Share.text(context, "averaging.csv", Averager.csv(), "text/csv") },
                )
            }
        }
    }
}

@Composable
private fun ScatterPlot(result: AvgResult?, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val (step, half) = scatterScale(result)
    Canvas(modifier) {
        val c = center
        val pxPerM = (size.minDimension / 2f) / half.toFloat()
        // Grid
        var k = 0
        while (k * step <= half + 1e-9) {
            val d = (k * step).toFloat() * pxPerM
            for (sign in listOf(-1f, 1f)) {
                if (k == 0 && sign < 0) continue
                drawLine(C.Line, Offset(c.x + sign * d, 0f), Offset(c.x + sign * d, size.height), 1f)
                drawLine(C.Line, Offset(0f, c.y + sign * d), Offset(size.width, c.y + sign * d), 1f)
            }
            k++
        }
        if (result == null) return@Canvas

        val points = result.offsets
        for ((i, p) in points.withIndex()) {
            if (abs(p.first) > half || abs(p.second) > half) continue
            val o = Offset(c.x + p.first * pxPerM, c.y - p.second * pxPerM)
            val latest = i == points.lastIndex
            drawCircle(if (latest) C.Amber else C.Blue.copy(alpha = 0.7f), if (latest) 3.5.dp.toPx() else 2.2.dp.toPx(), o)
        }
        val cep = result.cep50.toFloat() * pxPerM
        val r95 = result.r95.toFloat() * pxPerM
        drawCircle(C.Accent, cep, c, style = Stroke(1.5.dp.toPx()))
        drawCircle(C.Accent, r95, c, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
        val arm = 8.dp.toPx()
        drawLine(C.Text, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 2.dp.toPx())
        drawLine(C.Text, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 2.dp.toPx())

        val style = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = C.Accent)
        drawText(tm, String.format(Locale.ROOT, "CEP50 %.1f m", result.cep50), Offset(c.x + cep * 0.72f + 4.dp.toPx(), c.y - cep * 0.72f - 14.dp.toPx()), style)
        drawText(tm, String.format(Locale.ROOT, "R95 %.1f m", result.r95), Offset(c.x + r95 * 0.72f + 4.dp.toPx(), c.y + r95 * 0.72f), style)
    }
}

@Composable
private fun HistoryChart(history: List<AccuracySample>, nowMs: Long, modifier: Modifier) {
    Canvas(modifier) {
        drawLine(C.Grid, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
        if (history.size < 2) return@Canvas
        val span = 10 * 60 * 1000f
        val maxV = max(history.maxOf { it.horizontal }, 5f) * 1.1f
        val path = Path()
        history.forEachIndexed { i, s ->
            val x = size.width * (1f - ((nowMs - s.elapsedMs) / span)).coerceIn(0f, 1f)
            val y = size.height - size.height * (s.horizontal / maxV)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, C.Accent, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}
