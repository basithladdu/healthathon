package `in`.wedevit.care

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/*
 * Today's doses as one shared holder (25 Nov redesign): the Medicines tab, Today's next-dose row and any dose sheet read the same
 * numbers and mark doses the same way. It is a lift-and-shift of TodayDosesCard's computation (MedicineStripPhotos.kt); the data
 * layer (MedicineDoses, MedicineDoseRules, DoseUndoWindow, markMedicineDose) is used exactly as before.
 */

/** A night dose marked after midnight: [time] is the dose time, [save] writes it for the chosen night. */
class WhichNightRequest(val time: String, val save: (LocalDate) -> Unit)

/**
 * Today's doses for the selected person. [groups] are the timed doses by time ([MedicineDoseRules.groups]); [untimed] the doses with
 * a count but no time (as-needed medicines left out); [asNeeded] the "when needed" medicines; [others] the current medicines with
 * no dose today (weekly, not daily). [taken] and [total] count only scheduled doses (as-needed ones are open-ended).
 * Call [rememberTodayDoses] once per page.
 */
@Stable class TodayDoses internal constructor(
    val personId: String,
    val medicines: List<CareItem>,
    val groups: List<DoseGroup>,
    val untimed: List<Pair<CareItem, MedicineDose>>,
    val asNeeded: List<CareItem>,
    val others: List<CareItem>,
    val taken: Int,
    val total: Int,
    val nextPending: DoseGroup?,
    val enabled: Boolean,
    private val byId: Map<String, CareItem>,
    private val doses: Map<String, List<MedicineDose>>,
    private val events: List<MedicineIntakeEvent>,
    private val photos: Map<String, File?>,
    private val day: LocalDate,
    private val zone: ZoneId,
    private val onMark: (CareItem, MedicineDose, MedicineDoseChoice, Boolean) -> Unit,
    private val onAsNeeded: (CareItem) -> Unit,
    internal val whichNightState: MutableState<WhichNightRequest?>,
    internal val problemState: MutableState<String>,
) {
    /** Nothing to show: no current medicine at all. */
    val isEmpty: Boolean get() = medicines.isEmpty()
    /** Every scheduled dose of today is marked Taken. */
    val allTaken: Boolean get() = total > 0 && taken == total
    val whichNight: WhichNightRequest? get() = whichNightState.value

    fun item(id: String): CareItem? = byId[id]
    fun dosesOf(item: CareItem): List<MedicineDose> = doses[item.id].orEmpty()
    fun stateOf(item: CareItem, dose: MedicineDose): MedicineDoseState = MedicineDoses.state(item, dose, events, day, zone)
    fun familyOf(item: CareItem, dose: MedicineDose): Boolean = MedicineDoses.loggedByFamily(item, dose, events, day, zone)
    fun photoOf(item: CareItem): File? = photos[item.id]
    /** The as-needed doses taken today (their times), for "Last: 14:10". */
    fun asNeededTimes(item: CareItem): List<Long> = MedicineDoses.asNeededTimes(item.id, events, day, zone)

    /** The (medicine, dose) pairs of one time group, in the group's order. */
    fun entries(group: DoseGroup): List<Pair<CareItem, MedicineDose>> = group.medicines.mapNotNull { m ->
        val item = byId[m.itemId] ?: return@mapNotNull null
        val dose = doses[m.itemId].orEmpty().firstOrNull { it.time == group.time } ?: return@mapNotNull null
        item to dose
    }

    /** How many doses of [group] are still not marked. */
    fun pendingIn(group: DoseGroup): Int = entries(group).count { (item, dose) -> stateOf(item, dose) == MedicineDoseState.PENDING }

    /** Marks one dose. [quick] (a one-tap Taken) is held with Undo; a late night dose first asks which night (see [WhichNightDialogHost]). */
    fun mark(item: CareItem, dose: MedicineDose, choice: MedicineDoseChoice, quick: Boolean) = onMark(item, dose, choice, quick)

    /** "Took one" for an as-needed medicine: one more taken report with this moment's time. */
    fun tookAsNeeded(item: CareItem) = onAsNeeded(item)
}

