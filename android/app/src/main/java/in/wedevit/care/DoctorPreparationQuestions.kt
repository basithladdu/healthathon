package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** A question the clinician wants to cover. [section] indexes [NoteRules.labels]. [answer] is typed by the clinician (or left empty); empty means unanswered. */
internal data class DoctorPreparationQuestion(val id: String, val text: String, val section: Int = DOCTOR_PREPARATION_DEFAULT_SECTION, val answer: String = "")

/** Rows saved before sections existed carry no section; they were things to cover, so they land under "Still to discuss". */
internal const val DOCTOR_PREPARATION_DEFAULT_SECTION = 3
internal const val DOCTOR_PREPARATION_MAX_QUESTIONS = 20
internal const val DOCTOR_PREPARATION_MAX_QUESTION_CHARS = 1000
internal const val DOCTOR_PREPARATION_MAX_ANSWER_CHARS = 2000

/** Standard wording, one list per [NoteRules.labels] entry, used only when the previous note has nothing under "Still to discuss". Clinicians may review and edit every line. */
val DOCTOR_STANDARD_QUESTIONS_WHAT_MATTERS = listOf("What matters most to you at the moment?", "What are you hoping for from your care?")
val DOCTOR_STANDARD_QUESTIONS_WHO_WAS_THERE = listOf("Who would you like involved in decisions about your care?")
val DOCTOR_STANDARD_QUESTIONS_WHAT_WE_DISCUSSED = listOf("What is your understanding of your current health?", "What worries you most about the future?")
val DOCTOR_STANDARD_QUESTIONS_STILL_TO_DISCUSS = listOf("Is there anything you would like to talk about that we have not covered?")
val DOCTOR_STANDARD_QUESTIONS_NEXT_STEPS = listOf("What would you like to happen next?", "Who should hear about what we have agreed?")
val DOCTOR_STANDARD_QUESTIONS = listOf(DOCTOR_STANDARD_QUESTIONS_WHAT_MATTERS, DOCTOR_STANDARD_QUESTIONS_WHO_WAS_THERE, DOCTOR_STANDARD_QUESTIONS_WHAT_WE_DISCUSSED, DOCTOR_STANDARD_QUESTIONS_STILL_TO_DISCUSS, DOCTOR_STANDARD_QUESTIONS_NEXT_STEPS)
/** Wording for turning a "Still to discuss" line (not already a question) into a prompt, and for echoing the care context. */
const val DOCTOR_UNFINISHED_QUESTION_PREFIX = "Left open last time: "
const val DOCTOR_UNFINISHED_QUESTION_SUFFIX = " Where does this stand now?"
const val DOCTOR_CONTEXT_QUESTION_PREFIX = "Care context on record: "
const val DOCTOR_CONTEXT_QUESTION_SUFFIX = " Is this still how things stand?"

/** The five section texts of the previous published note, in [NoteRules.labels] order. Plain holder; callers copy values in from the saved version. */
internal data class DoctorPreviousNote(val whatMatters: String = "", val whoWasThere: String = "", val whatWeDiscussed: String = "", val stillToDiscuss: String = "", val nextSteps: String = "") {
    val sections get() = listOf(whatMatters, whoWasThere, whatWeDiscussed, stillToDiscuss, nextSteps)
}

private val bulletPrefix = Regex("^(?:[-*•]+|\\d+[.)])\\s*")
private fun cleanLine(line: String): String = line.trim().replace(bulletPrefix, "").trim()
private fun clip(s: String, n: Int): String = if (s.length <= n) s else s.take(n - 1).trimEnd() + "…"

/**
 * Proposes a starting set of prompts. Previous "Still to discuss" lines come first; only when there are none is the standard set added. The care context is echoed as plain wording, never interpreted.
 * Suggestions are prompts, not findings: answers are always empty. Ids are positional ("suggested-N") so the same input always yields the same list.
 */
