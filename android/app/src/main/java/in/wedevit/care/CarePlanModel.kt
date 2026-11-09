package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.Period

/*
 * Sahara emergency care plan (ECTPR): the Kotlin twin of prototype/app/ectpr-model.ts.
 * Source: SVK's tech spec §5 and clinical spec §3–§6 (source-materials/team-context/svk-specs-2026-10-29).
 * Choices are stored as enums with the same names as the web model, never as translated text, so one record
 * prints in any language and the JSON written here reads on the web and back.
 * null always means "No preference recorded" = standard care (rule V1). Nothing is pre-selected.
 */

const val ECTPR_FORM_VERSION = "Sahara ECTPR v1.0"

/** An English/Hindi pair for text that is shown but never stored. */
data class Bi(val en: String, val hi: String, val local: String? = null) {
    /** Telugu, Urdu and Kannada come from the dictionaries; [local] is set when a line is built from smaller translated parts. */
    fun pick(hindi: Boolean) = if (hindi) hi else local ?: careTr(false, en, hi)
    companion object {
        /** Parts joined in every language, so a composed line still reads in the chosen language. */
        fun join(separator: String, parts: List<Bi>) = Bi(parts.joinToString(separator) { it.en }, parts.joinToString(separator) { it.hi }, parts.joinToString(separator) { it.pick(false) })
        fun labeled(label: Bi, value: Bi) = join(": ", listOf(label, value))
        fun plain(value: String) = Bi(value, value, value)
    }
}

enum class GoalOfCare { A_LIFE, B_CEILING, C_COMFORT }
enum class Cpr { ATTEMPT, DNACPR }
enum class DnacprReason { FUTILITY, PATIENT_REFUSAL, BURDEN_OUTWEIGHS_BENEFIT }
enum class TransferReason { PAIN, BREATHLESSNESS, BLEEDING, SEIZURES, FALL_FRACTURE, BLOCKED_TUBE, INFECTION_IV, CONFUSION, CAREGIVER_RESPITE }
enum class TransferKind { ANY, ONLY_FOR, NO }
data class HospitalTransfer(val kind: TransferKind, val reasons: List<TransferReason> = emptyList())
enum class DurationUnit { HOURS, DAYS, SESSIONS }
/** A time-limited trial (clinical spec §10.1): duration, formal review date/time and named reviewers. */
data class TrialPlan(val durationValue: Int = 0, val durationUnit: DurationUnit = DurationUnit.DAYS, val reviewAt: String = "", val reviewers: List<String> = emptyList()) {
    val complete: Boolean get() = durationValue > 0 && reviewAt.isNotBlank() && reviewers.any { it.isNotBlank() }
}
enum class IcuKind { YES, TRIAL, NO }
data class Icu(val kind: IcuKind, val trial: TrialPlan? = null)
enum class VasopressorKind { NO, TRIAL, YES_NO_LIMIT, YES_MAX_AGENTS }
data class Vasopressors(val kind: VasopressorKind, val trial: TrialPlan? = null, val maxAgents: Int? = null)
enum class DialysisKind { NO, TRIAL, YES, ONLY_IF_REVERSIBLE }
data class Dialysis(val kind: DialysisKind, val trial: TrialPlan? = null)
enum class NgKind { NO, UP_TO_DAYS, YES }
data class NgFeeding(val kind: NgKind, val days: Int? = null)
enum class PegFeeding { NO, ONLY_AFTER_PALLIATIVE_DISCUSSION, YES }
enum class Hydration { ORAL_SIPS, LIMITED, FULL }
enum class AmbulanceOption { FULL_RESUSCITATION, SYMPTOM_RELIEF_TRANSPORT, TRANSPORT_HOME_NURSE_ESCORT }
enum class Capacity { HAS, HAS_DELEGATED, FLUCTUATES, LACKS }
enum class AuthorityBasis { NAMED_IN_AMD, NAMED_BY_PATIENT, NEXT_OF_KIN, COURT_APPOINTED }
enum class IdType { AADHAAR, VOTER_ID, PAN, PASSPORT, DRIVING_LICENCE, RATION_CARD, OTHER }
enum class DirectiveKind { VALID_AMD, PRIOR_WISHES, NONE }
data class ExistingDirective(val kind: DirectiveKind, val date: String = "", val location: String = "", val attestor: String = "", val note: String = "",
    /** The directive was signed before two witnesses (true), or not (false). Null when not recorded. */
    val twoWitnesses: Boolean? = null)
enum class ImplantedDevice { NONE, PACEMAKER, ICD, CRT_D, OTHER }
enum class IcdDecision { DEACTIVATE, KEEP }

data class DecisionMaker(
    val name: String = "", val relation: String = "", val phone24h: String = "",
    /** Never a full Aadhaar: ID type + last 4 characters only (tech spec §2). */
    val idType: IdType? = null, val idLast4: String? = null,
    val authorityBasis: AuthorityBasis? = null,
) { val blank: Boolean get() = name.isBlank() && relation.isBlank() && phone24h.isBlank() }

enum class EctprStatus { DRAFT, ACTIVE, SUPERSEDED, REVOKED }
enum class SignatureRole { TREATING_CONSULTANT, PALLIATIVE_CLINICIAN, NURSE, PATIENT, SURROGATE, WITNESS }
/** Which decision-maker signed, when the role is SURROGATE (legal review). */
enum class SurrogateRank { PRIMARY, SECONDARY }
data class Signature(val role: SignatureRole, val name: String, val registrationNo: String? = null, val signedAt: String, val thumbImpression: Boolean? = null, val surrogateRank: SurrogateRank? = null)

data class EctprPatient(val name: String = "", val dob: String = "", val sex: String = "", val address: String = "", val phone: String = "", val uhid: String = "", val languagePref: String = "", val gender: String = "")
data class CareTeam(val oncologist: String = "", val palliativePhysician: String = "", val palliativePhone24h: String = "", val extraTeam: List<String> = emptyList(), val localDoctor: String = "")
data class DecisionMakers(val primary: DecisionMaker? = null, val alternate: DecisionMaker? = null, val familyToInform: List<String> = emptyList(), val familyConsensusNote: String = "",
    val presentAtDiscussion: List<String> = emptyList(), val attemptedContact: List<String> = emptyList())
data class IcdDeactivation(val decision: IcdDecision? = null, val date: String = "")
data class ClinicalDetails(val diagnosis: String = "", val stage: String = "", val ecog: Int? = null, val allergies: String = "", val implantedDevice: ImplantedDevice? = null,
    val icdDeactivation: IcdDeactivation = IcdDeactivation(), val keyMedicines: String = "", val eligibilityDate: String = "")
data class CapacityAssessment(val status: Capacity? = null, val assessor: String = "", val assessedOn: String = "", val basis: String = "", val reassessOn: String = "")
data class PrognosticUnderstanding(val confirmed: Boolean = false, val confirmedBy: String = "", val confirmedOn: String = "")
data class SurrogateDeclaration(val answeringAsSurrogate: Boolean = false, val confirmed: Boolean = false, val confirmedOn: String = "")
enum class KnowsRecordedWhen { BEFORE_COUNSELLING, AFTER_COUNSELLING }
data class PlanValues(val patientKnows: Boolean? = null, val ownWords: String = "", val unacceptableOutcomes: List<String> = emptyList(), val familyAskedNotToTell: Boolean = false,
    val directQuestionPlan: String = "", val financialConcernDiscussed: Boolean? = null, val socialWorkReferral: Boolean? = null, val knowsRecordedWhen: KnowsRecordedWhen? = null,
    /** The patient knows the diagnosis and the prognosis (legal form 5.3). */
    val knowsPrognosis: Boolean = false,
    /** The patient does not wish to know details and has delegated (legal form 5.3). */
    val doesNotWishToKnow: Boolean = false)
data class DnacprDecidedBy(val name: String = "", val registrationNo: String = "", val decidedAt: String = "")
data class CeilingConditions(val reversibleCauseException: Boolean? = null, val nivMaxHours: Int? = null, val spo2Floor: Int? = null, val homeNivReason: String = "")
/** Comfort feeding by mouth is always on (L9); it is stored so the record says so explicitly. */
data class Feeding(val ng: NgFeeding? = null, val peg: PegFeeding? = null, val removeExistingTube: Boolean = false) { val comfortOral: Boolean get() = true }
data class PlanChoices(
    val goalOfCare: GoalOfCare? = null,
    val cpr: Cpr? = null, val dnacprReason: DnacprReason? = null, val dnacprDiscussedWith: String = "",
    val dnacprDecidedBy: DnacprDecidedBy? = null,
    /** V2 reason. */
    val cprConflictReason: String = "",
    val hospitalTransfer: HospitalTransfer? = null,
    val callBeforeAmbulance: List<String> = emptyList(),
    val ambulance: AmbulanceOption? = null,
    /** 0 comfort only · 1 nasal prongs/simple mask · 2 non-rebreather/venturi · 3 HFNC · 4 NIV · 5 intubation. Highest acceptable level. */
    val respCeiling: Int? = null, val maxOxygenLpm: Double? = null,
    val ceilingConditions: CeilingConditions = CeilingConditions(),
    val icu: Icu? = null, val vasopressors: Vasopressors? = null, val dialysis: Dialysis? = null,
    val feeding: Feeding = Feeding(),
    val hydration: Hydration? = null,
    /** Reasons recorded against conflict warnings C1, C2 and C3. */
    val conflictReasons: Map<String, String> = emptyMap(),
    /** V3 confirmation. */
    val goalAConfirmed: Boolean = false,
    /** Optional time-limited NIV trial (C4 lists NIV among the trials). An extra field the web model may ignore. */
    val nivTrial: TrialPlan? = null,
) {
    val anyChosen: Boolean get() = goalOfCare != null || cpr != null || hospitalTransfer != null || ambulance != null || respCeiling != null || icu != null ||
        vasopressors != null || dialysis != null || feeding.ng != null || feeding.peg != null || feeding.removeExistingTube || hydration != null || nivTrial != null
}

/** Legal review, Rituparna P, 31 Oct 2026. Null on the record because older saved records do not have them. */
data class EctprHomeCare(val assessmentFrequency: String = "", val coordinator: String = "", val ngoContact: String = "", val ngoConsulted: Boolean = false, val previousAdmission: Boolean? = null, val previousAdmissionDetails: String = "")
/** The removed "stopping or not starting treatment" step (lead doctor, Nov 2026). Kept only so older saved plans still load and save unchanged; never shown. */
enum class WithdrawalFinal { TO_BE_PERFORMED, FAMILY_REFUSED, BOARD_REFUSED, PHYSICIAN_REVOKED }
data class EctprWithdrawal(val applicable: Boolean = false, val primaryBoardDate: String = "", val secondaryBoardDate: String = "", val primaryBoardMembers: String = "", val secondaryBoardMembers: String = "", val boardDecision: String = "", val finalDecision: WithdrawalFinal? = null, val protocol: String = "")

data class EctprRecord(
    val id: String, val patientId: String, val version: Int = 1, val formVersion: String = ECTPR_FORM_VERSION, val status: EctprStatus = EctprStatus.DRAFT,
    val createdAt: String, val updatedAt: String = createdAt, val createdBy: String = "",
    val createdInEd: Boolean = false,
    val patient: EctprPatient = EctprPatient(),
    val careTeam: CareTeam = CareTeam(),
    val decisionMakers: DecisionMakers = DecisionMakers(),
    val clinical: ClinicalDetails = ClinicalDetails(),
    val capacity: CapacityAssessment = CapacityAssessment(),
    val existingDirective: ExistingDirective? = null,
    /** V4 gate: "illness explained as incurable; family can repeat it back". */
    val prognosticUnderstanding: PrognosticUnderstanding = PrognosticUnderstanding(),
    /** V5: surrogate's substituted-judgement declaration. */
    val surrogateDeclaration: SurrogateDeclaration = SurrogateDeclaration(),
    val values: PlanValues = PlanValues(),
    val choices: PlanChoices = PlanChoices(),
    val homeCare: EctprHomeCare? = null,
    val withdrawal: EctprWithdrawal? = null,
    val reviewDue: String = "",
    val signatures: List<Signature> = emptyList(),
    val limitedLiteracy: Boolean = false,
    val revokedAt: String? = null, val revokedReason: String? = null,
    val supersedes: String? = null,
    /** Optional goals-of-care talk: verbatim lines and the doctor's note. Local only; EdCardSnapshot removes it from the card. */
    val gocTranscript: GocTranscript? = null,
) {
    val consultantSignature: Signature? get() = signatures.firstOrNull { it.role == SignatureRole.TREATING_CONSULTANT && it.name.isNotBlank() && !it.registrationNo.isNullOrBlank() }
    val witnesses: List<Signature> get() = signatures.filter { it.role == SignatureRole.WITNESS && it.name.isNotBlank() }
}

