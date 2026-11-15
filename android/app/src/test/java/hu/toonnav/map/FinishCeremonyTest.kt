package hu.toonnav.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishCeremonyTest {
    @Test
    fun thePodiumWaitsForARealArrivalAndASafeStop() {
        assertFalse(FinishCeremony.ready(arrived = false, speedMetersPerSecond = 0.0, hasSpeed = true, fixLive = true))
        assertFalse(FinishCeremony.ready(arrived = true, speedMetersPerSecond = 8.0, hasSpeed = true, fixLive = true))
        assertFalse(FinishCeremony.ready(arrived = true, speedMetersPerSecond = null, hasSpeed = false, fixLive = true))
        assertFalse(FinishCeremony.ready(arrived = true, speedMetersPerSecond = 0.0, hasSpeed = true, fixLive = false))
        assertTrue(FinishCeremony.ready(arrived = true, speedMetersPerSecond = 0.2, hasSpeed = true, fixLive = true))
    }
}
