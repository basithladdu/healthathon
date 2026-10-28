package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CareDailyFlowTest {
    private val today = LocalDate.of(2026, 10, 28)
    private fun med(id: String, done: String = "") = CareItem(id = id, personId = "p", kind = "medicine", title = id, doneOn = done)

    @Test fun medicinesStillToTickComeFirst() {
        val items = listOf(med("a", today.toString()), med("b"), med("c", "2026-10-27"), CareItem(personId = "p", kind = "task", title = "t"))
        assertEquals(listOf("b", "c", "a"), CareDailyFlow.medicinesForToday(items, today).map { it.id })
        assertEquals(1, CareDailyFlow.takenToday(items, today))
    }

    @Test fun newSymptomNoteStartsWithTheLastReporter() {
        fun note(respondent: String, author: String, created: Long) = CareItem(personId = "p", kind = "checkin", title = "Pain · 4", created = created,
            detail = SymptomNoteCodec.encode(SymptomNote("Pain", 4, "2026-10-27", "09:00", respondent = respondent, author = author)))
        val entries = listOf(note("patient", "Rahul", 1), note("caregiver", "Asha", 5), CareItem(personId = "p", kind = "checkin", title = CheckInMood.GOOD, created = 9),
            note("caregiver", "Other person", 7).copy(personId = "q"))
        assertEquals("caregiver" to "Asha", CareDailyFlow.lastReporter(entries, "p"))
        assertNull(CareDailyFlow.lastReporter(entries, "nobody"))
    }

    @Test fun oneTapCheckInSavesOnceAndChangesOnlyWhenDifferent() {
        assertEquals(CheckInMood.OKAY, CareDailyFlow.checkInChange(null, CheckInMood.OKAY)?.title)
        val saved = CareItem(id = "x", personId = "p", kind = "checkin", title = CheckInMood.OKAY, detail = "slept well")
        assertNull(CareDailyFlow.checkInChange(saved, CheckInMood.OKAY))
        val changed = CareDailyFlow.checkInChange(saved, CheckInMood.GOOD)!!
        assertEquals("x", changed.id); assertEquals(CheckInMood.GOOD, changed.title); assertEquals("slept well", changed.detail)
        assertNull(CareDailyFlow.checkInChange(null, "Felt tired"))
    }
}
