package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CareTimelineTest {
    private val utc = ZoneId.of("UTC")
    private val person = "p1"
    private fun day(s: String) = LocalDate.parse(s).atStartOfDay(utc).toInstant().toEpochMilli()
    private val now = day("2026-10-31") + 3_600_000
    private fun schedule(id: String, category: String, on: String, status: String = "open", who: String = person, reportId: String = "", detail: String = "") =
        CareItem(id, who, "task", category, CareEntryCodec.encode(CareEntry(type = "schedule", category = category, status = status, reportId = reportId, reportName = if (reportId.isBlank()) "" else "Scan.pdf", detail = detail)), day(on))
    private fun lab(id: String, title: String, on: String?, reportId: String = "", detail: String = "") =
        CareItem(id, person, "task", title, CareEntryCodec.encode(CareEntry(type = "lab", value = "9.8", unit = "g/dL", reportId = reportId, reportName = if (reportId.isBlank()) "" else "Bloods.pdf", detail = detail)), on?.let(::day) ?: 0, created = day("2026-09-01"))
    private fun report(id: String, name: String, on: String, who: String = person) = CareReport(id, who, name, "application/pdf", "$id.pdf", 10, day(on))
    private fun events(items: List<CareItem> = emptyList(), reports: List<CareReport> = emptyList(), notes: List<CareVersion> = emptyList(), type: (CareReport) -> String? = { null }, folder: (CareReport) -> String = { DocumentFolderRules.OTHER }) =
        CareTimelineRules.events(person, items, reports, notes, type, folder, now, utc)

    @Test fun newestFirstAndOnlyThisPersonAndPast() {
        val list = events(listOf(schedule("a", "Chemotherapy", "2026-09-01"), schedule("b", "Radiotherapy", "2026-10-05"), schedule("c", "Chemotherapy", "2026-11-05"),
            schedule("d", "Chemotherapy", "2026-10-01", who = "p2"), schedule("e", "Chemotherapy", "2026-10-02", status = "cancelled")))
        assertEquals(listOf("calendar:b", "calendar:a"), list.map { it.id })
        assertTrue(list.all { it.source == TimelineSource.CALENDAR })
    }

    @Test fun todayCountsButTomorrowDoesNot() {
        assertEquals(1, events(listOf(schedule("a", "Chemotherapy", "2026-10-31"))).size)
        assertEquals(0, events(listOf(schedule("a", "Chemotherapy", "2026-11-01"))).size)
    }

    @Test fun reportsUseTheFamilysTypeAndNeverInventAReportDate() {
        val reports = listOf(report("r1", "PET CT.pdf", "2026-09-12"), report("r2", "Slide.pdf", "2026-08-01"), report("r3", "Stage.pdf", "2026-07-01"), report("r4", "Bloods.pdf", "2026-06-01"), report("r5", "MRI brain.pdf", "2026-05-01"), report("r6", "Scan 2026-10-01 10-00.jpg", "2026-10-01"))
        val type = mapOf("r1" to "PET-CT", "r2" to "Histopathology", "r3" to "Staging")
        val list = events(reports = reports, type = { type[it.id] }, folder = { if (it.id == "r4") DocumentFolderRules.LAB else DocumentFolderRules.OTHER })
        val byId = list.associateBy { it.sourceId }
        assertEquals(TimelineKind.SCAN, byId.getValue("r1").kind); assertEquals(TimelineKind.BIOPSY, byId.getValue("r2").kind); assertEquals(TimelineKind.STAGING, byId.getValue("r3").kind)
        assertEquals(TimelineKind.LAB, byId.getValue("r4").kind); assertEquals(TimelineKind.SCAN, byId.getValue("r5").kind)
        assertEquals("a camera scan file name alone is not a scan result", TimelineKind.DOCUMENT, byId.getValue("r6").kind)
        assertTrue(list.all { it.dateIsFileDate && it.isFile && it.source == TimelineSource.REPORT })
        assertEquals("PET-CT", byId.getValue("r1").subtype)
    }

    @Test fun labResultsKeepTheirReportAndPage() {
        val copied = DocumentEvidenceRules.copiedDetail(DocumentFactSource("r9", person, "sha", 2, 4, "Hb 9.8", 5), "Bloods.pdf")
        val list = events(listOf(lab("l1", "Haemoglobin", "2026-10-02", reportId = "r9", detail = copied), lab("l2", "Calcium", "2026-10-03"), lab("l3", "Sodium", null)))
        val l1 = list.first { it.id == "lab:l1" }
        assertEquals(TimelineSource.REPORT, l1.source); assertEquals("r9", l1.sourceId); assertEquals(2, l1.page); assertEquals("Haemoglobin 9.8 g/dL", l1.title); assertFalse(l1.dateIsFileDate)
        assertEquals(TimelineSource.LAB_HISTORY, list.first { it.id == "lab:l2" }.source)
        val undated = list.first { it.id == "lab:l3" }
        assertTrue("a result without a report date says the date is when it was added", undated.dateIsFileDate); assertEquals(day("2026-09-01"), undated.epoch)
    }

    @Test fun copiedNotesPointToTheirReportPage() {
        val detail = DocumentEvidenceRules.copiedDetail(DocumentFactSource("r7", person, "sha", 3, 1, "Plan next scan", 5), "Discharge.pdf")
        val note = CareItem("n1", person, "journal", "Plan next scan", detail, created = day("2026-10-10"))
        val plain = CareItem("n2", person, "journal", "Slept well", "Slept well.", created = day("2026-10-11"))
        val list = events(listOf(note, plain), reports = listOf(report("r7", "Discharge.pdf", "2026-10-01")))
        val n1 = list.first { it.id == "note:n1" }
        assertEquals(TimelineSource.REPORT, n1.source); assertEquals(3, n1.page); assertEquals("r7", n1.sourceId)
        // Everyday journal lines are not document events.
        assertTrue(list.none { it.id == "note:n2" })
    }

    @Test fun visitsAndGoalsNotesAreEvents() {
        val visit = CareItem("v1", person, "visit", "Oncology review", "plain text", day("2026-10-20"))
        val version = CareVersion("g1", person, 2, "Rahul", "s", "p", "pa", "t", "q", "n", "Dr Sen", day("2026-10-15"), "h")
        val list = events(listOf(visit, schedule("t1", "Test", "2026-10-18")), notes = listOf(version))
        assertEquals(listOf("calendar:v1", "calendar:t1", "goals:g1"), list.map { it.id })
        assertEquals(TimelineKind.TEST, list[1].kind); assertEquals("2", list[2].title); assertEquals(TimelineSource.GOALS, list[2].source)
    }

    @Test fun summaryTakesTheNewestOfEachKindAndLeavesUnknownEmpty() {
        val list = events(listOf(schedule("a", "Chemotherapy", "2026-09-01"), schedule("b", "Chemotherapy", "2026-10-01"), schedule("c", "Radiotherapy", "2026-06-01")),
            reports = listOf(report("r1", "PET.pdf", "2026-09-12"), report("r2", "PET old.pdf", "2026-03-12")), type = { if (it.id.startsWith("r")) "PET-CT" else null })
        val s = CareTimelineRules.summary(list.shuffled())
        assertEquals("calendar:b", s.chemo?.id); assertEquals("calendar:c", s.radio?.id); assertEquals("report:r1", s.scan?.id); assertNull(s.biopsy)
        assertEquals(TimelineSummary(null, null, null, null), CareTimelineRules.summary(emptyList()))
    }

    @Test fun filtersSelectGroups() {
        val list = events(listOf(schedule("a", "Chemotherapy", "2026-09-01"), lab("l", "Hb", "2026-09-02"), schedule("v", "Visit", "2026-09-03")), reports = listOf(report("r", "Biopsy.pdf", "2026-09-04")), type = { "Biopsy" })
        assertEquals(4, CareTimelineRules.filter(list, TimelineFilter.ALL).size)
        assertEquals(listOf("calendar:a"), CareTimelineRules.filter(list, TimelineFilter.TREATMENT).map { it.id })
        assertEquals(listOf("report:r"), CareTimelineRules.filter(list, TimelineFilter.SCANS).map { it.id })
        assertEquals(listOf("lab:l"), CareTimelineRules.filter(list, TimelineFilter.LAB).map { it.id })
        assertEquals(listOf("calendar:v"), CareTimelineRules.filter(list, TimelineFilter.VISITS).map { it.id })
    }

    @Test fun monthsGroupNewestFirst() {
        val list = events(listOf(schedule("a", "Chemotherapy", "2026-08-31"), schedule("b", "Chemotherapy", "2026-10-01"), schedule("c", "Radiotherapy", "2026-10-20"), schedule("d", "Chemotherapy", "2025-12-25")))
        val months = CareTimelineRules.byMonth(list, utc)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 8), YearMonth.of(2025, 12)), months.map { it.first })
        assertEquals(listOf("calendar:c", "calendar:b"), months[0].second.map { it.id })
    }

    @Test fun pageIsZeroWhenNotRecorded() {
        assertEquals(0, CareTimelineRules.pageOf("Slept well"))
        assertEquals(12, CareTimelineRules.pageOf("Copied from: A · B.pdf · page 12 · line 3"))
    }
}
