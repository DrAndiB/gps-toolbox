package lu.andreasbraun.gpstoolbox.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.service.LiveService

/**
 * Reasons to keep GNSS running while the app is in the background. While the set is non-empty,
 * LiveService runs as a foreground service with the live notification.
 */
object Background {
    enum class Reason { NOTIFICATION, TRACK, AVERAGE, LOG }

    private val _reasons = MutableStateFlow<Set<Reason>>(emptySet())
    val reasons: StateFlow<Set<Reason>> = _reasons.asStateFlow()

    /** Must be called while the app is visible: Android only allows starting a location service from the foreground. */
    fun require(context: Context, reason: Reason) {
        if (!GnssEngine.hasPermission()) return
        _reasons.value = _reasons.value + reason
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, LiveService::class.java))
        }
    }

    fun drop(reason: Reason) {
        _reasons.value = _reasons.value - reason
    }
}
