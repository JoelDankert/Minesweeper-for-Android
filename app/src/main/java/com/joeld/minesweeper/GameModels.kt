package com.joeld.minesweeper

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

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
    val showMineDensity: Boolean = false,
    val mineDensityMinFade: Float = 0.1f,
    val mineDensityMaxFade: Float = 0.3f,
    val roundCorners: Boolean = false,
    val mergeTiles: Boolean = false,
    val fillGaps: Boolean = true,
    val cordingEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val screenShakeEnabled: Boolean = true,
    val longPressDelayMs: Int = 250,
    val animationSpeedPercent: Int = 50,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledTheme: Boolean = false,
    val themeId: String = "sand",
    val languageMode: LanguageMode = LanguageMode.SYSTEM
) {
    fun nightMode(): Int {
        return when (themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
    }

    fun usesDarkPalette(context: Context): Boolean {
        return when (themeMode) {
            ThemeMode.SYSTEM -> {
                val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mask == Configuration.UI_MODE_NIGHT_YES
            }
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }

    fun usesAmoledPalette(context: Context): Boolean = amoledTheme && usesDarkPalette(context)
}

enum class LanguageMode(val id: String, val label: String, val languageTag: String?) {
    SYSTEM("system", "System", null),
    ENGLISH("en", "English", "en"),
    GERMAN("de", "Deutsch", "de"),
    SPANISH("es", "Español", "es"),
    FRENCH("fr", "Français", "fr");

    fun localeList(): LocaleListCompat {
        return languageTag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
    }

    fun previewContext(context: Context): Context {
        val tag = languageTag ?: systemPreviewLanguageTag()
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(config)
    }

    companion object {
        private val supportedPreviewLanguageTags = values().mapNotNull { it.languageTag }.toSet()

        fun fromId(id: String?): LanguageMode = values().firstOrNull { it.id == id } ?: SYSTEM

        private fun systemPreviewLanguageTag(): String {
            val systemLocale = Resources.getSystem().configuration.locales.get(0)
            return systemLocale.language.takeIf { it in supportedPreviewLanguageTags } ?: "en"
        }
    }
}

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun nightMode(): Int {
        return when (this) {
            SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
    }

    companion object {
        fun fromId(id: String?): ThemeMode = when (id) {
            "amoled" -> DARK
            else -> values().firstOrNull { it.id == id } ?: SYSTEM
        }
    }
}

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

data class RecentGameEntry(
    val record: RecentGameRecord,
    val modeName: String,
    val modeMeta: String
)
