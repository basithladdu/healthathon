package `in`.wedevit.care

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

/** Prescription dates are supplied facts; zero/blank means not recorded, never inferred. */
@Entity(indices = [Index("personId")])
data class DoctorPrescription(
    @PrimaryKey val id: String, val personId: String, val title: String,
    val prescribedOn: String = "", val createdAt: Long,
    val endedAt: Long = 0, val endedOn: String = "", val endReason: String = "",
    val replacesId: String = "", val revision: Long = 1,
)

/** One CAS pointer per person; the pointed-to group is current, all others are past. */
@Entity data class DoctorPrescriptionState(
    @PrimaryKey val personId: String, val currentId: String = "", val revision: Long = 0,
)

/** Names/instructions are retained even if an underlying medicine is later edited. */
@Entity(primaryKeys = ["prescriptionId", "medicineId"], indices = [Index("personId")])
data class DoctorPrescriptionMedicine(
    val prescriptionId: String, val medicineId: String, val personId: String,
    val name: String, val instructions: String, val position: Int,
    val addedAt: Long, val removedAt: Long = 0,
)

/** Immutable mutation receipt doubles as membership audit and exact retry protection. */
@Entity(indices = [Index("personId")])
data class DoctorPrescriptionMutation(
    @PrimaryKey val requestId: String, val personId: String, val prescriptionId: String,
    val operation: String, val payloadHash: String, val payloadJson: String,
    val recordedAt: Long, val resultingStateRevision: Long, val resultingGroupRevision: Long,
)

