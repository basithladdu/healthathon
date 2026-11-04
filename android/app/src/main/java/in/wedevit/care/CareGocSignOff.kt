package `in`.wedevit.care

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

/*
 * Goals-of-care note sign-off: the doctor, then the patient or a family member, then a witness when needed, sign on
 * this phone; then the note version is locked. Twin of prototype/app/goc-signoff.ts and goc-signature-pad.tsx.
 *
 * JSON field names, the same on web and Android (do not rename):
 *   GocSignOffRecord {
 *     scheme: "sahara-goc-signoff-v1", id, noteId,
 *     noteSha256        SHA-256 of the note text (UTF-8) that was read and signed
 *     status            "OPEN" | "LOCKED" | "INVALIDATED"
 *     createdAt         ISO-8601 UTC with milliseconds
 *     signatures        GocSignature[] in signing order
 *     lockedAt, sealSha256, invalidatedAt, invalidatedReason   null until set
 *   }
 *   GocSignature {
 *     role              "DOCTOR" | "PATIENT" | "FAMILY" | "WITNESS"
 *     name              never blank
 *     registrationNo    DOCTOR only, never blank there; otherwise null
 *     relation          PATIENT "Self"; FAMILY e.g. "Daughter" (never blank); WITNESS optional; DOCTOR null
 *     consent           PATIENT/FAMILY: "I have read or heard this note and it records what we discussed";
 *                       WITNESS: saw the note read out and the person sign; DOCTOR: true
 *     canRead           PATIENT/FAMILY only, otherwise null
 *     method            "DRAWN" | "THUMB"
 *     imageDataUrl      "data:image/png;base64,..." 1-bit PNG, at most 600 px wide
 *     imageSha256       SHA-256 of the PNG bytes
 *     strokeCount, signedAt (ISO-8601), noteSha256 (the note hash this signature was made on)
 *   }
 * Seal: sealSha256 = SHA-256 of the UTF-8 bytes of [GocSignOffRules.sealInput]: each part as "<length>:<value>;"
 * (length in UTF-16 units, the same as JavaScript), parts = scheme, noteId, noteSha256, then per signature role, name,
 * registrationNo, relation, consent (1/0), canRead (1/0/-), method, imageSha256, strokeCount, signedAt, noteSha256.
 *
 * Kept on this phone only, like the care plan ([CarePlanStore]): app-private SharedPreferences "goc-signoff-v1-<account>",
 * one JSON value per note id holding every sign-off for that note (invalidated ones stay as history). Nothing is sent anywhere.
 */

internal data class GocSignature(
    val role: String, val name: String, val registrationNo: String?, val relation: String?, val consent: Boolean, val canRead: Boolean?,
    val method: String, val imageDataUrl: String, val imageSha256: String, val strokeCount: Int, val signedAt: String, val noteSha256: String,
)

internal data class GocSignOffRecord(
    val id: String, val noteId: String, val noteSha256: String, val status: String, val createdAt: String,
    val signatures: List<GocSignature> = emptyList(), val lockedAt: String? = null, val sealSha256: String? = null,
    val invalidatedAt: String? = null, val invalidatedReason: String? = null, val scheme: String = GocSignOffRules.SCHEME,
)

/** What the person signing typed and drew; the hashes and the time are filled in by [GocSignOffRules.add]. */
internal data class GocSignatureInput(
    val role: String, val name: String, val registrationNo: String? = null, val relation: String? = null, val consent: Boolean,
    val canRead: Boolean? = null, val method: String, val png: ByteArray, val strokeCount: Int,
)

internal enum class GocSignOffError { NOTE_CHANGED, LOCKED, INVALIDATED, WRONG_ORDER, NAME_BLANK, REGISTRATION_BLANK, RELATION_BLANK, CONSENT_MISSING, CAN_READ_MISSING, NO_SIGNATURE, NOT_READY }
internal class GocSignOffException(val code: GocSignOffError) : IllegalStateException(code.name)

/** A changed note always gives a fresh [record]; [invalidated] is the earlier one with its signatures, when there were any. */
internal data class GocReconcile(val record: GocSignOffRecord, val invalidated: GocSignOffRecord?)

