@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One medicine in the daily lists (Today and Care → Medicines), kept short: strip photo, name, a one-line summary,
 * what the medicine does, one tick per dose, and History. Edit, Side effect? and Remove sit in the ⋮ menu.
 * [compact] (Today) keeps only the photo, name and dose ticks.
 */
@Composable fun MedicineCareCard(item: CareItem, vm: CareModel, compact: Boolean = false) {
    val t = careText(); val context = LocalContext.current
    val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val events by vm.medicineEvents.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var delete by rememberSaveable(item.id) { mutableStateOf(false) }
    var sideEffect by rememberSaveable(item.id) { mutableStateOf(false) }
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val store = remember(vm.storageNamespace) { MedicineStripPhotos(context.applicationContext, vm.storageNamespace) }
    val photoRevision by MedicineStripPhotos.revision.collectAsStateWithLifecycle()
    val photo = remember(item.personId, item.id, photoRevision) { runCatching { store.first(item.personId, item.id) }.getOrNull() }
    val doses = medicineDosesFor(item, vm)
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val allTaken = MedicineDoses.allTaken(MedicineDoses.states(item, doses, events, today, zone))
    val entry = remember(item.detail) { MedicineEntryText.parse(item.detail) }
    val purpose = remember(item.detail) { MedicineCardText.purpose(item.detail) }
    val rest = remember(item.detail) { MedicineCardText.rest(item.detail) }
    val summary = entry?.let { e ->
        val food = when (e.food) { MedicineMealTiming.BEFORE_FOOD -> t("Before food", "खाने से पहले"); MedicineMealTiming.WITH_FOOD -> t("With food", "खाने के साथ"); MedicineMealTiming.AFTER_FOOD -> t("After food", "खाने के बाद"); else -> "" }
        listOf(e.strength, e.howMuch, e.howOften, food).filter(String::isNotBlank).joinToString(" · ")
    }.orEmpty()
    SurfaceCard(if (allTaken) DoctorStatusGoodBg else Paper) {
        Row(verticalAlignment = Alignment.Top) {
            // The pillbox: the strip photo once one is taken; tap an empty one to add it.
            Box(Modifier.clickable(enabled = canEdit && !busy, onClickLabel = t("Edit", "बदलें")) { editing = true }.testTag("medicine-photo-${item.id}")) { StripThumb(photo, 56) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(enabled = !compact && rest.isNotBlank(), onClickLabel = if (open) t("Show less", "कम दिखाएँ") else t("Show more", "और दिखाएँ")) { open = !open }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!compact && summary.isNotBlank()) Text(summary, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub, maxLines = if (open) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                if (!compact && purpose.isNotBlank()) Text(purpose, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk, maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("medicine-purpose-${item.id}"))
                if (!compact && open && rest.isNotBlank()) Text(rest, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                if (!compact && !open && rest.isNotBlank()) Text(t("More", "और"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RustText)
            }
            if (canEdit) Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("care-menu-${item.id}")) { Icon(Icons.Default.MoreVert, t("More actions", "और विकल्प"), tint = DoctorCardInk) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(t("Edit", "बदलें"), color = DoctorCardInk) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = DoctorCardInk) }, onClick = { menu = false; editing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-edit-${item.id}"))
                    DropdownMenuItem(text = { Text(t("Side effect?", "कोई दुष्प्रभाव?"), color = DoctorCardInk) }, leadingIcon = { Icon(Icons.Default.ErrorOutline, null, tint = DoctorCardInk) }, onClick = { menu = false; sideEffect = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-side-effect-${item.id}"))
                    DropdownMenuItem(text = { Text(t("Remove", "हटाएँ"), color = RustText) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = RustText) }, onClick = { menu = false; delete = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-remove-${item.id}"))
                }
            }
        }
        MedicineDoseActions(item, vm, showHistory = !compact)
    }
    if (sideEffect) SideEffectSheet(item, vm, onDismiss = { sideEffect = false })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(t("Remove this entry?", "यह जानकारी हटाएँ?")) }, text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.remove(item); delete = false }, enabled = !busy && canEdit) { Text(t("Remove", "हटाएँ"), color = RustText, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(t("Keep", "रखें")) } })
    if (editing) AddCareItem(item.kind, vm, onDismiss = { editing = false }, initial = item)
}

