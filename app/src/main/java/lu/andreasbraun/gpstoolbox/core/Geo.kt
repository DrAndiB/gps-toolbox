package lu.andreasbraun.gpstoolbox.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Small-area geodesy helpers on a spherical Earth (good to ~0.3 % over any distance). */
object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial great-circle bearing from point 1 to point 2, degrees 0..360 clockwise from true north. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    /** East/north offset in metres of (lat, lon) relative to (lat0, lon0). Accurate for offsets up to a few km. */
    fun eastNorth(lat0: Double, lon0: Double, lat: Double, lon: Double): Pair<Double, Double> {
        val east = Math.toRadians(lon - lon0) * EARTH_RADIUS_M * cos(Math.toRadians(lat0))
        val north = Math.toRadians(lat - lat0) * EARTH_RADIUS_M
        return east to north
    }

    fun normalize(deg: Double): Double {
        val d = deg % 360.0
        return if (d < 0) d + 360.0 else d
    }

    fun normalize(deg: Float): Float {
        val d = deg % 360f
        return if (d < 0) d + 360f else d
    }

    private val CARDINALS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    fun cardinal(deg: Double): String = CARDINALS[(((normalize(deg) + 11.25) / 22.5).toInt()) % 16]
}
