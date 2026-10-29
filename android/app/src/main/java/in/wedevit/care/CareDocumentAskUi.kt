@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
    var local by remember(selected) { mutableStateOf<LocalDocumentAnswer?>(null) }
    var message by remember(selected) { mutableStateOf("") }
    var notice by remember(selected) { mutableStateOf("") }
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

    /** Always finds the matching lines on this phone first. Only when [online] and the person agreed, the question goes out too; if that fails, the phone's lines are the answer. */
    fun ask(online: Boolean) {
        val picked = if (searchAll) reports.take(DocumentQuestionRules.MAX_SEARCH_DOCUMENTS) else reports.filter { it.id in chosen }; val asked = question
        val remote = service.takeIf { online && consent }
        job = scope.launch {
            message = ""; notice = ""; answer = null; local = null
            try {
                val sources = picked.mapIndexedNotNull { index, report ->
                    try {
                    progress = t("Reading ${report.name} on this phone (${index + 1} of ${picked.size})", "इस फ़ोन पर ${report.name} पढ़ रहे हैं (${picked.size} में से ${index + 1})")
                    val text = withContext(Dispatchers.IO) {
                        store.loadVerified(report, vm.reportFiles.file(report)) ?: CareDocumentReader(context).read(vm.reportFiles.file(report), report).also(store::save)
                    }
                    DocumentQuestionSource(report.id, report.name, text.pages, text.reviewedAt > 0, report.created)
                    } catch (cancel: CancellationException) { throw cancel } catch (error: Exception) { if (searchAll) null else throw error }
                }
                progress = t("Finding matching lines on this phone…", "इस फ़ोन पर मिलती पंक्तियाँ ढूँढ रहे हैं…")
                val found = withContext(Dispatchers.Default) { DocumentQuestionRules.matchLines(asked, sources) }
                if (remote == null || cloud == null) { local = found; return@launch }
                progress = t("Asking ${remote.provider}…", "${remote.provider} से पूछ रहे हैं…")
                try {
                    val request = if (searchAll) DocumentQuestionRules.retrieve(asked, sources) else DocumentQuestionRules.build(asked, sources)
                    answer = client.ask(request, cloud.transcriptionToken(), true)
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    notice = t("The online answer did not work this time${error.message?.let { " (${it.trimEnd('.')})" }.orEmpty()}. Below are the matching lines found on this phone instead.", "ऑनलाइन जवाब इस बार नहीं मिला। इसकी जगह इस फ़ोन पर मिली पंक्तियाँ नीचे हैं।")
                }
                local = found
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
        else {
            if (service == null) item { SurfaceCard(Mint) {
                Text(t("Online answers are not set up right now", "ऑनलाइन जवाब अभी चालू नहीं है"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, modifier = Modifier.testTag("document-ask-off"))
                Text(t("You can still find the lines in your documents that match your question. This works on this phone, without internet. Nothing is sent.", "आप फिर भी अपने कागज़ों में सवाल से मिलती पंक्तियाँ ढूँढ सकते हैं। यह इसी फ़ोन पर, बिना इंटरनेट के चलता है। कुछ नहीं भेजा जाता।"), style = MaterialTheme.typography.bodySmall)
            } }
            item { CheckLine(searchAll, { searchAll = it; answer = null; local = null }, if (service == null) t("Search all my documents", "मेरे सारे कागज़ों में खोजें") else t("Search all my documents. Only the lines that match my question are sent.", "मेरे सारे कागज़ों में खोजें। सिर्फ़ सवाल से मिलती पंक्तियाँ भेजी जाती हैं।")) }
            if (!searchAll) item { SectionTitle(t("Choose documents (up to ${DocumentQuestionRules.MAX_DOCUMENTS})", "कागज़ चुनें (${DocumentQuestionRules.MAX_DOCUMENTS} तक)")) }
            if (reports.isEmpty()) item { Text(t("Add a report first.", "पहले कोई रिपोर्ट जोड़ें।")) }
            if (!searchAll) items(reports, key = { it.id }) { report ->
                val on = report.id in chosen
                Row(Modifier.fillMaxWidth().toggleable(on, enabled = job == null && (on || chosen.size < DocumentQuestionRules.MAX_DOCUMENTS), role = Role.Checkbox) { chosen = if (it) chosen + report.id else chosen - report.id; answer = null; local = null }
                    .padding(vertical = 4.dp).testTag("document-ask-pick-${report.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
                    Column(Modifier.weight(1f)) {
                        Text(report.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(DocumentFolderRules.label(folders[report.id]?.folder ?: DocumentFolderRules.OTHER, t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { OutlinedTextField(question, { question = it.take(DocumentQuestionRules.MAX_QUESTION); answer = null; local = null }, label = { Text(t("Your question", "आपका सवाल")) }, minLines = 2, enabled = job == null, modifier = Modifier.fillMaxWidth().testTag("document-ask-question")) }
            if (service != null) item { CheckLine(consent, { consent = it }, t("I agree to send my question and ${if (searchAll) "the matching lines from my documents" else "the text of these ${chosen.size} documents"} to ${service.provider}. Photos and files are not sent. Nothing is sent until I tap Ask. I will be asked again next time.", "मैं अपना सवाल और ${if (searchAll) "मेरे कागज़ों की मिलती पंक्तियाँ" else "इन ${chosen.size} कागज़ों की लिखाई"} ${service.provider} को भेजने के लिए राज़ी हूँ। फ़ोटो और फ़ाइलें नहीं भेजी जातीं। ‘पूछें’ दबाने तक कुछ नहीं भेजा जाता। अगली बार फिर पूछा जाएगा।")) }
            item { val ready = (if (searchAll) reports.isNotEmpty() else chosen.isNotEmpty()) && question.isNotBlank() && job == null
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (service != null) Button(onClick = { ask(true) }, enabled = consent && ready, modifier = Modifier.testTag("document-ask-send")) { Text(t("Ask", "पूछें")) }
                if (service == null) Button(onClick = { ask(false) }, enabled = ready, modifier = Modifier.testTag("document-ask-local")) { Text(t("Find in my documents", "मेरे कागज़ों में ढूँढें")) }
                else OutlinedButton(onClick = { ask(false) }, enabled = ready, modifier = Modifier.testTag("document-ask-local")) { Text(t("Find on this phone only", "सिर्फ़ इस फ़ोन पर ढूँढें")) }
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
        if (notice.isNotBlank()) item { Text(notice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.announce()) }
        local?.let { found ->
            val online = answer?.found == true
            if (!found.found) { if (answer == null) item { SurfaceCard {
                Text(t("No matching lines on this phone", "इस फ़ोन पर मिलती पंक्तियाँ नहीं मिलीं"), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("document-ask-local-none"))
                Text(t("Try other words, for example the test name as it is printed on the report.", "दूसरे शब्द आज़माएँ, जैसे रिपोर्ट पर छपा जाँच का नाम।"), style = MaterialTheme.typography.bodySmall)
            } } }
            else {
                if (answer == null) item { SurfaceCard(Mint) {
                    Text(t("Answer from your documents", "आपके कागज़ों से जवाब"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, modifier = Modifier.testTag("document-ask-local-answer"))
                    Text(t("These are the lines in your documents that match your question, best match first. The phone only finds them; it does not explain them. Nothing left this phone.", "ये आपके कागज़ों की वे पंक्तियाँ हैं जो आपके सवाल से मिलती हैं, सबसे अच्छी पहले। फ़ोन सिर्फ़ इन्हें ढूँढता है, इनका मतलब नहीं बताता। कुछ भी इस फ़ोन से बाहर नहीं गया।"), style = MaterialTheme.typography.bodySmall)
                } }
                if (answer == null && found.values.isNotEmpty()) item { SurfaceCard(Sage) {
                    Text(t("Value found · newest document first", "मिला हुआ मान · सबसे नया कागज़ पहले"), style = MaterialTheme.typography.labelLarge)
                    found.values.forEach { value -> val hit = value.match
                        Spacer(Modifier.height(6.dp))
                        Text(marked(hit.text, hit.marks), fontWeight = FontWeight.Medium); if (hit.next.isNotBlank()) Text(hit.next, fontWeight = FontWeight.Medium)
                        Text(listOfNotNull(hit.reportName, dayText(value.date), t("page ${hit.page} · line ${hit.line}", "पेज ${hit.page} · पंक्ति ${hit.line}")).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                } }
                item { SectionTitle(if (online) t("Lines this is based on", "जिन पंक्तियों पर यह आधारित है") else t("Matching lines", "मिलती पंक्तियाँ")) }
                items(found.groups, key = { "local:${it.reportId}" }) { group -> SurfaceCard {
                    Text(group.reportName, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    dayText(group.date)?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    group.lines.forEach { hit ->
                        Spacer(Modifier.height(6.dp))
                        Text(t("Page ${hit.page} · line ${hit.line}", "पेज ${hit.page} · पंक्ति ${hit.line}"), style = MaterialTheme.typography.labelMedium)
                        Text(marked(hit.text, hit.marks))
                    }
                    if (!group.reviewed) Text(t("The phone read this text. No one has checked it yet. Compare it with the paper.", "यह लिखाई फ़ोन ने पढ़ी है। अभी किसी ने जाँची नहीं। असली कागज़ से मिलाएँ।"), style = MaterialTheme.typography.bodySmall, color = RustText)
                    reports.firstOrNull { it.id == group.reportId }?.let { report -> TextButton(onClick = { onOpen(report) }, colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(t("Open this document", "यह कागज़ खोलें")) } }
                } }
            }
        }
        item { Text(t("Answers come only from your documents. Not medical advice. Ask your care team.", "जवाब सिर्फ़ आपके कागज़ों से आते हैं। यह डॉक्टरी सलाह नहीं है। अपनी देखभाल टीम से पूछें।"), style = MaterialTheme.typography.bodySmall) }
    }
}

/** The matched words in bold on a light gold background, so the eye finds them in a long line. */
private fun marked(text: String, marks: List<IntRange>) = buildAnnotatedString {
    append(text); marks.filter { it.first >= 0 && it.last < text.length }.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = Gold.copy(alpha = 0.55f)), it.first, it.last + 1) }
}
/** yyyymmdd as dd/mm/yyyy, the way Indian reports print it; null when unknown. */
private fun dayText(day: Int): String? = if (day <= 0) null else "%02d/%02d/%04d".format(day % 100, day / 100 % 100, day / 10000)
