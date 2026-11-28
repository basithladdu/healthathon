@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.style.TextAlign
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

internal const val ED_VIEW_DETAIL = "ED Quick View"
private const val CARD_VIEW_DETAIL = "wallet card"
private fun planPdfName(version: Int) = "sahara-plan-v$version.pdf"
/** Same content, ignoring when it was last saved. */
internal fun samePlan(a: EctprRecord, b: EctprRecord) = a.copy(updatedAt = "") == b.copy(updatedAt = "")
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
    field == "reviewDue" -> 6
    else -> 7
}

/**
 * Emergency care plan (ECTPR) and living will (AMD), offline on this phone. Reached from Today and More (family and patient)
 * and from the doctor's patient tools. Pages: home, plan wizard, goals of care (the doctor's Goals of care tab), ED Quick View, wallet card, living will.
 */
@Composable fun CarePlanScreen(vm: CareModel, clinician: Boolean, onBack: () -> Unit, startAt: String = "", onGoalsOfCare: (() -> Unit)? = null) {
    val context = LocalContext.current; val t = careText(); val hindi = careHindi(); val scope = rememberCoroutineScope()
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.id == selected }
    val store = remember(vm.storageNamespace) { CarePlanStore(context.applicationContext, vm.storageNamespace) }
    var loadProblem by remember(selected) { mutableStateOf(false) }
    var state by remember(selected) { mutableStateOf(runCatching { store.load(selected) }.getOrElse { loadProblem = true; CarePlanState() }) }
    var page by rememberSaveable(selected) { mutableStateOf("home") }
    // The talk's choices that the plan's rules blocked from saving: the full plan opens with them, so they can be fixed there.
    var gocPending by remember(selected) { mutableStateOf<EctprRecord?>(null) }
    // startAt "goc": the goals-of-care talk as its own page (a draft is opened if none is); the full plan is one named tap away.
    val actor = remember(clinician) { if (clinician) "Doctor" else vm.viewRole.value }
    val signedInDoctor = rememberSignedInDoctor(vm, clinician)
    // A new plan starts with what the patient profile already holds (name, sex, phone, address, illness, allergies).
    val items by vm.items.collectAsStateWithLifecycle()
    fun profile(): CarePatientProfile? = PatientProfileCodec.latest(items, selected)?.let { PatientProfileCodec.decode(it.detail) }
        ?: runCatching { CarePatientProfileStore(context, vm.storageNamespace).loadOrNull(selected) }.getOrNull()
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
            // No offline text QR on paper (meeting, 24 Nov): text in a QR opens in a notes app where anyone can edit it. Only the web link QR is printed.
            val file = runCatching { withContext(Dispatchers.IO) { File(store.folder(selected), planPdfName(r.version)).also { CarePlanPdf.plan(it, r, interp, AmdLifecycle.edSummary(r.existingDirective, amdSummary), card?.url, null, card?.server == CardServerState.ISSUED, hindi, today, CarePlanBook.openReview(state, r.id), AmdLifecycle.governs(state.amds, selected, r.existingDirective)) } } }.getOrNull()
            if (file == null) vm.message.value = t("Couldn't make the PDF. Try again.", "PDF नहीं बना। फिर कोशिश करें।")
            else { commit { CarePlanBook.printed(it, AuditRecordType.ECTPR, r.id, actor, planNow()) }; share(file, t("Share the emergency plan", "आपातकालीन योजना साझा करें"), AuditRecordType.ECTPR, r.id) }
        }
    }
    // Opening the goals-of-care talk with no draft: a signed plan carries over into a new version; with no plan at all, a first plan starts.
    fun gocStart(s: CarePlanState): CarePlanState = if (person != null && EctprLifecycle.active(s.ectprs, selected) != null) GocNewVersion.start(s, selected, actor, planNow(), today)
        else CarePlanBook.startPlan(s, selected, actor, planNow(), EctprPrefill.patient(person?.name.orEmpty(), profile()), clinical = EctprPrefill.clinical(profile())).first
    if (person == null) { Box(Modifier.fillMaxSize()); return }
    // Patients and families never open the doctor's talk or form (Dr Sujay, 22 Nov): their Goals of care tab is the signed note, read-only, and the way to ask for the conversation.
    // Clinical team, 24 Nov 2026: the patient side keeps only Goals of care. The emergency-room view, the pocket card and advance care planning
    // pages were duplicates for patients (the SOS sheet has the one emergency card), so they are hidden here; the doctor side keeps them all,
    // and nothing saved is removed. "What we do with your data" always stays on the patient's Care planning page: the doctor
    // said it is important (24 Nov), so it is not hidden even when this phone holds no plan yet.
    if (!clinician) {
        val fromCareTab = startAt != "goc"
        LazyColumn(Modifier.fillMaxSize().testTag(if (fromCareTab) "care-plan-home" else "goc-family-page"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "header", contentType = "header") { PlanHeader(t("Goals of care", "देखभाल के लक्ष्य"), onBack, if (fromCareTab) "care-plan-header" else "goc-family-header") }
            item(key = "goc-tile", contentType = "tile") { FamilyTile(t("Goals of care discussion", "देखभाल के लक्ष्यों की चर्चा"), Icons.Default.Forum, FamilyAccent.Indigo, Modifier.animateItem().testTag("care-plan-goc-tile")) { PatientGocStatus(vm, state.ectprs, person.id) } }
            if (fromCareTab) item(key = "dpdp", contentType = "dpdp") { Box(Modifier.animateItem()) { DpdpNoticeCard(DpdpScope.CARE_PLAN, state, person.id, store, actor != "Family", ::commit, actor) } }
        }
        return
    }
    // The way from anywhere else to the doctor's one Goals of care place: the section when the caller has one, else this screen's talk page.
    val openGoalsOfCare: () -> Unit = onGoalsOfCare ?: { if (EctprLifecycle.draft(state.ectprs, selected) != null || (canEdit && commit { gocStart(it) })) page = "goc" }
    if (startAt == "goc" && clinician) LaunchedEffect(selected) {
        if (page != "home") return@LaunchedEffect
        if (EctprLifecycle.draft(state.ectprs, selected) == null && canEdit) commit { gocStart(it) }
        if (EctprLifecycle.draft(state.ectprs, selected) != null) page = "goc"
    }
    val canManageData = clinician || actor != "Family"
    // Pages slide in from the end and back out to the plan home, the same motion as the rest of the app.
    // From the Goals of care tab, closing the full plan goes back to the talk, and closing the talk leaves the tab.
    val wizardBack = if (startAt == "goc") "goc" else "home"
    CompositionLocalProvider(LocalSignedInDoctor provides signedInDoctor) {
    SaharaScreenHost(page, Modifier.fillMaxSize(), label = "care-plan-page", direction = { from, to -> if (to == "home" || (from == "wizard" && to == "goc")) SaharaMotion.Direction.Back else SaharaMotion.Direction.Forward }) { shownPage ->
        // Inside the page host, so a back swipe previews the plan page that is closing, not the whole Care plan tab.
        SaharaBackHandler(shownPage != "home") { page = "home" }
        when (shownPage) {
        "goc" -> {
            val draft = EctprLifecycle.draft(state.ectprs, selected)
            if (draft == null) { LaunchedEffect(Unit) { page = "home" }; return@SaharaScreenHost }
            // D15: a request from the patient or family shows above the talk until a signed note answers it.
            val latestSigned = DoctorGocDiscussions.list(state.ectprs, selected).filter { it.signed }.mapNotNull { gocMillis(it.at) }.maxOrNull()
            Column(Modifier.fillMaxSize()) {
                DoctorGocRequestBanner(vm, selected, latestSigned)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalGocFolder provides runCatching { store.folder(selected) }.getOrNull()) { GocTabFlow(draft, canEdit,
                        onSave = { r -> commit { CarePlanBook.saveDraft(it, r, actor, planNow(), today) } },
                        onClose = { if (startAt == "goc") onBack() else page = "home" }, onFullPlan = { pending -> gocPending = pending; page = "wizard" },
                        onNewVersion = { commit { GocNewVersion.start(it, selected, actor, planNow(), today) } }) }
                }
            }
        }
        "wizard" -> {
            val draft = EctprLifecycle.draft(state.ectprs, selected)
            if (draft == null) { LaunchedEffect(Unit) { page = "home" }; return@SaharaScreenHost }
            CompositionLocalProvider(LocalGocFolder provides runCatching { store.folder(selected) }.getOrNull()) { EctprWizard(draft, clinician, canEdit, start = gocPending?.takeIf { it.id == draft.id } ?: draft, familyReadOnly = actor == "Family" && !clinician, personName = person.name,
                onSave = { r -> commit { CarePlanBook.saveDraft(it, r, actor, planNow(), today) } },
                onSign = { r -> commit { CarePlanBook.sign(CarePlanBook.saveDraft(it, r, actor, planNow(), today), r.id, actor, planNow(), today) }.also { ok -> if (ok) page = "ed" } },
                onClose = { gocPending = null; page = wizardBack },
                onGoc = if (clinician) ({ gocPending = null; if (startAt == "goc") page = "goc" else openGoalsOfCare() }) else null) }
        }
        "pilot" -> PilotLogPage(state, selected, actor, ED_VIEW_DETAIL, onBack = { page = "home" }, commit = ::commit)
        "ed" -> EdQuickViewPage(state, selected, onBack = { page = "home" }, onCard = { page = "card" }, onShare = ::sharePlanPdf,
            onViewed = { id -> commit { CarePlanBook.viewed(it, AuditRecordType.ECTPR, id, actor, planNow(), ED_VIEW_DETAIL) } })
        "card" -> WalletCardPage(state, selected, online, canEdit, emergencyUrl = remember(vm.storageNamespace, selected) { runCatching { EmergencyCardStore(context.applicationContext, vm.storageNamespace).load(selected)?.link?.url }.getOrNull() }, onRetry = ::pushCards, onReissue = { commit { CarePlanBook.reissueCard(it, selected, actor, planNow()) } }, onBack = { page = "home" }, onShare = ::sharePlanPdf,
            onViewed = { id -> commit { CarePlanBook.viewed(it, AuditRecordType.CARD, id, actor, planNow(), CARD_VIEW_DETAIL) } })
        "amd" -> LivingWillPage(state, person, store, canEdit, clinician, canManageData, actor, onBack = { page = "home" }, commit = ::commit, onShare = { file, id -> share(file, t("Share the advance care planning", "अग्रिम देखभाल योजना साझा करें"), AuditRecordType.AMD, id) })
        else -> CarePlanHome(state, person, clinician, canEdit, loadProblem, store, canManageData, actor, ::commit, transport.takeIf { online }, onBack = onBack, onPage = { page = it },
            vm = vm, onGoc = openGoalsOfCare,
            onStart = { inEd -> if (commit { CarePlanBook.startPlan(it, selected, actor, planNow(), EctprPrefill.patient(person.name, profile()), inEd, EctprPrefill.clinical(profile())).first }) page = "wizard" },
            onReview = { trigger -> if (commit { CarePlanBook.requestReview(it, selected, trigger, actor, planNow(), today).first }) page = "wizard" },
            onWithdraw = { id, reason, by -> if (commit { CarePlanBook.withdraw(it, id, reason, by, actor, planNow()) }) vm.message.value = t("Plan withdrawn", "योजना वापस ली गई") },
            onDiscard = { id -> commit { CarePlanBook.discard(it, id, actor, planNow()) } })
    } }
    }
}

