package lu.andreasbraun.gpstoolbox.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

data class SensorState(
    /** Magnetic heading of the top edge of the phone, degrees 0..360. */
    val headingMagnetic: Float? = null,
    /** SensorManager.SENSOR_STATUS_* of the magnetometer / rotation vector. */
    val headingAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    /** Gravity in device coordinates (m/s²), low-pass filtered. Used by the level. */
    val gravity: Vec3? = null,
    val pressureHpa: Float? = null,
    val magnetic: Vec3? = null,
    val acceleration: Vec3? = null,
    val rotationRate: Vec3? = null,
    val lightLux: Float? = null,
    val available: Set<Int> = emptySet(),
)

/** Ref-counted owner of the phone's motion and environment sensors. */
object SensorEngine : SensorEventListener {
    private lateinit var sm: SensorManager
    private lateinit var displays: DisplayManager

    private val _state = MutableStateFlow(SensorState())
    val state: StateFlow<SensorState> = _state.asStateFlow()

    private val users = mutableSetOf<String>()
    private var registered = false

    private val rotationMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private var headingSin = 0.0
    private var headingCos = 1.0
    private var headingInit = false
    private var gravityFiltered: FloatArray? = null

    private val types = listOf(
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_PRESSURE,
        Sensor.TYPE_LIGHT,
    )

    fun init(context: Context) {
        sm = context.getSystemService(SensorManager::class.java)
        displays = context.getSystemService(DisplayManager::class.java)
        _state.update { it.copy(available = types.filter { t -> sm.getDefaultSensor(t) != null }.toSet()) }
    }

    fun acquire(tag: String) {
        users += tag
        update()
    }

    fun release(tag: String) {
        users -= tag
        update()
    }

    private fun update() {
        if (users.isNotEmpty() && !registered) {
            for (t in types) {
                val sensor = sm.getDefaultSensor(t) ?: continue
                val rate = if (t == Sensor.TYPE_PRESSURE || t == Sensor.TYPE_LIGHT) SensorManager.SENSOR_DELAY_NORMAL else SensorManager.SENSOR_DELAY_UI
                sm.registerListener(this, sensor, rate)
            }
            registered = true
        } else if (users.isEmpty() && registered) {
            sm.unregisterListener(this)
            registered = false
            headingInit = false
            gravityFiltered = null
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> onRotation(v)
            Sensor.TYPE_GRAVITY -> onGravity(v, alpha = 0.3f)
            Sensor.TYPE_ACCELEROMETER -> {
                _state.update { it.copy(acceleration = Vec3(v[0], v[1], v[2])) }
                if (Sensor.TYPE_GRAVITY !in _state.value.available) onGravity(v, alpha = 0.1f)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> _state.update { it.copy(magnetic = Vec3(v[0], v[1], v[2])) }
            Sensor.TYPE_GYROSCOPE -> _state.update {
                it.copy(rotationRate = Vec3(Math.toDegrees(v[0].toDouble()).toFloat(), Math.toDegrees(v[1].toDouble()).toFloat(), Math.toDegrees(v[2].toDouble()).toFloat()))
            }
            Sensor.TYPE_PRESSURE -> _state.update { it.copy(pressureHpa = v[0]) }
            Sensor.TYPE_LIGHT -> _state.update { it.copy(lightLux = v[0]) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            _state.update { it.copy(headingAccuracy = accuracy) }
        }
    }

    private fun onRotation(values: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        val rotation = displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        val (ax, ay) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, ax, ay, remapped)
        SensorManager.getOrientation(remapped, orientation)
        val az = orientation[0].toDouble()
        // Smooth on the unit circle so the needle does not jump at 0/360.
        if (!headingInit) {
            headingSin = sin(az); headingCos = cos(az); headingInit = true
        } else {
            headingSin = headingSin * 0.85 + sin(az) * 0.15
            headingCos = headingCos * 0.85 + cos(az) * 0.15
        }
        var deg = Math.toDegrees(atan2(headingSin, headingCos)).toFloat()
        if (deg < 0) deg += 360f
        _state.update { it.copy(headingMagnetic = deg) }
    }

    private fun onGravity(v: FloatArray, alpha: Float) {
        val g = gravityFiltered
        val next = if (g == null) v.copyOf(3) else floatArrayOf(
            g[0] + alpha * (v[0] - g[0]),
            g[1] + alpha * (v[1] - g[1]),
            g[2] + alpha * (v[2] - g[2]),
        )
        gravityFiltered = next
        _state.update { it.copy(gravity = Vec3(next[0], next[1], next[2])) }
    }
}
