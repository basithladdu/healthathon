@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** The day a result belongs to: its report date, or the day it was added when no date was recorded (never 1970). */
internal fun labEpoch(item: CareItem): Long = if (item.due > 86_400_000L) item.due else item.created

internal fun labDateText(item: CareItem, t: (String, String) -> String): String =
    if (item.due > 86_400_000L) date(item.due) else if (item.created > 86_400_000L) t("${date(item.created)} (date added)", "${date(item.created)} (जोड़ने की तारीख़)") else t("Date not recorded", "तारीख़ दर्ज नहीं")

fun labGroups(items: List<CareItem>): List<List<Pair<CareItem, CareEntry>>> = items.mapNotNull { item ->
    CareEntryCodec.decode(item.detail)?.takeIf { it.type == "lab" }?.let { item to it }
}.groupBy { (item, data) -> item.title.trim().lowercase() to data.unit.trim() }.values
    .map { values -> values.sortedByDescending { labEpoch(it.first) } }.sortedByDescending { labEpoch(it.first().first) }

@Composable fun CareLabs(vm: CareModel) { val t = careText();
    val entries by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var query by rememberSaveable(selected) { mutableStateOf("") }
    var adding by rememberSaveable(selected) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CareItem?>(null) }
    var addingDay by rememberSaveable(selected) { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<TreatmentDay?>(null) }
    val treatmentDays = TreatmentDayRules.days(entries, selected)
    val groups = labGroups(entries.filter { it.personId == selected && it.title.contains(query.trim(), ignoreCase = true) })
    LazyColumn(Modifier.fillMaxSize().testTag("lab-history"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Heading(t("Lab history", "जाँच रिपोर्टें")); Spacer(Modifier.weight(1f)); if (canEdit) FilledTonalIconButton(onClick = { adding = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Gold)) { Icon(Icons.Default.Add, t("Add a lab result", "जाँच का नतीजा जोड़ें")) } } }
        item { OutlinedTextField(query, { query = it }, placeholder = { Text(t("Find a test", "जाँच खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, t("Clear search", "खोज मिटाएँ")) } }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) }
        if (groups.isEmpty()) item { SurfaceCard { Icon(Icons.Default.Science, null, tint = Rust, modifier = Modifier.size(34.dp)); Spacer(Modifier.height(8.dp)); Text(if (query.isBlank()) t("Keep results together", "सारे नतीजे एक जगह रखें") else t("No matching results", "कोई नतीजा नहीं मिला"), fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); if (query.isBlank()) { Text(t("Copy the value, unit and date from the report.", "रिपोर्ट से मान, इकाई और तारीख़ लिखें।"), style = MaterialTheme.typography.bodyMedium); if (canEdit) TextButton(onClick = { adding = true }) { Text(t("Add a result", "नतीजा जोड़ें")) } } else TextButton(onClick = { query = "" }) { Text(t("Clear search", "खोज मिटाएँ")) } } }
        item { SurfaceCard {
            Text(t("Treatment days", "इलाज के दिन"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            Text(if (treatmentDays.isEmpty()) t("Add chemo or radiotherapy days to see them marked on the charts.", "कीमो या रेडियोथेरेपी के दिन जोड़ें, ताकि वे चार्ट पर दिखें।") else t("Marked on the charts below with dashed lines.", "नीचे के चार्ट पर डैश वाली लाइन से दिखाए गए हैं।"), style = MaterialTheme.typography.bodySmall)
            treatmentDays.take(6).forEach { day -> Row(Modifier.fillMaxWidth().then(if (canEdit) Modifier.clickable { editingDay = day } else Modifier).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${date(day.item.due)} · ${TreatmentDayRules.label(day.category)}", Modifier.weight(1f), color = if (day.category == "Chemotherapy") DoctorStatusWarn else Ink)
                if (canEdit) Icon(Icons.Default.Edit, t("Edit ${day.category} day ${date(day.item.due)}", "${date(day.item.due)} का दिन बदलें"), Modifier.size(18.dp))
            } }
            if (treatmentDays.size > 6) Text(t("${treatmentDays.size - 6} earlier days are in Calendar.", "${treatmentDays.size - 6} पुराने दिन कैलेंडर में हैं।"), style = MaterialTheme.typography.bodySmall)
            if (canEdit) TextButton(onClick = { addingDay = true }, modifier = Modifier.testTag("add-treatment-day")) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add a treatment day", "इलाज का दिन जोड़ें")) }
        } }
        items(groups, key = { values -> "${values.first().first.title.lowercase()}:${values.first().second.unit}" }) { values -> LabHistoryCard(vm, values, treatmentDays, onEdit = { editing = it }) }
        if (groups.isNotEmpty()) item { Text(t("Results and normal ranges are copied from reports. Ask your doctor what they mean for you.", "मान और सामान्य सीमा रिपोर्ट से लिखे गए हैं। इनका आपकी देखभाल के लिए क्या मतलब है, डॉक्टर से पूछें।"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
    if (adding) CareLabEditor(vm) { adding = false }
    editing?.let { item -> CareLabEditor(vm, item) { editing = null } }
    if (addingDay) TreatmentDayEditor(vm) { addingDay = false }
    editingDay?.let { day -> TreatmentDayEditor(vm, day) { editingDay = null } }
}

@Composable private fun LabHistoryCard(vm: CareModel, values: List<Pair<CareItem, CareEntry>>, treatmentDays: List<TreatmentDay>, onEdit: (CareItem) -> Unit) { val t = careText();
    val latest = values.first()
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(latest.first.title, fontWeight = FontWeight.Medium, fontSize = 18.sp, fontFamily = SaharaTitleFont); Text("${latest.second.value} ${latest.second.unit}", color = Rust, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Text("${labDateText(latest.first, t)} · ${values.size} ${if (values.size == 1) "result" else "results"}", style = MaterialTheme.typography.bodySmall) }
        }
        if (values.size > 1) LabHistoryPlot(values, treatmentDays)
        values.forEach { (item, data) -> LabResultRow(vm, item, data, showDate = true, onEdit = { onEdit(item) }) }
    }
}

private fun labNum(v: Double): String = v.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Printed low/high from the newest result's range fields or text such as "13.0–17.0 g/dL". */
private fun labRange(data: CareEntry): Pair<Double?, Double?> {
    val low = data.rangeLow.toDoubleOrNull(); val high = data.rangeHigh.toDoubleOrNull()
    if (low != null || high != null) return low to high
    val nums = Regex("""\d+(?:\.\d+)?""").findAll(data.rangeText).mapNotNull { it.value.toDoubleOrNull() }.toList()
    return if (nums.size == 2 && nums[0] <= nums[1]) nums[0] to nums[1] else null to null
}

@Composable private fun LabHistoryPlot(values: List<Pair<CareItem, CareEntry>>, treatmentDays: List<TreatmentDay>) { val t = careText();
    // oldest first; sortedBy is stable so results sharing a day stay in recorded order
    val points = values.reversed().mapNotNull { (item, entry) -> entry.value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { labEpoch(item).toDouble() to it } }.sortedBy { it.first }
    if (points.size < 2) return
    val unit = values.first().second.unit
    val (refLow, refHigh) = labRange(values.first().second)
    val firstTime = points.first().first
    val lastTime = points.last().first
    val markers = TreatmentDayRules.within(treatmentDays, firstTime.toLong(), lastTime.toLong())
    val dataMin = points.minOf { it.second }; val dataMax = points.maxOf { it.second }
    val lowEdge = listOfNotNull(dataMin, refLow, refHigh).min(); val highEdge = listOfNotNull(dataMax, refLow, refHigh).max()
    val pad = ((highEdge - lowEdge).takeIf { it > 0 } ?: kotlin.math.abs(highEdge).coerceAtLeast(1.0)) * 0.12
    // Round the axis to a tidy step (1, 2, 2.5, 5 × 10ⁿ) so gridlines read 8, 10, 12… rather than 8.94, 11.91.
    val rawStep = ((highEdge + pad) - (lowEdge - pad)) / 3.0
    val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(rawStep.coerceAtLeast(1e-9))))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rawStep }
    val yMin = kotlin.math.floor((lowEdge - pad) / step) * step; val yMax = kotlin.math.ceil((highEdge + pad) / step) * step
    val ticks = generateSequence(yMin) { it + step }.takeWhile { it <= yMax + step / 2 }.toList()
    val bands = if (refLow != null || refHigh != null) listOf(ChartBand(refLow ?: yMin, refHigh ?: yMax, Mint, 0.7f)) else emptyList()
    // A date under every result (the newest bold); labels that would overlap are dropped by the chart.
    val xLabels = buildList {
        add(ChartXLabel(lastTime, shortLabDate(lastTime.toLong()), strong = true))
        points.dropLast(1).asReversed().forEach { add(ChartXLabel(it.first, shortLabDate(it.first.toLong()))) }
    }
    val summary = points.joinToString("; ") { (time, v) -> "${date(time.toLong())}: ${labNum(v)} $unit" }
    val rangeText = if (bands.isEmpty()) "" else t(" Printed range ${labNum(refLow ?: yMin)} to ${labNum(refHigh ?: yMax)} $unit.", " छपी सीमा ${labNum(refLow ?: yMin)} से ${labNum(refHigh ?: yMax)} $unit।")
    Text(t("Recorded values ($unit)", "दर्ज मान ($unit)"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
    CareLineChart(
        points = points.map { ChartPoint(it.first, it.second) }, yMin = yMin, yMax = yMax, yTicks = ticks, yText = { labNum(it) },
        xLabels = xLabels, latestText = labNum(points.last().second), tag = "lab-trend", height = 200.dp, bands = bands, pointText = { labNum(it) },
        markers = markers.map { ChartMarker(careEpoch(careDay(it.item.due)).toDouble(), if (it.category == "Chemotherapy") DoctorStatusWarn else Ink) },
        description = t("Line chart of ${points.size} recorded values. $summary.$rangeText", "${points.size} दर्ज मानों का रेखा चार्ट। $summary।$rangeText"),
    )
    // Legend and change: what the band and dashed lines mean, and how far the value has moved since the first result.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (bands.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 10.dp).background(Mint, RoundedCornerShape(3.dp))); Spacer(Modifier.width(5.dp))
            Text(t("Printed range ${labNum(refLow ?: yMin)}–${labNum(refHigh ?: yMax)} $unit", "छपी सीमा ${labNum(refLow ?: yMin)}–${labNum(refHigh ?: yMax)} $unit"), style = MaterialTheme.typography.labelSmall, color = DoctorCardInk)
        }
        if (markers.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("┆", color = DoctorStatusWarn, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp))
            Text(t("Treatment day", "इलाज का दिन"), style = MaterialTheme.typography.labelSmall, color = DoctorCardInk)
        }
    }
    if (points.size > 1) {
        val change = points.last().second - points.first().second
        val arrow = if (change > 0) "↑" else if (change < 0) "↓" else "→"
        val since = shortLabDate(firstTime.toLong())
        Text(t("$arrow ${labNum(kotlin.math.abs(change))} $unit since $since · ${points.size} results", "$arrow ${labNum(kotlin.math.abs(change))} $unit, $since से · ${points.size} नतीजे"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.padding(top = 2.dp))
    }
    TreatmentDayRules.categories.forEach { category -> markers.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { shown ->
        Text("┆ ${TreatmentDayRules.label(category)}: " + shown.joinToString(", ") { date(it.item.due) }, style = MaterialTheme.typography.labelSmall, color = if (category == "Chemotherapy") DoctorStatusWarn else Ink)
    } }
}

@Composable private fun LabResultRow(vm: CareModel, item: CareItem, data: CareEntry, showDate: Boolean, onEdit: () -> Unit) { val t = careText();
    val reports by vm.reports.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val report = reports.firstOrNull { it.id == data.reportId && it.personId == item.personId }
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (showDate) { Text(labDateText(item, t), fontWeight = FontWeight.SemiBold); Text("${data.value} ${data.unit}", color = Rust, style = MaterialTheme.typography.titleMedium) }
            val printedRange = data.rangeText.ifBlank { when { data.rangeLow.isNotBlank() && data.rangeHigh.isNotBlank() -> "${data.rangeLow}–${data.rangeHigh} ${data.unit}"; data.rangeLow.isNotBlank() -> t("From ${data.rangeLow} ${data.unit}", "कम से कम ${data.rangeLow} ${data.unit}"); data.rangeHigh.isNotBlank() -> t("Up to ${data.rangeHigh} ${data.unit}", "ज़्यादा से ज़्यादा ${data.rangeHigh} ${data.unit}"); else -> "" } }
            if (printedRange.isNotBlank()) Text(t("Printed range: $printedRange", "छपी सीमा: $printedRange"), style = MaterialTheme.typography.bodySmall)
            CareLabRules.printedFlag(data)?.let { flag -> Text(t(flag, when { flag.startsWith("Low") -> "कम · छपी सीमा से नीचे"; flag.startsWith("High") -> "ज़्यादा · छपी सीमा से ऊपर"; else -> "छपी सीमा के अंदर" }), style = MaterialTheme.typography.labelMedium, color = if (flag.startsWith("Within")) MaterialTheme.colorScheme.secondary else Rust) }
            if (data.detail.isNotBlank()) Text(data.detail, style = MaterialTheme.typography.bodySmall)
            if (data.reportName.isNotBlank()) Text(data.reportName, style = MaterialTheme.typography.bodySmall)
            if (data.reportId.isNotBlank() && report == null) Text(t("Report file unavailable here", "रिपोर्ट की फ़ाइल यहाँ नहीं है"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canEdit) IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.Edit, t("Edit ${item.title} result from ${labDateText(item, t)}", "${labDateText(item, t)} का ${item.title} नतीजा बदलें")) }
    }
    if (report != null) TextButton(onClick = { runCatching { vm.reportFiles.intent(report, false) }.onSuccess { open(context, it) }.onFailure { vm.message.value = t("Couldn't open this report. Add it again from Files.", "यह रिपोर्ट नहीं खुल सकी। Files से इसे फिर जोड़ें।") } }) { Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(t("Open report", "रिपोर्ट खोलें")) }
}

@Composable private fun CareLabEditor(vm: CareModel, initial: CareItem? = null, onDismiss: () -> Unit) { val t = careText();
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
    var due by rememberSaveable { mutableLongStateOf(initial?.let { labEpoch(it) } ?: careEpoch(LocalDate.now())) }
    var low by rememberSaveable { mutableStateOf(existing?.rangeLow.orEmpty()) }
    var high by rememberSaveable { mutableStateOf(existing?.rangeHigh.orEmpty()) }
    var rangeText by rememberSaveable { mutableStateOf(existing?.rangeText.orEmpty()) }
    var detail by rememberSaveable { mutableStateOf(existing?.detail.orEmpty()) }
    var reportId by rememberSaveable { mutableStateOf(existing?.reportId.orEmpty()) }
    var reportName by rememberSaveable { mutableStateOf(existing?.reportName.orEmpty()) }
    var remove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(entryId) { vm.actionError.value = null }
    LaunchedEffect(selected) { if (selected != captured) onDismiss() }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp).testTag("lab-editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Heading(if (initial == null) t("Add a lab result", "जाँच का नतीजा जोड़ें") else t("Edit lab result", "जाँच का नतीजा बदलें"), 26)
            OutlinedTextField(name, { name = it; error = "" }, label = { Text(t("Test name on report", "रिपोर्ट पर जाँच का नाम")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (name.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Haemoglobin", "Red blood cells", "Platelets", "Creatinine", "Sodium").forEach { suggestion -> SuggestionChip(onClick = { name = suggestion }, label = { Text(suggestion) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it; error = "" }, label = { Text(t("Value", "मान")) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                OutlinedTextField(unit, { unit = it; error = "" }, label = { Text(t("Printed unit", "छपी इकाई")) }, modifier = Modifier.weight(1f), singleLine = true)
            }
            CareDateButton(due, t("Report date", "रिपोर्ट की तारीख़"), { due = it }, latest = LocalDate.now())
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Printed reference range (optional)", "छपी सामान्य सीमा (चाहें तो)"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(low, { low = it }, label = { Text(t("Lower", "निचली")) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(high, { high = it }, label = { Text(t("Upper", "ऊपरी")) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
                OutlinedTextField(rangeText, { rangeText = it }, label = { Text(t("Or copy the printed range", "या छपी सीमा लिखें")) }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                Text(t("Linked report", "जुड़ी रिपोर्ट"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = reportId.isBlank(), onClick = { reportId = ""; reportName = "" }, label = { Text(t("No report link", "रिपोर्ट नहीं जोड़ी")) }, modifier = Modifier.heightIn(min = 48.dp))
                    reports.filter { it.personId == captured }.forEach { report ->
                        FilterChip(selected = reportId == report.id, onClick = { reportId = report.id; reportName = report.name }, label = { Text(report.name) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                if (reports.none { it.personId == captured }) Text(t("Add report files in Documents to link them here.", "यहाँ जोड़ने के लिए कागज़ात में रिपोर्ट की फ़ाइलें डालें।"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(detail, { detail = it }, label = { Text(t("Report notes (optional)", "रिपोर्ट पर नोट (चाहें तो)")) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            }
            (error.ifBlank { saveError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = { runCatching {
                require(selected == captured && canEdit) { t("Choose the right person before saving.", "सहेजने से पहले सही व्यक्ति चुनें।") }
                val data = CareEntry(type = "lab", detail = detail.trim(), sender = existing?.sender ?: vm.cloud?.session?.value?.email.orEmpty(), value = value.trim(), unit = unit.trim(), rangeLow = low.trim(), rangeHigh = high.trim(), rangeText = rangeText.trim(), reportId = reportId, reportName = reportName)
                CareLabRules.validate(name, data, due)
                val encoded = CareEntryCodec.encode(data)
                if (initial == null) vm.saveItem("task", name, encoded, due, done = onDismiss, id = entryId)
                else vm.updateItem(initial.copy(title = name, detail = encoded, due = due), onDismiss)
            }.onFailure { error = it.message.orEmpty() } }, enabled = !busy && canEdit && name.isNotBlank() && value.isNotBlank() && unit.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save result", "नतीजा सहेजें")) }
            if (initial != null && canEdit) TextButton(onClick = { remove = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(t("Remove result", "नतीजा हटाएँ")) }
        }
    }
    if (remove && initial != null) AlertDialog(onDismissRequest = { remove = false }, title = { Text(t("Remove this result?", "यह नतीजा हटाएँ?")) }, text = { Text("${initial.title} · ${labDateText(initial, t)}") }, confirmButton = { TextButton(onClick = { vm.remove(initial); remove = false; onDismiss() }, enabled = !busy) { Text(t("Remove result", "नतीजा हटाएँ")) } }, dismissButton = { TextButton(onClick = { remove = false }) { Text(t("Keep", "रखें")) } })
}

/** "28 Oct": short enough to fit under every point of a lab chart. */
private fun shortLabDate(epoch: Long): String = java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(careDateFormat("d MMM", CareLanguageState.shown == CareLang.HI))