internal object GocSignOffRules {
    const val SCHEME = "sahara-goc-signoff-v1"
    const val OPEN = "OPEN"; const val LOCKED = "LOCKED"; const val INVALIDATED = "INVALIDATED"
    const val DOCTOR = "DOCTOR"; const val PATIENT = "PATIENT"; const val FAMILY = "FAMILY"; const val WITNESS = "WITNESS"
    const val PATIENT_OR_FAMILY = "PATIENT_OR_FAMILY"; const val LOCK = "LOCK"
    const val DRAWN = "DRAWN"; const val THUMB = "THUMB"
    const val NAME_MAX = 120
    const val PNG_PREFIX = "data:image/png;base64,"
    val DEFAULT_STEPS = listOf(DOCTOR, PATIENT_OR_FAMILY)
    const val INVALIDATED_REASON = "The note was changed after it was signed. All signatures on the earlier text are no longer valid. Read the new note together and sign again."
    const val RESTARTED_REASON = "The signatures were started again before the note was locked."

    private val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    /** ISO-8601 UTC with milliseconds, the same text as JavaScript's toISOString(). */
    fun iso(at: Long): String = iso.format(Instant.ofEpochMilli(at))
    fun isoNow(): String = iso(System.currentTimeMillis())

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    /** The note is hashed exactly as given, so any change counts. */
    fun noteSha256(noteText: String): String = sha256(noteText.toByteArray(Charsets.UTF_8))

    fun dataUrl(png: ByteArray): String = PNG_PREFIX + Base64.getEncoder().encodeToString(png)
    fun dataUrlBytes(dataUrl: String): ByteArray? =
        if (!dataUrl.startsWith(PNG_PREFIX) || dataUrl.length <= PNG_PREFIX.length) null else runCatching { Base64.getDecoder().decode(dataUrl.substring(PNG_PREFIX.length)) }.getOrNull()

    private fun part(value: String) = "${value.length}:$value;"
    fun sealInput(noteId: String, noteSha256: String, signatures: List<GocSignature>): String = buildString {
        append(part(SCHEME)); append(part(noteId)); append(part(noteSha256))
        signatures.forEach { s ->
            append(part(s.role)); append(part(s.name)); append(part(s.registrationNo ?: "")); append(part(s.relation ?: ""))
            append(part(if (s.consent) "1" else "0")); append(part(when (s.canRead) { null -> "-"; true -> "1"; false -> "0" }))
            append(part(s.method)); append(part(s.imageSha256)); append(part(s.strokeCount.toString())); append(part(s.signedAt)); append(part(s.noteSha256))
        }
    }
    fun sealSha256(r: GocSignOffRecord): String = sha256(sealInput(r.noteId, r.noteSha256, r.signatures).toByteArray(Charsets.UTF_8))

    fun start(noteId: String, noteText: String, at: Long = System.currentTimeMillis(), id: String = UUID.randomUUID().toString()) =
        GocSignOffRecord(id, noteId, noteSha256(noteText), OPEN, iso(at))

    fun witnessRequired(signatures: List<GocSignature>, steps: List<String> = DEFAULT_STEPS): Boolean =
        WITNESS in steps || signatures.any { (it.role == PATIENT || it.role == FAMILY) && (it.method == THUMB || it.canRead == false) }

    fun requiredSteps(signatures: List<GocSignature>, steps: List<String> = DEFAULT_STEPS): List<String> =
        listOfNotNull(DOCTOR, PATIENT_OR_FAMILY.takeIf { it in steps }, WITNESS.takeIf { witnessRequired(signatures, steps) })

    private fun stepOf(role: String) = when (role) { DOCTOR -> DOCTOR; WITNESS -> WITNESS; else -> PATIENT_OR_FAMILY }

    /** The next step to sign, [LOCK] when everyone has signed, or null once locked or invalidated. */
    fun nextStep(r: GocSignOffRecord, steps: List<String> = DEFAULT_STEPS): String? {
        if (r.status != OPEN) return null
        val done = r.signatures.map { stepOf(it.role) }.toSet()
        return requiredSteps(r.signatures, steps).firstOrNull { it !in done } ?: LOCK
    }

    private fun fail(code: GocSignOffError): Nothing = throw GocSignOffException(code)

    private fun checkOpen(r: GocSignOffRecord, noteText: String) {
        if (r.status == LOCKED) fail(GocSignOffError.LOCKED)
        if (r.status == INVALIDATED) fail(GocSignOffError.INVALIDATED)
        if (noteSha256(noteText) != r.noteSha256) fail(GocSignOffError.NOTE_CHANGED)
    }

