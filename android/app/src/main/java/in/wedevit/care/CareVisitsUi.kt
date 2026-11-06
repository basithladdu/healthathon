@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A doctor's speciality, read from words the family or doctor already typed for the visit. Never guessed beyond those words. */
enum class CareVisitSpeciality { ONCOLOGY, PALLIATIVE, RADIOTHERAPY, SURGERY, HOME_CARE, GENERAL }

/** What one compact visit card shows. [status] is "" (planned), "completed", "cancelled" or "missed". */
data class CareVisitInfo(
    val id: String, val due: Long, val timed: Boolean, val doctor: String, val speciality: CareVisitSpeciality?,
    val place: String, val note: String, val status: String, val structured: Boolean,
)

/** Medicines added and prescription papers saved on a visit's day: what "that day's prescription" can honestly point to. */
data class CareVisitDayPrescription(val medicines: List<CareItem>, val documents: List<CareReport>) {
    val isEmpty get() = medicines.isEmpty() && documents.isEmpty()
}

object CareVisitRules {
    // Checked in this order: "radiation oncology" is radiotherapy and "pain and palliative" is palliative, before the wider words.
    private val cues: List<Pair<CareVisitSpeciality, List<String>>> = listOf(
        CareVisitSpeciality.PALLIATIVE to listOf("palliative", "hospice", "पैलिएटिव", "प्रशामक"),
        CareVisitSpeciality.RADIOTHERAPY to listOf("radiotherapy", "radiation", "radiation oncology", "रेडियोथेरेपी"),
        CareVisitSpeciality.ONCOLOGY to listOf("oncology", "oncologist", "onco", "cancer", "chemo", "chemotherapy", "कैंसर", "ऑन्कोलॉजी"),
        CareVisitSpeciality.SURGERY to listOf("surgery", "surgeon", "surgical", "सर्जरी", "सर्जन"),
        CareVisitSpeciality.HOME_CARE to listOf("home care", "home visit", "होम केयर"),
        CareVisitSpeciality.GENERAL to listOf("general medicine", "physician", "family doctor", "general practitioner", "gp"),
    )

    private fun spaced(text: String) = " " + text.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim() + " "

    /** The first speciality whose words appear in [texts] as whole words, or null. */
    fun speciality(vararg texts: String): CareVisitSpeciality? {
        val text = spaced(texts.joinToString(" "))
        return cues.firstOrNull { (_, words) -> words.any { text.contains(" ${spaced(it).trim()} ") } }?.first
    }

    /** One visit as its card shows it. A family's own visit keeps "Doctor or hospital" in its title; a doctor's planned visit keeps the doctor in its sender. */
    fun info(item: CareItem): CareVisitInfo {
        val entry = CareEntryCodec.decode(item.detail)?.takeIf { it.type == "schedule" }
        val doctor = entry?.sender?.trim()?.ifBlank { null } ?: item.title.trim()
        val place = entry?.location?.trim().orEmpty()
        val note = if (entry != null) DoctorVisitDetails.readable(entry.detail).trim() else if (CareEntryCodec.isStructured(item.detail)) "" else item.detail.trim()
        val status = when {
            entry != null -> entry.status.takeIf { it in setOf("completed", "cancelled", "missed") }.orEmpty()
            item.doneOn.isNotBlank() -> "completed"
            else -> ""
        }
        return CareVisitInfo(item.id, item.due, entry?.timed == true, doctor, speciality(item.title, doctor, place, note), place, note, status, entry != null)
    }

