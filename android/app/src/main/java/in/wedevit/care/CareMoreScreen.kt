@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

// More (25 Nov redesign): a stock Android settings page. One anchor (the person's avatar and name), then plain rows under small
// group labels, Sign out and the version footer at the end. No cards, no coloured icon squares.
@Composable fun More(vm: CareModel, person: Person, onAccount: () -> Unit, onDelete: () -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, onFeedback: () -> Unit = {}, onReminders: () -> Unit = {}, onSharedWorkspace: () -> Unit = {}, onDocuments: () -> Unit = {}, doctorEmbedded: Boolean = false, walkthrough: Boolean = false, onCarePlan: (() -> Unit)? = null, onDevices: (() -> Unit)? = null, onInviteFamily: (() -> Unit)? = null) {
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current; val versions by vm.versions.collectAsStateWithLifecycle(); val acks by vm.acknowledgements.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val t = careText()
    val clinicianView by vm.clinician.collectAsStateWithLifecycle()
    val signedInSession = vm.cloud?.session?.collectAsStateWithLifecycle()?.value
    val email = signedInSession?.email.orEmpty()
    val language = LocalCareLanguage.current
    var about by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var profile by rememberSaveable { mutableStateOf(false) }
    var languageSheet by remember { mutableStateOf(false) }
    // Sign out sits at the bottom of More (owner, 25 Nov: "there's no sign out"), with the same confirm and logic as the account screen.
    var signingOut by remember { mutableStateOf(false) }
    var signOutBusy by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf<String?>(null) }
    val signOutScope = rememberCoroutineScope()
    // Patients and family reach Documents, Calendar, Lab history, the emergency plan and tubes and lines from the Care tab,
    // so those rows are only here for doctors.
    val shortcutsElsewhere = !doctorEmbedded && !clinicianView
    val age = remember(items, person.id) { PatientProfileCodec.latest(items, person.id)?.let { PatientProfileCodec.decode(it.detail)?.age } }
    val exportNotes = {
        val text = versions.joinToString("\n\n──────────\n\n") { v -> NoteRules.text(v, acks.filter { it.versionId == v.id && it.contentHash == v.contentHash }) }
        open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), t("Export care notes", "देखभाल नोट भेजें")))
    }
    val exportRow: @Composable () -> Unit = {
        SaharaRow(t("Export care notes", "देखभाल नोट भेजें"), Modifier.testTag("more-export-notes"), supporting = t("${versions.size} saved", "${versions.size} सहेजे गए"),
            icon = Icons.Default.Share, enabled = versions.isNotEmpty(), onClick = exportNotes)
    }
    val accountRow: @Composable () -> Unit = {
        SaharaRow(if (vm.cloud == null) t("Sign in", "साइन इन") else t("Account and sharing", "खाता और साझा करना"), Modifier.testTag("more-account"),
            supporting = if (vm.cloud == null) t("Share care with family", "परिवार के साथ देखभाल साझा करें") else email, icon = Icons.Default.AccountCircle, onClick = onAccount)
    }
    val showAccount = !doctorEmbedded && !walkthrough
    val showInvite = showAccount && onInviteFamily != null && vm.cloud != null && owner
    val showFeedback = !walkthrough
    val showDoctorEntry = !doctorEmbedded && !walkthrough
    val showRemove = owner && !walkthrough
    val showSignOut = signedInSession != null && !doctorEmbedded && !walkthrough
    val content: LazyListScope.() -> Unit = {
        if (doctorEmbedded) item(key = "more-heading", contentType = "heading") {
            Text(t("Daily care & tools", "रोज़ की देखभाल और साधन"), Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).a11yHeading(), style = MaterialTheme.typography.titleLarge)
        }
        // The one anchor: the person's initial, name and one short line. The full profile (and its editor) is one tap away.
        if (shortcutsElsewhere) item(key = "more-profile", contentType = "profile") {
            val scheme = MaterialTheme.colorScheme
            val line = listOfNotNull(age?.let { t("$it years", "$it साल") }, email.takeIf(String::isNotBlank)).joinToString(" · ")
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp).testTag("more-profile"), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(80.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text(person.name.trim().take(1).uppercase().ifBlank { "?" }, color = scheme.onPrimary, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                Text(person.name, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (line.isNotBlank()) Text(line, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { profile = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("more-profile-edit")) { Text(t("Edit profile", "प्रोफ़ाइल बदलें")) }
            }
        }
        // Doctors: the care tools first, as before (patients and family have them on the Care tab).
        if (!shortcutsElsewhere) {
            item(key = "more-care-label", contentType = "label") { SaharaGroupLabel(t("Care", "देखभाल")) }
            item(key = "more-care-group", contentType = "group") { Column(Modifier.fillMaxWidth()) {
                SaharaRow(t("Documents", "दस्तावेज़"), Modifier.testTag("more-documents-entry"), icon = Icons.Default.FolderOpen, onClick = onDocuments)
                if (onCarePlan != null) SaharaRow(t("Emergency plan", "आपातकालीन योजना"), Modifier.testTag("more-care-plan-entry"), icon = Icons.Default.LocalHospital, onClick = onCarePlan)
                if (onDevices != null && !clinicianView) SaharaRow(t("Tubes and lines", "नलियाँ और लाइनें"), Modifier.testTag("more-devices"), icon = Icons.Default.Healing, onClick = onDevices)
                SaharaRow(t("Calendar", "कैलेंडर"), Modifier.testTag("more-calendar"), icon = Icons.Default.CalendarMonth, onClick = onCalendar)
                SaharaRow(t("Lab history", "जाँच का इतिहास"), Modifier.testTag("more-labs"), icon = Icons.Default.Science, onClick = onLabs)
                exportRow()
            } }
        }
        // Simple Mode first and in plain sight, for patient and family, in the demo walkthrough too (root-cause fix, 25 Nov).
        if (shortcutsElsewhere) item(key = "more-simple-mode", contentType = "simple") { SimpleModeSwitchRow(vm, person) }
        // Doctors and nurses do not switch Simple Mode on, but they can write the message the patient's "Read to me" button reads.
        else if (!walkthrough) item(key = "more-simple-message", contentType = "simple") { SimpleReadAloudMessageRow(vm, person, clinician = true) }
        item(key = "more-settings-label", contentType = "label") { SaharaGroupLabel(t("Settings", "सेटिंग")) }
        item(key = "more-settings-group", contentType = "group") { Column(Modifier.fillMaxWidth()) {
            SaharaRow(t("Language", "भाषा"), Modifier.testTag("more-language"), supporting = language?.lang?.label, icon = Icons.Default.Translate,
                enabled = language != null, onClick = { languageSheet = true })
            if (!walkthrough) SaharaRow(t("Notifications", "सूचनाएँ"), Modifier.testTag("more-reminders"), supporting = t("Medicine and visit reminders", "दवा और मुलाक़ात के रिमाइंडर"),
                icon = Icons.Default.NotificationsNone, onClick = onReminders)
            SaharaRow(t("Privacy and data", "निजता और डेटा"), Modifier.testTag("more-privacy"), supporting = t("Research choice and privacy policy", "शोध का विकल्प और निजता नीति"),
                icon = Icons.Default.Lock, onClick = { privacy = true })
        } }
        if (shortcutsElsewhere) {
            item(key = "more-sharing-label", contentType = "label") { SaharaGroupLabel(t("Family and sharing", "परिवार और साझा करना")) }
            item(key = "more-sharing-group", contentType = "group") { Column(Modifier.fillMaxWidth()) {
                if (showAccount) accountRow()
                // Inviting family is one tap from here, not only in the avatar menu.
                if (showInvite) SaharaRow(t("Invite family", "परिवार को बुलाएँ"), Modifier.testTag("more-invite-family"), supporting = t("Send a link on WhatsApp", "व्हाट्सऐप पर लिंक भेजें"),
                    icon = Icons.Default.PersonAdd, onClick = { onInviteFamily?.invoke() })
                exportRow()
            } }
        } else if (showAccount) item(key = "more-account-row", contentType = "row") { accountRow() }
        if (showFeedback || doctorEmbedded || showDoctorEntry) {
            item(key = "more-help-label", contentType = "label") { SaharaGroupLabel(t("Help", "मदद")) }
            item(key = "more-help-group", contentType = "group") { Column(Modifier.fillMaxWidth()) {
                if (showFeedback) SaharaRow(t("Send feedback", "राय भेजें"), Modifier.testTag("patient-feedback-entry"), icon = Icons.Default.SentimentSatisfiedAlt, onClick = onFeedback)
                if (doctorEmbedded) SaharaRow(t("Shared care", "साझा देखभाल"), icon = Icons.Default.Groups, onClick = onSharedWorkspace)
                else if (showDoctorEntry) SaharaRow(t("I'm a doctor", "मैं डॉक्टर हूँ"), Modifier.testTag("more-doctor-workspace-entry"), icon = Icons.Default.MedicalServices, onClick = onSharedWorkspace)
            } }
        }
        // The irreversible step, then Sign out as the last row (owner, 25 Nov), both in the error colour.
        if (showRemove || showSignOut) item(key = "more-end-actions", contentType = "group") { Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (showRemove) SaharaRow(t("Remove this person's records", "इस व्यक्ति के रिकॉर्ड हटाएँ"), Modifier.testTag("more-delete"), icon = Icons.Default.DeleteOutline,
                tone = SaharaTone.Error, headlineColor = MaterialTheme.colorScheme.error, chevron = false, onClick = onDelete)
            if (showSignOut) SaharaRow(t("Sign out", "साइन आउट करें"), Modifier.testTag("more-sign-out"), supporting = email, icon = Icons.AutoMirrored.Filled.Logout,
                tone = SaharaTone.Error, headlineColor = MaterialTheme.colorScheme.error, chevron = false, enabled = !signOutBusy, onClick = { signingOut = true })
        } }
        // The version only as a small footer; a tap opens About.
        item(key = "more-about", contentType = "footer") {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { about = true }.testTag("more-about"), contentAlignment = Alignment.Center) {
                Text(androidx.compose.ui.res.stringResource(R.string.app_name) + " · " + t("Version ${BuildConfig.VERSION_NAME}", "संस्करण ${BuildConfig.VERSION_NAME}"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
    when {
        // Pushed pages replace More while open: back arrow, system back, and the bottom bar hidden (SaharaPage does all three).
        privacy -> PrivacyAndDataPage(vm, person, canChange = !clinicianView && !doctorEmbedded) { privacy = false }
        profile && shortcutsElsewhere -> SaharaColumnPage(t("Edit profile", "प्रोफ़ाइल बदलें"), onBack = { profile = false }, backTag = "more-profile-back") {
            Spacer(Modifier.height(8.dp)); PatientProfileCard(vm, person)
        }
        // Inside the doctor workspace the doctor's own header is already on screen, so More is only its list.
        doctorEmbedded -> LazyColumn(Modifier.fillMaxSize().testTag("more-list"), contentPadding = PaddingValues(bottom = 32.dp), content = content)
        else -> SaharaPage(t("More", "और"), listModifier = Modifier.testTag("more-list"), contentPadding = PaddingValues(bottom = 32.dp), actions = { SaharaPersonAction() }, content = content)
    }
    if (languageSheet) SaharaLanguageSheet(onDismiss = { languageSheet = false })
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text(t("Sign out?", "साइन आउट करें?")) },
        confirmButton = { TextButton(onClick = {
            signingOut = false; val cloud = vm.cloud
            if (cloud != null && !signOutBusy) { signOutBusy = true; vm.stopRecording(); signOutScope.launch {
                try { cloud.signOut() } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; signOutError = e.message ?: t("Couldn't finish. Please try again.", "काम पूरा नहीं हो सका। कृपया फिर कोशिश करें।") }
                finally { signOutBusy = false }
            } }
        }, modifier = Modifier.testTag("more-sign-out-confirm")) { Text(t("Sign out", "साइन आउट करें")) } },
        dismissButton = { TextButton(onClick = { signingOut = false }) { Text(t("Cancel", "रद्द करें")) } })
    signOutError?.let { SaharaDialog(it, onDismiss = { signOutError = null }, tone = SaharaTone.Error) }
    // About is a standard dialog.
    if (about) AlertDialog(onDismissRequest = { about = false }, modifier = Modifier.testTag("more-about-dialog"),
        title = { Text(androidx.compose.ui.res.stringResource(R.string.app_name)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(androidx.compose.ui.res.stringResource(R.string.app_full_name))
            Text(t(MedicineNames.ATTRIBUTION, "दवा के नाम: CDCI (NRCeS / C-DAC), CC BY 4.0"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by/4.0/"))) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("CC BY 4.0 licence", "CC BY 4.0 लाइसेंस")) }
            Text(t("Version ${BuildConfig.VERSION_NAME}", "संस्करण ${BuildConfig.VERSION_NAME}"), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { about = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) } })
}