    /** Adds one signature. Refused when the note text is not the one this sign-off started on, or anything is missing. */
    fun add(r: GocSignOffRecord, noteText: String, input: GocSignatureInput, steps: List<String> = DEFAULT_STEPS, at: Long = System.currentTimeMillis()): GocSignOffRecord {
        checkOpen(r, noteText)
        if (input.role !in setOf(DOCTOR, PATIENT, FAMILY, WITNESS) || nextStep(r, steps) != stepOf(input.role)) fail(GocSignOffError.WRONG_ORDER)
        val name = input.name.trim().take(NAME_MAX)
        if (name.isEmpty()) fail(GocSignOffError.NAME_BLANK)
        val registrationNo = if (input.role == DOCTOR) input.registrationNo.orEmpty().trim() else null
        if (input.role == DOCTOR && registrationNo.isNullOrEmpty()) fail(GocSignOffError.REGISTRATION_BLANK)
        val relation = when (input.role) { PATIENT -> "Self"; DOCTOR -> null; else -> input.relation.orEmpty().trim().take(NAME_MAX).ifEmpty { null } }
        if (input.role == FAMILY && relation == null) fail(GocSignOffError.RELATION_BLANK)
        val signer = input.role == PATIENT || input.role == FAMILY
        if (signer && input.canRead == null) fail(GocSignOffError.CAN_READ_MISSING)
        if (input.role != DOCTOR && !input.consent) fail(GocSignOffError.CONSENT_MISSING)
        if (input.png.isEmpty() || input.strokeCount < 1) fail(GocSignOffError.NO_SIGNATURE)
        val signature = GocSignature(input.role, name, registrationNo, relation, if (input.role == DOCTOR) true else input.consent, if (signer) input.canRead else null,
            if (input.method == THUMB) THUMB else DRAWN, dataUrl(input.png), sha256(input.png), input.strokeCount, iso(at), r.noteSha256)
        return r.copy(signatures = r.signatures + signature)
    }

    /** Locks the note version once every required person has signed, and computes the seal. */
    fun lock(r: GocSignOffRecord, noteText: String, steps: List<String> = DEFAULT_STEPS, at: Long = System.currentTimeMillis()): GocSignOffRecord {
        checkOpen(r, noteText)
        if (nextStep(r, steps) != LOCK) fail(GocSignOffError.NOT_READY)
        return r.copy(status = LOCKED, lockedAt = iso(at), sealSha256 = sealSha256(r))
    }

    /**
     * Call whenever the note text may have changed. Same text: nothing happens. Changed with no signatures: the sign-off
     * moves to the new text. Changed after any signature, or after locking: the old record becomes INVALIDATED, with
     * every signature on it, and a fresh OPEN record starts for the new text.
     */
    fun reconcile(r: GocSignOffRecord, noteText: String, at: Long = System.currentTimeMillis()): GocReconcile {
        val sha = noteSha256(noteText)
        if (r.status == INVALIDATED) return GocReconcile(if (sha == r.noteSha256) r else start(r.noteId, noteText, at), null)
        if (sha == r.noteSha256) return GocReconcile(r, null)
        if (r.status == OPEN && r.signatures.isEmpty()) return GocReconcile(r.copy(noteSha256 = sha), null)
        return GocReconcile(start(r.noteId, noteText, at), r.copy(status = INVALIDATED, invalidatedAt = iso(at), invalidatedReason = INVALIDATED_REASON))
    }

    /** The doctor starts the signatures again. Open signatures are kept as INVALIDATED; a locked record cannot be discarded. */
    fun restart(r: GocSignOffRecord, noteText: String, at: Long = System.currentTimeMillis()): GocReconcile {
        if (r.status == LOCKED) fail(GocSignOffError.LOCKED)
        val fresh = start(r.noteId, noteText, at)
        if (r.status == INVALIDATED || r.signatures.isEmpty()) return GocReconcile(fresh, null)
        return GocReconcile(fresh, r.copy(status = INVALIDATED, invalidatedAt = iso(at), invalidatedReason = RESTARTED_REASON))
    }

