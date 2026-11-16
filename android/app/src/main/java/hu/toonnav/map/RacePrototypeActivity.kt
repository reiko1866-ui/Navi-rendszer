package hu.toonnav.map

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Separate driving preview. It follows a live fix on the active route when one
 * exists, and it does not replace the map screen.
 */
class RacePrototypeActivity : ComponentActivity() {
    private val guidance = GuidanceSession()
    private val routes = OsrmClient()
    private val reroutes = RerouteSession()
    private val voice by lazy { VoiceGuide(assets) }
    private val hudPrefs by lazy { getSharedPreferences("toonnav-hud", MODE_PRIVATE) }
    private lateinit var speedView: TextView
    private lateinit var maneuverView: TextView
    private lateinit var remainingView: TextView
    private lateinit var etaView: TextView
    private lateinit var statusView: TextView
    private lateinit var voiceButton: Button
    private lateinit var nightButton: Button
    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private var renderer: RaceRenderer? = null
    private var updatesStarted = false
    private var carIndex = 0
    private var night = false
    private var voiceOn = true
    private var usingLiveRoute = false
    private var offRouteSpoken = false
    private var routeSeq = 0
    private var plan: OsrmRoutePlan? = null
    private var route = emptyList<OsrmPoint>()
    private var samples = emptyList<RoadSample>()
    private var roadKinds = emptyList<RoadKind>()
    private var elevations = FloatArray(0)
    private var terrain: ((Double, Double) -> Float)? = null
    private var windowEnd = 0.0
    private var buildingRoad = false
    @Volatile var travelAlong = 8.0

