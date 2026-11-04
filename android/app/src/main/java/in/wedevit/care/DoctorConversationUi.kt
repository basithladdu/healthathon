@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import android.app.DatePickerDialog
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private fun conversationDate(at: Long) = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

/** Presentation only: the original model remains responsible for consent, saving and review. */
@Composable fun DoctorConversations(vm: CareModel, canEdit: Boolean, onRead: (CareVersion) -> Unit, onRequestConversation: () -> Unit, onInteractionActiveChange: (Boolean) -> Unit, onHeaderBackChange: ((() -> Unit)?) -> Unit = {}, onCaptureHeroChange: (Boolean) -> Unit = {}) { val t = careText();
    val person by vm.selected.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val archives by vm.conversationArchives.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable(person) { mutableStateOf(false) }
    var destination by rememberSaveable(person) { mutableStateOf("landing") }
    var interaction by remember { mutableStateOf(false) }
    val editorState = rememberSaveableStateHolder()
    var doctorFilter by rememberSaveable(person) { mutableStateOf("") }
    var dateFilter by rememberSaveable(person) { mutableStateOf("") }
    var doctorMenu by remember { mutableStateOf(false) }
    var archiveDetail by remember(person) {mutableStateOf<DoctorConversationArchive?>(null)}
    // Goals-of-care discussions from the plan's talk (3 Nov meeting: each one in the legal form's layout, Sections I to IV).
    val gocs = rememberDoctorGocDiscussions(vm, person)
    var openGoc by rememberSaveable(person) { mutableStateOf("") }
    val context = LocalContext.current
    val own = versions.filter { it.personId == person }.sortedByDescending { it.published }
    val headerCallback by rememberUpdatedState(onHeaderBackChange)
    DisposableEffect(editing, destination, archiveDetail?.id, recording, interaction, openGoc) {
        headerCallback(if(openGoc.isNotEmpty()) ({openGoc=""}) else if(archiveDetail!=null) ({archiveDetail=null}) else if (editing && !recording && !interaction) ({ editing = false }) else if (!editing && destination != "landing") ({ destination = "landing" }) else null)
        onDispose { headerCallback(null) }
    }
    if (editing) {
        BackHandler(!recording && !interaction) { editing = false }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { editorState.SaveableStateProvider("$person:conversation-editor") { Talk(vm, canEdit, onRead, onRequestConversation, { active -> interaction = active; onInteractionActiveChange(active) }, doctorPresentation = true,
                onDoctorBack = { editing = false }, onDoctorCaptureChange = onCaptureHeroChange,
                onDoctorStartNew = { vm.startNewDoctorConversation(doctorConversationIdentity(vm.accountNamespace,draft)) {} }) } }
        }
        return
    }
    gocs.firstOrNull { it.id == openGoc }?.let { open ->
        BackHandler { openGoc = "" }
        DoctorGocFormPage(open) { openGoc = "" }
        return
    }
    if (destination == "landing") {
        DoctorConversationLanding(canEdit && !busy && draft.personId == person,
            onRecent = { destination = "recent" },
            onRecord = { vm.prepareDoctorConversation(); editing = true },
            onHistory = { destination = "history" },
            recentPreview = own.firstOrNull()?.let { t("Version ${it.version}", "संस्करण ${it.version}") + " \u00B7 ${conversationDate(it.published)}" },
            archivedCount = own.size + archives.count { it.personId == person }) {
            if (gocs.isNotEmpty() || own.isNotEmpty()) item(key = "goc-heading") { SectionTitle(t("Goals-of-care discussions", "देखभाल के लक्ष्यों पर बातचीत")) }
            items(gocs, key = { "goc:${it.id}" }) { d -> DoctorGocDiscussionRow(d) { openGoc = d.id } }
            items(own, key = { "note:${it.id}" }) { note -> DoctorNoteDiscussionRow(note) { onRead(note) } }
        }
        return
    }
    BackHandler { destination = "landing" }
    archiveDetail?.takeIf {it.personId==person}?.let { saved -> AlertDialog(onDismissRequest={archiveDetail=null},title={Text(t("Archived draft · not published", "सँभाला गया ड्राफ़्ट · प्रकाशित नहीं"))},text={DoctorArchiveContent(saved)},confirmButton={TextButton(onClick={archiveDetail=null}){Text(t("Close", "बंद करें"))}}) }
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-conversations"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = { destination = "landing" }, colors = ButtonDefaults.textButtonColors(contentColor = RustText), modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text(t("Back to conversations", "बातचीत पर वापस"), fontSize = 16.sp) } }
        if (destination == "recent") {
            item { Heading(t("Recent conversation", "हाल की बातचीत"), 28) }
            if (own.isEmpty()) item { DoctorEmptyState(Icons.Default.Forum, t("Nothing saved yet", "अभी कुछ सहेजा नहीं")) }
            else item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { DoctorConversationCard(own.first(), onRead); DoctorSavedConversationContext(own.first()) } }
        } else {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = Rust); SectionTitle(t("Conversation history", "बातचीत का इतिहास")) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                OutlinedButton(onClick = { doctorMenu = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(doctorFilter.ifBlank { t("All recorded doctors", "सभी दर्ज डॉक्टर") }) }
                DropdownMenu(doctorMenu, { doctorMenu = false }) {
                    DropdownMenuItem(text = { Text(t("All recorded doctors", "सभी दर्ज डॉक्टर")) }, onClick = { doctorFilter = ""; doctorMenu = false })
                    own.map { it.doctor }.filter(String::isNotBlank).distinct().sorted().forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { doctorFilter = name; doctorMenu = false }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { val initial = dateFilter.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> dateFilter = LocalDate.of(y, m + 1, d).toString() }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (dateFilter.isBlank()) t("Choose a day", "दिन चुनें") else LocalDate.parse(dateFilter).format(DateTimeFormatter.ofPattern("d MMM yyyy"))) }
                if (dateFilter.isNotBlank()) TextButton(onClick = { dateFilter = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Clear date", "तारीख़ हटाएँ")) }
            }
            }
        }
        val filtered = own.filter { (doctorFilter.isBlank() || it.doctor == doctorFilter) && (dateFilter.isBlank() || Instant.ofEpochMilli(it.published).atZone(ZoneId.systemDefault()).toLocalDate().toString() == dateFilter) }
        if (filtered.isEmpty() && own.isNotEmpty()) item { DoctorEmptyState(Icons.Default.History, t("No conversation matches these filters.", "इन फ़िल्टरों से कोई बातचीत नहीं मिली।")) }
        if (own.isEmpty()) item { DoctorEmptyState(Icons.Default.Forum, t("Nothing saved yet", "अभी कुछ सहेजा नहीं")) }
        items(filtered, key = { it.id }) { DoctorConversationCard(it, onRead) }
        item { Spacer(Modifier.height(12.dp)); SectionTitle(t("Archived drafts · not published", "सँभाले गए ड्राफ़्ट · प्रकाशित नहीं")) }
        val ownArchives=archives.filter {it.personId==person}
        if(ownArchives.isEmpty())item { DoctorEmptyState(Icons.Default.Inventory2, t("No archived drafts.", "कोई सँभाला ड्राफ़्ट नहीं।")) }
        items(ownArchives,key={"archive:${it.id}"}) { saved -> DoctorListRow(Icons.Default.Inventory2, t("Unfinished conversation draft", "अधूरा बातचीत ड्राफ़्ट"), conversationDate(saved.archivedAt), onClick = { archiveDetail = saved }) }
        }
    }
}

