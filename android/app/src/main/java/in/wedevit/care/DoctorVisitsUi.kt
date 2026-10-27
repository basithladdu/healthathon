package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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

@Composable fun DoctorVisits(vm: CareModel, personId: String, onCalendar: () -> Unit, onAddVisit: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}, onHeaderBackChange: ((() -> Unit)?) -> Unit = {}) {
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
    if (!ready) Text("Opening this person's visits…", Modifier.padding(20.dp))
    else if (detail != null) DoctorVisitDetailsContent(detail, canEdit, busy, conditionSources = entries, discussionSources = notes, onBack = { detailId = null }, onEdit = { editing = detail }, onPlanAnother = { detailId = null; onAddVisit() }, onStatus = { status ->
        if (vm.selected.value == personId && vm.draft.value.personId == personId && vm.accountNamespace == account && (vm.cloud == null || vm.cloud.session.value?.userId == account) && vm.canEdit.value && !vm.busy.value) {
            val original = CareEntryCodec.decode(detail.detail) ?: CareEntry("schedule", category = "Visit", detail = detail.detail)
            val updated = original.copy(status = status, completedAt = if (status == "completed") System.currentTimeMillis() else original.completedAt)
            vm.updateItem(detail.copy(detail = CareEntryCodec.encode(updated)), {})
        }
    }) else DoctorVisitsContent(rows, canEdit, System.currentTimeMillis(), onOpen = { detailId = it.id }, onCalendar = onCalendar, onAdd = onAddVisit, conditionSources = entries, discussionSources = notes)
    editing?.let { DoctorVisitEditor(vm, personId, onDismiss = { editing = null }, initial = it) }
}

