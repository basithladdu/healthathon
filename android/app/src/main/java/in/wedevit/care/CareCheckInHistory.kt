package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day of check-ins: the day's mood (if any), its symptom notes, its distress answer and every entry of that day, newest first. */
internal data class CheckInDay(val day: LocalDate, val mood: String?, val symptomNotes: Int, val distressScore: Int?, val distressAnswered: Boolean, val items: List<CareItem>)

/** The Check-ins page as one compact row per day (Dr Sujay, 22 Nov review: the page was too long). Pure so it can be unit tested. */
internal object CheckInDays {
    /** How many days show before "Show older days". */
    const val RECENT = 7

    /** The day an entry belongs to: a symptom note's observed date, otherwise the day it was saved. */
    fun dayOf(item: CareItem, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        SymptomNoteCodec.decode(item.detail)?.observedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Instant.ofEpochMilli(item.created).atZone(zone).toLocalDate()

    fun group(items: List<CareItem>, zone: ZoneId = ZoneId.systemDefault()): List<CheckInDay> =
        items.groupBy { dayOf(it, zone) }.toSortedMap(compareByDescending { it }).map { (day, rows) ->
            val newest = rows.sortedByDescending { it.created }
            val distress = newest.firstNotNullOfOrNull { if (DistressCodec.isStructured(it.detail)) DistressCodec.decode(it.detail) else null }
            CheckInDay(day,
                mood = newest.firstOrNull { !SymptomNoteCodec.isStructured(it.detail) && !DistressCodec.isStructured(it.detail) && it.title in CareDailyFlow.moods }?.title,
                symptomNotes = newest.count { SymptomNoteCodec.isStructured(it.detail) },
                distressScore = distress?.let { if (it.distress) it.score ?: 0 else 0 }, distressAnswered = distress != null, items = newest)
        }
}

/** The Check-ins page: the distress graph one tap away, then one short row per day; a row opens to show that day's entries. */
@Composable internal fun CheckInHistoryModule(checkins: List<CareItem>, personId: String, vm: CareModel) {
    val t = careText(); val hindi = careHindi()
    val days = remember(checkins) { CheckInDays.group(checkins) }
    var showDistress by rememberSaveable(personId) { mutableStateOf(false) }
    var showOlder by rememberSaveable(personId) { mutableStateOf(false) }
    var openDay by rememberSaveable(personId) { mutableStateOf("") }
    val hasDistress = remember(checkins, personId) { DistressRules.history(checkins, personId).isNotEmpty() }
    if (hasDistress) {
        OutlinedButton(onClick = { showDistress = !showDistress }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, SaharaSection.CheckIns.solid),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("checkin-distress-toggle")) {
            Text(if (showDistress) t("Hide distress graph", "परेशानी का ग्राफ़ छिपाएँ") else t("See distress graph", "परेशानी का ग्राफ़ देखें"), color = DoctorCardInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            SaharaChevron(showDistress, null, tint = DoctorCardInk)
        }
        AnimatedVisibility(showDistress, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { DistressHistoryCard(checkins, personId) }
    }
    if (days.isEmpty()) { SaharaCalmEmpty(t("No check-ins yet.", "अभी कोई हाल नहीं लिखा।")); return }
    val shown = if (showOlder) days else days.take(CheckInDays.RECENT)
    val today = LocalDate.now(); val dayFormat = careDateFormat("EEE d MMM", hindi)
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear().testTag("checkin-days")) {
        Column {
            shown.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = DoctorHairline)
                val dayKey = d.day.toString(); val open = openDay == dayKey
                val label = when (d.day) { today -> t("Today", "आज"); today.minusDays(1) -> t("Yesterday", "कल"); else -> d.day.format(dayFormat) }
                val parts = listOfNotNull(
                    d.mood?.let { checkInMoodLabel(it, hindi) },
                    d.symptomNotes.takeIf { it > 0 }?.let { n -> if (n == 1) t("1 symptom note", "1 लक्षण नोट") else t("$n symptom notes", "$n लक्षण नोट") },
                    d.distressScore?.takeIf { d.distressAnswered }?.let { s -> t("Distress $s/10", "परेशानी $s/10") })
                Row(Modifier.fillMaxWidth().saharaStagger(i).heightIn(min = 56.dp).clickable(role = Role.Button) { openDay = if (open) "" else dayKey }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("checkin-day-$dayKey"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                        Text(parts.ifEmpty { listOf(if (d.items.size == 1) t("1 entry", "1 नोट") else t("${d.items.size} entries", "${d.items.size} नोट")) }.joinToString(" · "),
                            fontSize = 14.sp, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    SaharaChevron(open, if (open) t("Hide details", "जानकारी छिपाएँ") else t("Show details", "जानकारी देखें"))
                }
                AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { d.items.forEach { key(it.id) { CareRow(it, vm) } } }
                }
            }
        }
    }
    if (days.size > CheckInDays.RECENT) TextButton(onClick = { showOlder = !showOlder }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("checkin-show-older")) {
        Text(if (showOlder) t("Show recent days only", "सिर्फ़ हाल के दिन दिखाएँ") else t("Show older days", "पुराने दिन दिखाएँ"), color = RustText, fontWeight = FontWeight.SemiBold)
    }
}
