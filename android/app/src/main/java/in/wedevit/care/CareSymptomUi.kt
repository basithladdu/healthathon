@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.lerp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable fun CareSymptomDiary(vm: CareModel, onBack: () -> Unit, startAdding: Boolean = false, onStartAddingHandled: () -> Unit = {}) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The app-wide English/Hindi choice; this screen's chips switch every family-side screen.
    val language = LocalCareLanguage.current ?: rememberCareLanguage(vm.accountNamespace)
    val hindi = language.hindi
    var adding by rememberSaveable(selected) { mutableStateOf(false) }
    // "Note a symptom" on Today opens the form once; coming back to this screen later does not reopen it.
    LaunchedEffect(startAdding) { if (startAdding) { if (canEdit) adding = true; onStartAddingHandled() } }
    var showingPainHistory by rememberSaveable(selected) { mutableStateOf(false) }
    val person = people.firstOrNull { it.id == selected }
    // Decode each note once for sorting; a decode inside the comparator ran on every comparison.
    val notes = remember(entries, selected) { entries.filter { it.personId == selected && SymptomNoteCodec.isStructured(it.detail) }
        .map { it to SymptomNoteCodec.decode(it.detail)?.let { n -> "${n.observedDate}T${n.observedTime}" }.orEmpty() }
        .sortedByDescending { it.second }.map { it.first } }
    val hasPainHistory = remember(entries, person?.id) { person != null && PainHistoryRules.entries(entries, person.id).isNotEmpty() }
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
                    FilterChip(hindi == value, { language.choose(value) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        if (canEdit && person != null) item {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("symptom-add")) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(t("How are you feeling?", "तबीयत कैसी है?"))
            }
        }
        if (person != null && hasPainHistory) item {
            OutlinedButton(onClick = { showingPainHistory = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("symptom-pain-history")) { Text(t("Pain history on body", "शरीर पर दर्द का इतिहास")) }
            Text(t(PainBands.SOURCE_EN, PainBands.SOURCE_HI), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, modifier = Modifier.padding(top = 6.dp))
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

/** Friendly "Today, 4:01 PM" / "Yesterday, ..." / "26 Oct, 4:01 PM" (Hindi too); falls back to the stored text. */
internal fun friendlyWhen(date: String, time: String, hindi: Boolean, today: LocalDate = LocalDate.now()): String = runCatching {
    val d = LocalDate.parse(date); val locale = java.util.Locale(if (hindi) "hi" else "en")
    val clock = LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a", locale))
    val day = when (d) { today -> if (hindi) "आज" else "Today"; today.minusDays(1) -> if (hindi) "कल" else "Yesterday"; else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy", locale)) }
    "$day, $clock"
}.getOrDefault("$date $time")

/** Strength badge: Wong-Baker block colour for 1-10, neutral grey for 0 (no heat colour), outlined "Not rated" when absent. Pain adds its verbal band ("7 · Severe"); other symptoms show the number only. */
@Composable internal fun StrengthBadge(rating: Int?, hindi: Boolean, band: Boolean = false) {
    val shape = RoundedCornerShape(50)
    if (rating == null) { Text(if (hindi) "अंक नहीं बताया" else "Not rated", color = DoctorCardSub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("symptom-card-badge").border(1.dp, DoctorHairline, shape).padding(horizontal = 12.dp, vertical = 6.dp)); return }
    val bg = Color(PainBodyMapRules.heatColor(rating)); val ink = if (rating == 0) DoctorCardInk else readableOn(bg)
    Text(if (band) "$rating · ${PainBands.label(rating, hindi)}" else "$rating/10", color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("symptom-card-badge").background(bg, shape).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable private fun SymptomTag(text: String) = Text(text, color = DoctorCardInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.background(Cream, RoundedCornerShape(50)).border(1.dp, DoctorHairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp))

@Composable fun SymptomNoteCard(item: CareItem, vm: CareModel, hindi: Boolean = false) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var followingUp by rememberSaveable(item.id) { mutableStateOf(false) }
    var showingTrend by rememberSaveable(item.id) { mutableStateOf(false) }
    var showingPainHistory by rememberSaveable(item.id) { mutableStateOf(false) }
    var removing by rememberSaveable(item.id) { mutableStateOf(false) }
    var menuOpen by remember(item.id) { mutableStateOf(false) }
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val note = remember(item.detail) { SymptomNoteCodec.decode(item.detail) }
    val isPain = note?.symptom?.trim()?.equals("Pain", true) == true
    val isMood = note?.symptom == SymptomDiaryRules.MOOD
    val largeText = LocalDensity.current.fontScale > 1.15f
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(lerp(Cream, DoctorAccent, .15f)), contentAlignment = Alignment.Center) { Icon(symptomIcon(note?.symptom ?: item.title), null, Modifier.size(22.dp), tint = DoctorAccent) }
            Text(note?.let { SymptomDiaryRules.symptomLabel(it.symptom, hindi) } ?: item.title, color = DoctorCardInk, fontFamily = SaharaTitleFont, fontSize = 18.sp, lineHeight = 22.sp, maxLines = if (largeText) Int.MAX_VALUE else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (note != null && !largeText && !isMood) StrengthBadge(PainBodyMapRules.overall(note.painBodyRatings) ?: note.rating, hindi, band = isPain)
            if (canEdit && note != null) Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp).testTag("symptom-card-menu-${item.id}")) { Icon(Icons.Default.MoreVert, t("More actions", "और विकल्प"), tint = DoctorCardSub) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(t("Update rating", "नया अंक लिखें")) }, enabled = !busy, onClick = { menuOpen = false; followingUp = true }, modifier = Modifier.testTag("symptom-followup-${item.id}"))
                    DropdownMenuItem(text = { Text(t("Edit", "बदलें")) }, leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)) }, enabled = !busy, onClick = { menuOpen = false; editing = true }, modifier = Modifier.testTag("symptom-edit-${item.id}"))
                    DropdownMenuItem(text = { Text(t("Remove", "हटाएँ")) }, enabled = !busy, onClick = { menuOpen = false; removing = true }, modifier = Modifier.testTag("symptom-remove-${item.id}"))
                }
            } else Spacer(Modifier.width(8.dp))
        }
        if (note != null) {
            if (largeText && !isMood) StrengthBadge(PainBodyMapRules.overall(note.painBodyRatings) ?: note.rating, hindi, band = isPain)
            if (isMood) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("symptom-card-mood")) {
                SymptomDiaryRules.moodShort.forEach { (key, label) -> SymptomTag("${if (hindi) label.second else label.first} ${SymptomDiaryRules.ratingLabel(note.moodRatings[key], hindi)}") }
            }
            if (note.painBodyRatings.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note.painBodySites.filter { it in note.painBodyRatings }.forEach { site -> val r = note.painBodyRatings.getValue(site)
                    Row(Modifier.background(Cream, RoundedCornerShape(50)).border(1.dp, DoctorHairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(Color(PainBodyMapRules.heatColor(r)), CircleShape).border(1.dp, DoctorHairline, CircleShape)); Text("${PainBodyMapRules.label(site, hindi)} $r · ${PainBands.label(r, hindi)}", color = DoctorCardInk, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }
            }
            Text(friendlyWhen(note.observedDate, note.observedTime, hindi), color = DoctorCardSub, fontSize = 14.sp, modifier = Modifier.testTag("symptom-card-when"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SymptomTag(SymptomDiaryRules.label(SymptomDiaryRules.recalls, note.recall, hindi))
                SymptomTag("${SymptomDiaryRules.label(SymptomDiaryRules.respondents, note.respondent, hindi)} · ${note.author}")
            }
            val details = listOf(SymptomDiaryRules.ratingAnchors(note.scale, hindi).takeIf { note.scale == SymptomDiaryRules.WELLBEING_SCALE }.orEmpty(), note.startedOn.takeIf { it.isNotBlank() }?.let { "${t("Started", "शुरू हुआ")}: $it" }.orEmpty(), note.painLocation,
                note.painBodySites.takeIf { it.isNotEmpty() && note.painBodyRatings.isEmpty() }?.joinToString(prefix = t("Pain areas: ", "दर्द की जगह: ")) { PainBodyMapRules.label(it, hindi) }.orEmpty(), note.painTriggers.joinToString { SymptomDiaryRules.label(SymptomDiaryRules.triggers, it, hindi) },
                note.selfReportedChange.takeIf { it.isNotBlank() }?.let { "${t("Reported change", "उनके मुताबिक बदलाव")}: ${SymptomDiaryRules.label(SymptomDiaryRules.changes, it, hindi)}" }.orEmpty()).filter(String::isNotBlank)
            if (details.isNotEmpty()) Text(details.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
            if (note.note.isNotBlank()) Text(note.note, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = DoctorCardInk)
        } else Text(SymptomNoteCodec.readable(item.detail, hindi), style = MaterialTheme.typography.bodyMedium)
        val comparison = remember(entries, item, note) { note?.let { SymptomDiaryRules.comparison(entries, item, it) } }
        if (comparison != null) {
            val baseline = SymptomNoteCodec.decode(comparison.baseline.detail)!!
            val previous = SymptomNoteCodec.decode(comparison.previous.detail)!!
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("${t("First matching entry", "पहला समान नोट")}: ${baseline.observedDate} ${baseline.observedTime} · ${SymptomDiaryRules.ratingLabel(baseline.rating, hindi)}", style = MaterialTheme.typography.bodySmall)
            Text("${t("Previous matching entry", "पिछला समान नोट")}: ${previous.observedDate} ${previous.observedTime} · ${SymptomDiaryRules.ratingLabel(previous.rating, hindi)}", style = MaterialTheme.typography.bodySmall)
            Text("${t("Number difference", "अंकों का अंतर")}: ${if (comparison.difference > 0) "+" else ""}${comparison.difference}", style = MaterialTheme.typography.bodySmall)
        }
        if (note != null) Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isPain) FilledTonalButton(onClick = { showingPainHistory = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("symptom-pain-history-${item.id}")) { Icon(Icons.Default.AccessibilityNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("View on body", "शरीर पर देखें")) }
            OutlinedButton(onClick = { showingTrend = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("symptom-trend-${item.id}")) { Icon(Icons.Default.Timeline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Trend", "बदलाव")) }
        }
    } }
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
        hindi = hindi, initial = initial, followup = followup, busy = busy, canEdit = canEdit && selected == capturedPerson, actionError = actionError, sharedOnline = vm.cloud != null,
        onSave = { id, value -> vm.saveSymptomNote(capturedPerson, id, value, expectedToken, onClose) }, onClose = onClose)
}

/** The same native form can be checked with isolated callback fixtures, without touching accounts or real records. */
@Composable fun SymptomNoteForm(personId: String, personName: String, entries: List<CareItem>, hindi: Boolean,
    initial: CareItem? = null, busy: Boolean = false, canEdit: Boolean = true, actionError: String? = null,
    followup: CareItem? = null, sharedOnline: Boolean = false, onSave: (String, SymptomNote) -> Unit, onClose: () -> Unit) {
    val original = remember(initial?.id) { initial?.let { SymptomNoteCodec.decode(it.detail) } }
    val largeText = LocalDensity.current.fontScale > 1.15f
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
    // Mood: two separate scales; -2 = not answered yet, -1 = Not rated. Answering either one is enough to go on.
    var moodLow by rememberSaveable(id) { mutableIntStateOf(if (original == null) -2 else original.moodRatings["low-mood"] ?: -1) }
    var moodAnxiety by rememberSaveable(id) { mutableIntStateOf(if (original == null) -2 else original.moodRatings["anxiety"] ?: -1) }
    var observedDate by rememberSaveable(id) { mutableStateOf(seed?.observedDate ?: LocalDate.now().toString()) }
    var observedTime by rememberSaveable(id) { mutableStateOf(seed?.observedTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var recall by rememberSaveable(id) { mutableStateOf(seed?.recall ?: "now") }
    // A brand-new note starts with who answered last time for this person (shown on the "About this note" step, changeable).
    val lastWho = remember(id) { if (initial == null && prior == null) CareDailyFlow.lastReporter(entries, capturedPerson) else null }
    var respondent by rememberSaveable(id) { mutableStateOf(seed?.respondent ?: lastWho?.first.orEmpty()) }
    var author by rememberSaveable(id) { mutableStateOf(seed?.author ?: lastWho?.second.orEmpty()) }
    var startedOn by rememberSaveable(id) { mutableStateOf(seed?.startedOn.orEmpty()) }
    var note by rememberSaveable(id) { mutableStateOf(original?.note.orEmpty()) }
    var location by rememberSaveable(id) { mutableStateOf(seed?.painLocation.orEmpty()) }
    var bodySites by rememberSaveable(id) { mutableStateOf(seed?.painBodySites?.joinToString("|").orEmpty()) }
    var bodyPoints by rememberSaveable(id) { mutableStateOf(PainBodyMapRules.encodePoints(seed?.painBodyPoints.orEmpty()).toString()) }
    var bodyRatings by rememberSaveable(id) { mutableStateOf(PainBodyMapRules.ratingsKey(seed?.let { n -> n.painBodyRatings.ifEmpty { n.rating?.let { r -> n.painBodySites.associateWith { r } }.orEmpty() } }.orEmpty())) }
    var triggerValues by rememberSaveable(id) { mutableStateOf(seed?.painTriggers?.joinToString("|").orEmpty()) }
    var change by rememberSaveable(id) { mutableStateOf(original?.selfReportedChange.orEmpty()) }
    var localError by rememberSaveable(id) { mutableStateOf("") }
    // Pain: each spot keeps its own rating; the note's overall rating is the highest rated spot (the quick follow-up keeps the single rating card).
    val spotRatings = if (symptom == "Pain") PainBodyMapRules.parseRatingsKey(bodyRatings).filterKeys { it in bodySites.split('|') } else emptyMap()
    val spotsDrive = symptom == "Pain" && spotRatings.isNotEmpty() && !(prior != null && initial == null && !fullDetails)
    val isMood = symptom == SymptomDiaryRules.MOOD
    val ratingNow = if (spotsDrive) PainBodyMapRules.overall(spotRatings)!! else if (isMood) { if (moodLow == -2 && moodAnxiety == -2) -2 else -1 } else rating
    val value = SymptomNote(symptom, ratingNow.takeIf { it >= 0 }, observedDate, observedTime, recall, respondent,
        if (respondent == "patient") personName else author, startedOn, note, location, triggerValues.split('|').filter(String::isNotBlank), scale = scale,
        painBodySites = bodySites.split('|').filter(String::isNotBlank), painBodyPoints = PainBodyMapRules.decodePoints(org.json.JSONArray(bodyPoints)),
        painBodyModel = if (bodyPoints != "[]") PainBodyMapRules.MODEL else "", painBodyRatings = spotRatings,
        moodRatings = if (isMood) listOf("low-mood" to moodLow, "anxiety" to moodAnxiety).filter { it.second >= 0 }.toMap() else emptyMap())
    val current = initial ?: CareItem(id = id, personId = capturedPerson, kind = "checkin", title = symptom)
    val comparison = if (ratingNow >= 0 && respondent.isNotBlank() && value.author.isNotBlank()) SymptomDiaryRules.comparison(entries, current, value) else null
    // A changed question/reference cannot retain a prior subjective comparison.
    var referenceKey by rememberSaveable(id) { mutableStateOf(if (original != null) "${original.symptom}|${original.scale}|${original.recall}|${original.respondent}|${original.author}|${original.painLocation}|${original.painTriggers.sorted()}|${original.painBodySites.sorted()}|${PainBodyMapRules.pointContext(original.painBodyPoints)}|${original.observedDate}|${original.observedTime}|${original.comparedWithId}" else "") }
    LaunchedEffect(symptom, scale, recall, respondent, value.author, location, triggerValues, bodySites, bodyPoints, observedDate, observedTime, comparison?.previous?.id) {
        val key = "$symptom|$scale|$recall|$respondent|${value.author}|$location|${value.painTriggers.sorted()}|${value.painBodySites.sorted()}|${PainBodyMapRules.pointContext(value.painBodyPoints)}|$observedDate|$observedTime|${comparison?.previous?.id.orEmpty()}"
        if (referenceKey.isNotBlank() && referenceKey != key) change = ""
        referenceKey = key
    }
    LaunchedEffect(personId) { if (personId != capturedPerson) onClose() }
    // Steps: what -> (where, Pain only) -> how (one page) -> more (+ Save). Quick follow-ups stay on a single short page.
    val quick = prior != null && initial == null && !fullDetails
    val steps = if (symptom == "Pain") listOf("what", "where", "how", "more") else listOf("what", "how", "more")
    var stepKey by rememberSaveable(id) { mutableStateOf(if (initial != null) "more" else if (prior != null) "how" else "what") }
    val cur = if (stepKey in steps) stepKey else if (stepKey == "where") "how" else steps.first()
    val pos = steps.indexOf(cur)
    val ratingMissing = ratingNow == -2
    val dateProblem = SymptomDiaryRules.error(value.copy(startedOn = "")) in setOf("Choose a valid date and time.", "The observation cannot be in the future.")
    val stepValid = when (cur) {
        "what" -> symptom.trim().length in 1..80
        "how" -> !ratingMissing && respondent in SymptomDiaryRules.respondents && recall in SymptomDiaryRules.recalls && value.author.trim().length in 1..120 && !dateProblem
        else -> true
    }
    val stepTitle = when (cur) { "what" -> t("What would you like to note?", "किस बारे में नोट लिखना है?"); "where" -> t("Where does it hurt?", "कहाँ दर्द है?"); "how" -> t("About this note", "इस नोट के बारे में"); else -> t("Anything else? (optional)", "कुछ और? (वैकल्पिक)") }
    val goBack: () -> Unit = { if (quick || pos <= 0) onClose() else stepKey = steps[pos - 1] }
    val scroll = rememberScrollState()
    LaunchedEffect(cur) { scroll.scrollTo(0) }
    // System back steps back through the form; a swipe down or scrim tap only closes it before anything is chosen, so answers are never dropped by accident (Cancel always closes).
    val gestureCloses by rememberUpdatedState(initial == null && prior == null && pos == 0 && symptom.isBlank())
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || gestureCloses }), containerColor = Cream,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)) {
        // Inside the sheet: the sheet is its own window, so a back handler outside it never hears system back.
        BackHandler(onBack = goBack)
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Heading(t(if (prior != null && initial == null) "Update rating" else if (initial == null) "How are you feeling?" else "Edit symptom note", if (prior != null && initial == null) "नया अंक लिखें" else if (initial == null) "तबीयत कैसी है?" else "तबीयत का नोट बदलें"), 26)
                Text(personName, color = DoctorCardSub, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (!quick) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { steps.forEachIndexed { i, _ -> Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= pos) DoctorAccent else DoctorHairline)) } }
                    Text("${t("Step", "चरण")} ${pos + 1} ${t("of", "/")} ${steps.size} · $stepTitle", color = DoctorCardSub, fontSize = 14.sp, modifier = Modifier.testTag("symptom-step-indicator"))
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp)) {
              AnimatedContent(cur, transitionSpec = { SaharaMotion.transform(if (steps.indexOf(targetState) >= steps.indexOf(initialState)) SaharaMotion.Direction.Forward else SaharaMotion.Direction.Back) }, label = "symptom-step") { cur ->
              Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                if (!quick && cur == "what") SymptomSection(null, t("What would you like to note?", "किस बारे में नोट लिखना है?"), t("Tap one to continue.", "एक चुनें, अगला पन्ना खुलेगा।")) {
                    SymptomDiaryRules.symptoms.entries.chunked(if (largeText) 1 else 2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (en, _) -> SymptomTile(if (en == "Other") otherSymptom else !otherSymptom && symptom == en, SymptomDiaryRules.symptomLabel(en, hindi), symptomIcon(en), "symptom-choice-$en", Modifier.weight(1f).fillMaxHeight()) {
                                otherSymptom = en == "Other"; symptom = if (otherSymptom) "" else en; scale = SymptomDiaryRules.scaleForSymptom(en); if (en != "Pain") { location = ""; triggerValues = ""; bodySites = ""; bodyPoints = "[]"; bodyRatings = "" }; rating = -2; moodLow = -2; moodAnxiety = -2
                                if (en != "Other") stepKey = if (en == "Pain") "where" else "how"
                            } }
                            if (pair.size == 1 && !largeText) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (otherSymptom) CareField(symptom, { symptom = it.take(80) }, t("Symptom name", "परेशानी का नाम"), singleLine = true)
                }
                if (!quick && cur == "where" && symptom == "Pain") Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CarePainBodyMap(value.painBodySites, value.painBodyPoints, ratingNow.takeIf { it >= 0 }, hindi, ratings = spotRatings, onSpotRating = { site, r -> bodyRatings = PainBodyMapRules.ratingsKey(spotRatings + (site to r)) }) { sites, points -> bodySites = sites.joinToString("|"); bodyPoints = PainBodyMapRules.encodePoints(points).toString(); bodyRatings = PainBodyMapRules.ratingsKey(spotRatings.filterKeys { it in sites }) }
                    CareField(location, { location = it.take(100) }, t("Pain location (optional)", "दर्द की जगह (वैकल्पिक)"), singleLine = true)
                    Text(t("When does it happen? (optional)", "कब होता है? (वैकल्पिक)"), color = DoctorCardInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { SymptomDiaryRules.triggers.forEach { (key, labels) ->
                        SymptomPill(key in value.painTriggers, if (hindi) labels.second else labels.first, null, multi = true) { val next = value.painTriggers.toMutableSet(); if (!next.add(key)) next.remove(key); triggerValues = next.sorted().joinToString("|") }
                    } }
                }
                if (!quick && cur == "how") SymptomSection(null, t("Who reported this?", "किसने बताया?")) {
                    DiaryChoices(SymptomDiaryRules.respondents, respondent, hindi) { respondent = it }
                    if (respondent.isNotBlank() && respondent != "patient") CareField(author, { author = it.take(120) }, t(if (respondent == "assisted-patient") "Written with help from" else "Name of the person who saw it", if (respondent == "assisted-patient") "लिखने में किसने मदद की" else "देखने वाले का नाम"), singleLine = true)
                }
                if (!quick && cur == "how") SymptomSection(null, t("Time frame", "समय अवधि")) {
                    DiaryChoices(SymptomDiaryRules.recalls, recall, hindi) { recall = it }
                }
                // Pain strength is set per spot in the "where" step: with a rated spot there is no card here; without one, offer to go back or choose Not rated.
                if (!quick && cur == "how" && symptom == "Pain" && !spotsDrive) SymptomSection(null, t("No pain strength set yet.", "दर्द की तीव्रता अभी नहीं बताई।")) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SymptomPill(false, t("Back to rate a spot", "किसी जगह का अंक चुनें"), "symptom-pain-rate-back") { stepKey = "where" }
                        SymptomPill(rating == -1, t("Not rated", "अंक नहीं बताया"), "symptom-unrated") { rating = -1; change = "" }
                    }
                }
                if ((quick || cur == "how") && isMood) SymptomDiaryRules.moodScales.forEach { (key, label) ->
                    val chosen = if (key == "low-mood") moodLow else moodAnxiety
                    val set: (Int) -> Unit = { n -> if (key == "low-mood") { moodLow = n } else { moodAnxiety = n } }
                    SymptomSection(null, if (hindi) label.second else label.first, SymptomDiaryRules.ratingAnchors(scale, hindi)) {
                        RatingReadout(chosen, hindi, scale)
                        listOf(0..5, 6..10).forEach { range ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                range.forEach { number -> RatingButton(number, chosen == number, Modifier.weight(1f).aspectRatio(1f), tag = "symptom-mood-$key-$number") { set(number) } }
                                if (range.count() < 6) Spacer(Modifier.weight(1f))
                            }
                        }
                        SymptomPill(chosen == -1, t("Not rated / unable to rate", "अंक नहीं बताया / बता नहीं सकते"), "symptom-mood-$key-unrated", Modifier.fillMaxWidth()) { set(-1) }
                    }
                }
                if ((quick || cur == "how") && !(symptom == "Pain" && !quick) && !isMood) SymptomSection(null, SymptomDiaryRules.ratingQuestion(scale, hindi)) {
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
                if (quick || cur == "how") SymptomSection(null, t("When was this?", "यह कब हुआ?"), t("Change the date or time if it was earlier.", "पहले का हो तो तारीख या समय बदलें।")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { val d = LocalDate.parse(observedDate); DatePickerDialog(context, { _, y, m, day -> observedDate = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(observedDate, color = DoctorCardInk) }
                        OutlinedButton(onClick = { val time = LocalTime.parse(observedTime); TimePickerDialog(context, { _, h, m -> observedTime = "%02d:%02d".format(h, m) }, time.hour, time.minute, true).show() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(observedTime, color = DoctorCardInk) }
                    }
                }
                if (quick || cur == "more") {
                    if (comparison != null) {
                        val previous = SymptomNoteCodec.decode(comparison.previous.detail)!!
                        SymptomSection(null, t("Compared with that note (optional)", "उस नोट के मुकाबले (वैकल्पिक)")) {
                            Surface(shape = RoundedCornerShape(14.dp), color = DoctorStatusGoodBg, modifier = Modifier.fillMaxWidth()) {
                                Text("${t("Previous matching note", "पिछला समान नोट")}: ${previous.observedDate} ${previous.observedTime} · ${previous.rating}/10", color = DoctorCardInk, fontSize = 15.sp, modifier = Modifier.padding(12.dp).testTag("symptom-previous-reference"))
                            }
                            DiaryChoices(SymptomDiaryRules.changes, change, hindi) { change = if (change == it) "" else it }
                        }
                    } else if (ratingNow >= 0 && respondent.isNotBlank()) Text(t("No earlier matching rating", "पहले का समान अंक नहीं है"), color = DoctorCardSub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
                if (!quick && cur == "more") SymptomSection(null, t("Anything else? (optional)", "कुछ और? (वैकल्पिक)")) {
                    OutlinedButton(onClick = { val d = runCatching { LocalDate.parse(startedOn) }.getOrDefault(LocalDate.parse(observedDate)); DatePickerDialog(context, { _, y, m, day -> startedOn = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.maxDate = LocalDate.parse(observedDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.show() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (startedOn.isBlank()) t("Started on… (optional)", "कब शुरू हुआ… (वैकल्पिक)") else "${t("Started", "शुरू हुआ")}: $startedOn", color = DoctorCardInk) }
                    if (startedOn.isNotBlank()) TextButton(onClick = { startedOn = "" }) { Text(t("Clear start date", "शुरू होने की तारीख हटाएँ")) }
                    CareField(note, { note = it.take(500) }, t("Note (optional)", "नोट (वैकल्पिक)"), minLines = 2)
                }
              }
              }
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val saveNow: () -> Unit = {
                    val saved = value.copy(selfReportedChange = change.takeIf { comparison != null }.orEmpty(), comparedWithId = if (change.isNotBlank()) comparison?.previous?.id.orEmpty() else "")
                    localError = if (ratingNow == -2) t("Choose a rating or Not rated.", "अंक चुनें या अंक नहीं बताया चुनें।") else SymptomDiaryRules.error(saved)?.let { e -> t(e, SymptomErrorsHindi[e] ?: e) }.orEmpty()
                    if (localError.isBlank()) onSave(id, saved)
                }
                (localError.takeIf { it.isNotBlank() } ?: actionError)?.let { Text(if (hindi && it == SymptomDiaryRules.SAVE_CONFLICT) "यह नोट बदल गया है या हटा दिया गया है। आपके जवाब यहीं हैं। सहेजने से पहले यह फ़ॉर्म बंद करके मौजूदा नोट फिर खोलें।" else if (hindi) "नोट सहेजा नहीं गया। जवाब यहीं हैं। तारीख, नाम और चुने हुए जवाब जाँचें।" else it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (!quick && !stepValid && cur == "how") Text(if (ratingMissing) t("Choose a rating or Not rated.", "अंक चुनें या अंक नहीं बताया चुनें।") else t("Answer the questions above to continue.", "आगे बढ़ने के लिए ऊपर के सवाल भरें।"), color = DoctorCardSub, fontSize = 14.sp)
                if (sharedOnline && (quick || pos == steps.lastIndex || (cur == "how" && stepValid))) Text(t(CarePrivacy.SYMPTOM_NOTICE_EN, CarePrivacy.SYMPTOM_NOTICE_HI), color = DoctorCardSub, fontSize = 13.sp, modifier = Modifier.testTag("symptom-privacy-notice"))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = goBack, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("symptom-step-back")) { Text(if (quick || pos <= 0) t("Cancel", "रद्द करें") else t("Back", "पीछे"), color = DoctorCardSub, fontSize = 16.sp) }
                    if (quick || pos == steps.lastIndex) Button(onClick = saveNow, enabled = !busy && canEdit && personId == capturedPerson, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Color.White), modifier = Modifier.weight(if (largeText) 1f else 2f).heightIn(min = 56.dp).testTag("symptom-save")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save note", "नोट सहेजें"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    else Button(onClick = { stepKey = steps[pos + 1] }, enabled = stepValid, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Color.White), modifier = Modifier.weight(if (largeText) 1f else 2f).heightIn(min = 56.dp).testTag("symptom-step-next")) { Text(t("Next", "आगे"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                }
                // The rest of the form is optional: once this step is answered, the note can be saved without opening "Anything else?".
                if (!quick && cur == "how" && stepValid && pos < steps.lastIndex) OutlinedButton(onClick = saveNow, enabled = !busy && canEdit && personId == capturedPerson, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("symptom-save-now")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save now · extra details are optional", "अभी सहेजें · बाक़ी जानकारी वैकल्पिक है"), color = DoctorAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable private fun DiaryChoices(options: Map<String, Pair<String, String>>, selected: String, hindi: Boolean, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { (key, label) -> SymptomPill(selected == key, if (hindi) label.second else label.first, "diary-choice-$key", onClick = { onSelect(key) }) } }
}
