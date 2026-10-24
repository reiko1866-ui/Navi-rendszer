package hu.toonnav.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.MapboxOptions
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.RotateGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.maps.plugin.ModelElevationReference
import com.mapbox.maps.plugin.ModelScaleMode
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.OnRotateListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraStateChangedObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import java.io.IOException
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class, MapboxExperimental::class)
class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val search = PhotonSearch()
    private val voice by lazy { VoiceGuide(assets) }
    private val navigationLocationProvider = NavigationLocationProvider()

    private lateinit var mapHost: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var settingsButton: Button
    private lateinit var searchCard: LinearLayout
    private lateinit var fromField: EditText
    private lateinit var toField: EditText
    private lateinit var results: LinearLayout
    private lateinit var previewCard: LinearLayout
    private lateinit var previewMeta: TextView
    private lateinit var maneuverCard: LinearLayout
    private lateinit var maneuverArrow: ImageView
    private lateinit var stepDistance: TextView
    private lateinit var maneuverText: TextView
    private lateinit var driveCard: LinearLayout
    private lateinit var remainText: TextView
    private lateinit var etaText: TextView
    private lateinit var cameraTools: LinearLayout
    private lateinit var recenterButton: Button
    private lateinit var overviewButton: Button

    private var mapView: MapView? = null
    private var viewportDataSource: MapboxNavigationViewportDataSource? = null
    private var navigationCamera: NavigationCamera? = null
    private var routeLineApi: MapboxRouteLineApi? = null
    private var routeLineView: MapboxRouteLineView? = null
    private var routeArrowApi: MapboxRouteArrowApi? = null
    private var routeArrowView: MapboxRouteArrowView? = null
    private var loadedStyle: Style? = null
    private var navigationRef: MapboxNavigation? = null
    private var navigationReady = false

    private var origin: Place? = null
    private var destination: Place? = null
    private var resultTarget = "to"
    private var searchSeq = 0
    private var routeSeq = 0
    private var guiding = false
    private var overviewLocked = false
    private var cameraFollows = false
    private var lastFixElapsed = 0L
    private var hasFix = false
    private var locationGranted = false
    private var awaitingGps = false
    private var routeDrawn = false
    private var acceptedRouteId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = fine || coarse
        if (locationGranted) {
            clearStatus()
            settingsButton.visibility = android.view.View.GONE
            startLocationSession()
        } else {
            showStatus(getString(R.string.status_permission))
            settingsButton.visibility = android.view.View.VISIBLE
        }
    }

    private val routesObserver = RoutesObserver { update ->
        val routes = update.navigationRoutes
        if (routes.isEmpty()) {
            routeDrawn = false
            return@RoutesObserver
        }
        drawRoutes(routes)
        viewportDataSource?.onRouteChanged(routes.first())
        viewportDataSource?.evaluate()
        if (!guiding) navigationCamera?.requestNavigationCameraToOverview()
        val routeId = routes.first().id
        if (guiding && acceptedRouteId != null && acceptedRouteId != routeId) {
            voice.speak("reroute", "ujratervezes", null, routeId)
        }
        acceptedRouteId = routeId
        routeDrawn = true
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhanced = locationMatcherResult.enhancedLocation
            hasFix = true
            awaitingGps = false
            lastFixElapsed = SystemClock.elapsedRealtime()
            navigationLocationProvider.changePosition(enhanced, locationMatcherResult.keyPoints)
            viewportDataSource?.onLocationChanged(enhanced)
            viewportDataSource?.evaluate()
            if (statusText.text == getString(R.string.status_gps)) clearStatus()
        }
    }

    private val progressObserver = RouteProgressObserver { progress ->
        if (!guiding) return@RouteProgressObserver
        val banner = progress.bannerInstructions?.primary()
        val instruction = banner?.text()?.takeIf { it.isNotBlank() } ?: "Tovább"
        val type = banner?.type()
        val modifier = banner?.modifier()
        val stepMeters = progress.currentLegProgress?.currentStepProgress?.distanceRemaining?.toDouble()
        stepDistance.text = if (stepMeters == null) "—" else ManeuverEvents.formatDistance(stepMeters)
        maneuverText.text = instruction
        maneuverArrow.rotation = ManeuverEvents.bannerRotation(modifier)
        remainText.text = getString(R.string.remaining) + " " +
            ManeuverEvents.formatDistance(progress.distanceRemaining.toDouble()) + " · " +
            ManeuverEvents.formatDuration(progress.durationRemaining)
        etaText.text = getString(R.string.arrival) + " " + clockAfter(progress.durationRemaining)
        val eventId = ManeuverEvents.eventId(type, modifier)
        val stepIndex = progress.currentLegProgress?.currentStepProgress?.stepIndex ?: 0
        voice.speak("turn", eventId, stepMeters, instruction + "@" + stepIndex)
        viewportDataSource?.onRouteProgressChanged(progress)
        viewportDataSource?.evaluate()
        renderRouteProgress(progress)
    }

    private val cameraStateObserver = NavigationCameraStateChangedObserver { state ->
        val following = state == NavigationCameraState.FOLLOWING ||
            state == NavigationCameraState.TRANSITION_TO_FOLLOWING
        cameraFollows = following
        if (!::recenterButton.isInitialized || !guiding) return@NavigationCameraStateChangedObserver
        recenterButton.visibility = if (following) android.view.View.GONE else android.view.View.VISIBLE
        val overview = state == NavigationCameraState.OVERVIEW ||
            state == NavigationCameraState.TRANSITION_TO_OVERVIEW
        overviewButton.setText(if (overview) R.string.follow_view else R.string.overview)
    }

    private val moveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) = releaseFollow()
        override fun onMove(detector: MoveGestureDetector) = false
        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }

    private val rotateListener = object : OnRotateListener {
        override fun onRotateBegin(detector: RotateGestureDetector) = releaseFollow()
        override fun onRotate(detector: RotateGestureDetector) {}
        override fun onRotateEnd(detector: RotateGestureDetector) {}
    }

    private val scaleListener = object : OnScaleListener {
        override fun onScaleBegin(detector: StandardScaleGestureDetector) = releaseFollow()
        override fun onScale(detector: StandardScaleGestureDetector) {}
        override fun onScaleEnd(detector: StandardScaleGestureDetector) {}
    }

    private val gpsWatch = object : Runnable {
        override fun run() {
            val stale = lastFixElapsed > 0L &&
                SystemClock.elapsedRealtime() - lastFixElapsed > DriveCamera.gpsStaleMs
            val showingGps = statusText.text == getString(R.string.status_gps)
            if (guiding && stale && (statusText.visibility != android.view.View.VISIBLE || showingGps)) {
                showStatus(getString(R.string.status_gps))
            }
            mainHandler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
        if (token.isNotBlank()) MapboxOptions.accessToken = token
        setContentView(R.layout.activity_main)
        bindViews()
        Thread { voice.load() }.start()
        if (token.isBlank()) {
            showStatus(getString(R.string.status_token))
            searchCard.visibility = android.view.View.GONE
            return
        }
        buildMap()
        wireSearch()
        findViewById<Button>(R.id.startButton).setOnClickListener { startGuidance() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopGuidance() }
        recenterButton.setOnClickListener { recenter() }
        overviewButton.setOnClickListener { toggleOverview() }
        settingsButton.setOnClickListener { openAppSettings() }
        ensureNavigation()
        requestLocationPermission()
        mainHandler.post(gpsWatch)
    }

    override fun onStart() {
        super.onStart()
        if (guiding && cameraFollows && !overviewLocked) {
            navigationCamera?.requestNavigationCameraToFollowing()
        }
    }

    private fun ensureNavigation() {
        if (navigationRef != null || BuildConfig.MAPBOX_PUBLIC_TOKEN.isBlank()) return
        val created = MapboxNavigationProvider.create(NavigationOptions.Builder(this).build())
        navigationRef = created
        navigationReady = true
        created.registerRoutesObserver(routesObserver)
        created.registerLocationObserver(locationObserver)
        created.registerRouteProgressObserver(progressObserver)
    }

    private fun bindViews() {
        mapHost = findViewById(R.id.mapHost)
        statusText = findViewById(R.id.statusText)
        settingsButton = findViewById(R.id.settingsButton)
        searchCard = findViewById(R.id.searchCard)
        fromField = findViewById(R.id.fromField)
        toField = findViewById(R.id.toField)
        results = findViewById(R.id.results)
        previewCard = findViewById(R.id.previewCard)
        previewMeta = findViewById(R.id.previewMeta)
        maneuverCard = findViewById(R.id.maneuverCard)
        maneuverArrow = findViewById(R.id.maneuverArrow)
        stepDistance = findViewById(R.id.stepDistance)
        maneuverText = findViewById(R.id.maneuverText)
        driveCard = findViewById(R.id.driveCard)
        remainText = findViewById(R.id.remainText)
        etaText = findViewById(R.id.etaText)
        cameraTools = findViewById(R.id.cameraTools)
        recenterButton = findViewById(R.id.recenterButton)
        overviewButton = findViewById(R.id.overviewButton)
    }

    private fun buildMap() {
        val map = MapView(this)
        mapHost.addView(map, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        mapView = map
        map.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(19.040235, 47.497912))
                .zoom(11.0)
                .build()
        )
        val density = resources.displayMetrics.density
        map.logo.marginBottom = 140f * density
        val line = ContextCompat.getColor(this, R.color.route_green)
        val casing = ContextCompat.getColor(this, R.color.route_casing)
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(this)
                .routeLineColorResources(routeColors(line, casing))
                .lineDepthOcclusionFactor(0.85)
                .routeLineBlurEnabled(false)
                .build()
        )
        routeArrowApi = MapboxRouteArrowApi()
        routeArrowView = MapboxRouteArrowView(
            RouteArrowOptions.Builder(this)
                .withArrowColor(ContextCompat.getColor(this, R.color.route_arrow))
                .withArrowCasingColor(casing)
                .build()
        )
        val viewport = MapboxNavigationViewportDataSource(map.mapboxMap)
        val follow = viewport.options.followingFrameOptions
        follow.defaultPitch = DriveCamera.followPitch
        follow.minZoom = DriveCamera.followMinZoom
        follow.maxZoom = DriveCamera.followMaxZoom
        follow.maximizeViewableGeometryWhenPitchZero = false
        follow.pitchNearManeuvers.enabled = true
        follow.pitchNearManeuvers.triggerDistanceFromManeuver = 220.0
        follow.frameGeometryAfterManeuver.enabled = true
        follow.frameGeometryAfterManeuver.distanceToFrameAfterManeuver = 130.0
        follow.bearingSmoothing.enabled = true
        follow.bearingSmoothing.maxBearingAngleDiff = 40.0
        val overview = viewport.options.overviewFrameOptions
        overview.bearingUpdatesAllowed = true
        overview.pitchUpdatesAllowed = true
        overview.maxZoom = 15.5
        viewportDataSource = viewport
        applyDriveFraming()
        val camera = NavigationCamera(map.mapboxMap, map.camera, viewport)
        camera.registerNavigationCameraStateChangeObserver(cameraStateObserver)
        navigationCamera = camera
        map.gestures.addOnMoveListener(moveListener)
        map.gestures.addOnRotateListener(rotateListener)
        map.gestures.addOnScaleListener(scaleListener)
        map.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyDriveFraming() }
        map.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            loadedStyle = style
            val scale = DriveCamera.carModelScale
            map.location.apply {
                setLocationProvider(navigationLocationProvider)
                locationPuck = LocationPuck3D(
                    modelUri = "asset://car.glb",
                    modelScale = listOf(scale, scale, scale),
                    modelRotation = listOf(0f, 0f, DriveCamera.carModelYaw),
                    modelTranslation = listOf(0f, 0f, 0f),
                    modelElevationReference = ModelElevationReference.GROUND,
                    modelScaleMode = ModelScaleMode.VIEWPORT,
                    modelCastShadows = false,
                    modelReceiveShadows = false,
                    modelEmissiveStrength = 0.85f
                )
                puckBearingEnabled = true
                puckBearing = PuckBearing.COURSE
                enabled = true
                pulsingEnabled = false
                showAccuracyRing = false
            }
        }
        map.mapboxMap.subscribeMapLoadingError {
            showStatus(getString(R.string.status_map))
        }
    }

    private fun wireSearch() {
        findViewById<Button>(R.id.fromSearch).setOnClickListener { runSearch("from") }
        findViewById<Button>(R.id.toSearch).setOnClickListener { runSearch("to") }
        fromField.doAfterTextChanged {
            if (origin != null && it?.toString() != origin?.name) {
                origin = null
                clearRoute()
            }
        }
        toField.doAfterTextChanged {
            if (destination != null && it?.toString() != destination?.name) {
                destination = null
                clearRoute()
            }
        }
    }

    private fun runSearch(target: String) {
        resultTarget = target
        val field = if (target == "from") fromField else toField
        val seq = ++searchSeq
        hideKeyboard()
        search.search(field.text?.toString().orEmpty()) { result ->
            mainHandler.post {
                if (seq != searchSeq) return@post
                result.onSuccess { places -> showPlaces(places) }
                result.onFailure { err ->
                    results.removeAllViews()
                    when (err) {
                        is SearchLimit -> showStatus(getString(R.string.status_search_limit))
                        else -> showStatus(getString(R.string.status_search))
                    }
                }
            }
        }
    }

    private fun showPlaces(places: List<Place>) {
        results.removeAllViews()
        if (places.isEmpty()) {
            showStatus(getString(R.string.status_no_result))
            return
        }
        clearStatus()
        for (place in places) {
            val button = Button(this)
            button.text = if (place.detail.isBlank()) place.name else place.name + "\n" + place.detail
            button.isAllCaps = false
            button.setOnClickListener { choosePlace(place) }
            results.addView(button)
        }
    }

    private fun choosePlace(place: Place) {
        hideKeyboard()
        results.removeAllViews()
        if (resultTarget == "from") {
            origin = place
            fromField.setText(place.name)
        } else {
            destination = place
            toField.setText(place.name)
        }
        val start = origin
        val goal = destination
        if (start != null && goal != null) requestRoute(start, goal)
    }

    private fun requestRoute(start: Place, goal: Place) {
        if (kotlin.math.abs(start.lat - goal.lat) < 0.00008 && kotlin.math.abs(start.lon - goal.lon) < 0.00008) {
            showStatus(getString(R.string.status_same_point))
            return
        }
        val nav = navigationRef
        if (!navigationReady || nav == null) {
            showStatus(getString(R.string.status_route))
            return
        }
        guiding = false
        overviewLocked = false
        routeDrawn = false
        maneuverCard.visibility = android.view.View.GONE
        driveCard.visibility = android.view.View.GONE
        cameraTools.visibility = android.view.View.GONE
        val seq = ++routeSeq
        showStatus(getString(R.string.status_planning), error = false)
        val options = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .coordinatesList(listOf(Point.fromLngLat(start.lon, start.lat), Point.fromLngLat(goal.lon, goal.lat)))
            .language("hu")
            .voiceUnits(DirectionsCriteria.METRIC)
            .build()
        nav.requestRoutes(options, object : NavigationRouterCallback {
            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                if (seq != routeSeq) return
                mainHandler.post { showStatus(getString(R.string.status_route_cancel)) }
            }

            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                if (seq != routeSeq) return
                val network = reasons.any { failure ->
                    val thrown = failure.throwable
                    thrown is IOException || (failure.message ?: "").contains("resolve", ignoreCase = true)
                }
                mainHandler.post {
                    previewCard.visibility = android.view.View.GONE
                    showStatus(getString(if (network) R.string.status_route_network else R.string.status_route))
                }
            }

            override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                if (seq != routeSeq) return
                mainHandler.post {
                    if (routes.isEmpty()) {
                        showStatus(getString(R.string.status_route))
                        return@post
                    }
                    clearStatus()
                    nav.setNavigationRoutes(routes)
                    val route = routes.first().directionsRoute
                    val distance = route.distance()
                    val duration = route.duration()
                    previewMeta.text = ManeuverEvents.formatDistance(distance) + " · " +
                        ManeuverEvents.formatDuration(duration) + "\n" +
                        getString(R.string.arrival) + " " + clockAfter(duration)
                    previewCard.visibility = android.view.View.VISIBLE
                    maneuverCard.visibility = android.view.View.GONE
                    driveCard.visibility = android.view.View.GONE
                    voice.speak("utvonal_kesz", "utvonal_kesz", null, "kesz")
                }
            }
        })
    }

    private fun startGuidance() {
        if (!locationGranted) {
            showStatus(getString(R.string.status_permission))
            requestLocationPermission()
            return
        }
        if (!locationEnabled()) {
            showStatus(getString(R.string.status_gps_off))
            return
        }
        if (!hasFix) {
            awaitingGps = true
            showStatus(getString(R.string.status_gps))
            startLocationSession()
            mainHandler.postDelayed({
                if (awaitingGps && !hasFix) showStatus(getString(R.string.status_gps))
            }, 15000)
            return
        }
        guiding = true
        overviewLocked = false
        clearStatus()
        previewCard.visibility = android.view.View.GONE
        searchCard.visibility = android.view.View.GONE
        maneuverCard.visibility = android.view.View.VISIBLE
        driveCard.visibility = android.view.View.VISIBLE
        cameraTools.visibility = android.view.View.VISIBLE
        recenterButton.visibility = android.view.View.GONE
        overviewButton.setText(R.string.overview)
        stepDistance.text = ""
        maneuverText.text = getString(R.string.status_follow)
        maneuverArrow.rotation = 0f
        applyDriveFraming()
        viewportDataSource?.evaluate()
        navigationCamera?.requestNavigationCameraToFollowing()
        voice.speak("indulas", "indulas", null, "indulas")
    }

    private fun stopGuidance() {
        guiding = false
        overviewLocked = false
        cameraFollows = false
        maneuverCard.visibility = android.view.View.GONE
        driveCard.visibility = android.view.View.GONE
        cameraTools.visibility = android.view.View.GONE
        searchCard.visibility = android.view.View.VISIBLE
        if (previewMeta.text.isNotBlank()) previewCard.visibility = android.view.View.VISIBLE
        clearManeuverArrow()
        navigationCamera?.requestNavigationCameraToOverview()
    }

    private fun recenter() {
        if (!guiding) return
        overviewLocked = false
        applyDriveFraming()
        viewportDataSource?.evaluate()
        navigationCamera?.requestNavigationCameraToFollowing()
    }

    private fun toggleOverview() {
        if (!guiding) return
        val state = navigationCamera?.state
        val inOverview = state == NavigationCameraState.OVERVIEW ||
            state == NavigationCameraState.TRANSITION_TO_OVERVIEW
        if (inOverview) recenter() else {
            overviewLocked = true
            navigationCamera?.requestNavigationCameraToOverview()
        }
    }

    private fun releaseFollow() {
        if (!guiding) return
        overviewLocked = false
        navigationCamera?.requestNavigationCameraToIdle()
    }

    private fun clearRoute() {
        routeSeq += 1
        guiding = false
        overviewLocked = false
        cameraFollows = false
        routeDrawn = false
        acceptedRouteId = null
        previewCard.visibility = android.view.View.GONE
        maneuverCard.visibility = android.view.View.GONE
        driveCard.visibility = android.view.View.GONE
        cameraTools.visibility = android.view.View.GONE
        navigationRef?.setNavigationRoutes(emptyList())
        viewportDataSource?.clearRouteData()
        viewportDataSource?.evaluate()
        clearDrawnRoute()
    }

    private fun renderRouteProgress(progress: RouteProgress) {
        val style = loadedStyle ?: return
        routeLineApi?.updateWithRouteProgress(progress) { value ->
            routeLineView?.renderRouteLineUpdate(style, value)
        }
        val arrow = routeArrowApi?.addUpcomingManeuverArrow(progress) ?: return
        routeArrowView?.renderManeuverUpdate(style, arrow)
    }

    private fun clearManeuverArrow() {
        val style = loadedStyle ?: return
        val cleared = routeArrowApi?.clearArrows() ?: return
        routeArrowView?.render(style, cleared)
    }

    private fun clearDrawnRoute() {
        val style = loadedStyle ?: return
        routeLineApi?.clearRouteLine { value ->
            mainHandler.post { routeLineView?.renderClearRouteLineValue(style, value) }
        }
        clearManeuverArrow()
    }

    private fun applyDriveFraming() {
        val height = mapView?.height?.toDouble()?.takeIf { it > 0.0 }
            ?: resources.displayMetrics.heightPixels.toDouble()
        val density = resources.displayMetrics.density
        val follow = DriveCamera.followingPadding(height, density)
        val overview = DriveCamera.overviewPadding(density)
        viewportDataSource?.followingPadding = EdgeInsets(follow.top, follow.left, follow.bottom, follow.right)
        viewportDataSource?.overviewPadding = EdgeInsets(overview.top, overview.left, overview.bottom, overview.right)
    }

    private fun routeColors(line: Int, casing: Int): RouteLineColorResources {
        val traveled = ContextCompat.getColor(this, R.color.route_traveled)
        val traveledCasing = ContextCompat.getColor(this, R.color.route_traveled_casing)
        return RouteLineColorResources.Builder()
            .routeDefaultColor(line)
            .routeLowCongestionColor(line)
            .routeModerateCongestionColor(line)
            .routeHeavyCongestionColor(line)
            .routeSevereCongestionColor(line)
            .routeUnknownCongestionColor(line)
            .routeCasingColor(casing)
            .routeLineTraveledColor(traveled)
            .routeLineTraveledCasingColor(traveledCasing)
            .build()
    }

    private fun drawRoutes(routes: List<NavigationRoute>) {
        val style = loadedStyle ?: return
        val api = routeLineApi ?: return
        val view = routeLineView ?: return
        api.setNavigationRoutes(routes) { data ->
            view.renderRouteDrawData(style, data)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationSession() {
        if (!locationGranted || !navigationReady) return
        navigationRef?.startTripSession()
    }

    private fun requestLocationPermission() {
        val needed = ArrayList<String>()
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        locationGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (needed.isEmpty() || (locationGranted && (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)))) {
            if (locationGranted) startLocationSession()
            return
        }
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun locationEnabled(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gps = try { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (err: Exception) { false }
        val network = try { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (err: Exception) { false }
        return gps || network
    }

    private fun showStatus(text: String, error: Boolean = true) {
        statusText.setTextColor(androidx.core.content.ContextCompat.getColor(this, if (error) R.color.nav_error else R.color.nav_ok))
        statusText.text = text
        statusText.visibility = android.view.View.VISIBLE
    }

    private fun clearStatus() {
        statusText.text = ""
        statusText.visibility = android.view.View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(fromField.windowToken, 0)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun clockAfter(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0) return "--:--"
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis() + (seconds * 1000.0).toLong()
        return String.format(Locale.US, "%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        mapView?.gestures?.removeOnMoveListener(moveListener)
        mapView?.gestures?.removeOnRotateListener(rotateListener)
        mapView?.gestures?.removeOnScaleListener(scaleListener)
        navigationCamera?.unregisterNavigationCameraStateChangeObserver(cameraStateObserver)
        navigationRef?.let { nav ->
            nav.unregisterRoutesObserver(routesObserver)
            nav.unregisterLocationObserver(locationObserver)
            nav.unregisterRouteProgressObserver(progressObserver)
            nav.stopTripSession()
        }
        navigationRef = null
        navigationReady = false
        if (MapboxNavigationProvider.isCreated()) MapboxNavigationProvider.destroy()
        routeLineApi?.cancel()
        routeLineView?.cancel()
        voice.release()
        super.onDestroy()
    }
}
