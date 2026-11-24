package `in`.wedevit.care

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class EmergencyLinkTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val at = 1_793_000_000_000L
    private val token = "ab".repeat(32)
    private val longGoals = "Being at home with family matters most to Asha. ".repeat(40)
    private val source = EmergencySource("Asha (fictional)", EmergencyGoals(2, "Asha (patient)", at, longGoals, "Call the home-care nurse first."),
        listOf(EmergencyContact("Ravi", "Son", "+91 90000 00002")), listOf(EmergencyMedicine("Paracetamol 500 mg", "Twice daily")))

    @Test fun linkPutsTheTokenInTheFragmentOnly() {
        assertEquals("https://sahara.wedevit.in/emergency#t=$token", EmergencyLinkRules.url(token))
        assertThrows(IllegalArgumentException::class.java) { EmergencyLinkRules.url("ABC") }
        assertThrows(IllegalArgumentException::class.java) { EmergencyLinkRules.url("$token/../x") }
    }

    @Test fun missingServerFunctionMeansNotSetUpAndIsSaidPlainly() {
        assertEquals(EmergencyLinkProblem.NOT_SET_UP, EmergencyLinkRules.problemFor(404))
        assertEquals(EmergencyLinkProblem.NOT_OWNER, EmergencyLinkRules.problemFor(403))
        assertEquals(EmergencyLinkProblem.OFFLINE, EmergencyLinkRules.problemFor(500))
        assertTrue(EmergencyLinkProblem.NOT_SET_UP.en.contains("not available yet"))
        EmergencyLinkProblem.entries.forEach { assertTrue(it.name, it.hi.isNotBlank()) }
    }

    @Test fun serverAnswersAreRead() {
        val link = EmergencyLinkRules.decodeCreated("""{"token":"$token","created_at":"2026-10-27T20:23:21.903456+00:00","expires_at":"2027-10-27T20:23:21.903456+00:00"}""", 0)
        assertEquals(token, link.token)
        assertEquals(1_824_668_601_903L, link.expiresAt)
        assertThrows(IllegalArgumentException::class.java) { EmergencyLinkRules.decodeCreated("""{"token":"nope"}""", 0) }
        val status = EmergencyLinkRules.decodeStatus("""{"live":true,"views":3,"last_viewed_at":"2026-10-28T01:00:00+05:30","expires_at":"2027-10-27T20:23:21+00:00"}""")
        assertTrue(status.live); assertEquals(3, status.views); assertNotNull(status.lastViewedAt)
        assertFalse(EmergencyLinkRules.decodeStatus("""{"live":false}""").live)
        // 24 Nov: each published card is a version with its own token.
        val published = EmergencyLinkRules.decodeCreated("""{"token":"$token","created_at":"2026-10-27T20:23:21+00:00","expires_at":"2027-11-24T00:00:00+00:00","version":4,"published_at":"2026-11-24T10:00:00+00:00"}""", 0)
        assertEquals(4, published.version); assertEquals(1_795_514_400_000L, published.publishedAt)
        assertEquals(1, link.version); assertEquals(link.createdAt, link.publishedAt)
        assertEquals(4, EmergencyLinkRules.decodeStatus("""{"live":true,"views":0,"version":4}""").version)
        assertNull(EmergencyLinkRules.decodeStatus("""{"live":true,"views":0}""").version)
    }

    @Test fun webCopyHasOnlyTheTickedSectionsInFull() {
        val record = EmergencyCardRecord.make("p1", source, setOf(EmergencySection.NAME, EmergencySection.GOALS), null, at, zone, "AB12CD34")
        val card = EmergencyLinkRules.card(source, record)
        assertEquals("sahara-emergency-card", card.getString("kind"))
        assertEquals("Asha (fictional)", card.getString("name"))
        assertEquals(longGoals, card.getJSONObject("goals").getString("whatMatters"))
        assertTrue("QR copy is shortened", !record.text.contains(longGoals))
        assertFalse(card.has("contacts")); assertFalse(card.has("medicines"))
        val notIncluded = card.getJSONArray("notIncluded").toString()
        assertTrue(notIncluded.contains("emergency contacts (family chose not to include)"))
        assertTrue(notIncluded.contains("current medicines (family chose not to include)"))
    }

    @Test fun webCopyCarriesRecordedAllergiesAndOnlyTheReallyMissingDetails() {
        val withAllergy = source.copy(allergies = "Penicillin", age = 64)
        val record = EmergencyCardRecord.make("p1", withAllergy, EmergencySection.entries.toSet(), null, at, zone, "AB12CD34")
        val card = EmergencyLinkRules.card(withAllergy, record)
        assertEquals("Penicillin", card.getString("allergies"))
        assertEquals("conditions", card.getString("notRecorded"))
        assertTrue(card.getJSONArray("notIncluded").toString().contains("age (recorded in app)"))
        val none = EmergencyLinkRules.card(source, EmergencyCardRecord.make("p1", source, EmergencySection.entries.toSet(), null, at, zone, "AB12CD35"))
        assertFalse(none.has("allergies")); assertEquals("age, conditions, allergies", none.getString("notRecorded"))
    }

    @Test fun hugeMedicineListsStayUnderTheServerLimitAndSaySo() {
        val big = source.copy(medicines = (1..60).map { EmergencyMedicine("दवा नंबर $it ".repeat(10), "दिन में दो बार खाने के बाद ".repeat(15)) })
        val record = EmergencyCardRecord.make("p1", big, EmergencySection.entries.toSet(), null, at, zone, "AB12CD34")
        val card = EmergencyLinkRules.card(big, record)
        assertTrue(card.toString().toByteArray(Charsets.UTF_8).size <= EmergencyLinkRules.MAX_CARD_BYTES)
        assertTrue(card.getJSONArray("notIncluded").toString().contains("medicines (no room)"))
    }

    @Test fun linkSurvivesRestartsAndANewCardWaitsForItsOwnVersion() {
        val first = EmergencyCardRecord.make("p1", source, EmergencySection.entries.toSet(), null, at, zone, "AB12CD34").copy(link = EmergencyLink(token, at, at + 1000, version = 2, publishedAt = at + 7))
        val again = EmergencyCardRecord.decode(first.encode())
        assertEquals(first.link, again.link)
        // A link saved by an app from before 24 Nov reads as version 1, published when it was made.
        val legacy = JSONObject(first.encode()).apply { put("link", JSONObject().put("token", token).put("createdAt", at).put("expiresAt", at + 1000)) }.toString()
        assertEquals(EmergencyLink(token, at, at + 1000, 1, at), EmergencyCardRecord.decode(legacy).link)
        // The next card is published as a new version with a new token, so it starts without the old link.
        val next = EmergencyCardRecord.make("p1", source, setOf(EmergencySection.NAME), again, at + 5, zone, "CD34EF56")
        assertNull(next.link)
        assertEquals(2, next.number)
        assertNull(EmergencyCardRecord.decode(first.copy(link = null).encode()).link)
    }

    @Test fun webCopyCarriesNextOfKinDoctorAndMedicineTable() {
        val withDoctor = source.copy(doctor = EmergencyDoctor("Dr Shirin", "+91 90000 00007", "Sahara Clinic", "+91 40 1111 2222"),
            medicines = listOf(EmergencyMedicine("Morphine", "Strength: 5 mg\nHow often: Every 4 hours")))
        val card = EmergencyLinkRules.card(withDoctor, EmergencyCardRecord.make("p1", withDoctor, EmergencySection.entries.toSet(), null, at, zone, "AB12CD34"))
        assertEquals(2, card.getInt("v"))
        assertEquals("Ravi", card.getJSONObject("nextOfKin").getString("name"))
        assertEquals("+91 90000 00002", card.getJSONObject("nextOfKin").getString("phone"))
        val doctor = card.getJSONObject("doctor")
        assertEquals("Dr Shirin", doctor.getString("name")); assertEquals("Sahara Clinic", doctor.getString("place")); assertEquals("+91 40 1111 2222", doctor.getString("landline"))
        assertEquals(0, card.getJSONArray("otherContacts").length())
        val medicine = card.getJSONArray("medicines").getJSONObject(0)
        assertEquals("5 mg", medicine.getString("strength")); assertEquals("Every 4 hours", medicine.getString("when"))
        // Kept for pages deployed before 24 Nov.
        assertEquals(1, card.getJSONArray("contacts").length()); assertTrue(medicine.has("detail"))
        assertFalse(card.getJSONArray("notIncluded").toString().contains("no treating doctor saved"))
        assertTrue(EmergencyLinkRules.card(source, EmergencyCardRecord.make("p1", source, EmergencySection.entries.toSet(), null, at, zone, "AB12CD35")).getJSONArray("notIncluded").toString().contains("no treating doctor saved"))
    }

    @Test fun linkQrScansBackToTheUrl() {
        val qr = EmergencyLinkQr.encode(EmergencyLinkRules.url(token))
        val m = qr.matrix; val scale = 6; val quiet = 4; val side = (m.width + 2 * quiet) * scale
        val pixels = IntArray(side * side) { i -> val x = i % side / scale - quiet; val y = i / side / scale - quiet; if (x in 0 until m.width && y in 0 until m.height && m.get(x, y).toInt() == 1) 0xFF000000.toInt() else -1 }
        val read = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))).text
        assertEquals(EmergencyLinkRules.url(token), read)
    }
}
