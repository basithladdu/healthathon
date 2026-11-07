package `in`.wedevit.care

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Sahara's colour system (7 Nov 2026, "calm" pass after the lead doctor's feedback that the vivid palette did not feel calm for
 * a cancer patient): warm cream and paper grounds, soft sage green for buttons and the selected state, and muted, gentle
 * section tones (dusty rose, slate blue, mauve, ochre, clay) instead of bright saturated ones. Each section still owns one
 * SOLID for tiles, chips, headers and buttons with white text, and a darker INK for small text on light grounds. True red is
 * kept only for SOS, emergency and danger states (Error). A deep sage-green header band for the welcome panel. Checked in SaharaPaletteTest: white on every solid >= 4.5:1 except Marigold
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

    // Header band (welcome panel): deep sage green with white text (7:1).
    val Band = Color(0xFF335F50)
    val BandRaised = Color(0xFF447263)
    val OnBand = Color.White
    val OnBandSub = Color(0xFFDCEBE3)

    // Calm header (7 Nov): the screen's own cream ground, dark ink text, a hairline below. Replaces the maroon band on the top bar;
    // [Band] stays for the welcome panel. Ink on Ground is above 7:1.
    val Header = Ground
    val OnHeader = Ink
    val OnHeaderSub = InkSub
    val HeaderRaised = Well
    val HeaderLine = Line

    // Primary: soft sage green for buttons, white text (5.4:1, and 4.9:1 as small text on cream); Pressed is the darker step.
    val Primary = Color(0xFF3B7562)
    val PrimaryPressed = Color(0xFF2C5A4A)
    val PrimaryWash = Color(0xFFDDEBE3)
    val OnPrimary = Color.White

    // Status: never reuse a section accent for these.
    val Success = Color(0xFF3F7A52); val SuccessWash = Color(0xFFDDEDE2)
    val Warning = Color(0xFF9A6420); val WarningWash = Color(0xFFF9EBCF)
    /** True red: SOS, emergency and danger only. */
    val Error = Color(0xFFD32F2F); val ErrorWash = Color(0xFFFDE0E0)
    val Info = Color(0xFF43709F); val InfoWash = Color(0xFFE1ECF6)

    /** Round 3: the soft warm wash behind Today, so white and tinted cards stand off it (ink keeps >= 7:1). */
    val GroundWarm = Color(0xFFFBF1E3)
}

/**
 * One section colour: [solid] is the bright fill for tiles, chips, headers and big buttons, [onSolid] the bold text and
 * icon on it, [wash] a light background for pills and rows, and [ink] the darker step for small text and icons on
 * [wash], cream or white (also the pressed step of a solid button). Pick one tone per section and use it everywhere.
 */
enum class SaharaTone(val solid: Color, val onSolid: Color, val wash: Color, val ink: Color) {
    /** The default brand tone (name kept from the old terracotta): now the soft sage green. */
    Rust(SaharaColors.Primary, Color.White, SaharaColors.PrimaryWash, SaharaColors.PrimaryPressed),
    Teal(Color(0xFF3A7D75), Color.White, Color(0xFFDDEEEA), Color(0xFF2C5F59)),
    Indigo(Color(0xFF5A6FA8), Color.White, Color(0xFFE4E9F5), Color(0xFF3F4F85)),
    Plum(Color(0xFF8C5A86), Color.White, Color(0xFFF1E4EF), Color(0xFF6E3F69)),
    Leaf(Color(0xFF4F7F4B), Color.White, Color(0xFFE0EEDC), Color(0xFF3A6337)),
    /** The one muted solid under 4.5:1 with white (3.7:1): bold or large text only on it; small text uses [ink]. */
    Marigold(Color(0xFFB07A2E), Color.White, Color(0xFFF8ECD3), Color(0xFF7A5214)),
    Rose(Color(0xFFA8586F), Color.White, Color(0xFFF5E3E8), Color(0xFF8A3F55)),
    Gold(Color(0xFFE6C168), SaharaColors.Ink, Color(0xFFFAF0D2), Color(0xFF6E5000)),
    Success(SaharaColors.Success, Color.White, SaharaColors.SuccessWash, Color(0xFF2F6140)),
    Warning(SaharaColors.Warning, Color.White, SaharaColors.WarningWash, Color(0xFF7A4A10)),
    Error(SaharaColors.Error, Color.White, SaharaColors.ErrorWash, Color(0xFFB71C1C)),
    Info(SaharaColors.Info, Color.White, SaharaColors.InfoWash, Color(0xFF2F5A85)),
    Neutral(SaharaColors.InkSub, Color.White, SaharaColors.Well, SaharaColors.Ink),
}

/** Fill for a section's header band with white text and back arrow: the solid, except Marigold, which steps to a deeper ochre so a small white subtitle also keeps 4.5:1. */
val SaharaTone.band: Color get() = if (this == SaharaTone.Marigold) Color(0xFF9A6A25) else solid

/** One colour per section (round 3), used for its header band, cards, list chips and rows everywhere. */
object SaharaSection {
    val Medicines = SaharaTone.Plum; val Symptoms = SaharaTone.Rose; val Visits = SaharaTone.Indigo; val CheckIns = SaharaTone.Teal
    val ToDo = SaharaTone.Leaf; val Documents = SaharaTone.Marigold; val Labs = SaharaTone.Info; val Community = SaharaTone.Rose
    val Emergency = SaharaTone.Error; val GoalsOfCare = SaharaTone.Indigo; val Help = SaharaTone.Teal; val Conversations = SaharaTone.Marigold
}

// Supporting category colours, now the soft section washes. Older screens read these names.
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
