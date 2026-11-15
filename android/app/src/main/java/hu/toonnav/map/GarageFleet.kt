package hu.toonnav.map

/**
 * Original ToonNav liveries. The drawable nose points up, the same way as the
 * existing map marker, so the current bearing rotation still applies.
 */
object GarageFleet {
    const val numberMin = 1
    const val numberMax = 99

    data class Car(
        val id: String,
        val name: String,
        val livery: String,
        val marker: Int,
        val defaultNumber: Int,
        val body: Int,
        val accent: Int,
        val wing: Int
    )

    val cars = listOf(
        Car("eben", "Ében", "Grafit karosszéria, piros orr", R.drawable.ic_garage_eben, 8, 0xFF2A2E33.toInt(), 0xFFE10600.toInt(), 0xFF141618.toInt()),
        Car("parazs", "Parázs", "Mélyvörös karosszéria, narancs szárny", R.drawable.ic_garage_parazs, 14, 0xFF8E1B1B.toInt(), 0xFFFF6A1A.toInt(), 0xFF3A1210.toInt()),
        Car("marvany", "Márvány", "Elefántcsont karosszéria, fekete szárny", R.drawable.ic_garage_marvany, 21, 0xFFF3EDE2.toInt(), 0xFF161616.toInt(), 0xFF2C2C2C.toInt()),
        Car("lang", "Láng", "Narancs karosszéria, fekete orr", R.drawable.ic_garage_lang, 36, 0xFFE85D04.toInt(), 0xFF111111.toInt(), 0xFF1A1A1A.toInt()),
        Car("alap", "Alap", "Az eredeti ToonNav versenyautó", R.drawable.ic_race_car, 1, 0xFF1F4E8C.toInt(), 0xFFF4F1EC.toInt(), 0xFF0E2A4A.toInt())
    )

    fun car(id: String?): Car? = cars.firstOrNull { it.id == id }

    /** The selected livery, or the original base car when nothing is stored. */
    fun shown(id: String?): Car = car(id) ?: car("alap") ?: cars.first()

    fun clamp(number: Int): Int = number.coerceIn(numberMin, numberMax)

    /** Unselected garage keeps the marker that is already on the map. */
    fun markerRes(carId: String?): Int = car(carId)?.marker ?: R.drawable.ic_race_car
}
