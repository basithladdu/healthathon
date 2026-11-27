@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Medicine form pieces (clinical team and owner, 24 Nov 2026): tap, never type, where a tap will do. How often is a choice that fills
 * the dose times; strength is a number with a unit chip; the form is a chip. Everything is saved as the same text as before
 * (MedicineEntryText, or the free-text lines), so older phones and the doctor read it unchanged.
 */

@Composable private fun MedicineFieldTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading())
}

/** One chip colour set; the fill glides between white and the chosen green ([SaharaMotion.SELECT_MS]) instead of snapping. */
@Composable internal fun medicineChipColors(on: Boolean): SelectableChipColors {
    val fill by animateColorAsState(if (on) DoctorStatusGoodBg else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "medicine-chip")
    return FilterChipDefaults.filterChipColors(containerColor = fill, labelColor = DoctorCardInk,
        selectedContainerColor = fill, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = DoctorStatusGood)
}

/**
 * Tablet, Capsule, Syrup, Injection, Patch, Drops, Ointment, Inhaler, Nebuliser as chips ([MedicinePhotoFields.forms]); tapping the
 * chosen one clears it. An older saved form shows as its chip ("Oral solution" as Syrup, "SC injection" as Injection) without being
 * rewritten until a chip is tapped. Test tags: "[tagPrefix]-tablet", "[tagPrefix]-nebuliser", and so on.
 */
@Composable fun MedicineFormPicker(selected: String, onSelect: (String) -> Unit, enabled: Boolean = true, tagPrefix: String = "medicine-form") {
    val t = careText(); val tick = rememberSaharaTick()
    val current = MedicinePhotoFields.canonicalForm(selected)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SaharaDropdown(t("Form", "रूप"), MedicinePhotoFields.forms.firstOrNull { current.equals(it.first, ignoreCase = true) }?.let { t(it.first, it.second) } ?: current, enabled = enabled,
            options = MedicinePhotoFields.forms.map { (english, hindiName) -> SaharaDropdownOption(t(english, hindiName), "$tagPrefix-${english.lowercase()}", current.equals(english, ignoreCase = true)) },
            tag = "$tagPrefix-select", placeholder = t("Choose the form", "रूप चुनें"), onPick = { i -> tick(); onSelect(MedicinePhotoFields.forms[i].first) })
        if (current.isNotBlank() && MedicinePhotoFields.forms.none { it.first == current }) Text(t("Saved as: $current", "ऐसे सहेजा: $current"), fontSize = 14.sp, color = DoctorCardSub)
    }
}

/**
 * Once a day, Twice a day, Three times a day, Four times a day, As needed (SOS): one 56 dp chip each, never free text.
 * [writtenBefore] shows an older "How often" text under the chips until a choice replaces it. Test tags: "[tagPrefix]-once",
 * "[tagPrefix]-twice", "[tagPrefix]-three", "[tagPrefix]-four", "[tagPrefix]-as_needed".
 */
@Composable fun MedicineFrequencyPicker(selected: MedicineFrequency?, onSelect: (MedicineFrequency) -> Unit, enabled: Boolean = true, writtenBefore: String = "",
    tagPrefix: String = "medicine-frequency") {
    val t = careText(); val tick = rememberSaharaTick()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MedicineFieldTitle(t("How often", "कितनी बार"))
        var open by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = open && enabled, onExpandedChange = { if (enabled) open = it }) {
            OutlinedTextField(value = selected?.let { t(it.english, it.hindi) } ?: "", onValueChange = {}, readOnly = true, enabled = enabled, singleLine = true,
                placeholder = { Text(t("Choose how often", "चुनें कितनी बार"), fontSize = 16.sp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open && enabled) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().heightIn(min = 56.dp).testTag(tagPrefix))
            ExposedDropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }, containerColor = Paper) {
                MedicineFrequency.entries.forEach { frequency ->
                    val on = frequency == selected
                    DropdownMenuItem(text = { Text(t(frequency.english, frequency.hindi), fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = SaharaColors.Ink) },
                        trailingIcon = if (on) { { Icon(Icons.Default.Check, t("Selected", "चुना हुआ"), Modifier.size(18.dp), tint = RustText) } } else null,
                        onClick = { tick(); open = false; onSelect(frequency) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("medicine-frequency-option-${frequency.name.lowercase()}"))
                }
            }
        }
        if (writtenBefore.isNotBlank()) Text(t("Written before: $writtenBefore", "पहले लिखा था: $writtenBefore"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.testTag("$tagPrefix-written"))
    }
}

/**
 * How often, then the dose times it fills ([MedicineFrequencyRules.timesFor] with the usual 08:00 / 13:00 / 18:00 / 21:00), which can
 * still be changed by tapping ([CareTimeChips]). As needed has no times. [onChange] gets the "How often" text and the times together.
 */
