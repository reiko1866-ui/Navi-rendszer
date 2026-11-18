package hu.toonnav.map

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

data class GpsSample(
    val lon: Double,
    val lat: Double,
    val timeMs: Long,
    val accuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val bearingDegrees: Double?
)

data class GpsEstimate(
    val lon: Double,
    val lat: Double,
    val speedMetersPerSecond: Double,
    val bearingDegrees: Double?,
    val accuracyMeters: Double?,
    val rejected: Boolean
)

/**
 * Adaptive GPS estimate. A single spike is ignored. Smoothing tightens when
 * the fix is accurate and the vehicle is moving, and it freezes the heading
 * while the vehicle is stopped. This is not a claim about a phone.
 */
class GpsTrackFilter {
    private var lon = Double.NaN
    private var lat = Double.NaN
    private var speed = 0.0
    private var bearing: Double? = null
    private var accuracy: Double? = null
    private var timeMs = 0L
    private var outlier: GpsSample? = null

    fun reset() {
        lon = Double.NaN
        lat = Double.NaN
        speed = 0.0
        bearing = null
        accuracy = null
        timeMs = 0L
        outlier = null
    }

    fun accept(sample: GpsSample): GpsEstimate {
        if (!sample.lon.isFinite() || !sample.lat.isFinite()) return current(true)
        val reportedAccuracy = sample.accuracyMeters?.takeIf { it.isFinite() && it >= 0.0 }
        if (lon.isNaN()) {
            lon = sample.lon
            lat = sample.lat
            timeMs = sample.timeMs
            accuracy = reportedAccuracy
            speed = usableSpeed(sample.speedMetersPerSecond) ?: 0.0
            bearing = sample.bearingDegrees?.takeIf { it.isFinite() }?.let(VehicleHeading::normalize)
            outlier = null
            return current(false)
        }
        val dt = (sample.timeMs - timeMs) / 1000.0
        if (dt < 0.0) return current(true)
        if (dt > gapResetSeconds) {
            lon = sample.lon
            lat = sample.lat
            timeMs = sample.timeMs
            accuracy = reportedAccuracy
            speed = usableSpeed(sample.speedMetersPerSecond) ?: speed
            outlier = null
            return current(false)
        }
        val moved = distance(lon, lat, sample.lon, sample.lat)
        val acc = reportedAccuracy ?: assumedAccuracyMeters
        val gate = gateMeters(acc, speed, dt)
        if (moved > gate) {
            val prior = outlier
            val agrees = prior != null &&
                sample.timeMs - prior.timeMs in 0..outlierWindowMs &&
                distance(prior.lon, prior.lat, sample.lon, sample.lat) <= kotlin.math.max(acc, outlierAgreeMeters)
            if (!agrees) {
                outlier = sample
                return current(true)
            }
            outlier = null
            blend(sample, dt, acc, relocationAlpha)
            return current(false)
        }
        outlier = null
        blend(sample, dt, acc, alpha(acc, speed))
        return current(false)
    }

    private fun blend(sample: GpsSample, dt: Double, accuracyMeters: Double, alpha: Double) {
        val moved = distance(lon, lat, sample.lon, sample.lat)
        val reported = usableSpeed(sample.speedMetersPerSecond)
        val derived = if (dt >= 0.25 && moved >= kotlin.math.max(4.0, accuracyMeters * 0.45)) moved / dt else null
        val measured = reported ?: derived
        if (measured != null) speed += (measured - speed) * 0.45
        if (speed < stoppedMetersPerSecond) speed = 0.0
        moveToward(sample.lon, sample.lat, alpha)
        val remain = distance(lon, lat, sample.lon, sample.lat)
        val budget = kotlin.math.max(lagBudgetMeters, accuracyMeters * 0.35)
        if (remain > budget && speed >= 2.0 && accuracyMeters <= 25.0) {
            val extra = ((remain - budget) / remain).coerceIn(0.0, 0.9)
            moveToward(sample.lon, sample.lat, extra)
        }
        updateBearing(sample, accuracyMeters, moved, dt)
        accuracy = sample.accuracyMeters?.takeIf { it.isFinite() && it >= 0.0 } ?: accuracy
        timeMs = sample.timeMs
    }

