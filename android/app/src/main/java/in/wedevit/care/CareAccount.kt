@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.clearAndSetSemantics
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
        "doctor-walkthrough" -> DoctorDemo(onExit = { demoOpen = false; walkthroughPatient = null }, cloud = cloud, initialPatientId = walkthroughPatient, careModel = model, onCareAccount = { accountOpen = true }, skipLogin = true)
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
        if (accountOpen) AccountSheet(cloud, model, onDoctorWorkspace = { accountOpen = false; doctorOpen = true }, onDismiss = { accountOpen = false; if (cloud.session.value == null) doctorEntryPending = false }, banner = joinBanner, onInvite = root::offerInviteLink, onDeleted = { accountNotice = it; accountOpen = false })
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

@Composable fun AccountSheet(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, banner: String? = null, onInvite: ((String) -> Unit)? = null, onDeleted: (String) -> Unit = {}, onDoctorWorkspace: (() -> Unit)? = null) {
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
            }) { padding -> Box(Modifier.padding(padding).padding(top = 8.dp)) { AccountContent(cloud, vm, onDismiss, heading = "", onBusyChange = { busy = it }, banner = banner, onInvite = onInvite, onDeleted = onDeleted, onDoctorWorkspace = onDoctorWorkspace) } }
        }
    }
    else SaharaScreenSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        // The sheet covers the app header, so the language dropdown comes along to the sign-in form.
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) { CareLanguageMenu() }
        AccountContent(cloud, vm, onDismiss, onBusyChange = { busy = it }, banner = banner, onInvite = onInvite, onDeleted = onDeleted, onDoctorWorkspace = onDoctorWorkspace)
    }
}

/** The same genuine account form, reusable inside the welcome panel. */
private fun clipboardText(context: android.content.Context): String? = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

