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
    var sourceOpen by remember(overview) {mutableStateOf<String?>(null)}
    var historyOpen by remember(overview) {mutableStateOf(false)}
    var handoverOpen by remember(overview) {mutableStateOf(false)}
    var backgroundOpen by remember(overview) {mutableStateOf(false)}
    var discussionDetailsOpen by remember(overview) {mutableStateOf(false)}
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
                Row {TextButton(onClick={handoverOpen=!handoverOpen},modifier=Modifier.heightIn(min=48.dp).testTag("overview-handover-toggle")) {Text(if(handoverOpen) "Hide handover context" else "About this handover")}}
                if(handoverOpen) {Text(overview.previousProfessional?.role ?: "Professional role not recorded");Text(overview.previousProfessional?.disclosure ?: "No previous professional is documented.",style=MaterialTheme.typography.bodySmall)}
                TextButton(onClick={backgroundOpen=!backgroundOpen},modifier=Modifier.heightIn(min=48.dp).testTag("overview-background-toggle")) {Text(if(backgroundOpen) "Hide background summary" else "Background summary")}
                if(backgroundOpen) {Text(overview.background,lineHeight=24.sp);Text("Historical sample profile context, not a current clinical assessment.",style=MaterialTheme.typography.bodySmall,color=Ink.copy(alpha=.75f))}
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
                    TextButton(onClick={discussionDetailsOpen=!discussionDetailsOpen},modifier=Modifier.heightIn(min=48.dp).testTag("overview-discussion-details-toggle")) {Text(if(discussionDetailsOpen) "Hide discussion details" else "Discussion details")}
                    if(discussionDetailsOpen) {
                        OverviewLabel("What matters to the patient",overviewValue(discussion.priorities))
                        OverviewLabel("Who participated",overviewValue(discussion.participants))
                        if(discussion.questions.isBlank()) OverviewLabel("Questions to carry forward","Not recorded")
                        OverviewLabel("Still undecided",overviewValue(discussion.undecided))
                    }
                    OverviewSource(discussion,sourceOpen==discussion.id) {sourceOpen=if(sourceOpen==discussion.id) null else discussion.id}
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
            TextButton(onClick={historyOpen=!historyOpen},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("overview-history-toggle")) {Text(if(historyOpen) "Hide earlier discussions" else "Earlier discussions (${earlierDiscussions.size})")}
            if(historyOpen) earlierDiscussions.forEach {earlier ->
                Surface(color=Paper,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Ink.copy(alpha=.1f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(overviewDate(earlier.publishedAt),style=MaterialTheme.typography.labelLarge,color=Rust)
                        OverviewDiscussionMeta(earlier)
                        TextButton(onClick={sourceOpen=if(sourceOpen==earlier.id) null else earlier.id},modifier=Modifier.heightIn(min=48.dp).testTag("overview-history-${earlier.id}")) {Text(if(sourceOpen==earlier.id) "Hide this source" else "Read this discussion source")}
                        if(sourceOpen==earlier.id) Text(earlier.sourceText.ifBlank {"Original source not recorded."},lineHeight=24.sp,modifier=Modifier.testTag("overview-source-text"))
                    }
                }
            }
        }
    }
}
@Composable private fun OverviewLabel(label:String,value:String) {
    var expanded by remember(label,value) {mutableStateOf(false)}
    var overflows by remember(label,value) {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.labelLarge,color=Ink.copy(alpha=.75f))
        Text(value,lineHeight=24.sp,maxLines=if(expanded) Int.MAX_VALUE else 4,overflow=TextOverflow.Ellipsis,
            onTextLayout={if(!expanded) overflows=it.didOverflowHeight || it.multiParagraph.didExceedMaxLines})
        if(overflows) TextButton(onClick={expanded=!expanded},modifier=Modifier.heightIn(min=48.dp)) {Text(if(expanded) "Show less" else "Read full field")}
    }
}
@Composable private fun OverviewDiscussionMeta(discussion:DoctorOverviewDiscussion) {
    Text(if(discussion.isPublishedPreview) "Local preview version ${discussion.versionNumber} · not a verified clinical note" else "Previous sample discussion · not a verified clinical note",style=MaterialTheme.typography.labelMedium,color=Rust)
    Text(if(discussion.isPublishedPreview) "Self-entered reviewer: ${discussion.reviewerName ?: "Not recorded"} · identity not verified" else "Discussion author: ${discussion.reviewerName ?: "Not recorded"}",style=MaterialTheme.typography.bodySmall)
    Text("Source: ${discussion.sourceLabel}",style=MaterialTheme.typography.bodySmall,color=Ink.copy(alpha=.7f))
}
@Composable private fun OverviewSource(discussion:DoctorOverviewDiscussion,open:Boolean,onToggle:()->Unit) {
    if(discussion.sourceText.isNotBlank()) {
        TextButton(onClick=onToggle,modifier=Modifier.heightIn(min=48.dp).testTag("overview-source-${discussion.id}")) {Text(if(open) "Hide original source" else "Read original source")}
        if(open) Text(discussion.sourceText,lineHeight=24.sp,modifier=Modifier.testTag("overview-source-text"))
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
