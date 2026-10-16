package `in`.wedevit.care

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Entity data class Person(@PrimaryKey val id: String = newId(), val name: String, val created: Long = System.currentTimeMillis())
@Entity data class Conversation(
    @PrimaryKey val personId: String, val source: String = "", val priorities: String = "", val participants: String = "",
    val topics: String = "", val questions: String = "", val nextSteps: String = "", val audioPath: String = "",
    val consentAt: Long = 0, val updated: Long = System.currentTimeMillis()
)
@Entity(indices = [Index(value = ["personId", "version"], unique = true)])
data class CareVersion(
    @PrimaryKey val id: String = newId(), val personId: String, val version: Int, val personName: String,
    val source: String, val priorities: String, val participants: String, val topics: String,
    val questions: String, val nextSteps: String, val doctor: String, val published: Long = System.currentTimeMillis(),
    val contentHash: String
)
@Entity(indices = [Index("versionId")])
data class Acknowledgement(val versionId: String, val contentHash: String, val name: String, val relationship: String, val at: Long = System.currentTimeMillis(), @PrimaryKey val id: String = newId())
@Entity data class CareItem(@PrimaryKey val id: String = newId(), val personId: String, val kind: String, val title: String, val detail: String = "", val due: Long = 0, val doneOn: String = "", val created: Long = System.currentTimeMillis(), @ColumnInfo(defaultValue = "0") val revision: Long = 0)
@Entity(indices = [Index("personId")])
data class CareReport(@PrimaryKey val id: String = newId(), val personId: String, val name: String, val mime: String, val fileName: String, val bytes: Long, val created: Long = System.currentTimeMillis())

@Dao interface CareDao {
    @Query("SELECT * FROM Person ORDER BY created") fun people(): Flow<List<Person>>
    @Insert suspend fun addPerson(person: Person)
    @Query("SELECT * FROM Conversation WHERE personId = :id") suspend fun draft(id: String): Conversation?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDraft(draft: Conversation)
    @Query("SELECT * FROM CareVersion WHERE personId = :id ORDER BY version DESC") fun versions(id: String): Flow<List<CareVersion>>
    @Query("SELECT * FROM CareVersion WHERE personId = :id ORDER BY version DESC LIMIT 1") suspend fun latest(id: String): CareVersion?
    @Insert suspend fun insertVersion(version: CareVersion)
    @Query("SELECT * FROM Acknowledgement") fun acknowledgements(): Flow<List<Acknowledgement>>
    @Query("SELECT * FROM Acknowledgement WHERE versionId = :versionId ORDER BY at") suspend fun confirmations(versionId: String): List<Acknowledgement>
    @Query("SELECT * FROM Acknowledgement WHERE id = :id") suspend fun confirmation(id: String): Acknowledgement?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun acknowledge(acknowledgement: Acknowledgement)
    @Query("SELECT * FROM CareItem WHERE personId = :id ORDER BY created DESC") fun items(id: String): Flow<List<CareItem>>
    @Query("SELECT * FROM CareItem WHERE id = :id LIMIT 1") suspend fun item(id: String): CareItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveItem(item: CareItem)
    @Delete suspend fun removeItem(item: CareItem)
    @Query("SELECT * FROM CareReport WHERE personId = :id ORDER BY created DESC") fun reports(id: String): Flow<List<CareReport>>
    @Insert suspend fun addReport(report: CareReport)
    @Delete suspend fun removeReport(report: CareReport)
    @Query("DELETE FROM CareReport WHERE personId = :id") suspend fun deleteReports(id: String)
    @Query("DELETE FROM Person WHERE id = :id") suspend fun deletePersonRow(id: String)
    @Query("DELETE FROM Conversation WHERE personId = :id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM Acknowledgement WHERE versionId IN (SELECT id FROM CareVersion WHERE personId = :id)") suspend fun deleteAcknowledgements(id: String)
    @Query("DELETE FROM CareVersion WHERE personId = :id") suspend fun deleteVersions(id: String)
    @Query("DELETE FROM CareItem WHERE personId = :id") suspend fun deleteItems(id: String)
    @Query("DELETE FROM Person") suspend fun clearPeople()
    @Query("DELETE FROM CareItem") suspend fun clearItems()
    @Query("DELETE FROM CareVersion") suspend fun clearVersions()
    @Query("DELETE FROM Acknowledgement") suspend fun clearAcknowledgements()
    @Query("DELETE FROM Conversation WHERE personId NOT IN (SELECT id FROM Person)") suspend fun clearRemovedDrafts()
    @Insert suspend fun addPeople(people: List<Person>)
    @Insert suspend fun addItems(items: List<CareItem>)
    @Insert suspend fun addVersions(versions: List<CareVersion>)
    @Insert suspend fun addAcknowledgements(acknowledgements: List<Acknowledgement>)
}

val ReportsMigration = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS CareReport (id TEXT NOT NULL, personId TEXT NOT NULL, name TEXT NOT NULL, mime TEXT NOT NULL, fileName TEXT NOT NULL, bytes INTEGER NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_CareReport_personId ON CareReport (personId)")
    }
}

val AcknowledgementsMigration = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS Acknowledgement_new (versionId TEXT NOT NULL, contentHash TEXT NOT NULL, name TEXT NOT NULL, relationship TEXT NOT NULL, at INTEGER NOT NULL, id TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO Acknowledgement_new (versionId, contentHash, name, relationship, at, id) SELECT versionId, contentHash, name, relationship, at, versionId FROM Acknowledgement")
        db.execSQL("DROP TABLE Acknowledgement")
        db.execSQL("ALTER TABLE Acknowledgement_new RENAME TO Acknowledgement")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Acknowledgement_versionId ON Acknowledgement (versionId)")
    }
}

