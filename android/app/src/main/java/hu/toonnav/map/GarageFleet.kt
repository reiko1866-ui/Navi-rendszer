package hu.toonnav.map

/**
 * One formula mesh and twelve saved liveries. The original eight ids stay.
 * The drawable nose points up, the same way as the map marker, so the current
 * bearing rotation still applies. Wheels stay dark; the body, sides and wings
 * carry the two colors.
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
        val side: Int,
        val accent: Int,
        val frontWing: Int,
        val frontDetail: Int,
        val rearWing: Int,
        val rearDetail: Int,
        val rim: Int,
        val nut: Int
    ) {
        /** The 2D nose uses the front wing as its wing color. */
        val wing: Int get() = frontWing
    }

    val cars = listOf(
        Car(
            "piros", "Piros-fekete", "Piros karosszéria, fekete oldal, fehér hátsó szárny",
            R.drawable.ic_garage_piros, 1,
            body = 0xFFE10600.toInt(), side = 0xFF141414.toInt(), accent = 0xFFF4F4F4.toInt(),
            frontWing = 0xFF141414.toInt(), frontDetail = 0xFFF4F4F4.toInt(),
            rearWing = 0xFFF4F4F4.toInt(), rearDetail = 0xFFE10600.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "kek", "Elektromos kék", "Kék karosszéria, fekete oldal, világoskék hátsó szárny",
            R.drawable.ic_garage_kek, 11,
            body = 0xFF1560FF.toInt(), side = 0xFF0C0C0C.toInt(), accent = 0xFFD6E6FF.toInt(),
            frontWing = 0xFF0C0C0C.toInt(), frontDetail = 0xFF3D8BFF.toInt(),
            rearWing = 0xFF3D8BFF.toInt(), rearDetail = 0xFF0C0C0C.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "zold", "Zöld-arany", "Zöld karosszéria, arany oldal és első szárny",
            R.drawable.ic_garage_zold, 27,
            body = 0xFF0E7A3D.toInt(), side = 0xFFD4AF37.toInt(), accent = 0xFFF3E2A1.toInt(),
            frontWing = 0xFFC9A227.toInt(), frontDetail = 0xFF0E7A3D.toInt(),
            rearWing = 0xFF08351C.toInt(), rearDetail = 0xFFE6C35C.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "lila", "Lila-cián", "Lila karosszéria, cián oldal és hátsó szárny",
            R.drawable.ic_garage_lila, 44,
            body = 0xFF6A2BD6.toInt(), side = 0xFF14081F.toInt(), accent = 0xFF2EE6FF.toInt(),
            frontWing = 0xFF111111.toInt(), frontDetail = 0xFF2EE6FF.toInt(),
            rearWing = 0xFF2EE6FF.toInt(), rearDetail = 0xFF6A2BD6.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "ezust", "Ezüst-kék", "Ezüst karosszéria, kék oldal és szárny",
            R.drawable.ic_garage_ezust, 5,
            body = 0xFFD8DCE4.toInt(), side = 0xFF1E5FE0.toInt(), accent = 0xFFF7F8FA.toInt(),
            frontWing = 0xFF163E8C.toInt(), frontDetail = 0xFFD8DCE4.toInt(),
            rearWing = 0xFF7EB0FF.toInt(), rearDetail = 0xFFF7F8FA.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "fekete", "Fekete-neon", "Fekete karosszéria, neonsárga csík és első szárny",
            R.drawable.ic_garage_fekete, 63,
            body = 0xFF1A1A1A.toInt(), side = 0xFFD6FF00.toInt(), accent = 0xFFF4FFB0.toInt(),
            frontWing = 0xFFD6FF00.toInt(), frontDetail = 0xFF111111.toInt(),
            rearWing = 0xFF111111.toInt(), rearDetail = 0xFFD6FF00.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "narancs", "Narancs-grafit", "Narancs karosszéria, grafit oldal és első szárny",
            R.drawable.ic_garage_narancs, 81,
            body = 0xFFFF6A00.toInt(), side = 0xFF3A3F46.toInt(), accent = 0xFFFFD2A8.toInt(),
            frontWing = 0xFF2E3238.toInt(), frontDetail = 0xFFFF6A00.toInt(),
            rearWing = 0xFFFF8A1F.toInt(), rearDetail = 0xFF2E3238.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "turkiz", "Türkiz", "Türkiz karosszéria, sötétkék oldal és első szárny",
            R.drawable.ic_garage_turkiz, 16,
            body = 0xFF1AD1C4.toInt(), side = 0xFF0C3A6E.toInt(), accent = 0xFFE8FFFC.toInt(),
            frontWing = 0xFF0C3A6E.toInt(), frontDetail = 0xFF1AD1C4.toInt(),
            rearWing = 0xFF7EFFF4.toInt(), rearDetail = 0xFF08284A.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "feherpiros", "Fehér-piros", "Fehér karosszéria, piros oldal és első szárny",
            R.drawable.ic_garage_feherpiros, 7,
            body = 0xFFF4F5F7.toInt(), side = 0xFFE10600.toInt(), accent = 0xFF1A1A1A.toInt(),
            frontWing = 0xFFE10600.toInt(), frontDetail = 0xFFF4F5F7.toInt(),
            rearWing = 0xFF1A1A1A.toInt(), rearDetail = 0xFFE10600.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "feherkek", "Fehér-kék", "Fehér karosszéria, kék oldal és első szárny",
            R.drawable.ic_garage_feherkek, 3,
            body = 0xFFF7F8FA.toInt(), side = 0xFF1B4FBF.toInt(), accent = 0xFF0A1A3A.toInt(),
            frontWing = 0xFF1B4FBF.toInt(), frontDetail = 0xFFF7F8FA.toInt(),
            rearWing = 0xFF0A1A3A.toInt(), rearDetail = 0xFF4C8DFF.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "lime", "Lime-fekete", "Lime karosszéria, fekete oldal, zöld hátsó szárny",
            R.drawable.ic_garage_lime, 21,
            body = 0xFFB6F23A.toInt(), side = 0xFF101010.toInt(), accent = 0xFFE8FF9A.toInt(),
            frontWing = 0xFF101010.toInt(), frontDetail = 0xFFB6F23A.toInt(),
            rearWing = 0xFF7CB518.toInt(), rearDetail = 0xFF101010.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        ),
        Car(
            "arany", "Arany-fekete", "Arany karosszéria, fekete oldal és első szárny",
            R.drawable.ic_garage_arany, 55,
            body = 0xFFD4AF37.toInt(), side = 0xFF141414.toInt(), accent = 0xFFFFE08A.toInt(),
            frontWing = 0xFF141414.toInt(), frontDetail = 0xFFD4AF37.toInt(),
            rearWing = 0xFF8A7018.toInt(), rearDetail = 0xFFFFE08A.toInt(),
            rim = 0xFF16181C.toInt(), nut = 0xFF2A2A2A.toInt()
        )
    )

    private val legacy = mapOf(
        "alap" to "kek",
        "eben" to "fekete",
        "parazs" to "piros",
        "marvany" to "ezust",
        "lang" to "narancs"
    )

    fun car(id: String?): Car? {
        val key = legacy[id] ?: id
        return cars.firstOrNull { it.id == key }
    }

    /** The selected livery, or electric blue when nothing is stored. */
    fun shown(id: String?): Car = car(id) ?: car("kek") ?: cars.first()

    fun clamp(number: Int): Int = number.coerceIn(numberMin, numberMax)

    /** Unselected garage keeps the marker that is already on the map. */
    fun markerRes(carId: String?): Int = car(carId)?.marker ?: R.drawable.ic_race_car
}
