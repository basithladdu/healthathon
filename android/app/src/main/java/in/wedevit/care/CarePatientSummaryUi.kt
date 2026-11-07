package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * "Rahul at a glance": a short, read-only register on Today for patient and family. Every line comes from entries already on the
 * phone and every row opens the page it names (trailing chevron). A read-only family member sees the same rows; [onAddVisit] is null for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PatientSummaryCard(vm: CareModel, onSymptoms: () -> Unit, onVisit: (CareItem?) -> Unit, onAddVisit: (() -> Unit)?, onGoals: (CareVersion?) -> Unit, onCarePlan: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle()
    val name = people.firstOrNull { it.id == selected }?.name.orEmpty().ifBlank { t("Care", "देखभाल") }
    val role by vm.viewRole.collectAsStateWithLifecycle()
    var visitDialog by rememberSaveable { mutableStateOf(false) }
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
    // Family: the one thing they do most is first and big. (The patient has it on the check-in card above.)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (role == "Family") Button(onClick = onSymptoms, shape = RoundedCornerShape(SaharaRadius.Md), colors = ButtonDefaults.buttonColors(containerColor = SaharaTone.Teal.solid, contentColor = SaharaTone.Teal.onSolid), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).saharaAppear().testTag("summary-note-symptom")) {
        Icon(Icons.Default.Healing, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)); Text(t("Note a symptom", "तबीयत का नोट लिखें"), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
    Surface(Modifier.fillMaxWidth().saharaAppear().animateContentSize().testTag("patient-summary"), shape = RoundedCornerShape(SaharaRadius.Lg), color = SaharaColors.Surface, border = BorderStroke(1.5.dp, SaharaTone.Teal.solid)) { Column {
        Row(Modifier.fillMaxWidth().background(SaharaTone.Teal.solid).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = SaharaColors.Surface, modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SaharaTone.Teal.ink) } }
            Spacer(Modifier.width(12.dp))
            Text(t("$name at a glance", "$name एक नज़र में"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SaharaTone.Teal.onSolid, maxLines = 2, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlanceRow(Icons.Default.Healing, SaharaSection.Symptoms, t("Pain and symptoms", "दर्द और लक्षण"), summary.symptoms.firstOrNull()?.let { CarePatientSummaryRules.symptomLine(it, hindi) } ?: CarePatientSummaryRules.noSymptoms(hindi), "summary-symptoms", onSymptoms)
        GlanceRow(Icons.Default.Event, SaharaSection.Visits, t("Next visit", "अगली मुलाक़ात"), visitLine, "summary-visit") { if (visit == null && onAddVisit != null) visitDialog = true else onVisit(visit) }
        GlanceRow(Icons.Default.Description, SaharaSection.GoalsOfCare, t("Goals of care", "देखभाल के लक्ष्य"), CarePatientSummaryRules.noteLine(note, note?.let { date(it.published) }.orEmpty(), hindi), "today-goals-of-care") { onGoals(note) }
        GlanceRow(Icons.Default.LocalHospital, SaharaSection.Emergency, t("Emergency plan", "आपातकालीन योजना"), CarePatientSummaryRules.planLine(summary.plan, hindi), "summary-plan", onCarePlan)
        }
    } }
    }
    if (visitDialog) QuickVisitDialog(vm, onDismiss = { visitDialog = false })
}

/**
 * Next visit from Today: only the day and time are needed (tomorrow 10:00 is filled in). Doctor or hospital and place are optional and
 * stay behind "More details".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun QuickVisitDialog(vm: CareModel, onDismiss: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val capturedPerson = remember { person }; val capturedAccount = remember { vm.accountNamespace }
    val zone = ZoneId.systemDefault()
    var day by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var hour by rememberSaveable { mutableIntStateOf(10) }; var minute by rememberSaveable { mutableIntStateOf(0) }
    var more by rememberSaveable { mutableStateOf(false) }
    var who by rememberSaveable { mutableStateOf("") }; var place by rememberSaveable { mutableStateOf("") }
    val chosen = LocalDate.parse(day)
    LaunchedEffect(person) { if (person != capturedPerson) onDismiss() }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(t("Next visit", "अगली मुलाक़ात")) }, text = {
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> day = LocalDate.of(y, m + 1, d).toString() }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show() }, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-visit-date")) {
                    Icon(Icons.Default.Event, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Day *", "दिन *") + ": " + chosen.format(careDateFormat("d MMM", careHindi())), maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = { TimePickerDialog(context, { _, h, mi -> hour = h; minute = mi }, hour, minute, false).show() }, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-visit-time")) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Time *", "समय *") + ": " + LocalTime.of(hour, minute).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), maxLines = 1, softWrap = false)
                }
            }
            TextButton(onClick = { more = !more }, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-visit-more")) {
                Text(t("More details", "और जानकारी"), fontWeight = FontWeight.SemiBold); Icon(if (more) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            }
            AnimatedVisibility(more) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(who, { who = it.take(200) }, label = { Text(t("Doctor or hospital (optional)", "डॉक्टर या अस्पताल (चाहें तो)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(place, { place = it.take(500) }, label = { Text(t("Place or note (optional)", "जगह या नोट (चाहें तो)")) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }, confirmButton = { TextButton(enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-visit-save"), onClick = {
        if (!canEditCareContext(vm, capturedPerson, capturedAccount)) { vm.message.value = t("Care access changed. Reopen this person's records before saving.", "देखभाल की अनुमति बदल गई है। सहेजने से पहले इस व्यक्ति के रिकॉर्ड फिर से खोलें।"); onDismiss() }
        else vm.saveItem("visit", who.trim().ifBlank { t("Doctor visit", "डॉक्टर से मुलाक़ात") }, place.trim(), LocalDateTime.of(chosen, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli(), done = onDismiss)
    }) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save visit", "मुलाक़ात सहेजें"), fontWeight = FontWeight.SemiBold) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(t("Cancel", "रद्द करें")) } })
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
