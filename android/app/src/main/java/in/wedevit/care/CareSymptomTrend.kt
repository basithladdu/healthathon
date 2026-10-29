@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.foundation.background
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
        value <= 3 -> if (hindi) "हल्का" else "Mild"
        value <= 6 -> if (hindi) "मध्यम" else "Moderate"
        else -> if (hindi) "तेज़" else "Severe"
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
        if (date == today) (if (hindi) "आज" else "Today") else date.format(careDateFormat("EEE d", hindi))

    fun title(name: String, window: TrendWindow, hindi: Boolean): String = when {
        window.lastWeek -> if (hindi) "पिछले 7 दिन में $name" else "$name over the last 7 days"
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
            latestDate == today -> if (hindi) "आज का आख़िरी नोट: ${latest.value}।" else "Latest today: ${latest.value}."
            latestDate != null -> if (hindi) "आख़िरी नोट: ${latest.value} (${dayLabel(latestDate, true, today)})।" else "Latest: ${latest.value} on ${dayLabel(latestDate, false, today)}."
            else -> if (hindi) "आख़िरी नोट: ${latest.value}।" else "Latest: ${latest.value}."
        }
        if (noted.size == 1) return if (hindi) "सिर्फ़ एक दिन का नोट। $latestPart" else "Only one day noted. $latestPart"
        val high = noted.first { it.value == noted.maxOf { d -> d.value!! } }
        val highPart = if (hindi) "सबसे ज़्यादा: ${dayLabel(high.date, true, today)} (${high.value})।" else "Highest on ${dayLabel(high.date, false, today)} (${high.value})."
        return "$highPart $latestPart"
    }

    /** What a screen reader says for the whole chart: title, summary and every day's number. */
    fun description(title: String, summary: String, window: TrendWindow, hindi: Boolean, today: LocalDate = LocalDate.now()): String =
        "$title. $summary " + window.days.joinToString("; ") { d ->
            "${dayLabel(d.date, hindi, today)}: ${d.value?.let { "$it ${ChartBands.word(it, hindi)}" } ?: if (hindi) "कोई नोट नहीं" else "no entry"}"
        }
}

/** Tiny legend in words: Mild · Moderate · Severe with their colours and number ranges. */
@Composable internal fun ChartLegend(hindi: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("chart-legend")) {
        listOf(2, 5, 8).zip(listOf("0–3", "4–6", "7–10")).forEach { (sample, range) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).background(ChartBands.color(sample), CircleShape))
                Text("${ChartBands.word(sample, hindi)} $range", fontSize = 13.sp, color = DoctorCardSub)
            }
        }
    }
}

@Composable internal fun ChartEmpty(hindi: Boolean, tag: String) =
    Text(if (hindi) "अभी कोई नोट नहीं। ‘आज’ पेज से एक जोड़ें।" else "No entries yet. Add one from Today.", color = DoctorCardSub, fontSize = 15.sp, modifier = Modifier.testTag("$tag-empty"))

/**
 * One question per chart: "How strong was it each day?". Simple bars (highest number each day) on a 0–10 scale with only
 * 0, 5 and 10 marked, coloured by band, a one-line summary above and every entry listed on request below.
 */
@Composable internal fun DayBarChart(name: String, points: List<DatedRating>, hindi: Boolean, tag: String) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
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
            val plot = 120.dp
            val axis = 26.dp
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = TrendChartRules.description(title, summary, window, hindi, today) }.testTag("$tag-chart")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.width(axis).height(plot), verticalArrangement = Arrangement.SpaceBetween) {
                        listOf("10", "5", "0").forEach { Text(it, fontSize = 12.sp, lineHeight = 12.sp, color = DoctorCardSub) }
                    }
                    window.days.forEach { day ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.value?.toString() ?: "–", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (day.value == null) DoctorCardSub else DoctorCardInk)
                            Spacer(Modifier.height(2.dp))
                            val v = day.value
                            if (v != null) Box(Modifier.fillMaxWidth(0.62f).height((plot * (v / 10f)).coerceAtLeast(4.dp))
                                .background(ChartBands.color(v), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = axis), color = DoctorCardSub.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Spacer(Modifier.width(axis))
                    window.days.forEach { day ->
                        val top = if (day.date == today) t("Today", "आज") else day.date.format(careDateFormat("EEE", hindi))
                        Text("$top\n${day.date.dayOfMonth}", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 14.sp,
                            color = if (day.date == today) DoctorCardInk else DoctorCardSub, fontWeight = if (day.date == today) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Text(t("Each bar is the highest number noted that day. 0 = none, 10 = worst.", "हर खंभा उस दिन का सबसे ऊँचा अंक है। 0 = बिलकुल नहीं, 10 = सबसे ज़्यादा।"), fontSize = 13.sp, color = DoctorCardSub)
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
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val name = SymptomDiaryRules.symptomLabel(value.symptom, hindi)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(name, 27)
            ChartLegend(hindi)
            if (value.symptom == SymptomDiaryRules.MOOD) SymptomDiaryRules.moodShort.forEach { (key, label) ->
                val points = remember(entries, current.personId, key) { SymptomDiaryRules.moodSeries(entries, current.personId, key) }
                DayBarChart(if (hindi) label.second else label.first, points, hindi, "mood-trend-$key")
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
    val t = { en: String, hi: String -> if (hindi) hi else en }
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
    DayBarChart(if (hindi) "$part में दर्द" else "$part pain", points, hindi, "pain-part-trend-$site")
}
