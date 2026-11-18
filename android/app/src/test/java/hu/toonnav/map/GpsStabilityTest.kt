package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * GPS smoothing, route lock, and reroute timing. These are JVM cases.
 * They do not prove the behavior on a phone.
 */
class GpsStabilityTest {
    private val lat = 47.0
    private val lon = 19.0

    @Test
    fun aStableFixStaysCloseToTheMeasuredCourse() {
        val filter = GpsTrackFilter()
        var estimate = filter.accept(sample(0.0, 0.0, 0L, 5.0, 15.0, 90.0))
        for (step in 1..4) {
            estimate = filter.accept(sample(step * 15.0, 0.0, step * 1_000L, 5.0, 15.0, 90.0))
        }
        val error = hypot(estimate.lon.let { eastOf(it) } - 60.0, northOf(estimate.lat))
        assertTrue("lag $error", error < 8.0)
        assertFalse(estimate.rejected)
        assertEquals(90.0, estimate.bearingDegrees!!, 25.0)
    }

    @Test
    fun aWeakFixDoesNotDragTheEstimate() {
        val filter = GpsTrackFilter()
        val start = filter.accept(sample(0.0, 0.0, 0L, 8.0, 0.0, 90.0))
        val weak = filter.accept(sample(0.0, 25.0, 1_000L, 70.0, 0.0, 0.0))
        val moved = hypot(eastOf(weak.lon) - eastOf(start.lon), northOf(weak.lat) - northOf(start.lat))
        assertTrue("weak move $moved", moved < 6.0)
        assertFalse(weak.rejected)
    }

    @Test
    fun oneSuddenJumpIsIgnoredAndASecondAgreeingJumpIsAccepted() {
        val filter = GpsTrackFilter()
        filter.accept(sample(0.0, 0.0, 0L, 5.0, 12.0, 90.0))
        val steady = filter.accept(sample(12.0, 0.0, 1_000L, 5.0, 12.0, 90.0))
        val spike = filter.accept(sample(12.0, 80.0, 2_000L, 5.0, 12.0, 0.0))
        assertTrue(spike.rejected)
        assertEquals(steady.lat, spike.lat, 1e-8)
        val again = filter.accept(sample(14.0, 78.0, 3_000L, 5.0, 12.0, 0.0))
        assertFalse(again.rejected)
        assertTrue(abs(northOf(again.lat) - 78.0) < 25.0)
    }

    @Test
    fun aStoppedVehicleKeepsItsHeadingAndDoesNotChaseJitter() {
        val filter = GpsTrackFilter()
        val parked = filter.accept(sample(0.0, 0.0, 0L, 4.0, 0.0, 10.0))
        var last = parked
        for (step in 1..6) {
            val side = if (step % 2 == 0) 3.0 else -3.0
            last = filter.accept(sample(side, side * 0.2, step * 1_000L, 4.0, 0.0, 160.0))
        }
        assertEquals(10.0, last.bearingDegrees!!, 0.001)
        val wander = hypot(eastOf(last.lon), northOf(last.lat))
        assertTrue("wander $wander", wander < 1.5)
        assertEquals(0.0, last.speedMetersPerSecond, 0.001)
    }

    @Test
    fun slowSpeedDoesNotTakeHeadingFromPositionNoise() {
        val filter = GpsTrackFilter()
        filter.accept(sample(0.0, 0.0, 0L, 5.0, 0.4, 20.0))
        val next = filter.accept(sample(0.4, 2.0, 1_000L, 5.0, 0.4, 180.0))
        assertEquals(20.0, next.bearingDegrees!!, 0.001)
    }

    @Test
    fun aRealTurnChangesHeadingWithoutSnappingThroughTheWholeAngle() {
        val filter = GpsTrackFilter()
        filter.accept(sample(0.0, 0.0, 0L, 5.0, 8.0, 0.0))
        val turned = filter.accept(sample(2.0, 8.0, 1_000L, 5.0, 8.0, 90.0))
        val bearing = turned.bearingDegrees!!
        assertTrue("bearing $bearing", bearing > 40.0)
        assertTrue("bearing $bearing", bearing < 80.0)
    }

