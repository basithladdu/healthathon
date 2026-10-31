package `in`.wedevit.care

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Short patient summary for the Today screen (handoff A6). Built only from entries already on the phone; nothing is stored. */
enum class SummaryTrend { UP, DOWN, SAME, FIRST }

/** One recent symptom. [site] is a body-part id for per-spot pain, [moodScale] a Mood scale key; otherwise [symptom] is the stored symptom key. */
data class SummarySymptom(val symptom: String, val site: String = "", val moodScale: String = "", val value: Int, val previous: Int?, val trend: SummaryTrend)

data class SummaryPlan(val kind: EdKind, val reviewDue: String)

data class PatientSummary(
    val symptoms: List<SummarySymptom>, val medicineTotal: Int, val medicineTicked: Int,
    val nextVisit: CareItem?, val latestNote: CareVersion?, val plan: SummaryPlan,
)

object CarePatientSummaryRules {
    const val RECENT_DAYS = 7L
    const val MAX_SYMPTOM_LINES = 6

    fun trend(latest: Int, previous: Int?): SummaryTrend = when { previous == null -> SummaryTrend.FIRST; latest > previous -> SummaryTrend.UP; latest < previous -> SummaryTrend.DOWN; else -> SummaryTrend.SAME }

    /** The latest point and the one before it with the same time frame, or null when the latest is older than [RECENT_DAYS]. */
    private fun latestWithPrevious(series: List<DatedRating>, today: LocalDate): Pair<Int, Int?>? {
        val last = series.lastOrNull() ?: return null
        val day = runCatching { LocalDate.parse(last.date) }.getOrNull() ?: return null
        if (day.isBefore(today.minusDays(RECENT_DAYS - 1)) || day.isAfter(today)) return null
        val previous = series.dropLast(1).lastOrNull { it.recall == last.recall }?.value
        return last.value to previous
    }

    private fun line(symptom: String, site: String, scale: String, series: List<DatedRating>, today: LocalDate): SummarySymptom? =
        latestWithPrevious(series, today)?.let { (v, p) -> SummarySymptom(symptom, site, scale, v, p, trend(v, p)) }

