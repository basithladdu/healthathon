@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class DoctorSchedulePatient(val id: String, val name: String)

// Meeting-card tints, cycled per card so neighbouring visits are easy to tell apart.
private val ScheduleTints get() = listOf(Peach to Cream, SaharaSoftBlue to Paper, Mint to Paper, SaharaLavender to Paper)

private fun visitDay(due: Long, zone: ZoneId) = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()

/** Shared Schedule tab for the practice doctor and the connected doctor: week strip, day cards, patient picker. */
@Composable internal fun DoctorSchedule(
    patients: List<DoctorSchedulePatient>, items: List<CareItem>, onOpenPatient: (String) -> Unit, onAllPatients: () -> Unit,
    planner: @Composable (personId: String, onDismiss: () -> Unit) -> Unit, modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null,
) { val t = careText(); val hindi = careHindi()
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedText)
    var picking by rememberSaveable { mutableStateOf(false) }
    var planFor by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault(); val today = LocalDate.now()
    val visits = remember(patients, items) {
        patients.flatMap { p -> doctorVisitsFor(items, p.id).filter { it.due > 0 && CareEntryCodec.decode(it.detail)?.status != "cancelled" }.map { p to it } }
    }
    val byDay = remember(visits) { visits.groupBy { visitDay(it.second.due, zone) } }
    val dayVisits = byDay[selected].orEmpty().sortedBy { it.second.due }
    val weekStart = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    fun pick(day: LocalDate) { selectedText = day.toString(); expandedId = null }

    LazyColumn(modifier.fillMaxSize().testTag("doctor-schedule"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, tint = DoctorAccent, modifier = Modifier.size(28.dp))
                    Text(t("Schedule", "समय-सूची"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    Button(onClick = { picking = true }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Color.White), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-schedule-add")) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add", "जोड़ें"))
                    }
                }
                TextButton(onClick = onAllPatients, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-schedule-all-patients")) { Text(t("All patients", "सभी मरीज़"), color = RustText) }
            }
        }
        item {
            Surface(color = Paper, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pick(selected.minusMonths(1)) }, Modifier.size(48.dp)) { Icon(Icons.Default.ChevronLeft, t("Previous month", "पिछला महीना"), tint = DoctorCardInk) }
                    Text(careDateFormat("MMM yyyy", hindi).format(selected), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = DoctorCardInk)
                    IconButton(onClick = { pick(selected.plusMonths(1)) }, Modifier.size(48.dp)) { Icon(Icons.Default.ChevronRight, t("Next month", "अगला महीना"), tint = DoctorCardInk) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Arrows stay narrow so seven day columns still fit a 360dp phone; height keeps the 48dp touch target.
                IconButton(onClick = { pick(selected.minusWeeks(1)) }, Modifier.size(width = 32.dp, height = 48.dp)) { Icon(Icons.Default.ChevronLeft, t("Previous week", "पिछला हफ़्ता"), tint = DoctorCardInk) }
                (0L..6L).forEach { offset ->
                    val day = weekStart.plusDays(offset); val isSelected = day == selected; val has = byDay.containsKey(day)
                    val label = careDateFormat("EEEE, d MMMM", hindi).format(day)
                    Surface(onClick = { pick(day) }, shape = RoundedCornerShape(14.dp), color = if (isSelected) DoctorAccent else Color.Transparent,
                        border = if (!isSelected && day == today) BorderStroke(1.5.dp, DoctorAccent) else null,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp).semantics { contentDescription = label }.testTag("doctor-schedule-day-$day")) {
                      Box(contentAlignment = Alignment.Center) {
                        val ink = if (isSelected) Color.White else DoctorCardInk
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(careDateFormat("EEE", hindi).format(day), fontSize = 11.sp, color = ink.copy(alpha = if (isSelected) .9f else .7f), maxLines = 1, softWrap = false)
                            Text(careDateFormat("dd", hindi).format(day), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                            Box(Modifier.size(5.dp).clip(CircleShape).background(if (has) (if (isSelected) Color.White else DoctorAccent) else Color.Transparent))
                        }
                      }
                    }
                }
                IconButton(onClick = { pick(selected.plusWeeks(1)) }, Modifier.size(width = 32.dp, height = 48.dp)) { Icon(Icons.Default.ChevronRight, t("Next week", "अगला हफ़्ता"), tint = DoctorCardInk) }
            }
        }
        if (dayVisits.isEmpty()) item(key = "schedule-empty") {
            val label = careDateFormat("EEE d MMM", hindi).format(selected)
            Surface(color = Paper, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.EventAvailable, null, tint = DoctorAccent, modifier = Modifier.size(32.dp))
                    Text(t("No visits on $label", "$label को कोई विज़िट नहीं"), fontWeight = FontWeight.Medium, color = DoctorCardInk, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Add a visit", "विज़िट जोड़ें")) }
                }
            }
        }
        items(dayVisits.size, key = { "visit-${dayVisits[it].second.id}" }) { index ->
            val (patient, item) = dayVisits[index]
            ScheduleVisitCard(patient, item, ScheduleTints[index % ScheduleTints.size], expandedId == item.id, { expandedId = if (expandedId == item.id) null else item.id }, { onOpenPatient(patient.id) })
        }
        if (footer != null) item { footer() }
    }

    if (picking) PatientPickerSheet(patients, onDismiss = { picking = false }, onPick = { picking = false; planFor = it })
    planFor?.let { id -> planner(id) { planFor = null } }
}

