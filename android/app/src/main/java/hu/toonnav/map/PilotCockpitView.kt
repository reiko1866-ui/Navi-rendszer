package hu.toonnav.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
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
 * Live racing view drawn on the existing Android canvas. The road is the projected
 * route. The nose is the selected garage livery, anchored to the bottom of the screen.
 * This is not a separate 3D engine and it does not invent road geometry.
 */
class PilotCockpitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var strip = RoadStrip.EMPTY
    private var travelMeters = 0.0
    private var night = true
    private var body = 0xFF1F4E8C.toInt()
    private var side = 0xFF0B1E4A.toInt()
    private var accent = 0xFFF4F1EC.toInt()
    private var wing = 0xFF0E2A4A.toInt()
    private var numberText = ""
    private var holeOpen = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val hole = RectF()
    private val box = RectF()
    private val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private var skyShader: LinearGradient? = null
    private var roadShader: LinearGradient? = null
    private var shadeNight: Boolean? = null
    private var shadeHeight = 0

    init {
        isClickable = false
        isFocusable = false
        setBackgroundColor(0x00000000)
    }

    fun show(
        speedKmh: Int?,
        distanceMeters: Double?,
        instruction: String,
        arrowDegrees: Float,
        strip: RoadStrip,
        travelMeters: Double = 0.0,
        carId: String? = null,
        night: Boolean = true,
        startNumber: Int? = null
    ) {
        this.strip = strip
        this.travelMeters = if (travelMeters.isFinite() && travelMeters > 0.0) travelMeters else 0.0
        this.night = night
        val car = GarageFleet.shown(carId)
        body = car.body
        side = car.side
        accent = car.accent
        wing = car.wing
        numberText = startNumber?.toString().orEmpty()
        invalidate()
    }

    fun setMapWindow(open: Boolean, left: Float, top: Float, right: Float, bottom: Float) {
        holeOpen = open && right > left && bottom > top
        hole.set(left, top, right, bottom)
        invalidate()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = false

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width < 2f || height < 2f) return
        canvas.save()
        if (holeOpen) {
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            path.addRect(0f, 0f, width, height, Path.Direction.CW)
            path.addRect(hole, Path.Direction.CCW)
            canvas.clipPath(path)
        }
        drawSky(canvas, width, height)
        drawRoad(canvas, width, height)
        drawNose(canvas, width, height)
        canvas.restore()
    }

    private fun drawSky(canvas: Canvas, width: Float, height: Float) {
        ensureShaders(width, height)
        paint.shader = skyShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width, height * 0.42f, paint)
        paint.shader = null
    }

    private fun drawRoad(canvas: Canvas, width: Float, height: Float) {
        val road = strip
        if (!road.ready) return
        val top = height * 0.08f
        val bottom = height * 0.70f
        val near = road.center.maxOf { it.y }
        val far = road.center.minOf { it.y }
        val depth = (near - far).coerceAtLeast(0.04f)
        fun sx(point: ScreenPoint) = point.x * width
        fun sy(point: ScreenPoint): Float {
            val close = ((point.y - far) / depth).coerceIn(0f, 1f)
            return top + close * (bottom - top)
        }
        path.reset()
        path.moveTo(0f, height)
        path.lineTo(sx(road.left.first()), sy(road.left.first()))
        curve(path, road.left, ::sx, ::sy)
        path.lineTo(sx(road.right.last()), sy(road.right.last()))
        curve(path, road.right.asReversed(), ::sx, ::sy)
        path.lineTo(width, height)
        path.close()
        ensureShaders(width, height)
        paint.shader = roadShader
        paint.style = Paint.Style.FILL
        paint.pathEffect = CornerPathEffect(90f)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val phase = ((travelMeters / RoadRibbon.sampleMeters).toInt())
        for (index in 0 until road.center.size - 1) {
            val close = ((sy(road.center[index]) - top) / (bottom - top)).coerceIn(0f, 1f)
            val side = road.kerbSide.getOrElse(index) { 0 }
            if (side != 0) {
                val edgeA = if (side > 0) road.right[index] else road.left[index]
                val edgeB = if (side > 0) road.right[index + 1] else road.left[index + 1]
                paint.color = if ((index + phase) % 2 == 0) 0xFFE10600.toInt() else 0xFFF4F1EC.toInt()
                paint.strokeWidth = 4f + 14f * close
                canvas.drawLine(sx(edgeA), sy(edgeA), sx(edgeB), sy(edgeB), paint)
            }
            paint.color = 0x66F4F1EC
            paint.strokeWidth = 1.5f + 3f * close
            canvas.drawLine(sx(road.left[index]), sy(road.left[index]), sx(road.left[index + 1]), sy(road.left[index + 1]), paint)
            canvas.drawLine(sx(road.right[index]), sy(road.right[index]), sx(road.right[index + 1]), sy(road.right[index + 1]), paint)
            val groove = 0.18f + 0.08f * close
            groove(canvas, road.left[index], road.right[index], road.left[index + 1], road.right[index + 1], 0.34f, groove, ::sx, ::sy)
            groove(canvas, road.left[index], road.right[index], road.left[index + 1], road.right[index + 1], 0.66f, groove, ::sx, ::sy)
            if ((index + phase) % 2 == 0) {
                paint.color = 0xDDF4F1EC.toInt()
                paint.strokeWidth = 1.5f + 5f * close
                canvas.drawLine(sx(road.center[index]), sy(road.center[index]), sx(road.center[index + 1]), sy(road.center[index + 1]), paint)
            }
        }
    }

    private fun curve(
        path: Path,
        points: List<ScreenPoint>,
        sx: (ScreenPoint) -> Float,
        sy: (ScreenPoint) -> Float
    ) {
        if (points.size < 2) return
        for (index in 0 until points.size - 2) {
            val next = points[index + 1]
            val after = points[index + 2]
            path.quadTo(sx(next), sy(next), (sx(next) + sx(after)) * 0.5f, (sy(next) + sy(after)) * 0.5f)
        }
        val last = points.last()
        path.lineTo(sx(last), sy(last))
    }

    private fun groove(
        canvas: Canvas,
        leftA: ScreenPoint,
        rightA: ScreenPoint,
        leftB: ScreenPoint,
        rightB: ScreenPoint,
        across: Float,
        width: Float,
        sx: (ScreenPoint) -> Float,
        sy: (ScreenPoint) -> Float
    ) {
        paint.color = 0x55101418
        paint.strokeWidth = width
        canvas.drawLine(
            sx(leftA) + (sx(rightA) - sx(leftA)) * across,
            sy(leftA) + (sy(rightA) - sy(leftA)) * across,
            sx(leftB) + (sx(rightB) - sx(leftB)) * across,
            sy(leftB) + (sy(rightB) - sy(leftB)) * across,
            paint
        )
    }

    private fun drawNose(canvas: Canvas, width: Float, height: Float) {
        val top = height * 0.62f
        val bottom = height
        val mid = width * 0.5f
        val wingTop = top + (bottom - top) * 0.34f
        val wingBottom = top + (bottom - top) * 0.62f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        box.set(width * 0.06f, bottom - height * 0.03f, width * 0.94f, bottom + 8f)
        canvas.drawOval(box, paint)
        paint.color = wing
        canvas.drawRoundRect(width * 0.015f, wingTop - 6f, width * 0.07f, wingBottom + 22f, 10f, 10f, paint)
        canvas.drawRoundRect(width * 0.93f, wingTop - 6f, width * 0.985f, wingBottom + 22f, 10f, 10f, paint)
        val flaps = 3
        val flapGap = (wingBottom - wingTop) / flaps
        for (flap in 0 until flaps) {
            val flapTop = wingTop + flap * flapGap
            paint.color = if (flap == 0) accent else wing
            canvas.drawRoundRect(width * 0.05f, flapTop, width * 0.95f, flapTop + flapGap * 0.72f, 8f, 8f, paint)
            paint.color = 0x33000000
            canvas.drawRect(width * 0.08f, flapTop + flapGap * 0.5f, width * 0.92f, flapTop + flapGap * 0.68f, paint)
        }
        path.reset()
        path.moveTo(mid, top)
        path.quadTo(mid - width * 0.03f, top + (bottom - top) * 0.16f, mid - width * 0.045f, wingTop)
        path.lineTo(mid - width * 0.11f, bottom)
        path.lineTo(mid + width * 0.11f, bottom)
        path.lineTo(mid + width * 0.045f, wingTop)
        path.quadTo(mid + width * 0.03f, top + (bottom - top) * 0.16f, mid, top)
        path.close()
        paint.shader = LinearGradient(mid, top, mid, bottom, lighten(body), body, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = side
        canvas.drawRect(mid - width * 0.095f, wingTop, mid - width * 0.055f, bottom - 8f, paint)
        canvas.drawRect(mid + width * 0.055f, wingTop, mid + width * 0.095f, bottom - 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = 0x66FFFFFF
        canvas.drawLine(mid, top + 12f, mid, bottom - 10f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF101318.toInt()
        box.set(mid - width * 0.055f, top + (bottom - top) * 0.12f, mid + width * 0.055f, top + (bottom - top) * 0.30f)
        canvas.drawRoundRect(box, 14f, 14f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = accent
        canvas.drawRoundRect(box, 14f, 14f, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = condensed
        paint.textAlign = Paint.Align.CENTER
        paint.color = 0xFFF6F3EE.toInt()
        paint.textSize = min(width, height) * 0.022f
        canvas.drawText("TOONNAV", mid, box.centerY() + paint.textSize * 0.3f, paint)
        if (numberText.isNotEmpty()) {
            paint.color = accent
            paint.textSize = min(width, height) * 0.034f
            canvas.drawText(numberText, mid, bottom - height * 0.045f, paint)
        }
        paint.strokeWidth = 0f
    }

    private fun ensureShaders(width: Float, height: Float) {
        if (skyShader != null && shadeNight == night && shadeHeight == height.toInt()) return
        shadeNight = night
        shadeHeight = height.toInt()
        skyShader = if (night) {
            LinearGradient(0f, 0f, 0f, height * 0.42f, 0xFF070B14.toInt(), 0xFF243044.toInt(), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, 0f, 0f, height * 0.42f, 0xFF7EADD0.toInt(), 0xFFE4EEF4.toInt(), Shader.TileMode.CLAMP)
        }
        roadShader = LinearGradient(0f, height * 0.08f, 0f, height * 0.70f, 0xFF1A1D22.toInt(), 0xFF3C4148.toInt(), Shader.TileMode.CLAMP)
    }

    private fun lighten(color: Int): Int {
        val red = ((android.graphics.Color.red(color) * 0.55f) + 90f).toInt().coerceIn(0, 255)
        val green = ((android.graphics.Color.green(color) * 0.55f) + 90f).toInt().coerceIn(0, 255)
        val blue = ((android.graphics.Color.blue(color) * 0.55f) + 90f).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(red, green, blue)
    }
}
