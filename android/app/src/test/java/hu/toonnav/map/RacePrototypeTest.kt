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
    fun theFormulaAssetIsADetailedLicensedModelAndTheRoadHasThickness() {
        val file = java.io.File("src/main/assets/models/formula-car.glb")
        val bytes = file.readBytes()
        assertTrue(bytes.size > 500_000)
        assertEquals(0x46546C67, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int)
        val json = RaceGlb.json(bytes)
        assertTrue(json.optJSONArray("extensionsRequired") == null)
        val names = List(json.getJSONArray("nodes").length()) { json.getJSONArray("nodes").getJSONObject(it).optString("name") }
        assertTrue(names.contains("formula-car"))
        assertTrue(names.contains("front_wing"))
        val materials = List(json.getJSONArray("materials").length()) { json.getJSONArray("materials").getJSONObject(it).optString("name") }
        assertTrue(materials.any { it.startsWith("paint_primary") })
        assertTrue(materials.any { it.startsWith("carbon") })
        val paint = (0 until json.getJSONArray("materials").length())
            .map { json.getJSONArray("materials").getJSONObject(it) }
            .first { it.optString("name") == "paint_primary_body" }
        assertEquals(1.0, paint.getJSONObject("extensions").getJSONObject("KHR_materials_clearcoat").getDouble("clearcoatFactor"), 0.001)
        val accessors = json.getJSONArray("accessors")
        var triangles = 0
        for (meshIndex in 0 until json.getJSONArray("meshes").length()) {
            val primitives = json.getJSONArray("meshes").getJSONObject(meshIndex).getJSONArray("primitives")
            for (primitive in 0 until primitives.length()) {
                val indices = primitives.getJSONObject(primitive).optInt("indices", -1)
                if (indices >= 0) triangles += accessors.getJSONObject(indices).getInt("count") / 3
            }
        }
        assertTrue(triangles > 30_000)
        val license = java.io.File("src/main/assets/models/formula-car-license.txt").readText()
        assertTrue(license.contains("CC0"))
        val road = RaceGeometry.worldModel(RaceGeometry.sampleRoute(line(0.0, 80.0), 2.0)).first { it.material == "asphalt" }
        val box = RaceGeometry.bounds(road)
        assertTrue(box[4] - box[1] > 0.05f)
        assertTrue(RaceGeometry.worldModel(RaceGeometry.sampleRoute(line(0.0, 80.0), 2.0)).any { it.material == "grass" })
        val slots = GarageFleet.cars.map { car ->
            RaceLivery.color("body", car) to RaceLivery.color("accent", car)
        }
        assertEquals(GarageFleet.cars.size, slots.toSet().size)
        assertEquals("body", RaceLivery.slot("paint_primary_body"))
        assertEquals(null, RaceLivery.slot("tire"))
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
        val samples = RaceGeometry.sampleRoute(line, 2.0, List(line.size) { RoadKind.MAIN })
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
        val first = encoded()
        val second = encoded()
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
        assertEquals("road", json.getJSONArray("nodes").getJSONObject(0).getString("name"))
        val names = materialNames(json)
        assertTrue(names.containsAll(listOf("asphalt", "grass", "shoulder", "marking")))
        val textured = RaceGlb.json(RaceGlb.encode(RaceTrack.model(RaceTrack.samplePreview()), roadImages()))
        val asphalt = (0 until textured.getJSONArray("materials").length())
            .map { textured.getJSONArray("materials").getJSONObject(it) }
            .first { it.getString("name") == "asphalt" }
        assertTrue(asphalt.has("normalTexture"))
        assertEquals("image/ktx2", textured.getJSONArray("images").getJSONObject(0).getString("mimeType"))
        assertTrue(json.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0)
            .getJSONObject("attributes").has("TEXCOORD_0"))
        assertTrue(second.size > 10_000)
        val png = RaceGlb.asphaltPng()
        assertEquals(137.toByte(), png[0])
        assertEquals("PNG".toByteArray()[0], png[1])
    }

    @Test
    fun theDrivingCameraKeepsTheNoseLowAndAgreesWithRouteMotion() {
        val pose = RaceCamera.pose(0.0, 0.0, 0.0)
        val nose = RaceCamera.nosePoint(0.0, 0.0, 0.0)
        val offset = RaceCamera.verticalOffset(pose, nose[0], nose[1], nose[2])
        assertTrue(offset < -0.15f)
        assertTrue(offset > -0.9f)
        val wing = RaceCamera.wingPoint(0.0, 0.0, 0.0, 1.0)
        val wingNdc = RaceCamera.ndc(pose, wing[0], wing[1], wing[2])
        assertTrue(abs(wingNdc[0]) < 0.92f)
        assertTrue(wingNdc[1] < -0.15f && wingNdc[1] > -0.98f)
        val tire = RaceCamera.tirePoint(0.0, 0.0, 0.0, -1.0)
        val tireNdc = RaceCamera.ndc(pose, tire[0], tire[1], tire[2])
        assertTrue(abs(tireNdc[0]) < 0.95f)
        assertTrue(tireNdc[1] > -0.98f && tireNdc[1] < -0.1f)
        val ahead = RaceCamera.ndc(pose, 0f, 0.1f, 40f)
        assertTrue(abs(ahead[0]) < 0.2f)
        assertTrue(ahead[1] > offset)
        val model = RaceCamera.cameraModel(0.0, 0.0, 0.0)
        assertTrue(model[0] > 0.8f)
        assertTrue(java.io.File("src/main/assets/models/noon-sky.hdr").readBytes().decodeToString(0, 10).startsWith("#?RADIANCE"))
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

    private fun encoded(): ByteArray {
        return RaceGlb.encode(RaceTrack.model(RaceTrack.samplePreview()))
    }

    @Test
    fun roadClassChangesWidthAndMarkings() {
        val straight = line(0.0, 80.0)
        val motorway = RaceGeometry.worldModel(RaceGeometry.sampleRoute(straight, 2.0, List(straight.size) { RoadKind.MOTORWAY }))
        val residential = RaceGeometry.worldModel(RaceGeometry.sampleRoute(straight, 2.0, List(straight.size) { RoadKind.RESIDENTIAL }))
        val unknown = RaceGeometry.worldModel(RaceGeometry.sampleRoute(straight, 2.0))
        assertTrue(eastSpan(motorway.first { it.material == "asphalt" }) > eastSpan(residential.first { it.material == "asphalt" }) + 4.0)
        assertTrue(motorway.any { it.material == "edge" })
        assertTrue(motorway.none { it.material == "marking" })
        assertTrue(residential.none { it.material == "marking" })
        assertTrue(unknown.none { it.material == "marking" })
        val corner = listOf(0.0 to 0.0, 0.0 to 40.0, 40.0 to 40.0)
        val motorwayCorner = RaceGeometry.worldModel(RaceGeometry.sampleRoute(corner, 2.0, List(corner.size) { RoadKind.MOTORWAY }))
        assertTrue(motorwayCorner.none { it.material.startsWith("kerb") })
        val mainCorner = RaceGeometry.worldModel(RaceGeometry.sampleRoute(corner, 2.0, List(corner.size) { RoadKind.MAIN }))
        assertTrue(mainCorner.any { it.material.startsWith("kerb") })
    }

    @Test
    fun elevationRaisesTheRoadAndTheCarSitsOnIt() {
        val straight = line(0.0, 40.0)
        val heights = floatArrayOf(0f, 6f)
        val samples = RaceGeometry.sampleRoute(straight, 2.0, emptyList(), heights)
        assertTrue(samples.last().elevation > 5f)
        var highest = 0f
        for (road in RaceGeometry.worldModel(samples).filter { it.material.startsWith("asphalt") }) {
            for (index in 1 until road.positions.size step 3) highest = maxOf(highest, road.positions[index])
        }
        assertTrue(highest > 5f)
        val surface = samples.last().elevation.toDouble() + RaceGeometry.deckTop
        val planted = RaceCamera.carTransform(0.0, 40.0, 0.0, surface)
        assertEquals(surface.toFloat(), planted[13], 0.001f)
    }

    @Test
    fun routeStepsPaintTheOverviewWithTheirClass() {
        val route = listOf(OsrmPoint(19.0, 47.0), OsrmPoint(19.002, 47.0))
        val step = OsrmStep("M1", 200.0, 10.0, OsrmManeuver("continue", "straight", route.first(), null), listOf("motorway"))
        val kinds = RouteRoadClass.kinds(route, listOf(step))
        assertEquals(RoadKind.MOTORWAY, kinds.first())
        assertEquals(RoadKind.MOTORWAY, kinds.last())
        val unknown = RouteRoadClass.kinds(route, listOf(step.copy(classes = emptyList(), maneuver = step.maneuver.copy(type = "turn"))))
        assertEquals(RoadKind.UNKNOWN, unknown.first())
    }

    @Test
    fun terrariumDecodeMatchesThePublishedFormula() {
        assertEquals(140.1, TerrainTiles.decode(128, 140, 22), 0.05)
        val tile = TerrainTiles.tileIndex(19.040, 47.497, 13)
        assertEquals(4529, tile.first)
        assertEquals(2864, tile.second)
    }

    @Test
    fun corridorChunksMeetWithoutRebuildingTheWholeRoute() {
        val points = ArrayList<Pair<Double, Double>>()
        var north = 0.0
        while (north <= 900.0) {
            points.add(0.0 to north)
            north += 20.0
        }
        val samples = RaceGeometry.sampleRoute(points, 2.5)
        val end = samples.last().along
        val covered = HashSet<Int>()
        for (index in 0 until samples.size - 1) covered.add(RaceGeometry.chunkIndex(samples[index].along))
        assertEquals(RaceGeometry.chunksCovering(0.0, end).toSet(), covered)
        val first = RaceGeometry.worldModel(samples, null, 0.0, RaceGeometry.chunkMeters)
        val second = RaceGeometry.worldModel(samples, null, RaceGeometry.chunkMeters, RaceGeometry.chunkMeters * 2)
        assertTrue(first.any { it.material.startsWith("asphalt") })
        assertTrue(second.any { it.material.startsWith("asphalt") })
        assertEquals(maxNorth(first).toDouble(), minNorth(second).toDouble(), 0.2)
        val names = RaceGeometry.worldModel(samples).map { it.material }.toSet()
        assertTrue(names.contains("asphalt"))
        assertTrue(names.any { it.startsWith("asphalt") && it != "asphalt" })
        val straight = line(0.0, 40.0)
        val road = RaceGeometry.worldModel(RaceGeometry.sampleRoute(straight, 2.0)).first { it.material.startsWith("asphalt") }
        val frame = RoadFrame.quaternions(road)
        val length = kotlin.math.sqrt(frame[0] * frame[0] + frame[1] * frame[1] + frame[2] * frame[2] + frame[3] * frame[3])
        assertEquals(1.0, length.toDouble(), 0.001)
    }

    @Test
    fun sceneryUsesMappedFeaturesAndStaysOffTheRoad() {
        val samples = RaceGeometry.sampleRoute(line(0.0, 80.0), 2.0)
        assertTrue(Scenery.obstructs(0.0, 20.0, samples))
        assertTrue(!Scenery.obstructs(40.0, 20.0, samples))
        val square = listOf(30.0 to 10.0, 80.0 to 10.0, 80.0 to 70.0, 30.0 to 70.0, 30.0 to 10.0)
        assertTrue(Scenery.inside(50.0, 40.0, square))
        assertTrue(!Scenery.inside(0.0, 40.0, square))
        val wood = Scenery.forestPoints(square, 4, samples)
        assertTrue(wood.isNotEmpty())
        assertTrue(wood.all { Scenery.inside(it.first, it.second, square) && !Scenery.obstructs(it.first, it.second, samples) })
        val origin = OsrmPoint(19.0, 47.0)
        val json = """
            {"elements":[
              {"type":"node","lon":19.0,"lat":47.0,"tags":{"natural":"tree"}},
              {"type":"node","lon":19.001,"lat":47.0,"tags":{"natural":"tree"}},
              {"type":"way","tags":{"building":"yes","height":"12"},"geometry":[
                {"lon":19.0,"lat":47.0002},{"lon":19.00005,"lat":47.0002},
                {"lon":19.00005,"lat":47.00025},{"lon":19.0,"lat":47.00025},{"lon":19.0,"lat":47.0002}
              ]},
              {"type":"way","tags":{"building":"yes","building:levels":"2"},"geometry":[
                {"lon":19.0011,"lat":47.0004},{"lon":19.0012,"lat":47.0004},
                {"lon":19.0012,"lat":47.0005},{"lon":19.0011,"lat":47.0005},{"lon":19.0011,"lat":47.0004}
              ]}
            ]}
        """.trimIndent()
        val meshes = Scenery.parse(json, origin, samples, null)
        assertTrue(meshes.any { it.material == "trunk" })
        assertTrue(meshes.any { it.material.startsWith("building") })
        val buildings = meshes.filter { it.material.startsWith("building") }
        assertEquals(1, buildings.size)
        var highest = 0f
        for (mesh in buildings) {
            for (index in 1 until mesh.positions.size step 3) highest = maxOf(highest, mesh.positions[index])
        }
        assertTrue(highest > 5f)
    }

    @Test
    fun distantGroundFollowsSurveyedElevationAndAvoidsTheCarriageway() {
        val samples = RaceGeometry.sampleRoute(line(0.0, 40.0), 2.0)
        val hills = RaceGeometry.horizon(samples, { east, _ -> if (kotlin.math.abs(east) > 120.0) 18f else 0f }, 0.0, 20.0)
        assertTrue(hills != null)
        var far = 0f
        var nearRoad = false
        val mesh = hills!!
        for (index in mesh.positions.indices step 3) {
            val east = mesh.positions[index]
            val height = mesh.positions[index + 1]
            val north = mesh.positions[index + 2]
            if (kotlin.math.abs(east) > 120f) far = maxOf(far, height)
            if (kotlin.math.abs(east) < 12f && north in 0f..40f) nearRoad = true
        }
        assertTrue(far > 10f)
        assertTrue(!nearRoad)
    }

    @Test
    fun theMeshWindowFollowsTheCarInsteadOfTheWholeRoute() {
        val points = ArrayList<Pair<Double, Double>>()
        var north = 0.0
        while (north <= 4000.0) {
            points.add(0.0 to north)
            north += 20.0
        }
        val samples = RaceGeometry.sampleRoute(points, 2.5)
        val near = RaceTrack.window(samples, 100.0)
        val far = RaceTrack.window(samples, 2500.0)
        assertTrue(near.last().along < 2000.0)
        assertTrue(far.first().along > 2000.0)
        assertTrue(far.last().along - far.first().along < 2100.0)
    }

    private fun maxNorth(meshes: List<RaceMesh>): Float = northLimit(meshes, highest = true)

    private fun minNorth(meshes: List<RaceMesh>): Float = northLimit(meshes, highest = false)

    private fun northLimit(meshes: List<RaceMesh>, highest: Boolean): Float {
        var value = if (highest) Float.NEGATIVE_INFINITY else Float.POSITIVE_INFINITY
        for (mesh in meshes.filter { it.material.startsWith("asphalt") }) {
            for (index in 2 until mesh.positions.size step 3) {
                value = if (highest) maxOf(value, mesh.positions[index]) else minOf(value, mesh.positions[index])
            }
        }
        return value
    }

    private fun eastSpan(mesh: RaceMesh): Double {
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        for (index in mesh.positions.indices step 3) {
            val east = mesh.positions[index].toDouble()
            if (east < min) min = east
            if (east > max) max = east
        }
        return max - min
    }

    private fun roadImages(): Map<String, ByteArray> {
        return RaceGlb.roadImages { name -> java.io.File("src/main/assets/models/road/$name.ktx2").readBytes() }
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
