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
    }

    fun replaceAll(built: Map<Int, List<RaceMesh>>) {
        val stale = chunks.keys.toList()
        for (key in stale) remove(chunks.remove(key))
        uploadChunks(built)
    }

    fun uploadChunks(built: Map<Int, List<RaceMesh>>) {
        for ((index, meshes) in built) {
            remove(chunks.remove(index))
            val parts = meshes.mapNotNull { upload(it) }
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
        for (instance in instances.values) engine.destroyMaterialInstance(instance)
        instances.clear()
        engine.destroyMaterial(material)
    }

    private fun upload(mesh: RaceMesh): Part? {
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
            .fog(true)
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

    private class Part(val entity: Int, val vertices: VertexBuffer, val indices: IndexBuffer)
}
