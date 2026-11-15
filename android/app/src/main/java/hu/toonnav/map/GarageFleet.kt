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
        val defaultNumber: Int
    )

    val cars = listOf(
        Car("eben", "Ében", "Grafit karosszéria, piros orr", R.drawable.ic_garage_eben, 8),
        Car("parazs", "Parázs", "Mélyvörös karosszéria, narancs szárny", R.drawable.ic_garage_parazs, 14),
        Car("marvany", "Márvány", "Elefántcsont karosszéria, fekete szárny", R.drawable.ic_garage_marvany, 21),
        Car("lang", "Láng", "Narancs karosszéria, fekete orr", R.drawable.ic_garage_lang, 36)
    )

    fun car(id: String?): Car? = cars.firstOrNull { it.id == id }

    fun clamp(number: Int): Int = number.coerceIn(numberMin, numberMax)

    /** Unselected garage keeps the marker that is already on the map. */
    fun markerRes(carId: String?): Int = car(carId)?.marker ?: R.drawable.ic_race_car
}
