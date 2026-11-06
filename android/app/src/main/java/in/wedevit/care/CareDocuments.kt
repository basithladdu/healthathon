@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
    // A file that will not open, or no camera app, is said in a small dialog rather than a passing toast.
    var docProblem by remember { mutableStateOf("") }
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
            .onFailure { file.delete(); importingFor = ""; scanPath = ""; docProblem = t("No camera app found.", "कैमरा ऐप नहीं मिला।") }
        Unit
    }
    val bothLanguages: (String, String) -> String = { en, hi -> "$en $hi" }
    val visibleReports = reports.filter { report -> filter != "Care notes" && (folderFilter.isEmpty() || folderOf(report) == folderFilter) && (filter != "Cancer" || cancerType(report) != null) && DocumentSearchWords.matchesName(search, "${report.name} ${cancerType(report)?.let { CancerDocumentRules.label(it, bothLanguages) }.orEmpty()}") }
    val visibleNotes = notes.filter { filter !in setOf("Reports", "Cancer") && folderFilter.isEmpty() && DocumentSearchWords.matchesName(search, "Goals of care देखभाल के लक्ष्य ${it.personName} ${it.doctor} Version ${it.version}") }

    fun openReport(report: CareReport, share: Boolean) {
        try {
            val intent = vm.reportFiles.intent(report, share)
            open(context, if (share) Intent.createChooser(intent, t("Share report", "रिपोर्ट भेजें")) else intent)
        } catch (_: Exception) { docProblem = t("Couldn't open this report.", "यह रिपोर्ट नहीं खुल सकी।") }
    }

    var addSheet by rememberSaveable { mutableStateOf(false) }
    // Two ways in: photograph a paper report, or pick a file that is already on the phone.
    if (addSheet) ModalBottomSheet(onDismissRequest = { addSheet = false }, containerColor = Cream) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Add document", "कागज़ जोड़ें"), Modifier.a11yHeading().padding(bottom = 4.dp), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            AddDocOption(t("Scan with camera", "कैमरे से स्कैन करें"), Icons.Default.PhotoCamera, Peach, Modifier.testTag("document-scan")) { addSheet = false; scanReport() }
            AddDocOption(t("Upload a file", "फ़ाइल अपलोड करें"), Icons.Default.UploadFile, SaharaSoftBlue, Modifier.testTag("document-upload")) { addSheet = false; addReport() }
        }
    }
    LazyColumn(Modifier.fillMaxSize().saharaScreenEnter().testTag("documents-list"), state = listState, contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Heading(t("Documents", "कागज़ात"), 30)
            }
            if (canEdit) SaharaPrimaryButton(t("Add document", "कागज़ जोड़ें"), onClick = { addSheet = true }, icon = Icons.Default.Add, enabled = !busy, modifier = Modifier.saharaPulseGlow(corner = SaharaRadius.Md, active = reports.isEmpty()).testTag("document-add"))
        } }
        // Timeline and Ask as two slim buttons (they open their own screens), so the list starts in the first screenful.
        item { Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaharaSecondaryButton(t("Care timeline", "देखभाल की समय-रेखा"), onClick = { timeline = true }, icon = Icons.Default.Timeline, tone = SaharaTone.Marigold, navigates = true, modifier = Modifier.weight(1f).fillMaxHeight().testTag("care-timeline-open"))
            if (reports.isNotEmpty()) SaharaSecondaryButton(t("Ask documents", "कागज़ों से पूछें"), onClick = { asking = true }, icon = Icons.Default.QuestionAnswer, tone = SaharaTone.Indigo, navigates = true, modifier = Modifier.weight(1f).fillMaxHeight().testTag("document-ask-open"))
        } }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(t("Search documents", "कागज़ात खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, t("Clear document search", "खोज मिटाएँ")) } },
            modifier = Modifier.fillMaxWidth().testTag("document-search"), shape = RoundedCornerShape(14.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Care notes", "Reports", "Cancer").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(when (label) { "Care notes" -> t("Care notes", "देखभाल नोट"); "Reports" -> t("Reports", "रिपोर्टें"); "Cancer" -> t("Cancer documents", "कैंसर के कागज़"); else -> t("All", "सभी") }) }, shape = RoundedCornerShape(50), modifier = Modifier.testTag("document-filter-$label")) }
        } }
        // Auto-sorted folders as coloured tiles with their counts; tap one to open it, tap again to see everything.
        // Only folders that hold something (and the open one) are shown, so empty tiles never push the list down.
        if (reports.isNotEmpty()) item { val counts = DocumentFolderRules.counts(reports.map(::folderOf))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentFolderRules.folders.filter { (counts[it] ?: 0) > 0 || it == folderFilter }.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { folder -> FolderTile(DocumentFolderRules.label(folder, t), folder, counts[folder] ?: 0, folderFilter == folder, Modifier.weight(1f).fillMaxHeight().saharaStagger(DocumentFolderRules.folders.indexOf(folder)).testTag("document-folder-$folder")) { folderFilter = if (folderFilter == folder) "" else folder } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                } }
            }
        }
        // Searching inside checked report text only makes sense once something is typed.
        if (search.isNotBlank()) item { OutlinedButton(onClick = {
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
                } catch (_: Exception) { textError = t("Search didn't work. Your documents are safe. Try again.", "खोज नहीं हो पाई। आपके कागज़ सुरक्षित हैं। फिर कोशिश करें।") }
                finally { searching = false }
            }
        }, enabled = !searching, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (searching) t("Searching your reports…", "रिपोर्टों में खोज रहे हैं…") else t("Search checked reports", "जाँची गई रिपोर्टों में खोजें")) } }
        if (textError.isNotBlank()) item { Text(textError, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.announce()) }
        if (searchedText.isNotBlank()) {
            item { SectionTitle(t("From your reports", "आपकी रिपोर्टों से")) }
            if (textMatches.isEmpty()) item { SurfaceCard { Text(t("Not found in your reports", "आपकी रिपोर्टों में नहीं मिला"), fontWeight = FontWeight.SemiBold, color = DoctorCardInk) } }
            items(textMatches, key = { "${it.reportId}:${it.page}:${it.line}" }) { match ->
                reports.firstOrNull { it.id == match.reportId }?.let { report -> SurfaceCard(Mint) {
                    Text(report.name, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(t("Page ${match.page} · line ${match.line}", "पेज ${match.page} · पंक्ति ${match.line}"), fontSize = 14.sp, color = DoctorCardSub)
                    Spacer(Modifier.height(4.dp)); Text(match.quote, color = DoctorCardInk)
                    TextButton(onClick = { reviewId = report.id }, colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(t("Open report and copy this", "रिपोर्ट खोलें और यह कॉपी करें")) }
                } }
            }
        }
        if (visibleNotes.isEmpty() && visibleReports.isEmpty()) item { SurfaceCard {
            SaharaLottie(SaharaLottieKind.Document, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp)); Text(if (search.isNotBlank()) t("No documents found", "कोई कागज़ नहीं मिला") else when (filter) { "Reports" -> t("No reports yet", "अभी कोई रिपोर्ट नहीं"); "Care notes" -> t("No care notes yet", "अभी कोई देखभाल नोट नहीं"); "Cancer" -> t("No cancer documents yet", "अभी कैंसर का कोई कागज़ नहीं"); else -> t("No documents yet", "अभी कोई कागज़ नहीं") }, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk)
            Spacer(Modifier.height(4.dp))
            if (search.isNotBlank()) TextButton(onClick = { search = ""; filter = "All" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Clear search", "खोज मिटाएँ")) }
            else if (filter != "Care notes" && canEdit) TextButton(onClick = { addSheet = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add document", "कागज़ जोड़ें")) }
        } }
        if (visibleNotes.isNotEmpty()) item { SectionTitle(t("Care notes", "देखभाल नोट")) }
        items(visibleNotes, key = { it.id }) { note ->
            val latest = note.id == notes.firstOrNull()?.id
            Card(onClick = { onRead(note) }, modifier = Modifier.animateItem().saharaStagger(visibleNotes.indexOf(note)).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Paper), border = BorderStroke(1.dp, DoctorHairline)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = if (latest) SaharaSoftGold else SaharaLavender) { Icon(Icons.Default.Description, null, Modifier.padding(10.dp).size(22.dp), tint = DoctorAccent) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Goals of care", "देखभाल के लक्ष्य"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, fontSize = 17.sp, color = DoctorCardInk)
                        Text(t("Version ${note.version}", "संस्करण ${note.version}") + " · ${date(note.published)} · ${note.doctor}", fontSize = 14.sp, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (latest) { Spacer(Modifier.width(8.dp)); DocsChip(t("Latest", "नया"), SaharaSoftGold) }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Read version ${note.version}", "संस्करण ${note.version} पढ़ें"), Modifier.size(20.dp), tint = DoctorAccent)
                }
            }
        }
        if (visibleReports.isNotEmpty()) item(key = "reports-title") { SaharaStateSwitch(folderFilter, Modifier.animateItem(), label = "open-folder") { open ->
            if (open.isEmpty()) SectionTitle(t("Reports", "रिपोर्टें")) else SectionTitle(DocumentFolderRules.label(open, t), t("Show all", "सभी दिखाएँ")) { folderFilter = "" }
        } }
        items(visibleReports, key = { it.id }) { report ->
            Surface(Modifier.animateItem().saharaStagger(visibleNotes.size + visibleReports.indexOf(report)).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline)) {
                Column {
                    var menuOpen by remember { mutableStateOf(false) }
                    val editable = canEdit && prescriptionOriginals.none { it.id == report.id }
                    Row(Modifier.fillMaxWidth().clickable { openReport(report, false) }.padding(start = 16.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                        val tone = documentFolderTone(folderOf(report))
                        Surface(shape = RoundedCornerShape(12.dp), color = tone.soft) { Icon(if (report.mime == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Image, null, Modifier.padding(10.dp).size(22.dp), tint = tone.accent) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(top = 2.dp)) {
                            Text(report.name, fontWeight = FontWeight.Medium, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = SaharaTitleFont, color = DoctorCardInk)
                            Text(Formatter.formatShortFileSize(context, report.bytes) + " · " + date(report.created), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub, modifier = Modifier.padding(top = 2.dp))
                            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DocsChip(DocumentFolderRules.label(folderOf(report), t), tone.soft, Modifier.testTag("report-folder-${report.id}"), color = tone.accent)
                                cancerType(report)?.let { type -> DocsChip(CancerDocumentRules.label(type, t), CancerTone.soft, Modifier.testTag("report-cancer-${report.id}"), color = CancerTone.accent) }
                            }
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
                    // Tapping the row opens the file; Review and Share sit here as outlined capsules, the rest in the menu above.
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        @Composable fun Capsule(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
                            // Slim capsule so the three actions fit on one line; the touch area still meets 48dp.
                            Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Paper, contentColor = DoctorCardInk, border = BorderStroke(1.5.dp, DoctorAccent), modifier = Modifier.minimumInteractiveComponentSize().testTag(tag)) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(16.dp), tint = DoctorAccent); Spacer(Modifier.width(6.dp)); Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
                            }
                        }
                        Capsule(t("Review", "जाँचें"), Icons.Default.DocumentScanner, "report-review-${report.id}") { reviewId = report.id }
                        Capsule(t("Share", "भेजें"), Icons.Default.Share, "report-share-${report.id}") { openReport(report, true) }
                    }
                }
            }
        }
        if (search.isBlank() && filter != "Reports") item { TextButton(onClick = onTalk, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (clinician) t("Start a conversation", "बातचीत शुरू करें") else t("Talk with your doctor", "अपने डॉक्टर से बात करें")) } }
    }
    if (docProblem.isNotBlank()) SaharaDialog(docProblem, onDismiss = { docProblem = "" }, tone = SaharaTone.Error)
    remove?.let { report -> AlertDialog(onDismissRequest = { if (!busy) remove = null }, title = { Text(t("Remove this report?", "यह रिपोर्ट हटाएँ?")) }, text = { Text(report.name) },
        confirmButton = { TextButton(onClick = { vm.removeReport(report) { remove = null; cancerStore.remove(report.id); cancerChoices = cancerStore.all(); folderStore.remove(report.id); folders = folderStore.all() } }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(t("Remove", "हटाएँ"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { remove = null }, enabled = !busy) { Text(t("Keep", "रखें")) } }) }
    marking?.let { report ->
        val current = cancerType(report)
        AlertDialog(onDismissRequest = { marking = null }, title = { Text(t("What kind of document?", "यह कौन सा कागज़ है?")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(report.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            DocumentFolderRules.folders.forEach { folder ->
                FilterChip(selected = folderOf(report) == folder, onClick = { folderStore.choose(report.id, folder); folders = folderStore.all(); moving = null },
                    label = { Text(DocumentFolderRules.label(folder, t)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("folder-choice-$folder"))
            }
        } }, confirmButton = { TextButton(onClick = { moving = null }) { Text(t("Close", "बंद करें")) } })
    }
    renaming?.let { report ->
        val nameError = ReportNameRules.error(newName)
        AlertDialog(onDismissRequest = { if (!busy) renaming = null }, title = { Text(t("Rename report", "रिपोर्ट का नाम बदलें")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(newName, { newName = it.take(160) }, label = { Text(t("Report name", "रिपोर्ट का नाम")) }, singleLine = true, shape = RoundedCornerShape(14.dp), isError = nameError != null, supportingText = nameError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("report-rename-field"))
        } }, confirmButton = { TextButton(onClick = { vm.renameReport(report, newName) { renaming = null } }, enabled = !busy && nameError == null && newName.trim() != report.name, modifier = Modifier.testTag("report-rename-save")) { Text(t("Save", "सेव करें")) } },
            dismissButton = { TextButton(onClick = { renaming = null }, enabled = !busy) { Text(t("Cancel", "रद्द करें")) } })
    }
}


/** One choice in the Add document sheet. */
@Composable private fun AddDocOption(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    ActionCard(Paper, modifier.fillMaxWidth().heightIn(min = 72.dp), onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = tint) { Icon(icon, null, Modifier.padding(12.dp).size(26.dp), tint = DoctorAccent) }
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = DoctorAccent)
        }
    }
}

/** Each auto-sorted folder keeps one colour everywhere: its tile, its file icons, its pills and the Ask badge. */
internal data class FolderTone(val accent: androidx.compose.ui.graphics.Color, val soft: androidx.compose.ui.graphics.Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)
internal fun documentFolderTone(folder: String?): FolderTone = when (folder) {
    // The design lane's section tones (SaharaTone): solid for the icon and an open tile, wash for chips, ink for text on the wash.
    DocumentFolderRules.BILLS -> FolderTone(SaharaTone.Marigold.ink, SaharaTone.Marigold.wash, Icons.AutoMirrored.Filled.ReceiptLong)
    DocumentFolderRules.LAB -> FolderTone(SaharaTone.Teal.ink, SaharaTone.Teal.wash, Icons.Default.Science)
    DocumentFolderRules.PRESCRIPTIONS -> FolderTone(SaharaTone.Plum.ink, SaharaTone.Plum.wash, Icons.Default.Medication)
    DocumentFolderRules.DISCHARGE -> FolderTone(SaharaTone.Indigo.ink, SaharaTone.Indigo.wash, Icons.Default.LocalHospital)
    else -> FolderTone(SaharaColors.InkSub, SaharaColors.Well, Icons.Default.Folder)
}
internal val CancerTone = FolderTone(SaharaTone.Rose.ink, SaharaTone.Rose.wash, Icons.Default.LocalHospital)

/** One folder tile: coloured icon chip, name and count. The open folder fills with its colour. */
@Composable private fun FolderTile(title: String, folder: String, count: Int, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tone = documentFolderTone(folder)
    val fill by androidx.compose.animation.animateColorAsState(if (open) tone.accent else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "folder-tile")
    val ink by androidx.compose.animation.animateColorAsState(if (open) Paper else DoctorCardInk, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "folder-ink")
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = fill, border = BorderStroke(1.5.dp, tone.accent), interactionSource = interaction,
        modifier = modifier.saharaPress(interaction).heightIn(min = 56.dp).semantics { selected = open; contentDescription = "$title, $count" }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = if (open) Paper else tone.soft) { Icon(if (open) Icons.Default.FolderOpen else tone.icon, null, Modifier.padding(7.dp).size(20.dp), tint = tone.accent) }
            Spacer(Modifier.width(8.dp))
            Text(title, Modifier.weight(1f), color = ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, style = LocalTextStyle.current.copy(hyphens = androidx.compose.ui.text.style.Hyphens.None, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading))
            SaharaAnimatedText("${saharaCountUp(count)}") { Text(it, color = if (open) Paper else tone.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** A small status pill: a label on a soft tint, fully rounded. */
@Composable private fun DocsChip(text: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = DoctorCardInk) {
    Surface(shape = RoundedCornerShape(50), color = tint, contentColor = color, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
