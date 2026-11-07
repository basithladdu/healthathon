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
    SaharaScreenSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).saharaContentSize().testTag("side-effect-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(t("Side effect?", "कोई दुष्प्रभाव?"), 24)
            // Which medicine, as one pill under the title.
            Surface(shape = RoundedCornerShape(50), color = SaharaSoftBlue) {
                Row(Modifier.heightIn(min = 40.dp).padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(careIcon("medicine"), null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(8.dp))
                    Text(medicine.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 2)
                }
            }
            SideEffectStep(1, t("What happened?", "क्या हुआ?")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SideEffectRules.effects.keys.forEach { key -> PickChip(key in picked, SideEffectRules.effectLabel(key, hindi), DoctorAccent, Modifier.testTag("side-effect-chip-$key")) { pickedKeys = (if (key in picked) picked - key else picked + key).joinToString(",") } }
                }
                CareField(other, { other = it.take(SideEffectRules.MAX_OTHER) }, t("Details (optional)", "विवरण (चाहें तो)"), minLines = 2)
            }
            SideEffectStep(2, t("How bad was it?", "कितना ज़्यादा था?")) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SideEffectRules.severities.keys.forEachIndexed { i, key -> PickChip(severity == key, SideEffectRules.severityLabel(key, hindi), SeverityTints.getOrElse(i) { DoctorAccent }, Modifier.testTag("side-effect-severity-$key")) { severity = key } }
                }
            }
            SideEffectStep(3, t("When?", "कब?")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SideEffectRules.whenOptions.forEach { (key, label) -> PickChip(whenKey == key, careTr(hindi, label.first, label.second), DoctorAccent, Modifier.testTag("side-effect-when-$key")) { whenKey = key } }
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(stopped, role = Role.Switch) { stopped = it }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("I stopped taking it", "मैंने इसे लेना बंद कर दिया"), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f))
                    Switch(checked = stopped, onCheckedChange = null)
                }
            }
            problem?.let { Text(it, color = RustText, fontSize = 14.sp, modifier = Modifier.announce()) }
            Button(onClick = {
                val now = System.currentTimeMillis()
                val note = SideEffectNote(medicine.id, medicine.title, SideEffectRules.effects.keys.filter { it in picked }, other.trim(), severity, SideEffectRules.whenAt(whenKey, now), stopped, SideEffectRules.reporterFor(vm.viewRole.value))
                val err = SideEffectRules.error(note, now)
                if (err != null) problem = err
                else if (canEditCareContext(vm, medicine.personId, capturedAccount)) { problem = null; vm.saveItem("journal", SideEffectRules.title(note), SideEffectCodec.encode(note), done = onDismiss) }
            }, enabled = !busy && canEdit, colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 54.dp).testTag("side-effect-save")) {
                if (busy) { CircularProgressIndicator(Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save", "सहेजें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Solid green, amber and red for Mild, Moderate and Severe (the chart band colours), in SideEffectRules.severities order. */
private val SeverityTints = listOf(ChartBands.Mild, ChartBands.Moderate, ChartBands.Severe)

/** One numbered step of the side-effect form on its own card. */
@Composable private fun SideEffectStep(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = DoctorAccent, modifier = Modifier.size(28.dp)) { Box(contentAlignment = Alignment.Center) { Text("$number", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) } }
                Spacer(Modifier.width(10.dp))
                Text(title, fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            }
            content()
        }
    }
}

/** A choice chip: strong outline when free, a solid fill with white text and a tick when picked, so it reads as a button. */
@Composable private fun PickChip(selected: Boolean, label: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val ink = readableOn(tint)
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, style = SymptomWords) },
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null, shape = RoundedCornerShape(50),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = DoctorCardSub.copy(alpha = .5f), selectedBorderColor = tint, borderWidth = 1.5.dp, selectedBorderWidth = 1.5.dp),
        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = tint, selectedLabelColor = ink, selectedLeadingIconColor = ink),
        modifier = modifier.heightIn(min = 48.dp))
}

