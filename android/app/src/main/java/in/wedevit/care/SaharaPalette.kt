package `in`.wedevit.care

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// Supporting category colours only; Rust remains the primary action colour.
val SaharaSoftBlue = Color(0xFFDCE7F1)
val SaharaLavender = Color(0xFFE7DEF2)
val SaharaSoftGold get() = lerp(Cream, Gold, .52f)
val SaharaApricot get() = lerp(Cream, Peach, .72f)

/** Doctor walkthrough card tints. Each one carries DoctorCardInk well above 7:1. */
internal val DoctorCardOverview get() = SaharaApricot
internal val DoctorCardProgress = SaharaSoftBlue
internal val DoctorCardMedicine get() = SaharaSoftGold
internal val DoctorCardConversation = SaharaLavender
internal val DoctorCardVisit get() = lerp(Cream, Peach, .5f)
internal val DoctorCardReport get() = Paper
internal val DoctorCardMint = Color(0xFFDBEADF)

/** Text tiers for tinted cards; alpha-blended ink loses contrast, so these are solid. */
internal val DoctorCardInk = Ink
internal val DoctorCardSub = Color(0xFF55483F)
internal val DoctorCardFaint = Color(0xFF6E6056)
internal val DoctorHairline = Color(0x1F382E29)
internal val DoctorAccent = Rust

/** Status tints for intake, visits and review stages. */
internal val DoctorStatusGood = Color(0xFF2F6B46)
internal val DoctorStatusGoodBg = Color(0xFFDBEADF)
internal val DoctorStatusWarn = Color(0xFF8A5A1B)
internal val DoctorStatusWarnBg = Color(0xFFFBE8C8)
internal val DoctorStatusRest = Color(0xFF7A4332)
internal val DoctorStatusRestBg = Color(0xFFF7DDD2)

/** Rust text on tinted cards (Mint, Peach, Sage, the check-in wash): plain Rust falls under 4.5:1 there; this darker Rust stays above it on every Sahara tint. */
internal val RustText = Color(0xFF9E3B23)

/** White or dark ink, whichever reads better on a coloured fill such as a pain-strength block. */
internal fun readableOn(fill: Color): Color {
    val l = fill.luminance()
    return if ((1.05f) / (l + .05f) >= (l + .05f) / (WelcomePanelInk.luminance() + .05f)) Color.White else WelcomePanelInk
}
