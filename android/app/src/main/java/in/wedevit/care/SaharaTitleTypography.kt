@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package `in`.wedevit.care

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sahara's type (AI-FIR pairing): Anek Latin SemiBold for titles, Noto Sans for body and controls.
 * Hindi falls back to the phone's Noto Sans Devanagari, so both languages share one design family.
 * Both files are OFL subsets in res/font (about 69 KB together; see android/licenses/fonts).
 */
internal val SaharaTitleFont = FontFamily(Font(R.font.sahara_anek_semibold, FontWeight.SemiBold))
internal val SaharaBodyFont = FontFamily(
    listOf(400, 500, 600, 700).map { w -> Font(R.font.sahara_noto_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) })

/** Radii: 12 cards and fields, 14 tiles, 16 large tiles and registers, 28 dialogs and sheets (Material 3), pill for buttons, chips and status. */
object SaharaRadius { val Sm = 12.dp; val Md = 14.dp; val Lg = 16.dp; val Hero = 28.dp; val Pill = 999.dp }

/** No word is ever split across lines: whole-word wrapping, no hyphenation. Headings balance their lines. */
private fun TextStyle.words(heading: Boolean) = copy(lineBreak = if (heading) LineBreak.Heading else LineBreak.Paragraph, hyphens = Hyphens.None)

/** Titles in the display face; body and labels in Noto Sans. Sizes keep the 5 Nov floor: body >= 16 sp, secondary >= 14 sp. */
internal fun saharaTitleTypography(base: Typography = Typography()): Typography {
    fun title(s: TextStyle) = s.copy(fontFamily = SaharaTitleFont, fontWeight = FontWeight.SemiBold).words(true)
    fun body(s: TextStyle) = s.copy(fontFamily = SaharaBodyFont).words(false)
    return Typography(
        displayLarge = title(base.displayLarge), displayMedium = title(base.displayMedium), displaySmall = title(base.displaySmall),
        headlineLarge = title(base.headlineLarge), headlineMedium = title(base.headlineMedium), headlineSmall = title(base.headlineSmall),
        titleLarge = title(base.titleLarge), titleMedium = title(base.titleMedium), titleSmall = title(base.titleSmall),
        bodyLarge = body(base.bodyLarge), bodyMedium = body(base.bodyMedium), bodySmall = body(base.bodySmall),
        labelLarge = body(base.labelLarge), labelMedium = body(base.labelMedium), labelSmall = body(base.labelSmall),
    )
}

/** The app's theme typography: [saharaTitleTypography] with the small roles raised for tired eyes and cheap screens. */
internal fun saharaTypography(base: Typography = Typography()): Typography {
    val t = saharaTitleTypography(base)
    return t.copy(
        bodyMedium = t.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        bodySmall = t.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelLarge = t.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    )
}
