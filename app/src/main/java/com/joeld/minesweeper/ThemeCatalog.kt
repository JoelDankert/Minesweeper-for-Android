package com.joeld.minesweeper

import android.graphics.Color
import kotlin.math.abs

object ThemeCatalog {
    private data class ThemeSeed(
        val id: String,
        val name: String,
        val accent: Int
    )

    private val baseLight = ThemePalette(
        id = "sand",
        name = "Sand",
        background = Color.parseColor("#F7F5EF"),
        panel = Color.parseColor("#EFEBE2"),
        input = Color.parseColor("#FFFFFF"),
        accent = Color.parseColor("#7CA7C7"),
        ink = Color.parseColor("#20262B"),
        inkSoft = Color.parseColor("#69747C"),
        hiddenCell = Color.parseColor("#4F99D6"),
        revealedCell = Color.WHITE,
        grid = Color.parseColor("#DEE4E8"),
        mine = Color.parseColor("#20262B"),
        flag = Color.parseColor("#2F6F9F"),
        error = Color.parseColor("#C86A6A")
    )

    private val baseDark = ThemePalette(
        id = "sand",
        name = "Sand",
        background = Color.parseColor("#191B1D"),
        panel = Color.parseColor("#24282B"),
        input = Color.parseColor("#2D3236"),
        accent = Color.parseColor("#C6B58F"),
        ink = Color.parseColor("#F3F1EA"),
        inkSoft = Color.parseColor("#A7B0B6"),
        hiddenCell = Color.parseColor("#7A6740"),
        revealedCell = Color.parseColor("#202427"),
        grid = Color.parseColor("#373D41"),
        mine = Color.parseColor("#F3F1EA"),
        flag = Color.parseColor("#F0D88A"),
        error = Color.parseColor("#E08B81")
    )

    private val seeds = listOf(
        ThemeSeed("sand", "Sand", Color.parseColor("#C9A96A")),
        ThemeSeed("sage", "Sage", Color.parseColor("#90A784")),
        ThemeSeed("mist", "Mist", Color.parseColor("#7EAFCF")),
        ThemeSeed("rose", "Rose", Color.parseColor("#D7689B")),
        ThemeSeed("apricot", "Apricot", Color.parseColor("#F1A15D")),
        ThemeSeed("lagoon", "Lagoon", Color.parseColor("#35B7D6")),
        ThemeSeed("ember", "Ember", Color.parseColor("#C95A3C")),
        ThemeSeed("iris", "Iris", Color.parseColor("#786EE8")),
        ThemeSeed("citrus", "Citrus", Color.parseColor("#A9BF32"))
    )

    fun allNames(): List<String> = seeds.map { it.name }

    fun themeIds(): List<String> = seeds.map { it.id }

    fun resolve(themeId: String, dark: Boolean): ThemePalette {
        val seed = seeds.firstOrNull { it.id == themeId } ?: seeds.first()
        val base = if (dark) baseDark else baseLight
        val baseHue = hueOf(base.accent)
        val targetHue = hueOf(seed.accent)
        val hueShift = shortestHueDelta(baseHue, targetHue)

        return ThemePalette(
            id = seed.id,
            name = seed.name,
            background = shiftHue(base.background, hueShift),
            panel = shiftHue(base.panel, hueShift),
            input = shiftHue(base.input, hueShift),
            accent = shiftHue(base.accent, hueShift),
            ink = shiftHue(base.ink, hueShift),
            inkSoft = shiftHue(base.inkSoft, hueShift),
            hiddenCell = shiftHue(base.hiddenCell, hueShift),
            revealedCell = shiftHue(base.revealedCell, hueShift),
            grid = shiftHue(base.grid, hueShift),
            mine = shiftHue(base.mine, hueShift),
            flag = shiftHue(base.flag, hueShift),
            error = shiftHue(base.error, hueShift)
        )
    }

    private fun hueOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    private fun shortestHueDelta(from: Float, to: Float): Float {
        val direct = to - from
        return when {
            direct > 180f -> direct - 360f
            direct < -180f -> direct + 360f
            else -> direct
        }
    }

    private fun shiftHue(color: Int, delta: Float): Int {
        if (abs(delta) < 0.001f) return color
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] < 0.01f) return color
        hsv[0] = ((hsv[0] + delta) % 360f + 360f) % 360f
        return Color.HSVToColor(Color.alpha(color), hsv)
    }
}
