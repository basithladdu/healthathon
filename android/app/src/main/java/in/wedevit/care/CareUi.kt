@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

val Ink = Color(0xFF382E29)
val Cream = Color(0xFFFFF8EE)
val Paper = Color(0xFFFFFDF9)
val Rust = Color(0xFFB9472C)
val Gold = Color(0xFFFFCF5C)
val Sage = Color(0xFFD9EAA8)
val Lilac = Color(0xFFDDC8FF)
val Peach = Color(0xFFFFD1B3)

@Composable fun CareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Rust, onPrimary = Color.White, secondary = Color(0xFF465E45),
        background = Cream, surface = Paper, onSurface = Ink, onBackground = Ink, surfaceVariant = Color(0xFFF1E9DE),
        onSurfaceVariant = Color(0xFF65574E), outline = Color(0xFF8D7B70)), content = content)
}

@Composable fun CareApp(vm: CareModel, onAccount: () -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val clinician by vm.clinician.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val activeRecording by vm.recording.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshError by vm.refreshError.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var personMenu by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<CareVersion?>(null) }
    val screens = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val person = people.firstOrNull { it.id == selected }
    LaunchedEffect(selected) { reading = null }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.stopRecording()
            if (event == Lifecycle.Event.ON_START) vm.refresh()
        }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    BackHandler(tab != 0 || activeRecording) { if (activeRecording) vm.stopRecording() else tab = 0 }
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = person != null && !activeRecording) { personMenu = true }.padding(vertical = 8.dp, horizontal = 2.dp)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.app_name), fontFamily = FontFamily.Serif, fontSize = 23.sp)
                    if (person != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name, color = Rust, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Icon(Icons.Default.ExpandMore, "Choose person", tint = Rust, modifier = Modifier.size(20.dp))
                    }
                }
                DropdownMenu(expanded = personMenu, onDismissRequest = { personMenu = false }) {
                    people.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.select(p.id); reading = null; personMenu = false }) }
                    DropdownMenuItem(text = { Text("Add someone") }, onClick = { personMenu = false; dialog = "person" })
                }
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { dialog = "role" }, colors = ButtonDefaults.textButtonColors(contentColor = Ink)) {
                Icon(Icons.Default.PersonOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (clinician) "Doctor" else "Family")
            }
        }
    }, bottomBar = {
        if (person != null) CareNavigation(tab) { next ->
            if (activeRecording && next != 1) vm.stopRecording()
            tab = next
        }
    }) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            if (person == null) Welcome(onAccount = onAccount, onStart = { dialog = "person" })
            else key(person.id, tab) { screens.SaveableStateProvider("${person.id}:$tab") {
                ScreenEntrance { when (tab) {
                    0 -> Today(vm, clinician, onTalk = { tab = 1 }, onCare = { tab = 2 }, onNearby = { tab = 3 }, onAdd = { if (canEdit) dialog = it else vm.message.value = "Ask the person who invited you to allow changes." }, onRead = { reading = it })
                    1 -> Talk(vm, clinician && canEdit, onRead = { reading = it })
                    2 -> CareList(vm, onAdd = { if (canEdit) dialog = it else vm.message.value = "Ask the person who invited you to allow changes." })
                    3 -> Nearby(vm.centres)
                    else -> More(vm, person, onAccount = onAccount, onDelete = { dialog = "delete" })
                } }
            } }
            refreshError?.let { error -> Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Peach) { Row(verticalAlignment = Alignment.CenterVertically) { Text(error, Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.bodySmall); TextButton(onClick = vm::refresh) { Text("Retry") } } } }
            if (busy || refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Rust)
        }
    }
    if (dialog == "person") AddPerson(onDismiss = { dialog = "" }, busy = busy || refreshing, error = actionError, onSave = { vm.addPerson(it) { dialog = "" } })
    if (dialog == "role") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("How are you using the app?") }, text = {
        Column { Text("Choose your view. This doesn't verify a doctor's identity.")
            Spacer(Modifier.height(16.dp)); listOf(false to "Patient or family", true to "Doctor").forEach { (role, name) ->
                OutlinedButton(onClick = { vm.role(role); dialog = "" }, modifier = Modifier.fillMaxWidth()) { Text(name) }
            }
        }
    }, confirmButton = {})
    if (dialog in listOf("medicine", "visit", "task", "checkin", "journal")) AddCareItem(dialog, vm, onDismiss = { dialog = "" })
    if (dialog == "delete") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Remove ${person?.name}'s records?") },
        text = { Text("This removes their notes, recordings and care entries. Export anything you want to keep first.") },
        confirmButton = { TextButton(onClick = { val removedId = person?.id; vm.removePerson { (0..4).forEach { screens.removeState("$removedId:$it") }; dialog = "" } }, enabled = !busy) { Text("Remove records") } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Keep records") } })
    reading?.let { note -> NoteReader(vm, note, onDismiss = { reading = null }) }
}

