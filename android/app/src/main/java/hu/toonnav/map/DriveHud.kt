package hu.toonnav.map

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class DriveHud(private val activity: Context) {
    private lateinit var maneuverCard: LinearLayout
    private lateinit var maneuverArrow: ImageView
    private lateinit var stepDistance: TextView
    private lateinit var maneuverText: TextView
    private lateinit var leftColumn: View
    private lateinit var speedValue: TextView
    private lateinit var speedLimitSign: TextView
    private lateinit var noticeStrip: LinearLayout
    private lateinit var cameraTools: ScrollView
    private lateinit var voiceButton: ImageButton
    private lateinit var orientationButton: TextView
    private lateinit var themeButton: TextView
    private lateinit var overviewButton: ImageButton
    private lateinit var recenterButton: ImageButton
    private lateinit var driveCard: LinearLayout
    private lateinit var roadNow: TextView
    private lateinit var remainText: TextView
    private lateinit var etaText: TextView

    fun bind(root: View) {
        maneuverCard = root.findViewById(R.id.maneuverCard)
        maneuverArrow = root.findViewById(R.id.maneuverArrow)
        stepDistance = root.findViewById(R.id.stepDistance)
        maneuverText = root.findViewById(R.id.maneuverText)
        leftColumn = root.findViewById<View>(R.id.leftColumn)
        speedValue = root.findViewById(R.id.speedValue)
        speedLimitSign = root.findViewById(R.id.speedLimitSign)
        noticeStrip = root.findViewById(R.id.noticeStrip)
        cameraTools = root.findViewById(R.id.cameraTools)
        voiceButton = root.findViewById(R.id.voiceButton)
        orientationButton = root.findViewById(R.id.orientationButton)
        themeButton = root.findViewById(R.id.themeButton)
        overviewButton = root.findViewById(R.id.overviewButton)
        recenterButton = root.findViewById(R.id.recenterButton)
        driveCard = root.findViewById(R.id.driveCard)
        roadNow = root.findViewById(R.id.roadNow)
        remainText = root.findViewById(R.id.remainText)
        etaText = root.findViewById(R.id.etaText)
    }

    fun showGuidance(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        maneuverCard.visibility = visibility
        leftColumn.visibility = visibility
        cameraTools.visibility = visibility
        driveCard.visibility = visibility
    }

    fun renderManeuver(distance: String, road: String, rotation: Float) {
        stepDistance.text = distance
        maneuverText.text = road
        maneuverArrow.rotation = rotation
    }

    fun renderTrip(road: String, remaining: String, arrival: String) {
        roadNow.text = road.ifBlank { "—" }
        remainText.text = remaining
        etaText.text = arrival
    }

    fun renderSpeed(kmh: Int?) {
        speedValue.text = kmh?.toString() ?: "—"
    }

    fun renderNotices(notices: List<Notice>) {
        val limit = notices.firstOrNull { it.kind == NoticeKind.SPEED_LIMIT && it.limitKmh != null }
        if (limit == null) {
            speedLimitSign.visibility = View.GONE
            speedLimitSign.text = ""
            speedLimitSign.contentDescription = null
        } else {
            speedLimitSign.visibility = View.VISIBLE
            speedLimitSign.text = limit.limitKmh.toString()
            speedLimitSign.contentDescription = activity.getString(R.string.limit_desc) + " " + limit.limitKmh
        }
        noticeStrip.removeAllViews()
        for (notice in notices) {
            if (notice.kind == NoticeKind.SPEED_LIMIT) continue
            val chip = TextView(activity)
            val title = titleFor(notice.label)
            val distance = notice.distanceMeters?.let { " · " + ManeuverEvents.formatDistance(it) }.orEmpty()
            chip.text = title + distance
            chip.setTextColor(ContextCompat.getColor(activity, R.color.nav_ink))
            chip.textSize = 13f
            val pad = (10 * activity.resources.displayMetrics.density).toInt()
            chip.setPadding(pad, pad, pad, pad)
            chip.setBackgroundResource(R.drawable.bg_card)
            chip.contentDescription = title + ", " + notice.source
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (6 * activity.resources.displayMetrics.density).toInt()
            noticeStrip.addView(chip, params)
        }
    }

    fun renderControls(
        voiceOn: Boolean,
        northUp: Boolean,
        night: Boolean,
        overview: Boolean,
        bearing: Double?,
        recenterNeeded: Boolean
    ) {
        voiceButton.isSelected = voiceOn
        voiceButton.setImageResource(if (voiceOn) R.drawable.ic_voice else R.drawable.ic_voice_off)
        voiceButton.contentDescription = activity.getString(if (voiceOn) R.string.voice_on else R.string.voice_off)
        orientationButton.isSelected = northUp
        orientationButton.text = if (northUp) "É" else GpsSpeed.compassLetter(bearing)
        orientationButton.contentDescription = activity.getString(if (northUp) R.string.north_up else R.string.course_up)
        themeButton.isSelected = night
        themeButton.text = if (night) "☾" else "☀"
        themeButton.contentDescription = activity.getString(if (night) R.string.night_mode else R.string.day_mode)
        overviewButton.isSelected = overview
        overviewButton.contentDescription = activity.getString(if (overview) R.string.follow_view else R.string.overview)
        recenterButton.isSelected = recenterNeeded
    }

    private fun titleFor(label: String): String {
        val id = when (label) {
            "roadwork" -> R.string.notice_roadwork
            "closure" -> R.string.notice_closure
            "accident" -> R.string.notice_accident
            "hazard" -> R.string.notice_hazard
            "weather" -> R.string.notice_weather
            "lane" -> R.string.notice_lane
            "railway" -> R.string.notice_railway
            else -> R.string.notice_other
        }
        return activity.getString(id)
    }
}
