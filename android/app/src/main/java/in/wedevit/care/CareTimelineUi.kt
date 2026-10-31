@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter

private fun timelineIcon(kind: TimelineKind): ImageVector = when (kind) {
    TimelineKind.CHEMO -> Icons.Default.Vaccines; TimelineKind.RADIO -> Icons.Default.FlashOn
    TimelineKind.SCAN -> Icons.Default.Image; TimelineKind.BIOPSY -> Icons.Default.Biotech; TimelineKind.STAGING -> Icons.Default.FactCheck
    TimelineKind.LAB -> Icons.Default.Science; TimelineKind.TEST -> Icons.Default.Biotech; TimelineKind.VISIT -> Icons.Default.MedicalServices
    TimelineKind.NOTE -> Icons.Default.Description; TimelineKind.DOCUMENT -> Icons.Default.Folder
}
private fun timelineColor(kind: TimelineKind): Color = when (kind.filter) { TimelineFilter.TREATMENT -> Peach; TimelineFilter.SCANS -> Sage; TimelineFilter.LAB -> Mint; else -> Gold }

/** "PET-CT scan", "Chemotherapy day" and so on, in the chosen language. */
private fun timelineLabel(e: TimelineEvent, t: (String, String) -> String): String = when {
    e.subtype in CancerDocumentRules.types -> CancerDocumentRules.label(e.subtype, t)
    else -> when (e.kind) {
        TimelineKind.CHEMO -> t("Chemotherapy day", "कीमो का दिन"); TimelineKind.RADIO -> t("Radiotherapy day", "रेडियोथेरेपी का दिन")
        TimelineKind.SCAN -> t("Scan", "स्कैन"); TimelineKind.BIOPSY -> t("Biopsy", "बायोप्सी"); TimelineKind.STAGING -> t("Staging report", "स्टेजिंग रिपोर्ट")
        TimelineKind.LAB -> if (e.isFile) t("Lab report", "लैब रिपोर्ट") else t("Lab result", "जाँच का नतीजा")
        TimelineKind.TEST -> t("Test", "जाँच"); TimelineKind.VISIT -> t("Visit", "मुलाक़ात"); TimelineKind.NOTE -> t("Care note", "देखभाल नोट"); TimelineKind.DOCUMENT -> t("Document", "कागज़")
    }
}
/** The headline: a typed title, or the type of document for a file, or the goals note version. */
private fun timelineHeadline(e: TimelineEvent, t: (String, String) -> String): String = when {
    e.source == TimelineSource.GOALS -> t("Goals of care", "देखभाल के लक्ष्य") + " · " + t("Version ", "संस्करण ") + e.title
    e.isFile -> timelineLabel(e, t)
    e.title.isNotBlank() -> e.title
    else -> timelineLabel(e, t)
}
/** Where the event comes from, without the "From:" lead-in. */
private fun timelineSource(e: TimelineEvent, t: (String, String) -> String): String = when (e.source) {
    TimelineSource.REPORT -> e.sourceName.ifBlank { t("a report", "एक रिपोर्ट") } + if (e.page > 0) t(", page ", ", पेज ") + e.page else ""
    TimelineSource.CALENDAR -> t("Calendar", "कैलेंडर")
    TimelineSource.LAB_HISTORY -> t("Lab history", "जाँच रिपोर्टें")
    TimelineSource.CARE_NOTE -> t("Care notes", "देखभाल नोट")
    TimelineSource.GOALS -> t("Goals of care", "देखभाल के लक्ष्य") + " " + t("Version ", "संस्करण ") + e.title
}
private fun timelineFilterLabel(f: TimelineFilter, t: (String, String) -> String) = when (f) {
    TimelineFilter.ALL -> t("All", "सभी"); TimelineFilter.TREATMENT -> t("Treatment", "इलाज"); TimelineFilter.SCANS -> t("Scans", "स्कैन")
    TimelineFilter.LAB -> t("Lab", "जाँच"); TimelineFilter.VISITS -> t("Visits & notes", "मुलाक़ातें और नोट")
}

