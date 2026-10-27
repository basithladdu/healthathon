package `in`.wedevit.care

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal object MedicineLocalCourseLock {
    private val locks=mutableMapOf<String,Mutex>()
    fun forPerson(namespace:String,person:String)=synchronized(locks){locks.getOrPut("$namespace:$person"){Mutex()}}
}

/** Real care records only. No access to the independent synthetic doctor-dose tables. */
class MedicineHistoryRepo(context: Context, private val cloud: CareCloud?, private val accountId: String) {
    private val app = context.applicationContext
    private val sync get() = cloud?.let { CareSyncQueue.get(app, it, accountId) }
    private fun prefs(person: String): android.content.SharedPreferences {
        require(person.isNotBlank())
        val key = MessageDigest.getInstance("SHA-256").digest("$accountId:$person".toByteArray()).joinToString("") { "%02x".format(it) }
        return app.getSharedPreferences("medicine-history-$key", Context.MODE_PRIVATE)
    }
    private fun identity() { if (cloud != null) require(accountId.isNotBlank() && cloud.session.value?.userId == accountId) { "The signed-in account changed. Reopen this person's medicines." } }
    private suspend fun rows(table: String, personId: String, order: String): JSONArray {
        UUID.fromString(personId)
        val all=JSONArray(); var offset=0
        do { val page=JSONArray(cloud!!.request("GET","/rest/v1/$table?person_id=eq.$personId&order=$order&limit=500&offset=$offset",expectedAccountId=accountId))
            for(i in 0 until page.length()) all.put(page.getJSONObject(i)); offset += page.length()
            if(page.length()<500) break
        } while(true)
        return all
    }
    private fun readArray(rows: JSONArray, block: (JSONObject) -> Unit) { for(i in 0 until rows.length()) block(rows.getJSONObject(i)) }
    private fun snapshot(courses: JSONArray, events: JSONArray): MedicineHistorySnapshot {
        val c = mutableListOf<MedicineCourse>(); val e = mutableListOf<MedicineIntakeEvent>()
        fun time(row: JSONObject, key: String): Long? = if(row.isNull(key) || !row.has(key)) null else Instant.parse(row.getString(key)).toEpochMilli()
        readArray(courses) { c += MedicineCourse(it.getString("medicine_id"), it.getString("medicine_name"), it.optString("directions"), it.getBoolean("past"), time(it,"changed_at")!!, it.optString("reason"), it.optLong("revision",0)) }
        readArray(events) { e += MedicineIntakeEvent(it.getString("id"), it.getString("medicine_id"), it.getString("medicine_name"), it.getString("outcome"), time(it,"planned_at"), time(it,"reported_taken_at"), it.optString("reported_date").takeUnless { value -> value == "null" }.orEmpty(), time(it,"logged_at")!!, it.optString("reporter_label"), it.optString("note"), it.optString("source","manual")) }
        return MedicineHistorySnapshot(c.distinctBy {it.medicineId},e.distinctBy {it.id}.sortedByDescending { it.loggedAt })
    }
    fun cached(personId: String): MedicineHistorySnapshot {
        val p = prefs(personId)
        return snapshot(JSONArray(p.getString("courses","[]")), JSONArray(p.getString("events","[]")))
    }
    suspend fun load(personId: String): MedicineHistorySnapshot = withContext(Dispatchers.IO) {
        identity()
        if(cloud == null) return@withContext cached(personId)
        val courses = rows("healthathon_medicine_courses",personId,"changed_at.desc,medicine_id.asc")
        val serverEvents = rows("healthathon_medicine_events",personId,"logged_at.desc,id.asc")
        readArray(courses) { require(it.getString("person_id")==personId) { "The returned medicine course belongs to a different care profile." } }
        readArray(serverEvents) { require(it.getString("person_id")==personId) { "The returned intake report belongs to a different care profile." } }
        identity()
        // Reports still waiting to send stay visible until the server has them.
        val events = CareSyncOps.overlayEvents(serverEvents, sync!!.pending(personId))
        snapshot(courses,events).also { check(prefs(personId).edit().putString("courses",courses.toString()).putString("events",events.toString()).commit()) { "Could not cache medicine history on this device." } }
    }
    suspend fun setPast(personId: String, item: CareItem, past: Boolean, reason: String, expectedRevision: Long = cached(personId).courses.firstOrNull {it.medicineId==item.id}?.revision ?: 0): MedicineHistorySnapshot = withContext(Dispatchers.IO) {
        identity(); require(item.personId == personId && item.kind == "medicine" && reason.length <= 1000)
        if(cloud != null) {
            cloud.request("POST", "/rest/v1/rpc/healthathon_set_medicine_course", JSONObject().put("p_person",personId).put("p_medicine",item.id).put("p_past",past).put("p_reason",reason).put("p_expected_revision",expectedRevision), expectedAccountId=accountId)
            return@withContext load(personId)
        }
        MedicineLocalCourseLock.forPerson(accountId,personId).withLock {
        val now = System.currentTimeMillis(); val p=prefs(personId); val rows=JSONArray(p.getString("courses","[]")); val kept=JSONArray()
        val previous=cached(personId).courses.firstOrNull {it.medicineId==item.id}
        if(previous!=null&&previous.revision==expectedRevision+1&&previous.past==past&&previous.reason==reason)return@withContext cached(personId)
        require((previous?.revision ?: 0)==expectedRevision) { "This medicine changed elsewhere. Refresh before changing its course." }
        for(i in 0 until rows.length()) if(rows.getJSONObject(i).getString("medicine_id") != item.id) kept.put(rows.getJSONObject(i))
        kept.put(JSONObject().put("medicine_id",item.id).put("medicine_name",item.title).put("directions",item.detail).put("past",past).put("changed_at",medicineInstant(now)).put("reason",reason).put("revision",expectedRevision+1))
        val events=JSONArray(p.getString("events","[]")); events.put(JSONObject().put("id",UUID.randomUUID().toString()).put("medicine_id",item.id).put("medicine_name",item.title)
            .put("outcome",if(past) "course_past" else "course_current").put("logged_at",medicineInstant(now)).put("reporter_label","Reporter not recorded").put("note",reason).put("source","course-change"))
        check(p.edit().putString("courses",kept.toString()).putString("events",events.toString()).commit()) { "Could not save this medicine's status. Nothing was deleted." }
        cached(personId)
        }
    }
    suspend fun append(personId: String, event: MedicineIntakeEvent): MedicineHistorySnapshot = withContext(Dispatchers.IO) {
        identity(); MedicineHistoryRules.validate(event,System.currentTimeMillis())
        val row = JSONObject().put("id",event.id).put("person_id",personId).put("medicine_id",event.medicineId).put("medicine_name",event.name).put("outcome",event.outcome)
            .put("planned_at",event.plannedAt?.let(::medicineInstant) ?: JSONObject.NULL).put("reported_taken_at",event.reportedTakenAt?.let(::medicineInstant) ?: JSONObject.NULL)
            .put("reported_date",event.reportedDate.ifBlank { null } ?: JSONObject.NULL).put("reporter_label",event.reporter).put("note",event.note).put("source",event.source)
        if(cloud != null) {
            // Kept on the phone first and sent in order; the report id makes a repeated send return the first one.
            val status = sync!!.submit(CareSyncOps.medicineEvent(personId, event.medicineId, row))
            if(status == CareSyncStatus.SENT) try { return@withContext load(personId) } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e }
            val p=prefs(personId)
            val events=CareSyncOps.overlayEvents(JSONArray(p.getString("events","[]")), sync!!.pending(personId))
            check(p.edit().putString("events",events.toString()).commit()) { "Could not save the intake report." }
            return@withContext cached(personId)
        }
        row.put("logged_at",medicineInstant(event.loggedAt))
        val p=prefs(personId);val events=JSONArray(p.getString("events","[]"))
        for(i in 0 until events.length()) if(events.getJSONObject(i).getString("id")==event.id) {
            val old=cached(personId).events.first {it.id==event.id}; require(old.copy(loggedAt=event.loggedAt)==event) { "This report identifier already belongs to different information." }
            return@withContext cached(personId)
        }
        events.put(row);check(p.edit().putString("events",events.toString()).commit()) { "Could not save the intake report." };cached(personId)
    }

    /** Walkthrough seeding only: that dataset is rebuilt from scratch, so its stale local events are dropped
     *  instead of colliding with reused identifiers. Signed-in accounts keep their append-only history. */
    internal fun resetSeededPerson(personId: String) {
        require(cloud == null) { "Only local walkthrough history may be reset." }
        check(prefs(personId).edit().remove("events").commit()) { "Could not reset the walkthrough intake history." }
    }
}

/** Call only after a successful device-local checkbox save. It never invents a taken time. */
internal suspend fun appendLocalMedicineCheckoff(context: Context, accountId: String, item: CareItem, beforeDate: String, afterDate: String, loggedAt: Long = System.currentTimeMillis()) {
    if(item.kind != "medicine" || beforeDate == afterDate) return
    MedicineHistoryRepo(context,null,accountId).append(item.personId,MedicineIntakeEvent(UUID.randomUUID().toString(),item.id,item.title,
        if(afterDate.isBlank()) "unmarked" else "taken", reportedDate=afterDate.ifBlank { beforeDate }, loggedAt=loggedAt,
        reporter="Reporter not recorded",note=if(afterDate.isBlank()) "Daily check-off removed; earlier report retained." else "Daily check-off. Exact taken time was not recorded.",source="daily-checkoff"))
}