@Composable fun Welcome(onAccount: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.conversation), "A patient and doctor talking", Modifier.fillMaxWidth().heightIn(max = 270.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(24.dp)); Heading("Cancer care,\ntogether.", 32)
        Spacer(Modifier.height(12.dp)); Text("Care notes, medicines and visits.", fontSize = 17.sp)
        Spacer(Modifier.height(24.dp)); Button(onClick = onStart, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Add a person", fontSize = 17.sp); Spacer(Modifier.width(12.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
        TextButton(onClick = onAccount, modifier = Modifier.fillMaxWidth()) { Text("Your account") }
    }
}

@Composable fun Today(vm: CareModel, clinician: Boolean, onTalk: () -> Unit, onCare: () -> Unit, onNearby: () -> Unit, onAdd: (String) -> Unit, onRead: (CareVersion) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val latest = versions.firstOrNull()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    LazyColumn(Modifier.fillMaxSize().testTag("today-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Heading("Today", 30); Spacer(Modifier.weight(1f)); Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            if (vm.cloud != null) IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh records") }
        } }
        item { ActionCard(Gold, onClick = { if (!clinician && latest != null) onRead(latest) else onTalk() }) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Goals of care", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp)); Heading(if (clinician) "Let's talk" else "Your care notes", 27)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (clinician) "Start talking" else "Read notes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Image(painterResource(R.drawable.conversation), null, Modifier.size(104.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
            }
        } }
        if (clinician && latest != null) item { ActionCard(Paper, onClick = { onRead(latest) }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Latest care note", fontWeight = FontWeight.Bold); Text("${date(latest.published)} · ${latest.doctor}", style = MaterialTheme.typography.bodySmall) }; Icon(Icons.AutoMirrored.Filled.ArrowForward, "Read note", Modifier.size(20.dp)) }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("How are you?", Icons.Default.WbSunny, Peach, Modifier.weight(1f)) { onAdd("checkin") }
            Tile("Find care", Icons.Default.Place, Sage, Modifier.weight(1f), onNearby)
        } }
        val checkin = items.firstOrNull { it.kind == "checkin" && it.created >= dayStart }
        if (checkin != null) item { CareRow(checkin, vm) }
        item { SectionTitle("Up next", "Add visit") { onAdd("visit") } }
        val visits = items.filter { it.kind == "visit" && (it.due == 0L || it.due >= LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }.sortedBy { it.due }
        if (visits.isEmpty()) item { SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Event, null, tint = Rust); Spacer(Modifier.width(12.dp)); Text("No upcoming visits") } } }
        items(visits.take(2), key = { it.id }) { CareRow(it, vm) }
        item { SectionTitle("Medicines", "See all", onCare) }
        val medicines = items.filter { it.kind == "medicine" }
        if (medicines.isEmpty()) item { OutlinedButton(onClick = { onAdd("medicine") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a medicine") } }
        items(medicines.take(3), key = { it.id }) { CareRow(it, vm) }
        item { SectionTitle("To do", "Add task") { onAdd("task") } }
        val tasks = items.filter { it.kind == "task" }.sortedBy { it.doneOn.isNotBlank() }
        if (tasks.isEmpty()) item { SurfaceCard { Text("Nothing to do yet", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(tasks.take(4), key = { it.id }) { CareRow(it, vm) }
        if (tasks.size > 4) item { TextButton(onClick = onCare) { Text("See all ${tasks.size} tasks") } }
        item { SectionTitle("Journal", "Write a note") { onAdd("journal") } }
        val notes = items.filter { it.kind == "journal" }
        if (notes.isEmpty()) item { SurfaceCard { Text("No notes yet", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(notes.take(2), key = { it.id }) { CareRow(it, vm) }
    }
}

@Composable fun Talk(vm: CareModel, clinician: Boolean, onRead: (CareVersion) -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle(); val seconds by vm.recordingSeconds.collectAsStateWithLifecycle(); val level by vm.amplitude.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }; var consent by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    var previousStep by rememberSaveable { mutableIntStateOf(step) }
    LaunchedEffect(step) { if (step != previousStep) { scroll.scrollToItem(0); previousStep = step } }
    var permissionDialog by remember { mutableStateOf(false) }; var doctor by rememberSaveable { mutableStateOf("") }; var reviewed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startRecording(consent) else vm.message.value = "Microphone permission wasn't granted. You can still type the conversation." }
    LaunchedEffect(draft.source, draft.priorities, draft.participants, draft.topics, draft.questions, draft.nextSteps) { reviewed = false }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("conversation-list"), state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading(if (clinician) "Conversation" else "Care notes") }
        if (clinician) {
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Conversation", "Review", "Save").forEachIndexed { i, name -> FilterChip(selected = step == i, onClick = { step = i }, label = { Text("${i + 1} $name", fontSize = 12.sp) }) } } }
            when (step) {
                0 -> {
                    item { SurfaceCard(Peach) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(10.dp)); Text(if (recording) "Recording · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "Conversation audio", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(10.dp))
                        if (recording) {
                            val meter by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "microphone level")
                            LinearProgressIndicator(progress = { meter }, modifier = Modifier.fillMaxWidth(), color = Rust)
                        } else Text("Ask everyone before recording.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp)); Button(onClick = { if (recording) vm.stopRecording() else { consent = false; permissionDialog = true } }, shape = RoundedCornerShape(14.dp), enabled = draft.personId.isNotBlank()) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (recording) "Stop and save" else "Record conversation") }
                    } }
                    items(recordings, key = { it.absolutePath }) { file -> AudioPlayback(file.absolutePath, onDelete = { vm.deleteAudio(file.absolutePath) }) }
                    item { CareField(value = draft.source, onValueChange = { if (it.length <= 12000) vm.edit(draft.copy(source = it)) }, label = "Conversation", placeholder = "Write or paste what was said…", minLines = 5, supporting = "Recordings aren't converted to text.") }
                    item { Button(onClick = { vm.organise(); step = 1 }, enabled = draft.source.isNotBlank() && !recording, modifier = Modifier.fillMaxWidth()) { Text("Organise and review"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                }
                1 -> {
                    item { Text("Check the words. Leave anything not discussed blank.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(NoteRules.labels.withIndex().toList(), key = { it.index }) { (index, label) ->
                        CareField(value = NoteRules.values(draft)[index], onValueChange = { value -> if (value.length <= 4000) vm.edit(when (index) {
                            0 -> draft.copy(priorities = value); 1 -> draft.copy(participants = value); 2 -> draft.copy(topics = value); 3 -> draft.copy(questions = value); else -> draft.copy(nextSteps = value)
                        }) }, label = label, minLines = 2)
                    }
                    item { SurfaceCard(Sage) { Text("Original words", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); SelectionContainerText(draft.source.ifBlank { "Add the conversation first." }) } }
                    item { Button(onClick = { step = 2 }, enabled = draft.source.isNotBlank() && NoteRules.values(draft).any { it.isNotBlank() }, modifier = Modifier.fillMaxWidth()) { Text("Continue to save") } }
                }
                else -> {
                    item { SurfaceCard(Gold) { Heading("Final check", 26); Spacer(Modifier.height(6.dp)); Text("Earlier versions stay available.") } }
                    item { CareField(value = doctor, onValueChange = { doctor = it.take(100) }, label = "Doctor's name", singleLine = true) }
                    item { CheckLine(reviewed, { reviewed = it }, "I checked every section against the conversation. This records what was discussed, without adding treatment advice.") }
                    item { Text("A typed name is not a verified identity or legal signature.", style = MaterialTheme.typography.bodySmall) }
                    item { Button(onClick = { vm.publish(doctor, reviewed) { step = 0; reviewed = false } }, enabled = reviewed && doctor.isNotBlank() && !busy && draft.source.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save care note") } }
                }
            }
        } else {
            item { SurfaceCard(Gold) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Forum, null, Modifier.size(28.dp)); Spacer(Modifier.width(14.dp)); Text("Read together. Ask your doctor if anything needs changing.", modifier = Modifier.weight(1f)) } } }
            item { OutlinedButton(onClick = { vm.saveItem("task", "Ask for a goals-of-care conversation", "Discuss what matters and questions for the care team.") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a reminder to talk") } }
        }
        if (clinician) item { SectionTitle("Saved notes") }
        if (versions.isEmpty()) item { Text("Your first care note will appear here after a doctor reviews and saves it.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(versions, key = { it.id }) { note -> Card(onClick = { onRead(note) }, colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Version ${note.version}" + if (note.id == versions.first().id) " · Latest" else " · Earlier", fontWeight = FontWeight.Bold); Text("${date(note.published)} · ${note.doctor}", style = MaterialTheme.typography.bodySmall) }; Icon(Icons.AutoMirrored.Filled.ArrowForward, "Read note") }
        } }
    }
    if (permissionDialog) AlertDialog(onDismissRequest = { permissionDialog = false }, title = { Text("Before we record") }, text = { CheckLine(consent, { consent = it }, "Everyone here has agreed to this recording.") }, confirmButton = {
        TextButton(onClick = { permissionDialog = false; if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording(consent) else permission.launch(Manifest.permission.RECORD_AUDIO) }, enabled = consent) { Text("Start recording") }
    }, dismissButton = { TextButton(onClick = { permissionDialog = false }) { Text("Cancel") } })
}

@Composable fun AudioPlayback(path: String, onDelete: () -> Unit) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }; var playing by remember(path) { mutableStateOf(false) }
    var confirmDelete by remember(path) { mutableStateOf(false) }
    val context = LocalContext.current
    DisposableEffect(path) { onDispose { player?.release() } }
    SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) { player?.stop(); player?.release(); player = null; playing = false }
            else try {
                val audio = MediaPlayer.create(context, Uri.fromFile(File(path))) ?: error("Recording unavailable")
                player = audio; audio.setOnCompletionListener { playing = false; it.release(); player = null }; audio.start(); playing = true
            } catch (_: Exception) { player?.release(); player = null; playing = false; android.widget.Toast.makeText(context, "Couldn't play this recording.", android.widget.Toast.LENGTH_LONG).show() }
        }) { Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, if (playing) "Stop playback" else "Play recording") }
        Text("Conversation · " + Instant.ofEpochMilli(File(path).lastModified()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm")), modifier = Modifier.weight(1f), fontSize = 14.sp)
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, "Delete recording") }
    } }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this recording?") }, text = { Text("Your written care notes stay available.") },
        confirmButton = { TextButton(onClick = { player?.release(); player = null; playing = false; confirmDelete = false; onDelete() }) { Text("Delete recording") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep recording") } })
}

@Composable fun NoteReader(vm: CareModel, note: CareVersion, onDismiss: () -> Unit) {
    val acks by vm.acknowledgements.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val ack = acks.firstOrNull { it.versionId == note.id && it.contentHash == note.contentHash }
    var name by rememberSaveable(note.id) { mutableStateOf("") }; var relationship by rememberSaveable(note.id) { mutableStateOf("Patient") }; var reviewed by rememberSaveable(note.id) { mutableStateOf(false) }
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Heading("Goals of care"); Text("${note.personName} · Version ${note.version}"); Text("Reviewed by ${note.doctor} · ${date(note.published)}", style = MaterialTheme.typography.bodySmall) }
            items(NoteRules.labels.zip(NoteRules.values(note))) { (label, value) -> SurfaceCard(if (label == "What matters") Gold else Paper) { Text(label, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); SelectionContainerText(value.ifBlank { "Not recorded" }) } }
            item { var open by remember { mutableStateOf(false) }; TextButton(onClick = { open = !open }) { Text(if (open) "Hide original conversation" else "Read original conversation") }; if (open) SelectionContainerText(note.source) }
            item { OutlinedButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, NoteRules.text(note, ack)), "Share care note")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share this version") } }
            if (ack != null) item { SurfaceCard(Sage) { Text("Read and confirmed", fontWeight = FontWeight.Bold); Text("${ack.name} · ${ack.relationship} · ${date(ack.at)}") } }
            else if (versions.firstOrNull()?.id == note.id) {
                item { HorizontalDivider(); Spacer(Modifier.height(12.dp)); Heading("Does this reflect\nyour conversation?", 26) }
                item { CareField(value = name, onValueChange = { name = it.take(100) }, label = "Your name", singleLine = true) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Patient", "Family / carer").forEach { role -> FilterChip(selected = relationship == role, onClick = { relationship = role }, label = { Text(role) }) } } }
                item { CheckLine(reviewed, { reviewed = it }, "I read this version and it reflects our conversation. I can ask my doctor to update it later.") }
                item { Button(onClick = { vm.confirm(note, name, relationship, reviewed) {} }, enabled = reviewed && name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text("Confirm this version") } }
            }
            item { Text("This records a conversation. It is not a treatment order or legal directive. Names are self-entered.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable fun CareList(vm: CareModel, onAdd: (String) -> Unit) {
    val entries by vm.items.collectAsStateWithLifecycle(); var filter by rememberSaveable { mutableStateOf("All") }
    var adding by remember { mutableStateOf(false) }
    val kinds = linkedMapOf("Medicines" to "medicine", "Visits" to "visit", "To do" to "task", "Check-ins" to "checkin", "Journal" to "journal")
    LazyColumn(Modifier.fillMaxSize().testTag("care-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Heading("Daily care", 30); Spacer(Modifier.weight(1f))
            Box {
                FilledTonalButton(onClick = { kinds[filter]?.let(onAdd) ?: run { adding = true } }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Gold, contentColor = Ink)) { Icon(Icons.Default.Add, null, Modifier.size(19.dp)); Spacer(Modifier.width(6.dp)); Text("Add") }
                DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                    kinds.forEach { (label, kind) -> DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(careIcon(kind), null) }, onClick = { adding = false; onAdd(kind) }) }
                }
            }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Medicines", "Visits", "To do", "Check-ins", "Journal").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) } } }
        val filtered = entries.filter { filter == "All" || it.kind == kinds[filter] }
        if (filtered.isEmpty()) item { SurfaceCard {
            Icon(careIcon(kinds[filter] ?: "task"), null, Modifier.size(32.dp), tint = Rust)
            Spacer(Modifier.height(12.dp)); Text(if (filter == "All") "Nothing added yet" else "No ${filter.lowercase()} yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { kinds[filter]?.let(onAdd) ?: run { adding = true } }, contentPadding = PaddingValues(top = 10.dp, bottom = 4.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add an entry") }
        } }
        items(filtered, key = { it.id }) { Box(Modifier.animateItem()) { CareRow(it, vm) } }
    }
}

