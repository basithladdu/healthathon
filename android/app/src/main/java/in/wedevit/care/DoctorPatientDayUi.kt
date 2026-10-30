package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class DoctorReportedMood(val title: String) { GOOD("A good day"), OKAY("An okay day"), HARD("A hard day") }
internal fun doctorMood(title: String) = DoctorReportedMood.entries.firstOrNull { it.title == title }
internal fun doctorCheckIns(entries: List<CareItem>, personId: String, now: Long) = entries.filter {
    it.personId == personId && it.kind == "checkin" && it.created in 1..now && !CareEntryCodec.isStructured(it.detail) && !DistressCodec.isStructured(it.detail)
}.sortedWith(compareByDescending<CareItem> { it.created }.thenBy { it.id })
internal data class DoctorMedicineCounts(val total: Int, val markedToday: Int) { val notMarked get() = total - markedToday }
internal fun doctorMedicineCounts(entries: List<CareItem>, personId: String, today: LocalDate): DoctorMedicineCounts {
    val medicines = entries.filter { it.personId == personId && it.kind == "medicine" && !CareEntryCodec.isStructured(it.detail) }
    return DoctorMedicineCounts(medicines.size, medicines.count { it.doneOn == today.toString() })
}
internal fun isDoctorStructuredVisit(item: CareItem) = item.kind == "visit" && CareEntryCodec.decode(item.detail)?.let { it.type == "schedule" && it.category == "Visit" } == true
internal fun doctorUpcomingVisits(entries: List<CareItem>, personId: String, now: Long, zone: ZoneId = ZoneId.systemDefault()) = entries.filter {
    val upcoming = it.due == 0L || if (CareEntryCodec.decode(it.detail)?.timed == true) it.due >= now else Instant.ofEpochMilli(it.due).atZone(zone).toLocalDate() >= Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    it.personId == personId && it.kind == "visit" && upcoming &&
        (!CareEntryCodec.isStructured(it.detail) || isDoctorStructuredVisit(it)) && CareEntryCodec.decode(it.detail)?.status !in listOf("cancelled", "completed")
}.sortedBy { if (it.due == 0L) Long.MAX_VALUE else it.due }
private fun doctorRecordedTime(at: Long, zone: ZoneId) = if (at <= 0) "Date not recorded" else DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a").withZone(zone).format(Instant.ofEpochMilli(at))

@Composable internal fun DoctorPatientDay(vm: CareModel, person: Person, onOverview: () -> Unit, onCheckIns: () -> Unit, onMedicines: () -> Unit, onCalendar: () -> Unit, onLabs: () -> Unit, onCareTeam: () -> Unit, onAddVisit: () -> Unit, onReadNote: (CareVersion) -> Unit) {
    val entries by vm.items.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    DoctorPatientDayContent(person, entries, versions, canEdit, System.currentTimeMillis(), ZoneId.systemDefault(), onOverview, onCheckIns, onMedicines, onCalendar, onLabs, onCareTeam, onAddVisit, onReadNote, careTeamSummary = { DoctorCareTeamSummary(vm, person.id, onCareTeam) })
}

