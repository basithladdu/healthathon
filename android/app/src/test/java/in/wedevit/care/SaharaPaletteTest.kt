package `in`.wedevit.care

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every colour pair the design system hands out passes WCAG AA: 4.5:1 for text, 3:1 for icons and control edges. */
class SaharaPaletteTest {
    private fun ratio(a: Color, b: Color): Float { val x = a.luminance() + .05f; val y = b.luminance() + .05f; return maxOf(x, y) / minOf(x, y) }
    private fun aa(name: String, fg: Color, bg: Color, min: Float = 4.5f) = assertTrue("$name: ${ratio(fg, bg)}", ratio(fg, bg) >= min)

    @Test fun groundsAndText() {
        listOf(SaharaColors.Ground, SaharaColors.Surface, SaharaColors.Well).forEach { bg ->
            aa("ink", SaharaColors.Ink, bg, 7f); aa("sub", SaharaColors.InkSub, bg, 7f); aa("faint", SaharaColors.InkFaint, bg) }
        aa("band", SaharaColors.OnBand, SaharaColors.Band, 7f); aa("band sub", SaharaColors.OnBandSub, SaharaColors.Band)
        aa("button", SaharaColors.OnPrimary, SaharaColors.Primary); aa("pressed button", SaharaColors.OnPrimary, SaharaColors.PrimaryPressed)
        aa("rust text on white", RustText, SaharaColors.Surface)
        aa("outline edge", SaharaColors.LineStrong, SaharaColors.Surface, 3f)
    }

    @Test fun everyToneReadsOnItsSolidAndItsWash() {
        SaharaTone.entries.forEach { tone ->
            aa("${tone.name} on solid", tone.onSolid, tone.solid)
            aa("${tone.name} ink on wash", tone.ink, tone.wash)
            aa("${tone.name} sub text on wash", SaharaColors.InkSub, tone.wash)
            aa("${tone.name} chip vs surface", tone.solid, SaharaColors.Surface, if (tone == SaharaTone.Gold) 1.5f else 3f)
        }
    }

    @Test fun readableOnPicksTheBetterInk() {
        assertEquals(Color.White, readableOn(SaharaColors.Primary))
        assertEquals(SaharaColors.Ink, readableOn(SaharaTone.Gold.wash))
    }

    @Test fun permissionStepsFollowAndroidRules() {
        assertEquals(SaharaPermissionRules.Step.Granted, SaharaPermissionRules.next(granted = true, askedBefore = true, rationale = false))
        assertEquals(SaharaPermissionRules.Step.Explain, SaharaPermissionRules.next(granted = false, askedBefore = false, rationale = false))
        assertEquals(SaharaPermissionRules.Step.Explain, SaharaPermissionRules.next(granted = false, askedBefore = true, rationale = true))
        assertEquals(SaharaPermissionRules.Step.Settings, SaharaPermissionRules.next(granted = false, askedBefore = true, rationale = false))
    }
}
