@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
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
    var lookIn by rememberSaveable(selected) { mutableStateOf(DocumentQuestionRules.SCOPE_ALL) }
    val searchAll = lookIn != DocumentQuestionRules.SCOPE_PICK
    val scoped = DocumentQuestionRules.inScope(reports, lookIn, folders) { it.id }
    val counts = DocumentFolderRules.counts(reports.map { folders[it.id]?.folder ?: DocumentFolderRules.OTHER })
    var consent by remember(chosen, question, lookIn) { mutableStateOf(false) }
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
        val picked = if (searchAll) scoped.take(DocumentQuestionRules.MAX_SEARCH_DOCUMENTS) else reports.filter { it.id in chosen }; val asked = question
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
                    DocumentQuestionSource(report.id, report.name, text.pages, text.reviewedAt > 0, report.created, folders[report.id]?.folder.orEmpty())
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
                    notice = run { val detail = error.message?.let { " (${it.trimEnd('.')})" }.orEmpty(); t("The online answer did not work this time$detail. Below are the matching lines found on this phone instead.", "ऑनलाइन जवाब इस बार नहीं मिला$detail। इसकी जगह इस फ़ोन पर मिली पंक्तियाँ नीचे हैं।") }
                }
                local = found
            } catch (cancel: CancellationException) { message = t("Stopped. Nothing else was sent.", "रोक दिया। और कुछ नहीं भेजा गया।"); throw cancel }
            catch (error: Exception) { message = error.message ?: t("Could not get an answer. Your documents are safe.", "जवाब नहीं मिल सका। आपके कागज़ सुरक्षित हैं।") }
            finally { progress = ""; consent = false; job = null }
        }
    }

    /** Ready-made questions, one per kind of document the person has, so a bill and a lab report can each be tried. */
    val suggestions = listOf(
        DocumentFolderRules.BILLS to t("How much was my last bill?", "मेरा पिछला बिल कितने का था?"),
        DocumentFolderRules.LAB to t("What is my latest haemoglobin?", "मेरा सबसे नया हीमोग्लोबिन कितना है?"),
        DocumentFolderRules.PRESCRIPTIONS to t("Which medicines are listed on my prescription?", "मेरे पर्चे पर कौन सी दवाइयाँ लिखी हैं?"),
        DocumentFolderRules.DISCHARGE to t("When was I discharged?", "मुझे अस्पताल से छुट्टी कब मिली?"),
        DocumentQuestionRules.SCOPE_ALL to t("When is my next follow-up?", "अगली जाँच के लिए कब आना है?"),
    ).filter { (where, _) -> where == DocumentQuestionRules.SCOPE_ALL || (counts[where] ?: 0) > 0 }
    fun clear() { answer = null; local = null }

    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("document-ask"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { job?.cancel(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to documents", "कागज़ात पर वापस")) }
            Heading(t("Ask your documents", "अपने कागज़ों से पूछें"), 26)
        } }
        if (!checked) item { SurfaceCard { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(t("Checking if this is ready…", "देख रहे हैं कि यह तैयार है या नहीं…")) } }
        else {
            if (service == null) item { DocumentAskPill(t("Offline · matching lines only", "ऑफ़लाइन · सिर्फ़ मिलती पंक्तियाँ"), Mint, Modifier.testTag("document-ask-off")) }
            item { OutlinedTextField(question, { question = it.take(DocumentQuestionRules.MAX_QUESTION); clear() }, label = { Text(t("Your question", "आपका सवाल")) }, minLines = 2, enabled = job == null, modifier = Modifier.fillMaxWidth().testTag("document-ask-question")) }
            if (reports.isNotEmpty()) item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("document-ask-suggestions")) {
                suggestions.forEach { (where, text) ->
                    SuggestionChip(onClick = { question = text; lookIn = where; clear() }, enabled = job == null, label = { Text(text) }, shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("document-ask-suggest-$where"))
                }
            } }
            item { Text(t("Look in", "कहाँ देखें"), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val scopes = listOf(DocumentQuestionRules.SCOPE_ALL) + DocumentFolderRules.folders.filter { (counts[it] ?: 0) > 0 } + DocumentQuestionRules.SCOPE_PICK
                scopes.forEach { option ->
                    val label = when (option) {
                        DocumentQuestionRules.SCOPE_ALL -> t("All", "सभी") + " · ${reports.size}"
                        DocumentQuestionRules.SCOPE_PICK -> t("Choose", "चुनें")
                        else -> DocumentFolderRules.label(option, t) + " · ${counts[option] ?: 0}"
                    }
                    FilterChip(selected = lookIn == option, onClick = { lookIn = option; clear() }, enabled = job == null, label = { Text(label) }, shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("document-ask-scope-$option"))
                }
            } }
            if (reports.isEmpty()) item { Text(t("Add a report first.", "पहले कोई रिपोर्ट जोड़ें।")) }
            if (!searchAll) item { Text(t("Choose documents (up to ${DocumentQuestionRules.MAX_DOCUMENTS})", "कागज़ चुनें (${DocumentQuestionRules.MAX_DOCUMENTS} तक)"), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub) }
            if (!searchAll) items(reports, key = { it.id }) { report ->
                val on = report.id in chosen
                Row(Modifier.fillMaxWidth().toggleable(on, enabled = job == null && (on || chosen.size < DocumentQuestionRules.MAX_DOCUMENTS), role = Role.Checkbox) { chosen = if (it) chosen + report.id else chosen - report.id; clear() }
                    .padding(vertical = 4.dp).testTag("document-ask-pick-${report.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
                    Text(report.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp)); DocumentAskTypeBadge(folders[report.id]?.folder)
                }
            }
            if (service != null) item { val count = chosen.size; val sendWhat = if (searchAll) t("the matching lines from my documents", "मेरे कागज़ों की मिलती पंक्तियाँ") else t("the text of these $count documents", "इन $count कागज़ों की लिखाई"); val sendTo = service.provider; CheckLine(consent, { consent = it }, t("Send my question and $sendWhat to $sendTo, this time only. Photos and files stay on this phone.", "मेरा सवाल और $sendWhat सिर्फ़ इस बार $sendTo को भेजें। फ़ोटो और फ़ाइलें इसी फ़ोन पर रहती हैं।")) }
            item { val ready = (if (searchAll) scoped.isNotEmpty() else chosen.isNotEmpty()) && question.isNotBlank() && job == null
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
                    Text(t("Answer", "जवाब"), style = MaterialTheme.typography.labelLarge)
                    SelectionContainerText(result.answer)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Sources", "स्रोत"), style = MaterialTheme.typography.labelMedium, color = DoctorCardSub)
                        result.citations.indices.forEach { DocumentAskNumber(it + 1) }
                    }
                } }
                itemsIndexed(result.citations, key = { _, it -> "${it.reportId}:${it.page}:${it.line}" }) { index, citation ->
                    DocumentAskSource(index + 1, citation.reportName, folders[citation.reportId]?.folder, t("Page ${citation.page} · line ${citation.line}", "पेज ${citation.page} · पंक्ति ${citation.line}"), citation.reviewed,
                        reports.firstOrNull { it.id == citation.reportId }?.let { report -> { onOpen(report) } }) {
                        Text("“${citation.quote}”", fontStyle = FontStyle.Italic)
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
                if (answer == null && found.values.isNotEmpty()) item { SurfaceCard(Sage) {
                    Text(t("Value found · newest document first", "मिला हुआ मान · सबसे नया कागज़ पहले"), style = MaterialTheme.typography.labelLarge)
                    found.values.forEach { value -> val hit = value.match
                        Spacer(Modifier.height(6.dp))
                        Text(marked(hit.text, hit.marks), fontWeight = FontWeight.Medium); if (hit.next.isNotBlank()) Text(hit.next, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(listOfNotNull(hit.reportName, dayText(value.date), t("page ${hit.page} · line ${hit.line}", "पेज ${hit.page} · पंक्ति ${hit.line}")).joinToString(" · "), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(6.dp)); DocumentAskTypeBadge(folders[hit.reportId]?.folder)
                        }
                    }
                } }
                item { Row(verticalAlignment = Alignment.CenterVertically, modifier = if (answer == null) Modifier.testTag("document-ask-local-answer") else Modifier) {
                    SectionTitle(if (online) t("Lines this is based on", "जिन पंक्तियों पर यह आधारित है") else t("Matching lines", "मिलती पंक्तियाँ"))
                } }
                itemsIndexed(found.groups, key = { _, it -> "local:${it.reportId}" }) { index, group ->
                    DocumentAskSource(index + 1, group.reportName, folders[group.reportId]?.folder, dayText(group.date), group.reviewed,
                        reports.firstOrNull { it.id == group.reportId }?.let { report -> { onOpen(report) } }) {
                        group.lines.forEach { hit ->
                            Text(t("Page ${hit.page} · line ${hit.line}", "पेज ${hit.page} · पंक्ति ${hit.line}"), style = MaterialTheme.typography.labelMedium, color = DoctorCardSub)
                            Text(marked(hit.text, hit.marks))
                        }
                    }
                }
            }
        }
        item { InfoDisclosure(t("How answers work", "जवाब कैसे बनते हैं")) {
            Text(t("Answers come only from your documents. Not medical advice. Ask your care team.", "जवाब सिर्फ़ आपके कागज़ों से आते हैं। यह डॉक्टरी सलाह नहीं है। अपनी देखभाल टीम से पूछें।"), style = MaterialTheme.typography.bodySmall)
            Text(t("The phone sorts your documents into bills, lab reports, prescriptions and discharge summaries from their words. You can move a document to another folder on the Documents screen.", "फ़ोन आपके कागज़ों को उनके शब्दों से बिल, लैब रिपोर्ट, पर्चे और डिस्चार्ज सारांश में छाँटता है। आप कागज़ात वाली स्क्रीन पर किसी कागज़ को दूसरे फ़ोल्डर में डाल सकते हैं।"), style = MaterialTheme.typography.bodySmall)
            if (service == null) Text(t("You can still find the lines in your documents that match your question. This works on this phone, without internet. Nothing is sent.", "आप फिर भी अपने कागज़ों में सवाल से मिलती पंक्तियाँ ढूँढ सकते हैं। यह इसी फ़ोन पर, बिना इंटरनेट के चलता है। कुछ नहीं भेजा जाता।"), style = MaterialTheme.typography.bodySmall)
            else Text(t("Nothing is sent until you tap Ask, and you are asked again each time. Only your question, the matching lines and each document's type are sent, never file names.", "‘पूछें’ दबाने तक कुछ नहीं भेजा जाता, और हर बार फिर पूछा जाता है। सिर्फ़ आपका सवाल, मिलती पंक्तियाँ और हर कागज़ का प्रकार भेजा जाता है, फ़ाइल के नाम कभी नहीं।"), style = MaterialTheme.typography.bodySmall)
            Text(t("Matching lines are found on this phone, not explained. Best match first.", "मिलती पंक्तियाँ इसी फ़ोन पर ढूँढी जाती हैं, समझाई नहीं जातीं। सबसे अच्छी पहले।"), style = MaterialTheme.typography.bodySmall)
        } }
    }
}