enum class IssueSeverity { BLOCK, CONFIRM, WARN }
/** Same as the web: BLOCK stops saving (or, for [EctprRules.ACTIVATION_ONLY], signing), CONFIRM stops signing until a reason or confirmation is recorded, WARN is shown only. */
data class RuleIssue(val id: String, val severity: IssueSeverity, val field: String, val message: String, val messageHi: String) {
    fun text(hindi: Boolean) = careTr(hindi, message, messageHi)
}

internal object PlanDates {
    fun day(value: String): LocalDate? = value.trim().takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
    fun age(dob: String, today: LocalDate): Int? = day(dob)?.takeIf { !it.isAfter(today) }?.let { Period.between(it, today).years }
}

/** ID type + last 4 only. Whatever is typed (even a full 12-digit Aadhaar) keeps just its last four letters or digits. */
object PlanIds {
    private val LAST4 = Regex("^[A-Za-z0-9]{4}$")
    fun last4(input: String): String = input.filter { it.isLetterOrDigit() }.takeLast(4).uppercase()
    fun valid(value: String?): Boolean = value == null || LAST4.matches(value)
}

/** Rules V1–V9 (tech spec §5) and C1–C8 (clinical spec §4), plus L1 and the trial and capacity completeness checks. */
object EctprRules {
    /** Checks that only stop a plan becoming Active; a draft may be saved without them. */
    val ACTIVATION_ONLY = setOf("V6", "V7", "V8", "REVIEW_DUE")

    private fun issue(id: String, severity: IssueSeverity, field: String, en: String, hi: String) = RuleIssue(id, severity, field, en, hi)

    fun check(r: EctprRecord, today: LocalDate): List<RuleIssue> = buildList {
        val c = r.choices
        val ceiling = c.respCeiling
        // L1 / scope: adults only.
        PlanDates.age(r.patient.dob, today)?.let { if (it < 18) add(issue("L1", IssueSeverity.BLOCK, "patient.dob", "Sahara plans are for adults. This date of birth is under 18.", "सहारा की योजना वयस्कों के लिए है। यह जन्मतिथि 18 साल से कम की है।")) }
        if (r.createdInEd) add(issue("ED_START", IssueSeverity.WARN, "createdInEd", "Started in the emergency department. A plan made for the first time in the resuscitation bay records panic, not preference; review it when the patient is stable.", "यह आपातकालीन विभाग में शुरू हुई। पहली बार इमरजेंसी में बनी योजना घबराहट दर्ज करती है, पसंद नहीं; हालत स्थिर होने पर इसे दोबारा देखें।"))
        // Decision-makers: last 4 of an ID only.
        listOfNotNull(r.decisionMakers.primary, r.decisionMakers.alternate).forEach { if (!PlanIds.valid(it.idLast4)) add(issue("ID_LAST4", IssueSeverity.BLOCK, "decisionMakers", "Keep only the last 4 characters of an ID, never the full number.", "पहचान-पत्र के सिर्फ़ आख़िरी 4 अंक रखें, पूरा नंबर कभी नहीं।")) }
        // Capacity (tech spec §5): FLUCTUATES and LACKS need assessor, date and basis. C7: fluctuating capacity needs a reassessment date.
        if (r.capacity.status == Capacity.FLUCTUATES || r.capacity.status == Capacity.LACKS) {
            if (r.capacity.assessor.isBlank() || r.capacity.assessedOn.isBlank() || r.capacity.basis.isBlank())
                add(issue("CAPACITY", IssueSeverity.BLOCK, "capacity", "Record who assessed capacity, when, and the basis (understand, retain, weigh, communicate).", "लिखें कि निर्णय-क्षमता किसने, कब और किस आधार पर जाँची (समझना, याद रखना, तौलना, बताना)।"))
        }
        if (r.capacity.status == Capacity.FLUCTUATES && PlanDates.day(r.capacity.reassessOn) == null)
            add(issue("C7", IssueSeverity.BLOCK, "capacity.reassessOn", "Capacity fluctuates: add a reassessment date.", "निर्णय-क्षमता घटती-बढ़ती है: दोबारा जाँच की तारीख़ जोड़ें।"))
        // C8: family asked that the patient not be told, but the patient has capacity.
        if (r.values.familyAskedNotToTell && (r.capacity.status == Capacity.HAS || r.capacity.status == Capacity.HAS_DELEGATED) && r.values.directQuestionPlan.isBlank())
            add(issue("C8", IssueSeverity.BLOCK, "values.directQuestionPlan", "The family asked that the patient not be told, and the patient has capacity. Record the plan for answering direct questions honestly.", "परिवार ने मरीज़ को न बताने को कहा है, और मरीज़ निर्णय ले सकते हैं। सीधे सवालों का ईमानदारी से जवाब देने की योजना लिखें।"))
        // V4: treatment sections are locked until prognostic understanding is recorded.
        if (c.anyChosen && !r.prognosticUnderstanding.confirmed)
            add(issue("V4", IssueSeverity.BLOCK, "prognosticUnderstanding", "Treatment choices are locked until the illness has been explained as incurable and the family can repeat it back.", "इलाज के फ़ैसले तब तक बंद हैं जब तक यह न समझा दिया जाए कि बीमारी ठीक नहीं होगी और परिवार इसे दोहरा न सके।"))
        // V5: a surrogate confirms the substituted-judgement declaration before answering.
        if (r.surrogateDeclaration.answeringAsSurrogate && !r.surrogateDeclaration.confirmed && c.anyChosen)
            add(issue("V5", IssueSeverity.BLOCK, "surrogateDeclaration", "A decision-maker answering for the patient must first confirm they will say what the patient would have chosen.", "मरीज़ की ओर से जवाब देने वाले को पहले पुष्टि करनी है कि वे वही बताएँगे जो मरीज़ ख़ुद चुनते।"))
        // V2: comfort goal, or a ceiling below intubation, with "attempt CPR" needs a reason.
        if (c.cpr == Cpr.ATTEMPT && (c.goalOfCare == GoalOfCare.C_COMFORT || (ceiling != null && ceiling < 5)) && c.cprConflictReason.isBlank())
            add(issue("V2", IssueSeverity.BLOCK, "choices.cprConflictReason", "CPR cannot be given without intubation. With this goal or breathing ceiling, \"attempt CPR\" needs a recorded reason.", "बिना साँस की नली के CPR नहीं दिया जा सकता। इस लक्ष्य या साँस की सीमा के साथ \"CPR करें\" के लिए कारण लिखना ज़रूरी है।"))
        // V3: goal A with a limit is internally inconsistent; confirm.
        if (c.goalOfCare == GoalOfCare.A_LIFE && ((ceiling != null && ceiling < 5) || c.icu?.kind == IcuKind.NO) && !c.goalAConfirmed)
            add(issue("V3", IssueSeverity.CONFIRM, "choices.goalAConfirmed", "Goal A (prioritise life) with a breathing ceiling below intubation or no ICU is unusual. Confirm this is intended.", "लक्ष्य A (जीवन को प्राथमिकता) के साथ साँस की नली से कम सीमा या ICU नहीं — यह असामान्य है। पुष्टि करें कि यही मतलब है।"))
        // DNACPR needs its reason and decision-maker (clinical spec §8).
        if (c.cpr == Cpr.DNACPR) {
            if (c.dnacprReason == null) add(issue("DNACPR_REASON", IssueSeverity.BLOCK, "choices.dnacprReason", "Choose the reason for DNACPR.", "DNACPR का कारण चुनें।"))
            val by = c.dnacprDecidedBy
            if (by == null || by.name.isBlank() || by.registrationNo.isBlank() || by.decidedAt.isBlank())
                add(issue("DNACPR_DECIDED_BY", IssueSeverity.BLOCK, "choices.dnacprDecidedBy", "Record who decided DNACPR: name, registration number, date and time.", "DNACPR किसने तय किया: नाम, रजिस्ट्रेशन नंबर, तारीख़ और समय लिखें।"))
        }
        if (c.hospitalTransfer?.kind == TransferKind.ONLY_FOR && c.hospitalTransfer.reasons.isEmpty())
            add(issue("TRANSFER_REASONS", IssueSeverity.BLOCK, "choices.hospitalTransfer", "Tick at least one reason for going to hospital.", "अस्पताल जाने का कम से कम एक कारण चुनें।"))
        // C1: no transfer, but ICU or a ceiling of HFNC or above. Home NIV is a valid exception: allow with reason.
        val icuWanted = c.icu?.kind == IcuKind.YES || c.icu?.kind == IcuKind.TRIAL
        val homeNivOk = !icuWanted && c.ceilingConditions.homeNivReason.isNotBlank()
        if (c.hospitalTransfer?.kind == TransferKind.NO && (icuWanted || (ceiling != null && ceiling >= 3)) && !homeNivOk && c.conflictReasons["C1"].isNullOrBlank())
            add(issue("C1", IssueSeverity.CONFIRM, "choices.conflictReasons.C1", "Hospital transfer is \"No\", but HFNC, NIV or ICU usually need admission. Record a reason (for example, home NIV).", "अस्पताल ले जाना \"नहीं\" है, पर HFNC, NIV या ICU के लिए आमतौर पर भर्ती ज़रूरी है। कारण लिखें (जैसे घर पर NIV)।"))
        // C2: comfort goal with ICU, vasopressors or dialysis = Yes.
        if (c.goalOfCare == GoalOfCare.C_COMFORT && (c.icu?.kind == IcuKind.YES || c.vasopressors?.kind == VasopressorKind.YES_NO_LIMIT || c.vasopressors?.kind == VasopressorKind.YES_MAX_AGENTS || c.dialysis?.kind == DialysisKind.YES) && c.conflictReasons["C2"].isNullOrBlank())
            add(issue("C2", IssueSeverity.BLOCK, "choices.conflictReasons.C2", "Goal C (comfort) with ICU, vasopressors or dialysis is a conflict. It can be saved only with a recorded reason.", "लक्ष्य C (आराम) के साथ ICU, BP की दवा या डायलिसिस आपस में टकराते हैं। कारण लिखे बिना सहेजा नहीं जा सकता।"))
        // C3: intubation or vasopressors, but no ICU.
        if ((ceiling == 5 || c.vasopressors?.kind == VasopressorKind.YES_NO_LIMIT || c.vasopressors?.kind == VasopressorKind.YES_MAX_AGENTS) && c.icu?.kind == IcuKind.NO && c.conflictReasons["C3"].isNullOrBlank())
            add(issue("C3", IssueSeverity.CONFIRM, "choices.conflictReasons.C3", "Invasive ventilation and vasopressors need ICU or HDU, but ICU is \"No\". Record a reason.", "साँस की मशीन और BP की दवा के लिए ICU/HDU चाहिए, पर ICU \"नहीं\" है। कारण लिखें।"))
        // C4: every trial needs a duration > 0, a review date/time and reviewers.
        val trials = listOfNotNull(c.icu?.takeIf { it.kind == IcuKind.TRIAL }?.let { "ICU" to it.trial }, c.vasopressors?.takeIf { it.kind == VasopressorKind.TRIAL }?.let { "Vasopressors" to it.trial },
            c.dialysis?.takeIf { it.kind == DialysisKind.TRIAL }?.let { "Dialysis" to it.trial }, c.nivTrial?.let { "NIV" to it })
        trials.filter { it.second?.complete != true }.forEach { (name, _) ->
            add(issue("C4", IssueSeverity.BLOCK, "choices.trial.$name", "$name trial: enter the duration, the review date and time, and who will review.", "$name ट्रायल: अवधि, समीक्षा की तारीख़-समय और समीक्षा करने वालों के नाम लिखें।"))
        }
        if (c.vasopressors?.kind == VasopressorKind.YES_MAX_AGENTS && (c.vasopressors.maxAgents ?: 0) <= 0)
            add(issue("VASO_MAX", IssueSeverity.BLOCK, "choices.vasopressors", "Enter the maximum number of vasopressor agents.", "BP की दवाओं की अधिकतम संख्या लिखें।"))
        if (c.feeding.ng?.kind == NgKind.UP_TO_DAYS && (c.feeding.ng.days ?: 0) <= 0)
            add(issue("NG_DAYS", IssueSeverity.BLOCK, "choices.feeding.ng", "Enter how many days of nasogastric feeding before review.", "समीक्षा से पहले कितने दिन नाक की नली से खाना, लिखें।"))
        // C5: ICD with DNACPR or comfort goal → deactivation decision and date.
        val icd = r.clinical.implantedDevice == ImplantedDevice.ICD || r.clinical.implantedDevice == ImplantedDevice.CRT_D
        if (icd && (c.cpr == Cpr.DNACPR || c.goalOfCare == GoalOfCare.C_COMFORT) && (r.clinical.icdDeactivation.decision == null || r.clinical.icdDeactivation.date.isBlank()))
            add(issue("C5", IssueSeverity.WARN, "clinical.icdDeactivation", "The patient has an ICD. Record the deactivation decision and date, so a dying patient is not shocked.", "मरीज़ में ICD है। इसे बंद करने का फ़ैसला और तारीख़ लिखें, ताकि आख़िरी समय में झटके न लगें।"))
        // C6: removing an existing tube is withdrawal → board pathway; never actionable from the plan alone.
        if (c.feeding.removeExistingTube)
            add(issue("C6", IssueSeverity.WARN, "choices.feeding.removeExistingTube", "Removing an existing feeding tube is withdrawal of treatment. It follows the medical board pathway and cannot be acted on from this plan alone.", "लगी हुई खाने की नली हटाना इलाज वापस लेना है। इसके लिए मेडिकल बोर्ड की प्रक्रिया है; सिर्फ़ इस योजना से ऐसा नहीं किया जा सकता।"))
        // Ceiling creep (clinical spec §8): treatment limits filled while the values section is blank.
        if (c.anyChosen && r.values.ownWords.isBlank() && r.values.patientKnows == null)
            add(issue("VALUES_BLANK", IssueSeverity.WARN, "values", "Treatment choices are filled but the values section is blank. Record what matters to the patient in their own words.", "इलाज के फ़ैसले भरे हैं पर मूल्य वाला हिस्सा ख़ाली है। मरीज़ के लिए क्या ज़रूरी है, उनके अपने शब्दों में लिखें।"))
        // Activation-only checks.
        if (PlanDates.day(r.reviewDue)?.isBefore(today) != false) add(issue("REVIEW_DUE", IssueSeverity.BLOCK, "reviewDue", "Set a review date that is not in the past.", "समीक्षा की ऐसी तारीख़ चुनें जो बीत न चुकी हो।"))
        if (r.consultantSignature == null) add(issue("V7", IssueSeverity.BLOCK, "signatures", "The plan becomes active only when the treating consultant signs with their registration number.", "योजना तभी लागू होगी जब इलाज करने वाले कंसल्टेंट अपने रजिस्ट्रेशन नंबर के साथ हस्ताक्षर करें।"))
        // V8: every printed page and the emergency card need the name and hospital number (the card server refuses a plan without a UHID).
        if (r.patient.name.isBlank() || r.patient.uhid.isBlank()) add(issue("V8", IssueSeverity.BLOCK, "patient", "Every printed page shows the name and hospital number (UHID). Enter both.", "हर छपे पन्ने पर नाम और अस्पताल नंबर (UHID) होता है। दोनों लिखें।"))
        if ((r.limitedLiteracy || r.signatures.any { it.thumbImpression == true }) && r.witnesses.size < 2)
            add(issue("V6", IssueSeverity.BLOCK, "signatures", "A thumb impression or limited literacy needs two witnesses.", "अँगूठे के निशान या कम पढ़े-लिखे होने पर दो गवाह ज़रूरी हैं।"))
        // W2, legal review: primary and secondary decision-makers both sign.
        if (r.signatures.any { it.role == SignatureRole.SURROGATE } && r.decisionMakers.alternate?.name?.isNotBlank() == true && r.signatures.none { it.surrogateRank == SurrogateRank.SECONDARY })
            add(issue("W2", IssueSeverity.WARN, "signatures", "A secondary decision-maker is named. They should sign too.", "द्वितीय फ़ैसला लेने वाले का नाम दर्ज है। उन्हें भी साइन करना चाहिए।"))
    }