@Database(entities = [Person::class, Conversation::class, CareVersion::class, Acknowledgement::class, CareItem::class, CareReport::class], version = 4, exportSchema = true)
abstract class CareDatabase : RoomDatabase() {
    abstract fun care(): CareDao
    companion object {
        private val instances = mutableMapOf<String, CareDatabase>()
        fun get(context: Context, account: String = ""): CareDatabase = synchronized(this) {
            require(account.isEmpty() || runCatching { UUID.fromString(account) }.isSuccess)
            val filename = if (account.isEmpty()) "care.db" else "care-$account.db"
            instances.getOrPut(filename) { Room.databaseBuilder(context.applicationContext, CareDatabase::class.java, filename)
                .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE CareItem ADD COLUMN revision INTEGER NOT NULL DEFAULT 0") }
                }, ReportsMigration, AcknowledgementsMigration).build() }
        }
    }
}

object NoteRules {
    val labels = listOf("What matters", "Who was there", "What we discussed", "Still to discuss", "Next steps")
    fun values(d: Conversation) = listOf(d.priorities, d.participants, d.topics, d.questions, d.nextSteps)
    fun values(v: CareVersion) = listOf(v.priorities, v.participants, v.topics, v.questions, v.nextSteps)
    fun hash(parts: List<String>): String = MessageDigest.getInstance("SHA-256")
        .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun publish(person: Person, draft: Conversation, doctor: String, reviewed: Boolean, version: Int): CareVersion {
        require(person.id == draft.personId) { "This conversation belongs to a different person." }
        require(reviewed && doctor.trim().isNotEmpty()) { "Review the note and add the doctor's name." }
        require(draft.source.isNotBlank() && values(draft).any { it.isNotBlank() }) { "Add the conversation and review its sections." }
        require(draft.source.length <= 12000 && values(draft).all { it.length <= 4000 }) { "Shorten this conversation before saving." }
        val hash = hash(listOf(person.id, person.name, draft.source, doctor.trim()) + values(draft))
        return CareVersion(personId = person.id, version = version, personName = person.name, source = draft.source,
            priorities = draft.priorities, participants = draft.participants, topics = draft.topics, questions = draft.questions,
            nextSteps = draft.nextSteps, doctor = doctor.trim(), contentHash = hash)
    }
    fun acknowledge(version: CareVersion, latest: CareVersion?, name: String, relationship: String, reviewed: Boolean, existing: List<Acknowledgement> = emptyList()): Acknowledgement {
        require(latest?.id == version.id && latest.contentHash == version.contentHash) { "A newer note is available. Open it before confirming." }
        require(reviewed && name.trim().length in 1..100 && relationship in listOf("Patient", "Family / carer")) { "Read the note and add your name, up to 100 characters." }
        val matching = existing.filter { it.versionId == version.id && it.contentHash == version.contentHash }
        val patient = matching.firstOrNull { it.relationship == "Patient" }
        require(relationship != "Family / carer" || patient != null) { "The patient needs to confirm this version before family adds a confirmation." }
        matching.firstOrNull { it.relationship == relationship && it.name.trim().equals(name.trim(), ignoreCase = true) }?.let { return it }
        require(relationship != "Patient" || patient == null) { "The patient's confirmation is already saved for this version." }
        val id = if (relationship == "Patient" && existing.none { it.id == version.id }) version.id else newId()
        return Acknowledgement(version.id, version.contentHash, name.trim(), relationship, id = id)
    }
    // Explicit section headings only: keeps negation, uncertainty and the patient's words intact.
    fun organise(draft: Conversation): Conversation {
        val sections = Array(5) { StringBuilder() }; var current = 2
        draft.source.lines().forEach { line ->
            val found = labels.indexOfFirst { line.trim().startsWith("$it:", ignoreCase = true) }
            val text = if (found >= 0) { current = found; line.substringAfter(':').trim() } else line.trim()
            if (text.isNotBlank()) { if (sections[current].isNotEmpty()) sections[current].append('\n'); sections[current].append(text) }
        }
        return draft.copy(priorities = sections[0].toString(), participants = sections[1].toString(), topics = sections[2].toString(), questions = sections[3].toString(), nextSteps = sections[4].toString())
    }
    fun text(v: CareVersion, ack: Acknowledgement? = null): String = text(v, listOfNotNull(ack))
    fun text(v: CareVersion, acknowledgements: List<Acknowledgement>): String = buildString {
        appendLine("GOALS OF CARE • ${v.personName} • Version ${v.version}")
        appendLine("Reviewed by ${v.doctor} • ${java.time.Instant.ofEpochMilli(v.published)}")
        labels.zip(values(v)).forEach { (label, value) -> appendLine("\n$label\n${value.ifBlank { "Not recorded" }}") }
        appendLine("\nOriginal conversation\n${v.source}")
        acknowledgements.filter { it.versionId == v.id && it.contentHash == v.contentHash }
            .sortedWith(compareBy<Acknowledgement> { it.relationship != "Patient" }.thenBy { it.at }).forEach { ack ->
                appendLine("\nRead and confirmed by ${ack.name} (${ack.relationship}) • ${java.time.Instant.ofEpochMilli(ack.at)}")
            }
        appendLine("\nNames are self-entered. This is a record of a discussion, not a treatment order or legal directive.")
    }
}