/** One source: its number, the document's name and type, where in it, the lines, and a link to open it. */
@Composable private fun DocumentAskSource(number: Int, name: String, folder: String?, where: String?, reviewed: Boolean, onOpen: (() -> Unit)?, lines: @Composable ColumnScope.() -> Unit) {
    val t = careText()
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DocumentAskNumber(number); Spacer(Modifier.width(8.dp))
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp)); DocumentAskTypeBadge(folder)
        }
        where?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = DoctorCardSub) }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = lines)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!reviewed) Text(t("Not checked yet · compare with the paper", "अभी जाँचा नहीं · असली कागज़ से मिलाएँ"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = RustText)
            else Spacer(Modifier.weight(1f))
            onOpen?.let { TextButton(onClick = it, colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(t("Open this document", "यह कागज़ खोलें")) } }
        }
    }
}

/** The number that ties an answer to its source card. */
@Composable private fun DocumentAskNumber(number: Int) {
    Surface(shape = CircleShape, color = Ink, contentColor = Paper, modifier = Modifier.size(22.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("$number", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** What kind of document this is, as sorted on the phone or chosen by the person. */
@Composable internal fun DocumentAskTypeBadge(folder: String?) {
    val t = careText()
    val (label, color) = when (folder) {
        DocumentFolderRules.BILLS -> t("Bill", "बिल") to Peach
        DocumentFolderRules.LAB -> t("Lab report", "लैब रिपोर्ट") to Mint
        DocumentFolderRules.PRESCRIPTIONS -> t("Prescription", "पर्चा") to Sage
        DocumentFolderRules.DISCHARGE -> t("Discharge summary", "डिस्चार्ज सारांश") to Gold.copy(alpha = 0.6f)
        else -> t("Other", "अन्य") to DoctorHairline
    }
    DocumentAskPill(label, color, Modifier.testTag("document-ask-type-${folder ?: DocumentFolderRules.OTHER}"))
}

@Composable private fun DocumentAskPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = color, contentColor = DoctorCardInk, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** The matched words in bold on a light gold background, so the eye finds them in a long line. */
private fun marked(text: String, marks: List<IntRange>) = buildAnnotatedString {
    append(text); marks.filter { it.first >= 0 && it.last < text.length }.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = Gold.copy(alpha = 0.55f)), it.first, it.last + 1) }
}
/** yyyymmdd as dd/mm/yyyy, the way Indian reports print it; null when unknown. */
private fun dayText(day: Int): String? = if (day <= 0) null else "%02d/%02d/%04d".format(day % 100, day / 100 % 100, day / 10000)
