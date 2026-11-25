package `in`.wedevit.care

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pure rules for the tap age picker, kept apart from the UI so they can be unit tested. */
object CareAgePickerRules {
    const val MIN = 1
    const val MAX = 120
    /** The quick starting points: tap the nearest, then + or - to the exact age. */
    val STARTS = listOf(20, 30, 40, 50, 60, 70, 80, 90)
    /** The stored text one year up or down from [value], kept in [MIN]..[MAX]; a blank or unreadable value stays as it is. */
    fun step(value: String, by: Int): String = value.trim().toIntOrNull()?.let { (it + by).coerceIn(MIN, MAX).toString() } ?: value
}

/**
 * Age by tap (24 Nov clinical meeting: older patients tap, they do not type). Simple Mode turns itself on at 60 and above from this
 * age, so it must be easy to set right. A row of starting points (20 to 90) sets the age in one tap, then large - and + buttons move it
 * a year at a time. Tapping the picked starting point again clears it. [value] is the age as text ("62" or ""), the same text the
 * typed field used, so [CarePatientProfileRules.age] still checks it. Sahara never asks for a date of birth.
 * Tags: [tag] on the shown number, "$tag-minus" / "$tag-plus" on the buttons, "$tag-20" to "$tag-90" on the starting points.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CareAgePicker(value: String, onChange: (String) -> Unit, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    large: Boolean = false, tone: SaharaTone = SaharaTone.Marigold) {
    val t = careText()
    val tick = rememberSaharaTick()
    val age = value.trim().toIntOrNull()
    val buttonSize: Dp = if (large) 64.dp else 56.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            FilledTonalIconButton(onClick = { tick(); onChange(CareAgePickerRules.step(value, -1)) }, enabled = enabled && age != null && age > CareAgePickerRules.MIN,
                modifier = Modifier.size(buttonSize).testTag("$tag-minus")) {
                Icon(Icons.Default.Remove, t("One year less", "एक साल कम"), Modifier.size(28.dp))
            }
            val spoken = if (age == null) t("Age not set", "उम्र नहीं चुनी") else t("Age", "उम्र") + " " + age
            Box(Modifier.widthIn(min = 112.dp).testTag(tag).semantics { contentDescription = spoken }, contentAlignment = Alignment.Center) {
                SaharaAnimatedText(age?.toString() ?: "") { shown ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(shown.ifEmpty { "–" }, fontSize = if (large) 48.sp else 40.sp, lineHeight = if (large) 54.sp else 46.sp, fontWeight = FontWeight.Bold,
                            color = SaharaColors.Ink, textAlign = TextAlign.Center)
                        Text(t("years", "साल"), fontSize = 15.sp, color = SaharaColors.InkSub)
                    }
                }
            }
            FilledTonalIconButton(onClick = { tick(); onChange(CareAgePickerRules.step(value, 1)) }, enabled = enabled && age != null && age < CareAgePickerRules.MAX,
                modifier = Modifier.size(buttonSize).testTag("$tag-plus")) {
                Icon(Icons.Default.Add, t("One year more", "एक साल ज़्यादा"), Modifier.size(28.dp))
            }
        }
        Text(if (age == null) t("Tap the nearest number, then + or − to fine-tune.", "सबसे पास वाला नंबर दबाएँ, फिर + या − से ठीक करें।")
            else t("Use + or − to change it by one year.", "एक-एक साल बदलने के लिए + या − दबाएँ।"),
            Modifier.fillMaxWidth(), fontSize = 15.sp, color = SaharaColors.InkSub, textAlign = TextAlign.Center)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CareAgePickerRules.STARTS.forEach { n ->
                val on = age == n
                // The picked number eases into its colour and gives one small bounce, like the other Sahara choices.
                val fill by animateColorAsState(if (on) tone.solid else SaharaColors.Surface, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "age fill")
                val ink by animateColorAsState(if (on) tone.onSolid else SaharaColors.Ink, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "age ink")
                val edge by animateColorAsState(if (on) tone.solid else SaharaColors.Edge, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "age edge")
                Surface(checked = on, onCheckedChange = { tick(); onChange(if (on) "" else n.toString()) }, enabled = enabled, shape = CircleShape,
                    color = fill, contentColor = ink,
                    border = BorderStroke(if (on) 2.dp else 1.dp, edge),
                    modifier = Modifier.size(if (large) 56.dp else 48.dp).saharaSelectBounce(on).testTag("$tag-$n").semantics { contentDescription = t("Age", "उम्र") + " $n" }) {
                    Box(contentAlignment = Alignment.Center) { Text("$n", fontSize = 18.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
                }
            }
        }
    }
}
