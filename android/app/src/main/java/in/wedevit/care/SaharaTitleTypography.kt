package `in`.wedevit.care

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Original app pairing: system serif titles and Android's default body/control font. */
internal val SaharaTitleFont = FontFamily.Serif
internal val SaharaBodyFont = FontFamily.Default

/** Preserve original role sizes, weights and spacing; body and controls stay exactly base. */
internal fun saharaTitleTypography(base: Typography = Typography()) = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SaharaTitleFont),
    displayMedium = base.displayMedium.copy(fontFamily = SaharaTitleFont),
    displaySmall = base.displaySmall.copy(fontFamily = SaharaTitleFont),
    headlineLarge = base.headlineLarge.copy(fontFamily = SaharaTitleFont),
    headlineMedium = base.headlineMedium.copy(fontFamily = SaharaTitleFont),
    headlineSmall = base.headlineSmall.copy(fontFamily = SaharaTitleFont),
    titleLarge = base.titleLarge.copy(fontFamily = SaharaTitleFont),
    titleMedium = base.titleMedium.copy(fontFamily = SaharaTitleFont),
    titleSmall = base.titleSmall.copy(fontFamily = SaharaTitleFont),
    bodyLarge = base.bodyLarge, bodyMedium = base.bodyMedium, bodySmall = base.bodySmall,
    labelLarge = base.labelLarge, labelMedium = base.labelMedium, labelSmall = base.labelSmall,
)

/**
 * The app's theme typography: [saharaTitleTypography] with the small roles raised for tired eyes and cheap screens
 * (5 Nov UI brief: body at least 16 sp, secondary text at least 14 sp). Families and weights are unchanged.
 */
internal fun saharaTypography(base: Typography = Typography()): Typography {
    val titles = saharaTitleTypography(base)
    return titles.copy(
        bodyMedium = titles.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
        bodySmall = titles.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
        labelLarge = titles.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = titles.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
        labelSmall = titles.labelSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    )
}
