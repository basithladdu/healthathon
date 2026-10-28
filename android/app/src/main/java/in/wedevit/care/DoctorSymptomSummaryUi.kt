package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One line of the doctor's symptom summary: a whole symptom, one Mood scale, or one body part's pain. */
data class DoctorSymptomRow(val kind: String, val key: String, val series: List<DatedRating>) {
    val latest get() = series.last()
    val painBands get() = kind == "part" || (kind == "symptom" && key.trim().equals("Pain", true))
}

object DoctorSymptomSummaryRules {
    /** This person's rated symptom notes only, newest change first. Mood shows its two scales; Pain also shows each body part. */
    fun rows(items: List<CareItem>, personId: String): List<DoctorSymptomRow> {
        val mine = items.filter { it.personId == personId && it.kind == "checkin" }
        val symptoms = mine.mapNotNull { SymptomNoteCodec.decode(it.detail)?.symptom?.trim() }.distinctBy { it.lowercase() }
        val rows = symptoms.filter { it != SymptomDiaryRules.MOOD }.map { DoctorSymptomRow("symptom", it, SymptomDiaryRules.symptomSeries(mine, personId, it)) } +
            SymptomDiaryRules.moodScales.keys.map { DoctorSymptomRow("mood", it, SymptomDiaryRules.moodSeries(mine, personId, it)) } +
            SymptomDiaryRules.painPartsWithHistory(mine, personId).map { DoctorSymptomRow("part", it, SymptomDiaryRules.painPartSeries(mine, personId, it)) }
        return rows.filter { it.series.isNotEmpty() }.sortedByDescending { "${it.latest.date}T${it.latest.time}" }
    }
    fun label(row: DoctorSymptomRow, hindi: Boolean): String = when (row.kind) {
        "mood" -> SymptomDiaryRules.moodShort[row.key]?.let { if (hindi) it.second else it.first }.orEmpty()
        "part" -> "${if (hindi) "दर्द" else "Pain"} · ${PainBodyMapRules.label(row.key, hindi)}"
        else -> SymptomDiaryRules.symptomLabel(row.key, hindi)
    }
}

/** Read-only symptom and pain summary for the doctor's patient hub. It only reads the records the shared-care access already returned for this person. */
@Composable internal fun DoctorSymptomSummary(entries: List<CareItem>, personId: String) {
    val t = careText(); val hindi = careHindi()
    val rows = remember(entries, personId) { DoctorSymptomSummaryRules.rows(entries, personId) }
    val hasPainBody = remember(entries, personId) { PainHistoryRules.entries(entries, personId).isNotEmpty() }
    var open by rememberSaveable(personId) { mutableStateOf("") }
    var showingBody by rememberSaveable(personId) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("doctor-symptom-summary")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Symptoms and pain", "लक्षण और दर्द"), fontFamily = SaharaTitleFont, fontSize = 20.sp, color = DoctorCardInk)
            Text(t("Read-only. From the patient's and family's own notes; not a clinical score.", "सिर्फ़ पढ़ने के लिए। मरीज़ और परिवार के अपने नोट; क्लिनिकल स्कोर नहीं।"), fontSize = 13.sp, color = DoctorCardSub)
            if (rows.isEmpty()) Text(t("No symptom notes yet.", "अभी लक्षण का कोई नोट नहीं।"), color = DoctorCardInk, modifier = Modifier.testTag("doctor-symptom-summary-empty"))
            rows.forEach { row ->
                val id = "${row.kind}:${row.key}"
                val latest = row.latest
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = if (open == id) "" else id }.testTag("doctor-symptom-row-$id"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(DoctorSymptomSummaryRules.label(row, hindi), color = DoctorCardInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${latest.value}/10${if (row.painBands) " · ${PainBands.label(latest.value, hindi)}" else ""}", color = DoctorCardInk, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${row.series.takeLast(6).joinToString(" → ") { it.value.toString() }} · ${friendlyWhen(latest.date, latest.time, hindi)}", fontSize = 13.sp, color = DoctorCardSub)
                }
                if (open == id) DatedRatingTrend(DoctorSymptomSummaryRules.label(row, hindi), row.series, hindi, "doctor-symptom-trend-$id", painBands = row.painBands)
            }
            if (rows.any { it.painBands }) Text(t(PainBands.SOURCE_EN, PainBands.SOURCE_HI), fontSize = 12.sp, color = DoctorCardSub)
            if (hasPainBody) OutlinedButton(onClick = { showingBody = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-symptom-pain-body")) {
                Icon(Icons.Default.AccessibilityNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("View pain on body", "शरीर पर दर्द देखें"))
            }
        }
    }
    if (showingBody) PainBodyHistorySheet(entries, personId, hindi, onClose = { showingBody = false })
}
