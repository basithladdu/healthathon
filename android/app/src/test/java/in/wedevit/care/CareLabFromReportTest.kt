package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareLabFromReportTest {
    @Test fun commonReportLinesSplitIntoNameValueUnitAndPrintedRange() {
        assertEquals(LabLineCandidate("Haemoglobin", "10.2", "g/dL", "12.0", "15.5"), LabLineParser.parse("Haemoglobin 10.2 g/dL 12.0 - 15.5"))
        assertEquals(LabLineCandidate("S. Creatinine", "1.1", "mg/dL", "0.6", "1.2"), LabLineParser.parse("S. Creatinine : 1.1 mg/dL (0.6-1.2)"))
        assertEquals(LabLineCandidate("Platelet count", "150000", "/cumm", "150000", "450000"), LabLineParser.parse("Platelet count 1,50,000 /cumm 1,50,000 – 4,50,000"))
        assertEquals(LabLineCandidate("Sodium", "131", "mmol/L", "135", "145"), LabLineParser.parse("Sodium 131 L mmol/L 135-145"))
        assertEquals(LabLineCandidate("Total WBC", "4.2", "x10^3/uL", "4", "11"), LabLineParser.parse("Total WBC 4.2 x10^3/uL 4 to 11"))
        assertEquals(LabLineCandidate("CA 15-3", "38", "U/mL", "", "30"), LabLineParser.parse("CA 15-3 38 U/mL < 30"))
    }
    @Test fun numbersInsideNamesAndUnitsAfterTheRangeAreHandled() {
        assertEquals(LabLineCandidate("Vitamin B12", "250", "pg/mL", "", ""), LabLineParser.parse("Vitamin B12 250 pg/mL"))
        assertEquals(LabLineCandidate("Albumin", "3.1", "g/dL", "3.5", "5.0"), LabLineParser.parse("Albumin 3.1 3.5-5.0 g/dL"))
        assertEquals(LabLineCandidate("HbA1c", "6.5", "%", "", ""), LabLineParser.parse("HbA1c 6.5 %"))
    }
    @Test fun linesWithoutAResultGiveNoSuggestion() {
        assertNull(LabLineParser.parse("Date: 12/03/2026"))
        assertNull(LabLineParser.parse("Department of Pathology"))
        assertNull(LabLineParser.parse("12 Main Road"))
        assertNull(LabLineParser.parse(""))
    }
}
