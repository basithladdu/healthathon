@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** One day of check-ins: the day's mood (if any), its symptom notes, its distress answer and every entry of that day, newest first. */
internal data class CheckInDay(val day: LocalDate, val mood: String?, val symptomNotes: Int, val distressScore: Int?, val distressAnswered: Boolean, val items: List<CareItem>)

/** The Check-ins page as one compact row per day (Dr Sujay, 22 Nov review: the page was too long). Pure so it can be unit tested. */
internal object CheckInDays {
    /** The short list: this many days back, today included (clinical team, 24 Nov 2026). Older days are reached on the month calendar. */
    const val RECENT = 7

    /** The day an entry belongs to: a symptom note's observed date, otherwise the day it was saved. */
    fun dayOf(item: CareItem, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        SymptomNoteCodec.decode(item.detail)?.observedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Instant.ofEpochMilli(item.created).atZone(zone).toLocalDate()

    fun group(items: List<CareItem>, zone: ZoneId = ZoneId.systemDefault()): List<CheckInDay> =
        items.groupBy { dayOf(it, zone) }.toSortedMap(compareByDescending { it }).map { (day, rows) ->
            val newest = rows.sortedByDescending { it.created }
            val distress = newest.firstNotNullOfOrNull { if (DistressCodec.isStructured(it.detail)) DistressCodec.decode(it.detail) else null }
            CheckInDay(day,
                mood = newest.firstOrNull { !SymptomNoteCodec.isStructured(it.detail) && !DistressCodec.isStructured(it.detail) && it.title in CareDailyFlow.moods }?.title,
                symptomNotes = newest.count { SymptomNoteCodec.isStructured(it.detail) },
                distressScore = distress?.let { if (it.distress) it.score ?: 0 else 0 }, distressAnswered = distress != null, items = newest)
        }

    /** The days of the last [RECENT] days (today and the six before it) that have anything noted, newest first. */
    fun lastWeek(days: List<CheckInDay>, today: LocalDate): List<CheckInDay> =
        days.filter { !it.day.isAfter(today) && !it.day.isBefore(today.minusDays(RECENT - 1L)) }

    /** A month as calendar rows, Monday first; null fills the days before the 1st and after the last day. */
    fun monthGrid(month: YearMonth): List<List<LocalDate?>> {
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        return cells.chunked(7).map { row -> row + List(7 - row.size) { null } }
    }

    /** What a day's dot means: its mood when one was saved, otherwise "notes only" (a symptom or distress note). */
    fun dotKind(day: CheckInDay): String = day.mood ?: NOTES_ONLY
    const val NOTES_ONLY = "notes"
}

/** One dot colour per kind of day, so the dots, the legend and the list agree. */
private fun checkInTone(kind: String): SaharaTone = when (kind) {
    CheckInMood.GOOD -> SaharaTone.Leaf
    CheckInMood.OKAY -> SaharaTone.Marigold
    CheckInMood.HARD -> SaharaTone.Rose
    else -> SaharaTone.Indigo
}
private fun checkInDotColor(kind: String): Color = checkInTone(kind).solid

/**
 * One entry of a day as a single line (24 Nov: "we don't need big cards"): time, what it was, its number as a small chip. Tapping
 * the line opens the usual full card under it (edit, remove, graph); tapping again closes it.
 */
@Composable private fun CheckInEntryRow(item: CareItem, vm: CareModel) {
    val t = careText(); val hindi = careHindi()
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    val note = remember(item.detail) { if (SymptomNoteCodec.isStructured(item.detail)) SymptomNoteCodec.decode(item.detail) else null }
    val distress = remember(item.detail) { if (DistressCodec.isStructured(item.detail)) DistressCodec.decode(item.detail) else null }
    val label = when {
        note != null -> SymptomDiaryRules.symptomLabel(note.symptom, hindi)
        distress != null -> t("Distress thermometer", "परेशानी थर्मामीटर")
        item.kind == "checkin" -> checkInMoodLabel(item.title, hindi)
        else -> item.title
    }
    val score = when { note != null -> SymptomSort.headline(item); distress != null -> if (distress.distress) distress.score ?: 0 else 0; else -> null }
    val dot = when { note != null -> symptomColor(note.symptom); distress != null -> ChartBands.color(score ?: 0); else -> checkInDotColor(item.title) }
    val time = remember(item.created, note?.observedTime, hindi) {
        val clock = careDateFormat("h:mm a", hindi)
        note?.observedTime?.let { runCatching { LocalTime.parse(it).format(clock) }.getOrNull() }
            ?: Instant.ofEpochMilli(item.created).atZone(ZoneId.systemDefault()).format(clock)
    }
    val tick = rememberSaharaTick()
    Column(Modifier.fillMaxWidth().testTag("checkin-entry-${item.id}")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { tick(); open = !open }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(time, fontSize = 13.sp, color = DoctorCardSub, maxLines = 1, softWrap = false)
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            score?.let { s -> Text("$s/10", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.testTag("checkin-entry-score-${item.id}")) }
            SaharaChevron(open, if (open) t("Hide details", "जानकारी छिपाएँ") else t("Show details", "जानकारी देखें"), Modifier.size(20.dp))
        }
        AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Box(Modifier.padding(top = 4.dp, bottom = 6.dp)) { CareRow(item, vm) } }
    }
}

