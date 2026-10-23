@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable fun CareSymptomDiary(vm: CareModel, onBack: () -> Unit) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences = remember(vm.accountNamespace) { context.getSharedPreferences("sahara-symptom-language-${vm.accountNamespace}", 0) }
    var hindi by rememberSaveable(vm.accountNamespace) { mutableStateOf(preferences.getBoolean("hindi", false)) }
    var adding by rememberSaveable(selected) { mutableStateOf(false) }
    val person = people.firstOrNull { it.id == selected }
    val notes = entries.filter { it.personId == selected && SymptomNoteCodec.isStructured(it.detail) }
        .sortedByDescending { SymptomNoteCodec.decode(it.detail)?.let { n -> "${n.observedDate}T${n.observedTime}" }.orEmpty() }
    val t = { en: String, hi: String -> if (hindi) hi else en }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("symptom-diary"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
                Heading(t("Symptom tracker", "तबीयत के नोट"), 27)
            }
            Text(person?.name.orEmpty(), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "English", true to "हिन्दी").forEach { (value, label) ->
                    FilterChip(hindi == value, { hindi = value; preferences.edit().putBoolean("hindi", value).apply() }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        if (canEdit && person != null) item {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("symptom-add")) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(t("How are you feeling?", "तबीयत कैसी है?"))
            }
        }
        if (notes.isEmpty()) item { SurfaceCard(Mint) { Text(t("No symptom notes yet", "अभी तबीयत का कोई नोट नहीं है"), fontWeight = FontWeight.SemiBold) } }
        if (notes.isNotEmpty()) item {
            TextButton(onClick = {
                val text = listOf("Sahara — ${t("Personal symptom notes", "तबीयत के निजी नोट")}", person?.name.orEmpty(),
                    *notes.map { "${SymptomNoteCodec.decode(it.detail)?.let { n -> SymptomDiaryRules.symptomLabel(n.symptom, hindi) }.orEmpty()}\n${SymptomNoteCodec.readable(it.detail, hindi)}" }.toTypedArray()).joinToString("\n\n")
                open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), t("Share symptom notes", "तबीयत के नोट साझा करें")))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(t("Share notes", "नोट साझा करें")) }
        }
        items(notes, key = { it.id }) { SymptomNoteCard(it, vm, hindi) }
    }
    if (adding && person != null) SymptomNoteEditor(vm, hindi, onClose = { adding = false })
}

@Composable fun SymptomNoteCard(item: CareItem, vm: CareModel, hindi: Boolean = false) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var removing by rememberSaveable(item.id) { mutableStateOf(false) }
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val note = SymptomNoteCodec.decode(item.detail)
    SurfaceCard {
        Text(note?.let { SymptomDiaryRules.symptomLabel(it.symptom, hindi) } ?: item.title, fontWeight = FontWeight.SemiBold)
        Text(SymptomNoteCodec.readable(item.detail, hindi), style = MaterialTheme.typography.bodyMedium)
        val comparison = note?.let { SymptomDiaryRules.comparison(entries, item, it) }
        if (comparison != null) {
            val baseline = SymptomNoteCodec.decode(comparison.baseline.detail)!!
            val previous = SymptomNoteCodec.decode(comparison.previous.detail)!!
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("${t("First matching entry", "पहला समान नोट")}: ${baseline.observedDate} ${baseline.observedTime} · ${SymptomDiaryRules.ratingLabel(baseline.rating, hindi)}", style = MaterialTheme.typography.bodySmall)
            Text("${t("Previous matching entry", "पिछला समान नोट")}: ${previous.observedDate} ${previous.observedTime} · ${SymptomDiaryRules.ratingLabel(previous.rating, hindi)}", style = MaterialTheme.typography.bodySmall)
            Text("${t("Number difference", "अंकों का अंतर")}: ${if (comparison.difference > 0) "+" else ""}${comparison.difference}", style = MaterialTheme.typography.bodySmall)
        }
        if (canEdit && note != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editing = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-edit-${item.id}")) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Edit", "बदलें")) }
            TextButton(onClick = { removing = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Remove", "हटाएँ")) }
        }
    }
    if (editing) SymptomNoteEditor(vm, hindi, initial = item, onClose = { editing = false })
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text(t("Remove this note?", "यह नोट हटाएँ?")) }, text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { vm.remove(item); removing = false }, enabled = !busy) { Text(t("Remove", "हटाएँ")) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(t("Keep", "रखें")) } })
}

@Composable fun SymptomNoteEditor(vm: CareModel, hindi: Boolean, initial: CareItem? = null, onClose: () -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val entries by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val capturedPerson = rememberSaveable(initial?.id) { initial?.personId ?: selected }
    val expectedToken = rememberSaveable(initial?.id) { initial?.let(SymptomDiaryRules::editToken) }
    LaunchedEffect(initial?.id) { vm.actionError.value = null }
    LaunchedEffect(selected) { if (selected != capturedPerson) onClose() }
    SymptomNoteForm(personId = capturedPerson, personName = people.firstOrNull { it.id == capturedPerson }?.name.orEmpty(), entries = entries,
        hindi = hindi, initial = initial, busy = busy, canEdit = canEdit && selected == capturedPerson, actionError = actionError,
        onSave = { id, value -> vm.saveSymptomNote(capturedPerson, id, value, expectedToken, onClose) }, onClose = onClose)
}

