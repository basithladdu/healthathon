package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * Living will / Advance Medical Directive (AMD), Vidhi Centre – ACP India model template.
 * Legal rules from Common Cause (2018) as modified on 24 Jan 2023, applied in Harish Rana (11 Mar 2026): tech spec §2, L1–L9.
 * The app only prepares the document. It becomes valid as signed paper before two witnesses, attested by a notary or gazetted
 * officer (L3); it is never validated by e-signature, and the app never withdraws treatment (L8).
 */

const val AMD_FORM_VERSION = "Sahara AMD v1.0 (Vidhi–ACP India model)"

enum class AmdStatus { DRAFT, GENERATED, EXECUTED, SUPERSEDED, REVOKED }
/** The situations in which the directive applies (Vidhi template, part A). */
enum class AmdSituation { TERMINAL_ILLNESS, PERSISTENT_VEGETATIVE_STATE, PERMANENT_UNCONSCIOUSNESS, ADVANCED_IRREVERSIBLE_DEMENTIA }
/** Life-prolonging treatments the maker may refuse (part B). Tube feeding is medical treatment (Harish Rana, L9). */
enum class AmdTreatment { CPR, MECHANICAL_VENTILATION, DIALYSIS, TUBE_FEEDING, ICU_ADMISSION, VASOPRESSORS, BLOOD_TRANSFUSION, IV_ANTIBIOTICS }
enum class PlaceOfCare { HOME, HOSPITAL, HOSPICE, NO_PREFERENCE }
enum class AttestorKind { NOTARY, GAZETTED_OFFICER }

data class AmdMaker(val name: String = "", val dob: String = "", val sex: String = "", val address: String = "", val phone: String = "", val idType: IdType? = null, val idLast4: String? = null)
/** A named representative / health-care decision-maker (DHR), in preference order. */
data class Dhr(val name: String = "", val relation: String = "", val phone: String = "", val address: String = "", val idType: IdType? = null, val idLast4: String? = null)
data class AmdWitness(val name: String = "", val address: String = "", val signedOn: String = "")
data class AmdAttestor(val kind: AttestorKind? = null, val name: String = "", val registrationOrDesignation: String = "", val place: String = "", val attestedOn: String = "")
/** The signed paper, photographed. [sha256] lets a doctor check the copy they see is the one recorded (L5). */
data class AmdScan(val fileName: String, val sha256: String, val recordedAt: String)
data class AmdAnswers(
    val situations: List<AmdSituation> = emptyList(),
    val refusedTreatments: List<AmdTreatment> = emptyList(),
    val placeOfCare: PlaceOfCare? = null,
    val religiousWishes: String = "",
    val otherWishes: String = "",
    val organDonation: Boolean? = null,
)
data class AmdRecord(
    val id: String, val patientId: String, val version: Int = 1, val formVersion: String = AMD_FORM_VERSION, val status: AmdStatus = AmdStatus.DRAFT,
    val createdAt: String, val updatedAt: String = createdAt,
    val maker: AmdMaker = AmdMaker(),
    /** L1: "I am an adult of sound mind and make this directive voluntarily." */
    val soundMindDeclaration: Boolean = false,
    val answers: AmdAnswers = AmdAnswers(),
    val dhrs: List<Dhr> = emptyList(),
    val witnesses: List<AmdWitness> = emptyList(),
    val attestor: AmdAttestor? = null,
    val scan: AmdScan? = null,
    val generatedAt: String? = null, val executedAt: String? = null,
    val revokedAt: String? = null, val revokedReason: String? = null,
    val supersedes: String? = null,
    /** Few states have appointed custodians yet (tech spec §2, decided): allowed to stay pending. */
    val custodianPending: Boolean = true,
)

