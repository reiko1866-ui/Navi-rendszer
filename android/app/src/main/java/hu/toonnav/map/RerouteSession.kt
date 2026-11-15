package hu.toonnav.map

/**
 * One OSRM replacement at a time, only after a confirmed departure.
 * A failed request keeps the current route and waits before trying again.
 */
class RerouteSession {
    var inFlight: Boolean = false
        private set
    private var quietUntilMs = 0L

    fun allow(nowMs: Long, confirmed: Boolean, guiding: Boolean): Boolean {
        return guiding && confirmed && !inFlight && nowMs >= quietUntilMs
    }

    fun started() {
        inFlight = true
    }

    fun succeeded(nowMs: Long) {
        inFlight = false
        quietUntilMs = nowMs + 15_000L
    }

    fun failed(nowMs: Long) {
        inFlight = false
        quietUntilMs = nowMs + 20_000L
    }

    fun reset() {
        inFlight = false
        quietUntilMs = 0L
    }
}
