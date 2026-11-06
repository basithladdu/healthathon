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
    LazyColumn(Modifier.fillMaxSize().testTag("lab-history"), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Heading(t("Lab history", "जाँच रिपोर्टें")); Spacer(Modifier.weight(1f)); if (canEdit) FilledTonalIconButton(onClick = { adding = true }, modifier = Modifier.size(48.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = DoctorAccent, contentColor = Paper)) { Icon(Icons.Default.Add, t("Add a lab result", "जाँच का नतीजा जोड़ें")) } } }
        item { OutlinedTextField(query, { query = it }, placeholder = { Text(t("Find a test", "जाँच खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, t("Clear search", "खोज मिटाएँ")) } }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) }
        if (groups.isEmpty()) item { SurfaceCard { Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Mint) { Icon(Icons.Default.Science, null, Modifier.padding(12.dp).size(28.dp), tint = DoctorAccent) }; Spacer(Modifier.height(12.dp)); Text(if (query.isBlank()) t("No results yet", "अभी कोई नतीजा नहीं") else t("No matching results", "कोई नतीजा नहीं मिला"), fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk); if (query.isBlank()) { if (canEdit) TextButton(onClick = { adding = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Add a result", "नतीजा जोड़ें")) } } else TextButton(onClick = { query = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Clear search", "खोज मिटाएँ")) } } }
        items(groups, key = { values -> "${values.first().first.title.lowercase()}:${values.first().second.unit}" }) { values -> Box(Modifier.animateItem()) { LabHistoryCard(vm, values, treatmentDays, onEdit = { editing = it }) } }
        // Results first; treatment days, which only mark the charts, come after them.
        item(key = "treatment-days") { SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = SaharaApricot) { Icon(Icons.Default.Event, null, Modifier.padding(8.dp).size(18.dp), tint = DoctorAccent) }
                Spacer(Modifier.width(12.dp))
                Text(t("Treatment days", "इलाज के दिन"), Modifier.weight(1f).a11yHeading(), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, fontSize = 18.sp, color = DoctorCardInk)
            }
            treatmentDays.take(6).forEach { day -> Row(Modifier.fillMaxWidth().then(if (canEdit) Modifier.clickable { editingDay = day } else Modifier).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${date(day.item.due)} · ${TreatmentDayRules.label(day.category)}", Modifier.weight(1f), color = if (day.category == "Chemotherapy") DoctorStatusWarn else DoctorCardInk)
                if (canEdit) Icon(Icons.Default.Edit, t("Edit ${day.category} day ${date(day.item.due)}", "${date(day.item.due)} का दिन बदलें"), Modifier.size(18.dp), tint = DoctorCardSub)
            } }
            if (canEdit) TextButton(onClick = { addingDay = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("add-treatment-day")) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add a treatment day", "इलाज का दिन जोड़ें")) }
        } }
    }
    if (adding) CareLabEditor(vm) { adding = false }
    editing?.let { item -> CareLabEditor(vm, item) { editing = null } }
    if (addingDay) TreatmentDayEditor(vm) { addingDay = false }
    editingDay?.let { day -> TreatmentDayEditor(vm, day) { editingDay = null } }
}

