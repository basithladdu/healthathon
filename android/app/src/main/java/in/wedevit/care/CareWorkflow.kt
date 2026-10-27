@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/** Explicitly versioned payloads leave older, plain-text care entries untouched. */
data class CareEntry(
    val type: String,
    val detail: String = "",
    val assignee: String = "",
    val sender: String = "",
    val status: String = "open",
    val acceptedBy: String = "",
    val acceptedAt: Long = 0,
    val completedBy: String = "",
    val completedAt: Long = 0,
    val category: String = "",
    val location: String = "",
    val timed: Boolean = false,
    val value: String = "",
    val unit: String = "",
    val rangeLow: String = "",
    val rangeHigh: String = "",
    val rangeText: String = "",
    val reportId: String = "",
    val reportName: String = "",
)

object CareEntryCodec {
    const val PREFIX = "sahara:entry:v1\n"
    val types = setOf("task", "conversation", "schedule", "lab")
    fun isStructured(detail: String) = detail.startsWith(PREFIX)
    fun encode(entry: CareEntry): String {
        require(entry.type in types) { "Choose a supported care entry." }
        val json = JSONObject().put("type", entry.type).put("detail", entry.detail)
            .put("assignee", entry.assignee).put("sender", entry.sender).put("status", entry.status)
            .put("acceptedBy", entry.acceptedBy).put("acceptedAt", entry.acceptedAt)
            .put("completedBy", entry.completedBy).put("completedAt", entry.completedAt)
            .put("category", entry.category).put("location", entry.location).put("timed", entry.timed)
            .put("value", entry.value).put("unit", entry.unit).put("rangeLow", entry.rangeLow)
            .put("rangeHigh", entry.rangeHigh).put("rangeText", entry.rangeText)
            .put("reportId", entry.reportId).put("reportName", entry.reportName)
        return (PREFIX + json.toString()).also { require(it.length <= 4000) { "Shorten the details before saving." } }
    }
    fun decode(detail: String): CareEntry? {
        if (!isStructured(detail)) return null
        return runCatching {
            val j = JSONObject(detail.removePrefix(PREFIX))
            val type = j.getString("type"); require(type in types)
            CareEntry(type, j.optString("detail"), j.optString("assignee"), j.optString("sender"),
                j.optString("status", "open"), j.optString("acceptedBy"), j.optLong("acceptedAt"),
                j.optString("completedBy"), j.optLong("completedAt"), j.optString("category"),
                j.optString("location"), j.optBoolean("timed"), j.optString("value"), j.optString("unit"),
                j.optString("rangeLow"), j.optString("rangeHigh"), j.optString("rangeText"),
                j.optString("reportId"), j.optString("reportName"))
        }.getOrNull()
    }
    fun readable(detail: String): String = decode(detail)?.let { entry ->
        when (entry.type) {
            "lab" -> "${entry.value} ${entry.unit}".trim()
            "schedule" -> listOf(entry.category, entry.location, if (entry.category == "Visit") DoctorVisitDetails.readable(entry.detail) else entry.detail).filter(String::isNotBlank).joinToString(" · ")
            else -> listOf(entry.statusLabel(), entry.assignee, entry.detail).filter(String::isNotBlank).joinToString(" · ")
        }
    } ?: if (SymptomNoteCodec.isStructured(detail)) SymptomNoteCodec.readable(detail) else if (isStructured(detail)) "This entry needs a newer app to open." else detail
}

fun CareEntry.statusLabel() = when (status) {
    "accepted" -> if (type == "conversation") "Picked up" else "Accepted"
    "completed" -> "Completed"
    "cancelled" -> "Cancelled"
    else -> if (type == "conversation") "Requested" else "To do"
}

object CareWorkflowRules {
    fun validate(title: String, entry: CareEntry) {
        require(title.trim().length in 1..200) { "Add a short title, up to 200 characters." }
        require(entry.type in setOf("task", "conversation")) { "Choose a task or a conversation." }
        require(entry.assignee.trim().length in 1..120) { "Add who this is for." }
        require(entry.detail.length <= 1600 && entry.sender.length <= 160) { "Shorten the details before saving." }
    }
    fun transition(entry: CareEntry, next: String, actor: String, at: Long = System.currentTimeMillis()): CareEntry {
        require(actor.trim().length in 1..120) { "Add your name." }
        require(entry.type in setOf("task", "conversation")) { "This is not a task or conversation request." }
        val valid = when (entry.status) {
            "open" -> setOf("accepted", "cancelled")
            "accepted" -> setOf("completed", "cancelled")
            "completed", "cancelled" -> setOf("open")
            else -> emptySet()
        }
        require(next in valid) { "This entry changed. Refresh and open it again." }
        return when (next) {
            "accepted" -> entry.copy(status = next, acceptedBy = actor.trim(), acceptedAt = at)
            "completed" -> entry.copy(status = next, completedBy = actor.trim(), completedAt = at)
            "open" -> entry.copy(status = next, acceptedBy = "", acceptedAt = 0, completedBy = "", completedAt = 0)
            else -> entry.copy(status = next)
        }
    }
}

