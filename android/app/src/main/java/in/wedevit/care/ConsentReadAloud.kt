package `in`.wedevit.care

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONObject

/*
 * Consent for people who cannot read (clinical team's note, point 11): before anyone confirms or signs, the text is
 * read to them, and the app records how. Three answers, one of which is required:
 *   SELF    they read it themselves
 *   PHONE   "Read it to me": the phone's own voice read it, in the app language, to the end
 *   PERSON  "Someone read it to me": who read it (name and relationship, picked from people on the record where possible)
 * PHONE and PERSON also record who saw it being read (the witness). Nothing here leaves the phone by itself; callers
 * store the reading next to the confirmation or signature it belongs to.
 *
 * JSON (same names everywhere it is stored): { method, readerName, readerRelation, language, witnessName, witnessRelation, at }.
 */

data class ConsentReading(
    val method: String,
    val readerName: String = "",
    val readerRelation: String = "",
    /** PHONE only: the app language the phone read in ("hi", "te", ...). */
    val language: String = "",
    val witnessName: String = "",
    val witnessRelation: String = "",
    /** ISO-8601 UTC, when the reading was chosen or finished. */
    val at: String = "",
)

/** Someone already on the record who could have read the text or witnessed it. */
data class ConsentPerson(val name: String, val relation: String)

enum class ConsentReadingProblem { METHOD, NOT_HEARD, READER_NAME, READER_RELATION, READER_IS_SIGNER, WITNESS_NAME, WITNESS_RELATION, WITNESS_IS_SIGNER, WITNESS_IS_READER }

object ConsentReadingRules {
    const val SELF = "SELF"
    const val PHONE = "PHONE"
    const val PERSON = "PERSON"
    const val NAME_MAX = 120
    val METHODS = listOf(SELF, PHONE, PERSON)

    /** Relationships offered as taps, so nobody has to type one. Stored in English; shown in the app language. */
    val RELATIONS = listOf("Son", "Daughter", "Husband", "Wife", "Brother", "Sister", "Grandchild", "Friend", "Neighbour", "Nurse", "Doctor", "Social worker", "Volunteer")

