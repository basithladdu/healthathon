package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * First run (22 Nov redesign). One thing per screen instead of one big form:
 *   Hello (logo, tagline, one line about Sahara, Get started) -> Who are you? -> Account (create / sign in / keep on this phone) -> Name -> Age (optional)
 *   -> About the illness (optional) -> Doctor now (optional) -> Treatment now (optional) -> You're all set (Dr Shirin, 23 Nov:
 *   the care details come before the home page). The doctor card goes to the doctor sign-in and the doctor practice, as before.
 * The account comes before the profile on purpose: a person added on this phone stays in the guest records, so someone who wants
 * an account makes it first and the profile is then saved into the account. Progress (intro seen, role) is kept on the phone,
 * because signing in recreates the care screens and the flow then resumes at the name question.
 * Steps move on Sahara's shared axis ([SaharaMotion.transform]); nothing moves when animations or Battery Saver are off.
 */

/** The steps; [panel] is the old welcome panel name each one replaces, still reported to CareRoot (it watches for "doctor"). */
internal enum class OnboardingStep(val panel: String) {
    Hello(""), Role(""), Account("family"), SignIn("account"), SignUp("account"), Doctor("doctor"), Name("person"), Age("person"), Illness("person"),
    /** The doctor the patient sees now (not the doctor sign-in, which is [Doctor]). */
    CareDoctor("person"), Treatment("person"), Finish("person")
}

internal object OnboardingFlow {
    /** The patient and family path the thin progress bar measures. */
    private val path = listOf(OnboardingStep.Role, OnboardingStep.Account, OnboardingStep.Name, OnboardingStep.Age, OnboardingStep.Illness,
        OnboardingStep.CareDoctor, OnboardingStep.Treatment, OnboardingStep.Finish)

    /** 0..1 along [path]; null where no bar is shown (Hello and the doctor sign-in). Sign-in and sign-up sit at the account step. */
    fun progress(step: OnboardingStep): Float? = when (step) {
        OnboardingStep.Hello, OnboardingStep.Doctor -> null
        OnboardingStep.SignIn, OnboardingStep.SignUp -> (path.indexOf(OnboardingStep.Account) + 1f) / path.size
        else -> (path.indexOf(step) + 1f) / path.size
    }

    /** Where the flow opens: signed in as patient or family it resumes at the name; otherwise on the hello screen. */
    fun start(signedIn: Boolean, role: String): OnboardingStep = when {
        signedIn && (role == SignupRole.PATIENT || role == SignupRole.FAMILY) -> OnboardingStep.Name
        else -> OnboardingStep.Hello
    }

    /** Where "Get started" on the hello screen goes: always the role cards (there are no intro pages). */
    fun afterHello(): OnboardingStep = OnboardingStep.Role

    /** The step Back returns to, or null when Back should leave the app. [helloShown]: the hello screen was shown in this visit. */
    fun back(step: OnboardingStep, signedIn: Boolean, helloShown: Boolean = false): OnboardingStep? = when (step) {
        OnboardingStep.Hello -> null
        OnboardingStep.Role -> if (helloShown) OnboardingStep.Hello else null
        OnboardingStep.Account, OnboardingStep.Doctor -> OnboardingStep.Role
        OnboardingStep.SignIn, OnboardingStep.SignUp -> if (signedIn) OnboardingStep.Name else OnboardingStep.Account
        OnboardingStep.Name -> if (signedIn) OnboardingStep.Role else OnboardingStep.Account
        OnboardingStep.Age -> OnboardingStep.Name
        OnboardingStep.Illness -> OnboardingStep.Age
        OnboardingStep.CareDoctor -> OnboardingStep.Illness
        OnboardingStep.Treatment -> OnboardingStep.CareDoctor
        OnboardingStep.Finish -> OnboardingStep.Treatment
    }

    /** The optional profile details, or null when every one was skipped (then only the name is saved, as before). */
    fun profile(ageText: String, illness: String, illnessSince: String = "", doctorName: String = "", doctorPlace: String = "", doctorPhone: String = "",
        treatments: List<String> = emptyList(), treatmentNote: String = "", doctorLandline: String = ""): CarePatientProfile? {
        val p = CarePatientProfile(age = CarePatientProfileRules.age(ageText).first, illness = illness.trim(), illnessSince = illnessSince.trim(),
            doctorName = doctorName.trim(), doctorPlace = doctorPlace.trim(), doctorPhone = doctorPhone.trim(),
            treatments = treatments.filter { it in CarePatientProfile.TREATMENTS }.distinct(), treatmentNote = treatmentNote.trim(), doctorLandline = doctorLandline.trim())
        return if (p == CarePatientProfile()) null else p
    }

    /** The saved treatment codes travel as one comma-joined string, so they survive rotation in a saveable state. */
    fun codes(joined: String): List<String> = joined.split(',').filter { it in CarePatientProfile.TREATMENTS }
}

/** First-run progress on this phone (not per account, since it is read before anyone signs in). */
internal class CareOnboardingPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sahara-onboarding", 0)
    var introSeen: Boolean get() = prefs.getBoolean("intro-seen", false); set(value) { prefs.edit().putBoolean("intro-seen", value).apply() }
    var role: String get() = prefs.getString("role", "").orEmpty(); set(value) { prefs.edit().putString("role", value).apply() }
    var brandShown: Boolean get() = prefs.getBoolean("brand-shown", false); set(value) { prefs.edit().putBoolean("brand-shown", value).apply() }
}

/** Role cards stay stacked rows below 360 dp or at 1.3x text and above; wider, the icon grows a little. */
internal fun welcomeRolesStacked(screenWidthDp: Int, fontScale: Float): Boolean = screenWidthDp < 360 || fontScale >= 1.3f

/**
 * Adds the first person, then (once that save has finished and the person is listed) the optional profile details as the shared
 * profile entry, exactly as the profile editor saves them. Runs on the model's scope because the welcome screen leaves as soon as
 * the person exists.
 */
internal fun CareModel.addPersonWithProfile(name: String, profile: CarePatientProfile?) {
    addPerson(name) {
        if (profile == null) return@addPerson
        val id = selected.value
        viewModelScope.launch {
            val ready = withTimeoutOrNull(15_000) { people.first { list -> list.any { it.id == id } }; busy.first { !it }; true } ?: false
            if (ready && selected.value == id && canEdit.value) saveItem("journal", PatientProfileCodec.TITLE, PatientProfileCodec.encode(profile))
        }
    }
}

