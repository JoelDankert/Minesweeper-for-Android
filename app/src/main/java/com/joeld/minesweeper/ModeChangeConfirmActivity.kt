package com.joeld.minesweeper

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.joeld.minesweeper.databinding.ActivityModeChangeConfirmBinding

class ModeChangeConfirmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityModeChangeConfirmBinding
    private lateinit var palette: ThemePalette

    override fun onCreate(savedInstanceState: Bundle?) {
        val repository = PrefsRepository(this)
        val settings = repository.loadSettings()
        AppCompatDelegate.setDefaultNightMode(
            if (settings.darkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        binding = ActivityModeChangeConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        palette = ThemeCatalog.resolve(settings.themeId, settings.darkTheme)
        setupInsets()
        applyPalette()

        binding.backButton.setOnClickListener { finish() }
        binding.cancelButton.setOnClickListener { finish() }
        binding.continueButton.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
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

    private fun applyPalette() {
        binding.root.setBackgroundColor(palette.background)
        binding.topBar.setBackgroundColor(palette.background)
        binding.content.setBackgroundColor(palette.background)
        binding.titleText.setTextColor(palette.ink)
        binding.messageText.setTextColor(palette.inkSoft)

        binding.card.background = GradientDrawable().apply {
            cornerRadius = 26f.dpF
            setColor(palette.panel)
        }
        binding.cancelButton.background = GradientDrawable().apply {
            cornerRadius = 22f.dpF
            setColor(palette.panel)
        }
        binding.continueButton.background = GradientDrawable().apply {
            cornerRadius = 22f.dpF
            setColor(palette.accent)
        }
        binding.cancelButton.setTextColor(palette.ink)
        binding.continueButton.setTextColor(palette.revealedCell)

        binding.backButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.panel)
        }
        binding.backButton.imageTintList = ColorStateList.valueOf(palette.ink)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private val Float.dpF: Float
        get() = this * resources.displayMetrics.density
}
