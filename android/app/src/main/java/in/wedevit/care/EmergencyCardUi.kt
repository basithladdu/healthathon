package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.qrcode.encoder.QRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Cards stay on this phone only: private preferences plus a PDF in app storage. Nothing is uploaded. */
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
    fun pdf(record: EmergencyCardRecord) = File(folder(record.personId), "sahara-emergency-card-${record.number}-${record.id}.pdf")
    fun removePerson(personId: String) { preferences.edit().remove(personId).commit(); folder(personId).deleteRecursively() }
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
        fun line(value: String, paint: Paint = text, gap: Float = 16f) { if (y > 780f) { finish(); start() }; page!!.canvas.drawText(value, 42f, y, paint); y += gap }
        fun paragraph(value: String, paint: Paint = text) = value.lines().forEach { original ->
            var remaining = original
            if (remaining.isEmpty()) line("", paint)
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, 511f, null).coerceAtLeast(1)
                val split = if (count < remaining.length) remaining.lastIndexOf(' ', count).takeIf { it > 0 } ?: count else count
                line(remaining.take(split), paint); remaining = remaining.drop(split).trimStart()
            }
        }
        try {
            start()
            line("Sahara emergency card · आपातकालीन कार्ड", heading, 22f)
            line("Card ${record.number} · ID ${record.id} · made ${cardDay(record.madeAt)}", strong, 20f)
            val matrix = qr.matrix; val quiet = 4; val side = 300f; val cell = side / (matrix.width + 2 * quiet); val left = 42f; val top = y
            for (row in 0 until matrix.height) for (col in 0 until matrix.width) if (matrix.get(col, row).toInt() == 1)
                page!!.canvas.drawRect(left + (col + quiet) * cell, top + (row + quiet) * cell, left + (col + quiet + 1) * cell, top + (row + quiet + 1) * cell, black)
            y = top + side + 18f
            paragraph(EmergencyCardRules.OFFLINE_EN, strong); paragraph(EmergencyCardRules.OFFLINE_HI, strong)
            paragraph("Scan with any phone camera to read the text below. If the family has a newer card, this one is outdated. A printed card cannot be switched off: tear up old copies.")
            line("")
            paragraph(record.text)
            line("")
            paragraph("Made on the family's phone from what they recorded in Sahara. Names are self-entered. This is not a treatment order, legal directive or hospital record.", small)
            finish()
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
    }
}

private fun cardDay(at: Long) = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

@Composable private fun QrImage(qr: QRCode, modifier: Modifier = Modifier) {
    val matrix = qr.matrix
    Canvas(modifier.aspectRatio(1f).background(Color.White).semantics { contentDescription = "Emergency card QR code" }) {
        val quiet = 4; val cell = size.width / (matrix.width + 2 * quiet)
        for (row in 0 until matrix.height) for (col in 0 until matrix.width) if (matrix.get(col, row).toInt() == 1)
            drawRect(Color.Black, Offset((col + quiet) * cell, (row + quiet) * cell), Size(cell + .5f, cell + .5f))
    }
}

