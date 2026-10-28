package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class GoalsSignatureTest {
    private val person = Person(id = "6f0c2c3e-5d0a-4a4b-9c39-2f6d7e1b8a10", name = "Patient")
    private val note = NoteRules.publish(person, Conversation(person.id, source = "I want to be home.", priorities = "I want to be home."), "Doctor", true, 1)
    private val patient = NoteRules.acknowledge(note, note, "Asha", "Patient", true)
    private val image = byteArrayOf(1, 2, 3, 4, 5)
    private val at = 1_761_640_000_000L

    @Test fun imageHashIsSha256OfTheStoredBytes() {
        // Known vector: SHA-256 of "abc".
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", NoteSignatureRules.sha256("abc".toByteArray()))
        val s = NoteSignatureRules.create(note, patient, NoteSignatureRules.DRAWN, image, at)
        assertEquals(NoteSignatureRules.sha256(image), s.imageSha256)
        assertEquals(note.contentHash, s.contentHash)
        assertEquals(note.id, s.versionId)
        assertEquals("Asha", s.signerName)
        assertTrue(s.fileName.endsWith(".png"))
        assertTrue(NoteSignatureRules.create(note, patient, NoteSignatureRules.PHOTO, image, at).fileName.endsWith(".jpg"))
    }

    @Test fun bindingCoversVersionHashSignerImageAndTime() {
        val s = NoteSignatureRules.create(note, patient, NoteSignatureRules.DRAWN, image, at)
        assertTrue(NoteSignatureRules.verify(s, note, image))
        assertFalse(NoteSignatureRules.verify(s, note, byteArrayOf(9)))
        listOf(s.copy(signerName = "Someone"), s.copy(relationship = "Family / carer"), s.copy(signedAt = at + 1), s.copy(kind = NoteSignatureRules.PHOTO),
            s.copy(imageSha256 = NoteSignatureRules.sha256(byteArrayOf(9))), s.copy(ackId = newId()), s.copy(contentHash = "0".repeat(64)), s.copy(versionId = newId()))
            .forEach { assertFalse(it.toString(), NoteSignatureRules.verify(it, note)) }
    }

    @Test fun signatureNeverMovesToAnotherVersion() {
        val v2 = NoteRules.publish(person, Conversation(person.id, source = "I want to be home with my family.", priorities = "Home with family."), "Doctor", true, 2)
        val s = NoteSignatureRules.create(note, patient, NoteSignatureRules.DRAWN, image, at)
        assertFalse(NoteSignatureRules.verify(s, v2))
        assertTrue(NoteSignatureRules.forVersion(listOf(s), v2, listOf(patient)).isEmpty())
        // A confirmation from another version cannot be signed against this one.
        assertThrows(IllegalArgumentException::class.java) { NoteSignatureRules.create(v2, patient, NoteSignatureRules.DRAWN, image, at) }
        // Same id, edited content: the content hash no longer matches.
        assertThrows(IllegalArgumentException::class.java) { NoteSignatureRules.create(note.copy(contentHash = "f".repeat(64)), patient, NoteSignatureRules.DRAWN, image, at) }
    }

    @Test fun rejectsEmptyOversizedOrUnknownKind() {
        assertThrows(IllegalArgumentException::class.java) { NoteSignatureRules.create(note, patient, NoteSignatureRules.DRAWN, ByteArray(0), at) }
        assertThrows(IllegalArgumentException::class.java) { NoteSignatureRules.create(note, patient, NoteSignatureRules.DRAWN, ByteArray(NoteSignatureRules.MAX_IMAGE_BYTES + 1), at) }
        assertThrows(IllegalArgumentException::class.java) { NoteSignatureRules.create(note, patient, "id-card", image, at) }
    }

    @Test fun shownOnlyUnderTheMatchingConfirmation() {
        val family = NoteRules.acknowledge(note, note, "Ravi", "Family / carer", true, listOf(patient))
        val p = NoteSignatureRules.create(note, patient, NoteSignatureRules.DRAWN, image, at)
        val f = NoteSignatureRules.create(note, family, NoteSignatureRules.PHOTO, image, at + 5)
        assertEquals(listOf(p, f), NoteSignatureRules.forVersion(listOf(f, p), note, listOf(patient, family)))
        // The family confirmation is not shown (e.g. not account-verified): its signature is not shown either.
        assertEquals(listOf(p), NoteSignatureRules.forVersion(listOf(f, p), note, listOf(patient)))
    }

    @Test fun inkMustBeMoreThanATap() {
        assertFalse(SignatureInk.enough(emptyList()))
        assertFalse(SignatureInk.enough(listOf(listOf(0.5f to 0.5f))))
        assertFalse(SignatureInk.enough(listOf(listOf(0.1f to 0.5f, 0.2f to 0.5f))))
        assertTrue(SignatureInk.enough(listOf(listOf(0.1f to 0.5f, 0.3f to 0.4f), listOf(0.4f to 0.5f, 0.6f to 0.6f))))
    }

    @Test fun storeKeepsImageAndRecordAndDetectsChanges() {
        val dir = Files.createTempDirectory("signatures").toFile()
        try {
            val store = NoteSignatureStore(dir)
            val s = NoteSignatureRules.create(note, patient, NoteSignatureRules.DRAWN, image, at)
            assertThrows(IllegalArgumentException::class.java) { store.save(s, byteArrayOf(7)) }
            store.save(s, image)
            assertEquals(listOf(s), store.list(person.id))
            assertArrayEquals(image, store.checkedImage(s))
            store.image(s).writeBytes(byteArrayOf(7))
            assertNull(store.checkedImage(s))
            store.removePerson(person.id)
            assertTrue(store.list(person.id).isEmpty())
            assertThrows(IllegalArgumentException::class.java) { store.list("../escape") }
        } finally { dir.deleteRecursively() }
    }

    @Test fun recordRoundTripsThroughJson() {
        val s = NoteSignatureRules.create(note, patient, NoteSignatureRules.PHOTO, image, at)
        assertEquals(s, NoteSignatureRules.decode(org.json.JSONObject(NoteSignatureRules.encode(s).toString())))
        assertThrows(IllegalArgumentException::class.java) { NoteSignatureRules.decode(NoteSignatureRules.encode(s).put("fileName", "../x.png")) }
    }
}
