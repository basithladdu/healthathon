package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * Living will / Advance Medical Directive (AMD): the Kotlin twin of prototype/app/amd-rules.ts and the AMD section of ectpr-model.ts.
 * Template: Vidhi Centre for Legal Policy / ACP India model AMD. Law: Common Cause (2018) as modified on 24 Jan 2023 (MA 1699/2019 in
 * WP(C) 215/2005); Harish Rana (11 Mar 2026). The app only prepares the paper. It becomes valid when signed by hand before two witnesses
 * and attested by a notary or gazetted officer (L3); it is never validated by e-signature, and the app never withdraws treatment (L8).
 */

const val AMD_FORM_VERSION = "Sahara AMD v1.0 (Vidhi/ACP India model; SC order 24 Jan 2023)"

enum class AmdStatus { DRAFT, GENERATED, EXECUTED, SUPERSEDED, REVOKED }
/** Conditions the directive applies to (Vidhi §2, footnotes 2–3). At least one is required. */
enum class AmdCondition { TERMINAL, END_STAGE, VEGETATIVE }
/** Treatments listed in Vidhi §2. TUBE_FEEDING is clinically assisted nutrition and hydration: medical treatment per Harish Rana (L9). */
enum class AmdTreatment { CPR, VENTILATION, DIALYSIS, TUBE_FEEDING, IV_FLUIDS_ANTIBIOTICS, CHEMOTHERAPY, RADIOTHERAPY, SURGERY }
/** REFUSE = "I DO NOT WANT", ACCEPT = "I WANT"; no entry = not decided (nothing pre-selected). There is no option to end life. */
enum class AmdChoice { REFUSE, ACCEPT }
enum class AmdPlaceOfCare { HOSPITAL, HOSPICE, HOME, NAMED_INSTITUTION }
enum class AttestorKind { NOTARY, GAZETTED_OFFICER }
enum class AmdRecipientKind { DHR, FAMILY_PHYSICIAN, CUSTODIAN }
enum class AmdCopyMethod { IN_PERSON, POST, DIGITAL }
enum class AmdCustodianStatus { ACKNOWLEDGED, PENDING }

data class AmdExecutant(val name: String = "", val gender: String = "", val dob: String = "", val idType: IdType? = null, val idLast4: String = "",
    val permanentAddress: String = "", val currentAddress: String = "", val localBody: String = "", val readOutToMe: Boolean = false)
data class AmdDeclarations(val capacity: Boolean = false, val consideredCarefully: Boolean = false, val voluntary: Boolean = false, val understandsNotAppliedIfUnclear: Boolean = false, val capacityCertificateAttached: Boolean = false)
data class AmdWishes(val placeOfCare: AmdPlaceOfCare? = null, val institution: String = "", val physician: String = "", val focus: String = "", val preferredName: String = "", val genderPronouns: String = "", val partnerName: String = "", val partnerTerm: String = "")
data class AmdAfterDeath(val organDonor: Boolean = false, val bodyDonation: Boolean = false, val bodyDonationInstitution: String = "", val lastRites: String = "")
/** Designated healthcare representative (Vidhi §4), in preference order. Never a full ID: type + last 4 digits only. */
data class AmdDhr(val id: String = CardTokens.random(8), val name: String = "", val relation: String = "", val dob: String = "", val idType: IdType? = null, val idLast4: String = "",
    val phone: String = "", val phoneAlt: String = "", val email: String = "", val permanentAddress: String = "", val currentAddress: String = "", val agreed: Boolean = false)
data class AmdFamilyPhysician(val name: String = "", val registrationNo: String = "", val phone: String = "", val address: String = "")
data class AmdContent(
    val executant: AmdExecutant = AmdExecutant(), val declarations: AmdDeclarations = AmdDeclarations(), val values: String = "",
    val conditions: List<AmdCondition> = emptyList(), val anticipatedConditions: String = "",
    val treatments: Map<AmdTreatment, AmdChoice> = emptyMap(),
    val wishes: AmdWishes = AmdWishes(), val afterDeath: AmdAfterDeath = AmdAfterDeath(),
    val dhrs: List<AmdDhr> = listOf(AmdDhr()), val dhrsActJointly: Boolean = false,
    val familyPhysician: AmdFamilyPhysician = AmdFamilyPhysician(), val custodianBody: String = "",
)
data class AmdWitness(val name: String = "", val address: String = "", val idType: IdType? = null, val idLast4: String = "", val email: String = "", val phone: String = "")
data class AmdAttestor(val kind: AttestorKind? = null, val name: String = "", val designation: String = "", val registrationNo: String = "")
data class AmdExecution(val signedOn: String = "", val place: String = "", val thumbImpression: Boolean = false, val witnesses: List<AmdWitness> = listOf(AmdWitness(), AmdWitness()), val attestor: AmdAttestor = AmdAttestor(),
    /** File name in this person's private folder; the SHA-256 lets a doctor check the copy is the one recorded (L5). */
    val scanKey: String = "", val scanName: String = "", val scanType: String = "", val scanSha256: String = "", val scanUploadedAt: String = "")
/** L5: custodian acknowledgement, or "custodian pending" (few states have appointed custodians). */
data class AmdCustodian(val status: AmdCustodianStatus = AmdCustodianStatus.PENDING, val body: String = "", val officer: String = "", val acknowledgedOn: String = "")
/** L4 + DPDP: one entry per copy recipient, each with its own consent. */
data class AmdCopy(val id: String, val kind: AmdRecipientKind, val name: String, val dhrId: String? = null, val consent: Boolean = false, val consentAt: String = "", val given: Boolean = false, val givenOn: String = "", val method: AmdCopyMethod? = null)
data class AmdAbha(val address: String, val consent: Boolean, val linkedOn: String)
data class AmdRevocation(val revokedOn: String, val reason: String, val recordedAt: String, val scanKey: String? = null, val scanSha256: String? = null)
data class AmdRecord(
    val id: String, val patientId: String, val version: Int = 1, val formVersion: String = AMD_FORM_VERSION, val status: AmdStatus = AmdStatus.DRAFT,
    val createdAt: String, val updatedAt: String = createdAt, val createdBy: String = "",
    val content: AmdContent = AmdContent(),
    val generatedAt: String? = null, val pdfLang: String? = null, val pdfSha256: String? = null, val contentSha256: String? = null,
    val execution: AmdExecution? = null, val custodian: AmdCustodian? = null, val copies: List<AmdCopy> = emptyList(), val abha: AmdAbha? = null,
    val supersededAt: String? = null, val revocation: AmdRevocation? = null,
)
/** What the ED view and the card know about the living will; the document itself stays on this phone. */
data class AmdStatusSummary(val status: AmdStatus, val executedOn: String?, val version: Int, val attestor: String?)

