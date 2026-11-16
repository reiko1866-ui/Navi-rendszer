package hu.toonnav.map

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class ScreenPoint(val x: Float, val y: Float)

/**
 * The road ahead in the cockpit, projected from the route that was already calculated.
 * An empty route stays empty. This is not a camera image and not a separate circuit.
 */
data class RoadStrip(
    val left: List<ScreenPoint>,
    val right: List<ScreenPoint>,
    val center: List<ScreenPoint>,
    val kerbSide: List<Int> = emptyList()
) {
    val ready: Boolean get() = center.size >= 2

    companion object {
        val EMPTY = RoadStrip(emptyList(), emptyList(), emptyList())
    }
}

object RoadRibbon {
    const val aheadMeters = 280.0
    const val halfWidthMeters = 3.8
    const val sampleMeters = 3.0
    private const val eyeMeters = 18.0
    private const val awayLimitMeters = 80.0
    private const val kerbTurn = 0.07

    fun project(lon: Double, lat: Double, bearingDegrees: Double, route: List<OsrmPoint>): RoadStrip {
        if (!lon.isFinite() || !lat.isFinite() || !bearingDegrees.isFinite() || route.size < 2) {
            return RoadStrip.EMPTY
        }
        val origin = OsrmPoint(lon, lat)
        val radians = Math.toRadians(bearingDegrees)
        val sinBearing = sin(radians)
        val cosBearing = cos(radians)
        val local = ArrayList<Vec>(route.size)
        for (point in route) {
            if (!point.lon.isFinite() || !point.lat.isFinite()) continue
            val (east, north) = GeoMeters.local(origin, point)
            val forward = east * sinBearing + north * cosBearing
            val right = east * cosBearing - north * sinBearing
            local.add(Vec(forward, right))
        }
        if (local.size < 2) return RoadStrip.EMPTY
        val ribbon = trace(local) ?: return RoadStrip.EMPTY
        return RoadStrip(
            left = ribbon.map { place(it.left.forward, it.left.right) },
            right = ribbon.map { place(it.right.forward, it.right.right) },
            center = ribbon.map { place(it.center.forward, it.center.right) },
            kerbSide = ribbon.map { it.side }
        )
    }

    private fun trace(points: List<Vec>): List<RibbonSample>? {
        val along = DoubleArray(points.size)
        for (index in 1 until points.size) {
            along[index] = along[index - 1] + distance(points[index - 1], points[index])
        }
        val total = along.last()
        if (total < 6.0) return null
        val nearest = nearest(points, along) ?: return null
        if (nearest.distance > awayLimitMeters) return null
        val end = (nearest.along + aheadMeters).coerceAtMost(total)
        if (end - nearest.along < 6.0) return null
        val samples = ArrayList<RibbonSample>()
        var cursor = nearest.along
        var previousForward = 1.0
        var previousRight = 0.0
        var hasPrevious = false
        while (cursor <= end + 0.01) {
            val center = at(points, along, cursor) ?: break
            val ahead = at(points, along, (cursor + sampleMeters).coerceAtMost(total)) ?: center
            val behind = at(points, along, (cursor - sampleMeters).coerceAtLeast(0.0)) ?: center
            val tangentForward = ahead.forward - behind.forward
            val tangentRight = ahead.right - behind.right
            val length = hypot(tangentForward, tangentRight).coerceAtLeast(0.001)
            val rightForward = -tangentRight / length
            val rightSide = tangentForward / length
            val side = kerbSide(previousForward, previousRight, tangentForward, tangentRight, hasPrevious)
            samples.add(
                RibbonSample(
                    center = center,
                    left = Vec(
                        center.forward - rightForward * halfWidthMeters,
                        center.right - rightSide * halfWidthMeters
                    ),
                    right = Vec(
                        center.forward + rightForward * halfWidthMeters,
                        center.right + rightSide * halfWidthMeters
                    ),
                    side = side
                )
            )
            previousForward = tangentForward
            previousRight = tangentRight
            hasPrevious = true
            cursor += sampleMeters
        }
        return if (samples.size < 2) null else samples
    }