/**
 * The welcome flow shown while no person is open. [accountContent] and [doctorContent] are the real account forms (CareRoot);
 * without them the old callbacks ([onAccount], [onSharedWorkspace]) open the account sheet instead. [onSaveProfile] adds the person;
 * without it the final button calls [onStart].
 */
@Composable fun Welcome(onAccount: () -> Unit, onStart: () -> Unit, onSharedWorkspace: () -> Unit = {}, onDoctorDemo: () -> Unit = {}, onPatientWalkthrough: (String) -> Unit = {},
    accountContent: (@Composable (() -> Unit, (Boolean) -> Unit) -> Unit)? = null,
    doctorContent: (@Composable (() -> Unit, (Boolean) -> Unit) -> Unit)? = null,
    onPanelChanged: (String) -> Unit = {}, entryBusy: Boolean = false,
    signedIn: Boolean = false, profileError: String? = null,
    onSaveProfile: ((String, CarePatientProfile?) -> Unit)? = null) {
    val context = LocalContext.current
    val prefs = remember { CareOnboardingPrefs(context) }
    val t = careText()
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var step by rememberSaveable { mutableStateOf(OnboardingFlow.start(signedIn, prefs.role)) }
    val helloShown by rememberSaveable { mutableStateOf(step == OnboardingStep.Hello) }
    var role by rememberSaveable { mutableStateOf(prefs.role.takeIf { it == SignupRole.FAMILY } ?: SignupRole.PATIENT) }
    var chosen by rememberSaveable { mutableStateOf(prefs.role) }
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var illness by rememberSaveable { mutableStateOf("") }
    var illnessSince by rememberSaveable { mutableStateOf("") }
    var doctorName by rememberSaveable { mutableStateOf("") }
    var doctorPlace by rememberSaveable { mutableStateOf("") }
    var doctorPhone by rememberSaveable { mutableStateOf("") }
    var doctorLandline by rememberSaveable { mutableStateOf("") }
    var treatmentCodes by rememberSaveable { mutableStateOf("") }
    var treatmentNote by rememberSaveable { mutableStateOf("") }
    val treatments = OnboardingFlow.codes(treatmentCodes)
    fun answers() = OnboardingFlow.profile(age, illness, illnessSince, doctorName, doctorPlace, doctorPhone, treatments, treatmentNote, doctorLandline)
    var formBusy by remember { mutableStateOf(false) }
    var direction by remember { mutableStateOf(SaharaMotion.Direction.Forward) }
    var walkthroughChooser by rememberSaveable { mutableStateOf(false) }
    val locked = formBusy || entryBusy
    val family = role == SignupRole.FAMILY

    fun go(next: OnboardingStep, dir: SaharaMotion.Direction = SaharaMotion.Direction.Forward) {
        if (next == step) return
        focus.clearFocus()
        if (step == OnboardingStep.Hello) prefs.introSeen = true
        if (next.panel != step.panel) onPanelChanged(next.panel)
        direction = dir; step = next
    }
    val backTarget = OnboardingFlow.back(step, signedIn, helloShown)
    fun back() {
        if (locked) return
        backTarget?.let { go(it, SaharaMotion.Direction.Back) }
        backTarget?.let { go(it, SaharaMotion.Direction.Back) }
    }
    fun pick(key: String) {
        if (locked) return
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        chosen = key; prefs.role = key
        if (key == SignupRole.DOCTOR) go(OnboardingStep.Doctor)
        else { role = key; go(if (signedIn) OnboardingStep.Name else OnboardingStep.Account) }
    }
    val close: () -> Unit = { back() }
    // The steps are a plain AnimatedContent (no screen host) and back waits while locked, so a plain BackHandler, without the preview.
    androidx.activity.compose.BackHandler(backTarget != null) { back() }
    if (walkthroughChooser) PatientWalkthroughChooser(onPick = { walkthroughChooser = false; onPatientWalkthrough(it) }, onDismiss = { walkthroughChooser = false })

    val ageError = CarePatientProfileRules.age(age).second
    SideEffect { WelcomeBackdrop.value = when (step) { OnboardingStep.Role, OnboardingStep.Account, OnboardingStep.Doctor -> SaharaColors.PrimaryWash; OnboardingStep.SignIn, OnboardingStep.SignUp -> androidx.compose.ui.graphics.Color(0xFFF4F8F5); OnboardingStep.Hello -> SaharaColors.Ground; else -> WelcomePanelOrange } }
    DisposableEffect(Unit) { onDispose { WelcomeBackdrop.value = WelcomePanelOrange } }
    Column(Modifier.fillMaxSize().then(when (step) {
        OnboardingStep.Hello -> Modifier.background(Brush.verticalGradient(listOf(SaharaColors.PrimaryWash, SaharaColors.Ground)))
        // The same logo-green wash as the patient-side screens, behind the choice, sign-in and sign-up steps.
        OnboardingStep.Role, OnboardingStep.Account, OnboardingStep.SignIn, OnboardingStep.SignUp, OnboardingStep.Doctor -> Modifier.saharaHealthBackground()
        else -> Modifier
    }).testTag("welcome-panel-screen")) {
        OnboardingTopRow(step, canBack = backTarget != null, enabled = !locked, onBack = ::back,
            onDemo = { walkthroughChooser = true }, onDoctorDemo = onDoctorDemo)
        AnimatedContent(step, Modifier.weight(1f).fillMaxWidth(), transitionSpec = { SaharaMotion.transform(direction, rtl) }, contentAlignment = Alignment.TopCenter, label = "onboarding-step") { shown ->
            when (shown) {
                OnboardingStep.Hello -> HelloPage { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); go(OnboardingFlow.afterHello()) }
                OnboardingStep.Role -> OnboardingPage {
                    OnboardingLogoHero()
                    StepHeading(t("Who are you?", "आप कौन हैं?"), t("Choose the one that fits you best.", "जो आप पर सबसे सही बैठे, वह चुनें।"))
                    val large = !welcomeRolesStacked(LocalConfiguration.current.screenWidthDp, LocalDensity.current.fontScale)
                    listOf(
                        OnboardingChoice(SignupRole.PATIENT, t("I'm the patient", "मैं मरीज़ हूँ"), t("Keep your own care in one place", "अपनी देखभाल एक जगह रखें"), Icons.Default.Person, SaharaTone.Teal, "welcome-patient-family"),
                        OnboardingChoice(SignupRole.FAMILY, t("I'm caring for someone", "मैं किसी की देखभाल कर रहा/रही हूँ"), t("Family member or carer", "परिवार का सदस्य या देखभाल करने वाले"), Icons.Default.People, SaharaTone.Leaf, "welcome-family"),
                        OnboardingChoice(SignupRole.DOCTOR, t("I'm a doctor or nurse", "मैं डॉक्टर या नर्स हूँ"), t("We check your registration number first", "पहले हम आपकी पंजीकरण संख्या जाँचते हैं"), Icons.Default.MedicalServices, SaharaTone.Indigo, "doctor-workspace-entry"),
                    ).forEachIndexed { index, choice -> OnboardingChoiceCard(choice, index, selected = chosen == choice.key, enabled = !locked, large = large) { pick(choice.key) } }
                }
                OnboardingStep.Account -> OnboardingPage {
                    OnboardingLogoHero()
                    StepHeading(t("Do you want an account?", "क्या आप खाता बनाना चाहते हैं?"),
                        t("With an account, your records are kept safe if you change phones, and family and doctors can join you.", "खाते के साथ, फ़ोन बदलने पर भी आपके रिकॉर्ड सुरक्षित रहते हैं, और परिवार व डॉक्टर आपसे जुड़ सकते हैं।"))
                    // The clinic's invite link is the main way in (older-patients doc, point 1): it comes first and largest and opens
                    // the account form with its invite card on top (ClinicInviteFirst in CareAccount.kt); email and password come second.
                    OnboardingChoiceCard(OnboardingChoice("invite", t("I have an invite link from my clinic", "मेरे पास क्लिनिक से निमंत्रण लिंक है"),
                        t("Your nurse or doctor sends it on WhatsApp or SMS.", "आपकी नर्स या डॉक्टर इसे WhatsApp या SMS पर भेजते हैं।"), Icons.Default.Link, SaharaTone.Teal, "welcome-invite-link"),
                        0, selected = false, enabled = !locked, large = true) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (accountContent == null) onAccount() else go(OnboardingStep.SignUp)
                    }
                    Text(t("No invite link?", "निमंत्रण लिंक नहीं है?"), Modifier.padding(top = 4.dp).a11yHeading(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = SaharaColors.InkSub)
                    val choices = buildList {
                        if (CareEmailAvailability.ready) add(OnboardingChoice("create", t("Create an account", "खाता बनाएँ"), t("Email and password", "ईमेल और पासवर्ड"), Icons.Default.PersonAdd, SaharaTone.Rust, "welcome-create-account"))
                        add(OnboardingChoice("signin", t("I already have an account", "मेरा पहले से खाता है"), t("Sign in", "साइन इन"), Icons.Default.AccountCircle, SaharaTone.Indigo, "welcome-account"))
                        add(OnboardingChoice("local", t("Continue without an account", "बिना खाते के आगे बढ़ें"), t("Your records stay on this phone", "आपके रिकॉर्ड इसी फ़ोन पर रहेंगे"), Icons.Default.PhoneAndroid, SaharaTone.Leaf, "welcome-add-person"))
                    }
                    choices.forEachIndexed { index, choice ->
                        OnboardingChoiceCard(choice, index + 1, selected = false, enabled = !locked, large = false) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            when (choice.key) {
                                "create" -> if (accountContent == null) onAccount() else go(OnboardingStep.SignUp)
                                "signin" -> if (accountContent == null) onAccount() else go(OnboardingStep.SignIn)
                                else -> if (onSaveProfile == null) onStart() else go(OnboardingStep.Name)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                OnboardingStep.SignIn, OnboardingStep.SignUp -> Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
                    OnboardingLogoHero(80.dp)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(LocalSignupIntent provides SignupIntent(create = shown == OnboardingStep.SignUp, role = role)) {
                            accountContent?.invoke(close) { formBusy = it }
                        }
                    }
                }
                OnboardingStep.Doctor -> Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
                    OnboardingLogoHero(80.dp)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalSignupIntent provides SignupIntent(create = false, role = SignupRole.DOCTOR)) {
                        if (doctorContent != null) doctorContent.invoke(close) { formBusy = it }
                        else OnboardingPage {
                            OnboardingHero(SaharaLottieKind.Heart, SaharaTone.Indigo)
                            StepHeading(t("Doctor sign in", "डॉक्टर साइन इन"), null)
                            SaharaPrimaryButton(t("Sign in", "साइन इन"), onClick = onSharedWorkspace, enabled = !locked, modifier = Modifier.fillMaxWidth().testTag("welcome-open-doctor-workspace"))
                        }
                    }
                    }
                }
                OnboardingStep.Name -> OnboardingPage {
                    OnboardingHero(if (family) SaharaLottieKind.Hug else SaharaLottieKind.Wave, SaharaTone.Teal)
                    StepHeading(if (family) t("What is the name of the person you care for?", "जिनकी देखभाल करते हैं, उनका नाम क्या है?") else t("What is your name?", "आपका नाम क्या है?"),
                        if (family) t("You can add more people later.", "आप बाद में और लोग जोड़ सकते हैं।") else null)
                    OnboardingField(name, { name = it.take(100) }, t("Name", "नाम"), "onboarding-name",
                        KeyboardOptions(capitalization = KeyboardCapitalization.Words, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)) { if (name.isNotBlank()) go(OnboardingStep.Age) }
                    if (signedIn && accountContent != null) TextButton(onClick = { go(OnboardingStep.SignIn) }, enabled = !locked, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 56.dp).testTag("onboarding-your-account")) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Your account", "आपका खाता"), fontWeight = FontWeight.SemiBold)
                    }
                }
                OnboardingStep.Age -> OnboardingPage {
                    OnboardingHero(SaharaLottieKind.Calendar, SaharaTone.Marigold)
                    StepHeading(if (family) t("How old are they?", "उनकी उम्र क्या है?") else t("How old are you?", "आपकी उम्र क्या है?"), t("Optional", "ज़रूरी नहीं"))
                    // By tap, not typing (24 Nov): Simple Mode turns on by itself at 60 and above from this age.
                    CareAgePicker(age, { picked -> focus.clearFocus(); age = picked }, "onboarding-age", Modifier.fillMaxWidth(), enabled = !locked, large = true)
                    // The age hint slides in under the picker instead of popping, and out again once it is fixed.
                    OnboardingShowWhen(ageError, Modifier.fillMaxWidth()) { Text(t(it.en, it.hi), Modifier.fillMaxWidth().announce(), color = RustText, fontSize = 15.sp, textAlign = TextAlign.Center) }
                }
                OnboardingStep.Illness -> OnboardingPage {
                    OnboardingHero(SaharaLottieKind.Breathe, SaharaTone.Plum, compact = true)
                    StepHeading(t("About the illness", "बीमारी के बारे में"),
                        t("Optional", "ज़रूरी नहीं") + " · " + if (signedIn) t("Shared only with the family and doctors you invite.", "सिर्फ़ उन्हीं परिवार वालों और डॉक्टरों के साथ साझा होगा जिन्हें आप बुलाएँ।") else t("Kept on this phone.", "इसी फ़ोन पर रहेगा।"))
                    // Tap first (24 Nov): the common illnesses as chips; "Other" or the field below for anything else. A tapped chip fills the field.
                    val illnessFocus = remember { FocusRequester() }
                    IllnessChips(illness, enabled = !locked, onPick = { label ->
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        illness = if (illness.trim() == label) "" else label
                    }, onOther = { wasChip ->
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (wasChip) illness = ""
                        runCatching { illnessFocus.requestFocus() }
                    })
                    OnboardingField(illness, { illness = it.take(200) }, if (family) t("What illness are they living with?", "उन्हें कौन-सी बीमारी है?") else t("What illness are you living with?", "आपको कौन-सी बीमारी है?"), "onboarding-illness",
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        placeholder = t("For example: breast cancer, heart failure", "जैसे: स्तन कैंसर, हृदय की कमज़ोरी"), autoFocus = false, focusRequester = illnessFocus) { focus.clearFocus() }
                    // Since when, by tap (24 Nov clinical meeting): Months or Years, then a number from 1 to 12. Nothing to type.
                    Text(t("Since when", "कब से"), Modifier.fillMaxWidth().padding(top = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink)
                    // The picker gives its own key-tap feel on each tap.
                    CareDurationPicker(illnessSince, { picked ->
                        focus.clearFocus()
                        illnessSince = picked
                    }, "onboarding-illness-since", Modifier.fillMaxWidth(), enabled = !locked, unitHeight = 56.dp)
                }
                OnboardingStep.CareDoctor -> OnboardingPage {
                    OnboardingHero(SaharaLottieKind.Heart, SaharaTone.Rust, compact = true)
                    StepHeading(if (family) t("Which doctor are they seeing now?", "वे अभी किस डॉक्टर को दिखा रहे हैं?") else t("Which doctor are you seeing now?", "आप अभी किस डॉक्टर को दिखा रहे हैं?"), t("Optional", "ज़रूरी नहीं"))
                    val phoneFocus = remember { FocusRequester() }; val landlineFocus = remember { FocusRequester() }
                    OnboardingField(doctorName, { doctorName = it.take(100) }, t("Doctor's name", "डॉक्टर का नाम"), "onboarding-doctor-name",
                        KeyboardOptions(capitalization = KeyboardCapitalization.Words, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)) { focus.moveFocus(FocusDirection.Down) }
                    OnboardingField(doctorPlace, { doctorPlace = it.take(120) }, t("Hospital or clinic", "अस्पताल या क्लिनिक"), "onboarding-doctor-place",
                        KeyboardOptions(capitalization = KeyboardCapitalization.Words, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next), autoFocus = false) { runCatching { phoneFocus.requestFocus() } }
                    // Country code by tap (India first), the number on the phone keyboard; the hospital's landline is optional beside it.
                    CarePhoneField(doctorPhone, { doctorPhone = it }, t("Contact number (doctor or hospital)", "संपर्क नंबर (डॉक्टर या अस्पताल)"), "onboarding-doctor-phone", Modifier.fillMaxWidth(),
                        large = true, container = SaharaColors.Surface, enabled = !locked, imeAction = ImeAction.Next, focusRequester = phoneFocus) { runCatching { landlineFocus.requestFocus() } }
                    CarePhoneField(doctorLandline, { doctorLandline = it }, t("Hospital / landline number (optional)", "अस्पताल / लैंडलाइन नंबर (ज़रूरी नहीं)"), "onboarding-doctor-landline", Modifier.fillMaxWidth(),
                        large = true, container = SaharaColors.Surface, enabled = !locked, imeAction = ImeAction.Done, focusRequester = landlineFocus) { go(OnboardingStep.Treatment) }
                }
                OnboardingStep.Treatment -> OnboardingPage {
                    OnboardingHero(SaharaLottieKind.Pill, SaharaTone.Leaf, compact = true)
                    StepHeading(t("What treatment is going on now?", "अभी कौन-सा इलाज चल रहा है?"), t("Choose all that apply. Optional.", "जो भी लागू हों, सब चुनें। ज़रूरी नहीं।"))
                    TreatmentChips(treatments, enabled = !locked) { code ->
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        treatmentCodes = CarePatientProfile.toggleTreatment(treatments, code).joinToString(",")
                    }
                    OnboardingField(treatmentNote, { treatmentNote = it.take(200) }, t("Anything else (optional)", "कुछ और (ज़रूरी नहीं)"), "onboarding-treatment-note",
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done), autoFocus = false) { go(OnboardingStep.Finish) }
                }
                OnboardingStep.Finish -> OnboardingFinish(name.trim(), answers(), profileError)
            }
        }
        if (step != OnboardingStep.Hello && step != OnboardingStep.Doctor) OnboardingDock(step) { dockStep ->
            when (dockStep) {
                OnboardingStep.Hello -> Unit
                OnboardingStep.Role -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, Modifier.size(22.dp), tint = SaharaColors.PrimaryPressed)
                    Spacer(Modifier.width(10.dp))
                    Text(t("We never ask for Aadhaar, date of birth or gender to sign up. Only an email and a password.", "साइन अप के लिए हम कभी आधार, जन्मतिथि या लिंग नहीं माँगते। सिर्फ़ ईमेल और पासवर्ड।"),
                        Modifier.weight(1f), color = SaharaColors.PrimaryPressed, fontSize = 14.sp, lineHeight = 20.sp)
                }
                OnboardingStep.Account -> TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.PrimaryPressed)) {
                    Icon(Icons.Default.Policy, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Privacy policy", "निजता नीति"), fontWeight = FontWeight.SemiBold)
                }
                OnboardingStep.Doctor -> Unit
                OnboardingStep.Name -> DockButton(t("Next", "आगे"), Modifier.testTag("onboarding-next"), enabled = name.isNotBlank() && !locked) { go(OnboardingStep.Age) }
                OnboardingStep.Age, OnboardingStep.Illness, OnboardingStep.CareDoctor, OnboardingStep.Treatment -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val filled = when (dockStep) {
                        OnboardingStep.Age -> age.isNotBlank() && ageError == null
                        OnboardingStep.Illness -> illness.isNotBlank() || illnessSince.isNotBlank()
                        OnboardingStep.CareDoctor -> doctorName.isNotBlank() || doctorPlace.isNotBlank() || doctorPhone.isNotBlank() || doctorLandline.isNotBlank()
                        else -> treatments.isNotEmpty() || treatmentNote.isNotBlank()
                    }
                    val next = when (dockStep) { OnboardingStep.Age -> OnboardingStep.Illness; OnboardingStep.Illness -> OnboardingStep.CareDoctor; OnboardingStep.CareDoctor -> OnboardingStep.Treatment; else -> OnboardingStep.Finish }
                    DockButton(t("Next", "आगे"), Modifier.testTag("onboarding-next"), enabled = filled && !locked) { go(next) }
                    // Skipping clears what this step holds, so a half-typed answer is never saved by accident.
                    TextButton(onClick = {
                        when (dockStep) {
                            OnboardingStep.Age -> age = ""
                            OnboardingStep.Illness -> { illness = ""; illnessSince = "" }
                            OnboardingStep.CareDoctor -> { doctorName = ""; doctorPlace = ""; doctorPhone = ""; doctorLandline = "" }
                            else -> { treatmentCodes = ""; treatmentNote = "" }
                        }
                        go(next)
                    }, enabled = !locked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("onboarding-skip-step"), colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.OnBand)) {
                        Text(t("Skip for now", "अभी छोड़ें"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
                OnboardingStep.Finish -> DockButton(if (entryBusy) t("Saving…", "सहेज रहे हैं…") else t("Open Sahara", "सहारा खोलें"), Modifier.testTag("welcome-save-person"),
                    enabled = name.isNotBlank(), busy = entryBusy) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val save = onSaveProfile
                    if (save == null) onStart() else save(name.trim(), answers())
                }
                OnboardingStep.SignIn, OnboardingStep.SignUp -> Unit
            }
        }
    }
}

