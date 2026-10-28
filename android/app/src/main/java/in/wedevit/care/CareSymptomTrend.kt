@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Actual matching entries only. Dates and numbers remain visible without interpreting a diagnosis or treatment. */
@Composable fun SymptomTrendSheet(entries: List<CareItem>, current: CareItem, value: SymptomNote, hindi: Boolean, onClose: () -> Unit) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val history = SymptomDiaryRules.series(entries, current, value)
    val plotted = history.takeLast(30)
    val points = plotted.map { it.second }
    val ink = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(SymptomDiaryRules.symptomLabel(value.symptom, hindi), 27)
            if (value.symptom == SymptomDiaryRules.MOOD) SymptomDiaryRules.moodScales.forEach { (key, label) ->
                DatedRatingTrend(if (hindi) label.second else label.first, SymptomDiaryRules.moodSeries(entries, current.personId, key), hindi, "mood-trend-$key")
            }
            if (value.symptom.trim() == "Pain") PainPartTrendPicker(entries, current.personId, hindi, preferred = value.painBodyRatings.maxByOrNull { it.value }?.key ?: value.painBodySites.firstOrNull())
            if (value.symptom != SymptomDiaryRules.MOOD) {
            Text(t("Matching ratings", "समान जानकारी वाले अंक"), fontWeight = FontWeight.SemiBold)
            Text("${SymptomDiaryRules.label(SymptomDiaryRules.recalls, value.recall, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.respondents, value.respondent, hindi)} · ${value.author}")
            Text(SymptomDiaryRules.ratingAnchors(value.scale, hindi), style = MaterialTheme.typography.bodySmall)
            if (history.isEmpty()) Text(t("No matching rated entries yet", "अभी समान जानकारी वाला अंक नहीं है")) else {
                val baseline = history.first().second
                Text("${t("First matching entry", "पहला समान नोट")}: ${baseline.observedDate} ${baseline.observedTime} · ${baseline.rating}/10", modifier = Modifier.testTag("symptom-trend-baseline"))
                if (points.size < 2) Text(t("One entry. Add a later rating to see change.", "एक नोट है। बदलाव देखने के लिए बाद में नया अंक लिखें।"))
                if (history.size > plotted.size) Text(t("Chart shows the latest 30 matching ratings; first entry remains above.", "ग्राफ़ में पिछले 30 समान अंक हैं; पहला नोट ऊपर है।"), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.height(164.dp), verticalArrangement = Arrangement.SpaceBetween) { Text("10"); Text("5"); Text("0") }
                    Canvas(Modifier.weight(1f).height(164.dp).testTag("symptom-trend-chart").semantics {
                        contentDescription = t("Symptom ratings over their actual observation times. Exact dates and numbers are listed below.", "असली तारीख और समय के अनुसार अंक। नीचे हर तारीख और अंक दिए हैं।")
                    }) {
                        val inset = 7.dp.toPx()
                        val width = size.width - 2 * inset; val height = size.height - 2 * inset
                        (0..2).forEach { grid -> val y = inset + grid * height / 2; drawLine(line, Offset(inset, y), Offset(inset + width, y), 1.dp.toPx()) }
                        val start = LocalDateTime.parse("${points.first().observedDate}T${points.first().observedTime}")
                        val elapsed = points.map { ChronoUnit.MINUTES.between(start, LocalDateTime.parse("${it.observedDate}T${it.observedTime}")) }
                        val span = elapsed.last().coerceAtLeast(1)
                        val positions = points.mapIndexed { index, point -> Offset(inset + elapsed[index].toFloat() / span * width, inset + (10 - point.rating!!) / 10f * height) }
                        positions.zipWithNext().forEach { (a, b) -> drawLine(ink, a, b, 2.dp.toPx()) }
                        positions.forEach { drawCircle(ink, 4.dp.toPx(), it) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(points.first().observedDate, style = MaterialTheme.typography.labelSmall); Text(points.last().observedDate, style = MaterialTheme.typography.labelSmall) }
                Text(t("Missing / unrated answers are not plotted. Gaps follow the actual dates.", "अधूरे या बिना अंक के जवाब ग्राफ़ में नहीं हैं। दूरी असली तारीखों के अनुसार है।"), style = MaterialTheme.typography.bodySmall)
                history.asReversed().forEach { (_, note) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${note.observedDate} ${note.observedTime}"); Text("${note.rating}/10", fontWeight = FontWeight.SemiBold) }
                }
            }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) }
        }
    }
}

