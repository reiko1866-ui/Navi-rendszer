package hu.toonnav.map

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

data class ScreenPoint(val x: Float, val y: Float)

/**
 * The road ahead in the cockpit, projected from the route that was already calculated.
 * An empty route stays empty. This is not a camera image and not a separate circuit.
 */
data class RoadStrip(
    val left: List<ScreenPoint>,
    val right: List<ScreenPoint>,
    val center: List<ScreenPoint>
) {
    val ready: Boolean get() = center.size >= 2

    companion object {
        val EMPTY = RoadStrip(emptyList(), emptyList(), emptyList())
    }
}

object RoadRibbon {
    const val aheadMeters = 170.0
    const val halfWidthMeters = 3.4
    private const val eyeMeters = 8.0

    fun project(lon: Double, lat: Double, bearingDegrees: Double, route: List<OsrmPoint>): RoadStrip {
        if (!lon.isFinite() || !lat.isFinite() || !bearingDegrees.isFinite() || route.size < 2) {
            return RoadStrip.EMPTY
        }
        val origin = OsrmPoint(lon, lat)
        val radians = Math.toRadians(bearingDegrees)
        val sinBearing = sin(radians)
        val cosBearing = cos(radians)
        val frame = ArrayList<Pair<Double, Double>>(route.size)
        for (point in route) {
            if (!point.lon.isFinite() || !point.lat.isFinite()) continue
            val (east, north) = GeoMeters.local(origin, point)
            val forward = east * sinBearing + north * cosBearing
            val right = east * cosBearing - north * sinBearing
            frame.add(forward to right)
        }
        val samples = sampleAhead(frame)
        if (samples.size < 2) return RoadStrip.EMPTY
        return RoadStrip(
            left = samples.map { place(it.first, it.second - halfWidthMeters) },
            right = samples.map { place(it.first, it.second + halfWidthMeters) },
            center = samples.map { place(it.first, it.second) }
        )
    }

    private fun sampleAhead(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.size < 2) return emptyList()
        val farthest = points.maxOf { it.first }
        if (farthest < 6.0) return emptyList()
        val samples = ArrayList<Pair<Double, Double>>()
        var cursor = 4.0
        while (cursor <= aheadMeters && cursor <= farthest) {
            val hit = atForward(points, cursor) ?: break
            samples.add(hit)
            cursor += 8.0
        }
        return samples
    }

    private fun atForward(points: List<Pair<Double, Double>>, target: Double): Pair<Double, Double>? {
        for (index in 0 until points.size - 1) {
            val start = points[index]
            val end = points[index + 1]
            val span = end.first - start.first
            if (span <= 0.5) continue
            if (target < start.first || target > end.first) continue
            val blend = ((target - start.first) / span).coerceIn(0.0, 1.0)
            return target to (start.second + (end.second - start.second) * blend)
        }
        return null
    }

    private fun place(forward: Double, lateral: Double): ScreenPoint {
        val depth = forward.coerceAtLeast(1.5)
        val perspective = (eyeMeters / (depth + eyeMeters)).toFloat()
        val x = 0.5f + (lateral / 7.5).toFloat() * perspective
        return ScreenPoint(x, perspective)
    }
}

object CockpitInstruction {
    fun line(label: String, arrowDegrees: Float): String {
        val text = label.trim()
        if (text.startsWith("Cél")) return "CÉL"
        if (text.startsWith("Körforgalom")) return text.uppercase(Locale("hu", "HU"))
        return when {
            arrowDegrees >= 170f || arrowDegrees <= -170f -> "FORDULJ VISSZA"
            arrowDegrees >= 20f -> "FORDULJ JOBBRA"
            arrowDegrees <= -20f -> "FORDULJ BALRA"
            else -> "EGYENESEN"
        }
    }
}
