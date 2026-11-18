package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RerouteSessionTest {
    @Test
    fun aConfirmedDepartureRequestsOneRouteAndAFailureKeepsTheWait() {
        val session = RerouteSession()
        assertFalse(session.allow(1_000L, confirmed = false, guiding = true))
        assertTrue(session.allow(1_000L, confirmed = true, guiding = true))
        session.started()
        assertFalse(session.allow(1_100L, confirmed = true, guiding = true))
        session.failed(2_000L)
        assertFalse(session.allow(6_000L, confirmed = true, guiding = true))
        assertTrue(session.allow(7_000L, confirmed = true, guiding = true))
    }

    @Test
    fun successWaitsBeforeAnotherRequestAndResetClearsIt() {
        val session = RerouteSession()
        session.started()
        session.succeeded(5_000L)
        assertFalse(session.allow(6_000L, confirmed = true, guiding = true))
        assertFalse(session.allow(10_000L, confirmed = true, guiding = true))
        assertTrue(session.allow(11_000L, confirmed = true, guiding = true))
        assertFalse(session.allow(6_000L, confirmed = true, guiding = false))
        session.reset()
        assertTrue(session.allow(6_000L, confirmed = true, guiding = true))
    }

    @Test
    fun aFailedRequestIsReportedAsCoolingAndASuccessIsNot() {
        val session = RerouteSession()
        session.started()
        session.failed(1_000L)
        assertTrue(session.cooling(5_000L))
        assertFalse(session.allow(5_000L, confirmed = true, guiding = true))
        session.reset()
        session.started()
        session.succeeded(1_000L)
        assertFalse(session.cooling(2_000L))
        assertFalse(session.allow(2_000L, confirmed = true, guiding = true))
    }

    @Test
    fun aNewerDepartureCancelsTheRequestAlreadyRunning() {
        val session = RerouteSession()
        val first = session.started(19.0, 47.0, 1_000L)
        assertTrue(session.inFlight)
        assertFalse(session.shouldReplace(2_000L, 19.0, 47.0))
        assertTrue(session.shouldReplace(4_000L, 19.0, 47.0))
        val second = session.replace(19.001, 47.0, 4_000L)
        assertTrue(second != first)
        assertTrue(session.stale(first))
        assertFalse(session.stale(second))
        assertTrue(session.inFlight)
    }

    @Test
    fun rerouteTimeoutsAreShorterThanTheFirstRouteAndTheRadiusFollowsAccuracy() {
        assertTrue(ReroutePolicy.connectTimeoutMs <= 6_000)
        assertTrue(ReroutePolicy.readTimeoutMs <= 8_000)
        assertTrue(ReroutePolicy.connectTimeoutMs < 12_000)
        assertEquals(40.0, ReroutePolicy.startRadius(1.0), 0.001)
        assertTrue(ReroutePolicy.startRadius(10.0) < ReroutePolicy.startRadius(40.0))
        assertEquals(250.0, ReroutePolicy.startRadius(500.0), 0.001)
    }
}
