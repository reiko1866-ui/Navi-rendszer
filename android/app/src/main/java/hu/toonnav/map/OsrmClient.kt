package hu.toonnav.map

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

data class OsrmPoint(val lon: Double, val lat: Double)

data class OsrmManeuver(
    val type: String,
    val modifier: String,
    val location: OsrmPoint,
    val exit: Int?
)

data class OsrmStep(
    val name: String,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val maneuver: OsrmManeuver
)

data class OsrmWaypoint(
    val name: String,
    val location: OsrmPoint,
    val snapMeters: Double?
)

data class OsrmRoutePlan(
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val coordinates: List<OsrmPoint>,
    val steps: List<OsrmStep>,
    val waypoints: List<OsrmWaypoint>
)

sealed class OsrmResult {
    data class Ready(val plan: OsrmRoutePlan) : OsrmResult()
    data object NoRoute : OsrmResult()
    data class NoSegment(val coordinateIndex: Int) : OsrmResult()
    data object Limit : OsrmResult()
    data object Network : OsrmResult()
    data object Malformed : OsrmResult()
}

class OsrmClient {
    private val pool = Executors.newSingleThreadExecutor()

    fun requestRoute(
        startLon: Double,
        startLat: Double,
        goalLon: Double,
        goalLat: Double,
        startRadiusMeters: Double = snapMeters,
        onResult: (OsrmResult) -> Unit
    ) {
        pool.execute {
            try {
                val connection = (URL(routeUrl(startLon, startLat, goalLon, goalLat, startRadiusMeters)).openConnection() as HttpURLConnection)
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "ToonNav")
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                connection.disconnect()
                onResult(interpret(code, body))
            } catch (err: Exception) {
                onResult(OsrmResult.Network)
            }
        }
    }

    companion object {
        const val snapMeters = 2000.0
        private val coordinateIndex = Regex("""coordinate\s+(\d+)""")

        fun routeUrl(
            startLon: Double,
            startLat: Double,
            goalLon: Double,
            goalLat: Double,
            startRadiusMeters: Double = snapMeters,
            goalRadiusMeters: Double = snapMeters
        ): String {
            return "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                pair(startLon, startLat) + ";" +
                pair(goalLon, goalLat) +
                "?overview=full&geometries=geojson&steps=true&alternatives=false&radiuses=" +
                radius(startRadiusMeters) + ";" + radius(goalRadiusMeters)
        }

        fun interpret(httpCode: Int, body: String): OsrmResult {
            if (httpCode == 429) return OsrmResult.Limit
            if (httpCode !in 200..299) return OsrmResult.Network
            return parse(body)
        }

        fun parse(body: String): OsrmResult {
            val root = try {
                JSONObject(body)
            } catch (err: Exception) {
                return OsrmResult.Malformed
            }
            val code = root.optString("code")
            if (code == "NoRoute") return OsrmResult.NoRoute
            if (code == "NoSegment") return OsrmResult.NoSegment(segmentIndex(root.optString("message")))
            if (code != "Ok") return OsrmResult.Malformed
            val routes = root.optJSONArray("routes") ?: return OsrmResult.Malformed
            if (routes.length() == 0) return OsrmResult.NoRoute
            val route = routes.optJSONObject(0) ?: return OsrmResult.Malformed
            return readRoute(route, root) ?: OsrmResult.Malformed
        }

        private fun readRoute(route: JSONObject, root: JSONObject): OsrmResult.Ready? {
            val distance = number(route.opt("distance")) ?: return null
            if (distance < 0) return null
            val duration = number(route.opt("duration"))
            val coordinates = readLine(route.optJSONObject("geometry")) ?: return null
            if (coordinates.size < 2) return null
            val steps = readSteps(route)
            val waypoints = readWaypoints(root)
            return OsrmResult.Ready(OsrmRoutePlan(distance, duration, coordinates, steps, waypoints))
        }

        private fun readLine(geometry: JSONObject?): List<OsrmPoint>? {
            val coordinates = geometry?.optJSONArray("coordinates") ?: return null
            val points = ArrayList<OsrmPoint>()
            for (i in 0 until coordinates.length()) {
                val pair = coordinates.optJSONArray(i) ?: return null
                if (pair.length() < 2) return null
                val lon = number(pair.opt(0)) ?: return null
                val lat = number(pair.opt(1)) ?: return null
                points.add(OsrmPoint(lon, lat))
            }
            return points
        }

        private fun readSteps(route: JSONObject): List<OsrmStep> {
            val legs = route.optJSONArray("legs") ?: return emptyList()
            val steps = ArrayList<OsrmStep>()
            for (legIndex in 0 until legs.length()) {
                val list = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
                for (stepIndex in 0 until list.length()) {
                    val step = list.optJSONObject(stepIndex) ?: continue
                    val maneuver = step.optJSONObject("maneuver") ?: continue
                    val location = maneuver.optJSONArray("location") ?: continue
                    if (location.length() < 2) continue
                    val lon = number(location.opt(0)) ?: continue
                    val lat = number(location.opt(1)) ?: continue
                    val distance = number(step.opt("distance")) ?: 0.0
                    steps.add(
                        OsrmStep(
                            name = step.optString("name"),
                            distanceMeters = distance,
                            durationSeconds = number(step.opt("duration")),
                            maneuver = OsrmManeuver(
                                type = maneuver.optString("type"),
                                modifier = maneuver.optString("modifier"),
                                location = OsrmPoint(lon, lat),
                                exit = wholeExit(maneuver.opt("exit"))
                            )
                        )
                    )
                }
            }
            return steps
        }

        private fun readWaypoints(root: JSONObject): List<OsrmWaypoint> {
            val list = root.optJSONArray("waypoints") ?: return emptyList()
            val waypoints = ArrayList<OsrmWaypoint>()
            for (i in 0 until list.length()) {
                val waypoint = list.optJSONObject(i) ?: continue
                val location = waypoint.optJSONArray("location") ?: continue
                if (location.length() < 2) continue
                val lon = number(location.opt(0)) ?: continue
                val lat = number(location.opt(1)) ?: continue
                waypoints.add(OsrmWaypoint(waypoint.optString("name"), OsrmPoint(lon, lat), number(waypoint.opt("distance"))))
            }
            return waypoints
        }

        private fun wholeExit(value: Any?): Int? {
            val number = number(value) ?: return null
            val whole = number.toInt()
            if (number != whole.toDouble() || whole !in 1..12) return null
            return whole
        }

        private fun segmentIndex(message: String): Int {
            val match = coordinateIndex.find(message) ?: return -1
            return match.groupValues[1].toIntOrNull() ?: -1
        }

        private fun number(value: Any?): Double? {
            if (value !is Number) return null
            val number = value.toDouble()
            if (!number.isFinite()) return null
            return number
        }

        private fun pair(lon: Double, lat: Double): String {
            return String.format(Locale.US, "%.6f,%.6f", lon, lat)
        }

        private fun radius(meters: Double): String {
            return String.format(Locale.US, "%.0f", meters)
        }
    }
}