object AmdText {
    fun condition(c: AmdCondition) = when (c) {
        AmdCondition.TERMINAL -> Bi("A terminal illness (an illness that will end my life)", "अंतिम अवस्था की बीमारी (जो मेरा जीवन ख़त्म करेगी)")
        AmdCondition.END_STAGE -> Bi("An end-stage condition (lasting damage that keeps getting worse, where treatment will not work)", "अंतिम चरण की स्थिति (स्थायी नुक़सान जो बढ़ता जाए, जहाँ इलाज काम न करे)")
        AmdCondition.VEGETATIVE -> Bi("A vegetative state (awake but not aware, for many months)", "वेजिटेटिव अवस्था (जागे हुए पर कई महीनों तक होश नहीं)")
    }
    fun treatment(t: AmdTreatment) = when (t) {
        AmdTreatment.CPR -> Bi("CPR (pressing the chest, electric shocks to restart the heart)", "CPR (छाती दबाना, दिल दोबारा चलाने के लिए बिजली का झटका)")
        AmdTreatment.VENTILATION -> Bi("Breathing machine (ventilator) or other life support", "साँस की मशीन (वेंटिलेटर) या अन्य जीवन-रक्षक मशीन")
        AmdTreatment.DIALYSIS -> Bi("Dialysis (machine that cleans the blood when kidneys fail)", "डायलिसिस (गुर्दे फेल होने पर ख़ून साफ़ करने वाली मशीन)")
        AmdTreatment.TUBE_FEEDING -> Bi("Feeding through a tube or drip", "नली या ड्रिप से खाना देना")
        AmdTreatment.IV_FLUIDS_ANTIBIOTICS -> Bi("Drips and medicines such as antibiotics (comfort medicines always continue)", "ड्रिप और एंटीबायोटिक जैसी दवाएँ (आराम की दवाएँ हमेशा जारी)")
        AmdTreatment.CHEMOTHERAPY -> Bi("Chemotherapy", "कीमोथेरेपी")
        AmdTreatment.RADIOTHERAPY -> Bi("Radiotherapy", "रेडियोथेरेपी")
        AmdTreatment.SURGERY -> Bi("Surgery (operations), except for comfort", "सर्जरी (ऑपरेशन), आराम के लिए छोड़कर")
    }
    fun treatmentHelp(t: AmdTreatment) = when (t) {
        AmdTreatment.CPR -> Bi("Used when the heart stops. In very serious illness it rarely works and can break ribs.", "दिल रुकने पर किया जाता है। बहुत गंभीर बीमारी में यह कम ही काम करता है और पसलियाँ टूट सकती हैं।")
        AmdTreatment.VENTILATION -> Bi("A tube in the throat connected to a machine that breathes for you, usually in the ICU.", "गले में एक नली जो मशीन से जुड़ी होती है और आपके लिए साँस लेती है, आमतौर पर ICU में।")
        AmdTreatment.DIALYSIS -> Bi("Usually several hours, a few times a week.", "आमतौर पर हफ़्ते में कुछ बार, कई घंटे।")
        AmdTreatment.TUBE_FEEDING -> Bi("A tube in the nose or stomach, or a drip. The Supreme Court treats this as medical treatment (Harish Rana, 2026). Food and drink by mouth for comfort, mouth care and hand feeding always continue.", "नाक या पेट में नली, या ड्रिप। सुप्रीम कोर्ट इसे चिकित्सा इलाज मानता है (हरीश राणा, 2026)। आराम के लिए मुँह से खाना-पीना, मुँह की देखभाल और हाथ से खिलाना हमेशा जारी रहते हैं।")
        AmdTreatment.IV_FLUIDS_ANTIBIOTICS -> Bi("Pain and comfort medicines are never stopped by this choice.", "इस चुनाव से दर्द और आराम की दवाएँ कभी बंद नहीं होतीं।")
        AmdTreatment.CHEMOTHERAPY -> Bi("Medicines that treat cancer.", "कैंसर का इलाज करने वाली दवाएँ।")
        AmdTreatment.RADIOTHERAPY -> Bi("Radiation treatment for cancer.", "कैंसर के लिए विकिरण इलाज।")
        AmdTreatment.SURGERY -> Bi("Operations to relieve symptoms or improve quality of life are still allowed.", "लक्षणों से राहत या जीवन की गुणवत्ता के लिए ऑपरेशन फिर भी हो सकते हैं।")
    }
    fun choice(c: AmdChoice) = when (c) { AmdChoice.REFUSE -> Bi("I do not want", "मुझे नहीं चाहिए"); AmdChoice.ACCEPT -> Bi("I want", "मुझे चाहिए") }
    val NOT_DECIDED = Bi("Not decided", "तय नहीं")
    fun place(p: AmdPlaceOfCare) = when (p) {
        AmdPlaceOfCare.HOSPITAL -> Bi("In a hospital or medical facility", "अस्पताल या चिकित्सा केंद्र में")
        AmdPlaceOfCare.HOSPICE -> Bi("In a hospice or similar place", "हॉस्पिस या ऐसी जगह में")
        AmdPlaceOfCare.HOME -> Bi("At home", "घर पर")
        AmdPlaceOfCare.NAMED_INSTITUTION -> Bi("In a place I name", "मेरी बताई जगह पर")
    }
    fun attestor(a: AttestorKind) = when (a) { AttestorKind.NOTARY -> Bi("Notary", "नोटरी"); AttestorKind.GAZETTED_OFFICER -> Bi("Gazetted officer", "राजपत्रित अधिकारी") }
    fun status(s: AmdStatus) = when (s) {
        AmdStatus.DRAFT -> Bi("Draft", "ड्राफ़्ट"); AmdStatus.GENERATED -> Bi("Printed, not yet signed", "प्रिंट हुआ, अभी हस्ताक्षर नहीं")
        AmdStatus.EXECUTED -> Bi("Signed and recorded", "हस्ताक्षरित और दर्ज"); AmdStatus.SUPERSEDED -> Bi("Replaced by a newer version", "नए संस्करण से बदला गया"); AmdStatus.REVOKED -> Bi("Cancelled in writing", "लिखित रूप में रद्द")
    }
    fun copyKind(k: AmdRecipientKind) = when (k) { AmdRecipientKind.DHR -> Bi("Representative", "प्रतिनिधि"); AmdRecipientKind.FAMILY_PHYSICIAN -> Bi("Family doctor", "पारिवारिक डॉक्टर"); AmdRecipientKind.CUSTODIAN -> Bi("Custodian", "संरक्षक") }
    fun copyMethod(m: AmdCopyMethod) = when (m) { AmdCopyMethod.IN_PERSON -> Bi("In person", "हाथ से"); AmdCopyMethod.POST -> Bi("By post", "डाक से"); AmdCopyMethod.DIGITAL -> Bi("Digital copy", "डिजिटल प्रति") }
    val COMFORT = Bi("Comfort care, pain relief, mouth care and feeding by mouth always continue. This cannot be changed.", "आराम की देखभाल, दर्द से राहत, मुँह की देखभाल और मुँह से खिलाना हमेशा जारी रहते हैं। इसे बदला नहीं जा सकता।")
    /** Used by the plan's feeding step. */
    val TUBE_NOTE = Bi("Tube feeding is a medical treatment. Comfort feeding by mouth always continues.", "नली से खाना एक चिकित्सा इलाज है। मुँह से आराम के लिए खिलाना हमेशा जारी रहता है।")
    /** L7: shown wherever the AMD status appears in the ED view. AMD_ED_CAVEAT in amd-rules.ts. */
    val CAVEAT = Bi("If the situation is ambiguous or not anticipated by the AMD, it is not applied.", "अगर स्थिति साफ़ नहीं है या AMD में उसका अनुमान नहीं था, तो AMD लागू नहीं होता।")
    val GOVERNS = Bi("The AMD governs over this plan.", "AMD इस योजना से ऊपर मान्य है।")
    val PAPER = Bi("Sahara prints the paper. It becomes valid only when you sign it by hand in front of two witnesses and a notary or gazetted officer attests it. Then you add a photo or scan here.", "सहारा काग़ज़ प्रिंट करता है। यह तभी वैध होता है जब आप दो गवाहों के सामने हाथ से हस्ताक्षर करें और नोटरी या राजपत्रित अधिकारी इसे सत्यापित करें। फिर आप यहाँ फ़ोटो या स्कैन जोड़ें।")
    /** LegalPending in prototype/app/ectpr-plan-screen.tsx. */
    val LEGAL_PENDING_TITLE = Bi("Legal review pending", "क़ानूनी समीक्षा बाक़ी")
    val LEGAL_PENDING = Bi("For a patient who cannot decide, withholding or withdrawing life-sustaining treatment may need the Medical Board process (Common Cause 2023; Harish Rana 2026). This plan records wishes and clinical limits; it does not by itself legally authorise withholding treatment.", "जो मरीज़ फ़ैसला नहीं ले सकते, उनके लिए जीवन-रक्षक इलाज शुरू न करने या रोकने के लिए मेडिकल बोर्ड की प्रक्रिया ज़रूरी हो सकती है (कॉमन कॉज़ 2023; हरीश राणा 2026)। यह योजना इच्छाएँ और इलाज की सीमाएँ दर्ज करती है; यह अपने आप में इलाज रोकने की क़ानूनी अनुमति नहीं देती।")
    /** boardReconsiderationNotice in prototype/app/ectpr-labels.ts. */
    val RECONSIDERATION = Bi("After both boards agree, nothing is done for 30 days. In this reconsideration period, anyone affected may go to court (Harish Rana, 2026 INSC 222, para 322(vi)).", "दोनों बोर्ड सहमत होने के बाद 30 दिन तक कुछ नहीं किया जाता। इस पुनर्विचार अवधि में कोई भी प्रभावित व्यक्ति अदालत जा सकता है (हरीश राणा, 2026 INSC 222, पैरा 322(vi))।")
    /** L8, AMD_BOARD_PATHWAY in amd-rules.ts: information only. The app has no "withdraw" action. */
    val BOARD_PATHWAY = listOf(
        Bi("Withholding or withdrawing life-sustaining treatment for a patient who cannot decide follows the board process below. This app only shows it as information. It never starts, stops or decides any treatment. (Legal review pending.)", "जो मरीज़ ख़ुद निर्णय नहीं ले सकता, उसके लिए जीवन-रक्षक इलाज न शुरू करना या रोकना नीचे दी गई बोर्ड प्रक्रिया से होता है। यह ऐप इसे केवल जानकारी के रूप में दिखाता है। यह कभी कोई इलाज शुरू, बंद या तय नहीं करता। (क़ानूनी समीक्षा बाक़ी।)"),
        Bi("1. Primary Medical Board (hospital): the treating doctor and at least two experts with 5+ years of experience. It meets the patient with the named representatives and gives its opinion, preferably within 48 hours.", "1. प्राथमिक मेडिकल बोर्ड (अस्पताल): इलाज करने वाले डॉक्टर और कम से कम दो विशेषज्ञ जिन्हें 5+ साल का अनुभव हो। यह नामित प्रतिनिधियों के साथ मरीज़ से मिलता है और हो सके तो 48 घंटे में राय देता है।"),
        Bi("2. Secondary Medical Board: a doctor nominated by the district Chief Medical Officer (from a panel reviewed at least every 12 months; nomination preferably within 48 hours of the hospital’s request) and at least two other experts, none from the first board. Opinion preferably within 48 hours.", "2. द्वितीयक मेडिकल बोर्ड: ज़िला मुख्य चिकित्सा अधिकारी द्वारा नामित डॉक्टर (ऐसे पैनल से जिसकी समीक्षा कम से कम हर 12 महीने हो; नामांकन हो सके तो अस्पताल के अनुरोध के 48 घंटे में) और कम से कम दो अन्य विशेषज्ञ, जो पहले बोर्ड में न हों। राय हो सके तो 48 घंटे में।"),
        Bi("3. Consent: the named representatives (DHRs) consent in writing; without an AMD, the next of kin’s written consent, reflecting the patient’s own wishes. The hospital then informs the Judicial Magistrate First Class (JMFC).", "3. सहमति: नामित प्रतिनिधि (DHR) लिखित सहमति देते हैं; AMD न हो तो निकट संबंधी की लिखित सहमति, जो मरीज़ की अपनी इच्छा दिखाए। फिर अस्पताल न्यायिक मजिस्ट्रेट प्रथम श्रेणी (JMFC) को सूचित करता है।"),
        Bi("4. After both boards agree there is a 30-day reconsideration period, when anyone affected may go to court (Harish Rana, 2026). Only then is there a planned palliative process, never abandonment.", "4. दोनों बोर्ड सहमत होने के बाद 30 दिन की पुनर्विचार अवधि होती है, जिसमें कोई भी प्रभावित व्यक्ति अदालत जा सकता है (हरीश राणा, 2026)। उसके बाद ही योजना के साथ पैलिएटिव देखभाल की प्रक्रिया होती है, कभी भी छोड़ देना नहीं।"),
        Bi("5. If a board refuses, or the hospital will not start the process, the representatives, family or doctors may approach the High Court (Article 226).", "5. अगर बोर्ड मना करे, या अस्पताल प्रक्रिया शुरू न करे, तो प्रतिनिधि, परिवार या डॉक्टर हाई कोर्ट (अनुच्छेद 226) जा सकते हैं।"),
    )
}

/** Only the last 4 digits are ever stored. A pasted full Aadhaar keeps just its last four. */
object AmdIds {
    private val LAST4 = Regex("^\\d{4}$")
    fun last4(input: String): String = input.filter { it in '0'..'9' }.takeLast(4)
    fun valid(value: String): Boolean = value.isEmpty() || LAST4.matches(value)
}

