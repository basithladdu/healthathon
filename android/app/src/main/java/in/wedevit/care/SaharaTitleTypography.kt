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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
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
object SaharaRadius { val Sm = 12.dp; val Md = 14.dp; val Lg = 16.dp; val Xl = 20.dp; val Hero = 28.dp; val Pill = 999.dp }

/** Elevation (22 Nov premium pass): flat by default, one soft step for cards, a higher one for the floating bar and sheets. Shadows are warm ([SaharaColors.Shadow]). */
object SaharaElevation { val Flat = 0.dp; val Card = 1.dp; val Raised = 3.dp; val Floating = 6.dp }

/** Spacing on a 4 dp grid: screen gutter 16, gaps between cards 12, inside cards 16, between sections 24. */
object SaharaSpacing { val Xs = 4.dp; val Sm = 8.dp; val Md = 12.dp; val Lg = 16.dp; val Xl = 24.dp; val Gutter = 16.dp }

/** No word is ever split across lines: whole-word wrapping, no hyphenation. Headings balance their lines. */
private fun TextStyle.words(heading: Boolean) = copy(lineBreak = if (heading) LineBreak.Heading else LineBreak.Paragraph, hyphens = Hyphens.None)

/** Titles in the display face; body and labels in Noto Sans. Sizes keep the 5 Nov floor: body >= 16 sp, secondary >= 14 sp. */
internal fun saharaTitleTypography(base: Typography = Typography()): Typography {
    fun title(s: TextStyle) = s.copy(fontFamily = SaharaTitleFont, fontWeight = FontWeight.SemiBold, lineHeightStyle = SaharaTrimmedLeading).words(true)
    fun body(s: TextStyle) = s.copy(fontFamily = SaharaBodyFont).words(false)
    fun label(s: TextStyle) = body(s).copy(lineHeightStyle = SaharaTrimmedLeading)
    return Typography(
        displayLarge = title(base.displayLarge), displayMedium = title(base.displayMedium), displaySmall = title(base.displaySmall),
        headlineLarge = title(base.headlineLarge), headlineMedium = title(base.headlineMedium), headlineSmall = title(base.headlineSmall),
        titleLarge = title(base.titleLarge), titleMedium = title(base.titleMedium), titleSmall = title(base.titleSmall),
        bodyLarge = body(base.bodyLarge), bodyMedium = body(base.bodyMedium), bodySmall = body(base.bodySmall),
        labelLarge = label(base.labelLarge), labelMedium = label(base.labelMedium), labelSmall = label(base.labelSmall),
    )
}

/**
 * Titles and labels drop the extra leading above the first line and below the last, so a one-line title or button label sits
 * where its padding says. Body text keeps its full 1.5x leading (Trim.None) for easy reading over several lines.
 */
private val SaharaTrimmedLeading = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/**
 * Indic scripts other than Devanagari (Telugu, Kannada, Tamil, Malayalam, Bengali, Gujarati, Punjabi) set visibly smaller
 * than Latin at the same size, with tall marks above and below the line (meeting 24 Nov). Their text is 1.08x larger with
 * 1.15x line height; English, Hindi and Marathi (Devanagari) and Urdu stay at 1.0.
 */
internal object SaharaScriptScale {
    private val larger = setOf(CareLang.TE, CareLang.KN, CareLang.TA, CareLang.ML, CareLang.BN, CareLang.GU, CareLang.PA)
    fun fontScale(lang: CareLang): Float = if (lang in larger) 1.08f else 1f
    fun lineHeightScale(lang: CareLang): Float = if (lang in larger) 1.15f else 1f
}

/** Scales one style for [lang]'s script ([SaharaScriptScale]); unspecified sizes stay unspecified. */
internal fun TextStyle.forScript(lang: CareLang): TextStyle {
    val size = SaharaScriptScale.fontScale(lang); val line = SaharaScriptScale.lineHeightScale(lang)
    if (size == 1f && line == 1f) return this
    return copy(fontSize = if (fontSize.isSpecified) fontSize * size else fontSize, lineHeight = if (lineHeight.isSpecified) lineHeight * line else lineHeight)
}

/** Every role of [typography] scaled for [lang]'s script. */
internal fun saharaScriptTypography(typography: Typography, lang: CareLang): Typography {
    if (SaharaScriptScale.fontScale(lang) == 1f && SaharaScriptScale.lineHeightScale(lang) == 1f) return typography
    return with(typography) { copy(
        displayLarge = displayLarge.forScript(lang), displayMedium = displayMedium.forScript(lang), displaySmall = displaySmall.forScript(lang),
        headlineLarge = headlineLarge.forScript(lang), headlineMedium = headlineMedium.forScript(lang), headlineSmall = headlineSmall.forScript(lang),
        titleLarge = titleLarge.forScript(lang), titleMedium = titleMedium.forScript(lang), titleSmall = titleSmall.forScript(lang),
        bodyLarge = bodyLarge.forScript(lang), bodyMedium = bodyMedium.forScript(lang), bodySmall = bodySmall.forScript(lang),
        labelLarge = labelLarge.forScript(lang), labelMedium = labelMedium.forScript(lang), labelSmall = labelSmall.forScript(lang),
    ) }
}

/**
 * The app's theme typography: [saharaTitleTypography] with the small roles raised for tired eyes and cheap screens, then
 * scaled for the language on screen ([SaharaScriptScale]). Reading [CareLanguageState.current] from the theme's composable
 * recomposes the theme when the language changes.
 */
internal fun saharaTypography(base: Typography = Typography(), lang: CareLang = CareLanguageState.current): Typography = saharaScriptTypography(saharaBaseTypography(base), lang)

private fun saharaBaseTypography(base: Typography): Typography {
    val t = saharaTitleTypography(base)
    // Premium pass (22 Nov): large titles tracked in a touch (-0.2 sp) for a calmer, set look; body lines get 1.5x leading for easy reading.
    fun tight(s: TextStyle) = s.copy(letterSpacing = (-0.2).sp)
    return t.copy(
        displayLarge = tight(t.displayLarge), displayMedium = tight(t.displayMedium), displaySmall = tight(t.displaySmall), headlineLarge = tight(t.headlineLarge), headlineMedium = tight(t.headlineMedium), headlineSmall = tight(t.headlineSmall),
        titleLarge = t.titleLarge.copy(lineHeight = 30.sp),
        bodyLarge = t.bodyLarge.copy(lineHeight = 26.sp, letterSpacing = 0.15.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        bodySmall = t.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelLarge = t.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    )
}