/** Validation never supplies clinical ranges or interprets a result. */
object CareLabRules {
    /** Compare only numeric limits copied for this result; never supply a clinical range. */
    fun printedFlag(entry: CareEntry): String? {
        if (entry.rangeText.isNotBlank()) return null
        val value = entry.value.toBigDecimalOrNull() ?: return null
        val low = entry.rangeLow.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        val high = entry.rangeHigh.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        if ((entry.rangeLow.isNotBlank() && low == null) || (entry.rangeHigh.isNotBlank() && high == null)) return null
        if (low != null && high != null && low > high) return null
        return when {
            low != null && value < low -> "Low · below printed range"
            high != null && value > high -> "High · above printed range"
            low != null && high != null -> "Within printed range"
            else -> null
        }
    }
    private fun number(value: String, label: String): BigDecimal {
        require(value.trim().matches(Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) { "Enter a numeric $label." }
        return value.trim().toBigDecimal()
    }
    fun validate(title: String, entry: CareEntry, due: Long, today: LocalDate = LocalDate.now()) {
        require(entry.type == "lab") { "Choose a lab result." }
        require(title.trim().length in 1..120) { "Add the test name." }
        require(entry.value.length <= 40 && entry.unit.trim().length in 1..40) { "Add the result and printed unit." }
        require(entry.rangeLow.length <= 40 && entry.rangeHigh.length <= 40) { "Shorten the printed limits." }
        number(entry.value, "result")
        require(due > 0 && careDay(due) <= today) { "Choose the date printed on the report, up to today." }
        val low = entry.rangeLow.takeIf(String::isNotBlank)?.let { number(it, "lower limit") }
        val high = entry.rangeHigh.takeIf(String::isNotBlank)?.let { number(it, "upper limit") }
        require(low == null || high == null || low <= high) { "The lower limit must be below the upper limit." }
        require(entry.rangeText.length <= 160 && entry.detail.length <= 800) { "Shorten the report notes." }
    }
}

@Composable fun WorkflowSummary(vm: CareModel, onOpen: () -> Unit) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val active = entries.mapNotNull { item -> CareEntryCodec.decode(item.detail)?.takeIf { it.type in setOf("task", "conversation") && it.status in setOf("open", "accepted") } }
    val t = careText()
    if (active.isNotEmpty()) Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Groups, null, tint = Rust); Spacer(Modifier.width(12.dp))
            val talks = active.count { it.type == "conversation" }; val jobs = active.count { it.type == "task" }
            Column(Modifier.weight(1f)) { Text(t("Care team", "देखभाल टीम"), fontWeight = FontWeight.SemiBold); Text(t("$talks conversations · $jobs tasks", "$talks बातचीत · $jobs काम"), style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, t("Open care team", "देखभाल टीम खोलें"))
        }
    }
}

