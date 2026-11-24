@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalTime
import java.util.Locale

/*
 * Times are tapped, never typed (owner, 24 Nov: typing the time is needless strain for older patients).
 * Values stay "HH:mm" (24-hour, two digits each), exactly what CareReminderRules.validTimes accepts, so nothing downstream changes;
 * only what is shown is 12-hour ("8:30 AM").
 */

/** Most reminder times one medicine may carry (CareReminderRules.validTimes). */
const val CARE_TIME_MAX = 6

private val careClockPattern = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")

/** "08:30" as a clock time; null for anything that is not a saved HH:mm value. */
internal fun careClock(value: String): LocalTime? = value.trim().takeIf { it.matches(careClockPattern) }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

/** The stored form: always "HH:mm" with Latin digits, whatever the phone's language. */
internal fun careClockValue(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

/** "08:30" shown as "8:30 AM" (in the chosen language's AM/PM words). Anything else is shown as it is. */
internal fun careClockLabel(value: String, hindi: Boolean): String = careClock(value)?.let { careDateFormat("h:mm a", hindi).format(it) } ?: value

/**
 * One time, chosen by tapping: a large button showing the time ("8:30 AM") or "Choose time", which opens the Material clock dial (12-hour) with OK / Cancel.
 * [value] and what [onChange] receives are "HH:mm", or "" when [allowClear] lets the time be removed. [modifier] (and its test tag) sits on the button itself.
 */
@Composable fun CareTimeButton(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, allowClear: Boolean = false) {
    val t = careText(); val hindi = careHindi()
    var picking by remember { mutableStateOf(false) }
    val chosen = careClock(value)
    if (picking) CareTimeDialog(chosen ?: LocalTime.of(8, 0), label, onPick = { picked -> picking = false; onChange(picked) }, onDismiss = { picking = false })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { picking = true }, enabled = enabled, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper, contentColor = DoctorCardInk),
            modifier = modifier.weight(1f).heightIn(min = 56.dp)) {
            Icon(Icons.Default.Schedule, null, Modifier.size(22.dp), tint = RustText); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, color = DoctorCardSub, maxLines = 1)
                Text(if (chosen != null) careClockLabel(value, hindi) else t("Choose time", "समय चुनें"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1)
            }
        }
        if (allowClear && chosen != null) IconButton(onClick = { onChange("") }, enabled = enabled, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Close, t("Remove time", "समय हटाएँ")) }
    }
}

/**
 * Several times for one medicine: quick-choice chips (Morning, Afternoon, Evening, Night at [quick]) that toggle, any other chosen time as a chip
 * that removes itself when tapped, and "+ Other time", which opens the clock dial. At most [CARE_TIME_MAX] times; [onChange] always gets them
 * distinct and sorted, as "HH:mm". Test tags: "[tagPrefix]-HH:mm" on each quick chip, "[tagPrefix]-other" on the "+ Other time" chip,
 * "[tagPrefix]-chosen-HH:mm" on each other chosen time.
 */
@Composable fun CareTimeChips(times: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    quick: List<String> = MedicineDoseRules.commonTimes, tagPrefix: String = "care-time") {
    val t = careText(); val hindi = careHindi(); val lang = careLanguage()
    var picking by remember { mutableStateOf(false) }
    val full = times.size >= CARE_TIME_MAX
    fun set(next: List<String>) = onChange(next.distinct().sorted().take(CARE_TIME_MAX))
    if (picking) CareTimeDialog(LocalTime.of(8, 0), t("Other time", "दूसरा समय"), onPick = { picked -> picking = false; if (picked !in times && !full) set(times + picked) }, onDismiss = { picking = false })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { time ->
                val slot = MedicineDoseRules.slot(time); val on = time in times
                FilterChip(selected = on, enabled = enabled && (on || !full), onClick = { set(if (on) times - time else times + time) },
                    label = { Text("${careTr(lang, slot.english, slot.hindi)} · ${careClockLabel(time, hindi)}", fontSize = 16.sp, maxLines = 1, softWrap = false, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
                    leadingIcon = if (on) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null, shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = DoctorStatusGoodBg, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = DoctorStatusGood),
                    modifier = Modifier.heightIn(min = 56.dp).testTag("$tagPrefix-$time"))
            }
            times.filter { it !in quick }.forEach { time ->
                val shown = careClockLabel(time, hindi)
                InputChip(selected = true, enabled = enabled, onClick = { set(times - time) },
                    label = { Text(shown, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) },
                    trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50),
                    colors = InputChipDefaults.inputChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = DoctorStatusGoodBg, selectedLabelColor = DoctorCardInk, selectedTrailingIconColor = DoctorCardInk),
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = shown + ". " + t("Remove time", "समय हटाएँ") }.testTag("$tagPrefix-chosen-$time"))
            }
            if (enabled && !full) SaharaAddChip(t("Other time", "दूसरा समय"), { picking = true }, Modifier.heightIn(min = 56.dp).testTag("$tagPrefix-other"), tone = SaharaTone.Plum)
        }
        if (full) Text(t("Up to six times.", "ज़्यादा से ज़्यादा छह समय।"), fontSize = 14.sp, color = DoctorCardSub)
    }
}

/** The Material clock dial (12-hour, AM/PM) in a calm dialog; [onPick] gets "HH:mm". */
@Composable private fun CareTimeDialog(initial: LocalTime, title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val t = careText()
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Paper, tonalElevation = 0.dp, modifier = Modifier.testTag("care-time-dialog")) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.fillMaxWidth())
                TimePicker(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp).testTag("care-time-cancel")) { Text(t("Cancel", "रद्द करें"), fontSize = 16.sp, color = RustText) }
                    Button(onClick = { onPick(careClockValue(state.hour, state.minute)) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent),
                        modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 96.dp).testTag("care-time-ok")) { Text(t("OK", "ठीक है"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
