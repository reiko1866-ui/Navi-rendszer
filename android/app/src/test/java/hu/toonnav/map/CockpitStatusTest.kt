package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            CockpitState(GpsLamp.SEARCH, NavFlag.GPS_SEARCH, StatusPlate.CAUTION),
            CockpitStatus.classify(phrases.gpsSearch, true, false, true, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.BAD, NavFlag.GPS_BAD, StatusPlate.ALERT),
            CockpitStatus.classify(phrases.gpsLost, true, true, false, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.BAD, NavFlag.GPS_BAD, StatusPlate.ALERT),
            CockpitStatus.classify(phrases.gpsOff, true, false, true, phrases)
        )
        assertEquals(
            CockpitState(GpsLamp.SEARCH, NavFlag.GPS_BAD, StatusPlate.CAUTION),
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
    fun theRingFollowsTheRealSpeedAndDoesNotInventANumber() {
        assertEquals(0, CockpitGauge.level(null))
        assertEquals(0, CockpitGauge.level(-1))
        assertEquals(36 * 10_000 / 220, CockpitGauge.level(36))
        assertEquals(10_000, CockpitGauge.level(400))
        assertFalse(CockpitGauge.level(36) == CockpitGauge.level(37))
    }
}
