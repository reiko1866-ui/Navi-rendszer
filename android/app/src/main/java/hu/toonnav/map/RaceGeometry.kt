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
    const val kerbTurn = 0.10
    const val deckTop = 0.04
    const val deckDepth = 0.08
    const val shoulderWidth = 2.2
    const val asphaltTile = 4.0
    const val grassTile = 4.0
    const val gravelTile = 2.0

    fun worldModel(samples: List<RoadSample>, heightAt: ((Double, Double) -> Float)? = null): List<RaceMesh> {
        if (samples.size < 2) return emptyList()
        val grass = MeshBuilder()
        val asphalt = MeshBuilder()
        val shoulder = MeshBuilder()
        val rubber = MeshBuilder()
        val dashes = MeshBuilder()
        val edges = MeshBuilder()
        val kerbRed = MeshBuilder()
        val kerbWhite = MeshBuilder()
        ground(grass, samples, heightAt)
        for (index in 0 until samples.size - 1) {
            val a = samples[index]
            val b = samples[index + 1]
            val rightA = rightAt(samples, index)
            val rightB = rightAt(samples, index + 1)
            val profileA = RoadProfile.of(a.kind)
            val profileB = RoadProfile.of(b.kind)
            val halfA = profileA.halfWidth
            val halfB = profileB.halfWidth
            deck(asphalt, a, b, rightA, rightB, -halfA, halfA, -halfB, halfB, deckTop, deckDepth, asphaltTile)
            val shoulderA = profileA.shoulder
            val shoulderB = profileB.shoulder
            ribbon(shoulder, a, b, rightA, rightB, -(halfA + shoulderA), -halfA, -(halfB + shoulderB), -halfB, 0.012, gravelTile)
            ribbon(shoulder, a, b, rightA, rightB, halfA, halfA + shoulderA, halfB, halfB + shoulderB, 0.012, gravelTile)
            val trackA = (halfA * 0.34).coerceAtMost(1.15)
            val trackB = (halfB * 0.34).coerceAtMost(1.15)
            ribbon(rubber, a, b, rightA, rightB, trackA - 0.16, trackA + 0.16, trackB - 0.16, trackB + 0.16, deckTop + 0.008, asphaltTile)
            ribbon(rubber, a, b, rightA, rightB, -(trackA + 0.16), -(trackA - 0.16), -(trackB + 0.16), -(trackB - 0.16), deckTop + 0.008, asphaltTile)
            if (profileA.edgeLines && profileB.edgeLines) {
                ribbon(edges, a, b, rightA, rightB, halfA - 0.28, halfA - 0.12, halfB - 0.28, halfB - 0.12, deckTop + 0.012, 1.0)
                ribbon(edges, a, b, rightA, rightB, -(halfA - 0.12), -(halfA - 0.28), -(halfB - 0.12), -(halfB - 0.28), deckTop + 0.012, 1.0)
            }
            if (profileA.center == CenterMark.DASH && profileB.center == CenterMark.DASH && ((a.along / 6.0).toInt()) % 2 == 0) {
                ribbon(dashes, a, b, rightA, rightB, 0.09, -0.09, 0.09, -0.09, deckTop + 0.014, 1.0)
            }
            val corner = abs(a.turn) >= kerbTurn || abs(b.turn) >= kerbTurn
            if (corner && profileA.kerbs && profileB.kerbs) {
                val inside = if (a.turn + b.turn >= 0.0) 1.0 else -1.0
                deck(
                    if (((a.along / 2.4).toInt()) % 2 == 0) kerbRed else kerbWhite,
                    a, b, rightA, rightB,
                    inside * (halfA + 0.42), inside * halfA,
                    inside * (halfB + 0.42), inside * halfB,
                    deckTop + 0.045, 0.045, 1.2
                )
            }
        }
        return listOf(
            grass.build("grass"),
            shoulder.build("shoulder"),
            asphalt.build("asphalt"),
            rubber.build("rubber"),
            edges.build("edge"),
            dashes.build("marking"),
            kerbRed.build("kerbRed"),
            kerbWhite.build("kerbWhite")
        ).filter { it.vertexCount >= 3 }
    }

    fun materials(): List<RaceMaterial> {
        return listOf(
            RaceMaterial("asphalt", 1f, 1f, 1f, 1f, 1f),
            RaceMaterial("grass", 1f, 1f, 1f, 1f, 1f),
            RaceMaterial("shoulder", 1f, 1f, 1f, 1f, 1f),
            RaceMaterial("rubber", 0.015f, 0.015f, 0.016f, 0f, 0.96f, 0.72f),
            RaceMaterial("edge", 0.92f, 0.92f, 0.90f, 0f, 0.45f),
            RaceMaterial("marking", 0.95f, 0.95f, 0.93f, 0f, 0.42f),
            RaceMaterial("kerbRed", 0.82f, 0.08f, 0.05f, 0f, 0.4f),
            RaceMaterial("kerbWhite", 0.93f, 0.93f, 0.91f, 0f, 0.4f)
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

    private fun ground(builder: MeshBuilder, samples: List<RoadSample>, heightAt: ((Double, Double) -> Float)?) {
        var minEast = Double.POSITIVE_INFINITY
        var maxEast = Double.NEGATIVE_INFINITY
        var minNorth = Double.POSITIVE_INFINITY
        var maxNorth = Double.NEGATIVE_INFINITY
        for (sample in samples) {
            if (sample.east < minEast) minEast = sample.east
            if (sample.east > maxEast) maxEast = sample.east
            if (sample.north < minNorth) minNorth = sample.north
            if (sample.north > maxNorth) maxNorth = sample.north
        }
        val pad = 110.0
        minEast -= pad
        maxEast += pad
        minNorth -= pad
        maxNorth += pad
        val spanEast = (maxEast - minEast).coerceAtLeast(1.0)
        val spanNorth = (maxNorth - minNorth).coerceAtLeast(1.0)
        var step = 48.0
        while ((spanEast / step) * (spanNorth / step) > 2400.0) step *= 1.4
        var east = minEast
        while (east < maxEast - 0.01) {
            val nextEast = (east + step).coerceAtMost(maxEast)
            var north = minNorth
            while (north < maxNorth - 0.01) {
                val nextNorth = (north + step).coerceAtMost(maxNorth)
                val u0 = (east / grassTile).toFloat()
                val u1 = (nextEast / grassTile).toFloat()
                val v0 = (north / grassTile).toFloat()
                val v1 = (nextNorth / grassTile).toFloat()
                val y00 = groundHeight(samples, heightAt, east, north)
                val y10 = groundHeight(samples, heightAt, nextEast, north)
                val y11 = groundHeight(samples, heightAt, nextEast, nextNorth)
                val y01 = groundHeight(samples, heightAt, east, nextNorth)
                val a = builder.add(floatArrayOf(east.toFloat(), y00, north.toFloat()), u0, v0)
                val b = builder.add(floatArrayOf(nextEast.toFloat(), y10, north.toFloat()), u1, v0)
                val c = builder.add(floatArrayOf(nextEast.toFloat(), y11, nextNorth.toFloat()), u1, v1)
                val d = builder.add(floatArrayOf(east.toFloat(), y01, nextNorth.toFloat()), u0, v1)
                builder.quad(a, b, c, d)
                north = nextNorth
            }
            east = nextEast
        }
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

    private fun groundHeight(
        samples: List<RoadSample>,
        heightAt: ((Double, Double) -> Float)?,
        east: Double,
        north: Double
    ): Float {
        var best = 0f
        var bestDistance = Double.POSITIVE_INFINITY
        for (sample in samples) {
            val distance = hypot(sample.east - east, sample.north - north)
            if (distance < bestDistance) {
                bestDistance = distance
                best = sample.elevation
            }
        }
        val surveyed = heightAt?.invoke(east, north) ?: return best
        val blend = (bestDistance / 18.0).coerceIn(0.0, 1.0).toFloat()
        return best * (1f - blend) + surveyed * blend
    }

    private fun at(points: List<Pair<Double, Double>>, along: DoubleArray, distance: Double): Pair<Double, Double> {
        if (distance <= 0.0) return points.first()
        for (index in 0 until points.size - 1) {
            if (distance > along[index + 1]) continue
            val span = (along[index + 1] - along[index]).coerceAtLeast(0.001)
            val blend = ((distance - along[index]) / span).coerceIn(0.0, 1.0)
            val start = points[index]
            val end = points[index + 1]
            return (start.first + (end.first - start.first) * blend) to
                (start.second + (end.second - start.second) * blend)
        }
        return points.last()
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
