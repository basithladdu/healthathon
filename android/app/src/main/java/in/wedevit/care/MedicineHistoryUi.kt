@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
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
    val saved by vm.medicineEvents.collectAsStateWithLifecycle()
    val held by DoseUndoWindow.app.pending.collectAsStateWithLifecycle()
    val events = DoseUndoWindow.shown(saved, held, item.personId)
    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var delete by rememberSaveable(item.id) { mutableStateOf(false) }
    var sideEffect by rememberSaveable(item.id) { mutableStateOf(false) }
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
    // Medicines plum wash (green once every dose today is taken), so each medicine reads as part of its section.
    SurfaceCard(if (allTaken) DoctorStatusGoodBg else SaharaSection.Medicines.wash) {
        Row(verticalAlignment = Alignment.Top) {
            // The pillbox: the strip photo once one is taken; tap an empty one to add it.
            Box(Modifier.clickable(enabled = canEdit && !busy, onClickLabel = t("Edit", "बदलें")) { editing = true }.testTag("medicine-photo-${item.id}")) { StripThumb(photo, 56) }
            Spacer(Modifier.width(12.dp))
            // Everything about the medicine is shown at once: no tap-to-expand (20 Oct rule: no collapsible sections).
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!compact && summary.isNotBlank()) Text(summary, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                if (!compact && purpose.isNotBlank()) Text(purpose, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk, modifier = Modifier.testTag("medicine-purpose-${item.id}"))
                if (!compact && rest.isNotBlank()) Text(rest, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
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

/**
 * Saves one dose tap. A one-tap Taken ([quick]) is held for a few seconds with "Marked taken · Undo" ([DoseUndoWindow]);
 * Undo then writes nothing. A choice from the Taken / Missed menu is written at once, after any held tick.
 */
internal suspend fun markMedicineDose(context: Context, vm: CareModel, item: CareItem, doses: List<MedicineDose>, dose: MedicineDose, taken: Boolean, quick: Boolean, label: String) {
    val window = DoseUndoWindow.app
    if (!(quick && taken)) { window.flush(); recordMedicineDose(context, vm, item, doses, dose, taken); return }
    val zone = ZoneId.systemDefault(); val app = context.applicationContext
    val shown = MedicineDoses.report(item, dose, true, LocalDate.now(zone), zone, System.currentTimeMillis(), MedicineDoses.reporter(vm.viewRole.value))
    window.hold(DoseUndoWindow.Pending(item.personId, item.id, dose.key, item.title, label, shown) { recordMedicineDose(app, vm, item, doses, dose, true) })
}

/**
 * "Marked taken · Undo" under a dose list while a one-tap tick waits, with a bar that runs down for the few seconds Undo
 * is offered. [itemId] limits it to one medicine (Care list); null shows any of the person's medicines (Today).
 * The held tick is written when the app goes to the background or the list leaves the screen.
 */
@Composable internal fun DoseUndoRow(personId: String, itemId: String? = null) {
    val t = careText(); val window = DoseUndoWindow.app
    val held by window.pending.collectAsStateWithLifecycle()
    val failure by window.failure.collectAsStateWithLifecycle()
    val mine = held?.takeIf { it.personId == personId && (itemId == null || it.itemId == itemId) }
    val lifecycle = LocalLifecycleOwner.current
    val stillMine by rememberUpdatedState(mine != null)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) window.flushSoon() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); if (stillMine) window.flushSoon() }
    }
    // Keeps the last tick on screen while the row slides away.
    var last by remember { mutableStateOf<DoseUndoWindow.Pending?>(null) }
    if (mine != null) last = mine
    AnimatedVisibility(mine != null, enter = fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + expandVertically(SaharaMotion.tween(SaharaMotion.SAVED_MS)),
        exit = fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS)) + shrinkVertically(SaharaMotion.tween(SaharaMotion.EXIT_MS)), label = "dose-undo") {
        val p = last ?: return@AnimatedVisibility
        Surface(shape = RoundedCornerShape(SaharaRadius.Md), color = SaharaTone.Success.wash, border = BorderStroke(1.5.dp, SaharaTone.Success.solid), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("dose-undo-${p.itemId}-${p.doseKey}")) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp).announce(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, Modifier.size(22.dp), tint = SaharaTone.Success.ink)
                    Spacer(Modifier.width(10.dp))
                    Text("${t("Marked taken", "ली हुई दर्ज की")} · ${p.title} ${p.doseLabel}", Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { window.undo() }, modifier = Modifier.heightIn(min = 48.dp).testTag("dose-undo-button"), colors = ButtonDefaults.textButtonColors(contentColor = SaharaTone.Success.ink)) {
                        Text(t("Undo", "वापस लें"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // The time left for Undo, as a bar that empties. Hidden when the phone's animations are off.
                if (SaharaMotion.enabled) {
                    val left = remember(p) { Animatable(1f) }
                    LaunchedEffect(p) { left.animateTo(0f, tween(DoseUndoWindow.WINDOW_MS.toInt(), easing = LinearEasing)) }
                    LinearProgressIndicator(progress = { left.value }, modifier = Modifier.fillMaxWidth().height(4.dp), color = SaharaTone.Success.solid, trackColor = Color.Transparent, drawStopIndicator = {}, gapSize = 0.dp)
                }
            }
        }
    }
    failure?.takeIf { itemId == null || it.first == itemId }?.let { (_, message) ->
        SaharaDialog(t("Not saved", "सहेजा नहीं गया"), onDismiss = { window.failure.value = null }, text = message.ifBlank { t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।") }, tone = SaharaTone.Error)
    }
}

/** Under each medicine: one tick per dose (taken, missed or not yet), then History. */
@Composable internal fun MedicineDoseActions(item: CareItem, vm: CareModel, showHistory: Boolean = true) {
    val t = careText(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val saved by vm.medicineEvents.collectAsStateWithLifecycle()
    val held by DoseUndoWindow.app.pending.collectAsStateWithLifecycle()
    val events = DoseUndoWindow.shown(saved, held, item.personId)
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
    // Dose chips wrap, so every dose of the day is visible without sideways scrolling.
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        doses.forEach { dose ->
            val label = medicineDoseLabel(dose)
            MedicineDoseChip(item, dose, states[dose.key] ?: MedicineDoseState.PENDING, enabled = editable && !busy && !working) { taken, quick ->
                if (canEditCareContext(vm, item.personId, capturedAccount)) { working = true; problem = ""; scope.launch {
                    try { markMedicineDose(context, vm, item, doses, dose, taken, quick, label) }
                    catch (e: Exception) { if (e is CancellationException) throw e; problem = if (e is IllegalArgumentException) e.message ?: failed else failed }
                    finally { working = false }
                } }
            }
        }
        if (showHistory) DoseChip(Icons.Default.History, t("History", "इतिहास"), "care-medicine-history-${item.id}", true) { history = true }
    }
    DoseUndoRow(item.personId, item.id)
    if (problem.isNotBlank()) SaharaDialog(t("Not saved", "सहेजा नहीं गया"), onDismiss = { problem = "" }, text = problem, tone = SaharaTone.Error)
    if (history) MedicineHistorySheet(item, vm) { history = false }
}

/** "08:00", "Dose 2" or "Today" for one dose chip. */
@Composable internal fun medicineDoseLabel(dose: MedicineDose): String { val t = careText()
    return when { dose.timed -> dose.time; dose.count == 1 -> t("Today", "आज"); else -> t("Dose ${dose.index}", "खुराक ${dose.index}") }
}

/**
 * One dose. A dose not marked yet is ticked Taken with one tap; a marked dose (or a long press) opens Taken / Missed,
 * so a mistake can be corrected. Green when taken, red-brown when missed, amber once its time has passed unmarked.
 */
@Composable internal fun MedicineDoseChip(item: CareItem, dose: MedicineDose, state: MedicineDoseState, enabled: Boolean, onMark: (taken: Boolean, quick: Boolean) -> Unit) {
    val t = careText(); var menu by remember { mutableStateOf(false) }
    val late = state == MedicineDoseState.PENDING && dose.timed && LocalTime.now() > LocalTime.parse(dose.time)
    // Solid once there is a state (green taken, red missed, amber late) with a white icon and time; white with a plum edge before.
    val (fg, bgTarget) = when {
        state == MedicineDoseState.TAKEN -> SaharaTone.Success.onSolid to SaharaTone.Success.solid
        state == MedicineDoseState.MISSED -> SaharaTone.Error.onSolid to SaharaTone.Error.solid
        late -> SaharaTone.Warning.onSolid to SaharaTone.Warning.solid
        else -> SaharaSection.Medicines.ink to Paper
    }
    val bg by animateColorAsState(bgTarget, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "dose-chip")
    val label = medicineDoseLabel(dose)
    val stateWord = when (state) { MedicineDoseState.TAKEN -> t("Taken", "ली"); MedicineDoseState.MISSED -> t("Missed", "छूटी"); else -> t("Not yet", "अभी नहीं") }
    val pending = state == MedicineDoseState.PENDING
    val tapLabel = if (pending) t("Mark taken", "ली हुई दर्ज करें") else t("Change", "बदलें")
    val holdLabel = t("Taken or missed", "ली या छूटी")
    Box {
        Surface(shape = RoundedCornerShape(50), color = bg, border = if (pending && !late) BorderStroke(1.5.dp, SaharaSection.Medicines.solid) else null,
            modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(50))
                .combinedClickable(enabled = enabled, role = Role.Button, onClickLabel = tapLabel, onLongClickLabel = holdLabel, onLongClick = { menu = true }) { if (pending) onMark(true, true) else menu = true }
                .semantics { contentDescription = "${item.title}, $label: $stateWord" }.testTag("medicine-dose-${item.id}-${dose.key}")) {
            // A clear time chip: the dose time in bold; the state shows as icon and colour (the word is in the description).
            Row(Modifier.heightIn(min = 40.dp).padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                DoseStateIcon(state, fg, 18)
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (pending && !late) DoctorCardInk else fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(t("Taken", "ली"), color = DoctorCardInk) }, leadingIcon = { Icon(Icons.Default.Check, null, tint = DoctorStatusGood) }, onClick = { menu = false; onMark(true, false) }, modifier = Modifier.heightIn(min = 48.dp).testTag("medicine-dose-taken-${item.id}-${dose.key}"))
            DropdownMenuItem(text = { Text(t("Missed", "छूटी"), color = DoctorCardInk) }, leadingIcon = { Icon(Icons.Default.Close, null, tint = DoctorStatusRest) }, onClick = { menu = false; onMark(false, false) }, modifier = Modifier.heightIn(min = 48.dp).testTag("medicine-dose-missed-${item.id}-${dose.key}"))
        }
    }
}

