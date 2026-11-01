package `in`.wedevit.care

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
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
    val taken = summary.medicineTicked.coerceAtMost(summary.medicineTotal)
    Surface(shape = RoundedCornerShape(26.dp), color = Paper, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().testTag("patient-summary")) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFFFF1D6), Color(0xFFFFFBF4), Color(0xFFE2F0E7)))).padding(vertical = 8.dp, horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = SaharaApricot, modifier = Modifier.size(56.dp)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 26.sp, color = DoctorAccent) } }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(today.format(careDateFormat("EEEE, d MMM", hindi)), fontSize = 12.sp, color = DoctorCardSub)
                    Text(t("$name at a glance", "$name एक नज़र में"), fontFamily = SaharaTitleFont, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 2)
                }
                Spacer(Modifier.width(8.dp))
                MedicineRing(taken, summary.medicineTotal)
            }
            if (!open) Text(CarePatientSummaryRules.collapsed(summary, hindi), Modifier.padding(top = 10.dp), fontSize = 14.sp, color = DoctorCardSub, maxLines = 3)
            // The drop-down: the same five sections as before, one tap away.
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }.semantics(mergeDescendants = true) { stateDescription = if (open) expandedWord else collapsedWord },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(if (open) t("Hide details", "ब्योरा छिपाएँ") else t("Show details", "ब्योरा दिखाएँ"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RustText)
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Rust)
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

/** Today's medicines as a ring: how many are ticked out of how many are due. */
@Composable private fun MedicineRing(taken: Int, total: Int) {
    val t = careText(); val hindi = careHindi()
    val progress by animateFloatAsState(if (total == 0) 0f else taken.toFloat() / total, label = "medicine ring")
    val label = CarePatientSummaryRules.medicineStatus(total, taken, hindi)
    Box(Modifier.size(84.dp).semantics(mergeDescendants = true) { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color(0x1F1F5C45), 0f, 360f, false, style = stroke)
            if (progress > 0f) drawArc(Color(0xFF3E8A68), -90f, 360f * progress, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (total == 0) "–" else "$taken/$total", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk)
            Text(t("Medicines", "दवाइयाँ"), fontSize = 10.sp, color = DoctorCardSub)
        }
    }
}

/** Today's four quick actions as tall rounded tiles with their own art. Tags match the older shortcut row. */
@Composable fun TodayQuickActions(onCalendar: () -> Unit, onSos: () -> Unit, onFamily: () -> Unit, onLabs: () -> Unit) {
    val t = careText()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Quick actions", "झटपट काम"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                QuickAction(t("Calendar", "कैलेंडर"), R.drawable.quick_calendar_art, Color(0xFFFDE7DD), "shortcut-calendar", onCalendar),
                QuickAction(t("SOS", "SOS मदद"), R.drawable.quick_sos_art, Color(0xFFFBE0DC), "today-sos", onSos),
                QuickAction(t("Family", "परिवार"), R.drawable.quick_family_art, Color(0xFFE2F0E7), "shortcut-workflows", onFamily),
                QuickAction(t("Labs", "जाँच"), R.drawable.quick_labs_art, Color(0xFFECE6FA), "shortcut-labs", onLabs),
            ).forEach { action ->
                ActionCard(Paper, Modifier.weight(1f).height(124.dp).testTag(action.tag), action.onClick) {
                    Column(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                        Surface(shape = CircleShape, color = action.tint, modifier = Modifier.size(60.dp)) { Box(contentAlignment = Alignment.Center) { Image(saharaImage(action.art, 46.dp), null, Modifier.size(46.dp), contentScale = ContentScale.Fit) } }
                        Text(action.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1)
                    }
                }
            }
        }
    }
}

private class QuickAction(val label: String, val art: Int, val tint: Color, val tag: String, val onClick: () -> Unit)

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
