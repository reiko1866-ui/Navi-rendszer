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
 * Second lock on the ground. The driving materials are already non-metallic.
 * Keeping the runtime write means a future texture cannot turn the verge into
 * a mirror if its metallic channel fails to load.
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

    fun layout(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        topInsetPx: Int = 0,
        bottomInsetPx: Int = 0
    ): Layout {
        val scale = density.coerceAtLeast(0.75f)
        fun px(dp: Int): Int = (dp * scale).roundToInt().coerceAtLeast(1)
        val margin = px(12)
        val gap = px(8)
        val marginTop = margin + topInsetPx.coerceAtLeast(0)
        val marginBottom = margin + bottomInsetPx.coerceAtLeast(0)
        val shortestDp = minOf(widthPx, heightPx) / scale
        val touch = px(if (shortestDp >= 600f || widthPx / scale >= 1000f) 64 else 52)
        val portrait = heightPx >= widthPx
        val speedSp = when {
            shortestDp >= 700f -> 44f
            shortestDp >= 480f -> 34f
            else -> 28f
        }
        val maneuverSp = if (shortestDp >= 600f) 22f else 18f
        val detailSp = if (shortestDp >= 600f) 18f else 16f
        val buttonSp = if (shortestDp >= 600f) 16f else 14f
        val labels = listOf(
            "Leállítás",
            "Hang kikapcsolva",
            "Térkép",
            "Garázs",
            "Éjszakai térkép",
            "Festés"
        )
        val widths = labels.map { label -> buttonWidth(label, buttonSp, scale, touch) }
        val columnWidth = widths.maxOrNull() ?: touch
        val stack = touch * labels.size + gap * (labels.size - 1)
        val sideLeft = widthPx - margin - columnWidth
        val useSide = !portrait &&
            stack + marginTop + marginBottom <= heightPx &&
            columnWidth < widthPx * 0.34f &&
            sideLeft - margin > px(280)
        val splitWidth = ((widthPx * 0.28f).roundToInt()).coerceAtLeast(touch)
        val nameWidth = maxOf(
            buttonWidth("Festés", buttonSp, scale, touch),
            buttonWidth("Garázs", buttonSp, scale, touch)
        )
        val splitColumn = nameWidth.coerceAtMost(splitWidth).coerceAtLeast(touch)
        val split = portrait && !useSide && splitColumn * 2 + gap + margin * 2 < widthPx
        val controls = when {
            useSide -> List(labels.size) { index ->
                val top = marginTop + index * (touch + gap)
                DriveBox(sideLeft, top, sideLeft + columnWidth, top + touch)
            }
            split -> splitSides(splitColumn, widthPx, heightPx, margin, marginBottom, gap, touch)
            else -> packBottom(widths, widthPx, heightPx, margin, marginBottom, gap, touch)
        }
        val controlTop = controls.minOf { it.top }
        val guideCeiling = if (useSide || split) heightPx - marginBottom else controlTop - gap
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
        val guidance = DriveBox(margin, marginTop, margin + guideWidth, marginTop + guideHeight)
        val roadTop = guidance.bottom + gap
        val roadLeft = if (split) margin + splitColumn + gap else margin
        val roadRight = when {
            useSide -> sideLeft - gap
            split -> widthPx - margin - splitColumn - gap
            else -> widthPx - margin
        }
        val roadBottom = if (useSide || split) heightPx - marginBottom else controlTop - gap
        val road = DriveBox(roadLeft, roadTop, roadRight, roadBottom)
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
        // Uppercase Material buttons are wider than the raw glyph count.
        val text = (label.length * textSp * 0.70f * density).roundToInt()
        val padding = (24f * density).roundToInt()
        return maxOf(touch, text + padding)
    }

    private fun splitSides(
        column: Int,
        widthPx: Int,
        heightPx: Int,
        margin: Int,
        marginBottom: Int,
        gap: Int,
        touch: Int
    ): List<DriveBox> {
        val boxes = arrayOfNulls<DriveBox>(6)
        val stack = touch * 3 + gap * 2
        val top = heightPx - marginBottom - stack
        val right = widthPx - margin - column
        for (row in 0 until 3) {
            val y = top + row * (touch + gap)
            boxes[row] = DriveBox(margin, y, margin + column, y + touch)
            boxes[row + 3] = DriveBox(right, y, right + column, y + touch)
        }
        return boxes.map { it ?: DriveBox(0, 0, touch, touch) }
    }

    private fun packBottom(
        widths: List<Int>,
        widthPx: Int,
        heightPx: Int,
        margin: Int,
        marginBottom: Int,
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
            val top = heightPx - marginBottom - touch - rowFromBottom * (touch + gap)
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
