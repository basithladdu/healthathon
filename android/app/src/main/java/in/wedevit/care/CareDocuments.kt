package `in`.wedevit.care

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Last listed prescription originals per account and person, so Documents opens with them instead of popping them in after a file read. */
private val PrescriptionOriginalsCache = java.util.concurrent.ConcurrentHashMap<String, List<CareReport>>()

@Composable fun CareDocuments(vm: CareModel, onRead: (CareVersion) -> Unit, onTalk: () -> Unit) {
    val savedReports by vm.reports.collectAsStateWithLifecycle()
    val savedNotes by vm.versions.collectAsStateWithLifecycle()
    val person by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val clinician by vm.clinician.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val originalsKey = "${vm.storageNamespace}:$person"
    var prescriptionOriginals by remember(person) { mutableStateOf(PrescriptionOriginalsCache[originalsKey].orEmpty()) }
    LaunchedEffect(person, busy) {
        if (person.isNotBlank() && !busy) prescriptionOriginals = withContext(Dispatchers.IO) { runCatching { MedicineSourceStore(context.applicationContext, vm.storageNamespace).list(person) }.getOrDefault(emptyList()) }.also { PrescriptionOriginalsCache[originalsKey] = it }
    }
    val reports = (savedReports.filter { it.personId == person } + prescriptionOriginals).distinctBy { it.id }
    val notes = savedNotes.filter { it.personId == person }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var importingFor by rememberSaveable { mutableStateOf("") }
    var remove by remember { mutableStateOf<CareReport?>(null) }
    var reviewId by rememberSaveable { mutableStateOf<String?>(null) }
    val cancerStore = remember(vm.storageNamespace) { CancerDocumentStore(context, vm.storageNamespace) }
    var cancerChoices by remember(vm.storageNamespace) { mutableStateOf(cancerStore.all()) }
    var marking by remember { mutableStateOf<CareReport?>(null) }
    var renaming by remember { mutableStateOf<CareReport?>(null) }
    var newName by remember { mutableStateOf("") }
    var scanPath by rememberSaveable { mutableStateOf("") }
    fun cancerType(report: CareReport) = CancerDocumentRules.type(report.name, cancerChoices[report.id])
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current; val t = careText()
    var textMatches by remember(person) { mutableStateOf(emptyList<DocumentSearchExcerpt>()) }
    var searchedText by remember(person) { mutableStateOf("") }
    var searching by remember(person) { mutableStateOf(false) }
    var textError by remember(person) { mutableStateOf("") }
    reports.firstOrNull { it.id == reviewId }?.let { report -> CareDocumentReview(vm, report) { reviewId = null }; return }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && importingFor.isNotEmpty()) vm.importReport(uri, importingFor)
        importingFor = ""
    }
    val addReport = { importingFor = person; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = File(scanPath)
        if (saved && file.isFile && file.length() > 0 && importingFor.isNotEmpty()) vm.importReport(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), importingFor)
        else if (scanPath.isNotBlank()) file.delete()
        importingFor = ""; scanPath = ""
    }
    /** The photo goes to the app's cache, is copied into the private report folder like a picked file, and the cache is cleared on the next scan. */
    val scanReport = {
        val directory = File(context.cacheDir, "report-scan").apply { deleteRecursively(); mkdirs() }
        val file = File(directory, "Scan ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm"))}.jpg")
        runCatching { check(file.createNewFile()); importingFor = person; scanPath = file.absolutePath; camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) }
            .onFailure { file.delete(); importingFor = ""; scanPath = ""; vm.message.value = t("No camera app is available. Choose a file instead.", "कैमरा ऐप नहीं मिला। फ़ाइल चुनें।") }
        Unit
    }
    val bothLanguages: (String, String) -> String = { en, hi -> "$en $hi" }
    val visibleReports = reports.filter { report -> filter != "Care notes" && (filter != "Cancer" || cancerType(report) != null) && DocumentSearchWords.matchesName(search, "${report.name} ${cancerType(report)?.let { CancerDocumentRules.label(it, bothLanguages) }.orEmpty()}") }
    val visibleNotes = notes.filter { filter !in setOf("Reports", "Cancer") && DocumentSearchWords.matchesName(search, "Goals of care देखभाल के लक्ष्य ${it.personName} ${it.doctor} Version ${it.version}") }

    fun openReport(report: CareReport, share: Boolean) {
        try {
            val intent = vm.reportFiles.intent(report, share)
            open(context, if (share) Intent.createChooser(intent, t("Share report", "रिपोर्ट भेजें")) else intent)
        } catch (_: Exception) { vm.message.value = t("Couldn't open this report. Add it again from Files.", "यह रिपोर्ट नहीं खुल सकी। Files से इसे फिर जोड़ें।") }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("documents-list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(t("Documents", "कागज़ात"), 30)
                Text(t("${notes.size} care ${if (notes.size == 1) "note" else "notes"} · ${reports.size} ${if (reports.size == 1) "report" else "reports"}", "${notes.size} देखभाल नोट · ${reports.size} रिपोर्ट"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canEdit) FilledTonalIconButton(onClick = addReport, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(48.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Gold, contentColor = Ink)) { Icon(Icons.Default.UploadFile, t("Add a report", "रिपोर्ट जोड़ें")) }
        } }
        if (vm.demoMode) item { Text(t("Reports you add stay on this phone in the walkthrough and are removed with it. Opening or sharing a file uses your phone's normal share sheet.", "डेमो में जोड़ी गई रिपोर्टें इसी फ़ोन पर रहती हैं और डेमो के साथ हट जाती हैं। फ़ाइल खोलने या भेजने के लिए फ़ोन का सामान्य तरीका इस्तेमाल होता है।"), style = MaterialTheme.typography.bodySmall) }
        else item { Text(t("Report files stay on this phone. They are not uploaded, and your care team sees them only if you share them. Lab results you copy out of a report are saved with your care record.", "रिपोर्ट की फ़ाइलें इसी फ़ोन पर रहती हैं। ये अपलोड नहीं होतीं; देखभाल टीम इन्हें तभी देखती है जब आप भेजें। रिपोर्ट से कॉपी किए गए लैब नतीजे देखभाल रिकॉर्ड में सेव होते हैं।"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("documents-on-phone")) }
        if (canEdit) item { OutlinedButton(onClick = scanReport, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("document-scan")) { Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Scan a report with the camera", "कैमरे से रिपोर्ट स्कैन करें")) } }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(t("Search documents", "कागज़ात खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, t("Clear document search", "खोज मिटाएँ")) } },
            modifier = Modifier.fillMaxWidth().testTag("document-search"), shape = RoundedCornerShape(16.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Care notes", "Reports", "Cancer").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(when (label) { "Care notes" -> t("Care notes", "देखभाल नोट"); "Reports" -> t("Reports", "रिपोर्टें"); "Cancer" -> t("Cancer documents", "कैंसर के कागज़"); else -> t("All", "सभी") }) }, modifier = Modifier.testTag("document-filter-$label")) }
        } }
        item { TextButton(onClick = {
            val query = search.trim(); val capturedPerson = person
            searching = true; textError = ""
            scope.launch {
                try {
                    val found = withContext(Dispatchers.IO) {
                        val store = CareDocumentReviewStore(context.applicationContext, vm.storageNamespace)
                        val saved = reports.mapNotNull { report -> store.loadVerified(report, vm.reportFiles.file(report)) }
                        DocumentEvidenceRules.find(query, saved)
                    }
                    if (vm.selected.value == capturedPerson) { textMatches = found; searchedText = query }
                } catch (_: Exception) { textError = t("Couldn't search saved report text. The originals are unchanged. Try again.", "रिपोर्ट के लिखे हिस्से में खोज नहीं हो सकी। असली फ़ाइलें वैसी ही हैं। फिर कोशिश करें।") }
                finally { searching = false }
            }
        }, enabled = search.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth()) { Text(if (searching) t("Searching your reports…", "रिपोर्टों में खोज रहे हैं…") else t("Find in reviewed report text", "जाँची गई रिपोर्टों में खोजें")) } }
        if (textError.isNotBlank()) item { Text(textError, color = MaterialTheme.colorScheme.error) }
        if (searchedText.isNotBlank()) {
            item { SectionTitle(t("From your reports", "आपकी रिपोर्टों से")) }
            if (textMatches.isEmpty()) item { SurfaceCard { Text(t("Not found in your reports", "आपकी रिपोर्टों में नहीं मिला"), fontWeight = FontWeight.SemiBold); Text(t("No answer was generated. Try fewer words, or open a report and review its text first.", "कोई जवाब नहीं बनाया गया। कम शब्दों से खोजें, या पहले रिपोर्ट खोलकर उसका लिखा हिस्सा जाँचें।"), style = MaterialTheme.typography.bodySmall) } }
            items(textMatches, key = { "${it.reportId}:${it.page}:${it.line}" }) { match ->
                reports.firstOrNull { it.id == match.reportId }?.let { report -> SurfaceCard(Sage) {
                    Text(report.name, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text(t("Page ${match.page} · line ${match.line}", "पेज ${match.page} · पंक्ति ${match.line}"), style = MaterialTheme.typography.labelMedium)
                    Text(match.quote)
                    TextButton(onClick = { reviewId = report.id }, colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(t("Read source and copy fact", "मूल पढ़ें और बात कॉपी करें")) }
                } }
            }
        }
        if (visibleNotes.isEmpty() && visibleReports.isEmpty()) item { SurfaceCard {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(36.dp), tint = Rust)
            Spacer(Modifier.height(12.dp)); Text(if (search.isNotBlank()) t("No documents found", "कोई कागज़ नहीं मिला") else when (filter) { "Reports" -> t("No reports yet", "अभी कोई रिपोर्ट नहीं"); "Care notes" -> t("No care notes yet", "अभी कोई देखभाल नोट नहीं"); "Cancer" -> t("No cancer documents yet", "अभी कैंसर का कोई कागज़ नहीं"); else -> t("Keep your care together", "देखभाल के सारे कागज़ एक जगह रखें") }, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            Spacer(Modifier.height(6.dp)); Text(if (search.isNotBlank()) t("Try a name or clear your search.", "कोई नाम लिखें या खोज मिटाएँ।") else if (filter == "Cancer") t("PET-CT, biopsy, histopathology and staging reports appear here. Add a report, then tap “Cancer document?” on it.", "पेट-सीटी, बायोप्सी, हिस्टोपैथोलॉजी और स्टेजिंग रिपोर्टें यहाँ दिखेंगी। रिपोर्ट जोड़ें, फिर उस पर “कैंसर का कागज़?” दबाएँ।") else t("Your reviewed care notes and reports appear here.", "जाँचे गए देखभाल नोट और रिपोर्टें यहाँ दिखेंगी।"), style = MaterialTheme.typography.bodyMedium)
            if (search.isNotBlank()) TextButton(onClick = { search = ""; filter = "All" }) { Text(t("Clear search", "खोज मिटाएँ")) }
            else if (filter != "Care notes" && canEdit) TextButton(onClick = addReport, enabled = !busy) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add a report", "रिपोर्ट जोड़ें")) }
        } }
        if (visibleNotes.isNotEmpty()) item { SectionTitle(t("Care notes", "देखभाल नोट")) }
        items(visibleNotes, key = { it.id }) { note ->
            Card(onClick = { onRead(note) }, modifier = Modifier.animateItem().fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (note.id == notes.firstOrNull()?.id) Gold else Paper)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, Modifier.size(26.dp)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Goals of care", "देखभाल के लक्ष्य"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                        Text(t("Version ${note.version}", "संस्करण ${note.version}") + if (note.id == notes.firstOrNull()?.id) t(" · Latest", " · नया") else t(" · Earlier", " · पुराना"), style = MaterialTheme.typography.bodySmall)
                        Text("${date(note.published)} · ${note.doctor}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Read version ${note.version}", "संस्करण ${note.version} पढ़ें"), Modifier.size(20.dp))
                }
            }
        }
        if (visibleReports.isNotEmpty()) item { SectionTitle(t("Reports", "रिपोर्टें")) }
        items(visibleReports, key = { it.id }) { report ->
            Surface(Modifier.animateItem().fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Paper) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { openReport(report, false) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = if (report.mime == "application/pdf") Peach else Sage) { Icon(if (report.mime == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Image, null, Modifier.padding(10.dp).size(23.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(report.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = SaharaTitleFont)
                            Text(Formatter.formatShortFileSize(context, report.bytes), style = MaterialTheme.typography.bodySmall)
                            Text(date(report.created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            cancerType(report)?.let { type -> Text(CancerDocumentRules.label(type, t) + if (cancerChoices[report.id].isNullOrBlank()) t(" · from file name", " · नाम से अंदाज़ा") else "", style = MaterialTheme.typography.labelMedium, color = RustText, modifier = Modifier.testTag("report-cancer-${report.id}")) }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { openReport(report, false) }, modifier = Modifier.testTag("report-open-${report.id}")) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Open", "खोलें")) }
                        TextButton(onClick = { reviewId = report.id }, modifier = Modifier.testTag("report-review-${report.id}")) { Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Read and copy facts", "पढ़ें और बातें कॉपी करें")) }
                        TextButton(onClick = { openReport(report, true) }, modifier = Modifier.testTag("report-share-${report.id}")) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Share", "भेजें")) }
                        if (canEdit && prescriptionOriginals.none { it.id == report.id }) TextButton(enabled = !busy, onClick = { newName = report.name; renaming = report }, modifier = Modifier.testTag("report-rename-${report.id}")) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Rename", "नाम बदलें")) }
                        if (canEdit && prescriptionOriginals.none { it.id == report.id }) TextButton(onClick = { marking = report }, modifier = Modifier.testTag("report-mark-cancer-${report.id}")) { Icon(Icons.Default.LocalHospital, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (cancerType(report) == null) t("Cancer document?", "कैंसर का कागज़?") else t("Change type", "प्रकार बदलें")) }
                        if (canEdit && prescriptionOriginals.none { it.id == report.id }) TextButton(enabled = !busy, onClick = { remove = report }, modifier = Modifier.testTag("report-remove-${report.id}")) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Remove", "हटाएँ")) }
                    }
                }
            }
        }
        if (search.isBlank() && filter != "Reports") item { TextButton(onClick = onTalk, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (clinician) t("Start a conversation", "बातचीत शुरू करें") else t("Talk with your doctor", "अपने डॉक्टर से बात करें")) } }
    }
    remove?.let { report -> AlertDialog(onDismissRequest = { if (!busy) remove = null }, title = { Text(t("Remove this report?", "यह रिपोर्ट हटाएँ?")) }, text = { Text(report.name + t("\n\nSahara's copy on this phone is deleted. A file you picked from Files stays there; a camera scan is gone for good.", "\n\nइस फ़ोन से Sahara की कॉपी मिट जाएगी। Files से चुनी गई असली फ़ाइल वहीं रहेगी; कैमरे से किया स्कैन हमेशा के लिए हट जाएगा।")) },
        confirmButton = { TextButton(onClick = { vm.removeReport(report) { remove = null; cancerStore.remove(report.id); cancerChoices = cancerStore.all() } }, enabled = !busy) { Text(t("Remove report", "रिपोर्ट हटाएँ")) } },
        dismissButton = { TextButton(onClick = { remove = null }, enabled = !busy) { Text(t("Keep", "रखें")) } }) }
    marking?.let { report ->
        val current = cancerType(report)
        AlertDialog(onDismissRequest = { marking = null }, title = { Text(t("What kind of document?", "यह कौन सा कागज़ है?")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(report.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t("Only this label changes. It stays on this phone, like the report file.", "सिर्फ़ यह लेबल बदलेगा। यह रिपोर्ट फ़ाइल की तरह इसी फ़ोन पर रहता है।"), style = MaterialTheme.typography.bodySmall)
            (CancerDocumentRules.types + CancerDocumentRules.NOT_CANCER).forEach { type ->
                val chosen = if (type == CancerDocumentRules.NOT_CANCER) current == null else current == type
                FilterChip(selected = chosen, onClick = { cancerStore.set(report.id, type); cancerChoices = cancerStore.all(); marking = null },
                    label = { Text(if (type == CancerDocumentRules.NOT_CANCER) t("Not a cancer document", "कैंसर का कागज़ नहीं") else CancerDocumentRules.label(type, t)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cancer-type-$type"))
            }
        } }, confirmButton = { TextButton(onClick = { marking = null }) { Text(t("Close", "बंद करें")) } })
    }
    renaming?.let { report ->
        val nameError = ReportNameRules.error(newName)
        AlertDialog(onDismissRequest = { if (!busy) renaming = null }, title = { Text(t("Rename report", "रिपोर्ट का नाम बदलें")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(newName, { newName = it.take(160) }, label = { Text(t("Report name", "रिपोर्ट का नाम")) }, singleLine = true, isError = nameError != null, supportingText = nameError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("report-rename-field"))
            Text(t("Only the name in Sahara changes. The file and the facts copied from it stay the same.", "सिर्फ़ Sahara में नाम बदलेगा। फ़ाइल और उससे कॉपी की गई बातें वैसी ही रहेंगी।"), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { vm.renameReport(report, newName) { renaming = null } }, enabled = !busy && nameError == null && newName.trim() != report.name, modifier = Modifier.testTag("report-rename-save")) { Text(t("Save", "सेव करें")) } },
            dismissButton = { TextButton(onClick = { renaming = null }, enabled = !busy) { Text(t("Cancel", "रद्द करें")) } })
    }
}
