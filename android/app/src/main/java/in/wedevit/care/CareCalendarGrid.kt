package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import kotlinx.coroutines.flow.filterNotNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** One place for the calendar's category colours so grid dots, legend and list bars always agree. */
object CalendarKinds {
    val order = listOf("Chemotherapy", "Radiotherapy", "Visit", "Test", "Follow-up", "Medicine", "Procedure", "Other")
    fun key(item: CareItem): String = calendarCategory(item).let { if (it in order) it else "Other" }
    /** The same section colours as the rest of the app: chemo rose, radiotherapy plum, visits sky, tests teal. */
    fun color(key: String): Color = when (key) {
        "Chemotherapy" -> SaharaTone.Rose.solid
        "Radiotherapy" -> SaharaTone.Plum.solid
        "Visit" -> SaharaTone.Indigo.solid
        "Test" -> SaharaTone.Teal.solid
        "Follow-up" -> SaharaTone.Marigold.solid
        "Medicine" -> SaharaTone.Leaf.solid
        "Procedure" -> SaharaTone.Rust.solid
        else -> SaharaTone.Neutral.solid
    }
    @Composable fun label(key: String): String {
        val t = careText(); val hindi = careHindi()
        return when {
            key == "Other" -> t("Other", "अन्य")
            key in TreatmentDayRules.categories -> TreatmentDayRules.label(key)
            else -> careCategoryLabel(key, hindi)
        }
    }
}

private fun weekStart(day: LocalDate): LocalDate = day.with(DayOfWeek.MONDAY)

/** How far either side of the opening day the swipeable month and week rows reach; moving past the edge extends them. */
private const val PAGER_YEARS = 10L

/**
 * Swipeable months (kizitonwose Calendar, MIT): a 6 x 7 grid per month, Monday first. The pager follows [day]
 * (arrows, date picker, "Back to today"); a finger swipe to another month moves [day] to the same date there.
 */
@Composable internal fun SaharaMonthPager(day: LocalDate, onDay: (LocalDate) -> Unit, modifier: Modifier = Modifier, dayContent: @Composable BoxScope.(date: LocalDate, inMonth: Boolean) -> Unit) {
    val anchor = remember { YearMonth.from(day) }
    val state = rememberCalendarState(startMonth = anchor.minusYears(PAGER_YEARS), endMonth = anchor.plusYears(PAGER_YEARS), firstVisibleMonth = anchor,
        firstDayOfWeek = DayOfWeek.MONDAY, outDateStyle = OutDateStyle.EndOfGrid)
    val month = YearMonth.from(day)
    val latestDay by rememberUpdatedState(day); val latestOnDay by rememberUpdatedState(onDay)
    LaunchedEffect(month) {
        if (month < state.startMonth) state.startMonth = month.minusYears(1)
        if (month > state.endMonth) state.endMonth = month.plusYears(1)
        if (state.firstVisibleMonth.yearMonth != month) { if (SaharaMotion.enabled) state.animateScrollToMonth(month) else state.scrollToMonth(month) }
    }
    // Only a finger swipe changes the day; the pager's own scrolls (above) just follow it.
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(state) { state.interactionSource.interactions.collect { if (it is DragInteraction.Start) dragged = true } }
    LaunchedEffect(state) {
        snapshotFlow { if (state.isScrollInProgress) null else state.firstVisibleMonth.yearMonth }.filterNotNull().collect { shown ->
            if (!dragged) return@collect
            dragged = false
            val current = latestDay
            if (shown != YearMonth.from(current)) latestOnDay(shown.atDay(minOf(current.dayOfMonth, shown.lengthOfMonth())))
        }
    }
    HorizontalCalendar(modifier, state = state, dayContent = { d -> dayContent(d.date, d.position == DayPosition.MonthDate) })
}

/** Swipeable weeks (kizitonwose Calendar, MIT), Monday first. Follows [day]; a finger swipe moves [day] by whole weeks. */
@Composable internal fun SaharaWeekPager(day: LocalDate, onDay: (LocalDate) -> Unit, modifier: Modifier = Modifier, dayContent: @Composable BoxScope.(date: LocalDate) -> Unit) {
    val anchor = remember { weekStart(day) }
    val state = rememberWeekCalendarState(startDate = anchor.minusYears(PAGER_YEARS), endDate = anchor.plusYears(PAGER_YEARS), firstVisibleWeekDate = anchor, firstDayOfWeek = DayOfWeek.MONDAY)
    val start = weekStart(day)
    val latestDay by rememberUpdatedState(day); val latestOnDay by rememberUpdatedState(onDay)
    LaunchedEffect(start) {
        if (start < state.startDate) state.startDate = start.minusYears(1)
        if (start.plusDays(6) > state.endDate) state.endDate = start.plusYears(1)
        if (state.firstVisibleWeek.days.first().date != start) { if (SaharaMotion.enabled) state.animateScrollToWeek(start) else state.scrollToWeek(start) }
    }
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(state) { state.interactionSource.interactions.collect { if (it is DragInteraction.Start) dragged = true } }
    LaunchedEffect(state) {
        snapshotFlow { if (state.isScrollInProgress) null else state.firstVisibleWeek.days.first().date }.filterNotNull().collect { shown ->
            if (!dragged) return@collect
            dragged = false
            val current = latestDay
            val weeks = ChronoUnit.WEEKS.between(weekStart(current), shown)
            if (weeks != 0L) latestOnDay(current.plusWeeks(weeks))
        }
    }
    WeekCalendar(modifier, state = state, dayContent = { d -> dayContent(d.date) })
}

