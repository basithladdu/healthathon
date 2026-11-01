package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.time.DayOfWeek
import java.time.LocalDate

/** One place for the calendar's category colours so grid dots, legend and list bars always agree. */
object CalendarKinds {
    val order = listOf("Chemotherapy", "Radiotherapy", "Visit", "Test", "Follow-up", "Medicine", "Procedure", "Other")
    fun key(item: CareItem): String = calendarCategory(item).let { if (it in order) it else "Other" }
    fun color(key: String): Color = when (key) {
        "Chemotherapy" -> Color(0xFFB9472C)
        "Radiotherapy" -> Color(0xFF7B5EA7)
        "Visit" -> Color(0xFF3F7CAC)
        "Test" -> Color(0xFF2F8F83)
        "Follow-up" -> Color(0xFFB98300)
        "Medicine" -> Color(0xFF5E8C3A)
        "Procedure" -> Color(0xFFC2647A)
        else -> Color(0xFF7A7A7A)
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

/** Week/Month switch, navigation, day grid and colour legend. Selecting a day reports it through onDay. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable fun CareCalendarGrid(entries: List<CareItem>, day: LocalDate, week: Boolean, onWeek: (Boolean) -> Unit, onDay: (LocalDate) -> Unit) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    val first = if (week) weekStart(day) else weekStart(day.withDayOfMonth(1))
    val rows = if (week) 1 else 6
    val kindsByDay = remember(entries, first, rows) {
        (0 until rows * 7).associate { i -> val d = first.plusDays(i.toLong()); d to CalendarKinds.order.filter { k -> calendarItems(entries, d).any { CalendarKinds.key(it) == k } } }
    }
    val countByDay = remember(entries, first, rows) { (0 until rows * 7).associate { i -> val d = first.plusDays(i.toLong()); d to calendarItems(entries, d).size } }
    Column(Modifier.fillMaxWidth().testTag("calendar-grid"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!week, { onWeek(false) }, label = { Text(t("Month", "महीना")) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("calendar-month-toggle"))
            FilterChip(week, { onWeek(true) }, label = { Text(t("Week", "सप्ताह")) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("calendar-week-toggle"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDay(if (week) day.minusWeeks(1) else day.minusMonths(1)) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ChevronLeft, if (week) t("Previous week", "पिछला सप्ताह") else t("Previous month", "पिछला महीना")) }
            val title = if (week) "${first.format(careDateFormat("d MMM", hindi))} – ${first.plusDays(6).format(careDateFormat("d MMM yyyy", hindi))}" else day.format(careDateFormat("MMMM yyyy", hindi))
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Normal)
            IconButton(onClick = { onDay(if (week) day.plusWeeks(1) else day.plusMonths(1)) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ChevronRight, if (week) t("Next week", "अगला सप्ताह") else t("Next month", "अगला महीना")) }
        }
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i -> Text(first.plusDays(i.toLong()).format(careDateFormat("EEE", hindi)), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = RustText) }
        }
        (0 until rows).forEach { r ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { c ->
                    val d = first.plusDays((r * 7 + c).toLong()); val kinds = kindsByDay[d].orEmpty(); val n = countByDay[d] ?: 0
                    val inMonth = week || d.month == day.month; val selected = d == day; val isToday = d == today
                    val desc = d.format(careDateFormat("EEE d MMM", hindi)) + if (n > 0) ", " + (if (n == 1) t("1 entry", "1 प्रविष्टि") else t("$n entries", "$n प्रविष्टियाँ")) else ""
                    Box(Modifier.weight(1f).padding(1.dp).heightIn(min = 48.dp).background(if (selected) Rust else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onDay(d) }.semantics { contentDescription = desc }.testTag("calendar-day-$d")) {
                        Surface(Modifier.matchParentSize(), color = Color.Transparent, shape = RoundedCornerShape(12.dp), border = if (isToday) BorderStroke(2.dp, Rust) else null) {}
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.dayOfMonth.toString(), fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.White else if (inMonth) DoctorCardInk else DoctorCardSub, fontSize = 15.sp)
                            Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                kinds.take(3).forEach { k -> Box(Modifier.size(6.dp).background(if (selected) Color.White else CalendarKinds.color(k), CircleShape)) }
                                if (kinds.size > 3) Text("+${kinds.size - 3}", fontSize = 9.sp, color = if (selected) Color.White else DoctorCardSub)
                            }
                        }
                    }
                }
            }
        }
        // Fixed legend
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().testTag("calendar-legend"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CalendarKinds.order.forEach { k -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(CalendarKinds.color(k), CircleShape)); Spacer(Modifier.width(5.dp)); Text(CalendarKinds.label(k), style = MaterialTheme.typography.labelSmall) } }
        }
    }
}