@Composable internal fun PlanHeader(title: String, onBack: () -> Unit, tag: String) { val t = careText()
    // A stock Material top app bar (25 Nov redesign): title left, back arrow, default colours. It sits as the list's first item,
    // so the plan's many pages keep their own lists; the app shell already keeps the status bar clear.
    TopAppBar(title = { Text(title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.a11yHeading()) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("$tag-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) } },
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
}

/** Always on: clinical spec §6 / Sahaara §1. */
@Composable internal fun IfInDoubtBox(compact: Boolean = false) { val hindi = careHindi()
    Surface(color = RustText, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("if-in-doubt")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text(IfInDoubt.TITLE.pick(hindi), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (!compact) {
                IfInDoubt.CONDITIONS.forEach { Text("• " + it.pick(hindi), color = Color.White, style = MaterialTheme.typography.bodyMedium) }
                Text(IfInDoubt.AFTER.pick(hindi), color = Color.White, fontWeight = FontWeight.SemiBold)
            } else Text((IfInDoubt.CONDITIONS + IfInDoubt.AFTER).joinToString(" ") { it.pick(hindi) }, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** V9: printed on every record and shown on every ED view. */
@Composable internal fun CareContinuesBox() { val hindi = careHindi()
    Surface(color = Mint, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("care-continues")) {
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
            Surface(color = if (warn) DoctorStatusWarnBg else DoctorStatusRestBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (warn) Icons.Default.Info else Icons.Default.ErrorOutline, null, tint = if (warn) DoctorStatusWarn else DoctorStatusRest, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(i.text(hindi), style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
        }
    }
}

@Composable internal fun PlanOption(label: String, meaning: String?, selected: Boolean, tag: String, onClick: () -> Unit) {
    // A selectable card: radio mark on the left, label, then one line of meaning. Selected = sage fill and an ink border.
    // The fill and border ease to the new choice, the mark gives one small bounce, and the phone answers with a light tap.
    val tick = rememberSaharaTick()
    val fill by animateColorAsState(if (selected) Sage else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "plan-option-fill")
    val edge by animateColorAsState(if (selected) Ink else Ink.copy(alpha = .3f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "plan-option-edge")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill).border(if (selected) 2.dp else 1.dp, edge, RoundedCornerShape(12.dp))
        .selectable(selected, role = Role.RadioButton, onClick = { if (!selected) tick(); onClick() }).heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 12.dp).testTag(tag), verticalAlignment = Alignment.Top) {
        Icon(if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, Modifier.padding(top = 1.dp).saharaSelectBounce(selected).size(22.dp), tint = if (selected) Ink else DoctorCardSub)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
            if (!meaning.isNullOrBlank()) Text(meaning, fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub)
        }
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
    val tick = rememberSaharaTick()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o -> val on = value == o
            FilterChip(on, { tick(); onChange(if (on) null else o) }, label = { Text(label(o).pick(hindi), fontSize = 15.sp) },
                leadingIcon = if (on) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null,
                shape = RoundedCornerShape(50), border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, borderColor = Ink.copy(alpha = .3f), selectedBorderColor = Ink, selectedBorderWidth = 1.5.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = Ink, selectedContainerColor = Sage, selectedLabelColor = Ink, selectedLeadingIconColor = Ink),
                modifier = Modifier.saharaSelectBounce(on, .08f).heightIn(min = 48.dp).testTag("$tag-${o.toString().lowercase()}")) }
    }
}

/**
 * A whole number in the plan. With [range] it is a scroll wheel opening on [start] (owner, 25 Nov: numbers are tapped, not typed); without
 * one (money and other open-ended counts for clinicians) it stays a number field.
 */
@Composable internal fun PlanNumberField(value: Int?, label: String, tag: String, range: IntRange? = null, unit: String = "", start: Int? = null, onChange: (Int?) -> Unit) {
    if (range != null) {
        CareNumberWheelButton(value?.toDouble(), { onChange(it?.toInt()) }, label, unit, CareNumberScale(range.first.toDouble(), range.last.toDouble(), 1.0),
            (start ?: range.first).toDouble(), tag, Modifier.fillMaxWidth(), allowClear = true)
        return
    }
    OutlinedTextField(value?.toString().orEmpty(), { s -> onChange(s.filter(Char::isDigit).take(4).toIntOrNull()) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper))
}

/**
 * The previous admission is saved as one line of text: the hospital, then " · " and the day in English ("Apollo · 3 Mar 2025").
 * An older line typed freely ("Apollo in March") reads back as the hospital with no day, so nothing written is lost.
 */
internal object PlanAdmission {
    private val format = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)
    private const val SEP = " · "
    fun split(details: String): Pair<String, LocalDate?> {
        val at = details.lastIndexOf(SEP)
        val day = if (at >= 0) runCatching { LocalDate.parse(details.substring(at + SEP.length).trim(), format) }.getOrNull() else
            runCatching { LocalDate.parse(details.trim(), format) }.getOrNull()
        return when { day == null -> details to null; at >= 0 -> details.substring(0, at) to day; else -> "" to day }
    }
    /** [where] is kept exactly as typed (a space mid-word is not lost while typing). */
    fun join(where: String, day: LocalDate?): String = listOf(where, day?.format(format).orEmpty()).filter(String::isNotBlank).joinToString(SEP)
}

@Composable internal fun PlanDateField(value: String, label: String, onChange: (String) -> Unit, latest: LocalDate? = null) {
    CareDateButton(PlanDates.day(value)?.let(::careEpoch) ?: 0L, label, { onChange(if (it == 0L) "" else careDay(it).toString()) }, allowClear = true, latest = latest)
}

/** An optional part of a form that opens in place (a field asked only after a choice): it unfolds instead of popping in. */
@Composable internal fun ColumnScope.PlanReveal(visible: Boolean, gap: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap.dp), content = content) }
}

@Composable internal fun PlanCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

// ───────────────────────────── Home ─────────────────────────────

