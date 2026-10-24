package `in`.wedevit.care

import android.app.Application
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Local patient and family walkthrough over Rahul's existing test record. It shares the offline walkthrough
 * namespace with the doctor walkthrough, so notes a doctor publishes there are what Rahul and Arjun read here.
 * No account, network or cloud is involved, and the real app's saved role and database are never touched.
 */
object PatientWalkthrough {
    const val DOCTOR = "Dr Meera Nair"
    data class User(val key: String, val name: String, val role: String, val summary: String) { val label get() = "$name · $role" }
    val users = listOf(User("rahul", "Rahul", "Patient", "Sees his own care record"), User("arjun", "Arjun", "Family", "Rahul's brother · Sees what family can see"))
    fun user(key: String?) = users.firstOrNull { it.key == key }
    private val rahul get() = DoctorTestProfiles.profiles.first { it.key == "rahul" }
    /** Both walkthrough users read Rahul's record; Arjun-the-patient from the doctor walkthrough is never offered. */
    val personId get() = DoctorTestProfiles.personId(rahul)
    fun visiblePeople(all: List<Person>) = all.filter { it.id == personId }
    /** Records only the patient and family walkthrough adds on top of Rahul's doctor walkthrough record. */
    data class Extras(val items: List<CareItem>, val versions: List<CareVersion>, val contacts: List<CareContact>)
    fun extras(now: Long, zone: ZoneId = ZoneId.systemDefault()): Extras {
        val base = DoctorTestProfiles.fixture(rahul, now, zone)
        val full = DoctorTestProfiles.fixture(rahul, now, zone, walkthrough = true)
        return Extras(full.items.filter { i -> base.items.none { it.id == i.id } }, full.versions.filter { v -> base.versions.none { it.id == v.id } }, full.contacts.filter { c -> base.contacts.none { it.id == c.id } })
    }
    /** Idempotent: the doctor walkthrough seed runs first, then missing extras are added by id. */
    suspend fun seed(vm: CareModel, context: Context) {
        DoctorTestProfiles.seed(vm, context)
        val anchor = context.getSharedPreferences("doctor-test-profiles-${vm.storageNamespace}", Context.MODE_PRIVATE).getLong("rahul", 0)
        check(anchor > 0) { "Rahul's care record is not ready." }
        val extra = extras(anchor)
        vm.seedDoctorDemo { dao ->
            if (dao.people().first().none { it.id == personId }) return@seedDoctorDemo
            val haveItems = dao.items(personId).first().map { it.id }.toSet(); val existing = dao.versions(personId).first(); val haveVersions = existing.map { it.id }.toSet()
            // The doctor walkthrough may already have published more versions, so added notes take the next free number.
            var next = (existing.maxOfOrNull { it.version } ?: 0) + 1
            extra.versions.filter { it.id !in haveVersions }.sortedBy { it.version }.forEach { v -> val renumbered = v.copy(version = next++)
                // The framed hash covers the version number, so it is recomputed; the older hash scheme does not include it.
                dao.insertVersion(if (renumbered.hashScheme == DoctorConversationContext.HASH_SCHEME) renumbered.copy(contentHash = DoctorConversationContext.framedHash(renumbered)) else renumbered) }
            extra.items.filter { it.id !in haveItems }.forEach { dao.saveItem(it) }
        }
        val contacts = LocalCareContacts(context, vm.storageNamespace)
        extra.contacts.filter { c -> contacts.list(personId).none { it.id == c.id } }.forEach(contacts::save)
    }
}

/** Two ready-made users. Choosing one enters the walkthrough as that user. */
@Composable fun PatientWalkthroughChooser(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("patient-walkthrough-chooser"), title = { Text("Try the walkthrough") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PatientWalkthrough.users.forEach { user ->
                Card(Modifier.fillMaxWidth().testTag("patient-walkthrough-${user.key}").clickable { onPick(user.key) }) {
                    Column(Modifier.padding(16.dp)) { Text("${user.name} — ${user.role}", fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text(user.summary, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        } },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Hosts the normal patient app on a local model fixed to one walkthrough user. Leaving restores the real app. */
@Composable fun PatientWalkthroughHost(userKey: String, onExit: () -> Unit) {
    val user = PatientWalkthrough.user(userKey)
    if (user == null) { LaunchedEffect(Unit) { onExit() }; return }
    val context = LocalContext.current.applicationContext
    val vm = remember(userKey) { CareModel.createPatientWalkthrough(context as Application, user.role) }
    val scope = rememberCoroutineScope()
    var ready by remember(userKey) { mutableStateOf(false) }; var error by remember(userKey) { mutableStateOf<String?>(null) }
    var closing by remember(userKey) { mutableStateOf(false) }; var attempt by remember { mutableIntStateOf(0) }
    val people by vm.people.collectAsState(); val selected by vm.selected.collectAsState(); val draft by vm.draft.collectAsState()
    fun leave() { if (!closing) { closing = true; scope.launch { val job = vm.closeDoctorDemo(); job.join(); onExit() } } }
    DisposableEffect(vm) { onDispose { vm.closeDoctorDemo() } }
    LaunchedEffect(vm, attempt) {
        try { PatientWalkthrough.seed(vm, context); vm.select(PatientWalkthrough.personId); vm.refreshCareContacts(); vm.refreshMedicineHistory(); ready = true }
        catch (e: Exception) { if (e is CancellationException) throw e; android.util.Log.e("SaharaSeed", "patient walkthrough load failed", e); error = "The walkthrough could not load. Your own profiles were not changed." }
    }
    val person = PatientWalkthrough.visiblePeople(people).firstOrNull()
    BackHandler(error != null || (ready && person == null)) { leave() }
    when {
        closing -> Column(Modifier.padding(20.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Leaving the walkthrough…") }
        error != null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(error!!); Button(onClick = { error = null; attempt++ }) { Text("Retry") }; TextButton(onClick = ::leave) { Text("Leave walkthrough") } }
        ready && person == null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Rahul's walkthrough record was removed. It will not be recreated automatically."); TextButton(onClick = ::leave) { Text("Leave walkthrough") } }
        !ready || person == null || selected != person.id || draft.personId != person.id -> Column(Modifier.padding(20.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Opening care records…") }
        else -> CareApp(vm, onAccount = { vm.message.value = "Account and sharing are unavailable in the walkthrough." }, walkthrough = user, onLeaveWalkthrough = ::leave)
    }
}
