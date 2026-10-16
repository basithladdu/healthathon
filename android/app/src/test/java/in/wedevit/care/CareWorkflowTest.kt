package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CareWorkflowTest {
    private val day = LocalDate.of(2026, 10, 16)

    @Test fun ordinaryNotesAndJsonAreNotReinterpretedAsWorkflows() {
        assertNull(CareEntryCodec.decode("Collect medicines after lunch"))
        assertNull(CareEntryCodec.decode("{\"type\":\"task\",\"assignee\":\"A\"}"))
        assertEquals("Original directions", CareEntryCodec.readable("Original directions"))
    }

    @Test fun structuredRoundTripKeepsRecipientSourceAndStatusEvidence() {
        val entry = CareEntry("task", detail = "Bring the report\nKeep original", assignee = "Ayesha", sender = "family@example.test", status = "accepted", acceptedBy = "Ayesha", acceptedAt = 123)
        assertEquals(entry, CareEntryCodec.decode(CareEntryCodec.encode(entry)))
    }

    @Test fun corruptOrFuturePayloadDoesNotThrowOrRenderJson() {
        assertNull(CareEntryCodec.decode(CareEntryCodec.PREFIX + "broken"))
        assertNull(CareEntryCodec.decode(CareEntryCodec.PREFIX + "{\"type\":\"unknown\"}"))
        assertEquals("This entry needs a newer app to open.", CareEntryCodec.readable(CareEntryCodec.PREFIX + "broken"))
    }

    @Test fun taskRequiresANamedRecipient() {
        assertThrows(IllegalArgumentException::class.java) { CareWorkflowRules.validate("Collect report", CareEntry("task")) }
        CareWorkflowRules.validate("Collect report", CareEntry("task", assignee = "Fatima"))
    }

    @Test fun taskCannotSkipAcceptanceAndKeepsWhoDidEachStep() {
        val pending = CareEntry("task", assignee = "Ayesha")
        assertThrows(IllegalArgumentException::class.java) { CareWorkflowRules.transition(pending, "completed", "Ayesha", 20) }
        val accepted = CareWorkflowRules.transition(pending, "accepted", "Ayesha", 10)
        val completed = CareWorkflowRules.transition(accepted, "completed", "Sara", 20)
        assertEquals("Ayesha", completed.acceptedBy)
        assertEquals(10L, completed.acceptedAt)
        assertEquals("Sara", completed.completedBy)
        assertEquals(20L, completed.completedAt)
        assertEquals("completed", completed.status)
    }

    @Test fun transitionRequiresNameAndRejectsRepeatedCompletion() {
        assertThrows(IllegalArgumentException::class.java) { CareWorkflowRules.transition(CareEntry("task"), "accepted", " ") }
        assertThrows(IllegalArgumentException::class.java) { CareWorkflowRules.transition(CareEntry("task", status = "completed"), "completed", "A") }
    }

    @Test fun cancelledAndCompletedEntriesCanBeReopenedWithoutOldCurrentStatus() {
        val reopened = CareWorkflowRules.transition(CareEntry("conversation", status = "completed", acceptedBy = "Doctor", acceptedAt = 10, completedBy = "Doctor", completedAt = 20), "open", "Family", 30)
        assertEquals("open", reopened.status)
        assertEquals("", reopened.acceptedBy)
        assertEquals(0L, reopened.completedAt)
    }

    @Test fun numericLabValuesAndPrintedRangesValidateWithoutInterpretation() {
        val lab = CareEntry("lab", value = "12.4", unit = "g/dL", rangeLow = "12", rangeHigh = "16", rangeText = "See report's age-specific note")
        CareLabRules.validate("Haemoglobin", lab, careEpoch(day), day)
        assertEquals(lab, CareEntryCodec.decode(CareEntryCodec.encode(lab)))
    }

    @Test fun rejectsNonnumericNonfiniteMissingUnitsAndReversedLimits() {
        listOf("NaN", "Infinity", "12 mg", "", "1,200", "1e309").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { CareLabRules.validate("Test", CareEntry("lab", value = value, unit = "mg/dL"), careEpoch(day), day) }
        }
        assertThrows(IllegalArgumentException::class.java) { CareLabRules.validate("Test", CareEntry("lab", value = "12", unit = ""), careEpoch(day), day) }
        assertThrows(IllegalArgumentException::class.java) { CareLabRules.validate("Test", CareEntry("lab", value = "12", unit = "mg/dL", rangeLow = "16", rangeHigh = "12"), careEpoch(day), day) }
    }

    @Test fun reportDatesMustBeRealAndNotFuture() {
        val lab = CareEntry("lab", value = "12", unit = "g/dL")
        assertThrows(IllegalArgumentException::class.java) { CareLabRules.validate("Test", lab, 0, day) }
        assertThrows(IllegalArgumentException::class.java) { CareLabRules.validate("Test", lab, careEpoch(day.plusDays(1)), day) }
    }

    @Test fun flagsCompareOnlyTheLimitsCopiedForThatResult() {
        val result = CareEntry("lab", value = "11.9", unit = "g/dL", rangeLow = "12", rangeHigh = "16")
        assertEquals("Low · below printed range", CareLabRules.printedFlag(result))
        assertEquals("Within printed range", CareLabRules.printedFlag(result.copy(value = "12")))
        assertEquals("Within printed range", CareLabRules.printedFlag(result.copy(value = "16")))
        assertEquals("High · above printed range", CareLabRules.printedFlag(result.copy(value = "16.1")))
    }

    @Test fun flagsDoNotInventLimitsOrInterpretTextualRanges() {
        val result = CareEntry("lab", value = "12", unit = "g/dL")
        assertNull(CareLabRules.printedFlag(result))
        assertNull(CareLabRules.printedFlag(result.copy(rangeLow = "16", rangeHigh = "12")))
        assertNull(CareLabRules.printedFlag(result.copy(rangeLow = "10", rangeHigh = "20", rangeText = "Age-specific: see report")))
        assertNull(CareLabRules.printedFlag(result.copy(value = "NaN", rangeLow = "10", rangeHigh = "20")))
    }

    @Test fun historiesNeverMergeIncompatibleUnits() {
        val entries = listOf(
            CareItem(personId = "p", kind = "task", title = "Creatinine", detail = CareEntryCodec.encode(CareEntry("lab", value = "1", unit = "mg/dL")), due = careEpoch(day)),
            CareItem(personId = "p", kind = "task", title = "creatinine", detail = CareEntryCodec.encode(CareEntry("lab", value = "88", unit = "µmol/L")), due = careEpoch(day)),
            CareItem(personId = "p", kind = "task", title = "Creatinine", detail = CareEntryCodec.encode(CareEntry("lab", value = "1.1", unit = "mg/dL")), due = careEpoch(day.minusDays(1)))
        )
        val grouped = labGroups(entries)
        assertEquals(2, grouped.size)
        assertEquals(2, grouped.first { it.first().second.unit == "mg/dL" }.size)
        assertEquals("1", grouped.first { it.first().second.unit == "mg/dL" }.first().second.value)
    }

    @Test fun calendarIncludesActualDatedKindsButNotLabsOrCancelledRequests() {
        val date = careEpoch(day)
        val entries = listOf(
            CareItem(personId = "p", kind = "medicine", title = "Medicine", due = date),
            CareItem(personId = "p", kind = "visit", title = "Visit", due = date),
            CareItem(personId = "p", kind = "task", title = "Test", due = date, detail = CareEntryCodec.encode(CareEntry("schedule", category = "Test"))),
            CareItem(personId = "p", kind = "task", title = "Result", due = date, detail = CareEntryCodec.encode(CareEntry("lab", value = "12", unit = "g/dL"))),
            CareItem(personId = "p", kind = "task", title = "Cancelled", due = date, detail = CareEntryCodec.encode(CareEntry("conversation", status = "cancelled"))),
            CareItem(personId = "p", kind = "medicine", title = "No date"),
            CareItem(personId = "p", kind = "visit", title = "Tomorrow", due = careEpoch(day.plusDays(1)))
        )
        assertEquals(listOf("Medicine", "Test", "Visit"), calendarItems(entries, day).map { it.title })
    }

    @Test fun datedPlainTasksKeepTheirOrdinaryEditorInsteadOfBecomingVisits() {
        val task = CareItem(personId = "p", kind = "task", title = "Bring reports", detail = "Original instructions", due = careEpoch(day))
        assertTrue(usePlainCalendarTaskEditor(task))
        assertEquals("Task", calendarCategory(task))
        assertFalse(usePlainCalendarTaskEditor(task.copy(detail = CareEntryCodec.encode(CareEntry("schedule", category = "Visit")))))
        assertFalse(usePlainCalendarTaskEditor(task.copy(detail = CareEntryCodec.PREFIX + "future payload")))
        assertFalse(usePlainCalendarTaskEditor(task.copy(kind = "visit")))
        assertFalse(usePlainCalendarTaskEditor(task.copy(kind = "medicine")))
    }
}
