package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Original personal diary. It does not administer or score ESAS-r or IPOS. */
data class SymptomNote(
    val symptom: String = "", val rating: Int? = null, val observedDate: String = "", val observedTime: String = "",
    val recall: String = "now", val respondent: String = "", val author: String = "", val startedOn: String = "",
    val note: String = "", val painLocation: String = "", val painTriggers: List<String> = emptyList(),
    val selfReportedChange: String = "", val comparedWithId: String = "", val scale: String = "sahara-personal-nrs-v1",
    val painBodySites: List<String> = emptyList(),
    val painBodyPoints: List<PainBodyPoint3d> = emptyList(),
    val painBodyModel: String = "",
    /** Optional per-spot strength, site id to 0-10 (one spot per site, with or without an exact point). Empty on older notes, which fall back to [rating]. */
    val painBodyRatings: Map<String, Int> = emptyMap(),
)

data class SymptomComparison(val baseline: CareItem, val previous: CareItem, val difference: Int)

object SymptomDiaryRules {
    const val SAVE_CONFLICT = "This note changed or was removed. Your answers are still here. Close this form and reopen the current note before saving."
    const val PERSONAL_SCALE = "sahara-personal-nrs-v1"
    const val WELLBEING_SCALE = "sahara-personal-wellbeing-nrs-v1"
    // Keys are stored identifiers. Changing a visible label must not rename earlier records.
    val symptoms = linkedMapOf(
        "Pain" to "दर्द", "Tiredness" to "थकान / ऊर्जा की कमी", "Sleepiness" to "बहुत नींद आना",
        "Poor appetite" to "भूख कम लगना", "Dry mouth" to "मुँह सूखना / मुँह में दर्द",
        "Breathlessness" to "साँस फूलना", "Constipation" to "कब्ज़", "Diarrhea" to "दस्त", "Nausea" to "मतली",
        "Vomiting" to "उल्टी", "Difficulty swallowing" to "निगलने में परेशानी", "Cough" to "खाँसी",
        "Bowel / bladder incontinence" to "मल / पेशाब पर नियंत्रण न रहना", "Sadness" to "उदासी", "Worry" to "चिंता",
        "Overall well-being" to "कुल मिलाकर तबीयत", "Other" to "अन्य",
    )
    private val englishLabels = mapOf("Tiredness" to "Fatigue / lack of energy", "Sleepiness" to "Drowsiness / excessive sleepiness", "Dry mouth" to "Dry / sore mouth")
    private val legacyHindiLabels = mapOf("Sleep difficulty" to "सोने में परेशानी", "Weight loss" to "वज़न घटना")
    val recalls = linkedMapOf("now" to ("Right now" to "अभी"), "average-24h" to ("24-hour average" to "24 घंटे का औसत"), "worst-24h" to ("Worst in 24 hours" to "24 घंटे में सबसे ज़्यादा"))
    val respondents = linkedMapOf("patient" to ("Patient" to "मरीज़"), "assisted-patient" to ("Patient, with help" to "मरीज़ ने मदद से बताया"), "caregiver" to ("Seen by family or carer" to "देखभाल करने वाले ने देखा"), "clinician" to ("Seen by care team" to "देखभाल टीम ने देखा"))
    val triggers = linkedMapOf("rest" to ("At rest" to "आराम करते समय"), "turning" to ("Turning in bed" to "करवट लेते समय"), "walking" to ("Standing / walking" to "खड़े होते / चलते समय"), "coughing" to ("Coughing" to "खाँसते समय"), "other" to ("Other" to "अन्य"), "not-sure" to ("Not sure" to "पक्का नहीं"))
    val changes = linkedMapOf("better" to ("Better" to "बेहतर"), "same" to ("Same" to "वैसा ही"), "worse" to ("Worse" to "ज़्यादा खराब"), "not-sure" to ("Not sure" to "पक्का नहीं"))
    fun label(labels: Map<String, Pair<String, String>>, key: String, hindi: Boolean) = labels[key]?.let { if (hindi) it.second else it.first }.orEmpty()
    fun symptomLabel(value: String, hindi: Boolean) = if (hindi) symptoms[value] ?: legacyHindiLabels[value] ?: value else englishLabels[value] ?: value
    fun scaleForSymptom(value: String) = if (value == "Overall well-being") WELLBEING_SCALE else PERSONAL_SCALE
    fun ratingQuestion(scale: String, hindi: Boolean) = if (scale == WELLBEING_SCALE) {
        if (hindi) "कुल मिलाकर तबीयत कैसी है?" else "How is the overall well-being?"
    } else if (hindi) "कितनी परेशानी है?" else "How strong is it?"
    fun ratingAnchors(scale: String, hindi: Boolean) = if (scale == WELLBEING_SCALE) {
        if (hindi) "0 = सबसे अच्छी तबीयत · 10 = सबसे खराब तबीयत" else "0 = best possible well-being · 10 = worst possible well-being"
    } else if (hindi) "0 = बिलकुल नहीं · 10 = सबसे ज़्यादा" else "0 = none · 10 = worst imaginable"
    fun ratingLabel(value: Int?, hindi: Boolean = false) = value?.let { "$it/10" } ?: if (hindi) "अंक नहीं बताया" else "Not rated"
    fun editToken(item: CareItem): String = JSONObject().put("id", item.id).put("personId", item.personId).put("kind", item.kind)
        .put("title", item.title).put("detail", item.detail).put("due", item.due).put("doneOn", item.doneOn)
        .put("created", item.created).put("revision", item.revision).toString()
    fun saveConflict(existing: CareItem?, personId: String, expectedToken: String?): String? {
        if (expectedToken == null && existing == null) return null
        if (expectedToken != null && existing != null && existing.personId == personId && existing.kind == "checkin" && SymptomNoteCodec.isStructured(existing.detail)
            && editToken(existing) == expectedToken) return null
        return SAVE_CONFLICT
    }
    fun error(value: SymptomNote, now: LocalDateTime = LocalDateTime.now()): String? {
        if (value.symptom.trim().length !in 1..80) return "Choose a symptom or add a short name."
        if (value.rating != null && value.rating !in 0..10) return "Choose a number from 0 to 10, or Not rated."
        val observed = runCatching { LocalDateTime.of(LocalDate.parse(value.observedDate), LocalTime.parse(value.observedTime)) }.getOrNull() ?: return "Choose a valid date and time."
        if (observed > now) return "The observation cannot be in the future."
        if (value.startedOn.isNotBlank() && runCatching { LocalDate.parse(value.startedOn) <= observed.toLocalDate() }.getOrDefault(false).not()) return "The start date must be on or before this observation."
        if (value.scale !in setOf(PERSONAL_SCALE, WELLBEING_SCALE) || (value.scale == WELLBEING_SCALE && value.symptom != "Overall well-being") || value.recall !in recalls || value.respondent !in respondents) return "Choose who reported this and the time frame."
        if (value.author.trim().length !in 1..120) return "Add who wrote or observed this note."
        if (value.note.length > 500 || value.painLocation.length > 100 || value.painTriggers.any { it !in triggers }) return "Shorten the note or review the pain details."
        if (value.painBodySites.size > PainBodyMapRules.sites.size || value.painBodySites.any { it !in PainBodyMapRules.sites }) return "Choose a listed pain area."
        if (value.painBodyPoints.size > 12 || value.painBodyPoints.map { it.site }.distinct().size != value.painBodyPoints.size || value.painBodyPoints.any { !PainBodyMapRules.validPoint(it) || it.site !in value.painBodySites }) return "Choose a valid point on a selected pain area."
        if (value.painBodyRatings.any { (site, r) -> site !in value.painBodySites || r !in 0..10 }) return "Choose a number from 0 to 10 for a selected pain area."
        if (value.painBodyPoints.isNotEmpty() && value.painBodyModel != PainBodyMapRules.MODEL) return "This body map version cannot be edited here."
        if (value.painBodyModel.isNotEmpty() && value.painBodyModel != PainBodyMapRules.MODEL) return "This body map version cannot be edited here."
        if (value.symptom != "Pain" && (value.painLocation.isNotBlank() || value.painTriggers.isNotEmpty() || value.painBodySites.isNotEmpty() || value.painBodyPoints.isNotEmpty() || value.painBodyRatings.isNotEmpty())) return "Pain details belong to a pain entry."
        if (value.selfReportedChange.isNotEmpty() && value.selfReportedChange !in changes) return "Choose a comparison."
        if (value.selfReportedChange.isEmpty() != value.comparedWithId.isEmpty()) return "Choose the previous entry for this comparison."
        return null
    }
    fun sameContext(a: CareItem, aNote: SymptomNote, b: CareItem, bNote: SymptomNote): Boolean =
        a.personId == b.personId && aNote.scale == bNote.scale && aNote.symptom.trim().equals(bNote.symptom.trim(), true) &&
            aNote.recall == bNote.recall && aNote.respondent == bNote.respondent &&
            (aNote.respondent !in setOf("caregiver", "clinician") || aNote.author.trim() == bNote.author.trim()) &&
            aNote.painLocation.trim().equals(bNote.painLocation.trim(), true) && aNote.painTriggers.toSet() == bNote.painTriggers.toSet() &&
            aNote.painBodySites.toSet() == bNote.painBodySites.toSet() && aNote.painBodyModel == bNote.painBodyModel && PainBodyMapRules.pointContext(aNote.painBodyPoints) == PainBodyMapRules.pointContext(bNote.painBodyPoints)
    fun series(entries: List<CareItem>, current: CareItem, value: SymptomNote): List<Pair<CareItem, SymptomNote>> =
        entries.mapNotNull { item -> SymptomNoteCodec.decode(item.detail)?.let { item to it } }
            .filter { (item, note) -> item.kind == "checkin" && note.rating != null && sameContext(current, value, item, note) }
            .sortedWith(compareBy<Pair<CareItem, SymptomNote>> { "${it.second.observedDate}T${it.second.observedTime}" }.thenBy { it.first.id })
    fun followUp(value: SymptomNote, now: LocalDateTime = LocalDateTime.now()): SymptomNote = value.copy(
        rating = null, observedDate = now.toLocalDate().toString(), observedTime = now.toLocalTime().withSecond(0).withNano(0).toString(),
        note = "", selfReportedChange = "", comparedWithId = "", painBodyRatings = emptyMap(),
    )
    fun comparison(entries: List<CareItem>, current: CareItem, value: SymptomNote): SymptomComparison? {
        val rating = value.rating ?: return null
        val key = "${value.observedDate}T${value.observedTime}"
        val matching = entries.mapNotNull { item -> SymptomNoteCodec.decode(item.detail)?.let { item to it } }
            .filter { (item, note) -> item.id != current.id && note.rating != null && sameContext(current, value, item, note) && "${note.observedDate}T${note.observedTime}" < key }
            .sortedWith(compareBy<Pair<CareItem, SymptomNote>> { "${it.second.observedDate}T${it.second.observedTime}" }.thenBy { it.first.id })
        if (matching.isEmpty()) return null
        return SymptomComparison(matching.first().first, matching.last().first, rating - matching.last().second.rating!!)
    }
}

