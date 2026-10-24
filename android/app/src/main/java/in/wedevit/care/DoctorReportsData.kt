package `in`.wedevit.care

enum class DoctorReportCategory(val label: String) {
    LAB_RESULTS("Lab results"), SCANS("Scans"), PATHOLOGY("Pathology"), CLINIC_LETTERS("Clinic letters"),
    DISCHARGE("Discharge"), PRESCRIPTIONS("Prescriptions"), OTHER("Other"),
}
data class DoctorReportSummary(
    val id: String, val patientId: String, val patientName: String, val title: String,
    val category: DoctorReportCategory, val summary: String,
    val sourceLabel: String = "Summary from patient record",
    val author: String? = null, val createdAt: Long? = null,
    val hasAttachedFile: Boolean = false,
)

/** Explicit metadata for known sample summaries. Text keywords never establish a report type. */
object DoctorReportsData {
    private data class Fixture(val text: String, val title: String, val category: DoctorReportCategory)
    private val fixtures = mapOf(
        "demo-asha" to Fixture("Clinic letter: seen in oncology review. Pain in the thoracic spine and ribs has increased over three weeks and is controlled with regular modified-release morphine. Palliative radiotherapy to the spine was discussed and Asha asked for time to decide. A family discussion is requested.", "Clinic letter", DoctorReportCategory.CLINIC_LETTERS),
        "demo-ravi" to Fixture("Discharge summary: admitted for four days with an exacerbation of COPD, treated with steroids and antibiotics, and discharged home on oxygen. Advised review with the community team within one week; breathlessness plan and questions to be reviewed with the care team.", "Discharge summary", DoctorReportCategory.DISCHARGE),
        "demo-leela" to Fixture("Laboratory report: haemoglobin 9.8 g/dL (printed range 12.0-15.5), albumin 31 g/L (printed range 35-50), ALT 88 U/L (printed range 7-35). Values and printed ranges only; no interpretation is given here.", "Laboratory report", DoctorReportCategory.LAB_RESULTS),
        "demo-imran" to Fixture("Imaging report: bone scan reports multiple foci of increased uptake in the pelvis, lumbar spine and right femur, consistent with metastatic disease as printed by the reporting radiologist. No further interpretation is added here.", "Imaging report", DoctorReportCategory.SCANS),
        "demo-maya" to Fixture("Follow-up letter: seen in the heart failure clinic. Ankle swelling is slightly less after the diuretic was increased. Weights, fluid advice and the plan for who to call out of hours were reviewed with her son.", "Follow-up letter", DoctorReportCategory.CLINIC_LETTERS),
        "demo-joseph" to Fixture("Consultation note: haematology review after cycle four. Night sweats have reduced and fatigue persists. Options including further treatment, supportive care and symptom control were described and the family asked for time to consider.", "Consultation note", DoctorReportCategory.OTHER),
    )
    fun reports(patientId: String): List<DoctorReportSummary> {
        val patient = DoctorDemoData.patients.firstOrNull { it.id == patientId } ?: return emptyList()
        return patient.reportSummaries.mapIndexed { index, text ->
            val known = fixtures[patientId]?.takeIf { index == 0 && it.text == text }
            DoctorReportSummary("$patientId-report-$index", patientId, patient.name, known?.title ?: "Report summary ${index + 1}", known?.category ?: DoctorReportCategory.OTHER, text)
        }
    }
    fun filter(reports: List<DoctorReportSummary>, query: String, category: DoctorReportCategory?): List<DoctorReportSummary> {
        val term = query.trim()
        return reports.filter { (category == null || it.category == category) && (term.isEmpty() || listOf(it.title, it.summary, it.category.label).any { text -> text.contains(term, ignoreCase = true) }) }
    }
    fun counts(reports: List<DoctorReportSummary>): Map<DoctorReportCategory, Int> = DoctorReportCategory.entries.associateWith { category -> reports.count { it.category == category } }
}
