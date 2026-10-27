package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
        Verification.UNKNOWN -> if (hindi) "सत्यापन की स्थिति देखी जा रही है" else "Checking verification"
        Verification.NOT_SUBMITTED -> if (hindi) "पंजीकरण अभी जमा नहीं हुआ" else "Registration not submitted"
        Verification.PENDING -> if (hindi) "मंज़ूरी का इंतज़ार" else "Waiting for approval"
        Verification.APPROVED -> if (hindi) "डॉक्टर के रूप में मंज़ूर" else "Approved as a doctor"
        Verification.REJECTED -> if (hindi) "पंजीकरण मंज़ूर नहीं हुआ" else "Registration not approved"
        Verification.SUSPENDED -> if (hindi) "मंज़ूरी रोकी गई है" else "Approval on hold"
    }

    fun verificationBody(state: Verification, hindi: Boolean): String = when (state) {
        Verification.UNKNOWN -> if (hindi) "सर्वर से आपकी स्थिति ली जा रही है।" else "Asking the server for your status."
        Verification.NOT_SUBMITTED -> if (hindi) "अपना पंजीकृत नाम, मेडिकल काउंसिल और पंजीकरण नंबर जमा करें। मंज़ूरी के बाद ही मरीज़ आपको अपने डॉक्टर के रूप में जोड़ सकते हैं।" else "Submit your registered name, medical council and registration number. Patients can add you as their doctor only after approval."
        Verification.PENDING -> if (hindi) "Sahara टीम हर पंजीकरण खुद जाँचती है। यह अपने आप मंज़ूर नहीं होता, इसलिए इसमें समय लग सकता है। तब तक डॉक्टर वाला निमंत्रण कोड काम नहीं करेगा। बाद में \"फिर से देखें\" दबाएँ।" else "The Sahara team checks every registration by hand. Nothing is approved automatically, so this can take a while. Until then, a doctor invitation code will not work. Tap \"Check again\" later."
        Verification.APPROVED -> if (hindi) "मरीज़ या परिवार से मिला डॉक्टर निमंत्रण कोड अब काम करेगा। हर मरीज़ खुद तय करता है कि आप क्या देख सकते हैं।" else "Doctor invitation codes from a patient or family will now work. Each patient decides what you can see."
        Verification.REJECTED, Verification.SUSPENDED -> if (hindi) "डॉक्टर निमंत्रण कोड अभी काम नहीं करेंगे। विवरण ठीक करके फिर से जमा करें।" else "Doctor invitation codes will not work for now. Correct your details and submit them again."
    }

    fun checkedAt(at: Long, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        (if (hindi) "आख़िरी बार देखा: " else "Last checked ") + DateTimeFormatter.ofPattern("h:mm a", if (hindi) Locale("hi", "IN") else Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(at))
}

/** The app's shared English/Hindi choice, or a phone set to Hindi. */
@Composable internal fun doctorUiHindi(): Boolean = careHindi() || Locale.getDefault().language == "hi"

private fun doctorWhen(item: CareItem, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    if (item.due <= 0) return if (hindi) "तारीख़ दर्ज नहीं" else "Date not recorded"
    val timed = CareEntryCodec.decode(item.detail)?.timed == true
    return DateTimeFormatter.ofPattern(if (timed) "EEE, d MMM · h:mm a" else "EEE, d MMM", if (hindi) Locale("hi", "IN") else Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(item.due))
}

@Composable internal fun DoctorVerificationCard(state: DoctorConnectedHome.Verification, note: String, checkedAt: Long, checking: Boolean, error: String?, hindi: Boolean, onCheck: () -> Unit, onOpenForm: () -> Unit) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val color = when (state) { DoctorConnectedHome.Verification.APPROVED -> Mint; DoctorConnectedHome.Verification.PENDING, DoctorConnectedHome.Verification.UNKNOWN -> Gold.copy(alpha = .35f); else -> Peach }
    Surface(color = color, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("doctor-verification-card")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(DoctorConnectedHome.verificationTitle(state, hindi), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("doctor-verification-status"))
            Text(DoctorConnectedHome.verificationBody(state, hindi))
            if (note.isNotBlank() && state in setOf(DoctorConnectedHome.Verification.REJECTED, DoctorConnectedHome.Verification.SUSPENDED)) Text(t("Reviewer note: ", "जाँचने वाले की टिप्पणी: ") + note, style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (checkedAt > 0) Text(DoctorConnectedHome.checkedAt(checkedAt, hindi), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCheck, enabled = !checking, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-verification-check")) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (checking) t("Checking…", "देखा जा रहा है…") else t("Check again", "फिर से देखें"))
                }
                if (state != DoctorConnectedHome.Verification.PENDING && state != DoctorConnectedHome.Verification.UNKNOWN)
                    TextButton(onClick = onOpenForm, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-verification-form")) {
                        Text(if (state == DoctorConnectedHome.Verification.APPROVED) t("View details", "विवरण देखें") else t("Submit details", "विवरण जमा करें"))
                    }
            }
        }
    }
}

@Composable internal fun DoctorPatientSearchField(query: String, onQuery: (String) -> Unit, hindi: Boolean) {
    val focus = LocalFocusManager.current
    OutlinedTextField(query, { onQuery(it.take(80)) }, Modifier.fillMaxWidth().testTag("doctor-patient-search"), singleLine = true,
        placeholder = { Text(if (hindi) "नाम या मरीज़ ID (जैसे SH-1A2B3C4D)" else "Name or patient ID (e.g. SH-1A2B3C4D)") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, Modifier.size(48.dp)) { Icon(Icons.Default.Clear, if (hindi) "खोज साफ़ करें" else "Clear search") } },
        shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
}