    /** Problems: NOTE_CHANGED, SIGNATURE_ON_OTHER_TEXT, IMAGE_CHANGED, SEAL_MISSING, SEAL_CHANGED, INVALIDATED. Empty and LOCKED means intact. */
    fun verify(r: GocSignOffRecord, noteText: String): List<String> {
        val problems = linkedSetOf<String>()
        if (r.status == INVALIDATED) problems += "INVALIDATED"
        if (noteSha256(noteText) != r.noteSha256) problems += "NOTE_CHANGED"
        r.signatures.forEach { s ->
            if (s.noteSha256 != r.noteSha256) problems += "SIGNATURE_ON_OTHER_TEXT"
            val bytes = dataUrlBytes(s.imageDataUrl)
            if (bytes == null || sha256(bytes) != s.imageSha256) problems += "IMAGE_CHANGED"
        }
        if (r.sealSha256 == null) problems += "SEAL_MISSING" else if (sealSha256(r) != r.sealSha256) problems += "SEAL_CHANGED"
        return problems.toList()
    }

    fun intact(r: GocSignOffRecord, noteText: String): Boolean = r.status == LOCKED && verify(r, noteText).isEmpty()
}

internal object GocSignOffJson {
    private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    fun encode(s: GocSignature): JSONObject = JSONObject().put("role", s.role).put("name", s.name).put("registrationNo", s.registrationNo.orNull())
        .put("relation", s.relation.orNull()).put("consent", s.consent).put("canRead", s.canRead.orNull()).put("method", s.method)
        .put("imageDataUrl", s.imageDataUrl).put("imageSha256", s.imageSha256).put("strokeCount", s.strokeCount).put("signedAt", s.signedAt).put("noteSha256", s.noteSha256)

    fun encode(r: GocSignOffRecord): JSONObject = JSONObject().put("scheme", r.scheme).put("id", r.id).put("noteId", r.noteId).put("noteSha256", r.noteSha256)
        .put("status", r.status).put("createdAt", r.createdAt).put("signatures", JSONArray(r.signatures.map(::encode)))
        .put("lockedAt", r.lockedAt.orNull()).put("sealSha256", r.sealSha256.orNull()).put("invalidatedAt", r.invalidatedAt.orNull()).put("invalidatedReason", r.invalidatedReason.orNull())

    fun signature(o: JSONObject) = GocSignature(o.getString("role"), o.getString("name"), o.str("registrationNo"), o.str("relation"), o.getBoolean("consent"),
        if (o.isNull("canRead")) null else o.getBoolean("canRead"), o.getString("method"), o.getString("imageDataUrl"), o.getString("imageSha256"),
        o.getInt("strokeCount"), o.getString("signedAt"), o.getString("noteSha256"))

    fun decode(o: JSONObject): GocSignOffRecord {
        require(o.getString("scheme") == GocSignOffRules.SCHEME)
        val status = o.getString("status").also { require(it in setOf(GocSignOffRules.OPEN, GocSignOffRules.LOCKED, GocSignOffRules.INVALIDATED)) }
        val list = o.getJSONArray("signatures")
        return GocSignOffRecord(o.getString("id"), o.getString("noteId"), o.getString("noteSha256"), status, o.getString("createdAt"),
            (0 until list.length()).map { signature(list.getJSONObject(it)) }, o.str("lockedAt"), o.str("sealSha256"), o.str("invalidatedAt"), o.str("invalidatedReason"))
    }

    fun encodeAll(records: List<GocSignOffRecord>): String = JSONObject().put("records", JSONArray(records.map(::encode))).toString()
    fun decodeAll(json: String?): List<GocSignOffRecord> {
        val array = json?.let { runCatching { JSONObject(it).getJSONArray("records") }.getOrNull() } ?: return emptyList()
        return (0 until array.length()).mapNotNull { runCatching { decode(array.getJSONObject(it)) }.getOrNull() }
    }

    /** Adds or replaces (by id). A LOCKED record changes only to INVALIDATED; an INVALIDATED one never changes. */
    fun merge(records: List<GocSignOffRecord>, record: GocSignOffRecord): List<GocSignOffRecord> {
        val old = records.firstOrNull { it.id == record.id } ?: return records + record
        check(!(old.status == GocSignOffRules.LOCKED && record.status != GocSignOffRules.INVALIDATED)) { "A locked sign-off cannot be changed." }
        check(old.status != GocSignOffRules.INVALIDATED) { "An invalidated sign-off cannot be changed." }
        return records.map { if (it.id == record.id) record else it }
    }
}

