package hu.toonnav.map

/**
 * One navigation run.
 *
 * The map activity searches, previews, and draws the line. While a trip is
 * active, or while the Filament screen is in front, only that screen may
 * listen for navigation GPS, advance the route, reroute, or speak.
 */
object NavigationOwner {
    enum class Seat { MAP, RACE }

    var seat: Seat = Seat.MAP
        private set
    var aliveRaces: Int = 0
        private set
    private var mapOverview = false

    fun claimRace() {
        seat = Seat.RACE
    }

    fun releaseRace(returningToMap: Boolean) {
        seat = Seat.MAP
        if (returningToMap) mapOverview = true
    }

    fun mapMayListen(): Boolean = seat == Seat.MAP

    /** An active trip is never guided from the map, even if the 3D screen is paused. */
    fun mapMayGuide(tripActive: Boolean): Boolean = seat == Seat.MAP && !tripActive

    fun speaker(tripActive: Boolean): Seat {
        return if (tripActive || seat == Seat.RACE) Seat.RACE else Seat.MAP
    }

    fun raceCreated() {
        aliveRaces += 1
    }

    fun raceDestroyed() {
        aliveRaces = (aliveRaces - 1).coerceAtLeast(0)
        if (aliveRaces == 0 && seat == Seat.RACE) seat = Seat.MAP
    }

    fun consumeMapOverview(): Boolean {
        val show = mapOverview
        mapOverview = false
        return show
    }

    fun resetForTest() {
        seat = Seat.MAP
        aliveRaces = 0
        mapOverview = false
        NavigationVoice.clearRetained()
    }
}

enum class RaceRouteSource { LIVE, SAMPLE, MISSING }

object NavigationRoute {
    fun choose(navigation: Boolean, livePoints: Int, hasPlan: Boolean): RaceRouteSource {
        val live = livePoints >= 2 && hasPlan
        if (navigation) return if (live) RaceRouteSource.LIVE else RaceRouteSource.MISSING
        return if (live) RaceRouteSource.LIVE else RaceRouteSource.SAMPLE
    }

    enum class GarageAction { RETURN, OPEN_LIVE, OPEN_SAMPLE }

    fun garageAction(aliveRaces: Int, livePoints: Int, hasPlan: Boolean): GarageAction {
        if (aliveRaces > 0) return GarageAction.RETURN
        return if (livePoints >= 2 && hasPlan) GarageAction.OPEN_LIVE else GarageAction.OPEN_SAMPLE
    }
}

object NavigationVoice {
    var retainedKey: String = ""
        private set

    fun announceStart(navigation: Boolean, requested: Boolean, live: Boolean): Boolean {
        return navigation && requested && live
    }

    fun mapMaySpeak(tripActive: Boolean): Boolean {
        return NavigationOwner.speaker(tripActive) == NavigationOwner.Seat.MAP
    }

    fun retain(key: String) {
        retainedKey = key
    }

    fun clearRetained() {
        retainedKey = ""
    }
}
