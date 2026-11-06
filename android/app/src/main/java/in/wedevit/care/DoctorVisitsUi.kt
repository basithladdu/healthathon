package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DoctorVisitDetail(val conditionRef: String = "", val discussionRef: String = "", val note: String = "")
object DoctorVisitDetails {
    private const val PREFIX = "sahara:visit-summary:v1\n"
    fun encode(conditionRef: String, discussionRef: String, note: String): String {
        listOf(conditionRef, discussionRef).filter(String::isNotBlank).forEach { java.util.UUID.fromString(it) }
        if (conditionRef.isBlank() && discussionRef.isBlank()) return note.also { require(it.length <= 1600) }
        return (PREFIX + JSONObject()
        .put("conditionRef", conditionRef).put("discussionRef", discussionRef).put("note", note.trim()).toString())
        .also { require(it.length <= 1600) { "Shorten the visit summary to fit this record." } }
    }
    fun decode(detail: String): DoctorVisitDetail = if (detail.startsWith(PREFIX)) runCatching {
        val j = JSONObject(detail.removePrefix(PREFIX)); DoctorVisitDetail(j.optString("conditionRef"), j.optString("discussionRef"), j.optString("note"))
    }.getOrElse { DoctorVisitDetail(note = "This visit summary needs a newer app to open.") } else DoctorVisitDetail(note = detail)
    fun readable(detail: String): String = decode(detail).note
}

