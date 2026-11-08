package lu.andreasbraun.gpstoolbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaAndGeoTest {
    @Test
    fun parsesGga() {
        val gga = Nmea.parse("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47") as Nmea.Gga
        assertEquals(1, gga.quality)
        assertEquals(8, gga.satellites)
        assertEquals(545.4, gga.mslAltitude!!, 1e-9)
        assertEquals(46.9, gga.geoidSeparation!!, 1e-9)
    }

    @Test
    fun parsesGsaWithSystemId() {
        val body = "GNGSA,A,3,05,13,15,29,,,,,,,,,1.4,1.1,0.9,1"
        var cs = 0
        for (ch in body) cs = cs xor ch.code
        val line = "$" + body + "*" + String.format("%02X", cs)
        val gsa = Nmea.parse(line) as Nmea.Gsa
        assertEquals(3, gsa.fixType)
        assertEquals(1.4, gsa.pdop!!, 1e-9)
        assertEquals(1.1, gsa.hdop!!, 1e-9)
        assertEquals(0.9, gsa.vdop!!, 1e-9)
    }

    @Test
    fun rejectsBadChecksum() {
        assertFalse(Nmea.checksumOk("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*48"))
        assertNull(Nmea.parse("garbage"))
    }

    @Test
    fun distanceAndBearing() {
        // Luxembourg City to Trier, roughly 41 km to the north-east
        val d = Geo.distance(49.6116, 6.1319, 49.7499, 6.6371)
        assertTrue("distance $d", d in 39_000.0..41_000.0)
        val b = Geo.bearing(49.6116, 6.1319, 49.7499, 6.6371)
        assertTrue("bearing $b", b in 60.0..70.0)
        assertEquals("NE", Geo.cardinal(45.0))
        assertEquals("N", Geo.cardinal(359.0))
    }

    @Test
    fun eastNorthOffsets() {
        val (e, n) = Geo.eastNorth(49.0, 6.0, 49.0 + 1.0 / 111_195.0, 6.0)
        assertEquals(0.0, e, 1e-6)
        assertEquals(1.0, n, 0.01)
    }

    @Test
    fun gpxEscapesNames() {
        val gpx = Gpx.waypoints(listOf(Waypoint("1", "Car & bike <here>", 49.0, 6.0, 300.0, 2f, 0L)))
        assertTrue(gpx.contains("<name>Car &amp; bike &lt;here&gt;</name>"))
        assertTrue(gpx.contains("lat=\"49.0000000\" lon=\"6.0000000\""))
    }
}