/** "A good day · 2 symptom notes · Distress 3/10", or the number of entries when there is nothing else to say. */
@Composable private fun checkInDayLine(d: CheckInDay): String {
    val t = careText(); val hindi = careHindi()
    val parts = listOfNotNull(
        d.mood?.let { checkInMoodLabel(it, hindi) },
        d.symptomNotes.takeIf { it > 0 }?.let { n -> if (n == 1) t("1 symptom note", "1 लक्षण नोट") else t("$n symptom notes", "$n लक्षण नोट") },
        d.distressScore?.takeIf { d.distressAnswered }?.let { s -> t("Distress $s/10", "परेशानी $s/10") })
    return parts.ifEmpty { listOf(if (d.items.size == 1) t("1 entry", "1 नोट") else t("${d.items.size} entries", "${d.items.size} नोट")) }.joinToString(" · ")
}

@Composable private fun checkInDayLabel(day: LocalDate): String {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    return when (day) { today -> t("Today", "आज"); today.minusDays(1) -> t("Yesterday", "कल"); else -> day.format(careDateFormat("EEE d MMM", hindi)) }
}

/**
 * The Check-ins page (clinical team, 24 Nov 2026): the distress graph one tap away, the last 7 days as a short list (a row opens to
 * show that day's entries), then a month calendar with one coloured dot per day; tapping a day shows that day under the calendar.
 */
