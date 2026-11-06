package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** One line of the doctor's symptom summary: a whole symptom, one Mood scale, or one body part's pain. */
data class DoctorSymptomRow(val kind: String, val key: String, val series: List<DatedRating>) {
    val latest get() = series.last()
    val painBands get() = kind == "part" || (kind == "symptom" && key.trim().equals("Pain", true))
}

object DoctorSymptomSummaryRules {
    /** This person's rated symptom notes only, most severe first (latest number, then newest). Mood shows its two scales; Pain also shows each body part. */
    fun rows(items: List<CareItem>, personId: String): List<DoctorSymptomRow> {
        val mine = items.filter { it.personId == personId && it.kind == "checkin" }
        val symptoms = mine.mapNotNull { SymptomNoteCodec.decode(it.detail)?.symptom?.trim() }.distinctBy { it.lowercase() }
        val rows = symptoms.filter { it != SymptomDiaryRules.MOOD }.map { DoctorSymptomRow("symptom", it, SymptomDiaryRules.symptomSeries(mine, personId, it)) } +
            SymptomDiaryRules.moodScales.keys.map { DoctorSymptomRow("mood", it, SymptomDiaryRules.moodSeries(mine, personId, it)) } +
            SymptomDiaryRules.painPartsWithHistory(mine, personId).map { DoctorSymptomRow("part", it, SymptomDiaryRules.painPartSeries(mine, personId, it)) }
        return DoctorTrendRules.bySeverity(rows.filter { it.series.isNotEmpty() })
    }
    fun label(row: DoctorSymptomRow, hindi: Boolean): String = when (row.kind) {
        "mood" -> SymptomDiaryRules.moodShort[row.key]?.let { careTr(hindi, it.first, it.second) }.orEmpty()
        "part" -> "${careTr(hindi, "Pain", "दर्द")} · ${PainBodyMapRules.label(row.key, hindi)}"
        else -> SymptomDiaryRules.symptomLabel(row.key, hindi)
    }
    /** Highest number noted in the 7 days ending today, or null when nothing was noted in that time. */
    fun highestLastWeek(row: DoctorSymptomRow, today: LocalDate = LocalDate.now()): Int? = row.series
        .filter { p -> runCatching { LocalDate.parse(p.date).let { !it.isBefore(today.minusDays(6)) && !it.isAfter(today) } }.getOrDefault(false) }
        .maxOfOrNull { it.value }
    /** The entry before the latest one, if any. */
    fun previous(row: DoctorSymptomRow): Int? = row.series.dropLast(1).lastOrNull()?.value
    /** ↑ higher, ↓ lower, → same as the entry before; "–" with only one entry. */
    fun arrow(row: DoctorSymptomRow): String = previous(row)?.let { before -> when { row.latest.value > before -> "↑"; row.latest.value < before -> "↓"; else -> "→" } } ?: "–"
}

/**
 * Read-only symptom and pain summary, most severe first: one compact row per symptom. Tap a row for its trend over [range].
 * With [limit] (the patient hub) only the top rows show, and [onSeeAll] opens the Symptoms & distress tab.
 */
