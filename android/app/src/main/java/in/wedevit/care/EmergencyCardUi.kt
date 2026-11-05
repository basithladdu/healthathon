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

/** Real local PDF of the card, drawn with the same PdfDocument approach as [DoctorWorkflowPdf]. */
internal object EmergencyCardPdf {
    fun create(file: File, record: EmergencyCardRecord, qr: QRCode) {
        file.parentFile?.mkdirs()
        val ink = android.graphics.Color.rgb(56, 46, 41)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = ink }
        val heading = Paint(text).apply { textSize = 18f; isFakeBoldText = true }
        val strong = Paint(text).apply { textSize = 12f; isFakeBoldText = true }
        val small = Paint(text).apply { textSize = 9f }
        val black = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
        val pdf = PdfDocument()
        var number = 0; var page: PdfDocument.Page? = null; var y = 0f
        fun start() { number++; page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create()); y = 48f }
        fun finish() { page!!.canvas.drawText("Card ${record.number} · ID ${record.id} · page $number", 42f, 812f, small); pdf.finishPage(page!!) }
        fun line(value: String, paint: Paint = text, gap: Float = 16f, x: Float = 42f) { if (y > 780f) { finish(); start() }; page!!.canvas.drawText(value, x, y, paint); y += gap }
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
        fun paragraph(value: String, paint: Paint = text) = wrap(value, paint, 511f).forEach { line(it, paint) }
        fun drawQr(code: QRCode, left: Float, top: Float, side: Float) {
            val matrix = code.matrix; val quiet = 4; val cell = side / (matrix.width + 2 * quiet)
            for (row in 0 until matrix.height) for (col in 0 until matrix.width) if (matrix.get(col, row).toInt() == 1)
                page!!.canvas.drawRect(left + (col + quiet) * cell, top + (row + quiet) * cell, left + (col + quiet + 1) * cell, top + (row + quiet + 1) * cell, black)
        }
        try {
            start()
            line("Sahara emergency card · आपातकालीन कार्ड", heading, 22f)
            line("Card ${record.number} · ID ${record.id} · made ${cardDay(record.madeAt)}", strong, 20f)
            val link = record.link
            val top = y
            if (link != null) {
                drawQr(EmergencyLinkQr.encode(link.url), 42f, top, 240f); drawQr(qr, 313f, top, 240f)
                y = top + 254f
                val left = wrap("WEB LINK: scan for the latest version (needs internet). Read-only. · नवीनतम संस्करण के लिए स्कैन करें।", strong, 236f) + wrap(link.url, small, 236f) + wrap("Works until ${cardDay(link.expiresAt)} unless the family turns it off.", small, 236f)
                val right = wrap("OFFLINE CODE: the card text itself, no internet needed. · बिना इंटरनेट के।", strong, 236f) + wrap("This printed copy cannot be switched off: tear up old copies.", small, 236f)
                val start = y
                left.forEachIndexed { i, it -> line(it, if (i == 0) strong else small, 13f) }
                val leftEnd = y; y = start
                right.forEachIndexed { i, it -> page!!.canvas.drawText(it, 313f, y, if (i == 0) strong else small); y += 13f }
                y = maxOf(leftEnd, y) + 8f
            } else {
                drawQr(qr, 42f, top, 300f)
                y = top + 318f
                paragraph(EmergencyCardRules.OFFLINE_EN, strong); paragraph(EmergencyCardRules.OFFLINE_HI, strong)
                paragraph("Scan with any phone camera to read the text below. If the family has a newer card, this one is outdated. A printed card cannot be switched off: tear up old copies.")
            }
            line("")
            paragraph(record.text)
            line("")
            paragraph("Made on the family's phone from what they recorded in Sahara. Names are self-entered. This is not a treatment order, legal directive or hospital record.", small)
            finish()
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

/** Today: a large QR (the web link when it is live, otherwise the offline code), or the way to make one. */
@Composable fun EmergencyCardOnToday(vm: CareModel, onRead: (CareVersion) -> Unit = {}) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val t = careText()
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val acknowledgements by vm.acknowledgements.collectAsStateWithLifecycle(); val trustedAcks by vm.authenticatedConfirmationIds.collectAsStateWithLifecycle()
    val contacts by vm.careContacts.collectAsStateWithLifecycle(); val contactsLoading by vm.careContactsLoading.collectAsStateWithLifecycle()
    val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle(); val owner by vm.isOwner.collectAsStateWithLifecycle()
    val store = remember(vm.storageNamespace) { EmergencyCardStore(context.applicationContext, vm.storageNamespace) }
    val client = remember(vm.cloud) { vm.cloud?.takeUnless { vm.demoMode }?.let(::EmergencyLinkClient) }
    var record by remember(selected) { mutableStateOf(store.load(selected)) }
    var setup by rememberSaveable(selected) { mutableStateOf(false) }
    var fullScreen by rememberSaveable(selected) { mutableStateOf(false) }
    var lockFor by rememberSaveable(selected) { mutableStateOf<String?>(null) }
    var confirmNewLink by rememberSaveable(selected) { mutableStateOf(false) }
    var linkProblem by remember(selected) { mutableStateOf<EmergencyLinkProblem?>(null) }
    var linkBusy by remember(selected) { mutableStateOf(false) }
    var linkStatus by remember(selected) { mutableStateOf<EmergencyLinkStatus?>(null) }
    var busy by remember(selected) { mutableStateOf(false) }
    val name = people.firstOrNull { it.id == selected }?.name.orEmpty()
    val source = remember(name, items, versions, acknowledgements, trustedAcks, contacts) {
        EmergencySource(
            name = name,
            goals = EmergencyCardRules.latestConfirmed(versions.filter { it.personId == selected }, acknowledgements) { vm.cloud == null || it.id in trustedAcks },
            contacts = contacts.filter { it.personId == selected }.sortedBy { it.role != "family" }.map { EmergencyContact(it.name, it.relationship, it.phone) },
            medicines = items.filter { it.kind == "medicine" && it.personId == selected }.map { EmergencyMedicine(it.title, if (CareEntryCodec.isStructured(it.detail)) "" else it.detail) },
        )
    }
    val ready = loadedFor == selected && !contactsLoading
    val blocked: EmergencyLinkProblem? = when { vm.demoMode -> EmergencyLinkProblem.WALKTHROUGH; client == null -> EmergencyLinkProblem.SIGNED_OUT; !owner -> EmergencyLinkProblem.NOT_OWNER; else -> null }
    val card = record
    val qr = remember(card?.text) { card?.text?.let { runCatching { EmergencyCardRules.qr(it) }.getOrNull() } }
    val linkQr = remember(card?.link?.token) { card?.link?.let { runCatching { EmergencyLinkQr.encode(it.url) }.getOrNull() } }
    val readNote = source.goals?.let { g -> versions.firstOrNull { it.version == g.version && it.personId == selected } }

    fun keep(next: EmergencyCardRecord) { store.save(next); EmergencyCardPdf.create(store.pdf(next), next, EmergencyCardRules.qr(next.text)) }
    fun ensurePdf(c: EmergencyCardRecord): File = store.pdf(c).takeIf(File::isFile) ?: run { EmergencyCardPdf.create(store.pdf(c), c, EmergencyCardRules.qr(c.text)); store.pdf(c) }

    /** Puts [c] on the server: same link when one exists ([fresh] = false), otherwise a new one. Returns the saved record. */
    suspend fun syncLink(c: EmergencyCardRecord, fresh: Boolean): EmergencyCardRecord {
        val api = client ?: return c
        val payload = EmergencyLinkRules.card(source, c)
        val next = try {
            if (c.link != null && !fresh) { api.update(c.personId, payload); c } else c.copy(link = api.create(c.personId, payload))
        } catch (e: EmergencyLinkException) {
            if (e.problem == EmergencyLinkProblem.LINK_OFF && c.link != null) withContext(Dispatchers.IO) { keep(c.copy(link = null)) }.also { record = c.copy(link = null) }
            throw e
        }
        if (next !== c) withContext(Dispatchers.IO) { keep(next) }
        linkProblem = null
        linkStatus = runCatching { api.status(c.personId) }.getOrNull()
        return next
    }

    // A card without a link gets one as soon as the owner can make it; a live link shows how often it was opened.
    LaunchedEffect(card?.id, card?.link?.token, blocked) {
        val c = card ?: return@LaunchedEffect
        if (blocked != null || client == null) return@LaunchedEffect
        linkBusy = true
        try {
            if (c.link == null) record = syncLink(c, fresh = true)
            else { val s = client.status(c.personId); linkStatus = s; if (!s.live) linkProblem = EmergencyLinkProblem.LINK_OFF }
        } catch (e: EmergencyLinkException) { linkProblem = e.problem }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; linkProblem = EmergencyLinkProblem.OFFLINE }
        finally { linkBusy = false }
    }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val c = record
        if (uri != null && c != null) scope.launch {
            try { withContext(Dispatchers.IO) { val file = ensurePdf(c); (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).use { out -> file.inputStream().use { it.copyTo(out) } } }; vm.message.value = t("Card PDF saved", "कार्ड PDF सहेजा गया") }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = t("Couldn't save the PDF. Try again.", "PDF सहेजा नहीं जा सका।") }
        }
    }
    val saveLocked = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val c = record; val password = lockFor
        lockFor = null
        if (uri != null && c != null && password != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { val locked = PdfPasswordLock.lock(ensurePdf(c).readBytes(), password); (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).use { it.write(locked) } }
                vm.message.value = t("Locked PDF saved", "पासवर्ड वाला PDF सहेजा गया")
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = t("Couldn't save the locked PDF. Try again.", "PDF सहेजा नहीं जा सका।") }
        }
    }
    fun share(c: EmergencyCardRecord, password: String? = null) = scope.launch {
        try {
            val file = withContext(Dispatchers.IO) { val plain = ensurePdf(c); if (password == null) plain else store.lockedPdf(c).also { it.writeBytes(PdfPasswordLock.lock(plain.readBytes(), password)) } }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), t("Share the emergency card", "आपातकालीन कार्ड साझा करें")))
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = t("No app is available to share the PDF.", "PDF साझा करने के लिए कोई ऐप नहीं है।") }
    }
    fun print(c: EmergencyCardRecord) = scope.launch {
        try {
            val file = withContext(Dispatchers.IO) { ensurePdf(c) }
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print("Sahara emergency card ${c.number}", PdfFilePrintAdapter(file, "Sahara-emergency-card-${c.number}.pdf"), null)
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = t("Printing isn't available on this phone. Use Save PDF.", "प्रिंट उपलब्ध नहीं है।") }
    }
    fun remake(sections: Set<EmergencySection>, previous: EmergencyCardRecord?) = scope.launch {
        if (busy) return@launch
        busy = true
        try {
            val made = withContext(Dispatchers.IO) { EmergencyCardRecord.make(selected, source, sections, previous, System.currentTimeMillis()).also(::keep) }
            record = made; setup = false
            if (blocked == null) try { record = syncLink(made, fresh = made.link == null) } catch (e: EmergencyLinkException) { linkProblem = e.problem } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; linkProblem = EmergencyLinkProblem.OFFLINE }
            vm.message.value = if (previous == null) t("Card ready", "कार्ड तैयार") else t("Card updated", "कार्ड अपडेट हुआ")
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = e.message ?: t("Couldn't make the card. Try again.", "कार्ड नहीं बना।") }
        finally { busy = false }
    }

    // No card yet: make one now from the saved records (every part), so the QR shows at once. A tap retries if that failed.
    LaunchedEffect(selected, ready, card == null) {
        if (card == null && ready && selected.isNotBlank() && !busy) remake(EmergencySection.entries.toSet(), null)
    }
    if (selected.isBlank()) return
    if (card == null || qr == null) {
        ActionCard(Peach, modifier = Modifier.fillMaxWidth().testTag("emergency-card-entry"), onClick = { if (ready && !busy) remake(EmergencySection.entries.toSet(), null) }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, null, Modifier.size(36.dp), tint = Rust); Spacer(Modifier.width(12.dp))
                Text(t("Emergency card", "आपातकालीन कार्ड"), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink, modifier = Modifier.weight(1f))
                if (!ready || busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Rust)
                else Icon(Icons.Default.Refresh, null, Modifier.size(19.dp))
            }
        }
    } else {
        val stale = ready && runCatching { card.outOfDate(source) }.getOrDefault(false)
        val shownQr = linkQr ?: qr
        val link = card.link
        Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("emergency-card")) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrImage(shownQr, if (link != null) t("Emergency web link QR code", "आपातकालीन वेब लिंक QR कोड") else t("Emergency card offline QR code", "आपातकालीन कार्ड ऑफ़लाइन QR कोड"), Modifier.fillMaxWidth().widthIn(max = 340.dp).clickable(onClickLabel = t("Show full screen", "पूरी स्क्रीन पर दिखाएँ")) { fullScreen = true }.testTag("emergency-card-qr"))
                if (name.isNotBlank()) Text(name, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.testTag("emergency-card-name"))
                Text(t("Scan in an emergency", "आपातकाल में स्कैन करें"), fontSize = 16.sp, color = DoctorCardSub, textAlign = TextAlign.Center)
                val problem = linkProblem
                if (linkBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Rust)
                else if (problem != null) Column(Modifier.fillMaxWidth().background(DoctorStatusWarnBg, RoundedCornerShape(14.dp)).padding(12.dp).testTag("emergency-link-problem")) {
                    Text(t(problem.en, problem.hi), fontSize = 14.sp, color = DoctorStatusWarn)
                    if (problem == EmergencyLinkProblem.OFFLINE || problem == EmergencyLinkProblem.NOT_SET_UP || problem == EmergencyLinkProblem.LINK_OFF) TextButton(onClick = {
                        scope.launch {
                            linkBusy = true
                            try { record = syncLink(record ?: return@launch, fresh = problem == EmergencyLinkProblem.LINK_OFF || record?.link == null); vm.message.value = t("Web link is live", "वेब लिंक चालू है") }
                            catch (e: EmergencyLinkException) { linkProblem = e.problem; vm.message.value = t(e.problem.en, e.problem.hi) }
                            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; linkProblem = EmergencyLinkProblem.OFFLINE }
                            finally { linkBusy = false }
                        }
                    }, enabled = !linkBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (problem == EmergencyLinkProblem.LINK_OFF) t("Make a new link", "नया लिंक बनाएँ") else t("Try again", "फिर कोशिश करें")) }
                }
                if (stale) Column(Modifier.fillMaxWidth().background(DoctorStatusWarnBg, RoundedCornerShape(14.dp)).padding(12.dp).testTag("emergency-card-stale")) {
                    TextButton(onClick = { remake(card.sections, card) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("emergency-card-update")) { Text(t("Update card", "कार्ड अपडेट करें")) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { share(card) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("emergency-card-share")) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Share", "साझा करें")) }
                    OutlinedButton(onClick = { print(card) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("emergency-card-print")) { Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Print / Save", "प्रिंट / सहेजें")) }
                }
                TextButton(onClick = { setup = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("emergency-card-replace")) { Text(t("Edit", "बदलें")) }
            }
        }
        if (fullScreen) EmergencyQrFullScreen(linkQr, qr, card) { fullScreen = false }
        if (confirmNewLink) AlertDialog(onDismissRequest = { confirmNewLink = false },
            title = { Text(t("Replace the web link?", "लिंक बदलें?")) },
            text = { Text(t("The old link will stop working.", "पुराना लिंक बंद हो जाएगा।")) },
            confirmButton = { TextButton(onClick = {
                confirmNewLink = false
                scope.launch {
                    linkBusy = true
                    try { record = syncLink(record ?: return@launch, fresh = true); vm.message.value = t("Link replaced", "लिंक बदल दिया गया") }
                    catch (e: EmergencyLinkException) { linkProblem = e.problem; vm.message.value = t(e.problem.en, e.problem.hi) }
                    catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = t(EmergencyLinkProblem.OFFLINE.en, EmergencyLinkProblem.OFFLINE.hi) }
                    finally { linkBusy = false }
                }
            }, modifier = Modifier.testTag("emergency-link-replace-confirm")) { Text(t("Replace link", "लिंक बदलें")) } },
            dismissButton = { TextButton(onClick = { confirmNewLink = false }) { Text(t("Cancel", "रद्द करें")) } })
        lockFor?.let { _ -> PdfPasswordDialog(onDismiss = { lockFor = null }, onSave = { password -> lockFor = password; saveLocked.launch("Sahara-emergency-card-${card.number}-locked.pdf") }, onShare = { password -> lockFor = null; share(card, password) }) }
    }
    if (setup) EmergencyCardSetup(source, name, ready && !busy, card, blocked, onDismiss = { setup = false }, onMake = { sections -> remake(sections, card) }) {
        val c = card
        if (c != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.link != null) linkStatus?.takeIf { it.live }?.let { s -> Text(if (s.views == 0) t("Not opened yet", "अभी तक नहीं खोला गया") else { val views = s.views; val last = s.lastViewedAt?.let { val day = cardDay(it); t(", last on $day", ", आख़िरी बार $day") }.orEmpty(); if (views == 1) t("Opened once$last", "एक बार खोला गया$last") else t("Opened $views times$last", "$views बार खोला गया$last") }, fontSize = 14.sp, color = DoctorCardSub) }
            readNote?.takeIf { EmergencySection.GOALS in c.sections }?.let { note ->
                OutlinedButton(onClick = { setup = false; onRead(note) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("emergency-card-note")) { Icon(Icons.Default.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Read note", "नोट पढ़ें")) }
            }
            OutlinedButton(onClick = { setup = false; savePdf.launch("Sahara-emergency-card-${c.number}.pdf") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("emergency-card-save")) { Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Save PDF", "PDF सहेजें")) }
            OutlinedButton(onClick = { setup = false; lockFor = "" }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("emergency-card-lock")) { Icon(Icons.Default.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Locked PDF", "लॉक PDF")) }
            if (c.link != null && blocked == null) TextButton(onClick = { setup = false; confirmNewLink = true }, enabled = !linkBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("emergency-link-replace")) { Text(t("Replace link", "लिंक बदलें"), color = RustText) }
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

/** Full screen for showing hospital staff: white, maximum brightness, a gentle SaharaMotion scale-in, tap to close. */
@Composable private fun EmergencyQrFullScreen(linkQr: QRCode?, offlineQr: QRCode, card: EmergencyCardRecord, onClose: () -> Unit) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    var offline by rememberSaveable { mutableStateOf(linkQr == null) }; val t = careText()
    LaunchedEffect(shown.currentState, shown.targetState) { if (!shown.targetState && !shown.currentState && shown.isIdle) onClose() }
    Dialog(onDismissRequest = { shown.targetState = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.let { w -> w.setWindowAnimations(0); w.attributes = w.attributes.apply { screenBrightness = 1f } } }
        AnimatedVisibility(shown,
            enter = fadeIn(tween(SaharaMotion.ENTER_MS - SaharaMotion.FADE_DELAY_MS, delayMillis = SaharaMotion.FADE_DELAY_MS)) + scaleIn(tween(SaharaMotion.ENTER_MS, easing = FastOutSlowInEasing), initialScale = .88f),
            exit = fadeOut(tween(SaharaMotion.EXIT_MS, easing = FastOutSlowInEasing)) + scaleOut(tween(SaharaMotion.EXIT_MS, easing = FastOutSlowInEasing), targetScale = .94f)) {
            Column(Modifier.fillMaxSize().background(Color.White).clickable(onClickLabel = t("Close", "बंद करें")) { shown.targetState = false }.padding(20.dp).testTag("emergency-card-fullscreen"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(t("Emergency card", "आपातकालीन कार्ड"), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.a11yHeading())
                Spacer(Modifier.height(16.dp))
                QrImage(if (offline || linkQr == null) offlineQr else linkQr, if (offline || linkQr == null) t("Emergency card offline QR code", "आपातकालीन कार्ड ऑफ़लाइन QR कोड") else t("Emergency web link QR code", "आपातकालीन वेब लिंक QR कोड"), Modifier.fillMaxWidth().widthIn(max = 480.dp).border(3.dp, Color.Black))
                Spacer(Modifier.height(16.dp))
                Text(t("Card ${card.number} · ID ${card.id} · made ${cardDay(card.madeAt)}", "कार्ड ${card.number} · ID ${card.id} · बना ${cardDay(card.madeAt)}"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                if (linkQr != null) OutlinedButton(onClick = { offline = !offline }, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, RustText), modifier = Modifier.heightIn(min = 48.dp).testTag("emergency-fullscreen-toggle")) { Text(if (offline) t("Web link code", "वेब लिंक कोड") else t("Offline code", "ऑफ़लाइन कोड"), color = RustText) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EmergencyCardSetup(source: EmergencySource, name: String, ready: Boolean, previous: EmergencyCardRecord?, blocked: EmergencyLinkProblem?, onDismiss: () -> Unit, onMake: (Set<EmergencySection>) -> Unit, extras: @Composable () -> Unit = {}) {
    var chosen by rememberSaveable { mutableStateOf(previous?.sections?.map { it.name }.orEmpty()) }; val t = careText()
    val sections = chosen.mapNotNull { s -> EmergencySection.entries.firstOrNull { it.name == s } }.toSet()
    val preview = remember(source, sections, previous) { if (sections.isEmpty()) null else runCatching { EmergencyCardRules.build(source, sections, (previous?.number ?: 0) + 1, "NEW", System.currentTimeMillis()) }.getOrNull() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        LazyColumn(Modifier.fillMaxWidth().testTag("emergency-card-setup"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Heading(if (previous == null) t("Emergency card", "आपातकालीन कार्ड") else t("Edit card", "कार्ड बदलें"), 26) }
            item {
                val goals = source.goals
                EmergencySection.entries.forEach { section ->
                    val detail = when (section) {
                        EmergencySection.NAME -> name.ifBlank { t("No name saved", "नाम नहीं है") }
                        EmergencySection.GOALS -> goals?.let { t("Note v${it.version}, confirmed ${cardDay(it.confirmedAt)}", "नोट v${it.version}, पुष्टि ${cardDay(it.confirmedAt)}") + (it.newerUnconfirmedVersion?.let { v -> t(" (newer note v$v not confirmed yet)", " (नया नोट v$v अभी पुष्टि नहीं हुआ)") } ?: "") } ?: t("No confirmed note", "पुष्टि किया गया नोट नहीं")
                        EmergencySection.CONTACTS -> if (source.contacts.isEmpty()) t("No contacts saved", "कोई संपर्क नहीं है") else t("${source.contacts.size} saved", "${source.contacts.size} सहेजे गए")
                        EmergencySection.MEDICINES -> if (source.medicines.isEmpty()) t("No current medicines listed", "कोई दवा नहीं है") else t("${source.medicines.size} current", "${source.medicines.size} दवाएँ")
                    }
                    CheckLine(section.name in chosen, { on -> chosen = if (on) chosen + section.name else chosen - section.name }, "${t(section.en, section.hi)}\n$detail")
                }
            }
            if (preview != null) item { SurfaceCard {
                Text(t("Preview", "झलक"), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); Text(preview.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("emergency-card-preview"))
                if (preview.leftOut.isNotEmpty()) Text(t("Left out: ", "छोड़ा गया: ") + preview.leftOut.joinToString("; "), style = MaterialTheme.typography.bodySmall, color = RustText)
            } }
            item { Button(onClick = { onMake(sections) }, enabled = ready && preview != null, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("emergency-card-make")) { Text(if (!ready) t("Loading…", "लोड हो रहा है…") else if (sections.isEmpty()) t("Choose what to include", "क्या शामिल करें चुनें") else if (previous == null) t("Make card", "कार्ड बनाएँ") else t("Save", "सहेजें")) } }
            item { extras() }
        }
    }
}
