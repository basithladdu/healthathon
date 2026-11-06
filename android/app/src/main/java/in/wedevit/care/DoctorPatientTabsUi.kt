package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One doctor-side patient tab. [section] is the route key EmbeddedPatientCare already understands; "Labs" is drawn here. */
internal data class DoctorPatientTab(val section: String, val en: String, val hi: String, val icon: ImageVector, val color: Color)

/** The patient's tabs, most used first (3 Nov meeting: symptoms and distress, labs, goals of care, visits, medicines). */
internal val doctorPatientTabs = listOf(
    // Goals of care first: it opens Ritu's legal form with the recording (record → Gemini → ticks), not a menu.
    DoctorPatientTab("GoalsOfCare", "Goals of care", "देखभाल के लक्ष्य", Icons.Outlined.Assignment, DoctorCardConversation),
    DoctorPatientTab("CarePlan", "Care plan & card", "देखभाल योजना और कार्ड", Icons.Outlined.Description, DoctorCardReport),
    DoctorPatientTab("Progress", "Symptoms & distress", "लक्षण और परेशानी", Icons.Outlined.Insights, DoctorCardProgress),
    DoctorPatientTab("Labs", "Lab reports", "जाँच रिपोर्टें", Icons.Outlined.Science, DoctorCardOverview),
    DoctorPatientTab("Conversations", "Conversations", "बातचीत", Icons.Outlined.Forum, DoctorCardConversation),
    DoctorPatientTab("Visits", "Visits", "विज़िट", Icons.Outlined.CalendarMonth, DoctorCardVisit),
    DoctorPatientTab("Medicines", "Medicines", "दवाइयाँ", Icons.Outlined.Medication, DoctorCardMedicine),
    DoctorPatientTab("Reports", "Documents", "कागज़ात", Icons.Outlined.FolderOpen, DoctorCardReport),
)

/** The earlier practice record (DoctorDemoUi) still routes these six sections, Overview included. */
internal val doctorLegacyHubTabs = listOf(
    DoctorPatientTab("Summary", "Overview", "सार", Icons.Outlined.GridView, DoctorCardOverview),
    DoctorPatientTab("Progress", "Progress", "प्रगति", Icons.Outlined.Insights, DoctorCardProgress),
    DoctorPatientTab("Medicines", "Medicines", "दवाइयाँ", Icons.Outlined.Medication, DoctorCardMedicine),
    DoctorPatientTab("Conversations", "Conversations", "बातचीत", Icons.Outlined.Forum, DoctorCardConversation),
    DoctorPatientTab("Visits", "Visits", "विज़िट", Icons.Outlined.CalendarMonth, DoctorCardVisit),
    DoctorPatientTab("Reports", "Reports", "रिपोर्टें", Icons.Outlined.FolderOpen, DoctorCardReport),
)

/** Two panes (list beside detail) from 840dp, the Material "expanded" width: tablets and CARE on a computer. */
internal fun doctorTwoPane(widthDp: Float): Boolean = widthDp >= 840f

/** What the detail pane shows. The old Overview ("Summary") and the hub itself both open Symptoms & distress. */
internal fun doctorContentSection(section: String): String = if (section == "Hub" || section == "Summary") "Progress" else section

/** How far back a trend chart reaches. [days] null means from the first entry. */
internal enum class DoctorTrendRange(val days: Long?, val en: String, val hi: String) {
    WEEKS_2(14, "2 weeks", "2 हफ़्ते"),
    MONTH_1(30, "1 month", "1 महीना"),
    MONTHS_3(91, "3 months", "3 महीने"),
    ALL(null, "All", "सब");
    companion object { fun of(name: String) = entries.firstOrNull { it.name == name } ?: ALL }
}

internal object DoctorTrendRules {
    /** One value per day: the highest noted that day, oldest first. Unreadable dates are left out. */
    fun daily(points: List<DatedRating>): List<Pair<LocalDate, Int>> = points
        .mapNotNull { p -> runCatching { LocalDate.parse(p.date) }.getOrNull()?.let { it to p.value } }
        .groupBy({ it.first }, { it.second }).map { (day, values) -> day to values.max() }.sortedBy { it.first }

