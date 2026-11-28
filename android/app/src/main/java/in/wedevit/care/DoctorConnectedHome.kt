@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
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

/** The design lane's section and status tones (SaharaTone), named for the doctor screens. [SaharaTone.solid] fills chips and bars; [SaharaTone.ink] is text on [SaharaTone.wash]. */
internal typealias DoctorTone = SaharaTone
internal object DoctorTones {
    val Rust = SaharaTone.Rust
    val Teal = SaharaTone.Teal
    val Indigo = SaharaTone.Indigo
    val Plum = SaharaTone.Plum
    val Leaf = SaharaTone.Leaf
    val Marigold = SaharaTone.Marigold
    val Good = SaharaTone.Success
    val Attention = SaharaTone.Warning
    val Red = SaharaTone.Error
    private val cycle = listOf(Rust, Teal, Indigo, Plum, Leaf, Marigold)
    /** A stable tone per name so the same patient always gets the same avatar colour. */
    fun forName(name: String): DoctorTone = cycle[Math.floorMod(name.trim().lowercase().hashCode(), cycle.size)]
}
/** The doctor header: the calm cream ground with dark ink (no dark band since 7 Nov). */
internal val DoctorBand get() = SaharaColors.Header

/** Status pill (22 Nov premium pass): the tone's light wash with a 1 dp edge in its colour and bold ink text (>= 4.5:1); never colour alone (it always carries a word). */
@Composable internal fun DoctorPill(text: String, tone: DoctorTone, modifier: Modifier = Modifier) {
    Surface(color = tone.wash, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, tone.solid), modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = tone.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** u-FIR icon chip: a small flat tile in the section colour with a white glyph. */
@Composable internal fun DoctorIconChip(icon: ImageVector, tone: DoctorTone, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(tone.solid, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tone.onSolid, modifier = Modifier.size(size * .5f))
    }
}

/** A number that counts up from 0 when it first appears and slides to a new value when it changes. Instant when motion is off. */
@Composable internal fun doctorCountUp(value: Int): Int {
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) { target = value }
    val shown by animateIntAsState(target, SaharaMotion.tween(SaharaMotion.ENTER_MS * 2, easing = SaharaMotion.Emphasized), label = "tilecount")
    return shown
}

/** Two tiles side by side only when each still has room for its label at this text size; otherwise one per row, so no word is ever split. */
internal fun doctorTilesPaired(widthDp: Float, fontScale: Float): Boolean = widthDp / fontScale.coerceAtLeast(1f) >= 320f

/** Bento launch tile: icon chip, big real count, label, optional status pill. The whole tile is the action. */
@Composable internal fun DoctorStatTile(icon: ImageVector, count: Int?, label: String, tone: DoctorTone, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, pill: Pair<String, DoctorTone>? = null) {
    // The design lane's solid bento tile: bright section colour, white icon, big count, white bold label.
    SaharaBentoTile(label, icon, tone, onClick, modifier.testTag(tag).semantics(mergeDescendants = true) { contentDescription = "${count ?: "—"} $label" + (pill?.let { ", ${it.first}" } ?: "") },
        value = count?.let { "${doctorCountUp(it)}" } ?: "—", caption = pill?.first)
}

/** Text that wraps only between words. */
internal val DoctorWholeWords = TextStyle(lineBreak = LineBreak.Heading, hyphens = Hyphens.None)

/** How many rows a Home section shows before "See all" (owner, 25 Nov: "too much scrolling, too many cards"). */
internal const val DOCTOR_HOME_ROWS = 3

/** One count in [DoctorCountStrip]: a real number, its label, and where a tap goes. */
internal class DoctorCount(val label: String, val count: Int?, val tone: DoctorTone, val tag: String, val note: String? = null, val onClick: () -> Unit)

/**
 * The day's counts as one compact strip (25 Nov declutter): one card with equal cells split by hairlines, instead of a
 * grid of big tiles. All cells sit in one row when the labels fit at this text size; otherwise two per row. Each cell is a 64 dp button.
 */
