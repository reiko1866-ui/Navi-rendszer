package hu.toonnav.map

import kotlin.math.roundToInt

object GpsSpeed {
    const val freshMs = 4_000L

    fun kmh(metersPerSecond: Double?, ageMs: Long): Int? {
        if (ageMs < 0L || ageMs > freshMs) return null
        val speed = metersPerSecond ?: return null
        if (!speed.isFinite() || speed < 0.0) return null
        return (speed * 3.6).roundToInt()
    }

    fun compassLetter(bearingDegrees: Double?): String {
        val bearing = bearingDegrees ?: return "·"
        if (!bearing.isFinite()) return "·"
        val names = arrayOf("É", "ÉK", "K", "DK", "D", "DNY", "NY", "ÉNY")
        val turns = ((bearing % 360.0) + 360.0) % 360.0
        val index = (turns / 45.0).roundToInt() % 8
        return names[index]
    }
}
