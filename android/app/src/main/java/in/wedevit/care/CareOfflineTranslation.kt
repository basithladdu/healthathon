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

private suspend fun <T> Task<T>.translationAwait(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
