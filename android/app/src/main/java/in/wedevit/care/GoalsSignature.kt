package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * A goals-of-care sign-off: the image of a signature (drawn on this phone, or a photo of one on paper)
 * tied to one confirmation of one exact note version. It is an acknowledgement, not a legal signature.
 *
 * [imageSha256] is the SHA-256 of the stored image bytes. [bindingSha256] covers the note version id,
 * its content hash, the confirmation, the signer, the kind, the image hash and the time, so a signature
 * can never be shown under a different version or a changed image.
 */
internal data class NoteSignature(
    val id: String,
    val personId: String,
    val ackId: String,
    val versionId: String,
    val contentHash: String,
    val signerName: String,
    val relationship: String,
    val kind: String,
    val imageSha256: String,
    val bindingSha256: String,
    val fileName: String,
    val signedAt: Long,
)

internal object NoteSignatureRules {
    const val SCHEME = "sahara-note-signature-v1"
    const val DRAWN = "drawn"
    const val PHOTO = "photo"
    const val MAX_IMAGE_BYTES = 3_000_000
    private val hex64 = Regex("[0-9a-f]{64}")

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Length-prefixed, like [NoteRules.hash], so no two field lists give the same input. */
    fun binding(personId: String, ackId: String, versionId: String, contentHash: String, signerName: String, relationship: String, kind: String, imageSha256: String, signedAt: Long): String =
        NoteRules.hash(listOf(SCHEME, personId, ackId, versionId, contentHash, signerName, relationship, kind, imageSha256, signedAt.toString()))

    fun binding(s: NoteSignature): String = binding(s.personId, s.ackId, s.versionId, s.contentHash, s.signerName, s.relationship, s.kind, s.imageSha256, s.signedAt)

    /** Signs [ack] for [version] with [image]. Fails unless the confirmation is for this exact version. */
    fun create(version: CareVersion, ack: Acknowledgement, kind: String, image: ByteArray, at: Long, id: String = newId()): NoteSignature {
        require(ack.versionId == version.id && ack.contentHash == version.contentHash) { "This signature is for a different version of the note." }
        require(kind == DRAWN || kind == PHOTO) { "Draw a signature or add a photo of one." }
        require(image.isNotEmpty() && image.size <= MAX_IMAGE_BYTES) { "The signature image is empty or too large." }
        require(at > 0)
        val imageHash = sha256(image)
        val ext = if (kind == DRAWN) "png" else "jpg"
        return NoteSignature(id, version.personId, ack.id, version.id, version.contentHash, ack.name.trim(), ack.relationship, kind, imageHash,
            binding(version.personId, ack.id, version.id, version.contentHash, ack.name.trim(), ack.relationship, kind, imageHash, at), "signature-$id.$ext", at)
    }

    /** True only when the record is bound to [version], its binding hash is intact and, when given, [image] is the signed image. */
    fun verify(s: NoteSignature, version: CareVersion, image: ByteArray? = null): Boolean =
        s.versionId == version.id && s.contentHash == version.contentHash && s.personId == version.personId &&
            hex64.matches(s.imageSha256) && s.bindingSha256 == binding(s) && (image == null || sha256(image) == s.imageSha256)

    /** Signatures to show under [version]: bound to it and to a confirmation of it that is shown. */
    fun forVersion(all: List<NoteSignature>, version: CareVersion, confirmations: List<Acknowledgement>): List<NoteSignature> {
        val acks = confirmations.filter { it.versionId == version.id && it.contentHash == version.contentHash }.associateBy { it.id }
        return all.filter { s -> verify(s, version) && acks[s.ackId]?.let { it.name.trim() == s.signerName && it.relationship == s.relationship } == true }.sortedBy { it.signedAt }
    }

    fun shortHash(hash: String): String = if (hash.length < 16) hash else "${hash.take(8)}…${hash.takeLast(8)}"

    fun encode(s: NoteSignature): JSONObject = JSONObject().put("id", s.id).put("personId", s.personId).put("ackId", s.ackId).put("versionId", s.versionId)
        .put("contentHash", s.contentHash).put("signerName", s.signerName).put("relationship", s.relationship).put("kind", s.kind)
        .put("imageSha256", s.imageSha256).put("bindingSha256", s.bindingSha256).put("fileName", s.fileName).put("signedAt", s.signedAt).put("scheme", SCHEME)

    fun decode(o: JSONObject): NoteSignature {
        require(o.getString("scheme") == SCHEME)
        val s = NoteSignature(o.getString("id"), o.getString("personId"), o.getString("ackId"), o.getString("versionId"), o.getString("contentHash"),
            o.getString("signerName"), o.getString("relationship"), o.getString("kind"), o.getString("imageSha256"), o.getString("bindingSha256"), o.getString("fileName"), o.getLong("signedAt"))
        require(s.fileName.matches(Regex("signature-[A-Za-z0-9-]+\\.(png|jpg)")))
        return s
    }
}

/** Drawn strokes in 0..1 coordinates of the pad. */
internal object SignatureInk {
    /** Enough ink to be a signature rather than a stray tap: total stroke length at least [minLength] pad widths. */
    fun enough(strokes: List<List<Pair<Float, Float>>>, minLength: Float = 0.35f): Boolean =
        strokes.sumOf { stroke -> stroke.zipWithNext { a, b -> Math.hypot((b.first - a.first).toDouble(), (b.second - a.second).toDouble()) }.sum() } >= minLength
}

/** Signature images and their records, on this phone only, per account and person. Removed with the person. */
internal class NoteSignatureStore(private val root: File) {
    private fun folder(personId: String) = File(root, personId.also { require(runCatching { UUID.fromString(it) }.isSuccess) })
    private fun index(personId: String) = File(folder(personId), "signatures.json")

    fun list(personId: String): List<NoteSignature> {
        val file = index(personId); if (!file.isFile) return emptyList()
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { runCatching { NoteSignatureRules.decode(array.getJSONObject(it)) }.getOrNull() }.filter { it.personId == personId }
    }

    fun image(s: NoteSignature): File = File(folder(s.personId), s.fileName)

    /** Image bytes when the file is there and still matches its hash, otherwise null. */
    fun checkedImage(s: NoteSignature): ByteArray? = image(s).takeIf { it.isFile }?.readBytes()?.takeIf { NoteSignatureRules.sha256(it) == s.imageSha256 }

    @Synchronized fun save(s: NoteSignature, bytes: ByteArray) {
        require(NoteSignatureRules.sha256(bytes) == s.imageSha256 && s.bindingSha256 == NoteSignatureRules.binding(s)) { "The signature changed before it was saved. Sign again." }
        val dir = folder(s.personId).apply { mkdirs() }
        val image = File(dir, s.fileName)
        writeAtomically(image, bytes)
        try {
            val all = list(s.personId).filter { it.id != s.id } + s
            writeAtomically(index(s.personId), JSONArray().apply { all.forEach { put(NoteSignatureRules.encode(it)) } }.toString().toByteArray())
        } catch (failure: Exception) { image.delete(); throw failure }
    }

    fun removePerson(personId: String) { folder(personId).deleteRecursively() }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        check(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) { "The signature could not be saved on this phone." }
    }

    companion object {
        fun of(context: android.content.Context, namespace: String) =
            NoteSignatureStore(File(context.filesDir, "note-signatures/${namespace.ifBlank { "guest" }.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) }}"))
    }
}