@Composable fun CareRow(item: CareItem, vm: CareModel) {
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var delete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val done = if (item.kind == "medicine") item.doneOn == LocalDate.now().toString() else item.doneOn.isNotBlank()
    val color by animateColorAsState(if (done) Color(0xFFE7EED6) else Paper, tween(180), label = "care completed")
    SurfaceCard(color) { Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.kind in listOf("medicine", "task")) Checkbox(checked = done, onCheckedChange = { vm.toggle(item) }, enabled = canEdit && !busy)
        else Surface(shape = RoundedCornerShape(12.dp), color = if (item.kind == "visit") Lilac else Peach, modifier = Modifier.padding(end = 12.dp)) { Icon(careIcon(item.kind), null, tint = Ink, modifier = Modifier.padding(10.dp).size(22.dp)) }
        Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); if (item.due > 0) Text(date(item.due), color = Rust, fontSize = 13.sp); if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodyMedium); if (item.kind in listOf("checkin", "journal")) Text(date(item.created), style = MaterialTheme.typography.bodySmall) }
        Box {
            IconButton(onClick = { menu = true }, enabled = canEdit && !busy) { Icon(Icons.Default.MoreHoriz, "Options for ${item.title}", Modifier.size(22.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; editing = true })
                DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { menu = false; delete = true })
            }
        }
    } }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Remove this entry?") }, text = { Text(item.title) }, confirmButton = { TextButton(onClick = { vm.remove(item); delete = false }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep") } })
    if (editing) AddCareItem(item.kind, vm, onDismiss = { editing = false }, initial = item)
}