/** Week/Month switch, navigation, day grid and colour legend. Selecting a day reports it through onDay; a swipe moves a month (or week). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun CareCalendarGrid(entries: List<CareItem>, day: LocalDate, week: Boolean, onWeek: (Boolean) -> Unit, onDay: (LocalDate) -> Unit) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    val first = weekStart(day)
    // One surface for the whole grid: switch, month name, weekdays, days and legend.
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp).testTag("calendar-grid"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !week, onClick = { onWeek(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("calendar-month-toggle")) { Text(t("Month", "महीना")) }
            SegmentedButton(selected = week, onClick = { onWeek(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("calendar-week-toggle")) { Text(t("Week", "सप्ताह")) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDay(if (week) day.minusWeeks(1) else day.minusMonths(1)) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ChevronLeft, if (week) t("Previous week", "पिछला सप्ताह") else t("Previous month", "पिछला महीना"), tint = Rust) }
            val title = if (week) "${first.format(careDateFormat("d MMM", hindi))} – ${first.plusDays(6).format(careDateFormat("d MMM yyyy", hindi))}" else day.format(careDateFormat("MMMM yyyy", hindi))
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1)
            IconButton(onClick = { onDay(if (week) day.plusWeeks(1) else day.plusMonths(1)) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ChevronRight, if (week) t("Next week", "अगला सप्ताह") else t("Next month", "अगला महीना"), tint = Rust) }
        }
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i -> Text(first.plusDays(i.toLong()).format(careDateFormat("EEE", hindi)), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RustText, maxLines = 1) }
        }
        val cell: @Composable (LocalDate, Boolean) -> Unit = { d, inMonth ->
            val dayItems = remember(entries, d) { calendarItems(entries, d) }
            val kinds = remember(dayItems) { CalendarKinds.order.filter { k -> dayItems.any { CalendarKinds.key(it) == k } } }
            val n = dayItems.size
            val selected = d == day; val isToday = d == today
            val desc = d.format(careDateFormat("EEE d MMM", hindi)) + if (n > 0) ", " + (if (n == 1) t("1 entry", "1 प्रविष्टि") else t("$n entries", "$n प्रविष्टियाँ")) else ""
            val fill by androidx.compose.animation.animateColorAsState(if (selected) Rust else Color.Transparent, SaharaMotion.tween(SaharaMotion.SELECT_MS))
            Box(Modifier.fillMaxWidth().padding(1.dp).heightIn(min = 48.dp).background(fill, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onDay(d) }.semantics { contentDescription = desc }.testTag(if (inMonth) "calendar-day-$d" else "calendar-outday-$d")) {
                Surface(Modifier.matchParentSize(), color = Color.Transparent, shape = RoundedCornerShape(12.dp), border = if (isToday) BorderStroke(2.dp, Rust) else null) {}
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfMonth.toString(), fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.White else if (inMonth) DoctorCardInk else DoctorCardSub, fontSize = 15.sp)
                    Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        kinds.take(3).forEach { k -> Box(Modifier.size(6.dp).background(if (selected) Color.White else CalendarKinds.color(k), CircleShape)) }
                        if (kinds.size > 3) Text("+${kinds.size - 3}", fontSize = 10.sp, color = if (selected) Color.White else DoctorCardSub)
                    }
                }
            }
        }
        if (week) SaharaWeekPager(day, onDay, Modifier.fillMaxWidth()) { d -> cell(d, true) }
        else SaharaMonthPager(day, onDay, Modifier.fillMaxWidth()) { d, inMonth -> cell(d, inMonth) }
        // Fixed legend
        HorizontalDivider(color = DoctorHairline)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().testTag("calendar-legend"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CalendarKinds.order.forEach { k -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(CalendarKinds.color(k), CircleShape)); Spacer(Modifier.width(6.dp)); Text(CalendarKinds.label(k), fontSize = 14.sp, color = DoctorCardSub) } }
        }
    }
    }
}
