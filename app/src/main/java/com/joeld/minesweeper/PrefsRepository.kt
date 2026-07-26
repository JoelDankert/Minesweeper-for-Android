package com.joeld.minesweeper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PrefsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("minesweeper_prefs", Context.MODE_PRIVATE)

    fun loadModes(): MutableList<GameMode> {
        val raw = prefs.getString(KEY_MODES, null)
        val parsed = raw?.let(::parseModes)
        return when {
            parsed.isNullOrEmpty() -> defaultModes().toMutableList()
            shouldMigrateLegacyDefaults(parsed) -> defaultModes().toMutableList().also(::saveModes)
            else -> parsed.toMutableList()
        }
    }

    fun saveModes(modes: List<GameMode>) {
        val array = JSONArray()
        modes.forEach { mode ->
            array.put(
                JSONObject().apply {
                    put("id", mode.id)
                    put("name", mode.name)
                    put("width", mode.width)
                    put("height", mode.height)
                    put("mines", mode.mines)
                    put("noGuess", mode.noGuess)
                    put("noFlagMode", mode.noFlagMode)
                }
            )
        }
        prefs.edit()
            .putString(KEY_MODES, array.toString())
            .putString(KEY_MODE_RECENCY, pruneModeRecency(modes))
            .apply()
    }

    fun loadSettings(): AppSettings {
        return AppSettings(
            flagModeDefault = prefs.getBoolean(KEY_FLAG_MODE_DEFAULT, false),
            showInputToggle = prefs.getBoolean(KEY_SHOW_INPUT_TOGGLE, true),
            showTopClears = prefs.getBoolean(KEY_SHOW_TOP_CLEARS, true),
            cordingEnabled = prefs.getBoolean(KEY_CORDING_ENABLED, true),
            vibrateEnabled = prefs.getBoolean(KEY_VIBRATE_ENABLED, true),
            longPressDelayMs = clampLongPressDelay(prefs.getInt(KEY_LONG_PRESS_DELAY_MS, 250)),
            animationSpeedPercent = clampAnimationSpeed(prefs.getInt(KEY_ANIMATION_SPEED_PERCENT, 50)),
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, false),
            themeId = prefs.getString(KEY_THEME_ID, "sand") ?: "sand"
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_FLAG_MODE_DEFAULT, settings.flagModeDefault)
            .putBoolean(KEY_SHOW_INPUT_TOGGLE, settings.showInputToggle)
            .putBoolean(KEY_SHOW_TOP_CLEARS, settings.showTopClears)
            .putBoolean(KEY_CORDING_ENABLED, settings.cordingEnabled)
            .putBoolean(KEY_VIBRATE_ENABLED, settings.vibrateEnabled)
            .putInt(KEY_LONG_PRESS_DELAY_MS, clampLongPressDelay(settings.longPressDelayMs))
            .putInt(KEY_ANIMATION_SPEED_PERCENT, clampAnimationSpeed(settings.animationSpeedPercent))
            .putBoolean(KEY_DARK_THEME, settings.darkTheme)
            .putString(KEY_THEME_ID, settings.themeId)
            .apply()
    }

    fun loadSelectedModeId(modes: List<GameMode>): String {
        val saved = prefs.getString(KEY_SELECTED_MODE, null)
        return modes.firstOrNull { it.id == saved }?.id ?: modes.first().id
    }

    fun saveSelectedModeId(modeId: String) {
        prefs.edit().putString(KEY_SELECTED_MODE, modeId).apply()
    }

    fun sortModesByRecency(modes: List<GameMode>): MutableList<GameMode> {
        val recency = loadModeRecency()
        return modes.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<GameMode>> { recency[it.value.id] ?: Long.MIN_VALUE }
                    .thenBy { it.index }
            )
            .map { it.value }
            .toMutableList()
    }

    fun markModeUsed(modeId: String, usedAtEpochMs: Long = System.currentTimeMillis()) {
        val recency = loadModeRecency().toMutableMap()
        recency[modeId] = usedAtEpochMs
        prefs.edit().putString(KEY_MODE_RECENCY, serializeModeRecency(recency)).apply()
    }

    fun createMode(
        name: String,
        width: Int,
        height: Int,
        mines: Int,
        noGuess: Boolean,
        noFlagMode: Boolean = false
    ): GameMode {
        return GameMode(
            id = UUID.randomUUID().toString(),
            name = name,
            width = width,
            height = height,
            mines = mines,
            noGuess = noGuess,
            noFlagMode = noFlagMode
        )
    }

    fun restoreDefaultModes() {
        val existingModes = loadModes()
        val currentDefaults = existingModes.filter { isDefaultModeId(it.id) }.associateBy { it.id }
        val changedDefaultIds = defaultModes()
            .filter { defaultMode -> currentDefaults[defaultMode.id] != defaultMode }
            .map { it.id }

        val mergedModes = buildList {
            addAll(defaultModes())
            addAll(existingModes.filterNot { isDefaultModeId(it.id) })
        }
        saveModes(mergedModes)
        changedDefaultIds.forEach(::clearModeData)
    }

    fun saveProgress(progress: GameProgress) {
        val cells = JSONArray()
        progress.cells.forEach { cell ->
            cells.put(
                JSONObject().apply {
                    put("mine", cell.isMine)
                    put("adj", cell.adjacentMines)
                    put("revealed", cell.revealed)
                    put("flagged", cell.flagged)
                }
            )
        }
        val obj = JSONObject().apply {
            put("modeId", progress.modeId)
            put("state", progress.state.name)
            put("boardGenerated", progress.boardGenerated)
            put("revealedCount", progress.revealedCount)
            put("flagsCount", progress.flagsCount)
            put("elapsedSeconds", progress.elapsedSeconds)
            put("inputMode", progress.inputMode.name)
            put("explodedCellIndex", progress.explodedCellIndex)
            put("cells", cells)
        }
        prefs.edit().putString(progressKey(progress.modeId), obj.toString()).apply()
    }

    fun loadProgress(modeId: String): GameProgress? {
        val raw = prefs.getString(progressKey(modeId), null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val array = obj.getJSONArray("cells")
            val cells = MutableList(array.length()) { index ->
                val cellObj = array.getJSONObject(index)
                BoardCell(
                    isMine = cellObj.getBoolean("mine"),
                    adjacentMines = cellObj.getInt("adj"),
                    revealed = cellObj.getBoolean("revealed"),
                    flagged = cellObj.getBoolean("flagged")
                )
            }
            GameProgress(
                modeId = obj.getString("modeId"),
                state = GameState.valueOf(obj.getString("state")),
                boardGenerated = obj.getBoolean("boardGenerated"),
                revealedCount = obj.getInt("revealedCount"),
                flagsCount = obj.getInt("flagsCount"),
                elapsedSeconds = obj.optInt("elapsedSeconds", 0),
                inputMode = InputMode.valueOf(obj.optString("inputMode", InputMode.REVEAL.name)),
                explodedCellIndex = obj.optInt("explodedCellIndex", -1),
                cells = cells
            )
        }.getOrNull()
    }

    fun clearProgress(modeId: String) {
        prefs.edit().remove(progressKey(modeId)).apply()
    }

    fun hasProgress(modeId: String): Boolean = prefs.contains(progressKey(modeId))

    fun clearRecentGames(modeId: String) {
        prefs.edit().remove(recentKey(modeId)).apply()
    }

    fun clearModeData(modeId: String) {
        prefs.edit()
            .remove(progressKey(modeId))
            .remove(recentKey(modeId))
            .apply()
    }

    fun appendRecentGame(record: RecentGameRecord) {
        val history = loadRecentGames(record.modeId).toMutableList()
        history.add(0, record)
        val trimmed = history.take(8)
        val array = JSONArray()
        trimmed.forEach {
            array.put(
                JSONObject().apply {
                    put("modeId", it.modeId)
                    put("won", it.won)
                    put("elapsedSeconds", it.elapsedSeconds)
                    put("finishedAtEpochMs", it.finishedAtEpochMs)
                }
            )
        }
        prefs.edit().putString(recentKey(record.modeId), array.toString()).apply()
    }

    fun loadRecentGames(modeId: String): List<RecentGameRecord> {
        val raw = prefs.getString(recentKey(modeId), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { index ->
                val obj = array.getJSONObject(index)
                RecentGameRecord(
                    modeId = obj.getString("modeId"),
                    won = obj.getBoolean("won"),
                    elapsedSeconds = obj.getInt("elapsedSeconds"),
                    finishedAtEpochMs = obj.getLong("finishedAtEpochMs")
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun parseModes(raw: String): List<GameMode>? {
        return runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { index ->
                val obj = array.getJSONObject(index)
                GameMode(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    width = obj.getInt("width"),
                    height = obj.getInt("height"),
                    mines = obj.getInt("mines"),
                    noGuess = obj.optBoolean("noGuess", false),
                    noFlagMode = obj.optBoolean("noFlagMode", false)
                )
            }
        }.getOrNull()
    }

    private fun defaultModes(): List<GameMode> {
        return listOf(
            GameMode("easy", "Easy", 8, 8, 8, true, false),
            GameMode("medium", "Medium", 12, 12, 25, true, false),
            GameMode("hard", "Hard", 16, 16, 40, true, false)
        )
    }

    fun isDefaultModeId(modeId: String): Boolean {
        return modeId == "easy" || modeId == "medium" || modeId == "hard"
    }

    private fun shouldMigrateLegacyDefaults(modes: List<GameMode>): Boolean {
        if (modes.size != 3) return false
        return modes == listOf(
            GameMode("easy", "Easy", 9, 9, 10, true, false),
            GameMode("medium", "Medium", 16, 16, 40, true, false),
            GameMode("hard", "Hard", 30, 16, 99, true, false)
        )
    }

    private companion object {
        const val KEY_MODES = "modes"
        const val KEY_SELECTED_MODE = "selected_mode"
        const val KEY_FLAG_MODE_DEFAULT = "flag_mode_default"
        const val KEY_SHOW_INPUT_TOGGLE = "show_input_toggle"
        const val KEY_SHOW_TOP_CLEARS = "show_top_clears"
        const val KEY_CORDING_ENABLED = "cording_enabled"
        const val KEY_VIBRATE_ENABLED = "vibrate_enabled"
        const val KEY_LONG_PRESS_DELAY_MS = "long_press_delay_ms"
        const val KEY_ANIMATION_SPEED_PERCENT = "animation_speed_percent"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_THEME_ID = "theme_id"
        const val KEY_MODE_RECENCY = "mode_recency"

        fun progressKey(modeId: String) = "progress_$modeId"
        fun recentKey(modeId: String) = "recent_$modeId"
    }

    private fun clampLongPressDelay(value: Int): Int {
        val clamped = value.coerceIn(50, 500)
        return ((clamped + 25) / 50) * 50
    }

    private fun clampAnimationSpeed(value: Int): Int {
        val clamped = value.coerceIn(0, 100)
        return ((clamped + 2) / 5) * 5
    }

    private fun loadModeRecency(): Map<String, Long> {
        val raw = prefs.getString(KEY_MODE_RECENCY, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.optLong(key, Long.MIN_VALUE))
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun serializeModeRecency(recency: Map<String, Long>): String {
        return JSONObject().apply {
            recency.forEach { (modeId, timestamp) ->
                put(modeId, timestamp)
            }
        }.toString()
    }

    private fun pruneModeRecency(modes: List<GameMode>): String {
        val validIds = modes.mapTo(mutableSetOf()) { it.id }
        val pruned = loadModeRecency().filterKeys(validIds::contains)
        return serializeModeRecency(pruned)
    }
}
