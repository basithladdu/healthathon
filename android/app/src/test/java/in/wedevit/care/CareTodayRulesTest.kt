package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareTodayRulesTest {
    private val dayStart = 1_800_000_000_000L
    private fun visit(id: String, due: Long, detail: String = "") = CareItem(id, "p1", "visit", id, detail, due)

    @Test fun upNextShowsDatedVisitsSoonestFirstAndUndatedOnesLast() {
        val rows = listOf(visit("undated", 0), visit("later", dayStart + 5_000), visit("soon", dayStart + 1_000), visit("yesterday", dayStart - 1))
        assertEquals(listOf("soon", "later", "undated"), CareTodayRules.upcomingVisits(rows, dayStart).map { it.id })
    }

    @Test fun upNextDropsFinishedDoctorVisitsAndOtherStructuredEntries() {
        val planned = CareEntryCodec.encode(CareEntry("schedule", category = "Visit"))
        val done = CareEntryCodec.encode(CareEntry("schedule", category = "Visit", status = "completed"))
        val conversation = CareEntryCodec.encode(CareEntry("conversation"))
        val rows = listOf(visit("planned", dayStart + 10, planned), visit("done", dayStart + 10, done), visit("talk", dayStart + 10, conversation), CareItem("task", "p1", "task", "Buy pads", due = dayStart + 10))
        assertEquals(listOf("planned"), CareTodayRules.upcomingVisits(rows, dayStart).map { it.id })
    }

    @Test fun todaysCheckInIgnoresSymptomDiaryNotesAndEarlierDays() {
        val symptom = CareItem("symptom", "p1", "checkin", "Pain", SymptomNoteCodec.encode(SymptomNote("Pain", 7, "2026-10-27", "12:00", "now", "patient", "Person")), created = dayStart + 30)
        val yesterday = CareItem("old", "p1", "checkin", "Good", created = dayStart - 10)
        assertNull(CareTodayRules.todayCheckIn(listOf(symptom, yesterday), dayStart))
        val today = CareItem("today", "p1", "checkin", "Okay", created = dayStart + 20)
        assertEquals("today", CareTodayRules.todayCheckIn(listOf(symptom, today, yesterday), dayStart)?.id)
    }

    @Test fun emptySectionsAreAFewWordsWithoutInstructions() {
        for (section in listOf("visits", "tasks", "journal")) {
            val editable = CareTodayRules.emptyText(section, canEdit = true)
            val readOnly = CareTodayRules.emptyText(section, canEdit = false)
            assertFalse(section, editable.contains("Tap "))
            assertFalse(section, readOnly.contains("Tap "))
            // Every string carries its Hindi half after the separator.
            for (text in listOf(editable, readOnly)) assertTrue(text, text.substringAfter(" · ", "").any { it in 'ऀ'..'ॿ' })
        }
    }

    @Test fun aReminderOpensThePersonWhoseSavedItemIsDue() {
        val snapshot = JSONArray().put(JSONObject().put("id", "m1").put("person", "rahul")).put(JSONObject().put("id", "v1").put("person", "asha")).toString()
        assertEquals("asha", CareTodayRules.personForItem(snapshot, "v1"))
        assertEquals("", CareTodayRules.personForItem(snapshot, "gone"))
        assertEquals("", CareTodayRules.personForItem(null, "v1"))
        assertEquals("", CareTodayRules.personForItem("not json", "v1"))
    }
}
