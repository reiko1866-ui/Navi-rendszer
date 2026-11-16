package hu.toonnav.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Which cockpit is on screen. It does not read GPS, plan a route, or decide arrival.
 * Arrival and cancellation both leave the driving instruments, and they stay distinct.
 */
data class DriveSession(
    val guiding: Boolean = false,
    val arrived: Boolean = false,
    val collapsed: Boolean = false,
    val searchOpen: Boolean = false
) {
    val canFold: Boolean get() = guiding && !arrived

    /** Speed, maneuver, remaining distance and arrival time. */
    val instrumentsOpen: Boolean get() = guiding && !arrived && !collapsed && !searchOpen

    /** The existing follow camera stays on while the trip is active, including a folded cockpit. */
    val followCamera: Boolean get() = guiding && !arrived

    fun start(): DriveSession = DriveSession(guiding = true)

    fun cancel(): DriveSession = DriveSession()

    fun arrive(): DriveSession = DriveSession(arrived = true)

    fun toggleCollapse(): DriveSession {
        if (!canFold) return this
        return copy(collapsed = !collapsed)
    }

    fun toggleSearch(): DriveSession = copy(searchOpen = !searchOpen)

    fun closeSearch(): DriveSession = if (searchOpen) copy(searchOpen = false) else this

    /** A newly opened screen with a trip still running shows the driving cockpit. */
    fun openedAgain(): DriveSession = copy(collapsed = false, searchOpen = false)

    companion object {
        fun restore(stored: DriveSession, hasPlan: Boolean): DriveSession {
            if (stored.guiding && !stored.arrived && hasPlan) return stored.openedAgain()
            return DriveSession()
        }
    }
}

/**
 * The calculated trip kept for the lifetime of this process.
 * A new process has no trip, so a cold start does not invent one.
 */
object DriveSessionMemory {
    var plan: OsrmRoutePlan? = null
    var routeJson: String? = null
    var routePoints: List<OsrmPoint> = emptyList()
    var traveledMeters: Double = 0.0
    var origin: Place? = null
    var destination: Place? = null
    var revision: Int = 0
    var abandon: Boolean = false

    fun hold(
        plan: OsrmRoutePlan,
        routeJson: String?,
        routePoints: List<OsrmPoint>,
        traveledMeters: Double,
        origin: Place?,
        destination: Place?
    ) {
        this.plan = plan
        this.routeJson = routeJson
        this.routePoints = routePoints
        this.traveledMeters = traveledMeters
        this.origin = origin
        this.destination = destination
        revision += 1
    }

    fun clear() {
        plan = null
        routeJson = null
        routePoints = emptyList()
        traveledMeters = 0.0
        origin = null
        destination = null
        revision += 1
    }
}

/**
 * The same trip, kept in the app's private files so a reopened process can
 * show the driving view again. It is removed when the trip ends.
 */
object DriveTripFile {
    private const val NAME = "toonnav-drive-trip.json"

    fun save(context: Context) {
        val plan = DriveSessionMemory.plan ?: return
        try {
            val json = JSONObject()
            json.put("distance", plan.distanceMeters)
            json.put("duration", plan.durationSeconds ?: JSONObject.NULL)
            json.put("routeJson", DriveSessionMemory.routeJson ?: JSONObject.NULL)
            json.put("traveled", DriveSessionMemory.traveledMeters)
            json.put("coordinates", points(plan.coordinates))
            json.put("line", points(DriveSessionMemory.routePoints))
            json.put("steps", JSONArray().also { steps ->
                for (step in plan.steps) {
                    steps.put(JSONObject()
                        .put("name", step.name)
                        .put("distance", step.distanceMeters)
                        .put("duration", step.durationSeconds ?: JSONObject.NULL)
                        .put("type", step.maneuver.type)
                        .put("modifier", step.maneuver.modifier)
                        .put("exit", step.maneuver.exit ?: JSONObject.NULL)
                        .put("lon", step.maneuver.location.lon)
                        .put("lat", step.maneuver.location.lat))
                }
            })
            json.put("waypoints", JSONArray().also { marks ->
                for (mark in plan.waypoints) {
                    marks.put(JSONObject()
                        .put("name", mark.name)
                        .put("lon", mark.location.lon)
                        .put("lat", mark.location.lat)
                        .put("snap", mark.snapMeters ?: JSONObject.NULL))
                }
            })
            json.put("origin", place(DriveSessionMemory.origin))
            json.put("destination", place(DriveSessionMemory.destination))
            file(context).writeText(json.toString())
        } catch (err: Exception) {
            // The in-memory trip still drives the current process.
        }
    }