/** On this phone only, the same way as the care plan: app-private SharedPreferences, one JSON value per note. */
internal class GocSignOffStore(context: Context, namespace: String) {
    private val safe = namespace.ifBlank { "guest" }.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) }
    private val preferences = context.getSharedPreferences("goc-signoff-v1-$safe", Context.MODE_PRIVATE)
    /** Every sign-off for [noteId], oldest first; the last one is the current one. */
    fun load(noteId: String): List<GocSignOffRecord> = GocSignOffJson.decodeAll(preferences.getString(noteId, null)).filter { it.noteId == noteId }
    @Synchronized fun save(record: GocSignOffRecord): List<GocSignOffRecord> {
        val all = GocSignOffJson.merge(load(record.noteId), record)
        check(preferences.edit().putString(record.noteId, GocSignOffJson.encodeAll(all)).commit()) { "The sign-off could not be saved on this phone." }
        return all
    }
    fun removeNote(noteId: String) { preferences.edit().remove(noteId).commit() }
}

// ---------------------------------------------------------------------------

private fun gocErrorText(t: (String, String) -> String, code: GocSignOffError): String = when (code) {
    GocSignOffError.NOTE_CHANGED -> t("The note has changed. Read the new note together before signing.", "नोट बदल गया है। साइन करने से पहले नया नोट साथ में पढ़ें।")
    GocSignOffError.LOCKED -> t("This note version is locked. No more signatures can be added.", "नोट का यह संस्करण लॉक है। अब और हस्ताक्षर नहीं जुड़ सकते।")
    GocSignOffError.INVALIDATED -> t("These signatures are no longer valid. Sign again.", "ये हस्ताक्षर अब मान्य नहीं हैं। फिर से साइन करें।")
    GocSignOffError.WRONG_ORDER -> t("Sign in order: doctor, then patient or family, then witness.", "क्रम से साइन करें: डॉक्टर, फिर मरीज़ या परिवार, फिर गवाह।")
    GocSignOffError.NAME_BLANK -> t("Write the name of the person signing.", "साइन करने वाले का नाम लिखें।")
    GocSignOffError.REGISTRATION_BLANK -> t("Write the doctor’s registration number.", "डॉक्टर का रजिस्ट्रेशन नंबर लिखें।")
    GocSignOffError.RELATION_BLANK -> t("Write how the family member is related to the patient.", "लिखें कि परिवार के सदस्य का मरीज़ से क्या रिश्ता है।")
    GocSignOffError.CONSENT_MISSING -> t("Tick the box to confirm before signing.", "साइन करने से पहले पुष्टि के लिए बॉक्स पर टिक करें।")
    GocSignOffError.CAN_READ_MISSING -> t("Say whether they can read the note themselves.", "बताएँ कि क्या वे नोट ख़ुद पढ़ सकते हैं।")
    GocSignOffError.NO_SIGNATURE -> t("Sign or give a thumb impression on the pad.", "जगह पर साइन करें या अंगूठा लगाएँ।")
    GocSignOffError.NOT_READY -> t("Someone still needs to sign.", "अभी किसी के हस्ताक्षर बाक़ी हैं।")
}

private fun gocRoleText(t: (String, String) -> String, role: String): String = when (role) {
    GocSignOffRules.DOCTOR -> t("Doctor", "डॉक्टर")
    GocSignOffRules.PATIENT -> t("Patient", "मरीज़")
    GocSignOffRules.FAMILY -> t("Family member", "परिवार के सदस्य")
    else -> t("Witness", "गवाह")
}

@Composable private fun GocChoice(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) DoctorStatusGoodBg else Paper).border(if (selected) 1.5.dp else 1.dp, if (selected) DoctorStatusGood else DoctorHairline, RoundedCornerShape(14.dp))
        .selectable(selected, onClick = onSelect, role = Role.RadioButton).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(start = 4.dp)); Text(label, Modifier.padding(start = 4.dp), fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = DoctorCardInk)
    }
}

@Composable private fun GocTick(label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, onValueChange = onChange, role = Role.Checkbox).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null); Text(label, Modifier.padding(start = 8.dp), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
    }
}

/** One signer's step heading: a Rust dot and the step's own words ("1. Doctor"). */
@Composable private fun GocStepHeading(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Rust, CircleShape)); Spacer(Modifier.width(10.dp))
        Text(text, fontFamily = SaharaTitleFont, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading())
    }
}

