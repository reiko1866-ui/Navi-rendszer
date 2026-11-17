package hu.toonnav.map

import com.google.android.filament.Box
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Road, verge, and scenery geometry. The material library stays loaded.
 * A corridor move uploads only the new chunk buffers.
 */
class RoadGpu(
    private val engine: Engine,
    private val scene: Scene,
    asset: FilamentAsset
) {
    private val materials = HashMap<String, MaterialInstance>()
    private val owned = ArrayList<MaterialInstance>()
    private val chunks = HashMap<Int, List<Part>>()
    private val scenery = HashMap<Int, List<Part>>()
    private var horizon: List<Part> = emptyList()
    val chunkKeys: Set<Int> get() = chunks.keys

    init {
        val declared = RaceGeometry.materials()
        val instances = asset.instance.materialInstances
        for (index in instances.indices) {
            val instance = instances[index]
            val name = instance.name.ifBlank { declared.getOrNull(index)?.name.orEmpty() }
            if (name.isNotBlank()) materials.putIfAbsent(name, instance)
        }
        val asphalt = materials["asphalt"]
        if (asphalt != null) {
            val names = arrayOf("asphalt", "asphaltWarm", "asphaltCool", "asphaltDark")
            val tint = arrayOf(
                floatArrayOf(0.52f, 0.52f, 0.50f),
                floatArrayOf(0.56f, 0.53f, 0.48f),
                floatArrayOf(0.48f, 0.50f, 0.52f),
                floatArrayOf(0.40f, 0.40f, 0.39f)
            )
            for (index in names.indices) {
                val copy = MaterialInstance.duplicate(asphalt, names[index])
                try {
                    copy.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, tint[index][0], tint[index][1], tint[index][2], 1f)
                    copy.setParameter("metallicFactor", 0f)
                    copy.setParameter("roughnessFactor", 0.88f)
                } catch (_: Throwable) {
                    // The shared asphalt instance still has the light texture if a parameter is rejected.
                }
                copy.setCullingMode(Material.CullingMode.NONE)
                copy.setPolygonOffset(-0.6f, -1f)
                materials[names[index]] = copy
                owned.add(copy)
            }
        }
        for (name in arrayOf("grass", "shoulder")) {
            val instance = materials[name] ?: continue
            val metallic = RoadShade.groundMetallic(name) ?: continue
            try {
                instance.setParameter("metallicFactor", metallic)
            } catch (_: Throwable) {
                // A rejected factor leaves the glTF value. The ORM map still zeroes metal when it loads.
            }
        }
        for (instance in materials.values) instance.setCullingMode(Material.CullingMode.NONE)
        materials["marking"]?.setPolygonOffset(-1.2f, -2f)
        materials["edge"]?.setPolygonOffset(-0.8f, -1.5f)
        materials["kerbRed"]?.setPolygonOffset(-1.5f, -2f)
        materials["kerbWhite"]?.setPolygonOffset(-1.5f, -2f)
    }

    fun uploadChunks(built: Map<Int, List<RaceMesh>>) {
        for ((index, meshes) in built) {
            remove(chunks.remove(index))
            val parts = meshes.mapNotNull { upload(it, cast = false, contact = false) }
            if (parts.isNotEmpty()) chunks[index] = parts
        }
    }

    fun dropChunksExcept(needed: IntRange) {
        val keep = needed.toSet()
        val stale = chunks.keys.filter { it !in keep }
        for (key in stale) remove(chunks.remove(key))
    }

    fun replaceAll(built: Map<Int, List<RaceMesh>>, nextHorizon: RaceMesh?) {
        val stale = chunks.keys.toList()
        for (key in stale) remove(chunks.remove(key))
        uploadChunks(built)
        replaceHorizon(nextHorizon)
    }

    fun replaceHorizon(nextHorizon: RaceMesh?) {
        remove(horizon)
        horizon = if (nextHorizon == null) emptyList() else listOfNotNull(upload(nextHorizon, cast = false, contact = false))
    }

    fun uploadScenery(built: Map<Int, List<RaceMesh>>) {
        for ((index, meshes) in built) {
            remove(scenery.remove(index))
            val parts = meshes.mapNotNull { upload(it, cast = it.material == "trunk" || it.material.startsWith("canopy"), contact = false) }
            if (parts.isNotEmpty()) scenery[index] = parts
        }
    }

    fun dropSceneryExcept(needed: IntRange) {
        val keep = needed.toSet()
        val stale = scenery.keys.filter { it !in keep }
        for (key in stale) remove(scenery.remove(key))
    }

    fun dropAllScenery() {
        val stale = scenery.keys.toList()
        for (key in stale) remove(scenery.remove(key))
    }

    fun destroy() {
        val stale = chunks.keys.toList()
        for (key in stale) remove(chunks.remove(key))
        dropAllScenery()
        remove(horizon)
        horizon = emptyList()
        for (instance in owned) engine.destroyMaterialInstance(instance)
        owned.clear()
    }

    private fun upload(mesh: RaceMesh, cast: Boolean, contact: Boolean): Part? {
        if (mesh.vertexCount < 3 || mesh.indices.isEmpty()) return null
        val material = materialFor(mesh.material) ?: return null
        val quaternions = RoadFrame.quaternions(mesh)
        val vertices = RoadVertices.bytes(mesh, quaternions)
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(mesh.vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, RoadVertices.stride)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, RoadVertices.stride)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, 36, RoadVertices.stride)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, RoadVertices.stride)
            .attribute(VertexBuffer.VertexAttribute.UV1, 0, VertexBuffer.AttributeType.FLOAT2, 52, RoadVertices.stride)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, vertices)
        val indexBytes = ByteBuffer.allocateDirect(mesh.indices.size * 4).order(ByteOrder.nativeOrder())
        val intView = indexBytes.asIntBuffer()
        intView.put(mesh.indices)
        indexBytes.rewind()
        val indexBuffer = IndexBuffer.Builder()
            .indexCount(mesh.indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, indexBytes)
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, material)
            .boundingBox(bounds(mesh))
            .castShadows(cast)
            .receiveShadows(true)
            .screenSpaceContactShadows(contact)
            .culling(true)
            .fog(true)
            .build(engine, entity)
        scene.addEntity(entity)
        return Part(entity, vertexBuffer, indexBuffer)
    }

    private fun materialFor(name: String): MaterialInstance? {
        materials[name]?.let { return it }
        if (name.startsWith("asphalt")) return materials["asphalt"]
        return null
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
            (maxX - minX) * 0.5f + 0.5f,
            (maxY - minY) * 0.5f + 0.5f,
            (maxZ - minZ) * 0.5f + 0.5f
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

/**
 * Filament's ubershader vertex layout always carries COLOR and UV1
 * (UbershaderProvider.needsDummyData), even when the glTF primitive does not
 * declare them. This buffer supplies white so the draw stays valid.
 * White vertex color does not paint the road. The asphalt, grass, and shoulder
 * disappeared because their occlusion sampler is black on OpenGL ES when the
 * KTX2 texture does not bind, and that zero multiplies the lit color away.
 * The driving materials therefore have no textures.
 */
internal object RoadVertices {
    const val stride = 60

    fun bytes(mesh: RaceMesh, quaternions: FloatArray): ByteBuffer {
        val vertices = ByteBuffer.allocateDirect(mesh.vertexCount * stride).order(ByteOrder.nativeOrder())
        for (index in 0 until mesh.vertexCount) {
            vertices.putFloat(mesh.positions[index * 3])
            vertices.putFloat(mesh.positions[index * 3 + 1])
            vertices.putFloat(mesh.positions[index * 3 + 2])
            vertices.putFloat(quaternions[index * 4])
            vertices.putFloat(quaternions[index * 4 + 1])
            vertices.putFloat(quaternions[index * 4 + 2])
            vertices.putFloat(quaternions[index * 4 + 3])
            vertices.putFloat(mesh.uvs[index * 2])
            vertices.putFloat(mesh.uvs[index * 2 + 1])
            vertices.putFloat(1f)
            vertices.putFloat(1f)
            vertices.putFloat(1f)
            vertices.putFloat(1f)
            vertices.putFloat(mesh.uvs[index * 2])
            vertices.putFloat(mesh.uvs[index * 2 + 1])
        }
        vertices.flip()
        return vertices
    }
}
