package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareCancerDocumentsTest {
    @Test fun fileNamesSuggestCancerDocumentTypes() {
        assertEquals("PET-CT", CancerDocumentRules.detect("PET_CT report March.pdf"))
        assertEquals("PET-CT", CancerDocumentRules.detect("WholeBody PETCT.jpg"))
        assertEquals("Biopsy", CancerDocumentRules.detect("Breast biopsy result.pdf"))
        assertEquals("Biopsy", CancerDocumentRules.detect("FNAC neck node.png"))
        assertEquals("Histopathology", CancerDocumentRules.detect("HPE report.pdf"))
        assertEquals("Histopathology", CancerDocumentRules.detect("IHC panel.pdf"))
        assertEquals("Staging", CancerDocumentRules.detect("TNM staging.pdf"))
    }
    @Test fun ordinaryReportsAreNotGuessed() {
        assertNull(CancerDocumentRules.detect("Blood test results.png"))
        assertNull(CancerDocumentRules.detect("Competition entry.pdf"))
        assertNull(CancerDocumentRules.detect("Carpet bill.pdf"))
        assertNull(CancerDocumentRules.detect("Lab pathology report.pdf"))
    }
    @Test fun familyChoiceWinsOverFileName() {
        assertEquals("Biopsy", CancerDocumentRules.type("Scan 12.jpg", "Biopsy"))
        assertNull(CancerDocumentRules.type("PET CT.pdf", CancerDocumentRules.NOT_CANCER))
        assertEquals("PET-CT", CancerDocumentRules.type("PET CT.pdf", null))
        assertEquals("Other", CancerDocumentRules.type("Letter.pdf", "Other"))
        assertNull(CancerDocumentRules.type("Letter.pdf", "Unknown type"))
    }
}
