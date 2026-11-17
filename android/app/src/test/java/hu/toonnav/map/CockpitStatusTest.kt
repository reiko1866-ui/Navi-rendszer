package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitStatusTest {
    private val phrases = StatusPhrases(
        gpsSearch = "GPS jel keresése…",
        gpsLost = "A GPS jel nem érkezik. Várj a műholdra, vagy kapcsold be a helymeghatározást.",
        gpsOff = "A helymeghatározás ki van kapcsolva a telefonon.",
        gpsPoor = "A GPS pontatlan, ezért a célba érés és a letérés még nem számít.",
        permission = "A helyengedély hiányzik. Engedélyezd a pontos helyet a navigációhoz.",
        precise = "A pontos hely engedélye hiányzik. A navigációhoz engedélyezd a pontos helyet.",
        offRoute = "Letértél az útvonalról.",
        arrived = "Megérkeztél a célhoz.",
        planning = "Közúti útvonal kérése…",
        follow = "Indulás. A jel a térképen követ."
    )

    @Test
    fun readsTheRealStatusSentences() {
        assertEquals(
            CockpitState(GpsLamp.SEARCH, NavFlag.SEARCH, StatusPlate.CAUTION),
            CockpitStatus.classify(phrases.gpsSearch, true, false, true, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.BAD, NavFlag.ACTIVE, StatusPlate.ALERT),
            CockpitStatus.classify(phrases.gpsLost, true, true, false, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.BAD, NavFlag.SEARCH, StatusPlate.ALERT),
            CockpitStatus.classify(phrases.gpsOff, true, false, true, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.SEARCH, NavFlag.ACTIVE, StatusPlate.CAUTION),
            CockpitStatus.classify(phrases.gpsPoor, true, true, false, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.OK, NavFlag.OFF_ROUTE, StatusPlate.ALERT),
            CockpitStatus.classify(phrases.offRoute, true, true, false, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.OK, NavFlag.ARRIVED, StatusPlate.LIVE),
            CockpitStatus.classify(phrases.arrived, true, true, false, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.UNKNOWN, NavFlag.PLANNING, StatusPlate.CAUTION),
            CockpitStatus.classify(phrases.planning, true, false, true, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.OK, NavFlag.ACTIVE, StatusPlate.LIVE),
            CockpitStatus.classify(phrases.follow, true, true, false, phrases)
        )
    }

    @Test
    fun aLiveReadoutKeepsTheRouteActiveAndASearchStaysASearch() {
        val readout = "47.00000, 19.00000 · 36 km/h"
        assertEquals(NavFlag.ACTIVE, CockpitStatus.classify(readout, true, true, false, phrases).nav)
        assertEquals(GpsLamp.OK, CockpitStatus.classify(readout, true, true, false, phrases).lamp)
        assertEquals(NavFlag.SEARCH, CockpitStatus.classify("", false, false, true, phrases).nav)
        assertEquals(NavFlag.IDLE, CockpitStatus.classify("", false, false, false, phrases).nav)
        assertEquals(NavFlag.SEARCH, CockpitStatus.classify("Nincs találat a megadott címre.", true, false, true, phrases).nav)
    }

    @Test
    fun aCalculatedRouteIsNotReportedAsMissing() {
        val readout = "47.49700, 19.04000 · 24 km/h"
        val planned = CockpitStatus.classify(readout, true, false, false, phrases, planned = true)
        assertEquals(NavFlag.READY, planned.nav)
        assertEquals(GpsLamp.OK, planned.lamp)
        val idle = CockpitStatus.classify(readout, true, false, false, phrases, planned = false)
        assertEquals(NavFlag.IDLE, idle.nav)
        assertEquals(GpsLamp.OK, idle.lamp)
        val guiding = CockpitStatus.classify(readout, true, true, false, phrases, planned = true)
        assertEquals(NavFlag.ACTIVE, guiding.nav)
        val lost = CockpitStatus.classify(phrases.gpsLost, true, false, false, phrases, planned = true)
        assertEquals(GpsLamp.BAD, lost.lamp)
        assertEquals(NavFlag.READY, lost.nav)
        assertEquals(NavFlag.READY, CockpitStatus.classify("", false, false, false, phrases, planned = true).nav)
        assertEquals(NavFlag.IDLE, CockpitStatus.classify("", false, false, false, phrases, planned = false).nav)
        assertFalse(RouteCaption.previewVisible(0, false, false))
        assertFalse(RouteCaption.previewVisible(1, false, false))
        assertFalse(RouteCaption.previewVisible(4, true, false))
        assertFalse(RouteCaption.previewVisible(4, false, true))
        assertTrue(RouteCaption.previewVisible(4, false, false))
        assertEquals("24,7 km · 18 perc", RouteCaption.preview(24_700.0, 18.0 * 60.0))
    }

    @Test
    fun theRingFollowsTheRealSpeedAndDoesNotInventANumber() {
        assertEquals(0, CockpitGauge.level(null))
        assertEquals(0, CockpitGauge.level(-1))
        assertEquals(36 * 10_000 / 220, CockpitGauge.level(36))
        assertEquals(10_000, CockpitGauge.level(400))
        assertFalse(CockpitGauge.level(36) == CockpitGauge.level(37))
    }
}