@Composable fun Nearby(centres: List<Centre>) {
    var search by rememberSaveable { mutableStateOf("") }; var service by rememberSaveable { mutableStateOf("All") }; var morphine by rememberSaveable { mutableStateOf(false) }
    var showMap by rememberSaveable { mutableStateOf(true) }
    var chosen by remember { mutableStateOf<Centre?>(null) }
    val visible = centres.filter { c -> "${c.name} ${c.city} ${c.district} ${c.address}".contains(search.trim(), true) && (service == "All" || c.services.contains(service)) && (!morphine || c.morphine == "available") }
    LazyColumn(Modifier.fillMaxSize().testTag("nearby-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Heading("Find care"); Text("Karnataka · ${visible.size} ${if (visible.size == 1) "centre" else "centres"}", color = Rust) }
            IconButton(onClick = { showMap = !showMap }) { Icon(if (showMap) Icons.AutoMirrored.Filled.ListAlt else Icons.Default.Map, if (showMap) "Show list only" else "Show map") }
        } }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Place or hospital") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") } }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Home care", "Clinic visits", "Inpatient care").forEach { s -> FilterChip(selected = service == s, onClick = { service = s }, label = { Text(s) }) } } }
        item { FilterChip(selected = morphine, onClick = { morphine = !morphine }, label = { Text("Morphine listed") }, leadingIcon = { Icon(Icons.Default.Medication, null, Modifier.size(18.dp)) }); Text("Pallium India · checked 2 Oct 2026. Call to confirm.", style = MaterialTheme.typography.bodySmall) }
        if (showMap && visible.isNotEmpty()) item {
            Column(Modifier.clip(RoundedCornerShape(20.dp))) { CentreMap(visible) { chosen = it } }
            val located = visible.count { it.latitude != null && it.longitude != null }
            if (located < visible.size) Text("$located mapped · ${visible.size - located} addresses in the list", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        if (visible.isEmpty()) item { SurfaceCard { Text("No centres match that search."); TextButton(onClick = { search = ""; service = "All"; morphine = false }) { Text("Clear filters") } } }
        items(visible, key = { it.id }) { c -> CentreCard(c) }
    }
    chosen?.let { centre -> ModalBottomSheet(onDismissRequest = { chosen = null }, containerColor = Cream) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) { CentreCard(centre) }
    } }
}

