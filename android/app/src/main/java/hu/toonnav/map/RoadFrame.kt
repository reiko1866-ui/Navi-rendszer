package hu.toonnav.map

import kotlin.math.sqrt

/**
 * Filament lit materials read a tangent frame as a quaternion, not a normal vector.
 * The packing matches Filament's packTangentFrame so the lit normal stays the mesh normal.
 */
object RoadFrame {
    fun quaternions(mesh: RaceMesh): FloatArray {
        val count = mesh.vertexCount
        val tangent = FloatArray(count * 3)
        val bitangent = FloatArray(count * 3)
        val positions = mesh.positions
        val uvs = mesh.uvs
        val indices = mesh.indices
        var triangle = 0
        while (triangle < indices.size) {
            val i0 = indices[triangle]
            val i1 = indices[triangle + 1]
            val i2 = indices[triangle + 2]
            val e1x = positions[i1 * 3] - positions[i0 * 3]
            val e1y = positions[i1 * 3 + 1] - positions[i0 * 3 + 1]
            val e1z = positions[i1 * 3 + 2] - positions[i0 * 3 + 2]
            val e2x = positions[i2 * 3] - positions[i0 * 3]
            val e2y = positions[i2 * 3 + 1] - positions[i0 * 3 + 1]
            val e2z = positions[i2 * 3 + 2] - positions[i0 * 3 + 2]
            val du1 = uvs[i1 * 2] - uvs[i0 * 2]
            val dv1 = uvs[i1 * 2 + 1] - uvs[i0 * 2 + 1]
            val du2 = uvs[i2 * 2] - uvs[i0 * 2]
            val dv2 = uvs[i2 * 2 + 1] - uvs[i0 * 2 + 1]
            val denom = du1 * dv2 - dv1 * du2
            val scale = if (kotlin.math.abs(denom) < 1e-8f) 0f else 1f / denom
            val tx = (e1x * dv2 - e2x * dv1) * scale
            val ty = (e1y * dv2 - e2y * dv1) * scale
            val tz = (e1z * dv2 - e2z * dv1) * scale
            val bx = (e2x * du1 - e1x * du2) * scale
            val by = (e2y * du1 - e1y * du2) * scale
            val bz = (e2z * du1 - e1z * du2) * scale
            for (index in intArrayOf(i0, i1, i2)) {
                tangent[index * 3] += tx
                tangent[index * 3 + 1] += ty
                tangent[index * 3 + 2] += tz
                bitangent[index * 3] += bx
                bitangent[index * 3 + 1] += by
                bitangent[index * 3 + 2] += bz
            }
            triangle += 3
        }
        val packed = FloatArray(count * 4)
        for (index in 0 until count) {
            val nx = mesh.normals[index * 3]
            val ny = mesh.normals[index * 3 + 1]
            val nz = mesh.normals[index * 3 + 2]
            var tx = tangent[index * 3]
            var ty = tangent[index * 3 + 1]
            var tz = tangent[index * 3 + 2]
            val dot = tx * nx + ty * ny + tz * nz
            tx -= nx * dot
            ty -= ny * dot
            tz -= nz * dot
            var length = sqrt(tx * tx + ty * ty + tz * tz)
            if (length < 1e-5f) {
                tx = 1f
                ty = 0f
                tz = 0f
                length = 1f
            }
            tx /= length
            ty /= length
            tz /= length
            var bx = ny * tz - nz * ty
            var by = nz * tx - nx * tz
            var bz = nx * ty - ny * tx
            val handed = bx * bitangent[index * 3] + by * bitangent[index * 3 + 1] + bz * bitangent[index * 3 + 2]
            if (handed < 0f) {
                bx = -bx
                by = -by
                bz = -bz
            }
            val q = packTangentFrame(tx, ty, tz, bx, by, bz, nx, ny, nz)
            packed[index * 4] = q[0]
            packed[index * 4 + 1] = q[1]
            packed[index * 4 + 2] = q[2]
            packed[index * 4 + 3] = q[3]
        }
        return packed
    }

    /**
     * Filament's mat3::packTangentFrame. The shader recovers the normal from the
     * quaternion and the bitangent direction from the sign of w. Packing a mirrored
     * frame as an ordinary rotation points the lit normal along the horizon, so the
     * road is black under the sun.
     */
    fun packTangentFrame(
        tx: Float, ty: Float, tz: Float,
        bx: Float, by: Float, bz: Float,
        nx: Float, ny: Float, nz: Float
    ): FloatArray {
        val cx = ny * tz - nz * ty
        val cy = nz * tx - nx * tz
        val cz = nx * ty - ny * tx
        val rotation = quaternion(tx, ty, tz, cx, cy, cz, nx, ny, nz)
        var x = rotation[0]
        var y = rotation[1]
        var z = rotation[2]
        var w = rotation[3]
        val bias = 1f / ((1 shl 15) - 1).toFloat()
        if (w < bias) {
            val factor = sqrt(1f - bias * bias)
            x *= factor
            y *= factor
            z *= factor
            w = bias
        }
        val reflected = (ty * nz - tz * ny) * bx + (tz * nx - tx * nz) * by + (tx * ny - ty * nx) * bz < 0f
        if (reflected) {
            x = -x
            y = -y
            z = -z
            w = -w
        }
        return floatArrayOf(x, y, z, w)
    }

    fun quaternion(
        tx: Float, ty: Float, tz: Float,
        bx: Float, by: Float, bz: Float,
        nx: Float, ny: Float, nz: Float
    ): FloatArray {
        val trace = tx + by + nz
        val x: Float
        val y: Float
        val z: Float
        val w: Float
        if (trace > 0f) {
            val s = sqrt(trace + 1f) * 2f
            w = 0.25f * s
            x = (bz - ny) / s
            y = (nx - tz) / s
            z = (ty - bx) / s
        } else if (tx > by && tx > nz) {
            val s = sqrt(1f + tx - by - nz) * 2f
            w = (bz - ny) / s
            x = 0.25f * s
            y = (ty + bx) / s
            z = (nx + tz) / s
        } else if (by > nz) {
            val s = sqrt(1f + by - tx - nz) * 2f
            w = (nx - tz) / s
            x = (ty + bx) / s
            y = 0.25f * s
            z = (bz + ny) / s
        } else {
            val s = sqrt(1f + nz - tx - by) * 2f
            w = (ty - bx) / s
            x = (nx + tz) / s
            y = (bz + ny) / s
            z = 0.25f * s
        }
        var qx = x
        var qy = y
        var qz = z
        var qw = w
        val length = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-6f)
        qx /= length
        qy /= length
        qz /= length
        qw /= length
        if (qw < 0f) {
            qx = -qx
            qy = -qy
            qz = -qz
            qw = -qw
        }
        return floatArrayOf(qx, qy, qz, qw)
    }
}
