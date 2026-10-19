package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareDocumentEvidenceTest {
    private val value = ReviewedDocumentText("report-1", "person-1", "a".repeat(64), listOf(DocumentPageText(1, "Original line\nTab Example 10 mg as written")), 1)
    @Test fun copiedFactKeepsExactPageLineHashAndCheckedTime() {
        val reviewed = DocumentEvidenceRules.confirm(value, true, 123)
        val source = DocumentEvidenceRules.source(reviewed, 1, 2, "Tab Example 10 mg as written")
        assertEquals("person-1", source.personId); assertEquals(2, source.line); assertEquals(123L, source.reviewedAt)
        val detail = DocumentEvidenceRules.copiedDetail(source, "Original prescription.pdf")
        assertTrue(detail.contains("page 1 · line 2")); assertTrue(detail.contains("a".repeat(64)))
        assertTrue(detail.contains("does not confirm treatment occurred"))
    }
    @Test fun correctionsRetainOriginalAndRequireNewConfirmation() {
        val corrected = value.copy(pages = listOf(value.pages.single().copy(reviewedText = "Corrected text")))
        assertEquals("Original line\nTab Example 10 mg as written", corrected.pages.single().originalText)
        fails { DocumentEvidenceRules.source(corrected, 1, 1, "Corrected text") }
        assertEquals("Corrected text", DocumentEvidenceRules.source(DocumentEvidenceRules.confirm(corrected, true, 200), 1, 1, "Corrected text").quote)
    }
    @Test fun fabricatedCrossPageOrChangedQuoteCannotBecomeFact() {
        val reviewed = DocumentEvidenceRules.confirm(value, true, 1)
        fails { DocumentEvidenceRules.source(reviewed, 2, 1, "Original line") }
        fails { DocumentEvidenceRules.source(reviewed, 1, 1, "Change dose to 20 mg") }
        fails { DocumentEvidenceRules.confirm(value, false, 1) }
        fails { DocumentEvidenceRules.confirm(value.copy(pages = listOf(DocumentPageText(1, ""))), true, 1) }
    }
    @Test fun packagingAndDiagnosisCannotCreatePrescriptionEntries() {
        val found = copiedPrescriptionMedicines(listOf(DocumentPageText(1, "Patient Sample\nDiagnosis: take treatment\nTab Example 10 mg as written\nHospital bill 500 mg")))
        assertEquals(1, found.size); assertEquals("10 mg", found.single().strength)
        assertEquals("Tab Example 10 mg as written", found.single().sourceQuote)
        assertEquals(listOf(0), found.single().prescriptionPageIndexes)
        assertEquals(-1, found.single().medicineImageIndex)
        assertTrue(found.single().suggestedTimes.isEmpty()); assertNull(found.single().durationDays)
    }
    @Test fun sourceSearchReturnsQuotedCurrentPersonPagesAndUnsupportedAnswersStayEmpty() {
        val saved = DocumentEvidenceRules.confirm(value, true, 100)
        val found = DocumentEvidenceRules.find("my medicine prescription", listOf(saved))
        // The source says 'Tab', not 'medicine'; search may return no supported passage and must not invent one.
        assertTrue(found.isEmpty())
        val exact = DocumentEvidenceRules.find("Example", listOf(saved)).single()
        assertEquals("report-1", exact.reportId); assertEquals(1, exact.page)
        assertTrue(exact.quote.contains("Tab Example 10 mg as written"))
        assertTrue(DocumentEvidenceRules.find("cure diagnosis", listOf(saved)).isEmpty())
        assertTrue(DocumentEvidenceRules.find("Example", listOf(value)).isEmpty())
    }
    private fun fails(action: () -> Unit) { try { action(); fail("Expected source validation to reject this value") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { } }
}
