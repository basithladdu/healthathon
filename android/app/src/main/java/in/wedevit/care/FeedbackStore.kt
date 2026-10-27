package `in`.wedevit.care

import android.content.Context
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** App-experience feedback only, never patient data. The latest save is kept on the phone; a save made while signed in is
 *  queued here and sent to Sahara's server (sahara_submit_app_feedback) as that account, and leaves the queue only after the server accepts it. */
class FeedbackStore(context: Context, accountScope: String, private val surface: String, private val now: () -> Long = { System.currentTimeMillis() }) {
    init { require(accountScope.isNotBlank() && accountScope.length <= 200 && surface.isNotBlank() && surface.length <= 100) }
    private val scope = MessageDigest.getInstance("SHA-256").digest("${accountScope.length}:$accountScope:${surface.length}:$surface".toByteArray()).joinToString("") { "%02x".format(it) }
    private val preferences = context.applicationContext.getSharedPreferences("sahara-feedback-v1-$scope", Context.MODE_PRIVATE)
    private var failure: String? = null
    fun error(): String? = failure
    @Synchronized fun latest(): FeedbackRecord? {
        val text = preferences.getString("latest", null) ?: return null
        return try {
            val row = JSONObject(text)
            require(row.getInt("schema") == 1)
            val record = FeedbackRecord(row.getDouble("score").toFloat(), row.getString("note"), row.getLong("savedAt"))
            require(FeedbackRules.error(record.score, record.note) == null && record.savedAt > 0)
            failure = null; record
        } catch (_: Exception) { failure = "Saved feedback could not be read. It has not been replaced."; null }
    }
    /** Id of the latest save, when it has one (saves before 27 Oct had none and were never sent). */
    @Synchronized fun latestId(): String? = preferences.getString("latest", null)?.let { runCatching { JSONObject(it).optString("id").ifBlank { null } }.getOrNull() }
    @Synchronized fun pending(): List<FeedbackSubmission> = runCatching { FeedbackQueue.decode(preferences.getString("pending", null)) }.getOrDefault(emptyList())
    fun isQueued(id: String?): Boolean = id != null && pending().any { it.id == id }

    /** [accountId] is the signed-in account, or null when signed out (then it is kept on this phone only and never sent). */
    @Synchronized fun save(score: Float, note: String, accountId: String? = null): String? {
        FeedbackRules.error(score, note)?.let { return it }
        latest(); failure?.let { return it }
        val id = UUID.randomUUID().toString(); val at = now(); val words = note.trim()
        val queue = if (accountId == null) null else try {
            FeedbackQueue.add(FeedbackQueue.decode(preferences.getString("pending", null)), FeedbackSubmission(id, accountId, surface, score, words, at))
        } catch (error: IllegalArgumentException) { return error.message } catch (_: Exception) { return "Unsent feedback on this phone could not be read. It has not been replaced." }
        val row = JSONObject().put("schema", 1).put("score", score.toDouble()).put("note", words).put("savedAt", at).put("id", id)
        val edit = preferences.edit().putString("latest", row.toString())
        queue?.let { edit.putString("pending", FeedbackQueue.encode(it)) }
        return if (edit.commit()) null else "Feedback could not be saved. Your note is still here."
    }

    /** Sends this account's queued feedback. Returns null when nothing is left for it, otherwise why it is still queued. */
    suspend fun flush(accountId: String, send: suspend (FeedbackSubmission) -> Unit): String? = sending.withLock {
        FeedbackQueue.flush(pending(), accountId, send) { remaining -> synchronized(this) { preferences.edit().putString("pending", FeedbackQueue.encode(remaining)).commit() } }
    }

    private companion object { val sending = Mutex() }
}

/** The server call for one queued feedback. Idempotent on the id, so a retry after a lost reply does not add a second row. */
internal suspend fun CareCloud.sendAppFeedback(item: FeedbackSubmission) {
    require(session.value?.userId == item.accountId) { "Signed in as a different account. It stays on this phone." }
    val body = JSONObject().put("p_id", item.id).put("p_surface", item.surface).put("p_score", item.score.toDouble()).put("p_note", item.note).put("p_saved_ms", item.savedAt)
    val reply = JSONObject(request("POST", "/rest/v1/rpc/sahara_submit_app_feedback", body, expectedAccountId = item.accountId))
    require(reply.optString("id") == item.id && reply.optString("status") == "received") { "Sahara did not confirm this feedback. It stays on this phone." }
}