@Composable private fun DoctorArchiveContent(saved:DoctorConversationArchive) { val t = careText();
    val draft=remember(saved.id,saved.draftJson) {DoctorConversationContext.decodeArchive(saved)}
    Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(t("Archived ${conversationDate(saved.archivedAt)}", "सँभाला गया ${conversationDate(saved.archivedAt)}"))
        if(draft==null) Text(t("This retained draft needs a newer app to read its context. Nothing has been replaced or published.", "इस रखे ड्राफ़्ट का संदर्भ पढ़ने के लिए नया ऐप चाहिए। कुछ बदला या प्रकाशित नहीं हुआ।"))
        else {
            NoteRules.labels.zip(NoteRules.values(draft)).forEach {(label,value)->Text(label,fontWeight=FontWeight.Bold);Text(value.ifBlank {t("Not recorded", "दर्ज नहीं")})}
            if(draft.contextJson.isNotBlank()) {
                Text(t("Saved goals · read-only", "सहेजे लक्ष्य · सिर्फ़ पढ़ने के लिए"),style=MaterialTheme.typography.titleMedium)
                doctorContextGoals(draft).forEach {goal->Text(goal.key.replace('_',' '),fontWeight=FontWeight.Bold);Text("${goal.status.replace('_',' ')} · ${goal.origin}");Text(goal.answer.ifBlank {t("No answer recorded", "कोई जवाब दर्ज नहीं")});goal.evidence.forEach {Text(t("Original excerpt: ${it.quote}", "मूल अंश: ${it.quote}"))}}
            }
            Text(t("Original source", "मूल स्रोत"),fontWeight=FontWeight.Bold);Text(draft.source.ifBlank {t("No source text recorded", "कोई स्रोत दर्ज नहीं")})
        }
    }
}

