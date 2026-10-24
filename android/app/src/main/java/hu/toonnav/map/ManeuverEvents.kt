package hu.toonnav.map

import java.util.Locale

object ManeuverEvents {
    val working = setOf(
        "indulas",
        "utvonal_kesz",
        "egyenes",
        "balra",
        "jobbra",
        "enyhen_balra",
        "enyhen_jobbra",
        "elesen_balra",
        "elesen_jobbra",
        "vissza",
        "korforgalom",
        "korforgalom_kijarat",
        "folytatas",
        "ujratervezes",
        "rossz_irany",
        "gps_hiany",
        "cel",
        "masik_folytatas"
    )

    fun bannerRotation(modifier: String?): Float {
        return when (modifier ?: "") {
            "sharp left" -> -135f
            "left" -> -90f
            "slight left" -> -35f
            "slight right" -> 35f
            "right" -> 90f
            "sharp right" -> 135f
            "uturn" -> 180f
            else -> 0f
        }
    }

    fun eventId(type: String?, modifier: String?): String {
        val kind = type ?: ""
        if (kind == "arrive") return "cel"
        if (kind == "exit roundabout" || kind == "exit rotary") return "korforgalom_kijarat"
        if (kind == "roundabout" || kind == "rotary") return "korforgalom"
        return when (modifier ?: "") {
            "sharp left" -> "elesen_balra"
            "left" -> "balra"
            "slight left" -> "enyhen_balra"
            "straight" -> "egyenes"
            "slight right" -> "enyhen_jobbra"
            "right" -> "jobbra"
            "sharp right" -> "elesen_jobbra"
            "uturn" -> "vissza"
            else -> ""
        }
    }

    fun canPlay(state: String?, eventId: String?, recognition: String?): Boolean {
        if (state != "mentve") return false
        if (eventId.isNullOrBlank() || eventId == "nincs" || eventId == "folytatas" || eventId == "masik_folytatas") return false
        if (recognition != null && recognition != "mukodik") return false
        if (recognition == null && eventId !in working) return false
        return true
    }

    fun shouldSpeak(kind: String, meters: Double?, lastKey: String, key: String): Boolean {
        if (key.isBlank() || key == lastKey) return false
        if (kind == "reroute" || kind == "indulas" || kind == "utvonal_kesz" || kind == "gps" || kind == "rossz_irany") return true
        if (meters == null || meters > 400.0 || meters < 20.0) return false
        return true
    }

    fun formatDistance(meters: Double): String {
        if (!meters.isFinite() || meters < 0) return "—"
        if (meters >= 950.0) {
            return String.format(Locale.US, "%.1f km", meters / 1000.0).replace('.', ',')
        }
        return "${meters.toInt()} m"
    }

    fun formatDuration(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0) return "—"
        val whole = seconds.toInt()
        if (whole < 60) return "$whole mp"
        val mins = (whole + 30) / 60
        if (mins < 60) return "$mins perc"
        val hours = mins / 60
        val rest = mins % 60
        return "$hours óra $rest perc"
    }
}