    fun normalise(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Read to them (by the phone or a person): someone must also have seen it being read. */
    fun needsWitness(method: String?): Boolean = method == PHONE || method == PERSON

    /** The "can read" answer the goals-of-care sign-off stores, from how the text was read. */
    fun canRead(r: ConsentReading): Boolean = r.method == SELF

    /** Trimmed, capped, and only the fields that belong to the method. */
    fun clean(r: ConsentReading): ConsentReading {
        fun c(s: String) = s.trim().replace(Regex("\\s+"), " ").take(NAME_MAX)
        val person = r.method == PERSON; val witness = needsWitness(r.method)
        return ConsentReading(r.method, if (person) c(r.readerName) else "", if (person) c(r.readerRelation) else "", if (r.method == PHONE) r.language.trim() else "",
            if (witness) c(r.witnessName) else "", if (witness) c(r.witnessRelation) else "", r.at.trim())
    }

    /**
     * Everything still missing before the reading counts. Empty means it may be used. [heard]: the phone voice reached
     * the end (PHONE only). [askWitness] false when a separate witness signature follows (goals-of-care sign-off).
     */
    fun problems(reading: ConsentReading?, signerName: String, askWitness: Boolean, heard: Boolean = true): List<ConsentReadingProblem> = buildList {
        val r = reading?.let(::clean)
        if (r == null || r.method !in METHODS) { add(ConsentReadingProblem.METHOD); return@buildList }
        val signer = normalise(signerName)
        if (r.method == PHONE && !heard) add(ConsentReadingProblem.NOT_HEARD)
        if (r.method == PERSON) {
            if (r.readerName.isBlank()) add(ConsentReadingProblem.READER_NAME)
            if (r.readerRelation.isBlank()) add(ConsentReadingProblem.READER_RELATION)
            if (signer.isNotEmpty() && normalise(r.readerName) == signer) add(ConsentReadingProblem.READER_IS_SIGNER)
        }
        if (askWitness && needsWitness(r.method)) {
            if (r.witnessName.isBlank()) add(ConsentReadingProblem.WITNESS_NAME)
            if (r.witnessRelation.isBlank()) add(ConsentReadingProblem.WITNESS_RELATION)
            val witness = normalise(r.witnessName)
            if (witness.isNotEmpty() && witness == signer) add(ConsentReadingProblem.WITNESS_IS_SIGNER)
            if (witness.isNotEmpty() && r.method == PERSON && witness == normalise(r.readerName)) add(ConsentReadingProblem.WITNESS_IS_READER)
        }
    }

    /** The script each app language is written in, so the phone voice only reads words it can say as written. */
    private fun scriptOf(language: String): Character.UnicodeScript? = when (language) {
        "en" -> Character.UnicodeScript.LATIN; "hi", "mr" -> Character.UnicodeScript.DEVANAGARI; "te" -> Character.UnicodeScript.TELUGU
        "kn" -> Character.UnicodeScript.KANNADA; "ta" -> Character.UnicodeScript.TAMIL; "ml" -> Character.UnicodeScript.MALAYALAM
        "ur" -> Character.UnicodeScript.ARABIC; "bn" -> Character.UnicodeScript.BENGALI; "gu" -> Character.UnicodeScript.GUJARATI
        "pa" -> Character.UnicodeScript.GURMUKHI; else -> null
    }

    /**
     * "Read it to me" reads the exact text, never a machine translation, so it is offered only when every line is written
     * mostly in the script of [language] (a few words in another script, such as "CPR", are fine).
     */
    fun phoneCanReadExactly(text: String, language: String): Boolean {
        val expected = scriptOf(language) ?: return false
        var anyLetters = false
        for (line in text.lines()) {
            val counts = line.filter(Char::isLetter).groupingBy { Character.UnicodeScript.of(it.code) }.eachCount()
            if (counts.isEmpty()) continue
            anyLetters = true
            if (counts.maxByOrNull { it.value }?.key != expected) return false
        }
        return anyLetters
    }

    /** What "heard to the end" is tied to: the exact text and the language it was read in. */
    fun heardKey(text: String, language: String): String = language + ":" + GocSignOffRules.sha256(text.toByteArray(Charsets.UTF_8))

    fun valid(reading: ConsentReading?, signerName: String, askWitness: Boolean, heard: Boolean = true): Boolean = problems(reading, signerName, askWitness, heard).isEmpty()

    /** People to offer as taps: unique by name, blanks dropped, [exclude] (the signer) left out. */
    fun people(candidates: List<ConsentPerson>, exclude: String = ""): List<ConsentPerson> {
        val skip = normalise(exclude)
        return candidates.map { ConsentPerson(it.name.trim(), it.relation.trim()) }.filter { it.name.isNotBlank() && normalise(it.name) != skip }.distinctBy { normalise(it.name) }
    }

    fun encode(r: ConsentReading): JSONObject = clean(r).let { c ->
        JSONObject().put("method", c.method).put("readerName", c.readerName).put("readerRelation", c.readerRelation).put("language", c.language)
            .put("witnessName", c.witnessName).put("witnessRelation", c.witnessRelation).put("at", c.at)
    }

    fun decode(o: JSONObject?): ConsentReading? {
        if (o == null) return null
        val method = o.optString("method").takeIf { it in METHODS } ?: return null
        return clean(ConsentReading(method, o.optString("readerName"), o.optString("readerRelation"), o.optString("language"), o.optString("witnessName"), o.optString("witnessRelation"), o.optString("at")))
    }

    /** The parts a seal covers, in order (used by the goals-of-care seal only when a reading is present, so older seals stay the same). */
    fun sealParts(r: ConsentReading): List<String> = clean(r).let { listOf("readAloud", it.method, it.readerName, it.readerRelation, it.language, it.witnessName, it.witnessRelation, it.at) }

    private fun who(name: String, relation: String) = name + if (relation.isNotBlank()) " ($relation)" else ""

    /** One line for records and PDFs in English, e.g. "Read aloud by Ravi (Son) · witnessed by Mohan (Neighbour)". */
    fun summaryEnglish(r: ConsentReading): String = summary(r) { en, _ -> en }

    fun summaryHindi(r: ConsentReading): String = summary(r) { _, hi -> hi }

    /** The same line in the app language ([t] is the screen's text picker). Relationships are shown in the app language too. */
    fun summary(r: ConsentReading, t: (String, String) -> String): String {
        val c = clean(r)
        val head = when (c.method) {
            SELF -> t("Read it themselves", "ख़ुद पढ़ा")
            PHONE -> t("Read aloud by the phone", "फ़ोन ने पढ़कर सुनाया") + (CareLang.of(c.language)?.let { " · " + it.label }.orEmpty())
            else -> t("Read aloud by", "पढ़कर सुनाया:") + " " + who(c.readerName, relationLabel(t, c.readerRelation))
        }
        val witness = if (c.witnessName.isNotBlank()) " · " + t("witnessed by", "गवाह:") + " " + who(c.witnessName, relationLabel(t, c.witnessRelation)) else ""
        return head + witness
    }

    /** A stored English relationship in the app language; anything else (a typed relationship) as written. */
    fun relationLabel(t: (String, String) -> String, relation: String): String = when (relation) {
        "Son" -> t("Son", "बेटा"); "Daughter" -> t("Daughter", "बेटी"); "Husband" -> t("Husband", "पति"); "Wife" -> t("Wife", "पत्नी")
        "Brother" -> t("Brother", "भाई"); "Sister" -> t("Sister", "बहन"); "Grandchild" -> t("Grandchild", "पोता / पोती"); "Friend" -> t("Friend", "दोस्त")
        "Neighbour" -> t("Neighbour", "पड़ोसी"); "Nurse" -> t("Nurse", "नर्स"); "Doctor" -> t("Doctor", "डॉक्टर"); "Social worker" -> t("Social worker", "सामाजिक कार्यकर्ता")
        "Volunteer" -> t("Volunteer", "स्वयंसेवक"); "Self" -> t("Self", "ख़ुद")
        else -> relation
    }
}

/** Readings saved next to a confirmation (by its id), on this phone only, per account. */
internal class ConsentReadingStore(context: Context, namespace: String) {
    private val safe = namespace.ifBlank { "guest" }.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "guest" }
    private val preferences = context.getSharedPreferences("consent-reading-v1-$safe", Context.MODE_PRIVATE)
    fun save(key: String, reading: ConsentReading): Boolean = preferences.edit().putString(key, ConsentReadingRules.encode(reading).toString()).commit()
    fun load(key: String): ConsentReading? = preferences.getString(key, null)?.let { runCatching { ConsentReadingRules.decode(JSONObject(it)) }.getOrNull() }
}

