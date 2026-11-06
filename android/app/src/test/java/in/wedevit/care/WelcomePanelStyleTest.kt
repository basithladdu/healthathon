package `in`.wedevit.care

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class WelcomePanelStyleTest {
    @Test fun welcomePanelIsTheHeaderBandWithWhiteText() {
        assertEquals(SaharaColors.Band, WelcomePanelOrange)
        assertEquals(Color.White, WelcomePanelInk)
        val contrast = (WelcomePanelInk.luminance() + .05f) / (WelcomePanelOrange.luminance() + .05f)
        assertTrue("Normal-size welcome text needs at least 4.5:1 contrast", contrast >= 4.5f)
    }

    @Test fun collapsedPanelHasMoreBreathingRoomWithoutCrowdingCompactScreens() {
        assertEquals(64, welcomePanelGapDp(800, 1f))
        assertEquals(64, welcomePanelGapDp(640, 1.2f))
        assertEquals(32, welcomePanelGapDp(639, 1f))
        assertEquals(32, welcomePanelGapDp(800, 1.3f))
        assertEquals(32, welcomePanelGapDp(500, 1.5f))
    }

    @Test fun heroMovesDownWithinItsOwnRegionWithoutChangingPanelGap() {
        assertEquals(72, welcomeHeroTopPaddingDp(800, 1f))
        assertEquals(72, welcomeHeroTopPaddingDp(640, 1.2f))
        assertEquals(48, welcomeHeroTopPaddingDp(639, 1f))
        assertEquals(48, welcomeHeroTopPaddingDp(800, 1.3f))
        assertEquals(48, welcomeHeroTopPaddingDp(500, 1.5f))
        assertEquals(24, (welcomeHeroTopPaddingDp(800, 1f) - 24) / 2)
        assertEquals(12, (welcomeHeroTopPaddingDp(500, 1.5f) - 24) / 2)
        assertEquals(64, welcomePanelGapDp(800, 1f))
        assertEquals(32, welcomePanelGapDp(500, 1.5f))
    }
}