@Composable fun DoctorConversationAudience(vm: CareModel, draft: Conversation, onBusy: (Boolean) -> Unit) { val t = careText();
    val cloud=vm.cloud
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val modelBusy by vm.busy.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    val expected=doctorConversationIdentity(vm.accountNamespace,draft)
    var working by remember(draft.personId,draft.conversationId) {mutableStateOf(false)}
    var error by remember(draft.personId,draft.conversationId) {mutableStateOf<String?>(null)}
    var audience by remember(draft.personId,draft.conversationId) {mutableStateOf<ConversationAudience?>(null)}
    var candidates by remember(draft.personId,draft.conversationId) {mutableStateOf(emptyList<ConversationAudienceCandidate>())}
    var selected by remember(draft.personId,draft.conversationId) {mutableStateOf(emptySet<String>())}
    val callback by rememberUpdatedState(onBusy)
    SideEffect { callback(working) }
    DisposableEffect(Unit) {onDispose {callback(false)}}
    fun same()=vm.selected.value==expected.personId && vm.accountNamespace==expected.accountId && vm.cloud?.session?.value?.userId==expected.accountId && doctorGoalContextMatches(draft,vm.draft.value) && vm.isOwner.value
    Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DoctorInfoRow(t("Family access to this conversation", "इस बातचीत तक परिवार की पहुँच"), t("Choose who can see this, if anyone.", "चुनें कि इसे कौन देख सकता है, अगर कोई।"), t("The owner chooses existing invited family accounts. No one is added automatically, and this does not grant access to another conversation.", "मालिक पहले से बुलाए परिवार के खाते चुनते हैं। कोई अपने-आप नहीं जुड़ता, और इससे किसी और बातचीत की पहुँच नहीं मिलती।"), title = true)
        if(cloud==null) DoctorInfoRow(t("Online family access is unavailable.", "ऑनलाइन परिवार की पहुँच उपलब्ध नहीं है।"), "", t("Online family access is unavailable for device-local or demo conversations.", "फ़ोन वाली या डेमो बातचीत के लिए ऑनलाइन परिवार की पहुँच उपलब्ध नहीं है।"))
        else if(!owner) DoctorInfoRow(t("Only the owner can approve family access.", "परिवार की पहुँच सिर्फ़ मालिक मंज़ूर कर सकते हैं।"), "", t("Only this care profile's owner can approve the audience. This account cannot grant family access.", "देखने वालों को सिर्फ़ इस देखभाल प्रोफ़ाइल के मालिक मंज़ूर कर सकते हैं। यह खाता परिवार को पहुँच नहीं दे सकता।"))
        else {
            OutlinedButton(onClick={
                error=null
                vm.saveDoctorSharedDraftForAudience(expected) {
                    working=true
                    scope.launch {try {
                        check(same()) {t("The conversation or account changed.", "बातचीत या खाता बदल गया।")}
                        val people=cloud.conversationCandidates(expected.personId,expected.accountId)
                        val saved=cloud.conversationAudience(expected.personId,expected.conversationId,expected.accountId)
                        check(same()) {t("The conversation or account changed.", "बातचीत या खाता बदल गया।")}
                        candidates=people;audience=saved;selected=saved?.memberIds.orEmpty()
                    }catch(e:Exception){if(e is CancellationException)throw e;error=e.message?:t("Sharing could not be loaded. No access changed.", "साझा करने की जानकारी नहीं खुली। कोई पहुँच नहीं बदली।")}finally{working=false}}
                }
            },enabled=!modelBusy&&!working,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(t("Save draft and choose family access", "ड्राफ़्ट सहेजें और परिवार की पहुँच चुनें"))}
            audience?.let { previous ->
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(candidates.isEmpty()) Text(t("No eligible invited family accounts. You can approve an owner-only conversation.", "बुलाए गए परिवार का कोई योग्य खाता नहीं। आप सिर्फ़ मालिक वाली बातचीत मंज़ूर कर सकते हैं।"))
                    candidates.forEach { member -> CheckLine(member.userId in selected,{checked->selected=if(checked)selected+member.userId else selected-member.userId},member.label) }
                    Button(onClick={working=true;error=null;scope.launch {try {check(same());val saved=cloud.saveConversationAudience(expected.personId,expected.conversationId,previous,selected,true,expected.accountId);check(same());audience=saved}catch(e:Exception){if(e is CancellationException)throw e;error=e.message?:t("Access was not changed. Refresh before trying again.", "पहुँच नहीं बदली। फिर कोशिश से पहले ताज़ा करें।")}finally{working=false}}},enabled=!working&&!modelBusy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(t("Approve only these accounts", "सिर्फ़ इन खातों को मंज़ूरी दें"))}
                    if(previous.approved) Text(t("Audience approval saved. Publication still requires the separate clinician review.", "देखने वालों की मंज़ूरी सहेजी गई। प्रकाशन के लिए डॉक्टर की अलग समीक्षा अब भी ज़रूरी है।"),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        error?.let {Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())}
    } }
}

