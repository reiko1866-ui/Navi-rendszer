package hu.toonnav.map

import kotlin.math.abs

/**
 * Diagnostic scene used only by tests. The driving screen does not build it
 * and has no control that shows it.
 *
 * The five pieces are independent: a green ground, a grey road, the daytime
 * sky radiance, a grid plus axes, and the real driving meshes for the sample
 * route. None of them carry a texture.
 */
object WorldDiagnostic {
    data class Marker(val name: String, val x: Float, val y: Float, val z: Float)

    fun ground(): RaceMesh = quad("grass", 0f, -24f, 6f, 24f, 72f)

    fun road(): RaceMesh {
        val y = RaceGeometry.deckTop.toFloat() + 0.01f
        return quad("asphalt", y, -3.6f, 6f, 3.6f, 72f)
    }

    fun grid(): RaceMesh {
        val y = RaceGeometry.deckTop.toFloat() + 0.02f
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val uvs = ArrayList<Float>()
        val indices = ArrayList<Int>()
        fun add(x0: Float, z0: Float, x1: Float, z1: Float) {
            val base = positions.size / 3
            positions.addAll(listOf(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1))
            repeat(4) {
                normals.addAll(listOf(0f, 1f, 0f))
                uvs.addAll(listOf(0f, 0f))
            }
            indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
        }
        val half = 0.05f
        var line = 8f
        while (line <= 64f) {
            add(-16f, line - half, 16f, line + half)
            line += 8f
        }
        var across = -16f
        while (across <= 16f) {
            add(across - half, 8f, across + half, 64f)
            across += 8f
        }
        return RaceMesh("edge", positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
    }

    fun axes(): List<RaceMesh> {
        val y = RaceGeometry.deckTop.toFloat() + 0.04f
        return listOf(
            quad("kerbRed", y, 0.2f, 9f, 6f, 9.3f),
            quad("canopy", 1.6f, -0.15f, 9f, 0.15f, 9.3f),
            quad("marking", y, -0.15f, 10f, 0.15f, 22f)
        )
    }

    fun drivingWorld(): List<RaceMesh> {
        return RaceGeometry.worldModel(RaceTrack.samplesFor(RaceTrack.samplePreview()))
    }

    fun markers(): List<Marker> {
        val roadY = RaceGeometry.deckTop.toFloat()
        return listOf(
            Marker("ground-near", 0f, 0f, 12f),
            Marker("ground-side", -10f, 0f, 24f),
            Marker("ground-far", 8f, 0f, 70f),
            Marker("road", 0f, roadY, 18f),
            Marker("road-edge", 3.2f, roadY, 22f),
            Marker("axis-x", 4f, 0.4f, 10f),
            Marker("axis-y", 0f, 3f, 10f),
            Marker("axis-z", 0f, 0.4f, 16f)
        )
    }

    fun inFrontOfCamera(aspect: Double): List<String> {
        val hidden = ArrayList<String>()
        for (marker in markers()) {
            if (!visible(marker.x, marker.y, marker.z, aspect)) hidden.add(marker.name)
        }
        return hidden
    }

    /**
     * The sample route's carriageway, 18 m ahead of the camera, using the
     * same lens as the driving view.
     */
    fun drivingCenterVisible(aspect: Double): Boolean {
        val route = RaceTrack.samplePreview()
        val samples = RaceTrack.samplesFor(route)
        if (samples.size < 2) return false
        val start = samples.first().along
        val here = RaceTrack.pointAt(samples, start)
        val ahead = RaceTrack.pointAt(samples, start + 18.0)
        val bearing = RaceTrack.bearing(route, samples, start)
        val pose = RaceCamera.pose(here.east, here.north, bearing, here.elevation + RaceGeometry.deckTop)
        val fov = DriveDisplay.verticalFov(aspect)
        val ndc = RaceCamera.ndc(
            pose,
            ahead.east.toFloat(),
            (ahead.elevation + RaceGeometry.deckTop).toFloat(),
            ahead.north.toFloat(),
            fov,
            aspect
        )
        return ndc[0] != 2f && abs(ndc[0]) < 1f && abs(ndc[1]) < 1f
    }

    fun visible(x: Float, y: Float, z: Float, aspect: Double): Boolean {
        val pose = RaceCamera.pose(0.0, 0.0, 0.0)
        val ndc = RaceCamera.ndc(pose, x, y, z, DriveDisplay.verticalFov(aspect), aspect)
        return ndc[0] != 2f && ndc[0] >= -1f && ndc[0] <= 1f && ndc[1] >= -1f && ndc[1] <= 1f
    }

    private fun quad(material: String, y: Float, x0: Float, z0: Float, x1: Float, z1: Float): RaceMesh {
        return RaceMesh(
            material,
            floatArrayOf(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1),
            floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            intArrayOf(0, 1, 2, 0, 2, 3)
        )
    }
}

/**
 * The bytes that used to feed the road materials. Filament 1.75 asks the
 * transcoder for ETC2 and DXT5 as compressed targets, then uncompressed RGBA.
 * A linear UASTC file therefore has no compressed format a phone GPU provides.
 * Adreno samples the unbound texture as magenta. Mali samples it as black.
 */
internal object Ktx2Facts {
    data class Header(
        val supercompression: Int,
        val transfer: Int,
        val colorModel: Int,
        val vkFormat: Int
    )

    fun inspect(bytes: ByteArray): Header? {
        if (bytes.size < 80 || bytes[0] != 0xAB.toByte() || bytes[1] != 'K'.code.toByte()) return null
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.position(12)
        val vkFormat = buffer.int
        buffer.position(12 + 8 * 4)
        val scheme = buffer.int
        val dfdOffset = buffer.int
        if (dfdOffset + 16 > bytes.size) return null
        val model = bytes[dfdOffset + 12].toInt() and 0xFF
        val transfer = bytes[dfdOffset + 14].toInt() and 0xFF
        return Header(scheme, transfer, model, vkFormat)
    }

    fun isLinearUastc(header: Header): Boolean {
        return header.supercompression == 2 && header.colorModel == 166 && header.transfer == 1
    }
}