object AmdRules {
    /** The living will is the patient's own words: an invited family member (actor "Family") can read it but not change it. Doctors keep their existing rights. */
    fun canChange(canEdit: Boolean, clinician: Boolean, actor: String) = canEdit && (clinician || actor != "Family")
    private fun issue(id: String, severity: IssueSeverity, field: String, en: String, hi: String) = RuleIssue(id, severity, field, en, hi)
    private val PREFIX = Regex("^(dr|mr|mrs|ms|shri|smt|sri|kumari)\\.?\\s+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    fun normaliseName(name: String) = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC).lowercase().trim().replace(PREFIX, "").replace(NON_WORD, " ").trim()
    /** Words that ask for an act intended to end life. Such a request is illegal in India and cannot be printed. */
    private val ENDING_LIFE = Regex("\\b(active\\s+euthanasia|mercy\\s+killing|lethal\\s+injection|assisted\\s+(suicide|dying)|help\\s+me\\s+die|end\\s+my\\s+life|kill\\s+me)\\b|इच्छा\\s*मृत्यु\\s*का\\s*इंजेक्शन|ज़हर\\s*का\\s*इंजेक्शन|मुझे\\s*मार\\s*दें", RegexOption.IGNORE_CASE)

    /** L1, L2, L9 and the no-euthanasia rule. BLOCK stops the document being made. */
    fun validateContent(content: AmdContent, today: LocalDate): List<RuleIssue> = buildList {
        val ex = content.executant
        if (ex.name.isBlank()) add(issue("NAME", IssueSeverity.BLOCK, "executant.name", "Enter your full name.", "अपना पूरा नाम लिखें।"))
        val age = PlanDates.age(ex.dob, today)
        if (ex.dob.isBlank()) add(issue("L1_DOB", IssueSeverity.BLOCK, "executant.dob", "Enter your date of birth.", "अपनी जन्म तिथि लिखें।"))
        else if (age == null) add(issue("L1_DOB", IssueSeverity.BLOCK, "executant.dob", "Enter a real date of birth.", "सही जन्म तिथि लिखें।"))
        else if (age < 18) add(issue("L1_ADULT", IssueSeverity.BLOCK, "executant.dob", "Only an adult (18 years or older) can complete advance care planning.", "केवल वयस्क (18 साल या उससे ज़्यादा) ही अग्रिम देखभाल योजना बना सकते हैं।"))
        if (!AmdIds.valid(ex.idLast4)) add(issue("ID_LAST4", IssueSeverity.BLOCK, "executant.idLast4", "Enter only the last 4 digits of the ID.", "पहचान पत्र के केवल आख़िरी 4 अंक लिखें।"))
        if (ex.permanentAddress.isBlank()) add(issue("ADDRESS", IssueSeverity.WARN, "executant.permanentAddress", "Address is empty. Write it by hand on the paper.", "पता खाली है। काग़ज़ पर हाथ से लिखें।"))
        val d = content.declarations
        if (!d.capacity) add(issue("L1_CAPACITY", IssueSeverity.BLOCK, "declarations.capacity", "Confirm that you understand what this document means.", "पुष्टि करें कि आप समझते हैं कि इस दस्तावेज़ का क्या मतलब है।"))
        if (!d.voluntary) add(issue("L1_VOLUNTARY", IssueSeverity.BLOCK, "declarations.voluntary", "Confirm that nobody is forcing or pressuring you.", "पुष्टि करें कि कोई आप पर दबाव नहीं डाल रहा है।"))
        if (!d.consideredCarefully) add(issue("L1_CONSIDERED", IssueSeverity.BLOCK, "declarations.consideredCarefully", "Confirm that you have thought about this carefully.", "पुष्टि करें कि आपने इस पर ध्यान से सोचा है।"))
        if (!d.understandsNotAppliedIfUnclear) add(issue("L7_UNDERSTOOD", IssueSeverity.BLOCK, "declarations.understandsNotAppliedIfUnclear", "Confirm that you understand it will not be used if the situation is unclear or was not foreseen.", "पुष्टि करें कि आप समझते हैं कि स्थिति साफ़ न होने या पहले से न सोची गई होने पर इसका उपयोग नहीं होगा।"))
        if (content.conditions.isEmpty()) add(issue("CONDITIONS", IssueSeverity.BLOCK, "conditions", "Choose at least one situation when these advance care planning wishes apply.", "कम से कम एक स्थिति चुनें जब ये अग्रिम देखभाल योजना की इच्छाएँ लागू हों।"))
        if (content.treatments.isEmpty()) add(issue("NO_TREATMENT_CHOICE", IssueSeverity.WARN, "treatments", "No treatment chosen. The general direction still applies.", "कोई इलाज नहीं चुना। सामान्य निर्देश फिर भी लागू होगा।"))
        listOf("values" to content.values, "anticipatedConditions" to content.anticipatedConditions, "wishes.focus" to content.wishes.focus, "afterDeath.lastRites" to content.afterDeath.lastRites).forEach { (field, text) ->
            if (ENDING_LIFE.containsMatchIn(text)) add(issue("NO_EUTHANASIA", IssueSeverity.BLOCK, field, "Advance care planning cannot ask for anything meant to end life. That is against the law in India. Please change these words.", "अग्रिम देखभाल योजना में जीवन ख़त्म करने के लिए कुछ भी नहीं माँगा जा सकता। यह भारत में क़ानून के ख़िलाफ़ है। कृपया ये शब्द बदलें।"))
        }
        val named = content.dhrs.filter { it.name.isNotBlank() }
        if (named.isEmpty()) add(issue("L2_DHR_REQUIRED", IssueSeverity.BLOCK, "dhrs", "Name at least one person who can speak for you (a representative).", "कम से कम एक व्यक्ति का नाम लिखें जो आपकी ओर से बोल सके (प्रतिनिधि)।"))
        else if (named.size < 2) add(issue("L2_DHR_TWO", IssueSeverity.WARN, "dhrs", "Name a second representative, in case the first cannot be reached.", "दूसरा प्रतिनिधि भी लिखें, ताकि पहले से संपर्क न हो तो दूसरे से हो सके।"))
        content.dhrs.forEachIndexed { index, dhr ->
            if (dhr.name.isBlank() && dhr.phone.isBlank() && dhr.relation.isBlank()) return@forEachIndexed
            val n = index + 1; val field = "dhrs.$index"
            if (dhr.name.isBlank()) add(issue("DHR_NAME", IssueSeverity.BLOCK, "$field.name", "Representative $n: enter the full name.", "प्रतिनिधि $n: पूरा नाम लिखें।"))
            if (dhr.relation.isBlank()) add(issue("DHR_RELATION", IssueSeverity.BLOCK, "$field.relation", "Representative $n: say how they are related to you.", "प्रतिनिधि $n: बताएँ कि वे आपके क्या लगते हैं।"))
            if (dhr.phone.isBlank()) add(issue("DHR_PHONE", IssueSeverity.BLOCK, "$field.phone", "Representative $n: enter a mobile number.", "प्रतिनिधि $n: मोबाइल नंबर लिखें।"))
            if (!AmdIds.valid(dhr.idLast4)) add(issue("ID_LAST4", IssueSeverity.BLOCK, "$field.idLast4", "Representative $n: enter only the last 4 digits of the ID.", "प्रतिनिधि $n: पहचान पत्र के केवल आख़िरी 4 अंक लिखें।"))
            if (dhr.dob.isNotBlank() && (PlanDates.age(dhr.dob, today) ?: 0) < 18) add(issue("DHR_ADULT", IssueSeverity.BLOCK, "$field.dob", "Representative $n must be an adult (18 or older).", "प्रतिनिधि $n वयस्क (18 या उससे ज़्यादा) होना चाहिए।"))
            if (dhr.name.isNotBlank() && ex.name.isNotBlank() && normaliseName(dhr.name) == normaliseName(ex.name)) add(issue("DHR_SELF", IssueSeverity.BLOCK, "$field.name", "You cannot name yourself as your representative.", "आप ख़ुद को अपना प्रतिनिधि नहीं बना सकते।"))
            if (!dhr.agreed) add(issue("DHR_AGREED", IssueSeverity.WARN, "$field.agreed", "Representative $n: tick once they agree.", "प्रतिनिधि $n: सहमत होने पर टिक करें।"))
        }
        val names = named.map { normaliseName(it.name) }
        if (names.toSet().size != names.size) add(issue("DHR_DUPLICATE", IssueSeverity.BLOCK, "dhrs", "The same person is listed twice as a representative.", "एक ही व्यक्ति प्रतिनिधि के रूप में दो बार लिखा है।"))
        if (content.custodianBody.isBlank()) add(issue("CUSTODIAN_BODY", IssueSeverity.WARN, "custodianBody", "Write your panchayat or municipality.", "अपनी पंचायत या नगरपालिका लिखें।"))
    }

    /** L3 and L5. BLOCK keeps the living will "printed, not signed". */
    fun validateExecution(r: AmdRecord, e: AmdExecution, custodian: AmdCustodian?, today: LocalDate): List<RuleIssue> = buildList {
        if (r.status != AmdStatus.GENERATED) add(issue("NOT_GENERATED", IssueSeverity.BLOCK, "status", "Print the PDF first. Only a printed and signed version can be recorded.", "पहले PDF प्रिंट करें। केवल प्रिंट और हस्ताक्षरित संस्करण दर्ज हो सकता है।"))
        val signed = PlanDates.day(e.signedOn)
        if (signed == null) add(issue("SIGNED_ON", IssueSeverity.BLOCK, "execution.signedOn", "Enter the date it was signed.", "हस्ताक्षर की तारीख़ लिखें।"))
        else if (signed.isAfter(today)) add(issue("SIGNED_FUTURE", IssueSeverity.BLOCK, "execution.signedOn", "The signing date cannot be in the future.", "हस्ताक्षर की तारीख़ भविष्य की नहीं हो सकती।"))
        else if (r.generatedAt != null && PlanDates.day(r.generatedAt)?.isAfter(signed) == true) add(issue("SIGNED_BEFORE_PRINT", IssueSeverity.BLOCK, "execution.signedOn", "The signing date is before this version was printed. Check you uploaded the right version.", "हस्ताक्षर की तारीख़ इस संस्करण के प्रिंट से पहले की है। जाँचें कि सही संस्करण अपलोड किया है।"))
        if (!Regex("^[a-f0-9]{64}$").matches(e.scanSha256) || e.scanKey.isBlank()) add(issue("L3_SCAN", IssueSeverity.BLOCK, "execution.scan", "Upload a photo or scan of the signed and attested paper.", "हस्ताक्षरित और सत्यापित काग़ज़ की फ़ोटो या स्कैन अपलोड करें।"))
        val executant = normaliseName(r.content.executant.name)
        val dhrNames = r.content.dhrs.map { normaliseName(it.name) }.filter(String::isNotBlank)
        val witnesses = e.witnesses.take(2).let { it + List(2 - it.size) { AmdWitness() } }
        witnesses.forEachIndexed { index, w ->
            val n = index + 1; val field = "execution.witnesses.$index"
            if (w.name.isBlank()) { add(issue("L3_WITNESS", IssueSeverity.BLOCK, "$field.name", "Enter the name of witness $n.", "गवाह $n का नाम लिखें।")); return@forEachIndexed }
            val name = normaliseName(w.name)
            if (name == executant) add(issue("WITNESS_SELF", IssueSeverity.BLOCK, "$field.name", "The person doing the advance care planning cannot be their own witness.", "अग्रिम देखभाल योजना बनाने वाला व्यक्ति ख़ुद अपना गवाह नहीं हो सकता।"))
            if (name in dhrNames) add(issue("L3_WITNESS_IS_DHR", IssueSeverity.WARN, "$field.name", "Witness $n is also a representative.", "गवाह $n एक प्रतिनिधि भी है।"))
            if (!AmdIds.valid(w.idLast4)) add(issue("ID_LAST4", IssueSeverity.BLOCK, "$field.idLast4", "Witness $n: enter only the last 4 digits of the ID.", "गवाह $n: पहचान पत्र के केवल आख़िरी 4 अंक लिखें।"))
        }
        if (witnesses[0].name.isNotBlank() && normaliseName(witnesses[0].name) == normaliseName(witnesses[1].name)) add(issue("WITNESS_SAME", IssueSeverity.BLOCK, "execution.witnesses.1.name", "The two witnesses must be two different people.", "दोनों गवाह अलग-अलग व्यक्ति होने चाहिए।"))
        val a = e.attestor
        if (a.kind == null) add(issue("L3_ATTESTOR_KIND", IssueSeverity.BLOCK, "execution.attestor.kind", "Choose who attested it: a notary or a gazetted officer.", "चुनें कि किसने सत्यापित किया: नोटरी या राजपत्रित अधिकारी।"))
        if (a.name.isBlank()) add(issue("L3_ATTESTOR", IssueSeverity.BLOCK, "execution.attestor.name", "Enter the name of the notary or gazetted officer.", "नोटरी या राजपत्रित अधिकारी का नाम लिखें।"))
        else if (normaliseName(a.name) in (listOf(executant) + dhrNames + witnesses.map { normaliseName(it.name) })) add(issue("ATTESTOR_INDEPENDENT", IssueSeverity.BLOCK, "execution.attestor.name", "The notary or gazetted officer must be a different person from you, your representatives and the witnesses.", "नोटरी या राजपत्रित अधिकारी आपसे, आपके प्रतिनिधियों से और गवाहों से अलग व्यक्ति होना चाहिए।"))
        if (custodian == null) add(issue("L5_CUSTODIAN", IssueSeverity.BLOCK, "custodian", "Record the custodian: acknowledged, or \"custodian pending\".", "संरक्षक दर्ज करें: पावती मिली, या \"संरक्षक बाक़ी\"।"))
        else custodianIssue(custodian)?.let { add(it) }
    }

    fun custodianIssue(c: AmdCustodian): RuleIssue? = if (c.status == AmdCustodianStatus.ACKNOWLEDGED && (c.officer.isBlank() || c.acknowledgedOn.isBlank()))
        issue("L5_CUSTODIAN_DETAIL", IssueSeverity.BLOCK, "custodian", "Enter the custodian officer’s name and the date they acknowledged it.", "संरक्षक अधिकारी का नाम और पावती की तारीख़ लिखें।") else null

    fun abhaIssue(address: String, consent: Boolean): RuleIssue? {
        val a = address.trim()
        if (a.isEmpty()) return null
        if (!consent) return issue("ABHA_CONSENT", IssueSeverity.BLOCK, "abha", "Tick consent before linking to ABHA.", "ABHA से जोड़ने से पहले सहमति पर टिक करें।")
        val fine = Regex("^[a-z0-9._-]{4,}@(abdm|sbx)$", RegexOption.IGNORE_CASE).matches(a) || Regex("^\\d{2}-?\\d{4}-?\\d{4}-?\\d{4}$").matches(a)
        return if (fine) null else issue("ABHA_FORMAT", IssueSeverity.BLOCK, "abha", "Enter an ABHA address like name@abdm, or the 14-digit ABHA number.", "ABHA पता जैसे name@abdm, या 14 अंकों का ABHA नंबर लिखें।")
    }

    fun revocationIssues(revokedOn: String, writtenSigned: Boolean, today: LocalDate): List<RuleIssue> = buildList {
        if (!writtenSigned) add(issue("L6_WRITTEN", IssueSeverity.BLOCK, "revocation.written", "Advance care planning is cancelled in writing. Tick that the person signed a written cancellation.", "अग्रिम देखभाल योजना लिखित रूप में रद्द होती है। टिक करें कि व्यक्ति ने लिखित रद्दीकरण पर हस्ताक्षर किए हैं।"))
        val day = PlanDates.day(revokedOn)
        if (day == null) add(issue("REVOKED_ON", IssueSeverity.BLOCK, "revocation.revokedOn", "Enter the date of the written cancellation.", "लिखित रद्दीकरण की तारीख़ लिखें।"))
        else if (day.isAfter(today)) add(issue("REVOKED_FUTURE", IssueSeverity.BLOCK, "revocation.revokedOn", "The date cannot be in the future.", "तारीख़ भविष्य की नहीं हो सकती।"))
    }
    internal fun blocking(issues: List<RuleIssue>) = issues.filter { it.severity == IssueSeverity.BLOCK }
    private fun attest(issues: List<RuleIssue>) { val b = blocking(issues); check(b.isEmpty()) { b.joinToString(" ") { it.message } } }
    internal fun require(issues: List<RuleIssue>) = attest(issues)
}

/** Lifecycle (tech spec §6): DRAFT → GENERATED → EXECUTED; edits make a new version (L6); revoked in writing. */
object AmdLifecycle {
    fun newDraft(patientId: String, now: String, content: AmdContent = AmdContent(), createdBy: String = "", version: Int = 1, id: String = CardTokens.random(8)) =
        AmdRecord(id = id, patientId = patientId, version = version, createdAt = now, createdBy = createdBy, content = content)