internal fun doctorVisitsFor(entries: List<CareItem>, personId: String) = entries.filter { it.personId == personId && it.kind == "visit" && (!CareEntryCodec.isStructured(it.detail) || isDoctorStructuredVisit(it)) }.sortedByDescending { it.due.takeIf { due -> due > 0 } ?: it.created }
internal fun doctorVisitCompleted(item: CareItem) = CareEntryCodec.decode(item.detail)?.status == "completed" || (!CareEntryCodec.isStructured(item.detail) && item.doneOn.isNotBlank())
internal fun doctorVisitState(item: CareItem, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val entry = CareEntryCodec.decode(item.detail)
    val past = item.due > 0 && if (entry?.timed == true) item.due < now else Instant.ofEpochMilli(item.due).atZone(zone).toLocalDate() < Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when { entry?.status == "cancelled" -> "Cancelled"; entry?.status == "missed" -> "Missed · recorded"; doctorVisitCompleted(item) -> "Completed"; item.due == 0L -> "Date not recorded"; past -> "Past · completion not recorded"; else -> "Upcoming" }
}
internal fun doctorVisitDoctor(item: CareItem) = CareEntryCodec.decode(item.detail)?.sender?.trim().orEmpty()
internal fun doctorCompletedDate(item: CareItem, now: Long): Long? = CareEntryCodec.decode(item.detail)?.completedAt?.takeIf { it in 1..now } ?: item.due.takeIf { it in 1..now }
internal fun doctorLastCompletedByDoctor(entries: List<CareItem>, now: Long = System.currentTimeMillis()): List<CareItem> = entries.filter { doctorVisitCompleted(it) && doctorVisitDoctor(it).isNotBlank() && doctorCompletedDate(it, now) != null }.groupBy { doctorVisitDoctor(it).lowercase(java.util.Locale.ROOT) }.values.mapNotNull { rows -> rows.maxByOrNull { doctorCompletedDate(it, now)!! } }
private fun visitSourceDate(at: Long) = if (at <= 0) "Date not recorded" else DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
private fun visitDate(item: CareItem): String = if (item.due <= 0) "Date not recorded" else DateTimeFormatter.ofPattern(if (CareEntryCodec.decode(item.detail)?.timed == true) "EEE, d MMM yyyy · h:mm a" else "EEE, d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.due))
internal data class DoctorVisitSources(val condition: CareItem?, val discussion: CareVersion?)
internal fun doctorVisitSources(item: CareItem, entries: List<CareItem>, notes: List<CareVersion>): DoctorVisitSources {
    val links = DoctorVisitDetails.decode(CareEntryCodec.decode(item.detail)?.detail ?: item.detail)
    return DoctorVisitSources(entries.firstOrNull { it.id == links.conditionRef && it.personId == item.personId && it.kind == "checkin" && !CareEntryCodec.isStructured(it.detail) }, notes.firstOrNull { it.id == links.discussionRef && it.personId == item.personId })
}

@Composable fun DoctorVisits(vm: CareModel, personId: String, onCalendar: () -> Unit, onAddVisit: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}, onHeaderBackChange: ((() -> Unit)?) -> Unit = {}) { val t = careText();
    val entries by vm.items.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle(); val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val notes by vm.versions.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val account = remember(personId) { vm.accountNamespace }; val session = vm.cloud?.session?.collectAsStateWithLifecycle()?.value
    val ready = selected == personId && draft.personId == personId && vm.accountNamespace == account && (vm.cloud == null || session?.userId == account)
    var detailId by remember(personId, account) { mutableStateOf<String?>(null) }; var editing by remember(personId, account) { mutableStateOf<CareItem?>(null) }
    val rows = if (ready) doctorVisitsFor(entries, personId) else emptyList(); val detail = rows.firstOrNull { it.id == detailId }
    val blockedCallback by rememberUpdatedState(onNavigationBlockedChange); val backCallback by rememberUpdatedState(onHeaderBackChange)
    SideEffect { blockedCallback(busy || editing != null) }
    DisposableEffect(detailId, busy, ready) { backCallback(if (detailId != null && !busy && ready) ({ detailId = null }) else null); onDispose { backCallback(null) } }
    DisposableEffect(Unit) { onDispose { blockedCallback(false) } }
    LaunchedEffect(ready, canEdit) { if (!ready) detailId = null; if (!ready || !canEdit) editing = null }
    BackHandler(detailId != null && editing == null && !busy) { detailId = null }
    if (!ready) Text(t("Opening this person's visits…", "इस व्यक्ति की विज़िट खुल रही हैं…"), Modifier.padding(20.dp))
    else if (detail != null) DoctorVisitDetailsContent(detail, canEdit, busy, conditionSources = entries, discussionSources = notes, onBack = { detailId = null }, onEdit = { editing = detail }, onPlanAnother = { detailId = null; onAddVisit() }, onStatus = { status ->
        if (vm.selected.value == personId && vm.draft.value.personId == personId && vm.accountNamespace == account && (vm.cloud == null || vm.cloud.session.value?.userId == account) && vm.canEdit.value && !vm.busy.value) {
            val original = CareEntryCodec.decode(detail.detail) ?: CareEntry("schedule", category = "Visit", detail = detail.detail)
            val updated = original.copy(status = status, completedAt = if (status == "completed") System.currentTimeMillis() else original.completedAt)
            vm.updateItem(detail.copy(detail = CareEntryCodec.encode(updated)), {})
        }
    }) else DoctorVisitsContent(rows, canEdit, System.currentTimeMillis(), onOpen = { detailId = it.id }, onCalendar = onCalendar, onAdd = onAddVisit, conditionSources = entries, discussionSources = notes)
    editing?.let { DoctorVisitEditor(vm, personId, onDismiss = { editing = null }, initial = it) }
}