    @Test
    fun rapidDirectionChangesAreRateLimited() {
        val filter = GpsTrackFilter()
        filter.accept(sample(0.0, 0.0, 0L, 5.0, 8.0, 0.0))
        val reversed = filter.accept(sample(0.0, 8.0, 1_000L, 5.0, 8.0, 180.0))
        val firstTurn = abs(MarkerBearing.shortestDelta(0.0, reversed.bearingDegrees!!))
        assertTrue("turn $firstTurn", firstTurn > 30.0 && firstTurn < 100.0)
        val back = filter.accept(sample(0.0, 16.0, 2_000L, 5.0, 8.0, 0.0))
        val returnTurn = abs(MarkerBearing.shortestDelta(reversed.bearingDegrees!!, back.bearingDegrees!!))
        assertTrue("return $returnTurn", returnTurn > 20.0)
    }

    @Test
    fun aShortGapKeepsMovingAndThenCorrects() {
        val filter = GpsTrackFilter()
        filter.accept(sample(0.0, 0.0, 0L, 6.0, 10.0, 90.0))
        val after = filter.accept(sample(30.0, 0.0, 3_000L, 6.0, 10.0, 90.0))
        assertFalse(after.rejected)
        val east = eastOf(after.lon)
        assertTrue("east $east", east > 20.0)
        assertTrue("east $east", east < 34.0)
    }

    @Test
    fun parallelRoadsDoNotStealAMatchingHeading() {
        val route = listOf(at(0.0, 0.0), at(0.0, 200.0), at(20.0, 200.0), at(20.0, 0.0))
        val hint = LockHint(alongMeters = 150.0, bearingDegrees = 0.0, speedMetersPerSecond = 12.0, accuracyMeters = 6.0)
        val snapped = RouteLock.snap(route, at(16.0, 150.0).lon, at(16.0, 150.0).lat, hint)!!
        assertTrue("along ${snapped.alongMeters}", snapped.alongMeters < 190.0)
        assertFalse(snapped.departed)
    }

    @Test
    fun aRealDepartureLeavesTheRouteAndABriefOffsetDoesNotConfirm() {
        val plan = plan(listOf(at(0.0, 0.0), at(0.0, 400.0)))
        val tracker = RouteProgressTracker()
        tracker.setPlan(plan)
        val side = at(70.0, 120.0)
        val first = tracker.update(side.lon, side.lat, 0L, 6.0, 8.0, 90.0)!!
        assertTrue(first.update.offRoute)
        assertFalse(first.offRouteConfirmed)
        val second = tracker.update(side.lon, side.lat, 1_200L, 6.0, 8.0, 90.0)!!
        assertTrue(second.offRouteConfirmed)
        val back = tracker.update(at(0.0, 140.0).lon, at(0.0, 140.0).lat, 2_000L, 6.0, 8.0, 0.0)!!
        assertFalse(back.offRouteConfirmed)
        assertTrue(back.update.distanceAlongMeters > 100.0)
    }

