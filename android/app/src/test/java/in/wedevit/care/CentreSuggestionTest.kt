package `in`.wedevit.care

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** "Suggest a centre that's missing": validation, the queued write, and how the send-later queue treats it. Synthetic data only. */
class CentreSuggestionTest {
    private class FakeDao : CareSyncDao {
        val rows = MutableStateFlow<List<CarePendingWrite>>(emptyList()); private var next = 1L
        override suspend fun pending() = rows.value.sortedBy { it.seq }
        override suspend fun countFor(recordKey: String) = rows.value.count { it.recordKey == recordKey }
        override fun count(state: String): Flow<Int> = rows.map { list -> list.count { it.state == state } }
        override suspend fun insert(write: CarePendingWrite): Long { require(rows.value.none { it.opId == write.opId }); val row = write.copy(seq = next++); rows.value += row; return row.seq }
        override suspend fun update(write: CarePendingWrite) { rows.value = rows.value.map { if (it.seq == write.seq) write else it } }
        override suspend fun delete(seq: Long) { rows.value = rows.value.filterNot { it.seq == seq } }
        override suspend fun purgePerson(personId: String) { rows.value = rows.value.filterNot { it.personId == personId } }
    }
    private val good = CentreSuggestionDraft("  Asha Palliative Clinic ", " Pune ", "Maharashtra", "Near the bus stand", "+91 98765 43210", "Home visits")

    @Test fun aCompleteSuggestionPassesAndOptionalFieldsMayBeBlank() {
        assertNull(CentreSuggestionRules.error(good))
        assertNull(CentreSuggestionRules.error(CentreSuggestionDraft("Asha Clinic", "Pune", "Maharashtra")))
    }
    @Test fun nameTownAndStateAreRequired() {
        assertEquals("Add the centre's name.", CentreSuggestionRules.error(good.copy(name = "  "))?.first)
        assertEquals("Add the centre's name.", CentreSuggestionRules.error(good.copy(name = "12345"))?.first)
        assertEquals("Add the town or city.", CentreSuggestionRules.error(good.copy(city = ""))?.first)
        assertEquals("Add the state.", CentreSuggestionRules.error(good.copy(state = " "))?.first)
    }
    @Test fun lengthAndPhoneLimitsMatchTheServerTable() {
        assertNotNull(CentreSuggestionRules.error(good.copy(name = "A".repeat(CentreSuggestionRules.NAME_MAX + 1))))
        assertNotNull(CentreSuggestionRules.error(good.copy(address = "a".repeat(CentreSuggestionRules.ADDRESS_MAX + 1))))
        assertNotNull(CentreSuggestionRules.error(good.copy(note = "a".repeat(CentreSuggestionRules.NOTE_MAX + 1))))
        assertEquals("Check the phone number (6 to 15 digits).", CentreSuggestionRules.error(good.copy(phone = "call me"))?.first)
        assertNotNull(CentreSuggestionRules.error(good.copy(phone = "12345")))
        assertTrue(CentreSuggestionRules.validPhone("0477 2287497"))
        assertTrue(CentreSuggestionRules.validPhone("(020) 2612-3456"))
    }
    @Test fun everyMessageHasHindi() {
        val devanagari = Regex("[\\u0900-\\u097F]")
        val drafts = listOf(good.copy(name = ""), good.copy(name = "A".repeat(200)), good.copy(city = ""), good.copy(city = "A".repeat(100)), good.copy(state = ""),
            good.copy(state = "A".repeat(100)), good.copy(address = "a".repeat(400)), good.copy(phone = "x"), good.copy(note = "a".repeat(600)))
        drafts.forEach { d -> val (en, hi) = CentreSuggestionRules.error(d)!!; assertTrue(en, devanagari.containsMatchIn(hi)) }
        listOf("waiting", "kept", "pending", "approved", "rejected").forEach { assertTrue(it, devanagari.containsMatchIn(CentreSuggestionRules.statusText(it).second)) }
    }
    @Test fun aSentSuggestionSaysSentForReviewNotCheckedYet() {
        assertEquals("Sent for review", CentreSuggestionRules.statusText("pending").first)
        assertEquals("Waiting to send", CentreSuggestionRules.statusText("waiting").first)
    }

