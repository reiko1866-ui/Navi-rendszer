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
        for (index in 0 until PlainWorld.haze.size - 1) {
            val left = PlainWorld.haze[index]
            val right = PlainWorld.haze[index + 1]
            assertTrue(abs(left.red - right.red) < 0.12f)
            assertTrue(abs(left.green - right.green) < 0.12f)
            assertTrue(abs(left.blue - right.blue) < 0.12f)
        }
        assertTrue(abs(PlainWorld.haze.last().blue - PlainWorld.sky.blue) < 0.12f)
        assertEquals(PlainWorld.dayGradient.size, PlainWorld.nightGradient.size)
        assertTrue(PlainWorld.fogDistance > 40f)
        assertTrue(PlainWorld.fogDensity < 0.05f)
        val shown = PlainWorld.sky.blue * RaceLighting.exposure(PlainWorld.aperture, PlainWorld.shutter, PlainWorld.sensitivity)
        assertTrue(shown > 0.35f)
        assertTrue(RaceLighting.exposure(16f, 1f / 125f, 100f) < 0.0001f)
    }

    @Test
    fun straightCorridorHasEverySurface() {
        val meshes = PlainWorld.meshes(PlainWorld.previewSamples(160.0, 8.0))
        val names = meshes.map { it.material }.toSet()
        val required = (setOf(
            PlainWorld.ground.name,
            PlainWorld.verge.name,
            PlainWorld.shoulder.name,
            PlainWorld.road.name,
            PlainWorld.edge.name,
            PlainWorld.center.name
        ) + PlainWorld.haze.map { it.name }).toSet()
        assertTrue(names.containsAll(required))
        assertFalse(names.contains(PlainWorld.kerbRed.name))
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
        assertTrue(meshes.any { it.material == PlainWorld.kerbRed.name })
        assertTrue(meshes.any { it.material == PlainWorld.kerbWhite.name })
        val grass = meshes.first { it.material == PlainWorld.ground.name }
        var low = Float.POSITIVE_INFINITY
        var high = Float.NEGATIVE_INFINITY
        for (vertex in 0 until grass.vertexCount) {
            val y = grass.positions[vertex * 3 + 1]
            if (y < low) low = y
            if (y > high) high = y
        }
        assertTrue("relief $low..$high", high - low > 0.15f)
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
            val nose = RaceCamera.ndc(pose, 0f, 0.42f, 32.5f, fov, aspect)
            val further = RaceCamera.ndc(pose, 0f, 0.06f, 70f, fov, aspect)
            assertTrue("nose ${nose.toList()}", abs(nose[0]) < 0.4f && abs(nose[1]) < 1f)
            assertTrue("road beyond the nose", nose[1] < further[1])
        }
    }

    @Test
    fun distantGroundStaysClearOfTheRoad() {
        val meshes = PlainWorld.meshes(PlainWorld.previewSamples(120.0, 8.0))
        val road = meshes.first { it.material == PlainWorld.road.name }
        var roadHigh = Float.NEGATIVE_INFINITY
        for (vertex in 0 until road.vertexCount) {
            val y = road.positions[vertex * 3 + 1]
            if (y > roadHigh) roadHigh = y
        }
        assertTrue("asphalt stays flat $roadHigh", roadHigh < 0.2f)
        for (mesh in meshes) {
            if (mesh.material == PlainWorld.road.name || mesh.material == PlainWorld.center.name || mesh.material == PlainWorld.edge.name) continue
            for (vertex in 0 until mesh.vertexCount) {
                val east = mesh.positions[vertex * 3]
                val y = mesh.positions[vertex * 3 + 1]
                val north = mesh.positions[vertex * 3 + 2]
                if (abs(east.toDouble()) < 3.2 && north > 8f && north < 110f) {
                    assertTrue("terrain on the road $y", y < 0.05f)
                }
                assertTrue("no invented mountain $y", y < 4f)
            }
        }
    }

    @Test
    fun roadTerrainAndCarShareOneElevation() {
        val climb = graded(0.08)
        val drop = graded(-0.06)
        assertTrue(RaceTrack.gradeRadians(climb, 80.0) > 0.05)
        assertTrue(RaceTrack.gradeRadians(drop, 80.0) < -0.04)
        val climbed = PlainWorld.meshes(climb)
        val road = climbed.first { it.material == PlainWorld.road.name }
        val high = vertexNear(road, 0.0, 160.0)
        assertTrue("uphill road ${high[1]}", high[1] > 12f)
        val low = vertexNear(road, 0.0, 20.0)
        assertTrue(high[1] > low[1] + 8f)
        for (mesh in climbed) {
            if (mesh.material == PlainWorld.road.name || mesh.material == PlainWorld.edge.name || mesh.material == PlainWorld.center.name) continue
            for (vertex in 0 until mesh.vertexCount) {
                val east = mesh.positions[vertex * 3].toDouble()
                val y = mesh.positions[vertex * 3 + 1]
                val north = mesh.positions[vertex * 3 + 2].toDouble()
                if (abs(east) > 3.4 || north < 5.0 || north > 190.0) continue
                val roadY = (north * 0.08).toFloat()
                assertTrue("terrain through the climb $y at $north", y < roadY + 0.35f)
            }
        }
        val surface = climb.last().elevation.toDouble() + RaceGeometry.deckTop
        val grade = RaceTrack.gradeRadians(climb, climb.last().along)
        val planted = RaceCamera.carTransform(0.0, climb.last().north, 0.0, surface, grade)
        assertEquals(surface.toFloat(), planted[13], 0.001f)
        val noseY = planted[5] * 0.40f + planted[9] * 2.60f + planted[13]
        assertTrue("nose follows the grade $noseY", noseY > surface.toFloat() + 0.55f)
        val flatLook = RaceCamera.pose(0.0, 40.0, 0.0, 3.2, 3.2)
        val upLook = RaceCamera.pose(0.0, 40.0, 0.0, 3.2, 7.0)
        assertTrue(upLook.targetY > flatLook.targetY + 2f)

        val field: (Double, Double) -> Float = { east, _ -> if (abs(east) > 16.0) 14f else 0f }
        val sided = PlainWorld.meshes(PlainWorld.previewSamples(80.0, 8.0), field = field)
        var ridge = 0f
        var carriage = 1f
        for (mesh in sided) {
            val carriagePaint = mesh.material == PlainWorld.road.name || mesh.material == PlainWorld.roadShade.name ||
                mesh.material == PlainWorld.center.name || mesh.material == PlainWorld.edge.name ||
                mesh.material == PlainWorld.shoulder.name || mesh.material == PlainWorld.kerbRed.name ||
                mesh.material == PlainWorld.kerbWhite.name
            if (carriagePaint) {
                if (mesh.material == PlainWorld.road.name) {
                    for (vertex in 0 until mesh.vertexCount) carriage = minOf(carriage, mesh.positions[vertex * 3 + 1])
                }
                continue
            }
            for (vertex in 0 until mesh.vertexCount) {
                val east = abs(mesh.positions[vertex * 3])
                val y = mesh.positions[vertex * 3 + 1]
                if (east > 22f) ridge = maxOf(ridge, y)
                if (east < 3f) assertTrue("field crosses the asphalt $y", y < 0.02f)
            }
        }
        assertTrue("surveyed ridge $ridge", ridge > 8f)
        assertTrue(carriage < 0.2f)

        val hairpin = hairpin()
        val folded = PlainWorld.meshes(hairpin)
        for (mesh in folded) {
            if (mesh.material == PlainWorld.road.name || mesh.material == PlainWorld.edge.name || mesh.material == PlainWorld.center.name || mesh.material == PlainWorld.kerbRed.name || mesh.material == PlainWorld.kerbWhite.name) continue
            for (vertex in 0 until mesh.vertexCount) {
                val east = mesh.positions[vertex * 3].toDouble()
                val y = mesh.positions[vertex * 3 + 1]
                val north = mesh.positions[vertex * 3 + 2].toDouble()
                if (distanceToLine(east, north, hairpin.map { it.east to it.north }) < 3.2) {
                    assertTrue("hairpin terrain $y", y < 0.2f)
                }
            }
        }

        val pieces = PlainWorld.chunks(climb, 0..1)
        val seam = 250.0 * 0.08
        for (mesh in pieces.getValue(0) + pieces.getValue(1)) {
            if (mesh.material != PlainWorld.road.name) continue
            for (vertex in 0 until mesh.vertexCount) {
                val north = mesh.positions[vertex * 3 + 2]
                val y = mesh.positions[vertex * 3 + 1]
                if (abs(north - 250f) < 3f) assertTrue("chunk seam $y", abs(y - seam) < 0.4f)
            }
        }

        val noisy = floatArrayOf(0f, 1f, 80f, 2f, 3f)
        val places = noisy.indices.map { OsrmPoint(19.0, 47.0 + it * 0.0001) }
        val smoothed = RouteElevation.smooth(places, noisy, 30.0)
        assertTrue("spike survives ${smoothed[2]}", smoothed[2] < 30f)
        val mid = RaceTrack.pointAt(climb, 50.0)
        assertEquals(4f, mid.elevation, 0.15f)
        val aspects = listOf(16.0 / 9.0, 9.0 / 16.0, 21.0 / 9.0)
        for (aspect in aspects) {
            val pose = RaceCamera.pose(0.0, 40.0, 0.0, 3.26, 6.4)
            val roadAhead = RaceCamera.ndc(pose, 0f, 6.5f, 80f, DriveDisplay.verticalFov(aspect), aspect)
            assertTrue(abs(roadAhead[0]) < 0.2f)
            assertTrue(abs(roadAhead[1]) < 1.2f)
        }

        val flatShot = paint(PlainWorld.previewSamples(240.0, 8.0), 480, 270, 16.0 / 9.0)
        val upShot = paint(climb, 480, 270, 16.0 / 9.0, north = 40.0, surface = 3.26, lookSurface = 6.2)
        val downShot = paint(drop, 480, 270, 16.0 / 9.0, north = 40.0, surface = -2.34, lookSurface = -3.4)
        val curve = samples(arc())
        val curveShot = paint(curve, 480, 270, 16.0 / 9.0, east = 4.0, north = 0.2, bearing = 78.0, surface = 0.06, lookSurface = 0.06)
        val beside: (Double, Double) -> Float = { east, _ -> if (kotlin.math.abs(east) > 18.0) 9f else 0f }
        val besideShot = paint(PlainWorld.previewSamples(240.0, 8.0), 480, 270, 16.0 / 9.0, field = beside)
        val artifacts = File("/opt/cursor/artifacts")
        writePng(File("/tmp/road-flat.png"), flatShot)
        writePng(File("/tmp/road-uphill.png"), upShot)
        writePng(File("/tmp/road-downhill.png"), downShot)
        writePng(File("/tmp/road-curve.png"), curveShot)
        writePng(File("/tmp/road-beside.png"), besideShot)
        if (artifacts.isDirectory) {
            writePng(File(artifacts, "road-flat.png"), flatShot)
            writePng(File(artifacts, "road-uphill.png"), upShot)
            writePng(File(artifacts, "road-downhill.png"), downShot)
            writePng(File(artifacts, "road-curve.png"), curveShot)
            writePng(File(artifacts, "road-beside.png"), besideShot)
        }
        assertTrue("flat ${flatShot.road}", flatShot.road > 400)
        assertTrue("up ${upShot.road}", upShot.road > 200)
        assertTrue("down ${downShot.road}", downShot.road > 80)
        assertTrue("curve ${curveShot.road}", curveShot.road > 80)
        assertTrue("beside ${besideShot.road}", besideShot.road > 200)
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

    private fun graded(slope: Double): List<RoadSample> {
        val built = ArrayList<RoadSample>()
        var along = 0.0
        while (along <= 320.0) {
            built.add(RoadSample(0.0, along, along, 0.0, RoadKind.MAIN, (along * slope).toFloat()))
            along += 5.0
        }
        return built
    }

    private fun arc(): List<Pair<Double, Double>> {
        val points = ArrayList<Pair<Double, Double>>()
        val radius = 70.0
        var cursor = 0.0
        while (cursor <= 160.0) {
            val angle = cursor / radius
            points.add((radius * kotlin.math.sin(angle)) to (radius * (1.0 - kotlin.math.cos(angle))))
            cursor += 5.0
        }
        return points
    }

    private fun hairpin(): List<RoadSample> {
        val points = ArrayList<Pair<Double, Double>>()
        var east = 0.0
        var north = 0.0
        repeat(16) {
            points.add(east to north)
            north += 4.0
        }
        val radius = 14.0
        for (step in 1..16) {
            val angle = step / 16.0 * Math.PI
            points.add((radius * (1.0 - kotlin.math.cos(angle))) to (64.0 + radius * kotlin.math.sin(angle)))
        }
        east = radius * 2.0
        north = 64.0
        repeat(16) {
            north -= 4.0
            points.add(east to north)
        }
        return samples(points)
    }

    private fun vertexNear(mesh: RaceMesh, east: Double, north: Double): FloatArray {
        var best = Float.POSITIVE_INFINITY
        var found = floatArrayOf(0f, 0f, 0f)
        for (vertex in 0 until mesh.vertexCount) {
            val x = mesh.positions[vertex * 3]
            val y = mesh.positions[vertex * 3 + 1]
            val z = mesh.positions[vertex * 3 + 2]
            val distance = hypot(x - east, z - north).toFloat()
            if (distance < best) {
                best = distance
                found = floatArrayOf(x, y, z)
            }
        }
        return found
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

    private fun mixChannel(from: Int, to: Int, amount: Float): Int {
        val blend = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val start = (from shr shift) and 0xFF
            val end = (to shr shift) and 0xFF
            return (start + (end - start) * blend).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
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

    private fun paint(
        samples: List<RoadSample>,
        width: Int,
        height: Int,
        aspect: Double,
        east: Double = 0.0,
        north: Double = 30.0,
        bearing: Double = 0.0,
        surface: Double = 0.06,
        lookSurface: Double = surface,
        field: ((Double, Double) -> Float)? = null
    ): Shot {
        val sky = PlainWorld.sky.packed() and 0xFFFFFF
        val horizon = PlainWorld.horizon.packed() and 0xFFFFFF
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val down = (y.toFloat() / height.toFloat()).coerceIn(0f, 1f)
            val color = mixChannel(sky, horizon, down * down)
            for (x in 0 until width) pixels[y * width + x] = color
        }
        val depth = FloatArray(width * height) { Float.POSITIVE_INFINITY }
        val pose = RaceCamera.pose(east, north, bearing, surface, lookSurface)
        val model = RaceCamera.cameraModel(east, north, bearing, surface, lookSurface)
        val forwardX = -model[8]
        val forwardY = -model[9]
        val forwardZ = -model[10]
        val fov = DriveDisplay.verticalFov(aspect)
        val order = PlainWorld.haze.map { it.name } + listOf(
            PlainWorld.earth.name,
            PlainWorld.grassDeep.name,
            PlainWorld.grassLight.name,
            PlainWorld.ground.name,
            PlainWorld.verge.name,
            PlainWorld.shoulder.name,
            PlainWorld.roadShade.name,
            PlainWorld.road.name,
            PlainWorld.kerbRed.name,
            PlainWorld.kerbWhite.name,
            PlainWorld.edge.name,
            PlainWorld.center.name
        )
        val meshes = PlainWorld.meshes(samples, field = field).sortedBy { order.indexOf(it.material) }
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
        val hazeColors = PlainWorld.haze.map { it.packed() and 0xFFFFFF }.toSet()
        for (pixel in pixels) {
            when (pixel) {
                sky -> skyCount++
                groundColor -> ground++
                roadColor -> road++
                shoulderColor -> shoulder++
                centerColor, edgeColor -> white++
                in hazeColors -> far++
                else -> if ((pixel and 0xFF) > ((pixel ushr 16) and 0xFF)) skyCount++
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
