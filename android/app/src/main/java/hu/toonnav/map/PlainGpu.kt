package hu.toonnav.map

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws [PlainWorld] with one unlit material. A runtime RGBA grain multiplies
 * the color on roads, shoulders and ground. Markings stay flat. The grain is
 * an RGBA image built in memory, not a compressed map and not a normal map.
 * A white texture is bound first, so a failed grain still leaves the flat color.
 */
class PlainGpu(
    private val engine: Engine,
    private val scene: Scene,
    materialBytes: ByteArray
) {
    private val material: Material
    private val instances = HashMap<String, MaterialInstance>()
    private val chunks = HashMap<Int, List<Part>>()
    private val skyParts = ArrayList<Part>()
    private val retained = ArrayList<ByteBuffer>()
    private var white: Texture? = null
    private var grain: Texture? = null
    val chunkKeys: Set<Int> get() = chunks.keys

    init {
        val buffer = ByteBuffer.allocateDirect(materialBytes.size).order(ByteOrder.nativeOrder())
        buffer.put(materialBytes)
        buffer.flip()
        material = Material.Builder().payload(buffer, buffer.remaining()).build(engine)
        val sampler = TextureSampler(
            TextureSampler.MinFilter.LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.REPEAT
        )
        white = solidTexture(255)
        grain = try {
            grainTexture(128)
        } catch (_: Throwable) {
            null
        }
        for (swatch in PlainWorld.all) {
            if (swatch === PlainWorld.sky || swatch === PlainWorld.nightSky) continue
            val instance = material.createInstance()
            instance.setParameter("baseColor", swatch.red, swatch.green, swatch.blue, 1f)
            instance.setCullingMode(Material.CullingMode.NONE)
            bind(instance, swatch.name, sampler)
            instances[swatch.name] = instance
        }
        buildSky(sampler)
    }

    /** Moves the sky rings with the car. The mesh is not rebuilt. */
    fun placeSky(east: Double, north: Double, altitude: Double = 0.0) {
        if (skyParts.isEmpty()) return
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            east.toFloat(), altitude.toFloat(), north.toFloat(), 1f
        )
        val transforms = engine.transformManager
        for (part in skyParts) {
            var instance = transforms.getInstance(part.entity)
            if (instance == 0) instance = transforms.create(part.entity)
            transforms.setTransform(instance, matrix)
        }
    }

    fun applySky(night: Boolean) {
        val gradient = if (night) PlainWorld.nightGradient else PlainWorld.dayGradient
        for (index in gradient.indices) {
            val color = gradient[index]
            val name = PlainWorld.dayGradient[index].name
            instances[name]?.setParameter("baseColor", color.red, color.green, color.blue, 1f)
        }
    }

    fun replaceAll(built: Map<Int, List<RaceMesh>>) {
        val stale = chunks.keys.toList()
        for (key in stale) remove(chunks.remove(key))
        uploadChunks(built)
    }

    fun uploadChunks(built: Map<Int, List<RaceMesh>>) {
        for ((index, meshes) in built) {
            remove(chunks.remove(index))
            val parts = meshes.mapNotNull { upload(it, fog = true) }
            if (parts.isNotEmpty()) chunks[index] = parts
        }
    }

    fun dropChunksExcept(needed: IntRange) {
        val keep = needed.toSet()
        val stale = chunks.keys.filter { it !in keep }
        for (key in stale) remove(chunks.remove(key))
    }

    fun destroy() {
        val stale = chunks.keys.toList()
        for (key in stale) remove(chunks.remove(key))
        remove(skyParts)
        skyParts.clear()
        for (instance in instances.values) engine.destroyMaterialInstance(instance)
        instances.clear()
        white?.let { engine.destroyTexture(it) }
        grain?.let { engine.destroyTexture(it) }
        white = null
        grain = null
        retained.clear()
        engine.destroyMaterial(material)
    }

    private fun upload(mesh: RaceMesh, fog: Boolean): Part? {
        if (mesh.vertexCount < 3 || mesh.indices.isEmpty()) return null
        val instance = instances[mesh.material] ?: return null
        val stride = 20
        val vertices = ByteBuffer.allocateDirect(mesh.vertexCount * stride).order(ByteOrder.nativeOrder())
        for (index in 0 until mesh.vertexCount) {
            vertices.putFloat(mesh.positions[index * 3])
            vertices.putFloat(mesh.positions[index * 3 + 1])
            vertices.putFloat(mesh.positions[index * 3 + 2])
            vertices.putFloat(mesh.uvs[index * 2])
            vertices.putFloat(mesh.uvs[index * 2 + 1])
        }
        vertices.flip()
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(mesh.vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, stride)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 12, stride)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, vertices)
        val indexBytes = ByteBuffer.allocateDirect(mesh.indices.size * 4).order(ByteOrder.nativeOrder())
        indexBytes.asIntBuffer().put(mesh.indices)
        indexBytes.rewind()
        val indexBuffer = IndexBuffer.Builder()
            .indexCount(mesh.indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, indexBytes)
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, instance)
            .boundingBox(bounds(mesh))
            .castShadows(false)
            .receiveShadows(false)
            .culling(true)
            .fog(fog)
            .build(engine, entity)
        scene.addEntity(entity)
        return Part(entity, vertexBuffer, indexBuffer)
    }

    private fun bounds(mesh: RaceMesh): Box {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (index in mesh.positions.indices step 3) {
            val x = mesh.positions[index]
            val y = mesh.positions[index + 1]
            val z = mesh.positions[index + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
        }
        return Box(
            (minX + maxX) * 0.5f,
            (minY + maxY) * 0.5f,
            (minZ + maxZ) * 0.5f,
            (maxX - minX) * 0.5f + 1f,
            (maxY - minY) * 0.5f + 1f,
            (maxZ - minZ) * 0.5f + 1f
        )
    }

    private fun remove(parts: List<Part>?) {
        if (parts == null) return
        for (part in parts) {
            scene.removeEntity(part.entity)
            engine.renderableManager.destroy(part.entity)
            engine.destroyVertexBuffer(part.vertices)
            engine.destroyIndexBuffer(part.indices)
            engine.destroyEntity(part.entity)
        }
    }

    private fun bind(instance: MaterialInstance, name: String, sampler: TextureSampler) {
        val fallback = white
        if (fallback != null) {
            try {
                instance.setParameter("albedo", fallback, sampler)
                instance.setParameter("detail", 0f)
            } catch (_: Throwable) {
                return
            }
        }
        val detailed = grain
        if (detailed != null && PlainWorld.takesDetail(name)) {
            try {
                instance.setParameter("albedo", detailed, sampler)
                instance.setParameter("detail", 1f)
            } catch (_: Throwable) {
                try {
                    instance.setParameter("detail", 0f)
                } catch (_: Throwable) {
                    // The flat color remains. A rejected grain must not leave a missing texture.
                }
            }
        }
    }

    private fun solidTexture(value: Int): Texture {
        val pixels = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        retained.add(pixels)
        val byte = value.coerceIn(0, 255).toByte()
        pixels.put(byte)
        pixels.put(byte)
        pixels.put(byte)
        pixels.put(255.toByte())
        pixels.flip()
        return uploadTexture(1, pixels)
    }

    private fun grainTexture(size: Int): Texture {
        val pixels = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
        retained.add(pixels)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val fine = hash(x, y)
                val coarse = hash(x / 4, y / 4)
                val stone = 168 + ((fine * 3 + coarse) / 4) * 70 / 255
                pixels.put(stone.coerceIn(150, 245).toByte())
                pixels.put(stone.coerceIn(150, 245).toByte())
                pixels.put(stone.coerceIn(150, 245).toByte())
                pixels.put(255.toByte())
            }
        }
        pixels.flip()
        return uploadTexture(size, pixels)
    }

    private fun uploadTexture(size: Int, pixels: ByteBuffer): Texture {
        val texture = Texture.Builder()
            .width(size)
            .height(size)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.RGBA8)
            .build(engine)
        texture.setImage(
            engine,
            0,
            Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE)
        )
        return texture
    }

    private fun hash(x: Int, y: Int): Int {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        return (n ushr 16) and 255
    }

    private fun buildSky(sampler: TextureSampler) {
        val radius = 340.0
        val segments = 24
        val splits = floatArrayOf(0f, 24f, 52f, 88f, 136f, 210f)
        val count = minOf(PlainWorld.dayGradient.size, splits.size - 1)
        for (index in 0 until count) {
            val range = splits[index] to splits[index + 1]
            val swatch = PlainWorld.dayGradient[index]
            val instance = material.createInstance()
            instance.setParameter("baseColor", swatch.red, swatch.green, swatch.blue, 1f)
            instance.setCullingMode(Material.CullingMode.NONE)
            bind(instance, swatch.name, sampler)
            instances[swatch.name] = instance
            val mesh = cylinder(swatch.name, radius, range.first, range.second, segments)
            val part = upload(mesh, fog = false) ?: continue
            skyParts.add(part)
        }
    }

    private fun cylinder(name: String, radius: Double, y0: Float, y1: Float, segments: Int): RaceMesh {
        val rings = segments + 1
        val positions = FloatArray(rings * 2 * 3)
        val normals = FloatArray(positions.size)
        val uvs = FloatArray(rings * 2 * 2)
        val indices = IntArray(segments * 6)
        for (step in 0 until rings) {
            val angle = step.toDouble() / segments * Math.PI * 2.0
            val x = (cos(angle) * radius).toFloat()
            val z = (sin(angle) * radius).toFloat()
            val bottom = step * 2
            val top = bottom + 1
            positions[bottom * 3] = x
            positions[bottom * 3 + 1] = y0
            positions[bottom * 3 + 2] = z
            positions[top * 3] = x
            positions[top * 3 + 1] = y1
            positions[top * 3 + 2] = z
            normals[bottom * 3 + 1] = 1f
            normals[top * 3 + 1] = 1f
        }
        for (step in 0 until segments) {
            val a = step * 2
            val b = a + 1
            val c = a + 3
            val d = a + 2
            val offset = step * 6
            indices[offset] = a
            indices[offset + 1] = b
            indices[offset + 2] = c
            indices[offset + 3] = a
            indices[offset + 4] = c
            indices[offset + 5] = d
        }
        return RaceMesh(name, positions, normals, uvs, indices)
    }

    private class Part(val entity: Int, val vertices: VertexBuffer, val indices: IndexBuffer)
}
