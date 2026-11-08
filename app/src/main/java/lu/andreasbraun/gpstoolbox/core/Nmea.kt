package lu.andreasbraun.gpstoolbox.core

/** Minimal NMEA 0183 parsing for the values Android's Location API does not expose (DOP, fix type, MSL altitude). */
object Nmea {
    sealed interface Sentence

    /** GGA: fix quality 0 = none, 1 = GNSS, 2 = DGNSS, 4/5 = RTK. */
    data class Gga(val quality: Int, val satellites: Int?, val hdop: Double?, val mslAltitude: Double?, val geoidSeparation: Double?) : Sentence

    /** GSA: fix type 1 = none, 2 = 2D, 3 = 3D. */
    data class Gsa(val fixType: Int, val pdop: Double?, val hdop: Double?, val vdop: Double?) : Sentence

    fun checksumOk(line: String): Boolean {
        val s = line.trim()
        val star = s.lastIndexOf('*')
        if (!s.startsWith("$") || star < 0 || star + 3 > s.length) return false
        var cs = 0
        for (i in 1 until star) cs = cs xor s[i].code
        val given = s.substring(star + 1, star + 3).toIntOrNull(16) ?: return false
        return cs == given
    }

    fun parse(line: String): Sentence? {
        val s = line.trim()
        if (s.length < 7 || !checksumOk(s)) return null
        val body = s.substring(1, s.lastIndexOf('*'))
        val f = body.split(',')
        if (f.isEmpty() || f[0].length < 5) return null
        return when (f[0].substring(f[0].length - 3)) {
            "GGA" -> if (f.size >= 12) Gga(
                quality = f[6].toIntOrNull() ?: 0,
                satellites = f[7].toIntOrNull(),
                hdop = f[8].toDoubleOrNull(),
                mslAltitude = f[9].toDoubleOrNull(),
                geoidSeparation = f[11].toDoubleOrNull(),
            ) else null
            "GSA" -> if (f.size >= 18) Gsa(
                fixType = f[2].toIntOrNull() ?: 1,
                pdop = f[15].toDoubleOrNull()?.takeIf { it > 0 },
                hdop = f[16].toDoubleOrNull()?.takeIf { it > 0 },
                vdop = f[17].toDoubleOrNull()?.takeIf { it > 0 },
            ) else null
            else -> null
        }
    }
}
