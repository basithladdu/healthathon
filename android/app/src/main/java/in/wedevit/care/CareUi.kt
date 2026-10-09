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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable fun CareApp(vm: CareModel = viewModel()) {
    val people by vm.people.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val clinician by vm.clinician.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val activeRecording by vm.recording.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var personMenu by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<CareVersion?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val person = people.firstOrNull { it.id == selected }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.stopRecording() }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    BackHandler(tab != 0 || activeRecording) { if (activeRecording) vm.stopRecording() else tab = 0 }
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 66.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(R.string.app_name), fontFamily = FontFamily.Serif, fontSize = 27.sp, modifier = Modifier.weight(1f))
                AssistChip(onClick = { dialog = "role" }, label = { Text(if (clinician) "Doctor" else "Family") }, leadingIcon = { Icon(Icons.Default.PersonOutline, null, Modifier.size(17.dp)) })
            }
            if (person != null) Box {
                TextButton(onClick = { personMenu = true }, enabled = !activeRecording, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.AccountCircle, null, tint = Rust, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(person.name, color = Ink, maxLines = 1); Icon(Icons.Default.ExpandMore, "Choose person")
                }
                DropdownMenu(expanded = personMenu, onDismissRequest = { personMenu = false }) {
                    people.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.select(p.id); reading = null; personMenu = false }) }
                    DropdownMenuItem(text = { Text("Add someone") }, onClick = { personMenu = false; dialog = "person" })
                }
            }
        }
    }, bottomBar = {
        if (person != null) NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
            val labels = listOf("Today", "Talk", "Care", "Nearby", "More")
            val icons = listOf(Icons.Default.WbSunny, Icons.Default.Forum, Icons.Default.FavoriteBorder, Icons.Default.Place, Icons.Default.MoreHoriz)
            labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { if (activeRecording && index != 1) vm.stopRecording(); tab = index },
                icon = { Icon(icons[index], null) }, label = { Text(label, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Gold)) }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (person == null) Welcome { dialog = "person" }
            else key(person.id) { when (tab) {
                0 -> Today(vm, person, clinician, onTalk = { tab = 1 }, onCare = { tab = 2 }, onNearby = { tab = 3 }, onAdd = { dialog = it }, onRead = { reading = it })
                1 -> Talk(vm, clinician, onRead = { reading = it })
                2 -> CareList(vm, onAdd = { dialog = it })
                3 -> Nearby(vm.centres)
                else -> More(vm, person, onDelete = { dialog = "delete" })
            } }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Rust)
        }
    }
    if (dialog == "person") AddPerson(onDismiss = { dialog = "" }, onSave = { vm.addPerson(it) { dialog = "" } })
    if (dialog == "role") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("How are you using the app?") }, text = {
        Column { Text("Choose the view you need. Names and roles are self-entered; this does not verify a clinician.")
            Spacer(Modifier.height(16.dp)); listOf(false to "Patient or family", true to "Doctor").forEach { (role, name) ->
                OutlinedButton(onClick = { vm.role(role); dialog = "" }, modifier = Modifier.fillMaxWidth()) { Text(name) }
            }
        }
    }, confirmButton = {})
    if (dialog in listOf("medicine", "visit", "task", "checkin", "journal")) AddCareItem(dialog, vm, onDismiss = { dialog = "" })
    if (dialog == "delete") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Remove ${person?.name}'s records?") },
        text = { Text("This removes their notes, recordings and care entries from this phone. Export anything you want to keep first.") },
        confirmButton = { TextButton(onClick = { vm.removePerson { dialog = "" } }, enabled = !busy) { Text("Remove records") } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Keep records") } })
    reading?.let { note -> NoteReader(vm, note, onDismiss = { reading = null }) }
}

@Composable fun Welcome(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.conversation), "A patient and doctor talking", Modifier.fillMaxWidth().heightIn(max = 270.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(28.dp)); Heading("Care starts with\na conversation.", 36)
        Spacer(Modifier.height(12.dp)); Text("Keep what matters, the next visit and everyday care together.", fontSize = 18.sp, lineHeight = 26.sp)
        Spacer(Modifier.height(26.dp)); Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Let's begin", fontSize = 17.sp); Spacer(Modifier.width(12.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
    }
}

