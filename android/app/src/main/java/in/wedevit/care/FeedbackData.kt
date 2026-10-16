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
