@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * Dates, times and body numbers are tapped, never typed (owner, 25 Nov: "why are they typing a date").
 * One shared set of pickers so every screen looks and moves the same:
 *  - CareDatePickerDialog: the Material calendar in Picker mode, with no switch to a typing box.
 *  - CareClockDialog: three scroll wheels (hour, minute in fives, AM/PM), like CareTimeButton's picker.
 *  - CareDateTimeDialog: the calendar, then the clock, for a moment in time.
 *  - CareNumberWheelButton: a number such as weight or height on one scroll wheel.
 */

/** A range of numbers shown on one wheel: [min] to [max] in [step]s. Pure, so it is unit tested. */
data class CareNumberScale(val min: Double, val max: Double, val step: Double, val decimals: Int = if (step % 1.0 == 0.0) 0 else 1) {
    init { require(step > 0 && max >= min) }
    /** How many rows the wheel has. */
    val count: Int get() = ((max - min) / step).roundToInt() + 1
    /** The value on row [index] (clamped to the scale), rounded so 0.5 steps never drift. */
    fun valueAt(index: Int): Double = round(min + index.coerceIn(0, count - 1) * step)
    /** The row nearest to [value]; a value outside the scale opens on the nearest end. */
    fun indexOf(value: Double): Int = ((value - min) / step).roundToInt().coerceIn(0, count - 1)
    /** [value] kept on the scale: clamped, then snapped to the nearest step. */
    fun snap(value: Double): Double = valueAt(indexOf(value))
    /**
     * What OK saves when a wheel opened on [initial] now rests on [index]: [initial] itself while the wheel still sits on its own row, so
     * a saved value that is off the scale or between steps (230 kg, 0.25 L/min) is never changed just by opening and pressing OK.
     */
    fun picked(initial: Double, index: Int): Double = if (index.coerceIn(0, count - 1) == indexOf(initial)) initial else valueAt(index)
    /** "62.5", "62" (a whole number shows no ".0"), always with Latin digits. */
    fun text(value: Double): String = round(value).let { if (decimals == 0 || it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.ROOT, "%.${decimals}f", it) }
    private fun round(v: Double): Double { val f = Math.pow(10.0, decimals.toDouble()); return Math.round(v * f) / f }
}

/** The wheels Sahara uses for numbers patients and families give. */
object CarePickerScales {
    /** Weight 1-300 kg in half kilos: the whole range CarePatientProfileRules.weight accepts, so a child or a heavy adult can be entered. */
    val WEIGHT_KG = CareNumberScale(1.0, 300.0, 0.5)
    /** Height 30-250 cm: the whole range CarePatientProfileRules.height accepts. */
    val HEIGHT_CM = CareNumberScale(30.0, 250.0, 1.0)
    /** Oxygen flow 0.5-15 L/min in half litres. */
    val OXYGEN_LPM = CareNumberScale(0.5, 15.0, 0.5)
    /** Where a weight wheel opens when nothing is saved yet. */
    const val WEIGHT_START = 60.0
    /** Where a height wheel opens when nothing is saved yet. */
    const val HEIGHT_START = 160.0
}

