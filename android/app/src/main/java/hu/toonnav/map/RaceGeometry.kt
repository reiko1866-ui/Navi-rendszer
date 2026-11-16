package hu.toonnav.map

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Triangle meshes for the isolated Filament prototype. Positions are meters.
 * The car looks along +Z. The road lies on Y = 0, X east and Z north.
 * Nothing here draws pixels or touches navigation state.
 */
data class RaceMaterial(
    val name: String,
    val red: Float,
    val green: Float,
    val blue: Float,
    val metallic: Float,
    val roughness: Float,
    val alpha: Float = 1f
)

data class RaceMesh(
    val material: String,
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    init {
        require(positions.size % 3 == 0)
        require(normals.size == positions.size)
        require(uvs.size == vertexCount * 2)
        require(indices.size % 3 == 0)
    }
}

data class RaceModel(val car: List<RaceMesh>, val world: List<RaceMesh>, val materials: List<RaceMaterial>)

data class RoadSample(
    val east: Double,
    val north: Double,
    val along: Double,
    val turn: Double,
    val kind: RoadKind = RoadKind.UNKNOWN,
    val elevation: Float = 0f
)

object RaceGeometry {
    const val roadHalfWidth = 3.6
    /** Sine of the heading change between samples. About 0.02 is an 80 m bend, not a hairpin. */
    const val kerbTurn = 0.02
    const val deckTop = 0.06
    const val deckDepth = 0.08
    const val shoulderWidth = 2.2
    const val asphaltTile = 2.5
    const val grassTile = 4.0
    const val gravelTile = 2.0
    const val chunkMeters = 250.0

    /** Stable patches of asphalt tint. Nearby cells differ, and the run is not a fixed stripe. */
    fun wearLayer(along: Double): Int {
        val cell = (along / 28.0).toInt()
        val mixed = cell * 1103515245 + 12345
        return mixed.ushr(16) and 3
    }

    fun chunkIndex(along: Double): Int = kotlin.math.floor(along / chunkMeters).toInt().coerceAtLeast(0)

    fun chunksCovering(along: Double, routeEnd: Double, behind: Double = 220.0, ahead: Double = 1_700.0): IntRange {
        if (routeEnd <= 1.0) return 0..0
        val first = chunkIndex((along - behind).coerceAtLeast(0.0))
        val lastAlong = (along + ahead).coerceAtMost((routeEnd - 0.01).coerceAtLeast(0.0))
        return first..chunkIndex(lastAlong)
    }

