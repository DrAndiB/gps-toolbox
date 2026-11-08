package lu.andreasbraun.gpstoolbox.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import lu.andreasbraun.gpstoolbox.engine.Vec3
import lu.andreasbraun.gpstoolbox.ui.ListRow
import lu.andreasbraun.gpstoolbox.ui.Panel
import lu.andreasbraun.gpstoolbox.ui.ScreenHeader
import lu.andreasbraun.gpstoolbox.ui.SectionLabel
import lu.andreasbraun.gpstoolbox.ui.theme.C
import java.util.Locale

private fun xyz(v: Vec3?, digits: Int = 1): String? =
    v?.let { String.format(Locale.ROOT, "x %.${digits}f · y %.${digits}f · z %.${digits}f", it.x, it.y, it.z) }

@Composable
fun SensorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by SensorEngine.state.collectAsStateWithLifecycle()
    val receiver = remember {
        val lm = context.getSystemService(LocationManager::class.java)
        val model = lm.gnssHardwareModelName ?: "Not reported by this phone"
        val year = lm.gnssYearOfHardware
        model to (if (year > 0) year.toString() else "Not reported")
    }

    fun missing(type: Int) = type !in s.available

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Sensors", "Live readings", onBack = onBack)
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(spacing = 0.dp) {
                ListRow(
                    "Barometer",
                    s.pressureHpa?.let {
                        String.format(Locale.ROOT, "≈ %.0f m in the standard atmosphere (weather shifts this)", SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it))
                    } ?: if (missing(Sensor.TYPE_PRESSURE)) "Not available" else "Waiting…",
                    s.pressureHpa?.let { String.format(Locale.ROOT, "%.1f hPa", it) },
                    C.Text,
                )
                HorizontalDivider(color = C.Line)
                ListRow(
                    "Magnetic field",
                    xyz(s.magnetic) ?: if (missing(Sensor.TYPE_MAGNETIC_FIELD)) "Not available" else "Waiting…",
                    s.magnetic?.let { String.format(Locale.ROOT, "%.1f µT", it.magnitude) },
                    C.Text,
                )
                HorizontalDivider(color = C.Line)
                ListRow(
                    "Accelerometer",
                    xyz(s.acceleration, 2)?.let { "$it m/s²" } ?: if (missing(Sensor.TYPE_ACCELEROMETER)) "Not available" else "Waiting…",
                    s.acceleration?.let { String.format(Locale.ROOT, "%.2f g", it.magnitude / SensorManager.GRAVITY_EARTH) },
                    C.Text,
                )
                HorizontalDivider(color = C.Line)
                ListRow(
                    "Gyroscope",
                    xyz(s.rotationRate)?.let { "$it °/s" } ?: if (missing(Sensor.TYPE_GYROSCOPE)) "Not available" else "Waiting…",
                    s.rotationRate?.let { String.format(Locale.ROOT, "%.1f °/s", it.magnitude) },
                    C.Text,
                )
                HorizontalDivider(color = C.Line)
                ListRow(
                    "Light",
                    if (missing(Sensor.TYPE_LIGHT)) "Not available" else "Ambient light sensor",
                    s.lightLux?.let { String.format(Locale.ROOT, "%.0f lx", it) },
                    C.Text,
                )
                HorizontalDivider(color = C.Line)
                ListRow(
                    "Compass calibration",
                    "Magnetometer accuracy reported by Android",
                    headingAccuracyLabel(s.headingAccuracy),
                    if (s.headingAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) C.Accent else C.Amber,
                )
            }

            SectionLabel("GNSS receiver")
            Panel(spacing = 0.dp) {
                ListRow("Chipset", receiver.first)
                HorizontalDivider(color = C.Line)
                ListRow("Hardware year", receiver.second)
            }
            Text("Raw measurement support shows up in Raw log: if records appear there, the phone provides them.", fontSize = 12.sp, color = C.Faint)
        }
    }
}