@Composable private fun CarePlanHome(state: CarePlanState, person: Person, clinician: Boolean, canEdit: Boolean, loadProblem: Boolean, store: CarePlanStore, canManage: Boolean, actor: String, commit: ((CarePlanState) -> CarePlanState) -> Boolean, transport: EdCardTransport?, onBack: () -> Unit, onPage: (String) -> Unit, vm: CareModel, onGoc: () -> Unit,
    onStart: (Boolean) -> Unit, onReview: (ReviewTrigger) -> Unit, onWithdraw: (String, String, String) -> Unit, onDiscard: (String) -> Unit) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    val active = EctprLifecycle.active(state.ectprs, person.id); val draft = EctprLifecycle.draft(state.ectprs, person.id)
    val interp = EdView.forPatient(state.ectprs, person.id, today)
    // The version register is sorted once per change to the plans, not on every scroll frame.
    val versions = remember(state.ectprs, person.id) { state.ectprs.filter { it.patientId == person.id }.sortedByDescending { it.version } }
    var dialog by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().testTag("care-plan-home"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header", contentType = "header") { PlanHeader(t("Care planning", "देखभाल योजना"), onBack, "care-plan-header") }
        if (loadProblem) item(key = "load-problem", contentType = "banner") { Surface(color = Peach, shape = RoundedCornerShape(12.dp), modifier = Modifier.animateItem()) { Text(t("Couldn't read the saved plan.", "सहेजी योजना पढ़ी नहीं जा सकी।"), Modifier.padding(12.dp)) } }
        // One family of three clinical documents, each with a one-line plain description.
        item(key = "intro", contentType = "intro") { Text(t("Goals of care discussion, advance care planning and the emergency care plan belong together. All three are clinical documents.", "देखभाल के लक्ष्यों की चर्चा, अग्रिम देखभाल योजना और आपातकालीन देखभाल योजना साथ चलती हैं। तीनों क्लिनिकल दस्तावेज़ हैं।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub, modifier = Modifier.testTag("care-plan-family-intro")) }
        // D15 on the doctor's plan home too: the patient's or family's open request, until a signed note answers it.
        if (clinician) item(key = "goc-request", contentType = "notice") { DoctorGocRequestNotice(vm, person.id, onOpen = onGoc.takeIf { canEdit || draft != null }, modifier = Modifier.animateItem(), ectprs = state.ectprs) }
        item(key = "goc-tile", contentType = "tile") { FamilyTile(t("Goals of care discussion", "देखभाल के लक्ष्यों की चर्चा"), Icons.Default.Forum, FamilyAccent.Indigo, Modifier.animateItem().testTag("care-plan-goc-tile")) {
            Text(t("The recorded conversation about what matters most and the care to aim for.", "सबसे ज़रूरी क्या है और किस देखभाल का लक्ष्य रखें, इस पर दर्ज की गई बातचीत।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
            // One Goals of care place (Dr Sujay, 22 Nov): here the doctor sees the versions and goes there; the family reads the signed note and can ask for the talk.
            if (clinician) DoctorGocHistory(remember(state.ectprs, person.id) { DoctorGocDiscussions.list(state.ectprs, person.id) }, onOpenGoalsOfCare = onGoc.takeIf { canEdit || draft != null })
            else PatientGocStatus(vm, state.ectprs, person.id)
        } }
        item(key = "ed-entry", contentType = "entry") { ActionCard(if (interp.kind == EdKind.ACTIVE) Mint else Peach, Modifier.animateItem().fillMaxWidth().testTag("care-plan-ed-entry"), onClick = { onPage("ed") }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, null, tint = Rust); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("For the emergency room", "आपातकालीन कक्ष के लिए"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(t("A one-page summary hospital staff can read fast", "एक पन्ने का सारांश, जिसे अस्पताल के लोग जल्दी पढ़ सकें"), fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub)
                    // The plan's state at a glance: signed and in force, a draft, or none yet.
                    FamilyPill(when { active != null -> t("Plan v${active.version} active", "योजना v${active.version} लागू"); draft != null -> t("Draft", "मसौदा"); else -> t("No plan yet", "अभी योजना नहीं") },
                        when { active != null -> FamilyTone.GOOD; draft != null -> FamilyTone.WAIT; else -> FamilyTone.NEUTRAL }, Modifier.testTag("care-plan-status"))
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        } }
        item(key = "ectpr-tile", contentType = "tile") { FamilyTile(t("Emergency care plan & card", "आपातकालीन देखभाल योजना और कार्ड"), Icons.Default.LocalHospital, FamilyAccent.Marigold, Modifier.animateItem()) {
            Text(t("The summary for the ambulance or emergency department, with a QR code.", "एम्बुलेंस या आपातकालीन विभाग के लिए सारांश, QR कोड के साथ।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
            // Patients and families see where the plan stands; only the doctor starts, continues, reviews or withdraws it (Dr Sujay, 21 Nov).
            if (!clinician) Text(when {
                active != null -> t("Signed by your doctor (version ${active.version}). Show the pocket card in an emergency.", "आपके डॉक्टर ने साइन किया (संस्करण ${active.version})। आपात स्थिति में पॉकेट कार्ड दिखाएँ।")
                draft != null -> t("Your doctor is preparing this plan with you.", "आपके डॉक्टर आपके साथ यह योजना बना रहे हैं।")
                else -> t("Your doctor makes this plan with you.", "आपके डॉक्टर यह योजना आपके साथ बनाते हैं।")
            }, fontSize = 15.sp, color = DoctorCardInk, modifier = Modifier.testTag("care-plan-family-status"))
            else when {
                draft != null -> {
                    Button(onClick = { onPage("wizard") }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-continue")) { Text(t("Continue draft", "मसौदा जारी रखें")) }
                    TextButton(onClick = { dialog = "discard" }, enabled = canEdit) { Text(t("Discard draft", "मसौदा हटाएँ")) }
                }
                active != null -> {
                    Button(onClick = { dialog = "review" }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-review")) { Text(t("Review", "समीक्षा")) }
                    // The withdraw form unfolds in place of its button, and folds back on "Keep plan".
                    SaharaStateSwitch(dialog == "withdraw", Modifier.fillMaxWidth(), label = "care-plan-withdraw") { open ->
                        if (open) WithdrawPlanForm(active, onCancel = { dialog = "" }, onWithdraw = { reason, by -> dialog = ""; onWithdraw(active.id, reason, by) })
                        else TextButton(onClick = { dialog = "withdraw" }, enabled = canEdit, modifier = Modifier.testTag("care-plan-withdraw")) { Text(t("Withdraw", "वापस लें"), color = RustText) }
                    }
                }
                else -> Button(onClick = { if (clinician) dialog = "start" else onStart(false) }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-start")) { Text(t("Start a plan", "योजना शुरू करें")) }
            }
            OutlinedButton(onClick = { onPage("card") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-card-entry")) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(8.dp)); Text(t("Pocket emergency card", "जेब में रखने का आपातकालीन कार्ड")) }
        } }
        if (active != null && transport != null && ReviewReminder.due(active, today)) item(key = "review-reminder", contentType = "reminder") { Box(Modifier.animateItem()) { ReviewReminderCard(active, canEdit, transport) { status -> commit { CarePlanBook.reminded(it, active.id, actor, planNow(), status) } } } }
        item(key = "amd-tile", contentType = "tile") { FamilyTile(t("Advance care planning", "अग्रिम देखभाल योजना"), Icons.Default.Gavel, FamilyAccent.Plum, Modifier.animateItem()) {
            Text(t("The person’s own wishes about future care, written down.", "भविष्य की देखभाल के बारे में व्यक्ति की अपनी इच्छाएँ, लिखित रूप में।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
            // How it differs from the doctor's note, in plain words (Dr Sujay, 22 Nov: the three looked like one form).
            Text(if (clinician) t("The patient fills this in, in their own words, and can change it at any time. Your goals-of-care note is different: you write and sign it after the conversation.",
                "मरीज़ इसे अपने शब्दों में भरते हैं और कभी भी बदल सकते हैं। आपका देखभाल के लक्ष्यों का नोट अलग है: उसे आप बातचीत के बाद लिखते और साइन करते हैं।")
                else t("This is yours: the patient fills it in, in their own words, and can change it at any time. The goals-of-care note above is different: your doctor writes and signs it after talking with you.",
                "यह आपका है: मरीज़ इसे अपने शब्दों में भरते हैं और कभी भी बदल सकते हैं। ऊपर वाला देखभाल के लक्ष्यों का नोट अलग है: उसे आपके डॉक्टर आपसे बात करने के बाद लिखते और साइन करते हैं।"),
                fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardInk, modifier = Modifier.testTag("care-plan-amd-difference"))
            Button(onClick = { onPage("amd") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-plan-amd-entry")) { Icon(Icons.Default.Gavel, null); Spacer(Modifier.width(8.dp)); Text(t("Open", "खोलें")) }
        } }
        // Versions as a plain register, always visible: version, status, date.
        if (clinician && versions.isNotEmpty()) item(key = "versions", contentType = "tile") { FamilyTile(t("Plan versions", "योजना के संस्करण"), Icons.Default.History, FamilyAccent.Indigo, Modifier.animateItem().testTag("care-plan-versions")) {
            versions.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = DoctorHairline)
                Text("v${r.version} · ${PlanText.status(r.status).pick(hindi)} · ${r.updatedAt.take(10)}" + (r.revokedReason?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
        } }
        if (clinician && state.audit.events.isNotEmpty()) item(key = "audit", contentType = "disclosure") { Box(Modifier.animateItem()) { InfoDisclosure(t("Audit log", "ऑडिट लॉग")) {
            // The chain check and the 40 lines are worked out once per change to the log, not on every frame the list moves.
            val audit = state.audit
            val intact = remember(audit) { audit.verify() }
            val lines = remember(audit) { audit.events.takeLast(40).reversed().map { e -> "${e.at.take(16).replace('T', ' ')} · ${e.actor} · ${e.action.name} ${e.recordType.name}${e.detail?.let { " · $it" } ?: ""}" } }
            if (!intact) Text(t("Warning: the log has been changed.", "चेतावनी: लॉग बदला गया है।"), style = MaterialTheme.typography.bodySmall, color = RustText)
            lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
        } } }
        item(key = "dpdp", contentType = "dpdp") { Box(Modifier.animateItem()) { DpdpNoticeCard(DpdpScope.CARE_PLAN, state, person.id, store, canManage, commit, actor) } }
    }
    when (dialog) {
        "start" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t("Where is this plan being made?", "यह योजना कहाँ बन रही है?")) },
            confirmButton = { TextButton(onClick = { dialog = ""; onStart(false) }, modifier = Modifier.testTag("care-plan-start-clinic")) { Text(t("Clinic, ward or home", "क्लिनिक, वार्ड या घर")) } },
            dismissButton = { TextButton(onClick = { dialog = ""; onStart(true) }) { Text(t("Emergency department", "आपातकालीन विभाग"), color = RustText) } })
        "review" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t("Why is the plan being reviewed?", "योजना की समीक्षा क्यों हो रही है?")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewTrigger.entries.forEach { tr -> OutlinedButton(onClick = { dialog = ""; onReview(tr) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review-${tr.name.lowercase()}")) { Text(PlanText.trigger(tr).pick(hindi)) } }
            } }, confirmButton = {}, dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Cancel", "रद्द करें")) } })
        "discard" -> draft?.let { d -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t("Discard draft v${d.version}?", "मसौदा v${d.version} हटाएँ?")) },
            confirmButton = { TextButton(onClick = { dialog = ""; onDiscard(d.id) }) { Text(t("Discard", "हटाएँ"), color = RustText) } },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Keep draft", "मसौदा रखें")) } }) }
    }
}

@Composable private fun WithdrawPlanForm(plan: EctprRecord, onCancel: () -> Unit, onWithdraw: (String, String) -> Unit) { val t = careText()
    var reason by rememberSaveable { mutableStateOf("") }; var by by rememberSaveable { mutableStateOf("") }
    Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("care-plan-withdraw-form")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Withdraw plan v${plan.version}?", "योजना v${plan.version} वापस लें?"), fontWeight = FontWeight.SemiBold)
        CareField(reason, { reason = it }, t("Reason", "कारण")); CareField(by, { by = it }, t("Signed by (name)", "हस्ताक्षर करने वाले (नाम)"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Keep plan", "योजना रखें")) }
            Button(onClick = { onWithdraw(reason.trim(), by.trim()) }, enabled = reason.isNotBlank() && by.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("care-plan-withdraw-confirm")) { Text(t("Withdraw", "वापस लें")) }
        }
    } }
}

// ───────────────────────────── Wizard ─────────────────────────────

@Composable private fun EctprWizard(record: EctprRecord, clinician: Boolean, canEdit: Boolean, start: EctprRecord = record, familyReadOnly: Boolean = false, personName: String = "", onSave: (EctprRecord) -> Boolean, onSign: (EctprRecord) -> Unit, onClose: () -> Unit, onGoc: (() -> Unit)? = null) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    var d by remember(record.id) { mutableStateOf(start) }
    var step by rememberSaveable(record.id) { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf<List<RuleIssue>>(emptyList()) }
    var leaving by remember { mutableStateOf(false) }
    // Where "Discard changes" goes when the plan cannot be saved yet: closing, or the Goals of care section.
    var leaveThen by remember { mutableStateOf<(() -> Unit)?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val saved = rememberSaharaHaptic()
    // Each step opens at its top; a refused "Next" brings its reasons into view, since the step can be long.
    LaunchedEffect(step) { listState.scrollToItem(0) }
    LaunchedEffect(shown) { if (shown.isNotEmpty()) listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }
    val steps = listOf(t("Patient and illness", "मरीज़ और बीमारी"), t("Decision-makers", "फ़ैसला लेने वाले"), t("Capacity", "निर्णय-क्षमता"),
        t("Understanding", "समझ"), t("What matters", "क्या ज़रूरी है"), t("Treatment choices", "इलाज के फ़ैसले"), t("Review date", "समीक्षा की तारीख़"), t("Signatures", "हस्ताक्षर"), t("Check and sign", "जाँचें और हस्ताक्षर करें"))
    val blockers = EctprRules.saveBlockers(d, today)
    fun update(change: (EctprRecord) -> EctprRecord) { d = change(d); shown = emptyList() }
    fun choices(change: (PlanChoices) -> PlanChoices) = update { it.copy(choices = change(it.choices)) }
    fun leaveTo(then: () -> Unit) { if (samePlan(d, record)) then() else if (blockers.isEmpty()) { if (onSave(d)) then() } else { leaveThen = then; leaving = true } }
    fun tryLeave() = leaveTo(onClose)
    fun next() {
        val mine = blockers.filter { planStepOf(it.field) <= step }
        if (mine.isNotEmpty()) { shown = mine; return }
        if (blockers.isEmpty() && !samePlan(d, record) && !onSave(d)) return
        step = (step + 1).coerceAtMost(steps.lastIndex); shown = emptyList()
    }
    SaharaBackHandler { tryLeave() }
    // An earlier step stays on this screen, so it uses a plain BackHandler (no "leaving" preview); composed later, it goes first.
    androidx.activity.compose.BackHandler(step > 0) { step--; shown = emptyList() }
    Column(Modifier.fillMaxSize().testTag("ectpr-wizard")) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::tryLeave, modifier = Modifier.size(48.dp).testTag("ectpr-wizard-close")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
            // Which plan, whose, and the step by its name, so a step never shows as a bare number.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("Emergency care plan", "आपातकालीन देखभाल योजना") + (personName.ifBlank { d.patient.name }.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub)
                SaharaAnimatedText(steps[step]) { name -> Text(t("Step ${step + 1} · $name", "चरण ${step + 1} · $name"), fontFamily = SaharaTitleFont, fontSize = 24.sp, lineHeight = 28.sp, color = Ink, modifier = Modifier.a11yHeading().testTag("ectpr-step-name")) }
            }
        }
        LinearProgressIndicator(progress = { (step + 1f) / steps.size }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp).clip(RoundedCornerShape(50)), color = SaharaColors.Primary, trackColor = SaharaColors.Well)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "read-aloud") { PlanReadAloud({ steps[step] + ".\n" + PlanSpeech.wizardStep(d, step, hindi) }, "wizard-${record.id}-$step") }
            when (step) {
                0 -> patientStep(d, ::update)
                1 -> decisionMakerStep(d, ::update)
                2 -> capacityStep(d, ::update)
                3 -> understandingStep(d, ::update)
                4 -> valuesStep(d, ::update, familyReadOnly, personName)
                5 -> treatmentStep(d, today, ::update, ::choices, onGoc?.let { go -> { leaveTo(go) } })
                6 -> reviewStep(d, today, ::update)
                7 -> signatureStep(d, clinician, ::update)
                else -> item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val stop = EctprRules.activationBlockers(d, today); val warn = EctprRules.warnings(d, today)
                    val ready = stop.isEmpty()
                    Surface(color = if (ready) DoctorStatusGoodBg else DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag(if (ready) "ectpr-ready" else "ectpr-not-ready")) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (ready) DoctorStatusGood else DoctorStatusWarn, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                            Text(if (ready) t("Ready to sign", "हस्ताक्षर के लिए तैयार")
                                else t("Still needed:", "अभी चाहिए:"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink, modifier = Modifier.weight(1f).announce())
                        }
                    }
                    IssueList(stop); IssueList(warn)
                    PlanSummaryExtras(d)
                    GocNoteForSigning(d)
                    Button(onClick = { onSign(d) }, enabled = canEdit && stop.isEmpty(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("ectpr-sign")) { Icon(Icons.Default.Verified, null); Spacer(Modifier.width(8.dp)); Text(t("Sign plan", "योजना पर हस्ताक्षर करें")) }
                    OutlinedButton(onClick = { if (blockers.isEmpty() && onSave(d)) { saved(); onClose() } else shown = blockers }, enabled = canEdit, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Save draft", "मसौदा सहेजें")) }
                } }
            }
            // The treatment step already lists its own checks; only show what is not on screen there.
            val refused = if (step == 5) shown.filter { planStepOf(it.field) != 5 } else shown
            if (shown.isNotEmpty()) item(key = "refused-$step", contentType = "issues") { Box(Modifier.animateItem()) { IssueList(refused) } }
        }
        Surface(color = Paper, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (step > 0) { step--; shown = emptyList() } else tryLeave() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    if (step > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                    Text(if (step > 0) t("Back", "पीछे") else t("Close", "बंद करें"))
                }
                if (step < steps.lastIndex) Button(onClick = ::next, enabled = canEdit, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("ectpr-next")) {
                    Text(t("Next", "आगे")); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text(t("Can't save yet", "अभी सहेजा नहीं जा सकता")) },
        text = { Column { blockers.forEach { Text("• " + it.text(hindi), style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { TextButton(onClick = { leaving = false }) { Text(t("Fix them", "ठीक करें")) } },
        dismissButton = { TextButton(onClick = { leaving = false; (leaveThen ?: onClose)() }) { Text(t("Discard changes", "बदलाव छोड़ें"), color = RustText) } })
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
        CareField(d.patient.sex, { v -> p { it.copy(sex = v) } }, t("Sex", "लिंग"), singleLine = true)
        CareField(d.patient.gender, { v -> p { it.copy(gender = v) } }, t("Gender", "जेंडर"), singleLine = true)
        // Both are asked on legal advice (22 Nov); one line says why they differ, without judgement.
        Text(t("Sex is as on medical records. Gender is how the patient describes themselves. Often they are the same.", "लिंग वह है जो मेडिकल रिकॉर्ड में लिखा है। जेंडर वह है जैसे मरीज़ खुद को बताते हैं। अक्सर दोनों एक ही होते हैं।"),
            fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub, modifier = Modifier.testTag("plan-sex-gender-hint"))
        CareField(d.patient.uhid, { v -> p { it.copy(uhid = v) } }, t("Hospital number (UHID)", "अस्पताल नंबर (UHID)"), singleLine = true, voice = false)
        CareField(d.patient.phone, { v -> p { it.copy(phone = v) } }, t("Phone", "फ़ोन"), singleLine = true, voice = false)
        CareField(d.patient.address, { v -> p { it.copy(address = v) } }, t("Address", "पता"))
        CareField(d.clinical.diagnosis, { v -> c { it.copy(diagnosis = v) } }, t("Diagnosis", "बीमारी"))
        CareField(d.clinical.stage, { v -> c { it.copy(stage = v) } }, t("Stage", "अवस्था"), singleLine = true)
        Text(t("Performance status (ECOG)", "रोज़ के काम करने की क्षमता (ECOG)"), fontWeight = FontWeight.Medium)
        ChipGroup(d.clinical.ecog, (0..4).toList(), { Bi("ECOG $it", "ECOG $it") }, "ecog") { v -> c { it.copy(ecog = v) } }
        CareField(d.clinical.allergies, { v -> c { it.copy(allergies = v) } }, t("Allergies", "एलर्जी"), placeholder = t("For example: penicillin", "जैसे: पेनिसिलिन"))
        Text(t("Implanted device", "शरीर में लगा यंत्र"), fontWeight = FontWeight.Medium)
        ChipGroup(d.clinical.implantedDevice, ImplantedDevice.entries, PlanText::device, "device") { v -> c { it.copy(implantedDevice = v) } }
        KeyMedicinesEditor(d.clinical.keyMedicines) { v -> c { it.copy(keyMedicines = v) } }
        PlanDateField(d.clinical.eligibilityDate, t("Eligible for this plan since", "इस योजना के योग्य कब से"), { v -> c { it.copy(eligibilityDate = v) } }, latest = LocalDate.now())
        SectionTitle(t("Care team", "देखभाल टीम"))
        CareField(d.careTeam.oncologist, { v -> team { it.copy(oncologist = v) } }, t("Main treating doctor", "मुख्य इलाज करने वाले डॉक्टर"), singleLine = true)
        CareField(d.careTeam.palliativePhysician, { v -> team { it.copy(palliativePhysician = v) } }, t("Palliative doctor", "पैलिएटिव डॉक्टर"), singleLine = true)
        CareField(d.careTeam.palliativePhone24h, { v -> team { it.copy(palliativePhone24h = v) } }, t("Palliative 24-hour phone", "पैलिएटिव 24 घंटे का फ़ोन"), singleLine = true, voice = false)
        CareField(d.careTeam.localDoctor, { v -> team { it.copy(localDoctor = v) } }, t("Local doctor", "स्थानीय डॉक्टर"), singleLine = true)
    }
}

private fun LazyListScope.decisionMakerStep(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) {
    item { DecisionMakerFields(d.decisionMakers.primary, true) { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(primary = v)) } } }
    item { DecisionMakerFields(d.decisionMakers.alternate, false) { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(alternate = v)) } } }
    item { FamilyFields(d, update) }
}

@Composable private fun FamilyFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    // Those present were already named for the goals-of-care talk; they are not asked for again.
    val talkPresent = d.gocTranscript?.takeIf { it.planVersion == d.version }?.participants.orEmpty().split(',', '\n').map(String::trim).filter(String::isNotBlank)
    LaunchedEffect(d.id) { if (d.decisionMakers.presentAtDiscussion.none(String::isNotBlank) && talkPresent.isNotEmpty()) update { it.copy(decisionMakers = it.decisionMakers.copy(presentAtDiscussion = talkPresent)) } }
    // Optional notes stay one tap away until they hold something.
    var showUnreached by rememberSaveable(d.id) { mutableStateOf(false) }; var showNote by rememberSaveable(d.id) { mutableStateOf(false) }
    PlanCard {
        SectionTitle(t("Family and friends", "परिवार और दोस्त"))
        CareField(d.decisionMakers.presentAtDiscussion.joinToString("\n"), { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(presentAtDiscussion = v.lines())) } }, t("Present, with relation (one per line)", "मौजूद, रिश्ते के साथ (हर लाइन में एक)"), minLines = 2)
        CareField(d.decisionMakers.familyToInform.joinToString("\n"), { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(familyToInform = v.lines())) } }, t("Family to inform (one per line)", "बताने वाले परिवारजन (हर लाइन में एक)"), minLines = 2)
        PlanReveal(showUnreached || d.decisionMakers.attemptedContact.any(String::isNotBlank)) { CareField(d.decisionMakers.attemptedContact.joinToString("\n"), { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(attemptedContact = v.lines())) } }, t("Could not reach (one per line)", "संपर्क नहीं हुआ (हर लाइन में एक)"), minLines = 2) }
        PlanReveal(showNote || d.decisionMakers.familyConsensusNote.isNotBlank()) { CareField(d.decisionMakers.familyConsensusNote, { v -> update { it.copy(decisionMakers = it.decisionMakers.copy(familyConsensusNote = v)) } }, t("Family discussion note", "परिवार से बातचीत का नोट")) }
        val unreachedHidden = !showUnreached && d.decisionMakers.attemptedContact.none(String::isNotBlank); val noteHidden = !showNote && d.decisionMakers.familyConsensusNote.isBlank()
        PlanReveal(unreachedHidden || noteHidden) { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (unreachedHidden) PlanAddButton(t("Could not reach (one per line)", "संपर्क नहीं हुआ (हर लाइन में एक)"), "open-unreached") { showUnreached = true }
            if (noteHidden) PlanAddButton(t("Family discussion note", "परिवार से बातचीत का नोट"), "open-family-note") { showNote = true }
        } }
    }
}

