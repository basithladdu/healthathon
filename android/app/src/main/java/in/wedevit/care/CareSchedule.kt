@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun careDay(epoch: Long): LocalDate = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
fun careEpoch(day: LocalDate): Long = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun calendarCategory(item: CareItem): String = CareEntryCodec.decode(item.detail)?.let {
    when (it.type) { "schedule" -> it.category; "conversation" -> "Conversation"; "task" -> "Task"; else -> "" }
} ?: when (item.kind) { "medicine" -> "Medicine"; "visit" -> "Visit"; "task" -> "Task"; else -> "" }
fun usePlainCalendarTaskEditor(item: CareItem): Boolean = item.kind == "task" && !CareEntryCodec.isStructured(item.detail)
fun calendarItems(entries: List<CareItem>, day: LocalDate): List<CareItem> = entries.filter { item ->
    item.due > 0 && careDay(item.due) == day && calendarCategory(item).isNotBlank() && CareEntryCodec.decode(item.detail)?.status != "cancelled"
}.sortedWith(compareBy<CareItem> { if (it.kind == "medicine") it.doneOn == day.toString() else it.doneOn.isNotBlank() }.thenBy { it.due }.thenBy { it.title.lowercase() })

@Composable fun DailyCareShortcuts(onCalendar: () -> Unit, onWorkflow: () -> Unit, onLabs: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("Calendar", Icons.Default.CalendarMonth, onCalendar), Triple("Care team", Icons.Default.Groups, onWorkflow), Triple("Lab history", Icons.Default.Science, onLabs)).forEach { (label, icon, action) ->
            Card(onClick = action, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag(when (label) { "Calendar" -> "shortcut-calendar"; "Care team" -> "shortcut-workflows"; else -> "shortcut-labs" }), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = when (label) { "Calendar" -> SaharaSoftBlue; "Care team" -> SaharaLavender; else -> SaharaApricot })) { Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = DoctorAccent, modifier = Modifier.size(26.dp)); Spacer(Modifier.height(6.dp)); Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center); Text(when (label) { "Calendar" -> "Dates and reminders"; "Care team" -> "Tasks and chats"; else -> "Past test results" }, fontSize = 12.sp, lineHeight = 15.sp, color = DoctorCardSub, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        }
    }
}

@Composable fun CareDateButton(value: Long, label: String, onChange: (Long) -> Unit, allowClear: Boolean = false, latest: LocalDate? = null) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val day = if (value > 0) careDay(value) else LocalDate.now()
            DatePickerDialog(context, { _, year, month, date -> onChange(careEpoch(LocalDate.of(year, month + 1, date))) }, day.year, day.monthValue - 1, day.dayOfMonth)
                .apply { latest?.let { datePicker.maxDate = careEpoch(it.plusDays(1)) - 1 } }.show()
        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(if (value > 0) listOf(label, date(value)).filter(String::isNotBlank).joinToString(" · ") else label) }
        if (allowClear && value > 0) IconButton(onClick = { onChange(0) }) { Icon(Icons.Default.Close, "Remove $label") }
    }
}

