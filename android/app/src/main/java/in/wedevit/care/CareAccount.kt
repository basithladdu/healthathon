@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CareRootModel(app: Application) : AndroidViewModel(app) {
    val cloud = CareCloud(app)
    private var account: String? = null
    private var careStore = ViewModelStore()
    fun storeFor(id: String): ViewModelStore {
        if (account != id) { careStore.clear(); careStore = ViewModelStore(); account = id }
        return careStore
    }
    override fun onCleared() { careStore.clear(); super.onCleared() }
    private val pendingStore = CarePendingJoinStore(CarePrefsKeyValue(app.getSharedPreferences("care_pending_join", android.content.Context.MODE_PRIVATE)))
    val pendingJoin = kotlinx.coroutines.flow.MutableStateFlow(pendingStore.load(System.currentTimeMillis()))
    val linkNotice = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** Called by MainActivity for https://sahara.wedevit.in/join and sahara://join links. */
    fun offerInviteLink(link: String) {
        val invite = CareInviteLink.parse(link)
        if (invite == null) { linkNotice.value = "That invitation link isn't complete. Ask for a new one, or paste the code under Join someone's care."; return }
        PendingJoin(invite.code, invite.name, System.currentTimeMillis()).also { pendingStore.save(it); pendingJoin.value = it }
    }
    fun clearPendingJoin() { pendingStore.clear(); pendingJoin.value = null }
}

