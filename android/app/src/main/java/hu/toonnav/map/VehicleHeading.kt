package hu.toonnav.map

import kotlin.math.atan2
import kotlin.math.hypot

object VehicleHeading {
    const val movingMetersPerSecond = 1.0

    fun choose(gpsBearing: Double?, speedMetersPerSecond: Double?, routeBearing: Double?): Double? {
        val gps = gpsBearing?.takeIf { it.isFinite() }?.let(::normalize)
        val route = routeBearing?.takeIf { it.isFinite() }?.let(::normalize)
        val moving = speedMetersPerSecond != null &&
            speedMetersPerSecond.isFinite() &&
            speedMetersPerSecond >= movingMetersPerSecond
        if (moving && gps != null) return gps
        if (route != null) return route
        return gps
    }

    fun normalize(degrees: Double): Double {
        val turns = degrees % 360.0
        return if (turns < 0.0) turns + 360.0 else turns
    }
}

object RouteHeading {
    fun forwardDegrees(points: List<OsrmPoint>, distanceAlongMeters: Double): Double? {
        if (points.size < 2) return null
        val origin = points.first()
        val local = points.map { GeoMeters.local(origin, it) }
        var cursor = 0.0
        var chosen: Segment? = null
        var last: Segment? = null
        for (index in 1 until local.size) {
            val start = local[index - 1]
            val end = local[index]
            val length = hypot(end.first - start.first, end.second - start.second)
            if (length < 0.05) continue
            val segment = Segment(start.first, start.second, end.first, end.second, cursor, length)
            last = segment
            if (distanceAlongMeters <= cursor + length) {
                chosen = segment
                break
            }
            cursor += length
        }
        val segment = chosen ?: last ?: return null
        val east = segment.bx - segment.ax
        val north = segment.by - segment.ay
        return VehicleHeading.normalize(Math.toDegrees(atan2(east, north)))
    }

    private data class Segment(
        val ax: Double,
        val ay: Double,
        val bx: Double,
        val by: Double,
        val start: Double,
        val length: Double
    )
}