// ---------------------------------------------------------------------------

private fun consentProblemText(t: (String, String) -> String, p: ConsentReadingProblem): String = when (p) {
    ConsentReadingProblem.METHOD -> t("Choose how it was read before confirming.", "पुष्टि से पहले चुनें कि यह कैसे पढ़ा गया।")
    ConsentReadingProblem.NOT_HEARD -> t("Listen to the whole reading first.", "पहले पूरा पढ़ना सुनें।")
    ConsentReadingProblem.READER_NAME -> t("Who read it?", "किसने पढ़कर सुनाया?")
    ConsentReadingProblem.READER_RELATION -> t("How are they related to the patient?", "उनका मरीज़ से क्या रिश्ता है?")
    ConsentReadingProblem.READER_IS_SIGNER -> t("The person who read it cannot be the person confirming.", "पढ़कर सुनाने वाला और पुष्टि करने वाला एक ही नहीं हो सकते।")
    ConsentReadingProblem.WITNESS_NAME -> t("Who saw it being read?", "किसने इसे पढ़ते हुए देखा?")
    ConsentReadingProblem.WITNESS_RELATION -> t("How is the witness related to the patient?", "गवाह का मरीज़ से क्या रिश्ता है?")
    ConsentReadingProblem.WITNESS_IS_SIGNER -> t("The witness must be someone other than the person confirming.", "गवाह पुष्टि करने वाले से अलग व्यक्ति होना चाहिए।")
    ConsentReadingProblem.WITNESS_IS_READER -> t("The witness must be someone other than the person who read it.", "गवाह पढ़कर सुनाने वाले से अलग व्यक्ति होना चाहिए।")
}

