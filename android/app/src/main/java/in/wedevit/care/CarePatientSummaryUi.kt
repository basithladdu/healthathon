package `in`.wedevit.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/**
 * "Rahul at a glance": a short, read-only summary at the top of Today for patient and family. Every number comes from entries already
 * on the phone. It only opens existing screens, so a read-only family member sees the same card with no edit action.
 */
@Composable fun PatientSummaryCard(vm: CareModel, onSymptoms: () -> Unit, onMedicines: () -> Unit, onVisit: (CareItem) -> Unit, onRead: (CareVersion) -> Unit, onCarePlan: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle()
    val name = people.firstOrNull { it.id == selected }?.name.orEmpty().ifBlank { t("Care", "देखभाल") }
    val today = LocalDate.now()
    val store = remember(vm.storageNamespace) { CarePlanStore(context.applicationContext, vm.storageNamespace) }
    val plans = remember(selected) { runCatching { store.load(selected).ectprs }.getOrDefault(emptyList()) }
    val summary = remember(items, versions, selected, plans, today) { CarePatientSummaryRules.build(items, versions, selected, plans, today) }
    var open by rememberSaveable(selected) { mutableStateOf(false) }
    var symOpen by rememberSaveable(selected) { mutableStateOf(false) }; var medOpen by rememberSaveable(selected) { mutableStateOf(false) }
    var visitOpen by rememberSaveable(selected) { mutableStateOf(false) }; var noteOpen by rememberSaveable(selected) { mutableStateOf(false) }
    var planOpen by rememberSaveable(selected) { mutableStateOf(false) }
    val expandedWord = t("Expanded", "खुला"); val collapsedWord = t("Collapsed", "बंद")
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, modifier = Modifier.fillMaxWidth().testTag("patient-summary")) {
        Column(Modifier.padding(vertical = 6.dp, horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { open = !open }.padding(vertical = 8.dp).semantics(mergeDescendants = true) { heading(); stateDescription = if (open) expandedWord else collapsedWord },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Heading(t("$name at a glance", "$name एक नज़र में"), 22)
                    if (!open) Text(CarePatientSummaryRules.collapsed(summary, hindi), Modifier.padding(top = 4.dp), fontSize = 14.sp, color = DoctorCardSub, maxLines = 3)
                }
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) t("Hide details", "ब्योरा छिपाएँ") else t("Show details", "ब्योरा दिखाएँ"), tint = Rust)
            }
            if (open) {
                val topSymptom = summary.symptoms.firstOrNull()
                GlanceSection(Icons.Default.Healing, t("Pain and symptoms", "दर्द और लक्षण"), topSymptom?.let { CarePatientSummaryRules.symptomLine(it, hindi) } ?: CarePatientSummaryRules.noSymptoms(hindi), symOpen, { symOpen = !symOpen }) {
                    summary.symptoms.forEach { GlanceRow(CarePatientSummaryRules.symptomLine(it, hindi), onSymptoms) }
                    GlanceRow(t("See symptom notes", "तबीयत के नोट देखें"), onSymptoms, action = true)
                }
                GlanceSection(Icons.Default.Medication, t("Medicines", "दवाइयाँ"), CarePatientSummaryRules.medicineStatus(summary.medicineTotal, summary.medicineTicked, hindi), medOpen, { medOpen = !medOpen }) {
                    GlanceRow(CarePatientSummaryRules.medicineCount(summary.medicineTotal, hindi), onMedicines)
                    GlanceRow(t("See medicines", "दवाइयाँ देखें"), onMedicines, action = true)
                }
                GlanceSection(Icons.Default.Event, t("Next visit", "अगली मुलाक़ात"), CarePatientSummaryRules.nextVisit(summary.nextVisit, hindi), visitOpen, { visitOpen = !visitOpen }) {
                    summary.nextVisit?.let { v -> GlanceRow("${v.title.ifBlank { t("Visit", "मुलाक़ात") }} · ${CarePatientSummaryRules.visitDay(v, hindi)}", { onVisit(v) }) }
                    GlanceRow(t("See calendar", "कैलेंडर देखें"), { onVisit(summary.nextVisit ?: CareItem(personId = selected, kind = "visit", title = "")) }, action = true)
                }
                GlanceSection(Icons.Default.Description, t("Latest note from the doctor", "डॉक्टर का सबसे नया नोट"), CarePatientSummaryRules.noteLine(summary.latestNote, summary.latestNote?.let { date(it.published) }.orEmpty(), hindi), noteOpen, { noteOpen = !noteOpen }) {
                    summary.latestNote?.let { n -> GlanceRow(t("Read", "पढ़ें"), { onRead(n) }, action = true) } ?: GlanceRow(CarePatientSummaryRules.noteLine(null, "", hindi), {})
                }
                GlanceSection(Icons.Default.LocalHospital, t("Emergency care plan", "आपातकालीन देखभाल योजना"), CarePatientSummaryRules.planLine(summary.plan, hindi), planOpen, { planOpen = !planOpen }) {
                    GlanceRow(t("See emergency plan", "आपातकालीन योजना देखें"), onCarePlan, action = true)
                }
            }
        }
    }
}

@Composable private fun GlanceSection(icon: ImageVector, title: String, subtitle: String, open: Boolean, toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val t = careText(); val expandedWord = t("Expanded", "खुला"); val collapsedWord = t("Collapsed", "बंद")
    HorizontalDivider(color = Rust.copy(alpha = 0.15f))
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = toggle).padding(vertical = 6.dp).semantics(mergeDescendants = true) { stateDescription = if (open) expandedWord else collapsedWord },
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Rust); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(subtitle, fontSize = 13.sp, color = DoctorCardSub, maxLines = 3) }
        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
    }
    if (open) Column(Modifier.padding(start = 36.dp), content = content)
}

@Composable private fun GlanceRow(text: String, onClick: () -> Unit, action: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (action) FontWeight.Bold else FontWeight.Normal, color = if (action) RustText else MaterialTheme.colorScheme.onSurface)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = Rust)
    }
}