    private fun updateBearing(sample: GpsSample, accuracyMeters: Double, moved: Double, dt: Double) {
        if (speed < headingSpeedMetersPerSecond) return
        val reported = sample.bearingDegrees?.takeIf { it.isFinite() && accuracyMeters <= 30.0 && (usableSpeed(sample.speedMetersPerSecond) ?: 0.0) >= headingSpeedMetersPerSecond }
        val course = reported ?: if (moved >= kotlin.math.max(5.0, accuracyMeters * 0.45)) {
            val local = GeoMeters.local(OsrmPoint(lon, lat), OsrmPoint(sample.lon, sample.lat))
            if (hypot(local.first, local.second) < 1.0) null
            else VehicleHeading.normalize(Math.toDegrees(atan2(local.first, local.second)))
        } else {
            null
        }
        val aim = course ?: return
        val from = bearing
        if (from == null) {
            bearing = aim
            return
        }
        val limit = (headingBaseDegreesPerSecond + speed * headingSpeedGain) * dt.coerceAtLeast(0.0)
        bearing = MarkerBearing.approach(from, aim, limit)
    }

    private fun moveToward(targetLon: Double, targetLat: Double, fraction: Double) {
        val blend = fraction.coerceIn(0.0, 1.0)
        if (blend <= 0.0) return
        val local = GeoMeters.local(OsrmPoint(lon, lat), OsrmPoint(targetLon, targetLat))
        val lat0 = Math.toRadians(lat)
        lat += Math.toDegrees(local.second * blend / GeoMeters.radiusMeters)
        lon += Math.toDegrees(local.first * blend / (GeoMeters.radiusMeters * cos(lat0)))
    }

    private fun current(rejected: Boolean): GpsEstimate {
        return GpsEstimate(lon, lat, speed, bearing, accuracy, rejected)
    }

    companion object {
        const val stoppedMetersPerSecond = 0.8
        const val headingSpeedMetersPerSecond = 1.4
        const val assumedAccuracyMeters = 35.0
        const val gapResetSeconds = 30.0
        const val outlierWindowMs = 4_000L
        const val outlierAgreeMeters = 20.0
        const val relocationAlpha = 0.8
        const val lagBudgetMeters = 3.5
        const val headingBaseDegreesPerSecond = 35.0
        const val headingSpeedGain = 4.0

        fun alpha(accuracyMeters: Double, speedMetersPerSecond: Double): Double {
            var value = when {
                accuracyMeters <= 8.0 -> 0.78
                accuracyMeters <= 15.0 -> 0.62
                accuracyMeters <= 30.0 -> 0.40
                accuracyMeters <= 50.0 -> 0.22
                else -> 0.10
            }
            if (speedMetersPerSecond < stoppedMetersPerSecond) value = kotlin.math.min(value, 0.06)
            if (speedMetersPerSecond > 12.0 && accuracyMeters <= 15.0) value = kotlin.math.max(value, 0.80)
            return value
        }

        fun gateMeters(accuracyMeters: Double, speedMetersPerSecond: Double, dtSeconds: Double): Double {
            val predicted = speedMetersPerSecond * dtSeconds.coerceIn(0.0, 4.0)
            val motion = predicted * 1.6 + 8.0
            return if (accuracyMeters <= 20.0) {
                kotlin.math.max(accuracyMeters * 2.4, motion)
            } else {
                kotlin.math.max(accuracyMeters * 1.15, motion)
            }
        }

        private fun usableSpeed(speed: Double?): Double? {
            val value = speed ?: return null
            if (!value.isFinite() || value < 0.0) return null
            return value
        }

        private fun distance(fromLon: Double, fromLat: Double, toLon: Double, toLat: Double): Double {
            val local = GeoMeters.local(OsrmPoint(fromLon, fromLat), OsrmPoint(toLon, toLat))
            return hypot(local.first, local.second)
        }
    }
}
