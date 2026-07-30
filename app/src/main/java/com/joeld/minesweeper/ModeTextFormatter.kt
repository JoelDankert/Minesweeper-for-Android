package com.joeld.minesweeper

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.util.Locale
import kotlin.math.roundToInt

object ModeTextFormatter {
    fun compact(context: Context, mode: GameMode, showMineDensity: Boolean): String {
        return compact(
            width = mode.width,
            height = mode.height,
            mines = mode.mines,
            noGuess = mode.noGuess,
            noFlagMode = mode.noFlagMode,
            showMineDensity = showMineDensity,
            noGuessLabel = context.getString(R.string.no_guess_short),
            noFlagLabel = context.getString(R.string.no_flag_short)
        )
    }

    fun compactStyled(
        context: Context,
        mode: GameMode,
        settings: AppSettings,
        palette: ThemePalette,
        baseTextColor: Int
    ): CharSequence {
        val text = compact(context, mode, settings.showMineDensity)
        return styleDensity(text, mode.width, mode.height, mode.mines, settings, palette, baseTextColor)
    }

    fun styleDensity(
        text: String,
        width: Int,
        height: Int,
        mines: Int,
        settings: AppSettings,
        palette: ThemePalette,
        baseTextColor: Int
    ): CharSequence {
        if (!settings.showMineDensity) return text
        val densityText = formatDensity(width, height, mines)
        val start = text.indexOf(densityText)
        if (start < 0) return text
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(densityColor(width, height, mines, settings, palette, baseTextColor)),
                start,
                start + densityText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    fun styleDensityInText(text: String, settings: AppSettings, palette: ThemePalette, baseTextColor: Int): CharSequence {
        if (!settings.showMineDensity) return text
        val match = Regex("""(\d+) x (\d+) · (\d+) ([0-9]+(?:\.[0-9]+)?)""").find(text) ?: return text
        val width = match.groupValues[1].toIntOrNull() ?: return text
        val height = match.groupValues[2].toIntOrNull() ?: return text
        val mines = match.groupValues[3].toIntOrNull() ?: return text
        val range = match.groups[4]?.range ?: return text
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(densityColor(width, height, mines, settings, palette, baseTextColor)),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    fun compact(
        width: Int,
        height: Int,
        mines: Int,
        noGuess: Boolean,
        noFlagMode: Boolean,
        showMineDensity: Boolean,
        noGuessLabel: String = "NG",
        noFlagLabel: String = "NF"
    ): String {
        val mineText = if (showMineDensity) {
            "$mines ${formatDensity(width, height, mines)}"
        } else {
            mines.toString()
        }
        val tags = listOfNotNull(
            noGuessLabel.takeIf { noGuess },
            noFlagLabel.takeIf { noFlagMode }
        ).joinToString(" ")
        val suffix = tags.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""
        return "$width x $height · $mineText$suffix"
    }

    fun parseMineInput(input: String, width: Int, height: Int): Int? {
        val trimmed = input.trim().replace(',', '.')
        if (trimmed.isEmpty()) return null
        val density = trimmed.toDoubleOrNull()
        return if (density != null && trimmed.contains('.') && density > 0.0 && density <= 1.0) {
            (width * height * density).roundToInt()
        } else {
            trimmed.toIntOrNull()
        }
    }

    private fun formatDensity(width: Int, height: Int, mines: Int): String {
        val cells = width * height
        if (cells <= 0) return "0"
        val density = mines.toDouble() / cells.toDouble()
        return String.format(Locale.US, "%.2f", density).trimEnd('0').trimEnd('.')
    }

    private fun densityColor(
        width: Int,
        height: Int,
        mines: Int,
        settings: AppSettings,
        palette: ThemePalette,
        baseTextColor: Int
    ): Int {
        val cells = width * height
        if (cells <= 0) return baseTextColor
        val density = mines.toFloat() / cells.toFloat()
        val min = settings.mineDensityMinFade.coerceIn(0f, 1f)
        val max = settings.mineDensityMaxFade.coerceIn(min + 0.01f, 1f)
        val amount = ((density - min) / (max - min)).coerceIn(0f, 1f)
        return Color.rgb(
            lerp(Color.red(baseTextColor), Color.red(palette.accent), amount),
            lerp(Color.green(baseTextColor), Color.green(palette.accent), amount),
            lerp(Color.blue(baseTextColor), Color.blue(palette.accent), amount)
        )
    }

    private fun lerp(start: Int, end: Int, amount: Float): Int {
        return (start + (end - start) * amount).roundToInt().coerceIn(0, 255)
    }
}