    /**
     * Medicines of the same person added on the visit's day, and prescription documents saved that day ([folderOf] says which
     * documents are prescriptions). Empty for an undated visit.
     */
    fun dayPrescription(visit: CareItem, items: List<CareItem>, documents: List<CareReport>, folderOf: (CareReport) -> String, zone: ZoneId = ZoneId.systemDefault()): CareVisitDayPrescription {
        if (visit.due <= 0) return CareVisitDayPrescription(emptyList(), emptyList())
        fun day(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val visitDay = day(visit.due)
        val medicines = items.filter { it.personId == visit.personId && it.kind == "medicine" && it.created > 0 && day(it.created) == visitDay }.sortedBy { it.title.lowercase() }
        val papers = documents.filter { it.personId == visit.personId && it.created > 0 && day(it.created) == visitDay && folderOf(it) == DocumentFolderRules.PRESCRIPTIONS }.distinctBy { it.id }.sortedBy { it.created }
        return CareVisitDayPrescription(medicines, papers)
    }
}

/** Open tasks first (soonest due, undated last), then done ones (most recently done first). */
object CareTodoRules {
    fun split(tasks: List<CareItem>): Pair<List<CareItem>, List<CareItem>> {
        val (done, open) = tasks.partition { it.doneOn.isNotBlank() }
        return open.sortedWith(compareBy<CareItem> { if (it.due > 0) it.due else Long.MAX_VALUE }.thenBy { it.title.lowercase() }) to
            done.sortedWith(compareByDescending<CareItem> { it.doneOn }.thenByDescending { it.created })
    }
    /** Due before today and not done. */
    fun overdue(item: CareItem, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) = item.doneOn.isBlank() && item.due > 0 && Instant.ofEpochMilli(item.due).atZone(zone).toLocalDate() < today
}

/** One section colour per speciality: its wash for the card, white for the date block, its solid for the speciality chip. */
private fun visitTone(s: CareVisitSpeciality?): SaharaTone = when (s) {
    CareVisitSpeciality.ONCOLOGY -> SaharaTone.Plum
    CareVisitSpeciality.PALLIATIVE -> SaharaTone.Teal
    CareVisitSpeciality.RADIOTHERAPY -> SaharaTone.Gold
    CareVisitSpeciality.SURGERY -> SaharaTone.Indigo
    CareVisitSpeciality.HOME_CARE -> SaharaTone.Marigold
    CareVisitSpeciality.GENERAL -> SaharaTone.Leaf
    null -> SaharaTone.Neutral
}

@Composable private fun visitSpecialityLabel(s: CareVisitSpeciality): String { val t = careText(); return when (s) {
    CareVisitSpeciality.ONCOLOGY -> t("Oncology", "कैंसर विभाग"); CareVisitSpeciality.PALLIATIVE -> t("Palliative care", "पैलिएटिव केयर")
    CareVisitSpeciality.RADIOTHERAPY -> t("Radiotherapy", "रेडियोथेरेपी"); CareVisitSpeciality.SURGERY -> t("Surgery", "सर्जरी")
    CareVisitSpeciality.HOME_CARE -> t("Home care", "घर पर देखभाल"); CareVisitSpeciality.GENERAL -> t("General medicine", "सामान्य चिकित्सा")
} }

@Composable private fun visitStatusLabel(status: String): String { val t = careText(); return when (status) {
    "completed" -> t("Completed", "पूरा हुआ"); "cancelled" -> t("Cancelled", "रद्द"); "missed" -> t("Missed", "छूट गई"); else -> ""
} }

/** A coloured date block: big day number over the short month; a dash when the date is not recorded. */
@Composable private fun VisitDateBlock(at: Long, color: Color) {
    val hindi = careHindi()
    val day = at.takeIf { it > 0 }?.let(::careDay)
    Surface(shape = RoundedCornerShape(14.dp), color = color, modifier = Modifier.size(width = 48.dp, height = 52.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(day?.dayOfMonth?.toString() ?: "—", fontFamily = SaharaTitleFont, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1)
            if (day != null) Text(day.format(careDateFormat("MMM", hindi)).uppercase(), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = RustText, maxLines = 1)
            if (day != null && day.year != LocalDate.now().year) Text("${day.year}", fontSize = 11.sp, lineHeight = 12.sp, color = DoctorCardSub, maxLines = 1)
        }
    }
}

/** A small rounded label. */
@Composable private fun VisitChip(text: String, color: Color, ink: Color = DoctorCardInk) {
    Surface(shape = RoundedCornerShape(50), color = color) { Text(text, Modifier.padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** One visit: date, doctor and speciality only. A tap opens that day's details. */
@Composable fun CareVisitRow(item: CareItem, onOpen: () -> Unit) {
    val t = careText()
    val info = remember(item) { CareVisitRules.info(item) }
    val tone = visitTone(info.speciality)
    val speciality = info.speciality?.let { visitSpecialityLabel(it) }
    val status = visitStatusLabel(info.status)
    val dateText = if (item.due > 0) date(item.due) else t("Date not recorded", "तारीख़ दर्ज नहीं")
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = tone.wash, border = BorderStroke(1.dp, DoctorHairline),
        modifier = Modifier.fillMaxWidth().saharaAppear().heightIn(min = 64.dp).semantics(mergeDescendants = true) { contentDescription = listOfNotNull(dateText, info.doctor, speciality, status.ifBlank { null }).joinToString(", ") }.testTag("care-visit-${item.id}")) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            VisitDateBlock(item.due, Paper)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.doctor.ifBlank { t("Visit", "मुलाक़ात") }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (info.status == "cancelled") TextDecoration.LineThrough else null)
                if (speciality != null || status.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (speciality != null) VisitChip(speciality, tone.solid, tone.onSolid)
                    if (status.isNotBlank()) VisitChip(status, Paper, if (info.status == "completed") DoctorAccent else RustText)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(22.dp), tint = Rust)
        }
    }
}

