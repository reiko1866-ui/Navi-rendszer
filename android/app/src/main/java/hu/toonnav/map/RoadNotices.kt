package hu.toonnav.map

enum class NoticeKind {
    SPEED_LIMIT,
    SPEED_CAMERA,
    TRAFFIC_LIGHT,
    ROADWORK,
    CLOSURE,
    OTHER
}

data class Notice(
    val id: String,
    val kind: NoticeKind,
    val source: String,
    val distanceMeters: Double?,
    val limitKmh: Int?,
    val validUntilMs: Long?,
    val label: String
)

data class SpeedLimitSample(
    val speed: Int?,
    val unit: String?
)

data class RoadObjectSample(
    val id: String,
    val type: String,
    val subType: String?,
    val closed: Boolean,
    val distanceMeters: Double?,
    val endTimeMs: Long?,
    val provider: String?
)

/**
 * Display-only road notices. Routing never reads this object.
 *
 * Speed limit: source mapbox-location-matcher, fresh GPS fix only, unit known,
 * value 1..200 km/h. Missing, stale, or unknown data stays hidden.
 * Speed camera and traffic light: this SDK has no such road-object or incident
 * type, so both stay hidden. They are never inferred from hazards or alert codes.
 * Roadwork, closure, and other warnings: Mapbox incidents and railway crossings
 * within 1500 m, not past their end time, at most three, nearest first.
 */
object RoadNotices {
    const val aheadMeters = 1_500.0
    const val maxLimitKmh = 200
    const val matcherSource = "mapbox-location-matcher"
    const val incidentSource = "mapbox-route-incident"
    const val railwaySource = "mapbox-road-object"

    fun postedLimit(sample: SpeedLimitSample, nowMs: Long, fixAgeMs: Long): Notice? {
        if (fixAgeMs < 0L || fixAgeMs > GpsSpeed.freshMs) return null
        val kmh = toKmh(sample.speed, sample.unit) ?: return null
        if (kmh !in 1..maxLimitKmh) return null
        return Notice(
            id = "speed-limit",
            kind = NoticeKind.SPEED_LIMIT,
            source = matcherSource,
            distanceMeters = null,
            limitKmh = kmh,
            validUntilMs = nowMs + GpsSpeed.freshMs,
            label = "limit"
        )
    }

    fun fromRoadObjects(samples: List<RoadObjectSample>, nowMs: Long): List<Notice> {
        return samples.mapNotNull { sample ->
            val distance = sample.distanceMeters
            if (distance == null || !distance.isFinite() || distance < 0.0 || distance > aheadMeters) return@mapNotNull null
            val end = sample.endTimeMs
            if (end != null && end < nowMs) return@mapNotNull null
            val kind = kindFor(sample) ?: return@mapNotNull null
            val id = sample.id.trim()
            if (id.isEmpty()) return@mapNotNull null
            Notice(
                id = id,
                kind = kind,
                source = sourceFor(sample),
                distanceMeters = distance,
                limitKmh = null,
                validUntilMs = end,
                label = labelFor(sample)
            )
        }
    }

    fun visible(notices: List<Notice>, nowMs: Long): List<Notice> {
        return notices
            .filter { notice ->
                notice.id.isNotBlank() &&
                    notice.source.isNotBlank() &&
                    (notice.validUntilMs == null || notice.validUntilMs >= nowMs) &&
                    notice.kind != NoticeKind.SPEED_CAMERA &&
                    notice.kind != NoticeKind.TRAFFIC_LIGHT
            }
            .distinctBy { it.id }
            .sortedWith(compareBy<Notice> { it.distanceMeters ?: -1.0 })
            .take(3)
    }

    private fun toKmh(speed: Int?, unit: String?): Int? {
        if (speed == null || speed <= 0) return null
        val kmh = when (unit) {
            "KILOMETERS_PER_HOUR" -> speed.toDouble()
            "MILES_PER_HOUR" -> speed * 1.609344
            "METERS_PER_SECOND" -> speed * 3.6
            else -> return null
        }
        if (!kmh.isFinite()) return null
        return kotlin.math.round(kmh).toInt()
    }

    private fun kindFor(sample: RoadObjectSample): NoticeKind? {
        if (sample.closed || sample.type == "ROAD_CLOSURE") return NoticeKind.CLOSURE
        return when (sample.type) {
            "CONSTRUCTION" -> NoticeKind.ROADWORK
            "ACCIDENT", "ROAD_HAZARD", "WEATHER", "LANE_RESTRICTION", "DISABLED_VEHICLE", "RAILWAY_CROSSING" -> NoticeKind.OTHER
            else -> null
        }
    }

    private fun labelFor(sample: RoadObjectSample): String {
        if (sample.closed || sample.type == "ROAD_CLOSURE") return "closure"
        return when (sample.type) {
            "CONSTRUCTION" -> "roadwork"
            "ACCIDENT" -> "accident"
            "ROAD_HAZARD" -> "hazard"
            "WEATHER" -> "weather"
            "LANE_RESTRICTION" -> "lane"
            "RAILWAY_CROSSING" -> "railway"
            else -> "other"
        }
    }

    private fun sourceFor(sample: RoadObjectSample): String {
        if (sample.type == "RAILWAY_CROSSING") return railwaySource
        val provider = sample.provider?.trim().orEmpty()
        val plain = provider.matches(Regex("[A-Za-z0-9._-]{1,40}"))
        if (!plain || provider.contains("token") || provider.contains("pk.")) return incidentSource
        return "$incidentSource:$provider"
    }
}
