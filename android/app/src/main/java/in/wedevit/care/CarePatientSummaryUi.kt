package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    // A plain register, always open: symptoms and the emergency plan. Medicines, visits and the latest note each have
    // their own section on Today ([onMedicines], [onVisit], [onRead] stay for callers), so they are not repeated here.
    Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear().testTag("patient-summary")) {
        Column(Modifier.saharaContentSize().padding(top = 12.dp, start = 16.dp, end = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = SaharaTone.Teal.wash, modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = SaharaTone.Teal.ink) } }
                Spacer(Modifier.width(12.dp))
                Text(t("$name at a glance", "$name एक नज़र में"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 2, modifier = Modifier.weight(1f).semantics { heading() })
            }
            GlanceRow(Icons.Default.Healing, SaharaTone.Rose, t("Pain and symptoms", "दर्द और लक्षण"), summary.symptoms.firstOrNull()?.let { CarePatientSummaryRules.symptomLine(it, hindi) } ?: CarePatientSummaryRules.noSymptoms(hindi), "summary-symptoms", onSymptoms)
            GlanceRow(Icons.Default.LocalHospital, SaharaTone.Marigold, t("Emergency care plan", "आपातकालीन देखभाल योजना"), CarePatientSummaryRules.planLine(summary.plan, hindi), "summary-plan", onCarePlan)
        }
    }
}

/** Today's page shortcuts, two by two, with full-word labels (the same names as on the Care tab). SOS has its own bar above. */
@Composable fun TodayQuickActions(onCalendar: () -> Unit, onSymptoms: () -> Unit, onLabs: () -> Unit, onCommunity: () -> Unit) {
    val t = careText()
    val actions = listOf(
        QuickAction(t("Calendar", "कैलेंडर"), Icons.Default.CalendarMonth, SaharaTone.Indigo, "shortcut-calendar", onCalendar),
        QuickAction(t("Symptoms", "लक्षण"), Icons.Default.MonitorHeart, SaharaTone.Rose, "symptom-tracker-entry", onSymptoms),
        QuickAction(t("Lab history", "जाँच रिपोर्टें"), Icons.Default.Science, SaharaTone.Marigold, "shortcut-labs", onLabs),
        QuickAction(t("Community", "समुदाय"), Icons.Default.Groups, SaharaTone.Leaf, "shortcut-community", onCommunity),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    ActionCard(action.tone.wash, Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp).testTag(action.tag), action.onClick) {
                        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = action.tone.solid) { Icon(action.icon, null, Modifier.padding(8.dp).size(22.dp), tint = action.tone.onSolid) }
                            Spacer(Modifier.width(10.dp))
                            Text(action.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.weight(1f),
                                style = LocalTextStyle.current.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading, hyphens = androidx.compose.ui.text.style.Hyphens.None))
                        }
                    }
                }
            }
        }
    }
}

private class QuickAction(val label: String, val icon: ImageVector, val tone: SaharaTone, val tag: String, val onClick: () -> Unit)

/** One register row: a coloured icon tile, the title, today's line under it, and an arrow; the whole row opens its page. */
@Composable private fun GlanceRow(icon: ImageVector, tone: SaharaTone, title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    HorizontalDivider(color = DoctorHairline)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp).semantics(mergeDescendants = true) {}.testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = tone.solid) { Icon(icon, null, Modifier.padding(6.dp).size(20.dp), tint = tone.onSolid) }; Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk); Text(subtitle, fontSize = 14.sp, color = DoctorCardSub, maxLines = 3) }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = RustText)
    }
}
