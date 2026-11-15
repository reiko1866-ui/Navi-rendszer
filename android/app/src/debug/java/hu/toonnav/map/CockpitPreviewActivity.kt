package hu.toonnav.map

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos

/**
 * Debug-only screen so the cockpit can be photographed on an emulator.
 * Every distance, maneuver and speed shown here comes from GuidanceSession,
 * ManeuverEvents and GpsSpeed. It is not a launcher screen.
 */
class CockpitPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val scene = intent.getStringExtra("scene") ?: "idle"
        val night = scene == "night" || intent.getBooleanExtra("night", false)
        val host = FrameLayout(this)
        host.setBackgroundColor(if (night) 0xFF17202B.toInt() else 0xFFC9C2B2.toInt())
        host.fitsSystemWindows = true
        setContentView(host)
        layoutInflater.inflate(R.layout.activity_hud, host, true)
        val hud = DriveHud(this)
        hud.bind(host)
        hud.renderControls(true, false, night, false, 90.0, false)
        when (scene) {
            "active", "night" -> showCue(hud, host, cruise(150.0), guiding = true)
            "turn" -> showCue(hud, host, cruise(370.0), guiding = true)
            "gps" -> showMessage(hud, host, getString(R.string.status_gps_search), guiding = false)
            "lost" -> showCue(hud, host, lost(), guiding = true, speed = null, message = getString(R.string.status_gps))
            "offroute" -> showCue(hud, host, departure(), guiding = true, message = getString(R.string.status_off_route))
            else -> showIdle(hud, host)
        }
    }

    private fun showIdle(hud: DriveHud, host: View) {
        host.findViewById<View>(R.id.searchCard).visibility = View.VISIBLE
        hud.showGuidance(false)
        hud.renderSpeed(null)
        hud.renderManeuver("—", "", 0f)
        hud.renderTrip("", "", "--:--")
        val status = host.findViewById<TextView>(R.id.statusText)
        status.text = ""
        status.visibility = View.GONE
    }

    private fun showMessage(hud: DriveHud, host: View, message: String, guiding: Boolean) {
        host.findViewById<View>(R.id.searchCard).visibility = if (guiding) View.GONE else View.VISIBLE
        hud.showGuidance(guiding)
        hud.renderSpeed(null)
        hud.renderManeuver("—", "", 0f)
        hud.renderTrip("", "", "--:--")
        val status = host.findViewById<TextView>(R.id.statusText)
        status.text = message
        status.visibility = View.VISIBLE
    }

    private fun showCue(
        hud: DriveHud,
        host: View,
        sample: Sample,
        guiding: Boolean,
        speed: Int? = GpsSpeed.kmh(10.0, 0L),
        message: String? = null
    ) {
        host.findViewById<View>(R.id.searchCard).visibility = View.GONE
        hud.showGuidance(guiding)
        hud.renderSpeed(speed)
        hud.renderManeuver(
            ManeuverEvents.formatDistance(sample.cue.maneuverDistanceMeters),
            sample.cue.maneuverLabel,
            sample.cue.arrowDegrees
        )
        hud.renderTrip(
            sample.cue.roadName,
            ManeuverEvents.formatDistance(sample.cue.remainingMeters) + " a célig",
            eta(sample)
        )
        val status = host.findViewById<TextView>(R.id.statusText)
        status.text = message ?: GpsReadout.text(sample.point.lat, sample.point.lon, speed).orEmpty()
        status.visibility = if (status.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun eta(sample: Sample): String {
        val offset = NavigationEta.offsetMillis(
            sample.plan.durationSeconds,
            NavigationEta.fraction(sample.cue.remainingMeters, sample.cue.totalMeters)
        ) ?: return "--:--"
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis() + offset
        return String.format(Locale.US, "%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    private fun cruise(meters: Double): Sample {
        val plan = route()
        val session = GuidanceSession()
        session.setPlan(plan)
        val point = east(meters)
        val cue = session.onFix(point.lon, point.lat, 1_000L, 0L, 8.0)!!
        return Sample(plan, point, cue)
    }

    private fun lost(): Sample {
        val plan = route()
        val session = GuidanceSession()
        session.setPlan(plan)
        val point = east(150.0)
        session.onFix(point.lon, point.lat, 1_000L, 0L, 8.0)
        val cue = session.refreshAge(8_001L)!!
        return Sample(plan, point, cue)
    }

    private fun departure(): Sample {
        val plan = route()
        val session = GuidanceSession()
        session.setPlan(plan)
        val point = northOf(east(200.0), 100.0)
        session.onFix(point.lon, point.lat, 0L, 0L, 10.0)
        val cue = session.onFix(point.lon, point.lat, 8_000L, 0L, 10.0)!!
        return Sample(plan, point, cue)
    }

    private fun route(): OsrmRoutePlan {
        val end = east(1000.0)
        return OsrmRoutePlan(
            1000.0,
            180.0,
            listOf(OsrmPoint(lon, lat), end),
            listOf(
                OsrmStep("", 0.0, null, OsrmManeuver("depart", "straight", OsrmPoint(lon, lat), null)),
                OsrmStep("Első", 100.0, null, OsrmManeuver("continue", "straight", east(100.0), null)),
                OsrmStep("Második", 300.0, null, OsrmManeuver("turn", "right", east(400.0), null)),
                OsrmStep("Otthon", 600.0, null, OsrmManeuver("arrive", "", end, null))
            ),
            listOf(OsrmWaypoint("Otthon", end, null))
        )
    }

    private fun east(meters: Double): OsrmPoint {
        val lat0 = Math.toRadians(lat)
        val delta = Math.toDegrees(meters / (GeoMeters.radiusMeters * cos(lat0)))
        return OsrmPoint(lon + delta, lat)
    }

    private fun northOf(origin: OsrmPoint, meters: Double): OsrmPoint {
        val delta = Math.toDegrees(meters / GeoMeters.radiusMeters)
        return OsrmPoint(origin.lon, origin.lat + delta)
    }

    private data class Sample(val plan: OsrmRoutePlan, val point: OsrmPoint, val cue: GuidanceCue)

    companion object {
        private const val lat = 47.0
        private const val lon = 19.0
    }
}
