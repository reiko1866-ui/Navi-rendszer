package hu.toonnav.map

import android.os.Handler
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Large turntable of the same formula model used on the road. It does not
 * touch navigation. If Filament cannot start, the caller keeps the flat icon.
 */
class RaceGarageStage(
    private val engine: Engine,
    private val swapView: View,
    private val renderer: Renderer,
    private val scene: Scene,
    private val camera: Camera,
    private val loader: AssetLoader,
    private val resources: ResourceLoader,
    private val materials: UbershaderProvider,
    private val asset: FilamentAsset,
    private val sky: Skybox,
    private val indirect: IndirectLight,
    private val painter: LiveryPainter
) {
    var surfaceHelper: UiHelper? = null
    private var swapChain: com.google.android.filament.SwapChain? = null
    private var closed = false
    private var width = 0
    private var height = 0
    private var yaw = 0.55
    private var lastFrame = 0L
    private val choreographer = Choreographer.getInstance()
    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            draw(frameTimeNanos)
            if (!closed) choreographer.postFrameCallback(this)
        }
    }

    fun applyLivery(car: GarageFleet.Car) {
        painter.apply(asset, car)
    }

    fun destroy() {
        closed = true
        choreographer.removeFrameCallback(callback)
        painter.destroy()
        loader.destroyAsset(asset)
        resources.destroy()
        loader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        scene.skybox = null
        scene.indirectLight = null
        engine.destroySkybox(sky)
        engine.destroyIndirectLight(indirect)
        swapChain?.let { engine.destroySwapChain(it) }
        engine.destroyRenderer(renderer)
        engine.destroyView(swapView)
        engine.destroyScene(scene)
        engine.destroy()
    }

    private fun draw(frameTimeNanos: Long) {
        val chain = swapChain ?: return
        if (width < 2 || height < 2 || closed) return
        val step = if (lastFrame == 0L) 0.0 else ((frameTimeNanos - lastFrame) / 1_000_000_000.0).coerceIn(0.0, 0.05)
        lastFrame = frameTimeNanos
        yaw += step * 0.35
        val eyeX = (sin(yaw) * 7.6).toFloat()
        val eyeZ = (cos(yaw) * 7.6).toFloat()
        camera.setProjection(28.0, width.toDouble() / height.toDouble(), 0.08, 80.0, Camera.Fov.VERTICAL)
        camera.setExposure(PlainWorld.aperture, PlainWorld.shutter, PlainWorld.sensitivity)
        camera.setModelMatrix(RaceCamera.lookingModel(eyeX, 1.9f, eyeZ, 0f, 0.42f, 0.2f))
        swapView.viewport = Viewport(0, 0, width, height)
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(swapView)
            renderer.endFrame()
        }
    }

    private fun attach(surface: Surface) {
        if (closed) return
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(surface)
    }

    companion object {
        fun start(surface: SurfaceView, carBytes: ByteArray, car: GarageFleet.Car, host: android.app.Activity, handler: Handler): RaceGarageStage? {
            Utils.init()
            Gltfio.init()
            val engine = Engine.Builder().backend(Engine.Backend.OPENGL).build()
            return try {
                val provider = UbershaderProvider(engine)
                val loader = AssetLoader(engine, provider, EntityManager.get())
                val resources = ResourceLoader(engine)
                val buffer = ByteBuffer.allocateDirect(carBytes.size).order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(carBytes)
                buffer.flip()
                val asset = loader.createAsset(buffer) ?: throw IllegalStateException("car")
                resources.loadResources(asset)
                asset.releaseSourceData()
                val scene = engine.createScene()
                val view = engine.createView()
                view.scene = scene
                view.blendMode = View.BlendMode.OPAQUE
                view.antiAliasing = View.AntiAliasing.FXAA
                view.setPostProcessingEnabled(true)
                view.setShadowingEnabled(false)
                val ao = view.ambientOcclusionOptions
                ao.enabled = false
                view.ambientOcclusionOptions = ao
                val sky = Skybox.Builder().color(0.62f, 0.72f, 0.82f, 1f).build(engine)
                scene.skybox = sky
                val fill = FloatArray(27)
                fill[0] = 0.55f
                fill[1] = 0.55f
                fill[2] = 0.58f
                val indirect = IndirectLight.Builder().irradiance(3, fill).intensity(1f).build(engine)
                scene.indirectLight = indirect
                val light = EntityManager.get().create()
                LightManager.Builder(LightManager.Type.SUN)
                    .color(1f, 0.97f, 0.92f)
                    .intensity(PlainWorld.sunLux)
                    .direction(0.35f, -0.86f, 0.28f)
                    .castShadows(false)
                    .build(engine, light)
                scene.addEntity(light)
                scene.addEntities(asset.entities)
                val camera = engine.createCamera(EntityManager.get().create())
                camera.setExposure(PlainWorld.aperture, PlainWorld.shutter, PlainWorld.sensitivity)
                view.camera = camera
                val painter = LiveryPainter(engine)
                val stage = RaceGarageStage(
                    engine, view, engine.createRenderer(), scene, camera, loader, resources, provider, asset, sky, indirect, painter
                )
                stage.applyLivery(car)
                val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
                stage.surfaceHelper = helper
                helper.renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(nativeWindow: Surface) {
                        handler.post { stage.attach(nativeWindow) }
                    }

                    override fun onDetachedFromSurface() {
                        handler.post {
                            if (stage.closed) return@post
                            stage.swapChain?.let { engine.destroySwapChain(it) }
                            stage.swapChain = null
                        }
                    }

                    override fun onResized(nextWidth: Int, nextHeight: Int) {
                        handler.post {
                            stage.width = nextWidth
                            stage.height = nextHeight
                        }
                    }
                }
                host.runOnUiThread { helper.attachTo(surface) }
                stage.choreographer.postFrameCallback(stage.callback)
                stage
            } catch (failed: Throwable) {
                engine.destroy()
                null
            }
        }
    }
}
