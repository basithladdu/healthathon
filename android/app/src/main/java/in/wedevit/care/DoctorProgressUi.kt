package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stateless timeline; parent owns scrolling and the distinct report-detail route. */
@Composable fun DoctorProgressTimeline(reports: List<DoctorProgressReport>, onOpenReport: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("doctor-progress-timeline"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reported updates", fontSize = 23.sp, fontWeight = FontWeight.Medium, color = Ink, fontFamily = SaharaTitleFont)
        Text("A history of patient and family reports, not a clinical assessment.", color = DoctorCardSub, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
        reports.firstOrNull()?.let { Text("Times shown in ${it.zoneId}", color = DoctorCardFaint, style = MaterialTheme.typography.bodySmall) }
        if (reports.isEmpty()) Text("No reported updates recorded.", color = DoctorCardSub, modifier = Modifier.padding(vertical = 20.dp))
        reports.forEachIndexed { index, report ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(48.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = if (report.isLatest) Rust else SaharaApricot, contentColor = if (report.isLatest) Paper else Ink, modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Text(report.date.dayOfMonth.toString(), fontSize = 19.sp, fontWeight = FontWeight.SemiBold) } }
                    Text(report.date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)), style = MaterialTheme.typography.labelSmall, color = DoctorCardSub, modifier = Modifier.padding(top = 5.dp))
                    if (index != reports.lastIndex) Box(Modifier.padding(top = 8.dp).width(2.dp).weight(1f).background(Rust.copy(alpha = .18f)))
                }
                Surface(onClick = { onOpenReport(report.id) }, shape = RoundedCornerShape(22.dp), color = if (report.isLatest) SaharaSoftGold else if (report.source == SampleReportSource.FAMILY) SaharaLavender else SaharaSoftBlue, contentColor = Ink, modifier = Modifier.weight(1f).testTag("progress-report-${report.id}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(if (report.source == SampleReportSource.PATIENT) Icons.Outlined.PersonOutline else Icons.Outlined.PeopleOutline, null, tint = Rust, modifier = Modifier.size(18.dp)); Text(if (report.source == SampleReportSource.PATIENT) "Patient reported" else "Family reported", style = MaterialTheme.typography.labelMedium) }
                        Text(if (report.isLatest) "Latest update" else "Earlier update", fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                        Text(progressTimestamp(report.at, report.zoneId), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        Text(report.text, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                        TextButton(onClick = { onOpenReport(report.id) }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("progress-details-${report.id}")) { Text("More details", color = Rust, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(7.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Rust, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
        Text("Dates reflect when each update was recorded.", style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint)
    }
}

/** Read-only historical report, never populated from a different report's current state. */
@Composable fun DoctorProgressReportDetail(report: DoctorProgressReport, onOpenVisits: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("progress-report-detail-${report.id}"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("At this update", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Ink, fontFamily = SaharaTitleFont)
        Text(progressTimestamp(report.at, report.zoneId) + " · " + if (report.source == SampleReportSource.PATIENT) "Patient reported" else "Family reported", color = Rust, style = MaterialTheme.typography.bodyMedium)
        Text("Times shown in ${report.zoneId}", color = DoctorCardFaint, style = MaterialTheme.typography.bodySmall)
        ProgressDetailCard("Reported update", Icons.Outlined.Notes, SaharaSoftGold) { Text(report.text, lineHeight = 25.sp) }
        ProgressDetailCard("Symptoms and condition", Icons.Outlined.HealthAndSafety, Paper) {
            if (report.symptoms.isNullOrBlank() && report.clinicalAssessment.isNullOrBlank()) Text("No symptom update or clinical assessment was recorded in this report. This does not mean there were no symptoms.", lineHeight = 23.sp)
            else { report.symptoms?.takeIf { it.isNotBlank() }?.let { Text("Reported symptoms: $it", lineHeight = 23.sp) }; report.clinicalAssessment?.takeIf { it.isNotBlank() }?.let { Text("Recorded clinical assessment: $it", lineHeight = 23.sp) } }
        }
        if (report.questions.isNotEmpty()) ProgressDetailCard("Questions and concerns", Icons.Outlined.QuestionAnswer, SaharaSoftBlue) { report.questions.forEach { Text(it, lineHeight = 23.sp) }; Text("Specific questions were not recorded.", style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint) }
        if (report.familySupport.isNotEmpty()) ProgressDetailCard("Family coordination", Icons.Outlined.PeopleOutline, SaharaLavender) { report.familySupport.forEach { Text(it, lineHeight = 23.sp) }; Text("No transport arrangement or booking was recorded.", style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint) }
        if (report.medicineActivity.isNotEmpty()) ProgressDetailCard("Medicine activity for this day", Icons.Outlined.Medication, Paper) {
            Text("As known at this update. Separate activity scheduled on the same day—not information supplied by this update. Later logs are excluded. Not verified ingestion or prescribing instructions.", style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp, color = DoctorCardSub)
            report.medicineActivity.forEach { dose ->
                HorizontalDivider(color = Ink.copy(alpha = .1f))
                Text(dose.medicineLabel, fontWeight = FontWeight.SemiBold)
                Text(dose.status, color = Rust, style = MaterialTheme.typography.labelMedium)
                Text("Planned: ${progressTimestamp(dose.scheduledAt, report.zoneId)}", style = MaterialTheme.typography.bodySmall)
                Text("Reported taken: ${dose.reportedTakenAt?.let { progressTimestamp(it, report.zoneId) } ?: "Not reported"}", style = MaterialTheme.typography.bodySmall)
                Text("Logged: ${dose.loggedAt?.let { progressTimestamp(it, report.zoneId) } ?: "Not logged"}", style = MaterialTheme.typography.bodySmall)
                dose.source?.let { Text(if (it == SampleReportSource.PATIENT) "Patient report" else "Family report", style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint) }
            }
        }
        report.linkedDiscussion?.let { discussion -> ProgressDetailCard("Linked discussion", Icons.Outlined.Forum, SaharaApricot) { Text("Report linked to this discussion. Author and date are not recorded.", style = MaterialTheme.typography.bodySmall, color = DoctorCardSub); Text(discussion.text, lineHeight = 24.sp) } }
        Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Ink.copy(alpha = .1f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Other information", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                val unknowns = buildList { if (report.questions.isEmpty()) add("specific questions"); if (report.familySupport.isEmpty()) add("family arrangements"); if (report.linkedDiscussion == null) add("a linked discussion"); add("a linked visit") }
                Text("This update does not record ${unknowns.joinToString(", ")}. Visit history is separate.", color = DoctorCardSub, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
                if (report.medicineActivity.isEmpty()) Text("No related medicine activity was scheduled on this report date.", color = DoctorCardSub, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
                TextButton(onClick = onOpenVisits, modifier = Modifier.heightIn(min = 48.dp).testTag("progress-open-visits")) { Icon(Icons.Outlined.CalendarMonth, null, tint = Rust, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open patient visits", color = Rust) }
            }
        }
    }
}

@Composable private fun ProgressDetailCard(title: String, icon: ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, contentColor = Ink, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Ink.copy(alpha = .08f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Rust, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, modifier = Modifier.a11yHeading()) }; content() }
    }
}
private fun progressTimestamp(at: Long, zoneId: String): String = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.of(zoneId)).format(Instant.ofEpochMilli(at))
