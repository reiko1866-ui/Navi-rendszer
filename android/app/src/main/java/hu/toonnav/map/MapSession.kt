package hu.toonnav.map

import java.util.Locale
import kotlin.math.hypot

object MapStyles {
    const val day = "https://tiles.openfreemap.org/styles/liberty"
    const val night = "https://tiles.openfreemap.org/styles/dark"

    fun uri(nightMode: Boolean): String = if (nightMode) night else day
}

enum class LocationAccess {
    READY,
    PRECISE_REQUIRED,
    DENIED,
    PROVIDER_OFF
}

object LocationAccessGate {
    fun decide(fineGranted: Boolean, coarseGranted: Boolean, providerEnabled: Boolean): LocationAccess {
        if (!fineGranted && !coarseGranted) return LocationAccess.DENIED
        if (!providerEnabled) return LocationAccess.PROVIDER_OFF
        if (!fineGranted) return LocationAccess.PRECISE_REQUIRED
        return LocationAccess.READY
    }
}

enum class GpsSampleVerdict {
    LIVE,
    STALE,
    MOCK,
    INVALID
}

object GpsFixGate {
    fun ageMs(elapsedRealtimeNanos: Long, nowElapsedNanos: Long, timeMillis: Long, nowMillis: Long): Long {
        if (elapsedRealtimeNanos > 0L && nowElapsedNanos > 0L) {
            val age = (nowElapsedNanos - elapsedRealtimeNanos) / 1_000_000L
            return if (age < 0L) 0L else age
        }
        if (timeMillis <= 0L || nowMillis <= 0L) return Long.MAX_VALUE
        val age = nowMillis - timeMillis
        return if (age < 0L) 0L else age
    }

    fun verdict(latitude: Double, longitude: Double, ageMs: Long, mock: Boolean): GpsSampleVerdict {
        if (!latitude.isFinite() || !longitude.isFinite()) return GpsSampleVerdict.INVALID
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return GpsSampleVerdict.INVALID
        if (mock) return GpsSampleVerdict.MOCK
        if (ageMs < 0L || ageMs > GpsTrustGate.lostAfterMs) return GpsSampleVerdict.STALE
        return GpsSampleVerdict.LIVE
    }
}

data class ListenTarget(val provider: String, val highAccuracy: Boolean)

object LocationListenPlan {
    const val fused = "fused"
    const val gps = "gps"
    const val network = "network"

    fun targets(
        sdkInt: Int,
        fineGranted: Boolean,
        locationEnabled: Boolean,
        gpsEnabled: Boolean,
        networkEnabled: Boolean
    ): List<ListenTarget> {
        if (!locationEnabled) return emptyList()
        val targets = ArrayList<ListenTarget>()
        if (sdkInt >= 31) targets.add(ListenTarget(fused, true))
        if (fineGranted && gpsEnabled) targets.add(ListenTarget(gps, true))
        if (networkEnabled) targets.add(ListenTarget(network, false))
        return targets
    }
}

object GpsOrigin {
    fun place(name: String, sawFix: Boolean, latitude: Double, longitude: Double): Place? {
        if (!sawFix || GpsReadout.coordinates(latitude, longitude) == null) return null
        return Place(name, "", latitude, longitude, PlacePrecision.PLACE)
    }
}

object GpsReadout {
    fun coordinates(latitude: Double, longitude: Double): String? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    }

    fun speedKmh(metersPerSecond: Float, hasSpeed: Boolean, ageMs: Long): Int? {
        if (!hasSpeed) return null
        return GpsSpeed.kmh(metersPerSecond.toDouble(), ageMs)
    }

    fun text(latitude: Double, longitude: Double, speedKmh: Int?): String? {
        val position = coordinates(latitude, longitude) ?: return null
        if (speedKmh == null) return position
        return "$position · $speedKmh km/h"
    }
}

object RouteLineJson {
    fun feature(points: List<OsrmPoint>): String? {
        if (points.size < 2) return null
        if (points.any { !it.lon.isFinite() || !it.lat.isFinite() }) return null
        val coordinates = points.joinToString(",") { point ->
            String.format(Locale.US, "[%.6f,%.6f]", point.lon, point.lat)
        }
        return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
    }
}

data class RouteLineParts(
    val traveled: List<OsrmPoint>,
    val remaining: List<OsrmPoint>
)

object RouteLineSplit {
    fun cut(points: List<OsrmPoint>, traveledMeters: Double): RouteLineParts {
        val line = points.filter { it.lon.isFinite() && it.lat.isFinite() }
        if (line.size < 2 || !traveledMeters.isFinite() || traveledMeters <= 0.0) {
            return RouteLineParts(emptyList(), line)
        }
        val origin = line.first()
        val local = line.map { GeoMeters.local(origin, it) }
        var cursor = 0.0
        for (index in 1 until line.size) {
            val start = local[index - 1]
            val end = local[index]
            val length = hypot(end.first - start.first, end.second - start.second)
            if (length < 0.05) continue
            if (traveledMeters > cursor + length) {
                cursor += length
                continue
            }
            val into = (traveledMeters - cursor).coerceIn(0.0, length)
            val fraction = into / length
            return splitAt(line, index, fraction)
        }
        return RouteLineParts(line, emptyList())
    }

    private fun splitAt(line: List<OsrmPoint>, index: Int, fraction: Double): RouteLineParts {
        val start = line[index - 1]
        val end = line[index]
        val traveled = ArrayList<OsrmPoint>(index + 1)
        for (cursor in 0 until index) traveled.add(line[cursor])
        val remaining = ArrayList<OsrmPoint>(line.size - index + 1)
        if (fraction <= 0.000001) {
            remaining.addAll(line.subList(index - 1, line.size))
        } else if (fraction >= 0.999999) {
            traveled.add(end)
            remaining.addAll(line.subList(index, line.size))
        } else {
            val cut = OsrmPoint(
                start.lon + (end.lon - start.lon) * fraction,
                start.lat + (end.lat - start.lat) * fraction
            )
            traveled.add(cut)
            remaining.add(cut)
            remaining.addAll(line.subList(index, line.size))
        }
        return RouteLineParts(traveled, remaining)
    }
}
