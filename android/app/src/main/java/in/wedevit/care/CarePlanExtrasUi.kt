@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.Intent
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate

private val Small @Composable get() = MaterialTheme.typography.bodySmall
private fun livingWillPdfName(version: Int) = "sahara-living-will-v$version.pdf"

/** Read-aloud on the plan screens: the phone's own voice in the chosen language. Nothing leaves the phone. */
@Composable internal fun PlanReadAloud(text: () -> String, identity: String) { val t = careText()
    val context = LocalContext.current; val lang = careLanguage()
    var revision by remember { mutableIntStateOf(0) }
    val reader = remember(identity) { CareReadAloud(context) { revision++ } }
    DisposableEffect(reader) { onDispose { reader.close() } }
    @Suppress("UNUSED_VARIABLE") val changed = revision
    var pressed by remember(identity) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { pressed = true; if (reader.speaking) reader.stop() else reader.read(text(), lang.code + "-IN") }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp).testTag("plan-read-aloud")) {
            Icon(if (reader.speaking) Icons.Default.Stop else Icons.Default.VolumeUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(if (reader.speaking) t("Stop reading", "पढ़ना रोकें") else t("Read aloud", "पढ़कर सुनाएँ"))
        }
        if (pressed && reader.status.isNotBlank()) Text(reader.status, style = Small, color = DoctorCardSub)
    }
}

/** L8: information only. The app has no "withdraw" action. */
@Composable internal fun BoardPathway(title: String) { val hindi = careHindi()
    InfoDisclosure(title) { AmdText.BOARD_PATHWAY.forEach { Text(it.pick(hindi), style = MaterialTheme.typography.bodyMedium) } }
}

@Composable internal fun LegalPendingBox() { val t = careText(); val hindi = careHindi()
    Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("legal-pending")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(AmdText.LEGAL_PENDING_TITLE.pick(hindi), fontWeight = FontWeight.Bold)
        Text(AmdText.LEGAL_PENDING.pick(hindi), style = MaterialTheme.typography.bodyMedium)
        BoardPathway(t("Board process", "बोर्ड प्रक्रिया"))
    } }
}

/** Notice at the start of the plan and the living will, with a working download and delete. Family members see the notice only. */
@Composable internal fun DpdpNoticeCard(scope: DpdpScope, state: CarePlanState, patientId: String, store: CarePlanStore, canManage: Boolean, commit: ((CarePlanState) -> CarePlanState) -> Boolean, actor: String) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current; val coroutine = rememberCoroutineScope()
    // Always shown (no Show/Hide): the notice is short and people should not have to look for it. Seeing the screen counts as seeing it.
    LaunchedEffect(scope, patientId) { runCatching { store.markNoticeSeen(scope) } }
    var confirming by rememberSaveable { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    val blocks = Dpdp.notice(scope)
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Your data", "आपका डेटा"), Modifier.weight(1f).testTag("dpdp-title"), fontWeight = FontWeight.SemiBold)
        }
        run {
            blocks.forEach { b -> Text(b.title.pick(hindi), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium); Text(b.body.pick(hindi), style = Small) }
            if (canManage) {
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dpdp-download"), onClick = {
                    busy = true; message = ""
                    coroutine.launch {
                        val file = runCatching { withContext(Dispatchers.IO) {
                            val folder = store.folder(patientId)
                            val scans = Dpdp.scanKeys(state, patientId).mapNotNull { key -> File(folder, key).takeIf { it.isFile }?.let { f -> key to JSONObject().put("name", f.name).put("dataUrl", "data:application/octet-stream;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)) } }.toMap()
                            File(folder, Dpdp.fileName(patientId, LocalDate.now())).also { it.writeText(Dpdp.export(state, patientId, Instant.now(), scans).toString(2)) }
                        } }.getOrNull()
                        busy = false
                        if (file == null) message = t("Download failed. Try again.", "डाउनलोड नहीं हुआ। फिर कोशिश करें।")
                        else {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), t("Download my data", "मेरा डेटा डाउनलोड करें")))
                            commit { CarePlanBook.exported(it, scope, patientId, actor, planNow()) }
                            message = t("Ready. Keep the file safe.", "तैयार। फ़ाइल सँभालकर रखें।")
                        }
                    }
                }) { Text(t("Download my data", "मेरा डेटा डाउनलोड करें")) }
                if (!confirming) TextButton(onClick = { confirming = true }, enabled = !busy, modifier = Modifier.testTag("dpdp-delete")) { Text(t("Delete draft", "ड्राफ़्ट मिटाएँ"), color = RustText) }
                else Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Delete the unsigned draft? This cannot be undone.", "बिना साइन का ड्राफ़्ट मिटाएँ? इसे वापस नहीं लाया जा सकता।"), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { confirming = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Keep", "रखें")) }
                        Button(modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dpdp-delete-confirm"), onClick = {
                            var deleted = 0
                            commit { s -> CarePlanBook.deleteDrafts(s, scope, patientId, actor, planNow()).also { deleted = it.second }.first }
                            confirming = false
                            message = if (deleted > 0) t("Draft deleted.", "ड्राफ़्ट मिट गया।")
                                else t("No draft to delete.", "मिटाने के लिए कोई ड्राफ़्ट नहीं।")
                        }) { Text(t("Delete", "मिटाएँ")) }
                    }
                } }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dpdp-message"))
        }
    }
}

/** Offered once a signed plan's review is due within 14 days. Says plainly when the server has no SMS set up. */
@Composable internal fun ReviewReminderCard(r: EctprRecord, canEdit: Boolean, transport: EdCardTransport, onSent: (String) -> Unit) {
    val t = careText(); val coroutine = rememberCoroutineScope(); val today = LocalDate.now()
    var configured by remember { mutableStateOf<Boolean?>(null) }; var checked by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var result by remember { mutableStateOf("") }
    LaunchedEffect(r.id) { configured = ReviewReminder.configured(transport); checked = true }
    val recipient = ReviewReminder.recipient(r); val days = ReviewReminder.daysUntilReview(r, today) ?: 0
    PlanCard {
        Text(when { days < 0 -> { val late = -days; t("Review is $late days late", "समीक्षा $late दिन देर से है") }; days == 0L -> t("Review is due today", "समीक्षा आज है"); else -> t("Review is due in $days days", "समीक्षा $days दिन में है") }, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("review-reminder"))
        if (checked && configured == false) Text(t("SMS is off. Please phone them.", "SMS बंद है। कृपया फ़ोन करें।"), style = Small, color = RustText, modifier = Modifier.testTag("review-sms-not-configured"))
        if (checked && configured == null) Text(t("No connection. Please phone them.", "कनेक्शन नहीं है। कृपया फ़ोन करें।"), style = Small, color = RustText)
        if (recipient == null) Text(t("No mobile number.", "कोई मोबाइल नंबर नहीं।"), style = Small)
        else { val name = recipient.first; Text(t("To $name", "$name को"), style = Small) }
        OutlinedButton(enabled = canEdit && !busy && recipient != null && configured == true, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review-sms-send"), onClick = {
            busy = true; result = ""
            coroutine.launch { val status = ReviewReminder.send(r, recipient!!.second, transport); busy = false; result = status; onSent(status) }
        }) { Text(if (busy) t("Sending…", "भेज रहे हैं…") else t("Send reminder", "रिमाइंडर भेजें")) }
        if (result.isNotBlank()) Text(when (result) {
            "sent" -> t("Reminder sent.", "संदेश भेज दिया गया।")
            "not-configured" -> t("SMS is off. Please phone them.", "SMS बंद है। कृपया फ़ोन करें।")
            "too-many" -> t("Too many reminders. Try later.", "बहुत सारे रिमाइंडर। बाद में कोशिश करें।")
            "no-valid-number" -> t("No mobile number.", "कोई मोबाइल नंबर नहीं।")
            else -> t("Not sent. Please phone them.", "नहीं भेजा गया। कृपया फ़ोन करें।")
        }, style = MaterialTheme.typography.bodyMedium)
    }
}

