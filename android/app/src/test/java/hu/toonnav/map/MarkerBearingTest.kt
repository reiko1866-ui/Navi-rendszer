package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkerBearingTest {
    @Test
    fun holdsTheLastHeadingWhenStoppedWithoutARoute() {
        assertEquals(12.0, MarkerBearing.advance(12.0, 80.0, 0.2, null)!!, 0.001)
        assertEquals(12.0, MarkerBearing.advance(12.0, 200.0, 0.0, null)!!, 0.001)
        assertEquals(40.0, MarkerBearing.advance(null, 40.0, 0.0, null)!!, 0.001)
        assertNull(MarkerBearing.advance(null, null, 0.0, null))
    }

    @Test
    fun usesTheRouteDirectionWhileStopped() {
        assertEquals(90.0, MarkerBearing.advance(10.0, 200.0, 0.4, 90.0)!!, 0.001)
    }

    @Test
    fun turnsTowardTheCourseWithoutJumping() {
        assertEquals(18.0, MarkerBearing.advance(0.0, 90.0, 2.0, null)!!, 0.001)
        assertEquals(42.0, MarkerBearing.advance(0.0, 90.0, 8.0, null)!!, 0.001)
        assertEquals(20.0, MarkerBearing.advance(0.0, 20.0, 8.0, null)!!, 0.001)
        assertEquals(10.0, MarkerBearing.advance(350.0, 10.0, 8.0, null)!!, 0.001)
        assertEquals(308.0, MarkerBearing.advance(350.0, 200.0, 8.0, null)!!, 0.001)
    }

    @Test
    fun keepsThePreviousHeadingWhenTheCourseDisappears() {
        assertEquals(15.0, MarkerBearing.advance(15.0, null, 6.0, null)!!, 0.001)
    }
}
