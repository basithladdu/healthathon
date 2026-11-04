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
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Default.Forum, null, tint = DoctorAccent, modifier = Modifier.size(24.dp))
                    Text(t("Conversation workspace", "बातचीत की जगह"), fontFamily = SaharaTitleFont, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                }
                if (model.currentDraft != null) {
                    Column(Modifier.testTag("conversation-draft"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("Saved draft · not a published Care Note", "सहेजा ड्राफ़्ट · प्रकाशित केयर नोट नहीं"), fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                    }
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("workflow-open-conversation"), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Paper), shape = RoundedCornerShape(14.dp)) {
                    Text(if (model.currentDraft != null) t("Continue discussion draft", "चर्चा का ड्राफ़्ट जारी रखें") else t("Open conversation workspace", "बातचीत की जगह खोलें"), fontSize = 16.sp)
                }
            }
        }
        model.latestPublished?.let { discussion ->
            Column(Modifier.testTag("conversation-latest"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading(t("Most recent published discussion", "सबसे नई प्रकाशित चर्चा"))
                DiscussionReadingCard(discussion, highlighted = true)
            }
        }
        if (model.earlierPublished.isNotEmpty()) {
            Column(Modifier.testTag("conversation-earlier"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading(t("Earlier published discussions", "पहले प्रकाशित चर्चाएँ"))
                model.earlierPublished.forEach { discussion -> key(model.patientId, discussion.id) { DiscussionReadingCard(discussion, highlighted = false) } }
            }
        }
        model.priorSample?.let { discussion ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConversationHeading(t("Previous discussion on record", "रिकॉर्ड में पिछली चर्चा"))
                DiscussionReadingCard(discussion, highlighted = false)
            }
        }
        if (model.latestPublished == null && model.priorSample == null) {
            Text(t("No saved discussion is recorded yet. You can prepare one in the workspace.", "अभी कोई सहेजी चर्चा दर्ज नहीं है। आप बातचीत की जगह में एक तैयार कर सकते हैं।"), fontSize = 15.sp, lineHeight = 23.sp, color = DoctorCardInk)
        }
    }
}

@Composable private fun ConversationHeading(text: String) {
    Text(text, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont, modifier = Modifier.a11yHeading())
}

@Composable private fun DiscussionReadingCard(discussion: DoctorOverviewDiscussion, highlighted: Boolean) { val t = careText();
    val fields = listOf(t("What matters to the patient", "मरीज़ के लिए क्या ज़रूरी है") to discussion.priorities, t("Who participated", "कौन शामिल था") to discussion.participants, t("What was discussed", "किस पर बात हुई") to discussion.topics, t("Questions and concerns", "सवाल और चिंताएँ") to discussion.questions, t("Still undecided", "अभी तय नहीं") to discussion.undecided, t("Next steps", "आगे के कदम") to discussion.nextSteps)
    val author = discussion.reviewerName?.takeIf { it.isNotBlank() } ?: t("Author not recorded", "लेखक दर्ज नहीं")
    val brief = discussion.topics.ifBlank { discussion.priorities }.ifBlank { t("No summary recorded", "कोई सार दर्ज नहीं") }
    Surface(color = if (highlighted) DoctorCardConversation else DoctorCardReport, shape = RoundedCornerShape(20.dp), border = if (highlighted) null else BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("conversation-card-${discussion.id}")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (discussion.isPublishedPreview) t("Version ${discussion.versionNumber}", "संस्करण ${discussion.versionNumber}") else t("Earlier discussion", "पहले की चर्चा"), fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                Text(conversationDate(discussion.publishedAt, careHindi()), fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
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
                    HorizontalDivider(color = DoctorHairline)
                }
                Text(t("Original words", "मूल शब्द"), fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                Text(discussion.sourceLabel, fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                SelectionContainer { Text(discussion.sourceText, fontSize = 15.sp, lineHeight = 24.sp, color = DoctorCardInk, modifier = Modifier.testTag("conversation-source-${discussion.id}")) }
            }
        }
    }
}
