package hu.toonnav.map

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws [PlainWorld] with one unlit material and a color parameter.
 * The vertex buffer is position only, so it does not depend on the glTF
 * ubershader layout, tangents, or any texture unit.
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
    val chunkKeys: Set<Int> get() = chunks.keys

    init {
        val buffer = ByteBuffer.allocateDirect(materialBytes.size).order(ByteOrder.nativeOrder())
        buffer.put(materialBytes)
        buffer.flip()
        material = Material.Builder().payload(buffer, buffer.remaining()).build(engine)
        for (swatch in PlainWorld.all) {
            if (swatch === PlainWorld.sky || swatch === PlainWorld.nightSky) continue
            val instance = material.createInstance()
            instance.setParameter("baseColor", swatch.red, swatch.green, swatch.blue, 1f)
            instance.setCullingMode(Material.CullingMode.NONE)
            instances[swatch.name] = instance
        }
        buildSky()
    }

    /** Moves the sky rings with the car. The mesh is not rebuilt. */
    fun placeSky(east: Double, north: Double) {
        if (skyParts.isEmpty()) return
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            east.toFloat(), 0f, north.toFloat(), 1f
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
        engine.destroyMaterial(material)
    }

    private fun upload(mesh: RaceMesh, fog: Boolean): Part? {
        if (mesh.vertexCount < 3 || mesh.indices.isEmpty()) return null
        val instance = instances[mesh.material] ?: return null
        val vertices = ByteBuffer.allocateDirect(mesh.vertexCount * 12).order(ByteOrder.nativeOrder())
        for (index in mesh.positions.indices) vertices.putFloat(mesh.positions[index])
        vertices.flip()
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(mesh.vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
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

    private fun buildSky() {
        val radius = 340.0
        val segments = 24
        val heights = listOf(0f to 28f, 28f to 74f, 74f to 170f)
        for ((index, range) in heights.withIndex()) {
            val swatch = PlainWorld.dayGradient[index]
            val instance = material.createInstance()
            instance.setParameter("baseColor", swatch.red, swatch.green, swatch.blue, 1f)
            instance.setCullingMode(Material.CullingMode.NONE)
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
