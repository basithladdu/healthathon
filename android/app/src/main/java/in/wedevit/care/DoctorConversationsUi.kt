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
        Surface(color = DoctorCardConversation, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Default.Forum, null, tint = DoctorAccent, modifier = Modifier.size(24.dp))
                    Text("Conversation workspace", fontFamily = SaharaTitleFont, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                }
                Text("Prepare the next discussion, or continue your saved words.", fontSize = 15.sp, lineHeight = 23.sp, color = DoctorCardInk)
                if (model.currentDraft != null) {
                    Column(Modifier.testTag("conversation-draft"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Saved draft · not a published Care Note", fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                    }
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("workflow-open-conversation"), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Paper), shape = RoundedCornerShape(14.dp)) {
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
                ConversationHeading("Previous discussion on record")
                Text("An undated discussion from this patient's earlier record. It is separate from the versions saved here.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                DiscussionReadingCard(discussion, highlighted = false)
            }
        }
        if (model.latestPublished == null && model.priorSample == null) {
            Text("No saved discussion is recorded yet. You can prepare one in the workspace.", fontSize = 15.sp, lineHeight = 23.sp, color = DoctorCardInk)
        }
        DoctorFootnote("The reviewer's identity is not verified. Audio is not automatically transcribed, so the words must be checked by a person. Audio stays on this device and is not included when a written note is shared.")
    }
}

@Composable private fun ConversationHeading(text: String) {
    Text(text, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont)
}

@Composable private fun DiscussionReadingCard(discussion: DoctorOverviewDiscussion, highlighted: Boolean) {
    val fields = listOf("What matters to the patient" to discussion.priorities, "Who participated" to discussion.participants, "What was discussed" to discussion.topics, "Questions and concerns" to discussion.questions, "Still undecided" to discussion.undecided, "Next steps" to discussion.nextSteps)
    val author = discussion.reviewerName?.takeIf { it.isNotBlank() } ?: "Author not recorded"
    val brief = discussion.topics.ifBlank { discussion.priorities }.ifBlank { "No summary recorded" }
    Surface(color = if (highlighted) DoctorCardConversation else DoctorCardReport, shape = RoundedCornerShape(20.dp), border = if (highlighted) null else BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("conversation-card-${discussion.id}")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (discussion.isPublishedPreview) "Version ${discussion.versionNumber}" else "Earlier discussion", fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                Text(conversationDate(discussion.publishedAt), fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                Text(author, fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
            }
            Text(brief, fontSize = 15.sp, lineHeight = 23.sp, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = DoctorHairline)
            Column(Modifier.testTag("conversation-expand-${discussion.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (discussion.isPublishedPreview) {
                    fields.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
                            SelectionContainer { Text(value, fontSize = 15.sp, lineHeight = 23.sp, color = DoctorCardInk) }
                        }
                    }
                    if (fields.any { it.second.isBlank() }) Text("Other headings were not recorded in this version.", fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint)
                    HorizontalDivider(color = DoctorHairline)
                }
                Text("Original words", fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                Text(discussion.sourceLabel, fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                SelectionContainer { Text(discussion.sourceText, fontSize = 15.sp, lineHeight = 24.sp, color = DoctorCardInk, modifier = Modifier.testTag("conversation-source-${discussion.id}")) }
            }
        }
    }
}
