package lu.andreasbraun.gpstoolbox.data

import android.content.Context
import android.location.GnssMeasurementsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lu.andreasbraun.gpstoolbox.AppScope
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogState(
    val nmeaActive: Boolean = false,
    val nmeaLines: Long = 0,
    val rawActive: Boolean = false,
    val rawRecords: Long = 0,
    val rawEventsSeen: Boolean = false,
)

/** Writes NMEA sentences and raw GNSS measurements to files for post-processing. */
@OptIn(ExperimentalCoroutinesApi::class)
object RawLogger {
    private lateinit var dir: File
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(LogState())
    val state: StateFlow<LogState> = _state.asStateFlow()
    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private var nmeaJob: Job? = null
    private var nmeaWriter: BufferedWriter? = null
    private var rawJob: Job? = null
    private var rawWriter: BufferedWriter? = null

    private const val RAW_HEADER =
        "# GPS Toolbox raw GNSS measurements\n" +
            "elapsedRealtimeNanos,timeNanos,fullBiasNanos,biasNanos,driftNanosPerSecond," +
            "constellation,svid,carrierFrequencyHz,codeType,cn0DbHz,timeOffsetNanos,state," +
            "receivedSvTimeNanos,receivedSvTimeUncertaintyNanos," +
            "pseudorangeRateMetersPerSecond,pseudorangeRateUncertaintyMetersPerSecond," +
            "accumulatedDeltaRangeState,accumulatedDeltaRangeMeters,accumulatedDeltaRangeUncertaintyMeters,multipathIndicator\n"

    fun init(context: Context) {
        dir = File(context.filesDir, "logs").apply { mkdirs() }
        refreshFiles()
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

    fun startNmea(context: Context) {
        if (_state.value.nmeaActive) return
        val file = File(dir, "nmea-${stamp()}.nmea")
        _state.update { it.copy(nmeaActive = true, nmeaLines = 0) }
        Background.require(context, Background.Reason.LOG)
        nmeaJob = AppScope.launch(io) {
            val w = file.bufferedWriter()
            nmeaWriter = w
            var n = 0L
            GnssEngine.nmea.collect { line ->
                w.write(line)
                w.write("\r\n")
                n++
                if (n % 50 == 0L) {
                    w.flush()
                    _state.update { it.copy(nmeaLines = n) }
                }
            }
        }
        refreshFiles()
        Telemetry.event("log_start", "kind" to "nmea")
    }

    suspend fun stopNmea() {
        if (_state.value.nmeaActive) Telemetry.event("log_stop", "kind" to "nmea", "lines" to _state.value.nmeaLines)
        nmeaJob?.cancel()
        nmeaJob = null
        withContext(io) { runCatching { nmeaWriter?.close() } }
        nmeaWriter = null
        _state.update { it.copy(nmeaActive = false) }
        dropIfIdle()
        refreshFiles()
    }

    fun startRaw(context: Context) {
        if (_state.value.rawActive) return
        val file = File(dir, "raw-${stamp()}.csv")
        _state.update { it.copy(rawActive = true, rawRecords = 0, rawEventsSeen = false) }
        Background.require(context, Background.Reason.LOG)
        GnssEngine.acquireMeasurements()
        rawJob = AppScope.launch(io) {
            val w = file.bufferedWriter()
            rawWriter = w
            w.write(RAW_HEADER)
            var n = 0L
            GnssEngine.measurements.collect { event ->
                n += write(w, event)
                w.flush()
                _state.update { it.copy(rawRecords = n, rawEventsSeen = true) }
            }
        }
        refreshFiles()
        Telemetry.event("log_start", "kind" to "raw")
    }

    suspend fun stopRaw() {
        val s = _state.value
        // "supported" tells whether this phone delivers raw measurements at all.
        if (s.rawActive) Telemetry.event("log_stop", "kind" to "raw", "records" to s.rawRecords, "supported" to s.rawEventsSeen)
        rawJob?.cancel()
        rawJob = null
        GnssEngine.releaseMeasurements()
        withContext(io) { runCatching { rawWriter?.close() } }
        rawWriter = null
        _state.update { it.copy(rawActive = false) }
        dropIfIdle()
        refreshFiles()
    }

    private fun dropIfIdle() {
        val s = _state.value
        if (!s.nmeaActive && !s.rawActive) Background.drop(Background.Reason.LOG)
    }

    private fun write(w: BufferedWriter, event: GnssMeasurementsEvent): Int {
        val c = event.clock
        val clock = String.format(
            Locale.ROOT, "%s,%d,%s,%s,%s",
            if (c.hasElapsedRealtimeNanos()) c.elapsedRealtimeNanos.toString() else "",
            c.timeNanos,
            if (c.hasFullBiasNanos()) c.fullBiasNanos.toString() else "",
            if (c.hasBiasNanos()) c.biasNanos.toString() else "",
            if (c.hasDriftNanosPerSecond()) c.driftNanosPerSecond.toString() else "",
        )
        var count = 0
        for (m in event.measurements) {
            w.write(clock)
            w.write(
                String.format(
                    Locale.ROOT, ",%d,%d,%s,%s,%.2f,%.1f,%d,%d,%d,%.4f,%.4f,%d,%.4f,%.4f,%d\n",
                    m.constellationType,
                    m.svid,
                    if (m.hasCarrierFrequencyHz()) m.carrierFrequencyHz.toString() else "",
                    if (m.hasCodeType()) m.codeType else "",
                    m.cn0DbHz,
                    m.timeOffsetNanos,
                    m.state,
                    m.receivedSvTimeNanos,
                    m.receivedSvTimeUncertaintyNanos,
                    m.pseudorangeRateMetersPerSecond,
                    m.pseudorangeRateUncertaintyMetersPerSecond,
                    m.accumulatedDeltaRangeState,
                    m.accumulatedDeltaRangeMeters,
                    m.accumulatedDeltaRangeUncertaintyMeters,
                    m.multipathIndicator,
                ),
            )
            count++
        }
        return count
    }

    fun delete(file: File) {
        file.delete()
        refreshFiles()
    }

    private fun refreshFiles() {
        _files.value = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
