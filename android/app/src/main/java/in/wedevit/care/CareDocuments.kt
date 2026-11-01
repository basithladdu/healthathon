@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
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

@Composable fun CareDocuments(vm: CareModel, onRead: (CareVersion) -> Unit, onTalk: () -> Unit, onCalendarAt: ((Long) -> Unit)? = null, onLabs: (() -> Unit)? = null, onCare: (() -> Unit)? = null) {
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
    val folderStore = remember(vm.storageNamespace) { DocumentFolderStore(context, vm.storageNamespace) }
    var folders by remember(vm.storageNamespace) { mutableStateOf(folderStore.all()) }
    var folderFilter by rememberSaveable { mutableStateOf("") }
    var moving by remember { mutableStateOf<CareReport?>(null) }
    var asking by rememberSaveable { mutableStateOf(false) }
    var timeline by rememberSaveable { mutableStateOf(false) }
    var reviewPage by rememberSaveable { mutableIntStateOf(1) }
    var sorting by remember { mutableStateOf(false) }
    fun folderOf(report: CareReport) = folders[report.id]?.folder ?: DocumentFolderRules.OTHER
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current; val t = careText()
    var textMatches by remember(person) { mutableStateOf(emptyList<DocumentSearchExcerpt>()) }
    var searchedText by remember(person) { mutableStateOf("") }
    var searching by remember(person) { mutableStateOf(false) }
    var textError by remember(person) { mutableStateOf("") }
    /** Sorting reads the first two pages on this phone, unless the person already checked the text. Nothing is uploaded. */
    LaunchedEffect(reports.map { it.id }) {
        val unsorted = reports.filter { it.id !in folders }
        if (unsorted.isEmpty()) return@LaunchedEffect
        sorting = true
        try { unsorted.forEach { report ->
            val folder = if (prescriptionOriginals.any { it.id == report.id }) DocumentFolderRules.PRESCRIPTIONS else withContext(Dispatchers.IO) {
                val text = try {
                    val file = vm.reportFiles.file(report)
                    val reviewed = CareDocumentReviewStore(context.applicationContext, vm.storageNamespace).loadVerified(report, file)
                    reviewed?.pages?.joinToString("\n") { it.reviewedText } ?: CareDocumentReader(context).read(file, report, maxPages = 2).pages.joinToString("\n") { it.originalText }
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (_: Exception) { "" }
                DocumentFolderRules.sort(report.name, text)
            }
            folderStore.sorted(report.id, folder); folders = folderStore.all()
        } } finally { sorting = false }
    }
    reports.firstOrNull { it.id == reviewId }?.let { report -> CareDocumentReview(vm, report, startPage = reviewPage) { reviewId = null; reviewPage = 1 }; return }
    if (asking) { CareDocumentAsk(vm, reports, folders, onOpen = { report -> asking = false; reviewId = report.id }) { asking = false }; return }
    if (timeline) { CareTimeline(vm, reports, ::cancerType, ::folderOf, onOpenReport = { report, page -> reviewPage = page; reviewId = report.id }, onReadNote = onRead, onCalendarAt = onCalendarAt, onLabs = onLabs, onCare = onCare, onClose = { timeline = false }); return }
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
    val visibleReports = reports.filter { report -> filter != "Care notes" && (folderFilter.isEmpty() || folderOf(report) == folderFilter) && (filter != "Cancer" || cancerType(report) != null) && DocumentSearchWords.matchesName(search, "${report.name} ${cancerType(report)?.let { CancerDocumentRules.label(it, bothLanguages) }.orEmpty()}") }
    val visibleNotes = notes.filter { filter !in setOf("Reports", "Cancer") && folderFilter.isEmpty() && DocumentSearchWords.matchesName(search, "Goals of care देखभाल के लक्ष्य ${it.personName} ${it.doctor} Version ${it.version}") }

    fun openReport(report: CareReport, share: Boolean) {
        try {
            val intent = vm.reportFiles.intent(report, share)
            open(context, if (share) Intent.createChooser(intent, t("Share report", "रिपोर्ट भेजें")) else intent)
        } catch (_: Exception) { vm.message.value = t("Couldn't open this report. Add it again from Files.", "यह रिपोर्ट नहीं खुल सकी। Files से इसे फिर जोड़ें।") }
    }

    var addSheet by rememberSaveable { mutableStateOf(false) }
    // Two ways in: photograph a paper report, or pick a file that is already on the phone.
    if (addSheet) ModalBottomSheet(onDismissRequest = { addSheet = false }, containerColor = Cream) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Add document", "कागज़ जोड़ें"), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            AddDocOption(t("Scan with camera", "कैमरे से स्कैन करें"), t("Take a photo of a paper report or prescription.", "काग़ज़ की रिपोर्ट या पर्चे की फ़ोटो लें।"), Icons.Default.PhotoCamera, Peach, Modifier.testTag("document-scan")) { addSheet = false; scanReport() }
            AddDocOption(t("Upload a file", "फ़ाइल अपलोड करें"), t("Choose a PDF or photo already on this phone.", "इस फ़ोन पर पहले से रखी PDF या फ़ोटो चुनें।"), Icons.Default.UploadFile, SaharaSoftBlue, Modifier.testTag("document-upload")) { addSheet = false; addReport() }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("documents-list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(t("Documents", "कागज़ात"), 30)
                Text(run { val n = notes.size; val m = reports.size; (if (n == 1) t("1 care note", "1 देखभाल नोट") else t("$n care notes", "$n देखभाल नोट")) + " · " + (if (m == 1) t("1 report", "1 रिपोर्ट") else t("$m reports", "$m रिपोर्ट")) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canEdit) Button(onClick = { addSheet = true }, enabled = !busy, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("document-add")) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add document", "कागज़ जोड़ें"), fontWeight = FontWeight.SemiBold)
            }
        } }
        item { Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DocsTile(t("Care timeline", "देखभाल की समय-रेखा"), t("Key events from your documents", "आपके कागज़ों की मुख्य बातें"), Icons.Default.Timeline, Peach, Modifier.weight(1f).fillMaxHeight().testTag("care-timeline-open")) { timeline = true }
            if (reports.isNotEmpty()) DocsTile(t("Ask your documents", "अपने कागज़ों से पूछें"), t("Find dates, results and doses", "तारीख़, नतीजे और खुराक खोजें"), Icons.Default.QuestionAnswer, SaharaLavender, Modifier.weight(1f).fillMaxHeight().testTag("document-ask-open")) { asking = true }
        } }
        if (vm.demoMode) item { Row(verticalAlignment = Alignment.Top) { Icon(Icons.Default.Lock, null, Modifier.size(14.dp).padding(top = 2.dp), tint = DoctorCardSub); Spacer(Modifier.width(6.dp)); Text(t("In the demo, reports you add stay on this phone. They are removed when the demo ends.", "डेमो में जोड़ी गई रिपोर्टें इसी फ़ोन पर रहती हैं। डेमो ख़त्म होने पर ये हट जाती हैं।"), fontSize = 12.sp, color = DoctorCardSub) } }
        else item { Row(verticalAlignment = Alignment.Top) { Icon(Icons.Default.Lock, null, Modifier.size(14.dp).padding(top = 2.dp), tint = DoctorCardSub); Spacer(Modifier.width(6.dp)); Text(t("Report files stay on this phone. Your care team sees them only if you share them. Lab results you copy from a report are saved in your care record.", "रिपोर्ट की फ़ाइलें इसी फ़ोन पर रहती हैं। देखभाल टीम इन्हें तभी देखती है जब आप भेजें। रिपोर्ट से कॉपी किए लैब नतीजे आपके देखभाल रिकॉर्ड में सेव होते हैं।"), fontSize = 12.sp, color = DoctorCardSub) } }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(t("Search documents", "कागज़ात खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, t("Clear document search", "खोज मिटाएँ")) } },
            modifier = Modifier.fillMaxWidth().testTag("document-search"), shape = RoundedCornerShape(16.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Care notes", "Reports", "Cancer").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(when (label) { "Care notes" -> t("Care notes", "देखभाल नोट"); "Reports" -> t("Reports", "रिपोर्टें"); "Cancer" -> t("Cancer documents", "कैंसर के कागज़"); else -> t("All", "सभी") }) }, modifier = Modifier.testTag("document-filter-$label")) }
        } }
        if (reports.isNotEmpty()) item { Column {
            val counts = DocumentFolderRules.counts(reports.map(::folderOf))
            Text(if (sorting) t("Folders · sorting on this phone…", "फ़ोल्डर · इसी फ़ोन पर छाँट रहे हैं…") else t("Folders", "फ़ोल्डर"), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentFolderRules.folders.forEach { folder -> FilterChip(selected = folderFilter == folder, onClick = { folderFilter = if (folderFilter == folder) "" else folder },
                    label = { Text("${DocumentFolderRules.label(folder, t)} · ${counts[folder] ?: 0}") }, modifier = Modifier.testTag("document-folder-$folder")) }
            }
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
                } catch (_: Exception) { textError = t("Search did not work. Your reports are safe. Try again.", "खोज नहीं हो सकी। आपकी रिपोर्टें सुरक्षित हैं। फिर कोशिश करें।") }
                finally { searching = false }
            }
        }, enabled = search.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth()) { Text(if (searching) t("Searching your reports…", "रिपोर्टों में खोज रहे हैं…") else t("Search checked reports", "जाँची गई रिपोर्टों में खोजें")) } }
        if (textError.isNotBlank()) item { Text(textError, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        if (searchedText.isNotBlank()) {
            item { SectionTitle(t("From your reports", "आपकी रिपोर्टों से")) }
            if (textMatches.isEmpty()) item { SurfaceCard { Text(t("Not found in your reports", "आपकी रिपोर्टों में नहीं मिला"), fontWeight = FontWeight.SemiBold); Text(t("Try fewer words. Or open a report and check its text first.", "कम शब्दों से खोजें। या पहले रिपोर्ट खोलकर उसकी लिखाई जाँचें।"), style = MaterialTheme.typography.bodySmall) } }
            items(textMatches, key = { "${it.reportId}:${it.page}:${it.line}" }) { match ->
                reports.firstOrNull { it.id == match.reportId }?.let { report -> SurfaceCard(Sage) {
                    Text(report.name, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text(t("Page ${match.page} · line ${match.line}", "पेज ${match.page} · पंक्ति ${match.line}"), style = MaterialTheme.typography.labelMedium)
                    Text(match.quote)
                    TextButton(onClick = { reviewId = report.id }, colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(t("Open report and copy this", "रिपोर्ट खोलें और यह कॉपी करें")) }
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
                    var menuOpen by remember { mutableStateOf(false) }
                    val editable = canEdit && prescriptionOriginals.none { it.id == report.id }
                    Row(Modifier.fillMaxWidth().clickable { openReport(report, false) }.padding(start = 14.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                        Surface(shape = RoundedCornerShape(12.dp), color = if (report.mime == "application/pdf") Peach else Sage) { Icon(if (report.mime == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Image, null, Modifier.padding(8.dp).size(22.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(top = 2.dp)) {
                            Text(report.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = SaharaTitleFont, color = DoctorCardInk)
                            Text(Formatter.formatShortFileSize(context, report.bytes) + " · " + date(report.created) + " · " + DocumentFolderRules.label(folderOf(report), t) + if (folders[report.id]?.chosen == true) "" else t(" · sorted on this phone", " · फ़ोन ने छाँटा"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, modifier = Modifier.testTag("report-folder-${report.id}"))
                            cancerType(report)?.let { type -> Text(CancerDocumentRules.label(type, t) + if (cancerChoices[report.id].isNullOrBlank()) t(" · from file name", " · नाम से अंदाज़ा") else "", style = MaterialTheme.typography.labelMedium, color = RustText, modifier = Modifier.testTag("report-cancer-${report.id}")) }
                        }
                        if (canEdit) Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp).testTag("report-menu-${report.id}")) { Icon(Icons.Default.MoreVert, t("More actions", "और विकल्प"), tint = DoctorCardInk) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (editable) DropdownMenuItem(text = { Text(t("Rename", "नाम बदलें")) }, enabled = !busy, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; newName = report.name; renaming = report }, modifier = Modifier.heightIn(min = 48.dp).testTag("report-rename-${report.id}"))
                                DropdownMenuItem(text = { Text(t("Move", "फ़ोल्डर बदलें")) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) }, onClick = { menuOpen = false; moving = report }, modifier = Modifier.heightIn(min = 48.dp).testTag("report-move-${report.id}"))
                                if (editable) DropdownMenuItem(text = { Text(if (cancerType(report) == null) t("Cancer document?", "कैंसर का कागज़?") else t("Change type", "प्रकार बदलें")) }, leadingIcon = { Icon(Icons.Default.LocalHospital, null) }, onClick = { menuOpen = false; marking = report }, modifier = Modifier.heightIn(min = 48.dp).testTag("report-mark-cancer-${report.id}"))
                                if (editable) DropdownMenuItem(text = { Text(t("Remove", "हटाएँ"), color = RustText) }, enabled = !busy, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = RustText) }, onClick = { menuOpen = false; remove = report }, modifier = Modifier.heightIn(min = 48.dp).testTag("report-remove-${report.id}"))
                            }
                        } else Spacer(Modifier.width(10.dp))
                    }
                    // Main actions as small capsules; the rest live in the menu above.
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        @Composable fun Capsule(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
                            // Slim capsule so the three actions fit on one line; the touch area still meets 48dp.
                            Surface(onClick = onClick, shape = RoundedCornerShape(50), color = SaharaSoftGold, contentColor = DoctorCardInk, modifier = Modifier.minimumInteractiveComponentSize().testTag(tag)) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(15.dp), tint = DoctorAccent); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
                            }
                        }
                        Capsule(t("Open", "खोलें"), Icons.AutoMirrored.Filled.OpenInNew, "report-open-${report.id}") { openReport(report, false) }
                        Capsule(t("Read and copy facts", "पढ़ें और बातें कॉपी करें"), Icons.Default.DocumentScanner, "report-review-${report.id}") { reviewId = report.id }
                        Capsule(t("Share", "भेजें"), Icons.Default.Share, "report-share-${report.id}") { openReport(report, true) }
                    }
                }
            }
        }
        if (search.isBlank() && filter != "Reports") item { TextButton(onClick = onTalk, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (clinician) t("Start a conversation", "बातचीत शुरू करें") else t("Talk with your doctor", "अपने डॉक्टर से बात करें")) } }
    }
    remove?.let { report -> AlertDialog(onDismissRequest = { if (!busy) remove = null }, title = { Text(t("Remove this report?", "यह रिपोर्ट हटाएँ?")) }, text = { Text(report.name + t("\n\nSahara's copy on this phone is deleted. A file you picked from Files stays there; a camera scan is gone for good.", "\n\nइस फ़ोन से Sahara की कॉपी मिट जाएगी। Files से चुनी गई असली फ़ाइल वहीं रहेगी; कैमरे से किया स्कैन हमेशा के लिए हट जाएगा।")) },
        confirmButton = { TextButton(onClick = { vm.removeReport(report) { remove = null; cancerStore.remove(report.id); cancerChoices = cancerStore.all(); folderStore.remove(report.id); folders = folderStore.all() } }, enabled = !busy) { Text(t("Remove report", "रिपोर्ट हटाएँ")) } },
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
    moving?.let { report ->
        AlertDialog(onDismissRequest = { moving = null }, title = { Text(t("Move to folder", "किस फ़ोल्डर में रखें?")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(report.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t("Only the folder changes. It stays on this phone, like the report file.", "सिर्फ़ फ़ोल्डर बदलेगा। यह रिपोर्ट फ़ाइल की तरह इसी फ़ोन पर रहता है।"), style = MaterialTheme.typography.bodySmall)
            DocumentFolderRules.folders.forEach { folder ->
                FilterChip(selected = folderOf(report) == folder, onClick = { folderStore.choose(report.id, folder); folders = folderStore.all(); moving = null },
                    label = { Text(DocumentFolderRules.label(folder, t)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("folder-choice-$folder"))
            }
        } }, confirmButton = { TextButton(onClick = { moving = null }) { Text(t("Close", "बंद करें")) } })
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


/** A tappable tile on the Documents screen: icon, title and one short line. */
@Composable private fun DocsTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    ActionCard(tint, modifier, onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Paper) { Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = DoctorAccent) }
                Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = DoctorAccent)
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
            Text(subtitle, fontSize = 12.sp, color = DoctorCardSub)
        }
    }
}

/** One choice in the Add document sheet. */
@Composable private fun AddDocOption(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    ActionCard(Paper, modifier.fillMaxWidth(), onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = tint) { Icon(icon, null, Modifier.padding(12.dp).size(26.dp), tint = DoctorAccent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk); Text(subtitle, fontSize = 13.sp, color = DoctorCardSub) }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = DoctorAccent)
        }
    }
}