/** Doctor-only overview: the Patient/Family Today screen is deliberately not reused or restyled. */
@Composable internal fun DoctorPatientDayContent(person: Person, entries: List<CareItem>, versions: List<CareVersion>, canEdit: Boolean, now: Long, zone: ZoneId, onOverview: () -> Unit, onCheckIns: () -> Unit, onMedicines: () -> Unit, onCalendar: () -> Unit, onLabs: () -> Unit, onCareTeam: () -> Unit, onAddVisit: () -> Unit, onReadNote: (CareVersion) -> Unit, careTeamSummary: (@Composable () -> Unit)? = null) { val t = careText();
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val checks = doctorCheckIns(entries, person.id, now)
    val todayCheck = checks.firstOrNull { Instant.ofEpochMilli(it.created).atZone(zone).toLocalDate() == today }
    val visits = doctorUpcomingVisits(entries, person.id, now, zone)
    val latestNote = versions.filter { it.personId == person.id && it.published in 1..now }.maxByOrNull { it.published }
    val medicines = doctorMedicineCounts(entries, person.id, today)
    val questions = latestNote?.questions.orEmpty().lines().count { it.isNotBlank() }
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-patient-day"), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Heading(t("Care today", "आज की देखभाल"), 29); Text(person.name, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk) }; Text(today.format(DateTimeFormatter.ofPattern("EEE, d MMM")), fontSize = 13.sp, color = DoctorCardSub) } }
        item { DoctorGlanceRow(visits.firstOrNull(), zone, medicines, questions, onCalendar, onMedicines) { latestNote?.let(onReadNote) } }
        item { DoctorDayTopCards(todayCheck, checks.firstOrNull(), zone, onOverview, onCheckIns, onCalendar, onCareTeam, onLabs) }
        item { DistressHistoryCard(entries, person.id) }
        careTeamSummary?.let { summary -> item { summary() } }
        latestNote?.let { note -> item { Card(onClick = { onReadNote(note) }, colors = CardDefaults.cardColors(containerColor = DoctorCardReport), border = BorderStroke(1.dp, DoctorHairline), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("doctor-latest-note")) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(t("Latest care note", "नया केयर नोट"), fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk); Text(doctorRecordedTime(note.published, zone), fontSize = 13.sp, color = DoctorCardSub); Text(ReviewWorkflow.label(note), fontSize = 13.sp, color = DoctorCardSub); Text(note.topics.ifBlank { t("Read the recorded discussion.", "दर्ज चर्चा पढ़ें।") }, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk, maxLines = 3); Text(t("Read exact version ${note.version}", "संस्करण ${note.version} पढ़ें"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent, modifier = Modifier.heightIn(min = 32.dp)) } } } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(t("Up next", "आगे क्या है"), fontSize = 21.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), fontFamily = SaharaTitleFont, color = DoctorCardInk); TextButton(onClick = onAddVisit, enabled = canEdit, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-add-visit")) { Icon(Icons.Default.Add, null); Text(t("Add visit", "विज़िट जोड़ें")) } }; if (!canEdit) Text(t("Read-only access. A permitted editor must add a visit.", "सिर्फ़ पढ़ने की पहुँच। विज़िट अनुमति वाला व्यक्ति ही जोड़ सकता है।"), fontSize = 13.sp, color = DoctorCardSub) }
        if (visits.isEmpty()) item { SurfaceCard { Text(t("No upcoming visits recorded", "आगे की कोई विज़िट दर्ज नहीं"), fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk); Text(t("Add a visit to plan the next conversation.", "अगली बातचीत तय करने के लिए विज़िट जोड़ें।"), fontSize = 13.sp, color = DoctorCardSub) } }
        items(visits.take(3), key = { it.id }) { visit -> DoctorVisitSummary(visit, zone, onCalendar) }
    }
}

/** Three tappable at-a-glance tiles built only from recorded entries; stacks at large text. */
@Composable private fun DoctorGlanceRow(nextVisit: CareItem?, zone: ZoneId, medicines: DoctorMedicineCounts, questions: Int, onVisit: () -> Unit, onMedicines: () -> Unit, onQuestions: () -> Unit) { val t = careText();
    val visitValue = nextVisit?.let { v -> if (v.due > 0) Instant.ofEpochMilli(v.due).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM")) else t("Date not recorded", "तारीख़ दर्ज नहीं") } ?: t("None recorded", "कुछ दर्ज नहीं")
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> DoctorGlanceTile(t("Next visit", "अगली विज़िट"), visitValue, nextVisit?.title ?: t("No upcoming visit", "आगे कोई विज़िट नहीं"), DoctorCardOverview, onVisit, m.testTag("doctor-glance-visit")) },
        { m -> DoctorGlanceTile(t("Medicines today", "आज की दवाइयाँ"), if (medicines.total == 0) t("None recorded", "कुछ दर्ज नहीं") else "${medicines.markedToday} of ${medicines.total}", if (medicines.total == 0) t("No medicines listed", "कोई दवा दर्ज नहीं") else t("marked as taken", "ली गई बताई"), DoctorCardReport, onMedicines, m.testTag("doctor-glance-medicines")) },
        { m -> DoctorGlanceTile(t("Open questions", "खुले सवाल"), "$questions", if (questions == 1) t("in the latest care note", "नए केयर नोट में") else t("in the latest care note", "नए केयर नोट में"), DoctorCardReport, onQuestions, m.testTag("doctor-glance-questions")) },
    )
    if (LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
    else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) } }
}

