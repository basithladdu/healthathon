@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

// Visit-card colour bars, cycled per card so neighbouring visits are easy to tell apart.
private val ScheduleTones get() = listOf(DoctorTones.Rust, DoctorTones.Teal, DoctorTones.Plum, DoctorTones.Indigo)

private fun visitDay(due: Long, zone: ZoneId) = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()

/** Shared Schedule tab for the practice doctor and the connected doctor: week strip, day cards, patient picker. */
@Composable internal fun DoctorSchedule(
    patients: List<DoctorSchedulePatient>, items: List<CareItem>, onOpenPatient: (String) -> Unit, onAllPatients: () -> Unit,
    planner: @Composable (personId: String, onDismiss: () -> Unit) -> Unit, modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null,
) { val t = careText(); val hindi = careHindi()
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = remember(selectedText) { LocalDate.parse(selectedText) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var planFor by rememberSaveable { mutableStateOf<String?>(null) }
    var monthView by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault(); val today = LocalDate.now()
    val visits = remember(patients, items) {
        patients.flatMap { p -> doctorVisitsFor(items, p.id).filter { it.due > 0 && CareEntryCodec.decode(it.detail)?.status != "cancelled" }.map { p to it } }
    }
    val byDay = remember(visits) { visits.groupBy { visitDay(it.second.due, zone) } }
    val dayVisits = remember(byDay, selected) { byDay[selected].orEmpty().sortedBy { it.second.due } }
    val counts = remember(byDay) { byDay.mapValues { it.value.size } }
    // Day-strip formatters are built once per language, not again in every cell while the strip swipes.
    val lang = CareLanguageState.current
    val weekdayFormat = remember(hindi, lang) { careDateFormat("EEE", hindi) }
    val dayNumberFormat = remember(hindi, lang) { careDateFormat("dd", hindi) }
    val longDayFormat = remember(hindi, lang) { careDateFormat("EEEE, d MMMM", hindi) }
    val tick = rememberSaharaTick()
    fun pick(day: LocalDate) { selectedText = day.toString() }

    LazyColumn(modifier.fillMaxSize().testTag("doctor-schedule"), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, tint = DoctorAccent, modifier = Modifier.size(28.dp))
                    Text(t("Schedule", "शेड्यूल"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    Button(onClick = { picking = true }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorTones.Rust.solid, contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-schedule-add")) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add", "जोड़ें"))
                    }
                }
                TextButton(onClick = onAllPatients, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-schedule-all-patients")) { Icon(Icons.Default.People, null, Modifier.size(18.dp), tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("All patients", "सभी मरीज़"), color = RustText) }
            }
        }
        item {
            // Week or Month (Dr Sujay, 22 Nov): the month grid shows how many visits fall on each day.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("doctor-schedule-view")) {
                SegmentedButton(selected = !monthView, onClick = { if (monthView) tick(); monthView = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-schedule-week-toggle")) { Text(t("Week", "सप्ताह")) }
                SegmentedButton(selected = monthView, onClick = { if (!monthView) tick(); monthView = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-schedule-month-toggle")) { Text(t("Month", "महीना")) }
            }
        }
        item {
            Surface(color = Paper, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pick(selected.minusMonths(1)) }, Modifier.size(48.dp)) { Icon(Icons.Default.ChevronLeft, t("Previous month", "पिछला महीना"), tint = DoctorCardInk) }
                    Text(careDateFormat(if (monthView) "MMMM yyyy" else "MMM yyyy", hindi).format(selected), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = DoctorCardInk)
                    IconButton(onClick = { pick(selected.plusMonths(1)) }, Modifier.size(48.dp)) { Icon(Icons.Default.ChevronRight, t("Next month", "अगला महीना"), tint = DoctorCardInk) }
                }
            }
        }
        if (monthView) item(key = "schedule-month", contentType = "month") {
            Box(Modifier.animateItem()) { DoctorScheduleMonth(selected, today, counts) { pick(it) } }
        } else item(key = "schedule-week", contentType = "week") {
            Row(Modifier.fillMaxWidth().animateItem(), verticalAlignment = Alignment.CenterVertically) {
                // Arrows stay narrow so seven day columns still fit a 360dp phone; height keeps the 48dp touch target.
                IconButton(onClick = { pick(selected.minusWeeks(1)) }, Modifier.size(width = 32.dp, height = 48.dp)) { Icon(Icons.Default.ChevronLeft, t("Previous week", "पिछला हफ़्ता"), tint = DoctorCardInk) }
                // Swipe the strip for the next or previous week (kizitonwose Calendar); the arrows do the same.
                SaharaWeekPager(selected, { pick(it) }, Modifier.weight(1f)) { day ->
                    val isSelected = day == selected; val has = byDay.containsKey(day)
                    val label = longDayFormat.format(day)
                    val fill by animateColorAsState(if (isSelected) DoctorTones.Rust.solid else DoctorTones.Rust.solid.copy(alpha = 0f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "schedule-day-fill")
                    Surface(onClick = { if (!isSelected) tick(); pick(day) }, shape = RoundedCornerShape(14.dp), color = fill,
                        border = if (!isSelected && day == today) BorderStroke(1.5.dp, DoctorAccent) else null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { contentDescription = label }.testTag("doctor-schedule-day-$day")) {
                      Box(contentAlignment = Alignment.Center) {
                        val ink = if (isSelected) Color.White else DoctorCardInk
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(weekdayFormat.format(day), fontSize = 12.sp, color = if (isSelected) Color.White else DoctorCardSub, maxLines = 1, softWrap = false)
                            Text(dayNumberFormat.format(day), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                            Box(Modifier.size(5.dp).clip(CircleShape).background(if (has) (if (isSelected) Color.White else DoctorAccent) else Color.Transparent))
                        }
                      }
                    }
                }
                IconButton(onClick = { pick(selected.plusWeeks(1)) }, Modifier.size(width = 32.dp, height = 48.dp)) { Icon(Icons.Default.ChevronRight, t("Next week", "अगला हफ़्ता"), tint = DoctorCardInk) }
            }
        }
        if (dayVisits.isEmpty()) item(key = "schedule-empty", contentType = "empty") {
            val label = careDateFormat("EEE d MMM", hindi).format(selected)
            Surface(color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().animateItem()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Peach), contentAlignment = Alignment.Center) { Icon(Icons.Default.EventAvailable, null, tint = DoctorAccent, modifier = Modifier.size(24.dp)) }
                    Text(t("No visits on $label", "$label को कोई विज़िट नहीं"), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Add a visit", "विज़िट जोड़ें")) }
                }
            }
        }
        items(dayVisits.size, key = { "visit-${dayVisits[it].second.id}" }, contentType = { "visit" }) { index ->
            val (patient, item) = dayVisits[index]
            Box(Modifier.animateItem()) { ScheduleVisitCard(patient, item, ScheduleTones[index % ScheduleTones.size]) { onOpenPatient(patient.id) } }
        }
        if (footer != null) item { footer() }
    }

    if (picking) PatientPickerSheet(patients, onDismiss = { picking = false }, onPick = { picking = false; planFor = it })
    planFor?.let { id -> planner(id) { planFor = null } }
}