/** "+ label": opens an optional empty slot only when it is wanted. */
@Composable private fun PlanAddButton(label: String, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, color = RustText) }
}

@Composable private fun DecisionMakerFields(value: DecisionMaker?, primary: Boolean, onChange: (DecisionMaker?) -> Unit) { val t = careText()
    val dm = value ?: DecisionMaker()
    fun set(change: (DecisionMaker) -> DecisionMaker) { val next = change(dm); onChange(if (next == DecisionMaker()) null else next) }
    PlanCard {
        SectionTitle(if (primary) t("Primary decision-maker", "मुख्य फ़ैसला लेने वाले") else t("Secondary decision-maker", "द्वितीय फ़ैसला लेने वाले"))
        CareField(dm.name, { v -> set { it.copy(name = v) } }, t("Name", "नाम"), singleLine = true)
        CareField(dm.relation, { v -> set { it.copy(relation = v) } }, t("Relation", "रिश्ता"), singleLine = true)
        CareField(dm.phone24h, { v -> set { it.copy(phone24h = v) } }, t("24-hour phone", "24 घंटे का फ़ोन"), singleLine = true, voice = false)
        Text(t("ID type", "पहचान-पत्र का प्रकार"), fontWeight = FontWeight.Medium)
        ChipGroup(dm.idType, IdType.entries, PlanText::idType, "dm-${if (primary) "primary" else "alternate"}-id") { v -> set { it.copy(idType = v) } }
        CareField(dm.idLast4.orEmpty(), { v -> set { it.copy(idLast4 = PlanIds.last4(v).ifBlank { null }) } }, t("Last 4 digits of the ID", "पहचान-पत्र के आख़िरी 4 अंक"), singleLine = true, voice = false)
        OptionGroup(t("Basis of authority", "अधिकार का आधार"), dm.authorityBasis, AuthorityBasis.entries, PlanText::authority, tag = "dm-basis-${if (primary) "primary" else "alternate"}") { v -> set { it.copy(authorityBasis = v) } }
    }
}

/**
 * Key medicines as a list (Shirin, 21 Nov): each medicine has its own name, dose, and start and end dates, with "+ Add medicine".
 * Stored as one line per medicine in the same text field, so the PDF, the ED card and older plans keep working:
 * "Morphine sulfate · 10 mg every 4 h · from 2026-11-01 · until 2026-12-01".
 */
internal data class KeyMedicine(val name: String = "", val dose: String = "", val from: String = "", val until: String = "")
internal object KeyMedicines {
    fun parse(text: String): List<KeyMedicine> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val parts = line.split(" · ").map { it.trim() }
        KeyMedicine(name = parts.first(), dose = parts.drop(1).firstOrNull { !it.startsWith("from ") && !it.startsWith("until ") }.orEmpty(),
            from = parts.firstOrNull { it.startsWith("from ") }?.removePrefix("from ").orEmpty(), until = parts.firstOrNull { it.startsWith("until ") }?.removePrefix("until ").orEmpty())
    }
    fun encode(list: List<KeyMedicine>): String = list.filter { it.name.isNotBlank() || it.dose.isNotBlank() }.joinToString("\n") { m ->
        listOf(m.name.trim(), m.dose.trim(), m.from.takeIf { it.isNotBlank() }?.let { "from $it" }.orEmpty(), m.until.takeIf { it.isNotBlank() }?.let { "until $it" }.orEmpty())
            .filter { it.isNotBlank() }.joinToString(" · ")
    }
}

