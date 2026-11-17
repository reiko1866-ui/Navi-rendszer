package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Geometry and a software picture of the new backdrop. The picture is not a
 * Filament frame and it is not a phone frame.
 */
class PlainWorldTest {
    @Test
    fun colorsArePlainAndNotMagenta() {
        assertFalse(LegacyBackdrop.enabled)
        for (swatch in PlainWorld.all) {
            assertFalse(swatch.name.contains("ktx"))
            val magenta = swatch.red > 0.9f && swatch.green < 0.05f && swatch.blue > 0.9f
            assertFalse(magenta)
        }
        assertTrue(PlainWorld.ground.green > PlainWorld.ground.red * 2f)
        assertTrue(PlainWorld.ground.green > PlainWorld.ground.blue * 2f)
        assertTrue(abs(PlainWorld.road.red - PlainWorld.road.green) < 0.04f)
        assertTrue(PlainWorld.road.red in 0.3f..0.6f)
        assertTrue(PlainWorld.shoulder.red > PlainWorld.shoulder.green)
        assertTrue(PlainWorld.shoulder.green > PlainWorld.shoulder.blue)
        assertTrue(PlainWorld.sky.blue > PlainWorld.sky.green && PlainWorld.sky.green > PlainWorld.sky.red)
        assertTrue(PlainWorld.center.red > 0.9f && PlainWorld.edge.red > 0.85f)
        assertTrue(PlainWorld.farBand.blue > PlainWorld.farBand.red)
        val shown = PlainWorld.sky.blue * RaceLighting.exposure(PlainWorld.aperture, PlainWorld.shutter, PlainWorld.sensitivity)
        assertTrue(shown > 0.35f)
        assertTrue(RaceLighting.exposure(16f, 1f / 125f, 100f) < 0.0001f)
    }

    @Test
    fun straightCorridorHasEverySurface() {
        val meshes = PlainWorld.meshes(PlainWorld.previewSamples(160.0, 8.0))
        val names = meshes.map { it.material }.toSet()
        assertEquals(
            setOf(
                PlainWorld.ground.name,
                PlainWorld.farBand.name,
                PlainWorld.horizonBand.name,
                PlainWorld.shoulder.name,
                PlainWorld.road.name,
                PlainWorld.edge.name,
                PlainWorld.center.name
            ),
            names
        )
        val center = meshes.first { it.material == PlainWorld.center.name }
        var gaps = 0
        var dashes = 0
        for (index in 0 until 18) {
            val along = index * 8.0
            if (covers(center, 0.0, along + 4.0)) dashes++ else gaps++
        }
        assertTrue(dashes >= 8)
        assertTrue(gaps >= 6)
    }

    @Test
    fun roadBendsWithTheSamples() {
        val points = ArrayList<Pair<Double, Double>>()
        val radius = 90.0
        var cursor = 0.0
        while (cursor <= 220.0) {
            val angle = cursor / radius
            points.add((radius * kotlin.math.sin(angle)) to (radius * (1.0 - kotlin.math.cos(angle))))
            cursor += 5.0
        }
        val samples = samples(points)
        val meshes = PlainWorld.meshes(samples)
        val center = meshes.first { it.material == PlainWorld.center.name }
        val road = meshes.first { it.material == PlainWorld.road.name }
        var maxCenter = 0.0
        var maxRoad = 0.0
        for (vertex in 0 until center.vertexCount) {
            val east = center.positions[vertex * 3].toDouble()
            val north = center.positions[vertex * 3 + 2].toDouble()
            maxCenter = maxOf(maxCenter, distanceToLine(east, north, points))
        }
        for (vertex in 0 until road.vertexCount) {
            val east = road.positions[vertex * 3].toDouble()
            val north = road.positions[vertex * 3 + 2].toDouble()
            maxRoad = maxOf(maxRoad, distanceToLine(east, north, points))
        }
        assertTrue(maxCenter < 0.25)
        assertTrue(maxRoad < PlainWorld.roadHalf + 0.2)
        val end = points.last()
        assertTrue(end.first > 40.0)
        assertTrue(covers(road, end.first, end.second))
    }

