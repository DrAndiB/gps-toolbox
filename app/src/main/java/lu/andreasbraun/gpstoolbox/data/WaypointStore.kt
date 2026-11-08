package lu.andreasbraun.gpstoolbox.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lu.andreasbraun.gpstoolbox.AppScope
import lu.andreasbraun.gpstoolbox.core.Fix
import lu.andreasbraun.gpstoolbox.core.Waypoint
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Waypoints kept as a small JSON file in app storage. */
object WaypointStore {
    private lateinit var file: File
    private val _items = MutableStateFlow<List<Waypoint>>(emptyList())
    val items: StateFlow<List<Waypoint>> = _items.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "waypoints.json")
        _items.value = runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    fun nextName(): String = "WP ${_items.value.size + 1}"

    fun add(name: String, lat: Double, lon: Double, altitude: Double?, accuracy: Float?): Waypoint {
        val wp = Waypoint(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { nextName() },
            lat = lat,
            lon = lon,
            altitude = altitude,
            accuracy = accuracy,
            time = System.currentTimeMillis(),
        )
        _items.value = _items.value + wp
        persist()
        Telemetry.event("waypoint_add", "acc" to accuracy, "total" to _items.value.size)
        return wp
    }

    fun addFromFix(fix: Fix, name: String = nextName()): Waypoint =
        add(name, fix.lat, fix.lon, fix.altitude, fix.accuracy)

    fun rename(id: String, name: String) {
        _items.value = _items.value.map { if (it.id == id) it.copy(name = name) else it }
        persist()
        Telemetry.event("waypoint_rename")
    }

    fun delete(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
        persist()
        Telemetry.event("waypoint_delete", "total" to _items.value.size)
    }

    private fun persist() {
        val snapshot = _items.value
        AppScope.launch(Dispatchers.IO) {
            val arr = JSONArray()
            for (w in snapshot) {
                arr.put(
                    JSONObject()
                        .put("id", w.id).put("name", w.name)
                        .put("lat", w.lat).put("lon", w.lon)
                        .put("alt", w.altitude ?: JSONObject.NULL)
                        .put("acc", w.accuracy?.toDouble() ?: JSONObject.NULL)
                        .put("time", w.time),
                )
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString(2))
            tmp.renameTo(file)
        }
    }

    private fun parse(text: String): List<Waypoint> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Waypoint(
                id = o.getString("id"),
                name = o.getString("name"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                altitude = if (o.isNull("alt")) null else o.getDouble("alt"),
                accuracy = if (o.isNull("acc")) null else o.getDouble("acc").toFloat(),
                time = o.getLong("time"),
            )
        }
    }
}
