package hu.toonnav.map

import java.util.Locale

enum class GpsTrust {
    USABLE,
    INACCURATE,
    UNUSABLE,
    LOST
}

object GpsTrustGate {
    const val accurateMeters = 40.0
    const val usableMeters = 120.0
    const val lostAfterMs = 8_000L

    fun assess(ageMs: Long, accuracyMeters: Double?): GpsTrust {
        if (ageMs < 0L || ageMs > lostAfterMs) return GpsTrust.LOST
        if (accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters < 0.0) return GpsTrust.UNUSABLE
        if (accuracyMeters > usableMeters) return GpsTrust.UNUSABLE
        if (accuracyMeters > accurateMeters) return GpsTrust.INACCURATE
        return GpsTrust.USABLE
    }
}

object NavigationPolicy {
    const val arrivalMeters = 30.0
}

object NavigationEta {
    fun fraction(remainingMeters: Double, totalMeters: Double): Double {
        if (!remainingMeters.isFinite() || !totalMeters.isFinite() || totalMeters <= 0.0) return 0.0
        return (remainingMeters / totalMeters).coerceIn(0.0, 1.0)
    }

    fun offsetMillis(durationSeconds: Double?, fraction: Double): Long? {
        if (durationSeconds == null || !durationSeconds.isFinite() || durationSeconds < 0.0) return null
        if (!fraction.isFinite()) return null
        return (durationSeconds * fraction.coerceIn(0.0, 1.0) * 1000.0).toLong()
    }
}

object ManeuverText {
    fun phrase(type: String, modifier: String, exit: Int?, road: String): String {
        val direction = direction(modifier)
        val base = when (type) {
            "arrive" -> "Cél"
            "roundabout", "rotary", "exit roundabout", "exit rotary" -> {
                if (exit != null) "Körforgalom, $exit. kijárat" else "Körforgalom"
            }
            "on ramp" -> join("Felhajtó", direction)
            "off ramp" -> join("Lehajtó", direction)
            "fork" -> join("Elágazás", direction)
            "end of road" -> join("Az út vége", direction)
            "merge" -> join("Sorolj be", direction)
            else -> if (direction.isEmpty()) "" else capitalize(direction)
        }
        val text = when {
            base.isNotEmpty() -> base
            road.isNotBlank() -> road
            else -> "Tovább"
        }
        if (type != "arrive" && road.isNotBlank() && base.isNotEmpty() && !text.contains(road)) {
            return "$text, $road"
        }
        return text
    }

    private fun join(label: String, direction: String): String {
        if (direction.isEmpty()) return label
        return "$label, $direction"
    }

    private fun capitalize(text: String): String {
        return text.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale("hu", "HU")) else char.toString()
        }
    }

    private fun direction(modifier: String): String {
        return when (modifier) {
            "uturn" -> "fordulj vissza"
            "sharp right" -> "élesen jobbra"
            "right" -> "jobbra"
            "slight right" -> "enyhén jobbra"
            "straight" -> "egyenesen"
            "slight left" -> "enyhén balra"
            "left" -> "balra"
            "sharp left" -> "élesen balra"
            else -> ""
        }
    }
}

data class GuidanceCue(
    val maneuverLabel: String,
    val maneuverDistanceMeters: Double,
    val remainingMeters: Double,
    val totalMeters: Double,
    val arrowDegrees: Float,
    val roadName: String,
    val arrived: Boolean,
    val offRouteConfirmed: Boolean,
    val trust: GpsTrust,
    val progressHeld: Boolean,
    val maneuverType: String = "",
    val maneuverModifier: String = "",
    val roadClasses: List<String> = emptyList()
)

data class FollowCamera(
    val move: Boolean,
    val bearing: Double,
    val tilt: Double,
    val zoom: Double
)

object FollowCameraPlan {
    fun decide(following: Boolean, northUp: Boolean, trust: GpsTrust, bearing: Double?): FollowCamera {
        val zoom = (DriveCamera.followMinZoom + DriveCamera.followMaxZoom) / 2.0
        if (!following || trust == GpsTrust.LOST || trust == GpsTrust.UNUSABLE) {
            return FollowCamera(false, 0.0, 0.0, zoom)
        }
        if (northUp) return FollowCamera(true, 0.0, 0.0, zoom)
        val course = bearing?.takeIf { it.isFinite() } ?: 0.0
        val normalized = ((course % 360.0) + 360.0) % 360.0
        return FollowCamera(true, normalized, DriveCamera.followPitch, zoom)
    }
}

class GuidanceSession {
    private val tracker = RouteProgressTracker()
    private var plan: OsrmRoutePlan? = null
    private var arrived = false
    private var lastCue: GuidanceCue? = null
    private var lastAccuracy: Double? = null
    private var destination = "Cél"