private fun doctorSectionLabel(key: String) = when (key) { "patient_priorities" -> "What matters"; "participants" -> "Who was there"; "understanding_questions" -> "What we discussed"; "concerns_uncertainty" -> "Still undecided"; else -> "Next steps" }
private val doctorSectionOrder = listOf("patient_priorities", "participants", "understanding_questions", "concerns_uncertainty", "next_steps")

@Composable private fun DoctorChip(text: String, fg: Color, bg: Color) { Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) { Text(text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) } }
@Composable private fun DoctorStatusChip(status: String) { val t = careText();
    when (status) {
        "discussed" -> DoctorChip(t("Discussed", "बात हुई"), DoctorStatusGood, DoctorStatusGoodBg)
        "not_recorded" -> DoctorChip(t("Not recorded", "दर्ज नहीं"), DoctorStatusRest, DoctorStatusRestBg)
        "undecided" -> DoctorChip("Undecided", DoctorStatusWarn, DoctorStatusWarnBg)
        "deferred" -> DoctorChip(t("Deferred", "टाला गया"), DoctorStatusWarn, DoctorStatusWarnBg)
        else -> DoctorChip(t("Needs review", "जाँच बाक़ी"), DoctorStatusWarn, DoctorStatusWarnBg)
    }
}
@Composable fun DoctorFootnote(text: String) { Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardFaint) }

/** Friendly empty list: an icon in a soft circle and one line. */
@Composable private fun DoctorEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(DoctorCardConversation, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = RustText, modifier = Modifier.size(22.dp)) }
            Text(text, fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardSub, modifier = Modifier.weight(1f))
        }
    }
}

/** One tappable list row: icon tile, title, one line under it, chevron. */
@Composable private fun DoctorListRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(DoctorCardConversation, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = RustText, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontFamily = SaharaTitleFont, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                if (sub.isNotBlank()) Text(sub, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
            }
            Icon(Icons.Default.ChevronRight, null, tint = DoctorCardSub)
        }
    }
}

@Composable private fun DoctorQuote(text: String) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 4.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(DoctorAccent, RoundedCornerShape(2.dp)))
        Text("\u201C$text\u201D", fontSize = 15.sp, lineHeight = 23.sp, fontStyle = FontStyle.Italic, color = DoctorCardInk, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable fun DoctorSavedConversationContext(note: CareVersion) { val t = careText();
    if (note.hashScheme != DoctorConversationContext.HASH_SCHEME) return
    val goals = remember(note.id, note.contextJson) { doctorContextGoals(Conversation(note.personId, source = note.source, contextJson = note.contextJson)).associateBy { it.key } }
    if (goals.isEmpty()) {
        Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
            Text(runCatching { DoctorConversationContext.readable(note) }.getOrElse { t("This saved context needs an updated app. The original note has been retained.", "इस सहेजे संदर्भ के लिए नया ऐप चाहिए। मूल नोट रखा गया है।") }.ifBlank { t("No saved goals were recorded for this version.", "इस संस्करण के लिए कोई लक्ष्य सहेजा नहीं गया।") }, fontSize = 16.sp, lineHeight = 24.sp, color = DoctorCardInk, modifier = Modifier.padding(16.dp))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        doctorSectionOrder.mapNotNull { goals[it] }.forEach { goal ->
            Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(doctorSectionLabel(goal.key), fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f))
                        DoctorStatusChip(goal.status)
                    }
                    Text(goal.answer.ifBlank { t("Nothing was recorded for this section.", "इस हिस्से के लिए कुछ दर्ज नहीं।") }, fontSize = 16.sp, lineHeight = 24.sp, color = if (goal.answer.isBlank()) DoctorCardSub else DoctorCardInk)
                    goal.evidence.forEach { DoctorQuote(it.quote) }
                    if (CarriedForward.isOrigin(goal.origin)) Text(t("Carried forward from version ${CarriedForward.version(goal.origin)}, not discussed in this conversation", "संस्करण ${CarriedForward.version(goal.origin)} से आगे लाया गया, इस बातचीत में चर्चा नहीं हुई"), fontSize = 14.sp, lineHeight = 20.sp, color = RustText)
                }
            }
        }
    }
}
@Composable fun DoctorRecorderLabel(vm:CareModel,draft:Conversation) { val t = careText();
    val label=runCatching {JSONObject(draft.contextJson).getString("recorderLabel")}.getOrDefault("")
    val expected=doctorConversationIdentity(vm.accountNamespace,draft)
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {CareField(label,{vm.updateDoctorRecorderLabel(it.take(120),expected)},t("Recorded by (self-entered name)", "दर्ज करने वाले (ख़ुद लिखा नाम)"),singleLine=true);DoctorInfoRow(t("Your name is only a label.", "आपका नाम सिर्फ़ पहचान के लिए है।"), "", t("This name is a label, not verified professional identity. An account identifier is recorded only when known at capture.", "यह नाम सिर्फ़ पहचान है, पुष्ट पेशेवर पहचान नहीं। खाते की पहचान तभी दर्ज होती है जब रिकॉर्ड करते समय पता हो।"))}
}

