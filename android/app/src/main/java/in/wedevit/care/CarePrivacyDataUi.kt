package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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

/**
 * More > Privacy and data, a pushed page (back arrow, bottom bar hidden): the research opt-in as one switch row, the person's data
 * choices with Share, and the privacy policy, which opens inside the app ([PrivacyPolicyPage]).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable internal fun PrivacyAndDataPage(vm: CareModel, person: Person, canChange: Boolean, onBack: () -> Unit) {
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
    // The policy replaces this page while it is open (its own back arrow and system back return here).
    var policy by rememberSaveable { mutableStateOf(false) }
    if (policy) PrivacyPolicyPage(onBack = { policy = false })
    else SaharaPage(t("Privacy and data", "निजता और डेटा"), onBack = onBack, backTag = "privacy-data-back", listModifier = Modifier.testTag("privacy-data")) {
        // The research opt-in: one switch row, off by default. The whole row toggles; switching on asks first (read aloud).
        item(key = "research", contentType = "switch") { Column(Modifier.fillMaxWidth()) {
            val state = if (allowed) t("Allowed", "अनुमति है") else t("Not allowed", "अनुमति नहीं है")
            SaharaRow(t("Medical research", "चिकित्सा शोध"),
                Modifier.toggleable(allowed, enabled = changeable, role = Role.Switch, onValueChange = { on ->
                    // A light tick when the question opens; the confirming buzz only when the choice is really saved.
                    if (on) { tick(); reading = null; readingRound++; confirm = true }
                    else save(false, null)
                }).semantics { stateDescription = state },
                supporting = t("Allow my data, with my name and contact details removed, to be used for medical research by my care team", "मेरा डेटा, मेरा नाम और संपर्क जानकारी हटाकर, मेरी देखभाल टीम के चिकित्सा शोध में इस्तेमाल किया जा सकता है"),
                supportingMaxLines = 4, icon = Icons.Default.Science, enabled = changeable, chevron = false,
                trailing = { Switch(checked = allowed, onCheckedChange = null, enabled = changeable, modifier = Modifier.testTag("privacy-research-switch")) })
            if (!canChange) Text(t("Only the patient or family can change this.", "इसे सिर्फ़ मरीज़ या परिवार बदल सकते हैं।"), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (!canSave) Text(t("Only someone who can edit this care record can change this.", "इसे सिर्फ़ वही बदल सकते हैं जो इस देखभाल रिकॉर्ड में बदलाव कर सकते हैं।"), Modifier.padding(horizontal = 16.dp).testTag("privacy-research-no-edit"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        // The data-choices summary as plain text, and the row that shares it.
        item(key = "choices-label", contentType = "label") { SaharaGroupLabel(t("Your data choices", "आपके डेटा विकल्प")) }
        item(key = "choices", contentType = "text") {
            Text(summary, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("privacy-data-summary"), fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        item(key = "share", contentType = "row") {
            SaharaRow(t("Share my data choices", "मेरे डेटा विकल्प भेजें"), Modifier.testTag("privacy-data-share"), icon = Icons.Default.Share, chevron = false, onClick = {
                open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, summary), t("Share my data choices", "मेरे डेटा विकल्प भेजें")))
            })
        }
        item(key = "divider", contentType = "divider") { androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        // The policy opens inside the app; only its last line links to the website.
        item(key = "policy", contentType = "row") {
            SaharaRow(t("Privacy policy", "निजता नीति"), Modifier.testTag("privacy-data-policy"), icon = Icons.Default.Description, onClick = { policy = true })
        }
    }
    // The consent text is read to the person first (clinical note point 11), like every other confirmation in the app.
    val consentText = t("Allow my data, with my name and contact details removed, to be used for medical research by my care team", "मेरा डेटा, मेरा नाम और संपर्क जानकारी हटाकर, मेरी देखभाल टीम के चिकित्सा शोध में इस्तेमाल किया जा सकता है") + "\n" +
        t("Your name and contact details are removed first. You can switch this off at any time in Privacy and data.", "पहले आपका नाम और संपर्क जानकारी हटा दी जाती है। आप इसे कभी भी 'निजता और डेटा' में बंद कर सकते हैं।")
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, modifier = Modifier.testTag("privacy-research-confirm"),
        title = { Text(t("Allow research use?", "शोध के लिए इस्तेमाल की अनुमति दें?")) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Your name and contact details are removed first. You can switch this off at any time in Privacy and data.", "पहले आपका नाम और संपर्क जानकारी हटा दी जाती है। आप इसे कभी भी 'निजता और डेटा' में बंद कर सकते हैं।"), fontSize = 17.sp, lineHeight = 24.sp)
            ConsentReadAloudStep(consentText, "research-${person.id}-$readingRound", if (owner) person.name else "", readers, onChange = { reading = it }, modifier = Modifier.testTag("privacy-research-read-aloud"))
        } },
        confirmButton = { Button(onClick = { val heard = reading ?: return@Button; confirm = false; save(true, heard) }, enabled = reading != null,
            modifier = Modifier.heightIn(min = 48.dp).testTag("privacy-research-allow")) { Text(t("Allow", "अनुमति दें"), fontSize = 17.sp, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("privacy-research-not-now")) { Text(t("Not now", "अभी नहीं"), fontSize = 17.sp) } })
}