/**
 * The Visits module: upcoming and past visits as compact cards. A tap opens that day's details and prescription.
 * [onCalendarAt] opens the Calendar on a day (a doctor's planned visit is changed there).
 */
@Composable fun CareVisitsModule(vm: CareModel, upcoming: List<CareItem>, past: List<CareItem>, onCalendarAt: (Long) -> Unit) {
    val t = careText()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    if (upcoming.isEmpty() && past.isEmpty()) { SaharaLottie(SaharaLottieKind.Calendar, Modifier.fillMaxWidth()); ModuleEmpty(Icons.Default.EventAvailable, t("No visits yet.", "अभी कोई मुलाक़ात नहीं।")) } else {
        CareModuleList("visits-upcoming", upcoming, t("No upcoming visits.", "आगे कोई मुलाक़ात नहीं।"), t("Upcoming", "आगे की"), { it.id }) { CareVisitRow(it) { openId = it.id } }
        CareModuleList("visits-past", past, t("No past visits yet.", "पहले की कोई मुलाक़ात नहीं।"), t("Past", "पहले की"), { it.id }) { CareVisitRow(it) { openId = it.id } }
    }
    (upcoming + past).firstOrNull { it.id == openId }?.let { visit -> CareVisitDaySheet(vm, visit, onCalendarAt) { openId = null } }
}

/** A friendly empty module: a soft icon and one line. */
@Composable private fun ModuleEmpty(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = SaharaApricot) { Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = DoctorAccent) }
        Spacer(Modifier.width(12.dp)); Text(text, fontSize = 15.sp, color = DoctorCardSub)
    }
}

