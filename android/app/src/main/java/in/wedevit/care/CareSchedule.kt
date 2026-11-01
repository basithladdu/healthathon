@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
/** The Calendar opens on a visit's own day when a visit card opened it, otherwise on today. */
internal fun calendarStartDay(initialDay: Long, today: LocalDate = LocalDate.now()): LocalDate = if (initialDay > 0) careDay(initialDay) else today
fun calendarCategory(item: CareItem): String = CareEntryCodec.decode(item.detail)?.let {
    when (it.type) { "schedule" -> it.category; "conversation" -> "Conversation"; "task" -> "Task"; else -> "" }
} ?: when (item.kind) { "medicine" -> "Medicine"; "visit" -> "Visit"; "task" -> "Task"; else -> "" }
fun usePlainCalendarTaskEditor(item: CareItem): Boolean = item.kind == "task" && !CareEntryCodec.isStructured(item.detail)
fun calendarItems(entries: List<CareItem>, day: LocalDate): List<CareItem> = entries.filter { item ->
    item.due > 0 && careDay(item.due) == day && calendarCategory(item).isNotBlank() && CareEntryCodec.decode(item.detail)?.status != "cancelled"
}.sortedWith(compareBy<CareItem> { if (it.kind == "medicine") it.doneOn == day.toString() else it.doneOn.isNotBlank() }.thenBy { it.due }.thenBy { it.title.lowercase() })

@Composable fun DailyCareShortcuts(onCalendar: () -> Unit, onWorkflow: () -> Unit, onLabs: () -> Unit) {
    val t = careText()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("Calendar", Icons.Default.CalendarMonth, onCalendar), Triple("Care team", Icons.Default.Groups, onWorkflow), Triple("Lab history", Icons.Default.Science, onLabs)).forEach { (label, icon, action) ->
            Card(onClick = action, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag(when (label) { "Calendar" -> "shortcut-calendar"; "Care team" -> "shortcut-workflows"; else -> "shortcut-labs" }), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = when (label) { "Calendar" -> SaharaSoftBlue; "Care team" -> SaharaLavender; else -> SaharaApricot })) { Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = DoctorAccent, modifier = Modifier.size(26.dp)); Spacer(Modifier.height(6.dp)); Text(when (label) { "Calendar" -> t("Calendar", "कैलेंडर"); "Care team" -> t("Care team", "देखभाल टीम"); else -> t("Lab history", "जाँच रिपोर्टें") }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center); Text(when (label) { "Calendar" -> t("Dates and reminders", "तारीख़ें और रिमाइंडर"); "Care team" -> t("Tasks and chats", "काम और बातचीत"); else -> t("Past test results", "पुरानी जाँच के नतीजे") }, fontSize = 12.sp, lineHeight = 15.sp, color = DoctorCardSub, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        }
    }
}

@Composable fun CareDateButton(value: Long, label: String, onChange: (Long) -> Unit, allowClear: Boolean = false, latest: LocalDate? = null) {
    val context = LocalContext.current; val t = careText()
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val day = if (value > 0) careDay(value) else LocalDate.now()
            DatePickerDialog(context, { _, year, month, date -> onChange(careEpoch(LocalDate.of(year, month + 1, date))) }, day.year, day.monthValue - 1, day.dayOfMonth)
                .apply { latest?.let { datePicker.maxDate = careEpoch(it.plusDays(1)) - 1 } }.show()
        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(if (value > 0) listOf(label, date(value)).filter(String::isNotBlank).joinToString(" · ") else label) }
        if (allowClear && value > 0) IconButton(onClick = { onChange(0) }) { Icon(Icons.Default.Close, t("Remove $label", "$label हटाएँ")) }
    }
}

