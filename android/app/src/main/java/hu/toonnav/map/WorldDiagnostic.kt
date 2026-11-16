package hu.toonnav.map

/**
 * The diagnostic scene, without a button on the driving screen.
 *
 * A phone that can draw the untextured edge lines and still shows no asphalt
 * or grass is not a camera or a geometry failure. Those two materials used to
 * sample an occlusion texture. This scene is the control: a solid sky, a
 * green ground, a grey road, and three axes, all in front of the same lens
 * the driving view uses. [RacePrototypeActivity] does not install it.
 */
object WorldDiagnostic {
    data class Marker(val name: String, val x: Float, val y: Float, val z: Float)

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
        val pose = RaceCamera.pose(0.0, 0.0, 0.0)
        val fov = DriveDisplay.verticalFov(aspect)
        val hidden = ArrayList<String>()
        for (marker in markers()) {
            val ndc = RaceCamera.ndc(pose, marker.x, marker.y, marker.z, fov, aspect)
            val behind = ndc[0] == 2f
            val outside = ndc[0] < -1f || ndc[0] > 1f || ndc[1] < -1f || ndc[1] > 1f
            if (behind || outside) hidden.add(marker.name)
        }
        return hidden
    }
}

/**
 * The bytes that used to feed the road materials. Filament 1.75 asks the
 * transcoder for ETC2 and DXT5 as compressed targets, then uncompressed RGBA.
 * Linear UASTC therefore has no compressed format a phone GPU can take.
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
