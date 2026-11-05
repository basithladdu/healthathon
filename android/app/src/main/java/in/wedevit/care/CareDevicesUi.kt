@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.wedevit.care

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * Tubes and lines, v1: what is in, when it was put in, when it is due for a change, where, and a one-tap daily check.
 * [namespace] and [personId] choose whose list this is (pass vm.storageNamespace and the selected person).
 * [palliativePhone] null reads the 24-hour palliative phone from that person's care plan on this phone.
 */
@Composable fun CareDevicesScreen(onBack: () -> Unit, namespace: String = "", personId: String = "", palliativePhone: String? = null) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val store = remember(namespace) { CareDevicesStore(context, namespace) }
    var devices by remember(namespace, personId) { mutableStateOf(runCatching { store.load(personId) }.getOrDefault(emptyList())) }
    val phone = remember(namespace, personId, palliativePhone) {
        palliativePhone?.trim() ?: runCatching { CareDeviceRules.palliativePhone(CarePlanStore(context.applicationContext, namespace).load(personId).ectprs) }.getOrDefault("")
    }
    var editing by remember { mutableStateOf<CareDevice?>(null) }
    var checking by remember { mutableStateOf<CareDevice?>(null) }
    var saveError by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    fun persist(next: List<CareDevice>): Boolean = runCatching { store.save(personId, next) }.isSuccess.also { ok -> saveError = !ok; if (ok) devices = next }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("devices-screen"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("devices-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
            Spacer(Modifier.width(4.dp))
            Heading(t("Tubes and lines", "नलियाँ और लाइनें"), 26)
        } }
        if (CareDeviceRules.tellCareTeam(devices, today)) item { DevicesTellTeam(phone) { open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(phone)))) } }
        if (saveError) item { Row(Modifier.fillMaxWidth().announce(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(8.dp))
            Text(t("Couldn't save. Try again.", "सहेजा नहीं जा सका। फिर कोशिश करें।"), color = RustText, fontWeight = FontWeight.Medium)
        } }
        // Empty state: icon, one line, and the one action (the add button sits inside it, so it is never shown twice).
        if (devices.isEmpty()) item { Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("devices-empty")) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = Mint, contentColor = DoctorCardInk, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Medication, null, Modifier.size(24.dp)) } }
                Text(t("No tubes or lines added", "कोई नली या लाइन नहीं जोड़ी गई"), fontSize = 16.sp, color = DoctorCardInk)
            }
        } }
        items(CareDeviceRules.sorted(devices), key = { it.id }) { d -> DevicesCard(d, today, hindi, onCheck = { checking = d }, onEdit = { editing = d }) }
        item { Button(onClick = { editing = CareDevice() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("devices-add"), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(t("Add tube or line", "नली या लाइन जोड़ें"), fontWeight = FontWeight.SemiBold)
        } }
    }
    editing?.let { d ->
        val existing = devices.any { it.id == d.id }
        DevicesEditSheet(d, existing, hindi, onDismiss = { editing = null }, onRemove = { if (persist(devices.filter { it.id != d.id })) editing = null }) { saved -> if (persist(CareDeviceRules.upsert(devices, saved))) editing = null }
    }
    checking?.let { d ->
        DevicesCheckDialog(d, CareDeviceRules.checkOn(d, today)?.signs.orEmpty(), onDismiss = { checking = null }) { signs ->
            val current = devices.firstOrNull { it.id == d.id } ?: d
            if (persist(CareDeviceRules.upsert(devices, CareDeviceRules.record(current, today, signs)))) checking = null
        }
    }
}

/** The only advice the screen gives: tell the care team today, with the palliative phone when the care plan has one. */
@Composable private fun DevicesTellTeam(phone: String, onCall: () -> Unit) { val t = careText()
    Surface(shape = RoundedCornerShape(20.dp), color = Peach, contentColor = DoctorCardInk, modifier = Modifier.fillMaxWidth().testTag("devices-tell-team").announce()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = RustText); Spacer(Modifier.width(12.dp))
            Text(t("Tell your care team today", "आज ही अपनी देखभाल टीम को बताएँ"), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (phone.any(Char::isDigit)) Button(onClick = onCall, modifier = Modifier.heightIn(min = 48.dp).testTag("devices-call-team"), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = RustText)) {
                Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(phone, maxLines = 1)
            }
        }
    }
}