/** The Sahara logo on the soft two-ring circle, used above the role, account, sign-in and doctor screens (and, larger, on the hello screen). */
@Composable private fun OnboardingLogoHero(size: Dp = 112.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(SaharaColors.PrimaryWash), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size * .78f).clip(CircleShape).background(SaharaColors.Surface), contentAlignment = Alignment.Center) {
                Image(saharaImage(R.drawable.sahara_professional_logo, size * .6f), null, Modifier.size(size * .6f), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
    }
}

/** A small, centred, underlined "Demo" link (optionally after a short lead-in), 48 dp tall. */
@Composable private fun DemoLink(lead: String?, color: androidx.compose.ui.graphics.Color, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val t = careText()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (lead != null) Text(lead, fontSize = 16.sp, color = SaharaColors.InkSub)
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag(tag), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
            Text(t("Demo", "डेमो"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
        }
    }
}

/** The very first screen: the logo on two soft rings, the name, the tagline, one line about the app and "Get started". Calm; the logo fades and scales in once. */
@Composable private fun HelloPage(onStart: () -> Unit) {
    val t = careText()
    var shown by remember { mutableStateOf(!SaharaMotion.enabled) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, if (SaharaMotion.enabled) tween(700, easing = FastOutSlowInEasing) else snap(), label = "hello logo")
    Column(Modifier.fillMaxSize().testTag("onboarding-hello").padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            Box(Modifier.size(200.dp).graphicsLayer { alpha = appear; scaleX = .88f + .12f * appear; scaleY = .88f + .12f * appear }.clip(CircleShape).background(SaharaColors.PrimaryWash), contentAlignment = Alignment.Center) {
                Box(Modifier.size(156.dp).clip(CircleShape).background(SaharaColors.Surface), contentAlignment = Alignment.Center) {
                    Image(saharaImage(R.drawable.sahara_professional_logo, 120.dp), null, Modifier.size(120.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
            Text(t("Sahara", "सहारा"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, color = SaharaColors.Ink, textAlign = TextAlign.Center)
            Text(t("Palliative care, together.", "साथ मिलकर, सुकून भरी देखभाल।"), fontSize = 18.sp, lineHeight = 26.sp, color = SaharaColors.InkSub, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().widthIn(max = 460.dp).clip(RoundedCornerShape(20.dp)).background(SaharaColors.PrimaryWash).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("“", Modifier.height(40.dp), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp, color = SaharaColors.Primary)
                Text(t("Comfort, dignity and your own wishes — kept in one place, for you and the people who care for you.",
                    "आराम, सम्मान और आपकी अपनी इच्छाएँ — एक ही जगह, आपके और आपकी देखभाल करने वालों के लिए।"),
                    fontSize = 17.sp, lineHeight = 26.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = SaharaColors.Ink)
            }
        }
        Button(onClick = onStart, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = SaharaColors.Primary, contentColor = androidx.compose.ui.graphics.Color.White),
            contentPadding = PaddingValues(horizontal = 24.dp), modifier = Modifier.fillMaxWidth().height(56.dp).saharaPressBounce().testTag("onboarding-get-started")) {
            Text(t("Get started", "शुरू करें"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(22.dp))
        }
        Spacer(Modifier.height(20.dp).navigationBarsPadding())
    }
}

private class OnboardingChoice(val key: String, val title: String, val detail: String, val icon: ImageVector, val tone: SaharaTone, val tag: String)

/** Back, the thin progress bar and the demo link. The bar fills smoothly as steps advance and fades away where it has no meaning. */
@Composable private fun OnboardingTopRow(step: OnboardingStep, canBack: Boolean, enabled: Boolean, onBack: () -> Unit, onDemo: () -> Unit, onDoctorDemo: () -> Unit) {
    val t = careText()
    val progress = OnboardingFlow.progress(step)
    val barAlpha by animateFloatAsState(if (progress == null) 0f else 1f, SaharaMotion.tween(SaharaMotion.VALUE_MS), label = "onboarding progress alpha")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp)) {
            // Back fades in and out in its fixed slot, so nothing beside it shifts.
            // Fully qualified: inside this Box (in a Row) the bare name would pick RowScope.AnimatedVisibility and fail to compile.
            androidx.compose.animation.AnimatedVisibility(canBack, enter = SaharaMotion.enter, exit = SaharaMotion.exit, label = "onboarding back") {
                IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(56.dp).testTag("welcome-panel-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = SaharaColors.Ink) }
            }
        }
        Box(Modifier.weight(1f).padding(horizontal = 8.dp).graphicsLayer { alpha = barAlpha }) {
            SaharaProgressBar(progress ?: 0f, Modifier.testTag("onboarding-progress"), tone = SaharaTone.Rust, label = if (progress == null) null else t("Progress", "प्रगति") + " " + (progress * 100).toInt() + "%")
        }
        Box(Modifier.widthIn(min = 56.dp), contentAlignment = Alignment.CenterEnd) {
            // The patient demo sits in the top-right corner of Hello, Role and Account (one instance per screen).
            androidx.compose.animation.AnimatedVisibility(step == OnboardingStep.Hello || step == OnboardingStep.Role || step == OnboardingStep.Account || step == OnboardingStep.Doctor, enter = SaharaMotion.enter, exit = SaharaMotion.exit, label = "onboarding demo") {
                val doctorStep = step == OnboardingStep.Doctor
                TextButton(onClick = if (doctorStep) onDoctorDemo else onDemo, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag(if (doctorStep) "doctor-demo-entry" else "patient-walkthrough-entry"), colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.PrimaryPressed)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(t("Demo", "डेमो"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                }
            }
        }
    }
}

/** One question or choice per page: generous padding, scrolls at large text, and its rows rise in once. */
@Composable private fun OnboardingPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

/** The page's title and its one short line, centred under the [OnboardingHero] picture. */
@Composable private fun StepHeading(title: String, detail: String?) {
    Text(title, Modifier.fillMaxWidth().a11yHeading().testTag("onboarding-heading"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, textAlign = TextAlign.Center, color = SaharaColors.Ink)
    if (detail != null) Text(detail, Modifier.fillMaxWidth(), fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, color = SaharaColors.InkSub)
    Spacer(Modifier.height(4.dp))
}

/**
 * Every onboarding page opens with one Lottie, centred above its title on a soft round wash (24 Nov: "Lottie animations on
 * everything"). Looping kinds loop calmly; the play-once kinds play three times and rest; Success and Confetti play once.
 * With "Remove animations" or Battery Saver on, [SaharaLottie] shows its still frame. [compact] is for pages with choice cards or
 * several fields, so the first card or field stays on screen; while the keyboard is open the picture shrinks out of its way.
 */
@Composable private fun OnboardingHero(kind: SaharaLottieKind, tone: SaharaTone, compact: Boolean = false, tag: String? = null, size: Dp? = null) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val full = size ?: when {
        compact -> if (screenHeight < 720) 112.dp else 140.dp
        screenHeight < 700 -> 140.dp
        else -> 176.dp
    }
    // Only whether the keyboard is open matters here, so the page does not recompose on every frame of the keyboard sliding.
    val density = LocalDensity.current; val ime = WindowInsets.ime
    val imeOpen by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val fraction = animateFloatAsState(if (imeOpen) .55f else 1f, SaharaMotion.tween(SaharaMotion.VALUE_MS), label = "onboarding hero")
    val once = kind == SaharaLottieKind.Success || kind == SaharaLottieKind.Confetti
    // The shrinking is read in layout and drawing only (no recomposition per frame): the slot's height follows the fraction,
    // and the wash and Lottie keep their full size and scale down from the centre.
    Box(Modifier.fillMaxWidth().layout { measurable, constraints ->
        val h = (full.roundToPx() * fraction.value).toInt().coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(placeable.width, h) { placeable.place(0, 0) }
    }.then(if (tag != null) Modifier.testTag(tag) else Modifier), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(full).graphicsLayer { scaleX = fraction.value; scaleY = fraction.value }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(full * .94f).clip(CircleShape).background(tone.wash))
            SaharaLottie(kind, Modifier.size(full * .8f), loop = kind.loops && !once, iterations = if (once) 1 else 3)
        }
    }
}

/**
 * A big tappable choice: icon chip, title and one plain line. It springs in gently when chosen (and remembers the choice when
 * the person comes back), bounces under the finger, and rises in after the card above it on first show.
 */
@Composable private fun OnboardingChoiceCard(choice: OnboardingChoice, index: Int, selected: Boolean, enabled: Boolean, large: Boolean = true, onClick: () -> Unit) {
    val tone = choice.tone
    val scale by animateFloatAsState(if (selected) 1.015f else 1f, if (SaharaMotion.enabled) spring(dampingRatio = .55f, stiffness = Spring.StiffnessMediumLow) else snap(), label = "choice select")
    val edge by animateDpAsState(if (selected) 3.dp else 1.5.dp, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "choice edge")
    val fill by animateColorAsState(if (selected) tone.wash else SaharaColors.Surface, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "choice fill")
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(SaharaRadius.Xl), color = fill, contentColor = SaharaColors.Ink,
        border = BorderStroke(edge, tone.solid.copy(alpha = if (selected) 1f else .6f)), shadowElevation = if (selected) 3.dp else 2.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (large) 88.dp else 72.dp).saharaStagger(index).saharaPressBounce().graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { this.selected = selected }.testTag(choice.tag)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SaharaIconChip(choice.icon, tone, size = if (large) 52 else 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(choice.title, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = tone.ink)
                Text(choice.detail, fontSize = 15.sp, lineHeight = 20.sp, color = SaharaColors.InkSub)
            }
            Spacer(Modifier.width(8.dp))
            Icon(if (selected) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(26.dp), tint = tone.ink)
        }
    }
}

