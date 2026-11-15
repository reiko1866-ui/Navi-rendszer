package hu.toonnav.map

/**
 * Road class comes only from an explicit maneuver or a road-class token.
 * Speed is intentionally not an input. An unknown road stays unknown.
 */
enum class RoadKind {
    UNKNOWN,
    MOTORWAY,
    EXPRESSWAY,
    MAIN,
    SECONDARY,
    RESIDENTIAL,
    ROUNDABOUT,
    RAMP
}

object RoadClassifier {
    fun classify(maneuverType: String, classes: List<String>, mapClass: String? = null): RoadKind {
        val type = maneuverType.lowercase()
        if (type == "roundabout" || type == "rotary" || type == "exit roundabout" || type == "exit rotary") {
            return RoadKind.ROUNDABOUT
        }
        if (type == "on ramp" || type == "off ramp") return RoadKind.RAMP
        val tokens = ArrayList<String>(classes.size + 1)
        for (name in classes) {
            val cleaned = name.trim().lowercase()
            if (cleaned.isNotEmpty()) tokens.add(cleaned)
        }
        val mapped = mapClass?.trim()?.lowercase().orEmpty()
        if (mapped.isNotEmpty()) tokens.add(mapped)
        return when {
            tokens.any { it == "motorway_link" || it == "trunk_link" } -> RoadKind.RAMP
            tokens.any { it == "motorway" } -> RoadKind.MOTORWAY
            tokens.any { it == "trunk" } -> RoadKind.EXPRESSWAY
            tokens.any { it == "primary" } -> RoadKind.MAIN
            tokens.any { it == "secondary" } -> RoadKind.SECONDARY
            tokens.any { it == "tertiary" || it == "residential" || it == "living_street" || it == "unclassified" || it == "service" } -> RoadKind.RESIDENTIAL
            else -> RoadKind.UNKNOWN
        }
    }

    fun label(kind: RoadKind): String {
        return when (kind) {
            RoadKind.MOTORWAY -> "Autópálya"
            RoadKind.EXPRESSWAY -> "Autóút"
            RoadKind.MAIN -> "Főút"
            RoadKind.SECONDARY -> "Mellékút"
            RoadKind.RESIDENTIAL -> "Lakóutca"
            RoadKind.ROUNDABOUT -> "Körforgalom"
            RoadKind.RAMP -> "Csomópont"
            RoadKind.UNKNOWN -> ""
        }
    }
}

object RoadGuidance {
    fun leadMeters(kind: RoadKind, maneuverType: String): Double {
        val type = maneuverType.lowercase()
        if (type == "off ramp" || type == "on ramp" || kind == RoadKind.MOTORWAY || kind == RoadKind.EXPRESSWAY) {
            return 1000.0
        }
        if (kind == RoadKind.ROUNDABOUT || kind == RoadKind.RESIDENTIAL) return 250.0
        return 400.0
    }

    fun shown(kind: RoadKind, offRoute: Boolean, held: Boolean): RoadKind {
        if (offRoute || held) return RoadKind.UNKNOWN
        return kind
    }
}
