package hu.toonnav.map

import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCameraTest {
    @Test
    fun followingFrameKeepsTheCarLowWithoutZoomJumps() {
        val padding = DriveCamera.followingPadding(viewHeightPx = 2000.0, density = 2.5f)
        val focal = padding.focalY(2000.0) / 2000.0
        assertTrue(focal > 0.66 && focal < 0.74)
        assertTrue(DriveCamera.followMaxZoom - DriveCamera.followMinZoom < 0.3)
        assertTrue(DriveCamera.followPitch > 40.0)
    }

    @Test
    fun overviewPaddingLeavesRoomForTheBanners() {
        val padding = DriveCamera.overviewPadding(2f)
        assertTrue(padding.top > 100.0)
        assertTrue(padding.bottom > padding.top)
    }
}
