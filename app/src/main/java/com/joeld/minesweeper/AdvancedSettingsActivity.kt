package com.joeld.minesweeper

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.SeekBar
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
        binding.mergeTiles.setOnCheckedChangeListener { _, isChecked ->
            updateFillGapsState(isChecked)
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
        binding.mergeTiles.isChecked = settings.mergeTiles
        binding.fillGaps.isChecked = settings.fillGaps
        updateFillGapsState(binding.mergeTiles.isChecked)
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
        repository.saveSettings(
            settings.copy(
                showInputToggle = binding.showBottomToggle.isChecked,
                showTopClears = binding.showTopClears.isChecked,
                mergeTiles = binding.mergeTiles.isChecked,
                fillGaps = binding.mergeTiles.isChecked && binding.fillGaps.isChecked,
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
        binding.longPressLabel.setTextColor(palette.ink)
        binding.longPressValue.setTextColor(palette.inkSoft)
        binding.animationSpeedLabel.setTextColor(palette.ink)
        binding.animationSpeedValue.setTextColor(palette.inkSoft)
        listOf(binding.showBottomToggle, binding.showTopClears, binding.mergeTiles, binding.fillGaps).forEach {
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
        updateFillGapsState(binding.mergeTiles.isChecked)
    }

    private fun updateRestoreModesState() {
        binding.restoreModesValue.text =
            getString(if (restorePending) R.string.pending else R.string.restore)
        binding.restoreModesValue.setTextColor(if (restorePending) palette.accent else palette.inkSoft)
    }

    private fun updateFillGapsState(mergeEnabled: Boolean) {
        binding.fillGaps.isEnabled = mergeEnabled
        binding.fillGaps.alpha = if (mergeEnabled) 1f else 0.45f
        if (!mergeEnabled) {
            binding.fillGaps.isChecked = false
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

    private fun snapAnimationSpeed(value: Int): Int {
        return ((value.coerceIn(0, 100) + (ANIMATION_SPEED_STEP / 2)) / ANIMATION_SPEED_STEP) * ANIMATION_SPEED_STEP
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