@Composable internal fun CheckInHistoryModule(checkins: List<CareItem>, personId: String, vm: CareModel) {
    val t = careText(); val hindi = careHindi()
    val days = remember(checkins) { CheckInDays.group(checkins) }
    var showDistress by rememberSaveable(personId) { mutableStateOf(false) }
    var openDay by rememberSaveable(personId) { mutableStateOf("") }
    var monthText by rememberSaveable(personId) { mutableStateOf(YearMonth.now().toString()) }
    var picked by rememberSaveable(personId) { mutableStateOf("") }
    val hasDistress = remember(checkins, personId) { DistressRules.history(checkins, personId).isNotEmpty() }
    val tick = rememberSaharaTick()
    if (hasDistress) {
        OutlinedButton(onClick = { tick(); showDistress = !showDistress }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("checkin-distress-toggle")) {
            Text(if (showDistress) t("Hide distress graph", "परेशानी का ग्राफ़ छिपाएँ") else t("See distress graph", "परेशानी का ग्राफ़ देखें"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            SaharaChevron(showDistress, null)
        }
        AnimatedVisibility(showDistress, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { DistressHistoryCard(checkins, personId) }
    }
    if (days.isEmpty()) { SaharaCalmEmpty(t("No check-ins yet.", "अभी कोई हाल नहीं लिखा।")); return }
    val today = LocalDate.now()
    val week = remember(days, today) { CheckInDays.lastWeek(days, today) }
    Text(t("Last 7 days", "पिछले 7 दिन"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.a11yHeading().testTag("checkin-week-title"))
    if (week.isEmpty()) Text(t("No check-ins in the last 7 days.", "पिछले 7 दिनों में कोई हाल नहीं लिखा।"), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("checkin-week-empty"))
    else Column(Modifier.fillMaxWidth().testTag("checkin-days")) {
        Column {
            week.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider()
                val dayKey = d.day.toString(); val open = openDay == dayKey
                // One line per day: the day, then small chips (24 Nov: "reduce the cards, we don't need big cards").
                Row(Modifier.fillMaxWidth().saharaStagger(i).heightIn(min = 52.dp).clickable(role = Role.Button) { tick(); openDay = if (open) "" else dayKey }.padding(horizontal = 12.dp, vertical = 6.dp).testTag("checkin-day-$dayKey"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(checkInDotColor(CheckInDays.dotKind(d)), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    // The day, and one plain line under it (owner, 25 Nov: no pills).
                    Column(Modifier.weight(1f)) {
                        Text(checkInDayLabel(d.day), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(checkInDayLine(d), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    SaharaChevron(open, if (open) t("Hide details", "जानकारी छिपाएँ") else t("Show details", "जानकारी देखें"))
                }
                AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                    Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp)) { d.items.forEach { key(it.id) { CheckInEntryRow(it, vm) } } }
                }
            }
        }
    }
    val month = remember(monthText) { runCatching { YearMonth.parse(monthText) }.getOrElse { YearMonth.now() } }
    val byDay = remember(days) { days.associateBy { it.day } }
    val pickedDay = remember(picked) { runCatching { LocalDate.parse(picked) }.getOrNull() }
    CheckInMonthCalendar(month, byDay, picked = pickedDay, today = today,
        onMonth = { tick(); monthText = it.toString(); picked = "" }, onPick = { d -> tick(); picked = if (picked == d.toString()) "" else d.toString() })
    // The tapped day, under the calendar: its line, then its entries with their usual actions. It opens and closes in place
    // (the last day stays drawn while it closes), and switching to another day crossfades instead of jumping.
    var lastDay by remember { mutableStateOf(pickedDay) }
    LaunchedEffect(pickedDay) { if (pickedDay != null) lastDay = pickedDay }
    AnimatedVisibility(pickedDay != null, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
      SaharaStateSwitch(pickedDay ?: lastDay, label = "checkin-picked-day") { shownDay -> shownDay?.let { day ->
        val d = byDay[day]
        Column(Modifier.fillMaxWidth().testTag("checkin-picked-day")) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(checkInDayLabel(day), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.a11yHeading())
                if (d == null) Text(t("Nothing noted this day.", "इस दिन कुछ नहीं लिखा गया।"), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text(checkInDayLine(d), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column { d.items.forEach { key(it.id) { CheckInEntryRow(it, vm) } } }
                }
            }
        }
      } }
    }
}

/**
 * A plain month (Monday first) with one coloured dot under each day that has a check-in: the mood's colour, or indigo for notes only.
 * The arrows change month; a future month cannot be opened. Tapping a day reports it. Test tags: "checkin-cal-<date>".
 */
@Composable private fun CheckInMonthCalendar(month: YearMonth, byDay: Map<LocalDate, CheckInDay>, picked: LocalDate?, today: LocalDate, onMonth: (YearMonth) -> Unit, onPick: (LocalDate) -> Unit) {
    val t = careText(); val hindi = careHindi()
    val grid = remember(month) { CheckInDays.monthGrid(month) }
    val firstMonday = month.atDay(1).with(DayOfWeek.MONDAY)
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("checkin-calendar")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.size(48.dp).testTag("checkin-cal-previous")) { Icon(Icons.Default.ChevronLeft, t("Previous month", "पिछला महीना"), tint = Rust) }
                SaharaStateSwitch(month, Modifier.weight(1f), label = "checkin-cal-month") { m ->
                    Text(m.atDay(1).format(careDateFormat("MMMM yyyy", hindi)), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1)
                }
                IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.from(today), modifier = Modifier.size(48.dp).testTag("checkin-cal-next")) { Icon(Icons.Default.ChevronRight, t("Next month", "अगला महीना"), tint = if (month < YearMonth.from(today)) Rust else DoctorHairline) }
            }
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i -> Text(firstMonday.plusDays(i.toLong()).format(careDateFormat("EEE", hindi)), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RustText, maxLines = 1) }
            }
            grid.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { d ->
                        if (d == null) Spacer(Modifier.weight(1f).heightIn(min = 48.dp))
                        else {
                            val entry = byDay[d]; val selected = d == picked; val isToday = d == today; val future = d.isAfter(today)
                            val desc = d.format(careDateFormat("EEE d MMM", hindi)) + (entry?.let { ", " + checkInDayLine(it) } ?: "")
                            // The picked day's fill fades in and the old one fades out, like the system date picker.
                            val cellFill by androidx.compose.animation.animateColorAsState(if (selected) Rust else Rust.copy(alpha = 0f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "checkin-cal-fill")
                            Box(Modifier.weight(1f).padding(1.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(cellFill)
                                .border(if (isToday && !selected) 2.dp else 0.dp, if (isToday && !selected) Rust else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable(enabled = !future, role = Role.Button) { onPick(d) }.semantics { contentDescription = desc }.testTag("checkin-cal-$d"), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(d.dayOfMonth.toString(), fontSize = 15.sp, fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when { selected -> Color.White; future -> DoctorCardSub; else -> DoctorCardInk })
                                    Box(Modifier.padding(top = 2.dp).size(7.dp).background(when { entry == null -> Color.Transparent; selected -> Color.White; else -> checkInDotColor(CheckInDays.dotKind(entry)) }, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = DoctorHairline)
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("checkin-cal-legend"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (CareDailyFlow.moods.reversed().map { it to checkInMoodLabel(it, hindi) } + (CheckInDays.NOTES_ONLY to t("Notes only", "सिर्फ़ नोट"))).forEach { (kind, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(checkInDotColor(kind), CircleShape)); Spacer(Modifier.width(6.dp)); Text(label, fontSize = 14.sp, color = DoctorCardSub) }
                }
            }
        }
    }
}
