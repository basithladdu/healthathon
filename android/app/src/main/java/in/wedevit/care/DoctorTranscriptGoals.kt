package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class TranscriptGoalStatus { Answered, Found, NotFound }

/** Best verbatim transcript span for [id], or blank when the matcher found none. A suggestion only, never verified. */
internal fun transcriptGoalSpan(matches: QuestionMatchResult?, id: String): String = matches?.forQuestion(id)?.matches?.firstOrNull()?.quote?.trim().orEmpty()
internal fun transcriptGoalStatus(row: DoctorPreparationQuestion, matches: QuestionMatchResult?): TranscriptGoalStatus = if (row.answer.isNotBlank()) TranscriptGoalStatus.Answered else if (transcriptGoalSpan(matches, row.id).isNotBlank()) TranscriptGoalStatus.Found else TranscriptGoalStatus.NotFound
/** Rows with detected text saved as the answer where blank; saved answers are never overwritten. */
internal fun transcriptGoalsFill(rows: List<DoctorPreparationQuestion>, matches: QuestionMatchResult?): List<DoctorPreparationQuestion> = rows.map { if (it.answer.isBlank()) it.copy(answer = transcriptGoalSpan(matches, it.id).take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS)) else it }

/** Collapsible goals-of-care cards for the transcript page. [onSave] receives (question id, answer). */
@Composable internal fun DoctorTranscriptGoals(rows: List<DoctorPreparationQuestion>, matches: QuestionMatchResult?, editable: Boolean, onSave: (String, String) -> Unit, onBackToWho: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().testTag("transcript-goals"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Goals of care", fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
        if (rows.isEmpty()) {
            Text("No goals-of-care questions were added before recording. Add them on the Who's here page.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
            TextButton(onClick = onBackToWho, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-goals-back")) { Text("Back to who is here", color = DoctorAccent) }
        } else rows.forEach { row -> key(row.id) { GoalCard(row, matches, editable, onSave) } }
    }
}

@Composable private fun GoalCard(row: DoctorPreparationQuestion, matches: QuestionMatchResult?, editable: Boolean, onSave: (String, String) -> Unit) {
    var open by remember(row.id) { mutableStateOf(false) }
    val span = transcriptGoalSpan(matches, row.id)
    val status = transcriptGoalStatus(row, matches)
    var draft by remember(row.id) { mutableStateOf<String?>(null) }
    val prefilled = row.answer.isBlank() && span.isNotBlank()
    val shown = draft ?: row.answer.ifBlank { span }
    Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("transcript-goal-${row.id}")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.text, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                    val (label, fg, bg) = when (status) { TranscriptGoalStatus.Answered -> Triple("Answered", DoctorStatusGood, DoctorStatusGoodBg); TranscriptGoalStatus.Found -> Triple("Found in the conversation", DoctorStatusGood, DoctorStatusGoodBg); TranscriptGoalStatus.NotFound -> Triple("Not found — add the answer", DoctorStatusWarn, DoctorStatusWarnBg) }
                    Surface(shape = RoundedCornerShape(50), color = bg) { Text(label, fontSize = 12.sp, color = fg, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp).testTag("transcript-goal-status-${row.id}")) }
                }
                IconButton(onClick = { open = !open }, modifier = Modifier.size(48.dp)) { Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (open) "Collapse" else "Expand", tint = DoctorCardSub) }
            }
            if (open) {
                if (editable) {
                    OutlinedTextField(shown, { draft = it.take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS) }, placeholder = { Text("Write the answer") }, minLines = 3, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("transcript-goal-answer-${row.id}"))
                    if (prefilled && draft == null) Text("Taken from the conversation words — check and edit.", fontSize = 12.sp, color = DoctorCardFaint)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${shown.length}/$DOCTOR_PREPARATION_MAX_ANSWER_CHARS", fontSize = 12.sp, color = DoctorCardFaint, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSave(row.id, shown.trim()); draft = null }, enabled = shown.trim() != row.answer && (draft != null || prefilled), modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-goal-save-${row.id}")) { Text("Save answer", color = DoctorAccent) }
                    }
                } else Text(shown.ifBlank { "No answer yet." }, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk, modifier = Modifier.testTag("transcript-goal-answer-${row.id}"))
            }
        }
    }
}