@Composable private fun LabHistoryCard(vm: CareModel, values: List<Pair<CareItem, CareEntry>>, treatmentDays: List<TreatmentDay>, onEdit: (CareItem) -> Unit) { val t = careText();
    val latest = values.first()
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(latest.first.title, Modifier.a11yHeading(), fontWeight = FontWeight.Medium, fontSize = 18.sp, fontFamily = SaharaTitleFont, color = DoctorCardInk)
                Text("${latest.second.value} ${latest.second.unit}", color = Rust, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(labDateText(latest.first, t), fontSize = 14.sp, color = DoctorCardSub)
            }
            Spacer(Modifier.width(8.dp))
            LabPill(if (values.size == 1) t("1 result", "1 नतीजा") else t("${values.size} results", "${values.size} नतीजे"), SaharaSoftBlue, DoctorCardInk)
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
    val all = values.reversed().mapNotNull { (item, entry) -> entry.value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { labEpoch(item).toDouble() to it } }.sortedBy { it.first }
    if (all.size < 2) return
    val now = System.currentTimeMillis()
    val chartPoints = all.map { ChartPoint(it.first, it.second) }
    var rangeName by rememberSaveable(values.first().first.id) { mutableStateOf(ChartRange.ALL.name) }
    val range = ChartRange.valueOf(rangeName).takeIf { it in ChartRangeRules.choices(chartPoints, now) } ?: ChartRange.ALL
    // What the chosen range shows: the y axis, the change line and the treatment-day list follow it.
    val points = ChartRangeRules.filter(chartPoints, range, now).map { it.x to it.y!! }
    val unit = values.first().second.unit
    val (refLow, refHigh) = labRange(values.first().second)
    val firstTime = points.first().first
    val lastTime = points.last().first
    val spanStart = ChartRangeRules.start(range, now)?.toLong() ?: firstTime.toLong()
    val markers = TreatmentDayRules.within(treatmentDays, spanStart, maxOf(lastTime.toLong(), if (range == ChartRange.ALL) lastTime.toLong() else now))
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
    val summary = points.joinToString("; ") { (time, v) -> "${date(time.toLong())}: ${labNum(v)} $unit" }
    val rangeText = if (bands.isEmpty()) "" else t(" Printed range ${labNum(refLow ?: yMin)} to ${labNum(refHigh ?: yMax)} $unit.", " छपी सीमा ${labNum(refLow ?: yMin)} से ${labNum(refHigh ?: yMax)} $unit।")
    Text(t("Recorded values ($unit)", "दर्ज मान ($unit)"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.padding(top = 12.dp))
    // Treatment days are dashed lines labelled "Chemo" or "Radiotherapy" at the top, so they can be read against the values.
    CareTimeLineChart(
        points = chartPoints, yMin = yMin, yMax = yMax, yTicks = ticks, yText = { labNum(it) },
        latestText = { labNum(it) }, tag = "lab-trend", height = 200.dp, bands = bands, pointText = { labNum(it) },
        markers = treatmentDays.map { ChartMarker(careEpoch(careDay(it.item.due)).toDouble(), if (it.category == "Chemotherapy") DoctorStatusWarn else Ink, TreatmentDayRules.label(it.category)) },
        description = t("Line chart of ${points.size} recorded values. $summary.$rangeText", "${points.size} दर्ज मानों का रेखा चार्ट। $summary।$rangeText"),
        range = range, onRangeChange = { rangeName = it.name }, now = now,
    )
    // Legend and change: what the band and dashed lines mean, and how far the value has moved since the first result.
    FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (bands.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 10.dp).background(Mint, RoundedCornerShape(3.dp))); Spacer(Modifier.width(5.dp))
            Text(t("Printed range ${labNum(refLow ?: yMin)}–${labNum(refHigh ?: yMax)} $unit", "छपी सीमा ${labNum(refLow ?: yMin)}–${labNum(refHigh ?: yMax)} $unit"), fontSize = 14.sp, color = DoctorCardInk)
        }
        if (markers.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("┆", color = DoctorStatusWarn, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp))
            Text(t("Treatment day", "इलाज का दिन"), fontSize = 14.sp, color = DoctorCardInk)
        }
    }
    if (points.size > 1) {
        val change = points.last().second - points.first().second
        val arrow = if (change > 0) "↑" else if (change < 0) "↓" else "→"
        val since = shortLabDate(firstTime.toLong())
        Text(t("$arrow ${labNum(kotlin.math.abs(change))} $unit since $since · ${points.size} results", "$arrow ${labNum(kotlin.math.abs(change))} $unit, $since से · ${points.size} नतीजे"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.padding(top = 4.dp))
    }
    TreatmentDayRules.categories.forEach { category -> markers.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { shown ->
        Text("┆ ${TreatmentDayRules.label(category)}: " + shown.joinToString(", ") { date(it.item.due) }, fontSize = 14.sp, color = if (category == "Chemotherapy") DoctorStatusWarn else DoctorCardInk)
    } }
}