internal fun doctorSuggestQuestions(previous: DoctorPreviousNote?, careContext: String): List<DoctorPreparationQuestion> {
    val out = mutableListOf<Pair<String, Int>>()
    val unfinished = previous?.stillToDiscuss.orEmpty().lines().map(::cleanLine).filter { it.isNotEmpty() }
    if (unfinished.isNotEmpty()) unfinished.forEach { out += clip(if (it.endsWith("?")) it else DOCTOR_UNFINISHED_QUESTION_PREFIX + it.trimEnd('.', ';', ',') + "." + DOCTOR_UNFINISHED_QUESTION_SUFFIX, DOCTOR_PREPARATION_MAX_QUESTION_CHARS) to 3 }
    else DOCTOR_STANDARD_QUESTIONS.forEachIndexed { section, list -> list.forEach { out += it to section } }
    val ctx = careContext.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    if (ctx.isNotEmpty()) out += (DOCTOR_CONTEXT_QUESTION_PREFIX + clip(ctx, 300).trimEnd('.') + "." + DOCTOR_CONTEXT_QUESTION_SUFFIX) to 0
    return out.take(DOCTOR_PREPARATION_MAX_QUESTIONS).mapIndexed { i, (text, section) -> DoctorPreparationQuestion("suggested-$i", text, section, "") }
}

internal fun doctorAnsweredQuestions(rows: List<DoctorPreparationQuestion>): List<DoctorPreparationQuestion> = rows.filter { it.answer.isNotBlank() }
internal fun doctorUnansweredQuestions(rows: List<DoctorPreparationQuestion>): List<DoctorPreparationQuestion> = rows.filter { it.answer.isBlank() }

/** Private preparation only: deliberately excluded from Conversation, clinical context and hashes. */
internal class DoctorPreparationStore(private val read: (String) -> String?, private val write: (String, String) -> Boolean) {
    constructor(context: Context, namespace: String) : this(
        { key -> context.getSharedPreferences("doctor-preparation-${DoctorConversationContext.sha256(namespace.toByteArray())}", Context.MODE_PRIVATE).getString(key, null) },
        { key, value -> context.getSharedPreferences("doctor-preparation-${DoctorConversationContext.sha256(namespace.toByteArray())}", Context.MODE_PRIVATE).edit().putString(key, value).commit() })
    fun load(scope: String): List<DoctorPreparationQuestion> {
        val raw = read(scope) ?: return emptyList()
        val rows = JSONArray(raw)
        // Older rows have only id and text: a missing or out-of-range section becomes the default, a missing answer becomes unanswered.
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> DoctorPreparationQuestion(row.getString("id"), row.getString("text"), row.optInt("section", DOCTOR_PREPARATION_DEFAULT_SECTION).takeIf { s -> s in NoteRules.labels.indices } ?: DOCTOR_PREPARATION_DEFAULT_SECTION, row.optString("answer", "")) } }.also(::validate)
    }
    fun save(scope: String, rows: List<DoctorPreparationQuestion>) {
        validate(rows)
        check(write(scope, JSONArray(rows.map { JSONObject().put("id", it.id).put("text", it.text).put("section", it.section).put("answer", it.answer) }).toString())) { "Questions were not saved. Keep this editor open and try again." }
    }
    companion object {
        fun scope(account: String, person: String, discussion: String): String {
            require(person.isNotBlank())
            // Legacy drafts have no identity. A newly started doctor discussion always has a UUID.
            return DoctorConversationContext.sha256(listOf(account, person, discussion.ifBlank { "legacy-current-draft" }).joinToString("") { "${it.toByteArray(Charsets.UTF_8).size}:$it" }.toByteArray(Charsets.UTF_8))
        }
        fun validate(rows: List<DoctorPreparationQuestion>) {
            require(rows.size <= DOCTOR_PREPARATION_MAX_QUESTIONS && rows.map { it.id }.distinct().size == rows.size) { "Keep up to 20 questions." }
            require(rows.all { it.id.isNotBlank() && it.text.trim().length in 1..DOCTOR_PREPARATION_MAX_QUESTION_CHARS }) { "Enter a question of 1–1000 characters." }
            require(rows.all { it.section in NoteRules.labels.indices }) { "Choose a note section for each question." }
            require(rows.all { it.answer.length <= DOCTOR_PREPARATION_MAX_ANSWER_CHARS }) { "Keep each answer within 2000 characters." }
        }
    }
}

