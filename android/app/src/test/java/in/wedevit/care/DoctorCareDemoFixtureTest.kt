package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Pure fixture audit: never opens a Room database or an account. */
class DoctorCareDemoFixtureTest {
    private val now = Instant.parse("2026-10-23T06:30:00Z").toEpochMilli()
    private fun fixture() = DoctorCareDemoData.fixture(now, ZoneId.of("Asia/Kolkata"))

    @Test fun identifiersAreStableAndNeverBorrowAnActualProfile() {
        val one=fixture(); val two=fixture()
        assertEquals(one,two)
        assertEquals(DoctorCareDemoData.PERSON_ID,one.person.id)
        assertNotEquals("a",one.person.id);assertNotEquals("asha",one.person.id)
        assertEquals("Meena Krishnan",one.person.name)
        val ids=one.items.map {it.id}+one.versions.map {it.id}+one.contacts.map {it.id}+one.reports.map {it.id}
        assertEquals(ids.size,ids.toSet().size)
        ids.forEach {UUID.fromString(it)}
        assertTrue(one.items.all {it.personId==one.person.id})
        assertTrue(one.versions.all {it.personId==one.person.id})
        assertTrue(one.contacts.all {it.personId==one.person.id})
        assertTrue(one.reports.all {it.personId==one.person.id})
    }
    @Test fun historicalNotesRetainFullContentAndNeverClaimVerification() {
        val data=fixture()
        assertEquals(listOf(1,2),data.versions.map {it.version})
        data.versions.forEach { note ->
            assertTrue(note.source.contains("No treatment decision is recorded."))
            assertTrue(NoteRules.values(note).all {it.isNotBlank()})
            assertEquals(ReviewWorkflow.NOT_VERIFIED,note.reviewerVerification)
            assertEquals(ReviewWorkflow.REVIEWED_UNVERIFIED,note.reviewStatus)
            assertEquals(64,note.contentHash.length)
        }
        assertNotEquals(data.versions[0].questions,data.versions[1].questions)
        assertNotEquals(data.versions[0].contentHash,data.versions[1].contentHash)
        assertTrue(data.draft.source.contains("Unfinished draft"))
        assertFalse(data.versions.any {it.source.contains("Unfinished draft")})
    }
    @Test fun datesMedicineReportsAndDocumentsStayHonest() {
        val data=fixture()
        assertEquals(7,data.items.count {it.kind=="checkin"})
        assertEquals(3,data.items.count {it.kind=="medicine"})
        data.items.filter {it.kind=="medicine"}.also {meds->assertEquals(meds.size,meds.map {it.title}.toSet().size);assertTrue(meds.all {it.title.isNotBlank() && it.detail.isNotBlank()})}
        assertTrue(data.intake.all {event->data.items.any {it.id==event.medicineId && it.kind=="medicine"}})
        val dateOnly=data.intake.single {it.source=="daily-checkoff"}
        assertNull(dateOnly.reportedTakenAt)
        assertTrue(dateOnly.reportedDate.isNotBlank())
        assertEquals(listOf("Care discussion summary.png","Blood test results.png"),data.reports.map {it.name});assertTrue(data.reports.all {it.fileName.startsWith("demo-") && !it.fileName.contains("..")})
        val visits=data.items.filter {it.kind=="visit"}
        assertEquals(5,visits.size)
        assertTrue(visits.all {CareEntryCodec.decode(it.detail)?.sender.orEmpty() in setOf("Dr Anita Sen","Dr Vikram Rao")})
    }
}
