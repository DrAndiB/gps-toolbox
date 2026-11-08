package lu.andreasbraun.gpstoolbox.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lu.andreasbraun.gpstoolbox.AppScope
import lu.andreasbraun.gpstoolbox.core.Fix
import lu.andreasbraun.gpstoolbox.core.Geo
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import java.util.Locale
import kotlin.math.sqrt

data class AvgSample(val lat: Double, val lon: Double, val altitude: Double?, val accuracy: Float?, val time: Long)

data class AvgResult(
    val count: Int,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    /** Median distance of fixes from the mean (50 % circle). */
    val cep50: Double,
    /** 95th percentile distance from the mean. */
    val r95: Double,
    /** Standard error of the mean, assuming independent fixes (optimistic: fixes are correlated). */
    val stdError: Double,
    /** Distance between the mean of the first and last quarter of fixes. */
    val drift: Double?,
    /** East/north offsets of recent fixes from the mean, metres (for the scatter plot). */
    val offsets: List<Pair<Float, Float>>,
)

data class AveragerState(
    val running: Boolean = false,
    val startedElapsedMs: Long = 0L,
    val stoppedElapsedMs: Long? = null,
    val result: AvgResult? = null,
    val rejected: Int = 0,
)

/** Averages many fixes to estimate a stationary position more precisely than any single fix. */
object Averager {
    private const val MAX_ACCURACY_M = 30f
    private const val MAX_PLOT_POINTS = 600

    private val samples = ArrayList<AvgSample>()
    private var job: Job? = null
    private val _state = MutableStateFlow(AveragerState())
    val state: StateFlow<AveragerState> = _state.asStateFlow()

    fun init() = Unit

    fun start(context: Context) {
        if (_state.value.running) return
        samples.clear()
        _state.value = AveragerState(running = true, startedElapsedMs = SystemClock.elapsedRealtime())
        Background.require(context, Background.Reason.AVERAGE)
        job = AppScope.launch { GnssEngine.fixes.collect { onFix(it) } }
        Telemetry.event("avg_start")
    }

    fun stop() {
        val s = _state.value
        if (s.running) {
            val r = s.result
            Telemetry.event(
                "avg_stop",
                "n" to (r?.count ?: 0),
                "dur_s" to (SystemClock.elapsedRealtime() - s.startedElapsedMs) / 1000,
                "cep50" to r?.cep50,
                "r95" to r?.r95,
                "drift" to r?.drift,
                "rejected" to s.rejected,
            )
        }
        job?.cancel()
        job = null
        Background.drop(Background.Reason.AVERAGE)
        _state.value = _state.value.copy(running = false, stoppedElapsedMs = SystemClock.elapsedRealtime())
    }

    fun reset() {
        stop()
        samples.clear()
        _state.value = AveragerState()
    }

    private fun onFix(fix: Fix) {
        val acc = fix.accuracy
        if (acc != null && acc > MAX_ACCURACY_M) {
            _state.value = _state.value.copy(rejected = _state.value.rejected + 1)
            return
        }
        samples += AvgSample(fix.lat, fix.lon, fix.altitude, acc, fix.time)
        _state.value = _state.value.copy(result = compute(samples))
    }

    fun compute(list: List<AvgSample>): AvgResult? {
        if (list.isEmpty()) return null
        val n = list.size
        val lat = list.sumOf { it.lat } / n
        val lon = list.sumOf { it.lon } / n
        val alts = list.mapNotNull { it.altitude }
        val alt = if (alts.isEmpty()) null else alts.sum() / alts.size

        val en = list.map { Geo.eastNorth(lat, lon, it.lat, it.lon) }
        val dists = en.map { sqrt(it.first * it.first + it.second * it.second) }.sorted()
        val cep50 = percentile(dists, 0.5)
        val r95 = percentile(dists, 0.95)
        val varE = en.sumOf { it.first * it.first } / n
        val varN = en.sumOf { it.second * it.second } / n
        val stdError = sqrt((varE + varN) / n)

        val drift = if (n >= 8) {
            val q = n / 4
            val first = list.subList(0, q)
            val last = list.subList(n - q, n)
            Geo.distance(
                first.sumOf { it.lat } / q, first.sumOf { it.lon } / q,
                last.sumOf { it.lat } / q, last.sumOf { it.lon } / q,
            )
        } else null

        val plot = en.takeLast(MAX_PLOT_POINTS).map { it.first.toFloat() to it.second.toFloat() }
        return AvgResult(n, lat, lon, alt, cep50, r95, stdError, drift, plot)
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val idx = ((sorted.size - 1) * p)
        val lo = idx.toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo)
    }

    fun csv(): String = buildString {
        append("time_utc_ms,lat,lon,altitude_m,accuracy_m\n")
        for (s in samples) {
            append(String.format(Locale.ROOT, "%d,%.8f,%.8f,%s,%s\n", s.time, s.lat, s.lon,
                s.altitude?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "",
                s.accuracy?.let { String.format(Locale.ROOT, "%.2f", it) } ?: ""))
        }
        _state.value.result?.let {
            append(String.format(Locale.ROOT, "# mean,%.8f,%.8f,%s,cep50 %.2f m,r95 %.2f m,n %d\n", it.lat, it.lon,
                it.altitude?.let { a -> String.format(Locale.ROOT, "%.2f", a) } ?: "", it.cep50, it.r95, it.count))
        }
    }
}
