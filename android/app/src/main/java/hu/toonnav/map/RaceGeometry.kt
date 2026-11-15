package hu.toonnav.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
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
    val indices: IntArray
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    init {
        require(positions.size % 3 == 0)
        require(normals.size == positions.size)
        require(indices.size % 3 == 0)
    }
}

data class RaceModel(val car: List<RaceMesh>, val world: List<RaceMesh>, val materials: List<RaceMaterial>)

data class RoadSample(val east: Double, val north: Double, val along: Double, val turn: Double)

object RaceGeometry {
    const val roadHalfWidth = 3.6
    const val kerbTurn = 0.10

    fun carModel(body: RaceMaterial, accent: RaceMaterial): List<RaceMesh> {
        return listOf(
            nose(body.name),
            wing(body.name, 2.22, 1.78, 0.22, 0.045, 0.92),
            wing(body.name, 1.76, 1.46, 0.28, 0.030, 0.86),
            wing(accent.name, 1.44, 1.22, 0.34, 0.022, 0.78),
            endplate(accent.name, -0.94f),
            endplate(accent.name, 0.94f)
        )
    }

    fun worldModel(samples: List<RoadSample>): List<RaceMesh> {
        if (samples.size < 2) return emptyList()
        val asphalt = MeshBuilder()
        val groove = MeshBuilder()
        val dashes = MeshBuilder()
        val kerbRed = MeshBuilder()
        val kerbWhite = MeshBuilder()
        for (index in 0 until samples.size - 1) {
            val a = samples[index]
            val b = samples[index + 1]
            val (rightX, rightZ) = rightOf(a, b)
            ribbon(asphalt, a, b, rightX, rightZ, roadHalfWidth, roadHalfWidth, 0.0)
            ribbon(groove, a, b, rightX, rightZ, 1.15, 0.82, 0.012)
            ribbon(groove, a, b, rightX, rightZ, -0.82, -1.15, 0.012)
            val dash = ((a.along / 6.0).toInt()) % 2 == 0
            if (dash) ribbon(dashes, a, b, rightX, rightZ, 0.07, -0.07, 0.02)
            if (abs(a.turn) >= kerbTurn || abs(b.turn) >= kerbTurn) {
                val inside = if (a.turn + b.turn >= 0.0) 1.0 else -1.0
                val inner = inside * roadHalfWidth
                val outer = inside * (roadHalfWidth + 0.42)
                val target = if (((a.along / 2.4).toInt()) % 2 == 0) kerbRed else kerbWhite
                ribbon(target, a, b, rightX, rightZ, outer, inner, 0.025)
            }
        }
        return listOf(
            asphalt.build("asphalt"),
            groove.build("groove"),
            dashes.build("marking"),
            kerbRed.build("kerbRed"),
            kerbWhite.build("kerbWhite")
        ).filter { it.vertexCount >= 3 }
    }