    @Test
    fun drivingCameraFramesTheHorizonOnFiveScreens() {
        val aspects = listOf(1280.0 / 720.0, 1920.0 / 1080.0, 2560.0 / 1440.0, 1080.0 / 2400.0, 2400.0 / 1080.0)
        val pose = RaceCamera.pose(0.0, 30.0, 0.0, 0.06)
        assertTrue(pose.targetY > 0.06f)
        assertTrue(pose.eyeY > pose.targetY)
        assertTrue(pose.targetZ > pose.eyeZ + 10f)
        val model = RaceCamera.cameraModel(0.0, 30.0, 0.0, 0.06)
        val forwardX = -model[8]
        val forwardY = -model[9]
        val forwardZ = -model[10]
        assertTrue(forwardZ > 0.9f)
        assertTrue(forwardY < -0.02f)
        val near = depth(pose, forwardX, forwardY, forwardZ, 0f, 0.06f, 34f)
        val far = depth(pose, forwardX, forwardY, forwardZ, 400f, 0f, 80f)
        assertTrue(near > RaceCamera.nearMeters)
        assertTrue(far < RaceCamera.farMeters)
        assertTrue(far > near)
        for (aspect in aspects) {
            val fov = DriveDisplay.verticalFov(aspect)
            val roadNear = RaceCamera.ndc(pose, 0f, 0.06f, 48f, fov, aspect)
            val roadFar = RaceCamera.ndc(pose, 0f, 0.06f, 140f, fov, aspect)
            val grass = RaceCamera.ndc(pose, 12f, 0f, 55f, fov, aspect)
            val horizon = RaceCamera.ndc(pose, 0f, 0f, 700f, fov, aspect)
            val sky = RaceCamera.ndc(pose, 0f, 30f, 180f, fov, aspect)
            assertTrue(abs(roadNear[0]) < 0.05f)
            assertTrue(roadNear[1] < roadFar[1])
            assertTrue(roadFar[1] < horizon[1])
            assertTrue(horizon[1] < sky[1])
            assertTrue(abs(roadNear[1]) < 1f && abs(horizon[1]) < 1f && sky[1] < 1.2f)
            assertTrue(abs(grass[0]) < 1.15f)
            assertTrue(grass[1] < horizon[1])
        }
    }

