package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PatientWalkthroughTest {
    private val now=Instant.parse("2026-10-23T10:00:00Z").toEpochMilli()
    private val utc=ZoneId.of("UTC")
    private val rahul=DoctorTestProfiles.profiles.first {it.key=="rahul"}
    @Test fun usersMapToFixedRolesAndNeverDoctor() {
        assertEquals(listOf("Patient","Family"),PatientWalkthrough.users.map {it.role})
        assertEquals("Rahul · Patient",PatientWalkthrough.user("rahul")!!.label);assertEquals("Arjun · Family",PatientWalkthrough.user("arjun")!!.label)
        assertNull(PatientWalkthrough.user("doctor"));assertNull(PatientWalkthrough.user(null))
        assertTrue(PatientWalkthrough.users.none {it.role=="Doctor"})
    }
    @Test fun onlyRahulsRecordIsVisible() {
        val arjunPatient=DoctorTestProfiles.profiles.first {it.key=="arjun"}
        val all=listOf(Person(DoctorTestProfiles.personId(arjunPatient),"Arjun",1),Person(DoctorTestProfiles.personId(rahul),"Rahul",1),Person(DoctorCareDemoData.PERSON_ID,"Other",1))
        assertEquals(listOf("Rahul"),PatientWalkthrough.visiblePeople(all).map {it.name})
    }
    @Test fun rahulsFamilyLineIncludesArjunAndOtherPatientIsUnchanged() {
        assertTrue(rahul.family.contains("Arjun · brother"))
        assertEquals("Nisha · mother / Dev · brother",DoctorTestProfiles.profiles.first {it.key=="arjun"}.family)
    }
    @Test fun baseFixtureIsUnchangedAndExtrasAreAdditive() {
        val base=DoctorTestProfiles.fixture(rahul,now,utc);val full=DoctorTestProfiles.fixture(rahul,now,utc,walkthrough=true)
        assertEquals(base,DoctorTestProfiles.fixture(rahul,now,utc))
        assertEquals(3,base.contacts.size);assertEquals(2,base.versions.size)
        assertTrue(full.items.map {it.id}.containsAll(base.items.map {it.id}));assertTrue(full.versions.map {it.id}.containsAll(base.versions.map {it.id}))
        val extra=PatientWalkthrough.extras(now,utc)
        assertEquals(full.items.size-base.items.size,extra.items.size)
        assertEquals(listOf("Arjun","Dr Meera Nair"),extra.contacts.map {it.name})
        assertEquals(listOf("Dr Meera Nair"),extra.versions.map {it.doctor});extra.versions.forEach {DoctorConversationContext.validateVersion(it);assertFalse(it.reviewerVerification=="verified")}
        assertTrue(extra.items.all {it.personId==full.person.id})
        assertEquals(3,extra.items.count {it.kind=="task"});assertEquals(2,extra.items.count {it.kind=="journal"});assertEquals(1,extra.items.count {it.kind=="visit"})
        assertTrue(extra.items.filter {it.kind=="journal"}.all {it.detail.contains("Arjun (brother)")})
        assertTrue(CareEntryCodec.decode(extra.items.first {it.kind=="visit"}.detail)!!.sender.isNotBlank())
        assertEquals(extra,PatientWalkthrough.extras(now,utc))
    }
    @Test fun otherPatientFixtureIgnoresWalkthroughFlag() {
        val arjun=DoctorTestProfiles.profiles.first {it.key=="arjun"}
        assertEquals(DoctorTestProfiles.fixture(arjun,now,utc),DoctorTestProfiles.fixture(arjun,now,utc,walkthrough=true))
    }
}
