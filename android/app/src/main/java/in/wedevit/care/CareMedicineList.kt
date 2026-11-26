package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Check
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId

/** The part of the day a medicine is listed under on the Medicines page. */
enum class MedicineSlot { MORNING, AFTERNOON, EVENING, NIGHT, AS_NEEDED, OTHER }

/** What grouping needs to know about one medicine. [times] are its dose times ("08:00"), empty when it has none. */
data class MedicineSlotInput(val id: String, val name: String, val times: List<String>, val asNeeded: Boolean)

/**
 * Groups current medicines by time of day for the Medicines page, so 20 or more medicines read as a few short lists.
 *
 * A medicine taken at several times is listed ONCE, under its earliest dose time, with all its dose chips on that one
 * row. It is not repeated under each time: a second row for the same medicine would carry a second set of dose ticks, and
 * a tap on the wrong copy could mark (or miss) a dose twice. One row means one set of ticks.
 */
object MedicineTimeGroups {
    /** Morning 05:00 to 11:59, afternoon 12:00 to 16:59, evening 17:00 to 20:59, night 21:00 to 04:59. */
    fun slotOfTime(time: String): MedicineSlot? {
        val hour = time.take(2).toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        return when (hour) { in 5..11 -> MedicineSlot.MORNING; in 12..16 -> MedicineSlot.AFTERNOON; in 17..20 -> MedicineSlot.EVENING; else -> MedicineSlot.NIGHT }
    }

    /** The medicine's group: by its earliest dose time; else "as needed" when it says so; else "other" (weekly, or no time). */
    fun slotOf(input: MedicineSlotInput): MedicineSlot {
        val first = input.times.filter { it.length >= 4 }.minOrNull()
        return first?.let(::slotOfTime) ?: if (input.asNeeded) MedicineSlot.AS_NEEDED else MedicineSlot.OTHER
    }

    /** Non-empty groups in day order; inside a group, by earliest time then name. Each medicine appears exactly once. */
    fun group(inputs: List<MedicineSlotInput>): List<Pair<MedicineSlot, List<MedicineSlotInput>>> {
        val order = compareBy<MedicineSlotInput>({ it.times.minOrNull() ?: "99:99" }, { it.name.lowercase() })
        val bySlot = inputs.groupBy { slotOf(it) }
        return MedicineSlot.values().mapNotNull { slot -> bySlot[slot]?.sortedWith(order)?.let { slot to it } }
    }

    /** Medicines whose name contains [query] (ignoring case and surrounding spaces); everything when the query is blank. */
    fun filterByName(inputs: List<MedicineSlotInput>, query: String): List<MedicineSlotInput> {
        val q = query.trim()
        return if (q.isEmpty()) inputs else inputs.filter { it.name.contains(q, ignoreCase = true) }
    }

    /** The search field shows only for a long list. */
    const val SEARCH_ABOVE = 8
}

/**
 * Care > Medicines: current medicines grouped by time of day (each group with its count), a name search once there are
 * more than [MedicineTimeGroups.SEARCH_ABOVE], compact expandable rows, and past medicines behind a "Past (n)" toggle.
 */
