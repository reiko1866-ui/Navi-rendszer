package hu.toonnav.map

import kotlin.math.cos
import kotlin.math.hypot

data class RouteManeuver(
    val type: String,
    val modifier: String,
    val name: String,
    val exit: Int?,
    val alongMeters: Double,
    val classes: List<String> = emptyList()
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

    fun describe(
        plan: OsrmRoutePlan,
        line: List<OsrmPoint>,
        along: Double,
        cross: Double,
        total: Double,
        offRoute: Boolean
    ): RouteUpdate {
        return finish(plan, line, along, cross, total).copy(offRoute = offRoute)
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
                    ignored = ignorable(step),
                    classes = step.classes
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
        val ignored: Boolean,
        val classes: List<String> = emptyList()
    ) {
        fun toManeuver() = RouteManeuver(type, modifier, name, exit, alongMeters, classes)
    }
}

class OffRouteHold {
    private var sinceMs = 0L
    private var samples = 0

    companion object {
        const val ignoreAccuracyMeters = 65.0
        const val hugeCrossMeters = 140.0
        const val headingLeaveDegrees = 38.0
        const val headingSpeedMetersPerSecond = 2.0
        const val headingMinCrossMeters = 12.0
        const val clearCrossMeters = 55.0
        const val sustainedCrossMeters = 24.0
        const val headingHoldMs = 1_200L
        const val clearHoldMs = 1_000L
        const val sustainedHoldMs = 2_800L
        const val ambiguousHoldMs = 4_500L
    }

    fun observe(
        offRoute: Boolean,
        timeMs: Long,
        accuracyMeters: Double?,
        crossTrackMeters: Double = Double.NaN,
        speedMetersPerSecond: Double? = null,
        headingErrorDegrees: Double? = null
    ): Boolean {
        val accuracy = accuracyMeters
        val poor = accuracy == null || !accuracy.isFinite() || accuracy < 0.0 || accuracy > ignoreAccuracyMeters
        val huge = crossTrackMeters.isFinite() && crossTrackMeters >= hugeCrossMeters
        if (poor && !huge) return false
        if (accuracy != null && accuracy > 45.0 && crossTrackMeters.isFinite() && crossTrackMeters < accuracy * 1.25 && !huge) {
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
        val speed = speedMetersPerSecond ?: 0.0
        val heading = headingErrorDegrees ?: 0.0
        val holdMs = when {
            heading >= headingLeaveDegrees &&
                speed >= headingSpeedMetersPerSecond &&
                crossTrackMeters.isFinite() &&
                crossTrackMeters >= headingMinCrossMeters -> headingHoldMs
            crossTrackMeters.isFinite() && crossTrackMeters >= clearCrossMeters -> clearHoldMs
            crossTrackMeters.isFinite() && crossTrackMeters >= sustainedCrossMeters -> sustainedHoldMs
            else -> ambiguousHoldMs
        }
        return samples >= 2 && elapsed >= holdMs
    }

    fun reset() {
        sinceMs = 0L
        samples = 0
    }
}

class RouteProgressTracker {
    private var plan: OsrmRoutePlan? = null
    private val hold = OffRouteHold()
    private var hint = LockHint()
    var last: RouteUpdate? = null
        private set

    fun setPlan(plan: OsrmRoutePlan) {
        this.plan = plan
        hold.reset()
        hint = LockHint()
        last = null
    }

    fun clear() {
        plan = null
        hold.reset()
        hint = LockHint()
        last = null
    }

    fun resetHold() {
        hold.reset()
    }