    @Test
    fun theCarDoesNotJumpWhileTheFixIsStableAndItHoldsStillWhenStopped() {
        val route = listOf(at(0.0, 0.0), at(400.0, 0.0))
        val follow = RoadFollow()
        var previous = 0.0
        var pose: RoutePose? = null
        for (frame in 0..40) {
            val now = frame * 16L
            val gps = if (frame < 20) 0L else 1_000L
            val east = if (frame < 20) 20.0 else 34.0
            pose = follow.advance(route, at(east, 0.4).lon, at(east, 0.4).lat, 14.0, true, 5.0, gps, now, 90.0)
            assertNotNull(pose)
            if (frame > 0) assertTrue("step ${pose!!.alongMeters - previous}", pose!!.alongMeters - previous < 0.6)
            previous = pose!!.alongMeters
        }
        assertTrue(pose!!.alongMeters > 20.0)
        val parked = RoadFollow()
        val still = parked.advance(route, at(40.0, 0.0).lon, at(40.0, 0.0).lat, 0.0, true, 5.0, 0L, 0L, 90.0)!!
        val jitter = parked.advance(route, at(44.0, 1.0).lon, at(44.0, 1.0).lat, 0.0, true, 5.0, 1_000L, 16L, 200.0)!!
        assertEquals(still.alongMeters, jitter.alongMeters, 0.2)
        assertEquals(still.bearingDegrees, jitter.bearingDegrees, 0.2)
    }

    @Test
    fun aCornerTurnsGraduallyAndAFarFixReleasesTheCar() {
        val route = listOf(at(0.0, 0.0), at(0.0, 80.0), at(80.0, 80.0))
        val follow = RoadFollow()
        follow.advance(route, at(0.0, 10.0).lon, at(0.0, 10.0).lat, 10.0, true, 5.0, 0L, 0L, 0.0)
        var last = 0.0
        for (frame in 1..30) {
            val along = 10.0 + frame * 0.16
            val east = if (along < 80.0) 0.0 else along - 80.0
            val north = if (along < 80.0) along else 80.0
            val pose = follow.advance(route, at(east, north).lon, at(east, north).lat, 10.0, true, 5.0, frame * 16L, frame * 16L, null)!!
            assertTrue("turn ${abs(pose.bearingDegrees - last)}", abs(MarkerBearing.shortestDelta(last, pose.bearingDegrees)) < 8.0)
            last = pose.bearingDegrees
        }
        val released = follow.advance(route, at(70.0, 10.0).lon, at(70.0, 10.0).lat, 8.0, true, 5.0, 5_000L, 5_000L, 90.0)!!
        val local = GeoMeters.local(route.first(), OsrmPoint(released.lon, released.lat))
        assertTrue(local.first > 40.0)
    }

    @Test
    fun aFailedRouteAndAReplacedRequestDoNotConfirmTogether() {
        val session = RerouteSession()
        val first = session.started(19.0, 47.0, 0L)
        session.failed(1_000L)
        assertFalse(session.allow(2_000L, confirmed = true, guiding = true))
        assertTrue(session.cooling(2_000L))
        assertFalse(session.stale(first))
        val next = session.started(19.0, 47.0, 6_000L)
        assertTrue(session.stale(first))
        assertFalse(session.stale(next))
        assertTrue(session.shouldReplace(6_000L, 19.01, 47.0))
    }

    private fun sample(
        east: Double,
        north: Double,
        timeMs: Long,
        accuracy: Double,
        speed: Double,
        bearing: Double
    ): GpsSample {
        val point = at(east, north)
        return GpsSample(point.lon, point.lat, timeMs, accuracy, speed, bearing)
    }

    private fun eastOf(longitude: Double): Double {
        return GeoMeters.local(OsrmPoint(lon, lat), OsrmPoint(longitude, lat)).first
    }

    private fun northOf(latitude: Double): Double {
        return GeoMeters.local(OsrmPoint(lon, lat), OsrmPoint(lon, latitude)).second
    }

    private fun at(eastMeters: Double, northMeters: Double): OsrmPoint {
        val lat0 = Math.toRadians(lat)
        val dLon = Math.toDegrees(eastMeters / (GeoMeters.radiusMeters * cos(lat0)))
        val dLat = Math.toDegrees(northMeters / GeoMeters.radiusMeters)
        return OsrmPoint(lon + dLon, lat + dLat)
    }

    private fun plan(points: List<OsrmPoint>): OsrmRoutePlan {
        return OsrmRoutePlan(0.0, null, points, emptyList(), emptyList())
    }
}
