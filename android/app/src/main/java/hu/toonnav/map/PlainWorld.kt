package hu.toonnav.map

import kotlin.math.hypot

/**
 * The previous backdrop is still in the project and comes back if [enabled]
 * is set to true.
 *
 * That path is the one the phone could not show:
 * - sky and background: [RaceLighting] plus models/noon-sky.hdr
 * - grass and terrain: [RaceGeometry.worldModel] and [RaceGeometry.horizon]
 * - shoulder: the gravel ribbon in that same model
 * - distant surroundings: [Scenery] and the horizon disc
 * - image lighting and fog: the HDR probe, [RaceFog], and the sunny-16 exposure
 *
 * None of those are consulted while this flag is false.
 */
object LegacyBackdrop {
    const val enabled = false
}

/**
 * Solid driving backdrop. Every color is a material parameter. There is no
 * texture, HDR, IBL image, KTX2, or ORM map.
 */
object PlainWorld {
    data class Swatch(val name: String, val red: Float, val green: Float, val blue: Float) {
        fun packed(): Int {
            fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).toInt()
            return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
        }
    }

    val ground = Swatch("plain-ground", 0.15f, 0.62f, 0.16f)
    val road = Swatch("plain-road", 0.45f, 0.45f, 0.43f)
    val shoulder = Swatch("plain-shoulder", 0.72f, 0.46f, 0.24f)
    val farBand = Swatch("plain-far", 0.18f, 0.42f, 0.72f)
    val horizonBand = Swatch("plain-horizon", 0.16f, 0.34f, 0.30f)
    val center = Swatch("plain-center", 0.96f, 0.96f, 0.94f)
    val edge = Swatch("plain-edge", 0.93f, 0.93f, 0.90f)
    val sky = Swatch("plain-sky", 0.55f, 0.78f, 0.96f)
    val nightSky = Swatch("plain-night", 0.06f, 0.09f, 0.18f)

    /** Exposure near ev 0, so these 0–1 colors stay visible. Not sunny-16. */
    const val aperture = 1f
    const val shutter = 1f
    const val sensitivity = 100f
    /**
     * The driving camera used to be sunny-16, with a 100000 lux sun. This
     * exposure is about 32000 times more sensitive, so the sun is scaled down
     * by the same amount and the unlit colors stay in the 0–1 range.
     */
    const val sunLux = 3.2f

    const val roadHalf = 5.0
    const val shoulderWidth = 3.2
    const val grassWidth = 42.0
    const val farWidth = 70.0
    const val horizonWidth = 240.0

    fun swatch(name: String): Swatch? {
        return all.firstOrNull { it.name == name }
    }

    val all = listOf(ground, road, shoulder, farBand, horizonBand, center, edge, sky, nightSky)

    fun previewSamples(length: Double = 800.0, step: Double = 8.0): List<RoadSample> {
        if (length <= 1.0 || step <= 0.0) return emptyList()
        val samples = ArrayList<RoadSample>()
        var cursor = 0.0
        while (cursor <= length + 0.01) {
            val along = cursor.coerceAtMost(length)
            samples.add(RoadSample(0.0, along, along, 0.0, RoadKind.MAIN, 0f))
            cursor += step
        }
        return samples
    }

    fun meshes(samples: List<RoadSample>, from: Double = Double.NEGATIVE_INFINITY, until: Double = Double.POSITIVE_INFINITY): List<RaceMesh> {
        if (samples.size < 2) return emptyList()
        val groundStrip = Strip(ground.name)
        val farStrip = Strip(farBand.name)
        val horizonStrip = Strip(horizonBand.name)
        val shoulderStrip = Strip(shoulder.name)
        val roadStrip = Strip(road.name)
        val edgeStrip = Strip(edge.name)
        val centerStrip = Strip(center.name)
        val outerShoulder = roadHalf + shoulderWidth
        val outerGrass = outerShoulder + grassWidth
        val outerFar = outerGrass + farWidth
        val outerHorizon = outerFar + horizonWidth
        for (index in 0 until samples.size - 1) {
            val a = samples[index]
            val b = samples[index + 1]
            if (b.along < from || a.along > until) continue
            val rightA = right(samples, index)
            val rightB = right(samples, index + 1)
            band(groundStrip, a, b, rightA, rightB, outerShoulder, outerGrass, 0.0)
            band(farStrip, a, b, rightA, rightB, outerGrass, outerFar, 0.0)
            band(horizonStrip, a, b, rightA, rightB, outerFar, outerHorizon, 0.0)
            band(shoulderStrip, a, b, rightA, rightB, roadHalf, outerShoulder, 0.03)
            band(roadStrip, a, b, rightA, rightB, -roadHalf, roadHalf, 0.06)
            band(edgeStrip, a, b, rightA, rightB, roadHalf - 0.55, roadHalf - 0.22, 0.09)
            if ((a.along / 8.0).toInt() % 2 == 0) {
                band(centerStrip, a, b, rightA, rightB, -0.16, 0.16, 0.11)
            }
        }
        return listOf(groundStrip, farStrip, horizonStrip, shoulderStrip, roadStrip, edgeStrip, centerStrip).mapNotNull { it.build() }
    }

    fun chunks(samples: List<RoadSample>, needed: IntRange): Map<Int, List<RaceMesh>> {
        val built = LinkedHashMap<Int, List<RaceMesh>>()
        for (index in needed) {
            val from = index * RaceGeometry.chunkMeters
            val until = (index + 1) * RaceGeometry.chunkMeters
            val piece = meshes(samples, from, until)
            if (piece.isNotEmpty()) built[index] = piece
        }
        return built
    }

    private fun band(
        strip: Strip,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        from: Double,
        to: Double,
        lift: Double
    ) {
        val span = hypot(b.east - a.east, b.north - a.north)
        if (span < 0.05) return
        // Both sides. A negative range is already a side; a positive range is mirrored.
        if (from >= 0.0 && to > from) {
            quad(strip, a, b, rightA, rightB, from, to, lift)
            quad(strip, a, b, rightA, rightB, -to, -from, lift)
        } else {
            quad(strip, a, b, rightA, rightB, from, to, lift)
        }
    }

    private fun quad(
        strip: Strip,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        from: Double,
        to: Double,
        lift: Double
    ) {
        val a0 = strip.add(a, rightA, from, lift)
        val a1 = strip.add(a, rightA, to, lift)
        val b1 = strip.add(b, rightB, to, lift)
        val b0 = strip.add(b, rightB, from, lift)
        strip.quad(a0, a1, b1, b0)
    }

    private fun right(samples: List<RoadSample>, index: Int): Pair<Double, Double> {
        val previous = samples[(index - 1).coerceAtLeast(0)]
        val next = samples[(index + 1).coerceAtMost(samples.lastIndex)]
        val east = next.east - previous.east
        val north = next.north - previous.north
        val length = hypot(east, north).coerceAtLeast(0.001)
        return (north / length) to (-east / length)
    }

    private class Strip(val name: String) {
        private val positions = ArrayList<Float>()
        private val normals = ArrayList<Float>()
        private val uvs = ArrayList<Float>()
        private val indices = ArrayList<Int>()

        fun add(sample: RoadSample, right: Pair<Double, Double>, lateral: Double, lift: Double): Int {
            val index = positions.size / 3
            positions.add((sample.east + right.first * lateral).toFloat())
            positions.add((sample.elevation + lift).toFloat())
            positions.add((sample.north + right.second * lateral).toFloat())
            normals.add(0f)
            normals.add(1f)
            normals.add(0f)
            uvs.add(0f)
            uvs.add(0f)
            return index
        }

        fun quad(a: Int, b: Int, c: Int, d: Int) {
            indices.add(a)
            indices.add(b)
            indices.add(c)
            indices.add(a)
            indices.add(c)
            indices.add(d)
        }

        fun build(): RaceMesh? {
            if (indices.size < 3) return null
            return RaceMesh(name, positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
        }
    }
}
