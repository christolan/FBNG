package com.xiaojiwei.fbng.ui

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aspect → bar side and thickness. The game rect is always the centered fit of
 * the full play area; [HudBand.Overlay] is used when the bars are thinner than
 * the touch target, and that choice does not shrink the rect.
 *
 * 56dp is the play screen's minimum comfortable bar (a 48dp control plus 4dp
 * padding on each side). At Find N6's 412 ppi that is about 144px; at the RP6's
 * 400 ppi it is 140px. These tests pass the pixel threshold directly.
 *
 * No emulator here. On device, confirm kovsh stays centered and the buttons sit
 * in the bars: Find N6 inner 2480×2248 / 2248×2480 and cover 2616×1140 /
 * 1140×2616, and RP6 landscape 1920×1080, including a rotation and a fold.
 */
class LayoutPolicyTest {
    @Test
    fun fourByThreeOnAWideLandscapePhoneUsesSideBars() {
        // 2400×1080 phone, and the RP6's 1920×1080 panel. 4:3 is narrower than both.
        val phone = layoutGameHud(2400, 1080, 4f / 3f, minBarPx = 140)
        assertEquals(HudBand.Sides, phone.band)
        assertEquals(BarAxis.Sides, phone.axis)
        assertEquals(480, phone.barPx)
        assertEquals(1440, phone.gameWidthPx)
        assertEquals(1080, phone.gameHeightPx)
        assertCentered(phone, 2400, 1080)

        val rp6 = layoutGameHud(1920, 1080, 4f / 3f, minBarPx = 140)
        assertEquals(HudBand.Sides, rp6.band)
        assertEquals(240, rp6.barPx)
        assertEquals(1440, rp6.gameWidthPx)
        assertEquals(1080, rp6.gameHeightPx)
        assertCentered(rp6, 1920, 1080)
        assertFalse(rp6.usedFallback)
    }

    @Test
    fun fourByThreeOnATallPortraitPhoneUsesEndBars() {
        val layout = layoutGameHud(1080, 2400, 4f / 3f, minBarPx = 140)
        assertEquals(HudBand.Ends, layout.band)
        assertEquals(BarAxis.Ends, layout.axis)
        assertEquals(795, layout.barPx)
        assertEquals(1080, layout.gameWidthPx)
        assertEquals(810, layout.gameHeightPx)
        assertCentered(layout, 1080, 2400)
    }

    @Test
    fun aGameThatMatchesTheScreenKeepsAFullFrameAndOverlays() {
        val match = layoutGameHud(1920, 1080, 1920f / 1080f, minBarPx = 140)
        assertEquals(HudBand.Overlay, match.band)
        assertEquals(BarAxis.None, match.axis)
        assertEquals(0, match.barPx)
        assertEquals(0, match.gameLeftPx)
        assertEquals(0, match.gameTopPx)
        assertEquals(1920, match.gameWidthPx)
        assertEquals(1080, match.gameHeightPx)
    }

    @Test
    fun thinBarsDoNotStealSpaceFromTheCenteredFrame() {
        // 1.70 on 1920×1080 leaves 42px bars, under a 140px touch target.
        val thin = layoutGameHud(1920, 1080, 1.70f, minBarPx = 140)
        val usable = layoutGameHud(1920, 1080, 1.70f, minBarPx = 42)
        assertEquals(HudBand.Overlay, thin.band)
        assertEquals(BarAxis.Sides, thin.axis)
        assertEquals(42, thin.barPx)
        assertEquals(HudBand.Sides, usable.band)
        assertEquals(thin.gameLeftPx, usable.gameLeftPx)
        assertEquals(thin.gameTopPx, usable.gameTopPx)
        assertEquals(thin.gameWidthPx, usable.gameWidthPx)
        assertEquals(thin.gameHeightPx, usable.gameHeightPx)
        assertEquals(1080, thin.gameHeightPx)
        assertCentered(thin, 1920, 1080)
    }

    @Test
    fun foldableInnerScreenLetterboxesFourByThreeOnBothOrientations() {
        // Find N6 inner panel, 2480×2248 landscape and 2248×2480 portrait.
        // 56dp at 412 ppi is ~144px; the bars are thicker than that.
        val landscape = layoutGameHud(2480, 2248, 4f / 3f, minBarPx = 144)
        assertEquals(HudBand.Ends, landscape.band)
        assertEquals(194, landscape.barPx)
        assertEquals(2480, landscape.gameWidthPx)
        assertEquals(1860, landscape.gameHeightPx)
        assertCentered(landscape, 2480, 2248)

        val portrait = layoutGameHud(2248, 2480, 4f / 3f, minBarPx = 144)
        assertEquals(HudBand.Ends, portrait.band)
        assertEquals(397, portrait.barPx)
        assertEquals(2248, portrait.gameWidthPx)
        assertEquals(1686, portrait.gameHeightPx)
        assertCentered(portrait, 2248, 2480)
    }