/** Month view: a Monday-first grid; each day shows how many visits it has. Tap a day to list its visits below; swipe for another month. */
@Composable private fun DoctorScheduleMonth(selected: LocalDate, today: LocalDate, counts: Map<LocalDate, Int>, onDay: (LocalDate) -> Unit) { val t = careText(); val hindi = careHindi()
    val lang = CareLanguageState.current
    val weekdayFormat = remember(hindi, lang) { careDateFormat("EEE", hindi) }
    val longDayFormat = remember(hindi, lang) { careDateFormat("EEEE, d MMMM", hindi) }
    val tick = rememberSaharaTick()
    Surface(color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("doctor-schedule-month")) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val monday = selected.with(java.time.DayOfWeek.MONDAY)
            Row(Modifier.fillMaxWidth()) {
                (0L..6L).forEach { i -> Text(weekdayFormat.format(monday.plusDays(i)), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = DoctorCardSub, maxLines = 1, softWrap = false) }
            }
            SaharaMonthPager(selected, onDay, Modifier.fillMaxWidth()) { day, inMonth ->
                val isSelected = day == selected; val count = counts[day] ?: 0
                val label = longDayFormat.format(day) + when (count) { 0 -> ""; 1 -> ", " + t("1 visit", "1 विज़िट"); else -> ", " + t("$count visits", "$count विज़िट") }
                val fill by animateColorAsState(if (isSelected) DoctorTones.Rust.solid else DoctorTones.Rust.solid.copy(alpha = 0f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "schedule-month-fill")
                Surface(onClick = { if (!isSelected) tick(); onDay(day) }, enabled = inMonth, shape = RoundedCornerShape(12.dp), color = fill,
                    border = if (!isSelected && day == today) BorderStroke(1.5.dp, DoctorAccent) else null,
                    modifier = Modifier.padding(2.dp).fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = label }.testTag("doctor-schedule-month-day-$day")) {
                    if (inMonth) Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(day.dayOfMonth.toString(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) Color.White else DoctorCardInk, maxLines = 1)
                        if (count > 0) Box(Modifier.clip(RoundedCornerShape(50)).background(if (isSelected) Color.White else DoctorTones.Rust.solid).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Text(count.toString(), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) DoctorTones.Rust.ink else Color.White, maxLines = 1)
                        } else Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

/** One visit, one tap: the whole row opens the patient. Time, place, doctor and note are on the row, so nothing needs expanding. */
@Composable private fun ScheduleVisitCard(patient: DoctorSchedulePatient, item: CareItem, tone: DoctorTone, onOpen: () -> Unit) { val t = careText(); val hindi = careHindi()
    val entry = remember(item.detail) { CareEntryCodec.decode(item.detail) }
    val time = if (entry?.timed == true) remember(item.due, hindi, CareLanguageState.current) { careDateFormat("h:mm a", hindi).format(Instant.ofEpochMilli(item.due).atZone(ZoneId.systemDefault())) } else t("Time not set", "समय तय नहीं")
    val place = entry?.location?.trim().orEmpty().ifBlank { t("Visit", "विज़िट") }
    val doctor = doctorVisitDoctor(item)
    val note = remember(entry, item.detail) { DoctorVisitDetails.decode(entry?.detail ?: item.detail).note.trim() }
    val done = doctorVisitCompleted(item)
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = onOpen, interactionSource = press, color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().saharaAppear().saharaPress(press).testTag("doctor-schedule-card-${item.id}")) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(tone.solid))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(time, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tone.ink, softWrap = false)
                    if (done) DoctorPill(t("Completed", "पूरी हुई"), DoctorTones.Good)
                }
                Text(patient.name, fontFamily = SaharaTitleFont, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // A pin only beside a real address; "Family meeting" or "Home visit" shows its own visit-type icon (24 Nov meeting).
                    // The typed location, not the translated "Visit" fallback, decides: a blank place never gets a pin in any language.
                    Icon(careVisitPlaceIcon(entry?.location?.trim().orEmpty(), item.title, doctor), null, Modifier.size(16.dp), tint = DoctorCardSub)
                    Text(listOf(place, doctor).filter { it.isNotBlank() }.joinToString(" · "), Modifier.weight(1f), fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (note.isNotBlank()) Text(note, fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, t("Open", "खोलें"), Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).testTag("doctor-schedule-open-${item.id}"), tint = DoctorCardSub)
        }
    }
}

