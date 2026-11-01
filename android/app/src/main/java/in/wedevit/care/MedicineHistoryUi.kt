@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Under each medicine in the family's daily list: today's reported status, "Missed today", and that medicine's intake history. */
@Composable internal fun MedicineDoseActions(item: CareItem, vm: CareModel) {
    val t = careText()
    val events by vm.medicineEvents.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val current by vm.items.collectAsStateWithLifecycle()
    var history by rememberSaveable(item.id) { mutableStateOf(false) }
    var confirmMissed by rememberSaveable(item.id) { mutableStateOf(false) }
    var sideEffect by rememberSaveable(item.id) { mutableStateOf(false) }
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val today = LocalDate.now()
    val outcome = MedicineHistoryRules.todayOutcome(item, events, today, ZoneId.systemDefault())
    if (outcome == "skipped") Text(t("Reported missed today", "आज छूटी बताई गई"), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RustText, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (canEdit && outcome == null && current.any { it.id == item.id }) DoseChip(Icons.Default.RemoveCircleOutline, t("Missed today", "आज छूटी"), "care-missed-${item.id}", !busy) { confirmMissed = true }
        if (canEdit) DoseChip(Icons.Default.ErrorOutline, t("Side effect?", "कोई दुष्प्रभाव?"), "care-side-effect-${item.id}", !busy) { sideEffect = true }
        DoseChip(Icons.Default.History, t("History", "इतिहास"), "care-medicine-history-${item.id}", true) { history = true }
    }
    if (confirmMissed) AlertDialog(onDismissRequest = { confirmMissed = false },
        title = { Text(t("Record a missed dose?", "छूटी खुराक दर्ज करें?")) },
        text = { Text(if (vm.cloud != null) t("${item.title}: this adds a \"missed today\" report to the medicine history, shared with the people and doctor you invited. Earlier reports stay. Ask the doctor or nurse what to do about a missed dose.", "${item.title}: इससे दवा के इतिहास में “आज छूटी” की सूचना जुड़ेगी, जो आपके बुलाए लोगों और डॉक्टर को दिखेगी। पहले की जानकारी नहीं मिटेगी। छूटी खुराक के बारे में डॉक्टर या नर्स से पूछें।")
            else t("${item.title}: this adds a \"missed today\" report to the medicine history on this phone. Earlier reports stay. Ask the doctor or nurse what to do about a missed dose.", "${item.title}: इससे इस फ़ोन में दवा के इतिहास में “आज छूटी” की सूचना जुड़ेगी। पहले की जानकारी नहीं मिटेगी। छूटी खुराक के बारे में डॉक्टर या नर्स से पूछें।")) },
        confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.reportMedicineMissed(item); confirmMissed = false }, enabled = !busy && canEdit) { Text(t("Record missed", "दर्ज करें")) } },
        dismissButton = { TextButton(onClick = { confirmMissed = false }) { Text(t("Cancel", "रद्द करें")) } })
    if (sideEffect) SideEffectSheet(item, vm, onDismiss = { sideEffect = false })
    if (history) ModalBottomSheet(onDismissRequest = { history = false }, containerColor = Cream) {
        val mine = events.filter { it.medicineId == item.id }
        val now = System.currentTimeMillis()
        LazyColumn(Modifier.fillMaxWidth().testTag("care-medicine-history-sheet"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Heading("${item.title} · ${t("History", "इतिहास")}", 24) }
            item { Text(t("Family report only. Not confirmed as taken or checked by anyone else.", "सिर्फ़ परिवार की सूचना। किसी और ने लेना पक्का या जाँचा नहीं है।"), fontSize = 13.sp, color = DoctorCardSub) }
            item { Text(t("Taken and missed reports, newest first. A report is what someone recorded, not proof a dose was swallowed.", "लेने और छूटने की जानकारी, नई सबसे ऊपर। सूचना वही है जो किसी ने दर्ज की, खुराक निगलने का सबूत नहीं।"), fontSize = 13.sp, color = DoctorCardSub) }
            if (mine.isEmpty()) item { Text(t("No reports yet. Tick the box when the dose is taken, or tap Missed today.", "अभी कोई जानकारी नहीं। खुराक लेने पर बॉक्स पर टिक करें, या “आज छूटी” दबाएँ।"), fontSize = 15.sp, color = DoctorCardInk) }
            items(mine, key = { it.id }) { CompactMedicineReport(it, mine, now) }
            medicineSideEffectItems(SideEffectLog.forMedicine(SideEffectLog.entries(allItems, item.personId), item.id), vm)
            item { Text(if (vm.cloud != null) t("Saved in your account. Family and doctors you share with can see it.", "आपके खाते में सेव। जिनके साथ आपने साझा किया, वे देख सकते हैं।") else t("Saved only on this phone. Sign in to share it with family and the doctor.", "सिर्फ़ इस फ़ोन पर सेव। परिवार और डॉक्टर से साझा करने के लिए साइन इन करें।"), fontSize = 12.sp, color = DoctorCardFaint) }
            if (vm.cloud != null) item { TextButton(onClick = vm::refreshMedicineHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Refresh history", "इतिहास ताज़ा करें")) } }
        }
    }
}

/** A small rounded action capsule under a medicine's details. */
@Composable private fun DoseChip(icon: ImageVector, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    // Slim capsule so the three actions fit on one line; the touch area still meets 48dp.
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50), color = Mint, modifier = Modifier.minimumInteractiveComponentSize().testTag(tag)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = DoctorAccent); Spacer(Modifier.width(5.dp))
            Text(label, color = DoctorCardInk, fontSize = 12.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1)
        }
    }
}