    fun latest(records: List<AmdRecord>, patientId: String): AmdRecord? = records.filter { it.patientId == patientId }.maxByOrNull { it.version }
    /** L6: the latest EXECUTED version is the current one. Revoked or superseded versions are never current. */
    fun current(records: List<AmdRecord>, patientId: String): AmdRecord? = records.filter { it.patientId == patientId && it.status == AmdStatus.EXECUTED }.maxByOrNull { it.version }
    /** The version the person is working on, if any. */
    fun working(records: List<AmdRecord>, patientId: String): AmdRecord? = latest(records, patientId)?.takeIf { it.status == AmdStatus.DRAFT || it.status == AmdStatus.GENERATED }

    /** The first draft, or a new version from the latest content. An unsigned printed version is set aside: its paper must not be signed after changes. */
    fun start(records: List<AmdRecord>, patientId: String, actor: String, now: String, executantName: String = "", id: String = CardTokens.random(8)): Pair<List<AmdRecord>, AmdRecord> {
        val last = latest(records, patientId)
        if (last?.status == AmdStatus.DRAFT) return records to last
        val cleaned = records.map { if (it.patientId == patientId && it.status == AmdStatus.GENERATED) it.copy(status = AmdStatus.SUPERSEDED, supersededAt = now, updatedAt = now) else it }
        val base = if (last != null && last.status != AmdStatus.REVOKED) last.content else AmdContent(executant = AmdExecutant(name = executantName))
        val draft = newDraft(patientId, now, base, actor, (last?.version ?: 0) + 1, id)
        return (cleaned + draft) to draft
    }

    /** Only a draft changes. Printed or signed versions are frozen: start a new version. */
    fun update(r: AmdRecord, content: AmdContent, now: String): AmdRecord {
        check(r.status == AmdStatus.DRAFT) { "This version is printed or signed and cannot be changed. Start a new version." }
        return r.copy(content = content, updatedAt = now)
    }

    /** DRAFT → GENERATED once the PDF is made; printing a generated one again only records the newest file hash. */
    fun markGenerated(r: AmdRecord, pdfSha256: String, lang: String, now: String, today: LocalDate): AmdRecord {
        if (r.status == AmdStatus.GENERATED) return r.copy(pdfSha256 = pdfSha256, pdfLang = lang, updatedAt = now)
        check(r.status == AmdStatus.DRAFT) { "Only a draft can be printed for signing." }
        AmdRules.require(AmdRules.validateContent(r.content, today))
        return r.copy(status = AmdStatus.GENERATED, generatedAt = now, updatedAt = now, pdfSha256 = pdfSha256, contentSha256 = contentHash(r), pdfLang = lang)
    }

    /** Copy checklist from the content: every DHR, the family physician and the local-body custodian (L4). */
    fun seedCopies(content: AmdContent, existing: List<AmdCopy> = emptyList()): List<AmdCopy> {
        val wanted = content.dhrs.filter { it.name.isNotBlank() }.map { Triple(AmdRecipientKind.DHR, it.name.trim(), it.id) } +
            listOfNotNull(content.familyPhysician.name.trim().ifBlank { null }?.let { Triple(AmdRecipientKind.FAMILY_PHYSICIAN, it, null) }) +
            Triple(AmdRecipientKind.CUSTODIAN, content.custodianBody.trim().ifBlank { "Local-body custodian" }, null)
        return wanted.map { (kind, name, dhrId) ->
            existing.firstOrNull { it.kind == kind && (kind != AmdRecipientKind.DHR || it.dhrId == dhrId) }?.copy(name = name) ?: AmdCopy(CardTokens.random(8), kind, name, dhrId)
        }
    }

    /** GENERATED → EXECUTED only with the signed scan, two witnesses and the attestor (L3) and a custodian state (L5). Each executed version needs its own copies. */
    fun execute(records: List<AmdRecord>, id: String, execution: AmdExecution, custodian: AmdCustodian?, now: String, today: LocalDate): List<AmdRecord> {
        val r = records.firstOrNull { it.id == id } ?: error("Advance care planning not found.")
        AmdRules.require(AmdRules.validateExecution(r, execution, custodian, today))
        return records.map {
            when {
                it.id == id -> it.copy(status = AmdStatus.EXECUTED, updatedAt = now, execution = execution, custodian = custodian, copies = seedCopies(it.content))
                it.patientId == r.patientId && it.status == AmdStatus.EXECUTED -> it.copy(status = AmdStatus.SUPERSEDED, supersededAt = now, updatedAt = now)
                else -> it
            }
        }
    }

    /** A copy can be marked given only after that recipient's own consent is recorded (DPDP: consent per recipient). */
    fun updateCopy(r: AmdRecord, copyId: String, consent: Boolean? = null, given: Boolean? = null, method: AmdCopyMethod? = null, now: String, today: LocalDate): AmdRecord {
        check(r.status == AmdStatus.EXECUTED) { "Copies can be logged only for the signed version." }
        return r.copy(updatedAt = now, copies = r.copies.map { c ->
            if (c.id != copyId) return@map c
            var next = c.copy(consent = consent ?: c.consent, given = given ?: c.given, method = method ?: c.method)
            if (consent == false) next = next.copy(given = false, givenOn = "", method = null, consentAt = "")
            if (consent == true && !c.consent) next = next.copy(consentAt = now)
            check(!next.given || next.consent) { "Record consent to share with this person before marking the copy as given." }
            if (next.given && next.givenOn.isBlank()) next = next.copy(givenOn = today.toString())
            if (!next.given) next = next.copy(givenOn = "")
            next
        })
    }

    fun updateCustodian(r: AmdRecord, custodian: AmdCustodian, now: String): AmdRecord {
        check(r.status == AmdStatus.EXECUTED) { "Only a signed version has a custodian record." }
        AmdRules.require(listOfNotNull(AmdRules.custodianIssue(custodian)))
        return r.copy(custodian = custodian, updatedAt = now)
    }

    fun updateAbha(r: AmdRecord, address: String, consent: Boolean, now: String, today: LocalDate): AmdRecord {
        check(r.status == AmdStatus.EXECUTED) { "Only a signed version can be linked." }
        AmdRules.require(listOfNotNull(AmdRules.abhaIssue(address, consent)))
        val a = address.trim()
        return r.copy(abha = if (a.isEmpty()) null else AmdAbha(a, true, r.abha?.linkedOn?.ifBlank { null } ?: today.toString()), updatedAt = now)
    }

