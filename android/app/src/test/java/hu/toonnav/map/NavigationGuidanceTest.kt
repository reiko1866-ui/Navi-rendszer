package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class NavigationGuidanceTest {
    private val lat = 47.0
    private val lon = 19.0

    @Test
    fun movesTheManeuverAndTheRemainingDistanceWithEachFix() {
        val session = GuidanceSession()
        session.setPlan(route())
        val first = session.onFix(east(100.0).lon, lat, 1_000L, 0L, 8.0)!!
        val second = session.onFix(east(220.0).lon, lat, 2_000L, 0L, 8.0)!!
        assertEquals("Jobbra, Második", first.maneuverLabel)
        assertEquals(300.0, first.maneuverDistanceMeters, 0.3)
        assertEquals(900.0, first.remainingMeters, 0.3)
        assertTrue(second.maneuverDistanceMeters < first.maneuverDistanceMeters)
        assertTrue(second.remainingMeters < first.remainingMeters)
        assertFalse(first.progressHeld)
        assertEquals("Első", first.roadName)
        assertEquals(90f, first.arrowDegrees, 0.01f)
    }

    @Test
    fun namesTurnsAndRoundaboutExits() {
        assertEquals("Balra", ManeuverText.phrase("turn", "left", null, ""))
        assertEquals("Jobbra, Fő utca", ManeuverText.phrase("turn", "right", null, "Fő utca"))
        assertEquals("Körforgalom, 2. kijárat", ManeuverText.phrase("roundabout", "right", 2, ""))
        assertEquals("Körforgalom", ManeuverText.phrase("exit rotary", "", null, ""))
        assertEquals("Cél", ManeuverText.phrase("arrive", "", null, "Otthon"))
    }

    @Test
    fun signalsASustainedDepartureAndClearsItOnReturn() {
        val session = GuidanceSession()
        session.setPlan(route())
        val side = northOf(east(200.0), 100.0)
        val first = session.onFix(side.lon, side.lat, 0L, 0L, 10.0)!!
        val second = session.onFix(side.lon, side.lat, 8_000L, 0L, 10.0)!!
        assertFalse(first.offRouteConfirmed)
        assertTrue(second.offRouteConfirmed)
        val back = session.onFix(east(200.0).lon, lat, 9_000L, 0L, 10.0)!!
        assertFalse(back.offRouteConfirmed)
    }

    @Test
    fun doesNotConfirmDepartureFromAnInaccurateFixButStillAdvances() {
        val session = GuidanceSession()
        session.setPlan(route())
        val started = session.onFix(east(100.0).lon, lat, 0L, 0L, 8.0)!!
        val side = northOf(east(200.0), 100.0)
        val poor = session.onFix(side.lon, side.lat, 8_000L, 0L, 55.0)!!
        assertFalse(poor.offRouteConfirmed)
        assertEquals(GpsTrust.INACCURATE, poor.trust)
        assertFalse(poor.progressHeld)
        assertTrue(poor.remainingMeters < started.remainingMeters)
    }

    @Test
    fun holdsProgressWhenTheFixIsUnusableOrLost() {
        val session = GuidanceSession()
        session.setPlan(route())
        val good = session.onFix(east(150.0).lon, lat, 0L, 0L, 8.0)!!
        val unusable = session.onFix(east(400.0).lon, lat, 1_000L, 0L, 130.0)!!
        assertTrue(unusable.progressHeld)
        assertEquals(good.remainingMeters, unusable.remainingMeters, 0.001)
        assertEquals(good.maneuverDistanceMeters, unusable.maneuverDistanceMeters, 0.001)
        assertFalse(unusable.offRouteConfirmed)
        val lost = session.refreshAge(8_001L)!!
        assertEquals(GpsTrust.LOST, lost.trust)
        assertTrue(lost.progressHeld)
        assertEquals(good.remainingMeters, lost.remainingMeters, 0.001)
    }

    @Test
    fun ignoresANonFinitePosition() {
        val session = GuidanceSession()
        session.setPlan(route())
        val good = session.onFix(east(100.0).lon, lat, 0L, 0L, 8.0)!!
        val ignored = session.onFix(Double.NaN, lat, 1_000L, 0L, 8.0)!!
        assertEquals(good.remainingMeters, ignored.remainingMeters, 0.001)
    }

    @Test
    fun announcesArrivalOnlyCloseToTheDestinationOnAUsableFix() {
        val session = GuidanceSession()
        session.setPlan(route())
        val early = session.onFix(east(969.0).lon, lat, 0L, 0L, 8.0)!!
        assertFalse(early.arrived)
        val arrived = session.onFix(east(980.0).lon, lat, 1_000L, 0L, 8.0)!!
        assertTrue(arrived.arrived)
        assertEquals("Cél", arrived.maneuverLabel)
        assertEquals(0.0, arrived.remainingMeters, 0.001)
        val wandered = session.onFix(northOf(east(200.0), 100.0).lon, northOf(east(200.0), 100.0).lat, 2_000L, 0L, 8.0)!!
        assertTrue(wandered.arrived)
    }

    @Test
    fun doesNotArriveWhileOffTheRouteOrWhileTheSignalIsPoor() {
        val off = GuidanceSession()
        off.setPlan(route())
        val beside = northOf(east(980.0), 100.0)
        assertFalse(off.onFix(beside.lon, beside.lat, 0L, 0L, 8.0)!!.arrived)
        val poor = GuidanceSession()
        poor.setPlan(route())
        assertFalse(poor.onFix(east(990.0).lon, lat, 0L, 0L, 50.0)!!.arrived)
    }

    @Test
    fun clearsTheRoute() {
        val session = GuidanceSession()
        session.setPlan(route())
        session.onFix(east(100.0).lon, lat, 0L, 0L, 8.0)
        session.clear()
        assertNull(session.onFix(east(120.0).lon, lat, 1_000L, 0L, 8.0))
        assertNull(session.refreshAge(1_000L))
    }

    @Test
    fun classifiesGpsTrust() {
        assertEquals(GpsTrust.USABLE, GpsTrustGate.assess(0L, 40.0))
        assertEquals(GpsTrust.INACCURATE, GpsTrustGate.assess(8_000L, 40.01))
        assertEquals(GpsTrust.INACCURATE, GpsTrustGate.assess(0L, 120.0))
        assertEquals(GpsTrust.UNUSABLE, GpsTrustGate.assess(0L, 120.01))
        assertEquals(GpsTrust.UNUSABLE, GpsTrustGate.assess(0L, null))
        assertEquals(GpsTrust.LOST, GpsTrustGate.assess(8_001L, 5.0))
        assertEquals(GpsTrust.LOST, GpsTrustGate.assess(-1L, 5.0))
    }

    @Test
    fun cameraFollowsATrustedCourseAndFreezesALostSignal() {
        val follow = FollowCameraPlan.decide(true, false, GpsTrust.USABLE, 90.0)
        assertTrue(follow.move)
        assertEquals(90.0, follow.bearing, 0.001)
        assertEquals(DriveCamera.followPitch, follow.tilt, 0.001)
        val north = FollowCameraPlan.decide(true, true, GpsTrust.USABLE, 90.0)
        assertEquals(0.0, north.bearing, 0.001)
        assertEquals(0.0, north.tilt, 0.001)
        assertFalse(FollowCameraPlan.decide(true, false, GpsTrust.LOST, 90.0).move)
        assertFalse(FollowCameraPlan.decide(true, false, GpsTrust.UNUSABLE, 90.0).move)
        assertFalse(FollowCameraPlan.decide(false, false, GpsTrust.USABLE, 90.0).move)
        assertTrue(FollowCameraPlan.decide(true, false, GpsTrust.INACCURATE, null).move)
    }

    @Test
    fun scalesTheArrivalClockByTheRemainingFraction() {
        assertEquals(0.25, NavigationEta.fraction(250.0, 1000.0), 0.0001)
        assertEquals(90_000L, NavigationEta.offsetMillis(180.0, 0.5))
        assertNull(NavigationEta.offsetMillis(null, 0.5))
    }

    private fun route(): OsrmRoutePlan {
        val end = east(1000.0)
        return OsrmRoutePlan(
            1000.0,
            180.0,
            listOf(OsrmPoint(lon, lat), end),
            listOf(
                OsrmStep("", 0.0, null, OsrmManeuver("depart", "straight", OsrmPoint(lon, lat), null)),
                OsrmStep("Első", 100.0, null, OsrmManeuver("continue", "straight", east(100.0), null)),
                OsrmStep("Második", 300.0, null, OsrmManeuver("turn", "right", east(400.0), null)),
                OsrmStep("Otthon", 600.0, null, OsrmManeuver("arrive", "", end, null))
            ),
            listOf(OsrmWaypoint("Otthon", end, null))
        )
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