@Composable private fun KeyMedicinesEditor(value: String, onChange: (String) -> Unit) { val t = careText()
    var rows by remember { mutableStateOf(KeyMedicines.parse(value).ifEmpty { listOf(KeyMedicine()) }) }
    fun set(next: List<KeyMedicine>) { rows = next; onChange(KeyMedicines.encode(next)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("key-medicines")) {
        Text(t("Key medicines", "मुख्य दवाएँ"), fontWeight = FontWeight.Medium)
        rows.forEachIndexed { i, m ->
            Surface(shape = RoundedCornerShape(14.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("key-medicine-$i")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CareField(m.name, { v -> set(rows.toMutableList().also { it[i] = m.copy(name = v) }) }, t("Medicine ${i + 1}", "दवा ${i + 1}"), singleLine = true)
                    CareField(m.dose, { v -> set(rows.toMutableList().also { it[i] = m.copy(dose = v) }) }, t("Dose and how often", "खुराक और कितनी बार"), singleLine = true)
                    PlanDateField(m.from, t("Started on", "कब शुरू हुई"), { v -> set(rows.toMutableList().also { it[i] = m.copy(from = v) }) })
                    PlanDateField(m.until, t("Planned until (optional)", "कब तक (वैकल्पिक)"), { v -> set(rows.toMutableList().also { it[i] = m.copy(until = v) }) })
                    if (rows.size > 1) TextButton(onClick = { set(rows.toMutableList().also { it.removeAt(i) }) }, modifier = Modifier.heightIn(min = 48.dp).testTag("key-medicine-remove-$i")) {
                        Text(t("Remove this medicine", "यह दवा हटाएँ"), color = RustText)
                    }
                }
            }
        }
        OutlinedButton(onClick = { rows = rows + KeyMedicine() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("key-medicine-add")) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(t("Add medicine", "दवा जोड़ें"))
        }
    }
}

private fun LazyListScope.capacityStep(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { CapacityFields(d, update) } }

@Composable private fun CapacityFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    fun cap(change: (CapacityAssessment) -> CapacityAssessment) = update { it.copy(capacity = change(it.capacity)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // What "capacity" means here, in plain words, before the choice (Shirin, 21 Nov: the step felt vague).
        Surface(shape = RoundedCornerShape(14.dp), color = SaharaTone.Indigo.wash, modifier = Modifier.fillMaxWidth().testTag("capacity-intro")) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("What capacity means here", "यहाँ निर्णय-क्षमता का मतलब"), fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                Text(t("Can the patient, right now, make their own decisions about the treatment in this plan? That means they can understand the information, remember it long enough, weigh it up, and tell you their choice in any way.",
                    "क्या मरीज़ अभी, इस योजना के इलाज के बारे में अपने फ़ैसले खुद ले सकते हैं? यानी वे जानकारी समझ सकें, उसे कुछ देर याद रख सकें, उस पर सोच सकें, और किसी भी तरह अपना फ़ैसला बता सकें।"), fontSize = 14.sp, color = DoctorCardInk)
                Text(t("It is about this decision today, not about age, education or illness in general. If it changes from day to day, choose \"fluctuates\" and check again.",
                    "यह आज के इसी फ़ैसले के बारे में है, उम्र, पढ़ाई या बीमारी के बारे में नहीं। अगर यह रोज़ बदलती है, तो \"बदलती रहती है\" चुनें और फिर से जाँचें।"), fontSize = 14.sp, color = DoctorCardSub)
            }
        }
        CapacityGuideCard()
        OptionGroup(t("Capacity", "निर्णय-क्षमता"), d.capacity.status, Capacity.entries, PlanText::capacity, tag = "capacity") { v -> cap { it.copy(status = v) } }
        val assessed = d.capacity.status == Capacity.FLUCTUATES || d.capacity.status == Capacity.LACKS
        if (assessed) {
            // Kept outside the unfolding part, so it runs only while the choice asks for an assessment (not while the fields fold away).
            val doctor = LocalSignedInDoctor.current
            LaunchedEffect(d.capacity.status) { if (d.capacity.assessor.isBlank() && doctor != null || d.capacity.assessedOn.isBlank()) cap { it.copy(assessor = it.assessor.ifBlank { doctor?.name.orEmpty() }, assessedOn = it.assessedOn.ifBlank { LocalDate.now().toString() }) } }
        }
        PlanReveal(assessed) {
            CareField(d.capacity.assessor, { v -> cap { it.copy(assessor = v) } }, t("Assessed by", "किसने जाँचा"), singleLine = true)
            PlanDateField(d.capacity.assessedOn, t("Assessed on", "जाँच की तारीख़"), { v -> cap { it.copy(assessedOn = v) } }, latest = LocalDate.now())
            CareField(d.capacity.basis, { v -> cap { it.copy(basis = v) } }, t("Basis of the assessment", "जाँच का आधार"), minLines = 2)
        }
        PlanReveal(d.capacity.status == Capacity.FLUCTUATES) { PlanDateField(d.capacity.reassessOn, t("Reassess on", "दोबारा जाँच की तारीख़"), { v -> cap { it.copy(reassessOn = v) } }) }
        val dir = d.existingDirective
        OptionGroup(t("Existing advance care planning or wishes", "पहले की अग्रिम देखभाल योजना या इच्छाएँ"), dir?.kind, DirectiveKind.entries, { when (it) {
            DirectiveKind.VALID_AMD -> Bi("Signed, attested advance care planning", "हस्ताक्षरित, सत्यापित अग्रिम देखभाल योजना"); DirectiveKind.PRIOR_WISHES -> Bi("Wishes said or written before", "पहले कही या लिखी इच्छाएँ"); DirectiveKind.NONE -> Bi("None", "कोई नहीं") } }, tag = "directive") { k ->
            update { it.copy(existingDirective = k?.let { kind -> (it.existingDirective ?: ExistingDirective(kind)).copy(kind = kind) }) }
        }
        if (dir?.kind == DirectiveKind.VALID_AMD) {
            PlanDateField(dir.date, t("Date of the advance care planning", "अग्रिम देखभाल योजना की तारीख़"), { v -> update { it.copy(existingDirective = dir.copy(date = v)) } }, latest = LocalDate.now())
            CareField(dir.location, { v -> update { it.copy(existingDirective = dir.copy(location = v)) } }, t("Where the original is kept", "मूल कहाँ रखी है"), singleLine = true)
            CareField(dir.attestor, { v -> update { it.copy(existingDirective = dir.copy(attestor = v)) } }, t("Attested by (notary or gazetted officer)", "किसने सत्यापित किया (नोटरी या राजपत्रित अधिकारी)"), singleLine = true)
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

@Composable private fun UnderstandingFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText(); val doctor = LocalSignedInDoctor.current
    val today = LocalDate.now().toString()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanCard {
            SectionTitle(t("Understanding the illness", "बीमारी की समझ"))
            CheckLine(d.prognosticUnderstanding.confirmed, { v -> update { it.copy(prognosticUnderstanding = it.prognosticUnderstanding.copy(confirmed = v, confirmedOn = if (v) it.prognosticUnderstanding.confirmedOn.ifBlank { today } else "", confirmedBy = if (v) it.prognosticUnderstanding.confirmedBy.ifBlank { doctor?.name.orEmpty() } else it.prognosticUnderstanding.confirmedBy)) } },
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

private fun LazyListScope.valuesStep(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit, readOnly: Boolean, personName: String) { item { ValuesFields(d, update, readOnly, personName) } }

/** [readOnly]: family members see the patient's own words but cannot change them. */
@Composable private fun ValuesFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit, readOnly: Boolean = false, personName: String = "") { val t = careText()
    fun v(change: (PlanValues) -> PlanValues) = update { it.copy(values = change(it.values)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("Does the patient know about the illness?", "क्या मरीज़ को बीमारी के बारे में पता है?"), d.values.patientKnows, listOf(true, false), { if (it) Bi("Yes", "हाँ") else Bi("No", "नहीं") }, tag = "patient-knows") { x -> v { it.copy(patientKnows = x) } }
        OptionGroup(t("When was this recorded?", "यह कब दर्ज किया गया?"), d.values.knowsRecordedWhen, KnowsRecordedWhen.entries, PlanText::knowsRecordedWhen, tag = "knows-when", noneLabel = NOT_RECORDED) { x -> v { it.copy(knowsRecordedWhen = x) } }
        if (readOnly) {
            val person = object { val name = personName }
            Text(t("What matters most, in the patient's own words", "सबसे ज़रूरी क्या है, मरीज़ के अपने शब्दों में"), fontWeight = FontWeight.SemiBold)
            Text(d.values.ownWords.ifBlank { "—" }, modifier = Modifier.testTag("values-own-words-read-only"))
            Text(t("Outcomes the patient would not accept (one per line)", "जो हालात मरीज़ को मंज़ूर नहीं (हर लाइन में एक)"), fontWeight = FontWeight.SemiBold)
            Text(d.values.unacceptableOutcomes.filter(String::isNotBlank).joinToString("\n").ifBlank { "—" })
            Text(t("Only ${person.name} can change this.", "इसे सिर्फ़ ${person.name} बदल सकते हैं।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub, modifier = Modifier.testTag("values-family-read-only"))
        } else {
            CareField(d.values.ownWords, { x -> v { it.copy(ownWords = x) } }, t("What matters most, in the patient's own words", "सबसे ज़रूरी क्या है, मरीज़ के अपने शब्दों में"), minLines = 3)
            CareField(d.values.unacceptableOutcomes.joinToString("\n"), { x -> v { it.copy(unacceptableOutcomes = x.lines()) } }, t("Outcomes the patient would not accept (one per line)", "जो हालात मरीज़ को मंज़ूर नहीं (हर लाइन में एक)"), minLines = 2)
        }
        CheckLine(d.values.familyAskedNotToTell, { x -> v { it.copy(familyAskedNotToTell = x) } }, t("The family asked that the patient not be told", "परिवार ने कहा है कि मरीज़ को न बताया जाए"))
        PlanReveal(d.values.familyAskedNotToTell) { CareField(d.values.directQuestionPlan, { x -> v { it.copy(directQuestionPlan = x) } }, t("Plan if the patient asks directly", "अगर मरीज़ सीधे पूछें तो क्या करेंगे"), minLines = 2) }
        CheckLine(d.values.financialConcernDiscussed == true, { x -> v { it.copy(financialConcernDiscussed = x) } }, t("Money worries were asked about directly", "पैसों की चिंता के बारे में सीधे पूछा गया"))
        CheckLine(d.values.socialWorkReferral == true, { x -> v { it.copy(socialWorkReferral = x) } }, t("Social-work or scheme referral made", "सोशल वर्क या सरकारी योजना के लिए भेजा गया"))
    }
}

private fun LazyListScope.treatmentStep(d: EctprRecord, today: LocalDate, update: ((EctprRecord) -> EctprRecord) -> Unit, choices: ((PlanChoices) -> PlanChoices) -> Unit, onGoc: (() -> Unit)? = null) {
    val locked = !d.prognosticUnderstanding.confirmed || (d.surrogateDeclaration.answeringAsSurrogate && !d.surrogateDeclaration.confirmed)
    // The talk is recorded only in the Goals of care section (Dr Sujay, 22 Nov); here is where it stands for this version, and the way there.
    item(key = "goc-conversation") { GocPlanStepLink(d, onGoc) }
    if (locked) { item { LockedTreatment(d) }; item { HomeCareFields(d, update) }; return }
    item { TreatmentIntro() }
    item { GoalAndTransfer(d, choices) }
    item { CprFields(d, update, choices) }
    item { BreathingFields(d, today, choices) }
    item { OrganSupportFields(d, today, choices) }
    item { FeedingFields(d, choices) }
    item { ConflictFields(d, today, choices) }
    item { HomeCareFields(d, update) }
}

/** The plan's treatment step: this version's goals-of-care talk, read-only, with its status, and one way to the Goals of care section. */
@Composable private fun GocPlanStepLink(d: EctprRecord, onGoc: (() -> Unit)?) { val t = careText(); val hindi = careHindi()
    val own = d.gocTranscript?.takeIf { it.planVersion == d.version }
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("goc-plan-link")) {
            Icon(if (own?.signOff != null) Icons.Default.Lock else Icons.Default.Forum, null, tint = RustText); Spacer(Modifier.width(10.dp))
            Text(t("Goals of care", "देखभाल के लक्ष्य"), Modifier.weight(1f).a11yHeading(), fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = SaharaTitleFont, color = DoctorCardInk)
            FamilyPill(when { own?.signOff != null -> t("Signed and locked", "साइन होकर बंद"); own != null -> t("Not signed yet", "अभी साइन नहीं"); else -> t("Not recorded", "दर्ज नहीं") },
                when { own?.signOff != null -> FamilyTone.GOOD; own != null -> FamilyTone.WAIT; else -> FamilyTone.NEUTRAL })
        }
        own?.let { g -> Text(GocNote.kindLabel(g.noteKind).pick(hindi), fontSize = 14.sp, color = DoctorCardSub) }
        Text(t("The conversation is recorded and signed in the Goals of care section. The choices it confirmed show in the steps below.", "बातचीत देखभाल के लक्ष्य वाले हिस्से में रिकॉर्ड और साइन होती है। उसमें पक्के हुए फ़ैसले नीचे के चरणों में दिखते हैं।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
        if (onGoc != null) OutlinedButton(onClick = onGoc, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-plan-link-open")) { Text(t("Open Goals of care", "देखभाल के लक्ष्य खोलें"), color = RustText) }
    }
}

/** Helps a doctor called to the home. Not a treatment choice, so the V4 and V5 locks do not apply. */
@Composable private fun HomeCareFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText()
    val home = d.homeCare ?: EctprHomeCare()
    fun set(change: (EctprHomeCare) -> EctprHomeCare) = update { it.copy(homeCare = change(it.homeCare ?: EctprHomeCare())) }
    PlanCard {
        SectionTitle(t("Home care (if relevant)", "घर पर देखभाल (अगर लागू हो)"))
        CareField(home.assessmentFrequency, { v -> set { it.copy(assessmentFrequency = v) } }, t("How often the patient is assessed", "मरीज़ की जाँच कितनी बार होती है"), singleLine = true)
        CareField(home.coordinator, { v -> set { it.copy(coordinator = v) } }, t("Person coordinating between the family and the hospital or centre (name, phone, centre)", "परिवार और अस्पताल या केंद्र के बीच तालमेल रखने वाले (नाम, फ़ोन, केंद्र)"))
        CareField(home.ngoContact, { v -> set { it.copy(ngoContact = v, ngoConsulted = it.ngoConsulted && v.isNotBlank()) } }, t("Non-governmental organisation caring for the patient: representative and contact", "मरीज़ की देखभाल करने वाली ग़ैर-सरकारी संस्था: प्रतिनिधि और संपर्क"))
        PlanReveal(home.ngoContact.isNotBlank()) { CheckLine(home.ngoConsulted, { v -> set { it.copy(ngoConsulted = v) } }, t("The organisation was consulted about this plan", "इस योजना पर संस्था से सलाह ली गई")) }
        OptionGroup(t("Admitted to any hospital before for this condition?", "क्या इस बीमारी के लिए पहले किसी अस्पताल में भर्ती हुए थे?"), home.previousAdmission, listOf(true, false), { if (it) Bi("Yes", "हाँ") else Bi("No", "नहीं") }, tag = "home-admitted", noneLabel = NOT_RECORDED) { x -> set { it.copy(previousAdmission = x) } }
        // Where is written, when is tapped on the calendar (owner, 25 Nov: no typed dates); both stay in the one saved line, "Place · 3 Mar 2025".
        PlanReveal(home.previousAdmission == true) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (where, whenDay) = PlanAdmission.split(home.previousAdmissionDetails)
            CareField(where, { v -> set { it.copy(previousAdmissionDetails = PlanAdmission.join(v, whenDay)) } }, t("Which hospital", "कौन सा अस्पताल"), singleLine = true)
            CareDateButton(whenDay?.let(::careEpoch) ?: 0L, t("When admitted", "कब भर्ती हुए"), { e -> set { it.copy(previousAdmissionDetails = PlanAdmission.join(where, if (e == 0L) null else careDay(e))) } }, allowClear = true, latest = LocalDate.now())
        } }
    }
}

@Composable private fun LockedTreatment(d: EctprRecord) { val t = careText()
    Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("treatment-locked")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Lock, null, tint = DoctorStatusWarn); Spacer(Modifier.width(10.dp))
            Text(if (!d.prognosticUnderstanding.confirmed) t("Confirm Understanding first", "पहले 'समझ' की पुष्टि करें")
                else t("Decision-maker must confirm first", "पहले फ़ैसला लेने वाले पुष्टि करें"))
        }
    }
}

