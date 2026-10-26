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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
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
    var showingPainHistory by rememberSaveable(selected) { mutableStateOf(false) }
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
        if (person != null && PainHistoryRules.entries(entries, person.id).isNotEmpty()) item {
            OutlinedButton(onClick = { showingPainHistory = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("symptom-pain-history")) { Text(t("Pain history on body", "शरीर पर दर्द का इतिहास")) }
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
    if (showingPainHistory && person != null) PainBodyHistorySheet(entries, person.id, hindi, onClose = { showingPainHistory = false })
}

@Composable fun SymptomNoteCard(item: CareItem, vm: CareModel, hindi: Boolean = false) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var followingUp by rememberSaveable(item.id) { mutableStateOf(false) }
    var showingTrend by rememberSaveable(item.id) { mutableStateOf(false) }
    var showingPainHistory by rememberSaveable(item.id) { mutableStateOf(false) }
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
        if (canEdit && note != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { followingUp = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-followup-${item.id}")) { Text(t("Update rating", "नया अंक लिखें")) }
            TextButton(onClick = { editing = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-edit-${item.id}")) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Edit", "बदलें")) }
            TextButton(onClick = { removing = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Remove", "हटाएँ")) }
        }
        if (note?.symptom?.trim()?.equals("Pain", true) == true) TextButton(onClick = { showingPainHistory = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-pain-history-${item.id}")) { Text(t("View pain on body", "शरीर पर दर्द देखें")) }
        if (note != null) TextButton(onClick = { showingTrend = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-trend-${item.id}")) { Text(t("View trend", "अंकों का बदलाव देखें")) }
    }
    if (editing) SymptomNoteEditor(vm, hindi, initial = item, onClose = { editing = false })
    if (followingUp) SymptomNoteEditor(vm, hindi, followup = item, onClose = { followingUp = false })
    if (showingTrend && note != null) SymptomTrendSheet(entries, item, note, hindi, onClose = { showingTrend = false })
    if (showingPainHistory && note != null) PainBodyHistorySheet(entries, item.personId, hindi, initialId = item.id, onClose = { showingPainHistory = false })
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text(t("Remove this note?", "यह नोट हटाएँ?")) }, text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { vm.remove(item); removing = false }, enabled = !busy) { Text(t("Remove", "हटाएँ")) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(t("Keep", "रखें")) } })
}

@Composable fun SymptomNoteEditor(vm: CareModel, hindi: Boolean, initial: CareItem? = null, followup: CareItem? = null, onClose: () -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val entries by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val capturedPerson = rememberSaveable(initial?.id, followup?.id) { initial?.personId ?: followup?.personId ?: selected }
    val expectedToken by rememberSaveable(initial?.id) { mutableStateOf(initial?.let(SymptomDiaryRules::editToken)) }
    LaunchedEffect(initial?.id) { vm.actionError.value = null }
    LaunchedEffect(selected) { if (selected != capturedPerson) onClose() }
    SymptomNoteForm(personId = capturedPerson, personName = people.firstOrNull { it.id == capturedPerson }?.name.orEmpty(), entries = entries,
        hindi = hindi, initial = initial, followup = followup, busy = busy, canEdit = canEdit && selected == capturedPerson, actionError = actionError,
        onSave = { id, value -> vm.saveSymptomNote(capturedPerson, id, value, expectedToken, onClose) }, onClose = onClose)
}

/** The same native form can be checked with isolated callback fixtures, without touching accounts or real records. */
@Composable fun SymptomNoteForm(personId: String, personName: String, entries: List<CareItem>, hindi: Boolean,
    initial: CareItem? = null, busy: Boolean = false, canEdit: Boolean = true, actionError: String? = null,
    followup: CareItem? = null, onSave: (String, SymptomNote) -> Unit, onClose: () -> Unit) {
    val original = remember(initial?.id) { initial?.let { SymptomNoteCodec.decode(it.detail) } }
    val prior = remember(followup?.id) { followup?.let { SymptomNoteCodec.decode(it.detail) } }
    val seed = remember(initial?.id, followup?.id) { original ?: prior?.let(SymptomDiaryRules::followUp) }
    val id = rememberSaveable { initial?.id ?: newId() }
    val capturedPerson = rememberSaveable(id) { initial?.personId ?: personId }
    val context = LocalContext.current
    val t = { en: String, hi: String -> if (hindi) hi else en }
    var fullDetails by rememberSaveable(id) { mutableStateOf(prior == null || original != null) }
    var symptom by rememberSaveable(id) { mutableStateOf(seed?.symptom.orEmpty()) }
    var otherSymptom by rememberSaveable(id) { mutableStateOf(seed?.symptom?.let { it !in SymptomDiaryRules.symptoms } ?: false) }
    var scale by rememberSaveable(id) { mutableStateOf(seed?.scale ?: SymptomDiaryRules.PERSONAL_SCALE) }
    var rating by rememberSaveable(id) { mutableIntStateOf(if (original == null) -2 else original.rating ?: -1) }
    var observedDate by rememberSaveable(id) { mutableStateOf(seed?.observedDate ?: LocalDate.now().toString()) }
    var observedTime by rememberSaveable(id) { mutableStateOf(seed?.observedTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var recall by rememberSaveable(id) { mutableStateOf(seed?.recall ?: "now") }
    var respondent by rememberSaveable(id) { mutableStateOf(seed?.respondent.orEmpty()) }
    var author by rememberSaveable(id) { mutableStateOf(seed?.author.orEmpty()) }
    var startedOn by rememberSaveable(id) { mutableStateOf(seed?.startedOn.orEmpty()) }
    var note by rememberSaveable(id) { mutableStateOf(original?.note.orEmpty()) }
    var location by rememberSaveable(id) { mutableStateOf(seed?.painLocation.orEmpty()) }
    var bodySites by rememberSaveable(id) { mutableStateOf(seed?.painBodySites?.joinToString("|").orEmpty()) }
    var bodyPoints by rememberSaveable(id) { mutableStateOf(PainBodyMapRules.encodePoints(seed?.painBodyPoints.orEmpty()).toString()) }
    var triggerValues by rememberSaveable(id) { mutableStateOf(seed?.painTriggers?.joinToString("|").orEmpty()) }
    var change by rememberSaveable(id) { mutableStateOf(original?.selfReportedChange.orEmpty()) }
    var localError by rememberSaveable(id) { mutableStateOf("") }
    val value = SymptomNote(symptom, rating.takeIf { it >= 0 }, observedDate, observedTime, recall, respondent,
        if (respondent == "patient") personName else author, startedOn, note, location, triggerValues.split('|').filter(String::isNotBlank), scale = scale,
        painBodySites = bodySites.split('|').filter(String::isNotBlank), painBodyPoints = PainBodyMapRules.decodePoints(org.json.JSONArray(bodyPoints)),
        painBodyModel = if (bodyPoints != "[]") PainBodyMapRules.MODEL else "")
    val current = initial ?: CareItem(id = id, personId = capturedPerson, kind = "checkin", title = symptom)
    val comparison = if (rating >= 0 && respondent.isNotBlank() && value.author.isNotBlank()) SymptomDiaryRules.comparison(entries, current, value) else null
    // A changed question/reference cannot retain a prior subjective comparison.
    var referenceKey by rememberSaveable(id) { mutableStateOf(if (original != null) "${original.symptom}|${original.scale}|${original.recall}|${original.respondent}|${original.author}|${original.painLocation}|${original.painTriggers.sorted()}|${original.painBodySites.sorted()}|${PainBodyMapRules.pointContext(original.painBodyPoints)}|${original.observedDate}|${original.observedTime}|${original.comparedWithId}" else "") }
    LaunchedEffect(symptom, scale, recall, respondent, value.author, location, triggerValues, bodySites, bodyPoints, observedDate, observedTime, comparison?.previous?.id) {
        val key = "$symptom|$scale|$recall|$respondent|${value.author}|$location|${value.painTriggers.sorted()}|${value.painBodySites.sorted()}|${PainBodyMapRules.pointContext(value.painBodyPoints)}|$observedDate|$observedTime|${comparison?.previous?.id.orEmpty()}"
        if (referenceKey.isNotBlank() && referenceKey != key) change = ""
        referenceKey = key
    }
    LaunchedEffect(personId) { if (personId != capturedPerson) onClose() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Heading(t(if (prior != null && initial == null) "Update rating" else if (initial == null) "How are you feeling?" else "Edit symptom note", if (prior != null && initial == null) "नया अंक लिखें" else if (initial == null) "तबीयत कैसी है?" else "तबीयत का नोट बदलें"), 28)
                Text(personName, color = DoctorCardSub, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (prior != null && initial == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!fullDetails, { fullDetails = false }, label = { Text(t("Quick rating", "सिर्फ़ नया अंक")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-quick-mode"))
                    FilterChip(fullDetails, { fullDetails = true }, label = { Text(t("Full details", "पूरी जानकारी")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-full-mode"))
                }
                if (!fullDetails) SurfaceCard(Mint) {
                    Text(SymptomDiaryRules.symptomLabel(symptom, hindi), fontWeight = FontWeight.SemiBold)
                    Text("${SymptomDiaryRules.label(SymptomDiaryRules.recalls, recall, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.respondents, respondent, hindi)} · ${value.author}")
                    Text(t("Check the same context; open Full details if anything changed.", "यही जानकारी सही है तो नया अंक लिखें। कुछ बदला है तो पूरी जानकारी खोलें।"), style = MaterialTheme.typography.bodySmall)
                    if (location.isNotBlank() || value.painBodySites.isNotEmpty()) Text(listOf(location, value.painBodySites.joinToString { PainBodyMapRules.label(it, hindi) }).filter(String::isNotBlank).joinToString(" · "))
                    if (startedOn.isNotBlank()) Text("${t("Started", "शुरू हुआ")}: $startedOn")
                }
            }
            if (fullDetails) SymptomSection(1, t("What would you like to note?", "किस बारे में नोट लिखना है?"), t("Pick one.", "एक चुनें।")) {
                SymptomDiaryRules.symptoms.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (en, _) -> SymptomTile(if (en == "Other") otherSymptom else !otherSymptom && symptom == en, SymptomDiaryRules.symptomLabel(en, hindi), symptomIcon(en), "symptom-choice-$en", Modifier.weight(1f).fillMaxHeight()) {
                            otherSymptom = en == "Other"; symptom = if (otherSymptom) "" else en; scale = SymptomDiaryRules.scaleForSymptom(en); if (en != "Pain") { location = ""; triggerValues = ""; bodySites = ""; bodyPoints = "[]" }; rating = -2
                        } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (otherSymptom) CareField(symptom, { symptom = it.take(80) }, t("Symptom name", "परेशानी का नाम"), singleLine = true)
            }
            if (fullDetails) SymptomSection(2, t("Who reported this?", "किसने बताया?")) {
                DiaryChoices(SymptomDiaryRules.respondents, respondent, hindi) { respondent = it }
                if (respondent.isNotBlank() && respondent != "patient") CareField(author, { author = it.take(120) }, t(if (respondent == "assisted-patient") "Written with help from" else "Observer's name", if (respondent == "assisted-patient") "लिखने में किसने मदद की" else "देखने वाले का नाम"), singleLine = true)
            }
            if (fullDetails) SymptomSection(3, t("Time frame", "समय अवधि")) {
                DiaryChoices(SymptomDiaryRules.recalls, recall, hindi) { recall = it }
            }
            SymptomSection(4, SymptomDiaryRules.ratingQuestion(scale, hindi)) {
                Text(SymptomDiaryRules.ratingAnchors(scale, hindi), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("symptom-rating-anchors"))
                RatingReadout(rating, hindi, scale)
                listOf(0..5, 6..10).forEach { range ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        range.forEach { number -> RatingButton(number, rating == number, Modifier.weight(1f).aspectRatio(1f)) { rating = number } }
                        if (range.count() < 6) Spacer(Modifier.weight(1f))
                    }
                }
                SymptomPill(rating == -1, t("Not rated / unable to rate", "अंक नहीं बताया / बता नहीं सकते"), "symptom-unrated", Modifier.fillMaxWidth()) { rating = -1; change = "" }
            }
            SymptomSection(5, t("When was this?", "यह कब हुआ?"), t("Change the date or time if it was earlier.", "पहले का हो तो तारीख या समय बदलें।")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { val d = LocalDate.parse(observedDate); DatePickerDialog(context, { _, y, m, day -> observedDate = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(observedDate, color = DoctorCardInk) }
                    OutlinedButton(onClick = { val time = LocalTime.parse(observedTime); TimePickerDialog(context, { _, h, m -> observedTime = "%02d:%02d".format(h, m) }, time.hour, time.minute, true).show() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(observedTime, color = DoctorCardInk) }
                }
            }
            if (fullDetails && symptom == "Pain") SymptomSection(null, t("About the pain", "दर्द के बारे में"), t("Optional details.", "वैकल्पिक जानकारी।")) {
                CarePainBodyMap(value.painBodySites, value.painBodyPoints, rating.takeIf { it >= 0 }, hindi) { sites, points -> bodySites = sites.joinToString("|"); bodyPoints = PainBodyMapRules.encodePoints(points).toString() }
                CareField(location, { location = it.take(100) }, t("Pain location (optional)", "दर्द की जगह (वैकल्पिक)"), singleLine = true)
                Text(t("When does it happen? (optional)", "कब होता है? (वैकल्पिक)"), color = DoctorCardInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { SymptomDiaryRules.triggers.forEach { (key, labels) ->
                    SymptomPill(key in value.painTriggers, if (hindi) labels.second else labels.first, null, multi = true) { val next = value.painTriggers.toMutableSet(); if (!next.add(key)) next.remove(key); triggerValues = next.sorted().joinToString("|") }
                } }
            }
            if (comparison != null) {
                val previous = SymptomNoteCodec.decode(comparison.previous.detail)!!
                SymptomSection(null, t("Compared with that note (optional)", "उस नोट के मुकाबले (वैकल्पिक)")) {
                    Surface(shape = RoundedCornerShape(14.dp), color = DoctorStatusGoodBg, modifier = Modifier.fillMaxWidth()) {
                        Text("${t("Previous matching note", "पिछला समान नोट")}: ${previous.observedDate} ${previous.observedTime} · ${previous.rating}/10", color = DoctorCardInk, fontSize = 15.sp, modifier = Modifier.padding(12.dp).testTag("symptom-previous-reference"))
                    }
                    DiaryChoices(SymptomDiaryRules.changes, change, hindi) { change = if (change == it) "" else it }
                }
            } else if (rating >= 0 && respondent.isNotBlank()) Text(t("No earlier matching rating", "पहले का समान अंक नहीं है"), color = DoctorCardSub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
            if (fullDetails) SymptomSection(null, t("More details", "और जानकारी"), t("Optional.", "वैकल्पिक।")) {
                OutlinedButton(onClick = { val d = runCatching { LocalDate.parse(startedOn) }.getOrDefault(LocalDate.parse(observedDate)); DatePickerDialog(context, { _, y, m, day -> startedOn = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.maxDate = LocalDate.parse(observedDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.show() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (startedOn.isBlank()) t("Started on… (optional)", "कब शुरू हुआ… (वैकल्पिक)") else "${t("Started", "शुरू हुआ")}: $startedOn", color = DoctorCardInk) }
                if (startedOn.isNotBlank()) TextButton(onClick = { startedOn = "" }) { Text(t("Clear start date", "शुरू होने की तारीख हटाएँ")) }
                CareField(note, { note = it.take(500) }, t("Anything else? (optional)", "कुछ और? (वैकल्पिक)"), minLines = 2)
            }
            (localError.takeIf { it.isNotBlank() } ?: actionError)?.let { Text(if (hindi && it == SymptomDiaryRules.SAVE_CONFLICT) "यह नोट बदल गया है या हटा दिया गया है। आपके जवाब यहीं हैं। सहेजने से पहले यह फ़ॉर्म बंद करके मौजूदा नोट फिर खोलें।" else if (hindi) "नोट सहेजा नहीं गया। जवाब यहीं हैं। तारीख, नाम और चुने हुए जवाब जाँचें।" else it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Button(onClick = {
                val saved = value.copy(selfReportedChange = change.takeIf { comparison != null }.orEmpty(), comparedWithId = if (change.isNotBlank()) comparison?.previous?.id.orEmpty() else "")
                localError = if (rating == -2) t("Choose a rating or Not rated.", "अंक चुनें या अंक नहीं बताया चुनें।") else SymptomDiaryRules.error(saved).orEmpty()
                if (localError.isBlank()) onSave(id, saved)
            }, enabled = !busy && canEdit && personId == capturedPerson, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("symptom-save")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save note", "नोट सहेजें"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), color = DoctorCardSub, fontSize = 16.sp) }
        }
    }
}

@Composable private fun DiaryChoices(options: Map<String, Pair<String, String>>, selected: String, hindi: Boolean, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { (key, label) -> SymptomPill(selected == key, if (hindi) label.second else label.first, "diary-choice-$key", onClick = { onSelect(key) }) } }
}
