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
    val roadWarm = Swatch("plain-road-warm", 0.40f, 0.37f, 0.33f)
    val roadCool = Swatch("plain-road-cool", 0.33f, 0.35f, 0.37f)
    val roadSpeck = Swatch("plain-road-speck", 0.30f, 0.30f, 0.29f)
    val shoulder = Swatch("plain-shoulder", 0.55f, 0.42f, 0.27f)
    val shoulderDark = Swatch("plain-shoulder-dark", 0.42f, 0.32f, 0.20f)
    val shoulderLight = Swatch("plain-shoulder-light", 0.68f, 0.54f, 0.38f)
    val rock = Swatch("plain-rock", 0.48f, 0.46f, 0.42f)
    val trunk = Swatch("plain-trunk", 0.36f, 0.24f, 0.14f)
    val canopy = Swatch("plain-canopy", 0.12f, 0.34f, 0.12f)
    val canopyLight = Swatch("plain-canopy-light", 0.28f, 0.50f, 0.18f)
    val bush = Swatch("plain-bush", 0.22f, 0.42f, 0.16f)
    val verge = Swatch("plain-verge", 0.40f, 0.50f, 0.24f)
    /** Soft patch under the car. Unlit, so a failed shadow map cannot black the world. */
    val contact = Swatch("plain-contact", 0.07f, 0.08f, 0.07f)
    val center = Swatch("plain-center", 0.94f, 0.94f, 0.92f)
    val edge = Swatch("plain-edge", 0.90f, 0.90f, 0.88f)
    val kerbRed = Swatch("plain-kerb-red", 0.78f, 0.08f, 0.05f)
    val kerbWhite = Swatch("plain-kerb-white", 0.94f, 0.94f, 0.92f)
    /** Horizon first, zenith last. Drawn as untextured rings, not an HDR image. */
    val dayGradient = listOf(
        Swatch("plain-sky-band-0", 0.80f, 0.88f, 0.95f),
        Swatch("plain-sky-band-1", 0.90f, 0.94f, 0.97f),
        Swatch("plain-sky-band-2", 0.50f, 0.72f, 0.94f),
        Swatch("plain-sky-band-3", 0.28f, 0.54f, 0.90f),
        Swatch("plain-sky-band-4", 0.16f, 0.40f, 0.82f)
    )
    val cloud = Swatch("plain-sky-cloud", 0.95f, 0.96f, 0.98f)
    val nightCloud = Swatch("plain-night-cloud", 0.16f, 0.20f, 0.30f)
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
    /**
     * Fill for the lit car only. The unlit road ignores it. The chase camera
     * looks at the rear face, which the overhead sun barely reaches, so the
     * fill has to be strong enough for a silver base color to stay silver.
     */
    const val carFillIntensity = 5.5f
    const val carFillNight = 0.45f
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
    private const val farReach = 260.0

    fun swatch(name: String): Swatch? {
        return all.firstOrNull { it.name == name }
    }

    val asphalt = listOf(road, roadShade, roadWarm, roadCool, roadSpeck)
    val gravel = listOf(shoulder, shoulderDark, shoulderLight)

    fun isAsphalt(name: String): Boolean = asphalt.any { it.name == name }

    fun isGravel(name: String): Boolean = gravel.any { it.name == name }

    /** Surfaces that may show the runtime grain. Markings stay a flat color. */
    fun takesDetail(name: String): Boolean {
        return isAsphalt(name) || isGravel(name) || name == ground.name || name == grassDeep.name ||
            name == grassLight.name || name == earth.name || name == rock.name || name == verge.name ||
            name == trunk.name || name == canopy.name || name == canopyLight.name || name == bush.name
    }

    fun isPlant(name: String): Boolean {
        return name == trunk.name || name == canopy.name || name == canopyLight.name || name == bush.name
    }

    val all = listOf(
        ground, grassDeep, grassLight, earth, rock,
        road, roadShade, roadWarm, roadCool, roadSpeck,
        shoulder, shoulderDark, shoulderLight, verge,
        trunk, canopy, canopyLight, bush, contact, cloud,
        center, edge, kerbRed, kerbWhite, sky, nightSky
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
            edge.name to Strip(edge.name),
            center.name to Strip(center.name),
            kerbRed.name to Strip(kerbRed.name),
            kerbWhite.name to Strip(kerbWhite.name)
        )
        for (swatch in gravel + asphalt) strips.getOrPut(swatch.name) { Strip(swatch.name) }
        val sharp = BooleanArray(curve.size) { realCorner(curve, it) }
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
            val lip = 0.42
            band(strips.getValue(shoulderDark.name), a, b, rightA, rightB, halfA, halfA + lip, halfB, halfB + lip, 0.025)
            band(strips.getValue(shoulder.name), a, b, rightA, rightB, halfA + lip, halfA + profileA.shoulder, halfB + lip, halfB + profileB.shoulder, 0.02)
            band(strips.getValue(shoulderDark.name), a, b, rightA, rightB, -(halfA + lip), -halfA, -(halfB + lip), -halfB, 0.025)
            band(strips.getValue(shoulder.name), a, b, rightA, rightB, -(halfA + profileA.shoulder), -(halfA + lip), -(halfB + profileB.shoulder), -(halfB + lip), 0.02)
            val asphaltName = asphaltName(a.along, a.east, a.north)
            band(strips.getValue(asphaltName), a, b, rightA, rightB, -halfA, halfA, -halfB, halfB, 0.06)
            if (profileA.edgeLines || profileB.edgeLines) {
                band(strips.getValue(edge.name), a, b, rightA, rightB, halfA - 0.48, halfA - 0.18, halfB - 0.48, halfB - 0.18, 0.09)
                band(strips.getValue(edge.name), a, b, rightA, rightB, -(halfA - 0.18), -(halfA - 0.48), -(halfB - 0.18), -(halfB - 0.48), 0.09)
            }
            if (profileA.center == CenterMark.DASH && (a.along / 8.0).toInt() % 2 == 0) {
                band(strips.getValue(center.name), a, b, rightA, rightB, -0.13, 0.13, -0.13, 0.13, 0.11)
            }
            if ((profileA.kerbs || profileB.kerbs) && (sharp[index] || sharp[index + 1])) {
                val kerb = if ((a.along / 1.6).toInt() % 2 == 0) kerbRed.name else kerbWhite.name
                band(strips.getValue(kerb), a, b, rightA, rightB, halfA - 0.04, halfA + 0.58, halfB - 0.04, halfB + 0.58, 0.10)
                band(strips.getValue(kerb), a, b, rightA, rightB, -(halfA + 0.58), -(halfA - 0.04), -(halfB + 0.58), -(halfB - 0.04), 0.10)
            }
        }
        val terrain = terrainStrips(curve, from, until, field)
        val plants = sceneryStrips(curve, from, until)
        return (strips.values + terrain.values + plants.values).mapNotNull { it.build() }
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
                    val y00 = heightAt(x, z, nearest(anchors, x, z) ?: anchor, field)
                    val y10 = heightAt(x + used, z, nearest(anchors, x + used, z) ?: anchor, field)
                    val y11 = heightAt(x + used, z + used, nearest(anchors, x + used, z + used) ?: anchor, field)
                    val y01 = heightAt(x, z + used, nearest(anchors, x, z + used) ?: anchor, field)
                    val rise = (y00 + y10 + y11 + y01) / 4.0 - anchor.elevation
                    val name = materialFor(anchor.distance, centerEast, centerNorth, rise)
                    val strip = strips.getOrPut(name) { Strip(name) }
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

    private fun materialFor(distance: Double, east: Double, north: Double, rise: Double = 0.0): String {
        if (distance > 110.0) {
            val band = ((distance - 110.0) / 22.0).toInt().coerceIn(0, haze.lastIndex)
            return haze[band].name
        }
        if (rise > 8.0) return rock.name
        if (rise > 2.4) return earth.name
        if (distance < 7.4) return shoulder.name
        if (distance < 16.0) return verge.name
        val patch = sin(east * 0.011) * cos(north * 0.008)
        return when {
            patch < -0.45 -> earth.name
            patch > 0.5 -> grassLight.name
            patch < -0.12 -> grassDeep.name
            else -> ground.name
        }
    }

    /** Stable asphalt patches. The fine grain is the runtime texture, not a second stripe. */
    private fun asphaltName(along: Double, east: Double, north: Double): String {
        val cell = (along / 12.0).toInt()
        val salt = (east * 0.41 + north * 0.27).toInt()
        val mixed = (cell * 1103515245 + salt * 12345).ushr(16)
        return asphalt[Math.floorMod(mixed, asphalt.size)].name
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

    /**
     * Red-white kerbs only where the heading changes by several degrees across
     * about fourteen meters. A gentle bend stays a plain shoulder.
     */
    private fun realCorner(curve: List<RoadSample>, index: Int): Boolean {
        val here = curve[index]
        var before = here
        var after = here
        for (sample in curve) {
            if (sample.along <= here.along - 7.0) before = sample
            if (after === here && sample.along >= here.along + 7.0) after = sample
        }
        if (before === here || after === here) return abs(here.turn) > 0.08
        return corner(before, here, after) > 0.07
    }

    /** Rounded crowns and low bushes beside the carriageway. They stay off the asphalt. */
    private fun sceneryStrips(curve: List<RoadSample>, from: Double, until: Double): Map<String, Strip> {
        val trunkStrip = Strip(trunk.name)
        val canopyStrip = Strip(canopy.name)
        val lightStrip = Strip(canopyLight.name)
        val bushStrip = Strip(bush.name)
        for (index in curve.indices) {
            val sample = curve[index]
            if (sample.along < from || sample.along >= until) continue
            val treeSlot = (sample.along / 28.0).toInt()
            if (abs(sample.along - treeSlot * 28.0) <= 1.4) {
                plantTree(trunkStrip, canopyStrip, lightStrip, curve, index, sample, treeSlot)
            }
            val bushSlot = (sample.along / 15.0).toInt()
            if (bushSlot % 2 == 1 && abs(sample.along - bushSlot * 15.0) <= 1.2) {
                plantBush(bushStrip, curve, index, sample, bushSlot)
            }
        }
        return linkedMapOf(
            trunk.name to trunkStrip,
            canopy.name to canopyStrip,
            canopyLight.name to lightStrip,
            bush.name to bushStrip
        )
    }

    private fun plantTree(
        trunkStrip: Strip,
        canopyStrip: Strip,
        lightStrip: Strip,
        curve: List<RoadSample>,
        index: Int,
        sample: RoadSample,
        slot: Int
    ) {
        val side = if (slot and 1 == 0) 1.0 else -1.0
        val lateral = side * (16.0 + (slot % 4) * 2.4)
        val placed = beside(curve, index, sample, lateral)
        val groundY = sample.elevation.toDouble()
        val kind = Math.floorMod(slot, 3)
        val height = if (kind == 1) 6.4 else 4.6
        crown(trunkStrip, placed.first, placed.second, groundY, groundY + height * 0.38, 0.18, 6)
        when (kind) {
            1 -> {
                crown(canopyStrip, placed.first, placed.second, groundY + height * 0.30, groundY + height * 0.68, 0.85, 6)
                crown(lightStrip, placed.first, placed.second, groundY + height * 0.58, groundY + height, 0.55, 6)
            }
            2 -> {
                crown(canopyStrip, placed.first - 0.45, placed.second, groundY + height * 0.28, groundY + height * 0.74, 1.25, 6)
                crown(lightStrip, placed.first + 0.5, placed.second + 0.15, groundY + height * 0.46, groundY + height, 0.95, 6)
            }
            else -> {
                crown(canopyStrip, placed.first, placed.second, groundY + height * 0.26, groundY + height * 0.72, 1.45, 7)
                crown(lightStrip, placed.first + 0.15, placed.second - 0.1, groundY + height * 0.55, groundY + height, 0.9, 6)
            }
        }
    }

    private fun plantBush(strip: Strip, curve: List<RoadSample>, index: Int, sample: RoadSample, slot: Int) {
        val side = if (slot and 1 == 0) -1.0 else 1.0
        val placed = beside(curve, index, sample, side * (10.5 + (slot % 3) * 1.2))
        val groundY = sample.elevation.toDouble()
        crown(strip, placed.first, placed.second, groundY + 0.05, groundY + 0.9, 0.8, 6)
    }

    private fun beside(
        curve: List<RoadSample>,
        index: Int,
        sample: RoadSample,
        lateral: Double
    ): Pair<Double, Double> {
        val axis = right(curve, index)
        return (sample.east + axis.first * lateral) to (sample.north + axis.second * lateral)
    }

    /** A faceted ball, used for crowns and bushes. Not a single triangle. */
    private fun crown(
        strip: Strip,
        east: Double,
        north: Double,
        y0: Double,
        y1: Double,
        radius: Double,
        sides: Int
    ) {
        val mid = (y0 + y1) * 0.5
        val top = strip.addPoint(east, north, y1)
        val bottom = strip.addPoint(east, north, y0)
        val ring = ArrayList<Int>(sides)
        for (step in 0 until sides) {
            val angle = step.toDouble() / sides * Math.PI * 2.0
            ring.add(strip.addPoint(east + cos(angle) * radius, north + sin(angle) * radius, mid))
        }
        for (step in 0 until sides) {
            val next = (step + 1) % sides
            strip.tri(top, ring[step], ring[next])
            strip.tri(bottom, ring[next], ring[step])
        }
    }

    /** Soft cloud puffs around the origin. The sky moves them with the car. */
    fun skyClouds(): List<RaceMesh> {
        val strip = Strip(cloud.name)
        val puffs = arrayOf(
            doubleArrayOf(-62.0, 42.0, 78.0, 20.0, 5.0, 11.0),
            doubleArrayOf(54.0, 50.0, 120.0, 16.0, 4.2, 9.0),
            doubleArrayOf(8.0, 64.0, 168.0, 24.0, 5.4, 13.0),
            doubleArrayOf(-96.0, 36.0, 140.0, 14.0, 3.6, 8.0),
            doubleArrayOf(92.0, 46.0, 64.0, 13.0, 3.4, 7.5),
            doubleArrayOf(-16.0, 72.0, 210.0, 28.0, 6.0, 15.0)
        )
        for (puff in puffs) cloud(strip, puff[0], puff[1], puff[2], puff[3], puff[4], puff[5])
        return listOfNotNull(strip.build())
    }

    private fun cloud(
        strip: Strip,
        east: Double,
        y: Double,
        north: Double,
        rx: Double,
        ry: Double,
        rz: Double
    ) {
        val sides = 8
        val bands = 3
        val rings = ArrayList<List<Int>>(bands + 1)
        for (band in 0..bands) {
            val lat = (band.toDouble() / bands - 0.5) * Math.PI
            val ringRadius = cos(lat)
            val ring = ArrayList<Int>(sides)
            for (step in 0 until sides) {
                val angle = step.toDouble() / sides * Math.PI * 2.0
                ring.add(
                    strip.addPoint(
                        east + cos(angle) * ringRadius * rx,
                        north + sin(angle) * ringRadius * rz,
                        y + sin(lat) * ry
                    )
                )
            }
            rings.add(ring)
        }
        for (band in 0 until bands) {
            val lower = rings[band]
            val upper = rings[band + 1]
            for (step in 0 until sides) {
                val next = (step + 1) % sides
                strip.tri(lower[step], upper[step], upper[next])
                strip.tri(lower[step], upper[next], lower[next])
            }
        }
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
            val east = sample.east + right.first * lateral
            val north = sample.north + right.second * lateral
            val index = addPoint(east, north, sample.elevation + lift)
            val slot = uvs.size
            uvs[slot - 2] = (sample.along * 0.22).toFloat()
            uvs[slot - 1] = (lateral * 0.45).toFloat()
            return index
        }

        fun addPoint(east: Double, north: Double, y: Double): Int {
            val index = positions.size / 3
            positions.add(east.toFloat())
            positions.add(y.toFloat())
            positions.add(north.toFloat())
            normals.add(0f)
            normals.add(1f)
            normals.add(0f)
            uvs.add((east * 0.09).toFloat())
            uvs.add((north * 0.09).toFloat())
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

        fun tri(a: Int, b: Int, c: Int) {
            indices.add(a)
            indices.add(b)
            indices.add(c)
        }

        fun build(): RaceMesh? {
            if (indices.size < 3) return null
            return RaceMesh(name, positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
        }
    }
}
