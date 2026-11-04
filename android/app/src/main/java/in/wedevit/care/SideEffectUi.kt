@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** "Side effect?" on a medicine: a quick note of what happened, kept for the doctor's next visit. It records only; it gives no advice. */
@Composable fun SideEffectSheet(medicine: CareItem, vm: CareModel, onDismiss: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val busy by vm.busy.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var pickedKeys by rememberSaveable(medicine.id) { mutableStateOf("") }
    val picked = pickedKeys.split(",").filter { it.isNotBlank() }
    var other by rememberSaveable(medicine.id) { mutableStateOf("") }
    var severity by rememberSaveable(medicine.id) { mutableStateOf("") }
    var whenKey by rememberSaveable(medicine.id) { mutableStateOf("now") }
    var stopped by rememberSaveable(medicine.id) { mutableStateOf(false) }
    var problem by remember(medicine.id) { mutableStateOf<String?>(null) }
    val capturedAccount = remember(medicine.id) { vm.accountNamespace }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp).testTag("side-effect-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Heading(t("Side effect?", "कोई दुष्प्रभाव?"), 24) }
                MedicineInfoButton(t("Side effect?", "कोई दुष्प्रभाव?"), t("Write down what happened so the doctor can talk about it at the next visit. This only records it; it is not advice.", "जो हुआ उसे लिख लें ताकि अगली मुलाक़ात में डॉक्टर इस पर बात कर सकें। यह सिर्फ़ दर्ज करता है, सलाह नहीं है।") + "\n\n" +
                    t("The choices are a short general list, not this medicine's own side effects. Use Other for anything else.", "ये विकल्प एक छोटी आम सूची हैं, इस दवा के अपने दुष्प्रभाव नहीं। बाक़ी सब के लिए “अन्य” चुनें।") + "\n\n" +
                    (if (vm.cloud != null) t("Saved in your account. Family and doctors you share with can see it.", "आपके खाते में सेव। जिनके साथ आपने साझा किया, वे देख सकते हैं।") else t("Saved only on this phone. Sign in to share it with family and the doctor.", "सिर्फ़ इस फ़ोन पर सेव। परिवार और डॉक्टर से साझा करने के लिए साइन इन करें।")), "side-effect-info")
            }
            Surface(shape = RoundedCornerShape(16.dp), color = SaharaSoftBlue, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(careIcon("medicine"), null, Modifier.size(22.dp), tint = DoctorAccent); Spacer(Modifier.width(10.dp))
                    Text(medicine.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                }
            }
            SideEffectStep(1, t("What happened?", "क्या हुआ?")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SideEffectRules.effects.keys.forEach { key -> PickChip(key in picked, SideEffectRules.effectLabel(key, hindi), Mint, Modifier.testTag("side-effect-chip-$key")) { pickedKeys = (if (key in picked) picked - key else picked + key).joinToString(",") } }
                }
                CareField(other, { other = it.take(SideEffectRules.MAX_OTHER) }, t("In your own words (optional)", "अपने शब्दों में (ज़रूरी नहीं)"), minLines = 2)
            }
            SideEffectStep(2, t("How bad was it?", "कितना ज़्यादा था?")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SideEffectRules.severities.keys.forEachIndexed { i, key -> PickChip(severity == key, SideEffectRules.severityLabel(key, hindi), SeverityTints.getOrElse(i) { Mint }, Modifier.weight(1f).testTag("side-effect-severity-$key")) { severity = key } }
                }
            }
            SideEffectStep(3, t("When?", "कब?")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SideEffectRules.whenOptions.forEach { (key, label) -> PickChip(whenKey == key, careTr(hindi, label.first, label.second), SaharaSoftBlue, Modifier.testTag("side-effect-when-$key")) { whenKey = key } }
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().toggleable(stopped, role = Role.Switch) { stopped = it }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("I stopped taking it", "मैंने इसे लेना बंद कर दिया"), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f))
                    Switch(checked = stopped, onCheckedChange = null)
                }
            }
            problem?.let { Text(it, color = RustText, fontSize = 14.sp, modifier = Modifier.announce()) }
            if (!canEdit) Text(t("You can read these records but not change them.", "आप ये रिकॉर्ड देख सकते हैं, बदल नहीं सकते।"), fontSize = 13.sp, color = DoctorCardSub)
            Button(onClick = {
                val now = System.currentTimeMillis()
                val note = SideEffectNote(medicine.id, medicine.title, SideEffectRules.effects.keys.filter { it in picked }, other.trim(), severity, SideEffectRules.whenAt(whenKey, now), stopped, SideEffectRules.reporterFor(vm.viewRole.value))
                val err = SideEffectRules.error(note, now)
                if (err != null) problem = err
                else if (canEditCareContext(vm, medicine.personId, capturedAccount)) { problem = null; vm.saveItem("journal", SideEffectRules.title(note), SideEffectCodec.encode(note), done = onDismiss) }
            }, enabled = !busy && canEdit, colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("side-effect-save")) { Text(t("Save side effect", "दुष्प्रभाव सहेजें")) }
        }
    }
}

