@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
    val events = remember(saved, held, item.personId) { DoseUndoWindow.shown(saved, held, item.personId) }
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
    val allTaken = remember(item, doses, events, today) { MedicineDoses.allTaken(MedicineDoses.states(item, doses, events, today, zone)) }
    val entry = remember(item.detail) { MedicineEntryText.parse(item.detail) }
    // Known phrases ("For pain.", "Take with food.") show in the reader's language; the saved words never change (MedicinePhrases).
    val lang = careLanguage()
    val purpose = remember(item.detail, lang) { MedicinePhrases.shown(MedicineCardText.purpose(item.detail), lang) }
    val rest = remember(item.detail, lang) { MedicinePhrases.shown(MedicineCardText.rest(item.detail), lang) }
    val summary = entry?.let { e ->
        val food = when (e.food) { MedicineMealTiming.BEFORE_FOOD -> t("Before food", "खाने से पहले"); MedicineMealTiming.WITH_FOOD -> t("With food", "खाने के साथ"); MedicineMealTiming.AFTER_FOOD -> t("After food", "खाने के बाद"); else -> "" }
        val often = MedicineFrequencyRules.fromText(e.howOften)?.takeIf { it.english.equals(e.howOften.trim(), true) }?.let { t(it.english, it.hindi) } ?: MedicinePhrases.shown(e.howOften, lang)
        listOf(e.strength, e.howMuch, often, food).filter(String::isNotBlank).joinToString(" · ")
    }.orEmpty()
    // Medicines plum wash (green once every dose today is taken), so each medicine reads as part of its section.
    // The wash glides to green as the last dose is ticked (colour only; the card's size never animates).
    val cardWash by animateColorAsState(if (allTaken) DoctorStatusGoodBg else SaharaSection.Medicines.wash, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "medicine-card-wash")
    SurfaceCard(cardWash) {
        Row(verticalAlignment = Alignment.Top) {
            // The pillbox: the strip photo once one is taken; tap an empty one to add it.
            Box(Modifier.clickable(enabled = canEdit && !busy, onClickLabel = t("Edit", "बदलें")) { editing = true }.testTag("medicine-photo-${item.id}")) { StripThumb(photo, 56) }
            Spacer(Modifier.width(12.dp))
            // Everything about the medicine is shown at once: no tap-to-expand (20 Oct rule: no collapsible sections).
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (MedicineAlarmRules.isImportant(item)) MedicineAlarmTag(Modifier.testTag("medicine-card-alarm-${item.id}"))
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
internal suspend fun recordMedicineDose(context: Context, vm: CareModel, item: CareItem, doses: List<MedicineDose>, dose: MedicineDose, taken: Boolean, day: LocalDate = LocalDate.now(ZoneId.systemDefault()),
                                        skipped: Boolean = false): Boolean {
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val current = vm.allItems.value.firstOrNull { it.id == item.id } ?: item
    val plan = MedicineDoses.plan(current, doses, vm.medicineEvents.value, dose, taken, day, zone, System.currentTimeMillis(), MedicineDoses.reporter(vm.viewRole.value), skipped = skipped && !taken)
    val repo = MedicineHistoryRepo(context.applicationContext, vm.cloud, vm.storageNamespace)
    var snapshot: MedicineHistorySnapshot? = null
    plan.events.forEach { snapshot = repo.append(current.personId, it) }
    snapshot?.let { if (vm.selected.value == current.personId) vm.medicineEvents.value = it.events }
    if (taken) runCatching { CareReminderScheduler(context.applicationContext, vm.accountNamespace).markTaken(current.id) }
    // A morphine dose marked taken: ask (at most monthly per medicine) where it came from.
    if (taken && MorphineSourceRules.isMorphine(current.title) && MorphineSourceRules.shouldAsk(context, current.id)) MorphineSourcePrompt.ask.value = current
    // The whole-day tick is only ever today's, so a dose saved for last night leaves it as it is.
    if (day == today && (current.doneOn == today.toString()) != (plan.doneOn == today.toString())) { vm.busy.first { !it }; vm.toggle(current) }
    return true
}

/** "Took a dose now" for an as-needed medicine: one more taken report with this moment's time. */
internal suspend fun recordAsNeededDose(context: Context, vm: CareModel, item: CareItem): Boolean {
    val event = MedicineDoses.asNeededDose(item, System.currentTimeMillis(), MedicineDoses.reporter(vm.viewRole.value))
    val snapshot = MedicineHistoryRepo(context.applicationContext, vm.cloud, vm.storageNamespace).append(item.personId, event)
    if (vm.selected.value == item.personId) vm.medicineEvents.value = snapshot.events
    runCatching { CareReminderScheduler(context.applicationContext, vm.accountNamespace).markTaken(item.id) }
    if (MorphineSourceRules.isMorphine(item.title) && MorphineSourceRules.shouldAsk(context, item.id)) MorphineSourcePrompt.ask.value = item
    return true
}

/**
 * Saves one dose tap. A one-tap Taken ([quick]) is held for a few seconds with "Marked taken · Undo" ([DoseUndoWindow]);
 * Undo then writes nothing. A choice from the Taken / Not taken / Skipped menu is written at once, after any held tick.
 * [skipped] (with [taken] false) saves Skipped, left out on purpose, instead of Not taken.
 */
internal suspend fun markMedicineDose(context: Context, vm: CareModel, item: CareItem, doses: List<MedicineDose>, dose: MedicineDose, taken: Boolean, quick: Boolean, label: String,
                                      day: LocalDate = LocalDate.now(ZoneId.systemDefault()), skipped: Boolean = false) {
    val window = DoseUndoWindow.app
    if (!(quick && taken)) { window.flush(); recordMedicineDose(context, vm, item, doses, dose, taken, day, skipped); return }
    val zone = ZoneId.systemDefault(); val app = context.applicationContext
    val shown = MedicineDoses.report(item, dose, true, day, zone, System.currentTimeMillis(), MedicineDoses.reporter(vm.viewRole.value))
    window.hold(DoseUndoWindow.Pending(item.personId, item.id, dose.key, item.title, label, shown) { recordMedicineDose(app, vm, item, doses, dose, true, day) })
}

/**
 * A night dose marked after midnight ([MedicineDoses.askWhichNight]): was it last night's, given late, or tonight's?
 * [ask] is the dose time and what to save for the chosen day. Closing the dialog saves nothing.
 */
@Composable internal fun WhichNightDialog(ask: Pair<String, (LocalDate) -> Unit>?, onDismiss: () -> Unit) {
    val t = careText(); val (time, save) = ask ?: return
    val today = LocalDate.now(ZoneId.systemDefault())
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("Which night's dose?", "किस रात की खुराक?")) },
        text = { Text(t("It is past midnight. Is this last night's $time dose, given late, or tonight's?", "आधी रात बीत चुकी है। क्या यह कल रात की $time वाली खुराक है, जो देर से दी गई, या आज रात की?")) },
        confirmButton = { TextButton(onClick = { onDismiss(); save(today.minusDays(1)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("dose-which-night-last")) { Text(t("Last night's", "कल रात की"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { onDismiss(); save(today) }, modifier = Modifier.heightIn(min = 48.dp).testTag("dose-which-night-tonight")) { Text(t("Tonight's", "आज रात की")) } })
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
                    SaharaLottie(SaharaLottieKind.Success, Modifier.size(28.dp))
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
    val events = remember(saved, held, item.personId) { DoseUndoWindow.shown(saved, held, item.personId) }
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val current by vm.items.collectAsStateWithLifecycle()
    var history by rememberSaveable(item.id) { mutableStateOf(false) }
    var working by remember(item.id) { mutableStateOf(false) }
    var problem by remember(item.id) { mutableStateOf("") }
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val doses = medicineDosesFor(item, vm)
    var whichNight by remember(item.id) { mutableStateOf<Pair<String, (LocalDate) -> Unit>?>(null) }
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val states = remember(item, doses, events, today) { MedicineDoses.states(item, doses, events, today, zone) }
    val editable = remember(canEdit, current, item.id) { canEdit && current.any { it.id == item.id } }
    val failed = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।")
    // Dose chips wrap, so every dose of the day is visible without sideways scrolling.
    // A weekly, monthly or every-few-days medicine has no daily tick: its directions say when it is due.
    if (doses.isEmpty() && MedicineDoses.notDaily(item)) Text(t("Not a daily medicine. Follow its directions for when to give it.", "यह रोज़ की दवा नहीं है। कब देनी है, इसके निर्देश देखें।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub, modifier = Modifier.padding(top = 8.dp).testTag("medicine-not-daily-${item.id}"))
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        doses.forEach { dose ->
            val label = medicineDoseLabel(dose)
            MedicineDoseChip(item, dose, states[dose.key] ?: MedicineDoseState.PENDING, enabled = editable && !busy && !working) { choice, quick ->
                val taken = choice == MedicineDoseChoice.TAKEN
                val save: (LocalDate) -> Unit = { day -> if (canEditCareContext(vm, item.personId, capturedAccount)) { working = true; problem = ""; scope.launch {
                    try { markMedicineDose(context, vm, item, doses, dose, taken, quick, label, day, skipped = choice == MedicineDoseChoice.SKIPPED) }
                    catch (e: Exception) { if (e is CancellationException) throw e; problem = if (e is IllegalArgumentException) e.message ?: failed else failed }
                    finally { working = false }
                } } }
                if (MedicineDoses.askWhichNight(item, dose, events, ZonedDateTime.now(zone))) whichNight = dose.time to save else save(LocalDate.now(zone))
            }
        }
        if (showHistory) DoseChip(Icons.Default.History, t("History", "इतिहास"), "care-medicine-history-${item.id}", true) { history = true }
    }
    DoseUndoRow(item.personId, item.id)
    WhichNightDialog(whichNight) { whichNight = null }
    if (problem.isNotBlank()) SaharaDialog(t("Not saved", "सहेजा नहीं गया"), onDismiss = { problem = "" }, text = problem, tone = SaharaTone.Error)
    if (history) MedicineHistorySheet(item, vm) { history = false }
}

/** "08:00", "Dose 2" or "Today" for one dose chip. */
@Composable internal fun medicineDoseLabel(dose: MedicineDose): String { val t = careText()
    return when { dose.timed -> dose.time; dose.count == 1 -> t("Today", "आज"); else -> t("Dose ${dose.index}", "खुराक ${dose.index}") }
}

/**
 * One dose. A dose not marked yet is ticked Taken with one tap; a marked dose (or a long press) opens Taken / Not taken / Skipped,
 * so a mistake can be corrected. Green when taken, red-brown when not taken, grey when skipped, amber once it is an hour past its
 * time and still unmarked ([MedicineLateRule]).
 */
@Composable internal fun MedicineDoseChip(item: CareItem, dose: MedicineDose, state: MedicineDoseState, enabled: Boolean, onMark: (choice: MedicineDoseChoice, quick: Boolean) -> Unit) {
    val t = careText(); var menu by remember { mutableStateOf(false) }
    val late = state == MedicineDoseState.PENDING && dose.timed && MedicineLateRule.overdue(dose.time, LocalTime.now())
    // Solid once there is a state (green taken, red not taken, grey skipped, amber late) with a white icon and time; white with a plum edge before.
    val (fg, bgTarget) = when {
        state == MedicineDoseState.TAKEN -> SaharaTone.Success.onSolid to SaharaTone.Success.solid
        state == MedicineDoseState.MISSED -> SaharaTone.Error.onSolid to SaharaTone.Error.solid
        state == MedicineDoseState.SKIPPED -> SaharaTone.Neutral.onSolid to SaharaTone.Neutral.solid
        late -> SaharaTone.Warning.onSolid to SaharaTone.Warning.solid
        else -> SaharaSection.Medicines.ink to Paper
    }
    val bg by animateColorAsState(bgTarget, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "dose-chip")
    val label = medicineDoseLabel(dose)
    val stateWord = doseStateWord(state)
    val pending = state == MedicineDoseState.PENDING
    val tapLabel = if (pending) t("Mark taken", "ली हुई दर्ज करें") else t("Change", "बदलें")
    val holdLabel = t("Taken, not taken or skipped", "ली, नहीं ली या छोड़ी")
    val haptic = rememberSaharaHaptic(); val tick = rememberSaharaTick()
    Box {
        // The chip answers a tick with one soft bounce as it turns green (draw-only; the tap is never delayed).
        Surface(shape = RoundedCornerShape(50), color = bg, border = if (pending && !late) BorderStroke(1.5.dp, SaharaSection.Medicines.solid) else null,
            modifier = Modifier.saharaSelectBounce(state == MedicineDoseState.TAKEN, .08f).minimumInteractiveComponentSize().clip(RoundedCornerShape(50))
                .combinedClickable(enabled = enabled, role = Role.Button, onClickLabel = tapLabel, onLongClickLabel = holdLabel, onLongClick = { tick(); menu = true }) { if (pending) { haptic(); onMark(MedicineDoseChoice.TAKEN, true) } else { tick(); menu = true } }
                .semantics { contentDescription = "${item.title}, $label: $stateWord" }.testTag("medicine-dose-${item.id}-${dose.key}")) {
            // A clear time chip: the dose time in bold; the state shows as icon and colour (the word is in the description).
            Row(Modifier.heightIn(min = 40.dp).padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                // Before it is marked, the sun or moon of its time (point 6); then the tick, cross or dash on the chip's colour.
                if (pending && !late && dose.timed) DoseSlotIcon(MedicineDoseRules.slot(dose.time), 20, describe = false) else DoseStateIcon(state, fg, if (pending) 18 else 22)
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (pending && !late) DoctorCardInk else fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        DoseChoiceMenu(menu, { menu = false }, "medicine-dose", "${item.id}-${dose.key}") { if (it == MedicineDoseChoice.TAKEN) haptic(); onMark(it, false) }
    }
}

/** The word for a dose's state: Taken, Not taken, Skipped or Not yet. */
@Composable internal fun doseStateWord(state: MedicineDoseState): String { val t = careText()
    return when (state) { MedicineDoseState.TAKEN -> t("Taken", "ली"); MedicineDoseState.MISSED -> t("Not taken", "नहीं ली"); MedicineDoseState.SKIPPED -> t("Skipped", "छोड़ी"); else -> t("Not yet", "अभी नहीं") }
}

/**
 * Taken / Not taken / Skipped: the only three answers for a dose (Dr Shirin, 24 Nov 2026). Each row is 56 dp. Test tags are
 * "[tagPrefix]-taken-[tagSuffix]", "[tagPrefix]-missed-[tagSuffix]" (Not taken keeps its older tag) and "[tagPrefix]-skipped-[tagSuffix]".
 */
@Composable internal fun DoseChoiceMenu(expanded: Boolean, onDismiss: () -> Unit, tagPrefix: String, tagSuffix: String, onChoose: (MedicineDoseChoice) -> Unit) {
    val t = careText()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MedicineDoseChoice.entries.forEach { choice ->
            val (icon, tint, word) = when (choice) {
                MedicineDoseChoice.TAKEN -> Triple(Icons.Default.Check, DoctorStatusGood, "taken")
                MedicineDoseChoice.NOT_TAKEN -> Triple(Icons.Default.Close, DoctorStatusRest, "missed")
                MedicineDoseChoice.SKIPPED -> Triple(Icons.Default.Remove, DoctorCardSub, "skipped")
            }
            DropdownMenuItem(text = { Text(t(choice.english, choice.hindi), color = DoctorCardInk, fontSize = 16.sp) }, leadingIcon = { Icon(icon, null, tint = tint) },
                onClick = { onDismiss(); onChoose(choice) }, modifier = Modifier.heightIn(min = 56.dp).testTag("$tagPrefix-$word-$tagSuffix"))
        }
    }
}

/** The dose's state as an icon that pops in when it changes: a tick once taken, a cross once not taken, a dash once skipped, an empty ring before. */
@Composable internal fun DoseStateIcon(state: MedicineDoseState, tint: androidx.compose.ui.graphics.Color, size: Int) {
    AnimatedContent(state, transitionSpec = {
        (fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS, easing = SaharaMotion.Emphasized), initialScale = 0.4f)) togetherWith fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS))
    }, label = "dose-state") { shown ->
        Icon(when (shown) { MedicineDoseState.TAKEN -> Icons.Default.Check; MedicineDoseState.MISSED -> Icons.Default.Close; MedicineDoseState.SKIPPED -> Icons.Default.Remove; else -> Icons.Default.RadioButtonUnchecked }, null, Modifier.size(size.dp), tint = tint)
    }
}

/** A small "?" that opens a short explanation, in place of helper text on the screen (3 Nov meeting). */
@Composable internal fun MedicineInfoButton(title: String, text: String, tag: String = "medicine-info") {
    val t = careText(); var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp).testTag(tag)) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, t("More information", "और जानकारी"), Modifier.size(22.dp), tint = RustText) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = { open = false }) { Text(t("OK", "ठीक है")) } })
}

