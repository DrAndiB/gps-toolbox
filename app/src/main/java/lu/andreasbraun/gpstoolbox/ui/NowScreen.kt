package lu.andreasbraun.gpstoolbox.ui

import android.hardware.GeomagneticField
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.core.CoordFormat
import lu.andreasbraun.gpstoolbox.core.Coordinates
import lu.andreasbraun.gpstoolbox.core.Fix
import lu.andreasbraun.gpstoolbox.core.Geo
import lu.andreasbraun.gpstoolbox.core.GnssState
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.data.WaypointStore
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono
import lu.andreasbraun.gpstoolbox.util.Share
import java.util.Locale
import kotlin.math.ln

fun fixLabel(g: GnssState): String {
    if (!g.running) return "GNSS off"
    val fix = g.fix ?: return "Searching · ${g.tracked} satellites"
    val type = when {
        g.fixType == 3 -> "3D"
        g.fixType == 2 -> "2D"
        fix.altitudeEllipsoid != null -> "3D"
        else -> "2D"
    }
    return "$type fix" + if (g.dualBand) " · dual-band L1+L5" else ""
}

fun statusColor(g: GnssState): Color = when {
    !g.running || !g.providerEnabled -> C.Red
    g.fix == null -> C.Amber
    g.fixType == 2 -> C.Amber
    else -> C.Accent
}

fun accuracyColor(acc: Float?): Color = when {
    acc == null -> C.Muted
    acc <= 5f -> C.Accent
    acc <= 15f -> C.Amber
    else -> C.Red
}

/** True heading from the compass, or null without a compass reading. */
fun trueHeading(magnetic: Float?, fix: Fix?): Float? {
    magnetic ?: return null
    val decl = fix?.let {
        GeomagneticField(it.lat.toFloat(), it.lon.toFloat(), (it.altitude ?: 0.0).toFloat(), System.currentTimeMillis()).declination
    } ?: 0f
    return Geo.normalize(magnetic + decl)
}

@Composable
fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
fun NowScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val gnss by GnssEngine.state.collectAsStateWithLifecycle()
    val sensors by SensorEngine.state.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val now by rememberElapsedMs()
    val fix = gnss.fix
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Now",
            subtitle = fixLabel(gnss),
            leading = { StatusDot(statusColor(gnss)) },
            actions = {
                IconBtn(Ic.share, "Share position", onClick = {
                    if (fix != null) Share.position(context, fix.lat, fix.lon, fix.accuracy, null) else Share.toast(context, "No fix yet")
                })
                IconBtn(Ic.settings, "Settings", onOpenSettings)
            },
        )
        if (!gnss.providerEnabled) LocationOffBanner()

        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PositionPanel(fix, settings.coordFormat, gnss, now)

            val speed = fix?.speed?.let { Fmt.num(it * 3.6, 1) } ?: "—"
            val altLabel = if (fix?.altitudeMsl != null || fix == null) "Altitude MSL" else "Altitude (ellipsoid)"
            val alt = fix?.altitude?.let { Fmt.num(it, 0) } ?: "—"
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Speed", speed, "km/h", Modifier.weight(1f))
                StatTile(altLabel, alt, "m", Modifier.weight(1f))
            }

            val compass = trueHeading(sensors.headingMagnetic, fix)
            val gpsCourse = fix?.bearing?.takeIf { (fix?.speed ?: 0f) > 0.5f }
            val (headingValue, headingUnit) = when {
                gpsCourse != null -> String.format(Locale.ROOT, "%.0f°", gpsCourse) to
                    (Geo.cardinal(gpsCourse.toDouble()) + (compass?.let { String.format(Locale.ROOT, " · %.0f°", it) } ?: ""))
                compass != null -> String.format(Locale.ROOT, "%.0f°", compass) to (Geo.cardinal(compass.toDouble()) + " · compass")
                else -> "—" to null
            }
            val dop = gnss.dop
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Heading · GPS / compass", headingValue, headingUnit, Modifier.weight(1f))
                StatTile("DOP · P / H / V", Fmt.num(dop.pdop, 1), "${Fmt.num(dop.hdop, 1)} / ${Fmt.num(dop.vdop, 1)}", Modifier.weight(1f))
            }

            SatelliteSummary(gnss)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Save waypoint", onClick = {
                    if (fix != null) saving = true else Share.toast(context, "No fix yet")
                }, modifier = Modifier.weight(1f))
                ActionButton("Copy coordinates", primary = false, modifier = Modifier.weight(1f), onClick = {
                    if (fix != null) {
                        Share.copy(context, "Position", Coordinates.singleLine(fix.lat, fix.lon, settings.coordFormat))
                    } else {
                        Share.toast(context, "No fix yet")
                    }
                })
            }
        }
    }

    if (saving && fix != null) {
        NameDialog(
            title = "Save waypoint",
            initial = WaypointStore.nextName(),
            confirm = "Save",
            onConfirm = { name ->
                WaypointStore.addFromFix(fix, name.ifBlank { WaypointStore.nextName() })
                saving = false
                Share.toast(context, "Waypoint saved")
            },
            onDismiss = { saving = false },
        )
    }
}