    private fun stops(i: RuleIssue) = i.severity != IssueSeverity.WARN
    /** What stops a draft being saved. "Goal C + attempt CPR cannot save without a reason." A CONFIRM (C1, C3, V3) waits until signing, as on the web. */
    fun saveBlockers(r: EctprRecord, today: LocalDate) = check(r, today).filter { it.severity == IssueSeverity.BLOCK && it.id !in ACTIVATION_ONLY }
    /** What stops a draft becoming the Active plan (V7 and the rest). */
    fun activationBlockers(r: EctprRecord, today: LocalDate) = check(r, today).filter(::stops)
    fun warnings(r: EctprRecord, today: LocalDate) = check(r, today).filter { it.severity == IssueSeverity.WARN }

    /** V8: every printed page carries name, UHID, version, form version, print date and page x of y. */
    fun pageFooter(r: EctprRecord, printDate: LocalDate, page: Int, pages: Int): String =
        "${r.patient.name.ifBlank { "Name not recorded" }} · UHID ${r.patient.uhid.ifBlank { "not recorded" }} · plan v${r.version} · ${r.formVersion} · printed $printDate · page $page of $pages"
}

/** Sahaara v1.0 §1 "If in doubt, resuscitate". Hard-coded on every ED view and print.
 *  Each condition starts with "If" (Sujay, WhatsApp 1 Nov), then the action. Same wording as prototype/app/ectpr-rules.ts. */
object IfInDoubt {
    val TITLE = Bi("IF IN DOUBT, RESUSCITATE", "संदेह हो तो जान बचाने का पूरा इलाज करें")
    /** No longer shown: the box now starts with the "If" conditions. Kept empty so older callers still compile. */
    val LEAD = Bi("", "")
    val CONDITIONS = listOf(
        Bi("If the record is unsigned, out of date or cannot be found.", "अगर रिकॉर्ड पर हस्ताक्षर नहीं है, वह पुराना हो गया है या मिल नहीं रहा।"),
        Bi("If the problem is new, reversible and unrelated to the illness (injury, choking, drug reaction, low sugar).", "अगर समस्या नई है, ठीक हो सकती है और बीमारी से जुड़ी नहीं है (चोट, गले में अटकना, दवा का रिएक्शन, शुगर कम होना)।"),
        Bi("If the patient has capacity and says something different now. What they say now always overrides this record.", "अगर मरीज़ में फ़ैसला लेने की क्षमता है और वे अब कुछ और कह रहे हैं। उनकी अभी की बात हमेशा इस रिकॉर्ड से ऊपर है।"),
        Bi("If the record is ambiguous.", "अगर रिकॉर्ड साफ़ नहीं है।"),
    )
    val AFTER = Bi("Then resuscitate and give full standard emergency care. Clarify with the treating team afterwards.", "तब पुनर्जीवन (CPR) दें और पूरा सामान्य आपातकालीन इलाज करें। बाद में इलाज करने वाली टीम से बात करें।")
}

/** V9: Sahaara v1.0 §16 "What will always be provided, whatever else is limited", printed on every ECTPR, so the record is never read as "DNR = no care". */
object CareThatContinues {
    val TITLE = Bi("Care that always continues", "देखभाल जो हमेशा जारी रहती है")
    /** Exactly ALWAYS_PROVIDED_CARE in prototype/app/ectpr-rules.ts (the §16 wording), with the Hindi of ectpr-labels.ts. */
    val ITEMS = listOf(
        Bi("Relief of pain, using opioids in adequate doses, by whatever route works", "दर्द से राहत — ओपिऑइड पूरी खुराक में, जिस भी रास्ते से असर करे"),
        Bi("Relief of breathlessness — opioid, anxiolytic, fan, positioning, oxygen if it helps", "साँस फूलने से राहत — ओपिऑइड, घबराहट की दवा, पंखा, सही स्थिति, ऑक्सीजन अगर मदद करे"),
        Bi("Sedation for distress that cannot be relieved any other way", "ऐसी तकलीफ़ के लिए नींद की दवा (सेडेशन) जो किसी और तरह कम न हो"),
        Bi("Control of nausea, secretions, agitation, itch, hiccups", "जी मिचलाना, बलगम या स्राव, बेचैनी, खुजली और हिचकी पर नियंत्रण"),
        Bi("Mouth care and eye care, given often", "मुँह और आँखों की देखभाल, बार-बार"),
        Bi("Comfortable positioning; turning as tolerated; air mattress to prevent pressure sores", "आरामदायक स्थिति; जितना सह सकें करवट बदलना; बेडसोर से बचाव के लिए एयर गद्दा"),
        Bi("Skin and wound care; management of odour", "त्वचा और घाव की देखभाल; बदबू पर नियंत्रण"),
        Bi("Bowel care — assessment and management of constipation", "पेट की देखभाल — कब्ज़ की जाँच और इलाज"),
        Bi("Bladder care — assessment and management of retention and incontinence", "मूत्राशय की देखभाल — पेशाब रुकने और अपने-आप निकल जाने की जाँच और इलाज"),
        Bi("Privacy, dignity, and gentle handling in every personal-care task", "हर निजी देखभाल में निजता, गरिमा और कोमल व्यवहार"),
        Bi("Presence of family; spiritual and religious support as the patient wishes", "परिवार का साथ; मरीज़ की इच्छा के अनुसार आध्यात्मिक और धार्मिक सहारा"),
        Bi("Honest, unhurried communication with the patient and the family, every day", "मरीज़ और परिवार से हर दिन ईमानदार, बिना जल्दबाज़ी की बातचीत"),
    )
    val NOTE = Bi("A limit on treatment never means no care.", "इलाज पर सीमा का मतलब कभी 'देखभाल नहीं' नहीं होता।")
}

val NO_PREFERENCE = Bi("No preference recorded — standard care", "कोई पसंद दर्ज नहीं — सामान्य इलाज")
val NOT_RECORDED = Bi("Not recorded", "दर्ज नहीं")

