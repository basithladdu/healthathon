package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorAttendeesTest {
    private fun store(): Pair<DoctorAttendeeStore, MutableMap<String, String>> {
        val backing = mutableMapOf<String, String>()
        return DoctorAttendeeStore({ backing[it] }, { k, v -> backing[k] = v; true }) to backing
    }
    private fun rows(vararg pairs: Pair<String, Boolean>) = pairs.mapIndexed { i, (name, ok) -> DoctorAttendee("id-$i", name, "Daughter", ok) }

    @Test fun startingListNamesDoctorAndPatientWithoutPreTickedConsent() {
        val start = doctorAttendeeStartingList("Dr Meera Nair", "Rahul")
        assertEquals(listOf("Dr Meera Nair", "Rahul"), start.map { it.name })
        assertEquals(listOf("Doctor", "Patient"), start.map { it.role })
        assertTrue("Consent must never be pre-ticked", start.none { it.consented })
        assertEquals(2, start.map { it.id }.distinct().size)
    }

    @Test fun recordingIsBlockedUntilEveryoneHasConsented() {
        assertFalse(doctorAttendeesAllConsented(emptyList()))
        assertFalse(doctorAttendeesAllConsented(rows("Rahul" to true, "Meera" to false)))
        assertTrue(doctorAttendeesAllConsented(rows("Rahul" to true, "Meera" to true)))
    }

    @Test fun whoWasThereListsEveryNameWithItsRole() {
        val list = listOf(DoctorAttendee("a", "Dr Meera Nair", "Doctor", true), DoctorAttendee("b", " Rahul ", " Patient ", true))
        assertEquals("Dr Meera Nair (Doctor), Rahul (Patient)", doctorAttendeesWhoWasThere(list))
        assertEquals("", doctorAttendeesWhoWasThere(emptyList()))
    }

    @Test fun savedListReturnsExactlyWhatWasStored() {
        val (s, backing) = store()
        val scope = DoctorAttendeeStore.scope("acct", "person", "discussion")
        assertEquals(emptyList<DoctorAttendee>(), s.load(scope))
        val saved = rows("Rahul" to true, "Meera" to false)
        s.save(scope, saved)
        assertEquals(saved, s.load(scope))
        assertTrue(backing.containsKey(scope))
    }

    @Test fun scopeSeparatesPeopleAndDiscussionsAndRejectsBlankPerson() {
        val a = DoctorAttendeeStore.scope("acct", "rahul", "d1")
        assertNotEquals(a, DoctorAttendeeStore.scope("acct", "arjun", "d1"))
        assertNotEquals(a, DoctorAttendeeStore.scope("acct", "rahul", "d2"))
        assertNotEquals(a, DoctorAttendeeStore.scope("other", "rahul", "d1"))
        assertEquals(a, DoctorAttendeeStore.scope("acct", "rahul", "d1"))
        assertThrows(IllegalArgumentException::class.java) { DoctorAttendeeStore.scope("acct", " ", "d1") }
    }

    @Test fun validationRejectsTooManyBlankOrDuplicateEntries() {
        assertThrows(IllegalArgumentException::class.java) { DoctorAttendeeStore.validate((1..13).map { DoctorAttendee("id-$it", "Name $it", "Nurse") }) }
        assertThrows(IllegalArgumentException::class.java) { DoctorAttendeeStore.validate(listOf(DoctorAttendee("same", "A", "Nurse"), DoctorAttendee("same", "B", "Nurse"))) }
        assertThrows(IllegalArgumentException::class.java) { DoctorAttendeeStore.validate(listOf(DoctorAttendee("a", "  ", "Nurse"))) }
        assertThrows(IllegalArgumentException::class.java) { DoctorAttendeeStore.validate(listOf(DoctorAttendee("", "A", "Nurse"))) }
        assertThrows(IllegalArgumentException::class.java) { DoctorAttendeeStore.validate(listOf(DoctorAttendee("a", "A", " "))) }
        assertThrows(IllegalArgumentException::class.java) { DoctorAttendeeStore.validate(listOf(DoctorAttendee("a", "x".repeat(101), "Nurse"))) }
        DoctorAttendeeStore.validate((1..12).map { DoctorAttendee("id-$it", "Name $it", "Nurse") })
    }

    @Test fun storedRowsWithoutConsentLoadAsNotConsented() {
        val (s, backing) = store()
        val scope = DoctorAttendeeStore.scope("acct", "person", "discussion")
        backing[scope] = """[{"id":"a","name":"Rahul","role":"Patient"}]"""
        val loaded = s.load(scope)
        assertEquals(1, loaded.size)
        assertFalse("A row with no consent field must not count as consented", loaded.single().consented)
        assertFalse(doctorAttendeesAllConsented(loaded))
    }
}
