package hu.toonnav.map

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Trees and buildings taken from OpenStreetMap for the corridor around a live route.
 * A missing reply, or a feature that would stand on the carriageway, adds nothing.
 */
object Scenery {
    const val chunkMeters = 500.0
    private const val maxBytes = 2_500_000

    fun obstructs(east: Double, north: Double, samples: List<RoadSample>, margin: Double = 1.6): Boolean {
        var best = Double.POSITIVE_INFINITY
        var limit = 4.0
        for (sample in samples) {
            val distance = hypot(sample.east - east, sample.north - north)
            if (distance >= best) continue
            best = distance
            val profile = RoadProfile.of(sample.kind)
            val extra = when (sample.kind) {
                RoadKind.MOTORWAY, RoadKind.EXPRESSWAY, RoadKind.RAMP -> 2.4
                else -> 0.0
            }
            limit = profile.halfWidth + profile.shoulder + margin + extra
        }
        return best < limit
    }

    fun inside(east: Double, north: Double, ring: List<Pair<Double, Double>>): Boolean {
        if (ring.size < 3) return false
        var crossings = 0
        for (index in 0 until ring.size - 1) {
            val a = ring[index]
            val b = ring[index + 1]
            val crosses = (a.second > north) != (b.second > north)
            if (!crosses) continue
            val x = a.first + (b.first - a.first) * (north - a.second) / (b.second - a.second)
            if (east < x) crossings++
        }
        return crossings % 2 == 1
    }

    /** Points inside a mapped wood. The spacing is hashed so it is not a row beside the road. */
    fun forestPoints(ring: List<Pair<Double, Double>>, salt: Int, samples: List<RoadSample>): List<Pair<Double, Double>> {
        if (ring.size < 4) return emptyList()
        var minEast = Double.POSITIVE_INFINITY
        var maxEast = Double.NEGATIVE_INFINITY
        var minNorth = Double.POSITIVE_INFINITY
        var maxNorth = Double.NEGATIVE_INFINITY
        for (point in ring) {
            if (point.first < minEast) minEast = point.first
            if (point.first > maxEast) maxEast = point.first
            if (point.second < minNorth) minNorth = point.second
            if (point.second > maxNorth) maxNorth = point.second
        }
        if (maxEast - minEast > 700 || maxNorth - minNorth > 700) return emptyList()
        val angle = ((salt * 17) % 50) / 50.0 * PI
        val ca = cos(angle)
        val sa = sin(angle)
        val points = ArrayList<Pair<Double, Double>>()
        var east = minEast
        while (east <= maxEast && points.size < 70) {
            var north = minNorth
            while (north <= maxNorth && points.size < 70) {
                val rotatedEast = east * ca - north * sa
                val rotatedNorth = east * sa + north * ca
                val jitter = hash(rotatedEast, rotatedNorth, salt)
                val placedEast = east + ((jitter and 1023) / 1023.0 - 0.5) * 8.0
                val placedNorth = north + (((jitter ushr 10) and 1023) / 1023.0 - 0.5) * 8.0
                if (inside(placedEast, placedNorth, ring) && !obstructs(placedEast, placedNorth, samples, 2.2)) {
                    points.add(placedEast to placedNorth)
                }
                north += 14.0
            }
            east += 14.0
        }
        return points
    }

