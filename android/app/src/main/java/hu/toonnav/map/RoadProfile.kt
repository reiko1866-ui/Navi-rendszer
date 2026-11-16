package hu.toonnav.map

enum class CenterMark { NONE, DASH }

/**
 * Widths and markings that can be justified from an OSRM road class.
 * Lane counts are not in the route, so no class draws extra lane dividers.
 * A motorway or ramp is treated as one carriageway: edge lines, no opposing center line.
 */
data class RoadProfile(
    val halfWidth: Double,
    val shoulder: Double,
    val center: CenterMark,
    val edgeLines: Boolean,
    val kerbs: Boolean
) {
    companion object {
        fun of(kind: RoadKind): RoadProfile = when (kind) {
            RoadKind.MOTORWAY -> RoadProfile(5.25, 0.6, CenterMark.NONE, edgeLines = true, kerbs = false)
            RoadKind.EXPRESSWAY -> RoadProfile(4.6, 0.8, CenterMark.NONE, edgeLines = true, kerbs = false)
            RoadKind.MAIN -> RoadProfile(3.75, 1.4, CenterMark.DASH, edgeLines = true, kerbs = true)
            RoadKind.SECONDARY -> RoadProfile(3.1, 1.3, CenterMark.DASH, edgeLines = true, kerbs = true)
            RoadKind.RESIDENTIAL -> RoadProfile(2.5, 0.7, CenterMark.NONE, edgeLines = false, kerbs = true)
            RoadKind.ROUNDABOUT -> RoadProfile(3.4, 1.0, CenterMark.NONE, edgeLines = true, kerbs = true)
            RoadKind.RAMP -> RoadProfile(2.4, 0.8, CenterMark.NONE, edgeLines = true, kerbs = false)
            RoadKind.UNKNOWN -> RoadProfile(3.0, 1.0, CenterMark.NONE, edgeLines = false, kerbs = true)
        }
    }
}

object RouteRoadClass {
    /** Assigns one class per overview point by walking the step distances. Empty classes stay unknown. */
    fun kinds(route: List<OsrmPoint>, steps: List<OsrmStep>): List<RoadKind> {
        if (route.isEmpty()) return emptyList()
        val assigned = MutableList(route.size) { RoadKind.UNKNOWN }
        if (steps.isEmpty() || route.size < 2) return assigned
        val along = DoubleArray(route.size)
        for (index in 1 until route.size) {
            val step = GeoMeters.local(route[index - 1], route[index])
            along[index] = along[index - 1] + kotlin.math.hypot(step.first, step.second)
        }
        var cursor = 0.0
        var point = 0
        for (step in steps) {
            val kind = RoadClassifier.classify(step.maneuver.type, step.classes)
            val end = cursor + step.distanceMeters.coerceAtLeast(0.0)
            while (point < route.size && along[point] <= end + 1.0) {
                assigned[point] = kind
                point++
            }
            cursor = end
        }
        if (point in assigned.indices) {
            val tail = assigned[point - 1]
            while (point < assigned.size) {
                assigned[point] = tail
                point++
            }
        }
        return assigned
    }
}
