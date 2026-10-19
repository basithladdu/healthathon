package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun conversationDate(at: Long?) = at?.takeIf { it > 0 }?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a"))
} ?: "Date not recorded"

/** Read-only history. Continuing opens the existing patient draft without replacing it. */
@Composable fun DoctorConversationsContent(model: DoctorConversationsModel, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("doctor-conversations"), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(color = SaharaLavender, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Default.Forum, null, tint = Rust, modifier = Modifier.size(24.dp))
                    Text("Conversation workspace", fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Text("Prepare the next discussion, or continue your saved words.", fontSize = 16.sp, lineHeight = 23.sp, color = Ink)
                if (model.currentDraft != null) {
                    Column(Modifier.testTag("conversation-draft"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Saved draft · not a published Care Note", fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = Ink.copy(alpha = .75f))
                    }
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("workflow-open-conversation"), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Paper), shape = RoundedCornerShape(14.dp)) {
                    Text(if (model.currentDraft != null) "Continue discussion draft" else "Open conversation workspace", fontSize = 16.sp)
                }
            }
        }
        model.latestPublished?.let { discussion ->
            Column(Modifier.testTag("conversation-latest"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading("Most recent published discussion")
                DiscussionReadingCard(discussion, highlighted = true)
            }
        }
        if (model.earlierPublished.isNotEmpty()) {
            Column(Modifier.testTag("conversation-earlier"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading("Earlier published discussions")
                model.earlierPublished.forEach { discussion -> key(model.patientId, discussion.id) { DiscussionReadingCard(discussion, highlighted = false) } }
            }
        }
        model.priorSample?.let { discussion ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading("Previous sample discussion")
                Text("An undated example from this patient's sample record. It is separate from published local preview versions.", fontSize = 16.sp, lineHeight = 23.sp, color = Ink.copy(alpha = .75f))
                DiscussionReadingCard(discussion, highlighted = false)
            }
        }
        if (model.latestPublished == null && model.priorSample == null) {
            Text("No saved discussion is recorded yet. You can prepare one in the workspace.", fontSize = 18.sp, lineHeight = 26.sp, color = Ink)
        }
    }
}

@Composable private fun ConversationHeading(text: String) {
    Text(text, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, color = Ink)
}

@Composable private fun DiscussionReadingCard(discussion: DoctorOverviewDiscussion, highlighted: Boolean) {
    var expanded by remember(discussion) { mutableStateOf(false) }
    val fields = listOf("What matters to the patient" to discussion.priorities, "Who participated" to discussion.participants, "What was discussed" to discussion.topics, "Questions and concerns" to discussion.questions, "Still undecided" to discussion.undecided, "Next steps" to discussion.nextSteps)
    val summary = listOf(discussion.topics, discussion.priorities, discussion.questions, discussion.nextSteps, discussion.sourceText).firstOrNull { it.isNotBlank() }.orEmpty()
    Surface(color = if (highlighted) SaharaApricot else Paper, shape = RoundedCornerShape(if (highlighted) 20.dp else 16.dp), border = if (highlighted) null else BorderStroke(1.dp, Ink.copy(alpha = .12f)), modifier = Modifier.fillMaxWidth().testTag("conversation-card-${discussion.id}")) {
        Column(Modifier.padding(if (highlighted) 20.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (discussion.isPublishedPreview) "Care Note · Version ${discussion.versionNumber}" else "Original sample discussion", fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(conversationDate(discussion.publishedAt), fontSize = 14.sp, lineHeight = 20.sp, color = Ink.copy(alpha = .75f))
            Text(if (discussion.isPublishedPreview) "Self-entered reviewer: ${discussion.reviewerName ?: "Name not recorded"} · identity not verified" else "Author not recorded · fictional sample", fontSize = 14.sp, lineHeight = 20.sp, color = Ink.copy(alpha = .75f))
            if (!expanded) Text(summary, fontSize = 18.sp, lineHeight = 26.sp, color = Ink, maxLines = if (highlighted) 4 else 3, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp).testTag("conversation-expand-${discussion.id}")) {
                Text(if (expanded) "Close discussion" else "Read full discussion", fontSize = 16.sp)
            }
            if (expanded) {
                if (discussion.isPublishedPreview) {
                    fields.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            SelectionContainer { Text(value, fontSize = 18.sp, lineHeight = 26.sp, color = Ink) }
                        }
                    }
                    if (fields.any { it.second.isBlank() }) Text("Other headings were not recorded in this version.", fontSize = 14.sp, lineHeight = 20.sp, color = Ink.copy(alpha = .75f))
                    HorizontalDivider(color = Ink.copy(alpha = .12f))
                }
                Text("Original words", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(discussion.sourceLabel, fontSize = 14.sp, lineHeight = 20.sp, color = Ink.copy(alpha = .75f))
                SelectionContainer { Text(discussion.sourceText, fontSize = 18.sp, lineHeight = 26.sp, color = Ink, modifier = Modifier.testTag("conversation-source-${discussion.id}")) }
                Text("Local preview only · not a verified clinical record", fontSize = 14.sp, lineHeight = 20.sp, color = Rust)
            }
        }
    }
}
