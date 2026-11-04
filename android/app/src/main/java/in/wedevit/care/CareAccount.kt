@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import android.app.Application
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
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
        if (invite == null) { linkNotice.value = careTr(CareLanguageState.shown, "That invitation link isn't complete. Ask for a new one, or paste the code under Join someone's care.", "वह निमंत्रण लिंक अधूरा है। नया लिंक माँगें, या “किसी की देखभाल से जुड़ें” में कोड चिपकाएँ।"); return }
        PendingJoin(invite.code, invite.name, System.currentTimeMillis()).also { pendingStore.save(it); pendingJoin.value = it }
    }
    fun clearPendingJoin() { pendingStore.clear(); pendingJoin.value = null }
    /** Set by MainActivity when a reminder notification is tapped: the person to open Today for ("" = whoever is selected). */
    val openToday = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
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
    val openToday by root.openToday.collectAsStateWithLifecycle()
    val session by cloud.session.collectAsStateWithLifecycle()
    // Survives the account switch that follows deleting an account, so the guest screen can say what happened.
    var accountNotice by rememberSaveable { mutableStateOf<String?>(null) }
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
        val rt = { en: String, hi: String -> careTr(rootLanguage.hindi, en, hi) }
        LaunchedEffect(accountNotice) { if (session == null) accountNotice?.let { model.message.value = it; accountNotice = null } }
        val joinBanner = pending?.let { rt("Sign in or create an account to join ${it.possessive} care", "${it.name.ifBlank { "इस व्यक्ति" }} की देखभाल से जुड़ने के लिए साइन इन करें या खाता बनाएँ") }
        val mode = when { patientWalkthroughUser != null -> "patient-walkthrough"; demoOpen -> "doctor-walkthrough"; doctorOpen && session != null -> "doctor"; else -> "care" }
        // Entering a walkthrough or workspace moves forward; leaving it moves back to the welcome/care app.
        // One language choice for every screen, the doctor workspace and walkthroughs included.
        CompositionLocalProvider(LocalCareLanguage provides rootLanguage) { CareLanguageDirection(rootLanguage) {
        SaharaScreenHost(mode, Modifier.fillMaxSize().background(Cream), label = "Sahara root", direction = { _, to -> if (to == "care") SaharaMotion.Direction.Back else SaharaMotion.Direction.Forward }) { shown -> when (shown) {
        "patient-walkthrough" -> patientWalkthroughUser?.let { PatientWalkthroughHost(it, onExit = { patientWalkthroughUser = null }, onSwitch = { patientWalkthroughUser = it }) }
        "doctor-walkthrough" -> DoctorDemo(onExit = { demoOpen = false; walkthroughPatient = null }, cloud = cloud, initialPatientId = walkthroughPatient, careModel = model, onCareAccount = { accountOpen = true })
        "doctor" -> ConnectedDoctorWorkspace(cloud, model, onExit = { doctorOpen = false }, onAccount = { accountOpen = true }, onWalkthrough = { walkthroughPatient = it; demoOpen = true })
        else -> CareApp(model, onAccount = { accountOpen = true }, onDoctorDemo = {
            if (model.recording.value) model.message.value = rt("Stop the recording before opening the doctor practice walkthrough.", "डॉक्टर अभ्यास वॉकथ्रू खोलने से पहले रिकॉर्डिंग रोकें।")
            else { walkthroughPatient = null; demoOpen = true }
        }, onPatientWalkthrough = { if (model.recording.value) model.message.value = rt("Stop the recording before opening the walkthrough.", "वॉकथ्रू खोलने से पहले रिकॉर्डिंग रोकें।") else patientWalkthroughUser = it },
            onDoctorWorkspace = { doctorEntryPending = true; if (session != null) { doctorOpen = true; doctorEntryPending = false } else accountOpen = true },
            welcomeAccountContent = { close, onBusy -> AccountContent(cloud, model, close, onBusyChange = onBusy, banner = joinBanner, onInvite = root::offerInviteLink, onDeleted = { accountNotice = it }) },
            welcomeDoctorContent = { close, onBusy ->
                if (session == null) AccountContent(cloud, model, close, heading = rt("Doctor sign in", "डॉक्टर साइन इन"), onBusyChange = onBusy, onDeleted = { accountNotice = it })
                else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Heading(rt("Your doctor workspace", "आपका डॉक्टर कार्यक्षेत्र"), 26)
                    Text(session!!.email, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    Text(rt("Use your own account. Professional verification and patient access are separate.", "अपना खाता इस्तेमाल करें। डॉक्टर होने की पुष्टि और मरीज़ तक पहुँच अलग-अलग हैं।"), color = DoctorCardSub)
                    Button(onClick = { doctorOpen = true; doctorEntryPending = false }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(rt("Open workspace", "कार्यक्षेत्र खोलें")) }
                }
            }, onWelcomePanelChanged = { doctorEntryPending = it == "doctor" },
            openToday = openToday, onOpenTodayHandled = { root.openToday.value = null })
        } }
        } }
        CompositionLocalProvider(LocalCareLanguage provides rootLanguage) { CareLanguageDirection(rootLanguage) {
        if (accountOpen) AccountSheet(cloud, model, onDismiss = { accountOpen = false; if (cloud.session.value == null) doctorEntryPending = false }, banner = joinBanner, onInvite = root::offerInviteLink, onDeleted = { accountNotice = it; accountOpen = false })
        val joinSession = session
        if (joinSession != null && pending != null) PendingJoinDialog(cloud, model, pending!!, joinSession.email, onClear = root::clearPendingJoin)
        } }
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
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { CareLanguageMenu() }
            Heading("Sahara AI sign-in",26)
            Text("Use your real Sahara account for authenticated online requests. This does not verify you as a doctor or grant access to any patient.")
            if(session!=null) { Text("Account connected",color=Rust);TextButton(onClick=onDismiss) { Text("Return to conversation") } }
            else {
                OutlinedTextField(email,{email=it},singleLine=true,label={Text("Sahara account email")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},singleLine=true,label={Text("Password")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
                Button(onClick={if(!busy){busy=true;error=null;scope.launch {try {cloud.signIn(email,password);password="";onDismiss()}catch(e:Exception){if(e is CancellationException)throw e;error="Sign-in did not finish. Check your email, password and confirmation status."}finally{busy=false}}}},enabled=!busy && email.contains('@') && password.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(busy) "Signing in…" else "Sign in") }
                Text("The prefilled doctor preview credentials are not a real account. To create an account, return to Sahara → Your account.",style=MaterialTheme.typography.bodySmall)
            }
            error?.let {Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())}
            TextButton(onClick=onDismiss,enabled=!busy) {Text("Close")}
        }
    }
}

