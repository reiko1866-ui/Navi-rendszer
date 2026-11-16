package hu.toonnav.map

/**
 * One OSRM replacement at a time, only after a confirmed departure.
 * A failed request keeps the current route and waits before trying again.
 */
class RerouteSession {
    var inFlight: Boolean = false
        private set
    private var quietUntilMs = 0L
    private var rejected = false

    fun allow(nowMs: Long, confirmed: Boolean, guiding: Boolean): Boolean {
        return guiding && confirmed && !inFlight && nowMs >= quietUntilMs
    }

    fun cooling(nowMs: Long): Boolean = rejected && !inFlight && nowMs < quietUntilMs

    fun started() {
        inFlight = true
        rejected = false
    }

    fun succeeded(nowMs: Long) {
        inFlight = false
        rejected = false
        quietUntilMs = nowMs + 15_000L
    }

    fun failed(nowMs: Long) {
        inFlight = false
        rejected = true
        quietUntilMs = nowMs + 20_000L
    }

    fun reset() {
        inFlight = false
        rejected = false
        quietUntilMs = 0L
    }
}
