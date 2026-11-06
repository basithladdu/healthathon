package `in`.wedevit.care

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Sahara's colour system (6 Nov 2026, HUMAN_APP_GUIDE palette): Sahara's warm family (rust, cream, paper), laid out in the
 * AI-FIR grammar: section accents, a dark header band, a cream information ground, paper surfaces, solid rust for
 * buttons and the selected state, and one distinct accent per section (Teal Today, Indigo doctor and visits,
 * Plum medicines, Leaf family, Marigold care plan and documents). Every text pair below is WCAG AA or better (checked
 * in SaharaPaletteTest): body text 4.5:1, icons and control edges 3:1.
 */
object SaharaColors {
    // Ground and ink
    val Ground = Color(0xFFFFF8EE)
    val Surface = Color(0xFFFFFDF9)
    val Well = Color(0xFFEFE7DF)
    val Ink = Color(0xFF382E29)
    val InkSub = Color(0xFF4A3E36)
    val InkFaint = Color(0xFF5E5249)
    /** Decorative dividers only. */
    val Line = Color(0xFFE6D8CC)
    /** Edges of cards, tiles and controls: 3:1 on cream and paper (WCAG 1.4.11), so a tappable card reads as one. */
    val Edge = Color(0xFF9C887B)
    val LineStrong = Color(0xFF8D7B70)

    // Header band (top bar, welcome panel): deep maroon-brown with white text.
    val Band = Color(0xFF4A1F14)
    val BandRaised = Color(0xFF63291B)
    val OnBand = Color.White
    val OnBandSub = Color(0xFFF3D9CF)

    // Primary: solid rust for buttons, white text; Pressed is the darker step.
    val Primary = Color(0xFFB9472C)
    val PrimaryPressed = Color(0xFF9E3B23)
    val PrimaryWash = Color(0xFFFFE2D2)
    val OnPrimary = Color.White

    // Status: never reuse a section accent for these.
    val Success = Color(0xFF2F6B46); val SuccessWash = Color(0xFFDBEADF)
    val Warning = Color(0xFF8A5A1B); val WarningWash = Color(0xFFFBE8C8)
    val Error = Color(0xFFB3261E); val ErrorWash = Color(0xFFF9DEDC)
    val Info = Color(0xFF3F51A3); val InfoWash = Color(0xFFE0E4F7)
}

/**
 * One section colour: [solid] for icon chips and bars, [onSolid] for the icon on it, [wash] for a tile or row
 * background and [ink] for text on that wash. Pick one tone per section and use it everywhere for that section.
 */
enum class SaharaTone(val solid: Color, val onSolid: Color, val wash: Color, val ink: Color) {
    Rust(SaharaColors.Primary, Color.White, SaharaColors.PrimaryWash, SaharaColors.PrimaryPressed),
    Teal(Color(0xFF0F6E6A), Color.White, Color(0xFFD3EEEC), Color(0xFF0F6E6A)),
    Indigo(Color(0xFF3F51A3), Color.White, Color(0xFFE0E4F7), Color(0xFF3F51A3)),
    Plum(Color(0xFF8A3B78), Color.White, Color(0xFFF4DDEE), Color(0xFF8A3B78)),
    Leaf(Color(0xFF386D29), Color.White, Color(0xFFDDEFD5), Color(0xFF386D29)),
    Marigold(Color(0xFF8F5A00), Color.White, Color(0xFFFCEBC7), Color(0xFF8F5A00)),
    Rose(Color(0xFFA8325E), Color.White, Color(0xFFF8DCE6), Color(0xFF962A52)),
    Gold(Color(0xFFF2B33D), SaharaColors.Ink, Color(0xFFFFF0CC), Color(0xFF7A5300)),
    Success(SaharaColors.Success, Color.White, SaharaColors.SuccessWash, SaharaColors.Success),
    Warning(SaharaColors.Warning, Color.White, SaharaColors.WarningWash, SaharaColors.Warning),
    Error(SaharaColors.Error, Color.White, SaharaColors.ErrorWash, SaharaColors.Error),
    Info(SaharaColors.Info, Color.White, SaharaColors.InfoWash, SaharaColors.Info),
    Neutral(SaharaColors.InkSub, Color.White, SaharaColors.Well, SaharaColors.Ink),
}

// Supporting category colours, now the vivid section washes. Older screens read these names.
val SaharaSoftBlue = SaharaTone.Indigo.wash
val SaharaLavender = SaharaTone.Plum.wash
val SaharaSoftGold = SaharaTone.Gold.wash
val SaharaApricot = SaharaTone.Marigold.wash

/** Doctor walkthrough card tints, one section colour each. DoctorCardInk/Sub/Faint stay AA on all of them. */
internal val DoctorCardOverview get() = SaharaTone.Rust.wash
internal val DoctorCardProgress = SaharaTone.Indigo.wash
internal val DoctorCardMedicine get() = SaharaTone.Gold.wash
internal val DoctorCardConversation = SaharaTone.Plum.wash
internal val DoctorCardVisit get() = SaharaTone.Marigold.wash
internal val DoctorCardReport get() = SaharaColors.Surface
internal val DoctorCardMint = SaharaTone.Teal.wash

/** Text tiers: solid colours, never alpha-blended ink (faded text on a Redmi, 4 Nov). */
internal val DoctorCardInk = SaharaColors.Ink
internal val DoctorCardSub = SaharaColors.InkSub
internal val DoctorCardFaint = SaharaColors.InkFaint
internal val DoctorHairline = SaharaColors.Edge
internal val DoctorAccent = SaharaColors.Primary

/** Status tints for intake, visits and review stages. */
internal val DoctorStatusGood = SaharaColors.Success
internal val DoctorStatusGoodBg = SaharaColors.SuccessWash
internal val DoctorStatusWarn = SaharaColors.Warning
internal val DoctorStatusWarnBg = SaharaColors.WarningWash
internal val DoctorStatusRest = SaharaColors.Error
internal val DoctorStatusRestBg = SaharaColors.ErrorWash

/** Rust text and icons on white or on any section wash (>= 6:1). */
internal val RustText = SaharaColors.PrimaryPressed

/** White or dark ink, whichever reads better on a coloured fill such as a pain-strength block. */
internal fun readableOn(fill: Color): Color {
    val l = fill.luminance()
    return if ((1.05f) / (l + .05f) >= (l + .05f) / (SaharaColors.Ink.luminance() + .05f)) Color.White else SaharaColors.Ink
}
