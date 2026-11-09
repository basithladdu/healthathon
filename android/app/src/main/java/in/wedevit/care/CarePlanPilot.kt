package `in`.wedevit.care

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * Pilot audit (clinical spec §8, Appendix D.3), the Kotlin twin of prototype/app/ectpr-pilot.ts. Doctor only, made-up patients only during the pilot.
 * Everything that can be worked out from the records is worked out here; the screen stays thin.
 */

enum class PilotPlace { HOME, HOSPICE, WARD, ICU, ED, OTHER }
data class EpisodeLog(val id: String, val patientId: String, val ectprId: String?, val edArrivalAt: String, val firstRecordAccessAt: String?, val highestSupportDelivered: Int?,
    val icuAdmitted: Boolean?, val vasopressorsGiven: Boolean? = null, val dialysisGiven: Boolean? = null, val cprGiven: Boolean?, val discordanceReason: String = "", val recordedBy: String = "", val recordedAt: String = "")
data class OutcomeRecord(val id: String, val patientId: String, val placeOfDeath: PilotPlace?, val preferredPlaceOfDeath: PilotPlace?, val icuDaysLast30: Int?, val ventilatorDaysLast30: Int?,
    val lastAdmissionAt: String?, val firstSymptomControlRxAt: String?, val outOfPocketLastAdmissionInr: Long?, val recordedBy: String = "", val recordedAt: String = "")
data class IncidentReport(val id: String, val patientId: String, val ectprId: String?, val appliedToReversibleIllness: Boolean, val familyFeltPushed: Boolean, val details: String, val reportedBy: String = "", val reportedAt: String = "")
data class ValuesIndicator(val valuesFilled: Boolean, val treatmentFilled: Int, val treatmentTotal: Int) { val limitsWithoutValues get() = treatmentFilled > 0 && !valuesFilled }
data class EpisodeRow(val episode: EpisodeLog, val minutes: Long?, val withinTarget: Boolean?, val concordant: Boolean?)
data class PilotSummary(val episodes: Int, val retrieved: Int, val withinTarget: Int, val compared: Int, val concordant: Int)
enum class EpisodeProblem { NO_ARRIVAL, ACCESS_BEFORE_ARRIVAL, REASON_NEEDED }
enum class OutcomeProblem { NOTHING_ENTERED, DAYS_OUT_OF_RANGE, RX_BEFORE_ADMISSION, NEGATIVE_SPEND }

object Pilot {
    /** Clinical spec §8: the record should be retrieved within 10 minutes of ED arrival. */
    const val RETRIEVAL_TARGET_MINUTES = 10L

    /** An ISO instant, or the phone's local "2026-10-29 10:30" as typed. Null when it is not a time. */
    fun instant(value: String?, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val v = value?.trim().orEmpty(); if (v.isEmpty()) return null
        return runCatching { Instant.parse(v) }.getOrNull() ?: runCatching { LocalDateTime.parse(v.replace(' ', 'T')).atZone(zone).toInstant() }.getOrNull()
    }
    fun iso(typed: String, zone: ZoneId = ZoneId.systemDefault()): String? = instant(typed, zone)?.toString()

    fun retrievalMinutes(e: EpisodeLog): Long? { val a = instant(e.firstRecordAccessAt) ?: return null; val b = instant(e.edArrivalAt) ?: return null; return Math.round(java.time.Duration.between(b, a).toMillis() / 60000.0) }

    /** Delivered care stayed within the recorded ceiling and CPR decision. Null when nothing was recorded to compare. */
    fun concordant(e: EpisodeLog, r: EctprRecord): Boolean? {
        val c = r.choices; val checks = mutableListOf<Boolean>()
        if (c.respCeiling != null && e.highestSupportDelivered != null) checks += e.highestSupportDelivered <= c.respCeiling
        if (c.cpr == Cpr.DNACPR && e.cprGiven != null) checks += !e.cprGiven
        if (c.icu?.kind == IcuKind.NO && e.icuAdmitted != null) checks += !e.icuAdmitted
        return if (checks.isEmpty()) null else checks.all { it }
    }

