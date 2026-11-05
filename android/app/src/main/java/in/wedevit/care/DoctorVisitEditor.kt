@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

@Composable
fun DoctorVisitEditor(vm: CareModel, personId: String, onDismiss: () -> Unit, initial: CareItem? = null) { val t = careText();
    val context = LocalContext.current
    val account = remember(personId) { vm.accountNamespace }
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.actionError.collectAsStateWithLifecycle()
    val session = vm.cloud?.session?.collectAsStateWithLifecycle()?.value
    val accountMatches = if (vm.cloud == null) account.isEmpty() else session?.userId == account
    val entryId = rememberSaveable(account, personId, initial?.id) { initial?.id ?: newId() }
    val previous = remember(entryId) { initial?.let { CareEntryCodec.decode(it.detail) } }
    val priorSummary = remember(entryId) { DoctorVisitDetails.decode(previous?.detail ?: initial?.detail.orEmpty()) }
    val created = rememberSaveable(entryId) { System.currentTimeMillis() }
    var startsAt by rememberSaveable(entryId) { mutableLongStateOf(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
    var doctor by rememberSaveable(entryId) { mutableStateOf(previous?.sender.orEmpty()) }
    var location by rememberSaveable(entryId) { mutableStateOf(previous?.location.orEmpty()) }
    var note by rememberSaveable(entryId) { mutableStateOf(priorSummary.note) }
    var conditionRef by rememberSaveable(entryId) { mutableStateOf(priorSummary.conditionRef) }
    var discussionRef by rememberSaveable(entryId) { mutableStateOf(priorSummary.discussionRef) }
    val entries by vm.items.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    LaunchedEffect(entryId) { initial?.due?.takeIf { it > 0 }?.let { startsAt = it } }
    LaunchedEffect(entryId) {
        vm.actionError.value = null
        if (vm.cloud != null) try {
            val name = vm.cloud.clinicianProfile()?.name.orEmpty()
            if (vm.selected.value == personId && vm.cloud.session.value?.userId == account && doctor.isBlank()) doctor = name
        } catch (e: Exception) { if (e is CancellationException) throw e }
    }
    LaunchedEffect(selected) { if (selected != personId && !busy) onDismiss() }
    LaunchedEffect(accountMatches, busy) { if (!accountMatches && !busy) onDismiss() }
    val chosen = Instant.ofEpochMilli(startsAt).atZone(ZoneId.systemDefault())
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        DoctorVisitForm(doctor, { doctor = it.take(120) }, location, { location = it.take(160) }, note, { note = it.take(1600) }, chosen.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), chosen.format(DateTimeFormatter.ofPattern("h:mm a")), chosen.zone.id, vm.cloud == null, busy, accountMatches && selected == personId && canEdit, error,
            onDate = { DatePickerDialog(context, { _, year, month, day -> startsAt = java.time.LocalDate.of(year, month + 1, day).atTime(chosen.toLocalTime()).atZone(chosen.zone).toInstant().toEpochMilli() }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show() },
            onTime = { TimePickerDialog(context, { _, hour, minute -> startsAt = chosen.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli() }, chosen.hour, chosen.minute, false).show() },
            onSave = { try { vm.saveDoctorVisit(personId, account, doctor, startsAt, location, DoctorVisitDetails.encode(conditionRef, discussionRef, note), onDismiss, entryId, created, initial) } catch (e: IllegalArgumentException) { vm.actionError.value = e.message ?: t("Check the linked records and shorten the note.", "जुड़े रिकॉर्ड जाँचें और नोट छोटा करें।") } }, onCancel = onDismiss,
            sourceLinks = {
                HorizontalDivider(color = DoctorHairline)
                Text(t("Linked records", "जुड़े रिकॉर्ड"), Modifier.a11yHeading(), fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk)
                Text(t("Condition update", "हालत का अपडेट"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
                SourceChoice(t("None", "कोई नहीं"), conditionRef.isBlank()) { conditionRef = "" }
                entries.filter { it.personId == personId && it.kind == "checkin" }.takeLast(20).forEach { row -> SourceChoice("${date(row.created)} · ${row.title}", conditionRef == row.id) { conditionRef = row.id } }
                Text(t("Care discussion", "देखभाल चर्चा"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
                SourceChoice(t("None", "कोई नहीं"), discussionRef.isBlank()) { discussionRef = "" }
                versions.filter { it.personId == personId }.take(20).forEach { row -> SourceChoice("Version ${row.version} · ${date(row.published)}", discussionRef == row.id) { discussionRef = row.id } }
            })
    }
}

/** Stateless production form. Tests can render it without opening an account or database. */
@Composable fun DoctorVisitForm(doctor: String, onDoctor: (String) -> Unit, location: String, onLocation: (String) -> Unit, note: String, onNote: (String) -> Unit, dateLabel: String, timeLabel: String, zoneLabel: String, deviceLocal: Boolean, busy: Boolean, canSave: Boolean, error: String?, onDate: () -> Unit, onTime: () -> Unit, onSave: () -> Unit, onCancel: () -> Unit, sourceLinks: @Composable () -> Unit = {}) { val t = careText();
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).testTag("doctor-real-visit-editor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(t("Plan a visit", "विज़िट तय करें"), 26)
            CareField(doctor, onDoctor, t("Doctor's name", "डॉक्टर का नाम"), singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDate, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1.4f).heightIn(min = 48.dp), enabled = !busy) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(dateLabel, maxLines = 2) }
                OutlinedButton(onClick = onTime, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("doctor-visit-time"), enabled = !busy) { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(timeLabel, maxLines = 1) }
            }
            CareField(location, onLocation, t("Place or visit type (optional)", "जगह या विज़िट का प्रकार (चाहें तो)"), singleLine = true)
            CareField(note, onNote, t("Notes", "नोट"), minLines = 2)
            sourceLinks()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = onSave, enabled = canSave && !busy && doctor.isNotBlank(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("doctor-save-real-visit")) { if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)) }; Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save visit", "विज़िट सहेजें")) }
            TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें")) }
        }
}

@Composable private fun SourceChoice(label: String, selected: Boolean, onClick: () -> Unit) { FilterChip(selected, onClick, label = { Text(label, fontSize = 15.sp) }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) }
