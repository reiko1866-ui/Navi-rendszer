package hu.toonnav.map

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Writes a binary glTF of the prototype meshes. Filament loads this as real
 * geometry; it is not a picture of a car.
 */
object RaceGlb {
    fun encode(model: RaceModel): ByteArray {
        val bin = ByteBuffer.allocate(estimate(model)).order(ByteOrder.LITTLE_ENDIAN)
        val bufferViews = JSONArray()
        val accessors = JSONArray()
        val materials = JSONArray()
        val materialIndex = HashMap<String, Int>()
        for (material in model.materials) {
            materialIndex[material.name] = materials.length()
            materials.put(materialJson(material))
        }
        val worldPrimitives = JSONArray()
        for (mesh in model.world) primitive(mesh, bin, bufferViews, accessors, materialIndex, worldPrimitives)
        val meshLength = bin.position()
        val png = asphaltPng()
        val paddedMesh = align(meshLength)
        val bytes = ByteArray(paddedMesh + align(png.size))
        bin.flip()
        bin.get(bytes, 0, meshLength)
        System.arraycopy(png, 0, bytes, paddedMesh, png.size)
        bufferViews.put(JSONObject().put("buffer", 0).put("byteOffset", paddedMesh).put("byteLength", png.size))
        val asphalt = materialIndex["asphalt"]
        if (asphalt != null) {
            materials.getJSONObject(asphalt).getJSONObject("pbrMetallicRoughness")
                .put("baseColorTexture", JSONObject().put("index", 0))
        }
        val root = JSONObject()
        root.put("asset", JSONObject().put("version", "2.0").put("generator", "ToonNav RacePrototype"))
        root.put("scene", 0)
        root.put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
        root.put("nodes", JSONArray().put(JSONObject().put("name", "road").put("mesh", 0)))
        root.put("meshes", JSONArray().put(JSONObject().put("name", "road").put("primitives", worldPrimitives)))
        root.put("materials", materials)
        root.put("buffers", JSONArray().put(JSONObject().put("byteLength", bytes.size)))
        root.put("bufferViews", bufferViews)
        root.put("accessors", accessors)
        root.put("images", JSONArray().put(JSONObject().put("mimeType", "image/png").put("bufferView", bufferViews.length() - 1)))
        root.put("textures", JSONArray().put(JSONObject().put("source", 0)))
        return pack(root.toString().toByteArray(Charsets.UTF_8), bytes)
    }

    fun json(glb: ByteArray): JSONObject {
        val buffer = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        buffer.int
        buffer.int
        buffer.int
        val length = buffer.int
        buffer.int
        val json = ByteArray(length)
        buffer.get(json)
        val text = json.toString(Charsets.UTF_8).trim()
        return JSONObject(text)
    }

    private fun primitive(
        mesh: RaceMesh,
        bin: ByteBuffer,
        views: JSONArray,
        accessors: JSONArray,
        materials: Map<String, Int>,
        primitives: JSONArray
    ) {
        if (mesh.vertexCount < 3) return
        pad(bin)
        val positionOffset = bin.position()
        for (value in mesh.positions) bin.putFloat(value)
        val positionLength = bin.position() - positionOffset
        pad(bin)
        val normalOffset = bin.position()
        for (value in mesh.normals) bin.putFloat(value)
        val normalLength = bin.position() - normalOffset
        pad(bin)
        val uvOffset = bin.position()
        for (value in mesh.uvs) bin.putFloat(value)
        val uvLength = bin.position() - uvOffset
        pad(bin)
        val indexOffset = bin.position()
        for (value in mesh.indices) bin.putInt(value)
        val indexLength = bin.position() - indexOffset
        val positionView = views.length()
        views.put(view(positionOffset, positionLength))
        val normalView = views.length()
        views.put(view(normalOffset, normalLength))
        val uvView = views.length()
        views.put(view(uvOffset, uvLength))
        val indexView = views.length()
        views.put(view(indexOffset, indexLength))
        val bounds = RaceGeometry.bounds(mesh)
        val positionAccessor = accessors.length()
        accessors.put(accessor(positionView, mesh.vertexCount, bounds))
        val normalAccessor = accessors.length()
        accessors.put(JSONObject().put("bufferView", normalView).put("componentType", 5126).put("count", mesh.vertexCount).put("type", "VEC3"))
        val uvAccessor = accessors.length()
        accessors.put(JSONObject().put("bufferView", uvView).put("componentType", 5126).put("count", mesh.vertexCount).put("type", "VEC2"))
        val indexAccessor = accessors.length()
        accessors.put(JSONObject().put("bufferView", indexView).put("componentType", 5125).put("count", mesh.indices.size).put("type", "SCALAR"))
        primitives.put(JSONObject()
            .put("attributes", JSONObject().put("POSITION", positionAccessor).put("NORMAL", normalAccessor).put("TEXCOORD_0", uvAccessor))
            .put("indices", indexAccessor)
            .put("material", materials[mesh.material] ?: 0))
    }

