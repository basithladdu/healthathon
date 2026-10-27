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

/** Unique throwaway database only; never opens care.db or an account cache. */
class CareSyncMigrationTest {
    @Test fun upgradeKeepsRecordsAndAddsAnEmptyOrderedQueue() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "qa-sync-migration-${newId()}.db"
        val person = newId(); val item = newId()
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("in.wedevit.care.CareDatabase/7.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                val entities = schema.getJSONArray("entities")
                repeat(entities.length()) { i -> val e = entities.getJSONObject(i); val table = e.getString("tableName")
                    old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = e.optJSONArray("indices"); if (indices != null) repeat(indices.length()) { k -> old.execSQL(indices.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", table)) }
                }
                val setup = schema.getJSONArray("setupQueries"); repeat(setup.length()) { old.execSQL(setup.getString(it)) }
                old.execSQL("INSERT INTO Person(id,name,created) VALUES(?,?,?)", arrayOf(person, "Synthetic retained person", 1))
                old.execSQL("INSERT INTO CareItem(id,personId,kind,title,detail,due,doneOn,created,revision) VALUES(?,?,?,?,?,?,?,?,?)", arrayOf(item, person, "checkin", "Synthetic check-in", "", 0, "", 2, 3))
                old.version = 7
            }
            val db = Room.databaseBuilder(context, CareDatabase::class.java, name).addMigrations(CareSyncMigration7To8).build()
            try {
                assertEquals(3L, db.care().item(item)!!.revision)
                assertTrue(db.sync().pending().isEmpty())
                val first = CareSyncOps.saveEntry(CareItem(personId = person, kind = "checkin", title = "First"))
                val second = CareSyncOps.saveEntry(CareItem(personId = person, kind = "checkin", title = "Second"))
                db.sync().insert(first); db.sync().insert(second)
                assertEquals(listOf("First", "Second"), db.sync().pending().map { CareSyncOps.item(it).title })
                assertEquals(2, db.sync().count(CareSyncQueue.WAITING).first())
                assertTrue(runCatching { db.sync().insert(first) }.isFailure)
                db.sync().purgePerson(person); assertTrue(db.sync().pending().isEmpty())
            } finally { db.close() }
        } finally { check(name.startsWith("qa-sync-migration-")); context.deleteDatabase(name) }
    }
}