    @Test
    fun materialFileHasNoTexturePayload() {
        val file = asset("materials/plain_solid.filamat")
        val bytes = file.readBytes()
        assertTrue(bytes.size > 1000)
        val magic = String(bytes.copyOfRange(0, 8), Charsets.US_ASCII)
        assertEquals("SREV_TAM", magic)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("baseColor"))
        assertFalse(text.contains("ktx2"))
        assertFalse(text.contains("image/png"))
        assertFalse(bytes.containsPng())
        val mat = File("/workspace/android/materials/plain_solid.mat").readText()
        assertTrue(mat.contains("unlit"))
        assertFalse(mat.contains("sampler"))
    }

    @Test
    fun drivingScreenUsesThePlainBackdropAndKeepsTheOldOne() {
        val activity = source("RacePrototypeActivity.kt").readText()
        assertTrue(activity.contains("LegacyBackdrop.enabled"))
        assertTrue(activity.contains("startPlain"))
        assertTrue(activity.contains("noon-sky.hdr"))
        assertTrue(activity.contains("RaceGeometry.libraryModel()"))
        val plain = source("PlainDriveActivity.kt").readText()
        assertFalse(plain.contains("noon-sky"))
        assertFalse(plain.contains("ktx2"))
        assertFalse(plain.contains(".glb"))
        assertFalse(plain.contains("gltfio"))
        val gpu = source("PlainGpu.kt").readText()
        assertFalse(gpu.contains("baseColorMap"))
        assertFalse(gpu.contains("KTX"))
        val index = File("/workspace/index.html")
        val previousMap = File("/workspace/elozo-terkep.html")
        assertTrue(index.isFile)
        assertTrue(previousMap.isFile)
        assertFalse(index.readText().contains("plain_solid"))
        assertFalse(previousMap.readText().contains("PlainWorld"))
    }

    @Test
    fun softwarePictureShowsGroundRoadShoulderAndSky() {
        val samples = PlainWorld.previewSamples(640.0, 8.0)
        val wide = paint(samples, 480, 270, 16.0 / 9.0)
        val tall = paint(samples, 270, 480, 9.0 / 16.0)
        assertTrue("wide sky ${wide.sky}", wide.sky > 4000)
        assertTrue("wide ground ${wide.ground}", wide.ground > 4000)
        assertTrue("wide road ${wide.road}", wide.road > 1500)
        assertTrue("wide shoulder ${wide.shoulder}", wide.shoulder > 400)
        assertTrue("wide white ${wide.white}", wide.white > 40)
        assertTrue("wide far ${wide.far}", wide.far > 200)
        assertTrue("tall sky ${tall.sky}", tall.sky > 4000)
        assertTrue("tall ground ${tall.ground}", tall.ground > 2000)
        assertTrue("tall road ${tall.road}", tall.road > 800)
        val wideRoad = rowSpan(wide, PlainWorld.road.packed() and 0xFFFFFF)
        assertTrue(wideRoad.nearWidth > wideRoad.farWidth)
        writePng(File("/tmp/plain-world-wide.png"), wide)
        writePng(File("/tmp/plain-world-tall.png"), tall)
        val artifacts = File("/opt/cursor/artifacts")
        if (artifacts.isDirectory) {
            writePng(File(artifacts, "plain-world-wide.png"), wide)
            writePng(File(artifacts, "plain-world-tall.png"), tall)
        }
    }

    private fun covers(mesh: RaceMesh, east: Double, north: Double): Boolean {
        val x = east.toFloat()
        val z = north.toFloat()
        val positions = mesh.positions
        val indices = mesh.indices
        var cursor = 0
        while (cursor < indices.size) {
            val a = indices[cursor]
            val b = indices[cursor + 1]
            val c = indices[cursor + 2]
            val ax = positions[a * 3]
            val az = positions[a * 3 + 2]
            val bx = positions[b * 3]
            val bz = positions[b * 3 + 2]
            val cx = positions[c * 3]
            val cz = positions[c * 3 + 2]
            val area = (bx - ax) * (cz - az) - (cx - ax) * (bz - az)
            if (abs(area) > 1e-4f) {
                val w0 = ((bx - x) * (cz - z) - (cx - x) * (bz - z)) / area
                val w1 = ((cx - x) * (az - z) - (ax - x) * (cz - z)) / area
                val w2 = ((ax - x) * (bz - z) - (bx - x) * (az - z)) / area
                if (w0 >= -0.02f && w1 >= -0.02f && w2 >= -0.02f) return true
            }
            cursor += 3
        }
        return false
    }

    private fun samples(points: List<Pair<Double, Double>>): List<RoadSample> {
        val built = ArrayList<RoadSample>()
        var along = 0.0
        for (index in points.indices) {
            if (index > 0) {
                val previous = points[index - 1]
                along += hypot(points[index].first - previous.first, points[index].second - previous.second)
            }
            built.add(RoadSample(points[index].first, points[index].second, along, 0.0, RoadKind.MAIN, 0f))
        }
        return built
    }

    private fun distanceToLine(east: Double, north: Double, line: List<Pair<Double, Double>>): Double {
        var best = Double.POSITIVE_INFINITY
        for (index in 0 until line.size - 1) {
            val start = line[index]
            val end = line[index + 1]
            val dx = end.first - start.first
            val dy = end.second - start.second
            val span = hypot(dx, dy).coerceAtLeast(0.001)
            val blend = (((east - start.first) * dx + (north - start.second) * dy) / (span * span)).coerceIn(0.0, 1.0)
            val px = start.first + dx * blend
            val py = start.second + dy * blend
            best = minOf(best, hypot(east - px, north - py))
        }
        return best
    }

    private fun depth(
        pose: RaceCameraPose,
        forwardX: Float,
        forwardY: Float,
        forwardZ: Float,
        x: Float,
        y: Float,
        z: Float
    ): Float {
        return (x - pose.eyeX) * forwardX + (y - pose.eyeY) * forwardY + (z - pose.eyeZ) * forwardZ
    }

    private class Shot(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val sky: Int,
        val ground: Int,
        val road: Int,
        val shoulder: Int,
        val white: Int,
        val far: Int
    )

    private fun paint(samples: List<RoadSample>, width: Int, height: Int, aspect: Double): Shot {
        val sky = PlainWorld.sky.packed() and 0xFFFFFF
        val pixels = IntArray(width * height) { sky }
        val depth = FloatArray(width * height) { Float.POSITIVE_INFINITY }
        val pose = RaceCamera.pose(0.0, 30.0, 0.0, 0.06)
        val model = RaceCamera.cameraModel(0.0, 30.0, 0.0, 0.06)
        val forwardX = -model[8]
        val forwardY = -model[9]
        val forwardZ = -model[10]
        val fov = DriveDisplay.verticalFov(aspect)
        val order = listOf(
            PlainWorld.horizonBand.name,
            PlainWorld.farBand.name,
            PlainWorld.ground.name,
            PlainWorld.shoulder.name,
            PlainWorld.road.name,
            PlainWorld.edge.name,
            PlainWorld.center.name
        )
        val meshes = PlainWorld.meshes(samples).sortedBy { order.indexOf(it.material) }
        for (mesh in meshes) {
            val color = PlainWorld.swatch(mesh.material)!!.packed() and 0xFFFFFF
            val index = mesh.indices
            val position = mesh.positions
            var cursor = 0
            while (cursor < index.size) {
                val polygon = ArrayList<ClipVertex>(3)
                for (corner in 0 until 3) {
                    val vertex = index[cursor + corner]
                    val x = position[vertex * 3]
                    val y = position[vertex * 3 + 1]
                    val z = position[vertex * 3 + 2]
                    val ahead = (x - pose.eyeX) * forwardX + (y - pose.eyeY) * forwardY + (z - pose.eyeZ) * forwardZ
                    polygon.add(ClipVertex(x, y, z, ahead))
                }
                val clipped = clipNear(polygon, RaceCamera.nearMeters.toFloat())
                if (clipped.size >= 3) {
                    val screen = Array(clipped.size) { corner ->
                        val point = clipped[corner]
                        val ndc = RaceCamera.ndc(pose, point.x, point.y, point.z, fov, aspect)
                        floatArrayOf(
                            (ndc[0] * 0.5f + 0.5f) * width,
                            (0.5f - ndc[1] * 0.5f) * height,
                            point.ahead
                        )
                    }
                    for (corner in 1 until clipped.size - 1) {
                        fill(pixels, depth, width, height, arrayOf(screen[0], screen[corner], screen[corner + 1]), color)
                    }
                }
                cursor += 3
            }
        }
        var skyCount = 0
        var ground = 0
        var road = 0
        var shoulder = 0
        var white = 0
        var far = 0
        val groundColor = PlainWorld.ground.packed() and 0xFFFFFF
        val roadColor = PlainWorld.road.packed() and 0xFFFFFF
        val shoulderColor = PlainWorld.shoulder.packed() and 0xFFFFFF
        val centerColor = PlainWorld.center.packed() and 0xFFFFFF
        val edgeColor = PlainWorld.edge.packed() and 0xFFFFFF
        val farColor = PlainWorld.farBand.packed() and 0xFFFFFF
        val horizonColor = PlainWorld.horizonBand.packed() and 0xFFFFFF
        for (pixel in pixels) {
            when (pixel) {
                sky -> skyCount++
                groundColor -> ground++
                roadColor -> road++
                shoulderColor -> shoulder++
                centerColor, edgeColor -> white++
                farColor, horizonColor -> far++
            }
        }
        return Shot(pixels, width, height, skyCount, ground, road, shoulder, white, far)
    }

    private data class Span(val nearWidth: Int, val farWidth: Int)

    private fun rowSpan(shot: Shot, color: Int): Span {
        fun widthAt(row: Int): Int {
            var count = 0
            val start = row * shot.width
            for (x in 0 until shot.width) if (shot.pixels[start + x] == color) count++
            return count
        }
        return Span(widthAt((shot.height * 0.82f).toInt()), widthAt((shot.height * 0.48f).toInt()))
    }

    private class ClipVertex(val x: Float, val y: Float, val z: Float, val ahead: Float)

    private fun clipNear(polygon: List<ClipVertex>, near: Float): List<ClipVertex> {
        if (polygon.isEmpty()) return emptyList()
        val output = ArrayList<ClipVertex>()
        var previous = polygon.last()
        for (current in polygon) {
            val prevIn = previous.ahead >= near
            val currIn = current.ahead >= near
            if (currIn) {
                if (!prevIn) output.add(clipEdge(previous, current, near))
                output.add(current)
            } else if (prevIn) {
                output.add(clipEdge(previous, current, near))
            }
            previous = current
        }
        return output
    }

    private fun clipEdge(start: ClipVertex, end: ClipVertex, near: Float): ClipVertex {
        val span = (end.ahead - start.ahead).takeIf { abs(it) > 1e-5f } ?: 1f
        val blend = ((near - start.ahead) / span).coerceIn(0f, 1f)
        return ClipVertex(
            start.x + (end.x - start.x) * blend,
            start.y + (end.y - start.y) * blend,
            start.z + (end.z - start.z) * blend,
            near
        )
    }

    private fun fill(
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        screen: Array<FloatArray>,
        color: Int
    ) {
        val minX = screen.minOf { it[0] }.toInt().coerceIn(0, width - 1)
        val maxX = screen.maxOf { it[0] }.toInt().coerceIn(0, width - 1)
        val minY = screen.minOf { it[1] }.toInt().coerceIn(0, height - 1)
        val maxY = screen.maxOf { it[1] }.toInt().coerceIn(0, height - 1)
        val ax = screen[0][0]
        val ay = screen[0][1]
        val bx = screen[1][0]
        val by = screen[1][1]
        val cx = screen[2][0]
        val cy = screen[2][1]
        val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if (abs(area) < 0.5f) return
        for (y in minY..maxY) {
            val row = y * width
            for (x in minX..maxX) {
                val px = x + 0.5f
                val py = y + 0.5f
                val w0 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / area
                val w1 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / area
                val w2 = ((ax - px) * (by - py) - (bx - px) * (ay - py)) / area
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                val z = w0 * screen[0][2] + w1 * screen[1][2] + w2 * screen[2][2]
                val slot = row + x
                if (z < depth[slot]) {
                    depth[slot] = z
                    pixels[slot] = color
                }
            }
        }
    }

    private fun ByteArray.containsPng(): Boolean {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        if (size < 4) return false
        for (index in 0..size - 4) {
            if (this[index] == signature[0] && this[index + 1] == signature[1] && this[index + 2] == signature[2] && this[index + 3] == signature[3]) {
                return true
            }
        }
        return false
    }

    private fun writePng(file: File, shot: Shot) {
        val raw = ByteArrayOutputStream()
        for (y in 0 until shot.height) {
            raw.write(0)
            val start = y * shot.width
            for (x in 0 until shot.width) {
                val pixel = shot.pixels[start + x]
                raw.write((pixel ushr 16) and 0xFF)
                raw.write((pixel ushr 8) and 0xFF)
                raw.write(pixel and 0xFF)
            }
        }
        val deflater = Deflater()
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val compressed = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        while (!deflater.finished()) {
            val count = deflater.deflate(chunk)
            compressed.write(chunk, 0, count)
        }
        deflater.end()
        val png = ByteArrayOutputStream()
        png.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val header = ByteBuffer.allocate(13)
        header.putInt(shot.width)
        header.putInt(shot.height)
        header.put(8)
        header.put(2)
        header.put(0)
        header.put(0)
        header.put(0)
        writeChunk(png, "IHDR", header.array())
        writeChunk(png, "IDAT", compressed.toByteArray())
        writeChunk(png, "IEND", ByteArray(0))
        file.parentFile?.mkdirs()
        file.writeBytes(png.toByteArray())
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val length = ByteBuffer.allocate(4).putInt(data.size).array()
        out.write(length)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }

    private fun asset(relative: String): File {
        val candidates = listOf(
            File("src/main/assets/$relative"),
            File("app/src/main/assets/$relative"),
            File("android/app/src/main/assets/$relative")
        )
        return candidates.first { it.isFile }
    }

    private fun source(name: String): File {
        val file = File("/workspace/android/app/src/main/java/hu/toonnav/map/$name")
        assertTrue(file.absolutePath, file.isFile)
        return file
    }

}
