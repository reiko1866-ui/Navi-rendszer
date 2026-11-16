package hu.toonnav.map

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationOwnerTest {
    @After
    fun reset() {
        NavigationOwner.resetForTest()
    }

    @Test
    fun navigationUsesTheLiveRouteAndNeverTheSample() {
        assertEquals(RaceRouteSource.LIVE, NavigationRoute.choose(navigation = true, livePoints = 40, hasPlan = true))
        assertEquals(RaceRouteSource.MISSING, NavigationRoute.choose(navigation = true, livePoints = 1, hasPlan = true))
        assertEquals(RaceRouteSource.MISSING, NavigationRoute.choose(navigation = true, livePoints = 8, hasPlan = false))
        assertEquals(RaceRouteSource.SAMPLE, NavigationRoute.choose(navigation = false, livePoints = 0, hasPlan = false))
        assertEquals(RaceRouteSource.LIVE, NavigationRoute.choose(navigation = false, livePoints = 6, hasPlan = true))
    }

    @Test
    fun theMapDoesNotListenGuideOrSpeakWhileTheRaceScreenOwnsTheTrip() {
        assertTrue(NavigationOwner.mapMayListen())
        assertTrue(NavigationOwner.mapMayGuide(tripActive = false))
        assertFalse(NavigationOwner.mapMayGuide(tripActive = true))
        assertTrue(NavigationVoice.mapMaySpeak(tripActive = false))
        assertFalse(NavigationVoice.mapMaySpeak(tripActive = true))
        assertEquals(NavigationOwner.Seat.RACE, NavigationOwner.speaker(tripActive = true))

        NavigationOwner.claimRace()
        assertFalse(NavigationOwner.mapMayListen())
        assertFalse(NavigationOwner.mapMayGuide(tripActive = false))
        assertFalse(NavigationVoice.mapMaySpeak(tripActive = false))
        assertEquals(NavigationOwner.Seat.RACE, NavigationOwner.speaker(tripActive = false))

        NavigationOwner.releaseRace(returningToMap = true)
        assertTrue(NavigationOwner.mapMayListen())
        assertTrue(NavigationOwner.consumeMapOverview())
        assertFalse(NavigationOwner.consumeMapOverview())
        assertFalse(NavigationOwner.mapMayGuide(tripActive = true))
        assertFalse(NavigationVoice.mapMaySpeak(tripActive = true))
    }

    @Test
    fun departureIsAnnouncedOnceAndOnlyForALiveNavigationLaunch() {
        assertTrue(NavigationVoice.announceStart(navigation = true, requested = true, live = true))
        assertFalse(NavigationVoice.announceStart(navigation = true, requested = false, live = true))
        assertFalse(NavigationVoice.announceStart(navigation = false, requested = true, live = true))
        assertFalse(NavigationVoice.announceStart(navigation = true, requested = true, live = false))
    }

    @Test
    fun garageDoesNotOpenASecondDrivingScreen() {
        assertEquals(
            NavigationRoute.GarageAction.OPEN_SAMPLE,
            NavigationRoute.garageAction(aliveRaces = 0, livePoints = 0, hasPlan = false)
        )
        assertEquals(
            NavigationRoute.GarageAction.OPEN_LIVE,
            NavigationRoute.garageAction(aliveRaces = 0, livePoints = 12, hasPlan = true)
        )
        NavigationOwner.raceCreated()
        assertEquals(
            NavigationRoute.GarageAction.RETURN,
            NavigationRoute.garageAction(NavigationOwner.aliveRaces, livePoints = 12, hasPlan = true)
        )
        NavigationOwner.raceDestroyed()
        assertEquals(0, NavigationOwner.aliveRaces)
        assertEquals(NavigationOwner.Seat.MAP, NavigationOwner.seat)
    }
}
