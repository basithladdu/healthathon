@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

fun labGroups(items: List<CareItem>): List<List<Pair<CareItem, CareEntry>>> = items.mapNotNull { item ->
    CareEntryCodec.decode(item.detail)?.takeIf { it.type == "lab" }?.let { item to it }
}.groupBy { (item, data) -> item.title.trim().lowercase() to data.unit.trim() }.values
    .map { values -> values.sortedByDescending { it.first.due } }.sortedByDescending { it.first().first.due }

@Composable fun CareLabs(vm: CareModel) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var query by rememberSaveable(selected) { mutableStateOf("") }
    var adding by rememberSaveable(selected) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CareItem?>(null) }
    val groups = labGroups(entries.filter { it.personId == selected && it.title.contains(query.trim(), ignoreCase = true) })
    LazyColumn(Modifier.fillMaxSize().testTag("lab-history"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Heading("Lab history"); Spacer(Modifier.weight(1f)); if (canEdit) FilledTonalIconButton(onClick = { adding = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Gold)) { Icon(Icons.Default.Add, "Add a lab result") } } }
        item { OutlinedTextField(query, { query = it }, placeholder = { Text("Find a test") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) }
        if (groups.isEmpty()) item { SurfaceCard { Icon(Icons.Default.Science, null, tint = Rust, modifier = Modifier.size(34.dp)); Spacer(Modifier.height(8.dp)); Text(if (query.isBlank()) "Keep results together" else "No matching results", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); if (query.isBlank()) { Text("Copy the value, unit and date from the report.", style = MaterialTheme.typography.bodyMedium); if (canEdit) TextButton(onClick = { adding = true }) { Text("Add a result") } } else TextButton(onClick = { query = "" }) { Text("Clear search") } } }
        items(groups, key = { values -> "${values.first().first.title.lowercase()}:${values.first().second.unit}" }) { values -> LabHistoryCard(vm, values, onEdit = { editing = it }) }
        if (groups.isNotEmpty()) item { Text("Values and reference ranges are copied from reports. Ask your clinician what they mean for your care.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
    if (adding) CareLabEditor(vm) { adding = false }
    editing?.let { item -> CareLabEditor(vm, item) { editing = null } }
}

@Composable private fun LabHistoryCard(vm: CareModel, values: List<Pair<CareItem, CareEntry>>, onEdit: (CareItem) -> Unit) {
    val latest = values.first()
    var expanded by rememberSaveable(latest.first.personId, latest.first.title, latest.second.unit) { mutableStateOf(false) }
    SurfaceCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(latest.first.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp); Text("${latest.second.value} ${latest.second.unit}", color = Rust, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Text("${date(latest.first.due)} · ${values.size} ${if (values.size == 1) "result" else "results"}", style = MaterialTheme.typography.bodySmall) }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse ${latest.first.title}" else "Show ${latest.first.title} history")
        }
        if (expanded && values.size > 1) LabHistoryPlot(values)
        (if (expanded) values else listOf(latest)).forEach { (item, data) -> LabResultRow(vm, item, data, showDate = expanded, onEdit = { onEdit(item) }) }
    }
}

@Composable private fun LabHistoryPlot(values: List<Pair<CareItem, CareEntry>>) {
    val points = values.mapNotNull { (item, entry) -> entry.value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { item.due.toDouble() to it } }.sortedBy { it.first }
    if (points.size < 2) return
    val firstTime = points.first().first
    val timeSpan = (points.last().first - firstTime).coerceAtLeast(1.0)
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${min.toBigDecimal().stripTrailingZeros().toPlainString()}–${max.toBigDecimal().stripTrailingZeros().toPlainString()} ${values.first().second.unit}", style = MaterialTheme.typography.labelSmall); Text("Recorded values", style = MaterialTheme.typography.labelSmall) }
    Canvas(Modifier.fillMaxWidth().height(82.dp).padding(vertical = 10.dp)) {
        val padding = 8.dp.toPx()
        val plotWidth = size.width - padding * 2
        val plotHeight = size.height - padding * 2
        val offsets = points.map { (time, value) -> Offset(padding + ((time - firstTime) / timeSpan * plotWidth).toFloat(), if (max == min) size.height / 2 else padding + ((max - value) / span * plotHeight).toFloat()) }
        offsets.zipWithNext().forEach { (from, to) -> drawLine(Rust, from, to, 2.dp.toPx()) }
        offsets.forEach { drawCircle(Rust, 4.dp.toPx(), it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(date(points.first().first.toLong()), style = MaterialTheme.typography.labelSmall); Text(date(points.last().first.toLong()), style = MaterialTheme.typography.labelSmall) }
}

@Composable private fun LabResultRow(vm: CareModel, item: CareItem, data: CareEntry, showDate: Boolean, onEdit: () -> Unit) {
    val reports by vm.reports.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val report = reports.firstOrNull { it.id == data.reportId && it.personId == item.personId }
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (showDate) { Text(date(item.due), fontWeight = FontWeight.SemiBold); Text("${data.value} ${data.unit}", color = Rust, style = MaterialTheme.typography.titleMedium) }
            val printedRange = data.rangeText.ifBlank { when { data.rangeLow.isNotBlank() && data.rangeHigh.isNotBlank() -> "${data.rangeLow}–${data.rangeHigh} ${data.unit}"; data.rangeLow.isNotBlank() -> "From ${data.rangeLow} ${data.unit}"; data.rangeHigh.isNotBlank() -> "Up to ${data.rangeHigh} ${data.unit}"; else -> "" } }
            if (printedRange.isNotBlank()) Text("Printed range: $printedRange", style = MaterialTheme.typography.bodySmall)
            CareLabRules.printedFlag(data)?.let { flag -> Text(flag, style = MaterialTheme.typography.labelMedium, color = if (flag.startsWith("Within")) MaterialTheme.colorScheme.secondary else Rust) }
            if (data.detail.isNotBlank()) Text(data.detail, style = MaterialTheme.typography.bodySmall)
            if (data.reportName.isNotBlank()) Text(data.reportName, style = MaterialTheme.typography.bodySmall)
            if (data.reportId.isNotBlank() && report == null) Text("Report file unavailable here", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canEdit) IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.Edit, "Edit ${item.title} result from ${date(item.due)}") }
    }
    if (report != null) TextButton(onClick = { runCatching { vm.reportFiles.intent(report, false) }.onSuccess { open(context, it) }.onFailure { vm.message.value = "Couldn't open this report. Add it again from Files." } }) { Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Open report") }
}

