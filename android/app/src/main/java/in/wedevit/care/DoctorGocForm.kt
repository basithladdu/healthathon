package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * One goals-of-care discussion as the doctor reads it, in the legal form's layout (Sections I to IV, legal review 31 Oct 2026).
 * [record] feeds [GocForm.sections], the same builder the talk screen and the plan PDF use; [note] is the doctor's own note.
 */
internal data class DoctorGocDiscussion(
    val id: String, val record: EctprRecord, val topics: Map<GocDecision, GocTopicRecord>,
    /** ISO time: when it was signed and locked, else when it was reviewed or last changed. */
    val at: String, val signOff: GocSignOffSummary?, val note: String,
) {
    val signed: Boolean get() = signOff != null
    fun sections(): List<GocFormSection> = GocForm.sections(record, topics, signOff?.formSigners().orEmpty())
}

internal object DoctorGocDiscussions {
    /** Every plan version with its own saved talk (not a copy carried from an earlier version), newest first. */
    fun list(ectprs: List<EctprRecord>, personId: String): List<DoctorGocDiscussion> = ectprs
        .filter { it.patientId == personId }
        .mapNotNull { r ->
            r.gocTranscript?.takeIf { it.planVersion == r.version && (it.note.isNotBlank() || it.topics.isNotEmpty() || it.lines.isNotEmpty()) }?.let { g ->
                DoctorGocDiscussion(r.id, r, g.topics.associateBy { it.decision }, g.signOff?.lockedAt?.takeIf(String::isNotBlank) ?: g.reviewedAt.ifBlank { r.updatedAt }, g.signOff, g.note)
            }
        }
        .sortedByDescending { it.at }

    /** A saved conversation note in the same layout. Only what the note itself recorded is filled; the rest reads "Not recorded". */
    fun fromNote(note: CareVersion, hindi: Boolean): DoctorGocDiscussion {
        val at = Instant.ofEpochMilli(note.published).toString()
        val present = note.participants.split('\n', ',', ';').map(String::trim).filter(String::isNotBlank)
        val record = EctprRecord(id = note.id, patientId = note.personId, version = note.version, createdAt = at,
            patient = EctprPatient(name = note.personName), decisionMakers = DecisionMakers(presentAtDiscussion = present),
            values = PlanValues(ownWords = note.priorities))
        val rest = listOf(2, 3, 4).mapNotNull { i -> NoteRules.values(note)[i].trim().takeIf(String::isNotBlank)?.let { noteSectionLabel(NoteRules.labels[i], hindi) + ": " + it } }
        return DoctorGocDiscussion(note.id, record, emptyMap(), at, null, rest.joinToString("\n\n"))
    }

    /** The lines the PDF prints, in order: title, status, Sections I to IV, then the doctor's note. Kept here so it can be tested. */
    fun pdfLines(d: DoctorGocDiscussion, hindi: Boolean, status: String): List<Pair<String, Int>> = buildList {
        add(Bi("Goals-of-care note, in the order of the legal form", "देखभाल के लक्ष्यों का नोट, क़ानूनी फ़ॉर्म के क्रम में").pick(hindi) to 0)
        add(listOf(d.record.patient.name, d.at.take(16).replace('T', ' '), status).filter(String::isNotBlank).joinToString(" · ") to 2)
        d.sections().forEach { s -> add(s.title.pick(hindi) to 1); s.rows.forEach { add(it.pick(hindi) to 2) } }
        if (d.note.isNotBlank()) { add(Bi("Doctor's note", "डॉक्टर का नोट").pick(hindi) to 1); add(d.note to 2) }
    }
}

/** The doctor-side PDF of one discussion: the form's sections as text, A4, page x of y. Drawn like [CarePlanPdf], never on the wallet card. */
internal object DoctorGocPdf {
    /** The same layout as the plan PDF's goals-of-care pages ([CarePlanPdf.gocNote]): the legal form with its boxes and tables. */
    fun write(context: Context, d: DoctorGocDiscussion, hindi: Boolean, status: String): File {
        val folder = File(context.cacheDir, "doctor-preview-exports").apply { mkdirs() }
        val file = File(folder, "goals-of-care-${d.id.filter { it.isLetterOrDigit() || it == '-' }.take(48)}.pdf")
        CarePlanPdf.gocNote(file, d.record, hindi, LocalDate.now(), note = d.note, status = status, topics = d.topics, signers = d.signOff?.formSigners().orEmpty())
        return file
    }

