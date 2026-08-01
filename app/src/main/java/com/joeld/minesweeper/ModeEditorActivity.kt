package com.joeld.minesweeper

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.materialswitch.MaterialSwitch
import com.joeld.minesweeper.databinding.ActivityModeEditorBinding

class ModeEditorActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MODE_ID = "mode_id"
        private const val MAX_BOARD_DIMENSION = 200
    }

    private lateinit var binding: ActivityModeEditorBinding
    private lateinit var repository: PrefsRepository
    private lateinit var palette: ThemePalette
    private lateinit var modes: MutableList<GameMode>
    private var existingMode: GameMode? = null
    private var pendingModeSave: GameMode? = null
    private var pendingDuplicateModeSave: GameMode? = null
    private var pendingDuplicateExistingMode: GameMode? = null
    private var pendingDeleteModeId: String? = null
    private var pendingClearScoresMode: GameMode? = null

    private val confirmLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_FIRST_USER) {
                pendingDuplicateModeSave?.let { duplicateMode ->
                    persistMode(duplicateMode)
                    clearPendingActions()
                    return@registerForActivityResult
                }
            }
            if (result.resultCode != Activity.RESULT_OK) {
                clearPendingActions()
                return@registerForActivityResult
            }
            pendingDuplicateExistingMode?.let { existingMode ->
                repository.saveSelectedModeId(existingMode.id)
                repository.markModeUsed(existingMode.id)
                startActivity(
                    Intent(this, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_MODE_ID, existingMode.id)
                        .putExtra(GameActivity.EXTRA_RESUME, false)
                )
                clearPendingActions()
                finish()
                return@registerForActivityResult
            }
            pendingModeSave?.let { confirmedMode ->
                persistMode(confirmedMode)
                clearPendingActions()
                return@registerForActivityResult
            }
            pendingDeleteModeId?.let { modeId ->
                persistDelete(modeId)
                clearPendingActions()
                return@registerForActivityResult
            }
            pendingClearScoresMode?.let { mode ->
                repository.clearScores(mode)
                clearPendingActions()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = PrefsRepository(this)
        val settings = repository.loadSettings()
        AppCompatDelegate.setDefaultNightMode(settings.nightMode())
        super.onCreate(savedInstanceState)
        binding = ActivityModeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        palette = ThemeCatalog.resolve(settings.themeId, settings.usesDarkPalette(this), settings.usesAmoledPalette(this))
        modes = repository.loadModes()
        existingMode = intent.getStringExtra(EXTRA_MODE_ID)?.let { id -> modes.firstOrNull { it.id == id } }

        setupInsets()
        applyPalette()
        populate()
        binding.backButton.setOnClickListener { finish() }
        binding.createButton.setOnClickListener { saveMode() }
        binding.saveButton.setOnClickListener {
            if (existingMode == null) startUnsavedGame() else saveMode()
        }
        binding.deleteButton.setOnClickListener { deleteMode() }
        binding.clearScoresButton.setOnClickListener { clearScores() }
        binding.minesInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) normalizeMineInput()
        }
        binding.widthInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) normalizeMineInput()
        }
        binding.heightInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) normalizeMineInput()
        }
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

    private fun populate() {
        val mode = existingMode ?: createModeTemplate()
        binding.titleText.text = if (existingMode == null) getString(R.string.create_mode) else getString(R.string.edit_mode)
        binding.saveButton.text = getString(if (existingMode == null) R.string.new_game_short else R.string.save)
        binding.createButton.isVisible = existingMode == null
        binding.nameInput.setText(mode.name)
        binding.widthInput.setText(mode.width.toString())
        binding.heightInput.setText(mode.height.toString())
        binding.minesInput.setText(mode.mines.toString())
        binding.noGuessSwitch.isChecked = mode.noGuess
        binding.noFlagSwitch.isChecked = mode.noFlagMode
        binding.deleteButton.isVisible = existingMode != null
        binding.clearScoresButton.isVisible = existingMode != null
    }

    private fun createModeTemplate(): GameMode {
        val lastPlayed = repository.loadModesWithRecentTemplates().firstOrNull()
        return lastPlayed?.copy(id = "", name = "")
            ?: repository.createMode("", 12, 12, 20, true)
    }

    private fun readModeFromInputs(): GameMode? {
        normalizeMineInput()
        val width = binding.widthInput.text.toString().toIntOrNull() ?: 0
        val height = binding.heightInput.text.toString().toIntOrNull() ?: 0
        val cellCount = width * height
        val mines = ModeTextFormatter.parseMineInput(binding.minesInput.text.toString(), width, height) ?: 0
        val safeReserve = minOf(9, cellCount - 1)
        val maxMines = cellCount - safeReserve
        if (width < 5 || height < 5 || width > MAX_BOARD_DIMENSION || height > MAX_BOARD_DIMENSION || mines < 1 || mines > maxMines) {
            binding.errorText.text = getString(R.string.mode_error)
            binding.errorText.isVisible = true
            return null
        }
        return repository.createMode(
            name = binding.nameInput.text.toString().trim(),
            width = width,
            height = height,
            mines = mines,
            noGuess = binding.noGuessSwitch.isChecked,
            noFlagMode = binding.noFlagSwitch.isChecked
        )
    }

    private fun startUnsavedGame() {
        val mode = readModeFromInputs() ?: return
        val modeId = repository.scoreKey(mode)
        repository.markModeUsed(modeId)
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_MODE_ID, modeId)
                .putExtra(GameActivity.EXTRA_RESUME, false)
        )
        finish()
    }

    private fun saveMode() {
        val inputMode = readModeFromInputs() ?: return
        val base = existingMode ?: repository.createMode("", inputMode.width, inputMode.height, inputMode.mines, inputMode.noGuess, inputMode.noFlagMode)
        val updated = base.copy(
            name = inputMode.name,
            width = inputMode.width,
            height = inputMode.height,
            mines = inputMode.mines,
            noGuess = inputMode.noGuess,
            noFlagMode = inputMode.noFlagMode
        )
        val finalModes = if (existingMode == null) {
            (modes + updated).toMutableList()
        } else {
            modes.map { if (it.id == updated.id) updated else it }.toMutableList()
        }
        if (existingMode == null) {
            val duplicate = modes.firstOrNull { hasExactSameRules(it, updated) }
            if (duplicate != null) {
                pendingDuplicateModeSave = updated
                pendingDuplicateExistingMode = duplicate
                pendingModeSave = null
                pendingDeleteModeId = null
                pendingClearScoresMode = null
                openDuplicateModeConfirm(duplicate)
                return
            }
        }
        val requiresConfirmation = existingMode != null && modeRulesChanged(existingMode!!, updated)
        if (requiresConfirmation) {
            pendingModeSave = updated
            pendingDuplicateModeSave = null
            pendingDuplicateExistingMode = null
            pendingDeleteModeId = null
            pendingClearScoresMode = null
            openModeConfirm()
            return
        }
        persistMode(updated)
    }

    private fun normalizeMineInput() {
        val width = binding.widthInput.text.toString().toIntOrNull() ?: return
        val height = binding.heightInput.text.toString().toIntOrNull() ?: return
        val mines = ModeTextFormatter.parseMineInput(binding.minesInput.text.toString(), width, height) ?: return
        if (binding.minesInput.text.toString() != mines.toString()) {
            binding.minesInput.setText(mines.toString())
            binding.minesInput.setSelection(binding.minesInput.text?.length ?: 0)
        }
    }

    private fun deleteMode() {
        val mode = existingMode ?: return
        if (modes.size == 1) {
            binding.errorText.text = getString(R.string.mode_delete_error)
            binding.errorText.isVisible = true
            return
        }
        pendingModeSave = null
        pendingDuplicateModeSave = null
        pendingDuplicateExistingMode = null
        pendingDeleteModeId = mode.id
        pendingClearScoresMode = null
        openDeleteConfirm()
    }

    private fun clearScores() {
        val mode = existingMode ?: return
        pendingModeSave = null
        pendingDuplicateModeSave = null
        pendingDuplicateExistingMode = null
        pendingDeleteModeId = null
        pendingClearScoresMode = mode
        confirmLauncher.launch(
            Intent(this, ModeChangeConfirmActivity::class.java)
                .putExtra(ModeChangeConfirmActivity.EXTRA_TITLE, getString(R.string.clear_scores))
                .putExtra(ModeChangeConfirmActivity.EXTRA_MESSAGE, getString(R.string.clear_scores_confirm_message))
                .putExtra(ModeChangeConfirmActivity.EXTRA_DESTRUCTIVE, true)
        )
    }

    private fun persistMode(updated: GameMode) {
        val finalModes = if (existingMode == null) {
            (modes + updated).toMutableList()
        } else {
            modes.map { if (it.id == updated.id) updated else it }.toMutableList()
        }
        repository.saveModes(finalModes)
        repository.saveSelectedModeId(updated.id)
        if (existingMode != null && modeRulesChanged(existingMode!!, updated)) {
            repository.clearProgress(updated.id)
        }
        if (existingMode == null) {
            repository.markModeUsed(updated.id)
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_MODE_ID, updated.id)
                    .putExtra(GameActivity.EXTRA_RESUME, false)
            )
        }
        finish()
    }

    private fun modeRulesChanged(previous: GameMode, updated: GameMode): Boolean {
        return !hasExactSameRules(previous, updated)
    }

    private fun hasExactSameRules(first: GameMode, second: GameMode): Boolean {
        return first.width == second.width &&
            first.height == second.height &&
            first.mines == second.mines &&
            first.noGuess == second.noGuess &&
            first.noFlagMode == second.noFlagMode
    }

    private fun persistDelete(modeId: String) {
        repository.saveModes(modes.filter { it.id != modeId })
        finish()
    }

    private fun openModeConfirm() {
        confirmLauncher.launch(
            Intent(this, ModeChangeConfirmActivity::class.java)
                .putExtra(ModeChangeConfirmActivity.EXTRA_MESSAGE, getString(R.string.mode_change_confirm_message))
        )
    }

    private fun openDeleteConfirm() {
        confirmLauncher.launch(
            Intent(this, ModeChangeConfirmActivity::class.java)
                .putExtra(ModeChangeConfirmActivity.EXTRA_TITLE, getString(R.string.delete))
                .putExtra(ModeChangeConfirmActivity.EXTRA_MESSAGE, getString(R.string.mode_delete_confirm_message))
        )
    }

    private fun openDuplicateModeConfirm(existingMode: GameMode) {
        val label = existingMode.name.ifBlank {
            ModeTextFormatter.compact(this, existingMode, repository.loadSettings().showMineDensity)
        }
        confirmLauncher.launch(
            Intent(this, ModeChangeConfirmActivity::class.java)
                .putExtra(ModeChangeConfirmActivity.EXTRA_TITLE, getString(R.string.mode_already_exists_title))
                .putExtra(ModeChangeConfirmActivity.EXTRA_MESSAGE, getString(R.string.mode_already_exists_message, label))
                .putExtra(ModeChangeConfirmActivity.EXTRA_CONTINUE_LABEL, getString(R.string.use_existing))
                .putExtra(ModeChangeConfirmActivity.EXTRA_CANCEL_LABEL, getString(R.string.create_anyway))
                .putExtra(ModeChangeConfirmActivity.EXTRA_CANCEL_RESULT, Activity.RESULT_FIRST_USER)
        )
    }

    private fun clearPendingActions() {
        pendingModeSave = null
        pendingDuplicateModeSave = null
        pendingDuplicateExistingMode = null
        pendingDeleteModeId = null
        pendingClearScoresMode = null
    }

    private fun applyPalette() {
        binding.root.setBackgroundColor(palette.background)
        binding.topBar.setBackgroundColor(palette.background)
        binding.content.setBackgroundColor(palette.background)
        binding.titleText.setTextColor(palette.ink)
        listOf(binding.nameInput, binding.widthInput, binding.heightInput, binding.minesInput).forEach {
            it.background = GradientDrawable().apply {
                cornerRadius = 18f * resources.displayMetrics.density
                setColor(palette.input)
            }
            it.setTextColor(palette.ink)
            it.setHintTextColor(palette.inkSoft)
        }
        listOf(binding.noGuessSwitch, binding.noFlagSwitch).forEach {
            it.setTextColor(palette.ink)
            applySwitchPalette(it)
        }
        binding.errorText.setTextColor(android.graphics.Color.parseColor("#D94B4B"))
        binding.saveButton.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.accent)
        }
        binding.saveButton.setTextColor(palette.revealedCell)
        binding.createButton.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.accent)
        }
        binding.createButton.setTextColor(palette.revealedCell)
        if (existingMode == null) {
            binding.saveButton.background = GradientDrawable().apply {
                cornerRadius = 22f * resources.displayMetrics.density
                setColor(palette.panel)
            }
            binding.saveButton.setTextColor(palette.ink)
        }
        binding.deleteButton.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.deleteButton.setTextColor(palette.ink)
        binding.clearScoresButton.background = GradientDrawable().apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(palette.panel)
        }
        binding.clearScoresButton.setTextColor(palette.ink)
        binding.backButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.panel)
        }
        binding.backButton.imageTintList = android.content.res.ColorStateList.valueOf(palette.ink)
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

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
