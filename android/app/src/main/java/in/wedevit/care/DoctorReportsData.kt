package `in`.wedevit.care

enum class DoctorReportCategory(val label: String) {
    LAB_RESULTS("Lab results"), SCANS("Scans"), PATHOLOGY("Pathology"), CLINIC_LETTERS("Clinic letters"),
    DISCHARGE("Discharge"), PRESCRIPTIONS("Prescriptions"), OTHER("Other"),
}
data class DoctorReportSummary(
    val id: String, val patientId: String, val patientName: String, val title: String,
    val category: DoctorReportCategory, val summary: String,
    val sourceLabel: String = "Built-in fictional report summary",
    val author: String? = null, val createdAt: Long? = null,
    val hasAttachedFile: Boolean = false,
)

/** Explicit metadata for known sample summaries. Text keywords never establish a report type. */
object DoctorReportsData {
    private data class Fixture(val text: String, val title: String, val category: DoctorReportCategory)
    private val fixtures = mapOf(
        "demo-asha" to Fixture("Sample clinic letter: follow-up discussion requested. No real report attached.", "Clinic letter", DoctorReportCategory.CLINIC_LETTERS),
        "demo-ravi" to Fixture("Sample discharge-summary placeholder: review questions with the care team.", "Discharge summary", DoctorReportCategory.DISCHARGE),
        "demo-leela" to Fixture("Sample laboratory-report summary: demonstration text only, no clinical results.", "Laboratory report", DoctorReportCategory.LAB_RESULTS),
        "demo-imran" to Fixture("Sample imaging-report placeholder: no image or diagnostic interpretation included.", "Imaging report", DoctorReportCategory.SCANS),
        "demo-maya" to Fixture("Sample follow-up letter: demonstration of a document summary.", "Follow-up letter", DoctorReportCategory.CLINIC_LETTERS),
        "demo-joseph" to Fixture("Sample consultation note: no original document attached.", "Consultation note", DoctorReportCategory.OTHER),
    )
    fun reports(patientId: String): List<DoctorReportSummary> {
        val patient = DoctorDemoData.patients.firstOrNull { it.id == patientId } ?: return emptyList()
        return patient.reportSummaries.mapIndexed { index, text ->
            val known = fixtures[patientId]?.takeIf { index == 0 && it.text == text }
            DoctorReportSummary("$patientId-report-$index", patientId, patient.name.removeSuffix(" Demo"), known?.title ?: "Report summary ${index + 1}", known?.category ?: DoctorReportCategory.OTHER, text)
        }
    }
    fun filter(reports: List<DoctorReportSummary>, query: String, category: DoctorReportCategory?): List<DoctorReportSummary> {
        val term = query.trim()
        return reports.filter { (category == null || it.category == category) && (term.isEmpty() || listOf(it.title, it.summary, it.category.label).any { text -> text.contains(term, ignoreCase = true) }) }
    }
    fun counts(reports: List<DoctorReportSummary>): Map<DoctorReportCategory, Int> = DoctorReportCategory.entries.associateWith { category -> reports.count { it.category == category } }
}