@Composable internal fun DoctorSymptomSummary(entries: List<CareItem>, personId: String, range: DoctorTrendRange = DoctorTrendRange.ALL, limit: Int? = null, onSeeAll: (() -> Unit)? = null, today: LocalDate = LocalDate.now()) {
    val t = careText(); val hindi = careHindi()
    val all = remember(entries, personId) { DoctorSymptomSummaryRules.rows(entries, personId) }
    val rows = limit?.let { all.take(it) } ?: all
    val hasPainBody = remember(entries, personId) { PainHistoryRules.entries(entries, personId).isNotEmpty() }
    var open by rememberSaveable(personId) { mutableStateOf("") }
    var showingBody by rememberSaveable(personId) { mutableStateOf(false) }
    val numberCol = 56.dp
    val changeCol = 72.dp
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("doctor-symptom-summary")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("Symptoms and pain", "लक्षण और दर्द"), fontFamily = SaharaTitleFont, fontSize = 20.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            if (rows.isEmpty()) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Healing, null, tint = DoctorAccent, modifier = Modifier.size(24.dp)); Text(t("No symptom notes yet.", "अभी लक्षण का कोई नोट नहीं।"), fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.testTag("doctor-symptom-summary-empty")) }
            else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(t("Symptom", "लक्षण"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, modifier = Modifier.weight(1f))
                    Text(t("Latest", "अभी"), fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, textAlign = TextAlign.Center, modifier = Modifier.width(numberCol))
                    Text(t("Highest", "सबसे ज़्यादा"), fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, textAlign = TextAlign.Center, modifier = Modifier.width(numberCol))
                    Text(t("Change", "बदलाव"), fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, textAlign = TextAlign.Center, modifier = Modifier.width(changeCol))
                }
                HorizontalDivider(color = DoctorHairline)
            }
            rows.forEach { row ->
                val id = "${row.kind}:${row.key}"
                val latest = row.latest
                val name = DoctorSymptomSummaryRules.label(row, hindi)
                val highest = DoctorTrendRules.highest(row.series, range, today)
                val before = DoctorSymptomSummaryRules.previous(row)
                val arrow = DoctorSymptomSummaryRules.arrow(row)
                val changeWords = when (arrow) {
                    "↑" -> t("up from $before", "$before से बढ़ा")
                    "↓" -> t("down from $before", "$before से घटा")
                    "→" -> t("same as before", "पहले जैसा")
                    else -> t("only one entry", "सिर्फ़ एक नोट")
                }
                val spoken = "$name. ${t("Latest", "अभी")} ${latest.value}, ${ChartBands.word(latest.value, hindi)}. " +
                    "${t("Highest", "सबसे ज़्यादा")} ${highest ?: t("none", "कोई नहीं")}. $changeWords. " +
                    if (open == id) t("Tap to hide chart.", "चार्ट छिपाने के लिए छुएँ।") else t("Tap to see chart.", "चार्ट देखने के लिए छुएँ।")
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = if (open == id) "" else id }
                    .semantics { contentDescription = spoken }.testTag("doctor-symptom-row-$id"), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(name, color = DoctorCardInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(friendlyWhen(latest.date, latest.time, hindi), fontSize = 14.sp, color = DoctorCardSub)
                        if (latest.respondent == "caregiver") SymptomLoggedByFamilyChip(hindi, "doctor-symptom-family-$id")
                    }
                    Box(Modifier.width(numberCol), contentAlignment = Alignment.Center) {
                        val fill = ChartBands.color(latest.value)
                        Text("${latest.value}", color = readableOn(fill), fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.background(fill, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    Text(highest?.toString() ?: "–", color = DoctorCardInk, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.width(numberCol))
                    val changeShort = when {
                        before == null -> "–"
                        arrow == "→" -> t("→ same", "→ वही")
                        else -> t("$arrow from $before", "$arrow $before से")
                    }
                    Text(changeShort, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        color = when (arrow) { "↑" -> ChartBands.Severe; "↓" -> ChartBands.Mild; else -> DoctorCardSub }, modifier = Modifier.width(changeCol))
                }
                if (open == id) DoctorScoreTrend(name, DoctorTrendRules.daily(row.series), range, today, "doctor-symptom-trend-$id")
                HorizontalDivider(color = DoctorHairline)
            }
            if (rows.isNotEmpty()) ChartLegend(hindi)
            if (onSeeAll != null && all.size > rows.size) TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-symptom-see-all")) {
                Text(t("All symptoms (${all.size})", "सभी लक्षण (${all.size})"), color = DoctorAccent, fontWeight = FontWeight.Medium); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = DoctorAccent)
            }
            if (hasPainBody) OutlinedButton(onClick = { showingBody = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-symptom-pain-body")) {
                Icon(Icons.Default.AccessibilityNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("View pain on body", "शरीर पर दर्द देखें"))
            }
        }
    }
    if (showingBody) PainBodyHistorySheet(entries, personId, hindi, onClose = { showingBody = false })
}
