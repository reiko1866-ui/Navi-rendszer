package hu.toonnav.map

import kotlin.math.hypot

/**
 * One OSRM replacement at a time. A newer departure cancels the token of the
 * request already running. A failure waits briefly, then another try is allowed.
 */
class RerouteSession {
    var inFlight: Boolean = false
        private set
    private var quietUntilMs = 0L
    private var rejected = false
    private var ticket = 0
    private var startedAtMs = 0L
    private var originLon = Double.NaN
    private var originLat = Double.NaN

    fun allow(nowMs: Long, confirmed: Boolean, guiding: Boolean): Boolean {
        return guiding && confirmed && !inFlight && nowMs >= quietUntilMs
    }

    fun cooling(nowMs: Long): Boolean = rejected && !inFlight && nowMs < quietUntilMs

    fun started(lon: Double, lat: Double, nowMs: Long): Int {
        inFlight = true
        rejected = false
        startedAtMs = nowMs
        originLon = lon
        originLat = lat
        ticket += 1
        return ticket
    }

    fun started() {
        started(Double.NaN, Double.NaN, 0L)
    }

    fun shouldReplace(nowMs: Long, lon: Double, lat: Double): Boolean {
        if (!inFlight) return false
        if (nowMs - startedAtMs >= replaceAfterMs) return true
        if (!lon.isFinite() || !lat.isFinite() || !originLon.isFinite() || !originLat.isFinite()) return false
        return distance(originLon, originLat, lon, lat) >= replaceMoveMeters
    }

    fun replace(lon: Double, lat: Double, nowMs: Long): Int = started(lon, lat, nowMs)

    fun stale(token: Int): Boolean = token != ticket

    fun succeeded(nowMs: Long) {
        inFlight = false
        rejected = false
        quietUntilMs = nowMs + successQuietMs
    }

    fun failed(nowMs: Long) {
        inFlight = false
        rejected = true
        quietUntilMs = nowMs + failureQuietMs
    }

    fun reset() {
        inFlight = false
        rejected = false
        quietUntilMs = 0L
        ticket += 1
        originLon = Double.NaN
        originLat = Double.NaN
    }

    private fun distance(fromLon: Double, fromLat: Double, toLon: Double, toLat: Double): Double {
        val local = GeoMeters.local(OsrmPoint(fromLon, fromLat), OsrmPoint(toLon, toLat))
        return hypot(local.first, local.second)
    }

    companion object {
        const val successQuietMs = 6_000L
        const val failureQuietMs = 5_000L
        const val replaceAfterMs = 2_500L
        const val replaceMoveMeters = 35.0
    }
}

object ReroutePolicy {
    const val connectTimeoutMs = 6_000
    const val readTimeoutMs = 8_000

    fun startRadius(accuracyMeters: Double?): Double {
        val accuracy = accuracyMeters?.takeIf { it.isFinite() && it >= 0.0 } ?: 30.0
        return (accuracy * 3.0 + 20.0).coerceIn(40.0, 250.0)
    }
}
