package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SymSelectedBg get() = lerp(Cream, DoctorAccent, .2f)

internal fun symptomIcon(key: String): ImageVector = when (key) {
    "Pain" -> Icons.Default.Healing; "Tiredness" -> Icons.Default.BatteryAlert; "Sleepiness" -> Icons.Default.Bedtime
    "Nausea" -> Icons.Default.SentimentVeryDissatisfied; "Poor appetite" -> Icons.Default.Restaurant; "Breathlessness" -> Icons.Default.Air
    "Sadness" -> Icons.Default.SentimentDissatisfied; "Worry" -> Icons.Default.Psychology; "Constipation" -> Icons.Default.Wc
    "Sleep difficulty" -> Icons.Default.NightsStay; else -> Icons.Default.MoreHoriz
}

internal fun ratingColor(n: Int): Color = if (n <= 5) lerp(DoctorStatusGood, Color(0xFFB8860B), n / 5f) else lerp(Color(0xFFB8860B), Color(0xFF8E2F1C), (n - 5) / 5f)

/** A numbered, rounded section card with a short helper line. */
@Composable internal fun SymptomSection(number: Int?, title: String, helper: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (number != null) Box(Modifier.size(28.dp).clip(CircleShape).background(DoctorAccent), contentAlignment = Alignment.Center) { Text(number.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = DoctorCardInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                    if (helper != null) Text(helper, color = DoctorCardSub, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
            content()
        }
    }
}

/** Symptom tile: icon + label, warm fill and check when selected (single choice, so RadioButton). */
@Composable internal fun SymptomTile(selected: Boolean, label: String, icon: ImageVector, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.heightIn(min = 56.dp).testTag(tag).clip(shape).background(if (selected) SymSelectedBg else Cream, shape)
        .border(if (selected) 2.dp else 1.dp, if (selected) DoctorAccent else DoctorHairline, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) DoctorAccent else DoctorCardSub)
        Text(label, Modifier.weight(1f), color = DoctorCardInk, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = DoctorAccent)
    }
}

/** Pill option: filled with a check when selected. [multi] switches the semantics to Checkbox. */
@Composable internal fun SymptomPill(selected: Boolean, label: String, tag: String?, modifier: Modifier = Modifier, multi: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = modifier.heightIn(min = 48.dp).let { if (tag != null) it.testTag(tag) else it }.clip(shape).background(if (selected) DoctorAccent else Cream, shape)
        .border(1.dp, if (selected) DoctorAccent else DoctorHairline, shape)
    val clickable = if (multi) base.toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() }) else base.selectable(selected, role = Role.RadioButton, onClick = onClick)
    Row(clickable.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        if (selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color.White)
        Text(label, color = if (selected) Color.White else DoctorCardInk, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable internal fun RatingButton(number: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = ratingColor(number)
    Box(modifier.heightIn(min = 48.dp).testTag("symptom-rating-$number").clip(CircleShape).background(if (selected) c else lerp(c, Paper, .82f), CircleShape)
        .border(if (selected) 3.dp else 1.dp, if (selected) DoctorCardInk else c.copy(alpha = .5f), CircleShape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(number.toString(), color = if (selected) readableOn(c) else DoctorCardInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

/** Display the patient's actual number without inventing clinical severity bands. */
@Composable internal fun RatingReadout(rating: Int, hindi: Boolean, scale: String = SymptomDiaryRules.PERSONAL_SCALE) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            rating >= 0 -> {
                Text(rating.toString(), color = ratingColor(rating), fontFamily = SaharaTitleFont, fontSize = 44.sp, lineHeight = 48.sp)
                Text("/ 10", color = DoctorCardInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (scale == SymptomDiaryRules.WELLBEING_SCALE && rating in setOf(0, 10)) Text(if (rating == 0) {
                    if (hindi) "सबसे अच्छी तबीयत" else "Best possible well-being"
                } else if (hindi) "सबसे खराब तबीयत" else "Worst possible well-being", color = DoctorCardInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
            rating == -1 -> Text(if (hindi) "अंक नहीं बताया" else "Not rated", color = DoctorCardInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            else -> Text(if (hindi) "नीचे से एक अंक चुनें" else "Tap a number below", color = DoctorCardSub, fontSize = 15.sp)
        }
    }
}
