package `in`.wedevit.care

import android.content.Context
import java.security.MessageDigest
import org.json.JSONObject

/** App-experience feedback only. No network, patient data, analytics or service submissions. */
class FeedbackStore(context: Context, accountScope: String, surface: String, private val now: () -> Long = { System.currentTimeMillis() }) {
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
    @Synchronized fun save(score: Float, note: String): String? {
        FeedbackRules.error(score, note)?.let { return it }
        latest(); failure?.let { return it }
        val row = JSONObject().put("schema", 1).put("score", score.toDouble()).put("note", note.trim()).put("savedAt", now())
        return if (preferences.edit().putString("latest", row.toString()).commit()) null else "Feedback could not be saved. Your note is still here."
    }
}