@Composable
private fun PositionPanel(fix: Fix?, format: CoordFormat, gnss: GnssState, nowElapsedMs: Long) {
    Panel(radius = 20.dp, padding = 18.dp, spacing = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Position · WGS84", Modifier.weight(1f))
            FormatChip(format) {
                val all = CoordFormat.entries
                Prefs.update { s -> s.copy(coordFormat = all[(all.indexOf(format) + 1) % all.size]) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (fix != null) {
                val (l1, l2) = Coordinates.lines(fix.lat, fix.lon, format)
                CoordText(l1)
                CoordText(l2)
            } else {
                CoordText("— — —")
                Text("Waiting for satellites…", fontSize = 14.sp, color = C.Muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AccuracyRing(fix?.accuracy, Modifier.size(64.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    fix?.accuracy?.let { String.format(Locale.ROOT, "±%.1f m", it) } ?: "±— m",
                    fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.Text,
                )
                Text(
                    "Horizontal · vertical " + (fix?.verticalAccuracy?.let { String.format(Locale.ROOT, "±%.1f m", it) } ?: "—"),
                    fontSize = 13.sp, color = C.Muted,
                )
                if (fix != null) {
                    val ageS = ((nowElapsedMs * 1_000_000L - fix.elapsedNanos) / 1e9).coerceAtLeast(0.0)
                    val rate = gnss.updateRateHz?.let { String.format(Locale.ROOT, " · %.0f Hz", it) } ?: ""
                    Text(
                        String.format(Locale.ROOT, "Fix age %.1f s", ageS) + rate,
                        fontSize = 13.sp, color = if (ageS > 5) C.Amber else C.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoordText(text: String) {
    Text(
        text, fontFamily = Mono, fontSize = 27.sp, fontWeight = FontWeight.Medium, color = C.Text,
        maxLines = 1, overflow = TextOverflow.Clip, letterSpacing = (-0.3).sp,
    )
}

@Composable
private fun FormatChip(format: CoordFormat, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(C.Bg)
            .border(1.dp, C.Line, shape)
            .clickable(role = Role.Button, onClickLabel = "Change coordinate format", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(format.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
        Spacer(Modifier.width(6.dp))
        Icon(Ic.swap, contentDescription = null, tint = C.Muted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun AccuracyRing(accuracy: Float?, modifier: Modifier) {
    // Full ring at 0.5 m, empty at 50 m, logarithmic in between.
    val fraction = accuracy?.let { (1 - ln(it.coerceAtLeast(0.5f) / 0.5f) / ln(100f)).coerceIn(0.04f, 1f) } ?: 0f
    val color = accuracyColor(accuracy)
    Canvas(modifier) {
        val stroke = 6.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(C.Line, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(color, -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(C.Text, 3.dp.toPx(), center)
    }
}

@Composable
private fun SatelliteSummary(gnss: GnssState) {
    val tracked = gnss.satellites.filter { it.tracked }
    val groups = tracked.groupBy { it.constellation }.toList().sortedBy { it.first.ordinal }
    Panel(spacing = 12.dp) {
        PanelTitle("Satellites", "${gnss.used} used / ${gnss.tracked} seen")
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for ((c, list) in groups) {
                val used = list.count { it.usedInFix }
                if (used > 0) Box(Modifier.weight(used.toFloat()).fillMaxSize().background(Color(c.argb)))
            }
            val unused = tracked.count { !it.usedInFix }
            if (unused > 0 || gnss.used == 0) Box(Modifier.weight(unused.coerceAtLeast(1).toFloat()).fillMaxSize().background(C.Line))
        }
        if (groups.isEmpty()) {
            Text("No satellites tracked yet. Go outside or near a window.", fontSize = 13.sp, color = C.Muted)
        } else {
            for (row in groups.chunked(4)) {
                Row(Modifier.fillMaxWidth()) {
                    for ((c, list) in row) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(c.argb), maxLines = 1)
                            Text("${list.count { it.usedInFix }}/${list.size}", fontFamily = Mono, fontSize = 13.sp, color = C.TextSoft)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