@Composable private fun TreatmentIntro() { val t = careText()
    Text(t("No preference = standard care", "कोई पसंद नहीं = सामान्य इलाज"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
}

@Composable private fun GoalAndTransfer(d: EctprRecord, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText(); val hindi = careHindi()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OptionGroup(t("Goal of care", "देखभाल का लक्ष्य"), c.goalOfCare, GoalOfCare.entries, PlanText::goal, tag = "goal") { v -> choices { it.copy(goalOfCare = v) } }
        OptionGroup(t("Going to hospital", "अस्पताल जाना"), c.hospitalTransfer?.kind, TransferKind.entries, PlanText::transfer, tag = "transfer") { v -> choices { it.copy(hospitalTransfer = v?.let { k -> HospitalTransfer(k, if (k == TransferKind.ONLY_FOR) it.hospitalTransfer?.reasons.orEmpty() else emptyList()) }) } }
        PlanReveal(c.hospitalTransfer?.kind == TransferKind.ONLY_FOR, gap = 0) {
            val reasons = c.hospitalTransfer?.reasons.orEmpty()
            TransferReason.entries.forEach { r -> CheckLine(r in reasons, { on -> choices { val now = it.hospitalTransfer?.reasons.orEmpty(); it.copy(hospitalTransfer = HospitalTransfer(TransferKind.ONLY_FOR, if (on) now + r else now - r)) } }, PlanText.transferReason(r).pick(hindi)) }
        }
        CareField(c.callBeforeAmbulance.joinToString("\n"), { v -> choices { it.copy(callBeforeAmbulance = v.lines()) } }, t("Numbers to call before an ambulance (one per line)", "एम्बुलेंस से पहले किसे फ़ोन करें (हर लाइन में एक)"), minLines = 2)
        OptionGroup(t("If an ambulance comes", "अगर एम्बुलेंस आए"), c.ambulance, AmbulanceOption.entries, PlanText::ambulance, tag = "ambulance") { v -> choices { it.copy(ambulance = v) } }
    }
}

@Composable private fun CprFields(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("CPR if the heart stops", "दिल रुकने पर CPR"), c.cpr, Cpr.entries, PlanText::cpr, tag = "cpr") { v -> choices { it.copy(cpr = v) } }
        if (c.cpr == Cpr.DNACPR) {
            OptionGroup(t("Reason for DNACPR", "DNACPR का कारण"), c.dnacprReason, DnacprReason.entries, PlanText::dnacprReason, tag = "dnacpr-reason") { v -> choices { it.copy(dnacprReason = v) } }
            CareField(c.dnacprDiscussedWith, { v -> choices { it.copy(dnacprDiscussedWith = v) } }, t("Discussed with", "किससे बात हुई"), singleLine = true)
            val by = c.dnacprDecidedBy ?: DnacprDecidedBy()
            // Decided now, by the signed-in doctor, with those present on step 1: filled once, all still editable.
            val doctor = LocalSignedInDoctor.current
            LaunchedEffect(c.dnacprDecidedBy == null) { if (c.dnacprDecidedBy == null) choices { it.copy(
                dnacprDecidedBy = DnacprDecidedBy(doctor?.name.orEmpty(), doctor?.registration.orEmpty(), java.time.LocalDateTime.now().format(DNACPR_TIME_FORMAT)),
                dnacprDiscussedWith = it.dnacprDiscussedWith.ifBlank { d.decisionMakers.presentAtDiscussion.filter(String::isNotBlank).joinToString(", ") }) } }
            LaunchedEffect(doctor) { if (doctor != null && by.name.isBlank() && by.registrationNo.isBlank()) choices { it.copy(dnacprDecidedBy = (it.dnacprDecidedBy ?: DnacprDecidedBy()).copy(name = doctor.name, registrationNo = doctor.registration)) } }
            PrefilledField(by.name, doctor?.name.orEmpty(), t("Decided by (doctor's name)", "किसने तय किया (डॉक्टर का नाम)"), "dnacpr-by-name") { CareField(by.name, { v -> choices { it.copy(dnacprDecidedBy = by.copy(name = v)) } }, t("Decided by (doctor's name)", "किसने तय किया (डॉक्टर का नाम)"), singleLine = true) }
            PrefilledField(by.registrationNo, doctor?.registration.orEmpty(), t("Registration number", "रजिस्ट्रेशन नंबर"), "dnacpr-by-reg") { CareField(by.registrationNo, { v -> choices { it.copy(dnacprDecidedBy = by.copy(registrationNo = v)) } }, t("Registration number", "रजिस्ट्रेशन नंबर"), singleLine = true, voice = false) }
            // Tapped, not typed: a date button and the clock dial; still stored as "2026-10-29 10:30".
            PlanDateTimeField(by.decidedAt, t("Date and time decided", "तय करने की तारीख़ और समय")) { v -> choices { it.copy(dnacprDecidedBy = by.copy(decidedAt = v)) } }
        }
        if (c.cpr == Cpr.ATTEMPT && (c.goalOfCare == GoalOfCare.C_COMFORT || (c.respCeiling != null && c.respCeiling < 5)))
            CareField(c.cprConflictReason, { v -> choices { it.copy(cprConflictReason = v) } }, t("Reason for CPR with this goal or ceiling", "इस लक्ष्य या सीमा के साथ CPR का कारण"), minLines = 2)
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
        PlanNumberField(tr.durationValue.takeIf { it > 0 }, t("Length", "अवधि"), "$tag-duration", range = 1..365) { onChange(tr.copy(durationValue = it ?: 0)) }
        ChipGroup(tr.durationUnit, DurationUnit.entries, PlanText::unit, "$tag-unit") { onChange(tr.copy(durationUnit = it ?: defaultUnit)) }
        PlanDateField(date, t("Review date", "समीक्षा की तारीख़"), { v -> onChange(tr.copy(reviewAt = listOf(v, time).filter(String::isNotBlank).joinToString(" "))) })
        // The clock dial instead of typing "10:00"; reviewAt stays "yyyy-MM-dd HH:mm" (or the date alone).
        CareTimeButton(time.take(5), { v -> onChange(tr.copy(reviewAt = listOf(date.ifBlank { LocalDate.now().toString() }, v.take(5)).filter(String::isNotBlank).joinToString(" "))) }, t("Review time", "समीक्षा का समय"), modifier = Modifier.testTag("$tag-review-time"), allowClear = true)
        CareField(tr.reviewers.joinToString(", "), { v -> onChange(tr.copy(reviewers = v.split(',').map(String::trim))) }, t("Reviewers (names, comma-separated)", "समीक्षा करने वाले (नाम, कॉमा से अलग)"), singleLine = true)
    }
}

@Composable private fun BreathingFields(d: EctprRecord, today: LocalDate, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("Highest breathing support", "साँस की सबसे ऊँची मदद"), c.respCeiling, (0..5).toList(), PlanText::ceiling, tag = "ceiling") { v -> choices { it.copy(respCeiling = v, nivTrial = it.nivTrial.takeIf { _ -> (v ?: 0) >= 4 }) } }
        val level = c.respCeiling
        // Oxygen flow on a wheel, 0.5-15 L/min in half litres, never typed (owner, 25 Nov).
        PlanReveal(level != null && level >= 1) { CareNumberWheelButton(c.maxOxygenLpm, { v -> choices { it.copy(maxOxygenLpm = v) } }, t("Maximum oxygen (L/min)", "अधिकतम ऑक्सीजन (L/min)"), "L/min",
            CarePickerScales.OXYGEN_LPM, 2.0, "max-oxygen", Modifier.fillMaxWidth(), allowClear = true) }
        PlanReveal(level != null && level < 5) { CheckLine(c.ceilingConditions.reversibleCauseException == true, { v -> choices { it.copy(ceilingConditions = it.ceilingConditions.copy(reversibleCauseException = v)) } },
            t("A new, reversible cause (such as choking or a drug reaction) is treated fully, above this ceiling", "नया और ठीक हो सकने वाला कारण (जैसे गले में अटकना या दवा का रिएक्शन) इस सीमा से ऊपर भी पूरा इलाज पाएगा")) }
        PlanReveal(level != null && level >= 1) { PlanNumberField(c.ceilingConditions.spo2Floor, t("Oxygen level to aim for, at least (SpO₂ %)", "कम से कम ऑक्सीजन स्तर (SpO₂ %)"), "spo2-floor", range = 70..100, unit = "%", start = 92) { v -> choices { it.copy(ceilingConditions = it.ceilingConditions.copy(spo2Floor = v)) } } }
        PlanReveal(level != null && level >= 4) {
            PlanNumberField(c.ceilingConditions.nivMaxHours, t("NIV for at most (hours)", "NIV ज़्यादा से ज़्यादा (घंटे)"), "niv-hours", range = 1..168, start = 24) { v -> choices { it.copy(ceilingConditions = it.ceilingConditions.copy(nivMaxHours = v)) } }
            CheckLine(c.nivTrial != null, { on -> choices { it.copy(nivTrial = if (on) TrialPlan(3, DurationUnit.DAYS) else null) } }, t("NIV as a time-limited trial", "NIV तय समय के ट्रायल के रूप में"))
            PlanReveal(c.nivTrial != null) { TrialEditor(t("NIV trial", "NIV ट्रायल"), c.nivTrial, DurationUnit.DAYS, "niv-trial") { v -> choices { it.copy(nivTrial = v) } } }
        }
        if (c.hospitalTransfer?.kind == TransferKind.NO && level != null && level >= 3) CareField(c.ceilingConditions.homeNivReason, { v -> choices { it.copy(ceilingConditions = it.ceilingConditions.copy(homeNivReason = v), conflictReasons = it.conflictReasons + ("C1" to v)) } },
            t("Home NIV or other reason this works without hospital", "घर पर NIV या कोई और कारण कि यह अस्पताल के बिना चलेगा"))
    }
}

@Composable private fun OrganSupportFields(d: EctprRecord, today: LocalDate, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("Intensive care (ICU)", "इंटेंसिव केयर (ICU)"), c.icu?.kind, IcuKind.entries, PlanText::icu, tag = "icu") { v -> choices { it.copy(icu = v?.let { k -> Icu(k, if (k == IcuKind.TRIAL) it.icu?.trial ?: TrialPlan(3, DurationUnit.DAYS) else null) }) } }
        PlanReveal(c.icu?.kind == IcuKind.TRIAL) { TrialEditor(t("ICU trial", "ICU ट्रायल"), c.icu?.trial, DurationUnit.DAYS, "icu-trial") { v -> choices { it.copy(icu = Icu(IcuKind.TRIAL, v)) } } }
        OptionGroup(t("Drugs to support blood pressure", "BP सँभालने की दवाएँ"), c.vasopressors?.kind, VasopressorKind.entries, PlanText::vaso, tag = "vaso") { v -> choices { it.copy(vasopressors = v?.let { k -> Vasopressors(k, if (k == VasopressorKind.TRIAL) it.vasopressors?.trial ?: TrialPlan(durationUnit = DurationUnit.HOURS) else null, if (k == VasopressorKind.YES_MAX_AGENTS) it.vasopressors?.maxAgents ?: 1 else null) }) } }
        PlanReveal(c.vasopressors?.kind == VasopressorKind.TRIAL) { TrialEditor(t("Vasopressor trial", "BP दवा ट्रायल"), c.vasopressors?.trial, DurationUnit.HOURS, "vaso-trial") { v -> choices { it.copy(vasopressors = Vasopressors(VasopressorKind.TRIAL, v)) } } }
        PlanReveal(c.vasopressors?.kind == VasopressorKind.YES_MAX_AGENTS) { PlanNumberField(c.vasopressors?.maxAgents, t("Maximum number of drugs", "दवाओं की अधिकतम संख्या"), "vaso-max", range = 1..5) { v -> choices { it.copy(vasopressors = Vasopressors(VasopressorKind.YES_MAX_AGENTS, maxAgents = v)) } } }
        OptionGroup(t("Dialysis", "डायलिसिस"), c.dialysis?.kind, DialysisKind.entries, PlanText::dialysis, tag = "dialysis") { v -> choices { it.copy(dialysis = v?.let { k -> Dialysis(k, if (k == DialysisKind.TRIAL) it.dialysis?.trial ?: TrialPlan(durationUnit = DurationUnit.SESSIONS) else null) }) } }
        PlanReveal(c.dialysis?.kind == DialysisKind.TRIAL) { TrialEditor(t("Dialysis trial", "डायलिसिस ट्रायल"), c.dialysis?.trial, DurationUnit.SESSIONS, "dialysis-trial") { v -> choices { it.copy(dialysis = Dialysis(DialysisKind.TRIAL, v)) } } }
    }
}

