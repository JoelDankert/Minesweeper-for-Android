package com.joeld.minesweeper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class CellVisualState(
        val revealed: Boolean,
        val flagged: Boolean,
        val isMine: Boolean,
        val adjacentMines: Int,
        val exploded: Boolean
    )

    private data class CellTransition(
        val previous: CellVisualState,
        val current: CellVisualState,
        val startedAtMs: Long
    )

    private enum class TransitionKind {
        REVEAL,
        FLAG,
        SNAP
    }

    interface Listener {
        fun onCellTap(col: Int, row: Int)
        fun onCellLongPress(col: Int, row: Int)
    }

    private var game: MinesweeperGame? = null
    private var listener: Listener? = null
    private var interactionsEnabled = true
    private var palette = ThemeCatalog.resolve("sand", false)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiddenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val revealedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val revealedMinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val explodedMinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val minePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flagPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellRect = RectF()
    private val numberPaints = (1..8).associateWith { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val flagIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_flag_material)?.mutate()
    private val lastCellStates = mutableMapOf<Int, CellVisualState>()
    private val cellTransitions = mutableMapOf<Int, CellTransition>()

    private var scale = 1f
    private var minScale = 0.6f
    private var maxScale = 12f
    private var offsetX = 0f
    private var offsetY = 0f
    private var baseCellSize = 0f

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchMoved = false
    private var longPressTriggered = false
    private var downCell: Pair<Int, Int>? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var longPressDelayMs = 250L
    private var animationDurationMs = 126L

    private val longPressRunnable = Runnable {
        val cell = downCell ?: return@Runnable
        longPressTriggered = true
        listener?.onCellLongPress(cell.first, cell.second)
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val previousScale = scale
            scale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val worldX = (detector.focusX - offsetX) / previousScale
            val worldY = (detector.focusY - offsetY) / previousScale
            offsetX = detector.focusX - worldX * scale
            offsetY = detector.focusY - worldY * scale
            clampOffsets()
            invalidate()
            return true
        }
    }).apply {
        isQuickScaleEnabled = false
    }

    init {
        applyPalette(palette)
    }

    fun bind(game: MinesweeperGame, listener: Listener) {
        this.game = game
        this.listener = listener
        lastCellStates.clear()
        captureCurrentStates(game).forEachIndexed { index, state -> lastCellStates[index] = state }
        cellTransitions.clear()
        resetCamera()
        invalidate()
    }

    fun setInteractionsEnabled(enabled: Boolean) {
        interactionsEnabled = enabled
    }

    fun setLongPressDelayMs(delayMs: Int) {
        longPressDelayMs = delayMs.coerceIn(50, 500).toLong()
    }

    fun setAnimationSpeedPercent(speedPercent: Int) {
        val clamped = speedPercent.coerceIn(0, 100)
        animationDurationMs = if (clamped >= 100) 0L else 40L + (((100 - clamped) / 100f) * 520f).toLong()
    }

    fun setPalette(themePalette: ThemePalette) {
        palette = themePalette
        applyPalette(themePalette)
        invalidate()
    }

    fun refresh() {
        val current = game ?: return
        val currentStates = captureCurrentStates(current)
        if (animationDurationMs > 0L) {
            val now = SystemClock.elapsedRealtime()
            currentStates.forEachIndexed { index, state ->
                val previous = lastCellStates[index]
                if (previous != null && previous != state) {
                    cellTransitions[index] = CellTransition(previous, state, now)
                }
            }
        } else {
            cellTransitions.clear()
        }
        lastCellStates.clear()
        currentStates.forEachIndexed { index, state -> lastCellStates[index] = state }
        invalidate()
    }

    fun performActionHaptic() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun resetCamera() {
        val current = game ?: return
        val boardRatio = current.width().toFloat() / current.height().toFloat()
        val viewRatio = if (height == 0) 1f else width.toFloat() / height.toFloat()
        val usableWidth = width * 0.98f
        val usableHeight = height * 0.98f
        baseCellSize = if (boardRatio > viewRatio) usableWidth / current.width() else usableHeight / current.height()
        scale = 1f
        minScale = 0.45f
        maxScale = max(18f, 120f / min(current.width(), current.height()).toFloat())
        val boardWidth = current.width() * baseCellSize
        val boardHeight = current.height() * baseCellSize
        offsetX = (width - boardWidth) / 2f
        offsetY = (height - boardHeight) / 2f
        clampOffsets()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (game != null) resetCamera()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(backgroundPaint)
        val current = game ?: return
        if (baseCellSize == 0f) resetCamera()

        val cellSize = baseCellSize * scale
        val now = SystemClock.elapsedRealtime()
        var hasActiveAnimations = false
        for (row in 0 until current.height()) {
            for (col in 0 until current.width()) {
                val index = row * current.width() + col
                val left = offsetX + col * cellSize
                val top = offsetY + row * cellSize
                val right = left + cellSize
                val bottom = top + cellSize
                if (right < 0f || bottom < 0f || left > width || top > height) continue
                val cell = current.getCell(col, row)
                cellRect.set(left, top, right, bottom)
                val inset = (cellSize * 0.035f).coerceAtLeast(1f)
                cellRect.inset(inset, inset)
                val radius = cellSize * 0.22f

                val currentState = CellVisualState(
                    revealed = cell.revealed,
                    flagged = cell.flagged,
                    isMine = cell.isMine,
                    adjacentMines = cell.adjacentMines,
                    exploded = current.explodedCellIndex() == index
                )
                val transition = cellTransitions[index]
                if (transition != null && animationDurationMs > 0L) {
                    val progress = ((now - transition.startedAtMs).toFloat() / animationDurationMs.toFloat()).coerceIn(0f, 1f)
                    if (progress < 1f) {
                        hasActiveAnimations = true
                        drawCellTransition(canvas, cellRect, radius, transition, eased(progress))
                    } else {
                        cellTransitions.remove(index)
                        drawCellState(canvas, cellRect, radius, currentState, 255)
                    }
                } else {
                    drawCellState(canvas, cellRect, radius, currentState, 255)
                }
            }
        }
        if (hasActiveAnimations) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactionsEnabled) return false
        scaleDetector.onTouchEvent(event)
        val current = game ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                touchDownX = event.x
                touchDownY = event.y
                touchMoved = false
                longPressTriggered = false
                downCell = locateCell(event.x, event.y, current)
                postDelayed(longPressRunnable, longPressDelayMs)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPressRunnable)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index == -1) return true
                val x = event.getX(index)
                val y = event.getY(index)
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                if (!touchMoved && (abs(x - touchDownX) > touchSlop || abs(y - touchDownY) > touchSlop)) {
                    touchMoved = true
                    removeCallbacks(longPressRunnable)
                }

                if (touchMoved || scaleDetector.isInProgress) {
                    offsetX += dx
                    offsetY += dy
                    clampOffsets()
                    invalidate()
                }

                lastTouchX = x
                lastTouchY = y
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (!touchMoved && !longPressTriggered) {
                    locateCell(event.x, event.y, current)?.let { listener?.onCellTap(it.first, it.second) }
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyPalette(themePalette: ThemePalette) {
        backgroundPaint.color = themePalette.background
        hiddenPaint.color = themePalette.hiddenCell
        revealedPaint.color = themePalette.revealedCell
        revealedMinePaint.color = themePalette.hiddenCell
        explodedMinePaint.color = android.graphics.Color.parseColor("#D94B4B")
        gridPaint.color = themePalette.grid
        gridPaint.strokeWidth = resources.displayMetrics.density
        minePaint.color = themePalette.revealedCell
        flagPaint.color = if (isDarkColor(themePalette.background)) {
            android.graphics.Color.parseColor("#5E6368")
        } else {
            android.graphics.Color.parseColor("#C7CCD1")
        }
        flagIcon?.let {
            DrawableCompat.setTint(it, themePalette.background)
        }

        numberPaints.forEach { (_, paint) ->
            paint.color = themePalette.accent
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
    }

    private fun drawCellState(canvas: Canvas, rect: RectF, radius: Float, state: CellVisualState, alpha: Int) {
        when {
            state.revealed -> {
                val fillPaint = when {
                    state.isMine && state.exploded -> explodedMinePaint
                    state.isMine && state.flagged -> flagPaint
                    state.isMine -> revealedMinePaint
                    else -> revealedPaint
                }
                drawRoundRectWithAlpha(canvas, rect, radius, fillPaint, alpha)
            }
            state.flagged -> {
                drawRoundRectWithAlpha(canvas, rect, radius, flagPaint, alpha)
                drawFlag(canvas, rect, alpha)
            }
            else -> {
                drawRoundRectWithAlpha(canvas, rect, radius, hiddenPaint, alpha)
            }
        }

        if (state.revealed) {
            if (state.isMine) {
                drawMine(canvas, rect, alpha)
            } else if (state.adjacentMines > 0) {
                drawCenteredText(canvas, rect, state.adjacentMines, alpha)
            }
        }
    }

    private fun drawCellTransition(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        transition: CellTransition,
        progress: Float
    ) {
        when (transitionKind(transition.previous, transition.current)) {
            TransitionKind.REVEAL -> drawRevealTransition(canvas, rect, radius, transition.previous, transition.current, progress)
            TransitionKind.FLAG -> drawFlagTransition(canvas, rect, radius, transition.previous, transition.current, progress)
            TransitionKind.SNAP -> drawCellState(canvas, rect, radius, transition.current, 255)
        }
    }

    private fun transitionKind(previous: CellVisualState, current: CellVisualState): TransitionKind {
        return when {
            !previous.revealed && current.revealed -> TransitionKind.REVEAL
            !previous.revealed && !current.revealed && previous.flagged != current.flagged -> TransitionKind.FLAG
            else -> TransitionKind.SNAP
        }
    }

    private fun drawRevealTransition(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        previous: CellVisualState,
        current: CellVisualState,
        progress: Float
    ) {
        drawCellBase(canvas, rect, radius, current, 255)
        drawCellContent(canvas, rect, current, fadeAlpha(progress))

        drawRoundRectWithAlpha(canvas, rect, radius, hiddenPaint, fadeAlpha(1f - progress))
        drawCellContent(canvas, rect, previous, fadeAlpha(1f - progress))
    }

    private fun drawFlagTransition(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        previous: CellVisualState,
        current: CellVisualState,
        progress: Float
    ) {
        val startColor = if (previous.flagged) flagPaint.color else hiddenPaint.color
        val endColor = if (current.flagged) flagPaint.color else hiddenPaint.color
        gridPaint.color = lerpColor(startColor, endColor, progress)
        canvas.drawRoundRect(rect, radius, radius, gridPaint)
        gridPaint.color = palette.grid

        drawCellContent(canvas, rect, previous, fadeAlpha(1f - progress))
        drawCellContent(canvas, rect, current, fadeAlpha(progress))
    }

    private fun drawCellBase(canvas: Canvas, rect: RectF, radius: Float, state: CellVisualState, alpha: Int) {
        val fillPaint = when {
            state.revealed && state.isMine && state.exploded -> explodedMinePaint
            state.revealed && state.isMine && state.flagged -> flagPaint
            state.revealed && state.isMine -> revealedMinePaint
            state.revealed -> revealedPaint
            state.flagged -> flagPaint
            else -> hiddenPaint
        }
        drawRoundRectWithAlpha(canvas, rect, radius, fillPaint, alpha)
    }

    private fun drawCellContent(canvas: Canvas, rect: RectF, state: CellVisualState, alpha: Int) {
        if (alpha <= 0) return
        drawLayeredAlpha(canvas, rect, alpha) {
            when {
                state.revealed && state.isMine -> drawMine(canvas, rect)
                state.revealed && state.adjacentMines > 0 -> drawCenteredText(canvas, rect, state.adjacentMines)
                !state.revealed && state.flagged -> drawFlag(canvas, rect)
            }
        }
    }

    private fun locateCell(x: Float, y: Float, game: MinesweeperGame): Pair<Int, Int>? {
        val cellSize = baseCellSize * scale
        if (cellSize <= 0f) return null
        val col = ((x - offsetX) / cellSize).toInt()
        val row = ((y - offsetY) / cellSize).toInt()
        return if (col in 0 until game.width() && row in 0 until game.height()) col to row else null
    }

    private fun clampOffsets() {
        val current = game ?: return
        val cellSize = baseCellSize * scale
        val boardWidth = current.width() * cellSize
        val boardHeight = current.height() * cellSize
        val horizontalPadding = width * 0.18f
        val verticalPadding = height * 0.18f

        offsetX = if (boardWidth + horizontalPadding * 2 <= width) {
            (width - boardWidth) / 2f
        } else {
            offsetX.coerceIn(width - boardWidth - horizontalPadding, horizontalPadding)
        }

        offsetY = if (boardHeight + verticalPadding * 2 <= height) {
            (height - boardHeight) / 2f
        } else {
            offsetY.coerceIn(height - boardHeight - verticalPadding, verticalPadding)
        }
    }

    private fun drawMine(canvas: Canvas, rect: RectF, alpha: Int = 255) {
        val originalAlpha = minePaint.alpha
        minePaint.alpha = alpha
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = rect.width() * 0.16f
        canvas.drawCircle(cx, cy, radius, minePaint)
        val arm = rect.width() * 0.26f
        val stroke = rect.width() * 0.06f
        minePaint.strokeWidth = stroke
        canvas.drawLine(cx - arm, cy, cx + arm, cy, minePaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, minePaint)
        canvas.drawLine(cx - arm * 0.7f, cy - arm * 0.7f, cx + arm * 0.7f, cy + arm * 0.7f, minePaint)
        canvas.drawLine(cx - arm * 0.7f, cy + arm * 0.7f, cx + arm * 0.7f, cy - arm * 0.7f, minePaint)
        minePaint.alpha = originalAlpha
    }

    private fun drawFlag(canvas: Canvas, rect: RectF, alpha: Int = 255) {
        val insetX = rect.width() * 0.14f
        val insetY = rect.height() * 0.12f
        val left = (rect.left + insetX).toInt()
        val top = (rect.top + insetY).toInt()
        val right = (rect.right - insetX).toInt()
        val bottom = (rect.bottom - rect.height() * 0.1f).toInt()
        flagIcon?.alpha = alpha
        flagIcon?.setBounds(left, top, right, bottom)
        flagIcon?.draw(canvas)
        flagIcon?.alpha = 255
    }

    private fun drawCenteredText(canvas: Canvas, rect: RectF, number: Int, alpha: Int = 255) {
        val paint = numberPaints.getValue(number)
        val originalAlpha = paint.alpha
        paint.alpha = alpha
        paint.textSize = rect.height() * 0.48f
        val y = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(number.toString(), rect.centerX(), y, paint)
        paint.alpha = originalAlpha
    }

    private fun drawRoundRectWithAlpha(canvas: Canvas, rect: RectF, radius: Float, paint: Paint, alpha: Int) {
        val originalAlpha = paint.alpha
        paint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.alpha = originalAlpha
    }

    private inline fun drawLayeredAlpha(canvas: Canvas, rect: RectF, alpha: Int, block: () -> Unit) {
        if (alpha <= 0) return
        if (alpha >= 255) {
            block()
            return
        }
        val checkpoint = canvas.saveLayerAlpha(rect.left, rect.top, rect.right, rect.bottom, alpha)
        block()
        canvas.restoreToCount(checkpoint)
    }

    private fun fadeAlpha(progress: Float): Int {
        return (progress.coerceIn(0f, 1f) * 255f).toInt()
    }

    private fun lerpColor(from: Int, to: Int, progress: Float): Int {
        val clamped = progress.coerceIn(0f, 1f)
        val a = android.graphics.Color.alpha(from) + ((android.graphics.Color.alpha(to) - android.graphics.Color.alpha(from)) * clamped).toInt()
        val r = android.graphics.Color.red(from) + ((android.graphics.Color.red(to) - android.graphics.Color.red(from)) * clamped).toInt()
        val g = android.graphics.Color.green(from) + ((android.graphics.Color.green(to) - android.graphics.Color.green(from)) * clamped).toInt()
        val b = android.graphics.Color.blue(from) + ((android.graphics.Color.blue(to) - android.graphics.Color.blue(from)) * clamped).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }

    private fun eased(progress: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        return 1f - (1f - clamped) * (1f - clamped)
    }

    private fun captureCurrentStates(currentGame: MinesweeperGame): List<CellVisualState> {
        return List(currentGame.width() * currentGame.height()) { index ->
            val row = index / currentGame.width()
            val col = index % currentGame.width()
            val cell = currentGame.getCell(col, row)
            CellVisualState(
                revealed = cell.revealed,
                flagged = cell.flagged,
                isMine = cell.isMine,
                adjacentMines = cell.adjacentMines,
                exploded = currentGame.explodedCellIndex() == index
            )
        }
    }

    private fun shift(color: Int, factor: Float): Int {
        val r = (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun isDarkColor(color: Int): Boolean {
        val luminance =
            (0.299f * android.graphics.Color.red(color) +
                0.587f * android.graphics.Color.green(color) +
                0.114f * android.graphics.Color.blue(color)) / 255f
        return luminance < 0.5f
    }
}
