package com.joeld.minesweeper

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.joeld.minesweeper.databinding.ActivityAdvancedSettingsBinding

class AdvancedSettingsActivity : AppCompatActivity() {
    companion object {
        private const val STATE_RESTORE_PENDING = "state_restore_pending"
        private const val ANIMATION_SPEED_STEP = 5
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
        settings = repository.loadSettings()
        restorePending = savedInstanceState?.getBoolean(STATE_RESTORE_PENDING) ?: false
        AppCompatDelegate.setDefaultNightMode(
            if (settings.darkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        palette = ThemeCatalog.resolve(settings.themeId, settings.darkTheme)

        setupInsets()
        applyPalette()
        bindValues()
        binding.backButton.setOnClickListener { finish() }
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

    private fun applySettings() {
        if (restorePending) {
            repository.restoreDefaultModes()
        }
        val minFade = parseDensityFade(binding.mineDensityMinFadeInput.text.toString(), settings.mineDensityMinFade)
        val maxFade = parseDensityFade(binding.mineDensityMaxFadeInput.text.toString(), settings.mineDensityMaxFade)
        repository.saveSettings(
            settings.copy(
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
                animationSpeedPercent = snapAnimationSpeed(binding.animationSpeedSeekBar.progress)
            )
        )
        finish()
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
        listOf(binding.showBottomToggle, binding.showTopClears, binding.showMineDensity, binding.roundCorners, binding.mergeTiles, binding.fillGaps, binding.screenShake).forEach {
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
        binding.applyButton.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.accent)
        }
        binding.applyButton.setTextColor(palette.revealedCell)
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
