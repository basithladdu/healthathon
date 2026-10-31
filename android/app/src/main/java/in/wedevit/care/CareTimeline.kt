package `in`.wedevit.care

import java.time.YearMonth
import java.time.ZoneId

/** The filter chips on the Care timeline. */
enum class TimelineFilter { ALL, TREATMENT, SCANS, LAB, VISITS }

enum class TimelineKind(val filter: TimelineFilter) {
    CHEMO(TimelineFilter.TREATMENT), RADIO(TimelineFilter.TREATMENT),
    SCAN(TimelineFilter.SCANS), BIOPSY(TimelineFilter.SCANS), STAGING(TimelineFilter.SCANS),
    LAB(TimelineFilter.LAB),
    TEST(TimelineFilter.VISITS), VISIT(TimelineFilter.VISITS), NOTE(TimelineFilter.VISITS), DOCUMENT(TimelineFilter.VISITS),
}

/** Where an event comes from, so a tap can open the right place. */
enum class TimelineSource { REPORT, CALENDAR, LAB_HISTORY, CARE_NOTE, GOALS }

/**
 * One dated fact already in the record. [title] is text the family typed or a report name (never reworded here);
 * for [TimelineSource.GOALS] it is the version number. [dateIsFileDate] is true when the date is the day the file or
 * result was added to Sahara, because the report's own date is not recorded.
 */
data class TimelineEvent(
    val id: String, val epoch: Long, val kind: TimelineKind, val title: String,
    val source: TimelineSource, val sourceName: String = "", val sourceId: String = "", val page: Int = 0,
    val dateIsFileDate: Boolean = false, val subtype: String = "", val isFile: Boolean = false,
)

data class TimelineSummary(val chemo: TimelineEvent?, val radio: TimelineEvent?, val scan: TimelineEvent?, val biopsy: TimelineEvent?)

object CareTimelineRules {
    private val copiedFrom = Regex("Copied from: .+? · page (\\d+)")
    private val scanWords = listOf("ct scan", "mri", "ultrasound", "usg", "x ray", "xray", "mammogram", "pet")

    /** The page a copied fact came from ("Copied from: name · page 2 · line 4"), or 0 when it is not recorded. */
    fun pageOf(detail: String): Int = copiedFrom.find(detail)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun looksLikeScan(name: String): Boolean {
        val text = " " + name.lowercase().replace(Regex("[^a-z0-9]+"), " ") + " "
        return scanWords.any { text.contains(" $it ") }
    }

