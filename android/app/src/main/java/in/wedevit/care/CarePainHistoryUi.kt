@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Each frame is one real observation, never a merged heat map or an inferred location. */
object PainHistoryRules {
    fun entries(items: List<CareItem>, personId: String): List<Pair<CareItem, SymptomNote>> = items
        .filter { it.personId == personId && it.kind == "checkin" }
        .mapNotNull { item -> SymptomNoteCodec.decode(item.detail)?.takeIf { it.symptom.trim().equals("Pain", true) }?.let { item to it } }
        .sortedWith(compareByDescending<Pair<CareItem, SymptomNote>> { "${it.second.observedDate}T${it.second.observedTime}" }.thenBy { it.first.id })
    fun selectedId(history: List<Pair<CareItem, SymptomNote>>, preferred: String): String? = history.firstOrNull { it.first.id == preferred }?.first?.id ?: history.firstOrNull()?.first?.id
    fun locationKind(note: SymptomNote): String = when {
        note.painBodyModel.isNotBlank() && note.painBodyModel != PainBodyMapRules.MODEL -> "unsupported-model"
        note.painBodyPoints.isNotEmpty() -> "points"
        note.painBodySites.isNotEmpty() -> "regions"
        note.painLocation.isNotBlank() -> "text"
        else -> "none"
    }
}

@Composable fun PainBodyHistorySheet(entries: List<CareItem>, personId: String, hindi: Boolean, initialId: String = "", onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        PainBodyHistoryContent(entries, personId, hindi, initialId, onClose)
    }
}

/** Read-only fixture surface: no save/remove callback, account, database or patient identity lookup. */
@Composable fun PainBodyHistoryContent(entries: List<CareItem>, personId: String, hindi: Boolean, initialId: String = "", onClose: () -> Unit) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val history = remember(entries, personId) { PainHistoryRules.entries(entries, personId) }
    var preferred by rememberSaveable(personId, initialId) { mutableStateOf(initialId) }
    val selected = PainHistoryRules.selectedId(history, preferred)
    val index = history.indexOfFirst { it.first.id == selected }
    // Tapping a body-part tile zooms the model and shows that part's own dated trend below it.
    var part by rememberSaveable(personId) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).testTag("pain-history"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Heading(t("Pain history", "दर्द का इतिहास"), 27)
        if (index < 0) Text(t("No saved pain entries yet", "अभी दर्द का कोई नोट नहीं है")) else {
            val note = history[index].second
            Text(t("Choose a saved observation", "सहेजा हुआ नोट चुनें"), fontWeight = FontWeight.SemiBold)
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.first.id }) { (item, value) ->
                    FilterChip(selected == item.id, { preferred = item.id }, label = { Text("${value.observedDate} ${value.observedTime} · ${SymptomDiaryRules.ratingLabel(value.rating, hindi)}") }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-history-entry-${item.id}"))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { preferred = history[index + 1].first.id }, enabled = index < history.lastIndex, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pain-history-older")) { Text(t("Older", "पुराना")) }
                OutlinedButton(onClick = { preferred = history[index - 1].first.id }, enabled = index > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pain-history-newer")) { Text(t("Newer", "नया")) }
            }
            Text("${note.observedDate} ${note.observedTime} · ${SymptomDiaryRules.ratingLabel(note.rating, hindi)}", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("pain-history-observation"))
            Text("${SymptomDiaryRules.label(SymptomDiaryRules.recalls, note.recall, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.respondents, note.respondent, hindi)} · ${note.author}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("pain-history-context"))
            when (PainHistoryRules.locationKind(note)) {
                "points" -> Text(if (note.painBodyRatings.isNotEmpty()) t("Exact saved points. Each spot shows its own saved rating.", "सहेजे हुए सटीक बिंदु। हर जगह का रंग उसका अपना अंक दिखाता है।") else t("Exact saved points. Colour shows this entry's rating for its selected areas.", "सहेजे हुए सटीक बिंदु। रंग इस नोट के चुने हुए हिस्सों का अंक दिखाता है।"), style = MaterialTheme.typography.bodySmall)
                "regions" -> Text(t("Body areas were saved; no exact 3D point was recorded.", "शरीर के हिस्से सहेजे थे; सटीक 3D बिंदु नहीं लिखा था।"), style = MaterialTheme.typography.bodySmall)
                "text" -> Text(t("This entry has a written location only; no point is added to the body.", "इस नोट में जगह केवल लिखी है; शरीर पर कोई बिंदु नहीं जोड़ा गया है।"), style = MaterialTheme.typography.bodySmall)
                "unsupported-model" -> Text(t("This body map version cannot be displayed here. The original record is retained.", "यह शरीर के नक्शे का संस्करण यहाँ नहीं खुल सकता। मूल नोट सुरक्षित है।"), style = MaterialTheme.typography.bodySmall)
                else -> Text(t("No body location was recorded for this entry.", "इस नोट में शरीर पर दर्द की जगह नहीं लिखी थी।"), style = MaterialTheme.typography.bodySmall)
            }
            if (note.painLocation.isNotBlank()) Text(note.painLocation, modifier = Modifier.testTag("pain-history-location"))
            if (PainHistoryRules.locationKind(note) != "unsupported-model") CarePainBodyMap(note.painBodySites, note.painBodyPoints, note.rating, hindi, readOnly = true, ratings = note.painBodyRatings, onFocus = { part = it }) { _, _ -> }
            part?.let { PainPartTrend(entries, personId, it, hindi) } ?: Text(t("Tap a body part to see its own pain over time.", "किसी हिस्से को छुएँ, उसका समय के साथ दर्द दिखेगा।"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("pain-part-trend-hint"))
            if (note.startedOn.isNotBlank()) Text("${t("Started", "शुरू हुआ")}: ${note.startedOn}", style = MaterialTheme.typography.bodySmall)
            if (note.painTriggers.isNotEmpty()) Text(note.painTriggers.joinToString { SymptomDiaryRules.label(SymptomDiaryRules.triggers, it, hindi) }, style = MaterialTheme.typography.bodySmall)
            if (note.note.isNotBlank()) Text(note.note)
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pain-history-close")) { Text(t("Close", "बंद करें")) }
    }
}
