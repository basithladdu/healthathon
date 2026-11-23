package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Doctor-only, in-app notices (Dr Shirin and Dr Sujay, 23 Nov 2026). Nothing here posts a system notification:
 * severe pain or distress is shown as a calm pop-up when the doctor opens the patient, and an emergency card web
 * link opened elsewhere is shown as a notice inside the app. Patients and family never see either.
 */

internal enum class SevereKind { PAIN, DISTRESS }

/** One severe score: a pain note or a distress check-in of [SevereRules.SEVERE_FROM] or more. [key] changes if the score is edited. */
internal data class SevereEntry(val itemId: String, val kind: SevereKind, val score: Int, val at: Long) {
    val key: String get() = "$itemId:$score"
}

internal object SevereRules {
    /** 7/10 or more on the pain scale or the distress thermometer. */
    const val SEVERE_FROM = 7
    const val WINDOW_DAYS = 7L
    const val MAX_LINES = 3
    private const val DAY = 86_400_000L

    private fun observedAt(note: SymptomNote, zone: ZoneId): Long? = runCatching {
        LocalDate.parse(note.observedDate).atTime(LocalTime.parse(note.observedTime)).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /**
     * Severe pain and distress for [personId] in the last [WINDOW_DAYS] days, newest first. Pain counts by the note's
     * headline number (the highest rated spot, else the overall rating) and its observed time; distress by the check-in's
     * score (a "No" answer never counts) and when it was saved. Mood, other symptoms and unrated notes never count.
     */
    fun entries(items: List<CareItem>, personId: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<SevereEntry> {
        val pain = items.filter { it.personId == personId && SymptomNoteCodec.isStructured(it.detail) }.mapNotNull { item ->
            val note = SymptomNoteCodec.decode(item.detail)?.takeIf { it.symptom.trim().equals("Pain", ignoreCase = true) } ?: return@mapNotNull null
            val score = PainBodyMapRules.overall(note.painBodyRatings) ?: note.rating ?: return@mapNotNull null
            SevereEntry(item.id, SevereKind.PAIN, score, observedAt(note, zone) ?: item.created)
        }
        val distress = DistressRules.history(items, personId).mapNotNull { (item, check) ->
            check.score?.takeIf { check.distress }?.let { SevereEntry(item.id, SevereKind.DISTRESS, it, item.created) }
        }
        return (pain + distress).filter { it.score >= SEVERE_FROM && it.at >= now - WINDOW_DAYS * DAY && it.at <= now + 10 * 60_000L }
            .sortedWith(compareByDescending<SevereEntry> { it.at }.thenBy { it.itemId })
    }

    /** At most [MAX_LINES], most recent first. */
    fun lines(entries: List<SevereEntry>): List<SevereEntry> = entries.take(MAX_LINES)

    /** Show once per new severe entry: only when one of them has not been acknowledged on this phone. */
    fun shouldShow(entries: List<SevereEntry>, acknowledged: Set<String>): Boolean = entries.any { it.key !in acknowledged }
}

/** What this doctor's phone already acknowledged for one patient. Kept per signed-in account. */
internal class SevereMemory(context: Context, namespace: String, personId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("sahara-doctor-severe", Context.MODE_PRIVATE)
    private val key = "$namespace|$personId"
    fun acknowledged(): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty()
    /** Keeps only the current entries, so the list never grows. */
    fun acknowledge(entries: List<SevereEntry>) { prefs.edit().putStringSet(key, entries.map { it.key }.toSet()).apply() }
}

/** The newest emergency card web-link open this doctor has already been told about, per patient. */
internal class EmergencyViewMemory(context: Context, namespace: String) {
    private val prefs = context.applicationContext.getSharedPreferences("sahara-emergency-views", Context.MODE_PRIVATE)
    private val prefix = "$namespace|"
    fun lastSeen(personId: String): Long = prefs.getLong(prefix + personId, 0L)
    fun markSeen(personId: String, at: Long) { if (at > lastSeen(personId)) prefs.edit().putLong(prefix + personId, at).apply() }
}

/**
 * Doctors only, when a patient is opened: first the severe pain or distress pop-up (if there is a new severe entry),
 * then, only after it is closed, the surprise question ([surpriseQuestion] null leaves it out, e.g. in Practice).
 * The two dialogs never stack.
 */
@Composable internal fun DoctorPatientOpenPrompts(vm: CareModel, personId: String, onOpenSymptoms: () -> Unit, surpriseQuestion: (() -> Unit)? = null) {
    val context = LocalContext.current; val namespace = vm.storageNamespace
    val memory = remember(namespace, personId) { SevereMemory(context, namespace, personId) }
    var checked by remember(namespace, personId) { mutableStateOf(false) }
    var severe by remember(namespace, personId) { mutableStateOf<List<SevereEntry>>(emptyList()) }
    LaunchedEffect(namespace, personId) {
        if (personId.isBlank()) return@LaunchedEffect
        vm.itemsLoadedFor.first { it == personId }
        withFrameNanos { } // the item list settles one frame after its person is marked loaded
        val found = SevereRules.entries(vm.allItems.value, personId, System.currentTimeMillis())
        severe = if (SevereRules.shouldShow(found, memory.acknowledged())) found else emptyList()
        checked = true
    }
    if (!checked) return
    if (severe.isNotEmpty()) SeverePainDistressDialog(SevereRules.lines(severe),
        onOpen = { memory.acknowledge(severe); severe = emptyList(); onOpenSymptoms() },
        onClose = { memory.acknowledge(severe); severe = emptyList() })
    else if (surpriseQuestion != null) GocSurpriseQuestionPrompt(vm, personId, surpriseQuestion)
}

@Composable private fun SeverePainDistressDialog(lines: List<SevereEntry>, onOpen: () -> Unit, onClose: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val format = DateTimeFormatter.ofPattern("d MMM", careLocale(hindi)).withZone(ZoneId.systemDefault())
    AlertDialog(onDismissRequest = onClose, modifier = Modifier.testTag("doctor-severe-dialog"),
        title = { Text(t("Severe pain or distress this week", "इस हफ़्ते तेज़ दर्द या परेशानी"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Recorded for this patient in the last 7 days:", "पिछले 7 दिनों में इस मरीज़ के लिए दर्ज:"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub)
                lines.forEach { entry ->
                    val score = entry.score; val day = format.format(Instant.ofEpochMilli(entry.at))
                    Text(if (entry.kind == SevereKind.PAIN) t("Pain $score/10 on $day", "दर्द $score/10, $day") else t("Distress $score/10 on $day", "परेशानी $score/10, $day"),
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("doctor-severe-line"))
                }
            }
        },
        confirmButton = { Button(onClick = onOpen, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-severe-open")) { Text(t("Open Symptoms & distress", "लक्षण और परेशानी खोलें")) } },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-severe-close")) { Text(t("Close", "बंद करें")) } })
}

/** "Emergency card for Asha opened at City Hospital, 23 Nov, 4:10 PM" (or without the place when it was skipped). */
internal fun emergencyViewNotice(name: String, view: EmergencyCardView, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = DateTimeFormatter.ofPattern("d MMM, h:mm a", careLocale(hindi)).withZone(zone).format(Instant.ofEpochMilli(view.at))
    val place = view.place
    return if (place.isNotBlank()) careTr(hindi, "Emergency card for $name opened at $place, $time", "$name का आपातकालीन कार्ड $place में खोला गया, $time")
    else careTr(hindi, "Emergency card for $name opened (place not given), $time", "$name का आपातकालीन कार्ड खोला गया (जगह नहीं बताई), $time")
}

/** The last opens of the web link, one line each, and plainly what cannot be seen. */
@Composable internal fun EmergencyCardOpenedList(views: List<EmergencyCardView>, modifier: Modifier = Modifier) {
    val t = careText(); val hindi = careHindi()
    val format = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", careLocale(hindi)).withZone(ZoneId.systemDefault())
    Column(modifier.fillMaxWidth().testTag("emergency-card-opened"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        views.take(EmergencyViewRules.RECENT).forEach { view ->
            val time = format.format(Instant.ofEpochMilli(view.at)); val place = view.place
            Text(if (place.isNotBlank()) t("Opened at $place on $time", "$place में $time को खोला गया") else t("Opened on $time (place not given)", "$time को खोला गया (जगह नहीं बताई)"),
                fontSize = 14.sp, color = DoctorCardInk, modifier = Modifier.testTag("emergency-card-opened-line"))
        }
        Text(t("Only web link opens are shown. Scans of the offline code on a printed card can't be seen.", "केवल वेब लिंक से खोलना दिखता है। छपे कार्ड के ऑफ़लाइन कोड का स्कैन नहीं दिखता।"), fontSize = 13.sp, color = DoctorCardSub)
    }
}
