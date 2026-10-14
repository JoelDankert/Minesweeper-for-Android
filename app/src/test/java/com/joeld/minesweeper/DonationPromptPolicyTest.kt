package com.joeld.minesweeper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationPromptPolicyTest {
    @Test
    fun `first open of a local day is visible`() {
        assertTrue(DonationPromptPolicy.shouldShow(true, null, "2026-10-13"))
    }

    @Test
    fun `dismissal survives reopening on the same local day`() {
        assertFalse(DonationPromptPolicy.shouldShow(true, "2026-10-13", "2026-10-13"))
    }

    @Test
    fun `dismissal rolls over on the next local day`() {
        assertTrue(DonationPromptPolicy.shouldShow(true, "2026-10-13", "2026-10-14"))
    }

    @Test
    fun `opt out always hides the prompt`() {
        assertFalse(DonationPromptPolicy.shouldShow(false, null, "2026-10-13"))
        assertFalse(DonationPromptPolicy.shouldShow(false, "2026-10-12", "2026-10-13"))
    }

    @Test
    fun `canceling opt out leaves daily prompt enabled`() {
        assertTrue(DonationPromptPolicy.resolveEnabledAfterOptOut(true, confirmed = false))
    }

    @Test
    fun `only explicit confirmation opts out`() {
        assertFalse(DonationPromptPolicy.resolveEnabledAfterOptOut(true, confirmed = true))
    }
}
