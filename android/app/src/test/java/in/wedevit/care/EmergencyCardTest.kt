package `in`.wedevit.care

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class EmergencyCardTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val at = 1_793_000_000_000L
    private val goals = EmergencyGoals(3, "Rahul (patient)", at, "Stay at home with family; avoid ICU if possible.", "Call Dr Meera if pain is not controlled.")
    private val source = EmergencySource("Rahul Verma", goals,
        listOf(EmergencyContact("Meera", "Sister", "+91 90000 00001"), EmergencyContact("Priya Nair", "Nurse coordinator")),
        listOf(EmergencyMedicine("Morphine sulfate 10 mg", "Oral, every 4 hours as needed. For breakthrough pain."), EmergencyMedicine("Ondansetron 4 mg")))
    private val all = EmergencySection.entries.toSet()

    @Test fun onlyChosenSectionsAreOnTheCardAndTheRestIsNamed() {
        val card = EmergencyCardRules.build(source, setOf(EmergencySection.NAME, EmergencySection.CONTACTS), 1, "AB12CD34", at, zone)
        assertTrue(card.text.contains("NAME: Rahul Verma"))
        assertTrue(card.text.contains("- Meera (Sister) +91 90000 00001"))
        assertFalse(card.text.contains("Morphine")); assertFalse(card.text.contains("ICU"))
        assertTrue(card.leftOut.contains("goals of care (family chose not to include)"))
        assertTrue(card.leftOut.contains("current medicines (family chose not to include)"))
        assertTrue(card.text.contains("Card 1 · ID AB12CD34"))
        assertTrue(card.text.contains("not linked to any hospital system"))
        assertTrue(card.text.contains("NOT RECORDED IN APP: age, conditions, allergies"))
    }

    @Test fun goalsCarryTheConfirmedVersionAndMedicinesKeepTheirFirstInstruction() {
        val card = EmergencyCardRules.build(source, all, 1, "AB12CD34", at, zone)
        assertTrue(card.text.contains("GOALS OF CARE (note v3, confirmed in app by Rahul (patient)"))
        assertTrue(card.text.contains("What matters: Stay at home with family; avoid ICU if possible."))
        assertTrue(card.text.contains("- Morphine sulfate 10 mg - Oral, every 4 hours as needed"))
        assertFalse(card.text.contains("breakthrough"))
        assertTrue(card.leftOut.isEmpty())
    }

    @Test fun recordedAllergiesArePrintedAndNeverCalledNotRecorded() {
        val withProfile = source.copy(allergies = "Penicillin (rash)", conditions = "Lung cancer", age = 71)
        val card = EmergencyCardRules.build(withProfile, all, 1, "AB12CD34", at, zone)
        assertTrue(card.text.contains("ALLERGIES (as listed by family): Penicillin (rash)"))
        assertFalse(card.text.contains("NOT RECORDED IN APP"))
        assertTrue(card.leftOut.contains("age (recorded in app)")); assertTrue(card.leftOut.contains("conditions (recorded in app)"))
        assertFalse(card.text.contains("Lung cancer"))
        val onlyAllergies = EmergencyCardRules.build(source.copy(allergies = "Sulfa"), all, 1, "AB12CD34", at, zone)
        assertTrue(onlyAllergies.text.contains("NOT RECORDED IN APP: age, conditions")); assertFalse(onlyAllergies.text.contains("conditions, allergies"))
        assertEquals("age, conditions, allergies", EmergencyCardRules.notRecorded(source))
        // A saved card made before the allergy was added is now out of date.
        assertTrue(EmergencyCardRecord.make("p", source, all, null, at, zone, id = "A").outOfDate(source.copy(allergies = "Sulfa"), zone))
    }

    @Test fun aFullCardShortensAllergiesLastAndStillFits() {
        val big = source.copy(name = "र".repeat(80), medicines = (1..40).map { EmergencyMedicine("दवा $it") }, contacts = (1..12).map { EmergencyContact("मीरा $it", "बहन", "+91 98765 4321$it") },
            goals = goals.copy(whatMatters = "घर पर रहना ".repeat(80)), allergies = "पेनिसिलिन से चकत्ते ".repeat(20), conditions = "कैंसर", age = 70)
        val card = EmergencyCardRules.build(big, all, 1, "AB12CD34", at, zone)
        assertTrue(EmergencyCardRules.bytes(card.text) <= EmergencyCardRules.MAX_BYTES)
        assertTrue(card.text.contains("ALLERGIES (as listed by family): पेनिसिलिन"))
    }

    @Test fun nothingChosenIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { EmergencyCardRules.build(source, emptySet(), 1, "X", at, zone) }
    }

    @Test fun tooMuchTextDropsMedicinesThenShortensGoalsAndSaysSo() {
        val big = source.copy(medicines = (1..40).map { EmergencyMedicine("Medicine number $it 500 mg", "Twice daily after food") },
            goals = goals.copy(whatMatters = "Being at home matters most. ".repeat(60)))
        val card = EmergencyCardRules.build(big, all, 7, "AB12CD34", at, zone)
        assertTrue(EmergencyCardRules.bytes(card.text) <= EmergencyCardRules.MAX_BYTES)
        assertTrue(card.leftOut.any { it.endsWith("medicines (no room)") })
        assertTrue(card.leftOut.contains("rest of the goals-of-care note (no room)"))
        assertTrue(card.text.contains("NOT ON THIS CARD:"))
        assertTrue(card.text.contains("- Meera (Sister)"))
        assertTrue(card.text.contains("…"))
    }

    @Test fun mediumOverflowOnlyDropsMedicinesFromTheEnd() {
        val many = source.copy(medicines = (1..30).map { EmergencyMedicine("Medicine $it") })
        val card = EmergencyCardRules.build(many, all, 1, "AB12CD34", at, zone)
        assertTrue(card.text.contains("- Medicine 1\n"))
        assertFalse(card.text.contains("- Medicine 30"))
        assertFalse(card.leftOut.any { it.contains("goals") })
        assertTrue(EmergencyCardRules.bytes(card.text) <= EmergencyCardRules.MAX_BYTES)
    }

    @Test fun hindiTextIsCutOnWholeLettersAndStaysValidUtf8() {
        val hindi = source.copy(name = "राहुल वर्मा", goals = goals.copy(whatMatters = "घर पर परिवार के साथ रहना सबसे ज़रूरी है। ".repeat(40)),
            contacts = (1..12).map { EmergencyContact("मीरा $it", "बहन", "+91 98765 4321$it") })
        val card = EmergencyCardRules.build(hindi, all, 1, "AB12CD34", at, zone)
        val bytes = card.text.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size <= EmergencyCardRules.MAX_BYTES)
        assertEquals(card.text, String(bytes, Charsets.UTF_8))
        assertFalse(card.text.contains('�'))
        assertTrue(card.text.contains("NAME: राहुल वर्मा"))
    }

    @Test fun clipNeverSplitsASurrogatePair() {
        val value = "ab😀cd"
        assertEquals("ab…", EmergencyCardRules.clip(value, 3))
        assertEquals("ab😀…", EmergencyCardRules.clip(value, 4))
        assertEquals(value, EmergencyCardRules.clip(value, 5))
    }

    @Test fun fullestCardStillMakesAScannableQrThatReadsBackExactly() {
        val hindi = source.copy(name = "राहुल वर्मा", goals = goals.copy(whatMatters = "घर पर परिवार के साथ रहना सबसे ज़रूरी है। ".repeat(40)),
            medicines = (1..40).map { EmergencyMedicine("दवा $it") })
        val text = EmergencyCardRules.build(hindi, all, 1, "AB12CD34", at, zone).text
        val qr = EmergencyCardRules.qr(text)
        assertTrue("QR version ${qr.version.versionNumber}", qr.version.versionNumber <= 25)
        assertEquals(text, decode(qr))
        assertEquals(EmergencyCardRules.build(source, all, 1, "AB12CD34", at, zone).text, decode(EmergencyCardRules.qr(EmergencyCardRules.build(source, all, 1, "AB12CD34", at, zone).text)))
    }

    @Test fun overLongTextIsRefusedByTheEncoder() {
        assertThrows(IllegalArgumentException::class.java) { EmergencyCardRules.qr("x".repeat(EmergencyCardRules.MAX_BYTES + 1)) }
    }

    @Test fun latestConfirmedNoteNeedsAPatientConfirmationOfThatExactVersion() {
        fun v(n: Int, hash: String = "h$n") = CareVersion(id = "v$n", personId = "p", version = n, personName = "Rahul", source = "s", priorities = "matters $n", participants = "", topics = "", questions = "", nextSteps = "next $n", doctor = "Dr", contentHash = hash)
        val versions = listOf(v(1), v(2), v(3))
        val acks = listOf(
            Acknowledgement("v1", "h1", "Rahul", "Patient", at = 10, id = "a1"),
            Acknowledgement("v2", "h2", "Rahul", "Patient", at = 20, id = "a2"),
            Acknowledgement("v3", "old-hash", "Rahul", "Patient", at = 30, id = "a3"),
            Acknowledgement("v3", "h3", "Arjun", "Family / carer", at = 31, id = "a4"),
        )
        val goals = requireNotNull(EmergencyCardRules.latestConfirmed(versions, acks))
        assertEquals(2, goals.version); assertEquals("matters 2", goals.whatMatters); assertEquals(3, goals.newerUnconfirmedVersion); assertEquals("Rahul (patient)", goals.confirmedBy)
        assertEquals(1, EmergencyCardRules.latestConfirmed(versions, acks) { it.id != "a2" }?.version)
        assertNull(EmergencyCardRules.latestConfirmed(versions, emptyList()))
    }

    @Test fun replacingGivesANewIdAndMarksTheOldCardOutdated() {
        val first = EmergencyCardRecord.make("p", source, all, null, at, zone, id = "AAAA1111")
        val second = EmergencyCardRecord.make("p", source, setOf(EmergencySection.NAME), first, at + 1000, zone, id = "BBBB2222")
        assertEquals(2, second.number); assertEquals("BBBB2222", second.id)
        assertEquals(listOf(ReplacedEmergencyCard("AAAA1111", 1, at, at + 1000)), second.replaced)
        assertTrue(second.text.contains("Card 2 · ID BBBB2222"))
        val third = EmergencyCardRecord.make("p", source, all, second, at + 2000, zone, id = "CCCC3333")
        assertEquals(listOf("AAAA1111", "BBBB2222"), third.replaced.map { it.id })
        assertThrows(IllegalArgumentException::class.java) { EmergencyCardRecord.make("p", source, all, third, at, zone, id = "CCCC3333") }
        assertThrows(IllegalArgumentException::class.java) { EmergencyCardRecord.make("other", source, all, third, at, zone, id = "DDDD4444") }
    }

    @Test fun savedCardRoundTripsAndNoticesChangedRecords() {
        val card = EmergencyCardRecord.make("p", source, all, EmergencyCardRecord.make("p", source, all, null, at, zone, id = "AAAA1111"), at + 5, zone, id = "BBBB2222")
        assertEquals(card, EmergencyCardRecord.decode(card.encode()))
        assertFalse(card.outOfDate(source, zone))
        assertTrue(card.outOfDate(source.copy(medicines = source.medicines + EmergencyMedicine("Lactulose 15 ml")), zone))
        assertFalse(EmergencyCardRecord.make("p", source, setOf(EmergencySection.NAME), null, at, zone, id = "C").outOfDate(source.copy(medicines = emptyList()), zone))
    }

    private fun decode(qr: com.google.zxing.qrcode.encoder.QRCode): String {
        val m = qr.matrix; val scale = 4; val quiet = 4; val side = (m.width + 2 * quiet) * scale
        val pixels = IntArray(side * side) { i -> val x = i % side / scale - quiet; val y = i / side / scale - quiet
            if (x in 0 until m.width && y in 0 until m.height && m.get(x, y).toInt() == 1) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true, DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }
}
