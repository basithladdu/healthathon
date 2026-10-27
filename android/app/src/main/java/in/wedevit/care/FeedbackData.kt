package `in`.wedevit.care

data class FeedbackRecord(val score: Float, val note: String, val savedAt: Long)

object FeedbackRules {
    const val noteLimit = 500
    fun label(score: Float): String = when {
        score < 1f / 3f -> "Not great"
        score < 2f / 3f -> "Okay"
        else -> "Good"
    }
    fun error(score: Float, note: String): String? = when {
        !score.isFinite() || score !in 0f..1f -> "Choose how your app experience felt."
        note.length > noteLimit -> "Keep your note under 500 characters."
        else -> null
    }
    /** Continuous curve; no three-image or emoji state switching. */
    fun mouthCurve(score: Float): Float = (score.coerceIn(0f, 1f) - .5f) * .48f
    private fun eyeValue(score: Float, sad: Float, neutral: Float, happy: Float): Float {
        val value = score.coerceIn(0f, 1f)
        return if (value <= .5f) sad + (neutral - sad) * value * 2f else neutral + (happy - neutral) * (value - .5f) * 2f
    }
    fun eyeHeight(score: Float): Float = eyeValue(score, .18f, .05f, .31f)
    fun eyeWidth(score: Float): Float = eyeValue(score, .18f, .28f, .31f)
}

/** One app-feedback save waiting for the server. [accountId] is the account signed in when it was saved; it is only ever sent as that account. */
data class FeedbackSubmission(val id: String, val accountId: String, val surface: String, val score: Float, val note: String, val savedAt: Long)

/** Pure queue logic behind FeedbackStore, kept free of Android so it can be unit-tested. */
object FeedbackQueue {
    const val limit = 20
    fun decode(text: String?): List<FeedbackSubmission> {
        if (text == null) return emptyList()
        val rows = org.json.JSONArray(text)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            FeedbackSubmission(row.getString("id"), row.getString("accountId"), row.getString("surface"), row.getDouble("score").toFloat(), row.getString("note"), row.getLong("savedAt")).also {
                require(it.id.isNotBlank() && it.accountId.isNotBlank() && FeedbackRules.error(it.score, it.note) == null && it.savedAt > 0)
            }
        }
    }
    fun encode(items: List<FeedbackSubmission>): String = org.json.JSONArray(items.map {
        org.json.JSONObject().put("id", it.id).put("accountId", it.accountId).put("surface", it.surface).put("score", it.score.toDouble()).put("note", it.note).put("savedAt", it.savedAt)
    }).toString()
    fun add(items: List<FeedbackSubmission>, item: FeedbackSubmission): List<FeedbackSubmission> {
        require(items.size < limit) { "$limit feedback notes are waiting to be sent. Connect to the internet and open Feedback while signed in. · $limit फ़ीडबैक भेजे जाने बाकी हैं। इंटरनेट से जुड़कर, साइन इन रहते हुए फ़ीडबैक खोलें।" }
        return items + item
    }

    /** Sends this account's queued items oldest first. Each success is removed and saved at once; the first failure stops, so the rest stay queued. Returns the failure message, or null when all were sent. */
    suspend fun flush(items: List<FeedbackSubmission>, accountId: String, send: suspend (FeedbackSubmission) -> Unit, persist: (List<FeedbackSubmission>) -> Boolean): String? {
        var remaining = items
        for (item in items.filter { it.accountId == accountId }) {
            try { send(item) } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (error: Exception) { return error.message ?: "Couldn't reach Sahara." }
            remaining = remaining.filterNot { it.id == item.id }
            if (!persist(remaining)) return "Sent, but this phone couldn't record that. It may be sent again; the server keeps one copy."
        }
        return null
    }
}