object AmdText {
    fun situation(s: AmdSituation) = when (s) {
        AmdSituation.TERMINAL_ILLNESS -> Bi("I have a terminal illness with no reasonable hope of recovery", "मुझे ऐसी आख़िरी अवस्था की बीमारी है जिसमें ठीक होने की कोई उचित उम्मीद नहीं")
        AmdSituation.PERSISTENT_VEGETATIVE_STATE -> Bi("I am in a persistent vegetative state", "मैं लगातार अचेत-सी (वेजिटेटिव) अवस्था में हूँ")
        AmdSituation.PERMANENT_UNCONSCIOUSNESS -> Bi("I am permanently unconscious, with no reasonable hope of waking", "मैं स्थायी रूप से बेहोश हूँ, होश आने की कोई उचित उम्मीद नहीं")
        AmdSituation.ADVANCED_IRREVERSIBLE_DEMENTIA -> Bi("I have advanced, irreversible loss of mind (such as late dementia) and cannot decide", "मेरी समझ पूरी तरह और हमेशा के लिए चली गई है (जैसे आख़िरी अवस्था का डिमेंशिया) और मैं फ़ैसला नहीं ले सकता/सकती")
    }
    fun treatment(t: AmdTreatment) = when (t) {
        AmdTreatment.CPR -> Bi("CPR (restarting the heart)", "CPR (दिल फिर चालू करना)")
        AmdTreatment.MECHANICAL_VENTILATION -> Bi("A breathing machine (ventilator)", "साँस की मशीन (वेंटिलेटर)")
        AmdTreatment.DIALYSIS -> Bi("Dialysis", "डायलिसिस")
        AmdTreatment.TUBE_FEEDING -> Bi("Feeding through a tube (nose or stomach)", "नली से खाना (नाक या पेट से)")
        AmdTreatment.ICU_ADMISSION -> Bi("Admission to intensive care (ICU)", "ICU में भर्ती")
        AmdTreatment.VASOPRESSORS -> Bi("Drugs to keep up blood pressure", "BP बनाए रखने की दवाएँ")
        AmdTreatment.BLOOD_TRANSFUSION -> Bi("Blood transfusion", "ख़ून चढ़ाना")
        AmdTreatment.IV_ANTIBIOTICS -> Bi("Antibiotics through a vein to prolong life", "जीवन लंबा करने के लिए नस से एंटीबायोटिक")
    }
    fun place(p: PlaceOfCare) = when (p) { PlaceOfCare.HOME -> Bi("At home", "घर पर"); PlaceOfCare.HOSPITAL -> Bi("In hospital", "अस्पताल में"); PlaceOfCare.HOSPICE -> Bi("In a hospice", "हॉस्पिस में"); PlaceOfCare.NO_PREFERENCE -> Bi("No preference", "कोई पसंद नहीं") }
    fun attestor(a: AttestorKind) = when (a) { AttestorKind.NOTARY -> Bi("Notary", "नोटरी"); AttestorKind.GAZETTED_OFFICER -> Bi("Gazetted officer", "राजपत्रित अधिकारी") }
    fun status(s: AmdStatus) = when (s) {
        AmdStatus.DRAFT -> Bi("Draft", "मसौदा"); AmdStatus.GENERATED -> Bi("Generated — not yet signed and attested", "बन गया — अभी हस्ताक्षर और सत्यापन नहीं")
        AmdStatus.EXECUTED -> Bi("Executed — signed, witnessed and attested", "लागू — हस्ताक्षर, गवाह और सत्यापन हो गया"); AmdStatus.SUPERSEDED -> Bi("Replaced by a newer version", "नए संस्करण से बदला गया"); AmdStatus.REVOKED -> Bi("Revoked", "रद्द")
    }
    val COMFORT = Bi("Whatever I refuse, I want care for my comfort: pain and symptom relief, mouth care, and food and drink by mouth if I want them.", "मैं जो भी इलाज मना करूँ, मुझे आराम की देखभाल चाहिए: दर्द और तकलीफ़ से राहत, मुँह की सफ़ाई, और चाहूँ तो मुँह से खाना-पीना।")
    val TUBE_NOTE = Bi("Tube feeding is a medical treatment. Comfort feeding by mouth always continues.", "नली से खाना एक चिकित्सा इलाज है। मुँह से आराम के लिए खिलाना हमेशा जारी रहता है।")
    /** L7: an ambiguous or unanticipated situation is not covered; the directive is never applied automatically. */
    val CAVEAT = Bi("A living will applies only to the situations it describes. If the situation is unclear or was not anticipated, it is not applied — treat as usual and contact the named representatives. Stopping treatment that is already running needs the medical boards (never this app).",
        "लिविंग विल सिर्फ़ लिखी गई स्थितियों पर लागू होती है। अगर स्थिति साफ़ नहीं या पहले सोची नहीं गई थी, तो यह लागू नहीं होती — सामान्य इलाज करें और नामित प्रतिनिधियों से बात करें। चल रहा इलाज बंद करने के लिए मेडिकल बोर्ड ज़रूरी हैं (यह ऐप कभी नहीं)।")
    val PAPER = Bi("This app only prepares the document. It is valid only when you sign the paper before two witnesses and a notary or gazetted officer attests it.", "यह ऐप सिर्फ़ दस्तावेज़ तैयार करता है। यह तभी मान्य है जब आप दो गवाहों के सामने काग़ज़ पर हस्ताक्षर करें और नोटरी या राजपत्रित अधिकारी इसे सत्यापित करें।")
}

