package lu.andreasbraun.gpstoolbox.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lu.andreasbraun.gpstoolbox.AppScope
import lu.andreasbraun.gpstoolbox.BuildConfig
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import lu.andreasbraun.gpstoolbox.ui.ActionButton
import lu.andreasbraun.gpstoolbox.ui.Fmt
import lu.andreasbraun.gpstoolbox.ui.fieldColors
import lu.andreasbraun.gpstoolbox.ui.rememberWallClock
import lu.andreasbraun.gpstoolbox.util.Share
import lu.andreasbraun.gpstoolbox.core.CoordFormat
import lu.andreasbraun.gpstoolbox.data.Agnss
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.ui.Panel
import lu.andreasbraun.gpstoolbox.ui.Pill
import lu.andreasbraun.gpstoolbox.ui.ScreenHeader
import lu.andreasbraun.gpstoolbox.ui.SectionLabel
import lu.andreasbraun.gpstoolbox.ui.ToggleRow
import lu.andreasbraun.gpstoolbox.ui.theme.C

@Composable
private fun TelemetrySection() {
    val context = LocalContext.current
    val t by Telemetry.state.collectAsStateWithLifecycle()
    val wall by rememberWallClock(10_000L)
    var repo by rememberSaveable(t.repo) { mutableStateOf(t.repo) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Telemetry.refreshPending() } }

    SectionLabel("Usage data")
    Panel(spacing = 0.dp) {
        ToggleRow(
            "Collect usage data",
            "Opt-in · uploaded to your own private GitHub repository",
            t.enabled,
        ) { on -> Telemetry.setEnabled(context, on) }
        if (t.enabled) {
            HorizontalDivider(color = C.Line)
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it.trim() },
                    singleLine = true,
                    label = { Text("Private repository (owner/name)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    singleLine = true,
                    label = { Text(if (t.hasToken) "Access token (saved, type to replace)" else "Fine-grained access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        "Save",
                        enabled = repo != t.repo || token.isNotEmpty(),
                        height = 44.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Telemetry.configure(context, repo, token.ifEmpty { null })
                            token = ""
                            Share.toast(context, "Saved")
                        },
                    )
                    ActionButton(
                        if (busy) "Uploading…" else "Upload now",
                        primary = false,
                        enabled = t.hasToken && !busy,
                        height = 44.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            busy = true
                            AppScope.launch {
                                val r = Telemetry.upload()
                                busy = false
                                Share.toast(context, r.message)
                            }
                        },
                    )
                }
                val err = t.lastError
                Text(
                    when {
                        err != null -> "Last upload failed: $err"
                        t.lastUpload > 0 -> "Last upload ${Fmt.ago(t.lastUpload, wall)} · ${Fmt.size(t.pendingBytes)} waiting"
                        else -> "${Fmt.size(t.pendingBytes)} collected, not uploaded yet"
                    },
                    fontSize = 12.sp,
                    color = if (err != null) C.Red else C.Muted,
                )
            }
        }
    }
    Text(
        "Records which screens and tools you use, settings changes, GNSS quality once a minute (accuracy, satellites, " +
            "signal strength), assistance-data refreshes, time to first fix and crashes. Never coordinates, waypoint names " +
            "or file contents. Uploads a minute after you leave the app and every 6 hours. Turning it off deletes " +
            "anything not yet uploaded.",
        fontSize = 12.sp,
        color = C.Faint,
    )
}

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

            TelemetrySection()

            SectionLabel("About")
            Panel {
                Text(
                    "No ads, no account, no third-party tracking. Your position never leaves the phone unless you share " +
                        "or export it. Usage data, if you turn it on, goes only to the GitHub repository you enter above.",
                    fontSize = 13.sp, color = C.TextSoft,
                )
            }
        }
    }
}
