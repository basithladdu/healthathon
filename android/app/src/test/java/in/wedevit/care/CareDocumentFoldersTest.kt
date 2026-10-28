package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareDocumentFoldersTest {
    private val lab = """CITY DIAGNOSTIC LABORATORY
Patient: R. Kumar   Sample collected: 12/10/2026
Test Name            Result   Units   Biological Reference Interval
Haemoglobin          9.8      g/dL    13.0 - 17.0
Platelet count       1.4      lakh/cumm   1.5 - 4.1"""
    private val bill = """SHREE MEDICALS
TAX INVOICE   Bill No: 4471   GSTIN 29ABCDE1234F1Z5
Tab Paracetamol 500 mg x 10    ₹ 22.00
Inj Ondansetron 4 mg x 2       ₹ 64.00
Total amount  ₹ 86.00   Paid by cash"""
    private val prescription = """Dr. S. Rao, MD (Palliative Medicine)
Rx
1. Tab Morphine SR 10 mg   1-0-1   after food   x 5 days
2. Syp Lactulose 15 ml   HS
3. Tab Ondansetron 4 mg   SOS"""
    private val discharge = """DISCHARGE SUMMARY
Date of Admission: 02/10/2026   Date of Discharge: 08/10/2026
Final diagnosis: Carcinoma cervix, stage IVB
Course in hospital: pain controlled with oral morphine. Investigations: Haemoglobin 9.1 g/dL
Advice on discharge: Tab Morphine SR 10 mg BD; review in OPD on 12/11/2026"""

    @Test fun textReadOnThePhoneSortsEachKind() {
        assertEquals(DocumentFolderRules.LAB, DocumentFolderRules.sort("Scan 2026-10-12 09-14.jpg", lab))
        assertEquals(DocumentFolderRules.BILLS, DocumentFolderRules.sort("IMG_2041.jpg", bill))
        assertEquals(DocumentFolderRules.PRESCRIPTIONS, DocumentFolderRules.sort("Scan 2026-10-20 18-02.jpg", prescription))
        assertEquals(DocumentFolderRules.DISCHARGE, DocumentFolderRules.sort("document.pdf", discharge))
    }

    @Test fun aDischargeSummaryWinsOverTheMedicinesAndTestsInsideIt() {
        val scores = DocumentFolderRules.scores("document.pdf", discharge)
        assertTrue(scores.toString(), scores.getValue(DocumentFolderRules.DISCHARGE) > scores.getValue(DocumentFolderRules.PRESCRIPTIONS))
        assertTrue(scores.toString(), scores.getValue(DocumentFolderRules.DISCHARGE) > scores.getValue(DocumentFolderRules.LAB))
    }

    @Test fun aPharmacyBillIsABillNotAPrescription() {
        val scores = DocumentFolderRules.scores("IMG_2041.jpg", bill)
        assertTrue(scores.toString(), scores.getValue(DocumentFolderRules.BILLS) > scores.getValue(DocumentFolderRules.PRESCRIPTIONS))
    }

    @Test fun fileNamesAloneCanSortWhenNoTextWasRead() {
        assertEquals(DocumentFolderRules.BILLS, DocumentFolderRules.sort("Hospital bill October.pdf", ""))
        assertEquals(DocumentFolderRules.PRESCRIPTIONS, DocumentFolderRules.sort("Prescription Dr Rao.jpg", ""))
        assertEquals(DocumentFolderRules.DISCHARGE, DocumentFolderRules.sort("Discharge summary.pdf", ""))
        assertEquals(DocumentFolderRules.LAB, DocumentFolderRules.sort("Lab report CBC.pdf", ""))
    }

    @Test fun hindiWordsSort() {
        assertEquals(DocumentFolderRules.BILLS, DocumentFolderRules.sort("scan.jpg", "दवा की दुकान\nबिल नंबर 22\nकुल राशि ₹ 340"))
        assertEquals(DocumentFolderRules.DISCHARGE, DocumentFolderRules.sort("scan.jpg", "डिस्चार्ज की तारीख 08/10/2026\nभर्ती 02/10/2026"))
    }

    @Test fun unclearOrEmptyTextGoesToOther() {
        assertEquals(DocumentFolderRules.OTHER, DocumentFolderRules.sort("Scan 1.jpg", ""))
        assertEquals(DocumentFolderRules.OTHER, DocumentFolderRules.sort("Letter.pdf", "To whom it may concern, this is to certify that the patient is under our care."))
        // Whole words only: "bilirubin" is not "bill", "mrs" is not "rs", "total" alone is not enough.
        assertEquals(DocumentFolderRules.OTHER, DocumentFolderRules.sort("notes.jpg", "Mrs Kumar asked about total care at home"))
    }

    @Test fun countsCoverEveryFolderIncludingEmptyOnes() {
        val counts = DocumentFolderRules.counts(listOf("lab", "lab", "bills", "other"))
        assertEquals(mapOf("discharge" to 0, "bills" to 1, "lab" to 2, "prescriptions" to 0, "other" to 1), counts)
    }

    @Test fun thePersonsChoiceIsStoredSeparatelyFromTheSort() {
        assertEquals(DocumentFolder("lab", true), DocumentFolderRules.decode(DocumentFolderRules.encode(DocumentFolder("lab", true))))
        assertEquals(DocumentFolder("bills", false), DocumentFolderRules.decode(DocumentFolderRules.encode(DocumentFolder("bills", false))))
        assertNull(DocumentFolderRules.decode("chosen:unknown"))
        assertNull(DocumentFolderRules.decode("lab"))
        assertNull(DocumentFolderRules.decode(null))
    }

    @Test fun everyFolderHasEnglishAndHindiLabels() {
        DocumentFolderRules.folders.forEach { folder ->
            assertTrue(HindiScan.hasHindi(DocumentFolderRules.label(folder) { _, hi -> hi }))
            assertFalse(HindiScan.hasHindi(DocumentFolderRules.label(folder) { en, _ -> en }))
        }
    }
}
