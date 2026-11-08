package lu.andreasbraun.gpstoolbox.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import lu.andreasbraun.gpstoolbox.AppScope
import lu.andreasbraun.gpstoolbox.data.RawLogger
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.ui.ConfirmDialog
import lu.andreasbraun.gpstoolbox.ui.Panel
import lu.andreasbraun.gpstoolbox.ui.PanelTitle
import lu.andreasbraun.gpstoolbox.ui.ScreenHeader
import lu.andreasbraun.gpstoolbox.ui.SectionLabel
import lu.andreasbraun.gpstoolbox.ui.ToggleRow
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono
import lu.andreasbraun.gpstoolbox.util.Share
import java.io.File

@Composable
fun RawLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by RawLogger.state.collectAsStateWithLifecycle()
    val files by RawLogger.files.collectAsStateWithLifecycle()
    val tail = remember { mutableStateListOf<String>() }
    var deleting by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        GnssEngine.nmea.collect { line ->
            tail.add(line)
            if (tail.size > 14) tail.removeAt(0)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Raw log", "NMEA and raw GNSS measurements", onBack = onBack)
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(padding = 12.dp, spacing = 0.dp) {
                ToggleRow(
                    "NMEA sentences",
                    if (state.nmeaActive) "${state.nmeaLines} lines written" else "Plain-text .nmea, readable by most GNSS tools",
                    state.nmeaActive,
                ) { on -> if (on) RawLogger.startNmea(context) else AppScope.launch { RawLogger.stopNmea() } }
                HorizontalDivider(color = C.Line)
                ToggleRow(
                    "Raw measurements",
                    when {
                        state.rawActive && !state.rawEventsSeen -> "Waiting… not every phone provides raw measurements"
                        state.rawActive -> "${state.rawRecords} measurements written"
                        else -> "Pseudoranges, carrier phase and C/N₀ as CSV, for post-processing"
                    },
                    state.rawActive,
                ) { on -> if (on) RawLogger.startRaw(context) else AppScope.launch { RawLogger.stopRaw() } }
            }
            Text(
                "Logging continues in the background. Raw measurements request full GNSS tracking, which uses more battery.",
                fontSize = 12.sp, color = C.Faint,
            )

            Panel(spacing = 2.dp) {
                PanelTitle("Live NMEA", "${tail.size} latest")
                if (tail.isEmpty()) Text("No sentences yet.", fontSize = 13.sp, color = C.Muted)
                for (line in tail) {
                    Text(line, fontFamily = Mono, fontSize = 10.sp, color = C.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (files.isNotEmpty()) {
                SectionLabel("Log files")
                Panel(spacing = 0.dp, padding = 10.dp) {
                    files.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = C.Line)
                        FileRow(f, onShare = {
                            Share.file(context, f, if (f.extension == "csv") "text/csv" else "text/plain")
                        }, onDelete = { deleting = f })
                    }
                }
            }
        }
    }

    deleting?.let { f ->
        ConfirmDialog("Delete ${f.name}?", "This cannot be undone.", "Delete", destructive = true, onConfirm = {
            RawLogger.delete(f)
            deleting = null
        }, onDismiss = { deleting = null })
    }
}