    private fun signedAt(r: EctprRecord) = r.signatures.firstOrNull { it.role == SignatureRole.TREATING_CONSULTANT }?.signedAt
    /** Reach: whole days from the date found eligible to the consultant's signature. Null until both exist. */
    fun reachDays(r: EctprRecord): Long? { val a = PlanDates.day(r.clinical.eligibilityDate) ?: return null; val b = PlanDates.day(signedAt(r).orEmpty()) ?: return null; return ChronoUnit.DAYS.between(a, b) }

    /** The 11 decision fields of DECISION_FIELDS in ectpr-rules.ts. */
    private fun treatmentFilled(c: PlanChoices) = listOf(c.hospitalTransfer, c.cpr, c.respCeiling, c.icu, c.goalOfCare, c.vasopressors, c.dialysis, c.feeding.ng, c.feeding.peg, c.hydration, c.ambulance).count { it != null }
    const val TREATMENT_TOTAL = 11
    fun valuesIndicator(r: EctprRecord) = ValuesIndicator(r.values.ownWords.isNotBlank() || r.values.unacceptableOutcomes.any(String::isNotBlank), treatmentFilled(r.choices), TREATMENT_TOTAL)

    /** The signed version in force at a moment: newest consultant signature at or before it, unless cancelled by then. */
    fun planInForceAt(versions: List<EctprRecord>, at: String): EctprRecord? {
        val moment = instant(at) ?: return null
        val newest = versions.filter { r -> instant(signedAt(r))?.isAfter(moment) == false }.maxByOrNull { instant(signedAt(it))!! } ?: return null
        return if (instant(newest.revokedAt)?.isAfter(moment) == false) null else newest
    }

    fun episodeProblems(e: EpisodeLog, plan: EctprRecord?): List<EpisodeProblem> = buildList {
        val arrival = instant(e.edArrivalAt)
        if (arrival == null) add(EpisodeProblem.NO_ARRIVAL)
        val access = instant(e.firstRecordAccessAt)
        if (arrival != null && access?.isBefore(arrival) == true) add(EpisodeProblem.ACCESS_BEFORE_ARRIVAL)
        if (plan != null && concordant(e, plan) == false && e.discordanceReason.isBlank()) add(EpisodeProblem.REASON_NEEDED)
    }

    /** First time the ED opened the record at or after arrival, from the access log. Family and clinic screens do not count. */
    fun firstEdAccessAfter(audit: List<AuditEvent>, arrivalAt: String, edDetail: String): String? {
        val arrival = instant(arrivalAt) ?: return null
        return audit.filter { it.action == AuditAction.BREAK_GLASS || (it.action == AuditAction.VIEW && it.detail == edDetail) }.mapNotNull { e -> instant(e.at)?.takeIf { !it.isBefore(arrival) } }.minOrNull()?.toString()
    }

    fun row(e: EpisodeLog, versions: List<EctprRecord>): EpisodeRow {
        val minutes = retrievalMinutes(e)
        val plan = versions.firstOrNull { it.id == e.ectprId } ?: planInForceAt(versions, e.edArrivalAt)
        return EpisodeRow(e, minutes, minutes?.let { it <= RETRIEVAL_TARGET_MINUTES }, plan?.let { concordant(e, it) })
    }

    fun summary(rows: List<EpisodeRow>) = PilotSummary(rows.size, rows.count { it.minutes != null }, rows.count { it.withinTarget == true }, rows.count { it.concordant != null }, rows.count { it.concordant == true })

    fun placeMatches(o: OutcomeRecord): Boolean? = if (o.placeOfDeath != null && o.preferredPlaceOfDeath != null) o.placeOfDeath == o.preferredPlaceOfDeath else null

    /** Hours from admission to the first symptom-control prescription, to one decimal. Null when unknown or out of order. */
    fun hoursToSymptomRx(o: OutcomeRecord): Double? {
        val a = instant(o.lastAdmissionAt) ?: return null; val b = instant(o.firstSymptomControlRxAt) ?: return null
        val ms = java.time.Duration.between(a, b).toMillis(); return if (ms < 0) null else Math.round(ms / 360000.0) / 10.0
    }