/** The dose's state as an icon that pops in when it changes: a tick once taken, a cross once missed, an empty ring before. */
@Composable internal fun DoseStateIcon(state: MedicineDoseState, tint: androidx.compose.ui.graphics.Color, size: Int) {
    AnimatedContent(state, transitionSpec = {
        (fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS, easing = SaharaMotion.Emphasized), initialScale = 0.4f)) togetherWith fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS))
    }, label = "dose-state") { shown ->
        Icon(when (shown) { MedicineDoseState.TAKEN -> Icons.Default.Check; MedicineDoseState.MISSED -> Icons.Default.Close; else -> Icons.Default.RadioButtonUnchecked }, null, Modifier.size(size.dp), tint = tint)
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
            item { SaharaSectionBand("${item.title} · ${t("History", "इतिहास")}", SaharaSection.Medicines) }
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
    // White capsule with a solid plum edge on the plum card, so it reads as a button.
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50), color = Paper, border = BorderStroke(1.5.dp, SaharaSection.Medicines.solid), modifier = Modifier.minimumInteractiveComponentSize().testTag(tag)) {
        Row(Modifier.heightIn(min = 40.dp).padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = SaharaSection.Medicines.ink); Spacer(Modifier.width(6.dp))
            Text(label, color = SaharaSection.Medicines.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
    // Solid status pills: green taken, amber late, red missed, bold white word.
    val (word, pillTone) = when {
        event.outcome == "skipped" -> t("Missed", "छूटी") to SaharaTone.Error
        status.startsWith("Late") -> t("Late", "देर से") to SaharaTone.Warning
        event.outcome == "taken" -> t("Taken", "ली") to SaharaTone.Success
        else -> t(status, MedicineStatusHindi[status] ?: status) to SaharaTone.Warning
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
            Surface(color = pillTone.solid, shape = RoundedCornerShape(50)) { Text(word, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = pillTone.onSolid) }
            Spacer(Modifier.width(12.dp))
            Text(main, Modifier.weight(1f), fontSize = 15.sp, color = DoctorCardInk)
            if (MedicineHistoryRules.loggedByFamily(event)) SymptomLoggedByFamilyChip(careHindi(), "medicine-report-family-${event.id}")
        }
        Text(t("Reported by ", "बताने वाले: ") + event.reporter.ifBlank { t("Not recorded", "दर्ज नहीं") } + " · " + t("Logged: ", "दर्ज: ") + reportTime.format(Instant.ofEpochMilli(event.loggedAt)), Modifier.padding(top = 8.dp), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
        if (event.note.isNotBlank() && event.note != FAMILY_ONLY_NOTE && !event.note.startsWith("Planned dose.") && event.plannedAt == null) Text(event.note, Modifier.padding(top = 4.dp), fontSize = 15.sp, color = DoctorCardInk)
    }
}