/** [onInvite] keeps a pasted invitation while signed out (it is offered after sign-in). [onDeleted] receives the message to show once the account is gone. */
@Composable fun AccountContent(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, heading: String? = null, onBusyChange: (Boolean) -> Unit = {}, banner: String? = null, onInvite: ((String) -> Unit)? = null, onDeleted: (String) -> Unit = {}, onDoctorWorkspace: (() -> Unit)? = null) {
    val session by cloud.session.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordShown by remember { mutableStateOf(false) }
    // The first-run flow already asked "Who are you?" and whether to create or sign in; it passes both here (see SignupIntent).
    val signupIntent = LocalSignupIntent.current
    var create by rememberSaveable { mutableStateOf(signupIntent?.create == true) }
    var signupRole by rememberSaveable { mutableStateOf(signupIntent?.role.orEmpty()) }
    var signupAgreed by rememberSaveable { mutableStateOf(false) }
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
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (session == null) AuthHeader(heading, create)
            else if (heading != "") Heading(heading ?: t("Your account", "आपका खाता"), 28)
            banner?.let { Surface(color = SaharaColors.PrimaryWash, shape = RoundedCornerShape(SaharaRadius.Lg), border = BorderStroke(1.dp, SaharaColors.Primary.copy(alpha = .4f))) { Text(it, Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Medium, color = DoctorCardInk) } }
            if (session == null) {
                error?.let { AuthErrorBanner(it) }
                // The clinic's invite link is the main way in (owner's point 1). Once an invite is waiting its "ready" card leads; otherwise
                // the invite option sits under the form, behind an "or" divider, as a bordered card.
                val invitePaste = {
                    val pasted = clipboardText(context)
                    if (CareInviteLink.parse(pasted) != null) { onInvite?.invoke(pasted.orEmpty()); error = null; if (signupIntent == null) create = true; true } else false
                }
                if (onInvite != null && banner != null) ClinicInviteFirst(ready = true, enabled = !busy, onPaste = invitePaste)
                if (create && signupIntent?.role.isNullOrBlank()) SignupRolePicker(signupRole) { signupRole = it }
                AuthCard {
                    AuthField(email, { email = it }, t("Email", "ईमेल"), Icons.Default.MailOutline, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    AuthField(password, { password = it }, t("Password", "पासवर्ड"), Icons.Default.Lock, KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        placeholder = if (create) t("At least 10 characters", "कम से कम 10 अक्षर") else null,
                        visual = if (passwordShown) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = { IconButton(onClick = { passwordShown = !passwordShown }) { Icon(if (passwordShown) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (passwordShown) t("Hide password", "पासवर्ड छिपाएँ") else t("Show password", "पासवर्ड दिखाएँ"), tint = SaharaColors.PrimaryPressed) } })
                    if (create) Surface(shape = RoundedCornerShape(SaharaRadius.Md), color = if (signupAgreed) SaharaColors.PrimaryWash else AuthFieldFill, border = BorderStroke(if (signupAgreed) 1.5.dp else 1.dp, if (signupAgreed) SaharaColors.Primary else AuthFieldBorder), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { signupAgreed = !signupAgreed }.padding(end = 12.dp).testTag("signup-agree"), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = signupAgreed, onCheckedChange = { signupAgreed = it })
                            Text(t("I am 18 or over and agree to the privacy policy. If the patient is a child, I am their parent or guardian.",
                                "मेरी उम्र 18 साल या उससे ज़्यादा है और मैं निजता नीति से सहमत हूँ। अगर मरीज़ बच्चा है, तो मैं उनका माता-पिता या अभिभावक हूँ।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardInk, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    AuthPrimaryButton(if (busy) t("Please wait…", "कृपया रुकें…") else if (create) t("Create account", "खाता बनाएँ") else t("Sign in", "साइन इन करें"), onClick = { authRun {
                        if (create) when (cloud.signUp(email, password)) {
                            CareSignUp.SIGNED_IN -> { password = ""; SignupRole.save(context, signupRole); SignupRole.agreed(context) }
                            CareSignUp.CONFIRMATION_SENT -> { SignupRole.save(context, signupRole); SignupRole.agreed(context); awaitingConfirmation = email.trim(); create = false; password = ""; notice = t("Check your email to confirm, then sign in.", "पुष्टि के लिए अपना ईमेल देखें, फिर साइन इन करें।") }
                            CareSignUp.ALREADY_REGISTERED -> { create = false; password = ""; error = careAuthMessage(CARE_ALREADY_REGISTERED, hindi) }
                        }
                        else { cloud.signIn(email, password); awaitingConfirmation = "" }
                    } }, enabled = email.contains('@') && password.isNotEmpty() && (!create || (signupRole.isNotEmpty() && signupAgreed)), busy = busy)
                    if (!create) TextButton(onClick = { authRun {
                        cloud.sendPasswordReset(email)
                        notice = t("Check your email for a reset link.", "पासवर्ड बदलने का लिंक अपने ईमेल में देखें।")
                    } }, enabled = !busy && email.contains('@') && CareEmailAvailability.ready, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.PrimaryPressed, disabledContentColor = AuthLinkDisabled)) { Text(t("Forgot password?", "पासवर्ड भूल गए?"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Lock, null, Modifier.padding(top = 2.dp).size(16.dp), tint = DoctorCardSub); Spacer(Modifier.width(8.dp))
                        Text(t("You stay signed in on this phone until you choose Sign out.", "जब तक आप साइन आउट न चुनें, आप इस फ़ोन पर साइन इन रहते हैं।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub, modifier = Modifier.testTag("account-stay-signed-in"))
                    }
                    if (awaitingConfirmation.isNotBlank()) SaharaSecondaryButton(t("Resend email", "ईमेल फिर भेजें"), onClick = { authRun { cloud.resendConfirmation(awaitingConfirmation); notice = t("Email sent again.", "ईमेल फिर भेजा गया।") } }, enabled = !busy, icon = Icons.Default.MailOutline, modifier = Modifier.fillMaxWidth())
                }
                if (onInvite != null && banner == null) {
                    AuthOrDivider()
                    ClinicInviteFirst(ready = false, enabled = !busy, onPaste = invitePaste)
                }
                if (create) TextButton(onClick = { create = !create; error = null; notice = null }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.PrimaryPressed, disabledContentColor = AuthLinkDisabled)) {
                    Text(t("Already have an account? Sign in", "पहले से खाता है? साइन इन करें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(t("New here?", "नए हैं?"), fontSize = 16.sp, color = DoctorCardSub)
                    TextButton(onClick = { create = !create; error = null; notice = null }, enabled = !busy && CareEmailAvailability.ready, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.PrimaryPressed, disabledContentColor = AuthLinkDisabled)) { Text(t("Create an account", "खाता बनाएँ"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
                AuthPrivacyStrip { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) }
            } else {
                // Who is signed in, with refresh beside it: one row instead of a line and a separate button.
                Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(28.dp), tint = Rust); Spacer(Modifier.width(12.dp))
                        Text(session!!.email, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.refresh(); onDismiss() }, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, t("Refresh", "ताज़ा करें"), tint = Rust) }
                    }
                }
                if (onDoctorWorkspace != null && SignupRole.get(context) == SignupRole.DOCTOR) Surface(shape = RoundedCornerShape(16.dp), color = SaharaTone.Indigo.wash, modifier = Modifier.fillMaxWidth().testTag("account-doctor-verify")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("Finish your doctor verification", "डॉक्टर सत्यापन पूरा करें"), fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                        Text(t("Add your medical council and registration number. The Sahara team checks it, usually within a day, before you can see patients.",
                            "अपनी मेडिकल काउंसिल और पंजीकरण संख्या जोड़ें। मरीज़ों को देखने से पहले Sahara टीम इसे जाँचती है, आम तौर पर एक दिन में।"), fontSize = 14.sp, color = DoctorCardInk)
                        SaharaPrimaryButton(t("Open doctor workspace", "डॉक्टर वर्कस्पेस खोलें"), onClick = { onDoctorWorkspace() }, tone = SaharaTone.Indigo, modifier = Modifier.fillMaxWidth())
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
                CareField(code, { code = CareInviteLink.parse(it)?.code ?: it.trim().take(4000) }, t("Invite code", "निमंत्रण कोड"), singleLine = true, voice = false)
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
        }
    // Results and errors come as a short dialog, signed in or out, so they are never missed below the fold or the keyboard.
    if (session != null) error?.let { SaharaDialog(it, onDismiss = { error = null }, tone = SaharaTone.Error) }
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
            runCatching { vm.forgetLocalAccountData() }; CareVoiceNotes.forgetAll(context, vm.storageNamespace); inviteStore.save(session!!.userId, emptyList())
            onDeleted(if (result.loginRemoved) t("Your Sahara account and its data were deleted.", "आपका Sahara खाता और उसकी जानकारी मिटा दी गई।") else t("Your data was deleted and the account closed.", "आपकी जानकारी मिटा दी गई और खाता बंद हो गया।"))
            cloud.forgetSession()
        } }, enabled = deleteUnderstood) { Text(t("Delete", "मिटाएँ")) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(t("Keep my account", "खाता रहने दें")) } })
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text(t("Sign out?", "साइन आउट करें?")) }, confirmButton = { TextButton(onClick = { signingOut = false; run { vm.stopRecording(); cloud.signOut() } }, modifier = Modifier.testTag("account-sign-out-confirm")) { Text(t("Sign out", "साइन आउट करें")) } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text(t("Cancel", "रद्द करें")) } })
}

