package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DriveDisplayTest {
    @Test
    fun wide720KeepsTheRoadAndLeavesTheCenterClear() {
        assertProfile(1280, 720, 1f)
    }

    @Test
    fun wide1080KeepsTheRoadAndLeavesTheCenterClear() {
        assertProfile(1920, 1080, 1.5f)
    }

    @Test
    fun wide2kKeepsTheRoadAndLeavesTheCenterClear() {
        assertProfile(2560, 1440, 2f)
    }

    @Test
    fun phonePortraitKeepsTheRoadAndLeavesTheCenterClear() {
        assertProfile(1080, 2400, 2.75f)
    }

    @Test
    fun phoneLandscapeKeepsTheRoadAndLeavesTheCenterClear() {
        assertProfile(2400, 1080, 2.75f)
    }

    @Test
    fun phoneButtonsKeepFestesAndGarazsOnOneLine() {
        val layout = DriveChrome.layout(1080, 2400, 2.75f)
        fun needed(label: String): Int {
            val text = (label.length * layout.buttonSp * 0.62f * 2.75f).toInt()
            val padding = (16f * 2.75f).toInt()
            return text + padding
        }
        assertTrue("festés ${layout.livery.width}", layout.livery.width >= needed("FESTÉS"))
        assertTrue("garázs ${layout.garage.width}", layout.garage.width >= needed("GARÁZS"))
        assertTrue(layout.livery.height <= layout.touchPx + 1)
        assertTrue(layout.roadWindow.overlaps(layout.livery).not())
        val nose = DriveBox(1080 / 2 - 70, 2400 - 160, 1080 / 2 + 70, 2400 - 24)
        for (box in layout.controls()) assertTrue("$box covers the car", !box.overlaps(nose))
    }

    @Test
    fun controlsStayAboveTheAndroidNavigationBar() {
        val density = 2.75f
        val inset = (48f * density).toInt()
        val width = 1080
        val height = 2400
        val layout = DriveChrome.layout(width, height, density, topInsetPx = inset, bottomInsetPx = inset)
        assertTrue(layout.guidance.top >= inset)
        val car = DriveBox(width / 2 - 80, height - inset - 120, width / 2 + 80, height - inset - 8)
        for (box in layout.controls()) {
            assertTrue("$box under the navigation bar", box.bottom <= height - inset + 1)
            assertTrue("$box covers the car", !box.overlaps(car))
            assertTrue(box.inside(width, height))
        }
        assertTrue(layout.roadWindow.overlaps(car))
    }

    @Test
    fun ultraWideCarDisplayStillShowsTheCarriageway() {
        assertProfile(1920, 720, 1f)
    }

    @Test
    fun weakHardwareDropsEffectsWithoutChangingTheRoadChoice() {
        assertEquals(DriveDisplay.Quality.REDUCED, DriveDisplay.quality(1280, 720, 128, 3))
        assertEquals(DriveDisplay.Quality.REDUCED, DriveDisplay.quality(1920, 1080, 256, 2))
        assertEquals(DriveDisplay.Quality.REDUCED, DriveDisplay.quality(2560, 1440, 192, 3))
        assertEquals(DriveDisplay.Quality.FULL, DriveDisplay.quality(1920, 1080, 256, 3))
        assertEquals(DriveDisplay.Quality.FULL, DriveDisplay.quality(2560, 1440, 512, 3))
    }

    @Test
    fun groundStaysDiffuseEvenIfATextureIsAddedLater() {
        val grass = RaceGeometry.materials().first { it.name == "grass" }
        val shoulder = RaceGeometry.materials().first { it.name == "shoulder" }
        assertEquals(0f, grass.metallic)
        assertEquals(0f, shoulder.metallic)
        assertTrue(grass.green > grass.red)
        assertEquals(0f, RoadShade.groundMetallic("grass"))
        assertEquals(0f, RoadShade.groundMetallic("shoulder"))
        assertEquals(0f, RoadShade.groundMetallic("asphalt"))
        assertNull(RoadShade.groundMetallic("marking"))
    }

    private fun assertProfile(width: Int, height: Int, density: Float) {
        val aspect = width.toDouble() / height.toDouble()
        val vertical = DriveDisplay.verticalFov(aspect)
        val horizontal = DriveDisplay.horizontalFov(aspect)
        assertTrue("horizontal $horizontal", horizontal >= 48.0)
        assertTrue(vertical in DriveDisplay.minVerticalDegrees..DriveDisplay.maxVerticalDegrees)
        val pose = RaceCamera.pose(0.0, 0.0, 0.0)
        for (ahead in floatArrayOf(12f, 30f, 80f)) {
            val center = RaceCamera.ndc(pose, 0f, 0.06f, ahead, vertical, aspect)
            assertTrue("center $ahead ${center.toList()}", abs(center[0]) < 1f && abs(center[1]) < 1f)
        }
        val edge = RaceCamera.ndc(pose, 3.6f, 0.06f, 18f, vertical, aspect)
        assertTrue("edge ${edge.toList()}", abs(edge[0]) < 1f && abs(edge[1]) < 1f)
        if (horizontal >= 68.0) {
            val grass = RaceCamera.ndc(pose, -14f, 0.0f, 28f, vertical, aspect)
            assertTrue("grass ${grass.toList()}", abs(grass[0]) < 1f && abs(grass[1]) < 1f)
        }
        val layout = DriveChrome.layout(width, height, density)
        val chrome = listOf(layout.guidance) + layout.controls()
        for (box in chrome) {
            assertTrue("$box outside $width x $height", box.inside(width, height))
            assertTrue("$box covers the road window", !box.overlaps(layout.roadWindow))
        }
        for (index in chrome.indices) {
            for (other in index + 1 until chrome.size) {
                assertTrue("${chrome[index]} overlaps ${chrome[other]}", !chrome[index].overlaps(chrome[other]))
            }
        }
        val clear = layout.roadWindow.width.toLong() * layout.roadWindow.height.toLong()
        val screen = width.toLong() * height.toLong()
        assertTrue("clear $clear of $screen", clear * 100 >= screen * 33)
        assertTrue(layout.touchPx + 1 >= (48f * density).toInt())
        assertTrue(layout.speedSp >= 28f)
        assertTrue(layout.maneuverSp >= 18f)
        assertTrue(layout.detailSp >= 16f)
        assertTrue(layout.controls().all { it.height + 1 >= (48f * density).toInt() })
    }
}