@Composable private fun FeedingFields(d: EctprRecord, choices: ((PlanChoices) -> PlanChoices) -> Unit) { val t = careText()
    val c = d.choices
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(t("Nose tube feeding (NG)", "नाक की नली से खाना (NG)"), c.feeding.ng?.kind, NgKind.entries, PlanText::ng, tag = "ng") { v -> choices { it.copy(feeding = it.feeding.copy(ng = v?.let { k -> NgFeeding(k, if (k == NgKind.UP_TO_DAYS) it.feeding.ng?.days ?: 7 else null) })) } }
        PlanReveal(c.feeding.ng?.kind == NgKind.UP_TO_DAYS) { PlanNumberField(c.feeding.ng?.days, t("Days before review", "समीक्षा से पहले दिन"), "ng-days", range = 1..90, start = 7) { v -> choices { it.copy(feeding = it.feeding.copy(ng = NgFeeding(NgKind.UP_TO_DAYS, v))) } } }
        OptionGroup(t("Stomach tube (PEG)", "पेट की नली (PEG)"), c.feeding.peg, PegFeeding.entries, PlanText::peg, tag = "peg") { v -> choices { it.copy(feeding = it.feeding.copy(peg = v)) } }
        CheckLine(c.feeding.removeExistingTube, { v -> choices { it.copy(feeding = it.feeding.copy(removeExistingTube = v)) } }, t("The patient or family wish an existing feeding tube to be removed", "मरीज़ या परिवार चाहते हैं कि लगी हुई खाने की नली हटाई जाए"))
        OptionGroup(t("Fluids", "तरल (पानी)"), c.hydration, Hydration.entries, PlanText::hydration, tag = "hydration") { v -> choices { it.copy(hydration = v) } }
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
        if (issues.any { it.id == "V3" } || c.goalAConfirmed) CheckLine(c.goalAConfirmed, { v -> choices { it.copy(goalAConfirmed = v) } }, t("Confirmed: this is what was decided", "पुष्टि: यही तय हुआ था"))
    }
}

private fun LazyListScope.reviewStep(d: EctprRecord, today: LocalDate, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { ReviewFields(d, today, update) } }

@Composable private fun ReviewFields(d: EctprRecord, today: LocalDate, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText(); val hindi = careHindi()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanDateField(d.reviewDue, t("Review due", "समीक्षा की तारीख़"), { v -> update { it.copy(reviewDue = v) } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4L, 8L, ReviewRules.ROUTINE_REVIEW_WEEKS).distinct().forEach { w -> AssistChip(onClick = { update { it.copy(reviewDue = today.plusWeeks(w).toString()) } }, label = { Text(t("In $w weeks", "$w हफ़्ते में")) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        SectionTitle(t("Review sooner if", "पहले समीक्षा करें अगर"))
        ReviewTrigger.entries.filter { it != ReviewTrigger.ROUTINE_INTERVAL }.forEach { Text("• " + PlanText.trigger(it).pick(hindi), style = MaterialTheme.typography.bodyMedium) }    }
}

private fun LazyListScope.signatureStep(d: EctprRecord, clinician: Boolean, update: ((EctprRecord) -> EctprRecord) -> Unit) { item { SignatureFields(d, clinician, update) } }

@Composable private fun SignatureFields(d: EctprRecord, clinician: Boolean, update: ((EctprRecord) -> EctprRecord) -> Unit) { val t = careText(); val hindi = careHindi()
    // The signed-in doctor's approved name and registration number are filled in; the signer's name comes from step 0 or step 1.
    val doctor = LocalSignedInDoctor.current
    var consultant by rememberSaveable { mutableStateOf(doctor?.name.orEmpty()) }; var reg by rememberSaveable { mutableStateOf(doctor?.registration.orEmpty()) }
    LaunchedEffect(doctor) { if (doctor != null && d.signatures.none { it.role == SignatureRole.TREATING_CONSULTANT }) { if (consultant.isBlank()) consultant = doctor.name; if (reg.isBlank()) reg = doctor.registration } }
    var coOpen by rememberSaveable { mutableStateOf(false) }; var moreWitness by rememberSaveable { mutableStateOf(false) }
    var coName by rememberSaveable { mutableStateOf("") }; var coReg by rememberSaveable { mutableStateOf("") }; var coRole by rememberSaveable { mutableStateOf(SignatureRole.PALLIATIVE_CLINICIAN.name) }
    fun knownSigner(role: String) = when (role) { SurrogateRank.PRIMARY.name -> d.decisionMakers.primary?.name.orEmpty(); SurrogateRank.SECONDARY.name -> d.decisionMakers.alternate?.name.orEmpty(); else -> d.patient.name }.trim()
    var signerRole by rememberSaveable { mutableStateOf(if (d.surrogateDeclaration.answeringAsSurrogate) SurrogateRank.PRIMARY.name else SignatureRole.PATIENT.name) }
    var signer by rememberSaveable { mutableStateOf(knownSigner(signerRole)) }; var thumb by rememberSaveable { mutableStateOf(false) }
    var witness by rememberSaveable { mutableStateOf("") }
    // Required before the patient or decision-maker signs: how the plan was read to them (ConsentReadAloud.kt).
    var reading by remember { mutableStateOf<ConsentReading?>(null) }; var readingRound by rememberSaveable { mutableIntStateOf(0) }
    fun add(s: Signature) = update { r -> r.copy(signatures = (if (s.role == SignatureRole.TREATING_CONSULTANT) r.signatures.filter { it.role != SignatureRole.TREATING_CONSULTANT } else r.signatures) + s) }
    fun roleLabel(r: SignatureRole) = when (r) {
        SignatureRole.TREATING_CONSULTANT -> Bi("Treating consultant", "इलाज करने वाले कंसल्टेंट"); SignatureRole.PALLIATIVE_CLINICIAN -> Bi("Palliative clinician", "पैलिएटिव डॉक्टर"); SignatureRole.NURSE -> Bi("Nurse", "नर्स")
        SignatureRole.PATIENT -> Bi("Patient", "मरीज़"); SignatureRole.SURROGATE -> Bi("Decision-maker", "फ़ैसला लेने वाले"); SignatureRole.WITNESS -> Bi("Witness", "गवाह")
    }
    fun rankLabel(rank: SurrogateRank) = when (rank) { SurrogateRank.PRIMARY -> Bi("Primary decision-maker", "मुख्य फ़ैसला लेने वाले"); SurrogateRank.SECONDARY -> Bi("Secondary decision-maker", "द्वितीय फ़ैसला लेने वाले") }
    fun signerLabel(value: String) = SurrogateRank.entries.firstOrNull { it.name == value }?.let(::rankLabel) ?: roleLabel(SignatureRole.PATIENT)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d.signatures.isNotEmpty()) PlanCard {
            SectionTitle(t("Signed so far", "अब तक हस्ताक्षर"))
            d.signatures.forEachIndexed { i, s -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${(s.surrogateRank?.let(::rankLabel) ?: roleLabel(s.role)).pick(hindi)}: ${s.name}${s.registrationNo?.let { " · $it" } ?: ""}${if (s.thumbImpression == true) t(" · thumb impression", " · अँगूठे का निशान") else ""} · ${s.signedAt.take(16).replace('T', ' ')}${s.readAloud?.let { " · " + ConsentReadingRules.summary(it, t) }.orEmpty()}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { update { r -> r.copy(signatures = r.signatures.filterIndexed { j, _ -> j != i }) } }) { Icon(Icons.Default.Close, t("Remove signature", "हस्ताक्षर हटाएँ")) }
            } }
        }
        PlanCard {
            SectionTitle(t("Treating consultant", "इलाज करने वाले कंसल्टेंट"))
            PrefilledField(consultant, doctor?.name.orEmpty(), t("Consultant's name", "कंसल्टेंट का नाम"), "sign-consultant-name") { CareField(consultant, { consultant = it }, t("Consultant's name", "कंसल्टेंट का नाम"), singleLine = true) }
            PrefilledField(reg, doctor?.registration.orEmpty(), t("Medical registration number", "मेडिकल रजिस्ट्रेशन नंबर"), "sign-consultant-reg") { CareField(reg, { reg = it }, t("Medical registration number", "मेडिकल रजिस्ट्रेशन नंबर"), singleLine = true, voice = false) }
            Button(onClick = { add(Signature(SignatureRole.TREATING_CONSULTANT, consultant.trim(), reg.trim(), planNow())); consultant = ""; reg = "" }, enabled = consultant.isNotBlank() && reg.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign-consultant")) { Text(t("Sign", "हस्ताक्षर करें")) }
        }
        PlanCard {
            SectionTitle(t("Co-signature (optional)", "सह-हस्ताक्षर (वैकल्पिक)"))
            // Optional: no empty boxes until asked for.
            SaharaStateSwitch(coOpen, Modifier.fillMaxWidth(), label = "cosign-open") { open ->
            if (!open) OutlinedButton(onClick = { coOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open-cosign")) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Add co-signature", "सह-हस्ताक्षर जोड़ें")) }
            else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChipGroup(SignatureRole.valueOf(coRole), listOf(SignatureRole.PALLIATIVE_CLINICIAN, SignatureRole.NURSE), ::roleLabel, "cosign-role") { coRole = (it ?: SignatureRole.PALLIATIVE_CLINICIAN).name }
            CareField(coName, { coName = it }, t("Name", "नाम"), singleLine = true); CareField(coReg, { coReg = it }, t("Registration number", "रजिस्ट्रेशन नंबर"), singleLine = true, voice = false)
            OutlinedButton(onClick = { add(Signature(SignatureRole.valueOf(coRole), coName.trim(), coReg.trim().ifBlank { null }, planNow())); coName = ""; coReg = ""; coOpen = false }, enabled = coName.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Add co-signature", "सह-हस्ताक्षर जोड़ें")) }
            } }
        }
        PlanCard {
            SectionTitle(t("Patient or decision-maker", "मरीज़ या फ़ैसला लेने वाले"))
            ChipGroup(signerRole, listOf(SignatureRole.PATIENT.name, SurrogateRank.PRIMARY.name, SurrogateRank.SECONDARY.name), ::signerLabel, "signer-role") { val next = it ?: SignatureRole.PATIENT.name; if (signer.isBlank() || signer.trim() == knownSigner(signerRole)) signer = knownSigner(next); signerRole = next }
            PrefilledField(signer, knownSigner(signerRole), t("Name", "नाम"), "signer-name-$signerRole") { CareField(signer, { signer = it }, t("Name", "नाम"), singleLine = true) }
            val readTo = remember(d.decisionMakers, d.witnesses) { (listOfNotNull(d.decisionMakers.primary, d.decisionMakers.alternate).map { ConsentPerson(it.name, it.relation) } + d.witnesses.map { ConsentPerson(it.name, "") }) }
            val planText = remember(d, hindi) { PlanSpeech.summary(d, null, hindi) }
            ConsentReadAloudStep(planText, "plan-sign-${d.id}-${d.version}-$readingRound", signer, readTo, onChange = { reading = it })
            CheckLine(thumb, { thumb = it }, t("Signed with a thumb impression", "अँगूठे के निशान से हस्ताक्षर"))
            OutlinedButton(onClick = {
                val heard = reading ?: return@OutlinedButton
                val rank = SurrogateRank.entries.firstOrNull { it.name == signerRole }
                // Read to them: the plan's rule for limited literacy applies, so two witnesses are asked for.
                val signed = Signature(if (rank == null) SignatureRole.PATIENT else SignatureRole.SURROGATE, signer.trim(), null, planNow(), thumb.takeIf { it }, rank, heard)
                update { r -> r.copy(limitedLiteracy = r.limitedLiteracy || !ConsentReadingRules.canRead(heard), signatures = r.signatures + signed) }; signer = ""; thumb = false; reading = null; readingRound++
            }, enabled = signer.isNotBlank() && reading != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-signer-signature")) { Text(t("Add signature", "हस्ताक्षर जोड़ें")) }
            CheckLine(d.limitedLiteracy, { v -> update { it.copy(limitedLiteracy = v) } }, t("Limited reading or writing", "पढ़ना-लिखना कम आता है"))
        }
        if (d.limitedLiteracy || d.signatures.any { it.thumbImpression == true } || d.witnesses.isNotEmpty()) PlanCard {
            SectionTitle(t("Witnesses (two needed)", "गवाह (दो चाहिए)"))
            // Two are needed; a blank box for a third only when asked for.
            if (d.witnesses.size < 2 || moreWitness) {
                CareField(witness, { witness = it }, t("Witness name", "गवाह का नाम"), singleLine = true)
                OutlinedButton(onClick = { add(Signature(SignatureRole.WITNESS, witness.trim(), null, planNow())); witness = ""; moreWitness = false }, enabled = witness.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-witness")) { Text(t("Add witness", "गवाह जोड़ें")) }
            } else OutlinedButton(onClick = { moreWitness = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open-witness")) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Add witness", "गवाह जोड़ें")) }
        }
    }
}

