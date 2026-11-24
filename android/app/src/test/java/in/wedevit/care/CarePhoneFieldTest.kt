package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarePhoneFieldTest {
    private fun parts(code: String, number: String) = CarePhone.Parts(code, number)

    @Test fun combineWritesTheCodeAndGroupsAnIndianMobile() {
        assertEquals("+91 98765 43210", CarePhone.combine("+91", "9876543210"))
        assertEquals("+91 98765 43210", CarePhone.combine("+91", "98765-43210"))
        assertEquals("+91 98765 43210", CarePhone.combine("91", "98765x43210"))
        assertEquals("+971 501234567", CarePhone.combine("+971", "50 123 4567"))
        // A landline or a part-typed number keeps its digits as typed, without grouping.
        assertEquals("+91 08023456789", CarePhone.combine("+91", "080 2345 6789"))
        assertEquals("+91 98765", CarePhone.combine("+91", "98765"))
        // A blank code falls back to India.
        assertEquals("+91 98765 43210", CarePhone.combine("", "9876543210"))
    }

    @Test fun aCodeWithoutDigitsIsNoNumber() {
        assertEquals("", CarePhone.combine("+971", ""))
        assertEquals("", CarePhone.combine("+91", " - "))
    }

    @Test fun numbersAreCutAtFifteenDigits() {
        assertEquals("+44 " + "1".repeat(15), CarePhone.combine("+44", "1".repeat(20)))
    }

    @Test fun splitReadsTheStoredForm() {
        assertEquals(parts("+91", "9876543210"), CarePhone.split("+91 98765 43210"))
        assertEquals(parts("+971", "501234567"), CarePhone.split("+971 501234567"))
        assertEquals(parts("+1", "4155550100"), CarePhone.split("+1 415 555 0100"))
        // A code the short list does not have is kept when a space follows it.
        assertEquals(parts("+49", "301234567"), CarePhone.split("+49 30 1234567"))
        assertEquals(parts("+91", ""), CarePhone.split("+91"))
    }

    @Test fun splitFindsTheCodeWithoutASpace() {
        assertEquals(parts("+91", "9876543210"), CarePhone.split("+919876543210"))
        assertEquals(parts("+971", "501234567"), CarePhone.split("+971501234567"))
        assertEquals(parts("+965", "51234567"), CarePhone.split("0096551234567"))
        assertEquals(parts("+44", "7700900123"), CarePhone.split("+447700900123"))
    }

    @Test fun oldNumbersWithoutACodeReadAsIndia() {
        assertEquals(parts("+91", "9876543210"), CarePhone.split("9876543210"))
        assertEquals(parts("+91", "9876543210"), CarePhone.split("98765-43210"))
        assertEquals(parts("+91", "9876543210"), CarePhone.split("098765 43210"))
        assertEquals(parts("+91", "9876543210"), CarePhone.split("91 98765 43210"))
        assertEquals(parts("+91", "8023456789"), CarePhone.split("080 2345 6789"))
        assertEquals(parts("+91", "12345"), CarePhone.split("12345"))
    }

    @Test fun blankAndDigitlessTextIsEmpty() {
        assertEquals(parts("+91", ""), CarePhone.split(""))
        assertEquals(parts("+91", ""), CarePhone.split("   "))
        assertEquals(parts("+91", ""), CarePhone.split("call the clinic"))
    }

    @Test fun combineAndSplitRoundTripForEveryListedCode() {
        CarePhone.CODES.forEach { code ->
            val stored = CarePhone.combine(code, "9876543210")
            assertTrue(stored, stored.startsWith("$code "))
            assertEquals(stored, parts(code, "9876543210"), CarePhone.split(stored))
        }
    }

    @Test fun normaliseTurnsOldNumbersIntoTheStoredForm() {
        assertEquals("+91 98765 43210", CarePhone.normalise("98765-43210"))
        assertEquals("+91 98765 43210", CarePhone.normalise("+91 98765 43210"))
        assertEquals("", CarePhone.normalise(""))
    }

    @Test fun theListStartsWithIndiaAndHasTenCodes() {
        assertEquals("+91", CarePhone.CODES.first())
        assertEquals(10, CarePhone.CODES.distinct().size)
        assertEquals("+91", CarePhone.normaliseCode(""))
        assertEquals("+971", CarePhone.normaliseCode("971"))
    }
}
