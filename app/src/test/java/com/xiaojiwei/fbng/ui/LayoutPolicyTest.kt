package com.xiaojiwei.fbng.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutPolicyTest {
    @Test
    fun libraryUsesTwoPanesOnAWideWindowOrABookFold() {
        assertFalse(libraryUsesTwoPanes(400, null))
        assertTrue(libraryUsesTwoPanes(700, null))
        val book = FoldSnapshot(orientationVertical = true, separating = true, hingeStartPx = 400, hingeEndPx = 480)
        assertTrue(libraryUsesTwoPanes(400, book))
    }

    @Test
    fun shellWidthUsesTheWindowSizeClassBreakpoints() {
        assertEquals(ShellWidth.Compact, shellWidth(599))
        assertEquals(ShellWidth.Medium, shellWidth(SHELL_MEDIUM_DP))
        assertEquals(ShellWidth.Medium, shellWidth(SHELL_EXPANDED_DP - 1))
        assertEquals(ShellWidth.Expanded, shellWidth(SHELL_EXPANDED_DP))
    }

    @Test
    fun navigationRailStartsAtMediumAndOnABookFold() {
        assertFalse(shellUsesNavigationRail(599, null))
        assertTrue(shellUsesNavigationRail(SHELL_MEDIUM_DP, null))
        assertTrue(shellUsesNavigationRail(SHELL_EXPANDED_DP, null))
        val book = FoldSnapshot(orientationVertical = true, separating = true, hingeStartPx = 400, hingeEndPx = 480)
        assertTrue(shellUsesNavigationRail(400, book))
        val tabletop = FoldSnapshot(orientationVertical = false, separating = true, hingeStartPx = 400, hingeEndPx = 480)
        assertFalse(shellUsesNavigationRail(400, tabletop))
    }
}
