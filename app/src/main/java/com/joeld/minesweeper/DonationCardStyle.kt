package com.joeld.minesweeper

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import com.joeld.minesweeper.databinding.ItemDonationCardBinding

/** The same donation card is used in Settings and as the first Home card. */
internal fun styleDonationCard(card: ItemDonationCardBinding, palette: ThemePalette, density: Float) {
    card.root.background = GradientDrawable().apply {
        cornerRadius = 26f * density
        setColor(palette.panel)
    }
    card.donationTitle.setTextColor(palette.ink)
    card.donationSubtitle.setTextColor(palette.inkSoft)
    card.donationIcon.imageTintList = ColorStateList.valueOf(palette.accent)
    card.donationBannerClose.imageTintList = ColorStateList.valueOf(palette.inkSoft)
    card.donationAction.background = GradientDrawable().apply {
        cornerRadius = 18f * density
        setColor(palette.accent)
    }
    card.donationAction.setTextColor(palette.revealedCell)
}
