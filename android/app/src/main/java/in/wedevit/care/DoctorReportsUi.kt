package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stateless: route owner keys query/category to the selected patient and owns vertical scrolling. */
@Composable fun DoctorReportsBrowser(
    patientName: String,
    reports: List<DoctorReportSummary>,
    query: String,
    category: DoctorReportCategory?,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (DoctorReportCategory?) -> Unit,
    onOpenReport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = DoctorReportsData.filter(reports, query, category)
    val counts = DoctorReportsData.counts(reports)
    Column(modifier.fillMaxWidth().testTag("reports-browser"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(color = SaharaApricot, contentColor = Ink, shape = RoundedCornerShape(25.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Surface(color = Rust, contentColor = Color.White, shape = RoundedCornerShape(17.dp)) { Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(14.dp).size(30.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Reports", fontSize = 25.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text("$patientName's records", fontSize = 14.sp)
                    Text("${reports.size} ${if (reports.size == 1) "summary" else "summaries"} · ${reports.count { it.hasAttachedFile }} attached files", style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                }
            }
        }
        OutlinedTextField(value = query, onValueChange = { onQueryChange(it.take(200)) }, label = { Text("Search report summaries") }, leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Rust) }, trailingIcon = if (query.isNotBlank()) { { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, "Clear search") } } } else null, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reports-search"), shape = RoundedCornerShape(17.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rust, focusedLabelColor = Rust, cursorColor = Rust))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Browse categories", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink, fontFamily = SaharaTitleFont)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportFolder("All summaries", reports.size, category == null, "All", onClick = { onCategoryChange(null) })
                DoctorReportCategory.entries.forEach { item -> ReportFolder(item.label, counts.getValue(item), category == item, item.name, onClick = { onCategoryChange(item) }) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Report summaries", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f), fontFamily = SaharaTitleFont)
            Text("${visible.size} shown", style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint)
        }
        if (visible.isEmpty()) Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Ink.copy(alpha = .1f)), modifier = Modifier.fillMaxWidth().testTag("reports-empty")) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.FindInPage, null, tint = Rust, modifier = Modifier.size(30.dp))
                Text(if (query.isNotBlank()) "No matching summaries" else "No summaries in this category", fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                Text("Try another category or clear your search. This patient has text summaries only, not attached files.", style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                TextButton(onClick = { onQueryChange(""); onCategoryChange(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show all summaries", color = Rust) }
            }
        }
        visible.forEach { report ->
            Surface(onClick = { onOpenReport(report.id) }, color = Paper, contentColor = Ink, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Ink.copy(alpha = .1f)), modifier = Modifier.fillMaxWidth().testTag("report-open-${report.id}")) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = SaharaSoftGold, shape = RoundedCornerShape(13.dp)) { Icon(Icons.Outlined.Description, null, tint = Rust, modifier = Modifier.padding(11.dp).size(24.dp)) }
                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(report.title, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text(report.category.label, style = MaterialTheme.typography.bodySmall, color = DoctorCardFaint) }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Open report summary", tint = Rust, modifier = Modifier.size(20.dp))
                    }
                    Text(report.summary, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                    Text("Summary only · No file attached", color = Rust, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text("Original documents, dates and authors have not been supplied.", style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, lineHeight = 18.sp)
    }
}

@Composable private fun ReportFolder(label: String, count: Int, selected: Boolean, tag: String, onClick: () -> Unit) {
    val categorySelected = selected
    Surface(onClick = onClick, color = if (selected) Rust else SaharaSoftGold, contentColor = if (selected) Color.White else Ink, shape = RoundedCornerShape(18.dp), border = if (selected) null else BorderStroke(1.dp, Rust.copy(alpha = .13f)), modifier = Modifier.widthIn(min = 126.dp).heightIn(min = 96.dp).testTag("reports-category-$tag").semantics { this.selected = categorySelected }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Folder, null, Modifier.size(25.dp)); Spacer(Modifier.width(12.dp)); Text(count.toString(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
            Text(if (count == 1) "1 summary" else "$count summaries", fontSize = 12.sp, color = if (selected) Color.White.copy(alpha = .85f) else DoctorCardFaint)
        }
    }
}

@Composable fun DoctorReportDetail(report: DoctorReportSummary, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("report-detail-${report.id}"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = SaharaApricot, shape = RoundedCornerShape(23.dp), contentColor = Ink, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Icon(Icons.Outlined.Description, null, tint = Rust, modifier = Modifier.size(30.dp)); Text(report.title, fontSize = 25.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text("${report.patientName} · ${report.category.label}", style = MaterialTheme.typography.bodyMedium); Text("Text summary · No original file attached", color = RustText, style = MaterialTheme.typography.labelMedium) } }
        Surface(color = Paper, shape = RoundedCornerShape(22.dp), contentColor = Ink, border = BorderStroke(1.dp, Ink.copy(alpha = .1f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Summary", fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                Text(report.summary, lineHeight = 25.sp, modifier = Modifier.testTag("report-exact-summary"))
                HorizontalDivider(color = Ink.copy(alpha = .1f))
                Text("Source: ${report.sourceLabel}", style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                Text("Author: ${report.author?.takeIf { it.isNotBlank() } ?: "Not recorded"}", style = MaterialTheme.typography.bodySmall)
                Text("Document date: Not recorded", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("This is a text summary, not a verified medical report. There is no original document to download, share or interpret.", style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub, lineHeight = 22.sp)
    }
}