    @Test
    fun foldableCoverScreenPillarsInLandscapeAndLetterboxesInPortrait() {
        val landscape = layoutGameHud(2616, 1140, 4f / 3f, minBarPx = 144)
        assertEquals(HudBand.Sides, landscape.band)
        assertEquals(548, landscape.barPx)
        assertEquals(1520, landscape.gameWidthPx)
        assertEquals(1140, landscape.gameHeightPx)
        assertCentered(landscape, 2616, 1140)

        val portrait = layoutGameHud(1140, 2616, 4f / 3f, minBarPx = 144)
        assertEquals(HudBand.Ends, portrait.band)
        // 855px of game height leaves 1761px of bars; the odd pixel stays outside the game.
        assertEquals(880, portrait.barPx)
        assertEquals(880, portrait.gameTopPx)
        assertEquals(1140, portrait.gameWidthPx)
        assertEquals(855, portrait.gameHeightPx)
        assertCentered(portrait, 1140, 2616)
    }

    @Test
    fun rp6PortraitMovesTheSameGameIntoEndBars() {
        val landscape = layoutGameHud(1920, 1080, 4f / 3f, minBarPx = 140)
        val portrait = layoutGameHud(1080, 1920, 4f / 3f, minBarPx = 140)
        assertEquals(HudBand.Sides, landscape.band)
        assertEquals(HudBand.Ends, portrait.band)
        assertEquals(555, portrait.barPx)
        assertEquals(1080, portrait.gameWidthPx)
        assertEquals(810, portrait.gameHeightPx)
        assertCentered(portrait, 1080, 1920)
    }

    @Test
    fun aVerticalGameAndAWidescreenGameDoNotUseTheFourByThreeFallback() {
        val vertical = layoutGameHud(1920, 1080, 3f / 4f, minBarPx = 140)
        assertEquals(HudBand.Sides, vertical.band)
        assertEquals(555, vertical.barPx)
        assertEquals(810, vertical.gameWidthPx)
        assertEquals(0.75f, vertical.aspectUsed, 0.0001f)
        assertFalse(vertical.usedFallback)

        val wide = layoutGameHud(1920, 1080, 2f, minBarPx = 48)
        assertEquals(HudBand.Ends, wide.band)
        assertEquals(60, wide.barPx)
        assertEquals(960, wide.gameHeightPx)
        assertEquals(2f, wide.aspectUsed, 0.0001f)
        assertFalse(wide.usedFallback)
    }

    @Test
    fun anUnusableCoreAspectFallsBackToFourByThreeWithoutMovingTheFit() {
        val expected = layoutGameHud(1920, 1080, FALLBACK_GAME_ASPECT, minBarPx = 140)
        for (reported in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f, 0.04f, 21f)) {
            val layout = layoutGameHud(1920, 1080, reported, minBarPx = 140)
            assertTrue(layout.usedFallback)
            assertEquals(FALLBACK_GAME_ASPECT, layout.aspectUsed, 0.0001f)
            assertEquals(expected.band, layout.band)
            assertEquals(expected.barPx, layout.barPx)
            assertEquals(expected.gameLeftPx, layout.gameLeftPx)
            assertEquals(expected.gameWidthPx, layout.gameWidthPx)
            assertEquals(expected.gameHeightPx, layout.gameHeightPx)
        }
        assertEquals(0.05f, usableGameAspect(0.05f), 0.0001f)
        assertEquals(20f, usableGameAspect(20f), 0.0001f)
    }

    @Test
    fun theComfortThresholdIsInclusiveAndDoesNotChangeTheGameRect() {
        val atLimit = layoutGameHud(1920, 1080, 4f / 3f, minBarPx = 240)
        val tooThin = layoutGameHud(1920, 1080, 4f / 3f, minBarPx = 241)
        assertEquals(HudBand.Sides, atLimit.band)
        assertEquals(HudBand.Overlay, tooThin.band)
        assertEquals(atLimit.gameLeftPx, tooThin.gameLeftPx)
        assertEquals(atLimit.gameWidthPx, tooThin.gameWidthPx)
        assertEquals(atLimit.gameHeightPx, tooThin.gameHeightPx)
    }

    @Test
    fun aNewCoreAspectRetargetsTheBarsOnTheSameScreen() {
        val fourByThree = layoutGameHud(1920, 1080, 4f / 3f, minBarPx = 140)
        val matched = layoutGameHud(1920, 1080, 16f / 9f, minBarPx = 140)
        assertEquals(HudBand.Sides, fourByThree.band)
        assertEquals(240, fourByThree.barPx)
        assertEquals(HudBand.Overlay, matched.band)
        assertEquals(0, matched.barPx)
        assertEquals(1920, matched.gameWidthPx)
        assertEquals(1080, matched.gameHeightPx)
    }

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

    private fun assertCentered(layout: GameHudLayout, screenWidth: Int, screenHeight: Int) {
        val right = screenWidth - layout.gameLeftPx - layout.gameWidthPx
        val bottom = screenHeight - layout.gameTopPx - layout.gameHeightPx
        assertTrue(abs(layout.gameLeftPx - right) <= 1)
        assertTrue(abs(layout.gameTopPx - bottom) <= 1)
        assertEquals(layout.gameLeftPx, (screenWidth - layout.gameWidthPx) / 2)
        assertEquals(layout.gameTopPx, (screenHeight - layout.gameHeightPx) / 2)
    }
}
