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

/** Isolated walkthrough fixtures. Nothing here is a clinical instruction. */
object DoctorDemoData {
    const val doctorName = "Dr Meera Nair"
    /** Practice only: an obviously made-up number so practice forms come prefilled. Never a real registration. */
    const val registrationNo = "PRACTICE-DEMO"
    const val disclaimer = "Identity not verified · not a clinical record"
    val appointmentTypes = listOf("In person", "Phone", "Video")
    val statuses = listOf("Scheduled", "Completed", "Cancelled")
    val patients = listOf(
        DoctorDemoPatient("demo-asha", "Asha Menon", 54, "Metastatic breast cancer · bone pain and family planning",
            "Lives with breast cancer that has spread to the spine and ribs. Pain is the main concern, worst on turning in bed. Wants visits to fit around time with her daughters and has questions to bring to the next conversation.",
            listOf("Discussion: family time matters most; she would rather have a calm evening at home than a long trip to hospital.", "Follow-up topic: agree who should attend the next conversation and what she wants her daughters to hear first."),
            listOf("Morphine sulfate modified-release 20 mg, oral, every 12 hours, for background bone pain", "Morphine sulfate oral solution 5 mg, oral, every 4 hours as needed, for breakthrough pain", "Paracetamol 1 g, oral, four times daily", "Senna 15 mg, oral, at night, while taking morphine", "Denosumab 120 mg, subcutaneous injection, every 4 weeks"),
            listOf("Clinic letter: seen in oncology review. Pain in the thoracic spine and ribs has increased over three weeks and is controlled with regular modified-release morphine. Palliative radiotherapy to the spine was discussed and Asha asked for time to decide. A family discussion is requested."),
            listOf("Check-in: pain woke her twice last night, settled with a breakthrough dose. Prefers afternoon appointments.", "Daughters would like a written summary of what was agreed at the last visit.")),
        DoctorDemoPatient("demo-ravi", "Ravi Kulkarni", 67, "Advanced COPD · breathlessness at home",
            "Severe COPD with breathlessness walking between rooms and frequent night-time waking. Needs one clear contact person; his son and daughter-in-law share the practical help.",
            listOf("Discussion: keep explanations simple and short; he tires quickly in longer conversations.", "Next conversation: ask who he wants involved in decisions about future hospital admissions."),
            listOf("Tiotropium 18 microgram inhaler, one capsule inhaled each morning", "Salbutamol 100 microgram inhaler, 2 puffs as needed for wheeze", "Morphine oral solution 2.5 mg, oral, every 4 hours as needed, for breathlessness at rest", "Prednisolone 30 mg, oral, each morning for 5 days (current course, finishing at review)", "Home oxygen 2 litres per minute via nasal cannula for at least 15 hours a day"),
            listOf("Discharge summary: admitted for four days with an exacerbation of COPD, treated with steroids and antibiotics, and discharged home on oxygen. Advised review with the community team within one week; breathlessness plan and questions to be reviewed with the care team."),
            listOf("Family update: his son is arranging transport to the chest clinic and a neighbour will help on weekdays.", "Check-in: breathless on the stairs and used the oxygen overnight. Slept better propped on three pillows.")),
        DoctorDemoPatient("demo-leela", "Leela Pillai", 43, "Advanced bowel cancer · nausea and appetite loss",
            "Living with bowel cancer that has spread to the liver. Nausea in the mornings and poor appetite are limiting her day. She is preparing questions for her next review and collecting copies of recent reports.",
            listOf("Discussion: she would like to know what to expect over the coming months, explained at her own pace.", "Draft topic: discuss what information she wants to receive and who may hear it."),
            listOf("Ondansetron 4 mg, oral, twice daily, for nausea", "Dexamethasone 4 mg, oral, each morning with food, for nausea and appetite", "Metoclopramide 10 mg, oral, three times daily before meals if nausea persists", "Paracetamol 1 g, oral, four times daily for abdominal discomfort", "Lactulose 15 ml, oral, at night"),
            listOf("Laboratory report: haemoglobin 9.8 g/dL (printed range 12.0-15.5), albumin 31 g/L (printed range 35-50), ALT 88 U/L (printed range 7-35). Values and printed ranges only; no interpretation is given here."),
            listOf("Check-in: nausea settled by midday, ate half a bowl of rice and some soup. Would prefer a phone call for scheduling.")),
        DoctorDemoPatient("demo-imran", "Imran Qureshi", 61, "Prostate cancer · pain and care preferences",
            "Advanced prostate cancer with bone metastases and pain in the hips. Would like the care team to understand his everyday priorities and routines before discussing future plans.",
            listOf("Discussion: maintaining familiar routines, including his morning walk to the garden, matters to him.", "Unresolved topic: who should receive copies of the care note."),
            listOf("Tamsulosin 400 microgram, oral, each evening", "Bicalutamide 50 mg, oral, once daily", "Tramadol 50 mg, oral, up to four times daily as needed for hip pain", "Dexamethasone 2 mg, oral, each morning for 7 days", "Senna 15 mg, oral, at night"),
            listOf("Imaging report: bone scan reports multiple foci of increased uptake in the pelvis, lumbar spine and right femur, consistent with metastatic disease as printed by the reporting radiologist. No further interpretation is added here."),
            listOf("Observation: hip pain eased after a short walk and a dose of tramadol. Asked for a longer visit so he can ask all his questions.")),
        DoctorDemoPatient("demo-maya", "Maya Iyer", 72, "Advanced heart failure · caregiver handover",
            "End-stage heart failure with ankle swelling and breathlessness lying flat. Two caregivers, her son and a paid helper, want a shared checklist and a clear record of the last discussion.",
            listOf("Discussion: she wants to be included in every update and does not want decisions made without her.", "Caregiver handover: record daily weight each morning and tell the team of a gain of 2 kg in 3 days."),
            listOf("Furosemide 40 mg, oral, each morning", "Spironolactone 25 mg, oral, once daily", "Bisoprolol 2.5 mg, oral, once daily", "Ramipril 2.5 mg, oral, at night", "Glyceryl trinitrate 400 microgram sublingual spray, as needed for chest tightness"),
            listOf("Follow-up letter: seen in the heart failure clinic. Ankle swelling is slightly less after the diuretic was increased. Weights, fluid advice and the plan for who to call out of hours were reviewed with her son."),
            listOf("Family update: her son will attend the next visit; the helper stays on weekdays.", "Check-in: slept on two pillows, weight 61.4 kg this morning, a little less swelling.")),
        DoctorDemoPatient("demo-joseph", "Joseph Thomas", 58, "Relapsed lymphoma · follow-up after chemotherapy",
            "Relapsed non-Hodgkin lymphoma with night sweats and tiredness after his last cycle. Reviewing the previous conversation and preparing the next set of questions with his wife.",
            listOf("Discussion: he asked for time to consider options and did not want a decision made at this visit.", "No preference or treatment decision should be inferred from this note."),
            listOf("Prednisolone 40 mg, oral, each morning for 5 days with food", "Allopurinol 300 mg, oral, once daily", "Aciclovir 400 mg, oral, twice daily", "Co-trimoxazole 480 mg, oral, once daily, three days a week", "Ondansetron 8 mg, oral, twice daily for 3 days after each cycle"),
            listOf("Consultation note: haematology review after cycle four. Night sweats have reduced and fatigue persists. Options including further treatment, supportive care and symptom control were described and the family asked for time to consider."),
            listOf("Check-in: tired after the weekend but ate well. Wishes to review the wording before a note is shared.")),
    )