    fun worldModel(
        samples: List<RoadSample>,
        heightAt: ((Double, Double) -> Float)? = null,
        fromAlong: Double = Double.NEGATIVE_INFINITY,
        untilAlong: Double = Double.POSITIVE_INFINITY
    ): List<RaceMesh> {
        if (samples.size < 2) return emptyList()
        val grass = MeshBuilder()
        val asphalt = Array(4) { MeshBuilder() }
        val shoulder = MeshBuilder()
        val rubber = MeshBuilder()
        val dashes = MeshBuilder()
        val edges = MeshBuilder()
        val blend = MeshBuilder()
        val kerbRed = MeshBuilder()
        val kerbWhite = MeshBuilder()
        val asphaltNames = arrayOf("asphalt", "asphaltWarm", "asphaltCool", "asphaltDark")
        for (index in 0 until samples.size - 1) {
            val a = samples[index]
            val b = samples[index + 1]
            if (a.along < fromAlong || a.along >= untilAlong) continue
            val rightA = rightAt(samples, index)
            val rightB = rightAt(samples, index + 1)
            val profileA = RoadProfile.of(a.kind)
            val profileB = RoadProfile.of(b.kind)
            val halfA = profileA.halfWidth
            val halfB = profileB.halfWidth
            deck(asphalt[wearLayer(a.along)], a, b, rightA, rightB, -halfA, halfA, -halfB, halfB, deckTop, deckDepth, asphaltTile)
            val shoulderA = profileA.shoulder
            val shoulderB = profileB.shoulder
            ribbon(shoulder, a, b, rightA, rightB, -(halfA + shoulderA), -halfA, -(halfB + shoulderB), -halfB, 0.012, gravelTile)
            ribbon(shoulder, a, b, rightA, rightB, halfA, halfA + shoulderA, halfB, halfB + shoulderB, 0.012, gravelTile)
            ribbon(blend, a, b, rightA, rightB, -(halfA + shoulderA * 0.55), -(halfA + 0.02), -(halfB + shoulderB * 0.55), -(halfB + 0.02), 0.018, gravelTile)
            ribbon(blend, a, b, rightA, rightB, halfA + 0.02, halfA + shoulderA * 0.55, halfB + 0.02, halfB + shoulderB * 0.55, 0.018, gravelTile)
            verge(grass, a, b, rightA, rightB, -(halfA + shoulderA), -(halfA + shoulderA + 90.0), -(halfB + shoulderB), -(halfB + shoulderB + 90.0), heightAt)
            verge(grass, a, b, rightA, rightB, halfA + shoulderA, halfA + shoulderA + 90.0, halfB + shoulderB, halfB + shoulderB + 90.0, heightAt)
            val trackA = (halfA * 0.34).coerceAtMost(1.15)
            val trackB = (halfB * 0.34).coerceAtMost(1.15)
            ribbon(rubber, a, b, rightA, rightB, trackA - 0.16, trackA + 0.16, trackB - 0.16, trackB + 0.16, deckTop + 0.008, asphaltTile)
            ribbon(rubber, a, b, rightA, rightB, -(trackA + 0.16), -(trackA - 0.16), -(trackB + 0.16), -(trackB - 0.16), deckTop + 0.008, asphaltTile)
            if (profileA.edgeLines && profileB.edgeLines) {
                ribbon(edges, a, b, rightA, rightB, halfA - 0.28, halfA - 0.12, halfB - 0.28, halfB - 0.12, deckTop + 0.012, 1.0)
                ribbon(edges, a, b, rightA, rightB, -(halfA - 0.12), -(halfA - 0.28), -(halfB - 0.12), -(halfB - 0.28), deckTop + 0.012, 1.0)
            }
            if (profileA.center == CenterMark.DASH && profileB.center == CenterMark.DASH && ((a.along / 6.0).toInt()) % 2 == 0) {
                ribbon(dashes, a, b, rightA, rightB, 0.13, -0.13, 0.13, -0.13, deckTop + 0.016, 1.0)
            }
            if (kerbSafe(a, b, rightA, rightB)) {
                layKerbs(kerbRed, kerbWhite, a, b, rightA, rightB, halfA, halfB)
            }
        }
        return listOf(
            grass.build("grass"),
            shoulder.build("shoulder"),
            rubber.build("rubber"),
            edges.build("edge"),
            blend.build("vergeBlend"),
            dashes.build("marking"),
            kerbRed.build("kerbRed"),
            kerbWhite.build("kerbWhite")
        ).plus(asphalt.mapIndexed { index, builder -> builder.build(asphaltNames[index]) }).filter { it.vertexCount >= 3 }
    }

    /**
     * Coarse ground from the same elevation field, outside the road verge.
     * An empty field produces nothing, so a missing survey does not invent hills.
     */
    fun horizon(
        samples: List<RoadSample>,
        heightAt: (Double, Double) -> Float,
        centerEast: Double,
        centerNorth: Double
    ): RaceMesh? {
        if (samples.size < 2) return null
        val builder = MeshBuilder()
        val radius = 760.0
        val step = 52.0
        var east = centerEast - radius
        while (east < centerEast + radius) {
            val nextEast = east + step
            var north = centerNorth - radius
            while (north < centerNorth + radius) {
                val nextNorth = north + step
                val midEast = (east + nextEast) * 0.5
                val midNorth = (north + nextNorth) * 0.5
                if (hypot(midEast - centerEast, midNorth - centerNorth) <= radius && roadDistance(samples, midEast, midNorth) > 70.0) {
                    val y00 = heightAt(east, north) - 0.12f
                    val y10 = heightAt(nextEast, north) - 0.12f
                    val y11 = heightAt(nextEast, nextNorth) - 0.12f
                    val y01 = heightAt(east, nextNorth) - 0.12f
                    val u0 = (east / grassTile).toFloat()
                    val u1 = (nextEast / grassTile).toFloat()
                    val v0 = (north / grassTile).toFloat()
                    val v1 = (nextNorth / grassTile).toFloat()
                    val a = builder.add(floatArrayOf(east.toFloat(), y00, north.toFloat()), u0, v0)
                    val b = builder.add(floatArrayOf(nextEast.toFloat(), y10, north.toFloat()), u1, v0)
                    val c = builder.add(floatArrayOf(nextEast.toFloat(), y11, nextNorth.toFloat()), u1, v1)
                    val d = builder.add(floatArrayOf(east.toFloat(), y01, nextNorth.toFloat()), u0, v1)
                    builder.quad(a, b, c, d)
                }
                north = nextNorth
            }
            east = nextEast
        }
        val mesh = builder.build("grass")
        return if (mesh.vertexCount >= 3) mesh else null
    }

