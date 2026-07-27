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

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = PrefsRepository(this)
        settings = repository.loadSettings()
        AppCompatDelegate.setDefaultNightMode(
            if (settings.darkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
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
        palette = ThemeCatalog.resolve(settings.themeId, settings.darkTheme)
        modes = repository.sortModesByRecency(repository.loadModes())
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
        modes.forEach { mode ->
            binding.modeStrip.addView(createModeCard(mode))
        }
        binding.createModeButton.visibility = View.VISIBLE
    }

    private fun createModeCard(mode: GameMode): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_mode_card, binding.modeStrip, false)
        val hasProgress = repository.hasContinuableProgress(mode.id)
        val continueButton = view.findViewById<TextView>(R.id.continueButton)
        val newGameButton = view.findViewById<TextView>(R.id.newGameButton)
        view.findViewById<TextView>(R.id.modeTitle).text = mode.name
        view.findViewById<TextView>(R.id.modeMeta).text = formatModeMeta(mode)
        continueButton.apply {
            visibility = if (hasProgress) View.VISIBLE else View.GONE
            setOnClickListener {
                if (repository.hasContinuableProgress(mode.id)) {
                    openGame(mode.id, true)
                }
            }
        }
        newGameButton.setOnClickListener {
            repository.clearProgress(mode.id)
            openGame(mode.id, false)
        }
        (newGameButton.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
            width = 0
            weight = if (hasProgress) 1f else 2f
            marginStart = if (hasProgress) 8.dp else 0
        }
        view.setOnLongClickListener {
            startActivity(Intent(this, ModeEditorActivity::class.java).putExtra(ModeEditorActivity.EXTRA_MODE_ID, mode.id))
            true
        }
        applyModeCardStyle(view)
        return view
    }

    private fun openGame(modeId: String, resume: Boolean) {
        repository.markModeUsed(modeId)
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_MODE_ID, modeId)
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

    private fun formatModeMeta(mode: GameMode): String {
        return getString(R.string.mode_meta, mode.width, mode.height, mode.mines, if (mode.noGuess) getString(R.string.no_guess_short) else "").trim()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private val Float.dpF: Float
        get() = this * resources.displayMetrics.density
}
