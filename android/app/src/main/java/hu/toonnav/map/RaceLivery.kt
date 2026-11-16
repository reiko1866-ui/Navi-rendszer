package hu.toonnav.map

/**
 * The five garage cars share one formula mesh. Only the painted materials change.
 */
object RaceLivery {
    fun slot(materialName: String): String? {
        val name = materialName.lowercase()
        return when {
            name.startsWith("paint_primary") -> "body"
            name.startsWith("paint_accent") -> "accent"
            name.startsWith("paint_secondary") -> "wing"
            else -> null
        }
    }

    fun color(slot: String, car: GarageFleet.Car): Int {
        return when (slot) {
            "body" -> car.body
            "accent" -> car.accent
            "wing" -> car.wing
            else -> car.body
        }
    }
}
