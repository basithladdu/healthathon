package `in`.wedevit.care

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** A report's name is only a label in Sahara; the stored file name never changes. */
object ReportNameRules {
    fun error(name: String): String? = when {
        name.isBlank() -> careBilingual("Add a name.", "नाम लिखें।")
        name.trim().length > 160 -> careBilingual("Use 160 characters or fewer.", "160 अक्षर तक।")
        name.any { it.isISOControl() } || name.contains('/') || name.contains('\\') -> careBilingual("Don't use / or \\ in the name.", "/ या \\ न लिखें।")
        else -> null
    }
    fun clean(name: String): String { error(name)?.let { throw IllegalArgumentException(it) }; return name.trim() }
}

/** Private copies, isolated in the same account and person boundaries as the Room cache. */
class ReportFiles(private val context: Context, account: String = "") {
    private val root = File(context.filesDir, "reports/" + account.ifEmpty { "guest" })
    init { require(account.isEmpty() || runCatching { UUID.fromString(account) }.isSuccess) }

    private fun folder(person: String): File {
        require(runCatching { UUID.fromString(person) }.isSuccess) { "Choose a person before adding a report." }
        return File(root, person)
    }

    fun file(report: CareReport): File {
        val directory = folder(report.personId).canonicalFile
        val file = File(directory, report.fileName).canonicalFile
        require(file.parentFile == directory) { "Report unavailable." }
        return file
    }

    suspend fun copyFrom(uri: Uri, person: String): CareReport = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a PDF or photo from Files." }
        val resolver = context.contentResolver
        var name = "Report"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let {
                    require(cursor.getLong(it) <= MAX_BYTES) { "Choose a file smaller than 20 MB." }
                }
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) ?: name }
            }
        }
        name = name.replace('\\', '/').substringAfterLast('/').filterNot { it.isISOControl() }.trim().take(160).ifBlank { "Report" }
        val stream = resolver.openInputStream(uri) ?: throw IllegalArgumentException("Couldn't open that file. Choose it again.")
        stream.buffered().use { input ->
            input.mark(16)
            val header = ByteArray(12)
            var count = 0
            while (count < header.size) { val read = input.read(header, count, header.size - count); if (read < 0) break; count += read }
            input.reset()
            val format = when {
                count >= 5 && header.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray()) -> "application/pdf" to "pdf"
                count >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() -> "image/jpeg" to "jpg"
                count >= 8 && header.take(8).toByteArray().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> "image/png" to "png"
                count == 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp" to "webp"
                else -> throw IllegalArgumentException("Choose a PDF, JPG, PNG or WebP photo.")
            }
            val directory = folder(person)
            require(directory.isDirectory || directory.mkdirs()) { "Couldn't save the report. Check free space and try again." }
            val filename = newId() + "." + format.second
            val target = File(directory, filename)
            try {
                var bytes = 0L
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        bytes += read
                        require(bytes <= MAX_BYTES) { "Choose a file smaller than 20 MB." }
                        output.write(buffer, 0, read)
                    }
                }
                CareReport(personId = person, name = name, mime = format.first, fileName = filename, bytes = bytes)
            } catch (e: Exception) { target.delete(); throw e }
        }
    }

    fun intent(report: CareReport, share: Boolean): Intent {
        val file = file(report)
        require(file.isFile) { "This report is missing. Add it again from Files." }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file, report.name)
        return Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            if (share) { type = report.mime; putExtra(Intent.EXTRA_STREAM, uri) }
            else setDataAndType(uri, report.mime)
            clipData = ClipData.newUri(context.contentResolver, report.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun removePerson(person: String) { folder(person).deleteRecursively() }
    companion object { const val MAX_BYTES = 20L * 1024 * 1024 }
}
