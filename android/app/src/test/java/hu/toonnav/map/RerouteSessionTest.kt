package hu.toonnav.map

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
        assertFalse(session.allow(10_000L, confirmed = true, guiding = true))
        assertTrue(session.allow(22_000L, confirmed = true, guiding = true))
    }

    @Test
    fun successWaitsBeforeAnotherRequestAndResetClearsIt() {
        val session = RerouteSession()
        session.started()
        session.succeeded(5_000L)
        assertFalse(session.allow(6_000L, confirmed = true, guiding = true))
        assertFalse(session.allow(6_000L, confirmed = true, guiding = false))
        session.reset()
        assertTrue(session.allow(6_000L, confirmed = true, guiding = true))
    }
}
