package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

/** "Is everything on the card still the same?" (clinical team, 24 Nov 2026): renewal keeps the content and moves the valid-until date. */
class EmergencyCardRenewalTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val at = 1_793_000_000_000L
    private val day = 86_400_000L
    private val source = EmergencySource("Rahul Verma", null,
        listOf(EmergencyContact("Meera", "Sister", "+91 90000 00001", "family")),
        listOf(EmergencyMedicine("Morphine sulfate 10 mg", "Oral, every 4 hours as needed.")),
        doctor = EmergencyDoctor("Dr Anil Rao", "+91 90000 00009", "City Hospital"))
    private val card = EmergencyCardRecord.make("p", source, EmergencySection.entries.toSet(), null, at, zone, id = "AB12CD34")

    @Test fun theQuestionShowsFrom14DaysBeforeTheValidUntilDate() {
        assertFalse(card.renewalDue(at + 75 * day))
        assertTrue(card.renewalDue(at + 76 * day))
        assertTrue(card.renewalDue(at + 120 * day))
    }

    @Test fun yesStillTheSameRenewsThreeMonthsWithoutChangingTheContent() {
        val now = at + 85 * day
        val renewed = card.renew(now, zone)
        assertEquals(now + 90 * day, renewed.validUntil)
        assertEquals(card.id, renewed.id); assertEquals(card.number, renewed.number); assertEquals(card.body, renewed.body); assertEquals(card.sections, renewed.sections)
        assertFalse(renewed.renewalDue(now))
        val before = org.json.JSONObject(card.snapshot); val after = org.json.JSONObject(renewed.snapshot)
        assertEquals(now + 90 * day, after.getLong("validUntil"))
        assertEquals(before.getJSONArray("medicines").toString(), after.getJSONArray("medicines").toString())
        assertEquals(before.getJSONObject("nextOfKin").toString(), after.getJSONObject("nextOfKin").toString())
        assertFalse(renewed.outOfDate(source, zone))
        assertTrue(renewed.text.contains("VALID UNTIL " + EmergencyCardRules.day(now + 90 * day, zone)))
    }

    @Test fun theRenewalIsSavedOnThePhone() {
        val renewed = card.renew(at + 80 * day, zone)
        assertEquals(renewed.validUntil, EmergencyCardRecord.decode(renewed.encode()).validUntil)
        assertNull(EmergencyCardRecord.decode(card.encode()).renewedAt)
    }
}
