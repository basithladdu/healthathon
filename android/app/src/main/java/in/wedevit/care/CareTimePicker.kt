@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

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
 * One time, chosen by tapping: a large button showing the time ("8:30 AM") or "Choose time", which opens the scroll-wheel time picker (hour, minute, AM/PM) with OK / Cancel.
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
            // The sun, sunset or moon of the chosen time (point 6); a clock before a time is chosen.
            if (chosen != null) DoseSlotIcon(MedicineDoseRules.slot(value), 26, describe = false) else Icon(Icons.Default.Schedule, null, Modifier.size(22.dp), tint = RustText)
            Spacer(Modifier.width(12.dp))
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
 * that removes itself when tapped, and "+ Other time", which opens the scroll-wheel time picker. At most [CARE_TIME_MAX] times; [onChange] always gets them
 * distinct and sorted, as "HH:mm". Test tags: "[tagPrefix]-HH:mm" on each quick chip, "[tagPrefix]-other" on the "+ Other time" chip,
 * "[tagPrefix]-chosen-HH:mm" on each other chosen time.
 */
@Composable fun CareTimeChips(times: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    quick: List<String> = MedicineDoseRules.commonTimes, tagPrefix: String = "care-time") {
    val t = careText(); val hindi = careHindi(); val lang = careLanguage()
    var picking by remember { mutableStateOf(false) }
    val tick = rememberSaharaTick()
    val full = times.size >= CARE_TIME_MAX
    fun set(next: List<String>) = onChange(next.distinct().sorted().take(CARE_TIME_MAX))
    if (picking) CareTimeDialog(LocalTime.of(8, 0), t("Other time", "दूसरा समय"), onPick = { picked -> picking = false; if (picked !in times && !full) set(times + picked) }, onDismiss = { picking = false })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { time ->
                val slot = MedicineDoseRules.slot(time); val on = time in times
                // Same look as before; the fill now glides to green and the chip gives one soft bounce when chosen.
                FilterChip(selected = on, enabled = enabled && (on || !full), onClick = { tick(); set(if (on) times - time else times + time) },
                    label = { Text("${careTr(lang, slot.english, slot.hindi)} · ${careClockLabel(time, hindi)}", fontSize = 16.sp, maxLines = 2, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
                    // Sun for Morning, bright sun for Afternoon, sunset for Evening, moon for Night (point 6); a tick once chosen.
                    leadingIcon = { DoseSlotIcon(slot, 22, describe = false) },
                    trailingIcon = if (on) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = DoctorStatusGood) } } else null, shape = RoundedCornerShape(50),
                    colors = medicineChipColors(on),
                    modifier = Modifier.saharaSelectBounce(on, .08f).heightIn(min = 56.dp).testTag("$tagPrefix-$time"))
            }
            times.filter { it !in quick }.forEach { time ->
                val shown = careClockLabel(time, hindi)
                InputChip(selected = true, enabled = enabled, onClick = { tick(); set(times - time) },
                    label = { Text(shown, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2) },
                    leadingIcon = { DoseSlotIcon(MedicineDoseRules.slot(time), 22, describe = false) },
                    trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50),
                    colors = InputChipDefaults.inputChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = DoctorStatusGoodBg, selectedLabelColor = DoctorCardInk, selectedTrailingIconColor = DoctorCardInk),
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = shown + ". " + t("Remove time", "समय हटाएँ") }.testTag("$tagPrefix-chosen-$time"))
            }
            if (enabled && !full) SaharaAddChip(t("Other time", "दूसरा समय"), { tick(); picking = true }, Modifier.heightIn(min = 56.dp).testTag("$tagPrefix-other"), tone = SaharaTone.Plum)
        }
        AnimatedVisibility(full, enter = SaharaMotion.enter, exit = SaharaMotion.exit, label = "$tagPrefix-full") {
            Text(t("Up to six times.", "ज़्यादा से ज़्यादा छह समय।"), fontSize = 14.sp, color = DoctorCardSub)
        }
    }
}

/**
 * A calm dialog with three scroll wheels, like a phone alarm clock (doctor, 24 Nov: "a scrolling thing like on the iPhone"): hour 1-12,
 * minute in fives, AM/PM. The middle row is the choice; a light tick is felt on each new row, and a row can also simply be tapped.
 * [onPick] gets "HH:mm". A saved minute that is not a multiple of five (08:07 from a scan or the doctor) gets its own row on the minute
 * wheel, so OK keeps it exactly unless the minute wheel is turned.
 */
