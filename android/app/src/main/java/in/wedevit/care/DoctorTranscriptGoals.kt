package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneOffset

/** Answered = the doctor saved an answer; Suggested = word matching found a transcript line, not yet accepted; Open = nothing yet. */
internal enum class TranscriptGoalStatus { Answered, Suggested, Open }

/** Best transcript line for [id] found by word matching, or null. A suggestion only: the doctor accepts or edits it. */
internal fun transcriptGoalSuggestion(matches: QuestionMatchResult?, id: String): QuestionMatch? = matches?.forQuestion(id)?.matches?.firstOrNull()?.takeIf { it.quote.isNotBlank() }
/** Why this line was suggested, so the doctor can check it before Accept. */
internal fun transcriptSuggestionNote(match: QuestionMatch): String = match.askedOnLine?.let { careBilingual("The reply after the question on line $it. Check it against the conversation before you accept.", "पंक्ति $it के सवाल के बाद का जवाब। ठीक है दबाने से पहले जाँचें।") }
    ?: careBilingual("This line shares words with the question. Check it against the conversation before you accept.", "इस पंक्ति के शब्द सवाल से मिलते हैं। ठीक है दबाने से पहले जाँचें।")
/** Verbatim words of the best suggested line, or blank. */
internal fun transcriptGoalSpan(matches: QuestionMatchResult?, id: String): String = transcriptGoalSuggestion(matches, id)?.quote?.trim().orEmpty()
internal fun transcriptGoalStatus(row: DoctorPreparationQuestion, matches: QuestionMatchResult?): TranscriptGoalStatus = if (row.answer.isNotBlank()) TranscriptGoalStatus.Answered else if (transcriptGoalSpan(matches, row.id).isNotBlank()) TranscriptGoalStatus.Suggested else TranscriptGoalStatus.Open

/** The transcript line an answer came from: only when the answer is exactly that line's words (edited words get no link, so they never carry a false citation). */
internal fun transcriptAnswerLine(source: String, answer: String): CareSourceLine? = answer.trim().takeIf { it.isNotBlank() && !it.contains('\n') }?.let { CareDraftAssist.references(source, it).firstOrNull()?.line }

/** Live checklist: covered = answered or suggested from the transcript; open = nothing yet. */
internal data class TranscriptChecklist(val rows: List<Pair<DoctorPreparationQuestion, TranscriptGoalStatus>>) {
    val covered: Int get() = rows.count { it.second != TranscriptGoalStatus.Open }
    val open: Int get() = rows.count { it.second == TranscriptGoalStatus.Open }
    val summary: String get() = careBilingual("$covered of ${rows.size} covered", "${rows.size} में से $covered पर बात हुई")
}
internal fun transcriptChecklist(rows: List<DoctorPreparationQuestion>, matches: QuestionMatchResult?) = TranscriptChecklist(rows.map { it to transcriptGoalStatus(it, matches) })
internal fun transcriptChecklistLabel(status: TranscriptGoalStatus): String = when (status) {
    TranscriptGoalStatus.Answered -> careBilingual("Covered · answer saved", "जवाब सहेजा")
    TranscriptGoalStatus.Suggested -> careBilingual("Suggested from the transcript · found by matching words", "बातचीत से सुझाव, शब्द मिलाकर")
    TranscriptGoalStatus.Open -> careBilingual("Open", "अभी बाकी")
}

/** The pre-written walkthrough transcript seeded for [personId], or null for anyone who is not a walkthrough patient. */
internal fun doctorWalkthroughSample(personId: String): String? = runCatching { when {
    personId.isBlank() -> null
    personId == DoctorCareDemoData.PERSON_ID -> DoctorCareDemoData.fixture(WALKTHROUGH_SAMPLE_AT, ZoneOffset.UTC).draft.source
    else -> DoctorTestProfiles.profiles.firstOrNull { DoctorTestProfiles.personId(it) == personId }?.let { DoctorTestProfiles.fixture(it, WALKTHROUGH_SAMPLE_AT, ZoneOffset.UTC).draft.source }
} }.getOrNull()
/** Any valid time: the fixtures reject 0, and only the transcript's words are used. */
internal const val WALKTHROUGH_SAMPLE_AT = 1_761_523_200_000L
/** True only inside the walkthrough while the transcript is still the seeded sample (also after small edits): it must be labelled "Sample", never shown as transcribed. */
internal fun doctorTranscriptIsSample(walkthrough: Boolean, source: String, sample: String?): Boolean =
    walkthrough && sample != null && source.isNotBlank() && source.lineSequence().first().trim() == sample.lineSequence().first().trim()

