package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Caregiving changes (SVK's structure, 23 Nov; the team agreed). Family and carers only: the patient never sees any of this.
 *
 * - A quiet check-in on Today after [CaregivingStatusRules.INACTIVE_DAYS] days with nothing added for the patient, or since this
 *   phone last opened Sahara: "Are you still caring for <name>?".
 * - "I'm no longer actively caregiving", with an optional reason, stops every reminder for that patient on this phone at once,
 *   and Today goes quiet (archived). Nothing is deleted: the records stay, and others in the care circle can still add to them.
 * - Reminders can instead be paused for 30, 60 or 90 days; they come back on their own after that.
 * Kept on this phone, per account and patient, like the caregiver's own checks. Pure rules so they can be unit tested.
 */
enum class CaregivingState { ACTIVE, PAUSED, ENDED }

data class CaregivingStatus(
    val state: CaregivingState = CaregivingState.ACTIVE,
    /** One of [CaregivingStatusRules.reasons], or blank when not given. Only for [CaregivingState.ENDED]. */
    val reason: String = "",
    val since: LocalDate? = null,
    /** First day reminders ring again, for [CaregivingState.PAUSED]. */
    val pausedUntil: LocalDate? = null,
    /** The check-in is not asked again before this day ("Remind me later", "Things are steady"). */
    val askAgainFrom: LocalDate? = null,
    /** The last day this phone opened Today for this patient. */
    val lastOpen: LocalDate? = null,
)

object CaregivingStatusRules {
    /** SVK: "stops opening the app for 3–5 consecutive days". The lower end, so the check-in is not late. */
    const val INACTIVE_DAYS = 3L
    const val REMIND_LATER_DAYS = 2L
    const val STEADY_QUIET_DAYS = 7L
    val PAUSE_CHOICES = listOf(30, 60, 90)
    const val PASSED_AWAY = "passed-away"
    const val CARE_FACILITY = "care-facility"
    const val SOMEONE_ELSE = "someone-else"
    const val PREFER_NOT = "prefer-not"
    val reasons = listOf(PASSED_AWAY, CARE_FACILITY, SOMEONE_ELSE, PREFER_NOT)

    /** A pause that has run out is active again, with no tap needed. */
    fun effective(status: CaregivingStatus, today: LocalDate): CaregivingStatus =
        if (status.state == CaregivingState.PAUSED && (status.pausedUntil == null || !today.isBefore(status.pausedUntil))) status.copy(state = CaregivingState.ACTIVE, pausedUntil = null, since = null) else status

    /** True while no reminder for this patient may ring on this phone: paused (until the pause ends) or no longer caregiving. */
    fun remindersStopped(status: CaregivingStatus, today: LocalDate): Boolean = effective(status, today).state != CaregivingState.ACTIVE

    /** Today goes quiet (records kept, daily tasks hidden) once the family member is no longer caregiving. Never a delete. */
    fun archived(status: CaregivingStatus): Boolean = status.state == CaregivingState.ENDED

    /** Days with no sign of caregiving: the longer of the gap since the last open and the gap since the last entry. Null with neither. */
    fun inactiveDays(lastOpen: LocalDate?, lastLogged: LocalDate?, today: LocalDate): Long? =
        listOfNotNull(lastOpen, lastLogged).takeIf { it.isNotEmpty() }?.maxOf { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }

    /** The quiet check-in: active caregiving, not asked again yet, and [INACTIVE_DAYS] or more with no open or no entry. */
    fun checkInDue(status: CaregivingStatus, lastOpen: LocalDate?, lastLogged: LocalDate?, today: LocalDate): Boolean {
        val s = effective(status, today)
        if (s.state != CaregivingState.ACTIVE) return false
        if (s.askAgainFrom != null && today.isBefore(s.askAgainFrom)) return false
        return (inactiveDays(lastOpen, lastLogged, today) ?: return false) >= INACTIVE_DAYS
    }

    fun steady(status: CaregivingStatus, today: LocalDate) = status.copy(askAgainFrom = today.plusDays(STEADY_QUIET_DAYS))
    fun later(status: CaregivingStatus, today: LocalDate) = status.copy(askAgainFrom = today.plusDays(REMIND_LATER_DAYS))
    fun pause(status: CaregivingStatus, today: LocalDate, days: Int): CaregivingStatus {
        require(days in PAUSE_CHOICES) { "Choose 30, 60 or 90 days." }
        return status.copy(state = CaregivingState.PAUSED, pausedUntil = today.plusDays(days.toLong()), since = today, reason = "")
    }
    /** "I'm no longer actively caregiving": [reason] is optional (blank or one of [reasons]). */
    fun stop(status: CaregivingStatus, today: LocalDate, reason: String = ""): CaregivingStatus {
        require(reason.isBlank() || reason in reasons) { "Unknown reason." }
        return status.copy(state = CaregivingState.ENDED, reason = reason, since = today, pausedUntil = null)
    }
    /** Back to caregiving: reminders on again, and no check-in for a week. */
    fun resume(status: CaregivingStatus, today: LocalDate) = CaregivingStatus(askAgainFrom = today.plusDays(STEADY_QUIET_DAYS), lastOpen = status.lastOpen)

    fun encode(s: CaregivingStatus): String = JSONObject().put("state", s.state.name).put("reason", s.reason).put("since", s.since?.toString().orEmpty())
        .put("pausedUntil", s.pausedUntil?.toString().orEmpty()).put("askAgainFrom", s.askAgainFrom?.toString().orEmpty()).put("lastOpen", s.lastOpen?.toString().orEmpty()).toString()
    fun decode(raw: String?): CaregivingStatus {
        if (raw.isNullOrBlank()) return CaregivingStatus()
        return runCatching {
            val j = JSONObject(raw)
            fun day(name: String) = j.optString(name).takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val state = runCatching { CaregivingState.valueOf(j.optString("state")) }.getOrDefault(CaregivingState.ACTIVE)
            CaregivingStatus(state, j.optString("reason").takeIf { it in reasons }.orEmpty(), day("since"), day("pausedUntil"), day("askAgainFrom"), day("lastOpen"))
                .let { if (it.state == CaregivingState.PAUSED && it.pausedUntil == null) it.copy(state = CaregivingState.ACTIVE) else it }
        }.getOrDefault(CaregivingStatus())
    }
    fun key(account: String, person: String) = "$account|$person"
}

/** On this phone only, one entry per account and patient. The reminder scheduler reads it to skip a stopped patient. */
internal class CaregivingStatusStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sahara_caregiving_status", Context.MODE_PRIVATE)
    fun load(account: String, person: String): CaregivingStatus = CaregivingStatusRules.decode(prefs.getString(CaregivingStatusRules.key(account, person), null))
    fun save(account: String, person: String, status: CaregivingStatus) { prefs.edit().putString(CaregivingStatusRules.key(account, person), CaregivingStatusRules.encode(status)).commit() }
    /** Patients whose reminders must not ring for [account] today. */
    fun stoppedPeople(account: String, today: LocalDate = LocalDate.now()): Set<String> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith("$account|")) return@mapNotNull null
        key.removePrefix("$account|").takeIf { CaregivingStatusRules.remindersStopped(CaregivingStatusRules.decode(value as? String), today) }
    }.toSet()
}

