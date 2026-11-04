package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

/** Ask your documents: looking in one kind of document, and telling the AI each document's kind. */
class CareDocumentAskScopeTest {
    private val folders = mapOf(
        "bill" to DocumentFolder(DocumentFolderRules.BILLS, false),
        "cbc" to DocumentFolder(DocumentFolderRules.LAB, true),
    )
    private val ids = listOf("bill", "cbc", "unsorted")

    @Test fun allAndChooseKeepEveryDocument() {
        assertEquals(ids, DocumentQuestionRules.inScope(ids, DocumentQuestionRules.SCOPE_ALL, folders) { it })
        assertEquals(ids, DocumentQuestionRules.inScope(ids, DocumentQuestionRules.SCOPE_PICK, folders) { it })
    }

    @Test fun aFolderKeepsOnlyItsDocumentsAndUnsortedCountsAsOther() {
        assertEquals(listOf("bill"), DocumentQuestionRules.inScope(ids, DocumentFolderRules.BILLS, folders) { it })
        assertEquals(listOf("cbc"), DocumentQuestionRules.inScope(ids, DocumentFolderRules.LAB, folders) { it })
        assertEquals(listOf("unsorted"), DocumentQuestionRules.inScope(ids, DocumentFolderRules.OTHER, folders) { it })
        assertTrue(DocumentQuestionRules.inScope(ids, DocumentFolderRules.DISCHARGE, folders) { it }.isEmpty())
    }

    @Test fun eachKnownKindIsNamedForTheAiAndOtherIsNot() {
        assertEquals("bill", DocumentQuestionRules.kindName(DocumentFolderRules.BILLS))
        assertEquals("lab report", DocumentQuestionRules.kindName(DocumentFolderRules.LAB))
        assertEquals("prescription", DocumentQuestionRules.kindName(DocumentFolderRules.PRESCRIPTIONS))
        assertEquals("discharge summary", DocumentQuestionRules.kindName(DocumentFolderRules.DISCHARGE))
        assertNull(DocumentQuestionRules.kindName(DocumentFolderRules.OTHER)); assertNull(DocumentQuestionRules.kindName(""))
    }

    @Test fun thePayloadCarriesTheKindButNotTheFileNameAndTheHashIgnoresIt() {
        val bill = DocumentQuestionSource("bill", "Hospital bill.jpg", listOf(DocumentPageText(1, "Total amount 2450")), reviewed = true, kind = DocumentFolderRules.BILLS)
        val lab = DocumentQuestionSource("cbc", "CBC.jpg", listOf(DocumentPageText(1, "Haemoglobin 9.8 g/dL")), reviewed = true, kind = DocumentFolderRules.LAB)
        val other = DocumentQuestionSource("x", "Note.jpg", listOf(DocumentPageText(1, "Something else")), reviewed = true)
        val request = DocumentQuestionRules.build("What is the total amount?", listOf(bill, lab, other))
        val documents = DocumentQuestionRules.payload(request).getJSONArray("documents")
        assertEquals("bill", documents.getJSONObject(0).getString("kind"))
        assertEquals("lab report", documents.getJSONObject(1).getString("kind"))
        assertFalse(documents.getJSONObject(2).has("kind"))
        assertFalse(documents.toString().contains("Hospital bill.jpg"))
        val unsorted = DocumentQuestionRules.build("What is the total amount?", listOf(bill.copy(kind = ""), lab.copy(kind = ""), other))
        assertEquals(unsorted.sourceHash, request.sourceHash)
    }
}