/**
 * A newest-first timeline built only from what is already on this phone: reports, calendar days, lab results and care notes.
 * Each event names its source and opens it. Nothing is read from a report here and nothing is added.
 */
@Composable fun CareTimeline(
    vm: CareModel, reports: List<CareReport>, cancerType: (CareReport) -> String?, folderOf: (CareReport) -> String,
    onOpenReport: (CareReport, Int) -> Unit, onReadNote: (CareVersion) -> Unit,
    onCalendarAt: ((Long) -> Unit)? = null, onLabs: (() -> Unit)? = null, onCare: (() -> Unit)? = null, onClose: () -> Unit,
) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle()
    val notes by vm.versions.collectAsStateWithLifecycle()
    val person by vm.selected.collectAsStateWithLifecycle()
    var filterName by rememberSaveable { mutableStateOf(TimelineFilter.ALL.name) }
    val filter = TimelineFilter.entries.firstOrNull { it.name == filterName } ?: TimelineFilter.ALL
    val now = remember(items, reports, notes) { System.currentTimeMillis() }
    val all = remember(person, items, reports, notes) { CareTimelineRules.events(person, items, reports, notes, cancerType, folderOf, now) }
    val summary = CareTimelineRules.summary(all)
    val shown = CareTimelineRules.filter(all, filter)
    val months = CareTimelineRules.byMonth(shown)
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", careLocale(hindi))
    BackHandler { onClose() }
    fun canOpen(e: TimelineEvent) = when (e.source) {
        TimelineSource.REPORT -> reports.any { it.id == e.sourceId }; TimelineSource.CALENDAR -> onCalendarAt != null; TimelineSource.LAB_HISTORY -> onLabs != null
        TimelineSource.CARE_NOTE -> onCare != null; TimelineSource.GOALS -> notes.any { it.id == e.sourceId }
    }
    fun open(e: TimelineEvent) { when (e.source) {
        TimelineSource.REPORT -> reports.firstOrNull { it.id == e.sourceId }?.let { onOpenReport(it, e.page.coerceAtLeast(1)) }
        TimelineSource.CALENDAR -> onCalendarAt?.invoke(e.epoch); TimelineSource.LAB_HISTORY -> onLabs?.invoke(); TimelineSource.CARE_NOTE -> onCare?.invoke()
        TimelineSource.GOALS -> notes.firstOrNull { it.id == e.sourceId }?.let(onReadNote)
    } }
    fun dateText(e: TimelineEvent) = date(e.epoch) + if (e.dateIsFileDate) t(" (date added)", " (जोड़ने की तारीख़)") else ""
    fun description(e: TimelineEvent) = listOf(dateText(e), timelineHeadline(e, t), t("From: ", "स्रोत: ") + timelineSource(e, t)).joinToString(". ")

    LazyColumn(Modifier.fillMaxSize().testTag("care-timeline"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to documents", "कागज़ात पर वापस")) }
            Column(Modifier.weight(1f)) { Heading(t("Care timeline", "देखभाल की समय-रेखा"), 28); Text(t("Key events from your documents", "आपके कागज़ों की मुख्य बातें"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } }
        if (vm.demoMode) item { Text(t("Practice record: these dates are sample data, not a real person's treatment.", "अभ्यास रिकॉर्ड: ये तारीख़ें नमूना हैं, किसी असली व्यक्ति का इलाज नहीं।"), style = MaterialTheme.typography.bodySmall, color = RustText, modifier = Modifier.testTag("timeline-practice-note")) }
        item { SurfaceCard {
            Text(t("At a glance", "एक नज़र में"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            listOf(
                Triple(t("Last chemotherapy", "आख़िरी कीमो"), summary.chemo, "chemo"), Triple(t("Last radiotherapy", "आख़िरी रेडियोथेरेपी"), summary.radio, "radio"),
                Triple(t("Last scan", "आख़िरी स्कैन"), summary.scan, "scan"), Triple(t("Last biopsy or histopathology", "आख़िरी बायोप्सी या हिस्टोपैथोलॉजी"), summary.biopsy, "biopsy"),
            ).forEach { (label, event, tag) ->
                val value = event?.let { dateText(it) } ?: t("Not recorded", "दर्ज नहीं")
                val source = event?.let { t("From: ", "स्रोत: ") + timelineSource(it, t) }
                val tappable = event != null && canOpen(event)
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (tappable) Modifier.clickable { open(event!!) } else Modifier).padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(label, value, source).joinToString(". ") }.testTag("timeline-summary-$tag"), verticalArrangement = Arrangement.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontWeight = FontWeight.SemiBold, color = if (event == null) MaterialTheme.colorScheme.onSurfaceVariant else Ink)
                    if (source != null) Text(source, style = MaterialTheme.typography.bodySmall, color = if (tappable) RustText else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimelineFilter.entries.forEach { option -> FilterChip(selected = filter == option, onClick = { filterName = option.name }, label = { Text(timelineFilterLabel(option, t)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("timeline-filter-${option.name}")) }
        } }
        if (shown.isEmpty()) item { SurfaceCard {
            Icon(Icons.Default.Timeline, null, Modifier.size(34.dp), tint = Rust)
            Spacer(Modifier.height(8.dp))
            Text(if (all.isEmpty()) t("Nothing on the timeline yet", "समय-रेखा में अभी कुछ नहीं") else t("Nothing here", "यहाँ कुछ नहीं"), fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            Text(if (all.isEmpty()) t("Add reports, lab results and chemo or radiotherapy days. They appear here with where they came from.", "रिपोर्टें, जाँच के नतीजे और कीमो या रेडियोथेरेपी के दिन जोड़ें। वे यहाँ अपने स्रोत के साथ दिखेंगे।") else t("Try another filter.", "कोई और फ़िल्टर चुनें।"), style = MaterialTheme.typography.bodyMedium)
            if (all.isNotEmpty()) TextButton(onClick = { filterName = TimelineFilter.ALL.name }) { Text(t("Show all", "सब दिखाएँ")) }
        } }
        months.forEach { (month, events) ->
            item(key = "month-$month") { Text(month.atDay(1).format(monthFormat), Modifier.a11yHeading().padding(top = 6.dp), fontSize = 20.sp, fontFamily = SaharaTitleFont) }
            items(events, key = { it.id }) { e ->
                val tappable = canOpen(e)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = CircleShape, color = timelineColor(e.kind), modifier = Modifier.size(36.dp)) { Box(contentAlignment = Alignment.Center) { Icon(timelineIcon(e.kind), null, Modifier.size(20.dp), tint = Ink) } }
                        Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Surface(modifier = Modifier.weight(1f).padding(bottom = 10.dp).heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = description(e) }.testTag("timeline-event-${e.id}"),
                        onClick = { open(e) }, enabled = tappable, shape = RoundedCornerShape(16.dp), color = Paper) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(dateText(e) + " · " + timelineLabel(e, t), style = MaterialTheme.typography.labelMedium, color = RustText)
                            Text(timelineHeadline(e, t), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(t("From: ", "स्रोत: ") + timelineSource(e, t), style = MaterialTheme.typography.bodySmall, color = if (tappable) RustText else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (e.dateIsFileDate && e.isFile) Text(t("Report date not recorded. This is the day the file was added.", "रिपोर्ट की तारीख़ दर्ज नहीं। यह फ़ाइल जोड़ने का दिन है।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Text(t("Dates come from reports you added, Calendar days and results you copied. A Calendar day marks a day you noted; it does not confirm a treatment was given. Everything stays on this phone.", "तारीख़ें आपकी जोड़ी रिपोर्टों, कैलेंडर के दिनों और कॉपी किए नतीजों से हैं। कैलेंडर का दिन आपका नोट किया दिन है; इससे इलाज दिए जाने की पुष्टि नहीं होती। सब कुछ इसी फ़ोन पर रहता है।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
