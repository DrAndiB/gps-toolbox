package lu.andreasbraun.gpstoolbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lu.andreasbraun.gpstoolbox.MainActivity
import lu.andreasbraun.gpstoolbox.R
import lu.andreasbraun.gpstoolbox.core.Coordinates
import lu.andreasbraun.gpstoolbox.data.Averager
import lu.andreasbraun.gpstoolbox.data.Background
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.data.RawLogger
import lu.andreasbraun.gpstoolbox.data.TrackRecorder
import lu.andreasbraun.gpstoolbox.data.WaypointStore
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.util.Share
import java.util.Locale

/**
 * Keeps GNSS running while the app is in the background (live notification, track recording,
 * averaging or logging) and shows the current position in an ongoing notification.
 */
class LiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GnssEngine.acquire(TAG)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always enter the foreground first: Android requires it shortly after startForegroundService.
        enterForeground()
        when (intent?.action) {
            ACTION_COPY -> GnssEngine.state.value.fix?.let {
                Share.copy(this, "Position", Coordinates.singleLine(it.lat, it.lon, Prefs.current.coordFormat))
            }
            ACTION_WAYPOINT -> {
                val fix = GnssEngine.state.value.fix
                if (fix != null) {
                    val wp = WaypointStore.addFromFix(fix)
                    Share.toast(this, "Saved ${wp.name}")
                } else {
                    Share.toast(this, "No fix yet")
                }
            }
            ACTION_STOP -> stopEverything()
        }
        if (!started) {
            started = true
            scope.launch {
                // Stop as soon as nothing needs background GNSS any more.
                Background.reasons.collectLatest { reasons ->
                    if (reasons.isEmpty()) stopSelfCleanly()
                }
            }
            scope.launch {
                while (true) {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification())
                    delay(2000)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }.onFailure {
            // Location permission missing or started from the background: give up quietly.
            stopSelf()
        }
    }

    private fun stopEverything() {
        Prefs.update { it.copy(liveNotification = false) }
        Background.drop(Background.Reason.NOTIFICATION)
        if (Averager.state.value.running) Averager.stop()
        scope.launch {
            if (TrackRecorder.state.value.recording) {
                val file = TrackRecorder.stop()
                if (file != null) Share.toast(this@LiveService, "Track saved")
            }
            if (RawLogger.state.value.nmeaActive) RawLogger.stopNmea()
            if (RawLogger.state.value.rawActive) RawLogger.stopRaw()
        }
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        GnssEngine.release(TAG)
        super.onDestroy()
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun buildNotification(): Notification {
        val state = GnssEngine.state.value
        val fix = state.fix
        val reasons = Background.reasons.value
        val activity = mutableListOf<String>()
        if (Background.Reason.TRACK in reasons) {
            val t = TrackRecorder.state.value
            activity += String.format(Locale.ROOT, "Recording %.2f km", t.distanceM / 1000)
        }
        if (Background.Reason.AVERAGE in reasons) {
            activity += "Averaging ${Averager.state.value.result?.count ?: 0} fixes"
        }
        if (Background.Reason.LOG in reasons) activity += "Logging"

        val title = buildString {
            append("GPS Toolbox")
            if (activity.isNotEmpty()) append(" · ").append(activity.joinToString(" · "))
        }
        val accuracy = fix?.accuracy?.let { String.format(Locale.ROOT, "±%.1f m", it) }
        val coords = fix?.let { Coordinates.singleLine(it.lat, it.lon, Prefs.current.coordFormat) } ?: "Waiting for fix…"
        val details = buildString {
            append("${state.used}/${state.tracked} sats")
            fix?.speed?.let { append(String.format(Locale.ROOT, " · %.1f km/h", it * 3.6)) }
            fix?.altitude?.let { append(String.format(Locale.ROOT, " · %.0f m", it)) }
        }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(listOfNotNull(accuracy, coords).joinToString("  "))
            .setSubText(accuracy)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$coords\n$details"))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Copy", action(ACTION_COPY, 1))
            .addAction(0, "Waypoint", action(ACTION_WAYPOINT, 2))
            .addAction(0, "Stop", action(ACTION_STOP, 3))
            .build()
    }

    private fun action(name: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, LiveService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "service"
        const val CHANNEL_ID = "live"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_COPY = "copy"
        private const val ACTION_WAYPOINT = "waypoint"
        private const val ACTION_STOP = "stop"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Live position", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing notification while GNSS runs in the background"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
