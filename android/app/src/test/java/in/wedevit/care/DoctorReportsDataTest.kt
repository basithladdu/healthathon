package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorReportsDataTest {
    @Test fun reportsRetainExactPatientSpecificSourceAndUnknownFileMetadata() {
        DoctorDemoData.patients.forEach { patient -> val reports = DoctorReportsData.reports(patient.id); assertEquals(patient.reportSummaries, reports.map { it.summary }); assertTrue(reports.all { it.patientId == patient.id && !it.hasAttachedFile && it.createdAt == null && it.author == null }) }
        assertTrue(DoctorReportsData.reports("unknown").isEmpty())
    }
    @Test fun declaredCategoriesMatchKnownFixturesOnly() {
        assertEquals(DoctorReportCategory.CLINIC_LETTERS, DoctorReportsData.reports("demo-asha").single().category)
        assertEquals(DoctorReportCategory.DISCHARGE, DoctorReportsData.reports("demo-ravi").single().category)
        assertEquals(DoctorReportCategory.LAB_RESULTS, DoctorReportsData.reports("demo-leela").single().category)
        assertEquals(DoctorReportCategory.SCANS, DoctorReportsData.reports("demo-imran").single().category)
        assertEquals(DoctorReportCategory.CLINIC_LETTERS, DoctorReportsData.reports("demo-maya").single().category)
        assertEquals(DoctorReportCategory.OTHER, DoctorReportsData.reports("demo-joseph").single().category)
    }
    @Test fun searchAndCategoryFiltersWorkTogetherWithoutInventingItems() {
        val reports = DoctorDemoData.patients.flatMap { DoctorReportsData.reports(it.id) }
        assertEquals(2, DoctorReportsData.filter(reports, "", DoctorReportCategory.CLINIC_LETTERS).size)
        assertEquals(2, DoctorReportsData.filter(reports, " FOLLOW-UP ", DoctorReportCategory.CLINIC_LETTERS).size)
        assertEquals(1, DoctorReportsData.filter(reports, "discussion requested", DoctorReportCategory.CLINIC_LETTERS).size)
        assertTrue(DoctorReportsData.filter(reports, "no match", null).isEmpty())
        assertTrue(DoctorReportsData.filter(reports, "", DoctorReportCategory.PRESCRIPTIONS).isEmpty())
    }
    @Test fun countsAreSummaryCountsAndIncludeEmptyCategories() {
        val reports = DoctorReportsData.reports("demo-leela"); val counts = DoctorReportsData.counts(reports)
        assertEquals(DoctorReportCategory.entries.size, counts.size); assertEquals(1, counts[DoctorReportCategory.LAB_RESULTS]); assertEquals(0, counts[DoctorReportCategory.PATHOLOGY]); assertEquals(reports.size, counts.values.sum())
    }
}
