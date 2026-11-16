package hu.toonnav.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves the driving world no longer depends on the KTX2 samplers that
 * OpenGL ES samples as black, and that the diagnostic ground, road, and
 * axes sit inside the five required screens. This is a JVM test. It does
 * not open a phone GPU.
 */
class WorldDiagnosticTest {
    @Test
    fun drivingMaterialsAreSolidColorsWithNoSamplers() {
        val json = RaceGlb.json(RaceGlb.encode(RaceGeometry.libraryModel()))
        assertFalse(json.has("images"))
        assertFalse(json.has("textures"))
        assertFalse(json.has("extensionsUsed"))
        val asphalt = material(json, "asphalt")
        val grass = material(json, "grass")
        val shoulder = material(json, "shoulder")
        assertFalse(asphalt.has("occlusionTexture"))
        assertFalse(asphalt.has("normalTexture"))
        assertFalse(grass.has("occlusionTexture"))
        assertFalse(shoulder.has("occlusionTexture"))
        val asphaltColor = asphalt.getJSONObject("pbrMetallicRoughness").getJSONArray("baseColorFactor")
        assertTrue(asphaltColor.getDouble(0) in 0.35..0.7)
        assertEquals(0.0, asphalt.getJSONObject("pbrMetallicRoughness").getDouble("metallicFactor"), 0.001)
        val grassColor = grass.getJSONObject("pbrMetallicRoughness").getJSONArray("baseColorFactor")
        assertTrue(grassColor.getDouble(1) > grassColor.getDouble(0) * 1.4)
        assertTrue(grassColor.getDouble(1) > grassColor.getDouble(2))
        assertEquals(0.0, grass.getJSONObject("pbrMetallicRoughness").getDouble("metallicFactor"), 0.001)
        val shoulderColor = shoulder.getJSONObject("pbrMetallicRoughness").getJSONArray("baseColorFactor")
        assertTrue(shoulderColor.getDouble(0) > shoulderColor.getDouble(2))
    }

    @Test
    fun aDimFallbackSkyIsBlackUnderTheDrivingExposure() {
        val sunny = RaceLighting.exposure(16f, 1f / 125f, 100f)
        assertTrue(0.62f * sunny < 0.001f)
        val shown = RaceLighting.dayRadiance[2] * sunny
        assertTrue(shown > 0.4f)
        assertTrue(shown < 1.2f)
        val night = RaceLighting.exposure(8f, 1f / 30f, 800f)
        val nightShown = RaceLighting.nightRadiance[2] * night
        assertTrue(nightShown > 0.05f)
        assertTrue(nightShown < 0.35f)
    }

    @Test
    fun diagnosticGroundRoadAndAxesAreInsideEveryRequiredScreen() {
        for (aspect in profiles()) {
            val hidden = WorldDiagnostic.inFrontOfCamera(aspect)
            assertTrue("aspect $aspect hid $hidden", hidden.isEmpty())
        }
    }

    @Test
    fun fiveDiagnosticPiecesStaySeparateFromTheDrivingScreen() {
        val ground = WorldDiagnostic.ground()
        val road = WorldDiagnostic.road()
        val grid = WorldDiagnostic.grid()
        val axes = WorldDiagnostic.axes()
        val world = WorldDiagnostic.drivingWorld()
        assertEquals("grass", ground.material)
        assertEquals("asphalt", road.material)
        assertEquals("edge", grid.material)
        assertEquals(listOf("kerbRed", "canopy", "marking"), axes.map { it.material })
        assertTrue(world.any { it.material.startsWith("asphalt") && it.triangleCount > 0 })
        assertTrue(world.any { it.material == "grass" && it.triangleCount > 0 })
        assertTrue(world.any { it.material == "shoulder" && it.triangleCount > 0 })
        assertTrue(world.any { it.material == "kerbRed" && it.triangleCount > 0 })
        val grass = RaceGeometry.materials().first { it.name == "grass" }
        val asphalt = RaceGeometry.materials().first { it.name == "asphalt" }
        assertTrue(grass.green > grass.red && grass.green > grass.blue)
        assertTrue(asphalt.red in 0.35f..0.7f)
        assertTrue(kotlin.math.abs(asphalt.red - asphalt.green) < 0.08f)
        assertFalse(isMagenta(grass.red, grass.green, grass.blue))
        assertFalse(isMagenta(asphalt.red, asphalt.green, asphalt.blue))
        for (material in RaceGeometry.materials()) {
            assertEquals(material.name, 1f, material.alpha)
            assertFalse(isMagenta(material.red, material.green, material.blue))
            assertFalse(material.red < 0.02f && material.green < 0.02f && material.blue < 0.02f)
        }
        val activity = java.io.File("src/main/java/hu/toonnav/map/RacePrototypeActivity.kt").readText()
        assertFalse(activity.contains("WorldDiagnostic"))
        for (aspect in profiles()) {
            assertTrue(WorldDiagnostic.visible(0f, 0f, 20f, aspect))
            assertTrue(WorldDiagnostic.visible(0f, RaceGeometry.deckTop.toFloat(), 18f, aspect))
            assertTrue(WorldDiagnostic.visible(0f, RaceGeometry.deckTop.toFloat(), 16f, aspect))
            assertTrue(WorldDiagnostic.drivingCenterVisible(aspect))
        }
    }

    @Test
    fun normalAndOrmFilesAreLinearUastc() {
        for (name in listOf("asphalt-normal.ktx2", "asphalt-orm.ktx2", "grass-normal.ktx2", "grass-orm.ktx2", "gravel-normal.ktx2", "gravel-orm.ktx2")) {
            val header = Ktx2Facts.inspect(File("src/main/assets/models/road/$name").readBytes())
            assertTrue(name, header != null && Ktx2Facts.isLinearUastc(header))
            assertEquals(0, header!!.vkFormat)
        }
        val color = Ktx2Facts.inspect(File("src/main/assets/models/road/grass-color.ktx2").readBytes())
        assertTrue(color != null)
        assertEquals(1, color!!.supercompression)
        assertEquals(163, color.colorModel)
        assertEquals(2, color.transfer)
        assertFalse(Ktx2Facts.isLinearUastc(color))
        assertNull(Ktx2Facts.inspect(RaceGlb.asphaltPng()))
    }

    private fun profiles(): List<Double> {
        return listOf(1280.0 / 720.0, 1920.0 / 1080.0, 2560.0 / 1440.0, 1080.0 / 2400.0, 2400.0 / 1080.0)
    }

    private fun isMagenta(red: Float, green: Float, blue: Float): Boolean {
        return red > 0.8f && blue > 0.8f && green < 0.25f
    }

    private fun material(json: JSONObject, name: String): JSONObject {
        val materials = json.getJSONArray("materials")
        for (index in 0 until materials.length()) {
            val item = materials.getJSONObject(index)
            if (item.getString("name") == name) return item
        }
        throw AssertionError(name)
    }
}