/** The Material date picker works in UTC midnight millis; these turn a calendar day to and from them without any time-zone shift. */
internal fun careUtcMillis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun careUtcDay(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** A clock time as the wheels hold it: hour and minute, no seconds. A saved 10:07 stays 10:07 (careMinuteRows gives 07 its own row). */
internal fun careWheelTime(time: LocalTime): LocalTime = LocalTime.of(time.hour, time.minute)

/**
 * The calendar as a dialog: tap the month and year at the top to jump to a year, then tap a day. Never typed: Picker mode with no
 * keyboard toggle. [initial] is preselected (null opens on [opensOn] or today with nothing picked); days after [latest] and before
 * [earliest] cannot be picked. [onPick] gets the chosen calendar day.
 */
@Composable fun CareDatePickerDialog(initial: LocalDate?, title: String, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit,
    latest: LocalDate? = null, earliest: LocalDate? = null, opensOn: LocalDate? = null) {
    val t = careText()
    val start = initial ?: opensOn ?: latest?.takeIf { it.isBefore(LocalDate.now()) } ?: LocalDate.now()
    val maxUtc = latest?.let(::careUtcMillis); val minUtc = earliest?.let(::careUtcMillis)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let(::careUtcMillis),
        initialDisplayedMonthMillis = careUtcMillis(start),
        // The Material state throws when the shown or picked day's year is outside yearRange, so an old saved day outside the limits
        // still opens (isSelectableYear keeps it from being picked again).
        yearRange = minOf(earliest?.year ?: 1900, start.year, initial?.year ?: start.year)..maxOf(latest?.year ?: (LocalDate.now().year + 30), start.year, initial?.year ?: start.year, earliest?.year ?: 1900),
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = (maxUtc == null || utcTimeMillis <= maxUtc) && (minUtc == null || utcTimeMillis >= minUtc)
            override fun isSelectableYear(year: Int) = (latest == null || year <= latest.year) && (earliest == null || year >= earliest.year)
        })
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(careUtcDay(it)) } },
            enabled = state.selectedDateMillis != null, modifier = Modifier.heightIn(min = 48.dp).testTag("care-date-ok")) { Text(t("OK", "ठीक है"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("care-date-cancel")) { Text(t("Cancel", "रद्द करें"), fontSize = 16.sp) } }) {
        DatePicker(state, title = { Text(title, Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) }, showModeToggle = false)
    }
}

/**
 * A time as a dialog with three scroll wheels (hour 1-12, minute in fives plus a saved off-five minute, AM/PM), the same wheels as [CareTimeButton]. No keyboard,
 * no clock face to aim at. [onPick] gets the chosen time.
 */
@Composable fun CareClockDialog(initial: LocalTime, title: String, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val start = careWheelTime(initial)
    val minutes = remember(start.minute) { careMinuteRows(start.minute) }
    var hour12 by remember { mutableIntStateOf(careHour12(start.hour)) }
    var minute by remember { mutableIntStateOf(start.minute) }
    var pm by remember { mutableStateOf(start.hour >= 12) }
    val amWord = remember(hindi) { careDateFormat("a", hindi).format(LocalTime.of(9, 0)) }
    val pmWord = remember(hindi) { careDateFormat("a", hindi).format(LocalTime.of(21, 0)) }
    val chosen = LocalTime.of(careHour24(hour12, pm), minute)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Paper, modifier = Modifier.testTag("care-clock-dialog")) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.fillMaxWidth())
                Text(careDateFormat("h:mm a", hindi).format(chosen), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("care-clock-shown"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CareValueWheel(12, hour12 - 1, { String.format(Locale.ROOT, "%d", it + 1) }, { hour12 = it + 1 }, t("Hour", "घंटा"), "care-clock-hour", Modifier.weight(1f))
                    Text(":", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    CareValueWheel(minutes.size, minutes.indexOf(start.minute), { String.format(Locale.ROOT, "%02d", minutes[it]) }, { minute = minutes[it] }, t("Minute", "मिनट"), "care-clock-minute", Modifier.weight(1f))
                    CareValueWheel(2, if (pm) 1 else 0, { if (it == 0) amWord else pmWord }, { pm = it == 1 }, t("AM or PM", "AM या PM"), "care-clock-ampm", Modifier.weight(1f))
                }
                CarePickerActions(onDismiss, { onPick(chosen) }, "care-clock")
            }
        }
    }
}

/**
 * A moment in time: the calendar first, then the clock wheels. [onPick] gets the chosen day at the chosen time; Cancel at either step
 * leaves everything as it was.
 */
@Composable fun CareDateTimeDialog(initial: LocalDateTime, title: String, onPick: (LocalDateTime) -> Unit, onDismiss: () -> Unit, latest: LocalDate? = null) {
    val t = careText()
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val picked = day
    if (picked == null) CareDatePickerDialog(initial.toLocalDate(), title, { day = it }, onDismiss, latest = latest)
    else CareClockDialog(initial.toLocalTime(), title + " · " + t("time", "समय"), { onPick(picked.atTime(it)) }, onDismiss)
}

