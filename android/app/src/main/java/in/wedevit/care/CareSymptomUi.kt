@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
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
    // The app-wide language choice (header dropdown).
    val language = LocalCareLanguage.current ?: rememberCareLanguage(vm.accountNamespace)
    val hindi = language.hindi
    var adding by rememberSaveable(selected) { mutableStateOf(false) }
    // A bento tile opens the form with that symptom already chosen (one tap fewer); "All symptoms" opens the full list.
    var quickStart by rememberSaveable(selected) { mutableStateOf("") }
    // "Note a symptom" on Today opens the form once; coming back to this screen later does not reopen it.
    LaunchedEffect(startAdding) { if (startAdding) { if (canEdit) adding = true; onStartAddingHandled() } }
    var showingPainHistory by rememberSaveable(selected) { mutableStateOf(false) }
    val person = people.firstOrNull { it.id == selected }
    // Decode each note once for sorting; a decode inside the comparator ran on every comparison.
    val notes = remember(entries, selected) { entries.filter { it.personId == selected && SymptomNoteCodec.isStructured(it.detail) }
        .map { it to SymptomNoteCodec.decode(it.detail)?.let { n -> "${n.observedDate}T${n.observedTime}" }.orEmpty() }
        .sortedByDescending { it.second }.map { it.first } }
    var severeFirst by rememberSaveable(selected) { mutableStateOf(true) }
    val shown = remember(notes, severeFirst) { if (severeFirst) SymptomSort.severeFirst(notes, { SymptomSort.headline(it) }, { SymptomSort.stamp(it) }) else notes }
    val hasPainHistory = remember(entries, person?.id) { person != null && PainHistoryRules.entries(entries, person.id).isNotEmpty() }
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    // Recently noted symptoms first, then the common ones, so the usual note is one tap away.
    val quickKeys = remember(notes) { (notes.mapNotNull { SymptomNoteCodec.decode(it.detail)?.symptom?.trim() } + SymptomQuickDefaults)
        .filter { it in SymptomDiaryRules.symptoms && it != "Other" }.distinct().take(5) }
    val largeText = LocalDensity.current.fontScale > 1.15f
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("symptom-diary"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // One title, the person's name as its single subtitle line.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = Ink) }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Heading(t("Symptoms", "लक्षण"), 27)
                    if (!person?.name.isNullOrBlank()) Text(person?.name.orEmpty(), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
                }
            }
        }
        if (canEdit && person != null) item(key = "quick-grid") {
            Column(Modifier.saharaAppear(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Log a symptom", "लक्षण दर्ज करें"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                val tiles = quickKeys.map { it to SymptomDiaryRules.symptomLabel(it, hindi) } + ("Other" to t("All symptoms", "सभी लक्षण"))
                tiles.chunked(if (largeText) 1 else 2).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (key, label) ->
                            SymptomTile(false, label, key, if (key == "Other") "symptom-add" else "symptom-quick-$key", Modifier.weight(1f).fillMaxHeight(), role = androidx.compose.ui.semantics.Role.Button) {
                                quickStart = if (key == "Other") "" else key; adding = true
                            }
                        }
                        if (row.size == 1 && !largeText) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (person != null && hasPainHistory) item {
            // Opens past pain notes on the body model (read only); new pain is added with "How are you feeling?".
            OutlinedButton(onClick = { showingPainHistory = true }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, DoctorAccent), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).saharaAppear().testTag("symptom-pain-history")) {
                Icon(Icons.Default.AccessibilityNew, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(8.dp)); Text(t("Pain map", "दर्द का नक्शा"), color = RustText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // Friendly empty state: a calm picture and one line; the "How are you feeling?" button above is its action.
        if (notes.isEmpty()) item {
            Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SaharaIllustration(SaharaArtKind.NoSymptoms)
                    Text(t("No symptom notes yet", "अभी तबीयत का कोई नोट नहीं है"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        if (notes.isNotEmpty()) item(key = "notes-head") {
            // One row: the list's name and count, with Share beside it (it shares this list).
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${t("Notes", "नोट")} · ${notes.size}", fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
                TextButton(onClick = {
                val text = listOf("Sahara — ${t("Personal symptom notes", "तबीयत के निजी नोट")}", person?.name.orEmpty(),
                    *notes.map { "${SymptomNoteCodec.decode(it.detail)?.let { n -> SymptomDiaryRules.symptomLabel(n.symptom, hindi) }.orEmpty()}\n${SymptomNoteCodec.readable(it.detail, hindi)}" }.toTypedArray()).joinToString("\n\n")
                open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), t("Share symptom notes", "तबीयत के नोट साझा करें")))
            }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Share, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(6.dp)); Text(t("Share notes", "नोट साझा करें"), color = RustText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (notes.size > 1) item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("symptom-sort")) {
                SegmentedButton(selected = severeFirst, onClick = { severeFirst = true }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-sort-severe")) { Text(t("Most severe", "सबसे गंभीर")) }
                SegmentedButton(selected = !severeFirst, onClick = { severeFirst = false }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-sort-newest")) { Text(t("Newest", "सबसे नया")) }
            }
        }
        items(shown, key = { it.id }) { Box(Modifier.animateItem().saharaAppear()) { SymptomNoteCard(it, vm, hindi) } }
    }
    if (adding && person != null) SymptomNoteEditor(vm, hindi, startSymptom = quickStart.ifBlank { null }, onClose = { adding = false; quickStart = "" })
    if (showingPainHistory && person != null) PainBodyHistorySheet(entries, person.id, hindi, onClose = { showingPainHistory = false })
}

/** Shown on the tracker's quick tiles when the person has fewer than five recent symptoms. */
internal val SymptomQuickDefaults = listOf("Pain", "Tiredness", "Breathlessness", "Nausea", "Mood")

/** Severity ordering for the tracker list; pure so it can be unit tested. */
object SymptomSort {
    /** Highest score first; ties newest first (stamp compares as "date" + "T" + "time"); unscored entries last. */
    fun <T> severeFirst(items: List<T>, score: (T) -> Int?, stamp: (T) -> String): List<T> =
        items.sortedWith(compareByDescending<T> { score(it) ?: -1 }.thenByDescending { stamp(it) })
    /** The number the card badge shows; mood notes have no badge, so their worst scale counts. */
    fun headline(item: CareItem): Int? = SymptomNoteCodec.decode(item.detail)?.let { n ->
        if (n.symptom == SymptomDiaryRules.MOOD) n.moodRatings.values.maxOrNull() else PainBodyMapRules.overall(n.painBodyRatings) ?: n.rating }
    fun stamp(item: CareItem): String = SymptomNoteCodec.decode(item.detail)?.let { "${it.observedDate}T${it.observedTime}" }.orEmpty()
}

/** Friendly "Today, 4:01 PM" / "Yesterday, ..." / "26 Oct, 4:01 PM" (Hindi too); falls back to the stored text. */
internal fun friendlyWhen(date: String, time: String, hindi: Boolean, today: LocalDate = LocalDate.now()): String = runCatching {
    val d = LocalDate.parse(date); val locale = careLocale(hindi)
    val clock = LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a", locale))
    val day = when (d) { today -> careTr(hindi, "Today", "आज"); today.minusDays(1) -> careTr(hindi, "Yesterday", "कल"); else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy", locale)) }
    "$day, $clock"
}.getOrDefault("$date $time")

/** Strength badge: Wong-Baker block colour for 1-10, neutral grey for 0 (no heat colour), outlined "Not rated" when absent. Pain adds its verbal band ("7 · Severe"); other symptoms show the number only. */
@Composable internal fun StrengthBadge(rating: Int?, hindi: Boolean, band: Boolean = false) {
    val shape = RoundedCornerShape(50)
    if (rating == null) { Text(careTr(hindi, "Not rated", "अंक नहीं बताया"), color = DoctorCardSub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("symptom-card-badge").border(1.dp, DoctorHairline, shape).padding(horizontal = 12.dp, vertical = 6.dp)); return }
    val bg = Color(PainBodyMapRules.heatColor(rating)); val ink = if (rating == 0) DoctorCardInk else readableOn(bg)
    Text(if (band) "$rating · ${PainBands.label(rating, hindi)}" else "$rating/10", color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("symptom-card-badge").background(bg, shape).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable private fun SymptomTag(text: String) = Text(text, color = DoctorCardInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.background(Cream, RoundedCornerShape(50)).border(1.dp, DoctorHairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp))

/** Small "Logged by family" chip: shown where a family member or carer wrote a symptom note or ticked a dose. */
@Composable internal fun SymptomLoggedByFamilyChip(hindi: Boolean, tag: String = "logged-by-family") = Text(careTr(hindi, "Logged by family", "परिवार ने दर्ज किया"),
    color = DoctorCardInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.background(Cream, RoundedCornerShape(50)).border(1.dp, DoctorHairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp).testTag(tag))

@Composable fun SymptomNoteCard(item: CareItem, vm: CareModel, hindi: Boolean = false) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var followingUp by rememberSaveable(item.id) { mutableStateOf(false) }
    var showingTrend by rememberSaveable(item.id) { mutableStateOf(false) }
    var showingPainHistory by rememberSaveable(item.id) { mutableStateOf(false) }
    var removing by rememberSaveable(item.id) { mutableStateOf(false) }
    var painGone by rememberSaveable(item.id) { mutableStateOf(false) }
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val note = remember(item.detail) { SymptomNoteCodec.decode(item.detail) }
    val isPain = note?.symptom?.trim()?.equals("Pain", true) == true
    val isMood = note?.symptom == SymptomDiaryRules.MOOD
    val largeText = LocalDensity.current.fontScale > 1.15f
    val accent = symptomColor(note?.symptom ?: item.title)
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaContentSize()) { Row(Modifier.height(IntrinsicSize.Min)) {
      // The symptom's own colour down the left edge, so a long list scans by colour as well as by name.
      Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
      Column(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SymptomIconChip(note?.symptom ?: item.title, 40.dp)
            Text(note?.let { SymptomDiaryRules.symptomLabel(it.symptom, hindi) } ?: item.title, color = DoctorCardInk, fontFamily = SaharaTitleFont, fontSize = 18.sp, lineHeight = 22.sp, maxLines = if (largeText) Int.MAX_VALUE else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (note != null && !largeText && !isMood) StrengthBadge(PainBodyMapRules.overall(note.painBodyRatings) ?: note.rating, hindi, band = isPain)
            Spacer(Modifier.width(8.dp))
        }
        if (note != null) {
            if (largeText && !isMood) StrengthBadge(PainBodyMapRules.overall(note.painBodyRatings) ?: note.rating, hindi, band = isPain)
            if (isMood) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("symptom-card-mood")) {
                SymptomDiaryRules.moodShort.forEach { (key, label) -> SymptomTag("${careTr(hindi, label.first, label.second)} ${SymptomDiaryRules.ratingLabel(note.moodRatings[key], hindi)}") }
            }
            if (note.painBodyRatings.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note.painBodySites.filter { it in note.painBodyRatings }.forEach { site -> val r = note.painBodyRatings.getValue(site)
                    Row(Modifier.background(Cream, RoundedCornerShape(50)).border(1.dp, DoctorHairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(Color(PainBodyMapRules.heatColor(r)), CircleShape).border(1.dp, DoctorHairline, CircleShape)); Text("${PainBodyMapRules.label(site, hindi)} $r · ${PainBands.label(r, hindi)}", color = DoctorCardInk, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }
            }
            Text("${friendlyWhen(note.observedDate, note.observedTime, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.recalls, note.recall, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.respondents, note.respondent, hindi)} · ${note.author}", color = DoctorCardSub, fontSize = 14.sp, modifier = Modifier.testTag("symptom-card-when"))
            if (SymptomDiaryRules.loggedByFamily(note)) SymptomLoggedByFamilyChip(hindi, "symptom-card-family-${item.id}")
            val details = listOf(SymptomDiaryRules.ratingAnchors(note.scale, hindi).takeIf { note.scale == SymptomDiaryRules.WELLBEING_SCALE }.orEmpty(), note.startedOn.takeIf { it.isNotBlank() }?.let { "${t("Started", "शुरू हुआ")}: $it" }.orEmpty(), note.painLocation,
                note.painBodySites.takeIf { it.isNotEmpty() && note.painBodyRatings.isEmpty() }?.joinToString(prefix = t("Pain areas: ", "दर्द की जगह: ")) { PainBodyMapRules.label(it, hindi) }.orEmpty(), note.painTriggers.joinToString { SymptomDiaryRules.label(SymptomDiaryRules.triggers, it, hindi) },
                note.selfReportedChange.takeIf { it.isNotBlank() }?.let { "${t("Reported change", "उनके मुताबिक बदलाव")}: ${SymptomDiaryRules.label(SymptomDiaryRules.changes, it, hindi)}" }.orEmpty()).filter(String::isNotBlank)
            if (details.isNotEmpty()) Text(details.joinToString("\n"), fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub)
            if (note.note.isNotBlank()) Text(note.note, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = DoctorCardInk)
        } else Text(SymptomNoteCodec.readable(item.detail, hindi), style = MaterialTheme.typography.bodyMedium)
        val comparison = remember(entries, item, note) { note?.let { SymptomDiaryRules.comparison(entries, item, it) } }
        if (comparison != null) {
            // One plain line against the last comparable note: "Last time 6/10 (Yesterday, 4:01 PM) · Now 4/10 ↓ lower".
            val previous = SymptomNoteCodec.decode(comparison.previous.detail)!!
            val change = when { comparison.difference > 0 -> t("↑ higher", "↑ ज़्यादा"); comparison.difference < 0 -> t("↓ lower", "↓ कम"); else -> t("→ same", "→ उतना ही") }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("${t("Last time", "पिछली बार")} ${SymptomDiaryRules.ratingLabel(previous.rating, hindi)} (${friendlyWhen(previous.observedDate, previous.observedTime, hindi)}) · ${t("Now", "अब")} ${SymptomDiaryRules.ratingLabel(note?.rating, hindi)} $change", style = MaterialTheme.typography.bodyMedium, color = DoctorCardInk, modifier = Modifier.testTag("symptom-card-change"))
        }
        // One idea per card, few buttons: See graph and Update rating as buttons; Pain has gone stays one tap for pain;
        // View on body, Edit and Remove sit in one clearly named "More" list.
        if (note != null) Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showingTrend = true }, shape = RoundedCornerShape(14.dp), border = BorderStroke(2.dp, DoctorAccent), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("symptom-trend-${item.id}")) { Icon(Icons.Default.Timeline, null, Modifier.size(18.dp), tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("See graph", "ग्राफ़ देखें"), color = RustText, fontWeight = FontWeight.SemiBold, style = SymptomWords) }
            if (canEdit) Button(onClick = { followingUp = true }, enabled = !busy, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Color.White), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("symptom-followup-${item.id}")) { Text(t("Update rating", "नया अंक लिखें"), fontWeight = FontWeight.SemiBold, style = SymptomWords) }
        }
        if (note != null && (canEdit || isPain)) Row(Modifier.fillMaxWidth().testTag("symptom-card-actions-${item.id}"), verticalAlignment = Alignment.CenterVertically) {
            // Pain that went away is a new 0 note at the same spots, so the trend drops to 0 instead of keeping the old number.
            if (canEdit && isPain && (PainBodyMapRules.overall(note.painBodyRatings) ?: note.rating ?: 0) > 0) TextButton(onClick = { painGone = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-pain-gone-${item.id}")) { Text(t("Pain has gone (0)", "दर्द चला गया (0)"), color = RustText, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.weight(1f))
            Box {
                var more by remember { mutableStateOf(false) }
                TextButton(onClick = { more = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-more-${item.id}")) { Text(t("More", "और"), color = DoctorCardInk, fontWeight = FontWeight.SemiBold); Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp), tint = DoctorCardInk) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    if (isPain) DropdownMenuItem(text = { Text(t("View on body", "शरीर पर देखें")) }, leadingIcon = { Icon(Icons.Default.AccessibilityNew, null) }, onClick = { more = false; showingPainHistory = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-pain-history-${item.id}"))
                    if (canEdit) DropdownMenuItem(text = { Text(t("Edit", "बदलें")) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, enabled = !busy, onClick = { more = false; editing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-edit-${item.id}"))
                    if (canEdit) DropdownMenuItem(text = { Text(t("Remove", "हटाएँ"), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, enabled = !busy, onClick = { more = false; removing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("symptom-remove-${item.id}"))
                }
            }
        }
    } } }
    if (editing) SymptomNoteEditor(vm, hindi, initial = item, onClose = { editing = false })
    if (followingUp) SymptomNoteEditor(vm, hindi, followup = item, onClose = { followingUp = false })
    if (showingTrend && note != null) SymptomHistorySheet(entries, item, note, hindi, onClose = { showingTrend = false })
    if (painGone && note != null) AlertDialog(onDismissRequest = { painGone = false }, title = { Text(t("Has the pain gone?", "क्या दर्द चला गया?")) },
        text = { Text(note.painBodySites.joinToString { PainBodyMapRules.label(it, hindi) }.ifBlank { SymptomDiaryRules.symptomLabel(note.symptom, hindi) }) },
        confirmButton = { TextButton(onClick = { painGone = false; vm.saveSymptomNote(item.personId, newId(), SymptomDiaryRules.painGone(note), null) }, enabled = !busy, modifier = Modifier.testTag("symptom-pain-gone-confirm-${item.id}")) { Text(t("Save 0", "0 सहेजें")) } },
        dismissButton = { TextButton(onClick = { painGone = false }) { Text(t("Keep", "रखें")) } })
    if (showingPainHistory && note != null) PainBodyHistorySheet(entries, item.personId, hindi, initialId = item.id, onClose = { showingPainHistory = false })
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text(t("Remove this note?", "यह नोट हटाएँ?")) }, text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { vm.remove(item); removing = false }, enabled = !busy) { Text(t("Remove", "हटाएँ"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(t("Keep", "रखें")) } })
}

@Composable fun SymptomNoteEditor(vm: CareModel, hindi: Boolean, initial: CareItem? = null, followup: CareItem? = null, startSymptom: String? = null, onClose: () -> Unit) {
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
        viewRole = vm.viewRole.value, startSymptom = startSymptom, onSave = { id, value -> vm.saveSymptomNote(capturedPerson, id, value, expectedToken, onClose) }, onClose = onClose)
}

/** The same native form can be checked with isolated callback fixtures, without touching accounts or real records. */
@Composable fun SymptomNoteForm(personId: String, personName: String, entries: List<CareItem>, hindi: Boolean,
    initial: CareItem? = null, busy: Boolean = false, canEdit: Boolean = true, actionError: String? = null,
    followup: CareItem? = null, sharedOnline: Boolean = false, viewRole: String = "", startSymptom: String? = null, onSave: (String, SymptomNote) -> Unit, onClose: () -> Unit) {
    val original = remember(initial?.id) { initial?.let { SymptomNoteCodec.decode(it.detail) } }
    val largeText = LocalDensity.current.fontScale > 1.15f
    val prior = remember(followup?.id) { followup?.let { SymptomNoteCodec.decode(it.detail) } }
    val seed = remember(initial?.id, followup?.id) { original ?: prior?.let(SymptomDiaryRules::followUp) }
    val id = rememberSaveable { initial?.id ?: newId() }
    val capturedPerson = rememberSaveable(id) { initial?.personId ?: personId }
    val context = LocalContext.current
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    var fullDetails by rememberSaveable(id) { mutableStateOf(prior == null || original != null) }
    // A tile on the tracker may already have chosen the symptom; only a brand-new note takes it.
    val started = startSymptom?.takeIf { initial == null && followup == null && it in SymptomDiaryRules.symptoms && it != "Other" }
    var symptom by rememberSaveable(id) { mutableStateOf(seed?.symptom ?: started.orEmpty()) }
    var otherSymptom by rememberSaveable(id) { mutableStateOf(seed?.symptom?.let { it !in SymptomDiaryRules.symptoms } ?: false) }
    var scale by rememberSaveable(id) { mutableStateOf(seed?.scale ?: started?.let(SymptomDiaryRules::scaleForSymptom) ?: SymptomDiaryRules.PERSONAL_SCALE) }
    var rating by rememberSaveable(id) { mutableIntStateOf(if (original == null) -2 else original.rating ?: -1) }
    // Mood: two separate scales; -2 = not answered yet, -1 = Not rated. Answering either one is enough to go on.
    var moodLow by rememberSaveable(id) { mutableIntStateOf(if (original == null) -2 else original.moodRatings["low-mood"] ?: -1) }
    var moodAnxiety by rememberSaveable(id) { mutableIntStateOf(if (original == null) -2 else original.moodRatings["anxiety"] ?: -1) }
    var observedDate by rememberSaveable(id) { mutableStateOf(seed?.observedDate ?: LocalDate.now().toString()) }
    var observedTime by rememberSaveable(id) { mutableStateOf(seed?.observedTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var recall by rememberSaveable(id) { mutableStateOf(seed?.recall ?: "now") }
    // A brand-new note starts with who answered last time for this person (shown on the "About this note" step, changeable).
    // An invited family member (viewRole "Family") starts on "Seen by family or carer" instead.
    val lastWho = remember(id) { if (initial == null && prior == null) SymptomDiaryRules.defaultReporter(viewRole, CareDailyFlow.lastReporter(entries, capturedPerson)) else null }
    var respondent by rememberSaveable(id) { mutableStateOf(seed?.respondent ?: lastWho?.first.orEmpty()) }
    var author by rememberSaveable(id) { mutableStateOf(seed?.author ?: lastWho?.second.orEmpty()) }
    // Decided once when the form opens, so sections never jump while answering.
    val reporterFirst = rememberSaveable(id) { respondent.isBlank() }
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
    // Steps: what -> (where, Pain only) -> how (one page, with the optional extras and Save). Quick follow-ups stay on a single short page.
    val quick = prior != null && initial == null && !fullDetails
    val steps = if (symptom == "Pain") listOf("what", "where", "how") else listOf("what", "how")
    var stepKey by rememberSaveable(id) { mutableStateOf(if (initial != null || prior != null) "how" else if (started != null) { if (started == "Pain") "where" else "how" } else "what") }
    val cur = if (stepKey in steps) stepKey else if (stepKey == "where" || stepKey == "more") "how" else steps.first()
    val pos = steps.indexOf(cur)
    val ratingMissing = ratingNow == -2
    val dateProblem = SymptomDiaryRules.error(value.copy(startedOn = "")) in setOf("Choose a valid date and time.", "The observation cannot be in the future.")
    val howValid = !ratingMissing && respondent in SymptomDiaryRules.respondents && recall in SymptomDiaryRules.recalls && value.author.trim().length in 1..120 && !dateProblem
    val stepValid = when (cur) {
        "what" -> symptom.trim().length in 1..80
        "how" -> howValid
        else -> true
    }
    // Pain: once a spot is rated and who/when are already known (remembered from last time), Save sits on the body step; "More" still opens the details step.
    val saveFromWhere = !quick && cur == "where" && spotsDrive && howValid
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
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Heading(t(if (prior != null && initial == null) "Update rating" else if (initial == null) "Log a symptom" else "Edit symptom note", if (prior != null && initial == null) "नया अंक लिखें" else if (initial == null) "लक्षण दर्ज करें" else "तबीयत का नोट बदलें"), 26)
                Text(personName, color = DoctorCardSub, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (!quick) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { steps.forEachIndexed { i, _ -> val bar by androidx.compose.animation.animateColorAsState(if (i <= pos) DoctorAccent else DoctorHairline, SaharaMotion.tween(SaharaMotion.ENTER_MS), label = "step-bar"); Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(bar)) } }
                    Text("${pos + 1} ${t("of", "/")} ${steps.size}", color = DoctorCardSub, fontSize = 14.sp, modifier = Modifier.testTag("symptom-step-indicator"))
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
                        if (location.isNotBlank() || value.painBodySites.isNotEmpty()) Text(listOf(location, value.painBodySites.joinToString { PainBodyMapRules.label(it, hindi) }).filter(String::isNotBlank).joinToString(" · "))
                        if (startedOn.isNotBlank()) Text("${t("Started", "शुरू हुआ")}: $startedOn")
                    }
                }
                if (!quick && cur == "what") SymptomSection(null, t("What would you like to note?", "किस बारे में नोट लिखना है?")) {
                    SymptomDiaryRules.symptoms.entries.chunked(if (largeText) 1 else 2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (en, _) -> SymptomTile(if (en == "Other") otherSymptom else !otherSymptom && symptom == en, SymptomDiaryRules.symptomLabel(en, hindi), en, "symptom-choice-$en", Modifier.weight(1f).fillMaxHeight()) {
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
                        SymptomPill(key in value.painTriggers, careTr(hindi, labels.first, labels.second), null, multi = true) { val next = value.painTriggers.toMutableSet(); if (!next.add(key)) next.remove(key); triggerValues = next.sorted().joinToString("|") }
                    } }
                }
                // The rating is the main question, so it comes first; who answered and the time frame follow
                // (they are remembered from the last note). With nothing remembered yet, they stay on top so Save is never blocked out of sight.
                val whoAndTimeFrame: @Composable () -> Unit = {
                if (!quick && cur == "how") SymptomSection(null, t("Who reported this?", "किसने बताया?")) {
                    DiaryChoices(SymptomDiaryRules.respondents, respondent, hindi) { respondent = it }
                    if (respondent.isNotBlank() && respondent != "patient") CareField(author, { author = it.take(120) }, t(if (respondent == "assisted-patient") "Written with help from" else "Name of the person who saw it", if (respondent == "assisted-patient") "लिखने में किसने मदद की" else "देखने वाले का नाम"), singleLine = true)
                }
                if (!quick && cur == "how") SymptomSection(null, t("Time frame", "समय अवधि")) {
                    DiaryChoices(SymptomDiaryRules.recalls, recall, hindi) { recall = it }
                }
                }
                if (reporterFirst) whoAndTimeFrame()
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
                    SymptomSection(null, careTr(hindi, label.first, label.second), SymptomDiaryRules.ratingAnchors(scale, hindi), accent = symptomColor(symptom)) {
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
                if ((quick || cur == "how") && !(symptom == "Pain" && !quick) && !isMood) SymptomSection(null, SymptomDiaryRules.ratingQuestion(scale, hindi), accent = symptomColor(symptom)) {
                    Text(SymptomDiaryRules.ratingAnchors(scale, hindi), fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, style = SymptomWords, modifier = Modifier.testTag("symptom-rating-anchors"))
                    RatingReadout(rating, hindi, scale)
                    listOf(0..5, 6..10).forEach { range ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            range.forEach { number -> RatingButton(number, rating == number, Modifier.weight(1f).aspectRatio(1f)) { rating = number } }
                            if (range.count() < 6) Spacer(Modifier.weight(1f))
                        }
                    }
                    SymptomPill(rating == -1, t("Not rated / unable to rate", "अंक नहीं बताया / बता नहीं सकते"), "symptom-unrated", Modifier.fillMaxWidth()) { rating = -1; change = "" }
                }
                if (quick || cur == "how") SymptomSection(null, t("When was this?", "यह कब हुआ?")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { val d = LocalDate.parse(observedDate); DatePickerDialog(context, { _, y, m, day -> observedDate = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(observedDate, color = DoctorCardInk) }
                        OutlinedButton(onClick = { val time = LocalTime.parse(observedTime); TimePickerDialog(context, { _, h, m -> observedTime = "%02d:%02d".format(h, m) }, time.hour, time.minute, true).show() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(observedTime, color = DoctorCardInk) }
                    }
                }
                if (quick || cur == "how") {
                    if (comparison != null) {
                        val previous = SymptomNoteCodec.decode(comparison.previous.detail)!!
                        SymptomSection(null, t("Compared with that note (optional)", "उस नोट के मुकाबले (वैकल्पिक)")) {
                            Surface(shape = RoundedCornerShape(14.dp), color = DoctorStatusGoodBg, modifier = Modifier.fillMaxWidth()) {
                                Text("${t("Previous matching note", "पिछला समान नोट")}: ${previous.observedDate} ${previous.observedTime} · ${previous.rating}/10", color = DoctorCardInk, fontSize = 15.sp, modifier = Modifier.padding(12.dp).testTag("symptom-previous-reference"))
                            }
                            DiaryChoices(SymptomDiaryRules.changes, change, hindi) { change = if (change == it) "" else it }
                        }
                    }
                }
                if (!reporterFirst) whoAndTimeFrame()
                if (!quick && cur == "how") SymptomSection(null, t("Anything else? (optional)", "कुछ और? (वैकल्पिक)")) {
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
                (localError.takeIf { it.isNotBlank() } ?: actionError)?.let { Text(if (hindi && it == SymptomDiaryRules.SAVE_CONFLICT) "यह नोट बदल गया है या हटा दिया गया है। आपके जवाब यहीं हैं। सहेजने से पहले यह फ़ॉर्म बंद करके मौजूदा नोट फिर खोलें।" else if (hindi) "नोट सहेजा नहीं गया। जवाब यहीं हैं। तारीख, नाम और चुने हुए जवाब जाँचें।" else careTr(false, it, it), color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (!quick && !stepValid && cur == "how" && ratingMissing) Text(t("Choose a rating or Not rated.", "अंक चुनें या अंक नहीं बताया चुनें।"), color = DoctorCardSub, fontSize = 14.sp)
                val back: @Composable (Modifier) -> Unit = { m -> OutlinedButton(onClick = goBack, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, DoctorCardSub.copy(alpha = .5f)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = m.heightIn(min = 56.dp).testTag("symptom-step-back")) { Text(if (quick || pos <= 0) t("Cancel", "रद्द करें") else t("Back", "पीछे"), color = DoctorCardInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = SymptomWords) } }
                val save: @Composable (Modifier) -> Unit = { m -> Button(onClick = saveNow, enabled = !busy && canEdit && personId == capturedPerson && (quick || saveFromWhere || stepValid), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp), modifier = m.heightIn(min = 56.dp).testTag("symptom-save")) {
                    // Saving shows a small spinner in the button itself; the sheet closes when the note is stored.
                    AnimatedContent(busy, transitionSpec = { SaharaMotion.crossfade() }, label = "save-state") { saving ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (saving) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(if (saving) t("Saving…", "सहेज रहे हैं…") else t("Save note", "नोट सहेजें"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = SymptomWords)
                        }
                    }
                } }
                val next: @Composable (Modifier, Boolean) -> Unit = { m, primary -> if (primary) Button(onClick = { stepKey = steps[pos + 1] }, enabled = stepValid, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp), modifier = m.heightIn(min = 56.dp).testTag("symptom-step-next")) { Text(t("Next", "आगे"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    else OutlinedButton(onClick = { stepKey = steps[pos + 1] }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, DoctorAccent), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = m.heightIn(min = 56.dp).testTag("symptom-step-next")) { Text(t("More", "और"), color = RustText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) } }
                if (largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (quick || pos == steps.lastIndex || saveFromWhere) save(Modifier.fillMaxWidth())
                    if (saveFromWhere) next(Modifier.fillMaxWidth(), false) else if (!(quick || pos == steps.lastIndex)) next(Modifier.fillMaxWidth(), true)
                    back(Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    back(Modifier.weight(1f))
                    if (saveFromWhere) next(Modifier.weight(1f), false)
                    if (quick || pos == steps.lastIndex || saveFromWhere) save(Modifier.weight(2f)) else next(Modifier.weight(2f), true)
                }
            }
        }
    }
}

@Composable private fun DiaryChoices(options: Map<String, Pair<String, String>>, selected: String, hindi: Boolean, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { (key, label) -> SymptomPill(selected == key, careTr(hindi, label.first, label.second), "diary-choice-$key", onClick = { onSelect(key) }) } }
}

private const val SymptomNoonMs = 43_200_000L

/** Treatment days (chemo, radiotherapy) as dashed markers with a short label, placed at midday like the daily points. */
internal fun symptomTreatmentMarkers(entries: List<CareItem>, personId: String): List<ChartMarker> = TreatmentDayRules.days(entries, personId).map {
    ChartMarker((careEpoch(careDay(it.item.due)) + SymptomNoonMs).toDouble(), if (it.category == "Chemotherapy") DoctorStatusWarn else Ink, TreatmentDayRules.label(it.category))
}

/**
 * A 0–10 symptom, mood or body-part trend from the first note on: one point per day (that day's highest number) joined by a line,
 * coloured by band, with 1 week / 1 month / 3 months / All chips and treatment days marked. Same first arguments as DayBarChart,
 * so the doctor screens can swap to it directly.
 */
@Composable fun SymptomHistoryChart(name: String, points: List<DatedRating>, hindi: Boolean, tag: String, markers: List<ChartMarker> = emptyList(), height: androidx.compose.ui.unit.Dp = 180.dp) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val today = LocalDate.now()
    val days = remember(points) { SymptomDiaryRules.dailyHighs(points) }
    var range by rememberSaveable(tag) { mutableStateOf(ChartRange.ALL.name) }
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontFamily = SaharaTitleFont, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = DoctorCardInk, style = SymptomWords, modifier = Modifier.weight(1f))
            CareChartInfoButton(name, t("Each point is the highest number noted that day. 0 = none, 10 = worst.", "हर बिंदु उस दिन का सबसे ऊँचा अंक है। 0 = बिलकुल नहीं, 10 = सबसे ज़्यादा।"), tag)
        }
        if (days.isEmpty()) ChartEmpty(hindi, tag) else {
            val now = System.currentTimeMillis()
            val chartPoints = days.map { (day, v) -> ChartPoint((careEpoch(day) + SymptomNoonMs).toDouble(), v.toDouble(), ChartBands.color(v)) }
            val current = ChartRange.valueOf(range).takeIf { it in ChartRangeRules.choices(chartPoints, now) } ?: ChartRange.ALL
            val start = ChartRangeRules.start(current, now)
            val shownDays = days.filter { (day, _) -> start == null || careEpoch(day) + SymptomNoonMs >= start }
            val shownDates = shownDays.map { it.first.toString() }.toSet()
            val shownPoints = points.filter { it.date in shownDates }
            val window = TrendWindow(shownDays.map { (day, v) -> DayValue(day, v) }, lastWeek = false)
            val summary = TrendChartRules.summary(window, shownPoints, hindi, today)
            if (summary.isNotBlank()) Text(summary, fontSize = 15.sp, color = DoctorCardInk, modifier = Modifier.testTag("$tag-summary"))
            CareTimeLineChart(
                points = chartPoints, yMin = 0.0, yMax = 10.0, yTicks = listOf(0.0, 5.0, 10.0), yText = { it.toInt().toString() },
                description = TrendChartRules.description(name, summary, window, hindi, today), tag = tag, height = height,
                lineColor = DoctorCardInk.copy(alpha = 0.55f), markers = markers, yTitle = t("Score 0–10", "अंक 0–10"), xTitle = t("Date", "तारीख"), pointText = { it.toInt().toString() }, latestText = { it.toInt().toString() },
                bands = listOf(ChartBand(0.0, 3.5, ChartBands.Mild), ChartBand(3.5, 6.5, ChartBands.Moderate), ChartBand(6.5, 10.0, ChartBands.Severe)),
                range = current, onRangeChange = { range = it.name }, now = now,
            )
            // Every entry in the chosen range, newest first: when and who, then its number as a band-coloured pill (no show/hide toggle).
            Text("${t("Entries", "नोट")} · ${shownPoints.size}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.padding(top = 4.dp).testTag("$tag-entries"))
            shownPoints.asReversed().forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(color = DoctorHairline)
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${friendlyWhen(p.date, p.time, hindi)} · ${SymptomDiaryRules.label(SymptomDiaryRules.respondents, p.respondent, hindi)}", fontSize = 14.sp, color = DoctorCardSub, style = SymptomWords, modifier = Modifier.weight(1f))
                    val band = ChartBands.color(p.value)
                    Text("${p.value} · ${ChartBands.word(p.value, hindi)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = readableOn(band), modifier = Modifier.background(band, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}

/** "See graph" from a symptom card: the history from the first note, one chart per question (Mood shows its two scales). */
@Composable fun SymptomHistorySheet(entries: List<CareItem>, current: CareItem, value: SymptomNote, hindi: Boolean, onClose: () -> Unit) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val name = SymptomDiaryRules.symptomLabel(value.symptom, hindi)
    val markers = remember(entries, current.personId) { symptomTreatmentMarkers(entries, current.personId) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { SymptomIconChip(value.symptom, 48.dp); Box(Modifier.weight(1f)) { Heading(name, 27) } }
            ChartLegend(hindi)
            if (value.symptom == SymptomDiaryRules.MOOD) SymptomDiaryRules.moodShort.forEach { (key, label) ->
                val points = remember(entries, current.personId, key) { SymptomDiaryRules.moodSeries(entries, current.personId, key) }
                SymptomHistoryChart(careTr(hindi, label.first, label.second), points, hindi, "mood-trend-$key", markers)
            } else {
                val points = remember(entries, current.personId, value.symptom) { SymptomDiaryRules.symptomSeries(entries, current.personId, value.symptom) }
                SymptomHistoryChart(name, points, hindi, "symptom-trend", markers)
                if (value.symptom.trim() == "Pain") SymptomPartTrendPicker(entries, current.personId, hindi, preferred = value.painBodyRatings.maxByOrNull { it.value }?.key ?: value.painBodySites.firstOrNull())
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) }
        }
    }
}

/** Pick one body part; only that part's chart is shown, so parts never overlap on one chart. */
@Composable internal fun SymptomPartTrendPicker(entries: List<CareItem>, personId: String, hindi: Boolean, preferred: String? = null) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val parts = remember(entries, personId) { SymptomDiaryRules.painPartsWithHistory(entries, personId) }
    if (parts.size < 2) return
    var chosen by rememberSaveable(personId) { mutableStateOf(preferred?.takeIf { it in parts } ?: parts.first()) }
    val part = chosen.takeIf { it in parts } ?: parts.first()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = DoctorHairline)
        Text(t("Pain in one body part", "शरीर के एक हिस्से का दर्द"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
        // Each chip carries a dot in the colour of that part's latest number, so the worst part stands out before it is opened.
        val latest = remember(entries, personId, parts) { parts.associateWith { SymptomDiaryRules.painPartSeries(entries, personId, it).lastOrNull()?.value } }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { id -> FilterChip(id == part, { chosen = id }, label = { Text(PainBodyMapRules.label(id, hindi) + (latest[id]?.let { " · $it" } ?: ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }, shape = CircleShape,
                leadingIcon = if (latest[id] != null) { { Box(Modifier.size(12.dp).background(ChartBands.color(latest.getValue(id)!!), CircleShape)) } } else null,
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = id == part, borderColor = DoctorCardSub.copy(alpha = .45f), selectedBorderColor = DoctorAccent, borderWidth = 1.5.dp, selectedBorderWidth = 2.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = lerp(Paper, DoctorAccent, .14f), selectedLabelColor = DoctorCardInk), modifier = Modifier.heightIn(min = 48.dp).testTag("pain-part-chip-$id")) }
        }
        AnimatedContent(part, transitionSpec = { SaharaMotion.crossfade() }, label = "part trend") { shownPart -> SymptomPartTrend(entries, personId, shownPart, hindi, compact = true) }
    }
}

/** One body part's own pain from its first note on; a "Pain has gone" note shows as the line dropping to 0. */
@Composable internal fun SymptomPartTrend(entries: List<CareItem>, personId: String, site: String, hindi: Boolean, compact: Boolean = false) {
    val points = remember(entries, personId, site) { SymptomDiaryRules.painPartSeries(entries, personId, site) }
    val markers = remember(entries, personId) { symptomTreatmentMarkers(entries, personId) }
    val part = PainBodyMapRules.label(site, hindi)
    SymptomHistoryChart(careTr(hindi, "$part pain", "$part में दर्द"), points, hindi, "pain-part-trend-$site", markers, height = if (compact) 150.dp else 180.dp)
}

/**
 * A friendly round face for "How was your day?": a soft warm head, dot eyes, rosy cheeks and a mouth that bends smoothly from a
 * gentle frown (0) through a straight line (0.5) to a smile (1). Nothing sharp: no brows and no tall eyes.
 */
@Composable fun CareMoodFace(score: Float, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val v = score.coerceIn(0f, 1f)
        val w = size.minDimension
        val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val r = w * .46f
        drawCircle(lerp(Color(0xFFFFE3C2), Color(0xFFFFE08A), v), r, c)
        drawCircle(Color(0xFFE8B86A).copy(alpha = .55f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(w * .018f))
        val ink = Ink.copy(alpha = .85f)
        // Eyes: round dots that squint a little on a good day.
        val eyeY = c.y - r * .12f; val eyeDx = r * .34f; val eyeR = r * .085f; val squash = 1f - .25f * v
        listOf(-1f, 1f).forEach { side ->
            drawOval(ink, androidx.compose.ui.geometry.Offset(c.x + side * eyeDx - eyeR, eyeY - eyeR * squash), androidx.compose.ui.geometry.Size(eyeR * 2, eyeR * 2 * squash))
        }
        // Cheeks, rosier on a good day.
        listOf(-1f, 1f).forEach { side -> drawCircle(Color(0xFFF4978E).copy(alpha = .18f + .22f * v), r * .13f, androidx.compose.ui.geometry.Offset(c.x + side * r * .56f, c.y + r * .14f)) }
        // Mouth: one smooth curve whose bend follows the score.
        val bend = (v - .5f) * r * .5f
        val y = c.y + r * .36f
        val mouth = androidx.compose.ui.graphics.Path().apply { moveTo(c.x - r * .3f, y); quadraticBezierTo(c.x, y + bend * 2f, c.x + r * .3f, y) }
        drawPath(mouth, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * .035f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}
