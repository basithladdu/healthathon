@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate

private val PlanAlert = Color(0xFF9E3B23)
internal const val ED_VIEW_DETAIL = "ED Quick View"
private const val CARD_VIEW_DETAIL = "wallet card"
private fun planPdfName(version: Int) = "sahara-plan-v$version.pdf"
/** Same content, ignoring when it was last saved. */
private fun samePlan(a: EctprRecord, b: EctprRecord) = a.copy(updatedAt = "") == b.copy(updatedAt = "")
internal fun planNow(): String = Instant.now().toString()

/** Which wizard step a rule's field belongs to, so "Next" checks only what has been asked so far. */
internal fun planStepOf(field: String): Int = when {
    field == "clinical.icdDeactivation" -> 5
    field.startsWith("patient") || field.startsWith("clinical") || field == "createdInEd" -> 0
    field.startsWith("decisionMakers") -> 1
    field.startsWith("capacity") || field.startsWith("existingDirective") -> 2
    field.startsWith("prognosticUnderstanding") || field.startsWith("surrogateDeclaration") -> 3
    field.startsWith("values") -> 4
    field.startsWith("choices") -> 5
    field == "withdrawal" -> 6
    field == "reviewDue" -> 7
    else -> 8
}

/**
 * Emergency care plan (ECTPR) and living will (AMD), offline on this phone. Reached from Today and More (family and patient)
 * and from the doctor's patient tools. Pages: home, plan wizard, ED Quick View, wallet card, living will.
 */
@Composable fun CarePlanScreen(vm: CareModel, clinician: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current; val t = careText(); val hindi = careHindi(); val scope = rememberCoroutineScope()
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.id == selected }
    val store = remember(vm.storageNamespace) { CarePlanStore(context.applicationContext, vm.storageNamespace) }
    var loadProblem by remember(selected) { mutableStateOf(false) }
    var state by remember(selected) { mutableStateOf(runCatching { store.load(selected) }.getOrElse { loadProblem = true; CarePlanState() }) }
    var page by rememberSaveable(selected) { mutableStateOf("home") }
    val actor = remember(clinician) { if (clinician) "Doctor" else vm.viewRole.value }
    val today = LocalDate.now()
    // The practice walkthrough never reaches the card server.
    val online = !vm.demoMode
    val transport = remember { EdCardHttpTransport() }
    fun pushCards() { if (online && selected.isNotBlank()) scope.launch { runCatching { CarePlanCardSync.person(store, selected, transport) } } }
    fun commit(change: (CarePlanState) -> CarePlanState): Boolean = try {
        val next = change(state); store.save(selected, next); state = next
        if (next.tokens.any { it.pending }) pushCards()
        true
    } catch (e: Exception) { vm.message.value = t("Not saved: ", "सहेजा नहीं गया: ") + (e.message ?: ""); false }
    // The queue runs on open and whenever the stored plan changes behind this screen.
    LaunchedEffect(selected) { pushCards(); CarePlanCardSync.changes.collect { if (it == selected) runCatching { store.load(selected) }.onSuccess { fresh -> state = fresh } } }
    fun share(file: File, title: String, type: AuditRecordType, id: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), title))
            commit { CarePlanBook.shared(it, type, id, actor, planNow()) }
        }.onFailure { vm.message.value = t("No app is available to share the PDF.", "PDF साझा करने के लिए कोई ऐप नहीं है।") }
    }
    fun sharePlanPdf() {
        val interp = EdView.forPatient(state.ectprs, selected, today); val r = interp.record ?: return
        scope.launch {
            val amdSummary = AmdLifecycle.statusSummary(state.amds, selected); val card = CardTokens.live(state.tokens, r.id)
            val offline = EdCardOffline.forQr(r, amdSummary, hindi, today)
            val file = runCatching { withContext(Dispatchers.IO) { File(store.folder(selected), planPdfName(r.version)).also { CarePlanPdf.plan(it, r, interp, AmdLifecycle.edSummary(r.existingDirective, amdSummary), card?.url, offline, card?.server == CardServerState.ISSUED, hindi, today) } } }.getOrNull()
            if (file == null) vm.message.value = t("Couldn't make the PDF. Try again.", "PDF नहीं बना। फिर कोशिश करें।")
            else { commit { CarePlanBook.printed(it, AuditRecordType.ECTPR, r.id, actor, planNow()) }; share(file, t("Share the emergency plan", "आपातकालीन योजना साझा करें"), AuditRecordType.ECTPR, r.id) }
        }
    }
    BackHandler(page != "home") { page = "home" }
    if (person == null) { Box(Modifier.fillMaxSize()); return }
    val canManageData = clinician || actor != "Family"
    when (page) {
        "wizard" -> {
            val draft = EctprLifecycle.draft(state.ectprs, selected)
            if (draft == null) { LaunchedEffect(Unit) { page = "home" }; return }
            CompositionLocalProvider(LocalGocFolder provides runCatching { store.folder(selected) }.getOrNull()) { EctprWizard(draft, clinician, canEdit,
                onSave = { r -> commit { CarePlanBook.saveDraft(it, r, actor, planNow(), today) } },
                onSign = { r -> commit { CarePlanBook.sign(CarePlanBook.saveDraft(it, r, actor, planNow(), today), r.id, actor, planNow(), today) }.also { ok -> if (ok) page = "ed" } },
                onClose = { page = "home" }) }
        }
        "pilot" -> PilotLogPage(state, selected, actor, ED_VIEW_DETAIL, onBack = { page = "home" }, commit = ::commit)
        "ed" -> EdQuickViewPage(state, selected, onBack = { page = "home" }, onCard = { page = "card" }, onShare = ::sharePlanPdf,
            onViewed = { id -> commit { CarePlanBook.viewed(it, AuditRecordType.ECTPR, id, actor, planNow(), ED_VIEW_DETAIL) } })
        "card" -> WalletCardPage(state, selected, online, canEdit, onRetry = ::pushCards, onReissue = { commit { CarePlanBook.reissueCard(it, selected, actor, planNow()) } }, onBack = { page = "home" }, onShare = ::sharePlanPdf,
            onViewed = { id -> commit { CarePlanBook.viewed(it, AuditRecordType.CARD, id, actor, planNow(), CARD_VIEW_DETAIL) } })
        "amd" -> LivingWillPage(state, person, store, canEdit, clinician, canManageData, actor, onBack = { page = "home" }, commit = ::commit, onShare = { file, id -> share(file, t("Share the living will", "लिविंग विल साझा करें"), AuditRecordType.AMD, id) })
        else -> CarePlanHome(state, person, clinician, canEdit, loadProblem, store, canManageData, actor, ::commit, transport.takeIf { online }, onBack = onBack, onPage = { page = it },
            onStart = { inEd -> if (commit { CarePlanBook.startPlan(it, selected, actor, planNow(), EctprPatient(name = person.name), inEd).first }) page = "wizard" },
            onReview = { trigger -> if (commit { CarePlanBook.requestReview(it, selected, trigger, actor, planNow(), today).first }) page = "wizard" },
            onWithdraw = { id, reason, by -> if (commit { CarePlanBook.withdraw(it, id, reason, by, actor, planNow()) }) vm.message.value = t("Plan withdrawn. Its wallet card link is switched off.", "योजना वापस ली गई। इसके वॉलेट कार्ड का लिंक बंद हो गया।") },
            onDiscard = { id -> commit { CarePlanBook.discard(it, id, actor, planNow()) } })
    }
}

