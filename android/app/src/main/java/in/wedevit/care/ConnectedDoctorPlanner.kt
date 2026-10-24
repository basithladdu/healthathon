package `in`.wedevit.care

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Deliberately local, account-scoped simulation. Never reads or modifies the old walkthrough. */
internal class ConnectedDoctorPlanner(context: Context, accountId: String) {
    private val scope = MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray()).joinToString("") { "%02x".format(it) }
    private val preferences = context.getSharedPreferences("doctor-connected-test-planner-$scope", Context.MODE_PRIVATE)
    fun visits(): List<DoctorDemoAppointment> {
        val rows = JSONArray(preferences.getString("visits", "[]"))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> DoctorDemoAppointment(row.getString("id"), row.getString("patient"), row.getLong("at"), row.getInt("duration"), row.getString("type"), row.getString("status"), row.getString("note")) } }
    }
    fun requests(): String = preferences.getString("requests", "{}")!!.also { JSONObject(it) }
    fun draft(patientId: String): String? = preferences.getString("draft-$patientId", null)
    fun saveDraft(patientId: String, value: String) {
        JSONObject(value)
        check(preferences.edit().putString("draft-$patientId", value).commit()) { "Your local test draft could not be saved. Keep this screen open and try again." }
    }
    fun save(visits: List<DoctorDemoAppointment>, requests: String) {
        JSONObject(requests)
        val rows = JSONArray().also { array -> visits.forEach { array.put(JSONObject().put("id",it.id).put("patient",it.patientId).put("at",it.startMillis).put("duration",it.durationMinutes).put("type",it.type).put("status",it.status).put("note",it.note)) } }
        check(preferences.edit().putString("visits",rows.toString()).putString("requests",requests).commit()) { "Local test planner could not be saved. Keep this screen open and try again." }
    }
}