@Composable internal fun DoctorVisitsContent(visits: List<CareItem>, canEdit: Boolean, now: Long, onOpen: (CareItem) -> Unit, onCalendar: () -> Unit, onAdd: () -> Unit, conditionSources: List<CareItem> = emptyList(), discussionSources: List<CareVersion> = emptyList()) {
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-visits"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(8.dp)) { Heading("Visits",29);Button(onClick=onAdd,enabled=canEdit,modifier=Modifier.heightIn(min=48.dp)){Text("Plan visit")} };Text("Appointments and recorded visit summaries",fontSize=16.sp) }
        item { Text("All appointments", fontSize = 21.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text("A past date alone does not mean the visit happened.") }
        if (visits.isEmpty()) item { SurfaceCard { Text("No visits recorded yet"); Text("Plan a visit to keep its time and later discussion together.") } }
        items(visits, key = { "all:${it.id}" }) { VisitListCard(it, now, onOpen, conditionSources, discussionSources, highlighted = it.id == visits.firstOrNull()?.id) }
    }
}
internal val DoctorVisitHighlight = Color(0xFFD97757)
internal val DoctorVisitHighlightInk = Color(0xFF201711)
@Composable private fun VisitListCard(item: CareItem, now: Long, onOpen: (CareItem) -> Unit, conditionSources: List<CareItem>, discussionSources: List<CareVersion>, highlighted: Boolean = false) {
    val links = DoctorVisitDetails.decode(CareEntryCodec.decode(item.detail)?.detail ?: item.detail)
    val sources = doctorVisitSources(item, conditionSources, discussionSources)
    val largeText = LocalDensity.current.fontScale > 1.2f
    val background = if (highlighted) DoctorVisitHighlight else Color.White
    val ink = if (highlighted) DoctorVisitHighlightInk else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
        val x = 8.dp.toPx()
        // Extend into the lazy-list spacing so successive appointments share one rail.
        drawLine(Color(0xFFD4C2B2), Offset(x, -7.dp.toPx()), Offset(x, size.height + 7.dp.toPx()), 1.5.dp.toPx())
        drawCircle(DoctorVisitHighlight, 4.dp.toPx(), Offset(x, 29.dp.toPx()))
    }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.width(16.dp))
        Card(onClick = { onOpen(item) }, shape = RoundedCornerShape(if(highlighted) 22.dp else 16.dp), colors = CardDefaults.cardColors(containerColor = background), modifier = Modifier.weight(1f).heightIn(min = 104.dp).testTag("doctor-visit-card-${item.id}")) {
            Column(Modifier.padding(if(largeText) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (largeText) {
                    Text(doctorVisitDoctor(item).ifBlank { "Doctor not recorded" }, color=ink, fontSize=18.sp, fontFamily=SaharaTitleFont, fontWeight=FontWeight.Medium)
                    Text("Planned: ${visitDate(item)}", color=ink.copy(alpha=.85f), fontSize=13.sp, lineHeight=19.sp)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.Top) {
                    Text(doctorVisitDoctor(item).ifBlank { "Doctor not recorded" }, color=ink, fontSize=18.sp, lineHeight=24.sp, fontFamily=SaharaTitleFont, fontWeight=FontWeight.Medium, modifier=Modifier.weight(1f))
                    Text("Planned\n${visitDate(item)}", color=ink.copy(alpha=.85f), fontSize=12.sp, lineHeight=17.sp, modifier=Modifier.widthIn(max=128.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(links.note.ifBlank {sources.discussion?.topics.orEmpty().ifBlank {"Care review visit"}}, color=ink, fontSize=16.sp, lineHeight=23.sp, maxLines=2, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward,contentDescription="Open visit details",modifier=Modifier.size(18.dp),tint=ink)
                }
            }
        }
    }
}
@Composable internal fun DoctorVisitDetailsContent(item: CareItem, canEdit: Boolean, busy: Boolean, onBack: () -> Unit, onEdit: () -> Unit, onStatus: (String) -> Unit, conditionSources: List<CareItem> = emptyList(), discussionSources: List<CareVersion> = emptyList(), onPlanAnother: () -> Unit = {}) {
    val entry = CareEntryCodec.decode(item.detail); val summary = DoctorVisitDetails.decode(entry?.detail ?: item.detail)
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-visit-details"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TextButton(onClick = onBack, enabled = !busy) { Text("Back to visits") }; Heading("Visit details", 28); Text(doctorVisitDoctor(item).ifBlank { "Doctor not recorded" }, fontSize = 21.sp); Text("Planned: ${visitDate(item)}"); Text(doctorVisitState(item, System.currentTimeMillis()), color = Rust);if(doctorVisitCompleted(item))Text(entry?.completedAt?.takeIf {it in 1..System.currentTimeMillis()}?.let {"Completion recorded: ${visitSourceDate(it)}"}?:"Completion time not recorded.") }
        item { SurfaceCard { Text("Place / visit type", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text(entry?.location?.ifBlank { "Not recorded" } ?: "Not recorded") } }
        val sources = doctorVisitSources(item, conditionSources, discussionSources)
        val condition = sources.condition
        val discussion = sources.discussion
        val conditionText = if (summary.conditionRef.isBlank()) "Not recorded for this visit." else condition?.let { "${visitSourceDate(it.created)} · Patient / family report\n${it.title}\n${CareEntryCodec.readable(it.detail)}" } ?: "Linked update is not accessible or no longer available."
        val discussionText = if (summary.discussionRef.isBlank()) "Not recorded for this visit." else discussion?.let { "${visitSourceDate(it.published)}\n${ReviewWorkflow.label(it)}\n${it.topics.ifBlank { "Discussion not recorded in this note." }}\n${it.nextSteps}" } ?: "Linked care note is not accessible or no longer available."
        listOf("Linked condition report" to conditionText, "Discussion linked to this visit" to discussionText, "Visit notes" to summary.note).forEach { (label, value) -> item { SurfaceCard { Text(label, fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text(value.ifBlank { "Not recorded for this visit." }, fontSize = 16.sp) } } }
        item { Text("These are this visit's recorded details, not information inferred from other notes or check-ins.") }
        if (canEdit) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doctorVisitCompleted(item) || entry?.status == "cancelled") Button(onClick = onPlanAnother, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Plan another visit") }
            else { OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Edit or reschedule") }; Button(onClick = { onStatus("completed") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Mark completed") }; TextButton(onClick = { onStatus("cancelled") }, enabled = !busy) { Text("Cancel visit") } }
        } }
    }
}