/** One large choice: icon, words of 18 sp, a tick when chosen. No fixed height, so the largest font size still fits. */
@Composable private fun ConsentChoice(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, tag: String, onSelect: () -> Unit) {
    val tick = rememberSaharaTick()
    val fill by animateColorAsState(if (selected) DoctorStatusGoodBg else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "consent-choice-fill")
    val edge by animateColorAsState(if (selected) DoctorStatusGood else DoctorHairline, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "consent-choice-edge")
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).saharaSelectBounce(selected, .04f).clip(RoundedCornerShape(16.dp)).background(fill)
        .border(if (selected) 2.dp else 1.dp, edge, RoundedCornerShape(16.dp))
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = { if (!selected) tick(); onSelect() })
        .padding(horizontal = 16.dp, vertical = 12.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(30.dp), tint = DoctorCardInk); Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = DoctorCardInk)
        AnimatedVisibility(selected, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Icon(Icons.Default.CheckCircle, null, Modifier.size(26.dp), tint = DoctorStatusGood) }
    }
}

/**
 * Picks a person: a tap on someone already on the record (name and relationship together), or "Someone else" with a
 * short name field and relationship taps. [onPick] gets (name, relation).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ConsentPersonPicker(title: String, people: List<ConsentPerson>, name: String, relation: String, enabled: Boolean, tag: String, onPick: (String, String) -> Unit) { val t = careText()
    val known = people.firstOrNull { ConsentReadingRules.normalise(it.name) == ConsentReadingRules.normalise(name) && name.isNotBlank() }
    var other by rememberSaveable(tag) { mutableStateOf(people.isEmpty()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.a11yHeading())
        if (people.isNotEmpty()) {
            val labels = people.map { p -> p.name + if (p.relation.isNotBlank()) " · " + ConsentReadingRules.relationLabel(t, p.relation) else "" }
            val elseLabel = t("Someone else", "कोई और")
            val opts = people.mapIndexed { i, _ -> SaharaDropdownOption(labels[i], "$tag-person-$i", !other && known == people[i]) } + SaharaDropdownOption(elseLabel, "$tag-other", other)
            SaharaDropdown(title, if (other) elseLabel else known?.let { labels[people.indexOf(it)] }.orEmpty(), opts, tag = "$tag-select", enabled = enabled, placeholder = t("Choose a person", "व्यक्ति चुनें"),
                onPick = { i -> if (i < people.size) { other = false; onPick(people[i].name, people[i].relation) } else { other = true; if (known != null) onPick("", "") } })
        }
        // Someone else: a short name and a relationship tap. Someone on the record without a relationship: the taps only.
        AnimatedVisibility(other || known?.relation?.isBlank() == true, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (other) CareField(name, { onPick(it.take(ConsentReadingRules.NAME_MAX), relation) }, t("Name", "नाम"), singleLine = true)
                SaharaDropdown(t("Relationship to the patient", "मरीज़ से रिश्ता"), if (relation.isBlank()) "" else ConsentReadingRules.relationLabel(t, relation),
                    ConsentReadingRules.RELATIONS.map { r -> SaharaDropdownOption(ConsentReadingRules.relationLabel(t, r), "$tag-relation-$r", relation == r) },
                    tag = "$tag-relation-select", enabled = enabled, placeholder = t("Choose", "चुनें"), onPick = { i -> onPick(name, ConsentReadingRules.RELATIONS[i]) })
            }
        }
    }
}

/**
 * The read-aloud step that comes before any confirmation or signature. It reports a complete [ConsentReading] through
 * [onChange], or null while something is still missing, so the caller can keep its Confirm or Sign button off until then.
 *
 * [text] is exactly what is being confirmed; "Read it to me" reads those exact words with the phone's voice in the app
 * language, never a translation, and is offered only when [text] is written in the app language and a voice is installed.
 * [people] are offered as taps for who read it and who witnessed it. [askWitness] false when a witness signs separately.
 */
