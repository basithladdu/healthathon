package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ConsentReadAloudTest {
    private val png = GocMonoPng.encode(8, 2, BooleanArray(16) { it % 2 == 0 })
    private val note = "Sample note. Asha wants to be cared for at home."
    private val at = Instant.parse("2026-11-25T10:00:00Z").toEpochMilli()
    private val byPhone = ConsentReading(ConsentReadingRules.PHONE, language = "hi", witnessName = "Mohan K", witnessRelation = "Neighbour", at = "2026-11-25T10:00:00.000Z")
    private val bySon = ConsentReading(ConsentReadingRules.PERSON, "Ravi Sample", "Son", witnessName = "Nurse Sample", witnessRelation = "Nurse", at = "2026-11-25T10:00:00.000Z")

    @Test fun thePhoneOnlyReadsTextWrittenInTheAppLanguage() {
        // English text for an English user; a Hindi line with a Latin acronym for a Hindi or Marathi user.
        assertTrue(ConsentReadingRules.phoneCanReadExactly(note, "en"))
        assertTrue(ConsentReadingRules.phoneCanReadExactly("आशा घर पर देखभाल चाहती हैं। CPR नहीं।\n\n", "hi"))
        assertTrue(ConsentReadingRules.phoneCanReadExactly("आशा यांना घरी काळजी हवी आहे.", "mr"))
        // English text is never machine-translated into Tamil or Bengali for consent; Hindi text is not read in English.
        assertFalse(ConsentReadingRules.phoneCanReadExactly(note, "ta"))
        assertFalse(ConsentReadingRules.phoneCanReadExactly(note, "bn"))
        assertFalse(ConsentReadingRules.phoneCanReadExactly("आशा घर पर देखभाल चाहती हैं।", "en"))
        // One line in another script is enough to refuse; Punjabi needs Gurmukhi.
        assertFalse(ConsentReadingRules.phoneCanReadExactly("आशा घर पर देखभाल चाहती हैं।\nWhat matters. Home.", "hi"))
        assertTrue(ConsentReadingRules.phoneCanReadExactly("ਆਸ਼ਾ ਘਰ ਵਿੱਚ ਦੇਖਭਾਲ ਚਾਹੁੰਦੀ ਹੈ।", "pa"))
        assertFalse(ConsentReadingRules.phoneCanReadExactly("आशा घर पर देखभाल चाहती हैं।", "pa"))
        // Nothing to read, or an unknown language.
        assertFalse(ConsentReadingRules.phoneCanReadExactly("  \n 12 ", "en"))
        assertFalse(ConsentReadingRules.phoneCanReadExactly(note, "xx"))
    }

    @Test fun aMethodIsRequired() {
        assertEquals(listOf(ConsentReadingProblem.METHOD), ConsentReadingRules.problems(null, "Asha", askWitness = true))
        assertEquals(listOf(ConsentReadingProblem.METHOD), ConsentReadingRules.problems(ConsentReading("MAYBE"), "Asha", askWitness = true))
        assertTrue(ConsentReadingRules.valid(ConsentReading(ConsentReadingRules.SELF), "Asha", askWitness = true))
    }

    @Test fun thePhoneMustReadToTheEndAndAWitnessMustSeeIt() {
        assertTrue(ConsentReadingProblem.NOT_HEARD in ConsentReadingRules.problems(byPhone, "Asha", askWitness = true, heard = false))
        assertTrue(ConsentReadingRules.valid(byPhone, "Asha", askWitness = true, heard = true))
        val noWitness = byPhone.copy(witnessName = "", witnessRelation = "")
        assertEquals(listOf(ConsentReadingProblem.WITNESS_NAME, ConsentReadingProblem.WITNESS_RELATION), ConsentReadingRules.problems(noWitness, "Asha", askWitness = true))
        // When a witness signs separately (goals-of-care sign-off), none is asked for here.
        assertTrue(ConsentReadingRules.valid(noWitness, "Asha", askWitness = false))
    }

    @Test fun whoReadItIsNamedWithTheirRelationship() {
        assertTrue(ConsentReadingRules.valid(bySon, "Asha Sample", askWitness = true))
        assertTrue(ConsentReadingProblem.READER_NAME in ConsentReadingRules.problems(bySon.copy(readerName = " "), "Asha", askWitness = true))
        assertTrue(ConsentReadingProblem.READER_RELATION in ConsentReadingRules.problems(bySon.copy(readerRelation = ""), "Asha", askWitness = true))
        assertTrue(ConsentReadingProblem.READER_IS_SIGNER in ConsentReadingRules.problems(bySon, " ravi  SAMPLE ", askWitness = true))
    }

    @Test fun theWitnessIsSomeoneElse() {
        assertTrue(ConsentReadingProblem.WITNESS_IS_SIGNER in ConsentReadingRules.problems(bySon.copy(witnessName = "Asha Sample"), "asha sample", askWitness = true))
        assertTrue(ConsentReadingProblem.WITNESS_IS_READER in ConsentReadingRules.problems(bySon.copy(witnessName = "Ravi  Sample"), "Asha", askWitness = true))
    }

    @Test fun cleanKeepsOnlyTheFieldsOfTheMethod() {
        val self = ConsentReadingRules.clean(ConsentReading(ConsentReadingRules.SELF, "Ravi", "Son", "hi", "Mohan", "Friend"))
        assertEquals(ConsentReading(ConsentReadingRules.SELF), self)
        val phone = ConsentReadingRules.clean(byPhone.copy(readerName = "Ravi", witnessName = "  Mohan   K "))
        assertEquals("", phone.readerName); assertEquals("Mohan K", phone.witnessName); assertEquals("hi", phone.language)
        assertEquals(ConsentReadingRules.NAME_MAX, ConsentReadingRules.clean(bySon.copy(readerName = "x".repeat(500))).readerName.length)
        assertFalse(ConsentReadingRules.canRead(bySon)); assertTrue(ConsentReadingRules.canRead(ConsentReading(ConsentReadingRules.SELF)))
    }

    @Test fun jsonRoundTrip() {
        listOf(byPhone, bySon, ConsentReading(ConsentReadingRules.SELF, at = "2026-11-25T10:00:00.000Z")).forEach {
            assertEquals(ConsentReadingRules.clean(it), ConsentReadingRules.decode(JSONObject(ConsentReadingRules.encode(it).toString())))
        }
        assertNull(ConsentReadingRules.decode(null))
        assertNull(ConsentReadingRules.decode(JSONObject().put("method", "NOPE")))
    }

    @Test fun summaryNamesWhoReadAndWhoSaw() {
        assertEquals("Read aloud by Ravi Sample (Son) · witnessed by Nurse Sample (Nurse)", ConsentReadingRules.summaryEnglish(bySon))
        assertEquals("Read aloud by the phone · हिन्दी · witnessed by Mohan K (Neighbour)", ConsentReadingRules.summaryEnglish(byPhone))
        assertEquals("Read it themselves", ConsentReadingRules.summaryEnglish(ConsentReading(ConsentReadingRules.SELF)))
        assertTrue(ConsentReadingRules.summaryHindi(bySon).contains("बेटा"))
    }

    @Test fun peopleOnTheRecordAreOfferedOnceWithoutTheSigner() {
        val people = listOf(ConsentPerson("Ravi", "Son"), ConsentPerson(" ravi ", "Son"), ConsentPerson("", "Nurse"), ConsentPerson("Asha", "Self"), ConsentPerson("Mohan", ""))
        assertEquals(listOf("Ravi", "Mohan"), ConsentReadingRules.people(people, exclude = "ASHA").map { it.name })
    }

    @Test fun heardIsTiedToTheTextAndLanguage() {
        assertEquals(ConsentReadingRules.heardKey(note, "hi"), ConsentReadingRules.heardKey(note, "hi"))
        assertNotEquals(ConsentReadingRules.heardKey(note, "hi"), ConsentReadingRules.heardKey(note, "te"))
        assertNotEquals(ConsentReadingRules.heardKey(note, "hi"), ConsentReadingRules.heardKey("$note ", "hi"))
    }

    // ── Goals-of-care sign-off ──

    private fun doctorSigned(): GocSignOffRecord {
        val r = GocSignOffRules.start("n1", note, at, "s1")
        return GocSignOffRules.add(r, note, GocSignatureInput(GocSignOffRules.DOCTOR, "Dr Sample", registrationNo = "KMC-1", consent = true, method = GocSignOffRules.DRAWN, png = png, strokeCount = 2), at = at)
    }

    @Test fun aReadingIsStoredWithTheSignatureAndDecidesCanRead() {
        val r = GocSignOffRules.add(doctorSigned(), note, GocSignatureInput(GocSignOffRules.FAMILY, "Ravi Sample", relation = "Son", consent = true, canRead = true, method = GocSignOffRules.THUMB, png = png, strokeCount = 1,
            readAloud = ConsentReading(ConsentReadingRules.PERSON, "Lata Sample", "Nurse")), at = at)
        val s = r.signatures.last()
        assertEquals(false, s.canRead); assertEquals("Lata Sample", s.readAloud?.readerName)
        // Read to them: a witness must sign before the note locks.
        assertEquals(GocSignOffRules.WITNESS, GocSignOffRules.nextStep(r))
        val back = GocSignOffJson.decode(JSONObject(GocSignOffJson.encode(r).toString()))
        assertEquals(r, back)
    }

    @Test fun anIncompleteReadingIsRefused() {
        try {
            GocSignOffRules.add(doctorSigned(), note, GocSignatureInput(GocSignOffRules.PATIENT, "Asha", consent = true, canRead = false, method = GocSignOffRules.DRAWN, png = png, strokeCount = 1,
                readAloud = ConsentReading(ConsentReadingRules.PERSON, "", "")), at = at)
            throw AssertionError("expected a refusal")
        } catch (e: GocSignOffException) { assertEquals(GocSignOffError.READ_ALOUD_MISSING, e.code) }
    }

    @Test fun theSealCoversTheReadingButIsUnchangedWithoutOne() {
        val plain = GocSignOffRules.add(doctorSigned(), note, GocSignatureInput(GocSignOffRules.PATIENT, "Asha", consent = true, canRead = false, method = GocSignOffRules.DRAWN, png = png, strokeCount = 1), at = at)
        val read = GocSignOffRules.add(doctorSigned(), note, GocSignatureInput(GocSignOffRules.PATIENT, "Asha", consent = true, method = GocSignOffRules.DRAWN, png = png, strokeCount = 1,
            readAloud = ConsentReading(ConsentReadingRules.PHONE, language = "hi", at = "2026-11-25T10:00:00.000Z")), at = at)
        assertFalse(GocSignOffRules.sealInput(plain.noteId, plain.noteSha256, plain.signatures).contains("readAloud"))
        assertTrue(GocSignOffRules.sealInput(read.noteId, read.noteSha256, read.signatures).contains("9:readAloud;5:PHONE;"))
        // Changing who read it after signing breaks the seal.
        val witness = GocSignatureInput(GocSignOffRules.WITNESS, "Nurse Sample", relation = "Nurse", consent = true, method = GocSignOffRules.DRAWN, png = png, strokeCount = 1)
        val locked = GocSignOffRules.lock(GocSignOffRules.add(read, note, witness, at = at), note, at = at)
        assertTrue(GocSignOffRules.intact(locked, note))
        val tampered = locked.copy(signatures = locked.signatures.map { if (it.readAloud != null) it.copy(readAloud = it.readAloud.copy(method = ConsentReadingRules.SELF)) else it })
        assertTrue("SEAL_CHANGED" in GocSignOffRules.verify(tampered, note))
    }

    @Test fun theFormShowsWhoReadTheNote() {
        val reading = ConsentReading(ConsentReadingRules.PERSON, "Lata Sample", "Nurse")
        val so = GocSignOffSummary("s", "seal", "2026-11-25T10:00:00Z", listOf("Dr Sample", "Asha"), listOf("DOCTOR", "PATIENT"), listOf("REG-1", ""), listOf("DRAWN", "THUMB"),
            listOf("2026-11-25T09:59:00Z", "2026-11-25T10:00:00Z"), listOf(null, reading))
        val signer = so.formSigners().first { it.role == "PATIENT" }
        assertTrue(signer.thumb); assertEquals(reading, signer.readAloud)
    }

    // ── Thumbprint photo ──

    @Test fun thumbPhotoFitsAndKeepsItsShape() {
        assertEquals(480 to 360, GocThumbPhoto.fit(4000, 3000))
        assertEquals(270 to 480, GocThumbPhoto.fit(1080, 1920))
        assertEquals(200 to 100, GocThumbPhoto.fit(200, 100))
        assertEquals(0 to 0, GocThumbPhoto.fit(0, 10))
    }

    @Test fun thumbPhotoInkFollowsTheLight() {
        // Ridges darker than the picture's average are ink, in a bright room and in a dim one.
        val bright = intArrayOf(250, 250, 250, 120, 250, 110)
        assertEquals(listOf(false, false, false, true, false, true), GocThumbPhoto.ink(bright).toList())
        val dim = intArrayOf(90, 90, 40, 90, 35, 90)
        assertEquals(listOf(false, false, true, false, true, false), GocThumbPhoto.ink(dim).toList())
        assertEquals(0, GocThumbPhoto.ink(IntArray(0)).size)
        assertEquals(255, GocThumbPhoto.luma(0xFFFFFFFF.toInt())); assertEquals(0, GocThumbPhoto.luma(0xFF000000.toInt()))
    }
}
