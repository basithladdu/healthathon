@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package `in`.wedevit.care

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/*
 * The Medicines tab (25 Nov redesign, owner override: stock Material 3, no tinted boxes, no pills, no illustrations).
 * One visual anchor (the dose ring), then today's doses as plain list rows under sticky time headers, each with a round tick.
 * Tap the tick = Taken (Undo in the snackbar); tap the row = the dose sheet (Taken / Not taken / Skipped, then the medicine's
 * actions). Add is the FAB; History and Search are in the top bar; past medicines are one row at the end.
 */

/**
 * The Medicines tab. [onScan] opens the prescription / strip scanner, [onAdd] the typed form, [onReminders] the reminder times
 * (left out when the page cannot open them). Root tab: no back arrow unless [onBack] is given.
 */
@Composable fun MedicinesPage(vm: CareModel, onScan: () -> Unit, onAdd: () -> Unit, onReminders: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    val t = careText(); val context = LocalContext.current
    val doses = rememberTodayDoses(vm)
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val clinician by vm.clinician.collectAsStateWithLifecycle()
    val everything by vm.allItems.collectAsStateWithLifecycle(); val visible by vm.items.collectAsStateWithLifecycle()
    val person = doses.personId
    // Past = medicines the history says were stopped (or the doctor took off the list); the same split the Care list used.
    val past = remember(everything, visible, person) {
        CareModules.splitMedicines(everything.filter { !CareEntryCodec.isStructured(it.detail) }, visible).second.filter { it.personId == person }
    }
    val store = remember(vm.storageNamespace) { MedicineStripPhotos(context.applicationContext, vm.storageNamespace) }
    val photoRevision by MedicineStripPhotos.revision.collectAsStateWithLifecycle()
    val pastPhotos = remember(past, photoRevision) { past.associate { it.id to runCatching { store.first(it.personId, it.id) }.getOrNull() } }
    // Side effects noted since the last visit and not yet discussed: one row that opens them in a sheet.
    val sideEffects = remember(everything, person) {
        val now = System.currentTimeMillis()
        SideEffectLog.toRaise(SideEffectLog.entries(everything, person), SideEffectLog.lastVisitAt(everything, person, now))
    }

    var showPast by rememberSaveable(person) { mutableStateOf(false) }
    var searchOpen by rememberSaveable(person) { mutableStateOf(false) }
    var query by rememberSaveable(person) { mutableStateOf("") }
    var addSheet by rememberSaveable { mutableStateOf(false) }
    var doseSheet by rememberSaveable(person) { mutableStateOf("") } // "itemId|doseKey"
    var medicineSheet by rememberSaveable(person) { mutableStateOf("") }
    var historyPicker by rememberSaveable(person) { mutableStateOf(false) }
    var historyFor by rememberSaveable(person) { mutableStateOf("") }
    var editFor by rememberSaveable(person) { mutableStateOf("") }
    var removeFor by rememberSaveable(person) { mutableStateOf("") }
    var sideEffectFor by rememberSaveable(person) { mutableStateOf("") }
    var raiseSheet by rememberSaveable(person) { mutableStateOf(false) }

    DoseUndoSnackbarEffect(person)
    WhichNightDialogHost(doses)

    val searching = doses.medicines.size > MedicineTimeGroups.SEARCH_ABOVE
    val match: Set<String>? = remember(doses.medicines, query, searchOpen, searching) {
        if (!searching || !searchOpen || query.isBlank()) null
        else MedicineTimeGroups.filterByName(doses.medicines.map { MedicineSlotInput(it.id, it.title, emptyList(), false) }, query).map { it.id }.toSet()
    }
    fun isShown(item: CareItem) = match == null || item.id in match
    fun findMedicine(id: String): CareItem? = doses.item(id) ?: past.firstOrNull { it.id == id }

    // The medicine's own actions, under the dose choices or in its sheet. View-only people get History only.
    val medicineActions: (CareItem) -> List<SheetAction> = { item ->
        listOfNotNull(
            SheetAction(t("Note a side effect", "दुष्प्रभाव लिखें"), Icons.Default.ErrorOutline, "care-side-effect-${item.id}") { sideEffectFor = item.id }.takeIf { canEdit },
            SheetAction(t("History", "इतिहास"), Icons.Default.History, "care-medicine-history-${item.id}") { historyFor = item.id },
            onReminders?.let { open -> SheetAction(t("Reminder times", "याद दिलाने का समय"), Icons.Default.Alarm, "medicine-reminders-${item.id}") { open() } }?.takeIf { canEdit },
            SheetAction(t("Edit", "बदलें"), Icons.Default.Edit, "care-edit-${item.id}") { editFor = item.id }.takeIf { canEdit },
            SheetAction(t("Remove", "हटाएँ"), Icons.Default.DeleteOutline, "care-remove-${item.id}", destructive = true) { removeFor = item.id }.takeIf { canEdit },
        )
    }

    if (showPast) PastMedicinesPage(past, pastPhotos, onBack = { showPast = false }, onOpen = { medicineSheet = it.id })
    else {
        val fab: (@Composable (Boolean) -> Unit)? = if (canEdit) ({ expanded ->
            ExtendedFloatingActionButton(text = { Text(t("Add medicine", "दवा जोड़ें")) }, icon = { Icon(Icons.Default.Add, null) }, onClick = { addSheet = true },
                expanded = expanded, modifier = Modifier.testTag("medicines-add"))
        }) else null
        SaharaPage(t("Medicines", "दवाइयाँ"), onBack = onBack, listModifier = Modifier.testTag("medicines-list"), fab = fab, actions = {
            if (searching) IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }, modifier = Modifier.size(48.dp).testTag("medicines-search-open")) {
                Icon(Icons.Default.Search, t("Search medicines", "दवा खोजें"))
            }
            if (!doses.isEmpty || past.isNotEmpty()) IconButton(onClick = { historyPicker = true }, modifier = Modifier.size(48.dp).testTag("medicines-history")) {
                Icon(Icons.Default.History, t("History", "इतिहास"))
            }
            SaharaPersonAction()
        }) {
            if (searching && searchOpen) item(key = "search", contentType = "search") {
                OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, singleLine = true,
                    placeholder = { Text(t("Search medicines", "दवा खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { IconButton(onClick = { query = ""; searchOpen = false }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, t("Close", "बंद करें")) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("medicines-search"))
            }
            if (doses.isEmpty) item(key = "empty", contentType = "empty") { MedicinesEmpty() }
            else if (doses.total > 0 && match == null) item(key = "hero", contentType = "hero") { MedicinesHero(doses) }
            if (sideEffects.isNotEmpty() && match == null) item(key = "raise", contentType = "row") {
                val count = sideEffects.size
                SaharaRow(if (count == 1) t("Tell the doctor: 1 side effect", "डॉक्टर को बताएँ: 1 दुष्प्रभाव") else t("Tell the doctor: $count side effects", "डॉक्टर को बताएँ: $count दुष्प्रभाव"),
                    Modifier.testTag("side-effects-to-raise"), icon = Icons.Default.ErrorOutline, onClick = { raiseSheet = true })
            }
            medicineRows(doses, ::isShown, onOpenDose = { item, dose -> doseSheet = "${item.id}|${dose.key}" }, onOpenMedicine = { medicineSheet = it.id })
            if (match != null && match.isEmpty()) item(key = "no-match", contentType = "row") {
                Text(t("No medicine matches that name.", "इस नाम की कोई दवा नहीं मिली।"), Modifier.padding(16.dp), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (past.isNotEmpty() && match == null) item(key = "past", contentType = "row") {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                SaharaRow(t("Past medicines", "पहले की दवाइयाँ") + " (${past.size})", Modifier.testTag("medicines-past-toggle"), icon = Icons.Default.Inventory2, onClick = { showPast = true })
            }
        }
    }

    // ── Sheets and dialogs ──
    if (addSheet) SaharaActionSheet(t("Add medicine", "दवा जोड़ें"), listOf(
        SheetAction(t("Scan a prescription or strip", "पर्चा या पत्ता स्कैन करें"), Icons.Default.CameraAlt, "care-module-medicines-scan") { onScan() },
        SheetAction(t("Type it in", "ख़ुद लिखें"), Icons.Default.Edit, "care-add-medicine") { onAdd() },
    ), onDismiss = { addSheet = false })

    doseSheet.takeIf { it.isNotBlank() }?.let { key ->
        val item = doses.item(key.substringBefore('|')); val dose = item?.let { doses.dosesOf(it).firstOrNull { d -> d.key == key.substringAfter('|') } }
        if (item == null || dose == null) LaunchedEffect(key) { doseSheet = "" }
        else DoseSheet(item, dose, doses.stateOf(item, dose), doses.photoOf(item), enabled = doses.enabled, onChoice = { doses.mark(item, dose, it, false) },
            actions = medicineActions(item), onDismiss = { doseSheet = "" }, doseLabel = if (!dose.timed && dose.count > 1) t("Dose ${dose.index}", "खुराक ${dose.index}") else null)
    }

    medicineSheet.takeIf { it.isNotBlank() }?.let { id ->
        val item = findMedicine(id)
        if (item == null) LaunchedEffect(id) { medicineSheet = "" }
        else {
            val photo = doses.photoOf(item) ?: pastPhotos[item.id]
            val line = medicineScheduleWords(item)
            SaharaActionSheet(item.title, medicineActions(item), onDismiss = { medicineSheet = "" }, header = {
                if (photo != null || line.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (photo != null) { StripThumb(photo, 96); Spacer(Modifier.width(16.dp)) }
                    if (line.isNotBlank()) Text(line, Modifier.weight(1f), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        }
    }

    if (historyPicker) {
        val all = (doses.medicines + past).distinctBy { it.id }
        SaharaActionSheet(t("History", "इतिहास"), all.map { item -> SheetAction(item.title, Icons.Default.Medication, "medicines-history-${item.id}") { historyFor = item.id } }, onDismiss = { historyPicker = false })
    }
    // A medicine that is gone (removed, or another person chosen) closes its sheet instead of showing nothing.
    val historyItem = historyFor.takeIf { it.isNotBlank() }?.let(::findMedicine)
    if (historyItem != null) MedicineHistorySheet(historyItem, vm) { historyFor = "" } else if (historyFor.isNotBlank()) LaunchedEffect(historyFor) { historyFor = "" }
    val editItem = editFor.takeIf { it.isNotBlank() }?.let(::findMedicine)
    if (editItem != null) AddCareItem(editItem.kind, vm, onDismiss = { editFor = "" }, initial = editItem) else if (editFor.isNotBlank()) LaunchedEffect(editFor) { editFor = "" }
    val removeItem = removeFor.takeIf { it.isNotBlank() }?.let(::findMedicine)
    if (removeItem != null) RemoveMedicineDialog(removeItem, vm) { removeFor = "" } else if (removeFor.isNotBlank()) LaunchedEffect(removeFor) { removeFor = "" }
    val sideEffectItem = sideEffectFor.takeIf { it.isNotBlank() }?.let(::findMedicine)
    if (sideEffectItem != null) SideEffectSheet(sideEffectItem, vm, onDismiss = { sideEffectFor = "" }) else if (sideEffectFor.isNotBlank()) LaunchedEffect(sideEffectFor) { sideEffectFor = "" }
    if (raiseSheet) SideEffectsToRaiseSheet(sideEffects, vm, clinician) { raiseSheet = false }
}

/** "8:00 AM" for a saved dose time ("08:00"); the saved text when it cannot be read. */
private fun doseClock(time: String, hindi: Boolean): String = runCatching { doseClock(LocalTime.parse(time), hindi) }.getOrDefault(time)
private fun doseClock(time: LocalTime, hindi: Boolean): String = time.format(careDateFormat("h:mm a", hindi))

// ───────────────────────────── The list ─────────────────────────────

/** Today's doses by time, then Any time, When needed and the medicines with no dose today. [shown] filters by the search. */
private fun LazyListScope.medicineRows(doses: TodayDoses, shown: (CareItem) -> Boolean, onOpenDose: (CareItem, MedicineDose) -> Unit, onOpenMedicine: (CareItem) -> Unit) {
    // medicine-row-<id> marks the first row of each medicine on the page.
    val firstOf = mutableSetOf<String>()
    fun first(item: CareItem) = firstOf.add(item.id)
    doses.groups.forEach { group ->
        val entries = doses.entries(group).filter { (item, _) -> shown(item) }
        if (entries.isEmpty()) return@forEach
        val firsts = entries.map { (item, _) -> first(item) }
        stickyHeader(key = "h-${group.time}", contentType = "header") { TimeGroupHeader(doses, group) }
        itemsIndexed(entries, key = { _, (item, dose) -> "${item.id}:${dose.key}" }, contentType = { _, _ -> "dose" }) { index, (item, dose) ->
            DoseListRow(doses, item, dose, firsts[index], divider = index > 0, onOpen = { onOpenDose(item, dose) })
        }
    }
    val untimed = doses.untimed.filter { (item, _) -> shown(item) }
    if (untimed.isNotEmpty()) {
        val firsts = untimed.map { (item, _) -> first(item) }
        stickyHeader(key = "h-any-time", contentType = "header") {
            val t = careText(); val left = untimed.count { (item, dose) -> doses.stateOf(item, dose) == MedicineDoseState.PENDING }
            ListHeader(Icons.Default.Schedule, t("Any time", "कभी भी"), left, "today-doses-any-time")
        }
        itemsIndexed(untimed, key = { _, (item, dose) -> "${item.id}:${dose.key}" }, contentType = { _, _ -> "dose" }) { index, (item, dose) ->
            DoseListRow(doses, item, dose, firsts[index], divider = index > 0, onOpen = { onOpenDose(item, dose) })
        }
    }
    val asNeeded = doses.asNeeded.filter(shown)
    if (asNeeded.isNotEmpty()) {
        val firsts = asNeeded.map { first(it) }
        stickyHeader(key = "h-as-needed", contentType = "header") { val t = careText(); ListHeader(Icons.Default.Medication, t("When needed", "ज़रूरत पड़ने पर"), null, "medicines-group-as_needed") }
        itemsIndexed(asNeeded, key = { _, item -> "${item.id}:as-needed" }, contentType = { _, _ -> "as-needed" }) { index, item ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 88.dp))
            Box(if (firsts[index]) Modifier.testTag("medicine-row-${item.id}") else Modifier) {
                AsNeededRow(item, doses.asNeededTimes(item), doses.photoOf(item), doses.enabled, onTook = { doses.tookAsNeeded(item) }, onOpen = { onOpenMedicine(item) })
            }
        }
    }
    val others = doses.others.filter(shown)
    if (others.isNotEmpty()) {
        stickyHeader(key = "h-other", contentType = "header") { val t = careText(); ListHeader(null, t("Other", "अन्य"), null, "medicines-group-other") }
        itemsIndexed(others, key = { _, item -> "${item.id}:other" }, contentType = { _, _ -> "other" }) { index, item ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 88.dp))
            SaharaRow(item.title, Modifier.testTag("medicine-row-${item.id}"), supporting = medicineScheduleWords(item).ifBlank { null },
                leading = { MedicineThumb(doses.photoOf(item)) }, onClick = { onOpenMedicine(item) })
        }
    }
}

/** One dose: the shared [DoseRow] with the medicine's strength, amount and food as its one supporting line. */
@Composable private fun DoseListRow(doses: TodayDoses, item: CareItem, dose: MedicineDose, firstOfMedicine: Boolean, divider: Boolean, onOpen: () -> Unit) {
    val t = careText()
    val line = medicineDetailWords(item)
    val supporting = if (!dose.timed && dose.count > 1) listOf(t("Dose ${dose.index}", "खुराक ${dose.index}"), line).filter(String::isNotBlank).joinToString(" · ") else line
    Column(Modifier.fillMaxWidth().then(if (firstOfMedicine) Modifier.testTag("medicine-row-${item.id}") else Modifier)) {
        if (divider) HorizontalDivider(Modifier.padding(start = 88.dp))
        DoseRow(item, dose, doses.stateOf(item, dose), doses.photoOf(item), supporting, enabled = doses.enabled, family = doses.familyOf(item, dose),
            onMark = { choice, quick -> doses.mark(item, dose, choice, quick) }, onOpen = onOpen, modifier = Modifier.testTag("today-dose-${item.id}-${dose.key}"))
    }
}

/** "Morning · 8:00 AM" with the time's picture, and "2 to take" (or a tick once every dose of the time is marked). Sticky. */
@Composable private fun TimeGroupHeader(doses: TodayDoses, group: DoseGroup) {
    val t = careText(); val hindi = careHindi()
    val clock = remember(group.time, hindi) { doseClock(group.time, hindi) }
    ListHeader(doseSlotIcon(group.slot), t(group.slot.english, group.slot.hindi) + " · " + clock, doses.pendingIn(group), "today-doses-${group.time}")
}

/** A sticky section header on the page background: plain icon, the title in the accent colour, and what is left on the right. */
@Composable private fun ListHeader(icon: ImageVector?, title: String, left: Int?, tag: String) {
    val t = careText(); val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(scheme.background).heightIn(min = 48.dp).padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant); Spacer(Modifier.width(12.dp)) }
        Text(title, Modifier.weight(1f).a11yHeading(), style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.SemiBold, color = scheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        when {
            left == null -> {}
            left == 0 -> Icon(Icons.Default.Check, t("All marked", "सब दर्ज"), Modifier.size(20.dp), tint = scheme.primary)
            else -> Text(t("$left to take", "$left लेनी हैं"), fontSize = 15.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** An as-needed medicine: how often it was taken today, and "Took one" (any number of times; each is saved with its time). */
@Composable private fun AsNeededRow(item: CareItem, times: List<Long>, photo: File?, enabled: Boolean, onTook: () -> Unit, onOpen: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val haptic = rememberSaharaHaptic(); val scheme = MaterialTheme.colorScheme
    val last = times.lastOrNull()?.let { doseClock(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime(), hindi) }
    val count = when { times.isEmpty() -> t("Not taken today", "आज नहीं ली")
        times.size == 1 -> t("Taken once today, at $last", "आज एक बार ली, $last पर")
        else -> t("Taken ${times.size} times today, last at $last", "आज ${times.size} बार ली, आख़िरी $last पर") }
    ListItem(
        headlineContent = { Text(item.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(count, Modifier.testTag("today-as-needed-count"), fontSize = 15.sp, maxLines = 2, color = scheme.onSurfaceVariant) },
        leadingContent = { MedicineThumb(photo) },
        trailingContent = {
            FilledTonalButton(onClick = { haptic(); onTook() }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("today-as-needed-took")) {
                Text(t("Took one", "एक ली"), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onOpen).testTag("today-as-needed-${item.id}"))
}

/** The strip photo (real content) or a plain pill icon, 56dp. */
@Composable private fun MedicineThumb(photo: File?) {
    if (photo != null) StripThumb(photo, 56)
    else Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Medication, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** The dose ring and what is next: "3 of 5" in the ring, "Next: Night" and "9:00 PM · 2 medicines" beside it. */
@Composable private fun MedicinesHero(doses: TodayDoses) {
    val t = careText(); val hindi = careHindi(); val scheme = MaterialTheme.colorScheme
    val taken = doses.taken; val total = doses.total
    val spoken = t("$taken of $total taken", "$total में से $taken ली")
    Row(Modifier.fillMaxWidth().padding(16.dp).testTag("medicines-hero"), verticalAlignment = Alignment.CenterVertically) {
        SaharaRing(listOf(RingArc(if (total == 0) 0f else taken.toFloat() / total)), description = spoken, size = 128.dp, stroke = 12.dp) {
            if (doses.allTaken) Icon(Icons.Default.Check, null, Modifier.size(40.dp), tint = scheme.primary)
            else Text(t("$taken of $total", "$total में से $taken"), Modifier.padding(horizontal = 16.dp), fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken }.testTag("today-doses-count")) {
            val next = doses.nextPending
            when {
                doses.allTaken -> Text(t("All done for today", "आज की सब दवाएँ हो गईं"), style = MaterialTheme.typography.titleLarge)
                next != null -> {
                    val slot = t(next.slot.english, next.slot.hindi)
                    val clock = remember(next.time, hindi) { doseClock(next.time, hindi) }
                    val count = next.medicines.size
                    Text(t("Next: $slot", "अगली: $slot"), style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(clock + " · " + (if (count == 1) t("1 medicine", "1 दवा") else t("$count medicines", "$count दवाएँ")), Modifier.padding(top = 4.dp), fontSize = 16.sp, color = scheme.onSurfaceVariant)
                }
                else -> { val left = total - taken; Text(t("$left to take", "$left लेनी हैं"), style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

/** No medicine yet: a plain icon and one line; the FAB adds one. */
@Composable private fun MedicinesEmpty() {
    val t = careText(); val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 64.dp).testTag("medicines-empty"), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Medication, null, Modifier.size(48.dp), tint = scheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(t("No medicines yet.", "अभी कोई दवा नहीं।"), fontSize = 20.sp, textAlign = TextAlign.Center)
    }
}

/** "500 mg · 1 tablet · After food" from the saved form; empty when nothing was filled in. */
@Composable private fun medicineDetailWords(item: CareItem): String {
    val t = careText()
    val entry = remember(item.detail) { MedicineEntryText.parse(item.detail) } ?: return ""
    val food = when (entry.food) { MedicineMealTiming.BEFORE_FOOD -> t("Before food", "खाने से पहले"); MedicineMealTiming.WITH_FOOD -> t("With food", "खाने के साथ"); MedicineMealTiming.AFTER_FOOD -> t("After food", "खाने के बाद"); else -> "" }
    return listOf(entry.strength, entry.howMuch, food).filter(String::isNotBlank).joinToString(" · ")
}

/** The detail line, or "Not daily" / "As needed" for a medicine without a dose today. */
@Composable private fun medicineScheduleWords(item: CareItem): String {
    val t = careText()
    val detail = medicineDetailWords(item)
    val kind = when { MedicineDoses.isAsNeededMedicine(item) -> t("As needed", "ज़रूरत पड़ने पर"); MedicineDoses.notDaily(item) -> t("Not daily", "रोज़ नहीं"); else -> "" }
    return listOf(kind, detail).filter(String::isNotBlank).joinToString(" · ")
}

// ───────────────────────────── Sub-pages and sheets ─────────────────────────────

/** Past medicines as a pushed page (back arrow, no bottom bar). A row opens the medicine's sheet (History, Remove). */
@Composable internal fun PastMedicinesPage(past: List<CareItem>, photos: Map<String, File?>, onBack: () -> Unit, onOpen: (CareItem) -> Unit) {
    val t = careText()
    SaharaPage(t("Past medicines", "पहले की दवाइयाँ"), onBack = onBack, listModifier = Modifier.testTag("medicines-past")) {
        itemsIndexed(past, key = { _, item -> item.id }) { index, item ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 88.dp))
            SaharaRow(item.title, Modifier.testTag("medicine-past-${item.id}"), supporting = medicineScheduleWords(item).ifBlank { null }, leading = { MedicineThumb(photos[item.id]) }, onClick = { onOpen(item) })
        }
    }
}

/** The side effects to raise at the next visit, in a sheet (replaces the Side effects to raise card). */
@Composable private fun SideEffectsToRaiseSheet(entries: List<SideEffectEntry>, vm: CareModel, clinician: Boolean, onDismiss: () -> Unit) {
    val t = careText()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("side-effects-to-raise-sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(t("Side effects to raise", "बताने वाले दुष्प्रभाव"), Modifier.padding(horizontal = 24.dp, vertical = 8.dp).a11yHeading(), style = MaterialTheme.typography.titleLarge)
            entries.forEachIndexed { i, e -> key(e.item.id) {
                if (i > 0) HorizontalDivider()
                Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    SideEffectSummary(e, showMedicine = true, showReporter = clinician) { MarkDiscussedButton(e, vm, SideEffectRules.reporterFor(vm.viewRole.value)) }
                }
            } }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}