/** Compact covered / open list. [onDark] for the terracotta record stage. */
@Composable internal fun DoctorGoalsChecklist(rows: List<DoctorPreparationQuestion>, matches: QuestionMatchResult?, onDark: Boolean = false, hint: String? = null) {
    if (rows.isEmpty()) return
    val list = transcriptChecklist(rows, matches)
    val ink = if (onDark) Color.White else DoctorCardInk
    val sub = if (onDark) Color.White.copy(alpha = .85f) else DoctorCardSub
    Column(Modifier.fillMaxWidth().testTag("goals-checklist"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(careBilingual("Goals-of-care checklist", "लक्ष्य-सूची"), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = ink)
        Text(list.summary, fontSize = 13.sp, lineHeight = 18.sp, color = sub, modifier = Modifier.testTag("goals-checklist-summary"))
        list.rows.forEach { (row, status) ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().testTag("goals-checklist-${row.id}")) {
                val done = status != TranscriptGoalStatus.Open
                Icon(if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (onDark) Color.White else if (status == TranscriptGoalStatus.Answered) DoctorStatusGood else if (done) DoctorStatusWarn else DoctorCardFaint, modifier = Modifier.size(20.dp).padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.text, fontSize = 14.sp, lineHeight = 19.sp, color = ink)
                    Text(transcriptChecklistLabel(status), fontSize = 12.sp, lineHeight = 16.sp, color = sub)
                }
            }
        }
        if (hint != null) Text(hint, fontSize = 12.sp, lineHeight = 16.sp, color = sub)
    }
}