/**
 * One medicine's history as a month calendar (Dr Sujay, 24 Nov 2026), in place of the long list of every dose: a dot per day
 * (green all taken, red something not taken, blue a side effect noted; two can show), month arrows (or a swipe), and the chosen
 * day's dose reports under the grid, with that day's side-effect notes. Opens on today.
 */
@Composable internal fun MedicineHistorySheet(item: CareItem, vm: CareModel, onDismiss: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val events by vm.medicineEvents.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    // Who reported a dose and when it was logged is for the care team (owner, 25 Nov); patients and family see the answer only.
    val clinician by vm.clinician.collectAsStateWithLifecycle()
    var sideEffect by rememberSaveable(item.id) { mutableStateOf(false) }
    val doses = medicineDosesFor(item, vm)
    val context = LocalContext.current
    val store = remember(vm.storageNamespace) { MedicineStripPhotos(context.applicationContext, vm.storageNamespace) }
    val photoRevision by MedicineStripPhotos.revision.collectAsStateWithLifecycle()
    val photo = remember(item.personId, item.id, photoRevision) { runCatching { store.first(item.personId, item.id) }.getOrNull() }
    SaharaScreenSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
        var chosen by rememberSaveable(item.id) { mutableStateOf(today.toEpochDay()) }
        val day = LocalDate.ofEpochDay(chosen)
        val mine = remember(events, item.id) { events.filter { it.medicineId == item.id } }
        val sideEffects = remember(allItems, item.id, item.personId) { SideEffectLog.forMedicine(SideEffectLog.entries(allItems, item.personId), item.id) }
        val sideTimes = remember(sideEffects) { sideEffects.map { it.note.happenedAt } }
        val dayReports = remember(mine, chosen) { MedicineHistoryCalendar.eventsOn(item.id, mine, day, zone) }
        val daySideEffects = remember(sideEffects, chosen) { sideEffects.filter { Instant.ofEpochMilli(it.note.happenedAt).atZone(zone).toLocalDate() == day } }
        val reportsShown = rememberShownCount(item.id to chosen)
        val now = System.currentTimeMillis()
        LazyColumn(Modifier.fillMaxWidth().testTag("care-medicine-history-sheet"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "history-band") { SaharaSectionBand("${item.title} · ${t("History", "इतिहास")}", SaharaSection.Medicines) }
            // The strip photo the patient knows, large, above the month (point 6: the photo everywhere the medicine appears).
            item(key = "history-photo") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StripThumb(photo, 96, Modifier.testTag("medicine-history-photo"))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                        DoseSlotLine(doses.filter { it.timed }.map { it.time }, iconSize = 22, fontSize = 15)
                        if (MedicineAlarmRules.isImportant(item)) MedicineAlarmTag()
                    }
                }
            }
            item(key = "calendar") { MedicineHistoryCalendarCard(item, doses, mine, sideTimes, day, today) { chosen = it.toEpochDay() } }
            item(key = "day-title") {
                // The chosen day's name changes in place (a short fade and rise) when another day is tapped.
                val dayTitle = remember(chosen, hindi) { day.format(careDateFormat("EEEE, d MMMM", hindi)) }
                SaharaAnimatedText(dayTitle) { shown ->
                    Text(shown, Modifier.padding(start = 4.dp, top = 4.dp).a11yHeading().testTag("medicine-history-day-title"),
                        fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                }
            }
            // A quiet line for a day without reports; the Side effect? action sits below.
            if (dayReports.isEmpty() && daySideEffects.isEmpty()) item(key = "day-empty") {
                Row(Modifier.animateItem().fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, Modifier.size(22.dp), tint = DoctorCardSub); Spacer(Modifier.width(10.dp))
                    Text(if (mine.isEmpty()) t("No reports yet.", "अभी कोई जानकारी नहीं।") else t("Nothing reported on this day.", "इस दिन कुछ दर्ज नहीं।"), fontSize = 16.sp, color = DoctorCardInk)
                }
            }
            cappedItems(dayReports, reportsShown, key = { it.id }, tag = "medicine-history") { CompactMedicineReport(it, mine, now, clinician) }
            medicineSideEffectItems(daySideEffects, vm, showReporter = clinician)
            if (canEdit) item(key = "history-side-effect-action") { OutlinedButton(onClick = { sideEffect = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.animateItem().fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).testTag("care-medicine-history-side-effect")) { Icon(Icons.Default.ErrorOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Side effect?", "कोई दुष्प्रभाव?"), fontSize = 16.sp) } }
            if (vm.cloud != null) item(key = "history-refresh") { TextButton(onClick = vm::refreshMedicineHistory, modifier = Modifier.animateItem().fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Refresh history", "इतिहास ताज़ा करें"), fontSize = 15.sp) } }
        }
    }
    if (sideEffect) SideEffectSheet(item, vm, onDismiss = { sideEffect = false })
}

private val CalendarTaken: Color get() = SaharaTone.Success.solid
private val CalendarNotTaken: Color get() = SaharaTone.Error.solid
private val CalendarSideEffect: Color get() = SaharaTone.Info.solid

/** The month grid of one medicine's history (Monday first), reusing the app's swipeable month pager; [onDay] gets the tapped day. */
@Composable private fun MedicineHistoryCalendarCard(item: CareItem, doses: List<MedicineDose>, events: List<MedicineIntakeEvent>, sideTimes: List<Long>, day: LocalDate, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val t = careText(); val hindi = careHindi(); val zone = ZoneId.systemDefault()
    // One calm white surface, no edge: month arrows, weekday names, days and the three-dot key.
    Surface(shape = RoundedCornerShape(SaharaRadius.Lg), color = Paper, modifier = Modifier.fillMaxWidth().testTag("medicine-history-calendar")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDay(day.minusMonths(1)) }, modifier = Modifier.size(48.dp).testTag("medicine-history-prev-month")) { Icon(Icons.Default.ChevronLeft, t("Previous month", "पिछला महीना"), tint = SaharaSection.Medicines.ink) }
                Text(day.format(careDateFormat("MMMM yyyy", hindi)), Modifier.weight(1f).testTag("medicine-history-month"), textAlign = TextAlign.Center, fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1)
                val later = YearMonth.from(day) < YearMonth.from(today)
                IconButton(onClick = { onDay(day.plusMonths(1).let { if (it > today) today else it }) }, enabled = later, modifier = Modifier.size(48.dp).testTag("medicine-history-next-month")) {
                    Icon(Icons.Default.ChevronRight, t("Next month", "अगला महीना"), tint = if (later) SaharaSection.Medicines.ink else DoctorCardFaint)
                }
            }
            val monday = day.with(DayOfWeek.MONDAY)
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i -> Text(monday.plusDays(i.toLong()).format(careDateFormat("EEE", hindi)), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, maxLines = 1) }
            }
            SaharaMonthPager(day, onDay, Modifier.fillMaxWidth()) { d, inMonth ->
                val marks = remember(events, sideTimes, doses, d, today) { MedicineHistoryCalendar.marks(item, doses, events, sideTimes, d, zone, today) }
                MedicineCalendarDay(d, inMonth, d == day, d == today, marks) { onDay(d) }
            }
            HorizontalDivider(color = DoctorHairline.copy(alpha = .6f))
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("medicine-history-legend"), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(CalendarTaken to t("All taken", "सब ली"), CalendarNotTaken to t("Not taken", "नहीं ली"), CalendarSideEffect to t("Side effect", "दुष्प्रभाव")).forEach { (color, label) ->
                    Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(color, CircleShape)); Spacer(Modifier.width(6.dp)); Text(label, fontSize = 14.sp, color = DoctorCardSub) }
                }
            }
        }
    }
}

