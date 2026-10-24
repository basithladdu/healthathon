package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DoctorVisitContractTest {
    private val at = Instant.parse("2026-10-24T09:45:00Z").toEpochMilli()

    @Test fun doctorVisitPreservesExactTimeAndDeclaredDoctorWithoutChangingOtherPeople() {
        val item = DoctorVisitRules.item("qa-visit", "qa-person", "QA test doctor", at, "Synthetic clinic", "Bring sample reports")
        assertEquals("qa-visit", item.id)
        assertEquals("qa-person", item.personId)
        assertEquals("visit", item.kind)
        assertEquals(at, item.due)
        val entry = requireNotNull(CareEntryCodec.decode(item.detail))
        assertEquals("schedule", entry.type)
        assertEquals("Visit", entry.category)
        assertTrue(entry.timed)
        assertEquals("QA test doctor", entry.sender)
        assertEquals("Synthetic clinic", entry.location)
        assertEquals("Bring sample reports", entry.detail)
    }

    @Test fun doctorVisitCannotPretendToSaveWithoutANameOrTime() {
        assertThrows(IllegalArgumentException::class.java) { DoctorVisitRules.validate("", at, "", "") }
        assertThrows(IllegalArgumentException::class.java) { DoctorVisitRules.validate("QA test doctor", 0, "", "") }
        assertThrows(IllegalArgumentException::class.java) { DoctorVisitRules.validate("QA test doctor", -1, "", "") }
    }

    @Test fun ordinaryOldVisitTextRemainsPlainAndIsNotReinterpreted() {
        val original = "Clinic appointment after lunch"
        assertNull(CareEntryCodec.decode(original))
        assertEquals(original, CareEntryCodec.readable(original))
    }
}