@Composable private fun ScheduleVisitCard(patient: DoctorSchedulePatient, item: CareItem, tint: Pair<Color, Color>, expanded: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) { val t = careText(); val hindi = careHindi()
    val entry = CareEntryCodec.decode(item.detail)
    val time = if (entry?.timed == true) careDateFormat("h:mm a", hindi).format(Instant.ofEpochMilli(item.due).atZone(ZoneId.systemDefault())) else t("Time not set", "समय तय नहीं")
    val place = entry?.location?.trim().orEmpty().ifBlank { t("Visit", "विज़िट") }
    val doctor = doctorVisitDoctor(item).ifBlank { t("Doctor not recorded", "डॉक्टर दर्ज नहीं") }
    val note = DoctorVisitDetails.decode(entry?.detail ?: item.detail).note.trim()
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(tint.first, tint.second))).clickable(onClick = onToggle).testTag("doctor-schedule-card-${item.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("Visit with ${patient.name}", "${patient.name} के साथ विज़िट"), fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    Text(time, fontSize = 14.sp, color = DoctorCardSub)
                }
                Surface(onClick = onToggle, shape = CircleShape, color = Color.White.copy(alpha = .75f), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) t("Hide details", "विवरण छिपाएँ") else t("Show details", "विवरण दिखाएँ"), tint = DoctorCardInk) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .8f)), contentAlignment = Alignment.Center) { Text(patient.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RustText) }
                Text(place, Modifier.weight(1f), fontSize = 14.sp, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(color = Color.Transparent, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, DoctorCardSub)) {
                    Text(if (doctorVisitCompleted(item)) t("Completed", "पूरी हुई") else t("Planned", "तय"), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, color = DoctorCardInk)
                }
            }
            if (expanded) {
                Text(t("Doctor: $doctor", "डॉक्टर: $doctor"), fontSize = 14.sp, color = DoctorCardInk)
                if (note.isNotBlank()) Text(t("Appointment logistics: $note", "अपॉइंटमेंट का इंतज़ाम: $note"), fontSize = 14.sp, color = DoctorCardSub)
                OutlinedButton(onClick = onOpen, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-schedule-open-${item.id}")) { Text(t("Open", "खोलें"), color = RustText) }
            }
        }
    }
}

