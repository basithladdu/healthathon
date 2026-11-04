package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    return d.signOff?.let { t("Signed and locked", "साइन होकर बंद") + (if (it.signers.isNotEmpty()) ": " + it.signers.joinToString(", ") else "") } ?: t("The note is not signed yet.", "नोट पर अभी हस्ताक्षर नहीं हुए।")
}

/** One row of the doctor's Goals of care list: version, date and whether it is signed. */
@Composable internal fun DoctorGocDiscussionRow(d: DoctorGocDiscussion, onOpen: () -> Unit) { val t = careText()
    Surface(onClick = onOpen, color = DoctorCardConversation, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("doctor-goc-${d.id}")) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("Version ", "संस्करण ") + d.record.version + " · " + d.at.take(10), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            Text(doctorGocStatus(d), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (d.signed) DoctorStatusGood else RustText)
        }
    }
}

/** Sections I to IV as the legal form, the same view the talk screen shows ([GocFormView]). */
@Composable internal fun DoctorGocFormSections(d: DoctorGocDiscussion) {
    GocFormView(d.sections(), Modifier.testTag("doctor-goc-form"))
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
    }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-goc-pdf")) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("PDF") }
}

/** The legal-form view of one discussion: status, PDF, Sections I to IV, then the doctor's note. */
@Composable internal fun DoctorGocFormPage(d: DoctorGocDiscussion, onBack: () -> Unit) { val t = careText()
    var problem by remember(d.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("doctor-goc-page"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-goc-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text(t("Back", "वापस")) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(t("Goals-of-care note", "देखभाल के लक्ष्यों का नोट"), 26)
                Text(t("Version ", "संस्करण ") + d.record.version + " · " + d.at.take(16).replace('T', ' '), fontSize = 13.sp, color = DoctorCardSub)
                Text(doctorGocStatus(d), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (d.signed) DoctorStatusGood else RustText)
            }
            DoctorGocPdfButton(d) { problem = it }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        DoctorGocFormSections(d)
        if (d.note.isNotBlank()) Surface(color = DoctorCardConversation, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("doctor-goc-note")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Doctor's note", "डॉक्टर का नोट"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                SelectionContainer { Text(d.note, fontSize = 15.sp, lineHeight = 23.sp, color = DoctorCardInk) }
            }
        }
        Spacer(Modifier.height(48.dp))
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
