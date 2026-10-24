package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverEventsTest {
    @Test
    fun pointsTheBannerArrowWithTheModifier() {
        assertEquals(-90f, ManeuverEvents.bannerRotation("left"))
        assertEquals(135f, ManeuverEvents.bannerRotation("sharp right"))
        assertEquals(0f, ManeuverEvents.bannerRotation("straight"))
        assertEquals(180f, ManeuverEvents.bannerRotation("uturn"))
    }

    @Test
    fun mapsTurnModifiers() {
        assertEquals("balra", ManeuverEvents.eventId("turn", "left"))
        assertEquals("korforgalom_kijarat", ManeuverEvents.eventId("exit roundabout", "right"))
        assertEquals("cel", ManeuverEvents.eventId("arrive", null))
    }

    @Test
    fun playsOnlySavedRecognizedClips() {
        assertTrue(ManeuverEvents.canPlay("mentve", "balra", "mukodik"))
        assertFalse(ManeuverEvents.canPlay("ellenorzes", "balra", "mukodik"))
        assertFalse(ManeuverEvents.canPlay("mentve", "dugo", "hianyzik"))
        assertFalse(ManeuverEvents.canPlay("mentve", "folytatas", "mukodik"))
    }

    @Test
    fun speaksManeuverInsideTheWindowOnce() {
        assertTrue(ManeuverEvents.shouldSpeak("turn", 80.0, "", "balra@80"))
        assertFalse(ManeuverEvents.shouldSpeak("turn", 900.0, "", "balra@900"))
        assertFalse(ManeuverEvents.shouldSpeak("turn", 80.0, "balra@80", "balra@80"))
        assertTrue(ManeuverEvents.shouldSpeak("indulas", null, "", "indulas"))
    }

    @Test
    fun formatsMetricDistance() {
        assertEquals("80 m", ManeuverEvents.formatDistance(80.2))
        assertEquals("1,2 km", ManeuverEvents.formatDistance(1200.0))
    }
}
