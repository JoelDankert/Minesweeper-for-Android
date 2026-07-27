package com.joeld.minesweeper

data class GameMode(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val mines: Int,
    val noGuess: Boolean,
    val noFlagMode: Boolean = false
)

data class AppSettings(
    val flagModeDefault: Boolean = false,
    val showInputToggle: Boolean = true,
    val showTopClears: Boolean = true,
    val mergeTiles: Boolean = true,
    val fillGaps: Boolean = true,
    val cordingEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val longPressDelayMs: Int = 250,
    val animationSpeedPercent: Int = 50,
    val darkTheme: Boolean = false,
    val themeId: String = "sand"
)

enum class InputMode {
    REVEAL,
    FLAG
}

enum class GameState {
    READY,
    RUNNING,
    WON,
    LOST
}

data class BoardCell(
    var isMine: Boolean = false,
    var adjacentMines: Int = 0,
    var revealed: Boolean = false,
    var flagged: Boolean = false
)

data class RevealResult(
    val changed: Boolean,
    val exploded: Boolean = false,
    val won: Boolean = false,
    val boardGenerated: Boolean = false,
    val noGuessFailed: Boolean = false
)

data class ThemePalette(
    val id: String,
    val name: String,
    val background: Int,
    val panel: Int,
    val input: Int,
    val accent: Int,
    val ink: Int,
    val inkSoft: Int,
    val hiddenCell: Int,
    val revealedCell: Int,
    val grid: Int,
    val mine: Int,
    val flag: Int,
    val error: Int
)

data class GameProgress(
    val modeId: String,
    val state: GameState,
    val boardGenerated: Boolean,
    val revealedCount: Int,
    val flagsCount: Int,
    val elapsedSeconds: Int,
    val inputMode: InputMode,
    val explodedCellIndex: Int,
    val cells: List<BoardCell>
)

data class RecentGameRecord(
    val modeId: String,
    val won: Boolean,
    val elapsedSeconds: Int,
    val finishedAtEpochMs: Long
)
