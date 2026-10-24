package hu.toonnav.map

data class FramePadding(
    val top: Double,
    val left: Double,
    val bottom: Double,
    val right: Double
) {
    fun focalY(viewHeightPx: Double): Double {
        return top + (viewHeightPx - top - bottom) / 2.0
    }
}

object DriveCamera {
    const val followPitch = 52.0
    const val followMinZoom = 16.35
    const val followMaxZoom = 16.55
    const val gpsStaleMs = 8_000L

    /**
     * The glTF nose points along +X. The location puck's default yaw turns that
     * axis onto the travel bearing, so the chase camera looks at the rear.
     */
    const val carModelYaw = 90f
    const val carModelScale = 9f

    fun followingPadding(viewHeightPx: Double, density: Float): FramePadding {
        val bottom = 112.0 * density
        val side = 28.0 * density
        val minTop = 96.0 * density
        val top = (0.40 * viewHeightPx + bottom).coerceAtLeast(minTop)
        return FramePadding(top, side, bottom, side)
    }

    fun overviewPadding(density: Float): FramePadding {
        val side = 36.0 * density
        return FramePadding(120.0 * density, side, 148.0 * density, side)
    }
}
