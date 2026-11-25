package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The card record and its PDF stay in this phone's private storage. When the profile owner is signed
 * in, a copy of the chosen sections also goes to the Sahara server behind a read-only web link.
 */
internal class EmergencyCardStore(context: Context, namespace: String) {
    private val safe = namespace.ifBlank { "guest" }.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) }
    private val preferences = context.getSharedPreferences("emergency-card-v1-$safe", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "emergency-cards/$safe")
    private fun folder(personId: String) = File(root, personId.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) })
    fun load(personId: String): EmergencyCardRecord? = if (personId.isBlank()) null else preferences.getString(personId, null)?.let { runCatching { EmergencyCardRecord.decode(it) }.getOrNull() }
    fun save(record: EmergencyCardRecord) {
        check(preferences.edit().putString(record.personId, record.encode()).commit()) { "The card could not be saved on this phone." }
        folder(record.personId).listFiles()?.filter { it.name != pdf(record).name }?.forEach(File::delete)
    }
    /** The link part of the name makes a new link produce a new PDF. */
    fun pdf(record: EmergencyCardRecord) = File(folder(record.personId), "sahara-emergency-card-${record.number}-${record.id}${record.link?.let { "-" + it.token.take(8) }.orEmpty()}.pdf")
    fun lockedPdf(record: EmergencyCardRecord) = File(folder(record.personId), "sahara-emergency-card-${record.number}-locked.pdf")
    fun removePerson(personId: String) { preferences.edit().remove(personId).commit(); folder(personId).deleteRecursively() }
}

internal object EmergencyLinkQr {
    /** ECC Q: the link is short, so it can afford more damage tolerance than the text code. */
    fun encode(url: String): QRCode = Encoder.encode(url, ErrorCorrectionLevel.Q, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
}

/**
 * The printed card (clinical team, 24 Nov 2026): ONE A4 page. The QR holds only the web link; beside it the name, card
 * number and version, the valid-until date (3 months) and allergies; then two tables (who to call, current medicines),
 * a short goals-of-care note and the footer. Rows that do not fit are counted ("+3 more on the web copy"), never split
 * onto a second page. Drawn from the card's web copy ([EmergencyCardRecord.snapshot]) so paper and page match.
 */
internal object EmergencyCardPdf {
    private const val W = 595f; private const val H = 842f; private const val M = 36f; private const val RH = 17f

