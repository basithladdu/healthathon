package `in`.wedevit.care

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class DoctorContributionDay(val date: LocalDate, val report: CareItem?, val future: Boolean)
internal data class DoctorContributionDays(val start: LocalDate, val today: LocalDate, val actualCount: Int, val days: List<DoctorContributionDay>,val weekCount:Int)

/** Input is one already-authorized person's records; never merge multiple patients. */
internal fun doctorContributionDays(checks: List<CareItem>, zone: ZoneId, now: Long): DoctorContributionDays {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val start = today.withDayOfMonth(1)
    val valid = if (checks.map { it.personId }.distinct().size > 1) emptyList() else checks.filter {
        it.kind == "checkin" && it.created in 1..now && !CareEntryCodec.isStructured(it.detail) &&
            Instant.ofEpochMilli(it.created).atZone(zone).toLocalDate().let {date->date.year==today.year&&date.month==today.month}
    }
    val latest = valid.sortedWith(compareByDescending<CareItem> { it.created }.thenBy { it.id })
        .groupBy { Instant.ofEpochMilli(it.created).atZone(zone).toLocalDate() }.mapValues { it.value.first() }
    return DoctorContributionDays(start, today, valid.size, (0L until today.lengthOfMonth().toLong()).map {
        val date = start.plusDays(it)
        DoctorContributionDay(date, latest[date], date > today)
    },(start.dayOfWeek.value-1+today.lengthOfMonth()+6)/7)
}

internal fun doctorContributionColor(title: String): Color? = when (doctorMood(title)) {
    DoctorReportedMood.HARD -> Color(0xFFE88982)
    DoctorReportedMood.OKAY -> Color(0xFFF2B273)
    DoctorReportedMood.GOOD -> Color(0xFF8FC4A2)
    null -> null
}

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun DoctorContributionChart(checks: List<CareItem>, zone: ZoneId, now: Long, onSelect: (CareItem) -> Unit = {}) {
    val calendar = remember(checks, zone, now) { doctorContributionDays(checks, zone, now) }
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val monthLabel=calendar.start.format(monthFormat)
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val rowHeight = (26 * scale).dp
    val labelWidth = (40 * scale).dp
    Surface(color = Peach.copy(alpha = .4f), shape = RoundedCornerShape(22.dp)) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(monthLabel, fontFamily=SaharaTitleFont,fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(labelWidth)) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                    Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) { Text(it, fontSize = 11.sp, lineHeight = 15.sp) }
                }
            }
            Canvas(Modifier.weight(1f).height(rowHeight * 7).testTag("doctor-condition-chart").semantics {
                contentDescription = "$monthLabel calendar, Monday to Sunday. ${calendar.actualCount} actual reports this month; latest report per day is shown. Missing days and unrecognized reports are neutral; future days are blank. Dates and full text are listed below. Not a clinical score."
            }) {
                val column = size.width / calendar.weekCount
                val row = size.height / 7f
                val side = minOf(column - 6.dp.toPx(), row - 6.dp.toPx()).coerceAtLeast(1f)
                calendar.days.forEach { day ->
                    if (!day.future) {
                        val index = calendar.start.dayOfWeek.value-1+java.time.temporal.ChronoUnit.DAYS.between(calendar.start, day.date).toInt()
                        val at = Offset(index / 7 * column + (column - side) / 2, index % 7 * row + (row - side) / 2)
                        val color = day.report?.let { doctorContributionColor(it.title) }
                        drawRoundRect(color ?: Color(0xFFE9E4DE), at, Size(side, side), CornerRadius(4.dp.toPx()))
                        if (day.report != null && color == null) drawRoundRect(Color(0xFF8D8178), at, Size(side, side), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
                    }
                }
            }
        }
    }
    }
}