/** A medicine's doses on this phone: saved times first, then this phone's reminder times, then the count in "How often". */
@Composable internal fun medicineDosesFor(item: CareItem, vm: CareModel): List<MedicineDose> {
    val context = LocalContext.current
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    return remember(item.id, item.detail) { MedicineDoses.forItem(item, scheduler.option(item.id)?.times) }
}

/**
 * Saves one dose tap: the dose's report (and, when a whole-day tick is being split, the doses it covered), then the
 * whole-day tick when it changes. Returns false when nothing could be saved.
 */
internal suspend fun recordMedicineDose(context: Context, vm: CareModel, item: CareItem, doses: List<MedicineDose>, dose: MedicineDose, taken: Boolean): Boolean {
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val current = vm.allItems.value.firstOrNull { it.id == item.id } ?: item
    val plan = MedicineDoses.plan(current, doses, vm.medicineEvents.value, dose, taken, today, zone, System.currentTimeMillis(), MedicineDoses.reporter(vm.viewRole.value))
    val repo = MedicineHistoryRepo(context.applicationContext, vm.cloud, vm.storageNamespace)
    var snapshot: MedicineHistorySnapshot? = null
    plan.events.forEach { snapshot = repo.append(current.personId, it) }
    snapshot?.let { if (vm.selected.value == current.personId) vm.medicineEvents.value = it.events }
    if ((current.doneOn == today.toString()) != (plan.doneOn == today.toString())) { vm.busy.first { !it }; vm.toggle(current) }
    return true
}

/** Under each medicine: one tick per dose (taken, missed or not yet), then History. */
@Composable internal fun MedicineDoseActions(item: CareItem, vm: CareModel, showHistory: Boolean = true) {
    val t = careText(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val events by vm.medicineEvents.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val current by vm.items.collectAsStateWithLifecycle()
    var history by rememberSaveable(item.id) { mutableStateOf(false) }
    var working by remember(item.id) { mutableStateOf(false) }
    var problem by remember(item.id) { mutableStateOf("") }
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val doses = medicineDosesFor(item, vm)
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val states = MedicineDoses.states(item, doses, events, today, zone)
    val editable = canEdit && current.any { it.id == item.id }
    val failed = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        doses.forEach { dose ->
            MedicineDoseChip(item, dose, states[dose.key] ?: MedicineDoseState.PENDING, enabled = editable && !busy && !working) { taken ->
                if (canEditCareContext(vm, item.personId, capturedAccount)) { working = true; problem = ""; scope.launch {
                    try { recordMedicineDose(context, vm, item, doses, dose, taken) }
                    catch (e: Exception) { if (e is CancellationException) throw e; problem = if (e is IllegalArgumentException) e.message ?: failed else failed }
                    finally { working = false }
                } }
            }
        }
        if (showHistory) DoseChip(Icons.Default.History, t("History", "इतिहास"), "care-medicine-history-${item.id}", true) { history = true }
    }
    if (problem.isNotBlank()) Text(problem, fontSize = 14.sp, color = RustText, modifier = Modifier.padding(top = 4.dp).announce())
    if (history) MedicineHistorySheet(item, vm) { history = false }
}

/** "08:00", "Dose 2" or "Today" for one dose chip. */
@Composable internal fun medicineDoseLabel(dose: MedicineDose): String { val t = careText()
    return when { dose.timed -> dose.time; dose.count == 1 -> t("Today", "आज"); else -> t("Dose ${dose.index}", "खुराक ${dose.index}") }
}