    /** Revoke in writing (L6): needs the date of the signed written cancellation. Every open version ends. */
    fun revoke(records: List<AmdRecord>, patientId: String, revokedOn: String, writtenSigned: Boolean, reason: String, now: String, today: LocalDate, scanKey: String? = null, scanSha256: String? = null): List<AmdRecord> {
        check(records.any { it.patientId == patientId && it.status in setOf(AmdStatus.EXECUTED, AmdStatus.GENERATED, AmdStatus.DRAFT) }) { "There is no advance care planning to cancel." }
        AmdRules.require(AmdRules.revocationIssues(revokedOn, writtenSigned, today))
        val revocation = AmdRevocation(revokedOn, reason.trim(), now, scanKey, scanSha256)
        return records.map { if (it.patientId == patientId && it.status in setOf(AmdStatus.EXECUTED, AmdStatus.GENERATED, AmdStatus.DRAFT)) it.copy(status = AmdStatus.REVOKED, revocation = revocation, updatedAt = now) else it }
    }

    /** "Delete my local draft": removes versions nobody has signed. EXECUTED, SUPERSEDED and REVOKED stay; a printed paper is not affected. */
    fun withoutUnsigned(records: List<AmdRecord>, patientId: String) = records.partition { !(it.patientId == patientId && (it.status == AmdStatus.DRAFT || it.status == AmdStatus.GENERATED)) }

    /** EXECUTED (current) wins; otherwise the latest state, so the ED can show "cancelled" or "not valid". null = no living will in Sahara. */
    fun statusSummary(records: List<AmdRecord>, patientId: String): AmdStatusSummary? {
        val pick = current(records, patientId) ?: latest(records, patientId) ?: return null
        val attestor = pick.execution?.let { "${if (it.attestor.kind == AttestorKind.NOTARY) "Notary" else "Gazetted officer"}: ${it.attestor.name}" }
        return AmdStatusSummary(pick.status, pick.execution?.signedOn, pick.version, attestor)
    }

    private fun label(d: ExistingDirective?): Bi {
        if (d == null) return NOT_RECORDED
        return when (d.kind) {
            DirectiveKind.NONE -> Bi("No advance care planning", "कोई अग्रिम देखभाल योजना नहीं")
            DirectiveKind.PRIOR_WISHES -> { val note = d.note; if (note.isBlank()) Bi("No AMD; earlier wishes noted", "AMD नहीं; पहले की इच्छाएँ दर्ज") else Bi.labeled(Bi("No AMD; earlier wishes noted", "AMD नहीं; पहले की इच्छाएँ दर्ज"), Bi.plain(note)) }
            DirectiveKind.VALID_AMD -> Bi.join(" · ", listOf(Bi("Valid advance care planning", "वैध अग्रिम देखभाल योजना"), Bi.plain(d.date), Bi.join(" ", listOf(Bi("held at", "रखी है"), if (d.location.isBlank()) NOT_RECORDED else Bi.plain(d.location))), Bi.join(" ", listOf(Bi("attested by", "सत्यापित"), if (d.attestor.isBlank()) NOT_RECORDED else Bi.plain(d.attestor)))))
        }
    }

    /** edAmdLines in prototype/app/ectpr-labels.ts: only EXECUTED counts as valid; "if both exist, the AMD governs". */
    fun edLines(directive: ExistingDirective?, amd: AmdStatusSummary?): Pair<List<Bi>, Boolean> {
        val fromForm = label(directive); val formValid = directive?.kind == DirectiveKind.VALID_AMD
        return when {
            amd?.status == AmdStatus.EXECUTED -> {
                val parts = amd.attestor.orEmpty().split(Regex(":\\s*"), 2)
                val attestor = if (amd.attestor == null) NOT_RECORDED else Bi.labeled(if (parts[0] == "Notary") Bi("Notary", "नोटरी") else Bi("Gazetted officer", "राजपत्रित अधिकारी"), Bi.plain(parts.getOrElse(1) { "" }))
                val version = amd.version; val signed = amd.executedOn ?: "—"
                listOfNotNull(Bi.join(" · ", listOf(Bi("Advance care planning executed in Sahara", "Sahara में निष्पादित अग्रिम देखभाल योजना"), Bi("version $version", "संस्करण $version"), Bi("signed $signed", "हस्ताक्षर $signed"), attestor)),
                    if (formValid) Bi.labeled(Bi("Also on this plan", "इस योजना में भी"), fromForm) else null, AmdText.GOVERNS) to true
            }
            amd?.status == AmdStatus.REVOKED -> { val version = amd.version
                listOf(Bi.join(" · ", listOf(Bi("Advance care planning cancelled in writing", "अग्रिम देखभाल योजना लिखित रूप में रद्द"), Bi("version $version", "संस्करण $version"))),
                    if (formValid) Bi.labeled(Bi("This plan still lists one. Check with the decision-maker", "यह योजना अब भी एक दिखाती है। निर्णयकर्ता से पूछें"), fromForm) else fromForm) to false }
            amd != null -> { val version = amd.version
                listOfNotNull(Bi.join(" · ", listOf(Bi("Advance care planning in Sahara is not signed and attested, so it is not valid", "Sahara की अग्रिम देखभाल योजना पर हस्ताक्षर और सत्यापन नहीं, इसलिए मान्य नहीं"), Bi("version $version", "संस्करण $version"))),
                    fromForm, if (formValid) AmdText.GOVERNS else null) to formValid }
            else -> listOfNotNull(fromForm, if (formValid) AmdText.GOVERNS else null) to formValid
        }
    }

    /** What the ED Quick View says about the living will, always with the L7 caveat beside it. */
    fun edSummary(records: List<AmdRecord>, patientId: String, directive: ExistingDirective? = null): Bi = edSummary(directive, statusSummary(records, patientId))
    fun edSummary(directive: ExistingDirective?, amd: AmdStatusSummary?): Bi = Bi.join(" ", edLines(directive, amd).first + AmdText.CAVEAT)
    fun governs(records: List<AmdRecord>, patientId: String, directive: ExistingDirective?) = edLines(directive, statusSummary(records, patientId)).second

    fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    /** SHA-256 of what the person chose, printed on every PDF page; the file's own SHA-256 is kept in the record. */
    fun contentHash(r: AmdRecord): String = sha256(AmdJson.stable(JSONObject().put("patientId", r.patientId).put("version", r.version).put("formVersion", r.formVersion).put("content", AmdJson.content(r.content))).toByteArray(Charsets.UTF_8))
}

/** JSON in the same shape as AmdVersion in prototype/app/ectpr-model.ts. */
object AmdJson {
    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k)
    private fun JSONObject.ns(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.n(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)
    private inline fun <reified E : Enum<E>> JSONObject.e(k: String): E? = if (isNull(k)) null else enumValues<E>().firstOrNull { it.name == optString(k) }
    private fun list(a: JSONArray?) = a?.let { (0 until it.length()).map(it::getJSONObject) }.orEmpty()

    /** stableStringify in amd-rules.ts: sorted keys, for hashing. */
    fun stable(v: Any?): String = when (v) {
        is JSONObject -> v.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "${JSONObject.quote(it)}:${stable(v.get(it))}" }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { stable(v.get(it)) }
        is String -> JSONObject.quote(v)
        null, JSONObject.NULL -> "null"
        else -> v.toString()
    }

    private fun dhr(d: AmdDhr) = JSONObject().put("id", d.id).put("name", d.name).put("relation", d.relation).put("dob", d.dob).n("idType", d.idType?.name).put("idLast4", d.idLast4).put("phone", d.phone).put("phoneAlt", d.phoneAlt).put("email", d.email).put("permanentAddress", d.permanentAddress).put("currentAddress", d.currentAddress).put("agreed", d.agreed)
    private fun dhr(j: JSONObject) = AmdDhr(j.s("id").ifBlank { CardTokens.random(8) }, j.s("name"), j.s("relation"), j.s("dob"), j.e<IdType>("idType"), j.s("idLast4"), j.s("phone"), j.s("phoneAlt"), j.s("email"), j.s("permanentAddress"), j.s("currentAddress"), j.optBoolean("agreed"))
    private fun witness(w: AmdWitness) = JSONObject().put("name", w.name).put("address", w.address).n("idType", w.idType?.name).put("idLast4", w.idLast4).put("email", w.email).put("phone", w.phone)
    private fun witness(j: JSONObject) = AmdWitness(j.s("name"), j.s("address"), j.e<IdType>("idType"), j.s("idLast4"), j.s("email"), j.s("phone"))

    fun content(c: AmdContent): JSONObject = JSONObject()
        .put("executant", JSONObject().put("name", c.executant.name).put("gender", c.executant.gender).put("dob", c.executant.dob).n("idType", c.executant.idType?.name).put("idLast4", c.executant.idLast4).put("permanentAddress", c.executant.permanentAddress).put("currentAddress", c.executant.currentAddress).put("localBody", c.executant.localBody).put("readOutToMe", c.executant.readOutToMe))
        .put("declarations", JSONObject().put("capacity", c.declarations.capacity).put("consideredCarefully", c.declarations.consideredCarefully).put("voluntary", c.declarations.voluntary).put("understandsNotAppliedIfUnclear", c.declarations.understandsNotAppliedIfUnclear).put("capacityCertificateAttached", c.declarations.capacityCertificateAttached))
        .put("values", c.values).put("conditions", JSONArray(c.conditions.map { it.name })).put("anticipatedConditions", c.anticipatedConditions)
        .put("treatments", JSONObject().also { j -> AmdTreatment.entries.forEach { t -> j.n(t.name, c.treatments[t]?.name) } })
        .put("comfortCareAlways", true).put("comfortOralFeedingAlways", true)
        .put("wishes", JSONObject().n("placeOfCare", c.wishes.placeOfCare?.name).put("institution", c.wishes.institution).put("physician", c.wishes.physician).put("focus", c.wishes.focus).put("preferredName", c.wishes.preferredName).put("genderPronouns", c.wishes.genderPronouns).put("partnerName", c.wishes.partnerName).put("partnerTerm", c.wishes.partnerTerm))
        .put("afterDeath", JSONObject().put("organDonor", c.afterDeath.organDonor).put("bodyDonation", c.afterDeath.bodyDonation).put("bodyDonationInstitution", c.afterDeath.bodyDonationInstitution).put("lastRites", c.afterDeath.lastRites))
        .put("dhrs", JSONArray(c.dhrs.map(::dhr))).put("dhrsActJointly", c.dhrsActJointly)
        .put("familyPhysician", JSONObject().put("name", c.familyPhysician.name).put("registrationNo", c.familyPhysician.registrationNo).put("phone", c.familyPhysician.phone).put("address", c.familyPhysician.address))
        .put("custodianBody", c.custodianBody)