    fun share(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), title))
    }
}

@Composable internal fun doctorGocStatus(d: DoctorGocDiscussion): String { val t = careText()
    return d.signOff?.let { t("Signed and locked", "साइन होकर बंद") + (if (it.signers.isNotEmpty()) ": " + it.signers.joinToString(", ") else "") } ?: t("Not signed yet", "अभी साइन नहीं")
}

/** Signed or not, as a small pill: green with a lock when signed, amber otherwise. The full status (with signers) is the accessibility text. */
@Composable private fun DoctorGocStatusChip(d: DoctorGocDiscussion) { val t = careText()
    val full = doctorGocStatus(d)
    Surface(color = if (d.signed) DoctorStatusGoodBg else DoctorStatusWarnBg, contentColor = if (d.signed) DoctorStatusGood else DoctorStatusWarn, shape = RoundedCornerShape(50),
        modifier = Modifier.clearAndSetSemantics { contentDescription = full }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (d.signed) Icons.Default.Lock else Icons.Default.EditNote, null, Modifier.size(16.dp))
            Text(if (d.signed) t("Signed and locked", "साइन होकर बंद") else t("Not signed yet", "अभी साइन नहीं"), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** One row of the doctor's Goals of care list: version, date and whether it is signed. */
@Composable internal fun DoctorGocDiscussionRow(d: DoctorGocDiscussion, onOpen: () -> Unit) { val t = careText()
    Surface(onClick = onOpen, color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("doctor-goc-${d.id}")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(DoctorCardConversation, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, null, tint = RustText, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Version ", "संस्करण ") + d.record.version + " · " + d.at.take(10), fontFamily = SaharaTitleFont, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                DoctorGocStatusChip(d)
            }
            Icon(Icons.Default.ChevronRight, null, tint = DoctorCardSub)
        }
    }
}

/** Sections I to IV of the legal form, drawn as one clean paper document: numbered section headings, ruled rows, tick boxes, tables and signature boxes. Every word comes from [GocForm]. */
@Composable internal fun DoctorGocFormSections(d: DoctorGocDiscussion) { val hindi = careHindi()
    val sections = d.sections()
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("doctor-goc-form")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            sections.forEach { section -> GocDocSection(section, hindi) }
        }
    }
}

private val GocDocSignatureHeads = setOf("Signature / thumb impression", "Signature", "SIGNATURE")

@Composable private fun GocDocSection(section: GocFormSection, hindi: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("goc-form-section-${section.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).border(1.5.dp, Rust, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(section.id, color = RustText, fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Text(section.title.pick(hindi), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
        }
        HorizontalDivider(thickness = 2.dp, color = Rust)
        section.groups.forEach { g ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (g.title.en.isNotBlank()) Text(g.title.pick(hindi), color = RustText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
                g.rows.forEachIndexed { i, row -> if (i > 0) HorizontalDivider(color = DoctorHairline); GocDocRow(row, hindi) }
                g.table?.let { table -> if (section.id == "IV" && table.head.any { it.en in GocDocSignatureHeads }) GocDocSignatures(table, hindi) else GocDocTable(table, hindi) }
            }
        }
    }
}

@Composable private fun GocDocRow(row: GocFormRow, hindi: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (row.label.en.isNotBlank()) Text(row.label.pick(hindi), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
        if (row.value.en.isNotBlank()) Text(row.value.pick(hindi), fontSize = 16.sp, lineHeight = 23.sp, fontWeight = if (row.open) FontWeight.Normal else FontWeight.SemiBold,
            fontStyle = if (row.open) FontStyle.Italic else FontStyle.Normal, color = if (row.open) DoctorCardFaint else DoctorCardInk)
        row.ticks.forEach { tick -> GocDocTick(tick.on, tick.text.pick(hindi) + if (tick.detail.isBlank()) "" else " " + tick.detail) }
    }
}

