package com.joeld.minesweeper

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MinesweeperGame(private val mode: GameMode, private val cordingEnabled: Boolean) {
    private val cells = MutableList(mode.width * mode.height) { BoardCell() }
    private val seedRandom = Random(System.nanoTime())
    private var explodedCellIndex = -1

    var state: GameState = GameState.READY
        private set

    var boardGenerated = false
        private set

    var revealedCount = 0
        private set

    var flagsCount = 0
        private set

    fun modeId(): String = mode.id

    fun width(): Int = mode.width

    fun height(): Int = mode.height

    fun mines(): Int = mode.mines

    fun remainingMines(): Int = mode.mines - flagsCount

    fun getCell(col: Int, row: Int): BoardCell = cells[index(col, row)]

    fun explodedCellIndex(): Int = explodedCellIndex

    fun importProgress(progress: GameProgress) {
        cells.indices.forEach { index ->
            val saved = progress.cells[index]
            val cell = cells[index]
            cell.isMine = saved.isMine
            cell.adjacentMines = saved.adjacentMines
            cell.revealed = saved.revealed
            cell.flagged = saved.flagged
        }
        state = progress.state
        boardGenerated = progress.boardGenerated
        revealedCount = progress.revealedCount
        flagsCount = progress.flagsCount
        explodedCellIndex = progress.explodedCellIndex
    }

    fun exportProgress(elapsedSeconds: Int, inputMode: InputMode): GameProgress {
        return GameProgress(
            modeId = mode.id,
            state = state,
            boardGenerated = boardGenerated,
            revealedCount = revealedCount,
            flagsCount = flagsCount,
            elapsedSeconds = elapsedSeconds,
            inputMode = inputMode,
            explodedCellIndex = explodedCellIndex,
            cells = cells.map {
                BoardCell(
                    isMine = it.isMine,
                    adjacentMines = it.adjacentMines,
                    revealed = it.revealed,
                    flagged = it.flagged
                )
            }
        )
    }

    fun reveal(col: Int, row: Int): RevealResult {
        if (!inBounds(col, row) || state == GameState.LOST || state == GameState.WON) {
            return RevealResult(changed = false)
        }

        if (!boardGenerated) {
            val generated = generateBoard(col, row)
            if (!generated) {
                clearBoard()
                state = GameState.READY
                boardGenerated = false
                return RevealResult(changed = false, noGuessFailed = true)
            }
            state = GameState.RUNNING
        }

        val cell = getCell(col, row)
        if (cell.flagged) return RevealResult(changed = false)

        if (cell.revealed) {
            if (cordingEnabled && cell.adjacentMines > 0) {
                val changed = chord(col, row)
                val won = if (state == GameState.RUNNING) checkWin() else false
                return RevealResult(changed = changed, won = won)
            }
            return RevealResult(changed = false)
        }

        val changed = revealInternal(col, row)
        if (cell.isMine) {
            state = GameState.LOST
            explodedCellIndex = index(col, row)
            revealAllMines()
            return RevealResult(changed = true, exploded = true, boardGenerated = true)
        }

        val won = checkWin()
        return RevealResult(changed = changed, won = won, boardGenerated = true)
    }

    fun toggleFlag(col: Int, row: Int): Boolean {
        if (mode.noFlagMode) return false
        if (!inBounds(col, row) || state == GameState.LOST || state == GameState.WON) return false
        val cell = getCell(col, row)
        if (cell.revealed) return false
        cell.flagged = !cell.flagged
        flagsCount += if (cell.flagged) 1 else -1
        return true
    }

    private fun generateBoard(firstCol: Int, firstRow: Int): Boolean {
        if (!mode.noGuess) {
            placeRandomBoard(firstCol, firstRow, seedRandom)
            calculateAdjacency()
            boardGenerated = true
            return true
        }

        val attempts = when {
            mode.width * mode.height <= 256 -> 2400
            else -> 1200
        }
        repeat(attempts) { attempt ->
            clearBoard()
            placeRandomBoard(firstCol, firstRow, Random(seedRandom.nextLong() + attempt))
            calculateAdjacency()
            if (NoGuessSolver(this, mode).isSolvableFrom(firstCol, firstRow)) {
                boardGenerated = true
                return true
            }
        }
        return false
    }

    private fun chord(col: Int, row: Int): Boolean {
        val cell = getCell(col, row)
        if (!cell.revealed || cell.adjacentMines == 0) return false
        val neighbors = neighbors(col, row)
        val flagged = neighbors.count { getCell(it.first, it.second).flagged }
        if (flagged != cell.adjacentMines) return false

        var changed = false
        neighbors.forEach { (nx, ny) ->
            val neighbor = getCell(nx, ny)
            if (!neighbor.flagged && !neighbor.revealed) {
                val result = reveal(nx, ny)
                changed = changed || result.changed
                if (state == GameState.LOST) {
                    return changed
                }
            }
        }
        return changed
    }

    private fun revealInternal(col: Int, row: Int): Boolean {
        val start = getCell(col, row)
        if (start.revealed || start.flagged) return false
        start.revealed = true
        revealedCount++
        if (start.isMine) return true
        if (start.adjacentMines != 0) return true

        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(col to row)
        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            neighbors(cx, cy).forEach { (nx, ny) ->
                val neighbor = getCell(nx, ny)
                if (neighbor.flagged || neighbor.revealed || neighbor.isMine) return@forEach
                neighbor.revealed = true
                revealedCount++
                if (neighbor.adjacentMines == 0) {
                    queue.add(nx to ny)
                }
            }
        }
        return true
    }

    private fun checkWin(): Boolean {
        if (state != GameState.RUNNING) return false
        val safeCells = mode.width * mode.height - mode.mines
        if (revealedCount == safeCells) {
            state = GameState.WON
            cells.forEach { cell ->
                if (cell.isMine && !cell.flagged) {
                    cell.flagged = true
                }
            }
            flagsCount = mode.mines
            revealAllMines()
            return true
        }
        return false
    }

    private fun revealAllMines() {
        cells.forEach { cell ->
            if (cell.isMine) cell.revealed = true
        }
    }

    private fun clearBoard() {
        cells.forEach {
            it.isMine = false
            it.adjacentMines = 0
            it.revealed = false
            it.flagged = false
        }
        revealedCount = 0
        flagsCount = 0
        explodedCellIndex = -1
    }

    private fun placeRandomBoard(firstCol: Int, firstRow: Int, random: Random) {
        val forbidden = mutableSetOf<Int>()
        for (y in max(0, firstRow - 1)..min(mode.height - 1, firstRow + 1)) {
            for (x in max(0, firstCol - 1)..min(mode.width - 1, firstCol + 1)) {
                forbidden += index(x, y)
            }
        }

        var placed = 0
        while (placed < mode.mines) {
            val candidate = random.nextInt(cells.size)
            if (candidate in forbidden || cells[candidate].isMine) continue
            cells[candidate].isMine = true
            placed++
        }
    }

    private fun calculateAdjacency() {
        for (row in 0 until mode.height) {
            for (col in 0 until mode.width) {
                val cell = getCell(col, row)
                cell.adjacentMines = if (cell.isMine) {
                    -1
                } else {
                    neighbors(col, row).count { (nx, ny) -> getCell(nx, ny).isMine }
                }
            }
        }
    }

    private fun neighbors(col: Int, row: Int): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>(8)
        for (y in max(0, row - 1)..min(mode.height - 1, row + 1)) {
            for (x in max(0, col - 1)..min(mode.width - 1, col + 1)) {
                if (x == col && y == row) continue
                result += x to y
            }
        }
        return result
    }

    private fun index(col: Int, row: Int): Int = row * mode.width + col

    private fun inBounds(col: Int, row: Int): Boolean {
        return col in 0 until mode.width && row in 0 until mode.height
    }
}
