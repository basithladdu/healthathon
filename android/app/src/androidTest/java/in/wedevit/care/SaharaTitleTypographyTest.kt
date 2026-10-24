package `in`.wedevit.care

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SaharaTitleTypographyTest {
    @get:Rule val ui = createComposeRule()
    @Test fun nativeTitlesKeepBodySizingAndHindiReadable() {
        val base = Typography()
        val styled = saharaTitleTypography(base)
        assertEquals(SaharaTitleFont, styled.headlineLarge.fontFamily)
        assertEquals(SaharaTitleFont, styled.titleLarge.fontFamily)
        assertEquals(FontFamily.SansSerif, SaharaTitleFont)
        assertEquals(FontFamily.Default, SaharaBodyFont)
        assertEquals(base.titleLarge.fontWeight, styled.titleLarge.fontWeight)
        assertEquals(base.bodyLarge, styled.bodyLarge)
        assertEquals(base.bodyMedium, styled.bodyMedium)
        assertEquals(base.bodySmall, styled.bodySmall)
        assertEquals(base.bodyLarge.fontSize, styled.bodyLarge.fontSize)
        assertEquals(base.bodyLarge.lineHeight, styled.bodyLarge.lineHeight)
        assertEquals(base.labelLarge, styled.labelLarge)
        assertEquals(base.labelMedium, styled.labelMedium)
        assertEquals(base.labelSmall, styled.labelSmall)
        ui.setContent { CareTheme { Column {
            Heading("Care conversations")
            Text("Capture their words", style = MaterialTheme.typography.headlineSmall)
            Text("नमस्ते · Original body text", style = MaterialTheme.typography.bodyLarge)
        } } }
        ui.onNodeWithText("Care conversations").assertIsDisplayed()
        ui.onNodeWithText("नमस्ते · Original body text").assertIsDisplayed()
    }
}