/** The same native form can be checked with isolated callback fixtures, without touching accounts or real records. */
@Composable fun SymptomNoteForm(personId: String, personName: String, entries: List<CareItem>, hindi: Boolean,
    initial: CareItem? = null, busy: Boolean = false, canEdit: Boolean = true, actionError: String? = null,
    onSave: (String, SymptomNote) -> Unit, onClose: () -> Unit) {
    val original = remember(initial?.id) { initial?.let { SymptomNoteCodec.decode(it.detail) } }
    val id = rememberSaveable { initial?.id ?: newId() }
    val capturedPerson = rememberSaveable(id) { initial?.personId ?: personId }
    val context = LocalContext.current
    val t = { en: String, hi: String -> if (hindi) hi else en }
    var symptom by rememberSaveable(id) { mutableStateOf(original?.symptom.orEmpty()) }
    var otherSymptom by rememberSaveable(id) { mutableStateOf(original?.symptom?.let { it !in SymptomDiaryRules.symptoms } ?: false) }
    var rating by rememberSaveable(id) { mutableIntStateOf(if (original == null) -2 else original.rating ?: -1) }
    var observedDate by rememberSaveable(id) { mutableStateOf(original?.observedDate ?: LocalDate.now().toString()) }
    var observedTime by rememberSaveable(id) { mutableStateOf(original?.observedTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var recall by rememberSaveable(id) { mutableStateOf(original?.recall ?: "now") }
    var respondent by rememberSaveable(id) { mutableStateOf(original?.respondent.orEmpty()) }
    var author by rememberSaveable(id) { mutableStateOf(original?.author.orEmpty()) }
    var startedOn by rememberSaveable(id) { mutableStateOf(original?.startedOn.orEmpty()) }
    var note by rememberSaveable(id) { mutableStateOf(original?.note.orEmpty()) }
    var location by rememberSaveable(id) { mutableStateOf(original?.painLocation.orEmpty()) }
    var triggerValues by rememberSaveable(id) { mutableStateOf(original?.painTriggers?.joinToString("|").orEmpty()) }
    var change by rememberSaveable(id) { mutableStateOf(original?.selfReportedChange.orEmpty()) }
    var localError by rememberSaveable(id) { mutableStateOf("") }
    val value = SymptomNote(symptom, rating.takeIf { it >= 0 }, observedDate, observedTime, recall, respondent,
        if (respondent == "patient") personName else author, startedOn, note, location, triggerValues.split('|').filter(String::isNotBlank))
    val current = initial ?: CareItem(id = id, personId = capturedPerson, kind = "checkin", title = symptom)
    val comparison = if (rating >= 0 && respondent.isNotBlank() && value.author.isNotBlank()) SymptomDiaryRules.comparison(entries, current, value) else null
    // A changed question/reference cannot retain a prior subjective comparison.
    var referenceKey by rememberSaveable(id) { mutableStateOf(if (original != null) "${original.symptom}|${original.recall}|${original.respondent}|${original.author}|${original.painLocation}|${original.painTriggers.sorted()}|${original.observedDate}|${original.observedTime}|${original.comparedWithId}" else "") }
    LaunchedEffect(symptom, recall, respondent, value.author, location, triggerValues, observedDate, observedTime, comparison?.previous?.id) {
        val key = "$symptom|$recall|$respondent|${value.author}|$location|${value.painTriggers.sorted()}|$observedDate|$observedTime|${comparison?.previous?.id.orEmpty()}"
        if (referenceKey.isNotBlank() && referenceKey != key) change = ""
        referenceKey = key
    }
    LaunchedEffect(personId) { if (personId != capturedPerson) onClose() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(t(if (initial == null) "How are you feeling?" else "Edit symptom note", if (initial == null) "तबीयत कैसी है?" else "तबीयत का नोट बदलें"), 27)
            Text(personName, fontWeight = FontWeight.SemiBold)
            Text(t("What is bothering you?", "क्या परेशानी है?"), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SymptomDiaryRules.symptoms.forEach { (en, hi) -> FilterChip(if (en == "Other") otherSymptom else !otherSymptom && symptom == en, {
                    otherSymptom = en == "Other"; symptom = if (otherSymptom) "" else en; if (en != "Pain") { location = ""; triggerValues = "" }; rating = -2
                }, label = { Text(if (hindi) hi else en) }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-choice-$en")) }
            }
            if (otherSymptom) CareField(symptom, { symptom = it.take(80) }, t("Symptom name", "परेशानी का नाम"), singleLine = true)
            Text(t("Who reported this?", "किसने बताया?"), fontWeight = FontWeight.SemiBold)
            DiaryChoices(SymptomDiaryRules.respondents, respondent, hindi) { respondent = it }
            if (respondent.isNotBlank() && respondent != "patient") CareField(author, { author = it.take(120) }, t(if (respondent == "assisted-patient") "Written with help from" else "Observer's name", if (respondent == "assisted-patient") "लिखने में किसने मदद की" else "देखने वाले का नाम"), singleLine = true)
            Text(t("Time frame", "समय अवधि"), fontWeight = FontWeight.SemiBold)
            DiaryChoices(SymptomDiaryRules.recalls, recall, hindi) { recall = it }
            Text(t("How strong is it?", "कितनी परेशानी है?"), fontWeight = FontWeight.SemiBold)
            Text(t("0 = none · 10 = worst imaginable", "0 = बिलकुल नहीं · 10 = सबसे ज़्यादा"), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..10).forEach { number -> FilterChip(rating == number, { rating = number }, label = { Text(number.toString()) }, modifier = Modifier.defaultMinSize(minWidth = 44.dp).heightIn(min = 48.dp).testTag("symptom-rating-$number")) }
                FilterChip(rating == -1, { rating = -1; change = "" }, label = { Text(t("Not rated / unable to rate", "अंक नहीं बताया / बता नहीं सकते")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-unrated"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { val d = LocalDate.parse(observedDate); DatePickerDialog(context, { _, y, m, day -> observedDate = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(observedDate) }
                OutlinedButton(onClick = { val time = LocalTime.parse(observedTime); TimePickerDialog(context, { _, h, m -> observedTime = "%02d:%02d".format(h, m) }, time.hour, time.minute, true).show() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(observedTime) }
            }
            if (symptom == "Pain") {
                CareField(location, { location = it.take(100) }, t("Pain location (optional)", "दर्द की जगह (वैकल्पिक)"), singleLine = true)
                Text(t("When does it happen? (optional)", "कब होता है? (वैकल्पिक)"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { SymptomDiaryRules.triggers.forEach { (key, labels) ->
                    FilterChip(key in value.painTriggers, { val next = value.painTriggers.toMutableSet(); if (!next.add(key)) next.remove(key); triggerValues = next.sorted().joinToString("|") }, label = { Text(if (hindi) labels.second else labels.first) }, modifier = Modifier.heightIn(min = 48.dp))
                } }
            }
            if (comparison != null) {
                val previous = SymptomNoteCodec.decode(comparison.previous.detail)!!
                SurfaceCard(Mint) {
                    Text("${t("Previous matching note", "पिछला समान नोट")}: ${previous.observedDate} ${previous.observedTime} · ${previous.rating}/10", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("symptom-previous-reference"))
                    Text(t("Compared with that note (optional)", "उस नोट के मुकाबले (वैकल्पिक)"), fontWeight = FontWeight.SemiBold)
                    DiaryChoices(SymptomDiaryRules.changes, change, hindi) { change = if (change == it) "" else it }
                }
            } else if (rating >= 0 && respondent.isNotBlank()) Text(t("No earlier matching rating", "पहले का समान अंक नहीं है"), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { val d = runCatching { LocalDate.parse(startedOn) }.getOrDefault(LocalDate.parse(observedDate)); DatePickerDialog(context, { _, y, m, day -> startedOn = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.maxDate = LocalDate.parse(observedDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.show() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (startedOn.isBlank()) t("Started on… (optional)", "कब शुरू हुआ… (वैकल्पिक)") else "${t("Started", "शुरू हुआ")}: $startedOn") }
            if (startedOn.isNotBlank()) TextButton(onClick = { startedOn = "" }) { Text(t("Clear start date", "शुरू होने की तारीख हटाएँ")) }
            CareField(note, { note = it.take(500) }, t("Anything else? (optional)", "कुछ और? (वैकल्पिक)"), minLines = 2)
            (localError.takeIf { it.isNotBlank() } ?: actionError)?.let { Text(if (hindi && it == SymptomDiaryRules.SAVE_CONFLICT) "यह नोट बदल गया है या हटा दिया गया है। आपके जवाब यहीं हैं। सहेजने से पहले यह फ़ॉर्म बंद करके मौजूदा नोट फिर खोलें।" else if (hindi) "नोट सहेजा नहीं गया। जवाब यहीं हैं। तारीख, नाम और चुने हुए जवाब जाँचें।" else it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val saved = value.copy(selfReportedChange = change.takeIf { comparison != null }.orEmpty(), comparedWithId = if (change.isNotBlank()) comparison?.previous?.id.orEmpty() else "")
                localError = if (rating == -2) t("Choose a rating or Not rated.", "अंक चुनें या अंक नहीं बताया चुनें।") else SymptomDiaryRules.error(saved).orEmpty()
                if (localError.isBlank()) onSave(id, saved)
            }, enabled = !busy && canEdit && personId == capturedPerson, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("symptom-save")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save note", "नोट सहेजें")) }
        }
    }
}

@Composable private fun DiaryChoices(options: Map<String, Pair<String, String>>, selected: String, hindi: Boolean, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.forEach { (key, label) -> FilterChip(selected == key, { onSelect(key) }, label = { Text(if (hindi) label.second else label.first) }, modifier = Modifier.heightIn(min = 48.dp).testTag("diary-choice-$key")) } }
}
