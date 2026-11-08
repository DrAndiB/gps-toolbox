package lu.andreasbraun.gpstoolbox.ui.tools

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.core.Coordinates
import lu.andreasbraun.gpstoolbox.core.Fix
import lu.andreasbraun.gpstoolbox.core.Geo
import lu.andreasbraun.gpstoolbox.core.Gpx
import lu.andreasbraun.gpstoolbox.core.Waypoint
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.data.WaypointStore
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import lu.andreasbraun.gpstoolbox.ui.ActionButton
import lu.andreasbraun.gpstoolbox.ui.ConfirmDialog
import lu.andreasbraun.gpstoolbox.ui.Fmt
import lu.andreasbraun.gpstoolbox.ui.Ic
import lu.andreasbraun.gpstoolbox.ui.IconBtn
import lu.andreasbraun.gpstoolbox.ui.Metric
import lu.andreasbraun.gpstoolbox.ui.NameDialog
import lu.andreasbraun.gpstoolbox.ui.Panel
import lu.andreasbraun.gpstoolbox.ui.ScreenHeader
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono
import lu.andreasbraun.gpstoolbox.ui.trueHeading
import lu.andreasbraun.gpstoolbox.util.Share
import java.util.Locale
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

@Composable
fun WaypointsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val waypoints by WaypointStore.items.collectAsStateWithLifecycle()
    val gnss by GnssEngine.state.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val fix = gnss.fix

    val selected = waypoints.firstOrNull { it.id == selectedId }
    if (selected != null) {
        BackHandler { selectedId = null }
        WaypointDetail(selected, onBack = { selectedId = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Waypoints",
            subtitle = "${waypoints.size} saved",
            onBack = onBack,
            actions = {
                IconBtn(Ic.export, "Export all as GPX", onClick = {
                    if (waypoints.isEmpty()) Share.toast(context, "No waypoints yet")
                    else Share.text(context, "waypoints.gpx", Gpx.waypoints(waypoints), "application/gpx+xml")
                })
            },
        )
        ActionButton(
            "Save current position",
            icon = Ic.plus,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            onClick = { if (fix != null) adding = true else Share.toast(context, "No fix yet") },
        )
        if (waypoints.isEmpty()) {
            Text(
                "Save a spot (your car, a trailhead, a meeting point) and the radar will lead you back to it.",
                fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(waypoints.reversed(), key = { it.id }) { w ->
                WaypointRow(w, fix, Coordinates.singleLine(w.lat, w.lon, settings.coordFormat)) { selectedId = w.id }
            }
        }
    }

    if (adding && fix != null) {
        NameDialog("Save waypoint", WaypointStore.nextName(), "Save", onConfirm = { name ->
            WaypointStore.addFromFix(fix, name.ifBlank { WaypointStore.nextName() })
            adding = false
        }, onDismiss = { adding = false })
    }
}

@Composable
private fun WaypointRow(w: Waypoint, fix: Fix?, coords: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Surface)
            .border(1.dp, C.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(w.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(coords, fontFamily = Mono, fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (fix != null) {
            val d = Geo.distance(fix.lat, fix.lon, w.lat, w.lon)
            val b = Geo.bearing(fix.lat, fix.lon, w.lat, w.lon)
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.meters(d), fontFamily = Mono, fontSize = 14.sp, color = C.Text)
                Text(String.format(Locale.ROOT, "%.0f° %s", b, Geo.cardinal(b)), fontSize = 12.sp, color = C.Muted)
            }
        }
    }
}

