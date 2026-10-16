package `in`.wedevit.care

data class DoctorDemoPatient(
    val id: String, val name: String, val age: Int, val careContext: String,
    val summary: String, val careNotes: List<String>, val medicines: List<String>,
    val reportSummaries: List<String>, val observations: List<String>,
)

data class DoctorDemoAppointment(
    val id: String, val patientId: String, val startMillis: Long,
    val durationMinutes: Int, val type: String, val status: String = "Scheduled", val note: String = "",
) {
    val endMillis: Long get() = startMillis + durationMinutes.toLong() * 60_000L
}

/** Entirely fictional, isolated UI fixtures. Nothing here is a clinical instruction. */
object DoctorDemoData {
    const val doctorName = "Dr Meera (fictional)"
    const val disclaimer = "Fictional demo only · identity not verified · not a clinical record"
    val appointmentTypes = listOf("In person", "Phone", "Video")
    val statuses = listOf("Scheduled", "Completed", "Cancelled")
    val patients = listOf(
        DoctorDemoPatient("demo-asha", "Asha Demo", 54, "Cancer support · care planning",
            "Wants visits to fit around time with family. Questions are being gathered for the next conversation.",
            listOf("Fictional discussion note: family time matters most.", "Follow-up topic: clarify who should attend the next conversation."),
            listOf("Fictional medicine list awaiting reconciliation; no prescription recorded."),
            listOf("Sample clinic letter: follow-up discussion requested. No real report attached."),
            listOf("Demo check-in: preferred appointment time is afternoon.", "Family would like a written summary.")),
        DoctorDemoPatient("demo-ravi", "Ravi Demo", 67, "Supportive care · family coordination",
            "Family members are organizing visits and practical help. Patient would like one clear contact person.",
            listOf("Fictional discussion note: keep explanations simple.", "Next conversation: ask who the patient wants involved."),
            listOf("Sample medication-review task; no medicine or dose has been prescribed."),
            listOf("Sample discharge-summary placeholder: review questions with the care team."),
            listOf("Demo family update: transport arrangements are being discussed.")),
        DoctorDemoPatient("demo-leela", "Leela Demo", 43, "Cancer support · visit preparation",
            "Preparing questions for a future review and collecting copies of reports.",
            listOf("Fictional draft: discuss what information the patient would like to receive."),
            listOf("No fictional medicines entered."),
            listOf("Sample laboratory-report summary: demonstration text only, no clinical results."),
            listOf("Demo check-in: would prefer a phone conversation for scheduling.")),
        DoctorDemoPatient("demo-imran", "Imran Demo", 61, "Palliative support · care preferences",
            "Would like the care team to understand everyday priorities before discussing future plans.",
            listOf("Fictional discussion note: maintaining familiar routines matters.", "Unresolved topic: who should receive copies of the care note."),
            listOf("Fictional medicine list not reviewed; no treatment directions supplied."),
            listOf("Sample imaging-report placeholder: no image or diagnostic interpretation included."),
            listOf("Demo observation: requested a longer visit to ask questions.")),
        DoctorDemoPatient("demo-maya", "Maya Demo", 72, "Supportive care · caregiver handover",
            "Two fictional caregivers want a shared checklist and a clear record of the last discussion.",
            listOf("Fictional discussion note: patient wants to be included in updates."),
            listOf("Sample reconciliation reminder only; no prescribing information."),
            listOf("Sample follow-up letter: demonstration of a document summary."),
            listOf("Demo family update: one caregiver will attend the next visit.")),
        DoctorDemoPatient("demo-joseph", "Joseph Demo", 58, "Cancer support · follow-up",
            "Reviewing the previous fictional conversation and preparing the next set of questions.",
            listOf("Fictional discussion note: patient asked for time to consider options.", "No preference or treatment decision should be inferred."),
            listOf("No fictional medicines entered."),
            listOf("Sample consultation note: no original document attached."),
            listOf("Demo check-in: wishes to review wording before sharing a note.")),
    )

    fun seedAppointments(nowMillis: Long): List<DoctorDemoAppointment> {
        val hour = 3_600_000L
        val nextHour = (nowMillis / hour + 1) * hour
        return listOf(
            DoctorDemoAppointment("demo-visit-1", patients[0].id, nextHour, 30, "In person", note = "Fictional care-planning conversation"),
            DoctorDemoAppointment("demo-visit-2", patients[1].id, nextHour + hour, 20, "Phone", note = "Demo only; no call is placed"),
            DoctorDemoAppointment("demo-visit-3", patients[2].id, nextHour + 24 * hour, 30, "Video", note = "Demo only; no video link is created"),
            DoctorDemoAppointment("demo-visit-4", patients[3].id, nextHour + 25 * hour, 45, "In person"),
            DoctorDemoAppointment("demo-visit-5", patients[4].id, nextHour + 48 * hour, 30, "Phone"),
            DoctorDemoAppointment("demo-visit-6", patients[5].id, nowMillis - 24 * hour, 30, "In person", "Completed", "Fictional previous review"),
        )
    }

    fun validateAppointment(value: DoctorDemoAppointment, existing: List<DoctorDemoAppointment>, nowMillis: Long): String? {
        if (value.id.isBlank()) return "An appointment ID is required."
        if (patients.none { it.id == value.patientId }) return "Choose a fictional demo patient."
        if (value.durationMinutes !in 1..480) return "Choose a duration between 1 and 480 minutes."
        if (value.type !in appointmentTypes) return "Choose In person, Phone or Video."
        if (value.status !in statuses) return "Choose a valid appointment status."
        if (value.note.length > 1000) return "Keep the appointment note under 1,000 characters."
        if (value.startMillis <= 0 || value.startMillis > Long.MAX_VALUE - value.durationMinutes.toLong() * 60_000L) return "Choose a valid appointment time."
        if (value.status == "Scheduled" && value.startMillis <= nowMillis) return "Choose a future appointment time."
        if (value.status != "Cancelled" && existing.any {
                it.id != value.id && it.status != "Cancelled" &&
                    value.startMillis < it.endMillis && it.startMillis < value.endMillis
            }) return "This overlaps another appointment for this demo doctor."
        return null
    }

    fun validateStatusChange(current: String, next: String): String? = when {
        current !in statuses || next !in statuses -> "Choose a valid appointment status."
        current == "Cancelled" && next != "Cancelled" -> "A cancelled appointment cannot be reopened or completed."
        current == "Completed" && next != "Completed" -> "A completed appointment cannot be reopened or cancelled."
        else -> null
    }
}