@Composable fun CareCalendar(vm: CareModel, onWorkflow: () -> Unit = {}, onAddDoctorVisit: (() -> Unit)? = null, initialDay: Long = 0L) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val person by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var dayString by rememberSaveable(person, initialDay) { mutableStateOf(calendarStartDay(initialDay).toString()) }
    var filter by rememberSaveable(person) { mutableStateOf("All") }
    var adding by rememberSaveable(person) { mutableStateOf(false) }
    var weekView by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CareItem?>(null) }; val t = careText(); val hindi = careHindi()
    val day =LocalDate.parse(dayString)
    val visible = calendarItems(entries.filter { it.personId == person }, day).filter { filter == "All" || calendarCategory(it) == filter }
    val undated = entries.filter { it.personId == person && it.kind == "medicine" && it.due == 0L }
    LazyColumn(Modifier.fillMaxSize().testTag("care-calendar"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onAddDoctorVisit != null && canEdit) item { TextButton(onClick = { adding = true }) { Text(t("Add a test or other calendar entry", "जाँच या कोई और तारीख़ जोड़ें")) } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Heading(t("Calendar", "कैलेंडर")); Spacer(Modifier.weight(1f)); if (canEdit) FilledTonalIconButton(onClick = { if (onAddDoctorVisit != null) onAddDoctorVisit() else adding = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Gold)) { Icon(Icons.Default.Add, t("Add to calendar", "कैलेंडर में जोड़ें")) } } }
        item { CareCalendarGrid(entries.filter { it.personId == person }, day, weekView, { weekView = it }, { dayString = it.toString() }) }
        item { Box(Modifier.fillMaxWidth()) { CareDateButton(careEpoch(day), "", { dayString = careDay(it).toString() }) } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All", "Medicine", "Visit", "Test", "Follow-up", "Procedure", "Chemotherapy", "Radiotherapy", "Task", "Conversation").forEach { category -> FilterChip(filter == category, { filter = category }, label = { Text(if (category in TreatmentDayRules.categories) TreatmentDayRules.label(category) else careCategoryLabel(category, hindi)) }) } } }
        if (day != LocalDate.now()) item { TextButton(onClick = { dayString = LocalDate.now().toString() }) { Text(t("Back to today", "आज पर वापस")) } }
        if (visible.isEmpty()) item { SurfaceCard { Text(day.format(careDateFormat("d MMM", hindi)).let { t("Nothing dated for $it", "$it के लिए कुछ तय नहीं") }, fontWeight = FontWeight.SemiBold); if (canEdit) TextButton(onClick = { if (onAddDoctorVisit != null) onAddDoctorVisit() else adding = true }) { Text(if (onAddDoctorVisit != null) t("Add a visit", "मुलाक़ात जोड़ें") else t("Add a visit, test or follow-up", "मुलाक़ात, जाँच या अगली मुलाक़ात जोड़ें")) } } }
        items(visible, key = { it.id }) { item ->
            val data = CareEntryCodec.decode(item.detail)
            if (data != null && data.type in setOf("task", "conversation")) Card(onClick = onWorkflow, colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text("${careCategoryLabel(calendarCategory(item), hindi)} · ${data.statusLabel(hindi)} · ${data.assignee}", style = MaterialTheme.typography.bodySmall); Text(t("Open care team", "देखभाल टीम खोलें"), color = RustText, style = MaterialTheme.typography.labelLarge) }
            } else CalendarEntryCard(vm, item, day, onEdit = { editing = item })
        }
        if (undated.isNotEmpty() && filter in setOf("All", "Medicine")) {
            item { SectionTitle(t("Medicines without a date", "बिना तारीख़ की दवाएँ")) }
            item { Text(t("Follow the prescription. Add a date to place a medicine in this calendar.", "पर्चे के अनुसार दें। दवा को कैलेंडर में रखने के लिए तारीख़ जोड़ें।"), style = MaterialTheme.typography.bodySmall) }
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
    val payload = CareEntryCodec.decode(item.detail); val t = careText(); val hindi = careHindi()
    val done = if (item.kind == "medicine") item.doneOn == day.toString() else item.doneOn.isNotBlank()
    val time = if (payload?.timed == true) Instant.ofEpochMilli(item.due).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")) else ""
    SurfaceCard(if (done) Sage.copy(alpha = .5f) else Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val kindColor = CalendarKinds.color(CalendarKinds.key(item))
            Box(Modifier.width(4.dp).height(40.dp).background(kindColor, RoundedCornerShape(2.dp))); Spacer(Modifier.width(8.dp))
            Icon(when (calendarCategory(item)) { "Medicine" -> Icons.Default.Medication; "Test" -> Icons.Default.Science; "Follow-up" -> Icons.Default.EventRepeat; "Procedure" -> Icons.Default.HealthAndSafety; else -> Icons.Default.Event }, null, tint = kindColor)
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text(listOf(careCategoryLabel(calendarCategory(item), hindi), time, if (done) t("Done", "हो गया") else "").filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Rust) }
            if (canEdit) IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.EditCalendar, t("Edit ${item.title}", "${item.title} बदलें")) }
        }
        if (item.detail.isNotBlank()) Text(CareEntryCodec.readable(item.detail), modifier = Modifier.padding(top = 8.dp))
        if (canEdit && (item.kind != "medicine" || day == LocalDate.now())) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.toggle(item) }, enabled = !busy) { Text(if (done) t("Undo done", "वापस करें") else if (item.kind == "medicine") t("Taken today", "आज ले ली") else t("Mark done", "हो गया")) } }
    }
}

