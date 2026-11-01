package `in`.wedevit.care

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only public language-pack downloads use a connection. translate() has no HTTP transport. */
class CareOfflineTranslation {
    private val manager = RemoteModelManager.getInstance()
    private val hindi = TranslateRemoteModel.Builder(TranslateLanguage.HINDI).build()
    suspend fun installed(): Boolean = manager.getDownloadedModels(TranslateRemoteModel::class.java).translationAwait().any { it.language == TranslateLanguage.HINDI }
    suspend fun download(wifiOnly: Boolean) {
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        manager.download(hindi, conditions).translationAwait()
        check(installed()) { "The Hindi language pack is not available yet. Try the download again." }
    }
    /** Language packs already on the phone. English is built in to ML Kit and always counts as installed. */
    suspend fun installedLanguages(): Set<String> =
        manager.getDownloadedModels(TranslateRemoteModel::class.java).translationAwait().map { it.language }.toSet() + TranslateLanguage.ENGLISH

    /** Public model download only; no note text is sent. [language] is an ML Kit code such as "te". */
    suspend fun downloadPack(language: String, wifiOnly: Boolean) {
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        manager.download(TranslateRemoteModel.Builder(language).build(), conditions).translationAwait()
        check(language in installedLanguages()) { "The language pack is not available yet." }
    }

    /**
     * Translates for listening only. Each line is checked with [CareTranslationRules.validate]; a line that fails,
     * is too long, or has no supported model is returned unchanged (and counted) rather than risk a wrong dose.
     * The needed packs must already be installed.
     */
    suspend fun translateForSpeech(lines: List<String>, targetBase: String, progress: (Int, Int) -> Unit): CareSpeechTranslation {
        val target = mlKitCode(targetBase) ?: return CareSpeechTranslation(lines, lines.size)
        val clients = HashMap<String, com.google.mlkit.nl.translate.Translator>()
        var kept = 0
        try {
            val out = lines.mapIndexed { index, line ->
                coroutineContext.ensureActive(); progress(index, lines.size)
                val from = scriptLanguage(line)?.let(::mlKitCode)
                val result = when {
                    from == null || from == target -> line
                    line.length > 4000 -> { kept++; line }
                    else -> {
                        val client = clients.getOrPut(from) { Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(target).build()) }
                        val translated = client.translate(line).translationAwait()
                        try { CareTranslationRules.validate(line, translated); translated } catch (_: IllegalArgumentException) { kept++; line }
                    }
                }
                result
            }
            return CareSpeechTranslation(out, kept)
        } finally { clients.values.forEach { it.close() } }
    }

    companion object {
        /** ML Kit's code for a base language tag ("hi-IN" -> "hi"), or null when ML Kit has no model (Malayalam). */
        fun mlKitCode(base: String): String? = TranslateLanguage.fromLanguageTag(base.substringBefore('-'))

        /** The language a line is written in, judged by its dominant script; null when it has no letters. */
        fun scriptLanguage(line: String): String? {
            val counts = HashMap<String, Int>()
            line.forEach { ch ->
                if (!ch.isLetter()) return@forEach
                val code = when (Character.UnicodeScript.of(ch.code)) {
                    Character.UnicodeScript.LATIN -> "en"
                    Character.UnicodeScript.DEVANAGARI -> "hi"
                    Character.UnicodeScript.TELUGU -> "te"
                    Character.UnicodeScript.KANNADA -> "kn"
                    Character.UnicodeScript.TAMIL -> "ta"
                    Character.UnicodeScript.MALAYALAM -> "ml"
                    Character.UnicodeScript.ARABIC -> "ur"
                    Character.UnicodeScript.BENGALI -> "bn"
                    Character.UnicodeScript.GUJARATI -> "gu"
                    else -> return@forEach
                }
                counts[code] = (counts[code] ?: 0) + 1
            }
            return counts.maxByOrNull { it.value }?.key
        }
    }

    suspend fun translate(source: String, target: String, progress: (Int, Int) -> Unit): List<CareTranslationLine> {
        require(target in listOf("hi", "en"))
        check(installed()) { "Download the Hindi–English pack first." }
        val lines = CareTranslationRules.lines(source)
        val clients = listOf("en" to "hi", "hi" to "en").associateWith { (from, to) ->
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        }
        try {
            return lines.mapIndexed { index, line ->
                coroutineContext.ensureActive(); progress(index, lines.size)
                val from = CareTranslationRules.sourceLanguage(line)
                // A mixed line must still run through the requested direction; never label an unchanged
                // English/Hindi mixture as a completed translation merely because it contains Hindi.
                val modelFrom = if (from == "mixed") if (target == "hi") "en" else "hi" else from
                val output = if (from == target) line else clients.getValue(modelFrom to target).translate(line).translationAwait()
                CareTranslationRules.validate(line, output)
                progress(index + 1, lines.size)
                CareTranslationLine(line, output, from, target)
            }
        } finally { clients.values.forEach { it.close() } }
    }
}

/** [keptOriginal] counts lines spoken in their original words because the translation was not trusted. */
data class CareSpeechTranslation(val lines: List<String>, val keptOriginal: Int)

private suspend fun <T> Task<T>.translationAwait(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