/** One-line summary with an (i) button that reveals the full explanation on demand; nothing is removed, only collapsed. */
@Composable private fun DoctorInfoRow(summary: String, hint: String, detail: String, title: Boolean = false) { val t = careText();
    var open by rememberSaveable(summary) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (title) Text(summary, fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk) else Text(summary, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                if (hint.isNotBlank()) Text(hint, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
            }
            IconButton(onClick = { open = !open }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Info, if (open) t("Hide details: $summary", "जानकारी छिपाएँ: $summary") else t("Show details: $summary", "जानकारी देखें: $summary"), tint = DoctorAccent) }
        }
        if (open) Text(detail, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable fun DoctorSavedLocalAudio(vm: CareModel,note: CareVersion) { val t = careText();
    val person by vm.selected.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val session=vm.cloud?.session?.collectAsStateWithLifecycle()?.value
    val account=vm.accountNamespace;val storage=vm.storageNamespace;val sessionId=session?.userId.orEmpty()
    val scope=rememberCoroutineScope()
    var file by remember(vm,storage,note.id,note.contentHash) {mutableStateOf<java.io.File?>(null)}
    var checking by remember(vm,storage,note.id) {mutableStateOf(false)}
    LaunchedEffect(vm,account,storage,sessionId,note.id,note.contentHash,person,versions) {file=null;if(note.hashScheme==DoctorConversationContext.HASH_SCHEME)file=vm.doctorAudioFor(note)}
    if(note.hashScheme!=DoctorConversationContext.HASH_SCHEME)return
    val original=file
    if(original==null) return
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val player=remember(vm,account,storage,sessionId,original.absolutePath) {runCatching {MediaPlayer.create(context,Uri.fromFile(original))}.getOrNull()}
    var playing by remember(vm,account,storage,sessionId,original.absolutePath) {mutableStateOf(false)}
    DisposableEffect(player,lifecycle) {
        player?.setOnCompletionListener {playing=false}
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP){player?.pause();playing=false}}
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer);player?.release()}
    }
    OutlinedButton(onClick={
        if(playing){runCatching {player?.pause()};playing=false}
        else {checking=true;scope.launch {try {
            val verified=vm.doctorAudioFor(note)
            val identity=vm.accountNamespace==account && vm.storageNamespace==storage && vm.cloud?.session?.value?.userId.orEmpty()==sessionId
            if(identity && verified!=null && verified.canonicalFile==original.canonicalFile && file?.canonicalFile==original.canonicalFile) {runCatching {player?.start()};playing=runCatching {player?.isPlaying==true}.getOrDefault(false)}
            else {runCatching {player?.pause()};playing=false;file=null}
        }finally{checking=false}}}
    },enabled=player!=null&&!checking,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(if(checking)t("Checking exact audio access…", "ऑडियो की पहुँच जाँच रहे हैं…") else if(playing)t("Pause device-local audio", "फ़ोन का ऑडियो रोकें") else t("Play exact device-local audio", "फ़ोन का ऑडियो चलाएँ"))}
}

