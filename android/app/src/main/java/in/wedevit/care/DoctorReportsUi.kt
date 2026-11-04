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
) { val t = careText();
    val visible = DoctorReportsData.filter(reports, query, category)
    val counts = DoctorReportsData.counts(reports)
    Column(modifier.fillMaxWidth().testTag("reports-browser"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = SaharaApricot, contentColor = Ink, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Rust, contentColor = Paper, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(12.dp).size(28.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("Reports", "रिपोर्टें"), Modifier.a11yHeading(), fontSize = 24.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text(t("$patientName's records", "$patientName के रिकॉर्ड"), fontSize = 15.sp, color = DoctorCardInk)
                    Text("${reports.size} ${if (reports.size == 1) "summary" else "summaries"} · ${reports.count { it.hasAttachedFile }} attached files", fontSize = 14.sp, color = DoctorCardSub)
                }
            }
        }
        OutlinedTextField(value = query, onValueChange = { onQueryChange(it.take(200)) }, label = { Text(t("Search report summaries", "रिपोर्ट के सार खोजें")) }, leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Rust) }, trailingIcon = if (query.isNotBlank()) { { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, t("Clear search", "खोज मिटाएँ")) } } } else null, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reports-search"), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rust, focusedLabelColor = Rust, cursorColor = Rust))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Browse categories", "श्रेणियाँ देखें"), Modifier.a11yHeading(), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink, fontFamily = SaharaTitleFont)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportFolder(t("All summaries", "सभी सार"), reports.size, category == null, "All", onClick = { onCategoryChange(null) })
                DoctorReportCategory.entries.forEach { item -> ReportFolder(item.label, counts.getValue(item), category == item, item.name, onClick = { onCategoryChange(item) }) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Report summaries", "रिपोर्ट के सार"), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont)
            Surface(color = SaharaSoftGold, shape = RoundedCornerShape(50)) { Text("${visible.size} shown", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk) }
        }
        if (visible.isEmpty()) Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("reports-empty")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.FindInPage, null, tint = Rust, modifier = Modifier.size(30.dp))
                Text(if (query.isNotBlank()) t("No matching summaries", "कोई सार नहीं मिला") else t("No summaries in this category", "इस श्रेणी में कोई सार नहीं"), fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                Text(t("Try another category or clear your search.", "दूसरी श्रेणी चुनें या खोज मिटाएँ।"), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardSub)
                TextButton(onClick = { onQueryChange(""); onCategoryChange(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Show all summaries", "सभी सार दिखाएँ"), color = Rust) }
            }
        }
        visible.forEach { report ->
            Surface(onClick = { onOpenReport(report.id) }, color = Paper, contentColor = Ink, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("report-open-${report.id}")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = SaharaSoftGold, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Description, null, tint = Rust, modifier = Modifier.padding(10.dp).size(24.dp)) }
                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(report.title, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text(report.category.label, fontSize = 14.sp, color = DoctorCardSub) }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Open report summary", "रिपोर्ट का सार खोलें"), tint = Rust, modifier = Modifier.size(20.dp))
                    }
                    Text(report.summary, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                    Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(50)) { Text(t("Summary only · No file attached", "सिर्फ़ सार · कोई फ़ाइल नहीं"), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = DoctorStatusRest, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

@Composable private fun ReportFolder(label: String, count: Int, selected: Boolean, tag: String, onClick: () -> Unit) {
    val categorySelected = selected
    Surface(onClick = onClick, color = if (selected) Rust else SaharaSoftGold, contentColor = if (selected) Color.White else Ink, shape = RoundedCornerShape(20.dp), border = if (selected) null else BorderStroke(1.dp, DoctorHairline), modifier = Modifier.widthIn(min = 126.dp).heightIn(min = 88.dp).testTag("reports-category-$tag").semantics { this.selected = categorySelected }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Folder, null, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Text(count.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
            Text(if (count == 1) "1 summary" else "$count summaries", fontSize = 14.sp, color = if (selected) Color.White else DoctorCardSub)
        }
    }
}

@Composable fun DoctorReportDetail(report: DoctorReportSummary, modifier: Modifier = Modifier) { val t = careText();
    Column(modifier.fillMaxWidth().testTag("report-detail-${report.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = SaharaApricot, shape = RoundedCornerShape(20.dp), contentColor = Ink, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Description, null, tint = Rust, modifier = Modifier.size(28.dp)); Text(report.title, Modifier.a11yHeading(), fontSize = 24.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text("${report.patientName} · ${report.category.label}", fontSize = 16.sp); Surface(color = Paper, shape = RoundedCornerShape(50)) { Text(t("Text summary · No original file attached", "लिखा सार · कोई मूल फ़ाइल नहीं"), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = RustText, fontSize = 14.sp, fontWeight = FontWeight.Medium) } } }
        Surface(color = Paper, shape = RoundedCornerShape(20.dp), contentColor = Ink, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Summary", "सार"), Modifier.a11yHeading(), fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                Text(report.summary, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.testTag("report-exact-summary"))
                HorizontalDivider(color = DoctorHairline)
                Text(t("Source: ${report.sourceLabel}", "स्रोत: ${report.sourceLabel}"), fontSize = 14.sp, color = DoctorCardSub)
                Text("Author: ${report.author?.takeIf { it.isNotBlank() } ?: t("Not recorded", "दर्ज नहीं")}", fontSize = 14.sp, color = DoctorCardSub)
                Text(t("Document date: Not recorded", "कागज़ की तारीख़: दर्ज नहीं"), fontSize = 14.sp, color = DoctorCardSub)
            }
        }
    }
}