@Composable fun CareCalendar(vm: CareModel, onWorkflow: () -> Unit = {}, onAddDoctorVisit: (() -> Unit)? = null) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val person by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var dayString by rememberSaveable(person) { mutableStateOf(LocalDate.now().toString()) }
    var filter by rememberSaveable(person) { mutableStateOf("All") }
    var adding by rememberSaveable(person) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CareItem?>(null) }
    val day = LocalDate.parse(dayString)
    val visible = calendarItems(entries.filter { it.personId == person }, day).filter { filter == "All" || calendarCategory(it) == filter }
    val undated = entries.filter { it.personId == person && it.kind == "medicine" && it.due == 0L }
    LazyColumn(Modifier.fillMaxSize().testTag("care-calendar"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onAddDoctorVisit != null && canEdit) item { TextButton(onClick = { adding = true }) { Text("Add a test or other calendar entry") } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Heading("Calendar"); Spacer(Modifier.weight(1f)); if (canEdit) FilledTonalIconButton(onClick = { if (onAddDoctorVisit != null) onAddDoctorVisit() else adding = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Gold)) { Icon(Icons.Default.Add, "Add to calendar") } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dayString = day.minusDays(1).toString() }) { Icon(Icons.Default.ChevronLeft, "Previous day") }
            Box(Modifier.weight(1f)) { CareDateButton(careEpoch(day), "", { dayString = careDay(it).toString() }) }
            IconButton(onClick = { dayString = day.plusDays(1).toString() }) { Icon(Icons.Default.ChevronRight, "Next day") }
        } }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (-3L..3L).forEach { offset -> val date = day.plusDays(offset); FilterChip(selected = offset == 0L, onClick = { dayString = date.toString() }, label = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(date.format(DateTimeFormatter.ofPattern("EEE")), style = MaterialTheme.typography.labelSmall); Text(date.dayOfMonth.toString(), fontWeight = FontWeight.Bold) } }, modifier = Modifier.widthIn(min = 40.dp)) }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All", "Medicine", "Visit", "Test", "Follow-up", "Procedure", "Task", "Conversation").forEach { category -> FilterChip(filter == category, { filter = category }, label = { Text(category) }) } } }
        if (day != LocalDate.now()) item { TextButton(onClick = { dayString = LocalDate.now().toString() }) { Text("Back to today") } }
        if (visible.isEmpty()) item { SurfaceCard { Text("Nothing dated for ${day.format(DateTimeFormatter.ofPattern("d MMM"))}", fontWeight = FontWeight.SemiBold); if (canEdit) TextButton(onClick = { if (onAddDoctorVisit != null) onAddDoctorVisit() else adding = true }) { Text(if (onAddDoctorVisit != null) "Add a visit" else "Add a visit, test or follow-up") } } }
        items(visible, key = { it.id }) { item ->
            val data = CareEntryCodec.decode(item.detail)
            if (data != null && data.type in setOf("task", "conversation")) Card(onClick = onWorkflow, colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text("${calendarCategory(item)} · ${data.statusLabel()} · ${data.assignee}", style = MaterialTheme.typography.bodySmall); Text("Open care team", color = Rust, style = MaterialTheme.typography.labelLarge) }
            } else CalendarEntryCard(vm, item, day, onEdit = { editing = item })
        }
        if (undated.isNotEmpty() && filter in setOf("All", "Medicine")) {
            item { SectionTitle("Medicines without a date") }
            item { Text("Follow the prescription. Add a date to place a medicine in this calendar.", style = MaterialTheme.typography.bodySmall) }
            items(undated, key = { "undated-${it.id}" }) { item -> CalendarEntryCard(vm, item, day, onEdit = { editing = item }) }
        }
    }
    if (adding) CareScheduleEditor(vm, defaultDate = careEpoch(day)) { adding = false }
    editing?.let { item ->
        if (usePlainCalendarTaskEditor(item)) AddCareItem("task", vm, onDismiss = { editing = null }, initial = item)
        else CareScheduleEditor(vm, item, defaultDate = careEpoch(day)) { editing = null }
    }
}

@Composable private fun CalendarEntryCard(vm: CareModel, item: CareItem, day: LocalDate, onEdit: () -> Unit) {
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val payload = CareEntryCodec.decode(item.detail)
    val done = if (item.kind == "medicine") item.doneOn == day.toString() else item.doneOn.isNotBlank()
    val time = if (payload?.timed == true) Instant.ofEpochMilli(item.due).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")) else ""
    SurfaceCard(if (done) Sage.copy(alpha = .5f) else Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(when (calendarCategory(item)) { "Medicine" -> Icons.Default.Medication; "Test" -> Icons.Default.Science; "Follow-up" -> Icons.Default.EventRepeat; "Procedure" -> Icons.Default.HealthAndSafety; else -> Icons.Default.Event }, null, tint = Rust)
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text(listOf(calendarCategory(item), time, if (done) "Done" else "").filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Rust) }
            if (canEdit) IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.EditCalendar, "Edit ${item.title}") }
        }
        if (item.detail.isNotBlank()) Text(CareEntryCodec.readable(item.detail), modifier = Modifier.padding(top = 8.dp))
        if (canEdit && (item.kind != "medicine" || day == LocalDate.now())) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.toggle(item) }, enabled = !busy) { Text(if (done) "Undo done" else if (item.kind == "medicine") "Taken today" else "Mark done") } }
    }
}