/** A drawn tick box like the paper form's: a ticked box is filled Rust with a check and its text is bold. Indented options keep the form's indent. */
@Composable private fun GocDocTick(on: Boolean, text: String) { val t = careText()
    val indent = text.startsWith("    ")
    Row(Modifier.fillMaxWidth().padding(start = if (indent) 24.dp else 0.dp).semantics(mergeDescendants = true) { stateDescription = if (on) t("Ticked", "निशान लगा") else t("Not ticked", "निशान नहीं") }, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 2.dp).size(18.dp).background(if (on) Rust else Paper, RoundedCornerShape(4.dp)).border(1.5.dp, if (on) Rust else DoctorCardFaint, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        if (text.isNotBlank()) Text(text.trimStart(), Modifier.weight(1f), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) DoctorCardInk else DoctorCardSub)
    }
}

/** One table cell: lines that start with a box are tick boxes; "Not discussed" is muted and italic. */
@Composable private fun GocDocCell(cell: Bi, hindi: Boolean, head: Boolean, single: Boolean) {
    val notDiscussed = GocForm.NOT_DISCUSSED.pick(hindi)
    cell.pick(hindi).lines().filter { it.isNotEmpty() || single }.forEach { line ->
        if (line.startsWith(GocForm.TICK_ON) || line.startsWith(GocForm.TICK_OFF)) GocDocTick(line.startsWith(GocForm.TICK_ON), line.drop(1).trim())
        else { val open = line == notDiscussed
            Text(line, fontSize = if (head) 13.sp else 14.sp, lineHeight = if (head) 18.sp else 20.sp, fontWeight = if (head) FontWeight.Bold else FontWeight.Normal, letterSpacing = if (head) 0.4.sp else 0.sp,
                fontStyle = if (open) FontStyle.Italic else FontStyle.Normal, color = if (open) DoctorCardFaint else if (head) DoctorCardSub else DoctorCardInk)
        }
    }
}

/** A ruled table like the paper: a cream header row and hairline cells. Three or four columns fit a phone; wider ones scroll sideways. */
@Composable private fun GocDocTable(table: GocFormTable, hindi: Boolean) {
    val n = table.head.size
    val weights = when {
        n == 3 && table.head[0].en == "LEVEL" -> listOf(0.6f, 3.6f, 1f)
        n == 3 -> listOf(1.1f, 1f, 1f)
        n == 4 -> listOf(1.8f, 1f, 1f, 0.8f)
        else -> List(n) { 1f }
    }
    val wide = n >= 5
    val shape = RoundedCornerShape(14.dp)
    Column((if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()).clip(shape).border(1.dp, DoctorHairline, shape)) {
        (listOf(table.head) + table.rows).forEachIndexed { index, cells ->
            if (index > 0) HorizontalDivider(color = DoctorHairline)
            Row((if (wide) Modifier.widthIn(min = 560.dp) else Modifier.fillMaxWidth()).height(IntrinsicSize.Min).background(if (index == 0) Cream else Paper)) {
                cells.forEachIndexed { i, cell ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(DoctorHairline))
                    Column((if (wide) Modifier.width(112.dp) else Modifier.weight(weights.getOrElse(i) { 1f })).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GocDocCell(cell, hindi, head = index == 0, single = cells.size == 1)
                    }
                }
            }
        }
    }
}

/**
 * Section 19 as signature boxes: the declaration's tick boxes (when the first head cell holds them), then one ruled box per signer, with the form's
 * column names as field labels and an empty signing box where nobody has signed yet.
 */
