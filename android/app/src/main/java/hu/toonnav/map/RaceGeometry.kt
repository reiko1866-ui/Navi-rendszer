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
    val roughness: Float
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

data class RoadSample(val east: Double, val north: Double, val along: Double, val turn: Double)

object RaceGeometry {
    const val roadHalfWidth = 3.6
    const val kerbTurn = 0.10
    const val deckTop = 0.06
    const val deckDepth = 0.16
    const val vergeWidth = 20.0
    const val vergeTop = 0.0

    fun worldModel(samples: List<RoadSample>): List<RaceMesh> {
        if (samples.size < 2) return emptyList()
        val asphalt = MeshBuilder()
        val verge = MeshBuilder()
        val groove = MeshBuilder()
        val dashes = MeshBuilder()
        val edges = MeshBuilder()
        val kerbRed = MeshBuilder()
        val kerbWhite = MeshBuilder()
        for (index in 0 until samples.size - 1) {
            val a = samples[index]
            val b = samples[index + 1]
            val rightA = rightAt(samples, index)
            val rightB = rightAt(samples, index + 1)
            deck(asphalt, a, b, rightA, rightB, -roadHalfWidth, roadHalfWidth, deckTop, deckDepth)
            ribbon(verge, a, b, rightA, rightB, -(roadHalfWidth + vergeWidth), -roadHalfWidth, vergeTop, 0f, 1f)
            ribbon(verge, a, b, rightA, rightB, roadHalfWidth, roadHalfWidth + vergeWidth, vergeTop, 0f, 1f)
            ribbon(groove, a, b, rightA, rightB, 1.15, 0.82, deckTop + 0.008, 0f, 1f)
            ribbon(groove, a, b, rightA, rightB, -0.82, -1.15, deckTop + 0.008, 0f, 1f)
            ribbon(edges, a, b, rightA, rightB, roadHalfWidth - 0.28, roadHalfWidth - 0.14, deckTop + 0.012, 0f, 1f)
            ribbon(edges, a, b, rightA, rightB, -(roadHalfWidth - 0.14), -(roadHalfWidth - 0.28), deckTop + 0.012, 0f, 1f)
            val dash = ((a.along / 6.0).toInt()) % 2 == 0
            if (dash) ribbon(dashes, a, b, rightA, rightB, 0.07, -0.07, deckTop + 0.016, 0f, 1f)
            if (abs(a.turn) >= kerbTurn || abs(b.turn) >= kerbTurn) {
                val inside = if (a.turn + b.turn >= 0.0) 1.0 else -1.0
                val inner = inside * roadHalfWidth
                val outer = inside * (roadHalfWidth + 0.45)
                val target = if (((a.along / 2.4).toInt()) % 2 == 0) kerbRed else kerbWhite
                deck(target, a, b, rightA, rightB, outer, inner, deckTop + 0.045, 0.045)
            }
        }
        return listOf(
            verge.build("verge"),
            asphalt.build("asphalt"),
            groove.build("groove"),
            edges.build("edge"),
            dashes.build("marking"),
            kerbRed.build("kerbRed"),
            kerbWhite.build("kerbWhite")
        ).filter { it.vertexCount >= 3 }
    }

    fun materials(): List<RaceMaterial> {
        return listOf(
            RaceMaterial("asphalt", 0.22f, 0.22f, 0.23f, 0.02f, 0.84f),
            RaceMaterial("verge", 0.18f, 0.20f, 0.16f, 0.0f, 0.96f),
            RaceMaterial("groove", 0.08f, 0.08f, 0.09f, 0.0f, 0.98f),
            RaceMaterial("edge", 0.86f, 0.86f, 0.84f, 0.0f, 0.48f),
            RaceMaterial("marking", 0.93f, 0.93f, 0.90f, 0.0f, 0.48f),
            RaceMaterial("kerbRed", 0.78f, 0.06f, 0.04f, 0.0f, 0.42f),
            RaceMaterial("kerbWhite", 0.92f, 0.92f, 0.90f, 0.0f, 0.42f)
        )
    }

    fun sampleRoute(points: List<Pair<Double, Double>>, stepMeters: Double): List<RoadSample> {
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
            val point = at(points, along, cursor.coerceAtMost(total))
            samples.add(RoadSample(point.first, point.second, cursor.coerceAtMost(total), 0.0))
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
        from: Double,
        to: Double,
        top: Double,
        depth: Double
    ) {
        val span = kotlin.math.abs(to - from).toFloat().coerceAtLeast(0.2f)
        ribbon(builder, a, b, rightA, rightB, from, to, top, 0f, span)
        wall(builder, a, b, rightA, rightB, from, top, top - depth)
        wall(builder, a, b, rightA, rightB, to, top, top - depth)
    }

    private fun wall(
        builder: MeshBuilder,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        offset: Double,
        top: Double,
        bottom: Double
    ) {
        val v0 = (a.along / 4.0).toFloat()
        val v1 = (b.along / 4.0).toFloat()
        val aTop = builder.add(edge(a, rightA, offset, top), 0f, v0)
        val bTop = builder.add(edge(b, rightB, offset, top), 1f, v1)
        val bBottom = builder.add(edge(b, rightB, offset, bottom), 1f, v1)
        val aBottom = builder.add(edge(a, rightA, offset, bottom), 0f, v0)
        builder.quad(aTop, bTop, bBottom, aBottom)
    }

    private fun ribbon(
        builder: MeshBuilder,
        a: RoadSample,
        b: RoadSample,
        rightA: Pair<Double, Double>,
        rightB: Pair<Double, Double>,
        from: Double,
        to: Double,
        lift: Double,
        u0: Float,
        u1: Float
    ) {
        val v0 = (a.along / 4.0).toFloat()
        val v1 = (b.along / 4.0).toFloat()
        val a0 = builder.add(edge(a, rightA, from, lift), u0, v0)
        val a1 = builder.add(edge(a, rightA, to, lift), u1, v0)
        val b1 = builder.add(edge(b, rightB, to, lift), u1, v1)
        val b0 = builder.add(edge(b, rightB, from, lift), u0, v1)
        builder.quad(a0, b0, b1, a1)
    }

    private fun edge(sample: RoadSample, right: Pair<Double, Double>, offset: Double, lift: Double): FloatArray {
        return floatArrayOf(
            (sample.east + right.first * offset).toFloat(),
            lift.toFloat(),
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