// ───────────────────────────── Pilot audit log (doctor only) ─────────────────────────────

/** A date button and a time box; the value stays as the typed local "2026-10-29 10:30". */
@Composable private fun PlanDateTimeField(value: String, label: String, onChange: (String) -> Unit) { val t = careText()
    val date = value.take(10); val time = value.drop(11)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlanDateField(date, label, { v -> onChange(listOf(v, time.ifBlank { "00:00" }).filter(String::isNotBlank).joinToString(" ").takeIf { v.isNotBlank() }.orEmpty()) }, latest = LocalDate.now())
        if (date.isNotBlank()) CareField(time, { v -> onChange("$date ${v.take(5)}") }, t("Time (24-hour)", "समय (24 घंटे)"), singleLine = true, placeholder = "10:30")
    }
}

@Composable private fun YesNoField(label: String, value: Boolean?, tag: String, onChange: (Boolean?) -> Unit) {
    Text(label, fontWeight = FontWeight.Medium)
    ChipGroup(value, listOf(true, false), { if (it) Bi("Yes", "हाँ") else Bi("No", "नहीं") }, tag, onChange)
}

@Composable internal fun PilotLogPage(state: CarePlanState, patientId: String, actor: String, edViewDetail: String, onBack: () -> Unit, commit: ((CarePlanState) -> CarePlanState) -> Boolean) {
    val t = careText(); val hindi = careHindi()
    val versions = state.ectprs.filter { it.patientId == patientId }.sortedByDescending { it.version }
    val episodes = state.episodes.filter { it.patientId == patientId }; val outcomes = state.outcomes.filter { it.patientId == patientId }; val incidents = state.incidents.filter { it.patientId == patientId }
    val rows = episodes.map { Pilot.row(it, versions) }; val summary = Pilot.summary(rows)
    val latestSigned = versions.firstOrNull { it.status != EctprStatus.DRAFT }; val reach = latestSigned?.let(Pilot::reachDays); val values = (latestSigned ?: versions.firstOrNull())?.let(Pilot::valuesIndicator)
    var panel by rememberSaveable { mutableStateOf("") }; var saved by remember { mutableStateOf("") }
    val target = Pilot.RETRIEVAL_TARGET_MINUTES; val notRecorded = NOT_RECORDED.pick(hindi)
    fun yesNo(v: Boolean?) = when (v) { null -> notRecorded; true -> t("Yes", "हाँ"); else -> t("No", "नहीं") }
    LazyColumn(Modifier.fillMaxSize().testTag("pilot-log"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PlanHeader(t("Audit log", "ऑडिट लॉग"), onBack, "pilot-header") }
        item { PlanCard {
            @Composable fun indicator(label: String, value: String, tag: String) { Column(Modifier.testTag(tag)) { Text(label, style = MaterialTheme.typography.labelMedium, color = DoctorCardSub); Text(value, fontWeight = FontWeight.SemiBold) } }
            indicator(t("Days to sign", "साइन तक दिन"), if (reach == null) t("Not known", "पता नहीं") else t("$reach days", "$reach दिन"), "pilot-reach")
            indicator(t("Values filled", "मूल्य भरे"), if (values == null) t("No plan yet", "अभी कोई योजना नहीं") else { val filled = values.treatmentFilled; val total = values.treatmentTotal; yesNo(values.valuesFilled) + " · " + t("$filled of $total treatments", "$total में से $filled इलाज") }, "pilot-values")
            run { val within = summary.withinTarget; val all = summary.episodes; indicator(t("Found within $target min", "$target मिनट में मिला"), t("$within of $all episodes", "$all में से $within मामले"), "pilot-retrieval") }
            run { val ok = summary.concordant; val compared = summary.compared; indicator(t("Matched the plan", "योजना के अनुसार"), t("$ok of $compared", "$compared में से $ok"), "pilot-concordance") }
        } }
        if (saved.isNotBlank()) item { Surface(color = DoctorStatusGoodBg, shape = RoundedCornerShape(12.dp)) { Text(saved, Modifier.padding(10.dp)) } }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("episode", t("ED visit", "ED विज़िट"), "pilot-add-episode"), Triple("outcome", t("Outcome", "नतीजा"), "pilot-add-outcome"), Triple("incident", t("Incident", "घटना"), "pilot-add-incident")).forEach { (name, label, tag) ->
                FilterChip(panel == name, { saved = ""; panel = if (panel == name) "" else name }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp).testTag(tag))
            }
        } }
        when (panel) {
            "episode" -> item { EpisodeForm(versions, state.audit.events, edViewDetail, onCancel = { panel = "" }) { e -> if (commit { CarePlanBook.addEpisode(it, e.copy(patientId = patientId, recordedBy = actor, recordedAt = planNow()), actor, planNow()) }) { panel = ""; saved = t("Saved.", "सेव हुआ।") } } }
            "outcome" -> item { OutcomeForm(onCancel = { panel = "" }) { o -> if (commit { CarePlanBook.addOutcome(it, o.copy(patientId = patientId, recordedBy = actor, recordedAt = planNow()), actor, planNow()) }) { panel = ""; saved = t("Saved.", "सेव हुआ।") } } }
            "incident" -> item { IncidentForm(onCancel = { panel = "" }) { i -> if (commit { CarePlanBook.addIncident(it, i.copy(patientId = patientId, ectprId = EctprLifecycle.active(it.ectprs, patientId)?.id, reportedBy = actor, reportedAt = planNow()), actor, planNow()) }) { panel = ""; saved = t("Saved.", "सेव हुआ।") } } }
        }
        item { SectionTitle(t("ED episodes", "ED के मामले")) }
        if (rows.isEmpty()) item { Text(t("None yet.", "अभी कुछ नहीं।"), style = Small) }
        rows.reversed().forEach { row -> item { PlanCard {
            val e = row.episode; val minutes = row.minutes
            Text(e.edArrivalAt.take(16).replace('T', ' '), fontWeight = FontWeight.SemiBold)
            Text(when (row.concordant) { null -> t("Nothing to compare", "तुलना के लिए कुछ नहीं"); true -> t("Matched the plan", "योजना के अनुसार"); else -> t("Differed from the plan", "योजना से अलग") }, color = if (row.concordant == false) RustText else DoctorCardSub, style = MaterialTheme.typography.bodyMedium)
            Text((if (minutes == null) t("Record not opened", "रिकॉर्ड नहीं खुला") else t("Opened after $minutes min", "$minutes मिनट बाद खुला")) + if (row.withinTarget == false) " · " + t("Over target", "लक्ष्य से ज़्यादा") else "", style = MaterialTheme.typography.bodyMedium)
            Text(t("Highest support", "सबसे ज़्यादा मदद") + ": " + (e.highestSupportDelivered?.let { PlanText.ceiling(it).pick(hindi) } ?: notRecorded) + " · CPR: " + yesNo(e.cprGiven), style = MaterialTheme.typography.bodyMedium)
            if (e.discordanceReason.isNotBlank()) Text(t("Why care differed", "इलाज अलग क्यों हुआ") + ": " + e.discordanceReason, style = MaterialTheme.typography.bodyMedium)
            Text(t("Recorded by", "दर्ज किया") + " " + e.recordedBy, style = Small, color = DoctorCardSub)
        } } }
        item { SectionTitle(t("Outcomes", "नतीजे")) }
        if (outcomes.isEmpty()) item { Text(t("None yet.", "अभी कुछ नहीं।"), style = Small) }
        outcomes.reversed().forEach { o -> item { PlanCard {
            val match = Pilot.placeMatches(o); val hours = Pilot.hoursToSymptomRx(o)
            Text(o.placeOfDeath?.let { Pilot.place(it).pick(hindi) } ?: notRecorded, fontWeight = FontWeight.SemiBold)
            Text(when (match) { null -> t("Preference not known", "पसंद पता नहीं"); true -> t("Where they preferred", "जहाँ वे चाहते थे"); else -> t("Not where they preferred", "जहाँ वे चाहते थे, वहाँ नहीं") }, style = MaterialTheme.typography.bodyMedium)
            Text(t("Preferred place", "पसंद की जगह") + ": " + (o.preferredPlaceOfDeath?.let { Pilot.place(it).pick(hindi) } ?: notRecorded), style = MaterialTheme.typography.bodyMedium)
            Text(t("Last 30 days", "आख़िरी 30 दिन") + ": " + t("ICU days", "ICU के दिन") + " " + (o.icuDaysLast30 ?: "–") + " · " + t("ventilator days", "वेंटिलेटर के दिन") + " " + (o.ventilatorDaysLast30 ?: "–"), style = MaterialTheme.typography.bodyMedium)
            Text(t("Admission to first symptom-control prescription", "भर्ती से लक्षण की पहली दवा तक") + ": " + (hours?.let { h -> t("$h hours", "$h घंटे") } ?: notRecorded), style = MaterialTheme.typography.bodyMedium)
            Text(t("Out-of-pocket spend, last admission", "आख़िरी भर्ती का अपनी जेब से ख़र्च") + ": " + (o.outOfPocketLastAdmissionInr?.let { "₹$it" } ?: notRecorded), style = MaterialTheme.typography.bodyMedium)
            Text(t("Recorded by", "दर्ज किया") + " " + o.recordedBy, style = Small, color = DoctorCardSub)
        } } }
        item { SectionTitle(t("Incidents", "घटनाएँ")) }
        if (incidents.isEmpty()) item { Text(t("None yet.", "अभी कुछ नहीं।"), style = Small) }
        incidents.reversed().forEach { i -> item { PlanCard {
            Text(i.reportedAt.take(16).replace('T', ' '), fontWeight = FontWeight.SemiBold)
            if (i.appliedToReversibleIllness) Text(t("Plan applied to a reversible illness", "ठीक होने वाली बीमारी पर योजना लागू हुई"), fontWeight = FontWeight.SemiBold)
            if (i.familyFeltPushed) Text(t("Family felt pushed", "परिवार को दबाव महसूस हुआ"), fontWeight = FontWeight.SemiBold)
            if (i.details.isNotBlank()) Text(i.details, style = MaterialTheme.typography.bodyMedium)
            Text(t("Reported by", "रिपोर्ट की") + " " + i.reportedBy, style = Small, color = DoctorCardSub)
        } } }
    }
}