    fun parse(
        json: String,
        origin: OsrmPoint,
        samples: List<RoadSample>,
        heightAt: ((Double, Double) -> Float)?
    ): List<RaceMesh> {
        if (samples.size < 2) return emptyList()
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val buildings = ArrayList<Footprint>()
        val trees = ArrayList<Plant>()
        for (index in 0 until elements.length()) {
            if (buildings.size >= 40 && trees.size >= 120) break
            val element = elements.optJSONObject(index) ?: continue
            val tags = element.optJSONObject("tags") ?: continue
            when (element.optString("type")) {
                "node" -> {
                    if (tags.optString("natural") != "tree" || trees.size >= 120) continue
                    val local = localOf(origin, element.optDouble("lon"), element.optDouble("lat"))
                    if (obstructs(local.first, local.second, samples)) continue
                    trees.add(Plant(local.first, local.second, hash(local.first, local.second, 1)))
                }
                "way" -> {
                    val ring = ringOf(origin, element.optJSONArray("geometry")) ?: continue
                    if (tags.has("building") && tags.optString("building") != "no" && buildings.size < 40) {
                        if (ring.any { obstructs(it.first, it.second, samples, 0.8) }) continue
                        buildings.add(Footprint(ring, heightOf(tags), hash(ring.first().first, ring.first().second, 3)))
                    } else if (trees.size < 120 && isWood(tags)) {
                        for (point in forestPoints(ring, index + 1, samples)) {
                            if (trees.size >= 120) break
                            trees.add(Plant(point.first, point.second, hash(point.first, point.second, index + 1)))
                        }
                    }
                }
            }
        }
        val keptTrees = trees.filter { plant -> buildings.none { inside(plant.east, plant.north, it.ring) } }
        return build(keptTrees, buildings, samples, heightAt)
    }

    fun bounds(origin: OsrmPoint, samples: List<RoadSample>, fromAlong: Double, untilAlong: Double): DoubleArray? {
        val slice = samples.filter { it.along in fromAlong..untilAlong }
        if (slice.size < 2) return null
        var minEast = Double.POSITIVE_INFINITY
        var maxEast = Double.NEGATIVE_INFINITY
        var minNorth = Double.POSITIVE_INFINITY
        var maxNorth = Double.NEGATIVE_INFINITY
        for (sample in slice) {
            if (sample.east < minEast) minEast = sample.east
            if (sample.east > maxEast) maxEast = sample.east
            if (sample.north < minNorth) minNorth = sample.north
            if (sample.north > maxNorth) maxNorth = sample.north
        }
        val pad = 170.0
        val corners = listOf(
            minEast - pad to minNorth - pad,
            maxEast + pad to minNorth - pad,
            maxEast + pad to maxNorth + pad,
            minEast - pad to maxNorth + pad
        ).map { RaceTrack.geographic(origin, it.first, it.second) }
        val south = corners.minOf { it.lat }
        val north = corners.maxOf { it.lat }
        val west = corners.minOf { it.lon }
        val east = corners.maxOf { it.lon }
        return doubleArrayOf(south, west, north, east)
    }

    fun fetch(south: Double, west: Double, north: Double, east: Double): String? {
        readMap(south, west, north, east)?.let { return it }
        val query = "[out:json][timeout:12];(" +
            "node[\"natural\"=\"tree\"]($south,$west,$north,$east);" +
            "way[\"landuse\"=\"forest\"]($south,$west,$north,$east);" +
            "way[\"natural\"=\"wood\"]($south,$west,$north,$east);" +
            "way[\"landuse\"=\"orchard\"]($south,$west,$north,$east);" +
            "way[\"building\"]($south,$west,$north,$east);" +
            ");out tags geom;"
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        for (endpoint in endpoints) {
            val body = read(endpoint, query) ?: continue
            if (body.contains("\"elements\"")) return body
        }
        return null
    }