/** One side-effect note as a card body: effect, severity, when, who reported it, and whether it was discussed. */
@Composable fun SideEffectSummary(entry: SideEffectEntry, showMedicine: Boolean, trailing: (@Composable ColumnScope.() -> Unit)? = null) {
    val t = careText(); val hindi = careHindi(); val n = entry.note
    Column(Modifier.fillMaxWidth().testTag("side-effect-${entry.item.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showMedicine) Text(n.medicineName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        Text(SideEffectRules.effectsText(n, hindi), fontSize = 16.sp, color = DoctorCardInk)
        // Severity as a status chip, then the date (and whether they stopped) on the same line.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (fg, bg) = when (n.severity) { "severe" -> DoctorStatusRest to DoctorStatusRestBg; "moderate" -> DoctorStatusWarn to DoctorStatusWarnBg; else -> DoctorStatusGood to DoctorStatusGoodBg }
            val severity = SideEffectRules.severityLabel(n.severity, hindi)
            if (severity.isNotBlank()) { Surface(shape = RoundedCornerShape(50), color = bg) { Text(severity, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1) }; Spacer(Modifier.width(8.dp)) }
            Text(date(n.happenedAt) + (if (n.stoppedTaking) " · " + t("Stopped", "बंद किया") else ""), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (n.severity == "severe") RustText else DoctorCardSub, modifier = Modifier.weight(1f))
        }
        Text(t("Reported by ", "बताने वाले: ") + n.reporter.ifBlank { t("Not recorded", "दर्ज नहीं") } + " · " + (if (n.discussedAt > 0) t("Discussed ", "बात हो चुकी ") + date(n.discussedAt) + n.discussedBy.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() else t("Not discussed yet", "अभी बात नहीं हुई")), fontSize = 14.sp, color = DoctorCardSub)
        trailing?.invoke(this)
    }
}

@Composable private fun MarkDiscussedButton(entry: SideEffectEntry, vm: CareModel, who: String) {
    val t = careText(); val busy by vm.busy.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val capturedAccount = remember(entry.item.id) { vm.accountNamespace }
    if (entry.note.discussedAt == 0L && canEdit) TextButton(onClick = { if (canEditCareContext(vm, entry.item.personId, capturedAccount)) vm.updateItem(SideEffectLog.markDiscussed(entry, who, System.currentTimeMillis())) {} }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("side-effect-discussed-${entry.item.id}")) { Text(t("Mark discussed", "बात हो गई"), color = DoctorAccent) }
}

/** Under one medicine's History: every side effect noted for it, newest first. */
fun androidx.compose.foundation.lazy.LazyListScope.medicineSideEffectItems(entries: List<SideEffectEntry>, vm: CareModel) {
    if (entries.isEmpty()) return
    item(key = "side-effects-heading") { val t = careText(); Text(t("Side effects noted", "दर्ज दुष्प्रभाव"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading().padding(top = 12.dp)) }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Side effects to raise", "बताने वाले दुष्प्रभाव"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            // One surface, notes separated by dividers (no card inside a card).
            open.forEachIndexed { i, e -> key(e.item.id) { if (i > 0) HorizontalDivider(color = DoctorHairline); SideEffectSummary(e, showMedicine = true) { MarkDiscussedButton(e, vm, SideEffectRules.reporterFor(vm.viewRole.value)) } } }
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
        if (note == null) Text(t("Couldn't read this note.", "यह नोट पढ़ा नहीं जा सका।"), color = DoctorCardSub)
        else {
            Surface(shape = RoundedCornerShape(50), color = SaharaSoftBlue, modifier = Modifier.padding(bottom = 4.dp)) { Text(t("Side effect", "दुष्प्रभाव"), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk) }
            SideEffectSummary(SideEffectEntry(item, note), showMedicine = true) { MarkDiscussedButton(SideEffectEntry(item, note), vm, SideEffectRules.reporterFor(vm.viewRole.value)) }
        }
        if (canEdit) TextButton(onClick = { delete = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-remove-${item.id}")) { Text(t("Remove", "हटाएँ"), color = RustText) }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(t("Remove this entry?", "यह जानकारी हटाएँ?")) }, text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.remove(item); delete = false }, enabled = !busy && canEdit) { Text(t("Remove", "हटाएँ"), color = RustText, fontWeight = FontWeight.SemiBold) } },
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
            Text(t("Side effects", "दुष्प्रभाव"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            if (recent.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = DoctorStatusGood); Spacer(Modifier.width(8.dp)); Text(t("Nothing since last visit", "पिछली मुलाक़ात के बाद कुछ नहीं"), fontSize = 16.sp, color = DoctorCardInk) }
            recent.forEachIndexed { i, e -> key(e.item.id) { if (i > 0) HorizontalDivider(color = DoctorHairline); SideEffectSummary(e, showMedicine = true) { MarkDiscussedButton2(e, vm) } } }
        }
    }
}

@Composable private fun MarkDiscussedButton2(entry: SideEffectEntry, vm: CareModel) {
    val t = careText(); val busy by vm.busy.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val capturedAccount = remember(entry.item.id) { vm.accountNamespace }
    if (entry.note.discussedAt == 0L && canEdit) TextButton(onClick = { if (canEditCareContext(vm, entry.item.personId, capturedAccount)) vm.updateItem(SideEffectLog.markDiscussed(entry, "Doctor", System.currentTimeMillis())) {} }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-side-effect-discussed-${entry.item.id}")) { Text(t("Discussed", "बात हुई"), color = DoctorAccent) }
}
