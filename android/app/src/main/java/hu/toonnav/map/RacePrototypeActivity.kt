package hu.toonnav.map

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.res.Configuration
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
import android.util.TypedValue
import android.view.Surface
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
 * The driving screen. A navigation launch follows the stored trip. A garage
 * preview without a trip keeps the labeled sample and does not guide.
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
    @Volatile private var route = emptyList<OsrmPoint>()
    @Volatile private var samples = emptyList<RoadSample>()
    private var roadKinds = emptyList<RoadKind>()
    private var elevations = FloatArray(0)
    @Volatile private var terrain: ((Double, Double) -> Float)? = null
    @Volatile private var windowEnd = 0.0
    @Volatile private var buildingRoad = false
    @Volatile private var buildingScenery = false
    @Volatile private var geometryGeneration = 0
    @Volatile private var sceneryGeneration = 0
    private val sceneryReady = HashSet<Int>()
    private var horizonCenter = Double.NaN
    @Volatile var travelAlong = 8.0
    @Volatile var uploadMs = 0.0
    @Volatile var peakFrameMs = 0.0

    @Volatile var followLongitude = Double.NaN
    @Volatile var followLatitude = Double.NaN
    @Volatile var followSpeed = 0.0
    @Volatile var followHasSpeed = false
    @Volatile var followElapsed = 0L
    @Volatile var followLive = false
    @Volatile var previewAlong = 8.0
    @Volatile var frameFps = Double.NaN
    @Volatile var frameMs = Double.NaN
    private val mainHandler = Handler(Looper.getMainLooper())
    private var navigationMode = false
    private var chromeWidth = 0
    private var chromeHeight = 0
    private var driveQuality = DriveDisplay.Quality.FULL
    private var qualityReduced = false
    private var slowFrames = 0
    private var announceStart = false
    private var tripArrived = false
    private var watchPosted = false
    private lateinit var finishLayer: android.widget.FrameLayout
    private val guidanceWatch = object : Runnable {
        override fun run() {
            refreshGuidanceAge()
            if (watchPosted) mainHandler.postDelayed(this, 1000L)
        }
    }
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        speedView = findViewById(R.id.raceSpeed)
        maneuverView = findViewById(R.id.raceManeuver)
        remainingView = findViewById(R.id.raceRemaining)
        etaView = findViewById(R.id.raceEta)
        statusView = findViewById(R.id.raceStatus)
        voiceButton = findViewById(R.id.raceVoice)
        nightButton = findViewById(R.id.raceNight)
        val content = findViewById<android.view.View>(android.R.id.content)
        content.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            applyChrome(right - left, bottom - top)
        }
        voiceOn = hudPrefs.getBoolean("voice", true)
        night = hudPrefs.getBoolean("night", false)
        voice.enabled = voiceOn
        navigationMode = intent.getBooleanExtra(EXTRA_NAVIGATION, false)
        announceStart = intent.getBooleanExtra(EXTRA_ANNOUNCE, false)
        if (!announceStart) voice.restoreKey(NavigationVoice.retainedKey)
        NavigationOwner.raceCreated()
        Thread {
            voice.load()
            if (!NavigationVoice.announceStart(navigationMode, announceStart, true)) return@Thread
            runOnUiThread {
                if (isDestroyed || !announceStart || !usingLiveRoute) return@runOnUiThread
                announceStart = false
                intent.putExtra(EXTRA_ANNOUNCE, false)
                voice.speak("indulas", "indulas", null, "indulas")
            }
        }.start()
        previewAlong = intent.getFloatExtra(EXTRA_PREVIEW_ALONG, 8f).toDouble().coerceIn(0.0, 5_000.0)
        val requested = intent.getStringExtra(EXTRA_CAR)
        val selected = GarageFleet.shown(requested ?: GarageStore(this).carId())
        carIndex = GarageFleet.cars.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        plan = DriveSessionMemory.plan
        val live = DriveSessionMemory.routePoints
        val choice = NavigationRoute.choose(navigationMode, live.size, plan != null)
        usingLiveRoute = choice == RaceRouteSource.LIVE
        route = when (choice) {
            RaceRouteSource.LIVE -> live.toList()
            RaceRouteSource.SAMPLE -> RaceTrack.samplePreview()
            RaceRouteSource.MISSING -> emptyList()
        }
        prepareSamples()
        if (usingLiveRoute) {
            val stored = DriveSessionMemory.traveledMeters
            val end = samples.lastOrNull()?.along ?: 0.0
            if (stored.isFinite() && stored > 1.0 && end > 1.0) {
                val along = stored.coerceIn(0.0, end)
                travelAlong = along
                previewAlong = along
            }
            plan?.let { guidance.setPlan(it) }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        findViewById<Button>(R.id.raceStop).setOnClickListener { confirmStop() }
        findViewById<Button>(R.id.raceBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.raceGarage).setOnClickListener {
            startActivity(Intent(this, GarageActivity::class.java))
        }
        voiceButton.setOnClickListener { toggleVoice() }
        nightButton.setOnClickListener { toggleNight() }
        findViewById<Button>(R.id.raceLivery).setOnClickListener { cycleLivery() }
        finishLayer = findViewById(R.id.raceFinish)
        findViewById<Button>(R.id.raceFinishMap).setOnClickListener {
            voice.halt()
            DriveSessionMemory.arrivalHandoff = true
            finish()
        }
        if (navigationMode) findViewById<Button>(R.id.raceBack).setText(R.string.race_back_map)
        refreshControls()
        paintHud(null)
        if (choice == RaceRouteSource.MISSING) {
            val missing = getString(R.string.race_route_missing)
            statusView.text = missing
            maneuverView.text = missing
            remainingView.text = missing
            return
        }
        val glMajor = glEsMajor()
        if (glMajor in 1..2) {
            showEngineFailed()
            return
        }
        driveQuality = DriveDisplay.quality(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels,
            memoryClassMb(),
            glMajor
        )
        if (driveQuality == DriveDisplay.Quality.REDUCED) {
            qualityReduced = true
            statusView.text = getString(R.string.race_quality_reduced)
        }
        val thread = HandlerThread("toonnav-filament", Process.THREAD_PRIORITY_DISPLAY)
        thread.start()
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler
        val surface = findViewById<SurfaceView>(R.id.raceSurface)
        val routeNow = route
        handler.post {
            val carBytes = assets.open("models/formula-car.glb").use { it.readBytes() }
            windowEnd = samples.lastOrNull()?.along ?: 0.0
            val created = if (LegacyBackdrop.enabled) {
                val skyBytes = assets.open("models/noon-sky.hdr").use { it.readBytes() }
                val roadBytes = RaceGlb.encode(RaceGeometry.libraryModel())
                RaceRenderer.start(
                    carBytes, roadBytes, skyBytes, routeNow, samples, selected, night, driveQuality, surface, handler, this
                )
            } else {
                val material = assets.open("materials/plain_solid.filamat").use { it.readBytes() }
                RaceRenderer.startPlain(
                    carBytes, material, routeNow, samples, selected, night, surface, handler, this
                )
            }
            renderer = created
            if (created == null) runOnUiThread { showEngineFailed() } else if (usingLiveRoute) requestElevation(routeSeq)
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        NavigationOwner.claimRace()
        if (usingLiveRoute) startLocationUpdates()
        startWatch()
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopLocationUpdates()
        stopWatch()
        voice.silence()
        NavigationVoice.retain(voice.currentKey())
        val ending = DriveSessionMemory.abandon || DriveSessionMemory.arrivalHandoff
        if (usingLiveRoute && !ending) rememberProgress()
        NavigationOwner.releaseRace(returningToMap = usingLiveRoute && !ending)
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        chromeWidth = 0
        chromeHeight = 0
        val content = findViewById<android.view.View>(android.R.id.content)
        applyChrome(content.width, content.height)
    }

    override fun onStop() {
        stopLocationUpdates()
        super.onStop()
    }

    override fun onDestroy() {
        stopWatch()
        mainHandler.removeCallbacksAndMessages(null)
        NavigationOwner.raceDestroyed()
        voice.release()
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
        if (!fine && !coarse) {
            statusView.text = getString(R.string.race_location_denied)
            return
        }
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
        if (!updatesStarted) statusView.text = getString(R.string.race_location_unavailable)
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
            cue.arrived -> {
                tripArrived = true
                statusView.text = getString(R.string.status_arrived)
                voice.halt()
                revealPodium()
            }
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
                clearGpsStatus()
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
        DriveTripFile.save(this)
        reroutes.succeeded(SystemClock.elapsedRealtime())
        offRouteSpoken = false
        voice.halt()
        voice.speak("reroute", "reroute", null, "ujratervezes")
        sceneryReady.clear()
        sceneryGeneration++
        rebuildCorridor(keepMotion = false)
        requestElevation(routeSeq)
        statusView.text = ""
        paintHud(guidance.refreshAge(fixAge()))
    }

    private fun prepareSamples() {
        roadKinds = if (usingLiveRoute) RouteRoadClass.kinds(route, plan?.steps.orEmpty()) else emptyList()
        samples = RaceTrack.samplesFor(route, roadKinds, elevations)
    }

    private fun rebuildCorridor(keepMotion: Boolean) {
        if (!LegacyBackdrop.enabled) {
            rebuildPlain(keepMotion)
            return
        }
        val handler = renderHandler
        if (handler == null) {
            buildingRoad = false
            return
        }
        geometryGeneration++
        val generation = geometryGeneration
        val source = samples
        val field = terrain
        val along = travelAlong
        val end = source.lastOrNull()?.along ?: 0.0
        windowEnd = end
        val needed = RaceGeometry.chunksCovering(along, end)
        val center = if (source.size >= 2) RaceTrack.pointAt(source, along) else null
        buildingRoad = true
        Thread {
            val built = needed.associateWith { index ->
                RaceGeometry.worldModel(
                    source,
                    field,
                    index * RaceGeometry.chunkMeters,
                    (index + 1) * RaceGeometry.chunkMeters
                )
            }
            val hills = if (center != null) RaceGeometry.horizon(source, field, center.east, center.north) else null
            handler.post {
                if (generation != geometryGeneration) return@post
                val uploadStarted = System.nanoTime()
                renderer?.syncRoad(source, route, built, hills, keepMotion)
                uploadMs = (System.nanoTime() - uploadStarted) / 1_000_000.0
                if (hills != null) horizonCenter = along
                buildingRoad = false
            }
        }.start()
    }

    fun noteHorizon(along: Double) {
        horizonCenter = along
    }

    fun slideWindow(along: Double) {
        if (!LegacyBackdrop.enabled) {
            slidePlain(along)
            return
        }
        val gpu = renderer?.roadGpu ?: return
        if (samples.size < 2) return
        val end = samples.last().along
        val needed = RaceGeometry.chunksCovering(along, end)
        if (!buildingRoad) {
            val missing = needed.filter { it !in gpu.chunkKeys }
            if (missing.isNotEmpty()) {
                buildingRoad = true
                val generation = geometryGeneration
                val source = samples
                val field = terrain
                val handler = renderHandler
                Thread {
                    val built = missing.associateWith { index ->
                        RaceGeometry.worldModel(
                            source,
                            field,
                            index * RaceGeometry.chunkMeters,
                            (index + 1) * RaceGeometry.chunkMeters
                        )
                    }
                    handler?.post {
                        if (generation == geometryGeneration) {
                            val started = System.nanoTime()
                            gpu.uploadChunks(built)
                            val endNow = samples.lastOrNull()?.along ?: 0.0
                            gpu.dropChunksExcept(RaceGeometry.chunksCovering(travelAlong, endNow))
                            uploadMs = (System.nanoTime() - started) / 1_000_000.0
                        }
                        buildingRoad = false
                    }
                }.start()
            } else {
                gpu.dropChunksExcept(needed)
                refreshHorizon(along)
            }
        }
        refreshScenery(along)
    }

    private fun refreshHorizon(along: Double) {
        if (buildingRoad || samples.size < 2) return
        if (!horizonCenter.isNaN() && kotlin.math.abs(along - horizonCenter) < 280.0) return
        buildingRoad = true
        val generation = geometryGeneration
        val source = samples
        val field = terrain
        val center = RaceTrack.pointAt(source, along)
        val handler = renderHandler
        Thread {
            val hills = RaceGeometry.horizon(source, field, center.east, center.north)
            handler?.post {
                if (generation == geometryGeneration && hills != null) {
                    renderer?.roadGpu?.replaceHorizon(hills)
                    horizonCenter = along
                }
                buildingRoad = false
            }
        }.start()
    }

    private fun sceneryRange(along: Double): IntRange? {
        val end = samples.lastOrNull()?.along ?: return null
        if (end <= 1.0) return 0..0
        val first = kotlin.math.floor(((along - 200.0).coerceAtLeast(0.0)) / Scenery.chunkMeters).toInt()
        val lastAlong = (along + 1_200.0).coerceAtMost((end - 0.01).coerceAtLeast(0.0))
        val last = kotlin.math.floor(lastAlong / Scenery.chunkMeters).toInt()
        return first..last
    }

    private fun trimScenery(keep: IntRange) {
        sceneryReady.retainAll(keep.toSet())
        renderer?.roadGpu?.dropSceneryExcept(keep)
    }

    private fun rebuildPlain(keepMotion: Boolean) {
        val handler = renderHandler
        if (handler == null) {
            buildingRoad = false
            return
        }
        geometryGeneration++
        val generation = geometryGeneration
        val source = samples
        val along = travelAlong
        val end = source.lastOrNull()?.along ?: 0.0
        windowEnd = end
        val needed = RaceGeometry.chunksCovering(along, end)
        buildingRoad = true
        Thread {
            val built = PlainWorld.chunks(source, needed)
            handler.post {
                if (generation != geometryGeneration) return@post
                renderer?.syncPlain(source, route, built, keepMotion)
                buildingRoad = false
            }
        }.start()
    }

    private fun slidePlain(along: Double) {
        val gpu = renderer?.plainGpu ?: return
        if (samples.size < 2) return
        val end = samples.last().along
        val needed = RaceGeometry.chunksCovering(along, end)
        if (buildingRoad) return
        val missing = needed.filter { it !in gpu.chunkKeys }
        if (missing.isEmpty()) {
            gpu.dropChunksExcept(needed)
            return
        }
        buildingRoad = true
        val generation = geometryGeneration
        val source = samples
        val handler = renderHandler
        Thread {
            val built = PlainWorld.chunks(source, missing.first()..missing.last())
            handler?.post {
                if (generation == geometryGeneration) {
                    gpu.uploadChunks(built.filterKeys { it in missing })
                    val endNow = samples.lastOrNull()?.along ?: 0.0
                    gpu.dropChunksExcept(RaceGeometry.chunksCovering(travelAlong, endNow))
                }
                buildingRoad = false
            }
        }.start()
    }

    private fun refreshScenery(along: Double) {
        if (!LegacyBackdrop.enabled) return
        if (!usingLiveRoute || buildingScenery || route.size < 2) return
        val needed = sceneryRange(along) ?: return
        val missing = needed.filter { it >= 0 && it !in sceneryReady }
        if (missing.isEmpty()) {
            trimScenery(needed)
            return
        }
        buildingScenery = true
        val generation = sceneryGeneration
        val origin = route.first()
        val source = samples
        val field = terrain
        val handler = renderHandler
        Thread {
            val built = HashMap<Int, List<RaceMesh>>()
            for (index in missing) {
                val box = Scenery.bounds(origin, source, index * Scenery.chunkMeters, (index + 1) * Scenery.chunkMeters)
                val json = if (box == null) null else Scenery.fetch(box[0], box[1], box[2], box[3])
                val slice = source.filter { it.along in (index * Scenery.chunkMeters - 30.0)..((index + 1) * Scenery.chunkMeters + 30.0) }
                built[index] = if (json == null) emptyList() else Scenery.parse(json, origin, slice, field)
            }
            handler?.post {
                if (generation == sceneryGeneration) {
                    val keep = sceneryRange(travelAlong) ?: needed
                    sceneryReady.addAll(missing.filter { it in keep })
                    renderer?.roadGpu?.uploadScenery(built.filterKeys { it in keep })
                    trimScenery(keep)
                }
                buildingScenery = false
            }
        }.start()
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
                sceneryReady.clear()
                sceneryGeneration++
                rebuildCorridor(keepMotion = true)
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
        DriveTripFile.save(this)
    }

    private fun showEngineFailed() {
        val failed = getString(R.string.race_engine_failed)
        statusView.text = failed
        maneuverView.text = failed
    }

    private fun glEsMajor(): Int {
        return try {
            val info = (getSystemService(ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo
            info.reqGlEsVersion shr 16
        } catch (failed: Throwable) {
            0
        }
    }

    private fun memoryClassMb(): Int {
        return try {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).memoryClass
        } catch (failed: Throwable) {
            0
        }
    }

    private fun applyChrome(widthPx: Int, heightPx: Int) {
        if (widthPx < 2 || heightPx < 2) return
        if (widthPx == chromeWidth && heightPx == chromeHeight) return
        chromeWidth = widthPx
        chromeHeight = heightPx
        val layout = DriveChrome.layout(widthPx, heightPx, resources.displayMetrics.density)
        place(findViewById(R.id.raceInfo), layout.guidance)
        place(findViewById(R.id.raceStop), layout.stop)
        place(findViewById(R.id.raceVoice), layout.voice)
        place(findViewById(R.id.raceBack), layout.map)
        place(findViewById(R.id.raceGarage), layout.garage)
        place(findViewById(R.id.raceNight), layout.night)
        place(findViewById(R.id.raceLivery), layout.livery)
        speedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, layout.speedSp)
        maneuverView.setTextSize(TypedValue.COMPLEX_UNIT_SP, layout.maneuverSp)
        remainingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, layout.detailSp)
        etaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, layout.detailSp)
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, layout.detailSp)
        val buttonSp = layout.buttonSp
        for (id in intArrayOf(R.id.raceStop, R.id.raceVoice, R.id.raceBack, R.id.raceGarage, R.id.raceNight, R.id.raceLivery)) {
            findViewById<Button>(id).setTextSize(TypedValue.COMPLEX_UNIT_SP, buttonSp)
        }
    }

    private fun place(view: android.view.View, box: DriveBox) {
        val params = FrameLayout.LayoutParams(box.width, box.height)
        params.leftMargin = box.left
        params.topMargin = box.top
        view.layoutParams = params
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
    }

    fun publishFrame(fps: Double, milliseconds: Double, peak: Double) {
        frameFps = fps
        frameMs = milliseconds
        peakFrameMs = peak
        if (qualityReduced || driveQuality == DriveDisplay.Quality.REDUCED) return
        if (milliseconds > 42.0) slowFrames += 1 else slowFrames = 0
        if (slowFrames < 4) return
        qualityReduced = true
        renderHandler?.post { renderer?.reduceDetail() }
        runOnUiThread {
            if (!isDestroyed && statusView.text.isNullOrBlank()) {
                statusView.text = getString(R.string.race_quality_reduced)
            }
        }
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

    private fun startWatch() {
        if (watchPosted || !usingLiveRoute) return
        watchPosted = true
        mainHandler.postDelayed(guidanceWatch, 1000L)
    }

    private fun stopWatch() {
        watchPosted = false
        mainHandler.removeCallbacks(guidanceWatch)
    }

    private fun refreshGuidanceAge() {
        if (!usingLiveRoute) return
        if (tripArrived) {
            revealPodium()
            return
        }
        if (!followLive) return
        val cue = guidance.refreshAge(fixAge()) ?: return
        paintHud(cue)
        if (reroutes.inFlight) return
        when (cue.trust) {
            GpsTrust.LOST -> statusView.text = getString(R.string.status_gps)
            GpsTrust.UNUSABLE, GpsTrust.INACCURATE -> statusView.text = getString(R.string.status_gps_poor)
            GpsTrust.USABLE -> clearGpsStatus()
        }
    }

    private fun clearGpsStatus() {
        val current = statusView.text?.toString().orEmpty().lineSequence().firstOrNull().orEmpty()
        if (current == getString(R.string.status_gps) || current == getString(R.string.status_gps_poor)) {
            statusView.text = ""
        }
    }

    private fun revealPodium() {
        if (!::finishLayer.isInitialized) return
        val age = fixAge()
        val show = FinishCeremony.ready(
            tripArrived,
            if (followHasSpeed) followSpeed else null,
            followHasSpeed,
            followLive && age in 0..GpsTrustGate.lostAfterMs
        )
        finishLayer.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        if (show) finishLayer.bringToFront()
    }

    companion object {
        const val EXTRA_CAR = "car"
        const val EXTRA_PREVIEW_ALONG = "previewAlong"
        const val EXTRA_NAVIGATION = "navigation"
        const val EXTRA_ANNOUNCE = "announce"
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
    private var roadAsset: FilamentAsset?,
    private val loader: AssetLoader,
    private val resources: ResourceLoader,
    private val materials: UbershaderProvider,
    private var route: List<OsrmPoint>,
    private var samples: List<RoadSample>,
    private val host: RacePrototypeActivity,
    val plainGpu: PlainGpu? = null
) {
    val roadGpu: RoadGpu? = roadAsset?.let { RoadGpu(engine, scene, it) }
    var surfaceHelper: UiHelper? = null
    var swapChain: SwapChain? = null
    private var closed = false
    private var width = 0
    private var height = 0
    private var lastFrame = 0L
    private var frameSum = 0.0
    private var peakFrame = 0.0
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

    fun reduceDetail() {
        if (closed) return
        val ao = swapView.ambientOcclusionOptions
        ao.enabled = false
        ao.quality = View.QualityLevel.LOW
        swapView.ambientOcclusionOptions = ao
        val resolution = swapView.dynamicResolutionOptions
        resolution.enabled = true
        resolution.minScale = 0.45f
        resolution.quality = View.QualityLevel.LOW
        swapView.dynamicResolutionOptions = resolution
        swapView.setShadowingEnabled(false)
        val sun = engine.lightManager.getInstance(light)
        if (sun != 0) engine.lightManager.setShadowCaster(sun, false)
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
        if (plainGpu != null) {
            val color = if (night) PlainWorld.nightSky else PlainWorld.sky
            if (night) {
                val dark = nightSky ?: Skybox.Builder().color(color.red, color.green, color.blue, 1f).build(engine).also { nightSky = it }
                scene.skybox = dark
            } else {
                scene.skybox = daySky
            }
            val sun = engine.lightManager.getInstance(light)
            engine.lightManager.setIntensity(sun, if (night) 0.2f else PlainWorld.sunLux)
            indirect.setIntensity(if (night) 0.08f else 1f)
            camera.setExposure(PlainWorld.aperture, PlainWorld.shutter, PlainWorld.sensitivity)
            val clear = renderer.clearOptions
            clear.clear = true
            clear.clearColor = doubleArrayOf(color.red.toDouble(), color.green.toDouble(), color.blue.toDouble(), 1.0)
            renderer.clearOptions = clear
            return
        }
        val fog = swapView.fogOptions
        fog.fogColorFromIbl = false
        fog.color = if (night) RaceLighting.nightRadiance.copyOf() else RaceLighting.dayRadiance.copyOf()
        swapView.fogOptions = fog
        val sun = engine.lightManager.getInstance(light)
        engine.lightManager.setIntensity(sun, if (night) 6_000f else 100_000f)
        probe?.indirect?.setIntensity(if (night) 0.08f else 1f)
        camera.setExposure(if (night) 8f else 16f, if (night) 1f / 30f else 1f / 125f, if (night) 800f else 100f)
        if (night) {
            val dark = nightSky ?: Skybox.Builder().color(RaceLighting.skyColor(RaceLighting.nightRadiance)).build(engine).also { nightSky = it }
            scene.skybox = dark
        } else {
            scene.skybox = daySky
        }
        val clear = renderer.clearOptions
        val radiance = if (night) RaceLighting.nightRadiance else RaceLighting.dayRadiance
        clear.clear = true
        clear.clearColor = doubleArrayOf(radiance[0].toDouble(), radiance[1].toDouble(), radiance[2].toDouble(), 1.0)
        renderer.clearOptions = clear
    }

    fun replaceRoad(bytes: ByteArray, nextRoute: List<OsrmPoint>, nextSamples: List<RoadSample>, keepMotion: Boolean = false) {
        if (closed || plainGpu != null) return
        val asset = loadAsset(bytes) ?: return
        val current = roadAsset ?: return
        scene.removeEntities(current.entities)
        loader.destroyAsset(current)
        roadAsset = asset
        scene.addEntities(asset.entities)
        route = nextRoute
        samples = nextSamples
        if (!keepMotion) {
            held = false
            bearingReady = false
        }
    }

    fun syncPlain(
        nextSamples: List<RoadSample>,
        nextRoute: List<OsrmPoint>,
        built: Map<Int, List<RaceMesh>>,
        keepMotion: Boolean
    ) {
        if (closed) return
        plainGpu?.replaceAll(built)
        route = nextRoute
        samples = nextSamples
        if (!keepMotion) {
            held = false
            bearingReady = false
        }
    }

    fun syncRoad(
        nextSamples: List<RoadSample>,
        nextRoute: List<OsrmPoint>,
        built: Map<Int, List<RaceMesh>>,
        hills: RaceMesh?,
        keepMotion: Boolean
    ) {
        if (closed) return
        roadGpu?.replaceAll(built, hills)
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
        roadGpu?.destroy()
        plainGpu?.destroy()
        loader.destroyAsset(carAsset)
        roadAsset?.let { loader.destroyAsset(it) }
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
            val here = RaceTrack.pointAt(samples, host.previewAlong)
            heldAlong = here.along
            east = here.east
            north = here.north
            bearing = RaceTrack.bearing(route, samples, heldAlong)
        }
        val surface = RaceTrack.pointAt(samples, heldAlong).elevation.toDouble() + RaceGeometry.deckTop
        host.travelAlong = heldAlong
        host.slideWindow(heldAlong)
        val turned = dampBearing(bearing, step)
        val aspect = width.toDouble() / height.toDouble()
        camera.setProjection(
            DriveDisplay.verticalFov(aspect),
            aspect,
            RaceCamera.nearMeters,
            RaceCamera.farMeters,
            Camera.Fov.VERTICAL
        )
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
            val frameMs = step * 1000.0
            if (frameMs > peakFrame) peakFrame = frameMs
            frameSum += frameMs
            frameCount += 1
            val now = android.os.SystemClock.uptimeMillis()
            if (frameCount >= 12 && now - posted > 250L) {
                val average = frameSum / frameCount
                host.publishFrame(1000.0 / average, average, peakFrame)
                frameSum = 0.0
                frameCount = 0
                peakFrame = 0.0
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
        fun startPlain(
            carBytes: ByteArray,
            materialBytes: ByteArray,
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
                view.setShadowingEnabled(false)
                view.setPostProcessingEnabled(true)
                val ao = view.ambientOcclusionOptions
                ao.enabled = false
                view.ambientOcclusionOptions = ao
                val fog = view.fogOptions
                fog.enabled = false
                view.fogOptions = fog
                val sky = Skybox.Builder()
                    .color(PlainWorld.sky.red, PlainWorld.sky.green, PlainWorld.sky.blue, 1f)
                    .build(engine)
                scene.skybox = sky
                val fill = FloatArray(27)
                fill[0] = 0.45f
                fill[1] = 0.45f
                fill[2] = 0.48f
                val indirect = IndirectLight.Builder().irradiance(3, fill).intensity(1f).build(engine)
                scene.indirectLight = indirect
                val light = EntityManager.get().create()
                LightManager.Builder(LightManager.Type.SUN)
                    .color(1.0f, 0.98f, 0.94f)
                    .intensity(PlainWorld.sunLux)
                    .direction(0.25f, -0.92f, 0.30f)
                    .castShadows(false)
                    .build(engine, light)
                scene.addEntity(light)
                val cameraEntity = EntityManager.get().create()
                val camera = engine.createCamera(cameraEntity)
                camera.setExposure(PlainWorld.aperture, PlainWorld.shutter, PlainWorld.sensitivity)
                view.camera = camera
                val renderer = engine.createRenderer()
                val clear = renderer.clearOptions
                clear.clear = true
                clear.clearColor = doubleArrayOf(
                    PlainWorld.sky.red.toDouble(),
                    PlainWorld.sky.green.toDouble(),
                    PlainWorld.sky.blue.toDouble(),
                    1.0
                )
                renderer.clearOptions = clear
                val plain = PlainGpu(engine, scene, materialBytes)
                val created = RaceRenderer(
                    engine, view, renderer, scene, sky, indirect, light, camera, cameraEntity,
                    null, sky,
                    load(loader, resources, carBytes) ?: throw IllegalStateException("car"),
                    null,
                    loader, resources, provider, route, samples, host,
                    plain
                )
                scene.addEntities(created.carAsset.entities)
                val routeEnd = samples.lastOrNull()?.along ?: 0.0
                val along = host.travelAlong.coerceIn(0.0, routeEnd.coerceAtLeast(0.0))
                val needed = RaceGeometry.chunksCovering(along, routeEnd)
                plain.uploadChunks(PlainWorld.chunks(samples, needed))
                val renderables = engine.renderableManager
                for (entity in created.carAsset.renderableEntities) {
                    val instance = renderables.getInstance(entity)
                    if (instance == 0) continue
                    renderables.setCastShadows(instance, false)
                    renderables.setReceiveShadows(instance, false)
                }
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

        fun start(
            carBytes: ByteArray,
            roadBytes: ByteArray,
            skyBytes: ByteArray,
            route: List<OsrmPoint>,
            samples: List<RoadSample>,
            car: GarageFleet.Car,
            night: Boolean,
            quality: DriveDisplay.Quality,
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
                val reduced = quality == DriveDisplay.Quality.REDUCED
                view.blendMode = View.BlendMode.OPAQUE
                view.antiAliasing = View.AntiAliasing.FXAA
                view.setShadowingEnabled(!reduced)
                view.setPostProcessingEnabled(true)
                val ao = view.ambientOcclusionOptions
                ao.enabled = !reduced
                ao.quality = if (reduced) View.QualityLevel.LOW else View.QualityLevel.MEDIUM
                view.ambientOcclusionOptions = ao
                val resolution = view.dynamicResolutionOptions
                resolution.enabled = true
                resolution.minScale = if (reduced) 0.45f else 0.7f
                resolution.maxScale = 1f
                resolution.quality = if (reduced) View.QualityLevel.LOW else View.QualityLevel.MEDIUM
                view.dynamicResolutionOptions = resolution
                val fog = view.fogOptions
                fog.enabled = true
                fog.distance = RaceFog.distance
                fog.maximumOpacity = RaceFog.maximumOpacity
                fog.density = RaceFog.density
                // Sea level at the road. A floor of 80 m puts the carriageway inside the fog.
                fog.height = RaceFog.height
                fog.heightFalloff = RaceFog.heightFalloff
                fog.cutOffDistance = RaceFog.cutOffDistance
                fog.fogColorFromIbl = false
                fog.color = RaceLighting.dayRadiance.copyOf()
                view.fogOptions = fog
                val probe = RaceLighting.create(engine, skyBytes)
                // The background is a solid radiance, not the HDR cubemap. A failed
                // or unbound environment map used to leave the phone with no sky.
                val sky = Skybox.Builder().color(RaceLighting.skyColor(RaceLighting.dayRadiance)).build(engine)
                probe?.sky?.let { engine.destroySkybox(it) }
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
                val shadows = LightManager.ShadowOptions()
                shadows.mapSize = if (reduced) 512 else 1024
                shadows.shadowCascades = 1
                shadows.shadowFar = if (reduced) 28f else 48f
                shadows.screenSpaceContactShadows = !reduced
                shadows.maxShadowDistance = 8f
                shadows.constantBias = 0.08f
                shadows.normalBias = 0.6f
                LightManager.Builder(LightManager.Type.SUN)
                    .color(1.0f, 0.97f, 0.92f)
                    .intensity(100_000f)
                    .direction(RaceLighting.sunX, RaceLighting.sunY, RaceLighting.sunZ)
                    .castShadows(!reduced)
                    .shadowOptions(shadows)
                    .sunAngularRadius(0.45f)
                    .build(engine, light)
                scene.addEntity(light)
                val cameraEntity = EntityManager.get().create()
                val camera = engine.createCamera(cameraEntity)
                camera.setExposure(16f, 1f / 125f, 100f)
                view.camera = camera
                val renderer = engine.createRenderer()
                val clear = renderer.clearOptions
                clear.clear = true
                clear.clearColor = doubleArrayOf(
                    RaceLighting.dayRadiance[0].toDouble(),
                    RaceLighting.dayRadiance[1].toDouble(),
                    RaceLighting.dayRadiance[2].toDouble(),
                    1.0
                )
                renderer.clearOptions = clear
                val created = RaceRenderer(
                    engine, view, renderer, scene, sky, indirect, light, camera, cameraEntity,
                    probe, sky,
                    load(loader, resources, carBytes) ?: throw IllegalStateException("car"),
                    load(loader, resources, roadBytes) ?: throw IllegalStateException("road"),
                    loader, resources, provider, route, samples, host
                )
                scene.addEntities(created.carAsset.entities)
                val routeEnd = samples.lastOrNull()?.along ?: 0.0
                val along = host.travelAlong.coerceIn(0.0, routeEnd.coerceAtLeast(0.0))
                val needed = RaceGeometry.chunksCovering(along, routeEnd)
                val built = needed.associateWith { index ->
                    RaceGeometry.worldModel(
                        samples,
                        null,
                        index * RaceGeometry.chunkMeters,
                        (index + 1) * RaceGeometry.chunkMeters
                    )
                }
                created.roadGpu?.uploadChunks(built)
                if (samples.size >= 2) {
                    val center = RaceTrack.pointAt(samples, along)
                    val ground = RaceGeometry.horizon(samples, null, center.east, center.north)
                    if (ground != null) {
                        created.roadGpu?.replaceHorizon(ground)
                        host.noteHorizon(along)
                    }
                }
                val renderables = engine.renderableManager
                for (entity in created.carAsset.renderableEntities) {
                    val instance = renderables.getInstance(entity)
                    if (instance == 0) continue
                    renderables.setCastShadows(instance, true)
                    renderables.setReceiveShadows(instance, true)
                    renderables.setScreenSpaceContactShadows(instance, true)
                }
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