    fun materials(bodyRed: Float, bodyGreen: Float, bodyBlue: Float, accentRed: Float, accentGreen: Float, accentBlue: Float): List<RaceMaterial> {
        return listOf(
            RaceMaterial("body", bodyRed, bodyGreen, bodyBlue, 0.62f, 0.28f),
            RaceMaterial("accent", accentRed, accentGreen, accentBlue, 0.45f, 0.34f),
            RaceMaterial("asphalt", 0.16f, 0.17f, 0.18f, 0.0f, 0.92f),
            RaceMaterial("groove", 0.07f, 0.07f, 0.08f, 0.0f, 0.98f),
            RaceMaterial("marking", 0.92f, 0.92f, 0.90f, 0.0f, 0.55f),
            RaceMaterial("kerbRed", 0.86f, 0.04f, 0.03f, 0.0f, 0.48f),
            RaceMaterial("kerbWhite", 0.94f, 0.94f, 0.92f, 0.0f, 0.48f)
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

    private fun nose(material: String): RaceMesh {
        val builder = MeshBuilder()
        val stations = 18
        val slices = 28
        val grid = Array(stations) { FloatArray(slices * 3) }
        for (station in 0 until stations) {
            val blend = station / (stations - 1.0)
            val z = (0.20 + (2.28 - 0.20) * blend).toFloat()
            val centerY = (0.50 - 0.16 * blend).toFloat()
            val radiusX = (0.155 * (1.0 - blend * blend)).toFloat().coerceAtLeast(0.012f)
            val radiusY = radiusX * 0.72f
            for (slice in 0 until slices) {
                val angle = (slice / slices.toDouble()) * PI * 2.0
                val x = (sin(angle) * radiusX).toFloat()
                val lower = if (cos(angle) < 0.0) 0.62f else 1f
                val y = centerY + (cos(angle) * radiusY * lower).toFloat()
                grid[station][slice * 3] = x
                grid[station][slice * 3 + 1] = y
                grid[station][slice * 3 + 2] = z
            }
        }
        for (station in 0 until stations - 1) {
            for (slice in 0 until slices) {
                val next = (slice + 1) % slices
                val a = builder.add(grid[station], slice)
                val b = builder.add(grid[station], next)
                val c = builder.add(grid[station + 1], next)
                val d = builder.add(grid[station + 1], slice)
                builder.quad(a, b, c, d)
            }
        }
        return builder.build(material)
    }

    private fun wing(
        material: String,
        leadingZ: Double,
        trailingZ: Double,
        baseY: Double,
        thickness: Double,
        span: Double
    ): RaceMesh {
        val builder = MeshBuilder()
        val spans = 14
        val chords = 8
        val upper = Array(spans) { Array(chords) { FloatArray(3) } }
        val lower = Array(spans) { Array(chords) { FloatArray(3) } }
        for (spanIndex in 0 until spans) {
            val spanBlend = spanIndex / (spans - 1.0)
            val x = ((spanBlend - 0.5) * 2.0 * span).toFloat()
            val droop = (abs(spanBlend - 0.5) * 0.06).toFloat()
            for (chordIndex in 0 until chords) {
                val chordBlend = chordIndex / (chords - 1.0)
                val z = (trailingZ + (leadingZ - trailingZ) * chordBlend).toFloat()
                val camber = sin(chordBlend * PI).toFloat()
                val y = (baseY - droop + camber * thickness).toFloat()
                upper[spanIndex][chordIndex][0] = x
                upper[spanIndex][chordIndex][1] = y
                upper[spanIndex][chordIndex][2] = z
                lower[spanIndex][chordIndex][0] = x
                lower[spanIndex][chordIndex][1] = y - (thickness * 0.45).toFloat()
                lower[spanIndex][chordIndex][2] = z
            }
        }
        sheet(builder, upper, true)
        sheet(builder, lower, false)
        for (spanIndex in 0 until spans - 1) {
            val a = builder.add(upper[spanIndex][0])
            val b = builder.add(upper[spanIndex + 1][0])
            val c = builder.add(lower[spanIndex + 1][0])
            val d = builder.add(lower[spanIndex][0])
            builder.quad(a, b, c, d)
            val e = builder.add(upper[spanIndex][chords - 1])
            val f = builder.add(upper[spanIndex + 1][chords - 1])
            val g = builder.add(lower[spanIndex + 1][chords - 1])
            val h = builder.add(lower[spanIndex][chords - 1])
            builder.quad(e, h, g, f)
        }
        return builder.build(material)
    }

    private fun sheet(builder: MeshBuilder, grid: Array<Array<FloatArray>>, upward: Boolean) {
        val spans = grid.size
        val chords = grid[0].size
        for (spanIndex in 0 until spans - 1) {
            for (chordIndex in 0 until chords - 1) {
                val a = builder.add(grid[spanIndex][chordIndex])
                val b = builder.add(grid[spanIndex + 1][chordIndex])
                val c = builder.add(grid[spanIndex + 1][chordIndex + 1])
                val d = builder.add(grid[spanIndex][chordIndex + 1])
                if (upward) builder.quad(a, d, c, b) else builder.quad(a, b, c, d)
            }
        }
    }

    private fun endplate(material: String, x: Float): RaceMesh {
        val builder = MeshBuilder()
        val y0 = 0.08f
        val y1 = 0.52f
        val z0 = 1.18f
        val z1 = 2.26f
        val half = 0.012f
        val corners = arrayOf(
            floatArrayOf(x - half, y0, z0),
            floatArrayOf(x + half, y0, z0),
            floatArrayOf(x + half, y0, z1),
            floatArrayOf(x - half, y0, z1),
            floatArrayOf(x - half, y1, z0),
            floatArrayOf(x + half, y1, z0),
            floatArrayOf(x + half, y1, z1),
            floatArrayOf(x - half, y1, z1)
        )
        val ids = corners.map { builder.add(it) }
        builder.quad(ids[0], ids[1], ids[2], ids[3])
        builder.quad(ids[4], ids[7], ids[6], ids[5])
        builder.quad(ids[0], ids[4], ids[5], ids[1])
        builder.quad(ids[3], ids[2], ids[6], ids[7])
        builder.quad(ids[0], ids[3], ids[7], ids[4])
        builder.quad(ids[1], ids[5], ids[6], ids[2])
        return builder.build(material)
    }

    private fun ribbon(
        builder: MeshBuilder,
        a: RoadSample,
        b: RoadSample,
        rightX: Double,
        rightZ: Double,
        from: Double,
        to: Double,
        lift: Double
    ) {
        val a0 = builder.add(edge(a, rightX, rightZ, from, lift))
        val a1 = builder.add(edge(a, rightX, rightZ, to, lift))
        val b1 = builder.add(edge(b, rightX, rightZ, to, lift))
        val b0 = builder.add(edge(b, rightX, rightZ, from, lift))
        builder.quad(a0, b0, b1, a1)
    }

    private fun edge(sample: RoadSample, rightX: Double, rightZ: Double, offset: Double, lift: Double): FloatArray {
        return floatArrayOf(
            (sample.east + rightX * offset).toFloat(),
            lift.toFloat(),
            (sample.north + rightZ * offset).toFloat()
        )
    }

    private fun rightOf(a: RoadSample, b: RoadSample): Pair<Double, Double> {
        val east = b.east - a.east
        val north = b.north - a.north
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
        private val indices = ArrayList<Int>()

        fun add(values: FloatArray, offset: Int = 0): Int {
            val index = positions.size / 3
            positions.add(values[offset * 3])
            positions.add(values[offset * 3 + 1])
            positions.add(values[offset * 3 + 2])
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
            return RaceMesh(material, positionArray, normalArray, indices.toIntArray())
        }
    }
}