@Composable fun AccountSheet(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, banner: String? = null, onInvite: ((String) -> Unit)? = null, onDeleted: (String) -> Unit = {}) {
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        // The sheet covers the app header, so the language dropdown comes along to the sign-in form.
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) { CareLanguageMenu() }
        AccountContent(cloud, vm, onDismiss, onBusyChange = { busy = it }, banner = banner, onInvite = onInvite, onDeleted = onDeleted)
    }
}

/** The same genuine account form, reusable inside the welcome panel. */
private fun clipboardText(context: android.content.Context): String? = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

/** [onInvite] keeps a pasted invitation while signed out (it is offered after sign-in). [onDeleted] receives the message to show once the account is gone. */
@Composable fun AccountContent(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, heading: String? = null, onBusyChange: (Boolean) -> Unit = {}, banner: String? = null, onInvite: ((String) -> Unit)? = null, onDeleted: (String) -> Unit = {}) {
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
    var deleting by remember { mutableStateOf(false) }
    var deleteUnderstood by remember { mutableStateOf(false) }
    var deletionByEmail by remember { mutableStateOf(false) }
    var awaitingConfirmation by rememberSaveable { mutableStateOf("") }
    var openInvites by remember(selected) { mutableStateOf<List<CareOpenInvite>?>(null) }
    var memberDetails by remember(selected) { mutableStateOf<List<CareMemberDetail>?>(null) }
    val canEditShared by vm.canEdit.collectAsStateWithLifecycle()
    val t = careText(); val hindi = careHindi()
    SideEffect { onBusyChange(busy) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        scope.launch {
            try { action() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: t("Couldn't finish. Please try again.", "काम पूरा नहीं हो सका। कृपया फिर कोशिश करें।") }
            finally { busy = false }
        }
    }
    // Invitation status and the people list both come from the server; this phone only remembers name and relationship.
    LaunchedEffect(selected, inviteTick, session?.userId) {
        val acct = session?.userId ?: return@LaunchedEffect; val now = System.currentTimeMillis()
        val all = inviteStore.all(acct); val kept = CareInviteLog.prune(all, now); if (kept != all) inviteStore.save(acct, kept)
        invites = kept.filter { it.person == selected }.sortedByDescending { it.createdAt }
    }
    suspend fun loadSharing(person: String) {
        val nextMembers = cloud.members(person); val nextOpen = cloud.openInvites(person); val nextDetails = cloud.memberDetails(person)
        if (vm.selected.value != person) return
        members = nextMembers; openInvites = nextOpen; memberDetails = nextDetails; membersLoaded = true
    }
    LaunchedEffect(selected, owner, inviteTick) {
        if (session != null && owner && selected.isNotBlank()) try { loadSharing(selected) }
        catch (e: Exception) { if (e is CancellationException) throw e; error = t("Couldn't load the people you've invited.", "आपके बुलाए लोगों की सूची नहीं खुल सकी।") }
    }
    fun authRun(action: suspend () -> Unit) = run {
        try { action() } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (careAuthProblem(e.message) == CareAuthProblem.NOT_CONFIRMED) awaitingConfirmation = email.trim()
            throw IllegalArgumentException(careAuthMessage(e.message, hindi), e)
        }
    }
    fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(careDateFormat(CARE_SHORT_DATE, hindi))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(heading ?: if (session == null) if (create) t("Create an account", "खाता बनाएँ") else t("Sign in", "साइन इन करें") else t("Your account", "आपका खाता"), 28)
            banner?.let { Surface(color = Mint, shape = RoundedCornerShape(14.dp)) { Text(it, Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Medium) } }
            if (session == null) {
                Text(t("Sign in to share care notes and daily care with people you invite.", "जिन्हें आप बुलाएँ, उनके साथ देखभाल नोट और रोज़ की देखभाल साझा करने के लिए साइन इन करें।"), color = DoctorCardSub)
                OutlinedTextField(email, { email = it }, label = { Text(t("Email", "ईमेल")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(t("Password", "पासवर्ड")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    supportingText = if (create) { { Text(t("Use at least 10 characters.", "कम से कम 10 अक्षर रखें।"), fontSize = 14.sp, color = DoctorCardSub) } } else null)
                Button(onClick = { authRun {
                    if (create) when (cloud.signUp(email, password)) {
                        CareSignUp.SIGNED_IN -> password = ""
                        CareSignUp.CONFIRMATION_SENT -> { awaitingConfirmation = email.trim(); create = false; password = ""; notice = t("Account created. We are sending a link to ${email.trim()}. Open that link on this phone, then sign in here. Check spam too. If nothing comes in 10 minutes, tap Resend confirmation email. If it still does not come, write to workwithdevit@gmail.com.", "खाता बन गया। ${email.trim()} पर एक लिंक भेज रहे हैं। वह लिंक इसी फ़ोन पर खोलें, फिर यहाँ साइन इन करें। स्पैम में भी देखें। 10 मिनट में ईमेल न आए तो “पुष्टि ईमेल फिर भेजें” दबाएँ। फिर भी न आए तो workwithdevit@gmail.com पर लिखें।") }
                        CareSignUp.ALREADY_REGISTERED -> { create = false; password = ""; error = careAuthMessage(CARE_ALREADY_REGISTERED, hindi) }
                    }
                    else { cloud.signIn(email, password); awaitingConfirmation = "" }
                } }, enabled = !busy && email.contains('@') && password.isNotEmpty(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current); Spacer(Modifier.width(8.dp)) }
                    Text(if (busy) t("Please wait…", "कृपया रुकें…") else if (create) t("Create account", "खाता बनाएँ") else t("Sign in", "साइन इन करें"))
                }
                if (awaitingConfirmation.isNotBlank()) OutlinedButton(onClick = { authRun { cloud.resendConfirmation(awaitingConfirmation); notice = t("Sending the email again to $awaitingConfirmation. If it does not come, write to workwithdevit@gmail.com.", "$awaitingConfirmation पर ईमेल फिर भेज रहे हैं। न आए तो workwithdevit@gmail.com पर लिखें।") } }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.MailOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Resend confirmation email", "पुष्टि ईमेल फिर भेजें")) }
                // Secondary links side by side, so the form stays short.
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!create) TextButton(onClick = { authRun {
                        cloud.sendPasswordReset(email)
                        notice = t("If that email has an account, you'll receive a reset link.", "अगर इस ईमेल से खाता बना है, तो पासवर्ड बदलने का लिंक आपको मिल जाएगा।")
                    } }, enabled = !busy && email.contains('@') && CareEmailAvailability.ready, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Forgot password?", "पासवर्ड भूल गए?")) }
                    TextButton(onClick = { create = !create; error = null; notice = null }, enabled = !busy && (create || CareEmailAvailability.ready), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (create) t("Already have an account? Sign in", "पहले से खाता है? साइन इन करें") else t("Create an account", "खाता बनाएँ"), fontWeight = FontWeight.SemiBold) }
                }
                if (!CareEmailAvailability.ready) Text(t("New accounts and password reset are temporarily unavailable. Existing confirmed accounts can sign in.", "नया खाता बनाना और पासवर्ड बदलना अभी कुछ समय के लिए बंद है। पहले से पक्के खाते साइन इन कर सकते हैं।"), fontSize = 14.sp, color = DoctorCardSub)
                if (onInvite != null) OutlinedButton(onClick = { val pasted = clipboardText(context); if (CareInviteLink.parse(pasted) != null) { onInvite(pasted.orEmpty()); error = null; notice = t("Invitation kept. Sign in or create an account, and Sahara will ask you to join.", "निमंत्रण रख लिया। साइन इन करें या खाता बनाएँ, फिर Sahara आपसे जुड़ने को पूछेगा।") } else { notice = null; error = t("No Sahara invite found on the clipboard. Copy the WhatsApp message or link first.", "कॉपी किए गए टेक्स्ट में Sahara का निमंत्रण नहीं मिला। पहले WhatsApp संदेश या लिंक कॉपी करें।") } }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Got an invitation? Paste it here", "निमंत्रण मिला है? यहाँ चिपकाएँ")) }
            } else {
                // Who is signed in, with refresh beside it: one row instead of a line and a separate button.
                Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(28.dp), tint = Rust); Spacer(Modifier.width(12.dp))
                        Text(session!!.email, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.refresh(); onDismiss() }, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, t("Refresh records", "रिकॉर्ड फिर से लाएँ"), tint = Rust) }
                    }
                }
                if (!owner && selected.isNotBlank()) people.firstOrNull { it.id == selected }?.let { p -> Text(if (canEditShared) t("${p.name}'s care is shared with you. You can read it and add to it.", "${p.name} की देखभाल आपके साथ साझा है। आप इसे देख सकते हैं और जोड़ सकते हैं।") else t("${p.name}'s care is shared with you. You can read it; only the person who invited you can allow changes.", "${p.name} की देखभाल आपके साथ साझा है। आप इसे देख सकते हैं; बदलाव की अनुमति सिर्फ़ बुलाने वाले दे सकते हैं।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub) }
                if (owner && selected.isNotBlank()) DetailPanel(people.firstOrNull { it.id == selected }?.name.orEmpty().let { t("Share $it's care", "$it की देखभाल साझा करें") }, Icons.Default.PersonAdd) {
                    Text(people.firstOrNull { it.id == selected }?.name.orEmpty().let { t("Let family see $it's care on their own phone", "परिवार अपने फ़ोन पर $it की देखभाल देख सके") }, style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
                    Button(onClick = { addFamily = true }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(t("Add family", "परिवार जोड़ें")) }
                    HorizontalDivider(color = DoctorHairline)
                    Text(t("Who are you inviting?", "आप किसे बुला रहे हैं?"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !inviteDoctor, onClick = { inviteDoctor = false; invite = "" }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Family", "परिवार")) }
                        SegmentedButton(selected = inviteDoctor, onClick = { inviteDoctor = true; invite = "" }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Doctor", "डॉक्टर")) }
                    }
                    if (!inviteDoctor) {
                        Text(t("Invited people can read the care notes and daily entries. Recordings and unfinished conversations aren't shared.", "बुलाए गए लोग देखभाल नोट और रोज़ की जानकारी पढ़ सकते हैं। रिकॉर्डिंग और अधूरी बातचीत साझा नहीं होती।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
                        CheckLine(allowChanges, { allowChanges = it; invite = "" }, t("Allow them to add and change records", "उन्हें रिकॉर्ड जोड़ने और बदलने दें"))
                        CheckLine(allowCareTeam, { allowCareTeam = it; invite = "" }, t("Share family and care-team contacts", "परिवार और देखभाल टीम के नंबर साझा करें"))
                    } else {
                        // The two long rules about doctor codes sit behind one tap.
                        InfoDisclosure(t("About this", "इसके बारे में"), Cream) {
                            Text(t("This creates a one-person code that you share yourself. Nothing is sent to the doctor, and creating it does not verify them.", "इससे एक व्यक्ति के लिए कोड बनता है, जिसे आप ख़ुद भेजते हैं। डॉक्टर को अपने-आप कुछ नहीं जाता, और कोड बनाने से डॉक्टर की पहचान पक्की नहीं होती।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
                            Text(t("A doctor can only join with this code after their clinician verification has been approved. Until then, joining with it will fail with a verification message.", "डॉक्टर इस कोड से तभी जुड़ सकते हैं जब उनकी डॉक्टर-पहचान मंज़ूर हो चुकी हो। तब तक जुड़ने पर पहचान से जुड़ा संदेश आएगा।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
                        }
                        PatientIdForDoctor(selected)
                        Text(t("What they can open", "वे क्या खोल सकेंगे"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        // Chips instead of a wall of checkboxes; each one still toggles on its own.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AccessChip(doctorNotes, t("Care notes and conversations", "देखभाल नोट और बातचीत")) { doctorNotes = !doctorNotes; invite = "" }
                            AccessChip(doctorCalendar, t("Calendar, medicines and tasks", "कैलेंडर, दवाइयाँ और काम")) { doctorCalendar = !doctorCalendar; invite = "" }
                            AccessChip(doctorReports, t("Report-derived records", "रिपोर्ट से बने रिकॉर्ड")) { doctorReports = !doctorReports; invite = "" }
                            AccessChip(doctorCosts, t("Costs", "ख़र्च")) { doctorCosts = !doctorCosts; invite = "" }
                        }
                        CheckLine(doctorEdit, { doctorEdit = it; invite = "" }, t("Allow them to add and change records (otherwise read-only)", "उन्हें रिकॉर्ड जोड़ने और बदलने दें (नहीं तो सिर्फ़ पढ़ सकेंगे)"))
                        Text(t("Access lasts", "कितने दिन खुला रहे"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf(1, 7, 30, 90).forEach { d -> AccessChip(doctorDays == d, t("$d days", "$d दिन")) { doctorDays = d; invite = "" } } }
                    }
                    OutlinedButton(onClick = { val person = selected; val account = session!!.userId; val doctor = inviteDoctor; run { val before = cloud.openInvites(person); val code = if (doctor) cloud.inviteAccess(person, "clinician", doctorEdit, mapOf("careNote" to doctorNotes, "calendar" to doctorCalendar, "reports" to doctorReports, "costs" to doctorCosts), doctorDays, expectedAccountId = account) else cloud.invite(person, allowChanges, allowCareTeam); if (vm.selected.value == person && cloud.session.value?.userId == account && vm.isOwner.value) { invite = code; inviteStore.logCreated(cloud, account, person, "", "", if (doctor) "clinician" else "family", before); inviteTick++ } } }, enabled = !busy && (!inviteDoctor || doctorNotes || doctorCalendar || doctorReports || doctorCosts), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (inviteDoctor) t("Create doctor invitation", "डॉक्टर के लिए निमंत्रण बनाएँ") else t("Create invitation", "निमंत्रण बनाएँ")) }
                    if (invite.isNotEmpty()) Surface(shape = RoundedCornerShape(14.dp), color = Mint, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (inviteDoctor) t("One person · the code expires in 3 days · works only for an approved clinician account", "एक व्यक्ति · कोड 3 दिन में बंद हो जाएगा · सिर्फ़ मंज़ूर डॉक्टर खाते के लिए") else t("One person · the link expires in 3 days · access then lasts 30 days", "एक व्यक्ति · लिंक 3 दिन में बंद होगा · उसके बाद पहुँच 30 दिन रहेगी"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardInk)
                            Button(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, CareInviteLink.shareText(people.firstOrNull { it.id == selected }?.name.orEmpty(), invite)), t("Share invitation", "निमंत्रण भेजें"))) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Share invitation", "निमंत्रण भेजें")) }
                            androidx.compose.foundation.text.selection.SelectionContainer { Text(invite, style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    if (invites.isNotEmpty()) { HorizontalDivider(color = DoctorHairline); Text(t("Invitations", "भेजे गए निमंत्रण"), fontWeight = FontWeight.Medium, color = DoctorCardInk); val now = System.currentTimeMillis(); invites.forEach { e ->
                        val status = CareInviteLog.status(e, openInvites, memberDetails, now)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MailOutline, null, Modifier.size(18.dp), tint = DoctorCardSub); Spacer(Modifier.width(8.dp))
                            Text(CareInviteLog.label(e, status, now, hindi, members.firstOrNull { it.userId == status.memberId }?.email.orEmpty()), Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, color = DoctorCardInk)
                            if (status.state == CareInviteState.OPEN) TextButton(onClick = { val person = selected; val acct = session!!.userId; run { cloud.cancelInvite(e.inviteId); inviteStore.save(acct, inviteStore.all(acct).filterNot { it.inviteId == e.inviteId }); inviteTick++; if (vm.selected.value == person) notice = t("Invitation cancelled. The link no longer works.", "निमंत्रण रद्द हुआ। अब लिंक काम नहीं करेगा।") } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), color = RustText) }
                        }
                    } }
                    if (membersLoaded) { HorizontalDivider(color = DoctorHairline); Text(if (members.isEmpty()) t("Nobody has joined yet.", "अभी तक कोई नहीं जुड़ा।") else t("People with access", "जिन्हें पहुँच है"), fontWeight = FontWeight.Medium, color = if (members.isEmpty()) DoctorCardSub else DoctorCardInk) }
                    members.forEachIndexed { index, member ->
                        val detail = memberDetails?.firstOrNull { it.userId == member.userId }; val now = System.currentTimeMillis()
                        if (index > 0) HorizontalDivider(color = DoctorHairline)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(member.email, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // Access level as a badge; dates on one quiet line.
                                Surface(shape = RoundedCornerShape(50), color = if (member.canEdit) DoctorStatusGoodBg else SaharaSoftBlue) { Text(if (member.canEdit) t("Can make changes", "बदलाव कर सकते हैं") else t("Can read", "सिर्फ़ पढ़ सकते हैं"), Modifier.padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (member.canEdit) DoctorStatusGood else DoctorCardInk) }
                                listOfNotNull(detail?.joinedAt?.takeIf { it > 0 }?.let { t("joined ${day(it)}", "${day(it)} को जुड़े") },
                                    detail?.expiresAt?.takeIf { it > 0 }?.let { if (it > now) t("access until ${day(it)}", "${day(it)} तक पहुँच") else t("access ended ${day(it)} · invite again", "${day(it)} को पहुँच ख़त्म · फिर से बुलाएँ") }).joinToString(" · ").takeIf(String::isNotBlank)?.let { Text(it, fontSize = 14.sp, color = DoctorCardSub) }
                            }
                            TextButton(onClick = { removing = member }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Remove", "हटाएँ"), color = RustText) }
                        }
                        OutlinedButton(onClick = { val person = selected; run {
                            try { cloud.setMemberEdit(person, member.userId, !member.canEdit) } catch (e: CareServerFeatureMissing) { throw IllegalStateException(t("You cannot change access yet. For now, remove them and send a new invite with the access you want.", "अभी पहुँच नहीं बदल सकते। अभी के लिए उन्हें हटाकर, सही पहुँच के साथ नया निमंत्रण भेजें।"), e) }
                            loadSharing(person); if (vm.selected.value == person) notice = if (member.canEdit) t("${member.email} can now only read.", "${member.email} अब सिर्फ़ देख सकते हैं।") else t("${member.email} can now add and change records.", "${member.email} अब रिकॉर्ड जोड़ और बदल सकते हैं।")
                        } }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (member.canEdit) t("Change to read only", "सिर्फ़ देखने की पहुँच दें") else t("Allow adding and changes", "जोड़ने-बदलने की पहुँच दें")) }
                        CheckLine(member.careTeamRead, { enabled -> val person = selected; val account = session!!.userId; run { require(vm.selected.value == person && cloud.session.value?.userId == account && vm.isOwner.value); cloud.setCareTeamSharing(person, member.userId, enabled); if (vm.selected.value == person && cloud.session.value?.userId == account) loadSharing(person) } }, t("Share care-team contacts with this person", "इस व्यक्ति के साथ देखभाल टीम के नंबर साझा करें"))
                        if (!member.careTeamPermissionKnown) Text(t("Sharing care-team contacts is not available yet.", "देखभाल टीम के नंबर अभी साझा नहीं हो सकते।"), fontSize = 14.sp, color = DoctorCardSub)
                    }
                    CareContactManager(vm, selected)
                }
                DetailPanel(t("Join someone's care", "किसी की देखभाल से जुड़ें"), Icons.Default.GroupAdd) {
                    Text(t("The person who invited you will see your email.", "जिसने आपको बुलाया है, उन्हें आपका ईमेल दिखेगा।"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
                    CareField(code, { code = CareInviteLink.parse(it)?.code ?: it.trim().take(4000) }, t("Invitation code or link", "निमंत्रण कोड या लिंक"), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { val pasted = clipboardText(context); val invite = CareInviteLink.parse(pasted); if (invite != null) { code = invite.code; error = null; notice = t("Invite pasted. Tap Join.", "निमंत्रण चिपका दिया। अब “जुड़ें” दबाएँ।") } else { notice = null; error = t("No Sahara invite found on the clipboard. Copy the WhatsApp message or link first.", "कॉपी किए गए टेक्स्ट में Sahara का निमंत्रण नहीं मिला। पहले WhatsApp संदेश या लिंक कॉपी करें।") } }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Paste invite", "निमंत्रण चिपकाएँ"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Button(onClick = { run { val before = vm.people.value.map { it.id }.toSet(); try { cloud.join(code) } catch (e: Exception) { if (e is CancellationException) throw e; throw IllegalArgumentException(careJoinFailureMessage(e.message, hindi), e) }; code = ""; val added = vm.joinedRefresh(before); notice = (added?.name?.let { t("You can now see $it's care.", "अब आप $it की देखभाल देख सकते हैं।") } ?: t("You can now see their care.", "अब आप उनकी देखभाल देख सकते हैं।")) + if (added != null && vm.canEditPerson(added.id)) t(" You can read and add.", " आप देख और जोड़ सकते हैं।") else t(" You can read.", " आप देख सकते हैं।") } }, enabled = !busy && code.trim().length == 48, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(t("Join", "जुड़ें")) }
                    }
                }
                HorizontalDivider(color = DoctorHairline)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { signingOut = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Sign out", "साइन आउट करें")) }
                    TextButton(onClick = { deleteUnderstood = false; deleting = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Delete my account and data", "मेरा खाता और जानकारी मिटाएँ")) }
                }
                if (deletionByEmail) TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.delete_account_url)))) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Request deletion by email", "ईमेल से मिटाने का अनुरोध भेजें")) }
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium, modifier = Modifier.announce()) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium, modifier = Modifier.announce()) }
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Privacy policy", "निजता नीति"), color = DoctorCardSub) }
        }
    if (addFamily) AddFamilySheet(vm, onDismiss = { addFamily = false }, onSignIn = {}, onInvited = { inviteTick++ })
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(t("Stop sharing with this person?", "इस व्यक्ति के साथ साझा करना बंद करें?")) }, text = { Text(t("They won't be able to read or change these records online. Copies they've already saved cannot be recalled.", "वे ये रिकॉर्ड ऑनलाइन न पढ़ सकेंगे, न बदल सकेंगे। जो कॉपी वे पहले सहेज चुके हैं, वह वापस नहीं ली जा सकती।")) }, confirmButton = { TextButton(onClick = { removing = null; val person = selected; run { cloud.removeMember(person, member.userId); loadSharing(person); if (vm.selected.value == person) notice = t("${member.email} no longer has access.", "${member.email} की पहुँच हटा दी गई।") } }) { Text(t("Stop sharing", "साझा करना बंद करें")) } }, dismissButton = { TextButton(onClick = { removing = null }) { Text(t("Keep sharing", "साझा करते रहें")) } }) }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(t("Delete your account and data?", "अपना खाता और जानकारी मिटाएँ?")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("This deletes your Sahara account and: the care records you set up (also for everyone you shared them with), your invitations, your doctor request, and your access to other people's care. This phone's copy is removed too. It cannot be undone. To keep care notes, send them from More first.", "इससे आपका Sahara खाता और ये चीज़ें मिट जाएँगी: आपके बनाए देखभाल रिकॉर्ड (जिनके साथ साझा किए, उनके लिए भी), आपके निमंत्रण, डॉक्टर वाला अनुरोध, और दूसरों की देखभाल तक आपकी पहुँच। इस फ़ोन की कॉपी भी हटेगी। यह वापस नहीं होगा। देखभाल नोट रखने हों तो पहले “और” से भेज लें।"))
            CheckLine(deleteUnderstood, { deleteUnderstood = it }, t("I understand this can't be undone", "मैं समझता/समझती हूँ कि यह वापस नहीं होगा"))
        } },
        confirmButton = { TextButton(onClick = { deleting = false; run {
            vm.stopRecording()
            val result = try { cloud.deleteMyAccount() } catch (e: CareServerFeatureMissing) { deletionByEmail = true; throw IllegalStateException(t("You cannot delete from the app yet. Nothing was deleted. Use Request deletion by email below.", "अभी ऐप से नहीं मिटा सकते। कुछ नहीं मिटा। नीचे “ईमेल से मिटाने का अनुरोध भेजें” दबाएँ।"), e) }
            runCatching { vm.forgetLocalAccountData() }; inviteStore.save(session!!.userId, emptyList())
            onDeleted(if (result.loginRemoved) t("Your Sahara account and its data were deleted.", "आपका Sahara खाता और उसकी जानकारी मिटा दी गई।") else t("Your Sahara data was deleted and the account is closed. The Sahara team removes the sign-in email itself; write to workwithdevit@gmail.com if you want that confirmed.", "आपकी Sahara जानकारी मिटा दी गई और खाता बंद हो गया। साइन-इन ईमेल Sahara टीम हटाती है; पक्का करना हो तो workwithdevit@gmail.com पर लिखें।"))
            cloud.forgetSession()
        } }, enabled = deleteUnderstood) { Text(t("Delete everything", "सब मिटाएँ")) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(t("Keep my account", "खाता रहने दें")) } })
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text(t("Sign out?", "साइन आउट करें?")) }, confirmButton = { TextButton(onClick = { signingOut = false; run { vm.stopRecording(); cloud.signOut() } }) { Text(t("Sign out", "साइन आउट करें")) } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text(t("Cancel", "रद्द करें")) } })
}

/** One access choice as a chip with a tick when on; at least 48dp tall. */
@Composable private fun AccessChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 14.sp) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp),
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = Rust))
}
