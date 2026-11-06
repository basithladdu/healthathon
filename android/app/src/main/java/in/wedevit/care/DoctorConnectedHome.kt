package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Real connected-patient rules for the signed-in doctor workspace. Never reads sample or test records. */
internal object DoctorConnectedHome {
    /** Short ID a family can read out to their doctor: "SH-" and the first 8 characters of the care-profile ID. */
    fun patientCode(personId: String): String = "SH-" + personId.filter { it.isLetterOrDigit() }.take(8).uppercase(Locale.ROOT)

    /** Name, short patient ID or full care-profile ID. Only ever filters people this account can already open. */
    fun matches(person: Person, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (person.name.contains(q, ignoreCase = true) || person.id.equals(q, ignoreCase = true)) return true
        val code = q.uppercase(Locale.ROOT).replace(" ", "").removePrefix("SH-").removePrefix("SH").filter { it.isLetterOrDigit() }
        return code.length >= 4 && code.all { it in '0'..'9' || it in 'A'..'F' } &&
            person.id.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT).startsWith(code)
    }

    fun search(people: List<Person>, query: String): List<Person> = people.filter { matches(it, query) }

    data class Visit(val person: Person, val item: CareItem)
    /** Every open visit across the doctor's connected patients, soonest first; undated visits last. */
    fun upcomingVisits(people: List<Person>, items: List<CareItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Visit> =
        people.flatMap { person -> doctorUpcomingVisits(items, person.id, now, zone).map { Visit(person, it) } }
            .sortedWith(compareBy<Visit> { if (it.item.due == 0L) Long.MAX_VALUE else it.item.due }.thenBy { it.person.name })

    /** The newest reported check-in per patient in the last [days] days, newest first. */
    fun recentCheckIns(people: List<Person>, items: List<CareItem>, now: Long, days: Int = 7): List<Pair<Person, CareItem>> =
        people.mapNotNull { person -> doctorCheckIns(items, person.id, now).firstOrNull { now - it.created <= days * 86_400_000L }?.let { person to it } }
            .sortedByDescending { it.second.created }

    enum class Verification { UNKNOWN, NOT_SUBMITTED, PENDING, APPROVED, REJECTED, SUSPENDED }
    fun verification(profile: CareClinicianProfile?, loaded: Boolean): Verification = when {
        !loaded -> Verification.UNKNOWN
        profile == null -> Verification.NOT_SUBMITTED
        else -> when (profile.status) { "pending" -> Verification.PENDING; "approved" -> Verification.APPROVED; "rejected" -> Verification.REJECTED; "suspended" -> Verification.SUSPENDED; else -> Verification.UNKNOWN }
    }

    fun verificationTitle(state: Verification, hindi: Boolean): String = when (state) {
        Verification.UNKNOWN -> careTr(hindi, "Checking…", "देखा जा रहा है…")
        Verification.NOT_SUBMITTED -> careTr(hindi, "Not verified", "सत्यापित नहीं")
        Verification.PENDING -> careTr(hindi, "Waiting for approval", "मंज़ूरी का इंतज़ार")
        Verification.APPROVED -> careTr(hindi, "Verified", "सत्यापित")
        Verification.REJECTED -> careTr(hindi, "Not approved", "मंज़ूर नहीं")
        Verification.SUSPENDED -> careTr(hindi, "Approval on hold", "मंज़ूरी रोकी गई है")
    }

    fun verificationBody(state: Verification, hindi: Boolean): String = when (state) {
        Verification.UNKNOWN -> careTr(hindi, "Asking the server for your status.", "सर्वर से आपकी स्थिति ली जा रही है।")
        Verification.NOT_SUBMITTED -> careTr(hindi, "Submit your registered name, medical council and registration number. Patients can add you as their doctor only after approval.", "अपना पंजीकृत नाम, मेडिकल काउंसिल और पंजीकरण नंबर जमा करें। मंज़ूरी के बाद ही मरीज़ आपको अपने डॉक्टर के रूप में जोड़ सकते हैं।")
        Verification.PENDING -> careTr(hindi, "The Sahara team checks every registration by hand. Nothing is approved automatically, so this can take a while. Until then, a doctor invitation code will not work. Tap \"Check again\" later.", "Sahara टीम हर पंजीकरण खुद जाँचती है। यह अपने आप मंज़ूर नहीं होता, इसलिए इसमें समय लग सकता है। तब तक डॉक्टर वाला निमंत्रण कोड काम नहीं करेगा। बाद में \"फिर से देखें\" दबाएँ।")
        Verification.APPROVED -> careTr(hindi, "Doctor invitation codes from a patient or family will now work. Each patient decides what you can see.", "मरीज़ या परिवार से मिला डॉक्टर निमंत्रण कोड अब काम करेगा। हर मरीज़ खुद तय करता है कि आप क्या देख सकते हैं।")
        Verification.REJECTED, Verification.SUSPENDED -> careTr(hindi, "Doctor invitation codes will not work for now. Correct your details and submit them again.", "डॉक्टर निमंत्रण कोड अभी काम नहीं करेंगे। विवरण ठीक करके फिर से जमा करें।")
    }

    fun checkedAt(at: Long, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        (careTr(hindi, "Last checked ", "आख़िरी बार देखा: ")) + DateTimeFormatter.ofPattern("h:mm a", careLocale(hindi)).withZone(zone).format(Instant.ofEpochMilli(at))
}

/** The app's shared English/Hindi choice, or a phone set to Hindi. */
@Composable internal fun doctorUiHindi(): Boolean = careHindi() || Locale.getDefault().language == "hi"

internal fun doctorWhen(item: CareItem, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    if (item.due <= 0) return careTr(hindi, "Date not recorded", "तारीख़ दर्ज नहीं")
    val timed = CareEntryCodec.decode(item.detail)?.timed == true
    return DateTimeFormatter.ofPattern(if (timed) "EEE, d MMM · h:mm a" else "EEE, d MMM", careLocale(hindi)).withZone(zone).format(Instant.ofEpochMilli(item.due))
}

@Composable internal fun DoctorVerificationCard(state: DoctorConnectedHome.Verification, note: String, checkedAt: Long, checking: Boolean, error: String?, hindi: Boolean, onCheck: () -> Unit, onOpenForm: () -> Unit) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val (ink, color) = when (state) { DoctorConnectedHome.Verification.APPROVED -> DoctorStatusGood to DoctorStatusGoodBg; DoctorConnectedHome.Verification.PENDING, DoctorConnectedHome.Verification.UNKNOWN -> DoctorStatusWarn to DoctorStatusWarnBg; else -> DoctorStatusRest to DoctorStatusRestBg }
    val icon = when (state) { DoctorConnectedHome.Verification.APPROVED -> Icons.Default.Verified; DoctorConnectedHome.Verification.PENDING, DoctorConnectedHome.Verification.UNKNOWN -> Icons.Default.HourglassTop; else -> Icons.Default.ErrorOutline }
    Surface(color = color, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("doctor-verification-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
                Text(DoctorConnectedHome.verificationTitle(state, hindi), Modifier.weight(1f).testTag("doctor-verification-status"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            }
            if (note.isNotBlank() && state in setOf(DoctorConnectedHome.Verification.REJECTED, DoctorConnectedHome.Verification.SUSPENDED)) Text(t("Reviewer note: ", "जाँचने वाले की टिप्पणी: ") + note, style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCheck, enabled = !checking, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-verification-check")) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (checking) t("Checking…", "देखा जा रहा है…") else t("Check again", "फिर से देखें"))
                }
                if (state != DoctorConnectedHome.Verification.PENDING && state != DoctorConnectedHome.Verification.UNKNOWN)
                    TextButton(onClick = onOpenForm, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-verification-form")) {
                        Text(if (state == DoctorConnectedHome.Verification.APPROVED) t("Details", "विवरण") else t("Verify", "सत्यापित करें"))
                    }
            }
        }
    }
}