object SymptomNoteCodec {
    const val PREFIX = "sahara:symptom:v1\n"
    fun isStructured(text: String) = text.startsWith(PREFIX)
    fun encode(value: SymptomNote): String = PREFIX + JSONObject().put("symptom", value.symptom.trim()).put("rating", value.rating ?: JSONObject.NULL)
        .put("observedDate", value.observedDate).put("observedTime", value.observedTime).put("recall", value.recall).put("respondent", value.respondent)
        .put("author", value.author.trim()).put("startedOn", value.startedOn).put("note", value.note.trim()).put("scale", value.scale)
        .put("painLocation", value.painLocation.trim()).put("painTriggers", JSONArray(value.painTriggers.distinct().sorted()))
        .put("painBodySites", JSONArray(value.painBodySites.distinct().sorted()))
        .put("painBodyPoints", PainBodyMapRules.encodePoints(value.painBodyPoints))
        .put("painBodyModel", value.painBodyModel)
        .also { if (value.painBodyRatings.isNotEmpty()) it.put("painBodyRatings", PainBodyMapRules.encodeRatings(value.painBodyRatings)) }
        .put("selfReportedChange", value.selfReportedChange).put("comparedWithId", value.comparedWithId).toString()
    fun decode(text: String): SymptomNote? {
        if (!isStructured(text)) return null
        return runCatching {
            val j = JSONObject(text.removePrefix(PREFIX)); val triggers = j.getJSONArray("painTriggers"); val sites = j.optJSONArray("painBodySites")
            SymptomNote(j.getString("symptom"), if (j.isNull("rating")) null else j.getInt("rating"), j.getString("observedDate"), j.getString("observedTime"),
                j.getString("recall"), j.getString("respondent"), j.getString("author"), j.optString("startedOn"), j.optString("note"), j.optString("painLocation"),
                (0 until triggers.length()).map { triggers.getString(it) }, j.optString("selfReportedChange"), j.optString("comparedWithId"), j.getString("scale"),
                sites?.let { list -> (0 until list.length()).map { list.getString(it) } }.orEmpty(), PainBodyMapRules.decodePoints(j.optJSONArray("painBodyPoints")), j.optString("painBodyModel"), PainBodyMapRules.decodeRatings(j.optJSONObject("painBodyRatings")))
                .also { require(SymptomDiaryRules.error(it) == null) }
        }.getOrNull()
    }
    fun readable(text: String, hindi: Boolean = false): String = decode(text)?.let { n ->
        listOf("${if (hindi) "देखा गया" else "Observed"}: ${n.observedDate} ${n.observedTime} · ${SymptomDiaryRules.ratingLabel(n.rating, hindi)}", SymptomDiaryRules.ratingAnchors(n.scale, hindi).takeIf { n.scale == SymptomDiaryRules.WELLBEING_SCALE }.orEmpty(), SymptomDiaryRules.label(SymptomDiaryRules.recalls, n.recall, hindi),
            "${SymptomDiaryRules.label(SymptomDiaryRules.respondents, n.respondent, hindi)} · ${n.author}",
            n.startedOn.takeIf { it.isNotBlank() }?.let { "${if (hindi) "शुरू हुआ" else "Started"}: $it" }.orEmpty(),
            n.painLocation, n.painBodySites.takeIf { it.isNotEmpty() }?.joinToString(prefix = if (hindi) "दर्द की जगह: " else "Pain areas: ") { PainBodyMapRules.label(it, hindi) + (n.painBodyRatings[it]?.let { r -> " $r/10" } ?: "") }.orEmpty(), n.painTriggers.joinToString { SymptomDiaryRules.label(SymptomDiaryRules.triggers, it, hindi) },
            n.selfReportedChange.takeIf { it.isNotBlank() }?.let { "${if (hindi) "उनके मुताबिक बदलाव" else "Reported change"}: ${SymptomDiaryRules.label(SymptomDiaryRules.changes, it, hindi)}" }.orEmpty(), n.note).filter(String::isNotBlank).joinToString("\n")
    } ?: if (!isStructured(text)) text else if (hindi) "यह नोट पढ़ा नहीं जा सका। मूल रिकॉर्ड सुरक्षित है।" else "This symptom note could not be read. The original record is retained."
}
