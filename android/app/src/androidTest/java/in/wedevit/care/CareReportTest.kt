package `in`.wedevit.care

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.RandomAccessFile

class CareReportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun reportCopiesAreIsolatedAndUnsafeFilesAreRejected() = runBlocking {
        val person = newId(); val otherAccount = newId()
        val files = ReportFiles(context); val other = ReportFiles(context, otherAccount)
        val source = CareReport(personId = person, name = "Scan.pdf", mime = "application/pdf", fileName = "source.pdf", bytes = 0)
        val original = files.file(source).apply { parentFile!!.mkdirs() }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", original, source.name)
        try {
            original.writeText("%PDF-1.7\nSynthetic fixture\n%%EOF")
            val report = files.copyFrom(uri, person)
            assertArrayEquals(original.readBytes(), files.file(report).readBytes())
            assertFalse(other.file(report).exists())
            assertNotEquals(files.file(report).canonicalPath, other.file(report).canonicalPath)
            val before = original.parentFile!!.list()!!.toSet()
            original.writeText("not a PDF or photo")
            try { files.copyFrom(uri, person); fail("Unsupported content was accepted") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("PDF")) }
            assertEquals(before, original.parentFile!!.list()!!.toSet())
            RandomAccessFile(original, "rw").use { it.setLength(ReportFiles.MAX_BYTES + 1) }
            try { files.copyFrom(uri, person); fail("Oversized file was accepted") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("20 MB")) }
            assertEquals(before, original.parentFile!!.list()!!.toSet())
            try { files.file(source.copy(fileName = "../outside.pdf")); fail("Path traversal allowed") } catch (_: IllegalArgumentException) { }
        } finally { files.removePerson(person); other.removePerson(person) }
    }

    @Test fun versionTwoUpgradePreservesCareAndReportMetadataAfterReopen() = runBlocking {
        val filename = "migration-${newId()}.db"
        val person = Person(name = "Migration test person")
        val task = CareItem(personId = person.id, kind = "task", title = "Existing task", doneOn = "2026-10-10", revision = 7)
        val path = context.getDatabasePath(filename).apply { parentFile!!.mkdirs() }
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("in.wedevit.care.CareDatabase/2.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO Person(id,name,created) VALUES(?,?,?)", arrayOf(person.id, person.name, person.created))
            old.execSQL("INSERT INTO CareItem(id,personId,kind,title,detail,due,doneOn,created,revision) VALUES(?,?,?,?,?,?,?,?,?)", arrayOf(task.id, person.id, task.kind, task.title, "", 0, task.doneOn, task.created, task.revision))
            old.version = 2
        }
        var db = Room.databaseBuilder(context, CareDatabase::class.java, filename).addMigrations(ReportsMigration, AcknowledgementsMigration).build()
        try {
            assertEquals(person, db.care().people().first().single())
            assertEquals(task, db.care().items(person.id).first().single())
            val report = CareReport(personId = person.id, name = "Saved scan.pdf", mime = "application/pdf", fileName = "test.pdf", bytes = 123)
            db.care().addReport(report); db.close()
            db = Room.databaseBuilder(context, CareDatabase::class.java, filename).addMigrations(ReportsMigration, AcknowledgementsMigration).build()
            assertEquals(report, db.care().reports(person.id).first().single())
            assertTrue(db.care().reports(newId()).first().isEmpty())
            assertEquals(task, db.care().items(person.id).first().single())
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
