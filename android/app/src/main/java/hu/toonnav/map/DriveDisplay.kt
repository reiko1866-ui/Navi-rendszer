package hu.toonnav.map

import kotlin.math.atan
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * One driving layout for every Android screen. The numbers come from the
 * pixel size and density, so a phone, a tablet, and a wide car display share
 * the same Filament view without a separate APK.
 */
object DriveDisplay {
    const val targetHorizontalDegrees = 72.0
    const val minVerticalDegrees = 34.0
    const val maxVerticalDegrees = 92.0

    /**
     * Vertical field of view that keeps about 72 degrees of the road across
     * the width. A fixed 48 degree vertical lens makes an ultra-wide display
     * mostly sky, and a tall phone cuts off the carriageway edges.
     */
    fun verticalFov(aspect: Double): Double {
        val safe = aspect.coerceIn(0.35, 3.4)
        val half = Math.toRadians(targetHorizontalDegrees * 0.5)
        val vertical = Math.toDegrees(2.0 * atan(tan(half) / safe))
        return vertical.coerceIn(minVerticalDegrees, maxVerticalDegrees)
    }

    fun horizontalFov(aspect: Double): Double {
        val vertical = verticalFov(aspect)
        val safe = aspect.coerceIn(0.35, 3.4)
        val half = Math.toRadians(vertical * 0.5)
        return Math.toDegrees(2.0 * atan(tan(half) * safe))
    }

    enum class Quality { FULL, REDUCED }

    /**
     * Drops ambient occlusion, shadow detail, and resolution scale. The road
     * mesh itself is not part of this choice.
     */
    fun quality(widthPx: Int, heightPx: Int, memoryClassMb: Int, glEsMajor: Int): Quality {
        if (glEsMajor in 1..2) return Quality.REDUCED
        if (memoryClassMb in 1..160) return Quality.REDUCED
        val pixels = widthPx.toLong() * heightPx.toLong()
        if (pixels >= 2560L * 1440L && memoryClassMb < 256) return Quality.REDUCED
        return Quality.FULL
    }
}

/**
 * Grass and the shoulder are stored with a metallic factor of 1 and expect the
 * ORM texture to multiply that back to zero. A phone that does not bind the
 * KTX2 map otherwise draws those surfaces as a mirror of the sky, so the
 * ground disappears beside the asphalt. Zeroing the factor leaves a loaded ORM
 * map unchanged, because its metallic channel is already zero.
 */
object RoadShade {
    fun groundMetallic(name: String): Float? {
        return if (name == "grass" || name == "shoulder" || name.startsWith("asphalt")) 0f else null
    }
}

data class DriveBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun inside(widthPx: Int, heightPx: Int): Boolean {
        return left >= 0 && top >= 0 && right <= widthPx && bottom <= heightPx && width > 0 && height > 0
    }

    fun overlaps(other: DriveBox): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }
}

object DriveChrome {
    data class Layout(
        val guidance: DriveBox,
        val stop: DriveBox,
        val voice: DriveBox,
        val map: DriveBox,
        val garage: DriveBox,
        val night: DriveBox,
        val livery: DriveBox,
        val roadWindow: DriveBox,
        val speedSp: Float,
        val maneuverSp: Float,
        val detailSp: Float,
        val buttonSp: Float,
        val touchPx: Int
    ) {
        fun controls(): List<DriveBox> = listOf(stop, voice, map, garage, night, livery)
    }