/** "By body part": one chip per part that has dated ratings; the chosen part's own trend shows straight away. */
@Composable internal fun PainPartTrendPicker(entries: List<CareItem>, personId: String, hindi: Boolean, preferred: String? = null) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val parts = remember(entries, personId) { SymptomDiaryRules.painPartsWithHistory(entries, personId) }
    if (parts.isEmpty()) return
    var chosen by rememberSaveable(personId) { mutableStateOf(preferred?.takeIf { it in parts } ?: parts.first()) }
    val part = chosen.takeIf { it in parts } ?: parts.first()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Pain by body part", "शरीर के हिस्से के अनुसार दर्द"), fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(parts) { id -> FilterChip(id == part, { chosen = id }, label = { Text(PainBodyMapRules.label(id, hindi)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-part-chip-$id")) }
        }
        PainPartTrend(entries, personId, part, hindi)
    }
}

/** One body part's own dated pain ratings, with the verbal band and its source. */
@Composable internal fun PainPartTrend(entries: List<CareItem>, personId: String, site: String, hindi: Boolean) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val points = remember(entries, personId, site) { SymptomDiaryRules.painPartSeries(entries, personId, site) }
    DatedRatingTrend("${PainBodyMapRules.label(site, hindi)} · ${t("pain over time", "समय के साथ दर्द")}", points, hindi, "pain-part-trend-$site", painBands = true)
    Text(t(PainBands.SOURCE_EN, PainBands.SOURCE_HI), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A dated series: the latest values as "10 → 9 → 8", a time-scaled graph and every dated rating with who reported it. */
@Composable internal fun DatedRatingTrend(title: String, points: List<DatedRating>, hindi: Boolean, tag: String, painBands: Boolean = false) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val ink = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val word = { v: Int -> if (painBands) " · ${PainBands.label(v, hindi)}" else "" }
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (points.isEmpty()) Text(t("No ratings yet", "अभी कोई अंक नहीं"), style = MaterialTheme.typography.bodySmall) else {
            val plotted = points.takeLast(30)
            Text(plotted.takeLast(6).joinToString(" → ") { it.value.toString() }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("$tag-path"))
            if (plotted.size >= 2) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.height(120.dp), verticalArrangement = Arrangement.SpaceBetween) { Text("10"); Text("5"); Text("0") }
                Canvas(Modifier.weight(1f).height(120.dp).semantics { contentDescription = t("Ratings over their actual dates. Each date and number is listed below.", "असली तारीख़ों के अनुसार अंक। नीचे हर तारीख़ और अंक दिए हैं।") }) {
                    val inset = 7.dp.toPx(); val width = size.width - 2 * inset; val height = size.height - 2 * inset
                    (0..2).forEach { grid -> val y = inset + grid * height / 2; drawLine(line, Offset(inset, y), Offset(inset + width, y), 1.dp.toPx()) }
                    val start = LocalDateTime.parse("${plotted.first().date}T${plotted.first().time}")
                    val elapsed = plotted.map { ChronoUnit.MINUTES.between(start, LocalDateTime.parse("${it.date}T${it.time}")) }
                    val span = elapsed.last().coerceAtLeast(1)
                    val positions = plotted.mapIndexed { i, p -> Offset(inset + elapsed[i].toFloat() / span * width, inset + (10 - p.value) / 10f * height) }
                    positions.zipWithNext().forEach { (a, b) -> drawLine(ink, a, b, 2.dp.toPx()) }
                    positions.forEach { drawCircle(ink, 4.dp.toPx(), it) }
                }
            }
            if (points.size > plotted.size) Text(t("Graph shows the latest 30 ratings; all dates are listed below.", "ग्राफ़ में पिछले 30 अंक हैं; सभी तारीख़ें नीचे हैं।"), style = MaterialTheme.typography.bodySmall)
            points.asReversed().forEach { p ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${friendlyWhen(p.date, p.time, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.respondents, p.respondent, hindi)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${p.value}/10${word(p.value)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