/** That visit's day: when, who, where, the note and the day's prescription, with Edit/Remove for a family's own visit. */
@Composable fun CareVisitDaySheet(vm: CareModel, visit: CareItem, onCalendarAt: (Long) -> Unit, onDismiss: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val all by vm.allItems.collectAsStateWithLifecycle(); val saved by vm.reports.collectAsStateWithLifecycle()
    val capturedAccount = remember(visit.id) { vm.accountNamespace }
    val info = remember(visit) { CareVisitRules.info(visit) }
    val tone = visitTone(info.speciality)
    // Scanned prescription photos are prescriptions by definition; other documents count when filed under Prescriptions.
    var originals by remember(visit.personId) { mutableStateOf<List<CareReport>>(emptyList()) }
    LaunchedEffect(visit.personId) { originals = withContext(Dispatchers.IO) { runCatching { MedicineSourceStore(context.applicationContext, vm.storageNamespace).list(visit.personId) }.getOrDefault(emptyList()) } }
    val folders = remember(vm.storageNamespace) { runCatching { DocumentFolderStore(context, vm.storageNamespace).all() }.getOrDefault(emptyMap()) }
    val originalIds = originals.map { it.id }.toSet()
    val prescription = remember(visit, all, saved, originals) {
        CareVisitRules.dayPrescription(visit, all, saved + originals, folderOf = { r -> if (r.id in originalIds) DocumentFolderRules.PRESCRIPTIONS else folders[r.id]?.folder ?: DocumentFolderRules.OTHER })
    }
    var editing by remember { mutableStateOf(false) }; var removing by remember { mutableStateOf(false) }
    val whenText = if (visit.due <= 0) t("Date not recorded", "तारीख़ दर्ज नहीं") else Instant.ofEpochMilli(visit.due).atZone(ZoneId.systemDefault())
        .format(if (info.timed) careDateFormat("EEE, d MMM yyyy · h:mm a", hindi) else careDateFormat("EEE, d MMM yyyy", hindi))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp).testTag("care-visit-day"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = tone.wash, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    VisitDateBlock(visit.due, Paper); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(info.doctor.ifBlank { t("Visit", "मुलाक़ात") }, fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                        Text(whenText, fontSize = 14.sp, color = DoctorCardSub)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            info.speciality?.let { VisitChip(visitSpecialityLabel(it), tone.solid, tone.onSolid) }
                            visitStatusLabel(info.status).takeIf(String::isNotBlank)?.let { VisitChip(it, Paper, if (info.status == "completed") DoctorAccent else RustText) }
                        }
                    }
                }
            }
            if (info.place.isNotBlank()) VisitDetailLine(Icons.Default.Place, info.place)
            if (info.note.isNotBlank()) VisitDetailLine(Icons.Default.EditNote, info.note)
            Text(t("Prescription", "पर्चा"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            if (prescription.isEmpty) Row(Modifier.fillMaxWidth().testTag("care-visit-no-prescription"), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(12.dp))
                Text(t("No prescription", "कोई पर्चा नहीं"), fontSize = 14.sp, color = DoctorCardSub)
            }
            else Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("care-visit-prescription")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prescription.documents.forEach { doc -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(10.dp))
                        Text(doc.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } }
                    prescription.medicines.forEach { m -> Row(verticalAlignment = Alignment.Top) {
                        Surface(shape = CircleShape, color = SaharaSoftBlue) { Icon(Icons.Default.Medication, null, Modifier.padding(5.dp).size(16.dp), tint = DoctorAccent) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            CareEntryCodec.readable(m.detail).takeIf(String::isNotBlank)?.let { Text(it, fontSize = 14.sp, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    } }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visit.due > 0) OutlinedButton(onClick = { onDismiss(); onCalendarAt(visit.due) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Open in calendar", "कैलेंडर में खोलें"))
                }
                if (canEdit && !info.structured) {
                    OutlinedButton(onClick = { editing = true }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("care-visit-edit")) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Edit", "बदलें")) }
                    TextButton(onClick = { removing = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("care-visit-remove")) { Text(t("Remove", "हटाएँ"), color = RustText) }
                }
            }
        }
    }
    if (editing) AddCareItem("visit", vm, onDismiss = { editing = false }, initial = visit)
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text(t("Remove this entry?", "यह जानकारी हटाएँ?")) }, text = { Text(visit.title) },
        confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, visit.personId, capturedAccount)) vm.remove(visit); removing = false; onDismiss() }, enabled = !busy && canEdit) { Text(t("Remove", "हटाएँ")) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(t("Keep", "रखें")) } })
}

@Composable private fun VisitDetailLine(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.size(20.dp), tint = Rust); Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 15.sp, color = DoctorCardInk)
        }
    }
}

