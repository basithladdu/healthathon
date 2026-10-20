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
@Composable fun DoctorOverviewContent(overview:DoctorPatientOverview,onConversation:()->Unit,onSection:(String)->Unit,onSchedule:()->Unit) {
    val discussion=overview.lastDiscussion
    val earlierDiscussions=overview.history.filter {it.id!=discussion?.id}
    Column(Modifier.fillMaxWidth().testTag("overview-content"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(color=SaharaSoftBlue,shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Handover at a glance",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                Column(Modifier.testTag("overview-previous-professional"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Previous care professional · fictional example",style=MaterialTheme.typography.labelMedium,color=Ink.copy(alpha=.75f))
                    Text(overview.previousProfessional?.name ?: "Name not recorded",fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                    Text("Not a verified note author or an assigned doctor",style=MaterialTheme.typography.bodySmall,color=Rust)
                }
                Text("Clinical assessment: ${overview.clinicalAssessment}",fontWeight=FontWeight.Medium,modifier=Modifier.testTag("overview-clinical-assessment"))
                Column(Modifier.testTag("overview-handover-toggle"), verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(overview.previousProfessional?.role ?: "Professional role not recorded");Text(overview.previousProfessional?.disclosure ?: "No previous professional is documented.",style=MaterialTheme.typography.bodySmall)}
                Column(Modifier.testTag("overview-background-toggle"), verticalArrangement=Arrangement.spacedBy(4.dp)) {Text("Background summary",fontWeight=FontWeight.SemiBold);Text(overview.background,lineHeight=24.sp);Text("Historical sample profile context, not a current clinical assessment.",style=MaterialTheme.typography.bodySmall,color=Ink.copy(alpha=.75f))}
            }
        }
        Text("Care handover",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
        Column {
            val report=overview.latestReport
            OverviewTimeline("Last reported update",overviewDate(report?.at),SaharaSoftBlue,"overview-latest-report") {
                Text(report?.text ?: "No patient or family update recorded.",lineHeight=24.sp)
                if(report!=null) Text("${report.source.name.lowercase().replaceFirstChar {it.uppercase()}} report · fictional check-in",style=MaterialTheme.typography.bodySmall,color=Rust)
                TextButton(onClick={onSection("Progress")},modifier=Modifier.heightIn(min=48.dp).testTag("overview-progress")) {Text("View reported updates")}
            }
            OverviewTimeline("Last conversation",overviewDate(discussion?.publishedAt),SaharaLavender,"overview-last-discussion") {
                if(discussion==null) Text("No published conversation recorded.") else {
                    OverviewDiscussionMeta(discussion)
                    OverviewLabel("Discussion brief",overviewValue(discussion.topics))
                    if(discussion.questions.isNotBlank()) Column(Modifier.testTag("overview-open-questions")) {OverviewLabel("Questions to carry forward",discussion.questions)}
                    Column(Modifier.testTag("overview-discussion-details-toggle"), verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        OverviewLabel("What matters to the patient",overviewValue(discussion.priorities))
                        OverviewLabel("Who participated",overviewValue(discussion.participants))
                        if(discussion.questions.isBlank()) OverviewLabel("Questions to carry forward","Not recorded")
                        OverviewLabel("Still undecided",overviewValue(discussion.undecided))
                    }
                    OverviewSource(discussion)
                }
                OutlinedButton(onClick=onConversation,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("overview-conversations")) {Text("Open conversations & Care Notes")}
            }
            OverviewTimeline("Next visit & actions",overviewDate(overview.nextVisit?.startMillis),SaharaSoftGold,"overview-next-visit") {
                val visit=overview.nextVisit
                Text(if(visit==null) "No upcoming visit recorded." else "${visit.type} · ${visit.durationMinutes} minutes",fontWeight=FontWeight.Medium)
                OverviewLabel("Recorded next steps",discussion?.nextSteps?.let(::overviewValue) ?: "Not recorded")
                Text("Next steps are copied from the selected discussion, not generated treatment advice.",style=MaterialTheme.typography.bodySmall,color=Ink.copy(alpha=.7f))
                TextButton(onClick={onSection("Reports")},modifier=Modifier.heightIn(min=48.dp).testTag("overview-reports")) {Text("View report summaries")}
                Button(onClick=onSchedule,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("demo-schedule-visit")) {Text("Schedule visit")}
            }
        }
        if(earlierDiscussions.isNotEmpty()) {
            Text("Earlier discussions (${earlierDiscussions.size})",fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("overview-history-toggle"))
            earlierDiscussions.forEach {earlier ->
                Surface(color=Paper,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Ink.copy(alpha=.1f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(overviewDate(earlier.publishedAt),style=MaterialTheme.typography.labelLarge,color=Rust)
                        OverviewDiscussionMeta(earlier)
                        Text("Original discussion source",fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("overview-history-${earlier.id}"))
                        Text(earlier.sourceText.ifBlank {"Original source not recorded."},lineHeight=24.sp,modifier=Modifier.testTag("overview-source-text-${earlier.id}"))
                    }
                }
            }
        }
    }
}
@Composable private fun OverviewLabel(label:String,value:String) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.labelLarge,color=Ink.copy(alpha=.75f))
        Text(value,lineHeight=24.sp)
    }
}
@Composable private fun OverviewDiscussionMeta(discussion:DoctorOverviewDiscussion) {
    Text(if(discussion.isPublishedPreview) "Local preview version ${discussion.versionNumber} · not a verified clinical note" else "Previous sample discussion · not a verified clinical note",style=MaterialTheme.typography.labelMedium,color=Rust)
    Text(if(discussion.isPublishedPreview) "Self-entered reviewer: ${discussion.reviewerName ?: "Not recorded"} · identity not verified" else "Discussion author: ${discussion.reviewerName ?: "Not recorded"}",style=MaterialTheme.typography.bodySmall)
    Text("Source: ${discussion.sourceLabel}",style=MaterialTheme.typography.bodySmall,color=Ink.copy(alpha=.7f))
}
@Composable private fun OverviewSource(discussion:DoctorOverviewDiscussion) {
    if(discussion.sourceText.isNotBlank()) {
        Text("Original source",fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("overview-source-${discussion.id}"))
        Text(discussion.sourceText,lineHeight=24.sp,modifier=Modifier.testTag("overview-source-text-${discussion.id}"))
    } else Text("Original source not recorded.",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun OverviewTimeline(title:String,date:String,tint:Color,tag:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag).drawBehind {
        val x=5.dp.toPx();drawLine(Rust.copy(alpha=.2f),Offset(x,12.dp.toPx()),Offset(x,size.height),strokeWidth=1.dp.toPx());drawCircle(Rust,radius=4.dp.toPx(),center=Offset(x,12.dp.toPx()))
    }.padding(start=24.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Surface(color=tint,shape=RoundedCornerShape(10.dp)) {Text(date,Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium,color=Ink)}
        Surface(color=Paper,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Ink.copy(alpha=.08f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(title,fontWeight=FontWeight.SemiBold,fontSize=18.sp);content()}
        }
    }
}