/**
 * "Who are you?" when creating an account. Different from apps that ask for Aadhaar, date of birth and gender: Sahara asks only
 * what care needs. The choice is kept on this phone; a doctor is pointed to registration-number verification after sign-up.
 */
internal object SignupRole {
    const val PATIENT = "patient"; const val FAMILY = "family"; const val DOCTOR = "doctor"
    private const val PREFS = "sahara-signup-role"
    fun save(context: android.content.Context, role: String) { if (role.isNotBlank()) context.getSharedPreferences(PREFS, 0).edit().putString("role", role).apply() }
    fun get(context: android.content.Context): String = context.getSharedPreferences(PREFS, 0).getString("role", "").orEmpty()
    /** Which version of the sign-up agreement was accepted, and when (kept as a record of consent). */
    const val AGREEMENT_VERSION = "2026-11-21"
    fun agreed(context: android.content.Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, 0).edit().putString("agreement", AGREEMENT_VERSION).putLong("agreedAt", now).apply()
    }
}

/**
 * What the first-run flow (CareOnboarding.kt) already knows when it shows the account form: whether the person chose to create an
 * account or sign in, and the role they picked. With a role the form skips its own role picker and saves that role on sign-up.
 */
internal data class SignupIntent(val create: Boolean, val role: String)
internal val LocalSignupIntent = staticCompositionLocalOf<SignupIntent?> { null }