object AmdRules {
    private fun issue(id: String, severity: IssueSeverity, field: String, en: String, hi: String) = RuleIssue(id, severity, field, en, hi)
    private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Checks before the document can be generated (Draft → Generated). */
    fun generateIssues(r: AmdRecord, today: LocalDate): List<RuleIssue> = buildList {
        val age = PlanDates.age(r.maker.dob, today)
        if (age == null) add(issue("L1_DOB", IssueSeverity.BLOCK, "maker.dob", "Enter the date of birth: a living will can be made only by an adult.", "जन्मतिथि लिखें: लिविंग विल सिर्फ़ वयस्क बना सकते हैं।"))
        else if (age < 18) add(issue("L1", IssueSeverity.BLOCK, "maker.dob", "A living will can be made only by an adult (18 or over).", "लिविंग विल सिर्फ़ वयस्क (18 या उससे ज़्यादा) बना सकते हैं।"))
        if (!r.soundMindDeclaration) add(issue("L1_CAPACITY", IssueSeverity.BLOCK, "soundMindDeclaration", "Confirm you are of sound mind and making this choice freely.", "पुष्टि करें कि आप पूरे होश में और अपनी मर्ज़ी से यह फ़ैसला ले रहे हैं।"))
        if (r.maker.name.isBlank()) add(issue("MAKER_NAME", IssueSeverity.BLOCK, "maker.name", "Enter your full name.", "अपना पूरा नाम लिखें।"))
        if (!PlanIds.valid(r.maker.idLast4) || r.dhrs.any { !PlanIds.valid(it.idLast4) }) add(issue("ID_LAST4", IssueSeverity.BLOCK, "idLast4", "Keep only the last 4 characters of an ID, never the full number.", "पहचान-पत्र के सिर्फ़ आख़िरी 4 अंक रखें, पूरा नंबर कभी नहीं।"))
        if (r.answers.situations.isEmpty()) add(issue("SITUATIONS", IssueSeverity.BLOCK, "answers.situations", "Choose at least one situation in which this directive applies.", "कम से कम एक स्थिति चुनें जिसमें यह निर्देश लागू हो।"))
        if (r.answers.refusedTreatments.isEmpty()) add(issue("TREATMENTS", IssueSeverity.BLOCK, "answers.refusedTreatments", "Choose at least one treatment you do not want in those situations.", "कम से कम एक इलाज चुनें जो आप उन स्थितियों में नहीं चाहते।"))
        // L2: at least one named representative, two recommended.
        val named = r.dhrs.filter { it.name.isNotBlank() }
        if (named.isEmpty()) add(issue("L2", IssueSeverity.BLOCK, "dhrs", "Name at least one person to make decisions for you (a guardian or close relative).", "कम से कम एक व्यक्ति का नाम दें जो आपके लिए फ़ैसले ले (अभिभावक या क़रीबी रिश्तेदार)।"))
        else if (named.size < 2) add(issue("L2_TWO", IssueSeverity.WARN, "dhrs", "Naming a second person is recommended, in case the first cannot be reached.", "दूसरा नाम देना अच्छा है, अगर पहले व्यक्ति से संपर्क न हो सके।"))
        if (named.any { it.phone.isBlank() }) add(issue("DHR_PHONE", IssueSeverity.WARN, "dhrs", "Add a phone number for each named person.", "हर नामित व्यक्ति का फ़ोन नंबर जोड़ें।"))
    }