@Composable internal fun DoctorPatientSearchField(query: String, onQuery: (String) -> Unit, hindi: Boolean) {
    val focus = LocalFocusManager.current
    OutlinedTextField(query, { onQuery(it.take(80)) }, Modifier.fillMaxWidth().testTag("doctor-patient-search"), singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper),
        placeholder = { Text(careTr(hindi, "Search", "खोजें")) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, Modifier.size(48.dp)) { Icon(Icons.Default.Clear, careTr(hindi, "Clear search", "खोज साफ़ करें")) } },
        shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
}

/** Sahara section tones in the u-FIR multi-colour grammar: a saturated ink for chips and pill text, its wash for pill and avatar fills. Ink on wash and white on ink are both above 4.5:1. */
internal data class DoctorTone(val ink: Color, val wash: Color)
internal object DoctorTones {
    val Rust = DoctorTone(Color(0xFFA3361A), Color(0xFFFDE7DD))
    val Teal = DoctorTone(Color(0xFF0B6E65), Color(0xFFDDF3EF))
    val Marigold = DoctorTone(Color(0xFF9A4D00), Color(0xFFFFEBCC))
    val Plum = DoctorTone(Color(0xFF7B2D8E), Color(0xFFF4E4F7))
    val Leaf = DoctorTone(Color(0xFF276B2B), Color(0xFFE3F2E1))
    val Rose = DoctorTone(Color(0xFFB0174F), Color(0xFFFCE4EC))
    val Info = DoctorTone(Color(0xFF0B5F8A), Color(0xFFE0F0FA))
    val Red = DoctorTone(Color(0xFFB3261E), Color(0xFFFDE4E1))
    private val cycle = listOf(Rust, Teal, Plum, Rose, Leaf, Marigold, Info)
    /** A stable tone per name so the same patient always gets the same avatar colour. */
    fun forName(name: String): DoctorTone = cycle[Math.floorMod(name.trim().lowercase().hashCode(), cycle.size)]
}
/** The dark header band, in Sahara's brown family. */
internal val DoctorBand = Color(0xFF4A1D14)