@Composable private fun CareScheduleEditor(vm: CareModel, initial: CareItem? = null, defaultDate: Long, onDismiss: () -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saveError by vm.actionError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val existing = initial?.let { CareEntryCodec.decode(it.detail) }
    val capturedPerson = remember { selected }
    val capturedAccount = remember { vm.accountNamespace }
    val entryId = rememberSaveable { initial?.id ?: newId() }
    val legacy = initial != null && existing == null
    var category by rememberSaveable { mutableStateOf(existing?.category ?: if (initial?.kind == "medicine") "Medicine" else "Visit") }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var detail by rememberSaveable { mutableStateOf(existing?.detail ?: initial?.detail.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(existing?.location.orEmpty()) }
    var due by rememberSaveable { mutableLongStateOf(initial?.due?.takeIf { it > 0 } ?: defaultDate) }
    var timed by rememberSaveable { mutableStateOf(existing?.timed ?: false) }
    var error by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf(false) }
    var extra by rememberSaveable { mutableStateOf(detail.isNotBlank() || location.isNotBlank()) }
    LaunchedEffect(entryId) { vm.actionError.value = null }
    LaunchedEffect(selected) { if (selected != capturedPerson) onDismiss() }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp).testTag("schedule-editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Heading(if (initial == null) "Plan care" else "Edit ${category.lowercase()}", 26)
            if (!legacy) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Visit", "Test", "Follow-up", "Procedure").forEach { choice -> FilterChip(category == choice, { category = choice }, label = { Text(choice) }) } }
            OutlinedTextField(title, { title = it; error = "" }, label = { Text(if (category == "Medicine") "Medicine" else "What is planned?") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            CareDateButton(due, "Date", { due = it; timed = false })
            if (category != "Medicine") Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val current = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
                    TimePickerDialog(context, { _, h, m -> due = careDay(due).atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); timed = true }, current.hour, current.minute, false).show()
                }) { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (timed) Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")) else "Add a time") }
                if (timed) TextButton(onClick = { due = careEpoch(careDay(due)); timed = false }) { Text("No time") }
            }
            if (!extra) TextButton(onClick = { extra = true }) { Text("Add place or details") }
            if (extra) {
                if (!legacy) OutlinedTextField(location, { location = it }, label = { Text("Place or clinician (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(detail, { detail = it }, label = { Text(if (category == "Medicine") "Prescribed dose and instructions" else "Details (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            }
            (error.ifBlank { saveError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { runCatching {
                require(canEditCareContext(vm, capturedPerson, capturedAccount)) { "Choose the right person before saving." }
                require(title.trim().length in 1..200 && due > 0) { "Add a title and date." }
                require(detail.length <= 1600 && location.length <= 160) { "Shorten the details before saving." }
                val text = if (initial?.kind == "medicine") detail else CareEntryCodec.encode(CareEntry(type = "schedule", category = category, detail = detail.trim(), location = location.trim(), timed = timed))
                if (initial == null) vm.saveItem("task", title, text, due, done = onDismiss, id = entryId)
                else vm.updateItem(initial.copy(title = title, detail = text, due = due), onDismiss)
            }.onFailure { error = it.message.orEmpty() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !busy && canEdit && title.isNotBlank()) { Text(if (busy) "Saving…" else "Save to calendar") }
            if (initial != null && canEdit) TextButton(onClick = { remove = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Remove entry") }
        }
    }
    if (remove && initial != null) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove this entry?") }, text = { Text(initial.title) }, confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, initial.personId, capturedAccount)) vm.remove(initial); remove = false; onDismiss() }, enabled = !busy) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Keep") } })
}
