package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class RouteProgressTest {
    private val lat = 47.0
    private val lon = 19.0

    @Test
    fun projectsOntoAStraightLine() {
        val update = RouteProgress.project(line(1000.0), east(250.0).lon, lat)
        assertEquals(250.0, update.distanceAlongMeters, 0.05)
        assertEquals(750.0, update.remainingMeters, 0.05)
        assertEquals(0.0, update.crossTrackMeters, 0.05)
        assertFalse(update.offRoute)
    }

    @Test
    fun scalesLengthWithTheGeometry() {
        val short = RouteProgress.project(line(1000.0), lon, lat)
        val long = RouteProgress.project(line(2000.0), lon, lat)
        assertEquals(2.0, long.totalMeters / short.totalMeters, 0.001)
        assertEquals(1000.0, short.remainingMeters, 0.05)
        assertEquals(2000.0, long.remainingMeters, 0.05)
    }

    @Test
    fun staysOnRouteAtTheEightyMeterThreshold() {
        val midway = east(500.0)
        val edge = northOf(midway, 80.0)
        val update = RouteProgress.project(line(1000.0), edge.lon, edge.lat)
        assertEquals(80.0, update.crossTrackMeters, 0.02)
        assertFalse(update.offRoute)
    }

    @Test
    fun leavesTheRouteBeyondTheThreshold() {
        val midway = east(500.0)
        val outside = northOf(midway, 80.02)
        val update = RouteProgress.project(line(1000.0), outside.lon, outside.lat)
        assertTrue(update.crossTrackMeters > 80.01)
        assertTrue(update.offRoute)
    }

    @Test
    fun prefersTheCloserBentSegment() {
        val corner = east(500.0)
        val end = northOf(corner, 500.0)
        val plan = plan(listOf(OsrmPoint(lon, lat), corner, end))
        val beside = eastOf(northOf(corner, 200.0), 0.0)
        val update = RouteProgress.project(plan, beside.lon, beside.lat)
        assertEquals(700.0, update.distanceAlongMeters, 0.2)
        assertEquals(0.0, update.crossTrackMeters, 0.2)
    }

    @Test
    fun clampsBeforeTheStartAndAfterTheEnd() {
        val before = east(-100.0)
        val ahead = RouteProgress.project(line(1000.0), before.lon, lat)
        assertEquals(0.0, ahead.distanceAlongMeters, 0.05)
        assertEquals(1000.0, ahead.remainingMeters, 0.05)
        assertEquals(100.0, ahead.crossTrackMeters, 0.05)
        val after = east(1100.0)
        val behind = RouteProgress.project(line(1000.0), after.lon, lat)
        assertEquals(1000.0, behind.distanceAlongMeters, 0.05)
        assertEquals(0.0, behind.remainingMeters, 0.05)
        assertEquals(100.0, behind.crossTrackMeters, 0.05)
    }

    @Test
    fun skipsDepartureAndStraightContinues() {
        val plan = maneuverPlan()
        val update = RouteProgress.project(plan, east(150.0).lon, lat)
        assertEquals("turn", update.maneuver.type)
        assertEquals("left", update.maneuver.modifier)
        assertEquals(250.0, update.maneuverDistanceMeters, 0.2)
        assertEquals("Első", update.roadName)
    }

    @Test
    fun advancesToArrivalAfterTheTurn() {
        val update = RouteProgress.project(maneuverPlan(), east(500.0).lon, lat)
        assertEquals("arrive", update.maneuver.type)
        assertEquals(500.0, update.maneuverDistanceMeters, 0.2)
        assertEquals("Második", update.roadName)
    }

    @Test
    fun keepsAWholeRoundaboutExit() {
        val plan = maneuverPlan(roundabout = true)
        val update = RouteProgress.project(plan, east(100.0).lon, lat)
        assertEquals("roundabout", update.maneuver.type)
        assertEquals(2, update.maneuver.exit)
        assertEquals(300.0, update.maneuverDistanceMeters, 0.2)
    }

    @Test
    fun confirmsAnAmbiguousDepartureOnlyAfterTwoSamplesAndTheLongerHold() {
        val hold = OffRouteHold()
        assertFalse(hold.observe(true, 1_000L, 10.0))
        assertFalse(hold.observe(true, 5_499L, 10.0))
        assertTrue(hold.observe(true, 5_500L, 10.0))
    }

    @Test
    fun doesNotConfirmASingleSampleEvenAfterTheHold() {
        val hold = OffRouteHold()
        assertFalse(hold.observe(true, 5_000L, 10.0))
    }

    @Test
    fun acceptsTheFortyMeterAccuracyBoundary() {
        val hold = OffRouteHold()
        assertFalse(hold.observe(true, 0L, 40.0))
        assertTrue(hold.observe(true, 8_000L, 40.0))
    }

    @Test
    fun aPoorSampleDoesNotEraseAClearDepartureTimer() {
        val hold = OffRouteHold()
        assertFalse(hold.observe(true, 0L, 10.0, 100.0))
        assertFalse(hold.observe(true, 400L, 90.0, 100.0))
        assertTrue(hold.observe(true, 1_000L, 10.0, 100.0))
    }

    @Test
    fun dropsTheHoldWhenTheFixReturnsOrTimeGoesBackwards() {
        val returned = OffRouteHold()
        assertFalse(returned.observe(true, 0L, 10.0))
        assertFalse(returned.observe(false, 8_000L, 10.0))
        assertFalse(returned.observe(true, 16_000L, 10.0))
        val reversed = OffRouteHold()
        assertFalse(reversed.observe(true, 10_000L, 10.0))
        assertFalse(reversed.observe(true, 9_000L, 10.0))
        assertTrue(reversed.observe(true, 17_000L, 10.0))
    }

    @Test
    fun aClearDepartureConfirmsSoonerThanASmallOne() {
        val clear = OffRouteHold()
        assertFalse(clear.observe(true, 0L, 10.0, 200.0))
        assertTrue(clear.observe(true, 1_000L, 10.0, 200.0))
        val small = OffRouteHold()
        assertFalse(small.observe(true, 0L, 8.0, 30.0))
        assertFalse(small.observe(true, 1_000L, 8.0, 30.0))
        assertTrue(small.observe(true, 2_800L, 8.0, 30.0))
    }

    @Test
    fun rejectsMissingAccuracy() {
        val hold = OffRouteHold()
        assertFalse(hold.observe(true, 0L, null))
        assertFalse(hold.observe(true, 8_000L, null))
    }

    @Test
    fun trackerConfirmsOnlyASustainedDeparture() {
        val tracker = RouteProgressTracker()
        tracker.setPlan(line(1000.0))
        val side = northOf(east(500.0), 100.0)
        val first = tracker.update(side.lon, side.lat, 0L, 12.0)
        val second = tracker.update(side.lon, side.lat, 8_000L, 12.0)
        assertTrue(first!!.update.offRoute)
        assertFalse(first.offRouteConfirmed)
        assertTrue(second!!.offRouteConfirmed)
        tracker.clear()
        assertNull(tracker.update(side.lon, side.lat, 20_000L, 12.0))
    }

    private fun line(meters: Double): OsrmRoutePlan = plan(listOf(OsrmPoint(lon, lat), east(meters)))

    private fun maneuverPlan(roundabout: Boolean = false): OsrmRoutePlan {
        val turn = east(400.0)
        val end = east(1000.0)
        val turnType = if (roundabout) "roundabout" else "turn"
        val exit = if (roundabout) 2 else null
        return plan(
            listOf(OsrmPoint(lon, lat), end),
            listOf(
                step("", 0.0, "depart", "straight", OsrmPoint(lon, lat), null),
                step("Első", 100.0, "continue", "straight", east(100.0), null),
                step("Második", 300.0, turnType, "left", turn, exit),
                step("Cél", 600.0, "arrive", "", end, null)
            ),
            listOf(OsrmWaypoint("Cél", end, null))
        )
    }

    private fun plan(
        points: List<OsrmPoint>,
        steps: List<OsrmStep> = emptyList(),
        waypoints: List<OsrmWaypoint> = emptyList()
    ): OsrmRoutePlan {
        return OsrmRoutePlan(0.0, null, points, steps, waypoints)
    }

    private fun step(name: String, distance: Double, type: String, modifier: String, at: OsrmPoint, exit: Int?): OsrmStep {
        return OsrmStep(name, distance, null, OsrmManeuver(type, modifier, at, exit))
    }

    private fun east(meters: Double): OsrmPoint = eastOf(OsrmPoint(lon, lat), meters)

    private fun eastOf(origin: OsrmPoint, meters: Double): OsrmPoint {
        val lat0 = Math.toRadians(origin.lat)
        val delta = Math.toDegrees(meters / (GeoMeters.radiusMeters * cos(lat0)))
        return OsrmPoint(origin.lon + delta, origin.lat)
    }

    private fun northOf(origin: OsrmPoint, meters: Double): OsrmPoint {
        val delta = Math.toDegrees(meters / GeoMeters.radiusMeters)
        return OsrmPoint(origin.lon, origin.lat + delta)
    }
}
