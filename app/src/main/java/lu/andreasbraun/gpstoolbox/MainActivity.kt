package lu.andreasbraun.gpstoolbox

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import lu.andreasbraun.gpstoolbox.data.Background
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import lu.andreasbraun.gpstoolbox.ui.AppRoot
import lu.andreasbraun.gpstoolbox.ui.theme.GpsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { GpsTheme { AppRoot() } }
    }

    private var startedAt = 0L

    override fun onStart() {
        super.onStart()
        startedAt = SystemClock.elapsedRealtime()
        Telemetry.foreground = true
        Telemetry.event(
            "app_open",
            "v" to BuildConfig.VERSION_CODE,
            "screen" to Telemetry.lastScreen,
            "permission" to GnssEngine.hasPermission(),
        )
        GnssEngine.acquire(UI)
        SensorEngine.acquire(UI)
        if (Prefs.current.liveNotification) Background.require(this, Background.Reason.NOTIFICATION)
    }

    override fun onResume() {
        super.onResume()
        // Picks up permission or location-setting changes made outside the app.
        GnssEngine.refresh()
    }

    override fun onStop() {
        Telemetry.foreground = false
        Telemetry.event("app_close", "fg_s" to (SystemClock.elapsedRealtime() - startedAt) / 1000)
        Telemetry.uploadSoon(this)
        GnssEngine.release(UI)
        SensorEngine.release(UI)
        super.onStop()
    }

    private companion object {
        const val UI = "ui"
    }
}