@Composable internal fun MedicineModuleList(vm: CareModel, current: List<CareItem>, past: List<CareItem>) {
    val t = careText(); val context = LocalContext.current
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    val inputs = remember(current) {
        current.map { item ->
            val times = MedicineDoses.forItem(item, scheduler.option(item.id)?.times).filter { it.timed }.map { it.time }
            MedicineSlotInput(item.id, item.title, times, MedicineDoses.isAsNeededMedicine(item))
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var pastOpen by rememberSaveable { mutableStateOf(false) }
    val searching = current.size > MedicineTimeGroups.SEARCH_ABOVE
    val active = if (searching) query else ""
    val groups = remember(inputs, active) { MedicineTimeGroups.group(MedicineTimeGroups.filterByName(inputs, active)) }
    val byId = remember(current) { current.associateBy { it.id } }
    val tick = rememberSaharaTick()
    // Taken on a spoken alarm is saved here too, as an ordinary tick.
    MedicineAlarmTakenSync(vm)
    // The Past chevron turns in the draw layer only, so the toggle never re-lays out the list while it moves.
    val pastTurn by animateFloatAsState(if (pastOpen) 180f else 0f, SaharaMotion.tween(SaharaMotion.EXPAND_MS, easing = SaharaMotion.Emphasized), label = "medicines-past-chevron")
    // No size animation on the whole list or a row (owner: "cards keep expanding"); a row opens in one step.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (searching) OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, singleLine = true,
            label = { Text(t("Search medicines", "दवा खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().testTag("medicines-search"))
        if (current.isEmpty()) QuietLine(t("No current medicines.", "अभी कोई दवा नहीं चल रही।"))
        // A search with no match fades in under the field instead of popping.
        AnimatedVisibility(current.isNotEmpty() && groups.isEmpty(), enter = SaharaMotion.enter, exit = SaharaMotion.exit, label = "medicines-no-match") {
            QuietLine(t("No medicine matches that name.", "इस नाम की कोई दवा नहीं मिली।"))
        }
        groups.forEach { (slot, list) -> key(slot) {
            // Each time of day under its picture: sun, bright sun, sunset, moon (point 6).
            Row(Modifier.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                medicineSlotDose(slot)?.let { DoseSlotBadge(it, 30); Spacer(Modifier.width(8.dp)) }
                Text("${medicineSlotTitle(slot)} · ${list.size}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.a11yHeading().testTag("medicines-group-${slot.name.lowercase()}"))
            }
            // One calm surface per time of day with hairlines between rows, instead of a card per medicine.
            Surface(shape = RoundedCornerShape(20.dp), color = androidx.compose.ui.graphics.Color(0xFFF3E4EC), shadowElevation = 2.dp, border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFD7C2DA)), modifier = Modifier.fillMaxWidth().testTag("medicines-group-list-${slot.name.lowercase()}")) {
                Column(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFFEBD9EF), androidx.compose.ui.graphics.Color(0xFFF6E0EA)))).padding(vertical = 2.dp)) {
                    list.forEachIndexed { index, input -> byId[input.id]?.let { item -> key(item.id) { MedicineCompactRow(item, vm, divider = index > 0) } } }
                }
            }
        } }
        if (past.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { tick(); pastOpen = !pastOpen }.padding(horizontal = 4.dp).testTag("medicines-past-toggle"), verticalAlignment = Alignment.CenterVertically) {
                Text("${t("Past", "पहले की")} (${past.size})", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.weight(1f).a11yHeading())
                Icon(Icons.Default.KeyboardArrowDown, if (pastOpen) t("Hide past medicines", "पहले की दवाइयाँ छिपाएँ") else t("Show past medicines", "पहले की दवाइयाँ दिखाएँ"), Modifier.graphicsLayer { rotationZ = pastTurn }, tint = DoctorCardSub)
            }
            // The past list is a section that opens and folds (the rows themselves never animate their size).
            AnimatedVisibility(pastOpen, enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "medicines-past") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { past.forEach { item -> key(item.id) { CareRow(item, vm) } } }
            }
        }
    }
}

/** The dose part of the day for a Medicines group, for its picture; none for As needed and Other. */
internal fun medicineSlotDose(slot: MedicineSlot): DoseSlot? = when (slot) {
    MedicineSlot.MORNING -> DoseSlot.MORNING; MedicineSlot.AFTERNOON -> DoseSlot.AFTERNOON; MedicineSlot.EVENING -> DoseSlot.EVENING; MedicineSlot.NIGHT -> DoseSlot.NIGHT
    else -> null
}

@Composable private fun medicineSlotTitle(slot: MedicineSlot): String { val t = careText()
    return when (slot) {
        MedicineSlot.MORNING -> t("Morning", "सुबह"); MedicineSlot.AFTERNOON -> t("Afternoon", "दोपहर"); MedicineSlot.EVENING -> t("Evening", "शाम"); MedicineSlot.NIGHT -> t("Night", "रात")
        MedicineSlot.AS_NEEDED -> t("As needed", "ज़रूरत पड़ने पर"); MedicineSlot.OTHER -> t("Other", "अन्य")
    }
}

/**
 * One medicine on one dense row (owner, 24 Nov 2026: patients take 10 to 20 medicines, so no big card each): name and strength,
 * a one-line schedule ("Morning · Night", "As needed"), and a small status for today on the right ("1 of 2", green once all are
 * taken, red when one was not taken). Tap the row to open the full card (dose ticks, directions, strip photo, the menu); "Show less" folds it.
 */
