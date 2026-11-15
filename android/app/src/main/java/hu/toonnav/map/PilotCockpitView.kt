package hu.toonnav.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Formula cockpit drawn over the driving screen. The red line is the calculated route.
 * The corner map is the real MapLibre view, placed above this layer by the activity.
 */
class PilotCockpitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var speedKmh: Int? = null
    private var distanceText = "—"
    private var instruction = ""
    private var arrowDegrees = 0f
    private var strip = RoadStrip.EMPTY

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    fun show(speedKmh: Int?, distanceMeters: Double?, instruction: String, arrowDegrees: Float, strip: RoadStrip) {
        this.speedKmh = speedKmh
        distanceText = if (distanceMeters == null || !distanceMeters.isFinite()) "—" else ManeuverEvents.formatDistance(distanceMeters)
        this.instruction = instruction
        this.arrowDegrees = arrowDegrees
        this.strip = strip
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width < 2f || height < 2f) return
        val horizon = height * 0.10f
        val roadBottom = height * 0.40f
        drawSky(canvas, width, horizon)
        drawRoad(canvas, horizon, roadBottom, width)
        drawHalo(canvas, width, height, roadBottom)
        drawDash(canvas, width, height, roadBottom)
        drawSpeed(canvas, width, height)
        drawWheel(canvas, width, height)
    }

    private fun drawSky(canvas: Canvas, width: Float, horizon: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, horizon, 0xFFB7C0C8.toInt(), 0xFF8D98A4.toInt(), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width, horizon + 2f, paint)
        paint.shader = null
    }

    private fun drawRoad(canvas: Canvas, top: Float, bottom: Float, width: Float) {
        canvas.save()
        canvas.clipRect(0f, top, width, bottom)
        fill(0xFF3C6A34.toInt())
        canvas.drawRect(0f, top, width, bottom, paint)
        val road = strip
        if (!road.ready) {
            straightDeck(canvas, top, bottom, width)
        } else {
            val near = road.center.maxOf { it.y }
            val far = road.center.minOf { it.y }
            val depth = (near - far).coerceAtLeast(0.04f)
            fun sx(point: ScreenPoint) = point.x * width
            fun sy(point: ScreenPoint): Float {
                val close = ((point.y - far) / depth).coerceIn(0f, 1f)
                return top + close * close * (bottom - top)
            }
            deck(canvas, road.left, road.right, ::sx, ::sy)
            kerbs(canvas, road.left, ::sx, ::sy)
            kerbs(canvas, road.right, ::sx, ::sy)
            racingLine(canvas, road.center, far, depth, ::sx, ::sy)
        }
        paint.shader = LinearGradient(0f, bottom - (bottom - top) * 0.28f, 0f, bottom, 0x00000000, 0xCC07080C.toInt(), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, bottom - (bottom - top) * 0.28f, width, bottom, paint)
        paint.shader = null
        canvas.restore()
    }

    private fun straightDeck(canvas: Canvas, top: Float, bottom: Float, width: Float) {
        path.reset()
        path.moveTo(width * 0.46f, top)
        path.lineTo(width * 0.54f, top)
        path.lineTo(width * 0.92f, bottom)
        path.lineTo(width * 0.08f, bottom)
        path.close()
        fill(0xFF2A2E34.toInt())
        canvas.drawPath(path, paint)
        stroke(0xFFE8E4DC.toInt(), 5f)
        canvas.drawLine(width * 0.46f, top, width * 0.08f, bottom, paint)
        canvas.drawLine(width * 0.54f, top, width * 0.92f, bottom, paint)
    }

    private fun deck(
        canvas: Canvas,
        left: List<ScreenPoint>,
        right: List<ScreenPoint>,
        sx: (ScreenPoint) -> Float,
        sy: (ScreenPoint) -> Float
    ) {
        path.reset()
        path.moveTo(sx(left.first()), sy(left.first()))
        for (point in left.drop(1)) path.lineTo(sx(point), sy(point))
        for (point in right.asReversed()) path.lineTo(sx(point), sy(point))
        path.close()
        fill(0xFF24282E.toInt())
        canvas.drawPath(path, paint)
        stroke(0xFFD8D4CC.toInt(), 3f)
        trace(canvas, left, sx, sy)
        trace(canvas, right, sx, sy)
    }

    private fun kerbs(
        canvas: Canvas,
        edge: List<ScreenPoint>,
        sx: (ScreenPoint) -> Float,
        sy: (ScreenPoint) -> Float
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        for (index in 0 until edge.size - 1) {
            paint.color = if (index % 2 == 0) 0xFFF4F1EC.toInt() else 0xFFE10600.toInt()
            paint.strokeWidth = 8f + 22f * edge[index].y
            canvas.drawLine(sx(edge[index]), sy(edge[index]), sx(edge[index + 1]), sy(edge[index + 1]), paint)
        }
    }

    private fun racingLine(
        canvas: Canvas,
        center: List<ScreenPoint>,
        far: Float,
        depth: Float,
        sx: (ScreenPoint) -> Float,
        sy: (ScreenPoint) -> Float
    ) {
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        for (index in 0 until center.size - 1) {
            val close = ((center[index].y + center[index + 1].y) * 0.5f - far) / depth
            paint.color = 0xFFFF1E14.toInt()
            paint.strokeWidth = 5f + 28f * close.coerceIn(0f, 1f)
            canvas.drawLine(sx(center[index]), sy(center[index]), sx(center[index + 1]), sy(center[index + 1]), paint)
        }
    }

    private fun trace(
        canvas: Canvas,
        edge: List<ScreenPoint>,
        sx: (ScreenPoint) -> Float,
        sy: (ScreenPoint) -> Float
    ) {
        for (index in 0 until edge.size - 1) {
            canvas.drawLine(sx(edge[index]), sy(edge[index]), sx(edge[index + 1]), sy(edge[index + 1]), paint)
        }
    }

    private fun drawHalo(canvas: Canvas, width: Float, height: Float, roadBottom: Float) {
        val pillar = width * 0.075f
        val bar = height * 0.055f
        fill(0xFF12141A.toInt())
        canvas.drawRect(0f, 0f, pillar, roadBottom, paint)
        canvas.drawRect(width - pillar, 0f, width, roadBottom, paint)
        path.reset()
        path.moveTo(0f, bar * 1.15f)
        path.quadTo(width * 0.5f, 0f, width, bar * 1.15f)
        path.lineTo(width, bar * 1.15f + height * 0.028f)
        path.quadTo(width * 0.5f, height * 0.03f, 0f, bar * 1.15f + height * 0.028f)
        path.close()
        canvas.drawPath(path, paint)
        weave(canvas, 0f, 0f, pillar, roadBottom, 7f)
        weave(canvas, width - pillar, 0f, width, roadBottom, 7f)
        stroke(0xFFFF3B12.toInt(), 4f)
        canvas.drawLine(pillar, roadBottom, width - pillar, roadBottom, paint)
        stroke(0xFF3A3E48.toInt(), 3f)
        canvas.drawLine(width * 0.5f, bar, width * 0.5f, roadBottom * 0.72f, paint)
    }

    private fun drawDash(canvas: Canvas, width: Float, height: Float, roadBottom: Float) {
        fill(0xFF0C0E12.toInt())
        canvas.drawRect(0f, roadBottom, width, height, paint)
        weave(canvas, 0f, roadBottom, width, height, 9f)
        fill(0xFF1A1C22.toInt())
        canvas.drawRect(0f, roadBottom, width, roadBottom + height * 0.018f, paint)
        fill(0xFFFF3B12.toInt())
        canvas.drawRect(0f, roadBottom, width, roadBottom + 4f, paint)
    }

    private fun drawSpeed(canvas: Canvas, width: Float, height: Float) {
        val value = speedKmh?.toString() ?: "—"
        val cx = width * 0.50f
        val top = height * 0.425f
        box.set(width * 0.30f, top, width * 0.70f, height * 0.515f)
        fill(0xFF07080C.toInt())
        canvas.drawRoundRect(box, 18f, 18f, paint)
        stroke(0xFF2C3038.toInt(), 3f)
        canvas.drawRoundRect(box, 18f, 18f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = condensed
        paint.textSize = height * 0.072f
        paint.color = 0x55FF220F
        val baseline = height * 0.492f
        canvas.drawText(value, cx, baseline, paint)
        paint.color = 0xFFFF2A14.toInt()
        canvas.drawText(value, cx, baseline, paint)
        val numberWidth = paint.measureText(value)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = height * 0.018f
        paint.color = 0xFFFFB08A.toInt()
        canvas.drawText("km/h", cx + numberWidth * 0.52f, baseline - height * 0.006f, paint)
    }

    private fun drawWheel(canvas: Canvas, width: Float, height: Float) {
        val left = width * 0.04f
        val right = width * 0.96f
        val top = height * 0.535f
        val bottom = height * 0.975f
        path.reset()
        path.moveTo(left + width * 0.07f, top)
        path.quadTo(width * 0.5f, top - height * 0.012f, right - width * 0.07f, top)
        path.lineTo(right, top + height * 0.045f)
        path.lineTo(right, bottom - height * 0.04f)
        path.quadTo(right, bottom, right - width * 0.05f, bottom)
        path.lineTo(left + width * 0.05f, bottom)
        path.quadTo(left, bottom, left, bottom - height * 0.04f)
        path.lineTo(left, top + height * 0.045f)
        path.close()
        fill(0xFF16181E.toInt())
        canvas.drawPath(path, paint)
        canvas.save()
        canvas.clipPath(path)
        weave(canvas, left, top, right, bottom, 8f)
        canvas.restore()
        stroke(0xFF3A3F4A.toInt(), 5f)
        canvas.drawPath(path, paint)

        val gripTop = top + height * 0.07f
        val gripBottom = bottom - height * 0.035f
        fill(0xFF0C0E13.toInt())
        canvas.drawRoundRect(left + width * 0.025f, gripTop, left + width * 0.145f, gripBottom, 36f, 36f, paint)
        canvas.drawRoundRect(right - width * 0.145f, gripTop, right - width * 0.025f, gripBottom, 36f, 36f, paint)
        stroke(0xFF2A2E36.toInt(), 3f)
        canvas.drawRoundRect(left + width * 0.025f, gripTop, left + width * 0.145f, gripBottom, 36f, 36f, paint)
        canvas.drawRoundRect(right - width * 0.145f, gripTop, right - width * 0.025f, gripBottom, 36f, 36f, paint)

        drawLeds(canvas, width, top + height * 0.028f)
        drawButtons(canvas, width, height)
        drawScreen(canvas, width, height)
    }

    private fun drawLeds(canvas: Canvas, width: Float, y: Float) {
        val count = 15
        val span = width * 0.40f
        val start = width * 0.30f
        val gap = span / count
        val radius = min(gap * 0.28f, 9f)
        for (index in 0 until count) {
            val color = when {
                index < 6 -> 0xFF3DDC57.toInt()
                index < 10 -> 0xFFFFC400.toInt()
                else -> 0xFFFF2A14.toInt()
            }
            fill(color)
            canvas.drawCircle(start + gap * (index + 0.5f), y, radius, paint)
        }
    }

    private fun drawButtons(canvas: Canvas, width: Float, height: Float) {
        val leftX = width * 0.22f
        val rightX = width * 0.78f
        button(canvas, leftX, height * 0.62f, 0xFFF5C400.toInt(), "TC")
        button(canvas, leftX, height * 0.70f, 0xFFF5C400.toInt(), "DRS")
        button(canvas, leftX, height * 0.78f, 0xFF2F6BFF.toInt(), "MAP")
        button(canvas, rightX, height * 0.62f, 0xFFE10600.toInt(), "")
        button(canvas, rightX, height * 0.70f, 0xFFE10600.toInt(), "")
        button(canvas, rightX, height * 0.78f, 0xFF18A34A.toInt(), "K2")
        val row = height * 0.90f
        button(canvas, width * 0.30f, row, 0xFF18A34A.toInt(), "K1")
        button(canvas, width * 0.42f, row, 0xFF2F6BFF.toInt(), "N")
        button(canvas, width * 0.54f, row, 0xFF18A34A.toInt(), "K1")
        button(canvas, width * 0.66f, row, 0xFFE10600.toInt(), "BB")
    }

    private fun button(canvas: Canvas, x: Float, y: Float, color: Int, label: String) {
        val radius = min(width, height) * 0.028f
        fill(color)
        canvas.drawCircle(x, y, radius, paint)
        stroke(0xFF07080C.toInt(), 2f)
        canvas.drawCircle(x, y, radius, paint)
        if (label.isEmpty()) return
        paint.style = Paint.Style.FILL
        paint.color = 0xFF101218.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = condensed
        paint.textSize = radius * 0.72f
        canvas.drawText(label, x, y + radius * 0.26f, paint)
    }

    private fun drawScreen(canvas: Canvas, width: Float, height: Float) {
        val screen = RectF(width * 0.31f, height * 0.60f, width * 0.69f, height * 0.84f)
        fill(0xFF05060A.toInt())
        canvas.drawRoundRect(screen, 14f, 14f, paint)
        stroke(0xFFFF3B12.toInt(), 3f)
        canvas.drawRoundRect(screen, 14f, 14f, paint)
        val cx = screen.centerX()
        val arrowY = screen.top + screen.height() * 0.16f
        canvas.save()
        canvas.rotate(arrowDegrees, cx, arrowY + screen.height() * 0.12f)
        fill(0xFFF4F1EC.toInt())
        path.reset()
        val head = screen.height() * 0.16f
        path.moveTo(cx, arrowY)
        path.lineTo(cx - head * 0.55f, arrowY + head)
        path.lineTo(cx - head * 0.18f, arrowY + head)
        path.lineTo(cx - head * 0.18f, arrowY + head * 2.05f)
        path.lineTo(cx + head * 0.18f, arrowY + head * 2.05f)
        path.lineTo(cx + head * 0.18f, arrowY + head)
        path.lineTo(cx + head * 0.55f, arrowY + head)
        path.close()
        canvas.drawPath(path, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = condensed
        paint.color = 0xFFF6F3EE.toInt()
        paint.textSize = screen.height() * 0.16f
        canvas.drawText(distanceText, cx, screen.top + screen.height() * 0.68f, paint)
        paint.color = 0xFFFFB08A.toInt()
        fit(instruction, screen.width() * 0.86f, screen.height() * 0.09f)
        canvas.drawText(instruction, cx, screen.top + screen.height() * 0.88f, paint)
    }

    private fun weave(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, step: Float) {
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = 0x18FFFFFF
        var cursor = left - (bottom - top)
        while (cursor < right) {
            canvas.drawLine(cursor, bottom, cursor + (bottom - top), top, paint)
            cursor += step
        }
        paint.color = 0x10000000
        cursor = left
        while (cursor < right + (bottom - top)) {
            canvas.drawLine(cursor, top, cursor - (bottom - top), bottom, paint)
            cursor += step
        }
        canvas.restore()
        paint.shader = null
    }

    private fun fit(text: String, maxWidth: Float, start: Float) {
        paint.textSize = start
        if (text.isEmpty()) return
        while (paint.measureText(text) > maxWidth && paint.textSize > 11f) {
            paint.textSize *= 0.92f
        }
    }

    private fun fill(color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    private fun stroke(color: Int, width: Float) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = color
        paint.strokeWidth = width
    }
}
