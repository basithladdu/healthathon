package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject

/**
 * Research consent (24 Nov meeting, DPDP Act 2023): a separate, explicit opt-in for the patient's records, with name and
 * contact details removed, to be used for medical research by their care team. Off until the person switches it on, and
 * it can be withdrawn at any time. It is not tied to sending feedback to improve the app.
 *
 * Each change is kept on the patient's shared record ([ResearchConsentCodec], one "journal" entry per change, newest
 * wins), so the patient's phone, family phones and the care team all see the same choice. The copy on this phone
 * ([ResearchConsentStore]) is only a cache and a fallback for choices made before the shared record kept them.
 */
internal data class ResearchConsentChoice(
    val allowed: Boolean,
    /** Epoch ms of the change. */
    val at: Long,
    /** Who changed it: "Patient" or "Family / carer" (blank for an older choice kept only on this phone). */
    val by: String = "",
    /** How the consent text was read before allowing (ConsentReadAloud.kt); null when switched off or for older choices. */
    val reading: ConsentReading? = null,
)

/**
 * The choice travels in the shared care record as a "journal" entry (an allowed server kind) whose detail carries [PREFIX],
 * like the patient profile. Every list that shows journal notes must skip entries where [isStructured] is true.
 */
internal object ResearchConsentCodec {
    const val PREFIX = "sahara:research-consent:v1\n"
    const val TITLE = "Research consent"
    fun isStructured(text: String) = text.startsWith(PREFIX)
    fun encode(c: ResearchConsentChoice): String = PREFIX + JSONObject().put("allowed", c.allowed).put("at", c.at).put("by", c.by)
        .put("readAloud", c.reading?.let(ConsentReadingRules::encode) ?: JSONObject.NULL).toString()
    fun decode(text: String): ResearchConsentChoice? = if (!isStructured(text)) null else runCatching {
        val o = JSONObject(text.removePrefix(PREFIX))
        val allowed = o.get("allowed") as? Boolean ?: return null
        val at = o.getLong("at").takeIf { it > 0 } ?: return null
        ResearchConsentChoice(allowed, at, o.optString("by").take(40), if (allowed) ConsentReadingRules.decode(o.optJSONObject("readAloud")) else null)
    }.getOrNull()
    fun item(personId: String, c: ResearchConsentChoice, id: String = newId()) = CareItem(id = id, personId = personId, kind = "journal", title = TITLE, detail = encode(c), created = c.at)
    /** The newest readable choice for [personId] on the shared record, or null when none was ever saved there. */
    fun latest(items: List<CareItem>, personId: String): ResearchConsentChoice? =
        items.filter { it.personId == personId && it.kind == "journal" && isStructured(it.detail) }
            .mapNotNull { item -> decode(item.detail)?.let { it to item } }
            .maxWithOrNull(compareBy<Pair<ResearchConsentChoice, CareItem>> { it.first.at }.thenBy { it.second.created }.thenBy { it.second.id })?.first
    /** The shared record wins; the phone's own copy only when the shared record has nothing yet. */
    fun current(shared: ResearchConsentChoice?, localAllowed: Boolean, localAt: Long): ResearchConsentChoice? =
        shared ?: if (localAt > 0L) ResearchConsentChoice(localAllowed, localAt) else null
}

/** This phone's cache of the choice (see [ResearchConsentCodec] for the shared record). */
internal object ResearchConsentStore {
    val revision = MutableStateFlow(0)
    private fun preferences(context: Context, namespace: String) = context.applicationContext.getSharedPreferences("sahara-research-consent-${namespace.ifBlank { "guest" }}", 0)
    /** False unless the person chose to allow it. */
    fun isAllowed(context: Context, namespace: String, personId: String): Boolean = personId.isNotBlank() && preferences(context, namespace).getBoolean("on:$personId", false)
    /** When the choice was last changed (epoch ms), or 0 when it was never changed. */
    fun changedAt(context: Context, namespace: String, personId: String): Long = if (personId.isBlank()) 0L else preferences(context, namespace).getLong("at:$personId", 0L)
    fun set(context: Context, namespace: String, personId: String, allowed: Boolean, now: Long = System.currentTimeMillis()) {
        if (personId.isBlank()) return
        preferences(context, namespace).edit().putBoolean("on:$personId", allowed).putLong("at:$personId", now).commit(); revision.value++
    }
}

