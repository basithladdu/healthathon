package `in`.wedevit.care

import java.io.File
import org.json.JSONObject

data class DoctorAiEvidence(val section: String, val quote: String)
data class DoctorAiDraft(val sections: Map<String, String>, val evidence: List<DoctorAiEvidence>, val warnings: List<String>, val provider: String, val model: String)
data class DoctorAudioAiResult(val transcript: ConversationTranscript, val draft: DoctorAiDraft?, val draftError: String?)
val doctorAiSectionKeys = listOf("priorities", "participants", "topics", "questions", "undecided", "nextSteps")

fun parseDoctorAiDraft(json: String, transcript: String): DoctorAiDraft {
    val result = JSONObject(json)
    require(result.getString("provider") == "google-gemini") { "Unknown AI provider." }
    val model = result.getString("model"); require(model.length in 1..100)
    val rawSections = result.getJSONObject("sections")
    val sections = doctorAiSectionKeys.associateWith { rawSections.getString(it).also { value -> require(value.length <= 4000) } }
    val rawEvidence = result.getJSONArray("evidence")
    require(rawEvidence.length() <= 120)
    val evidence = (0 until rawEvidence.length()).map { index -> rawEvidence.getJSONObject(index).let { DoctorAiEvidence(it.getString("section"), it.getString("quote")) } }
    require(evidence.all { it.section in doctorAiSectionKeys && it.quote.isNotBlank() && it.quote.length <= 4000 && transcript.lineSequence().any { line -> line.isNotBlank() && line == it.quote } }) { "AI source evidence did not match a complete transcript turn." }
    require(evidence.filter { it.section == "priorities" }.all { Regex("(?:^|\\]\\s*)patient\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(it.quote) }) { "The patient speaker was not established for priorities." }
    require(sections.all { (section, value) -> value == evidence.filter { it.section == section }.map { it.quote }.distinct().joinToString("\n") }) { "AI draft contains unsupported text." }
    val warnings = result.getJSONArray("warnings"); require(warnings.length() in 1..20)
    return DoctorAiDraft(sections, evidence, (0 until warnings.length()).map { warnings.getString(it).take(1000) }, "google-gemini", model)
}

/** Uses an actual Sahara user token. The local doctor mock gate cannot authorize this call. */
class DoctorAiClient(private val transcriber: ConversationTranscriber = ConversationTranscriber()) {
    suspend fun transcribeAndDraft(file: File, consent: RecordingConsent, bearerToken: String): DoctorAudioAiResult = transcriber.transcribeAndDraft(file, consent, bearerToken)
}
