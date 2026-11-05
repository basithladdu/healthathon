package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable fun CareDocumentReview(vm: CareModel, report: CareReport, startPage: Int = 1, onClose: () -> Unit) {
    val context = LocalContext.current; val t = careText()
    val scope = rememberCoroutineScope()
    val store = remember(context, vm.storageNamespace) { CareDocumentReviewStore(context.applicationContext, vm.storageNamespace) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var value by remember(report.id) { mutableStateOf<ReviewedDocumentText?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by rememberSaveable(report.id) { mutableStateOf("") }
    var page by rememberSaveable(report.id) { mutableIntStateOf(startPage.coerceAtLeast(1)) }
    var checked by rememberSaveable(report.id) { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var factLine by rememberSaveable(report.id) { mutableIntStateOf(0) }
    var title by rememberSaveable(report.id) { mutableStateOf("") }
    var kind by rememberSaveable(report.id) { mutableStateOf("Record") }
    var dateText by rememberSaveable(report.id) { mutableStateOf(LocalDate.now().toString()) }
    var timeText by rememberSaveable(report.id) { mutableStateOf("") }
    var factChecked by rememberSaveable(report.id) { mutableStateOf(false) }
    var labValue by rememberSaveable(report.id) { mutableStateOf("") }
    var labUnit by rememberSaveable(report.id) { mutableStateOf("") }
    var labLow by rememberSaveable(report.id) { mutableStateOf("") }
    var labHigh by rememberSaveable(report.id) { mutableStateOf("") }
    fun suggestLab(line: String) {
        val found = LabLineParser.parse(line)
        title = found?.name ?: line.take(160); labValue = found?.value.orEmpty(); labUnit = found?.unit.orEmpty(); labLow = found?.low.orEmpty(); labHigh = found?.high.orEmpty()
    }
    val currentPage = value?.pages?.firstOrNull { it.page == page }

    fun read() {
        job?.cancel(); loading = true; error = ""; checked = false; factLine = 0
        job = scope.launch {
            try {
                val result = CareDocumentReader(context).read(vm.reportFiles.file(report), report) { done, total -> progress = done to total }
                withContext(Dispatchers.IO) { store.save(result) }; value = result; page = 1
                if (result.pages.all { it.originalText.isBlank() }) error = t("No readable text found.", "पढ़ने लायक लिखाई नहीं मिली।")
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: t("Couldn't read this document.", "यह कागज़ पढ़ा नहीं जा सका।") }
            finally { loading = false }
        }
    }
    LaunchedEffect(report.id) {
        value = withContext(Dispatchers.IO) { runCatching { store.loadVerified(report, vm.reportFiles.file(report)) }.getOrNull() }
        value?.let { v -> if (v.pages.none { it.page == page }) page = 1 }
        if (value == null) read()
    }
    LaunchedEffect(selected) { if (selected != report.personId) { job?.cancel(); onClose() } }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    BackHandler { job?.cancel(); onClose() }

    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("document-review"), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { job?.cancel(); onClose() }, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to documents", "कागज़ात पर वापस")) }; Column(Modifier.weight(1f)) { Heading(t("Review report", "रिपोर्ट जाँचें"), 26); Text(report.name, fontSize = 14.sp, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
        item { OutlinedButton(onClick = { runCatching { open(context, vm.reportFiles.intent(report, false)) }.onFailure { error = t("Couldn't open this file.", "यह फ़ाइल नहीं खुल सकी।") } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("View original", "असली फ़ाइल देखें")) } }
        if (loading) item { SurfaceCard { LinearProgressIndicator(Modifier.fillMaxWidth(), color = DoctorAccent, trackColor = DoctorHairline); Spacer(Modifier.height(8.dp)); Text(t("Reading page ${progress.first} of ${progress.second.coerceAtLeast(1)}", "पेज ${progress.first} / ${progress.second.coerceAtLeast(1)} पढ़ रहे हैं")); TextButton(onClick = { job?.cancel(); loading = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel reading", "पढ़ना रोकें")) } } }
        if (error.isNotBlank()) item { SurfaceCard { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.announce()); TextButton(onClick = ::read, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Read again", "फिर पढ़ें"), color = RustText) } } }
        value?.let { document ->
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { document.pages.forEach { candidate -> FilterChip(selected = page == candidate.page, onClick = { page = candidate.page; factLine = 0; factChecked = false }, label = { Text(t("Page ${candidate.page}", "पेज ${candidate.page}")) }, shape = RoundedCornerShape(50)) } } }
            currentPage?.let { shown ->
                item { CareField(shown.reviewedText, { next ->
                    value = document.copy(pages = document.pages.map { if (it.page == page) it.copy(reviewedText = next.take(40_000)) else it }, reviewedAt = 0)
                    checked = false; factLine = 0; factChecked = false
                }, t("Text to check", "जाँचने के लिए लिखाई"), minLines = 8) }
                item { CheckLine(checked || document.reviewedAt > 0, { checked = it }, t("I checked this against the original.", "मैंने इसे असली फ़ाइल से मिला लिया है।")) }
                item { val saveReview: () -> Unit = { scope.launch {
                    try { val confirmed = DocumentEvidenceRules.confirm(requireNotNull(value), checked, System.currentTimeMillis()); withContext(Dispatchers.IO) { store.save(confirmed) }; value = confirmed; checked = false; error = ""; vm.message.value = t("Reviewed text saved", "जाँची गई लिखाई सहेज ली गई") }
                    catch (failure: Exception) { error = failure.message ?: t("Something went wrong. Try again.", "कुछ गड़बड़ हुई। फिर कोशिश करें।") }
                } }
                    if (document.reviewedAt > 0) OutlinedButton(onClick = saveReview, enabled = checked && !loading && canEdit, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Save review again", "जाँच फिर सहेजें")) }
                    else Button(onClick = saveReview, enabled = checked && !loading && canEdit, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Save reviewed text", "जाँची गई लिखाई सहेजें"), fontWeight = FontWeight.SemiBold) } }
                if (document.reviewedAt > 0 && canEdit) {
                    item { SectionTitle(t("Copy a checked fact", "जाँची हुई बात कॉपी करें")) }
                    shown.reviewedText.split('\n').forEachIndexed { index, line -> if (line.isNotBlank() && line.length <= 2000) item("line-$page-$index") {
                        FilterChip(selected = factLine == index + 1, onClick = { factLine = index + 1; if (kind == "Lab result") suggestLab(line) else title = line.take(160); factChecked = false }, label = { Text("${index + 1}. $line", Modifier.padding(vertical = 8.dp)) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    } }
                    if (factLine > 0) {
                        item { CareField(title, { title = it.take(160); factChecked = false }, if (kind == "Lab result") t("Test name on report", "रिपोर्ट पर जाँच का नाम") else t("Record name", "रिकॉर्ड का नाम"), singleLine = true) }
                        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Record", "Medicine", "Follow-up", "Test", "Lab result").forEach { option -> FilterChip(selected = kind == option, onClick = {
                            if (option == "Lab result" && kind != option) currentPage?.reviewedText?.split('\n')?.getOrNull(factLine - 1)?.let(::suggestLab)
                            kind = option; factChecked = false
                        }, label = { Text(when (option) { "Medicine" -> t("Medicine", "दवा"); "Follow-up" -> t("Follow-up", "अगली मुलाक़ात"); "Test" -> t("Test", "जाँच"); "Lab result" -> t("Lab result", "लैब का नतीजा"); else -> t("Record", "रिकॉर्ड") }) }, shape = RoundedCornerShape(50), modifier = Modifier.testTag("document-kind-$option")) } } }
                        if (kind == "Lab result") {
                            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { CareField(labValue, { labValue = it.take(40); factChecked = false }, t("Value", "नतीजा"), singleLine = true) }
                                Box(Modifier.weight(1f)) { CareField(labUnit, { labUnit = it.take(40); factChecked = false }, t("Printed unit", "छपी इकाई"), singleLine = true) }
                            } }
                            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { CareField(labLow, { labLow = it.take(40); factChecked = false }, t("Printed lower (optional)", "छपी निचली सीमा (ज़रूरी नहीं)"), singleLine = true) }
                                Box(Modifier.weight(1f)) { CareField(labHigh, { labHigh = it.take(40); factChecked = false }, t("Printed upper (optional)", "छपी ऊपरी सीमा (ज़रूरी नहीं)"), singleLine = true) }
                            } }
                            item { CareField(dateText, { dateText = it; factChecked = false }, t("Report date · YYYY-MM-DD", "रिपोर्ट की तारीख़ · YYYY-MM-DD"), singleLine = true) }
                        }
                        if (kind in listOf("Follow-up", "Test")) {
                            item { CareField(dateText, { dateText = it; factChecked = false }, t("Date · YYYY-MM-DD", "तारीख़ · YYYY-MM-DD"), singleLine = true) }
                            item { CareField(timeText, { timeText = it; factChecked = false }, t("Time · HH:mm", "समय · HH:mm"), singleLine = true) }
                        }
                        item { CheckLine(factChecked, { factChecked = it }, if (kind == "Lab result") t("I checked this against the original.", "मैंने इसे असली फ़ाइल से मिला लिया है।") else if (kind == "Medicine") t("I am copying an existing prescription, including its exact dose and directions. I am not changing treatment.", "मैं पहले से लिखा पर्चा, उसकी सही ख़ुराक और तरीक़े के साथ, कॉपी कर रहा/रही हूँ। मैं इलाज नहीं बदल रहा/रही।") else t("I checked this against the original.", "मैंने इसे असली फ़ाइल से मिला लिया है।")) }
                        item { Button(onClick = {
                            try {
                                require(title.trim().isNotBlank()) { t("Add a record name.", "रिकॉर्ड का नाम लिखें।") }
                                val current = requireNotNull(value); val quote = current.pages.single { it.page == page }.reviewedText.split('\n')[factLine - 1]
                                val source = DocumentEvidenceRules.source(current, page, factLine, quote)
                                val detail = DocumentEvidenceRules.copiedDetail(source, report.name)
                                if (kind == "Lab result") {
                                    require(factChecked) { t("Check this result first.", "पहले यह नतीजा जाँचें।") }
                                    require(detail.length <= 800) { t("Choose a shorter line.", "छोटी पंक्ति चुनें।") }
                                    val day = runCatching { LocalDate.parse(dateText.trim()) }.getOrElse { throw IllegalArgumentException(t("Enter the report date as YYYY-MM-DD.", "रिपोर्ट की तारीख़ YYYY-MM-DD में लिखें।")) }
                                    val lab = CareEntry(type = "lab", detail = detail, sender = vm.cloud?.session?.value?.email.orEmpty(), value = labValue.trim(), unit = labUnit.trim(), rangeLow = labLow.trim(), rangeHigh = labHigh.trim(), reportId = report.id, reportName = report.name)
                                    CareLabRules.validate(title, lab, careEpoch(day))
                                    vm.saveItem("task", title.trim(), CareEntryCodec.encode(lab), careEpoch(day), done = { factLine = 0; factChecked = false; vm.message.value = t("Added to Lab history", "लैब इतिहास में जुड़ गया") })
                                    error = ""; return@Button
                                }
                                val due = if (kind in listOf("Follow-up", "Test")) LocalDateTime.of(LocalDate.parse(dateText), LocalTime.parse(timeText)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0
                                require(factChecked) { t("Check this copied record first.", "पहले इस कॉपी किए रिकॉर्ड को जाँचें।") }
                                val targetKind = when(kind) { "Medicine" -> "medicine"; "Follow-up", "Test" -> "visit"; else -> "journal" }
                                val savedDetail = if (targetKind == "visit") {
                                    require(detail.length <= 1600) { t("Choose a shorter line.", "छोटी पंक्ति चुनें।") }
                                    CareEntryCodec.encode(CareEntry(type = "schedule", detail = detail, category = if (kind == "Test") "Test" else "Follow-up", timed = true, reportId = report.id, reportName = report.name))
                                } else detail
                                vm.saveItem(targetKind, title, savedDetail, due, done = { factLine = 0; factChecked = false; vm.message.value = if (due > 0) t("Added to Calendar", "कैलेंडर में जुड़ गया") else t("Checked fact copied", "जाँची हुई बात कॉपी हो गई") })
                                error = ""
                            } catch (failure: Exception) { error = failure.message ?: t("Check the date and exact time.", "तारीख़ और सही समय जाँचें।") }
                        }, enabled = factChecked && !busy, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("document-copy-fact")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else if (kind == "Lab result") t("Add to Lab history", "लैब इतिहास में जोड़ें") else if (kind in listOf("Follow-up", "Test")) t("Copy to Calendar", "कैलेंडर में कॉपी करें") else t("Copy checked fact", "जाँची हुई बात कॉपी करें")) } }
                    }
                }
            }
        }
    }
}
