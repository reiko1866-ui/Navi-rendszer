package hu.toonnav.map

/**
 * Continuous place on the route between GPS fixes. The car integrates speed,
 * then eases toward the new fix. It does not snap back when the next sample
 * arrives, and it leaves the route when the filtered fix is actually off it.
 */
class RoadFollow {
    private var along = Double.NaN
    private var bearing = Double.NaN
    private var targetAlong = Double.NaN
    private var gpsTime = Long.MIN_VALUE
    private var released = false
    private var frameTime = -1L

    fun reset() {
        along = Double.NaN
        bearing = Double.NaN
        targetAlong = Double.NaN
        gpsTime = Long.MIN_VALUE
        released = false
        frameTime = -1L
    }

    fun advance(
        route: List<OsrmPoint>,
        lon: Double,
        lat: Double,
        speedMetersPerSecond: Double,
        hasSpeed: Boolean,
        accuracyMeters: Double?,
        gpsTimeMs: Long,
        nowMs: Long,
        travelBearing: Double? = null
    ): RoutePose? {
        if (route.size < 2 || !lon.isFinite() || !lat.isFinite()) return null
        val dt = if (frameTime < 0L) 0.0 else ((nowMs - frameTime) / 1000.0).coerceIn(0.0, 0.05)
        frameTime = nowMs
        val accuracy = accuracyMeters?.takeIf { it.isFinite() && it >= 0.0 } ?: 25.0
        val speed = if (hasSpeed && speedMetersPerSecond.isFinite() && speedMetersPerSecond >= GpsTrackFilter.stoppedMetersPerSecond) {
            speedMetersPerSecond
        } else {
            0.0
        }
        if (gpsTimeMs != gpsTime) {
            gpsTime = gpsTimeMs
            val wasReleased = released
            val hint = LockHint(
                alongMeters = along.takeIf { it.isFinite() },
                bearingDegrees = travelBearing ?: bearing.takeIf { it.isFinite() },
                speedMetersPerSecond = speed,
                accuracyMeters = accuracy,
                departed = released
            )
            val snap = RouteLock.snap(route, lon, lat, hint) ?: return null
            targetAlong = snap.alongMeters
            released = snap.departed
            if (along.isNaN() || (wasReleased && !released)) along = targetAlong
        }
        if (along.isNaN()) return null
        if (!released) {
            if (speed > 0.0) along += speed * dt
            val error = targetAlong - along
            if (speed <= 0.0) {
                val limit = kotlin.math.max(8.0, accuracy)
                if (kotlin.math.abs(error) > limit) along += error.coerceIn(-1.5 * dt, 1.5 * dt)
            } else if (kotlin.math.abs(error) < 1.2) {
                along = targetAlong
            } else {
                val fix = (error * (dt / 0.40)).coerceIn(-10.0 * dt, 14.0 * dt)
                along += fix
            }
            val total = routeLength(route)
            if (total > 0.0) along = along.coerceIn(0.0, total)
        }
        val aim = if (released) {
            travelBearing?.takeIf { speed >= GpsTrackFilter.headingSpeedMetersPerSecond && it.isFinite() }
                ?: bearing.takeIf { it.isFinite() }
                ?: RouteHeading.forwardDegrees(route, along)
        } else {
            RouteHeading.forwardDegrees(route, along)
        }
        if (aim != null) {
            if (bearing.isNaN() || speed < GpsTrackFilter.stoppedMetersPerSecond) {
                if (bearing.isNaN()) bearing = aim
            } else {
                val limit = (40.0 + speed * 6.0) * dt
                bearing = MarkerBearing.approach(bearing, aim, limit)
            }
        }
        val place = if (released) OsrmPoint(lon, lat) else RouteLock.pointAt(route, along) ?: return null
        val shown = bearing.takeIf { it.isFinite() } ?: return null
        return RoutePose(place.lon, place.lat, shown, if (released) targetAlong else along)
    }

    private fun routeLength(route: List<OsrmPoint>): Double {
        val line = route.filter { it.lon.isFinite() && it.lat.isFinite() }
        if (line.size < 2) return 0.0
        val origin = line.first()
        val local = line.map { GeoMeters.local(origin, it) }
        var total = 0.0
        for (index in 1 until local.size) {
            total += kotlin.math.hypot(
                local[index].first - local[index - 1].first,
                local[index].second - local[index - 1].second
            )
        }
        return total
    }
}
