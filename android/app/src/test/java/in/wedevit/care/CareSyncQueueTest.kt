package `in`.wedevit.care

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Synthetic records only. The fake server copies the save rules of healthathon_save_record. */
class CareSyncQueueTest {
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
    /** Same id + same data returns the stored row; a new row with a used id or a stale revision is a conflict. */
    private class FakeServer {
        val records = mutableMapOf<String, Pair<Long, String>>(); val calls = mutableListOf<String>()
        var offline = false; var lostReply = false
        fun save(item: CareItem): Long {
            calls += "${item.id}@${item.revision}"
            if (offline) throw CareSyncOffline("Couldn't reach your care records.")
            val data = JSONObject(CareSyncOps.itemJson(item.copy(revision = 0)).toString()).toString()
            val existing = records[item.id]
            val revision = when {
                existing != null && existing.second == data -> existing.first
                item.revision == 0L && existing != null -> throw CareHttpException(409, "This record changed. Refresh and try again.", "23505")
                item.revision == 0L -> 1L
                existing?.first != item.revision -> throw CareHttpException(409, "This record changed. Refresh and try again.", "40001")
                else -> item.revision + 1
            }
            records[item.id] = revision to data
            if (lostReply) { lostReply = false; throw CareSyncOffline("Reply lost") }
            return revision
        }
        suspend fun send(op: CarePendingWrite): Long? = when (op.kind) {
            CareSyncOps.ENTRY_SAVE -> save(CareSyncOps.item(op))
            CareSyncOps.MEDICINE_EVENT -> { calls += "event:${JSONObject(op.payload).getString("id")}"; if (offline) throw CareSyncOffline("offline"); null }
            else -> null
        }
    }
    private val person = "00000000-0000-4000-8000-000000000001"
    private fun entry(id: String, title: String, revision: Long = 0) = CareItem(id = id, personId = person, kind = "checkin", title = title, created = 1, revision = revision)

    @Test fun waitingWritesSendInOrderAndARetryNeverDuplicates() = runBlocking {
        val dao = FakeDao(); val server = FakeServer()
        dao.insert(CareSyncOps.saveEntry(entry("a", "Pain 3"))); dao.insert(CareSyncOps.saveEntry(entry("b", "Pain 4")))
        server.offline = true
        val offline = CareSyncEngine.flush(dao, send = server::send)
        assertTrue(offline.stopped); assertEquals(2, dao.pending().size); assertEquals(listOf("a@0"), server.calls)
        assertEquals(1, dao.pending().first().attempts)
        // The first send reaches the server but the reply is lost: the retry must return the same row, not add one.
        server.offline = false; server.lostReply = true
        CareSyncEngine.flush(dao, send = server::send)
        assertEquals(2, dao.pending().size)
        val report = CareSyncEngine.flush(dao, send = server::send)
        assertEquals(2, report.count(CareSyncStatus.SENT)); assertTrue(dao.pending().isEmpty())
        assertEquals(listOf("a@0", "a@0", "a@0", "b@0"), server.calls)
        assertEquals(setOf("a", "b"), server.records.keys); assertEquals(1L, server.records["a"]!!.first)
    }

    @Test fun laterEditsOfTheSameRecordAreBasedOnTheNewServerRevision() = runBlocking {
        val dao = FakeDao(); val server = FakeServer()
        dao.insert(CareSyncOps.saveEntry(entry("a", "Pain 3")))
        dao.insert(CareSyncOps.saveEntry(entry("a", "Pain 5")))   // edited again while still offline: same local revision 0
        CareSyncEngine.flush(dao, send = server::send)
        assertEquals(listOf("a@0", "a@1"), server.calls); assertEquals(2L, server.records["a"]!!.first)
        assertTrue(dao.pending().isEmpty())
    }

    @Test fun serverVersionWinsForAnEditButFamilyAppendsAreKept() = runBlocking {
        val dao = FakeDao(); val server = FakeServer()
        server.records["visit"] = 3L to "doctor-edited"   // the doctor changed the visit after the phone last saw revision 1
        dao.insert(CareSyncOps.saveEntry(entry("visit", "Moved by family", revision = 1)))
        dao.insert(CareSyncOps.saveEntry(entry("visit", "Moved again", revision = 1)))
        dao.insert(CareSyncOps.medicineEvent(person, "visit", JSONObject().put("id", "e1")))
        dao.insert(CareSyncOps.saveEntry(entry("note", "Family check-in")))
        val report = CareSyncEngine.flush(dao, send = server::send)
        assertEquals(2, report.count(CareSyncStatus.SERVER_KEPT)); assertEquals(2, report.count(CareSyncStatus.SENT))
        assertEquals(3L to "doctor-edited", server.records["visit"])
        assertTrue(server.calls.contains("event:e1")); assertTrue(server.records.containsKey("note")); assertTrue(dao.pending().isEmpty())
    }

