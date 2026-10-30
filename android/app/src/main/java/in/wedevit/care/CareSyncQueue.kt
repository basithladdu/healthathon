package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.room.*
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Send-later queue for signed-in writes. Each write is stored on the phone first (in the
 * account's own database), then sent in the order it was made. The server stays the source
 * of truth: a refresh replaces the local copy with the server's, and only writes still
 * waiting here are laid back on top.
 */
@Entity(indices = [Index(value = ["opId"], unique = true), Index("recordKey")])
data class CarePendingWrite(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    /** Stable for the life of the write: retries reuse the record ids inside the payload, so the server sees the same write. */
    val opId: String = newId(),
    val kind: String,
    val personId: String,
    /** Writes for the same record are sent one after another and never overtake each other. */
    val recordKey: String,
    val payload: String,
    val baseRevision: Long = 0,
    /** A new record (entry, check-in, intake report, confirmation, contact). Appends are never discarded. */
    val isAppend: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val state: String = CareSyncQueue.WAITING,
    val lastError: String = "",
)
internal val CarePendingWrite.family: String get() = kind.substringBefore('.')

@Dao interface CareSyncDao {
    @Query("SELECT * FROM CarePendingWrite ORDER BY seq") suspend fun pending(): List<CarePendingWrite>
    @Query("SELECT COUNT(*) FROM CarePendingWrite WHERE recordKey = :recordKey") suspend fun countFor(recordKey: String): Int
    @Query("SELECT COUNT(*) FROM CarePendingWrite WHERE state = :state") fun count(state: String): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(write: CarePendingWrite): Long
    @Update suspend fun update(write: CarePendingWrite)
    @Query("DELETE FROM CarePendingWrite WHERE seq = :seq") suspend fun delete(seq: Long)
    @Query("DELETE FROM CarePendingWrite WHERE personId = :personId") suspend fun purgePerson(personId: String)
}

val CareSyncMigration7To8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { CareSyncSchema.statements.forEach(db::execSQL) }
}

internal object CareSyncSchema {
    val statements = listOf(
        "CREATE TABLE IF NOT EXISTS `CarePendingWrite` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `opId` TEXT NOT NULL, `kind` TEXT NOT NULL, `personId` TEXT NOT NULL, `recordKey` TEXT NOT NULL, `payload` TEXT NOT NULL, `baseRevision` INTEGER NOT NULL, `isAppend` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `state` TEXT NOT NULL, `lastError` TEXT NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_CarePendingWrite_opId` ON `CarePendingWrite` (`opId`)",
        "CREATE INDEX IF NOT EXISTS `index_CarePendingWrite_recordKey` ON `CarePendingWrite` (`recordKey`)",
    )
}

