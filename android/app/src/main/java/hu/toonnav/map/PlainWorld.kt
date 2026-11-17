package hu.toonnav.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
 *
 * The ribbon is resampled on a short curve so a bend is an arc, not a
 * corner cut from the raw samples. Colors stay untextured.
 */
object PlainWorld {
    data class Swatch(val name: String, val red: Float, val green: Float, val blue: Float) {
        fun packed(): Int {
            fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).toInt()
            return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
        }
    }

    val ground = Swatch("plain-ground", 0.17f, 0.55f, 0.18f)
    val road = Swatch("plain-road", 0.45f, 0.45f, 0.43f)
    val shoulder = Swatch("plain-shoulder", 0.62f, 0.46f, 0.28f)
    val verge = Swatch("plain-verge", 0.36f, 0.50f, 0.24f)
    val center = Swatch("plain-center", 0.96f, 0.96f, 0.94f)
    val edge = Swatch("plain-edge", 0.93f, 0.93f, 0.90f)
    val kerbRed = Swatch("plain-kerb-red", 0.78f, 0.08f, 0.05f)
    val kerbWhite = Swatch("plain-kerb-white", 0.94f, 0.94f, 0.92f)
    /** Zenith, mid sky, and a pale horizon. Drawn as untextured rings, not an HDR image. */
    val dayGradient = listOf(
        Swatch("plain-sky-band-0", 0.74f, 0.86f, 0.93f),
        Swatch("plain-sky-band-1", 0.52f, 0.72f, 0.92f),
        Swatch("plain-sky-band-2", 0.36f, 0.60f, 0.90f)
    )
    val nightGradient = listOf(
        Swatch("plain-night-band-0", 0.12f, 0.16f, 0.26f),
        Swatch("plain-night-band-1", 0.07f, 0.10f, 0.20f),
        Swatch("plain-night-band-2", 0.05f, 0.08f, 0.16f)
    )
    val horizon = dayGradient[0]
    val sky = dayGradient[2]
    val nightSky = nightGradient[2]

    /** Steps from the grass toward the pale horizon, so the distance is not one hard stripe. */
    val haze: List<Swatch> = (0 until 7).map { step ->
        val blend = (step + 1) / 7f * 0.92f
        Swatch(
            "plain-haze-$step",
            ground.red + (horizon.red - ground.red) * blend,
            ground.green + (horizon.green - ground.green) * blend,
            ground.blue + (horizon.blue - ground.blue) * blend
        )
    }
    val farBand = haze[haze.lastIndex - 1]
    val horizonBand = haze.last()

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
    /** Depth haze in the horizon color. The near road stays clear; distant ground fades. */
    const val fogDistance = 150f
    const val fogDensity = 0.0032f
    const val fogMaximumOpacity = 0.70f

    const val roadHalf = 5.0
    const val shoulderWidth = 3.2
    const val grassWidth = 28.0
    const val hazeBandWidth = 36.0

    fun swatch(name: String): Swatch? {
        return all.firstOrNull { it.name == name }
    }

    val all = listOf(ground, road, shoulder, verge, center, edge, kerbRed, kerbWhite, sky, nightSky) + haze

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
        val window = samples.filter { it.along >= from - 40.0 && it.along <= until + 40.0 }
        val curve = refine(if (window.size >= 2) window else samples)
        if (curve.size < 2) return emptyList()
        val groundStrip = Strip(ground.name)
        val vergeStrip = Strip(verge.name)
        val hazeStrips = haze.map { Strip(it.name) }
        val shoulderStrip = Strip(shoulder.name)
        val roadStrip = Strip(road.name)
        val edgeStrip = Strip(edge.name)
        val centerStrip = Strip(center.name)
        val redStrip = Strip(kerbRed.name)
        val whiteStrip = Strip(kerbWhite.name)
        val outerShoulder = roadHalf + shoulderWidth
        val outerGrass = outerShoulder + grassWidth
        val grassEdges = listOf(outerShoulder, outerShoulder + grassWidth * 0.34, outerShoulder + grassWidth * 0.67, outerGrass)
        for (index in 0 until curve.size - 1) {
            val a = curve[index]
            val b = curve[index + 1]
            if (b.along < from || a.along > until) continue
            val rightA = right(curve, index)
            val rightB = right(curve, index + 1)
            band(shoulderStrip, a, b, rightA, rightB, roadHalf, outerShoulder, 0.015)
            band(roadStrip, a, b, rightA, rightB, -roadHalf, roadHalf, 0.06)
            band(edgeStrip, a, b, rightA, rightB, roadHalf - 0.55, roadHalf - 0.22, 0.09)
            if ((a.along / 8.0).toInt() % 2 == 0) {
                band(centerStrip, a, b, rightA, rightB, -0.14, 0.14, 0.11)
            }
            if (abs(a.turn) > 0.01 || abs(b.turn) > 0.01) {
                val kerb = if ((a.along / 1.7).toInt() % 2 == 0) redStrip else whiteStrip
                band(kerb, a, b, rightA, rightB, roadHalf - 0.08, roadHalf + 0.62, 0.10)
            }
        }
        val stride = 4
        var terrain = 0
        while (terrain < curve.size - 1) {
            val next = (terrain + stride).coerceAtMost(curve.lastIndex)
            if (next == terrain) break
            val a = curve[terrain]
            val b = curve[next]
            if (b.along >= from && a.along <= until) {
                val rightA = right(curve, terrain)
                val rightB = right(curve, next)
                for (band in 0 until grassEdges.size - 1) {
                    band(groundStrip, a, b, rightA, rightB, grassEdges[band], grassEdges[band + 1], 0.0)
                }
                var lateral = outerGrass
                for (strip in hazeStrips) {
                    val outer = lateral + hazeBandWidth
                    band(strip, a, b, rightA, rightB, lateral, outer, 0.0)
                    lateral = outer
                }
                band(vergeStrip, a, b, rightA, rightB, outerShoulder - 0.15, outerShoulder + 2.4, 0.0)
            }
            terrain += stride
        }
        return (listOf(groundStrip, vergeStrip, shoulderStrip, roadStrip, edgeStrip, centerStrip, redStrip, whiteStrip) + hazeStrips)
            .mapNotNull { it.build() }
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

    private fun refine(samples: List<RoadSample>): List<RoadSample> {
        if (samples.size < 2) return samples
        val curved = ArrayList<RoadSample>()
        for (index in 0 until samples.size - 1) {
            val p0 = samples[(index - 1).coerceAtLeast(0)]
            val p1 = samples[index]
            val p2 = samples[index + 1]
            val p3 = samples[(index + 2).coerceAtMost(samples.lastIndex)]
            val span = hypot(p2.east - p1.east, p2.north - p1.north).coerceAtLeast(0.001)
            val corner = corner(p0, p1, p2)
            val tight = corner > 0.12
            val steps = (span / if (tight) 0.65 else 1.7).toInt().coerceIn(1, if (tight) 18 else 5)
            for (step in 0 until steps) {
                val t = step.toDouble() / steps
                curved.add(
                    RoadSample(
                        catmull(p0.east, p1.east, p2.east, p3.east, t),
                        catmull(p0.north, p1.north, p2.north, p3.north, t),
                        p1.along + (p2.along - p1.along) * t,
                        0.0,
                        p1.kind,
                        catmull(p0.elevation.toDouble(), p1.elevation.toDouble(), p2.elevation.toDouble(), p3.elevation.toDouble(), t).toFloat()
                    )
                )
            }
        }
        curved.add(samples.last())
        for (index in 1 until curved.size - 1) {
            val before = curved[index - 1]
            val current = curved[index]
            val after = curved[index + 1]
            val inX = current.east - before.east
            val inZ = current.north - before.north
            val outX = after.east - current.east
            val outZ = after.north - current.north
            val inLength = hypot(inX, inZ).coerceAtLeast(0.001)
            val outLength = hypot(outX, outZ).coerceAtLeast(0.001)
            val turn = (inX / inLength) * (outZ / outLength) - (inZ / inLength) * (outX / outLength)
            curved[index] = current.copy(turn = turn)
        }
        return curved
    }

    private fun corner(before: RoadSample, at: RoadSample, after: RoadSample): Double {
        val inX = at.east - before.east
        val inZ = at.north - before.north
        val outX = after.east - at.east
        val outZ = after.north - at.north
        val inLength = hypot(inX, inZ).coerceAtLeast(0.001)
        val outLength = hypot(outX, outZ).coerceAtLeast(0.001)
        return abs((inX / inLength) * (outZ / outLength) - (inZ / inLength) * (outX / outLength))
    }

    private fun catmull(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * (
            (2.0 * p1) +
                (-p0 + p2) * t +
                (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
                (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
            )
    }

    /** Flat on the asphalt, then a low roll that fades out before the horizon. */
    private fun relief(east: Double, north: Double, lateral: Double): Double {
        val fade = ((abs(lateral) - roadHalf - 0.35) / 12.0).coerceIn(0.0, 1.0)
        val smooth = fade * fade * (3.0 - 2.0 * fade)
        val outer = roadHalf + shoulderWidth + grassWidth
        val settle = (1.0 - ((abs(lateral) - outer) / 140.0).coerceIn(0.0, 1.0))
        val roll = sin(east * 0.021) * cos(north * 0.017) * 0.38
        val broad = sin(east * 0.006 + north * 0.005) * 0.72
        return (roll + broad) * smooth * settle
    }

    /** Low ridges beyond the verge. Flat on the asphalt and gone again at the far haze. */
    private fun hill(east: Double, north: Double, lateral: Double): Double {
        val inner = roadHalf + shoulderWidth + grassWidth * 0.45
        val outer = roadHalf + shoulderWidth + grassWidth + hazeBandWidth * 4.0
        val distance = abs(lateral)
        if (distance <= inner || distance >= outer) return 0.0
        val span = (outer - inner).coerceAtLeast(1.0)
        val t = ((distance - inner) / span).coerceIn(0.0, 1.0)
        val dome = sin(t * Math.PI)
        val vary = 0.42 + 0.58 * (0.5 + 0.5 * sin(east * 0.018 + north * 0.013))
        return dome * vary * 16.0
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
            val east = sample.east + right.first * lateral
            val north = sample.north + right.second * lateral
            positions.add(east.toFloat())
            positions.add((sample.elevation + lift + relief(east, north, lateral) + hill(east, north, lateral)).toFloat())
            positions.add(north.toFloat())
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
