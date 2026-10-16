package `in`.wedevit.care

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Supporting category colours only; Rust remains the primary action colour.
val SaharaSoftBlue = Color(0xFFE6EFF5)
val SaharaLavender = Color(0xFFEEE8F5)
val SaharaSoftGold get() = lerp(Cream, Gold, .42f)
val SaharaApricot get() = lerp(Cream, Peach, .6f)