@Composable internal fun ConsentReadAloudStep(
    text: String, identity: String, signerName: String, people: List<ConsentPerson>, onChange: (ConsentReading?) -> Unit,
    modifier: Modifier = Modifier, askWitness: Boolean = true, enabled: Boolean = true,
) { val t = careText()
    val context = LocalContext.current; val lang = careLanguage()
    var method by rememberSaveable(identity) { mutableStateOf<String?>(null) }
    // Heard is kept for one exact text in one language: another text, or another language, needs hearing again.
    var heardKey by rememberSaveable(identity) { mutableStateOf("") }
    val key = remember(text, lang) { ConsentReadingRules.heardKey(text, lang.code) }
    val heard = heardKey == key
    var at by rememberSaveable(identity) { mutableStateOf("") }
    var readerName by rememberSaveable(identity) { mutableStateOf("") }; var readerRelation by rememberSaveable(identity) { mutableStateOf("") }
    var witnessName by rememberSaveable(identity) { mutableStateOf("") }; var witnessRelation by rememberSaveable(identity) { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val reader = remember(identity) { CareReadAloud(context) { revision++ } }
    // The voice stops when the app goes to the background, and the phone's speech engine is shut down when the step leaves.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(reader, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && reader.busy) reader.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); reader.close() }
    }
    LaunchedEffect(key) { if (reader.busy) reader.stop() }
    @Suppress("UNUSED_VARIABLE") val changed = revision
    val finished = careTr(CareLanguageState.shown, "Finished reading", "पढ़ना पूरा हुआ")
    LaunchedEffect(revision) { if (!reader.speaking && !reader.working && reader.status == finished && method == ConsentReadingRules.PHONE && heardKey != key) { heardKey = key; at = GocSignOffRules.isoNow() } }

    fun choose(m: String) {
        if (method == m) return
        method = m; at = GocSignOffRules.isoNow()
        if (m != ConsentReadingRules.PHONE && reader.busy) reader.stop()
    }
    val offered = remember(people, signerName) { ConsentReadingRules.people(people, exclude = signerName) }
    // The phone reads the exact words only (no machine translation), and only with a voice installed for the app language.
    val exact = remember(text, lang) { ConsentReadingRules.phoneCanReadExactly(text, lang.code) }
    val voice = reader.hasVoice(lang.speechTag)
    val phoneOffered = exact && voice != false
    val candidate = method?.let { ConsentReadingRules.clean(ConsentReading(it, readerName, readerRelation, if (it == ConsentReadingRules.PHONE) lang.code else "", witnessName, witnessRelation, at)) }
    val problems = ConsentReadingRules.problems(candidate, signerName, askWitness, heard && phoneOffered)
    val ready = candidate.takeIf { problems.isEmpty() }
    val latest by rememberUpdatedState(onChange)
    LaunchedEffect(ready) { latest(ready) }

    Surface(color = Cream, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.fillMaxWidth().testTag("consent-read-aloud")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Before confirming, how was this read?", "पुष्टि से पहले, यह कैसे पढ़ा गया?"), fontFamily = SaharaTitleFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            ConsentChoice(t("Read it to me", "मुझे पढ़कर सुनाएँ"), Icons.Default.VolumeUp, method == ConsentReadingRules.PHONE, enabled && phoneOffered, "consent-read-phone") {
                choose(ConsentReadingRules.PHONE); if (!reader.busy && !heard) reader.read(text, lang.speechTag, translate = false)
            }
            if (!phoneOffered) Text(if (!exact) t("The phone can only read text written in the app language. Choose “Someone read it to me”.", "फ़ोन सिर्फ़ ऐप की भाषा में लिखा लेख पढ़ सकता है। “किसी ने पढ़कर सुनाया” चुनें।")
                else t("This phone has no voice for the app language. Choose “Someone read it to me”.", "इस फ़ोन में ऐप की भाषा की आवाज़ नहीं है। “किसी ने पढ़कर सुनाया” चुनें।"),
                fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub, modifier = Modifier.padding(start = 8.dp).testTag("consent-read-phone-unavailable"))
            AnimatedVisibility(method == ConsentReadingRules.PHONE && phoneOffered, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                Column(Modifier.fillMaxWidth().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { if (reader.busy) reader.stop() else { heardKey = ""; reader.read(text, lang.speechTag, translate = false) } }, enabled = enabled, shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 52.dp).testTag("consent-read-play")) {
                        Icon(if (reader.busy) Icons.Default.Stop else if (heard) Icons.Default.Replay else Icons.Default.VolumeUp, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (reader.busy) t("Stop reading", "पढ़ना रोकें") else if (heard) t("Read it again", "फिर से सुनाएँ") else t("Read it to me", "मुझे पढ़कर सुनाएँ"), fontSize = 17.sp)
                    }
                    if (heard) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = DoctorStatusGood); Spacer(Modifier.width(6.dp))
                        Text(t("Heard to the end", "पूरा सुना"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorStatusGood, modifier = Modifier.announce().testTag("consent-read-heard"))
                    } else if (reader.status.isNotBlank()) Text(reader.status, fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardSub, modifier = Modifier.announce())
                }
            }
            ConsentChoice(t("Someone read it to me", "किसी ने पढ़कर सुनाया"), Icons.Default.RecordVoiceOver, method == ConsentReadingRules.PERSON, enabled, "consent-read-person") { choose(ConsentReadingRules.PERSON) }
            AnimatedVisibility(method == ConsentReadingRules.PERSON, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                ConsentPersonPicker(t("Who read it?", "किसने पढ़कर सुनाया?"), offered, readerName, readerRelation, enabled, "consent-reader") { n, r -> readerName = n; readerRelation = r }
            }
            ConsentChoice(t("I read it myself", "मैंने ख़ुद पढ़ा"), Icons.Default.Visibility, method == ConsentReadingRules.SELF, enabled, "consent-read-self") { choose(ConsentReadingRules.SELF) }
            AnimatedVisibility(askWitness && ConsentReadingRules.needsWitness(method), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                ConsentPersonPicker(t("Who saw it being read? (witness)", "किसने इसे पढ़ते हुए देखा? (गवाह)"), offered.filter { method != ConsentReadingRules.PERSON || ConsentReadingRules.normalise(it.name) != ConsentReadingRules.normalise(readerName) },
                    witnessName, witnessRelation, enabled, "consent-witness") { n, r -> witnessName = n; witnessRelation = r }
            }
            val shown = problems.firstOrNull()?.takeIf { method != null }
            AnimatedVisibility(shown != null, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                Text(shown?.let { consentProblemText(t, it) }.orEmpty(), fontSize = 16.sp, lineHeight = 22.sp, color = RustText, modifier = Modifier.announce().testTag("consent-read-missing"))
            }
        }
    }
}

/** "Read aloud by Ravi (Son) · witnessed by Mohan (Neighbour)" under a confirmation or signature. */
@Composable internal fun ConsentReadingLine(reading: ConsentReading, modifier: Modifier = Modifier) { val t = careText()
    Row(modifier.fillMaxWidth().testTag("consent-reading-line"), verticalAlignment = Alignment.Top) {
        Icon(if (reading.method == ConsentReadingRules.SELF) Icons.Default.Visibility else Icons.Default.RecordVoiceOver, null, Modifier.size(18.dp).padding(top = 2.dp), tint = DoctorCardSub); Spacer(Modifier.width(6.dp))
        Text(ConsentReadingRules.summary(reading, t), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub)
    }
}
