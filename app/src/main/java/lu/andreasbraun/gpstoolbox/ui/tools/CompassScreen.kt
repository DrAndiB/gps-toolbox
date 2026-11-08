package lu.andreasbraun.gpstoolbox.ui.tools

import android.hardware.GeomagneticField
import android.hardware.SensorManager
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.core.Geo
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import lu.andreasbraun.gpstoolbox.ui.Panel
import lu.andreasbraun.gpstoolbox.ui.Pill
import lu.andreasbraun.gpstoolbox.ui.ScreenHeader
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

fun headingAccuracyLabel(accuracy: Int): String = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
    else -> "Unreliable"
}

@Composable
fun CompassScreen(onBack: () -> Unit) {
    val sensors by SensorEngine.state.collectAsStateWithLifecycle()
    val gnss by GnssEngine.state.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val fix = gnss.fix
    val declination = fix?.let {
        GeomagneticField(it.lat.toFloat(), it.lon.toFloat(), (it.altitude ?: 0.0).toFloat(), System.currentTimeMillis()).declination
    }
    val magnetic = sensors.headingMagnetic
    val useTrue = settings.trueNorth && declination != null
    val heading = magnetic?.let { if (useTrue) Geo.normalize(it + declination!!) else it }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Compass",
            subtitle = when {
                useTrue -> String.format(Locale.ROOT, "True north · declination %+.1f°", declination)
                settings.trueNorth -> "True north needs a fix · showing magnetic"
                else -> "Magnetic north"
            },
            onBack = onBack,
        )
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                heading?.let { String.format(Locale.ROOT, "%.0f°", it) } ?: "—",
                fontFamily = Mono, fontSize = 56.sp, fontWeight = FontWeight.Medium, color = C.Text,
            )
            Text(heading?.let { Geo.cardinal(it.toDouble()) } ?: "No compass on this phone", fontSize = 18.sp, color = C.Muted)
            CompassRose(heading ?: 0f, Modifier.fillMaxWidth(0.9f).aspectRatio(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("True north", selected = settings.trueNorth, onClick = { Prefs.update { it.copy(trueNorth = true) } })
                Pill("Magnetic", selected = !settings.trueNorth, onClick = { Prefs.update { it.copy(trueNorth = false) } })
            }
            Panel(Modifier.fillMaxWidth(), spacing = 6.dp) {
                val acc = sensors.headingAccuracy
                Row {
                    Text("Calibration", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Text, modifier = Modifier.weight(1f))
                    Text(
                        headingAccuracyLabel(acc), fontSize = 14.sp,
                        color = if (acc >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) C.Accent else C.Amber,
                    )
                }
                if (acc < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                    Text(
                        "Move the phone in a slow figure-8 a few times. Keep it away from metal, magnets and laptop speakers.",
                        fontSize = 13.sp, color = C.TextSoft,
                    )
                }
                val course = fix?.bearing?.takeIf { (fix?.speed ?: 0f) > 1f }
                Text(
                    if (course != null) String.format(Locale.ROOT, "GPS course %.0f° %s (needs movement, not affected by magnets)", course, Geo.cardinal(course.toDouble()))
                    else "GPS course appears when you are moving. It is not affected by magnetic interference.",
                    fontSize = 12.sp, color = C.Muted,
                )
            }
            Text("Hold the phone flat for the most accurate reading.", fontSize = 12.sp, color = C.Faint)
        }
    }
}

@Composable
private fun CompassRose(heading: Float, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2 - 6.dp.toPx()
        drawCircle(C.Surface, r, c)
        drawCircle(C.Line, r, c, style = Stroke(1.dp.toPx()))

        fun at(angleDeg: Float, radius: Float): Offset {
            val a = Math.toRadians((angleDeg - heading).toDouble())
            return Offset(c.x + radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
        }

        for (deg in 0 until 360 step 5) {
            val major = deg % 30 == 0
            val inner = r - if (major) 16.dp.toPx() else 8.dp.toPx()
            drawLine(if (major) C.TextSoft else C.Grid, at(deg.toFloat(), inner), at(deg.toFloat(), r - 2.dp.toPx()), if (major) 2.dp.toPx() else 1.dp.toPx())
        }
        val labels = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W", 30 to "30", 60 to "60", 120 to "120", 150 to "150", 210 to "210", 240 to "240", 300 to "300", 330 to "330")
        for ((deg, text) in labels) {
            val cardinal = text.length == 1
            val style = TextStyle(
                fontSize = if (cardinal) 20.sp else 11.sp,
                fontWeight = if (cardinal) FontWeight.SemiBold else FontWeight.Normal,
                color = if (text == "N") C.Red else if (cardinal) C.Text else C.Muted,
            )
            val layout = tm.measure(text, style)
            val p = at(deg.toFloat(), r - 34.dp.toPx())
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        // North needle (rotates with the rose) and fixed lubber line at the top.
        val needle = Path().apply {
            val tip = at(0f, r * 0.5f)
            val left = at(270f, 7.dp.toPx())
            val right = at(90f, 7.dp.toPx())
            moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
        }
        drawPath(needle, C.Red)
        val tail = Path().apply {
            val tip = at(180f, r * 0.5f)
            val left = at(270f, 7.dp.toPx())
            val right = at(90f, 7.dp.toPx())
            moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
        }
        drawPath(tail, C.Grid)
        drawCircle(C.Text, 5.dp.toPx(), c)
        val lubber = Path().apply {
            moveTo(c.x, c.y - r - 2.dp.toPx())
            lineTo(c.x - 8.dp.toPx(), c.y - r + 12.dp.toPx())
            lineTo(c.x + 8.dp.toPx(), c.y - r + 12.dp.toPx())
            close()
        }
        drawPath(lubber, C.Accent)
    }
}