    fun update(
        lon: Double,
        lat: Double,
        timeMs: Long,
        accuracyMeters: Double?,
        speedMetersPerSecond: Double? = null,
        bearingDegrees: Double? = null
    ): TrackedProgress? {
        val route = plan ?: return null
        val locked = RouteLock.project(
            route,
            lon,
            lat,
            hint.copy(
                speedMetersPerSecond = speedMetersPerSecond,
                bearingDegrees = bearingDegrees,
                accuracyMeters = accuracyMeters
            )
        ) ?: return null
        val update = locked.update
        val confirmed = hold.observe(
            update.offRoute,
            timeMs,
            accuracyMeters,
            update.crossTrackMeters,
            speedMetersPerSecond,
            locked.headingErrorDegrees
        )
        hint = LockHint(
            alongMeters = update.distanceAlongMeters,
            bearingDegrees = bearingDegrees ?: locked.segmentBearingDegrees,
            speedMetersPerSecond = speedMetersPerSecond,
            accuracyMeters = accuracyMeters,
            departed = update.offRoute
        )
        last = update
        return TrackedProgress(update, confirmed)
    }
}

data class LockHint(
    val alongMeters: Double? = null,
    val bearingDegrees: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val accuracyMeters: Double? = null,
    val departed: Boolean = false
)

data class RouteSnap(
    val alongMeters: Double,
    val crossMeters: Double,
    val segmentBearingDegrees: Double,
    val departed: Boolean,
    val headingErrorDegrees: Double
)

data class LockResult(
    val update: RouteUpdate,
    val headingErrorDegrees: Double,
    val segmentBearingDegrees: Double
)

/**
 * Keeps the match on the current leg when a later or parallel piece of the
 * same route is only a little closer. Off-route uses a band, not one distance.
 */
object RouteLock {
    fun stickMeters(accuracyMeters: Double): Double {
        val accuracy = accuracyMeters.coerceIn(0.0, 80.0)
        return (8.0 + accuracy * 0.55).coerceIn(10.0, 36.0)
    }

    fun leaveMeters(accuracyMeters: Double): Double {
        val accuracy = accuracyMeters.coerceIn(0.0, 80.0)
        val stick = stickMeters(accuracy)
        return (stick + 14.0 + accuracy * 0.45).coerceIn(stick + 12.0, 78.0)
    }

    fun sideBandMeters(accuracyMeters: Double): Double {
        val accuracy = accuracyMeters.coerceIn(0.0, 80.0)
        return stickMeters(accuracy) + kotlin.math.max(6.0, accuracy * 0.35)
    }

    fun snap(route: List<OsrmPoint>, lon: Double, lat: Double, hint: LockHint = LockHint()): RouteSnap? {
        val line = route.filter { it.lon.isFinite() && it.lat.isFinite() }
        if (line.size < 2 || !lon.isFinite() || !lat.isFinite()) return null
        val origin = line.first()
        val local = line.map { GeoMeters.local(origin, it) }
        val point = GeoMeters.local(origin, OsrmPoint(lon, lat))
        var cursor = 0.0
        var best: Candidate? = null
        var near: Candidate? = null
        val accuracy = hint.accuracyMeters?.takeIf { it.isFinite() && it >= 0.0 } ?: 25.0
        val speed = hint.speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val window = kotlin.math.max(45.0, speed * 5.0 + accuracy + 25.0)
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
            val along = cursor + length * t
            val bearing = VehicleHeading.normalize(Math.toDegrees(kotlin.math.atan2(dx, dy)))
            val candidate = Candidate(along, cross, bearing, score(along, cross, bearing, hint, speed))
            if (best == null || candidate.score < best.score) best = candidate
            val previous = hint.alongMeters
            if (previous != null && along in (previous - 30.0)..(previous + window)) {
                if (near == null || candidate.score < near.score) near = candidate
            }
            cursor += length
        }
        val chosen = choose(best, near, accuracy) ?: return null
        val headingError = headingError(hint.bearingDegrees, chosen.bearing, speed)
        return RouteSnap(
            alongMeters = chosen.along,
            crossMeters = chosen.cross,
            segmentBearingDegrees = chosen.bearing,
            departed = departed(chosen.cross, headingError, speed, accuracy, hint.departed),
            headingErrorDegrees = headingError
        )
    }

    fun project(plan: OsrmRoutePlan, lon: Double, lat: Double, hint: LockHint = LockHint()): LockResult? {
        val line = plan.coordinates.filter { it.lon.isFinite() && it.lat.isFinite() }
        val snapped = snap(line, lon, lat, hint) ?: return null
        val origin = line.first()
        val local = line.map { GeoMeters.local(origin, it) }
        var total = 0.0
        for (index in 1 until local.size) {
            val length = hypot(local[index].first - local[index - 1].first, local[index].second - local[index - 1].second)
            if (length >= 0.05) total += length
        }
        val described = RouteProgress.describe(plan, line, snapped.alongMeters, snapped.crossMeters, total, snapped.departed)
        return LockResult(described, snapped.headingErrorDegrees, snapped.segmentBearingDegrees)
    }

