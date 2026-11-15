package hu.toonnav.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Premium Formula cockpit. The carbon wheel is the approved frame. The red line, the speed
 * and the maneuver are drawn from the live route and the GPS fix. The corner map is the real
 * MapLibre view, placed above this layer by the activity.
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
    private var travelMeters = 0.0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private val frameDest = RectF()
    private val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private var frame: Bitmap? = null

    fun show(
        speedKmh: Int?,
        distanceMeters: Double?,
        instruction: String,
        arrowDegrees: Float,
        strip: RoadStrip,
        travelMeters: Double = 0.0
    ) {
        this.speedKmh = speedKmh
        distanceText = if (distanceMeters == null || !distanceMeters.isFinite()) "—" else ManeuverEvents.formatDistance(distanceMeters)
        this.instruction = instruction
        this.arrowDegrees = arrowDegrees
        this.strip = strip
        this.travelMeters = if (travelMeters.isFinite() && travelMeters > 0.0) travelMeters else 0.0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width < 2f || height < 2f) return
        val bitmap = cockpitFrame()
        if (bitmap != null) {
            cover(bitmap, width, height, frameDest)
            paint.shader = null
            paint.isFilterBitmap = true
            canvas.drawBitmap(bitmap, null, frameDest, paint)
        } else {
            fill(0xFF07080C.toInt())
            canvas.drawRect(0f, 0f, width, height, paint)
            frameDest.set(0f, 0f, width, height)
        }
        drawRoad(canvas)
        drawSpeed(canvas)
        drawManeuver(canvas)
    }

    private fun cockpitFrame(): Bitmap? {
        frame?.let { if (!it.isRecycled) return it }
        val decoded = BitmapFactory.decodeResource(resources, R.drawable.cockpit_f1_frame) ?: return null
        frame = decoded
        return decoded
    }

    private fun cover(bitmap: Bitmap, viewW: Float, viewH: Float, dest: RectF) {
        val scale = max(viewW / bitmap.width, viewH / bitmap.height)
        val drawnW = bitmap.width * scale
        val drawnH = bitmap.height * scale
        val left = (viewW - drawnW) / 2f
        val top = (viewH - drawnH) / 2f
        dest.set(left, top, left + drawnW, top + drawnH)
    }

    private fun x(fraction: Float) = frameDest.left + frameDest.width() * fraction

    private fun y(fraction: Float) = frameDest.top + frameDest.height() * fraction

    private fun drawRoad(canvas: Canvas) {
        path.reset()
        path.moveTo(x(0.0f), y(0.47f))
        path.lineTo(x(0.0f), y(0.04f))
        path.quadTo(x(0.50f), y(0.012f), x(1f), y(0.04f))
        path.lineTo(x(1f), y(0.47f))
        path.close()
        canvas.save()
        canvas.clipPath(path)
        val top = y(0.02f)
        val bottom = y(0.46f)
        val left = x(0f)
        val rightEdge = x(1f)
        fill(0xFF10141A.toInt())
        canvas.drawRect(left, top, rightEdge, bottom, paint)
        val road = strip
        if (road.ready) {
            val span = rightEdge - left
            val near = road.center.maxOf { it.y }
            val far = road.center.minOf { it.y }
            val depth = (near - far).coerceAtLeast(0.04f)
            fun sx(point: ScreenPoint) = left + point.x * span
            fun sy(point: ScreenPoint): Float {
                val close = ((point.y - far) / depth).coerceIn(0f, 1f)
                return top + close * (bottom - top)
            }
            fill(0xFF2A4630.toInt())
            canvas.drawRect(left, top, rightEdge, bottom, paint)
            path.reset()
            path.moveTo(sx(road.left.first()), sy(road.left.first()))
            for (point in road.left.drop(1)) path.lineTo(sx(point), sy(point))
            for (point in road.right.asReversed()) path.lineTo(sx(point), sy(point))
            path.close()
            fill(0xFF2A2E34.toInt())
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.BUTT
            for (index in 0 until road.left.size - 1) {
                paint.color = if (index % 2 == 0) 0xFFF4F1EC.toInt() else 0xFFE10600.toInt()
                paint.strokeWidth = 5f + 16f * ((sy(road.left[index]) - top) / (bottom - top)).coerceIn(0f, 1f)
                canvas.drawLine(sx(road.left[index]), sy(road.left[index]), sx(road.left[index + 1]), sy(road.left[index + 1]), paint)
                canvas.drawLine(sx(road.right[index]), sy(road.right[index]), sx(road.right[index + 1]), sy(road.right[index + 1]), paint)
            }
            racingLine(canvas, road.center, far, depth, ::sx, ::sy)
            movingMarks(canvas, road.center, ::sx, ::sy)
        }
        paint.shader = LinearGradient(0f, bottom - (bottom - top) * 0.28f, 0f, bottom, 0x00000000, 0xFF07080C.toInt(), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, bottom - (bottom - top) * 0.28f, rightEdge, bottom, paint)
        paint.shader = null
        canvas.restore()
    }

    private fun racingLine(
        canvas: Canvas,
        center: List<ScreenPoint>,
        far: Float,
        depth: Float,
        sx: (ScreenPoint) -> Float,
        sy: (ScreenPoint) -> Float
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (index in 0 until center.size - 1) {
            val close = ((center[index].y + center[index + 1].y) * 0.5f - far) / depth
            val nearness = close.coerceIn(0f, 1f)
            paint.color = 0x99FF1A10.toInt()
            paint.strokeWidth = 10f + 34f * nearness
            canvas.drawLine(sx(center[index]), sy(center[index]), sx(center[index + 1]), sy(center[index + 1]), paint)
            paint.color = 0xFFFF2A14.toInt()
            paint.strokeWidth = 4f + 16f * nearness
            canvas.drawLine(sx(center[index]), sy(center[index]), sx(center[index + 1]), sy(center[index + 1]), paint)
        }
    }

    private fun movingMarks(
        canvas: Canvas,
        center: List<ScreenPoint>,
        sx: (ScreenPoint) -> Float,
        sy: (ScreenPoint) -> Float
    ) {
        val shift = ((travelMeters / 4.0) % 2.0).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xCCF4F1EC.toInt()
        for (index in 0 until center.size - 1) {
            if ((index + shift) % 2 != 0) continue
            paint.strokeWidth = 2f + 6f * center[index].y
            canvas.drawLine(sx(center[index]), sy(center[index]), sx(center[index + 1]), sy(center[index + 1]), paint)
        }
    }

    private fun drawSpeed(canvas: Canvas) {
        box.set(x(0.40f), y(0.442f), x(0.74f), y(0.532f))
        fill(0xFF05060A.toInt())
        canvas.drawRoundRect(box, box.height() * 0.22f, box.height() * 0.22f, paint)
        val value = speedKmh?.toString() ?: "—"
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = condensed
        paint.textSize = box.height() * 0.62f
        paint.color = 0x55FF220F
        val baseline = box.centerY() + paint.textSize * 0.32f
        val numberX = box.centerX() - box.width() * 0.04f
        canvas.drawText(value, numberX, baseline, paint)
        paint.color = 0xFFFF2A14.toInt()
        canvas.drawText(value, numberX, baseline, paint)
        val numberWidth = paint.measureText(value)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = box.height() * 0.18f
        paint.color = 0xFFFFB08A.toInt()
        canvas.drawText("km/h", numberX + numberWidth * 0.54f, baseline - box.height() * 0.02f, paint)
    }

    private fun drawManeuver(canvas: Canvas) {
        box.set(x(0.338f), y(0.578f), x(0.662f), y(0.712f))
        fill(0xFF05060A.toInt())
        val radius = min(box.height(), box.width()) * 0.08f
        canvas.drawRoundRect(box, radius, radius, paint)
        stroke(0xFFFF3B12.toInt(), 2.5f)
        canvas.drawRoundRect(box, radius, radius, paint)
        val arrowX = box.left + box.width() * 0.24f
        val arrowY = box.centerY()
        canvas.save()
        canvas.rotate(arrowDegrees, arrowX, arrowY)
        fill(0xFFF4F1EC.toInt())
        path.reset()
        val head = box.height() * 0.22f
        path.moveTo(arrowX, arrowY - head * 1.15f)
        path.lineTo(arrowX - head * 0.62f, arrowY - head * 0.15f)
        path.lineTo(arrowX - head * 0.2f, arrowY - head * 0.15f)
        path.lineTo(arrowX - head * 0.2f, arrowY + head * 1.05f)
        path.lineTo(arrowX + head * 0.2f, arrowY + head * 1.05f)
        path.lineTo(arrowX + head * 0.2f, arrowY - head * 0.15f)
        path.lineTo(arrowX + head * 0.62f, arrowY - head * 0.15f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.restore()
        val textX = box.left + box.width() * 0.64f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = condensed
        paint.color = 0xFFF6F3EE.toInt()
        paint.textSize = box.height() * 0.28f
        canvas.drawText(distanceText, textX, box.centerY() - box.height() * 0.02f, paint)
        paint.color = 0xFFFFB08A.toInt()
        fit(instruction, box.width() * 0.46f, box.height() * 0.16f)
        canvas.drawText(instruction, textX, box.centerY() + box.height() * 0.24f, paint)
    }

    private fun fit(text: String, maxWidth: Float, start: Float) {
        paint.textSize = start
        if (text.isEmpty()) return
        while (paint.measureText(text) > maxWidth && paint.textSize > 10f) {
            paint.textSize *= 0.9f
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
