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

/** Exercises both historical schema-3 branches without opening any real care database. */
class CombinedCareMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun upstreamReportsVersionThreePreservesReports() = verifyUpgrade(3, false)
    @Test fun historicalLocalVersionThreePreservesConsentAndReview() = verifyUpgrade(3, true)
    @Test fun localVersionFourPreservesTranscriptionAndConsent() = verifyUpgrade(4, true)
    @Test fun upstreamVersionFourPreservesMultipleConfirmationIds() = verifyUpgrade(4, false)
    @Test fun localVersionFivePreservesReportsAndProvenance() = verifyUpgrade(5, true)
    @Test fun versionOneReachesSupersetThroughRevisionAndReports() = verifyUpgrade(1, false)
    @Test fun versionTwoReachesSupersetThroughReports() = verifyUpgrade(2, false)

    private fun verifyUpgrade(version: Int, local: Boolean) = runBlocking {
        val filename = "combined-migration-${newId()}.db"
        val person = Person(name = "Synthetic migration person", created = 100)
        val task = CareItem(personId = person.id, kind = "task", title = "Synthetic existing task", created = 101, revision = if(version==1) 0 else 7)
        val audioPath = "/synthetic-fixture/not-a-real-recording.m4a"
        val hasTranscript = local && version >= 4
        val source = Conversation(person.id, source = "Synthetic conversation", audioPath = audioPath, updated = 102,
            transcriptProvider = if (hasTranscript) "fixture-provider" else "",
            transcriptModel = if (hasTranscript) "fixture-model" else "",
            transcribedAt = if (hasTranscript) 103 else 0,
            transcriptAudioPath = if (hasTranscript) audioPath else "")
        val note = CareVersion(personId = person.id, version = 1, personName = person.name, source = source.source,
            priorities = "Synthetic priority", participants = "Synthetic participant", topics = "Synthetic topic",
            questions = "Synthetic question", nextSteps = "Synthetic next step", doctor = "Fixture reviewer",
            published = 104, contentHash = "fixture-existing-hash",
            draftOrigin = if (local) "fixture-origin" else ReviewWorkflow.MANUAL_DRAFT,
            transcriptionModel = if (hasTranscript) "fixture-model" else "", transcribedAt = if (hasTranscript) 103 else 0)
        val remoteAcknowledgements = !local && version == 4
        val ack = Acknowledgement(note.id, note.contentHash, "Fixture reader", "Patient", 105, id=if(remoteAcknowledgements) "existing-independent-patient-id" else note.id)
        val family = Acknowledgement(note.id, note.contentHash, "Fixture family", "Family / carer", 106, id="existing-independent-family-id")
        val consent = RecordingConsent(audioPath, person.id, "Synthetic participant", 106, 107, 2)
        val report = CareReport(personId = person.id, name = "Synthetic report.pdf", mime = "application/pdf",
            fileName = "fixture.pdf", bytes = 123, created = 108)
        val path = context.getDatabasePath(filename).apply { parentFile!!.mkdirs() }
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                // Canonical upstream 3/4 and local 5 retain their original schemas.
                // The incompatible historical local 4 is an exact safety-snapshot asset.
                // Historical local 3 is recreated exactly from the shared v2 schema plus
                // the consent/review SQL recorded in the pre-merge local migration.
                val fixtureVersion = if(local && version==3) 2 else version
                val fixtureAsset = if(local && version==4) "migration-fixtures/local-4.json" else "in.wedevit.care.CareDatabase/$fixtureVersion.json"
                val schema = InstrumentationRegistry.getInstrumentation().context.assets
                    .open(fixtureAsset)
                    .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                if (local && version == 3) {
                    old.execSQL("ALTER TABLE CareVersion ADD COLUMN draftOrigin TEXT NOT NULL DEFAULT 'manual'")
                    old.execSQL("ALTER TABLE CareVersion ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'reviewed_unverified'")
                    old.execSQL("ALTER TABLE CareVersion ADD COLUMN reviewerVerification TEXT NOT NULL DEFAULT 'not_verified'")
                    old.execSQL("CREATE TABLE RecordingConsent (audioPath TEXT NOT NULL, personId TEXT NOT NULL, participants TEXT NOT NULL, recordingConsentAt INTEGER NOT NULL, transcriptionConsentAt INTEGER NOT NULL, noticeVersion INTEGER NOT NULL, PRIMARY KEY(audioPath))")
                    old.execSQL("CREATE INDEX index_RecordingConsent_personId ON RecordingConsent(personId)")
                }
                old.execSQL("INSERT INTO Person VALUES(?,?,?)", arrayOf(person.id, person.name, person.created))
                if(version==1) old.execSQL("INSERT INTO CareItem VALUES(?,?,?,?,?,?,?,?)", arrayOf(task.id, task.personId, task.kind, task.title, task.detail, task.due, task.doneOn, task.created))
                else old.execSQL("INSERT INTO CareItem VALUES(?,?,?,?,?,?,?,?,?)", arrayOf(task.id, task.personId, task.kind, task.title, task.detail, task.due, task.doneOn, task.created, task.revision))
                old.execSQL("INSERT INTO Conversation(personId,source,priorities,participants,topics,questions,nextSteps,audioPath,consentAt,updated) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    arrayOf(person.id, source.source, "", "", "", "", "", audioPath, 0, source.updated))
                old.execSQL("INSERT INTO CareVersion(id,personId,version,personName,source,priorities,participants,topics,questions,nextSteps,doctor,published,contentHash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    arrayOf(note.id, person.id, 1, person.name, note.source, note.priorities, note.participants, note.topics, note.questions, note.nextSteps, note.doctor, note.published, note.contentHash))
                if(remoteAcknowledgements) {
                    listOf(ack,family).forEach {entry -> old.execSQL("INSERT INTO Acknowledgement(versionId,contentHash,name,relationship,at,id) VALUES(?,?,?,?,?,?)",arrayOf(entry.versionId,entry.contentHash,entry.name,entry.relationship,entry.at,entry.id))}
                } else old.execSQL("INSERT INTO Acknowledgement VALUES(?,?,?,?,?)", arrayOf(ack.versionId, ack.contentHash, ack.name, ack.relationship, ack.at))
                if (local) {
                    old.execSQL("UPDATE CareVersion SET draftOrigin=? WHERE id=?", arrayOf(note.draftOrigin, note.id))
                    old.execSQL("INSERT INTO RecordingConsent VALUES(?,?,?,?,?,?)", arrayOf(audioPath, person.id, consent.participants, consent.recordingConsentAt, consent.transcriptionConsentAt, consent.noticeVersion))
                }
                if((!local && version>=3) || version==5) old.execSQL("INSERT INTO CareReport VALUES(?,?,?,?,?,?,?)", arrayOf(report.id, person.id, report.name, report.mime, report.fileName, report.bytes, report.created))
                if (hasTranscript) {
                    old.execSQL("UPDATE Conversation SET transcriptProvider=?,transcriptModel=?,transcribedAt=?,transcriptAudioPath=?", arrayOf(source.transcriptProvider, source.transcriptModel, source.transcribedAt, audioPath))
                    old.execSQL("UPDATE CareVersion SET transcriptionModel=?,transcribedAt=?", arrayOf(note.transcriptionModel, note.transcribedAt))
                }
                old.version = version
            }
            fun open() = Room.databaseBuilder(context, CareDatabase::class.java, filename)
                .addMigrations(RevisionMigration,ReportsMigration, CombinedCareMigration3To6, CombinedCareMigration4To6,CombinedCareMigration5To6,CombinedCareMigration6To7,CareSyncMigration7To8).build()
            var db = open()
            try {
                assertEquals(person, db.care().people().first().single())
                assertEquals(task, db.care().items(person.id).first().single())
                assertEquals(source, db.care().draft(person.id))
                assertEquals(note, db.care().latest(person.id))
                assertEquals(if(remoteAcknowledgements) setOf(ack,family) else setOf(ack), db.care().acknowledgements().first().toSet())
                if (local) {
                    assertEquals(consent, db.care().recordingConsents(person.id).first().single())
                    if(version!=5) db.care().addReport(report)
                } else {
                    if(version>=3) assertEquals(report, db.care().reports(person.id).first().single())
                    else db.care().addReport(report)
                    db.care().saveRecordingConsent(consent)
                }
                db.close()
                db = open()
                // The merged doctor conversation/prescription schema extends the preserved v6 superset.
                assertEquals(7, db.openHelper.readableDatabase.version)
                assertEquals(report, db.care().reports(person.id).first().single())
                assertEquals(consent, db.care().recordingConsents(person.id).first().single())
                assertEquals(note, db.care().latest(person.id))
                assertEquals(source, db.care().draft(person.id))
                assertEquals(if(remoteAcknowledgements) setOf(ack,family) else setOf(ack),db.care().confirmations(note.id).toSet())
                db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                db.openHelper.readableDatabase.query("PRAGMA index_list('Acknowledgement')").use {cursor ->
                    val names=mutableSetOf<String>();while(cursor.moveToNext()) names.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    assertTrue("index_Acknowledgement_versionId" in names)
                }
            } finally { db.close() }
        } finally { context.deleteDatabase(filename) }
    }
}
