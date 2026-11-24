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
        listOf(EmergencyMedicine("Morphine sulfate 10 mg", "Oral, every 4 hours as needed. For breakthrough pain."), EmergencyMedicine("Ondansetron 4 mg")),
        doctor = EmergencyDoctor("Dr Anil Rao", "+91 90000 00009", "City Hospital", "+91 40 2345 6789"))
    private val all = EmergencySection.entries.toSet()

    @Test fun onlyChosenSectionsAreOnTheCardAndTheRestIsNamed() {
        val card = EmergencyCardRules.build(source, setOf(EmergencySection.NAME, EmergencySection.CONTACTS), 1, "AB12CD34", at, zone)
        assertTrue(card.text.contains("NAME: Rahul Verma"))
        assertTrue(card.text.contains("- Next of kin: Meera (Sister) +91 90000 00001"))
        assertTrue(card.text.contains("- Doctor: Dr Anil Rao, +91 90000 00009, City Hospital, landline +91 40 2345 6789"))
        assertFalse(card.text.contains("Morphine")); assertFalse(card.text.contains("ICU"))
        assertTrue(card.leftOut.contains("goals of care (family chose not to include)"))
        assertTrue(card.leftOut.contains("current medicines (family chose not to include)"))
        assertTrue(card.text.contains("Card 1 · ID AB12CD34"))
        assertTrue(card.text.contains("Scan the QR for this card on the Sahara website"))
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
        assertTrue(card.text.contains("- Next of kin: Meera (Sister)"))
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

    @Test fun medicinesBecomeNameStrengthAndWhen() {
        // Labelled lines saved by the medicine form and the prescription scan.
        assertEquals(EmergencyMedicineRow("Morphine", "10 mg", "1 tablet, Every 4 hours, 08:00, 12:00, after food"),
            EmergencyCardRules.medicineRow(EmergencyMedicine("Morphine", "Strength: 10 mg\nForm: tablet\nHow much each time: 1 tablet\nHow often: Every 4 hours\nTimes: 08:00, 12:00\nFood: after food")))
        assertEquals(EmergencyMedicineRow("Paracetamol", "500 mg · 1 tablet", "Twice daily, 08:00, 20:00"),
            EmergencyCardRules.medicineRow(EmergencyMedicine("Paracetamol", "Dose: 500 mg · 1 tablet\nFrequency: Twice daily\nTimes: 08:00, 20:00\nCopied source · prescription page 1:\nTab PCM 500")))
        // Older free text: the strength in the name, the first instruction as "when".
        assertEquals(EmergencyMedicineRow("Morphine sulfate 10 mg", "10 mg", "Oral, every 4 hours as needed"), EmergencyCardRules.medicineRow(source.medicines[0]))
        assertEquals(EmergencyMedicineRow("Lactulose", "15 ml", "15 ml at night"), EmergencyCardRules.medicineRow(EmergencyMedicine("Lactulose", "15 ml at night")))
        assertEquals(EmergencyMedicineRow("Ondansetron 4 mg", "4 mg", ""), EmergencyCardRules.medicineRow(source.medicines[1]))
    }

    @Test fun nextOfKinIsTheFirstFamilyContactAndTheDoctorComesFromTheProfile() {
        val people = listOf(EmergencyContact("Dr Sujay", "Palliative doctor", "+91 90000 00005", "clinician"), EmergencyContact("Ravi", "Son", "+91 90000 00002", "family"))
        val withoutProfileDoctor = source.copy(contacts = people, doctor = null)
        assertEquals("Ravi", EmergencyCardRules.nextOfKin(withoutProfileDoctor)?.name)
        assertEquals(EmergencyDoctor("Dr Sujay", "+91 90000 00005"), EmergencyCardRules.doctorOf(withoutProfileDoctor))
        assertTrue(EmergencyCardRules.otherContacts(withoutProfileDoctor).isEmpty())
        assertEquals("Dr Anil Rao", EmergencyCardRules.doctorOf(source.copy(contacts = people))?.name)
        assertEquals(listOf("Dr Sujay"), EmergencyCardRules.otherContacts(source.copy(contacts = people)).map { it.name })
        val none = EmergencyCardRules.build(source.copy(contacts = emptyList(), doctor = null), all, 1, "AB12CD34", at, zone)
        assertTrue(none.leftOut.contains("no contacts saved")); assertTrue(none.leftOut.contains("no treating doctor saved"))
        val noPhone = EmergencyCardRules.build(source.copy(contacts = listOf(EmergencyContact("Meera", "Sister"))), all, 1, "AB12CD34", at, zone)
        assertTrue(noPhone.leftOut.contains("no phone number saved for the guardian"))
    }

    @Test fun printedTablesComeFromTheSameCopyAsTheWebPage() {
        val record = EmergencyCardRecord.make("p", source.copy(medicines = source.medicines + EmergencyMedicine("Senna", "Strength: 8.6 mg\nHow often: At night")), all, null, at, zone, id = "AB12CD34")
        val card = org.json.JSONObject(record.snapshot)
        assertEquals(2, card.getInt("v"))
        assertEquals(record.madeAt + 90L * 86_400_000L, card.getLong("validUntil"))
        val rows = EmergencyCardTables.contacts(card)
        assertEquals(EmergencyCardTables.Row("Next of kin", "Meera (Sister)", "+91 90000 00001"), rows[0])
        assertEquals(EmergencyCardTables.Row("Treating doctor", "Dr Anil Rao", "+91 90000 00009"), rows[1])
        assertEquals(EmergencyCardTables.Row("Hospital / clinic", "City Hospital", "+91 40 2345 6789"), rows[2])
        assertEquals(EmergencyCardTables.Row("Nurse coordinator", "Priya Nair", EmergencyCardTables.NOT_SAVED), rows[3])
        assertEquals(4, rows.size)
        val meds = EmergencyCardTables.medicines(card)
        assertEquals(EmergencyCardTables.MedicineRow("Senna", "8.6 mg", "At night"), meds.last())
        assertEquals("10 mg", meds[0].strength)
        // A web copy from before 24 Nov (v1: no next-of-kin, doctor, strength or when keys) still makes the tables.
        val old = org.json.JSONObject("""{"kind":"sahara-emergency-card","v":1,"sections":["NAME","CONTACTS","MEDICINES"],"contacts":[{"name":"Ravi","relationship":"Son","phone":"+91 1"},{"name":"Asha","relationship":"Daughter","phone":""}],"medicines":[{"name":"Paracetamol 500 mg","detail":"Twice daily"}]}""")
        assertEquals(listOf(EmergencyCardTables.Row("Next of kin", "Ravi (Son)", "+91 1"), EmergencyCardTables.Row("Daughter", "Asha", EmergencyCardTables.NOT_SAVED)), EmergencyCardTables.contacts(old))
        assertEquals(listOf(EmergencyCardTables.MedicineRow("Paracetamol 500 mg", "500 mg", "Twice daily")), EmergencyCardTables.medicines(old))
        assertEquals(setOf(EmergencySection.NAME, EmergencySection.CONTACTS, EmergencySection.MEDICINES), EmergencyCardTables.sections(old))
    }

    @Test fun theOnlyQrIsTheWebLinkNeverTheCardText() {
        val token = "ef".repeat(32)
        val record = EmergencyCardRecord.make("p", source, all, null, at, zone, id = "AB12CD34").copy(link = EmergencyLink(token, at, at + 1000, version = 3))
        val read = decode(EmergencyLinkQr.encode(record.link!!.url))
        assertEquals("https://sahara.wedevit.in/emergency#t=$token", read)
        assertFalse(read.contains("Rahul")); assertFalse(read.contains("Morphine"))
        // The app code never puts card text in a QR: the Today screen, full screen and PDF encode only link.url.
        val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map { java.io.File(it) }.first { it.isDirectory }
        val ui = java.io.File(dir, "EmergencyCardUi.kt").readText()
        assertEquals(listOf("EmergencyLinkQr.encode(link.url)", "EmergencyLinkQr.encode(it.url)"), Regex("EmergencyLinkQr\\.encode\\([^)]*\\)").findAll(ui).map { it.value }.toList())
        assertFalse(ui.contains("Encoder.encode(text") || ui.contains("EmergencyCardRules.qr"))
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
        assertTrue(card.snapshot.isNotBlank())
        // A card saved before 24 Nov has no snapshot; it still opens and publishes from today's records.
        val old = org.json.JSONObject(card.encode()).apply { remove("snapshot") }.toString()
        assertEquals("", EmergencyCardRecord.decode(old).snapshot)
        assertEquals("sahara-emergency-card", EmergencyCardRecord.decode(old).webCard(source).getString("kind"))
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
