package com.joeld.minesweeper

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.joeld.minesweeper.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var repository: PrefsRepository
    private lateinit var palette: ThemePalette
    private var settings = AppSettings()
    private var modes = mutableListOf<GameMode>()
    private var recentOnlyModes = emptyList<GameMode>()
    private var showRecentModes = false

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = PrefsRepository(this)
        settings = repository.loadSettings()
        AppCompatDelegate.setDefaultNightMode(settings.nightMode())
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.createModeButton.setOnClickListener {
            startActivity(Intent(this, ModeEditorActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        settings = repository.loadSettings()
        palette = ThemeCatalog.resolve(settings.themeId, settings.usesDarkPalette(this), settings.usesAmoledPalette(this))
        modes = repository.sortModesByRecency(repository.loadModes())
        recentOnlyModes = repository.loadRecentModesNotSaved(modes)
        if (modes.isEmpty()) modes = mutableListOf(repository.createMode("Easy", 8, 8, 8, true))
        applyPalette()
        rebuildModeList()
    }

    private fun setupInsets() {
        val topPadding = binding.homeTopBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.homeTopBar.setPadding(20.dp, topPadding + bars.top, 20.dp, 8.dp)
            binding.modeScroll.setPadding(20.dp, 12.dp, 20.dp, 20.dp + bars.bottom)
            insets
        }
    }

    private fun rebuildModeList() {
        binding.modeStrip.removeAllViews()
        val continuableRecentOnlyModes = recentOnlyModes.filter { repository.hasContinuableProgress(it.id) }
        val historyRecentOnlyModes = recentOnlyModes.filterNot { repository.hasContinuableProgress(it.id) }
        continuableRecentOnlyModes.forEach { mode ->
            binding.modeStrip.addView(createModeCard(mode, dimmed = true, savedMode = false))
        }
        modes.forEach { mode ->
            binding.modeStrip.addView(createModeCard(mode, dimmed = false, savedMode = true))
        }
        if (historyRecentOnlyModes.isNotEmpty()) {
            binding.modeStrip.addView(createShowMoreButton())
        }
        if (showRecentModes) {
            historyRecentOnlyModes.forEach { mode ->
                binding.modeStrip.addView(createModeCard(mode, dimmed = true, savedMode = false))
            }
        }
        binding.createModeButton.visibility = View.VISIBLE
    }

    private fun createModeCard(mode: GameMode, dimmed: Boolean, savedMode: Boolean): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_mode_card, binding.modeStrip, false)
        view.alpha = if (dimmed) 0.62f else 1f
        val hasProgress = repository.hasContinuableProgress(mode.id)
        val continueButton = view.findViewById<TextView>(R.id.continueButton)
        val newGameButton = view.findViewById<TextView>(R.id.newGameButton)
        val modeMeta = ModeTextFormatter.compactStyled(this, mode, settings, palette, palette.inkSoft)
        val modeTitle = mode.name.ifBlank { modeMeta }
        view.findViewById<TextView>(R.id.modeTitle).text = modeTitle
        view.findViewById<TextView>(R.id.modeMeta).text = if (modeTitle.toString() == modeMeta.toString()) "" else modeMeta
        continueButton.apply {
            visibility = if (hasProgress) View.VISIBLE else View.GONE
            setOnClickListener {
                if (repository.hasContinuableProgress(mode.id)) {
                    if (savedMode) openGame(mode.id, true) else openCustomGame(mode, true)
                }
            }
        }
        newGameButton.setOnClickListener {
            if (savedMode) {
                repository.clearProgress(mode.id)
                openGame(mode.id, false)
            } else {
                repository.clearProgress(mode.id)
                openCustomGame(mode, false)
            }
        }
        (newGameButton.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
            width = 0
            weight = if (hasProgress) 1f else 2f
            marginStart = if (hasProgress) 8.dp else 0
        }
        if (savedMode) {
            view.setOnClickListener {
                startActivity(Intent(this, ModeEditorActivity::class.java).putExtra(ModeEditorActivity.EXTRA_MODE_ID, mode.id))
            }
        }
        applyModeCardStyle(view)
        return view
    }

    private fun createShowMoreButton(): TextView {
        return TextView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                48.dp
            ).also { it.bottomMargin = 12.dp }
            gravity = android.view.Gravity.CENTER
            text = getString(if (showRecentModes) R.string.show_less else R.string.show_more)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 18f.dpF
                setColor(palette.panel)
            }
            setTextColor(palette.ink)
            setOnClickListener {
                showRecentModes = !showRecentModes
                rebuildModeList()
            }
        }
    }

    private fun openGame(modeId: String, resume: Boolean) {
        repository.markModeUsed(modeId)
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_MODE_ID, modeId)
                .putExtra(GameActivity.EXTRA_RESUME, resume)
        )
    }

    private fun openCustomGame(mode: GameMode, resume: Boolean) {
        repository.markModeUsed(mode.id)
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_MODE_ID, mode.id)
                .putExtra(GameActivity.EXTRA_RESUME, resume)
        )
    }

    private fun applyPalette() {
        binding.root.setBackgroundColor(palette.background)
        binding.homeTopBar.setBackgroundColor(palette.background)
        tintIconButton(binding.settingsButton)
        binding.titleText.setTextColor(palette.ink)
        styleAction(binding.createModeButton)
    }

    private fun applyModeCardStyle(view: View) {
        view.background = GradientDrawable().apply {
            cornerRadius = 26f.dpF
            setColor(palette.panel)
        }
        view.findViewById<TextView>(R.id.modeTitle).setTextColor(palette.ink)
        view.findViewById<TextView>(R.id.modeMeta).setTextColor(palette.inkSoft)
        view.findViewById<TextView>(R.id.continueButton).takeIf { it.visibility == View.VISIBLE }?.let(::styleAction)
        styleAction(view.findViewById(R.id.newGameButton))
    }

    private fun styleAction(textView: TextView) {
        textView.background = GradientDrawable().apply {
            cornerRadius = 18f.dpF
            setColor(palette.accent)
        }
        textView.setTextColor(palette.revealedCell)
    }

    private fun tintIconButton(button: android.widget.ImageButton) {
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.panel)
        }
        button.imageTintList = ColorStateList.valueOf(palette.ink)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private val Float.dpF: Float
        get() = this * resources.displayMetrics.density
}
