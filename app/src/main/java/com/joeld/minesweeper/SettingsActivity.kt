package com.joeld.minesweeper

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.joeld.minesweeper.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val STATE_THEME_ID = "state_theme_id"
        private const val STATE_THEME_MODE = "state_theme_mode"
        private const val STATE_RESTORE_MODES_PENDING = "state_restore_modes_pending"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: PrefsRepository
    private lateinit var palette: ThemePalette
    private var settings = AppSettings()
    private var themeId = "sand"
    private var themeMode = ThemeMode.SYSTEM
    private var advancedSettings: AppSettings? = null
    private var restoreModesPending = false

    private val advancedLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val returnedSettings = AdvancedSettingsActivity.settingsFromIntent(
                    result.data,
                    advancedSettings ?: settings
                )
                advancedSettings = returnedSettings.takeIf(::advancedSettingsChanged)
                restoreModesPending = result.data?.getBooleanExtra(
                    AdvancedSettingsActivity.EXTRA_RESTORE_PENDING,
                    restoreModesPending
                ) ?: restoreModesPending
                refreshPreview()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = PrefsRepository(this)
        settings = repository.loadSettings()
        themeId = savedInstanceState?.getString(STATE_THEME_ID) ?: settings.themeId
        themeMode = savedInstanceState?.getString(STATE_THEME_MODE)?.let(ThemeMode::fromId) ?: settings.themeMode
        restoreModesPending = savedInstanceState?.getBoolean(STATE_RESTORE_MODES_PENDING) ?: false
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode())
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        palette = ThemeCatalog.resolve(themeId, previewUsesDarkPalette(), previewUsesAmoledPalette())

        setupInsets()
        bindValues()
        binding.backButton.setOnClickListener { finish() }
        binding.themeModeRow.setOnClickListener { cycleThemeMode() }
        binding.themeRow.setOnClickListener { cycleTheme() }
        binding.recentGamesRow.setOnClickListener {
            startActivity(Intent(this, RecentGamesActivity::class.java))
        }
        binding.advancedRow.setOnClickListener {
            advancedLauncher.launch(
                AdvancedSettingsActivity.addSettingsExtras(
                    Intent(this, AdvancedSettingsActivity::class.java),
                    advancedSettings ?: settings,
                    restoreModesPending
                )
            )
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
        refreshPreview()
    }

    private fun cycleThemeMode() {
        val modes = ThemeMode.values()
        val nextIndex = (modes.indexOf(themeMode) + 1) % modes.size
        themeMode = modes[nextIndex]
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
        if (restoreModesPending) {
            repository.restoreDefaultModes()
        }
        val pendingAdvanced = advancedSettings
        val next = latest.copy(
            flagModeDefault = binding.flagModeDefault.isChecked,
            cordingEnabled = binding.enableCording.isChecked,
            vibrateEnabled = binding.vibrateEnabled.isChecked,
            themeMode = themeMode,
            themeId = themeId
        ).let { base ->
            pendingAdvanced?.let { advanced ->
                base.copy(
                    showInputToggle = advanced.showInputToggle,
                    showTopClears = advanced.showTopClears,
                    showMineDensity = advanced.showMineDensity,
                    mineDensityMinFade = advanced.mineDensityMinFade,
                    mineDensityMaxFade = advanced.mineDensityMaxFade,
                    roundCorners = advanced.roundCorners,
                    mergeTiles = advanced.mergeTiles,
                    fillGaps = advanced.fillGaps,
                    screenShakeEnabled = advanced.screenShakeEnabled,
                    longPressDelayMs = advanced.longPressDelayMs,
                    animationSpeedPercent = advanced.animationSpeedPercent,
                    amoledTheme = advanced.amoledTheme
                )
            } ?: base
        }
        repository.saveSettings(next)
        settings = next
        advancedSettings = null
        restoreModesPending = false
        finish()
    }

    private fun advancedSettingsChanged(candidate: AppSettings): Boolean {
        return candidate.showInputToggle != settings.showInputToggle ||
            candidate.showTopClears != settings.showTopClears ||
            candidate.showMineDensity != settings.showMineDensity ||
            candidate.mineDensityMinFade != settings.mineDensityMinFade ||
            candidate.mineDensityMaxFade != settings.mineDensityMaxFade ||
            candidate.roundCorners != settings.roundCorners ||
            candidate.mergeTiles != settings.mergeTiles ||
            candidate.fillGaps != settings.fillGaps ||
            candidate.screenShakeEnabled != settings.screenShakeEnabled ||
            candidate.longPressDelayMs != settings.longPressDelayMs ||
            candidate.animationSpeedPercent != settings.animationSpeedPercent ||
            candidate.amoledTheme != settings.amoledTheme
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_THEME_ID, themeId)
        outState.putString(STATE_THEME_MODE, themeMode.id)
        outState.putBoolean(STATE_RESTORE_MODES_PENDING, restoreModesPending)
    }

    private fun refreshPreview() {
        palette = ThemeCatalog.resolve(themeId, previewUsesDarkPalette(), previewUsesAmoledPalette())
        binding.themeModeValue.text = themeModeLabel(themeMode)
        binding.themeValue.text = palette.name
        binding.advancedValue.text = getString(
            if (advancedSettings != null || restoreModesPending) R.string.pending else R.string.open
        )
        applyPalette()
    }

    private fun applyPalette() {
        val themeModeUnsaved = themeMode != settings.themeMode
        val themeColorUnsaved = themeId != settings.themeId

        binding.root.setBackgroundColor(palette.background)
        binding.topBar.setBackgroundColor(palette.background)
        binding.content.setBackgroundColor(palette.background)
        binding.titleText.setTextColor(palette.ink)
        binding.recentGamesText.setTextColor(palette.ink)
        binding.recentGamesValue.setTextColor(palette.inkSoft)
        binding.advancedText.setTextColor(palette.ink)
        binding.advancedValue.setTextColor(if (advancedSettings != null || restoreModesPending) palette.accent else palette.inkSoft)
        binding.themeModeLabel.setTextColor(palette.ink)
        binding.themeModeValue.setTextColor(if (themeModeUnsaved) palette.accent else palette.inkSoft)
        binding.themeLabel.setTextColor(palette.ink)
        binding.themeValue.setTextColor(if (themeColorUnsaved) palette.accent else palette.inkSoft)
        listOf(
            binding.flagModeDefault,
            binding.enableCording,
            binding.vibrateEnabled
        ).forEach {
            it.setTextColor(palette.ink)
            applySwitchPalette(it)
        }
        binding.themeModeRow.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.themeRow.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.recentGamesRow.background = GradientDrawable().apply {
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

    private fun previewUsesDarkPalette(): Boolean {
        return when (themeMode) {
            ThemeMode.SYSTEM -> {
                val mask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mask == Configuration.UI_MODE_NIGHT_YES
            }
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }

    private fun previewUsesAmoledPalette(): Boolean {
        return (advancedSettings?.amoledTheme ?: settings.amoledTheme) && previewUsesDarkPalette()
    }

    private fun themeModeLabel(mode: ThemeMode): String {
        return getString(
            when (mode) {
                ThemeMode.SYSTEM -> R.string.theme_mode_system
                ThemeMode.LIGHT -> R.string.theme_mode_light
                ThemeMode.DARK -> R.string.theme_mode_dark
            }
        )
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