@Composable fun CentreCard(c: Centre) {
    val context = LocalContext.current
    SurfaceCard {
            Text(c.city.uppercase(), color = Rust, fontSize = 11.sp, letterSpacing = 1.sp); Spacer(Modifier.height(6.dp)); Text(c.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp)); Text(c.services, style = MaterialTheme.typography.bodySmall); Text(c.address, style = MaterialTheme.typography.bodyMedium)
            if (c.morphine == "available") Text("Morphine listed · stock not confirmed", fontSize = 12.sp, color = Color(0xFF465E45), modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone.replace(Regex("[^+0-9]"), "")))) }) { Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Call") }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(c.name + " " + c.address)))) }) { Text("Directions") }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) }) { Text("Source") }
            }
    }
}

@Composable fun More(vm: CareModel, person: Person, onAccount: () -> Unit, onDelete: () -> Unit) {
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current; val versions by vm.versions.collectAsStateWithLifecycle(); val acks by vm.acknowledgements.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Heading("More") }
        item { OutlinedButton(onClick = onAccount, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.PersonOutline, null); Spacer(Modifier.width(8.dp)); Text(if (vm.cloud == null) "Sign in to share care" else "Your account and sharing") } }
        item { SurfaceCard(Gold) {
            Icon(Icons.Default.Description, null, Modifier.size(28.dp)); Spacer(Modifier.height(12.dp))
            Text("${person.name}'s care notes", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp)); Text("${versions.size} saved", color = Ink)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { val text = versions.joinToString("\n\n──────────\n\n") { v -> NoteRules.text(v, acks.firstOrNull { it.versionId == v.id }) }; open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Export care notes")) }, enabled = versions.isNotEmpty(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Export notes") }
        } }
        item { DetailPanel("Privacy", Icons.Default.Lock) {
            Text("You choose which notes to share and with whom.")
            Spacer(Modifier.height(10.dp)); Text("You can delete a recording or remove a person's records here.")
        } }
        item { DetailPanel("About", Icons.Default.Info) {
            Text(androidx.compose.ui.res.stringResource(R.string.app_full_name))
            Spacer(Modifier.height(10.dp)); Text("Your doctor guides treatment. Names and roles in the app aren't verified.")
            Spacer(Modifier.height(10.dp)); Text("The app groups the words you type. Recordings aren't converted to text.")
            Spacer(Modifier.height(12.dp)); Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        } }
        if (owner) item { TextButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Remove this person's records") } }
    }
}