/** Doctor Home: real visits and check-ins across this account's connected patients. */
internal fun LazyListScope.doctorConnectedHomeItems(people: List<Person>, items: List<CareItem>, now: Long, hindi: Boolean, onVisit: (String) -> Unit, onProgress: (String) -> Unit, onPatients: () -> Unit) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val visits = DoctorConnectedHome.upcomingVisits(people, items, now)
    val checks = DoctorConnectedHome.recentCheckIns(people, items, now)
    item(key = "home-patients") {
        Surface(color = Paper, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Your patients", "आपके मरीज़"), style = MaterialTheme.typography.titleLarge)
            Text(if (people.isEmpty()) t("No patient has shared their care with you yet.", "अभी किसी मरीज़ ने अपनी देखभाल आपसे साझा नहीं की है।")
                else t("${people.size} connected · shared by the patient or family", "${people.size} जुड़े हुए · मरीज़ या परिवार ने साझा किया"), modifier = Modifier.testTag("doctor-home-patient-count"))
            TextButton(onClick = onPatients, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (people.isEmpty()) t("Join with an invitation code", "निमंत्रण कोड से जुड़ें") else t("Open patients", "मरीज़ खोलें")) }
        } }
    }
    if (people.isEmpty()) return
    item(key = "home-visits-title") { Text(t("Upcoming visits", "आने वाली मुलाक़ातें"), style = MaterialTheme.typography.titleMedium) }
    if (visits.isEmpty()) item(key = "home-visits-empty") { Text(t("No open visits are planned for your patients.", "आपके मरीज़ों की कोई मुलाक़ात तय नहीं है।"), style = MaterialTheme.typography.bodyMedium) }
    items(visits.take(5), key = { "home-visit-${it.item.id}" }) { visit ->
        DoctorPatientListCard(visit.person.name, "${doctorWhen(visit.item, hindi)} · ${visit.item.title}", { onVisit(visit.person.id) })
    }
    item(key = "home-checks-title") { Text(t("Check-ins this week", "इस हफ़्ते के हालचाल"), style = MaterialTheme.typography.titleMedium) }
    if (checks.isEmpty()) item(key = "home-checks-empty") { Text(t("No check-ins reported in the last 7 days.", "पिछले 7 दिनों में कोई हालचाल दर्ज नहीं हुआ।"), style = MaterialTheme.typography.bodyMedium) }
    items(checks, key = { "home-check-${it.second.id}" }) { (person, check) ->
        DoctorPatientListCard(person.name, "${checkInMoodLabel(check.title, hindi)} · ${DateTimeFormatter.ofPattern("d MMM, h:mm a", if (hindi) Locale("hi", "IN") else Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(check.created))}", { onProgress(person.id) })
    }
}

/** Doctor Schedule: every open visit across connected patients, grouped by day. */
internal fun LazyListScope.doctorConnectedScheduleItems(people: List<Person>, items: List<CareItem>, now: Long, hindi: Boolean, onVisit: (String) -> Unit) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val visits = DoctorConnectedHome.upcomingVisits(people, items, now)
    item(key = "schedule-title") { Text(t("Schedule", "समय-सारणी"), style = MaterialTheme.typography.headlineMedium); Text(t("Open visits for the patients who share their care with you. Tap one to add or change a visit.", "जो मरीज़ आपसे देखभाल साझा करते हैं, उनकी खुली मुलाक़ातें। मुलाक़ात जोड़ने या बदलने के लिए छुएँ।")) }
    if (people.isEmpty()) item(key = "schedule-no-people") { Text(t("No connected patients yet.", "अभी कोई जुड़ा हुआ मरीज़ नहीं है।"), fontWeight = FontWeight.Medium) }
    else if (visits.isEmpty()) item(key = "schedule-empty") { Text(t("No open visits. Open a patient's Visits to plan one.", "कोई खुली मुलाक़ात नहीं। मुलाक़ात तय करने के लिए मरीज़ की मुलाक़ातें खोलें।")) }
    items(visits, key = { "schedule-${it.item.id}" }) { visit -> DoctorPatientListCard(visit.person.name, "${doctorWhen(visit.item, hindi)} · ${visit.item.title}", { onVisit(visit.person.id) }) }
    if (people.isNotEmpty()) {
        item(key = "schedule-by-patient") { Text(t("Plan a visit for", "इनके लिए मुलाक़ात तय करें"), style = MaterialTheme.typography.titleMedium) }
        items(people, key = { "schedule-person-${it.id}" }) { person -> DoctorPatientListCard(person.name, t("Visits", "मुलाक़ातें"), { onVisit(person.id) }) }
    }
}


/** The only place the signed-in workspace offers sample patients. They live on this device, apart from real care. */
@Composable internal fun DoctorWalkthroughCard(hindi: Boolean, onOpen: (DoctorTestProfiles.Profile) -> Unit) {
    Surface(color = Peach.copy(alpha = .45f), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("doctor-walkthrough-card")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (hindi) "वॉकथ्रू · नमूना मरीज़" else "Walkthrough · sample patients", style = MaterialTheme.typography.titleLarge)
            Text(if (hindi) "अभ्यास के लिए बनाए गए मरीज़, असली नहीं। ये सिर्फ़ इस फ़ोन पर रहते हैं और किसी असली मरीज़ के रिकॉर्ड में नहीं जाते।" else "Made-up patients for practice, not real people. They stay on this phone and never mix with a real patient's records.", style = MaterialTheme.typography.bodyMedium)
            DoctorTestProfiles.profiles.forEach { profile ->
                TextButton(onClick = { onOpen(profile) }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-walkthrough-${profile.key}")) { Text((if (hindi) "नमूना: " else "Sample: ") + profile.name) }
            }
        }
    }
}
