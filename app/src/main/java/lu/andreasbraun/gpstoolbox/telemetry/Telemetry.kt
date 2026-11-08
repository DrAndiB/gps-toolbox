package lu.andreasbraun.gpstoolbox.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lu.andreasbraun.gpstoolbox.AppScope
import lu.andreasbraun.gpstoolbox.BuildConfig
import lu.andreasbraun.gpstoolbox.core.Band
import lu.andreasbraun.gpstoolbox.data.Background
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

data class TelemetryState(
    val enabled: Boolean = false,
    val repo: String = Telemetry.DEFAULT_REPO,
    val hasToken: Boolean = false,
    val lastUpload: Long = 0L,
    val lastError: String? = null,
    val pendingBytes: Long = 0L,
)

data class UploadResult(val ok: Boolean, val message: String, val retry: Boolean = false)

/**
 * Opt-in usage telemetry. Events are appended to a local JSONL file and uploaded to a private
 * GitHub repository chosen by the user, with a token that never leaves the phone.
 *
 * Privacy rules: no coordinates, no waypoint names, no file contents. Only which screens and tools
 * are used, settings changes, GNSS quality figures, A-GNSS refreshes, time to first fix and errors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object Telemetry {
    const val DEFAULT_REPO = "DrAndiB/gps-toolbox-telemetry"
    private const val MAX_CURRENT_BYTES = 512 * 1024L
    private const val MAX_PENDING_BYTES = 8 * 1024 * 1024L
    private const val SNAPSHOT_MS = 60_000L
    private const val PERIODIC_WORK = "telemetry-upload"
    private const val SOON_WORK = "telemetry-upload-soon"
    private val REPO_PATTERN = Regex("^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$")

    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private lateinit var secret: SharedPreferences
    private lateinit var current: File
    private lateinit var pendingDir: File

    private val lock = Any()
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val uploadMutex = Mutex()
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** Random per process, groups events into sessions. */
    val sessionId: String = UUID.randomUUID().toString().take(8)

    @Volatile private var enabled = false

    /** True while the activity is visible. */
    @Volatile var foreground = false

    /** Last screen reported, included in app_open and crash events. */
    @Volatile var lastScreen: String? = null
        private set

    private val _state = MutableStateFlow(TelemetryState())
    val state: StateFlow<TelemetryState> = _state.asStateFlow()

    private val deviceTag: String by lazy {
        val id = sp.getString("deviceId", null)
            ?: UUID.randomUUID().toString().take(6).also { sp.edit().putString("deviceId", it).apply() }
        Build.MODEL.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(24) + "-" + id
    }

    fun init(context: Context) {
        app = context.applicationContext
        sp = app.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
        // Kept in its own file so backup rules can exclude it.
        secret = app.getSharedPreferences("telemetry_secret", Context.MODE_PRIVATE)
        val dir = File(app.filesDir, "telemetry").apply { mkdirs() }
        current = File(dir, "current.jsonl")
        pendingDir = File(dir, "pending").apply { mkdirs() }
        enabled = sp.getBoolean("enabled", false)
        _state.value = TelemetryState(
            enabled = enabled,
            repo = sp.getString("repo", DEFAULT_REPO) ?: DEFAULT_REPO,
            hasToken = !secret.getString("token", null).isNullOrBlank(),
            lastUpload = sp.getLong("lastUpload", 0L),
            lastError = sp.getString("lastError", null),
        )
        installCrashHandler()
        AppScope.launch(io) {
            refreshPending()
            for (line in queue) append(line)
        }
    }

    /** Logs one GNSS quality snapshot per minute while GNSS is running. */
    fun startSnapshots() {
        AppScope.launch {
            while (true) {
                delay(SNAPSHOT_MS)
                if (enabled && GnssEngine.state.value.running) event("gnss", *gnssSnapshot())
            }
        }
    }

    fun event(name: String, vararg props: Pair<String, Any?>) {
        if (!enabled) return
        val o = JSONObject()
        o.put("t", System.currentTimeMillis())
        o.put("e", name)
        o.put("s", sessionId)
        for ((key, value) in props) {
            val v = clean(value) ?: continue
            o.put(key, v)
        }
        queue.trySend(o.toString())
    }

    fun screen(name: String) {
        if (name == lastScreen) return
        lastScreen = name
        event("screen", "name" to name)
    }

    private fun clean(v: Any?): Any? = when (v) {
        null -> null
        is Float -> if (v.isNaN() || v.isInfinite()) null else round2(v.toDouble())
        is Double -> if (v.isNaN() || v.isInfinite()) null else round2(v)
        is Enum<*> -> v.name
        is Number, is Boolean, is String, is JSONObject, is JSONArray -> v
        else -> v.toString()
    }

    private fun round2(d: Double): Double = Math.round(d * 100.0) / 100.0

    private fun gnssSnapshot(): Array<Pair<String, Any?>> {
        val g = GnssEngine.state.value
        val f = g.fix
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val systems = JSONObject()
        g.satellites.filter { it.tracked }.groupBy { it.constellation }.forEach { (c, list) ->
            systems.put(c.label, JSONArray().put(list.count { it.usedInFix }).put(list.size))
        }
        val topL1 = g.satellites.mapNotNull { it.cn0On(Band.L1) }.sortedDescending().take(4)
        return arrayOf(
            "fix" to (f != null),
            "acc" to f?.accuracy,
            "vacc" to f?.verticalAccuracy,
            "fix_type" to g.fixType,
            "age_s" to f?.let { (nowNanos - it.elapsedNanos) / 1e9 },
            "rate_hz" to g.updateRateHz,
            "speed_kmh" to f?.speed?.let { it * 3.6 },
            "msl" to (f?.altitudeMsl != null),
            "pdop" to g.dop.pdop,
            "hdop" to g.dop.hdop,
            "vdop" to g.dop.vdop,
            "used" to g.used,
            "tracked" to g.tracked,
            "systems" to systems,
            "bands" to JSONArray(g.bandsInUse.map { it.name }),
            "dual_band" to g.dualBand,
            "cn0_top4" to (if (topL1.isEmpty()) null else topL1.average()),
            "fg" to foreground,
            "bg_reasons" to JSONArray(Background.reasons.value.map { it.name.lowercase() }),
            "heading_acc" to SensorEngine.state.value.headingAccuracy,
            "provider" to g.providerEnabled,
        )
    }

    private fun append(line: String) {
        if (!enabled) return
        synchronized(lock) {
            try {
                current.appendText(line + "\n")
                if (current.length() > MAX_CURRENT_BYTES) rotateLocked()
            } catch (e: IOException) {
                // Telemetry must never break the app.
            }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            if (enabled) {
                runCatching {
                    while (true) {
                        val pending = queue.tryReceive().getOrNull() ?: break
                        append(pending)
                    }
                    val o = JSONObject()
                        .put("t", System.currentTimeMillis())
                        .put("e", "crash")
                        .put("s", sessionId)
                        .put("thread", thread.name)
                        .put("screen", lastScreen ?: "")
                        .put("error", e.toString().take(400))
                        .put("stack", e.stackTraceToString().take(8000))
                    append(o.toString())
                }
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Moves the current file into the upload queue. Caller holds [lock]. */
    private fun rotateLocked() {
        if (!current.exists() || current.length() == 0L) return
        var stamp = System.currentTimeMillis()
        while (File(pendingDir, "$stamp.jsonl").exists()) stamp++
        val target = File(pendingDir, "$stamp.jsonl")
        if (!current.renameTo(target)) {
            current.copyTo(target, overwrite = true)
            current.delete()
        }
        // Cap local storage in case uploads never succeed.
        val files = pendingDir.listFiles()?.sortedBy { it.name } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_PENDING_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    /** Recomputes the size of data waiting for upload. Does file I/O: call off the main thread. */
    fun refreshPending() {
        val bytes = synchronized(lock) {
            (if (current.exists()) current.length() else 0L) + (pendingDir.listFiles()?.sumOf { it.length() } ?: 0L)
        }
        _state.update { it.copy(pendingBytes = bytes) }
    }

    fun setEnabled(context: Context, on: Boolean) {
        if (on == enabled) return
        enabled = on
        sp.edit().putBoolean("enabled", on).apply()
        _state.update { it.copy(enabled = on) }
        if (on) {
            event("telemetry_on", "v" to BuildConfig.VERSION_CODE, "screen" to lastScreen)
        } else {
            // Turning it off deletes everything not yet uploaded.
            AppScope.launch(io) {
                synchronized(lock) {
                    current.delete()
                    pendingDir.listFiles()?.forEach { it.delete() }
                }
                refreshPending()
            }
        }
        schedule(context)
    }

    fun configure(context: Context, repo: String, token: String?) {
        val r = repo.trim().removePrefix("https://github.com/").trimEnd('/')
        sp.edit().putString("repo", r).remove("lastError").apply()
        if (token != null) secret.edit().putString("token", token.trim()).apply()
        _state.update {
            it.copy(repo = r, hasToken = !secret.getString("token", null).isNullOrBlank(), lastError = null)
        }
        schedule(context)
    }

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC_WORK)
            wm.cancelUniqueWork(SOON_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<TelemetryUploadWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkConstraint())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Uploads shortly after the app goes to the background, so each session arrives promptly. */
    fun uploadSoon(context: Context) {
        if (!enabled || !_state.value.hasToken) return
        val request = OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
            .setConstraints(networkConstraint())
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun networkConstraint() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    suspend fun upload(): UploadResult = withContext(Dispatchers.IO) {
        val result = uploadMutex.withLock { uploadInternal() }
        val now = System.currentTimeMillis()
        if (result.ok) {
            sp.edit().putLong("lastUpload", now).remove("lastError").apply()
            _state.update { it.copy(lastUpload = now, lastError = null) }
        } else {
            sp.edit().putString("lastError", result.message).apply()
            _state.update { it.copy(lastError = result.message) }
        }
        refreshPending()
        result
    }

    private fun uploadInternal(): UploadResult {
        if (!enabled) return UploadResult(false, "Usage data is off")
        val token = secret.getString("token", null)?.trim()
        if (token.isNullOrEmpty()) return UploadResult(false, "No access token set")
        val repo = _state.value.repo
        if (!REPO_PATTERN.matches(repo)) return UploadResult(false, "Repository must look like owner/name")

        synchronized(lock) { rotateLocked() }
        val files = pendingDir.listFiles()?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return UploadResult(true, "Nothing new to upload")

        val meta = deviceMeta().toString()
        var sent = 0
        for (f in files) {
            val stamp = f.nameWithoutExtension
            val day = dayFormat.format(Date(stamp.toLongOrNull() ?: f.lastModified()))
            val path = "events/$day/$deviceTag-$stamp.jsonl"
            val code = try {
                put(repo, token, path, (meta + "\n" + f.readText()).toByteArray())
            } catch (e: IOException) {
                return UploadResult(false, "Network error (${e.javaClass.simpleName}), will retry", retry = true)
            }
            when {
                // 422 means the file already exists: an earlier upload succeeded before the local copy was deleted.
                code in 200..299 || code == 422 -> {
                    f.delete()
                    sent++
                }
                code == 401 -> return UploadResult(false, "Token rejected (401): check or renew it")
                code == 403 -> return UploadResult(false, "No write access (403): the token needs Contents read and write on $repo")
                code == 404 -> return UploadResult(false, "Repository not found (404): check the name and the token's repository access")
                code == 429 || code >= 500 -> return UploadResult(false, "GitHub unavailable ($code), will retry", retry = true)
                else -> return UploadResult(false, "Upload failed (HTTP $code)")
            }
        }
        return UploadResult(true, if (sent == 1) "Uploaded 1 file" else "Uploaded $sent files")
    }

    private fun deviceMeta(): JSONObject {
        val lm = app.getSystemService(LocationManager::class.java)
        val sm = app.getSystemService(SensorManager::class.java)
        val sensors = JSONArray()
        val types = listOf(
            Sensor.TYPE_PRESSURE to "baro",
            Sensor.TYPE_MAGNETIC_FIELD to "mag",
            Sensor.TYPE_GYROSCOPE to "gyro",
            Sensor.TYPE_ROTATION_VECTOR to "rotvec",
            Sensor.TYPE_GRAVITY to "gravity",
        )
        for ((type, label) in types) if (sm.getDefaultSensor(type) != null) sensors.put(label)
        return JSONObject()
            .put("meta", 1)
            .put("device", deviceTag)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("android", Build.VERSION.SDK_INT)
            .put("app", BuildConfig.VERSION_NAME)
            .put("code", BuildConfig.VERSION_CODE)
            .put("gnss_hw", lm.gnssHardwareModelName ?: "")
            .put("gnss_year", lm.gnssYearOfHardware)
            .put("sensors", sensors)
    }

    private fun put(repo: String, token: String, path: String, content: ByteArray): Int {
        val conn = URL("https://api.github.com/repos/$repo/contents/$path").openConnection() as HttpURLConnection
        try {
            val payload = JSONObject()
                .put("message", "Telemetry $path")
                .put("content", Base64.encodeToString(content, Base64.NO_WRAP))
                .toString()
                .toByteArray()
            conn.requestMethod = "PUT"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "GPS-Toolbox-Android")
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            runCatching { (if (code >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes() } }
            return code
        } finally {
            conn.disconnect()
        }
    }
}

class TelemetryUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val r = Telemetry.upload()
        return if (!r.ok && r.retry && runAttemptCount < 5) Result.retry() else Result.success()
    }
}