@Composable private fun GocDocSignatures(table: GocFormTable, hindi: Boolean) {
    val first = table.head.first()
    val declaration = first.pick(hindi).let { it.startsWith(GocForm.TICK_ON) || it.startsWith(GocForm.TICK_OFF) }
    if (declaration) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { GocDocCell(first, hindi, head = false, single = false) }
    val shape = RoundedCornerShape(14.dp)
    table.rows.forEach { cells ->
        Column(Modifier.fillMaxWidth().border(1.dp, DoctorHairline, shape).background(Paper, shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cells.forEachIndexed { i, cell ->
                val head = table.head.getOrNull(i)
                val value = cell.pick(hindi).trim()
                val rowName = i == 0 && (declaration || head == null || head.en.isBlank() || head.en == "ROLE" || head.en.startsWith("Witness"))
                when {
                    rowName -> if (value.isNotBlank()) Text(value, fontFamily = SaharaTitleFont, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    head != null && head.en in GocDocSignatureHeads -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(head.pick(hindi), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
                        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).border(1.dp, if (value.isBlank()) DoctorCardFaint else DoctorStatusGood, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                            if (value.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = DoctorStatusGood, modifier = Modifier.size(18.dp))
                                Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorStatusGood)
                            }
                        }
                    }
                    head != null -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(head.pick(hindi), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
                        Text(value.ifBlank { " " }, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk, modifier = Modifier.fillMaxWidth())
                        HorizontalDivider(color = DoctorCardFaint)
                    }
                }
            }
        }
    }
}

/** The PDF button: writes the form's sections with [DoctorGocPdf] and opens the share sheet. */
@Composable internal fun DoctorGocPdfButton(d: DoctorGocDiscussion, onProblem: (String) -> Unit = {}) { val t = careText(); val hindi = careHindi()
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val status = doctorGocStatus(d)
    var busy by remember(d.id) { mutableStateOf(false) }
    val failed = t("Couldn't make the PDF. Try again.", "PDF नहीं बना। फिर कोशिश करें।")
    OutlinedButton(onClick = {
        if (!busy) { busy = true; scope.launch {
            val file = runCatching { withContext(Dispatchers.IO) { DoctorGocPdf.write(context, d, hindi, status) } }.getOrNull()
            busy = false
            if (file == null) onProblem(failed) else runCatching { DoctorGocPdf.share(context, file, t("Share PDF", "PDF साझा करें")) }.onFailure { onProblem(t("No app is available to share the PDF.", "PDF साझा करने के लिए कोई ऐप नहीं है।")) }
        } }
    }, enabled = !busy, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Rust), colors = ButtonDefaults.outlinedButtonColors(contentColor = RustText), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-goc-pdf")) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = RustText, strokeWidth = 2.dp) else Icon(Icons.Default.PictureAsPdf, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp)); Text("PDF", fontSize = 16.sp)
    }
}

/** The legal-form view of one discussion: status, PDF, Sections I to IV, then the doctor's note. */
@Composable internal fun DoctorGocFormPage(d: DoctorGocDiscussion, onBack: () -> Unit) { val t = careText()
    var problem by remember(d.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("doctor-goc-page"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = RustText), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-goc-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text(t("Back", "वापस"), fontSize = 16.sp) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading(t("Goals-of-care note", "देखभाल के लक्ष्यों का नोट"), 26)
                Text(t("Version ", "संस्करण ") + d.record.version + " · " + d.at.take(16).replace('T', ' '), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                DoctorGocStatusChip(d)
            }
            DoctorGocPdfButton(d) { problem = it }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.announce()) }
        DoctorGocFormSections(d)
        if (d.note.isNotBlank()) Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("doctor-goc-note")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Doctor's note", "डॉक्टर का नोट"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                SelectionContainer { Text(d.note, fontSize = 16.sp, lineHeight = 24.sp, color = DoctorCardInk) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** The person's discussions from this phone's plan store, refreshed when the screen opens. */
@Composable internal fun rememberDoctorGocDiscussions(vm: CareModel, personId: String): List<DoctorGocDiscussion> {
    val context = LocalContext.current
    var found by remember(vm.storageNamespace, personId) { mutableStateOf(emptyList<DoctorGocDiscussion>()) }
    LaunchedEffect(vm.storageNamespace, personId) {
        found = if (personId.isBlank()) emptyList() else withContext(Dispatchers.IO) {
            runCatching { DoctorGocDiscussions.list(CarePlanStore(context.applicationContext, vm.storageNamespace).load(personId).ectprs, personId) }.getOrDefault(emptyList())
        }
    }
    return found
}
