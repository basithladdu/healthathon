package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Goals of care has one home (Dr Sujay, 22 Nov 2026): the doctor records, signs and opens it in the patient's Goals of care section.
 * Everywhere else shows its history read-only. Patients and families never fill in or sign the doctor's form; they read the signed
 * note and can ask their doctor for the conversation (D15).
 *
 * The request is an ordinary "Ask to talk" care entry (CareEntry type "conversation", category [GocRequestOps.CATEGORY]), so it uses the
 * path that already reaches the doctor: it syncs with the person's records (send-later queue included), the invited clinician reads it,
 * and it shows in the patient's requests list (doctor side: Conversations → requests, filter "Conversations") with Requested / Picked up / Completed.
 * No new table is needed.
 */
internal object GocRequestOps {
    /** Marks the request as one for the goals-of-care conversation (the entry's free "category" field). */
    const val CATEGORY = "goals-of-care"
    const val TITLE = "Goals of care conversation"

    fun entry(sender: String): CareEntry = CareEntry(type = "conversation", detail = "Asked from the Goals of care page in the patient's app.", assignee = "Doctor", sender = sender.take(160), category = CATEGORY)

    /** This person's goals-of-care requests that are still open (requested or picked up), newest first. */
    fun open(items: List<CareItem>, personId: String): List<Pair<CareItem, CareEntry>> = items.asSequence()
        .filter { it.personId == personId }
        .mapNotNull { item -> CareEntryCodec.decode(item.detail)?.takeIf { it.type == "conversation" && it.category == CATEGORY && it.status in setOf("open", "accepted") }?.let { item to it } }
        .sortedByDescending { it.first.created }.toList()

    /** Open requests a signed note has not answered yet: made after the latest signed goals-of-care note. */
    fun unanswered(open: List<Pair<CareItem, CareEntry>>, latestSignedAt: Long?): List<Pair<CareItem, CareEntry>> =
        open.filter { latestSignedAt == null || it.first.created > latestSignedAt }
}

