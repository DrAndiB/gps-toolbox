package lu.andreasbraun.gpstoolbox

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import lu.andreasbraun.gpstoolbox.data.Agnss
import lu.andreasbraun.gpstoolbox.data.Averager
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.data.RawLogger
import lu.andreasbraun.gpstoolbox.data.TrackRecorder
import lu.andreasbraun.gpstoolbox.data.WaypointStore
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.engine.SensorEngine
import lu.andreasbraun.gpstoolbox.service.LiveService

/** Process-wide scope for work that must outlive a screen (recording, logging, averaging). */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        GnssEngine.init(this)
        SensorEngine.init(this)
        WaypointStore.init(this)
        TrackRecorder.init(this)
        RawLogger.init(this)
        Averager.init()
        Agnss.init(this)
        LiveService.createChannel(this)
        // Keeps the background assistance-data refresh scheduled, even if the app is rarely opened.
        Agnss.schedule(this)
    }
}