    private fun readMap(south: Double, west: Double, north: Double, east: Double): String? {
        val connection = (URL("https://api.openstreetmap.org/api/0.6/map?bbox=$west,$south,$east,$north").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 12000
            setRequestProperty("User-Agent", "ToonNav/1.0 (Android navigation)")
        }
        return try {
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > maxBytes) return null
            mapToElements(bytes)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun mapToElements(bytes: ByteArray): String? {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val nodes = HashMap<String, JSONObject>()
        val elements = JSONArray()
        val root = document.documentElement ?: return null
        val children = root.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeName == "node") {
                val tags = tagsOf(child)
                val id = child.attributes?.getNamedItem("id")?.nodeValue ?: continue
                val lat = child.attributes?.getNamedItem("lat")?.nodeValue?.toDoubleOrNull() ?: continue
                val lon = child.attributes?.getNamedItem("lon")?.nodeValue?.toDoubleOrNull() ?: continue
                val node = JSONObject().put("lat", lat).put("lon", lon)
                nodes[id] = node
                if (tags.optString("natural") == "tree") {
                    elements.put(JSONObject().put("type", "node").put("lat", lat).put("lon", lon).put("tags", tags))
                }
            }
        }
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeName != "way") continue
            val tags = tagsOf(child)
            if (!tags.has("building") && !isWood(tags)) continue
            val geometry = JSONArray()
            val nested = child.childNodes
            for (cursor in 0 until nested.length) {
                val refNode = nested.item(cursor)
                if (refNode.nodeName != "nd") continue
                val ref = refNode.attributes?.getNamedItem("ref")?.nodeValue ?: continue
                val point = nodes[ref] ?: continue
                geometry.put(JSONObject().put("lon", point.getDouble("lon")).put("lat", point.getDouble("lat")))
            }
            if (geometry.length() >= 4) {
                elements.put(JSONObject().put("type", "way").put("tags", tags).put("geometry", geometry))
            }
        }
        return JSONObject().put("elements", elements).toString()
    }

    private fun tagsOf(node: org.w3c.dom.Node): JSONObject {
        val tags = JSONObject()
        val children = node.childNodes ?: return tags
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeName != "tag") continue
            val key = child.attributes?.getNamedItem("k")?.nodeValue ?: continue
            val value = child.attributes?.getNamedItem("v")?.nodeValue ?: continue
            tags.put(key, value)
        }
        return tags
    }

    private fun read(endpoint: String, query: String): String? {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("User-Agent", "ToonNav/1.0 (Android navigation)")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            val payload = "data=" + URLEncoder.encode(query, "UTF-8")
            connection.outputStream.use { it.write(payload.toByteArray()) }
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > maxBytes) return null
            bytes.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun build(
        trees: List<Plant>,
        buildings: List<Footprint>,
        samples: List<RoadSample>,
        heightAt: ((Double, Double) -> Float)?
    ): List<RaceMesh> {
        val trunk = MeshBuilder()
        val canopy = MeshBuilder()
        val canopyDark = MeshBuilder()
        for (plant in trees) {
            val y = ground(plant.east, plant.north, samples, heightAt)
            val scale = 0.75f + (plant.salt and 1023) / 1023f * 0.65f
            val yaw = (plant.salt and 0xffff) / 65535f * (PI.toFloat() * 2f)
            addTree(trunk, canopy, canopyDark, plant.east, plant.north, y, scale, yaw, plant.salt)
        }
        val blocks = Array(3) { MeshBuilder() }
        val names = arrayOf("building", "buildingWarm", "buildingCool")
        for (foot in buildings) {
            val target = blocks[kotlin.math.abs(foot.salt) % 3]
            addBuilding(target, foot, samples, heightAt)
        }
        return listOf(
            trunk.build("trunk"),
            canopy.build("canopy"),
            canopyDark.build("canopyDark")
        ).plus(blocks.mapIndexed { index, builder -> builder.build(names[index]) }).filter { it.vertexCount >= 3 }
    }

    private fun addTree(
        trunk: MeshBuilder,
        canopy: MeshBuilder,
        canopyDark: MeshBuilder,
        east: Double,
        north: Double,
        y: Float,
        scale: Float,
        yaw: Float,
        salt: Int
    ) {
        val sides = 6
        val height = 2.3f * scale
        val radius = 0.11f * scale
        val top = FloatArray(sides)
        val bottom = FloatArray(sides)
        for (side in 0 until sides) {
            val angle = yaw + side / sides.toFloat() * PI.toFloat() * 2f
            val x = east.toFloat() + cos(angle) * radius
            val z = north.toFloat() + sin(angle) * radius
            bottom[side] = trunk.add(floatArrayOf(x, y, z), 0f, 0f).toFloat()
            top[side] = trunk.add(floatArrayOf(x, y + height, z), 0f, 1f).toFloat()
        }
        for (side in 0 until sides) {
            val next = (side + 1) % sides
            trunk.quad(bottom[side].toInt(), bottom[next].toInt(), top[next].toInt(), top[side].toInt())
        }
        cone(canopy, east, north, y + height * 0.72f, 0.95f * scale, 1.7f * scale, yaw)
        val lean = if (salt and 1 == 0) 0.35f else -0.28f
        cone(canopyDark, east + lean * scale, north + lean * 0.4 * scale, y + height * 0.95f, 0.72f * scale, 1.25f * scale, yaw + 0.4f)
    }

    private fun cone(builder: MeshBuilder, east: Double, north: Double, y: Float, radius: Float, height: Float, yaw: Float) {
        val sides = 7
        val base = IntArray(sides)
        for (side in 0 until sides) {
            val angle = yaw + side / sides.toFloat() * PI.toFloat() * 2f
            base[side] = builder.add(
                floatArrayOf(east.toFloat() + cos(angle) * radius, y, north.toFloat() + sin(angle) * radius),
                0f, 0f
            )
        }
        val tip = builder.add(floatArrayOf(east.toFloat(), y + height, north.toFloat()), 0.5f, 1f)
        for (side in 0 until sides) {
            val next = (side + 1) % sides
            builder.triangle(base[side], base[next], tip)
        }
    }

    private fun addBuilding(
        builder: MeshBuilder,
        foot: Footprint,
        samples: List<RoadSample>,
        heightAt: ((Double, Double) -> Float)?
    ) {
        val ring = foot.ring
        var base = 0f
        for (point in ring) base += ground(point.first, point.second, samples, heightAt)
        base /= ring.size.coerceAtLeast(1)
        val top = base + foot.height
        for (index in 0 until ring.size - 1) {
            val a = ring[index]
            val b = ring[index + 1]
            val a0 = builder.add(floatArrayOf(a.first.toFloat(), base, a.second.toFloat()), 0f, 0f)
            val b0 = builder.add(floatArrayOf(b.first.toFloat(), base, b.second.toFloat()), 1f, 0f)
            val b1 = builder.add(floatArrayOf(b.first.toFloat(), top, b.second.toFloat()), 1f, 1f)
            val a1 = builder.add(floatArrayOf(a.first.toFloat(), top, a.second.toFloat()), 0f, 1f)
            builder.quad(a0, b0, b1, a1)
        }
        val roof = ring.dropLast(1)
        if (roof.size >= 3) {
            val anchor = builder.add(floatArrayOf(roof[0].first.toFloat(), top, roof[0].second.toFloat()), 0f, 0f)
            for (index in 1 until roof.size - 1) {
                val left = builder.add(floatArrayOf(roof[index].first.toFloat(), top, roof[index].second.toFloat()), 0f, 1f)
                val right = builder.add(floatArrayOf(roof[index + 1].first.toFloat(), top, roof[index + 1].second.toFloat()), 1f, 1f)
                builder.triangle(anchor, left, right)
            }
        }
    }

    private fun ground(east: Double, north: Double, samples: List<RoadSample>, heightAt: ((Double, Double) -> Float)?): Float {
        heightAt?.let { return it(east, north) }
        var best = 0f
        var bestDistance = Double.POSITIVE_INFINITY
        for (sample in samples) {
            val distance = hypot(sample.east - east, sample.north - north)
            if (distance < bestDistance) {
                bestDistance = distance
                best = sample.elevation
            }
        }
        return best
    }

    private fun heightOf(tags: JSONObject): Float {
        val measured = tags.optString("height").lowercase().removeSuffix("m").trim().toFloatOrNull()
        if (measured != null && measured in 2.5f..160f) return measured
        val levels = tags.optString("building:levels").toFloatOrNull()
        if (levels != null && levels in 1f..50f) return levels * 3.1f
        return when (tags.optString("building")) {
            "garage", "shed", "carport", "roof" -> 3.2f
            "apartments" -> 12f
            "commercial", "retail", "office" -> 8f
            else -> 6.5f
        }
    }

    private fun isWood(tags: JSONObject): Boolean {
        return tags.optString("landuse") == "forest" || tags.optString("landuse") == "orchard" || tags.optString("natural") == "wood"
    }

    private fun ringOf(origin: OsrmPoint, geometry: org.json.JSONArray?): List<Pair<Double, Double>>? {
        if (geometry == null || geometry.length() < 4) return null
        val ring = ArrayList<Pair<Double, Double>>(geometry.length())
        for (index in 0 until geometry.length()) {
            val point = geometry.optJSONObject(index) ?: return null
            ring.add(localOf(origin, point.optDouble("lon"), point.optDouble("lat")))
        }
        if (ring.first() != ring.last()) ring.add(ring.first())
        return ring
    }

    private fun localOf(origin: OsrmPoint, longitude: Double, latitude: Double): Pair<Double, Double> {
        val local = GeoMeters.local(origin, OsrmPoint(longitude, latitude))
        return local.first to local.second
    }

    private fun hash(east: Double, north: Double, salt: Int): Int {
        val mixed = (east * 1000.0).toInt() * 73856093 xor (north * 1000.0).toInt() * 19349663 xor salt * 83492791
        return mixed
    }

    private data class Plant(val east: Double, val north: Double, val salt: Int)
    private data class Footprint(val ring: List<Pair<Double, Double>>, val height: Float, val salt: Int)

    private class MeshBuilder {
        private val positions = ArrayList<Float>()
        private val uvs = ArrayList<Float>()
        private val indices = ArrayList<Int>()

        fun add(values: FloatArray, u: Float, v: Float): Int {
            val index = positions.size / 3
            positions.add(values[0])
            positions.add(values[1])
            positions.add(values[2])
            uvs.add(u)
            uvs.add(v)
            return index
        }

        fun triangle(a: Int, b: Int, c: Int) {
            indices.add(a)
            indices.add(b)
            indices.add(c)
        }

        fun quad(a: Int, b: Int, c: Int, d: Int) {
            triangle(a, b, c)
            triangle(a, c, d)
        }

        fun build(material: String): RaceMesh {
            val positionArray = FloatArray(positions.size) { positions[it] }
            val normalArray = FloatArray(positions.size)
            for (triangle in indices.indices step 3) {
                val a = indices[triangle] * 3
                val b = indices[triangle + 1] * 3
                val c = indices[triangle + 2] * 3
                if (a >= positionArray.size || b >= positionArray.size || c >= positionArray.size) continue
                val abx = positionArray[b] - positionArray[a]
                val aby = positionArray[b + 1] - positionArray[a + 1]
                val abz = positionArray[b + 2] - positionArray[a + 2]
                val acx = positionArray[c] - positionArray[a]
                val acy = positionArray[c + 1] - positionArray[a + 1]
                val acz = positionArray[c + 2] - positionArray[a + 2]
                val nx = aby * acz - abz * acy
                val ny = abz * acx - abx * acz
                val nz = abx * acy - aby * acx
                for (vertex in intArrayOf(a, b, c)) {
                    normalArray[vertex] += nx
                    normalArray[vertex + 1] += ny
                    normalArray[vertex + 2] += nz
                }
            }
            for (index in normalArray.indices step 3) {
                val length = sqrt(
                    normalArray[index] * normalArray[index] +
                        normalArray[index + 1] * normalArray[index + 1] +
                        normalArray[index + 2] * normalArray[index + 2]
                ).coerceAtLeast(0.0001f)
                normalArray[index] /= length
                normalArray[index + 1] /= length
                normalArray[index + 2] /= length
            }
            return RaceMesh(material, positionArray, normalArray, FloatArray(uvs.size) { uvs[it] }, indices.toIntArray())
        }
    }
}