/** Plain-language labels and meanings for every stored choice (clinical spec §3). */
object PlanText {
    fun goal(g: GoalOfCare) = when (g) {
        GoalOfCare.A_LIFE -> Bi("A · Prioritise life", "A · जीवन को प्राथमिकता")
        GoalOfCare.B_CEILING -> Bi("B · Balanced, within limits", "B · संतुलित, तय सीमा में")
        GoalOfCare.C_COMFORT -> Bi("C · Prioritise comfort", "C · आराम को प्राथमिकता")
    }
    fun goalMeaning(g: GoalOfCare) = when (g) {
        GoalOfCare.A_LIFE -> Bi("All medically effective treatment, including ICU and a breathing machine.", "हर असरदार इलाज, ICU और साँस की मशीन समेत।")
        GoalOfCare.B_CEILING -> Bi("Treat problems that can be reversed, within the limits below, as time-limited trials.", "जो ठीक हो सकता है उसका इलाज, नीचे दी सीमाओं में, तय समय के ट्रायल की तरह।")
        GoalOfCare.C_COMFORT -> Bi("Comfort is the goal. Go to hospital only if comfort cannot be achieved where the patient is.", "आराम ही लक्ष्य है। अस्पताल तभी, जब जहाँ मरीज़ हैं वहाँ आराम न मिल सके।")
    }
    fun cpr(c: Cpr) = when (c) { Cpr.ATTEMPT -> Bi("Attempt CPR", "CPR करें"); Cpr.DNACPR -> Bi("Do not attempt CPR (DNACPR)", "CPR न करें (DNACPR)") }
    fun cprMeaning(c: Cpr) = when (c) {
        Cpr.ATTEMPT -> Bi("If the heart and breathing stop, try to restart them. Applies only to cardiac arrest.", "दिल और साँस रुकने पर उन्हें फिर शुरू करने की कोशिश। सिर्फ़ दिल रुकने पर लागू।")
        Cpr.DNACPR -> Bi("If the heart stops, allow a natural death. It says nothing about any other treatment.", "दिल रुकने पर स्वाभाविक मृत्यु होने दें। बाकी किसी इलाज के बारे में यह कुछ नहीं कहता।")
    }
    fun dnacprReason(r: DnacprReason) = when (r) {
        DnacprReason.FUTILITY -> Bi("CPR would not work (clinical futility)", "CPR काम नहीं करेगा (चिकित्सकीय रूप से व्यर्थ)")
        DnacprReason.PATIENT_REFUSAL -> Bi("The patient does not want CPR", "मरीज़ CPR नहीं चाहते")
        DnacprReason.BURDEN_OUTWEIGHS_BENEFIT -> Bi("The burden outweighs the benefit", "फ़ायदे से ज़्यादा तकलीफ़")
    }
    fun transfer(k: TransferKind) = when (k) {
        TransferKind.ANY -> Bi("Yes, for any serious worsening", "हाँ, हालत बिगड़ने पर कभी भी")
        TransferKind.ONLY_FOR -> Bi("Only for the reasons ticked", "सिर्फ़ चुने हुए कारणों से")
        TransferKind.NO -> Bi("No — care where they are", "नहीं — जहाँ हैं वहीं देखभाल")
    }
    fun transferMeaning(k: TransferKind) = when (k) {
        TransferKind.ANY -> Bi("Take to hospital for any significant deterioration.", "हालत ज़्यादा बिगड़ने पर अस्पताल ले जाएँ।")
        TransferKind.ONLY_FOR -> Bi("Go to hospital only for the problems ticked below.", "सिर्फ़ नीचे चुनी समस्याओं के लिए अस्पताल जाएँ।")
        TransferKind.NO -> Bi("Care continues at home or where they are. Call the numbers listed before an ambulance.", "देखभाल घर पर या जहाँ हैं वहीं। एम्बुलेंस से पहले दिए गए नंबर पर फ़ोन करें।")
    }
    fun transferReason(r: TransferReason) = when (r) {
        TransferReason.PAIN -> Bi("Uncontrolled pain", "दर्द जो काबू में न आए")
        TransferReason.BREATHLESSNESS -> Bi("Uncontrolled breathlessness", "साँस फूलना जो काबू में न आए")
        TransferReason.BLEEDING -> Bi("Significant bleeding", "ज़्यादा ख़ून बहना")
        TransferReason.SEIZURES -> Bi("Seizures", "दौरे")
        TransferReason.FALL_FRACTURE -> Bi("Fall with suspected fracture", "गिरना और हड्डी टूटने का शक")
        TransferReason.BLOCKED_TUBE -> Bi("Blocked or displaced catheter, tube or stent", "कैथेटर, नली या स्टेंट बंद या खिसक जाना")
        TransferReason.INFECTION_IV -> Bi("Suspected infection needing IV treatment", "संक्रमण का शक जिसमें नस से दवा चाहिए")
        TransferReason.CONFUSION -> Bi("Acute confusion or agitation not settling", "अचानक उलझन या बेचैनी जो शांत न हो")
        TransferReason.CAREGIVER_RESPITE -> Bi("Caregiver unable to cope (respite)", "देखभाल करने वाले थक गए हों (आराम के लिए)")
    }
    fun ceiling(level: Int) = when (level) {
        0 -> Bi("0 · Comfort measures only", "0 · सिर्फ़ आराम के उपाय")
        1 -> Bi("1 · Nasal prongs or simple mask", "1 · नाक की नली या साधारण मास्क")
        2 -> Bi("2 · Non-rebreather or venturi mask", "2 · नॉन-रीब्रीदर या वेंचुरी मास्क")
        3 -> Bi("3 · High-flow nasal oxygen (HFNC)", "3 · हाई-फ़्लो नाक ऑक्सीजन (HFNC)")
        4 -> Bi("4 · Mask ventilation (NIV: CPAP/BiPAP)", "4 · मास्क से साँस की मशीन (NIV: CPAP/BiPAP)")
        else -> Bi("5 · Intubation and a breathing machine", "5 · गले में नली और साँस की मशीन")
    }
    fun ceilingMeaning(level: Int) = when (level) {
        0 -> Bi("Morphine-type medicine, anxiety relief, a fan, positioning, suction; oxygen only if it relieves.", "मॉर्फ़ीन जैसी दवा, घबराहट की दवा, पंखा, करवट, बलगम निकालना; ऑक्सीजन तभी जब आराम दे।")
        5 -> Bi("The highest level: everything below is also allowed.", "सबसे ऊँचा स्तर: इससे नीचे का सब भी मान्य है।")
        else -> Bi("The highest level allowed. Every lower level stays available; nothing higher starts without a new discussion.", "यह सबसे ऊँचा मान्य स्तर है। नीचे के सभी स्तर मिलते रहेंगे; इससे ऊपर नई बातचीत के बिना कुछ शुरू नहीं होगा।")
    }
    fun icu(k: IcuKind) = when (k) { IcuKind.YES -> Bi("Yes, ICU if needed", "हाँ, ज़रूरत हो तो ICU"); IcuKind.TRIAL -> Bi("Time-limited ICU trial", "तय समय का ICU ट्रायल"); IcuKind.NO -> Bi("No ICU — ward or comfort care", "ICU नहीं — वार्ड या आराम की देखभाल") }
    fun vaso(k: VasopressorKind) = when (k) {
        VasopressorKind.NO -> Bi("No blood-pressure support drugs", "BP बढ़ाने की दवा नहीं"); VasopressorKind.TRIAL -> Bi("Time-limited trial", "तय समय का ट्रायल")
        VasopressorKind.YES_NO_LIMIT -> Bi("Yes, no limit", "हाँ, बिना सीमा"); VasopressorKind.YES_MAX_AGENTS -> Bi("Yes, up to a number of drugs", "हाँ, तय संख्या तक दवाएँ")
    }
    fun dialysis(k: DialysisKind) = when (k) {
        DialysisKind.NO -> Bi("No dialysis", "डायलिसिस नहीं"); DialysisKind.TRIAL -> Bi("Time-limited trial", "तय समय का ट्रायल")
        DialysisKind.YES -> Bi("Yes, dialysis", "हाँ, डायलिसिस"); DialysisKind.ONLY_IF_REVERSIBLE -> Bi("Only if kidney failure can be reversed", "सिर्फ़ तब, जब किडनी ठीक हो सकती हो")
    }
    fun ng(k: NgKind) = when (k) { NgKind.NO -> Bi("No nose tube (NG)", "नाक की नली (NG) नहीं"); NgKind.UP_TO_DAYS -> Bi("NG for a set number of days, then review", "कुछ तय दिन NG, फिर समीक्षा"); NgKind.YES -> Bi("Yes, NG feeding", "हाँ, NG से खाना") }
    fun peg(p: PegFeeding) = when (p) { PegFeeding.NO -> Bi("No stomach tube (PEG)", "पेट की नली (PEG) नहीं"); PegFeeding.ONLY_AFTER_PALLIATIVE_DISCUSSION -> Bi("PEG only after a palliative-care discussion", "पैलिएटिव केयर से बात के बाद ही PEG"); PegFeeding.YES -> Bi("Yes, PEG", "हाँ, PEG") }
    fun hydration(h: Hydration) = when (h) { Hydration.ORAL_SIPS -> Bi("Sips by mouth only", "सिर्फ़ मुँह से घूँट"); Hydration.LIMITED -> Bi("Limited drip fluids", "सीमित ड्रिप"); Hydration.FULL -> Bi("Full maintenance fluids", "पूरी ड्रिप") }
    fun hydrationMeaning(h: Hydration) = when (h) {
        Hydration.ORAL_SIPS -> Bi("Mouth care and sips for comfort; no drip.", "आराम के लिए मुँह की सफ़ाई और घूँट; ड्रिप नहीं।")
        Hydration.LIMITED -> Bi("Up to 500–1000 mL saline a day, by vein or under the skin, matched to urine, reviewed daily. Reduce or stop if swelling, wet breathing or worse breathlessness.", "दिन में 500–1000 mL तक सलाइन, नस या त्वचा के नीचे से, पेशाब के हिसाब से, रोज़ समीक्षा। सूजन, गीली साँस या ज़्यादा साँस फूलने पर कम या बंद करें।")
        Hydration.FULL -> Bi("Full daily fluid needs by drip.", "दिन भर की पूरी ज़रूरत ड्रिप से।")
    }
    fun ambulance(a: AmbulanceOption) = when (a) {
        AmbulanceOption.FULL_RESUSCITATION -> Bi("Full pre-hospital resuscitation and transport", "रास्ते में पूरा इलाज और अस्पताल ले जाना")
        AmbulanceOption.SYMPTOM_RELIEF_TRANSPORT -> Bi("Transport with symptom relief only (no CPR, no intubation)", "सिर्फ़ तकलीफ़ में राहत के साथ ले जाना (CPR नहीं, नली नहीं)")
        AmbulanceOption.TRANSPORT_HOME_NURSE_ESCORT -> Bi("Take home from hospital for the last days, with a nurse if available", "आख़िरी दिनों के लिए अस्पताल से घर ले जाना, नर्स मिले तो उसके साथ")
    }
    fun capacity(c: Capacity) = when (c) {
        Capacity.HAS -> Bi("Has capacity", "निर्णय ले सकते हैं"); Capacity.HAS_DELEGATED -> Bi("Has capacity, has asked family to decide", "निर्णय ले सकते हैं, परिवार को सौंपा है")
        Capacity.FLUCTUATES -> Bi("Capacity fluctuates — reassess", "निर्णय-क्षमता घटती-बढ़ती है — दोबारा जाँचें"); Capacity.LACKS -> Bi("Lacks capacity", "निर्णय नहीं ले सकते")
    }
    fun authority(a: AuthorityBasis) = when (a) {
        AuthorityBasis.NAMED_IN_AMD -> Bi("Named in signed advance care planning", "हस्ताक्षरित अग्रिम देखभाल योजना में नामित")
        AuthorityBasis.NAMED_BY_PATIENT -> Bi("Named by the patient (no advance care planning)", "मरीज़ ने नाम दिया (अग्रिम देखभाल योजना नहीं)")
        AuthorityBasis.NEXT_OF_KIN -> Bi("Next of kin / next friend / guardian", "निकट संबंधी / मित्र / अभिभावक")
        AuthorityBasis.COURT_APPOINTED -> Bi("Court-appointed guardian", "अदालत द्वारा नियुक्त अभिभावक")
    }
    fun idType(i: IdType) = when (i) {
        IdType.AADHAAR -> Bi("Aadhaar", "आधार"); IdType.VOTER_ID -> Bi("Voter ID", "वोटर आईडी"); IdType.PAN -> Bi("PAN", "पैन")
        IdType.PASSPORT -> Bi("Passport", "पासपोर्ट"); IdType.DRIVING_LICENCE -> Bi("Driving licence", "ड्राइविंग लाइसेंस"); IdType.RATION_CARD -> Bi("Ration card", "राशन कार्ड"); IdType.OTHER -> Bi("Other", "अन्य")
    }
    fun device(d: ImplantedDevice) = when (d) {
        ImplantedDevice.NONE -> Bi("None", "कोई नहीं"); ImplantedDevice.PACEMAKER -> Bi("Pacemaker", "पेसमेकर"); ImplantedDevice.ICD -> Bi("ICD (shock device)", "ICD (झटका देने वाला यंत्र)")
        ImplantedDevice.CRT_D -> Bi("CRT-D (shock device)", "CRT-D (झटका देने वाला यंत्र)"); ImplantedDevice.OTHER -> Bi("Other device", "अन्य यंत्र")
    }
    fun status(s: EctprStatus) = when (s) { EctprStatus.DRAFT -> Bi("Draft — not signed", "मसौदा — हस्ताक्षर नहीं"); EctprStatus.ACTIVE -> Bi("Active", "लागू"); EctprStatus.SUPERSEDED -> Bi("Replaced by a newer version", "नए संस्करण से बदली गई"); EctprStatus.REVOKED -> Bi("Withdrawn", "वापस ली गई") }
    fun unit(u: DurationUnit) = when (u) { DurationUnit.HOURS -> Bi("hours", "घंटे"); DurationUnit.DAYS -> Bi("days", "दिन"); DurationUnit.SESSIONS -> Bi("sessions", "सत्र") }
    /** " · 3 days, review 2026-11-01 10:00 by Dr Iyer" in the chosen language. */
    fun trial(t: TrialPlan?): Bi {
        if (t == null) return Bi("", "", "")
        val at = t.reviewAt; val who = t.reviewers.filter(String::isNotBlank).joinToString(", ")
        val review = Bi("review $at by $who", "समीक्षा $at, $who")
        return Bi.join("", listOf(Bi.plain(" · ${t.durationValue} "), unit(t.durationUnit), Bi.plain(", "), review))
    }
    fun knowsRecordedWhen(k: KnowsRecordedWhen) = when (k) { KnowsRecordedWhen.BEFORE_COUNSELLING -> Bi("Before counselling", "काउंसलिंग से पहले"); KnowsRecordedWhen.AFTER_COUNSELLING -> Bi("After counselling", "काउंसलिंग के बाद") }
    private fun filled(label: Bi, value: String): Bi? = value.takeIf { it.isNotBlank() }?.let { Bi.labeled(label, Bi.plain(it)) }
    fun homeCareLines(r: EctprRecord): List<Bi> {
        val h = r.homeCare ?: return emptyList()
        val ngo = h.ngoContact.takeIf { it.isNotBlank() }?.let { contact ->
            val value = if (h.ngoConsulted) Bi.join(" · ", listOf(Bi.plain(contact), Bi("The organisation was consulted about this plan", "इस योजना पर संस्था से सलाह ली गई"))) else Bi.plain(contact)
            Bi.labeled(Bi("Non-governmental organisation caring for the patient: representative and contact", "मरीज़ की देखभाल करने वाली ग़ैर-सरकारी संस्था: प्रतिनिधि और संपर्क"), value)
        }
        val admitted = Bi("Admitted before for this condition", "इस बीमारी के लिए पहले भर्ती")
        return listOfNotNull(
            filled(Bi("How often the patient is assessed", "मरीज़ की जाँच कितनी बार होती है"), h.assessmentFrequency),
            filled(Bi("Person coordinating between the family and the hospital or centre (name, phone, centre)", "परिवार और अस्पताल या केंद्र के बीच तालमेल रखने वाले (नाम, फ़ोन, केंद्र)"), h.coordinator),
            ngo,
            if (h.previousAdmission == true) filled(admitted, h.previousAdmissionDetails) ?: admitted else null,
        )
    }
    /** Full record only, never the card's quick view: gender, who was at the discussion, who could not be reached, and when the patient's understanding was recorded. */
    fun fullRecordLines(r: EctprRecord): List<Bi> = listOfNotNull(
        filled(Bi("Gender", "जेंडर"), r.patient.gender),
        filled(Bi("Present for the discussion", "बातचीत में मौजूद"), r.decisionMakers.presentAtDiscussion.filter(String::isNotBlank).joinToString(", ")),
        filled(Bi("Could not be reached", "संपर्क नहीं हो सका"), r.decisionMakers.attemptedContact.filter(String::isNotBlank).joinToString(", ")),
        r.values.knowsRecordedWhen?.let { Bi.join(" ", listOf(Bi("When was this recorded?", "यह कब दर्ज किया गया?"), knowsRecordedWhen(it))) },
    )
    fun trigger(t: ReviewTrigger) = when (t) {
        ReviewTrigger.NEW_ADMISSION -> Bi("New admission", "नई भर्ती"); ReviewTrigger.TRANSFER_BETWEEN_SETTINGS -> Bi("Moved between home, hospital, hospice or care home", "घर, अस्पताल, हॉस्पिस या केयर होम के बीच बदलाव")
        ReviewTrigger.CONDITION_CHANGE -> Bi("Significant change in condition (either way)", "हालत में बड़ा बदलाव (बेहतर या बदतर)"); ReviewTrigger.PATIENT_OR_FAMILY_REQUEST -> Bi("Patient or family asked for a review", "मरीज़ या परिवार ने समीक्षा माँगी")
        ReviewTrigger.NEW_TREATMENT_LINE -> Bi("A new line of treatment started", "इलाज का नया दौर शुरू हुआ"); ReviewTrigger.ROUTINE_INTERVAL -> Bi("Routine review date reached", "नियमित समीक्षा की तारीख़ आ गई")
    }
}

