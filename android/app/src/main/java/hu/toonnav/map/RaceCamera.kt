package hu.toonnav.map

import kotlin.math.cos
import kotlin.math.sin

/**
 * Driving camera for the 3D prototype. The nose stays ahead of the lens and
 * below the horizon. Bearing is clockwise from north, matching RouteMotion.
 */
data class RaceCameraPose(
    val eyeX: Float,
    val eyeY: Float,
    val eyeZ: Float,
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
    val east: Float,
    val north: Float,
    val bearingDegrees: Double
)

object RaceCamera {
    const val eyeBackMeters = 0.55
    const val eyeHeightMeters = 0.95
    const val lookAheadMeters = 22.0

    fun pose(east: Double, north: Double, bearingDegrees: Double): RaceCameraPose {
        val yaw = Math.toRadians(bearingDegrees)
        val forwardX = sin(yaw)
        val forwardZ = cos(yaw)
        return RaceCameraPose(
            eyeX = (east - forwardX * eyeBackMeters).toFloat(),
            eyeY = eyeHeightMeters.toFloat(),
            eyeZ = (north - forwardZ * eyeBackMeters).toFloat(),
            targetX = (east + forwardX * lookAheadMeters).toFloat(),
            targetY = 0.55f,
            targetZ = (north + forwardZ * lookAheadMeters).toFloat(),
            east = east.toFloat(),
            north = north.toFloat(),
            bearingDegrees = bearingDegrees
        )
    }

    /** Column-major transform that puts the car on the route, nose along +Z locally. */
    fun carTransform(east: Double, north: Double, bearingDegrees: Double): FloatArray {
        val yaw = Math.toRadians(bearingDegrees)
        val cosine = cos(yaw).toFloat()
        val sine = sin(yaw).toFloat()
        return floatArrayOf(
            cosine, 0f, -sine, 0f,
            0f, 1f, 0f, 0f,
            sine, 0f, cosine, 0f,
            east.toFloat(), 0.02f, north.toFloat(), 1f
        )
    }

    /**
     * Vertical normalized position of a world point. Negative is below the
     * view center. This is the framing check used before a GPU is available.
     */
    fun verticalOffset(pose: RaceCameraPose, x: Float, y: Float, z: Float, fovDegrees: Double = 48.0): Float {
        val forwardX = pose.targetX - pose.eyeX
        val forwardY = pose.targetY - pose.eyeY
        val forwardZ = pose.targetZ - pose.eyeZ
        val forwardLength = kotlin.math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ).coerceAtLeast(0.001f)
        val fx = forwardX / forwardLength
        val fy = forwardY / forwardLength
        val fz = forwardZ / forwardLength
        val rx = fy * 0f - fz * 1f
        val ry = fz * 0f - fx * 0f
        val rz = fx * 1f - fy * 0f
        val rightLength = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(0.001f)
        val ux = (ry / rightLength) * fz - (rz / rightLength) * fy
        val uy = (rz / rightLength) * fx - (rx / rightLength) * fz
        val uz = (rx / rightLength) * fy - (ry / rightLength) * fx
        val vx = x - pose.eyeX
        val vy = y - pose.eyeY
        val vz = z - pose.eyeZ
        val depth = vx * fx + vy * fy + vz * fz
        if (depth <= 0.05f) return 0f
        val height = vx * ux + vy * uy + vz * uz
        val half = kotlin.math.tan(Math.toRadians(fovDegrees) * 0.5).toFloat()
        return (height / depth) / half
    }
}
