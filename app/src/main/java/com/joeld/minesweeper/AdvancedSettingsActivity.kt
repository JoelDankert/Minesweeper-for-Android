package com.joeld.minesweeper

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.joeld.minesweeper.databinding.ActivityAdvancedSettingsBinding

class AdvancedSettingsActivity : AppCompatActivity() {
    companion object {
        private const val STATE_RESTORE_PENDING = "state_restore_pending"
        private const val ANIMATION_SPEED_STEP = 5
        const val EXTRA_RESTORE_PENDING = "restore_pending"
        const val EXTRA_SHOW_INPUT_TOGGLE = "show_input_toggle"
        const val EXTRA_SHOW_TOP_CLEARS = "show_top_clears"
        const val EXTRA_SHOW_MINE_DENSITY = "show_mine_density"
        const val EXTRA_MINE_DENSITY_MIN_FADE = "mine_density_min_fade"
        const val EXTRA_MINE_DENSITY_MAX_FADE = "mine_density_max_fade"
        const val EXTRA_ROUND_CORNERS = "round_corners"
        const val EXTRA_MERGE_TILES = "merge_tiles"
        const val EXTRA_FILL_GAPS = "fill_gaps"
        const val EXTRA_SCREEN_SHAKE_ENABLED = "screen_shake_enabled"
        const val EXTRA_LONG_PRESS_DELAY_MS = "long_press_delay_ms"
        const val EXTRA_ANIMATION_SPEED_PERCENT = "animation_speed_percent"
        const val EXTRA_AMOLED_THEME = "amoled_theme"

        fun addSettingsExtras(intent: Intent, settings: AppSettings, restorePending: Boolean): Intent {
            return intent
                .putExtra(EXTRA_RESTORE_PENDING, restorePending)
                .putExtra(EXTRA_SHOW_INPUT_TOGGLE, settings.showInputToggle)
                .putExtra(EXTRA_SHOW_TOP_CLEARS, settings.showTopClears)
                .putExtra(EXTRA_SHOW_MINE_DENSITY, settings.showMineDensity)
                .putExtra(EXTRA_MINE_DENSITY_MIN_FADE, settings.mineDensityMinFade)
                .putExtra(EXTRA_MINE_DENSITY_MAX_FADE, settings.mineDensityMaxFade)
                .putExtra(EXTRA_ROUND_CORNERS, settings.roundCorners)
                .putExtra(EXTRA_MERGE_TILES, settings.mergeTiles)
                .putExtra(EXTRA_FILL_GAPS, settings.fillGaps)
                .putExtra(EXTRA_SCREEN_SHAKE_ENABLED, settings.screenShakeEnabled)
                .putExtra(EXTRA_LONG_PRESS_DELAY_MS, settings.longPressDelayMs)
                .putExtra(EXTRA_ANIMATION_SPEED_PERCENT, settings.animationSpeedPercent)
                .putExtra(EXTRA_AMOLED_THEME, settings.amoledTheme)
        }

        fun settingsFromIntent(intent: Intent?, fallback: AppSettings): AppSettings {
            intent ?: return fallback
            return fallback.copy(
                showInputToggle = intent.getBooleanExtra(EXTRA_SHOW_INPUT_TOGGLE, fallback.showInputToggle),
                showTopClears = intent.getBooleanExtra(EXTRA_SHOW_TOP_CLEARS, fallback.showTopClears),
                showMineDensity = intent.getBooleanExtra(EXTRA_SHOW_MINE_DENSITY, fallback.showMineDensity),
                mineDensityMinFade = intent.getFloatExtra(EXTRA_MINE_DENSITY_MIN_FADE, fallback.mineDensityMinFade),
                mineDensityMaxFade = intent.getFloatExtra(EXTRA_MINE_DENSITY_MAX_FADE, fallback.mineDensityMaxFade),
                roundCorners = intent.getBooleanExtra(EXTRA_ROUND_CORNERS, fallback.roundCorners),
                mergeTiles = intent.getBooleanExtra(EXTRA_MERGE_TILES, fallback.mergeTiles),
                fillGaps = intent.getBooleanExtra(EXTRA_FILL_GAPS, fallback.fillGaps),
                screenShakeEnabled = intent.getBooleanExtra(EXTRA_SCREEN_SHAKE_ENABLED, fallback.screenShakeEnabled),
                longPressDelayMs = intent.getIntExtra(EXTRA_LONG_PRESS_DELAY_MS, fallback.longPressDelayMs),
                animationSpeedPercent = intent.getIntExtra(EXTRA_ANIMATION_SPEED_PERCENT, fallback.animationSpeedPercent),
                amoledTheme = intent.getBooleanExtra(EXTRA_AMOLED_THEME, fallback.amoledTheme)
            )
        }
    }

    private lateinit var binding: ActivityAdvancedSettingsBinding
    private lateinit var repository: PrefsRepository
    private lateinit var palette: ThemePalette
    private var settings = AppSettings()
    private var restorePending = false

