package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchConsentTest {
    private val person = "person-1"
    private val heard = ConsentReading(ConsentReadingRules.PERSON, " Ravi  Sample ", "Son", witnessName = "Mohan", witnessRelation = "Neighbour", at = "2026-11-25T10:00:00.000Z")

    @Test fun theChoiceRoundTripsThroughTheSharedRecord() {
        val on = ResearchConsentChoice(true, 1_000, "Patient", heard)
        val item = ResearchConsentCodec.item(person, on, "c1")
        assertEquals("journal", item.kind); assertEquals(ResearchConsentCodec.TITLE, item.title); assertEquals(1_000L, item.created)
        assertTrue(ResearchConsentCodec.isStructured(item.detail))
        assertEquals(on.copy(reading = ConsentReadingRules.clean(heard)), ResearchConsentCodec.decode(item.detail))
        // Switched off: no reading is kept with it.
        assertNull(ResearchConsentCodec.decode(ResearchConsentCodec.encode(ResearchConsentChoice(false, 2_000, "Family / carer", heard)))!!.reading)
        // Not ours, or unreadable.
        assertNull(ResearchConsentCodec.decode("Research consent"))
        assertNull(ResearchConsentCodec.decode(ResearchConsentCodec.PREFIX + "{\"allowed\":\"yes\",\"at\":1}"))
        assertNull(ResearchConsentCodec.decode(ResearchConsentCodec.PREFIX + "{\"allowed\":true,\"at\":0}"))
        assertNull(ResearchConsentCodec.decode(ResearchConsentCodec.PREFIX + "not json"))
    }

    @Test fun theNewestChangeOnTheSharedRecordWinsForThatPerson() {
        val items = listOf(
            ResearchConsentCodec.item(person, ResearchConsentChoice(true, 1_000, "Patient", heard), "a"),
            ResearchConsentCodec.item(person, ResearchConsentChoice(false, 3_000, "Patient"), "b"),
            ResearchConsentCodec.item("someone-else", ResearchConsentChoice(true, 9_000, "Patient"), "c"),
            CareItem("d", person, "journal", "A note", "Felt fine today", created = 5_000),
        )
        assertEquals(ResearchConsentChoice(false, 3_000, "Patient"), ResearchConsentCodec.latest(items, person))
        assertNull(ResearchConsentCodec.latest(items.filter { it.id == "d" }, person))
    }

    @Test fun theSharedRecordWinsOverThisPhonesCopy() {
        val shared = ResearchConsentChoice(true, 1_000, "Patient", heard)
        assertEquals(shared, ResearchConsentCodec.current(shared, localAllowed = false, localAt = 5_000))
        // An older choice kept only on this phone is still shown until a change is saved to the shared record.
        assertEquals(ResearchConsentChoice(true, 5_000), ResearchConsentCodec.current(null, localAllowed = true, localAt = 5_000))
        assertNull(ResearchConsentCodec.current(null, localAllowed = false, localAt = 0))
    }

    @Test fun theSummaryNamesHowItWasReadOnlyWhileAllowed() {
        val t: (String, String) -> String = { en, _ -> en }
        val line = ConsentReadingRules.summary(heard, t)
        assertTrue(ResearchConsentRules.summary("Asha", true, "25 Nov", t, line).contains(line))
        assertFalse(ResearchConsentRules.summary("Asha", false, "25 Nov", t, line).contains(line))
    }
}