@Composable fun DoctorConversationLanding(canRecord: Boolean, onRecent: () -> Unit, onRecord: () -> Unit, onHistory: () -> Unit, recentPreview: String? = null, archivedCount: Int = 0, discussions: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {}) { val t = careText();
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-conversations"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading(t("Goals of care", "देखभाल के लक्ष्य"), 30) }
        val titles = listOf(t("Recent conversation", "हाल की बातचीत"), t("Record conversation", "बातचीत रिकॉर्ड करें"), t("Conversation history", "बातचीत का इतिहास"))
        val arts = listOf(R.drawable.conversation_recent_art, R.drawable.conversation_record_art, R.drawable.conversation_history_art)
        val previews = listOf(recentPreview ?: t("Nothing saved yet", "अभी कुछ सहेजा नहीं"), if (canRecord) t("Needs everyone's agreement first", "पहले सबकी सहमति चाहिए") else t("Recording is not available to you", "रिकॉर्डिंग आपके लिए उपलब्ध नहीं"), if (archivedCount == 0) t("No earlier conversations", "कोई पुरानी बातचीत नहीं") else if (archivedCount == 1) t("1 saved conversation", "1 सहेजी बातचीत") else t("$archivedCount saved conversations", "$archivedCount सहेजी बातचीत"))
        // Record conversation is the primary action (filled accent, listed first); the other two are lighter.
        items(listOf(1, 0, 2), key = { it }) { index ->
            val primary = index == 1
            val ink = if (primary) Color.White else DoctorCardInk
            val sub = if (primary) Color.White else DoctorCardSub
            Surface(onClick = when (index) { 0 -> onRecent; 1 -> onRecord; else -> onHistory }, enabled = index != 1 || canRecord, color = if (primary) DoctorAccent else DoctorCardConversation, contentColor = ink, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().heightIn(min = if (primary) 172.dp else 120.dp).testTag(if (index == 1) "doctor-start-conversation" else "doctor-conversation-card-$index")) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                    Image(saharaImage(arts[index], 120.dp), contentDescription = null, contentScale = ContentScale.Fit, alpha = if (primary) .5f else .4f, modifier = Modifier.align(Alignment.BottomEnd).width(maxWidth * .30f).height(if (primary) 120.dp else 80.dp).testTag("doctor-conversation-art-$index"))
                    Column(Modifier.fillMaxWidth(.67f).testTag("doctor-conversation-copy-$index"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(titles[index], fontFamily = SaharaTitleFont, fontSize = if (primary) 21.sp else 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = ink)
                        Text(previews[index], fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = sub)
                    }
                }
            }
        }
        discussions()
    }
}

/** A saved conversation note in the Goals of care list; it opens in the note reader, which shows the form's layout. */
@Composable private fun DoctorNoteDiscussionRow(note: CareVersion, onOpen: () -> Unit) { val t = careText()
    DoctorListRow(Icons.Default.Description, t("Version ", "संस्करण ") + note.version + " · " + conversationDate(note.published), ReviewWorkflow.label(note), Modifier.testTag("doctor-goc-note-${note.id}"), onOpen)
}

internal fun doctorContextGoals(draft: Conversation): List<DoctorGoalContext> = runCatching {
    DoctorConversationContext.validate(draft.source,draft.contextJson)
    val rows = JSONObject(draft.contextJson).getJSONArray("goals")
    (0 until rows.length()).map { i -> val g = rows.getJSONObject(i); val ev = g.getJSONArray("evidence"); DoctorGoalContext(g.getString("key"), g.getString("status"), g.getString("origin"), g.getString("answer"), (0 until ev.length()).map { n -> ev.getJSONObject(n).let { DoctorSourceEvidence(it.getInt("start"),it.getInt("end"),it.getString("quote")) } }) }
}.getOrDefault(emptyList())

