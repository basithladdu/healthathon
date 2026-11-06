package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.time.Instant
import java.util.zip.CRC32
import java.util.zip.Inflater

class CareGocSignOffTest {
    private val note = "Sample note. Asha wants to be cared for at home. No CPR."
    private val at = Instant.parse("2026-11-04T10:00:00Z").toEpochMilli()
    private val png = GocMonoPng.encode(8, 2, BooleanArray(16) { it % 2 == 0 })
    private fun doctor(name: String = "Dr Sample", reg: String = "KMC-123") = GocSignatureInput(GocSignOffRules.DOCTOR, name, registrationNo = reg, consent = true, method = GocSignOffRules.DRAWN, png = png, strokeCount = 2)
    private fun patient(role: String = GocSignOffRules.PATIENT, name: String = "Asha Sample", relation: String? = null, canRead: Boolean? = true, consent: Boolean = true, method: String = GocSignOffRules.DRAWN) =
        GocSignatureInput(role, name, relation = relation, consent = consent, canRead = canRead, method = method, png = png, strokeCount = 1)
    private fun witness(consent: Boolean = true) = GocSignatureInput(GocSignOffRules.WITNESS, "Nurse Sample", relation = "Nurse", consent = consent, method = GocSignOffRules.DRAWN, png = png, strokeCount = 1)
    private fun signed(input: GocSignatureInput = patient()): GocSignOffRecord {
        val r = GocSignOffRules.add(GocSignOffRules.start("note-1", note, at, "id-1"), note, doctor(), at = at)
        return GocSignOffRules.add(r, note, input, at = at)
    }
    private fun refused(code: GocSignOffError, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (e: GocSignOffException) { assertEquals(code, e.code) }
    }

