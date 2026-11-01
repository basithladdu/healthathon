@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
    var toggled by rememberSaveable { mutableStateOf("") }; var showAllMonths by rememberSaveable { mutableStateOf("") }
    fun flip(set: String, key: String) = set.split(',').filter { it.isNotBlank() }.let { if (key in it) it - key else it + key }.joinToString(",")
    val shortFormat = DateTimeFormatter.ofPattern("d MMM", careLocale(careHindi()))
    fun shortDate(epoch: Long) = java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault()).format(shortFormat)
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
        if (vm.demoMode) item { Surface(shape = RoundedCornerShape(14.dp), color = SaharaSoftGold, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = DoctorAccent); Spacer(Modifier.width(8.dp))
                Text(t("Practice record: these dates are sample data, not a real person's treatment.", "अभ्यास रिकॉर्ड: ये तारीख़ें नमूना हैं, किसी असली व्यक्ति का इलाज नहीं।"), fontSize = 13.sp, color = DoctorCardInk)
            }
        } }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("At a glance", "एक नज़र में"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            val tiles = listOf(
                GlanceTile(t("Last chemotherapy", "आख़िरी कीमो"), summary.chemo, "chemo", Icons.Default.Vaccines, Peach),
                GlanceTile(t("Last radiotherapy", "आख़िरी रेडियोथेरेपी"), summary.radio, "radio", Icons.Default.Healing, SaharaLavender),
                GlanceTile(t("Last scan", "आख़िरी स्कैन"), summary.scan, "scan", Icons.Default.DocumentScanner, SaharaSoftBlue),
                GlanceTile(t("Last biopsy or histopathology", "आख़िरी बायोप्सी या हिस्टोपैथोलॉजी"), summary.biopsy, "biopsy", Icons.Default.Science, Mint),
            )
            tiles.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { tile ->
                    val event = tile.event
                    val value = event?.let { dateText(it) } ?: t("Not recorded", "दर्ज नहीं")
                    val source = event?.let { t("From: ", "स्रोत: ") + timelineSource(it, t) }
                    val tappable = event != null && canOpen(event)
                    Surface(shape = RoundedCornerShape(18.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline),
                        modifier = Modifier.weight(1f).fillMaxHeight().then(if (tappable) Modifier.clickable { open(event!!) } else Modifier)
                            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(tile.label, value, source).joinToString(". ") }.testTag("timeline-summary-${tile.tag}")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(shape = CircleShape, color = tile.tint) { Icon(tile.icon, null, Modifier.padding(7.dp).size(18.dp), tint = DoctorAccent) }
                            Text(tile.label, fontSize = 12.sp, color = DoctorCardSub, maxLines = 2)
                            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (event == null) DoctorCardSub else DoctorCardInk)
                            if (source != null) Text(source, fontSize = 11.sp, color = if (tappable) RustText else DoctorCardSub, maxLines = 1)
                        }
                    }
                }
            } }
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
        items(months, key = { "month-${it.first}" }) { (month, events) ->
            val key = month.toString(); val newest = months.first().first == month
            val isOpen = newest != (key in toggled.split(',')) // older months start closed; a tap flips the default
            val expandedAll = key in showAllMonths.split(',')
            val visible = if (expandedAll) events else events.take(5)
            Column(Modifier.fillMaxWidth().animateItem()) {
                Surface(onClick = { toggled = flip(toggled, key) }, shape = RoundedCornerShape(14.dp), color = SaharaSoftGold, border = BorderStroke(1.dp, DoctorHairline),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).a11yHeading().testTag("timeline-month-$key")) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(month.atDay(1).format(monthFormat) + " · " + events.size, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                        Icon(if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (isOpen) t("Hide this month", "यह महीना छिपाएँ") else t("Show this month", "यह महीना दिखाएँ"), tint = DoctorCardInk)
                    }
                }
                AnimatedVisibility(isOpen, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                    Column(Modifier.padding(top = 8.dp)) {
                        visible.forEach { e ->
                            val tappable = canOpen(e)
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(shape = CircleShape, color = timelineColor(e.kind), modifier = Modifier.size(28.dp)) { Box(contentAlignment = Alignment.Center) { Icon(timelineIcon(e.kind), null, Modifier.size(16.dp), tint = Ink) } }
                                    Box(Modifier.width(2.dp).weight(1f).background(DoctorHairline))
                                }
                                // A row that opens something sits on a light card with an arrow, so it reads as tappable.
                                Surface(modifier = Modifier.weight(1f).padding(start = 6.dp, bottom = 8.dp).heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = description(e) }.testTag("timeline-event-${e.id}"),
                                    onClick = { open(e) }, enabled = tappable, shape = RoundedCornerShape(14.dp), color = if (tappable) Paper else Color.Transparent,
                                    border = if (tappable) BorderStroke(1.dp, DoctorHairline) else null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(shortDate(e.epoch), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = RustText)
                                            if (e.dateIsFileDate) Text(" " + t("(date added)", "(जोड़ने की तारीख़)"), style = MaterialTheme.typography.labelSmall, color = DoctorCardSub)
                                        }
                                        Text(timelineHeadline(e, t), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(timelineSource(e, t), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    if (tappable) Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(t("View", "देखें"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RustText)
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Rust)
                                    }
                                    }
                                }
                            }
                        }
                        if (events.size > 5) TextButton(onClick = { showAllMonths = flip(showAllMonths, key) }, modifier = Modifier.heightIn(min = 48.dp).testTag("timeline-more-$key")) {
                            Text(if (expandedAll) t("Show fewer", "कम दिखाएँ") else t("Show all ${events.size}", "सभी ${events.size} दिखाएँ"))
                        }
                    }
                }
            }
        }
        if (shown.any { it.dateIsFileDate && it.isFile }) item { Text(t("(date added): Report date not recorded. This is the day the file was added.", "(जोड़ने की तारीख़): रिपोर्ट की तारीख़ दर्ज नहीं। यह फ़ाइल जोड़ने का दिन है।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, modifier = Modifier.testTag("timeline-date-footnote")) }
        item { Text(t("Dates come from reports you added, Calendar days and results you copied. A Calendar day marks a day you noted; it does not confirm a treatment was given. Everything stays on this phone.", "तारीख़ें आपकी जोड़ी रिपोर्टों, कैलेंडर के दिनों और कॉपी किए नतीजों से हैं। कैलेंडर का दिन आपका नोट किया दिन है; इससे इलाज दिए जाने की पुष्टि नहीं होती। सब कुछ इसी फ़ोन पर रहता है।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** One "At a glance" tile: what it is, the newest matching event (or none), and its colour. */
private class GlanceTile(val label: String, val event: TimelineEvent?, val tag: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: androidx.compose.ui.graphics.Color)