@Composable private fun EpisodeForm(versions: List<EctprRecord>, audit: List<AuditEvent>, edViewDetail: String, onCancel: () -> Unit, onSave: (EpisodeLog) -> Unit) { val t = careText(); val hindi = careHindi()
    var arrival by rememberSaveable { mutableStateOf("") }; var access by rememberSaveable { mutableStateOf("") }; var support by remember { mutableStateOf<Int?>(null) }
    var cpr by remember { mutableStateOf<Boolean?>(null) }; var icu by remember { mutableStateOf<Boolean?>(null) }; var reason by rememberSaveable { mutableStateOf("") }
    var problems by remember { mutableStateOf<List<EpisodeProblem>>(emptyList()) }; var hint by remember { mutableStateOf("") }; var showReason by rememberSaveable { mutableStateOf(false) }
    val arrivalIso = Pilot.iso(arrival).orEmpty(); val plan = if (arrivalIso.isBlank()) null else Pilot.planInForceAt(versions, arrivalIso)
    val draft = EpisodeLog(CardTokens.random(8), "", plan?.id, arrivalIso, Pilot.iso(access), support, icu, null, null, cpr, reason.trim())
    val preview = if (arrivalIso.isBlank()) null else Pilot.row(draft, versions)
    PlanCard {
        PlanDateTimeField(arrival, t("ED arrival", "ED पहुँचना")) { arrival = it }
        PlanDateTimeField(access, t("Record opened", "रिकॉर्ड खुला")) { access = it }
        preview?.minutes?.let { m -> Text(t("$m minutes after arrival", "पहुँचने के $m मिनट बाद"), style = Small, color = DoctorCardSub) }
        TextButton(enabled = arrivalIso.isNotBlank(), onClick = {
            val found = Pilot.firstEdAccessAfter(audit, arrivalIso, edViewDetail)
            if (found != null) { access = java.time.LocalDateTime.ofInstant(Instant.parse(found), java.time.ZoneId.systemDefault()).toString().take(16).replace('T', ' '); hint = "" } else hint = t("No ED access after this time.", "इस समय के बाद ED का कोई ऐक्सेस नहीं।")
        }) { Text(t("Use access log", "ऐक्सेस लॉग से भरें")) }
        if (hint.isNotBlank()) Text(hint, style = Small)
        OptionGroup(t("Highest breathing support", "साँस की सबसे ऊँची मदद"), support, (0..5).toList(), PlanText::ceiling, tag = "pilot-support", noneLabel = NOT_RECORDED) { support = it }
        YesNoField(t("CPR given?", "CPR दिया गया?"), cpr, "pilot-cpr") { cpr = it }
        YesNoField(t("Admitted to ICU?", "ICU में भर्ती?"), icu, "pilot-icu") { icu = it }
        // Only needed when care differed from the plan: it opens by itself then, otherwise it waits behind "+ Add" until asked for.
        if (showReason || reason.isNotBlank() || preview?.concordant == false || EpisodeProblem.REASON_NEEDED in problems) CareField(reason, { reason = it }, t("Why care differed", "इलाज अलग क्यों हुआ"), minLines = 2)
        else SaharaAddChip(t("Add why care differed", "इलाज अलग क्यों हुआ, जोड़ें"), { showReason = true }, Modifier.testTag("pilot-add-reason"))
        if (preview != null) { val v = plan?.version
            Text(when { plan == null -> t("No signed plan at this time.", "उस समय कोई साइन की गई योजना नहीं।")
                preview.concordant == null -> t("v$v: nothing to compare", "v$v: तुलना के लिए कुछ नहीं")
                preview.concordant -> t("v$v: matches the plan", "v$v: योजना के अनुसार")
                else -> t("v$v: differs from the plan", "v$v: योजना से अलग") }, style = Small, modifier = Modifier.testTag("pilot-compare")) }
        problems.forEach { p -> Text(when (p) { EpisodeProblem.NO_ARRIVAL -> t("Enter the ED arrival time.", "ED पहुँचने का समय लिखें।"); EpisodeProblem.ACCESS_BEFORE_ARRIVAL -> t("The first access time cannot be before arrival.", "रिकॉर्ड खोलने का समय पहुँचने से पहले का नहीं हो सकता।"); EpisodeProblem.REASON_NEEDED -> t("Care differed from the plan. Write why.", "इलाज योजना से अलग हुआ। कारण लिखें।") }, color = RustText, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं")) }
            Button(onClick = { problems = Pilot.episodeProblems(draft, plan); if (problems.isEmpty()) onSave(draft) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pilot-save-episode")) { Text(t("Save", "सहेजें")) }
        }
    }
}

@Composable private fun OutcomeForm(onCancel: () -> Unit, onSave: (OutcomeRecord) -> Unit) { val t = careText()
    var place by remember { mutableStateOf<PilotPlace?>(null) }; var preferred by remember { mutableStateOf<PilotPlace?>(null) }; var icu by remember { mutableStateOf<Int?>(null) }; var vent by remember { mutableStateOf<Int?>(null) }
    var admitted by rememberSaveable { mutableStateOf("") }; var rx by rememberSaveable { mutableStateOf("") }; var spend by remember { mutableStateOf<Int?>(null) }; var problems by remember { mutableStateOf<List<OutcomeProblem>>(emptyList()) }
    PlanCard {
        Text(t("Place of death", "मृत्यु की जगह"), fontWeight = FontWeight.Medium); ChipGroup(place, PilotPlace.entries, Pilot::place, "pilot-place") { place = it }
        Text(t("Preferred place", "पसंद की जगह"), fontWeight = FontWeight.Medium); ChipGroup(preferred, PilotPlace.entries, Pilot::place, "pilot-preferred") { preferred = it }
        PlanNumberField(icu, t("ICU days in the last 30 days", "आख़िरी 30 दिनों में ICU के दिन"), "pilot-icu-days") { icu = it }
        PlanNumberField(vent, t("Ventilator days in the last 30 days", "आख़िरी 30 दिनों में वेंटिलेटर के दिन"), "pilot-vent-days") { vent = it }
        PlanDateTimeField(admitted, t("Last admission time", "आख़िरी भर्ती का समय")) { admitted = it }
        PlanDateTimeField(rx, t("First symptom-control prescription", "लक्षण की पहली दवा")) { rx = it }
        PlanNumberField(spend, t("Out-of-pocket spend, last admission (₹)", "आख़िरी भर्ती का अपनी जेब से ख़र्च (₹)"), "pilot-spend") { spend = it }
        problems.forEach { p -> Text(when (p) { OutcomeProblem.NOTHING_ENTERED -> t("Enter at least one item.", "कम से कम एक चीज़ भरें।"); OutcomeProblem.DAYS_OUT_OF_RANGE -> t("Days must be between 0 and 30.", "दिन 0 से 30 के बीच होने चाहिए।"); OutcomeProblem.RX_BEFORE_ADMISSION -> t("The first prescription cannot be before admission.", "पहली दवा भर्ती से पहले की नहीं हो सकती।"); OutcomeProblem.NEGATIVE_SPEND -> t("Spend cannot be below zero.", "ख़र्च शून्य से कम नहीं हो सकता।") }, color = RustText, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं")) }
            Button(onClick = { val o = OutcomeRecord(CardTokens.random(8), "", place, preferred, icu, vent, Pilot.iso(admitted), Pilot.iso(rx), spend?.toLong()); problems = Pilot.outcomeProblems(o); if (problems.isEmpty()) onSave(o) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pilot-save-outcome")) { Text(t("Save", "सहेजें")) }
        }
    }
}

@Composable private fun IncidentForm(onCancel: () -> Unit, onSave: (IncidentReport) -> Unit) { val t = careText()
    var reversible by rememberSaveable { mutableStateOf(false) }; var pushed by rememberSaveable { mutableStateOf(false) }; var details by rememberSaveable { mutableStateOf("") }; var empty by remember { mutableStateOf(false) }
    PlanCard {
        CheckLine(reversible, { reversible = it }, t("The plan was applied to a reversible illness", "ठीक होने वाली बीमारी पर योजना लागू की गई"))
        CheckLine(pushed, { pushed = it }, t("The family felt pushed", "परिवार को दबाव महसूस हुआ"))
        CareNoteField(details, { details = it }, t("Notes", "नोट"))
        if (empty) Text(t("Tick a box or write what happened.", "कोई बॉक्स चुनें या लिखें कि क्या हुआ।"), color = RustText, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं")) }
            Button(onClick = { val i = IncidentReport(CardTokens.random(8), "", null, reversible, pushed, details.trim()); empty = Pilot.incidentEmpty(i); if (!empty) onSave(i) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pilot-save-incident")) { Text(t("Save", "सहेजें")) }
        }
    }
}

// ───────────────────────────── Living will (Vidhi format) ─────────────────────────────

/** Same sections as the Vidhi model and prototype/app/amd-screen.tsx. The doctor only reads it here: nothing in the app starts or stops treatment. */
@Composable internal fun LivingWillPage(state: CarePlanState, person: Person, store: CarePlanStore, canEdit: Boolean, clinician: Boolean, canManage: Boolean, actor: String, onBack: () -> Unit, commit: ((CarePlanState) -> CarePlanState) -> Boolean, onShare: (File, String) -> Unit) {
    val t = careText(); val hindi = careHindi(); val lang = careLanguage(); val scope = rememberCoroutineScope(); val today = LocalDate.now(); val context = LocalContext.current
    val current = AmdLifecycle.current(state.amds, person.id); val working = AmdLifecycle.working(state.amds, person.id); val latest = AmdLifecycle.latest(state.amds, person.id)
    fun pdf(a: AmdRecord, then: (File, String) -> Unit) = scope.launch {
        val made = runCatching { withContext(Dispatchers.IO) { val f = File(store.folder(person.id), livingWillPdfName(a.version)); CarePlanPdf.livingWill(f, a, lang, today); f to AmdLifecycle.sha256(f.readBytes()) } }.getOrNull()
        if (made == null) android.widget.Toast.makeText(context, t("The PDF could not be made. Try again.", "PDF नहीं बन सका। फिर कोशिश करें।"), android.widget.Toast.LENGTH_LONG).show() else then(made.first, made.second)
    }
    fun printIt(a: AmdRecord) = pdf(a) { file, sha -> if (commit { CarePlanBook.generateAmd(it, a.id, sha, lang.code, actor, planNow(), today) }) onShare(file, a.id) }
    val familyReadOnly = canEdit && !AmdRules.canChange(canEdit, clinician, actor)
    @Suppress("NAME_SHADOWING") val canEdit = AmdRules.canChange(canEdit, clinician, actor)
    LazyColumn(Modifier.fillMaxSize().testTag("living-will"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PlanHeader(t("Advance care planning", "अग्रिम देखभाल योजना"), onBack, "amd-header") }
        item { Text(t("A clinical document that records the person’s own wishes about future care.", "एक क्लिनिकल दस्तावेज़, जिसमें भविष्य की देखभाल के बारे में व्यक्ति की अपनी इच्छाएँ दर्ज होती हैं।"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("amd-clinical-note")) }
        if (familyReadOnly) item { Text(t("Only ${person.name} can change this.", "इसे सिर्फ़ ${person.name} बदल सकते हैं।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub, modifier = Modifier.testTag("amd-family-read-only")) }
        (working ?: current)?.let { a -> item { PlanReadAloud({ PlanSpeech.livingWill(a.content, hindi) }, "amd-${a.id}") } }
        if (latest?.status == AmdStatus.REVOKED) item { val on = latest.revocation?.revokedOn.orEmpty(); Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("amd-cancelled")) { Text(t("Cancelled on $on", "$on को रद्द"), Modifier.padding(12.dp)) } }
        current?.let { c -> item(key = "executed-" + c.id + c.updatedAt) { ExecutedCard(c, store, person.id, canEdit && !clinician, working == null, clinician, actor, commit) } }
        if (clinician) {
            if (current == null) item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text(if (latest != null && latest.status != AmdStatus.REVOKED) t("Not signed. Not valid.", "हस्ताक्षर नहीं। वैध नहीं।") else t("No advance care planning", "कोई अग्रिम देखभाल योजना नहीं"), Modifier.padding(12.dp)) } }
        } else {
            if (working == null && current == null) item { Button(onClick = { commit { CarePlanBook.startAmd(it, person.id, actor, planNow(), person.name).first } }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("amd-start")) { Text(if (latest != null) t("New advance care planning", "नई अग्रिम देखभाल योजना") else t("Start", "शुरू करें")) } }
            working?.let { w -> when (w.status) {
                AmdStatus.DRAFT -> item(key = w.id + w.updatedAt) { AmdEditor(w, canEdit, today, onSave = { c -> commit { CarePlanBook.saveAmd(it, w.id, c, actor, planNow()) } }, onPrint = { c -> if (commit { CarePlanBook.saveAmd(it, w.id, c, actor, planNow()) }) printIt(w.copy(content = c)) }) }
                else -> item(key = w.id + w.updatedAt) { PrintedCard(w, store, person.id, canEdit, today, actor, commit, onReprint = { printIt(w) }) }
            } }
        }
        item { DpdpNoticeCard(DpdpScope.LIVING_WILL, state, person.id, store, canManage, commit, actor) }
    }
}

@Composable private fun AmdIdFields(idType: IdType?, last4: String, tag: String, onType: (IdType?) -> Unit, onLast4: (String) -> Unit) { val t = careText()
    Text(t("ID document", "पहचान पत्र"), fontWeight = FontWeight.Medium)
    ChipGroup(idType, IdType.entries, PlanText::idType, tag, onType)
    CareField(last4, { onLast4(AmdIds.last4(it)) }, t("Last 4 digits only", "केवल आख़िरी 4 अंक"), singleLine = true)
}

@Composable private fun AmdEditor(record: AmdRecord, canEdit: Boolean, today: LocalDate, onSave: (AmdContent) -> Boolean, onPrint: (AmdContent) -> Unit) {
    val t = careText(); val hindi = careHindi()
    var c by remember(record.id, record.updatedAt) { mutableStateOf(record.content) }
    fun ex(change: (AmdExecutant) -> AmdExecutant) { c = c.copy(executant = change(c.executant)) }
    fun decl(change: (AmdDeclarations) -> AmdDeclarations) { c = c.copy(declarations = change(c.declarations)) }
    fun wish(change: (AmdWishes) -> AmdWishes) { c = c.copy(wishes = change(c.wishes)) }
    fun after(change: (AmdAfterDeath) -> AmdAfterDeath) { c = c.copy(afterDeath = change(c.afterDeath)) }
    val issues = AmdRules.validateContent(c, today); val version = record.version; val status = AmdText.status(record.status).pick(hindi)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("Advance care planning v$version · $status", "अग्रिम देखभाल योजना v$version · $status"), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("amd-status-${record.status.name.lowercase()}"))
        PlanCard {
            SectionTitle(t("Section 1: Personal details", "खंड 1: व्यक्तिगत विवरण"))
            CareField(c.executant.name, { v -> ex { it.copy(name = v) } }, t("Full name", "पूरा नाम"), singleLine = true)
            CareField(c.executant.gender, { v -> ex { it.copy(gender = v) } }, t("Gender", "लिंग"), singleLine = true)
            PlanDateField(c.executant.dob, t("Date of birth", "जन्म तिथि"), { v -> ex { it.copy(dob = v) } }, latest = today)
            AmdIdFields(c.executant.idType, c.executant.idLast4, "amd-executant-id", { v -> ex { it.copy(idType = v) } }, { v -> ex { it.copy(idLast4 = v) } })
            CareField(c.executant.permanentAddress, { v -> ex { it.copy(permanentAddress = v) } }, t("Permanent address", "स्थायी पता"), minLines = 2)
            CareField(c.executant.currentAddress, { v -> ex { it.copy(currentAddress = v) } }, t("Current address (if different)", "मौजूदा पता (अगर अलग हो)"), minLines = 2)
            CareField(c.executant.localBody, { v -> ex { it.copy(localBody = v) } }, t("Municipality or panchayat", "नगरपालिका या पंचायत"), singleLine = true)
            CareField(c.values, { v -> c = c.copy(values = v) }, t("Why you are making this, and what matters to you (optional)", "आप यह क्यों बना रहे हैं, और आपके लिए क्या ज़रूरी है (वैकल्पिक)"), minLines = 2)
        }
        PlanCard {
            SectionTitle(t("Please confirm", "कृपया पुष्टि करें"))
            CheckLine(c.declarations.capacity, { v -> decl { it.copy(capacity = v) } }, t("I understand what this document means and what can happen because of it.", "मैं समझता/समझती हूँ कि इस दस्तावेज़ का क्या मतलब है और इससे क्या हो सकता है।"))
            CheckLine(c.declarations.consideredCarefully, { v -> decl { it.copy(consideredCarefully = v) } }, t("I have thought about it carefully.", "मैंने इस पर ध्यान से सोचा है।"))
            CheckLine(c.declarations.voluntary, { v -> decl { it.copy(voluntary = v) } }, t("I am doing this by my own free will. Nobody is forcing or pressuring me.", "मैं यह अपनी मर्ज़ी से कर रहा/रही हूँ। कोई मुझ पर ज़ोर या दबाव नहीं डाल रहा।"))
            CheckLine(c.declarations.understandsNotAppliedIfUnclear, { v -> decl { it.copy(understandsNotAppliedIfUnclear = v) } }, t("I understand doctors will not use it if the situation is unclear or one I did not think of.", "मैं समझता/समझती हूँ कि स्थिति साफ़ न हो या मैंने उसके बारे में न सोचा हो, तो डॉक्टर इसका उपयोग नहीं करेंगे।"))
            CheckLine(c.executant.readOutToMe, { v -> ex { it.copy(readOutToMe = v) } }, t("I cannot read or write. Someone wrote this as I said and read it out to me.", "मैं पढ़-लिख नहीं सकता/सकती। किसी ने इसे मेरे कहे अनुसार लिखा और मुझे पढ़कर सुनाया।"))
            CheckLine(c.declarations.capacityCertificateAttached, { v -> decl { it.copy(capacityCertificateAttached = v) } }, t("A doctor’s certificate that I can decide is attached (optional; not required).", "डॉक्टर का प्रमाणपत्र कि मैं निर्णय ले सकता/सकती हूँ, साथ लगा है (वैकल्पिक; ज़रूरी नहीं)।"))
        }
        PlanCard {
            SectionTitle(t("Section 2: Directions relating to life-sustaining treatment", "खंड 2: जीवन-रक्षक उपचार से जुड़े निर्देश"))
            Text(t("It applies only when your doctors decide there is no reasonable chance you will recover, and more treatment would only make dying longer. Choose the situations:", "यह तभी लागू होगा जब आपके डॉक्टर तय करें कि ठीक होने की कोई उचित संभावना नहीं है, और आगे का इलाज बस मृत्यु की प्रक्रिया लंबी करेगा। स्थितियाँ चुनें:"), style = MaterialTheme.typography.bodyMedium)
            AmdCondition.entries.forEach { s -> CheckLine(s in c.conditions, { on -> c = c.copy(conditions = if (on) c.conditions + s else c.conditions - s) }, AmdText.condition(s).pick(hindi)) }
            CareField(c.anticipatedConditions, { v -> c = c.copy(anticipatedConditions = v) }, t("Any illness you expect, and what you want for it (optional)", "कोई बीमारी जिसकी आप उम्मीद करते हैं, और उसके लिए आप क्या चाहते हैं (वैकल्पिक)"), minLines = 2)
            AmdTreatment.entries.forEach { tr ->
                HorizontalDivider(color = DoctorHairline)
                Text(AmdText.treatment(tr).pick(hindi), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink)
                ChipGroup(c.treatments[tr], AmdChoice.entries, AmdText::choice, "amd-treatment-${tr.name.lowercase()}") { v -> c = c.copy(treatments = if (v == null) c.treatments - tr else c.treatments + (tr to v)) }
                if (c.treatments[tr] == null) Text(AmdText.NOT_DECIDED.pick(hindi), fontSize = 14.sp, color = DoctorCardSub)
            }
            Surface(color = Mint, shape = RoundedCornerShape(12.dp)) { Text(AmdText.COMFORT.pick(hindi), Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        PlanCard {
            SectionTitle(t("Section 3 (Optional): Wishes or desires during end-of-life care", "खंड 3 (वैकल्पिक): जीवन के अंतिम समय की देखभाल में इच्छाएँ"))
            Text(t("Where would you like to be cared for at the end?", "अंत में आप कहाँ देखभाल चाहेंगे?"), fontWeight = FontWeight.Medium)
            ChipGroup(c.wishes.placeOfCare, AmdPlaceOfCare.entries, AmdText::place, "amd-place") { v -> wish { it.copy(placeOfCare = v) } }
            if (c.wishes.placeOfCare == AmdPlaceOfCare.NAMED_INSTITUTION) CareField(c.wishes.institution, { v -> wish { it.copy(institution = v) } }, t("Name of the place", "जगह का नाम"), singleLine = true)
            CareField(c.wishes.physician, { v -> wish { it.copy(physician = v) } }, t("Doctor you would like to look after you (name, place)", "डॉक्टर जिनसे आप देखभाल चाहेंगे (नाम, जगह)"), singleLine = true)
            CareField(c.wishes.focus, { v -> wish { it.copy(focus = v) } }, t("Anything doctors should pay special attention to", "कुछ जिस पर डॉक्टर ख़ास ध्यान दें"), minLines = 2)
            CareField(c.wishes.preferredName, { v -> wish { it.copy(preferredName = v) } }, t("Name you want to be called", "जिस नाम से आप बुलाए जाना चाहते हैं"), singleLine = true)
            CareField(c.wishes.genderPronouns, { v -> wish { it.copy(genderPronouns = v) } }, t("Gender or pronouns to respect", "सम्मान किए जाने वाला लिंग या सर्वनाम"), singleLine = true)
            CareField(c.wishes.partnerName, { v -> wish { it.copy(partnerName = v) } }, t("Partner’s name", "साथी का नाम"), singleLine = true)
            CareField(c.wishes.partnerTerm, { v -> wish { it.copy(partnerTerm = v) } }, t("How staff should refer to them", "स्टाफ़ उन्हें किस तरह संबोधित करे"), singleLine = true, placeholder = t("my partner", "मेरा साथी"))
        }
        PlanCard {
            SectionTitle(t("Section 4: Authorisation of the Designated Healthcare Representative (DHR)", "खंड 4: नामित स्वास्थ्य देखभाल प्रतिनिधि (DHR) का प्राधिकार"))
            c.dhrs.forEachIndexed { i, dhr -> AmdDhrFields(i, dhr, c.dhrs.size, today, onChange = { v -> c = c.copy(dhrs = c.dhrs.mapIndexed { j, x -> if (j == i) v else x }) }, onRemove = { c = c.copy(dhrs = c.dhrs.filterIndexed { j, _ -> j != i }) },
                onMoveUp = { c = c.copy(dhrs = c.dhrs.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }) }) }
            OutlinedButton(onClick = { c = c.copy(dhrs = c.dhrs + AmdDhr()) }, enabled = c.dhrs.size < 4, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-add-dhr")) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(t("Add representative", "प्रतिनिधि जोड़ें")) }
            if (c.dhrs.count { it.name.isNotBlank() } > 1) CheckLine(c.dhrsActJointly, { v -> c = c.copy(dhrsActJointly = v) }, t("They must decide together (instead of in order).", "उन्हें साथ मिलकर तय करना होगा (क्रम के बजाय)।"))
            Text(t("Family doctor (optional)", "पारिवारिक डॉक्टर (वैकल्पिक)"), fontWeight = FontWeight.Medium)
            CareField(c.familyPhysician.name, { v -> c = c.copy(familyPhysician = c.familyPhysician.copy(name = v)) }, t("Name", "नाम"), singleLine = true)
            CareField(c.familyPhysician.registrationNo, { v -> c = c.copy(familyPhysician = c.familyPhysician.copy(registrationNo = v)) }, t("Registration number", "पंजीकरण नंबर"), singleLine = true)
            CareField(c.familyPhysician.phone, { v -> c = c.copy(familyPhysician = c.familyPhysician.copy(phone = v)) }, t("Phone", "फ़ोन"), singleLine = true)
            CareField(c.custodianBody, { v -> c = c.copy(custodianBody = v) }, t("Local body that keeps advance care planning records (custodian)", "स्थानीय निकाय जो अग्रिम देखभाल योजना रखता है (संरक्षक)"), singleLine = true, placeholder = t("Municipality or panchayat", "नगरपालिका या पंचायत"))
        }
        PlanCard {
            SectionTitle(t("Section 5 (Optional): Directions to be followed upon my death", "खंड 5 (वैकल्पिक): मेरी मृत्यु के बाद पालन किए जाने वाले निर्देश"))
            CheckLine(c.afterDeath.organDonor, { v -> after { it.copy(organDonor = v) } }, t("I am a registered organ donor (Form 7, THOTA 1994).", "मैं पंजीकृत अंगदाता हूँ (फ़ॉर्म 7, THOTA 1994)।"))
            CheckLine(c.afterDeath.bodyDonation, { v -> after { it.copy(bodyDonation = v) } }, t("I want to donate my body for medical study.", "मैं चिकित्सा अध्ययन के लिए अपना शरीर दान करना चाहता/चाहती हूँ।"))
            if (c.afterDeath.bodyDonation) CareField(c.afterDeath.bodyDonationInstitution, { v -> after { it.copy(bodyDonationInstitution = v) } }, t("Medical college or organisation", "मेडिकल कॉलेज या संस्था"), singleLine = true)
            CareField(c.afterDeath.lastRites, { v -> after { it.copy(lastRites = v) } }, t("Wishes about last rites", "अंतिम संस्कार के बारे में इच्छाएँ"), minLines = 2)
        }
        IssueList(issues)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onSave(c) }, enabled = canEdit && c != record.content, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("amd-save")) { Text(t("Save", "सहेजें")) }
            Button(onClick = { onPrint(c) }, enabled = canEdit && AmdRules.blocking(issues).isEmpty(), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("amd-generate")) { Text(t("Make PDF", "PDF बनाएँ")) }
        }
    }
}

@Composable private fun AmdDhrFields(index: Int, dhr: AmdDhr, count: Int, today: LocalDate, onChange: (AmdDhr) -> Unit, onRemove: () -> Unit, onMoveUp: () -> Unit) { val t = careText(); val n = index + 1
    Surface(color = Cream, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Representative $n", "प्रतिनिधि $n"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (index > 0) IconButton(onClick = onMoveUp) { Icon(Icons.Default.ArrowUpward, t("Move up", "ऊपर करें")) }
            if (count > 1) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, t("Remove", "हटाएँ")) }
        }
        CareField(dhr.name, { onChange(dhr.copy(name = it)) }, t("Full name", "पूरा नाम"), singleLine = true)
        CareField(dhr.relation, { onChange(dhr.copy(relation = it)) }, t("Relation", "रिश्ता"), singleLine = true)
        CareField(dhr.phone, { onChange(dhr.copy(phone = it)) }, t("Mobile number", "मोबाइल नंबर"), singleLine = true)
        CareField(dhr.phoneAlt, { onChange(dhr.copy(phoneAlt = it)) }, t("Other number (optional)", "दूसरा नंबर (वैकल्पिक)"), singleLine = true)
        PlanDateField(dhr.dob, t("Date of birth (optional)", "जन्म तिथि (वैकल्पिक)"), { onChange(dhr.copy(dob = it)) }, latest = today)
        CareField(dhr.email, { onChange(dhr.copy(email = it)) }, t("Email (optional)", "ईमेल (वैकल्पिक)"), singleLine = true)
        AmdIdFields(dhr.idType, dhr.idLast4, "amd-dhr-$index-id", { onChange(dhr.copy(idType = it)) }, { onChange(dhr.copy(idLast4 = it)) })
        CareField(dhr.permanentAddress, { onChange(dhr.copy(permanentAddress = it)) }, t("Address", "पता"), minLines = 2)
        CheckLine(dhr.agreed, { onChange(dhr.copy(agreed = it)) }, t("I have talked to them and they agreed to be my representative.", "मैंने उनसे बात की है और वे मेरे प्रतिनिधि बनने के लिए सहमत हैं।"))
    } }
}

@Composable private fun CustodianFields(value: AmdCustodian?, defaultBody: String, onChange: (AmdCustodian) -> Unit) { val t = careText()
    val current = value ?: AmdCustodian(body = defaultBody)
    SectionTitle(t("Custodian (local body)", "संरक्षक (स्थानीय निकाय)"))
    PlanOption(t("Copy acknowledged", "प्रति की पावती मिली"), null, value?.status == AmdCustodianStatus.ACKNOWLEDGED, "amd-custodian-ack") { onChange(current.copy(status = AmdCustodianStatus.ACKNOWLEDGED)) }
    PlanOption(t("Custodian pending", "संरक्षक बाक़ी"), null, value?.status == AmdCustodianStatus.PENDING, "amd-custodian-pending") { onChange(current.copy(status = AmdCustodianStatus.PENDING)) }
    if (value?.status == AmdCustodianStatus.ACKNOWLEDGED) {
        CareField(current.officer, { onChange(current.copy(officer = it)) }, t("Officer’s name and office", "अधिकारी का नाम और कार्यालय"), singleLine = true)
        PlanDateField(current.acknowledgedOn, t("Date acknowledged", "पावती की तारीख़"), { onChange(current.copy(acknowledgedOn = it)) }, latest = LocalDate.now())
    }
}

/** Printed, not signed: the steps, and recording the signed paper (L3, L5). It stays not valid until all of this is recorded. */
@Composable private fun PrintedCard(w: AmdRecord, store: CarePlanStore, personId: String, canEdit: Boolean, today: LocalDate, actor: String, commit: ((CarePlanState) -> CarePlanState) -> Boolean, onReprint: () -> Unit) {
    val t = careText(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    var e by remember(w.id) { mutableStateOf(AmdExecution()) }; var custodian by remember(w.id) { mutableStateOf<AmdCustodian?>(null) }; var scanError by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val saved = runCatching { withContext(Dispatchers.IO) {
                val type = context.contentResolver.getType(uri).orEmpty(); val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
                val name = "amd-scan-${w.id}." + if (type == "application/pdf") "pdf" else "jpg"
                File(store.folder(personId), name).writeBytes(bytes); Triple(name, type, AmdLifecycle.sha256(bytes))
            } }.getOrNull()
            scanError = saved == null
            if (saved != null) e = e.copy(scanKey = saved.first, scanName = saved.first, scanType = saved.second, scanSha256 = saved.third, scanUploadedAt = planNow())
        }
    }
    fun w0(change: (AmdWitness) -> AmdWitness, i: Int) { e = e.copy(witnesses = e.witnesses.mapIndexed { j, x -> if (j == i) change(x) else x }) }
    val issues = AmdRules.validateExecution(w, e, custodian, today); val version = w.version
    PlanCard {
        Text(t("Printed. Not valid until signed.", "प्रिंट हुआ। हस्ताक्षर तक मान्य नहीं।"), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("amd-status-generated"))
        Text("1. " + t("Print the PDF. Read it once more.", "PDF प्रिंट करें। एक बार फिर पढ़ें।"), style = MaterialTheme.typography.bodyMedium)
        Text("2. " + t("Sign in front of two adult witnesses.", "दो वयस्क गवाहों के सामने हस्ताक्षर करें।"), style = MaterialTheme.typography.bodyMedium)
        Text("3. " + t("Take it to a notary or a gazetted officer to attest and stamp.", "इसे नोटरी या राजपत्रित अधिकारी के पास सत्यापन और मुहर के लिए ले जाएँ।"), style = MaterialTheme.typography.bodyMedium)
        Text("4. " + t("Take a clear photo or scan of every page and add it here.", "हर पेज की साफ़ फ़ोटो या स्कैन लें और यहाँ जोड़ें।"), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onReprint, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-reprint")) { Text(t("Share PDF", "PDF साझा करें")) }
        PlanDateField(e.signedOn, t("Date signed", "हस्ताक्षर की तारीख़"), { e = e.copy(signedOn = it) }, latest = today)
        CareField(e.place, { e = e.copy(place = it) }, t("Place", "जगह"), singleLine = true)
        CheckLine(e.thumbImpression, { e = e.copy(thumbImpression = it) }, t("Thumb impression instead of signature", "हस्ताक्षर की जगह अँगूठे का निशान"))
        listOf(0, 1).forEach { i -> val n = i + 1; val x = e.witnesses[i]
            Text(t("Witness $n", "गवाह $n"), fontWeight = FontWeight.Medium)
            CareField(x.name, { v -> w0({ it.copy(name = v) }, i) }, t("Full name", "पूरा नाम"), singleLine = true)
            CareField(x.phone, { v -> w0({ it.copy(phone = v) }, i) }, t("Mobile", "मोबाइल"), singleLine = true)
            CareField(x.address, { v -> w0({ it.copy(address = v) }, i) }, t("Address", "पता"), singleLine = true)
        }
        Text(t("Attested by", "सत्यापित करने वाले"), fontWeight = FontWeight.Medium)
        ChipGroup(e.attestor.kind, AttestorKind.entries, AmdText::attestor, "amd-attestor") { e = e.copy(attestor = e.attestor.copy(kind = it)) }
        CareField(e.attestor.name, { e = e.copy(attestor = e.attestor.copy(name = it)) }, t("Full name", "पूरा नाम"), singleLine = true)
        CareField(e.attestor.registrationNo, { e = e.copy(attestor = e.attestor.copy(registrationNo = it)) }, t("Registration no. or designation", "पंजीकरण नंबर या पद"), singleLine = true)
        CustodianFields(custodian, w.content.custodianBody) { custodian = it }
        OutlinedButton(onClick = { pick.launch("*/*") }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-scan")) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text(if (e.scanSha256.isBlank()) t("Add signed scan", "हस्ताक्षरित स्कैन जोड़ें") else t("Added · change", "जुड़ गया · बदलें")) }
        if (scanError) Text(t("Could not read file. Try another.", "फ़ाइल पढ़ी नहीं जा सकी। दूसरी आज़माएँ।"), color = RustText, style = Small)
        IssueList(issues)
        Button(onClick = { commit { CarePlanBook.executeAmd(it, w.id, e, custodian, actor, planNow(), today) } }, enabled = canEdit && AmdRules.blocking(issues).isEmpty(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("amd-execute")) { Text(t("Mark as signed", "हस्ताक्षरित दर्ज करें")) }
        TextButton(onClick = { commit { CarePlanBook.startAmd(it, w.patientId, actor, planNow()).first } }, enabled = canEdit, modifier = Modifier.testTag("amd-new-version")) { Text(t("New version", "नया संस्करण")) }
    }
}

/** The signed version: proof, per-recipient copies with their own consent, custodian, optional ABHA link, change and cancel. */
@Composable private fun ExecutedCard(c: AmdRecord, store: CarePlanStore, personId: String, canEdit: Boolean, noWorking: Boolean, clinician: Boolean, actor: String, commit: ((CarePlanState) -> CarePlanState) -> Boolean) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current; val today = LocalDate.now(); val ex = c.execution; val version = c.version
    PlanCard {
        SectionTitle(t("Signed advance care planning · version $version", "हस्ताक्षरित अग्रिम देखभाल योजना · संस्करण $version"))
        ex?.let { e ->
            Text(t("Signed", "हस्ताक्षर") + ": " + e.signedOn + (if (e.place.isNotBlank()) ", " + e.place else "") + if (e.thumbImpression) " · " + t("thumb impression", "अँगूठे का निशान") else "", style = MaterialTheme.typography.bodyMedium)
            Text(t("Witnesses", "गवाह") + ": " + e.witnesses.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium)
            Text(t("Attested by", "सत्यापित") + ": " + (e.attestor.kind?.let { AmdText.attestor(it).pick(hindi) }.orEmpty()) + " " + e.attestor.name + if (e.attestor.registrationNo.isNotBlank()) " (${e.attestor.registrationNo})" else "", style = MaterialTheme.typography.bodyMedium)
            Text(t("Custodian", "संरक्षक") + ": " + if (c.custodian?.status == AmdCustodianStatus.ACKNOWLEDGED) { val officer = c.custodian.officer; val on = c.custodian.acknowledgedOn; t("Acknowledged by $officer on $on", "$officer द्वारा $on को पावती") } else t("Custodian pending", "संरक्षक बाक़ी"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("amd-custodian-state"))
            var problem by remember(c.id) { mutableStateOf(false) }
            OutlinedButton(onClick = {
                val file = File(store.folder(personId), e.scanKey)
                problem = !file.isFile || AmdLifecycle.sha256(file.readBytes()) != e.scanSha256
                if (!problem) open(context, Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), e.scanType.ifBlank { "image/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-view-scan")) { Text(t("View scan", "स्कैन देखें")) }
            if (problem) Text(t("Scan missing or changed. Ask for the signed paper.", "स्कैन नहीं मिला या बदला हुआ है। हस्ताक्षरित काग़ज़ माँगें।"), color = RustText, style = Small)
        }
        Text(t("Does not want", "नहीं चाहते") + ": " + AmdTreatment.entries.filter { c.content.treatments[it] == AmdChoice.REFUSE }.joinToString("; ") { AmdText.treatment(it).pick(hindi) }.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
        Text(t("Wants", "चाहते हैं") + ": " + AmdTreatment.entries.filter { c.content.treatments[it] == AmdChoice.ACCEPT }.joinToString("; ") { AmdText.treatment(it).pick(hindi) }.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
        Text(t("Representatives, in order", "प्रतिनिधि, क्रम से") + ": " + c.content.dhrs.filter { it.name.isNotBlank() }.mapIndexed { i, d -> "${i + 1}. ${d.name} (${d.relation}) ${d.phone}" }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        if (!clinician) {
            SectionTitle(t("Copies", "प्रतियाँ"))
            c.copies.forEach { copy -> Surface(color = Cream, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("amd-copy-${copy.kind.name.lowercase()}")) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(copy.name + " · " + AmdText.copyKind(copy.kind).pick(hindi), fontWeight = FontWeight.SemiBold)
                CheckLine(copy.consent, { v -> if (canEdit) commit { CarePlanBook.amdCopy(it, c.id, copy.id, v, null, null, actor, planNow(), today) } }, if (copy.kind == AmdRecipientKind.CUSTODIAN) t("The patient agrees to send a copy to the custodian.", "मरीज़ संरक्षक को प्रति भेजने के लिए सहमत है।") else t("The patient agrees to share it, and this person agrees to receive and keep it.", "मरीज़ इसे साझा करने के लिए, और यह व्यक्ति इसे लेने और रखने के लिए सहमत है।"))
                if (copy.consent) {
                    val on = copy.givenOn
                    CheckLine(copy.given, { v -> if (canEdit) commit { CarePlanBook.amdCopy(it, c.id, copy.id, null, v, copy.method ?: AmdCopyMethod.IN_PERSON, actor, planNow(), today) } }, if (copy.given) t("Copy given on $on", "प्रति $on को दी गई") else t("Copy given", "प्रति दी गई"))
                    ChipGroup(copy.method, AmdCopyMethod.entries, AmdText::copyMethod, "amd-copy-method-${copy.id}") { v -> if (canEdit && v != null) commit { CarePlanBook.amdCopy(it, c.id, copy.id, null, null, v, actor, planNow(), today) } }
                }
            } } }
            if (c.custodian?.status != AmdCustodianStatus.ACKNOWLEDGED) {
                var custodian by remember(c.id) { mutableStateOf(c.custodian) }
                CustodianFields(custodian, c.content.custodianBody) { custodian = it }
                val problem = custodian?.let(AmdRules::custodianIssue)
                OutlinedButton(onClick = { custodian?.let { v -> commit { CarePlanBook.amdCustodian(it, c.id, v, actor, planNow()) } } }, enabled = canEdit && custodian != null && problem == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-custodian-save")) { Text(t("Save custodian", "संरक्षक सहेजें")) }
            }
            InfoDisclosure(t("Link ABHA", "ABHA जोड़ें")) {
                var address by rememberSaveable(c.id) { mutableStateOf(c.abha?.address.orEmpty()) }; var consent by rememberSaveable(c.id) { mutableStateOf(c.abha?.consent == true) }
                CareField(address, { address = it }, t("ABHA address or number", "ABHA पता या नंबर"), singleLine = true, placeholder = "name@abdm")
                CheckLine(consent, { consent = it }, t("The patient agrees to link the advance care planning with their ABHA records.", "मरीज़ अग्रिम देखभाल योजना को अपने ABHA रिकॉर्ड से जोड़ने के लिए सहमत है।"))
                val problem = AmdRules.abhaIssue(address, consent)
                problem?.let { Text(it.text(hindi), color = RustText, style = Small) }
                OutlinedButton(onClick = { commit { CarePlanBook.amdAbha(it, c.id, address, consent, actor, planNow(), today) } }, enabled = canEdit && problem == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-abha-save")) { Text(t("Save ABHA link", "ABHA लिंक सहेजें")) }
                c.abha?.let { a -> val on = a.linkedOn; Text(t("Linked on $on", "$on को जोड़ा गया"), style = Small, modifier = Modifier.testTag("amd-abha-linked")) }
            }
            if (noWorking) {
                OutlinedButton(onClick = { commit { CarePlanBook.startAmd(it, personId, actor, planNow()).first } }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-new-version")) { Text(t("New version", "नया संस्करण")) }
            }
            AmdCancelForm(canEdit) { on, written, reason -> commit { CarePlanBook.revokeAmd(it, personId, on, written, reason, actor, planNow(), today) } }
        }
    }
}

@Composable private fun AmdCancelForm(canEdit: Boolean, onRevoke: (String, Boolean, String) -> Unit) { val t = careText(); val today = LocalDate.now()
    var open by rememberSaveable { mutableStateOf(false) }; var on by rememberSaveable { mutableStateOf("") }; var written by rememberSaveable { mutableStateOf(false) }; var reason by rememberSaveable { mutableStateOf("") }
    if (!open) TextButton(onClick = { open = true }, enabled = canEdit, modifier = Modifier.testTag("amd-cancel")) { Text(t("Cancel the advance care planning", "अग्रिम देखभाल योजना रद्द करें"), color = RustText) }
    else Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanDateField(on, t("Date of the written cancellation", "लिखित रद्दीकरण की तारीख़"), { on = it }, latest = today)
        CheckLine(written, { written = it }, t("The person signed a written cancellation.", "व्यक्ति ने लिखित रद्दीकरण पर हस्ताक्षर किए हैं।"))
        CareField(reason, { reason = it }, t("Reason (optional)", "कारण (वैकल्पिक)"))
        val issues = AmdRules.revocationIssues(on, written, today)
        IssueList(issues)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { open = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Keep it", "रहने दें")) }
            Button(onClick = { open = false; onRevoke(on, written, reason.trim()) }, enabled = issues.isEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("amd-cancel-confirm")) { Text(t("Cancel the advance care planning", "अग्रिम देखभाल योजना रद्द करें")) }
        }
    } }
}
