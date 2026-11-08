package lu.andreasbraun.gpstoolbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected values come from pyproj (UTM) and the `mgrs` Python package (MGRS). */
class CoordinatesTest {
    private data class Case(val lat: Double, val lon: Double, val zone: Int, val e: Double, val n: Double, val mgrs: String)

    private val cases = listOf(
        Case(49.6116, 6.1319, 32, 292814.282, 5499399.330, "32UKV9281499399"),
        Case(48.8584, 2.2945, 31, 448252.001, 5411954.910, "31UDQ4825211954"),
        Case(-33.8568, 151.2153, 56, 334900.570, 6252288.753, "56HLH3490052288"),
        Case(60.39, 5.32, 32, 297230.220, 6700510.176, "32VKN9723000510"),
        Case(78.22, 15.65, 33, 514813.527, 8683004.154, "33XWG1481383004"),
        Case(0.0, 0.0, 31, 166021.443, 0.000, "31NAA6602100000"),
        Case(-0.0001, -0.0001, 30, 833967.414, 9999988.932, "30MZE3396799988"),
        Case(40.6892, -74.0445, 18, 580735.871, 4504695.165, "18TWL8073504695"),
        Case(35.6586, 139.7454, 54, 386437.603, 3946808.155, "54SUE8643746808"),
        Case(-54.8, -68.3, 19, 545000.053, 3927239.381, "19FEV4500027239"),
        Case(83.5, -30.0, 26, 462101.337, 9273261.909, "26XMT6210173261"),
        Case(-79.9, 10.0, 32, 519576.611, 1129407.482, "32CNS1957629407"),
    )

    @Test
    fun utmMatchesReference() {
        for (c in cases) {
            val u = Coordinates.utm(c.lat, c.lon)!!
            assertEquals("zone for $c", c.zone, u.zone)
            assertEquals("easting for $c", c.e, u.easting, 0.01)
            assertEquals("northing for $c", c.n, u.northing, 0.01)
        }
    }

    @Test
    fun mgrsMatchesReference() {
        for (c in cases) {
            assertEquals("mgrs for $c", c.mgrs, Coordinates.mgrs(c.lat, c.lon)!!.replace(" ", ""))
        }
    }

    @Test
    fun mgrsPrecisionTruncates() {
        assertEquals("32U KV 928 993", Coordinates.mgrs(49.6116, 6.1319, digits = 3))
    }

    @Test
    fun polarRegionsHaveNoUtm() {
        assertNull(Coordinates.utm(85.0, 10.0))
        assertNull(Coordinates.mgrs(-81.0, 10.0))
    }

    @Test
    fun degreeFormats() {
        val (lat, lon) = Coordinates.lines(49.6116195, 6.1319432, CoordFormat.DMS)
        assertEquals("49° 36′ 41.83″ N", lat)
        assertEquals("6° 07′ 55.00″ E", lon)
        val (dlat, dlon) = Coordinates.lines(-33.85681, -0.5, CoordFormat.DDM)
        assertEquals("33° 51.4086′ S", dlat)
        assertEquals("0° 30.0000′ W", dlon)
        assertEquals("49.611620, 6.131943", Coordinates.singleLine(49.6116195, 6.1319432, CoordFormat.DD))
    }

    @Test
    fun dmsRollsOverCleanly() {
        val (lat, _) = Coordinates.lines(49.99999999, 6.0, CoordFormat.DMS)
        assertEquals("50° 00′ 00.00″ N", lat)
    }
}
