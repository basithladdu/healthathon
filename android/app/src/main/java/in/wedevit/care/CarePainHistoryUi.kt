@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.unit.sp
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
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val history = remember(entries, personId) { PainHistoryRules.entries(entries, personId) }
    var preferred by rememberSaveable(personId, initialId) { mutableStateOf(initialId) }
    val selected = PainHistoryRules.selectedId(history, preferred)
    val index = history.indexOfFirst { it.first.id == selected }
    // Tapping a body-part tile zooms the model and shows that part's own dated trend below it.
    var part by rememberSaveable(personId) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("pain-history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading(t("Past pain on the body", "पहले का दर्द शरीर पर"), 27)
        if (index < 0) ChartEmpty(hindi, "pain-history") else {
            val note = history[index].second
            Text(t("Which entry?", "कौन-सा नोट?"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.first.id }) { (item, value) ->
                    FilterChip(selected == item.id, { preferred = item.id }, label = { Text("${friendlyWhen(value.observedDate, value.observedTime, hindi)} · ${SymptomDiaryRules.ratingLabel(value.rating, hindi)}", fontSize = 14.sp) }, shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk), modifier = Modifier.heightIn(min = 48.dp).testTag("pain-history-entry-${item.id}"))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { preferred = history[index + 1].first.id }, enabled = index < history.lastIndex, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pain-history-older")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Older", "पुराना")) }
                OutlinedButton(onClick = { preferred = history[index - 1].first.id }, enabled = index > 0, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pain-history-newer")) { Text(t("Newer", "नया")); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp)) }
            }
            Text("${friendlyWhen(note.observedDate, note.observedTime, hindi)} · ${SymptomDiaryRules.ratingLabel(note.rating, hindi)}${note.rating?.let { " · ${ChartBands.word(it, hindi)}" }.orEmpty()}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("pain-history-observation"))
            Text("${SymptomDiaryRules.label(SymptomDiaryRules.recalls, note.recall, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.respondents, note.respondent, hindi)} · ${note.author}", fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.testTag("pain-history-context"))
            when (PainHistoryRules.locationKind(note)) {
                "points", "regions", "text" -> {}
                "unsupported-model" -> Text(t("Can't show this picture", "यह तस्वीर यहाँ नहीं दिख सकती"), fontSize = 14.sp, color = DoctorCardSub)
                else -> Text(t("No place marked", "जगह नहीं बताई"), fontSize = 14.sp, color = DoctorCardSub)
            }
            if (note.painLocation.isNotBlank()) Text(note.painLocation, modifier = Modifier.testTag("pain-history-location"))
            if (PainHistoryRules.locationKind(note) != "unsupported-model") CarePainBodyMap(note.painBodySites, note.painBodyPoints, note.rating, hindi, readOnly = true, ratings = note.painBodyRatings, onFocus = { part = it }) { _, _ -> }
            part?.let { SymptomPartTrend(entries, personId, it, hindi) }
            if (note.startedOn.isNotBlank()) Text("${t("Started", "शुरू हुआ")}: ${note.startedOn}", fontSize = 14.sp, color = DoctorCardSub)
            if (note.painTriggers.isNotEmpty()) Text(note.painTriggers.joinToString { SymptomDiaryRules.label(SymptomDiaryRules.triggers, it, hindi) }, fontSize = 14.sp, color = DoctorCardSub)
            if (note.note.isNotBlank()) Text(note.note, fontSize = 16.sp, color = DoctorCardInk)
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pain-history-close")) { Text(t("Close", "बंद करें")) }
    }
}
