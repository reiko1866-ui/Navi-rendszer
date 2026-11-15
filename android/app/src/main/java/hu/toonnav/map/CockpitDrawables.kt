package hu.toonnav.map

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

class SpeedRingDrawable : Drawable() {
    var night: Boolean = false
        set(value) {
            field = value
            invalidateSelf()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onLevelChange(level: Int): Boolean {
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val box = RectF(bounds)
        val radius = box.width().coerceAtMost(box.height()) / 2f
        fill.color = if (night) 0xF0101216.toInt() else 0xF216191E.toInt()
        canvas.drawCircle(box.centerX(), box.centerY(), radius, fill)
        val stroke = radius * 0.14f
        val oval = RectF(box)
        oval.inset(stroke * 1.35f, stroke * 1.35f)
        track.strokeWidth = stroke
        track.color = 0x2EFFFFFF
        arc.strokeWidth = stroke
        arc.color = 0xFFFF4A1A.toInt()
        canvas.drawArc(oval, 135f, 270f, false, track)
        val sweep = 270f * level.coerceIn(0, 10_000) / 10_000f
        if (sweep > 0.5f) canvas.drawArc(oval, 135f, sweep, false, arc)
        rim.strokeWidth = radius * 0.035f
        rim.color = if (night) 0x99FF6A32.toInt() else 0x66FFFFFF
        canvas.drawCircle(box.centerX(), box.centerY(), radius - rim.strokeWidth, rim)
        val index = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6F6F3EE.toInt()
            strokeWidth = radius * 0.035f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(box.centerX(), box.top + radius * 0.16f, box.centerX(), box.top + radius * 0.32f, index)
    }

    override fun getOutline(outline: Outline) {
        outline.setOval(bounds)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class CockpitPanelDrawable : Drawable() {
    var night: Boolean = false
        set(value) {
            field = value
            invalidateSelf()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clip = Path()

    override fun draw(canvas: Canvas) {
        val box = RectF(bounds)
        val radius = cornerRadius(box)
        fill.style = Paint.Style.FILL
        fill.color = if (night) 0xF20C0E12.toInt() else 0xF2181B20.toInt()
        canvas.drawRoundRect(box, radius, radius, fill)
        canvas.save()
        clip.reset()
        clip.addRoundRect(box, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        hatch.strokeWidth = 1.1f
        hatch.color = if (night) 0x14FFFFFF else 0x10FFFFFF
        val step = (box.height() * 0.18f).coerceIn(10f, 18f)
        var x = box.left - box.height()
        while (x < box.right + box.height()) {
            canvas.drawLine(x, box.bottom, x + box.height(), box.top, hatch)
            canvas.drawLine(x + box.height(), box.bottom, x, box.top, hatch)
            x += step
        }
        canvas.restore()
        stroke.strokeWidth = (box.height().coerceAtMost(box.width()) * 0.012f).coerceIn(1.5f, 3f)
        stroke.color = if (night) 0xCCFF4A1A.toInt() else 0x99FF6A32.toInt()
        val inset = stroke.strokeWidth / 2f
        val outer = RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset)
        canvas.drawRoundRect(outer, radius, radius, stroke)
        val pip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        pip.strokeWidth = (stroke.strokeWidth * 0.4f).coerceAtLeast(1f)
        pip.color = 0x66F6F3EE
        val gap = stroke.strokeWidth + 2.2f
        canvas.drawRoundRect(
            RectF(box.left + gap, box.top + gap, box.right - gap, box.bottom - gap),
            radius,
            radius,
            pip
        )
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadius(RectF(bounds)))
    }

    override fun onBoundsChange(bounds: Rect) {
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun cornerRadius(box: RectF): Float {
        return (box.height().coerceAtMost(box.width()) * 0.16f).coerceIn(18f, 42f)
    }
}