/** Today's handle on the status: what it is, whether the check-in is due, and the one way to change it (reminders follow at once). */
internal class CaregivingStatusController(private val context: Context, private val account: String, private val person: String, initial: CaregivingStatus, private val previousOpen: LocalDate?) {
    var status by mutableStateOf(initial)
        private set
    var answeredNow by mutableStateOf(false)
        private set
    fun due(lastLogged: LocalDate?, today: LocalDate = LocalDate.now()) = !answeredNow && CaregivingStatusRules.checkInDue(status, previousOpen, lastLogged, today)
    fun update(next: CaregivingStatus) {
        val keepOpen = next.copy(lastOpen = status.lastOpen ?: LocalDate.now())
        CaregivingStatusStore(context).save(account, person, keepOpen); status = keepOpen; answeredNow = true
        // Stop (or bring back) every reminder for this patient at once, and clear any already on screen.
        runCatching { CareReminderScheduler(context.applicationContext, account).refreshAfterCaregivingChange() }
    }
}

/** Reads the status for the selected patient and records today's open (after reading the previous one, for the inactivity rule). */
@Composable internal fun rememberCaregivingStatus(vm: CareModel, person: String): CaregivingStatusController {
    val context = LocalContext.current; val account = vm.accountNamespace
    return remember(account, person) {
        val store = CaregivingStatusStore(context); val today = LocalDate.now()
        val saved = CaregivingStatusRules.effective(store.load(account, person), today)
        if (person.isNotBlank()) store.save(account, person, saved.copy(lastOpen = today))
        CaregivingStatusController(context, account, person, saved.copy(lastOpen = today), saved.lastOpen)
    }
}