/** One dose: tap to choose Taken or Missed. Green when taken, red-brown when missed, amber once its time has passed unmarked. */
@Composable internal fun MedicineDoseChip(item: CareItem, dose: MedicineDose, state: MedicineDoseState, enabled: Boolean, onMark: (Boolean) -> Unit) {
    val t = careText(); var menu by remember { mutableStateOf(false) }
    val late = state == MedicineDoseState.PENDING && dose.timed && LocalTime.now() > LocalTime.parse(dose.time)
    val (icon, fg, bg) = when {
        state == MedicineDoseState.TAKEN -> Triple(Icons.Default.Check, DoctorStatusGood, DoctorStatusGoodBg)
        state == MedicineDoseState.MISSED -> Triple(Icons.Default.Close, DoctorStatusRest, DoctorStatusRestBg)
        late -> Triple(Icons.Default.RadioButtonUnchecked, DoctorStatusWarn, DoctorStatusWarnBg)
        else -> Triple(Icons.Default.RadioButtonUnchecked, DoctorCardSub, Paper)
    }
    val label = medicineDoseLabel(dose)
    val stateWord = when (state) { MedicineDoseState.TAKEN -> t("Taken", "ली"); MedicineDoseState.MISSED -> t("Missed", "छूटी"); else -> t("Not yet", "अभी नहीं") }
    Box {
        Surface(onClick = { menu = true }, enabled = enabled, shape = RoundedCornerShape(50), color = bg, border = if (state == MedicineDoseState.PENDING && !late) BorderStroke(1.dp, DoctorCardFaint.copy(alpha = .4f)) else null,
            modifier = Modifier.minimumInteractiveComponentSize().semantics { contentDescription = "${item.title}, $label: $stateWord" }.testTag("medicine-dose-${item.id}-${dose.key}")) {
            // A clear time chip: the dose time in bold; the state shows as icon and colour (the word is in the description).
            Row(Modifier.heightIn(min = 40.dp).padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = fg); Spacer(Modifier.width(6.dp))
                Text(label, color = if (state == MedicineDoseState.PENDING && !late) DoctorCardInk else fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(t("Taken", "ली"), color = DoctorCardInk) }, leadingIcon = { Icon(Icons.Default.Check, null, tint = DoctorStatusGood) }, onClick = { menu = false; onMark(true) }, modifier = Modifier.heightIn(min = 48.dp).testTag("medicine-dose-taken-${item.id}-${dose.key}"))
            DropdownMenuItem(text = { Text(t("Missed", "छूटी"), color = DoctorCardInk) }, leadingIcon = { Icon(Icons.Default.Close, null, tint = DoctorStatusRest) }, onClick = { menu = false; onMark(false) }, modifier = Modifier.heightIn(min = 48.dp).testTag("medicine-dose-missed-${item.id}-${dose.key}"))
        }
    }
}

/** A small "?" that opens a short explanation, in place of helper text on the screen (3 Nov meeting). */
@Composable internal fun MedicineInfoButton(title: String, text: String, tag: String = "medicine-info") {
    val t = careText(); var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp).testTag(tag)) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, t("More information", "और जानकारी"), Modifier.size(22.dp), tint = RustText) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = { open = false }) { Text(t("OK", "ठीक है")) } })
}

