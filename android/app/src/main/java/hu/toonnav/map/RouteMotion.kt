package hu.toonnav.map

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Where the cockpit road is between GPS fixes. The step follows the calculated
 * route at the last measured speed. It does not move the map marker or the guidance.
 */
data class RoutePose(
    val lon: Double,
    val lat: Double,
    val bearingDegrees: Double,
    val alongMeters: Double
)

object RouteMotion {
    const val stoppedMetersPerSecond = 0.8
    const val maxPredictMs = 2_500L
    const val lookAheadMeters = 18.0
    const val offRouteMeters = 80.0

    fun pose(
        route: List<OsrmPoint>,
        lon: Double,
        lat: Double,
        speedMetersPerSecond: Double?,
        hasSpeed: Boolean,
        ageMs: Long,
        fixLive: Boolean
    ): RoutePose? {
        if (!fixLive || route.size < 2 || !lon.isFinite() || !lat.isFinite() || ageMs < 0L) return null
        val line = route.filter { it.lon.isFinite() && it.lat.isFinite() }
        if (line.size < 2) return null
        val origin = line.first()
        val local = line.map { GeoMeters.local(origin, it) }
        val along = DoubleArray(local.size)
        for (index in 1 until local.size) {
            along[index] = along[index - 1] + hypot(
                local[index].first - local[index - 1].first,
                local[index].second - local[index - 1].second
            )
        }
        val total = along.last()
        if (total < 6.0) return null
        val here = GeoMeters.local(origin, OsrmPoint(lon, lat))
        val nearest = nearest(local, along, here.first, here.second) ?: return null
        if (nearest.cross > offRouteMeters) return null
        val travel = nearest.along + advanceMeters(speedMetersPerSecond, hasSpeed, ageMs)
        val cursor = travel.coerceIn(0.0, total)
        val place = at(local, along, cursor) ?: return null
        val ahead = at(local, along, (cursor + lookAheadMeters).coerceAtMost(total)) ?: place
        val behind = at(local, along, (cursor - lookAheadMeters).coerceAtLeast(0.0)) ?: place
        val east = ahead.first - behind.first
        val north = ahead.second - behind.second
        if (hypot(east, north) < 0.5) return null
        val geo = geographic(origin, place.first, place.second)
        return RoutePose(
            lon = geo.lon,
            lat = geo.lat,
            bearingDegrees = VehicleHeading.normalize(Math.toDegrees(atan2(east, north))),
            alongMeters = cursor
        )
    }

    fun advanceMeters(speedMetersPerSecond: Double?, hasSpeed: Boolean, ageMs: Long): Double {
        if (!hasSpeed || ageMs < 0L || ageMs > GpsSpeed.freshMs) return 0.0
        val speed = speedMetersPerSecond ?: return 0.0
        if (!speed.isFinite() || speed < stoppedMetersPerSecond) return 0.0
        return speed * (ageMs.coerceAtMost(maxPredictMs) / 1000.0)
    }

    private fun nearest(
        local: List<Pair<Double, Double>>,
        along: DoubleArray,
        east: Double,
        north: Double
    ): Closest? {
        var best: Closest? = null
        for (index in 0 until local.size - 1) {
            val start = local[index]
            val end = local[index + 1]
            val dx = end.first - start.first
            val dy = end.second - start.second
            val length = hypot(dx, dy)
            if (length < 0.05) continue
            val blend = (((east - start.first) * dx + (north - start.second) * dy) / (length * length)).coerceIn(0.0, 1.0)
            val px = start.first + dx * blend
            val py = start.second + dy * blend
            val cross = hypot(east - px, north - py)
            val traveled = along[index] + length * blend
            if (best == null || cross < best.cross) best = Closest(traveled, cross)
        }
        return best
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

    private data class Closest(val along: Double, val cross: Double)
}