/** Draft annexure from the legal review, shown on the capacity step and printed in the PDF. */
object CapacityGuide {
    val TITLE = Bi("How to assess decision-making capacity", "निर्णय लेने की क्षमता कैसे जाँचें")
    val NOTE = Bi("Draft annexure. A clinician and the legal team must approve this wording before the pilot.", "मसौदा अनुलग्नक। पायलट से पहले डॉक्टर और क़ानूनी टीम को यह भाषा मंज़ूर करनी होगी।")
    val POINTS = listOf(
        Bi("Start by assuming the patient has capacity. Age, diagnosis or disagreeing with the doctor does not remove it.", "पहले मानें कि मरीज़ में क्षमता है। उम्र, बीमारी या डॉक्टर से असहमति से यह ख़त्म नहीं होती।"),
        Bi("Assess this one decision, at this time. Do not rely on old notes.", "इसी एक फ़ैसले के लिए, अभी जाँचें। पुराने नोट पर भरोसा न करें।"),
        Bi("Help them first: use their language, plain words and any aids, at the time of day they are most alert.", "पहले मदद करें: उनकी भाषा, सरल शब्द और कोई भी सहायक साधन इस्तेमाल करें, उस समय जब वे सबसे ज़्यादा सतर्क हों।"),
        Bi("Check all four: can they understand the information, retain it long enough, weigh the choices and their results, and communicate a choice in any way?", "चारों जाँचें: क्या वे जानकारी समझ सकते हैं, उसे काफ़ी देर याद रख सकते हैं, विकल्पों और नतीजों को तौल सकते हैं, और किसी भी तरह अपना फ़ैसला बता सकते हैं?"),
        Bi("If any one is missing, they lack capacity for this decision only. Record the basis and who assessed it, and still involve them as far as possible.", "अगर एक भी न हो, तो सिर्फ़ इस फ़ैसले की क्षमता नहीं है। आधार और जाँचने वाले का नाम लिखें, और फिर भी उन्हें जितना हो सके शामिल करें।"),
        Bi("If it may be temporary (delirium, medicines, pain, infection), treat the cause and check again before acting.", "अगर यह अस्थायी हो सकती है (डेलिरियम, दवाएँ, दर्द, संक्रमण), तो कारण का इलाज करें और कुछ करने से पहले दोबारा जाँचें।"),
    )
}

/** §18.4 mandatory review triggers. Each opens a prefilled draft and resets the review-due date. */
enum class ReviewTrigger { NEW_ADMISSION, TRANSFER_BETWEEN_SETTINGS, CONDITION_CHANGE, PATIENT_OR_FAMILY_REQUEST, NEW_TREATMENT_LINE, ROUTINE_INTERVAL }
data class ReviewRequest(val id: String, val ectprId: String, val trigger: ReviewTrigger, val at: String, val by: String, val note: String = "")

object ReviewRules {
    /** [Sign-off] routine review interval in weeks (clinical spec §6/§9). */
    const val ROUTINE_REVIEW_WEEKS = 12L
    fun defaultReviewDue(from: LocalDate): String = from.plusWeeks(ROUTINE_REVIEW_WEEKS).toString()
    /** Out of date once the review date has passed; a plan is still in date on the day itself. No readable date counts as out of date. */
    fun due(r: EctprRecord, today: LocalDate): Boolean = PlanDates.day(r.reviewDue)?.isBefore(today) ?: true
}

/** One-active-version lifecycle: Draft → Active → Superseded / Revoked. A signed plan is never edited; a review makes a new version. */
object EctprLifecycle {
    fun newId(): String = CardTokens.random(8)

    fun newDraft(patientId: String, createdBy: String, now: String, patient: EctprPatient = EctprPatient(), createdInEd: Boolean = false, id: String = newId()) =
        EctprRecord(id = id, patientId = patientId, createdAt = now, createdBy = createdBy, createdInEd = createdInEd, patient = patient)

    /** A review: a prefilled draft of [from]. Signatures go; understanding and V3 must be confirmed again; review-due resets. */
    fun reviewDraft(records: List<EctprRecord>, from: EctprRecord, createdBy: String, now: String, today: LocalDate, id: String = newId()): EctprRecord {
        require(records.none { it.patientId == from.patientId && it.status == EctprStatus.DRAFT }) { "Finish or discard the open draft first." }
        return from.copy(id = id, version = (records.filter { it.patientId == from.patientId }.maxOfOrNull { it.version } ?: from.version) + 1, status = EctprStatus.DRAFT,
            createdAt = now, updatedAt = now, createdBy = createdBy, createdInEd = false, signatures = emptyList(), revokedAt = null, revokedReason = null, supersedes = from.id, gocTranscript = from.gocTranscript?.copy(lines = emptyList(), audioKept = false),
            prognosticUnderstanding = from.prognosticUnderstanding.copy(confirmed = false), surrogateDeclaration = from.surrogateDeclaration.copy(confirmed = false),
            choices = from.choices.copy(goalAConfirmed = false), reviewDue = ReviewRules.defaultReviewDue(today))
    }

    /** Only drafts change. */
    fun edit(r: EctprRecord, now: String, change: (EctprRecord) -> EctprRecord): EctprRecord {
        check(r.status == EctprStatus.DRAFT) { "A signed plan is never edited. Start a review to make a new version." }
        val next = change(r)
        check(next.id == r.id && next.status == EctprStatus.DRAFT && next.version == r.version) { "Only the draft's content can change." }
        return next.copy(updatedAt = now)
    }

    /** V7 + one Active version per patient: signing makes this draft Active and the previous Active Superseded. */
    fun activate(records: List<EctprRecord>, id: String, now: String, today: LocalDate): List<EctprRecord> {
        val draft = records.firstOrNull { it.id == id } ?: error("Plan not found.")
        check(draft.status == EctprStatus.DRAFT) { "Only a draft can be signed." }
        val blockers = EctprRules.activationBlockers(draft, today)
        check(blockers.isEmpty()) { blockers.joinToString(" ") { it.message } }
        return records.map {
            when {
                it.id == id -> it.copy(status = EctprStatus.ACTIVE, updatedAt = now)
                it.patientId == draft.patientId && it.status == EctprStatus.ACTIVE -> it.copy(status = EctprStatus.SUPERSEDED, updatedAt = now)
                else -> it
            }
        }.also { check(it.count { r -> r.patientId == draft.patientId && r.status == EctprStatus.ACTIVE } == 1) }
    }

    /** A signed cancellation withdraws the active plan; its card tokens are revoked by [CardTokens.sync]. */
    fun revoke(records: List<EctprRecord>, id: String, reason: String, by: String, now: String): List<EctprRecord> {
        require(reason.isNotBlank()) { "Give the reason for withdrawing the plan." }
        require(by.isNotBlank()) { "Record who signed the cancellation." }
        val r = records.firstOrNull { it.id == id } ?: error("Plan not found.")
        check(r.status == EctprStatus.ACTIVE) { "Only the active plan can be withdrawn." }
        return records.map { if (it.id == id) it.copy(status = EctprStatus.REVOKED, revokedAt = now, revokedReason = "$reason (signed by $by)", updatedAt = now) else it }
    }

    fun discardDraft(records: List<EctprRecord>, id: String): List<EctprRecord> {
        check(records.firstOrNull { it.id == id }?.status == EctprStatus.DRAFT) { "Only an unsigned draft can be discarded." }
        return records.filterNot { it.id == id }
    }

    fun active(records: List<EctprRecord>, patientId: String): EctprRecord? = records.singleOrNull { it.patientId == patientId && it.status == EctprStatus.ACTIVE }
    fun draft(records: List<EctprRecord>, patientId: String): EctprRecord? = records.filter { it.patientId == patientId && it.status == EctprStatus.DRAFT }.maxByOrNull { it.version }
}

/** Where a card stands on the server, same names as the web: issued, local-only (not reached yet), revoked, revoke-pending (withdrawn here, server not told yet). */
enum class CardServerState { ISSUED, LOCAL_ONLY, REVOKED, REVOKE_PENDING }

/**
 * Random, revocable 256-bit QR token with no personal data (tech spec §7). [manageKey] proves this phone issued the card and goes only to
 * /api/ed-card. [snapshot] is the JSON the server stores, frozen when the card was made so a retry sends exactly the same thing.
 */
data class CardToken(val token: String, val ectprId: String, val patientId: String, val createdAt: String, val revokedAt: String? = null,
    val manageKey: String = "", val version: Int = 1, val snapshot: String = "", val server: CardServerState = CardServerState.LOCAL_ONLY, val serverMessage: String? = null, val revokedReason: String? = null) {
    val url: String get() = CardTokens.url(token)
    val live: Boolean get() = revokedAt == null
    /** Still needs the server: an upload for a live card, or telling it about a withdrawal. */
    val pending: Boolean get() = if (live) server != CardServerState.ISSUED else server != CardServerState.REVOKED
}

object CardTokens {
    const val BASE = "https://sahara.wedevit.in/ed/"
    /** ED_TOKEN_PATTERN and MANAGE_KEY_PATTERN in prototype/app/ed-card-snapshot.ts. */
    val PATTERN = Regex("^[A-Za-z0-9_-]{22,64}$")
    private val random = SecureRandom()
    fun random(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    /** randomUrlToken(32): 32 random bytes as base64url = 43 characters. */
    fun newToken(): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    fun url(token: String): String { require(PATTERN.matches(token)) { "Not a card token." }; return BASE + token }

    /** One live token for the Active plan; every token of a superseded, revoked or missing plan is withdrawn and queued to tell the server. */
    fun sync(tokens: List<CardToken>, records: List<EctprRecord>, patientId: String, now: String, amd: AmdStatusSummary? = null, newToken: () -> String = ::newToken): List<CardToken> {
        val active = EctprLifecycle.active(records, patientId)
        val kept = tokens.map { t ->
            if (t.patientId != patientId || !t.live || t.ectprId == active?.id) t
            else t.copy(revokedAt = now, revokedReason = if (records.firstOrNull { it.id == t.ectprId }?.status == EctprStatus.REVOKED) "Plan cancelled" else "Plan superseded by a newer version", server = CardServerState.REVOKE_PENDING, serverMessage = null)
        }
        return if (active != null && kept.none { it.ectprId == active.id && it.live }) kept + issue(active, now, amd, newToken) else kept
    }
    fun issue(active: EctprRecord, now: String, amd: AmdStatusSummary?, newToken: () -> String = ::newToken) =
        CardToken(newToken(), active.id, active.patientId, now, manageKey = newToken(), version = active.version, snapshot = EdCardSnapshot.json(active, amd).toString())
    /** A new card for the same plan, for example after the living will changed: the old link stops working. */
    fun reissue(tokens: List<CardToken>, active: EctprRecord, now: String, amd: AmdStatusSummary?): List<CardToken> =
        tokens.map { if (it.patientId == active.patientId && it.live) it.copy(revokedAt = now, revokedReason = "Replaced by a new card for version ${active.version}", server = CardServerState.REVOKE_PENDING, serverMessage = null) else it } + issue(active, now, amd)
    fun live(tokens: List<CardToken>, ectprId: String): CardToken? = tokens.lastOrNull { it.ectprId == ectprId && it.live }
}

enum class EdKind { NONE, UNSIGNED, OUT_OF_DATE, REVOKED, ACTIVE }
data class EdInterpretation(val kind: EdKind, val record: EctprRecord?, val standardCareApplies: Boolean, val showContent: Boolean, val banner: Bi, val flags: List<Bi> = emptyList())
data class EdRow(val key: String, val label: Bi, val value: Bi, val flag: Boolean = false)

/** ED Quick View (clinical spec §6). The "If in doubt" box and "Care that always continues" are always shown by the screen and the print. */
object EdView {
    /** [Sign-off] clinical spec §6: show the content under the out-of-date banner (as information, not instruction). */
    const val SHOW_CONTENT_WHEN_OUT_OF_DATE = true

