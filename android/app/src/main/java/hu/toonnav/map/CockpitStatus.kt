package hu.toonnav.map

enum class GpsLamp { UNKNOWN, OK, SEARCH, BAD }

enum class NavFlag { IDLE, SEARCH, ACTIVE, PLANNING, ARRIVED, OFF_ROUTE, GPS_SEARCH, GPS_BAD }

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

    fun classify(message: String, shown: Boolean, guiding: Boolean, searchOpen: Boolean, phrases: StatusPhrases): CockpitState {
        val text = message.trim()
        if (!shown || text.isEmpty()) {
            return CockpitState(GpsLamp.UNKNOWN, idleNav(guiding, searchOpen), StatusPlate.HIDDEN)
        }
        return when (text) {
            phrases.gpsSearch -> CockpitState(GpsLamp.SEARCH, NavFlag.GPS_SEARCH, StatusPlate.CAUTION)
            phrases.gpsLost, phrases.gpsOff, phrases.permission, phrases.precise ->
                CockpitState(GpsLamp.BAD, NavFlag.GPS_BAD, StatusPlate.ALERT)
            phrases.gpsPoor -> CockpitState(GpsLamp.SEARCH, NavFlag.GPS_BAD, StatusPlate.CAUTION)
            phrases.offRoute -> CockpitState(GpsLamp.OK, NavFlag.OFF_ROUTE, StatusPlate.ALERT)
            phrases.arrived -> CockpitState(GpsLamp.OK, NavFlag.ARRIVED, StatusPlate.LIVE)
            phrases.planning -> CockpitState(GpsLamp.UNKNOWN, NavFlag.PLANNING, StatusPlate.CAUTION)
            phrases.follow -> CockpitState(GpsLamp.OK, NavFlag.ACTIVE, StatusPlate.LIVE)
            else -> if (fixLine.matches(text)) {
                CockpitState(GpsLamp.OK, idleNav(guiding, searchOpen), StatusPlate.CALM)
            } else {
                CockpitState(GpsLamp.UNKNOWN, if (guiding) NavFlag.ACTIVE else NavFlag.SEARCH, StatusPlate.CAUTION)
            }
        }
    }

    private fun idleNav(guiding: Boolean, searchOpen: Boolean): NavFlag {
        return when {
            guiding -> NavFlag.ACTIVE
            searchOpen -> NavFlag.SEARCH
            else -> NavFlag.IDLE
        }
    }
}