@Composable private fun CareTimeDialog(initial: LocalTime, title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    var hour12 by remember { mutableIntStateOf(careHour12(initial.hour)) }
    val minutes = remember(initial.minute) { careMinuteRows(initial.minute) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var pm by remember { mutableStateOf(initial.hour >= 12) }
    val amWord = remember(hindi) { careDateFormat("a", hindi).format(LocalTime.of(9, 0)) }
    val pmWord = remember(hindi) { careDateFormat("a", hindi).format(LocalTime.of(21, 0)) }
    val value = careClockValue(careHour24(hour12, pm), minute)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Paper, tonalElevation = 0.dp, modifier = Modifier.testTag("care-time-dialog")) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.fillMaxWidth())
                Text(careClockLabel(value, hindi), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("care-time-shown"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CareWheel(12, hour12 - 1, { String.format(Locale.ROOT, "%d", it + 1) }, { hour12 = it + 1 }, t("Hour", "घंटा"), "care-time-hour", Modifier.weight(1f))
                    Text(":", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    CareWheel(minutes.size, minutes.indexOf(initial.minute), { String.format(Locale.ROOT, "%02d", minutes[it]) }, { minute = minutes[it] }, t("Minute", "मिनट"), "care-time-minute", Modifier.weight(1f))
                    CareWheel(2, if (pm) 1 else 0, { if (it == 0) amWord else pmWord }, { pm = it == 1 }, t("AM or PM", "AM या PM"), "care-time-ampm", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp).testTag("care-time-cancel")) { Text(t("Cancel", "रद्द करें"), fontSize = 16.sp, color = RustText) }
                    Button(onClick = { onPick(value) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent),
                        modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 96.dp).testTag("care-time-ok")) { Text(t("OK", "ठीक है"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** The minute wheel's rows: 00, 05 ... 55, plus [saved] in its place when it is not on a five, so a saved 08:07 is never rounded. */
internal fun careMinuteRows(saved: Int): List<Int> = ((0..55 step 5).toList() + listOf(saved).filter { it in 0..59 }).distinct().sorted()

/** 0-23 as the 12-hour clock's 1-12. */
internal fun careHour12(hour24: Int): Int = (hour24 % 12).let { if (it == 0) 12 else it }

/** 1-12 and AM/PM back to 0-23 (12 AM is 00, 12 PM is 12). */
internal fun careHour24(hour12: Int, pm: Boolean): Int = (hour12 % 12) + if (pm) 12 else 0

private val careWheelRow = 56.dp

/**
 * One scroll wheel: [count] rows, three in view, the middle one highlighted and chosen. It snaps to a row after a fling, ticks on each
 * new row, and a tapped row scrolls to the middle. [onSelected] gets the row index. TalkBack reads "[description]: [label]".
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun CareWheel(count: Int, initial: Int, label: (Int) -> String, onSelected: (Int) -> Unit, description: String, tag: String, modifier: Modifier = Modifier) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val tick = rememberSaharaTick()
    val scope = rememberCoroutineScope()
    val latestSelected by rememberUpdatedState(onSelected)
    val centred by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index ?: state.firstVisibleItemIndex
        }
    }
    LaunchedEffect(state) {
        var last = -1
        snapshotFlow { centred }.collect { index ->
            if (last >= 0 && index != last) tick()
            last = index
            latestSelected(index)
        }
    }
    Box(modifier.height(careWheelRow * 3).semantics { contentDescription = description + ": " + label(centred) }.testTag(tag), contentAlignment = Alignment.Center) {
        Surface(color = DoctorStatusGoodBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(careWheelRow)) {}
        LazyColumn(state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = careWheelRow), horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()) {
            items(count, contentType = { "care-wheel-row" }) { index ->
                val on = index == centred
                val ink by animateColorAsState(if (on) DoctorCardInk else DoctorCardSub, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "care-wheel-ink")
                // Like a phone's own time wheel: each row shrinks and fades smoothly with its distance from the middle while it turns.
                // The scroll position is read only in the draw layer, so a fling never recomposes the rows; the full 56 dp row stays tappable.
                Box(Modifier.fillMaxWidth().height(careWheelRow).clickable {
                    scope.launch { if (SaharaMotion.enabled) state.animateScrollToItem(index) else state.scrollToItem(index) }
                }.testTag("$tag-$index").graphicsLayer {
                    val info = state.layoutInfo
                    val row = info.visibleItemsInfo.firstOrNull { it.index == index }
                    if (row != null && row.size > 0) {
                        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val away = (abs(row.offset + row.size / 2f - middle) / row.size).coerceIn(0f, 1f)
                        val s = 1f - .25f * away
                        scaleX = s; scaleY = s; alpha = 1f - .3f * away
                    }
                }, contentAlignment = Alignment.Center) {
                    // Shrinks (down to 14 sp) rather than clips, so a long AM/PM word (Tamil முற்பகல், Kannada ಪೂರ್ವಾಹ್ನ) stays whole at large font sizes.
                    SaharaFitText(label(index), Modifier.padding(horizontal = 4.dp), style = TextStyle(fontSize = 24.sp), color = ink, maxLines = 1, min = 14f,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
