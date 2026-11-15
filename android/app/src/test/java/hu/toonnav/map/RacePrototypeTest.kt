package hu.toonnav.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class RacePrototypeTest {
    @Test
    fun theCarIsASolidMeshWithAWiderWingAndUnitNormals() {
        val model = RaceTrack.model(RaceTrack.samplePreview(), 0xFF1F4E8C.toInt(), 0xFFF4F1EC.toInt())
        val nose = model.car.first()
        val wings = model.car.drop(1).take(3)
        assertTrue(nose.triangleCount > 200)
        val noseBounds = RaceGeometry.bounds(nose)
        assertTrue(noseBounds[3] - noseBounds[0] > 0.02f)
        assertTrue(noseBounds[4] - noseBounds[1] > 0.02f)
        assertTrue(noseBounds[5] - noseBounds[2] > 1.0f)
        val wingSpan = wings.maxOf { bounds ->
            val box = RaceGeometry.bounds(bounds)
            box[3] - box[0]
        }
        assertTrue(wingSpan > noseBounds[3] - noseBounds[0] + 0.8f)
        for (mesh in model.car) {
            for (index in mesh.normals.indices step 3) {
                val length = sqrt(
                    mesh.normals[index] * mesh.normals[index] +
                        mesh.normals[index + 1] * mesh.normals[index + 1] +
                        mesh.normals[index + 2] * mesh.normals[index + 2]
                )
                assertEquals(1.0, length.toDouble(), 0.02)
            }
        }
    }

    @Test
    fun kerbsAppearOnlyWhereTheRouteActuallyTurns() {
        val straight = line(0.0, 80.0)
        val straightWorld = RaceGeometry.worldModel(RaceGeometry.sampleRoute(straight, 2.0))
        assertTrue(straightWorld.none { it.material.startsWith("kerb") })
        val gentle = arc(400.0, 30.0)
        val gentleWorld = RaceGeometry.worldModel(RaceGeometry.sampleRoute(gentle, 2.0))
        assertTrue(gentleWorld.none { it.material.startsWith("kerb") })
        val corner = listOf(0.0 to 0.0, 0.0 to 40.0, 40.0 to 40.0)
        val cornerWorld = RaceGeometry.worldModel(RaceGeometry.sampleRoute(corner, 2.0))
        assertTrue(cornerWorld.any { it.material.startsWith("kerb") && it.triangleCount > 0 })
    }

    @Test
    fun theRoadCenterStaysOnTheSuppliedRoute() {
        val line = line(0.0, 80.0)
        val samples = RaceGeometry.sampleRoute(line, 2.0)
        val dashes = RaceGeometry.worldModel(samples).first { it.material == "marking" }
        assertTrue(dashes.vertexCount > 8)
        for (index in dashes.positions.indices step 3) {
            val east = dashes.positions[index].toDouble()
            val north = dashes.positions[index + 2].toDouble()
            assertTrue(distanceToLine(east, north, line) < 0.2)
        }
    }

    @Test
    fun glbContainsTheCarRoadAndBothLiveries() {
        val first = encoded(GarageFleet.car("eben")!!)
        val second = encoded(GarageFleet.car("marvany")!!)
        assertEquals(0x46546C67, ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).int)
        assertTrue(first.size > 10_000)
        val json = RaceGlb.json(first)
        val buffer = ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(12)
        val jsonLength = buffer.int
        buffer.position(20 + jsonLength)
        val binLength = buffer.int
        assertEquals(json.getJSONArray("buffers").getJSONObject(0).getInt("byteLength"), binLength)
        val views = json.getJSONArray("bufferViews")
        for (index in 0 until views.length()) {
            val view = views.getJSONObject(index)
            val end = view.getInt("byteOffset") + view.getInt("byteLength")
            assertTrue(end <= binLength)
        }
        assertEquals("car", json.getJSONArray("nodes").getJSONObject(0).getString("name"))
        assertEquals("road", json.getJSONArray("nodes").getJSONObject(1).getString("name"))
        val names = materialNames(json)
        assertTrue(names.containsAll(listOf("body", "accent", "asphalt", "marking")))
        val eben = color(json, "body")
        val marble = color(RaceGlb.json(second), "body")
        assertTrue(abs(eben[0] - marble[0]) > 0.4)
        val colors = GarageFleet.cars.map { car -> color(RaceGlb.json(encoded(car)), "body").joinToString(",") }.toSet()
        assertEquals(GarageFleet.cars.size, colors.size)
        val png = RaceGlb.carbonPng()
        assertEquals(137.toByte(), png[0])
        assertEquals("PNG".toByteArray()[0], png[1])
    }

    @Test
    fun theDrivingCameraKeepsTheNoseLowAndAgreesWithRouteMotion() {
        val pose = RaceCamera.pose(0.0, 0.0, 0.0)
        val offset = RaceCamera.verticalOffset(pose, 0f, 0.36f, 2.28f)
        assertTrue(offset < -0.05f)
        val route = RaceTrack.samplePreview()
        val samples = RaceTrack.samplesFor(route)
        val bearing = RaceTrack.bearing(route, samples, 20.0)
        val motion = RouteMotion.pose(route, route.first().lon, route.first().lat, 0.0, true, 0L, true)
        assertTrue(motion != null)
        assertEquals(motion!!.bearingDegrees, bearing, 1.0)
        val transform = RaceCamera.carTransform(10.0, 20.0, 90.0)
        assertEquals(10f, transform[12], 0.001f)
        assertEquals(20f, transform[14], 0.001f)
        assertEquals(0f, transform[0], 0.001f)
        assertEquals(1f, transform[8], 0.02f)
    }

    private fun encoded(car: GarageFleet.Car): ByteArray {
        return RaceGlb.encode(RaceTrack.model(RaceTrack.samplePreview(), car.body, car.accent))
    }

    private fun materialNames(json: JSONObject): List<String> {
        val materials = json.getJSONArray("materials")
        return List(materials.length()) { materials.getJSONObject(it).getString("name") }
    }

    private fun color(json: JSONObject, name: String): List<Double> {
        val materials = json.getJSONArray("materials")
        for (index in 0 until materials.length()) {
            val material = materials.getJSONObject(index)
            if (material.getString("name") != name) continue
            val factor = material.getJSONObject("pbrMetallicRoughness").getJSONArray("baseColorFactor")
            return listOf(factor.getDouble(0), factor.getDouble(1), factor.getDouble(2))
        }
        throw AssertionError(name)
    }

    private fun line(northStart: Double, northEnd: Double): List<Pair<Double, Double>> {
        return listOf(0.0 to northStart, 0.0 to northEnd)
    }

    private fun arc(radius: Double, length: Double): List<Pair<Double, Double>> {
        val points = ArrayList<Pair<Double, Double>>()
        var cursor = 0.0
        while (cursor <= length) {
            val angle = cursor / radius
            points.add((radius * kotlin.math.sin(angle)) to (radius * (1.0 - kotlin.math.cos(angle))))
            cursor += 2.0
        }
        return points
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
}