    /**
     * Past events for one person from reports, calendar entries, lab results, care notes and goals notes.
     * Cancelled entries and dates after today are left out. Newest first.
     */
    fun events(
        personId: String, items: List<CareItem>, reports: List<CareReport>, notes: List<CareVersion>,
        cancerType: (CareReport) -> String?, folderOf: (CareReport) -> String, now: Long, zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimelineEvent> {
        val end = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val out = ArrayList<TimelineEvent>()
        for (item in items) {
            if (item.personId != personId) continue
            val entry = CareEntryCodec.decode(item.detail)
            when {
                entry != null && entry.type == "lab" -> {
                    val dated = item.due > 0
                    val at = if (dated) item.due else item.created
                    if (at <= 0 || at >= end) continue
                    val title = listOf(item.title.trim(), entry.value.trim(), entry.unit.trim()).filter(String::isNotBlank).joinToString(" ")
                    val fromReport = entry.reportId.isNotBlank()
                    out += TimelineEvent("lab:${item.id}", at, TimelineKind.LAB, title,
                        if (fromReport) TimelineSource.REPORT else TimelineSource.LAB_HISTORY,
                        if (fromReport) entry.reportName else "", if (fromReport) entry.reportId else item.id, if (fromReport) pageOf(entry.detail) else 0, dateIsFileDate = !dated)
                }
                entry != null && entry.type == "schedule" -> {
                    if (entry.status == "cancelled" || item.due <= 0 || item.due >= end) continue
                    val kind = when (entry.category) {
                        "Chemotherapy" -> TimelineKind.CHEMO; "Radiotherapy" -> TimelineKind.RADIO
                        "Test" -> TimelineKind.TEST; "Visit", "Follow-up", "Procedure" -> TimelineKind.VISIT; else -> continue
                    }
                    val fromReport = entry.reportId.isNotBlank()
                    out += TimelineEvent("calendar:${item.id}", item.due, kind, item.title.trim().ifBlank { entry.category },
                        if (fromReport) TimelineSource.REPORT else TimelineSource.CALENDAR,
                        if (fromReport) entry.reportName else "", if (fromReport) entry.reportId else item.id, if (fromReport) pageOf(entry.detail) else 0)
                }
                entry == null && item.kind == "visit" -> {
                    if (item.due <= 0 || item.due >= end) continue
                    out += TimelineEvent("calendar:${item.id}", item.due, TimelineKind.VISIT, item.title.trim(), TimelineSource.CALENDAR, sourceId = item.id)
                }
                entry == null && item.kind == "journal" -> {
                    val at = item.created
                    if (at <= 0 || at >= end) continue
                    val page = pageOf(item.detail)
                    val report = if (page > 0) Regex("Source ID: (\\S+)").find(item.detail)?.groupValues?.get(1)?.let { id -> reports.firstOrNull { it.id == id && it.personId == personId } } else null
                    // Only facts copied from a document belong here; everyday journal lines and side-effect notes do not.
                    if (report == null) continue
                    out += TimelineEvent("note:${item.id}", at, TimelineKind.NOTE, item.title.trim(),
                        if (report != null) TimelineSource.REPORT else TimelineSource.CARE_NOTE,
                        report?.name.orEmpty(), report?.id ?: item.id, if (report != null) page else 0)
                }
            }
        }
        for (report in reports) {
            if (report.personId != personId || report.created <= 0 || report.created >= end) continue
            val type = cancerType(report)
            val kind = when (type) {
                "PET-CT" -> TimelineKind.SCAN; "Biopsy", "Histopathology" -> TimelineKind.BIOPSY; "Staging" -> TimelineKind.STAGING
                null -> if (folderOf(report) == DocumentFolderRules.LAB) TimelineKind.LAB else if (looksLikeScan(report.name)) TimelineKind.SCAN else TimelineKind.DOCUMENT
                else -> TimelineKind.DOCUMENT
            }
            out += TimelineEvent("report:${report.id}", report.created, kind, report.name, TimelineSource.REPORT, report.name, report.id, 0, dateIsFileDate = true, subtype = type.orEmpty(), isFile = true)
        }
        for (note in notes) {
            if (note.personId != personId || note.published <= 0 || note.published >= end) continue
            out += TimelineEvent("goals:${note.id}", note.published, TimelineKind.NOTE, note.version.toString(), TimelineSource.GOALS, sourceId = note.id)
        }
        return out.sortedWith(compareByDescending<TimelineEvent> { it.epoch }.thenBy { it.kind.ordinal }.thenBy { it.id })
    }

    /** The newest event of each kind the summary strip shows. [events] may be in any order. */
    fun summary(events: List<TimelineEvent>): TimelineSummary {
        fun last(vararg kinds: TimelineKind) = events.filter { it.kind in kinds }.maxByOrNull { it.epoch }
        return TimelineSummary(last(TimelineKind.CHEMO), last(TimelineKind.RADIO), last(TimelineKind.SCAN), last(TimelineKind.BIOPSY))
    }

    fun filter(events: List<TimelineEvent>, filter: TimelineFilter): List<TimelineEvent> = if (filter == TimelineFilter.ALL) events else events.filter { it.kind.filter == filter }

    /** Month groups, newest month first, keeping the order of [events] inside each month. */
    fun byMonth(events: List<TimelineEvent>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<YearMonth, List<TimelineEvent>>> =
        events.sortedByDescending { it.epoch }.groupBy { YearMonth.from(java.time.Instant.ofEpochMilli(it.epoch).atZone(zone)) }.toList().sortedByDescending { it.first }
}
