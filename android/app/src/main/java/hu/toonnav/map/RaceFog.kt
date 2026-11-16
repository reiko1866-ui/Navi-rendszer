package hu.toonnav.map

import kotlin.math.abs
import kotlin.math.exp

/**
 * Filament height fog for the driving view.
 *
 * [height] is the fog sea level in world meters. The road sits near y = 0, so the
 * floor has to be there too. A floor of 80 m multiplies the extinction by about
 * e^(0.02 * 80) and, past [distance], paints the carriageway and the ground with
 * the sky color. [cutOffDistance] is past the camera far plane, so the skybox
 * (about 1e19 m out) is not fogged over the road.
 */
object RaceFog {
    const val distance = 160f
    const val maximumOpacity = 0.38f
    const val density = 0.00065f
    const val height = 0f
    const val heightFalloff = 0.02f
    const val cutOffDistance = 8_000f

    /**
     * Opacity Filament's surface fog produces for a fragment [fragmentDistance] meters
     * from the camera. [viewY] is the fragment's world-up offset from the camera.
     */
    fun opacity(fragmentDistance: Float, viewY: Float, cameraY: Float): Float {
        if (fragmentDistance < distance || fragmentDistance > cutOffDistance) return 0f
        val falloff = heightFalloff
        val densityY = -falloff * (cameraY - height)
        val densityZ = density * exp(densityY)
        val fh = falloff * viewY
        val perMeter = if (abs(fh) > 0.00125f) {
            (densityZ - density * exp(densityY - fh)) / fh
        } else {
            densityZ
        }
        val optical = perMeter * (fragmentDistance - distance).coerceAtLeast(0f)
        val transmittance = exp(-optical)
        return (1f - transmittance).coerceIn(0f, maximumOpacity)
    }
}
