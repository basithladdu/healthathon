@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
        if (invite == null) { linkNotice.value = careTr(CareLanguageState.shown, "Invite link not valid.", "निमंत्रण लिंक सही नहीं है।"); return }
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
        val joinBanner = pending?.let { rt("Sign in to join ${it.possessive} care", "${it.name.ifBlank { "इस व्यक्ति" }} की देखभाल से जुड़ने के लिए साइन इन करें") }
        val mode = when { patientWalkthroughUser != null -> "patient-walkthrough"; demoOpen -> "doctor-walkthrough"; doctorOpen && session != null -> "doctor"; else -> "care" }
        // Entering a walkthrough or workspace moves forward; leaving it moves back to the welcome/care app.
        // One language choice for every screen, the doctor workspace and walkthroughs included.
        CompositionLocalProvider(LocalCareLanguage provides rootLanguage) { CareLanguageDirection(rootLanguage) {
        SaharaScreenHost(mode, Modifier.fillMaxSize().background(Cream), label = "Sahara root", direction = { _, to -> if (to == "care") SaharaMotion.Direction.Back else SaharaMotion.Direction.Forward }) { shown -> when (shown) {
        "patient-walkthrough" -> patientWalkthroughUser?.let { PatientWalkthroughHost(it, onExit = { patientWalkthroughUser = null }, onSwitch = { patientWalkthroughUser = it }) }
        "doctor-walkthrough" -> DoctorDemo(onExit = { demoOpen = false; walkthroughPatient = null }, cloud = cloud, initialPatientId = walkthroughPatient, careModel = model, onCareAccount = { accountOpen = true })
        "doctor" -> ConnectedDoctorWorkspace(cloud, model, onExit = { doctorOpen = false }, onAccount = { accountOpen = true }, onWalkthrough = { walkthroughPatient = it; demoOpen = true })
        else -> CareApp(model, onAccount = { accountOpen = true }, onDoctorDemo = {
            if (model.recording.value) model.message.value = rt("Stop recording first.", "पहले रिकॉर्डिंग रोकें।")
            else { walkthroughPatient = null; demoOpen = true }
        }, onPatientWalkthrough = { if (model.recording.value) model.message.value = rt("Stop recording first.", "पहले रिकॉर्डिंग रोकें।") else patientWalkthroughUser = it },
            onDoctorWorkspace = { doctorEntryPending = true; if (session != null) { doctorOpen = true; doctorEntryPending = false } else accountOpen = true },
            welcomeAccountContent = { close, onBusy -> AccountContent(cloud, model, close, onBusyChange = onBusy, banner = joinBanner, onInvite = root::offerInviteLink, onDeleted = { accountNotice = it }) },
            welcomeDoctorContent = { close, onBusy ->
                if (session == null) AccountContent(cloud, model, close, heading = rt("Doctor sign in", "डॉक्टर साइन इन"), onBusyChange = onBusy, onDeleted = { accountNotice = it })
                else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Heading(rt("I'm a doctor", "मैं डॉक्टर हूँ"), 26)
                    Text(session!!.email, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    SaharaPrimaryButton(rt("Open doctor workspace", "डॉक्टर वर्कस्पेस खोलें"), onClick = { doctorOpen = true; doctorEntryPending = false }, icon = Icons.Default.MedicalServices, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
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
    val t = careText()
    SaharaScreenSheet(onDismissRequest={if(!busy)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Cream) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { CareLanguageMenu() }
            Heading(t("Sign in", "साइन इन करें"),26)
            if(session!=null) { Text(t("Signed in", "साइन इन हो गया"),color=Rust);TextButton(onClick=onDismiss) { Text(t("Done", "हो गया")) } }
            else {
                OutlinedTextField(email,{email=it},singleLine=true,label={Text(t("Email", "ईमेल"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},singleLine=true,label={Text(t("Password", "पासवर्ड"))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
                Button(onClick={if(!busy){busy=true;error=null;scope.launch {try {cloud.signIn(email,password);password="";onDismiss()}catch(e:Exception){if(e is CancellationException)throw e;error=t("Wrong email or password.", "ईमेल या पासवर्ड ग़लत है।")}finally{busy=false}}}},enabled=!busy && email.contains('@') && password.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(busy) t("Please wait…", "कृपया रुकें…") else t("Sign in", "साइन इन करें")) }
            }
            error?.let { SaharaDialog(it, onDismiss = { error = null }, tone = SaharaTone.Error) }
            TextButton(onClick=onDismiss,enabled=!busy) {Text(t("Close", "बंद करें"))}
        }
    }
}

@Composable fun AccountSheet(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, banner: String? = null, onInvite: ((String) -> Unit)? = null, onDeleted: (String) -> Unit = {}) {
    val t = careText()
    var busy by remember { mutableStateOf(false) }
    val session by cloud.session.collectAsStateWithLifecycle()
    // Signed in, Account is an ordinary Android page: a top app bar with back and a title, sliding in over the app.
    if (session != null) androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
        androidx.compose.animation.AnimatedVisibility(shown, enter = SaharaMotion.enter + androidx.compose.animation.slideInHorizontally(SaharaMotion.tween(SaharaMotion.ENTER_MS, easing = SaharaMotion.Emphasized)) { it / 12 }) {
            Scaffold(containerColor = Cream, modifier = Modifier.fillMaxSize().testTag("account-page"), topBar = {
                TopAppBar(title = { Text(t("Account", "खाता"), fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { if (!busy) onDismiss() }, enabled = !busy, modifier = Modifier.testTag("account-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) } },
                    actions = { CareLanguageMenu(compact = true) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream))
            }) { padding -> Box(Modifier.padding(padding).padding(top = 8.dp)) { AccountContent(cloud, vm, onDismiss, heading = "", onBusyChange = { busy = it }, banner = banner, onInvite = onInvite, onDeleted = onDeleted) } }
        }
    }
    else SaharaScreenSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
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
    var passwordShown by remember { mutableStateOf(false) }
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
            if (heading != "") Heading(heading ?: if (session == null) if (create) t("Create an account", "खाता बनाएँ") else t("Sign in", "साइन इन करें") else t("Your account", "आपका खाता"), if (session == null) 24 else 28)
            banner?.let { Surface(color = Mint, shape = RoundedCornerShape(14.dp)) { Text(it, Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Medium) } }
            if (session == null) {
                OutlinedTextField(email, { email = it }, label = { Text(t("Email", "ईमेल")) }, singleLine = true, leadingIcon = { Icon(Icons.Default.MailOutline, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), shape = RoundedCornerShape(SaharaRadius.Sm), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(t("Password", "पासवर्ड")) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = { IconButton(onClick = { passwordShown = !passwordShown }) { Icon(if (passwordShown) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (passwordShown) t("Hide password", "पासवर्ड छिपाएँ") else t("Show password", "पासवर्ड दिखाएँ")) } },
                    visualTransformation = if (passwordShown) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), shape = RoundedCornerShape(SaharaRadius.Sm), modifier = Modifier.fillMaxWidth(),
                    placeholder = if (create) { { Text(t("At least 10 characters", "कम से कम 10 अक्षर")) } } else null)
                SaharaPrimaryButton(if (busy) t("Please wait…", "कृपया रुकें…") else if (create) t("Create account", "खाता बनाएँ") else t("Sign in", "साइन इन करें"), onClick = { authRun {
                    if (create) when (cloud.signUp(email, password)) {
                        CareSignUp.SIGNED_IN -> password = ""
                        CareSignUp.CONFIRMATION_SENT -> { awaitingConfirmation = email.trim(); create = false; password = ""; notice = t("Check your email to confirm, then sign in.", "पुष्टि के लिए अपना ईमेल देखें, फिर साइन इन करें।") }
                        CareSignUp.ALREADY_REGISTERED -> { create = false; password = ""; error = careAuthMessage(CARE_ALREADY_REGISTERED, hindi) }
                    }
                    else { cloud.signIn(email, password); awaitingConfirmation = "" }
                } },
                    enabled = email.contains('@') && password.isNotEmpty(), busy = busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
                if (awaitingConfirmation.isNotBlank()) SaharaSecondaryButton(t("Resend email", "ईमेल फिर भेजें"), onClick = { authRun { cloud.resendConfirmation(awaitingConfirmation); notice = t("Email sent again.", "ईमेल फिर भेजा गया।") } }, enabled = !busy, icon = Icons.Default.MailOutline, modifier = Modifier.fillMaxWidth())
                // Secondary links side by side, so the form stays short.
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!create) TextButton(onClick = { authRun {
                        cloud.sendPasswordReset(email)
                        notice = t("Check your email for a reset link.", "पासवर्ड बदलने का लिंक अपने ईमेल में देखें।")
                    } }, enabled = !busy && email.contains('@') && CareEmailAvailability.ready, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Forgot password?", "पासवर्ड भूल गए?")) }
                    TextButton(onClick = { create = !create; error = null; notice = null }, enabled = !busy && (create || CareEmailAvailability.ready), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (create) t("Already have an account? Sign in", "पहले से खाता है? साइन इन करें") else t("Create an account", "खाता बनाएँ"), fontWeight = FontWeight.SemiBold) }
                }
                if (onInvite != null) SaharaSecondaryButton(t("Paste invite", "निमंत्रण चिपकाएँ"), onClick = { val pasted = clipboardText(context); if (CareInviteLink.parse(pasted) != null) { onInvite(pasted.orEmpty()); error = null; notice = t("Invite saved. Sign in to join.", "निमंत्रण रख लिया। जुड़ने के लिए साइन इन करें।") } else { notice = null; error = t("No invite copied.", "कोई निमंत्रण कॉपी नहीं हुआ।") } }, enabled = !busy, icon = Icons.Default.ContentPaste, modifier = Modifier.fillMaxWidth())
            } else {
                // Who is signed in, with refresh beside it: one row instead of a line and a separate button.
                Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(28.dp), tint = Rust); Spacer(Modifier.width(12.dp))
                        Text(session!!.email, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.refresh(); onDismiss() }, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, t("Refresh", "ताज़ा करें"), tint = Rust) }
                    }
                }
                if (owner && selected.isNotBlank()) {
                    // Who can see this care: the one main action (invite family) first, then one register of people and invitations.
                    Row(Modifier.fillMaxWidth().testTag("account-care-team"), verticalAlignment = Alignment.CenterVertically) {
                        SaharaSectionHeader(t("Family and care team", "परिवार और देखभाल टीम"), Modifier.weight(1f), tone = SaharaTone.Leaf, icon = Icons.Default.Groups)
                        if (membersLoaded) FamilyPill(t("${members.size} joined", "${members.size} जुड़े"), if (members.isEmpty()) FamilyTone.NEUTRAL else FamilyTone.GOOD)
                    }
                    SaharaPrimaryButton(t("Invite family", "परिवार को बुलाएँ"), onClick = { addFamily = true }, enabled = !busy, tone = SaharaTone.Leaf, icon = Icons.Default.PersonAdd, modifier = Modifier.fillMaxWidth().testTag("account-invite-family"))
                    SaharaSecondaryButton(t("Invite doctor", "डॉक्टर को बुलाएँ"), onClick = { inviteDoctor = true; invite = "" }, enabled = !busy, tone = SaharaTone.Indigo, icon = Icons.Default.MedicalServices, modifier = Modifier.fillMaxWidth().testTag("account-invite-doctor"))
                    if (membersLoaded || invites.isNotEmpty()) SaharaRegister {
                        if (membersLoaded && members.isEmpty()) Text(t("Nobody has joined yet.", "अभी तक कोई नहीं जुड़ा।"), Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 15.sp, color = DoctorCardSub)
                        members.forEachIndexed { index, member ->
                            val detail = memberDetails?.firstOrNull { it.userId == member.userId }; val now = System.currentTimeMillis()
                            if (index > 0) HorizontalDivider(color = DoctorHairline)
                            var menu by remember(member.userId) { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).saharaAppear(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(member.email, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    // Access level as a badge; dates on one quiet line.
                                    FamilyPill(if (member.canEdit) t("Can make changes", "बदलाव कर सकते हैं") else t("Can read", "सिर्फ़ पढ़ सकते हैं"), if (member.canEdit) FamilyTone.GOOD else FamilyTone.INFO)
                                    listOfNotNull(detail?.joinedAt?.takeIf { it > 0 }?.let { t("joined ${day(it)}", "${day(it)} को जुड़े") },
                                        detail?.expiresAt?.takeIf { it > 0 }?.let { if (it > now) t("access until ${day(it)}", "${day(it)} तक पहुँच") else t("access ended ${day(it)}", "${day(it)} को पहुँच ख़त्म") }).joinToString(" · ").takeIf(String::isNotBlank)?.let { Text(it, fontSize = 14.sp, color = DoctorCardSub) }
                                }
                                // Every change for this person sits in one menu: one button per row instead of three.
                                Box {
                                    IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.size(48.dp).testTag("account-member-menu")) { Icon(Icons.Default.MoreVert, t("Change access for ${member.email}", "${member.email} की पहुँच बदलें"), tint = DoctorCardInk) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Paper) {
                                        DropdownMenuItem(text = { Text(if (member.canEdit) t("Make view only", "सिर्फ़ देखने दें") else t("Allow editing", "बदलाव करने दें")) }, leadingIcon = { Icon(if (member.canEdit) Icons.Default.Visibility else Icons.Default.Edit, null) }, modifier = Modifier.heightIn(min = 48.dp), onClick = { menu = false; val person = selected; run {
                                            try { cloud.setMemberEdit(person, member.userId, !member.canEdit) } catch (e: CareServerFeatureMissing) { throw IllegalStateException(t("Not available yet.", "अभी उपलब्ध नहीं है।"), e) }
                                            loadSharing(person); if (vm.selected.value == person) notice = if (member.canEdit) t("${member.email} can now only read.", "${member.email} अब सिर्फ़ देख सकते हैं।") else t("${member.email} can now add and change records.", "${member.email} अब रिकॉर्ड जोड़ और बदल सकते हैं।")
                                        } })
                                        if (member.careTeamPermissionKnown) DropdownMenuItem(text = { Text(t("Share care-team contacts", "देखभाल टीम के नंबर भी साझा करें")) }, modifier = Modifier.heightIn(min = 48.dp),
                                            leadingIcon = { Checkbox(checked = member.careTeamRead, onCheckedChange = null) }, onClick = { menu = false; val enabled = !member.careTeamRead; val person = selected; val account = session!!.userId; run { require(vm.selected.value == person && cloud.session.value?.userId == account && vm.isOwner.value); cloud.setCareTeamSharing(person, member.userId, enabled); if (vm.selected.value == person && cloud.session.value?.userId == account) loadSharing(person) } })
                                        DropdownMenuItem(text = { Text(t("Stop sharing", "साझा करना बंद करें"), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.heightIn(min = 48.dp), onClick = { menu = false; removing = member })
                                    }
                                }
                            }
                        }
                        if (invites.isNotEmpty()) { if (membersLoaded) HorizontalDivider(color = DoctorHairline); val now = System.currentTimeMillis(); invites.forEach { e ->
                            val status = CareInviteLog.status(e, openInvites, memberDetails, now)
                            // Register row: who, a status dot (joined / waiting / closed), and Cancel while it is still open.
                            val tone = when (status.state) { CareInviteState.JOINED -> FamilyTone.GOOD; CareInviteState.OPEN, CareInviteState.UNTRACKED, CareInviteState.UNKNOWN -> FamilyTone.WAIT; else -> FamilyTone.NEUTRAL }
                            val full = CareInviteLog.label(e, status, now, hindi, members.firstOrNull { it.userId == status.memberId }?.email.orEmpty())
                            val who = full.substringBefore(" · ").takeIf { e.name.isNotBlank() || e.relation.isNotBlank() }
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 4.dp).saharaAppear(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tone.ink)); Spacer(Modifier.width(10.dp))
                                Text(full, Modifier.weight(1f).padding(vertical = 4.dp), fontSize = 15.sp, fontWeight = if (who != null) FontWeight.Medium else FontWeight.Normal, color = DoctorCardInk)
                                if (status.state == CareInviteState.OPEN) TextButton(onClick = { val person = selected; val acct = session!!.userId; run { cloud.cancelInvite(e.inviteId); inviteStore.save(acct, inviteStore.all(acct).filterNot { it.inviteId == e.inviteId }); inviteTick++; if (vm.selected.value == person) notice = t("Invitation cancelled.", "निमंत्रण रद्द हुआ।") } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), color = RustText) }
                            }
                        } }
                    }
                    CareContactManager(vm, selected)
                }
                SaharaSectionHeader(t("Join someone's care", "किसी की देखभाल से जुड़ें"), tone = SaharaTone.Teal, icon = Icons.Default.GroupAdd)
                CareField(code, { code = CareInviteLink.parse(it)?.code ?: it.trim().take(4000) }, t("Invite code", "निमंत्रण कोड"), singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaharaSecondaryButton(t("Paste invite", "निमंत्रण चिपकाएँ"), onClick = { val pasted = clipboardText(context); val invite = CareInviteLink.parse(pasted); if (invite != null) { code = invite.code; error = null; notice = null } else { notice = null; error = t("No invite copied.", "कोई निमंत्रण कॉपी नहीं हुआ।") } }, enabled = !busy, icon = Icons.Default.ContentPaste, tone = SaharaTone.Teal, modifier = Modifier.weight(1f))
                    SaharaPrimaryButton(t("Join", "जुड़ें"), onClick = { run { val before = vm.people.value.map { it.id }.toSet(); try { cloud.join(code) } catch (e: Exception) { if (e is CancellationException) throw e; throw IllegalArgumentException(careJoinFailureMessage(e.message, hindi), e) }; code = ""; val added = vm.joinedRefresh(before); notice = added?.name?.let { t("You can now see $it's care.", "अब आप $it की देखभाल देख सकते हैं।") } ?: t("You can now see their care.", "अब आप उनकी देखभाल देख सकते हैं।") } }, enabled = !busy && code.trim().length == 48, tone = SaharaTone.Teal, modifier = Modifier.weight(1f))
                }
                // Account housekeeping: one plain list at the bottom, sign out first, the irreversible step last.
                SaharaSectionHeader(t("Your account", "आपका खाता"), tone = SaharaTone.Rust, icon = Icons.Default.ManageAccounts)
                SaharaRegister {
                    SaharaListRow(t("Privacy policy", "निजता नीति"), icon = Icons.Default.Policy, tone = SaharaTone.Indigo, onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) })
                    HorizontalDivider(color = DoctorHairline)
                    SaharaListRow(t("Sign out", "साइन आउट करें"), Modifier.testTag("account-sign-out"), icon = Icons.AutoMirrored.Filled.Logout, tone = SaharaTone.Rust, onClick = { if (!busy) signingOut = true })
                    HorizontalDivider(color = DoctorHairline)
                    SaharaListRow(t("Delete account", "खाता मिटाएँ"), icon = Icons.Default.DeleteOutline, tone = SaharaTone.Error, onClick = { if (!busy) { deleteUnderstood = false; deleting = true } })
                    if (deletionByEmail) { HorizontalDivider(color = DoctorHairline); SaharaListRow(t("Request deletion by email", "ईमेल से मिटाने का अनुरोध भेजें"), icon = Icons.Default.MailOutline, tone = SaharaTone.Error, onClick = { if (!busy) open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.delete_account_url)))) }) }
                }
            }
            if (session == null) TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Privacy policy", "निजता नीति"), color = DoctorCardSub) }
        }
    // Results and errors come as a short dialog, signed in or out, so they are never missed below the fold or the keyboard.
    error?.let { SaharaDialog(it, onDismiss = { error = null }, tone = SaharaTone.Error) }
    if (error == null) notice?.let { SaharaDialog(it, onDismiss = { notice = null }, tone = if (session == null) SaharaTone.Info else SaharaTone.Success) }
    if (inviteDoctor && session != null && owner && selected.isNotBlank()) SaharaScreenSheet(onDismissRequest = { if (!busy) { inviteDoctor = false; invite = "" } }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        // Invite a doctor: what they may see and for how long, then one button; the link to share replaces the button when it is made.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).saharaScreenEnter().testTag("account-doctor-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaharaIconChip(Icons.Default.MedicalServices, SaharaTone.Indigo); Spacer(Modifier.width(12.dp))
                Text(t("Invite doctor", "डॉक्टर को बुलाएँ"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                IconButton(onClick = { inviteDoctor = false; invite = "" }, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, t("Close", "बंद करें"), tint = DoctorCardSub) }
            }
            PatientIdForDoctor(selected)
            Text(t("Access", "पहुँच"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccessChip(doctorNotes, t("Notes", "नोट")) { doctorNotes = !doctorNotes; invite = "" }
                AccessChip(doctorCalendar, t("Medicines & calendar", "दवाइयाँ और कैलेंडर")) { doctorCalendar = !doctorCalendar; invite = "" }
                AccessChip(doctorReports, t("Reports", "रिपोर्ट")) { doctorReports = !doctorReports; invite = "" }
                AccessChip(doctorCosts, t("Costs", "ख़र्च")) { doctorCosts = !doctorCosts; invite = "" }
            }
            CheckLine(doctorEdit, { doctorEdit = it; invite = "" }, t("Can edit", "बदलाव कर सकें"))
            Text(t("Days", "दिन"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf(1, 7, 30, 90).forEach { d -> AccessChip(doctorDays == d, t("$d days", "$d दिन")) { doctorDays = d; invite = "" } } }
            if (invite.isEmpty()) SaharaPrimaryButton(t("Create invite", "निमंत्रण बनाएँ"), onClick = { val person = selected; val account = session!!.userId; val doctor = inviteDoctor; run { val before = cloud.openInvites(person); val code = if (doctor) cloud.inviteAccess(person, "clinician", doctorEdit, mapOf("careNote" to doctorNotes, "calendar" to doctorCalendar, "reports" to doctorReports, "costs" to doctorCosts), doctorDays, expectedAccountId = account) else cloud.invite(person, allowChanges, allowCareTeam); if (vm.selected.value == person && cloud.session.value?.userId == account && vm.isOwner.value) { invite = code; inviteStore.logCreated(cloud, account, person, "", "", if (doctor) "clinician" else "family", before); inviteTick++ } } },
                enabled = !busy && (!inviteDoctor || doctorNotes || doctorCalendar || doctorReports || doctorCosts), busy = busy, tone = SaharaTone.Indigo, icon = Icons.Default.Link, modifier = Modifier.fillMaxWidth().testTag("account-doctor-create"))
            else Column(Modifier.saharaAppear(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FamilyPill(t("Expires in 3 days", "3 दिन में बंद होगा"), FamilyTone.GOOD)
                SaharaPrimaryButton(t("Share invite", "निमंत्रण भेजें"), onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, CareInviteLink.shareText(people.firstOrNull { it.id == selected }?.name.orEmpty(), invite)), t("Share", "भेजें"))) }, tone = SaharaTone.Indigo, icon = Icons.Default.Share, modifier = Modifier.fillMaxWidth().testTag("account-doctor-share"))
                androidx.compose.foundation.text.selection.SelectionContainer { Text(invite, style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    if (addFamily) AddFamilySheet(vm, onDismiss = { addFamily = false }, onSignIn = {}, onInvited = { inviteTick++ })
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(t("Stop sharing with this person?", "इस व्यक्ति के साथ साझा करना बंद करें?")) }, confirmButton = { TextButton(onClick = { removing = null; val person = selected; run { cloud.removeMember(person, member.userId); loadSharing(person); if (vm.selected.value == person) notice = t("${member.email} no longer has access.", "${member.email} की पहुँच हटा दी गई।") } }) { Text(t("Stop sharing", "साझा करना बंद करें")) } }, dismissButton = { TextButton(onClick = { removing = null }) { Text(t("Keep sharing", "साझा करते रहें")) } }) }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(t("Delete account?", "खाता मिटाएँ?")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Your account and the care records you set up will be deleted for everyone.", "आपका खाता और आपके बनाए देखभाल रिकॉर्ड सबके लिए मिट जाएँगे।"))
            CheckLine(deleteUnderstood, { deleteUnderstood = it }, t("I understand this can't be undone", "मैं समझता/समझती हूँ कि यह वापस नहीं होगा"))
        } },
        confirmButton = { TextButton(onClick = { deleting = false; run {
            vm.stopRecording()
            val result = try { cloud.deleteMyAccount() } catch (e: CareServerFeatureMissing) { deletionByEmail = true; throw IllegalStateException(t("Couldn't delete. Request deletion by email below.", "नहीं मिटा सके। नीचे ईमेल से अनुरोध भेजें।"), e) }
            runCatching { vm.forgetLocalAccountData() }; inviteStore.save(session!!.userId, emptyList())
            onDeleted(if (result.loginRemoved) t("Your Sahara account and its data were deleted.", "आपका Sahara खाता और उसकी जानकारी मिटा दी गई।") else t("Your data was deleted and the account closed.", "आपकी जानकारी मिटा दी गई और खाता बंद हो गया।"))
            cloud.forgetSession()
        } }, enabled = deleteUnderstood) { Text(t("Delete", "मिटाएँ")) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(t("Keep my account", "खाता रहने दें")) } })
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text(t("Sign out?", "साइन आउट करें?")) }, confirmButton = { TextButton(onClick = { signingOut = false; run { vm.stopRecording(); cloud.signOut() } }, modifier = Modifier.testTag("account-sign-out-confirm")) { Text(t("Sign out", "साइन आउट करें")) } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text(t("Cancel", "रद्द करें")) } })
}

/** One access choice as a chip with a tick when on; at least 48dp tall. */
@Composable private fun AccessChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 14.sp) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp),
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = Rust))
}
