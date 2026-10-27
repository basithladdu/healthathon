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
                if (result.pages.all { it.originalText.isBlank() }) error = t("No readable text found. Open the original and copy the words you can read.", "पढ़ने लायक लिखाई नहीं मिली। असली फ़ाइल खोलें और जो शब्द पढ़ सकें, उन्हें लिख लें।")
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: t("Could not read this document. The original is unchanged.", "यह कागज़ पढ़ा नहीं जा सका। असली फ़ाइल वैसी ही है।") }
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
        item { Row { IconButton(onClick = { job?.cancel(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to documents", "कागज़ात पर वापस")) }; Column(Modifier.weight(1f)) { Heading(t("Review report", "रिपोर्ट जाँचें"), 26); Text(report.name) } } }
        item { OutlinedButton(onClick = { runCatching { open(context, vm.reportFiles.intent(report, false)) }.onFailure { error = t("No app could open this original. Your file is unchanged.", "कोई ऐप यह फ़ाइल नहीं खोल सका। आपकी फ़ाइल वैसी ही है।") } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(t("View original", "असली फ़ाइल देखें")) } }
        item { Text(t("Read text, correct any mistakes, then copy only the facts you checked. Handwriting and small print may not be readable.", "लिखाई पढ़ें, ग़लतियाँ ठीक करें, फिर सिर्फ़ जाँची हुई बातें कॉपी करें। हाथ की लिखाई और बारीक छपाई शायद न पढ़ी जाए।"), style = MaterialTheme.typography.bodySmall) }
        if (loading) item { SurfaceCard { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(t("Reading page ${progress.first} of ${progress.second.coerceAtLeast(1)}", "पेज ${progress.first} / ${progress.second.coerceAtLeast(1)} पढ़ रहे हैं")); TextButton(onClick = { job?.cancel(); loading = false }) { Text(t("Cancel reading", "पढ़ना रोकें")) } } }
        if (error.isNotBlank()) item { SurfaceCard(Peach) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = ::read, enabled = !loading) { Text(t("Read again", "फिर पढ़ें")) } } }
        value?.let { document ->
            if (document.pages.size < document.pageCount) item { Text(t("First ${document.pages.size} of ${document.pageCount} pages read. Open the original for the remaining pages.", "${document.pageCount} में से पहले ${document.pages.size} पेज पढ़े गए। बाक़ी पेजों के लिए असली फ़ाइल खोलें।"), color = MaterialTheme.colorScheme.error) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { document.pages.forEach { candidate -> FilterChip(selected = page == candidate.page, onClick = { page = candidate.page; factLine = 0; factChecked = false }, label = { Text(t("Page ${candidate.page}", "पेज ${candidate.page}")) }) } } }
            currentPage?.let { shown ->
                item { CareField(shown.reviewedText, { next ->
                    value = document.copy(pages = document.pages.map { if (it.page == page) it.copy(reviewedText = next.take(40_000)) else it }, reviewedAt = 0)
                    checked = false; factLine = 0; factChecked = false
                }, t("Text to check", "जाँचने के लिए लिखाई"), minLines = 8) }
                if (shown.originalText != shown.reviewedText) item { Text(t("Corrections are separate from the original reading. The original file is kept.", "सुधार अलग रखे जाते हैं। असली फ़ाइल वैसी ही रहती है।"), style = MaterialTheme.typography.bodySmall) }
                item { CheckLine(checked || document.reviewedAt > 0, { checked = it }, t("I compared these pages and any corrections with the original.", "मैंने इन पेजों और सुधारों को असली फ़ाइल से मिला लिया है।")) }
                item { Button(onClick = { scope.launch {
                    try { val confirmed = DocumentEvidenceRules.confirm(requireNotNull(value), checked, System.currentTimeMillis()); withContext(Dispatchers.IO) { store.save(confirmed) }; value = confirmed; checked = false; error = ""; vm.message.value = t("Reviewed text saved", "जाँची गई लिखाई सहेज ली गई") }
                    catch (failure: Exception) { error = failure.message ?: t("Could not save the reviewed text. Keep this page open and try again.", "जाँची गई लिखाई सहेजी नहीं जा सकी। यह पेज खुला रखें और फिर कोशिश करें।") }
                } }, enabled = checked && !loading && canEdit, modifier = Modifier.fillMaxWidth()) { Text(if (document.reviewedAt > 0) t("Save review again", "जाँच फिर सहेजें") else t("Save reviewed text", "जाँची गई लिखाई सहेजें")) } }
                if (document.reviewedAt > 0 && canEdit) {
                    item { SectionTitle(t("Copy a checked fact", "जाँची हुई बात कॉपी करें")) }
                    item { Text(t("Choose the exact line to keep with a medicine, test or follow-up. A copied record does not confirm treatment happened.", "दवा, जाँच या अगली मुलाक़ात के लिए सही पंक्ति चुनें। कॉपी करने से यह पक्का नहीं होता कि इलाज हुआ।"), style = MaterialTheme.typography.bodySmall) }
                    shown.reviewedText.split('\n').forEachIndexed { index, line -> if (line.isNotBlank() && line.length <= 2000) item("line-$page-$index") {
                        FilterChip(selected = factLine == index + 1, onClick = { factLine = index + 1; title = line.take(160); factChecked = false }, label = { Text("${index + 1}. $line") }, modifier = Modifier.fillMaxWidth())
                    } }
                    if (factLine > 0) {
                        item { CareField(title, { title = it.take(160); factChecked = false }, t("Record name", "रिकॉर्ड का नाम"), singleLine = true) }
                        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Record", "Medicine", "Follow-up", "Test").forEach { option -> FilterChip(selected = kind == option, onClick = { kind = option; factChecked = false }, label = { Text(when (option) { "Medicine" -> t("Medicine", "दवा"); "Follow-up" -> t("Follow-up", "अगली मुलाक़ात"); "Test" -> t("Test", "जाँच"); else -> t("Record", "रिकॉर्ड") }) }) } } }
                        if (kind in listOf("Follow-up", "Test")) {
                            item { CareField(dateText, { dateText = it; factChecked = false }, t("Date · YYYY-MM-DD", "तारीख़ · YYYY-MM-DD"), singleLine = true) }
                            item { CareField(timeText, { timeText = it; factChecked = false }, t("Time · HH:mm", "समय · HH:mm"), singleLine = true) }
                        }
                        item { CheckLine(factChecked, { factChecked = it }, if (kind == "Medicine") t("I am copying an existing prescription, including its exact dose and directions. I am not changing treatment.", "मैं पहले से लिखा पर्चा, उसकी सही ख़ुराक और तरीक़े के साथ, कॉपी कर रहा/रही हूँ। मैं इलाज नहीं बदल रहा/रही।") else t("I checked the chosen line, name and any date against the original.", "मैंने चुनी पंक्ति, नाम और तारीख़ असली फ़ाइल से मिला ली है।")) }
                        item { Button(onClick = {
                            try {
                                require(title.trim().isNotBlank()) { t("Add a record name.", "रिकॉर्ड का नाम लिखें।") }
                                val current = requireNotNull(value); val quote = current.pages.single { it.page == page }.reviewedText.split('\n')[factLine - 1]
                                val source = DocumentEvidenceRules.source(current, page, factLine, quote)
                                val detail = DocumentEvidenceRules.copiedDetail(source, report.name)
                                val due = if (kind in listOf("Follow-up", "Test")) LocalDateTime.of(LocalDate.parse(dateText), LocalTime.parse(timeText)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0
                                require(factChecked) { t("Check this copied record first.", "पहले इस कॉपी किए रिकॉर्ड को जाँचें।") }
                                val targetKind = when(kind) { "Medicine" -> "medicine"; "Follow-up", "Test" -> "visit"; else -> "journal" }
                                val savedDetail = if (targetKind == "visit") {
                                    require(detail.length <= 1600) { t("Choose a shorter line so the complete source stays with this calendar entry.", "छोटी पंक्ति चुनें ताकि पूरा स्रोत इस कैलेंडर में साथ रहे।") }
                                    CareEntryCodec.encode(CareEntry(type = "schedule", detail = detail, category = if (kind == "Test") "Test" else "Follow-up", timed = true, reportId = report.id, reportName = report.name))
                                } else detail
                                vm.saveItem(targetKind, title, savedDetail, due, done = { factLine = 0; factChecked = false; vm.message.value = if (due > 0) t("Copied to Calendar. Check Reminders to enable alerts.", "कैलेंडर में कॉपी हो गया। अलर्ट के लिए रिमाइंडर देखें।") else t("Checked fact copied", "जाँची हुई बात कॉपी हो गई") })
                                error = ""
                            } catch (failure: Exception) { error = failure.message ?: t("Check the date and exact time.", "तारीख़ और सही समय जाँचें।") }
                        }, enabled = factChecked && !busy, modifier = Modifier.fillMaxWidth().testTag("document-copy-fact")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else if (kind in listOf("Follow-up", "Test")) t("Copy to Calendar", "कैलेंडर में कॉपी करें") else t("Copy checked fact", "जाँची हुई बात कॉपी करें")) } }
                    }
                }
            }
        }
    }
}
