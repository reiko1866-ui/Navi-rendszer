package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos

class VehicleHeadingTest {
    private val lat = 47.0
    private val lon = 19.0

    @Test
    fun usesTheGpsCourseWhileMoving() {
        assertEquals(12.0, VehicleHeading.choose(12.0, 8.0, 90.0)!!, 0.001)
        assertEquals(270.0, VehicleHeading.choose(-90.0, 1.0, 0.0)!!, 0.001)
    }

    @Test
    fun followsTheRouteWhenStoppedOrTheCourseIsMissing() {
        assertEquals(90.0, VehicleHeading.choose(200.0, 0.2, 90.0)!!, 0.001)
        assertEquals(45.0, VehicleHeading.choose(null, 6.0, 45.0)!!, 0.001)
        assertEquals(180.0, VehicleHeading.choose(null, null, 180.0)!!, 0.001)
        assertNull(VehicleHeading.choose(null, 0.0, null))
        assertNull(VehicleHeading.choose(Double.NaN, 4.0, Double.NaN))
    }

    @Test
    fun readsTheForwardDirectionOfTheRouteSegment() {
        val east = east(200.0)
        val north = northOf(east, 150.0)
        val line = listOf(OsrmPoint(lon, lat), east, north)
        assertEquals(90.0, RouteHeading.forwardDegrees(line, 20.0)!!, 0.2)
        assertEquals(0.0, RouteHeading.forwardDegrees(line, 260.0)!!, 0.2)
        assertEquals(270.0, RouteHeading.forwardDegrees(listOf(east, OsrmPoint(lon, lat)), 10.0)!!, 0.2)
        assertNull(RouteHeading.forwardDegrees(listOf(OsrmPoint(lon, lat)), 0.0))
    }

    private fun east(meters: Double): OsrmPoint {
        val lat0 = Math.toRadians(lat)
        val delta = Math.toDegrees(meters / (GeoMeters.radiusMeters * cos(lat0)))
        return OsrmPoint(lon + delta, lat)
    }

    private fun northOf(origin: OsrmPoint, meters: Double): OsrmPoint {
        val delta = Math.toDegrees(meters / GeoMeters.radiusMeters)
        return OsrmPoint(origin.lon, origin.lat + delta)
    }
}
