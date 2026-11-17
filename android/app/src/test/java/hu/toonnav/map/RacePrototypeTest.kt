package hu.toonnav.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val straightRoad = RaceGeometry.worldModel(RaceGeometry.sampleRoute(line(0.0, 80.0), 2.0))
        val road = straightRoad.first { it.material == "asphalt" }
        val box = RaceGeometry.bounds(road)
        assertTrue(box[4] - box[1] > 0.05f)
        var facingUp = 0
        var facingDown = 0
        for (index in road.normals.indices step 3) {
            val ny = road.normals[index + 1]
            if (kotlin.math.abs(ny) > 0.5f) {
                if (ny > 0f) facingUp++ else facingDown++
            }
        }
        assertTrue(facingUp > 0 && facingDown == 0)
        val blend = straightRoad.first { it.material == "vergeBlend" }
        for (index in blend.positions.indices step 3) {
            assertTrue(kotlin.math.abs(blend.positions[index]) > 2.4f)
            assertTrue(blend.positions[index + 1] < RaceGeometry.deckTop)
        }
        assertTrue(straightRoad.any { it.material == "grass" })
        val slots = GarageFleet.cars.map { car ->
            RaceLivery.color("body", car) to RaceLivery.color("accent", car)
        }
        assertEquals(GarageFleet.cars.size, slots.toSet().size)
        assertEquals("body", RaceLivery.slot("paint_primary_body"))
        assertEquals("front", RaceLivery.part("front_wing_shell", "paint_primary"))
        assertEquals("rear", RaceLivery.part("rear_wing_shell", "paint_primary"))
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
        val red = cornerWorld.first { it.material == "kerbRed" }
        val white = cornerWorld.first { it.material == "kerbWhite" }
        assertTrue(red.triangleCount > 0 && white.triangleCount > 0)
        var minEast = Float.POSITIVE_INFINITY
        var maxEast = Float.NEGATIVE_INFINITY
        var raised = 0f
        for (mesh in listOf(red, white)) {
            for (index in mesh.positions.indices step 3) {
                raised = maxOf(raised, mesh.positions[index + 1])
                val north = mesh.positions[index + 2]
                if (north in 30f..42f) {
                    minEast = minOf(minEast, mesh.positions[index])
                    maxEast = maxOf(maxEast, mesh.positions[index])
                }
            }
        }
        assertTrue(minEast < -2f)
        assertTrue(maxEast > 2f)
        assertTrue(raised > RaceGeometry.deckTop + 0.05f)
        val preview = RaceGeometry.worldModel(RaceTrack.samplesFor(RaceTrack.samplePreview()))
        assertTrue(preview.any { it.material == "kerbRed" && it.triangleCount > 0 })
        assertTrue(preview.any { it.material == "kerbWhite" && it.triangleCount > 0 })
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
        val driving = RaceGlb.json(RaceGlb.encode(RaceGeometry.libraryModel()))
        assertFalse(driving.has("images"))
        assertFalse(driving.has("textures"))
        val asphalt = (0 until driving.getJSONArray("materials").length())
            .map { driving.getJSONArray("materials").getJSONObject(it) }
            .first { it.getString("name") == "asphalt" }
        assertFalse(asphalt.has("normalTexture"))
        assertFalse(asphalt.has("occlusionTexture"))
        assertEquals(0.0, asphalt.getJSONObject("pbrMetallicRoughness").getDouble("metallicFactor"), 0.001)
        val factor = asphalt.getJSONObject("pbrMetallicRoughness").getJSONArray("baseColorFactor")
        assertTrue(factor.getDouble(0) in 0.35..0.7)
        assertTrue(json.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0)
            .getJSONObject("attributes").has("TEXCOORD_0"))
        assertTrue(second.size > 10_000)
        val png = RaceGlb.asphaltPng()
        assertEquals(137.toByte(), png[0])
        assertEquals("PNG".toByteArray()[0], png[1])
        val mean = pngMeanRed(png)
        assertTrue(mean in 150..185)
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
        assertTrue(motorwayCorner.any { it.material == "kerbRed" })
        assertTrue(motorwayCorner.any { it.material == "kerbWhite" })
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
    fun theCarriagewayMeshIsWiderThanItsLaneMarkings() {
        val straight = RaceGeometry.sampleRoute(line(0.0, 80.0), 2.0, List(2) { RoadKind.MAIN })
        val bend = RaceGeometry.sampleRoute(listOf(0.0 to 0.0, 0.0 to 36.0, 28.0 to 36.0), 2.0, List(3) { RoadKind.MAIN })
        val straightShot = paintDrive(straight, 0.0, 0.0, 0.0)
        val bendShot = paintDrive(bend, 0.0, 8.0, 0.0)
        assertTrue(straightShot.asphalt > straightShot.white * 4)
        assertTrue(straightShot.ground > 20)
        assertTrue(bendShot.asphalt > bendShot.white * 3)
        assertTrue(bendShot.ground > 10)
        val road = RaceGeometry.worldModel(straight).first { it.material.startsWith("asphalt") }
        val frame = RoadFrame.quaternions(road)
        var mixed = 0
        var triangles = 0
        val index = road.indices
        var cursor = 0
        while (cursor < index.size) {
            val a = index[cursor]
            val b = index[cursor + 1]
            val c = index[cursor + 2]
            if (road.normals[a * 3 + 1] > 0.5f) {
                triangles++
                val ab = quaternionDot(frame, a, b)
                val ac = quaternionDot(frame, a, c)
                val bc = quaternionDot(frame, b, c)
                if (ab < 0f || ac < 0f || bc < 0f) mixed++
            }
            cursor += 3
        }
        assertEquals(0, mixed)
        assertTrue(triangles > 0)
    }

    @Test
    fun theShaderGetsAWhiteVertexColorAndASecondUv() {
        val samples = RaceGeometry.sampleRoute(line(0.0, 40.0), 2.0, List(2) { RoadKind.MAIN })
        val asphalt = RaceGeometry.worldModel(samples).first { it.material == "asphalt" }
        val grass = RaceGeometry.worldModel(samples).first { it.material == "grass" }
        for (mesh in listOf(asphalt, grass)) {
            val packed = RoadVertices.bytes(mesh, RoadFrame.quaternions(mesh))
            assertEquals(mesh.vertexCount * RoadVertices.stride, packed.remaining())
            for (vertex in intArrayOf(0, mesh.vertexCount / 2, mesh.vertexCount - 1)) {
                val base = vertex * RoadVertices.stride
                assertEquals(1f, packed.getFloat(base + 36), 0f)
                assertEquals(1f, packed.getFloat(base + 40), 0f)
                assertEquals(1f, packed.getFloat(base + 44), 0f)
                assertEquals(1f, packed.getFloat(base + 48), 0f)
                assertEquals(packed.getFloat(base + 28), packed.getFloat(base + 52), 0f)
                assertEquals(packed.getFloat(base + 32), packed.getFloat(base + 56), 0f)
            }
        }
    }

    @Test
    fun fogDoesNotCoverTheRoadAndTheFarPlaneIncludesTheCorridor() {
        assertTrue(RaceCamera.farMeters > RaceGeometry.corridorAhead + 500.0)
        assertTrue(RaceFog.height <= RaceGeometry.deckTop + 0.5f)
        assertTrue(RaceFog.cutOffDistance > RaceCamera.farMeters)
        assertEquals(0f, RaceFog.opacity(80f, -1f, 1.14f), 0.001f)
        val bend = RaceFog.opacity(220f, -1f, 1.14f)
        assertTrue(bend < 0.12f)
        val distant = RaceFog.opacity(1_000f, -1f, 1.14f)
        assertTrue(distant < 0.45f)
        assertEquals(0f, RaceFog.opacity(1.0e19f, 0f, 1.14f), 0.001f)
        val pose = RaceCamera.pose(0.0, 0.0, 0.0)
        for (ahead in floatArrayOf(8f, 80f, 400f)) {
            val screen = RaceCamera.ndc(pose, 0f, 0.06f, ahead)
            assertTrue(abs(screen[0]) < 1f)
            assertTrue(abs(screen[1]) < 1f)
        }
        val afterTheTurn = RaceCamera.ndc(pose, 18f, 0.06f, 40f)
        assertTrue(abs(afterTheTurn[0]) < 1f)
        assertTrue(abs(afterTheTurn[1]) < 1f)
        assertTrue(400.0 < RaceCamera.farMeters)
        assertTrue(RaceCamera.nearMeters < 0.2)
    }

    @Test
    fun theWholeRouteReachesTheMeshAroundTheCarOnAStraightAndABend() {
        val points = ArrayList<Pair<Double, Double>>()
        var north = 0.0
        while (north <= 900.0) {
            points.add(0.0 to north)
            north += 20.0
        }
        val straight = RaceGeometry.sampleRoute(points, 2.5)
        val end = straight.last().along
        val fromTheStart = RaceGeometry.chunksCovering(0.0, end)
        assertEquals(0, missedCarriageway(straight, fromTheStart))
        val along = 2_500.0
        val longPoints = ArrayList<Pair<Double, Double>>()
        var cursor = 0.0
        while (cursor <= 4_000.0) {
            longPoints.add(0.0 to cursor)
            cursor += 20.0
        }
        val longRoute = RaceGeometry.sampleRoute(longPoints, 2.5)
        val aroundTheCar = RaceGeometry.chunksCovering(along, longRoute.last().along)
        assertTrue(RaceGeometry.chunkIndex(along) in aroundTheCar)
        assertTrue(0 !in aroundTheCar)
        assertEquals(0, missedCarriageway(longRoute, aroundTheCar))
        val corner = RaceGeometry.sampleRoute(listOf(0.0 to 0.0, 0.0 to 40.0, 40.0 to 40.0), 2.0)
        val bent = RaceGeometry.chunksCovering(0.0, corner.last().along)
        assertEquals(0, missedCarriageway(corner, bent))
        assertEquals(0, vergeCoveringCarriageway(corner, bent))
        val tight = RaceGeometry.sampleRoute(arc(15.0, 40.0), 2.0)
        assertEquals(0, missedCarriageway(tight, RaceGeometry.chunksCovering(0.0, tight.last().along)))
        val road = RaceGeometry.worldModel(RaceGeometry.sampleRoute(line(0.0, 40.0), 2.0)).first { it.material.startsWith("asphalt") }
        val frame = RoadFrame.quaternions(road)
        var facingUp = 0
        for (index in 0 until road.vertexCount) {
            if (road.normals[index * 3 + 1] < 0.5f) continue
            facingUp++
            assertTrue(shadedNormalY(frame, index) > 0.5f)
        }
        assertTrue(facingUp > 0)
    }

    @Test
    fun missingSurveyStillDrawsFlatGroundOffTheCarriageway() {
        val samples = RaceGeometry.sampleRoute(line(0.0, 80.0), 2.0)
        val ground = RaceGeometry.horizon(samples, null, 0.0, 40.0)
        assertTrue(ground != null)
        val mesh = ground!!
        assertTrue(mesh.vertexCount >= 3)
        for (index in mesh.positions.indices step 3) {
            val east = mesh.positions[index]
            val height = mesh.positions[index + 1]
            val north = mesh.positions[index + 2]
            assertTrue(height > -1f && height < 1f)
            assertTrue(!(abs(east) < 12f && north in 0f..80f))
        }
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

    private fun pngMeanRed(png: ByteArray): Int {
        val header = java.nio.ByteBuffer.wrap(png, 16, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        val data = java.io.ByteArrayOutputStream()
        var cursor = 8
        while (cursor + 8 <= png.size) {
            val length = java.nio.ByteBuffer.wrap(png, cursor, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
            val type = String(png, cursor + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") data.write(png, cursor + 8, length)
            cursor += 12 + length
        }
        val inflater = java.util.zip.Inflater()
        inflater.setInput(data.toByteArray())
        val raw = ByteArray(header * (1 + header * 3))
        val inflated = inflater.inflate(raw)
        inflater.end()
        var sum = 0L
        var count = 0
        var index = 0
        while (index < inflated) {
            index++
            var column = 0
            while (column < header && index < inflated) {
                sum += raw[index].toInt() and 0xff
                count++
                index += 3
                column++
            }
        }
        return (sum / count).toInt()
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

    private fun missedCarriageway(samples: List<RoadSample>, chunks: IntRange): Int {
        val asphalt = chunks.flatMap { index ->
            RaceGeometry.worldModel(
                samples,
                null,
                index * RaceGeometry.chunkMeters,
                (index + 1) * RaceGeometry.chunkMeters
            )
        }.filter { it.material.startsWith("asphalt") }
        val end = samples.last().along
        var missed = 0
        for (sample in samples) {
            if (sample.along < 2.0 || sample.along > end - 2.0) continue
            if (RaceGeometry.chunkIndex(sample.along) !in chunks) continue
            if (asphalt.none { coversPlan(it, sample.east.toFloat(), sample.north.toFloat()) }) missed++
        }
        return missed
    }

    private fun vergeCoveringCarriageway(samples: List<RoadSample>, chunks: IntRange): Int {
        val grass = chunks.flatMap { index ->
            RaceGeometry.worldModel(
                samples,
                null,
                index * RaceGeometry.chunkMeters,
                (index + 1) * RaceGeometry.chunkMeters
            )
        }.filter { it.material == "grass" }
        val end = samples.last().along
        var covered = 0
        for (sample in samples) {
            if (sample.along < 2.0 || sample.along > end - 2.0) continue
            if (grass.any { coversPlan(it, sample.east.toFloat(), sample.north.toFloat()) }) covered++
        }
        return covered
    }

    private fun coversPlan(mesh: RaceMesh, x: Float, z: Float): Boolean {
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

    /** Filament's toTangentFrame normal, Y component. */
    private fun shadedNormalY(quaternion: FloatArray, vertex: Int): Float {
        val x = quaternion[vertex * 4]
        val y = quaternion[vertex * 4 + 1]
        val z = quaternion[vertex * 4 + 2]
        val ny = -2f * x * quaternion[vertex * 4 + 3] + 2f * y * z
        return ny
    }

    private class DriveShot(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val asphalt: Int,
        val white: Int,
        val ground: Int
    )

    private fun paintDrive(samples: List<RoadSample>, east: Double, north: Double, bearing: Double): DriveShot {
        val width = 320
        val height = 180
        val pixels = IntArray(width * height) { 0x8FB6DB }
        val depth = FloatArray(width * height) { Float.POSITIVE_INFINITY }
        val pose = RaceCamera.pose(east, north, bearing)
        val model = RaceCamera.cameraModel(east, north, bearing)
        val forwardX = -model[8]
        val forwardY = -model[9]
        val forwardZ = -model[10]
        val meshes = RaceGeometry.worldModel(samples)
        val order = listOf("grass", "shoulder", "asphalt", "asphaltWarm", "asphaltCool", "asphaltDark", "rubber", "vergeBlend", "kerbRed", "kerbWhite", "edge", "marking")
        val sorted = meshes.sortedBy { mesh ->
            val rank = order.indexOf(mesh.material)
            if (rank < 0) 50 else rank
        }
        for (mesh in sorted) {
            val color = when {
                mesh.material.startsWith("asphalt") -> 0x9A9A96
                mesh.material == "grass" -> 0x3E6B32
                mesh.material == "shoulder" -> 0x8A8070
                mesh.material == "edge" || mesh.material == "marking" || mesh.material == "kerbWhite" -> 0xF2F2F0
                mesh.material == "kerbRed" -> 0xC4140A
                mesh.material == "rubber" -> 0x2A2A28
                else -> 0x666666
            }
            val index = mesh.indices
            val position = mesh.positions
            var cursor = 0
            while (cursor < index.size) {
                val screen = Array(3) { FloatArray(3) }
                var visible = true
                for (corner in 0 until 3) {
                    val vertex = index[cursor + corner]
                    val x = position[vertex * 3]
                    val y = position[vertex * 3 + 1]
                    val z = position[vertex * 3 + 2]
                    val vx = x - pose.eyeX
                    val vy = y - pose.eyeY
                    val vz = z - pose.eyeZ
                    val ahead = vx * forwardX + vy * forwardY + vz * forwardZ
                    if (ahead < RaceCamera.nearMeters.toFloat() || ahead > RaceCamera.farMeters.toFloat()) visible = false
                    val ndc = RaceCamera.ndc(pose, x, y, z)
                    screen[corner][0] = (ndc[0] * 0.5f + 0.5f) * width
                    screen[corner][1] = (0.5f - ndc[1] * 0.5f) * height
                    screen[corner][2] = ahead
                }
                if (visible) fillTriangle(pixels, depth, width, height, screen, color)
                cursor += 3
            }
        }
        var asphalt = 0
        var white = 0
        var ground = 0
        for (pixel in pixels) {
            when (pixel) {
                0x9A9A96 -> asphalt++
                0xF2F2F0 -> white++
                0x3E6B32, 0x8A8070 -> ground++
            }
        }
        return DriveShot(pixels, width, height, asphalt, white, ground)
    }

    private fun fillTriangle(
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
            for (x in minX..maxX) {
                val px = x + 0.5f
                val py = y + 0.5f
                val w0 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / area
                val w1 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / area
                val w2 = ((ax - px) * (by - py) - (bx - px) * (ay - py)) / area
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                val z = w0 * screen[0][2] + w1 * screen[1][2] + w2 * screen[2][2]
                val slot = y * width + x
                if (z < depth[slot]) {
                    depth[slot] = z
                    pixels[slot] = color
                }
            }
        }
    }

    private fun quaternionDot(quaternion: FloatArray, left: Int, right: Int): Float {
        var dot = 0f
        for (component in 0 until 4) dot += quaternion[left * 4 + component] * quaternion[right * 4 + component]
        return dot
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
