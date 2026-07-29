package com.joeld.minesweeper

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.joeld.minesweeper.databinding.ActivityGameBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

class GameActivity : AppCompatActivity(), BoardView.Listener {
    companion object {
        const val EXTRA_MODE_ID = "mode_id"
        const val EXTRA_RESUME = "resume"
        private const val MINE_REVEAL_ANIMATION_SCALE = 0.2f
        private const val MINE_SHAKE_DURATION_MS = 260L
        private const val MINE_SHAKE_CYCLES = 5.5f
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var repository: PrefsRepository
    private lateinit var selectedMode: GameMode
    private lateinit var game: MinesweeperGame
    private lateinit var palette: ThemePalette

    private val scope = MainScope()
    private var timerJob: Job? = null
    private var settings = AppSettings()
    private var inputMode = InputMode.REVEAL
    private var startedAtMs = 0L
    private var carriedElapsedSeconds = 0
    private var boardBusy = false
    private var gameResultRecorded = false
    private var endGameCameraStarted = false
    private var latestFinishedRecord: RecentGameRecord? = null
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = PrefsRepository(this)
        settings = repository.loadSettings()
        AppCompatDelegate.setDefaultNightMode(if (settings.darkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val modes = repository.loadModes()
        val modeId = intent.getStringExtra(EXTRA_MODE_ID)
        selectedMode = modes.firstOrNull { it.id == modeId } ?: modes.first()
        palette = ThemeCatalog.resolve(settings.themeId, settings.darkTheme)

        setupInsets()
        setupClicks()
        applyPalette()
        loadInitialGame()
    }

    override fun onPause() {
        super.onPause()
        if (::game.isInitialized) {
            if (game.state == GameState.RUNNING) {
                carriedElapsedSeconds = currentElapsedSeconds()
                startedAtMs = SystemClock.elapsedRealtime() - carriedElapsedSeconds * 1000L
            }
            timerJob?.cancel()
            if (isPristineGame()) {
                repository.clearProgress(selectedMode.id)
            } else {
                repository.saveProgress(game.exportProgress(currentElapsedSeconds(), inputMode))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::game.isInitialized) {
            val latestSettings = repository.loadSettings()
            val flagModeDefaultChanged = latestSettings.flagModeDefault != settings.flagModeDefault
            settings = latestSettings
            palette = ThemeCatalog.resolve(settings.themeId, settings.darkTheme)
            if (flagModeDefaultChanged) {
                inputMode = defaultInputMode()
            }
            binding.boardView.setLongPressDelayMs(settings.longPressDelayMs)
            binding.boardView.setAnimationSpeedPercent(settings.animationSpeedPercent)
            binding.boardView.setRoundCorners(settings.roundCorners)
            binding.boardView.setMergeTiles(settings.mergeTiles)
            binding.boardView.setFillGaps(settings.fillGaps)
            binding.boardView.setPalette(palette)
            applyPalette()
            if (game.state == GameState.RUNNING) {
                startedAtMs = SystemClock.elapsedRealtime() - carriedElapsedSeconds * 1000L
                startTimer()
            } else {
                updateHeader()
            }
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onCellTap(col: Int, row: Int) {
        if (boardBusy) return
        val cell = game.getCell(col, row)
        when {
            game.state == GameState.READY -> revealCell(col, row, hapticOnChange = true)
            cell.revealed -> revealCell(col, row, hapticOnChange = true)
            !selectedMode.noFlagMode && inputMode == InputMode.FLAG -> if (game.toggleFlag(col, row)) {
                triggerActionHaptic()
                refreshBoard()
            }
            else -> revealCell(col, row, hapticOnChange = true)
        }
    }

    override fun onCellLongPress(col: Int, row: Int) {
        if (boardBusy) return
        val cell = game.getCell(col, row)
        if (cell.revealed) {
            return
        } else {
            val revealsOnLongPress = selectedMode.noFlagMode || inputMode == InputMode.FLAG || game.state == GameState.READY
            val result = if (revealsOnLongPress) {
                revealCellImmediate(col, row)
            } else {
                RevealResult(changed = game.toggleFlag(col, row))
            }
            if (result.changed) {
                triggerActionHaptic()
                if (result.exploded) triggerScreenShake()
                refreshBoard(animationDurationScale = if (result.exploded) MINE_REVEAL_ANIMATION_SCALE else 1f)
            }
        }
    }

    private fun setupInsets() {
        val topPadding = binding.topOverlay.paddingTop
        val bottomPadding = binding.bottomOverlay.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topOverlay.setPadding(16.dp, topPadding + bars.top, 16.dp, 0)
            binding.bottomOverlay.setPadding(16.dp, 0, 16.dp, bottomPadding + bars.bottom)
            insets
        }
    }

    private fun setupClicks() {
        binding.backButton.setOnClickListener { finish() }
        binding.topCenterHit.setOnClickListener {
            repository.clearProgress(selectedMode.id)
            startNewGame(resetCamera = false, resetInputMode = true)
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.revealToggle.setOnClickListener {
            inputMode = InputMode.REVEAL
            updateInputModeUi()
        }
        binding.flagToggle.setOnClickListener {
            if (selectedMode.noFlagMode) return@setOnClickListener
            inputMode = InputMode.FLAG
            updateInputModeUi()
        }
    }

    private fun loadInitialGame() {
        val resume = intent.getBooleanExtra(EXTRA_RESUME, false)
        val snapshot = if (resume) {
            repository.loadProgress(selectedMode.id)?.takeIf { it.cells.size == selectedMode.width * selectedMode.height }
        } else {
            null
        }
        startNewGame(resetCamera = true, resetInputMode = snapshot == null, progress = snapshot)
    }

    private fun startNewGame(resetCamera: Boolean, resetInputMode: Boolean, progress: GameProgress? = null) {
        timerJob?.cancel()
        boardBusy = false
        endGameCameraStarted = false
        binding.boardLoading.isVisible = false
        binding.boardView.setInteractionsEnabled(true)
        game = MinesweeperGame(selectedMode, settings.cordingEnabled)
        carriedElapsedSeconds = progress?.elapsedSeconds ?: 0
        startedAtMs = SystemClock.elapsedRealtime() - carriedElapsedSeconds * 1000L
        gameResultRecorded = progress?.state == GameState.WON || progress?.state == GameState.LOST
        latestFinishedRecord = null
        if (resetInputMode) {
            inputMode = defaultInputMode()
        }
        progress?.let {
            game.importProgress(it)
            inputMode = if (selectedMode.noFlagMode) InputMode.REVEAL else it.inputMode
        }
        binding.boardView.bind(game, this)
        binding.boardView.setLongPressDelayMs(settings.longPressDelayMs)
        binding.boardView.setAnimationSpeedPercent(settings.animationSpeedPercent)
        binding.boardView.setRoundCorners(settings.roundCorners)
        binding.boardView.setMergeTiles(settings.mergeTiles)
        binding.boardView.setFillGaps(settings.fillGaps)
        binding.boardView.setPalette(palette)
        if (resetCamera) binding.boardView.post { binding.boardView.resetCamera() }
        updateHeader()
        updateInputModeUi()
        updateRecentPanel()
        if (game.state == GameState.RUNNING) startTimer()
    }

    private fun revealCell(col: Int, row: Int, hapticOnChange: Boolean = false) {
        val wasReady = game.state == GameState.READY
        if (wasReady && selectedMode.noGuess) {
            boardBusy = true
            binding.boardLoading.isVisible = true
            binding.boardView.setInteractionsEnabled(false)
        }
        scope.launch {
            val result = if (wasReady && selectedMode.noGuess) {
                withContext(Dispatchers.Default) { game.reveal(col, row) }
            } else {
                game.reveal(col, row)
            }
            if (result.noGuessFailed) {
                showNoGuessFailedDialog()
            }
            if (result.changed) {
                if (hapticOnChange) {
                    triggerActionHaptic()
                }
                if (wasReady && game.state == GameState.RUNNING) {
                    startedAtMs = SystemClock.elapsedRealtime()
                    carriedElapsedSeconds = 0
                    startTimer()
                }
                if (game.state == GameState.WON || game.state == GameState.LOST) {
                    carriedElapsedSeconds = ((SystemClock.elapsedRealtime() - startedAtMs) / 1000L).toInt()
                }
                binding.boardLoading.isVisible = false
                if (result.exploded) triggerScreenShake()
                val zooming = refreshBoard(animationDurationScale = if (result.exploded) MINE_REVEAL_ANIMATION_SCALE else 1f)
                if (zooming) return@launch
            }
            boardBusy = false
            binding.boardLoading.isVisible = false
            binding.boardView.setInteractionsEnabled(true)
        }
    }

    private fun revealCellImmediate(col: Int, row: Int): RevealResult {
        val wasReady = game.state == GameState.READY
        val result = game.reveal(col, row)
        if (result.changed && wasReady && game.state == GameState.RUNNING) {
            startedAtMs = SystemClock.elapsedRealtime()
            carriedElapsedSeconds = 0
            startTimer()
        }
        if (result.changed && (game.state == GameState.WON || game.state == GameState.LOST)) {
            carriedElapsedSeconds = ((SystemClock.elapsedRealtime() - startedAtMs) / 1000L).toInt()
        }
        return result
    }

    private fun refreshBoard(animationDurationScale: Float = 1f): Boolean {
        binding.boardView.refresh(animationDurationScale)
        maybeRecordFinishedGame()
        updateHeader()
        updateInputModeUi()
        updateRecentPanel()
        repository.saveProgress(game.exportProgress(currentElapsedSeconds(), inputMode))
        return startEndGameCameraIfNeeded()
    }

    private fun triggerScreenShake() {
        if (!settings.screenShakeEnabled) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MINE_SHAKE_DURATION_MS
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val falloff = 1f - progress
                val easedFalloff = falloff * falloff * falloff
                val amplitude = 18f.dpF * easedFalloff
                binding.root.translationX = sin(progress * MINE_SHAKE_CYCLES * Math.PI.toFloat() * 2f) * amplitude
                if (progress >= 1f) binding.root.translationX = 0f
            }
            start()
        }
    }

    private fun startEndGameCameraIfNeeded(): Boolean {
        if (endGameCameraStarted) return false
        if (game.state != GameState.WON && game.state != GameState.LOST) return false
        endGameCameraStarted = true
        boardBusy = true
        binding.boardView.setInteractionsEnabled(false)
        binding.boardView.animateToFullBoard {
            boardBusy = false
            binding.boardLoading.isVisible = false
            binding.boardView.setInteractionsEnabled(true)
        }
        return true
    }

    private fun updateHeader() {
        binding.mineCountText.text = game.remainingMines().toString()
        binding.timerText.text = formatElapsed(currentElapsedSeconds() * 1000L)
        binding.recentModeText.text = selectedMode.name.ifBlank { formatModeMeta(selectedMode) }
        binding.recentStatusText.text = when (game.state) {
            GameState.WON -> winHeadline()
            GameState.LOST -> lossHeadline()
            else -> getString(R.string.scoreboard_title_default)
        }
    }

    private fun updateInputModeUi() {
        val terminal = ::game.isInitialized && (game.state == GameState.WON || game.state == GameState.LOST)
        binding.inputToggleGroup.isVisible = settings.showInputToggle && !selectedMode.noFlagMode && !terminal
        styleToggle(binding.revealToggle, inputMode == InputMode.REVEAL)
        styleToggle(binding.flagToggle, inputMode == InputMode.FLAG)
    }

    private fun formatModeMeta(mode: GameMode): String {
        val tags = listOfNotNull(
            getString(R.string.no_guess_short).takeIf { mode.noGuess },
            getString(R.string.no_flag_short).takeIf { mode.noFlagMode }
        ).joinToString(" ")
        val suffix = tags.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""
        return getString(R.string.mode_meta, mode.width, mode.height, mode.mines, suffix)
    }

    private fun defaultInputMode(): InputMode {
        return if (selectedMode.noFlagMode) InputMode.REVEAL else if (settings.flagModeDefault) InputMode.FLAG else InputMode.REVEAL
    }

    private fun triggerActionHaptic() {
        if (settings.vibrateEnabled) {
            binding.boardView.performActionHaptic()
        }
    }

    private fun showNoGuessFailedDialog() {
        startActivity(Intent(this, NoGuessFailedActivity::class.java))
    }

    private fun isPristineGame(): Boolean {
        return game.state == GameState.READY &&
            !game.boardGenerated &&
            game.revealedCount == 0 &&
            game.flagsCount == 0
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                if (game.state == GameState.RUNNING) {
                    binding.timerText.text = formatElapsed(currentElapsedSeconds() * 1000L)
                }
                delay(1000)
            }
        }
    }

    private fun currentElapsedSeconds(): Int {
        return when (game.state) {
            GameState.RUNNING -> ((SystemClock.elapsedRealtime() - startedAtMs) / 1000L).toInt()
            else -> carriedElapsedSeconds
        }
    }

    private fun applyPalette() {
        binding.root.setBackgroundColor(palette.background)
        binding.boardView.setPalette(palette)
        tintIconButton(binding.backButton)
        tintIconButton(binding.settingsButton)
        stylePanel(binding.inputToggleGroup, palette.panel, 24f)
        stylePanel(binding.recentPanel, palette.panel, 24f)
        updateInputModeUi()
        binding.mineCountText.setTextColor(palette.ink)
        binding.timerText.setTextColor(palette.inkSoft)
        binding.recentStatusText.setTextColor(palette.ink)
        binding.recentModeText.setTextColor(palette.inkSoft)
    }

    private fun maybeRecordFinishedGame() {
        if (gameResultRecorded) return
        if (game.state != GameState.WON && game.state != GameState.LOST) return
        val record = RecentGameRecord(
            modeId = repository.scoreKey(selectedMode),
            won = game.state == GameState.WON,
            elapsedSeconds = currentElapsedSeconds(),
            finishedAtEpochMs = System.currentTimeMillis()
        )
        repository.appendRecentGame(selectedMode, record)
        latestFinishedRecord = record
        gameResultRecorded = true
    }

    private fun updateRecentPanel() {
        val terminal = game.state == GameState.WON || game.state == GameState.LOST
        binding.recentPanel.isVisible = terminal && settings.showTopClears
        if (!terminal) return
        if (!settings.showTopClears) return
        binding.recentList.removeAllViews()
        repository.loadRecentGames(selectedMode)
            .filter { it.won }
            .sortedBy { it.elapsedSeconds }
            .take(5)
            .forEachIndexed { index, record ->
                binding.recentList.addView(
                    createScoreRow(
                        index = index,
                        leftText = "${index + 1}: ${formatFinishedAt(record.finishedAtEpochMs)}",
                        rightText = formatElapsed(record.elapsedSeconds * 1000L),
                        highlighted = isLatestFinishedRecord(record)
                    )
                )
            }
        if (binding.recentList.childCount == 0) {
            binding.recentList.addView(createScoreRow(0, getString(R.string.scoreboard_empty), "", false))
        }
    }

    private fun createScoreRow(index: Int, leftText: String, rightText: String, highlighted: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = if (index == 0) 10.dp else 6.dp }
            val typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                if (highlighted) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = leftText
                textSize = 13f
                setTextColor(palette.inkSoft)
                this.typeface = typeface
            })
            addView(TextView(context).apply {
                text = rightText
                textSize = 13f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                setTextColor(palette.inkSoft)
                this.typeface = typeface
            })
        }
    }

    private fun tintIconButton(button: android.widget.ImageButton) {
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((palette.panel and 0x00FFFFFF) or (0xD0000000.toInt()))
        }
        button.imageTintList = ColorStateList.valueOf(palette.ink)
    }

    private fun stylePanel(view: View, color: Int, radiusDp: Float) {
        view.background = GradientDrawable().apply {
            cornerRadius = radiusDp.dpF
            setColor(color)
        }
    }

    private fun styleToggle(view: TextView, selected: Boolean) {
        view.background = GradientDrawable().apply {
            cornerRadius = 20f.dpF
            setColor(if (selected) palette.accent else android.graphics.Color.TRANSPARENT)
        }
        view.setTextColor(if (selected) palette.revealedCell else palette.ink)
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes == 0) "${seconds}s" else "${minutes}m ${seconds.toString().padStart(2, '0')}s"
    }

    private fun formatFinishedAt(epochMs: Long): String {
        val date = Date(epochMs)
        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }

    private fun winHeadline(): String {
        val options = listOf("Nice!", "Great job!", "Clean!", "Strong!", "Perfect!")
        return options[currentElapsedSeconds().mod(options.size)]
    }

    private fun lossHeadline(): String {
        val options = listOf("Again!", "Next one!", "Close!", "Almost!", "Retry!")
        return options[currentElapsedSeconds().mod(options.size)]
    }

    private fun isLatestFinishedRecord(record: RecentGameRecord): Boolean {
        val latest = latestFinishedRecord ?: return false
        return latest.modeId == record.modeId &&
            latest.won == record.won &&
            latest.elapsedSeconds == record.elapsedSeconds &&
            latest.finishedAtEpochMs == record.finishedAtEpochMs
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private val Float.dpF: Float
        get() = this * resources.displayMetrics.density
}
