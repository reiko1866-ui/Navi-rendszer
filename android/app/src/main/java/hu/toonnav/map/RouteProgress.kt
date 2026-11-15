package hu.toonnav.map

import kotlin.math.cos
import kotlin.math.hypot

data class RouteManeuver(
    val type: String,
    val modifier: String,
    val name: String,
    val exit: Int?,
    val alongMeters: Double
)

data class RouteUpdate(
    val crossTrackMeters: Double,
    val remainingMeters: Double,
    val distanceAlongMeters: Double,
    val totalMeters: Double,
    val maneuver: RouteManeuver,
    val maneuverDistanceMeters: Double,
    val roadName: String,
    val offRoute: Boolean
)

data class TrackedProgress(
    val update: RouteUpdate,
    val offRouteConfirmed: Boolean
)

internal object GeoMeters {
    const val radiusMeters = 6_371_000.0

    fun local(origin: OsrmPoint, point: OsrmPoint): Pair<Double, Double> {
        val lat0 = Math.toRadians(origin.lat)
        val x = Math.toRadians(point.lon - origin.lon) * cos(lat0) * radiusMeters
        val y = Math.toRadians(point.lat - origin.lat) * radiusMeters
        return x to y
    }
}

object RouteProgress {
    const val offRouteMeters = 80.0
    const val crossTrackToleranceMeters = 0.01

    fun project(plan: OsrmRoutePlan, lon: Double, lat: Double): RouteUpdate {
        val line = plan.coordinates.filter { it.lon.isFinite() && it.lat.isFinite() }
        val here = OsrmPoint(lon, lat)
        if (line.size < 2 || !lon.isFinite() || !lat.isFinite()) {
            val anchor = line.firstOrNull() ?: here
            val cross = if (line.isEmpty()) 0.0 else distance(anchor, here)
            return finish(plan, line, 0.0, cross, 0.0)
        }
        val origin = line.first()
        val local = line.map { GeoMeters.local(origin, it) }
        val point = GeoMeters.local(origin, here)
        var cursor = 0.0
        var bestAlong = 0.0
        var bestCross = Double.POSITIVE_INFINITY
        for (index in 1 until local.size) {
            val start = local[index - 1]
            val end = local[index]
            val dx = end.first - start.first
            val dy = end.second - start.second
            val length = hypot(dx, dy)
            if (length < 0.05) continue
            var t = ((point.first - start.first) * dx + (point.second - start.second) * dy) / (length * length)
            if (t < 0.0) t = 0.0
            if (t > 1.0) t = 1.0
            val px = start.first + dx * t
            val py = start.second + dy * t
            val cross = hypot(point.first - px, point.second - py)
            if (cross < bestCross) {
                bestCross = cross
                bestAlong = cursor + length * t
            }
            cursor += length
        }
        val total = if (cursor > 0.0) cursor else bestAlong
        if (bestCross == Double.POSITIVE_INFINITY) bestCross = distance(origin, here)
        if (bestAlong < 0.0) bestAlong = 0.0
        if (bestAlong > total) bestAlong = total
        return finish(plan, line, bestAlong, bestCross, total)
    }

    private fun finish(plan: OsrmRoutePlan, line: List<OsrmPoint>, along: Double, cross: Double, total: Double): RouteUpdate {
        val maneuvers = maneuverIndex(plan, line, total)
        val next = maneuvers.firstOrNull { !it.ignored && it.alongMeters + 1.0 >= along }?.toManeuver()
            ?: RouteManeuver("arrive", "", destinationName(plan), null, total)
        val road = maneuvers.lastOrNull { it.alongMeters <= along + 1.0 && it.name.isNotBlank() }?.name
            ?: destinationName(plan)
        val maneuverDistance = (next.alongMeters - along).coerceAtLeast(0.0)
        val remaining = (total - along).coerceAtLeast(0.0)
        return RouteUpdate(
            crossTrackMeters = cross,
            remainingMeters = remaining,
            distanceAlongMeters = along,
            totalMeters = total,
            maneuver = next,
            maneuverDistanceMeters = maneuverDistance,
            roadName = road,
            offRoute = cross > offRouteMeters + crossTrackToleranceMeters
        )
    }

