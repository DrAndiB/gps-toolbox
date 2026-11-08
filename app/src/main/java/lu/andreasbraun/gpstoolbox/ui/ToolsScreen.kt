package lu.andreasbraun.gpstoolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.core.Coordinates
import lu.andreasbraun.gpstoolbox.core.GnssState
import lu.andreasbraun.gpstoolbox.data.Agnss
import lu.andreasbraun.gpstoolbox.data.Background
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.data.RawLogger
import lu.andreasbraun.gpstoolbox.data.Settings
import lu.andreasbraun.gpstoolbox.data.TrackRecorder
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono
import lu.andreasbraun.gpstoolbox.util.Share
import java.util.Locale

@Composable
fun ToolsScreen(onOpen: (Tool) -> Unit) {
    val context = LocalContext.current
    val gnss by GnssEngine.state.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val agnss by Agnss.state.collectAsStateWithLifecycle()
    val track by TrackRecorder.state.collectAsStateWithLifecycle()
    val log by RawLogger.state.collectAsStateWithLifecycle()
    val wall by rememberWallClock()
    var confirmColdStart by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Tools",
            subtitle = "No account · no ads",
            actions = { IconBtn(Ic.settings, "Settings", onClick = { onOpen(Tool.SETTINGS) }) },
        )
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Live notification")
            NotificationPreview(gnss, settings)
            Panel(padding = 10.dp, spacing = 0.dp) {
                ToggleRow(
                    "Show live notification",
                    "Keeps GNSS running in the background",
                    settings.liveNotification,
                ) { on ->
                    Prefs.update { it.copy(liveNotification = on) }
                    if (on) Background.require(context, Background.Reason.NOTIFICATION) else Background.drop(Background.Reason.NOTIFICATION)
                }
            }

            val tiles = listOf(
                ToolTile(Tool.COMPASS, Ic.compass, C.Blue, "Compass", "True / magnetic, calibration"),
                ToolTile(Tool.LEVEL, Ic.level, C.Amber, "Level", "Pitch / roll, bubble"),
                ToolTile(Tool.WAYPOINTS, Ic.pin, C.Accent, "Waypoints", "Radar back to a saved spot"),
                ToolTile(
                    Tool.TRACK, Ic.track, C.Red, "Track",
                    if (track.recording) "Recording · ${Fmt.meters(track.distanceM)}" else "Record, export GPX",
                    active = track.recording,
                ),
                ToolTile(
                    Tool.LOG, Ic.log, C.TextSoft, "Raw log",
                    if (log.nmeaActive || log.rawActive) "Logging…" else "NMEA, raw GNSS measurements",
                    active = log.nmeaActive || log.rawActive,
                ),
                ToolTile(Tool.SENSORS, Ic.sensors, C.TextSoft, "Sensors", "Baro, magnetometer, IMU"),
            )
            for (pair in tiles.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (t in pair) ToolTileView(t, Modifier.weight(1f)) { onOpen(t.tool) }
                }
            }

            Panel(padding = 14.dp, spacing = 0.dp) {
                ListRow(
                    title = "Refresh assistance data (A‑GNSS)",
                    subtitle = agnssSubtitle(settings, agnss.lastRequest, wall),
                    trailing = Fmt.ago(agnss.lastRequest, wall),
                    trailingColor = agnssColor(settings, agnss.lastRequest, wall),
                    onClick = {
                        val ok = Agnss.refreshNow(context)
                        Share.toast(context, if (ok) "Refresh requested" else "This phone did not accept the request")
                    },
                )
                HorizontalDivider(color = C.Line)
                ListRow(
                    title = "Cold start / clear cache",
                    subtitle = when {
                        gnss.firstFixPending -> "Waiting for first fix…"
                        gnss.lastTtffMs != null -> String.format(Locale.ROOT, "Last time to first fix %.1f s", gnss.lastTtffMs!! / 1000.0)
                        else -> "Time to first fix test"
                    },
                    onClick = { confirmColdStart = true },
                )
            }
        }
    }

    if (confirmColdStart) {
        ConfirmDialog(
            title = "Cold start?",
            text = "Deletes stored orbits, almanac and time, then restarts GNSS so the next fix starts from scratch. " +
                "Outdoors this takes from about 30 seconds to a few minutes. Assistance data is refreshed again automatically.",
            confirm = "Clear and restart",
            destructive = true,
            onConfirm = {
                confirmColdStart = false
                val ok = Agnss.coldStart(context)
                GnssEngine.restart()
                Share.toast(context, if (ok) "Cleared, waiting for first fix" else "This phone did not accept the request")
            },
            onDismiss = { confirmColdStart = false },
        )
    }
}

fun agnssSubtitle(s: Settings, lastRequest: Long, now: Long): String {
    if (!s.agnssAuto) return "Automatic refresh off · tap to refresh now"
    val schedule = "Auto every ${s.agnssIntervalHours} h" + if (s.agnssWifiOnly) " on Wi‑Fi" else ""
    if (lastRequest <= 0) return schedule
    val days = Agnss.TYPICAL_VALIDITY_DAYS - (now - lastRequest) / 86_400_000.0
    return if (days > 0.5) "$schedule · valid ~${days.toInt()} d (est.)" else "$schedule · probably expired"
}

fun agnssColor(s: Settings, lastRequest: Long, now: Long): Color {
    if (lastRequest <= 0) return C.Amber
    val ageH = (now - lastRequest) / 3_600_000.0
    return when {
        ageH <= s.agnssIntervalHours * 2 -> C.Accent
        ageH <= 24 * 3 -> C.Amber
        else -> C.Red
    }
}

private data class ToolTile(
    val tool: Tool,
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val active: Boolean = false,
)

@Composable
private fun ToolTileView(t: ToolTile, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(C.Surface)
            .border(1.dp, if (t.active) C.Accent else C.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(t.icon, contentDescription = null, tint = t.color, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
            Text(t.subtitle, fontSize = 12.sp, color = if (t.active) C.Accent else C.Muted, maxLines = 2)
        }
    }
}

@Composable
private fun NotificationPreview(gnss: GnssState, settings: Settings) {
    val fix = gnss.fix
    val dark = Color(0xFF14181C)
    val grey = Color(0xFF4A535C)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Text)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("GPS Toolbox · live", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = grey, modifier = Modifier.weight(1f))
            Text(fix?.accuracy?.let { String.format(Locale.ROOT, "±%.1f m", it) } ?: "", fontSize = 12.sp, color = grey)
        }
        Text(
            fix?.let { Coordinates.singleLine(it.lat, it.lon, settings.coordFormat) } ?: "Waiting for fix…",
            fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = dark,
        )
        Text(
            buildString {
                append("${gnss.used}/${gnss.tracked} sats")
                fix?.speed?.let { append(String.format(Locale.ROOT, " · %.1f km/h", it * 3.6)) }
                fix?.altitude?.let { append(String.format(Locale.ROOT, " · %.0f m", it)) }
            },
            fontSize = 12.sp, color = grey,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (a in listOf("Copy", "Waypoint", "Stop")) Text(a, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0B6E5E))
        }
    }
}
