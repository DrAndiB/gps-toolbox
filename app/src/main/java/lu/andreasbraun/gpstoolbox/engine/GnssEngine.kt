package lu.andreasbraun.gpstoolbox.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lu.andreasbraun.gpstoolbox.core.AccuracySample
import lu.andreasbraun.gpstoolbox.core.Band
import lu.andreasbraun.gpstoolbox.core.Constellation
import lu.andreasbraun.gpstoolbox.core.Dop
import lu.andreasbraun.gpstoolbox.core.Fix
import lu.andreasbraun.gpstoolbox.core.GnssState
import lu.andreasbraun.gpstoolbox.core.Nmea
import lu.andreasbraun.gpstoolbox.core.SatSignal
import lu.andreasbraun.gpstoolbox.core.Satellite
import lu.andreasbraun.gpstoolbox.data.Agnss
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry

/**
 * Single owner of all GNSS listeners. Screens and the live service "acquire" it with a tag;
 * listeners are registered while at least one user holds it and location permission is granted.
 * All callbacks arrive on the main thread.
 */
object GnssEngine {
    private lateinit var app: Context
    private lateinit var lm: LocationManager

    private val _state = MutableStateFlow(GnssState())
    val state: StateFlow<GnssState> = _state.asStateFlow()

    private val _fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 64)
    /** Every new fix, for recorders that need each sample (track, averaging). */
    val fixes: SharedFlow<Fix> = _fixes.asSharedFlow()

    private val _nmea = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val nmea: SharedFlow<String> = _nmea.asSharedFlow()

    private val _measurements = MutableSharedFlow<GnssMeasurementsEvent>(extraBufferCapacity = 64)
    val measurements: SharedFlow<GnssMeasurementsEvent> = _measurements.asSharedFlow()

    private val _history = MutableStateFlow<List<AccuracySample>>(emptyList())
    /** Horizontal accuracy over the last ten minutes. */
    val accuracyHistory: StateFlow<List<AccuracySample>> = _history.asStateFlow()

    private val users = mutableSetOf<String>()
    private var registered = false
    private var measurementUsers = 0
    private var measurementsRegistered = false

    private var nmeaMsl: Double? = null
    private var nmeaMslAt = 0L
    private var lastFixElapsed = 0L
    private var startedElapsed = 0L
    private var rateEma: Double? = null

    private const val HISTORY_MS = 10 * 60 * 1000L

    fun init(context: Context) {
        app = context.applicationContext
        lm = app.getSystemService(LocationManager::class.java)
        _state.update { it.copy(permission = hasPermission(), providerEnabled = providerEnabled()) }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun providerEnabled(): Boolean =
        runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    fun acquire(tag: String) {
        users += tag
        refresh()
    }

    fun release(tag: String) {
        users -= tag
        refresh()
    }

    /** Re-evaluates permission and registration, e.g. after the user granted access. */
    fun refresh() {
        val permitted = hasPermission()
        val want = users.isNotEmpty() && permitted
        if (want && !registered) register()
        if (!want && registered) unregister()
        updateMeasurementRegistration()
        _state.update { it.copy(permission = permitted, running = registered, providerEnabled = providerEnabled()) }
    }

    /** Stops and restarts the GNSS engine, e.g. after clearing assistance data, so a new TTFF is measured. */
    fun restart() {
        if (!registered) return
        unregister()
        _state.update { it.copy(fix = null, satellites = emptyList(), fixType = 0, dop = Dop(), firstFixPending = true) }
        register()
        updateMeasurementRegistration()
    }

    fun acquireMeasurements() {
        measurementUsers++
        updateMeasurementRegistration()
    }

    fun releaseMeasurements() {
        measurementUsers = (measurementUsers - 1).coerceAtLeast(0)
        updateMeasurementRegistration()
    }

    @SuppressLint("MissingPermission")
    private fun register() {
        if (!hasPermission()) return
        try {
            val executor = ContextCompat.getMainExecutor(app)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper())
            lm.registerGnssStatusCallback(executor, statusCallback)
            lm.addNmeaListener(executor, nmeaListener)
            registered = true
            startedElapsed = SystemClock.elapsedRealtime()
            Telemetry.event("gnss_start", "users" to users.joinToString(","))
        } catch (e: SecurityException) {
            registered = false
            Telemetry.event("gnss_error", "where" to "register", "error" to e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            // Device without a GPS provider.
            registered = false
            Telemetry.event("gnss_error", "where" to "register", "error" to e.javaClass.simpleName)
        }
    }

    private fun unregister() {
        runCatching { lm.removeUpdates(locationListener) }
        runCatching { lm.unregisterGnssStatusCallback(statusCallback) }
        runCatching { lm.removeNmeaListener(nmeaListener) }
        if (registered) Telemetry.event("gnss_stop", "on_s" to (SystemClock.elapsedRealtime() - startedElapsed) / 1000)
        registered = false
    }

    @SuppressLint("MissingPermission")
    private fun updateMeasurementRegistration() {
        val want = registered && measurementUsers > 0
        if (want && !measurementsRegistered) {
            val executor = ContextCompat.getMainExecutor(app)
            measurementsRegistered = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val request = GnssMeasurementRequest.Builder().setFullTracking(true).build()
                    lm.registerGnssMeasurementsCallback(request, executor, measurementCallback)
                } else {
                    lm.registerGnssMeasurementsCallback(executor, measurementCallback)
                }
            }.getOrDefault(false)
            Telemetry.event("raw_registration", "ok" to measurementsRegistered)
        } else if (!want && measurementsRegistered) {
            runCatching { lm.unregisterGnssMeasurementsCallback(measurementCallback) }
            measurementsRegistered = false
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderEnabled(provider: String) {
            Telemetry.event("provider", "enabled" to true)
            _state.update { it.copy(providerEnabled = true) }
        }
        override fun onProviderDisabled(provider: String) {
            Telemetry.event("provider", "enabled" to false)
            _state.update { it.copy(providerEnabled = false, fix = null) }
        }
    }

    private fun onLocation(location: Location) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val msl = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude() -> location.mslAltitudeMeters
            nmeaMsl != null && nowElapsed - nmeaMslAt < 3000 -> nmeaMsl
            else -> null
        }
        val fix = Fix(
            lat = location.latitude,
            lon = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            verticalAccuracy = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null,
            altitudeEllipsoid = if (location.hasAltitude()) location.altitude else null,
            altitudeMsl = msl,
            speed = if (location.hasSpeed()) location.speed else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            time = location.time,
            elapsedNanos = location.elapsedRealtimeNanos,
        )

        if (lastFixElapsed > 0) {
            val dt = (fix.elapsedNanos - lastFixElapsed) / 1e9
            if (dt > 0.05 && dt < 30) {
                val hz = 1.0 / dt
                rateEma = rateEma?.let { it * 0.8 + hz * 0.2 } ?: hz
            }
        }
        lastFixElapsed = fix.elapsedNanos

        fix.accuracy?.let { acc ->
            val sample = AccuracySample(nowElapsed, acc)
            _history.update { list -> (list + sample).dropWhile { nowElapsed - it.elapsedMs > HISTORY_MS } }
        }

        _state.update { it.copy(fix = fix, updateRateHz = rateEma, providerEnabled = true) }
        _fixes.tryEmit(fix)
    }

    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val signals = ArrayList<SatSignal>(status.satelliteCount)
            for (i in 0 until status.satelliteCount) {
                signals += SatSignal(
                    constellation = Constellation.fromType(status.getConstellationType(i)),
                    svid = status.getSvid(i),
                    band = Band.of(if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) else null),
                    cn0 = status.getCn0DbHz(i),
                    elevation = status.getElevationDegrees(i),
                    azimuth = status.getAzimuthDegrees(i),
                    usedInFix = status.usedInFix(i),
                    hasEphemeris = status.hasEphemerisData(i),
                    hasAlmanac = status.hasAlmanacData(i),
                )
            }
            val satellites = signals
                .groupBy { it.constellation to it.svid }
                .map { (key, list) ->
                    val primary = list.maxBy { it.cn0 }
                    Satellite(
                        constellation = key.first,
                        svid = key.second,
                        elevation = primary.elevation,
                        azimuth = primary.azimuth,
                        usedInFix = list.any { it.usedInFix },
                        hasEphemeris = list.any { it.hasEphemeris },
                        signals = list.sortedBy { it.band.ordinal },
                    )
                }
                .sortedWith(compareBy({ it.constellation.ordinal }, { it.svid }))
            _state.update { it.copy(satellites = satellites) }
        }

        override fun onFirstFix(ttffMillis: Int) {
            // Ties time to first fix to the age of the assistance data: the key check for offline use.
            val agnss = Agnss.state.value.lastRequest
            val agnssAgeH = if (agnss > 0) (System.currentTimeMillis() - agnss) / 3_600_000.0 else null
            Telemetry.event(
                "first_fix",
                "ttff_ms" to ttffMillis,
                "after_cold_start" to _state.value.firstFixPending,
                "agnss_age_h" to agnssAgeH,
                "fg" to Telemetry.foreground,
            )
            _state.update { it.copy(lastTtffMs = ttffMillis, firstFixPending = false) }
        }

        override fun onStopped() {
            _state.update { it.copy(satellites = emptyList()) }
        }
    }

    private val nmeaListener = OnNmeaMessageListener { message, _ ->
        _nmea.tryEmit(message.trim())
        val s = Nmea.parse(message)
        if (s is Nmea.Gga && s.quality > 0 && s.mslAltitude != null) {
            nmeaMsl = s.mslAltitude
            nmeaMslAt = SystemClock.elapsedRealtime()
        } else if (s is Nmea.Gsa) {
            _state.update { it.copy(fixType = s.fixType, dop = Dop(s.pdop, s.hdop, s.vdop)) }
        }
    }

    private val measurementCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            _measurements.tryEmit(eventArgs)
        }
    }
}