    fun load(context: Context): Boolean {
        val stored = file(context)
        if (!stored.isFile) return false
        return try {
            val json = JSONObject(stored.readText())
            val plan = OsrmRoutePlan(
                json.getDouble("distance"),
                json.optDoubleOrNull("duration"),
                readPoints(json.getJSONArray("coordinates")),
                readSteps(json.getJSONArray("steps")),
                readWaypoints(json.getJSONArray("waypoints"))
            )
            if (plan.coordinates.size < 2) return false
            DriveSessionMemory.hold(
                plan,
                json.optStringOrNull("routeJson"),
                readPoints(json.getJSONArray("line")),
                json.optDouble("traveled", 0.0),
                readPlace(json.optJSONObject("origin")),
                readPlace(json.optJSONObject("destination"))
            )
            true
        } catch (err: Exception) {
            false
        }
    }

    fun delete(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context): File = File(context.applicationContext.filesDir, NAME)

    private fun points(values: List<OsrmPoint>): JSONArray {
        return JSONArray().also { array ->
            for (point in values) array.put(JSONArray().put(point.lon).put(point.lat))
        }
    }

    private fun readPoints(array: JSONArray): List<OsrmPoint> {
        val points = ArrayList<OsrmPoint>(array.length())
        for (index in 0 until array.length()) {
            val pair = array.getJSONArray(index)
            points.add(OsrmPoint(pair.getDouble(0), pair.getDouble(1)))
        }
        return points
    }

    private fun readSteps(array: JSONArray): List<OsrmStep> {
        val steps = ArrayList<OsrmStep>(array.length())
        for (index in 0 until array.length()) {
            val step = array.getJSONObject(index)
            steps.add(OsrmStep(
                step.optString("name"),
                step.getDouble("distance"),
                step.optDoubleOrNull("duration"),
                OsrmManeuver(
                    step.optString("type"),
                    step.optString("modifier"),
                    OsrmPoint(step.getDouble("lon"), step.getDouble("lat")),
                    if (step.isNull("exit")) null else step.getInt("exit")
                )
            ))
        }
        return steps
    }

    private fun readWaypoints(array: JSONArray): List<OsrmWaypoint> {
        val marks = ArrayList<OsrmWaypoint>(array.length())
        for (index in 0 until array.length()) {
            val mark = array.getJSONObject(index)
            marks.add(OsrmWaypoint(
                mark.optString("name"),
                OsrmPoint(mark.getDouble("lon"), mark.getDouble("lat")),
                mark.optDoubleOrNull("snap")
            ))
        }
        return marks
    }

    private fun place(value: Place?): Any {
        if (value == null) return JSONObject.NULL
        return JSONObject()
            .put("name", value.name)
            .put("detail", value.detail)
            .put("lat", value.lat)
            .put("lon", value.lon)
            .put("precision", value.precision.name)
    }

    private fun readPlace(json: JSONObject?): Place? {
        if (json == null) return null
        val precision = try {
            PlacePrecision.valueOf(json.optString("precision"))
        } catch (err: IllegalArgumentException) {
            PlacePrecision.PLACE
        }
        return Place(json.optString("name"), json.optString("detail"), json.getDouble("lat"), json.getDouble("lon"), precision)
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return getDouble(name)
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val text = getString(name)
        return text.ifBlank { null }
    }
}

class DriveSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): DriveSession {
        return DriveSession(
            guiding = prefs.getBoolean(KEY_GUIDING, false),
            arrived = prefs.getBoolean(KEY_ARRIVED, false),
            collapsed = prefs.getBoolean(KEY_COLLAPSED, false)
        )
    }

    fun write(session: DriveSession) {
        prefs.edit()
            .putBoolean(KEY_GUIDING, session.guiding)
            .putBoolean(KEY_ARRIVED, session.arrived)
            .putBoolean(KEY_COLLAPSED, session.collapsed)
            .commit()
    }

    companion object {
        const val PREFS = "toonnav-drive"
        private const val KEY_GUIDING = "guiding"
        private const val KEY_ARRIVED = "arrived"
        private const val KEY_COLLAPSED = "collapsed"
    }
}