@Composable private fun PatientPickerSheet(patients: List<DoctorSchedulePatient>, onDismiss: () -> Unit, onPick: (String) -> Unit) { val t = careText()
    var query by remember { mutableStateOf("") }
    val shown = remember(patients, query) { patients.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    SaharaScreenSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp).testTag("doctor-schedule-picker"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Choose a patient", "मरीज़ चुनें"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth().testTag("doctor-schedule-search"), singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper),
                placeholder = { Text(t("Search patients by name", "नाम से मरीज़ खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, Modifier.size(48.dp)) { Icon(Icons.Default.Clear, t("Clear search", "खोज साफ़ करें")) } })
            AnimatedVisibility(shown.isEmpty(), enter = SaharaMotion.enter, exit = SaharaMotion.exit) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (patients.isEmpty()) Icons.Default.PersonOutline else Icons.Default.Search, null, tint = DoctorAccent)
                Text(if (patients.isEmpty()) t("No patients yet", "अभी कोई मरीज़ नहीं") else t("No matching patient", "कोई मरीज़ नहीं मिला"), Modifier.weight(1f), fontSize = 15.sp, color = DoctorCardSub)
                if (query.isNotEmpty()) TextButton(onClick = { query = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Clear search", "खोज साफ़ करें"), color = RustText) }
            } }
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }, contentType = { "patient" }) { p ->
                    Surface(onClick = { onPick(p.id) }, color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).animateItem()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Peach), contentAlignment = Alignment.Center) { Text(p.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RustText) }
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