@Composable internal fun PlanHeader(title: String, onBack: () -> Unit, tag: String) { val t = careText()
    Row(Modifier.fillMaxWidth().testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("$tag-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
        Spacer(Modifier.width(4.dp)); Heading(title, 26)
    }
}

/** Always on: clinical spec §6 / Sahaara §1. */
@Composable internal fun IfInDoubtBox(compact: Boolean = false) { val hindi = careHindi()
    Surface(color = PlanAlert, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("if-in-doubt")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text(IfInDoubt.TITLE.pick(hindi), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (!compact) {
                Text(IfInDoubt.LEAD.pick(hindi), color = Color.White)
                IfInDoubt.CONDITIONS.forEach { Text("• " + it.pick(hindi), color = Color.White, style = MaterialTheme.typography.bodyMedium) }
                Text(IfInDoubt.AFTER.pick(hindi), color = Color.White, fontWeight = FontWeight.SemiBold)
            } else Text(IfInDoubt.CONDITIONS.joinToString(" ") { it.pick(hindi) }, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** V9: printed on every record and shown on every ED view. */
@Composable internal fun CareContinuesBox() { val hindi = careHindi()
    Surface(color = Mint, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("care-continues")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Favorite, null, tint = Rust); Spacer(Modifier.width(8.dp)); Text(CareThatContinues.TITLE.pick(hindi), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            CareThatContinues.ITEMS.forEach { Text("• " + it.pick(hindi), style = MaterialTheme.typography.bodyMedium) }
            Text(CareThatContinues.NOTE.pick(hindi), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable internal fun IssueList(issues: List<RuleIssue>) { val hindi = careHindi()
    if (issues.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("plan-issues"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        issues.forEach { i ->
            val warn = i.severity == IssueSeverity.WARN
            Surface(color = if (warn) DoctorStatusWarnBg else DoctorStatusRestBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (warn) Icons.Default.Info else Icons.Default.ErrorOutline, null, tint = if (warn) DoctorStatusWarn else DoctorStatusRest, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text("${i.id} · ${i.text(hindi)}", style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
        }
    }
}

@Composable internal fun PlanOption(label: String, meaning: String?, selected: Boolean, tag: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) Sage else Paper).border(if (selected) 2.dp else 1.dp, if (selected) Ink else MaterialTheme.colorScheme.outline.copy(alpha = .5f), RoundedCornerShape(16.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp).testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
        }
        if (!meaning.isNullOrBlank()) Text(meaning, style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
    }
}

@Composable internal fun <T> OptionGroup(title: String, value: T?, options: List<T>, label: (T) -> Bi, meaning: ((T) -> Bi)? = null, tag: String, noneLabel: Bi = NO_PREFERENCE, onChange: (T?) -> Unit) { val hindi = careHindi()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontFamily = SaharaTitleFont, fontSize = 19.sp, modifier = Modifier.a11yHeading())
        PlanOption(noneLabel.pick(hindi), null, value == null, "$tag-none") { onChange(null) }
        options.forEach { o -> PlanOption(label(o).pick(hindi), meaning?.invoke(o)?.pick(hindi), value == o, "$tag-${o.toString().lowercase()}") { onChange(o) } }
    }
}

@Composable internal fun <T> ChipGroup(value: T?, options: List<T>, label: (T) -> Bi, tag: String, onChange: (T?) -> Unit) { val hindi = careHindi()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o -> FilterChip(value == o, { onChange(if (value == o) null else o) }, label = { Text(label(o).pick(hindi)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-${o.toString().lowercase()}")) }
    }
}

@Composable internal fun PlanNumberField(value: Int?, label: String, tag: String, onChange: (Int?) -> Unit) {
    OutlinedTextField(value?.toString().orEmpty(), { s -> onChange(s.filter(Char::isDigit).take(4).toIntOrNull()) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper))
}

@Composable internal fun PlanDateField(value: String, label: String, onChange: (String) -> Unit, latest: LocalDate? = null) {
    CareDateButton(PlanDates.day(value)?.let(::careEpoch) ?: 0L, label, { onChange(if (it == 0L) "" else careDay(it).toString()) }, allowClear = true, latest = latest)
}

@Composable internal fun PlanCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

// ───────────────────────────── Home ─────────────────────────────

@Composable private fun CarePlanHome(state: CarePlanState, person: Person, clinician: Boolean, canEdit: Boolean, loadProblem: Boolean, store: CarePlanStore, canManage: Boolean, actor: String, commit: ((CarePlanState) -> CarePlanState) -> Boolean, transport: EdCardTransport?, onBack: () -> Unit, onPage: (String) -> Unit,
    onStart: (Boolean) -> Unit, onReview: (ReviewTrigger) -> Unit, onWithdraw: (String, String, String) -> Unit, onDiscard: (String) -> Unit) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    val active = EctprLifecycle.active(state.ectprs, person.id); val draft = EctprLifecycle.draft(state.ectprs, person.id)
    val interp = EdView.forPatient(state.ectprs, person.id, today)
    var dialog by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().testTag("care-plan-home"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PlanHeader(t("Emergency plan & living will", "आपातकालीन योजना और लिविंग विल"), onBack, "care-plan-header") }
        item { Text(t("Ambulances and emergency doctors resuscitate by default. A wish counts only if this plan is found and signed. It stays on this phone; only a signed plan's wallet card copy goes to the Sahara server.", "एम्बुलेंस और इमरजेंसी डॉक्टर अपने-आप पूरा इलाज करते हैं। इच्छा तभी मानी जाती है जब यह योजना मिले और उस पर हस्ताक्षर हों। यह इसी फ़ोन पर रहती है; सिर्फ़ हस्ताक्षरित योजना के वॉलेट कार्ड की कॉपी Sahara सर्वर पर जाती है।"), style = MaterialTheme.typography.bodyMedium) }
        if (loadProblem) item { Surface(color = Peach, shape = RoundedCornerShape(14.dp)) { Text(t("The saved plan on this phone could not be read. Standard care applies until a new plan is signed.", "इस फ़ोन पर सहेजी योजना पढ़ी नहीं जा सकी। नई योजना पर हस्ताक्षर होने तक सामान्य इलाज लागू है।"), Modifier.padding(12.dp)) } }
        item { IfInDoubtBox(compact = true) }
        item { DpdpNoticeCard(DpdpScope.CARE_PLAN, state, person.id, store, canManage, commit, actor) }
        item { ActionCard(if (interp.kind == EdKind.ACTIVE) Mint else Peach, Modifier.fillMaxWidth().testTag("care-plan-ed-entry"), onClick = { onPage("ed") }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, null, tint = Rust); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(t("ED Quick View", "ED क्विक व्यू"), fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(interp.banner.pick(hindi), style = MaterialTheme.typography.bodySmall) }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        } }
        item { PlanCard {
            SectionTitle(t("Emergency care plan", "आपातकालीन देखभाल योजना"))
            Text(when {
                active != null -> t("Active plan v${active.version}, review due ${active.reviewDue}", "लागू योजना v${active.version}, समीक्षा ${active.reviewDue}")
                else -> t("No signed plan yet", "अभी कोई हस्ताक्षरित योजना नहीं")
            }, fontWeight = FontWeight.SemiBold)
            if (draft != null) Text(t("Draft v${draft.version} in progress — not signed, so it never reaches the ED view.", "मसौदा v${draft.version} तैयार हो रहा है — हस्ताक्षर नहीं, इसलिए ED व्यू में नहीं दिखेगा।"), style = MaterialTheme.typography.bodyMedium)
            if (!clinician && draft == null && active == null) Text(t("Fill it in together with your doctor. It becomes valid only when the treating consultant signs.", "इसे अपने डॉक्टर के साथ भरें। यह तभी मान्य होगी जब इलाज करने वाले कंसल्टेंट हस्ताक्षर करें।"), style = MaterialTheme.typography.bodySmall)
            if (!canEdit) Text(t("Ask the person who invited you to allow changes.", "बदलाव करने की अनुमति के लिए उस व्यक्ति से कहें जिसने आपको जोड़ा है।"), style = MaterialTheme.typography.bodySmall, color = RustText)
            when {
                draft != null -> {
                    Button(onClick = { onPage("wizard") }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-continue")) { Text(t("Continue draft", "मसौदा जारी रखें")) }
                    TextButton(onClick = { dialog = "discard" }, enabled = canEdit) { Text(t("Discard unsigned draft", "बिना हस्ताक्षर का मसौदा हटाएँ")) }
                }
                active != null -> {
                    Button(onClick = { dialog = "review" }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-review")) { Text(t("Review or change the plan", "योजना की समीक्षा या बदलाव")) }
                    if (dialog == "withdraw") WithdrawPlanForm(active, onCancel = { dialog = "" }, onWithdraw = { reason, by -> dialog = ""; onWithdraw(active.id, reason, by) })
                    else TextButton(onClick = { dialog = "withdraw" }, enabled = canEdit, modifier = Modifier.testTag("care-plan-withdraw")) { Text(t("Withdraw the plan", "योजना वापस लें"), color = RustText) }
                }
                else -> Button(onClick = { if (clinician) dialog = "start" else onStart(false) }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-start")) { Text(t("Start a plan", "योजना शुरू करें")) }
            }
            OutlinedButton(onClick = { onPage("card") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-card-entry")) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(8.dp)); Text(t("Wallet card with QR", "QR वाला वॉलेट कार्ड")) }
        } }
        if (active != null && transport != null && ReviewReminder.due(active, today)) item { ReviewReminderCard(active, canEdit, transport) { status -> commit { CarePlanBook.reminded(it, active.id, actor, planNow(), status) } } }
        item { PlanCard {
            SectionTitle(t("Living will", "लिविंग विल"))
            val current = AmdLifecycle.current(state.amds, person.id); val working = AmdLifecycle.working(state.amds, person.id)
            Text(listOfNotNull(current?.let { val v = it.version; t("Signed and recorded v$v", "हस्ताक्षरित और दर्ज v$v") }, working?.let { AmdText.status(it.status).pick(hindi) + " v${it.version}" }).ifEmpty { listOf(t("Not started", "शुरू नहीं हुई")) }.joinToString(" · "), fontWeight = FontWeight.SemiBold)
            Text(AmdText.PAPER.pick(hindi), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onPage("amd") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-amd-entry")) { Icon(Icons.Default.Gavel, null); Spacer(Modifier.width(8.dp)); Text(t("Open living will", "लिविंग विल खोलें")) }
        } }
        if (clinician) item { OutlinedButton(onClick = { onPage("pilot") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-pilot-entry")) { Icon(Icons.Default.Assessment, null); Spacer(Modifier.width(8.dp)); Text(t("Pilot audit log", "पायलट ऑडिट लॉग")) } }
        if (state.ectprs.any { it.patientId == person.id }) item { InfoDisclosure(t("Plan versions", "योजना के संस्करण")) {
            state.ectprs.filter { it.patientId == person.id }.sortedByDescending { it.version }.forEach { r ->
                Text("v${r.version} · ${PlanText.status(r.status).pick(hindi)} · ${r.updatedAt.take(10)}" + (r.revokedReason?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            }
        } }
        if (state.audit.events.isNotEmpty()) item { InfoDisclosure(t("Audit log (cannot be changed)", "ऑडिट लॉग (बदला नहीं जा सकता)")) {
            Text(if (state.audit.verify()) t("Chain checked: no entry has been changed or removed.", "जाँच हो गई: कोई एंट्री बदली या हटाई नहीं गई।") else t("Warning: the log has been changed.", "चेतावनी: लॉग बदला गया है।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
            state.audit.events.takeLast(40).reversed().forEach { e -> Text("${e.at.take(16).replace('T', ' ')} · ${e.actor} · ${e.action.name} ${e.recordType.name}${e.detail?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall) }
        } }
    }
    when (dialog) {
        "start" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t("Where is this plan being made?", "यह योजना कहाँ बन रही है?")) },
            text = { Text(t("Plans are best made at a clinic visit, at discharge, at ICU step-down or when home palliative care starts. A plan made for the first time in the emergency department records panic, not preference.", "योजना क्लिनिक, छुट्टी के समय, ICU से बाहर आने पर या घर पर पैलिएटिव केयर शुरू होने पर बनाना बेहतर है। पहली बार इमरजेंसी में बनी योजना घबराहट दर्ज करती है, पसंद नहीं।")) },
            confirmButton = { TextButton(onClick = { dialog = ""; onStart(false) }, modifier = Modifier.testTag("care-plan-start-clinic")) { Text(t("Clinic, ward or home", "क्लिनिक, वार्ड या घर")) } },
            dismissButton = { TextButton(onClick = { dialog = ""; onStart(true) }) { Text(t("Emergency department", "आपातकालीन विभाग"), color = RustText) } })
        "review" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t("Why is the plan being reviewed?", "योजना की समीक्षा क्यों हो रही है?")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("A review opens a new draft filled in from the current plan. The current plan stays active until the new one is signed.", "समीक्षा मौजूदा योजना से भरा नया मसौदा खोलती है। नई योजना पर हस्ताक्षर होने तक मौजूदा योजना लागू रहती है।"), style = MaterialTheme.typography.bodySmall)
                ReviewTrigger.entries.forEach { tr -> OutlinedButton(onClick = { dialog = ""; onReview(tr) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review-${tr.name.lowercase()}")) { Text(PlanText.trigger(tr).pick(hindi)) } }
            } }, confirmButton = {}, dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Cancel", "रद्द करें")) } })
        "discard" -> draft?.let { d -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t("Discard draft v${d.version}?", "मसौदा v${d.version} हटाएँ?")) },
            text = { Text(t("The unsigned answers are removed. The audit log keeps a note that a draft was discarded.", "बिना हस्ताक्षर के जवाब हट जाएँगे। ऑडिट लॉग में दर्ज रहेगा कि मसौदा हटाया गया।")) },
            confirmButton = { TextButton(onClick = { dialog = ""; onDiscard(d.id) }) { Text(t("Discard", "हटाएँ"), color = RustText) } },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Keep draft", "मसौदा रखें")) } }) }
    }
}

@Composable private fun WithdrawPlanForm(plan: EctprRecord, onCancel: () -> Unit, onWithdraw: (String, String) -> Unit) { val t = careText()
    var reason by rememberSaveable { mutableStateOf("") }; var by by rememberSaveable { mutableStateOf("") }
    Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("care-plan-withdraw-form")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Withdraw plan v${plan.version}?", "योजना v${plan.version} वापस लें?"), fontWeight = FontWeight.SemiBold)
        Text(t("A signed cancellation withdraws the plan and switches off its wallet card QR. Standard care then applies.", "हस्ताक्षरित रद्दीकरण से योजना वापस होती है और वॉलेट कार्ड का QR बंद हो जाता है। उसके बाद सामान्य इलाज लागू होता है।"), style = MaterialTheme.typography.bodySmall)
        CareField(reason, { reason = it }, t("Reason", "कारण")); CareField(by, { by = it }, t("Signed by (name)", "हस्ताक्षर करने वाले (नाम)"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Keep plan", "योजना रखें")) }
            Button(onClick = { onWithdraw(reason.trim(), by.trim()) }, enabled = reason.isNotBlank() && by.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("care-plan-withdraw-confirm")) { Text(t("Withdraw", "वापस लें")) }
        }
    } }
}

// ───────────────────────────── Wizard ─────────────────────────────

@Composable private fun EctprWizard(record: EctprRecord, clinician: Boolean, canEdit: Boolean, onSave: (EctprRecord) -> Boolean, onSign: (EctprRecord) -> Unit, onClose: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    var d by remember(record.id) { mutableStateOf(record) }
    var step by rememberSaveable(record.id) { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf<List<RuleIssue>>(emptyList()) }
    var leaving by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Each step opens at its top; a refused "Next" brings its reasons into view, since the step can be long.
    LaunchedEffect(step) { listState.scrollToItem(0) }
    LaunchedEffect(shown) { if (shown.isNotEmpty()) listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }
    val steps = listOf(t("Patient and illness", "मरीज़ और बीमारी"), t("Decision-makers", "फ़ैसला लेने वाले"), t("Capacity and existing directive", "निर्णय-क्षमता और पहले का निर्देश"),
        t("Understanding", "समझ"), t("What matters", "क्या ज़रूरी है"), t("Treatment choices", "इलाज के फ़ैसले"), t("Stopping or not starting treatment", "इलाज रोकना या शुरू न करना"), t("Review date", "समीक्षा की तारीख़"), t("Signatures", "हस्ताक्षर"), t("Check and sign", "जाँचें और हस्ताक्षर करें"))
    val blockers = EctprRules.saveBlockers(d, today)
    fun update(change: (EctprRecord) -> EctprRecord) { d = change(d); shown = emptyList() }
    fun choices(change: (PlanChoices) -> PlanChoices) = update { it.copy(choices = change(it.choices)) }
    fun tryLeave() { if (samePlan(d, record)) onClose() else if (blockers.isEmpty()) { if (onSave(d)) onClose() } else leaving = true }
    fun next() {
        val mine = blockers.filter { planStepOf(it.field) <= step }
        if (mine.isNotEmpty()) { shown = mine; return }
        if (blockers.isEmpty() && !samePlan(d, record) && !onSave(d)) return
        step = (step + 1).coerceAtMost(steps.lastIndex); shown = emptyList()
    }
    BackHandler { if (step > 0) { step--; shown = emptyList() } else tryLeave() }
    Column(Modifier.fillMaxSize().testTag("ectpr-wizard")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::tryLeave, modifier = Modifier.testTag("ectpr-wizard-close")) { Icon(Icons.Default.Close, t("Close", "बंद करें")) }
            Column(Modifier.weight(1f)) {
                Text(t("Emergency plan v${d.version} · step ${step + 1} of ${steps.size}", "आपातकालीन योजना v${d.version} · चरण ${step + 1} / ${steps.size}"), style = MaterialTheme.typography.labelMedium, color = DoctorCardSub)
                Text(steps[step], fontFamily = SaharaTitleFont, fontSize = 22.sp, modifier = Modifier.a11yHeading())
            }
        }
        LinearProgressIndicator(progress = { (step + 1f) / steps.size }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = Rust)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "read-aloud") { PlanReadAloud({ steps[step] + ".\n" + PlanSpeech.wizardStep(d, step, hindi) }, "wizard-${record.id}-$step") }
            when (step) {
                0 -> patientStep(d, ::update)
                1 -> decisionMakerStep(d, ::update)
                2 -> capacityStep(d, ::update)
                3 -> understandingStep(d, ::update)
                4 -> valuesStep(d, ::update)
                5 -> treatmentStep(d, today, ::update, ::choices)
                6 -> withdrawalStep(d, today, ::update)
                7 -> reviewStep(d, today, ::update)
                8 -> signatureStep(d, clinician, ::update)
                else -> item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val stop = EctprRules.activationBlockers(d, today); val warn = EctprRules.warnings(d, today)
                    Text(if (stop.isEmpty()) t("Ready to sign. Signing makes this the one active plan; any earlier version is replaced and its card link switched off.", "हस्ताक्षर के लिए तैयार। हस्ताक्षर से यही एक लागू योजना होगी; पुराना संस्करण बदल जाएगा और उसके कार्ड का लिंक बंद होगा।")
                        else t("Still needed before the plan can be active:", "योजना लागू होने से पहले अभी चाहिए:"), fontWeight = FontWeight.SemiBold)
                    IssueList(stop); IssueList(warn)
                    PlanSummaryExtras(d)
                    GocNoteForSigning(d)
                    CareContinuesBox()
                    Button(onClick = { onSign(d) }, enabled = canEdit && stop.isEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("ectpr-sign")) { Icon(Icons.Default.Verified, null); Spacer(Modifier.width(8.dp)); Text(t("Sign and make active", "हस्ताक्षर करें और लागू करें")) }
                    OutlinedButton(onClick = { if (blockers.isEmpty() && onSave(d)) onClose() else shown = blockers }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Save draft and close", "मसौदा सहेजें और बंद करें")) }
                } }
            }
            // The treatment step already lists its own checks; only show what is not on screen there.
            val refused = if (step == 5) shown.filter { planStepOf(it.field) != 5 } else shown
            if (shown.isNotEmpty()) item { IssueList(refused) }
        }
        Surface(color = Paper, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (step > 0) { step--; shown = emptyList() } else tryLeave() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (step > 0) t("Back", "पीछे") else t("Close", "बंद करें")) }
                if (step < steps.lastIndex) Button(onClick = ::next, enabled = canEdit, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ectpr-next")) { Text(t("Save and next", "सहेजें और आगे")) }
            }
        }
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text(t("Some answers can't be saved yet", "कुछ जवाब अभी सहेजे नहीं जा सकते")) },
        text = { Column { blockers.forEach { Text("• " + it.text(hindi), style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { TextButton(onClick = { leaving = false }) { Text(t("Fix them", "ठीक करें")) } },
        dismissButton = { TextButton(onClick = { leaving = false; onClose() }) { Text(t("Leave without the latest changes", "ताज़ा बदलाव छोड़कर जाएँ"), color = RustText) } })
}

private fun LazyListScope.patientStep(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) {
    item { PatientFields(d, update) }
}

@Composable private fun PatientFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    fun p(change: (EctprPatient) -> EctprPatient) = update { it.copy(patient = change(it.patient)) }
    fun c(change: (ClinicalDetails) -> ClinicalDetails) = update { it.copy(clinical = change(it.clinical)) }
    fun team(change: (CareTeam) -> CareTeam) = update { it.copy(careTeam = change(it.careTeam)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CareField(d.patient.name, { v -> p { it.copy(name = v) } }, t("Patient's name", "मरीज़ का नाम"))
        PlanDateField(d.patient.dob, t("Date of birth", "जन्मतिथि"), { v -> p { it.copy(dob = v) } }, latest = LocalDate.now())
        Text(t("Sahara plans are for adults (18 and over).", "सहारा की योजना वयस्कों (18 और ऊपर) के लिए है।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        CareField(d.patient.sex, { v -> p { it.copy(sex = v) } }, t("Sex", "लिंग"), singleLine = true)
        CareField(d.patient.gender, { v -> p { it.copy(gender = v) } }, t("Gender", "जेंडर"), singleLine = true)
        CareField(d.patient.uhid, { v -> p { it.copy(uhid = v) } }, t("Hospital number (UHID)", "अस्पताल नंबर (UHID)"), singleLine = true)
        CareField(d.patient.phone, { v -> p { it.copy(phone = v) } }, t("Phone", "फ़ोन"), singleLine = true)
        CareField(d.patient.address, { v -> p { it.copy(address = v) } }, t("Address", "पता"))
        CareField(d.clinical.diagnosis, { v -> c { it.copy(diagnosis = v) } }, t("Diagnosis", "बीमारी"))
        CareField(d.clinical.stage, { v -> c { it.copy(stage = v) } }, t("Stage", "अवस्था"), singleLine = true)
        Text(t("Performance status (ECOG)", "रोज़ के काम करने की क्षमता (ECOG)"), fontWeight = FontWeight.Medium)
        ChipGroup(d.clinical.ecog, (0..4).toList(), { Bi("ECOG $it", "ECOG $it") }, "ecog") { v -> c { it.copy(ecog = v) } }
        CareField(d.clinical.allergies, { v -> c { it.copy(allergies = v) } }, t("Allergies", "एलर्जी"), placeholder = t("For example: penicillin", "जैसे: पेनिसिलिन"))
        Text(t("Implanted device", "शरीर में लगा यंत्र"), fontWeight = FontWeight.Medium)
        ChipGroup(d.clinical.implantedDevice, ImplantedDevice.entries, PlanText::device, "device") { v -> c { it.copy(implantedDevice = v) } }
        CareField(d.clinical.keyMedicines, { v -> c { it.copy(keyMedicines = v) } }, t("Key medicines", "मुख्य दवाएँ"))
        PlanDateField(d.clinical.eligibilityDate, t("Eligible for this plan since", "इस योजना के योग्य कब से"), { v -> c { it.copy(eligibilityDate = v) } }, latest = LocalDate.now())
        SectionTitle(t("Care team", "देखभाल टीम"))
        CareField(d.careTeam.oncologist, { v -> team { it.copy(oncologist = v) } }, t("Main treating doctor or specialist (oncologist or any other)", "मुख्य इलाज करने वाले डॉक्टर या विशेषज्ञ (कैंसर या कोई और)"), singleLine = true)
        CareField(d.careTeam.palliativePhysician, { v -> team { it.copy(palliativePhysician = v) } }, t("Palliative doctor", "पैलिएटिव डॉक्टर"), singleLine = true)
        CareField(d.careTeam.palliativePhone24h, { v -> team { it.copy(palliativePhone24h = v) } }, t("Palliative 24-hour phone", "पैलिएटिव 24 घंटे का फ़ोन"), singleLine = true)
        CareField(d.careTeam.localDoctor, { v -> team { it.copy(localDoctor = v) } }, t("Local doctor", "स्थानीय डॉक्टर"), singleLine = true)
    }
}

private fun LazyListScope.decisionMakerStep(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) {
    item { DecisionMakerFields(d.decisionMakers.primary, true) { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(primary = v)) } } }
    item { DecisionMakerFields(d.decisionMakers.alternate, false) { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(alternate = v)) } } }
    item { FamilyFields(d, update) }
}

@Composable private fun FamilyFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    PlanCard {
        SectionTitle(t("Family and friends", "परिवार और दोस्त"))
        CareField(d.decisionMakers.presentAtDiscussion.joinToString("\n"), { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(presentAtDiscussion = v.lines())) } }, t("Family or friends present for this discussion, with their relationship (one per line)", "इस बातचीत में मौजूद परिवार या दोस्त, रिश्ते के साथ (हर लाइन में एक)"), minLines = 2,
            supporting = t("Includes spouses, live-in partners, same-sex and queer partners, and friends the patient chooses.", "इसमें पति-पत्नी, लिव-इन साथी, समलैंगिक और क्वीयर साथी, और मरीज़ के चुने हुए दोस्त शामिल हैं।"))
        CareField(d.decisionMakers.familyToInform.joinToString("\n"), { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(familyToInform = v.lines())) } }, t("Family or friends who must be informed before any major decision: name, relation, phone (one per line)", "जिन परिवारजनों या दोस्तों को बड़े फ़ैसले से पहले बताना ज़रूरी है: नाम, रिश्ता, फ़ोन (हर लाइन में एक)"), minLines = 2)
        CareField(d.decisionMakers.attemptedContact.joinToString("\n"), { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(attemptedContact = v.lines())) } }, t("Family or friends we tried to contact but could not reach (one per line)", "जिन परिवारजनों या दोस्तों से संपर्क की कोशिश हुई पर बात नहीं हो सकी (हर लाइन में एक)"), minLines = 2)
        CareField(d.decisionMakers.familyConsensusNote, { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(familyConsensusNote = v)) } }, t("Family discussion note", "परिवार से बातचीत का नोट"),
            supporting = t("Family agreement is a note, not legal authority.", "परिवार की सहमति एक नोट है, क़ानूनी अधिकार नहीं।"))
    }
}

@Composable private fun DecisionMakerFields(value: DecisionMaker?, primary: Boolean, onChange: (DecisionMaker?) -> Unit) { val t = careText()
    val dm = value ?: DecisionMaker()
    fun set(change: (DecisionMaker) -> DecisionMaker) { val next = change(dm); onChange(if (next == DecisionMaker()) null else next) }
    PlanCard {
        SectionTitle(if (primary) t("Primary decision-maker", "मुख्य फ़ैसला लेने वाले") else t("Secondary decision-maker", "द्वितीय फ़ैसला लेने वाले"))
        CareField(dm.name, { v -> set { it.copy(name = v) } }, t("Name", "नाम"), singleLine = true)
        CareField(dm.relation, { v -> set { it.copy(relation = v) } }, t("Relation to the patient", "मरीज़ से रिश्ता"), singleLine = true,
            supporting = t("Includes spouses, live-in partners, same-sex and queer partners, and friends the patient chooses.", "इसमें पति-पत्नी, लिव-इन साथी, समलैंगिक और क्वीयर साथी, और मरीज़ के चुने हुए दोस्त शामिल हैं।"))
        CareField(dm.phone24h, { v -> set { it.copy(phone24h = v) } }, t("24-hour phone", "24 घंटे का फ़ोन"), singleLine = true)
        Text(t("ID type", "पहचान-पत्र का प्रकार"), fontWeight = FontWeight.Medium)
        ChipGroup(dm.idType, IdType.entries, PlanText::idType, "dm-${if (primary) "primary" else "alternate"}-id") { v -> set { it.copy(idType = v) } }
        CareField(dm.idLast4.orEmpty(), { v -> set { it.copy(idLast4 = PlanIds.last4(v).ifBlank { null }) } }, t("Last 4 digits of the ID", "पहचान-पत्र के आख़िरी 4 अंक"), singleLine = true,
            supporting = t("Only the last 4 are kept, never the full number.", "सिर्फ़ आख़िरी 4 अंक रखे जाते हैं, पूरा नंबर कभी नहीं।"))
        OptionGroup(t("Basis of authority", "अधिकार का आधार"), dm.authorityBasis, AuthorityBasis.entries, PlanText::authority, tag = "dm-basis-${if (primary) "primary" else "alternate"}") { v -> set { it.copy(authorityBasis = v) } }
    }
}

private fun LazyListScope.capacityStep(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { CapacityFields(d, update) } }

@Composable private fun CapacityFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText(); val hindi = careHindi()
    fun cap(change: (CapacityAssessment) -> CapacityAssessment) = update { it.copy(capacity = change(it.capacity)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapacityGuideCard()
        Text(t("Capacity is decided for this decision, now — not taken from old notes. Record the basis: can the patient understand, remember, weigh and communicate the choice?", "निर्णय-क्षमता इसी फ़ैसले के लिए, अभी जाँची जाती है — पुराने नोट से नहीं। आधार लिखें: क्या मरीज़ फ़ैसला समझ, याद रख, तौल और बता सकते हैं?"), style = MaterialTheme.typography.bodyMedium)
        OptionGroup(t("Capacity", "निर्णय-क्षमता"), d.capacity.status, Capacity.entries, PlanText::capacity, tag = "capacity") { v -> cap { it.copy(status = v) } }
        if (d.capacity.status == Capacity.FLUCTUATES || d.capacity.status == Capacity.LACKS) {
            CareField(d.capacity.assessor, { v -> cap { it.copy(assessor = v) } }, t("Assessed by", "किसने जाँचा"), singleLine = true)
            PlanDateField(d.capacity.assessedOn, t("Assessed on", "जाँच की तारीख़"), { v -> cap { it.copy(assessedOn = v) } }, latest = LocalDate.now())
            CareField(d.capacity.basis, { v -> cap { it.copy(basis = v) } }, t("Basis of the assessment", "जाँच का आधार"), minLines = 2)
        }
        if (d.capacity.status == Capacity.FLUCTUATES) PlanDateField(d.capacity.reassessOn, t("Reassess on", "दोबारा जाँच की तारीख़"), { v -> cap { it.copy(reassessOn = v) } })
        Text(t("A patient with capacity can change or cancel any part of this plan by saying so; their wish now overrides the paper.", "निर्णय ले सकने वाले मरीज़ बोलकर योजना का कोई भी हिस्सा बदल या रद्द कर सकते हैं; उनकी अभी की इच्छा काग़ज़ से ऊपर है।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        val dir = d.existingDirective
        OptionGroup(t("Existing living will or wishes", "पहले की लिविंग विल या इच्छाएँ"), dir?.kind, DirectiveKind.entries, { when (it) {
            DirectiveKind.VALID_AMD -> Bi("A signed, attested living will", "हस्ताक्षरित, सत्यापित लिविंग विल"); DirectiveKind.PRIOR_WISHES -> Bi("Wishes said or written before", "पहले कही या लिखी इच्छाएँ"); DirectiveKind.NONE -> Bi("None", "कोई नहीं") } }, tag = "directive") { k ->
            update { it.copy(existingDirective = k?.let { kind -> (it.existingDirective ?: ExistingDirective(kind)).copy(kind = kind) }) }
        }
        if (dir?.kind == DirectiveKind.VALID_AMD) {
            PlanDateField(dir.date, t("Date of the living will", "लिविंग विल की तारीख़"), { v -> update { it.copy(existingDirective = dir.copy(date = v)) } }, latest = LocalDate.now())
            CareField(dir.location, { v -> update { it.copy(existingDirective = dir.copy(location = v)) } }, t("Where the original is kept", "मूल कहाँ रखी है"), singleLine = true)
            CareField(dir.attestor, { v -> update { it.copy(existingDirective = dir.copy(attestor = v)) } }, t("Attested by (notary or gazetted officer)", "किसने सत्यापित किया (नोटरी या राजपत्रित अधिकारी)"), singleLine = true)
            Text(AmdText.CAVEAT.pick(hindi), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        }
        if (dir?.kind == DirectiveKind.PRIOR_WISHES) CareField(dir.note, { v -> update { it.copy(existingDirective = dir.copy(note = v)) } }, t("What was said or written", "क्या कहा या लिखा गया था"), minLines = 2)
    }
}

/** The draft annexure from the legal review. */
@Composable private fun CapacityGuideCard() { val hindi = careHindi()
    InfoDisclosure(CapacityGuide.TITLE.pick(hindi)) {
        Text(CapacityGuide.NOTE.pick(hindi), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        CapacityGuide.POINTS.forEachIndexed { i, point -> Text("${i + 1}. " + point.pick(hindi), style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun LazyListScope.understandingStep(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { UnderstandingFields(d, update) } }

@Composable private fun UnderstandingFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    val today = LocalDate.now().toString()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanCard {
            SectionTitle(t("Understanding the illness", "बीमारी की समझ"))
            Text(t("Treatment choices stay locked until this is ticked. A choice made without knowing the illness cannot be cured is not an informed choice.", "जब तक इस पर टिक न हो, इलाज के फ़ैसले बंद रहेंगे। बीमारी ठीक न होने की जानकारी के बिना लिया फ़ैसला सोच-समझकर लिया फ़ैसला नहीं है।"), style = MaterialTheme.typography.bodySmall)
            CheckLine(d.prognosticUnderstanding.confirmed, { v -> update { it.copy(prognosticUnderstanding = it.prognosticUnderstanding.copy(confirmed = v, confirmedOn = if (v) it.prognosticUnderstanding.confirmedOn.ifBlank { today } else "")) } },
                t("The illness has been explained as incurable, and the patient or family can say it back in their own words.", "समझा दिया गया है कि बीमारी ठीक नहीं होगी, और मरीज़ या परिवार इसे अपने शब्दों में दोहरा सकते हैं।"))
            CareField(d.prognosticUnderstanding.confirmedBy, { v -> update { it.copy(prognosticUnderstanding = it.prognosticUnderstanding.copy(confirmedBy = v)) } }, t("Explained by", "किसने समझाया"), singleLine = true)
        }
        PlanCard {
            SectionTitle(t("Who is answering?", "जवाब कौन दे रहा है?"))
            PlanOption(t("The patient", "मरीज़ ख़ुद"), null, !d.surrogateDeclaration.answeringAsSurrogate, "answering-patient") { update { it.copy(surrogateDeclaration = SurrogateDeclaration()) } }
            PlanOption(t("A decision-maker, for the patient", "मरीज़ की ओर से फ़ैसला लेने वाले"), null, d.surrogateDeclaration.answeringAsSurrogate, "answering-surrogate") { update { it.copy(surrogateDeclaration = it.surrogateDeclaration.copy(answeringAsSurrogate = true)) } }
            val who = d.patient.name.ifBlank { t("the patient", "मरीज़") }
            if (d.surrogateDeclaration.answeringAsSurrogate) CheckLine(d.surrogateDeclaration.confirmed, { v -> update { it.copy(surrogateDeclaration = it.surrogateDeclaration.copy(confirmed = v, confirmedOn = if (v) today else "")) } },
                t("I will say what $who would have chosen, not what I want for them. Where I do not know, I will decide with the team in their best interests.", "मैं वही बताऊँगा/बताऊँगी जो $who ख़ुद चुनते, न कि जो मैं उनके लिए चाहता/चाहती हूँ। जहाँ मुझे नहीं पता, वहाँ टीम के साथ उनकी भलाई के लिए फ़ैसला करूँगा/करूँगी।"))
        }
    }
}

private fun LazyListScope.valuesStep(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { ValuesFields(d, update) } }

@Composable private fun ValuesFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    fun v(change: (PlanValues) -> PlanValues) = update { it.copy(values = change(it.values)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("Does the patient know about the illness?", "क्या मरीज़ को बीमारी के बारे में पता है?"), d.values.patientKnows, listOf(true, false), { if (it) Bi("Yes", "हाँ") else Bi("No", "नहीं") }, tag = "patient-knows") { x -> v { it.copy(patientKnows = x) } }
        OptionGroup(t("When was this recorded?", "यह कब दर्ज किया गया?"), d.values.knowsRecordedWhen, KnowsRecordedWhen.entries, PlanText::knowsRecordedWhen, tag = "knows-when", noneLabel = NOT_RECORDED) { x -> v { it.copy(knowsRecordedWhen = x) } }
        CareField(d.values.ownWords, { x -> v { it.copy(ownWords = x) } }, t("What matters most, in the patient's own words", "सबसे ज़रूरी क्या है, मरीज़ के अपने शब्दों में"), minLines = 3)
        CareField(d.values.unacceptableOutcomes.joinToString("\n"), { x -> v { it.copy(unacceptableOutcomes = x.lines()) } }, t("Outcomes the patient would not accept (one per line)", "जो हालात मरीज़ को मंज़ूर नहीं (हर लाइन में एक)"), minLines = 2)
        CheckLine(d.values.familyAskedNotToTell, { x -> v { it.copy(familyAskedNotToTell = x) } }, t("The family asked that the patient not be told", "परिवार ने कहा है कि मरीज़ को न बताया जाए"))
        if (d.values.familyAskedNotToTell) CareField(d.values.directQuestionPlan, { x -> v { it.copy(directQuestionPlan = x) } }, t("Plan if the patient asks directly", "अगर मरीज़ सीधे पूछें तो क्या करेंगे"), minLines = 2,
            supporting = t("A patient with capacity who asks must be answered honestly.", "निर्णय ले सकने वाले मरीज़ पूछें, तो सच बताना ज़रूरी है।"))
        CheckLine(d.values.financialConcernDiscussed == true, { x -> v { it.copy(financialConcernDiscussed = x) } }, t("Money worries were asked about directly", "पैसों की चिंता के बारे में सीधे पूछा गया"))
        CheckLine(d.values.socialWorkReferral == true, { x -> v { it.copy(socialWorkReferral = x) } }, t("Social-work or scheme referral made", "सोशल वर्क या सरकारी योजना के लिए भेजा गया"))
    }
}

private fun LazyListScope.treatmentStep(d: EctprRecord, today: LocalDate, update: ((EctprRecord) -> EctprRecord) -> Unit, choices: ((PlanChoices) -> PlanChoices) -> Unit) {
    val locked = !d.prognosticUnderstanding.confirmed || (d.surrogateDeclaration.answeringAsSurrogate && !d.surrogateDeclaration.confirmed)
    if (locked) { item { LockedTreatment(d) }; item { HomeCareFields(d, update) }; return }
    item { TreatmentIntro() }
    item(key = "goc-conversation") { GocConversationCard(d, update) }
    item { GoalAndTransfer(d, choices) }
    item { CprFields(d, update, choices) }
    item { BreathingFields(d, today, choices) }
    item { OrganSupportFields(d, today, choices) }
    item { FeedingFields(d, choices) }
    item { LegalPendingBox() }
    item { ConflictFields(d, today, choices) }
    item { HomeCareFields(d, update) }
}

/** Helps a doctor called to the home. Not a treatment choice, so the V4 and V5 locks do not apply. */
@Composable private fun HomeCareFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    val home = d.homeCare ?: EctprHomeCare()
    fun set(change: (EctprHomeCare) -> EctprHomeCare) = update { it.copy(homeCare = change(it.homeCare ?: EctprHomeCare())) }
    PlanCard {
        SectionTitle(t("Home care (if relevant)", "घर पर देखभाल (अगर लागू हो)"))
        Text(t("Helps a doctor called to the home see the care schedule and decide whether home care can continue.", "घर बुलाए गए डॉक्टर को देखभाल का क्रम देखने और यह तय करने में मदद करता है कि घर पर देखभाल जारी रह सकती है या नहीं।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        CareField(home.assessmentFrequency, { v -> set { it.copy(assessmentFrequency = v) } }, t("How often the patient is assessed", "मरीज़ की जाँच कितनी बार होती है"), singleLine = true)
        CareField(home.coordinator, { v -> set { it.copy(coordinator = v) } }, t("Person coordinating between the family and the hospital or centre (name, phone, centre)", "परिवार और अस्पताल या केंद्र के बीच तालमेल रखने वाले (नाम, फ़ोन, केंद्र)"))
        CareField(home.ngoContact, { v -> set { it.copy(ngoContact = v, ngoConsulted = it.ngoConsulted && v.isNotBlank()) } }, t("Non-governmental organisation caring for the patient: representative and contact", "मरीज़ की देखभाल करने वाली ग़ैर-सरकारी संस्था: प्रतिनिधि और संपर्क"))
        if (home.ngoContact.isNotBlank()) CheckLine(home.ngoConsulted, { v -> set { it.copy(ngoConsulted = v) } }, t("The organisation was consulted about this plan", "इस योजना पर संस्था से सलाह ली गई"))
        OptionGroup(t("Admitted to any hospital before for this condition?", "क्या इस बीमारी के लिए पहले किसी अस्पताल में भर्ती हुए थे?"), home.previousAdmission, listOf(true, false), { if (it) Bi("Yes", "हाँ") else Bi("No", "नहीं") }, tag = "home-admitted", noneLabel = NOT_RECORDED) { x -> set { it.copy(previousAdmission = x) } }
        if (home.previousAdmission == true) CareField(home.previousAdmissionDetails, { v -> set { it.copy(previousAdmissionDetails = v) } }, t("Where and when", "कहाँ और कब"), singleLine = true)
    }
}

private fun LazyListScope.withdrawalStep(d: EctprRecord, today: LocalDate, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { WithdrawalFields(d, today, update) } }

/** Records the process only. The app never triggers withdrawal. */
@Composable private fun WithdrawalFields(d: EctprRecord, today: LocalDate, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    val w = d.withdrawal ?: EctprWithdrawal()
    fun set(change: (EctprWithdrawal) -> EctprWithdrawal) = update { it.copy(withdrawal = change(it.withdrawal ?: EctprWithdrawal())) }
    Column(Modifier.testTag("withdrawal-step"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Fill this only if withdrawing or withholding treatment has been decided (Common Cause v Union of India, 2018 and 2023). It records the process. This plan alone never authorises it.", "इसे तभी भरें जब इलाज रोकने या शुरू न करने का फ़ैसला हुआ हो (Common Cause v Union of India, 2018 और 2023)। यह प्रक्रिया दर्ज करता है। सिर्फ़ यह योजना इसकी अनुमति कभी नहीं देती।"), style = MaterialTheme.typography.bodyMedium)
        IssueList(EctprRules.check(d, today).filter { it.id == "W1" })
        PlanCard {
            SectionTitle(t("Withdrawal or withholding (medical boards)", "इलाज रोकना या शुरू न करना (मेडिकल बोर्ड)"))
            CheckLine(w.applicable, { v -> set { it.copy(applicable = v) } }, t("Withdrawing or withholding treatment has been decided for this patient", "इस मरीज़ के लिए इलाज रोकने या शुरू न करने का फ़ैसला हुआ है"))
            if (w.applicable) {
                PlanDateField(w.primaryBoardDate, t("Date the Primary Board was formed", "प्राथमिक बोर्ड बनने की तारीख़"), { v -> set { it.copy(primaryBoardDate = v) } })
                PlanDateField(w.secondaryBoardDate, t("Date the Secondary Board was formed", "द्वितीय बोर्ड बनने की तारीख़"), { v -> set { it.copy(secondaryBoardDate = v) } })
                CareField(w.primaryBoardMembers, { v -> set { it.copy(primaryBoardMembers = v) } }, t("Members of the Primary Board", "प्राथमिक बोर्ड के सदस्य"), minLines = 2)
                CareField(w.secondaryBoardMembers, { v -> set { it.copy(secondaryBoardMembers = v) } }, t("Members of the Secondary Board", "द्वितीय बोर्ड के सदस्य"), minLines = 2)
                CareField(w.boardDecision, { v -> set { it.copy(boardDecision = v) } }, t("Decision of the Boards", "बोर्ड का फ़ैसला"), minLines = 2)
                OptionGroup(t("Final decision", "अंतिम फ़ैसला"), w.finalDecision, WithdrawalFinal.entries, { PlanText.withdrawalFinal(it) }, tag = "withdrawal-final", noneLabel = NOT_RECORDED) { x -> set { it.copy(finalDecision = x) } }
                CareField(w.protocol, { v -> set { it.copy(protocol = v) } }, t("Protocol for withdrawal or withholding", "इलाज रोकने या शुरू न करने का तरीका"), minLines = 3)
            }
        }
    }
}

@Composable private fun LockedTreatment(d: EctprRecord) { val t = careText()
    Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("treatment-locked")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Lock, null, tint = DoctorStatusWarn); Spacer(Modifier.width(10.dp))
            Text(if (!d.prognosticUnderstanding.confirmed) t("Locked: go back to Understanding and record that the illness has been explained and understood (V4).", "बंद है: 'समझ' पर वापस जाएँ और दर्ज करें कि बीमारी समझा दी गई और समझ ली गई है (V4)।")
                else t("Locked: the decision-maker must first confirm they will say what the patient would have chosen (V5).", "बंद है: फ़ैसला लेने वाले पहले पुष्टि करें कि वे वही बताएँगे जो मरीज़ चुनते (V5)।"))
        }
    }
}

@Composable private fun TreatmentIntro() { val t = careText()
    Text(t("Leave anything not discussed as \"No preference recorded\": that means standard care, never refusal. Each level allowed includes everything below it.", "जिस पर बात नहीं हुई उसे \"कोई पसंद दर्ज नहीं\" पर छोड़ दें: इसका मतलब सामान्य इलाज है, मना करना नहीं। हर मान्य स्तर में उससे नीचे का सब शामिल है।"), style = MaterialTheme.typography.bodyMedium)
}

@Composable private fun GoalAndTransfer(d: EctprRecord, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText(); val hindi = careHindi()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OptionGroup(t("Goal of care", "देखभाल का लक्ष्य"), c.goalOfCare, GoalOfCare.entries, PlanText::goal, PlanText::goalMeaning, "goal") { v -> choices { it.copy(goalOfCare = v) } }
        OptionGroup(t("Going to hospital", "अस्पताल जाना"), c.hospitalTransfer?.kind, TransferKind.entries, PlanText::transfer, PlanText::transferMeaning, "transfer") { v -> choices { it.copy(hospitalTransfer = v?.let { k -> HospitalTransfer(k, if (k == TransferKind.ONLY_FOR) it.hospitalTransfer?.reasons.orEmpty() else emptyList()) }) } }
        if (c.hospitalTransfer?.kind == TransferKind.ONLY_FOR) Column {
            TransferReason.entries.forEach { r -> CheckLine(r in c.hospitalTransfer.reasons, { on -> choices { it.copy(hospitalTransfer = HospitalTransfer(TransferKind.ONLY_FOR, if (on) it.hospitalTransfer!!.reasons + r else it.hospitalTransfer!!.reasons - r)) } }, PlanText.transferReason(r).pick(hindi)) }
        }
        CareField(c.callBeforeAmbulance.joinToString("\n"), { v -> choices { it.copy(callBeforeAmbulance = v.lines()) } }, t("Numbers to call before an ambulance (one per line)", "एम्बुलेंस से पहले किसे फ़ोन करें (हर लाइन में एक)"), minLines = 2)
        OptionGroup(t("If an ambulance comes", "अगर एम्बुलेंस आए"), c.ambulance, AmbulanceOption.entries, PlanText::ambulance, tag = "ambulance") { v -> choices { it.copy(ambulance = v) } }
        Text(t("Crews resuscitate by default; this counts only if a signed copy is handed over.", "एम्बुलेंस वाले अपने-आप पूरा इलाज करते हैं; यह तभी माना जाता है जब हस्ताक्षरित कॉपी दी जाए।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
    }
}

@Composable private fun CprFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("CPR if the heart stops", "दिल रुकने पर CPR"), c.cpr, Cpr.entries, PlanText::cpr, PlanText::cprMeaning, "cpr") { v -> choices { it.copy(cpr = v) } }
        if (c.cpr == Cpr.DNACPR) {
            OptionGroup(t("Reason for DNACPR", "DNACPR का कारण"), c.dnacprReason, DnacprReason.entries, PlanText::dnacprReason, tag = "dnacpr-reason") { v -> choices { it.copy(dnacprReason = v) } }
            CareField(c.dnacprDiscussedWith, { v -> choices { it.copy(dnacprDiscussedWith = v) } }, t("Discussed with", "किससे बात हुई"), singleLine = true)
            val by = c.dnacprDecidedBy ?: DnacprDecidedBy()
            CareField(by.name, { v -> choices { it.copy(dnacprDecidedBy = by.copy(name = v)) } }, t("Decided by (doctor's name)", "किसने तय किया (डॉक्टर का नाम)"), singleLine = true)
            CareField(by.registrationNo, { v -> choices { it.copy(dnacprDecidedBy = by.copy(registrationNo = v)) } }, t("Registration number", "रजिस्ट्रेशन नंबर"), singleLine = true)
            CareField(by.decidedAt, { v -> choices { it.copy(dnacprDecidedBy = by.copy(decidedAt = v)) } }, t("Date and time decided", "तय करने की तारीख़ और समय"), singleLine = true, placeholder = "2026-10-29 10:30")
        }
        if (c.cpr == Cpr.ATTEMPT && (c.goalOfCare == GoalOfCare.C_COMFORT || (c.respCeiling != null && c.respCeiling < 5)))
            CareField(c.cprConflictReason, { v -> choices { it.copy(cprConflictReason = v) } }, t("Reason for attempting CPR with this goal or ceiling (V2)", "इस लक्ष्य या सीमा के साथ CPR का कारण (V2)"), minLines = 2)
        val device = d.clinical.implantedDevice
        if (device == ImplantedDevice.ICD || device == ImplantedDevice.CRT_D) PlanCard {
            SectionTitle(t("Shock device (ICD)", "झटका देने वाला यंत्र (ICD)"))
            ChipGroup(d.clinical.icdDeactivation.decision, IcdDecision.entries, { if (it == IcdDecision.DEACTIVATE) Bi("Switch off shocks", "झटके बंद करें") else Bi("Keep shocks on", "झटके चालू रखें") }, "icd") { v -> update { it.copy(clinical = it.clinical.copy(icdDeactivation = it.clinical.icdDeactivation.copy(decision = v))) } }
            PlanDateField(d.clinical.icdDeactivation.date, t("Decision date", "फ़ैसले की तारीख़"), { v -> update { it.copy(clinical = it.clinical.copy(icdDeactivation = it.clinical.icdDeactivation.copy(date = v))) } })
        }
    }
}

@Composable private fun TrialEditor(title: String, trial: TrialPlan?, defaultUnit: DurationUnit, tag: String, onChange: (TrialPlan) -> Unit) { val t = careText()
    val tr = trial ?: TrialPlan(durationUnit = defaultUnit)
    val date = tr.reviewAt.take(10); val time = tr.reviewAt.drop(11)
    PlanCard {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(t("A trial needs a length, a formal review date and time, and who will review. About 3 days is usual (3–7).", "ट्रायल के लिए अवधि, समीक्षा की तारीख़-समय और समीक्षा करने वाले चाहिए। आमतौर पर लगभग 3 दिन (3–7)।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        PlanNumberField(tr.durationValue.takeIf { it > 0 }, t("Length", "अवधि"), "$tag-duration") { onChange(tr.copy(durationValue = it ?: 0)) }
        ChipGroup(tr.durationUnit, DurationUnit.entries, PlanText::unit, "$tag-unit") { onChange(tr.copy(durationUnit = it ?: defaultUnit)) }
        PlanDateField(date, t("Review date", "समीक्षा की तारीख़"), { v -> onChange(tr.copy(reviewAt = listOf(v, time).filter(String::isNotBlank).joinToString(" "))) })
        CareField(time, { v -> onChange(tr.copy(reviewAt = listOf(date.ifBlank { LocalDate.now().toString() }, v.take(5)).filter(String::isNotBlank).joinToString(" "))) }, t("Review time", "समीक्षा का समय"), singleLine = true, placeholder = "10:00")
        CareField(tr.reviewers.joinToString(", "), { v -> onChange(tr.copy(reviewers = v.split(',').map(String::trim))) }, t("Reviewers (names, comma-separated)", "समीक्षा करने वाले (नाम, कॉमा से अलग)"), singleLine = true)
    }
}

@Composable private fun BreathingFields(d: EctprRecord, today: LocalDate, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("Highest breathing support", "साँस की सबसे ऊँची मदद"), c.respCeiling, (0..5).toList(), PlanText::ceiling, PlanText::ceilingMeaning, "ceiling") { v -> choices { it.copy(respCeiling = v, nivTrial = it.nivTrial.takeIf { _ -> (v ?: 0) >= 4 }) } }
        val level = c.respCeiling
        if (level != null && level >= 1) OutlinedTextField(c.maxOxygenLpm?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(), { s -> choices { it.copy(maxOxygenLpm = s.filter { ch -> ch.isDigit() || ch == '.' }.take(5).toDoubleOrNull()) } },
            label = { Text(t("Maximum oxygen (L/min)", "अधिकतम ऑक्सीजन (L/min)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper))
        if (level != null && level < 5) CheckLine(c.ceilingConditions.reversibleCauseException == true, { v -> choices { it.copy(ceilingConditions = it.ceilingConditions.copy(reversibleCauseException = v)) } },
            t("A new, reversible cause (such as choking or a drug reaction) is treated fully, above this ceiling", "नया और ठीक हो सकने वाला कारण (जैसे गले में अटकना या दवा का रिएक्शन) इस सीमा से ऊपर भी पूरा इलाज पाएगा"))
        if (level != null && level >= 1) PlanNumberField(c.ceilingConditions.spo2Floor, t("Oxygen level to aim for, at least (SpO₂ %)", "कम से कम ऑक्सीजन स्तर (SpO₂ %)"), "spo2-floor") { v -> choices { it.copy(ceilingConditions = it.ceilingConditions.copy(spo2Floor = v)) } }
        if (level != null && level >= 4) {
            PlanNumberField(c.ceilingConditions.nivMaxHours, t("NIV for at most (hours)", "NIV ज़्यादा से ज़्यादा (घंटे)"), "niv-hours") { v -> choices { it.copy(ceilingConditions = it.ceilingConditions.copy(nivMaxHours = v)) } }
            CheckLine(c.nivTrial != null, { on -> choices { it.copy(nivTrial = if (on) TrialPlan(3, DurationUnit.DAYS) else null) } }, t("NIV as a time-limited trial", "NIV तय समय के ट्रायल के रूप में"))
            c.nivTrial?.let { tr -> TrialEditor(t("NIV trial", "NIV ट्रायल"), tr, DurationUnit.DAYS, "niv-trial") { v -> choices { it.copy(nivTrial = v) } } }
        }
        if (c.hospitalTransfer?.kind == TransferKind.NO && level != null && level >= 3) CareField(c.ceilingConditions.homeNivReason, { v -> choices { it.copy(ceilingConditions = it.ceilingConditions.copy(homeNivReason = v), conflictReasons = it.conflictReasons + ("C1" to v)) } },
            t("Home NIV or other reason this works without hospital", "घर पर NIV या कोई और कारण कि यह अस्पताल के बिना चलेगा"))
    }
}

@Composable private fun OrganSupportFields(d: EctprRecord, today: LocalDate, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("Intensive care (ICU)", "इंटेंसिव केयर (ICU)"), c.icu?.kind, IcuKind.entries, PlanText::icu, tag = "icu") { v -> choices { it.copy(icu = v?.let { k -> Icu(k, if (k == IcuKind.TRIAL) it.icu?.trial ?: TrialPlan(3, DurationUnit.DAYS) else null) }) } }
        if (c.icu?.kind == IcuKind.TRIAL) TrialEditor(t("ICU trial", "ICU ट्रायल"), c.icu.trial, DurationUnit.DAYS, "icu-trial") { v -> choices { it.copy(icu = Icu(IcuKind.TRIAL, v)) } }
        OptionGroup(t("Drugs to support blood pressure", "BP सँभालने की दवाएँ"), c.vasopressors?.kind, VasopressorKind.entries, PlanText::vaso, tag = "vaso") { v -> choices { it.copy(vasopressors = v?.let { k -> Vasopressors(k, if (k == VasopressorKind.TRIAL) it.vasopressors?.trial ?: TrialPlan(durationUnit = DurationUnit.HOURS) else null, if (k == VasopressorKind.YES_MAX_AGENTS) it.vasopressors?.maxAgents ?: 1 else null) }) } }
        if (c.vasopressors?.kind == VasopressorKind.TRIAL) TrialEditor(t("Vasopressor trial", "BP दवा ट्रायल"), c.vasopressors.trial, DurationUnit.HOURS, "vaso-trial") { v -> choices { it.copy(vasopressors = Vasopressors(VasopressorKind.TRIAL, v)) } }
        if (c.vasopressors?.kind == VasopressorKind.YES_MAX_AGENTS) PlanNumberField(c.vasopressors.maxAgents, t("Maximum number of drugs", "दवाओं की अधिकतम संख्या"), "vaso-max") { v -> choices { it.copy(vasopressors = Vasopressors(VasopressorKind.YES_MAX_AGENTS, maxAgents = v)) } }
        OptionGroup(t("Dialysis", "डायलिसिस"), c.dialysis?.kind, DialysisKind.entries, PlanText::dialysis, tag = "dialysis") { v -> choices { it.copy(dialysis = v?.let { k -> Dialysis(k, if (k == DialysisKind.TRIAL) it.dialysis?.trial ?: TrialPlan(durationUnit = DurationUnit.SESSIONS) else null) }) } }
        if (c.dialysis?.kind == DialysisKind.TRIAL) TrialEditor(t("Dialysis trial", "डायलिसिस ट्रायल"), c.dialysis.trial, DurationUnit.SESSIONS, "dialysis-trial") { v -> choices { it.copy(dialysis = Dialysis(DialysisKind.TRIAL, v)) } }
    }
}

@Composable private fun FeedingFields(d: EctprRecord, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText(); val hindi = careHindi()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Mint, shape = RoundedCornerShape(14.dp)) { Text(AmdText.TUBE_NOTE.pick(hindi), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
        OptionGroup(t("Nose tube feeding (NG)", "नाक की नली से खाना (NG)"), c.feeding.ng?.kind, NgKind.entries, PlanText::ng, tag = "ng") { v -> choices { it.copy(feeding = it.feeding.copy(ng = v?.let { k -> NgFeeding(k, if (k == NgKind.UP_TO_DAYS) it.feeding.ng?.days ?: 7 else null) })) } }
        if (c.feeding.ng?.kind == NgKind.UP_TO_DAYS) PlanNumberField(c.feeding.ng.days, t("Days before review", "समीक्षा से पहले दिन"), "ng-days") { v -> choices { it.copy(feeding = it.feeding.copy(ng = NgFeeding(NgKind.UP_TO_DAYS, v))) } }
        OptionGroup(t("Stomach tube (PEG)", "पेट की नली (PEG)"), c.feeding.peg, PegFeeding.entries, PlanText::peg, tag = "peg") { v -> choices { it.copy(feeding = it.feeding.copy(peg = v)) } }
        CheckLine(c.feeding.removeExistingTube, { v -> choices { it.copy(feeding = it.feeding.copy(removeExistingTube = v)) } }, t("The patient or family wish an existing feeding tube to be removed", "मरीज़ या परिवार चाहते हैं कि लगी हुई खाने की नली हटाई जाए"))
        OptionGroup(t("Fluids", "तरल (पानी)"), c.hydration, Hydration.entries, PlanText::hydration, PlanText::hydrationMeaning, "hydration") { v -> choices { it.copy(hydration = v) } }
    }
}

@Composable private fun ConflictFields(d: EctprRecord, today: LocalDate, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText()
    val issues = EctprRules.check(d, today).filter { planStepOf(it.field) == 5 }
    val c = d.choices
    val needsReason = listOf("C1", "C2", "C3").filter { id -> issues.any { it.id == id } || !c.conflictReasons[id].isNullOrBlank() }
    if (issues.isEmpty() && needsReason.isEmpty() && !c.goalAConfirmed) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(t("Checks", "जाँच"))
        IssueList(issues)
        needsReason.forEach { id -> CareField(c.conflictReasons[id].orEmpty(), { v -> choices { it.copy(conflictReasons = it.conflictReasons + (id to v)) } }, t("Reason for $id", "$id का कारण"), minLines = 2) }
        if (issues.any { it.id == "V3" } || c.goalAConfirmed) CheckLine(c.goalAConfirmed, { v -> choices { it.copy(goalAConfirmed = v) } }, t("Confirmed: this combination is what was decided (V3)", "पुष्टि: यही मेल तय हुआ था (V3)"))
        if (c.feeding.removeExistingTube) Text(t("The app records this wish but never triggers withdrawal.", "ऐप यह इच्छा दर्ज करता है, पर इलाज हटाने का काम कभी शुरू नहीं करता।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
    }
}

private fun LazyListScope.reviewStep(d: EctprRecord, today: LocalDate, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { ReviewFields(d, today, update) } }

@Composable private fun ReviewFields(d: EctprRecord, today: LocalDate, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText(); val hindi = careHindi()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("After this date the ED view shows the plan as OUT OF DATE and standard care applies.", "इस तारीख़ के बाद ED व्यू योजना को पुरानी दिखाएगा और सामान्य इलाज लागू होगा।"), style = MaterialTheme.typography.bodyMedium)
        PlanDateField(d.reviewDue, t("Review due", "समीक्षा की तारीख़"), { v -> update { it.copy(reviewDue = v) } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4L, 8L, ReviewRules.ROUTINE_REVIEW_WEEKS).distinct().forEach { w -> AssistChip(onClick = { update { it.copy(reviewDue = today.plusWeeks(w).toString()) } }, label = { Text(t("In $w weeks", "$w हफ़्ते में")) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        SectionTitle(t("Review sooner if", "पहले समीक्षा करें अगर"))
        ReviewTrigger.entries.filter { it != ReviewTrigger.ROUTINE_INTERVAL }.forEach { Text("• " + PlanText.trigger(it).pick(hindi), style = MaterialTheme.typography.bodyMedium) }
        Text(t("Signs of the last days (falling blood pressure, cold mottled skin, changed breathing, little urine) also call for an urgent review. Where unsure, take a second opinion.", "आख़िरी दिनों के संकेत (BP गिरना, ठंडी धब्बेदार त्वचा, साँस का बदलना, कम पेशाब) पर भी तुरंत समीक्षा करें। शक हो तो दूसरी राय लें।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
    }
}

private fun LazyListScope.signatureStep(d: EctprRecord, clinician: Boolean, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { SignatureFields(d, clinician, update) } }

@Composable private fun SignatureFields(d: EctprRecord, clinician: Boolean, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText(); val hindi = careHindi()
    var consultant by rememberSaveable { mutableStateOf("") }; var reg by rememberSaveable { mutableStateOf("") }
    var coName by rememberSaveable { mutableStateOf("") }; var coReg by rememberSaveable { mutableStateOf("") }; var coRole by rememberSaveable { mutableStateOf(SignatureRole.PALLIATIVE_CLINICIAN.name) }
    var signer by rememberSaveable { mutableStateOf("") }; var signerRole by rememberSaveable { mutableStateOf(if (d.surrogateDeclaration.answeringAsSurrogate) SurrogateRank.PRIMARY.name else SignatureRole.PATIENT.name) }; var thumb by rememberSaveable { mutableStateOf(false) }
    var witness by rememberSaveable { mutableStateOf("") }
    fun add(s: Signature) = update { r -> r.copy(signatures = (if (s.role == SignatureRole.TREATING_CONSULTANT) r.signatures.filter { it.role != SignatureRole.TREATING_CONSULTANT } else r.signatures) + s) }
    fun roleLabel(r: SignatureRole) = when (r) {
        SignatureRole.TREATING_CONSULTANT -> Bi("Treating consultant", "इलाज करने वाले कंसल्टेंट"); SignatureRole.PALLIATIVE_CLINICIAN -> Bi("Palliative clinician", "पैलिएटिव डॉक्टर"); SignatureRole.NURSE -> Bi("Nurse", "नर्स")
        SignatureRole.PATIENT -> Bi("Patient", "मरीज़"); SignatureRole.SURROGATE -> Bi("Decision-maker", "फ़ैसला लेने वाले"); SignatureRole.WITNESS -> Bi("Witness", "गवाह")
    }
    fun rankLabel(rank: SurrogateRank) = when (rank) { SurrogateRank.PRIMARY -> Bi("Primary decision-maker", "मुख्य फ़ैसला लेने वाले"); SurrogateRank.SECONDARY -> Bi("Secondary decision-maker", "द्वितीय फ़ैसला लेने वाले") }
    fun signerLabel(value: String) = SurrogateRank.entries.firstOrNull { it.name == value }?.let(::rankLabel) ?: roleLabel(SignatureRole.PATIENT)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("An unsigned form is a conversation note, not a plan. It becomes active only when the treating consultant signs with their registration number. Keep the signed paper copy with the family.", "बिना हस्ताक्षर का फ़ॉर्म बातचीत का नोट है, योजना नहीं। यह तभी लागू होता है जब इलाज करने वाले कंसल्टेंट अपने रजिस्ट्रेशन नंबर के साथ हस्ताक्षर करें। हस्ताक्षरित काग़ज़ी कॉपी परिवार के पास रखें।"), style = MaterialTheme.typography.bodyMedium)
        if (d.signatures.isNotEmpty()) PlanCard {
            SectionTitle(t("Signed so far", "अब तक हस्ताक्षर"))
            d.signatures.forEachIndexed { i, s -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${(s.surrogateRank?.let(::rankLabel) ?: roleLabel(s.role)).pick(hindi)}: ${s.name}${s.registrationNo?.let { " · $it" } ?: ""}${if (s.thumbImpression == true) t(" · thumb impression", " · अँगूठे का निशान") else ""} · ${s.signedAt.take(16).replace('T', ' ')}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { update { r -> r.copy(signatures = r.signatures.filterIndexed { j, _ -> j != i }) } }) { Icon(Icons.Default.Close, t("Remove signature", "हस्ताक्षर हटाएँ")) }
            } }
        }
        PlanCard {
            SectionTitle(t("Treating consultant", "इलाज करने वाले कंसल्टेंट"))
            if (!clinician) Text(t("To be signed by the doctor on their own phone or here in their presence.", "डॉक्टर अपने फ़ोन पर या यहाँ अपनी मौजूदगी में हस्ताक्षर करें।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
            CareField(consultant, { consultant = it }, t("Consultant's name", "कंसल्टेंट का नाम"), singleLine = true)
            CareField(reg, { reg = it }, t("Medical registration number", "मेडिकल रजिस्ट्रेशन नंबर"), singleLine = true)
            Button(onClick = { add(Signature(SignatureRole.TREATING_CONSULTANT, consultant.trim(), reg.trim(), planNow())); consultant = ""; reg = "" }, enabled = consultant.isNotBlank() && reg.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign-consultant")) { Text(t("Sign as treating consultant", "इलाज करने वाले कंसल्टेंट के रूप में हस्ताक्षर करें")) }
        }
        PlanCard {
            SectionTitle(t("Co-signature (optional)", "सह-हस्ताक्षर (वैकल्पिक)"))
            ChipGroup(SignatureRole.valueOf(coRole), listOf(SignatureRole.PALLIATIVE_CLINICIAN, SignatureRole.NURSE), ::roleLabel, "cosign-role") { coRole = (it ?: SignatureRole.PALLIATIVE_CLINICIAN).name }
            CareField(coName, { coName = it }, t("Name", "नाम"), singleLine = true); CareField(coReg, { coReg = it }, t("Registration number", "रजिस्ट्रेशन नंबर"), singleLine = true)
            OutlinedButton(onClick = { add(Signature(SignatureRole.valueOf(coRole), coName.trim(), coReg.trim().ifBlank { null }, planNow())); coName = ""; coReg = "" }, enabled = coName.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Add co-signature", "सह-हस्ताक्षर जोड़ें")) }
        }
        PlanCard {
            SectionTitle(t("Patient or decision-maker", "मरीज़ या फ़ैसला लेने वाले"))
            ChipGroup(signerRole, listOf(SignatureRole.PATIENT.name, SurrogateRank.PRIMARY.name, SurrogateRank.SECONDARY.name), ::signerLabel, "signer-role") { signerRole = it ?: SignatureRole.PATIENT.name }
            CareField(signer, { signer = it }, t("Name", "नाम"), singleLine = true)
            CheckLine(thumb, { thumb = it }, t("Signed with a thumb impression", "अँगूठे के निशान से हस्ताक्षर"))
            OutlinedButton(onClick = { val rank = SurrogateRank.entries.firstOrNull { it.name == signerRole }; add(Signature(if (rank == null) SignatureRole.PATIENT else SignatureRole.SURROGATE, signer.trim(), null, planNow(), thumb.takeIf { it }, rank)); signer = ""; thumb = false }, enabled = signer.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Add signature", "हस्ताक्षर जोड़ें")) }
            CheckLine(d.limitedLiteracy, { v -> update { it.copy(limitedLiteracy = v) } }, t("Limited reading or writing", "पढ़ना-लिखना कम आता है"))
        }
        if (d.limitedLiteracy || d.signatures.any { it.thumbImpression == true } || d.witnesses.isNotEmpty()) PlanCard {
            SectionTitle(t("Witnesses (two needed)", "गवाह (दो चाहिए)"))
            Text(t("${d.witnesses.size} of 2 recorded", "2 में से ${d.witnesses.size} दर्ज"), style = MaterialTheme.typography.bodySmall)
            Text(t("Needed when the patient signs by thumb impression or has limited literacy, for both home-based and hospital-based evaluation. Preferably people who are not decision-makers.", "जब मरीज़ अँगूठे से साइन करें या ठीक से पढ़ न सकें तब ज़रूरी, घर और अस्पताल दोनों जगह की जाँच में। बेहतर है कि ये फ़ैसला लेने वाले न हों।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
            CareField(witness, { witness = it }, t("Witness name", "गवाह का नाम"), singleLine = true)
            OutlinedButton(onClick = { add(Signature(SignatureRole.WITNESS, witness.trim(), null, planNow())); witness = "" }, enabled = witness.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-witness")) { Text(t("Add witness", "गवाह जोड़ें")) }
        }
    }
}

/** What was added by the legal review, on the check-and-sign step, so it is seen before signing. */
@Composable private fun PlanSummaryExtras(d: EctprRecord) { val t = careText(); val hindi = careHindi()
    val lines = listOfNotNull(d.decisionMakers.alternate?.takeUnless { it.blank }?.let { Bi.labeled(Bi("Secondary decision-maker", "द्वितीय फ़ैसला लेने वाले"), Bi.plain(listOf(it.name, it.relation, it.phone24h).filter(String::isNotBlank).joinToString(" · "))) }) +
        PlanText.fullRecordLines(d) + PlanText.homeCareLines(d) + PlanText.withdrawalLines(d)
    if (lines.isEmpty()) return
    PlanCard {
        SectionTitle(t("Full record", "पूरा रिकॉर्ड"))
        lines.forEach { Text(it.pick(hindi), style = MaterialTheme.typography.bodyMedium) }
    }
}

// ───────────────────────────── ED Quick View ─────────────────────────────

@Composable private fun EdQuickViewPage(state: CarePlanState, patientId: String, onBack: () -> Unit, onCard: () -> Unit, onShare: () -> Unit, onViewed: (String) -> Unit) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    val interp = EdView.forPatient(state.ectprs, patientId, today)
    val r = interp.record
    LaunchedEffect(r?.id) { onViewed(r?.id ?: "none") }
    val amdLine = AmdLifecycle.edSummary(state.amds, patientId, r?.existingDirective)
    LazyColumn(Modifier.fillMaxSize().testTag("ed-quick-view"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PlanHeader(t("ED Quick View", "ED क्विक व्यू"), onBack, "ed-header") }
        item { PlanReadAloud({ PlanSpeech.edView(interp, amdLine, hindi) }, "ed-$patientId") }
        if (r != null) item { Text(listOfNotNull(r.patient.name.ifBlank { null }, r.patient.uhid.takeIf { it.isNotBlank() }?.let { "UHID $it" }, PlanDates.age(r.patient.dob, today)?.let { t("$it years", "$it साल") }).joinToString(" · "), fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
        item { Surface(color = if (interp.kind == EdKind.ACTIVE) DoctorStatusGoodBg else DoctorStatusRestBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("ed-banner-${interp.kind.name.lowercase()}")) {
            Text(interp.banner.pick(hindi), Modifier.padding(14.dp).announce(), fontWeight = FontWeight.Bold, color = if (interp.kind == EdKind.ACTIVE) DoctorStatusGood else DoctorStatusRest)
        } }
        if (interp.flags.isNotEmpty()) item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("ed-flags")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { interp.flags.forEach { Text(it.pick(hindi), fontWeight = FontWeight.SemiBold, color = RustText) } }
        } }
        // An active plan leads with its decisions (tech spec §6), so the always-on box is the short form there; every condition stays on screen.
        item { IfInDoubtBox(compact = interp.kind == EdKind.ACTIVE) }
        if (r != null && interp.showContent) {
            val rows = EdView.rows(r, amdLine, CarePlanBook.openReview(state, r.id), AmdLifecycle.governs(state.amds, patientId, r.existingDirective))
            items(rows, key = { it.key }) { row -> EdRowView(row) }
            val fullRecord = PlanText.fullRecordLines(r)
            if (fullRecord.isNotEmpty()) item { InfoDisclosure(t("Full record", "पूरा रिकॉर्ड")) { fullRecord.forEach { Text(it.pick(hindi), style = MaterialTheme.typography.bodyMedium) } } }
            if (r.choices.feeding.removeExistingTube) item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp)) { Text(t("Wish recorded to remove an existing feeding tube: that is withdrawal and follows the medical board pathway. Not actionable from this plan.", "लगी हुई खाने की नली हटाने की इच्छा दर्ज है: यह इलाज वापस लेना है और मेडिकल बोर्ड की प्रक्रिया से होता है। इस योजना से नहीं किया जा सकता।"), Modifier.padding(12.dp)) } }
        } else {
            item { Text(t("No plan content is shown: give full standard emergency care.", "योजना की जानकारी नहीं दिखाई जाती: पूरा सामान्य आपातकालीन इलाज दें।"), style = MaterialTheme.typography.bodyMedium) }
            // A living will stands on its own: it is shown whatever state the plan is in.
            item { EdRowView(EdRow("AMD", Bi("Living will (AMD)", "लिविंग विल (AMD)"), amdLine, flag = AmdLifecycle.governs(state.amds, patientId, r?.existingDirective))) }
        }
        item { LegalPendingBox() }
        item { CareContinuesBox() }
        if (r != null) item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCard, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(6.dp)); Text(t("Wallet card", "वॉलेट कार्ड")) }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text(t("Share PDF", "PDF साझा करें")) }
        } }
    }
}

@Composable private fun EdRowView(row: EdRow) { val hindi = careHindi()
    Row(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).border(1.dp, if (row.flag) Rust else DoctorHairline, RoundedCornerShape(14.dp)).padding(12.dp).testTag("ed-row-${row.key.lowercase()}"), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(row.label.pick(hindi), style = MaterialTheme.typography.labelMedium, color = DoctorCardSub)
            Text(row.value.pick(hindi), fontWeight = if (row.value == NO_PREFERENCE || row.value == NOT_RECORDED) FontWeight.Normal else FontWeight.Bold, fontSize = 16.sp, color = if (row.flag) RustText else Ink)
        }
    }
}

// ───────────────────────────── Wallet card ─────────────────────────────

@Composable internal fun PlanQr(url: String, description: String, modifier: Modifier = Modifier, level: ErrorCorrectionLevel = ErrorCorrectionLevel.Q) {
    val matrix = remember(url, level) { Encoder.encode(url, level, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).matrix }
    Canvas(modifier.aspectRatio(1f).background(Color.White).semantics { contentDescription = description }) {
        val quiet = 4; val cell = size.width / (matrix.width + 2 * quiet)
        for (row in 0 until matrix.height) for (col in 0 until matrix.width) if (matrix.get(col, row).toInt() == 1)
            drawRect(Color.Black, Offset((col + quiet) * cell, (row + quiet) * cell), Size(cell + .5f, cell + .5f))
    }
}

/** Honest about where the card stands: on the server, or on this phone only. */
@Composable private fun CardStatus(card: CardToken, online: Boolean, onRetry: () -> Unit) { val t = careText()
    val issued = card.server == CardServerState.ISSUED
    Surface(color = if (issued) DoctorStatusGoodBg else DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag(if (issued) "card-issued" else "card-local-only")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (issued) t("Issued on the server", "सर्वर पर जारी") else t("Saved on this phone only", "सिर्फ़ इसी फ़ोन पर सहेजा गया"), fontWeight = FontWeight.Bold)
        Text(if (issued) t("A doctor who scans the link QR sees the ED Quick View. A copy is also saved on this phone.", "लिंक QR स्कैन करने वाले डॉक्टर को ED क्विक व्यू दिखता है। एक कॉपी इस फ़ोन पर भी है।")
            else t("The link QR will not open for a doctor until it is uploaded. It uploads by itself when the phone is online. Until then use the offline QR and the printed lines.", "अपलोड होने तक लिंक QR डॉक्टर के लिए नहीं खुलेगा। फ़ोन ऑनलाइन होते ही यह अपने-आप अपलोड होगा। तब तक ऑफ़लाइन QR और छपी लाइनों का इस्तेमाल करें।"), style = MaterialTheme.typography.bodyMedium)
        if (!issued) {
            card.serverMessage?.let { Text(if (it == EdCardApi.NETWORK) t("No internet right now.", "अभी इंटरनेट नहीं है।") else it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("card-server-message")) }
            if (online) TextButton(onClick = onRetry, modifier = Modifier.testTag("card-retry")) { Text(t("Try again now", "अभी फिर कोशिश करें")) }
            else Text(t("Practice mode: nothing is sent to the server.", "अभ्यास मोड: सर्वर पर कुछ नहीं भेजा जाता।"), style = MaterialTheme.typography.bodySmall)
        }
    } }
}

@Composable private fun WalletCardPage(state: CarePlanState, patientId: String, online: Boolean, canEdit: Boolean, onRetry: () -> Unit, onReissue: () -> Unit, onBack: () -> Unit, onShare: () -> Unit, onViewed: (String) -> Unit) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    val active = EctprLifecycle.active(state.ectprs, patientId)
    val token = active?.let { CardTokens.live(state.tokens, it.id) }
    val amdNow = AmdLifecycle.statusSummary(state.amds, patientId)
    val withdrawnPending = state.tokens.count { it.patientId == patientId && !it.live && it.pending }
    LaunchedEffect(active?.id) { active?.let { onViewed(it.id) } }
    LazyColumn(Modifier.fillMaxSize().testTag("wallet-card"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PlanHeader(t("Wallet card", "वॉलेट कार्ड"), onBack, "wallet-header") }
        if (withdrawnPending > 0) item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("card-revoke-pending")) { Text(t("Withdrawn on this phone. The server has not been told yet, so an old QR may still open. It will be told when the phone is online.", "इस फ़ोन पर वापस ली गई। सर्वर को अभी नहीं बताया गया, इसलिए पुराना QR अभी खुल सकता है। फ़ोन ऑनलाइन होने पर उसे बता दिया जाएगा।"), Modifier.padding(12.dp)) } }
        if (active == null || token == null) item { Surface(color = Peach, shape = RoundedCornerShape(16.dp)) { Text(t("A wallet card is made when a signed plan is active. A withdrawn or replaced plan's card is switched off.", "वॉलेट कार्ड तब बनता है जब हस्ताक्षरित योजना लागू हो। वापस ली गई या बदली गई योजना का कार्ड बंद हो जाता है।"), Modifier.padding(14.dp)) } }
        else {
            item { CardStatus(token, online, onRetry) }
            item { Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(2.dp, Ink), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("SAHARA EMERGENCY CARE PLAN", "सहारा आपातकालीन देखभाल योजना"), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    WalletCard.lines(active).forEach { (label, value) -> Row { Text(label.pick(hindi) + ": ", style = MaterialTheme.typography.bodySmall, color = DoctorCardSub); Text(value.pick(hindi), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) } }
                    Surface(color = PlanAlert, shape = RoundedCornerShape(10.dp)) { Text(WalletCard.TREAT_LINE.pick(hindi), Modifier.padding(8.dp), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) }
                    Text(t("Link QR: full card, needs internet", "लिंक QR: पूरा कार्ड, इंटरनेट चाहिए"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    PlanQr(token.url, t("QR code with a link to this plan", "इस योजना के लिंक वाला QR कोड"), Modifier.fillMaxWidth(.7f).align(Alignment.CenterHorizontally).testTag("wallet-qr"))
                    Text(t("Offline QR: this summary as text, no internet needed", "ऑफ़लाइन QR: यह सारांश लिखित रूप में, इंटरनेट नहीं चाहिए"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    PlanQr(EdCardOffline.forQr(active, amdNow, hindi, today), t("QR code: offline text summary of this card", "QR कोड: इस कार्ड का ऑफ़लाइन सारांश"), Modifier.fillMaxWidth(.8f).align(Alignment.CenterHorizontally).testTag("wallet-offline-qr"), ErrorCorrectionLevel.L)
                    run { val v = active.version; val due = active.reviewDue; Text(t("Plan v$v · review due $due", "योजना v$v · समीक्षा $due"), style = MaterialTheme.typography.labelSmall) }
                }
            } }
            val issuedAmd = runCatching { org.json.JSONObject(token.snapshot).optJSONObject("amdAtIssue") }.getOrNull()
            if (issuedAmd?.optString("status").orEmpty() != amdNow?.status?.name.orEmpty() || (amdNow != null && issuedAmd?.optInt("version") != amdNow.version)) item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("card-amd-changed")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("The living will changed after this card was issued, so the link shows the old status. Issue a new card and tear up the old one.", "यह कार्ड जारी होने के बाद लिविंग विल बदली है, इसलिए लिंक पुरानी स्थिति दिखाता है। नया कार्ड जारी करें और पुराना फाड़ दें।"), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onReissue, enabled = canEdit, modifier = Modifier.heightIn(min = 48.dp).testTag("card-reissue")) { Text(t("Issue a new card", "नया कार्ड जारी करें")) }
            } } }
            item { Text(t("The link QR holds only a random link with no name or medical details. The offline QR holds the summary above as plain text, so anyone who scans it can read it; keep the card with the patient. When the plan is replaced or withdrawn the link is switched off, but the offline QR on old paper still reads — tear up old cards.", "लिंक QR में सिर्फ़ एक रैंडम लिंक है, नाम या इलाज की जानकारी नहीं। ऑफ़लाइन QR में ऊपर का सारांश सादे अक्षरों में है, इसलिए जो भी स्कैन करे वह पढ़ सकता है; कार्ड मरीज़ के पास रखें। योजना बदलने या वापस लेने पर लिंक बंद हो जाता है, पर पुराने काग़ज़ का ऑफ़लाइन QR फिर भी पढ़ा जा सकता है — पुराने कार्ड फाड़ दें।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub) }
            item { Button(onClick = onShare, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text(t("Print or share the plan and card (PDF)", "योजना और कार्ड प्रिंट या साझा करें (PDF)")) } }
        }
    }
}

/** Entry card for Today (patient, family and doctor). */
@Composable fun CarePlanEntryCard(onOpen: () -> Unit) { val t = careText()
    ActionCard(SaharaSoftBlue, Modifier.fillMaxWidth().testTag("care-plan-entry"), onClick = onOpen) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalHospital, null, tint = Rust); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(t("Emergency plan & living will", "आपातकालीन योजना और लिविंग विल"), fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(t("ED Quick View, wallet card QR and living will", "ED क्विक व्यू, वॉलेट कार्ड QR और लिविंग विल"), fontSize = 13.sp) }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp))
        }
    }
}