@Composable private fun DoctorGlanceTile(label: String, value: String, note: String, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, color = color, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardSub)
            Text(value, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk)
            Text(note, fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint, maxLines = 2)
        }
    }
}

/** A full-width overview sits above a wider report and compact tools; narrow layouts stack. */
@Composable internal fun DoctorDayTopCards(todayCheck: CareItem?, lastCheck: CareItem?, zone: ZoneId, onOverview: () -> Unit, onCheckIns: () -> Unit, onCalendar: () -> Unit, onCareTeam: () -> Unit, onLabs: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("doctor-day-bento")) {
        val stacked = maxWidth < 350.dp * fontScale.coerceAtLeast(1f) || fontScale > 1.15f
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DoctorOverviewBentoCard(Modifier.fillMaxWidth(), onOverview)
            if(stacked) {
                DoctorConditionBentoCard(todayCheck, lastCheck, zone, false, Modifier.fillMaxWidth(), onCheckIns)
                DoctorToolsBentoCard(false, Modifier.fillMaxWidth(), onCalendar, onCareTeam, onLabs)
            } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DoctorConditionBentoCard(todayCheck, lastCheck, zone, true, Modifier.weight(1.5f).fillMaxHeight(), onCheckIns)
                DoctorToolsBentoCard(true, Modifier.weight(1f).fillMaxHeight(), onCalendar, onCareTeam, onLabs)
            }
        }
    }
}

@Composable private fun DoctorOverviewBentoCard(modifier: Modifier, onOpen: () -> Unit) { val t = careText();
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = DoctorCardOverview), shape = RoundedCornerShape(20.dp), modifier = modifier.testTag("doctor-care-overview")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Care overview", "देखभाल का सार"), fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                Text(t("Care story and next steps", "देखभाल की कहानी और अगले क़दम"), fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(t("Read overview", "सार पढ़ें"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = DoctorAccent) }
            }
            Image(saharaImage(R.drawable.care_notes_illustration, 72.dp), null, Modifier.size(72.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable private fun DoctorConditionBentoCard(todayCheck: CareItem?, lastCheck: CareItem?, zone: ZoneId, compact: Boolean, modifier: Modifier, onOpen: () -> Unit) { val t = careText();
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = DoctorCardProgress), shape = RoundedCornerShape(20.dp), modifier = modifier.heightIn(min=if(compact)152.dp else 0.dp).testTag("doctor-condition-today")) {
        Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Image(saharaImage(R.drawable.wellbeing_checkin_illustration, 160.dp), null, Modifier.size(160.dp).testTag("doctor-condition-illustration"), contentScale = ContentScale.Fit, alpha = .15f)
        }
        Column(Modifier.fillMaxWidth().padding(if(compact) 14.dp else 18.dp).testTag("doctor-condition-copy"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if(compact) t("Reported condition", "बताई गई हालत") else t("Today's reported condition", "आज की बताई गई हालत"), modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Start,fontSize = if(compact)18.sp else 20.sp, lineHeight = if(compact)22.sp else 25.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk)
            Column(Modifier.fillMaxWidth().testTag("doctor-condition-body"),verticalArrangement=Arrangement.spacedBy(4.dp),horizontalAlignment=Alignment.Start) {
            Text(todayCheck?.title ?: if(compact) t("No update today", "आज कोई अपडेट नहीं") else t("No update reported today", "आज कोई अपडेट नहीं बताया गया"), modifier=Modifier.testTag("doctor-condition-mood"),textAlign=TextAlign.Start,fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = todayCheck?.let { doctorMood(it.title) }?.let { doctorMoodColors(it).first } ?: DoctorCardInk)
            Text(todayCheck?.let { if(compact) DateTimeFormatter.ofPattern("h:mm a").withZone(zone).format(Instant.ofEpochMilli(it.created)) else doctorRecordedTime(it.created, zone) } ?: lastCheck?.let { if(compact) "Last: ${DateTimeFormatter.ofPattern("d MMM").withZone(zone).format(Instant.ofEpochMilli(it.created))}" else t("Last report: ${doctorRecordedTime(it.created, zone)}", "पिछली सूचना: ${doctorRecordedTime(it.created, zone)}") } ?: t("No check-ins yet", "अभी कोई हाल नहीं बताया"),modifier=Modifier.testTag("doctor-condition-time"),textAlign=TextAlign.Start, fontSize = 13.sp, color = DoctorCardSub)
            Text(if(compact) t("Patient / family report", "मरीज़ / परिवार की सूचना") else t("Reported by the patient or family.", "मरीज़ या परिवार ने बताया।"),modifier=Modifier.testTag("doctor-condition-source"),textAlign=TextAlign.Start, fontSize = 12.sp, color = DoctorCardFaint)
            }
            if(!compact) Text(t("View dated history", "तारीख़वार इतिहास देखें"), fontSize = 13.sp, color = DoctorAccent, fontWeight = FontWeight.SemiBold)
        }
        }
    }
}