    fun create(file: File, record: EmergencyCardRecord) {
        file.parentFile?.mkdirs()
        val card = record.snapshot.takeIf(String::isNotBlank)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
        val ink = android.graphics.Color.rgb(40, 33, 29); val red = android.graphics.Color.rgb(160, 32, 32)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = ink }
        val strong = Paint(text).apply { isFakeBoldText = true }
        val alert = Paint(strong).apply { color = red }
        val title = Paint(strong).apply { textSize = 17f }
        val nameP = Paint(strong).apply { textSize = 21f }
        val section = Paint(strong).apply { textSize = 11.5f }
        val head = Paint(strong).apply { textSize = 9.5f }
        val small = Paint(text).apply { textSize = 8.5f }
        val black = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
        val redFill = Paint().apply { color = red; style = Paint.Style.FILL }
        val shade = Paint().apply { color = android.graphics.Color.rgb(236, 231, 225); style = Paint.Style.FILL }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(150, 140, 130); strokeWidth = .7f; style = Paint.Style.STROKE }
        fun wrap(value: String, paint: Paint, width: Float): List<String> = value.lines().flatMap { original ->
            val out = mutableListOf<String>(); var remaining = original
            if (remaining.isEmpty()) out += ""
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
                val split = if (count < remaining.length) remaining.lastIndexOf(' ', count).takeIf { it > 0 } ?: count else count
                out += remaining.take(split); remaining = remaining.drop(split).trimStart()
            }
            out
        }
        fun fit(value: String, paint: Paint, width: Float): String {
            if (paint.measureText(value) <= width) return value
            val count = paint.breakText(value, true, width - paint.measureText("…"), null).coerceAtLeast(0)
            return value.take(count).trimEnd() + "…"
        }
        fun lines(value: String, paint: Paint, width: Float, max: Int): List<String> {
            val all = wrap(value, paint, width)
            return if (all.size <= max) all else all.take(max - 1) + fit(all.drop(max - 1).joinToString(" "), paint, width)
        }
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create()); val c = page.canvas
            var y = 46f
            c.drawText("SAHARA EMERGENCY CARD · आपातकालीन कार्ड", M, y, title)
            c.drawRect(M, y + 6f, W - M, y + 8.5f, redFill); y += 20f
            // QR (web link only) on the left; who and which card on the right.
            val link = record.link; val qrSide = 170f; val top = y
            if (link != null) {
                val code = EmergencyLinkQr.encode(link.url); val matrix = code.matrix; val quiet = 4; val cell = qrSide / (matrix.width + 2 * quiet)
                for (row in 0 until matrix.height) for (col in 0 until matrix.width) if (matrix.get(col, row).toInt() == 1)
                    c.drawRect(M + (col + quiet) * cell, top + (row + quiet) * cell, M + (col + quiet + 1) * cell, top + (row + quiet + 1) * cell, black)
            } else {
                c.drawRect(M + 6f, top + 6f, M + qrSide - 6f, top + qrSide - 6f, rule)
                lines("No QR code: the web link was not made. This paper cannot be checked online; call the family or doctor.", small, qrSide - 24f, 6).forEachIndexed { i, l -> c.drawText(l, M + 12f, top + 40f + i * 12f, small) }
            }
            val x = M + qrSide + 16f; val colW = W - M - x; var ry = top + 22f
            fun right(value: String, paint: Paint, max: Int, gap: Float = 13f) = lines(value, paint, colW, max).forEach { c.drawText(it, x, ry, paint); ry += gap }
            val name = card?.optString("name").orEmpty().ifBlank { record.text.lineSequence().firstOrNull { it.startsWith("NAME: ") }?.removePrefix("NAME: ").orEmpty() }
            if (name.isNotBlank()) right(name, nameP, 2, 25f)
            right("Card ${record.number}${link?.let { " · version ${it.version}" }.orEmpty()} · ID ${record.id} · made ${cardDay(record.madeAt)}", strong, 2)
            right("VALID UNTIL ${cardDay(record.validUntil)}. After this date the card may be out of date: check with the family or doctor.", alert, 3)
            card?.takeUnless { it.isNull("allergies") }?.optString("allergies")?.takeIf(String::isNotBlank)?.let { ry += 3f; right("ALLERGIES (as listed by family): $it", alert, 3) }
            ry += 3f
            if (link != null) right("Scan the QR to open this card on sahara.wedevit.in (needs internet). The page says if a newer card exists. · नवीनतम कार्ड के लिए QR स्कैन करें।", small, 4, 11f)
            y = maxOf(top + qrSide, ry) + 12f

            // Footer first, so the tables know how much room they have.
            val notOn = card?.optJSONArray("notIncluded")?.let { a -> (0 until a.length()).map(a::optString) }.orEmpty().ifEmpty { record.leftOut }
            val footer = buildList {
                if (notOn.isNotEmpty()) addAll(lines("Not on this card: " + notOn.joinToString("; ") + ".", small, W - 2 * M, 2))
                addAll(lines("Made on the family's phone from what they recorded in Sahara. Names are self-entered. Not a treatment order, advance directive or hospital record.", small, W - 2 * M, 2))
            }
            val footerTop = H - 34f - footer.size * 11f
            footer.forEachIndexed { i, l -> c.drawText(l, M, footerTop + i * 11f, small) }
            c.drawText("Card ${record.number} · ID ${record.id} · page 1 of 1", M, H - 24f, small)

            fun table(caption: String, headers: List<String>, widths: List<Float>, rows: List<List<String>>) {
                c.drawText(caption, M, y + 11f, section); y += 16f
                val tableTop = y
                fun row(cells: List<String>, paint: Paint, fill: Boolean) {
                    if (fill) c.drawRect(M, y, W - M, y + RH, shade)
                    var cx = M
                    cells.forEachIndexed { i, v -> c.drawText(fit(v, paint, widths[i] - 8f), cx + 4f, y + 12f, paint); cx += widths[i] }
                    y += RH; c.drawLine(M, y, W - M, y, rule)
                }
                c.drawLine(M, y, W - M, y, rule)
                row(headers, head, true)
                rows.forEach { row(it, text, false) }
                var cx = M; c.drawLine(cx, tableTop, cx, y, rule); widths.forEach { cx += it; c.drawLine(cx, tableTop, cx, y, rule) }
                y += 10f
            }
            if (card != null) {
                val sections = EmergencyCardTables.sections(card)
                val goals = card.optJSONObject("goals")?.takeIf { EmergencySection.GOALS in sections }
                val goalsText = goals?.let { g -> listOf("What matters: " to g.optString("whatMatters"), "Next steps: " to g.optString("nextSteps")).filter { it.second.isNotBlank() }.joinToString("  ") { it.first + it.second.replace(Regex("\\s+"), " ").trim() } }.orEmpty()
                val goalLines = if (goals == null) emptyList() else lines(goalsText.ifBlank { "No text in the note." }, text, W - 2 * M, 5)
                val goalsRoom = if (goals == null) 0f else 18f + goalLines.size * 12.5f + 6f
                if (EmergencySection.CONTACTS in sections) {
                    val rows = EmergencyCardTables.contacts(card).map { listOf(it.who, it.name, it.phone) }
                    val shown = if (rows.size <= 7) rows else rows.take(6) + listOf(listOf("+${rows.size - 6} more", "on the web copy (scan the QR)", ""))
                    table("WHO TO CALL · किसे फ़ोन करें", listOf("Who · कौन", "Name · नाम", "Phone · फ़ोन"), listOf(118f, 232f, 173f),
                        shown.ifEmpty { listOf(listOf("Next of kin", EmergencyCardTables.NOT_SAVED, "")) })
                }
                if (EmergencySection.MEDICINES in sections) {
                    val rows = EmergencyCardTables.medicines(card).map { listOf(it.name, it.strength, it.whenTaken) }
                    val room = ((footerTop - 10f - goalsRoom - y - 16f - RH) / RH).toInt().coerceAtLeast(1)
                    val shown = if (rows.size <= room) rows else rows.take(room - 1) + listOf(listOf("+${rows.size - room + 1} more medicines", "scan the QR for the full list", ""))
                    table("CURRENT MEDICINES (as listed by family) · अभी की दवाएँ", listOf("Medicine · दवा", "Strength · मात्रा", "When · कब"), listOf(205f, 112f, 206f),
                        shown.ifEmpty { listOf(listOf("No current medicines listed", "", "")) })
                }
                if (goals != null && y + goalsRoom <= footerTop) {
                    c.drawText(fit("GOALS OF CARE (note v${goals.optInt("version")}, confirmed in app by ${goals.optString("confirmedBy")} on ${cardDay(goals.optLong("confirmedAt"))}) · देखभाल के लक्ष्य", section, W - 2 * M), M, y + 11f, section); y += 18f
                    goalLines.forEach { c.drawText(it, M, y + 9f, text); y += 12.5f }
                }
            } else {
                // A card saved before 24 Nov: its text, cut to this one page.
                val room = ((footerTop - 10f - y) / 12.5f).toInt().coerceAtLeast(1)
                lines(record.body, text, W - 2 * M, room).forEach { c.drawText(it, M, y + 9f, text); y += 12.5f }
            }
            pdf.finishPage(page)
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
    }
}

/** Hands an existing PDF file to Android's print screen (printer or "Save as PDF"). */
private class PdfFilePrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancel: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancel.isCanceled) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), oldAttributes != newAttributes)
    }
    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, callback: WriteResultCallback) {
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(destination).use { out -> file.inputStream().use { it.copyTo(out) } }
            if (cancel.isCanceled) callback.onWriteCancelled() else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) { callback.onWriteFailed(e.message) }
    }
}

private fun cardDay(at: Long) = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

@Composable private fun QrImage(qr: QRCode, description: String, modifier: Modifier = Modifier) {
    val matrix = qr.matrix
    Canvas(modifier.aspectRatio(1f).background(Color.White).semantics { contentDescription = description }) {
        val quiet = 4; val cell = size.width / (matrix.width + 2 * quiet)
        for (row in 0 until matrix.height) for (col in 0 until matrix.width) if (matrix.get(col, row).toInt() == 1)
            drawRect(Color.Black, Offset((col + quiet) * cell, (row + quiet) * cell), Size(cell + .5f, cell + .5f))
    }
}