    fun outcomeProblems(o: OutcomeRecord): List<OutcomeProblem> = buildList {
        if (listOf(o.placeOfDeath, o.preferredPlaceOfDeath, o.icuDaysLast30, o.ventilatorDaysLast30, o.lastAdmissionAt, o.firstSymptomControlRxAt, o.outOfPocketLastAdmissionInr).all { it == null }) add(OutcomeProblem.NOTHING_ENTERED)
        if (listOf(o.icuDaysLast30, o.ventilatorDaysLast30).any { it != null && it !in 0..30 }) add(OutcomeProblem.DAYS_OUT_OF_RANGE)
        val a = instant(o.lastAdmissionAt); val b = instant(o.firstSymptomControlRxAt)
        if (a != null && b?.isBefore(a) == true) add(OutcomeProblem.RX_BEFORE_ADMISSION)
        if ((o.outOfPocketLastAdmissionInr ?: 0) < 0) add(OutcomeProblem.NEGATIVE_SPEND)
    }

    /** An incident must say what happened: a box ticked or a note written. */
    fun incidentEmpty(i: IncidentReport) = !i.appliedToReversibleIllness && !i.familyFeltPushed && i.details.isBlank()

    fun place(p: PilotPlace) = when (p) { PilotPlace.HOME -> Bi("Home", "घर"); PilotPlace.HOSPICE -> Bi("Hospice", "हॉस्पिस"); PilotPlace.WARD -> Bi("Hospital ward", "अस्पताल का वार्ड"); PilotPlace.ICU -> Bi.plain("ICU"); PilotPlace.ED -> Bi("Emergency department", "इमरजेंसी विभाग"); PilotPlace.OTHER -> Bi("Somewhere else", "कहीं और") }
}

object PilotJson {
    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k)
    private fun JSONObject.ns(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.ni(k: String): Int? = if (isNull(k)) null else optInt(k)
    private fun JSONObject.nb(k: String): Boolean? = if (isNull(k)) null else optBoolean(k)
    private fun JSONObject.n(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)
    private fun JSONObject.place(k: String) = if (isNull(k)) null else PilotPlace.entries.firstOrNull { it.name == optString(k) }

    fun encode(e: EpisodeLog) = JSONObject().put("id", e.id).put("patientId", e.patientId).n("ectprId", e.ectprId).put("edArrivalAt", e.edArrivalAt).n("firstRecordAccessAt", e.firstRecordAccessAt).n("highestSupportDelivered", e.highestSupportDelivered)
        .n("icuAdmitted", e.icuAdmitted).n("vasopressorsGiven", e.vasopressorsGiven).n("dialysisGiven", e.dialysisGiven).n("cprGiven", e.cprGiven).put("discordanceReason", e.discordanceReason).put("recordedBy", e.recordedBy).put("recordedAt", e.recordedAt)
    fun episode(j: JSONObject) = EpisodeLog(j.s("id"), j.s("patientId"), j.ns("ectprId"), j.s("edArrivalAt"), j.ns("firstRecordAccessAt"), j.ni("highestSupportDelivered"), j.nb("icuAdmitted"), j.nb("vasopressorsGiven"), j.nb("dialysisGiven"), j.nb("cprGiven"), j.s("discordanceReason"), j.s("recordedBy"), j.s("recordedAt"))
    fun encode(o: OutcomeRecord) = JSONObject().put("id", o.id).put("patientId", o.patientId).n("placeOfDeath", o.placeOfDeath?.name).n("preferredPlaceOfDeath", o.preferredPlaceOfDeath?.name).n("icuDaysLast30", o.icuDaysLast30).n("ventilatorDaysLast30", o.ventilatorDaysLast30)
        .n("lastAdmissionAt", o.lastAdmissionAt).n("firstSymptomControlRxAt", o.firstSymptomControlRxAt).n("outOfPocketLastAdmissionInr", o.outOfPocketLastAdmissionInr).put("recordedBy", o.recordedBy).put("recordedAt", o.recordedAt)
    fun outcome(j: JSONObject) = OutcomeRecord(j.s("id"), j.s("patientId"), j.place("placeOfDeath"), j.place("preferredPlaceOfDeath"), j.ni("icuDaysLast30"), j.ni("ventilatorDaysLast30"), j.ns("lastAdmissionAt"), j.ns("firstSymptomControlRxAt"), if (j.isNull("outOfPocketLastAdmissionInr")) null else j.optLong("outOfPocketLastAdmissionInr"), j.s("recordedBy"), j.s("recordedAt"))
    fun encode(i: IncidentReport) = JSONObject().put("id", i.id).put("patientId", i.patientId).n("ectprId", i.ectprId).put("appliedToReversibleIllness", i.appliedToReversibleIllness).put("familyFeltPushed", i.familyFeltPushed).put("details", i.details).put("reportedBy", i.reportedBy).put("reportedAt", i.reportedAt)
    fun incident(j: JSONObject) = IncidentReport(j.s("id"), j.s("patientId"), j.ns("ectprId"), j.optBoolean("appliedToReversibleIllness"), j.optBoolean("familyFeltPushed"), j.s("details"), j.s("reportedBy"), j.s("reportedAt"))
}

