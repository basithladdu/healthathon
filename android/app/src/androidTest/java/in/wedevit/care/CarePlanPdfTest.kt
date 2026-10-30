package `in`.wedevit.care

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

/** On a device only (PdfDocument has no native backing under Robolectric): both PDF generators run in English and Hindi and write a PDF file. */
@RunWith(AndroidJUnit4::class)
class CarePlanPdfTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val now = "2026-10-30T10:00:00Z"
    private fun out(name: String) = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
    private fun assertPdf(file: File) { assertTrue(file.path, file.length() > 500); assertTrue(file.path, file.readBytes().copyOf(4).decodeToString() == "%PDF") }

    @Test fun planAndLivingWillPdfs() {
        val plan = EctprLifecycle.newDraft("p1", "dr", now, EctprPatient(name = "Asha Rao", dob = "1950-01-01", uhid = "UH-1"), id = "r1").copy(
            status = EctprStatus.ACTIVE, reviewDue = "2027-01-20", prognosticUnderstanding = PrognosticUnderstanding(true, "Dr Iyer", "2026-10-30"),
            signatures = listOf(Signature(SignatureRole.TREATING_CONSULTANT, "Dr Iyer", "KMC-1", now)),
            choices = PlanChoices(goalOfCare = GoalOfCare.C_COMFORT, cpr = Cpr.DNACPR, dnacprReason = DnacprReason.FUTILITY, respCeiling = 1, hospitalTransfer = HospitalTransfer(TransferKind.NO)))
        val will = AmdLifecycle.newDraft("p1", now, AmdMaker("Asha Rao", "1950-01-01", idType = IdType.AADHAAR, idLast4 = "9012"), id = "a1").copy(soundMindDeclaration = true,
            answers = AmdAnswers(listOf(AmdSituation.TERMINAL_ILLNESS), listOf(AmdTreatment.TUBE_FEEDING, AmdTreatment.CPR), PlaceOfCare.HOME), dhrs = listOf(Dhr("Ravi Rao", "Son", "98450 00000")))
        listOf(false, true).forEach { hindi ->
            val suffix = if (hindi) "hi" else "en"
            out("plan-$suffix.pdf").also { CarePlanPdf.plan(it, plan, EdView.interpret(plan, today), AmdLifecycle.edSummary(emptyList(), "p1"), CardTokens.url("a".repeat(32)), hindi, today); assertPdf(it) }
            out("will-$suffix.pdf").also { CarePlanPdf.livingWill(it, will, hindi, today); assertPdf(it) }
        }
    }
}
