package com.joeld.minesweeper

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch

object InfoBubble {
    fun show(anchor: View, text: CharSequence, palette: ThemePalette) {
        val context = anchor.context
        val width = minOf(context.resources.displayMetrics.widthPixels - context.dp(40), context.dp(320))
        val bubble = TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(palette.ink)
            setLineSpacing(0f, 1.08f)
            setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(10))
            background = GradientDrawable().apply {
                cornerRadius = context.dpF(16f)
                setColor(palette.panel)
                setStroke(context.dp(1), palette.grid)
            }
            elevation = context.dpF(8f)
        }
        val popup = PopupWindow(
            bubble,
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = context.dpF(8f)
            setBackgroundDrawable(GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT) })
        }
        val root = anchor.rootView
        val visibleFrame = Rect()
        root.getWindowVisibleDisplayFrame(visibleFrame)

        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val margin = context.dp(8)
        val gap = context.dp(6)
        val bubbleHeight = bubble.measuredHeight
        val belowY = anchorLocation[1] + anchor.height + gap
        val aboveY = anchorLocation[1] - bubbleHeight - gap
        val y = when {
            belowY + bubbleHeight + margin <= visibleFrame.bottom -> belowY
            aboveY >= visibleFrame.top + margin -> aboveY
            else -> (visibleFrame.bottom - bubbleHeight - margin).coerceAtLeast(visibleFrame.top + margin)
        }
        val x = anchorLocation[0].coerceIn(
            visibleFrame.left + margin,
            visibleFrame.right - width - margin
        )

        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y)
    }

    fun attachTextTap(view: View, textProvider: () -> CharSequence, paletteProvider: () -> ThemePalette) {
        view.isClickable = true
        view.setOnClickListener {
            show(view, textProvider(), paletteProvider())
        }
    }

    fun attachSwitchTextTap(switch: MaterialSwitch, textProvider: () -> CharSequence, paletteProvider: () -> ThemePalette) {
        switch.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP && event.x < switch.width - switch.context.dp(96)) {
                show(view, textProvider(), paletteProvider())
                true
            } else {
                false
            }
        }
    }

    private fun android.content.Context.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun android.content.Context.dpF(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