@Composable private fun PatientPickerSheet(patients: List<DoctorSchedulePatient>, onDismiss: () -> Unit, onPick: (String) -> Unit) { val t = careText()
    var query by remember { mutableStateOf("") }
    val shown = patients.filter { it.name.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp).testTag("doctor-schedule-picker"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Choose a patient", "मरीज़ चुनें"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth().testTag("doctor-schedule-search"), singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text(t("Search patients by name", "नाम से मरीज़ खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, Modifier.size(48.dp)) { Icon(Icons.Default.Clear, t("Clear search", "खोज साफ़ करें")) } })
            if (shown.isEmpty()) Text(if (patients.isEmpty()) t("No patients yet", "अभी कोई मरीज़ नहीं") else t("No matching patient", "कोई मरीज़ नहीं मिला"), color = DoctorCardSub)
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { p ->
                    Surface(onClick = { onPick(p.id) }, color = Paper, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Peach), contentAlignment = Alignment.Center) { Text(p.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DoctorCardInk) }
                            Text(p.name, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                            Icon(Icons.Default.ChevronRight, null, tint = DoctorAccent)
                        }
                    }
                }
            }
        }
    }
}

/** Opens the existing Plan a visit sheet for a person on a real or device-local care model. */
@Composable internal fun DoctorCarePlanner(vm: CareModel, personId: String, onDismiss: () -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    LaunchedEffect(personId) { vm.select(personId) }
    // The sheet closes itself when another person is selected, so wait until this one is.
    if (selected == personId) DoctorVisitEditor(vm, personId, onDismiss)
}

/** Practice patients live in their own on-device database, so planning for them opens that database the way the practice care screen does. */
@Composable private fun DoctorPracticePlanner(personId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm = remember { CareModel.createDoctorDemo(context as Application) }
    val selected by vm.selected.collectAsStateWithLifecycle()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(vm) {
        try { DoctorTestProfiles.seed(vm, context); vm.select(personId); ready = true }
        catch (e: Exception) { if (e is CancellationException) throw e; onDismiss() }
    }
    DisposableEffect(vm) { onDispose { vm.closeDoctorDemo() } }
    if (ready && selected == personId) DoctorVisitEditor(vm, personId, onDismiss)
}

/** Practice doctor: Rahul and Arjun (practice database) plus any device-local care profiles. */
@Composable internal fun DoctorPracticeSchedule(careModel: CareModel?, carePeople: List<Person>, onOpenTest: (DoctorTestProfiles.Profile) -> Unit, onOpenCare: (String) -> Unit, onAllPatients: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var reload by remember { mutableIntStateOf(0) }
    val practiceItems by produceState(emptyList<CareItem>(), reload) {
        value = withContext(Dispatchers.IO) { runCatching { CareDatabase.get(context, CareModel.DOCTOR_DEMO_STORAGE).care().allItems() }.getOrDefault(emptyList()) }
    }
    val careItems by remember(careModel) { careModel?.allCareItems() ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val patients = DoctorTestProfiles.profiles.map { DoctorSchedulePatient(DoctorTestProfiles.personId(it), it.name) } +
        carePeople.filterNot { DoctorTestProfiles.contains(it.id) }.map { DoctorSchedulePatient(it.id, it.name) }
    DoctorSchedule(patients, practiceItems + careItems,
        onOpenPatient = { id -> DoctorTestProfiles.profiles.firstOrNull { DoctorTestProfiles.personId(it) == id }?.let(onOpenTest) ?: onOpenCare(id) },
        onAllPatients = onAllPatients, modifier = modifier,
        planner = { id, dismiss ->
            if (DoctorTestProfiles.contains(id)) DoctorPracticePlanner(id) { reload++; dismiss() }
            else if (careModel != null) DoctorCarePlanner(careModel, id, dismiss)
        })
}

/** Connected doctor: every patient who shares care, planned through the signed-in care model. */
@Composable internal fun DoctorConnectedSchedule(vm: CareModel, people: List<Person>, items: List<CareItem>, onOpenPatient: (String) -> Unit, onAllPatients: () -> Unit, modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null) {
    DoctorSchedule(people.map { DoctorSchedulePatient(it.id, it.name) }, items, onOpenPatient, onAllPatients, { id, dismiss -> DoctorCarePlanner(vm, id, dismiss) }, modifier, footer)
}