/** One day cell: its number and up to two dots (green or red, then blue). The chosen day is filled plum; today is bold. */
@Composable private fun MedicineCalendarDay(d: LocalDate, inMonth: Boolean, selected: Boolean, isToday: Boolean, marks: MedicineDayMarks, onClick: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val fill by animateColorAsState(if (selected) SaharaSection.Medicines.solid else Color.Transparent, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "medicine-day")
    val words = listOfNotNull(t("Doses taken: ${marks.asNeededTaken}", "ली गई खुराक: ${marks.asNeededTaken}").takeIf { marks.asNeededTaken > 0 }, t("All taken", "सब ली").takeIf { marks.allTaken && marks.asNeededTaken == 0 }, t("Not taken", "नहीं ली").takeIf { marks.notTaken }, t("Side effect noted", "दुष्प्रभाव दर्ज").takeIf { marks.sideEffect })
    val desc = (listOf(d.format(careDateFormat("EEE d MMM", hindi))) + words).joinToString(", ")
    Box(Modifier.fillMaxWidth().padding(2.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(fill)
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = desc }.testTag(if (inMonth) "medicine-history-day-$d" else "medicine-history-outday-$d"),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(d.dayOfMonth.toString(), fontSize = 15.sp, fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when { selected -> Color.White; isToday -> SaharaSection.Medicines.ink; inMonth -> DoctorCardInk; else -> DoctorCardFaint })
            Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                val dot = Modifier.size(7.dp).then(if (selected) Modifier.border(1.dp, Color.White, CircleShape) else Modifier)
                // An as-needed medicine: one green dot per dose taken that day (three at most, then the count is in the day's list).
                if (marks.asNeededTaken > 0) repeat(marks.asNeededTaken.coerceAtMost(3)) { Box(dot.background(CalendarTaken, CircleShape)) }
                else if (marks.notTaken) Box(dot.background(CalendarNotTaken, CircleShape)) else if (marks.allTaken) Box(dot.background(CalendarTaken, CircleShape))
                if (marks.sideEffect) Box(dot.background(CalendarSideEffect, CircleShape))
            }
        }
    }
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
@Composable private fun CompactMedicineReport(event: MedicineIntakeEvent, all: List<MedicineIntakeEvent>, now: Long, clinician: Boolean = true) {
    val t = careText()
    val status = MedicineHistoryRules.status(event, all, now)
    val lang = careLanguage()
    val noteShown = remember(event.note, lang) { MedicinePhrases.shown(event.note, lang) }
    // Only three answers (Taken / Not taken / Skipped); "late" is shown on a Taken report, never as its own answer.
    // Solid status pills: green taken, amber taken late, red not taken, grey skipped, bold white word.
    val (word, pillTone) = when (MedicineDoseStatus.choiceOf(event)) {
        MedicineDoseChoice.TAKEN -> if (MedicineDoseStatus.late(event)) t("Taken · late", "ली · देर से") to SaharaTone.Warning else t("Taken", "ली") to SaharaTone.Success
        MedicineDoseChoice.NOT_TAKEN -> t("Not taken", "नहीं ली") to SaharaTone.Error
        MedicineDoseChoice.SKIPPED -> t("Skipped", "छोड़ी") to SaharaTone.Neutral
        null -> t(status, MedicineStatusHindi[status] ?: status) to SaharaTone.Warning
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
            // The large green tick or red cross first (point 6), then the word on its colour.
            MedicineDoseStatus.choiceOf(event)?.let { DoseStateMark(MedicineDoseStatus.stateOf(it), 36, modifier = Modifier.testTag("medicine-report-mark-${event.id}")); Spacer(Modifier.width(8.dp)) }
            Surface(color = pillTone.solid, shape = RoundedCornerShape(50)) { Text(word, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = pillTone.onSolid) }
            Spacer(Modifier.width(12.dp))
            // The dose's time of day as a picture (sun, bright sun, sunset, moon) beside its time.
            doseAt?.let { DoseSlotIcon(MedicineDoseRules.slot(doseClock.format(Instant.ofEpochMilli(it))), 22); Spacer(Modifier.width(6.dp)) }
            Text(main, Modifier.weight(1f), fontSize = 15.sp, color = DoctorCardInk)
            if (MedicineHistoryRules.loggedByFamily(event)) SymptomLoggedByFamilyChip(careHindi(), "medicine-report-family-${event.id}")
        }
        if (clinician) Text(t("Reported by ", "बताने वाले: ") + event.reporter.ifBlank { t("Not recorded", "दर्ज नहीं") } + " · " + t("Logged: ", "दर्ज: ") + reportTime.format(Instant.ofEpochMilli(event.loggedAt)), Modifier.padding(top = 8.dp), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
        if (event.note.isNotBlank() && event.note != FAMILY_ONLY_NOTE && !event.note.startsWith("Planned dose.") && event.plannedAt == null) Text(if (event.note == MedicineDoses.AS_NEEDED_NOTE) t("As-needed dose taken.", "ज़रूरत पड़ने पर ली गई खुराक।") else noteShown, Modifier.padding(top = 4.dp), fontSize = 15.sp, color = DoctorCardInk)
    }
}
