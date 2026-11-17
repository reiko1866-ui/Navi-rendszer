package hu.toonnav.map

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Surface
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Utils
import java.util.concurrent.CountDownLatch

/**
 * The new backdrop on its own. No car, no glTF, no HDR, no KTX2, no route
 * service. Navigation does not start here.
 */
class PlainDriveActivity : AppCompatActivity() {
    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private var stage: Stage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plain_drive)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (glEsMajor() in 1..2) {
            showFailure()
            return
        }
        val thread = HandlerThread("toonnav-plain", Process.THREAD_PRIORITY_DISPLAY)
        thread.start()
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler
        val surface = findViewById<SurfaceView>(R.id.plainSurface)
        val material = assets.open("materials/plain_solid.filamat").use { it.readBytes() }
        val samples = PlainWorld.previewSamples()
        handler.post {
            val created = try {
                Stage.start(material, samples, surface, handler)
            } catch (failed: Throwable) {
                null
            }
            stage = created
            if (created == null) runOnUiThread { showFailure() }
        }
    }

    override fun onDestroy() {
        val handler = renderHandler
        val thread = renderThread
        if (handler != null && thread != null) {
            val done = CountDownLatch(1)
            handler.post {
                stage?.destroy()
                stage = null
                done.countDown()
            }
            done.await(2, java.util.concurrent.TimeUnit.SECONDS)
            thread.quitSafely()
        }
        super.onDestroy()
    }

    private fun showFailure() {
        val error = findViewById<TextView>(R.id.plainError)
        error.setText(R.string.race_engine_failed)
        error.visibility = android.view.View.VISIBLE
    }

    private fun glEsMajor(): Int {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.deviceConfigurationInfo.reqGlEsVersion ushr 16
    }

    private class Stage(
        private val engine: Engine,
        private val view: View,
        private val renderer: Renderer,
        private val scene: Scene,
        private val sky: Skybox,
        private val camera: Camera,
        private val plain: PlainGpu,
        private val light: Int
    ) {
        var swapChain: SwapChain? = null
        private var closed = false
        var width = 0
        var height = 0
        private val choreographer = android.view.Choreographer.getInstance()
        private val callback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                draw(frameTimeNanos)
                if (!closed) choreographer.postFrameCallback(this)
            }
        }

        fun destroy() {
            closed = true
            choreographer.removeFrameCallback(callback)
            plain.destroy()
            scene.skybox = null
            engine.destroySkybox(sky)
            engine.destroyEntity(light)
            swapChain?.let { engine.destroySwapChain(it) }
            engine.destroyRenderer(renderer)
            engine.destroyView(view)
            engine.destroyScene(scene)
            engine.destroy()
        }

        private fun draw(frameTimeNanos: Long) {
            val chain = swapChain ?: return
            if (width < 2 || height < 2) return
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(DriveDisplay.verticalFov(aspect), aspect, RaceCamera.nearMeters, RaceCamera.farMeters, Camera.Fov.VERTICAL)
            camera.setModelMatrix(RaceCamera.cameraModel(0.0, 30.0, 0.0, 0.06))
            view.viewport = Viewport(0, 0, width, height)
            if (renderer.beginFrame(chain, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }

        companion object {
            fun start(material: ByteArray, samples: List<RoadSample>, surface: SurfaceView, handler: Handler): Stage {
                val engine = Engine.Builder().backend(Engine.Backend.OPENGL).build()
                val scene = engine.createScene()
                val view = engine.createView()
                view.scene = scene
                view.blendMode = View.BlendMode.OPAQUE
                view.setPostProcessingEnabled(true)
                view.setShadowingEnabled(false)
                view.antiAliasing = View.AntiAliasing.FXAA
                val fog = view.fogOptions
                fog.enabled = true
                fog.distance = PlainWorld.fogDistance
                fog.maximumOpacity = PlainWorld.fogMaximumOpacity
                fog.density = PlainWorld.fogDensity
                fog.height = 0f
                fog.heightFalloff = 0.01f
                fog.cutOffDistance = 8_000f
                fog.fogColorFromIbl = false
                fog.color = floatArrayOf(PlainWorld.sky.red, PlainWorld.sky.green, PlainWorld.sky.blue)
                view.fogOptions = fog
                val ao = view.ambientOcclusionOptions
                ao.enabled = false
                view.ambientOcclusionOptions = ao
                val sky = Skybox.Builder().color(PlainWorld.sky.red, PlainWorld.sky.green, PlainWorld.sky.blue, 1f).build(engine)
                scene.skybox = sky
                val renderer = engine.createRenderer()
                val clear = renderer.clearOptions
                clear.clear = true
                clear.clearColor = doubleArrayOf(PlainWorld.sky.red.toDouble(), PlainWorld.sky.green.toDouble(), PlainWorld.sky.blue.toDouble(), 1.0)
                renderer.clearOptions = clear
                val light = EntityManager.get().create()
                LightManager.Builder(LightManager.Type.SUN)
                    .color(1f, 0.98f, 0.94f)
                    .intensity(PlainWorld.sunLux)
                    .direction(0.25f, -0.92f, 0.30f)
                    .castShadows(false)
                    .build(engine, light)
                scene.addEntity(light)
                val cameraEntity = EntityManager.get().create()
                val camera = engine.createCamera(cameraEntity)
                camera.setExposure(PlainWorld.aperture, PlainWorld.shutter, PlainWorld.sensitivity)
                view.camera = camera
                val plain = PlainGpu(engine, scene, material)
                plain.uploadChunks(PlainWorld.chunks(samples, RaceGeometry.chunksCovering(30.0, samples.last().along)))
                val stage = Stage(engine, view, renderer, scene, sky, camera, plain, light)
                val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
                helper.renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(nativeWindow: Surface) {
                        handler.post {
                            stage.swapChain?.let { engine.destroySwapChain(it) }
                            stage.swapChain = engine.createSwapChain(nativeWindow)
                        }
                    }

                    override fun onDetachedFromSurface() {
                        handler.post {
                            stage.swapChain?.let { engine.destroySwapChain(it) }
                            stage.swapChain = null
                        }
                    }

                    override fun onResized(width: Int, height: Int) {
                        handler.post {
                            stage.width = width
                            stage.height = height
                        }
                    }
                }
                surface.post { helper.attachTo(surface) }
                stage.choreographer.postFrameCallback(stage.callback)
                return stage
            }
        }
    }

    companion object {
        init {
            Utils.init()
        }
    }
}
