package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsSpeedTest {
    @Test
    fun convertsAFreshGpsSpeedToKilometersPerHour() {
        assertEquals(36, GpsSpeed.kmh(10.0, 0L))
        assertEquals(0, GpsSpeed.kmh(0.0, 1_000L))
    }

    @Test
    fun hidesMissingStaleOrImpossibleSpeeds() {
        assertNull(GpsSpeed.kmh(null, 0L))
        assertNull(GpsSpeed.kmh(10.0, 4_001L))
        assertNull(GpsSpeed.kmh(-1.0, 0L))
        assertNull(GpsSpeed.kmh(Double.NaN, 0L))
    }

    @Test
    fun namesCompassPointsFromBearing() {
        assertEquals("É", GpsSpeed.compassLetter(0.0))
        assertEquals("K", GpsSpeed.compassLetter(90.0))
        assertEquals("D", GpsSpeed.compassLetter(180.0))
        assertEquals("NY", GpsSpeed.compassLetter(-90.0))
        assertEquals("·", GpsSpeed.compassLetter(null))
    }
}