    fun pointAt(route: List<OsrmPoint>, distance: Double): OsrmPoint? {
        val line = route.filter { it.lon.isFinite() && it.lat.isFinite() }
        if (line.isEmpty()) return null
        val origin = line.first()
        val local = line.map { GeoMeters.local(origin, it) }
        val along = DoubleArray(local.size)
        for (index in 1 until local.size) {
            along[index] = along[index - 1] + hypot(
                local[index].first - local[index - 1].first,
                local[index].second - local[index - 1].second
            )
        }
        val place = at(local, along, distance.coerceIn(0.0, along.last())) ?: return null
        return geographic(origin, place.first, place.second)
    }

    private fun choose(best: Candidate?, near: Candidate?, accuracy: Double): Candidate? {
        if (best == null) return null
        if (near == null) return best
        val stick = stickMeters(accuracy)
        val jumped = kotlin.math.abs(best.along - near.along) > 40.0
        if (jumped && near.cross <= stick && best.cross + 8.0 >= near.cross) return near
        return best
    }

    private fun score(along: Double, cross: Double, bearing: Double, hint: LockHint, speed: Double): Double {
        var value = cross
        val previous = hint.alongMeters
        if (previous != null) {
            val back = (previous - along - 12.0).coerceAtLeast(0.0)
            val leap = (along - previous - speed * 2.0 - 40.0).coerceAtLeast(0.0)
            value += back * 3.0 + leap * 0.7
        }
        val travel = hint.bearingDegrees?.takeIf { it.isFinite() }
        if (travel != null && speed >= 1.5) {
            value += kotlin.math.abs(MarkerBearing.shortestDelta(travel, bearing)) * 0.4
        }
        return value
    }

    private fun headingError(travel: Double?, segment: Double, speed: Double): Double {
        val course = travel?.takeIf { it.isFinite() } ?: return 0.0
        if (speed < 1.5) return 0.0
        return kotlin.math.abs(MarkerBearing.shortestDelta(course, segment))
    }

    private fun departed(cross: Double, headingError: Double, speed: Double, accuracy: Double, wasDeparted: Boolean): Boolean {
        val stick = stickMeters(accuracy)
        val leave = leaveMeters(accuracy)
        val band = sideBandMeters(accuracy)
        if (wasDeparted) return cross > stick
        if (cross > leave) return true
        if (speed > 2.5 && cross > band) return true
        return speed > 2.0 && headingError >= OffRouteHold.headingLeaveDegrees && cross > stick
    }

    private fun at(local: List<Pair<Double, Double>>, along: DoubleArray, distance: Double): Pair<Double, Double>? {
        if (local.isEmpty()) return null
        if (distance <= 0.0) return local.first()
        val total = along.last()
        if (distance >= total) return local.last()
        for (index in 0 until local.size - 1) {
            val startAt = along[index]
            val endAt = along[index + 1]
            val span = endAt - startAt
            if (span < 0.05 || distance < startAt || distance > endAt) continue
            val blend = ((distance - startAt) / span).coerceIn(0.0, 1.0)
            val start = local[index]
            val end = local[index + 1]
            return (start.first + (end.first - start.first) * blend) to (start.second + (end.second - start.second) * blend)
        }
        return local.last()
    }

    private fun geographic(origin: OsrmPoint, east: Double, north: Double): OsrmPoint {
        val lat0 = Math.toRadians(origin.lat)
        val lat = origin.lat + Math.toDegrees(north / GeoMeters.radiusMeters)
        val lon = origin.lon + Math.toDegrees(east / (GeoMeters.radiusMeters * cos(lat0)))
        return OsrmPoint(lon, lat)
    }

    private data class Candidate(val along: Double, val cross: Double, val bearing: Double, val score: Double)
}
