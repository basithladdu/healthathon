@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.*

/**
 * Today, the landing page (25 Nov redesign, owner: "looks AI-generated"). A stock Material page: the greeting as the large
 * top-bar title with the avatar menu on the left and SOS on the right, the date and the three mood faces (the one visual
 * anchor), a row of round quick actions, then "Up next" as plain list rows: the next dose, pain and symptoms, the next visit,
 * goals of care. Family get "How is <name> feeling today?" instead of the faces, their own to-dos and their caregiving rows.
 * Nothing here is a card, a tinted box or a pill. The doctor view (clinician) keeps its own layout ([ClinicianToday]).
 */
@Composable fun Today(vm: CareModel, clinician: Boolean, onTalk: () -> Unit, onCare: (String) -> Unit, onNearby: () -> Unit, onAdd: (String) -> Unit, onRead: (CareVersion) -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, onSharedWorkspace: () -> Unit = {}, onSymptoms: () -> Unit = {}, onInviteFamily: () -> Unit = {}, onCalendarAt: (Long) -> Unit = { onCalendar() }, onCheckInWords: () -> Unit = {}, onSymptomNote: () -> Unit = onSymptoms, onDistress: () -> Unit = {}, onCarePlan: () -> Unit = {},
    onCarePlanFromSos: () -> Unit = onCarePlan, sosReopen: Boolean = false, onSosReopened: () -> Unit = {}, onCommunity: () -> Unit = {}, viewerName: String = "") {
    if (clinician) { ClinicianToday(vm, onTalk = onTalk, onCare = onCare, onAdd = onAdd, onRead = onRead, onCalendar = onCalendar, onWorkflow = onWorkflow, onLabs = onLabs, onCalendarAt = onCalendarAt); return }
    val people by vm.people.collectAsStateWithLifecycle(); val role by vm.viewRole.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val selectedPerson by vm.selected.collectAsStateWithLifecycle(); val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle()
    val loaded = loadedFor == selectedPerson; val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val hindi = careHindi(); val t = careText()
    val family = CareFamilyRules.isFamily(role, false)
    val personName = people.firstOrNull { it.id == selectedPerson }?.name.orEmpty()
    val caringName = personName.ifBlank { t("the patient", "मरीज़") }
    val menu = LocalSaharaPersonMenu.current

    // SOS: one sheet with the emergency numbers, the emergency QR card, care nearby, and (family only) what to do after a death.
    var sosOpen by rememberSaveable { mutableStateOf(false) }
    var sosCardFirst by rememberSaveable { mutableStateOf(false) }
    var afterDeathOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sosReopen) { if (sosReopen) { sosOpen = true; onSosReopened() } }
    if (sosOpen) SaharaScreenSheet(onDismissRequest = { sosOpen = false; sosCardFirst = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            // The Emergency card quick action opens the same sheet with the card first.
            if (sosCardFirst) { EmergencyCardOnToday(vm, onRead); Spacer(Modifier.height(12.dp)); EmergencyNumbersCard() }
            else { EmergencyNumbersCard(); Spacer(Modifier.height(12.dp)); EmergencyCardOnToday(vm, onRead) }
            Spacer(Modifier.height(8.dp))
            // Nearby stays one tap from SOS now that the Help tab is gone.
            SaharaRow(t("Nearby", "आस-पास"), Modifier.testTag("shortcut-nearby"), icon = Icons.Default.Place, onClick = { sosOpen = false; onNearby() })
            // Family and carers only: the patient is never shown anything about their own death.
            if (family) { Spacer(Modifier.height(12.dp)); AfterDeathEntry { afterDeathOpen = true } }
        }
    }
    if (afterDeathOpen) AfterDeathGuideSheet { afterDeathOpen = false }

    // Caregiving changes (SVK, 23 Nov), family and carers only: the quiet check-in, pause, or "no longer caregiving" (records kept).
    val caregiving = if (family && selectedPerson.isNotBlank()) rememberCaregivingStatus(vm, selectedPerson) else null
    val caregivingDue = caregiving != null && loaded && caregiving.due(caregivingLastLogged(items, selectedPerson))
    var caregivingCheckIn by rememberSaveable(selectedPerson) { mutableStateOf(false) }
    var caregivingAsked by rememberSaveable(selectedPerson) { mutableStateOf(false) }
    var caregivingSheet by rememberSaveable(selectedPerson) { mutableStateOf(false) }
    var caregivingStatusSheet by rememberSaveable(selectedPerson) { mutableStateOf(false) }
    // A check that is due opens by itself once; after that its row stays until it is answered.
    LaunchedEffect(caregivingDue) { if (caregivingDue && !caregivingAsked) { caregivingAsked = true; caregivingCheckIn = true } }
    val caregivingState = caregiving?.status?.state
    LaunchedEffect(caregivingState) { if (caregivingState == CaregivingState.ACTIVE) caregivingStatusSheet = false }
    val archived = caregiving != null && CaregivingStatusRules.archived(caregiving.status)
    if (caregiving != null && (caregivingCheckIn || caregivingSheet)) SaharaScreenSheet(onDismissRequest = { caregivingCheckIn = false; caregivingSheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            CaregivingCheckInCard(caregiving, caringName, asked = caregivingCheckIn) { caregivingCheckIn = false; caregivingSheet = false }
        }
    }
    if (caregiving != null && caregivingStatusSheet) SaharaScreenSheet(onDismissRequest = { caregivingStatusSheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) { CaregivingStatusCard(caregiving, caringName) }
    }
    // The family member's own distress and wellbeing check, in a sheet (it used to sit inline on Today).
    var wellbeingOpen by rememberSaveable(selectedPerson) { mutableStateOf(false) }
    if (wellbeingOpen && family) SaharaScreenSheet(onDismissRequest = { wellbeingOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) { FamilyCaregiverCheckCard(vm, caringName) }
    }

    // Worked out once per change of the list, so Today scrolls without redoing the sorting.
    val openTasks = remember(items) { items.filter { it.kind == "task" && !CareEntryCodec.isStructured(it.detail) && it.doneOn.isBlank() } }
    val checkIn = remember(items, selectedPerson, dayStart) { CheckInMood.today(items, selectedPerson, dayStart) }
    val distressToday = remember(items, selectedPerson, dayStart) { DistressRules.today(items, selectedPerson, dayStart) }
    val planStore = remember(vm.storageNamespace) { CarePlanStore(context.applicationContext, vm.storageNamespace) }
    val plans = remember(selectedPerson) { runCatching { planStore.load(selectedPerson).ectprs }.getOrDefault(emptyList()) }
    val summary = remember(items, versions, selectedPerson, plans, today) { CarePatientSummaryRules.build(items, versions, selectedPerson, plans, today) }
    var visitDialog by rememberSaveable { mutableStateOf(false) }
    if (visitDialog) QuickVisitDialog(vm, onDismiss = { visitDialog = false })
    // "Marked taken · Undo" as the app's snackbar while a one-tap tick waits.
    TodayDoseUndo(selectedPerson)

    val hour = LocalTime.now().hour
    val greeting = CareTodayRules.greetingLine(hour, hindi, personName, patient = !family, viewerName = if (family) viewerName else "")
    val nav: (@Composable () -> Unit)? = when {
        menu == null -> null
        family -> ({ PersonAvatarButton(personName, t("Caring for", "इनकी देखभाल"), menu) })
        else -> ({ SaharaPersonAction() })
    }
    val actions = buildList {
        if (canEdit && !family) add(QuickActionSpec(t("Symptom", "लक्षण"), Icons.Default.Healing, tag = "today-checkin-symptom", onClick = onSymptomNote))
        if (family && !archived) add(QuickActionSpec(t("To do", "करने के काम"), Icons.Default.Checklist, tag = "today-todo-title", onClick = { onCare("To do") }))
        add(QuickActionSpec(t("Medicines", "दवाइयाँ"), Icons.Default.Medication, tag = "today-medicines-title", onClick = { onCare("Medicines") }))
        add(QuickActionSpec(t("Visits", "मुलाक़ातें"), Icons.Default.Event, tag = "today-visits", onClick = onCalendar))
        if (family && !archived) add(QuickActionSpec(t("Your wellbeing", "आपकी सेहत"), Icons.Default.Favorite, tag = "family-caregiver-check-open", onClick = { wellbeingOpen = true }))
        // Simple Mode from Today (it asks first; the app then shows the big spoken buttons). Family set it for the patient on this phone.
        menu?.onSimpleMode?.let { add(QuickActionSpec(t("Simple mode", "सरल मोड"), Icons.Default.TouchApp, tag = "today-simple-mode", onClick = it)) }
        add(QuickActionSpec(t("Emergency card", "आपातकालीन कार्ड"), Icons.Default.QrCode, tag = "today-emergency-card", onClick = { sosCardFirst = true; sosOpen = true }))
        if (!family) add(QuickActionSpec(t("Care team", "देखभाल टीम"), Icons.Default.Forum, tag = "today-care-team", onClick = onWorkflow))
    }
    val addButton: @Composable (() -> Unit) -> Unit = { onClick -> TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Add", "जोड़ें")) } }

    SaharaPage(title = greeting, navigationIcon = nav, actions = { SosTopButton(onClick = { sosCardFirst = false; sosOpen = true }); Spacer(Modifier.width(8.dp)) },
        refreshing = if (vm.cloud != null) refreshing else null, onRefresh = { vm.refresh() },
        contentPadding = PaddingValues(bottom = 32.dp), listModifier = Modifier.testTag("today-list")) {
        // The one visual anchor: the date and the mood faces (family: how the patient is doing, and one button).
        item(key = "today-hero", contentType = "hero") {
            Column(saharaTodayItem(Modifier.fillMaxWidth().testTag("today-greeting"))) {
                Text(today.format(careDateFormat("EEEE, d MMMM", hindi)), Modifier.padding(horizontal = 16.dp).testTag("today-date"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (!family) TodayCheckIn(vm, checkIn, canEdit, onWords = onCheckInWords, onDistress = onDistress)
                else {
                    // The patient's own check-in, read-only, when they made one today.
                    checkIn?.let { ci ->
                        val face = CareDailyFlow.moods.indexOf(ci.title).coerceAtLeast(0) / 2f
                        SaharaRow(checkInMoodLabel(ci.title, hindi), Modifier.testTag("today-checkin-seen"), supporting = Instant.ofEpochMilli(ci.created).atZone(zone).format(careDateFormat("h:mm a", hindi)),
                            leading = { CareMoodFace(face, Modifier.size(32.dp).clearAndSetSemantics { }) })
                    }
                    if (canEdit) Button(onClick = onSymptomNote, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 56.dp).testTag("summary-note-symptom")) {
                        Icon(Icons.Default.Healing, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                        Text(t("How is $caringName feeling today?", "$caringName आज कैसा महसूस कर रहे हैं?"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        item(key = "today-actions", contentType = "actions") { QuickActionRow(actions, saharaTodayItem(Modifier.padding(top = 16.dp))) }
        item(key = "up-next", contentType = "label") { SaharaGroupLabel(t("Up next", "आगे क्या है"), saharaTodayItem()) }
        // No longer caregiving: Today goes quiet (no doses or to-dos); the records stay in the Care tab.
        if (!archived) item(key = "today-doses", contentType = "row") { TodayNextDose(vm, loaded, onOpen = { onCare("Medicines") }, onAdd = { onAdd("medicine") }, modifier = saharaTodayItem()) }
        item(key = "summary-symptoms", contentType = "row") {
            SaharaRow(t("Pain and symptoms", "दर्द और लक्षण"), saharaTodayItem(Modifier.testTag("summary-symptoms")), icon = Icons.Default.Healing, onClick = onSymptoms,
                supporting = summary.symptoms.firstOrNull()?.let { CarePatientSummaryRules.symptomLine(it, hindi) } ?: CarePatientSummaryRules.noSymptoms(hindi))
        }
        val visit = summary.nextVisit
        if (visit != null) item(key = "summary-visit", contentType = "row") {
            val clock = if (visit.due == 0L) "" else ", " + Instant.ofEpochMilli(visit.due).atZone(zone).format(careDateFormat("h:mm a", hindi))
            SaharaRow(t("Next visit", "अगली मुलाक़ात"), saharaTodayItem(Modifier.testTag("summary-visit")), icon = Icons.Default.Event,
                supporting = CareTodayRules.whenLabel(visit.due, today, hindi) + clock + visit.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                onClick = { if (visit.due == 0L) onCalendar() else onCalendarAt(visit.due) })
        } else if (canEdit && loaded) item(key = "summary-visit", contentType = "row") {
            SaharaRow(t("No visit planned", "कोई मुलाक़ात तय नहीं"), saharaTodayItem(Modifier.testTag("summary-visit")), icon = Icons.Default.Event,
                trailing = { addButton { visitDialog = true } }, onClick = onCalendar)
        }
        summary.latestNote?.let { note -> item(key = "today-goals-of-care", contentType = "row") {
            SaharaRow(t("Goals of care", "देखभाल के लक्ष्य"), saharaTodayItem(Modifier.testTag("today-goals-of-care")), icon = Icons.Default.Description,
                supporting = CarePatientSummaryRules.noteLine(note, date(note.published), hindi), onClick = { onRead(note) })
        } }
        // Family and view-only: the patient's own distress answer today, read-only.
        if ((family || !canEdit) && distressToday != null) item(key = "today-distress-seen", contentType = "row") {
            val answer = remember(distressToday) { DistressCodec.decode(distressToday.detail) }
            SaharaRow(t("Distress", "परेशानी"), saharaTodayItem(Modifier.testTag("today-distress-seen")), icon = Icons.Default.Thermostat,
                supporting = answer?.let { if (it.distress) t("Yes", "हाँ") + (it.score?.let { s -> " · $s/10" } ?: "") else t("No", "नहीं") },
                onClick = if (canEdit) onDistress else null)
        }
        // Family: the next two open to-dos with a tick box; "See all" opens the list.
        if (family && !archived && openTasks.isNotEmpty()) {
            item(key = "todo-label", contentType = "label") { SaharaGroupLabel(t("To do", "करने के काम"), saharaTodayItem(), action = t("See all", "सब देखें"), onAction = { onCare("To do") }) }
            items(openTasks.take(2), key = { "todo-${it.id}" }, contentType = { "row" }) { task ->
                SaharaRow(task.title, saharaTodayItem(Modifier.testTag("today-task-${task.id}")), supporting = task.due.takeIf { it > 0L }?.let { date(it) },
                    leading = { Checkbox(checked = false, onCheckedChange = { vm.toggle(task) }, enabled = canEdit, modifier = Modifier.testTag("today-task-done-${task.id}")) },
                    onClick = { onCare("To do") })
            }
        }
        // Family: the caregiving rows (a due check, a pause or an ended status, or the quiet way to change it).
        if (caregiving != null) item(key = "caregiving", contentType = "row") {
            val status = caregiving.status
            Column(saharaTodayItem()) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                when {
                    caregivingDue -> SaharaRow(t("Are you still caring for $caringName?", "क्या आप अभी भी $caringName की देखभाल कर रहे हैं?"), Modifier.testTag("caregiving-checkin-row"), icon = Icons.Default.Favorite, onClick = { caregivingCheckIn = true })
                    status.state == CaregivingState.PAUSED -> {
                        val until = status.pausedUntil?.format(careDateFormat("d MMM", hindi)).orEmpty()
                        SaharaRow(t("Reminders paused until $until", "$until तक रिमाइंडर रुके हैं"), Modifier.testTag("caregiving-status-row"), icon = Icons.Default.NotificationsPaused, onClick = { caregivingStatusSheet = true })
                    }
                    status.state == CaregivingState.ENDED -> SaharaRow(t("Not actively caregiving", "अभी देखभाल नहीं कर रहे"), Modifier.testTag("caregiving-status-row"), icon = Icons.Default.NotificationsPaused, onClick = { caregivingStatusSheet = true })
                    else -> SaharaRow(t("Has your caregiving changed?", "क्या आपकी देखभाल में कुछ बदला है?"), Modifier.testTag("caregiving-change"), icon = Icons.Default.Tune, onClick = { caregivingSheet = true })
                }
            }
        }
    }
}

/**
 * The patient's check-in on Today: "How was today?" and three mood faces. A tap saves today's check-in (or changes the one
 * already saved) through the same save as the full check-in screen. Under the faces: a few words, and the distress question
 * one tap away (Dr Sujay). View-only people see the faces but cannot change them.
 */
@Composable private fun TodayCheckIn(vm: CareModel, checkIn: CareItem?, canEdit: Boolean, onWords: () -> Unit, onDistress: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val scheme = MaterialTheme.colorScheme
    val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val capturedAccount = remember(person) { vm.accountNamespace }
    val haptic = rememberSaharaHaptic()
    val done = checkIn != null
    Column(Modifier.fillMaxWidth().testTag("today-checkin")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("How was today?", "आज का दिन कैसा रहा?"), Modifier.weight(1f).a11yHeading(), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), fontWeight = FontWeight.SemiBold)
            // Once saved: a small tick and the word, in plain text (no pill).
            if (done) Row(Modifier.testTag("today-checkin-saved"), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = scheme.primary); Spacer(Modifier.width(4.dp))
                Text(t("Saved", "सहेजा"), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup()) {
            CareDailyFlow.moods.forEachIndexed { index, mood ->
                val selected = checkIn?.title == mood
                TodayMoodFace(checkInMoodLabel(mood, hindi), index / 2f, selected = selected, dimmed = done && !selected, enabled = !busy, modifier = Modifier.weight(1f).testTag("today-checkin-$index")) {
                    if (!canEdit) vm.message.value = t("View only", "सिर्फ़ देखने के लिए")
                    else {
                        val change = CareDailyFlow.checkInChange(checkIn, mood)
                        if (change != null && canEditCareContext(vm, person, capturedAccount)) { haptic(); if (checkIn != null) vm.updateItem(change) {} else vm.saveItem("checkin", mood, "") }
                    }
                }
            }
        }
        if (!checkIn?.detail.isNullOrBlank()) Text(checkIn?.detail.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (canEdit) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onWords, label = { Text(if (checkIn?.detail.isNullOrBlank()) t("Add a few words", "कुछ शब्द लिखें") else t("Change the words", "शब्द बदलें")) },
                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(AssistChipDefaults.IconSize)) }, modifier = Modifier.testTag("today-checkin-words"))
            AssistChip(onClick = onDistress, label = { Text(t("Feeling very upset?", "बहुत परेशान हैं?")) },
                leadingIcon = { Icon(Icons.Default.Thermostat, null, Modifier.size(AssistChipDefaults.IconSize), tint = scheme.error) }, modifier = Modifier.testTag("today-distress"))
        }
    }
}

/** One mood: the face (48dp) over its label, at least 88dp tall. The chosen one gets a ring in the accent colour; once one is saved the others fade. */
@Composable private fun TodayMoodFace(label: String, face: Float, selected: Boolean, dimmed: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fade by animateFloatAsState(if (dimmed) .4f else 1f, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "mood-fade")
    Column(modifier.heightIn(min = 88.dp).clip(RoundedCornerShape(16.dp)).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp).graphicsLayer { alpha = fade }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).then(if (selected) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
            CareMoodFace(face, Modifier.size(48.dp).saharaSelectBounce(selected, .2f).clearAndSetSemantics { })
        }
        Spacer(Modifier.height(4.dp))
        // Shrinks the label instead of splitting a word at large font sizes.
        SaharaFitText(label, Modifier.fillMaxWidth().padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
            color = if (selected) scheme.primary else scheme.onSurface, maxLines = 2, min = 12f, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

/**
 * The next dose as one row: a small ring of today's doses taken, the time ("Night · 21:00 · 2 medicines"), the names still to
 * take, and "Mark taken", which opens the time's doses in a sheet (one round tick each, and "Mark all taken"). The row opens the
 * Medicines tab. All taken: one quiet row. No medicines: "No medicines yet" with Add.
 * The dose computation is the one Today's doses card used (same rules, same save, same Undo window).
 */
@Composable private fun TodayNextDose(vm: CareModel, loaded: Boolean, onOpen: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle()
    val saved by vm.medicineEvents.collectAsStateWithLifecycle()
    val held by DoseUndoWindow.app.pending.collectAsStateWithLifecycle()
    val events = remember(saved, held, person) { DoseUndoWindow.shown(saved, held, person) }
    val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    val store = remember(vm.storageNamespace) { MedicineStripPhotos(context.applicationContext, vm.storageNamespace) }
    val photoRevision by MedicineStripPhotos.revision.collectAsStateWithLifecycle()
    val medicines = remember(items, person) { items.filter { it.kind == "medicine" && it.personId == person } }
    val doses = remember(medicines) { medicines.associate { it.id to MedicineDoses.forItem(it, scheduler.option(it.id)?.times) } }
    val byId = remember(medicines) { medicines.associateBy { it.id } }
    val groups = remember(medicines, doses) { MedicineDoseRules.groups(medicines.map { m -> DoseMedicine(m.id, m.personId, m.title, doses[m.id].orEmpty().filter { it.timed }.map { it.time }) }) }
    // As-needed (SOS) medicines have no tick to mark: they stay on the Medicines tab.
    val timed = remember(groups, byId, doses) { groups.map { g -> g to g.medicines.mapNotNull { m ->
        val item = byId[m.itemId]; val dose = doses[m.itemId].orEmpty().firstOrNull { it.time == g.time }
        if (item != null && dose != null && !MedicineDoses.isAsNeededMedicine(item)) item to dose else null
    } }.filter { it.second.isNotEmpty() } }
    val untimed = remember(medicines, doses) { medicines.filterNot { MedicineDoses.isAsNeededMedicine(it) }.flatMap { m -> doses[m.id].orEmpty().filter { !it.timed }.map { m to it } } }
    val photos = remember(medicines, photoRevision) { medicines.associate { it.id to runCatching { store.first(it.personId, it.id) }.getOrNull() } }
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    fun stateOf(item: CareItem, dose: MedicineDose) = MedicineDoses.state(item, dose, events, today, zone)
    val all = remember(timed, untimed) { timed.flatMap { it.second } + untimed }
    val taken = all.count { (item, dose) -> stateOf(item, dose) == MedicineDoseState.TAKEN }
    val next = timed.firstOrNull { (_, list) -> list.any { (item, dose) -> stateOf(item, dose) == MedicineDoseState.PENDING } }
    val untimedPending = untimed.any { (item, dose) -> stateOf(item, dose) == MedicineDoseState.PENDING }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    var whichNight by remember { mutableStateOf<Pair<String, (LocalDate) -> Unit>?>(null) }
    var sheetKey by rememberSaveable(person) { mutableStateOf<String?>(null) }
    var doseOpen by rememberSaveable(person) { mutableStateOf("") }
    val editable = canEdit && !busy && !working
    val failed = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।")
    fun labelOf(dose: MedicineDose): String = when { dose.timed -> dose.time; dose.count == 1 -> t("Today", "आज"); else -> t("Dose ${dose.index}", "खुराक ${dose.index}") }
    // One save for one dose or a whole time ("Mark all taken"); a one-tap Taken is held for Undo. A late night dose asks which night.
    fun save(entries: List<Pair<CareItem, MedicineDose>>, choice: MedicineDoseChoice, quick: Boolean) {
        val mine = entries.filter { (item, _) -> canEditCareContext(vm, item.personId, vm.accountNamespace) }
        if (mine.isEmpty()) return
        val run: (LocalDate) -> Unit = { day -> working = true; problem = ""; scope.launch {
            try { mine.forEach { (item, dose) -> markMedicineDose(context, vm, item, doses[item.id].orEmpty(), dose, choice == MedicineDoseChoice.TAKEN, quick, labelOf(dose), day, skipped = choice == MedicineDoseChoice.SKIPPED) } }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; problem = if (e is IllegalArgumentException) e.message ?: failed else failed }
            finally { working = false }
        } }
        val now = ZonedDateTime.now(zone)
        val ask = mine.firstOrNull { (item, dose) -> MedicineDoses.askWhichNight(item, dose, events, now) }
        if (ask != null) whichNight = ask.second.time to run else run(LocalDate.now(zone))
    }

    when {
        medicines.isEmpty() -> if (loaded) {
            val add: (@Composable () -> Unit)? = if (canEdit) ({ TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Add", "जोड़ें")) } }) else null
            SaharaRow(t("No medicines yet", "अभी कोई दवा नहीं"), modifier.testTag("today-doses"), icon = Icons.Default.Medication, trailing = add, chevron = false)
        }
        all.isEmpty() -> SaharaRow(t("Medicines", "दवाइयाँ"), modifier.testTag("today-doses"), icon = Icons.Default.Medication,
            supporting = CarePatientSummaryRules.medicineCount(medicines.size, hindi), onClick = onOpen)
        next == null && !untimedPending -> SaharaRow(t("Today's medicines", "आज की दवाएँ"), modifier.testTag("today-doses"), icon = Icons.Default.CheckCircle,
            supporting = t("All taken", "सब ली"), onClick = onOpen)
        else -> {
            val entries = next?.second ?: untimed
            val title = next?.first?.let { MedicineDoseRules.title(it, hindi) } ?: t("Any time", "कभी भी")
            val key = next?.first?.time ?: "any"
            val names = entries.filter { (item, dose) -> stateOf(item, dose) == MedicineDoseState.PENDING }.joinToString(", ") { it.first.title }
            val total = all.size
            val mark: (@Composable () -> Unit)? = if (canEdit) ({ FilledTonalButton(onClick = { sheetKey = key }, enabled = editable, modifier = Modifier.heightIn(min = 48.dp).testTag("today-doses-mark")) {
                Text(t("Mark taken", "ली हुई दर्ज करें"), maxLines = 1) } }) else null
            SaharaRow(title, modifier.testTag("today-doses"), supporting = names, supportingMaxLines = 1, onClick = onOpen, trailing = mark,
                leading = { SaharaRing(listOf(RingArc(taken / total.toFloat())), description = t("$taken of $total taken", "$total में से $taken ली"), size = 48.dp, stroke = 5.dp) {
                    Text("$taken/$total", style = MaterialTheme.typography.labelMedium, maxLines = 1) } })
        }
    }

    // The doses of one time, each with its round tick; "Mark all taken" when more than one is still to take.
    val sheetEntries = when (val k = sheetKey) { null -> emptyList(); "any" -> untimed; else -> timed.firstOrNull { it.first.time == k }?.second.orEmpty() }
    if (sheetKey != null && sheetEntries.isNotEmpty()) {
        val sheetTitle = timed.firstOrNull { it.first.time == sheetKey }?.first?.let { MedicineDoseRules.title(it, hindi) } ?: t("Any time", "कभी भी")
        val pending = sheetEntries.filter { (item, dose) -> stateOf(item, dose) == MedicineDoseState.PENDING }
        SaharaActionSheet(sheetTitle, emptyList(), onDismiss = { sheetKey = null }, header = {
            sheetEntries.forEach { (item, dose) -> key(item.id, dose.key) {
                DoseRow(item, dose, stateOf(item, dose), photos[item.id], supporting = "", enabled = editable, family = MedicineDoses.loggedByFamily(item, dose, events, today, zone),
                    onMark = { choice, quick -> save(listOf(item to dose), choice, quick) }, onOpen = { sheetKey = null; doseOpen = "${item.id}|${dose.key}" })
            } }
        }, footer = {
            if (pending.size > 1 && canEdit) Button(onClick = { save(pending, MedicineDoseChoice.TAKEN, true); sheetKey = null }, enabled = editable,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 56.dp).testTag("today-doses-mark-all")) {
                Text(t("Mark all taken", "सब ली हुई दर्ज करें"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { sheetKey = null }, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp).testTag("today-doses-done")) { Text(t("Done", "हो गया")) }
        })
    }
    // One dose's three choices (Taken, Not taken, Skipped), from a tap or long press on its row.
    all.firstOrNull { (item, dose) -> "${item.id}|${dose.key}" == doseOpen }?.let { (item, dose) ->
        DoseSheet(item, dose, stateOf(item, dose), photos[item.id], editable, onChoice = { choice -> save(listOf(item to dose), choice, false) },
            actions = listOf(SheetAction(t("Medicines", "दवाइयाँ"), Icons.Default.Medication, "dose-sheet-medicines") { onOpen() }), onDismiss = { doseOpen = "" })
    }
    WhichNightDialog(whichNight) { whichNight = null }
    if (problem.isNotBlank()) SaharaDialog(t("Not saved", "सहेजा नहीं गया"), onDismiss = { problem = "" }, text = problem, tone = SaharaTone.Error)
}

/**
 * "Marked taken · <medicine> · Undo" as the app's snackbar while a one-tap tick waits ([DoseUndoWindow]). The held tick is
 * written when its time ends, when the app goes to the background, or when Today closes. Write failures show as a dialog.
 */
@Composable private fun TodayDoseUndo(personId: String) {
    val t = careText(); val window = DoseUndoWindow.app
    val held by window.pending.collectAsStateWithLifecycle()
    val failure by window.failure.collectAsStateWithLifecycle()
    val mine = held?.takeIf { it.personId == personId }
    val snackbar = LocalSaharaSnackbar.current
    val lifecycle = LocalLifecycleOwner.current
    val stillMine by rememberUpdatedState(mine != null)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) window.flushSoon() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); if (stillMine) window.flushSoon() }
    }
    // A new tick replaces the snackbar; once the tick is written (or undone) the snackbar goes.
    LaunchedEffect(mine) {
        val p = mine ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("${t("Marked taken", "ली हुई दर्ज की")} · ${p.title} ${p.doseLabel}", actionLabel = t("Undo", "वापस लें"), duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) window.undo()
    }
    failure?.let { (_, message) ->
        SaharaDialog(t("Not saved", "सहेजा नहीं गया"), onDismiss = { window.failure.value = null }, text = message.ifBlank { t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।") }, tone = SaharaTone.Error)
    }
}

/**
 * The doctor view's Today (clinician), moved here unchanged from the earlier Today: "Let's talk", the latest note, the daily
 * shortcuts, the care team's requests, today's check-in rows, Up next, today's doses, To do and the journal.
 */
@Composable private fun ClinicianToday(vm: CareModel, onTalk: () -> Unit, onCare: (String) -> Unit, onAdd: (String) -> Unit, onRead: (CareVersion) -> Unit, onCalendar: () -> Unit, onWorkflow: () -> Unit, onLabs: () -> Unit, onCalendarAt: (Long) -> Unit) {
    val todayPeople by vm.people.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val selectedPerson by vm.selected.collectAsStateWithLifecycle(); val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle()
    val loaded = loadedFor == selectedPerson; val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val latest = versions.firstOrNull()
    val today = LocalDate.now()
    val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val hindi = careHindi(); val t = careText()
    val personName = todayPeople.firstOrNull { it.id == selectedPerson }?.name.orEmpty()
    val hour = LocalTime.now().hour
    val medicines = remember(items, today) { CareDailyFlow.medicinesForToday(items, today) }
    val visits = remember(items, dayStart) { CareTodayRules.upcomingVisits(items, dayStart) }
    val tasks = remember(items) { items.filter { it.kind == "task" && !CareEntryCodec.isStructured(it.detail) }.sortedBy { it.doneOn.isNotBlank() } }
    val todayCheckIn = remember(items, dayStart) { CareTodayRules.todayCheckIn(items, dayStart) }
    val todayDistress = remember(items, selectedPerson, dayStart) { DistressRules.today(items, selectedPerson, dayStart) }
    val journalNotes = remember(items) { items.filter { it.kind == "journal" && !PatientProfileCodec.isStructured(it.detail) && !SideEffectCodec.isStructured(it.detail) && !ResearchConsentCodec.isStructured(it.detail) } }
    SaharaPullRefresh(vm.refreshing, vm::refresh, enabled = vm.cloud != null) {
    LazyColumn(Modifier.fillMaxSize().background(SaharaBackdrop).saharaScreenEnter().testTag("today-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "today-header", contentType = "header") { Row(saharaTodayItem(Modifier.fillMaxWidth()).saharaStagger(0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(CareTodayRules.greetingLine(hour, hindi, personName, patient = false, viewerName = ""), Modifier.a11yHeading().testTag("today-greeting"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, color = DoctorCardInk)
                Text(today.format(careDateFormat("EEEE, d MMMM", hindi)), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.padding(top = 2.dp).testTag("today-date"))
            }
            // A labelled button, not a bare arrow icon nobody can name.
            if (vm.cloud != null) TextButton(onClick = vm::refresh, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Icon(Icons.Default.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(t("Refresh", "रिफ़्रेश करें")) }
        } }
        item(key = "goals-of-care") { ActionCard(SaharaTone.Marigold.wash, Modifier.testTag("today-goals-of-care"), onClick = onTalk) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = SaharaTone.Marigold.solid) { Icon(Icons.Default.Description, null, Modifier.padding(8.dp).size(22.dp), tint = SaharaTone.Marigold.onSolid) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Heading(t("Let's talk", "बात करें"), 22) }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Start talking", "बात शुरू करें"), Modifier.size(22.dp), tint = DoctorCardInk)
            }
        } }
        if (latest != null) item(key = "latest-note", contentType = "action-card") { ActionCard(Paper, onClick = { onRead(latest) }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t("Latest care note", "सबसे नया देखभाल नोट"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium); Text("${date(latest.published)} · ${latest.doctor}", style = MaterialTheme.typography.bodySmall); Text(ReviewWorkflow.label(latest), style = MaterialTheme.typography.labelSmall, color = RustText) }; Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Read note", "नोट पढ़ें"), Modifier.size(20.dp)) }
        } }
        item(key = "daily-shortcuts", contentType = "shortcuts") { DailyCareShortcuts(onCalendar, onWorkflow, onLabs) }
        item(key = "workflow-summary", contentType = "workflow") { WorkflowSummary(vm, onWorkflow) }
        todayCheckIn?.let { checkin -> item(key = "today-checkin-row", contentType = "care-row") { Box(saharaTodayItem()) { CareRow(checkin, vm) } } }
        todayDistress?.let { item(key = "today-distress-row", contentType = "care-row") { Box(saharaTodayItem()) { CareRow(it, vm) } } }
        item(key = "up-next-title", contentType = "section-title") { SectionTitle(t("Up next", "आगे क्या है"), if (canEdit && visits.isNotEmpty()) t("Add visit", "मुलाक़ात जोड़ें") else null) { onAdd("visit") } }
        if (visits.isEmpty() && loaded) item(key = "up-next-empty", contentType = "action-card") { ActionCard(SaharaSoftBlue, saharaTodayItem(Modifier.fillMaxWidth()), onClick = { if (canEdit) onAdd("visit") else onCalendar() }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Paper) { Icon(Icons.Default.Event, null, Modifier.padding(10.dp).size(24.dp), tint = DoctorAccent) }
                Spacer(Modifier.width(12.dp)); Text(CareTodayRules.emptyText("visits", canEdit, hindi), Modifier.weight(1f), fontSize = 16.sp, color = DoctorCardInk)
                Icon(if (canEdit) Icons.Default.Add else Icons.Default.ChevronRight, null, tint = DoctorAccent)
            }
        } }
        itemsIndexed(visits.take(2), key = { _, v -> "up-next-${v.id}" }, contentType = { _, _ -> "up-next" }) { index, v -> Box(saharaTodayItem()) {
            CareUpNextCard(v, CareTodayRules.whenLabel(v.due, today, hindi), if (index == 0) SaharaSoftBlue else SaharaApricot) { if (v.due == 0L) onCalendar() else onCalendarAt(v.due) }
        } }
        // The doses card under the title carries the one "x of y taken" count.
        item(key = "medicines-open", contentType = "section-header") { SaharaSectionHeader(t("Medicines", "दवाइयाँ"), saharaTodayItem().saharaStagger(3).testTag("today-medicines-title"), tone = SaharaTone.Plum,
            actionLabel = t("Open", "खोलें"), onAction = { onCare("Medicines") }, icon = careIcon("medicine")) }
        item(key = "today-doses", contentType = "doses") { Box(saharaTodayItem().saharaStagger(4)) { TodayDosesCard(vm) } }
        if (medicines.isEmpty() && loaded && canEdit) item(key = "medicines-add", contentType = "add-button") { SaharaSecondaryButton(t("Add a medicine", "दवा जोड़ें"), { onAdd("medicine") }, saharaTodayItem(Modifier.fillMaxWidth()), icon = Icons.Default.Add, tone = SaharaTone.Plum) }
        item(key = "todo-title", contentType = "section-title") { SectionTitle(t("To do", "करने के काम"), if (canEdit) t("Add task", "काम जोड़ें") else null) { onAdd("task") } }
        if (tasks.isEmpty() && loaded) item(key = "todo-empty", contentType = "empty") { SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Checklist, null, Modifier.size(22.dp), tint = DoctorAccent); Spacer(Modifier.width(12.dp)); Text(CareTodayRules.emptyText("tasks", canEdit, hindi), color = DoctorCardSub) } } }
        // Today stays short (24 Nov meeting): the latest few, then "See all".
        items(tasks.take(3), key = { it.id }, contentType = { "care-row" }) { Box(saharaTodayItem()) { CareRow(it, vm) } }
        if (tasks.size > 3) item(key = "todo-see-all", contentType = "see-all") { TextButton(onClick = { onCare("To do") }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(t("See all", "सब देखें")) } }
        item(key = "journal-title", contentType = "section-title") { SectionTitle(t("Journal", "डायरी"), t("Write a note", "नोट लिखें")) { onAdd("journal") } }
        val notes = journalNotes
        if (notes.isEmpty() && loaded) item(key = "journal-empty", contentType = "empty") { SurfaceCard { Text(CareTodayRules.emptyText("journal", canEdit, hindi), color = DoctorCardSub) } }
        items(notes.take(2), key = { it.id }, contentType = { "care-row" }) { Box(saharaTodayItem()) { CareRow(it, vm) } }
    }
    }
}
