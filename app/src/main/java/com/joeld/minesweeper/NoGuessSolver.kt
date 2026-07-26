package com.joeld.minesweeper

import kotlin.math.max
import kotlin.math.min

class NoGuessSolver(
    private val game: MinesweeperGame,
    private val mode: GameMode
) {
    private val width = mode.width
    private val height = mode.height
    private val size = width * height

    fun isSolvableFrom(firstCol: Int, firstRow: Int): Boolean {
        val visible = BooleanArray(size)
        val flagged = BooleanArray(size)
        revealVirtual(firstCol, firstRow, visible)

        while (true) {
            var progress = false
            val constraints = collectConstraints(visible, flagged)

            constraints.forEach { constraint ->
                if (constraint.remaining == 0) {
                    constraint.cells.forEach { idx ->
                        if (!visible[idx] && !flagged[idx]) {
                            revealVirtual(idx % width, idx / width, visible)
                            progress = true
                        }
                    }
                } else if (constraint.remaining == constraint.cells.size) {
                    constraint.cells.forEach { idx ->
                        if (!flagged[idx]) {
                            flagged[idx] = true
                            progress = true
                        }
                    }
                }
            }

            if (!progress) {
                val deductions = frontierDeductions(constraints, visible, flagged)
                deductions.safe.forEach { idx ->
                    if (!visible[idx] && !flagged[idx]) {
                        revealVirtual(idx % width, idx / width, visible)
                        progress = true
                    }
                }
                deductions.mines.forEach { idx ->
                    if (!flagged[idx]) {
                        flagged[idx] = true
                        progress = true
                    }
                }
            }

            if (!progress) break
        }

        return visible.count { it } == size - mode.mines
    }

    private fun collectConstraints(visible: BooleanArray, flagged: BooleanArray): List<Constraint> {
        val constraints = mutableListOf<Constraint>()
        for (row in 0 until height) {
            for (col in 0 until width) {
                val idx = index(col, row)
                if (!visible[idx]) continue
                val cell = game.getCell(col, row)
                if (cell.adjacentMines <= 0) continue
                val hidden = mutableSetOf<Int>()
                var flaggedCount = 0
                neighbors(col, row).forEach { (nx, ny) ->
                    val nIdx = index(nx, ny)
                    if (flagged[nIdx]) {
                        flaggedCount++
                    } else if (!visible[nIdx]) {
                        hidden += nIdx
                    }
                }
                if (hidden.isNotEmpty()) {
                    val remaining = cell.adjacentMines - flaggedCount
                    if (remaining < 0 || remaining > hidden.size) return emptyList()
                    constraints += Constraint(hidden.toIntArray(), remaining)
                }
            }
        }
        return constraints
    }

    private fun frontierDeductions(
        constraints: List<Constraint>,
        visible: BooleanArray,
        flagged: BooleanArray
    ): Deductions {
        if (constraints.isEmpty()) return Deductions(emptySet(), emptySet())

        val frontier = linkedSetOf<Int>()
        constraints.forEach { frontier += it.cells.toList() }
        if (frontier.isEmpty()) return Deductions(emptySet(), emptySet())

        val frontierList = frontier.toList()
        if (frontierList.size > 24) {
            return componentDeductions(constraints, visible, flagged)
        }

        val assignments = enumerateConsistentAssignments(frontierList, constraints)
        if (assignments.isEmpty()) return Deductions(emptySet(), emptySet())
        return summarizeAssignments(frontierList, assignments)
    }

    private fun componentDeductions(
        constraints: List<Constraint>,
        visible: BooleanArray,
        flagged: BooleanArray
    ): Deductions {
        val groups = splitConstraintComponents(constraints)
        val safe = mutableSetOf<Int>()
        val mines = mutableSetOf<Int>()
        groups.forEach { component ->
            val frontierList = component.frontier.toList()
            if (frontierList.size > 18) return@forEach
            val assignments = enumerateConsistentAssignments(frontierList, component.constraints)
            if (assignments.isEmpty()) return@forEach
            val summary = summarizeAssignments(frontierList, assignments)
            safe += summary.safe
            mines += summary.mines
        }
        return Deductions(safe, mines)
    }

    private fun enumerateConsistentAssignments(
        frontierList: List<Int>,
        constraints: List<Constraint>
    ): List<BooleanArray> {
        val memberships = Array(frontierList.size) { mutableListOf<Int>() }
        val localRemaining = IntArray(constraints.size) { constraints[it].remaining }
        val localUnknown = IntArray(constraints.size) { constraints[it].cells.size }
        val frontierPosition = frontierList.withIndex().associate { it.value to it.index }
        constraints.forEachIndexed { cIndex, constraint ->
            constraint.cells.forEach { cell ->
                memberships[frontierPosition.getValue(cell)] += cIndex
            }
        }

        val results = mutableListOf<BooleanArray>()
        val current = BooleanArray(frontierList.size)

        fun search(position: Int) {
            if (position == frontierList.size) {
                if (localRemaining.all { it == 0 }) {
                    results += current.copyOf()
                }
                return
            }

            fun tryValue(isMine: Boolean) {
                val touched = memberships[position]
                val changedUnknown = IntArray(touched.size)
                val changedRemaining = IntArray(touched.size)
                var valid = true
                touched.forEachIndexed { idx, cIndex ->
                    changedUnknown[idx] = localUnknown[cIndex]
                    changedRemaining[idx] = localRemaining[cIndex]
                    localUnknown[cIndex]--
                    if (isMine) localRemaining[cIndex]--
                    if (localRemaining[cIndex] < 0 || localRemaining[cIndex] > localUnknown[cIndex]) {
                        valid = false
                    }
                }
                if (valid) {
                    current[position] = isMine
                    search(position + 1)
                }
                touched.forEachIndexed { idx, cIndex ->
                    localUnknown[cIndex] = changedUnknown[idx]
                    localRemaining[cIndex] = changedRemaining[idx]
                }
            }

            tryValue(false)
            tryValue(true)
        }

        search(0)
        return results
    }

    private fun summarizeAssignments(frontierList: List<Int>, assignments: List<BooleanArray>): Deductions {
        val safe = mutableSetOf<Int>()
        val mines = mutableSetOf<Int>()
        frontierList.indices.forEach { pos ->
            val allMine = assignments.all { it[pos] }
            val allSafe = assignments.all { !it[pos] }
            when {
                allMine -> mines += frontierList[pos]
                allSafe -> safe += frontierList[pos]
            }
        }
        return Deductions(safe, mines)
    }

    private fun splitConstraintComponents(constraints: List<Constraint>): List<Component> {
        val remaining = constraints.toMutableList()
        val result = mutableListOf<Component>()

        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val group = mutableListOf(seed)
            val frontier = seed.cells.toMutableSet()
            var changed = true
            while (changed) {
                changed = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (candidate.cells.any { it in frontier }) {
                        group += candidate
                        frontier += candidate.cells.toList()
                        iterator.remove()
                        changed = true
                    }
                }
            }
            result += Component(group, frontier)
        }
        return result
    }

    private fun revealVirtual(col: Int, row: Int, visible: BooleanArray) {
        val startIdx = index(col, row)
        if (visible[startIdx] || game.getCell(col, row).isMine) return
        val queue = ArrayDeque<Pair<Int, Int>>()
        visible[startIdx] = true
        if (game.getCell(col, row).adjacentMines != 0) return
        queue += col to row
        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            neighbors(cx, cy).forEach { (nx, ny) ->
                val idx = index(nx, ny)
                if (visible[idx] || game.getCell(nx, ny).isMine) return@forEach
                visible[idx] = true
                if (game.getCell(nx, ny).adjacentMines == 0) {
                    queue += nx to ny
                }
            }
        }
    }

    private fun neighbors(col: Int, row: Int): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>(8)
        for (y in max(0, row - 1)..min(height - 1, row + 1)) {
            for (x in max(0, col - 1)..min(width - 1, col + 1)) {
                if (x == col && y == row) continue
                result += x to y
            }
        }
        return result
    }

    private fun index(col: Int, row: Int): Int = row * width + col

    private data class Constraint(val cells: IntArray, val remaining: Int)
    private data class Deductions(val safe: Set<Int>, val mines: Set<Int>)
    private data class Component(val constraints: List<Constraint>, val frontier: Set<Int>)
}
