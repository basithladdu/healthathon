package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@Composable fun CareDocumentReview(vm: CareModel, report: CareReport, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context, vm.storageNamespace) { CareDocumentReviewStore(context.applicationContext, vm.storageNamespace) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var value by remember(report.id) { mutableStateOf<ReviewedDocumentText?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by rememberSaveable(report.id) { mutableStateOf("") }
    var page by rememberSaveable(report.id) { mutableIntStateOf(1) }
    var checked by rememberSaveable(report.id) { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var factLine by rememberSaveable(report.id) { mutableIntStateOf(0) }
    var title by rememberSaveable(report.id) { mutableStateOf("") }
    var kind by rememberSaveable(report.id) { mutableStateOf("Record") }
    var dateText by rememberSaveable(report.id) { mutableStateOf(LocalDate.now().toString()) }
    var timeText by rememberSaveable(report.id) { mutableStateOf("") }
    var factChecked by rememberSaveable(report.id) { mutableStateOf(false) }
    val currentPage = value?.pages?.firstOrNull { it.page == page }

    fun read() {
        job?.cancel(); loading = true; error = ""; checked = false; factLine = 0
        job = scope.launch {
            try {
                val result = CareDocumentReader(context).read(vm.reportFiles.file(report), report) { done, total -> progress = done to total }
                withContext(Dispatchers.IO) { store.save(result) }; value = result; page = 1
                if (result.pages.all { it.originalText.isBlank() }) error = "No readable text found. Open the original and copy the words you can read."
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: "Could not read this document. The original is unchanged." }
            finally { loading = false }
        }
    }
    LaunchedEffect(report.id) {
        value = withContext(Dispatchers.IO) { runCatching { store.loadVerified(report, vm.reportFiles.file(report)) }.getOrNull() }
        if (value == null) read()
    }
    LaunchedEffect(selected) { if (selected != report.personId) { job?.cancel(); onClose() } }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    BackHandler { job?.cancel(); onClose() }

    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("document-review"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row { IconButton(onClick = { job?.cancel(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to documents") }; Column(Modifier.weight(1f)) { Heading("Review report", 26); Text(report.name) } } }
        item { OutlinedButton(onClick = { runCatching { open(context, vm.reportFiles.intent(report, false)) }.onFailure { error = "No app could open this original. Your file is unchanged." } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("View original") } }
        item { Text("Read text, correct any mistakes, then copy only the facts you checked. Handwriting and small print may not be readable.", style = MaterialTheme.typography.bodySmall) }
        if (loading) item { SurfaceCard { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Reading page ${progress.first} of ${progress.second.coerceAtLeast(1)}"); TextButton(onClick = { job?.cancel(); loading = false }) { Text("Cancel reading") } } }
        if (error.isNotBlank()) item { SurfaceCard(Peach) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = ::read, enabled = !loading) { Text("Read again") } } }
        value?.let { document ->
            if (document.pages.size < document.pageCount) item { Text("First ${document.pages.size} of ${document.pageCount} pages read. Open the original for the remaining pages.", color = MaterialTheme.colorScheme.error) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { document.pages.forEach { candidate -> FilterChip(selected = page == candidate.page, onClick = { page = candidate.page; factLine = 0; factChecked = false }, label = { Text("Page ${candidate.page}") }) } } }
            currentPage?.let { shown ->
                item { CareField(shown.reviewedText, { next ->
                    value = document.copy(pages = document.pages.map { if (it.page == page) it.copy(reviewedText = next.take(40_000)) else it }, reviewedAt = 0)
                    checked = false; factLine = 0; factChecked = false
                }, "Text to check", minLines = 8) }
                if (shown.originalText != shown.reviewedText) item { Text("Corrections are separate from the original reading. The original file is kept.", style = MaterialTheme.typography.bodySmall) }
                item { CheckLine(checked || document.reviewedAt > 0, { checked = it }, "I compared these pages and any corrections with the original.") }
                item { Button(onClick = { scope.launch {
                    try { val confirmed = DocumentEvidenceRules.confirm(requireNotNull(value), checked, System.currentTimeMillis()); withContext(Dispatchers.IO) { store.save(confirmed) }; value = confirmed; checked = false; error = ""; vm.message.value = "Reviewed text saved" }
                    catch (failure: Exception) { error = failure.message ?: "Could not save the reviewed text. Keep this page open and try again." }
                } }, enabled = checked && !loading && canEdit, modifier = Modifier.fillMaxWidth()) { Text(if (document.reviewedAt > 0) "Save review again" else "Save reviewed text") } }
                if (document.reviewedAt > 0 && canEdit) {
                    item { SectionTitle("Copy a checked fact") }
                    item { Text("Choose the exact line to keep with a medicine, test or follow-up. A copied record does not confirm treatment happened.", style = MaterialTheme.typography.bodySmall) }
                    shown.reviewedText.split('\n').forEachIndexed { index, line -> if (line.isNotBlank() && line.length <= 2000) item("line-$page-$index") {
                        FilterChip(selected = factLine == index + 1, onClick = { factLine = index + 1; title = line.take(160); factChecked = false }, label = { Text("${index + 1}. $line") }, modifier = Modifier.fillMaxWidth())
                    } }
                    if (factLine > 0) {
                        item { CareField(title, { title = it.take(160); factChecked = false }, "Record name", singleLine = true) }
                        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Record", "Medicine", "Follow-up", "Test").forEach { option -> FilterChip(selected = kind == option, onClick = { kind = option; factChecked = false }, label = { Text(option) }) } } }
                        if (kind in listOf("Follow-up", "Test")) {
                            item { CareField(dateText, { dateText = it; factChecked = false }, "Date · YYYY-MM-DD", singleLine = true) }
                            item { CareField(timeText, { timeText = it; factChecked = false }, "Time · HH:mm", singleLine = true) }
                        }
                        item { CheckLine(factChecked, { factChecked = it }, if (kind == "Medicine") "I am copying an existing prescription, including its exact dose and directions. I am not changing treatment." else "I checked the chosen line, name and any date against the original.") }
                        item { Button(onClick = {
                            try {
                                require(title.trim().isNotBlank()) { "Add a record name." }
                                val current = requireNotNull(value); val quote = current.pages.single { it.page == page }.reviewedText.split('\n')[factLine - 1]
                                val source = DocumentEvidenceRules.source(current, page, factLine, quote)
                                val detail = DocumentEvidenceRules.copiedDetail(source, report.name)
                                val due = if (kind in listOf("Follow-up", "Test")) LocalDateTime.of(LocalDate.parse(dateText), LocalTime.parse(timeText)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0
                                require(factChecked) { "Check this copied record first." }
                                val targetKind = when(kind) { "Medicine" -> "medicine"; "Follow-up", "Test" -> "visit"; else -> "journal" }
                                val savedDetail = if (targetKind == "visit") {
                                    require(detail.length <= 1600) { "Choose a shorter line so the complete source stays with this calendar entry." }
                                    CareEntryCodec.encode(CareEntry(type = "schedule", detail = detail, category = if (kind == "Test") "Test" else "Follow-up", timed = true, reportId = report.id, reportName = report.name))
                                } else detail
                                vm.saveItem(targetKind, title, savedDetail, due, done = { factLine = 0; factChecked = false; vm.message.value = if (due > 0) "Copied to Calendar. Check Reminders to enable alerts." else "Checked fact copied" })
                                error = ""
                            } catch (failure: Exception) { error = failure.message ?: "Check the date and exact time." }
                        }, enabled = factChecked && !busy, modifier = Modifier.fillMaxWidth().testTag("document-copy-fact")) { Text(if (busy) "Saving…" else if (kind in listOf("Follow-up", "Test")) "Copy to Calendar" else "Copy checked fact") } }
                    }
                }
            }
        }
    }
}
