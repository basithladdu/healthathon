package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/**
 * "Rahul at a glance": a short, read-only register on Today for patient and family. Every line comes from entries already on the
 * phone and every row opens the page it names (trailing chevron). A read-only family member sees the same rows; [onAddVisit] is null for them.
 */
@Composable fun PatientSummaryCard(vm: CareModel, onSymptoms: () -> Unit, onVisit: (CareItem?) -> Unit, onAddVisit: (() -> Unit)?, onGoals: (CareVersion?) -> Unit, onCarePlan: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle()
    val name = people.firstOrNull { it.id == selected }?.name.orEmpty().ifBlank { t("Care", "देखभाल") }
    val today = LocalDate.now()
    val store = remember(vm.storageNamespace) { CarePlanStore(context.applicationContext, vm.storageNamespace) }
    val plans = remember(selected) { runCatching { store.load(selected).ectprs }.getOrDefault(emptyList()) }
    val summary = remember(items, versions, selected, plans, today) { CarePatientSummaryRules.build(items, versions, selected, plans, today) }
    val visit = summary.nextVisit
    val visitLine = when {
        visit != null -> CareTodayRules.whenLabel(visit.due, today, hindi) + visit.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        onAddVisit != null -> t("None planned · Add a visit", "कोई तय नहीं · मुलाक़ात जोड़ें")
        else -> CarePatientSummaryRules.nextVisit(null, hindi)
    }
    val note = summary.latestNote
    // Four rows, always open; each names its page. Medicines have their own section on Today, so they are not repeated here.
    // A solid teal header strip with the patient's initial; each row below sits on its own section's wash.
    Surface(Modifier.fillMaxWidth().saharaAppear().testTag("patient-summary"), shape = RoundedCornerShape(SaharaRadius.Lg), color = SaharaColors.Surface, border = BorderStroke(1.5.dp, SaharaTone.Teal.solid)) { Column {
        Row(Modifier.fillMaxWidth().background(SaharaTone.Teal.solid).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = SaharaColors.Surface, modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SaharaTone.Teal.ink) } }
            Spacer(Modifier.width(12.dp))
            Text(t("$name at a glance", "$name एक नज़र में"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SaharaTone.Teal.onSolid, maxLines = 2, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlanceRow(Icons.Default.Healing, SaharaSection.Symptoms, t("Pain and symptoms", "दर्द और लक्षण"), summary.symptoms.firstOrNull()?.let { CarePatientSummaryRules.symptomLine(it, hindi) } ?: CarePatientSummaryRules.noSymptoms(hindi), "summary-symptoms", onSymptoms)
        GlanceRow(Icons.Default.Event, SaharaSection.Visits, t("Next visit", "अगली मुलाक़ात"), visitLine, "summary-visit") { if (visit == null && onAddVisit != null) onAddVisit() else onVisit(visit) }
        GlanceRow(Icons.Default.Description, SaharaSection.GoalsOfCare, t("Goals of care", "देखभाल के लक्ष्य"), CarePatientSummaryRules.noteLine(note, note?.let { date(it.published) }.orEmpty(), hindi), "today-goals-of-care") { onGoals(note) }
        GlanceRow(Icons.Default.LocalHospital, SaharaSection.Emergency, t("Emergency plan", "आपातकालीन योजना"), CarePatientSummaryRules.planLine(summary.plan, hindi), "summary-plan", onCarePlan)
        }
    } }
}

/** The last row on Today: the one way into every other care page (the Care tab). */
@Composable fun TodayAllCareTools(onOpen: () -> Unit) {
    val t = careText()
    SaharaRegister(tone = SaharaTone.Rust) { SaharaListRow(t("All care tools", "देखभाल के सभी साधन"), Modifier.testTag("today-all-care-tools"), detail = t("Medicines, visits, documents, labs and more", "दवाइयाँ, मुलाक़ातें, कागज़ात, जाँच और बाक़ी सब"),
        icon = Icons.Default.Apps, tone = SaharaTone.Rust, onClick = onOpen) }
}

/** One glance row: its section's wash with a solid edge, a solid icon chip, the title, today's line and a chevron; the whole row opens its page. */
@Composable private fun GlanceRow(icon: ImageVector, tone: SaharaTone, title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SaharaRadius.Md)
    SaharaListRow(title, Modifier.clip(shape).background(tone.wash).border(1.dp, tone.solid, shape).testTag(tag), detail = subtitle, icon = icon, tone = tone, onClick = onClick)
}
