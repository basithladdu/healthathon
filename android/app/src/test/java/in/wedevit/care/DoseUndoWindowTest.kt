package `in`.wedevit.care

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A one-tap "Taken" is held with Undo: Undo writes nothing at all; otherwise it is written exactly once. */
class DoseUndoWindowTest {
    private fun event(id: String) = MedicineIntakeEvent(id, "med", "Morphine 5 mg", "taken", plannedAt = 1_000L, loggedAt = 2_000L, reporter = "Patient report")
    private fun pending(id: String, writes: MutableList<String>) = DoseUndoWindow.Pending("person", "med", "t:08:00", "Morphine 5 mg", "08:00", event(id)) { writes += id }

    @Test fun undoWritesNothing() = runBlocking {
        val writes = mutableListOf<String>()
        val window = DoseUndoWindow(this, windowMs = 60_000)
        window.hold(pending("a", writes))
        assertEquals("a", window.pending.value?.event?.id)
        assertTrue(window.undo())
        assertNull(window.pending.value)
        assertFalse(window.undo())
        window.flush()
        assertEquals(emptyList<String>(), writes)
    }

    @Test fun windowEndWritesOnce() = runBlocking {
        val writes = mutableListOf<String>()
        val window = DoseUndoWindow(this, windowMs = 20)
        window.hold(pending("a", writes))
        delay(200)
        window.flush()
        assertEquals(listOf("a"), writes)
        assertFalse(window.undo())
    }

    @Test fun anotherTapWritesTheHeldTickFirst() = runBlocking {
        val writes = mutableListOf<String>()
        val window = DoseUndoWindow(this, windowMs = 60_000)
        window.hold(pending("a", writes))
        window.hold(pending("b", writes))
        assertEquals(listOf("a"), writes)
        window.flush()
        assertEquals(listOf("a", "b"), writes)
    }

    @Test fun heldTickShowsOnlyForItsPerson() {
        val held = pending("a", mutableListOf())
        assertEquals(listOf("a"), DoseUndoWindow.shown(emptyList(), held, "person").map { it.id })
        assertEquals(emptyList<MedicineIntakeEvent>(), DoseUndoWindow.shown(emptyList(), held, "someone-else"))
        assertEquals(emptyList<MedicineIntakeEvent>(), DoseUndoWindow.shown(emptyList(), null, "person"))
    }

    @Test fun failedWriteIsReportedNotThrown() = runBlocking {
        val window = DoseUndoWindow(this, windowMs = 60_000)
        window.hold(DoseUndoWindow.Pending("person", "med", "t:08:00", "Morphine 5 mg", "08:00", event("a")) { throw IllegalArgumentException("This dose could not be found. Refresh and try again.") })
        window.flush()
        assertEquals("med" to "This dose could not be found. Refresh and try again.", window.failure.value)
    }
}
