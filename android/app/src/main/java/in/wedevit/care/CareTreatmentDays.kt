@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** A family-entered treatment day, stored as an ordinary all-day calendar entry. It only marks a date; it is not a treatment record. */
data class TreatmentDay(val item: CareItem, val category: String)

object TreatmentDayRules {
    val categories = listOf("Chemotherapy", "Radiotherapy")
    // One language at a time: the header language switch decides which.
    fun label(category: String) = if (category == "Chemotherapy") careTr(CareLanguageState.shown, "Chemo", "कीमो") else careTr(CareLanguageState.shown, "Radiotherapy", "रेडियोथेरेपी")
    fun days(items: List<CareItem>, personId: String): List<TreatmentDay> = items.mapNotNull { item ->
        CareEntryCodec.decode(item.detail)?.takeIf { item.personId == personId && item.due > 0 && it.type == "schedule" && it.category in categories && it.status != "cancelled" }
            ?.let { TreatmentDay(item, it.category) }
    }.sortedByDescending { it.item.due }
    /** Days that fall on the chart's own date span (both ends included), so markers never stretch the axis. */
    fun within(days: List<TreatmentDay>, first: Long, last: Long): List<TreatmentDay> {
        val from = careDay(first); val to = careDay(last)
        return days.filter { careDay(it.item.due) in from..to }.sortedBy { it.item.due }
    }
    fun entry(category: String, note: String): String {
        require(category in categories) { careBilingual("Choose chemotherapy or radiotherapy.", "कीमो या रेडियोथेरेपी चुनें।") }
        require(note.length <= 400) { careBilingual("Shorten the note.", "टिप्पणी छोटी करें।") }
        return CareEntryCodec.encode(CareEntry(type = "schedule", category = category, detail = note.trim(), timed = false))
    }
}

@Composable fun TreatmentDayEditor(vm: CareModel, initial: TreatmentDay? = null, onDismiss: () -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val saveError by vm.actionError.collectAsStateWithLifecycle()
    val captured = remember { selected }
    val entryId = rememberSaveable { initial?.item?.id ?: newId() }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: "Chemotherapy") }
    var due by rememberSaveable { mutableLongStateOf(initial?.item?.due?.let { careEpoch(careDay(it)) } ?: careEpoch(LocalDate.now())) }
    var note by rememberSaveable { mutableStateOf(initial?.let { CareEntryCodec.decode(it.item.detail)?.detail }.orEmpty()) }
    var error by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf(false) }
    val t = careText()
    LaunchedEffect(entryId) { vm.actionError.value = null }
    LaunchedEffect(selected) { if (selected != captured) onDismiss() }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).testTag("treatment-day-editor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // One title; what a treatment day does sits behind the "i" button.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Heading(if (initial == null) careBilingual("Add a treatment day", "इलाज का दिन") else careBilingual("Edit treatment day", "दिन बदलें"), 26) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { TreatmentDayRules.categories.forEach { choice ->
                FilterChip(category == choice, { category = choice }, label = { Text(TreatmentDayRules.label(choice), fontSize = 15.sp) }, shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk), modifier = Modifier.heightIn(min = 48.dp).testTag("treatment-$choice"))
            } }
            CareDateButton(due, careBilingual("Date", "तारीख"), { due = it })
            OutlinedTextField(note, { note = it.take(400); error = "" }, label = { Text(careBilingual("Note (optional)", "टिप्पणी")) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), maxLines = 3)
            (error.ifBlank { saveError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = { runCatching {
                require(selected == captured && canEdit) { careBilingual("Choose the right person before saving.", "पहले सही व्यक्ति चुनें।") }
                val detail = TreatmentDayRules.entry(category, note)
                if (initial == null) vm.saveItem("task", category, detail, due, done = onDismiss, id = entryId)
                else vm.updateItem(initial.item.copy(title = category, detail = detail, due = due), onDismiss)
            }.onFailure { error = it.message.orEmpty() } }, enabled = !busy && canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("treatment-day-save"), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White)) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else careBilingual("Save day", "सेव करें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            if (initial != null && canEdit) TextButton(onClick = { remove = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(careBilingual("Remove day", "दिन हटाएँ"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (remove && initial != null) AlertDialog(onDismissRequest = { remove = false }, title = { Text(careBilingual("Remove this treatment day?", "यह दिन हटाएँ?")) }, text = { Text("${TreatmentDayRules.label(initial.category)} · ${date(initial.item.due)}") },
        confirmButton = { TextButton(onClick = { vm.remove(initial.item); remove = false; onDismiss() }, enabled = !busy) { Text(careBilingual("Remove", "हटाएँ"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) } }, dismissButton = { TextButton(onClick = { remove = false }) { Text(careBilingual("Keep", "रखें")) } })
}
