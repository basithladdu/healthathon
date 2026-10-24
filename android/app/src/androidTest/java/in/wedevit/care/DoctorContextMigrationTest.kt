package `in`.wedevit.care

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Unique throwaway database only; never opens care.db or an account cache. */
class DoctorContextMigrationTest {
    @Test fun additiveUpgradePreservesOldDraftHashAndAcknowledgement() = runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="qa-context-migration-${newId()}.db"
        val person=newId();val note=newId()
        val file=context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        try {
            SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
                val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("in.wedevit.care.CareDatabase/6.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                val entities=schema.getJSONArray("entities")
                repeat(entities.length()) { i -> val e=entities.getJSONObject(i);val table=e.getString("tableName")
                    old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indices=e.optJSONArray("indices");if(indices!=null) repeat(indices.length()) { k -> old.execSQL(indices.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}",table)) }
                }
                val setup=schema.getJSONArray("setupQueries");repeat(setup.length()) { old.execSQL(setup.getString(it)) }
                old.execSQL("INSERT INTO Person(id,name,created) VALUES(?,?,?)",arrayOf(person,"Synthetic retained person",1))
                old.execSQL("INSERT INTO Conversation(personId,source,priorities,participants,topics,questions,nextSteps,audioPath,consentAt,updated) VALUES(?,?,?,?,?,?,?,?,?,?)",arrayOf(person,"Synthetic source","Priority","Participant","Summary","Question","Next","/synthetic/local.m4a",2,3))
                old.execSQL("INSERT INTO CareVersion(id,personId,version,personName,source,priorities,participants,topics,questions,nextSteps,doctor,published,contentHash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",arrayOf(note,person,1,"Synthetic retained person","Synthetic source","Priority","Participant","Summary","Question","Next","Synthetic reviewer",4,"unchanged-historical-hash"))
                old.execSQL("INSERT INTO Acknowledgement(versionId,contentHash,name,relationship,at,id) VALUES(?,?,?,?,?,?)",arrayOf(note,"unchanged-historical-hash","Synthetic reader","Patient",5,note))
                old.version=6
            }
            val db=Room.databaseBuilder(context,CareDatabase::class.java,name).addMigrations(CombinedCareMigration6To7).build()
            try {
                assertEquals(1,db.care().people().first().size)
                val draft=db.care().draft(person)!!
                assertEquals("Synthetic source",draft.source);assertEquals("/synthetic/local.m4a",draft.audioPath);assertEquals("",draft.contextJson)
                val version=db.care().latest(person)!!
                assertEquals("unchanged-historical-hash",version.contentHash);assertEquals("legacy",version.hashScheme)
                assertEquals("unchanged-historical-hash",db.care().confirmation(note)!!.contentHash)
                assertTrue(db.prescriptions().groups(person).isEmpty());assertTrue(db.conversations().archives(person).first().isEmpty())
                val archive=DoctorConversationArchive(newId(),person,newId(),"synthetic-private-context",6)
                db.conversations().archive(archive)
                db.prescriptions().putGroup(DoctorPrescription(newId(),person,"Synthetic group",createdAt=7))
                db.withTransaction { db.care().clearPeople();db.care().addPerson(Person(person,"Synthetic retained person",1)) }
                assertEquals(archive,db.conversations().archives(person).first().single())
                assertEquals(1,db.prescriptions().groups(person).size)
                db.withTransaction { db.conversations().purgePerson(person);db.prescriptions().purgePerson(person) }
                assertTrue(db.conversations().archives(person).first().isEmpty());assertTrue(db.prescriptions().groups(person).isEmpty())
            } finally { db.close() }
        } finally { check(name.startsWith("qa-context-migration-"));context.deleteDatabase(name) }
    }
}
