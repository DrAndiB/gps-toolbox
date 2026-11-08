package lu.andreasbraun.gpstoolbox.data

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

data class AgnssState(
    /** Wall-clock time of the last refresh request, 0 = never. */
    val lastRequest: Long = 0L,
    val lastAccepted: Boolean = false,
    val lastWasBackground: Boolean = false,
    val lastColdStart: Long = 0L,
)

/**
 * Assistance data (A-GNSS). The app cannot download satellite orbits itself; it asks Android's
 * location stack to fetch predicted orbits (PSDS, called XTRA on Qualcomm) and network time.
 * Doing this on a schedule means the data is already on the phone when there is no reception.
 */
object Agnss {
    private const val WORK_NAME = "agnss-refresh"
    /** Typical validity of predicted orbits on current chipsets. Vendors differ, so the UI calls it an estimate. */
    const val TYPICAL_VALIDITY_DAYS = 7

    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(AgnssState())
    val state: StateFlow<AgnssState> = _state.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("agnss", Context.MODE_PRIVATE)
        _state.value = AgnssState(
            lastRequest = sp.getLong("lastRequest", 0L),
            lastAccepted = sp.getBoolean("lastAccepted", false),
            lastWasBackground = sp.getBoolean("lastWasBackground", false),
            lastColdStart = sp.getLong("lastColdStart", 0L),
        )
    }

    private fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Asks the system to inject fresh time and predicted orbits. Returns true if the request was accepted. */
    fun refreshNow(context: Context, background: Boolean = false): Boolean {
        if (!permitted(context)) return false
        val lm = context.getSystemService(LocationManager::class.java)
        fun send(cmd: String) = runCatching { lm.sendExtraCommand(LocationManager.GPS_PROVIDER, cmd, null) }.getOrDefault(false)
        val time = send("force_time_injection")
        var orbits = send("force_psds_injection")
        if (!orbits) orbits = send("force_xtra_injection") // name used before Android 11 and by some vendors
        val accepted = time || orbits
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(lastRequest = now, lastAccepted = accepted, lastWasBackground = background)
        sp.edit().putLong("lastRequest", now).putBoolean("lastAccepted", accepted).putBoolean("lastWasBackground", background).apply()
        return accepted
    }

    /** Deletes all assistance data so the next fix is a true cold start. */
    fun coldStart(context: Context): Boolean {
        if (!permitted(context)) return false
        val lm = context.getSystemService(LocationManager::class.java)
        val ok = runCatching { lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "delete_aiding_data", null) }.getOrDefault(false)
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(lastColdStart = now)
        sp.edit().putLong("lastColdStart", now).apply()
        return ok
    }

    /** (Re)schedules or cancels the periodic background refresh according to the settings. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val s = Prefs.current
        if (!s.agnssAuto) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (s.agnssWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AgnssWorker>(s.agnssIntervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class AgnssWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Agnss.refreshNow(applicationContext, background = true)
        // Never retry aggressively: the next period will try again.
        return Result.success()
    }
}