/** Hindi for the preparation store's checks and load/save errors; the English stays in the code, which tests rely on. */
internal val DoctorPreparationErrorsHindi = mapOf(
    "Questions were not saved. Keep this editor open and try again." to "सवाल सहेजे नहीं गए। यह खुला रखें और फिर कोशिश करें।",
    "Keep up to 20 questions." to "ज़्यादा से ज़्यादा 20 सवाल रखें।",
    "Enter a question of 1–1000 characters." to "1–1000 अक्षरों का सवाल लिखें।",
    "Choose a note section for each question." to "हर सवाल के लिए नोट का हिस्सा चुनें।",
    "Keep each answer within 2000 characters." to "हर जवाब 2000 अक्षरों के अंदर रखें।",
    "Questions could not be loaded. Retry before editing." to "सवाल नहीं खुले। बदलने से पहले फिर कोशिश करें।",
    "The discussion or access changed. Reopen these questions." to "बातचीत या पहुँच बदल गई। ये सवाल फिर से खोलें।",
    "Questions were not saved. Try again." to "सवाल सहेजे नहीं गए। फिर कोशिश करें।",
)

/** Shared loading and saving for both composables. Reads [canEdit] at save time so it never goes stale. */
private class PrepModel(val store: DoctorPreparationStore, val scope: String, val initial: List<DoctorPreparationQuestion>, val canEdit: () -> Boolean, val changed: () -> (List<DoctorPreparationQuestion>) -> Unit) {
    var rows by mutableStateOf(emptyList<DoctorPreparationQuestion>())
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    fun load() { try { val saved = store.load(scope); rows = if (saved.isEmpty()) initial else saved; loaded = true; error = null; changed()(rows) } catch (_: Exception) { error = "Questions could not be loaded. Retry before editing."; loaded = false } }
    fun persist(next: List<DoctorPreparationQuestion>): Boolean = try {
        check(canEdit()) { "The discussion or access changed. Reopen these questions." }
        store.save(scope, next); rows = next; error = null; changed()(next); true
    } catch (e: Exception) { error = e.message ?: "Questions were not saved. Try again."; false }
}

@Composable private fun rememberPrepModel(store: DoctorPreparationStore, scope: String, initial: List<DoctorPreparationQuestion>, editable: Boolean, canSave: () -> Boolean, onChange: (List<DoctorPreparationQuestion>) -> Unit, reload: Int = 0): PrepModel {
    val ed by rememberUpdatedState(editable); val cs by rememberUpdatedState(canSave); val ch by rememberUpdatedState(onChange)
    val m = remember(store, scope) { PrepModel(store, scope, initial, { ed && cs() }, { ch }) }
    // [reload] changes when another card saved to the same store (a goal answer kept on leaving the transcript page), so the next save never writes an older list over it.
    LaunchedEffect(store, scope, reload) { m.load() }
    return m
}

@Composable private fun PrepCard(tag: String, title: String, intro: String, m: PrepModel, content: @Composable ColumnScope.() -> Unit) { val t = careText();
    Card(Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = DoctorCardReport), border = BorderStroke(1.dp, DoctorHairline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            m.error?.let { Text(t(it, DoctorPreparationErrorsHindi[it] ?: it), fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("$tag-error").announce()) }
            if (!m.loaded) TextButton(onClick = { m.load() }, modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-retry")) { Text(t("Retry", "फिर कोशिश करें"), color = RustText, fontSize = 16.sp) }
            content()
        }
    }
}

