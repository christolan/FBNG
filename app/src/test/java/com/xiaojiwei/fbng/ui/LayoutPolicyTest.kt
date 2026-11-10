package com.xiaojiwei.fbng.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutPolicyTest {
    @Test
    fun phonePortraitUsesABottomBar() {
        assertEquals(GameChrome.BottomBar, chooseGameChrome(400, 800, 1200, 2400, null))
    }

    @Test
    fun wideLandscapeUsesASideBar() {
        assertEquals(GameChrome.SideBar, chooseGameChrome(840, 400, 2520, 1200, null))
    }

    @Test
    fun squareCoverStaysOnABottomBar() {
        assertEquals(GameChrome.BottomBar, chooseGameChrome(700, 760, 2100, 2280, null))
    }

    @Test
    fun verticalHingeSplitsTheBookPosture() {
        val fold = FoldSnapshot(orientationVertical = true, separating = true, hingeStartPx = 900, hingeEndPx = 980)
        assertEquals(GameChrome.SplitVertical, chooseGameChrome(800, 800, 2000, 2000, fold))
    }

    @Test
    fun horizontalHingeSplitsTheTabletopPosture() {
        val fold = FoldSnapshot(orientationVertical = false, separating = true, hingeStartPx = 900, hingeEndPx = 980)
        assertEquals(GameChrome.SplitHorizontal, chooseGameChrome(800, 900, 2000, 2200, fold))
    }

    @Test
    fun flatFoldDoesNotForceASplit() {
        val fold = FoldSnapshot(orientationVertical = true, separating = false, hingeStartPx = 900, hingeEndPx = 980)
        assertEquals(GameChrome.SideBar, chooseGameChrome(900, 800, 2000, 1800, fold))
    }

    @Test
    fun libraryUsesTwoPanesOnAWideWindowOrABookFold() {
        assertFalse(libraryUsesTwoPanes(400, null))
        assertTrue(libraryUsesTwoPanes(700, null))
        val book = FoldSnapshot(orientationVertical = true, separating = true, hingeStartPx = 400, hingeEndPx = 480)
        assertTrue(libraryUsesTwoPanes(400, book))
    }
}