@Composable fun CareWorkflows(vm: CareModel, onTalk: () -> Unit = {}) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val person by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val role by vm.viewRole.collectAsStateWithLifecycle()
    var filter by rememberSaveable(person) { mutableStateOf(if (role == "Doctor") "Conversations" else "All") }
    var showFinished by rememberSaveable(person) { mutableStateOf(false) }
    var adding by rememberSaveable(person) { mutableStateOf("") }
    val workflows = entries.filter { it.personId == person }.mapNotNull { item -> CareEntryCodec.decode(item.detail)?.takeIf { it.type in setOf("task", "conversation") }?.let { item to it } }
    val visible = workflows.filter { (_, data) -> (filter == "All" || data.type == if (filter == "Tasks") "task" else "conversation") && (showFinished || data.status !in setOf("completed", "cancelled")) }
        .sortedWith(compareBy<Pair<CareItem, CareEntry>> { it.second.status in setOf("completed", "cancelled") }.thenBy { it.first.due.takeIf { due -> due > 0 } ?: Long.MAX_VALUE }.thenByDescending { it.first.created })
    LazyColumn(Modifier.fillMaxSize().testTag("workflow-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Heading(if (role == "Doctor") "Care queue" else "Care team"); Spacer(Modifier.weight(1f)); if (vm.cloud != null) IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh care team") } } }
        if (canEdit) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { adding = "conversation" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Gold)) { Icon(Icons.Default.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ask to talk") }
            OutlinedButton(onClick = { adding = "task" }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AddTask, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add task") }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Conversations", "Tasks").forEach { label -> FilterChip(filter == label, { filter = label }, label = { Text(label) }) } } }
        item { FilterChip(showFinished, { showFinished = !showFinished }, label = { Text("Include finished") }, leadingIcon = { Icon(Icons.Default.CheckCircleOutline, null, Modifier.size(18.dp)) }) }
        if (visible.isEmpty()) item { SurfaceCard { Text(if (showFinished) "No matching requests or tasks" else "Nothing waiting here", fontWeight = FontWeight.SemiBold); if (canEdit) TextButton(onClick = { adding = if (filter == "Tasks") "task" else "conversation" }) { Text(if (filter == "Tasks") "Add a task" else "Ask for a conversation") } } }
        items(visible, key = { it.first.id }) { (item, data) -> WorkflowCard(vm, item, data, onTalk) }
    }
    if (adding.isNotEmpty()) CareWorkflowEditor(vm, adding) { adding = "" }
}

@Composable fun WorkflowCard(vm: CareModel, item: CareItem, entry: CareEntry? = CareEntryCodec.decode(item.detail), onTalk: () -> Unit = {}) {
    val data = entry ?: return
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val role by vm.viewRole.collectAsStateWithLifecycle()
    var action by remember(item.id) { mutableStateOf("") }
    var editing by remember(item.id) { mutableStateOf(false) }
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val active = data.status in setOf("open", "accepted")
    SurfaceCard(if (data.status == "completed") Sage.copy(alpha = .5f) else Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (data.type == "conversation") Icons.Default.Forum else Icons.Default.Assignment, null, tint = Rust)
            Spacer(Modifier.width(10.dp)); Text(item.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), fontFamily = SaharaTitleFont)
        }
        Text("${data.statusLabel()} · For ${data.assignee}", color = Rust, style = MaterialTheme.typography.labelLarge)
        if (data.sender.isNotBlank()) Text("From ${data.sender}", style = MaterialTheme.typography.bodySmall)
        if (item.due > 0) Text("${if (data.type == "conversation") "Preferred" else "Due"} ${date(item.due)}", style = MaterialTheme.typography.bodySmall)
        if (data.detail.isNotBlank()) Text(data.detail, modifier = Modifier.padding(top = 8.dp))
        if (data.acceptedBy.isNotBlank()) Text("Picked up by ${data.acceptedBy} · ${date(data.acceptedAt)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        if (data.completedBy.isNotBlank()) Text("Completed by ${data.completedBy} · ${date(data.completedAt)}", style = MaterialTheme.typography.bodySmall)
        if (canEdit) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (data.status == "open") TextButton(onClick = { editing = true }, enabled = !busy) { Text("Edit") }
            TextButton(onClick = { action = if (active) "cancelled" else "open" }, enabled = !busy) { Text(if (active) "Cancel" else "Reopen") }
            TextButton(onClick = { action = "remove" }, enabled = !busy) { Text("Remove") }
        }
        if (canEdit && active && (data.type != "conversation" || role == "Doctor")) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (data.type == "conversation" && data.status == "accepted") TextButton(onClick = onTalk, enabled = !busy) { Text("Open conversation") }
            TextButton(onClick = { action = if (data.status == "open") "accepted" else "completed" }, enabled = !busy) { Icon(if (data.status == "open") Icons.Default.PersonAdd else Icons.Default.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (data.status == "open") "Pick up" else if (data.type == "conversation") "Conversation done" else "Mark done") }
        }
    }
    if (editing) CareWorkflowEditor(vm, data.type, item, data) { editing = false }
    if (action.isNotEmpty()) {
        var actor by remember(action) { mutableStateOf(if (action == "completed") data.acceptedBy else "") }
        var error by remember(action) { mutableStateOf("") }
        val savingError by vm.actionError.collectAsStateWithLifecycle()
        AlertDialog(onDismissRequest = { if (!busy) action = "" }, title = { Text(when (action) { "accepted" -> "Pick up this ${if (data.type == "conversation") "conversation" else "task"}?"; "completed" -> "Mark completed?"; "cancelled" -> "Cancel this entry?"; "remove" -> "Remove this entry?"; else -> "Reopen this entry?" }) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title)
            if (action !in setOf("remove", "cancelled", "open")) OutlinedTextField(actor, { actor = it; error = "" }, label = { Text("Your name") }, singleLine = true)
            if (action == "accepted") Text("Names are self-entered and visible to this care circle.", style = MaterialTheme.typography.bodySmall)
            (error.ifBlank { savingError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            if (!canEditCareContext(vm, item.personId, capturedAccount)) { error = "Care access changed. Reopen this person's records." }
            else if (action == "remove") { vm.remove(item); action = "" }
            else runCatching {
                val name = if (action in setOf("accepted", "completed")) actor else actor.ifBlank { vm.cloud?.session?.value?.email.orEmpty().ifBlank { role } }
                val changed = CareWorkflowRules.transition(data, action, name)
                vm.updateItem(item.copy(detail = CareEntryCodec.encode(changed), doneOn = if (action == "completed") LocalDate.now().toString() else "")) { action = "" }
            }.onFailure { error = it.message.orEmpty() }
        }, modifier = Modifier.testTag("workflow-confirm-action")) { Text(if (busy) "Saving…" else when (action) { "accepted" -> "Pick up"; "completed" -> "Mark done"; "cancelled" -> "Cancel entry"; "remove" -> "Remove"; else -> "Reopen" }) } }, dismissButton = { TextButton(onClick = { action = "" }, enabled = !busy) { Text("Back") } })
    }
}

