package com.joeld.minesweeper

object DonationPromptPolicy {
    fun shouldShow(enabled: Boolean, dismissedLocalDate: String?, todayLocalDate: String): Boolean {
        return enabled && dismissedLocalDate != todayLocalDate
    }

    fun resolveEnabledAfterOptOut(currentEnabled: Boolean, confirmed: Boolean): Boolean {
        return if (confirmed) false else currentEnabled
    }
}