@Composable fun MedicineHowOftenFields(howOften: String, times: List<String>, onChange: (howOften: String, times: List<String>) -> Unit, enabled: Boolean = true,
    tagPrefix: String = "medicine-frequency") {
    val t = careText()
    val chosen = MedicineFrequencyRules.fromText(howOften)
    val written = howOften.trim().takeIf { it.isNotEmpty() && (chosen == null || !chosen.english.equals(it, ignoreCase = true)) }.orEmpty()
    MedicineFrequencyPicker(chosen, { picked -> onChange(picked.english, MedicineFrequencyRules.timesFor(picked, times)) }, enabled, written, tagPrefix)
    // Choosing As needed (or leaving it) crossfades the dose times and the "no fixed times" line instead of popping one in for the other.
    SaharaStateSwitch(chosen == MedicineFrequency.AS_NEEDED, Modifier.fillMaxWidth(), label = "$tagPrefix-times") { asNeeded ->
        if (asNeeded) Text(t("No fixed times. On Today, tap ‘Took a dose now’ each time it is taken.", "कोई तय समय नहीं। हर बार लेने पर आज वाले पन्ने पर ‘अभी एक खुराक ली’ छुएँ।"),
            fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
        else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { DoseTimesField(times, { onChange(howOften, it) }, enabled = enabled) }
    }
}

/** Shown beside the number: mg, mcg, ml and g as written everywhere; units, drops and puffs in the reader's language. */
@Composable internal fun medicineUnitLabel(unit: String): String { val t = careText()
    return when (unit) { "units" -> t("units", "यूनिट"); "drops" -> t("drops", "बूँदें"); "puffs" -> t("puffs", "पफ़"); else -> unit }
}

/**
 * Strength as a number (number keypad) and one unit chip: mg, mcg, ml, g, units, %, drops, puffs. [onChange] gets the saved text ("500 mg").
 * An older strength that is not a single number and unit ("5 mg/5 ml") is shown as saved and kept until a new number is typed.
 * Test tags: "[tagPrefix]-number" and "[tagPrefix]-unit-mg" and so on.
 */
@Composable fun MedicineStrengthField(value: String, onChange: (String) -> Unit, enabled: Boolean = true, tagPrefix: String = "medicine-strength") {
    val t = careText(); val tick = rememberSaharaTick()
    val parts = MedicineStrength.parts(value)
    var number by remember { mutableStateOf(parts?.number.orEmpty()) }
    var unit by remember { mutableStateOf(parts?.unit?.takeIf { it.isNotBlank() } ?: MedicineStrength.units.first()) }
    // A strength filled from outside (a strip photo) shows in the field.
    LaunchedEffect(value) {
        val outside = MedicineStrength.parts(value)
        if (outside != null && MedicineStrength.text(number, unit) != value.trim()) { number = outside.number; if (outside.unit.isNotBlank()) unit = outside.unit }
    }
    val older = parts == null
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MedicineFieldTitle(t("Strength", "ताक़त"))
        if (older) Text(t("Saved as: $value", "ऐसे सहेजा: $value"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.testTag("$tagPrefix-written"))
        OutlinedTextField(value = number, onValueChange = { typed -> val clean = MedicineStrength.cleanNumber(typed); number = clean; onChange(MedicineStrength.text(clean, unit)) },
            enabled = enabled, singleLine = true, label = { Text(t("Number", "संख्या")) }, placeholder = { Text("500", color = DoctorCardFaint) },
            suffix = { Text(medicineUnitLabel(unit), color = DoctorCardSub) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedLabelColor = RustText, unfocusedLabelColor = DoctorCardSub),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("$tagPrefix-number"))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MedicineStrength.units.forEach { u ->
                val on = u == unit
                FilterChip(selected = on, enabled = enabled, onClick = { tick(); unit = u; if (number.isNotBlank() || !older) onChange(MedicineStrength.text(number, u)) },
                    label = { Text(medicineUnitLabel(u), fontSize = 15.sp, maxLines = 1, softWrap = false, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
                    shape = RoundedCornerShape(50), colors = medicineChipColors(on), modifier = Modifier.saharaSelectBounce(on, .08f).heightIn(min = 48.dp).testTag("$tagPrefix-unit-$u"))
            }
        }
    }
}

/**
 * For an older medicine saved as free text (CareUi's "Dose and times" field): How often and dose-time chips that write only their own
 * "Frequency: " and "Times: " lines, and the person's words as written, which stay editable. Nothing written before is lost.
 */
@Composable fun MedicineFreeTextFields(detail: String, onDetail: (String) -> Unit, enabled: Boolean = true) {
    val t = careText()
    val chosen = MedicineLegacyText.frequency(detail)
    val times = CareReminderRules.printedTimes(detail)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MedicineFrequencyPicker(chosen, { picked -> onDetail(MedicineEntryText.withTimes(MedicineLegacyText.withFrequency(detail, picked), MedicineFrequencyRules.timesFor(picked, times))) }, enabled)
        AnimatedVisibility(chosen != MedicineFrequency.AS_NEEDED, enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "medicine-free-text-times") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { DoseTimesField(times, { onDetail(MedicineEntryText.withTimes(detail, it)) }, enabled = enabled) }
        }
        CareField(MedicineLegacyText.words(detail), { onDetail(MedicineLegacyText.withWords(detail, it.take(3800))) }, t("Directions as written", "जैसे लिखा है, वैसे निर्देश"), minLines = 2)
    }
}

/**
 * The doctor's prescription row: strength, form, how often with its dose times, and directions, saved as the same labelled text the
 * family form writes ([MedicineEntryText]). A row saved earlier as free text keeps its words ([MedicineFreeTextFields]).
 */
@Composable fun MedicineDetailPickers(detail: String, onDetail: (String) -> Unit, enabled: Boolean = true) {
    val t = careText()
    val entry = MedicineEntryText.parse(detail)
    if (entry == null) { MedicineFreeTextFields(detail, onDetail, enabled); return }
    fun change(next: MedicineEntry) = onDetail(MedicineEntryText.detail(next))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MedicineStrengthField(entry.strength, { change(entry.copy(strength = it.take(80))) }, enabled)
        MedicineFormPicker(entry.form, { change(entry.copy(form = it)) }, enabled)
        MedicineHowOftenFields(entry.howOften, entry.times, { often, times -> change(entry.copy(howOften = often, times = times)) }, enabled)
        CareField(entry.directions, { change(entry.copy(directions = it.take(500))) }, t("Directions", "निर्देश"), minLines = 2)
    }
}
