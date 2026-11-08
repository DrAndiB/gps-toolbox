package lu.andreasbraun.gpstoolbox.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import lu.andreasbraun.gpstoolbox.AppScope
import lu.andreasbraun.gpstoolbox.data.TrackRecorder
import lu.andreasbraun.gpstoolbox.ui.ActionButton
import lu.andreasbraun.gpstoolbox.ui.ConfirmDialog
import lu.andreasbraun.gpstoolbox.ui.Fmt
import lu.andreasbraun.gpstoolbox.ui.Ic
import lu.andreasbraun.gpstoolbox.ui.IconBtn
import lu.andreasbraun.gpstoolbox.ui.Metric
import lu.andreasbraun.gpstoolbox.ui.Panel
import lu.andreasbraun.gpstoolbox.ui.ScreenHeader
import lu.andreasbraun.gpstoolbox.ui.SectionLabel
import lu.andreasbraun.gpstoolbox.ui.rememberElapsedMs
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono
import lu.andreasbraun.gpstoolbox.util.Share
import java.io.File
import java.util.Locale

@Composable
fun TrackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by TrackRecorder.state.collectAsStateWithLifecycle()
    val files by TrackRecorder.files.collectAsStateWithLifecycle()
    val now by rememberElapsedMs(1000L)
    var deleting by remember { mutableStateOf<File?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Track",
            subtitle = if (state.recording) "Recording · continues in the background" else "${files.size} saved tracks",
            onBack = onBack,
        )
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(radius = 20.dp, spacing = 14.dp) {
                Text(
                    if (state.recording) Fmt.duration(now - state.startedElapsedMs) else "00:00",
                    fontFamily = Mono, fontSize = 44.sp, fontWeight = FontWeight.Medium,
                    color = if (state.recording) C.Text else C.Faint,
                )
                Row(Modifier.fillMaxWidth()) {
                    Metric("Distance", Fmt.meters(state.distanceM), Modifier.weight(1f))
                    Metric("Points", state.points.toString(), Modifier.weight(1f))
                    Metric("Accuracy", state.lastAccuracy?.let { String.format(Locale.ROOT, "±%.1f m", it) } ?: "—", Modifier.weight(1f))
                }
                if (state.recording) {
                    ActionButton("Stop and save", color = C.Red, modifier = Modifier.fillMaxWidth(), onClick = {
                        AppScope.launch {
                            val file = TrackRecorder.stop()
                            Share.toast(context, if (file != null) "Saved ${file.name}" else "Nothing recorded yet")
                        }
                    })
                } else {
                    ActionButton("Start recording", modifier = Modifier.fillMaxWidth(), onClick = { TrackRecorder.start(context) })
                }
                Text(
                    "Fixes worse than 30 m are skipped, and jitter while standing still is ignored.",
                    fontSize = 12.sp, color = C.Faint,
                )
            }

            if (files.isNotEmpty()) {
                SectionLabel("Saved tracks")
                Panel(spacing = 0.dp, padding = 10.dp) {
                    files.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = C.Line)
                        FileRow(f, onShare = { Share.file(context, f, "application/gpx+xml") }, onDelete = { deleting = f })
                    }
                }
            }
        }
    }

    deleting?.let { f ->
        ConfirmDialog("Delete ${f.name}?", "This cannot be undone.", "Delete", destructive = true, onConfirm = {
            TrackRecorder.delete(f)
            deleting = null
        }, onDismiss = { deleting = null })
    }
}

@Composable
fun FileRow(file: File, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.name, fontSize = 14.sp, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Fmt.size(file.length())} · ${Fmt.dateTime(file.lastModified())}", fontSize = 12.sp, color = C.Muted)
        }
        IconBtn(Ic.share, "Share ${file.name}", onShare, bordered = false)
        IconBtn(Ic.trash, "Delete ${file.name}", onDelete, bordered = false, tint = C.Muted)
    }
}