/** A large single-line answer field; the keyboard opens on it, and the keyboard's own Next/Done moves on. */
@Composable private fun OnboardingField(value: String, onValueChange: (String) -> Unit, label: String, tag: String, keyboard: KeyboardOptions,
    error: String? = null, placeholder: String? = null, autoFocus: Boolean = true, focusRequester: FocusRequester? = null, onSubmit: () -> Unit) {
    val own = remember { FocusRequester() }
    val focus = focusRequester ?: own
    // Only the first field of a page takes the keyboard on arrival; the keyboard's Next moves on to the others.
    if (autoFocus) LaunchedEffect(Unit) { delay(SaharaMotion.ms(SaharaMotion.ENTER_MS).toLong()); runCatching { focus.requestFocus() } }
    OutlinedTextField(value, onValueChange, singleLine = true, label = { Text(label) }, isError = error != null,
        placeholder = placeholder?.let { { Text(it, color = SaharaColors.InkFaint) } },
        supportingText = error?.let { { Text(it, Modifier.announce()) } },
        textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, color = SaharaColors.Ink),
        keyboardOptions = keyboard, keyboardActions = KeyboardActions(onNext = { onSubmit() }, onDone = { onSubmit() }),
        shape = RoundedCornerShape(SaharaRadius.Md),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SaharaColors.Surface, unfocusedContainerColor = SaharaColors.Surface, focusedBorderColor = SaharaColors.Primary, focusedLabelColor = RustText),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).focusRequester(focus).testTag(tag))
}