    /** Latest score per recently noted symptom, highest first. Pain is split by body spot when spot ratings exist. */
    fun symptoms(items: List<CareItem>, personId: String, today: LocalDate): List<SummarySymptom> {
        val notes = items.filter { it.personId == personId && it.kind == "checkin" }.mapNotNull { SymptomNoteCodec.decode(it.detail) }.map(SymptomDiaryRules::migrated)
        val names = notes.map { it.symptom.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val lines = names.flatMap { name ->
            when {
                name.equals("Pain", true) -> {
                    val sites = SymptomDiaryRules.painPartsWithHistory(items, personId)
                    val perSpot = sites.mapNotNull { line("Pain", it, "", SymptomDiaryRules.painPartSeries(items, personId, it), today) }
                    perSpot.ifEmpty { listOfNotNull(line("Pain", "", "", SymptomDiaryRules.symptomSeries(items, personId, "Pain"), today)) }
                }
                name == SymptomDiaryRules.MOOD -> SymptomDiaryRules.moodScales.keys.mapNotNull { line(name, "", it, SymptomDiaryRules.moodSeries(items, personId, it), today) }
                else -> listOfNotNull(line(name, "", "", SymptomDiaryRules.symptomSeries(items, personId, name), today))
            }
        }
        return lines.sortedByDescending { it.value }.take(MAX_SYMPTOM_LINES)
    }

    fun build(items: List<CareItem>, versions: List<CareVersion>, personId: String, plans: List<EctprRecord>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PatientSummary {
        val mine = items.filter { it.personId == personId }
        val medicines = CareDailyFlow.medicinesForToday(mine, today)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val ed = EdView.forPatient(plans, personId, today)
        return PatientSummary(
            symptoms = symptoms(mine, personId, today), medicineTotal = medicines.size, medicineTicked = CareDailyFlow.takenToday(mine, today),
            nextVisit = CareTodayRules.upcomingVisits(mine, dayStart).firstOrNull(), latestNote = versions.filter { it.personId == personId }.maxByOrNull { it.published },
            plan = SummaryPlan(ed.kind, ed.record?.reviewDue.orEmpty()),
        )
    }

    // ---- wording ----
    fun symptomName(s: SummarySymptom, hindi: Boolean): String = when {
        s.site.isNotEmpty() -> "${SymptomDiaryRules.symptomLabel("Pain", hindi)} (${PainBodyMapRules.label(s.site, hindi)})"
        s.moodScale.isNotEmpty() -> SymptomDiaryRules.moodShort[s.moodScale]?.let { careTr(hindi, it.first, it.second) } ?: SymptomDiaryRules.symptomLabel(s.symptom, hindi)
        else -> SymptomDiaryRules.symptomLabel(s.symptom, hindi)
    }

    /** "6/10, down from 8" */
    fun trendText(s: SummarySymptom, hindi: Boolean): String { val v = s.value; val p = s.previous
        return when (s.trend) {
            SummaryTrend.UP -> careTr(hindi, "$v/10, up from $p", "$v/10, पहले $p था, अब बढ़ा")
            SummaryTrend.DOWN -> careTr(hindi, "$v/10, down from $p", "$v/10, पहले $p था, अब घटा")
            SummaryTrend.SAME -> careTr(hindi, "$v/10, same as before", "$v/10, पहले जैसा")
            SummaryTrend.FIRST -> careTr(hindi, "$v/10, first note", "$v/10, पहला नोट")
        }
    }

    fun symptomLine(s: SummarySymptom, hindi: Boolean) = "${symptomName(s, hindi)} ${trendText(s, hindi)}"
    fun noSymptoms(hindi: Boolean) = careTr(hindi, "No symptoms logged this week", "इस हफ़्ते कोई लक्षण दर्ज नहीं")

    fun medicineCount(n: Int, hindi: Boolean): String = when (n) { 0 -> careTr(hindi, "No medicines yet", "अभी कोई दवा नहीं"); 1 -> careTr(hindi, "1 medicine", "1 दवा"); else -> careTr(hindi, "$n medicines", "$n दवाइयाँ") }
    fun medicineStatus(total: Int, ticked: Int, hindi: Boolean): String { val taken = ticked.coerceAtMost(total); val left = (total - taken).coerceAtLeast(0)
        return if (total == 0) careTr(hindi, "No medicines yet", "अभी कोई दवा नहीं") else careTr(hindi, "$taken of $total ticked today, $left not yet", "आज $total में से $taken पर टिक, $left बाक़ी")
    }

    fun visitDay(item: CareItem, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        if (item.due == 0L) careTr(hindi, "date to be set", "तारीख़ तय होनी है") else Instant.ofEpochMilli(item.due).atZone(zone).toLocalDate().format(careDateFormat("EEE d MMM", hindi))
    fun nextVisit(visit: CareItem?, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        if (visit == null) careTr(hindi, "No visit planned", "कोई मुलाक़ात तय नहीं") else { val day = visitDay(visit, hindi, zone); careTr(hindi, "Next visit $day", "अगली मुलाक़ात $day") }

    fun noteLine(v: CareVersion?, dateText: String, hindi: Boolean): String { if (v == null) return careTr(hindi, "No care note yet", "अभी कोई देखभाल नोट नहीं"); val n = v.version; val doctor = v.doctor.trim()
        return if (doctor.isEmpty()) careTr(hindi, "Version $n, $dateText", "संस्करण $n, $dateText") else careTr(hindi, "Version $n, $dateText, $doctor", "संस्करण $n, $dateText, $doctor")
    }

    fun planLine(p: SummaryPlan, hindi: Boolean): String { val due = p.reviewDue
        return when (p.kind) {
            EdKind.NONE -> careTr(hindi, "No emergency plan yet", "अभी कोई आपातकालीन योजना नहीं")
            EdKind.UNSIGNED -> careTr(hindi, "Draft, not signed yet", "मसौदा, अभी हस्ताक्षर नहीं")
            EdKind.OUT_OF_DATE -> careTr(hindi, "Out of date, review was due $due", "पुरानी, समीक्षा $due को होनी थी")
            EdKind.REVOKED -> careTr(hindi, "Withdrawn, no longer valid", "वापस ली गई, अब मान्य नहीं")
            EdKind.ACTIVE -> careTr(hindi, "Active, review due $due", "लागू, समीक्षा की तारीख़ $due")
        }
    }

    /** The collapsed line: top symptom, medicine count, next visit. */
    fun collapsed(s: PatientSummary, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val top = s.symptoms.firstOrNull()?.let { symptomLine(it, hindi) } ?: noSymptoms(hindi)
        return listOf(top, medicineCount(s.medicineTotal, hindi), nextVisit(s.nextVisit, hindi, zone)).joinToString(" · ")
    }
}
