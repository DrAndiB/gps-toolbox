package lu.andreasbraun.gpstoolbox.core

/** Constellation ids match android.location.GnssStatus.CONSTELLATION_*. */
enum class Constellation(val type: Int, val label: String, val prefix: Char, val argb: Long) {
    GPS(1, "GPS", 'G', 0xFF6EA8FE),
    GALILEO(6, "Galileo", 'E', 0xFFF5B83D),
    BEIDOU(5, "BeiDou", 'C', 0xFF4CC9B0),
    GLONASS(3, "GLONASS", 'R', 0xFFE86F6F),
    QZSS(4, "QZSS", 'J', 0xFFE79AD0),
    IRNSS(7, "NavIC", 'I', 0xFFFFB38A),
    SBAS(2, "SBAS", 'S', 0xFFB7A6F0),
    UNKNOWN(0, "Other", '?', 0xFF9AA4AE);

    companion object {
        fun fromType(type: Int): Constellation = entries.firstOrNull { it.type == type } ?: UNKNOWN
    }
}

/** Frequency bands, grouped the way most phones report them. */
enum class Band(val label: String) {
    L1("L1 · E1 · B1"),
    L5("L5 · E5a · B2a"),
    E5B("E5b · B2I"),
    L2("L2"),
    E6("E6 · B3"),
    UNKNOWN("Unknown");

    companion object {
        fun of(frequencyHz: Float?): Band {
            val mhz = (frequencyHz ?: return L1) / 1e6f
            return when {
                mhz > 1500f -> L1          // GPS/Galileo/QZSS L1 1575.42, BeiDou B1 1561/1575, GLONASS G1 ~1602
                mhz in 1170f..1182f -> L5  // L5 / E5a / B2a 1176.45
                mhz in 1195f..1215f -> E5B // E5b / B2I 1207.14
                mhz in 1220f..1250f -> L2  // GPS L2 1227.6, GLONASS G2 ~1246
                mhz in 1255f..1290f -> E6  // Galileo E6 1278.75, BeiDou B3 1268.52
                else -> UNKNOWN
            }
        }
    }
}

data class SatSignal(
    val constellation: Constellation,
    val svid: Int,
    val band: Band,
    val cn0: Float,
    val elevation: Float,
    val azimuth: Float,
    val usedInFix: Boolean,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean,
)

/** One space vehicle, possibly tracked on several bands. */
data class Satellite(
    val constellation: Constellation,
    val svid: Int,
    val elevation: Float,
    val azimuth: Float,
    val usedInFix: Boolean,
    val hasEphemeris: Boolean,
    val signals: List<SatSignal>,
) {
    val label: String get() = constellation.prefix + svid.toString().padStart(2, '0')
    val bestCn0: Float get() = signals.maxOfOrNull { it.cn0 } ?: 0f
    val tracked: Boolean get() = bestCn0 > 0f
    fun cn0On(band: Band): Float? = signals.firstOrNull { it.band == band && it.cn0 > 0f }?.cn0
}

data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    val verticalAccuracy: Float?,
    val altitudeEllipsoid: Double?,
    val altitudeMsl: Double?,
    val speed: Float?,
    val bearing: Float?,
    val time: Long,
    val elapsedNanos: Long,
) {
    val altitude: Double? get() = altitudeMsl ?: altitudeEllipsoid
}

data class Dop(val pdop: Double? = null, val hdop: Double? = null, val vdop: Double? = null)

data class AccuracySample(val elapsedMs: Long, val horizontal: Float)

data class GnssState(
    val permission: Boolean = false,
    val providerEnabled: Boolean = true,
    val running: Boolean = false,
    val fix: Fix? = null,
    /** 1 = none, 2 = 2D, 3 = 3D (from NMEA GSA), 0 = unknown. */
    val fixType: Int = 0,
    val dop: Dop = Dop(),
    val satellites: List<Satellite> = emptyList(),
    val updateRateHz: Double? = null,
    val lastTtffMs: Int? = null,
    val firstFixPending: Boolean = false,
) {
    val tracked: Int get() = satellites.count { it.tracked }
    val used: Int get() = satellites.count { it.usedInFix }
    val dualBand: Boolean
        get() = satellites.any { s -> s.usedInFix && s.signals.any { it.band == Band.L5 && it.usedInFix } }
    val bandsInUse: List<Band>
        get() = satellites.flatMap { s -> s.signals.filter { it.cn0 > 0f }.map { it.band } }.distinct().sortedBy { it.ordinal }
}