@Composable fun CareRoot(activity: MainActivity) {
    val root = remember { ViewModelProvider(activity)[CareRootModel::class.java] }
    val cloud = root.cloud
    var demoOpen by rememberSaveable { mutableStateOf(false) }
    var walkthroughPatient by rememberSaveable { mutableStateOf<String?>(null) }
    var patientWalkthroughUser by rememberSaveable { mutableStateOf<String?>(null) }
    var doctorOpen by rememberSaveable { mutableStateOf(false) }
    var doctorEntryPending by rememberSaveable { mutableStateOf(false) }
    val pending by root.pendingJoin.collectAsStateWithLifecycle()
    val linkNotice by root.linkNotice.collectAsStateWithLifecycle()
    val session by cloud.session.collectAsStateWithLifecycle()
    LaunchedEffect(session?.userId) {
        if (session != null && doctorEntryPending) { doctorOpen = true; doctorEntryPending = false }
        if (session == null) doctorOpen = false
    }
    key(session?.userId.orEmpty()) {
        val store = remember { root.storeFor(session?.userId.orEmpty()) }
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = store } }
        val model = remember { ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = CareModel(activity.application, cloud.takeIf { session != null }) as T
        })[CareModel::class.java] }
        SideEffect { activity.careModel = model }
        var accountOpen by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(pending?.code, session?.userId) { if (pending != null && session == null) accountOpen = true }
        LaunchedEffect(linkNotice) { linkNotice?.let { model.message.value = it; root.linkNotice.value = null } }
        val rootLanguage = rememberCareLanguage(model.accountNamespace)
        val rt = { en: String, hi: String -> if (rootLanguage.hindi) hi else en }
        val joinBanner = pending?.let { rt("Sign in or create an account to join ${it.possessive} care", "${it.name.ifBlank { "इस व्यक्ति" }} की देखभाल से जुड़ने के लिए साइन इन करें या खाता बनाएँ") }
        val mode = when { patientWalkthroughUser != null -> "patient-walkthrough"; demoOpen -> "doctor-walkthrough"; doctorOpen && session != null -> "doctor"; else -> "care" }
        // Entering a walkthrough or workspace moves forward; leaving it moves back to the welcome/care app.
        SaharaScreenHost(mode, Modifier.fillMaxSize().background(Cream), label = "Sahara root", direction = { _, to -> if (to == "care") SaharaMotion.Direction.Back else SaharaMotion.Direction.Forward }) { shown -> when (shown) {
        "patient-walkthrough" -> patientWalkthroughUser?.let { PatientWalkthroughHost(it, onExit = { patientWalkthroughUser = null }, onSwitch = { patientWalkthroughUser = it }) }
        "doctor-walkthrough" -> DoctorDemo(onExit = { demoOpen = false; walkthroughPatient = null }, cloud = cloud, initialPatientId = walkthroughPatient, careModel = model, onCareAccount = { accountOpen = true })
        "doctor" -> ConnectedDoctorWorkspace(cloud, model, onExit = { doctorOpen = false }, onAccount = { accountOpen = true }, onWalkthrough = { walkthroughPatient = it; demoOpen = true })
        else -> CareApp(model, onAccount = { accountOpen = true }, onDoctorDemo = {
            if (model.recording.value) model.message.value = rt("Stop the recording before opening the doctor demo.", "डॉक्टर डेमो खोलने से पहले रिकॉर्डिंग रोकें।")
            else { walkthroughPatient = null; demoOpen = true }
        }, onPatientWalkthrough = { if (model.recording.value) model.message.value = rt("Stop the recording before opening the walkthrough.", "डेमो खोलने से पहले रिकॉर्डिंग रोकें।") else patientWalkthroughUser = it },
            onDoctorWorkspace = { doctorEntryPending = true; if (session != null) { doctorOpen = true; doctorEntryPending = false } else accountOpen = true },
            welcomeAccountContent = { close, onBusy -> AccountContent(cloud, model, close, onBusyChange = onBusy, banner = joinBanner) },
            welcomeDoctorContent = { close, onBusy ->
                if (session == null) AccountContent(cloud, model, close, heading = rt("Doctor sign in", "डॉक्टर साइन इन"), onBusyChange = onBusy)
                else Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Heading(rt("Your doctor workspace", "आपका डॉक्टर कार्यक्षेत्र"), 26)
                    Text(session!!.email)
                    Text(rt("Use your own account. Professional verification and patient access are separate.", "अपना खाता इस्तेमाल करें। डॉक्टर होने की पुष्टि और मरीज़ तक पहुँच अलग-अलग हैं।"))
                    Button(onClick = { doctorOpen = true; doctorEntryPending = false }, modifier = Modifier.fillMaxWidth()) { Text(rt("Open workspace", "कार्यक्षेत्र खोलें")) }
                }
            }, onWelcomePanelChanged = { doctorEntryPending = it == "doctor" })
        } }
        CompositionLocalProvider(LocalCareLanguage provides rootLanguage) {
        if (accountOpen) AccountSheet(cloud, model, onDismiss = { accountOpen = false; if (cloud.session.value == null) doctorEntryPending = false }, banner = joinBanner)
        val joinSession = session
        if (joinSession != null && pending != null) PendingJoinDialog(cloud, model, pending!!, joinSession.email, onClear = root::clearPendingJoin)
        }
    }
}

/** Account authentication only. Never grants clinical verification or loads patient records. */
@Composable fun DoctorAiAccountSheet(cloud:CareCloud,onDismiss:()->Unit) {
    val session by cloud.session.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") };var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Cream) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Heading("Sahara AI sign-in",26)
            Text("Use your real Sahara account for authenticated online requests. This does not verify you as a doctor or grant access to any patient.")
            if(session!=null) { Text("Account connected",color=Rust);TextButton(onClick=onDismiss) { Text("Return to conversation") } }
            else {
                OutlinedTextField(email,{email=it},singleLine=true,label={Text("Sahara account email")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},singleLine=true,label={Text("Password")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
                Button(onClick={if(!busy){busy=true;error=null;scope.launch {try {cloud.signIn(email,password);password="";onDismiss()}catch(e:Exception){if(e is CancellationException)throw e;error="Sign-in did not finish. Check your email, password and confirmation status."}finally{busy=false}}}},enabled=!busy && email.contains('@') && password.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(busy) "Signing in…" else "Sign in") }
                Text("The prefilled doctor preview credentials are not a real account. To create an account, return to Sahara → Your account.",style=MaterialTheme.typography.bodySmall)
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            TextButton(onClick=onDismiss,enabled=!busy) {Text("Close")}
        }
    }
}

