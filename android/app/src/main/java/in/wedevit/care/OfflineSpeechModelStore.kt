package `in`.wedevit.care

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The only network boundary in offline speech: fixed, public model GETs; no audio or text parameters. */
class OfflineSpeechModelStore(
    context: Context,
    modelId: String = OfflineSpeechModel.id,
    private val files: List<OfflineModelFile> = OfflineSpeechModel.files,
    private val urlOf: (OfflineModelFile) -> String = OfflineSpeechModel::url,
) {
    val directory = File(context.noBackupFilesDir, "offline-speech/$modelId")
    private val downloadBytes: Long get() = files.sumOf { it.bytes }

    suspend fun ready(): Boolean = withContext(Dispatchers.IO) {
        modelLock.withLock { files.all { currentCoroutineContext().ensureActive(); OfflineSpeechRules.matches(File(directory, it.name), it) } }
    }

    suspend fun download(progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        modelLock.withLock {
            directory.mkdirs()
            val missing = files.filterNot { OfflineSpeechRules.matches(File(directory, it.name), it) }
            val remaining = missing.sumOf { it.bytes }
            require(directory.usableSpace > remaining + 64L * 1024 * 1024) { "Free at least ${(remaining / 1_000_000) + 64} MB, then try again." }
            var complete = downloadBytes - remaining
            progress(complete, downloadBytes)
            missing.forEach { spec ->
                currentCoroutineContext().ensureActive()
                val destination = File(directory, spec.name)
                val pending = File(directory, "${spec.name}.part")
                try {
                    require(destination.parentFile?.isDirectory == true || destination.parentFile?.mkdirs() == true) { "The language folder could not be created. Free some storage and retry." }
                    // URLs are immutable constants; neither the recording nor any identifier is sent.
                    val connection = URL(urlOf(spec)).openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 20_000; connection.readTimeout = 30_000
                        connection.setRequestProperty("Accept-Encoding", "identity")
                        require(connection.responseCode == HttpURLConnection.HTTP_OK) { "The language download could not connect. Try again." }
                        require(connection.url.protocol == "https") { "The language download used an insecure connection." }
                        val hash = MessageDigest.getInstance("SHA-256")
                        var received = 0L
                        connection.inputStream.buffered().use { input -> pending.outputStream().buffered().use { output ->
                            val buffer = ByteArray(65_536)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer); if (count < 0) break
                                received += count
                                if (received > spec.bytes) throw IOException("The language download has the wrong size. Try again.")
                                hash.update(buffer, 0, count); output.write(buffer, 0, count)
                                progress(complete + received, downloadBytes)
                            }
                        } }
                        val actualHash = hash.digest().joinToString("") { "%02x".format(it) }
                        require(received == spec.bytes && actualHash == spec.sha256) { "The language download could not be verified. Try again." }
                        require(pending.renameTo(destination)) { "The language file could not be saved. Free some storage and retry." }
                        complete += received
                    } finally { connection.disconnect() }
                } finally { pending.delete() }
            }
            progress(downloadBytes, downloadBytes)
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        // Explicit files only, never a recursive delete of a computed path.
        modelLock.withLock { files.forEach { File(directory, it.name).delete(); File(directory, "${it.name}.part").delete() } }
    }
    companion object { private val modelLock = Mutex() }
}
