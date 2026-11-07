package `in`.wedevit.care

import android.app.Application
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

/** Two ready-made users. Choosing one enters Practice as that user. */
@Composable fun PatientWalkthroughChooser(onPick: (String) -> Unit, onDismiss: () -> Unit) { val t = careText();
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("patient-walkthrough-chooser"), shape = RoundedCornerShape(SaharaRadius.Hero), containerColor = SaharaColors.Surface,
        icon = { SaharaIconChip(Icons.Default.School, SaharaTone.Marigold, size = 48) },
        title = { Text(t("Demo account as", "डेमो खाता, किसके रूप में"), Modifier.a11yHeading(), style = MaterialTheme.typography.titleLarge, color = SaharaColors.Ink, textAlign = TextAlign.Center) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PatientWalkthrough.users.forEach { user ->
                val patient = user.role == "Patient"
                val tone = if (patient) SaharaTone.Teal else SaharaTone.Leaf
                val interaction = remember(user.key) { MutableInteractionSource() }
                Surface(onClick = { onPick(user.key) }, shape = RoundedCornerShape(SaharaRadius.Lg), color = tone.wash, contentColor = SaharaColors.Ink, border = BorderStroke(1.5.dp, tone.solid), interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).saharaPress(interaction).testTag("patient-walkthrough-${user.key}")) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SaharaIconChip(if (patient) Icons.Default.PersonOutline else Icons.Default.PeopleOutline, tone, size = 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tone.ink)
                            Text(if (patient) t("Patient · sees his own care record", "मरीज़ · अपना देखभाल रिकॉर्ड देखते हैं") else t("Family · Rahul's brother", "परिवार · राहुल के भाई"), style = MaterialTheme.typography.bodyMedium, color = SaharaColors.InkSub)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = tone.ink)
                    }
                }
            }
        } },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), fontWeight = FontWeight.SemiBold) } })
}

/** The Practice entry on the welcome panels: a marigold row that reads as a button, with one line saying what it opens. */
@Composable internal fun SaharaPracticeEntry(title: String, detail: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val tone = SaharaTone.Marigold
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(SaharaRadius.Lg), color = tone.wash, contentColor = SaharaColors.Ink, border = BorderStroke(1.5.dp, tone.solid), interactionSource = interaction,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).saharaPress(interaction)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SaharaIconChip(Icons.Default.School, tone, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tone.ink)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = SaharaColors.InkSub)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = tone.ink)
        }
    }
}

/** Hosts the normal patient app on a local model fixed to one walkthrough user. Leaving restores the real app. */
@Composable fun PatientWalkthroughHost(userKey: String, onExit: () -> Unit, onSwitch: (String) -> Unit = {}) { val t = careText();
    val user = PatientWalkthrough.user(userKey)
    if (user == null) { LaunchedEffect(Unit) { onExit() }; return }
    val context = LocalContext.current.applicationContext
    val vm = remember(userKey) { CareModel.createPatientWalkthrough(context as Application, user.role) }
    val scope = rememberCoroutineScope()
    var ready by remember(userKey) { mutableStateOf(false) }; var error by remember(userKey) { mutableStateOf<String?>(null) }
    var closing by remember(userKey) { mutableStateOf(false) }; var attempt by remember { mutableIntStateOf(0) }
    // Account actions are not part of Practice; a short dialog says so instead of a toast.
    var accountUnavailable by remember(userKey) { mutableStateOf(false) }
    val people by vm.people.collectAsState(); val selected by vm.selected.collectAsState(); val draft by vm.draft.collectAsState()
    fun leave() { if (!closing) { closing = true; scope.launch { val job = vm.closeDoctorDemo(); job.join(); onExit() } } }
    fun switchTo(key: String) { if (key == userKey) return; if (!closing) { closing = true; scope.launch { val job = vm.closeDoctorDemo(); job.join(); onSwitch(key) } } }
    DisposableEffect(vm) { onDispose { vm.closeDoctorDemo() } }
    LaunchedEffect(vm, attempt) {
        try { PatientWalkthrough.seed(vm, context); vm.select(PatientWalkthrough.personId); vm.refreshCareContacts(); vm.refreshMedicineHistory(); ready = true }
        catch (e: Exception) { if (e is CancellationException) throw e; android.util.Log.e("SaharaSeed", "patient walkthrough load failed", e); error = t("Couldn't open the demo.", "डेमो नहीं खुल सका।") }
    }
    val person = PatientWalkthrough.visiblePeople(people).firstOrNull()
    val state = when {
        closing -> "closing"
        error != null -> "error"
        ready && person == null -> "removed"
        !ready || person == null || selected != person.id || draft.personId != person.id -> "opening"
        else -> "open"
    }
    // Back while the walkthrough is still opening, failed or removed leaves it; back while leaving waits.
    BackHandler(state != "open") { leave() }
    SaharaScreenHost(state, Modifier.fillMaxSize().background(Cream), label = "Patient walkthrough") { shown -> when (shown) {
        "closing" -> SaharaLoading(t("Leaving demo…", "डेमो से बाहर जा रहे हैं…"))
        "error" -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) { SaharaEmpty(error ?: "", icon = Icons.Default.ErrorOutline, actionLabel = t("Retry", "फिर कोशिश करें"), onAction = { error = null; attempt++ }, secondaryLabel = t("Leave demo", "डेमो से बाहर जाएँ"), onSecondary = ::leave) }
        "removed" -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) { SaharaEmpty(t("Demo record removed.", "डेमो रिकॉर्ड हटा दिया गया।"), secondaryLabel = t("Leave demo", "डेमो से बाहर जाएँ"), onSecondary = ::leave) }
        "opening" -> SaharaLoading(t("Opening care records…", "देखभाल रिकॉर्ड खुल रहे हैं…"))
        else -> CareApp(vm, onAccount = { accountUnavailable = true }, walkthrough = user, onLeaveWalkthrough = ::leave, onSwitchWalkthrough = ::switchTo)
    } }
    if (accountUnavailable) SaharaDialog(t("Not available in the demo", "डेमो में उपलब्ध नहीं"), onDismiss = { accountUnavailable = false },
        text = t("Leave the demo to sign in or use an account.", "साइन इन या खाते के लिए डेमो से बाहर जाएँ।"), confirmLabel = t("OK", "ठीक है"))
}
