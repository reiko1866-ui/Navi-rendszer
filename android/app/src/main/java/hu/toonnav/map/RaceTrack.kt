package hu.toonnav.map

import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns a route polyline into local meters for the 3D road. A missing live
 * route stays a labeled sample. It does not invent a second path beside the
 * coordinates it is given.
 */
object RaceTrack {
    fun samplesFor(route: List<OsrmPoint>): List<RoadSample> {
        val local = localMeters(route)
        if (local.size < 2) return emptyList()
        var length = 0.0
        for (index in 1 until local.size) {
            length += kotlin.math.hypot(
                local[index].first - local[index - 1].first,
                local[index].second - local[index - 1].second
            )
        }
        val step = (length / 1600.0).coerceAtLeast(2.0)
        return RaceGeometry.sampleRoute(local, step)
    }

    fun bearing(route: List<OsrmPoint>, samples: List<RoadSample>, alongMeters: Double): Double {
        if (route.size < 2 || samples.size < 2) return 0.0
        val here = pointAt(samples, alongMeters)
        val origin = route.first()
        val geo = geographic(origin, here.east, here.north)
        val posed = RouteMotion.pose(route, geo.lon, geo.lat, 0.0, true, 0L, true)
        if (posed != null) return posed.bearingDegrees
        val ahead = pointAt(samples, alongMeters + RouteMotion.lookAheadMeters)
        return Math.toDegrees(kotlin.math.atan2(ahead.east - here.east, ahead.north - here.north))
    }

    fun model(route: List<OsrmPoint>, body: Int, accent: Int): RaceModel {
        val samples = samplesFor(route)
        val materials = RaceGeometry.materials(
            channel(body, 16), channel(body, 8), channel(body, 0),
            channel(accent, 16), channel(accent, 8), channel(accent, 0)
        )
        return RaceModel(
            RaceGeometry.carModel(materials[0], materials[1]),
            RaceGeometry.worldModel(samples),
            materials
        )
    }

    private fun channel(color: Int, shift: Int): Float = ((color ushr shift) and 0xFF) / 255f

    fun localMeters(route: List<OsrmPoint>): List<Pair<Double, Double>> {
        if (route.size < 2) return emptyList()
        val origin = route.first()
        return route.map { point ->
            val local = GeoMeters.local(origin, point)
            local.first to local.second
        }
    }

    fun pointAt(samples: List<RoadSample>, alongMeters: Double): RoadSample {
        if (samples.isEmpty()) return RoadSample(0.0, 0.0, 0.0, 0.0)
        if (alongMeters <= samples.first().along) return samples.first()
        val last = samples.last()
        if (alongMeters >= last.along) return last
        for (index in 0 until samples.size - 1) {
            val start = samples[index]
            val end = samples[index + 1]
            if (alongMeters > end.along) continue
            val span = (end.along - start.along).coerceAtLeast(0.001)
            val blend = ((alongMeters - start.along) / span).coerceIn(0.0, 1.0)
            return RoadSample(
                start.east + (end.east - start.east) * blend,
                start.north + (end.north - start.north) * blend,
                alongMeters,
                start.turn
            )
        }
        return last
    }

    fun samplePreview(): List<OsrmPoint> {
        val origin = OsrmPoint(19.0, 47.0)
        val points = ArrayList<OsrmPoint>()
        var east = 0.0
        var north = 0.0
        var heading = 0.0
        repeat(90) { step ->
            points.add(geographic(origin, east, north))
            val bend = when {
                step in 28..48 -> Math.toRadians(3.2)
                step in 62..74 -> Math.toRadians(-2.4)
                else -> 0.0
            }
            heading += bend
            east += sin(heading) * 4.0
            north += cos(heading) * 4.0
        }
        return points
    }

    fun geographic(origin: OsrmPoint, east: Double, north: Double): OsrmPoint {
        val lat0 = Math.toRadians(origin.lat)
        val latitude = origin.lat + Math.toDegrees(north / GeoMeters.radiusMeters)
        val longitude = origin.lon + Math.toDegrees(east / (GeoMeters.radiusMeters * cos(lat0)))
        return OsrmPoint(longitude, latitude)
    }
}