    fun layout(widthPx: Int, heightPx: Int, density: Float): Layout {
        val scale = density.coerceAtLeast(0.75f)
        fun px(dp: Int): Int = (dp * scale).roundToInt().coerceAtLeast(1)
        val margin = px(12)
        val gap = px(8)
        val shortestDp = minOf(widthPx, heightPx) / scale
        val touch = px(if (shortestDp >= 600f || widthPx / scale >= 1000f) 64 else 56)
        val portrait = heightPx >= widthPx
        val speedSp = when {
            shortestDp >= 700f -> 44f
            shortestDp >= 480f -> 34f
            else -> 28f
        }
        val maneuverSp = if (shortestDp >= 600f) 22f else 18f
        val detailSp = if (shortestDp >= 600f) 18f else 16f
        val buttonSp = if (shortestDp >= 600f) 16f else 15f
        val labels = listOf(
            "Leállítás",
            "Hang kikapcsolva",
            "Vissza a térképre",
            "Garázs",
            "Éjszakai térkép",
            "Festés"
        )
        val widths = labels.map { label -> buttonWidth(label, buttonSp, scale, touch) }
        val columnWidth = widths.maxOrNull() ?: touch
        val stack = touch * labels.size + gap * (labels.size - 1)
        val sideLeft = widthPx - margin - columnWidth
        val useSide = !portrait &&
            stack + margin * 2 <= heightPx &&
            columnWidth < widthPx * 0.34f &&
            sideLeft - margin > px(280)
        val controls = if (useSide) {
            List(labels.size) { index ->
                val top = margin + index * (touch + gap)
                DriveBox(sideLeft, top, sideLeft + columnWidth, top + touch)
            }
        } else {
            packBottom(widths, widthPx, heightPx, margin, gap, touch)
        }
        val controlTop = controls.minOf { it.top }
        val guideCeiling = if (useSide) heightPx - margin else controlTop - gap
        val readout = (speedSp * scale).roundToInt() +
            (maneuverSp * scale).roundToInt() * 2 +
            (detailSp * scale).roundToInt() * 2 +
            px(28)
        val guideHeight = readout.coerceAtMost((heightPx * 0.36f).roundToInt())
            .coerceAtMost((guideCeiling - margin - gap).coerceAtLeast(px(88)))
        val guideWidth = when {
            portrait -> widthPx - margin * 2
            useSide -> (sideLeft - gap - margin).coerceAtMost(px(460))
            else -> minOf(px(460), (widthPx * 0.42f).toInt())
        }
        val guidance = DriveBox(margin, margin, margin + guideWidth, margin + guideHeight)
        val roadTop = guidance.bottom + gap
        val roadRight = if (useSide) sideLeft - gap else widthPx - margin
        val roadBottom = if (useSide) heightPx - margin else controlTop - gap
        val road = DriveBox(margin, roadTop, roadRight, roadBottom)
        return Layout(
            guidance,
            controls[0],
            controls[1],
            controls[2],
            controls[3],
            controls[4],
            controls[5],
            road,
            speedSp,
            maneuverSp,
            detailSp,
            buttonSp,
            touch
        )
    }

    private fun buttonWidth(label: String, textSp: Float, density: Float, touch: Int): Int {
        val text = (label.length * textSp * 0.62f * density).roundToInt()
        val padding = (20f * density).roundToInt()
        return maxOf(touch, text + padding)
    }

    private fun packBottom(
        widths: List<Int>,
        widthPx: Int,
        heightPx: Int,
        margin: Int,
        gap: Int,
        touch: Int
    ): List<DriveBox> {
        val limit = widthPx - margin * 2
        val rows = ArrayList<MutableList<Int>>()
        var current = ArrayList<Int>()
        var used = 0
        for (index in widths.indices) {
            val width = widths[index].coerceAtMost(limit)
            val next = if (current.isEmpty()) width else used + gap + width
            if (next > limit && current.isNotEmpty()) {
                rows.add(current)
                current = arrayListOf(index)
                used = width
            } else {
                current.add(index)
                used = if (current.size == 1) width else next
            }
        }
        if (current.isNotEmpty()) rows.add(current)
        val boxes = arrayOfNulls<DriveBox>(widths.size)
        for (rowFromBottom in rows.indices) {
            val top = heightPx - margin - touch - rowFromBottom * (touch + gap)
            var left = margin
            for (index in rows[rowFromBottom]) {
                val width = widths[index].coerceAtMost(limit)
                boxes[index] = DriveBox(left, top, left + width, top + touch)
                left += width + gap
            }
        }
        return boxes.map { it ?: DriveBox(0, 0, touch, touch) }
    }
}