/** The phone could not reach the server (no signal, timeout). The write stays queued. */
internal class CareSyncOffline(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
/** The server holds a different version of this record than the one the write was based on. */
internal class CareSyncConflict(message: String) : IllegalArgumentException(message)

enum class CareSyncStatus { SENT, WAITING, SERVER_KEPT, KEPT, REJECTED }
data class CareSyncReport(val outcomes: Map<String, CareSyncStatus>, val errors: Map<String, String>, val stopped: Boolean) {
    fun count(status: CareSyncStatus) = outcomes.count { it.value == status }
}

/** Ordering, retry and conflict rules, kept free of Android so they are unit-tested. */
internal object CareSyncEngine {
    enum class Failure { OFFLINE, CONFLICT, REJECTED }
    fun classify(e: Throwable): Failure = when {
        e is CareSyncOffline -> Failure.OFFLINE
        e is CareHttpException && (e.status == 401 || e.status == 408 || e.status == 429 || e.status >= 500) -> Failure.OFFLINE
        e is CareSyncConflict -> Failure.CONFLICT
        e is CareHttpException && e.code in setOf("40001", "23505") -> Failure.CONFLICT
        else -> Failure.REJECTED
    }

    /**
     * Sends every queued write in order. [send] returns the record's new server revision when it has one.
     * - No signal: stop and keep everything from this write on.
     * - Sent: remove it, and base later writes for the same record on the new revision.
     * - Conflict on an edit: the server version wins (it is usually the doctor's edit); this edit and later edits to the record are dropped.
     * - Conflict or rejection on an append (family additions): kept on the phone, marked failed, never discarded.
     * - [immediate]: the write the person just made, still on screen; a rejection is returned to them instead of being kept.
     */
    suspend fun flush(dao: CareSyncDao, immediate: String? = null, send: suspend (CarePendingWrite) -> Long?): CareSyncReport {
        val outcomes = linkedMapOf<String, CareSyncStatus>(); val errors = mutableMapOf<String, String>()
        val queue = dao.pending().toMutableList(); val blocked = mutableSetOf<String>(); val dropped = mutableSetOf<Long>()
        for (index in queue.indices) {
            val op = queue[index]
            if (op.seq in dropped) continue
            if (op.recordKey in blocked) { outcomes[op.opId] = CareSyncStatus.WAITING; continue }
            val result = try { Result.success(send(op)) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            val error = result.exceptionOrNull()
            if (error == null) {
                dao.delete(op.seq); outcomes[op.opId] = CareSyncStatus.SENT
                result.getOrNull()?.let { revision ->
                    for (later in index + 1 until queue.size) {
                        val next = queue[later]
                        if (next.recordKey == op.recordKey && next.family == op.family && next.seq !in dropped) { queue[later] = next.copy(baseRevision = revision); dao.update(queue[later]) }
                    }
                }
                continue
            }
            val reason = error.message.orEmpty().take(300)
            val failure = classify(error)
            when {
                failure == Failure.OFFLINE -> {
                    dao.update(op.copy(attempts = op.attempts + 1, lastError = reason))
                    queue.drop(index).filter { it.seq !in dropped }.forEach { outcomes[it.opId] = CareSyncStatus.WAITING }
                    return CareSyncReport(outcomes, errors, stopped = true)
                }
                op.opId == immediate -> { dao.delete(op.seq); outcomes[op.opId] = CareSyncStatus.REJECTED; errors[op.opId] = reason }
                failure == Failure.CONFLICT && !op.isAppend -> {
                    dao.delete(op.seq); outcomes[op.opId] = CareSyncStatus.SERVER_KEPT
                    queue.drop(index + 1).filter { it.recordKey == op.recordKey && it.family == op.family && !it.isAppend && it.seq !in dropped }.forEach {
                        dao.delete(it.seq); dropped += it.seq; outcomes[it.opId] = CareSyncStatus.SERVER_KEPT
                    }
                }
                else -> {
                    dao.update(op.copy(state = CareSyncQueue.FAILED, attempts = op.attempts + 1, lastError = reason))
                    blocked += op.recordKey; outcomes[op.opId] = CareSyncStatus.KEPT; errors[op.opId] = reason
                }
            }
        }
        return CareSyncReport(outcomes, errors, stopped = false)
    }
}

/** Payloads for each kind of write. Ids inside the payload never change, which keeps retries idempotent on the server. */
internal object CareSyncOps {
    const val ENTRY_SAVE = "entry.save"; const val ENTRY_REMOVE = "entry.remove"; const val ACK = "ack.save"
    const val MEDICINE_EVENT = "medicine.event"; const val CONTACT_SAVE = "contact.save"; const val CONTACT_REMOVE = "contact.remove"

    fun itemJson(i: CareItem) = JSONObject().put("id", i.id).put("personId", i.personId).put("kind", i.kind).put("title", i.title).put("detail", i.detail)
        .put("due", i.due).put("doneOn", i.doneOn).put("created", i.created).put("revision", i.revision)
    fun item(op: CarePendingWrite): CareItem = JSONObject(op.payload).let {
        CareItem(it.getString("id"), it.getString("personId"), it.getString("kind"), it.getString("title"), it.optString("detail"), it.optLong("due"), it.optString("doneOn"), it.getLong("created"), op.baseRevision)
    }
    fun ackJson(a: Acknowledgement) = JSONObject().put("versionId", a.versionId).put("contentHash", a.contentHash).put("name", a.name).put("relationship", a.relationship).put("at", a.at).put("id", a.id)
    fun ack(op: CarePendingWrite): Acknowledgement = JSONObject(op.payload).let { Acknowledgement(it.getString("versionId"), it.getString("contentHash"), it.getString("name"), it.getString("relationship"), it.getLong("at"), it.getString("id")) }
    fun contact(op: CarePendingWrite): CareContact = decodeCareContact(JSONObject(op.payload)).copy(revision = op.baseRevision)

    fun saveEntry(item: CareItem) = CarePendingWrite(kind = ENTRY_SAVE, personId = item.personId, recordKey = item.id, payload = itemJson(item).toString(), baseRevision = item.revision, isAppend = item.revision == 0L)
    fun removeEntry(item: CareItem) = CarePendingWrite(kind = ENTRY_REMOVE, personId = item.personId, recordKey = item.id, payload = itemJson(item).toString(), baseRevision = item.revision, isAppend = false)
    fun confirm(personId: String, ack: Acknowledgement) = CarePendingWrite(kind = ACK, personId = personId, recordKey = ack.versionId, payload = ackJson(ack).toString(), isAppend = true)
    /** [row] is the intake report exactly as the server takes it; its id makes a repeated send return the first one. */
    fun medicineEvent(personId: String, medicineId: String, row: JSONObject) = CarePendingWrite(kind = MEDICINE_EVENT, personId = personId, recordKey = medicineId, payload = row.toString(), isAppend = true)
    fun saveContact(contact: CareContact) = CarePendingWrite(kind = CONTACT_SAVE, personId = contact.personId, recordKey = contact.id, payload = contactJson(contact).toString(), baseRevision = contact.revision, isAppend = contact.revision == 0L)
    fun removeContact(contact: CareContact) = CarePendingWrite(kind = CONTACT_REMOVE, personId = contact.personId, recordKey = contact.id, payload = contactJson(contact).toString(), baseRevision = contact.revision, isAppend = false)

    /** Contacts as the person should see them: the server list with their unsent changes on top. */
    fun overlayContacts(server: List<CareContact>, pending: List<CarePendingWrite>): List<CareContact> = pending.fold(server) { list, op ->
        val contact = contact(op)
        when (op.kind) { CONTACT_SAVE -> list.filterNot { it.id == contact.id } + contact; CONTACT_REMOVE -> list.filterNot { it.id == contact.id }; else -> list }
    }
    /** Intake reports not yet on the server, in the cached-row shape, so the medicine history shows them. */
    fun overlayEvents(server: JSONArray, pending: List<CarePendingWrite>): JSONArray {
        val ids = (0 until server.length()).map { server.getJSONObject(it).getString("id") }.toSet()
        val merged = JSONArray(server.toString())
        pending.filter { it.kind == MEDICINE_EVENT }.map { op -> JSONObject(op.payload).also { if (!it.has("logged_at")) it.put("logged_at", medicineInstant(op.createdAt)) } }
            .filter { it.getString("id") !in ids }.forEach { merged.put(it) }
        return merged
    }
}

object CareSyncText {
    fun waiting(n: Int) = if (n == 1) careBilingual("1 change waiting to send", "1 बदलाव भेजना बाकी है") else careBilingual("$n changes waiting to send", "$n बदलाव भेजने बाकी हैं")
    fun kept(n: Int) = if (n == 1) careBilingual("1 change couldn't be sent. It is kept on this phone.", "1 बदलाव नहीं भेजा जा सका। यह फ़ोन पर रखा है।")
        else careBilingual("$n changes couldn't be sent. They are kept on this phone.", "$n बदलाव नहीं भेजे जा सके। ये फ़ोन पर रखे हैं।")
    fun serverKept(n: Int) = if (n == 1) careBilingual("1 change wasn't sent: the record was changed by someone else, so their version was kept.", "1 बदलाव नहीं भेजा गया: रिकॉर्ड किसी और ने बदला था, इसलिए उनका संस्करण रखा गया।")
        else careBilingual("$n changes weren't sent: the records were changed by someone else, so their versions were kept.", "$n बदलाव नहीं भेजे गए: रिकॉर्ड किसी और ने बदले थे, इसलिए उनके संस्करण रखे गए।")
    val REMOVED_WAITING: String get() = careBilingual("Removed on this phone. The server will be updated when you're online.", "फ़ोन से हटाया गया। इंटरनेट मिलने पर सर्वर पर भी हटेगा।")
    val CONFIRMED_WAITING: String get() = careBilingual("Your confirmation is saved on this phone. It will be sent when you're online.", "आपकी पुष्टि फ़ोन पर सहेजी गई। इंटरनेट मिलने पर भेजी जाएगी।")
    val SAVED_WAITING: String get() = careBilingual("Saved on this phone. It will be sent when you're online.", "फ़ोन पर सहेजा गया। इंटरनेट मिलने पर भेजा जाएगा।")
    val SEND_NOW: String get() = careBilingual("Send now", "अभी भेजें")
}

/** One queue per signed-in account. Only that account's session ever sends its writes. */
class CareSyncQueue private constructor(private val db: CareDatabase, @Volatile private var cloud: CareCloud, val account: String) {
    companion object {
        const val WAITING = "waiting"; const val FAILED = "failed"
        private val queues = mutableMapOf<String, CareSyncQueue>()
        fun get(context: Context, cloud: CareCloud, account: String): CareSyncQueue = synchronized(queues) {
            require(account.isNotBlank())
            queues.getOrPut(account) { CareSyncQueue(CareDatabase.get(context, account), cloud, account) }.also { it.cloud = cloud }
        }
    }
    private val dao = db.sync()
    private val sending = Mutex()
    private val sentConfirmations = ConcurrentHashMap<String, Acknowledgement>()
    val waiting: Flow<Int> = dao.count(WAITING)
    val kept: Flow<Int> = dao.count(FAILED)

    suspend fun pending(personId: String) = dao.pending().filter { it.personId == personId }
    suspend fun hasPending(recordKey: String) = dao.countFor(recordKey) > 0
    suspend fun purgePerson(personId: String) = dao.purgePerson(personId)
    /** The server's copy of a confirmation sent by [submit] (it may be an earlier identical one). */
    fun takeConfirmation(opId: String): Acknowledgement? = sentConfirmations.remove(opId)

    /**
     * Stores [write] (with [local], the matching change to the phone's copy, in one transaction), then tries to send the queue.
     * Returns SENT or WAITING. If the server turns this write down, it is removed, [rollback] restores the phone's copy and
     * the server's reason is thrown, so the person still sees their entry on screen.
     */
    suspend fun submit(write: CarePendingWrite, local: suspend () -> Unit = {}, rollback: suspend () -> Unit = {}): CareSyncStatus {
        db.withTransaction { dao.insert(write); local() }
        val report = flush(immediate = write.opId)
        return when (val status = report.outcomes[write.opId] ?: CareSyncStatus.WAITING) {
            CareSyncStatus.REJECTED -> { rollback(); throw IllegalArgumentException(report.errors[write.opId]?.takeIf(String::isNotBlank) ?: "Couldn't save. Your entry is still here.") }
            CareSyncStatus.SENT -> status
            else -> CareSyncStatus.WAITING
        }
    }

    /**
     * Stores [write] and tries to send the queue, without the [submit] rule that drops a turned-down write: it stays queued
     * (SENT, WAITING, or KEPT and retried on the next send). Used for centre suggestions, which belong to no care record.
     */
    suspend fun enqueue(write: CarePendingWrite): CareSyncStatus {
        db.withTransaction { dao.insert(write) }
        return flush().outcomes[write.opId] ?: CareSyncStatus.WAITING
    }

    suspend fun flush(immediate: String? = null): CareSyncReport = sending.withLock {
        if (cloud.session.value?.userId != account) return@withLock CareSyncReport(emptyMap(), emptyMap(), stopped = true)
        CareSyncEngine.flush(dao, immediate, ::send)
    }

    /** Re-applies writes that are still waiting after a refresh replaced the phone's copy with the server's. Call inside the refresh transaction. */
    suspend fun overlay(people: Set<String>) {
        val care = db.care()
        dao.pending().filter { it.personId in people }.forEach { op ->
            when (op.kind) {
                CareSyncOps.ENTRY_SAVE -> care.saveItem(CareSyncOps.item(op))
                CareSyncOps.ENTRY_REMOVE -> care.removeItem(CareSyncOps.item(op))
                CareSyncOps.ACK -> CareSyncOps.ack(op).let { if (care.confirmation(it.id) == null) care.acknowledge(it) }
            }
        }
    }

    private suspend fun send(op: CarePendingWrite): Long? {
        val service = cloud
        check(service.session.value?.userId == account) { "The signed-in account changed." }
        return when (op.kind) {
            CareSyncOps.ENTRY_SAVE -> {
                val saved = service.saveEntry(CareSyncOps.item(op), account)
                db.care().setRevision(saved.id, saved.revision); saved.revision
            }
            CareSyncOps.ENTRY_REMOVE -> { service.removeEntryOnce(CareSyncOps.item(op), account); null }
            CareSyncOps.ACK -> {
                val ack = CareSyncOps.ack(op)
                val saved = service.confirm(op.personId, ack)
                require(saved.versionId == ack.versionId && saved.contentHash == ack.contentHash && saved.relationship == ack.relationship) { "The saved confirmation didn't match this version. Refresh the care note before trying again." }
                val care = db.care()
                // The server sets the time and may return an earlier identical confirmation: keep exactly its copy.
                db.withTransaction { care.deleteAcknowledgement(ack.id); care.deleteAcknowledgement(saved.id); care.acknowledge(saved) }
                sentConfirmations[op.opId] = saved; null
            }
            CareSyncOps.MEDICINE_EVENT -> { service.request("POST", "/rest/v1/rpc/healthathon_report_medicine", JSONObject().put("p_event", JSONObject(op.payload).apply { remove("logged_at") }), expectedAccountId = account); null }
            CareSyncOps.CONTACT_SAVE -> service.saveCareContactOnce(CareSyncOps.contact(op)).revision
            CareSyncOps.CONTACT_REMOVE -> { service.removeCareContactOnce(CareSyncOps.contact(op)); null }
            CentreSuggestionOps.KIND -> { service.sendCentreSuggestion(op, account); null }
            else -> throw IllegalArgumentException("This change needs an updated app.")
        }
    }
}

/** Removal that is safe to repeat: a record that is already gone counts as removed; one changed by someone else is a conflict. */
internal suspend fun CareCloud.removeEntryOnce(item: CareItem, account: String) {
    val deleted = JSONArray(request("DELETE", "/rest/v1/healthathon_records?kind=eq.entry&id=eq.${item.id}&revision=eq.${item.revision}", expectedAccountId = account))
    if (deleted.length() == 1) return
    val still = JSONArray(request("GET", "/rest/v1/healthathon_records?kind=eq.entry&id=eq.${item.id}&select=id", expectedAccountId = account))
    if (still.length() > 0) throw CareSyncConflict("This entry changed. Refresh before removing it.")
}
/** A repeated first save of a contact finds the copy the server already holds instead of failing. */
internal suspend fun CareCloud.saveCareContactOnce(contact: CareContact): CareContact = try { saveCareContact(contact) } catch (e: CareHttpException) {
    if (e.code !in setOf("40001", "23505")) throw e
    val same = careContacts(contact.personId).contacts.firstOrNull { it.id == contact.id }
    if (same != null && same.name == contact.name.trim() && same.relationship == contact.relationship.trim() && same.role == contact.role && same.phone == contact.phone) same else throw CareSyncConflict("This contact changed. Refresh before saving.")
}
internal suspend fun CareCloud.removeCareContactOnce(contact: CareContact) {
    try { removeCareContact(contact) } catch (e: CareHttpException) {
        if (e.code != "40001") throw e
        if (careContacts(contact.personId).contacts.any { it.id == contact.id }) throw CareSyncConflict("This contact changed. Refresh before removing it.")
    }
}

/** Small, calm line above the tab bar while signed-in changes are still on the phone. */
@Composable fun CareSyncStatusLine(waiting: Int, kept: Int, busy: Boolean, onSend: () -> Unit, modifier: Modifier = Modifier) {
    if (waiting + kept == 0) return
    Surface(modifier.fillMaxWidth().testTag("care-sync-status"), color = SaharaSoftBlue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (waiting > 0) Text(CareSyncText.waiting(waiting), style = MaterialTheme.typography.bodySmall)
                if (kept > 0) Text(CareSyncText.kept(kept), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onSend, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(CareSyncText.SEND_NOW) }
        }
    }
}
