package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DoctorTestProfilesTest {
    private val now=Instant.parse("2026-10-23T10:00:00Z").toEpochMilli()
    @Test fun twoProfilesHaveDisjointIdsAndCompleteIndependentCare() {
        val fixtures=DoctorTestProfiles.profiles.map {DoctorTestProfiles.fixture(it,now,ZoneId.of("UTC"))}
        assertEquals(listOf("Rahul","Arjun"),fixtures.map {it.person.name})
        val ids=fixtures.map {f->(f.items.map {it.id}+f.versions.map {it.id}+f.contacts.map {it.id}+f.reports.map {it.id}+f.person.id).toSet()}
        assertTrue(ids[0].intersect(ids[1]).isEmpty())
        fixtures.forEach {f->
            assertNotEquals(DoctorCareDemoData.PERSON_ID,f.person.id)
            assertEquals(11,f.items.count {it.kind=="checkin"&&!SymptomNoteCodec.isStructured(it.detail)&&!DistressCodec.isStructured(it.detail)});assertEquals(6,f.items.count {it.kind=="visit"})
            assertEquals(5,f.items.count {it.kind=="medicine"});assertEquals(4,f.reports.size);assertEquals(3,f.contacts.size)
            assertTrue(f.items.all {it.personId==f.person.id});assertTrue(f.contacts.all {it.personId==f.person.id&&it.phone.isBlank()})
            DoctorConversationContext.validate(f.draft.source,f.draft.contextJson)
            f.versions.forEach {v->DoctorConversationContext.validateVersion(v);assertFalse(v.reviewerVerification=="verified")}
            assertEquals(5,DoctorConversationContext.goals(f.draft).size)
            val goals=DoctorConversationContext.goals(f.draft).associateBy {it.key}
            assertEquals(f.draft.priorities,goals.getValue("patient_priorities").answer)
            assertEquals(f.draft.questions,goals.getValue("understanding_questions").answer)
            assertEquals(f.draft.participants,goals.getValue("participants").answer)
            assertEquals(f.draft.nextSteps,goals.getValue("next_steps").answer)
            assertEquals("undecided",goals.getValue("concerns_uncertainty").status)
            goals.values.forEach {assertEquals(it.answer,it.evidence.single().quote)}
            assertTrue(f.intake.any {it.reportedDate.isNotBlank()&&it.reportedTakenAt==null})
            val diary=f.items.filter {it.kind=="checkin"&&(SymptomNoteCodec.isStructured(it.detail)||DistressCodec.isStructured(it.detail))}
            if(f.person.name=="Rahul") {
                assertEquals(listOf(6,4,null),DistressRules.history(f.items,f.person.id).reversed().map {it.second.score})
                assertEquals(listOf(7,5),SymptomDiaryRules.painPartSeries(f.items,f.person.id,"head").map {it.value})
                assertEquals(listOf(6,4),SymptomDiaryRules.painPartSeries(f.items,f.person.id,"abdomen").map {it.value})
                assertEquals(listOf(4),SymptomDiaryRules.moodSeries(f.items,f.person.id,"low-mood").map {it.value})
                assertTrue(diary.all {d->DistressCodec.decode(d.detail)!=null||SymptomNoteCodec.decode(d.detail)!=null})
            } else assertTrue(diary.isEmpty())
            f.items.filter {it.kind=="visit"}.forEach {visit->
                val links=DoctorVisitDetails.decode(CareEntryCodec.decode(visit.detail)!!.detail)
                if(links.conditionRef.isNotBlank())assertTrue(f.items.single {it.id==links.conditionRef}.created<=visit.due)
                if(links.discussionRef.isNotBlank())assertTrue(f.versions.single {it.id==links.discussionRef}.published<=visit.due)
            }
        }
    }
    @Test fun seedGatePreservesExistingPracticeAndLegacyFixture() {
        val before=DoctorCareDemoData.fixture(now,ZoneId.of("UTC"))
        val p=DoctorTestProfiles.profiles.first()
        assertTrue(DoctorTestProfiles.needsSeed(p,setOf(before.person.id)))
        assertFalse(DoctorTestProfiles.needsSeed(p,setOf(before.person.id,DoctorTestProfiles.personId(p))))
        assertFalse(DoctorTestProfiles.needsSeed(p,emptySet(),completed=true))
        assertEquals(before,DoctorCareDemoData.fixture(now,ZoneId.of("UTC")))
        assertEquals(DoctorTestProfiles.fixture(p,now),DoctorTestProfiles.fixture(p,now))
    }
}
