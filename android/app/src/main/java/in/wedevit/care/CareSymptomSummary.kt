@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One symptom on the summary list: its newest note, that note's number, the one before it and every note of it, newest first. */
data class SymptomSummary(val symptom: String, val latest: CareItem, val latestScore: Int?, val previousScore: Int?, val notes: List<CareItem>) {
    /** +1 higher than last time, -1 lower, 0 the same, null when there is nothing to compare. */
    val trend: Int? get() = if (latestScore == null || previousScore == null) null else latestScore.compareTo(previousScore).coerceIn(-1, 1)
}

/**
 * The symptom list as small summary cards (Dr Sujay, 22 Nov review): one card per symptom, never the same symptom twice
 * ("Pain" and "pain " are one), with the latest number and a small arrow against the note before it.
 */
object SymptomSummaryRules {
    /** The stored key a note is grouped under: the known symptom name in its stored spelling, otherwise the trimmed name. */
    fun key(symptom: String): String = symptom.trim().let { s -> SymptomDiaryRules.symptoms.keys.firstOrNull { it.equals(s, ignoreCase = true) } ?: s }

    /** Summaries for one person, the most recently noted symptom first. Notes that cannot be read are left out. */
    fun summaries(entries: List<CareItem>, personId: String): List<SymptomSummary> =
        entries.filter { it.personId == personId && SymptomNoteCodec.isStructured(it.detail) }
            .mapNotNull { item -> SymptomNoteCodec.decode(item.detail)?.let { Triple(item, it, "${it.observedDate}T${it.observedTime}") } }
            .sortedByDescending { it.third }
            .groupBy { key(it.second.symptom) }
            .map { (symptom, rows) ->
                val scores = rows.map { SymptomSort.headline(it.first) }
                SymptomSummary(symptom, rows.first().first, scores.first(), scores.drop(1).firstOrNull { it != null }, rows.map { it.first })
            }
            .sortedByDescending { SymptomSort.stamp(it.latest) }
}

/** A small card: icon, name, when it was last noted, the latest number and a small arrow. Tapping opens that symptom's history. */
@Composable fun SymptomSummaryCard(summary: SymptomSummary, hindi: Boolean, onOpen: () -> Unit) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val note = remember(summary.latest.detail) { SymptomNoteCodec.decode(summary.latest.detail) }
    val shape = RoundedCornerShape(14.dp); val accent = symptomColor(summary.symptom)
    val arrow = when (summary.trend) { 1 -> "↑"; -1 -> "↓"; 0 -> "→"; else -> "" }
    val arrowWords = when (summary.trend) { 1 -> t("Higher than last time", "पिछली बार से ज़्यादा"); -1 -> t("Lower than last time", "पिछली बार से कम"); 0 -> t("Same as last time", "पिछली बार जितना"); else -> "" }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp).saharaPressBounce().testTag("symptom-trend-${summary.symptom}").clip(shape).background(SaharaSection.Symptoms.wash, shape)
        .border(1.dp, SaharaSection.Symptoms.solid, shape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen).padding(start = 0.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
        Spacer(Modifier.width(10.dp))
        SymptomIconChip(summary.symptom, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(SymptomDiaryRules.symptomLabel(summary.symptom, hindi), color = DoctorCardInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(friendlyWhen(note.observedDate, note.observedTime, hindi) + if (summary.notes.size > 1) " · ${summary.notes.size} ${t("notes", "नोट")}" else "",
                color = DoctorCardSub, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("symptom-summary-when"))
        }
        if (note?.symptom == SymptomDiaryRules.MOOD) {
            summary.latestScore?.let { Text("$it/10", color = DoctorCardInk, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        } else StrengthBadge(summary.latestScore, hindi)
        if (arrow.isNotEmpty()) Text(arrow, color = DoctorCardInk, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp).semantics { contentDescription = arrowWords }.testTag("symptom-summary-arrow"))
        Icon(Icons.Default.ChevronRight, null, Modifier.size(22.dp), tint = SaharaSection.Symptoms.ink)
    }
}

/**
 * A symptom's detail: its graph (Mood shows its two scales, Pain its body parts), then its newest notes with the usual card actions.
 * The graph's own entry list is left out here (clinical team, 24 Nov 2026): it repeated the same notes a second time.
 */
@Composable fun SymptomDetailSheet(summary: SymptomSummary, entries: List<CareItem>, vm: CareModel, hindi: Boolean, onClose: () -> Unit) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val personId = summary.latest.personId
    val name = SymptomDiaryRules.symptomLabel(summary.symptom, hindi)
    val latest = remember(summary.latest.detail) { SymptomNoteCodec.decode(summary.latest.detail) }
    val markers = remember(entries, personId) { symptomTreatmentMarkers(entries, personId) }
    SaharaScreenSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("symptom-detail"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { SymptomIconChip(summary.symptom, 48.dp); Box(Modifier.weight(1f)) { Heading(name, 27) } }
            ChartLegend(hindi)
            if (summary.symptom == SymptomDiaryRules.MOOD) SymptomDiaryRules.moodShort.forEach { (key, label) ->
                val points = remember(entries, personId, key) { SymptomDiaryRules.moodSeries(entries, personId, key) }
                SymptomHistoryChart(careTr(hindi, label.first, label.second), points, hindi, "mood-trend-$key", markers, showEntries = false)
            } else {
                val points = remember(entries, personId, latest?.symptom) { SymptomDiaryRules.symptomSeries(entries, personId, latest?.symptom ?: summary.symptom) }
                SymptomHistoryChart(name, points, hindi, "symptom-trend", markers, showEntries = false)
                if (summary.symptom == "Pain") SymptomPartTrendPicker(entries, personId, hindi, preferred = latest?.painBodyRatings?.maxByOrNull { it.value }?.key ?: latest?.painBodySites?.firstOrNull(), showEntries = false)
            }
            Text("${t("Notes", "नोट")} · ${summary.notes.size}", fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SaharaSection.Symptoms.ink, modifier = Modifier.a11yHeading())
            // Every note of this symptom, newest first; the latest entries come straight from the live list so edits show at once.
            val live = remember(entries, summary) { summary.notes.mapNotNull { n -> entries.firstOrNull { it.id == n.id } } }
            // The newest five notes, then "Show older" (24 Nov).
            val notesShown = rememberShownCount("$personId:${summary.symptom}")
            live.shownOf(notesShown).forEach { SymptomNoteCard(it, vm, hindi) }
            SaharaShowOlder(live.size, notesShown, tag = "symptom-notes")
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) }
        }
    }
}
