package `in`.wedevit.care

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Group writes never fall back to device storage when a shared request fails. */
class DoctorPrescriptionRepo(context: Context, private val namespace: String, private val cloud: CareCloud?, database: CareDatabase? = null) {
    private val db = database ?: CareDatabase.get(context.applicationContext, namespace)
    private val history=MedicineHistoryRepo(context,cloud,namespace)
    private fun identity() {
        if(cloud != null) require(namespace.isNotBlank() && cloud.session.value?.userId == namespace) { "The signed-in account changed. Reopen prescriptions." }
    }
    private suspend fun capability() {
        val value=JSONObject(cloud!!.request("POST","/rest/v1/rpc/healthathon_prescription_capabilities",JSONObject(),expectedAccountId=namespace))
        require(value.optInt("version")==1&&value.optBoolean("atomicReplacement")&&value.optBoolean("batchEdit")) { "This server does not support atomic prescription groups yet. Nothing was changed." }
    }
    private fun decode(personId:String,value:JSONObject):DoctorPrescriptionSnapshot {
        require(value.getInt("version")==1)
        val state=value.getJSONObject("state");require(state.getString("person_id")==personId)
        val groups=mutableListOf<DoctorPrescription>();val medicines=mutableListOf<DoctorPrescriptionMedicine>()
        val rows=value.getJSONArray("groups")
        for(i in 0 until rows.length()) {val r=rows.getJSONObject(i);require(r.getString("person_id")==personId);groups+=DoctorPrescription(r.getString("id"),personId,r.getString("title"),r.optString("prescribed_on").takeUnless {it=="null"}.orEmpty(),r.getLong("created_at"),r.getLong("ended_at"),r.optString("ended_on").takeUnless {it=="null"}.orEmpty(),r.optString("end_reason"),r.optString("replaces_id").takeUnless {it=="null"}.orEmpty(),r.getLong("revision"))}
        val members=value.getJSONArray("medicines")
        for(i in 0 until members.length()){val r=members.getJSONObject(i);require(r.getString("person_id")==personId&&groups.any {it.id==r.getString("prescription_id")});medicines+=DoctorPrescriptionMedicine(r.getString("prescription_id"),r.getString("medicine_id"),personId,r.getString("name"),r.getString("instructions"),r.getInt("position"),r.getLong("added_at"),r.getLong("removed_at"))}
        val pointer=state.optString("current_id").takeUnless {it=="null"}.orEmpty();require(pointer.isBlank()||groups.any {it.id==pointer})
        return DoctorPrescriptionSnapshot(DoctorPrescriptionState(personId,pointer,state.getLong("revision")),groups,medicines)
    }
    suspend fun load(personId: String): DoctorPrescriptionSnapshot = withContext(Dispatchers.IO) {
        identity()
        if(cloud != null) {
            capability();val value=JSONObject(cloud.request("POST","/rest/v1/rpc/healthathon_prescription_snapshot",JSONObject().put("p_person",personId),expectedAccountId=namespace));identity();return@withContext decode(personId,value)
        }
        db.withTransaction { read(personId) }
    }
    private suspend fun read(personId: String): DoctorPrescriptionSnapshot {
        val dao=db.prescriptions()
        return DoctorPrescriptionSnapshot(dao.state(personId) ?: DoctorPrescriptionState(personId),dao.groups(personId),dao.medicines(personId))
    }
    suspend fun save(request: SaveDoctorPrescription, stillAllowed: () -> Boolean): DoctorPrescriptionSnapshot = withContext(Dispatchers.IO) {
        identity();DoctorPrescriptionRules.validate(request)
        val payload=JSONObject().put("person",request.personId).put("group",request.groupId).put("stateRevision",request.expectedStateRevision)
            .put("groupRevision",request.expectedGroupRevision).put("replace",request.replace).put("confirmed",request.confirmedReplacement)
            .put("title",request.title.trim()).put("date",request.prescribedOn).put("legacy",JSONArray(request.legacyCurrentIds.sorted())).put("legacyFingerprint",request.legacyFingerprint)
            .put("medicines",JSONArray(request.medicines.map { JSONObject().put("id",it.id).put("name",it.name.trim()).put("instructions",it.instructions) })).toString()
        val hash=MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        if(cloud!=null) {
            require(stillAllowed()) { "This care profile or its edit access changed." };capability()
            val value=JSONObject(cloud.request("POST","/rest/v1/rpc/healthathon_save_prescription",JSONObject().put("p_request",JSONObject(payload).put("requestId",request.requestId)),expectedAccountId=namespace))
            identity();require(stillAllowed()) { "The prescription was saved, but your care profile changed. Reopen its medicines to refresh." }
            return@withContext decode(request.personId,value)
        }
        MedicineLocalCourseLock.forPerson(namespace,request.personId).withLock { db.withTransaction {
            require(stillAllowed()) { "This care profile or its edit access changed. Nothing was saved." }
            val dao=db.prescriptions();val care=db.care()
            dao.receipt(request.requestId)?.let { receipt ->
                require(receipt.personId==request.personId && receipt.payloadHash==hash) { "This save identifier belongs to different information." }
                return@withTransaction read(request.personId)
            }
            require(care.people().first().any { it.id==request.personId }) { "This care profile is no longer available." }
            val before=read(request.personId);val current=before.current
            require(before.state.revision==request.expectedStateRevision && (current?.revision ?: 0)==request.expectedGroupRevision) { "The prescription changed. Refresh before saving your reviewed list." }
            require(request.replace || current==null || current.id==request.groupId) { "Only the current prescription can be edited." }
            require(!request.replace || before.groups.none {it.id==request.groupId}) { "Choose a new prescription identifier." }
            val authoritative=care.items(request.personId).first().filter {it.kind=="medicine"&&it.id !in history.cached(request.personId).pastIds&&it.id !in before.excludedMedicineIds}
            require(authoritative.map {it.id}.toSet()==request.legacyCurrentIds.toSet()&&
                (authoritative.isEmpty()&&request.legacyFingerprint.isBlank()||DoctorPrescriptionRules.legacyFingerprint(authoritative)==request.legacyFingerprint)) { "The current medicine list changed. Reopen and review the complete list before saving." }
            val now=System.currentTimeMillis()
            val originalIds=authoritative.map {it.id}
            val newIds=request.medicines.map {it.id}.toSet()
            // Existing IDs may only be edited in their own current list, never copied from another person's record.
            request.medicines.forEach { input ->
                val existing=care.item(input.id)
                require(existing==null || (existing.personId==request.personId && existing.kind=="medicine" && input.id in originalIds && !request.replace)) { "A medicine identifier is not part of this current prescription." }
            }
            if(request.replace && current!=null) {
                // Freeze the reviewed last-active values, including edits made through
                // the Patient/Family medicine screen. Earlier removed rows stay intact.
                dao.putMedicines(before.medicines.filter {it.prescriptionId==current.id&&it.removedAt==0L&&it.medicineId in originalIds}.map {member ->
                    val item=authoritative.first {it.id==member.medicineId};member.copy(name=item.title,instructions=item.detail)
                })
                dao.putGroup(current.copy(endedAt=now,endReason="Reviewed last-active list saved at replacement",revision=current.revision+1))
            }
            val group=if(!request.replace && current!=null) current.copy(title=request.title.trim(),prescribedOn=request.prescribedOn,revision=current.revision+1)
                else DoctorPrescription(request.groupId,request.personId,request.title.trim(),request.prescribedOn,now)
            dao.putGroup(group)
            if(!request.replace && current!=null) dao.putMedicines(before.medicines.filter {it.prescriptionId==current.id && it.removedAt==0L && it.medicineId !in newIds}.map {it.copy(removedAt=now)})
            if(!request.replace) dao.putMedicines(originalIds.filterNot {it in newIds||before.medicines.any {m->m.prescriptionId==group.id&&m.medicineId==it}}.mapIndexed {position,id ->
                val item=requireNotNull(care.item(id));require(item.personId==request.personId&&item.kind=="medicine")
                DoctorPrescriptionMedicine(group.id,id,request.personId,item.title,item.detail,position,now,removedAt=now)
            })
            // Legacy list receives an honest date-unknown archive, never an invented prescription date.
            val ungrouped=originalIds.filter {id->before.medicines.none {it.medicineId==id}}
            if(request.replace && ungrouped.isNotEmpty()) {
                val legacyId=java.util.UUID.nameUUIDFromBytes((request.requestId+":legacy").toByteArray()).toString()
                dao.putGroup(DoctorPrescription(legacyId,request.personId,"Earlier ungrouped medicines",createdAt=now,endedAt=now,endReason="Replaced after confirmation; original prescription date not recorded"))
                dao.putMedicines(ungrouped.mapIndexed { position,id -> val item=requireNotNull(care.item(id));require(item.personId==request.personId&&item.kind=="medicine");DoctorPrescriptionMedicine(legacyId,id,request.personId,item.title,item.detail,position,now) })
            }
            request.medicines.forEach { input ->
                val existing=care.item(input.id)
                care.saveItem(existing?.copy(title=input.name.trim(),detail=input.instructions,revision=existing.revision+1)
                    ?: CareItem(input.id,request.personId,"medicine",input.name.trim(),input.instructions,created=now))
            }
            dao.putMedicines(request.medicines.mapIndexed { position,input ->
                val previous=before.medicines.firstOrNull {it.prescriptionId==group.id&&it.medicineId==input.id}
                DoctorPrescriptionMedicine(group.id,input.id,request.personId,input.name.trim(),input.instructions,position,previous?.addedAt ?: now)
            })
            val state=DoctorPrescriptionState(request.personId,group.id,before.state.revision+1);dao.putState(state)
            dao.insertReceipt(DoctorPrescriptionMutation(request.requestId,request.personId,group.id,if(request.replace)"replace" else "edit",hash,payload,now,state.revision,group.revision))
            require(stillAllowed()) { "This care profile or its edit access changed. Nothing was saved." }
            read(request.personId)
        } }
    }
}