/** Today: a large QR of the web link (the only code on the card since 24 Nov), or the way to make one. */
@Composable fun EmergencyCardOnToday(vm: CareModel, onRead: (CareVersion) -> Unit = {}) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val t = careText(); val savedTap = rememberSaharaHaptic()
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val acknowledgements by vm.acknowledgements.collectAsStateWithLifecycle(); val trustedAcks by vm.authenticatedConfirmationIds.collectAsStateWithLifecycle()
    val contacts by vm.careContacts.collectAsStateWithLifecycle(); val contactsLoading by vm.careContactsLoading.collectAsStateWithLifecycle()
    val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle(); val owner by vm.isOwner.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val store = remember(vm.storageNamespace) { EmergencyCardStore(context.applicationContext, vm.storageNamespace) }
    val client = remember(vm.cloud) { vm.cloud?.takeUnless { vm.demoMode }?.let(::EmergencyLinkClient) }
    var record by remember(selected) { mutableStateOf(store.load(selected)) }
    var setup by rememberSaveable(selected) { mutableStateOf(false) }
    var fullScreen by rememberSaveable(selected) { mutableStateOf(false) }
    var lockFor by rememberSaveable(selected) { mutableStateOf<String?>(null) }
    var confirmNewLink by rememberSaveable(selected) { mutableStateOf(false) }
    var confirmLinkOff by rememberSaveable(selected) { mutableStateOf(false) }
    var linkProblem by remember(selected) { mutableStateOf<EmergencyLinkProblem?>(null) }
    var linkBusy by remember(selected) { mutableStateOf(false) }
    var linkStatus by remember(selected) { mutableStateOf<EmergencyLinkStatus?>(null) }
    var doctorViews by remember(selected) { mutableStateOf<List<EmergencyCardView>?>(null) }
    var busy by remember(selected) { mutableStateOf(false) }
    // Failures open a small dialog instead of a passing toast; routine "saved" lines stay as the quiet tick.
    var alert by remember(selected) { mutableStateOf<String?>(null) }
    // Results that matter (a file saved, a link made) get a short dialog too, not a passing toast.
    var done by remember(selected) { mutableStateOf<String?>(null) }
    val name = people.firstOrNull { it.id == selected }?.name.orEmpty()
    val source = remember(name, items, versions, acknowledgements, trustedAcks, contacts) {
        // Same profile the care plan prefills from: the shared entry first, else this phone's older copy.
        val profile = PatientProfileCodec.latest(items, selected)?.let { PatientProfileCodec.decode(it.detail) }
            ?: runCatching { CarePatientProfileStore(context, vm.storageNamespace).loadOrNull(selected) }.getOrNull()
        EmergencySource(
            name = name,
            goals = EmergencyCardRules.latestConfirmed(versions.filter { it.personId == selected }, acknowledgements) { vm.cloud == null || it.id in trustedAcks },
            // Next of kin is the first family contact (with a phone first); clinicians last.
            contacts = contacts.filter { it.personId == selected }.sortedWith(compareBy({ when (it.role) { "family" -> 0; "caregiver" -> 1; else -> 2 } }, { it.phone.isBlank() })).map { EmergencyContact(it.name, it.relationship, it.phone, it.role) },
            medicines = items.filter { it.kind == "medicine" && it.personId == selected }.map { EmergencyMedicine(it.title, if (CareEntryCodec.isStructured(it.detail)) "" else it.detail) },
            age = profile?.age, conditions = profile?.illness.orEmpty(), allergies = profile?.allergies.orEmpty(),
            doctor = profile?.let { EmergencyDoctor(it.doctorName.trim(), it.doctorPhone.trim(), it.doctorPlace.trim(), it.doctorLandline.trim()) },
        )
    }
    val ready = loadedFor == selected && !contactsLoading
    val blocked: EmergencyLinkProblem? = when { vm.demoMode -> EmergencyLinkProblem.WALKTHROUGH; client == null -> EmergencyLinkProblem.SIGNED_OUT; !owner -> EmergencyLinkProblem.NOT_OWNER; else -> null }
    val card = record
    val linkQr = remember(card?.link?.token) { card?.link?.let { runCatching { EmergencyLinkQr.encode(it.url) }.getOrNull() } }
    val readNote = source.goals?.let { g -> versions.firstOrNull { it.version == g.version && it.personId == selected } }

    fun keep(next: EmergencyCardRecord) { store.save(next); EmergencyCardPdf.create(store.pdf(next), next) }
    fun ensurePdf(c: EmergencyCardRecord): File = store.pdf(c).takeIf(File::isFile) ?: run { EmergencyCardPdf.create(store.pdf(c), c); store.pdf(c) }

    /**
     * Puts [c] on the server and returns the saved record with its link. Normally a new published version with its own
     * token ([EmergencyLinkClient.publish]); [fresh] = true starts a new link series instead ("Replace link", or a link
     * that was turned off), which stops every older printed QR at once.
     */
    suspend fun syncLink(c: EmergencyCardRecord, fresh: Boolean): EmergencyCardRecord {
        val api = client ?: return c
        val payload = c.webCard(source)
        val next = try {
            if (fresh) c.copy(link = api.create(c.personId, payload)) else c.copy(link = api.publish(c.personId, payload, c.link))
        } catch (e: EmergencyLinkException) {
            if (e.problem == EmergencyLinkProblem.LINK_OFF && c.link != null) withContext(Dispatchers.IO) { keep(c.copy(link = null)) }.also { record = c.copy(link = null) }
            throw e
        }
        if (next !== c) withContext(Dispatchers.IO) { keep(next) }
        linkProblem = null
        linkStatus = runCatching { api.status(c.personId) }.getOrNull()
        return next
    }

    // Someone else's profile (the patient's doctor): read where the web link was opened. Errors leave it hidden.
    LaunchedEffect(selected, owner, client) {
        doctorViews = null
        val api = client ?: return@LaunchedEffect
        if (owner || selected.isBlank()) return@LaunchedEffect
        doctorViews = try { api.views(selected).takeIf { it.live }?.recentViews } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; null }
    }

    // A card without a link gets one as soon as the owner can make it; a live link shows how often it was opened.
    LaunchedEffect(card?.id, card?.link?.token, blocked) {
        val c = card ?: return@LaunchedEffect
        if (blocked != null || client == null) return@LaunchedEffect
        linkBusy = true
        try {
            // Not straight after the family turned the link off: a new link then waits for "Make a new link".
            if (c.link == null) { if (linkProblem != EmergencyLinkProblem.LINK_OFF) record = syncLink(c, fresh = false) }
            else { val s = client.status(c.personId); linkStatus = s; if (!s.live) linkProblem = EmergencyLinkProblem.LINK_OFF }
        } catch (e: EmergencyLinkException) { linkProblem = e.problem }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; linkProblem = EmergencyLinkProblem.OFFLINE }
        finally { linkBusy = false }
    }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val c = record
        if (uri != null && c != null) scope.launch {
            try { withContext(Dispatchers.IO) { val file = ensurePdf(c); (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).use { out -> file.inputStream().use { it.copyTo(out) } } }; savedTap(); done = t("Card PDF saved", "कार्ड PDF सहेजा गया") }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; alert = t("Couldn't save the PDF. Try again.", "PDF सहेजा नहीं जा सका।") }
        }
    }
    val saveLocked = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val c = record; val password = lockFor
        lockFor = null
        if (uri != null && c != null && password != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { val locked = PdfPasswordLock.lock(ensurePdf(c).readBytes(), password); (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).use { it.write(locked) } }
                savedTap(); done = t("Locked PDF saved", "पासवर्ड वाला PDF सहेजा गया")
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; alert = t("Couldn't save the locked PDF. Try again.", "PDF सहेजा नहीं जा सका।") }
        }
    }
    fun share(c: EmergencyCardRecord, password: String? = null) = scope.launch {
        try {
            val file = withContext(Dispatchers.IO) { val plain = ensurePdf(c); if (password == null) plain else store.lockedPdf(c).also { it.writeBytes(PdfPasswordLock.lock(plain.readBytes(), password)) } }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), t("Share the emergency card", "आपातकालीन कार्ड साझा करें")))
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; alert = t("No app to share with.", "साझा करने के लिए ऐप नहीं है।") }
    }
    fun print(c: EmergencyCardRecord) = scope.launch {
        try {
            val file = withContext(Dispatchers.IO) { ensurePdf(c) }
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print("Sahara emergency card ${c.number}", PdfFilePrintAdapter(file, "Sahara-emergency-card-${c.number}.pdf"), null)
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; alert = t("Printing isn't available.", "प्रिंट उपलब्ध नहीं है।") }
    }
    fun remake(sections: Set<EmergencySection>, previous: EmergencyCardRecord?) = scope.launch {
        if (busy) return@launch
        busy = true
        try {
            val made = withContext(Dispatchers.IO) { EmergencyCardRecord.make(selected, source, sections, previous, System.currentTimeMillis()).also(::keep) }
            record = made; setup = false
            if (blocked == null) try { record = syncLink(made, fresh = false) } catch (e: EmergencyLinkException) { linkProblem = e.problem } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; linkProblem = EmergencyLinkProblem.OFFLINE }
            // A first card appears by itself (the QR is the result); an update is confirmed.
            savedTap()
            if (previous != null) done = t("Card updated", "कार्ड अपडेट हुआ")
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; alert = e.message ?: t("Couldn't make the card. Try again.", "कार्ड नहीं बना।") }
        finally { busy = false }
    }

    // "Yes, still the same" (clinical team, 24 Nov): the same card, valid 3 more months. Saved on this phone first, then the
    // same content is published as a new version so the website shows the new date too.
    fun renew(c: EmergencyCardRecord) = scope.launch {
        if (busy) return@launch
        busy = true
        try {
            val renewed = withContext(Dispatchers.IO) { c.renew(System.currentTimeMillis()).also(::keep) }
            record = renewed
            if (blocked == null) try { record = syncLink(renewed, fresh = false) } catch (e: EmergencyLinkException) { linkProblem = e.problem } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; linkProblem = EmergencyLinkProblem.OFFLINE }
            val until = cardDay(renewed.validUntil)
            savedTap()
            done = t("Thank you. The card is now valid until $until. Print the new card.", "धन्यवाद। कार्ड अब $until तक मान्य है। नया कार्ड छापें।")
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; alert = t("Couldn't renew the card. Try again.", "कार्ड आगे नहीं बढ़ाया जा सका। फिर कोशिश करें।") }
        finally { busy = false }
    }

    // No card yet: make one now from the saved records (every part), so the QR shows at once. A tap retries if that failed.
    LaunchedEffect(selected, ready, card == null) {
        if (card == null && ready && selected.isNotBlank() && !busy) remake(EmergencySection.entries.toSet(), null)
    }
    alert?.let { message -> SaharaDialog(message, onDismiss = { alert = null }, tone = SaharaTone.Error, modifier = Modifier.testTag("emergency-card-alert")) }
    done?.let { message -> SaharaDialog(message, onDismiss = { done = null }, tone = SaharaTone.Success, modifier = Modifier.testTag("emergency-card-done")) }
    if (selected.isBlank()) return
    if (card == null) {
        // Emergency red: the red wash with a solid red QR chip.
        ActionCard(SaharaSection.Emergency.wash, modifier = Modifier.fillMaxWidth().testTag("emergency-card-entry"), onClick = { if (ready && !busy) remake(EmergencySection.entries.toSet(), null) }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SaharaIconChip(Icons.Default.QrCode, SaharaSection.Emergency, size = 44); Spacer(Modifier.width(12.dp))
                Text(t("Emergency card", "आपातकालीन कार्ड"), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink, modifier = Modifier.weight(1f))
                if (!ready || busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SaharaSection.Emergency.solid)
                else Icon(Icons.Default.Refresh, null, Modifier.size(19.dp), tint = SaharaSection.Emergency.ink)
            }
        }
    } else {
        // Compared once per change to the card or its sources, not on every recomposition.
        val stale = remember(card, source, ready) { ready && runCatching { card.outOfDate(source) }.getOrDefault(false) }
        val link = card.link
        // The QR stays on white for scanners; the card's solid red edge says Emergency.
        Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = androidx.compose.foundation.BorderStroke(2.dp, SaharaSection.Emergency.solid), modifier = Modifier.fillMaxWidth().testTag("emergency-card")) {
            Column(Modifier.saharaContentSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The QR is only ever the web link (24 Nov): the page reads the card from the server.
                if (linkQr != null) QrImage(linkQr, t("Emergency web link QR code", "आपातकालीन वेब लिंक QR कोड"), Modifier.fillMaxWidth().widthIn(max = 240.dp).clickable(onClickLabel = t("Show full screen", "पूरी स्क्रीन पर दिखाएँ")) { fullScreen = true }.testTag("emergency-card-qr"))
                else Box(Modifier.fillMaxWidth().widthIn(max = 240.dp).aspectRatio(1f).border(2.dp, DoctorHairline, RoundedCornerShape(12.dp)).padding(16.dp).testTag("emergency-card-no-qr"), contentAlignment = Alignment.Center) {
                    if (linkBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Rust)
                    else Text(t("No QR yet: it needs the web link.", "अभी QR नहीं है: इसके लिए वेब लिंक चाहिए।"), fontSize = 15.sp, color = DoctorCardSub, textAlign = TextAlign.Center)
                }
                if (name.isNotBlank()) Text(name, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.testTag("emergency-card-name"))
                Text(t("Scan in an emergency", "आपातकाल में स्कैन करें"), fontSize = 16.sp, color = DoctorCardSub, textAlign = TextAlign.Center)
                val until = cardDay(card.validUntil)
                Text(t("Valid until $until", "$until तक मान्य"), fontSize = 14.sp, color = if (System.currentTimeMillis() > card.validUntil) DoctorStatusWarn else DoctorCardSub, textAlign = TextAlign.Center, modifier = Modifier.testTag("emergency-card-valid-until"))
                // Within 14 days of the valid-until date, or past it: one plain question with two answers (clinical team, 24 Nov).
                // It folds away once answered, instead of vanishing.
                AnimatedVisibility(canEdit && !stale && blocked != EmergencyLinkProblem.NOT_OWNER && card.renewalDue(System.currentTimeMillis()), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Column(Modifier.fillMaxWidth().saharaAppear().background(DoctorStatusWarnBg, RoundedCornerShape(12.dp)).padding(12.dp).testTag("emergency-card-renew"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Is everything on the card still the same?", "क्या कार्ड पर सब कुछ अब भी वैसा ही है?"), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink, modifier = Modifier.a11yHeading())
                    Text(t("Please look at the phone numbers and the medicines. The card is checked every 3 months.", "कृपया फ़ोन नंबर और दवाएँ देख लें। कार्ड हर 3 महीने में जाँचा जाता है।"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk)
                    SaharaPrimaryButton(t("Yes, still the same", "हाँ, सब वैसा ही है"), onClick = { renew(card) }, enabled = !busy, busy = busy, tone = SaharaTone.Teal, modifier = Modifier.fillMaxWidth().testTag("emergency-card-renew-same"))
                    SaharaSecondaryButton(t("Update card", "कार्ड अपडेट करें"), onClick = { setup = true }, enabled = !busy, icon = Icons.Default.Refresh, tone = SaharaTone.Marigold, modifier = Modifier.fillMaxWidth().testTag("emergency-card-renew-update"))
                } }
                // Where the web link was last opened, when the person opening it said.
                if (link != null) linkStatus?.takeIf { it.live }?.recentViews?.firstOrNull()?.let { v -> val at = cardDay(v.at); val place = v.place
                    Text(if (place.isNotBlank()) t("Last opened at $place on $at", "आख़िरी बार $place में $at को खोला गया") else t("Last opened on $at", "आख़िरी बार $at को खोला गया"), fontSize = 14.sp, color = DoctorCardSub, textAlign = TextAlign.Center, modifier = Modifier.testTag("emergency-card-last-opened")) }
                val problem = linkProblem
                // Whether the QR is live: the web link is on, the card is out of date, or there is no link yet.
                // Checking the link and its answer crossfade in place, instead of the spinner and the pill swapping with a jump.
                SaharaStateSwitch(linkBusy, Modifier.fillMaxWidth(), label = "emergency-link-state") { checking -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!checking) FamilyPill(when { link != null && problem == null -> t("Web link on", "वेब लिंक चालू"); stale -> t("Out of date", "पुराना है"); else -> t("No web link", "वेब लिंक नहीं") },
                    when { stale -> FamilyTone.WAIT; link != null && problem == null -> FamilyTone.GOOD; else -> FamilyTone.NEUTRAL }, Modifier.testTag("emergency-card-status"))
                if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Rust)
                else if (problem != null) Column(Modifier.fillMaxWidth().background(DoctorStatusWarnBg, RoundedCornerShape(12.dp)).padding(12.dp).testTag("emergency-link-problem")) {
                    Text(t(problem.en, problem.hi), fontSize = 14.sp, color = DoctorStatusWarn)
                    if (problem == EmergencyLinkProblem.OFFLINE || problem == EmergencyLinkProblem.NOT_SET_UP || problem == EmergencyLinkProblem.LINK_OFF) TextButton(onClick = {
                        scope.launch {
                            linkBusy = true
                            try { record = syncLink(record ?: return@launch, fresh = problem == EmergencyLinkProblem.LINK_OFF); done = t("Web link is live", "वेब लिंक चालू है") }
                            catch (e: EmergencyLinkException) { linkProblem = e.problem; alert = t(e.problem.en, e.problem.hi) }
                            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; linkProblem = EmergencyLinkProblem.OFFLINE }
                            finally { linkBusy = false }
                        }
                    }, enabled = !linkBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (problem == EmergencyLinkProblem.LINK_OFF) t("Make a new link", "नया लिंक बनाएँ") else t("Try again", "फिर कोशिश करें")) }
                }
                } }
                // One main action: Update when the card is out of date, otherwise Share. Print sits beside it; the rest is in "Edit card and PDF".
                if (stale) SaharaPrimaryButton(t("Update card", "कार्ड अपडेट करें"), onClick = { remake(card.sections, card) }, enabled = !busy, busy = busy, tone = SaharaTone.Marigold, icon = Icons.Default.Refresh, modifier = Modifier.fillMaxWidth().testTag("emergency-card-update"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stale) SaharaSecondaryButton(t("Share", "साझा करें"), onClick = { share(card) }, icon = Icons.Default.Share, modifier = Modifier.weight(1f).testTag("emergency-card-share"))
                    else SaharaPrimaryButton(t("Share", "साझा करें"), onClick = { share(card) }, icon = Icons.Default.Share, modifier = Modifier.weight(1f).testTag("emergency-card-share"))
                    SaharaSecondaryButton(t("Print", "प्रिंट करें"), onClick = { print(card) }, icon = Icons.Default.Print, tone = SaharaTone.Indigo, modifier = Modifier.weight(1f).testTag("emergency-card-print"))
                }
                TextButton(onClick = { setup = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("emergency-card-replace")) { Text(t("Edit card and PDF", "कार्ड और PDF बदलें"), color = RustText, fontWeight = FontWeight.Bold) }
                // The family can switch the web link off at any time; printed cards then say the link is off, and a new link can be made later.
                if (link != null && problem == null && client != null && owner && !linkBusy) TextButton(onClick = { confirmLinkOff = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("emergency-link-off")) {
                    Text(t("Turn off the web link", "वेब लिंक बंद करें"), color = DoctorCardSub)
                }
                if (confirmLinkOff) AlertDialog(onDismissRequest = { confirmLinkOff = false },
                    title = { Text(t("Turn off the web link?", "वेब लिंक बंद करें?")) },
                    text = { Text(t("Anyone who scans a printed card will see that the family turned this link off. You can make a new link later.", "छपा कार्ड स्कैन करने वाले देखेंगे कि परिवार ने यह लिंक बंद कर दिया है। आप बाद में नया लिंक बना सकते हैं।")) },
                    confirmButton = { TextButton(onClick = {
                        confirmLinkOff = false
                        scope.launch {
                            linkBusy = true
                            try { client?.revoke(card.personId); val off = card.copy(link = null); withContext(Dispatchers.IO) { keep(off) }; record = off; linkProblem = EmergencyLinkProblem.LINK_OFF; done = t("Web link turned off", "वेब लिंक बंद हो गया") }
                            catch (e: EmergencyLinkException) { alert = t(e.problem.en, e.problem.hi) }
                            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; alert = t(EmergencyLinkProblem.OFFLINE.en, EmergencyLinkProblem.OFFLINE.hi) }
                            finally { linkBusy = false }
                        }
                    }, modifier = Modifier.testTag("emergency-link-off-confirm")) { Text(t("Turn off", "बंद करें"), color = RustText, fontWeight = FontWeight.SemiBold) } },
                    dismissButton = { TextButton(onClick = { confirmLinkOff = false }) { Text(t("Keep it on", "चालू रखें")) } })
            }
        }
        if (fullScreen && linkQr != null) EmergencyQrFullScreen(linkQr, card) { fullScreen = false }
        if (confirmNewLink) AlertDialog(onDismissRequest = { confirmNewLink = false },
            title = { Text(t("Replace the web link?", "लिंक बदलें?")) },
            text = { Text(t("Every card printed with the old link will stop opening. Print the new card.", "पुराने लिंक वाले सभी छपे कार्ड खुलना बंद हो जाएँगे। नया कार्ड छापें।")) },
            confirmButton = { TextButton(onClick = {
                confirmNewLink = false
                scope.launch {
                    linkBusy = true
                    try { record = syncLink(record ?: return@launch, fresh = true); done = t("Link replaced", "लिंक बदल दिया गया") }
                    catch (e: EmergencyLinkException) { linkProblem = e.problem; alert = t(e.problem.en, e.problem.hi) }
                    catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; alert = t(EmergencyLinkProblem.OFFLINE.en, EmergencyLinkProblem.OFFLINE.hi) }
                    finally { linkBusy = false }
                }
            }, modifier = Modifier.testTag("emergency-link-replace-confirm")) { Text(t("Replace link", "लिंक बदलें")) } },
            dismissButton = { TextButton(onClick = { confirmNewLink = false }) { Text(t("Cancel", "रद्द करें")) } })
        lockFor?.let { _ -> PdfPasswordDialog(onDismiss = { lockFor = null }, onSave = { password -> lockFor = password; saveLocked.launch("Sahara-emergency-card-${card.number}-locked.pdf") }, onShare = { password -> lockFor = null; share(card, password) }) }
    }
    // The patient's doctor (not the profile owner) sees where the family's web link was opened. Fetched from the server;
    // nothing shows if this account may not read it or the link is off.
    val otherViews = doctorViews
    if (!owner && otherViews != null) Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("emergency-card-doctor-views"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t("Emergency card web link", "आपातकालीन कार्ड वेब लिंक"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
        if (otherViews.isEmpty()) Text(t("Not opened yet", "अभी तक नहीं खोला गया"), fontSize = 14.sp, color = DoctorCardSub)
        EmergencyCardOpenedList(otherViews)
    }
    if (setup) EmergencyCardSetup(source, name, ready && !busy, card, blocked, onDismiss = { setup = false }, onMake = { sections -> remake(sections, card) }) {
        val c = card
        if (c != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.link != null) linkStatus?.takeIf { it.live }?.let { s -> Text(if (s.views == 0) t("Not opened yet", "अभी तक नहीं खोला गया") else { val views = s.views; val last = s.lastViewedAt?.let { val day = cardDay(it); t(", last on $day", ", आख़िरी बार $day") }.orEmpty(); if (views == 1) t("Opened once$last", "एक बार खोला गया$last") else t("Opened $views times$last", "$views बार खोला गया$last") }, fontSize = 14.sp, color = DoctorCardSub) }
            if (c.link != null) linkStatus?.takeIf { it.live && it.recentViews.isNotEmpty() }?.let { EmergencyCardOpenedList(it.recentViews) }
            // Everything else for the card in one named list, one row each.
            SaharaSectionHeader(t("Card file", "कार्ड की फ़ाइल"), tone = SaharaTone.Marigold, icon = Icons.Default.Description)
            SaharaRegister {
                readNote?.takeIf { EmergencySection.GOALS in c.sections }?.let { note ->
                    SaharaListRow(t("Read note", "नोट पढ़ें"), Modifier.testTag("emergency-card-note"), icon = Icons.Default.Description, tone = SaharaTone.Indigo, onClick = { setup = false; onRead(note) })
                    HorizontalDivider(color = DoctorHairline)
                }
                SaharaListRow(t("Save PDF", "PDF सहेजें"), Modifier.testTag("emergency-card-save"), icon = Icons.Default.FileDownload, tone = SaharaTone.Teal, onClick = { setup = false; savePdf.launch("Sahara-emergency-card-${c.number}.pdf") })
                HorizontalDivider(color = DoctorHairline)
                SaharaListRow(t("Locked PDF", "लॉक PDF"), Modifier.testTag("emergency-card-lock"), icon = Icons.Default.Lock, tone = SaharaTone.Plum, onClick = { setup = false; lockFor = "" })
                if (c.link != null && blocked == null) {
                    HorizontalDivider(color = DoctorHairline)
                    SaharaListRow(t("Replace link", "लिंक बदलें"), Modifier.testTag("emergency-link-replace"), icon = Icons.Default.Refresh, tone = SaharaTone.Rust, onClick = { if (!linkBusy) { setup = false; confirmNewLink = true } })
                }
            }
        }
    }
}