@Composable fun AddPerson(onDismiss: () -> Unit, busy: Boolean, error: String?, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Who is this care for?") }, text = { Column { CareField(value = name, onValueChange = { name = it.take(100) }, label = "Name", singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank() && !busy) { Text(if (busy) "Saving…" else "Continue") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable fun AddCareItem(kind: String, vm: CareModel, onDismiss: () -> Unit, initial: CareItem? = null) {
    val entryId = rememberSaveable { initial?.id ?: newId() }
    val error by vm.actionError.collectAsStateWithLifecycle()
    LaunchedEffect(entryId) { vm.actionError.value = null }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }; var detail by rememberSaveable { mutableStateOf(initial?.detail ?: "") }; var due by rememberSaveable { mutableLongStateOf(initial?.due ?: 0) }
    val busy by vm.busy.collectAsStateWithLifecycle(); val context = LocalContext.current
    var extraNote by rememberSaveable { mutableStateOf(initial?.detail?.isNotBlank() == true) }
    val headings = mapOf("medicine" to "Add a medicine", "visit" to "Add a visit", "task" to "Add a task", "checkin" to "How is today?", "journal" to "Write a note")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 22.dp, end = 22.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(if (initial != null && kind != "checkin") "Edit ${when (kind) { "medicine" -> "medicine"; "visit" -> "visit"; "task" -> "task"; else -> "note" }}" else headings[kind] ?: "Add an entry", 28)
            if (kind == "checkin") {
                val moods = listOf(Triple("A good day", Icons.Default.SentimentSatisfiedAlt, Sage), Triple("An okay day", Icons.Default.SentimentNeutral, Gold), Triple("A hard day", Icons.Default.SentimentDissatisfied, Peach))
                moods.forEach { (mood, icon, tint) -> ChoiceRow(mood, icon, tint, title == mood) { title = mood } }
                TextButton(onClick = { extraNote = !extraNote }) { Icon(if (extraNote) Icons.Default.ExpandLess else Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(if (extraNote) "Hide note" else "Add a note") }
                AnimatedVisibility(extraNote, enter = expandVertically(tween(180)) + fadeIn(tween(180)), exit = shrinkVertically(tween(150)) + fadeOut(tween(150))) {
                    CareField(value = detail, onValueChange = { detail = it.take(4000) }, label = "Anything to add?", minLines = 2)
                }
            } else if (kind == "journal" && initial == null) {
                CareField(value = detail, onValueChange = { detail = it.take(4000); title = it.lineSequence().firstOrNull { line -> line.isNotBlank() }.orEmpty().take(80) }, label = "Your note", minLines = 4)
            } else {
                CareField(value = title, onValueChange = { title = it.take(200) }, label = when (kind) { "medicine" -> "Medicine name"; "visit" -> "Doctor or hospital"; "task" -> "What needs doing?"; else -> "Title" }, singleLine = kind != "task")
                if (kind == "medicine" || kind == "journal" || extraNote) CareField(value = detail, onValueChange = { detail = it.take(4000) }, label = when (kind) { "medicine" -> "Dose and times, as prescribed"; "visit" -> "Time, place and questions"; "task" -> "Details (optional)"; else -> "Your note" }, minLines = if (kind == "journal") 4 else 2)
                if (kind in listOf("task", "visit")) TextButton(onClick = { extraNote = !extraNote }) { Text(if (extraNote) "Hide details" else "Add details") }
            }
            if (kind == "visit") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today" to 0L, "Tomorrow" to 1L).forEach { (label, days) -> val day = LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); FilterChip(selected = due == day, onClick = { due = day }, label = { Text(label) }) }
            }
            if (kind == "visit") OutlinedButton(onClick = { val chosen = if (due > 0) Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> due = LocalDate.of(y, m + 1, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show() }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(if (due == 0L) "Choose date" else date(due)) }
            if (kind == "medicine") Text("Use your prescription. Medicine alerts aren't available.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { if (initial == null) vm.saveItem(kind, title, detail, due, onDismiss, entryId) else vm.updateItem(initial.copy(title = title, detail = detail, due = due), onDismiss) }, enabled = title.isNotBlank() && !busy && (kind != "visit" || due > 0) && (kind != "medicine" || detail.isNotBlank()), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (busy) "Saving…" else "Save", fontSize = 16.sp) }
        }
    }
}

@Composable fun SurfaceCard(color: Color = Paper, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(20.dp), color = color, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), content = content) } }
@Composable fun Tile(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) { ActionCard(color, modifier, onClick) { Column(Modifier.padding(16.dp).heightIn(min = 66.dp)) { Row(Modifier.fillMaxWidth()) { Icon(icon, null, Modifier.size(25.dp)); Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(17.dp)) }; Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } } }
@Composable fun Heading(text: String, size: Int = 30) { Text(text, fontFamily = FontFamily.Serif, fontSize = size.sp, lineHeight = (size + 4).sp, letterSpacing = (-0.7).sp) }
@Composable fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, fontSize = 20.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f)); if (action != null) TextButton(onClick = onAction) { Text(action) } } }
@Composable fun CheckLine(checked: Boolean, change: (Boolean) -> Unit, text: String) { Row(Modifier.fillMaxWidth().clickable { change(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) { Checkbox(checked = checked, onCheckedChange = change); Text(text, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium) } }
@Composable fun SelectionContainerText(text: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text) } }
fun date(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
fun open(context: android.content.Context, intent: Intent) { try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "No app available to open this.", android.widget.Toast.LENGTH_LONG).show() } }