    @Volatile var followLongitude = Double.NaN
    @Volatile var followLatitude = Double.NaN
    @Volatile var followSpeed = 0.0
    @Volatile var followHasSpeed = false
    @Volatile var followElapsed = 0L
    @Volatile var followLive = false
    @Volatile var frameFps = Double.NaN
    @Volatile var frameMs = Double.NaN
    @Volatile var roadImages: Map<String, ByteArray> = emptyMap()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            acceptFix(location)
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_race_prototype)
        speedView = findViewById(R.id.raceSpeed)
        maneuverView = findViewById(R.id.raceManeuver)
        remainingView = findViewById(R.id.raceRemaining)
        etaView = findViewById(R.id.raceEta)
        statusView = findViewById(R.id.raceStatus)
        voiceButton = findViewById(R.id.raceVoice)
        nightButton = findViewById(R.id.raceNight)
        voiceOn = hudPrefs.getBoolean("voice", true)
        night = hudPrefs.getBoolean("night", false)
        voice.enabled = voiceOn
        Thread { voice.load() }.start()
        val requested = intent.getStringExtra(EXTRA_CAR)
        val selected = GarageFleet.shown(requested ?: GarageStore(this).carId())
        carIndex = GarageFleet.cars.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        plan = DriveSessionMemory.plan
        val live = DriveSessionMemory.routePoints
        usingLiveRoute = live.size >= 2 && plan != null
        route = if (usingLiveRoute) live.toList() else RaceTrack.samplePreview()
        prepareSamples()
        plan?.let { guidance.setPlan(it) }
        findViewById<Button>(R.id.raceStop).setOnClickListener { confirmStop() }
        findViewById<Button>(R.id.raceBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.raceGarage).setOnClickListener {
            startActivity(Intent(this, GarageActivity::class.java))
        }
        voiceButton.setOnClickListener { toggleVoice() }
        nightButton.setOnClickListener { toggleNight() }
        findViewById<Button>(R.id.raceLivery).setOnClickListener { cycleLivery() }
        refreshControls()
        paintHud(null)
        val thread = HandlerThread("toonnav-filament", Process.THREAD_PRIORITY_DISPLAY)
        thread.start()
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler
        val surface = findViewById<SurfaceView>(R.id.raceSurface)
        val routeNow = route
        handler.post {
            val carBytes = assets.open("models/formula-car.glb").use { it.readBytes() }
            val skyBytes = assets.open("models/noon-sky.hdr").use { it.readBytes() }
            val images = RaceGlb.roadImages { name -> assets.open("models/road/$name.ktx2").use { it.readBytes() } }
            roadImages = images
            val shown = RaceTrack.window(samples, 0.0)
            windowEnd = shown.lastOrNull()?.along ?: 0.0
            val roadBytes = RaceGlb.encode(
                RaceModel(emptyList(), RaceGeometry.worldModel(shown, terrain), RaceGeometry.materials()),
                images
            )
            val created = RaceRenderer.start(
                carBytes, roadBytes, skyBytes, routeNow, samples, selected, night, surface, handler, this
            )
            renderer = created
            if (created == null) runOnUiThread { statusView.text = getString(R.string.race_engine_failed) }
            else if (usingLiveRoute) requestElevation(routeSeq)
        }
    }

    override fun onStart() {
        super.onStart()
        startLocationUpdates()
    }

    override fun onStop() {
        stopLocationUpdates()
        rememberProgress()
        super.onStop()
    }

    override fun onDestroy() {
        renderer?.surfaceHelper?.detach()
        val handler = renderHandler
        val thread = renderThread
        if (handler != null && thread != null) {
            val done = CountDownLatch(1)
            handler.post {
                renderer?.destroy()
                renderer = null
                done.countDown()
            }
            done.await(3, TimeUnit.SECONDS)
            thread.quitSafely()
        }
        super.onDestroy()
    }

    private fun confirmStop() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.stop_confirm_title)
            .setMessage(R.string.stop_confirm_message)
            .setPositiveButton(R.string.stop_nav) { _, _ ->
                voice.halt()
                DriveSessionMemory.abandon = true
                finish()
            }
            .setNegativeButton(R.string.stop_cancel, null)
            .show()
    }

    private fun cycleLivery() {
        val next = GarageFleet.cars[(carIndex + 1) % GarageFleet.cars.size]
        carIndex = GarageFleet.cars.indexOf(next)
        val storedNumber = GarageStore(this).number() ?: next.defaultNumber
        GarageStore(this).write(next.id, storedNumber)
        renderHandler?.post { renderer?.applyLivery(next) }
        paintHud(guidance.refreshAge(fixAge()))
    }

    private fun toggleVoice() {
        voiceOn = !voiceOn
        voice.enabled = voiceOn
        if (!voiceOn) voice.silence()
        hudPrefs.edit().putBoolean("voice", voiceOn).apply()
        refreshControls()
    }

    private fun toggleNight() {
        night = !night
        hudPrefs.edit().putBoolean("night", night).apply()
        val dark = night
        renderHandler?.post { renderer?.setNight(dark) }
        refreshControls()
    }

    private fun refreshControls() {
        voiceButton.text = getString(if (voiceOn) R.string.voice_on else R.string.voice_off)
        nightButton.text = getString(if (night) R.string.day_mode else R.string.night_mode)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (updatesStarted) return
        val fine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var registered = 0
        for (provider in providers) {
            val enabled = try { manager.isProviderEnabled(provider) } catch (err: Exception) { false }
            if (!enabled) continue
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    val request = android.location.LocationRequest.Builder(1_000L)
                        .setQuality(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
                        .setMinUpdateIntervalMillis(500L)
                        .build()
                    manager.requestLocationUpdates(provider, request, mainExecutor, locationListener)
                } else {
                    manager.requestLocationUpdates(provider, 1_000L, 0f, locationListener, Looper.getMainLooper())
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

    private fun stopLocationUpdates() {
        if (!updatesStarted) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        manager.removeUpdates(locationListener)
        updatesStarted = false
    }

    private fun acceptFix(location: Location) {
        val age = GpsFixGate.ageMs(
            location.elapsedRealtimeNanos,
            SystemClock.elapsedRealtimeNanos(),
            location.time,
            System.currentTimeMillis()
        )
        val mock = if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider
        if (GpsFixGate.verdict(location.latitude, location.longitude, age, mock) != GpsSampleVerdict.LIVE) return
        followLongitude = location.longitude
        followLatitude = location.latitude
        followHasSpeed = location.hasSpeed()
        followSpeed = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        followElapsed = SystemClock.elapsedRealtime()
        followLive = true
        val cue = if (usingLiveRoute) {
            guidance.onFix(
                location.longitude,
                location.latitude,
                SystemClock.elapsedRealtime(),
                age,
                if (location.hasAccuracy()) location.accuracy.toDouble() else null
            )
        } else {
            null
        }
        paintHud(cue)
        if (cue != null) handleCue(cue)
    }

    private fun handleCue(cue: GuidanceCue) {
        val along = cue.totalMeters - cue.remainingMeters
        if (along.isFinite()) {
            DriveSessionMemory.traveledMeters = along.coerceIn(0.0, cue.totalMeters.coerceAtLeast(0.0))
        }
        when {
            cue.arrived -> statusView.text = getString(R.string.status_arrived)
            cue.offRouteConfirmed -> {
                if (!offRouteSpoken) {
                    voice.halt()
                    voice.speak("rossz_irany", "rossz_irany", null, "letérés")
                    offRouteSpoken = true
                }
                rerouteFromHere()
            }
            cue.trust == GpsTrust.LOST -> statusView.text = getString(R.string.status_gps)
            cue.trust == GpsTrust.UNUSABLE || cue.trust == GpsTrust.INACCURATE -> statusView.text = getString(R.string.status_gps_poor)
            else -> {
                offRouteSpoken = false
                paintFrame()
            }
        }
        if (!cue.arrived && !cue.progressHeld && !cue.offRouteConfirmed) speakCue(cue)
    }

    private fun speakCue(cue: GuidanceCue) {
        val event = ManeuverEvents.eventId(cue.maneuverType, cue.maneuverModifier)
        if (event.isEmpty()) return
        val kind = RoadClassifier.classify(cue.maneuverType, cue.roadClasses)
        voice.speak("turn", event, cue.maneuverDistanceMeters, event + ":" + cue.maneuverLabel, RoadGuidance.leadMeters(kind, cue.maneuverType))
    }

    private fun rerouteFromHere() {
        val goal = DriveSessionMemory.destination ?: return
        val now = SystemClock.elapsedRealtime()
        if (!followLive || !reroutes.allow(now, confirmed = true, guiding = true)) {
            statusView.text = if (reroutes.cooling(now)) {
                getString(R.string.status_reroute_unavailable)
            } else {
                getString(R.string.status_off_route)
            }
            return
        }
        if (reroutes.inFlight) {
            statusView.text = getString(R.string.status_reroute_working)
            return
        }
        val seq = ++routeSeq
        reroutes.started()
        statusView.text = getString(R.string.status_reroute_working)
        routes.requestRoute(followLongitude, followLatitude, goal.lon, goal.lat) { result ->
            runOnUiThread {
                if (seq != routeSeq || isDestroyed) return@runOnUiThread
                when (result) {
                    is OsrmResult.Ready -> adoptRoute(result.plan)
                    else -> {
                        reroutes.failed(SystemClock.elapsedRealtime())
                        statusView.text = getString(R.string.status_reroute_unavailable)
                    }
                }
            }
        }
    }

    private fun adoptRoute(next: OsrmRoutePlan) {
        val json = RouteLineJson.feature(next.coordinates) ?: run {
            reroutes.failed(SystemClock.elapsedRealtime())
            statusView.text = getString(R.string.status_reroute_unavailable)
            return
        }
        plan = next
        route = next.coordinates
        elevations = FloatArray(0)
        terrain = null
        prepareSamples()
        usingLiveRoute = route.size >= 2
        guidance.setPlan(next)
        DriveSessionMemory.hold(next, json, route, 0.0, DriveSessionMemory.origin, DriveSessionMemory.destination)
        reroutes.succeeded(SystemClock.elapsedRealtime())
        offRouteSpoken = false
        voice.halt()
        voice.speak("reroute", "reroute", null, "ujratervezes")
        publishRoad(0.0, keepMotion = false)
        requestElevation(routeSeq)
        statusView.text = ""
        paintHud(guidance.refreshAge(fixAge()))
    }

    private fun prepareSamples() {
        roadKinds = if (usingLiveRoute) RouteRoadClass.kinds(route, plan?.steps.orEmpty()) else emptyList()
        samples = RaceTrack.samplesFor(route, roadKinds, elevations)
    }

    private fun publishRoad(along: Double, keepMotion: Boolean) {
        val shown = RaceTrack.window(samples, along)
        windowEnd = shown.lastOrNull()?.along ?: 0.0
        val field = terrain
        val images = roadImages
        val line = route
        val guide = samples
        val handler = renderHandler
        if (handler == null || images.isEmpty()) {
            buildingRoad = false
            return
        }
        handler.post {
            val bytes = RaceGlb.encode(
                RaceModel(emptyList(), RaceGeometry.worldModel(shown, field), RaceGeometry.materials()),
                images
            )
            renderer?.replaceRoad(bytes, line, guide, keepMotion)
            buildingRoad = false
        }
    }

    fun slideWindow(along: Double) {
        if (buildingRoad || samples.size < 2) return
        val routeEnd = samples.last().along
        if (windowEnd >= routeEnd - 1.0) return
        if (along < windowEnd - 420.0 || along >= routeEnd - 30.0) return
        buildingRoad = true
        publishRoad(along, keepMotion = true)
    }

    private fun requestElevation(seq: Int) {
        if (!usingLiveRoute || route.size < 2) return
        val line = route.toList()
        Thread {
            val field = TerrainTiles.load(line) ?: return@Thread
            runOnUiThread {
                if (seq != routeSeq || isDestroyed || route.size != line.size) return@runOnUiThread
                terrain = field
                val origin = line.first()
                elevations = FloatArray(line.size) { index ->
                    val local = GeoMeters.local(origin, line[index])
                    field(local.first, local.second)
                }
                prepareSamples()
                publishRoad(travelAlong, keepMotion = true)
            }
        }.start()
    }

    private fun rememberProgress() {
        val current = plan ?: return
        if (!usingLiveRoute) return
        DriveSessionMemory.hold(
            current,
            DriveSessionMemory.routeJson,
            route,
            DriveSessionMemory.traveledMeters,
            DriveSessionMemory.origin,
            DriveSessionMemory.destination
        )
    }

    private fun paintHud(cue: GuidanceCue?) {
        val speed = if (!followLive) null else GpsReadout.speedKmh(followSpeed.toFloat(), followHasSpeed, fixAge())
        speedView.text = if (speed == null) "— ${getString(R.string.speed_unit)}" else "$speed ${getString(R.string.speed_unit)}"
        if (cue == null) {
            maneuverView.text = GarageFleet.cars[carIndex].name
            remainingView.text = getString(if (usingLiveRoute) R.string.race_live_route else R.string.race_sample_route)
            etaView.text = getString(if (usingLiveRoute) R.string.race_waiting_gps else R.string.race_preview_note)
        } else {
            maneuverView.text = cue.maneuverLabel + "  " + ManeuverEvents.formatDistance(cue.maneuverDistanceMeters)
            remainingView.text = getString(R.string.remaining) + " " + ManeuverEvents.formatDistance(cue.remainingMeters)
            etaView.text = getString(R.string.arrival) + " " + etaText(cue)
        }
        paintFrame()
    }

    fun publishFrame(fps: Double, milliseconds: Double) {
        frameFps = fps
        frameMs = milliseconds
        runOnUiThread { if (!isDestroyed) paintFrame() }
    }

    private fun paintFrame() {
        if (!frameFps.isFinite()) return
        val base = statusView.text?.toString().orEmpty().lineSequence().firstOrNull { !it.contains("fps") }.orEmpty()
        val fps = "${frameFps.roundToInt()} fps, ${"%.1f".format(frameMs)} ms"
        statusView.text = if (base.isBlank()) fps else "$base\n$fps"
    }

    private fun etaText(cue: GuidanceCue): String {
        val offset = NavigationEta.offsetMillis(plan?.durationSeconds, NavigationEta.fraction(cue.remainingMeters, cue.totalMeters))
            ?: return "--:--"
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis() + offset
        return String.format(Locale.US, "%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    private fun fixAge(): Long {
        if (!followLive) return -1L
        return SystemClock.elapsedRealtime() - followElapsed
    }

    companion object {
        const val EXTRA_CAR = "car"
        init {
            Utils.init()
            Gltfio.init()
        }
    }
}

private class RaceRenderer(
    private val engine: Engine,
    private val swapView: View,
    private val renderer: Renderer,
    private val scene: Scene,
    private var skybox: Skybox,
    private val indirect: IndirectLight,
    private val light: Int,
    private val camera: Camera,
    private val cameraEntity: Int,
    private val probe: RaceLighting.Probe?,
    private val daySky: Skybox,
    private val carAsset: FilamentAsset,
    private var roadAsset: FilamentAsset,
    private val loader: AssetLoader,
    private val resources: ResourceLoader,
    private val materials: UbershaderProvider,
    private var route: List<OsrmPoint>,
    private var samples: List<RoadSample>,
    private val host: RacePrototypeActivity
) {
    var surfaceHelper: UiHelper? = null
    var swapChain: SwapChain? = null
    private var closed = false
    private var width = 0
    private var height = 0
    private var lastFrame = 0L
    private var frameSum = 0.0
    private var frameCount = 0
    private var posted = 0L
    private var heldEast = 0.0
    private var heldNorth = 0.0
    private var heldBearing = 0.0
    private var heldAlong = 8.0
    private var held = false
    private var smoothBearing = 0.0
    private var bearingReady = false
    private var nightSky: Skybox? = null
    private val choreographer = Choreographer.getInstance()
    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            draw(frameTimeNanos)
            if (!closed) choreographer.postFrameCallback(this)
        }
    }

    fun attach(surface: Surface) {
        if (closed) return
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(surface)
    }

    fun resize(nextWidth: Int, nextHeight: Int) {
        width = nextWidth
        height = nextHeight
    }

    fun applyLivery(car: GarageFleet.Car) {
        for (instance in carAsset.instance.materialInstances) {
            val slot = RaceLivery.slot(instance.name) ?: continue
            val color = RaceLivery.color(slot, car)
            instance.setParameter(
                "baseColorFactor",
                Colors.RgbaType.SRGB,
                ((color ushr 16) and 0xFF) / 255f,
                ((color ushr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f,
                1f
            )
        }
    }

    fun setNight(night: Boolean) {
        val fog = swapView.fogOptions
        fog.color = floatArrayOf(if (night) 0.02f else 0.62f, if (night) 0.03f else 0.72f, if (night) 0.05f else 0.86f)
        swapView.fogOptions = fog
        val sun = engine.lightManager.getInstance(light)
        engine.lightManager.setIntensity(sun, if (night) 6_000f else 100_000f)
        probe?.indirect?.setIntensity(if (night) 0.08f else 1f)
        camera.setExposure(if (night) 8f else 16f, if (night) 1f / 30f else 1f / 125f, if (night) 800f else 100f)
        if (night) {
            val dark = nightSky ?: Skybox.Builder().color(0.02f, 0.025f, 0.04f, 1f).build(engine).also { nightSky = it }
            scene.skybox = dark
        } else {
            scene.skybox = daySky
        }
    }

    fun replaceRoad(bytes: ByteArray, nextRoute: List<OsrmPoint>, nextSamples: List<RoadSample>, keepMotion: Boolean = false) {
        if (closed) return
        val asset = loadAsset(bytes) ?: return
        scene.removeEntities(roadAsset.entities)
        loader.destroyAsset(roadAsset)
        roadAsset = asset
        scene.addEntities(asset.entities)
        route = nextRoute
        samples = nextSamples
        if (!keepMotion) {
            held = false
            bearingReady = false
        }
    }

    fun destroy() {
        closed = true
        choreographer.removeFrameCallback(callback)
        loader.destroyAsset(carAsset)
        loader.destroyAsset(roadAsset)
        resources.destroy()
        loader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        scene.skybox = null
        scene.indirectLight = null
        nightSky?.let { if (it !== daySky) engine.destroySkybox(it) }
        engine.destroySkybox(daySky)
        if (probe != null) engine.destroyIndirectLight(probe.indirect) else engine.destroyIndirectLight(indirect)
        probe?.reflections?.let { engine.destroyTexture(it) }
        probe?.environment?.let { engine.destroyTexture(it) }
        engine.destroyEntity(light)
        swapChain?.let { engine.destroySwapChain(it) }
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroyView(swapView)
        engine.destroyRenderer(renderer)
        engine.destroyScene(scene)
        engine.destroy()
    }

    private fun draw(frameTimeNanos: Long) {
        if (closed) return
        val chain = swapChain ?: return
        if (width < 2 || height < 2) return
        val elapsed = if (lastFrame == 0L) 0.0 else (frameTimeNanos - lastFrame) / 1_000_000_000.0
        lastFrame = frameTimeNanos
        val step = elapsed.coerceIn(0.0, 0.05)
        val age = if (host.followLive) android.os.SystemClock.elapsedRealtime() - host.followElapsed else -1L
        val motion = if (host.followLive && route.size >= 2) {
            RouteMotion.pose(route, host.followLongitude, host.followLatitude, host.followSpeed, host.followHasSpeed, age, true)
        } else {
            null
        }
        val east: Double
        val north: Double
        val bearing: Double
        if (motion != null) {
            val local = GeoMeters.local(route.first(), OsrmPoint(motion.lon, motion.lat))
            east = local.first
            north = local.second
            bearing = motion.bearingDegrees
            heldAlong = motion.alongMeters
            heldEast = east
            heldNorth = north
            heldBearing = bearing
            held = true
        } else if (held) {
            east = heldEast
            north = heldNorth
            bearing = heldBearing
        } else {
            val here = RaceTrack.pointAt(samples, 8.0)
            heldAlong = here.along
            east = here.east
            north = here.north
            bearing = RaceTrack.bearing(route, samples, heldAlong)
        }
        val surface = RaceTrack.pointAt(samples, heldAlong).elevation.toDouble() + RaceGeometry.deckTop
        host.travelAlong = heldAlong
        host.slideWindow(heldAlong)
        val turned = dampBearing(bearing, step)
        camera.setProjection(RaceCamera.fovDegrees, width.toDouble() / height.toDouble(), 0.08, 1200.0, Camera.Fov.VERTICAL)
        camera.setModelMatrix(RaceCamera.cameraModel(east, north, turned, surface))
        val transform = engine.transformManager
        val car = transform.getInstance(carEntity())
        if (car != 0) transform.setTransform(car, RaceCamera.carTransform(east, north, turned, surface))
        swapView.viewport = Viewport(0, 0, width, height)
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(swapView)
            renderer.endFrame()
        }
        if (step > 0.0) {
            frameSum += step * 1000.0
            frameCount += 1
            val now = android.os.SystemClock.uptimeMillis()
            if (frameCount >= 12 && now - posted > 250L) {
                val average = frameSum / frameCount
                host.publishFrame(1000.0 / average, average)
                frameSum = 0.0
                frameCount = 0
                posted = now
            }
        }
    }

    private fun dampBearing(target: Double, dt: Double): Double {
        if (!bearingReady || dt <= 0.0) {
            bearingReady = true
            smoothBearing = target
            return target
        }
        var delta = target - smoothBearing
        delta = (delta + 540.0) % 360.0 - 180.0
        val follow = 1.0 - kotlin.math.exp(-dt / 0.12)
        val step = (delta * follow).coerceIn(-170.0 * dt, 170.0 * dt)
        smoothBearing = (smoothBearing + step + 360.0) % 360.0
        return smoothBearing
    }

    private fun carEntity(): Int {
        val named = carAsset.getFirstEntityByName("formula-car")
        return if (named != 0) named else carAsset.root
    }

    private fun loadAsset(bytes: ByteArray): FilamentAsset? {
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(bytes)
        buffer.flip()
        val asset = loader.createAsset(buffer) ?: return null
        resources.loadResources(asset)
        asset.releaseSourceData()
        return asset
    }

    companion object {
        fun start(
            carBytes: ByteArray,
            roadBytes: ByteArray,
            skyBytes: ByteArray,
            route: List<OsrmPoint>,
            samples: List<RoadSample>,
            car: GarageFleet.Car,
            night: Boolean,
            surface: SurfaceView,
            handler: Handler,
            host: RacePrototypeActivity
        ): RaceRenderer? {
            val engine = try {
                Engine.Builder().backend(Engine.Backend.OPENGL).build()
            } catch (failed: Throwable) {
                return null
            }
            return try {
                val provider = UbershaderProvider(engine)
                val loader = AssetLoader(engine, provider, EntityManager.get())
                val resources = ResourceLoader(engine)
                val scene = engine.createScene()
                val view = engine.createView()
                view.scene = scene
                view.blendMode = View.BlendMode.OPAQUE
                view.antiAliasing = View.AntiAliasing.FXAA
                view.setShadowingEnabled(true)
                view.setPostProcessingEnabled(true)
                val ao = view.ambientOcclusionOptions
                ao.enabled = true
                ao.quality = View.QualityLevel.MEDIUM
                view.ambientOcclusionOptions = ao
                val resolution = view.dynamicResolutionOptions
                resolution.enabled = true
                resolution.minScale = 0.7f
                resolution.maxScale = 1f
                resolution.quality = View.QualityLevel.MEDIUM
                view.dynamicResolutionOptions = resolution
                val fog = view.fogOptions
                fog.enabled = true
                fog.distance = 140f
                fog.maximumOpacity = 0.62f
                fog.density = 0.028f
                fog.height = 6f
                fog.heightFalloff = 2.5f
                fog.color = floatArrayOf(0.62f, 0.72f, 0.86f)
                view.fogOptions = fog
                val probe = RaceLighting.create(engine, skyBytes)
                val sky = probe?.sky ?: Skybox.Builder().color(0.62f, 0.72f, 0.86f, 1f).build(engine)
                val fallback = FloatArray(27)
                fallback[0] = 1.1f
                fallback[1] = 1.15f
                fallback[2] = 1.25f
                val indirect = probe?.indirect ?: IndirectLight.Builder()
                    .irradiance(3, fallback)
                    .intensity(30_000f)
                    .build(engine)
                scene.skybox = sky
                scene.indirectLight = indirect
                val light = EntityManager.get().create()
                LightManager.Builder(LightManager.Type.SUN)
                    .color(1.0f, 0.97f, 0.92f)
                    .intensity(100_000f)
                    .direction(RaceLighting.sunX, RaceLighting.sunY, RaceLighting.sunZ)
                    .castShadows(true)
                    .sunAngularRadius(0.45f)
                    .build(engine, light)
                scene.addEntity(light)
                val cameraEntity = EntityManager.get().create()
                val camera = engine.createCamera(cameraEntity)
                camera.setExposure(16f, 1f / 125f, 100f)
                view.camera = camera
                val created = RaceRenderer(
                    engine, view, engine.createRenderer(), scene, sky, indirect, light, camera, cameraEntity,
                    probe, sky,
                    load(loader, resources, carBytes) ?: throw IllegalStateException("car"),
                    load(loader, resources, roadBytes) ?: throw IllegalStateException("road"),
                    loader, resources, provider, route, samples, host
                )
                scene.addEntities(created.carAsset.entities)
                scene.addEntities(created.roadAsset.entities)
                created.applyLivery(car)
                if (night) created.setNight(true)
                val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
                created.surfaceHelper = helper
                helper.renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(nativeWindow: Surface) {
                        handler.post { created.attach(nativeWindow) }
                    }

                    override fun onDetachedFromSurface() {
                        handler.post {
                            if (created.closed) return@post
                            created.swapChain?.let { engine.destroySwapChain(it) }
                            created.swapChain = null
                        }
                    }

                    override fun onResized(width: Int, height: Int) {
                        handler.post { created.resize(width, height) }
                    }
                }
                host.runOnUiThread { helper.attachTo(surface) }
                created.choreographer.postFrameCallback(created.callback)
                created
            } catch (failed: Throwable) {
                engine.destroy()
                null
            }
        }

        private fun load(loader: AssetLoader, resources: ResourceLoader, bytes: ByteArray): FilamentAsset? {
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(bytes)
            buffer.flip()
            val asset = loader.createAsset(buffer) ?: return null
            resources.loadResources(asset)
            asset.releaseSourceData()
            return asset
        }
    }
}
