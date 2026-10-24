package `in`.wedevit.care

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

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