/** Milliseconds from an ISO instant, an offset time or a plain date; null when it is none of those. */
internal fun gocMillis(at: String?): Long? {
    val s = at?.trim().orEmpty(); if (s.isBlank()) return null
    return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDate.parse(s.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
}

@Composable private fun gocDay(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(careDateFormat("d MMM yyyy", careHindi()))

/**
 * Patient and family side: where the doctor's goals-of-care note stands, read-only, and the way to ask for the conversation.
 * Shown on Care planning and in place of the doctor's talk on the patient's Goals of care / Conversations tab.
 */
@Composable internal fun PatientGocStatus(vm: CareModel, ectprs: List<EctprRecord>, personId: String, modifier: Modifier = Modifier) {
    val t = careText(); val hindi = careHindi()
    val notes = remember(ectprs, personId) { DoctorGocDiscussions.list(ectprs, personId) }
    val signed = notes.firstOrNull { it.signed }
    val writing = notes.firstOrNull { !it.signed }?.takeIf { w -> signed == null || w.record.version > signed.record.version }
    var reading by remember(personId) { mutableStateOf<DoctorGocDiscussion?>(null) }
    val items by vm.items.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val asked = remember(items, personId) { GocRequestOps.open(items, personId).firstOrNull() }
    var result by remember(personId) { mutableStateOf("") }
    val savedTap = rememberSaharaHaptic()
    fun ask() {
        if (personId.isBlank() || busy || asked != null || vm.selected.value != personId) return
        runCatching { vm.saveItem("task", GocRequestOps.TITLE, CareEntryCodec.encode(GocRequestOps.entry(vm.cloud?.session?.value?.email.orEmpty().ifBlank { vm.viewRole.value })), done = { result = "sent"; savedTap() }) }
            .onFailure { result = "failed" }
    }
    Column(modifier.fillMaxWidth().testTag("goc-family-status"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Your doctor writes and signs this note after talking with you. You can read it here; only your doctor can change it.",
            "आपसे बात करने के बाद आपके डॉक्टर यह नोट लिखते और साइन करते हैं। आप इसे यहाँ पढ़ सकते हैं; इसे सिर्फ़ आपके डॉक्टर बदल सकते हैं।"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk)
        when {
            signed != null -> FamilyRegisterRow(t("Version ${signed.record.version}", "संस्करण ${signed.record.version}"), meta = signed.at.take(10),
                pill = t("Signed", "साइन हुआ") to FamilyTone.GOOD, modifier = Modifier.testTag("goc-family-signed"))
            writing == null -> Text(t("No signed goals-of-care note on this phone yet.", "इस फ़ोन पर अभी देखभाल के लक्ष्यों का कोई साइन किया हुआ नोट नहीं है।"), fontSize = 15.sp, color = DoctorCardSub, modifier = Modifier.testTag("goc-family-none"))
            else -> {}
        }
        if (writing != null) FamilyRegisterRow(t("Version ${writing.record.version}", "संस्करण ${writing.record.version}"), meta = t("Your doctor is still writing this one.", "आपके डॉक्टर अभी इसे लिख रहे हैं।"),
            pill = t("Not signed yet", "अभी साइन नहीं") to FamilyTone.WAIT, modifier = Modifier.testTag("goc-family-writing"))
        if (signed != null) OutlinedButton(onClick = { reading = signed }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-family-read")) {
            Text(t("Read the signed note", "साइन किया हुआ नोट पढ़ें"))
        }
        PlanReveal(result == "failed") { Text(t("Couldn't save that. Please try again.", "यह सहेजा नहीं जा सका। फिर से कोशिश करें।"), fontSize = 14.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        // Asking turns into "You asked" with a crossfade, instead of the button vanishing.
        SaharaStateSwitch(asked != null, Modifier.fillMaxWidth(), label = "goc-ask") { hasAsked -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasAsked) asked?.let { a -> FamilyRegisterRow(t("You asked for this conversation", "आपने यह बातचीत माँगी है"), meta = gocDay(a.first.created),
            pill = a.second.statusLabel(hindi) to (if (a.second.status == "accepted") FamilyTone.GOOD else FamilyTone.WAIT), modifier = Modifier.testTag("goc-ask-sent")) }
        // What the conversation is, in plain words, beside the button (clinical team, 24 Nov 2026).
        if (!hasAsked) Text(t("A calm talk with your doctor about what matters most to you, so your care follows your wishes. You can bring family.",
            "अपने डॉक्टर से एक शांत बातचीत, कि आपके लिए सबसे ज़रूरी क्या है, ताकि आपकी देखभाल आपकी इच्छा के अनुसार हो। आप परिवार को साथ ला सकते हैं।"),
            fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub, modifier = Modifier.testTag("goc-ask-explained"))
        if (!hasAsked) Button(onClick = ::ask, enabled = canEdit && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-ask-doctor")) {
            Icon(Icons.Default.Forum, null); Spacer(Modifier.width(8.dp)); Text(t("Ask my doctor for a goals of care conversation", "अपने डॉक्टर से देखभाल के लक्ष्यों पर बातचीत माँगें"))
        }
        if (!hasAsked && !canEdit) Text(t("Someone in the family who can make changes can send this.", "परिवार में जो बदलाव कर सकते हैं, वे इसे भेज सकते हैं।"), fontSize = 14.sp, color = DoctorCardSub)
        } }
    }
    reading?.let { d -> GocSignedNoteReader(d) { reading = null } }
}

/** The signed note, read-only, in the legal form's layout. */
@Composable internal fun GocSignedNoteReader(d: DoctorGocDiscussion, onClose: () -> Unit) { val t = careText()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Cream, modifier = Modifier.fillMaxSize().testTag("goc-family-reader")) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("goc-family-reader-close")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
                    Text(t("Goals-of-care note", "देखभाल के लक्ष्यों का नोट"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                }
                Text(t("Version ${d.record.version}", "संस्करण ${d.record.version}") + " · " + d.at.take(10) + " · " + doctorGocStatus(d), fontSize = 14.sp, color = DoctorCardSub)
                Text(t("Read only. Only your doctor can change this note.", "सिर्फ़ पढ़ने के लिए। इस नोट को सिर्फ़ आपके डॉक्टर बदल सकते हैं।"), fontSize = 14.sp, color = DoctorCardSub)
                GocFormView(d.sections())
                if (d.note.isNotBlank()) PlanCard {
                    Text(t("Doctor's note", "डॉक्टर का नोट"), fontWeight = FontWeight.SemiBold)
                    Text(d.note, fontSize = 15.sp, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Doctor side, everywhere except the Goals of care section itself: the versions as a plain register (version, status, date),
 * each readable, and one way to the Goals of care section.
 */
@Composable internal fun DoctorGocHistory(notes: List<DoctorGocDiscussion>, onOpenGoalsOfCare: (() -> Unit)?, modifier: Modifier = Modifier) { val t = careText()
    var reading by remember { mutableStateOf<DoctorGocDiscussion?>(null) }
    Column(modifier.fillMaxWidth().testTag("goc-history"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Recorded, signed and opened in the Goals of care section. Every version stays here as history.", "देखभाल के लक्ष्य वाले हिस्से में रिकॉर्ड, साइन और खोले जाते हैं। हर संस्करण यहाँ इतिहास में रहता है।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
        if (notes.isEmpty()) Text(t("No goals-of-care note yet.", "अभी देखभाल के लक्ष्यों का कोई नोट नहीं।"), fontSize = 15.sp, color = DoctorCardInk, modifier = Modifier.testTag("goc-history-empty"))
        notes.forEachIndexed { i, n ->
            if (i > 0) HorizontalDivider(color = DoctorHairline)
            FamilyRegisterRow(t("Version ${n.record.version}", "संस्करण ${n.record.version}"), meta = n.at.take(10),
                pill = (if (n.signed) t("Signed and locked", "साइन होकर बंद") else t("Not signed yet", "अभी साइन नहीं")) to (if (n.signed) FamilyTone.GOOD else FamilyTone.WAIT),
                modifier = Modifier.testTag("goc-history-${n.record.version}")) {
                TextButton(onClick = { reading = n }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Read", "पढ़ें"), color = RustText) }
            }
        }
        if (onOpenGoalsOfCare != null) Button(onClick = onOpenGoalsOfCare, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-history-open")) {
            Text(t("Open Goals of care", "देखभाल के लक्ष्य खोलें"))
        }
    }
    reading?.let { d -> GocSignedNoteReader(d) { reading = null } }
}

/** On the doctor's Goals of care section: the patient or family asked for the conversation (D15), until a signed note answers it. */
@Composable internal fun DoctorGocRequestBanner(vm: CareModel, personId: String, latestSignedAt: Long?) { val t = careText()
    val items by vm.items.collectAsStateWithLifecycle()
    val latest = remember(items, personId, latestSignedAt) { GocRequestOps.unanswered(GocRequestOps.open(items, personId), latestSignedAt).firstOrNull()?.first } ?: return
    Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorStatusWarn.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("goc-request-banner")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = DoctorStatusWarn); Spacer(Modifier.width(10.dp))
            Text(t("The patient or family asked for a goals of care conversation on ${gocDay(latest.created)}.", "मरीज़ या परिवार ने ${gocDay(latest.created)} को देखभाल के लक्ष्यों पर बातचीत माँगी।"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The same request, for the doctor's patient page (hub) and the plan home: a calm notice with one way to the Goals of care section,
 * shown while a request is open and no signed goals-of-care note has answered it. [ectprs] is the plan state when the caller already
 * holds it; otherwise it is read from this phone's plan store. Draws nothing when there is no unanswered request.
 */
@Composable internal fun DoctorGocRequestNotice(vm: CareModel, personId: String, onOpen: (() -> Unit)?, modifier: Modifier = Modifier, ectprs: List<EctprRecord>? = null) {
    val t = careText(); val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val stored = remember(vm.storageNamespace, personId, ectprs == null, items) {
        if (ectprs != null || personId.isBlank()) emptyList() else runCatching { CarePlanStore(context.applicationContext, vm.storageNamespace).load(personId).ectprs }.getOrDefault(emptyList())
    }
    val records = ectprs ?: stored
    val latest = remember(items, records, personId) {
        val signedAt = DoctorGocDiscussions.list(records, personId).filter { it.signed }.mapNotNull { gocMillis(it.at) }.maxOrNull()
        GocRequestOps.unanswered(GocRequestOps.open(items, personId), signedAt).firstOrNull()?.first
    } ?: return
    Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorStatusWarn.copy(alpha = .3f)),
        modifier = modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier).testTag("goc-request-notice")) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = DoctorStatusWarn); Spacer(Modifier.width(10.dp))
                Text(t("The patient or family asked for a goals of care conversation on ${gocDay(latest.created)}.", "मरीज़ या परिवार ने ${gocDay(latest.created)} को देखभाल के लक्ष्यों पर बातचीत माँगी।"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk, modifier = Modifier.weight(1f))
            }
            if (onOpen != null) TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("goc-request-notice-open")) { Text(t("Open Goals of care", "देखभाल के लक्ष्य खोलें"), color = RustText) }
        }
    }
}