/**
 * A number given by tapping (weight, height, oxygen): a large button showing "62.5 kg", or [label] when nothing is set, which opens one
 * scroll wheel of [scale] with OK / Cancel. A wheel opens on [value], or [start] when nothing is saved. [onChange] gets a value on the
 * scale, or null when [allowClear] lets it be removed. Tags: [tag] on the button, "$tag-wheel" on the wheel, "$tag-ok" on OK.
 */
@Composable fun CareNumberWheelButton(value: Double?, onChange: (Double?) -> Unit, label: String, unit: String, scale: CareNumberScale, start: Double,
    tag: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, allowClear: Boolean = false) {
    val t = careText()
    var picking by remember { mutableStateOf(false) }
    if (picking) CareNumberWheelDialog(value ?: start, label, unit, scale, tag, onPick = { picking = false; onChange(it) }, onDismiss = { picking = false })
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { picking = true }, enabled = enabled, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper, contentColor = DoctorCardInk),
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag(tag)) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, color = DoctorCardSub)
                Text(value?.let { scale.text(it) + " " + unit } ?: t("Choose", "चुनें"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            }
        }
        if (allowClear && value != null) IconButton(onClick = { onChange(null) }, enabled = enabled, modifier = Modifier.size(56.dp).testTag("$tag-clear")) { Icon(Icons.Default.Close, t("Remove", "हटाएँ")) }
    }
}

/** One scroll wheel of [scale] in a calm dialog; the big number on top is what OK saves. */
@Composable fun CareNumberWheelDialog(initial: Double, title: String, unit: String, scale: CareNumberScale, tag: String, onPick: (Double) -> Unit, onDismiss: () -> Unit) {
    var index by remember { mutableIntStateOf(scale.indexOf(initial)) }
    val value = scale.picked(initial, index)
    val shown = scale.text(value) + " " + unit
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Paper, modifier = Modifier.testTag("$tag-dialog")) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.fillMaxWidth())
                Text(shown, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("$tag-shown"))
                CareValueWheel(scale.count, index, { scale.text(scale.valueAt(it)) }, { index = it }, title, "$tag-wheel", Modifier.fillMaxWidth(.6f))
                CarePickerActions(onDismiss, { onPick(value) }, tag)
            }
        }
    }
}

@Composable private fun CarePickerActions(onDismiss: () -> Unit, onOk: () -> Unit, tag: String) {
    val t = careText()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp).testTag("$tag-cancel")) { Text(t("Cancel", "रद्द करें"), fontSize = 16.sp, color = RustText) }
        Button(onClick = onOk, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent),
            modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 96.dp).testTag("$tag-ok")) { Text(t("OK", "ठीक है"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

private val careValueWheelRow = 56.dp

/**
 * One scroll wheel: [count] rows, three in view, the middle one highlighted and chosen. It snaps after a fling, ticks softly on each new
 * row, and a tapped row glides to the middle. [onSelected] gets the row index. TalkBack reads "[description]: [label]".
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun CareValueWheel(count: Int, initial: Int, label: (Int) -> String, onSelected: (Int) -> Unit, description: String, tag: String, modifier: Modifier = Modifier) {
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
    Box(modifier.height(careValueWheelRow * 3).semantics { contentDescription = description + ": " + label(centred) }.testTag(tag), contentAlignment = Alignment.Center) {
        Surface(color = DoctorStatusGoodBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(careValueWheelRow)) {}
        LazyColumn(state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = careValueWheelRow), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            items(count, contentType = { "care-value-wheel-row" }) { index ->
                val on = index == centred
                val ink by animateColorAsState(if (on) DoctorCardInk else DoctorCardSub, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "care-value-wheel-ink")
                Box(Modifier.fillMaxWidth().height(careValueWheelRow).clickable {
                    scope.launch { if (SaharaMotion.enabled) state.animateScrollToItem(index) else state.scrollToItem(index) }
                }.testTag("$tag-$index").graphicsLayer {
                    // Rows shrink and fade with their distance from the middle; read only in the draw layer, so a fling never recomposes them.
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
