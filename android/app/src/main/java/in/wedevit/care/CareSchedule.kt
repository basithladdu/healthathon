@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import java.time.LocalTime
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
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Calendar", Icons.Default.CalendarMonth, onCalendar), Triple("Care team", Icons.Default.Groups, onWorkflow), Triple("Lab history", Icons.Default.Science, onLabs)).forEach { (label, icon, action) ->
            Card(onClick = action, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag(when (label) { "Calendar" -> "shortcut-calendar"; "Care team" -> "shortcut-workflows"; else -> "shortcut-labs" }), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = when (label) { "Calendar" -> SaharaSoftBlue; "Care team" -> SaharaLavender; else -> SaharaApricot })) { Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, tint = DoctorAccent, modifier = Modifier.size(26.dp)); Spacer(Modifier.height(8.dp)); Text(when (label) { "Calendar" -> t("Calendar", "कैलेंडर"); "Care team" -> t("Care team", "देखभाल टीम"); else -> t("Lab history", "जाँच रिपोर्टें") }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
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
        }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(if (value > 0) listOf(label, date(value)).filter(String::isNotBlank).joinToString(" · ") else label) }
        if (allowClear && value > 0) IconButton(onClick = { onChange(0) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, t("Remove $label", "$label हटाएँ")) }
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
    LazyColumn(Modifier.fillMaxSize().testTag("care-calendar"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Title first, then the doctor's extra entry link under it.
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Heading(t("Calendar", "कैलेंडर")); Spacer(Modifier.weight(1f)); if (canEdit) FilledTonalIconButton(onClick = { if (onAddDoctorVisit != null) onAddDoctorVisit() else adding = true }, modifier = Modifier.size(48.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Gold)) { Icon(Icons.Default.Add, t("Add to calendar", "कैलेंडर में जोड़ें")) } } }
        if (onAddDoctorVisit != null && canEdit) item { TextButton(onClick = { adding = true }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Add other", "और जोड़ें")) } }
        item { CareCalendarGrid(entries.filter { it.personId == person }, day, weekView, { weekView = it }, { dayString = it.toString() }) }
        // The picked day and "Back to today" share one row.
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { CareDateButton(careEpoch(day), "", { dayString = careDay(it).toString() }) }
            if (day != LocalDate.now()) TextButton(onClick = { dayString = LocalDate.now().toString() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back to today", "आज पर वापस")) }
        } }
        item { FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf("All", "Medicine", "Visit", "Test", "Follow-up", "Procedure", "Chemotherapy", "Radiotherapy", "Task", "Conversation").forEach { category -> FilterChip(filter == category, { filter = category }, label = { Text(if (category in TreatmentDayRules.categories) TreatmentDayRules.label(category) else careCategoryLabel(category, hindi), maxLines = 1, softWrap = false) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp), leadingIcon = if (filter == category) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null) } } }
        if (visible.isEmpty()) item { SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = SaharaSoftBlue) { Icon(Icons.Default.EventAvailable, null, Modifier.padding(8.dp).size(20.dp), tint = DoctorAccent) }
                Spacer(Modifier.width(12.dp))
                Text(day.format(careDateFormat("d MMM", hindi)).let { t("Nothing dated for $it", "$it के लिए कुछ तय नहीं") }, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            }
            if (canEdit) TextButton(onClick = { if (onAddDoctorVisit != null) onAddDoctorVisit() else adding = true }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Add", "जोड़ें")) }
        } }
        items(visible, key = { it.id }) { item ->
            val data = CareEntryCodec.decode(item.detail)
            if (data != null && data.type in setOf("task", "conversation")) Card(onClick = onWorkflow, colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold, color = DoctorCardInk); Text(listOf(careCategoryLabel(calendarCategory(item), hindi), data.statusLabel(hindi), data.assignee).filter(String::isNotBlank).joinToString(" · "), fontSize = 14.sp, color = DoctorCardSub) }
                    Icon(Icons.Default.ChevronRight, null, tint = Rust)
                }
            } else CalendarEntryCard(vm, item, day, onEdit = { editing = item })
        }
        if (undated.isNotEmpty() && filter in setOf("All", "Medicine")) {
            item { SectionTitle(t("Medicines without a date", "बिना तारीख़ की दवाएँ")) }
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
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf(careCategoryLabel(calendarCategory(item), hindi), time).filter(String::isNotBlank).joinToString(" · "), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = RustText)
                    // Done as a badge, not more words in the line.
                    if (done) { Spacer(Modifier.width(8.dp)); Surface(shape = RoundedCornerShape(50), color = DoctorStatusGoodBg) { Text(t("Done", "हो गया"), Modifier.padding(horizontal = 8.dp, vertical = 1.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorStatusGood) } }
                }
            }
            if (canEdit) IconButton(onClick = onEdit, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.EditCalendar, t("Edit ${item.title}", "${item.title} बदलें")) }
        }
        if (item.detail.isNotBlank()) Text(CareEntryCodec.readable(item.detail), modifier = Modifier.padding(top = 8.dp), color = DoctorCardSub)
        if (canEdit && (item.kind != "medicine" || day == LocalDate.now())) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.toggle(item) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (done) t("Undo done", "वापस करें") else if (item.kind == "medicine") t("Taken today", "आज ले ली") else t("Mark done", "हो गया")) } }
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
    // A new entry opens on the tapped day (or today) at the current time, so nothing has to be typed for when.
    var due by rememberSaveable { mutableLongStateOf(initial?.due?.takeIf { it > 0 } ?: (if (defaultDate > 0) careDay(defaultDate) else LocalDate.now()).atTime(LocalTime.now().withSecond(0).withNano(0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
    var timed by rememberSaveable { mutableStateOf(existing?.timed ?: (initial == null)) }
    var error by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf(false) }
    var extra by rememberSaveable { mutableStateOf(detail.isNotBlank() || location.isNotBlank()) }
    LaunchedEffect(entryId) { vm.actionError.value = null }
    LaunchedEffect(selected) { if (selected != capturedPerson) onDismiss() }
    SaharaScreenSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 32.dp).animateContentSize().testTag("schedule-editor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(if (initial == null) t("Plan care", "देखभाल की योजना") else t("Edit ${category.lowercase()}", "${careCategoryLabel(category, true)} बदलें"), 26)
            if (!legacy) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { (listOf("Visit", "Test", "Follow-up", "Procedure") + TreatmentDayRules.categories).forEach { choice -> FilterChip(category == choice, { category = choice }, label = { Text(if (choice in TreatmentDayRules.categories) TreatmentDayRules.label(choice) else careCategoryLabel(choice, hindi), maxLines = 1, softWrap = false) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp), leadingIcon = if (category == choice) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null) } }
            OutlinedTextField(title, { title = it; error = "" }, label = { Text((if (category == "Medicine") t("Medicine", "दवा") else t("What is planned?", "क्या तय है?")) + " *") }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(14.dp))
            CareDateButton(due, t("Date", "तारीख़") + " *", { picked ->
                val tm = if (timed) Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalTime() else LocalTime.MIDNIGHT
                due = careDay(picked).atTime(tm).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            })
            if (category != "Medicine") Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val current = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
                    TimePickerDialog(context, { _, h, m -> due = careDay(due).atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); timed = true }, current.hour, current.minute, false).show()
                }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (timed) Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")) else t("Add a time", "समय जोड़ें"), maxLines = 1, softWrap = false) }
                if (timed) TextButton(onClick = { due = careEpoch(careDay(due)); timed = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("No time", "समय नहीं"), maxLines = 1, softWrap = false) }
            }
            // Place, clinician and notes are optional: one tap away, not in the way.
            TextButton(onClick = { extra = !extra }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(if (extra) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("More details", "और जानकारी")) }
            AnimatedVisibility(extra) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!legacy) OutlinedTextField(location, { location = it }, label = { Text(t("Place or clinician (optional)", "जगह या डॉक्टर (चाहें तो)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(detail, { detail = it }, label = { Text(if (category == "Medicine") t("Prescribed dose and instructions", "लिखी गई ख़ुराक और तरीक़ा") else t("Details (optional)", "जानकारी (चाहें तो)")) }, modifier = Modifier.fillMaxWidth(), maxLines = 4, shape = RoundedCornerShape(14.dp))
                }
            }
            (error.ifBlank { saveError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = { runCatching {
                require(canEditCareContext(vm, capturedPerson, capturedAccount)) { t("Choose the right person before saving.", "सहेजने से पहले सही व्यक्ति चुनें।") }
                require(title.trim().length in 1..200 && due > 0) { t("Add a title and date.", "नाम और तारीख़ लिखें।") }
                require(detail.length <= 1600 && location.length <= 160) { t("Shorten the details before saving.", "सहेजने से पहले जानकारी छोटी करें।") }
                val text = if (initial?.kind == "medicine") detail else CareEntryCodec.encode(CareEntry(type = "schedule", category = category, detail = detail.trim(), location = location.trim(), timed = timed && category != "Medicine"))
                if (initial == null) vm.saveItem("task", title, text, due, done = onDismiss, id = entryId)
                else vm.updateItem(initial.copy(title = title, detail = text, due = due), onDismiss)
            }.onFailure { error = it.message.orEmpty() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp), enabled = !busy && canEdit && title.isNotBlank()) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save to calendar", "कैलेंडर में सहेजें")) }
            if (initial != null && canEdit) TextButton(onClick = { remove = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Remove", "हटाएँ")) }
        }
    }
    if (remove && initial != null) AlertDialog(onDismissRequest = { remove = false }, title = { Text(t("Remove this entry?", "इसे हटाएँ?")) }, text = { Text(initial.title) }, confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, initial.personId, capturedAccount)) vm.remove(initial); remove = false; onDismiss() }, enabled = !busy) { Text(t("Remove", "हटाएँ")) } }, dismissButton = { TextButton(onClick = { remove = false }) { Text(t("Keep", "रखें")) } })
}
