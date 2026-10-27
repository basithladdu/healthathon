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
@Composable fun DoctorProgressTimeline(reports: List<DoctorProgressReport>, onOpenReport: (String) -> Unit, modifier: Modifier = Modifier) { val t = careText();
    Column(modifier.fillMaxWidth().testTag("doctor-progress-timeline"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Reported updates", "बताए अपडेट"), fontSize = 23.sp, fontWeight = FontWeight.Medium, color = Ink, fontFamily = SaharaTitleFont)
        Text(t("A history of patient and family reports, not a clinical assessment.", "मरीज़ और परिवार की सूचनाओं का इतिहास, क्लिनिकल आकलन नहीं।"), color = DoctorCardSub, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
        reports.firstOrNull()?.let { Text(t("Times shown in ${it.zoneId}", "समय ${it.zoneId} के अनुसार"), color = DoctorCardFaint, style = MaterialTheme.typography.bodySmall) }
        if (reports.isEmpty()) Text(t("No reported updates recorded.", "कोई बताया अपडेट दर्ज नहीं।"), color = DoctorCardSub, modifier = Modifier.padding(vertical = 20.dp))
        reports.forEachIndexed { index, report ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(48.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = if (report.isLatest) Rust else SaharaApricot, contentColor = if (report.isLatest) Paper else Ink, modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Text(report.date.dayOfMonth.toString(), fontSize = 19.sp, fontWeight = FontWeight.SemiBold) } }
                    Text(report.date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)), style = MaterialTheme.typography.labelSmall, color = DoctorCardSub, modifier = Modifier.padding(top = 5.dp))
                    if (index != reports.lastIndex) Box(Modifier.padding(top = 8.dp).width(2.dp).weight(1f).background(Rust.copy(alpha = .18f)))
                }
                Surface(onClick = { onOpenReport(report.id) }, shape = RoundedCornerShape(22.dp), color = if (report.isLatest) SaharaSoftGold else if (report.source == SampleReportSource.FAMILY) SaharaLavender else SaharaSoftBlue, contentColor = Ink, modifier = Modifier.weight(1f).testTag("progress-report-${report.id}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(if (report.source == SampleReportSource.PATIENT) Icons.Outlined.PersonOutline else Icons.Outlined.PeopleOutline, null, tint = Rust, modifier = Modifier.size(18.dp)); Text(if (report.source == SampleReportSource.PATIENT) t("Patient reported", "मरीज़ ने बताया") else t("Family reported", "परिवार ने बताया"), style = MaterialTheme.typography.labelMedium) }
                        Text(if (report.isLatest) t("Latest update", "नया अपडेट") else t("Earlier update", "पुराना अपडेट"), fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                        Text(progressTimestamp(report.at, report.zoneId), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        Text(report.text, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                        TextButton(onClick = { onOpenReport(report.id) }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("progress-details-${report.id}")) { Text(t("More details", "और जानकारी"), color = Rust, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(7.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Rust, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
        Text(t("Dates reflect when each update was recorded.", "तारीख़ें बताती हैं कि हर अपडेट कब दर्ज हुआ।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint)
    }
}

/** Read-only historical report, never populated from a different report's current state. */
@Composable fun DoctorProgressReportDetail(report: DoctorProgressReport, onOpenVisits: () -> Unit, modifier: Modifier = Modifier) { val t = careText();
    Column(modifier.fillMaxWidth().testTag("progress-report-detail-${report.id}"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(t("At this update", "इस अपडेट में"), fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Ink, fontFamily = SaharaTitleFont)
        Text(progressTimestamp(report.at, report.zoneId) + " · " + if (report.source == SampleReportSource.PATIENT) t("Patient reported", "मरीज़ ने बताया") else t("Family reported", "परिवार ने बताया"), color = Rust, style = MaterialTheme.typography.bodyMedium)
        Text(t("Times shown in ${report.zoneId}", "समय ${report.zoneId} के अनुसार"), color = DoctorCardFaint, style = MaterialTheme.typography.bodySmall)
        ProgressDetailCard(t("Reported update", "बताया अपडेट"), Icons.Outlined.Notes, SaharaSoftGold) { Text(report.text, lineHeight = 25.sp) }
        ProgressDetailCard(t("Symptoms and condition", "लक्षण और हालत"), Icons.Outlined.HealthAndSafety, Paper) {
            if (report.symptoms.isNullOrBlank() && report.clinicalAssessment.isNullOrBlank()) Text(t("No symptom update or clinical assessment was recorded in this report. This does not mean there were no symptoms.", "इस सूचना में लक्षण या क्लिनिकल आकलन दर्ज नहीं। इसका मतलब यह नहीं कि कोई लक्षण नहीं थे।"), lineHeight = 23.sp)
            else { report.symptoms?.takeIf { it.isNotBlank() }?.let { Text(t("Reported symptoms: $it", "बताए लक्षण: $it"), lineHeight = 23.sp) }; report.clinicalAssessment?.takeIf { it.isNotBlank() }?.let { Text(t("Recorded clinical assessment: $it", "दर्ज क्लिनिकल आकलन: $it"), lineHeight = 23.sp) } }
        }
        if (report.questions.isNotEmpty()) ProgressDetailCard(t("Questions and concerns", "सवाल और चिंताएँ"), Icons.Outlined.QuestionAnswer, SaharaSoftBlue) { report.questions.forEach { Text(it, lineHeight = 23.sp) }; Text(t("Specific questions were not recorded.", "कोई ख़ास सवाल दर्ज नहीं।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint) }
        if (report.familySupport.isNotEmpty()) ProgressDetailCard(t("Family coordination", "परिवार का तालमेल"), Icons.Outlined.PeopleOutline, SaharaLavender) { report.familySupport.forEach { Text(it, lineHeight = 23.sp) }; Text(t("No transport arrangement or booking was recorded.", "आने-जाने का इंतज़ाम या बुकिंग दर्ज नहीं।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint) }
        if (report.medicineActivity.isNotEmpty()) ProgressDetailCard(t("Medicine activity for this day", "इस दिन की दवा गतिविधि"), Icons.Outlined.Medication, Paper) {
            Text(t("As known at this update. Separate activity scheduled on the same day—not information supplied by this update. Later logs are excluded. Not verified ingestion or prescribing instructions.", "इस अपडेट के समय की जानकारी। उसी दिन तय अलग गतिविधि—यह इस अपडेट ने नहीं बताई। बाद की एंट्री शामिल नहीं। दवा लेना या लिखने के निर्देश पुष्ट नहीं।"), style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp, color = DoctorCardSub)
            report.medicineActivity.forEach { dose ->
                HorizontalDivider(color = Ink.copy(alpha = .1f))
                Text(dose.medicineLabel, fontWeight = FontWeight.SemiBold)
                Text(dose.status, color = Rust, style = MaterialTheme.typography.labelMedium)
                Text(t("Planned: ${progressTimestamp(dose.scheduledAt, report.zoneId)}", "तय: ${progressTimestamp(dose.scheduledAt, report.zoneId)}"), style = MaterialTheme.typography.bodySmall)
                Text(t("Reported taken: ", "लेने का बताया समय: ") + "${dose.reportedTakenAt?.let { progressTimestamp(it, report.zoneId) } ?: t("Not reported", "सूचना नहीं")}", style = MaterialTheme.typography.bodySmall)
                Text(t("Logged: ", "दर्ज: ") + "${dose.loggedAt?.let { progressTimestamp(it, report.zoneId) } ?: t("Not logged", "दर्ज नहीं")}", style = MaterialTheme.typography.bodySmall)
                dose.source?.let { Text(if (it == SampleReportSource.PATIENT) t("Patient report", "मरीज़ की सूचना") else t("Family report", "परिवार की सूचना"), style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint) }
            }
        }
        report.linkedDiscussion?.let { discussion -> ProgressDetailCard(t("Linked discussion", "जुड़ी चर्चा"), Icons.Outlined.Forum, SaharaApricot) { Text(t("Report linked to this discussion. Author and date are not recorded.", "सूचना इस चर्चा से जुड़ी है। लेखक और तारीख़ दर्ज नहीं।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub); Text(discussion.text, lineHeight = 24.sp) } }
        Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Ink.copy(alpha = .1f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Other information", "और जानकारी"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                val unknowns = buildList { if (report.questions.isEmpty()) add(t("specific questions", "ख़ास सवाल")); if (report.familySupport.isEmpty()) add(t("family arrangements", "परिवार के इंतज़ाम")); if (report.linkedDiscussion == null) add(t("a linked discussion", "जुड़ी चर्चा")); add(t("a linked visit", "जुड़ी विज़िट")) }
                Text(t("This update does not record ${unknowns.joinToString(", ")}. Visit history is separate.", "इस अपडेट में ${unknowns.joinToString(", ")} दर्ज नहीं। विज़िट का इतिहास अलग है।"), color = DoctorCardSub, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
                if (report.medicineActivity.isEmpty()) Text(t("No related medicine activity was scheduled on this report date.", "इस सूचना की तारीख़ पर कोई जुड़ी दवा गतिविधि तय नहीं थी।"), color = DoctorCardSub, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
                TextButton(onClick = onOpenVisits, modifier = Modifier.heightIn(min = 48.dp).testTag("progress-open-visits")) { Icon(Icons.Outlined.CalendarMonth, null, tint = Rust, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Open patient visits", "मरीज़ की विज़िट खोलें"), color = Rust) }
            }
        }
    }
}

@Composable private fun ProgressDetailCard(title: String, icon: ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, contentColor = Ink, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Ink.copy(alpha = .08f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Rust, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont) }; content() }
    }
}
private fun progressTimestamp(at: Long, zoneId: String): String = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.of(zoneId)).format(Instant.ofEpochMilli(at))