@Composable private fun SectionHeading(index: Int) = Text(noteSectionLabel(NoteRules.labels[index], careHindi()), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.testTag("doctor-preparation-section-$index").a11yHeading())

/**
 * Editable numbered list of questions to cover; new questions default to "Still to discuss" for filing downstream. Saved on this device only; never part of a note or its hash.
 * [initial] is shown (not saved) while nothing is stored yet, e.g. from [doctorSuggestQuestions]. [onChange] receives the list on load and after each change.
 */
@Composable internal fun DoctorPreparationQuestions(
    store: DoctorPreparationStore, scope: String, editable: Boolean, canSave: () -> Boolean,
    onInteraction: (Boolean) -> Unit, initial: List<DoctorPreparationQuestion> = emptyList(), onChange: (List<DoctorPreparationQuestion>) -> Unit = {}, reload: Int = 0,
) { val t = careText();
    val m = rememberPrepModel(store, scope, initial, editable, canSave, onChange, reload)
    // Unsaved edits survive this card scrolling out of the list and rotation.
    var drafts by rememberSaveable(scope) { mutableStateOf(emptyMap<String, String>()) }
    var newText by rememberSaveable(scope) { mutableStateOf("") }
    var confirmRemove by remember(store, scope) { mutableStateOf<String?>(null) }
    confirmRemove?.let { id ->
        AlertDialog(onDismissRequest = { confirmRemove = null },
            title = { Text(t("Remove this question?", "यह सवाल हटाएँ?")) },
            confirmButton = { TextButton(onClick = { if (m.persist(m.rows.filterNot { it.id == id })) drafts = drafts - id; confirmRemove = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Remove question", "सवाल हटाएँ"), color = DoctorAccent) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें")) } })
    }
    val callback by rememberUpdatedState(onInteraction)
    val busy = drafts.isNotEmpty() || newText.isNotEmpty()
    DisposableEffect(busy) { callback(busy); onDispose { callback(false) } }
    PrepCard("doctor-preparation-questions", t("Questions", "सवाल"), "", m) {
        if (m.rows.isEmpty()) Text(t("No questions yet", "अभी कोई सवाल नहीं"), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardSub, modifier = Modifier.testTag("doctor-preparation-empty"))
        m.rows.forEachIndexed { index, row ->
            val shown = drafts[row.id] ?: row.text
            HorizontalDivider(color = DoctorHairline)
            Column(Modifier.fillMaxWidth().testTag("doctor-preparation-row-${row.id}")) {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("Question ${index + 1}", "सवाल ${index + 1}"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = RustText, modifier = Modifier.testTag("doctor-preparation-number-${row.id}"))
                    if (editable) {
                        OutlinedTextField(shown, { drafts = drafts + (row.id to it.take(DOCTOR_PREPARATION_MAX_QUESTION_CHARS)) }, label = { Text(t("Question", "सवाल")) }, minLines = 2, enabled = m.loaded, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-preparation-question-input-${row.id}"))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${shown.length}/$DOCTOR_PREPARATION_MAX_QUESTION_CHARS", fontSize = 13.sp, color = DoctorCardSub, modifier = Modifier.weight(1f))
                            if (drafts.containsKey(row.id)) TextButton(onClick = { if (m.persist(m.rows.map { if (it.id == row.id) it.copy(text = shown.trim()) else it })) drafts = drafts - row.id }, enabled = m.loaded && shown.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-preparation-save-${row.id}")) { Text(t("Save question", "सवाल सहेजें"), color = RustText) }
                            TextButton(onClick = { confirmRemove = row.id }, enabled = m.loaded, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-preparation-remove-${row.id}")) { Text(t("Remove", "हटाएँ"), color = RustText) }
                        }
                    } else Text(row.text, fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardInk)
                }
            }
        }
        if (editable) {
            HorizontalDivider(color = DoctorHairline)
            OutlinedTextField(newText, { newText = it.take(DOCTOR_PREPARATION_MAX_QUESTION_CHARS) }, label = { Text(t("New question", "नया सवाल")) }, minLines = 2, enabled = m.loaded, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-preparation-question-input"))
            TextButton(onClick = { if (m.persist(m.rows + DoctorPreparationQuestion(newId(), newText.trim(), DOCTOR_PREPARATION_DEFAULT_SECTION))) newText = "" }, enabled = m.loaded && m.rows.size < DOCTOR_PREPARATION_MAX_QUESTIONS && newText.isNotBlank(), shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-add-preparation-question")) { Text(t("Add question", "सवाल जोड़ें"), color = RustText, fontSize = 16.sp) }
        }
    }
}

/**
 * Gap-filling view for after the conversation: answered questions show their answer; unanswered ones show an empty field for the clinician to type into.
 * Nothing is filled in automatically here. Whatever is typed is the clinician's own note, stored with the question and outside any published note.
 */
@Composable internal fun DoctorPreparationAnswers(
    store: DoctorPreparationStore, scope: String, editable: Boolean, canSave: () -> Boolean, onChange: (List<DoctorPreparationQuestion>) -> Unit = {}, reload: Int = 0,
) { val t = careText();
    val m = rememberPrepModel(store, scope, emptyList(), editable, canSave, onChange, reload)
    var drafts by rememberSaveable(scope) { mutableStateOf(emptyMap<String, String>()) }
    val unanswered = doctorUnansweredQuestions(m.rows).size
    PrepCard("doctor-preparation-answers", t("Answers", "जवाब"), "", m) {
        Text(if (m.rows.isEmpty()) t("No questions yet", "अभी कोई सवाल नहीं") else if (unanswered == 0) t("All answered", "सबके जवाब मिले") else t("$unanswered unanswered", "$unanswered के जवाब बाक़ी"), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = if (m.rows.isNotEmpty() && unanswered == 0) DoctorStatusGood else DoctorStatusWarn, modifier = Modifier.testTag("doctor-preparation-answers-status"))
        NoteRules.labels.indices.forEach { s ->
            val group = m.rows.filter { it.section == s }
            if (group.isNotEmpty()) {
                SectionHeading(s)
                group.forEach { row ->
                    HorizontalDivider(color = DoctorHairline)
                    Column(Modifier.fillMaxWidth().testTag("doctor-preparation-answer-row-${row.id}")) {
                        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(row.text, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                            if (row.answer.isNotBlank()) {
                                Text(row.answer, fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardInk, modifier = Modifier.testTag("doctor-preparation-answer-${row.id}"))
                                Text(t("Answered", "जवाब मिला"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorStatusGood, modifier = Modifier.testTag("doctor-preparation-answered-${row.id}"))
                            } else {
                                val shown = drafts[row.id].orEmpty()
                                Text(t("Unanswered", "जवाब बाक़ी"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorStatusWarn, modifier = Modifier.testTag("doctor-preparation-unanswered-${row.id}"))
                                if (editable) {
                                    OutlinedTextField(shown, { drafts = drafts + (row.id to it.take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS)) }, label = { Text(t("Your answer", "आपका जवाब")) }, minLines = 2, enabled = m.loaded, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-preparation-answer-input-${row.id}"))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${shown.length}/$DOCTOR_PREPARATION_MAX_ANSWER_CHARS", fontSize = 13.sp, color = DoctorCardSub, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { if (m.persist(m.rows.map { if (it.id == row.id) it.copy(answer = shown.trim()) else it })) drafts = drafts - row.id }, enabled = m.loaded && shown.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-preparation-answer-save-${row.id}")) { Text(t("Save answer", "जवाब सहेजें"), color = RustText) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