@Composable private fun PdfPasswordDialog(onDismiss: () -> Unit, onSave: (String) -> Unit, onShare: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }; val t = careText()
    val error = PdfPasswordLock.passwordError(password)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(t("Locked PDF", "लॉक PDF")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(password, { password = it }, label = { Text(t("Password", "पासवर्ड")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = password.isNotEmpty() && error != null, supportingText = { if (password.isNotEmpty() && error != null) Text(error) }, modifier = Modifier.fillMaxWidth().testTag("emergency-pdf-password"))
        } },
        confirmButton = { Row { TextButton(onClick = { onShare(password) }, enabled = error == null) { Text(t("Share", "साझा करें")) }; TextButton(onClick = { onSave(password) }, enabled = error == null, modifier = Modifier.testTag("emergency-pdf-password-save")) { Text(t("Save", "सहेजें")) } } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "रद्द करें")) } })
}

/** Full screen for showing hospital staff: the web-link QR only, white, maximum brightness, a gentle SaharaMotion scale-in, tap to close. */
@Composable private fun EmergencyQrFullScreen(linkQr: QRCode, card: EmergencyCardRecord, onClose: () -> Unit) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val t = careText()
    LaunchedEffect(shown.currentState, shown.targetState) { if (!shown.targetState && !shown.currentState && shown.isIdle) onClose() }
    Dialog(onDismissRequest = { shown.targetState = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.let { w -> w.setWindowAnimations(0); w.attributes = w.attributes.apply { screenBrightness = 1f } } }
        AnimatedVisibility(shown,
            // SaharaMotion.tween: no scale-in when the phone's animations are off.
            enter = fadeIn(SaharaMotion.tween(SaharaMotion.ENTER_MS - SaharaMotion.FADE_DELAY_MS, delayMillis = SaharaMotion.FADE_DELAY_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.ENTER_MS, easing = FastOutSlowInEasing), initialScale = .88f),
            exit = fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS, easing = FastOutSlowInEasing)) + scaleOut(SaharaMotion.tween(SaharaMotion.EXIT_MS, easing = FastOutSlowInEasing), targetScale = .94f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
            Column(Modifier.fillMaxSize().background(Color.White).clickable(onClickLabel = t("Close", "बंद करें")) { shown.targetState = false }.padding(20.dp).testTag("emergency-card-fullscreen"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(t("Emergency card", "आपातकालीन कार्ड"), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.a11yHeading())
                Spacer(Modifier.height(16.dp))
                QrImage(linkQr, t("Emergency web link QR code", "आपातकालीन वेब लिंक QR कोड"), Modifier.fillMaxWidth().widthIn(max = 480.dp).border(3.dp, Color.Black))
                Spacer(Modifier.height(16.dp))
                Text(t("Card ${card.number} · ID ${card.id} · made ${cardDay(card.madeAt)}", "कार्ड ${card.number} · ID ${card.id} · बना ${cardDay(card.madeAt)}"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                val until = cardDay(card.validUntil)
                Text(t("Valid until $until", "$until तक मान्य"), fontSize = 16.sp, color = Color.Black, textAlign = TextAlign.Center)
            }
            // A visible way back at the top-left (tapping anywhere still closes it).
            SaharaBackButton({ shown.targetState = false }, Modifier.align(Alignment.TopStart).padding(8.dp), tag = "emergency-fullscreen-back")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EmergencyCardSetup(source: EmergencySource, name: String, ready: Boolean, previous: EmergencyCardRecord?, blocked: EmergencyLinkProblem?, onDismiss: () -> Unit, onMake: (Set<EmergencySection>) -> Unit, extras: @Composable () -> Unit = {}) {
    var chosen by rememberSaveable { mutableStateOf(previous?.sections?.map { it.name }.orEmpty()) }; val t = careText(); val tick = rememberSaharaTick()
    val sections = remember(chosen) { chosen.mapNotNull { s -> EmergencySection.entries.firstOrNull { it.name == s } }.toSet() }
    val preview = remember(source, sections, previous) { if (sections.isEmpty()) null else runCatching { EmergencyCardRules.build(source, sections, (previous?.number ?: 0) + 1, "NEW", System.currentTimeMillis()) }.getOrNull() }
    // The same web copy the printed PDF draws its tables from, so the preview shows what the paper will show.
    val previewCard = remember(source, sections, previous) { if (sections.isEmpty()) null else runCatching { org.json.JSONObject(EmergencyCardRecord.make(previous?.personId ?: "preview", source, sections, previous, System.currentTimeMillis(), id = "NEW").snapshot) }.getOrNull() }
    SaharaScreenSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        LazyColumn(Modifier.fillMaxWidth().testTag("emergency-card-setup"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "band", contentType = "band") { SaharaSectionBand(if (previous == null) t("Emergency card", "आपातकालीन कार्ड") else t("Edit card", "कार्ड बदलें"), SaharaSection.Emergency) }
            item(key = "sections", contentType = "sections") {
                val goals = source.goals
                EmergencySection.entries.forEach { section ->
                    val detail = when (section) {
                        EmergencySection.NAME -> name.ifBlank { t("No name saved", "नाम नहीं है") }
                        EmergencySection.GOALS -> goals?.let { t("Note v${it.version}, confirmed ${cardDay(it.confirmedAt)}", "नोट v${it.version}, पुष्टि ${cardDay(it.confirmedAt)}") + (it.newerUnconfirmedVersion?.let { v -> t(" (newer note v$v not confirmed yet)", " (नया नोट v$v अभी पुष्टि नहीं हुआ)") } ?: "") } ?: t("No confirmed note", "पुष्टि किया गया नोट नहीं")
                        EmergencySection.CONTACTS -> (if (source.contacts.isEmpty()) t("No contacts saved", "कोई संपर्क नहीं है") else t("${source.contacts.size} saved", "${source.contacts.size} सहेजे गए")) + " · " +
                            (EmergencyCardRules.doctorOf(source)?.name?.takeIf(String::isNotBlank)?.let { doctor -> t("Doctor: $doctor", "डॉक्टर: $doctor") } ?: t("No doctor saved", "डॉक्टर सहेजा नहीं गया"))
                        EmergencySection.MEDICINES -> if (source.medicines.isEmpty()) t("No current medicines listed", "कोई दवा नहीं है") else t("${source.medicines.size} current", "${source.medicines.size} दवाएँ")
                    }
                    CheckLine(section.name in chosen, { on -> tick(); chosen = if (on) chosen + section.name else chosen - section.name }, "${t(section.en, section.hi)}\n$detail")
                }
            }
            // The main action right under the choices; the card file options next; the long preview last.
            item(key = "make", contentType = "make") { SaharaPrimaryButton(if (!ready) t("Loading…", "लोड हो रहा है…") else if (previous == null) t("Make card", "कार्ड बनाएँ") else t("Save card", "कार्ड सहेजें"), onClick = { onMake(sections) }, enabled = ready && preview != null, tone = SaharaTone.Marigold, modifier = Modifier.fillMaxWidth().testTag("emergency-card-make")) }
            item(key = "extras", contentType = "extras") { extras() }
            if (preview != null) item(key = "preview", contentType = "preview") { Box(Modifier.animateItem()) { SurfaceCard {
                Text(t("Preview", "झलक"), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (previewCard != null) EmergencyCardTablesView(previewCard, Modifier.testTag("emergency-card-preview"))
                else Text(preview.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("emergency-card-preview"))
                if (preview.leftOut.isNotEmpty()) Text(t("Left out: ", "छोड़ा गया: ") + preview.leftOut.joinToString("; "), style = MaterialTheme.typography.bodySmall, color = RustText)
            } } }
        }
    }
}

/**
 * The printed card's two tables on the phone (24 Nov): who to call (next of kin, treating doctor, hospital) and the
 * current medicines (name, dose, when), read from the same web copy as the PDF ([EmergencyCardTables]), so they match.
 */
@Composable internal fun EmergencyCardTablesView(card: org.json.JSONObject, modifier: Modifier = Modifier) { val t = careText()
    val sections = remember(card) { EmergencyCardTables.sections(card) }
    val contacts = remember(card) { EmergencyCardTables.contacts(card) }
    val medicines = remember(card) { EmergencyCardTables.medicines(card) }
    fun who(label: String) = when (label) {
        "Next of kin" -> t("Next of kin", "निकटतम परिजन"); "Treating doctor" -> t("Treating doctor", "इलाज करने वाले डॉक्टर")
        "Hospital / clinic" -> t("Hospital / clinic", "अस्पताल / क्लिनिक"); else -> label
    }
    fun value(v: String) = if (v == EmergencyCardTables.NOT_SAVED) t("Not saved", "सहेजा नहीं गया") else v
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        card.optString("name").takeIf { it.isNotBlank() && EmergencySection.NAME in sections }?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink) }
        val until = cardDay(card.optLong("validUntil"))
        if (card.has("validUntil")) Text(t("Valid until $until", "$until तक मान्य"), fontSize = 14.sp, color = DoctorCardSub)
        card.optString("allergies").takeIf { it.isNotBlank() && !card.isNull("allergies") }?.let { Text(t("Allergies", "एलर्जी") + ": " + it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RustText) }
        if (EmergencySection.CONTACTS in sections) EmergencyTable(t("Who to call", "किसे फ़ोन करें"), listOf(t("Who", "कौन"), t("Name", "नाम"), t("Phone", "फ़ोन")), listOf(1f, 1.4f, 1.1f),
            contacts.map { listOf(who(it.who), value(it.name), value(it.phone)) }.ifEmpty { listOf(listOf(who("Next of kin"), t("Not saved", "सहेजा नहीं गया"), "")) }, "emergency-card-preview-contacts")
        if (EmergencySection.MEDICINES in sections) EmergencyTable(t("Current medicines", "अभी की दवाएँ"), listOf(t("Medicine", "दवा"), t("Dose", "मात्रा"), t("When", "कब")), listOf(1.4f, 0.9f, 1.2f),
            medicines.map { listOf(it.name, it.strength, it.whenTaken) }.ifEmpty { listOf(listOf(t("No current medicines listed", "कोई दवा नहीं है"), "", "")) }, "emergency-card-preview-medicines")
    }
}

@Composable private fun EmergencyTableRow(cells: List<String>, weights: List<Float>, header: Boolean) {
    Row(Modifier.fillMaxWidth().background(if (header) DoctorHairline.copy(alpha = .5f) else Color.Transparent).padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEachIndexed { i, v -> Text(v, Modifier.weight(weights[i]), fontSize = if (header) 13.sp else 15.sp, lineHeight = 20.sp, fontWeight = if (header) FontWeight.Bold else FontWeight.Normal, color = Ink) }
    }
}

@Composable private fun EmergencyTable(title: String, headers: List<String>, weights: List<Float>, rows: List<List<String>>, tag: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink, modifier = Modifier.a11yHeading())
    Column(Modifier.fillMaxWidth().border(1.dp, DoctorHairline, RoundedCornerShape(8.dp)).testTag(tag)) {
        EmergencyTableRow(headers, weights, header = true)
        rows.forEach { r -> HorizontalDivider(color = DoctorHairline); EmergencyTableRow(r, weights, header = false) }
    }
}
