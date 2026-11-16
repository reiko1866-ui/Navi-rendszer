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
    const val eyeLocalX = 0.0
    const val eyeLocalY = 1.08
    const val eyeLocalZ = 0.0
    const val lookLocalY = 0.30
    const val lookAheadMeters = 22.0
    const val fovDegrees = 48.0
    /** Near plane stays in front of the nose. Far plane clears the streamed corridor. */
    const val nearMeters = 0.08
    const val farMeters = 2_800.0
    const val noseLocalY = 0.40
    const val noseLocalZ = 2.60

    fun pose(east: Double, north: Double, bearingDegrees: Double, surface: Double = RaceGeometry.deckTop): RaceCameraPose {
        val eye = world(east, north, bearingDegrees, eyeLocalX, eyeLocalY, eyeLocalZ, surface)
        val look = world(east, north, bearingDegrees, 0.0, lookLocalY, eyeLocalZ + lookAheadMeters, surface)
        return RaceCameraPose(
            eyeX = eye[0], eyeY = eye[1], eyeZ = eye[2],
            targetX = look[0], targetY = look[1], targetZ = look[2],
            east = east.toFloat(),
            north = north.toFloat(),
            bearingDegrees = bearingDegrees
        )
    }

    fun nosePoint(east: Double, north: Double, bearingDegrees: Double, surface: Double = RaceGeometry.deckTop): FloatArray {
        return world(east, north, bearingDegrees, 0.0, noseLocalY, noseLocalZ, surface)
    }

    fun wingPoint(east: Double, north: Double, bearingDegrees: Double, side: Double, surface: Double = RaceGeometry.deckTop): FloatArray {
        return world(east, north, bearingDegrees, side * 0.98, 0.26, 2.45, surface)
    }

    fun tirePoint(east: Double, north: Double, bearingDegrees: Double, side: Double, surface: Double = RaceGeometry.deckTop): FloatArray {
        return world(east, north, bearingDegrees, side * 0.98, 0.36, 1.80, surface)
    }

    /**
     * Camera-to-world matrix. Filament looks down the camera's local -Z, so the
     * third column points backward. The first column points to the driver's right,
     * which is east when the car faces north.
     */
    fun cameraModel(east: Double, north: Double, bearingDegrees: Double, surface: Double = RaceGeometry.deckTop): FloatArray {
        val pose = pose(east, north, bearingDegrees, surface)
        return lookingModel(pose.eyeX, pose.eyeY, pose.eyeZ, pose.targetX, pose.targetY, pose.targetZ)
    }

    fun lookingModel(eyeX: Float, eyeY: Float, eyeZ: Float, targetX: Float, targetY: Float, targetZ: Float): FloatArray {
        val pose = RaceCameraPose(eyeX, eyeY, eyeZ, targetX, targetY, targetZ, eyeX, eyeZ, 0.0)
        val basis = basis(pose)
        return floatArrayOf(
            basis.rightX, basis.rightY, basis.rightZ, 0f,
            basis.upX, basis.upY, basis.upZ, 0f,
            -basis.forwardX, -basis.forwardY, -basis.forwardZ, 0f,
            eyeX, eyeY, eyeZ, 1f
        )
    }

    fun ndc(
        pose: RaceCameraPose,
        x: Float,
        y: Float,
        z: Float,
        fov: Double = fovDegrees,
        aspect: Double = 16.0 / 9.0
    ): FloatArray {
        val basis = basis(pose)
        val vx = x - pose.eyeX
        val vy = y - pose.eyeY
        val vz = z - pose.eyeZ
        val depth = vx * basis.forwardX + vy * basis.forwardY + vz * basis.forwardZ
        if (depth <= 0.05f) return floatArrayOf(2f, 0f)
        val half = kotlin.math.tan(Math.toRadians(fov) * 0.5)
        val screenX = (vx * basis.rightX + vy * basis.rightY + vz * basis.rightZ) / (depth * half * aspect)
        val screenY = (vx * basis.upX + vy * basis.upY + vz * basis.upZ) / (depth * half)
        return floatArrayOf(screenX.toFloat(), screenY.toFloat())
    }

    /** Column-major transform. The asset's nose points along local +Z. */
    fun carTransform(east: Double, north: Double, bearingDegrees: Double, surface: Double = RaceGeometry.deckTop): FloatArray {
        val yaw = Math.toRadians(bearingDegrees)
        val cosine = cos(yaw).toFloat()
        val sine = sin(yaw).toFloat()
        return floatArrayOf(
            cosine, 0f, -sine, 0f,
            0f, 1f, 0f, 0f,
            sine, 0f, cosine, 0f,
            east.toFloat(), surface.toFloat(), north.toFloat(), 1f
        )
    }

    fun world(
        east: Double,
        north: Double,
        bearingDegrees: Double,
        localX: Double,
        localY: Double,
        localZ: Double,
        surface: Double = RaceGeometry.deckTop
    ): FloatArray {
        val yaw = Math.toRadians(bearingDegrees)
        val cosine = cos(yaw)
        val sine = sin(yaw)
        return floatArrayOf(
            (east + cosine * localX + sine * localZ).toFloat(),
            (localY + surface).toFloat(),
            (north - sine * localX + cosine * localZ).toFloat()
        )
    }

    /**
     * Vertical normalized position of a world point. Negative is below the
     * view center. This is the framing check used before a GPU is available.
     */
    fun verticalOffset(pose: RaceCameraPose, x: Float, y: Float, z: Float, fov: Double = fovDegrees): Float {
        return ndc(pose, x, y, z, fov, 1.0)[1]
    }

    private data class Basis(
        val forwardX: Float,
        val forwardY: Float,
        val forwardZ: Float,
        val rightX: Float,
        val rightY: Float,
        val rightZ: Float,
        val upX: Float,
        val upY: Float,
        val upZ: Float
    )

    private fun basis(pose: RaceCameraPose): Basis {
        var fx = pose.targetX - pose.eyeX
        var fy = pose.targetY - pose.eyeY
        var fz = pose.targetZ - pose.eyeZ
        val length = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(0.001f)
        fx /= length
        fy /= length
        fz /= length
        // right = world up × forward. Facing north, that right vector is east.
        var rx = fz
        var ry = 0f
        var rz = -fx
        val rightLength = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(0.001f)
        rx /= rightLength
        ry /= rightLength
        rz /= rightLength
        val ux = fy * rz - fz * ry
        val uy = fz * rx - fx * rz
        val uz = fx * ry - fy * rx
        return Basis(fx, fy, fz, rx, ry, rz, ux, uy, uz)
    }
}
