package hu.toonnav.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
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
 * The road ribbon follows the route elevation. Terrain is a height grid in the
 * same local meters: inside the carriageway it stays under the asphalt, and
 * beyond the shoulder it uses the surveyed field when one exists. A missing
 * field stays a low roll. It does not grow a hill beside the road, because a
 * later bend used to drive through those hills.
 */
object PlainWorld {
    data class Swatch(val name: String, val red: Float, val green: Float, val blue: Float) {
        fun packed(): Int {
            fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).toInt()
            return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
        }
    }

    val ground = Swatch("plain-ground", 0.16f, 0.46f, 0.15f)
    val grassDeep = Swatch("plain-grass-deep", 0.10f, 0.34f, 0.11f)
    val grassLight = Swatch("plain-grass-light", 0.38f, 0.58f, 0.24f)
    val earth = Swatch("plain-earth", 0.47f, 0.36f, 0.22f)
    val road = Swatch("plain-road", 0.36f, 0.36f, 0.35f)
    val roadShade = Swatch("plain-road-shade", 0.31f, 0.31f, 0.30f)
    val shoulder = Swatch("plain-shoulder", 0.55f, 0.42f, 0.27f)
    val verge = Swatch("plain-verge", 0.34f, 0.46f, 0.22f)
    val center = Swatch("plain-center", 0.94f, 0.94f, 0.92f)
    val edge = Swatch("plain-edge", 0.90f, 0.90f, 0.88f)
    val kerbRed = Swatch("plain-kerb-red", 0.78f, 0.08f, 0.05f)
    val kerbWhite = Swatch("plain-kerb-white", 0.94f, 0.94f, 0.92f)
    /** Horizon first, zenith last. Drawn as untextured rings, not an HDR image. */
    val dayGradient = listOf(
        Swatch("plain-sky-band-0", 0.78f, 0.88f, 0.95f),
        Swatch("plain-sky-band-1", 0.62f, 0.78f, 0.94f),
        Swatch("plain-sky-band-2", 0.46f, 0.66f, 0.92f),
        Swatch("plain-sky-band-3", 0.32f, 0.56f, 0.88f),
        Swatch("plain-sky-band-4", 0.22f, 0.46f, 0.84f)
    )
    val nightGradient = listOf(
        Swatch("plain-night-band-0", 0.16f, 0.20f, 0.30f),
        Swatch("plain-night-band-1", 0.12f, 0.15f, 0.24f),
        Swatch("plain-night-band-2", 0.08f, 0.11f, 0.20f),
        Swatch("plain-night-band-3", 0.06f, 0.08f, 0.16f),
        Swatch("plain-night-band-4", 0.04f, 0.05f, 0.12f)
    )
    val horizon = dayGradient.first()
    val sky = dayGradient.last()
    val nightSky = nightGradient.last()

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
    private const val nearStep = 3.2
    private const val nearReach = 42.0
    private const val farStep = 18.0
    private const val farReach = 200.0

    fun swatch(name: String): Swatch? {
        return all.firstOrNull { it.name == name }
    }

    val all = listOf(
        ground, grassDeep, grassLight, earth, road, roadShade, shoulder, verge, center, edge, kerbRed, kerbWhite, sky, nightSky
    ) + haze

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

    fun meshes(
        samples: List<RoadSample>,
        from: Double = Double.NEGATIVE_INFINITY,
        until: Double = Double.POSITIVE_INFINITY,
        field: ((Double, Double) -> Float)? = null
    ): List<RaceMesh> {
        if (samples.size < 2) return emptyList()
        val window = samples.filter { it.along >= from - 40.0 && it.along <= until + 40.0 }
        val curve = refine(if (window.size >= 2) window else samples)
        if (curve.size < 2) return emptyList()
        val strips = linkedMapOf(
            shoulder.name to Strip(shoulder.name),
            road.name to Strip(road.name),
            roadShade.name to Strip(roadShade.name),
            edge.name to Strip(edge.name),
            center.name to Strip(center.name),
            kerbRed.name to Strip(kerbRed.name),
            kerbWhite.name to Strip(kerbWhite.name)
        )
        for (index in 0 until curve.size - 1) {
            val a = curve[index]
            val b = curve[index + 1]
            if (b.along < from || a.along > until) continue
            val rightA = right(curve, index)
            val rightB = right(curve, index + 1)
            val profileA = RoadProfile.of(a.kind)
            val profileB = RoadProfile.of(b.kind)
            val halfA = profileA.halfWidth
            val halfB = profileB.halfWidth
            band(strips.getValue(shoulder.name), a, b, rightA, rightB, halfA, halfA + profileA.shoulder, halfB, halfB + profileB.shoulder, 0.02)
            band(strips.getValue(shoulder.name), a, b, rightA, rightB, -(halfA + profileA.shoulder), -halfA, -(halfB + profileB.shoulder), -halfB, 0.02)
            val asphalt = if ((a.along / 18.0).toInt() % 5 == 0) roadShade.name else road.name
            band(strips.getValue(asphalt), a, b, rightA, rightB, -halfA, halfA, -halfB, halfB, 0.06)
            if (profileA.edgeLines || profileB.edgeLines) {
                band(strips.getValue(edge.name), a, b, rightA, rightB, halfA - 0.48, halfA - 0.18, halfB - 0.48, halfB - 0.18, 0.09)
                band(strips.getValue(edge.name), a, b, rightA, rightB, -(halfA - 0.18), -(halfA - 0.48), -(halfB - 0.18), -(halfB - 0.48), 0.09)
            }
            if (profileA.center == CenterMark.DASH && (a.along / 8.0).toInt() % 2 == 0) {
                band(strips.getValue(center.name), a, b, rightA, rightB, -0.13, 0.13, -0.13, 0.13, 0.11)
            }
            if ((profileA.kerbs || profileB.kerbs) && (abs(a.turn) > 0.02 || abs(b.turn) > 0.02)) {
                val kerb = if ((a.along / 1.6).toInt() % 2 == 0) kerbRed.name else kerbWhite.name
                band(strips.getValue(kerb), a, b, rightA, rightB, halfA - 0.04, halfA + 0.58, halfB - 0.04, halfB + 0.58, 0.10)
                band(strips.getValue(kerb), a, b, rightA, rightB, -(halfA + 0.58), -(halfA - 0.04), -(halfB + 0.58), -(halfB - 0.04), 0.10)
            }
        }
        val terrain = terrainStrips(curve, from, until, field)
        return (strips.values + terrain.values).mapNotNull { it.build() }
    }

    fun chunks(
        samples: List<RoadSample>,
        needed: IntRange,
        field: ((Double, Double) -> Float)? = null
    ): Map<Int, List<RaceMesh>> {
        val built = LinkedHashMap<Int, List<RaceMesh>>()
        for (index in needed) {
            val from = index * RaceGeometry.chunkMeters
            val until = (index + 1) * RaceGeometry.chunkMeters
            val piece = meshes(samples, from, until, field)
            if (piece.isNotEmpty()) built[index] = piece
        }
        return built
    }

    /** Height of the ground at a local point. The carriageway stays at the road elevation. */
    fun groundHeight(
        anchors: List<RoadSample>,
        east: Double,
        north: Double,
        field: ((Double, Double) -> Float)? = null
    ): Float {
        val anchor = nearest(anchors, east, north) ?: return 0f
        return heightAt(east, north, anchor, field).toFloat()
    }

    private fun terrainStrips(
        curve: List<RoadSample>,
        from: Double,
        until: Double,
        field: ((Double, Double) -> Float)?
    ): Map<String, Strip> {
        val strips = LinkedHashMap<String, Strip>()
        fillGrid(strips, curve, nearStep, nearReach, 0.0, from, until, field)
        fillGrid(strips, curve, farStep, farReach, nearReach, from, until, field)
        return strips
    }

    private fun fillGrid(
        strips: MutableMap<String, Strip>,
        anchors: List<RoadSample>,
        step: Double,
        reach: Double,
        inner: Double,
        from: Double,
        until: Double,
        field: ((Double, Double) -> Float)?
    ) {
        if (anchors.size < 2) return
        var minE = Double.POSITIVE_INFINITY
        var maxE = Double.NEGATIVE_INFINITY
        var minN = Double.POSITIVE_INFINITY
        var maxN = Double.NEGATIVE_INFINITY
        for (sample in anchors) {
            if (sample.east < minE) minE = sample.east
            if (sample.east > maxE) maxE = sample.east
            if (sample.north < minN) minN = sample.north
            if (sample.north > maxN) maxN = sample.north
        }
        minE -= reach
        maxE += reach
        minN -= reach
        maxN += reach
        var used = step
        while ((maxE - minE) / used > 320.0 || (maxN - minN) / used > 320.0) used *= 1.5
        val x0 = floor(minE / used) * used
        val z0 = floor(minN / used) * used
        val end = anchors.last().along
        var x = x0
        while (x < maxE - used * 0.25) {
            var z = z0
            while (z < maxN - used * 0.25) {
                val centerEast = x + used * 0.5
                val centerNorth = z + used * 0.5
                val anchor = nearest(anchors, centerEast, centerNorth)
                if (anchor != null && anchor.distance in inner..reach && owns(anchor.along, from, until, end)) {
                    val name = materialFor(anchor.distance, centerEast, centerNorth)
                    val strip = strips.getOrPut(name) { Strip(name) }
                    val y00 = heightAt(x, z, nearest(anchors, x, z) ?: anchor, field)
                    val y10 = heightAt(x + used, z, nearest(anchors, x + used, z) ?: anchor, field)
                    val y11 = heightAt(x + used, z + used, nearest(anchors, x + used, z + used) ?: anchor, field)
                    val y01 = heightAt(x, z + used, nearest(anchors, x, z + used) ?: anchor, field)
                    val a = strip.addPoint(x, z, y00)
                    val b = strip.addPoint(x + used, z, y10)
                    val c = strip.addPoint(x + used, z + used, y11)
                    val d = strip.addPoint(x, z + used, y01)
                    strip.quad(a, b, c, d)
                }
                z += used
            }
            x += used
        }
    }

    private fun owns(along: Double, from: Double, until: Double, end: Double): Boolean {
        if (along < from) return false
        if (along < until) return true
        return along <= end + 0.05 && until >= end - 0.05
    }

    private fun materialFor(distance: Double, east: Double, north: Double): String {
        if (distance > 72.0) {
            val band = ((distance - 72.0) / 18.0).toInt().coerceIn(0, haze.lastIndex)
            return haze[band].name
        }
        if (distance < 8.0) return shoulder.name
        if (distance < 12.0) return verge.name
        val patch = sin(east * 0.021) * cos(north * 0.017)
        return when {
            patch < -0.55 -> earth.name
            patch > 0.62 -> grassLight.name
            patch < -0.2 -> grassDeep.name
            else -> ground.name
        }
    }

    private fun heightAt(
        east: Double,
        north: Double,
        anchor: Anchor,
        field: ((Double, Double) -> Float)?
    ): Double {
        val road = anchor.elevation.toDouble()
        val edge = anchor.half + 0.35
        val shoulder = anchor.half + anchor.shoulder
        if (anchor.distance <= edge) return road - 0.08
        if (anchor.distance <= shoulder) {
            val t = ((anchor.distance - edge) / (shoulder - edge).coerceAtLeast(0.4)).coerceIn(0.0, 1.0)
            return road - 0.04 - 0.06 * t
        }
        val surveyed = if (field != null) {
            field(east, north).toDouble()
        } else {
            road + roll(east, north, anchor.distance, shoulder)
        }
        val span = 16.0
        val t = ((anchor.distance - shoulder) / span).coerceIn(0.0, 1.0)
        val smooth = t * t * (3.0 - 2.0 * t)
        val lip = road - 0.12
        return lip * (1.0 - smooth) + surveyed * smooth
    }

    /** A few meters of roll, and only past the shoulder. Zero means the road stays clear. */
    private fun roll(east: Double, north: Double, distance: Double, shoulder: Double): Double {
        val t = ((distance - shoulder) / 24.0).coerceIn(0.0, 1.0)
        val smooth = t * t * (3.0 - 2.0 * t)
        val wave = sin(east * 0.018) * cos(north * 0.015) * 1.05 + sin(east * 0.006 + north * 0.005) * 1.25
        return wave * smooth
    }

    private data class Anchor(
        val distance: Double,
        val elevation: Float,
        val along: Double,
        val half: Double,
        val shoulder: Double
    )

    private fun nearest(anchors: List<RoadSample>, east: Double, north: Double): Anchor? {
        var best = Double.POSITIVE_INFINITY
        var found: RoadSample? = null
        for (sample in anchors) {
            val distance = hypot(sample.east - east, sample.north - north)
            if (distance < best) {
                best = distance
                found = sample
            }
        }
        val sample = found ?: return null
        val profile = RoadProfile.of(sample.kind)
        return Anchor(best, sample.elevation, sample.along, profile.halfWidth, profile.shoulder)
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
                val elevation = p1.elevation + (p2.elevation - p1.elevation) * t
                curved.add(
                    RoadSample(
                        catmull(p0.east, p1.east, p2.east, p3.east, t),
                        catmull(p0.north, p1.north, p2.north, p3.north, t),
                        p1.along + (p2.along - p1.along) * t,
                        0.0,
                        p1.kind,
                        elevation.toFloat()
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

    private fun band(
        strip: Strip,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        fromA: Double,
        toA: Double,
        fromB: Double,
        toB: Double,
        lift: Double
    ) {
        val span = hypot(b.east - a.east, b.north - a.north)
        if (span < 0.05) return
        val a0 = strip.add(a, rightA, fromA, lift)
        val a1 = strip.add(a, rightA, toA, lift)
        val b1 = strip.add(b, rightB, toB, lift)
        val b0 = strip.add(b, rightB, fromB, lift)
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
            return addPoint(sample.east + right.first * lateral, sample.north + right.second * lateral, sample.elevation + lift)
        }

        fun addPoint(east: Double, north: Double, y: Double): Int {
            val index = positions.size / 3
            positions.add(east.toFloat())
            positions.add(y.toFloat())
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