    private fun nearest(points: List<Vec>, along: DoubleArray): Closest? {
        var best: Closest? = null
        for (index in 0 until points.size - 1) {
            val start = points[index]
            val end = points[index + 1]
            val spanForward = end.forward - start.forward
            val spanRight = end.right - start.right
            val length = hypot(spanForward, spanRight)
            if (length < 0.05) continue
            val blend = ((-start.forward * spanForward - start.right * spanRight) / (length * length)).coerceIn(0.0, 1.0)
            val forward = start.forward + spanForward * blend
            val right = start.right + spanRight * blend
            val distance = hypot(forward, right)
            val traveled = along[index] + length * blend
            if (best == null || distance < best.distance) best = Closest(traveled, distance)
        }
        return best
    }

    private fun at(points: List<Vec>, along: DoubleArray, distance: Double): Vec? {
        if (points.isEmpty()) return null
        if (distance <= 0.0) return points.first()
        val total = along.last()
        if (distance >= total) return points.last()
        for (index in 0 until points.size - 1) {
            val startAt = along[index]
            val endAt = along[index + 1]
            if (distance < startAt || distance > endAt) continue
            val span = endAt - startAt
            if (span < 0.05) continue
            val blend = ((distance - startAt) / span).coerceIn(0.0, 1.0)
            val start = points[index]
            val end = points[index + 1]
            return Vec(
                start.forward + (end.forward - start.forward) * blend,
                start.right + (end.right - start.right) * blend
            )
        }
        return points.last()
    }

    private fun place(forward: Double, lateral: Double): ScreenPoint {
        val depth = forward.coerceAtLeast(1.2)
        val perspective = (eyeMeters / (depth + eyeMeters)).toFloat()
        val x = 0.5f + (lateral / 8.0).toFloat() * perspective
        return ScreenPoint(x, perspective)
    }

    /**
     * Positive means the route turns right. The kerb is decorative and only
     * follows a bend that is already in the route geometry.
     */
    private fun kerbSide(
        previousForward: Double,
        previousRight: Double,
        tangentForward: Double,
        tangentRight: Double,
        hasPrevious: Boolean
    ): Int {
        if (!hasPrevious) return 0
        val previous = hypot(previousForward, previousRight).coerceAtLeast(0.001)
        val current = hypot(tangentForward, tangentRight).coerceAtLeast(0.001)
        val turn = (previousForward * tangentRight - previousRight * tangentForward) / (previous * current)
        return when {
            abs(turn) < kerbTurn -> 0
            turn > 0.0 -> 1
            else -> -1
        }
    }

    private fun distance(from: Vec, to: Vec): Double {
        return hypot(to.forward - from.forward, to.right - from.right)
    }

    private data class Vec(val forward: Double, val right: Double)

    private data class Closest(val along: Double, val distance: Double)

    private data class RibbonSample(val center: Vec, val left: Vec, val right: Vec, val side: Int)
}

/**
 * Display smoothing for the cockpit windshield only. The map marker and the
 * follow camera keep the heading they already use.
 */
object CockpitHeading {
    const val quietDegrees = 1.4
    const val followShare = 0.4
    const val maxStepDegrees = 11.0

    fun smooth(previous: Double?, measured: Double?): Double? {
        val target = measured?.takeIf { it.isFinite() }?.let(VehicleHeading::normalize) ?: return previous
        val from = previous?.takeIf { it.isFinite() }?.let(VehicleHeading::normalize) ?: return target
        val delta = MarkerBearing.shortestDelta(from, target)
        val step = when {
            kotlin.math.abs(delta) < quietDegrees -> 0.0
            kotlin.math.abs(delta) <= maxStepDegrees -> delta * followShare
            else -> delta.coerceIn(-maxStepDegrees, maxStepDegrees)
        }
        return VehicleHeading.normalize(from + step)
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