    /** Checks before the signed paper can be recorded as executed (Generated → Executed, L3). */
    fun executeIssues(r: AmdRecord, scan: AmdScan?, witnesses: List<AmdWitness>, attestor: AmdAttestor?): List<RuleIssue> = buildList {
        if (r.status != AmdStatus.GENERATED) add(issue("NOT_GENERATED", IssueSeverity.BLOCK, "status", "Generate and print the document first.", "पहले दस्तावेज़ बनाकर प्रिंट करें।"))
        if (scan == null || !Regex("^[a-f0-9]{64}$").matches(scan.sha256)) add(issue("L3_SCAN", IssueSeverity.BLOCK, "scan", "Add a photo of the signed paper.", "हस्ताक्षर किए काग़ज़ की फ़ोटो जोड़ें।"))
        val named = witnesses.filter { it.name.isNotBlank() }
        if (named.size < 2) add(issue("L3_WITNESSES", IssueSeverity.BLOCK, "witnesses", "Record both witnesses who saw you sign.", "दोनों गवाहों के नाम लिखें जिन्होंने आपको हस्ताक्षर करते देखा।"))
        if (named.size >= 2 && norm(named[0].name) == norm(named[1].name)) add(issue("L3_SAME_WITNESS", IssueSeverity.BLOCK, "witnesses", "The two witnesses must be different people.", "दोनों गवाह अलग-अलग व्यक्ति होने चाहिए।"))
        if (attestor?.kind == null || attestor.name.isBlank() || attestor.attestedOn.isBlank()) add(issue("L3_ATTESTOR", IssueSeverity.BLOCK, "attestor", "Record the notary or gazetted officer who attested it, and the date.", "सत्यापित करने वाले नोटरी या राजपत्रित अधिकारी और तारीख़ लिखें।"))
        val dhrNames = r.dhrs.map { norm(it.name) }.filter(String::isNotBlank).toSet()
        if (named.any { norm(it.name) in dhrNames }) add(issue("L3_WITNESS_IS_DHR", IssueSeverity.WARN, "witnesses", "A witness is also a named representative. Independent witnesses are preferred.", "एक गवाह नामित प्रतिनिधि भी है। स्वतंत्र गवाह बेहतर हैं।"))
    }
}

object AmdLifecycle {
    fun newDraft(patientId: String, now: String, maker: AmdMaker = AmdMaker(), id: String = CardTokens.random(8)) = AmdRecord(id = id, patientId = patientId, createdAt = now, maker = maker)

    /** Drafts change freely; a Generated document returns to Draft when edited (it has not been signed yet). An Executed one needs [newVersion] (L6). */
    fun edit(r: AmdRecord, now: String, change: (AmdRecord) -> AmdRecord): AmdRecord {
        check(r.status == AmdStatus.DRAFT || r.status == AmdStatus.GENERATED) { "A signed living will is not edited. Make a new version and sign it again." }
        val next = change(r)
        check(next.id == r.id && next.version == r.version) { "Only the content can change." }
        return next.copy(status = AmdStatus.DRAFT, generatedAt = null, updatedAt = now)
    }

    fun generate(r: AmdRecord, now: String, today: LocalDate): AmdRecord {
        check(r.status == AmdStatus.DRAFT) { "Only a draft can be generated." }
        val blockers = AmdRules.generateIssues(r, today).filter { it.severity != IssueSeverity.WARN }
        check(blockers.isEmpty()) { blockers.joinToString(" ") { it.message } }
        return r.copy(status = AmdStatus.GENERATED, generatedAt = now, updatedAt = now)
    }

    /** L3: Executed only once the signed scan, both witnesses and the attestor are recorded. The latest executed version is current (L6). */
    fun execute(records: List<AmdRecord>, id: String, scan: AmdScan, witnesses: List<AmdWitness>, attestor: AmdAttestor, now: String): List<AmdRecord> {
        val r = records.firstOrNull { it.id == id } ?: error("Living will not found.")
        val blockers = AmdRules.executeIssues(r, scan, witnesses, attestor).filter { it.severity != IssueSeverity.WARN }
        check(blockers.isEmpty()) { blockers.joinToString(" ") { it.message } }
        return records.map {
            when {
                it.id == id -> it.copy(status = AmdStatus.EXECUTED, scan = scan, witnesses = witnesses.filter { w -> w.name.isNotBlank() }, attestor = attestor, executedAt = now, updatedAt = now)
                it.patientId == r.patientId && it.status == AmdStatus.EXECUTED -> it.copy(status = AmdStatus.SUPERSEDED, updatedAt = now)
                else -> it
            }
        }
    }