@Composable private fun LabResultRow(vm: CareModel, item: CareItem, data: CareEntry, showDate: Boolean, onEdit: () -> Unit) { val t = careText();
    val reports by vm.reports.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val report = reports.firstOrNull { it.id == data.reportId && it.personId == item.personId }
    var openFailed by remember { mutableStateOf(false) }
    if (openFailed) AlertDialog(onDismissRequest = { openFailed = false }, text = { Text(t("Couldn't open this report.", "यह रिपोर्ट नहीं खुल सकी।")) }, confirmButton = { TextButton(onClick = { openFailed = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("OK", "ठीक है")) } })
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp), color = DoctorHairline)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (showDate) { Text(labDateText(item, t), fontWeight = FontWeight.SemiBold, color = DoctorCardInk); Text("${data.value} ${data.unit}", color = Rust, style = MaterialTheme.typography.titleMedium) }
            val printedRange = data.rangeText.ifBlank { when { data.rangeLow.isNotBlank() && data.rangeHigh.isNotBlank() -> "${data.rangeLow}–${data.rangeHigh} ${data.unit}"; data.rangeLow.isNotBlank() -> t("From ${data.rangeLow} ${data.unit}", "कम से कम ${data.rangeLow} ${data.unit}"); data.rangeHigh.isNotBlank() -> t("Up to ${data.rangeHigh} ${data.unit}", "ज़्यादा से ज़्यादा ${data.rangeHigh} ${data.unit}"); else -> "" } }
            if (printedRange.isNotBlank()) Text(t("Printed range: $printedRange", "छपी सीमा: $printedRange"), fontSize = 14.sp, color = DoctorCardSub)
            CareLabRules.printedFlag(data)?.let { flag -> val within = flag.startsWith("Within"); LabPill(t(flag, when { flag.startsWith("Low") -> "कम · छपी सीमा से नीचे"; flag.startsWith("High") -> "ज़्यादा · छपी सीमा से ऊपर"; else -> "छपी सीमा के अंदर" }), if (within) DoctorStatusGoodBg else DoctorStatusWarnBg, if (within) DoctorStatusGood else DoctorStatusWarn, Modifier.padding(vertical = 4.dp)) }
            if (data.detail.isNotBlank()) Text(data.detail, fontSize = 14.sp, color = DoctorCardSub)
            if (data.reportName.isNotBlank()) Text(data.reportName, fontSize = 14.sp, color = DoctorCardSub)
            if (data.reportId.isNotBlank() && report == null) Text(t("Report file unavailable here", "रिपोर्ट की फ़ाइल यहाँ नहीं है"), fontSize = 14.sp, color = DoctorCardSub)
        }
        if (canEdit) IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.Edit, t("Edit ${item.title} result from ${labDateText(item, t)}", "${labDateText(item, t)} का ${item.title} नतीजा बदलें")) }
    }
    if (report != null) TextButton(onClick = { runCatching { vm.reportFiles.intent(report, false) }.onSuccess { open(context, it) }.onFailure { openFailed = true } }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(t("Open report", "रिपोर्ट खोलें")) }
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
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("lab-editor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(if (initial == null) t("Add a lab result", "जाँच का नतीजा जोड़ें") else t("Edit lab result", "जाँच का नतीजा बदलें"), 26)
            OutlinedTextField(name, { name = it; error = "" }, label = { Text(t("Test name on report", "रिपोर्ट पर जाँच का नाम")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
            if (name.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Haemoglobin", "Red blood cells", "Platelets", "Creatinine", "Sodium").forEach { suggestion -> SuggestionChip(onClick = { name = suggestion }, label = { Text(suggestion) }, shape = RoundedCornerShape(50)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it; error = "" }, label = { Text(t("Value", "मान")) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                OutlinedTextField(unit, { unit = it; error = "" }, label = { Text(t("Printed unit", "छपी इकाई")) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp))
            }
            CareDateButton(due, t("Report date", "रिपोर्ट की तारीख़"), { due = it }, latest = LocalDate.now())
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Printed range", "छपी सीमा"), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(low, { low = it }, label = { Text(t("Lower", "निचली")) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(high, { high = it }, label = { Text(t("Upper", "ऊपरी")) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
                OutlinedTextField(rangeText, { rangeText = it }, label = { Text(t("Or copy the printed range", "या छपी सीमा लिखें")) }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(14.dp))
                Text(t("Linked report", "जुड़ी रिपोर्ट"), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = reportId.isBlank(), onClick = { reportId = ""; reportName = "" }, label = { Text(t("No report link", "रिपोर्ट नहीं जोड़ी")) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp))
                    reports.filter { it.personId == captured }.forEach { report ->
                        FilterChip(selected = reportId == report.id, onClick = { reportId = report.id; reportName = report.name }, label = { Text(report.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                OutlinedTextField(detail, { detail = it }, label = { Text(t("Report notes (optional)", "रिपोर्ट पर नोट (चाहें तो)")) }, modifier = Modifier.fillMaxWidth(), maxLines = 3, shape = RoundedCornerShape(14.dp))
            }
            (error.ifBlank { saveError.orEmpty() }).takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = { runCatching {
                require(selected == captured && canEdit) { t("Choose the right person before saving.", "सहेजने से पहले सही व्यक्ति चुनें।") }
                val data = CareEntry(type = "lab", detail = detail.trim(), sender = existing?.sender ?: vm.cloud?.session?.value?.email.orEmpty(), value = value.trim(), unit = unit.trim(), rangeLow = low.trim(), rangeHigh = high.trim(), rangeText = rangeText.trim(), reportId = reportId, reportName = reportName)
                CareLabRules.validate(name, data, due)
                val encoded = CareEntryCodec.encode(data)
                if (initial == null) vm.saveItem("task", name, encoded, due, done = onDismiss, id = entryId)
                else vm.updateItem(initial.copy(title = name, detail = encoded, due = due), onDismiss)
            }.onFailure { error = it.message.orEmpty() } }, enabled = !busy && canEdit && name.isNotBlank() && value.isNotBlank() && unit.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent)) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save result", "नतीजा सहेजें"), fontWeight = FontWeight.SemiBold) }
            if (initial != null && canEdit) TextButton(onClick = { remove = true }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Remove result", "नतीजा हटाएँ")) }
        }
    }
    if (remove && initial != null) AlertDialog(onDismissRequest = { remove = false }, title = { Text(t("Remove this result?", "यह नतीजा हटाएँ?")) }, text = { Text("${initial.title} · ${labDateText(initial, t)}") }, confirmButton = { TextButton(onClick = { vm.remove(initial); remove = false; onDismiss() }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(t("Remove result", "नतीजा हटाएँ")) } }, dismissButton = { TextButton(onClick = { remove = false }) { Text(t("Keep", "रखें")) } })
}

/** "28 Oct": short enough to fit under every point of a lab chart. */
private fun shortLabDate(epoch: Long): String = java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(careDateFormat("d MMM", CareLanguageState.shown == CareLang.HI))

/** A small fully rounded status pill: a count or a printed-range flag. */
@Composable private fun LabPill(text: String, tint: androidx.compose.ui.graphics.Color, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = tint, contentColor = color, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
