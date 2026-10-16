package `in`.wedevit.care

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Never accesses real accounts, Room, recordings, reports, or any network service. */
class DoctorDemoStore(
    context: Context,
    private val now: () -> Long = { System.currentTimeMillis() },
    preferencesName: String = "doctor-demo-v1",
) {
    init { require(preferencesName.startsWith("doctor-demo-")) { "Use an isolated doctor-demo preference namespace." } }
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    val patients: List<DoctorDemoPatient> get() = DoctorDemoData.patients

    @Synchronized fun appointments(): List<DoctorDemoAppointment> {
        val saved = preferences.getString("appointments", null)
        if (saved == null) {
            val seeded = DoctorDemoData.seedAppointments(now())
            persist(seeded)
            return seeded.sortedBy { it.startMillis }
        }
        return runCatching {
            val rows = JSONArray(saved)
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                DoctorDemoAppointment(row.getString("id"), row.getString("patientId"), row.getLong("startMillis"),
                    row.getInt("durationMinutes"), row.getString("type"), row.getString("status"), row.optString("note"))
            }.sortedBy { it.startMillis }
        }.getOrDefault(emptyList())
    }

    @Synchronized fun save(appointment: DoctorDemoAppointment): String? {
        val current = appointments()
        current.firstOrNull { it.id == appointment.id }?.let { previous ->
            DoctorDemoData.validateStatusChange(previous.status, appointment.status)?.let { return it }
        }
        DoctorDemoData.validateAppointment(appointment, current, now())?.let { return it }
        return if (persist(current.filterNot { it.id == appointment.id } + appointment)) null else "Couldn't save the demo appointment. Try again."
    }

    @Synchronized fun cancel(id: String): String? = updateStatus(id, "Cancelled")
    @Synchronized fun complete(id: String): String? = updateStatus(id, "Completed")

    private fun updateStatus(id: String, status: String): String? {
        val current = appointments()
        val selected = current.firstOrNull { it.id == id } ?: return "Demo appointment not found."
        DoctorDemoData.validateStatusChange(selected.status, status)?.let { return it }
        return if (persist(current.map { if (it.id == id) it.copy(status = status) else it })) null else "Couldn't update the demo appointment. Try again."
    }

    private fun persist(values: List<DoctorDemoAppointment>): Boolean {
        val rows = JSONArray()
        values.forEach { value -> rows.put(JSONObject().put("id", value.id).put("patientId", value.patientId)
            .put("startMillis", value.startMillis).put("durationMinutes", value.durationMinutes)
            .put("type", value.type).put("status", value.status).put("note", value.note)) }
        return preferences.edit().putString("appointments", rows.toString()).commit()
    }
}