@Composable private fun CareWorkflowEditor(vm: CareModel, type: String, initial: CareItem? = null, existing: CareEntry? = null, onDismiss: () -> Unit) {
    val person by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val saveError by vm.actionError.collectAsStateWithLifecycle()
    val role by vm.viewRole.collectAsStateWithLifecycle()
    val capturedPerson = remember { person }
    val capturedAccount = remember { vm.accountNamespace }
    val entryId = rememberSaveable { initial?.id ?: newId() }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var recipient by rememberSaveable { mutableStateOf(existing?.assignee ?: if (type == "conversation") "Doctor" else "") }
    var detail by rememberSaveable { mutableStateOf(existing?.detail.orEmpty()) }
    var due by rememberSaveable { mutableLongStateOf(initial?.due ?: 0) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(entryId) { vm.actionError.value = null }
    LaunchedEffect(person) { if (person != capturedPerson) onDismiss() }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp).testTag("workflow-editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Heading(if (initial != null) "Edit ${if (type == "conversation") "request" else "task"}" else if (type == "conversation") "Ask to talk" else "Ask for help", 26)
            OutlinedTextField(title, { title = it; error = "" }, label = { Text(if (type == "conversation") "What would you like to discuss?" else "What needs doing?") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            if (title.isEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (type == "conversation") listOf("Goals of care", "Comfort and symptoms", "Care at home") else listOf("Collect medicines", "Arrange transport", "Bring reports")).forEach { suggestion -> SuggestionChip(onClick = { title = suggestion }, label = { Text(suggestion) }) }
            }
            OutlinedTextField(recipient, { recipient = it; error = "" }, label = { Text(if (type == "conversation") "Doctor or care team" else "Who is this for?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CareDateButton(due, if (type == "conversation") "Preferred date" else "Due date", { due = it }, allowClear = true)
                OutlinedTextField(detail, { detail = it }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            }
            (error.ifBlank { saveError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = {
                runCatching {
                    require(canEditCareContext(vm, capturedPerson, capturedAccount)) { "Choose the right person before saving." }
                    val payload = (existing ?: CareEntry(type, sender = vm.cloud?.session?.value?.email.orEmpty().ifBlank { role })).copy(assignee = recipient.trim(), detail = detail.trim())
                    CareWorkflowRules.validate(title, payload)
                    val encoded = CareEntryCodec.encode(payload)
                    if (initial == null) vm.saveItem("task", title, encoded, due, done = onDismiss, id = entryId)
                    else vm.updateItem(initial.copy(title = title, detail = encoded, due = due), onDismiss)
                }.onFailure { error = it.message.orEmpty() }
            }, enabled = !busy && canEdit && title.isNotBlank() && recipient.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("workflow-save"), shape = RoundedCornerShape(14.dp)) { Text(if (busy) "Saving…" else if (initial != null) "Save changes" else if (type == "conversation") "Send request" else "Add task") }
        }
    }
}
