@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) }
        }
    }
}