// Sign-in / sign-up look (patient-side, green wash): white card, 1dp visible border, soft shadow; filled green-grey fields; green pill buttons.
private val AuthFieldFill = androidx.compose.ui.graphics.Color(0xFFF3F7F4)
private val AuthFieldBorder = androidx.compose.ui.graphics.Color(0xFFCFE0D6)
/** Disabled link text that still reads (>= 4.5:1 on white). */
private val AuthLinkDisabled = androidx.compose.ui.graphics.Color(0xFF55796A)
private val AuthErrorInk = androidx.compose.ui.graphics.Color(0xFF8C1D18)

/** Title and one grey line above the sign-in or sign-up form. [heading] overrides the title (the doctor sign-in passes its own). */
@Composable private fun AuthHeader(heading: String?, create: Boolean) {
    val t = careText()
    val title = heading ?: if (create) t("Create your account", "अपना खाता बनाएँ") else t("Welcome back", "वापसी पर स्वागत है")
    val sub = when {
        heading != null -> t("For registered doctors and nurses. We check your registration number.", "पंजीकृत डॉक्टरों और नर्सों के लिए। हम आपकी पंजीकरण संख्या जाँचते हैं।")
        create -> t("Only an email and a password. No Aadhaar.", "सिर्फ़ ईमेल और पासवर्ड। आधार नहीं।")
        else -> t("Sign in to see your care.", "अपनी देखभाल देखने के लिए साइन इन करें।")
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.fillMaxWidth().a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = SaharaColors.Ink)
        Text(sub, Modifier.fillMaxWidth(), fontSize = 16.sp, lineHeight = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = SaharaColors.InkSub)
    }
}

/** A soft red bordered banner; the message is the same one the dialog used to show. Announced to screen readers. */
@Composable private fun AuthErrorBanner(message: String) {
    Surface(shape = RoundedCornerShape(SaharaRadius.Lg), color = SaharaColors.ErrorWash, border = BorderStroke(1.dp, SaharaColors.Error.copy(alpha = .55f)), modifier = Modifier.fillMaxWidth().announce().testTag("account-error")) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text("⚠️", fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics {}); Spacer(Modifier.width(10.dp))
            Text(message, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = AuthErrorInk)
        }
    }
}