@Composable private fun MedicineCompactRow(item: CareItem, vm: CareModel, divider: Boolean) {
    val t = careText(); val lang = careLanguage(); val context = LocalContext.current
    // The strip photo on every row (point 6), so a medicine is found by sight, not by reading its name.
    val store = remember(vm.storageNamespace) { MedicineStripPhotos(context.applicationContext, vm.storageNamespace) }
    val photoRevision by MedicineStripPhotos.revision.collectAsStateWithLifecycle()
    val photo = remember(item.personId, item.id, photoRevision) { runCatching { store.first(item.personId, item.id) }.getOrNull() }
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    val tick = rememberSaharaTick()
    Column(Modifier.fillMaxWidth().testTag("medicine-row-${item.id}")) {
        if (divider && !open) HorizontalDivider(Modifier.padding(start = 72.dp), color = DoctorHairline.copy(alpha = .6f))
        if (open) Column(Modifier.padding(vertical = 4.dp)) {
            MedicineCareCard(item, vm)
            TextButton(onClick = { tick(); open = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("medicine-collapse-${item.id}")) { Icon(Icons.Default.KeyboardArrowUp, null); Spacer(Modifier.width(4.dp)); Text(t("Show less", "कम दिखाएँ")) }
        } else {
            val entry = remember(item.detail) { MedicineEntryText.parse(item.detail) }
            val strength = remember(entry) { entry?.let { e -> listOf(e.strength, e.howMuch).filter { it.isNotBlank() }.joinToString(" · ") }.orEmpty() }
            val doses = medicineDosesFor(item, vm)
            val schedule = medicineScheduleLine(item, doses, lang)
            val saved by vm.medicineEvents.collectAsStateWithLifecycle()
            val held by DoseUndoWindow.app.pending.collectAsStateWithLifecycle()
            val events = remember(saved, held, item.personId) { DoseUndoWindow.shown(saved, held, item.personId) }
            val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
            // Worked out once per change of doses or reports, not on every recomposition of a long list.
            val states = remember(item, doses, events, today) { MedicineDoses.states(item, doses, events, today, zone) }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = t("Show details", "ब्यौरा दिखाएँ")) { tick(); open = true }
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).testTag("medicine-expand-${item.id}"), verticalAlignment = Alignment.CenterVertically) {
                StripThumb(photo, 48, Modifier.testTag("medicine-row-photo-${item.id}"))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (strength.isNotBlank()) Text(" · $strength", fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    // Dose times as pictures with their words ("☀ Morning  🌙 Night"); a medicine without times keeps its words.
                    val timed = doses.filter { it.timed }.map { it.time }
                    if (timed.isNotEmpty()) DoseSlotLine(timed, Modifier.testTag("medicine-schedule-${item.id}"))
                    else if (schedule.isNotBlank()) Text(schedule, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("medicine-schedule-${item.id}"))
                    if (MedicineAlarmRules.isImportant(item)) MedicineAlarmTag(Modifier.testTag("medicine-alarm-tag-${item.id}"))
                }
                MedicineTodayStatus(item.id, states)
                Icon(Icons.Default.KeyboardArrowDown, t("Show details", "ब्यौरा दिखाएँ"), Modifier.padding(start = 4.dp), tint = DoctorCardSub)
            }
        }
    }
}

/** "Morning · Night" from the dose times; "As needed", "Not daily", or the chosen How often for a medicine without times. */
@Composable private fun medicineScheduleLine(item: CareItem, doses: List<MedicineDose>, lang: CareLang): String {
    val t = careText()
    val timed = doses.filter { it.timed }
    return when {
        timed.isNotEmpty() -> timed.map { MedicineDoseRules.slot(it.time) }.distinct().sortedBy { it.ordinal }.joinToString(" · ") { careTr(lang, it.english, it.hindi) }
        MedicineDoses.isAsNeededMedicine(item) -> t("As needed", "ज़रूरत पड़ने पर")
        MedicineDoses.notDaily(item) -> t("Not daily", "रोज़ नहीं")
        else -> MedicineFrequencyRules.fromText(MedicineDoses.frequencyText(item.detail))?.let { t(it.english, it.hindi) }
            ?: if (doses.size > 1) t("${doses.size} times a day", "दिन में ${doses.size} बार") else t("Once a day", "दिन में एक बार")
    }
}

/**
 * Today's doses on the right of a row: a large green tick once all are taken, a large red cross once one was not taken (point 6),
 * and "1 of 2" as a small pill while some are still due. Nothing without doses.
 */
@Composable private fun MedicineTodayStatus(itemId: String, states: Map<String, MedicineDoseState>) {
    val t = careText()
    if (states.isEmpty()) return
    val total = states.size; val taken = MedicineDoses.takenCount(states)
    val notTaken = states.values.any { it == MedicineDoseState.MISSED }
    val all = MedicineDoses.allTaken(states)
    if (all || notTaken) { DoseStateMark(if (notTaken) MedicineDoseState.MISSED else MedicineDoseState.TAKEN, 36, modifier = Modifier.testTag("medicine-status-$itemId")); return }
    val tone = when { notTaken -> SaharaTone.Error; all -> SaharaTone.Success; else -> SaharaTone.Neutral }
    val text = when { all -> t("Taken", "ली"); notTaken -> t("Not taken", "नहीं ली"); else -> t("$taken of $total", "$total में से $taken") }
    // The pill's colour glides between grey, green and red, and its words change in place, when a dose is marked.
    val wash by animateColorAsState(tone.wash, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "medicine-status-wash")
    val ink by animateColorAsState(tone.ink, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "medicine-status-ink")
    Surface(shape = RoundedCornerShape(50), color = wash, modifier = Modifier.testTag("medicine-status-$itemId")) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(all, enter = fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + expandHorizontally(SaharaMotion.tween(SaharaMotion.SAVED_MS)),
                exit = fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS)) + shrinkHorizontally(SaharaMotion.tween(SaharaMotion.EXIT_MS)), label = "medicine-status-tick") {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = ink); Spacer(Modifier.width(4.dp)) }
            }
            SaharaAnimatedText(text) { shown -> Text(shown, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, softWrap = false) }
        }
    }
}