/** The band-coloured dock at the foot (it meets the band behind the navigation bar). Its content cross-fades between steps. */
@Composable private fun OnboardingDock(step: OnboardingStep, content: @Composable (OnboardingStep) -> Unit) {
    // On the green-wash steps the dock is a calm light-green strip with dark green text; on sign-in and sign-up it is empty and clear. The other steps keep the band.
    val light = step == OnboardingStep.Role || step == OnboardingStep.Account || step == OnboardingStep.Doctor
    val clear = step == OnboardingStep.SignIn || step == OnboardingStep.SignUp
    val fill by animateColorAsState(when { light -> SaharaColors.PrimaryWash; clear -> androidx.compose.ui.graphics.Color.Transparent; else -> SaharaColors.Band }, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "dock fill")
    Surface(color = fill, contentColor = if (light) SaharaColors.PrimaryPressed else SaharaColors.OnBand, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        border = if (light) BorderStroke(1.dp, SaharaColors.Primary.copy(alpha = .3f)) else null, modifier = Modifier.fillMaxWidth().testTag("welcome-orange-panel")) {
        AnimatedContent(step, transitionSpec = { SaharaMotion.crossfade() }, contentAlignment = Alignment.Center, label = "Onboarding dock") { shown ->
            val empty = shown == OnboardingStep.SignIn || shown == OnboardingStep.SignUp
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = if (empty) 12.dp else 18.dp, bottom = if (empty) 4.dp else 14.dp)) { content(shown) }
        }
    }
}