/** Status pill: tone wash, tone ink, never colour alone (it always carries a word). */
@Composable internal fun DoctorPill(text: String, tone: DoctorTone, modifier: Modifier = Modifier) {
    Surface(color = tone.wash, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = tone.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** u-FIR icon chip: a small gradient tile in the section colour with a white glyph. */
@Composable internal fun DoctorIconChip(icon: ImageVector, tone: DoctorTone, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(Brush.linearGradient(listOf(lerp(tone.ink, Color.White, .22f), tone.ink)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .5f))
    }
}

/** A number that counts up from 0 when it first appears and slides to a new value when it changes. Instant when motion is off. */
@Composable internal fun doctorCountUp(value: Int): Int {
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) { target = value }
    val shown by animateIntAsState(target, SaharaMotion.tween(SaharaMotion.ENTER_MS * 2, easing = SaharaMotion.Emphasized), label = "tile count")
    return shown
}

/** Two tiles side by side only when each still has room for its label at this text size; otherwise one per row, so no word is ever split. */
internal fun doctorTilesPaired(widthDp: Float, fontScale: Float): Boolean = widthDp / fontScale.coerceAtLeast(1f) >= 320f

/** Bento launch tile: icon chip, big real count, label, optional status pill. The whole tile is the action. */
@Composable internal fun DoctorStatTile(icon: ImageVector, count: Int?, label: String, tone: DoctorTone, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, pill: Pair<String, DoctorTone>? = null) {
    val press = remember { MutableInteractionSource() }
    Surface(onClick = onClick, interactionSource = press, color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), shadowElevation = 1.dp,
        modifier = modifier.saharaAppear().saharaPress(press).heightIn(min = 76.dp).testTag(tag).semantics(mergeDescendants = true) { contentDescription = "${count ?: "—"} $label" + (pill?.let { ", ${it.first}" } ?: "") }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DoctorIconChip(icon, tone)
            Column(Modifier.weight(1f)) {
                Text(count?.let { "${doctorCountUp(it)}" } ?: "—", fontFamily = SaharaTitleFont, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"), modifier = Modifier.testTag("$tag-count"))
                Text(label, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, style = DoctorWholeWords)
                pill?.let { DoctorPill(it.first, it.second, Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

/** Text that wraps only between words. */
internal val DoctorWholeWords = TextStyle(lineBreak = LineBreak.Heading, hyphens = Hyphens.None)

/** Compact register row: coloured initial, name, one line of detail, status pill. 64dp tall at most text sizes. */
@Composable internal fun DoctorRegisterRow(name: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier, pill: Pair<String, DoctorTone>? = null, lead: String? = null) {
    val press = remember { MutableInteractionSource() }
    val tone = DoctorTones.forName(name)
    Surface(onClick = onClick, interactionSource = press, color = Paper, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorHairline),
        modifier = modifier.fillMaxWidth().saharaAppear().saharaPress(press).heightIn(min = 64.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lead != null) Text(lead, Modifier.widthIn(min = 64.dp), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = DoctorTones.Rust.ink, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
            else Box(Modifier.size(40.dp).background(tone.wash, CircleShape), contentAlignment = Alignment.Center) { Text(name.trim().take(1).uppercase(), color = tone.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(name, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(detail, fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            pill?.let { DoctorPill(it.first, it.second) }
            Icon(Icons.Default.ChevronRight, null, tint = DoctorCardSub, modifier = Modifier.size(20.dp))
        }
    }
}

/** What a patient needs today, for the register pill: a visit today, a hard day reported this week, or a recent check-in. */
internal fun doctorPatientStatus(person: Person, items: List<CareItem>, now: Long, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): Pair<String, DoctorTone>? {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    if (doctorUpcomingVisits(items, person.id, now, zone).any { it.due > 0 && Instant.ofEpochMilli(it.due).atZone(zone).toLocalDate() == today }) return careTr(hindi, "Visit today", "आज विज़िट") to DoctorTones.Marigold
    val check = doctorCheckIns(items, person.id, now).firstOrNull { now - it.created <= 7 * 86_400_000L } ?: return null
    return if (check.title == DoctorReportedMood.HARD.title) careTr(hindi, "Hard day", "मुश्किल दिन") to DoctorTones.Red else careTr(hindi, "Check-in", "हालचाल") to DoctorTones.Teal
}

/**
 * Doctor Home as a launchpad: four real counts as tiles, then the Today queue, then what is coming up and the week's check-ins.
 * [firstIndex] is how many list items the caller drew above, so the Check-ins tile can scroll to its section.
 */
internal fun LazyListScope.doctorConnectedHomeItems(people: List<Person>, items: List<CareItem>, now: Long, hindi: Boolean, onVisit: (String) -> Unit, onProgress: (String) -> Unit, onPatients: () -> Unit,
    onSchedule: () -> Unit = onPatients, onScrollTo: (Int) -> Unit = {}, firstIndex: Int = 0, zone: ZoneId = ZoneId.systemDefault()) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val visits = DoctorConnectedHome.upcomingVisits(people, items, now, zone)
    val (todayVisits, laterVisits) = visits.partition { it.item.due > 0 && Instant.ofEpochMilli(it.item.due).atZone(zone).toLocalDate() == today }
    val checks = DoctorConnectedHome.recentCheckIns(people, items, now)
    val (todayChecks, weekChecks) = checks.partition { Instant.ofEpochMilli(it.second.created).atZone(zone).toLocalDate() == today }
    val hard = checks.count { it.second.title == DoctorReportedMood.HARD.title }
    // Index of the Check-ins section: tiles (1) + Today title (1) + today rows or empty (>=1) + coming-up block.
    val todayRows = (todayVisits.size + todayChecks.size).coerceAtLeast(1)
    val laterRows = if (laterVisits.isEmpty()) 0 else 1 + laterVisits.take(5).size
    val checksIndex = firstIndex + 2 + todayRows + laterRows
    item(key = "home-tiles") {
        BoxWithConstraints {
            val paired = doctorTilesPaired(maxWidth.value, LocalDensity.current.fontScale)
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> DoctorStatTile(Icons.Default.People, people.size, t("Patients", "मरीज़"), DoctorTones.Rust, onPatients, "doctor-home-tile-patients", m) },
                { m -> DoctorStatTile(Icons.Default.Today, todayVisits.size, t("Visits today", "आज की विज़िट"), DoctorTones.Marigold, onSchedule, "doctor-home-tile-today", m) },
                { m -> DoctorStatTile(Icons.Default.EventAvailable, laterVisits.size, t("Coming up", "आगे की विज़िट"), DoctorTones.Teal, onSchedule, "doctor-home-tile-upcoming", m) },
                { m -> DoctorStatTile(Icons.Default.Favorite, checks.size, t("Check-ins, 7 days", "हालचाल, 7 दिन"), DoctorTones.Rose, { onScrollTo(checksIndex) }, "doctor-home-tile-checkins", m,
                    pill = if (hard > 0) t("$hard hard", "$hard मुश्किल") to DoctorTones.Red else null) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (paired) tiles.chunked(2).forEach { pair -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) { pair.forEach { it(Modifier.weight(1f).fillMaxHeight()) } } }
                else tiles.forEach { it(Modifier.fillMaxWidth()) }
            }
        }
    }
    if (people.isEmpty()) return
    item(key = "home-today-title") { DoctorHomeSection(t("Today", "आज"), todayVisits.size + todayChecks.size, DoctorTones.Marigold) }
    if (todayVisits.isEmpty() && todayChecks.isEmpty()) item(key = "home-today-empty") { DoctorHomeEmpty(Icons.Default.EventAvailable, t("Nothing due today", "आज कुछ तय नहीं")) }
    items(todayVisits, key = { "home-today-${it.item.id}" }) { visit ->
        DoctorRegisterRow(visit.person.name, visit.item.title, { onVisit(visit.person.id) }, lead = doctorTimeOrDay(visit.item, hindi, zone), pill = t("Visit", "विज़िट") to DoctorTones.Marigold)
    }
    items(todayChecks, key = { "home-today-check-${it.second.id}" }) { (person, check) ->
        DoctorRegisterRow(person.name, checkInMoodLabel(check.title, hindi), { onProgress(person.id) }, lead = DateTimeFormatter.ofPattern("h:mm a", careLocale(hindi)).withZone(zone).format(Instant.ofEpochMilli(check.created)),
            pill = if (check.title == DoctorReportedMood.HARD.title) t("Hard day", "मुश्किल दिन") to DoctorTones.Red else t("Check-in", "हालचाल") to DoctorTones.Teal)
    }
    if (laterVisits.isNotEmpty()) {
        item(key = "home-visits-title") { DoctorHomeSection(t("Coming up", "आगे की विज़िट"), laterVisits.size, DoctorTones.Teal) }
        items(laterVisits.take(5), key = { "home-visit-${it.item.id}" }) { visit ->
            DoctorRegisterRow(visit.person.name, "${doctorWhen(visit.item, hindi, zone)} · ${visit.item.title}", { onVisit(visit.person.id) })
        }
    }
    item(key = "home-checks-title") { DoctorHomeSection(t("Check-ins", "हालचाल"), weekChecks.size, DoctorTones.Rose) }
    if (weekChecks.isEmpty()) item(key = "home-checks-empty") { DoctorHomeEmpty(Icons.Default.Favorite, t("No other check-ins this week", "इस हफ़्ते और कोई हालचाल नहीं")) }
    items(weekChecks, key = { "home-check-${it.second.id}" }) { (person, check) ->
        DoctorRegisterRow(person.name, "${checkInMoodLabel(check.title, hindi)} · ${DateTimeFormatter.ofPattern("d MMM, h:mm a", careLocale(hindi)).withZone(zone).format(Instant.ofEpochMilli(check.created))}", { onProgress(person.id) },
            pill = if (check.title == DoctorReportedMood.HARD.title) t("Hard day", "मुश्किल दिन") to DoctorTones.Red else null)
    }
}

/** "10:30 AM" for a timed visit today, otherwise "Today". */
private fun doctorTimeOrDay(item: CareItem, hindi: Boolean, zone: ZoneId): String =
    if (CareEntryCodec.decode(item.detail)?.timed == true) DateTimeFormatter.ofPattern("h:mm a", careLocale(hindi)).withZone(zone).format(Instant.ofEpochMilli(item.due)) else careTr(hindi, "Today", "आज")

/** Section title with a tone bar and count: scannable at a glance. */
@Composable private fun DoctorHomeSection(title: String, count: Int, tone: DoctorTone) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).background(tone.ink, RoundedCornerShape(2.dp)))
        Text(title, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        if (count > 0) DoctorPill("$count", tone)
    }
}

/** One line with an icon for an empty list. */
@Composable private fun DoctorHomeEmpty(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).border(1.dp, DoctorHairline, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = DoctorCardSub, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub)
    }
}

