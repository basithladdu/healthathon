@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** The same three colour bands on every chart: green 0–3, amber 4–6, red 7–10. */
object ChartBands {
    val Mild = Color(0xFF2E7D4F)
    val Moderate = Color(0xFFD08A1A)
    val Severe = Color(0xFFC0392B)
    fun color(value: Int): Color = when { value <= 3 -> Mild; value <= 6 -> Moderate; else -> Severe }
    fun word(value: Int, hindi: Boolean): String = when {
        value <= 3 -> careTr(hindi, "Mild", "हल्का")
        value <= 6 -> careTr(hindi, "Moderate", "मध्यम")
        else -> careTr(hindi, "Severe", "तेज़")
    }
}

/** One day on a chart: the highest number noted that day, or null when nothing was noted. */
data class DayValue(val date: LocalDate, val value: Int?)

/** Days to draw. [lastWeek] is true for the 7 calendar days ending today; otherwise the latest days that have a note. */
data class TrendWindow(val days: List<DayValue>, val lastWeek: Boolean)

object TrendChartRules {
    fun window(points: List<DatedRating>, today: LocalDate = LocalDate.now()): TrendWindow {
        val byDay = points.mapNotNull { p -> runCatching { LocalDate.parse(p.date) }.getOrNull()?.let { it to p.value } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.maxOrNull() ?: 0 }
        if (byDay.isEmpty()) return TrendWindow(emptyList(), true)
        val start = today.minusDays(6)
        if (byDay.keys.any { !it.isBefore(start) && !it.isAfter(today) }) return TrendWindow((0L..6L).map { start.plusDays(it) }.map { DayValue(it, byDay[it]) }, true)
        return TrendWindow(byDay.keys.sorted().takeLast(7).map { DayValue(it, byDay[it]) }, false)
    }

    fun dayLabel(date: LocalDate, hindi: Boolean, today: LocalDate = LocalDate.now()): String =
        if (date == today) (careTr(hindi, "Today", "आज")) else date.format(careDateFormat("EEE d", hindi))

    fun title(name: String, window: TrendWindow, hindi: Boolean): String = when {
        window.lastWeek -> careTr(hindi, "$name over the last 7 days", "पिछले 7 दिन में $name")
        hindi -> "$name: आख़िरी ${window.days.size} दिन जब नोट लिखा"
        else -> "$name on the last ${window.days.size} days noted"
    }

    /** One plain line built only from numbers in the data, e.g. "Highest on Tue 28 (8). Latest today: 4." */
    fun summary(window: TrendWindow, points: List<DatedRating>, hindi: Boolean, today: LocalDate = LocalDate.now()): String {
        val noted = window.days.filter { it.value != null }
        if (noted.isEmpty() || points.isEmpty()) return ""
        val latest = points.last()
        val latestDate = runCatching { LocalDate.parse(latest.date) }.getOrNull()
        val latestPart = when {
            latestDate == today -> careTr(hindi, "Latest today: ${latest.value}.", "आज का आख़िरी नोट: ${latest.value}।")
            latestDate != null -> careTr(hindi, "Latest: ${latest.value} on ${dayLabel(latestDate, false, today)}.", "आख़िरी नोट: ${latest.value} (${dayLabel(latestDate, true, today)})।")
            else -> careTr(hindi, "Latest: ${latest.value}.", "आख़िरी नोट: ${latest.value}।")
        }
        if (noted.size == 1) return careTr(hindi, "Only one day noted. $latestPart", "सिर्फ़ एक दिन का नोट। $latestPart")
        val high = noted.first { it.value == noted.maxOf { d -> d.value!! } }
        val highPart = careTr(hindi, "Highest on ${dayLabel(high.date, false, today)} (${high.value}).", "सबसे ज़्यादा: ${dayLabel(high.date, true, today)} (${high.value})।")
        return "$highPart $latestPart"
    }

    /** What a screen reader says for the whole chart: title, summary and every day's number. */
    fun description(title: String, summary: String, window: TrendWindow, hindi: Boolean, today: LocalDate = LocalDate.now()): String =
        "$title. $summary " + window.days.joinToString("; ") { d ->
            "${dayLabel(d.date, hindi, today)}: ${d.value?.let { "$it ${ChartBands.word(it, hindi)}" } ?: careTr(hindi, "no entry", "कोई नोट नहीं")}"
        }
}

/** Tiny legend in words: Mild · Moderate · Severe with their colours and number ranges. */
@Composable internal fun ChartLegend(hindi: Boolean) {
    // Three soft pills, one per band, so the legend reads like the chips elsewhere in the app.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("chart-legend")) {
        listOf(2, 5, 8).zip(listOf("0–3", "4–6", "7–10")).forEach { (sample, range) ->
            Row(Modifier.background(Paper, CircleShape).border(1.dp, DoctorHairline, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).background(ChartBands.color(sample), CircleShape))
                Text("${ChartBands.word(sample, hindi)} $range", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            }
        }
    }
}

/** Empty chart: a soft rounded box with a timeline icon and one line, the same height feel as a chart so the sheet does not jump. */
@Composable internal fun ChartEmpty(hindi: Boolean, tag: String) {
    Column(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(20.dp)).border(1.dp, DoctorHairline, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Timeline, null, Modifier.size(32.dp), tint = DoctorCardSub)
        Text(careTr(hindi, "No entries yet. Add one from Today.", "अभी कोई नोट नहीं। ‘आज’ पेज से एक जोड़ें।"), color = DoctorCardSub, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("$tag-empty"))
    }
}