    private val confirmLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                repository.clearAllScores()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = PrefsRepository(this)
        val savedSettings = repository.loadSettings()
        settings = settingsFromIntent(intent, savedSettings)
        restorePending = savedInstanceState?.getBoolean(STATE_RESTORE_PENDING)
            ?: intent.getBooleanExtra(EXTRA_RESTORE_PENDING, false)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(savedSettings.nightMode())
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        palette = ThemeCatalog.resolve(savedSettings.themeId, savedSettings.usesDarkPalette(this), savedSettings.usesAmoledPalette(this))

        setupInsets()
        applyPalette()
        bindValues()
        binding.backButton.setOnClickListener { finishWithResult() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult()
            }
        })
        binding.restoreModesButton.setOnClickListener {
            restorePending = !restorePending
            updateRestoreModesState()
        }
        binding.clearAllScoresButton.setOnClickListener {
            confirmLauncher.launch(
                Intent(this, ModeChangeConfirmActivity::class.java)
                    .putExtra(ModeChangeConfirmActivity.EXTRA_TITLE, getString(R.string.clear_all_scores))
                    .putExtra(ModeChangeConfirmActivity.EXTRA_MESSAGE, getString(R.string.clear_all_scores_confirm_message))
                    .putExtra(ModeChangeConfirmActivity.EXTRA_DESTRUCTIVE, true)
            )
        }
        binding.amoledTheme.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(amoledTheme = isChecked)
            refreshPalette()
        }
        binding.roundCorners.setOnCheckedChangeListener { _, isChecked ->
            updateMergeTilesState(isChecked)
        }
        binding.mergeTiles.setOnCheckedChangeListener { _, isChecked ->
            updateFillGapsState(!binding.roundCorners.isChecked || isChecked)
        }
        binding.showMineDensity.setOnCheckedChangeListener { _, isChecked ->
            updateMineDensityFadeState(isChecked)
        }
        binding.longPressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.longPressValue.text = formatLongPressDelay(progressToDelay(progress))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        binding.animationSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val snapped = snapAnimationSpeed(progress)
                if (fromUser && seekBar != null && snapped != progress) {
                    seekBar.progress = snapped
                    return
                }
                binding.animationSpeedValue.text = formatAnimationSpeed(snapped)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar ?: return
                val snapped = snapAnimationSpeed(seekBar.progress)
                if (seekBar.progress != snapped) {
                    seekBar.progress = snapped
                }
                binding.animationSpeedValue.text = formatAnimationSpeed(snapped)
            }
        })
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
        binding.amoledTheme.isChecked = settings.amoledTheme
        binding.showBottomToggle.isChecked = settings.showInputToggle
        binding.showTopClears.isChecked = settings.showTopClears
        binding.showMineDensity.isChecked = settings.showMineDensity
        binding.mineDensityMinFadeInput.setText(formatDensityFade(settings.mineDensityMinFade))
        binding.mineDensityMaxFadeInput.setText(formatDensityFade(settings.mineDensityMaxFade))
        binding.roundCorners.isChecked = settings.roundCorners
        binding.mergeTiles.isChecked = settings.mergeTiles
        binding.fillGaps.isChecked = settings.fillGaps
        binding.screenShake.isChecked = settings.screenShakeEnabled
        updateMineDensityFadeState(binding.showMineDensity.isChecked)
        updateMergeTilesState(binding.roundCorners.isChecked)
        updateFillGapsState(!binding.roundCorners.isChecked || binding.mergeTiles.isChecked)
        binding.longPressSeekBar.progress = delayToProgress(settings.longPressDelayMs)
        binding.longPressValue.text = formatLongPressDelay(settings.longPressDelayMs)
        val snappedAnimationSpeed = snapAnimationSpeed(settings.animationSpeedPercent)
        binding.animationSpeedSeekBar.progress = snappedAnimationSpeed
        binding.animationSpeedValue.text = formatAnimationSpeed(snappedAnimationSpeed)
        updateRestoreModesState()
    }

    private fun currentSettings(): AppSettings {
        val minFade = parseDensityFade(binding.mineDensityMinFadeInput.text.toString(), settings.mineDensityMinFade)
        val maxFade = parseDensityFade(binding.mineDensityMaxFadeInput.text.toString(), settings.mineDensityMaxFade)
        return settings.copy(
            showInputToggle = binding.showBottomToggle.isChecked,
            showTopClears = binding.showTopClears.isChecked,
            showMineDensity = binding.showMineDensity.isChecked,
            mineDensityMinFade = minOf(minFade, maxFade),
            mineDensityMaxFade = maxOf(minFade, maxFade),
            roundCorners = binding.roundCorners.isChecked,
            mergeTiles = binding.roundCorners.isChecked && binding.mergeTiles.isChecked,
            fillGaps = (!binding.roundCorners.isChecked || binding.mergeTiles.isChecked) && binding.fillGaps.isChecked,
            screenShakeEnabled = binding.screenShake.isChecked,
            longPressDelayMs = progressToDelay(binding.longPressSeekBar.progress),
            animationSpeedPercent = snapAnimationSpeed(binding.animationSpeedSeekBar.progress),
            amoledTheme = binding.amoledTheme.isChecked
        )
    }

    private fun finishWithResult() {
        setResult(Activity.RESULT_OK, addSettingsExtras(Intent(), currentSettings(), restorePending))
        finish()
    }

    private fun refreshPalette() {
        palette = ThemeCatalog.resolve(settings.themeId, settings.usesDarkPalette(this), settings.usesAmoledPalette(this))
        applyPalette()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_RESTORE_PENDING, restorePending)
    }

    private fun applyPalette() {
        binding.root.setBackgroundColor(palette.background)
        binding.topBar.setBackgroundColor(palette.background)
        binding.content.setBackgroundColor(palette.background)
        binding.titleText.setTextColor(palette.ink)
        binding.restoreModesLabel.setTextColor(palette.ink)
        binding.clearAllScoresLabel.setTextColor(palette.ink)
        binding.clearAllScoresValue.setTextColor(palette.inkSoft)
        binding.longPressLabel.setTextColor(palette.ink)
        binding.longPressValue.setTextColor(palette.inkSoft)
        binding.animationSpeedLabel.setTextColor(palette.ink)
        binding.animationSpeedValue.setTextColor(palette.inkSoft)
        listOf(binding.mineDensityMinFadeInput, binding.mineDensityMaxFadeInput).forEach {
            it.background = GradientDrawable().apply {
                cornerRadius = 18f * resources.displayMetrics.density
                setColor(palette.input)
            }
            it.setTextColor(palette.ink)
            it.setHintTextColor(palette.inkSoft)
        }
        listOf(binding.amoledTheme, binding.showBottomToggle, binding.showTopClears, binding.showMineDensity, binding.roundCorners, binding.mergeTiles, binding.fillGaps, binding.screenShake).forEach {
            it.setTextColor(palette.ink)
            applySwitchPalette(it)
        }
        binding.longPressRow.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.animationSpeedRow.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.restoreModesButton.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.clearAllScoresButton.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.backButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.panel)
        }
        binding.backButton.imageTintList = ColorStateList.valueOf(palette.ink)
        updateRestoreModesState()
        updateMineDensityFadeState(binding.showMineDensity.isChecked)
        updateMergeTilesState(binding.roundCorners.isChecked)
        updateFillGapsState(!binding.roundCorners.isChecked || binding.mergeTiles.isChecked)
    }

    private fun updateRestoreModesState() {
        binding.restoreModesValue.text =
            getString(if (restorePending) R.string.pending else R.string.restore)
        binding.restoreModesValue.setTextColor(if (restorePending) palette.accent else palette.inkSoft)
    }

    private fun updateMergeTilesState(roundCornersEnabled: Boolean) {
        binding.mergeTiles.isEnabled = roundCornersEnabled
        binding.mergeTiles.alpha = if (roundCornersEnabled) 1f else 0.45f
        if (!roundCornersEnabled) {
            binding.mergeTiles.isChecked = false
        }
        updateFillGapsState(!roundCornersEnabled || binding.mergeTiles.isChecked)
    }

    private fun updateFillGapsState(fillGapsAllowed: Boolean) {
        binding.fillGaps.isEnabled = fillGapsAllowed
        binding.fillGaps.alpha = if (fillGapsAllowed) 1f else 0.45f
        if (!fillGapsAllowed) {
            binding.fillGaps.isChecked = false
        }
    }

    private fun updateMineDensityFadeState(enabled: Boolean) {
        listOf(binding.mineDensityMinFadeInput, binding.mineDensityMaxFadeInput).forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.45f
        }
    }

    private fun applySwitchPalette(switch: MaterialSwitch) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        switch.thumbTintList = ColorStateList(
            states,
            intArrayOf(palette.accent, palette.input)
        )
        switch.trackTintList = ColorStateList(
            states,
            intArrayOf(shift(palette.accent, 0.78f), shift(palette.panel, 0.92f))
        )
    }

    private fun shift(color: Int, factor: Float): Int {
        val r = (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun progressToDelay(progress: Int): Int = 50 + progress.coerceIn(0, 9) * 50

    private fun delayToProgress(delayMs: Int): Int = ((delayMs.coerceIn(50, 500) - 50) / 50)

    private fun formatLongPressDelay(delayMs: Int): String = "$delayMs ms"

    private fun formatAnimationSpeed(percent: Int): String = "$percent%"

    private fun formatDensityFade(value: Float): String {
        return "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
    }

    private fun parseDensityFade(value: String, fallback: Float): Float {
        return value.trim().replace(',', '.').toFloatOrNull()?.coerceIn(0f, 1f) ?: fallback
    }

    private fun snapAnimationSpeed(value: Int): Int {
        return ((value.coerceIn(0, 100) + (ANIMATION_SPEED_STEP / 2)) / ANIMATION_SPEED_STEP) * ANIMATION_SPEED_STEP
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