private val reportTime = DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a").withZone(ZoneId.systemDefault())
private const val FAMILY_ONLY_NOTE = "Family report only. Not confirmed as taken or checked by anyone else."

/** One compact card per report: status badge and main time, then one muted reporter line. */
@Composable private fun CompactMedicineReport(event: MedicineIntakeEvent, all: List<MedicineIntakeEvent>, now: Long) {
    val t = careText()
    val status = MedicineHistoryRules.status(event, all, now)
    val (word, fg, bg) = when {
        event.outcome == "skipped" -> Triple(t("Missed", "छूटी"), DoctorStatusRest, DoctorStatusRestBg)
        status.startsWith("Late") -> Triple(t("Late", "देर से"), DoctorStatusWarn, DoctorStatusWarnBg)
        event.outcome == "taken" -> Triple(t("Taken", "ली"), DoctorStatusGood, DoctorStatusGoodBg)
        else -> Triple(t(status, MedicineStatusHindi[status] ?: status), DoctorStatusWarn, DoctorStatusWarnBg)
    }
    val main = when (event.outcome) {
        "taken" -> event.reportedTakenAt?.let { reportTime.format(Instant.ofEpochMilli(it)) }
            ?: event.reportedDate.takeIf { it.isNotBlank() }?.let { t("$it · exact time not recorded", "$it · सही समय दर्ज नहीं") }
            ?: t("Exact time not recorded", "सही समय दर्ज नहीं")
        else -> event.plannedAt?.let { t("Planned: ", "तय: ") + reportTime.format(Instant.ofEpochMilli(it)) } ?: reportTime.format(Instant.ofEpochMilli(event.loggedAt))
    }
    SurfaceCard(Paper) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = bg, shape = RoundedCornerShape(50)) { Text(word, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg) }
            Spacer(Modifier.width(10.dp))
            Text(main, Modifier.weight(1f), fontSize = 15.sp, color = DoctorCardInk)
        }
        Text(t("Reported by ", "बताने वाले: ") + event.reporter.ifBlank { t("Not recorded", "दर्ज नहीं") } + " · " + t("Logged: ", "दर्ज: ") + reportTime.format(Instant.ofEpochMilli(event.loggedAt)), Modifier.padding(top = 4.dp), fontSize = 13.sp, color = DoctorCardSub)
        if (event.note.isNotBlank() && event.note != FAMILY_ONLY_NOTE && !event.note.startsWith("Planned dose.")) Text(event.note, Modifier.padding(top = 4.dp), fontSize = 14.sp, color = DoctorCardInk)
    }
}