/**
 * One question per chart: "How strong was it each day?". A connected line (highest number each day) on a 0–10 scale with only
 * 0, 5 and 10 marked, coloured by band, a one-line summary above and every entry listed on request below.
 */
@Composable internal fun DayBarChart(name: String, points: List<DatedRating>, hindi: Boolean, tag: String) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val today = LocalDate.now()
    val window = remember(points, today) { TrendChartRules.window(points, today) }
    var showAll by rememberSaveable(tag) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (window.days.isEmpty()) {
            Text(t("$name over the last 7 days", "पिछले 7 दिन में $name"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
            ChartEmpty(hindi, tag)
        } else {
            val title = TrendChartRules.title(name, window, hindi)
            val summary = TrendChartRules.summary(window, points, hindi, today)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
            Text(summary, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.testTag("$tag-summary"))
            val chartPoints = window.days.mapIndexed { i, d -> ChartPoint(i.toDouble(), d.value?.toDouble(), d.value?.let { ChartBands.color(it) }) }
            val xLabels = window.days.mapIndexed { i, d ->
                val top = if (d.date == today) t("Today", "आज") else d.date.format(careDateFormat("EEE", hindi))
                ChartXLabel(i.toDouble(), top + "\n" + d.date.dayOfMonth, strong = d.date == today)
            }
            CareLineChart(
                points = chartPoints, yMin = 0.0, yMax = 10.0, yTicks = listOf(0.0, 5.0, 10.0), yText = { it.toInt().toString() },
                xLabels = xLabels, description = TrendChartRules.description(title, summary, window, hindi, today),
                latestText = (window.days.lastOrNull { it.value != null }?.value ?: 0).toString(), tag = tag, height = 180.dp,
                lineColor = DoctorCardSub.copy(alpha = 0.7f), xMin = 0.0, xMax = (window.days.size - 1).coerceAtLeast(1).toDouble(), xLabelLines = 2,
                bands = listOf(ChartBand(0.0, 3.5, ChartBands.Mild), ChartBand(3.5, 6.5, ChartBands.Moderate), ChartBand(6.5, 10.0, ChartBands.Severe)),
            )
            Text(t("Each point is the highest number noted that day. 0 = none, 10 = worst.", "हर बिंदु उस दिन का सबसे ऊँचा अंक है। 0 = बिलकुल नहीं, 10 = सबसे ज़्यादा।"), fontSize = 14.sp, color = DoctorCardSub)
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-toggle")) {
                Text(if (showAll) t("Hide entries", "नोट छिपाएँ") else t("See all ${points.size} entries", "सभी ${points.size} नोट देखें"))
            }
            if (showAll) points.asReversed().forEach { p ->
                Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${friendlyWhen(p.date, p.time, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.respondents, p.respondent, hindi)}", fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.weight(1f))
                    Text("${p.value} · ${ChartBands.word(p.value, hindi)}", fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                }
            }
        }
    }
}

/** "See graph" from a symptom card: one simple chart per question (Mood shows its two scales as two small charts). */
@Composable fun SymptomTrendSheet(entries: List<CareItem>, current: CareItem, value: SymptomNote, hindi: Boolean, onClose: () -> Unit) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val name = SymptomDiaryRules.symptomLabel(value.symptom, hindi)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(name, 27)
            ChartLegend(hindi)
            if (value.symptom == SymptomDiaryRules.MOOD) SymptomDiaryRules.moodShort.forEach { (key, label) ->
                val points = remember(entries, current.personId, key) { SymptomDiaryRules.moodSeries(entries, current.personId, key) }
                DayBarChart(careTr(hindi, label.first, label.second), points, hindi, "mood-trend-$key")
            } else {
                val points = remember(entries, current.personId, value.symptom) { SymptomDiaryRules.symptomSeries(entries, current.personId, value.symptom) }
                DayBarChart(name, points, hindi, "symptom-trend")
                if (value.symptom.trim() == "Pain") PainPartTrendPicker(entries, current.personId, hindi, preferred = value.painBodyRatings.maxByOrNull { it.value }?.key ?: value.painBodySites.firstOrNull())
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) }
        }
    }
}

/** Pick one body part; only that part's chart is shown, so parts never overlap on one chart. */
@Composable internal fun PainPartTrendPicker(entries: List<CareItem>, personId: String, hindi: Boolean, preferred: String? = null) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val parts = remember(entries, personId) { SymptomDiaryRules.painPartsWithHistory(entries, personId) }
    if (parts.size < 2) return
    var chosen by rememberSaveable(personId) { mutableStateOf(preferred?.takeIf { it in parts } ?: parts.first()) }
    val part = chosen.takeIf { it in parts } ?: parts.first()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = DoctorHairline)
        Text(t("Pain in one body part", "शरीर के एक हिस्से का दर्द"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(parts) { id -> FilterChip(id == part, { chosen = id }, label = { Text(PainBodyMapRules.label(id, hindi)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-part-chip-$id")) }
        }
        PainPartTrend(entries, personId, part, hindi)
    }
}

/** One body part's own pain, day by day. */
@Composable internal fun PainPartTrend(entries: List<CareItem>, personId: String, site: String, hindi: Boolean) {
    val points = remember(entries, personId, site) { SymptomDiaryRules.painPartSeries(entries, personId, site) }
    val part = PainBodyMapRules.label(site, hindi)
    DayBarChart(careTr(hindi, "$part pain", "$part में दर्द"), points, hindi, "pain-part-trend-$site")
}
