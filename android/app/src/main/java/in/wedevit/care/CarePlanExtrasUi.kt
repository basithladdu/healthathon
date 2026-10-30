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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { if (reader.speaking) reader.stop() else reader.read(text(), lang.code + "-IN") }, modifier = Modifier.heightIn(min = 48.dp).testTag("plan-read-aloud")) {
            Icon(if (reader.speaking) Icons.Default.Stop else Icons.Default.VolumeUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(if (reader.speaking) t("Stop reading", "पढ़ना रोकें") else t("Read aloud", "पढ़कर सुनाएँ"))
        }
        if (reader.status.isNotBlank()) Text(reader.status, style = Small, color = DoctorCardSub)
    }
}

/** L8: information only. The app has no "withdraw" action. */
@Composable internal fun BoardPathway(title: String) { val hindi = careHindi()
    InfoDisclosure(title) { AmdText.BOARD_PATHWAY.forEach { Text(it.pick(hindi), style = MaterialTheme.typography.bodyMedium) } }
}

@Composable internal fun LegalPendingBox() { val t = careText(); val hindi = careHindi()
    Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("legal-pending")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(AmdText.LEGAL_PENDING_TITLE.pick(hindi), fontWeight = FontWeight.Bold)
        Text(AmdText.LEGAL_PENDING.pick(hindi), style = MaterialTheme.typography.bodyMedium)
        Text(AmdText.RECONSIDERATION.pick(hindi), style = MaterialTheme.typography.bodyMedium)
        BoardPathway(t("See every step of the board process (information only)", "बोर्ड प्रक्रिया के सभी चरण देखें (केवल जानकारी)"))
    } }
}

