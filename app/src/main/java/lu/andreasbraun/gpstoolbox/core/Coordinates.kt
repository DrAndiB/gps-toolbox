package lu.andreasbraun.gpstoolbox.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class CoordFormat(val label: String) {
    DD("DD"),
    DDM("DDM"),
    DMS("DMS"),
    UTM("UTM"),
    MGRS("MGRS"),
}

data class Utm(val zone: Int, val band: Char, val easting: Double, val northing: Double, val north: Boolean)

/**
 * WGS84 coordinate formatting. UTM uses the Snyder/USGS series (sub-millimetre inside a zone);
 * MGRS uses the AA lettering scheme for WGS84. Both were cross-checked against pyproj and the
 * mgrs reference library (see CoordinatesTest).
 */
object Coordinates {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private val E2 = F * (2 - F)
    private val EP2 = E2 / (1 - E2)

    private const val BANDS = "CDEFGHJKLMNPQRSTUVWX"
    private val COLUMN_SETS = arrayOf("ABCDEFGH", "JKLMNPQR", "STUVWXYZ")
    private const val ROW_LETTERS = "ABCDEFGHJKLMNPQRSTUV"

    fun utmZone(lat: Double, lon: Double): Int {
        var zone = floor((lon + 180) / 6).toInt() + 1
        if (zone > 60) zone = 60
        if (zone < 1) zone = 1
        if (lat >= 56 && lat < 64 && lon >= 3 && lon < 12) zone = 32
        if (lat >= 72 && lat <= 84) {
            zone = when {
                lon >= 0 && lon < 9 -> 31
                lon >= 9 && lon < 21 -> 33
                lon >= 21 && lon < 33 -> 35
                lon >= 33 && lon < 42 -> 37
                else -> zone
            }
        }
        return zone
    }

    fun latitudeBand(lat: Double): Char {
        val i = floor((lat + 80) / 8).toInt().coerceIn(0, 19)
        return BANDS[i]
    }