    fun seedAppointments(nowMillis: Long): List<DoctorDemoAppointment> {
        val hour = 3_600_000L
        val nextHour = (nowMillis / hour + 1) * hour
        return listOf(
            DoctorDemoAppointment("demo-visit-1", patients[0].id, nextHour, 30, "In person", note = "Care-planning conversation with Asha and her daughters"),
            DoctorDemoAppointment("demo-visit-2", patients[1].id, nextHour + hour, 20, "Phone", note = "Phone check-in on breathlessness and oxygen use"),
            DoctorDemoAppointment("demo-visit-3", patients[2].id, nextHour + 24 * hour, 30, "Video", note = "Video review of nausea and appetite"),
            DoctorDemoAppointment("demo-visit-4", patients[3].id, nextHour + 25 * hour, 45, "In person", note = "Longer visit to go through his questions on pain and routine"),
            DoctorDemoAppointment("demo-visit-5", patients[4].id, nextHour + 48 * hour, 30, "Phone", note = "Review of daily weights and swelling with her son"),
            DoctorDemoAppointment("demo-visit-6", patients[5].id, nowMillis - 24 * hour, 30, "In person", "Completed", "Review of pain control and treatment options"),
        )
    }

    fun validateAppointment(value: DoctorDemoAppointment, existing: List<DoctorDemoAppointment>, nowMillis: Long): String? {
        if (value.id.isBlank()) return "An appointment ID is required."
        if (patients.none { it.id == value.patientId }) return "Choose a patient."
        if (value.durationMinutes !in 1..480) return "Choose a duration between 1 and 480 minutes."
        if (value.type !in appointmentTypes) return "Choose In person, Phone or Video."
        if (value.status !in statuses) return "Choose a valid appointment status."
        if (value.note.length > 1000) return "Keep the appointment note under 1,000 characters."
        if (value.startMillis <= 0 || value.startMillis > Long.MAX_VALUE - value.durationMinutes.toLong() * 60_000L) return "Choose a valid appointment time."
        if (value.status == "Scheduled" && value.startMillis <= nowMillis) return "Choose a future appointment time."
        if (value.status != "Cancelled" && existing.any {
                it.id != value.id && it.status != "Cancelled" &&
                    value.startMillis < it.endMillis && it.startMillis < value.endMillis
            }) return "This overlaps another appointment."
        return null
    }

    fun validateStatusChange(current: String, next: String): String? = when {
        current !in statuses || next !in statuses -> "Choose a valid appointment status."
        current == "Cancelled" && next != "Cancelled" -> "A cancelled appointment cannot be reopened or completed."
        current == "Completed" && next != "Completed" -> "A completed appointment cannot be reopened or cancelled."
        else -> null
    }
}