/** What was added by the legal review, on the check-and-sign step, so it is seen before signing. */
@Composable private fun PlanSummaryExtras(d: EctprRecord) { val t = careText(); val hindi = careHindi()
    val lines = listOfNotNull(d.decisionMakers.alternate?.takeUnless { it.blank }?.let { Bi.labeled(Bi("Secondary decision-maker", "द्वितीय फ़ैसला लेने वाले"), Bi.plain(listOf(it.name, it.relation, it.phone24h).filter(String::isNotBlank).joinToString(" · "))) }) +
        PlanText.fullRecordLines(d) + PlanText.homeCareLines(d)
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
    LazyColumn(Modifier.fillMaxSize().testTag("ed-quick-view"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PlanHeader(t("For the emergency room", "आपातकालीन कक्ष के लिए"), onBack, "ed-header") }
        item { Text(t("A one-page summary hospital staff can read fast", "एक पन्ने का सारांश, जिसे अस्पताल के लोग जल्दी पढ़ सकें"), fontSize = 15.sp, lineHeight = 20.sp, color = DoctorCardSub) }
        item { PlanReadAloud({ PlanSpeech.edView(interp, amdLine, hindi) }, "ed-$patientId") }
        if (r != null) item { Text(listOfNotNull(r.patient.name.ifBlank { null }, r.patient.uhid.takeIf { it.isNotBlank() }?.let { "UHID $it" }, PlanDates.age(r.patient.dob, today)?.let { t("$it years", "$it साल") }).joinToString(" · "), fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
        item { Surface(color = if (interp.kind == EdKind.ACTIVE) DoctorStatusGoodBg else DoctorStatusRestBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, if (interp.kind == EdKind.ACTIVE) DoctorStatusGood else DoctorStatusRest), modifier = Modifier.fillMaxWidth().testTag("ed-banner-${interp.kind.name.lowercase()}")) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (interp.kind == EdKind.ACTIVE) Icons.Default.Verified else Icons.Default.Warning, null, tint = if (interp.kind == EdKind.ACTIVE) DoctorStatusGood else DoctorStatusRest, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp))
                Text(interp.banner.pick(hindi), Modifier.weight(1f).announce(), fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp, color = if (interp.kind == EdKind.ACTIVE) DoctorStatusGood else DoctorStatusRest)
            }
        } }
        if (interp.flags.isNotEmpty()) item(key = "ed-flags", contentType = "banner") { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.animateItem().fillMaxWidth().testTag("ed-flags")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { interp.flags.forEach { Text(it.pick(hindi), fontWeight = FontWeight.SemiBold, color = RustText) } }
        } }
        // An active plan leads with its decisions (tech spec §6), so the always-on box is the short form there; every condition stays on screen.
        item { IfInDoubtBox(compact = interp.kind == EdKind.ACTIVE) }
        if (r != null && interp.showContent) {
            val rows = EdView.rows(r, amdLine, CarePlanBook.openReview(state, r.id), AmdLifecycle.governs(state.amds, patientId, r.existingDirective))
            items(rows, key = { it.key }) { row -> EdRowView(row) }
            val fullRecord = PlanText.fullRecordLines(r)
            if (fullRecord.isNotEmpty()) item { InfoDisclosure(t("Full record", "पूरा रिकॉर्ड")) { fullRecord.forEach { Text(it.pick(hindi), style = MaterialTheme.typography.bodyMedium) } } }
            if (r.choices.feeding.removeExistingTube) item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp)) { Text(t("Wish to remove feeding tube: needs the medical board process", "खाने की नली हटाने की इच्छा: मेडिकल बोर्ड की प्रक्रिया चाहिए"), Modifier.padding(12.dp)) } }
        } else {
            item { Text(t("Give full standard emergency care.", "पूरा सामान्य आपातकालीन इलाज दें।"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            // A living will stands on its own: it is shown whatever state the plan is in.
            item { EdRowView(EdRow("AMD", Bi("Advance care planning", "अग्रिम देखभाल योजना"), amdLine, flag = AmdLifecycle.governs(state.amds, patientId, r?.existingDirective))) }
        }
        item { CareContinuesBox() }
        if (r != null) item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCard, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(6.dp)); Text(t("Pocket emergency card", "जेब में रखने का आपातकालीन कार्ड")) }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text(t("Share PDF", "PDF साझा करें")) }
        } }
    }
}

@Composable private fun EdRowView(row: EdRow) { val hindi = careHindi()
    Row(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(12.dp)).border(if (row.flag) 2.dp else 1.dp, if (row.flag) Rust else DoctorHairline, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp).testTag("ed-row-${row.key.lowercase()}"), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(row.label.pick(hindi), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
            Text(row.value.pick(hindi), fontWeight = if (row.value == NO_PREFERENCE || row.value == NOT_RECORDED) FontWeight.Normal else FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = if (row.flag) RustText else Ink)
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
    Surface(color = if (issued) DoctorStatusGoodBg else DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag(if (issued) "card-issued" else "card-local-only")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (issued) t("Card ready", "कार्ड तैयार") else t("Not uploaded yet", "अभी अपलोड नहीं हुआ"), fontWeight = FontWeight.Bold)
        if (!issued) {
            card.serverMessage?.let { Text(if (it == EdCardApi.NETWORK) t("No internet right now.", "अभी इंटरनेट नहीं है।") else it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("card-server-message")) }
            if (online) TextButton(onClick = onRetry, modifier = Modifier.testTag("card-retry")) { Text(t("Try again", "फिर कोशिश करें")) }
        }
    } }
}

@Composable private fun WalletCardPage(state: CarePlanState, patientId: String, online: Boolean, canEdit: Boolean, emergencyUrl: String? = null, onRetry: () -> Unit, onReissue: () -> Unit, onBack: () -> Unit, onShare: () -> Unit, onViewed: (String) -> Unit) {
    val t = careText(); val hindi = careHindi(); val today = LocalDate.now()
    val active = EctprLifecycle.active(state.ectprs, patientId)
    val token = active?.let { CardTokens.live(state.tokens, it.id) }
    val amdNow = AmdLifecycle.statusSummary(state.amds, patientId)
    val withdrawnPending = remember(state.tokens, patientId) { state.tokens.count { it.patientId == patientId && !it.live && it.pending } }
    LaunchedEffect(active?.id) { active?.let { onViewed(it.id) } }
    LazyColumn(Modifier.fillMaxSize().testTag("wallet-card"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PlanHeader(t("Pocket emergency card", "जेब में रखने का आपातकालीन कार्ड"), onBack, "wallet-header") }
        if (withdrawnPending > 0) item(key = "revoke-pending", contentType = "banner") { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.animateItem().fillMaxWidth().testTag("card-revoke-pending")) { Text(t("Old QR may still open until the phone is online.", "फ़ोन ऑनलाइन होने तक पुराना QR खुल सकता है।"), Modifier.padding(12.dp)) } }
        if (active == null || token == null) item { Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.QrCode, null, Modifier.size(40.dp), tint = DoctorCardSub)
            Text(t("No signed plan yet", "अभी कोई हस्ताक्षरित योजना नहीं"), fontWeight = FontWeight.SemiBold, color = Ink)
        } }
        else {
            item(key = "card-status", contentType = "status") { Box(Modifier.animateItem()) { CardStatus(token, online, onRetry) } }
            // Past the review date the ED view says standard care applies; the card says the same instead of showing the plan as current.
            EdView.interpret(active, today).takeIf { it.kind == EdKind.OUT_OF_DATE }?.let { stale -> item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("card-out-of-date")) { Text(stale.banner.pick(hindi), Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = Ink) } } }
            item { Surface(color = Paper, shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, Ink), modifier = Modifier.fillMaxWidth()) {
                // Read at a glance by ambulance or ED staff: dark on white, large values, one divider per line.
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("SAHARA EMERGENCY CARE PLAN", "सहारा आपातकालीन देखभाल योजना"), fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp, color = Ink, modifier = Modifier.a11yHeading())
                    Surface(color = RustText, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text(WalletCard.TREAT_LINE.pick(hindi), Modifier.padding(12.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp) }
                    WalletCard.lines(active).forEach { (label, value) -> Column(Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = DoctorHairline); Spacer(Modifier.height(8.dp))
                        Text(label.pick(hindi), fontSize = 14.sp, color = DoctorCardSub)
                        Text(value.pick(hindi), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    } }
                    HorizontalDivider(color = DoctorHairline)
                    Text(t("Online QR", "ऑनलाइन QR"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    PlanQr(token.url, t("QR code with a link to this plan", "इस योजना के लिंक वाला QR कोड"), Modifier.fillMaxWidth(.8f).widthIn(max = 320.dp).align(Alignment.CenterHorizontally).border(2.dp, Ink).testTag("wallet-qr"))
                    // No offline text QR (meeting, 24 Nov): text in a QR opens in a notes app where it can be edited. The emergency card's QR is
                    // its read-only web link (sahara.wedevit.in/emergency#t=…), the same code as on the patient's card; with no link, say where to scan.
                    if (emergencyUrl != null) {
                        Text(t("Emergency card QR", "आपातकालीन कार्ड QR"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        PlanQr(emergencyUrl, t("Emergency web link QR code", "आपातकालीन वेब लिंक QR कोड"), Modifier.fillMaxWidth(.8f).widthIn(max = 320.dp).align(Alignment.CenterHorizontally).border(2.dp, Ink).testTag("wallet-emergency-qr"))
                    } else Text(t("Scan the QR on the emergency card", "आपातकालीन कार्ड पर दिया QR स्कैन करें"), fontSize = 15.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("wallet-no-emergency-qr"))
                    run { val v = active.version; val due = active.reviewDue; Text(t("Plan v$v · review due $due", "योजना v$v · समीक्षा $due"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                }
            } }
            val issuedAmd = runCatching { org.json.JSONObject(token.snapshot).optJSONObject("amdAtIssue") }.getOrNull()
            if (issuedAmd?.optString("status").orEmpty() != amdNow?.status?.name.orEmpty() || (amdNow != null && issuedAmd?.optInt("version") != amdNow.version)) item(key = "amd-changed", contentType = "banner") { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.animateItem().fillMaxWidth().testTag("card-amd-changed")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Advance care planning changed. Issue a new card.", "अग्रिम देखभाल योजना बदली है। नया कार्ड जारी करें।"), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onReissue, enabled = canEdit, modifier = Modifier.heightIn(min = 48.dp).testTag("card-reissue")) { Text(t("New card", "नया कार्ड")) }
            } } }
            item { Button(onClick = onShare, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text(t("Print or share", "प्रिंट या साझा करें")) } }
        }
    }
}

/** Entry card on Today's SOS sheet (patient and family). Since 24 Nov 2026 the patient side of the plan is Goals of care only, so it says so. */
@Composable fun CarePlanEntryCard(onOpen: () -> Unit) { val t = careText()
    ActionCard(SaharaSoftBlue, Modifier.fillMaxWidth().testTag("care-plan-entry"), onClick = onOpen) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Forum, null, tint = Rust); Spacer(Modifier.width(12.dp))
            Text(t("Goals of care", "देखभाल के लक्ष्य"), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp))
        }
    }
}


/** Date and time stamp used when the doctor records the DNACPR decision. */
private val DNACPR_TIME_FORMAT: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy" + "-MM-dd HH:mm")