@Composable internal fun DoctorCountStrip(cells: List<DoctorCount>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = if (maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 80f * cells.size) cells.size else 2
        Surface(Modifier.fillMaxWidth().saharaAppear(), shape = RoundedCornerShape(SaharaRadius.Lg), color = Paper, border = BorderStroke(1.dp, DoctorHairline)) {
            Column {
                cells.chunked(perRow).forEachIndexed { r, row ->
                    if (r > 0) HorizontalDivider(color = DoctorHairline)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        row.forEachIndexed { i, cell ->
                            if (i > 0) VerticalDivider(color = DoctorHairline)
                            Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = cell.onClick).testTag(cell.tag)
                                .semantics(mergeDescendants = true) { contentDescription = "${cell.count ?: "—"} ${cell.label}" + (cell.note?.let { ", $it" } ?: "") }
                                .padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(cell.count?.let { "${doctorCountUp(it)}" } ?: "—", fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = cell.tone.ink, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                                Text(cell.label, fontSize = 13.sp, lineHeight = 17.sp, color = DoctorCardSub, textAlign = TextAlign.Center, maxLines = 2, style = DoctorWholeWords)
                                cell.note?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = DoctorTones.Attention.ink, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A Home section's header: title with its count, and an optional "See all" link on the right. 48 dp, no card. */
@Composable internal fun DoctorGroupHeader(title: String, count: Int, modifier: Modifier = Modifier, actionLabel: String? = null, actionTag: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (count > 0) "$title · $count" else title, Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 2, style = DoctorWholeWords)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp).then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier)) {
            Text(actionLabel, color = RustText, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false); Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = RustText)
        }
    }
}

/** One white card holding a section's rows split by hairlines (25 Nov declutter), instead of one card per row. */
@Composable internal fun DoctorGroupedList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().saharaAppear(), shape = RoundedCornerShape(SaharaRadius.Lg), color = Paper, border = BorderStroke(1.dp, DoctorHairline)) { Column(content = content) }
}

/** Rows of a grouped list with a hairline between them. */
@Composable internal fun <T> ColumnScope.DoctorGroupRows(rows: List<T>, row: @Composable (T) -> Unit) {
    rows.forEachIndexed { i, item -> if (i > 0) HorizontalDivider(Modifier.padding(start = 12.dp), color = DoctorHairline); row(item) }
}

/** One line in a grouped list: a time (or an icon, or the name's initial), the name, one detail line, an optional pill, a chevron. 56 dp at least. */
@Composable internal fun DoctorGroupRow(title: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier, lead: String? = null, leadTone: DoctorTone = DoctorTones.Indigo, icon: ImageVector? = null, pill: Pair<String, DoctorTone>? = null, description: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { if (description != null) contentDescription = description }
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            lead != null -> Text(lead, Modifier.widthIn(min = 64.dp), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = leadTone.ink, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
            icon != null -> Icon(icon, null, Modifier.size(22.dp), tint = DoctorAccent)
            else -> { val tone = DoctorTones.forName(title); Box(Modifier.size(36.dp).background(tone.wash, CircleShape).border(1.dp, tone.solid, CircleShape), contentAlignment = Alignment.Center) { Text(title.trim().take(1).uppercase(), color = tone.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp) } }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        pill?.let { DoctorPill(it.first, it.second) }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = DoctorCardSub)
    }
}

/** "See all (n)" / "Show fewer" under a grouped list that holds more than [DOCTOR_HOME_ROWS] rows. */
@Composable internal fun DoctorSeeAllToggle(total: Int, open: Boolean, hindi: Boolean, tag: String, onToggle: () -> Unit) {
    if (total <= DOCTOR_HOME_ROWS) return
    HorizontalDivider(color = DoctorHairline)
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) {
        Text(if (open) careTr(hindi, "Show fewer", "कम दिखाएँ") else careTr(hindi, "See all", "सब देखें") + " ($total)", color = RustText, fontWeight = FontWeight.SemiBold)
    }
}

