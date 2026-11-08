package lu.andreasbraun.gpstoolbox.core

import java.time.Instant
import java.util.Locale

data class Waypoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val time: Long,
)

data class TrackPoint(val lat: Double, val lon: Double, val altitude: Double?, val time: Long, val accuracy: Float?)

object Gpx {
    private const val HEADER = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GPS Toolbox" xmlns="http://www.topografix.com/GPX/1/1">
"""

    fun waypoints(list: List<Waypoint>): String = buildString {
        append(HEADER)
        for (w in list) {
            append(String.format(Locale.ROOT, "  <wpt lat=\"%.7f\" lon=\"%.7f\">\n", w.lat, w.lon))
            w.altitude?.let { append(String.format(Locale.ROOT, "    <ele>%.1f</ele>\n", it)) }
            append("    <time>${Instant.ofEpochMilli(w.time)}</time>\n")
            append("    <name>${escape(w.name)}</name>\n")
            w.accuracy?.let { append(String.format(Locale.ROOT, "    <desc>accuracy %.1f m</desc>\n", it)) }
            append("  </wpt>\n")
        }
        append("</gpx>\n")
    }

    fun track(name: String, points: List<TrackPoint>): String = buildString {
        append(HEADER)
        append("  <trk>\n    <name>${escape(name)}</name>\n    <trkseg>\n")
        for (p in points) {
            append(String.format(Locale.ROOT, "      <trkpt lat=\"%.7f\" lon=\"%.7f\">", p.lat, p.lon))
            p.altitude?.let { append(String.format(Locale.ROOT, "<ele>%.1f</ele>", it)) }
            append("<time>${Instant.ofEpochMilli(p.time)}</time>")
            p.accuracy?.let { append(String.format(Locale.ROOT, "<desc>±%.1f m</desc>", it)) }
            append("</trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
