package `in`.wedevit.care

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Shared source pages are copied once, independent of the number of medicines referencing them. */
class MedicineSourceStore(context: Context, private val account: String) {
    private val root = File(context.filesDir, "reports/${account.ifBlank { "guest" }}")
    private val reportFiles = ReportFiles(context.applicationContext, account)
    init { require(account.isBlank() || runCatching { UUID.fromString(account) }.isSuccess) }
    private fun index(person: String, scanId: String): File {
        require(runCatching { UUID.fromString(person); UUID.fromString(scanId) }.isSuccess)
        return File(File(root, person), "prescription-source-$scanId.json")
    }
    suspend fun save(person: String, scanId: String, uris: List<Uri>, files: ReportFiles): List<CareReport> = withContext(Dispatchers.IO) {
        require(uris.size <= 5)
        val target = index(person, scanId)
        if (target.isFile) {
            val saved = read(target, person)
            require(saved.size == uris.size && saved.all { files.file(it).isFile }) { "The prescription pages changed. Read them again before saving." }
            return@withContext saved
        }
        val copied = mutableListOf<CareReport>()
        try {
            uris.forEachIndexed { page, uri -> copied += files.copyFrom(uri, person).let { it.copy(name = "Prescription page ${page + 1} · ${it.name}") } }
            if (copied.isNotEmpty()) {
                val pages = JSONArray().apply { copied.forEachIndexed { page, report -> put(JSONObject().put("id", report.id).put("person", person).put("name", report.name).put("mime", report.mime).put("file", report.fileName).put("bytes", report.bytes).put("created", report.created).put("page", page + 1).put("sha256", sha256(files.file(report)))) } }
                val atomic = android.util.AtomicFile(target); val output = atomic.startWrite()
                try { output.write(JSONObject().put("scan", scanId).put("person", person).put("pages", pages).toString().toByteArray()); atomic.finishWrite(output) }
                catch (failure: Exception) { atomic.failWrite(output); throw failure }
            }
            copied
        } catch (failure: Exception) { copied.forEach { runCatching { files.file(it).delete() } }; throw failure }
    }
    fun list(person: String): List<CareReport> {
        require(runCatching { UUID.fromString(person) }.isSuccess)
        return File(root, person).listFiles().orEmpty().filter { it.name.startsWith("prescription-source-") && it.extension == "json" }.flatMap { runCatching { read(it, person) }.getOrDefault(emptyList()) }.distinctBy { it.id }
    }
    private fun read(file: File, person: String): List<CareReport> {
        require(file.length() in 1..50_000)
        val json = JSONObject(file.readText()); require(json.getString("person") == person)
        val array = json.getJSONArray("pages"); require(array.length() in 1..5)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            val report = CareReport(it.getString("id"), person, it.getString("name"), it.getString("mime"), it.getString("file"), it.getLong("bytes"), it.getLong("created"))
            val original = reportFiles.file(report)
            require(original.isFile && original.length() == report.bytes && sha256(original) == it.getString("sha256")) { "An original prescription page is unavailable or changed. Check the original file." }
            report
        } }
    }
    fun references(reports: List<CareReport>, files: ReportFiles): String = reports.mapIndexed { page, report -> "Page ${page + 1}: ${report.id} · SHA-256 ${sha256(files.file(report))}" }.joinToString("\n")
    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = java.security.MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(16 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
