package com.joeld.minesweeper

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joeld.minesweeper.databinding.ActivityDonationInfoBinding

class DonationInfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDonationInfoBinding
    private lateinit var palette: ThemePalette

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = PrefsRepository(this).loadSettings()
        AppCompatDelegate.setApplicationLocales(settings.languageMode.localeList())
        AppCompatDelegate.setDefaultNightMode(settings.nightMode())
        super.onCreate(savedInstanceState)
        binding = ActivityDonationInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        palette = ThemeCatalog.resolve(
            settings.themeId,
            settings.usesDarkPalette(this),
            settings.usesAmoledPalette(this)
        )
        applyPalette(settings)
        setupInsets()
        binding.closeButton.setOnClickListener { finish() }
        binding.buyCoffeeButton.setOnClickListener { openSupportPage() }
    }

    private fun openSupportPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/joeldankert")))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.donation_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupInsets() {
        val top = binding.topBar.paddingTop
        val bottom = binding.bottomPanel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(binding.topBar.paddingLeft, top + bars.top, binding.topBar.paddingRight, binding.topBar.paddingBottom)
            binding.bottomPanel.setPadding(binding.bottomPanel.paddingLeft, binding.bottomPanel.paddingTop, binding.bottomPanel.paddingRight, bottom + bars.bottom)
            insets
        }
    }

    private fun applyPalette(settings: AppSettings) {
        binding.root.setBackgroundColor(palette.background)
        binding.topBar.setBackgroundColor(palette.background)
        binding.bottomPanel.setBackgroundColor(palette.background)
        binding.closeButton.setTextColor(palette.ink)
        binding.topLabel.setTextColor(palette.accent)
        binding.eyebrow.setTextColor(palette.accent)
        binding.storyTitle.setTextColor(palette.ink)
        binding.storyWhyTitle.setTextColor(palette.ink)
        listOf(binding.storyIntro, binding.storyWhyBody, binding.bottomHint).forEach {
            it.setTextColor(palette.inkSoft)
        }
        binding.storyThanks.setTextColor(palette.ink)
        binding.heroPanel.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(palette.panel, ColorUtils.blendARGB(palette.panel, palette.accent, 0.20f))
        ).apply { cornerRadius = 30f.dpF }
        listOf(binding.heroTileLeft, binding.heroTileRight).forEach { tile: TextView ->
            tile.background = GradientDrawable().apply {
                cornerRadius = 16f.dpF
                setColor(palette.input)
            }
            tile.setTextColor(palette.accent)
        }
        binding.heroHeart.background = GradientDrawable().apply {
            cornerRadius = 22f.dpF
            setColor(palette.accent)
        }
        binding.heroHeart.imageTintList = ColorStateList.valueOf(palette.revealedCell)
        binding.storyThanks.background = GradientDrawable().apply {
            cornerRadius = 22f.dpF
            setColor(palette.panel)
            setStroke(1.dp, ColorUtils.blendARGB(palette.panel, palette.inkSoft, 0.25f))
        }
        binding.buyCoffeeButton.background = GradientDrawable().apply {
            cornerRadius = 20f.dpF
            setColor(palette.accent)
        }
        binding.buyCoffeeButton.setTextColor(palette.revealedCell)
        binding.bottomPanel.elevation = 8f.dpF
        window.statusBarColor = palette.background
        window.navigationBarColor = palette.background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val dark = settings.usesDarkPalette(this) || settings.usesAmoledPalette(this)
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Float.dpF: Float get() = this * resources.displayMetrics.density
}