@Composable fun Today(vm: CareModel, person: Person, clinician: Boolean, onTalk: () -> Unit, onCare: () -> Unit, onNearby: () -> Unit, onAdd: (String) -> Unit, onRead: (CareVersion) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val today = LocalDate.now().toString()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Column { Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Rust); Spacer(Modifier.height(7.dp)); Heading(if (clinician) "Make room for\nwhat matters." else "A little easier,\none day at a time.", 32) } }
        item { Card(colors = CardDefaults.cardColors(containerColor = Gold), shape = RoundedCornerShape(24.dp), onClick = onTalk) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("GOALS OF CARE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(10.dp)); Heading(if (clinician) "Let's talk." else "Your voice\ncomes first.", 28); Spacer(Modifier.height(14.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text(if (clinician) "Start talking" else "Open care notes", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp)) } }
                Spacer(Modifier.width(12.dp))
                Image(painterResource(R.drawable.conversation), null, Modifier.width(112.dp).height(132.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            }
        } }
        versions.firstOrNull()?.let { note -> item { SurfaceCard { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Latest care note", fontWeight = FontWeight.Bold); Text("Version ${note.version} · ${note.doctor}", style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { onRead(note) }) { Text("Read") } } } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("How are you?", "Save today's check-in", Icons.Default.WbSunny, Peach, Modifier.weight(1f)) { onAdd("checkin") }
            Tile("Help nearby", "Karnataka care centres", Icons.Default.Place, Sage, Modifier.weight(1f), onNearby)
        } }
        item { SectionTitle("Up next", "Add visit") { onAdd("visit") } }
        val visits = items.filter { it.kind == "visit" && (it.due == 0L || it.due >= System.currentTimeMillis() - 86400000) }.sortedBy { it.due }
        if (visits.isEmpty()) item { SurfaceCard { Icon(Icons.Default.Event, null, tint = Rust); Spacer(Modifier.height(8.dp)); Text("No visit added yet", fontWeight = FontWeight.SemiBold); Text("Keep the date and questions in one place.", style = MaterialTheme.typography.bodyMedium) } }
        items(visits.take(2), key = { it.id }) { CareRow(it, vm) }
        item { SectionTitle("Everyday care", "See all", onCare) }
        val medicines = items.filter { it.kind == "medicine" }
        if (medicines.isEmpty()) item { OutlinedButton(onClick = { onAdd("medicine") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Add a prescribed medicine") } }
        items(medicines.take(3), key = { it.id }) { item -> SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.doneOn == today, onCheckedChange = { vm.toggle(item) }); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text(item.detail, style = MaterialTheme.typography.bodySmall) }
        } } }
        item { Text("Your doctor guides treatment. This app keeps the conversation and follow-up together.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun Talk(vm: CareModel, clinician: Boolean, onRead: (CareVersion) -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle(); val seconds by vm.recordingSeconds.collectAsStateWithLifecycle(); val level by vm.amplitude.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }; var consent by rememberSaveable { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf(false) }; var doctor by rememberSaveable { mutableStateOf("") }; var reviewed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startRecording(consent) else vm.message.value = "Microphone permission wasn't granted. You can still type the conversation." }
    LaunchedEffect(draft.source, draft.priorities, draft.participants, draft.topics, draft.questions, draft.nextSteps) { reviewed = false }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("conversation-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("What matters to you?") }
        if (clinician) {
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Conversation", "Review", "Save").forEachIndexed { i, name -> FilterChip(selected = step == i, onClick = { step = i }, label = { Text("${i + 1} $name", fontSize = 12.sp) }) } } }
            when (step) {
                0 -> {
                    item { SurfaceCard(Peach) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(10.dp)); Text(if (recording) "Recording · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "Be present. Keep the words.", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(10.dp))
                        if (recording) LinearProgressIndicator(progress = { level.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Rust)
                        else Text("Ask everyone before recording. Audio stays on this phone.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp)); Button(onClick = { if (recording) vm.stopRecording() else permissionDialog = true }, enabled = draft.personId.isNotBlank()) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (recording) "Stop and save" else "Record conversation") }
                    } }
                    items(recordings, key = { it.absolutePath }) { file -> AudioPlayback(file.absolutePath, onDelete = { vm.deleteAudio(file.absolutePath) }) }
                    item { OutlinedTextField(value = draft.source, onValueChange = { if (it.length <= 12000) vm.edit(draft.copy(source = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Conversation") }, placeholder = { Text("Write or paste what was said…") }, minLines = 7, supportingText = { Text("Keep the patient's own words. Recording does not transcribe automatically.") }) }
                    item { Button(onClick = { vm.organise(); step = 1 }, enabled = draft.source.isNotBlank() && !recording, modifier = Modifier.fillMaxWidth()) { Text("Organise and review"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                }
                1 -> {
                    item { Text("Check each section against the conversation. Leave anything not discussed blank.") }
                    items(NoteRules.labels.withIndex().toList(), key = { it.index }) { (index, label) ->
                        OutlinedTextField(value = NoteRules.values(draft)[index], onValueChange = { value -> if (value.length <= 4000) vm.edit(when (index) {
                            0 -> draft.copy(priorities = value); 1 -> draft.copy(participants = value); 2 -> draft.copy(topics = value); 3 -> draft.copy(questions = value); else -> draft.copy(nextSteps = value)
                        }) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    }
                    item { SurfaceCard(Sage) { Text("Original words", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); SelectionContainerText(draft.source.ifBlank { "Add the conversation first." }) } }
                    item { Button(onClick = { step = 2 }, enabled = draft.source.isNotBlank() && NoteRules.values(draft).any { it.isNotBlank() }, modifier = Modifier.fillMaxWidth()) { Text("Continue to save") } }
                }
                else -> {
                    item { SurfaceCard(Gold) { Heading("Ready to keep.", 26); Text("Save a new version. Earlier care notes stay unchanged.") } }
                    item { OutlinedTextField(value = doctor, onValueChange = { doctor = it.take(100) }, label = { Text("Doctor's name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { CheckLine(reviewed, { reviewed = it }, "I checked every section against the conversation. This records what was discussed, without adding treatment advice.") }
                    item { Text("A typed name records who reviewed this. It is not identity verification or a legal signature.", style = MaterialTheme.typography.bodySmall) }
                    item { Button(onClick = { vm.publish(doctor, reviewed) { step = 0; reviewed = false } }, enabled = reviewed && doctor.isNotBlank() && !busy && draft.source.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save care note") } }
                }
            }
        } else {
            item { SurfaceCard(Gold) { Heading("Your wishes.\nYour words.", 29); Spacer(Modifier.height(10.dp)); Text("Read the care note together. Tell your doctor if anything needs changing.") } }
            item { OutlinedButton(onClick = { vm.saveItem("task", "Ask for a goals-of-care conversation", "Discuss what matters and questions for the care team.") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a reminder to talk") } }
        }
        item { SectionTitle("Care notes") }
        if (versions.isEmpty()) item { Text("Your first care note will appear here after a doctor reviews and saves it.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(versions, key = { it.id }) { note -> Card(onClick = { onRead(note) }, colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Version ${note.version}" + if (note.id == versions.first().id) " · Latest" else " · Earlier", fontWeight = FontWeight.Bold); Text("${date(note.published)} · ${note.doctor}", style = MaterialTheme.typography.bodySmall) }; Icon(Icons.AutoMirrored.Filled.ArrowForward, "Read note") }
        } }
    }
    if (permissionDialog) AlertDialog(onDismissRequest = { permissionDialog = false }, title = { Text("Before we record") }, text = { CheckLine(consent, { consent = it }, "Everyone here has agreed to this recording. I understand it is saved on this phone and can be deleted.") }, confirmButton = {
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
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this recording?") }, text = { Text("The audio will be removed from this phone. Your written care notes stay available.") },
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
                item { OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Heading("The everyday things.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Medicines", "As prescribed", Icons.Default.Medication, Peach, Modifier.weight(1f)) { onAdd("medicine") }
                Tile("Visits", "Dates & questions", Icons.Default.Event, Lilac, Modifier.weight(1f)) { onAdd("visit") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("To do", "Share the load", Icons.Default.Checklist, Sage, Modifier.weight(1f)) { onAdd("task") }
                Tile("My journal", "A moment for you", Icons.Default.EditNote, Gold, Modifier.weight(1f)) { onAdd("journal") }
            }
        }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Medicines", "Visits", "To do", "Check-ins", "Journal").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) } } }
        val keys = mapOf("Medicines" to "medicine", "Visits" to "visit", "To do" to "task", "Check-ins" to "checkin", "Journal" to "journal")
        val filtered = entries.filter { filter == "All" || it.kind == keys[filter] }
        if (filtered.isEmpty()) item { SurfaceCard { Text("Nothing here yet", fontWeight = FontWeight.SemiBold); Text("Add what you need, when you need it.") } }
        items(filtered, key = { it.id }) { CareRow(it, vm) }
    }
}

@Composable fun CareRow(item: CareItem, vm: CareModel) {
    var delete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    SurfaceCard { Row(verticalAlignment = Alignment.Top) {
        if (item.kind in listOf("medicine", "task")) Checkbox(checked = if (item.kind == "medicine") item.doneOn == LocalDate.now().toString() else item.doneOn.isNotBlank(), onCheckedChange = { vm.toggle(item) })
        else Icon(if (item.kind == "visit") Icons.Default.Event else Icons.Default.EditNote, null, tint = Rust, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
        Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); if (item.due > 0) Text(date(item.due), color = Rust, fontSize = 13.sp); if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodyMedium); if (item.kind in listOf("checkin", "journal")) Text(date(item.created), style = MaterialTheme.typography.bodySmall) }
        Column { IconButton(onClick = { editing = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Edit, "Edit ${item.title}", Modifier.size(18.dp)) }; IconButton(onClick = { delete = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Remove ${item.title}", Modifier.size(18.dp)) } }
    } }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Remove this entry?") }, text = { Text(item.title) }, confirmButton = { TextButton(onClick = { vm.remove(item); delete = false }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep") } })
    if (editing) AddCareItem(item.kind, vm, onDismiss = { editing = false }, initial = item)
}

@Composable fun Nearby(centres: List<Centre>) {
    var search by rememberSaveable { mutableStateOf("") }; var service by rememberSaveable { mutableStateOf("All") }; var morphine by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val visible = centres.filter { c -> "${c.name} ${c.city} ${c.district} ${c.address}".contains(search.trim(), true) && (service == "All" || c.services.contains(service)) && (!morphine || c.morphine == "available") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Heading("Care, closer to home."); Text("Karnataka · ${visible.size} centres", color = Rust) }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Search a place or hospital") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Home care", "Clinic visits", "Inpatient care").forEach { s -> FilterChip(selected = service == s, onClick = { service = s }, label = { Text(s) }) } } }
        item { FilterChip(selected = morphine, onClick = { morphine = !morphine }, label = { Text("Morphine listed") }, leadingIcon = { Icon(Icons.Default.Medication, null, Modifier.size(18.dp)) }); Text("Pallium India list · checked 2 Oct 2026. Call to confirm services and medicine availability.", style = MaterialTheme.typography.bodySmall) }
        if (visible.isEmpty()) item { SurfaceCard { Text("No centres match that search."); TextButton(onClick = { search = ""; service = "All"; morphine = false }) { Text("Clear filters") } } }
        items(visible, key = { it.name }) { c -> SurfaceCard {
            Text(c.city.uppercase(), color = Rust, fontSize = 11.sp, letterSpacing = 1.sp); Spacer(Modifier.height(6.dp)); Text(c.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp)); Text(c.services, style = MaterialTheme.typography.bodySmall); Text(c.address, style = MaterialTheme.typography.bodyMedium)
            if (c.morphine == "available") Text("Morphine listed · stock not confirmed", fontSize = 12.sp, color = Color(0xFF465E45), modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone.replace(Regex("[^+0-9]"), "")))) }) { Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Call") }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(c.name + " " + c.address)))) }) { Text("Directions") }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) }) { Text("Source") }
            }
        } }
    }
}

@Composable fun More(vm: CareModel, person: Person, onDelete: () -> Unit) {
    val context = LocalContext.current; val versions by vm.versions.collectAsStateWithLifecycle(); val acks by vm.acknowledgements.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Heading("Your care. Your choice.") }
        item { SurfaceCard(Sage) { Heading("On this phone.", 25); Spacer(Modifier.height(8.dp)); Text("Care notes, recordings and daily entries are stored in this app on this phone. They are not synced with another phone. Android backup is off."); Spacer(Modifier.height(8.dp)); Text("Shared exports leave the app. Check the recipient before sharing.", style = MaterialTheme.typography.bodySmall) } }
        item { SurfaceCard { Text("Conversation assistance", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Organise groups the words you enter. It does not generate medical advice. AI transcription and shared clinical accounts are not connected in this build.") } }
        item { OutlinedButton(onClick = { val text = versions.joinToString("\n\n──────────\n\n") { v -> NoteRules.text(v, acks.firstOrNull { it.versionId == v.id }) }; open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Export care notes")) }, enabled = versions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Export ${person.name}'s care notes") } }
        item { SurfaceCard { Text("About this build", fontWeight = FontWeight.Bold); Text("Native Android · ${BuildConfig.VERSION_NAME}\nWorking name: Saanthvana. Final branding is still being decided."); Spacer(Modifier.height(8.dp)); Text("This app keeps records and supports conversations. It does not diagnose, recommend treatment, verify clinician identity or replace medical care.", style = MaterialTheme.typography.bodySmall) } }
        item { TextButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Remove this person's records") } }
    }
}

@Composable fun AddPerson(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Who are we caring for?") }, text = { OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Continue") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable fun AddCareItem(kind: String, vm: CareModel, onDismiss: () -> Unit, initial: CareItem? = null) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }; var detail by rememberSaveable { mutableStateOf(initial?.detail ?: "") }; var due by rememberSaveable { mutableLongStateOf(initial?.due ?: 0) }
    val busy by vm.busy.collectAsStateWithLifecycle(); val context = LocalContext.current
    val headings = mapOf("medicine" to "Add a medicine", "visit" to "Plan a visit", "task" to "One less thing to remember", "checkin" to "How is today?", "journal" to "A moment for you")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 22.dp, end = 22.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(headings[kind] ?: "Add an entry", 28)
            if (kind == "checkin") {
                Text("Choose the words that fit.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("A good day", "An okay day", "A hard day").forEach { mood -> FilterChip(selected = title == mood, onClick = { title = mood }, label = { Text(mood) }) } }
            } else OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, label = { Text(when (kind) { "medicine" -> "Medicine name"; "visit" -> "Doctor or hospital"; "task" -> "What needs doing?"; else -> "A title for today" }) }, modifier = Modifier.fillMaxWidth(), singleLine = kind != "task")
            OutlinedTextField(value = detail, onValueChange = { detail = it.take(4000) }, label = { Text(when (kind) { "medicine" -> "Dose and times, as prescribed"; "visit" -> "Time, place and questions"; "task" -> "Who can help? Anything to add?"; else -> "What would you like to remember?" }) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            if (kind == "visit") OutlinedButton(onClick = { val now = LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> due = LocalDate.of(y, m + 1, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }, now.year, now.monthValue - 1, now.dayOfMonth).show() }) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(if (due == 0L) "Choose date" else date(due)) }
            if (kind == "medicine") Text("Copy the prescription. This app does not suggest doses or send medicine reminders yet.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { if (initial == null) vm.saveItem(kind, title, detail, due, onDismiss) else vm.updateItem(initial.copy(title = title, detail = detail, due = due), onDismiss) }, enabled = title.isNotBlank() && !busy && (kind != "visit" || due > 0) && (kind != "medicine" || detail.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "Saving…" else "Save") }
        }
    }
}

@Composable fun SurfaceCard(color: Color = Paper, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(20.dp), color = color, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), content = content) } }
@Composable fun Tile(title: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) { Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color)) { Column(Modifier.padding(16.dp).heightIn(min = 105.dp)) { Icon(icon, null, Modifier.size(26.dp)); Spacer(Modifier.height(14.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp) } } }
@Composable fun Heading(text: String, size: Int = 30) { Text(text, fontFamily = FontFamily.Serif, fontSize = size.sp, lineHeight = (size + 4).sp, letterSpacing = (-0.7).sp) }
@Composable fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, fontSize = 20.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f)); if (action != null) TextButton(onClick = onAction) { Text(action) } } }
@Composable fun CheckLine(checked: Boolean, change: (Boolean) -> Unit, text: String) { Row(Modifier.fillMaxWidth().clickable { change(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) { Checkbox(checked = checked, onCheckedChange = change); Text(text, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium) } }
@Composable fun SelectionContainerText(text: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text) } }
fun date(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
fun open(context: android.content.Context, intent: Intent) { try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "No app available to open this.", android.widget.Toast.LENGTH_LONG).show() } }
