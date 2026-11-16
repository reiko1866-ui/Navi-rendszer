package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class RouteMotionTest {
    private val lat = 47.0
    private val lon = 19.0

    @Test
    fun aStoppedOrStaleFixDoesNotSlideTheRoad() {
        val route = listOf(at(0.0, 0.0), at(200.0, 0.0))
        val parked = RouteMotion.pose(route, lon, lat, 0.2, true, 2_000L, true)!!
        val missing = RouteMotion.pose(route, lon, lat, null, false, 2_000L, true)!!
        val stale = RouteMotion.pose(route, lon, lat, 12.0, true, 5_000L, true)!!
        assertEquals(0.0, parked.alongMeters, 0.4)
        assertEquals(0.0, missing.alongMeters, 0.4)
        assertEquals(0.0, stale.alongMeters, 0.4)
        assertEquals(0.0, RouteMotion.advanceMeters(12.0, true, -1L), 0.0)
        assertNull(RouteMotion.pose(route, lon, lat, 12.0, true, 1_000L, false))
    }

    @Test
    fun speedAndElapsedTimeAdvanceAlongTheStraightRoute() {
        val route = listOf(at(0.0, 0.0), at(200.0, 0.0))
        val pose = RouteMotion.pose(route, lon, lat, 10.0, true, 1_000L, true)!!
        val (east, north) = GeoMeters.local(OsrmPoint(lon, lat), OsrmPoint(pose.lon, pose.lat))
        assertEquals(10.0, east, 0.8)
        assertEquals(0.0, north, 0.8)
        assertEquals(90.0, pose.bearingDegrees, 2.0)
        assertEquals(10.0, pose.alongMeters, 0.8)
    }

    @Test
    fun theCornerIsFollowedAndSeenBeforeTheCarReachesIt() {
        val route = listOf(at(0.0, 0.0), at(0.0, 80.0), at(80.0, 80.0))
        val early = RouteMotion.pose(route, lon, lat, 0.0, true, 0L, true)!!
        assertTrue(abs(early.bearingDegrees) < 4.0 || abs(early.bearingDegrees - 360.0) < 4.0)
        val origin = at(0.0, 70.0)
        val preview = RouteMotion.pose(route, origin.lon, origin.lat, 0.0, true, 0L, true)!!
        assertTrue(preview.bearingDegrees in 6.0..40.0)
        val through = RouteMotion.pose(route, lon, lat, 40.0, true, 2_500L, true)!!
        val (east, north) = GeoMeters.local(OsrmPoint(lon, lat), OsrmPoint(through.lon, through.lat))
        assertTrue(east > 10.0)
        assertTrue(north < 86.0)
        assertTrue(through.bearingDegrees > 70.0)
    }

    @Test
    fun aFixFarFromTheRouteDoesNotInventMotion() {
        val route = listOf(at(0.0, 0.0), at(40.0, 0.0))
        val away = at(0.0, 200.0)
        assertNull(RouteMotion.pose(route, away.lon, away.lat, 15.0, true, 1_000L, true))
    }

    private fun at(eastMeters: Double, northMeters: Double): OsrmPoint {
        val lat0 = Math.toRadians(lat)
        val dLon = Math.toDegrees(eastMeters / (GeoMeters.radiusMeters * cos(lat0)))
        val dLat = Math.toDegrees(northMeters / GeoMeters.radiusMeters)
        return OsrmPoint(lon + dLon, lat + dLat)
    }
}