@Composable fun AccountSheet(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, banner: String? = null) {
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        AccountContent(cloud, vm, onDismiss, onBusyChange = { busy = it }, banner = banner)
    }
}

/** The same genuine account form, reusable inside the welcome panel. */
@Composable fun AccountContent(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, heading: String? = null, onBusyChange: (Boolean) -> Unit = {}, banner: String? = null) {
    val session by cloud.session.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var allowChanges by rememberSaveable { mutableStateOf(false) }
    var allowCareTeam by rememberSaveable(selected) { mutableStateOf(false) }
    var invite by rememberSaveable(selected) { mutableStateOf("") }
    var inviteDoctor by rememberSaveable(selected) { mutableStateOf(false) }
    var doctorEdit by rememberSaveable(selected) { mutableStateOf(false) }
    var doctorNotes by rememberSaveable(selected) { mutableStateOf(true) }
    var doctorCalendar by rememberSaveable(selected) { mutableStateOf(true) }
    var doctorReports by rememberSaveable(selected) { mutableStateOf(false) }
    var doctorCosts by rememberSaveable(selected) { mutableStateOf(false) }
    var doctorDays by rememberSaveable(selected) { mutableStateOf(30) }
    var members by remember(selected) { mutableStateOf<List<CareMember>>(emptyList()) }
    var membersLoaded by remember(selected) { mutableStateOf(false) }
    var addFamily by remember { mutableStateOf(false) }
    var inviteTick by remember { mutableStateOf(0) }
    var invites by remember(selected) { mutableStateOf<List<CareInviteEntry>>(emptyList()) }
    val inviteStore = remember { careInviteStore(context) }
    var removing by remember { mutableStateOf<CareMember?>(null) }
    var signingOut by remember { mutableStateOf(false) }
    val t = careText()
    SideEffect { onBusyChange(busy) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        scope.launch {
            try { action() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: t("Couldn't finish. Please try again.", "काम पूरा नहीं हो सका। कृपया फिर कोशिश करें।") }
            finally { busy = false }
        }
    }
    LaunchedEffect(selected, members, membersLoaded, inviteTick, session?.userId) {
        val acct = session?.userId ?: return@LaunchedEffect; val now = System.currentTimeMillis()
        val all = inviteStore.all(acct)
        val next = if (membersLoaded) CareInviteLog.reconcile(all, selected, members.size, now).also { if (it != all) inviteStore.save(acct, it) } else all
        invites = next.filter { it.person == selected }.sortedByDescending { it.createdAt }
    }
    LaunchedEffect(selected, owner) {
        if (session != null && owner && selected.isNotBlank()) try { members = cloud.members(selected); membersLoaded = true }
        catch (e: Exception) { if (e is CancellationException) throw e; error = t("Couldn't load the people you've invited.", "आपके बुलाए लोगों की सूची नहीं खुल सकी।") }
    }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading(heading ?: if (session == null) if (create) t("Create an account", "खाता बनाएँ") else t("Sign in", "साइन इन करें") else t("Your account", "आपका खाता"), 28)
            banner?.let { Surface(color = Mint, shape = RoundedCornerShape(14.dp)) { Text(it, Modifier.fillMaxWidth().padding(14.dp), fontWeight = FontWeight.Medium) } }
            if (session == null) {
                Text(t("Sign in to share care notes and daily care with people you invite.", "जिन्हें आप बुलाएँ, उनके साथ देखभाल नोट और रोज़ की देखभाल साझा करने के लिए साइन इन करें।"))
                OutlinedTextField(email, { email = it }, label = { Text(t("Email", "ईमेल")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(t("Password", "पासवर्ड")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (create) Text(t("Use at least 10 characters.", "कम से कम 10 अक्षर रखें।"), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { run {
                    if (create) { if (!cloud.signUp(email, password)) { notice = t("Check your email to confirm your account, then sign in.", "खाता पक्का करने के लिए अपना ईमेल देखें, फिर साइन इन करें।"); create = false; password = "" } }
                    else cloud.signIn(email, password)
                } }, enabled = !busy && email.contains('@') && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) t("Please wait…", "कृपया रुकें…") else if (create) t("Create account", "खाता बनाएँ") else t("Sign in", "साइन इन करें")) }
                if (!create) TextButton(onClick = { run {
                    cloud.sendPasswordReset(email)
                    notice = t("If that email has an account, you'll receive a reset link.", "अगर इस ईमेल से खाता बना है, तो पासवर्ड बदलने का लिंक आपको मिल जाएगा।")
                } }, enabled = !busy && email.contains('@') && CareEmailAvailability.ready) { Text(t("Forgot password?", "पासवर्ड भूल गए?")) }
                TextButton(onClick = { create = !create; error = null; notice = null }, enabled = !busy && (create || CareEmailAvailability.ready)) { Text(if (create) t("Already have an account? Sign in", "पहले से खाता है? साइन इन करें") else t("Create an account", "खाता बनाएँ")) }
                if (!CareEmailAvailability.ready) Text(t("New accounts and password reset are temporarily unavailable. Existing confirmed accounts can sign in.", "नया खाता बनाना और पासवर्ड बदलना अभी कुछ समय के लिए बंद है। पहले से पक्के खाते साइन इन कर सकते हैं।"), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(session!!.email)
                OutlinedButton(onClick = { vm.refresh(); onDismiss() }, enabled = !busy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(t("Refresh records", "रिकॉर्ड फिर से लाएँ")) }
                DetailPanel(t("Join someone's care", "किसी की देखभाल से जुड़ें"), Icons.Default.GroupAdd) {
                    Text(t("The person who invited you will see your email.", "जिसने आपको बुलाया है, उन्हें आपका ईमेल दिखेगा।"), style = MaterialTheme.typography.bodySmall)
                    CareField(code, { code = CareInviteLink.parse(it)?.code ?: it.trim().take(4000) }, t("Invitation code or link", "निमंत्रण कोड या लिंक"), singleLine = true)
                    OutlinedButton(onClick = { val pasted = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString(); val invite = CareInviteLink.parse(pasted); if (invite != null) { code = invite.code; error = null; notice = t("Invite pasted. Tap Join.", "निमंत्रण चिपका दिया। अब “जुड़ें” दबाएँ।") } else { notice = null; error = t("No Sahara invite found on the clipboard. Copy the WhatsApp message or link first.", "कॉपी किए गए टेक्स्ट में Sahara का निमंत्रण नहीं मिला। पहले WhatsApp संदेश या लिंक कॉपी करें।") } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(8.dp)); Text(t("Paste invite", "निमंत्रण चिपकाएँ")) }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { run { val before = vm.people.value.map { it.id }.toSet(); try { cloud.join(code) } catch (e: Exception) { if (e is CancellationException) throw e; throw IllegalArgumentException(careJoinFailureMessage(e.message), e) }; code = ""; val added = vm.joinedRefresh(before); notice = added?.name?.let { t("You can now see $it's care.", "अब आप $it की देखभाल देख सकते हैं।") } ?: t("You can now see their care.", "अब आप उनकी देखभाल देख सकते हैं।") } }, enabled = !busy && code.trim().length == 48) { Text(t("Join", "जुड़ें")) }
                }
                if (owner && selected.isNotBlank()) DetailPanel(people.firstOrNull { it.id == selected }?.name.orEmpty().let { t("Share $it's care", "$it की देखभाल साझा करें") }, Icons.Default.PersonAdd) {
                    Button(onClick = { addFamily = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(t("Add family", "परिवार जोड़ें")) }
                    Text(people.firstOrNull { it.id == selected }?.name.orEmpty().let { t("Let family see $it's care on their own phone", "परिवार अपने फ़ोन पर $it की देखभाल देख सके") }, style = MaterialTheme.typography.bodySmall)
                    Text(t("Who are you inviting?", "आप किसे बुला रहे हैं?"), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !inviteDoctor, onClick = { inviteDoctor = false; invite = "" }, modifier = Modifier.heightIn(min = 48.dp), label = { Text(t("Family", "परिवार")) })
                        FilterChip(selected = inviteDoctor, onClick = { inviteDoctor = true; invite = "" }, modifier = Modifier.heightIn(min = 48.dp), label = { Text(t("Doctor", "डॉक्टर")) })
                    }
                    if (!inviteDoctor) {
                        Text(t("Invited people can read the care notes and daily entries. Recordings and unfinished conversations aren't shared.", "बुलाए गए लोग देखभाल नोट और रोज़ की जानकारी पढ़ सकते हैं। रिकॉर्डिंग और अधूरी बातचीत साझा नहीं होती।"))
                        CheckLine(allowChanges, { allowChanges = it; invite = "" }, t("Allow them to add and change records", "उन्हें रिकॉर्ड जोड़ने और बदलने दें"))
                        CheckLine(allowCareTeam, { allowCareTeam = it; invite = "" }, t("Share family and care-team contacts", "परिवार और देखभाल टीम के नंबर साझा करें"))
                    } else {
                        Text(t("This creates a one-person code that you share yourself. Nothing is sent to the doctor, and creating it does not verify them.", "इससे एक व्यक्ति के लिए कोड बनता है, जिसे आप ख़ुद भेजते हैं। डॉक्टर को अपने-आप कुछ नहीं जाता, और कोड बनाने से डॉक्टर की पहचान पक्की नहीं होती।"), style = MaterialTheme.typography.bodySmall)
                        Text(t("A doctor can only join with this code after their clinician verification has been approved. Until then, joining with it will fail with a verification message.", "डॉक्टर इस कोड से तभी जुड़ सकते हैं जब उनकी डॉक्टर-पहचान मंज़ूर हो चुकी हो। तब तक जुड़ने पर पहचान से जुड़ा संदेश आएगा।"), style = MaterialTheme.typography.bodySmall)
                        Text(t("What they can open", "वे क्या खोल सकेंगे"), style = MaterialTheme.typography.bodyMedium)
                        CheckLine(doctorNotes, { doctorNotes = it; invite = "" }, t("Care notes and conversations", "देखभाल नोट और बातचीत"))
                        CheckLine(doctorCalendar, { doctorCalendar = it; invite = "" }, t("Calendar, medicines and tasks", "कैलेंडर, दवाइयाँ और काम"))
                        CheckLine(doctorReports, { doctorReports = it; invite = "" }, t("Report-derived records", "रिपोर्ट से बने रिकॉर्ड"))
                        CheckLine(doctorCosts, { doctorCosts = it; invite = "" }, t("Costs", "ख़र्च"))
                        CheckLine(doctorEdit, { doctorEdit = it; invite = "" }, t("Allow them to add and change records (otherwise read-only)", "उन्हें रिकॉर्ड जोड़ने और बदलने दें (नहीं तो सिर्फ़ पढ़ सकेंगे)"))
                        Text(t("Access lasts", "कितने दिन खुला रहे"), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 7, 30, 90).forEach { d -> FilterChip(selected = doctorDays == d, onClick = { doctorDays = d; invite = "" }, modifier = Modifier.heightIn(min = 48.dp), label = { Text(t("$d days", "$d दिन")) }) } }
                    }
                    Button(onClick = { val person = selected; val account = session!!.userId; val doctor = inviteDoctor; run { val code = if (doctor) cloud.inviteAccess(person, "clinician", doctorEdit, mapOf("careNote" to doctorNotes, "calendar" to doctorCalendar, "reports" to doctorReports, "costs" to doctorCosts), doctorDays, expectedAccountId = account) else cloud.invite(person, allowChanges, allowCareTeam); if (vm.selected.value == person && cloud.session.value?.userId == account && vm.isOwner.value) invite = code } }, enabled = !busy && (!inviteDoctor || doctorNotes || doctorCalendar || doctorReports || doctorCosts), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (inviteDoctor) t("Create doctor invitation", "डॉक्टर के लिए निमंत्रण बनाएँ") else t("Create invitation", "निमंत्रण बनाएँ")) }
                    if (invite.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp)); Text(if (inviteDoctor) t("One person · the code expires in 3 days · works only for an approved clinician account", "एक व्यक्ति · कोड 3 दिन में बंद हो जाएगा · सिर्फ़ मंज़ूर डॉक्टर खाते के लिए") else t("One person · expires in 3 days", "एक व्यक्ति · 3 दिन में बंद हो जाएगा"), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, CareInviteLink.shareText(people.firstOrNull { it.id == selected }?.name.orEmpty(), invite)), t("Share invitation", "निमंत्रण भेजें"))) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(t("Share invitation", "निमंत्रण भेजें")) }
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(invite, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (invites.isNotEmpty()) { Text(t("Invitations", "भेजे गए निमंत्रण"), fontWeight = FontWeight.Medium); invites.forEach { Text(CareInviteLog.label(it, System.currentTimeMillis()), Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium) } }
                    members.forEach { member ->
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f).padding(top = 12.dp)) { Text(member.email); Text(if (member.canEdit) t("Can make changes", "बदलाव कर सकते हैं") else t("Can read", "सिर्फ़ पढ़ सकते हैं"), style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { removing = member }, enabled = !busy) { Text(t("Remove", "हटाएँ")) }
                        }
                        CheckLine(member.careTeamRead, { enabled -> val person = selected; val account = session!!.userId; run { require(vm.selected.value == person && cloud.session.value?.userId == account && vm.isOwner.value); cloud.setCareTeamSharing(person, member.userId, enabled); if (vm.selected.value == person && cloud.session.value?.userId == account) members = cloud.members(person) } }, t("Share care-team contacts with this person", "इस व्यक्ति के साथ देखभाल टीम के नंबर साझा करें"))
                        if (!member.careTeamPermissionKnown) Text(t("Care-team sharing is unavailable until the server update is installed.", "सर्वर का अपडेट लगने तक देखभाल टीम के नंबर साझा नहीं हो सकते।"), style = MaterialTheme.typography.bodySmall)
                    }
                    CareContactManager(vm, selected)
                }
                TextButton(onClick = { signingOut = true }, enabled = !busy) { Text(t("Sign out", "साइन आउट करें")) }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.delete_account_url)))) }, enabled = !busy) { Text(t("Delete account", "खाता मिटाएँ")) }
            }
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) }) { Text(t("Privacy policy", "निजता नीति")) }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    if (addFamily) AddFamilySheet(vm, onDismiss = { addFamily = false }, onSignIn = {}, onInvited = { inviteTick++ })
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(t("Stop sharing with this person?", "इस व्यक्ति के साथ साझा करना बंद करें?")) }, text = { Text(t("They won't be able to read or change these records online. Copies they've already saved cannot be recalled.", "वे ये रिकॉर्ड ऑनलाइन न पढ़ सकेंगे, न बदल सकेंगे। जो कॉपी वे पहले सहेज चुके हैं, वह वापस नहीं ली जा सकती।")) }, confirmButton = { TextButton(onClick = { removing = null; run { cloud.removeMember(selected, member.userId); members = cloud.members(selected) } }) { Text(t("Stop sharing", "साझा करना बंद करें")) } }, dismissButton = { TextButton(onClick = { removing = null }) { Text(t("Keep sharing", "साझा करते रहें")) } }) }
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text(t("Sign out?", "साइन आउट करें?")) }, confirmButton = { TextButton(onClick = { signingOut = false; run { vm.stopRecording(); cloud.signOut() } }) { Text(t("Sign out", "साइन आउट करें")) } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text(t("Cancel", "रद्द करें")) } })
}