/** The dock's main action: white on the band, band-coloured label, 56 dp, with Sahara's press bounce. */
@Composable private fun DockButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, shape = RoundedCornerShape(SaharaRadius.Md),
        colors = ButtonDefaults.buttonColors(containerColor = SaharaColors.OnBand, contentColor = SaharaColors.Band, disabledContainerColor = SaharaColors.BandRaised, disabledContentColor = SaharaColors.OnBandSub),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).saharaPressBounce()) {
        if (busy) { CircularProgressIndicator(Modifier.size(20.dp), color = SaharaColors.OnBandSub, strokeWidth = 2.5.dp); Spacer(Modifier.width(10.dp)) }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** The common illnesses as one-tap chips (56 dp, wrap at large text), then "Other", which opens the field for anything else. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun IllnessChips(value: String, enabled: Boolean, onPick: (String) -> Unit, onOther: (Boolean) -> Unit) {
    val t = careText()
    val options = listOf(
        "cancer" to t("Cancer", "कैंसर"), "heart" to t("Heart failure", "हृदय की कमज़ोरी"), "kidney" to t("Kidney disease", "गुर्दे की बीमारी"),
        "lung" to t("Lung disease", "फेफड़ों की बीमारी"), "stroke" to t("Stroke", "स्ट्रोक (लकवा)"), "dementia" to t("Dementia", "डिमेंशिया (याददाश्त की बीमारी)"),
        "liver" to t("Liver disease", "लिवर की बीमारी"), "parkinson" to t("Parkinson's disease", "पार्किंसन रोग"))
    val current = value.trim()
    val isChip = options.any { it.second == current }
    FlowRow(Modifier.fillMaxWidth().testTag("onboarding-illness-chips"), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, (key, label) -> OnboardingChip(label, on = current == label, enabled = enabled, tone = SaharaTone.Plum, index = index, tag = "onboarding-illness-$key") { onPick(label) } }
        OnboardingChip(t("Other", "अन्य"), on = current.isNotEmpty() && !isChip, enabled = enabled, tone = SaharaTone.Leaf, index = options.size, tag = "onboarding-illness-other") { onOther(isChip) }
    }
}

