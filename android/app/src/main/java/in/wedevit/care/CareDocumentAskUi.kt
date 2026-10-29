package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ask your documents. The text is read on this phone; only that text and the question go to the server function
 * `sahara-care-assist`, after the person agrees for this one send. Photos and files never leave the phone.
 */
@Composable fun CareDocumentAsk(vm: CareModel, reports: List<CareReport>, folders: Map<String, DocumentFolder>, onOpen: (CareReport) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current; val t = careText()
    val scope = rememberCoroutineScope()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val client = remember { CareRemoteAssist() }
    val store = remember(vm.storageNamespace) { CareDocumentReviewStore(context.applicationContext, vm.storageNamespace) }
    var capabilities by remember { mutableStateOf<CareAssistCapabilities?>(null) }
    var checked by remember { mutableStateOf(false) }
    var chosen by rememberSaveable(selected) { mutableStateOf(emptyList<String>()) }
    var question by rememberSaveable(selected) { mutableStateOf("") }
    var searchAll by rememberSaveable(selected) { mutableStateOf(true) }
    var consent by remember(chosen, question, searchAll) { mutableStateOf(false) }
    var answer by remember(selected) { mutableStateOf<DocumentAnswer?>(null) }
    var message by remember(selected) { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val cloud = vm.cloud
    LaunchedEffect(Unit) {
        capabilities = if (vm.demoMode || cloud == null) null else try { client.capabilities(cloud.transcriptionToken()) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        checked = true
    }
    val person = remember { selected }
    LaunchedEffect(selected) { if (selected != person) { job?.cancel(); onClose() } }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    BackHandler { job?.cancel(); onClose() }
    val service = capabilities?.takeIf { it.askReady }

    fun ask() {
        val picked = if (searchAll) reports.take(DocumentQuestionRules.MAX_SEARCH_DOCUMENTS) else reports.filter { it.id in chosen }; val asked = question
        job = scope.launch {
            message = ""; answer = null
            try {
                val sources = picked.mapIndexedNotNull { index, report ->
                    try {
                    progress = t("Reading ${report.name} on this phone (${index + 1} of ${picked.size})", "इस फ़ोन पर ${report.name} पढ़ रहे हैं (${picked.size} में से ${index + 1})")
                    val text = withContext(Dispatchers.IO) {
                        store.loadVerified(report, vm.reportFiles.file(report)) ?: CareDocumentReader(context).read(vm.reportFiles.file(report), report).also(store::save)
                    }
                    DocumentQuestionSource(report.id, report.name, text.pages, text.reviewedAt > 0)
                    } catch (cancel: CancellationException) { throw cancel } catch (error: Exception) { if (searchAll) null else throw error }
                }
                progress = t("Asking ${service?.provider.orEmpty()}…", "${service?.provider.orEmpty()} से पूछ रहे हैं…")
                val request = if (searchAll) DocumentQuestionRules.retrieve(asked, sources) else DocumentQuestionRules.build(asked, sources)
                answer = client.ask(request, requireNotNull(cloud).transcriptionToken(), consent)
            } catch (cancel: CancellationException) { message = t("Stopped. Nothing else was sent.", "रोक दिया। और कुछ नहीं भेजा गया।"); throw cancel }
            catch (error: Exception) { message = error.message ?: t("Could not get an answer. Your documents are safe.", "जवाब नहीं मिल सका। आपके कागज़ सुरक्षित हैं।") }
            finally { progress = ""; consent = false; job = null }
        }
    }

    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("document-ask"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { job?.cancel(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to documents", "कागज़ात पर वापस")) }
            Column(Modifier.weight(1f)) { Heading(t("Ask your documents", "अपने कागज़ों से पूछें"), 26); Text(t("For example: When is my next follow-up? Which lab test is due?", "जैसे: अगली जाँच के लिए कब आना है? कौन सी लैब जाँच बाकी है?"), style = MaterialTheme.typography.bodySmall) }
        } }
        if (!checked) item { SurfaceCard { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(t("Checking if this is ready…", "देख रहे हैं कि यह तैयार है या नहीं…")) } }
        else if (service == null) item { SurfaceCard(Mint) {
            Text(t("Asking is not available right now", "पूछने की सुविधा अभी नहीं है"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, modifier = Modifier.testTag("document-ask-off"))
            Text(t("Nothing is sent. You can still search your checked reports in Documents.", "कुछ नहीं भेजा जाता। आप कागज़ात में अपनी जाँची हुई रिपोर्टों में अब भी खोज सकते हैं।"), style = MaterialTheme.typography.bodySmall)
        } }
        else {
            item { CheckLine(searchAll, { searchAll = it; answer = null }, t("Search all my documents. Only the lines that match my question are sent.", "मेरे सारे कागज़ों में खोजें। सिर्फ़ सवाल से मिलती पंक्तियाँ भेजी जाती हैं।")) }
            if (!searchAll) item { SectionTitle(t("Choose documents (up to ${DocumentQuestionRules.MAX_DOCUMENTS})", "कागज़ चुनें (${DocumentQuestionRules.MAX_DOCUMENTS} तक)")) }
            if (reports.isEmpty()) item { Text(t("Add a report first.", "पहले कोई रिपोर्ट जोड़ें।")) }
            if (!searchAll) items(reports, key = { it.id }) { report ->
                val on = report.id in chosen
                Row(Modifier.fillMaxWidth().toggleable(on, enabled = job == null && (on || chosen.size < DocumentQuestionRules.MAX_DOCUMENTS), role = Role.Checkbox) { chosen = if (it) chosen + report.id else chosen - report.id; answer = null }
                    .padding(vertical = 4.dp).testTag("document-ask-pick-${report.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
                    Column(Modifier.weight(1f)) {
                        Text(report.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(DocumentFolderRules.label(folders[report.id]?.folder ?: DocumentFolderRules.OTHER, t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { OutlinedTextField(question, { question = it.take(DocumentQuestionRules.MAX_QUESTION); answer = null }, label = { Text(t("Your question", "आपका सवाल")) }, minLines = 2, enabled = job == null, modifier = Modifier.fillMaxWidth().testTag("document-ask-question")) }
            item { CheckLine(consent, { consent = it }, t("I agree to send my question and ${if (searchAll) "the matching lines from my documents" else "the text of these ${chosen.size} documents"} to ${service.provider}. Photos and files are not sent. Nothing is sent until I tap Ask. I will be asked again next time.", "मैं अपना सवाल और ${if (searchAll) "मेरे कागज़ों की मिलती पंक्तियाँ" else "इन ${chosen.size} कागज़ों की लिखाई"} ${service.provider} को भेजने के लिए राज़ी हूँ। फ़ोटो और फ़ाइलें नहीं भेजी जातीं। ‘पूछें’ दबाने तक कुछ नहीं भेजा जाता। अगली बार फिर पूछा जाएगा।")) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = ::ask, enabled = consent && (if (searchAll) reports.isNotEmpty() else chosen.isNotEmpty()) && question.isNotBlank() && job == null, modifier = Modifier.testTag("document-ask-send")) { Text(t("Ask", "पूछें")) }
                if (job != null) TextButton(onClick = { job?.cancel() }) { Text(t("Stop", "रोकें")) }
            } }
            if (progress.isNotBlank()) item { Text(progress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.announce()) }
        }
        if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        answer?.let { result ->
            if (!result.found) item { SurfaceCard { Text(t("Not found in the chosen documents", "चुने गए कागज़ों में नहीं मिला"), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("document-ask-not-found")); Text(t("Try other documents or other words.", "दूसरे कागज़ या दूसरे शब्द आज़माएँ।"), style = MaterialTheme.typography.bodySmall) } }
            else {
                item { SurfaceCard(Sage) {
                    Text(t("Answer · check it with the lines below", "जवाब · नीचे की पंक्तियों से मिलाएँ"), style = MaterialTheme.typography.labelLarge)
                    SelectionContainerText(result.answer)
                } }
                item { SectionTitle(t("Where this came from", "यह कहाँ से आया")) }
                items(result.citations, key = { "${it.reportId}:${it.page}:${it.line}" }) { citation ->
                    SurfaceCard {
                        Text(citation.reportName, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                        Text(t("Page ${citation.page} · line ${citation.line}", "पेज ${citation.page} · पंक्ति ${citation.line}"), style = MaterialTheme.typography.labelMedium)
                        Text(citation.quote)
                        if (!citation.reviewed) Text(t("The phone read this text. No one has checked it yet. Compare it with the paper.", "यह लिखाई फ़ोन ने पढ़ी है। अभी किसी ने जाँची नहीं। असली कागज़ से मिलाएँ।"), style = MaterialTheme.typography.bodySmall, color = RustText)
                        reports.firstOrNull { it.id == citation.reportId }?.let { report -> TextButton(onClick = { onOpen(report) }, colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(t("Open this document", "यह कागज़ खोलें")) } }
                    }
                }
            }
        }
        item { Text(t("Answers come only from your documents. Not medical advice. Ask your care team.", "जवाब सिर्फ़ आपके कागज़ों से आते हैं। यह डॉक्टरी सलाह नहीं है। अपनी देखभाल टीम से पूछें।"), style = MaterialTheme.typography.bodySmall) }
    }
}
