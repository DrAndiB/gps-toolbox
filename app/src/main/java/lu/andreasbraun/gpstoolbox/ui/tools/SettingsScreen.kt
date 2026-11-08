package lu.andreasbraun.gpstoolbox.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.BuildConfig
import lu.andreasbraun.gpstoolbox.core.CoordFormat
import lu.andreasbraun.gpstoolbox.data.Agnss
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.ui.Panel
import lu.andreasbraun.gpstoolbox.ui.Pill
import lu.andreasbraun.gpstoolbox.ui.ScreenHeader
import lu.andreasbraun.gpstoolbox.ui.SectionLabel
import lu.andreasbraun.gpstoolbox.ui.ToggleRow
import lu.andreasbraun.gpstoolbox.ui.theme.C

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Settings", "GPS Toolbox ${BuildConfig.VERSION_NAME}", onBack = onBack)
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Coordinate format")
            Panel {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in CoordFormat.entries) {
                        Pill(f.label, selected = s.coordFormat == f, onClick = { Prefs.update { it.copy(coordFormat = f) } })
                    }
                }
                Text(
                    "DD decimal degrees · DDM degrees and decimal minutes · DMS degrees, minutes, seconds · UTM · MGRS (military grid). " +
                        "Tapping the format on the Now screen cycles through them too.",
                    fontSize = 12.sp, color = C.Muted,
                )
            }

            SectionLabel("Assistance data (A‑GNSS)")
            Panel(spacing = 0.dp) {
                ToggleRow("Refresh automatically", "In the background, even when the app is closed", s.agnssAuto) { on ->
                    Prefs.update { it.copy(agnssAuto = on) }
                    Agnss.schedule(context)
                }
                if (s.agnssAuto) {
                    HorizontalDivider(color = C.Line)
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Every", fontSize = 13.sp, color = C.Muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (h in listOf(3, 6, 12, 24)) {
                                Pill("$h h", selected = s.agnssIntervalHours == h, onClick = {
                                    Prefs.update { it.copy(agnssIntervalHours = h) }
                                    Agnss.schedule(context)
                                })
                            }
                        }
                    }
                    HorizontalDivider(color = C.Line)
                    ToggleRow("Only on Wi‑Fi", "Saves mobile data; the download is small either way", s.agnssWifiOnly) { on ->
                        Prefs.update { it.copy(agnssWifiOnly = on) }
                        Agnss.schedule(context)
                    }
                }
            }
            Text(
                "Android downloads predicted satellite orbits and time when asked. Fresh data lets the receiver find satellites " +
                    "in seconds even without reception. Predicted orbits typically last up to a week, depending on the chipset. " +
                    "Android may delay background refreshes to save battery.",
                fontSize = 12.sp, color = C.Faint,
            )

            SectionLabel("Display")
            Panel(spacing = 0.dp) {
                ToggleRow("Keep screen on", "While the app is open", s.keepScreenOn) { on -> Prefs.update { it.copy(keepScreenOn = on) } }
                HorizontalDivider(color = C.Line)
                ToggleRow("Compass shows true north", "Corrects for magnetic declination at your position", s.trueNorth) { on ->
                    Prefs.update { it.copy(trueNorth = on) }
                }
            }

            SectionLabel("About")
            Panel {
                Text(
                    "No ads, no account, no tracking. The app has no internet permission: all data stays on this phone " +
                        "unless you share or export it.",
                    fontSize = 13.sp, color = C.TextSoft,
                )
            }
        }
    }
}