/** One big tickable pill chip (56 dp), as on the treatment step; it settles in with a soft spring when ticked, still when motion is off. */
@Composable private fun OnboardingChip(label: String, on: Boolean, enabled: Boolean, tone: SaharaTone, index: Int, tag: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (on) 1.03f else 1f, if (SaharaMotion.enabled) spring(dampingRatio = .5f, stiffness = Spring.StiffnessMedium) else snap(), label = "onboarding chip")
    val fill by animateColorAsState(if (on) tone.wash else SaharaColors.Surface, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "onboarding chip fill")
    val edge by animateColorAsState(if (on) tone.solid else SaharaColors.Edge, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "onboarding chip edge")
    Surface(checked = on, onCheckedChange = { onClick() }, enabled = enabled, shape = RoundedCornerShape(SaharaRadius.Pill),
        color = fill, contentColor = SaharaColors.Ink, border = BorderStroke(if (on) 2.dp else 1.5.dp, edge),
        modifier = Modifier.heightIn(min = 56.dp).saharaStagger(index).graphicsLayer { scaleX = scale; scaleY = scale }.testTag(tag)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (on) { Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = tone.ink); Spacer(Modifier.width(6.dp)) }
            Text(label, fontSize = 17.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) tone.ink else SaharaColors.Ink)
        }
    }
}