    /** Returns null outside the UTM latitude range (-80..84). */
    fun utm(lat: Double, lon: Double): Utm? {
        if (lat < -80 || lat > 84 || lon.isNaN() || lat.isNaN()) return null
        val zone = utmZone(lat, lon)
        val lon0 = Math.toRadians((zone - 1) * 6 - 180 + 3.0)
        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(lon)

        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val n = A / sqrt(1 - E2 * sinPhi * sinPhi)
        val t = tan(phi).pow(2)
        val c = EP2 * cosPhi * cosPhi
        val a = cosPhi * (lambda - lon0)
        val e4 = E2 * E2
        val e6 = e4 * E2
        val m = A * (
            (1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi -
                (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * sin(2 * phi) +
                (15 * e4 / 256 + 45 * e6 / 1024) * sin(4 * phi) -
                (35 * e6 / 3072) * sin(6 * phi)
            )
        val easting = K0 * n * (
            a + (1 - t + c) * a.pow(3) / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * EP2) * a.pow(5) / 120
            ) + 500000.0
        var northing = K0 * (
            m + n * tan(phi) * (
                a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24 +
                    (61 - 58 * t + t * t + 600 * c - 330 * EP2) * a.pow(6) / 720
                )
            )
        if (lat < 0) northing += 10_000_000.0
        return Utm(zone, latitudeBand(lat), easting, northing, lat >= 0)
    }

    /** MGRS with [digits] digits per axis (5 = 1 m). Truncates, as the MGRS standard requires. */
    fun mgrs(lat: Double, lon: Double, digits: Int = 5): String? {
        val u = utm(lat, lon) ?: return null
        val set = (u.zone - 1) % 3
        val colIndex = (floor(u.easting / 100000).toInt() - 1).coerceIn(0, 7)
        val col = COLUMN_SETS[set][colIndex]
        val rowOffset = if (u.zone % 2 == 1) 0 else 5
        val row = ROW_LETTERS[(floor(u.northing / 100000).toInt() + rowOffset) % 20]
        val divisor = 10.0.pow(5 - digits)
        val e = floor((u.easting % 100000) / divisor).toLong()
        val nn = floor((u.northing % 100000) / divisor).toLong()
        val pattern = "%0${digits}d"
        return String.format(
            Locale.ROOT, "%d%c %c%c $pattern $pattern",
            u.zone, u.band, col, row, e, nn,
        )
    }

    /** Two display lines for the big position readout. */
    fun lines(lat: Double, lon: Double, format: CoordFormat): Pair<String, String> = when (format) {
        CoordFormat.DD -> ddPart(lat, true) to ddPart(lon, false)
        CoordFormat.DDM -> ddmPart(lat, true) to ddmPart(lon, false)
        CoordFormat.DMS -> dmsPart(lat, true) to dmsPart(lon, false)
        CoordFormat.UTM -> utm(lat, lon)?.let {
            String.format(Locale.ROOT, "%d%c  %s E", it.zone, it.band, group(it.easting.toLong())) to
                String.format(Locale.ROOT, "%s N", group(it.northing.toLong()))
        } ?: ("Outside UTM" to "use DD near the poles")
        CoordFormat.MGRS -> mgrs(lat, lon)?.let {
            val parts = it.split(" ")
            "${parts[0]} ${parts[1]}" to "${parts[2]} ${parts[3]}"
        } ?: ("Outside MGRS" to "use DD near the poles")
    }

    /** One line, suitable for copying or a notification. DD uses the machine-friendly "lat, lon" form. */
    fun singleLine(lat: Double, lon: Double, format: CoordFormat): String = when (format) {
        CoordFormat.DD -> String.format(Locale.ROOT, "%.6f, %.6f", lat, lon)
        CoordFormat.UTM -> utm(lat, lon)?.let {
            String.format(Locale.ROOT, "%d%c %d %d", it.zone, it.band, it.easting.toLong(), it.northing.toLong())
        } ?: singleLine(lat, lon, CoordFormat.DD)
        CoordFormat.MGRS -> mgrs(lat, lon) ?: singleLine(lat, lon, CoordFormat.DD)
        else -> lines(lat, lon, format).let { "${it.first}, ${it.second}" }
    }

    private fun hemi(value: Double, isLat: Boolean): Char =
        if (isLat) (if (value >= 0) 'N' else 'S') else (if (value >= 0) 'E' else 'W')

    private fun ddPart(value: Double, isLat: Boolean): String {
        val s = String.format(Locale.ROOT, "%.6f", abs(value))
        val dot = s.indexOf('.')
        // Thin grouping after the third decimal makes long numbers easier to read: 49.611 620°
        val grouped = s.substring(0, dot + 4) + " " + s.substring(dot + 4)
        return "$grouped° ${hemi(value, isLat)}"
    }

    private fun ddmPart(value: Double, isLat: Boolean): String {
        val v = abs(value)
        var deg = floor(v).toInt()
        var min = (v - deg) * 60
        if (min >= 59.99995) { deg += 1; min = 0.0 }
        return String.format(Locale.ROOT, "%d° %07.4f′ %c", deg, min, hemi(value, isLat))
    }

    private fun dmsPart(value: Double, isLat: Boolean): String {
        val v = abs(value)
        var deg = floor(v).toInt()
        val totalMin = (v - deg) * 60
        var min = floor(totalMin).toInt()
        var sec = (totalMin - min) * 60
        if (sec >= 59.995) { sec = 0.0; min += 1 }
        if (min >= 60) { min = 0; deg += 1 }
        return String.format(Locale.ROOT, "%d° %02d′ %05.2f″ %c", deg, min, sec, hemi(value, isLat))
    }

    private fun group(v: Long): String {
        val s = v.toString()
        val sb = StringBuilder()
        for ((i, ch) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(' ')
            sb.append(ch)
        }
        return sb.toString()
    }
}
