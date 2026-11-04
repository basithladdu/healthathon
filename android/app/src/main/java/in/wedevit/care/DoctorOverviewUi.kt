package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun overviewDate(at:Long?):String = at?.takeIf {it>0}?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a"))
} ?: "Date not recorded"
private fun overviewValue(value:String) = value.takeIf {it.isNotBlank()} ?: "Not recorded"

/** Read-only handover. Sources stay attached to their exact selected discussion snapshot. */
@Composable fun DoctorOverviewContent(overview:DoctorPatientOverview,onConversation:()->Unit,onSection:(String)->Unit,onSchedule:()->Unit) { val t = careText();
    val discussion=overview.lastDiscussion
    val earlierDiscussions=overview.history.filter {it.id!=discussion?.id}
    Column(Modifier.fillMaxWidth().testTag("overview-content"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(color=SaharaSoftBlue,shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(t("Handover at a glance", "एक नज़र में हैंडओवर"),fontSize=19.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont)
                Column(Modifier.testTag("overview-previous-professional"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(t("Previous care professional", "पिछले देखभाल पेशेवर"),style=MaterialTheme.typography.labelMedium,color=DoctorCardSub)
                    Text(overview.previousProfessional?.name ?: t("Name not recorded", "नाम दर्ज नहीं"),fontWeight=FontWeight.Medium,fontSize=18.sp, fontFamily = SaharaTitleFont)
                }
                Text(t("Clinical assessment: ${overview.clinicalAssessment}", "क्लिनिकल आकलन: ${overview.clinicalAssessment}"),fontWeight=FontWeight.Medium,modifier=Modifier.testTag("overview-clinical-assessment"))
                Column(Modifier.testTag("overview-handover-toggle"), verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(overview.previousProfessional?.role ?: t("Professional role not recorded", "पेशेवर भूमिका दर्ज नहीं"));Text(overview.previousProfessional?.disclosure ?: t("No previous professional is documented.", "कोई पिछला पेशेवर दर्ज नहीं।"),fontSize=14.sp,color=DoctorCardSub)}
                Column(Modifier.testTag("overview-background-toggle"), verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(t("Background summary", "पृष्ठभूमि का सार"),fontWeight=FontWeight.SemiBold);Text(overview.background,lineHeight=24.sp)}
            }
        }
        Text(t("Care handover", "देखभाल हैंडओवर"),fontSize=21.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont)
        Column {
            val report=overview.latestReport
            OverviewTimeline(t("Last reported update", "पिछला बताया अपडेट"),overviewDate(report?.at),SaharaSoftBlue,"overview-latest-report") {
                Text(report?.text ?: t("No patient or family update recorded.", "मरीज़ या परिवार का कोई अपडेट दर्ज नहीं।"),lineHeight=24.sp)
                if(report!=null) Text("${report.source.name.lowercase().replaceFirstChar {it.uppercase()}} report",fontSize=14.sp,color=RustText)
                TextButton(onClick={onSection("Progress")},modifier=Modifier.heightIn(min=48.dp).testTag("overview-progress")) {Text(t("View reported updates", "बताए अपडेट देखें"))}
            }
            OverviewTimeline(t("Last conversation", "पिछली बातचीत"),overviewDate(discussion?.publishedAt),SaharaLavender,"overview-last-discussion") {
                if(discussion==null) Text(t("No published conversation recorded.", "कोई प्रकाशित बातचीत दर्ज नहीं।")) else {
                    OverviewDiscussionMeta(discussion)
                    OverviewLabel(t("Discussion brief", "चर्चा का सार"),overviewValue(discussion.topics))
                    if(discussion.questions.isNotBlank()) Column(Modifier.testTag("overview-open-questions")) {OverviewLabel(t("Questions to carry forward", "आगे ले जाने वाले सवाल"),discussion.questions)}
                    Column(Modifier.testTag("overview-discussion-details-toggle"), verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        OverviewLabel(t("What matters to the patient", "मरीज़ के लिए क्या ज़रूरी है"),overviewValue(discussion.priorities))
                        OverviewLabel(t("Who participated", "कौन शामिल था"),overviewValue(discussion.participants))
                        if(discussion.questions.isBlank()) OverviewLabel(t("Questions to carry forward", "आगे ले जाने वाले सवाल"),t("Not recorded", "दर्ज नहीं"))
                        OverviewLabel(t("Still undecided", "अभी तय नहीं"),overviewValue(discussion.undecided))
                    }
                    OverviewSource(discussion)
                }
                OutlinedButton(onClick=onConversation,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("overview-conversations")) {Text(t("Open conversations & Care Notes", "बातचीत और केयर नोट खोलें"))}
            }
            OverviewTimeline(t("Next visit & actions", "अगली विज़िट और काम"),overviewDate(overview.nextVisit?.startMillis),SaharaSoftGold,"overview-next-visit") {
                val visit=overview.nextVisit
                Text(if(visit==null) t("No upcoming visit recorded.", "आगे की कोई विज़िट दर्ज नहीं।") else "${visit.type} · ${visit.durationMinutes} minutes",fontWeight=FontWeight.Medium)
                OverviewLabel(t("Recorded next steps", "दर्ज अगले क़दम"),discussion?.nextSteps?.let(::overviewValue) ?: t("Not recorded", "दर्ज नहीं"))
                TextButton(onClick={onSection("Reports")},modifier=Modifier.heightIn(min=48.dp).testTag("overview-reports")) {Text(t("View report summaries", "सूचनाओं का सार देखें"))}
                Button(onClick=onSchedule,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("demo-schedule-visit")) {Text(t("Schedule visit", "विज़िट तय करें"))}
            }
        }
        if(earlierDiscussions.isNotEmpty()) {
            Text(t("Earlier discussions (${earlierDiscussions.size})", "पुरानी चर्चाएँ (${earlierDiscussions.size})"),fontWeight=FontWeight.Medium,modifier=Modifier.testTag("overview-history-toggle"), fontFamily = SaharaTitleFont)
            earlierDiscussions.forEach {earlier ->
                Surface(color=Paper,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,DoctorHairline)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(overviewDate(earlier.publishedAt),style=MaterialTheme.typography.labelLarge,color=Rust)
                        OverviewDiscussionMeta(earlier)
                        Text(t("Original discussion source", "चर्चा का मूल स्रोत"),fontWeight=FontWeight.Medium,modifier=Modifier.testTag("overview-history-${earlier.id}"), fontFamily = SaharaTitleFont)
                        Text(earlier.sourceText.ifBlank {t("Original source not recorded.", "मूल स्रोत दर्ज नहीं।")},lineHeight=24.sp,modifier=Modifier.testTag("overview-source-text-${earlier.id}"))
                    }
                }
            }
        }
    }
}
@Composable private fun OverviewLabel(label:String,value:String) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.labelLarge,color=DoctorCardSub)
        Text(value,lineHeight=24.sp)
    }
}
@Composable private fun OverviewDiscussionMeta(discussion:DoctorOverviewDiscussion) { val t = careText();
    Text(if(discussion.isPublishedPreview) t("Version ${discussion.versionNumber} · not a verified clinical note", "संस्करण ${discussion.versionNumber} · पुष्ट क्लिनिकल नोट नहीं") else t("Earlier discussion · not a verified clinical note", "पुरानी चर्चा · पुष्ट क्लिनिकल नोट नहीं"),style=MaterialTheme.typography.labelMedium,color=DoctorAccent)
    Text(if(discussion.isPublishedPreview) t("Self-entered reviewer: ", "ख़ुद लिखे समीक्षक: ") + (discussion.reviewerName ?: t("Not recorded", "दर्ज नहीं")) + t(" · identity not verified", " · पहचान पुष्ट नहीं") else t("Discussion author: ", "चर्चा के लेखक: ") + (discussion.reviewerName ?: t("Not recorded", "दर्ज नहीं")),fontSize=14.sp,color=DoctorCardSub)
    Text(t("Source: ${discussion.sourceLabel}", "स्रोत: ${discussion.sourceLabel}"),fontSize=14.sp,color=DoctorCardSub)
}
@Composable private fun OverviewSource(discussion:DoctorOverviewDiscussion) { val t = careText();
    if(discussion.sourceText.isNotBlank()) {
        Text(t("Original source", "मूल स्रोत"),fontWeight=FontWeight.Medium,modifier=Modifier.testTag("overview-source-${discussion.id}"), fontFamily = SaharaTitleFont)
        Text(discussion.sourceText,lineHeight=24.sp,modifier=Modifier.testTag("overview-source-text-${discussion.id}"))
    } else Text(t("Original source not recorded.", "मूल स्रोत दर्ज नहीं।"),fontSize=14.sp,color=DoctorCardSub)
}
@Composable private fun OverviewTimeline(title:String,date:String,tint:Color,tag:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag).drawBehind {
        val x=5.dp.toPx();drawLine(DoctorHairline,Offset(x,12.dp.toPx()),Offset(x,size.height),strokeWidth=1.dp.toPx());drawCircle(Rust,radius=4.dp.toPx(),center=Offset(x,12.dp.toPx()))
    }.padding(start=24.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Surface(color=tint,shape=RoundedCornerShape(50)) {Text(date,Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium,color=Ink)}
        Surface(color=Paper,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,DoctorHairline)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(title,fontWeight=FontWeight.Medium,fontSize=18.sp, fontFamily = SaharaTitleFont);content()}
        }
    }
}
