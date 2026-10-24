package `in`.wedevit.care

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DoctorHistoricalDose(val id: String, val medicineLabel: String, val scheduledAt: Long, val reportedTakenAt: Long?, val loggedAt: Long?, val source: SampleReportSource?, val status: String)
data class DoctorProgressReport(
    val id: String, val patientId: String, val at: Long, val source: SampleReportSource,
    val text: String, val date: LocalDate, val zoneId: String, val isLatest: Boolean,
    val questions: List<String>, val familySupport: List<String>,
    val symptoms: String? = null, val clinicalAssessment: String? = null,
    val medicineActivity: List<DoctorHistoricalDose> = emptyList(),
    val linkedDiscussion: WorkflowSource? = null,
)

/** Read-only walkthrough history. Time proximity is never evidence of a discussion or visit link. */
object DoctorProgressData {
    private const val questionRequest = "Would like time to ask questions at the next visit."
    private const val practicalHelp = "Family is arranging practical help for the next appointment."
    // Explicit link metadata: these existing report IDs refer to their own prior source.
    private val sampleDiscussionLinks = DoctorDemoData.patients.associate { "${it.id}-check-3" to "${it.id}-prior" }

    fun reports(patientId: String, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<DoctorProgressReport> =
        build(patientId, DoctorPatientTracking.checkIns(patientId), DoctorPatientTracking.doses(patientId), nowMillis, zone)

    fun build(patientId: String, checkIns: List<SamplePatientCheckIn>, doses: List<SampleMedicineDose>, nowMillis: Long, zone: ZoneId): List<DoctorProgressReport> {
        if (DoctorDemoData.patients.none { it.id == patientId }) return emptyList()
        val records = checkIns.filter { it.patientId == patientId && it.at > 0 && it.at <= nowMillis && it.id.isNotBlank() && it.text.isNotBlank() }.sortedWith(compareByDescending<SamplePatientCheckIn> { it.at }.thenBy { it.id })
        return records.mapIndexed { index, report ->
            val date = Instant.ofEpochMilli(report.at).atZone(zone).toLocalDate()
            val relatedDoses = doses.filter { it.patientId == patientId && it.scheduledAt > 0 && Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate() == date }
                .sortedBy { it.scheduledAt }.map { historicalDose(it, report.at) }
            val linkedSourceId = sampleDiscussionLinks[report.id]
            val linkedSource = linkedSourceId?.let { sourceId -> DoctorWorkflowData.sources(patientId).firstOrNull { it.id == sourceId } }
            DoctorProgressReport(report.id, patientId, report.at, report.source, report.text, date, zone.id, index == 0,
                questions = if (report.id == "$patientId-check-1" && report.source == SampleReportSource.PATIENT && report.text == questionRequest) listOf(report.text) else emptyList(),
                familySupport = if (report.id == "$patientId-check-2" && report.source == SampleReportSource.FAMILY && report.text == practicalHelp) listOf(report.text) else emptyList(),
                medicineActivity = relatedDoses, linkedDiscussion = linkedSource)
        }
    }

    fun historicalDose(dose: SampleMedicineDose, at: Long): DoctorHistoricalDose {
        val loggedInTime = dose.loggedAt?.let { it > 0 && it <= at } == true && dose.source != null
        val takenInTime = dose.reportedTakenAt?.let { it > 0 && it <= at } == true
        val known = loggedInTime && dose.outcome != null && (dose.outcome != SampleDoseOutcome.TAKEN || dose.reportedTakenAt == null || takenInTime)
        val taken = dose.reportedTakenAt.takeIf { known && takenInTime && dose.outcome == SampleDoseOutcome.TAKEN }
        val status = when {
            known && dose.outcome == SampleDoseOutcome.SKIPPED -> "Skipped reported"
            known && dose.outcome == SampleDoseOutcome.TAKEN && taken != null -> if (taken > dose.scheduledAt) "Late reported" else "Taken reported"
            known && dose.outcome == SampleDoseOutcome.TAKEN -> "Taken reported · time not recorded"
            dose.scheduledAt > at -> "Upcoming"
            else -> "Not logged"
        }
        return DoctorHistoricalDose(dose.id, dose.medicineLabel, dose.scheduledAt, taken, dose.loggedAt.takeIf { known }, dose.source.takeIf { known }, status)
    }
}