@Composable internal fun DoctorVisitsContent(visits: List<CareItem>, canEdit: Boolean, now: Long, onOpen: (CareItem) -> Unit, onCalendar: () -> Unit, onAdd: () -> Unit, conditionSources: List<CareItem> = emptyList(), discussionSources: List<CareVersion> = emptyList()) { val t = careText();
    CompositionLocalProvider(LocalSaharaStagger provides remember { SaharaStagger() }) { LazyColumn(Modifier.fillMaxSize().testTag("doctor-visits"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(8.dp)) { Heading(t("Visits", "विज़िट"),28);Button(onClick=onAdd,enabled=canEdit,shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp)){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(t("Plan visit", "विज़िट तय करें"))} } }
        if (visits.isEmpty()) item { SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Event, null, tint = DoctorAccent, modifier = Modifier.size(24.dp)); Text(t("No visits recorded yet", "अभी कोई विज़िट दर्ज नहीं"), fontSize = 16.sp, color = DoctorCardInk) } } }
        items(visits, key = { "all:${it.id}" }) { VisitListCard(it, now, onOpen, conditionSources, discussionSources, highlighted = it.id == visits.firstOrNull()?.id) }
    } }
}
internal val DoctorVisitHighlight = Color(0xFFD97757)
internal val DoctorVisitHighlightInk = Color(0xFF201711)
@Composable private fun VisitListCard(item: CareItem, now: Long, onOpen: (CareItem) -> Unit, conditionSources: List<CareItem>, discussionSources: List<CareVersion>, highlighted: Boolean = false) { val t = careText();
    val links = DoctorVisitDetails.decode(CareEntryCodec.decode(item.detail)?.detail ?: item.detail)
    val sources = doctorVisitSources(item, conditionSources, discussionSources)
    val largeText = LocalDensity.current.fontScale > 1.2f
    val background = if (highlighted) DoctorVisitHighlight else Paper
    val ink = if (highlighted) DoctorVisitHighlightInk else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
        val x = 8.dp.toPx()
        // Extend into the lazy-list spacing so successive appointments share one rail.
        drawLine(Ink.copy(alpha = .22f), Offset(x, -7.dp.toPx()), Offset(x, size.height + 7.dp.toPx()), 1.5.dp.toPx())
        drawCircle(DoctorVisitHighlight, 4.dp.toPx(), Offset(x, 29.dp.toPx()))
    }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.width(16.dp))
        Card(onClick = { onOpen(item) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = background), border = if (highlighted) null else androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline), modifier = Modifier.weight(1f).saharaAppear().heightIn(min = 96.dp).testTag("doctor-visit-card-${item.id}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (largeText) {
                    Text(doctorVisitDoctor(item).ifBlank { t("Doctor not recorded", "डॉक्टर दर्ज नहीं") }, color=ink, fontSize=18.sp, fontFamily=SaharaTitleFont, fontWeight=FontWeight.Medium)
                    Text(t("Planned: ${visitDate(item)}", "तय: ${visitDate(item)}"), color=ink.copy(alpha=.85f), fontSize=14.sp, lineHeight=20.sp)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.Top) {
                    Text(doctorVisitDoctor(item).ifBlank { t("Doctor not recorded", "डॉक्टर दर्ज नहीं") }, color=ink, fontSize=18.sp, lineHeight=24.sp, fontFamily=SaharaTitleFont, fontWeight=FontWeight.Medium, modifier=Modifier.weight(1f))
                    Text(visitDate(item), color=ink.copy(alpha=.85f), fontSize=14.sp, lineHeight=19.sp, textAlign=androidx.compose.ui.text.style.TextAlign.End, modifier=Modifier.widthIn(max=136.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(links.note.ifBlank {sources.discussion?.topics.orEmpty().ifBlank {t("Care review visit", "देखभाल समीक्षा विज़िट")}}, color=ink, fontSize=16.sp, lineHeight=23.sp, maxLines=2, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward,contentDescription=t("Open visit details", "विज़िट की जानकारी खोलें"),modifier=Modifier.size(20.dp),tint=ink)
                }
            }
        }
    }
}
@Composable internal fun DoctorVisitDetailsContent(item: CareItem, canEdit: Boolean, busy: Boolean, onBack: () -> Unit, onEdit: () -> Unit, onStatus: (String) -> Unit, conditionSources: List<CareItem> = emptyList(), discussionSources: List<CareVersion> = emptyList(), onPlanAnother: () -> Unit = {}) { val t = careText();
    val entry = CareEntryCodec.decode(item.detail); val summary = DoctorVisitDetails.decode(entry?.detail ?: item.detail)
    var confirmCancel by remember(item.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-visit-details"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Back to visits", "विज़िट पर वापस")) }
            Heading(doctorVisitDoctor(item).ifBlank { t("Doctor not recorded", "डॉक्टर दर्ज नहीं") }, 26)
            Text(t("Planned: ${visitDate(item)}", "तय: ${visitDate(item)}"), fontSize = 16.sp, color = DoctorCardSub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val state = doctorVisitState(item, System.currentTimeMillis())
                DoctorPill(when (state) { "Cancelled" -> t("Cancelled", "रद्द"); "Missed · recorded" -> t("Missed · recorded", "छूटी · दर्ज"); "Completed" -> t("Completed", "पूरी हुई"); "Date not recorded" -> t("Date not recorded", "तारीख़ दर्ज नहीं"); "Past · completion not recorded" -> t("Past · completion not recorded", "बीत गई · पूरी होना दर्ज नहीं"); else -> t("Upcoming", "आने वाली") }, when (state) { "Completed" -> DoctorTones.Leaf; "Upcoming" -> DoctorTones.Marigold; else -> DoctorTones.Red })
                            }
        } }
        item { VisitSection(t("Place / visit type", "जगह / विज़िट का प्रकार"), entry?.location?.ifBlank { t("Not recorded", "दर्ज नहीं") } ?: t("Not recorded", "दर्ज नहीं")) }
        val sources = doctorVisitSources(item, conditionSources, discussionSources)
        val condition = sources.condition
        val discussion = sources.discussion
        val conditionText = if (summary.conditionRef.isBlank()) t("Not recorded", "दर्ज नहीं") else condition?.let { "${visitSourceDate(it.created)} · " + t("Patient / family report", "मरीज़ / परिवार की सूचना") + "\n${it.title}\n${CareEntryCodec.readable(it.detail)}" } ?: t("Not available", "उपलब्ध नहीं")
        val discussionText = if (summary.discussionRef.isBlank()) t("Not recorded", "दर्ज नहीं") else discussion?.let { "${visitSourceDate(it.published)}\n${ReviewWorkflow.label(it)}\n${it.topics.ifBlank { t("Not recorded", "दर्ज नहीं") }}\n${it.nextSteps}" } ?: t("Not available", "उपलब्ध नहीं")
        listOf(t("Linked condition report", "जुड़ी हालत की सूचना") to conditionText, t("Discussion linked to this visit", "इस विज़िट से जुड़ी चर्चा") to discussionText, t("Visit notes", "विज़िट के नोट") to summary.note).forEach { (label, value) -> item { VisitSection(label, value.ifBlank { t("Not recorded", "दर्ज नहीं") }) } }
        if (canEdit) item { Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doctorVisitCompleted(item) || entry?.status == "cancelled") Button(onClick = onPlanAnother, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Plan another visit", "एक और विज़िट तय करें")) }
            else {
                Button(onClick = { onStatus("completed") }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Mark completed", "पूरी हुई")) }
                OutlinedButton(onClick = onEdit, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Edit or reschedule", "बदलें या समय बदलें")) }
                TextButton(onClick = { confirmCancel = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Cancel visit", "विज़िट रद्द करें"), color = MaterialTheme.colorScheme.error) }
            }
        } }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, containerColor = Cream, title = { Text(t("Cancel this visit?", "यह विज़िट रद्द करें?")) },
        confirmButton = { TextButton(onClick = { confirmCancel = false; onStatus("cancelled") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel visit", "विज़िट रद्द करें"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Keep visit", "विज़िट रहने दें")) } })
}

@Composable private fun VisitSection(label: String, value: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, Modifier.a11yHeading(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
            Text(value, fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardInk)
        }
    }
}