/** The transcript as numbered lines; [highlight] is the line number an answer jumped to. */
@Composable internal fun DoctorTranscriptLines(source: String, highlight: Int?, requesters: (Int) -> BringIntoViewRequester) {
    Column(Modifier.fillMaxWidth().testTag("transcript-lines"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CareDraftAssist.lines(source).forEach { line ->
            val lit = line.number == highlight
            Row(Modifier.fillMaxWidth().bringIntoViewRequester(requesters(line.number)).background(if (lit) DoctorStatusWarnBg else Color.Transparent, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 4.dp).testTag("transcript-line-${line.number}${if (lit) "-highlighted" else ""}")) {
                Text("${line.number}", fontSize = 12.sp, lineHeight = 22.sp, color = DoctorCardFaint, modifier = Modifier.width(28.dp))
                Text(line.text, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk, fontWeight = if (lit) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Collapsible goals-of-care cards for the transcript page. [onSave] receives (question id, answer); [onShowLine] jumps to a transcript line. */
@Composable internal fun DoctorTranscriptGoals(rows: List<DoctorPreparationQuestion>, matches: QuestionMatchResult?, editable: Boolean, onSave: (String, String) -> Unit, onBackToWho: () -> Unit = {}, source: String = "", onShowLine: (CareSourceLine) -> Unit = {}) { val t = careText();
    Column(Modifier.fillMaxWidth().testTag("transcript-goals"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Goals of care", "देखभाल के लक्ष्य"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
        if (rows.isEmpty()) {
            Text(t("No questions were prepared.", "कोई सवाल तैयार नहीं किया गया।"), fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
            TextButton(onClick = onBackToWho, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-goals-back")) { Text(t("Back to who is here", "कौन मौजूद है पर वापस"), color = DoctorAccent) }
        } else {
            Text(careBilingual("Suggestions come from matching words in the transcript. Accept or edit each answer before the note is saved.", "सुझाव बातचीत के शब्दों से हैं; हर जवाब जाँचें।"), fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
            rows.forEach { row -> key(row.id) { GoalCard(row, matches, editable, onSave, source, onShowLine) } }
        }
    }
}

@Composable private fun GoalCard(row: DoctorPreparationQuestion, matches: QuestionMatchResult?, editable: Boolean, onSave: (String, String) -> Unit, source: String, onShowLine: (CareSourceLine) -> Unit) { val t = careText();
    var open by rememberSaveable(row.id) { mutableStateOf(false) }
    val suggestion = transcriptGoalSuggestion(matches, row.id)
    val span = suggestion?.quote?.trim().orEmpty()
    val status = transcriptGoalStatus(row, matches)
    var draft by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    // A typed answer is kept when the page moves on (Continue, Back) without "Save answer"; onSave re-checks access itself.
    val pending by rememberUpdatedState(draft?.trim()?.takeIf { editable && it != row.answer })
    val save by rememberUpdatedState(onSave)
    DisposableEffect(row.id) { onDispose { pending?.let { save(row.id, it) } } }
    val shown = draft ?: row.answer
    val answerLine = remember(source, row.answer) { transcriptAnswerLine(source, row.answer) }
    Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("transcript-goal-${row.id}")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = if (open) t("Collapse", "बंद करें") else t("Expand", "खोलें")) { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.text, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                    val (fg, bg) = when (status) { TranscriptGoalStatus.Answered -> DoctorStatusGood to DoctorStatusGoodBg; TranscriptGoalStatus.Suggested -> DoctorStatusWarn to DoctorStatusWarnBg; TranscriptGoalStatus.Open -> DoctorStatusRest to DoctorStatusRestBg }
                    Surface(shape = RoundedCornerShape(50), color = bg) { Text(transcriptChecklistLabel(status), fontSize = 12.sp, color = fg, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp).testTag("transcript-goal-status-${row.id}")) }
                }
                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = DoctorCardSub, modifier = Modifier.size(48.dp).padding(12.dp))
            }
            if (open) {
                if (row.answer.isNotBlank() && draft == null) {
                    if (answerLine != null) TextButton(onClick = { onShowLine(answerLine) }, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-goal-line-${row.id}")) { Text(careBilingual("From line ${answerLine.number} · show in transcript", "पंक्ति देखें"), color = DoctorAccent) }
                }
                if (row.answer.isBlank() && draft == null && suggestion != null) Surface(shape = RoundedCornerShape(14.dp), color = DoctorStatusWarnBg, modifier = Modifier.fillMaxWidth().testTag("transcript-goal-suggestion-${row.id}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(careBilingual("Suggested from the transcript · found by matching words · line ${suggestion.line.number}", "पंक्ति ${suggestion.line.number}"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DoctorStatusWarn)
                        Text("“$span”", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk, modifier = Modifier.clickable(role = Role.Button, onClickLabel = t("Show this line in the transcript", "यह पंक्ति लिखाई में दिखाएँ")) { onShowLine(suggestion.line) })
                        Text(transcriptSuggestionNote(suggestion), fontSize = 12.sp, color = DoctorCardFaint, modifier = Modifier.testTag("transcript-goal-note-${row.id}"))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onShowLine(suggestion.line) }, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-goal-show-${row.id}")) { Text(t("Show line", "पंक्ति दिखाएँ"), color = DoctorAccent) }
                            if (editable) {
                                TextButton(onClick = { draft = span.take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS) }, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-goal-edit-${row.id}")) { Text(careBilingual("Edit", "बदलें"), color = DoctorAccent) }
                                Button(onClick = { onSave(row.id, span.take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-goal-accept-${row.id}")) { Text(careBilingual("Accept", "ठीक है")) }
                            }
                        }
                    }
                }
                if (editable) {
                    OutlinedTextField(shown, { draft = it.take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS) }, placeholder = { Text(careBilingual("Write the answer", "जवाब लिखें")) }, minLines = 3, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("transcript-goal-answer-${row.id}"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${shown.length}/$DOCTOR_PREPARATION_MAX_ANSWER_CHARS", fontSize = 12.sp, color = DoctorCardFaint, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSave(row.id, shown.trim()); draft = null }, enabled = draft != null && shown.trim() != row.answer, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-goal-save-${row.id}")) { Text(t("Save answer", "जवाब सहेजें"), color = DoctorAccent) }
                    }
                } else Text(shown.ifBlank { t("No answer yet.", "अभी कोई जवाब नहीं।") }, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk, modifier = Modifier.testTag("transcript-goal-answer-${row.id}"))
            }
        }
    }
}

/** Where the transcript words came from, shown under the transcript. */
internal fun doctorTranscriptOrigin(isSample: Boolean, transcribedAt: Long, model: String): String = when {
    isSample -> careBilingual("Practice · written in advance for the walkthrough, not taken from your recording", "अभ्यास, पहले से लिखा, आपकी रिकॉर्डिंग से नहीं")
    transcribedAt > 0 -> careBilingual("Transcript · $model. Check names, numbers and meaning against the audio.", "ऑडियो से नाम, अंक और मतलब जाँचें")
    else -> careBilingual("Typed or dictated here · check every word", "यहाँ लिखा गया")
}

internal val GOALS_CHECKLIST_HINT: String get() = careBilingual("Ticks update as the words are typed, dictated or transcribed", "शब्द लिखे जाने पर टिक बदलते हैं")