/**
 * Treatment now as big tickable chips (56 dp, wrap at large text). "Pain and symptom care only" and "Not sure" stand alone
 * ("No treatment now" was taken off on 24 Nov: patients still receive symptom care) (see [CarePatientProfile.toggleTreatment]). A ticked chip settles in with a soft spring; still when motion is off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TreatmentChips(selected: List<String>, enabled: Boolean, onToggle: (String) -> Unit) {
    val t = careText()
    FlowRow(Modifier.fillMaxWidth().testTag("onboarding-treatments"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CarePatientProfile.TREATMENTS.forEachIndexed { index, code ->
            val on = code in selected
            val tone = if (code in CarePatientProfile.TREATMENTS_ALONE) SaharaTone.Leaf else SaharaTone.Plum
            val scale by animateFloatAsState(if (on) 1.03f else 1f, if (SaharaMotion.enabled) spring(dampingRatio = .5f, stiffness = Spring.StiffnessMedium) else snap(), label = "treatment chip")
            val fill by animateColorAsState(if (on) tone.wash else SaharaColors.Surface, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "treatment chip fill")
            val edge by animateColorAsState(if (on) tone.solid else SaharaColors.Edge, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "treatment chip edge")
            Surface(checked = on, onCheckedChange = { onToggle(code) }, enabled = enabled, shape = RoundedCornerShape(SaharaRadius.Pill),
                color = fill, contentColor = SaharaColors.Ink, border = BorderStroke(if (on) 2.dp else 1.5.dp, edge),
                modifier = Modifier.heightIn(min = 56.dp).saharaStagger(index).graphicsLayer { scaleX = scale; scaleY = scale }.testTag("onboarding-treatment-$code")) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (on) { Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = tone.ink); Spacer(Modifier.width(6.dp)) }
                    Text(careTreatmentLabel(code, t), fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) tone.ink else SaharaColors.Ink)
                }
            }
        }
    }
}

/** "You're all set": the success mark, one calm line and the answers given, before the final button saves them. */
@Composable private fun OnboardingFinish(name: String, profile: CarePatientProfile?, error: String?) {
    val t = careText()
    val p = profile ?: CarePatientProfile()
    OnboardingPage {
        // The success tick on its wash, with one burst of confetti over it (played once; left out when animations are off).
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OnboardingHero(SaharaLottieKind.Success, SaharaTone.Success)
            if (remember { SaharaMotion.enabled }) SaharaLottie(SaharaLottieKind.Confetti, Modifier.size(200.dp), loop = false)
        }
        Text(t("You're all set", "सब तैयार है"), Modifier.fillMaxWidth().a11yHeading().testTag("onboarding-heading"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, textAlign = TextAlign.Center, color = SaharaColors.Ink)
        Text(t("Sahara is ready. You can add medicines, visits and notes from Today.", "सहारा तैयार है। आज वाले पेज से दवाइयाँ, मुलाक़ातें और नोट जोड़ सकते हैं।"), Modifier.fillMaxWidth(), fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, color = SaharaColors.InkSub)
        Surface(shape = RoundedCornerShape(SaharaRadius.Xl), color = SaharaColors.Surface, border = BorderStroke(1.dp, SaharaColors.Line), modifier = Modifier.fillMaxWidth().saharaAppear()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SaharaIconChip(Icons.Default.Person, SaharaTone.Teal, size = 48)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink)
                    p.age?.let { Text(t("Age", "उम्र") + " " + it, fontSize = 15.sp, color = SaharaColors.InkSub) }
                }
            }
        }
        // The care details, one row each, only for what was filled in; the doctor and family see the same on the profile card.
        val illnessLine = p.illness
        val doctorLine = careDoctorLine(p)
        val treatmentLine = careTreatmentLine(p, t)
        val rows = listOfNotNull(
            Triple(Icons.Default.MedicalInformation, t("Illness", "बीमारी"), illnessLine).takeIf { illnessLine.isNotBlank() },
            Triple(Icons.Default.Event, careIllnessSinceLabel(p.illnessSince, t), careIllnessSinceText(p.illnessSince, t)).takeIf { p.illnessSince.isNotBlank() },
            Triple(Icons.Default.LocalHospital, t("Doctor now", "अभी के डॉक्टर"), doctorLine).takeIf { doctorLine.isNotBlank() },
            Triple(Icons.Default.Healing, t("Treatment now", "अभी का इलाज"), treatmentLine).takeIf { treatmentLine.isNotBlank() })
        if (rows.isNotEmpty()) Surface(shape = RoundedCornerShape(SaharaRadius.Xl), color = SaharaColors.Surface, border = BorderStroke(1.dp, SaharaColors.Line), modifier = Modifier.fillMaxWidth().saharaAppear().testTag("onboarding-summary-care")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { (icon, label, value) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(icon, null, Modifier.size(22.dp), tint = RustText)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 14.sp, color = SaharaColors.InkSub)
                            Text(value, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = SaharaColors.Ink)
                        }
                    }
                }
            }
        }
        OnboardingShowWhen(error, Modifier.fillMaxWidth()) { Text(it, Modifier.fillMaxWidth().announce(), color = MaterialTheme.colorScheme.error, fontSize = 15.sp) }
    }
}

/**
 * A short brand moment after the system splash, on the very first launch only: the logo (the splash icon, same size and place,
 * so the hand-off is seamless) settles up while "Sahara" and the line under it rise in, then the whole layer fades into the
 * welcome screen, which is already composed underneath. Skipped on later launches and whenever animations are off.
 */
@Composable fun SaharaBrandMoment(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { CareOnboardingPrefs(context) }
    var showing by rememberSaveable { mutableStateOf(SaharaMotion.enabled && !prefs.brandShown) }
    LaunchedEffect(Unit) { if (showing) prefs.brandShown = true }
    Box(Modifier.fillMaxSize()) {
        content()
        if (showing) BrandOverlay { showing = false }
    }
}

@Composable private fun BrandOverlay(onDone: () -> Unit) {
    val t = careText()
    val lift = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    val layer = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { lift.animateTo(1f, tween(620, easing = SaharaMotion.Emphasized)) }
        delay(180)
        launch { words.animateTo(1f, tween(520, easing = SaharaMotion.Emphasized)) }
        delay(1100)
        layer.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        onDone()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = layer.value }.background(SaharaColors.Ground)
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
        .clearAndSetSemantics { contentDescription = t("Sahara", "सहारा") }.testTag("sahara-brand-moment"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The logo keeps the splash icon's size and place; a Lottie bloom opens behind it once and fades (it ends empty).
            Box(Modifier.size(240.dp).graphicsLayer {
                val s = 1f - .25f * lift.value; scaleX = s; scaleY = s; translationY = -lift.value * 20.dp.toPx() }, contentAlignment = Alignment.Center) {
                SaharaLottie(SaharaLottieKind.Bloom, Modifier.requiredSize(320.dp), loop = false)
                Image(painterResource(R.drawable.splash_icon), null, Modifier.fillMaxSize())
            }
            Text(t("Sahara", "सहारा"), Modifier.graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 18.dp.toPx() - 40.dp.toPx() },
                fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 46.sp, color = SaharaColors.Band)
            Text(t("Palliative care,\ntogether.", "पैलिएटिव देखभाल,\nसाथ मिलकर।"), Modifier.padding(horizontal = 24.dp).graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 24.dp.toPx() - 32.dp.toPx() },
                fontSize = 18.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, color = SaharaColors.InkSub)
        }
    }
}
