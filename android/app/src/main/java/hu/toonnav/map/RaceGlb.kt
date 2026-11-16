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
    fun encode(model: RaceModel, images: Map<String, ByteArray> = emptyMap()): ByteArray {
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
        val packedImages = ArrayList<Pair<String, ByteArray>>()
        for (name in imageOrder) {
            val bytes = images[name] ?: continue
            packedImages.add(name to bytes)
        }
        val imageOffsets = ArrayList<Int>()
        var cursor = align(meshLength)
        for (image in packedImages) {
            cursor = align(cursor)
            imageOffsets.add(cursor)
            cursor += image.second.size
        }
        val bytes = ByteArray(cursor.coerceAtLeast(align(meshLength)))
        bin.flip()
        if (meshLength > 0) bin.get(bytes, 0, meshLength)
        val imageViews = ArrayList<Int>()
        for (index in packedImages.indices) {
            val offset = imageOffsets[index]
            val image = packedImages[index].second
            System.arraycopy(image, 0, bytes, offset, image.size)
            bufferViews.put(JSONObject().put("buffer", 0).put("byteOffset", offset).put("byteLength", image.size))
            imageViews.add(bufferViews.length() - 1)
        }
        val imageJson = JSONArray()
        val textures = JSONArray()
        val textureIndex = HashMap<String, Int>()
        var usesBasis = false
        for (index in packedImages.indices) {
            val image = packedImages[index].second
            val png = image.size > 8 && image[0] == 137.toByte() && image[1] == 80.toByte()
            imageJson.put(JSONObject()
                .put("mimeType", if (png) "image/png" else "image/ktx2")
                .put("bufferView", imageViews[index])
                .put("name", packedImages[index].first))
            val texture = JSONObject().put("sampler", 0).put("source", index)
            if (!png) {
                texture.put("extensions", JSONObject().put("KHR_texture_basisu", JSONObject().put("source", index)))
                usesBasis = true
            }
            textures.put(texture)
            textureIndex[packedImages[index].first] = index
        }
        bind(materials, materialIndex, "asphalt", textureIndex, "asphalt")
        bind(materials, materialIndex, "grass", textureIndex, "grass")
        bind(materials, materialIndex, "shoulder", textureIndex, "gravel")
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
        root.put("samplers", JSONArray().put(JSONObject().put("wrapS", 10497).put("wrapT", 10497).put("magFilter", 9729).put("minFilter", 9987)))
        if (imageJson.length() > 0) {
            root.put("images", imageJson)
            root.put("textures", textures)
            if (usesBasis) root.put("extensionsUsed", JSONArray().put("KHR_texture_basisu"))
        }
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

    fun roadImages(read: (String) -> ByteArray): Map<String, ByteArray> {
        return imageOrder.associateWith { read(it) }
    }

    /**
     * The packed race asphalt is a charcoal albedo (about sRGB 62). A factor cannot
     * lighten it, so the driving view replaces that image with a light grey grain.
     */
    fun drivingImages(read: (String) -> ByteArray): Map<String, ByteArray> {
        val images = roadImages(read).toMutableMap()
        images["asphalt-color"] = asphaltPng(256)
        return images
    }

    private val imageOrder = listOf(
        "asphalt-color", "asphalt-normal", "asphalt-orm",
        "grass-color", "grass-normal", "grass-orm",
        "gravel-color", "gravel-normal", "gravel-orm"
    )

    private fun bind(materials: JSONArray, index: Map<String, Int>, materialName: String, textures: Map<String, Int>, prefix: String) {
        val slot = index[materialName] ?: return
        val color = textures["$prefix-color"] ?: return
        val normal = textures["$prefix-normal"] ?: return
        val orm = textures["$prefix-orm"] ?: return
        val material = materials.getJSONObject(slot)
        val pbr = material.getJSONObject("pbrMetallicRoughness")
        pbr.put("baseColorTexture", JSONObject().put("index", color))
        pbr.put("metallicRoughnessTexture", JSONObject().put("index", orm))
        val normalScale = if (materialName == "asphalt") 1.6 else 1.0
        material.put("normalTexture", JSONObject().put("index", normal).put("scale", normalScale))
        material.put("occlusionTexture", JSONObject().put("index", orm))
    }

    private fun materialJson(material: RaceMaterial): JSONObject {
        val json = JSONObject()
            .put("name", material.name)
            .put("doubleSided", true)
            .put("pbrMetallicRoughness", JSONObject()
                .put("baseColorFactor", JSONArray().put(material.red.toDouble()).put(material.green.toDouble()).put(material.blue.toDouble()).put(material.alpha.toDouble()))
                .put("metallicFactor", material.metallic.toDouble())
                .put("roughnessFactor", material.roughness.toDouble()))
        if (material.alpha < 0.999f) json.put("alphaMode", "BLEND")
        return json
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

    fun asphaltPng(size: Int = 256): ByteArray {
        val cells = 16
        val cell = size / cells
        fun hash(ix: Int, iy: Int): Int {
            val x = Math.floorMod(ix, cells)
            val y = Math.floorMod(iy, cells)
            var n = x * 374761393 + y * 668265263
            n = (n xor (n ushr 13)) * 1274126177
            return (n xor (n ushr 16)) and 255
        }
        val raw = ByteArray(size * (1 + size * 3))
        var cursor = 0
        for (y in 0 until size) {
            raw[cursor++] = 0
            val y0 = y / cell
            val fy = (y % cell) / cell.toFloat()
            for (x in 0 until size) {
                val x0 = x / cell
                val fx = (x % cell) / cell.toFloat()
                val n00 = hash(x0, y0)
                val n10 = hash(x0 + 1, y0)
                val n01 = hash(x0, y0 + 1)
                val n11 = hash(x0 + 1, y0 + 1)
                val nx0 = n00 + ((n10 - n00) * fx).toInt()
                val nx1 = n01 + ((n11 - n01) * fx).toInt()
                val blotch = nx0 + ((nx1 - nx0) * fy).toInt() - 128
                val fine = hash(x, y) % 11 - 5
                val value = (166 + blotch / 7 + fine).coerceIn(132, 202)
                raw[cursor++] = value.toByte()
                raw[cursor++] = (value - 1).coerceAtLeast(128).toByte()
                raw[cursor++] = (value - 3).coerceAtLeast(124).toByte()
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