/** DPDP notice (tech spec §7, L10), the twin of prototype/app/dpdp.ts. It says only what the code does; the web's "browser" reads "phone" here. */
enum class DpdpScope { CARE_PLAN, LIVING_WILL }
data class NoticeBlock(val title: Bi, val body: Bi)
object Dpdp {
    fun notice(scope: DpdpScope): List<NoticeBlock> {
        val plan = scope == DpdpScope.CARE_PLAN; val what = Bi("What we keep", "हम क्या रखते हैं"); val why = Bi("Why", "क्यों"); val where = Bi("Where it is", "यह कहाँ रहता है"); val rights = Bi("Your rights", "आपके अधिकार")
        return listOf(
            NoticeBlock(what, if (plan) Bi("Name, hospital number, date of birth, address and phone. The people who decide for the patient. Illness details, treatment choices, the patient’s own words and signatures. A log of who opened or changed the plan.", "नाम, अस्पताल नंबर, जन्म तिथि, पता और फ़ोन। मरीज़ की ओर से फ़ैसला लेने वाले लोग। बीमारी की जानकारी, इलाज के फ़ैसले, मरीज़ के अपने शब्द और हस्ताक्षर। यह रिकॉर्ड कि किसने योजना खोली या बदली।")
                else Bi("Your name, date of birth, address and the last 4 digits of your ID. Your representatives and witnesses. Your treatment wishes. The signed scan and its fingerprint (SHA-256). A log of each change, print and share.", "आपका नाम, जन्म तिथि, पता और पहचान पत्र के आख़िरी 4 अंक। आपके प्रतिनिधि और गवाह। इलाज के बारे में आपकी इच्छाएँ। हस्ताक्षर वाला स्कैन और उसकी पहचान (SHA-256)। हर बदलाव, प्रिंट और साझा करने का रिकॉर्ड।")),
            NoticeBlock(why, if (plan) Bi("So that an emergency doctor can find and follow the patient’s wishes, and the care team can review them.", "ताकि इमरजेंसी डॉक्टर मरीज़ की इच्छाएँ देख और मान सकें, और देखभाल टीम उनकी समीक्षा कर सके।")
                else Bi("To write down your advance care planning, keep track of its versions, and show doctors that it exists.", "आपकी अग्रिम देखभाल योजना को लिखने, उसके संस्करण सँभालने और डॉक्टरों को यह दिखाने के लिए कि वह मौजूद है।")),
            NoticeBlock(where, if (plan) Bi("On this phone. It leaves this phone only in two cases. When a plan is signed, an emergency card is issued and a copy of the signed plan goes to the Sahara server so the QR code works. If an SMS is sent, the phone number goes to the SMS company.", "इसी फ़ोन पर। यह सिर्फ़ दो हालात में बाहर जाता है। योजना पर हस्ताक्षर होने पर इमरजेंसी कार्ड जारी होता है और साइन की गई योजना की कॉपी Sahara सर्वर पर जाती है, ताकि QR कोड चले। SMS भेजने पर फ़ोन नंबर SMS कंपनी के पास जाता है।")
                else Bi("On this phone. It is not sent to any server. If an emergency card is issued, the card carries only the status, date, version and attesting officer of your advance care planning. You decide who gets a paper copy.", "इसी फ़ोन पर। यह किसी सर्वर पर नहीं भेजा जाता। इमरजेंसी कार्ड जारी होने पर कार्ड में सिर्फ़ अग्रिम देखभाल योजना की स्थिति, तारीख़, संस्करण और सत्यापन अधिकारी रहते हैं। काग़ज़ की कॉपी किसे देनी है, यह आप तय करते हैं।")),
            NoticeBlock(rights, if (plan) Bi("See it: download your data below. Correct it: ask for a review, and a new version is made. Withdraw: cancel the plan at any time, and the card stops working. Delete: remove an unsigned draft below. Signed versions and the log cannot be deleted, because they show what was decided and who saw it.", "देखना: नीचे अपना डेटा डाउनलोड करें। सुधारना: समीक्षा माँगें, नया संस्करण बनेगा। वापस लेना: योजना कभी भी रद्द करें, कार्ड काम करना बंद कर देगा। मिटाना: बिना साइन का ड्राफ़्ट नीचे से हटाएँ। साइन किए गए संस्करण और रिकॉर्ड नहीं मिटते, क्योंकि उनसे पता चलता है कि क्या तय हुआ और किसने देखा।")
                else Bi("See it: download your data below. Correct it: make a new version. Withdraw: cancel it in writing at any time. Delete: remove an unsigned draft below. Signed versions and the log cannot be deleted. Deleting here does not touch any paper you printed, so destroy that yourself.", "देखना: नीचे अपना डेटा डाउनलोड करें। सुधारना: नया संस्करण बनाएँ। वापस लेना: कभी भी लिखित रूप में रद्द करें। मिटाना: बिना साइन का ड्राफ़्ट नीचे से हटाएँ। साइन किए गए संस्करण और रिकॉर्ड नहीं मिटते। यहाँ मिटाने से छपा हुआ काग़ज़ नहीं मिटता, उसे आप ख़ुद नष्ट करें।")),
            NoticeBlock(Bi("How to ask", "कैसे कहें"), Bi("Use the buttons below, or tell your doctor or palliative care team. Ask them too if you want a copy on the Sahara server erased. During this pilot only made-up patients are used.", "नीचे के बटन इस्तेमाल करें, या अपने डॉक्टर या पैलिएटिव केयर टीम को बताएँ। Sahara सर्वर से कॉपी मिटवानी हो तो भी उन्हीं से कहें। इस पायलट में सिर्फ़ काल्पनिक मरीज़ों का डेटा है।")),
        )
    }

