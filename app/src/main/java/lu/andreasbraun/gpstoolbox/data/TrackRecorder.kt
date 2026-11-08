package lu.andreasbraun.gpstoolbox.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lu.andreasbraun.gpstoolbox.AppScope
import lu.andreasbraun.gpstoolbox.core.Fix
import lu.andreasbraun.gpstoolbox.core.Geo
import lu.andreasbraun.gpstoolbox.core.Gpx
import lu.andreasbraun.gpstoolbox.core.TrackPoint
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrackState(
    val recording: Boolean = false,
    val startedElapsedMs: Long = 0L,
    val points: Int = 0,
    val distanceM: Double = 0.0,
    val lastAccuracy: Float? = null,
)

/** Records fixes into a track and saves it as GPX when stopped. */
object TrackRecorder {
    private lateinit var dir: File
    private val _state = MutableStateFlow(TrackState())
    val state: StateFlow<TrackState> = _state.asStateFlow()
    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val points = ArrayList<TrackPoint>()
    private var job: Job? = null
    private var startedWall = 0L

    fun init(context: Context) {
        dir = File(context.filesDir, "tracks").apply { mkdirs() }
        refreshFiles()
    }

    fun start(context: Context) {
        if (_state.value.recording) return
        points.clear()
        startedWall = System.currentTimeMillis()
        _state.value = TrackState(recording = true, startedElapsedMs = SystemClock.elapsedRealtime())
        Background.require(context, Background.Reason.TRACK)
        job = AppScope.launch { GnssEngine.fixes.collect { onFix(it) } }
        Telemetry.event("track_start")
    }

    private fun onFix(fix: Fix) {
        val acc = fix.accuracy
        if (acc != null && acc > 30f) return
        val last = points.lastOrNull()
        val p = TrackPoint(fix.lat, fix.lon, fix.altitude, fix.time, acc)
        if (last == null) {
            points += p
        } else {
            val d = Geo.distance(last.lat, last.lon, p.lat, p.lon)
            // Ignore jitter while standing still; still log a point every 30 s.
            val minMove = maxOf(2.0, (acc ?: 5f) * 0.5)
            if (d >= minMove) {
                points += p
                _state.value = _state.value.copy(distanceM = _state.value.distanceM + d)
            } else if (p.time - last.time >= 30_000) {
                points += p
            }
        }
        _state.value = _state.value.copy(points = points.size, lastAccuracy = acc)
    }

    /** Stops recording and writes the GPX file. Returns the file, or null if nothing was recorded. */
    suspend fun stop(): File? {
        if (!_state.value.recording) return null
        job?.cancel()
        job = null
        Background.drop(Background.Reason.TRACK)
        val snapshot = points.toList()
        val finished = _state.value
        Telemetry.event(
            "track_stop",
            "points" to snapshot.size,
            "dist_m" to finished.distanceM,
            "dur_s" to (SystemClock.elapsedRealtime() - finished.startedElapsedMs) / 1000,
        )
        _state.value = TrackState()
        if (snapshot.isEmpty()) return null
        val name = "Track " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(startedWall))
        val file = File(dir, "track-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(startedWall)) + ".gpx")
        withContext(Dispatchers.IO) { file.writeText(Gpx.track(name, snapshot)) }
        refreshFiles()
        return file
    }

    fun delete(file: File) {
        file.delete()
        refreshFiles()
    }

    private fun refreshFiles() {
        _files.value = dir.listFiles { f -> f.extension == "gpx" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