/** The newest day anything was added for this patient, or null when nothing has been. */
internal fun caregivingLastLogged(items: List<CareItem>, person: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    items.filter { it.personId == person && it.created > 0 }.maxOfOrNull { it.created }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

/**
 * The check-in and its follow-ups in one calm card. [asked] true: it came up by itself after a quiet spell ("Remind me later"
 * shows); false: the family member opened it from "Has your caregiving changed?".
 */
@Composable internal fun CaregivingCheckInCard(controller: CaregivingStatusController, personName: String, asked: Boolean, onClose: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf("ask") }
    var afterDeathOpen by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    if (afterDeathOpen) AfterDeathGuideSheet { afterDeathOpen = false }
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaContentSize().testTag("caregiving-checkin")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (step) {
                "ask" -> {
                    CaregivingTitle(t("Are you still caring for $personName?", "क्या आप अभी भी $personName की देखभाल कर रहे हैं?"))
                    if (asked) CaregivingBody(t("Nothing has been added for a few days. Has anything changed?", "कुछ दिनों से कुछ नहीं जोड़ा गया। क्या कुछ बदला है?"))
                    CaregivingChoice(t("Yes, things are steady", "हाँ, सब पहले जैसा है"), "caregiving-steady") { controller.update(CaregivingStatusRules.steady(controller.status, today)); onClose() }
                    CaregivingChoice(t("Our care needs or routine have changed", "देखभाल की ज़रूरतें या दिनचर्या बदल गई हैं"), "caregiving-shifted") { step = "pause" }
                    CaregivingChoice(t("I'm no longer actively caregiving", "मैं अब देखभाल नहीं कर रहा/रही हूँ"), "caregiving-stop") { step = "reason" }
                    if (asked) TextButton(onClick = { controller.update(CaregivingStatusRules.later(controller.status, today)); onClose() }, modifier = Modifier.heightIn(min = 48.dp).testTag("caregiving-later")) {
                        Text(t("Remind me later", "बाद में याद दिलाएँ"), color = RustText, fontSize = 16.sp)
                    }
                }
                "reason" -> {
                    CaregivingTitle(t("What changed? (optional)", "क्या बदला? (चाहें तो बताएँ)"))
                    CaregivingBody(t("This only changes what Sahara shows you. Nothing is deleted.", "इससे सिर्फ़ यह बदलता है कि सहारा आपको क्या दिखाए। कुछ भी मिटाया नहीं जाता।"))
                    listOf(CaregivingStatusRules.PASSED_AWAY to t("$personName has passed away", "$personName का देहांत हो गया है"),
                        CaregivingStatusRules.CARE_FACILITY to t("$personName moved to a care facility or hospice", "$personName किसी देखभाल केंद्र या हॉस्पिस में चले गए हैं"),
                        CaregivingStatusRules.SOMEONE_ELSE to t("Someone else has taken over care", "अब कोई और देखभाल कर रहा है"),
                        CaregivingStatusRules.PREFER_NOT to t("I'd prefer not to say", "मैं नहीं बताना चाहता/चाहती")).forEach { (id, label) ->
                        CaregivingChoice(label, "caregiving-reason-$id") { controller.update(CaregivingStatusRules.stop(controller.status, today, id)); step = "done" }
                    }
                    TextButton(onClick = { step = "ask" }, modifier = Modifier.heightIn(min = 48.dp).testTag("caregiving-back")) { Text(t("Back", "वापस"), color = RustText, fontSize = 16.sp) }
                }
                "pause" -> {
                    CaregivingTitle(t("Pause reminders?", "रिमाइंडर रोकें?"))
                    CaregivingBody(t("Reminders for $personName stop for the time you choose, then start again. Records stay as they are.", "$personName के रिमाइंडर आपके चुने समय तक रुकेंगे, फिर अपने आप शुरू होंगे। रिकॉर्ड जैसे हैं वैसे रहेंगे।"))
                    CaregivingStatusRules.PAUSE_CHOICES.forEach { days ->
                        CaregivingChoice(t("Pause for $days days", "$days दिन के लिए रोकें"), "caregiving-pause-$days") { controller.update(CaregivingStatusRules.pause(controller.status, today, days)); onClose() }
                    }
                    CaregivingChoice(t("Keep reminders on", "रिमाइंडर चालू रखें"), "caregiving-keep") { controller.update(CaregivingStatusRules.steady(controller.status, today)); onClose() }
                }
                else -> {
                    CaregivingTitle(t("Reminders are off", "रिमाइंडर बंद हैं"))
                    CaregivingBody(t("All reminders for $personName are off on this phone. The records are kept, not deleted. Others in the care circle can still add to them.",
                        "इस फ़ोन पर $personName के सभी रिमाइंडर बंद हैं। रिकॉर्ड रखे गए हैं, मिटाए नहीं गए। देखभाल से जुड़े दूसरे लोग अब भी इनमें जोड़ सकते हैं।"))
                    CaregivingSupport(controller.status.reason == CaregivingStatusRules.PASSED_AWAY, context) { afterDeathOpen = true }
                    SaharaPrimaryButton(t("Done", "हो गया"), onClose, Modifier.fillMaxWidth().testTag("caregiving-done"), tone = SaharaTone.Teal)
                }
            }
        }
    }
}

