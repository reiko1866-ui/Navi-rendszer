package hu.toonnav.map

/**
 * Display-only heading for the map marker. Navigation and the follow camera
 * keep using the raw GPS course.
 */
object MarkerBearing {
    const val steadySpeedMetersPerSecond = 3.0
    const val slowStepDegrees = 18.0
    const val cruiseStepDegrees = 42.0

    fun advance(
        previous: Double?,
        chosen: Double?,
        speedMetersPerSecond: Double?,
        routeBearing: Double?
    ): Double? {
        val moving = speedMetersPerSecond != null &&
            speedMetersPerSecond.isFinite() &&
            speedMetersPerSecond >= VehicleHeading.movingMetersPerSecond
        val route = routeBearing?.takeIf { it.isFinite() }?.let(VehicleHeading::normalize)
        val aim = chosen?.takeIf { it.isFinite() }?.let(VehicleHeading::normalize)
        if (!moving) {
            if (route != null) return route
            return previous ?: aim
        }
        val target = aim ?: return previous
        val from = previous ?: return target
        val limit = if (speedMetersPerSecond < steadySpeedMetersPerSecond) slowStepDegrees else cruiseStepDegrees
        return approach(from, target, limit)
    }

    fun approach(from: Double, to: Double, maxStep: Double): Double {
        val step = shortestDelta(from, to).coerceIn(-maxStep, maxStep)
        return VehicleHeading.normalize(from + step)
    }

    fun shortestDelta(from: Double, to: Double): Double {
        return (to - from + 540.0) % 360.0 - 180.0
    }
}
