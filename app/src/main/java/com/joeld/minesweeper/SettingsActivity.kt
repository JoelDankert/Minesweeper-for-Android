package com.joeld.minesweeper

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.joeld.minesweeper.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val STATE_THEME_ID = "state_theme_id"
        private const val STATE_DARK_THEME = "state_dark_theme"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: PrefsRepository
    private lateinit var palette: ThemePalette
    private var settings = AppSettings()
    private var themeId = "sand"
    private var previewDarkTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = PrefsRepository(this)
        settings = repository.loadSettings()
        themeId = savedInstanceState?.getString(STATE_THEME_ID) ?: settings.themeId
        previewDarkTheme = savedInstanceState?.getBoolean(STATE_DARK_THEME) ?: settings.darkTheme
        AppCompatDelegate.setDefaultNightMode(
            if (previewDarkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        palette = ThemeCatalog.resolve(themeId, previewDarkTheme)

        setupInsets()
        bindValues()
        binding.backButton.setOnClickListener { finish() }
        binding.themeRow.setOnClickListener { cycleTheme() }
        binding.darkTheme.setOnCheckedChangeListener { _, _ -> refreshPreview() }
        binding.advancedRow.setOnClickListener {
            startActivity(Intent(this, AdvancedSettingsActivity::class.java))
        }
        binding.applyButton.setOnClickListener { applySettings() }
    }

    private fun setupInsets() {
        val topPadding = binding.topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(20.dp, topPadding + bars.top, 20.dp, 8.dp)
            binding.content.setPadding(20.dp, 8.dp, 20.dp, 20.dp + bars.bottom)
            insets
        }
    }

    private fun bindValues() {
        binding.flagModeDefault.isChecked = settings.flagModeDefault
        binding.enableCording.isChecked = settings.cordingEnabled
        binding.vibrateEnabled.isChecked = settings.vibrateEnabled
        if (binding.darkTheme.isChecked != previewDarkTheme) {
            binding.darkTheme.isChecked = previewDarkTheme
        }
        refreshPreview()
    }

    private fun cycleTheme() {
        val ids = ThemeCatalog.themeIds()
        val nextIndex = (ids.indexOf(themeId) + 1) % ids.size
        themeId = ids[nextIndex]
        refreshPreview()
    }

    private fun applySettings() {
        val latest = repository.loadSettings()
        val next = latest.copy(
            flagModeDefault = binding.flagModeDefault.isChecked,
            cordingEnabled = binding.enableCording.isChecked,
            vibrateEnabled = binding.vibrateEnabled.isChecked,
            darkTheme = binding.darkTheme.isChecked,
            themeId = themeId
        )
        repository.saveSettings(next)
        settings = next
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_THEME_ID, themeId)
        outState.putBoolean(STATE_DARK_THEME, binding.darkTheme.isChecked)
    }

    private fun refreshPreview() {
        previewDarkTheme = binding.darkTheme.isChecked
        palette = ThemeCatalog.resolve(themeId, previewDarkTheme)
        binding.themeValue.text = palette.name
        applyPalette()
    }

    private fun applyPalette() {
        val themeUnsaved = themeId != settings.themeId || binding.darkTheme.isChecked != settings.darkTheme

        binding.root.setBackgroundColor(palette.background)
        binding.topBar.setBackgroundColor(palette.background)
        binding.content.setBackgroundColor(palette.background)
        binding.titleText.setTextColor(palette.ink)
        binding.advancedText.setTextColor(palette.ink)
        binding.advancedValue.setTextColor(palette.inkSoft)
        binding.themeLabel.setTextColor(palette.ink)
        binding.themeValue.setTextColor(if (themeUnsaved) palette.accent else palette.inkSoft)
        listOf(
            binding.flagModeDefault,
            binding.enableCording,
            binding.vibrateEnabled,
            binding.darkTheme
        ).forEach {
            it.setTextColor(palette.ink)
            applySwitchPalette(it)
        }
        binding.themeRow.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.advancedRow.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.applyButton.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.accent)
        }
        binding.applyButton.setTextColor(palette.revealedCell)
        binding.backButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.panel)
        }
        binding.backButton.imageTintList = android.content.res.ColorStateList.valueOf(palette.ink)
    }

    private fun applySwitchPalette(switch: MaterialSwitch) {
        val thumbStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val trackStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        switch.thumbTintList = ColorStateList(
            thumbStates,
            intArrayOf(palette.accent, palette.input)
        )
        switch.trackTintList = ColorStateList(
            trackStates,
            intArrayOf(shift(palette.accent, 0.78f), shift(palette.panel, 0.92f))
        )
    }

    private fun shift(color: Int, factor: Float): Int {
        val r = (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(r, g, b)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