@Composable private fun CareScheduleEditor(vm: CareModel, initial: CareItem? = null, defaultDate: Long, onDismiss: () -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saveError by vm.actionError.collectAsStateWithLifecycle()
    val context = LocalContext.current; val t = careText(); val hindi = careHindi()
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
            Heading(if (initial == null) t("Plan care", "देखभाल की योजना") else t("Edit ${category.lowercase()}", "${careCategoryLabel(category, true)} बदलें"), 26)
            if (!legacy) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { (listOf("Visit", "Test", "Follow-up", "Procedure") + TreatmentDayRules.categories).forEach { choice -> FilterChip(category == choice, { category = choice }, label = { Text(if (choice in TreatmentDayRules.categories) TreatmentDayRules.label(choice) else careCategoryLabel(choice, hindi)) }) } }
            OutlinedTextField(title, { title = it; error = "" }, label = { Text(if (category == "Medicine") t("Medicine", "दवा") else t("What is planned?", "क्या तय है?")) }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            CareDateButton(due, t("Date", "तारीख़"), { due = it; timed = false })
            if (category != "Medicine") Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val current = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
                    TimePickerDialog(context, { _, h, m -> due = careDay(due).atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); timed = true }, current.hour, current.minute, false).show()
                }) { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (timed) Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")) else t("Add a time", "समय जोड़ें")) }
                if (timed) TextButton(onClick = { due = careEpoch(careDay(due)); timed = false }) { Text(t("No time", "समय नहीं")) }
            }
            if (!extra) TextButton(onClick = { extra = true }) { Text(t("Add place or details", "जगह या जानकारी जोड़ें")) }
            if (extra) {
                if (!legacy) OutlinedTextField(location, { location = it }, label = { Text(t("Place or clinician (optional)", "जगह या डॉक्टर (चाहें तो)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(detail, { detail = it }, label = { Text(if (category == "Medicine") t("Prescribed dose and instructions", "लिखी गई ख़ुराक और तरीक़ा") else t("Details (optional)", "जानकारी (चाहें तो)")) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            }
            (error.ifBlank { saveError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = { runCatching {
                require(canEditCareContext(vm, capturedPerson, capturedAccount)) { t("Choose the right person before saving.", "सहेजने से पहले सही व्यक्ति चुनें।") }
                require(title.trim().length in 1..200 && due > 0) { t("Add a title and date.", "नाम और तारीख़ लिखें।") }
                require(detail.length <= 1600 && location.length <= 160) { t("Shorten the details before saving.", "सहेजने से पहले जानकारी छोटी करें।") }
                val text = if (initial?.kind == "medicine") detail else CareEntryCodec.encode(CareEntry(type = "schedule", category = category, detail = detail.trim(), location = location.trim(), timed = timed))
                if (initial == null) vm.saveItem("task", title, text, due, done = onDismiss, id = entryId)
                else vm.updateItem(initial.copy(title = title, detail = text, due = due), onDismiss)
            }.onFailure { error = it.message.orEmpty() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !busy && canEdit && title.isNotBlank()) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save to calendar", "कैलेंडर में सहेजें")) }
            if (initial != null && canEdit) TextButton(onClick = { remove = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(t("Remove entry", "हटाएँ")) }
        }
    }
    if (remove && initial != null) AlertDialog(onDismissRequest = { remove = false }, title = { Text(t("Remove this entry?", "इसे हटाएँ?")) }, text = { Text(initial.title) }, confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, initial.personId, capturedAccount)) vm.remove(initial); remove = false; onDismiss() }, enabled = !busy) { Text(t("Remove", "हटाएँ")) } }, dismissButton = { TextButton(onClick = { remove = false }) { Text(t("Keep", "रखें")) } })
}