    /** buildDataExport in dpdp.ts. The card's token and manage key are left out on purpose: they are credentials. [scans] are data URLs by scan key. */
    fun export(s: CarePlanState, patientId: String, now: Instant, scans: Map<String, JSONObject> = emptyMap()): JSONObject = JSONObject()
        .put("format", "Sahara personal data export").put("formatVersion", 1).put("exportedAt", now.toString()).put("patientId", patientId).put("storedOn", "This phone (private app storage)")
        .put("ectprVersions", org.json.JSONArray(s.ectprs.filter { it.patientId == patientId }.map(PlanJson::encode))).put("audit", org.json.JSONArray(s.audit.events.map(PlanJson::encode)))
        .put("episodes", org.json.JSONArray(s.episodes.filter { it.patientId == patientId }.map(PilotJson::encode))).put("outcomes", org.json.JSONArray(s.outcomes.filter { it.patientId == patientId }.map(PilotJson::encode))).put("incidents", org.json.JSONArray(s.incidents.filter { it.patientId == patientId }.map(PilotJson::encode)))
        .put("amdVersions", org.json.JSONArray(s.amds.filter { it.patientId == patientId }.map(AmdJson::encode)))
        .put("scans", JSONObject().also { j -> scans.forEach { (k, v) -> j.put(k, v) } })
        .put("cards", org.json.JSONArray(s.tokens.filter { it.patientId == patientId }.map { c -> JSONObject().put("issuedAt", c.createdAt).put("version", c.version).put("server", c.server.name.lowercase().replace('_', '-')).also { j -> c.revokedAt?.let { j.put("revokedAt", it) } } }))
    fun scanKeys(s: CarePlanState, patientId: String): List<String> = s.amds.filter { it.patientId == patientId }.flatMap { listOfNotNull(it.execution?.scanKey?.ifBlank { null }, it.revocation?.scanKey?.ifBlank { null }) }.distinct()
    fun fileName(patientId: String, today: LocalDate) = "sahara-my-data-${patientId.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "patient" }}-$today.json"
}

/** What the read-aloud button says on each screen: the same words the screen shows, in the chosen language. */
object PlanSpeech {
    private fun line(label: Bi, value: Bi?, hindi: Boolean) = label.pick(hindi) + ": " + (value ?: NO_PREFERENCE).pick(hindi) + "."
    private fun said(label: Bi, value: String, hindi: Boolean) = label.pick(hindi) + ": " + value.ifBlank { NOT_RECORDED.pick(hindi) } + "."