/** One medicine's reports, newest first, then its side-effect notes. */
@Composable private fun MedicineHistorySheet(item: CareItem, vm: CareModel, onDismiss: () -> Unit) {
    val t = careText()
    val events by vm.medicineEvents.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var sideEffect by rememberSaveable(item.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        val zone = ZoneId.systemDefault()
        val mine = events.filter { it.medicineId == item.id }.let { all -> all.filterNot { MedicineDoses.redundantCheckoff(it, all, zone) } }
        val now = System.currentTimeMillis()
        LazyColumn(Modifier.fillMaxWidth().testTag("care-medicine-history-sheet"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Heading("${item.title} · ${t("History", "इतिहास")}", 24) }
            // Friendly empty state: icon and one line; the Side effect? action sits below.
            if (mine.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.History, null, Modifier.size(32.dp), tint = DoctorCardSub)
                Text(t("No reports yet.", "अभी कोई जानकारी नहीं।"), fontSize = 16.sp, color = DoctorCardInk)
            } }
            items(mine, key = { it.id }) { CompactMedicineReport(it, mine, now) }
            medicineSideEffectItems(SideEffectLog.forMedicine(SideEffectLog.entries(allItems, item.personId), item.id), vm)
            if (canEdit) item { OutlinedButton(onClick = { sideEffect = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).testTag("care-medicine-history-side-effect")) { Icon(Icons.Default.ErrorOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Side effect?", "कोई दुष्प्रभाव?"), fontSize = 16.sp) } }
            if (vm.cloud != null) item { TextButton(onClick = vm::refreshMedicineHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Refresh history", "इतिहास ताज़ा करें"), fontSize = 15.sp) } }
        }
    }
    if (sideEffect) SideEffectSheet(item, vm, onDismiss = { sideEffect = false })
}

/** A small rounded action capsule under a medicine's details. */
@Composable private fun DoseChip(icon: ImageVector, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    // Slim capsule so the actions fit on one line; the touch area still meets 48dp.
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50), color = Mint, modifier = Modifier.minimumInteractiveComponentSize().testTag(tag)) {
        Row(Modifier.heightIn(min = 40.dp).padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = RustText); Spacer(Modifier.width(6.dp))
            Text(label, color = DoctorCardInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

private val reportTime = DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a").withZone(ZoneId.systemDefault())
private val doseDay = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())
private val doseClock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private const val FAMILY_ONLY_NOTE = "Family report only. Not confirmed as taken or checked by anyone else."

/** One compact card per report: status badge and main time, then one muted reporter line. */
@Composable private fun CompactMedicineReport(event: MedicineIntakeEvent, all: List<MedicineIntakeEvent>, now: Long) {
    val t = careText()
    val status = MedicineHistoryRules.status(event, all, now)
    val (word, fg, bg) = when {
        event.outcome == "skipped" -> Triple(t("Missed", "छूटी"), DoctorStatusRest, DoctorStatusRestBg)
        status.startsWith("Late") -> Triple(t("Late", "देर से"), DoctorStatusWarn, DoctorStatusWarnBg)
        event.outcome == "taken" -> Triple(t("Taken", "ली"), DoctorStatusGood, DoctorStatusGoodBg)
        else -> Triple(t(status, MedicineStatusHindi[status] ?: status), DoctorStatusWarn, DoctorStatusWarnBg)
    }
    val doseAt = event.plannedAt.takeIf { event.outcome in setOf("taken", "skipped") && event.reportedTakenAt == null }
    val main = when {
        // A dose tick: its dose time and day (the exact swallowed time is never recorded).
        doseAt != null -> { val at = doseClock.format(Instant.ofEpochMilli(doseAt)); t("$at dose", "$at की खुराक") + " · " + doseDay.format(Instant.ofEpochMilli(doseAt)) }
        event.outcome == "taken" -> event.reportedTakenAt?.let { reportTime.format(Instant.ofEpochMilli(it)) }
            ?: event.reportedDate.takeIf { it.isNotBlank() }?.let { t("$it · exact time not recorded", "$it · सही समय दर्ज नहीं") }
            ?: t("Exact time not recorded", "सही समय दर्ज नहीं")
        else -> event.plannedAt?.let { t("Planned: ", "तय: ") + reportTime.format(Instant.ofEpochMilli(it)) } ?: reportTime.format(Instant.ofEpochMilli(event.loggedAt))
    }
    SurfaceCard(Paper) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = bg, shape = RoundedCornerShape(50)) { Text(word, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg) }
            Spacer(Modifier.width(12.dp))
            Text(main, Modifier.weight(1f), fontSize = 15.sp, color = DoctorCardInk)
            if (MedicineHistoryRules.loggedByFamily(event)) SymptomLoggedByFamilyChip(careHindi(), "medicine-report-family-${event.id}")
        }
        Text(t("Reported by ", "बताने वाले: ") + event.reporter.ifBlank { t("Not recorded", "दर्ज नहीं") } + " · " + t("Logged: ", "दर्ज: ") + reportTime.format(Instant.ofEpochMilli(event.loggedAt)), Modifier.padding(top = 8.dp), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
        if (event.note.isNotBlank() && event.note != FAMILY_ONLY_NOTE && !event.note.startsWith("Planned dose.") && event.plannedAt == null) Text(event.note, Modifier.padding(top = 4.dp), fontSize = 15.sp, color = DoctorCardInk)
    }
}