@Composable private fun CareLabEditor(vm: CareModel, initial: CareItem? = null, onDismiss: () -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saveError by vm.actionError.collectAsStateWithLifecycle()
    val captured = remember { selected }
    val entryId = rememberSaveable { initial?.id ?: newId() }
    val existing = initial?.let { CareEntryCodec.decode(it.detail) }
    var name by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var value by rememberSaveable { mutableStateOf(existing?.value.orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(existing?.unit.orEmpty()) }
    var due by rememberSaveable { mutableLongStateOf(initial?.due ?: careEpoch(LocalDate.now())) }
    var low by rememberSaveable { mutableStateOf(existing?.rangeLow.orEmpty()) }
    var high by rememberSaveable { mutableStateOf(existing?.rangeHigh.orEmpty()) }
    var rangeText by rememberSaveable { mutableStateOf(existing?.rangeText.orEmpty()) }
    var detail by rememberSaveable { mutableStateOf(existing?.detail.orEmpty()) }
    var reportId by rememberSaveable { mutableStateOf(existing?.reportId.orEmpty()) }
    var reportName by rememberSaveable { mutableStateOf(existing?.reportName.orEmpty()) }
    var extra by rememberSaveable { mutableStateOf(low.isNotBlank() || high.isNotBlank() || rangeText.isNotBlank() || detail.isNotBlank() || reportId.isNotBlank()) }
    var reportMenu by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(entryId) { vm.actionError.value = null }
    LaunchedEffect(selected) { if (selected != captured) onDismiss() }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp).testTag("lab-editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Heading(if (initial == null) "Add a lab result" else "Edit lab result", 26)
            OutlinedTextField(name, { name = it; error = "" }, label = { Text("Test name on report") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (name.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Haemoglobin", "Red blood cells", "Platelets", "Creatinine", "Sodium").forEach { suggestion -> SuggestionChip(onClick = { name = suggestion }, label = { Text(suggestion) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it; error = "" }, label = { Text("Value") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                OutlinedTextField(unit, { unit = it; error = "" }, label = { Text("Printed unit") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            CareDateButton(due, "Report date", { due = it }, latest = LocalDate.now())
            if (!extra) TextButton(onClick = { extra = true }) { Text("Add printed range or report") }
            if (extra) {
                Text("Printed reference range (optional)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(low, { low = it }, label = { Text("Lower") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(high, { high = it }, label = { Text("Upper") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
                OutlinedTextField(rangeText, { rangeText = it }, label = { Text("Or copy the printed range") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                Box {
                    OutlinedButton(onClick = { reportMenu = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(reportName.ifBlank { "Link an existing report" }) }
                    DropdownMenu(reportMenu, { reportMenu = false }) {
                        DropdownMenuItem(text = { Text("No report link") }, onClick = { reportId = ""; reportName = ""; reportMenu = false })
                        reports.filter { it.personId == captured }.forEach { report -> DropdownMenuItem(text = { Text(report.name) }, onClick = { reportId = report.id; reportName = report.name; reportMenu = false }) }
                    }
                }
                if (reports.none { it.personId == captured }) Text("Add report files in Documents to link them here.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(detail, { detail = it }, label = { Text("Report notes (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            }
            (error.ifBlank { saveError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { runCatching {
                require(selected == captured && canEdit) { "Choose the right person before saving." }
                val data = CareEntry(type = "lab", detail = detail.trim(), sender = existing?.sender ?: vm.cloud?.session?.value?.email.orEmpty(), value = value.trim(), unit = unit.trim(), rangeLow = low.trim(), rangeHigh = high.trim(), rangeText = rangeText.trim(), reportId = reportId, reportName = reportName)
                CareLabRules.validate(name, data, due)
                val encoded = CareEntryCodec.encode(data)
                if (initial == null) vm.saveItem("task", name, encoded, due, done = onDismiss, id = entryId)
                else vm.updateItem(initial.copy(title = name, detail = encoded, due = due), onDismiss)
            }.onFailure { error = it.message.orEmpty() } }, enabled = !busy && canEdit && name.isNotBlank() && value.isNotBlank() && unit.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Text(if (busy) "Saving…" else "Save result") }
            if (initial != null && canEdit) TextButton(onClick = { remove = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Remove result") }
        }
    }
    if (remove && initial != null) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove this result?") }, text = { Text("${initial.title} · ${date(initial.due)}") }, confirmButton = { TextButton(onClick = { vm.remove(initial); remove = false; onDismiss() }, enabled = !busy) { Text("Remove result") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Keep") } })
}