    fun wizardStep(d: EctprRecord, step: Int, hindi: Boolean): String = when (step) {
        0 -> listOf(said(Bi("Patient's name", "मरीज़ का नाम"), d.patient.name, hindi), said(Bi("Gender", "जेंडर"), d.patient.gender, hindi), said(Bi("Diagnosis", "बीमारी"), d.clinical.diagnosis, hindi), said(Bi("Allergies", "एलर्जी"), d.clinical.allergies, hindi), said(Bi("Key medicines", "मुख्य दवाएँ"), d.clinical.keyMedicines, hindi))
        1 -> listOf(said(Bi("Primary decision-maker", "मुख्य फ़ैसला लेने वाले"), d.decisionMakers.primary?.let { "${it.name} ${it.relation} ${it.phone24h}".trim() }.orEmpty(), hindi), said(Bi("Secondary decision-maker", "द्वितीय फ़ैसला लेने वाले"), d.decisionMakers.alternate?.let { "${it.name} ${it.relation} ${it.phone24h}".trim() }.orEmpty(), hindi),
            said(Bi("Present for the discussion", "बातचीत में मौजूद"), d.decisionMakers.presentAtDiscussion.filter(String::isNotBlank).joinToString(", "), hindi), said(Bi("Could not be reached", "संपर्क नहीं हो सका"), d.decisionMakers.attemptedContact.filter(String::isNotBlank).joinToString(", "), hindi))
        2 -> listOf(Bi("Capacity is decided for this decision, now — not taken from old notes. Record the basis: can the patient understand, remember, weigh and communicate the choice?", "निर्णय-क्षमता इसी फ़ैसले के लिए, अभी जाँची जाती है — पुराने नोट से नहीं। आधार लिखें: क्या मरीज़ फ़ैसला समझ, याद रख, तौल और बता सकते हैं?").pick(hindi), line(Bi("Capacity", "निर्णय-क्षमता"), d.capacity.status?.let(PlanText::capacity) ?: NOT_RECORDED, hindi)) + (listOf(CapacityGuide.TITLE, CapacityGuide.NOTE) + CapacityGuide.POINTS).map { it.pick(hindi) }
        3 -> listOf(Bi("Treatment choices stay locked until this is ticked. A choice made without knowing the illness cannot be cured is not an informed choice.", "जब तक इस पर टिक न हो, इलाज के फ़ैसले बंद रहेंगे। बीमारी ठीक न होने की जानकारी के बिना लिया फ़ैसला सोच-समझकर लिया फ़ैसला नहीं है।").pick(hindi), Bi("The illness has been explained as incurable, and the patient or family can say it back in their own words.", "समझा दिया गया है कि बीमारी ठीक नहीं होगी, और मरीज़ या परिवार इसे अपने शब्दों में दोहरा सकते हैं।").pick(hindi))
        4 -> listOf(said(Bi("When was this recorded?", "यह कब दर्ज किया गया?"), d.values.knowsRecordedWhen?.let { PlanText.knowsRecordedWhen(it).pick(hindi) }.orEmpty(), hindi), said(Bi("What matters most, in the patient's own words", "सबसे ज़रूरी क्या है, मरीज़ के अपने शब्दों में"), d.values.ownWords, hindi), said(Bi("Outcomes the patient would not accept (one per line)", "जो हालात मरीज़ को मंज़ूर नहीं (हर लाइन में एक)"), d.values.unacceptableOutcomes.filter(String::isNotBlank).joinToString(", "), hindi))
        5 -> { val c = d.choices; listOf(Bi("Leave anything not discussed as \"No preference recorded\": that means standard care, never refusal. Each level allowed includes everything below it.", "जिस पर बात नहीं हुई उसे \"कोई पसंद दर्ज नहीं\" पर छोड़ दें: इसका मतलब सामान्य इलाज है, मना करना नहीं। हर मान्य स्तर में उससे नीचे का सब शामिल है।").pick(hindi),
            line(Bi("Goal of care", "देखभाल का लक्ष्य"), c.goalOfCare?.let(PlanText::goal), hindi), line(Bi("Going to hospital", "अस्पताल जाना"), c.hospitalTransfer?.let { PlanText.transfer(it.kind) }, hindi), line(Bi("CPR if the heart stops", "दिल रुकने पर CPR"), c.cpr?.let(PlanText::cpr), hindi),
            line(Bi("Highest breathing support", "साँस की सबसे ऊँची मदद"), c.respCeiling?.let(PlanText::ceiling), hindi), line(Bi("Intensive care (ICU)", "इंटेंसिव केयर (ICU)"), c.icu?.let { PlanText.icu(it.kind) }, hindi), line(Bi("Dialysis", "डायलिसिस"), c.dialysis?.let { PlanText.dialysis(it.kind) }, hindi), line(Bi("Fluids", "तरल"), c.hydration?.let(PlanText::hydration), hindi)) + PlanText.homeCareLines(d).map { it.pick(hindi) } }
        6 -> listOf(Bi("After this date the ED view shows the plan as OUT OF DATE and standard care applies.", "इस तारीख़ के बाद ED व्यू योजना को पुरानी दिखाएगा और सामान्य इलाज लागू होगा।").pick(hindi), said(Bi("Review due", "समीक्षा की तारीख़"), d.reviewDue, hindi))
        7 -> listOf(Bi("An unsigned form is a conversation note, not a plan. It becomes active only when the treating consultant signs with their registration number. Keep the signed paper copy with the family.", "बिना हस्ताक्षर का फ़ॉर्म बातचीत का नोट है, योजना नहीं। यह तभी लागू होता है जब इलाज करने वाले कंसल्टेंट अपने रजिस्ट्रेशन नंबर के साथ हस्ताक्षर करें। हस्ताक्षरित काग़ज़ी कॉपी परिवार के पास रखें।").pick(hindi))
        else -> listOf(summary(d, null, hindi))
    }.joinToString("\n")

