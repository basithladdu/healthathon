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

private fun conversationDate(at: Long?, hindi: Boolean) = at?.takeIf { it > 0 }?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(careDateFormat("d MMM yyyy · h:mm a", hindi))
} ?: careTr(hindi, "Date not recorded", "तारीख़ दर्ज नहीं")

/** Read-only history. Continuing opens the existing patient draft without replacing it. */
@Composable fun DoctorConversationsContent(model: DoctorConversationsModel, onContinue: () -> Unit, modifier: Modifier = Modifier) { val t = careText();
    Column(modifier.fillMaxWidth().testTag("doctor-conversations"), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(color = DoctorCardConversation, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Default.Forum, null, tint = DoctorAccent, modifier = Modifier.size(24.dp))
                    Text(t("Conversations", "बातचीत"), fontFamily = SaharaTitleFont, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                }
                if (model.currentDraft != null) {
                    Column(Modifier.testTag("conversation-draft"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(color = DoctorStatusWarnBg, contentColor = DoctorStatusWarn, shape = RoundedCornerShape(50)) { Text(t("Draft", "ड्राफ़्ट"), fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                    }
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("workflow-open-conversation"), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Paper), shape = RoundedCornerShape(14.dp)) {
                    Text(if (model.currentDraft != null) t("Continue", "जारी रखें") else t("New conversation", "नई बातचीत"), fontSize = 16.sp)
                }
            }
        }
        model.latestPublished?.let { discussion ->
            Column(Modifier.testTag("conversation-latest"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading(t("Latest", "सबसे नई"))
                DiscussionReadingCard(discussion, highlighted = true)
            }
        }
        if (model.earlierPublished.isNotEmpty()) {
            Column(Modifier.testTag("conversation-earlier"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading(t("Earlier", "पहले की"))
                model.earlierPublished.forEach { discussion -> key(model.patientId, discussion.id) { DiscussionReadingCard(discussion, highlighted = false) } }
            }
        }
        model.priorSample?.let { discussion ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading(t("Previous", "पिछली"))
                DiscussionReadingCard(discussion, highlighted = false)
            }
        }
        if (model.latestPublished == null && model.priorSample == null) {
            Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Forum, null, tint = RustText, modifier = Modifier.size(24.dp))
                    Text(t("No conversations yet", "अभी कोई बातचीत नहीं"), fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardSub, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable private fun ConversationHeading(text: String) {
    Text(text, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont, modifier = Modifier.a11yHeading())
}

@Composable private fun DiscussionReadingCard(discussion: DoctorOverviewDiscussion, highlighted: Boolean) { val t = careText();
    val fields = listOf(t("Matters most", "सबसे ज़रूरी") to discussion.priorities, t("Who participated", "कौन शामिल था") to discussion.participants, t("Discussed", "चर्चा हुई") to discussion.topics, t("Questions", "सवाल") to discussion.questions, t("Still undecided", "अभी तय नहीं") to discussion.undecided, t("Next steps", "आगे के कदम") to discussion.nextSteps)
    val author = discussion.reviewerName?.takeIf { it.isNotBlank() } ?: t("Author not recorded", "लेखक दर्ज नहीं")
    val brief = discussion.topics.ifBlank { discussion.priorities }
    Surface(color = if (highlighted) DoctorCardConversation else DoctorCardReport, shape = RoundedCornerShape(20.dp), border = if (highlighted) null else BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("conversation-card-${discussion.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (discussion.isPublishedPreview) t("Version ${discussion.versionNumber}", "संस्करण ${discussion.versionNumber}") else t("Earlier discussion", "पहले की चर्चा"), fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                Text(conversationDate(discussion.publishedAt, careHindi()) + " · " + author, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
            }
            if (brief.isNotBlank()) Text(brief, fontSize = 16.sp, lineHeight = 24.sp, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = DoctorHairline)
            Column(Modifier.testTag("conversation-expand-${discussion.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (discussion.isPublishedPreview) {
                    fields.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
                            SelectionContainer { Text(value, fontSize = 16.sp, lineHeight = 24.sp, color = DoctorCardInk) }
                        }
                    }
                    HorizontalDivider(color = DoctorHairline)
                }
                Text(t("Transcript", "लिखाई"), fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                Text(discussion.sourceLabel, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                SelectionContainer { Text(discussion.sourceText, fontSize = 16.sp, lineHeight = 25.sp, color = DoctorCardInk, modifier = Modifier.testTag("conversation-source-${discussion.id}")) }
            }
        }
    }
}