@Composable private fun DevicesCard(d: CareDevice, today: LocalDate, hindi: Boolean, onCheck: () -> Unit, onEdit: () -> Unit) { val t = careText()
    val fmt = careDateFormat("d MMM yyyy", hindi)
    val check = CareDeviceRules.checkOn(d, today)
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("device-${d.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Title row: a colour dot for how soon the change is due, the type, and Edit.
            val dueSoon = CareDeviceRules.due(d, today)
            val dot = when (dueSoon?.kind) { CareDeviceDueKind.OVERDUE, CareDeviceDueKind.TODAY -> Rust; CareDeviceDueKind.LATER -> if ((dueSoon?.days ?: Long.MAX_VALUE) <= 7L) Gold else DoctorStatusGood; null -> DoctorHairline }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(10.dp))
                Text(t(d.type.en, d.type.hi), Modifier.weight(1f), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp).testTag("device-edit-${d.id}")) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = RustText); Spacer(Modifier.width(4.dp)); Text(t("Edit", "बदलें"), color = RustText) }
            }
            val line = listOfNotNull(d.inserted?.let { t("Put in", "लगाई गई") + " " + it.format(fmt) }, d.centre.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (line.isNotBlank()) Text(line, fontSize = 14.sp, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            dueSoon?.let { due ->
                val n = due.days
                val (text, color) = when (due.kind) {
                    CareDeviceDueKind.LATER -> (if (n == 1L) t("Change due in 1 day", "1 दिन में बदलनी है") else t("Change due in $n days", "$n दिन में बदलनी है")) to (if (n <= 7) SaharaSoftGold else Mint)
                    CareDeviceDueKind.TODAY -> t("Change due today", "आज बदलनी है") to Peach
                    CareDeviceDueKind.OVERDUE -> (if (n == 1L) t("Change was due 1 day ago", "1 दिन पहले बदलनी थी") else t("Change was due $n days ago", "$n दिन पहले बदलनी थी")) to Peach
                }
                Surface(shape = RoundedCornerShape(50), color = color, contentColor = DoctorCardInk, modifier = Modifier.testTag("device-due-${d.id}")) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (d.notes.isNotBlank()) Text(d.notes, fontSize = 14.sp, color = DoctorCardInk, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val checkText = when {
                check == null -> null
                check.signs.isEmpty() -> t("Checked today: all fine", "आज जाँचा: सब ठीक")
                else -> t("Checked today", "आज जाँचा") + ": " + check.signs.sortedBy { it.ordinal }.joinToString(", ") { t(it.en, it.hi) }
            }
            checkText?.let { Row(verticalAlignment = Alignment.CenterVertically) {
                val fine = check?.signs.isNullOrEmpty()
                Icon(if (fine) Icons.Default.CheckCircle else Icons.Default.WarningAmber, null, Modifier.size(18.dp), tint = if (fine) DoctorStatusGood else RustText); Spacer(Modifier.width(6.dp))
                Text(it, fontSize = 14.sp, color = if (fine) DoctorCardSub else RustText, fontWeight = FontWeight.Medium)
            } }
            OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("device-check-${d.id}"), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Checklist, null, Modifier.size(18.dp), tint = RustText); Spacer(Modifier.width(8.dp)); Text(if (check == null) t("Daily check", "रोज़ की जाँच") else t("Check again", "फिर से जाँचें"), color = RustText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Today's yes/no check. Ticking a sign means "yes"; saving with nothing ticked means all fine. */
@Composable private fun DevicesCheckDialog(d: CareDevice, initial: Set<CareDeviceSign>, onDismiss: () -> Unit, onSave: (Set<CareDeviceSign>) -> Unit) { val t = careText()
    var signs by remember(d.id) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Cream, modifier = Modifier.testTag("device-check-dialog"),
        title = { Text(t(d.type.en, d.type.hi), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Any of these today?", "आज इनमें से कुछ है?"), fontSize = 16.sp, color = DoctorCardInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CareDeviceSign.values().forEach { s -> DevicesChip(s in signs, t(s.en, s.hi), Modifier.testTag("device-sign-${s.name}")) { signs = if (s in signs) signs - s else signs + s } }
            }
            if (signs.isNotEmpty()) Row(Modifier.announce(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, null, Modifier.size(18.dp), tint = RustText); Spacer(Modifier.width(6.dp))
                Text(t("Tell your care team today", "आज ही अपनी देखभाल टीम को बताएँ"), fontWeight = FontWeight.Bold, color = RustText)
            }
        } },
        confirmButton = { Button(onClick = { onSave(signs) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("device-check-save")) { Text(if (signs.isEmpty()) t("All fine", "सब ठीक") else t("Save", "सहेजें"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें")) } })
}

@Composable private fun DevicesEditSheet(initial: CareDevice, existing: Boolean, hindi: Boolean, onDismiss: () -> Unit, onRemove: () -> Unit, onSave: (CareDevice) -> Unit) {
    val t = careText(); val context = LocalContext.current; val fmt = careDateFormat("d MMM yyyy", hindi)
    var type by remember { mutableStateOf(initial.type) }
    var inserted by remember { mutableStateOf(initial.inserted) }
    var changeDue by remember { mutableStateOf(initial.changeDue) }
    var centre by remember { mutableStateOf(initial.centre) }
    var notes by remember { mutableStateOf(initial.notes) }
    var error by remember { mutableStateOf<CareDeviceError?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    fun pick(start: LocalDate?, set: (LocalDate) -> Unit) { val d = start ?: LocalDate.now(); DatePickerDialog(context, { _, y, m, day -> set(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 32.dp).testTag("device-edit-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (existing) t("Edit tube or line", "नली या लाइन बदलें") else t("Add tube or line", "नली या लाइन जोड़ें"), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            Text(t("Type", "प्रकार"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CareDeviceType.values().forEach { v -> DevicesChip(type == v, t(v.en, v.hi), Modifier.testTag("device-type-${v.name}")) { type = v } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DevicesDateButton(t("Date put in", "लगाने की तारीख़"), inserted?.format(fmt), Modifier.weight(1f).testTag("device-inserted")) { pick(inserted) { inserted = it } }
                DevicesDateButton(t("Next change due", "अगली बार बदलना"), changeDue?.format(fmt), Modifier.weight(1f).testTag("device-change-due")) { pick(changeDue ?: inserted) { changeDue = it } }
            }
            OutlinedTextField(centre, { centre = it.take(120) }, Modifier.fillMaxWidth().testTag("device-centre"), label = { Text(t("Centre where it was put in", "कहाँ लगाई गई (अस्पताल)")) },
                leadingIcon = { Icon(Icons.Default.LocalHospital, null, Modifier.size(20.dp), tint = DoctorAccent) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedBorderColor = Rust, focusedLabelColor = RustText, cursorColor = Rust))
            OutlinedTextField(notes, { notes = it.take(500) }, Modifier.fillMaxWidth().testTag("device-notes"), label = { Text(t("Problems or notes (optional)", "दिक़्क़तें या नोट (ज़रूरी नहीं)")) },
                minLines = 2, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedBorderColor = Rust, focusedLabelColor = RustText, cursorColor = Rust))
            error?.let { Text(t(it.en, it.hi), color = RustText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.announce()) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) { Text(t("Cancel", "रद्द करें")) }
                Button(onClick = {
                    val bad = CareDeviceRules.error(inserted, changeDue, LocalDate.now())
                    if (bad != null) error = bad else onSave(initial.copy(type = type, inserted = inserted, changeDue = changeDue, centre = centre.trim(), notes = notes.trim()))
                }, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("device-save"), shape = RoundedCornerShape(14.dp)) { Text(t("Save", "सहेजें"), fontWeight = FontWeight.SemiBold) }
            }
            if (existing) TextButton(onClick = { confirmRemove = true }, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("device-remove")) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text(t("Remove", "हटाएँ"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, containerColor = Cream,
        title = { Text(t("Remove this tube or line?", "यह नली या लाइन हटाएँ?"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium) },
        confirmButton = { Button(onClick = { confirmRemove = false; onRemove() }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), modifier = Modifier.heightIn(min = 48.dp).testTag("device-remove-confirm")) { Text(t("Remove", "हटाएँ")) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें")) } })
}

@Composable private fun DevicesDateButton(label: String, value: String?, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, null, Modifier.size(16.dp), tint = DoctorAccent); Spacer(Modifier.width(6.dp))
                Text(value ?: "—", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1)
            }
        }
    }
}

@Composable private fun DevicesChip(selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null, shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = DoctorAccent),
        modifier = modifier.heightIn(min = 48.dp))
}
