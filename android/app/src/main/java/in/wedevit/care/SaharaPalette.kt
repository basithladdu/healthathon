package `in`.wedevit.care

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Sahara's colour system (6 Nov 2026, round 2 "vivid"): Sahara's warm family (terracotta, cream, paper) in AI-FIR's
 * multi-colour grammar. Each section owns one bright, saturated SOLID used on tiles, chips, headers and big buttons with
 * bold white text, and a darker INK for small text on light grounds. Solid rust-orange for buttons and the selected
 * state; a dark oxblood header band. Checked in SaharaPaletteTest: white on every solid >= 4.5:1 except Marigold
 * (3.25:1, bold or large text only), ink on its wash >= 5:1, body text 4.5:1, icons and control edges 3:1.
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
    val Band = Color(0xFF521A0C)
    val BandRaised = Color(0xFF74230F)
    val OnBand = Color.White
    val OnBandSub = Color(0xFFF6D5C8)

    // Calm header (7 Nov): the screen's own cream ground, dark ink text, a hairline below. Replaces the maroon band on the top bar;
    // [Band] stays for the welcome panel. Ink on Ground is above 7:1.
    val Header = Ground
    val OnHeader = Ink
    val OnHeaderSub = InkSub
    val HeaderRaised = Well
    val HeaderLine = Line

    // Primary: bright terracotta orange-red for buttons, white text (4.85:1, so it is also safe as small text on cream); Pressed is the darker step.
    val Primary = Color(0xFFD2390B)
    val PrimaryPressed = Color(0xFFA82A05)
    val PrimaryWash = Color(0xFFFFE1D3)
    val OnPrimary = Color.White

    // Status: never reuse a section accent for these.
    val Success = Color(0xFF188038); val SuccessWash = Color(0xFFD4EFDC)
    val Warning = Color(0xFFB45309); val WarningWash = Color(0xFFFDEBC8)
    val Error = Color(0xFFD32F2F); val ErrorWash = Color(0xFFFDE0E0)
    val Info = Color(0xFF0277BD); val InfoWash = Color(0xFFD6EEFB)

    /** Round 3: the soft warm wash behind Today, so white and tinted cards stand off it (ink keeps >= 7:1). */
    val GroundWarm = Color(0xFFFFEBD9)
}

/**
 * One section colour: [solid] is the bright fill for tiles, chips, headers and big buttons, [onSolid] the bold text and
 * icon on it, [wash] a light background for pills and rows, and [ink] the darker step for small text and icons on
 * [wash], cream or white (also the pressed step of a solid button). Pick one tone per section and use it everywhere.
 */
enum class SaharaTone(val solid: Color, val onSolid: Color, val wash: Color, val ink: Color) {
    Rust(SaharaColors.Primary, Color.White, SaharaColors.PrimaryWash, SaharaColors.PrimaryPressed),
    Teal(Color(0xFF00847A), Color.White, Color(0xFFCDF3EE), Color(0xFF00675F)),
    Indigo(Color(0xFF2447D8), Color.White, Color(0xFFDCE3FF), Color(0xFF1B35A8)),
    Plum(Color(0xFFC2178E), Color.White, Color(0xFFFCDDF1), Color(0xFF8E0F67)),
    Leaf(Color(0xFF28842E), Color.White, Color(0xFFD7F2D5), Color(0xFF1E6A22)),
    /** The one bright solid under 4.5:1 with white (3.25:1): bold or large text only on it; small text uses [ink]. */
    Marigold(Color(0xFFE36D00), Color.White, Color(0xFFFFE6C7), Color(0xFF8F4300)),
    Rose(Color(0xFFD81B60), Color.White, Color(0xFFFFDCE8), Color(0xFFA3124A)),
    Gold(Color(0xFFFFB300), SaharaColors.Ink, Color(0xFFFFF0C2), Color(0xFF7A5300)),
    Success(SaharaColors.Success, Color.White, SaharaColors.SuccessWash, Color(0xFF146C2E)),
    Warning(SaharaColors.Warning, Color.White, SaharaColors.WarningWash, Color(0xFF8A3F06)),
    Error(SaharaColors.Error, Color.White, SaharaColors.ErrorWash, Color(0xFFB71C1C)),
    Info(SaharaColors.Info, Color.White, SaharaColors.InfoWash, Color(0xFF01579B)),
    Neutral(SaharaColors.InkSub, Color.White, SaharaColors.Well, SaharaColors.Ink),
}

/** Fill for a section's header band with white text and back arrow: the solid, except Marigold, which steps to a deeper orange so a small white subtitle also keeps 4.5:1. */
val SaharaTone.band: Color get() = if (this == SaharaTone.Marigold) Color(0xFFB85400) else solid

/** One colour per section (round 3), used for its header band, cards, list chips and rows everywhere. */
object SaharaSection {
    val Medicines = SaharaTone.Plum; val Symptoms = SaharaTone.Rose; val Visits = SaharaTone.Indigo; val CheckIns = SaharaTone.Teal
    val ToDo = SaharaTone.Leaf; val Documents = SaharaTone.Marigold; val Labs = SaharaTone.Info; val Community = SaharaTone.Rose
    val Emergency = SaharaTone.Error; val GoalsOfCare = SaharaTone.Indigo; val Help = SaharaTone.Teal; val Conversations = SaharaTone.Marigold
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

/** Status tints for intake, visits and review stages: the text and icon colour is each status tone's ink (>= 5:1 on its wash). */
internal val DoctorStatusGood get() = SaharaTone.Success.ink
internal val DoctorStatusGoodBg = SaharaColors.SuccessWash
internal val DoctorStatusWarn get() = SaharaTone.Warning.ink
internal val DoctorStatusWarnBg = SaharaColors.WarningWash
internal val DoctorStatusRest get() = SaharaTone.Error.ink
internal val DoctorStatusRestBg = SaharaColors.ErrorWash

/** Rust text and icons on white or on any section wash (>= 5.5:1). */
internal val RustText = SaharaColors.PrimaryPressed

/** White or dark ink, whichever reads better on a coloured fill such as a pain-strength block. */
internal fun readableOn(fill: Color): Color {
    val l = fill.luminance()
    return if ((1.05f) / (l + .05f) >= (l + .05f) / (SaharaColors.Ink.luminance() + .05f)) Color.White else SaharaColors.Ink
}