    val ORDER = listOf("TRANSFER", "CPR", "RESP_CEILING", "ICU", "GOAL", "ALLERGIES", "DEVICE", "DECISION_MAKER", "TEAM", "CAPACITY", "AMD", "VERSION")


    fun interpret(r: EctprRecord?, today: LocalDate): EdInterpretation = state(r, today)

    private fun state(r: EctprRecord?, today: LocalDate): EdInterpretation = when {
        r == null -> EdInterpretation(EdKind.NONE, null, true, false, Bi("NO PLAN FOUND — standard care applies", "कोई योजना नहीं मिली — सामान्य इलाज लागू"))
        r.status == EctprStatus.REVOKED || r.status == EctprStatus.SUPERSEDED -> EdInterpretation(EdKind.REVOKED, r, true, false, Bi("CARD WITHDRAWN — this plan is no longer valid. Standard care applies.", "कार्ड वापस लिया गया — यह योजना अब मान्य नहीं। सामान्य इलाज लागू।"))
        r.status == EctprStatus.DRAFT || r.consultantSignature == null -> EdInterpretation(EdKind.UNSIGNED, r, true, false, Bi("UNSIGNED — a conversation note, not a plan. Standard care applies.", "बिना हस्ताक्षर — यह बातचीत का नोट है, योजना नहीं। सामान्य इलाज लागू।"))
        ReviewRules.due(r, today) -> EdInterpretation(EdKind.OUT_OF_DATE, r, true, SHOW_CONTENT_WHEN_OUT_OF_DATE, Bi("OUT OF DATE (review was due ${r.reviewDue}) — standard care applies. The content below is information, not instruction.", "पुरानी योजना (समीक्षा ${r.reviewDue} को होनी थी) — सामान्य इलाज लागू। नीचे की जानकारी सिर्फ़ जानकारी है, निर्देश नहीं।"))
        else -> EdInterpretation(EdKind.ACTIVE, r, false, true, Bi("ACTIVE PLAN — signed ${r.consultantSignature!!.signedAt.take(10)}, review due ${r.reviewDue}", "लागू योजना — हस्ताक्षर ${r.consultantSignature!!.signedAt.take(10)}, समीक्षा ${r.reviewDue}"))
    }

    /** The plan an ED doctor should see for a patient: the Active one, else the newest record (which never reads as instruction). */
    fun forPatient(records: List<EctprRecord>, patientId: String, today: LocalDate): EdInterpretation {
        val own = records.filter { it.patientId == patientId }
        return interpret(EctprLifecycle.active(own, patientId) ?: own.maxByOrNull { it.version }, today)
    }

    /** A scanned QR: a revoked or unknown token reads "card withdrawn". */
    fun forToken(token: String, tokens: List<CardToken>, records: List<EctprRecord>, today: LocalDate): EdInterpretation {
        val t = tokens.firstOrNull { it.token == token } ?: return interpret(null, today)
        val r = records.firstOrNull { it.id == t.ectprId }
        if (!t.live) return EdInterpretation(EdKind.REVOKED, r, true, false, Bi("CARD WITHDRAWN — this card is no longer valid. Standard care applies.", "कार्ड वापस लिया गया — यह कार्ड अब मान्य नहीं। सामान्य इलाज लागू।"))
        return interpret(r, today)
    }

    private fun dm(d: DecisionMaker?): Bi? = d?.takeUnless { it.blank }?.let {
        val basis = it.authorityBasis?.let(PlanText::authority)
        Bi(listOf(it.name, "(${it.relation})".takeIf { _ -> it.relation.isNotBlank() }, "24 h: ${it.phone24h}".takeIf { _ -> it.phone24h.isNotBlank() }, basis?.en).filterNotNull().joinToString(" "),
            listOf(it.name, "(${it.relation})".takeIf { _ -> it.relation.isNotBlank() }, "24 घंटे: ${it.phone24h}".takeIf { _ -> it.phone24h.isNotBlank() }, basis?.hi).filterNotNull().joinToString(" "))
    }

    private fun join(vararg parts: Bi?): Bi = Bi.join(" · ", parts.filterNotNull().filter { it.en.isNotBlank() })
    private fun text(value: String) = Bi.plain(value)

