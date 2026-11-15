package hu.toonnav.map

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Isolated Filament preview. It reads the current route and the saved car,
 * and it does not replace the driving screen or change navigation state.
 */
class RacePrototypeActivity : ComponentActivity() {
    private lateinit var statusView: TextView
    private lateinit var playButton: Button
    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private var renderer: RaceRenderer? = null
    private var playing = true
    private var carIndex = 0
    private var usingLiveRoute = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_race_prototype)
        statusView = findViewById(R.id.raceStatus)
        playButton = findViewById(R.id.racePlay)
        val requested = intent.getStringExtra(EXTRA_CAR)
        val selected = GarageFleet.shown(requested ?: GarageStore(this).carId())
        carIndex = GarageFleet.cars.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        val live = DriveSessionMemory.routePoints
        usingLiveRoute = live.size >= 2
        val route = if (usingLiveRoute) live.toList() else RaceTrack.samplePreview()
        val samples = RaceTrack.samplesFor(route)
        findViewById<Button>(R.id.raceBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.raceLivery).setOnClickListener { cycleLivery() }
        playButton.setOnClickListener {
            playing = !playing
            playButton.setText(if (playing) R.string.race_pause else R.string.race_play)
            renderer?.playing = playing
        }
        showStatus(null, null, false)
        val thread = HandlerThread("toonnav-filament", Process.THREAD_PRIORITY_DISPLAY)
        thread.start()
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler
        val surface = findViewById<SurfaceView>(R.id.raceSurface)
        handler.post {
            val created = RaceRenderer.start(route, samples, selected.body, selected.accent, surface, handler, this)
            renderer = created
            created?.playing = playing
            if (created == null) {
                runOnUiThread { statusView.text = getString(R.string.race_engine_failed) }
            }
        }
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

    private fun cycleLivery() {
        val next = GarageFleet.cars[(carIndex + 1) % GarageFleet.cars.size]
        carIndex = GarageFleet.cars.indexOf(next)
        val storedNumber = GarageStore(this).number() ?: next.defaultNumber
        GarageStore(this).write(next.id, storedNumber)
        renderHandler?.post { renderer?.applyLivery(next.body, next.accent) }
        showStatus(null, null, false)
    }

    private fun showStatus(fps: Double?, frameMs: Double?, reduced: Boolean) {
        val car = GarageFleet.cars[carIndex]
        val routeLabel = getString(if (usingLiveRoute) R.string.race_live_route else R.string.race_sample_route)
        val frame = if (fps == null || frameMs == null) "—" else "${fps.roundToInt()} fps, ${"%.1f".format(frameMs)} ms"
        val quality = if (reduced) "\n" + getString(R.string.race_quality_reduced) else ""
        statusView.text = "${car.name} · $routeLabel\n${getString(R.string.race_preview_note)}\n$frame$quality"
    }

    fun publishFrame(fps: Double, frameMs: Double, reduced: Boolean) {
        runOnUiThread { if (!isDestroyed) showStatus(fps, frameMs, reduced) }
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
    private val camera: Camera,
    private val asset: FilamentAsset,
    private val loader: AssetLoader,
    private val resources: ResourceLoader,
    private val materials: UbershaderProvider,
    private val skybox: Skybox,
    private val indirect: IndirectLight,
    private val light: Int,
    private val cameraEntity: Int,
    private val route: List<OsrmPoint>,
    private val samples: List<RoadSample>,
    private val host: RacePrototypeActivity
) {
    var playing = true
    var surfaceHelper: UiHelper? = null
    var swapChain: SwapChain? = null
    private var closed = false
    private var width = 0
    private var height = 0
    private var along = 0.0
    private var lastFrame = 0L
    private var frameSum = 0.0
    private var frameCount = 0
    private var reduced = false
    private var posted = 0L
    private val choreographer = Choreographer.getInstance()
    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            draw(frameTimeNanos)
            choreographer.postFrameCallback(this)
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

    fun applyLivery(body: Int, accent: Int) {
        for (instance in asset.instance.materialInstances) {
            val color = when (instance.name) {
                "body" -> body
                "accent" -> accent
                else -> continue
            }
            instance.setParameter(
                "baseColorFactor",
                Colors.RgbaType.SRGB,
                channel(color, 16),
                channel(color, 8),
                channel(color, 0),
                1f
            )
        }
    }

    fun destroy() {
        closed = true
        choreographer.removeFrameCallback(callback)
        loader.destroyAsset(asset)
        resources.destroy()
        loader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        scene.skybox = null
        scene.indirectLight = null
        engine.destroySkybox(skybox)
        engine.destroyIndirectLight(indirect)
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
        if (playing && samples.size >= 2) {
            val length = samples.last().along.coerceAtLeast(1.0)
            along = (along + 14.0 * step) % length
        }
        val here = RaceTrack.pointAt(samples, along)
        val bearing = RaceTrack.bearing(route, samples, along)
        val pose = RaceCamera.pose(here.east, here.north, bearing)
        val aspect = width.toDouble() / height.toDouble()
        camera.setProjection(48.0, aspect, 0.08, 500.0, Camera.Fov.VERTICAL)
        camera.lookAt(
            pose.eyeX.toDouble(), pose.eyeY.toDouble(), pose.eyeZ.toDouble(),
            pose.targetX.toDouble(), pose.targetY.toDouble(), pose.targetZ.toDouble(),
            0.0, 1.0, 0.0
        )
        val transform = engine.transformManager
        val car = transform.getInstance(asset.getFirstEntityByName("car"))
        if (car != 0) transform.setTransform(car, RaceCamera.carTransform(here.east, here.north, bearing))
        swapView.viewport = Viewport(0, 0, width, height)
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(swapView)
            renderer.endFrame()
        }
        if (step > 0.0) {
            frameSum += step * 1000.0
            frameCount += 1
            if (!reduced && frameCount >= 30 && frameSum / frameCount > 22.0) {
                reduced = true
                val options = swapView.ambientOcclusionOptions
                options.enabled = false
                swapView.ambientOcclusionOptions = options
            }
            val now = android.os.SystemClock.uptimeMillis()
            if (frameCount >= 12 && now - posted > 250L) {
                val average = frameSum / frameCount
                host.publishFrame(1000.0 / average, average, reduced)
                frameSum = 0.0
                frameCount = 0
                posted = now
            }
        }
    }

    companion object {
        fun start(
            route: List<OsrmPoint>,
            samples: List<RoadSample>,
            body: Int,
            accent: Int,
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
                val model = RaceTrack.model(route, body, accent)
                val glb = RaceGlb.encode(model)
                val buffer = ByteBuffer.allocateDirect(glb.size).order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(glb)
                buffer.flip()
                val provider = UbershaderProvider(engine)
                val loader = AssetLoader(engine, provider, EntityManager.get())
                val resources = ResourceLoader(engine)
                val asset = loader.createAsset(buffer) ?: throw IllegalStateException("glb")
                resources.loadResources(asset)
                asset.releaseSourceData()
                val scene = engine.createScene()
                scene.addEntities(asset.entities)
                val view = engine.createView()
                view.scene = scene
                view.blendMode = View.BlendMode.OPAQUE
                view.antiAliasing = View.AntiAliasing.FXAA
                view.setShadowingEnabled(true)
                val ao = view.ambientOcclusionOptions
                ao.enabled = true
                ao.quality = View.QualityLevel.MEDIUM
                view.ambientOcclusionOptions = ao
                val resolution = view.dynamicResolutionOptions
                resolution.enabled = true
                resolution.quality = View.QualityLevel.MEDIUM
                view.dynamicResolutionOptions = resolution
                val sky = Skybox.Builder().color(0.55f, 0.68f, 0.82f, 1f).build(engine)
                scene.skybox = sky
                val sh = FloatArray(27)
                sh[0] = 0.8f
                sh[1] = 0.85f
                sh[2] = 0.9f
                val indirect = IndirectLight.Builder().irradiance(3, sh).intensity(30_000f).build(engine)
                scene.indirectLight = indirect
                val light = EntityManager.get().create()
                LightManager.Builder(LightManager.Type.SUN)
                    .color(1.0f, 0.96f, 0.90f)
                    .intensity(110_000f)
                    .direction(0.35f, -0.82f, -0.25f)
                    .castShadows(true)
                    .sunAngularRadius(1.9f)
                    .build(engine, light)
                scene.addEntity(light)
                val cameraEntity = EntityManager.get().create()
                val camera = engine.createCamera(cameraEntity)
                view.camera = camera
                val created = RaceRenderer(
                    engine, view, engine.createRenderer(), scene, camera, asset, loader, resources,
                    provider, sky, indirect, light, cameraEntity, route, samples, host
                )
                created.applyLivery(body, accent)
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

        private fun channel(color: Int, shift: Int): Float = ((color ushr shift) and 0xFF) / 255f
    }
}
