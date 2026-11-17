package hu.toonnav.map

enum class GpsLamp { UNKNOWN, OK, SEARCH, BAD }

enum class NavFlag { IDLE, SEARCH, READY, ACTIVE, PLANNING, ARRIVED, OFF_ROUTE, GPS_SEARCH, GPS_BAD }

enum class StatusPlate { HIDDEN, CALM, LIVE, CAUTION, ALERT }

data class StatusPhrases(
    val gpsSearch: String,
    val gpsLost: String,
    val gpsOff: String,
    val gpsPoor: String,
    val permission: String,
    val precise: String,
    val offRoute: String,
    val arrived: String,
    val planning: String,
    val follow: String
)

data class CockpitState(
    val lamp: GpsLamp,
    val nav: NavFlag,
    val plate: StatusPlate
)

object CockpitGauge {
    const val scaleKmh = 220

    fun level(kmh: Int?): Int {
        if (kmh == null || kmh < 0) return 0
        return (kmh.coerceAtMost(scaleKmh) * 10_000) / scaleKmh
    }
}

object CockpitStatus {
    private val fixLine = Regex("^(-?\\d+\\.\\d+), (-?\\d+\\.\\d+)( · \\d+ km/h)?$")

    fun classify(
        message: String,
        shown: Boolean,
        guiding: Boolean,
        searchOpen: Boolean,
        phrases: StatusPhrases,
        planned: Boolean = false
    ): CockpitState {
        val text = message.trim()
        val route = routeNav(guiding, searchOpen, planned)
        if (!shown || text.isEmpty()) {
            return CockpitState(GpsLamp.UNKNOWN, route, StatusPlate.HIDDEN)
        }
        return when (text) {
            phrases.gpsSearch -> CockpitState(GpsLamp.SEARCH, route, StatusPlate.CAUTION)
            phrases.gpsLost, phrases.gpsOff, phrases.permission, phrases.precise ->
                CockpitState(GpsLamp.BAD, route, StatusPlate.ALERT)
            phrases.gpsPoor -> CockpitState(GpsLamp.SEARCH, route, StatusPlate.CAUTION)
            phrases.offRoute -> CockpitState(GpsLamp.OK, NavFlag.OFF_ROUTE, StatusPlate.ALERT)
            phrases.arrived -> CockpitState(GpsLamp.OK, NavFlag.ARRIVED, StatusPlate.LIVE)
            phrases.planning -> CockpitState(GpsLamp.UNKNOWN, NavFlag.PLANNING, StatusPlate.CAUTION)
            phrases.follow -> CockpitState(GpsLamp.OK, NavFlag.ACTIVE, StatusPlate.LIVE)
            else -> if (fixLine.matches(text)) {
                CockpitState(GpsLamp.OK, route, StatusPlate.CALM)
            } else {
                CockpitState(GpsLamp.UNKNOWN, route, StatusPlate.CAUTION)
            }
        }
    }

    /** GPS lamp and route caption are separate. A fix does not invent a route, and a route does not invent a fix. */
    private fun routeNav(guiding: Boolean, searchOpen: Boolean, planned: Boolean): NavFlag {
        return when {
            guiding -> NavFlag.ACTIVE
            planned -> NavFlag.READY
            searchOpen -> NavFlag.SEARCH
            else -> NavFlag.IDLE
        }
    }
}

/**
 * The map preview is drawn only from the current plan. Length and time are the
 * values the router returned, and the card disappears when that plan is gone.
 */
object RouteCaption {
    fun preview(distanceMeters: Double, durationSeconds: Double?): String {
        val duration = durationSeconds?.let { " · " + ManeuverEvents.formatDuration(it) }.orEmpty()
        return ManeuverEvents.formatDistance(distanceMeters) + duration
    }

    fun previewVisible(pointCount: Int, guiding: Boolean, arrived: Boolean): Boolean {
        return pointCount >= 2 && !guiding && !arrived
    }
}
