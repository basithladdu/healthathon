package `in`.wedevit.care
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
class WelcomePanelStyleTest {
    private fun contrast(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    @Test fun primaryActionsAndSelectedRoleHaveReadableText() {
        assertTrue("Primary button text", contrast(Rust, Color.White) >= 4.5f)
        assertTrue("Selected role text", contrast(WelcomePanelInk, WelcomePanelOrange) >= 4.5f)
        assertTrue("Screen text", contrast(Ink, Cream) >= 4.5f)
    }
}