/** Shown at the top of Today while reminders are paused or caregiving has ended: what is true now, and the way back. */
@Composable internal fun CaregivingStatusCard(controller: CaregivingStatusController, personName: String) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val status = controller.status
    var afterDeathOpen by rememberSaveable { mutableStateOf(false) }
    var supportOpen by rememberSaveable { mutableStateOf(false) }
    if (afterDeathOpen) AfterDeathGuideSheet { afterDeathOpen = false }
    val today = LocalDate.now()
    Surface(shape = RoundedCornerShape(20.dp), color = SaharaTone.Teal.wash, modifier = Modifier.fillMaxWidth().saharaContentSize().testTag("caregiving-status")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (status.state == CaregivingState.PAUSED) {
                val until = status.pausedUntil?.format(careDateFormat("d MMMM", hindi)).orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsPaused, null, Modifier.size(24.dp), tint = SaharaTone.Teal.ink); Spacer(Modifier.width(10.dp))
                    CaregivingTitle(t("Reminders paused until $until", "$until तक रिमाइंडर रुके हैं"))
                }
                CaregivingBody(t("Records stay as they are. Others in the care circle can still add to them.", "रिकॉर्ड जैसे हैं वैसे रहेंगे। देखभाल से जुड़े दूसरे लोग अब भी इनमें जोड़ सकते हैं।"))
                SaharaSecondaryButton(t("Turn reminders back on", "रिमाइंडर फिर से चालू करें"), { controller.update(CaregivingStatusRules.resume(status, today)) }, Modifier.fillMaxWidth().testTag("caregiving-resume"), icon = Icons.Default.NotificationsActive, tone = SaharaTone.Teal)
            } else {
                CaregivingTitle(t("Not actively caregiving", "अभी देखभाल नहीं कर रहे"))
                CaregivingBody(t("Reminders for $personName are off. The records are kept and can be opened from All care tools.", "$personName के रिमाइंडर बंद हैं। रिकॉर्ड रखे गए हैं और सभी देखभाल टूल्स से खोले जा सकते हैं।"))
                if (supportOpen) CaregivingSupport(status.reason == CaregivingStatusRules.PASSED_AWAY, context) { afterDeathOpen = true }
                else SaharaSecondaryButton(t("Support", "सहायता"), { supportOpen = true }, Modifier.fillMaxWidth().testTag("caregiving-support-open"), icon = Icons.Default.Spa, tone = SaharaTone.Teal)
                TextButton(onClick = { controller.update(CaregivingStatusRules.resume(status, today)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("caregiving-resume")) {
                    Text(t("I'm caring for $personName again", "मैं फिर से $personName की देखभाल कर रहा/रही हूँ"), color = RustText, fontSize = 16.sp)
                }
            }
        }
    }
}