    private fun content(j: JSONObject): AmdContent {
        val x = j.optJSONObject("executant") ?: JSONObject(); val d = j.optJSONObject("declarations") ?: JSONObject(); val w = j.optJSONObject("wishes") ?: JSONObject(); val a = j.optJSONObject("afterDeath") ?: JSONObject(); val f = j.optJSONObject("familyPhysician") ?: JSONObject(); val t = j.optJSONObject("treatments") ?: JSONObject()
        return AmdContent(AmdExecutant(x.s("name"), x.s("gender"), x.s("dob"), x.e<IdType>("idType"), x.s("idLast4"), x.s("permanentAddress"), x.s("currentAddress"), x.s("localBody"), x.optBoolean("readOutToMe")),
            AmdDeclarations(d.optBoolean("capacity"), d.optBoolean("consideredCarefully"), d.optBoolean("voluntary"), d.optBoolean("understandsNotAppliedIfUnclear"), d.optBoolean("capacityCertificateAttached")),
            j.s("values"), j.optJSONArray("conditions")?.let { a2 -> (0 until a2.length()).mapNotNull { i -> AmdCondition.entries.firstOrNull { it.name == a2.optString(i) } } }.orEmpty(), j.s("anticipatedConditions"),
            AmdTreatment.entries.mapNotNull { k -> t.e<AmdChoice>(k.name)?.let { k to it } }.toMap(),
            AmdWishes(w.e<AmdPlaceOfCare>("placeOfCare"), w.s("institution"), w.s("physician"), w.s("focus"), w.s("preferredName"), w.s("genderPronouns"), w.s("partnerName"), w.s("partnerTerm")),
            AmdAfterDeath(a.optBoolean("organDonor"), a.optBoolean("bodyDonation"), a.s("bodyDonationInstitution"), a.s("lastRites")),
            list(j.optJSONArray("dhrs")).map(::dhr).ifEmpty { listOf(AmdDhr()) }, j.optBoolean("dhrsActJointly"),
            AmdFamilyPhysician(f.s("name"), f.s("registrationNo"), f.s("phone"), f.s("address")), j.s("custodianBody"))
    }

    fun encode(r: AmdRecord): JSONObject = JSONObject().put("id", r.id).put("patientId", r.patientId).put("version", r.version).put("formVersion", r.formVersion).put("status", r.status.name)
        .put("createdAt", r.createdAt).put("updatedAt", r.updatedAt).put("createdBy", r.createdBy).put("content", content(r.content))
        .n("generatedAt", r.generatedAt).n("pdfLang", r.pdfLang).n("pdfSha256", r.pdfSha256).n("contentSha256", r.contentSha256)
        .n("execution", r.execution?.let { e -> JSONObject().put("signedOn", e.signedOn).put("place", e.place).put("thumbImpression", e.thumbImpression).put("witnesses", JSONArray(e.witnesses.map(::witness)))
            .put("attestor", JSONObject().n("kind", e.attestor.kind?.name).put("name", e.attestor.name).put("designation", e.attestor.designation).put("registrationNo", e.attestor.registrationNo))
            .put("scanKey", e.scanKey).put("scanName", e.scanName).put("scanType", e.scanType).put("scanSha256", e.scanSha256).put("scanUploadedAt", e.scanUploadedAt) })
        .n("custodian", r.custodian?.let { JSONObject().put("status", it.status.name).put("body", it.body).put("officer", it.officer).put("acknowledgedOn", it.acknowledgedOn) })
        .put("copies", JSONArray(r.copies.map { c -> JSONObject().put("id", c.id).put("kind", c.kind.name).put("name", c.name).n("dhrId", c.dhrId).put("consent", c.consent).put("consentAt", c.consentAt).put("given", c.given).put("givenOn", c.givenOn).n("method", c.method?.name) }))
        .n("abha", r.abha?.let { JSONObject().put("address", it.address).put("consent", it.consent).put("linkedOn", it.linkedOn) })
        .n("supersededAt", r.supersededAt)
        .n("revocation", r.revocation?.let { JSONObject().put("revokedOn", it.revokedOn).put("writtenRevocationSigned", true).put("reason", it.reason).put("recordedAt", it.recordedAt).n("scanKey", it.scanKey).n("scanSha256", it.scanSha256) })

    fun decode(j: JSONObject): AmdRecord {
        if (j.has("maker")) return migrate(j)
        return AmdRecord(id = j.getString("id"), patientId = j.s("patientId"), version = j.optInt("version", 1), formVersion = j.s("formVersion").ifBlank { AMD_FORM_VERSION }, status = j.e<AmdStatus>("status") ?: AmdStatus.DRAFT,
            createdAt = j.s("createdAt"), updatedAt = j.s("updatedAt"), createdBy = j.s("createdBy"), content = content(j.optJSONObject("content") ?: JSONObject()),
            generatedAt = j.ns("generatedAt"), pdfLang = j.ns("pdfLang"), pdfSha256 = j.ns("pdfSha256"), contentSha256 = j.ns("contentSha256"),
            execution = j.optJSONObject("execution")?.let { e -> val a = e.optJSONObject("attestor") ?: JSONObject()
                AmdExecution(e.s("signedOn"), e.s("place"), e.optBoolean("thumbImpression"), list(e.optJSONArray("witnesses")).map(::witness), AmdAttestor(a.e<AttestorKind>("kind"), a.s("name"), a.s("designation"), a.s("registrationNo")), e.s("scanKey"), e.s("scanName"), e.s("scanType"), e.s("scanSha256"), e.s("scanUploadedAt")) },
            custodian = j.optJSONObject("custodian")?.let { AmdCustodian(it.e<AmdCustodianStatus>("status") ?: AmdCustodianStatus.PENDING, it.s("body"), it.s("officer"), it.s("acknowledgedOn")) },
            copies = list(j.optJSONArray("copies")).map { c -> AmdCopy(c.s("id"), c.e<AmdRecipientKind>("kind") ?: AmdRecipientKind.CUSTODIAN, c.s("name"), c.ns("dhrId"), c.optBoolean("consent"), c.s("consentAt"), c.optBoolean("given"), c.s("givenOn"), c.e<AmdCopyMethod>("method")) },
            abha = j.optJSONObject("abha")?.let { AmdAbha(it.s("address"), it.optBoolean("consent"), it.s("linkedOn")) }, supersededAt = j.ns("supersededAt"),
            revocation = j.optJSONObject("revocation")?.let { AmdRevocation(it.s("revokedOn"), it.s("reason"), it.s("recordedAt"), it.ns("scanKey"), it.ns("scanSha256")) })
    }

    /**
     * A record in the earlier, approximated form from a test build. Its people and matching choices carry over, but its paper did not follow the
     * Vidhi text, so it comes back as a draft to be printed and signed again. "Dementia" and the other invented choices are dropped.
     */
    private fun migrate(j: JSONObject): AmdRecord {
        val m = j.optJSONObject("maker") ?: JSONObject(); val a = j.optJSONObject("answers") ?: JSONObject()
        val names = fun(k: String) = a.optJSONArray(k)?.let { x -> (0 until x.length()).map(x::optString) }.orEmpty()
        val conditions = names("situations").mapNotNull { when (it) { "TERMINAL_ILLNESS" -> AmdCondition.TERMINAL; "PERSISTENT_VEGETATIVE_STATE" -> AmdCondition.VEGETATIVE; else -> null } }
        val treatments = names("refusedTreatments").mapNotNull { when (it) { "CPR" -> AmdTreatment.CPR; "MECHANICAL_VENTILATION" -> AmdTreatment.VENTILATION; "DIALYSIS" -> AmdTreatment.DIALYSIS; "TUBE_FEEDING" -> AmdTreatment.TUBE_FEEDING; "IV_ANTIBIOTICS" -> AmdTreatment.IV_FLUIDS_ANTIBIOTICS; else -> null } }.associateWith { AmdChoice.REFUSE }
        val dhrs = list(j.optJSONArray("dhrs")).sortedBy { it.optInt("order") }.map { AmdDhr(name = it.s("name"), relation = it.s("relation"), phone = it.s("phone"), permanentAddress = it.s("address"), idType = it.e<IdType>("idType"), idLast4 = AmdIds.last4(it.s("idLast4"))) }
        val status = j.e<AmdStatus>("status")
        return AmdRecord(id = j.getString("id"), patientId = j.s("patientId"), version = j.optInt("version", 1), status = if (status == AmdStatus.SUPERSEDED || status == AmdStatus.REVOKED) status else AmdStatus.DRAFT, createdAt = j.s("createdAt"), updatedAt = j.s("updatedAt"),
            content = AmdContent(AmdExecutant(name = m.s("name"), gender = m.s("sex"), dob = m.s("dob"), idType = m.e<IdType>("idType"), idLast4 = AmdIds.last4(m.s("idLast4")), permanentAddress = m.s("address")), conditions = conditions, treatments = treatments, dhrs = dhrs.ifEmpty { listOf(AmdDhr()) }))
    }
}

/**
 * The Vidhi model text as used by prototype/app/amd-pdf.ts, built as plain blocks so a unit test can read it and the PDF only draws it.
 * English is the operative text. Headings also appear in the chosen language, as on the web.
 */
sealed interface AmdBlock {
    data class Title(val text: String, val translated: String?) : AmdBlock
    data class Heading(val text: String, val translated: String?) : AmdBlock
    data class Para(val text: String, val bold: Boolean = false, val italic: Boolean = false, val size: Float = 10.5f, val indent: Float = 0f, val prefix: String = "", val after: Float = 7f) : AmdBlock
    data class Table(val rows: List<List<String>>, val widths: List<Float>, val header: List<String>? = null, val minRow: Float = 0f) : AmdBlock
    data class BlankLine(val label: String, val value: String = "") : AmdBlock
    data class Space(val height: Float) : AmdBlock
    data object Rule : AmdBlock
    data object PageBreak : AmdBlock
    data class Stamp(val width: Float, val height: Float) : AmdBlock
}

