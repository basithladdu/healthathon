package `in`.wedevit.care

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * A one-tap "Taken" waits [windowMs] before it is written, while the screen says "Marked taken · Undo".
 * Undo drops it, so no intake report is ever written: nothing to delete later and no Missed added (the server
 * history is append-only). A wrong tick on a strong pain medicine hides a dose that is still due, so this matters.
 *
 * The tick is written at once when the window ends, when another dose is marked ([hold] or [flush]), when the
 * screen closes, or when the app goes to the background, so a real dose is never left unsaved.
 */
internal class DoseUndoWindow(private val scope: CoroutineScope, private val windowMs: Long = WINDOW_MS) {
    /** One held tick. [event] is shown in place of the report until [write] runs. */
    class Pending(val personId: String, val itemId: String, val doseKey: String, val title: String, val doseLabel: String,
                  val event: MedicineIntakeEvent, internal val write: suspend () -> Unit)

    val pending = MutableStateFlow<Pending?>(null)
    /** The last write that failed after its screen may have closed; shown as a dialog by the dose lists (item id to message; a blank message means the usual "Couldn't save"). */
    val failure = MutableStateFlow<Pair<String, String>?>(null)
    private var timer: Job? = null

    @Synchronized private fun take(): Pending? = pending.value?.also { pending.value = null }

    /** Holds [next]; a tick already waiting is written first. */
    suspend fun hold(next: Pending) {
        flush()
        synchronized(this) { pending.value = next; timer = scope.launch { delay(windowMs); take()?.let { runWrite(it) } } }
    }

    /** Drops the waiting tick without writing anything. False when there was none (already written). */
    fun undo(): Boolean {
        take() ?: return false
        timer?.cancel(); timer = null
        return true
    }

    /** Writes the waiting tick now. */
    suspend fun flush() {
        val waiting = take() ?: return
        timer?.cancel(); timer = null
        runWrite(waiting)
    }

    /** [flush] from places that cannot wait (screen closing, app in background). */
    fun flushSoon() { if (pending.value != null) scope.launch { flush() } }

    private suspend fun runWrite(p: Pending) {
        try { p.write() } catch (e: Exception) { if (e is CancellationException) throw e; failure.value = p.itemId to (e as? IllegalArgumentException)?.message.orEmpty() }
    }

    companion object {
        const val WINDOW_MS = 6_000L
        /** One window for the whole app: it outlives a screen, so a held tick is still written after Back. */
        val app: DoseUndoWindow by lazy { DoseUndoWindow(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }

        /** Reports with the held tick added, for showing dose states while it waits. */
        fun shown(events: List<MedicineIntakeEvent>, held: Pending?, personId: String): List<MedicineIntakeEvent> =
            if (held == null || held.personId != personId) events else events + held.event
    }
}
