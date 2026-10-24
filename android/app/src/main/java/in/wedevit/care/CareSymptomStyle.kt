package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal fun symptomIcon(key: String): ImageVector = when (key) {
    "Pain" -> Icons.Outlined.Healing; "Tiredness" -> Icons.Outlined.BatteryAlert; "Sleepiness" -> Icons.Outlined.Bedtime
    "Nausea" -> Icons.Outlined.SentimentVeryDissatisfied; "Poor appetite" -> Icons.Outlined.Restaurant; "Breathlessness" -> Icons.Outlined.Air
    "Sadness" -> Icons.Outlined.SentimentDissatisfied; "Worry" -> Icons.Outlined.Psychology; "Constipation" -> Icons.Outlined.Wc
    "Dry mouth" -> Icons.Outlined.WaterDrop; "Diarrhea" -> Icons.Outlined.Waves; "Vomiting" -> Icons.Outlined.Sick
    "Difficulty swallowing" -> Icons.Outlined.LocalDrink; "Cough" -> Icons.Outlined.Masks
    "Bowel / bladder incontinence" -> Icons.Outlined.Wc; "Overall well-being" -> Icons.Outlined.FavoriteBorder
    "Sleep difficulty" -> Icons.Outlined.NightsStay; else -> Icons.Outlined.MoreHoriz
}

/** Visible sections keep the full baseline understandable without hiding fields. */
@Composable internal fun SymptomSection(title: String, helper: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                if (helper != null) Text(helper, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
            }
            content()
        }
    }
}

/** One consistent outline icon family and a single calm selection accent. */
@Composable internal fun SymptomTile(selected: Boolean, label: String, icon: ImageVector, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.heightIn(min = 56.dp).testTag(tag).alpha(if (enabled) 1f else .55f).clip(shape).background(if (selected) Mint else Cream, shape)
        .border(if (selected) 2.dp else 1.dp, if (selected) Rust else MaterialTheme.colorScheme.outlineVariant, shape)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) Rust else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Rust)
    }
}

/** Pill option: filled with a check when selected. [multi] switches the semantics to Checkbox. */
@Composable internal fun SymptomPill(selected: Boolean, label: String, tag: String?, modifier: Modifier = Modifier, multi: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else .55f).let { if (tag != null) it.testTag(tag) else it }.clip(shape).background(if (selected) Rust else Cream, shape)
        .border(1.dp, if (selected) Rust else MaterialTheme.colorScheme.outlineVariant, shape)
    val clickable = if (multi) base.toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() }) else base.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    Row(clickable.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Color.White)
        Text(label, color = if (selected) Color.White else Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
    }
}

/** No number is stored until a real slider, endpoint or accessibility action chooses it. */
@Composable internal fun SymptomSeveritySlider(rating: Int, hindi: Boolean, scale: String, enabled: Boolean = true, onChange: (Int) -> Unit) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val chosen = rating in 0..10
    var interactionRating by remember { mutableIntStateOf(rating) }
    SideEffect { interactionRating = rating }
    val description = when {
        chosen -> "$rating / 10"
        rating == -1 -> t("Not rated", "अंक नहीं बताया")
        else -> t("Choose severity", "परेशानी का अंक चुनें")
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (chosen) "$rating" else "—", color = if (chosen) Rust else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("symptom-rating-value"))
        Text(if (chosen) t("out of 10", "10 में से") else description, color = Ink, fontSize = 16.sp,
            modifier = Modifier.testTag("symptom-rating-state"))
    }
    Text(SymptomDiaryRules.ratingAnchors(scale, hindi), color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("symptom-rating-anchors"))
    Slider(value = if (chosen) rating.toFloat() else 0f, onValueChange = {
        interactionRating = it.roundToInt().coerceIn(0, 10)
        onChange(interactionRating)
    },
        // Tapping the zero endpoint is a deliberate answer even when the unselected thumb starts there.
        onValueChangeFinished = { if (interactionRating < 0) onChange(0) }, enabled = enabled, valueRange = 0f..10f, steps = 9,
        colors = SliderDefaults.colors(thumbColor = if (chosen) Rust else MaterialTheme.colorScheme.outline,
            activeTrackColor = if (chosen) Rust else MaterialTheme.colorScheme.outlineVariant,
            inactiveTrackColor = Mint, activeTickColor = Paper, inactiveTickColor = Rust.copy(alpha = .35f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("symptom-rating-slider").clearAndSetSemantics {
            contentDescription = SymptomDiaryRules.ratingQuestion(scale, hindi)
            stateDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(if (chosen) rating.toFloat() else 0f, 0f..10f, 9)
            if (enabled) setProgress { requested ->
                if (!requested.isFinite()) false else { onChange(requested.roundToInt().coerceIn(0, 10)); true }
            } else disabled()
        })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onChange(0) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-severity-zero")) {
            Text(if (scale == SymptomDiaryRules.WELLBEING_SCALE) t("0 · best", "0 · सबसे अच्छी") else t("0 · none", "0 · बिल्कुल नहीं"))
        }
        TextButton(onClick = { onChange(10) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-severity-ten")) {
            Text(t("10 · worst", "10 · सबसे ज़्यादा"))
        }
    }
}