/** A one-line field in the app's style: 14 corners, paper fill. */
@Composable private fun GocSignField(value: String, onChange: (String) -> Unit, label: String, tag: String? = null) {
    OutlinedTextField(value, onChange, singleLine = true, label = { Text(label) }, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper),
        modifier = Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier))
}

@Composable private fun GocSignedRow(s: GocSignature) { val t = careText()
    val bitmap = remember(s.imageDataUrl) { GocSignOffRules.dataUrlBytes(s.imageDataUrl)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, DoctorHairline)) {
            if (bitmap != null) Image(bitmap, t("Signature of ", "हस्ताक्षर: ") + s.name, Modifier.width(120.dp).padding(4.dp), contentScale = ContentScale.Fit)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = DoctorStatusGood, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(gocRoleText(t, s.role), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DoctorStatusGood)
            }
            Text(s.name + (if (s.role == GocSignOffRules.FAMILY || s.role == GocSignOffRules.WITNESS) s.relation?.let { " ($it)" }.orEmpty() else ""), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
            s.registrationNo?.let { Text(t("Reg. no.", "रजि. नं.") + " " + it, fontSize = 14.sp, color = DoctorCardSub) }
            Text((if (s.method == GocSignOffRules.THUMB) t("Thumb impression", "अंगूठे का निशान") else t("Signed", "हस्ताक्षर")) + " · " +
                runCatching { signedAtLabel(Instant.parse(s.signedAt).toEpochMilli()) }.getOrDefault(s.signedAt), fontSize = 14.sp, color = DoctorCardSub)
        }
    }
}

/**
 * The whole sign-off for one note. [noteText] is the exact text read out or read together; any change to it after a
 * signature invalidates every signature (kept as history) and starts again. [onComplete] runs once, when locked.
 */