object VidhiText {
    const val S1_INTRO = "This advance medical directive (living will) and the designation of healthcare representative(s) is made by me:"
    const val FINAL_EXPRESSION = "If and when the time comes that I can no longer participate in decision-making regarding my own health and medical treatment, this directive should be treated as the final expression of my wishes."
    const val VALUES_INTRO = "Why I am making this directive, and the beliefs and values I wish to be respected during decision-making on my behalf:"
    const val PRIMARY_BASIS = "I request that all concerned (including my designated healthcare representative(s), my treating team, and the others involved) must treat my wishes in this document as the primary basis for any decision regarding my medical treatment, particularly decisions relating to life-sustaining treatment."
    const val ATTRIBUTION = "This directive follows the model advance medical directive drafted by Shreyashi Ray, Rituparna Padhy, Dhvani Mehta and Hardik Dua of the Vidhi Centre for Legal Policy, with inputs from Dr Smriti Khanna (P.D. Hinduja Hospital), published with ACP India. Prepared with the Sahara app."
    const val S2_LEAD = "In situations where my treating physician or team have determined that:"
    const val S2_PROLONG = "Any further medical intervention or course of treatment would only serve the purpose of artificially prolonging the process of dying."
    const val S2_DIRECT = "I direct that any life-prolonging medical procedure/treatment be withheld or withdrawn, and the course of natural death be permitted."
    const val S2_ANTICIPATED = "Health conditions that I anticipate, and my directions for them:"
    const val S2_REFUSE = "Specifically, I direct the withholding or withdrawal of the following medical procedures or forms of treatment (I DO NOT WANT):"
    const val S2_ACCEPT = "I direct the following medical procedures or forms of treatment to be provided or continued (I WANT):"
    const val S2_ORAL = "Comfort feeding by mouth, mouth care and hand feeding must always continue for as long as I can take them. Nothing in this directive asks for any act intended to end my life."
    const val S2_UNCLEAR = "I understand that if a situation is ambiguous, or was not anticipated by me in this directive, this directive will not be applied to it, and decisions will be taken as per the guidelines of the Hon’ble Supreme Court for persons without a directive."
    const val END_STAGE_NOTE = "² ‘End-stage condition’ means an irreversible condition that is caused by injury, disease, or illness which has resulted in progressively severe and permanent deterioration, and which, to a reasonable degree of medical probability, treatment of the condition would be ineffective."
    const val VEGETATIVE_NOTE = "³ ‘Vegetative state’ is one where a person is awake but does not show any signs of awareness. A person is unlikely to recover when they have been in a vegetative state for more than 6 months (if caused by a non-traumatic brain injury) or more than 12 months (if caused by a traumatic brain injury). (Source: The National Health Services, UK)"
    const val S4_DISCUSSED = "I have had extensive discussions with those named regarding my wishes, their responsibilities, and the situations which they may be expected to take decisions in. They have expressed that they are willing to be nominated as my “designated healthcare representative(s)”."
    const val S4_POWERS = "I authorise my DHRs to exercise the following powers:"
    const val S4_INFO = "They can obtain medical information from the treating team regarding my treatment, my health records, and any communications with the treating team;"
    const val S4_DECIDE = "They can take decisions and act on my behalf regarding my wishes in this document as necessary. These decisions would include, but are not limited to, providing consent, refusing, or withdrawing consent to any care, treatment, service, or procedure, guided primarily by my wishes as expressed in this directive."
    const val S4_RESPECT = "The decisions taken by my DHRs should be respected regardless of any contrary views that any other person may hold."
    const val S4_NEXT = "If my primary DHR is not available, or unable or unwilling to take a decision, then I authorise the following persons to act as my designated healthcare representative(s) in the stated order of preference:"
    const val ORGAN = "I am a registered organ donor in accordance with the Transplantation of Human Organs and Tissues Act, 1994. My loved ones, including my designated healthcare representative(s), have been informed of my wishes regarding organ donation."
    const val DECL_LEAD = "I declare that at the time of signing and executing this document,"
    const val DECL_READ = "This document has been drafted as per my directions, and its contents have been read out and explained to me."
    const val DECL_THOUGHT = "I have given careful thought and consideration to everything mentioned in this document;"
    const val DECL_WILLING = "I have willingly and voluntarily articulated and consented to everything mentioned in this document, without any coercion, duress, or undue influence from any person or entity."
    const val SC_ORDER = "This directive is executed in accordance with the order dated January 24, 2023, of the Hon’ble Supreme Court of India in MA No. 1699/2019 in WP (C) No. 215/2005 (SC Order). Further, a copy of this directive and healthcare power of attorney has been forwarded to:"
    const val VALID_LIFETIME = "I declare that this directive, and my authorisations for DHRs, shall be valid and remain in force throughout my lifetime, unless I modify or revoke them."
    const val VALID_REVISE = "In any case, I reserve the right to execute a revised version of this document, which makes changes to my directions/wishes, or revoke this document in its entirety. I shall also share copies of the revised version with:"
    const val WITNESS_NOTE = "[Witnesses should preferably be independent, i.e. not a designated healthcare representative named above.]"
    const val WITNESS_RECORD = "I, as a witness, record my satisfaction that the document has been executed voluntarily and without any coercion, duress, inducement, or compulsion."
    const val RECORD_NOTE = "This paper is valid only when signed by hand by the executor before two witnesses and attested by a notary or gazetted officer. Sahara does not sign, e-sign or validate advance medical directives. If any part of this paper is changed by hand, make a new version instead."
    val ALL = listOf(S1_INTRO, FINAL_EXPRESSION, VALUES_INTRO, PRIMARY_BASIS, ATTRIBUTION, S2_LEAD, S2_PROLONG, S2_DIRECT, S2_ANTICIPATED, S2_REFUSE, S2_ACCEPT, S2_ORAL, S2_UNCLEAR, END_STAGE_NOTE, VEGETATIVE_NOTE, S4_DISCUSSED, S4_POWERS, S4_INFO, S4_DECIDE, S4_RESPECT, S4_NEXT, ORGAN, DECL_LEAD, DECL_READ, DECL_THOUGHT, DECL_WILLING, SC_ORDER, VALID_LIFETIME, VALID_REVISE, WITNESS_NOTE, WITNESS_RECORD, RECORD_NOTE)

    /** AMD_TREATMENT_PDF_TEXT in amd-rules.ts: (refuse, accept). */
    fun treatment(t: AmdTreatment) = when (t) {
        AmdTreatment.CPR -> "Cardio-pulmonary resuscitation (CPR)" to "Cardio-pulmonary resuscitation (CPR)"
        AmdTreatment.VENTILATION -> "Ventilation or other kinds of artificial life support" to "Ventilation or other kinds of artificial life support"
        AmdTreatment.DIALYSIS -> "Dialysis" to "Dialysis"
        AmdTreatment.TUBE_FEEDING -> "Artificial administration of nutrition and hydration through means such as intravenous catheters or feeding tubes, if it serves only to artificially prolong the process of dying. [This does not apply to comfort feeding by mouth, mouth care, or nutrition and hydration given for comfort/symptom relief.]" to "Artificial administration of nutrition and hydration through feeding tubes or intravenous catheters"
        AmdTreatment.IV_FLUIDS_ANTIBIOTICS -> "Intravenous fluids and medications including antibiotics; excluding those that would provide comfort/relieve pain and suffering" to "Intravenous fluids and medications, including antibiotics and drugs that will provide comfort or relieve suffering/pain"
        AmdTreatment.CHEMOTHERAPY -> "Chemotherapy" to "Chemotherapy"
        AmdTreatment.RADIOTHERAPY -> "Radiotherapy" to "Radiotherapy"
        AmdTreatment.SURGERY -> "Surgery, unless it is for symptom control or to improve the quality of my life" to "Surgery"
    }
    fun idName(t: IdType) = when (t) { IdType.AADHAAR -> "Aadhaar"; IdType.VOTER_ID -> "Voter ID"; IdType.PAN -> "PAN card"; IdType.PASSPORT -> "Passport"; IdType.DRIVING_LICENCE -> "Driving licence"; IdType.RATION_CARD -> "Ration card"; IdType.OTHER -> "Other ID" }
}

object AmdDocument {
    private const val W = 451f
    private val WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    /** Section headings under the English, for reading only. */
    private fun headings(lang: CareLang): Map<String, String> = listOf(
        "Section 1: Personal details" to "खंड 1: व्यक्तिगत विवरण",
        "Section 2: Directions relating to life-sustaining treatment" to "खंड 2: जीवन-रक्षक उपचार से जुड़े निर्देश",
        "Section 3 (Optional): Wishes or desires during end-of-life care" to "खंड 3 (वैकल्पिक): जीवन के अंतिम समय की देखभाल में इच्छाएँ",
        "Section 4: Authorisation of the Designated Healthcare Representative (DHR)" to "खंड 4: नामित स्वास्थ्य देखभाल प्रतिनिधि (DHR) का प्राधिकार",
        "Section 5 (Optional): Directions to be followed upon my death" to "खंड 5 (वैकल्पिक): मेरी मृत्यु के बाद पालन किए जाने वाले निर्देश",
        "Declaration" to "घोषणा",
        "Validity of directive" to "निर्देश की वैधता",
        "Signature of the executor" to "निर्देश बनाने वाले के हस्ताक्षर",
        "Signature of witnesses" to "गवाहों के हस्ताक्षर",
        "Notarisation / Attestation" to "नोटरी / राजपत्रित अधिकारी द्वारा सत्यापन",
        "ADVANCE MEDICAL DIRECTIVE" to "अग्रिम चिकित्सा निर्देश (लिविंग विल)",
    ).associate { (en, hi) -> en to careTr(lang, en, hi) }
    val TRANSLATION_NOTE = Bi("The English text is the valid text. The headings in this language are only to help reading.", "अंग्रेज़ी पाठ ही मान्य है। हिंदी शीर्षक केवल समझने के लिए हैं।")

    private fun date(iso: String): String = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(iso)?.destructured?.let { (y, m, d) -> "$d/$m/$y" }.orEmpty()
    private fun idLine(type: IdType?, last4: String) = "1. Document name: ${type?.let(VidhiText::idName).orEmpty()}\n2. ID no.: ${if (last4.isNotBlank()) "__________________ (ends $last4)" else ""}"