    @Test fun aRejectedAppendIsKeptOnThePhoneAndHoldsBackOnlyItsOwnRecord() = runBlocking {
        val dao = FakeDao(); val calls = mutableListOf<String>()
        dao.insert(CareSyncOps.saveEntry(entry("a", "Kept")))
        dao.insert(CareSyncOps.saveEntry(entry("a", "Kept, edited")))
        dao.insert(CareSyncOps.saveEntry(entry("b", "Other")))
        val report = CareSyncEngine.flush(dao) { op -> calls += op.recordKey; if (op.recordKey == "a") throw CareHttpException(403, "You don't have permission to change these records.", "42501"); 1L }
        assertEquals(listOf("a", "b"), calls)
        assertEquals(CareSyncStatus.KEPT, report.outcomes[dao.pending().first().opId])
        assertEquals(listOf(CareSyncQueue.FAILED, CareSyncQueue.WAITING), dao.pending().map { it.state })
        assertEquals("You don't have permission to change these records.", dao.pending().first().lastError)
    }

    @Test fun theWriteOnScreenIsReturnedToThePersonWhenTheServerRefusesIt() = runBlocking {
        val dao = FakeDao()
        val id = CareSyncOps.saveEntry(entry("a", "Edit")).copy(baseRevision = 2, isAppend = false).also { dao.insert(it) }.opId
        val report = CareSyncEngine.flush(dao, immediate = id) { throw CareHttpException(409, "This record changed. Refresh and try again.", "40001") }
        assertEquals(CareSyncStatus.REJECTED, report.outcomes[id]); assertEquals("This record changed. Refresh and try again.", report.errors[id])
        assertTrue(dao.pending().isEmpty())
    }

    @Test fun onlyNetworkAndBusyServerFailuresWaitForRetry() {
        assertEquals(CareSyncEngine.Failure.OFFLINE, CareSyncEngine.classify(CareSyncOffline("no signal")))
        assertEquals(CareSyncEngine.Failure.OFFLINE, CareSyncEngine.classify(CareHttpException(503, "busy")))
        assertEquals(CareSyncEngine.Failure.OFFLINE, CareSyncEngine.classify(CareHttpException(401, "Sign in again to continue.")))
        assertEquals(CareSyncEngine.Failure.CONFLICT, CareSyncEngine.classify(CareHttpException(409, "changed", "40001")))
        assertEquals(CareSyncEngine.Failure.CONFLICT, CareSyncEngine.classify(CareSyncConflict("changed")))
        assertEquals(CareSyncEngine.Failure.REJECTED, CareSyncEngine.classify(CareHttpException(403, "no", "42501")))
        assertEquals(CareSyncEngine.Failure.REJECTED, CareSyncEngine.classify(IllegalArgumentException("Invalid care entry")))
    }

    @Test fun payloadsKeepTheirIdsAndUnsentChangesStayVisible() {
        val item = entry("a", "Pain 3").copy(detail = "Synthetic", due = 5, doneOn = "2026-10-27")
        val op = CareSyncOps.saveEntry(item)
        assertEquals(item, CareSyncOps.item(op)); assertTrue(op.isAppend); assertFalse(CareSyncOps.saveEntry(item.copy(revision = 4)).isAppend)
        val ack = Acknowledgement("v1", "a".repeat(64), "Synthetic reader", "Family / carer", 9, "ack-1")
        assertEquals(ack, CareSyncOps.ack(CareSyncOps.confirm(person, ack)))
        val kept = CareContact("c1", person, "Synthetic nurse", "Nurse", "clinician", revision = 2)
        val added = CareContact("c2", person, "Synthetic carer", "Neighbour", "caregiver")
        val shown = CareSyncOps.overlayContacts(listOf(kept), listOf(CareSyncOps.saveContact(added), CareSyncOps.removeContact(kept)))
        assertEquals(listOf(added), shown)
        val server = JSONArray().put(JSONObject().put("id", "e1"))
        val pending = listOf(CareSyncOps.medicineEvent(person, "m", JSONObject().put("id", "e1")), CareSyncOps.medicineEvent(person, "m", JSONObject().put("id", "e2")).copy(createdAt = 0))
        val merged = CareSyncOps.overlayEvents(server, pending)
        assertEquals(2, merged.length()); assertEquals("1970-01-01T00:00:00Z", merged.getJSONObject(1).getString("logged_at"))
    }

    @Test fun migrationCreatesExactlyTheTableRoomExpects() {
        val schema = JSONObject(File("schemas/in.wedevit.care.CareDatabase/8.json").readText()).getJSONObject("database")
        assertEquals(8, schema.getInt("version"))
        val entities = schema.getJSONArray("entities")
        val table = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == "CarePendingWrite" }
        val expected = listOf(table.getString("createSql")) + table.getJSONArray("indices").let { i -> (0 until i.length()).map { i.getJSONObject(it).getString("createSql") } }
        assertEquals(expected.map { it.replace("\${TABLE_NAME}", "CarePendingWrite") }, CareSyncSchema.statements)
    }
}