    /** Same, from epoch-millisecond timestamps (distress answers, lab results). */
    fun <T : Comparable<T>> dailyAt(values: List<Pair<Long, T>>, zone: ZoneId): List<Pair<LocalDate, T>> = values
        .filter { it.first > 0 }.map { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() to it.second }
        .groupBy({ it.first }, { it.second }).map { (day, v) -> day to v.max() }.sortedBy { it.first }

    /** Entries inside the range ending today; nothing dated after today. */
    fun <T> within(days: List<Pair<LocalDate, T>>, range: DoctorTrendRange, today: LocalDate): List<Pair<LocalDate, T>> =
        days.filter { (day, _) -> !day.isAfter(today) && (range.days == null || !day.isBefore(today.minusDays(range.days - 1))) }

    /** First day of the chart: the range start, or the first entry for All. */
    fun start(range: DoctorTrendRange, first: LocalDate?, today: LocalDate): LocalDate =
        range.days?.let { today.minusDays(it - 1) } ?: (first?.takeIf { !it.isAfter(today) } ?: today)

    /** Weekly dates up to a month, month starts beyond that. The end date comes first so it is never the one dropped. */
    fun xLabels(start: LocalDate, end: LocalDate, hindi: Boolean): List<ChartXLabel> {
        val span = ChronoUnit.DAYS.between(start, end)
        val dates = when {
            span <= 0 -> emptyList()
            span <= 45 -> generateSequence(start) { it.plusDays(7) }.takeWhile { it.isBefore(end) }.toList()
            else -> generateSequence(start.withDayOfMonth(1).let { if (it.isBefore(start)) it.plusMonths(1) else it }) { it.plusMonths(1) }.takeWhile { it.isBefore(end) }.toList()
        }
        val pattern = if (span <= 45) "d MMM" else "MMM"
        return listOf(ChartXLabel(end.toEpochDay().toDouble(), end.format(careDateFormat("d MMM", hindi)), strong = true)) +
            dates.map { ChartXLabel(it.toEpochDay().toDouble(), it.format(careDateFormat(pattern, hindi))) }
    }

    /** Most severe first: the latest number, then the newest entry. */
    fun bySeverity(rows: List<DoctorSymptomRow>): List<DoctorSymptomRow> =
        rows.sortedWith(compareByDescending<DoctorSymptomRow> { it.latest.value }.thenByDescending { "${it.latest.date}T${it.latest.time}" })

    /** Highest number in the range, or null when nothing was noted then. */
    fun highest(points: List<DatedRating>, range: DoctorTrendRange, today: LocalDate): Int? = within(daily(points), range, today).maxOfOrNull { it.second }
}

/** A small round "i" that opens the explanation the screen no longer prints. */
@Composable internal fun DoctorInfoIcon(title: String, text: String, tag: String) {
    val t = careText()
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp).testTag(tag)) { Icon(Icons.Outlined.Info, t("About this", "इसके बारे में"), Modifier.size(20.dp), tint = DoctorCardSub) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = { Text(text, lineHeight = 22.sp) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(t("Close", "बंद करें")) } })
}

