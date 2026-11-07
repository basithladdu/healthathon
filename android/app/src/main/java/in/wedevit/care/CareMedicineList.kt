package `in`.wedevit.care

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            MedicineSlotInput(item.id, item.title, times, MedicineDoses.isAsNeeded(MedicineDoses.frequencyText(item.detail)))
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var pastOpen by rememberSaveable { mutableStateOf(false) }
    val searching = current.size > MedicineTimeGroups.SEARCH_ABOVE
    val active = if (searching) query else ""
    val groups = remember(inputs, active) { MedicineTimeGroups.group(MedicineTimeGroups.filterByName(inputs, active)) }
    val byId = remember(current) { current.associateBy { it.id } }
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (searching) OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, singleLine = true,
            label = { Text(t("Search medicines", "दवा खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().testTag("medicines-search"))
        if (current.isEmpty()) QuietLine(t("No current medicines.", "अभी कोई दवा नहीं चल रही।"))
        else if (groups.isEmpty()) QuietLine(t("No medicine matches that name.", "इस नाम की कोई दवा नहीं मिली।"))
        groups.forEach { (slot, list) ->
            Text("${medicineSlotTitle(slot)} · ${list.size}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.padding(start = 4.dp, top = 4.dp).a11yHeading().testTag("medicines-group-${slot.name.lowercase()}"))
            list.forEach { input -> byId[input.id]?.let { item -> key(item.id) { MedicineCompactRow(item, vm) } } }
        }
        if (past.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { pastOpen = !pastOpen }.padding(horizontal = 4.dp).testTag("medicines-past-toggle"), verticalAlignment = Alignment.CenterVertically) {
                Text("${t("Past", "पहले की")} (${past.size})", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.weight(1f).a11yHeading())
                Icon(if (pastOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (pastOpen) t("Hide past medicines", "पहले की दवाइयाँ छिपाएँ") else t("Show past medicines", "पहले की दवाइयाँ दिखाएँ"), tint = DoctorCardSub)
            }
            if (pastOpen) past.forEach { item -> key(item.id) { CareRow(item, vm) } }
        }
    }
}

@Composable private fun medicineSlotTitle(slot: MedicineSlot): String { val t = careText()
    return when (slot) {
        MedicineSlot.MORNING -> t("Morning", "सुबह"); MedicineSlot.AFTERNOON -> t("Afternoon", "दोपहर"); MedicineSlot.EVENING -> t("Evening", "शाम"); MedicineSlot.NIGHT -> t("Night", "रात")
        MedicineSlot.AS_NEEDED -> t("As needed", "ज़रूरत पड़ने पर"); MedicineSlot.OTHER -> t("Other", "अन्य")
    }
}

/**
 * One medicine on one compact row: name and strength on a line, then its dose chips (the tick stays one tap away).
 * Tap the row to open the full card (directions, food, strip photo, the menu); "Show less" folds it again.
 */
@Composable private fun MedicineCompactRow(item: CareItem, vm: CareModel) {
    val t = careText()
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().animateContentSize().testTag("medicine-row-${item.id}")) {
        if (open) Column {
            MedicineCareCard(item, vm)
            TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("medicine-collapse-${item.id}")) { Icon(Icons.Default.KeyboardArrowUp, null); Spacer(Modifier.width(4.dp)); Text(t("Show less", "कम दिखाएँ")) }
        } else {
            val entry = remember(item.detail) { MedicineEntryText.parse(item.detail) }
            val strength = entry?.let { e -> listOf(e.strength, e.howMuch).filter { it.isNotBlank() }.joinToString(" · ") }.orEmpty()
            Surface(shape = RoundedCornerShape(16.dp), color = SaharaSection.Medicines.wash, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = t("Show details", "ब्यौरा दिखाएँ")) { open = true }.testTag("medicine-expand-${item.id}"), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (strength.isNotBlank()) Text(" · $strength", fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, t("Show details", "ब्यौरा दिखाएँ"), tint = DoctorCardSub)
                    }
                    MedicineDoseActions(item, vm, showHistory = false)
                }
            }
        }
    }
}
