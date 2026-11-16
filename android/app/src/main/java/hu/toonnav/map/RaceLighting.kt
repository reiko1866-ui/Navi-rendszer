package hu.toonnav.map

import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import com.google.android.filament.Texture
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Daylight image-based light aligned with the directional sun. The sky is a
 * generated probe, not a photograph of a real place, so the reflections match
 * the sun that casts the shadows.
 */
object RaceLighting {
    const val sunX = 0.1798f
    const val sunY = -0.8590f
    const val sunZ = 0.4794f
    private val sh = floatArrayOf(
        17030.7278f, 22285.6004f, 32573.3234f,
        -13180.2114f, -18043.4965f, -28534.8983f,
        -2271.8660f, -2271.8660f, -2271.8660f,
        851.9498f, 851.9498f, 851.9498f,
        -1620.9110f, -1620.9110f, -1620.9110f,
        4322.4293f, 4322.4293f, 4322.4293f,
        470.8415f, 85.8674f, -1069.0453f,
        -904.6945f, -904.6945f, -904.6945f,
        -1257.7383f, -1924.5318f, -3924.8958f
    )

    class Probe(
        val indirect: IndirectLight,
        val sky: Skybox,
        val reflections: Texture?,
        val environment: Texture?
    )

    fun create(engine: Engine, hdr: ByteArray): Probe? {
        val buffer = ByteBuffer.allocateDirect(hdr.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(hdr)
        buffer.flip()
        val equirect = HDRLoader.createTexture(engine, buffer, HDRLoader.Options()) ?: return null
        val context = IBLPrefilterContext(engine)
        val toCube = IBLPrefilterContext.EquirectangularToCubemap(context)
        val cube = try {
            toCube.run(equirect)
        } catch (failed: Throwable) {
            null
        }
        toCube.destroy()
        if (cube == null) {
            context.destroy()
            engine.destroyTexture(equirect)
            return null
        }
        val filter = IBLPrefilterContext.SpecularFilter(context)
        val reflections = try {
            filter.run(cube)
        } catch (failed: Throwable) {
            null
        }
        filter.destroy()
        context.destroy()
        engine.destroyTexture(equirect)
        if (reflections == null) {
            engine.destroyTexture(cube)
            return null
        }
        val indirect = IndirectLight.Builder()
            .reflections(reflections)
            .irradiance(3, sh)
            .intensity(1f)
            .build(engine)
        val sky = Skybox.Builder().environment(cube).showSun(false).intensity(1f).build(engine)
        return Probe(indirect, sky, reflections, cube)
    }
}
