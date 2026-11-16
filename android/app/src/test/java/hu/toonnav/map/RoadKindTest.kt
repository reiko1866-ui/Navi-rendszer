package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadKindTest {
    @Test
    fun classifiesOnlyExplicitRoadData() {
        assertEquals(RoadKind.MOTORWAY, RoadClassifier.classify("continue", listOf("motorway")))
        assertEquals(RoadKind.EXPRESSWAY, RoadClassifier.classify("new name", emptyList(), "trunk"))
        assertEquals(RoadKind.MAIN, RoadClassifier.classify("turn", listOf("primary")))
        assertEquals(RoadKind.SECONDARY, RoadClassifier.classify("turn", listOf("secondary")))
        assertEquals(RoadKind.RESIDENTIAL, RoadClassifier.classify("turn", listOf("residential")))
        assertEquals(RoadKind.ROUNDABOUT, RoadClassifier.classify("roundabout", listOf("motorway")))
        assertEquals(RoadKind.RAMP, RoadClassifier.classify("off ramp", listOf("motorway")))
        assertEquals(RoadKind.RAMP, RoadClassifier.classify("continue", listOf("motorway_link")))
        assertEquals(RoadKind.UNKNOWN, RoadClassifier.classify("continue", emptyList()))
        assertEquals("", RoadClassifier.label(RoadKind.UNKNOWN))
        assertEquals("Autópálya", RoadClassifier.label(RoadKind.MOTORWAY))
    }

    @Test
    fun hidesThePlannedClassAfterADepartureOrAHeldFix() {
        assertEquals(RoadKind.UNKNOWN, RoadGuidance.shown(RoadKind.MOTORWAY, offRoute = true, held = false))
        assertEquals(RoadKind.UNKNOWN, RoadGuidance.shown(RoadKind.MAIN, offRoute = false, held = true))
        assertEquals(RoadKind.RESIDENTIAL, RoadGuidance.shown(RoadKind.RESIDENTIAL, offRoute = false, held = false))
    }

    @Test
    fun motorwayAndRampWarningsStartEarlierThanUrbanOnes() {
        assertEquals(1000.0, RoadGuidance.leadMeters(RoadKind.MOTORWAY, "continue"), 0.1)
        assertEquals(1000.0, RoadGuidance.leadMeters(RoadKind.UNKNOWN, "off ramp"), 0.1)
        assertEquals(250.0, RoadGuidance.leadMeters(RoadKind.RESIDENTIAL, "turn"), 0.1)
        assertEquals(250.0, RoadGuidance.leadMeters(RoadKind.ROUNDABOUT, "roundabout"), 0.1)
        assertTrue(ManeuverEvents.shouldSpeak("turn", 800.0, "", "lehajto", 1000.0))
        assertTrue(!ManeuverEvents.shouldSpeak("turn", 800.0, "", "varos", 400.0))
    }
}