    fun setPlan(plan: OsrmRoutePlan) {
        this.plan = plan
        destination = plan.waypoints.lastOrNull()?.name?.trim().orEmpty().ifBlank { "Cél" }
        arrived = false
        lastCue = null
        lastAccuracy = null
        tracker.setPlan(plan)
    }

    fun clear() {
        plan = null
        arrived = false
        lastCue = null
        lastAccuracy = null
        destination = "Cél"
        tracker.clear()
    }

    fun onFix(
        lon: Double,
        lat: Double,
        timeMs: Long,
        ageMs: Long,
        accuracyMeters: Double?,
        speedMetersPerSecond: Double? = null,
        bearingDegrees: Double? = null
    ): GuidanceCue? {
        if (plan == null) return null
        if (!lon.isFinite() || !lat.isFinite()) return refreshAge(ageMs)
        val trust = GpsTrustGate.assess(ageMs, accuracyMeters)
        lastAccuracy = accuracyMeters
        if (arrived) return arrivedCue(trust)
        if (trust == GpsTrust.LOST || trust == GpsTrust.UNUSABLE) {
            tracker.resetHold()
            return hold(trust)
        }
        val tracked = tracker.update(lon, lat, timeMs, accuracyMeters, speedMetersPerSecond, bearingDegrees) ?: return null
        val update = tracked.update
        val reached = trust == GpsTrust.USABLE &&
            !update.offRoute &&
            update.maneuver.type == "arrive" &&
            update.maneuverDistanceMeters <= NavigationPolicy.arrivalMeters
        if (reached) {
            arrived = true
            return arrivedCue(trust)
        }
        val cue = cueFrom(update, trust, tracked.offRouteConfirmed && trust == GpsTrust.USABLE, false)
        lastCue = cue
        return cue
    }

    fun refreshAge(ageMs: Long): GuidanceCue? {
        if (plan == null) return null
        val trust = GpsTrustGate.assess(ageMs, lastAccuracy)
        if (arrived) return arrivedCue(trust)
        if (trust == GpsTrust.LOST || trust == GpsTrust.UNUSABLE) {
            tracker.resetHold()
            return hold(trust)
        }
        val cue = lastCue?.copy(trust = trust) ?: return null
        lastCue = cue
        return cue
    }

    private fun hold(trust: GpsTrust): GuidanceCue? {
        val previous = lastCue ?: return waiting(trust)
        val cue = previous.copy(trust = trust, progressHeld = true, offRouteConfirmed = false)
        lastCue = cue
        return cue
    }

    private fun waiting(trust: GpsTrust): GuidanceCue {
        val route = plan
        val cue = GuidanceCue(
            maneuverLabel = "GPS jelre vár",
            maneuverDistanceMeters = route?.distanceMeters ?: 0.0,
            remainingMeters = route?.distanceMeters ?: 0.0,
            totalMeters = route?.distanceMeters ?: 0.0,
            arrowDegrees = 0f,
            roadName = destination,
            arrived = false,
            offRouteConfirmed = false,
            trust = trust,
            progressHeld = true
        )
        lastCue = cue
        return cue
    }

    private fun arrivedCue(trust: GpsTrust): GuidanceCue {
        val cue = GuidanceCue(
            maneuverLabel = "Cél",
            maneuverDistanceMeters = 0.0,
            remainingMeters = 0.0,
            totalMeters = lastCue?.totalMeters ?: 0.0,
            arrowDegrees = 0f,
            roadName = destination,
            arrived = true,
            offRouteConfirmed = false,
            trust = trust,
            progressHeld = false
        )
        lastCue = cue
        return cue
    }

    private fun cueFrom(update: RouteUpdate, trust: GpsTrust, offRouteConfirmed: Boolean, held: Boolean): GuidanceCue {
        val maneuver = update.maneuver
        return GuidanceCue(
            maneuverLabel = ManeuverText.phrase(maneuver.type, maneuver.modifier, maneuver.exit, maneuver.name),
            maneuverDistanceMeters = update.maneuverDistanceMeters,
            remainingMeters = update.remainingMeters,
            totalMeters = update.totalMeters,
            arrowDegrees = if (maneuver.type == "arrive") 0f else ManeuverEvents.bannerRotation(maneuver.modifier),
            roadName = update.roadName.ifBlank { destination },
            arrived = false,
            offRouteConfirmed = offRouteConfirmed,
            trust = trust,
            progressHeld = held,
            maneuverType = maneuver.type,
            maneuverModifier = maneuver.modifier,
            roadClasses = maneuver.classes
        )
    }
}