/** Labelled shortcut rows (icon + name), each a 48dp-high target; tag and content description are unchanged. */
@Composable private fun DoctorToolsBentoCard(compact: Boolean, modifier: Modifier, onCalendar: () -> Unit, onCareTeam: () -> Unit, onLabs: () -> Unit) {
    Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.testTag("doctor-care-tools")) {
        val careHindiNow = careHindi()
        val shortcuts = listOf(Triple("Calendar", Icons.Default.CalendarMonth, onCalendar), Triple("Care team", Icons.Default.Groups, onCareTeam), Triple("Lab history", Icons.Default.Science, onLabs))
        Column(Modifier.fillMaxWidth().heightIn(min = if(compact) 152.dp else 0.dp).padding(8.dp), verticalArrangement = Arrangement.Center) {
            shortcuts.forEach { (label, icon, action) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = action).padding(horizontal = 8.dp).testTag("doctor-shortcut-$label").semantics(mergeDescendants = true) { contentDescription = doctorToolLabel(label, careHindiNow) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, null, Modifier.size(22.dp), tint = DoctorAccent)
                    Text(doctorToolLabel(label, careHindiNow), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                }
            }
        }
    }
}

@Composable internal fun DoctorVisitSummary(item: CareItem, zone: ZoneId = ZoneId.systemDefault(), onOpen: () -> Unit) { val t = careText();
    val entry = CareEntryCodec.decode(item.detail)
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = DoctorCardReport), border = BorderStroke(1.dp, DoctorHairline), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).testTag("doctor-visit-${item.id}")) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(item.title, fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text(if (item.due > 0) { if (entry?.timed == true) doctorRecordedTime(item.due, zone) else Instant.ofEpochMilli(item.due).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy")) } else t("Date not recorded", "तारीख़ दर्ज नहीं"), fontSize = 16.sp); if (entry != null) { Text(t("Doctor: ", "डॉक्टर: ") + entry.sender.ifBlank { t("Not recorded", "दर्ज नहीं") }, fontSize = 16.sp); if (entry.location.isNotBlank()) Text(entry.location); if (DoctorVisitDetails.readable(entry.detail).isNotBlank()) Text(DoctorVisitDetails.readable(entry.detail)); Text(entry.statusLabel(careHindi()) + t(" · saved plan, not a confirmed booking", " · सहेजी योजना, पक्की बुकिंग नहीं"), style = MaterialTheme.typography.bodySmall) } else if (item.detail.isNotBlank()) Text(item.detail) } }
}

@Composable internal fun DoctorCareStory(person: Person, versions: List<CareVersion>, onRead: (CareVersion) -> Unit) { val t = careText();
    val notes = versions.filter { it.personId == person.id }.sortedByDescending { it.published }
    val latest=notes.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-care-story"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Surface(color=Peach,shape=RoundedCornerShape(18.dp),modifier=Modifier.size(54.dp)){Box(contentAlignment=Alignment.Center){Text(person.name.trim().firstOrNull()?.uppercaseChar()?.toString()?:"?",fontSize=24.sp,fontWeight=FontWeight.SemiBold)}}
                Text(person.name.ifBlank {t("Name not recorded", "नाम दर्ज नहीं")},fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold)
            }
            Spacer(Modifier.height(18.dp));Heading(t("Care overview", "देखभाल का सार"),29)
        }
        item {
            Surface(color=Color(0xFFE4EFF5),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().testTag("doctor-handover-glance")) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(t("Handover at a glance", "एक नज़र में हैंडओवर"),fontSize=22.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text(t("Last recorded reviewer", "पिछले दर्ज समीक्षक"),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                    Text(latest?.doctor?.ifBlank {t("Not recorded", "दर्ज नहीं")}?:t("Not recorded", "दर्ज नहीं"),fontSize=18.sp,lineHeight=25.sp)
                    Text(if(latest==null)t("No saved care note", "कोई सहेजा केयर नोट नहीं")else t("From latest saved care note", "नए सहेजे केयर नोट से"),fontSize=14.sp)
                    latest?.let {Text(ReviewWorkflow.label(it),fontSize=15.sp,lineHeight=22.sp,color=Rust)}
                    HorizontalDivider(color=Color(0xFFCBDDE8))
                    Text(t("Clinical assessment", "क्लिनिकल आकलन"),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                    Text(t("Not recorded", "दर्ज नहीं"),fontSize=16.sp)
                    Text(t("What matters", "क्या ज़रूरी है"),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                    Text(latest?.priorities?.ifBlank {t("Not recorded", "दर्ज नहीं")}?:t("Not recorded", "दर्ज नहीं"),fontSize=16.sp,lineHeight=24.sp)
                    Text(t("Priorities from the saved discussion, not a current clinical assessment.", "सहेजी चर्चा की प्राथमिकताएँ, अभी का क्लिनिकल आकलन नहीं।"),fontSize=14.sp,lineHeight=21.sp)
                }
            }
        }
        item {Text(t("Care handover", "देखभाल हैंडओवर"),fontSize=22.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont)}
        if(notes.isEmpty()) item {SurfaceCard {Text(t("No care discussion recorded yet", "अभी कोई देखभाल चर्चा दर्ज नहीं"),fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont);Text(t("Saved discussions will appear here without replacing earlier versions.", "सहेजी चर्चाएँ यहाँ दिखेंगी, पुराने संस्करण बने रहेंगे।"),fontSize=16.sp,lineHeight=24.sp)}}
        items(notes,key={it.id}) {note ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.width(18.dp).fillMaxHeight()) {val x=size.width/2;drawLine(Color(0xFFCBDDE8),Offset(x,0f),Offset(x,size.height),2.dp.toPx());drawCircle(Rust,5.dp.toPx(),Offset(x,14.dp.toPx()))}
                Surface(color=Paper,shape=RoundedCornerShape(22.dp),modifier=Modifier.weight(1f).testTag("doctor-handover-note-${note.id}")) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Text(doctorRecordedTime(note.published,ZoneId.systemDefault()),fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold)
                        Text(t("Care note · version ${note.version}", "केयर नोट · संस्करण ${note.version}"),fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont)
                        Text(t("Recorded reviewer: ", "दर्ज समीक्षक: ") + note.doctor.ifBlank {t("Not recorded", "दर्ज नहीं")},fontSize=16.sp,lineHeight=23.sp)
                        Text(ReviewWorkflow.label(note),fontSize=15.sp,lineHeight=22.sp,color=Rust)
                        listOf(t("What matters", "क्या ज़रूरी है") to note.priorities,t("Who attended", "कौन मौजूद था") to note.participants,t("Discussion", "चर्चा") to note.topics,t("Questions", "सवाल") to note.questions,t("Next steps", "अगले क़दम") to note.nextSteps).forEach {(label,value)->
                            Text(label,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text(value.ifBlank {t("Not recorded", "दर्ज नहीं")},fontSize=16.sp,lineHeight=24.sp)
                        }
                        OutlinedButton(onClick={onRead(note)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("doctor-handover-read-${note.id}")){Text(t("Read exact note and source", "मूल नोट और स्रोत पढ़ें"))}
                    }
                }
            }
        }
    }
}