    private fun materialJson(material: RaceMaterial): JSONObject {
        return JSONObject()
            .put("name", material.name)
            .put("doubleSided", true)
            .put("pbrMetallicRoughness", JSONObject()
                .put("baseColorFactor", JSONArray().put(material.red.toDouble()).put(material.green.toDouble()).put(material.blue.toDouble()).put(1.0))
                .put("metallicFactor", material.metallic.toDouble())
                .put("roughnessFactor", material.roughness.toDouble()))
    }

    private fun view(offset: Int, length: Int): JSONObject {
        return JSONObject().put("buffer", 0).put("byteOffset", offset).put("byteLength", length)
    }

    private fun accessor(view: Int, count: Int, bounds: FloatArray): JSONObject {
        return JSONObject()
            .put("bufferView", view)
            .put("componentType", 5126)
            .put("count", count)
            .put("type", "VEC3")
            .put("min", JSONArray().put(bounds[0].toDouble()).put(bounds[1].toDouble()).put(bounds[2].toDouble()))
            .put("max", JSONArray().put(bounds[3].toDouble()).put(bounds[4].toDouble()).put(bounds[5].toDouble()))
    }

    private fun pack(json: ByteArray, bin: ByteArray): ByteArray {
        val jsonPad = (4 - (json.size % 4)) % 4
        val binPad = (4 - (bin.size % 4)) % 4
        val total = 12 + 8 + json.size + jsonPad + 8 + bin.size + binPad
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67)
        out.putInt(2)
        out.putInt(total)
        out.putInt(json.size + jsonPad)
        out.putInt(0x4E4F534A)
        out.put(json)
        repeat(jsonPad) { out.put(' '.code.toByte()) }
        out.putInt(bin.size + binPad)
        out.putInt(0x004E4942)
        out.put(bin)
        repeat(binPad) { out.put(0) }
        return out.array()
    }

    private fun estimate(model: RaceModel): Int {
        var bytes = 256
        for (mesh in model.car + model.world) {
            bytes += mesh.positions.size * 4 + mesh.normals.size * 4 + mesh.uvs.size * 4 + mesh.indices.size * 4 + 64
        }
        return bytes
    }

    private fun pad(buffer: ByteBuffer) {
        while (buffer.position() % 4 != 0) buffer.put(0)
    }

    private fun align(value: Int): Int = value + (4 - value % 4) % 4

    fun asphaltPng(size: Int = 128): ByteArray {
        val raw = ByteArray(size * (1 + size * 3))
        var cursor = 0
        var state = 0x1234567
        for (y in 0 until size) {
            raw[cursor++] = 0
            for (x in 0 until size) {
                state = state * 1103515245 + 12345
                val grain = ((state ushr 16) and 31) - 12
                val streak = if (y % 17 == 0 && x % 5 != 0) -18 else 0
                val patch = if (((x / 9) + (y / 13)) % 7 == 0) -10 else 0
                val value = (78 + grain + streak + patch).coerceIn(28, 120)
                raw[cursor++] = value.toByte()
                raw[cursor++] = (value + 1).coerceAtMost(125).toByte()
                raw[cursor++] = (value - 2).coerceAtLeast(20).toByte()
            }
        }
        return png(size, raw)
    }

    private fun png(size: Int, raw: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val compressed = ByteArray(raw.size + 64)
        val length = deflater.deflate(compressed)
        deflater.end()
        val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val ihdr = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
            .putInt(size).putInt(size).put(8).put(2).put(0).put(0).put(0).array()
        return signature + chunk("IHDR", ihdr) + chunk("IDAT", compressed.copyOf(length)) + chunk("IEND", ByteArray(0))
    }

    fun carbonPng(size: Int = 64): ByteArray {
        val raw = ByteArray(size * (1 + size * 3))
        var cursor = 0
        for (y in 0 until size) {
            raw[cursor++] = 0
            for (x in 0 until size) {
                val weave = ((x / 4) + (y / 4)) % 2 == 0
                val thread = if ((x + y) % 4 == 0) 18 else 0
                val value = ((if (weave) 28 else 18) + thread).coerceIn(0, 255)
                raw[cursor++] = value.toByte()
                raw[cursor++] = value.toByte()
                raw[cursor++] = (value + 2).coerceAtMost(255).toByte()
            }
        }
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val compressed = ByteArray(raw.size + 64)
        val length = deflater.deflate(compressed)
        deflater.end()
        val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val ihdr = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
            .putInt(size).putInt(size).put(8).put(2).put(0).put(0).put(0).array()
        return signature + chunk("IHDR", ihdr) + chunk("IDAT", compressed.copyOf(length)) + chunk("IEND", ByteArray(0))
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(12 + data.size).order(ByteOrder.BIG_ENDIAN)
        out.putInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.put(typeBytes)
        out.put(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.putInt(crc.value.toInt())
        return out.array()
    }
}