    @Test fun theQueuedWriteIsAnAppendWithTrimmedFieldsAndNoMorphineClaim() {
        val op = CentreSuggestionOps.write(good, id = "s1", now = 1_700_000_000_000)
        assertEquals(CentreSuggestionOps.KIND, op.kind); assertEquals(CentreSuggestionOps.PERSON, op.personId); assertEquals("s1", op.recordKey); assertTrue(op.isAppend)
        val row = JSONObject(op.payload)
        assertEquals(setOf("id", "name", "city", "state", "address", "phone", "note", "saved_at"), row.keys().asSequence().toSet())
        assertEquals("Asha Palliative Clinic", row.getString("name")); assertEquals("Pune", row.getString("city"))
        assertEquals("2023-11-14T22:13:20Z", row.getString("saved_at"))
        assertThrows(IllegalArgumentException::class.java) { CentreSuggestionOps.write(good.copy(name = "")) }
    }

    @Test fun withNoSignalTheSuggestionWaitsInTheQueueThenSendsOnce() = runBlocking {
        val dao = FakeDao(); val sent = mutableListOf<String>(); var offline = true
        val op = CentreSuggestionOps.write(good, id = "s1"); dao.insert(op)
        val send: suspend (CarePendingWrite) -> Long? = { w -> if (offline) throw CareSyncOffline("offline"); sent += w.recordKey; null }
        assertEquals(CareSyncStatus.WAITING, CareSyncEngine.flush(dao, send = send).outcomes[op.opId])
        assertEquals(listOf("waiting"), dao.pending().map { CentreSuggestionOps.fromPending(it).status })
        offline = false
        assertEquals(CareSyncStatus.SENT, CareSyncEngine.flush(dao, send = send).outcomes[op.opId])
        assertEquals(listOf("s1"), sent); assertTrue(dao.pending().isEmpty())
    }
    @Test fun aRefusedSuggestionIsKeptAndRetriedWithoutHoldingBackCareWrites() = runBlocking {
        val dao = FakeDao(); var tableReady = false; val sent = mutableListOf<String>()
        val suggestion = CentreSuggestionOps.write(good, id = "s1"); dao.insert(suggestion)
        val entry = CareSyncOps.saveEntry(CareItem(id = "e1", personId = "p1", kind = "checkin", title = "Okay", created = 1)); dao.insert(entry)
        val send: suspend (CarePendingWrite) -> Long? = { w ->
            if (w.kind == CentreSuggestionOps.KIND && !tableReady) throw CareHttpException(404, "Couldn't save. Your entry is still here.", "PGRST205")
            sent += w.recordKey; 1L
        }
        val first = CareSyncEngine.flush(dao, send = send)
        assertEquals(CareSyncStatus.KEPT, first.outcomes[suggestion.opId]); assertEquals(CareSyncStatus.SENT, first.outcomes[entry.opId])
        assertEquals(listOf("kept"), dao.pending().map { CentreSuggestionOps.fromPending(it).status })
        tableReady = true
        assertEquals(CareSyncStatus.SENT, CareSyncEngine.flush(dao, send = send).outcomes[suggestion.opId])
        assertEquals(listOf("e1", "s1"), sent); assertTrue(dao.pending().isEmpty())
    }

    @Test fun theListShowsUnsentOnesWithoutDuplicatingServerRows() {
        val server = CentreSuggestionOps.fromServer(JSONArray().put(JSONObject().put("id", "s0").put("name", "Old").put("city", "Pune").put("state", "Maharashtra").put("status", "approved").put("created_at", "2026-10-01T10:00:00+00:00"))
            .put(JSONObject().put("id", "s1").put("name", "Asha").put("city", "Pune").put("state", "Maharashtra").put("status", "pending").put("created_at", "2026-10-02T10:00:00+00:00")))
        assertEquals("approved", server[0].status)
        val waiting = CentreSuggestionOps.write(good, id = "s1", now = 1_900_000_000_000).copy(seq = 1)
        val merged = CentreSuggestionOps.merge(server, listOf(waiting, CareSyncOps.saveEntry(CareItem(id = "e1", personId = "p1", kind = "checkin", title = "Okay", created = 1))))
        assertEquals(listOf("s1" to "waiting", "s0" to "approved"), merged.map { it.id to it.status })
    }
}