/** The quiet row at the bottom of the family Today. */
@Composable internal fun CaregivingChangeRow(onOpen: () -> Unit) {
    val t = careText()
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("caregiving-change")) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Has your caregiving changed?", "क्या आपकी देखभाल में कुछ बदला है?"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                Text(t("Pause reminders, or stop them if you are no longer caregiving", "रिमाइंडर रोकें, या देखभाल नहीं कर रहे हों तो बंद करें"), fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DoctorCardSub)
        }
    }
}

/** Practical help only (no condolence text): Tele-MANAS, the after-death steps when that was the reason, and a caregiver group. */
@Composable private fun CaregivingSupport(passedAway: Boolean, context: Context, onAfterDeath: () -> Unit) {
    val t = careText()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("caregiving-support")) {
        CaregivingBody(t("Tele-MANAS is free, any time, in your language.", "टेली-मानस मुफ़्त है, किसी भी समय, आपकी भाषा में।"))
        SaharaPrimaryButton(t("Call Tele-MANAS 14416", "टेली-मानस 14416 पर कॉल करें"), { open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(AfterDeathGuide.TELE_MANAS)))) },
            Modifier.fillMaxWidth().testTag("caregiving-call-telemanas"), icon = Icons.Default.Call, tone = SaharaTone.Indigo)
        if (passedAway) SaharaSecondaryButton(t("What to do after a death", "मृत्यु के बाद क्या करें"), onAfterDeath, Modifier.fillMaxWidth().testTag("caregiving-after-death"), icon = Icons.Default.Spa, tone = SaharaTone.Teal, navigates = true)
        SaharaSecondaryButton(t("Swayam Foundation caregiver support", "स्वयं फ़ाउंडेशन देखभालकर्ता सहायता"), { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(CaregiverWellbeingRules.SUPPORT_GROUP_SWAYAM))) },
            Modifier.fillMaxWidth().testTag("caregiving-swayam"), icon = Icons.Default.Groups, tone = SaharaTone.Teal)
    }
}

@Composable private fun CaregivingTitle(text: String) = Text(text, fontFamily = SaharaTitleFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
@Composable private fun CaregivingBody(text: String) = Text(text, fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)
@Composable private fun CaregivingChoice(text: String, tag: String, onClick: () -> Unit) =
    SaharaSecondaryButton(text, onClick, Modifier.fillMaxWidth().testTag(tag), tone = SaharaTone.Teal)
