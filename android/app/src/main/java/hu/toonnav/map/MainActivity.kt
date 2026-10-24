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
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
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
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import java.io.IOException
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
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
    private lateinit var maneuverText: TextView
    private lateinit var remainText: TextView
    private lateinit var etaText: TextView

    private var mapView: MapView? = null
    private var viewportDataSource: MapboxNavigationViewportDataSource? = null
    private var navigationCamera: NavigationCamera? = null
    private var routeLineApi: MapboxRouteLineApi? = null
    private var routeLineView: MapboxRouteLineView? = null
    private var loadedStyle: Style? = null
    private var navigationRef: MapboxNavigation? = null
    private var navigationReady = false

    private var origin: Place? = null
    private var destination: Place? = null
    private var resultTarget = "to"
    private var searchSeq = 0
    private var routeSeq = 0
    private var guiding = false
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
            navigationLocationProvider.changePosition(enhanced, locationMatcherResult.keyPoints)
            viewportDataSource?.onLocationChanged(enhanced)
            viewportDataSource?.evaluate()
            if (guiding) navigationCamera?.requestNavigationCameraToFollowing()
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
        maneuverText.text = instruction
        remainText.text = getString(R.string.remaining) + " " +
            ManeuverEvents.formatDistance(progress.distanceRemaining.toDouble()) + " · " +
            ManeuverEvents.formatDuration(progress.durationRemaining)
        etaText.text = getString(R.string.arrival) + " " + clockAfter(progress.durationRemaining)
        val eventId = ManeuverEvents.eventId(type, modifier)
        val stepIndex = progress.currentLegProgress?.currentStepProgress?.stepIndex ?: 0
        voice.speak("turn", eventId, stepMeters, instruction + "@" + stepIndex)
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
        settingsButton.setOnClickListener { openAppSettings() }
        ensureNavigation()
        requestLocationPermission()
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
        maneuverText = findViewById(R.id.maneuverText)
        remainText = findViewById(R.id.remainText)
        etaText = findViewById(R.id.etaText)
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
        map.logo.marginBottom = 220f * density
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(this).build())
        viewportDataSource = MapboxNavigationViewportDataSource(map.mapboxMap)
        val edge = 40.0 * density
        val bottom = 260.0 * density
        viewportDataSource?.overviewPadding = EdgeInsets(72.0 * density, edge, bottom, edge)
        viewportDataSource?.followingPadding = EdgeInsets(140.0 * density, edge, bottom, edge)
        navigationCamera = NavigationCamera(map.mapboxMap, map.camera, viewportDataSource!!)
        map.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            loadedStyle = style
            map.location.apply {
                setLocationProvider(navigationLocationProvider)
                locationPuck = createDefault2DPuck()
                enabled = true
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
        routeDrawn = false
        maneuverCard.visibility = android.view.View.GONE
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
        clearStatus()
        previewCard.visibility = android.view.View.GONE
        searchCard.visibility = android.view.View.GONE
        maneuverCard.visibility = android.view.View.VISIBLE
        maneuverText.text = getString(R.string.status_follow)
        navigationCamera?.requestNavigationCameraToFollowing()
        voice.speak("indulas", "indulas", null, "indulas")
    }

    private fun stopGuidance() {
        guiding = false
        maneuverCard.visibility = android.view.View.GONE
        searchCard.visibility = android.view.View.VISIBLE
        if (previewMeta.text.isNotBlank()) previewCard.visibility = android.view.View.VISIBLE
        navigationCamera?.requestNavigationCameraToOverview()
    }

    private fun clearRoute() {
        routeSeq += 1
        guiding = false
        routeDrawn = false
        acceptedRouteId = null
        previewCard.visibility = android.view.View.GONE
        maneuverCard.visibility = android.view.View.GONE
        navigationRef?.setNavigationRoutes(emptyList())
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
