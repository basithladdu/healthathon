package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorOverviewDataTest {
    private val now = DoctorPatientTracking.asOf + 86_400_000L
    private fun version(id: String, number: Int, time: Long = now - 1000, patient: String = "demo-asha") = WorkflowVersion(
        id, patient, number, WorkflowDraft(patient, sourceText = "Fictional exact source $id", questions = "Questions from $id"), "Self-entered reviewer $id", time)

    @Test fun latestUsableSnapshotIsPatientScopedAndVersionNumberPrecedesClockTime() {
        val olderNumberLaterClock = version("v1", 1, now - 1)
        val higherNumberEarlierClock = version("v2", 2, now - 2000)
        val sameNumberLaterClock = version("v2-newer-time", 2, now - 1000)
        val otherPatient = version("unrelated", 999, patient = "demo-ravi")
        val result = DoctorOverviewData.build("demo-asha", listOf(otherPatient, olderNumberLaterClock, higherNumberEarlierClock, sameNumberLaterClock), emptyList(), now)!!
        assertEquals("v2-newer-time", result.lastDiscussion!!.id)
        assertEquals(sameNumberLaterClock.draft.sourceText, result.lastDiscussion!!.sourceText)
        assertTrue(result.lastDiscussion!!.isPublishedPreview)
        assertTrue(result.history.all { it.patientId == "demo-asha" })
        assertFalse(result.history.any { it.id == "unrelated" })
    }

    @Test fun malformedOrUnpublishedFutureSnapshotsDoNotBecomeLastDiscussion() {
        val good = version("good", 1)
        val invalid = listOf(
            good.copy(id = ""), good.copy(number = 0), good.copy(publishedAt = 0), good.copy(publishedAt = now + 1),
            good.copy(reviewerName = " "), good.copy(draft = good.draft.copy(patientId = "demo-ravi")),
            good.copy(draft = good.draft.copy(sourceText = "")), good.copy(draft = good.draft.copy(questions = "x".repeat(4001))),
            good.copy(draft = good.draft.copy(sourceIds = listOf("demo-ravi-prior"))))
        invalid.forEach { assertFalse(DoctorOverviewData.usableVersion(it, "demo-asha", now)) }
        val result = DoctorOverviewData.build("demo-asha", invalid, emptyList(), now)!!
        assertFalse(result.lastDiscussion!!.isPublishedPreview)
        assertEquals("demo-asha-prior", result.lastDiscussion!!.id)
    }

    @Test fun partialLatestHeadingsStayBlankInsteadOfBorrowingOlderPreferences() {
        val older = version("older", 1).copy(draft = WorkflowDraft("demo-asha", sourceText = "Old fictional source", priorities = "Old preference", nextSteps = "Old next step"))
        val latest = version("latest", 2)
        val note = DoctorOverviewData.build("demo-asha", listOf(older, latest), emptyList(), now)!!.lastDiscussion!!
        assertEquals("latest", note.id)
        assertEquals("", note.priorities)
        assertEquals("", note.nextSteps)
        assertEquals(latest.draft.questions, note.questions)
    }

    @Test fun undatedSampleAndIllustrativeProfessionalRemainSeparateFromUnknownAuthor() {
        val result = DoctorOverviewData.build("demo-asha", emptyList(), emptyList(), now)!!
        val prior = result.lastDiscussion!!
        assertNull(prior.publishedAt)
        assertNull(prior.reviewerName)
        assertNull(prior.versionNumber)
        assertEquals(DoctorWorkflowData.sources("demo-asha").first().text, prior.sourceText)
        assertTrue(prior.priorities.isBlank())
        assertEquals("Previous clinician", result.previousProfessional!!.role)
        assertEquals(prior.sourceId, result.previousProfessional!!.sourceId)
        assertTrue(result.previousProfessional!!.disclosure.contains("Not a verified note author or an assigned doctor"))
        val withReviewer = DoctorOverviewData.build("demo-asha", listOf(version("entered-name", 1)), emptyList(), now)!!
        assertNotEquals(withReviewer.lastDiscussion!!.reviewerName, withReviewer.previousProfessional!!.name)
        assertTrue(withReviewer.lastDiscussion!!.sourceLabel.contains("unverified self-entered"))
    }

    @Test fun reportedUpdateIsAttributedAndDoesNotBecomeClinicalAssessment() {
        val result = DoctorOverviewData.build("demo-asha", emptyList(), emptyList(), now)!!
        assertEquals("Not recorded", result.clinicalAssessment)
        assertEquals("demo-asha", result.latestReport!!.patientId)
        assertTrue(result.latestReport!!.at > 0 && result.latestReport!!.at <= now)
        assertEquals(SampleReportSource.PATIENT, result.latestReport!!.source)
        assertNull(DoctorOverviewData.build("demo-asha", emptyList(), emptyList(), 1)!!.latestReport)
        assertNull(DoctorOverviewData.build("unknown-patient", emptyList(), emptyList(), now))
    }

    @Test fun upcomingVisitMustBeFutureScheduledAndForThisPatient() {
        val valid = DoctorDemoAppointment("valid", "demo-asha", now + 1000, 30, "Phone")
        val visits = listOf(valid.copy(id = "later", startMillis = now + 2000), valid.copy(id = "cancelled", startMillis = now + 1, status = "Cancelled"),
            valid.copy(id = "completed", startMillis = now + 2, status = "Completed"), valid.copy(id = "past", startMillis = now - 1),
            valid.copy(id = "right-now", startMillis = now), valid.copy(id = "other-patient", patientId = "demo-ravi", startMillis = now + 3), valid)
        assertEquals("valid", DoctorOverviewData.build("demo-asha", emptyList(), visits, now)!!.nextVisit!!.id)
        assertNull(DoctorOverviewData.build("demo-asha", emptyList(), visits.filterNot { it.id in listOf("valid", "later") }, now)!!.nextVisit)
    }
}