/** The one white form card. */
@Composable private fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = SaharaColors.Surface, shadowElevation = 2.dp, border = BorderStroke(1.dp, SaharaColors.Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** A rounded, filled, single-line field: light green-grey fill, 1dp border, green border on focus, leading icon. */
@Composable private fun AuthField(value: String, onValueChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, keyboard: KeyboardOptions,
    placeholder: String? = null, visual: VisualTransformation = VisualTransformation.None, trailing: (@Composable () -> Unit)? = null) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, leadingIcon = { Icon(icon, null, tint = SaharaColors.PrimaryPressed) }, trailingIcon = trailing,
        placeholder = placeholder?.let { { Text(it, color = SaharaColors.InkFaint) } }, visualTransformation = visual, keyboardOptions = keyboard, shape = RoundedCornerShape(SaharaRadius.Md),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = AuthFieldFill, unfocusedContainerColor = AuthFieldFill, focusedBorderColor = SaharaColors.Primary, unfocusedBorderColor = AuthFieldBorder,
            focusedLabelColor = SaharaColors.PrimaryPressed, unfocusedLabelColor = SaharaColors.InkSub, cursorColor = SaharaColors.Primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
}

/** The green pill: 56 dp; disabled is the pale green wash with dark green text (not a grey slab). */
@Composable private fun AuthPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean, busy: Boolean) {
    Button(onClick = onClick, enabled = enabled && !busy, shape = androidx.compose.foundation.shape.CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = SaharaColors.Primary, contentColor = androidx.compose.ui.graphics.Color.White, disabledContainerColor = SaharaColors.PrimaryWash, disabledContentColor = SaharaColors.PrimaryPressed),
        border = if (enabled && !busy) null else BorderStroke(1.dp, SaharaColors.Primary.copy(alpha = .35f)),
        contentPadding = PaddingValues(horizontal = 24.dp), modifier = Modifier.fillMaxWidth().height(56.dp).saharaPressBounce()) {
        if (busy) { CircularProgressIndicator(Modifier.size(20.dp), color = SaharaColors.PrimaryPressed, strokeWidth = 2.5.dp); Spacer(Modifier.width(10.dp)) }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun AuthOrDivider() {
    val t = careText()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = AuthFieldBorder)
        Text(t("or", "या"), Modifier.padding(horizontal = 12.dp), fontSize = 15.sp, color = SaharaColors.InkSub)
        HorizontalDivider(Modifier.weight(1f), color = AuthFieldBorder)
    }
}

/** The calm privacy strip: green shield, the no-Aadhaar promise, and the Privacy policy link. */
@Composable private fun AuthPrivacyStrip(onPolicy: () -> Unit) {
    val t = careText()
    Surface(shape = RoundedCornerShape(SaharaRadius.Xl), color = SaharaColors.PrimaryWash, border = BorderStroke(1.dp, SaharaColors.Primary.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, Modifier.size(22.dp), tint = SaharaColors.PrimaryPressed); Spacer(Modifier.width(10.dp))
                Text(t("We never ask for Aadhaar, date of birth or gender to sign up. Only an email and a password.", "साइन अप के लिए हम कभी आधार, जन्मतिथि या लिंग नहीं माँगते। सिर्फ़ ईमेल और पासवर्ड।"),
                    Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, color = SaharaColors.PrimaryPressed)
            }
            TextButton(onClick = onPolicy, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.PrimaryPressed)) {
                Icon(Icons.Default.Policy, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Privacy policy", "निजता नीति"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable private fun SignupRolePicker(role: String, onRole: (String) -> Unit) { val t = careText()
    Text(t("Who are you?", "आप कौन हैं?"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
    listOf(
        Triple(SignupRole.FAMILY, t("I'm caring for someone", "मैं किसी की देखभाल कर रहा/रही हूँ"), t("Family member or carer", "परिवार का सदस्य या देखभाल करने वाले")),
        Triple(SignupRole.PATIENT, t("I'm the patient", "मैं मरीज़ हूँ"), t("Keep your own care in one place", "अपनी देखभाल एक जगह रखें")),
        Triple(SignupRole.DOCTOR, t("I'm a doctor or nurse", "मैं डॉक्टर या नर्स हूँ"), t("We check your registration number first", "पहले हम आपकी पंजीकरण संख्या जाँचते हैं")),
    ).forEach { (value, title, sub) ->
        val chosen = role == value
        Surface(shape = RoundedCornerShape(SaharaRadius.Lg), color = if (chosen) SaharaColors.PrimaryWash else SaharaColors.Surface, border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) SaharaColors.Primary else SaharaColors.Line),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onRole(value) }.testTag("signup-role-$value")) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, color = DoctorCardInk); Text(sub, fontSize = 13.sp, color = DoctorCardSub) }
                if (chosen) Icon(Icons.Default.Check, null, tint = SaharaColors.PrimaryPressed)
            }
        }
    }
    Text(t("We never ask for Aadhaar, date of birth or gender to sign up. Only an email and a password.", "साइन अप के लिए हम कभी आधार, जन्मतिथि या लिंग नहीं माँगते। सिर्फ़ ईमेल और पासवर्ड।"),
        fontSize = 13.sp, color = DoctorCardSub)
}

