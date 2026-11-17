package hu.toonnav.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val mainHandler = android.os.Handler(Looper.getMainLooper())
    private val search = PhotonSearch()
    private val localAddresses = HungaryAddresses(this)
    private val addressSearch = Executors.newSingleThreadExecutor()
    private val routes = OsrmClient()
    private val guidance = GuidanceSession()
    private val voice by lazy { VoiceGuide(assets) }
    private val hud = DriveHud(this)
    private val hudPrefs by lazy { getSharedPreferences("toonnav-hud", MODE_PRIVATE) }

    private lateinit var mapHost: FrameLayout
    private lateinit var pilotLayer: FrameLayout
    private lateinit var pilotCockpit: PilotCockpitView
    private lateinit var hudHost: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var settingsButton: Button
    private lateinit var searchCard: LinearLayout
    private lateinit var toField: EditText
    private lateinit var results: LinearLayout
    private lateinit var previewCard: LinearLayout
    private lateinit var previewMeta: TextView

    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var loadedStyle: Style? = null
    private var locationReady = false

    private var voiceOn = true
    private var nightMode = false
    private var northUp = false
    private var searchOpen = false
    private var pendingRoute = false
    private var hudReady = false
    private var statusError = true
    private var navigationHold = false
    private var lastPlaces = emptyList<Place>()
    private var origin: Place? = null
    private var destination: Place? = null
    private var resultTarget = "to"
    private var searchSeq = 0
    private var routeSeq = 0
    private var guiding = false
    private var driveView = DriveSession()
    private var following = false
    private var overviewShown = false
    private var fineGranted = false
    private var coarseGranted = false
    private var updatesStarted = false
    private var askedLocation = false
    private var openLocationSettings = false
    private var receiverRegistered = false
    private var sawFix = false
    private var centeredOnce = false
    private var lastFixElapsed = 0L
    private var lastLatitude = Double.NaN
    private var lastLongitude = Double.NaN
    private var lastSpeedMps = 0f
    private var lastHasSpeed = false
    private var lastAccuracy: Double? = null
    private var lastBearing: Double? = null
    private var displayedBearing: Double? = null
    private var cockpitGliding = false
    private val reroutes = RerouteSession()
    private var pilotMapOpen = false
    private var appliedDriveRevision = 0
    private var finishDismissed = false
    private var offRouteSpoken = false
    private var pilotNoticeText = ""
    private var tripRemaining = "—"
    private var tripEta = "--:--"
    private var roadKindLabel = ""
    private var stopDialog: android.app.AlertDialog? = null
    private lateinit var finishLayer: FrameLayout
    private lateinit var pilotSpeed: TextView
    private lateinit var pilotManeuver: TextView
    private lateinit var pilotManeuverDistance: TextView
    private lateinit var pilotRemaining: TextView
    private lateinit var pilotEta: TextView
    private lateinit var pilotRoadKind: TextView
    private lateinit var pilotNotice: TextView
    private lateinit var pilotMap: Button
    private lateinit var pilotVoice: Button
    private lateinit var pilotNight: Button
    private var appliedMarker = 0
    private var lastLocation: Location? = null
    private var lastTrust = GpsTrust.LOST
    private var readoutText = ""
    private var pilotVisible = false
    private var raceLaunching = false
    private var handingToRace = false
    private var pilotDistance: Double? = null
    private var pilotInstruction = ""
    private var pilotArrow = 0f
    private var activePlan: OsrmRoutePlan? = null
    private var routeJson: String? = null
    private var traveledMeters = 0.0
    private var routePoints = emptyList<OsrmPoint>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted(Manifest.permission.ACCESS_FINE_LOCATION)
        coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        refreshLocationAccess()
        if (fineGranted || coarseGranted) requestNotificationPermission()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onFix(location)
        }

        override fun onProviderEnabled(provider: String) {
            refreshLocationAccess()
        }

        override fun onProviderDisabled(provider: String) {
            refreshLocationAccess()
        }
    }

    private val providerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLocationAccess()
        }
    }

    private val gpsWatch = object : Runnable {
        override fun run() {
            if (sawFix && ::statusText.isInitialized) refreshProgressAge()
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)
        voiceOn = hudPrefs.getBoolean("voice", true)
        nightMode = hudPrefs.getBoolean("night", false)
        northUp = hudPrefs.getBoolean("north", false)
        voice.enabled = voiceOn
        restoreActiveDrive()
        inflateHud()
        Thread { voice.load() }.start()
        buildMap(savedInstanceState)
        mainHandler.post(gpsWatch)
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
        if (!receiverRegistered) {
            val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(providerReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(providerReceiver, filter)
            }
            receiverRegistered = true
        }
        readLocationGrants()
        if (!fineGranted && !coarseGranted && !askedLocation) {
            askedLocation = true
            requestLocationPermission()
        } else {
            refreshLocationAccess()
        }
    }

    override fun onResume() {
        super.onResume()
        raceLaunching = false
        handingToRace = false
        mapView?.onResume()
        voiceOn = hudPrefs.getBoolean("voice", voiceOn)
        voice.enabled = voiceOn
        if (DriveSessionMemory.abandon) {
            DriveSessionMemory.abandon = false
            DriveSessionMemory.arrivalHandoff = false
            if (guiding) stopGuidance()
        } else if (DriveSessionMemory.arrivalHandoff) {
            DriveSessionMemory.arrivalHandoff = false
            completeArrivalFromRace()
        } else if (guiding) {
            adoptMemoryIfNewer()
        }
        presentDrive()
        if (NavigationOwner.consumeMapOverview() && guiding && routePoints.size >= 2) showOverview()
        else if (driveView.followCamera && !driveView.guiding && !overviewShown) followVehicle()
        val style = loadedStyle ?: return
        val marker = GarageStore(this).markerRes()
        if (marker != appliedMarker) {
            installLocation(style)
            lastLocation?.let { pushLocation(it) }
        }
    }

    override fun onPause() {
        cockpitGliding = false
        mainHandler.removeCallbacks(cockpitGlide)
        stopLocationUpdates()
        mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        stopLocationUpdates()
        if (receiverRegistered) {
            unregisterReceiver(providerReceiver)
            receiverRegistered = false
        }
        mapView?.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        mapView?.onDestroy()
        mapView = null
        map = null
        loadedStyle = null
        locationReady = false
        voice.release()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        inflateHud()
        presentDrive()
        refreshReadout()
    }

    private fun inflateHud() {
        val toText = if (::toField.isInitialized) toField.text?.toString().orEmpty() else ""
        val previewText = if (::previewMeta.isInitialized) previewMeta.text?.toString().orEmpty() else ""
        val status = if (::statusText.isInitialized) statusText.text?.toString().orEmpty() else ""
        val statusVisible = ::statusText.isInitialized && statusText.visibility == View.VISIBLE
        val settingsVisible = ::settingsButton.isInitialized && settingsButton.visibility == View.VISIBLE
        val host = findViewById<FrameLayout>(R.id.hudHost)
        host.removeAllViews()
        layoutInflater.inflate(R.layout.activity_hud, host, true)
        bindViews()
        hud.bind(host)
        hudReady = true
        if (toText.isNotEmpty()) toField.setText(toText)
        if (previewText.isNotEmpty()) previewMeta.text = previewText
        settingsButton.visibility = if (settingsVisible) View.VISIBLE else View.GONE
        wireSearch()
        findViewById<Button>(R.id.closeSearch).setOnClickListener { closeSearch() }
        findViewById<Button>(R.id.startButton).setOnClickListener { startGuidance() }
        findViewById<Button>(R.id.resumeRaceButton).setOnClickListener { openRaceNavigation(announceStart = false) }
        findViewById<Button>(R.id.resumeStopButton).setOnClickListener { confirmStop() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { confirmStop() }
        findViewById<ImageButton>(R.id.searchButton).setOnClickListener { toggleSearch() }
        findViewById<TextView>(R.id.cockpitToggle).setOnClickListener { toggleCockpit() }
        val openGarage = View.OnClickListener {
            startActivity(Intent(this, GarageActivity::class.java))
        }
        findViewById<View>(R.id.racingFlag).setOnClickListener(openGarage)
        findViewById<View>(R.id.racingMark).setOnClickListener(openGarage)
        findViewById<ImageButton>(R.id.voiceButton).setOnClickListener { toggleVoice() }
        findViewById<TextView>(R.id.orientationButton).setOnClickListener { toggleNorth() }
        findViewById<TextView>(R.id.themeButton).setOnClickListener { toggleNight() }
        findViewById<ImageButton>(R.id.overviewButton).setOnClickListener { showOverview() }
        findViewById<ImageButton>(R.id.recenterButton).setOnClickListener { recenter() }
        findViewById<Button>(R.id.pilotStop).setOnClickListener { confirmStop() }
        findViewById<Button>(R.id.pilotSearch).setOnClickListener { toggleSearch() }
        findViewById<Button>(R.id.pilotGarage).setOnClickListener {
            startActivity(Intent(this, GarageActivity::class.java))
        }
        findViewById<Button>(R.id.pilotMap).setOnClickListener { togglePilotMap() }
        findViewById<Button>(R.id.pilotDrive).setOnClickListener {
            if (driveView.guiding) openRaceNavigation(announceStart = false)
        }
        findViewById<Button>(R.id.pilotVoice).setOnClickListener { toggleVoice() }
        findViewById<Button>(R.id.pilotNight).setOnClickListener { toggleNight() }
        findViewById<Button>(R.id.finishMap).setOnClickListener {
            finishDismissed = true
            revealFinish()
        }
        settingsButton.setOnClickListener {
            if (openLocationSettings) openLocationSourceSettings() else openAppSettings()
        }
        restoreHud(status, statusVisible)
    }

    private fun bindViews() {
        mapHost = findViewById(R.id.mapHost)
        pilotLayer = findViewById(R.id.pilotLayer)
        pilotCockpit = findViewById(R.id.pilotCockpit)
        finishLayer = findViewById(R.id.finishLayer)
        pilotSpeed = findViewById(R.id.pilotSpeed)
        pilotManeuver = findViewById(R.id.pilotManeuver)
        pilotManeuverDistance = findViewById(R.id.pilotManeuverDistance)
        pilotRemaining = findViewById(R.id.pilotRemaining)
        pilotEta = findViewById(R.id.pilotEta)
        pilotRoadKind = findViewById(R.id.pilotRoadKind)
        pilotNotice = findViewById(R.id.pilotNotice)
        pilotMap = findViewById(R.id.pilotMap)
        pilotVoice = findViewById(R.id.pilotVoice)
        pilotNight = findViewById(R.id.pilotNight)
        hudHost = findViewById(R.id.hudHost)
        statusText = findViewById(R.id.statusText)
        settingsButton = findViewById(R.id.settingsButton)
        searchCard = findViewById(R.id.searchCard)
        toField = findViewById(R.id.toField)
        results = findViewById(R.id.results)
        previewCard = findViewById(R.id.previewCard)
        previewMeta = findViewById(R.id.previewMeta)
    }

    private fun buildMap(savedInstanceState: Bundle?) {
        val view = MapView(this)
        mapHost.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        mapView = view
        view.onCreate(savedInstanceState)
        view.addOnDidFailLoadingMapListener { showStatus(getString(R.string.status_map)) }
        view.getMapAsync { ready ->
            map = ready
            ready.cameraPosition = CameraPosition.Builder()
                .target(LatLng(47.497912, 19.040235))
                .zoom(11.0)
                .tilt(28.0)
                .build()
            ready.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    following = false
                    overviewShown = false
                    refreshHud()
                }
            }
            applyStyle()
        }
    }

    private fun applyStyle() {
        val ready = map ?: return
        ready.setStyle(MapStyles.uri(nightMode)) { style ->
            try {
                RaceMapPaint.apply(style, nightMode)
            } catch (err: RuntimeException) {
                // The stock style remains if a layer cannot be recolored.
            }
            loadedStyle = style
            installLocation(style)
            drawRoute(style)
            lastLocation?.let { pushLocation(it) }
        }
    }

    private fun installLocation(style: Style) {
        val ready = map ?: return
        val marker = GarageStore(this).markerRes()
        appliedMarker = marker
        val vehicle = LocationComponentOptions.builder(this)
            .foregroundDrawable(marker)
            .gpsDrawable(marker)
            .bearingDrawable(marker)
            .foregroundDrawableStale(marker)
            .backgroundDrawable(R.drawable.ic_toon_tow_shadow)
            .backgroundDrawableStale(R.drawable.ic_toon_tow_shadow)
            .foregroundTintColor(null)
            .bearingTintColor(null)
            .backgroundTintColor(null)
            .foregroundStaleTintColor(null)
            .backgroundStaleTintColor(null)
            .enableStaleState(false)
            .minZoomIconScale(0.9f)
            .maxZoomIconScale(1.5f)
            .elevation(0f)
            .build()
        val options = LocationComponentActivationOptions.builder(this, style)
            .useDefaultLocationEngine(false)
            .locationComponentOptions(vehicle)
            .build()
        ready.locationComponent.activateLocationComponent(options)
        ready.locationComponent.isLocationComponentEnabled = true
        ready.locationComponent.cameraMode = CameraMode.NONE
        locationReady = true
    }

    private fun wireSearch() {
        findViewById<Button>(R.id.toSearch).setOnClickListener { runSearch("to") }
        toField.doAfterTextChanged {
            if (destination != null && it?.toString() != destination?.name) {
                destination = null
                pendingRoute = false
                clearRoute()
            }
        }
    }

    private fun runSearch(target: String) {
        resultTarget = target
        val query = toField.text?.toString().orEmpty()
        val seq = ++searchSeq
        hideKeyboard()
        addressSearch.execute {
            val lookup = try {
                localAddresses.lookup(query)
            } catch (err: Exception) {
                LocalLookup.Unavailable
            }
            if (lookup is LocalLookup.Hit) {
                mainHandler.post {
                    if (seq != searchSeq) return@post
                    showLocal(lookup.address)
                }
                return@execute
            }
            search.search(query) { result ->
                mainHandler.post {
                    if (seq != searchSeq) return@post
                    result.onSuccess { places -> showPlaces(places) }
                    result.onFailure { err ->
                        results.removeAllViews()
                        lastPlaces = emptyList()
                        when (err) {
                            is SearchLimit -> showStatus(getString(R.string.status_search_limit))
                            else -> showStatus(getString(R.string.status_search))
                        }
                    }
                }
            }
        }
    }

    private fun showLocal(address: LocalAddress) {
        lastPlaces = emptyList()
        results.removeAllViews()
        if (address.pointCount > 1) {
            showStatus(getString(R.string.status_local_choice), error = false)
        } else {
            clearStatus()
        }
        for (point in address.points) {
            val district = point.district?.let { " ($it)" }.orEmpty()
            val button = resultButton(getString(R.string.local_point_label,
                address.settlement,
                address.street,
                address.housenumber,
                district,
                formatCoordinate(point.targetLat, point.targetLon),
                formatCoordinate(point.osmLat, point.osmLon),
                methodLabel(point.targetMethod)
            ))
            button.setOnClickListener {
                choosePlace(Place(
                    "${address.settlement}, ${address.street} ${address.housenumber}",
                    methodLabel(point.targetMethod),
                    point.targetLat,
                    point.targetLon,
                    PlacePrecision.HOUSE
                ))
            }
            results.addView(button)
        }
        val attribution = TextView(this)
        attribution.text = getString(R.string.local_attribution)
        attribution.setTextColor(ContextCompat.getColor(this, R.color.cockpit_ink))
        attribution.setPadding(8, 8, 8, 8)
        results.addView(attribution)
    }

    private fun formatCoordinate(lat: Double, lon: Double): String {
        return String.format(Locale.US, "%.7f, %.7f", lat, lon)
    }

    private fun methodLabel(method: String): String {
        return when (method) {
            "computed_interior" -> getString(R.string.local_method_interior)
            "osm_centroid_inside" -> getString(R.string.local_method_centroid)
            "osm_node" -> getString(R.string.local_method_node)
            else -> getString(R.string.local_method_way)
        }
    }

    private fun showPlaces(places: List<Place>) {
        lastPlaces = places
        results.removeAllViews()
        if (places.isEmpty()) {
            showStatus(getString(R.string.status_no_result))
            return
        }
        if (places.none { it.precision == PlacePrecision.HOUSE } && places.any { it.precision == PlacePrecision.STREET }) {
            showStatus(getString(R.string.status_house_missing), error = false)
        } else {
            clearStatus()
        }
        for (place in places) {
            val label = if (place.detail.isBlank()) place.name else place.name + "\n" + place.detail
            val button = resultButton(label)
            button.setOnClickListener { choosePlace(place) }
            results.addView(button)
        }
    }

    private fun resultButton(label: CharSequence): Button {
        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        button.setTextColor(ContextCompat.getColor(this, R.color.cockpit_ink))
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2A211F.toInt())
        return button
    }

    private fun choosePlace(place: Place) {
        hideKeyboard()
        results.removeAllViews()
        lastPlaces = emptyList()
        destination = place
        toField.setText(place.name)
        val start = GpsOrigin.place(getString(R.string.gps_origin), sawFix, lastLatitude, lastLongitude)
        if (start == null) {
            origin = null
            pendingRoute = true
            when (locationAccess()) {
                LocationAccess.DENIED -> showStatus(getString(R.string.status_permission))
                LocationAccess.PROVIDER_OFF -> showStatus(getString(R.string.status_gps_off))
                LocationAccess.PRECISE_REQUIRED -> showStatus(getString(R.string.status_precise))
                LocationAccess.READY -> showStatus(getString(R.string.status_gps_search), error = false)
            }
            startLocationUpdates()
            return
        }
        pendingRoute = false
        origin = start
        closeSearch()
        requestRoute(start, place)
    }

    private fun offerPendingRoute() {
        if (!pendingRoute || guiding || activePlan != null) return
        val goal = destination ?: return
        val start = GpsOrigin.place(getString(R.string.gps_origin), sawFix, lastLatitude, lastLongitude) ?: return
        pendingRoute = false
        origin = start
        closeSearch()
        requestRoute(start, goal)
    }

    private fun closeSearch() {
        driveView = driveView.closeSearch()
        hideKeyboard()
        presentDrive()
    }

    private fun requestRoute(start: Place, goal: Place) {
        if (kotlin.math.abs(start.lat - goal.lat) < 0.00008 && kotlin.math.abs(start.lon - goal.lon) < 0.00008) {
            showStatus(getString(R.string.status_same_point))
            return
        }
        stopGuidance()
        val seq = ++routeSeq
        showStatus(getString(R.string.status_planning), error = false)
        routes.requestRoute(start.lon, start.lat, goal.lon, goal.lat) { result ->
            mainHandler.post {
                if (seq != routeSeq) return@post
                when (result) {
                    is OsrmResult.Ready -> showPlan(result.plan)
                    is OsrmResult.Limit -> showStatus(getString(R.string.status_route_network))
                    is OsrmResult.Network -> showStatus(getString(R.string.status_route_network))
                    else -> showStatus(getString(R.string.status_route))
                }
            }
        }
    }

    private fun rerouteFromHere() {
        if (!NavigationOwner.mapMayGuide(tripActive = true)) return
        val goal = destination
        val now = SystemClock.elapsedRealtime()
        if (goal == null || !sawFix || !lastLatitude.isFinite() || !lastLongitude.isFinite() || !driveView.guiding) {
            reportReroute(getString(R.string.status_off_route), true)
            return
        }
        if (reroutes.inFlight) {
            reportReroute(getString(R.string.status_reroute_working), false)
            return
        }
        if (!reroutes.allow(now, confirmed = true, guiding = true)) {
            if (reroutes.cooling(now)) reportReroute(getString(R.string.status_reroute_unavailable), true)
            else reportReroute(getString(R.string.status_off_route), true)
            return
        }
        val seq = ++routeSeq
        reroutes.started()
        reportReroute(getString(R.string.status_reroute_working), false)
        routes.requestRoute(lastLongitude, lastLatitude, goal.lon, goal.lat) { result ->
            mainHandler.post {
                if (seq != routeSeq) return@post
                if (!driveView.guiding) {
                    reroutes.reset()
                    return@post
                }
                when (result) {
                    is OsrmResult.Ready -> adoptReroute(result.plan)
                    else -> {
                        reroutes.failed(SystemClock.elapsedRealtime())
                        reportReroute(getString(R.string.status_reroute_unavailable), true)
                    }
                }
            }
        }
    }

    private fun reportReroute(text: String, error: Boolean) {
        pilotNoticeText = text
        showStatus(text, error)
        paintPilotReadout()
    }

    private fun adoptReroute(plan: OsrmRoutePlan) {
        val json = RouteLineJson.feature(plan.coordinates) ?: run {
            reroutes.failed(SystemClock.elapsedRealtime())
            reportReroute(getString(R.string.status_reroute_unavailable), true)
            return
        }
        activePlan = plan
        routeJson = json
        routePoints = plan.coordinates
        traveledMeters = 0.0
        guidance.setPlan(plan)
        loadedStyle?.let { drawRoute(it) }
        reroutes.succeeded(SystemClock.elapsedRealtime())
        navigationHold = false
        offRouteSpoken = false
        pilotNoticeText = ""
        syncRouteChrome()
        clearStatus()
        rememberDrive()
        voice.halt()
        voice.speak("reroute", "reroute", null, "ujratervezes")
        paintPilotReadout()
    }

    private fun showPlan(plan: OsrmRoutePlan) {
        val json = RouteLineJson.feature(plan.coordinates)
        if (json == null) {
            showStatus(getString(R.string.status_route))
            return
        }
        activePlan = plan
        routeJson = json
        routePoints = plan.coordinates
        traveledMeters = 0.0
        loadedStyle?.let { drawRoute(it) }
        navigationHold = false
        syncRouteChrome()
        clearStatus()
        showOverview()
        voice.speak("utvonal_kesz", "utvonal_kesz", null, "kesz")
    }

    private fun drawRoute(style: Style) {
        val parts = RouteLineSplit.cut(routePoints, traveledMeters)
        val traveled = RouteLineJson.feature(parts.traveled) ?: EMPTY_LINE
        val remaining = RouteLineJson.feature(parts.remaining) ?: EMPTY_LINE
        val traveledSource = style.getSourceAs<GeoJsonSource>(TRAVELED_SOURCE)
        val remainingSource = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)
        if (traveledSource == null || remainingSource == null) {
            style.addSource(GeoJsonSource(TRAVELED_SOURCE, traveled))
            style.addSource(GeoJsonSource(ROUTE_SOURCE, remaining))
            val night = nightMode
            val layers = listOf(
                raceLayer(TRAVELED_CASING, TRAVELED_SOURCE, 7.5f, color(R.color.race_done_casing, R.color.race_done_casing_night, night), 1f, 0f),
                raceLayer(TRAVELED_LINE, TRAVELED_SOURCE, 3.6f, color(R.color.race_done, R.color.race_done_night, night), 1f, 0f),
                raceLayer(ROUTE_GLOW, ROUTE_SOURCE, 16f, color(R.color.race_glow, R.color.race_glow_night, night), if (night) 0.34f else 0.22f, 3.5f),
                raceLayer(ROUTE_CASING, ROUTE_SOURCE, 8.5f, color(R.color.race_casing, R.color.race_casing_night, night), 1f, 0f),
                raceLayer(ROUTE_LINE, ROUTE_SOURCE, 4.6f, color(R.color.race_route, R.color.race_route_night, night), 1f, 0f),
                raceLayer(ROUTE_SHEEN, ROUTE_SOURCE, 1.6f, color(R.color.race_route_sheen, R.color.race_route_sheen_night, night), 0.85f, 0f)
            )
            placeRouteLayers(style, layers)
        } else {
            traveledSource.setGeoJson(traveled)
            remainingSource.setGeoJson(remaining)
        }
    }

    private fun placeRouteLayers(style: Style, layers: List<LineLayer>) {
        val anchor = "mapbox-location-shadow-layer"
        if (style.getLayer(anchor) != null) {
            var below = anchor
            for (layer in layers.asReversed()) {
                style.addLayerBelow(layer, below)
                below = layer.id
            }
        } else {
            layers.forEach { style.addLayer(it) }
        }
    }

    private fun raceLayer(id: String, source: String, width: Float, color: Int, opacity: Float, blur: Float): LineLayer {
        return LineLayer(id, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(width),
            PropertyFactory.lineOpacity(opacity),
            PropertyFactory.lineBlur(blur),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    }

    private fun color(day: Int, night: Int, nightMode: Boolean): Int {
        return ContextCompat.getColor(this, if (nightMode) night else day)
    }

    private fun clearRoute() {
        NavigationVoice.clearRetained()
        val keepSearch = driveView.searchOpen
        routeSeq += 1
        activePlan = null
        routeJson = null
        routePoints = emptyList()
        reroutes.reset()
        traveledMeters = 0.0
        guidance.clear()
        pilotDistance = null
        pilotInstruction = ""
        pilotArrow = 0f
        driveView = DriveSession(searchOpen = keepSearch)
        following = false
        overviewShown = false
        navigationHold = false
        presentDrive()
        rememberDrive()
        hud.renderManeuver("—", "", 0f)
        hud.renderTrip("", "", "--:--")
        loadedStyle?.let { drawRoute(it) }
        refreshHud()
    }

    private fun startGuidance() {
        val plan = activePlan
        if (plan == null || routePoints.size < 2) {
            showStatus(getString(R.string.status_route))
            return
        }
        when (locationAccess()) {
            LocationAccess.DENIED -> {
                showStatus(getString(R.string.status_permission))
                requestLocationPermission()
                return
            }
            LocationAccess.PROVIDER_OFF -> {
                showStatus(getString(R.string.status_gps_off))
                return
            }
            LocationAccess.PRECISE_REQUIRED -> {
                showStatus(getString(R.string.status_precise))
                return
            }
            LocationAccess.READY -> Unit
        }
        if (!sawFix) {
            showStatus(getString(R.string.status_gps_search), error = false)
            startLocationUpdates()
            return
        }
        guidance.clear()
        NavigationVoice.clearRetained()
        driveView = driveView.start()
        finishDismissed = false
        offRouteSpoken = false
        pilotMapOpen = false
        pilotNoticeText = ""
        following = false
        overviewShown = false
        previewCard.visibility = View.GONE
        voice.halt()
        handingToRace = true
        presentDrive()
        rememberDrive()
        hideKeyboard()
        openRaceNavigation(announceStart = true)
    }

    /**
     * Hands the stored trip to the Filament screen. The map keeps the line and
     * does not listen, guide, or speak while that screen owns the trip.
     */
    private fun openRaceNavigation(announceStart: Boolean) {
        if (raceLaunching || !driveView.guiding) return
        val plan = activePlan ?: DriveSessionMemory.plan
        val points = if (routePoints.size >= 2) routePoints else DriveSessionMemory.routePoints
        if (plan == null || points.size < 2) {
            handingToRace = false
            presentDrive()
            showStatus(getString(R.string.race_route_missing))
            return
        }
        if (DriveSessionMemory.plan == null) rememberDrive()
        raceLaunching = true
        NavigationOwner.claimRace()
        stopLocationUpdates()
        voice.halt()
        try {
            startActivity(Intent(this, RacePrototypeActivity::class.java)
                .putExtra(RacePrototypeActivity.EXTRA_CAR, GarageStore(this).carId())
                .putExtra(RacePrototypeActivity.EXTRA_NAVIGATION, true)
                .putExtra(RacePrototypeActivity.EXTRA_ANNOUNCE, announceStart))
        } catch (err: RuntimeException) {
            raceLaunching = false
            handingToRace = false
            NavigationOwner.releaseRace(returningToMap = false)
            presentDrive()
            showStatus(getString(R.string.race_engine_failed))
        }
    }

    private fun completeArrivalFromRace() {
        finishDismissed = true
        driveView = if (driveView.guiding) driveView.arrive() else DriveSession(arrived = true)
        guidance.clear()
        NavigationVoice.clearRetained()
        voice.halt()
        offRouteSpoken = false
        pilotMapOpen = false
        pilotNoticeText = ""
        following = false
        overviewShown = false
        navigationHold = true
        presentDrive()
        rememberDrive()
        showStatus(getString(R.string.status_arrived), error = false)
    }

    private fun confirmStop() {
        if (stopDialog?.isShowing == true) return
        stopDialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.stop_confirm_title)
            .setMessage(R.string.stop_confirm_message)
            .setPositiveButton(R.string.stop_nav) { _, _ -> stopGuidance() }
            .setNegativeButton(R.string.stop_cancel, null)
            .show()
    }

    private fun stopGuidance() {
        reroutes.reset()
        NavigationVoice.clearRetained()
        voice.halt()
        offRouteSpoken = false
        pilotMapOpen = false
        pilotNoticeText = ""
        finishDismissed = false
        driveView = driveView.cancel()
        pilotDistance = null
        pilotInstruction = ""
        pilotArrow = 0f
        following = false
        overviewShown = false
        navigationHold = false
        guidance.clear()
        presentDrive()
        rememberDrive()
        hud.renderManeuver("—", "", 0f)
        hud.renderTrip("", "", "--:--")
        refreshReadout()
    }

    private fun endDriveOnArrival() {
        if (driveView.arrived) return
        driveView = driveView.arrive()
        pilotDistance = null
        pilotInstruction = ""
        pilotArrow = 0f
        following = false
        overviewShown = false
        navigationHold = true
        presentDrive()
        rememberDrive()
    }

    private fun toggleCockpit() {
        val next = driveView.toggleCollapse()
        if (next == driveView) return
        driveView = next
        presentDrive()
        rememberDrive()
    }

    private fun presentDrive() {
        if (!hudReady) return
        searchOpen = driveView.searchOpen
        guiding = driveView.guiding
        pilotVisible = false
        hud.showGuidance(false, false)
        hud.showFold(false, false)
        findViewById<View>(R.id.topCluster).visibility = View.VISIBLE
        findViewById<View>(R.id.cameraTools).visibility = View.VISIBLE
        if (::searchCard.isInitialized) {
            searchCard.visibility = if (driveView.searchOpen) View.VISIBLE else View.GONE
        }
        val resumeCard = findViewById<View>(R.id.resumeRaceCard)
        val showResume = driveView.guiding && !handingToRace && NavigationOwner.mapMayListen()
        resumeCard.visibility = if (showResume) View.VISIBLE else View.GONE
        placePilotMap(false)
        if (::pilotLayer.isInitialized) pilotLayer.visibility = View.GONE
        revealFinish()
        refreshPilot()
        syncRouteChrome()
    }

    private fun syncRouteChrome() {
        if (!hudReady || !::previewCard.isInitialized || !::previewMeta.isInitialized) return
        val prepared = activePlan != null && routePoints.size >= 2 && !driveView.arrived
        hud.noteRoute(driveView.guiding && !driveView.arrived, prepared)
        val show = RouteCaption.previewVisible(if (prepared) routePoints.size else 0, driveView.guiding, driveView.arrived)
        if (show) {
            val plan = activePlan ?: return
            previewMeta.text = RouteCaption.preview(plan.distanceMeters, plan.durationSeconds)
            previewCard.visibility = View.VISIBLE
        } else {
            previewMeta.text = ""
            previewCard.visibility = View.GONE
        }
    }

    private fun revealFinish() {
        if (!::finishLayer.isInitialized) return
        val celebrate = !finishDismissed && FinishCeremony.ready(
            driveView.arrived,
            if (lastHasSpeed) lastSpeedMps.toDouble() else null,
            lastHasSpeed,
            sawFix
        )
        finishLayer.visibility = if (celebrate) View.VISIBLE else View.GONE
        if (celebrate) finishLayer.bringToFront()
    }

    private fun togglePilotMap() {
        pilotMapOpen = !pilotMapOpen
        placePilotMap(pilotVisible)
        refreshPilotControls()
    }

    private fun placePilotMap(pilot: Boolean) {
        if (!::mapHost.isInitialized) return
        val params = mapHost.layoutParams as FrameLayout.LayoutParams
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        if (pilot && pilotMapOpen) {
            params.width = (screenW * 0.46f).toInt().coerceAtLeast((148 * density).toInt())
            params.height = (screenH * 0.30f).toInt().coerceAtLeast((160 * density).toInt())
            params.gravity = Gravity.TOP or Gravity.START
            params.topMargin = (16 * density).toInt()
            params.leftMargin = (12 * density).toInt()
            params.marginStart = params.leftMargin
            params.marginEnd = 0
            val frame = (3 * density).toInt().coerceAtLeast(2)
            mapHost.setPadding(frame, frame, frame, frame)
            mapHost.setBackgroundColor(0xFFFF3B12.toInt())
            mapHost.elevation = 16f * density
            mapHost.visibility = View.VISIBLE
        } else if (pilot) {
            params.width = FrameLayout.LayoutParams.MATCH_PARENT
            params.height = FrameLayout.LayoutParams.MATCH_PARENT
            params.topMargin = 0
            params.leftMargin = 0
            params.marginStart = 0
            params.marginEnd = 0
            mapHost.setPadding(0, 0, 0, 0)
            mapHost.visibility = View.GONE
        } else {
            params.width = FrameLayout.LayoutParams.MATCH_PARENT
            params.height = FrameLayout.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP or Gravity.START
            params.topMargin = 0
            params.leftMargin = 0
            params.marginStart = 0
            params.marginEnd = 0
            mapHost.setPadding(0, 0, 0, 0)
            mapHost.setBackgroundColor(0x00000000)
            mapHost.elevation = 0f
            mapHost.visibility = View.VISIBLE
        }
        mapHost.layoutParams = params
        if (::pilotCockpit.isInitialized) {
            if (pilot && pilotMapOpen) {
                pilotCockpit.setMapWindow(
                    true,
                    params.leftMargin.toFloat(),
                    params.topMargin.toFloat(),
                    (params.leftMargin + params.width).toFloat(),
                    (params.topMargin + params.height).toFloat()
                )
            } else {
                pilotCockpit.setMapWindow(false, 0f, 0f, 0f, 0f)
            }
        }
        if (pilot) {
            if (::pilotLayer.isInitialized) pilotLayer.bringToFront()
            if (::hudHost.isInitialized) hudHost.bringToFront()
        }
    }

    private val cockpitGlide = Runnable {
        cockpitGliding = false
        if (!pilotVisible) return@Runnable
        refreshPilot()
    }

    private fun refreshPilot() {
        if (!::pilotCockpit.isInitialized || !pilotVisible) return
        val age = if (sawFix) SystemClock.elapsedRealtime() - lastFixElapsed else -1L
        val pose = RouteMotion.pose(
            routePoints,
            lastLongitude,
            lastLatitude,
            if (lastHasSpeed) lastSpeedMps.toDouble() else null,
            lastHasSpeed,
            age,
            sawFix
        )
        val strip = if (pose == null) {
            RoadStrip.EMPTY
        } else {
            RoadRibbon.project(pose.lon, pose.lat, pose.bearingDegrees, routePoints)
        }
        val speed = if (!sawFix) null else GpsReadout.speedKmh(lastSpeedMps, lastHasSpeed, age)
        val garage = GarageStore(this)
        pilotCockpit.show(
            speed,
            pilotDistance,
            pilotInstruction,
            pilotArrow,
            strip,
            pose?.alongMeters ?: 0.0,
            garage.carId(),
            nightMode,
            garage.number()
        )
        paintPilotReadout(speed, updateSpeed = true)
        revealFinish()
        val moving = pose != null && lastHasSpeed && lastSpeedMps >= RouteMotion.stoppedMetersPerSecond && age in 0..GpsSpeed.freshMs
        if (moving && !cockpitGliding) {
            cockpitGliding = true
            mainHandler.postDelayed(cockpitGlide, 40L)
        } else if (!moving && cockpitGliding) {
            cockpitGliding = false
            mainHandler.removeCallbacks(cockpitGlide)
        }
    }

    private fun rememberDrive() {
        DriveSessionStore(this).write(driveView)
        val plan = activePlan
        if (driveView.guiding && plan != null) {
            DriveSessionMemory.hold(plan, routeJson, routePoints, traveledMeters, origin, destination)
            DriveTripFile.save(this)
        } else {
            DriveSessionMemory.clear()
            DriveTripFile.delete(this)
        }
        appliedDriveRevision = DriveSessionMemory.revision
    }

    private fun restoreActiveDrive() {
        val stored = DriveSessionStore(this).read()
        if (DriveSessionMemory.plan == null) DriveTripFile.load(this)
        val plan = DriveSessionMemory.plan
        val resumed = DriveSession.restore(stored, plan != null)
        if (resumed.guiding && plan != null) {
            driveView = resumed
            activePlan = plan
            routeJson = DriveSessionMemory.routeJson
            routePoints = DriveSessionMemory.routePoints
            traveledMeters = DriveSessionMemory.traveledMeters
            origin = DriveSessionMemory.origin
            destination = DriveSessionMemory.destination
            guidance.clear()
            guiding = true
            following = true
            overviewShown = false
            searchOpen = false
            DriveSessionStore(this).write(driveView)
        } else {
            driveView = DriveSession()
            guiding = false
            DriveSessionMemory.clear()
            DriveTripFile.delete(this)
            if (stored.guiding || stored.arrived) DriveSessionStore(this).write(driveView)
        }
        appliedDriveRevision = DriveSessionMemory.revision
    }

    private fun adoptMemoryIfNewer() {
        if (DriveSessionMemory.revision == appliedDriveRevision) return
        val stored = DriveSessionMemory.plan ?: return
        activePlan = stored
        routeJson = DriveSessionMemory.routeJson
        routePoints = DriveSessionMemory.routePoints
        traveledMeters = DriveSessionMemory.traveledMeters
        guidance.clear()
        loadedStyle?.let { drawRoute(it) }
        appliedDriveRevision = DriveSessionMemory.revision
    }

    private fun recenter() {
        following = true
        overviewShown = false
        followVehicle()
        refreshHud()
    }

    private fun showOverview() {
        val ready = map ?: return
        following = false
        overviewShown = true
        if (routePoints.size >= 2) {
            val bounds = LatLngBounds.Builder()
            for (point in routePoints) bounds.include(LatLng(point.lat, point.lon))
            ready.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))
        } else if (sawFix) {
            ready.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lastLatitude, lastLongitude), 11.0))
        }
        refreshHud()
    }

    private fun toggleSearch() {
        driveView = driveView.toggleSearch()
        if (!driveView.searchOpen) hideKeyboard()
        presentDrive()
        DriveSessionStore(this).write(driveView)
    }

    private fun toggleVoice() {
        voiceOn = !voiceOn
        voice.enabled = voiceOn
        if (!voiceOn) voice.silence()
        hudPrefs.edit().putBoolean("voice", voiceOn).apply()
        refreshPilotControls()
        refreshHud()
    }

    private fun toggleNorth() {
        northUp = !northUp
        hudPrefs.edit().putBoolean("north", northUp).apply()
        followVehicle()
        refreshHud()
    }

    private fun toggleNight() {
        nightMode = !nightMode
        hudPrefs.edit().putBoolean("night", nightMode).apply()
        refreshPilotControls()
        refreshPilot()
        locationReady = false
        applyStyle()
        refreshHud()
    }

    private fun requestLocationPermission() {
        readLocationGrants()
        val needed = ArrayList<String>()
        if (!fineGranted) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!coarseGranted) needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (needed.isEmpty()) {
            refreshLocationAccess()
            requestNotificationPermission()
            return
        }
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshLocationAccess() {
        if (!::statusText.isInitialized) return
        readLocationGrants()
        when (locationAccess()) {
            LocationAccess.DENIED -> {
                stopLocationUpdates()
                openLocationSettings = false
                settingsButton.visibility = View.VISIBLE
                navigationHold = true
                showStatus(getString(R.string.status_permission))
            }
            LocationAccess.PROVIDER_OFF -> {
                stopLocationUpdates()
                openLocationSettings = true
                settingsButton.visibility = View.VISIBLE
                navigationHold = true
                showStatus(getString(R.string.status_gps_off))
            }
            LocationAccess.PRECISE_REQUIRED -> {
                openLocationSettings = false
                settingsButton.visibility = View.VISIBLE
                navigationHold = true
                showStatus(getString(R.string.status_precise))
                startLocationUpdates()
            }
            LocationAccess.READY -> {
                settingsButton.visibility = View.GONE
                startLocationUpdates()
                if (!sawFix) {
                    navigationHold = true
                    showStatus(getString(R.string.status_gps_search), error = false)
                } else if (!guiding && blockingStatus()) {
                    navigationHold = false
                    clearStatus()
                }
                refreshReadout()
            }
        }
        refreshHud()
    }

    private fun blockingStatus(): Boolean {
        val current = statusText.text?.toString().orEmpty()
        return current == getString(R.string.status_permission) ||
            current == getString(R.string.status_precise) ||
            current == getString(R.string.status_gps_off) ||
            current == getString(R.string.status_gps_search) ||
            current == getString(R.string.status_gps)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!NavigationOwner.mapMayListen()) return
        if (updatesStarted) return
        val access = locationAccess()
        if (access != LocationAccess.READY && access != LocationAccess.PRECISE_REQUIRED) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val targets = LocationListenPlan.targets(
            Build.VERSION.SDK_INT,
            fineGranted,
            true,
            providerEnabled(manager, LocationManager.GPS_PROVIDER),
            providerEnabled(manager, LocationManager.NETWORK_PROVIDER)
        )
        val executor = ContextCompat.getMainExecutor(this)
        var registered = 0
        for (target in targets) {
            val provider = providerName(target.provider) ?: continue
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    val quality = if (target.highAccuracy) {
                        LocationRequest.QUALITY_HIGH_ACCURACY
                    } else {
                        LocationRequest.QUALITY_BALANCED_POWER_ACCURACY
                    }
                    val request = LocationRequest.Builder(1_000L)
                        .setQuality(quality)
                        .setMinUpdateIntervalMillis(500L)
                        .setMaxUpdateDelayMillis(0L)
                        .build()
                    manager.requestLocationUpdates(provider, request, executor, locationListener)
                    manager.getCurrentLocation(provider, null, executor) { location ->
                        if (location != null) onFix(location)
                    }
                } else {
                    manager.requestLocationUpdates(provider, 1_000L, 0f, locationListener, Looper.getMainLooper())
                    if (Build.VERSION.SDK_INT >= 30) {
                        manager.getCurrentLocation(provider, null, executor) { location ->
                            if (location != null) onFix(location)
                        }
                    }
                }
                registered += 1
            } catch (err: SecurityException) {
                continue
            } catch (err: IllegalArgumentException) {
                continue
            }
        }
        updatesStarted = registered > 0
    }

    private fun providerName(provider: String): String? {
        return when (provider) {
            LocationListenPlan.gps -> LocationManager.GPS_PROVIDER
            LocationListenPlan.network -> LocationManager.NETWORK_PROVIDER
            LocationListenPlan.fused -> if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null
            else -> null
        }
    }

    private fun stopLocationUpdates() {
        if (!updatesStarted) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        manager.removeUpdates(locationListener)
        updatesStarted = false
    }

    private fun onFix(location: Location) {
        val age = GpsFixGate.ageMs(
            location.elapsedRealtimeNanos,
            SystemClock.elapsedRealtimeNanos(),
            location.time,
            System.currentTimeMillis()
        )
        val mock = if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider
        if (GpsFixGate.verdict(location.latitude, location.longitude, age, mock) != GpsSampleVerdict.LIVE) return
        sawFix = true
        lastFixElapsed = SystemClock.elapsedRealtime()
        lastLatitude = location.latitude
        lastLongitude = location.longitude
        lastHasSpeed = location.hasSpeed()
        lastSpeedMps = if (location.hasSpeed()) location.speed else 0f
        lastAccuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null
        lastBearing = if (location.hasBearing()) location.bearing.toDouble() else null
        lastLocation = location
        pushLocation(location)
        if (!centeredOnce && !guiding) {
            centeredOnce = true
            map?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
        }
        publishFix(0L)
        offerPendingRoute()
    }

    private fun refreshProgressAge() {
        if (!NavigationOwner.mapMayGuide(driveView.guiding)) {
            refreshReadout()
            return
        }
        val age = SystemClock.elapsedRealtime() - lastFixElapsed
        if (guiding && (fineGranted || coarseGranted) && locationEnabled()) {
            guidance.refreshAge(age)?.let { renderCue(it) }
            followVehicle()
        }
        refreshReadout()
    }

    private fun publishFix(ageMs: Long) {
        if (!NavigationOwner.mapMayGuide(driveView.guiding)) {
            refreshReadout()
            return
        }
        if (guiding) {
            guidance.onFix(lastLongitude, lastLatitude, SystemClock.elapsedRealtime(), ageMs, lastAccuracy)?.let { renderCue(it) }
            followVehicle()
        } else if (blockingStatus()) {
            navigationHold = false
            clearStatus()
        }
        refreshReadout()
    }

    private fun renderCue(cue: GuidanceCue) {
        if (!NavigationVoice.mapMaySpeak(driveView.guiding) || driveView.guiding) return
        lastTrust = cue.trust
        hud.renderManeuver(
            ManeuverEvents.formatDistance(cue.maneuverDistanceMeters),
            cue.maneuverLabel,
            cue.arrowDegrees
        )
        hud.renderTrip(cue.roadName, ManeuverEvents.formatDistance(cue.remainingMeters) + " a célig", etaText(cue))
        pilotDistance = cue.maneuverDistanceMeters
        pilotInstruction = CockpitInstruction.line(cue.maneuverLabel, cue.arrowDegrees)
        pilotArrow = cue.arrowDegrees
        tripRemaining = ManeuverEvents.formatDistance(cue.remainingMeters)
        tripEta = etaText(cue)
        val kind = RoadClassifier.classify(cue.maneuverType, cue.roadClasses)
        roadKindLabel = RoadClassifier.label(RoadGuidance.shown(kind, cue.offRouteConfirmed, cue.progressHeld))
        refreshPilot()
        speakCue(cue)
        when {
            cue.arrived -> {
                navigationHold = true
                offRouteSpoken = false
                pilotNoticeText = ""
                endDriveOnArrival()
                showStatus(getString(R.string.status_arrived), error = false)
            }
            cue.offRouteConfirmed -> {
                navigationHold = true
                if (!offRouteSpoken) {
                    voice.halt()
                    voice.speak("rossz_irany", "rossz_irany", null, "letérés")
                    offRouteSpoken = true
                }
                rerouteFromHere()
            }
            cue.trust == GpsTrust.LOST -> {
                navigationHold = true
                showStatus(getString(R.string.status_gps))
            }
            cue.trust == GpsTrust.UNUSABLE || cue.trust == GpsTrust.INACCURATE -> {
                navigationHold = true
                showStatus(getString(R.string.status_gps_poor))
            }
            else -> {
                navigationHold = false
                offRouteSpoken = false
                val current = statusText.text?.toString().orEmpty()
                if (current == getString(R.string.status_off_route) ||
                    current == getString(R.string.status_reroute_working) ||
                    current == getString(R.string.status_reroute_unavailable) ||
                    current == getString(R.string.status_gps_poor) ||
                    current == getString(R.string.status_gps) ||
                    current == getString(R.string.status_gps_search) ||
                    current == getString(R.string.status_precise) ||
                    current == getString(R.string.status_arrived)
                ) {
                    pilotNoticeText = ""
                    clearStatus()
                    paintPilotReadout()
                }
            }
        }
        val along = cue.totalMeters - cue.remainingMeters
        traveledMeters = if (along.isFinite()) along.coerceIn(0.0, cue.totalMeters.coerceAtLeast(0.0)) else 0.0
        loadedStyle?.let { drawRoute(it) }
        if (driveView.guiding && activePlan != null) {
            DriveSessionMemory.hold(activePlan!!, routeJson, routePoints, traveledMeters, origin, destination)
            appliedDriveRevision = DriveSessionMemory.revision
        }
    }

    private fun speakCue(cue: GuidanceCue) {
        if (!NavigationVoice.mapMaySpeak(driveView.guiding)) return
        if (!driveView.guiding || cue.arrived || cue.progressHeld || cue.offRouteConfirmed) return
        val event = ManeuverEvents.eventId(cue.maneuverType, cue.maneuverModifier)
        if (event.isEmpty()) return
        val kind = RoadClassifier.classify(cue.maneuverType, cue.roadClasses)
        voice.speak(
            "turn",
            event,
            cue.maneuverDistanceMeters,
            event + ":" + cue.maneuverLabel,
            RoadGuidance.leadMeters(kind, cue.maneuverType)
        )
    }

    private fun paintPilotReadout(speed: Int? = null, updateSpeed: Boolean = false) {
        if (!::pilotSpeed.isInitialized) return
        if (updateSpeed) pilotSpeed.text = speed?.toString() ?: "—"
        pilotManeuver.text = pilotInstruction
        pilotManeuverDistance.text = if (pilotDistance == null) "—" else ManeuverEvents.formatDistance(pilotDistance ?: 0.0)
        pilotRemaining.text = getString(R.string.remaining) + " " + tripRemaining
        pilotEta.text = getString(R.string.arrival) + " " + tripEta
        pilotRoadKind.text = roadKindLabel
        pilotRoadKind.visibility = if (roadKindLabel.isBlank()) View.GONE else View.VISIBLE
        pilotNotice.text = pilotNoticeText
        pilotNotice.visibility = if (pilotVisible && pilotNoticeText.isNotBlank()) View.VISIBLE else View.GONE
        refreshPilotControls()
    }

    private fun refreshPilotControls() {
        if (!::pilotVoice.isInitialized) return
        pilotVoice.text = getString(if (voiceOn) R.string.voice_on else R.string.voice_off)
        pilotMap.text = getString(if (pilotMapOpen) R.string.map_close else R.string.map_open)
        pilotNight.text = getString(if (nightMode) R.string.day_mode else R.string.night_mode)
    }

    private fun etaText(cue: GuidanceCue): String {
        val offset = NavigationEta.offsetMillis(activePlan?.durationSeconds, NavigationEta.fraction(cue.remainingMeters, cue.totalMeters))
            ?: return "--:--"
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis() + offset
        return String.format(Locale.US, "%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    private fun followVehicle() {
        if (!following || overviewShown || !sawFix) return
        val camera = FollowCameraPlan.decide(true, northUp, lastTrust, lastBearing)
        if (!camera.move) return
        val ready = map ?: return
        val height = (mapView?.height ?: 0).toDouble()
        val padding = DriveCamera.followingPadding(height, resources.displayMetrics.density)
        ready.easeCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(lastLatitude, lastLongitude))
                .zoom(camera.zoom)
                .bearing(camera.bearing)
                .tilt(camera.tilt)
                .padding(doubleArrayOf(padding.left, padding.top, padding.right, padding.bottom))
                .build()
        ), 700)
    }

    private fun pushLocation(location: Location) {
        if (!locationReady) return
        val component = map?.locationComponent ?: return
        val shown = Location(location)
        val speed = if (location.hasSpeed()) location.speed.toDouble() else null
        val route = routeBearing()
        val chosen = VehicleHeading.choose(
            if (location.hasBearing()) location.bearing.toDouble() else null,
            speed,
            route
        )
        val display = MarkerBearing.advance(displayedBearing, chosen, speed, route)
        displayedBearing = display
        if (display != null) shown.bearing = display.toFloat()
        component.forceLocationUpdate(shown)
        component.renderMode = if (display != null) RenderMode.GPS else RenderMode.NORMAL
        component.cameraMode = CameraMode.NONE
    }

    private fun routeBearing(): Double? {
        if (!guiding) return null
        val plan = activePlan ?: return null
        val update = RouteProgress.project(plan, lastLongitude, lastLatitude)
        if (update.offRoute) return null
        return RouteHeading.forwardDegrees(plan.coordinates, update.distanceAlongMeters)
    }

    private fun refreshReadout() {
        if (!hudReady || !sawFix) return
        val age = SystemClock.elapsedRealtime() - lastFixElapsed
        val speed = GpsReadout.speedKmh(lastSpeedMps, lastHasSpeed, age)
        val text = GpsReadout.text(lastLatitude, lastLongitude, speed)
        hud.renderSpeed(speed)
        refreshPilot()
        if (navigationHold || text == null) {
            refreshHud()
            return
        }
        val current = statusText.text?.toString().orEmpty()
        val canReplace = statusText.visibility != View.VISIBLE || current.isEmpty() || current == readoutText
        if (canReplace) {
            readoutText = text
            showStatus(text, error = false)
        }
        refreshHud()
    }

    private fun refreshHud() {
        if (!hudReady) return
        hud.renderControls(voiceOn, northUp, nightMode, overviewShown, lastBearing, guiding && !following)
    }

    private fun restoreHud(status: String, statusVisible: Boolean) {
        presentDrive()
        if (lastPlaces.isNotEmpty() && searchOpen) showPlaces(lastPlaces)
        else if (statusVisible && status.isNotEmpty()) showStatus(status, statusError)
        else clearStatus()
        refreshReadout()
        refreshHud()
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun readLocationGrants() {
        fineGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        coarseGranted = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun locationAccess(): LocationAccess {
        return LocationAccessGate.decide(fineGranted, coarseGranted, locationEnabled())
    }

    private fun locationEnabled(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) {
            return try {
                manager.isLocationEnabled
            } catch (err: Exception) {
                false
            }
        }
        return providerEnabled(manager, LocationManager.GPS_PROVIDER) || providerEnabled(manager, LocationManager.NETWORK_PROVIDER)
    }

    private fun providerEnabled(manager: LocationManager, provider: String): Boolean {
        return try {
            manager.isProviderEnabled(provider)
        } catch (err: Exception) {
            false
        }
    }

    private fun showStatus(text: String, error: Boolean = true) {
        statusError = error
        statusText.setTextColor(ContextCompat.getColor(this, if (error) R.color.nav_error else R.color.nav_ok))
        statusText.text = text
        statusText.visibility = if (pilotVisible) View.GONE else View.VISIBLE
    }

    private fun clearStatus() {
        statusError = false
        statusText.text = ""
        statusText.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val token = if (::toField.isInitialized) toField.windowToken else null
        if (token != null) imm.hideSoftInputFromWindow(token, 0)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun openLocationSourceSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    companion object {
        private const val EMPTY_LINE = """{"type":"FeatureCollection","features":[]}"""
        private const val ROUTE_SOURCE = "toonnav-route"
        private const val TRAVELED_SOURCE = "toonnav-route-traveled"
        private const val ROUTE_GLOW = "toonnav-route-glow"
        private const val ROUTE_CASING = "toonnav-route-casing"
        private const val ROUTE_LINE = "toonnav-route-line"
        private const val ROUTE_SHEEN = "toonnav-route-sheen"
        private const val TRAVELED_CASING = "toonnav-route-traveled-casing"
        private const val TRAVELED_LINE = "toonnav-route-traveled-line"
    }
}