@Composable
private fun WaypointDetail(w: Waypoint, onBack: () -> Unit) {
    val context = LocalContext.current
    val gnss by GnssEngine.state.collectAsStateWithLifecycle()
    val sensors by SensorEngine.state.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val fix = gnss.fix

    val distance = fix?.let { Geo.distance(it.lat, it.lon, w.lat, w.lon) }
    val bearing = fix?.let { Geo.bearing(it.lat, it.lon, w.lat, w.lon) }
    // Prefer GPS course while moving: it is immune to magnetic interference.
    val course = fix?.bearing?.takeIf { (fix?.speed ?: 0f) > 1f }
    val heading = course ?: trueHeading(sensors.headingMagnetic, fix)
    val relative = bearing?.let { b -> heading?.let { Geo.normalize(b - it) } ?: b }

    // How long the radar is used, and at roughly what range (order of magnitude only).
    DisposableEffect(w.id) {
        val opened = SystemClock.elapsedRealtime()
        val range = distance?.let { d -> if (d < 10) "<10m" else "~1e" + kotlin.math.floor(kotlin.math.log10(d)).toInt() + "m" }
        Telemetry.event("waypoint_open", "range" to range)
        onDispose { Telemetry.event("waypoint_close", "open_s" to (SystemClock.elapsedRealtime() - opened) / 1000) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = w.name,
            subtitle = "Saved ${Fmt.dateTime(w.time)}",
            onBack = onBack,
            actions = {
                IconBtn(Ic.copy, "Copy coordinates", onClick = {
                    Share.copy(context, w.name, Coordinates.singleLine(w.lat, w.lon, settings.coordFormat))
                })
                IconBtn(Ic.share, "Share", onClick = { Share.position(context, w.lat, w.lon, w.accuracy, w.name) })
            },
        )
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(Fmt.meters(distance), fontFamily = Mono, fontSize = 44.sp, fontWeight = FontWeight.Medium, color = C.Text)
            Text(
                bearing?.let { String.format(Locale.ROOT, "Bearing %.0f° %s", it, Geo.cardinal(it)) } ?: "Waiting for a fix…",
                fontSize = 15.sp, color = C.Muted,
            )
            Radar(distance, relative, Modifier.fillMaxWidth(0.85f).aspectRatio(1f))
            Text(
                when {
                    course != null -> "Arrow follows your direction of travel"
                    heading != null -> "Arrow follows the compass · hold the phone flat"
                    else -> "No heading available: arrow is relative to north"
                },
                fontSize = 12.sp, color = C.Faint,
            )
            Panel(Modifier.fillMaxWidth(), spacing = 8.dp) {
                Text(Coordinates.singleLine(w.lat, w.lon, settings.coordFormat), fontFamily = Mono, fontSize = 15.sp, color = C.Text)
                Row(Modifier.fillMaxWidth()) {
                    Metric("Waypoint accuracy", w.accuracy?.let { String.format(Locale.ROOT, "±%.1f m", it) } ?: "—", Modifier.weight(1f))
                    Metric("Your accuracy", fix?.accuracy?.let { String.format(Locale.ROOT, "±%.1f m", it) } ?: "—", Modifier.weight(1f))
                    Metric("Altitude", w.altitude?.let { String.format(Locale.ROOT, "%.0f m", it) } ?: "—", Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Open in maps", icon = Ic.map, primary = false, modifier = Modifier.weight(1f), onClick = {
                    val label = Uri.encode(w.name)
                    val uri = Uri.parse(String.format(Locale.ROOT, "geo:%.7f,%.7f?q=%.7f,%.7f(%s)", w.lat, w.lon, w.lat, w.lon, label))
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        .onFailure { Share.toast(context, "No maps app installed") }
                })
                ActionButton("Rename", icon = Ic.edit, primary = false, modifier = Modifier.weight(1f), onClick = { renaming = true })
            }
            ActionButton("Delete waypoint", icon = Ic.trash, primary = false, modifier = Modifier.fillMaxWidth(), onClick = { deleting = true })
        }
    }

    if (renaming) {
        NameDialog("Rename waypoint", w.name, "Save", onConfirm = {
            if (it.isNotBlank()) WaypointStore.rename(w.id, it)
            renaming = false
        }, onDismiss = { renaming = false })
    }
    if (deleting) {
        ConfirmDialog("Delete ${w.name}?", "This cannot be undone.", "Delete", destructive = true, onConfirm = {
            deleting = false
            WaypointStore.delete(w.id)
            onBack()
        }, onDismiss = { deleting = false })
    }
}

/** Radar: target dot at its relative bearing, distance on a log scale (1 m at the centre, 10 km at the rim). */
@Composable
private fun Radar(distance: Double?, relativeBearing: Double?, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2 - 4.dp.toPx()
            drawCircle(C.Surface, r, c)
            drawCircle(C.Line, r, c, style = Stroke(1.dp.toPx()))
            for (k in 1..3) drawCircle(C.Grid, r * k / 4f, c, style = Stroke(1.dp.toPx()))
            if (distance == null || relativeBearing == null) return@Canvas
            val frac = (ln(distance.coerceAtLeast(1.0)) / ln(10_000.0)).coerceIn(0.0, 1.0).toFloat()
            val a = Math.toRadians(relativeBearing)
            val p = Offset(c.x + r * frac * sin(a).toFloat(), c.y - r * frac * cos(a).toFloat())
            drawLine(C.Accent.copy(alpha = 0.4f), c, p, 2.dp.toPx())
            drawCircle(C.Accent, 9.dp.toPx(), p)
        }
        // Large arrow in the middle pointing at the target.
        if (relativeBearing != null) {
            Icon(
                Ic.arrow,
                contentDescription = "Direction to waypoint",
                tint = C.Text,
                modifier = Modifier.size(56.dp).rotate(relativeBearing.toFloat()),
            )
        }
    }
}