    /** The whole plan: rows, then what always continues. */
    fun summary(r: EctprRecord, amd: Bi?, hindi: Boolean): String = (EdView.rows(r, amd ?: NOT_RECORDED).filter { amd != null || it.key != "AMD" }.map { it.label.pick(hindi) + ": " + it.value.pick(hindi) + "." } + PlanText.fullRecordLines(r).map { it.pick(hindi) + "." } + careContinues(hindi)).joinToString("\n")
    private fun careContinues(hindi: Boolean) = (listOf(CareThatContinues.TITLE) + CareThatContinues.ITEMS).joinToString("\n") { it.pick(hindi) }
    private fun ifInDoubt(hindi: Boolean) = (listOf(IfInDoubt.TITLE) + IfInDoubt.CONDITIONS + IfInDoubt.AFTER).joinToString("\n") { it.pick(hindi) }

    fun edView(interp: EdInterpretation, amd: Bi, hindi: Boolean): String = buildList {
        add(interp.banner.pick(hindi)); interp.flags.forEach { add(it.pick(hindi)) }; add(ifInDoubt(hindi))
        val r = interp.record
        if (r != null && interp.showContent) add(summary(r, amd, hindi)) else { add(amd.pick(hindi)); add(careContinues(hindi)) }
    }.joinToString("\n")

    fun livingWill(c: AmdContent, hindi: Boolean): String = buildList {
        fun p(b: Bi) = b.pick(hindi)
        add(p(AmdText.PAPER))
        add(p(Bi("Made by", "बनाने वाले")) + ": " + c.executant.name.ifBlank { p(Bi("Not filled", "नहीं भरा")) } + ".")
        add(p(Bi("Applies to", "लागू होगा")) + ": " + c.conditions.joinToString(", ") { p(AmdText.condition(it)) }.ifBlank { p(Bi("Not filled", "नहीं भरा")) } + ".")
        AmdTreatment.entries.forEach { t -> add(p(AmdText.treatment(t)) + ": " + p(c.treatments[t]?.let(AmdText::choice) ?: AmdText.NOT_DECIDED) + ".") }
        add(p(AmdText.COMFORT))
        add(p(Bi("Representatives, in order", "प्रतिनिधि, क्रम से")) + ": " + c.dhrs.filter { it.name.isNotBlank() }.joinToString(", ") { it.name } + ".")
        add(p(AmdText.CAVEAT))
    }.joinToString("\n")
}