/** Today: a large offline QR card, or the way to make one. Family picks every section before anything is made. */
@Composable fun EmergencyCardOnToday(vm: CareModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val acknowledgements by vm.acknowledgements.collectAsStateWithLifecycle(); val trustedAcks by vm.authenticatedConfirmationIds.collectAsStateWithLifecycle()
    val contacts by vm.careContacts.collectAsStateWithLifecycle(); val contactsLoading by vm.careContactsLoading.collectAsStateWithLifecycle()
    val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle()
    val store = remember(vm.storageNamespace) { EmergencyCardStore(context.applicationContext, vm.storageNamespace) }
    var record by remember(selected) { mutableStateOf(store.load(selected)) }
    var setup by rememberSaveable(selected) { mutableStateOf(false) }
    var fullScreen by rememberSaveable(selected) { mutableStateOf(false) }
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
    val card = record
    val qr = remember(card?.text) { card?.text?.let { runCatching { EmergencyCardRules.qr(it) }.getOrNull() } }
    val pdfFile = card?.let(store::pdf)
    fun ensurePdf(): File? = card?.let { c -> pdfFile?.takeIf(File::isFile) ?: qr?.let { EmergencyCardPdf.create(store.pdf(c), c, it); store.pdf(c) } }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            try { withContext(Dispatchers.IO) { val file = requireNotNull(ensurePdf()); (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).use { out -> file.inputStream().use { it.copyTo(out) } } }; vm.message.value = "Card PDF saved · कार्ड PDF सहेजा गया" }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = "Couldn't save the PDF. Try again." }
        }
    }
    if (selected.isBlank()) return
    if (card == null || qr == null) {
        ActionCard(Peach, modifier = Modifier.fillMaxWidth().testTag("emergency-card-entry"), onClick = { setup = true }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, null, Modifier.size(36.dp), tint = Rust); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Emergency card · आपातकालीन कार्ड", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Make a QR card the hospital can scan. You choose what goes on it.", fontSize = 13.sp)
                    Text("Works without internet · बिना इंटरनेट के काम करता है", fontSize = 12.sp, color = RustText)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp))
            }
        }
    } else {
        val stale = ready && runCatching { card.outOfDate(source) }.getOrDefault(false)
        Surface(shape = RoundedCornerShape(22.dp), color = Paper, modifier = Modifier.fillMaxWidth().testTag("emergency-card")) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Emergency card · आपातकालीन कार्ड", fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
                QrImage(qr, Modifier.fillMaxWidth(.8f).widthIn(max = 300.dp).clickable { fullScreen = true }.testTag("emergency-card-qr"))
                Text("Tap the code to show it full screen · बड़ा दिखाने के लिए छुएँ", fontSize = 13.sp, color = DoctorCardSub, textAlign = TextAlign.Center)
                Text("Card ${card.number} · ID ${card.id} · made ${cardDay(card.madeAt)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${EmergencyCardRules.OFFLINE_EN}\n${EmergencyCardRules.OFFLINE_HI}", fontSize = 13.sp, color = RustText, textAlign = TextAlign.Center)
                if (stale) Text("Your records changed after this card was made. Replace the card to update it. · रिकॉर्ड बदल गए हैं, नया कार्ड बनाएँ।", fontSize = 13.sp, color = DoctorStatusWarn, modifier = Modifier.fillMaxWidth().background(DoctorStatusWarnBg, RoundedCornerShape(12.dp)).padding(10.dp).testTag("emergency-card-stale"))
                if (card.leftOut.isNotEmpty()) Text("Not on this card: " + card.leftOut.joinToString("; "), fontSize = 12.sp, color = DoctorCardSub, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { scope.launch {
                        try { val file = withContext(Dispatchers.IO) { requireNotNull(ensurePdf()) }; val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Print or share the emergency card")) }
                        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = "No app is available to print or share the PDF." }
                    } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("emergency-card-share"), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Gold, contentColor = Ink)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Print / share") }
                    OutlinedButton(onClick = { savePdf.launch("Sahara-emergency-card-${card.number}.pdf") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("emergency-card-save")) { Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save PDF") }
                }
                OutlinedButton(onClick = { setup = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("emergency-card-replace")) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Replace card · नया कार्ड बनाएँ") }
                if (card.replaced.isNotEmpty()) Text("Outdated cards (do not use): " + card.replaced.joinToString(", ") { "card ${it.number} · ID ${it.id}" }, fontSize = 12.sp, color = DoctorCardSub, modifier = Modifier.fillMaxWidth().testTag("emergency-card-outdated"))
            }
        }
        if (fullScreen) Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            LaunchedEffect(window) { window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } } }
            Column(Modifier.fillMaxSize().background(Color.White).clickable { fullScreen = false }.padding(20.dp).testTag("emergency-card-fullscreen"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Emergency card · आपातकालीन कार्ड", fontFamily = SaharaTitleFont, fontSize = 24.sp, color = Color.Black)
                Spacer(Modifier.height(12.dp)); QrImage(qr, Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
                Text("Card ${card.number} · ID ${card.id} · made ${cardDay(card.madeAt)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                Text(EmergencyCardRules.OFFLINE_EN, fontSize = 14.sp, color = Color.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp)); Text("Tap anywhere to close · बंद करने के लिए कहीं भी छुएँ", fontSize = 14.sp, color = DoctorCardSub)
            }
        }
    }
    if (setup) EmergencyCardSetup(source, name, ready, card, onDismiss = { setup = false }) { sections ->
        scope.launch {
            try {
                val made = withContext(Dispatchers.IO) {
                    val next = EmergencyCardRecord.make(selected, source, sections, card, System.currentTimeMillis())
                    EmergencyCardPdf.create(store.pdf(next), next, EmergencyCardRules.qr(next.text))
                    store.save(next); next
                }
                record = made; setup = false
                vm.message.value = if (card == null) "Card ready and saved on this phone · कार्ड तैयार है" else "New card ${made.number} made. Card ${card.number} is now outdated · पुराना कार्ड अब मान्य नहीं"
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = e.message ?: "Couldn't make the card. Try again." }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EmergencyCardSetup(source: EmergencySource, name: String, ready: Boolean, previous: EmergencyCardRecord?, onDismiss: () -> Unit, onMake: (Set<EmergencySection>) -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(previous?.sections?.map { it.name }.orEmpty()) }
    val sections = chosen.mapNotNull { s -> EmergencySection.entries.firstOrNull { it.name == s } }.toSet()
    val preview = remember(source, sections, previous) { if (sections.isEmpty()) null else runCatching { EmergencyCardRules.build(source, sections, (previous?.number ?: 0) + 1, "NEW", System.currentTimeMillis()) }.getOrNull() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        LazyColumn(Modifier.fillMaxWidth().testTag("emergency-card-setup"), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Heading(if (previous == null) "Emergency card" else "Replace card", 28); Text(if (previous == null) "आपातकालीन कार्ड" else "नया कार्ड बनाएँ", color = DoctorCardSub) }
            item { Text("${EmergencyCardRules.OFFLINE_EN}\n${EmergencyCardRules.OFFLINE_HI}", fontWeight = FontWeight.SemiBold, color = RustText) }
            item { Text("Anyone who scans the code can read what you put on it. Tick only what you are happy to share. · कोड स्कैन करने वाला इसे पढ़ सकता है। जो साझा करना ठीक लगे, वही चुनें।", style = MaterialTheme.typography.bodyMedium) }
            item {
                val goals = source.goals
                EmergencySection.entries.forEach { section ->
                    val detail = when (section) {
                        EmergencySection.NAME -> name.ifBlank { "No name saved" }
                        EmergencySection.GOALS -> goals?.let { "Note v${it.version}, confirmed ${cardDay(it.confirmedAt)}" + (it.newerUnconfirmedVersion?.let { v -> " (newer note v$v not confirmed yet)" } ?: "") } ?: "No confirmed note yet. The patient confirms a note in Read notes first."
                        EmergencySection.CONTACTS -> if (source.contacts.isEmpty()) "No contacts saved. Add them in Your care team." else "${source.contacts.size} saved"
                        EmergencySection.MEDICINES -> if (source.medicines.isEmpty()) "No current medicines listed" else "${source.medicines.size} current"
                    }
                    CheckLine(section.name in chosen, { on -> chosen = if (on) chosen + section.name else chosen - section.name }, "${section.en} · ${section.hi}\n$detail")
                }
            }
            item { Text("Age, conditions and allergies are not recorded in Sahara, so they are not on the card. The card says so. · उम्र, बीमारियाँ और एलर्जी ऐप में दर्ज नहीं हैं।", style = MaterialTheme.typography.bodySmall, color = DoctorCardSub) }
            if (previous != null) item { Text("Card ${previous.number} (ID ${previous.id}) will be marked outdated in the app. A printed copy cannot be switched off, so collect or tear up old copies.", style = MaterialTheme.typography.bodySmall) }
            if (preview != null) item { SurfaceCard {
                Text("What the code will contain · कोड में क्या होगा", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); Text(preview.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("emergency-card-preview"))
                Spacer(Modifier.height(6.dp)); Text("${EmergencyCardRules.bytes(preview.text)} of ${EmergencyCardRules.MAX_BYTES} bytes used", style = MaterialTheme.typography.labelSmall, color = DoctorCardSub)
                if (preview.leftOut.isNotEmpty()) Text("Left out: " + preview.leftOut.joinToString("; "), style = MaterialTheme.typography.bodySmall, color = RustText)
            } }
            item { Button(onClick = { onMake(sections) }, enabled = ready && preview != null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("emergency-card-make")) { Text(if (!ready) "Loading records…" else if (sections.isEmpty()) "Choose what to include" else if (previous == null) "Make card · कार्ड बनाएँ" else "Make new card · नया कार्ड बनाएँ") } }
        }
    }
}