/** The selected person's doses for today, with the one way to mark them. */
@Composable fun rememberTodayDoses(vm: CareModel): TodayDoses {
    val t = careText(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle()
    val saved by vm.medicineEvents.collectAsStateWithLifecycle()
    // A one-tap Taken waits a few seconds with Undo before it is written; until then it is shown from the held tick.
    val held by DoseUndoWindow.app.pending.collectAsStateWithLifecycle()
    val events = remember(saved, held, person) { DoseUndoWindow.shown(saved, held, person) }
    val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    val store = remember(vm.storageNamespace) { MedicineStripPhotos(context.applicationContext, vm.storageNamespace) }
    val photoRevision by MedicineStripPhotos.revision.collectAsStateWithLifecycle()
    val medicines = remember(items, person) { items.filter { it.kind == "medicine" && it.personId == person && !CareEntryCodec.isStructured(it.detail) } }
    val doses = remember(medicines) { medicines.associate { it.id to MedicineDoses.forItem(it, scheduler.option(it.id)?.times) } }
    val asNeeded = remember(medicines) { medicines.filter { MedicineDoses.isAsNeededMedicine(it) } }
    val scheduled = remember(medicines, asNeeded) { medicines.filter { it !in asNeeded } }
    val groups = remember(scheduled, doses) { MedicineDoseRules.groups(scheduled.map { m -> DoseMedicine(m.id, m.personId, m.title, doses[m.id].orEmpty().filter { it.timed }.map { it.time }) }) }
    val untimed = remember(scheduled, doses) { scheduled.flatMap { m -> doses[m.id].orEmpty().filter { !it.timed }.map { m to it } } }
    val others = remember(scheduled, doses) { scheduled.filter { doses[it.id].isNullOrEmpty() } }
    val photos = remember(medicines, photoRevision) { medicines.associate { it.id to runCatching { store.first(it.personId, it.id) }.getOrNull() } }
    val byId = remember(medicines) { medicines.associateBy { it.id } }
    val working = remember { mutableStateOf(false) }
    val problem = remember { mutableStateOf("") }
    val whichNight = remember { mutableStateOf<WhichNightRequest?>(null) }
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val total = remember(scheduled, doses) { scheduled.sumOf { doses[it.id].orEmpty().size } }
    val taken = remember(scheduled, doses, events, today) { scheduled.sumOf { m -> MedicineDoses.takenCount(MedicineDoses.states(m, doses[m.id].orEmpty(), events, today, zone)) } }
    val nextPending = remember(groups, byId, doses, events, today) {
        groups.firstOrNull { g -> g.medicines.any { m -> val item = byId[m.itemId]; val dose = doses[m.itemId].orEmpty().firstOrNull { it.time == g.time }
            item != null && dose != null && MedicineDoses.state(item, dose, events, today, zone) == MedicineDoseState.PENDING } }
    }
    val failed = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।")
    val today1 = t("Today", "आज")
    val doseWord: (MedicineDose) -> String = { dose -> t("Dose ${dose.index}", "खुराक ${dose.index}") }
    val asNeededFailed = t("This dose could not be saved. Try again.", "यह खुराक सहेजी नहीं जा सकी। फिर कोशिश करें।")
    val latestEvents by rememberUpdatedState(events)
    val mark: (CareItem, MedicineDose, MedicineDoseChoice, Boolean) -> Unit = { item, dose, choice, quick ->
        val label = when { dose.timed -> dose.time; dose.count == 1 -> today1; else -> doseWord(dose) }
        val save: (LocalDate) -> Unit = { day -> if (canEditCareContext(vm, item.personId, vm.accountNamespace)) { working.value = true; problem.value = ""; scope.launch {
            try { markMedicineDose(context, vm, item, doses[item.id].orEmpty(), dose, choice == MedicineDoseChoice.TAKEN, quick, label, day, skipped = choice == MedicineDoseChoice.SKIPPED) }
            catch (e: Exception) { if (e is CancellationException) throw e; problem.value = if (e is IllegalArgumentException) e.message ?: failed else failed }
            finally { working.value = false }
        } } }
        // A late night dose marked after midnight asks which night it was, so it does not cover tonight's dose.
        if (MedicineDoses.askWhichNight(item, dose, latestEvents, ZonedDateTime.now(zone))) whichNight.value = WhichNightRequest(dose.time, save) else save(LocalDate.now(zone))
    }
    val tookAsNeeded: (CareItem) -> Unit = { item ->
        if (canEditCareContext(vm, item.personId, vm.accountNamespace)) scope.launch {
            working.value = true
            try { recordAsNeededDose(context, vm, item) } catch (e: Exception) { if (e is CancellationException) throw e; problem.value = asNeededFailed } finally { working.value = false }
        }
    }
    return TodayDoses(person, medicines, groups, untimed, asNeeded, others, taken, total, nextPending, enabled = canEdit && !busy && !working.value,
        byId = byId, doses = doses, events = events, photos = photos, day = today, zone = zone, onMark = mark, onAsNeeded = tookAsNeeded,
        whichNightState = whichNight, problemState = problem)
}

/** The "Which night's dose?" question and the "Not saved" dialog for one [TodayDoses]. Put it once on the page that uses it. */
@Composable fun WhichNightDialogHost(doses: TodayDoses) {
    val t = careText()
    val ask = doses.whichNightState.value
    WhichNightDialog(ask?.let { it.time to it.save }) { doses.whichNightState.value = null }
    val problem = doses.problemState.value
    if (problem.isNotBlank()) SaharaDialog(t("Not saved", "सहेजा नहीं गया"), onDismiss = { doses.problemState.value = "" }, text = problem, tone = SaharaTone.Error)
}

/**
 * "Paracetamol marked taken · Undo" in the app's snackbar while a one-tap Taken waits ([DoseUndoWindow]). Undo drops it; when the
 * snackbar goes away the tick is written. The held tick is also written when the app goes to the background or the page closes,
 * and a write that failed later shows "Not saved". Call it once per page (not together with DoseUndoRow on the same page).
 */
@Composable fun DoseUndoSnackbarEffect(personId: String) {
    val t = careText(); val window = DoseUndoWindow.app
    val snackbar = LocalSaharaSnackbar.current
    val held by window.pending.collectAsStateWithLifecycle()
    val failure by window.failure.collectAsStateWithLifecycle()
    val mine = held?.takeIf { it.personId == personId }
    val message = mine?.let { "${it.title} " + t("marked taken", "ली गई") }
    val undo = t("Undo", "वापस लें")
    LaunchedEffect(mine) {
        val p = mine ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message.orEmpty(), actionLabel = undo, withDismissAction = false, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) window.undo() else if (window.pending.value === p) window.flushSoon()
    }
    val lifecycle = LocalLifecycleOwner.current
    val stillMine by rememberUpdatedState(mine != null)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) window.flushSoon() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); if (stillMine) window.flushSoon() }
    }
    failure?.let { (_, text) ->
        SaharaDialog(t("Not saved", "सहेजा नहीं गया"), onDismiss = { window.failure.value = null }, text = text.ifBlank { t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।") }, tone = SaharaTone.Error)
    }
}

/** Confirm before removing a medicine (kept here so every dose sheet asks the same way). */
@Composable internal fun RemoveMedicineDialog(item: CareItem, vm: CareModel, onDismiss: () -> Unit) {
    val t = careText()
    val busy by vm.busy.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("Remove this entry?", "यह जानकारी हटाएँ?")) }, text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.remove(item); onDismiss() }, enabled = !busy && canEdit) { Text(t("Remove", "हटाएँ")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Keep", "रखें")) } })
}
