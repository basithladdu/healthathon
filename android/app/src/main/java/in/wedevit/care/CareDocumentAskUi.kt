@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
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
    var askedText by remember(selected) { mutableStateOf("") }
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
            message = ""; notice = ""; answer = null; local = null; askedText = asked
            try {
                val sources = picked.mapNotNull { report ->
                    try {
                    progress = t("Searching…", "खोज रहे हैं…")
                    val text = withContext(Dispatchers.IO) {
                        store.loadVerified(report, vm.reportFiles.file(report)) ?: CareDocumentReader(context).read(vm.reportFiles.file(report), report).also(store::save)
                    }
                    DocumentQuestionSource(report.id, report.name, text.pages, text.reviewedAt > 0, report.created, folders[report.id]?.folder.orEmpty())
                    } catch (cancel: CancellationException) { throw cancel } catch (error: Exception) { if (searchAll) null else throw error }
                }
                progress = t("Searching…", "खोज रहे हैं…")
                val found = withContext(Dispatchers.Default) { DocumentQuestionRules.matchLines(asked, sources) }
                if (remote == null || cloud == null) { local = found; return@launch }
                progress = t("Asking ${remote.provider}…", "${remote.provider} से पूछ रहे हैं…")
                try {
                    val request = if (searchAll) DocumentQuestionRules.retrieve(asked, sources) else DocumentQuestionRules.build(asked, sources)
                    answer = client.ask(request, cloud.transcriptionToken(), true)
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    notice = run { val detail = error.message?.let { " (${it.trimEnd('.')})" }.orEmpty(); t("Online answer failed$detail.", "ऑनलाइन जवाब नहीं मिला$detail।") }
                }
                local = found
            } catch (cancel: CancellationException) { message = t("Stopped", "रोक दिया"); throw cancel }
            catch (error: Exception) { message = error.message ?: t("Something went wrong. Try again.", "कुछ गड़बड़ हुई। फिर कोशिश करें।") }
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
    fun clear() { answer = null; local = null; askedText = "" }

    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("document-ask"), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { job?.cancel(); onClose() }, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to documents", "कागज़ात पर वापस")) }
            Heading(t("Ask your documents", "अपने कागज़ों से पूछें"), 26)
        } }
        if (checked && service == null) item { DocumentAskPill(t("Offline", "ऑफ़लाइन"), Mint, Modifier.testTag("document-ask-off")) }
        if (!checked) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = DoctorAccent, trackColor = DoctorHairline) }
        else {
            item { OutlinedTextField(question, { question = it.take(DocumentQuestionRules.MAX_QUESTION); clear() }, label = { Text(t("Your question", "आपका सवाल")) }, minLines = 2, enabled = job == null, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("document-ask-question")) }
            if (reports.isNotEmpty()) item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("document-ask-suggestions")) {
                suggestions.forEach { (where, text) ->
                    SuggestionChip(onClick = { question = text; lookIn = where; clear() }, enabled = job == null, label = { Text(text, fontSize = 14.sp) }, shape = RoundedCornerShape(50),
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Paper, labelColor = DoctorCardInk),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("document-ask-suggest-$where"))
                }
            } }
            item { Text(t("Look in", "कहाँ देखें"), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val scopes = listOf(DocumentQuestionRules.SCOPE_ALL) + DocumentFolderRules.folders.filter { (counts[it] ?: 0) > 0 } + DocumentQuestionRules.SCOPE_PICK
                scopes.forEach { option ->
                    val label = when (option) {
                        DocumentQuestionRules.SCOPE_ALL -> t("All", "सभी") + " · ${reports.size}"
                        DocumentQuestionRules.SCOPE_PICK -> t("Choose", "चुनें")
                        else -> DocumentFolderRules.label(option, t) + " · ${counts[option] ?: 0}"
                    }
                    FilterChip(selected = lookIn == option, onClick = { lookIn = option; clear() }, enabled = job == null, label = { Text(label, fontSize = 14.sp) }, shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("document-ask-scope-$option"))
                }
            } }
            if (reports.isEmpty()) item { Text(t("Add a report first.", "पहले कोई रिपोर्ट जोड़ें।"), fontSize = 16.sp, color = DoctorCardSub) }
            if (!searchAll) item { Text(t("Choose documents (up to ${DocumentQuestionRules.MAX_DOCUMENTS})", "कागज़ चुनें (${DocumentQuestionRules.MAX_DOCUMENTS} तक)"), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub) }
            if (!searchAll) items(reports, key = { it.id }) { report ->
                val on = report.id in chosen
                Row(Modifier.fillMaxWidth().toggleable(on, enabled = job == null && (on || chosen.size < DocumentQuestionRules.MAX_DOCUMENTS), role = Role.Checkbox) { chosen = if (it) chosen + report.id else chosen - report.id; clear() }
                    .heightIn(min = 48.dp).padding(vertical = 4.dp).testTag("document-ask-pick-${report.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
                    Text(report.name, Modifier.weight(1f), color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp)); DocumentAskTypeBadge(folders[report.id]?.folder)
                }
            }
            if (service != null) item { val count = chosen.size; val sendWhat = if (searchAll) t("the matching lines from my documents", "मेरे कागज़ों की मिलती पंक्तियाँ") else t("the text of these $count documents", "इन $count कागज़ों की लिखाई"); val sendTo = service.provider; CheckLine(consent, { consent = it }, t("Send my question and $sendWhat to $sendTo", "मेरा सवाल और $sendWhat $sendTo को भेजें")) }
            item { val ready = (if (searchAll) scoped.isNotEmpty() else chosen.isNotEmpty()) && question.isNotBlank() && job == null
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (service != null) Button(onClick = { ask(true) }, enabled = consent && ready, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.heightIn(min = 48.dp).testTag("document-ask-send")) { Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Ask", "पूछें"), fontWeight = FontWeight.SemiBold) }
                if (service == null) Button(onClick = { ask(false) }, enabled = ready, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.heightIn(min = 48.dp).testTag("document-ask-local")) { Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Search", "खोजें")) }
                else OutlinedButton(onClick = { ask(false) }, enabled = ready, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("document-ask-local")) { Text(t("Search", "खोजें")) }
                if (job != null) TextButton(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Stop", "रोकें")) }
            } }
            if (progress.isNotBlank()) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = DoctorAccent, trackColor = DoctorHairline); Text(progress, fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.announce()) } }
        }
        if (message.isNotBlank()) item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f).announce(), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            if (job == null && question.isNotBlank() && (if (searchAll) scoped.isNotEmpty() else chosen.isNotEmpty())) TextButton(onClick = { ask(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Try again", "फिर कोशिश करें"), color = RustText, fontWeight = FontWeight.SemiBold) }
        } }
        // The question just asked, as the person's own message, so the answer reads like a reply.
        if (askedText.isNotBlank() && (answer != null || local != null)) item { DocumentAskBubble(mine = true) { Text(askedText, color = DoctorCardInk, fontSize = 16.sp) } }
        answer?.let { result ->
            if (!result.found) item { DocumentAskBubble(mine = false) { Text(t("Not found in the chosen documents", "चुने गए कागज़ों में नहीं मिला"), fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("document-ask-not-found")) } }
            else {
                item { DocumentAskBubble(mine = false) {
                    Text(t("Answer", "जवाब"), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub)
                    SelectionContainerText(result.answer)
                    if (result.citations.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Sources", "स्रोत"), fontSize = 14.sp, color = DoctorCardSub)
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
        if (notice.isNotBlank()) item { Text(notice, fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.announce()) }
        local?.let { found ->
            val online = answer?.found == true
            if (!found.found) { if (answer == null) item { DocumentAskBubble(mine = false) {
                Text(t("Nothing found", "कुछ नहीं मिला"), fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("document-ask-local-none"))
            } } }
            else {
                if (answer == null && found.values.isNotEmpty()) item { DocumentAskBubble(mine = false) {
                    Text(t("Value found", "मिला हुआ मान"), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub)
                    found.values.forEach { value -> val hit = value.match
                        Spacer(Modifier.height(6.dp))
                        Text(marked(hit.text, hit.marks), fontWeight = FontWeight.Medium, color = DoctorCardInk); if (hit.next.isNotBlank()) Text(hit.next, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(listOfNotNull(hit.reportName, dayText(value.date), t("page ${hit.page} · line ${hit.line}", "पेज ${hit.page} · पंक्ति ${hit.line}")).joinToString(" · "), Modifier.weight(1f, fill = false), fontSize = 14.sp, color = DoctorCardSub)
                            Spacer(Modifier.width(6.dp)); DocumentAskTypeBadge(folders[hit.reportId]?.folder)
                        }
                    }
                } }
                item { Row(verticalAlignment = Alignment.CenterVertically, modifier = if (answer == null) Modifier.testTag("document-ask-local-answer") else Modifier) {
                    SectionTitle(if (online) t("Sources", "स्रोत") else t("Matching lines", "मिलती पंक्तियाँ"))
                } }
                itemsIndexed(found.groups, key = { _, it -> "local:${it.reportId}" }) { index, group ->
                    DocumentAskSource(index + 1, group.reportName, folders[group.reportId]?.folder, dayText(group.date), group.reviewed,
                        reports.firstOrNull { it.id == group.reportId }?.let { report -> { onOpen(report) } }) {
                        group.lines.forEach { hit ->
                            Text(t("Page ${hit.page} · line ${hit.line}", "पेज ${hit.page} · पंक्ति ${hit.line}"), fontSize = 14.sp, color = DoctorCardSub)
                            Text(marked(hit.text, hit.marks), color = DoctorCardInk)
                        }
                    }
                }
            }
        }
    }
}

/** One source: its number, the document's name and type, where in it, the lines, and a link to open it. */
@Composable private fun DocumentAskSource(number: Int, name: String, folder: String?, where: String?, reviewed: Boolean, onOpen: (() -> Unit)?, lines: @Composable ColumnScope.() -> Unit) = DocumentAskArrive {
    val t = careText()
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DocumentAskNumber(number); Spacer(Modifier.width(8.dp))
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp)); DocumentAskTypeBadge(folder)
        }
        where?.let { Text(it, Modifier.padding(top = 4.dp), fontSize = 14.sp, color = DoctorCardSub) }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = lines)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!reviewed) Text(t("Not checked yet", "अभी जाँचा नहीं"), Modifier.weight(1f), fontSize = 14.sp, color = RustText)
            else Spacer(Modifier.weight(1f))
            onOpen?.let { TextButton(onClick = it, colors = ButtonDefaults.textButtonColors(contentColor = RustText), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Open", "खोलें"), fontWeight = FontWeight.SemiBold) } }
        }
    } }
}

