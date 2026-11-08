package lu.andreasbraun.gpstoolbox.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lu.andreasbraun.gpstoolbox.core.CoordFormat

data class Settings(
    val coordFormat: CoordFormat = CoordFormat.DD,
    val keepScreenOn: Boolean = false,
    val liveNotification: Boolean = false,
    val agnssAuto: Boolean = true,
    val agnssIntervalHours: Int = 6,
    val agnssWifiOnly: Boolean = true,
    val trueNorth: Boolean = true,
    val levelOffsetX: Float = 0f,
    val levelOffsetY: Float = 0f,
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _settings.value = Settings(
            coordFormat = runCatching { CoordFormat.valueOf(sp.getString("coordFormat", "DD")!!) }.getOrDefault(CoordFormat.DD),
            keepScreenOn = sp.getBoolean("keepScreenOn", false),
            liveNotification = sp.getBoolean("liveNotification", false),
            agnssAuto = sp.getBoolean("agnssAuto", true),
            agnssIntervalHours = sp.getInt("agnssIntervalHours", 6),
            agnssWifiOnly = sp.getBoolean("agnssWifiOnly", true),
            trueNorth = sp.getBoolean("trueNorth", true),
            levelOffsetX = sp.getFloat("levelOffsetX", 0f),
            levelOffsetY = sp.getFloat("levelOffsetY", 0f),
        )
    }

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        _settings.value = s
        sp.edit()
            .putString("coordFormat", s.coordFormat.name)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("liveNotification", s.liveNotification)
            .putBoolean("agnssAuto", s.agnssAuto)
            .putInt("agnssIntervalHours", s.agnssIntervalHours)
            .putBoolean("agnssWifiOnly", s.agnssWifiOnly)
            .putBoolean("trueNorth", s.trueNorth)
            .putFloat("levelOffsetX", s.levelOffsetX)
            .putFloat("levelOffsetY", s.levelOffsetY)
            .apply()
    }
}