/**
 * "I have an invite link from my clinic". The nurse or doctor makes the invite; the family taps the link in WhatsApp or SMS once
 * (Sahara opens by itself), or copies it and taps this card. [ready] is true once an invite is waiting. [onPaste] reads a copied
 * link and returns whether it was an invite. Shown as a white card with a teal border, not a heavy filled box.
 */
@Composable private fun ClinicInviteFirst(ready: Boolean, enabled: Boolean, onPaste: () -> Boolean) {
    val t = careText(); var help by rememberSaveable { mutableStateOf(false) }
    val tone = SaharaTone.Teal
    Surface(shape = RoundedCornerShape(SaharaRadius.Xl), color = if (ready) SaharaColors.PrimaryWash else SaharaColors.Surface, border = BorderStroke(1.5.dp, tone.solid.copy(alpha = .7f)), shadowElevation = if (ready) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth().saharaAppear().testTag("account-clinic-invite")) {
        Column(Modifier.saharaContentSize()) {
            androidx.compose.animation.AnimatedContent(ready, transitionSpec = { SaharaMotion.crossfade() }, label = "clinic-invite") { has ->
                if (has) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(36.dp), tint = SaharaTone.Success.solid); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("Your clinic's invite is ready", "आपके क्लिनिक का निमंत्रण तैयार है"), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = DoctorCardInk)
                        Text(t("Make your account once below. After that you stay signed in on this phone.", "नीचे एक बार अपना खाता बनाएँ। उसके बाद आप इस फ़ोन पर साइन इन रहते हैं।"), fontSize = 16.sp, color = DoctorCardInk)
                    }
                } else Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button) { help = !onPaste() }.padding(16.dp).testTag("account-clinic-invite-button"), verticalAlignment = Alignment.CenterVertically) {
                        SaharaIconChip(Icons.Default.Link, tone, size = 44); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t("I have an invite link from my clinic", "मेरे पास क्लिनिक से निमंत्रण लिंक है"), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = tone.ink)
                            if (!help) Text(t("Your nurse or doctor sends it on WhatsApp or SMS.", "आपकी नर्स या डॉक्टर इसे WhatsApp या SMS पर भेजते हैं।"), fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = tone.ink)
                    }
                    androidx.compose.animation.AnimatedVisibility(help, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                        Text(t("Open the message from your nurse or doctor and tap the link once. Sahara opens by itself. Or copy the link, come back and tap this button again.",
                            "अपनी नर्स या डॉक्टर का संदेश खोलें और लिंक पर एक बार टैप करें। Sahara अपने आप खुल जाएगा। या लिंक कॉपी करें, वापस आएँ और यह बटन फिर दबाएँ।"),
                            fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).announce().testTag("account-clinic-invite-help"))
                    }
                }
            }
        }
    }
}

/** One access choice as a chip with a tick when on; at least 48dp tall. */
@Composable private fun AccessChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 14.sp) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp),
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = Rust))
}