/** Notice at the start of the plan and the living will, with a working download and delete. Family members see the notice only. */
@Composable internal fun DpdpNoticeCard(scope: DpdpScope, state: CarePlanState, patientId: String, store: CarePlanStore, canManage: Boolean, commit: ((CarePlanState) -> CarePlanState) -> Boolean, actor: String) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current; val coroutine = rememberCoroutineScope()
    var open by rememberSaveable(scope) { mutableStateOf(!store.noticeSeen(scope)) }
    var confirming by rememberSaveable { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    val blocks = Dpdp.notice(scope)
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Your data and your rights", "आपका डेटा और आपके अधिकार"), Modifier.weight(1f).testTag("dpdp-title"), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { open = !open }, modifier = Modifier.testTag("dpdp-toggle")) { Text(if (open) t("Hide", "छिपाएँ") else t("Show", "दिखाएँ")) }
        }
        if (open) {
            PlanReadAloud({ blocks.joinToString("\n") { it.title.pick(hindi) + ". " + it.body.pick(hindi) } }, "dpdp-$scope")
            blocks.forEach { b -> Text(b.title.pick(hindi), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium); Text(b.body.pick(hindi), style = Small) }
            if (!canManage) Text(t("Only the patient or the care team can download or delete this data. Please ask them.", "यह डेटा सिर्फ़ मरीज़ या देखभाल टीम डाउनलोड कर या मिटा सकती है। कृपया उनसे कहें।"), style = Small, color = RustText)
            else {
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dpdp-download"), onClick = {
                    busy = true; message = ""
                    coroutine.launch {
                        val file = runCatching { withContext(Dispatchers.IO) {
                            val folder = store.folder(patientId)
                            val scans = Dpdp.scanKeys(state, patientId).mapNotNull { key -> File(folder, key).takeIf { it.isFile }?.let { f -> key to JSONObject().put("name", f.name).put("dataUrl", "data:application/octet-stream;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)) } }.toMap()
                            File(folder, Dpdp.fileName(patientId, LocalDate.now())).also { it.writeText(Dpdp.export(state, patientId, Instant.now(), scans).toString(2)) }
                        } }.getOrNull()
                        busy = false
                        if (file == null) message = t("The download could not be made. Try again.", "डाउनलोड नहीं बन सका। फिर कोशिश करें।")
                        else {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), t("Download my data", "मेरा डेटा डाउनलोड करें")))
                            commit { CarePlanBook.exported(it, scope, patientId, actor, planNow()) }
                            message = t("Your data is ready. It holds private health details, so keep the file safe.", "आपका डेटा तैयार है। इसमें निजी सेहत की जानकारी है, इसलिए फ़ाइल सँभालकर रखें।")
                        }
                    }
                }) { Text(t("Download my data", "मेरा डेटा डाउनलोड करें")) }
                if (!confirming) TextButton(onClick = { confirming = true }, enabled = !busy, modifier = Modifier.testTag("dpdp-delete")) { Text(t("Delete my local draft", "मेरा लोकल ड्राफ़्ट मिटाएँ"), color = RustText) }
                else Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("This deletes the unsigned draft from this phone. It cannot be undone. Signed versions and the log stay.", "इससे बिना साइन का ड्राफ़्ट इस फ़ोन से मिट जाएगा। इसे वापस नहीं लाया जा सकता। साइन किए गए संस्करण और रिकॉर्ड रहेंगे।"), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { confirming = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Keep the draft", "ड्राफ़्ट रखें")) }
                        Button(modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dpdp-delete-confirm"), onClick = {
                            var deleted = 0
                            commit { s -> CarePlanBook.deleteDrafts(s, scope, patientId, actor, planNow()).also { deleted = it.second }.first }
                            confirming = false
                            message = if (deleted > 0) t("The unsigned draft is deleted from this phone. Signed versions and the log were kept.", "बिना साइन का ड्राफ़्ट इस फ़ोन से मिट गया। साइन किए गए संस्करण और रिकॉर्ड रखे गए हैं।")
                                else t("There is no unsigned draft to delete. A plan someone has already signed is kept.", "मिटाने के लिए कोई बिना साइन का ड्राफ़्ट नहीं है। जिस योजना पर किसी ने साइन कर दिया है, वह रखी जाती है।")
                        }) { Text(t("Yes, delete the draft", "हाँ, ड्राफ़्ट मिटाएँ")) }
                    }
                } }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dpdp-message"))
            TextButton(onClick = { store.markNoticeSeen(scope); open = false }, modifier = Modifier.testTag("dpdp-read")) { Text(t("I have read this", "मैंने यह पढ़ लिया")) }
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
        if (checked && configured == false) Text(t("SMS not configured on this server, so reminders cannot be sent. Please phone them.", "इस सर्वर पर SMS सेट नहीं है, इसलिए संदेश नहीं भेजे जा सकते। कृपया फ़ोन करें।"), style = Small, color = RustText, modifier = Modifier.testTag("review-sms-not-configured"))
        if (checked && configured == null) Text(t("The server could not be reached. Check the internet, or phone them.", "सर्वर से संपर्क नहीं हो सका। इंटरनेट देखें, या फ़ोन करें।"), style = Small, color = RustText)
        if (recipient == null) Text(t("No valid mobile number for the patient or the primary decision-maker.", "मरीज़ या मुख्य निर्णयकर्ता का कोई सही मोबाइल नंबर नहीं है।"), style = Small)
        else { val name = recipient.first; Text(t("The reminder goes to $name. The number goes to the SMS company.", "संदेश $name को जाएगा। नंबर SMS कंपनी के पास जाता है।"), style = Small) }
        OutlinedButton(enabled = canEdit && !busy && recipient != null && configured == true, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review-sms-send"), onClick = {
            busy = true; result = ""
            coroutine.launch { val status = ReviewReminder.send(r, recipient!!.second, transport); busy = false; result = status; onSent(status) }
        }) { Text(if (busy) t("Sending…", "भेज रहे हैं…") else t("Send review reminder", "समीक्षा का संदेश भेजें")) }
        if (result.isNotBlank()) Text(when (result) {
            "sent" -> t("Reminder sent.", "संदेश भेज दिया गया।")
            "not-configured" -> t("SMS not configured on this server. No message was sent. Please phone them.", "इस सर्वर पर SMS सेट नहीं है। कोई संदेश नहीं गया। कृपया फ़ोन करें।")
            "too-many" -> t("Too many reminders to this number in the last hour. No message was sent.", "पिछले एक घंटे में इस नंबर पर बहुत सारे संदेश गए। कोई संदेश नहीं गया।")
            "no-valid-number" -> t("No valid mobile number for the patient or the primary decision-maker.", "मरीज़ या मुख्य निर्णयकर्ता का कोई सही मोबाइल नंबर नहीं है।")
            else -> t("The SMS could not be sent. Please phone them.", "SMS नहीं भेजा जा सका। कृपया फ़ोन करें।")
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
    LazyColumn(Modifier.fillMaxSize().testTag("pilot-log"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PlanHeader(t("Pilot audit log", "पायलट ऑडिट लॉग"), onBack, "pilot-header") }
        item { Text(t("For the care team only. Made-up patients only during the pilot. Saved on this phone.", "केवल देखभाल टीम के लिए। पायलट में सिर्फ़ काल्पनिक मरीज़। इसी फ़ोन पर सेव।"), style = MaterialTheme.typography.bodyMedium) }
        item { PlanCard {
            @Composable fun indicator(label: String, value: String, tag: String) { Column(Modifier.testTag(tag)) { Text(label, style = MaterialTheme.typography.labelMedium, color = DoctorCardSub); Text(value, fontWeight = FontWeight.SemiBold) } }
            indicator(t("Reach: found eligible to signed", "पहुँच: योग्य पाए जाने से साइन तक"), if (reach == null) t("Not known yet: needs the date found eligible and a signature", "अभी पता नहीं: योग्य पाए जाने की तारीख़ और साइन चाहिए") else t("$reach days", "$reach दिन"), "pilot-reach")
            indicator(t("Values section completed", "मूल्य वाला हिस्सा पूरा"), if (values == null) t("No plan yet", "अभी कोई योजना नहीं") else { val filled = values.treatmentFilled; val total = values.treatmentTotal; yesNo(values.valuesFilled) + " · " + t("treatment fields filled: $filled of $total", "भरे गए इलाज के हिस्से: $total में से $filled") }, "pilot-values")
            run { val within = summary.withinTarget; val all = summary.episodes; indicator(t("Record found within $target minutes of ED arrival", "ED पहुँचने के $target मिनट में रिकॉर्ड मिला"), t("$within of $all episodes", "$all में से $within मामले"), "pilot-retrieval") }
            run { val ok = summary.concordant; val compared = summary.compared; indicator(t("Care matched the recorded plan", "इलाज दर्ज योजना के अनुसार हुआ"), t("$ok of $compared that could be compared", "जिनकी तुलना हो सकी, उन $compared में से $ok"), "pilot-concordance") }
        } }
        if (saved.isNotBlank()) item { Surface(color = DoctorStatusGoodBg, shape = RoundedCornerShape(12.dp)) { Text(saved, Modifier.padding(10.dp)) } }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("episode", t("Log an ED episode", "ED का मामला दर्ज करें"), "pilot-add-episode"), Triple("outcome", t("Record an outcome", "नतीजा दर्ज करें"), "pilot-add-outcome"), Triple("incident", t("Report an incident", "घटना की रिपोर्ट करें"), "pilot-add-incident")).forEach { (name, label, tag) ->
                FilterChip(panel == name, { saved = ""; panel = if (panel == name) "" else name }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp).testTag(tag))
            }
        } }
        when (panel) {
            "episode" -> item { EpisodeForm(versions, state.audit.events, edViewDetail, onCancel = { panel = "" }) { e -> if (commit { CarePlanBook.addEpisode(it, e.copy(patientId = patientId, recordedBy = actor, recordedAt = planNow()), actor, planNow()) }) { panel = ""; saved = t("ED episode saved.", "ED का मामला सेव हुआ।") } } }
            "outcome" -> item { OutcomeForm(onCancel = { panel = "" }) { o -> if (commit { CarePlanBook.addOutcome(it, o.copy(patientId = patientId, recordedBy = actor, recordedAt = planNow()), actor, planNow()) }) { panel = ""; saved = t("Outcome saved.", "नतीजा सेव हुआ।") } } }
            "incident" -> item { IncidentForm(onCancel = { panel = "" }) { i -> if (commit { CarePlanBook.addIncident(it, i.copy(patientId = patientId, ectprId = EctprLifecycle.active(it.ectprs, patientId)?.id, reportedBy = actor, reportedAt = planNow()), actor, planNow()) }) { panel = ""; saved = t("Incident saved.", "घटना सेव हुई।") } } }
        }
        item { SectionTitle(t("ED episodes", "ED के मामले")) }
        if (rows.isEmpty()) item { Text(t("None recorded yet.", "अभी कुछ दर्ज नहीं।"), style = Small) }
        rows.reversed().forEach { row -> item { PlanCard {
            val e = row.episode; val minutes = row.minutes
            Text(e.edArrivalAt.take(16).replace('T', ' '), fontWeight = FontWeight.SemiBold)
            Text(when (row.concordant) { null -> t("Nothing to compare", "तुलना के लिए कुछ नहीं"); true -> t("Matched the plan", "योजना के अनुसार"); else -> t("Differed from the plan", "योजना से अलग") }, color = if (row.concordant == false) RustText else DoctorCardSub, style = MaterialTheme.typography.bodyMedium)
            Text((if (minutes == null) t("The record was not opened", "रिकॉर्ड नहीं खोला गया") else t("Record opened after $minutes minutes", "$minutes मिनट बाद रिकॉर्ड खुला")) + if (row.withinTarget == false) " · " + t("over the $target-minute target", "$target मिनट के लक्ष्य से ज़्यादा") else "", style = MaterialTheme.typography.bodyMedium)
            Text(t("Highest support", "सबसे ज़्यादा मदद") + ": " + (e.highestSupportDelivered?.let { PlanText.ceiling(it).pick(hindi) } ?: notRecorded) + " · CPR: " + yesNo(e.cprGiven), style = MaterialTheme.typography.bodyMedium)
            if (e.discordanceReason.isNotBlank()) Text(t("Why care differed", "इलाज अलग क्यों हुआ") + ": " + e.discordanceReason, style = MaterialTheme.typography.bodyMedium)
            Text(t("Recorded by", "दर्ज किया") + " " + e.recordedBy, style = Small, color = DoctorCardSub)
        } } }
        item { SectionTitle(t("Outcomes", "नतीजे")) }
        if (outcomes.isEmpty()) item { Text(t("None recorded yet.", "अभी कुछ दर्ज नहीं।"), style = Small) }
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
        if (incidents.isEmpty()) item { Text(t("None reported yet.", "अभी कोई रिपोर्ट नहीं।"), style = Small) }
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
    var problems by remember { mutableStateOf<List<EpisodeProblem>>(emptyList()) }; var hint by remember { mutableStateOf("") }
    val arrivalIso = Pilot.iso(arrival).orEmpty(); val plan = if (arrivalIso.isBlank()) null else Pilot.planInForceAt(versions, arrivalIso)
    val draft = EpisodeLog(CardTokens.random(8), "", plan?.id, arrivalIso, Pilot.iso(access), support, icu, null, null, cpr, reason.trim())
    val preview = if (arrivalIso.isBlank()) null else Pilot.row(draft, versions)
    PlanCard {
        PlanDateTimeField(arrival, t("ED arrival time", "ED पहुँचने का समय")) { arrival = it }
        PlanDateTimeField(access, t("First time the record was opened", "रिकॉर्ड पहली बार कब खुला")) { access = it }
        preview?.minutes?.let { m -> Text(t("$m minutes after arrival", "पहुँचने के $m मिनट बाद"), style = Small, color = DoctorCardSub) }
        TextButton(enabled = arrivalIso.isNotBlank(), onClick = {
            val found = Pilot.firstEdAccessAfter(audit, arrivalIso, edViewDetail)
            if (found != null) { access = java.time.LocalDateTime.ofInstant(Instant.parse(found), java.time.ZoneId.systemDefault()).toString().take(16).replace('T', ' '); hint = "" } else hint = t("The access log has no ED access after this arrival time.", "ऐक्सेस लॉग में इस समय के बाद ED का कोई ऐक्सेस नहीं है।")
        }) { Text(t("Fill from the access log", "ऐक्सेस लॉग से भरें")) }
        if (hint.isNotBlank()) Text(hint, style = Small)
        OptionGroup(t("Highest breathing support actually given", "असल में दी गई साँस की सबसे ज़्यादा मदद"), support, (0..5).toList(), PlanText::ceiling, tag = "pilot-support", noneLabel = NOT_RECORDED) { support = it }
        YesNoField(t("CPR given?", "CPR दिया गया?"), cpr, "pilot-cpr") { cpr = it }
        YesNoField(t("Admitted to ICU?", "ICU में भर्ती?"), icu, "pilot-icu") { icu = it }
        CareField(reason, { reason = it }, t("If care differed from the plan, why?", "अगर इलाज योजना से अलग हुआ, तो क्यों?"), minLines = 2)
        if (preview != null) { val v = plan?.version
            Text(when { plan == null -> t("No signed plan was in force at this time, so there is nothing to compare.", "उस समय कोई साइन की गई योजना लागू नहीं थी, इसलिए तुलना नहीं हो सकती।")
                preview.concordant == null -> t("Compared with version $v: nothing to compare yet.", "संस्करण $v से तुलना: अभी तुलना के लिए कुछ नहीं।")
                preview.concordant -> t("Compared with version $v: matches the plan.", "संस्करण $v से तुलना: योजना के अनुसार।")
                else -> t("Compared with version $v: differs from the plan.", "संस्करण $v से तुलना: योजना से अलग।") }, style = Small, modifier = Modifier.testTag("pilot-compare")) }
        problems.forEach { p -> Text(when (p) { EpisodeProblem.NO_ARRIVAL -> t("Enter the ED arrival time.", "ED पहुँचने का समय लिखें।"); EpisodeProblem.ACCESS_BEFORE_ARRIVAL -> t("The first access time cannot be before arrival.", "रिकॉर्ड खोलने का समय पहुँचने से पहले का नहीं हो सकता।"); EpisodeProblem.REASON_NEEDED -> t("Care differed from the plan. Write why.", "इलाज योजना से अलग हुआ। कारण लिखें।") }, color = RustText, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं")) }
            Button(onClick = { problems = Pilot.episodeProblems(draft, plan); if (problems.isEmpty()) onSave(draft) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pilot-save-episode")) { Text(t("Save the episode", "मामला सेव करें")) }
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
            Button(onClick = { val o = OutcomeRecord(CardTokens.random(8), "", place, preferred, icu, vent, Pilot.iso(admitted), Pilot.iso(rx), spend?.toLong()); problems = Pilot.outcomeProblems(o); if (problems.isEmpty()) onSave(o) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pilot-save-outcome")) { Text(t("Save the outcome", "नतीजा सेव करें")) }
        }
    }
}

@Composable private fun IncidentForm(onCancel: () -> Unit, onSave: (IncidentReport) -> Unit) { val t = careText()
    var reversible by rememberSaveable { mutableStateOf(false) }; var pushed by rememberSaveable { mutableStateOf(false) }; var details by rememberSaveable { mutableStateOf("") }; var empty by remember { mutableStateOf(false) }
    PlanCard {
        CheckLine(reversible, { reversible = it }, t("The plan was applied to a reversible illness", "ठीक होने वाली बीमारी पर योजना लागू की गई"))
        CheckLine(pushed, { pushed = it }, t("The family felt pushed", "परिवार को दबाव महसूस हुआ"))
        CareField(details, { details = it }, t("Notes", "नोट"), minLines = 3)
        if (empty) Text(t("Tick a box or write what happened.", "कोई बॉक्स चुनें या लिखें कि क्या हुआ।"), color = RustText, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं")) }
            Button(onClick = { val i = IncidentReport(CardTokens.random(8), "", null, reversible, pushed, details.trim()); empty = Pilot.incidentEmpty(i); if (!empty) onSave(i) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pilot-save-incident")) { Text(t("Save the incident", "घटना सेव करें")) }
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
    LazyColumn(Modifier.fillMaxSize().testTag("living-will"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PlanHeader(t("Living will", "लिविंग विल"), onBack, "amd-header") }
        item { Text(t("This follows the model living will by the Vidhi Centre for Legal Policy and ACP India, and the Supreme Court’s rules (2018, changed on 24 January 2023). If both a living will and an emergency plan exist, the living will governs.", "यह विधि सेंटर फ़ॉर लीगल पॉलिसी और ACP इंडिया की मॉडल लिविंग विल, और सुप्रीम कोर्ट के नियमों (2018, 24 जनवरी 2023 को बदले गए) पर आधारित है। अगर लिविंग विल और आपातकालीन योजना दोनों हों, तो लिविंग विल मानी जाती है।"), style = MaterialTheme.typography.bodyMedium) }
        item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp)) { Text(AmdText.PAPER.pick(hindi), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) } }
        (working ?: current)?.let { a -> item { PlanReadAloud({ PlanSpeech.livingWill(a.content, hindi) }, "amd-${a.id}") } }
        item { DpdpNoticeCard(DpdpScope.LIVING_WILL, state, person.id, store, canManage, commit, actor) }
        item { BoardPathway(t("What happens if treatment is to be held back or stopped (information only)", "अगर इलाज रोकना या न शुरू करना हो तो क्या होता है (केवल जानकारी)")) }
        if (latest?.status == AmdStatus.REVOKED) item { val on = latest.revocation?.revokedOn.orEmpty(); Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("amd-cancelled")) { Text(t("The living will was cancelled in writing on $on. It no longer applies.", "लिविंग विल $on को लिखित रूप में रद्द की गई। यह अब लागू नहीं है।"), Modifier.padding(12.dp)) } }
        current?.let { c -> item(key = "executed-" + c.id + c.updatedAt) { ExecutedCard(c, store, person.id, canEdit && !clinician, working == null, clinician, actor, commit) } }
        if (clinician) {
            if (current == null) item { Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text(if (latest != null && latest.status != AmdStatus.REVOKED) t("A living will has been started or printed but is not signed and recorded. It is not valid.", "लिविंग विल शुरू या प्रिंट हुई है पर हस्ताक्षरित और दर्ज नहीं है। यह वैध नहीं है।") else t("No living will is recorded in Sahara. Ask the family if a signed paper exists.", "सहारा में कोई लिविंग विल दर्ज नहीं है। परिवार से पूछें कि क्या कोई हस्ताक्षरित काग़ज़ है।"), Modifier.padding(12.dp)) } }
        } else {
            if (working == null && current == null) item { Button(onClick = { commit { CarePlanBook.startAmd(it, person.id, actor, planNow(), person.name).first } }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("amd-start")) { Text(if (latest != null) t("Make a new living will", "नई लिविंग विल बनाएँ") else t("Start a living will", "लिविंग विल शुरू करें")) } }
            working?.let { w -> when (w.status) {
                AmdStatus.DRAFT -> item(key = w.id + w.updatedAt) { AmdEditor(w, canEdit, today, onSave = { c -> commit { CarePlanBook.saveAmd(it, w.id, c, actor, planNow()) } }, onPrint = { c -> if (commit { CarePlanBook.saveAmd(it, w.id, c, actor, planNow()) }) printIt(w.copy(content = c)) }) }
                else -> item(key = w.id + w.updatedAt) { PrintedCard(w, store, person.id, canEdit, today, actor, commit, onReprint = { printIt(w) }) }
            } }
        }
    }
}

@Composable private fun AmdIdFields(idType: IdType?, last4: String, tag: String, onType: (IdType?) -> Unit, onLast4: (String) -> Unit) { val t = careText()
    Text(t("ID document", "पहचान पत्र"), fontWeight = FontWeight.Medium)
    ChipGroup(idType, IdType.entries, PlanText::idType, tag, onType)
    CareField(last4, { onLast4(AmdIds.last4(it)) }, t("Last 4 digits only", "केवल आख़िरी 4 अंक"), singleLine = true, supporting = t("Never type the full number here. Write it by hand on the paper.", "पूरा नंबर यहाँ कभी न लिखें। उसे काग़ज़ पर हाथ से लिखें।"))
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
        Text(t("Living will v$version · $status", "लिविंग विल v$version · $status"), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("amd-status-${record.status.name.lowercase()}"))
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
            Text(t("For each treatment, choose what you want in those situations. You can leave any as “Not decided”.", "हर इलाज के लिए चुनें कि उन स्थितियों में आप क्या चाहते हैं। किसी को भी “तय नहीं” छोड़ सकते हैं।"), style = MaterialTheme.typography.bodyMedium)
            AmdTreatment.entries.forEach { tr ->
                Text(AmdText.treatment(tr).pick(hindi), fontWeight = FontWeight.SemiBold)
                Text(AmdText.treatmentHelp(tr).pick(hindi), style = Small, color = DoctorCardSub)
                ChipGroup(c.treatments[tr], AmdChoice.entries, AmdText::choice, "amd-treatment-${tr.name.lowercase()}") { v -> c = c.copy(treatments = if (v == null) c.treatments - tr else c.treatments + (tr to v)) }
                if (c.treatments[tr] == null) Text(AmdText.NOT_DECIDED.pick(hindi), style = Small, color = DoctorCardSub)
            }
            Surface(color = Mint, shape = RoundedCornerShape(12.dp)) { Text(AmdText.COMFORT.pick(hindi), Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        PlanCard {
            SectionTitle(t("Section 3 (Optional): Wishes or desires during end-of-life care", "खंड 3 (वैकल्पिक): जीवन के अंतिम समय की देखभाल में इच्छाएँ"))
            Text(t("Where would you like to be cared for at the end?", "अंत में आप कहाँ देखभाल चाहेंगे?"), fontWeight = FontWeight.Medium)
            ChipGroup(c.wishes.placeOfCare, AmdPlaceOfCare.entries, AmdText::place, "amd-place") { v -> wish { it.copy(placeOfCare = v) } }
            if (c.wishes.placeOfCare == AmdPlaceOfCare.NAMED_INSTITUTION) CareField(c.wishes.institution, { v -> wish { it.copy(institution = v) } }, t("Name of the place", "जगह का नाम"), singleLine = true)
            CareField(c.wishes.physician, { v -> wish { it.copy(physician = v) } }, t("Doctor you would like to look after you (name, place)", "डॉक्टर जिनसे आप देखभाल चाहेंगे (नाम, जगह)"), singleLine = true)
            CareField(c.wishes.focus, { v -> wish { it.copy(focus = v) } }, t("Anything doctors should pay special attention to", "कुछ जिस पर डॉक्टर ख़ास ध्यान दें"), minLines = 2, placeholder = t("For example: easing worry and confusion so I can spend time with family", "जैसे: चिंता और उलझन कम करना ताकि मैं परिवार के साथ समय बिता सकूँ"))
            CareField(c.wishes.preferredName, { v -> wish { it.copy(preferredName = v) } }, t("Name you want to be called", "जिस नाम से आप बुलाए जाना चाहते हैं"), singleLine = true)
            CareField(c.wishes.genderPronouns, { v -> wish { it.copy(genderPronouns = v) } }, t("Gender or pronouns to respect", "सम्मान किए जाने वाला लिंग या सर्वनाम"), singleLine = true)
            CareField(c.wishes.partnerName, { v -> wish { it.copy(partnerName = v) } }, t("Partner’s name", "साथी का नाम"), singleLine = true)
            CareField(c.wishes.partnerTerm, { v -> wish { it.copy(partnerTerm = v) } }, t("How staff should refer to them", "स्टाफ़ उन्हें किस तरह संबोधित करे"), singleLine = true, placeholder = t("my partner", "मेरा साथी"))
        }
        PlanCard {
            SectionTitle(t("Section 4: Authorisation of the Designated Healthcare Representative (DHR)", "खंड 4: नामित स्वास्थ्य देखभाल प्रतिनिधि (DHR) का प्राधिकार"))
            Text(t("Representatives (DHRs) speak for you when you cannot. Put them in order: the first is asked first. Talk to them before you name them.", "प्रतिनिधि (DHR) तब आपकी ओर से बोलते हैं जब आप नहीं बोल सकते। उन्हें क्रम में रखें: पहले वाले से पहले पूछा जाएगा। नाम लिखने से पहले उनसे बात करें।"), style = MaterialTheme.typography.bodySmall)
            c.dhrs.forEachIndexed { i, dhr -> AmdDhrFields(i, dhr, c.dhrs.size, today, onChange = { v -> c = c.copy(dhrs = c.dhrs.mapIndexed { j, x -> if (j == i) v else x }) }, onRemove = { c = c.copy(dhrs = c.dhrs.filterIndexed { j, _ -> j != i }) },
                onMoveUp = { c = c.copy(dhrs = c.dhrs.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }) }) }
            OutlinedButton(onClick = { c = c.copy(dhrs = c.dhrs + AmdDhr()) }, enabled = c.dhrs.size < 4, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-add-dhr")) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(t("Add another representative", "एक और प्रतिनिधि जोड़ें")) }
            if (c.dhrs.count { it.name.isNotBlank() } > 1) CheckLine(c.dhrsActJointly, { v -> c = c.copy(dhrsActJointly = v) }, t("They must decide together (instead of in order).", "उन्हें साथ मिलकर तय करना होगा (क्रम के बजाय)।"))
            Text(t("Family doctor (optional)", "पारिवारिक डॉक्टर (वैकल्पिक)"), fontWeight = FontWeight.Medium)
            CareField(c.familyPhysician.name, { v -> c = c.copy(familyPhysician = c.familyPhysician.copy(name = v)) }, t("Name", "नाम"), singleLine = true)
            CareField(c.familyPhysician.registrationNo, { v -> c = c.copy(familyPhysician = c.familyPhysician.copy(registrationNo = v)) }, t("Registration number", "पंजीकरण नंबर"), singleLine = true)
            CareField(c.familyPhysician.phone, { v -> c = c.copy(familyPhysician = c.familyPhysician.copy(phone = v)) }, t("Phone", "फ़ोन"), singleLine = true)
            CareField(c.custodianBody, { v -> c = c.copy(custodianBody = v) }, t("Local body that keeps living wills (custodian)", "स्थानीय निकाय जो लिविंग विल रखता है (संरक्षक)"), singleLine = true,
                supporting = t("For example your municipal corporation or gram panchayat. Maharashtra and Karnataka (rural) have named custodians; Andhra Pradesh has not confirmed one yet. You can still sign and record “custodian pending”.", "जैसे आपका नगर निगम या ग्राम पंचायत। महाराष्ट्र और कर्नाटक (ग्रामीण) ने संरक्षक तय किए हैं; आंध्र प्रदेश ने अभी पुष्टि नहीं की है। आप फिर भी हस्ताक्षर करके “संरक्षक बाक़ी” दर्ज कर सकते हैं।"))
        }
        PlanCard {
            SectionTitle(t("Section 5 (Optional): Directions to be followed upon my death", "खंड 5 (वैकल्पिक): मेरी मृत्यु के बाद पालन किए जाने वाले निर्देश"))
            CheckLine(c.afterDeath.organDonor, { v -> after { it.copy(organDonor = v) } }, t("I am a registered organ donor (Form 7, THOTA 1994).", "मैं पंजीकृत अंगदाता हूँ (फ़ॉर्म 7, THOTA 1994)।"))
            CheckLine(c.afterDeath.bodyDonation, { v -> after { it.copy(bodyDonation = v) } }, t("I want to donate my body for medical study.", "मैं चिकित्सा अध्ययन के लिए अपना शरीर दान करना चाहता/चाहती हूँ।"))
            if (c.afterDeath.bodyDonation) CareField(c.afterDeath.bodyDonationInstitution, { v -> after { it.copy(bodyDonationInstitution = v) } }, t("Medical college or organisation", "मेडिकल कॉलेज या संस्था"), singleLine = true)
            CareField(c.afterDeath.lastRites, { v -> after { it.copy(lastRites = v) } }, t("Wishes about last rites", "अंतिम संस्कार के बारे में इच्छाएँ"), minLines = 2)
        }
        IssueList(issues)
        Surface(color = Peach, shape = RoundedCornerShape(12.dp)) { Text(t("Once printed, this version cannot be changed in the app. To change anything, start a new version and print again.", "प्रिंट होने के बाद यह संस्करण ऐप में बदला नहीं जा सकता। कुछ भी बदलने के लिए नया संस्करण शुरू करें और फिर से प्रिंट करें।"), Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onSave(c) }, enabled = canEdit && c != record.content, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("amd-save")) { Text(t("Save", "सहेजें")) }
            Button(onClick = { onPrint(c) }, enabled = canEdit && AmdRules.blocking(issues).isEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("amd-generate")) { Text(t("Make the PDF to print and sign", "प्रिंट और हस्ताक्षर के लिए PDF बनाएँ")) }
        }
    }
}

@Composable private fun AmdDhrFields(index: Int, dhr: AmdDhr, count: Int, today: LocalDate, onChange: (AmdDhr) -> Unit, onRemove: () -> Unit, onMoveUp: () -> Unit) { val t = careText(); val n = index + 1
    Surface(color = Cream, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (index == 0) t("1. First representative", "1. पहले प्रतिनिधि") else t("$n. If the one before cannot be reached", "$n. अगर पिछले से संपर्क न हो"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (index > 0) IconButton(onClick = onMoveUp) { Icon(Icons.Default.ArrowUpward, t("Move up", "ऊपर करें")) }
            if (count > 1) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, t("Remove", "हटाएँ")) }
        }
        CareField(dhr.name, { onChange(dhr.copy(name = it)) }, t("Full name", "पूरा नाम"), singleLine = true)
        CareField(dhr.relation, { onChange(dhr.copy(relation = it)) }, t("How they are related to you", "वे आपके क्या लगते हैं"), singleLine = true)
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
    PlanOption(t("The custodian received a copy and acknowledged it", "संरक्षक को प्रति मिली और उन्होंने पावती दी"), null, value?.status == AmdCustodianStatus.ACKNOWLEDGED, "amd-custodian-ack") { onChange(current.copy(status = AmdCustodianStatus.ACKNOWLEDGED)) }
    PlanOption(t("Custodian pending (none appointed yet, or no reply)", "संरक्षक बाक़ी (अभी नियुक्त नहीं, या जवाब नहीं)"), null, value?.status == AmdCustodianStatus.PENDING, "amd-custodian-pending") { onChange(current.copy(status = AmdCustodianStatus.PENDING)) }
    if (value?.status == AmdCustodianStatus.ACKNOWLEDGED) {
        CareField(current.officer, { onChange(current.copy(officer = it)) }, t("Officer’s name and office", "अधिकारी का नाम और कार्यालय"), singleLine = true)
        PlanDateField(current.acknowledgedOn, t("Date acknowledged", "पावती की तारीख़"), { onChange(current.copy(acknowledgedOn = it)) }, latest = LocalDate.now())
    }
    if (value?.status == AmdCustodianStatus.PENDING) Text(t("Tip from the Vidhi toolkit: write to the head of your local body (Commissioner, Sarpanch or President), citing para 198.3.6 of the Supreme Court order of 24 January 2023, and ask them to name a custodian. Attach a copy.", "विधि टूलकिट की सलाह: अपने स्थानीय निकाय के प्रमुख (आयुक्त, सरपंच या अध्यक्ष) को पत्र लिखें, 24 जनवरी 2023 के सुप्रीम कोर्ट आदेश के पैरा 198.3.6 का हवाला दें, और संरक्षक नियुक्त करने को कहें। एक प्रति साथ लगाएँ।"), style = Small, color = DoctorCardSub)
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
        Text(t("Version $version is printed. It is not valid until signed and attested.", "संस्करण $version प्रिंट हो गया है। हस्ताक्षर और सत्यापन तक यह मान्य नहीं है।"), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("amd-status-generated"))
        Text("1. " + t("Print the PDF. Read it once more.", "PDF प्रिंट करें। एक बार फिर पढ़ें।"), style = MaterialTheme.typography.bodyMedium)
        Text("2. " + t("Sign (or give a thumb impression) in front of two adult witnesses. Better if they are not your representatives.", "दो वयस्क गवाहों के सामने हस्ताक्षर करें (या अँगूठा लगाएँ)। बेहतर है कि वे आपके प्रतिनिधि न हों।"), style = MaterialTheme.typography.bodyMedium)
        Text("3. " + t("Take it to a notary or a gazetted officer to attest and stamp.", "इसे नोटरी या राजपत्रित अधिकारी के पास सत्यापन और मुहर के लिए ले जाएँ।"), style = MaterialTheme.typography.bodyMedium)
        Text("4. " + t("Take a clear photo or scan of every page and add it here.", "हर पेज की साफ़ फ़ोटो या स्कैन लें और यहाँ जोड़ें।"), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onReprint, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-reprint")) { Text(t("Share the PDF again", "PDF फिर से साझा करें")) }
        w.pdfSha256?.let { Text(t("Printed PDF SHA-256:", "प्रिंट PDF का SHA-256:") + " " + it, style = Small, color = DoctorCardSub) }
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
        OutlinedButton(onClick = { pick.launch("*/*") }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-scan")) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text(if (e.scanSha256.isBlank()) t("Photo or scan of the signed, attested paper", "हस्ताक्षरित, सत्यापित काग़ज़ की फ़ोटो या स्कैन") else t("Added · change", "जुड़ गया · बदलें")) }
        Text(t("Kept on this phone only. A SHA-256 fingerprint is recorded so a doctor can check the copy is the same.", "केवल इसी फ़ोन पर रहता है। SHA-256 फ़िंगरप्रिंट दर्ज होता है ताकि डॉक्टर जाँच सकें कि प्रति वही है।"), style = Small, color = DoctorCardSub)
        if (e.scanSha256.isNotBlank()) Text(t("Scan SHA-256", "स्कैन SHA-256") + ": " + e.scanSha256, style = Small, color = DoctorCardSub, modifier = Modifier.testTag("amd-scan-sha"))
        if (scanError) Text(t("That file could not be read. Try another.", "वह फ़ाइल पढ़ी नहीं जा सकी। दूसरी आज़माएँ।"), color = RustText, style = Small)
        IssueList(issues)
        Button(onClick = { commit { CarePlanBook.executeAmd(it, w.id, e, custodian, actor, planNow(), today) } }, enabled = canEdit && AmdRules.blocking(issues).isEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("amd-execute")) { Text(t("Record as signed and attested", "हस्ताक्षरित और सत्यापित के रूप में दर्ज करें")) }
        Text(t("Need to change something? Start a new version. This printed copy must then not be signed.", "कुछ बदलना है? नया संस्करण शुरू करें। तब इस प्रिंट की गई प्रति पर हस्ताक्षर नहीं करने चाहिए।"), style = Small)
        TextButton(onClick = { commit { CarePlanBook.startAmd(it, w.patientId, actor, planNow()).first } }, enabled = canEdit, modifier = Modifier.testTag("amd-new-version")) { Text(t("Start a new version", "नया संस्करण शुरू करें")) }
    }
}

/** The signed version: proof, per-recipient copies with their own consent, custodian, optional ABHA link, change and cancel. */
@Composable private fun ExecutedCard(c: AmdRecord, store: CarePlanStore, personId: String, canEdit: Boolean, noWorking: Boolean, clinician: Boolean, actor: String, commit: ((CarePlanState) -> CarePlanState) -> Boolean) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current; val today = LocalDate.now(); val ex = c.execution; val version = c.version
    PlanCard {
        SectionTitle(t("Signed living will · version $version", "हस्ताक्षरित लिविंग विल · संस्करण $version"))
        Text(if (clinician) t("Check the paper copy against the scan and its SHA-256, or with the custodian. Doctors only read this here; nothing in the app starts or stops treatment.", "काग़ज़ की प्रति को स्कैन और उसके SHA-256 से, या संरक्षक से मिलाएँ। डॉक्टर इसे यहाँ केवल पढ़ते हैं; ऐप में कुछ भी इलाज शुरू या बंद नहीं करता।") else t("This is the current living will. Doctors can see that it is signed, witnessed and attested.", "यह मौजूदा लिविंग विल है। डॉक्टर देख सकते हैं कि यह हस्ताक्षरित, गवाहों के सामने और सत्यापित है।"), style = MaterialTheme.typography.bodyMedium)
        ex?.let { e ->
            Text(t("Signed", "हस्ताक्षर") + ": " + e.signedOn + (if (e.place.isNotBlank()) ", " + e.place else "") + if (e.thumbImpression) " · " + t("thumb impression", "अँगूठे का निशान") else "", style = MaterialTheme.typography.bodyMedium)
            Text(t("Witnesses", "गवाह") + ": " + e.witnesses.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium)
            Text(t("Attested by", "सत्यापित") + ": " + (e.attestor.kind?.let { AmdText.attestor(it).pick(hindi) }.orEmpty()) + " " + e.attestor.name + if (e.attestor.registrationNo.isNotBlank()) " (${e.attestor.registrationNo})" else "", style = MaterialTheme.typography.bodyMedium)
            Text(t("Custodian", "संरक्षक") + ": " + if (c.custodian?.status == AmdCustodianStatus.ACKNOWLEDGED) { val officer = c.custodian.officer; val on = c.custodian.acknowledgedOn; t("Acknowledged by $officer on $on", "$officer द्वारा $on को पावती") } else t("Custodian pending", "संरक्षक बाक़ी"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("amd-custodian-state"))
            Text(t("Scan SHA-256", "स्कैन SHA-256") + ": " + e.scanSha256, style = Small, color = DoctorCardSub)
            c.pdfSha256?.let { Text(t("Printed PDF SHA-256", "प्रिंट PDF SHA-256") + ": " + it, style = Small, color = DoctorCardSub) }
            var problem by remember(c.id) { mutableStateOf(false) }
            OutlinedButton(onClick = {
                val file = File(store.folder(personId), e.scanKey)
                problem = !file.isFile || AmdLifecycle.sha256(file.readBytes()) != e.scanSha256
                if (!problem) open(context, Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), e.scanType.ifBlank { "image/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-view-scan")) { Text(t("View the signed scan", "हस्ताक्षरित स्कैन देखें")) }
            if (problem) Text(t("This copy is missing or does not match its fingerprint. Do not rely on it; ask for the signed paper.", "यह कॉपी नहीं मिली या फ़िंगरप्रिंट से मेल नहीं खाती। इस पर भरोसा न करें; हस्ताक्षरित काग़ज़ माँगें।"), color = RustText, style = Small)
        }
        Text(t("Does not want", "नहीं चाहते") + ": " + AmdTreatment.entries.filter { c.content.treatments[it] == AmdChoice.REFUSE }.joinToString("; ") { AmdText.treatment(it).pick(hindi) }.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
        Text(t("Wants", "चाहते हैं") + ": " + AmdTreatment.entries.filter { c.content.treatments[it] == AmdChoice.ACCEPT }.joinToString("; ") { AmdText.treatment(it).pick(hindi) }.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
        Text(t("Representatives, in order", "प्रतिनिधि, क्रम से") + ": " + c.content.dhrs.filter { it.name.isNotBlank() }.mapIndexed { i, d -> "${i + 1}. ${d.name} (${d.relation}) ${d.phone}" }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        if (!clinician) {
            SectionTitle(t("Copies", "प्रतियाँ"))
            Text(t("Give a copy to each person below. Ask each one first: their consent to receive it is recorded separately.", "नीचे हर व्यक्ति को एक प्रति दें। पहले हर एक से पूछें: प्रति लेने की उनकी सहमति अलग से दर्ज होती है।"), style = Small)
            c.copies.forEach { copy -> Surface(color = Cream, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("amd-copy-${copy.kind.name.lowercase()}")) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(copy.name + " · " + AmdText.copyKind(copy.kind).pick(hindi), fontWeight = FontWeight.SemiBold)
                CheckLine(copy.consent, { v -> if (canEdit) commit { CarePlanBook.amdCopy(it, c.id, copy.id, v, null, null, actor, planNow(), today) } }, if (copy.kind == AmdRecipientKind.CUSTODIAN) t("The patient agrees to send a copy to the custodian.", "मरीज़ संरक्षक को प्रति भेजने के लिए सहमत है।") else t("The patient agrees to share it, and this person agrees to receive and keep it.", "मरीज़ इसे साझा करने के लिए, और यह व्यक्ति इसे लेने और रखने के लिए सहमत है।"))
                if (copy.consent) {
                    val on = copy.givenOn
                    CheckLine(copy.given, { v -> if (canEdit) commit { CarePlanBook.amdCopy(it, c.id, copy.id, null, v, copy.method ?: AmdCopyMethod.IN_PERSON, actor, planNow(), today) } }, if (copy.given) t("Copy given on $on", "प्रति $on को दी गई") else t("Copy given", "प्रति दी गई"))
                    ChipGroup(copy.method, AmdCopyMethod.entries, AmdText::copyMethod, "amd-copy-method-${copy.id}") { v -> if (canEdit && v != null) commit { CarePlanBook.amdCopy(it, c.id, copy.id, null, null, v, actor, planNow(), today) } }
                } else Text(t("Consent is needed before this copy can be marked as given.", "प्रति दी गई चिह्नित करने से पहले सहमति ज़रूरी है।"), style = Small, color = DoctorCardSub)
            } } }
            if (c.custodian?.status != AmdCustodianStatus.ACKNOWLEDGED) {
                var custodian by remember(c.id) { mutableStateOf(c.custodian) }
                CustodianFields(custodian, c.content.custodianBody) { custodian = it }
                val problem = custodian?.let(AmdRules::custodianIssue)
                OutlinedButton(onClick = { custodian?.let { v -> commit { CarePlanBook.amdCustodian(it, c.id, v, actor, planNow()) } } }, enabled = canEdit && custodian != null && problem == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-custodian-save")) { Text(t("Save custodian", "संरक्षक सहेजें")) }
            }
            InfoDisclosure(t("Link to ABHA health records (optional)", "ABHA स्वास्थ्य रिकॉर्ड से जोड़ें (वैकल्पिक)")) {
                var address by rememberSaveable(c.id) { mutableStateOf(c.abha?.address.orEmpty()) }; var consent by rememberSaveable(c.id) { mutableStateOf(c.abha?.consent == true) }
                Text(t("The Supreme Court allows adding the living will to digital health records. Sahara only notes the ABHA address here; upload the scan in your ABHA app yourself.", "सुप्रीम कोर्ट लिविंग विल को डिजिटल स्वास्थ्य रिकॉर्ड में जोड़ने की अनुमति देता है। सहारा यहाँ केवल ABHA पता नोट करता है; स्कैन अपने ABHA ऐप में ख़ुद अपलोड करें।"), style = Small)
                CareField(address, { address = it }, t("ABHA address or number", "ABHA पता या नंबर"), singleLine = true, placeholder = "name@abdm")
                CheckLine(consent, { consent = it }, t("The patient agrees to link the living will with their ABHA records.", "मरीज़ लिविंग विल को अपने ABHA रिकॉर्ड से जोड़ने के लिए सहमत है।"))
                val problem = AmdRules.abhaIssue(address, consent)
                problem?.let { Text(it.text(hindi), color = RustText, style = Small) }
                OutlinedButton(onClick = { commit { CarePlanBook.amdAbha(it, c.id, address, consent, actor, planNow(), today) } }, enabled = canEdit && problem == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-abha-save")) { Text(t("Save ABHA link", "ABHA लिंक सहेजें")) }
                c.abha?.let { a -> val on = a.linkedOn; Text(t("Linked on $on", "$on को जोड़ा गया"), style = Small, modifier = Modifier.testTag("amd-abha-linked")) }
            }
            SectionTitle(t("Change it", "इसे बदलें"))
            if (noWorking) {
                Text(t("Changes make a new version that must be printed, signed and attested again. Version $version stays current until then.", "बदलाव से नया संस्करण बनता है जिसे फिर से प्रिंट, हस्ताक्षर और सत्यापित करना होगा। तब तक संस्करण $version मौजूदा रहेगा।"), style = Small)
                OutlinedButton(onClick = { commit { CarePlanBook.startAmd(it, personId, actor, planNow()).first } }, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("amd-new-version")) { Text(t("Start a new version", "नया संस्करण शुरू करें")) }
            } else Text(t("A new version is already in progress below.", "एक नया संस्करण नीचे पहले से चल रहा है।"), style = Small)
            SectionTitle(t("Cancel it", "इसे रद्द करें"))
            AmdCancelForm(canEdit) { on, written, reason -> commit { CarePlanBook.revokeAmd(it, personId, on, written, reason, actor, planNow(), today) } }
        }
    }
}

@Composable private fun AmdCancelForm(canEdit: Boolean, onRevoke: (String, Boolean, String) -> Unit) { val t = careText(); val today = LocalDate.now()
    var open by rememberSaveable { mutableStateOf(false) }; var on by rememberSaveable { mutableStateOf("") }; var written by rememberSaveable { mutableStateOf(false) }; var reason by rememberSaveable { mutableStateOf("") }
    Text(t("A living will is cancelled in writing: the person signs a short note saying they cancel it, the same way it was made. Then give the note to everyone who has a copy.", "लिविंग विल लिखित रूप में रद्द होती है: व्यक्ति एक छोटे नोट पर हस्ताक्षर करता है कि वह इसे रद्द कर रहा है, उसी तरह जैसे बनाई गई थी। फिर वह नोट उन सबको दें जिनके पास प्रति है।"), style = Small)
    if (!open) TextButton(onClick = { open = true }, enabled = canEdit, modifier = Modifier.testTag("amd-cancel")) { Text(t("Record a written cancellation", "लिखित रद्दीकरण दर्ज करें"), color = RustText) }
    else Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanDateField(on, t("Date of the written cancellation", "लिखित रद्दीकरण की तारीख़"), { on = it }, latest = today)
        CheckLine(written, { written = it }, t("The person signed a written cancellation.", "व्यक्ति ने लिखित रद्दीकरण पर हस्ताक्षर किए हैं।"))
        CareField(reason, { reason = it }, t("Reason (optional)", "कारण (वैकल्पिक)"))
        val issues = AmdRules.revocationIssues(on, written, today)
        IssueList(issues)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { open = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Keep it", "रहने दें")) }
            Button(onClick = { open = false; onRevoke(on, written, reason.trim()) }, enabled = issues.isEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("amd-cancel-confirm")) { Text(t("Cancel the living will", "लिविंग विल रद्द करें")) }
        }
    } }
}
