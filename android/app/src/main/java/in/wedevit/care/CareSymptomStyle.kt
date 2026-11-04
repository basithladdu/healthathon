package `in`.wedevit.care

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SymSelectedBg get() = lerp(Cream, DoctorAccent, .2f)

/** One colour picture per symptom (res/drawable/ic_symptom_*.xml). Keys are the stored symptom names; older keys keep their own picture. */
@DrawableRes internal fun symptomIconRes(key: String): Int = when (key.trim()) {
    "Pain" -> R.drawable.ic_symptom_pain; "Tiredness" -> R.drawable.ic_symptom_tiredness; "Sleepiness" -> R.drawable.ic_symptom_sleepiness
    "Poor appetite" -> R.drawable.ic_symptom_appetite; "Dry mouth" -> R.drawable.ic_symptom_dry_mouth; "Breathlessness" -> R.drawable.ic_symptom_breathlessness
    "Constipation" -> R.drawable.ic_symptom_constipation; "Diarrhea" -> R.drawable.ic_symptom_diarrhea; "Nausea" -> R.drawable.ic_symptom_nausea
    "Vomiting" -> R.drawable.ic_symptom_vomiting; "Difficulty swallowing" -> R.drawable.ic_symptom_swallowing; "Cough" -> R.drawable.ic_symptom_cough
    "Bowel / bladder incontinence" -> R.drawable.ic_symptom_incontinence; "Mood" -> R.drawable.ic_symptom_mood
    "Sadness" -> R.drawable.ic_symptom_sadness; "Worry" -> R.drawable.ic_symptom_worry; "Weight loss" -> R.drawable.ic_symptom_weight_loss
    "Overall well-being" -> R.drawable.ic_symptom_wellbeing; "Sleep difficulty" -> R.drawable.ic_symptom_sleep_difficulty
    else -> R.drawable.ic_symptom_other
}

/** The main colour of each symptom picture, so tints and accents next to the icon match it. */
internal fun symptomColor(key: String): Color = when (key.trim()) {
    "Pain" -> Color(0xFFDC2626); "Tiredness" -> Color(0xFFEA580C); "Sleepiness" -> Color(0xFF7C3AED)
    "Poor appetite" -> Color(0xFF0F766E); "Dry mouth" -> Color(0xFFE11D48); "Breathlessness" -> Color(0xFF0891B2)
    "Constipation" -> Color(0xFF8B5A35); "Diarrhea" -> Color(0xFFCA8A04); "Nausea" -> Color(0xFF65A30D)
    "Vomiting" -> Color(0xFF4D7C0F); "Difficulty swallowing" -> Color(0xFF9333EA); "Cough" -> Color(0xFF64748B)
    "Bowel / bladder incontinence" -> Color(0xFF0284C7); "Mood", "Sadness" -> Color(0xFF3B82F6); "Worry" -> Color(0xFFD97706)
    "Weight loss" -> Color(0xFFBE185D); "Overall well-being" -> Color(0xFFEAB308); "Sleep difficulty" -> Color(0xFF312E81)
    else -> Color(0xFF78716C)
}

/** The symptom picture as a painter. Draw it with [Image] (or Icon with tint = Color.Unspecified) so its colours stay. */
@Composable internal fun symptomIcon(key: String): Painter = painterResource(symptomIconRes(key))

/** The symptom picture in full colour. Pass [contentDescription] only when no label sits next to it. */
@Composable internal fun SymptomIcon(key: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(symptomIcon(key), contentDescription, modifier)
}

/** Round badge: a soft wash of the symptom colour behind its picture. */
@Composable internal fun SymptomIconBadge(key: String, modifier: Modifier = Modifier, size: Dp = 40.dp, contentDescription: String? = null) {
    Box(modifier.size(size).clip(CircleShape).background(lerp(Paper, symptomColor(key), .14f)), contentAlignment = Alignment.Center) {
        SymptomIcon(key, Modifier.size(size * .72f), contentDescription)
    }
}

internal fun ratingColor(n: Int): Color = if (n <= 5) lerp(DoctorStatusGood, Color(0xFFB8860B), n / 5f) else lerp(Color(0xFFB8860B), Color(0xFF8E2F1C), (n - 5) / 5f)

/** A numbered, rounded section card with a short helper line. */
@Composable internal fun SymptomSection(number: Int?, title: String, helper: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (number != null) Box(Modifier.sizeIn(minWidth = 28.dp, minHeight = 28.dp).clip(CircleShape).background(DoctorAccent), contentAlignment = Alignment.Center) { Text(number.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = DoctorCardInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp, modifier = Modifier.a11yHeading())
                    if (helper != null) Text(helper, color = DoctorCardSub, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
            content()
        }
    }
}

/** Symptom tile: icon + label, warm fill and check when selected (single choice, so RadioButton). */
@Composable internal fun SymptomTile(selected: Boolean, label: String, icon: Painter, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(modifier.heightIn(min = 56.dp).testTag(tag).clip(shape).background(if (selected) SymSelectedBg else Paper, shape)
        .border(if (selected) 2.dp else 1.dp, if (selected) DoctorAccent else DoctorHairline, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(icon, null, Modifier.size(32.dp))
        Text(label, Modifier.weight(1f), color = DoctorCardInk, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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

@Composable internal fun RatingButton(number: Int, selected: Boolean, modifier: Modifier = Modifier, tag: String = "symptom-rating-$number", onClick: () -> Unit) {
    val c = ratingColor(number)
    Box(modifier.heightIn(min = 48.dp).testTag(tag).clip(CircleShape).background(if (selected) c else lerp(c, Paper, .82f), CircleShape)
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
                    careTr(hindi, "Best possible well-being", "सबसे अच्छी तबीयत")
                } else careTr(hindi, "Worst possible well-being", "सबसे खराब तबीयत"), color = DoctorCardInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
            rating == -1 -> Text(careTr(hindi, "Not rated", "अंक नहीं बताया"), color = DoctorCardInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            else -> Text(careTr(hindi, "Tap a number below", "नीचे से एक अंक चुनें"), color = DoctorCardSub, fontSize = 15.sp)
        }
    }
}