/** One message in the ask thread: the person's question on the right, the reply on the left. */
@Composable private fun DocumentAskBubble(mine: Boolean, content: @Composable ColumnScope.() -> Unit) = DocumentAskArrive {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(shape = if (mine) RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp, bottomStart = 20.dp) else RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = if (mine) SaharaSoftGold else Mint, modifier = if (mine) Modifier.padding(start = 48.dp) else Modifier.fillMaxWidth().padding(end = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

/** A reply or source card rises in once when it arrives, instead of popping onto the screen. */
@Composable private fun DocumentAskArrive(content: @Composable () -> Unit) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(shown, enter = SaharaMotion.enter) { content() }
}

/** The number that ties an answer to its source card. */
@Composable private fun DocumentAskNumber(number: Int) {
    Surface(shape = CircleShape, color = DoctorAccent, contentColor = Paper, modifier = Modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("$number", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

/** What kind of document this is, as sorted on the phone or chosen by the person. */
@Composable internal fun DocumentAskTypeBadge(folder: String?) {
    val t = careText()
    val label = when (folder) {
        DocumentFolderRules.BILLS -> t("Bill", "बिल")
        DocumentFolderRules.LAB -> t("Lab report", "लैब रिपोर्ट")
        DocumentFolderRules.PRESCRIPTIONS -> t("Prescription", "पर्चा")
        DocumentFolderRules.DISCHARGE -> t("Discharge summary", "डिस्चार्ज सारांश")
        else -> t("Other", "अन्य")
    }
    // The same colour as the folder's tile on the Documents screen.
    val tone = documentFolderTone(folder)
    DocumentAskPill(label, tone.soft, Modifier.testTag("document-ask-type-${folder ?: DocumentFolderRules.OTHER}"), ink = tone.accent)
}

@Composable private fun DocumentAskPill(text: String, color: Color, modifier: Modifier = Modifier, ink: Color = DoctorCardInk) {
    Surface(shape = RoundedCornerShape(50), color = color, contentColor = ink, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The matched words in bold on a light gold background, so the eye finds them in a long line. */
private fun marked(text: String, marks: List<IntRange>) = buildAnnotatedString {
    append(text); marks.filter { it.first >= 0 && it.last < text.length }.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = Gold.copy(alpha = 0.55f)), it.first, it.last + 1) }
}
/** yyyymmdd as dd/mm/yyyy, the way Indian reports print it; null when unknown. */
private fun dayText(day: Int): String? = if (day <= 0) null else "%02d/%02d/%04d".format(day % 100, day / 100 % 100, day / 10000)
