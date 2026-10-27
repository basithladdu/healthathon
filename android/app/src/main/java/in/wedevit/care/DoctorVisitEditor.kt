@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
                Text(t("Link exact records (optional)", "रिकॉर्ड जोड़ें (चाहें तो)"), style = MaterialTheme.typography.titleMedium)
                Text(t("Linked health details remain protected by their own sharing permissions.", "जुड़ी सेहत की जानकारी अपनी साझा करने की अनुमति से सुरक्षित रहती है।"))
                Text(t("Condition update", "हालत का अपडेट"))
                SourceChoice("None", conditionRef.isBlank()) { conditionRef = "" }
                entries.filter { it.personId == personId && it.kind == "checkin" }.takeLast(20).forEach { row -> SourceChoice("${date(row.created)} · ${row.title}", conditionRef == row.id) { conditionRef = row.id } }
                Text(t("Care discussion", "देखभाल चर्चा"))
                SourceChoice("None", discussionRef.isBlank()) { discussionRef = "" }
                versions.filter { it.personId == personId }.take(20).forEach { row -> SourceChoice("Version ${row.version} · ${date(row.published)}", discussionRef == row.id) { discussionRef = row.id } }
            })
    }
}

/** Stateless production form. Tests can render it without opening an account or database. */
@Composable fun DoctorVisitForm(doctor: String, onDoctor: (String) -> Unit, location: String, onLocation: (String) -> Unit, note: String, onNote: (String) -> Unit, dateLabel: String, timeLabel: String, zoneLabel: String, deviceLocal: Boolean, busy: Boolean, canSave: Boolean, error: String?, onDate: () -> Unit, onTime: () -> Unit, onSave: () -> Unit, onCancel: () -> Unit, sourceLinks: @Composable () -> Unit = {}) { val t = careText();
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 22.dp, end = 22.dp, bottom = 28.dp).testTag("doctor-real-visit-editor"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading(t("Plan a visit", "विज़िट तय करें"), 28)
            Text(if (deviceLocal) t("This visit stays with this care profile on this device.", "यह विज़िट इसी फ़ोन पर इस देखभाल प्रोफ़ाइल के साथ रहती है।") else t("Save to this patient's shared care record. They can see the visit after refreshing their app.", "इस मरीज़ के साझा देखभाल रिकॉर्ड में सहेजें। ऐप ताज़ा करने पर उन्हें विज़िट दिखेगी।"))
            CareField(doctor, onDoctor, t("Doctor's name", "डॉक्टर का नाम"), singleLine = true)
            Text(t("This is the appointment's doctor label, not proof of professional verification.", "यह अपॉइंटमेंट के डॉक्टर का नाम है, पेशेवर पुष्टि का सबूत नहीं।"), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onDate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text(dateLabel) }
            OutlinedButton(onClick = onTime, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-visit-time"), enabled = !busy) { Text(timeLabel) }
            Text(t("Time zone: $zoneLabel", "समय क्षेत्र: $zoneLabel"), style = MaterialTheme.typography.bodySmall)
            CareField(location, onLocation, t("Place or visit type (optional)", "जगह या विज़िट का प्रकार (चाहें तो)"), singleLine = true)
            CareField(note, onNote, t("Appointment logistics (optional)", "अपॉइंटमेंट का इंतज़ाम (चाहें तो)"), minLines = 2)
            Text(t("Keep health condition and discussion details in the protected records linked below.", "सेहत की हालत और चर्चा की जानकारी नीचे जुड़े सुरक्षित रिकॉर्ड में रखें।"), style = MaterialTheme.typography.bodySmall)
            sourceLinks()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSave, enabled = canSave && !busy && doctor.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("doctor-save-real-visit")) { Text(if (busy) "Saving…" else t("Save visit", "विज़िट सहेजें")) }
            TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें")) }
        }
}

@Composable private fun SourceChoice(label: String, selected: Boolean, onClick: () -> Unit) { FilterChip(selected, onClick, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) }
