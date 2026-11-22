package `in`.wedevit.care

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class SaharaTitleTypographyTest {
    @get:Rule val ui = createComposeRule()
    // Updated 23 Nov: titles are Anek Latin SemiBold and body/labels are Noto Sans (the AI-FIR pairing in SaharaTitleTypography.kt),
    // no longer the system serif and default. Sizes still come from the Material base.
    @Test fun anekTitlesAndNotoSansBodyKeepTheBaseSizes() {
        val base = Typography()
        val styled = saharaTitleTypography(base)
        assertEquals(SaharaTitleFont, styled.headlineLarge.fontFamily)
        assertEquals(SaharaTitleFont, styled.titleLarge.fontFamily)
        assertEquals(FontWeight.SemiBold, styled.titleLarge.fontWeight)
        assertNotEquals(FontFamily.Default, SaharaTitleFont)
        listOf(styled.bodyLarge, styled.bodyMedium, styled.bodySmall, styled.labelLarge, styled.labelMedium, styled.labelSmall).forEach { assertEquals(SaharaBodyFont, it.fontFamily) }
        assertEquals(base.bodyLarge.fontSize, styled.bodyLarge.fontSize)
        assertEquals(base.bodyLarge.lineHeight, styled.bodyLarge.lineHeight)
        assertEquals(base.labelSmall.fontSize, styled.labelSmall.fontSize)
        ui.setContent { CareTheme { Column {
            Heading("Care conversations")
            Text("Capture their words", style = MaterialTheme.typography.headlineSmall)
            Text("नमस्ते · Original body text", style = MaterialTheme.typography.bodyLarge)
        } } }
        ui.onNodeWithText("Care conversations").assertIsDisplayed()
        ui.onNodeWithText("नमस्ते · Original body text").assertIsDisplayed()
    }
}