/** Calm green, amber and red for Mild, Moderate and Severe, in SideEffectRules.severities order. */
private val SeverityTints = listOf(androidx.compose.ui.graphics.Color(0xFFDCEFE1), androidx.compose.ui.graphics.Color(0xFFFBE7C6), androidx.compose.ui.graphics.Color(0xFFF7D6D0))

/** One numbered step of the side-effect form on its own card. */
@Composable private fun SideEffectStep(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = DoctorAccent, modifier = Modifier.size(26.dp)) { Box(contentAlignment = Alignment.Center) { Text("$number", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
                Spacer(Modifier.width(10.dp))
                Text(title, fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            }
            content()
        }
    }
}

/** A choice chip that shows a tick and a filled colour when picked, so the choice is clear at a glance. */
@Composable private fun PickChip(selected: Boolean, label: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) },
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null, shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(containerColor = Cream, labelColor = DoctorCardInk, selectedContainerColor = tint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = DoctorAccent),
        modifier = modifier.heightIn(min = 48.dp))
}

/** One side-effect note as a card body: effect, severity, when, who reported it, and whether it was discussed. */
@Composable fun SideEffectSummary(entry: SideEffectEntry, showMedicine: Boolean, trailing: (@Composable ColumnScope.() -> Unit)? = null) {
    val t = careText(); val hindi = careHindi(); val n = entry.note
    Column(Modifier.fillMaxWidth().testTag("side-effect-${entry.item.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showMedicine) Text(n.medicineName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        Text(SideEffectRules.effectsText(n, hindi), fontSize = 15.sp, color = DoctorCardInk)
        Text(SideEffectRules.severityLabel(n.severity, hindi) + " · " + date(n.happenedAt) + (if (n.stoppedTaking) " · " + t("They stopped taking it.", "उन्होंने इसे लेना बंद कर दिया।") else ""), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (n.severity == "severe") RustText else DoctorCardSub)
        Text(t("Reported by ", "बताने वाले: ") + n.reporter.ifBlank { t("Not recorded", "दर्ज नहीं") } + " · " + (if (n.discussedAt > 0) t("Discussed ", "बात हो चुकी ") + date(n.discussedAt) + n.discussedBy.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() else t("Not discussed yet", "अभी बात नहीं हुई")), fontSize = 13.sp, color = DoctorCardSub)
        trailing?.invoke(this)
    }
}

@Composable private fun MarkDiscussedButton(entry: SideEffectEntry, vm: CareModel, who: String) {
    val t = careText(); val busy by vm.busy.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val capturedAccount = remember(entry.item.id) { vm.accountNamespace }
    if (entry.note.discussedAt == 0L && canEdit) TextButton(onClick = { if (canEditCareContext(vm, entry.item.personId, capturedAccount)) vm.updateItem(SideEffectLog.markDiscussed(entry, who, System.currentTimeMillis())) {} }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("side-effect-discussed-${entry.item.id}")) { Text(t("Mark as discussed", "बात हो गई, चिह्नित करें"), color = DoctorAccent) }
}

/** Under one medicine's History: every side effect noted for it, newest first. */
fun androidx.compose.foundation.lazy.LazyListScope.medicineSideEffectItems(entries: List<SideEffectEntry>, vm: CareModel) {
    if (entries.isEmpty()) return
    item(key = "side-effects-heading") { val t = careText(); Text(t("Side effects noted", "दर्ज दुष्प्रभाव"), fontWeight = FontWeight.Medium, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading().padding(top = 6.dp)) }
    items(entries.size, key = { "side-effect-${entries[it].item.id}" }) { i -> val e = entries[i]; SurfaceCard(Paper) { SideEffectSummary(e, showMedicine = false) { MarkDiscussedButton(e, vm, SideEffectRules.reporterFor(vm.viewRole.value)) } } }
}

/** Patient/family Medicines section: unresolved side effects since the last doctor visit, ready to raise with the doctor. */
@Composable fun SideEffectsToRaiseCard(vm: CareModel) {
    val t = careText()
    val all by vm.allItems.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle()
    val now = remember(all) { System.currentTimeMillis() }
    val entries = remember(all, person) { SideEffectLog.entries(all, person) }
    val last = remember(all, person, now) { SideEffectLog.lastVisitAt(all, person, now) }
    val open = remember(entries, last) { SideEffectLog.toRaise(entries, last) }
    if (open.isEmpty()) return
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("side-effects-to-raise")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Side effects to raise at the next visit", "अगली मुलाक़ात में बताने वाले दुष्प्रभाव"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            Text(if (last > 0) t("Noted since the last doctor visit on ", "पिछली डॉक्टर मुलाक़ात के बाद दर्ज, ") + date(last) else t("Noted so far. No doctor visit is recorded yet.", "अब तक दर्ज। अभी कोई डॉक्टर मुलाक़ात दर्ज नहीं है।"), fontSize = 13.sp, color = DoctorCardSub)
            open.forEach { e -> key(e.item.id) { SideEffectSummary(e, showMedicine = true) { MarkDiscussedButton(e, vm, SideEffectRules.reporterFor(vm.viewRole.value)) } } }
        }
    }
}