    @Test fun hashesAndTimesMatchTheWeb() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", GocSignOffRules.sha256("abc".toByteArray()))
        assertNotEquals(GocSignOffRules.noteSha256(note), GocSignOffRules.noteSha256("$note "))
        assertEquals("2026-11-04T10:00:00.000Z", GocSignOffRules.iso(at))
    }

    /** The same record as the last test in prototype/tests/goc-signoff.test.mjs, so both platforms seal alike. */
    @Test fun crossPlatformVector() {
        val text = "Sample note. आशा wants to be cared for at home."
        val img = "abc".toByteArray()
        var r = GocSignOffRules.start("note-1", text, at, "id-1")
        r = GocSignOffRules.add(r, text, GocSignatureInput(GocSignOffRules.DOCTOR, "Dr Sample", registrationNo = "KMC-123", consent = true, method = GocSignOffRules.DRAWN, png = img, strokeCount = 2), at = at)
        r = GocSignOffRules.add(r, text, GocSignatureInput(GocSignOffRules.FAMILY, "Ravi Sample", relation = "Son", canRead = false, consent = true, method = GocSignOffRules.THUMB, png = img, strokeCount = 1), at = at)
        r = GocSignOffRules.add(r, text, GocSignatureInput(GocSignOffRules.WITNESS, "Nurse Sample", relation = "Nurse", consent = true, method = GocSignOffRules.DRAWN, png = img, strokeCount = 3), at = at)
        r = GocSignOffRules.lock(r, text, at = at)
        assertEquals("data:image/png;base64,YWJj", r.signatures[0].imageDataUrl)
        assertEquals("7e18e357acfbef1d4d20863c4607fb1854d60171e16541595918ac58bc6f8467", r.noteSha256)
        assertEquals("230b8a152907bf0d012847342958d40f05e1964e368d0875dd31e999e17f1adc", r.sealSha256)
    }

    @Test fun signingGoesInOrderThenLocks() {
        val r0 = GocSignOffRules.start("note-1", note, at, "id-1")
        assertEquals(GocSignOffRules.DOCTOR, GocSignOffRules.nextStep(r0))
        refused(GocSignOffError.WRONG_ORDER) { GocSignOffRules.add(r0, note, patient()) }
        refused(GocSignOffError.NOT_READY) { GocSignOffRules.lock(r0, note) }
        val r2 = signed()
        assertEquals(GocSignOffRules.LOCK, GocSignOffRules.nextStep(r2))
        assertEquals("Self", r2.signatures[1].relation)
        assertEquals(r0.noteSha256, r2.signatures[1].noteSha256)
        assertEquals(GocSignOffRules.sha256(png), r2.signatures[0].imageSha256)
    }

    @Test fun namesRegistrationRelationConsentAndReadingAreRequired() {
        val r0 = GocSignOffRules.start("note-1", note, at)
        refused(GocSignOffError.NAME_BLANK) { GocSignOffRules.add(r0, note, doctor(name = "   ")) }
        refused(GocSignOffError.REGISTRATION_BLANK) { GocSignOffRules.add(r0, note, doctor(reg = " ")) }
        refused(GocSignOffError.NO_SIGNATURE) { GocSignOffRules.add(r0, note, doctor().copy(png = ByteArray(0))) }
        val r1 = GocSignOffRules.add(r0, note, doctor())
        refused(GocSignOffError.NAME_BLANK) { GocSignOffRules.add(r1, note, patient(name = "")) }
        refused(GocSignOffError.CONSENT_MISSING) { GocSignOffRules.add(r1, note, patient(consent = false)) }
        refused(GocSignOffError.CAN_READ_MISSING) { GocSignOffRules.add(r1, note, patient(canRead = null)) }
        refused(GocSignOffError.RELATION_BLANK) { GocSignOffRules.add(r1, note, patient(role = GocSignOffRules.FAMILY, relation = " ")) }
        assertEquals("Daughter", GocSignOffRules.add(r1, note, patient(role = GocSignOffRules.FAMILY, relation = "Daughter")).signatures[1].relation)
    }

    @Test fun thumbOrCannotReadNeedsAWitness() {
        val thumb = signed(patient(method = GocSignOffRules.THUMB))
        assertEquals(listOf(GocSignOffRules.DOCTOR, GocSignOffRules.PATIENT_OR_FAMILY, GocSignOffRules.WITNESS), GocSignOffRules.requiredSteps(thumb.signatures))
        refused(GocSignOffError.NOT_READY) { GocSignOffRules.lock(thumb, note) }
        refused(GocSignOffError.CONSENT_MISSING) { GocSignOffRules.add(thumb, note, witness(consent = false)) }
        assertEquals(GocSignOffRules.LOCK, GocSignOffRules.nextStep(GocSignOffRules.add(thumb, note, witness())))
        assertEquals(GocSignOffRules.WITNESS, GocSignOffRules.nextStep(signed(patient(canRead = false))))
        assertEquals(GocSignOffRules.LOCK, GocSignOffRules.nextStep(signed()))
    }

    @Test fun lockSealsAndRefusesMore() {
        val locked = GocSignOffRules.lock(signed(), note, at = at)
        assertEquals(GocSignOffRules.LOCKED, locked.status)
        assertEquals(GocSignOffRules.sealSha256(locked), locked.sealSha256)
        assertNull(GocSignOffRules.nextStep(locked))
        refused(GocSignOffError.LOCKED) { GocSignOffRules.add(locked, note, witness()) }
        refused(GocSignOffError.LOCKED) { GocSignOffRules.lock(locked, note) }
        refused(GocSignOffError.LOCKED) { GocSignOffRules.restart(locked, note) }
        assertTrue(GocSignOffRules.intact(locked, note))
        assertEquals(emptyList<String>(), GocSignOffRules.verify(locked, note))
    }

    @Test fun verifyCatchesChangedNoteImageOrName() {
        val locked = GocSignOffRules.lock(signed(), note)
        assertEquals(listOf("NOTE_CHANGED"), GocSignOffRules.verify(locked, "$note!"))
        val image = locked.copy(signatures = locked.signatures.mapIndexed { i, s -> if (i == 0) s.copy(imageDataUrl = GocSignOffRules.dataUrl(byteArrayOf(1))) else s })
        assertTrue("IMAGE_CHANGED" in GocSignOffRules.verify(image, note))
        val name = locked.copy(signatures = locked.signatures.mapIndexed { i, s -> if (i == 1) s.copy(name = "Someone else") else s })
        assertEquals(listOf("SEAL_CHANGED"), GocSignOffRules.verify(name, note))
        assertEquals(listOf("SEAL_MISSING"), GocSignOffRules.verify(signed(), note))
        assertFalse(GocSignOffRules.intact(signed(), note))
    }

    @Test fun noSignatureOnAChangedNote() {
        val r1 = GocSignOffRules.add(GocSignOffRules.start("note-1", note), note, doctor())
        refused(GocSignOffError.NOTE_CHANGED) { GocSignOffRules.add(r1, "$note Edited.", patient()) }
        refused(GocSignOffError.NOTE_CHANGED) { GocSignOffRules.lock(signed(), "$note Edited.") }
    }

    @Test fun changingTheNoteAfterAnySignatureInvalidatesAll() {
        val moved = GocSignOffRules.reconcile(GocSignOffRules.start("note-1", note, at, "id-1"), "$note Edited.")
        assertNull(moved.invalidated)
        assertEquals("id-1", moved.record.id)
        assertEquals(GocSignOffRules.noteSha256("$note Edited."), moved.record.noteSha256)
        assertNull(GocSignOffRules.reconcile(signed(), note).invalidated)
        listOf(signed(), GocSignOffRules.lock(signed(), note)).forEach { before ->
            val result = GocSignOffRules.reconcile(before, "$note Edited.", at)
            val old = result.invalidated!!
            assertEquals(GocSignOffRules.INVALIDATED, old.status)
            assertEquals(2, old.signatures.size)
            assertTrue(old.invalidatedReason!!.contains("no longer valid"))
            assertEquals(GocSignOffRules.OPEN, result.record.status)
            assertTrue(result.record.signatures.isEmpty())
            assertNotEquals(before.id, result.record.id)
            assertNull(GocSignOffRules.nextStep(old))
            refused(GocSignOffError.INVALIDATED) { GocSignOffRules.add(old, "$note Edited.", doctor()) }
            assertTrue("INVALIDATED" in GocSignOffRules.verify(old, note))
        }
        assertEquals(GocSignOffRules.INVALIDATED, GocSignOffRules.restart(signed(), note).invalidated!!.status)
    }

    @Test fun jsonRoundTripsWithTheSharedFieldNamesAndMergeGuardsLockedRecords() {
        val locked = GocSignOffRules.lock(signed(patient(role = GocSignOffRules.FAMILY, relation = "Son", canRead = true)), note)
        val json = GocSignOffJson.encode(locked)
        listOf("scheme", "id", "noteId", "noteSha256", "status", "createdAt", "signatures", "lockedAt", "sealSha256", "invalidatedAt", "invalidatedReason").forEach { assertTrue(it, json.has(it)) }
        val s = json.getJSONArray("signatures").getJSONObject(0)
        listOf("role", "name", "registrationNo", "relation", "consent", "canRead", "method", "imageDataUrl", "imageSha256", "strokeCount", "signedAt", "noteSha256").forEach { assertTrue(it, s.has(it)) }
        assertTrue(s.isNull("relation") && s.isNull("canRead"))
        val back = GocSignOffJson.decode(JSONObject(json.toString()))
        assertEquals(locked, back)
        assertTrue(GocSignOffRules.intact(back, note))
        val all = GocSignOffJson.decodeAll(GocSignOffJson.encodeAll(listOf(locked)))
        assertEquals(listOf(locked), all)
        try { GocSignOffJson.merge(all, locked.copy(signatures = emptyList())); fail("locked changed") } catch (expected: IllegalStateException) {}
        val invalid = GocSignOffRules.reconcile(locked, "$note Edited.").invalidated!!
        val merged = GocSignOffJson.merge(all, invalid)
        assertEquals(GocSignOffRules.INVALIDATED, merged.single().status)
        try { GocSignOffJson.merge(merged, invalid.copy(status = GocSignOffRules.OPEN)); fail("invalidated changed") } catch (expected: IllegalStateException) {}
    }

    @Test fun monoPngIsAValidOneBitImage() {
        val bytes = GocMonoPng.encode(10, 3, BooleanArray(30) { it % 3 == 0 })
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), bytes.take(8).map { it.toInt() and 0xFF })
        val input = DataInputStream(bytes.inputStream()); input.skipBytes(8)
        assertEquals(13, input.readInt()); input.skipBytes(4)
        assertEquals(10, input.readInt()); assertEquals(3, input.readInt())
        assertEquals(1, input.readByte().toInt()); assertEquals(0, input.readByte().toInt())
        val crc = CRC32().apply { update(bytes, 12, 17) }.value.toInt()
        input.skipBytes(3); assertEquals(crc, input.readInt())
        val idat = input.readInt(); input.skipBytes(4)
        val data = ByteArray(idat); input.readFully(data)
        val inflater = Inflater().apply { setInput(data) }
        val out = ByteArrayOutputStream(); val buffer = ByteArray(64)
        while (!inflater.finished()) out.write(buffer, 0, inflater.inflate(buffer))
        val rows = out.toByteArray()
        assertEquals(9, rows.size)
        assertEquals(0, rows[0].toInt())
        assertEquals(0b01101101, rows[1].toInt() and 0xFF)
        // No thumb switch: a held press counts, a stray tap does not, and a drawn line counts whether held or not.
        assertTrue(GocSignatureInk.ready(listOf(listOf(0.5f to 0.5f)), held = true))
        assertFalse(GocSignatureInk.ready(listOf(listOf(0.5f to 0.5f)), held = false))
        assertTrue(GocSignatureInk.ready(listOf(listOf(0f to 0.5f, 0.5f to 0.5f)), held = false))
        assertFalse(GocSignatureInk.ready(emptyList(), held = true))
    }

    @Test fun aHeldPressIsAThumbImpressionAndADrawnLineIsNot() {
        val press = listOf(listOf(0.5f to 0.5f, 0.51f to 0.5f))
        val drawn = listOf(listOf(0f to 0.5f, 0.5f to 0.5f))
        assertTrue(GocSignatureInk.thumbPress(press, held = true))
        assertFalse(GocSignatureInk.thumbPress(press, held = false))
        assertFalse(GocSignatureInk.thumbPress(drawn, held = true))
        assertFalse(GocSignatureInk.thumbPress(emptyList(), held = true))
        assertTrue(GocSignatureInk.HOLD_MS in 300L..1000L)
    }
}