@Composable internal fun GocSignOff(
    noteId: String, noteText: String, store: GocSignOffStore, onComplete: (GocSignOffRecord) -> Unit,
    modifier: Modifier = Modifier, steps: List<String> = GocSignOffRules.DEFAULT_STEPS, onInvalidated: (GocSignOffRecord) -> Unit = {},
    /** Prefills, so the usual case is: sign, sign, done. The registration number is remembered on this phone. */
    doctorPrefill: String = "", signerPrefill: String = "",
    /** Lock as soon as the last person has signed: the signature is the confirmation. */
    autoLock: Boolean = true,
    /** Shows the note text again at the patient step. Off when the form is already on screen above. */
    showNote: Boolean = true,
) { val t = careText()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("goc-signoff-doctor", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val complete by rememberUpdatedState(onComplete)
    val invalidatedCallback by rememberUpdatedState(onInvalidated)
    var record by remember(noteId) { mutableStateOf<GocSignOffRecord?>(null) }
    var invalidatedCount by remember(noteId) { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    var doctorName by remember { mutableStateOf(doctorPrefill) }
    var registrationNo by remember { mutableStateOf(prefs.getString("registration", "").orEmpty()) }
    var signerRole by remember { mutableStateOf(GocSignOffRules.PATIENT) }
    var signerName by remember { mutableStateOf(signerPrefill) }
    var relation by remember { mutableStateOf("") }
    var canRead by remember { mutableStateOf<Boolean?>(true) }
    var witnessName by remember { mutableStateOf("") }
    var witnessRole by remember { mutableStateOf("") }
    var witnessConsent by remember { mutableStateOf(false) }

    suspend fun persist(r: GocSignOffRecord) {
        saveFailed = withContext(Dispatchers.IO) { runCatching { store.save(r) }.isFailure }
    }

    // Load, then follow the note text: a change after any signature invalidates them all.
    LaunchedEffect(noteId, noteText) {
        val current = record ?: withContext(Dispatchers.IO) { runCatching { store.load(noteId) }.getOrDefault(emptyList()) }.lastOrNull()
            ?.takeIf { it.status != GocSignOffRules.INVALIDATED }
            ?: GocSignOffRules.start(noteId, noteText).also { persist(it) }
        val result = GocSignOffRules.reconcile(current, noteText)
        result.invalidated?.let { persist(it); invalidatedCount = it.signatures.size; invalidatedCallback(it) }
        if (result.record != current) persist(result.record)
        record = result.record
    }

    fun sign(input: GocSignatureInput): Boolean {
        val base = record ?: return false
        return try {
            val next = GocSignOffRules.add(base, noteText, input, steps)
            record = next; error = null; invalidatedCount = null
            if (input.role == GocSignOffRules.DOCTOR) input.registrationNo?.takeIf(String::isNotBlank)?.let { prefs.edit().putString("registration", it).apply() }
            val locked = if (autoLock && GocSignOffRules.nextStep(next, steps) == GocSignOffRules.LOCK) runCatching { GocSignOffRules.lock(next, noteText, steps) }.getOrNull() else null
            if (locked != null) record = locked
            scope.launch { persist(next); if (locked != null) { persist(locked); complete(locked) } }
            true
        } catch (e: GocSignOffException) { error = gocErrorText(t, e.code); false }
    }

    val r = record
    Column(modifier.fillMaxWidth().testTag("goc-signoff"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (r == null) { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), color = Rust, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(t("Opening the sign-off…", "साइन-ऑफ़ खुल रहा है…"), color = DoctorCardSub, fontSize = 15.sp) }; return@Column }
        val locked = r.status == GocSignOffRules.LOCKED
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (locked) Icons.Default.Lock else Icons.Default.Edit, null, tint = if (locked) DoctorStatusGood else RustText); Spacer(Modifier.width(10.dp))
            Text(if (locked) t("Note signed and locked", "नोट पर हस्ताक्षर हुए और लॉक हुआ") else t("Sign the note", "नोट पर हस्ताक्षर"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading())
        }
        invalidatedCount?.let { n ->
            Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(t("The note was changed after it was signed. All $n signatures on the earlier text are no longer valid. Read the new note together and sign again.",
                    "साइन होने के बाद नोट बदला गया। पुराने नोट पर किए सभी $n हस्ताक्षर अब मान्य नहीं हैं। नया नोट साथ में पढ़ें और फिर से साइन करें।"), color = DoctorStatusRest, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(12.dp).testTag("goc-signoff-invalidated"))
            }
        }
        if (saveFailed) Text(t("This could not be saved on this phone. Try again.", "यह इस फ़ोन पर सेव नहीं हो सका। फिर कोशिश करें।"), color = RustText, fontSize = 15.sp)
        r.signatures.forEachIndexed { index, s -> if (index > 0) HorizontalDivider(color = DoctorHairline); GocSignedRow(s) }
        if (r.signatures.isNotEmpty() && !locked) HorizontalDivider(color = DoctorHairline)

        when (GocSignOffRules.nextStep(r, steps)) {
            GocSignOffRules.DOCTOR -> {
                GocStepHeading(t("1. Doctor", "1. डॉक्टर"))
                GocSignField(doctorName, { doctorName = it.take(GocSignOffRules.NAME_MAX) }, t("Name", "नाम"), "goc-doctor-name")
                GocSignField(registrationNo, { registrationNo = it.take(60) }, t("Registration no.", "रजिस्ट्रेशन नंबर"), "goc-doctor-reg")
                GocSignaturePad(allowThumb = false, onDone = { p -> sign(GocSignatureInput(GocSignOffRules.DOCTOR, doctorName, registrationNo = registrationNo, consent = true, method = p.method, png = p.png, strokeCount = p.strokeCount)) })
            }
            GocSignOffRules.PATIENT_OR_FAMILY -> {
                GocStepHeading(t("2. Patient or family member", "2. मरीज़ या परिवार के सदस्य"))
                if (showNote) Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, DoctorHairline)) {
                    Text(noteText, Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(12.dp), fontSize = 15.sp, lineHeight = 21.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GocChoice(t("Patient", "मरीज़"), signerRole == GocSignOffRules.PATIENT, { signerRole = GocSignOffRules.PATIENT }, Modifier.weight(1f))
                    GocChoice(t("Family member", "परिवार के सदस्य"), signerRole == GocSignOffRules.FAMILY, { signerRole = GocSignOffRules.FAMILY }, Modifier.weight(1f))
                }
                GocSignField(signerName, { signerName = it.take(GocSignOffRules.NAME_MAX) }, t("Name", "नाम"), "goc-signer-name")
                if (signerRole == GocSignOffRules.FAMILY) GocSignField(relation, { relation = it.take(GocSignOffRules.NAME_MAX) }, t("Relation to the patient", "मरीज़ से रिश्ता"), "goc-signer-relation")
                GocTick(t("The note was read to them (a witness also signs)", "नोट उन्हें पढ़कर सुनाया गया (एक गवाह भी साइन करेंगे)"), canRead == false, { canRead = !it }, "goc-signer-read-aloud")
                Surface(color = Cream, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                    Text(t("I have read or heard this note and it records what we discussed", "मैंने यह नोट पढ़ा या सुना है और इसमें वही लिखा है जिस पर हमने बात की"), fontFamily = SaharaTitleFont, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.padding(16.dp).testTag("goc-signer-consent"))
                }
                // Signing under that line is the agreement: one tap, "Agree and sign".
                GocSignaturePad(doneLabel = t("Agree and sign", "सहमत हूँ, साइन करें"), onDone = { p -> sign(GocSignatureInput(signerRole, signerName, relation = relation, consent = true, canRead = canRead, method = p.method, png = p.png, strokeCount = p.strokeCount)) })
            }
            GocSignOffRules.WITNESS -> {
                GocStepHeading(t("3. Witness", "3. गवाह"))
                Text(when {
                    r.signatures.any { it.method == GocSignOffRules.THUMB } -> t("A thumb impression was used, so a witness also signs.", "अंगूठे का निशान लगा है, इसलिए एक गवाह भी साइन करेंगे।")
                    r.signatures.any { it.canRead == false } -> t("The signer cannot read the note, so a witness also signs.", "साइन करने वाले नोट नहीं पढ़ सकते, इसलिए एक गवाह भी साइन करेंगे।")
                    else -> t("A witness also signs.", "एक गवाह भी साइन करेंगे।")
                }, color = DoctorCardSub, fontSize = 15.sp, lineHeight = 21.sp)
                GocSignField(witnessName, { witnessName = it.take(GocSignOffRules.NAME_MAX) }, t("Name", "नाम"), "goc-witness-name")
                GocSignField(witnessRole, { witnessRole = it.take(GocSignOffRules.NAME_MAX) }, t("Role (optional)", "भूमिका (वैकल्पिक)"))
                GocTick(t("I saw the note read out and the person sign or give their thumb impression", "मैंने नोट पढ़कर सुनाते और व्यक्ति को साइन करते या अंगूठा लगाते देखा"), witnessConsent, { witnessConsent = it }, "goc-witness-consent")
                GocSignaturePad(allowThumb = false, onDone = { p -> sign(GocSignatureInput(GocSignOffRules.WITNESS, witnessName, relation = witnessRole, consent = witnessConsent, method = p.method, png = p.png, strokeCount = p.strokeCount)) })
            }
            GocSignOffRules.LOCK -> Button(onClick = {
                try {
                    val locked = GocSignOffRules.lock(r, noteText, steps)
                    record = locked; error = null
                    scope.launch { persist(locked); complete(locked) }
                } catch (e: GocSignOffException) { error = gocErrorText(t, e.code) }
            }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-signoff-lock")) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(t("Lock this note version", "नोट का यह संस्करण लॉक करें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            else -> {}
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp, modifier = Modifier.testTag("goc-signoff-error").announce()) }
        if (r.status == GocSignOffRules.OPEN && r.signatures.isNotEmpty()) OutlinedButton(onClick = {
            val result = GocSignOffRules.restart(r, noteText)
            record = result.record; error = null
            scope.launch { result.invalidated?.let { persist(it) }; persist(result.record) }
        }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Refresh, null, tint = RustText, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Start the signatures again", "हस्ताक्षर फिर से शुरू करें"), color = RustText) }
        if (locked) Surface(color = DoctorStatusGoodBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lock, null, tint = DoctorStatusGood, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(t("Locked on", "लॉक हुआ:") + " " + runCatching { signedAtLabel(Instant.parse(r.lockedAt.orEmpty()).toEpochMilli()) }.getOrDefault(r.lockedAt.orEmpty()) + ". " +
                        t("Any change to the note or a signature will show.", "नोट या किसी हस्ताक्षर में बदलाव दिख जाएगा।"), color = DoctorStatusGood, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f).testTag("goc-signoff-locked"))
                }
                Text("SHA-256 " + NoteSignatureRules.shortHash(r.sealSha256.orEmpty()), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = DoctorCardSub, modifier = Modifier.padding(start = 28.dp))
            }
        }
    }
}
