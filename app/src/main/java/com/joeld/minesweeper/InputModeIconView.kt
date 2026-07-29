package com.joeld.minesweeper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

class InputModeIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    enum class Kind {
        MINE,
        FLAG
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val flagIcon = ContextCompat.getDrawable(context, R.drawable.ic_flag_material)?.mutate()

    var kind: Kind = Kind.MINE
        set(value) {
            field = value
            invalidate()
        }

    var iconColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = (width - paddingLeft - paddingRight).coerceAtMost(height - paddingTop - paddingBottom)
        val left = paddingLeft + (width - paddingLeft - paddingRight - size) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - size) / 2f
        rect.set(left, top, left + size, top + size)
        when (kind) {
            Kind.MINE -> drawMine(canvas, rect)
            Kind.FLAG -> drawFlag(canvas, rect)
        }
    }

    private fun drawMine(canvas: Canvas, rect: RectF) {
        paint.color = iconColor
        paint.style = Paint.Style.FILL
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = rect.width() * 0.16f
        canvas.drawCircle(cx, cy, radius, paint)
        val arm = rect.width() * 0.26f
        paint.strokeWidth = rect.width() * 0.06f
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
        canvas.drawLine(cx - arm * 0.7f, cy - arm * 0.7f, cx + arm * 0.7f, cy + arm * 0.7f, paint)
        canvas.drawLine(cx - arm * 0.7f, cy + arm * 0.7f, cx + arm * 0.7f, cy - arm * 0.7f, paint)
    }

    private fun drawFlag(canvas: Canvas, rect: RectF) {
        val icon = flagIcon ?: return
        DrawableCompat.setTint(icon, iconColor)
        val insetX = rect.width() * 0.14f
        val insetY = rect.height() * 0.12f
        icon.setBounds(
            (rect.left + insetX).toInt(),
            (rect.top + insetY).toInt(),
            (rect.right - insetX).toInt(),
            (rect.bottom - rect.height() * 0.1f).toInt()
        )
        icon.draw(canvas)
    }
}