    private fun maneuverIndex(plan: OsrmRoutePlan, line: List<OsrmPoint>, total: Double): List<IndexedManeuver> {
        if (line.size < 2) return emptyList()
        val origin = line.first()
        val local = line.map { GeoMeters.local(origin, it) }
        val found = ArrayList<IndexedManeuver>()
        var previous = 0.0
        for (step in plan.steps) {
            val along = projectAlong(local, GeoMeters.local(origin, step.maneuver.location)).coerceIn(0.0, total)
            val monotonic = along.coerceAtLeast(previous)
            previous = monotonic
            found.add(
                IndexedManeuver(
                    type = step.maneuver.type,
                    modifier = step.maneuver.modifier,
                    name = step.name,
                    exit = step.maneuver.exit,
                    alongMeters = monotonic,
                    ignored = ignorable(step)
                )
            )
        }
        if (found.none { it.type == "arrive" }) {
            found.add(IndexedManeuver("arrive", "", destinationName(plan), null, total, false))
        }
        return found
    }

    private fun projectAlong(local: List<Pair<Double, Double>>, point: Pair<Double, Double>): Double {
        var cursor = 0.0
        var bestAlong = 0.0
        var bestCross = Double.POSITIVE_INFINITY
        for (index in 1 until local.size) {
            val start = local[index - 1]
            val end = local[index]
            val dx = end.first - start.first
            val dy = end.second - start.second
            val length = hypot(dx, dy)
            if (length < 0.05) continue
            var t = ((point.first - start.first) * dx + (point.second - start.second) * dy) / (length * length)
            if (t < 0.0) t = 0.0
            if (t > 1.0) t = 1.0
            val px = start.first + dx * t
            val py = start.second + dy * t
            val cross = hypot(point.first - px, point.second - py)
            if (cross < bestCross) {
                bestCross = cross
                bestAlong = cursor + length * t
            }
            cursor += length
        }
        return bestAlong
    }

    private fun ignorable(step: OsrmStep): Boolean {
        val type = step.maneuver.type
        if (type == "depart" || type == "notification") return true
        val modifier = step.maneuver.modifier
        return (type == "continue" || type == "new name") && (modifier.isEmpty() || modifier == "straight")
    }

    private fun destinationName(plan: OsrmRoutePlan): String {
        val name = plan.waypoints.lastOrNull()?.name?.trim().orEmpty()
        return name.ifBlank { "Cél" }
    }

    private fun distance(from: OsrmPoint, to: OsrmPoint): Double {
        val local = GeoMeters.local(from, to)
        return hypot(local.first, local.second)
    }

    private data class IndexedManeuver(
        val type: String,
        val modifier: String,
        val name: String,
        val exit: Int?,
        val alongMeters: Double,
        val ignored: Boolean
    ) {
        fun toManeuver() = RouteManeuver(type, modifier, name, exit, alongMeters)
    }
}

class OffRouteHold {
    private var sinceMs = 0L
    private var samples = 0

    fun observe(offRoute: Boolean, timeMs: Long, accuracyMeters: Double?): Boolean {
        if (accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters < 0.0 || accuracyMeters > 40.0) {
            reset()
            return false
        }
        if (!offRoute) {
            reset()
            return false
        }
        if (samples == 0) {
            sinceMs = timeMs
            samples = 1
            return false
        }
        val elapsed = timeMs - sinceMs
        if (elapsed < 0L) {
            sinceMs = timeMs
            samples = 1
            return false
        }
        samples += 1
        return samples >= 2 && elapsed >= 8_000L
    }

    fun reset() {
        sinceMs = 0L
        samples = 0
    }
}

class RouteProgressTracker {
    private var plan: OsrmRoutePlan? = null
    private val hold = OffRouteHold()
    var last: RouteUpdate? = null
        private set

    fun setPlan(plan: OsrmRoutePlan) {
        this.plan = plan
        hold.reset()
        last = null
    }

    fun clear() {
        plan = null
        hold.reset()
        last = null
    }

    fun resetHold() {
        hold.reset()
    }

    fun update(lon: Double, lat: Double, timeMs: Long, accuracyMeters: Double?): TrackedProgress? {
        val route = plan ?: return null
        val update = RouteProgress.project(route, lon, lat)
        val confirmed = hold.observe(update.offRoute, timeMs, accuracyMeters)
        last = update
        return TrackedProgress(update, confirmed)
    }
}