/** Doctor Schedule: every open visit across connected patients, grouped by day. */
internal fun LazyListScope.doctorConnectedScheduleItems(people: List<Person>, items: List<CareItem>, now: Long, hindi: Boolean, onVisit: (String) -> Unit) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val visits = DoctorConnectedHome.upcomingVisits(people, items, now)
    item(key = "schedule-title") { Text(t("Schedule", "समय-सारणी"), style = MaterialTheme.typography.headlineMedium) }
    if (people.isEmpty()) item(key = "schedule-no-people") { Text(t("No patients yet", "अभी कोई मरीज़ नहीं"), fontWeight = FontWeight.Medium) }
    else if (visits.isEmpty()) item(key = "schedule-empty") { Text(t("No visits", "कोई विज़िट नहीं")) }
    items(visits, key = { "schedule-${it.item.id}" }) { visit -> DoctorPatientListCard(visit.person.name, "${doctorWhen(visit.item, hindi)} · ${visit.item.title}", { onVisit(visit.person.id) }) }
    if (people.isNotEmpty()) {
        item(key = "schedule-by-patient") { Text(t("Plan a visit", "विज़िट तय करें"), style = MaterialTheme.typography.titleMedium) }
        items(people, key = { "schedule-person-${it.id}" }) { person -> DoctorPatientListCard(person.name, t("Visits", "मुलाक़ातें"), { onVisit(person.id) }) }
    }
}


/** The only place the signed-in workspace offers practice patients. They live on this device, apart from real care. */
@Composable internal fun DoctorWalkthroughCard(hindi: Boolean, onOpen: (DoctorTestProfiles.Profile) -> Unit) {
    Surface(color = DoctorCardVisit, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("doctor-walkthrough-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, null, tint = DoctorAccent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Text(careTr(hindi, "Practice patients", "अभ्यास मरीज़"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.weight(1f))
            }
            DoctorTestProfiles.profiles.forEach { profile ->
                TextButton(onClick = { onOpen(profile) }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-walkthrough-${profile.key}")) { Text(profile.name, color = RustText, fontSize = 15.sp); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = RustText) }
            }
        }
    }
}
