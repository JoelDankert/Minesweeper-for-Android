package com.joeld.minesweeper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    private companion object {
        const val ENDGAME_CAMERA_DURATION_MS = 420L
    }

    private data class CellVisualState(
        val revealed: Boolean,
        val flagged: Boolean,
        val isMine: Boolean,
        val adjacentMines: Int,
        val exploded: Boolean
    )

    private data class CellTransition(
        val index: Int,
        val previous: CellVisualState,
        val current: CellVisualState,
        val previousNeighborhood: Map<Int, CellVisualState>,
        val currentNeighborhood: Map<Int, CellVisualState>,
        val startedAtMs: Long,
        val durationMs: Long
    )

    private data class CameraAnimation(
        val startScale: Float,
        val startOffsetX: Float,
        val startOffsetY: Float,
        val targetScale: Float,
        val targetOffsetX: Float,
        val targetOffsetY: Float,
        val startedAtMs: Long,
        val durationMs: Long,
        val onFinished: (() -> Unit)?
    )

    private data class EdgeExpansion(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private enum class TransitionKind {
        REVEAL,
        FLAG,
        MORPH,
        SNAP
    }

    private enum class MergeGroup {
        HIDDEN,
        REVEALED,
        FLAGGED,
        EXPLODED
    }

    private enum class Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT
    }

    interface Listener {
        fun onCellTap(col: Int, row: Int)
        fun onCellLongPress(col: Int, row: Int)
    }

    private var game: MinesweeperGame? = null
    private var listener: Listener? = null
    private var interactionsEnabled = true
    private var palette = ThemeCatalog.resolve("sand", false)
    private var roundCorners = true
    private var mergeTiles = true
    private var fillGaps = true

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiddenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val revealedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val revealedMinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val explodedMinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val minePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flagPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellRect = RectF()
    private val shapeRect = RectF()
    private val roundRectPath = Path()
    private val numberPaints = (1..8).associateWith { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val flagIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_flag_material)?.mutate()
    private val lastCellStates = mutableMapOf<Int, CellVisualState>()
    private val previousCellStates = mutableMapOf<Int, CellVisualState>()
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
    private val longPressTouchSlop = touchSlop * 0.4f
    private var longPressDelayMs = 250L
    private var animationDurationMs = 126L
    private var cameraAnimation: CameraAnimation? = null

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

    fun setRoundCorners(enabled: Boolean) {
        roundCorners = enabled
        invalidate()
    }

    fun setMergeTiles(enabled: Boolean) {
        mergeTiles = enabled
        invalidate()
    }

    fun setFillGaps(enabled: Boolean) {
        fillGaps = enabled
        invalidate()
    }

    fun refresh(animationDurationScale: Float = 1f) {
        val current = game ?: return
        val currentStates = captureCurrentStates(current)
        val currentStateMap = currentStates.withIndex().associate { it.index to it.value }
        if (animationDurationMs > 0L) {
            val now = SystemClock.elapsedRealtime()
            val transitionDuration = (animationDurationMs * animationDurationScale.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
            val transitionIndexes = linkedSetOf<Int>()
            currentStates.forEachIndexed { index, state ->
                val previous = lastCellStates[index]
                if (previous != null && previous != state) {
                    transitionIndexes += index
                    neighborIndexes(index, current.width(), current.height()).forEach(transitionIndexes::add)
                }
            }
            val previousStateMap = lastCellStates.toMap()
            transitionIndexes.forEach { index ->
                val previous = previousStateMap[index] ?: return@forEach
                val currentState = currentStates[index]
                val previousNeighborhood = captureNeighborhoodStates(index, current.width(), current.height(), previousStateMap)
                val currentNeighborhood = captureNeighborhoodStates(index, current.width(), current.height(), currentStateMap)
                val existing = cellTransitions[index]
                val shouldKeepExisting = existing != null &&
                    existing.current == currentState &&
                    neighborhoodsEqual(existing.currentNeighborhood, currentNeighborhood)
                if (!shouldKeepExisting &&
                    (
                        previous != currentState ||
                            cornerSignature(index, current.width(), current.height(), previousStateMap) != cornerSignature(index, current.width(), current.height(), currentStateMap) ||
                            !neighborhoodsEqual(previousNeighborhood, currentNeighborhood)
                        )
                ) {
                    cellTransitions[index] = CellTransition(
                        index = index,
                        previous = previous,
                        current = currentState,
                        previousNeighborhood = previousNeighborhood,
                        currentNeighborhood = currentNeighborhood,
                        startedAtMs = now,
                        durationMs = transitionDuration
                    )
                }
            }
            previousCellStates.clear()
            previousCellStates.putAll(previousStateMap)
        } else {
            cellTransitions.clear()
            previousCellStates.clear()
        }
        lastCellStates.clear()
        currentStates.forEachIndexed { index, state -> lastCellStates[index] = state }
        invalidate()
    }

    fun performActionHaptic() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun animateToFullBoard(onFinished: (() -> Unit)? = null) {
        val current = game ?: run {
            onFinished?.invoke()
            return
        }
        if (width == 0 || height == 0 || baseCellSize == 0f) {
            resetCamera()
            onFinished?.invoke()
            return
        }
        val boardWidth = current.width() * baseCellSize
        val boardHeight = current.height() * baseCellSize
        cameraAnimation = CameraAnimation(
            startScale = scale,
            startOffsetX = offsetX,
            startOffsetY = offsetY,
            targetScale = 1f,
            targetOffsetX = (width - boardWidth) / 2f,
            targetOffsetY = (height - boardHeight) / 2f,
            startedAtMs = SystemClock.elapsedRealtime(),
            durationMs = ENDGAME_CAMERA_DURATION_MS,
            onFinished = onFinished
        )
        invalidate()
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

        val now = SystemClock.elapsedRealtime()
        val cameraStillAnimating = updateCameraAnimation(now)
        val cellSize = baseCellSize * scale
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
                val inset = (cellSize * 0.024f).coerceAtLeast(0.8f)
                val radius = cellSize * 0.22f

                val currentState = visualStateForCell(cell, current.explodedCellIndex() == index)
                val transition = cellTransitions[index]
                if (transition != null && animationDurationMs > 0L) {
                    val progress = ((now - transition.startedAtMs).toFloat() / transition.durationMs.toFloat()).coerceIn(0f, 1f)
                    if (progress < 1f) {
                        hasActiveAnimations = true
                        drawCellTransition(canvas, cellRect, inset, radius, transition, eased(progress), current.width(), current.height())
                    } else {
                        cellTransitions.remove(index)
                        drawCellState(canvas, cellRect, inset, radius, currentState, 255, index, current.width(), current.height(), lastCellStates)
                    }
                } else {
                    drawCellState(canvas, cellRect, inset, radius, currentState, 255, index, current.width(), current.height(), lastCellStates)
                }
            }
        }
        if (hasActiveAnimations || cameraStillAnimating) postInvalidateOnAnimation()
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

                if (abs(x - touchDownX) > longPressTouchSlop || abs(y - touchDownY) > longPressTouchSlop) {
                    removeCallbacks(longPressRunnable)
                }

                if (!touchMoved && (abs(x - touchDownX) > touchSlop || abs(y - touchDownY) > touchSlop)) {
                    touchMoved = true
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
            DrawableCompat.setTint(it, minePaint.color)
        }

        numberPaints.forEach { (_, paint) ->
            paint.color = themePalette.accent
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
    }

    private fun drawCellState(
        canvas: Canvas,
        baseRect: RectF,
        inset: Float,
        radius: Float,
        state: CellVisualState,
        alpha: Int,
        index: Int,
        boardWidth: Int,
        boardHeight: Int,
        states: Map<Int, CellVisualState>
    ) {
        val cornerRadii = cornerRadii(index, boardWidth, boardHeight, states, state, radius)
        val reverseCorners = reverseCorners(index, boardWidth, boardHeight, states, state, radius, inset)
        val rect = adjustedRect(baseRect, inset, edgeExpansion(index, boardWidth, boardHeight, states, state, inset))
        when {
            state.revealed -> {
                val fillPaint = when {
                    state.isMine && state.exploded -> explodedMinePaint
                    state.isMine && state.flagged -> flagPaint
                    state.isMine -> revealedMinePaint
                    else -> revealedPaint
                }
                drawShapeWithAlpha(canvas, rect, cornerRadii, reverseCorners, fillPaint, alpha)
            }
            state.flagged -> {
                drawShapeWithAlpha(canvas, rect, cornerRadii, reverseCorners, flagPaint, alpha)
                drawFlag(canvas, rect, alpha)
            }
            else -> {
                drawShapeWithAlpha(canvas, rect, cornerRadii, reverseCorners, hiddenPaint, alpha)
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
        baseRect: RectF,
        inset: Float,
        radius: Float,
        transition: CellTransition,
        progress: Float,
        boardWidth: Int,
        boardHeight: Int
    ) {
        val previousRadii = cornerRadii(transition.index, boardWidth, boardHeight, transition.previousNeighborhood, transition.previous, radius)
        val currentRadii = cornerRadii(transition.index, boardWidth, boardHeight, transition.currentNeighborhood, transition.current, radius)
        val previousReverseCorners = reverseCorners(transition.index, boardWidth, boardHeight, transition.previousNeighborhood, transition.previous, radius, inset)
        val currentReverseCorners = reverseCorners(transition.index, boardWidth, boardHeight, transition.currentNeighborhood, transition.current, radius, inset)
        val previousRect = adjustedRect(
            baseRect,
            inset,
            edgeExpansion(transition.index, boardWidth, boardHeight, transition.previousNeighborhood, transition.previous, inset)
        )
        val currentRect = adjustedRect(
            baseRect,
            inset,
            edgeExpansion(transition.index, boardWidth, boardHeight, transition.currentNeighborhood, transition.current, inset)
        )
        val morphRect = lerpRect(previousRect, currentRect, progress)
        val morphRadii = lerpRadii(previousRadii, currentRadii, progress)
        val morphReverseCorners = lerpCornerFixes(previousReverseCorners, currentReverseCorners, progress)
        val kind = transitionKind(transition.previous, transition.current)
        when (kind) {
            TransitionKind.REVEAL -> drawRevealTransition(canvas, previousRect, morphRect, transition.previous, transition.current, progress, previousRadii, morphRadii, previousReverseCorners, morphReverseCorners)
            TransitionKind.FLAG -> drawFlagTransition(canvas, morphRect, transition.previous, transition.current, progress, morphRadii, morphReverseCorners)
            TransitionKind.MORPH -> drawMorphTransition(canvas, morphRect, transition.current, morphRadii, morphReverseCorners)
            TransitionKind.SNAP -> drawCellState(canvas, baseRect, inset, radius, transition.current, 255, transition.index, boardWidth, boardHeight, lastCellStates)
        }
    }

    private fun transitionKind(previous: CellVisualState, current: CellVisualState): TransitionKind {
        return when {
            !previous.revealed && current.revealed -> TransitionKind.REVEAL
            !previous.revealed && !current.revealed && previous.flagged != current.flagged -> TransitionKind.FLAG
            previous == current -> TransitionKind.MORPH
            else -> TransitionKind.SNAP
        }
    }

    private fun drawRevealTransition(
        canvas: Canvas,
        previousRect: RectF,
        currentRect: RectF,
        previous: CellVisualState,
        current: CellVisualState,
        progress: Float,
        previousRadii: FloatArray,
        currentRadii: FloatArray,
        previousReverseCorners: FloatArray,
        currentReverseCorners: FloatArray
    ) {
        val morphRect = lerpRect(previousRect, currentRect, progress)
        val morphRadii = lerpRadii(previousRadii, currentRadii, progress)
        val morphReverseCorners = lerpCornerFixes(previousReverseCorners, currentReverseCorners, progress)
        val colorProgress = progress
        gridPaint.color = lerpColor(fillColorForState(previous), fillColorForState(current), colorProgress)
        drawShapeWithAlpha(canvas, morphRect, morphRadii, morphReverseCorners, gridPaint, 255)
        gridPaint.color = palette.grid
        drawCellContent(canvas, morphRect, current, fadeAlpha(progress))

        if (previous.flagged) {
            drawCellContent(canvas, morphRect, previous, fadeAlpha(1f - progress))
        }
        if (!previous.flagged) {
            drawShapeWithAlpha(canvas, morphRect, morphRadii, morphReverseCorners, hiddenPaint, fadeAlpha(1f - progress))
        }
    }

    private fun drawFlagTransition(
        canvas: Canvas,
        rect: RectF,
        previous: CellVisualState,
        current: CellVisualState,
        progress: Float,
        radii: FloatArray,
        reverseCorners: FloatArray
    ) {
        val startColor = if (previous.flagged) flagPaint.color else hiddenPaint.color
        val endColor = if (current.flagged) flagPaint.color else hiddenPaint.color
        gridPaint.color = lerpColor(startColor, endColor, progress)
        drawShapeWithAlpha(canvas, rect, radii, reverseCorners, gridPaint, 255)
        gridPaint.color = palette.grid

        drawCellContent(canvas, rect, previous, fadeAlpha(1f - progress))
        drawCellContent(canvas, rect, current, fadeAlpha(progress))
    }

    private fun drawMorphTransition(canvas: Canvas, rect: RectF, state: CellVisualState, radii: FloatArray, reverseCorners: FloatArray) {
        drawCellBase(canvas, rect, state, 255, radii, reverseCorners)
        drawCellContent(canvas, rect, state, 255)
    }

    private fun drawCellBase(canvas: Canvas, rect: RectF, state: CellVisualState, alpha: Int, radii: FloatArray, reverseCorners: FloatArray) {
        val fillPaint = fillPaintForState(state)
        drawShapeWithAlpha(canvas, rect, radii, reverseCorners, fillPaint, alpha)
    }

    private fun fillPaintForState(state: CellVisualState): Paint {
        return when {
            state.revealed && state.isMine && state.exploded -> explodedMinePaint
            state.revealed && state.isMine && state.flagged -> flagPaint
            state.revealed && state.isMine -> revealedMinePaint
            state.revealed -> revealedPaint
            state.flagged -> flagPaint
            else -> hiddenPaint
        }
    }

    private fun fillColorForState(state: CellVisualState): Int = fillPaintForState(state).color

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
        val horizontalPadding = width * 0.40f
        val verticalPadding = height * 0.40f

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

    private fun updateCameraAnimation(now: Long): Boolean {
        val animation = cameraAnimation ?: return false
        val rawProgress = ((now - animation.startedAtMs).toFloat() / animation.durationMs.toFloat()).coerceIn(0f, 1f)
        val progress = eased(rawProgress)
        scale = animation.startScale + (animation.targetScale - animation.startScale) * progress
        offsetX = animation.startOffsetX + (animation.targetOffsetX - animation.startOffsetX) * progress
        offsetY = animation.startOffsetY + (animation.targetOffsetY - animation.startOffsetY) * progress
        return if (rawProgress >= 1f) {
            scale = animation.targetScale
            offsetX = animation.targetOffsetX
            offsetY = animation.targetOffsetY
            cameraAnimation = null
            animation.onFinished?.invoke()
            false
        } else {
            true
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

    private fun drawShapeWithAlpha(canvas: Canvas, rect: RectF, radii: FloatArray, reverseCorners: FloatArray, paint: Paint, alpha: Int) {
        val originalAlpha = paint.alpha
        paint.alpha = alpha
        roundRectPath.reset()
        roundRectPath.addRoundRect(rect, radii, Path.Direction.CW)
        canvas.drawPath(roundRectPath, paint)
        paint.alpha = originalAlpha
        drawCornerCovers(canvas, rect, reverseCorners, alpha)
    }

    private fun drawCornerCovers(canvas: Canvas, rect: RectF, reverseCorners: FloatArray, alpha: Int) {
        if (alpha <= 0) return
        val originalAlpha = backgroundPaint.alpha
        backgroundPaint.alpha = alpha
        val overlap = max(0.75f, resources.displayMetrics.density * 0.5f)
        reverseCorners[0].takeIf { it > 0f }?.let { size ->
            canvas.drawRect(rect.left - overlap, rect.top - overlap, rect.left + size, rect.top + size, backgroundPaint)
        }
        reverseCorners[1].takeIf { it > 0f }?.let { size ->
            canvas.drawRect(rect.right - size, rect.top - overlap, rect.right + overlap, rect.top + size, backgroundPaint)
        }
        reverseCorners[2].takeIf { it > 0f }?.let { size ->
            canvas.drawRect(rect.right - size, rect.bottom - size, rect.right + overlap, rect.bottom + overlap, backgroundPaint)
        }
        reverseCorners[3].takeIf { it > 0f }?.let { size ->
            canvas.drawRect(rect.left - overlap, rect.bottom - size, rect.left + size, rect.bottom + overlap, backgroundPaint)
        }
        backgroundPaint.alpha = originalAlpha
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

    private fun lerpRadii(from: FloatArray, to: FloatArray, progress: Float): FloatArray {
        val clamped = progress.coerceIn(0f, 1f)
        return FloatArray(8) { index ->
            from[index] + (to[index] - from[index]) * clamped
        }
    }

    private fun lerpCornerFixes(from: FloatArray, to: FloatArray, progress: Float): FloatArray {
        val clamped = progress.coerceIn(0f, 1f)
        return FloatArray(4) { index ->
            from[index] + (to[index] - from[index]) * clamped
        }
    }

    private fun lerpRect(from: RectF, to: RectF, progress: Float): RectF {
        val clamped = progress.coerceIn(0f, 1f)
        return RectF(
            from.left + (to.left - from.left) * clamped,
            from.top + (to.top - from.top) * clamped,
            from.right + (to.right - from.right) * clamped,
            from.bottom + (to.bottom - from.bottom) * clamped
        )
    }

    private fun eased(progress: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        return 1f - (1f - clamped) * (1f - clamped)
    }

    private fun cornerRadii(
        index: Int,
        boardWidth: Int,
        boardHeight: Int,
        states: Map<Int, CellVisualState>,
        state: CellVisualState,
        radius: Float
    ): FloatArray {
        if (!roundCorners) {
            return FloatArray(8)
        }
        if (!mergeTiles) {
            return FloatArray(8) { radius }
        }
        val row = index / boardWidth
        val col = index % boardWidth
        val group = mergeGroup(state)
        val topSame = neighborMatches(col, row - 1, boardWidth, boardHeight, states, group)
        val rightSame = neighborMatches(col + 1, row, boardWidth, boardHeight, states, group)
        val bottomSame = neighborMatches(col, row + 1, boardWidth, boardHeight, states, group)
        val leftSame = neighborMatches(col - 1, row, boardWidth, boardHeight, states, group)
        val topLeft = if (topSame || leftSame) 0f else radius
        val topRight = if (topSame || rightSame) 0f else radius
        val bottomRight = if (bottomSame || rightSame) 0f else radius
        val bottomLeft = if (bottomSame || leftSame) 0f else radius

        return floatArrayOf(
            topLeft, topLeft,
            topRight, topRight,
            bottomRight, bottomRight,
            bottomLeft, bottomLeft
        )
    }

    private fun reverseCorners(
        index: Int,
        boardWidth: Int,
        boardHeight: Int,
        states: Map<Int, CellVisualState>,
        state: CellVisualState,
        radius: Float,
        inset: Float
    ): FloatArray {
        if (!fillGaps || (roundCorners && !mergeTiles)) return floatArrayOf(0f, 0f, 0f, 0f)
        val row = index / boardWidth
        val col = index % boardWidth
        val group = mergeGroup(state)
        val topSame = neighborMatches(col, row - 1, boardWidth, boardHeight, states, group)
        val rightSame = neighborMatches(col + 1, row, boardWidth, boardHeight, states, group)
        val bottomSame = neighborMatches(col, row + 1, boardWidth, boardHeight, states, group)
        val leftSame = neighborMatches(col - 1, row, boardWidth, boardHeight, states, group)
        val topLeftSame = neighborMatches(col - 1, row - 1, boardWidth, boardHeight, states, group)
        val topRightSame = neighborMatches(col + 1, row - 1, boardWidth, boardHeight, states, group)
        val bottomRightSame = neighborMatches(col + 1, row + 1, boardWidth, boardHeight, states, group)
        val bottomLeftSame = neighborMatches(col - 1, row + 1, boardWidth, boardHeight, states, group)
        val coverSize = inset + edgeOverlap(inset)
        return floatArrayOf(
            if (topSame && leftSame && !topLeftSame) coverSize else 0f,
            if (topSame && rightSame && !topRightSame) coverSize else 0f,
            if (bottomSame && rightSame && !bottomRightSame) coverSize else 0f,
            if (bottomSame && leftSame && !bottomLeftSame) coverSize else 0f
        )
    }

    private fun edgeExpansion(
        index: Int,
        boardWidth: Int,
        boardHeight: Int,
        states: Map<Int, CellVisualState>,
        state: CellVisualState,
        inset: Float
    ): EdgeExpansion {
        if (!fillGaps) return EdgeExpansion(0f, 0f, 0f, 0f)
        val row = index / boardWidth
        val col = index % boardWidth
        val group = mergeGroup(state)
        val overlap = edgeOverlap(inset)
        val connectedExpansion = inset
        return EdgeExpansion(
            left = if (neighborMatches(col - 1, row, boardWidth, boardHeight, states, group)) connectedExpansion + overlap else 0f,
            top = if (neighborMatches(col, row - 1, boardWidth, boardHeight, states, group)) connectedExpansion + overlap else 0f,
            right = if (neighborMatches(col + 1, row, boardWidth, boardHeight, states, group)) connectedExpansion + overlap else 0f,
            bottom = if (neighborMatches(col, row + 1, boardWidth, boardHeight, states, group)) connectedExpansion + overlap else 0f
        )
    }

    private fun adjustedRect(baseRect: RectF, inset: Float, expansion: EdgeExpansion): RectF {
        shapeRect.set(
            baseRect.left + inset - expansion.left,
            baseRect.top + inset - expansion.top,
            baseRect.right - inset + expansion.right,
            baseRect.bottom - inset + expansion.bottom
        )
        return RectF(shapeRect)
    }

    private fun edgeOverlap(inset: Float): Float {
        return max(resources.displayMetrics.density * 0.28f, inset * 0.08f)
    }

    private fun fillGapDistance(inset: Float): Float {
        return (inset - edgeOverlap(inset)).coerceAtLeast(0f)
    }

    private fun neighborMatches(
        col: Int,
        row: Int,
        boardWidth: Int,
        boardHeight: Int,
        states: Map<Int, CellVisualState>,
        group: MergeGroup
    ): Boolean {
        if (col !in 0 until boardWidth || row !in 0 until boardHeight) return false
        val neighborState = states[row * boardWidth + col] ?: return false
        return mergeGroup(neighborState) == group
    }

    private fun mergeGroup(state: CellVisualState): MergeGroup {
        return when {
            state.revealed && state.isMine && state.exploded -> MergeGroup.EXPLODED
            state.isMine && state.flagged -> MergeGroup.FLAGGED
            state.isMine -> MergeGroup.HIDDEN
            state.revealed -> MergeGroup.REVEALED
            state.flagged -> MergeGroup.FLAGGED
            else -> MergeGroup.HIDDEN
        }
    }

    private fun cornerSignature(index: Int, boardWidth: Int, boardHeight: Int, states: Map<Int, CellVisualState>): String {
        val state = states[index] ?: return ""
        val row = index / boardWidth
        val col = index % boardWidth
        val group = mergeGroup(state)
        return buildString(4) {
            append(if (neighborMatches(col, row - 1, boardWidth, boardHeight, states, group)) '1' else '0')
            append(if (neighborMatches(col + 1, row, boardWidth, boardHeight, states, group)) '1' else '0')
            append(if (neighborMatches(col, row + 1, boardWidth, boardHeight, states, group)) '1' else '0')
            append(if (neighborMatches(col - 1, row, boardWidth, boardHeight, states, group)) '1' else '0')
        }
    }

    private fun neighborIndexes(index: Int, boardWidth: Int, boardHeight: Int): List<Int> {
        val row = index / boardWidth
        val col = index % boardWidth
        val result = ArrayList<Int>(9)
        for (dy in -1..1) {
            for (dx in -1..1) {
                val x = col + dx
                val y = row + dy
                if (x !in 0 until boardWidth || y !in 0 until boardHeight) continue
                result += y * boardWidth + x
            }
        }
        return result
    }

    private fun captureNeighborhoodStates(
        index: Int,
        boardWidth: Int,
        boardHeight: Int,
        states: Map<Int, CellVisualState>
    ): Map<Int, CellVisualState> {
        val row = index / boardWidth
        val col = index % boardWidth
        val neighborhood = LinkedHashMap<Int, CellVisualState>(9)
        for (dy in -1..1) {
            for (dx in -1..1) {
                val x = col + dx
                val y = row + dy
                if (x !in 0 until boardWidth || y !in 0 until boardHeight) continue
                val neighborIndex = y * boardWidth + x
                val state = states[neighborIndex] ?: continue
                neighborhood[neighborIndex] = state
            }
        }
        return neighborhood
    }

    private fun neighborhoodsEqual(
        first: Map<Int, CellVisualState>,
        second: Map<Int, CellVisualState>
    ): Boolean {
        if (first.size != second.size) return false
        return first.all { (index, state) -> second[index] == state }
    }

    private fun captureCurrentStates(currentGame: MinesweeperGame): List<CellVisualState> {
        return List(currentGame.width() * currentGame.height()) { index ->
            val row = index / currentGame.width()
            val col = index % currentGame.width()
            val cell = currentGame.getCell(col, row)
            visualStateForCell(cell, currentGame.explodedCellIndex() == index)
        }
    }

    private fun visualStateForCell(cell: BoardCell, exploded: Boolean): CellVisualState {
        if (!cell.revealed) {
            return CellVisualState(
                revealed = false,
                flagged = cell.flagged,
                isMine = false,
                adjacentMines = 0,
                exploded = false
            )
        }
        return CellVisualState(
            revealed = true,
            flagged = cell.flagged,
            isMine = cell.isMine,
            adjacentMines = cell.adjacentMines,
            exploded = exploded
        )
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
