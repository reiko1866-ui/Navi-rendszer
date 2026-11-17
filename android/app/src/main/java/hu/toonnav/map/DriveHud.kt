package hu.toonnav.map

import android.animation.ObjectAnimator
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class DriveHud(private val activity: android.content.Context) {
    private lateinit var maneuverCard: LinearLayout
    private lateinit var maneuverArrow: ImageView
    private lateinit var stepDistance: TextView
    private lateinit var maneuverText: TextView
    private lateinit var leftColumn: View
    private lateinit var speedPill: LinearLayout
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
    private lateinit var statusText: TextView
    private lateinit var searchCard: View
    private lateinit var gpsLamp: View
    private lateinit var gpsCaption: TextView
    private lateinit var navState: TextView
    private lateinit var cockpitToggle: TextView
    private lateinit var phrases: StatusPhrases
    private lateinit var maneuverPlate: CockpitPanelDrawable
    private lateinit var drivePlate: CockpitPanelDrawable
    private lateinit var searchPlate: CockpitPanelDrawable
    private lateinit var previewPlate: CockpitPanelDrawable
    private lateinit var barPlate: CockpitPanelDrawable
    private lateinit var speedRing: SpeedRingDrawable

    private var guidanceShown = false
    private var routeActive = false
    private var routePrepared = false
    private var night = false
    private var latestNotices: List<Notice> = emptyList()
    private var lastManeuver = ""
    private var lastRotation = Float.NaN
    private var ringAnim: ObjectAnimator? = null

    fun bind(root: View) {
        maneuverCard = root.findViewById(R.id.maneuverCard)
        maneuverArrow = root.findViewById(R.id.maneuverArrow)
        stepDistance = root.findViewById(R.id.stepDistance)
        maneuverText = root.findViewById(R.id.maneuverText)
        leftColumn = root.findViewById(R.id.leftColumn)
        speedPill = root.findViewById(R.id.speedPill)
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
        statusText = root.findViewById(R.id.statusText)
        searchCard = root.findViewById(R.id.searchCard)
        gpsLamp = root.findViewById(R.id.gpsLamp)
        gpsCaption = root.findViewById(R.id.gpsCaption)
        navState = root.findViewById(R.id.navState)
        cockpitToggle = root.findViewById(R.id.cockpitToggle)
        phrases = StatusPhrases(
            gpsSearch = activity.getString(R.string.status_gps_search),
            gpsLost = activity.getString(R.string.status_gps),
            gpsOff = activity.getString(R.string.status_gps_off),
            gpsPoor = activity.getString(R.string.status_gps_poor),
            permission = activity.getString(R.string.status_permission),
            precise = activity.getString(R.string.status_precise),
            offRoute = activity.getString(R.string.status_off_route),
            arrived = activity.getString(R.string.status_arrived),
            planning = activity.getString(R.string.status_planning),
            follow = activity.getString(R.string.status_follow)
        )
        maneuverPlate = CockpitPanelDrawable()
        drivePlate = CockpitPanelDrawable()
        searchPlate = CockpitPanelDrawable()
        previewPlate = CockpitPanelDrawable()
        barPlate = CockpitPanelDrawable()
        speedRing = SpeedRingDrawable()
        maneuverCard.background = maneuverPlate
        driveCard.background = drivePlate
        searchCard.background = searchPlate
        root.findViewById<View>(R.id.previewCard).background = previewPlate
        root.findViewById<View>(R.id.cockpitBar).background = barPlate
        speedPill.background = speedRing
        maneuverCard.clipToOutline = true
        driveCard.clipToOutline = true
        searchCard.clipToOutline = true
        searchCard.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> paintStatus() }
        statusText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                statusText.post { paintStatus() }
            }
        })
        paintStatus()
    }

    fun showGuidance(show: Boolean, routeActive: Boolean = show) {
        val opening = show && !guidanceShown
        guidanceShown = show
        this.routeActive = routeActive
        val visibility = if (show) View.VISIBLE else View.GONE
        maneuverCard.visibility = visibility
        driveCard.visibility = visibility
        leftColumn.visibility = visibility
        cameraTools.visibility = View.VISIBLE
        placeColumns(show)
        if (opening) {
            maneuverCard.alpha = 0.86f
            maneuverCard.animate().alpha(1f).setDuration(180).start()
        }
        paintStatus()
    }

    /** `guiding` is an active trip. `prepared` is a calculated plan that has not started. */
    fun noteRoute(guiding: Boolean, prepared: Boolean) {
        val active = guiding && prepared
        val planned = prepared && !guiding
        if (routeActive == active && routePrepared == planned) return
        routeActive = active
        routePrepared = planned
        paintStatus()
    }

    fun showFold(available: Boolean, collapsed: Boolean) {
        if (!::cockpitToggle.isInitialized) return
        cockpitToggle.visibility = if (available) View.VISIBLE else View.GONE
        val label = if (collapsed) R.string.cockpit_unfold else R.string.cockpit_fold
        cockpitToggle.setText(label)
        cockpitToggle.contentDescription = activity.getString(label)
    }

    fun renderManeuver(distance: String, road: String, rotation: Float) {
        val identityChanged = lastManeuver.isNotEmpty() && (road != lastManeuver || rotation != lastRotation)
        val distanceChanged = stepDistance.text?.toString().orEmpty().isNotEmpty() && stepDistance.text.toString() != distance
        stepDistance.text = distance
        maneuverText.text = road
        maneuverArrow.rotation = rotation
        if (distanceChanged) soften(stepDistance)
        if (identityChanged) {
            maneuverArrow.animate().cancel()
            maneuverArrow.scaleX = 1f
            maneuverArrow.scaleY = 1f
            maneuverArrow.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).withEndAction {
                maneuverArrow.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }.start()
        }
        lastManeuver = road
        lastRotation = rotation
    }

    fun renderTrip(road: String, remaining: String, arrival: String) {
        val nextRoad = road.ifBlank { "—" }
        if (roadNow.text?.toString() != nextRoad && roadNow.text?.isNotEmpty() == true) soften(roadNow)
        if (remainText.text?.toString() != remaining && remainText.text?.isNotEmpty() == true) soften(remainText)
        if (etaText.text?.toString() != arrival && etaText.text?.isNotEmpty() == true) soften(etaText)
        roadNow.text = nextRoad
        remainText.text = remaining
        etaText.text = arrival
    }

    fun renderSpeed(kmh: Int?) {
        val next = kmh?.toString() ?: "—"
        if (speedValue.text?.toString() != next && speedValue.text?.toString() != "—") soften(speedValue)
        speedValue.text = next
        val target = CockpitGauge.level(kmh)
        ringAnim?.cancel()
        ringAnim = ObjectAnimator.ofInt(speedRing, "level", speedRing.level, target).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    fun renderNotices(notices: List<Notice>) {
        latestNotices = notices
        drawNotices()
    }

    private fun drawNotices() {
        val notices = latestNotices
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
            chip.setTextColor(ContextCompat.getColor(activity, R.color.cockpit_ink))
            chip.textSize = 14f
            val pad = (10 * activity.resources.displayMetrics.density).toInt()
            chip.setPadding(pad, pad, pad, pad)
            chip.background = CockpitPanelDrawable().also { it.night = night }
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
        val nightChanged = this.night != night
        this.night = night
        maneuverPlate.night = night
        drivePlate.night = night
        searchPlate.night = night
        previewPlate.night = night
        barPlate.night = night
        speedRing.night = night
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
        if (nightChanged) drawNotices()
        paintStatus()
    }

    private fun paintStatus() {
        if (!::statusText.isInitialized || !::phrases.isInitialized) return
        val message = statusText.text?.toString().orEmpty()
        val shown = statusText.visibility == View.VISIBLE && message.isNotBlank()
        val state = CockpitStatus.classify(
            message,
            shown,
            routeActive,
            searchCard.visibility == View.VISIBLE,
            phrases,
            routePrepared
        )
        val lampColor = when (state.lamp) {
            GpsLamp.OK -> R.color.cockpit_ok
            GpsLamp.SEARCH -> R.color.cockpit_caution
            GpsLamp.BAD -> R.color.cockpit_alert
            GpsLamp.UNKNOWN -> R.color.cockpit_muted
        }
        val lamp = GradientDrawable()
        lamp.shape = GradientDrawable.OVAL
        lamp.setColor(ContextCompat.getColor(activity, lampColor))
        gpsLamp.background = lamp
        val gps = when (state.lamp) {
            GpsLamp.OK -> R.string.hud_gps_ok
            GpsLamp.SEARCH -> R.string.hud_gps_search
            GpsLamp.BAD -> R.string.hud_gps_bad
            GpsLamp.UNKNOWN -> R.string.hud_gps_unknown
        }
        val nav = when (state.nav) {
            NavFlag.IDLE -> R.string.hud_nav_idle
            NavFlag.SEARCH -> R.string.hud_nav_search
            NavFlag.READY -> R.string.hud_nav_ready
            NavFlag.ACTIVE -> R.string.hud_nav_active
            NavFlag.PLANNING -> R.string.hud_nav_plan
            NavFlag.ARRIVED -> R.string.hud_nav_arrived
            NavFlag.OFF_ROUTE -> R.string.hud_nav_off
            NavFlag.GPS_SEARCH -> R.string.hud_gps_search
            NavFlag.GPS_BAD -> R.string.hud_gps_bad
        }
        gpsCaption.setText(gps)
        navState.setText(nav)
        gpsLamp.contentDescription = activity.getString(gps)
        navState.contentDescription = activity.getString(nav)
        val navColor = when (state.nav) {
            NavFlag.ACTIVE, NavFlag.ARRIVED, NavFlag.READY -> R.color.cockpit_accent_soft
            NavFlag.PLANNING, NavFlag.GPS_SEARCH -> R.color.cockpit_caution
            NavFlag.OFF_ROUTE, NavFlag.GPS_BAD -> R.color.cockpit_alert
            else -> R.color.cockpit_ink
        }
        navState.setTextColor(ContextCompat.getColor(activity, navColor))
        if (!shown) return
        val fill: Int
        val edge: Int
        val ink: Int
        when (state.plate) {
            StatusPlate.ALERT -> {
                fill = 0xF03A1814.toInt()
                edge = ContextCompat.getColor(activity, R.color.cockpit_alert)
                ink = 0xFFFFE8E2.toInt()
            }
            StatusPlate.CAUTION -> {
                fill = 0xF02E2418.toInt()
                edge = ContextCompat.getColor(activity, R.color.cockpit_caution)
                ink = 0xFFFFE6C4.toInt()
            }
            StatusPlate.LIVE -> {
                fill = 0xF2181B20.toInt()
                edge = ContextCompat.getColor(activity, R.color.cockpit_accent)
                ink = ContextCompat.getColor(activity, R.color.cockpit_ink)
            }
            StatusPlate.CALM, StatusPlate.HIDDEN -> {
                fill = 0xE6181B20.toInt()
                edge = 0x33FFFFFF
                ink = ContextCompat.getColor(activity, R.color.cockpit_ink)
            }
        }
        val plate = GradientDrawable()
        plate.cornerRadius = 16f * activity.resources.displayMetrics.density
        plate.setColor(fill)
        plate.setStroke((activity.resources.displayMetrics.density * 1.5f).toInt().coerceAtLeast(1), edge)
        statusText.background = plate
        statusText.setTextColor(ink)
    }

    private fun placeColumns(guiding: Boolean) {
        val top = activity.resources.getDimensionPixelSize(if (guiding) R.dimen.tools_top else R.dimen.tools_top_idle)
        for (view in listOf(leftColumn, cameraTools)) {
            val params = view.layoutParams as? FrameLayout.LayoutParams ?: continue
            params.topMargin = top
            view.layoutParams = params
        }
    }

    private fun soften(view: View) {
        view.animate().cancel()
        view.alpha = 0.86f
        view.animate().alpha(1f).setDuration(140).start()
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
