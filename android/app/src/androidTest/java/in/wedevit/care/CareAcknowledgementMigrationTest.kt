package `in`.wedevit.care

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CareAcknowledgementMigrationTest {
    @Test fun versionThreeUpgradePreservesLegacyAckAndAllowsSeparateConfirmationsAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "ack-migration-${newId()}.db"
        val path = context.getDatabasePath(filename).apply { parentFile!!.mkdirs() }
        val versionId = newId()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("in.wedevit.care.CareDatabase/3.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
            old.execSQL("INSERT INTO Acknowledgement(versionId,contentHash,name,relationship,at) VALUES(?,?,?,?,?)", arrayOf(versionId, "a".repeat(64), "Existing family", "Family / carer", 10))
            old.version = 3
        }
        var db = Room.databaseBuilder(context, CareDatabase::class.java, filename).addMigrations(AcknowledgementsMigration).build()
        try {
            val legacy = db.care().acknowledgements().first().single()
            assertEquals(versionId, legacy.id)
            assertEquals(versionId, legacy.versionId)
            assertEquals("Existing family", legacy.name)
            val patient = Acknowledgement(versionId, "a".repeat(64), "Patient", "Patient", 20)
            val family = Acknowledgement(versionId, "a".repeat(64), "New family", "Family / carer", 30)
            db.care().acknowledge(patient); db.care().acknowledge(family)
            db.close()
            db = Room.databaseBuilder(context, CareDatabase::class.java, filename).addMigrations(AcknowledgementsMigration).build()
            assertEquals(setOf(legacy, patient, family), db.care().confirmations(versionId).toSet())
            assertEquals(3, db.care().acknowledgements().first().size)
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