/** Distinct amber marker for carried text; editing the field below replaces it with a hand-written answer. */
@Composable fun DoctorCarriedForwardBanner(goal: DoctorGoalContext, onConfirm: () -> Unit) { val t = careText();
    val confirmed = !CarriedForward.unconfirmed(goal)
    Surface(color = if (confirmed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("carriedForward_${goal.key}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (confirmed) t("Carried forward from version ${CarriedForward.version(goal.origin)} · confirmed by you", "संस्करण ${CarriedForward.version(goal.origin)} से आगे लाया गया · आपने पुष्टि की") else t("Carried forward from version ${CarriedForward.version(goal.origin)} · confirm this still applies", "संस्करण ${CarriedForward.version(goal.origin)} से आगे लाया गया · पुष्टि करें कि यह अब भी लागू है"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text(t("Not discussed in this conversation. Edit the text if anything changed.", "इस बातचीत में चर्चा नहीं हुई। कुछ बदला हो तो लिखाई बदलें।"), style = MaterialTheme.typography.bodySmall)
            if (!confirmed) Button(onClick = onConfirm, modifier = Modifier.testTag("confirmCarried_${goal.key}")) { Text(t("Still applies", "अब भी लागू है")) }
        }
    }
}

@Composable fun DoctorGoalsReview(draft: Conversation, onGoal: (DoctorGoalContext) -> Unit) { val t = careText();
    val labels = listOf(t("Patient priorities", "मरीज़ की प्राथमिकताएँ"), t("Understanding and questions", "समझ और सवाल"), t("Who should participate", "किसे शामिल होना चाहिए"), t("Concerns and uncertainty", "चिंताएँ और अनिश्चितता"), t("Next steps and follow-up", "अगले क़दम और फ़ॉलो-अप"))
    val goals = doctorContextGoals(draft)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Goals of care", "देखभाल के लक्ष्य"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            DoctorInfoIcon(t("Goals of care", "देखभाल के लक्ष्य"), t("Choose each status yourself. An answer or AI draft does not prove the topic was discussed.", "हर स्थिति ख़ुद चुनें। किसी जवाब या AI ड्राफ़्ट से यह साबित नहीं होता कि विषय पर बात हुई।"), "doctor-goals-review-info")
        }
        if (goals.isEmpty()) Text(t("This review context is unavailable or needs a newer app. Its original text has been retained; nothing was replaced.", "यह जाँच का संदर्भ उपलब्ध नहीं है या नया ऐप चाहिए। मूल लिखाई रखी गई है; कुछ नहीं बदला।"), color = MaterialTheme.colorScheme.error)
        goals.forEach { goal ->
            var expanded by rememberSaveable(goal.key) { mutableStateOf(true) }
            var menu by remember { mutableStateOf(false) }
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(labels[DoctorConversationContext.keys.indexOf(goal.key)], Modifier.weight(1f)); Text(if (expanded) "Hide" else "Expand") }
                Box { OutlinedButton(onClick = { menu = true }) { Text(goal.status.replace('_',' ')) }; DropdownMenu(menu, { menu = false }) { DoctorConversationContext.statuses.forEach { status -> DropdownMenuItem(text = { Text(status.replace('_',' ')) }, onClick = { onGoal(goal.copy(status = status, answer = if(status=="not_recorded") "" else goal.answer, evidence = if(status=="not_recorded") emptyList() else goal.evidence)); menu = false }) } } }
                if (expanded) {
                    if (CarriedForward.isOrigin(goal.origin)) DoctorCarriedForwardBanner(goal, onConfirm = { onGoal(CarriedForward.confirm(goal)) })
                    CareField(goal.answer, { value -> onGoal(goal.copy(answer = value, status = if(value.isBlank()) "not_recorded" else "needs_review", origin = "manual", evidence = emptyList())) }, labels[DoctorConversationContext.keys.indexOf(goal.key)], minLines = 2)
                    Text(t("Origin: ", "स्रोत: ") + "${if (CarriedForward.isOrigin(goal.origin)) t("carried forward from version ${CarriedForward.version(goal.origin)}", "संस्करण ${CarriedForward.version(goal.origin)} से आगे लाया गया") else goal.origin}. ${if(goal.evidence.isEmpty()) t("No exact source excerpt attached.", "कोई मूल अंश नहीं जुड़ा।") else t("${goal.evidence.size} exact source excerpts attached.", "${goal.evidence.size} मूल अंश जुड़े हैं।")}", style = MaterialTheme.typography.bodySmall)
                    goal.evidence.forEach { Text(it.quote, style = MaterialTheme.typography.bodyMedium) }
                }
            } }
        }
    }
}

internal fun doctorGoalContextMatches(expected: Conversation,current: Conversation): Boolean = expected.personId==current.personId && expected.conversationId.isNotBlank() && expected.conversationId==current.conversationId && DoctorConversationContext.sha256(expected.source.toByteArray(Charsets.UTF_8))==DoctorConversationContext.sha256(current.source.toByteArray(Charsets.UTF_8))
internal fun doctorConversationIdentity(account: String,draft: Conversation) = DoctorConversationIdentity(account,draft.personId,draft.conversationId,DoctorConversationContext.sha256(draft.source.toByteArray(Charsets.UTF_8)))
internal fun updateDoctorGoalFromUi(vm: CareModel,expected: Conversation,goal: DoctorGoalContext) {
    if(vm.selected.value==expected.personId && doctorGoalContextMatches(expected,vm.draft.value)) vm.updateDoctorGoal(goal,doctorConversationIdentity(vm.accountNamespace,expected))
    else vm.message.value="The conversation changed. Reopen this goal before editing."
}
internal fun assignDoctorSource(vm: CareModel, expected: Conversation, index: Int, line: CareSourceLine) {
    val current = vm.draft.value
    if(vm.selected.value!=expected.personId || !doctorGoalContextMatches(expected,current)) {vm.message.value="The original conversation changed. Choose its source words again.";return}
    val key = listOf("patient_priorities", "participants", "", "understanding_questions", "next_steps")[index]
    if (key.isBlank()) { vm.edit(CareDraftAssist.assign(current,index,line)); return }
    val goal = doctorContextGoals(current).firstOrNull {it.key==key} ?: return
    val before = current.source.substring(0,line.start).toByteArray(Charsets.UTF_8).size
    val quote = current.source.substring(line.start,line.end)
    val evidence = DoctorSourceEvidence(before,before+quote.toByteArray(Charsets.UTF_8).size,quote)
    updateDoctorGoalFromUi(vm,expected,goal.copy(answer = listOf(goal.answer,line.words).filter(String::isNotBlank).joinToString("\n"), status = "needs_review", origin = "source", evidence = (goal.evidence+evidence).distinct()))
}