/** A side-effect note in the Journal list. */
@Composable fun SideEffectEntryCard(item: CareItem, vm: CareModel) {
    val t = careText(); val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    var delete by rememberSaveable(item.id) { mutableStateOf(false) }
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val note = remember(item.detail) { SideEffectCodec.decode(item.detail) }
    SurfaceCard(Paper) {
        if (note == null) Text(t("This side-effect note could not be read. The original record is retained.", "यह नोट पढ़ा नहीं जा सका। मूल रिकॉर्ड सुरक्षित है।"), color = DoctorCardSub)
        else {
            Text(t("Side effect", "दुष्प्रभाव"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DoctorCardFaint)
            SideEffectSummary(SideEffectEntry(item, note), showMedicine = true) { MarkDiscussedButton(SideEffectEntry(item, note), vm, SideEffectRules.reporterFor(vm.viewRole.value)) }
        }
        if (canEdit) TextButton(onClick = { delete = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-remove-${item.id}")) { Text(t("Remove", "हटाएँ"), color = RustText) }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(t("Remove this entry?", "यह जानकारी हटाएँ?")) }, text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.remove(item); delete = false }, enabled = !busy && canEdit) { Text(t("Remove", "हटाएँ")) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(t("Keep", "रखें")) } })
}

/** The doctor's view: side effects the patient or family reported since the last visit, with a Discussed button. */
@Composable fun DoctorSideEffectsSection(vm: CareModel, personId: String) {
    val t = careText()
    val all by vm.allItems.collectAsStateWithLifecycle()
    val now = remember(all) { System.currentTimeMillis() }
    val entries = remember(all, personId) { SideEffectLog.entries(all, personId) }
    val last = remember(all, personId, now) { SideEffectLog.lastVisitAt(all, personId, now) }
    val recent = remember(entries, last) { SideEffectLog.sinceVisit(entries, last).sortedBy { it.note.discussedAt > 0 } }
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("doctor-side-effects")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Side effects reported since last visit", "पिछली मुलाक़ात के बाद बताए गए दुष्प्रभाव"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
                MedicineInfoButton(t("Side effects reported since last visit", "पिछली मुलाक़ात के बाद बताए गए दुष्प्रभाव"), (if (last > 0) t("Since the last recorded visit on ", "पिछली दर्ज मुलाक़ात के बाद, ") + date(last) else t("No visit is recorded yet, so everything reported is shown.", "अभी कोई मुलाक़ात दर्ज नहीं, इसलिए बताई गई हर बात दिख रही है।")) + "\n\n" + t("These are the patient's or family's own notes, not a clinical assessment.", "ये मरीज़ या परिवार के अपने नोट हैं, डॉक्टरी जाँच नहीं।") + "\n\n" +
                    t("The choices are a short general list, not this medicine's own side effects. Use Other for anything else.", "ये विकल्प एक छोटी आम सूची हैं, इस दवा के अपने दुष्प्रभाव नहीं। बाक़ी सब के लिए “अन्य” चुनें।"), "doctor-side-effect-info")
            }
            // Where the window starts, and what these notes are, sit behind the info icon (3 Nov meeting: less text on screen).
            if (recent.isEmpty()) Text(t("Nothing reported since the last visit.", "पिछली मुलाक़ात के बाद कुछ नहीं बताया गया।"), fontSize = 15.sp, color = DoctorCardInk)
            recent.forEach { e -> key(e.item.id) { SideEffectSummary(e, showMedicine = true) { MarkDiscussedButton2(e, vm) } } }
        }
    }
}

@Composable private fun MarkDiscussedButton2(entry: SideEffectEntry, vm: CareModel) {
    val t = careText(); val busy by vm.busy.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val capturedAccount = remember(entry.item.id) { vm.accountNamespace }
    if (entry.note.discussedAt == 0L && canEdit) TextButton(onClick = { if (canEditCareContext(vm, entry.item.personId, capturedAccount)) vm.updateItem(SideEffectLog.markDiscussed(entry, "Doctor", System.currentTimeMillis())) {} }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-side-effect-discussed-${entry.item.id}")) { Text(t("Discussed", "बात हुई"), color = DoctorAccent) }
}