    /** L6: a change is a new version that must be signed and attested again; the executed one stays current until then. */
    fun newVersion(records: List<AmdRecord>, from: AmdRecord, now: String, id: String = CardTokens.random(8)): AmdRecord {
        require(records.none { it.patientId == from.patientId && (it.status == AmdStatus.DRAFT || it.status == AmdStatus.GENERATED) }) { "Finish the living will already in progress first." }
        return from.copy(id = id, version = (records.filter { it.patientId == from.patientId }.maxOfOrNull { it.version } ?: from.version) + 1, status = AmdStatus.DRAFT,
            createdAt = now, updatedAt = now, witnesses = emptyList(), attestor = null, scan = null, generatedAt = null, executedAt = null, revokedAt = null, revokedReason = null, supersedes = from.id, soundMindDeclaration = false)
    }

    /** L6: revocable at any time, in writing. */
    fun revoke(records: List<AmdRecord>, id: String, reason: String, now: String): List<AmdRecord> {
        require(reason.isNotBlank()) { "Give the reason for revoking." }
        val r = records.firstOrNull { it.id == id } ?: error("Living will not found.")
        check(r.status == AmdStatus.EXECUTED || r.status == AmdStatus.GENERATED || r.status == AmdStatus.DRAFT) { "This version is no longer current." }
        return records.map { if (it.id == id) it.copy(status = AmdStatus.REVOKED, revokedAt = now, revokedReason = reason, updatedAt = now) else it }
    }

    fun current(records: List<AmdRecord>, patientId: String): AmdRecord? = records.filter { it.patientId == patientId && it.status == AmdStatus.EXECUTED }.maxByOrNull { it.version }
    fun working(records: List<AmdRecord>, patientId: String): AmdRecord? = records.filter { it.patientId == patientId && (it.status == AmdStatus.DRAFT || it.status == AmdStatus.GENERATED) }.maxByOrNull { it.version }

    /** What the ED Quick View says about the living will. Only an executed one counts, always with the L7 caveat. */
    fun edSummary(records: List<AmdRecord>, patientId: String): Bi {
        val current = current(records, patientId)
        if (current != null) {
            val refused = current.answers.refusedTreatments
            return Bi("Executed living will v${current.version} (${current.executedAt?.take(10)}): refuses ${refused.joinToString(", ") { AmdText.treatment(it).en.lowercase() }} in ${current.answers.situations.joinToString(" or ") { AmdText.situation(it).en.removePrefix("I have ").removePrefix("I am ").lowercase() }}. " +
                "Representative: ${current.dhrs.firstOrNull()?.let { "${it.name} ${it.phone}".trim() } ?: "—"}. The living will governs over this plan. ${AmdText.CAVEAT.en}",
                "लागू लिविंग विल v${current.version} (${current.executedAt?.take(10)}): ${refused.joinToString(", ") { AmdText.treatment(it).hi }} मना। प्रतिनिधि: ${current.dhrs.firstOrNull()?.let { "${it.name} ${it.phone}".trim() } ?: "—"}। लिविंग विल इस योजना से ऊपर है। ${AmdText.CAVEAT.hi}")
        }
        val working = working(records, patientId)
        return if (working != null) Bi("Living will in progress (${AmdText.status(working.status).en}) — not valid until signed, witnessed and attested.", "लिविंग विल तैयार हो रही है (${AmdText.status(working.status).hi}) — हस्ताक्षर, गवाह और सत्यापन से पहले मान्य नहीं।")
        else Bi("No living will recorded", "कोई लिविंग विल दर्ज नहीं")
    }

    fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}