/** 2 weeks · 1 month · 3 months · All. One choice changes every chart on the screen. */
@Composable internal fun DoctorRangeSelector(range: DoctorTrendRange, onRange: (DoctorTrendRange) -> Unit, modifier: Modifier = Modifier) {
    val t = careText()
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("doctor-trend-range"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DoctorTrendRange.entries.forEach { r ->
            FilterChip(selected = r == range, onClick = { onRange(r) }, label = { Text(t(r.en, r.hi), fontSize = 14.sp) },
                shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-trend-range-${r.name}"),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DoctorAccent, selectedLabelColor = Paper, containerColor = Paper, labelColor = DoctorCardSub))
        }
    }
}

private val doctorScoreBands = listOf(ChartBand(0.0, 3.5, ChartBands.Mild), ChartBand(3.5, 6.5, ChartBands.Moderate), ChartBand(6.5, 10.0, ChartBands.Severe))

/** A 0–10 trend over the chosen range, one point per day on a true time axis. Values are written on the points when there are few. */
@Composable internal fun DoctorScoreTrend(name: String, days: List<Pair<LocalDate, Int>>, range: DoctorTrendRange, today: LocalDate, tag: String) {
    val t = careText(); val hindi = careHindi()
    val shown = DoctorTrendRules.within(days, range, today)
    if (shown.isEmpty()) { Text(t("No data", "कोई डेटा नहीं"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.testTag("$tag-empty")); return }
    val start = DoctorTrendRules.start(range, shown.first().first, today)
    val (lo, hi) = start.toEpochDay().toDouble() to today.toEpochDay().toDouble()
    val description = "$name: " + shown.joinToString("; ") { (day, v) -> "${day.format(careDateFormat("d MMM yyyy", hindi))} $v ${ChartBands.word(v, hindi)}" }
    CareLineChart(
        points = shown.map { (day, v) -> ChartPoint(day.toEpochDay().toDouble(), v.toDouble(), ChartBands.color(v)) },
        yMin = 0.0, yMax = 10.0, yTicks = listOf(0.0, 5.0, 10.0), yText = { it.toInt().toString() },
        xLabels = DoctorTrendRules.xLabels(start, today, hindi), description = description, latestText = shown.last().second.toString(), tag = tag,
        height = 170.dp, lineColor = DoctorCardSub.copy(alpha = .7f), bands = doctorScoreBands,
        xMin = if (hi > lo) lo else lo - .5, xMax = if (hi > lo) hi else hi + .5,
        pointText = { v: Double -> v.toInt().toString() }.takeIf { shown.size <= 14 },
    )
}

/** A measured value (a lab result) over the chosen range, with the printed normal range as a band when the report gave one. */
@Composable internal fun DoctorValueTrend(name: String, unit: String, days: List<Pair<LocalDate, Double>>, printed: Pair<Double?, Double?>, range: DoctorTrendRange, today: LocalDate, tag: String) {
    val t = careText(); val hindi = careHindi()
    val shown = DoctorTrendRules.within(days, range, today)
    if (shown.isEmpty()) { Text(t("No data", "कोई डेटा नहीं"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.testTag("$tag-empty")); return }
    val start = DoctorTrendRules.start(range, shown.first().first, today)
    val (lo, hi) = start.toEpochDay().toDouble() to today.toEpochDay().toDouble()
    val edges = shown.map { it.second } + listOfNotNull(printed.first, printed.second)
    val min = edges.min(); val max = edges.max()
    val pad = ((max - min).takeIf { it > 0 } ?: kotlin.math.abs(max).coerceAtLeast(1.0)) * .15
    val yMin = min - pad; val yMax = max + pad
    val bands = if (printed.first != null || printed.second != null) listOf(ChartBand(printed.first ?: yMin, printed.second ?: yMax, Mint, .7f)) else emptyList()
    val description = "$name ($unit): " + shown.joinToString("; ") { (day, v) -> "${day.format(careDateFormat("d MMM yyyy", hindi))} ${doctorNumber(v)}" }
    CareLineChart(
        points = shown.map { (day, v) -> ChartPoint(day.toEpochDay().toDouble(), v) },
        yMin = yMin, yMax = yMax, yTicks = listOf(yMin, (yMin + yMax) / 2, yMax), yText = ::doctorNumber,
        xLabels = DoctorTrendRules.xLabels(start, today, hindi), description = description, latestText = doctorNumber(shown.last().second), tag = tag,
        height = 170.dp, bands = bands, xMin = if (hi > lo) lo else lo - .5, xMax = if (hi > lo) hi else hi + .5,
        pointText = ::doctorNumber.takeIf { shown.size <= 10 },
    )
}

internal fun doctorNumber(v: Double): String = v.toBigDecimal().setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Printed low/high from a lab entry's range fields or text such as "13.0–17.0". Never guessed. */
internal fun doctorLabRange(data: CareEntry): Pair<Double?, Double?> {
    val low = data.rangeLow.toDoubleOrNull(); val high = data.rangeHigh.toDoubleOrNull()
    if (low != null || high != null) return low to high
    val nums = Regex("""\d+(?:\.\d+)?""").findAll(data.rangeText).mapNotNull { it.value.toDoubleOrNull() }.toList()
    return if (nums.size == 2 && nums[0] <= nums[1]) nums[0] to nums[1] else null to null
}

/** Phone: one row of tabs on a white track (u-FIR tab grammar). The open tab is a solid fill in its own colour; the row scrolls to keep it in view. */
@Composable internal fun DoctorPatientTabRow(selected: String, onSection: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = careText()
    val scroll = rememberScrollState()
    val positions = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(selected, positions[selected]) { positions[selected]?.let { scroll.animateScrollTo((it - 48).coerceAtLeast(0), SaharaMotion.tween(SaharaMotion.SELECT_MS * 2)) } }
    Box(modifier.fillMaxWidth().background(Cream).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).border(1.dp, DoctorHairline, RoundedCornerShape(14.dp)).horizontalScroll(scroll).padding(4.dp).testTag("doctor-patient-tabs"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            doctorPatientTabs.forEach { tab ->
                val on = tab.section == selected
                val tone = doctorTabTone(tab.section)
                val fill by animateColorAsState(if (on) tone.ink else Color.Transparent, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "tab fill")
                val ink by animateColorAsState(if (on) Color.White else DoctorCardSub, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "tab ink")
                Row(Modifier.onGloballyPositioned { positions[tab.section] = it.positionInParent().x.toInt() }.heightIn(min = 48.dp).background(fill, RoundedCornerShape(10.dp))
                    .selectable(selected = on, role = Role.Tab) { if (!on) onSection(tab.section) }.padding(horizontal = 12.dp).testTag("doctor-tab-${tab.section}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(tab.icon, null, Modifier.size(18.dp), tint = if (on) Color.White else tone.ink)
                    Text(t(tab.en, tab.hi), fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = ink, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * The doctor's patient screen. Phone: the hub, then one tab at a time with the tab row on top (one tap between tabs).
 * 840dp and wider: the hub stays on the left as the list and the chosen tab fills the right.
 * [hub] gets (pane, selected): pane is true in the two-pane layout. [care] draws every section except "Labs".
 */
@Composable internal fun DoctorPatientWorkspace(vm: CareModel, personId: String, section: String, onSection: (String) -> Unit,
    hub: @Composable (pane: Boolean, selected: String?) -> Unit, care: @Composable (String) -> Unit) {
    val shown = doctorContentSection(section)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("doctor-patient-workspace")) {
        if (doctorTwoPane(maxWidth.value)) Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(380.dp).fillMaxHeight().testTag("doctor-patient-pane-list")) { hub(true, shown) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(DoctorHairline))
            Box(Modifier.weight(1f).fillMaxHeight().testTag("doctor-patient-pane-detail")) { DoctorPatientSectionContent(vm, personId, shown, care) }
        } else if (section == "Hub") hub(false, null)
        else Column(Modifier.fillMaxSize()) {
            DoctorPatientTabRow(shown, onSection)
            HorizontalDivider(color = DoctorHairline)
            // Tab change: the new tab fades in and drifts from the side it sits on in the row. One instance only, so its saved state stays single.
            val order = doctorPatientTabs.indexOfFirst { it.section == shown }
            var last by remember { mutableIntStateOf(order) }
            val drift = remember { Animatable(1f) }
            var sign by remember { mutableFloatStateOf(1f) }
            LaunchedEffect(order) { if (order != last && SaharaMotion.enabled) { sign = if (order > last) 1f else -1f; drift.snapTo(0f); last = order; drift.animateTo(1f, SaharaMotion.tween(SaharaMotion.ENTER_MS, easing = SaharaMotion.Emphasized)) } else last = order }
            Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { val p = if (order != last && SaharaMotion.enabled) 0f else drift.value; alpha = p; translationX = (1f - p) * sign * 24.dp.toPx() }) { DoctorPatientSectionContent(vm, personId, shown, care) }
        }
    }
}

@Composable private fun DoctorPatientSectionContent(vm: CareModel, personId: String, section: String, care: @Composable (String) -> Unit) {
    if (section == "Labs") DoctorLabsTab(vm, personId) else care(section)
}

/** Lab reports tab: one row per test, newest result first (red when outside the printed range). Tap a row for its trend over the range. */
@Composable internal fun DoctorLabsTab(vm: CareModel, personId: String) {
    val t = careText(); val hindi = careHindi()
    val entries by vm.items.collectAsStateWithLifecycle()
    var full by rememberSaveable(personId) { mutableStateOf(false) }
    var rangeKey by rememberSaveable(personId) { mutableStateOf(DoctorTrendRange.ALL.name) }
    var open by rememberSaveable(personId) { mutableStateOf("") }
    BackHandler(full) { full = false }
    if (full) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { full = false }, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp).testTag("doctor-labs-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text(t("Lab trends", "जाँच के रुझान")) }
            Box(Modifier.weight(1f).fillMaxWidth()) { CareLabs(vm) }
        }
        return
    }
    val range = DoctorTrendRange.of(rangeKey)
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val groups = remember(entries, personId) { labGroups(entries.filter { it.personId == personId }) }
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-labs"), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Heading(t("Lab reports", "जाँच रिपोर्टें"), 26); Spacer(Modifier.weight(1f))
                if (groups.isNotEmpty()) TextButton(onClick = { full = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-labs-all")) { Text(t("All results", "सभी नतीजे"), color = RustText) }
            }
        }
        item { DoctorRangeSelector(range, { rangeKey = it.name }) }
        if (groups.isEmpty()) item {
            Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Science, null, Modifier.size(28.dp), tint = DoctorAccent)
                    Text(t("No lab results yet", "अभी कोई जाँच नतीजा नहीं"), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.testTag("doctor-labs-empty"))
                    OutlinedButton(onClick = { full = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("All results", "सभी नतीजे")) }
                }
            }
        }
        items(groups, key = { values -> "${values.first().first.title.trim().lowercase()}:${values.first().second.unit.trim()}" }) { values ->
            val key = "${values.first().first.title.trim().lowercase()}:${values.first().second.unit.trim()}"
            val (latestItem, latest) = values.first()
            val printed = doctorLabRange(latest)
            val latestValue = latest.value.toDoubleOrNull()
            val previous = values.getOrNull(1)?.second?.value?.toDoubleOrNull()
            val outside = latestValue != null && ((printed.first != null && latestValue < printed.first!!) || (printed.second != null && latestValue > printed.second!!))
            val arrow = if (latestValue == null || previous == null) "" else when { latestValue > previous -> "↑"; latestValue < previous -> "↓"; else -> "→" }
            Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, if (outside) ChartBands.Severe.copy(alpha = .5f) else DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("doctor-lab-$key")) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { open = if (open == key) "" else key }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(latestItem.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                            Text(labDateText(latestItem, t), fontSize = 14.sp, color = DoctorCardSub)
                        }
                        Text("${latest.value} ${latest.unit}".trim(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (outside) ChartBands.Severe else DoctorCardInk, textAlign = TextAlign.End)
                        if (arrow.isNotEmpty()) Text(" $arrow", fontSize = 15.sp, color = DoctorCardSub, fontWeight = FontWeight.SemiBold)
                    }
                    if (open == key) {
                        val days = DoctorTrendRules.dailyAt(values.mapNotNull { (item, entry) -> entry.value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { labEpoch(item) to it } }, zone)
                        DoctorValueTrend(latestItem.title, latest.unit, days, printed, range, today, "doctor-lab-trend-$key")
                        if (printed.first != null || printed.second != null) Text("${printed.first?.let(::doctorNumber) ?: "–"}–${printed.second?.let(::doctorNumber) ?: "–"} ${latest.unit}", fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }

    }
}