private fun doctorMoodColors(mood: DoctorReportedMood?) = when (mood) { DoctorReportedMood.GOOD -> DoctorStatusGood to DoctorStatusGoodBg; DoctorReportedMood.OKAY -> DoctorStatusWarn to DoctorStatusWarnBg; DoctorReportedMood.HARD -> DoctorStatusRest to DoctorStatusRestBg; null -> DoctorCardSub to DoctorCardProgress }
private fun doctorMoodShort(mood: DoctorReportedMood) = when (mood) { DoctorReportedMood.GOOD -> "Good days"; DoctorReportedMood.OKAY -> "Okay days"; DoctorReportedMood.HARD -> "Hard days" }
private fun doctorDayLabel(at: Long, now: Long, zone: ZoneId): String {
    if (at <= 0) return "Date not recorded"
    val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate(); val days = java.time.temporal.ChronoUnit.DAYS.between(date, Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
    val time = DateTimeFormatter.ofPattern("h:mm a").withZone(zone).format(Instant.ofEpochMilli(at))
    return when (days) { 0L -> "Today · $time"; 1L -> "Yesterday · $time"; else -> "${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · $time" }
}
@Composable private fun DoctorMoodChip(mood: DoctorReportedMood?, fallback: String, modifier: Modifier = Modifier) {
    val (fg, bg) = doctorMoodColors(mood)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50), modifier = modifier) { Text(mood?.title ?: fallback, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = fg) }
}
private fun doctorPlural(n: Int) = if (n == 1) "1 update" else "$n updates"
private fun doctorToolLabel(label: String, hindi: Boolean) = careTr(hindi, label, when (label) { "Calendar" -> "कैलेंडर"; "Care team" -> "देखभाल टीम"; "Lab history" -> "जाँच रिपोर्टें"; else -> label })

