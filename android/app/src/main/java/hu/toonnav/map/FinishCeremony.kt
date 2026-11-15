package hu.toonnav.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * The podium opens only after a real arrival and a safe stop.
 * A timer alone does not start it.
 */
object FinishCeremony {
    const val stoppedMetersPerSecond = 0.8

    fun ready(arrived: Boolean, speedMetersPerSecond: Double?, hasSpeed: Boolean, fixLive: Boolean): Boolean {
        if (!arrived || !fixLive || !hasSpeed) return false
        val speed = speedMetersPerSecond ?: return false
        if (!speed.isFinite() || speed < 0.0) return false
        return speed < stoppedMetersPerSecond
    }
}

class FinishPodiumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = RectF()

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width < 2f || height < 2f) return
        paint.style = Paint.Style.FILL
        paint.color = 0x66000000
        canvas.drawRect(0f, 0f, width, height, paint)
        card.set(width * 0.12f, height * 0.28f, width * 0.88f, height * 0.72f)
        paint.color = 0xF014161C.toInt()
        canvas.drawRoundRect(card, 36f, 36f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = 0xFFFF3B12.toInt()
        canvas.drawRoundRect(card, 36f, 36f, paint)
        val flagTop = card.top + 28f
        val flagHeight = card.height() * 0.22f
        val cell = flagHeight / 4f
        var row = 0
        var y = flagTop
        while (y < flagTop + flagHeight - 1f) {
            var col = 0
            var x = card.left + 28f
            while (x < card.right - 28f) {
                paint.style = Paint.Style.FILL
                paint.color = if ((row + col) % 2 == 0) 0xFFF4F1EC.toInt() else 0xFF121418.toInt()
                canvas.drawRect(x, y, (x + cell).coerceAtMost(card.right - 28f), (y + cell).coerceAtMost(flagTop + flagHeight), paint)
                x += cell
                col += 1
            }
            y += cell
            row += 1
        }
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFF2A14.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = card.height() * 0.16f
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        canvas.drawText(context.getString(R.string.podium_title), card.centerX(), card.centerY() + paint.textSize * 0.2f, paint)
        paint.color = 0xFFF6F3EE.toInt()
        paint.textSize = card.height() * 0.07f
        canvas.drawText(context.getString(R.string.podium_arrived), card.centerX(), card.centerY() + card.height() * 0.24f, paint)
    }
}