@Composable private fun DoctorConversationCard(note: CareVersion, onRead: (CareVersion) -> Unit) { val t = careText();
    Surface(color = DoctorCardConversation, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Version ${note.version}", fontFamily = SaharaTitleFont, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            Text("${conversationDate(note.published)} \u00B7 ${note.doctor.ifBlank { t("Doctor name not recorded", "डॉक्टर का नाम दर्ज नहीं") }}", fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
        }
        Text(note.topics.ifBlank { note.priorities.ifBlank { t("Read the saved conversation", "सहेजी बातचीत पढ़ें") } }, fontSize = 16.sp, lineHeight = 24.sp, color = DoctorCardInk, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(ReviewWorkflow.label(note), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
        Button(onClick = { onRead(note) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Read full note and original words", "पूरा नोट और मूल शब्द पढ़ें")) }
    } }
}

@Composable fun DoctorCaptureHeader(mode: String, onMode: (String) -> Unit, recording: Boolean, dictating: Boolean, level: Float, seconds: Long, demo: Boolean, onBack: (() -> Unit)? = null, backEnabled: Boolean = true, localRecordingAllowed: Boolean = !demo, testRecord: Boolean = false) { val t = careText();
    var menu by remember { mutableStateOf(false) }
    // One quiet header: title, live/idle state and mode. The single record control, consent and transcript live in the panel below.
    Surface(color = DoctorCardConversation, contentColor = DoctorCardInk, shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp), modifier = Modifier.fillMaxWidth().testTag("doctor-capture-hero")) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) TextButton(onClick = onBack, enabled = backEnabled, colors = ButtonDefaults.textButtonColors(contentColor = DoctorAccent), contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-capture-back")) { Text(t("Back to conversations", "बातचीत पर वापस"), fontSize = 14.sp) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("Conversation", "बातचीत"), fontFamily = SaharaTitleFont, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(if (recording) DoctorAccent else DoctorCardFaint, CircleShape))
                        Text(if (recording) "Recording ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else if (dictating) t("Dictation in progress", "बोलकर लिखना जारी है") else t("$mode mode \u00B7 ready when you are", "${when (mode) { "Record" -> "रिकॉर्ड"; "Dictate" -> "बोलकर लिखना"; "Type" -> "टाइप"; else -> mode }} \u00B7 जब आप तैयार हों"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                    }
                }
                Box { IconButton(onClick = { menu = true }, enabled = !recording && !dictating, modifier = Modifier.size(48.dp).testTag("doctor-capture-mode")) { Icon(Icons.Default.MoreVert, t("Choose Record, Dictate or Type", "रिकॉर्ड, बोलकर लिखें या टाइप चुनें"), tint = DoctorCardInk) }; DropdownMenu(menu, { menu = false }) { listOf("Record", "Dictate", "Type").forEach { value -> val available = when(value) { "Record" -> localRecordingAllowed; "Dictate" -> !demo; else -> true }; DropdownMenuItem(text = { Text(value + if (!available) " \u00B7 unavailable" else "") }, enabled = available, onClick = { onMode(value); menu = false }) } } }
            }
        }
    }
}

@Composable fun DoctorGoalEditor(label: String, value: String, source: String, onValue: (String) -> Unit) { val t = careText();
    var expanded by rememberSaveable(label) { mutableStateOf(value.isBlank()) }
    val lines = value.lines().map(String::trim).filter(String::isNotBlank)
    val quoted = lines.isNotEmpty() && lines.all { source.contains(it) }
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label, modifier = Modifier.weight(1f)); Text(if (expanded) "Hide" else "Expand") }
        Text(if (value.isBlank()) t("Not recorded", "दर्ज नहीं") else if (quoted) t("Exact words found in the source · check context", "स्रोत में यही शब्द मिले · संदर्भ जाँचें") else t("Manually entered · verify against the source", "हाथ से लिखा · स्रोत से मिलाएँ"), style = MaterialTheme.typography.labelMedium, color = Rust)
        if (expanded) { CareField(value, onValue, label, minLines = 2); CareSourceLinks(source, value) }
        else Text(value.ifBlank { t("Not recorded", "दर्ज नहीं") })
    } }
}