object AmdJson {
    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k)
    private fun JSONObject.ns(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.n(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)
    private inline fun <reified E : Enum<E>> JSONObject.e(k: String): E? = if (isNull(k)) null else enumValues<E>().firstOrNull { it.name == optString(k) }
    private inline fun <reified E : Enum<E>> JSONObject.es(k: String): List<E> = optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { i -> enumValues<E>().firstOrNull { it.name == a.optString(i) } } }.orEmpty()
    private fun list(a: JSONArray?) = a?.let { (0 until it.length()).map(it::getJSONObject) }.orEmpty()

    fun encode(r: AmdRecord): JSONObject = JSONObject().put("id", r.id).put("patientId", r.patientId).put("version", r.version).put("formVersion", r.formVersion).put("status", r.status.name)
        .put("createdAt", r.createdAt).put("updatedAt", r.updatedAt)
        .put("maker", JSONObject().put("name", r.maker.name).put("dob", r.maker.dob).put("sex", r.maker.sex).put("address", r.maker.address).put("phone", r.maker.phone).n("idType", r.maker.idType?.name).n("idLast4", r.maker.idLast4))
        .put("soundMindDeclaration", r.soundMindDeclaration)
        .put("answers", JSONObject().put("situations", JSONArray(r.answers.situations.map { it.name })).put("refusedTreatments", JSONArray(r.answers.refusedTreatments.map { it.name }))
            .n("placeOfCare", r.answers.placeOfCare?.name).put("religiousWishes", r.answers.religiousWishes).put("otherWishes", r.answers.otherWishes).n("organDonation", r.answers.organDonation))
        .put("dhrs", JSONArray(r.dhrs.mapIndexed { i, d -> JSONObject().put("order", i + 1).put("name", d.name).put("relation", d.relation).put("phone", d.phone).put("address", d.address).n("idType", d.idType?.name).n("idLast4", d.idLast4) }))
        .put("witnesses", JSONArray(r.witnesses.map { JSONObject().put("name", it.name).put("address", it.address).put("signedOn", it.signedOn) }))
        .n("attestor", r.attestor?.let { JSONObject().n("kind", it.kind?.name).put("name", it.name).put("registrationOrDesignation", it.registrationOrDesignation).put("place", it.place).put("attestedOn", it.attestedOn) })
        .n("scan", r.scan?.let { JSONObject().put("fileName", it.fileName).put("sha256", it.sha256).put("recordedAt", it.recordedAt) })
        .n("generatedAt", r.generatedAt).n("executedAt", r.executedAt).n("revokedAt", r.revokedAt).n("revokedReason", r.revokedReason).n("supersedes", r.supersedes).put("custodianPending", r.custodianPending)

    fun decode(j: JSONObject): AmdRecord {
        val m = j.optJSONObject("maker") ?: JSONObject(); val a = j.optJSONObject("answers") ?: JSONObject()
        return AmdRecord(id = j.getString("id"), patientId = j.s("patientId"), version = j.optInt("version", 1), formVersion = j.s("formVersion").ifBlank { AMD_FORM_VERSION }, status = j.e<AmdStatus>("status") ?: AmdStatus.DRAFT,
            createdAt = j.s("createdAt"), updatedAt = j.s("updatedAt"),
            maker = AmdMaker(m.s("name"), m.s("dob"), m.s("sex"), m.s("address"), m.s("phone"), m.e<IdType>("idType"), m.ns("idLast4")),
            soundMindDeclaration = j.optBoolean("soundMindDeclaration"),
            answers = AmdAnswers(a.es("situations"), a.es("refusedTreatments"), a.e<PlaceOfCare>("placeOfCare"), a.s("religiousWishes"), a.s("otherWishes"), if (a.isNull("organDonation")) null else a.optBoolean("organDonation")),
            dhrs = list(j.optJSONArray("dhrs")).sortedBy { it.optInt("order") }.map { Dhr(it.s("name"), it.s("relation"), it.s("phone"), it.s("address"), it.e<IdType>("idType"), it.ns("idLast4")) },
            witnesses = list(j.optJSONArray("witnesses")).map { AmdWitness(it.s("name"), it.s("address"), it.s("signedOn")) },
            attestor = j.optJSONObject("attestor")?.let { AmdAttestor(it.e<AttestorKind>("kind"), it.s("name"), it.s("registrationOrDesignation"), it.s("place"), it.s("attestedOn")) },
            scan = j.optJSONObject("scan")?.let { AmdScan(it.s("fileName"), it.s("sha256"), it.s("recordedAt")) },
            generatedAt = j.ns("generatedAt"), executedAt = j.ns("executedAt"), revokedAt = j.ns("revokedAt"), revokedReason = j.ns("revokedReason"), supersedes = j.ns("supersedes"), custodianPending = j.optBoolean("custodianPending", true))
    }
}