@Composable internal fun DoctorCheckInHistory(personId: String, entries: List<CareItem>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) { val t = careText();
    val checks = doctorCheckIns(entries, personId, now)
    var detail by remember(personId) { mutableStateOf<CareItem?>(null) }
    var query by rememberSaveable(personId) { mutableStateOf("") }
    var moodKeys by rememberSaveable(personId) { mutableStateOf("") }
    val picked = moodKeys.split(",").filter { it.isNotEmpty() }.mapNotNull { k -> DoctorReportedMood.entries.firstOrNull { it.name == k } }.toSet()
    val needle = query.trim()
    val shown = checks.filter { c -> (picked.isEmpty() || doctorMood(c.title) in picked) && (needle.isEmpty() || c.title.contains(needle, true) || c.detail.contains(needle, true)) }
    val recent = checks.filter { it.created >= now - 30L * 86_400_000L }
    val grouped = shown.groupBy { java.time.YearMonth.from(Instant.ofEpochMilli(it.created).atZone(zone)) }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("doctor-checkin-history"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading(t("Reported condition history", "बताई गई हालत का इतिहास"), 27); Text(t("Dated self-reports, not a clinical score.", "तारीख़ वाली अपनी सूचनाएँ, क्लिनिकल स्कोर नहीं।"), Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint) }
        item { DistressHistoryCard(entries, personId) }
        if (checks.isEmpty()) item { SurfaceCard { Text(t("No check-ins recorded yet", "अभी कोई हाल दर्ज नहीं"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk); Text(t("Updates will appear here when recorded.", "दर्ज होने पर अपडेट यहाँ दिखेंगे।"), fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk) } } else {
            item {
                Surface(color = DoctorCardProgress, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("doctor-checkin-summary")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t("Last 30 days", "पिछले 30 दिन"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        if (recent.isEmpty()) Text(t("No reports in the last 30 days.", "पिछले 30 दिनों में कोई सूचना नहीं।"), fontSize = 13.sp, color = DoctorCardSub) else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DoctorReportedMood.entries.forEach { m -> val (fg, bg) = doctorMoodColors(m); val n = recent.count { doctorMood(it.title) == m }
                                Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).testTag("doctor-checkin-summary-${m.name.lowercase()}")) { Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) { Text("$n", fontFamily = SaharaTitleFont, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = fg); Text(doctorMoodShort(m), fontSize = 12.sp, lineHeight = 16.sp, color = fg) } } }
                        }
                        Text(t("A count of recorded self-reports, ${doctorPlural(recent.size)}.", "दर्ज अपनी सूचनाओं की गिनती: ${recent.size}।"), fontSize = 12.sp, color = DoctorCardFaint)
                    }
                }
            }
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("doctor-checkin-search"), singleLine = true, placeholder = { Text(t("Search updates", "अपडेट खोजें"), fontSize = 15.sp) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, Modifier.size(48.dp).testTag("doctor-checkin-search-clear")) { Icon(Icons.Default.Clear, t("Clear search", "खोज मिटाएँ")) } }, shape = RoundedCornerShape(20.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = DoctorCardInk))
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DoctorReportedMood.entries.forEach { m -> val (fg, bg) = doctorMoodColors(m); val on = m in picked
                        FilterChip(selected = on, onClick = { moodKeys = (if (on) picked - m else picked + m).joinToString(",") { it.name } }, label = { Text(doctorMoodShort(m), fontSize = 13.sp) }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-checkin-filter-${m.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = bg, selectedLabelColor = fg, labelColor = DoctorCardSub, containerColor = DoctorCardReport)) }
                }
            }
            item { Text(if (picked.isEmpty() && needle.isEmpty()) t("Showing all ${doctorPlural(checks.size)}", "सभी ${checks.size} अपडेट दिख रहे हैं") else t("Showing ${shown.size} of ${doctorPlural(checks.size)}", "${checks.size} में से ${shown.size} अपडेट दिख रहे हैं"), fontSize = 13.sp, color = DoctorCardSub, modifier = Modifier.testTag("doctor-checkin-count")) }
            if (shown.isEmpty()) item {
                SurfaceCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("doctor-checkin-empty")) {
                    Text(t("No updates match", "कोई अपडेट नहीं मिला"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    Text(t("Try different words or remove a filter.", "दूसरे शब्द आज़माएँ या कोई फ़िल्टर हटाएँ।"), fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                    TextButton(onClick = { query = ""; moodKeys = "" }, Modifier.heightIn(min = 48.dp).testTag("doctor-checkin-clear-filters")) { Text(t("Clear search and filters", "खोज और फ़िल्टर हटाएँ"), color = DoctorAccent) }
                } }
            }
            grouped.forEach { (month, list) ->
                item(key = "month-$month") { Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.padding(top = 4.dp).testTag("doctor-checkin-month-$month"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub) }
                items(list, key = { it.id }) { check -> DoctorCheckInCard(check, zone, now) { detail = check } }
            }
        }
    }
    detail?.let { check -> AlertDialog(onDismissRequest = { detail = null }, title = { Text(check.title) }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(doctorRecordedTime(check.created, zone)); Text(CareEntryCodec.readable(check.detail).ifBlank { t("No additional details recorded.", "और कोई जानकारी दर्ज नहीं।") }); Text(t("Self-reported update; clinical assessment and reporter identity are not independently verified.", "अपनी बताई सूचना; क्लिनिकल आकलन और बताने वाले की पहचान अलग से पुष्ट नहीं है।")) } }, confirmButton = { TextButton(onClick = { detail = null }) { Text(t("Close", "बंद करें")) } }) }
}

@Composable internal fun DoctorCheckInCard(check: CareItem, zone: ZoneId, now: Long = System.currentTimeMillis(), onOpen: () -> Unit) { val t = careText();
    Card(onClick = onOpen, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = DoctorCardReport), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("doctor-checkin-${check.id}")) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(doctorDayLabel(check.created, now, zone), Modifier.weight(1f).testTag("doctor-checkin-date-${check.id}"), fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                DoctorMoodChip(doctorMood(check.title), check.title, Modifier.testTag("doctor-checkin-mood-${check.id}"))
            }
            Column(Modifier.fillMaxWidth().testTag("doctor-checkin-body-${check.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(CareEntryCodec.readable(check.detail).ifBlank { t("No additional details recorded.", "और कोई जानकारी दर्ज नहीं।") }, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                Text(t("View full update", "पूरा अपडेट देखें"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent)
            }
        }
    }
}

/** Compatibility wrapper; date-bucket rendering is owned by the contribution calendar. */
@Composable internal fun DoctorConditionChart(checks: List<CareItem>, zone: ZoneId, onSelect: (CareItem) -> Unit = {}, now: Long = System.currentTimeMillis()) {
    DoctorContributionChart(checks, zone, now, onSelect)
}
