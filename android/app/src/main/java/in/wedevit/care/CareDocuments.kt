package `in`.wedevit.care

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun CareDocuments(vm: CareModel, onRead: (CareVersion) -> Unit, onTalk: () -> Unit) {
    val savedReports by vm.reports.collectAsStateWithLifecycle()
    val savedNotes by vm.versions.collectAsStateWithLifecycle()
    val person by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val clinician by vm.clinician.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prescriptionOriginals by remember(person) { mutableStateOf(emptyList<CareReport>()) }
    LaunchedEffect(person, busy) {
        if (person.isNotBlank() && !busy) prescriptionOriginals = withContext(Dispatchers.IO) { runCatching { MedicineSourceStore(context.applicationContext, vm.accountNamespace).list(person) }.getOrDefault(emptyList()) }
    }
    val reports = (savedReports.filter { it.personId == person } + prescriptionOriginals).distinctBy { it.id }
    val notes = savedNotes.filter { it.personId == person }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var importingFor by rememberSaveable { mutableStateOf("") }
    var remove by remember { mutableStateOf<CareReport?>(null) }
    var review by remember { mutableStateOf<CareReport?>(null) }
    var textMatches by remember(person) { mutableStateOf(emptyList<DocumentSearchExcerpt>()) }
    var searchedText by remember(person) { mutableStateOf("") }
    var searching by remember(person) { mutableStateOf(false) }
    var textError by remember(person) { mutableStateOf("") }
    review?.let { report -> CareDocumentReview(vm, report) { review = null }; return }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && importingFor.isNotEmpty()) vm.importReport(uri, importingFor)
        importingFor = ""
    }
    val addReport = { importingFor = person; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) }
    val visibleReports = reports.filter { filter != "Care notes" && it.name.contains(search.trim(), true) }
    val visibleNotes = notes.filter { filter != "Reports" && "Goals of care ${it.personName} ${it.doctor} Version ${it.version}".contains(search.trim(), true) }

    fun openReport(report: CareReport, share: Boolean) {
        try {
            val intent = vm.reportFiles.intent(report, share)
            open(context, if (share) Intent.createChooser(intent, "Share report") else intent)
        } catch (_: Exception) { vm.message.value = "Couldn't open this report. Add it again from Files." }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("documents-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading("Documents", 30)
                Text("${notes.size} care ${if (notes.size == 1) "note" else "notes"} · ${reports.size} ${if (reports.size == 1) "report" else "reports"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canEdit) FilledTonalIconButton(onClick = addReport, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(48.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Gold, contentColor = Ink)) { Icon(Icons.Default.UploadFile, "Add a report") }
        } }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Search documents") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear document search") } },
            modifier = Modifier.fillMaxWidth().testTag("document-search"), shape = RoundedCornerShape(16.dp), singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Care notes", "Reports").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) }
        } }
        item { TextButton(onClick = {
            val query = search.trim(); val capturedPerson = person
            searching = true; textError = ""
            scope.launch {
                try {
                    val found = withContext(Dispatchers.IO) {
                        val store = CareDocumentReviewStore(context.applicationContext, vm.accountNamespace)
                        val saved = reports.mapNotNull { report -> store.loadVerified(report, vm.reportFiles.file(report)) }
                        DocumentEvidenceRules.find(query, saved)
                    }
                    if (vm.selected.value == capturedPerson) { textMatches = found; searchedText = query }
                } catch (_: Exception) { textError = "Couldn't search saved report text. The originals are unchanged. Try again." }
                finally { searching = false }
            }
        }, enabled = search.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth()) { Text(if (searching) "Finding source passages…" else "Find in reviewed report text") } }
        if (textError.isNotBlank()) item { Text(textError, color = MaterialTheme.colorScheme.error) }
        if (searchedText.isNotBlank()) {
            item { SectionTitle("From your reports") }
            if (textMatches.isEmpty()) item { SurfaceCard { Text("No supported passage found", fontWeight = FontWeight.SemiBold); Text("No answer was generated. Try fewer words, or open a report and review its text first.", style = MaterialTheme.typography.bodySmall) } }
            items(textMatches, key = { "${it.reportId}:${it.page}:${it.line}" }) { match ->
                reports.firstOrNull { it.id == match.reportId }?.let { report -> SurfaceCard(Sage) {
                    Text(report.name, fontWeight = FontWeight.SemiBold)
                    Text("Page ${match.page} · line ${match.line}", style = MaterialTheme.typography.labelMedium)
                    Text(match.quote)
                    TextButton(onClick = { review = report }) { Text("Read source and copy fact") }
                } }
            }
        }
        if (visibleNotes.isEmpty() && visibleReports.isEmpty()) item { SurfaceCard {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(36.dp), tint = Rust)
            Spacer(Modifier.height(12.dp)); Text(if (search.isNotBlank()) "No documents found" else when (filter) { "Reports" -> "No reports yet"; "Care notes" -> "No care notes yet"; else -> "Keep your care together" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp)); Text(if (search.isNotBlank()) "Try a name or clear your search." else "Your reviewed care notes and reports appear here.", style = MaterialTheme.typography.bodyMedium)
            if (search.isNotBlank()) TextButton(onClick = { search = ""; filter = "All" }) { Text("Clear search") }
            else if (filter != "Care notes" && canEdit) TextButton(onClick = addReport, enabled = !busy) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add a report") }
        } }
        if (visibleNotes.isNotEmpty()) item { SectionTitle("Care notes") }
        items(visibleNotes, key = { it.id }) { note ->
            Card(onClick = { onRead(note) }, modifier = Modifier.animateItem().fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (note.id == notes.firstOrNull()?.id) Gold else Paper)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, Modifier.size(26.dp)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Goals of care", fontWeight = FontWeight.SemiBold)
                        Text("Version ${note.version}" + if (note.id == notes.firstOrNull()?.id) " · Latest" else " · Earlier", style = MaterialTheme.typography.bodySmall)
                        Text("${date(note.published)} · ${note.doctor}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Read version ${note.version}", Modifier.size(20.dp))
                }
            }
        }
        if (visibleReports.isNotEmpty()) item { SectionTitle("Reports") }
        items(visibleReports, key = { it.id }) { report ->
            var menu by remember { mutableStateOf(false) }
            Surface(Modifier.animateItem().fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Paper) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { openReport(report, false) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = if (report.mime == "application/pdf") Peach else Sage) { Icon(if (report.mime == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Image, null, Modifier.padding(10.dp).size(23.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(report.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(Formatter.formatShortFileSize(context, report.bytes), style = MaterialTheme.typography.bodySmall)
                            Text(date(report.created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.padding(end = 4.dp)) { Icon(Icons.Default.MoreHoriz, "Options for ${report.name}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }, onClick = { menu = false; openReport(report, false) })
                            DropdownMenuItem(text = { Text("Read and copy facts") }, leadingIcon = { Icon(Icons.Default.DocumentScanner, null) }, onClick = { menu = false; review = report })
                            DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; openReport(report, true) })
                            if (canEdit && prescriptionOriginals.none { it.id == report.id }) DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, enabled = !busy, onClick = { menu = false; remove = report })
                        }
                    }
                }
            }
        }
        if (search.isBlank() && filter != "Reports") item { TextButton(onClick = onTalk, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (clinician) "Start a conversation" else "Talk with your doctor") } }
    }
    remove?.let { report -> AlertDialog(onDismissRequest = { if (!busy) remove = null }, title = { Text("Remove this report?") }, text = { Text("${report.name}\n\nYour original file stays in Files.") },
        confirmButton = { TextButton(onClick = { vm.removeReport(report) { remove = null } }, enabled = !busy) { Text("Remove report") } },
        dismissButton = { TextButton(onClick = { remove = null }, enabled = !busy) { Text("Keep") } }) }
}
