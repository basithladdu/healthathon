package `in`.wedevit.care

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FeedbackQueueTest {
    private fun item(id: String, account: String = "account-a") = FeedbackSubmission(id, account, "patient", .75f, "Fictional interface note", 100L)

    @Test fun encodeDecodeRoundTripsAndRejectsBadRows() {
        val items = listOf(item("one"), item("two", "account-b"))
        assertEquals(items, FeedbackQueue.decode(FeedbackQueue.encode(items)))
        assertEquals(emptyList<FeedbackSubmission>(), FeedbackQueue.decode(null))
        assertThrows(Exception::class.java) { FeedbackQueue.decode("""[{"id":"x","accountId":"a","surface":"patient","score":4,"note":"","savedAt":1}]""") }
        assertThrows(Exception::class.java) { FeedbackQueue.decode("not json") }
    }

    @Test fun queueIsBoundedInsteadOfDroppingOldFeedback() {
        val full = (1..FeedbackQueue.limit).map { item("id-$it") }
        val error = assertThrows(IllegalArgumentException::class.java) { FeedbackQueue.add(full, item("extra")) }
        assertTrue(error.message!!.contains("·")) // English · Hindi
        assertEquals(2, FeedbackQueue.add(listOf(item("a")), item("b")).size)
    }

    @Test fun flushSendsOnlyThisAccountOldestFirstAndRemovesEachAfterSuccess() = runBlocking {
        val queue = listOf(item("1"), item("other", "account-b"), item("2"))
        val sent = mutableListOf<String>(); var stored = queue
        val error = FeedbackQueue.flush(queue, "account-a", { sent += it.id }) { stored = it; true }
        assertNull(error)
        assertEquals(listOf("1", "2"), sent)
        assertEquals(listOf("other"), stored.map { it.id }) // never sent as a different account
    }

    @Test fun failureKeepsTheRestQueuedAndNeverReportsSent() = runBlocking {
        val queue = listOf(item("1"), item("2"), item("3"))
        var stored = queue
        val error = FeedbackQueue.flush(queue, "account-a", { if (it.id == "2") throw IllegalArgumentException("Couldn't reach your care records.") }) { stored = it; true }
        assertEquals("Couldn't reach your care records.", error)
        assertEquals(listOf("2", "3"), stored.map { it.id })
    }

    @Test fun localPersistFailureIsReported() = runBlocking {
        val error = FeedbackQueue.flush(listOf(item("1")), "account-a", {}) { false }
        assertNotNull(error)
    }

    @Test fun statusSaysSentOnlyAfterTheServerConfirmed() {
        val local = FeedbackSendCopy.status(FeedbackSendState.LOCAL, saved = true, signedIn = false, error = null)
        assertTrue(local.startsWith("Saved on this phone only"))
        val queued = FeedbackSendCopy.status(FeedbackSendState.QUEUED, saved = true, signedIn = true, error = "Couldn't save.")
        assertTrue(queued.startsWith("Saved on this phone, not sent yet: Couldn't save."))
        assertFalse(queued.contains("Sent to"))
        assertTrue(FeedbackSendCopy.status(FeedbackSendState.SENT, saved = true, signedIn = true, error = null).startsWith("Sent to the Sahara team"))
        assertFalse(FeedbackSendCopy.status(FeedbackSendState.SENDING, saved = true, signedIn = true, error = null).startsWith("Sent"))
        listOf(FeedbackSendState.values().toList()).flatten().forEach { state -> listOf(true, false).forEach { saved ->
            assertTrue(FeedbackSendCopy.status(state, saved, true, null).contains(" · ")) // every line has its Hindi pair
        } }
    }
}