    /** Rows in exactly the clinical spec §6 order. Unticked choices read "No preference recorded — standard care" (V1). */
    fun rows(r: EctprRecord, amd: Bi, reviewRequest: ReviewRequest? = null, amdGoverns: Boolean = false): List<EdRow> {
        val c = r.choices
        val transfer = c.hospitalTransfer?.let { t ->
            val reasons = if (t.kind == TransferKind.ONLY_FOR) Bi.join(", ", t.reasons.map(PlanText::transferReason)) else null
            val call = c.callBeforeAmbulance.filter(String::isNotBlank).takeIf { it.isNotEmpty() }?.let { Bi.labeled(Bi("Call first", "पहले फ़ोन करें"), Bi.plain(it.joinToString(", "))) }
            join(PlanText.transfer(t.kind), reasons, call, c.ambulance?.let(PlanText::ambulance))
        }
        val cpr = c.cpr?.let { v -> join(PlanText.cpr(v), c.dnacprReason?.takeIf { v == Cpr.DNACPR }?.let(PlanText::dnacprReason),
            c.dnacprDecidedBy?.takeIf { v == Cpr.DNACPR && it.name.isNotBlank() }?.let { Bi("decided by ${it.name} (reg. ${it.registrationNo}) ${it.decidedAt}", "${it.name} (रजि. ${it.registrationNo}) ने ${it.decidedAt} को तय किया") },
            c.cprConflictReason.takeIf { v == Cpr.ATTEMPT && it.isNotBlank() }?.let { Bi("reason: $it", "कारण: $it") }) }
        val ceiling = c.respCeiling?.let { level -> join(PlanText.ceiling(level), Bi("all lower levels allowed", "नीचे के सभी स्तर मान्य").takeIf { level > 0 },
            c.maxOxygenLpm?.let { Bi("max O₂ $it L/min", "अधिकतम O₂ $it L/min") }, c.ceilingConditions.spo2Floor?.let { Bi("SpO₂ floor $it%", "SpO₂ न्यूनतम $it%") },
            c.ceilingConditions.nivMaxHours?.let { Bi("NIV up to $it h", "NIV $it घंटे तक") },
            c.ceilingConditions.reversibleCauseException?.takeIf { it }?.let { Bi("treat a reversible cause fully", "ठीक हो सकने वाले कारण का पूरा इलाज") },
            c.nivTrial?.let { Bi.join("", listOf(Bi("NIV trial", "NIV ट्रायल"), PlanText.trial(it))) }) }
        val icu = c.icu?.let { join(PlanText.icu(it.kind), PlanText.trial(it.trial).takeIf { _ -> it.kind == IcuKind.TRIAL }) }
        val organ = listOfNotNull(c.vasopressors?.let { Bi.labeled(Bi("Vasopressors", "BP की दवा"), Bi.join("", listOf(PlanText.vaso(it.kind), Bi.plain(it.maxAgents?.let { n -> " ($n)" } ?: ""), PlanText.trial(it.trial)))) },
            c.dialysis?.let { Bi.labeled(Bi("Dialysis", "डायलिसिस"), Bi.join("", listOf(PlanText.dialysis(it.kind), PlanText.trial(it.trial)))) })
        val goal = c.goalOfCare?.let { join(PlanText.goal(it), *organ.toTypedArray(), c.hydration?.let { h -> Bi.labeled(Bi("Fluids", "तरल"), PlanText.hydration(h)) },
            c.feeding.ng?.let { n -> Bi.labeled(Bi.plain("NG"), Bi.join("", listOf(PlanText.ng(n.kind), n.days?.let { d -> Bi(" ($d days)", " ($d दिन)") } ?: Bi("", "", "")))) },
            c.feeding.peg?.let { p -> Bi.labeled(Bi.plain("PEG"), PlanText.peg(p)) }) }
        val device = r.clinical.implantedDevice?.let { d -> join(PlanText.device(d), r.clinical.icdDeactivation.decision?.let { Bi.labeled(Bi("ICD decision", "ICD का फ़ैसला"), Bi.join(" ", listOf(if (it == IcdDecision.DEACTIVATE) Bi("Switch off shocks", "झटके बंद करें") else Bi("Keep shocks on", "झटके चालू रखें"), Bi.plain(r.clinical.icdDeactivation.date)))) }) }
        val consultant = r.consultantSignature
        val team = join(consultant?.let { Bi("Consultant: ${it.name} (reg. ${it.registrationNo})", "कंसल्टेंट: ${it.name} (रजि. ${it.registrationNo})") }
            ?: r.careTeam.oncologist.takeIf { it.isNotBlank() }?.let { Bi.labeled(Bi("Treating consultant", "इलाज करने वाले कंसल्टेंट"), Bi.plain(it)) },
            r.careTeam.palliativePhysician.takeIf { it.isNotBlank() }?.let { Bi("Palliative: $it ${r.careTeam.palliativePhone24h}".trim(), "पैलिएटिव: $it ${r.careTeam.palliativePhone24h}".trim()) })
        val capacity = r.capacity.status?.let { s -> join(PlanText.capacity(s), Bi("reassess by ${r.capacity.reassessOn}", "${r.capacity.reassessOn} तक दोबारा जाँचें").takeIf { s == Capacity.FLUCTUATES && r.capacity.reassessOn.isNotBlank() }) }
        val decider = join(dm(r.decisionMakers.primary), dm(r.decisionMakers.alternate)?.let { Bi.labeled(Bi("Secondary", "द्वितीय"), it) })
        val homeCare = PlanText.homeCareLines(r)
        val signedDay = consultant?.signedAt?.take(10) ?: "—"; val dueDay = r.reviewDue.ifBlank { "—" }; val form = r.formVersion; val number = r.version
        val version = Bi.join(" · ", listOfNotNull(Bi("v$number · $form · signed $signedDay · review due $dueDay", "v$number · $form · हस्ताक्षर $signedDay · समीक्षा $dueDay"),
            reviewRequest?.let { Bi.labeled(Bi("Review requested", "समीक्षा माँगी"), Bi.join(" ", listOf(PlanText.trigger(it.trigger), Bi.plain("(${it.at.take(10)})")))) }))
        // ORDER is the clinical spec's fixed list. Home care appears only when recorded, just before capacity, as on the web.
        return listOfNotNull(
            EdRow("TRANSFER", Bi("Hospital transfer", "अस्पताल ले जाना"), transfer ?: NO_PREFERENCE),
            EdRow("CPR", Bi("CPR", "CPR"), cpr ?: NO_PREFERENCE, flag = c.cpr == Cpr.DNACPR),
            EdRow("RESP_CEILING", Bi("Breathing support ceiling", "साँस की मदद की सीमा"), ceiling ?: NO_PREFERENCE),
            EdRow("ICU", Bi("ICU", "ICU"), icu ?: NO_PREFERENCE),
            EdRow("GOAL", Bi("Goal of care", "देखभाल का लक्ष्य"), goal ?: NO_PREFERENCE),
            EdRow("ALLERGIES", Bi("Allergies", "एलर्जी"), r.clinical.allergies.takeIf { it.isNotBlank() }?.let(::text) ?: NOT_RECORDED, flag = r.clinical.allergies.isNotBlank()),
            EdRow("DEVICE", Bi("Implanted device", "शरीर में लगा यंत्र"), device ?: NOT_RECORDED, flag = r.clinical.implantedDevice == ImplantedDevice.ICD || r.clinical.implantedDevice == ImplantedDevice.CRT_D),
            EdRow("DECISION_MAKER", Bi("Decision-maker (24 h phone)", "फ़ैसला लेने वाले (24 घंटे फ़ोन)"), decider.takeIf { it.en.isNotBlank() } ?: NOT_RECORDED),
            EdRow("TEAM", Bi("Treating consultant and palliative contact", "इलाज करने वाले कंसल्टेंट और पैलिएटिव संपर्क"), team.takeIf { it.en.isNotBlank() } ?: NOT_RECORDED),
            homeCare.takeIf { it.isNotEmpty() }?.let { EdRow("HOME_CARE", Bi("Home care", "घर पर देखभाल"), Bi.join("\n", it)) },
            EdRow("CAPACITY", Bi("Capacity", "निर्णय-क्षमता"), capacity ?: NOT_RECORDED, flag = r.capacity.status == Capacity.FLUCTUATES),
            EdRow("AMD", Bi("Advance care planning", "अग्रिम देखभाल योजना"), amd, flag = amdGoverns),
            EdRow("VERSION", Bi("Version, signed, review due", "संस्करण, हस्ताक्षर, समीक्षा"), version),
        )
    }
}

/** Wallet card (Appendix E). The QR carries only the token URL; the printed lines carry the essentials. */
object WalletCard {
    val TREAT_LINE = Bi("If unclear, or the problem is new and reversible — treat fully and call.", "अगर साफ़ न हो, या समस्या नई है और ठीक हो सकती है — पूरा इलाज करें और फ़ोन करें।")
    /** "Only for the reasons ticked" names the ticked reasons, as the ED view does, so the card can be read without the app. */
    fun transfer(t: HospitalTransfer): Bi = if (t.kind != TransferKind.ONLY_FOR) PlanText.transfer(t.kind)
        else Bi.labeled(PlanText.transfer(t.kind), if (t.reasons.isEmpty()) NOT_RECORDED else Bi.join(", ", t.reasons.map(PlanText::transferReason)))
    fun lines(r: EctprRecord): List<Pair<Bi, Bi>> {
        val c = r.choices
        val callFirst = c.callBeforeAmbulance.firstOrNull { it.isNotBlank() } ?: r.careTeam.palliativePhone24h.ifBlank { r.decisionMakers.primary?.phone24h.orEmpty() }
        val sign = r.consultantSignature
        fun t(v: String) = if (v.isBlank()) NOT_RECORDED else Bi.plain(v)
        return listOf(
            Bi("Patient", "मरीज़") to t(r.patient.name), Bi("UHID", "UHID") to t(r.patient.uhid), Bi("Diagnosis", "बीमारी") to t(r.clinical.diagnosis),
            Bi("CPR", "CPR") to (c.cpr?.let(PlanText::cpr) ?: NO_PREFERENCE),
            Bi("Breathing ceiling", "साँस की सीमा") to (c.respCeiling?.let(PlanText::ceiling) ?: NO_PREFERENCE),
            Bi("Hospital transfer", "अस्पताल ले जाना") to (c.hospitalTransfer?.let { transfer(it) } ?: NO_PREFERENCE),
            Bi("Call first", "पहले फ़ोन करें") to t(callFirst),
            Bi("Decision-maker", "फ़ैसला लेने वाले") to t(listOfNotNull(r.decisionMakers.primary?.name, r.decisionMakers.primary?.phone24h).filter(String::isNotBlank).joinToString(" · ")),
            Bi("Full record", "पूरा रिकॉर्ड") to Bi("Family's Sahara app; signed copy with the family", "परिवार के फ़ोन में सहारा ऐप; हस्ताक्षरित कॉपी परिवार के पास"),
            Bi("Signed by", "हस्ताक्षर") to t(sign?.let { "${it.name} (reg. ${it.registrationNo}) ${it.signedAt.take(10)}" }.orEmpty()),
            Bi("Review due", "समीक्षा") to t(r.reviewDue.takeIf(String::isNotBlank)?.let { "$it (v${r.version})" }.orEmpty()),
        )
    }
}

enum class AuditAction { VIEW, CREATE, EDIT, SIGN, PRINT, SHARE, REVOKE, BREAK_GLASS, REVIEW, UPLOAD, DELETE }
enum class AuditRecordType { ECTPR, AMD, CARD }
/** [prevHash]/[hash] chain the events, so a removed or changed entry shows up in [AuditLog.verify]. */
data class AuditEvent(val id: String, val at: String, val actor: String, val action: AuditAction, val recordType: AuditRecordType, val recordId: String, val detail: String? = null, val prevHash: String = "", val hash: String = "")

/** Append-only audit of every view, edit, sign, print and share. There is no way to change or remove an event. */
class AuditLog private constructor(private val list: List<AuditEvent>) {
    val events: List<AuditEvent> get() = list.toList()
    fun append(at: String, actor: String, action: AuditAction, type: AuditRecordType, recordId: String, detail: String? = null, id: String = CardTokens.random(8)): AuditLog {
        val prev = list.lastOrNull()?.hash.orEmpty()
        val e = AuditEvent(id, at, actor, action, type, recordId, detail, prev)
        return AuditLog(list + e.copy(hash = hashOf(e)))
    }
    fun verify(): Boolean = list.withIndex().all { (i, e) -> e.prevHash == (if (i == 0) "" else list[i - 1].hash) && e.hash == hashOf(e) }
    companion object {
        val EMPTY = AuditLog(emptyList())
        private fun hashOf(e: AuditEvent): String = MessageDigest.getInstance("SHA-256").digest(listOf(e.prevHash, e.id, e.at, e.actor, e.action.name, e.recordType.name, e.recordId, e.detail.orEmpty()).joinToString("\u001f").toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        /** Reads a stored log; a broken chain is refused rather than silently repaired. */
        fun of(events: List<AuditEvent>): AuditLog = AuditLog(events).also { check(it.verify()) { "The audit log on this phone has been changed." } }
    }
}

/** JSON in the same shape as prototype/app/ectpr-model.ts, so a record moves between the web and Android unchanged. */
object PlanJson {
    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k)
    private fun JSONObject.ns(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.ni(k: String): Int? = if (isNull(k)) null else optInt(k)
    private fun JSONObject.nd(k: String): Double? = if (isNull(k)) null else optDouble(k)
    private fun JSONObject.nb(k: String): Boolean? = if (isNull(k)) null else optBoolean(k)
    private fun JSONObject.o(k: String): JSONObject? = if (isNull(k)) null else optJSONObject(k)
    private inline fun <reified E : Enum<E>> JSONObject.e(k: String): E? = if (isNull(k)) null else enumValues<E>().firstOrNull { it.name == optString(k) }
    private fun JSONObject.strings(k: String): List<String> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
    private fun JSONObject.n(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)
    private fun arr(v: List<String>) = JSONArray(v)

    private fun trial(t: TrialPlan?) = t?.let { JSONObject().put("durationValue", it.durationValue).put("durationUnit", it.durationUnit.name).put("reviewAt", it.reviewAt).put("reviewers", arr(it.reviewers)) }
    private fun trial(j: JSONObject?) = j?.let { TrialPlan(it.optInt("durationValue"), it.e<DurationUnit>("durationUnit") ?: DurationUnit.DAYS, it.s("reviewAt"), it.strings("reviewers")) }
    private fun dm(d: DecisionMaker?) = d?.let { JSONObject().put("name", it.name).put("relation", it.relation).put("phone24h", it.phone24h).n("idType", it.idType?.name).n("idLast4", it.idLast4).n("authorityBasis", it.authorityBasis?.name) }
    private fun dm(j: JSONObject?) = j?.let { DecisionMaker(it.s("name"), it.s("relation"), it.s("phone24h"), it.e<IdType>("idType"), it.ns("idLast4"), it.e<AuthorityBasis>("authorityBasis")) }
    private fun kind(kind: String) = JSONObject().put("kind", kind)
    private fun homeCare(h: EctprHomeCare) = JSONObject().put("assessmentFrequency", h.assessmentFrequency).put("coordinator", h.coordinator).put("ngoContact", h.ngoContact).put("ngoConsulted", h.ngoConsulted).n("previousAdmission", h.previousAdmission).put("previousAdmissionDetails", h.previousAdmissionDetails)
    private fun homeCare(j: JSONObject) = EctprHomeCare(j.s("assessmentFrequency"), j.s("coordinator"), j.s("ngoContact"), j.optBoolean("ngoConsulted"), j.nb("previousAdmission"), j.s("previousAdmissionDetails"))
    private fun withdrawal(w: EctprWithdrawal) = JSONObject().put("applicable", w.applicable).put("primaryBoardDate", w.primaryBoardDate).put("secondaryBoardDate", w.secondaryBoardDate).put("primaryBoardMembers", w.primaryBoardMembers).put("secondaryBoardMembers", w.secondaryBoardMembers).put("boardDecision", w.boardDecision).n("finalDecision", w.finalDecision?.name).put("protocol", w.protocol)
    private fun withdrawal(j: JSONObject) = EctprWithdrawal(j.optBoolean("applicable"), j.s("primaryBoardDate"), j.s("secondaryBoardDate"), j.s("primaryBoardMembers"), j.s("secondaryBoardMembers"), j.s("boardDecision"), j.e<WithdrawalFinal>("finalDecision"), j.s("protocol"))

    fun encode(r: EctprRecord): JSONObject {
        val c = r.choices
        return JSONObject().put("id", r.id).put("patientId", r.patientId).put("version", r.version).put("formVersion", r.formVersion).put("status", r.status.name)
            .put("createdAt", r.createdAt).put("updatedAt", r.updatedAt).put("createdBy", r.createdBy).put("createdInEd", r.createdInEd)
            .put("patient", JSONObject().put("name", r.patient.name).put("dob", r.patient.dob).put("sex", r.patient.sex).put("address", r.patient.address).put("phone", r.patient.phone).put("uhid", r.patient.uhid).put("languagePref", r.patient.languagePref).put("gender", r.patient.gender))
            .put("careTeam", JSONObject().put("oncologist", r.careTeam.oncologist).put("palliativePhysician", r.careTeam.palliativePhysician).put("palliativePhone24h", r.careTeam.palliativePhone24h).put("extraTeam", arr(r.careTeam.extraTeam)).put("localDoctor", r.careTeam.localDoctor))
            .put("decisionMakers", JSONObject().n("primary", dm(r.decisionMakers.primary)).n("alternate", dm(r.decisionMakers.alternate)).put("familyToInform", arr(r.decisionMakers.familyToInform)).put("familyConsensusNote", r.decisionMakers.familyConsensusNote).put("presentAtDiscussion", arr(r.decisionMakers.presentAtDiscussion)).put("attemptedContact", arr(r.decisionMakers.attemptedContact)))
            .put("clinical", JSONObject().put("diagnosis", r.clinical.diagnosis).put("stage", r.clinical.stage).n("ecog", r.clinical.ecog).put("allergies", r.clinical.allergies).n("implantedDevice", r.clinical.implantedDevice?.name)
                .put("icdDeactivation", JSONObject().n("decision", r.clinical.icdDeactivation.decision?.name).put("date", r.clinical.icdDeactivation.date)).put("keyMedicines", r.clinical.keyMedicines).put("eligibilityDate", r.clinical.eligibilityDate))
            .put("capacity", JSONObject().n("status", r.capacity.status?.name).put("assessor", r.capacity.assessor).put("assessedOn", r.capacity.assessedOn).put("basis", r.capacity.basis).put("reassessOn", r.capacity.reassessOn))
            .n("existingDirective", r.existingDirective?.let { d -> when (d.kind) {
                DirectiveKind.VALID_AMD -> kind("VALID_AMD").put("date", d.date).put("location", d.location).put("attestor", d.attestor).n("twoWitnesses", d.twoWitnesses)
                DirectiveKind.PRIOR_WISHES -> kind("PRIOR_WISHES").put("note", d.note)
                DirectiveKind.NONE -> kind("NONE")
            } })
            .put("prognosticUnderstanding", JSONObject().put("confirmed", r.prognosticUnderstanding.confirmed).put("confirmedBy", r.prognosticUnderstanding.confirmedBy).put("confirmedOn", r.prognosticUnderstanding.confirmedOn))
            .put("surrogateDeclaration", JSONObject().put("answeringAsSurrogate", r.surrogateDeclaration.answeringAsSurrogate).put("confirmed", r.surrogateDeclaration.confirmed).put("confirmedOn", r.surrogateDeclaration.confirmedOn))
            .put("values", JSONObject().n("patientKnows", r.values.patientKnows).put("ownWords", r.values.ownWords).put("unacceptableOutcomes", arr(r.values.unacceptableOutcomes)).put("familyAskedNotToTell", r.values.familyAskedNotToTell)
                .put("directQuestionPlan", r.values.directQuestionPlan).n("financialConcernDiscussed", r.values.financialConcernDiscussed).n("socialWorkReferral", r.values.socialWorkReferral).n("knowsRecordedWhen", r.values.knowsRecordedWhen?.name)
                .put("knowsPrognosis", r.values.knowsPrognosis).put("doesNotWishToKnow", r.values.doesNotWishToKnow))
            .put("choices", JSONObject().n("goalOfCare", c.goalOfCare?.name).n("cpr", c.cpr?.name).n("dnacprReason", c.dnacprReason?.name).put("dnacprDiscussedWith", c.dnacprDiscussedWith)
                .n("dnacprDecidedBy", c.dnacprDecidedBy?.let { JSONObject().put("name", it.name).put("registrationNo", it.registrationNo).put("decidedAt", it.decidedAt) })
                .put("cprConflictReason", c.cprConflictReason)
                .n("hospitalTransfer", c.hospitalTransfer?.let { t -> kind(t.kind.name).also { if (t.kind == TransferKind.ONLY_FOR) it.put("reasons", JSONArray(t.reasons.map { r -> r.name })) } })
                .put("callBeforeAmbulance", arr(c.callBeforeAmbulance)).n("ambulance", c.ambulance?.name).n("respCeiling", c.respCeiling).n("maxOxygenLpm", c.maxOxygenLpm)
                .put("ceilingConditions", JSONObject().n("reversibleCauseException", c.ceilingConditions.reversibleCauseException).n("nivMaxHours", c.ceilingConditions.nivMaxHours).n("spo2Floor", c.ceilingConditions.spo2Floor).put("homeNivReason", c.ceilingConditions.homeNivReason))
                .n("icu", c.icu?.let { kind(it.kind.name).also { j -> if (it.kind == IcuKind.TRIAL) j.n("trial", trial(it.trial)) } })
                .n("vasopressors", c.vasopressors?.let { kind(it.kind.name).also { j -> if (it.kind == VasopressorKind.TRIAL) j.n("trial", trial(it.trial)); if (it.kind == VasopressorKind.YES_MAX_AGENTS) j.n("maxAgents", it.maxAgents) } })
                .n("dialysis", c.dialysis?.let { kind(it.kind.name).also { j -> if (it.kind == DialysisKind.TRIAL) j.n("trial", trial(it.trial)) } })
                .put("feeding", JSONObject().put("comfortOral", true).n("ng", c.feeding.ng?.let { kind(it.kind.name).also { j -> if (it.kind == NgKind.UP_TO_DAYS) j.n("days", it.days) } }).n("peg", c.feeding.peg?.name).put("removeExistingTube", c.feeding.removeExistingTube))
                .n("hydration", c.hydration?.name)
                .put("conflictReasons", JSONObject().also { j -> c.conflictReasons.filterValues(String::isNotBlank).forEach { (k, v) -> j.put(k, v) } })
                .put("goalAConfirmed", c.goalAConfirmed).n("nivTrial", trial(c.nivTrial)))
            .put("reviewDue", r.reviewDue)
            .put("signatures", JSONArray(r.signatures.map { s -> JSONObject().put("role", s.role.name).put("name", s.name).put("signedAt", s.signedAt).also { j -> s.registrationNo?.let { j.put("registrationNo", it) }; s.thumbImpression?.let { j.put("thumbImpression", it) }; s.surrogateRank?.let { j.put("surrogateRank", it.name) } } }))
            .put("limitedLiteracy", r.limitedLiteracy)
            .also { j -> r.gocTranscript?.let { j.put(GOC_TRANSCRIPT_KEY, GocTranscriptJson.encode(it)) }; r.homeCare?.let { j.put("homeCare", homeCare(it)) }; r.withdrawal?.let { j.put("withdrawal", withdrawal(it)) }; r.revokedAt?.let { j.put("revokedAt", it) }; r.revokedReason?.let { j.put("revokedReason", it) }; r.supersedes?.let { j.put("supersedes", it) } }
    }

    fun decode(j: JSONObject): EctprRecord {
        val p = j.optJSONObject("patient") ?: JSONObject(); val team = j.optJSONObject("careTeam") ?: JSONObject(); val dms = j.optJSONObject("decisionMakers") ?: JSONObject()
        val cl = j.optJSONObject("clinical") ?: JSONObject(); val cap = j.optJSONObject("capacity") ?: JSONObject(); val pu = j.optJSONObject("prognosticUnderstanding") ?: JSONObject()
        val sd = j.optJSONObject("surrogateDeclaration") ?: JSONObject(); val v = j.optJSONObject("values") ?: JSONObject(); val c = j.optJSONObject("choices") ?: JSONObject()
        val cc = c.optJSONObject("ceilingConditions") ?: JSONObject(); val f = c.optJSONObject("feeding") ?: JSONObject(); val icd = cl.optJSONObject("icdDeactivation") ?: JSONObject()
        return EctprRecord(
            id = j.getString("id"), patientId = j.s("patientId"), version = j.optInt("version", 1), formVersion = j.s("formVersion").ifBlank { ECTPR_FORM_VERSION },
            status = j.e<EctprStatus>("status") ?: EctprStatus.DRAFT, createdAt = j.s("createdAt"), updatedAt = j.s("updatedAt"), createdBy = j.s("createdBy"), createdInEd = j.optBoolean("createdInEd", false),
            patient = EctprPatient(p.s("name"), p.s("dob"), p.s("sex"), p.s("address"), p.s("phone"), p.s("uhid"), p.s("languagePref"), p.s("gender")),
            careTeam = CareTeam(team.s("oncologist"), team.s("palliativePhysician"), team.s("palliativePhone24h"), team.strings("extraTeam"), team.s("localDoctor")),
            decisionMakers = DecisionMakers(dm(dms.o("primary")), dm(dms.o("alternate")), dms.strings("familyToInform"), dms.s("familyConsensusNote"), dms.strings("presentAtDiscussion"), dms.strings("attemptedContact")),
            clinical = ClinicalDetails(cl.s("diagnosis"), cl.s("stage"), cl.ni("ecog"), cl.s("allergies"), cl.e<ImplantedDevice>("implantedDevice"), IcdDeactivation(icd.e<IcdDecision>("decision"), icd.s("date")), cl.s("keyMedicines"), cl.s("eligibilityDate")),
            capacity = CapacityAssessment(cap.e<Capacity>("status"), cap.s("assessor"), cap.s("assessedOn"), cap.s("basis"), cap.s("reassessOn")),
            existingDirective = j.o("existingDirective")?.let { d -> d.e<DirectiveKind>("kind")?.let { ExistingDirective(it, d.s("date"), d.s("location"), d.s("attestor"), d.s("note"), d.nb("twoWitnesses")) } },
            prognosticUnderstanding = PrognosticUnderstanding(pu.optBoolean("confirmed"), pu.s("confirmedBy"), pu.s("confirmedOn")),
            surrogateDeclaration = SurrogateDeclaration(sd.optBoolean("answeringAsSurrogate"), sd.optBoolean("confirmed"), sd.s("confirmedOn")),
            values = PlanValues(v.nb("patientKnows"), v.s("ownWords"), v.strings("unacceptableOutcomes"), v.optBoolean("familyAskedNotToTell"), v.s("directQuestionPlan"), v.nb("financialConcernDiscussed"), v.nb("socialWorkReferral"), v.e<KnowsRecordedWhen>("knowsRecordedWhen"),
                knowsPrognosis = v.optBoolean("knowsPrognosis"), doesNotWishToKnow = v.optBoolean("doesNotWishToKnow")),
            choices = PlanChoices(
                goalOfCare = c.e<GoalOfCare>("goalOfCare"), cpr = c.e<Cpr>("cpr"), dnacprReason = c.e<DnacprReason>("dnacprReason"), dnacprDiscussedWith = c.s("dnacprDiscussedWith"),
                dnacprDecidedBy = c.o("dnacprDecidedBy")?.let { DnacprDecidedBy(it.s("name"), it.s("registrationNo"), it.s("decidedAt")) }, cprConflictReason = c.s("cprConflictReason"),
                hospitalTransfer = c.o("hospitalTransfer")?.let { t -> t.e<TransferKind>("kind")?.let { HospitalTransfer(it, t.strings("reasons").mapNotNull { r -> TransferReason.entries.firstOrNull { e -> e.name == r } }) } },
                callBeforeAmbulance = c.strings("callBeforeAmbulance"), ambulance = c.e<AmbulanceOption>("ambulance"), respCeiling = c.ni("respCeiling")?.takeIf { it in 0..5 }, maxOxygenLpm = c.nd("maxOxygenLpm"),
                ceilingConditions = CeilingConditions(cc.nb("reversibleCauseException"), cc.ni("nivMaxHours"), cc.ni("spo2Floor"), cc.s("homeNivReason")),
                icu = c.o("icu")?.let { o -> o.e<IcuKind>("kind")?.let { Icu(it, trial(o.o("trial"))) } },
                vasopressors = c.o("vasopressors")?.let { o -> o.e<VasopressorKind>("kind")?.let { Vasopressors(it, trial(o.o("trial")), o.ni("maxAgents")) } },
                dialysis = c.o("dialysis")?.let { o -> o.e<DialysisKind>("kind")?.let { Dialysis(it, trial(o.o("trial"))) } },
                feeding = Feeding(f.o("ng")?.let { o -> o.e<NgKind>("kind")?.let { NgFeeding(it, o.ni("days")) } }, f.e<PegFeeding>("peg"), f.optBoolean("removeExistingTube")),
                hydration = c.e<Hydration>("hydration"),
                conflictReasons = c.optJSONObject("conflictReasons")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } }.orEmpty(),
                goalAConfirmed = c.optBoolean("goalAConfirmed"), nivTrial = trial(c.o("nivTrial")),
            ),
            homeCare = j.o("homeCare")?.let { homeCare(it) }, withdrawal = j.o("withdrawal")?.let { withdrawal(it) },
            reviewDue = j.s("reviewDue"),
            signatures = j.optJSONArray("signatures")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { s -> s.e<SignatureRole>("role")?.let { Signature(it, s.s("name"), s.ns("registrationNo"), s.s("signedAt"), s.nb("thumbImpression"), s.e<SurrogateRank>("surrogateRank")) } } }.orEmpty(),
            limitedLiteracy = j.optBoolean("limitedLiteracy"), revokedAt = j.ns("revokedAt"), revokedReason = j.ns("revokedReason"), supersedes = j.ns("supersedes"), gocTranscript = GocTranscriptJson.decode(j.optJSONObject(GOC_TRANSCRIPT_KEY), j.optInt("version", 1)),
        )
    }

    fun encode(t: CardToken) = JSONObject().put("token", t.token).put("ectprId", t.ectprId).put("patientId", t.patientId).put("createdAt", t.createdAt).n("revokedAt", t.revokedAt)
        .put("manageKey", t.manageKey).put("version", t.version).put("snapshot", t.snapshot).put("server", t.server.name).n("serverMessage", t.serverMessage).n("revokedReason", t.revokedReason)
    /** A card made before the server link has no manage key yet: it gets one now, and is uploaded like any new card. */
    fun token(j: JSONObject) = CardToken(j.getString("token"), j.s("ectprId"), j.s("patientId"), j.s("createdAt"), j.ns("revokedAt"),
        j.s("manageKey").ifBlank { CardTokens.newToken() }, j.optInt("version", 1), j.s("snapshot"), j.e<CardServerState>("server") ?: if (j.isNull("revokedAt")) CardServerState.LOCAL_ONLY else CardServerState.REVOKE_PENDING, j.ns("serverMessage"), j.ns("revokedReason"))
    fun encode(e: AuditEvent) = JSONObject().put("id", e.id).put("at", e.at).put("actor", e.actor).put("action", e.action.name).put("recordType", e.recordType.name).put("recordId", e.recordId).n("detail", e.detail).put("prevHash", e.prevHash).put("hash", e.hash)
    fun audit(j: JSONObject) = AuditEvent(j.s("id"), j.s("at"), j.s("actor"), j.e<AuditAction>("action") ?: AuditAction.VIEW, j.e<AuditRecordType>("recordType") ?: AuditRecordType.ECTPR, j.s("recordId"), j.ns("detail"), j.s("prevHash"), j.s("hash"))
    fun encode(r: ReviewRequest) = JSONObject().put("id", r.id).put("ectprId", r.ectprId).put("trigger", r.trigger.name).put("at", r.at).put("by", r.by).put("note", r.note)
    fun review(j: JSONObject) = ReviewRequest(j.s("id"), j.s("ectprId"), j.e<ReviewTrigger>("trigger") ?: ReviewTrigger.PATIENT_OR_FAMILY_REQUEST, j.s("at"), j.s("by"), j.s("note"))
    internal fun objects(a: JSONArray?): List<JSONObject> = a?.let { (0 until it.length()).map(it::getJSONObject) }.orEmpty()
}