    /** Every block of the paper, in the order of the Vidhi model. Signature, witness and attestation blocks are always blank. */
    fun blocks(r: AmdRecord, lang: CareLang, contentSha256: String, printedOn: LocalDate): List<AmdBlock> = buildList {
        val c = r.content; val ex = c.executant; val heads = if (lang == CareLang.EN) emptyMap() else headings(lang)
        fun heading(text: String) = add(AmdBlock.Heading(text, heads[text]))
        fun para(text: String, bold: Boolean = false, italic: Boolean = false, size: Float = 10.5f, indent: Float = 0f, prefix: String = "", after: Float = 7f) = add(AmdBlock.Para(text, bold, italic, size, indent, prefix, after))
        fun bullet(text: String) = para(text, indent = 14f, prefix = "•", after = 4f)
        add(AmdBlock.Title("ADVANCE MEDICAL DIRECTIVE", heads["ADVANCE MEDICAL DIRECTIVE"]))
        if (lang != CareLang.EN) para(careTr(lang, TRANSLATION_NOTE.en, TRANSLATION_NOTE.hi), size = 9f)
        heading("Section 1: Personal details")
        para(VidhiText.S1_INTRO)
        add(AmdBlock.Table(listOf(listOf("Full name", ex.name), listOf("Gender", ex.gender), listOf("Date of Birth", date(ex.dob).ifBlank { "DD/MM/YYYY" }), listOf("Government ID", idLine(ex.idType, ex.idLast4)),
            listOf("Full permanent residential address", ex.permanentAddress), listOf("Full current residential address", ex.currentAddress.ifBlank { ex.permanentAddress }), listOf("Municipality/Panchayat", ex.localBody)), listOf(150f, W - 150f), minRow = 18f))
        para(VidhiText.FINAL_EXPRESSION, bold = true, italic = true)
        if (c.values.isNotBlank()) { para(VidhiText.VALUES_INTRO, italic = true, after = 4f); para(c.values.trim(), indent = 14f) }
        para(VidhiText.PRIMARY_BASIS)
        add(AmdBlock.Rule); para("1 " + VidhiText.ATTRIBUTION, size = 8.5f)

        add(AmdBlock.PageBreak)
        heading("Section 2: Directions relating to life-sustaining treatment")
        val names = mapOf(AmdCondition.TERMINAL to "a terminal condition", AmdCondition.END_STAGE to "an end-stage condition²", AmdCondition.VEGETATIVE to "a vegetative state³")
        val chosen = AmdCondition.entries.filter { it in c.conditions }.map { names.getValue(it) }
        val conditionText = if (chosen.size <= 1) chosen.joinToString("") else chosen.dropLast(1).joinToString(", ") + " or " + chosen.last()
        para(VidhiText.S2_LEAD, after = 4f)
        bullet("There is no reasonable medical probability of recovery from $conditionText, and")
        bullet(VidhiText.S2_PROLONG)
        para(VidhiText.S2_DIRECT, bold = true)
        if (c.anticipatedConditions.isNotBlank()) { para(VidhiText.S2_ANTICIPATED, italic = true, after = 4f); para(c.anticipatedConditions.trim(), indent = 14f) }
        val refused = AmdTreatment.entries.filter { c.treatments[it] == AmdChoice.REFUSE }; val accepted = AmdTreatment.entries.filter { c.treatments[it] == AmdChoice.ACCEPT }
        if (refused.isNotEmpty()) { para(VidhiText.S2_REFUSE, after = 4f); refused.forEachIndexed { i, t -> para(VidhiText.treatment(t).first, indent = 14f, prefix = "${i + 1}.", after = 3f) }; add(AmdBlock.Space(5f)) }
        if (accepted.isNotEmpty()) { para(VidhiText.S2_ACCEPT, after = 4f); accepted.forEachIndexed { i, t -> para(VidhiText.treatment(t).second, indent = 14f, prefix = "${i + 1}.", after = 3f) }; add(AmdBlock.Space(5f)) }
        val donation = c.afterDeath.organDonor || c.afterDeath.bodyDonation
        para("Further, even when life-sustaining treatment is withheld/withdrawn, I direct the administration of medication or the performance of medical procedures for providing me with comfort care or to alleviate suffering in the form of pain, distress, or mental confusion${if (donation) " (or to facilitate organ or body donation)" else ""}.")
        para(VidhiText.S2_ORAL)
        para(VidhiText.S2_UNCLEAR)
        if (AmdCondition.END_STAGE in c.conditions || AmdCondition.VEGETATIVE in c.conditions) {
            add(AmdBlock.Rule)
            if (AmdCondition.END_STAGE in c.conditions) para(VidhiText.END_STAGE_NOTE, size = 8.5f, after = 3f)
            if (AmdCondition.VEGETATIVE in c.conditions) para(VidhiText.VEGETATIVE_NOTE, size = 8.5f, after = 3f)
        }

        heading("Section 3 (Optional): Wishes or desires during end-of-life care")
        val w = c.wishes
        val wishes = buildList {
            if (w.placeOfCare == AmdPlaceOfCare.NAMED_INSTITUTION && w.institution.isNotBlank()) add("Subject to feasibility as determined by my “designated healthcare representative(s)”, I wish to stay in ${w.institution.trim()} for the duration of such palliative/comfort care.")
            else w.placeOfCare?.let { p -> add("For the duration of end-of-life care, I wish to stay ${when (p) { AmdPlaceOfCare.HOSPITAL -> "in a hospital or appropriate medical facility"; AmdPlaceOfCare.HOSPICE -> "in a hospice or similar institution"; AmdPlaceOfCare.HOME -> "at home"; AmdPlaceOfCare.NAMED_INSTITUTION -> "in an institution chosen by my designated healthcare representative(s)" }} for the duration of such palliative/comfort care.") }
            if (w.physician.isNotBlank()) add("I would like ${w.physician.trim()} to take care of my healthcare at this stage, subject to availability and feasibility.")
            if (w.focus.isNotBlank()) add("In the course of end-of-life care, I wish for special attention towards: ${w.focus.trim()}")
            if (w.preferredName.isNotBlank() || w.genderPronouns.isNotBlank()) add("Through the course of treatment, I would like my name${if (w.preferredName.isNotBlank()) " (${w.preferredName.trim()})" else ""} and gender${if (w.genderPronouns.isNotBlank()) " (${w.genderPronouns.trim()})" else ""} to be respected by all health workers, caregivers, support staff, and others, including in all documentation and communication.")
            if (w.partnerName.isNotBlank()) add("I would like ${w.partnerName.trim()} to be referred to as ${w.partnerTerm.trim().ifBlank { "my partner" }} in all communication by health workers, caregivers, support staff, and others.")
        }
        if (wishes.isEmpty()) para("[No additional wishes recorded.]", italic = true) else wishes.forEach(::bullet)

        heading("Section 4: Authorisation of the Designated Healthcare Representative (DHR)")
        val dhrs = c.dhrs.filter { it.name.isNotBlank() }
        val count = dhrs.size; val word = WORDS.getOrElse(count) { count.toString() }
        para("In order to ensure that my wishes here are the primary basis for taking any treatment decisions, and to make decisions on my behalf for my medical treatment as may be needed from time to time, I have nominated $word ($count) designated healthcare representative(s) (DHRs) in order of preference below.${if (c.dhrsActJointly && count > 1) " I would like all listed persons to make decisions jointly." else ""}")
        para(VidhiText.S4_DISCUSSED)
        para(VidhiText.S4_POWERS, after = 4f)
        para(VidhiText.S4_INFO, indent = 14f, prefix = "-", after = 4f)
        para(VidhiText.S4_DECIDE, indent = 14f, prefix = "-")
        para(VidhiText.S4_RESPECT)
        fun dhrTable(d: AmdDhr, title: String) = add(AmdBlock.Table(listOf(listOf("a", "Full Name", d.name), listOf("b", "Relationship with the executor", d.relation), listOf("c", "Date of Birth", date(d.dob).ifBlank { "DD/MM/YYYY" }), listOf("d", "Government ID", idLine(d.idType, d.idLast4)),
            listOf("e", "Mobile Number(s)", "1. Primary: ${d.phone}\n2. Any other: ${d.phoneAlt}"), listOf("f", "Email ID(s)", "1. Primary: ${d.email}"), listOf("g", "Permanent Residential Address", d.permanentAddress), listOf("h", "Residential Address on the date of signing this document", d.currentAddress.ifBlank { d.permanentAddress })),
            listOf(40f, 140f, W - 180f), listOf("Sr No", "Particulars", title), 16f))
        dhrs.firstOrNull()?.let { para("I authorise the following person as my primary DHR", bold = true, after = 4f); dhrTable(it, "Information") }
        if (dhrs.size > 1) { para(VidhiText.S4_NEXT); dhrs.drop(1).forEachIndexed { i, d -> para("${i + 1}.", indent = 14f, after = 4f); dhrTable(d, "Details") } }

        heading("Section 5 (Optional): Directions to be followed upon my death")
        val after = c.afterDeath
        val last = buildList {
            if (after.organDonor) add(VidhiText.ORGAN)
            if (after.bodyDonation) add("I wish for my body to be donated for the purpose of medical research and education.${if (after.bodyDonationInstitution.isNotBlank()) " In this regard, I have communicated my wish to ${after.bodyDonationInstitution.trim()}." else ""} My loved ones, including my designated healthcare representative(s), have been informed of my wishes regarding body donation.")
            if (after.lastRites.isNotBlank()) add("My wishes about last rites: ${after.lastRites.trim()}")
        }
        if (last.isEmpty()) para("[No directions recorded.]", italic = true) else last.forEach(::bullet)

        heading("Declaration")
        para(VidhiText.DECL_LEAD, after = 4f)
        if (ex.readOutToMe) bullet(VidhiText.DECL_READ)
        bullet("I have the capacity and competence to understand the meaning and implications of everything mentioned in this document${if (c.declarations.capacityCertificateAttached) ". A certificate of capacity and competence from a registered medical practitioner is attached" else ""};")
        bullet(VidhiText.DECL_THOUGHT)
        bullet(VidhiText.DECL_WILLING)
        val custodian = c.custodianBody.trim().ifBlank { "______________________ (panchayat, municipality, district administration, municipal corporation, etc.)" }
        fun copies() {
            bullet("designated healthcare representative(s) (DHRs) mentioned in this directive${if (dhrs.isNotEmpty()) ": ${dhrs.joinToString(", ") { it.name.trim() }}" else ""}")
            if (c.familyPhysician.name.isNotBlank()) bullet("my family/chosen physician: ${c.familyPhysician.name.trim()}${if (c.familyPhysician.registrationNo.isNotBlank()) " (Reg. No. ${c.familyPhysician.registrationNo.trim()})" else ""}")
            bullet("the Competent officer/custodian of $custodian")
        }
        para(VidhiText.SC_ORDER, after = 4f); copies()
        heading("Validity of directive")
        para(VidhiText.VALID_LIFETIME)
        para(VidhiText.VALID_REVISE, after = 4f); copies()

        heading("Signature of the executor")
        add(AmdBlock.Space(40f))
        para("[Full signature or Thumb impression]")
        add(AmdBlock.BlankLine("Full name:", ex.name)); add(AmdBlock.BlankLine("Date:")); add(AmdBlock.BlankLine("Place:"))
        heading("Signature of witnesses")
        para(VidhiText.WITNESS_NOTE, italic = true, size = 9.5f)
        listOf(1, 2).forEach { n ->
            para(VidhiText.WITNESS_RECORD, indent = 14f, prefix = "$n.", after = 5f)
            add(AmdBlock.Table(listOf(listOf("Name:\nAddress:\n\nGovernment ID:\nEmail ID:\nMobile No.:", "")), listOf(W / 2, W / 2), listOf("Witness $n", "Signature")))
        }
        add(AmdBlock.BlankLine("Date:")); add(AmdBlock.BlankLine("Place:"))

        add(AmdBlock.PageBreak)
        heading("Notarisation / Attestation")
        para("This directive and authorisation of designated healthcare representative(s) has been signed in the presence of the undersigned by ${ex.name.ifBlank { "______________________" }} (Declarant) and I record my satisfaction that the document has been executed voluntarily and without any coercion or inducement or compulsion.")
        add(AmdBlock.Space(8f))
        para("SIGNED BEFORE ME", bold = true, after = 46f)
        para("(Full name of notary / Gazetted Officer)")
        add(AmdBlock.BlankLine("Notary registration no. / Designation and office:"))
        add(AmdBlock.Space(10f))
        para("APPROPRIATE AUTHORITY, STAMP", bold = true, after = 6f)
        add(AmdBlock.Stamp(200f, 90f))
        add(AmdBlock.BlankLine("Date:")); add(AmdBlock.BlankLine("Place:"))
        add(AmdBlock.Space(14f)); add(AmdBlock.Rule)
        para("Document record — Sahara AMD version ${r.version} · ${r.formVersion} · printed ${date(printedOn.toString())}", size = 8.5f, after = 2f)
        para("Content SHA-256: $contentSha256", size = 8.5f, after = 2f)
        para(VidhiText.RECORD_NOTE, size = 8.5f, after = 2f)
    }

    /** Every word of the paper as plain text, for tests. */
    fun plainText(blocks: List<AmdBlock>): String = blocks.joinToString("\n") { b -> when (b) {
        is AmdBlock.Title -> b.text; is AmdBlock.Heading -> b.text; is AmdBlock.Para -> b.text; is AmdBlock.BlankLine -> b.label + " " + b.value
        is AmdBlock.Table -> (listOfNotNull(b.header) + b.rows).joinToString("\n") { it.joinToString(" | ") }
        else -> ""
    } }
}