/** The plain lines of the data-choices summary (also what "Share my data choices" sends). */
internal object ResearchConsentRules {
    fun summary(personName: String, allowed: Boolean, changedOn: String?, t: (String, String) -> String, readLine: String? = null): String {
        val status = if (allowed) t("Medical research: allowed (name and contact details removed)", "चिकित्सा शोध: अनुमति है (नाम और संपर्क जानकारी हटाकर)")
            else t("Medical research: not allowed", "चिकित्सा शोध: अनुमति नहीं है")
        return listOfNotNull(
            t("Sahara data choices for $personName", "$personName के लिए Sahara डेटा विकल्प"),
            status,
            changedOn?.let { t("Last changed: $it", "आख़िरी बार बदला: $it") },
            readLine?.takeIf { allowed && it.isNotBlank() },
            t("Research use is separate from sending feedback to improve the app.", "शोध के लिए इस्तेमाल, ऐप सुधारने के लिए राय भेजने से अलग है।"),
            t("Records stay with the care team either way.", "दोनों हालात में रिकॉर्ड देखभाल टीम के पास रहते हैं।"),
        ).joinToString("\n")
    }
}

/** Settings > Privacy and data: the research opt-in, a short summary of the person's data choices, and the privacy policy. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable internal fun PrivacyAndDataScreen(vm: CareModel, person: Person, canChange: Boolean, onDismiss: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    val namespace = vm.storageNamespace
    val revision by ResearchConsentStore.revision.collectAsStateWithLifecycle()
    val items by vm.allItems.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val owner by vm.isOwner.collectAsStateWithLifecycle()
    val contacts by vm.careContacts.collectAsStateWithLifecycle()
    // The shared record (synced with the patient's care record) first; this phone's copy only for older choices.
    val shared = remember(items, person.id) { ResearchConsentCodec.latest(items, person.id) }
    val choice = remember(shared, namespace, person.id, revision) {
        ResearchConsentCodec.current(shared, ResearchConsentStore.isAllowed(context, namespace, person.id), ResearchConsentStore.changedAt(context, namespace, person.id))
    }
    val allowed = choice?.allowed == true
    val changed = choice?.at ?: 0L
    // Saved on the shared record, so only someone who can edit this person's record, with this person open, can change it.
    val canSave = canEdit && selected == person.id
    val changeable = canChange && canSave
    var confirm by rememberSaveable { mutableStateOf(false) }
    val haptic = rememberSaharaHaptic()
    val tick = rememberSaharaTick()
    var reading by remember(person.id) { mutableStateOf<ConsentReading?>(null) }; var readingRound by rememberSaveable(person.id) { mutableIntStateOf(0) }
    val readers = remember(contacts, person.id) { contacts.filter { it.personId == person.id }.map { ConsentPerson(it.name, it.relationship) } }
    fun save(on: Boolean, heard: ConsentReading?) {
        val now = System.currentTimeMillis()
        val made = ResearchConsentChoice(on, now, if (owner) "Patient" else "Family / carer", heard?.takeIf { on }?.let(ConsentReadingRules::clean))
        ResearchConsentStore.set(context, namespace, person.id, on, now)
        vm.saveItem("journal", ResearchConsentCodec.TITLE, ResearchConsentCodec.encode(made), done = {
            haptic(); vm.message.value = if (on) t("Research use is on. Thank you.", "शोध के लिए इस्तेमाल चालू है। धन्यवाद।") else t("Research use is off.", "शोध के लिए इस्तेमाल बंद है।")
        })
    }
    val changedOn = remember(changed) { if (changed > 0L) date(changed) else null }
    val lang = careLanguage()
    // Built once per change (and language), not on every recomposition; the share button sends the same text.
    val readLine = remember(choice?.reading, lang) { choice?.reading?.let { ConsentReadingRules.summary(it, t) } }
    val summary = remember(person.name, allowed, changedOn, lang, readLine) { ResearchConsentRules.summary(person.name, allowed, changedOn, t, readLine) }
    SaharaScreenSheet(onDismissRequest = onDismiss, containerColor = SaharaColors.Ground) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp).testTag("privacy-data"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SaharaBackRow(onDismiss, title = t("Privacy and data", "निजता और डेटा"), tag = "privacy-data-back")
            // The research opt-in: its own card, off by default.
            Surface(shape = RoundedCornerShape(SaharaRadius.Lg), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = SaharaTone.Indigo.solid, contentColor = Color.White, modifier = Modifier.size(36.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Science, null, Modifier.size(20.dp)) } }
                        Spacer(Modifier.width(12.dp))
                        Text(t("Medical research", "चिकित्सा शोध"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = DoctorCardInk)
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Allow my data, with my name and contact details removed, to be used for medical research by my care team", "मेरा डेटा, मेरा नाम और संपर्क जानकारी हटाकर, मेरी देखभाल टीम के चिकित्सा शोध में इस्तेमाल किया जा सकता है"),
                            Modifier.weight(1f), fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                        Spacer(Modifier.width(12.dp))
                        SaharaSwitch(checked = allowed, enabled = changeable, onCheckedChange = { on ->
                            // A light tick when the question opens; the confirming buzz only when the choice is really saved.
                            if (on) { tick(); reading = null; readingRound++; confirm = true }
                            else save(false, null)
                        }, modifier = Modifier.testTag("privacy-research-switch").semantics {
                            contentDescription = t("Medical research", "चिकित्सा शोध")
                            stateDescription = if (allowed) t("Allowed", "अनुमति है") else t("Not allowed", "अनुमति नहीं है")
                        })
                    }
                    Text(t("Off unless you switch it on. You can switch it off at any time, and your care does not change either way.", "जब तक आप चालू न करें, यह बंद रहता है। आप इसे कभी भी बंद कर सकते हैं, और दोनों हालात में आपकी देखभाल नहीं बदलती।"),
                        fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub)
                    if (!canChange) Text(t("Only the patient or family can change this.", "इसे सिर्फ़ मरीज़ या परिवार बदल सकते हैं।"), fontSize = 14.sp, color = DoctorCardSub)
                    else if (!canSave) Text(t("Only someone who can edit this care record can change this.", "इसे सिर्फ़ वही बदल सकते हैं जो इस देखभाल रिकॉर्ड में बदलाव कर सकते हैं।"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.testTag("privacy-research-no-edit"))
                }
            }
            // The data-choices summary, which can be shared as text.
            Surface(shape = RoundedCornerShape(SaharaRadius.Lg), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Your data choices", "आपके डेटा विकल्प"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = DoctorCardInk)
                    Text(summary, Modifier.testTag("privacy-data-summary"), fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardInk)
                    TextButton(onClick = {
                        open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, summary), t("Share my data choices", "मेरे डेटा विकल्प भेजें")))
                    }, modifier = Modifier.heightIn(min = 48.dp).testTag("privacy-data-share")) {
                        Icon(Icons.Default.Share, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(8.dp))
                        Text(t("Share my data choices", "मेरे डेटा विकल्प भेजें"), color = RustText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
            SaharaListRow(t("Privacy policy", "निजता नीति"), Modifier.testTag("privacy-data-policy"), icon = Icons.Default.Policy, tone = SaharaTone.Indigo,
                onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) })
        }
    }
    // The consent text is read to the person first (clinical note point 11), like every other confirmation in the app.
    val consentText = t("Allow my data, with my name and contact details removed, to be used for medical research by my care team", "मेरा डेटा, मेरा नाम और संपर्क जानकारी हटाकर, मेरी देखभाल टीम के चिकित्सा शोध में इस्तेमाल किया जा सकता है") + "\n" +
        t("Your name and contact details are removed first. You can switch this off at any time in Privacy and data.", "पहले आपका नाम और संपर्क जानकारी हटा दी जाती है। आप इसे कभी भी 'निजता और डेटा' में बंद कर सकते हैं।")
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Paper, modifier = Modifier.saharaDialogEnter().testTag("privacy-research-confirm"),
        title = { Text(t("Allow research use?", "शोध के लिए इस्तेमाल की अनुमति दें?"), fontSize = 22.sp, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Your name and contact details are removed first. You can switch this off at any time in Privacy and data.", "पहले आपका नाम और संपर्क जानकारी हटा दी जाती है। आप इसे कभी भी 'निजता और डेटा' में बंद कर सकते हैं।"), fontSize = 17.sp, lineHeight = 24.sp)
            ConsentReadAloudStep(consentText, "research-${person.id}-$readingRound", if (owner) person.name else "", readers, onChange = { reading = it }, modifier = Modifier.testTag("privacy-research-read-aloud"))
        } },
        confirmButton = { Button(onClick = { val heard = reading ?: return@Button; confirm = false; save(true, heard) }, enabled = reading != null,
            modifier = Modifier.heightIn(min = 48.dp).testTag("privacy-research-allow")) { Text(t("Allow", "अनुमति दें"), fontSize = 17.sp, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("privacy-research-not-now")) { Text(t("Not now", "अभी नहीं"), fontSize = 17.sp) } })
}