@Dao interface DoctorPrescriptionDao {
    @Query("SELECT * FROM DoctorPrescriptionState WHERE personId=:personId") suspend fun state(personId: String): DoctorPrescriptionState?
    @Query("SELECT * FROM DoctorPrescription WHERE personId=:personId ORDER BY createdAt DESC,id") suspend fun groups(personId: String): List<DoctorPrescription>
    @Query("SELECT * FROM DoctorPrescriptionMedicine WHERE personId=:personId ORDER BY position,medicineId") suspend fun medicines(personId: String): List<DoctorPrescriptionMedicine>
    @Query("SELECT * FROM DoctorPrescriptionMutation WHERE requestId=:requestId") suspend fun receipt(requestId: String): DoctorPrescriptionMutation?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putState(value: DoctorPrescriptionState)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGroup(value: DoctorPrescription)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMedicines(values: List<DoctorPrescriptionMedicine>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReceipt(value: DoctorPrescriptionMutation)
    @Query("SELECT DISTINCT m.medicineId FROM DoctorPrescriptionMedicine m LEFT JOIN DoctorPrescriptionState s ON s.personId=m.personId WHERE m.personId=:personId AND NOT EXISTS (SELECT 1 FROM DoctorPrescriptionMedicine active WHERE active.personId=m.personId AND active.prescriptionId=s.currentId AND active.medicineId=m.medicineId AND active.removedAt=0)") fun excludedIds(personId: String): kotlinx.coroutines.flow.Flow<List<String>>
    @Query("DELETE FROM DoctorPrescriptionMedicine WHERE personId=:personId") suspend fun purgeMedicines(personId: String)
    @Query("DELETE FROM DoctorPrescriptionMutation WHERE personId=:personId") suspend fun purgeMutations(personId: String)
    @Query("DELETE FROM DoctorPrescriptionState WHERE personId=:personId") suspend fun purgeState(personId: String)
    @Query("DELETE FROM DoctorPrescription WHERE personId=:personId") suspend fun purgeGroups(personId: String)
    @Transaction suspend fun purgePerson(personId: String) { purgeMedicines(personId);purgeMutations(personId);purgeState(personId);purgeGroups(personId) }
}

/** Called only by the combined additive Room migration; never opens or resets a database. */
object DoctorPrescriptionSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS DoctorPrescription (id TEXT NOT NULL, personId TEXT NOT NULL, title TEXT NOT NULL, prescribedOn TEXT NOT NULL, createdAt INTEGER NOT NULL, endedAt INTEGER NOT NULL, endedOn TEXT NOT NULL, endReason TEXT NOT NULL, replacesId TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DoctorPrescription_personId ON DoctorPrescription(personId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS DoctorPrescriptionState (personId TEXT NOT NULL, currentId TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(personId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS DoctorPrescriptionMedicine (prescriptionId TEXT NOT NULL, medicineId TEXT NOT NULL, personId TEXT NOT NULL, name TEXT NOT NULL, instructions TEXT NOT NULL, position INTEGER NOT NULL, addedAt INTEGER NOT NULL, removedAt INTEGER NOT NULL, PRIMARY KEY(prescriptionId,medicineId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DoctorPrescriptionMedicine_personId ON DoctorPrescriptionMedicine(personId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS DoctorPrescriptionMutation (requestId TEXT NOT NULL, personId TEXT NOT NULL, prescriptionId TEXT NOT NULL, operation TEXT NOT NULL, payloadHash TEXT NOT NULL, payloadJson TEXT NOT NULL, recordedAt INTEGER NOT NULL, resultingStateRevision INTEGER NOT NULL, resultingGroupRevision INTEGER NOT NULL, PRIMARY KEY(requestId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DoctorPrescriptionMutation_personId ON DoctorPrescriptionMutation(personId)")
    }
}

data class PrescriptionMedicineInput(val id: String, val name: String, val instructions: String)
data class DoctorPrescriptionSnapshot(val state: DoctorPrescriptionState, val groups: List<DoctorPrescription>, val medicines: List<DoctorPrescriptionMedicine>) {
    val current get() = groups.firstOrNull { it.id == state.currentId }
    fun members(groupId: String) = medicines.filter { it.prescriptionId == groupId && it.removedAt == 0L }.sortedBy { it.position }
    val excludedMedicineIds: Set<String> get() {
        val currentIds = members(state.currentId).map { it.medicineId }.toSet()
        return medicines.map { it.medicineId }.toSet() - currentIds
    }
}
data class SaveDoctorPrescription(
    val requestId: String, val personId: String, val groupId: String,
    val expectedStateRevision: Long, val expectedGroupRevision: Long,
    val replace: Boolean, val confirmedReplacement: Boolean,
    val title: String, val prescribedOn: String,
    val medicines: List<PrescriptionMedicineInput>,
    val legacyCurrentIds: List<String> = emptyList(),
    val legacyFingerprint: String = "",
)

object DoctorPrescriptionRules {
    fun legacyFingerprint(items: List<CareItem>): String {
        val content=items.sortedBy {it.id}.joinToString("") {item ->listOf(item.id,item.revision.toString(),item.title,item.detail,item.doneOn,item.created.toString()).joinToString(""){"${it.toByteArray(Charsets.UTF_8).size}:$it"}}
        return java.security.MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
    fun validate(request: SaveDoctorPrescription) {
        java.util.UUID.fromString(request.requestId); java.util.UUID.fromString(request.personId); java.util.UUID.fromString(request.groupId)
        require(request.title.trim().length in 1..200 && request.expectedStateRevision >= 0 && request.expectedGroupRevision >= 0)
        require(request.prescribedOn.isBlank() || runCatching { java.time.LocalDate.parse(request.prescribedOn) }.isSuccess) { "Enter a prescription date as YYYY-MM-DD, or leave it blank." }
        require(request.medicines.size in 1..50) { "Add between one and fifty reviewed medicines." }
        require(request.medicines.map { it.id }.distinct().size == request.medicines.size)
        request.medicines.forEach { java.util.UUID.fromString(it.id); require(it.name.trim().length in 1..200 && it.instructions.length <= 4000) { "Check the medicine name and instructions." } }
        request.legacyCurrentIds.forEach { java.util.UUID.fromString(it) }
        require(request.legacyCurrentIds.distinct().size==request.legacyCurrentIds.size)
        require(request.legacyFingerprint.isBlank()||request.legacyFingerprint.matches(Regex("[a-f0-9]{64}")))
        require(!request.replace || request.confirmedReplacement) { "Confirm replacement before ending the previous prescription." }
    }
}