/** The To do module: open tasks as one-line rows, then done ones. */
@Composable fun CareTodoModule(vm: CareModel, tasks: List<CareItem>) {
    val t = careText()
    val (open, done) = remember(tasks) { CareTodoRules.split(tasks) }
    if (tasks.isEmpty()) ModuleEmpty(Icons.Default.Checklist, t("No tasks yet.", "अभी कोई काम नहीं।")) else {
        CareModuleList("to-do", open, t("Nothing left to do.", "कोई काम बाक़ी नहीं।"), keyOf = { it.id }) { CareTaskRow(it, vm) }
        if (done.isNotEmpty()) CareModuleList("to-do-done", done, "", t("Done", "हो गया"), { it.id }) { CareTaskRow(it, vm) }
    }
}

/** One task on one line: tick box, title and due date. A tap shows its details; Edit and Remove sit behind the ⋮ menu. */
@Composable fun CareTaskRow(item: CareItem, vm: CareModel) {
    val t = careText()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }; var editing by rememberSaveable(item.id) { mutableStateOf(false) }; var removing by rememberSaveable(item.id) { mutableStateOf(false) }
    val done = item.doneOn.isNotBlank()
    val overdue = CareTodoRules.overdue(item, LocalDate.now())
    val detail = item.detail.takeIf { !CareEntryCodec.isStructured(it) }?.trim().orEmpty()
    Surface(onClick = { if (detail.isNotBlank()) expanded = !expanded }, shape = RoundedCornerShape(16.dp), color = if (done) Sage.copy(alpha = .45f) else Paper, border = BorderStroke(1.dp, DoctorHairline),
        modifier = Modifier.fillMaxWidth().saharaAppear().heightIn(min = 56.dp).testTag("care-task-${item.id}")) {
        Column(Modifier.padding(end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = done, onCheckedChange = { if (!busy && canEditCareContext(vm, item.personId, capturedAccount)) vm.toggle(item) }, enabled = canEdit,
                    modifier = Modifier.semantics { contentDescription = "${t("Done", "हो गया")}: ${item.title}" })
                Text(item.title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (done) DoctorCardSub else DoctorCardInk, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null)
                if (item.due > 0) VisitChip(careDay(item.due).format(DateTimeFormatter.ofPattern("d MMM", careLocale(careHindi()))), if (overdue) SaharaTone.Error.wash else SaharaTone.Gold.wash, if (overdue) SaharaTone.Error.ink else DoctorCardInk)
                if (canEdit) Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("care-menu-${item.id}")) { Icon(Icons.Default.MoreVert, t("More actions", "और विकल्प"), tint = DoctorCardInk) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(t("Edit", "बदलें"), color = DoctorCardInk) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = DoctorCardInk) }, onClick = { menu = false; editing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-edit-${item.id}"))
                        DropdownMenuItem(text = { Text(t("Remove", "हटाएँ"), color = RustText) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = RustText) }, onClick = { menu = false; removing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-remove-${item.id}"))
                    }
                } else Spacer(Modifier.width(8.dp))
            }
            if (expanded && detail.isNotBlank()) Text(detail, Modifier.padding(start = 48.dp, end = 12.dp, bottom = 10.dp), fontSize = 14.sp, color = DoctorCardSub)
        }
    }
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text(t("Remove this entry?", "यह जानकारी हटाएँ?")) }, text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.remove(item); removing = false }, enabled = !busy && canEdit) { Text(t("Remove", "हटाएँ")) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(t("Keep", "रखें")) } })
    if (editing) AddCareItem(item.kind, vm, onDismiss = { editing = false }, initial = item)
}

/** One saved conversation: a coloured date block, who it was with, and its version and review state on one line. */
@Composable fun CareConversationRow(note: CareVersion, onRead: (CareVersion) -> Unit) {
    val t = careText()
    Surface(onClick = { onRead(note) }, shape = RoundedCornerShape(20.dp), color = SaharaLavender, border = BorderStroke(1.dp, DoctorHairline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("care-note-${note.id}")) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            VisitDateBlock(note.published, Paper)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(note.doctor.trim().ifBlank { t("Goals of care", "देखभाल के लक्ष्य") }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    VisitChip(t("Version ${note.version}", "संस्करण ${note.version}"), Paper)
                    Text(ReviewWorkflow.label(note), fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(22.dp), tint = Rust)
        }
    }
}
