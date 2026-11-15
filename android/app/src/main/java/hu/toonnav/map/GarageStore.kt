package hu.toonnav.map

import android.content.Context

/**
 * Remembers the garage choice. It does not read location or change heading.
 */
class GarageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun carId(): String? = prefs.getString(KEY_CAR, null)?.takeIf { GarageFleet.car(it) != null }

    fun number(): Int? {
        if (!prefs.contains(KEY_NUMBER)) return null
        return GarageFleet.clamp(prefs.getInt(KEY_NUMBER, GarageFleet.numberMin))
    }

    fun markerRes(): Int = GarageFleet.markerRes(carId())

    fun write(carId: String, number: Int) {
        val car = GarageFleet.car(carId) ?: return
        prefs.edit()
            .putString(KEY_CAR, car.id)
            .putInt(KEY_NUMBER, GarageFleet.clamp(number))
            .apply()
    }

    companion object {
        const val PREFS = "toonnav-garage"
        private const val KEY_CAR = "car"
        private const val KEY_NUMBER = "number"
    }
}