/** Compact register row: coloured initial, name, one line of detail, status pill. 64dp tall at most text sizes. [section]: the list's colour, as the row's wash and edge. */
@Composable internal fun DoctorRegisterRow(name: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier, pill: Pair<String, DoctorTone>? = null, lead: String? = null, section: DoctorTone? = null) {
    val press = remember { MutableInteractionSource() }
    val tone = DoctorTones.forName(name)
    // Premium pass (22 Nov): one 1 dp edge in the list's colour (was 1.5 dp) and the radius token, so rows read lighter on the cream.
    Surface(onClick = onClick, interactionSource = press, color = section?.wash ?: Paper, shape = RoundedCornerShape(SaharaRadius.Md), border = BorderStroke(1.dp, section?.solid ?: DoctorHairline),
        modifier = modifier.fillMaxWidth().saharaAppear().saharaPress(press).heightIn(min = 64.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lead != null) Text(lead, Modifier.widthIn(min = 64.dp), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = (section ?: DoctorTones.Indigo).ink, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
            else Box(Modifier.size(40.dp).background(tone.wash, CircleShape).border(1.dp, tone.solid, CircleShape), contentAlignment = Alignment.Center) { Text(name.trim().take(1).uppercase(), color = tone.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(name, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(detail, fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            pill?.let { DoctorPill(it.first, it.second) }
            Icon(Icons.Default.ChevronRight, null, tint = section?.ink ?: DoctorCardSub, modifier = Modifier.size(20.dp))
        }
    }
}

/** What a patient needs today, for the register pill: a visit today or a hard day reported this week. Nothing otherwise (guide rule 21: chips only for status to act on). */
internal fun doctorPatientStatus(person: Person, items: List<CareItem>, now: Long, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): Pair<String, DoctorTone>? {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    if (doctorUpcomingVisits(items, person.id, now, zone).any { it.due > 0 && Instant.ofEpochMilli(it.due).atZone(zone).toLocalDate() == today }) return careTr(hindi, "Visit today", "आज विज़िट") to DoctorTones.Indigo
    val check = doctorCheckIns(items, person.id, now).firstOrNull { now - it.created <= 7 * 86_400_000L } ?: return null
    return if (check.title == DoctorReportedMood.HARD.title) careTr(hindi, "Hard day", "मुश्किल दिन") to DoctorTones.Attention else null
}

// ───────────────────────────── Doctor Home (25 Nov, owner: "looks AI-generated") ─────────────────────────────
// One screen for the walkthrough and the signed-in workspace, so the two never drift: a large title-left top bar, one visits
// ring with three stat lines, the next visit, then plain grouped rows. No cards, no pills, no tinted boxes.

/** One row on doctor Home: a time in a 72dp lead column ([lead]) or a plain [icon]. [alert]: the supporting line is in the error colour (a hard day). */
internal class DoctorHomeLine(val tag: String, val headline: String, val supporting: String?, val onClick: () -> Unit,
    val lead: String? = null, val icon: ImageVector? = null, val alert: Boolean = false)

/** A number beside the ring. A tap scrolls to the group [scrollTo] when that group is shown, otherwise runs [onClick]. [alert]: red when above 0. */
internal class DoctorHomeStat(val count: Int?, val label: String, val tag: String, val alert: Boolean = false, val scrollTo: String? = null, val onClick: () -> Unit = {})

/** The next visit: time (or date) over "Next", the patient, one detail line and an Open button. */
internal class DoctorHomeNext(val time: String, val name: String, val detail: String, val onOpen: () -> Unit)

/** A plain row above the ring for something that needs attention (verification, a notice, an error). Colour only when [alert]. */
internal class DoctorHomeBanner(val tag: String, val headline: String, val supporting: String?, val icon: ImageVector, val alert: Boolean = false,
    val onClick: (() -> Unit)? = null, val trailing: (@Composable () -> Unit)? = null)

/**
 * Rows under one label. [action] puts a text button on the label (tag [actionTag]); otherwise, when there are more rows than [cap],
 * the label gets "See all", which opens a sheet of every row (nothing expands in place). A group with no rows shows [empty] (and
 * [emptyAction]) or, when [empty] is null, is not drawn.
 */
internal class DoctorHomeGroup(val key: String, val title: String, val rows: List<DoctorHomeLine>, val action: String? = null, val actionTag: String? = null,
    val onAction: (() -> Unit)? = null, val cap: Int = Int.MAX_VALUE, val empty: String? = null, val emptyAction: String? = null, val onEmptyAction: (() -> Unit)? = null)

/** Everything doctor Home shows. [visitsDone] / [visitsTotal]: today's visits (null while loading). The ring scrolls to [ringScrollTo] or runs [onRing]. */
internal class DoctorHomeState(val title: String, val banners: List<DoctorHomeBanner>, val visitsDone: Int?, val visitsTotal: Int?, val ringTag: String,
    val ringScrollTo: String?, val onRing: () -> Unit, val stats: List<DoctorHomeStat>, val next: DoctorHomeNext?, val groups: List<DoctorHomeGroup>)

/** The groups Home draws, in order (empty groups without an empty line are left out). */
internal fun doctorHomeShownGroups(state: DoctorHomeState): List<DoctorHomeGroup> = state.groups.filter { it.rows.isNotEmpty() || it.empty != null }

/** The list index of a group: the banners are one item, the ring one, the next visit one. -1 when the group is not shown. */
internal fun doctorHomeGroupIndex(state: DoctorHomeState, key: String?): Int {
    val i = doctorHomeShownGroups(state).indexOfFirst { it.key == key }
    if (key == null || i < 0) return -1
    return (if (state.banners.isEmpty()) 0 else 1) + 1 + (if (state.next == null) 0 else 1) + i
}

/**
 * Doctor Home. Top bar actions: search (opens Patients), language, the doctor's initials (opens Account). The "New" button opens a
 * sheet: record a goals-of-care talk (then a patient picker), schedule a visit, add a patient. [listTag] goes on the list.
 */
@Composable internal fun DoctorHomeScreen(state: DoctorHomeState, avatarName: String, onSearch: () -> Unit, onAccount: () -> Unit,
    talkPatients: List<Pair<String, String>>, onTalk: (String) -> Unit, onScheduleVisit: () -> Unit, onAddPatient: () -> Unit, listTag: String,
    modifier: Modifier = Modifier, listState: LazyListState = rememberLazyListState(), refreshing: Boolean? = null, onRefresh: () -> Unit = {}) {
    val t = careText()
    val scope = rememberCoroutineScope()
    var newOpen by rememberSaveable { mutableStateOf(false) }
    var talkOpen by rememberSaveable { mutableStateOf(false) }
    var allOf by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = doctorHomeShownGroups(state)
    val go: (String?, () -> Unit) -> Unit = { key, fallback ->
        val index = doctorHomeGroupIndex(state, key)
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) } else fallback()
    }
    SaharaPage(state.title, modifier, large = true, listState = listState, listModifier = Modifier.testTag(listTag), refreshing = refreshing, onRefresh = onRefresh,
        actions = {
            IconButton(onClick = onSearch, modifier = Modifier.testTag("doctor-home-search")) { Icon(Icons.Default.Search, t("Search patients", "मरीज़ खोजें")) }
            CareLanguageMenu(compact = true)
            IconButton(onClick = onAccount, modifier = Modifier.testTag("doctor-home-account")) { DoctorInitials(avatarName, t("Account", "खाता")) }
        },
        fab = { expanded -> ExtendedFloatingActionButton(text = { Text(t("New", "नया")) }, icon = { Icon(Icons.Default.Add, null) }, onClick = { newOpen = true },
            modifier = Modifier.testTag("doctor-home-new"), expanded = expanded) }) {
        if (state.banners.isNotEmpty()) item(key = "home-banners") {
            Column(Modifier.fillMaxWidth()) {
                state.banners.forEach { b ->
                    SaharaRow(b.headline, Modifier.testTag(b.tag), supporting = b.supporting, supportingColor = if (b.alert) MaterialTheme.colorScheme.error else Color.Unspecified,
                        icon = b.icon, tone = if (b.alert) SaharaTone.Error else SaharaTone.Neutral, trailing = b.trailing, onClick = b.onClick, chevron = false)
                }
                HorizontalDivider()
            }
        }
        item(key = "home-hero") { DoctorHomeHero(state, go) }
        state.next?.let { next -> item(key = "home-next") {
            SaharaRow(next.name, Modifier.testTag("doctor-home-next"), supporting = next.detail, chevron = false,
                leading = { DoctorHomeTime(next.time, t("Next", "अगली"), accent = true) },
                trailing = { FilledTonalButton(onClick = next.onOpen, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-home-next-open")) { Text(t("Open", "खोलें")) } })
        } }
        groups.forEach { group -> item(key = "home-group-${group.key}") { DoctorHomeGroupBlock(group) { allOf = group.key } } }
    }
    if (newOpen) SaharaActionSheet(t("New", "नया"), listOfNotNull(
        if (talkPatients.isNotEmpty()) SheetAction(t("Record a goals-of-care talk", "देखभाल के लक्ष्यों की बातचीत रिकॉर्ड करें"), Icons.Default.Mic, "doctor-home-new-talk") { talkOpen = true } else null,
        SheetAction(t("Schedule a visit", "विज़िट तय करें"), Icons.Default.CalendarMonth, "doctor-home-new-visit", onClick = onScheduleVisit),
        SheetAction(t("Add a patient", "मरीज़ जोड़ें"), Icons.Default.PersonAdd, "doctor-home-new-patient", onClick = onAddPatient),
    ), onDismiss = { newOpen = false })
    if (talkOpen) SaharaActionSheet(t("Choose the patient", "मरीज़ चुनें"), talkPatients.map { (id, name) -> SheetAction(name, Icons.Default.Person, "doctor-home-talk-$id") { onTalk(id) } },
        onDismiss = { talkOpen = false })
    groups.firstOrNull { it.key == allOf }?.let { group ->
        SaharaActionSheet(group.title, group.rows.map { r -> SheetAction(r.headline, r.icon ?: Icons.Default.Event, "${r.tag}-all",
            supporting = listOfNotNull(r.lead, r.supporting).joinToString(" · ").ifBlank { null }, onClick = r.onClick) }, onDismiss = { allOf = null })
    }
}

/** The doctor's initials in a 36dp tonal circle (the Account button). */
@Composable private fun DoctorInitials(name: String, description: String) {
    val initials = name.substringBefore('@').split(' ', '.', '_').filter { it.isNotBlank() && !it.equals("dr", ignoreCase = true) }
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifBlank { "?" }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(36.dp).clearAndSetSemantics { contentDescription = description }) {
        Box(contentAlignment = Alignment.Center) { Text(initials, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1) }
    }
}

/** A time in the 72dp lead column, with an optional small line under it. [accent]: larger and in the primary colour (the next visit). */
@Composable private fun DoctorHomeTime(time: String, under: String?, accent: Boolean) {
    Column(Modifier.widthIn(min = 72.dp)) {
        Text(time, fontSize = if (accent) 18.sp else 15.sp, lineHeight = if (accent) 22.sp else 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
        if (under != null) Text(under, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The one visual anchor: the visits ring (primary colour) and up to three stat lines beside it; stacked at very large text. */
@Composable private fun DoctorHomeHero(state: DoctorHomeState, go: (String?, () -> Unit) -> Unit) {
    val t = careText()
    val scheme = MaterialTheme.colorScheme
    val done = state.visitsDone; val total = state.visitsTotal
    val progress = if (done != null && total != null && total > 0) done.toFloat() / total else 0f
    val centre = when { done == null || total == null -> "—"; total == 0 -> "0"; else -> t("$done of $total", "$total में से $done") }
    val described = when { done == null || total == null -> t("Visits today", "आज की विज़िट"); else -> t("$done of $total visits done today", "आज $total में से $done विज़िट पूरी") }
    // The ring is a fixed 120dp, so its centre text does not grow with the font scale (the ring's description carries the numbers).
    val density = LocalDensity.current
    val big = with(density) { 24.dp.toSp() }; val small = with(density) { 13.dp.toSp() }
    val ring: @Composable () -> Unit = {
        SaharaRing(listOf(RingArc(progress)), described, Modifier.testTag(state.ringTag), size = 120.dp, onClick = { go(state.ringScrollTo, state.onRing) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centre, fontSize = big, lineHeight = big * 1.2f, fontWeight = FontWeight.Bold, maxLines = 1, color = scheme.onSurface)
                Text(if (total == 0) t("visits today", "आज विज़िट") else t("visits", "विज़िट"), fontSize = small, lineHeight = small * 1.3f, maxLines = 1, color = scheme.onSurfaceVariant)
            }
        }
    }
    val stats: @Composable ColumnScope.() -> Unit = {
        state.stats.forEach { s ->
            val red = s.alert && (s.count ?: 0) > 0
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { go(s.scrollTo, s.onClick) }.testTag(s.tag)
                .semantics(mergeDescendants = true) { contentDescription = "${s.count ?: "—"} ${s.label}" }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(s.count?.toString() ?: "—", Modifier.widthIn(min = 32.dp), fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
                    color = if (red) scheme.error else scheme.onSurface, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(12.dp))
                Text(s.label, Modifier.weight(1f), fontSize = 16.sp, lineHeight = 20.sp, color = if (red) scheme.error else scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, style = DoctorWholeWords)
            }
        }
    }
    if (density.fontScale >= 1.5f) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("doctor-home-hero"), horizontalAlignment = Alignment.CenterHorizontally) {
        ring(); Spacer(Modifier.height(12.dp)); Column(Modifier.fillMaxWidth(), content = stats)
    } else Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("doctor-home-hero"), verticalAlignment = Alignment.CenterVertically) {
        ring(); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f), content = stats)
    }
}

/** A group: the label (Material settings style, with its action) and plain rows; no card. */
@Composable private fun DoctorHomeGroupBlock(group: DoctorHomeGroup, onAll: () -> Unit) {
    val t = careText()
    val capped = group.rows.size > group.cap
    val action = group.action ?: if (capped) t("See all", "सब देखें") else null
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(group.title, Modifier.weight(1f).a11yHeading(), style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (action != null) TextButton(onClick = { group.onAction?.invoke() ?: onAll() }, modifier = Modifier.heightIn(min = 48.dp).then(if (group.actionTag != null) Modifier.testTag(group.actionTag) else Modifier)) { Text(action) }
        }
        group.rows.take(group.cap).forEach { r ->
            val lead: (@Composable () -> Unit)? = if (r.lead != null) ({ DoctorHomeTime(r.lead, null, accent = false) }) else null
            SaharaRow(r.headline, Modifier.testTag(r.tag), supporting = r.supporting, supportingColor = if (r.alert) MaterialTheme.colorScheme.error else Color.Unspecified,
                icon = r.icon, leading = lead, onClick = r.onClick)
        }
        if (group.rows.isEmpty() && group.empty != null) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(group.empty, Modifier.weight(1f).testTag("doctor-home-${group.key}-empty"), fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (group.emptyAction != null) TextButton(onClick = { group.onEmptyAction?.invoke() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(group.emptyAction) }
        }
    }
}

/** Today's visits across the doctor's patients: (completed, all not cancelled). Feeds the Home ring. */
internal fun doctorVisitsToday(people: List<Person>, items: List<CareItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
    val ids = people.map { it.id }.toSet()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val status = items.filter { it.personId in ids && it.kind == "visit" && it.due > 0 && Instant.ofEpochMilli(it.due).atZone(zone).toLocalDate() == today &&
        (!CareEntryCodec.isStructured(it.detail) || isDoctorStructuredVisit(it)) }.map { CareEntryCodec.decode(it.detail)?.status }.filter { it != "cancelled" }
    return status.count { it == "completed" } to status.size
}

/**
 * Signed-in doctor Home from real connected patients only: the ring (today's visits), stat lines (coming up, hard days, patients), the
 * next visit, then Today (visits and check-ins), Coming up (4 rows, the rest on Schedule) and Check-ins this week (4 rows, the rest in a sheet).
 */
internal fun doctorConnectedHomeState(people: List<Person>, items: List<CareItem>, now: Long, hindi: Boolean, banners: List<DoctorHomeBanner>,
    onVisit: (String) -> Unit, onProgress: (String) -> Unit, onPatients: () -> Unit, onSchedule: () -> Unit, zone: ZoneId = ZoneId.systemDefault()): DoctorHomeState {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val locale = careLocale(hindi)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun day(item: CareItem) = Instant.ofEpochMilli(item.due).atZone(zone).toLocalDate()
    val visits = DoctorConnectedHome.upcomingVisits(people, items, now, zone)
    val next = visits.firstOrNull { it.item.due > 0 }
    val (todayVisits, laterVisits) = visits.partition { it.item.due > 0 && day(it.item) == today }
    val checks = DoctorConnectedHome.recentCheckIns(people, items, now)
    val (todayChecks, weekChecks) = checks.partition { Instant.ofEpochMilli(it.second.created).atZone(zone).toLocalDate() == today }
    fun hard(check: CareItem) = check.title == DoctorReportedMood.HARD.title
    val clock = DateTimeFormatter.ofPattern("h:mm a", locale).withZone(zone)
    val (done, total) = doctorVisitsToday(people, items, now, zone)
    val todayRows = todayVisits.filter { it !== next }.map { v -> DoctorHomeLine("home-today-${v.item.id}", v.person.name, v.item.title, { onVisit(v.person.id) }, lead = doctorTimeOrDay(v.item, hindi, zone)) } +
        todayChecks.map { (person, check) -> DoctorHomeLine("home-today-check-${check.id}", person.name, checkInMoodLabel(check.title, hindi), { onProgress(person.id) },
            lead = clock.format(Instant.ofEpochMilli(check.created)), alert = hard(check)) }
    val laterRows = laterVisits.filter { it !== next }.map { v -> DoctorHomeLine("home-visit-${v.item.id}", v.person.name, "${doctorWhen(v.item, hindi, zone)} · ${v.item.title}", { onVisit(v.person.id) }, icon = Icons.Default.Event) }
    val weekRows = weekChecks.map { (person, check) -> DoctorHomeLine("home-check-${check.id}", person.name,
        "${checkInMoodLabel(check.title, hindi)} · ${DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone).format(Instant.ofEpochMilli(check.created))}", { onProgress(person.id) },
        icon = Icons.Default.FavoriteBorder, alert = hard(check)) }
    val groups = if (people.isEmpty()) listOf(DoctorHomeGroup("today", t("Today", "आज"), emptyList(), empty = t("No patients yet", "अभी कोई मरीज़ नहीं"),
        emptyAction = t("Add a patient", "मरीज़ जोड़ें"), onEmptyAction = onPatients))
    else listOf(
        DoctorHomeGroup("today", t("Today", "आज"), todayRows, action = t("See schedule", "शेड्यूल देखें"), actionTag = "home-today-schedule", onAction = onSchedule,
            empty = if (todayVisits.isEmpty() && todayChecks.isEmpty()) t("Nothing due today", "आज कुछ तय नहीं") else null, emptyAction = t("Schedule a visit", "विज़िट तय करें"), onEmptyAction = onSchedule),
        DoctorHomeGroup("coming", t("Coming up", "आगे की विज़िट"), laterRows, action = if (laterRows.size > DOCTOR_HOME_LIST) t("See all", "सब देखें") else null,
            actionTag = "home-visits-all", onAction = onSchedule, cap = DOCTOR_HOME_LIST),
        DoctorHomeGroup("checks", t("Check-ins this week", "इस हफ़्ते के हालचाल"), weekRows, actionTag = "home-checks-all", cap = DOCTOR_HOME_LIST,
            empty = t("No check-ins this week", "इस हफ़्ते कोई हालचाल नहीं")),
    )
    val homeNext = next?.let { v ->
        val isToday = day(v.item) == today
        DoctorHomeNext(if (isToday) doctorTimeOrDay(v.item, hindi, zone) else DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone).format(Instant.ofEpochMilli(v.item.due)),
            v.person.name, if (isToday) v.item.title else "${doctorWhen(v.item, hindi, zone)} · ${v.item.title}") { onVisit(v.person.id) }
    }
    return DoctorHomeState(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)), banners, done, total, "doctor-home-tile-today",
        ringScrollTo = "today", onRing = if (people.isEmpty()) onPatients else onSchedule,
        stats = listOf(
            DoctorHomeStat(laterVisits.size, t("Coming up", "आगे की विज़िट"), "doctor-home-tile-upcoming", scrollTo = "coming", onClick = onSchedule),
            DoctorHomeStat(checks.count { hard(it.second) }, t("Hard days", "मुश्किल दिन"), "doctor-home-tile-checkins", alert = true, scrollTo = "checks", onClick = onPatients),
            DoctorHomeStat(people.size, t("Patients", "मरीज़"), "doctor-home-tile-patients", onClick = onPatients),
        ),
        next = homeNext, groups = groups)
}

/** How many rows a Home group shows before "See all". */
internal const val DOCTOR_HOME_LIST = 4

/** "10:30 AM" for a timed visit today, otherwise "Today". */
private fun doctorTimeOrDay(item: CareItem, hindi: Boolean, zone: ZoneId): String =
    if (CareEntryCodec.decode(item.detail)?.timed == true) DateTimeFormatter.ofPattern("h:mm a", careLocale(hindi)).withZone(zone).format(Instant.ofEpochMilli(item.due)) else careTr(hindi, "Today", "आज")

/** One quiet line with an icon for an empty list (no card: 25 Nov declutter). */
@Composable internal fun DoctorHomeEmpty(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = DoctorTones.Teal.ink, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub)
    }
}

/** Doctor Schedule: every open visit across connected patients, grouped by day. */
internal fun LazyListScope.doctorConnectedScheduleItems(people: List<Person>, items: List<CareItem>, now: Long, hindi: Boolean, onVisit: (String) -> Unit) {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    val visits = DoctorConnectedHome.upcomingVisits(people, items, now)
    item(key = "schedule-title") { Text(t("Schedule", "शेड्यूल"), style = MaterialTheme.typography.headlineMedium) }
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