    fun libraryModel(): RaceModel {
        val materials = materials()
        val meshes = materials.map { material ->
            RaceMesh(
                material.name,
                floatArrayOf(0f, -80f, 0f, 0.2f, -80f, 0f, 0f, -80f, 0.2f),
                floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f),
                floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
                intArrayOf(0, 1, 2)
            )
        }
        return RaceModel(emptyList(), meshes, materials)
    }

    fun materials(): List<RaceMaterial> {
        return listOf(
            RaceMaterial("asphalt", 1f, 1f, 1f, 0f, 0.86f),
            RaceMaterial("grass", 1f, 1f, 1f, 1f, 1f),
            RaceMaterial("shoulder", 1f, 1f, 1f, 1f, 1f),
            RaceMaterial("rubber", 0.34f, 0.34f, 0.33f, 0f, 0.92f, 0.32f),
            RaceMaterial("edge", 0.92f, 0.92f, 0.90f, 0f, 0.45f),
            RaceMaterial("marking", 0.95f, 0.95f, 0.93f, 0f, 0.42f),
            RaceMaterial("kerbRed", 0.78f, 0.07f, 0.04f, 0f, 0.42f),
            RaceMaterial("kerbWhite", 0.94f, 0.94f, 0.92f, 0f, 0.38f),
            RaceMaterial("vergeBlend", 0.52f, 0.48f, 0.40f, 0f, 0.9f, 0.35f),
            RaceMaterial("canopy", 0.14f, 0.26f, 0.10f, 0f, 0.88f),
            RaceMaterial("canopyDark", 0.08f, 0.16f, 0.07f, 0f, 0.92f),
            RaceMaterial("trunk", 0.20f, 0.13f, 0.07f, 0f, 0.86f),
            RaceMaterial("building", 0.55f, 0.52f, 0.47f, 0f, 0.82f),
            RaceMaterial("buildingWarm", 0.58f, 0.46f, 0.38f, 0f, 0.84f),
            RaceMaterial("buildingCool", 0.46f, 0.49f, 0.54f, 0f, 0.8f)
        )
    }

    fun sampleRoute(
        points: List<Pair<Double, Double>>,
        stepMeters: Double,
        kinds: List<RoadKind> = emptyList(),
        elevations: FloatArray = FloatArray(0)
    ): List<RoadSample> {
        if (points.size < 2 || stepMeters <= 0.0) return emptyList()
        val along = DoubleArray(points.size)
        for (index in 1 until points.size) {
            along[index] = along[index - 1] + hypot(
                points[index].first - points[index - 1].first,
                points[index].second - points[index - 1].second
            )
        }
        val total = along.last()
        if (total < 1.0) return emptyList()
        val samples = ArrayList<RoadSample>()
        var cursor = 0.0
        while (cursor <= total + 0.01) {
            val placed = place(points, along, kinds, elevations, cursor.coerceAtMost(total))
            samples.add(RoadSample(placed.east, placed.north, cursor.coerceAtMost(total), 0.0, placed.kind, placed.elevation))
            cursor += stepMeters
        }
        for (index in 1 until samples.size - 1) {
            val before = samples[index - 1]
            val current = samples[index]
            val after = samples[index + 1]
            val inX = current.east - before.east
            val inZ = current.north - before.north
            val outX = after.east - current.east
            val outZ = after.north - current.north
            val inLength = hypot(inX, inZ).coerceAtLeast(0.001)
            val outLength = hypot(outX, outZ).coerceAtLeast(0.001)
            val turn = (inX / inLength) * (outZ / outLength) - (inZ / inLength) * (outX / outLength)
            samples[index] = current.copy(turn = turn)
        }
        return samples
    }

    fun bounds(mesh: RaceMesh): FloatArray {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (index in mesh.positions.indices step 3) {
            val x = mesh.positions[index]
            val y = mesh.positions[index + 1]
            val z = mesh.positions[index + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
        }
        return floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
    }

    /**
     * A bend is safe for the racing overlay when the samples actually turn and
     * the two edges still point the same way. Straights and folded kinks are left bare.
     * The stripes are a driving-view overlay, not a claim about real kerbs.
     */
    private fun kerbSafe(
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>
    ): Boolean {
        if (abs(a.turn) < kerbTurn && abs(b.turn) < kerbTurn) return false
        val span = hypot(b.east - a.east, b.north - a.north)
        if (span < 0.4) return false
        val aligned = rightA.first * rightB.first + rightA.second * rightB.second
        return aligned > 0.15
    }

    private fun layKerbs(
        red: MeshBuilder,
        white: MeshBuilder,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        halfA: Double,
        halfB: Double
    ) {
        val span = hypot(b.east - a.east, b.north - a.north)
        val pieces = kotlin.math.ceil(span / 1.6).toInt().coerceAtLeast(1)
        val width = 0.62
        val overlap = 0.05
        val height = 0.08
        for (piece in 0 until pieces) {
            val t0 = piece.toDouble() / pieces
            val t1 = (piece + 1).toDouble() / pieces
            val start = mixSample(a, b, t0)
            val end = mixSample(a, b, t1)
            val rightStart = mixRight(rightA, rightB, t0)
            val rightEnd = mixRight(rightA, rightB, t1)
            val halfStart = halfA + (halfB - halfA) * t0
            val halfEnd = halfA + (halfB - halfA) * t1
            val builder = if (((start.along / 1.6).toInt()) % 2 == 0) red else white
            deck(
                builder, start, end, rightStart, rightEnd,
                halfStart - overlap, halfStart - overlap + width,
                halfEnd - overlap, halfEnd - overlap + width,
                deckTop + height, height, 1.6
            )
            deck(
                builder, start, end, rightStart, rightEnd,
                -(halfStart - overlap + width), -(halfStart - overlap),
                -(halfEnd - overlap + width), -(halfEnd - overlap),
                deckTop + height, height, 1.6
            )
        }
    }

    private fun mixSample(a: RoadSample, b: RoadSample, t: Double): RoadSample {
        return RoadSample(
            a.east + (b.east - a.east) * t,
            a.north + (b.north - a.north) * t,
            a.along + (b.along - a.along) * t,
            a.turn + (b.turn - a.turn) * t,
            a.kind,
            (a.elevation + (b.elevation - a.elevation) * t).toFloat()
        )
    }

    private fun mixRight(a: Pair<Double, Double>, b: Pair<Double, Double>, t: Double): Pair<Double, Double> {
        val east = a.first + (b.first - a.first) * t
        val north = a.second + (b.second - a.second) * t
        val length = hypot(east, north).coerceAtLeast(0.001)
        return (east / length) to (north / length)
    }

    private fun deck(
        builder: MeshBuilder,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        fromA: Double,
        toA: Double,
        fromB: Double,
        toB: Double,
        top: Double,
        depth: Double,
        tile: Double
    ) {
        ribbon(builder, a, b, rightA, rightB, fromA, toA, fromB, toB, top, tile)
        wall(builder, a, b, rightA, rightB, fromA, fromB, top, top - depth, tile)
        wall(builder, a, b, rightA, rightB, toA, toB, top, top - depth, tile)
    }

    private fun wall(
        builder: MeshBuilder,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        offsetA: Double,
        offsetB: Double,
        top: Double,
        bottom: Double,
        tile: Double
    ) {
        val v0 = (a.along / tile).toFloat()
        val v1 = (b.along / tile).toFloat()
        val u0 = (offsetA / tile).toFloat()
        val u1 = (offsetB / tile).toFloat()
        val aTop = builder.add(edge(a, rightA, offsetA, top), u0, v0)
        val bTop = builder.add(edge(b, rightB, offsetB, top), u1, v1)
        val bBottom = builder.add(edge(b, rightB, offsetB, bottom), u1, v1)
        val aBottom = builder.add(edge(a, rightA, offsetA, bottom), u0, v0)
        builder.quad(aTop, bTop, bBottom, aBottom)
    }

    private fun ribbon(
        builder: MeshBuilder,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        fromA: Double,
        toA: Double,
        fromB: Double,
        toB: Double,
        lift: Double,
        tile: Double
    ) {
        val v0 = (a.along / tile).toFloat()
        val v1 = (b.along / tile).toFloat()
        val a0 = builder.add(edge(a, rightA, fromA, lift), (fromA / tile).toFloat(), v0)
        val a1 = builder.add(edge(a, rightA, toA, lift), (toA / tile).toFloat(), v0)
        val b1 = builder.add(edge(b, rightB, toB, lift), (toB / tile).toFloat(), v1)
        val b0 = builder.add(edge(b, rightB, fromB, lift), (fromB / tile).toFloat(), v1)
        builder.quad(a0, b0, b1, a1)
    }

    private fun verge(
        builder: MeshBuilder,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        innerA: Double,
        outerA: Double,
        innerB: Double,
        outerB: Double,
        heightAt: ((Double, Double) -> Float)?
    ) {
        val bands = doubleArrayOf(0.0, 0.12, 0.34, 1.0)
        for (band in 0 until bands.size - 1) {
            val fromA = innerA + (outerA - innerA) * bands[band]
            val toA = innerA + (outerA - innerA) * bands[band + 1]
            val fromB = innerB + (outerB - innerB) * bands[band]
            val toB = innerB + (outerB - innerB) * bands[band + 1]
            val v0 = (a.along / grassTile).toFloat()
            val v1 = (b.along / grassTile).toFloat()
            val a0 = builder.add(vergePoint(a, rightA, fromA, heightAt), (fromA / grassTile).toFloat(), v0)
            val a1 = builder.add(vergePoint(a, rightA, toA, heightAt), (toA / grassTile).toFloat(), v0)
            val b1 = builder.add(vergePoint(b, rightB, toB, heightAt), (toB / grassTile).toFloat(), v1)
            val b0 = builder.add(vergePoint(b, rightB, fromB, heightAt), (fromB / grassTile).toFloat(), v1)
            builder.quad(a0, b0, b1, a1)
        }
    }

    private fun vergePoint(
        sample: RoadSample,
        right: Pair<Double, Double>,
        offset: Double,
        heightAt: ((Double, Double) -> Float)?
    ): FloatArray {
        val east = sample.east + right.first * offset
        val north = sample.north + right.second * offset
        val profile = RoadProfile.of(sample.kind)
        val edge = profile.halfWidth + profile.shoulder
        val surveyed = heightAt?.invoke(east, north) ?: sample.elevation
        val blend = ((abs(offset) - edge) / 22.0).coerceIn(0.0, 1.0).toFloat()
        val y = sample.elevation * (1f - blend) + surveyed * blend
        return floatArrayOf(east.toFloat(), y, north.toFloat())
    }

    private fun roadDistance(samples: List<RoadSample>, east: Double, north: Double): Double {
        var best = Double.POSITIVE_INFINITY
        for (sample in samples) {
            val distance = hypot(sample.east - east, sample.north - north)
            if (distance < best) best = distance
        }
        return best
    }

    private fun edge(sample: RoadSample, right: Pair<Double, Double>, offset: Double, lift: Double): FloatArray {
        return floatArrayOf(
            (sample.east + right.first * offset).toFloat(),
            lift.toFloat() + sample.elevation,
            (sample.north + right.second * offset).toFloat()
        )
    }

    private fun rightAt(samples: List<RoadSample>, index: Int): Pair<Double, Double> {
        val previous = samples[(index - 1).coerceAtLeast(0)]
        val next = samples[(index + 1).coerceAtMost(samples.lastIndex)]
        val east = next.east - previous.east
        val north = next.north - previous.north
        val length = hypot(east, north).coerceAtLeast(0.001)
        return (north / length) to (-east / length)
    }

    private data class Placed(val east: Double, val north: Double, val kind: RoadKind, val elevation: Float)

    private fun place(
        points: List<Pair<Double, Double>>,
        along: DoubleArray,
        kinds: List<RoadKind>,
        elevations: FloatArray,
        distance: Double
    ): Placed {
        if (distance <= 0.0) {
            return Placed(points.first().first, points.first().second, kindAt(kinds, 0), elevAt(elevations, 0))
        }
        for (index in 0 until points.size - 1) {
            if (distance > along[index + 1]) continue
            val span = (along[index + 1] - along[index]).coerceAtLeast(0.001)
            val blend = ((distance - along[index]) / span).coerceIn(0.0, 1.0)
            val start = points[index]
            val end = points[index + 1]
            val elevation = elevAt(elevations, index) + (elevAt(elevations, index + 1) - elevAt(elevations, index)) * blend.toFloat()
            return Placed(
                start.first + (end.first - start.first) * blend,
                start.second + (end.second - start.second) * blend,
                kindAt(kinds, index),
                elevation
            )
        }
        val last = points.lastIndex
        return Placed(points[last].first, points[last].second, kindAt(kinds, last), elevAt(elevations, last))
    }

    private fun kindAt(kinds: List<RoadKind>, index: Int): RoadKind {
        if (index !in kinds.indices) return RoadKind.UNKNOWN
        return kinds[index]
    }

    private fun elevAt(elevations: FloatArray, index: Int): Float {
        if (index !in elevations.indices) return 0f
        return elevations[index]
    }

    private class MeshBuilder {
        private val positions = ArrayList<Float>()
        private val uvs = ArrayList<Float>()
        private val indices = ArrayList<Int>()

        fun add(values: FloatArray, u: Float, v: Float): Int {
            val index = positions.size / 3
            positions.add(values[0])
            positions.add(values[1])
            positions.add(values[2])
            uvs.add(u)
            uvs.add(v)
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

        fun build(material: String): RaceMesh {
            val positionArray = FloatArray(positions.size) { positions[it] }
            val normalArray = FloatArray(positions.size)
            for (triangle in indices.indices step 3) {
                val a = indices[triangle] * 3
                val b = indices[triangle + 1] * 3
                val c = indices[triangle + 2] * 3
                val abx = positionArray[b] - positionArray[a]
                val aby = positionArray[b + 1] - positionArray[a + 1]
                val abz = positionArray[b + 2] - positionArray[a + 2]
                val acx = positionArray[c] - positionArray[a]
                val acy = positionArray[c + 1] - positionArray[a + 1]
                val acz = positionArray[c + 2] - positionArray[a + 2]
                val nx = aby * acz - abz * acy
                val ny = abz * acx - abx * acz
                val nz = abx * acy - aby * acx
                for (vertex in intArrayOf(a, b, c)) {
                    normalArray[vertex] += nx
                    normalArray[vertex + 1] += ny
                    normalArray[vertex + 2] += nz
                }
            }
            for (index in normalArray.indices step 3) {
                val length = sqrt(
                    normalArray[index] * normalArray[index] +
                        normalArray[index + 1] * normalArray[index + 1] +
                        normalArray[index + 2] * normalArray[index + 2]
                ).coerceAtLeast(0.0001f)
                normalArray[index] /= length
                normalArray[index + 1] /= length
                normalArray[index + 2] /= length
            }
            val uvArray = FloatArray(uvs.size) { uvs[it] }
            return RaceMesh(material, positionArray, normalArray, uvArray, indices.toIntArray())
        }
    }
}
