@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val CareRelations = listOf("Son", "Daughter", "Spouse", "Brother", "Sister", "Parent", "Other")
/** Shown in place of the saved English relation when Hindi is chosen. */
internal val CareRelationsHindi = mapOf("Son" to "बेटा", "Daughter" to "बेटी", "Spouse" to "पति/पत्नी", "Brother" to "भाई", "Sister" to "बहन", "Parent" to "माता/पिता", "Other" to "कोई और")

/**
 * Who is being added (Shirin, 6 Nov: "an option to pick between family member or nurse"). Stored in the existing care-team
 * contact role field with its existing values, so the server and older apps need nothing new: "family" or "caregiver".
 */
internal object CareCaregiverKind {
    const val FAMILY = "family"
    const val PAID_CARER = "caregiver"
    const val PAID_CARER_RELATION = "Nurse / paid carer"
    val kinds = listOf(FAMILY, PAID_CARER)
    /** The care-team contact role for a chosen kind; anything unknown is family. */
    fun contactRole(kind: String): String = if (kind == PAID_CARER) PAID_CARER else FAMILY
    /** The relationship saved with the contact and the invite log: the chosen relation for family, a fixed label for a paid carer. */
    fun relationship(kind: String, relation: String): String = if (kind == PAID_CARER) PAID_CARER_RELATION else relation.ifBlank { "Family" }
}

/** Opens WhatsApp (or WhatsApp Business) with the text ready to send; falls back to the share sheet. */
internal fun shareCareInvite(context: Context, text: String, whatsApp: Boolean) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (whatsApp) for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) { try { context.startActivity(Intent(send).setPackage(pkg)); return } catch (_: ActivityNotFoundException) {} }
    open(context, Intent.createChooser(send, "Share invitation"))
}

/** "Add a person" for family: name, relationship, how much they can do, then a WhatsApp invite link. */
@Composable fun AddFamilySheet(vm: CareModel, onDismiss: () -> Unit, onSignIn: () -> Unit, onInvited: () -> Unit = {}) {
    val cloud = vm.cloud
    val people by vm.people.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle(); val owner by vm.isOwner.collectAsStateWithLifecycle()
    val patient = people.firstOrNull { it.id == selected }?.name.orEmpty()
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }; var relation by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(CareCaregiverKind.FAMILY) }
    var canAdd by rememberSaveable { mutableStateOf(false) }; var careTeam by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }; var moreOptions by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val hindi = careHindi(); val t = careText()
    fun send(whatsApp: Boolean) {
        val service = cloud ?: return
        if (busy) return
        busy = true; error = null
        val person = selected; val account = service.session.value?.userId.orEmpty(); val label = CareInviteLink.cleanName(name)
        scope.launch {
            try {
                var invite = code
                if (invite.isEmpty()) {
                    val before = service.openInvites(person)
                    invite = service.invite(person, canAdd, careTeam)
                    require(CareInviteLink.isCode(invite)) { t("The invitation didn't look right. Please try again.", "निमंत्रण ठीक नहीं बना। कृपया फिर कोशिश करें।") }
                    if (vm.selected.value == person && service.session.value?.userId == account && vm.isOwner.value) {
                        code = invite
                        val relationship = CareCaregiverKind.relationship(kind, relation)
                        careInviteStore(context).logCreated(service, account, person, label, relationship, "family", before)
                        // Also kept as a care-team contact, so "Family member" or "Nurse / paid carer" is on record.
                        runCatching { vm.saveCareContact(CareContact(newId(), person, label, relationship, CareCaregiverKind.contactRole(kind)), vm.accountNamespace) {} }
                        onInvited()
                    }
                }
                shareCareInvite(context, CareInviteLink.shareText(patient, invite), whatsApp)
            } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: t("Couldn't create the invitation. Please try again.", "निमंत्रण नहीं बन सका। कृपया फिर कोशिश करें।") }
            finally { busy = false }
        }
    }
    val focus = remember { FocusRequester() }
    val canSend = !busy && name.isNotBlank()
    SaharaScreenSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp).saharaScreenEnter().testTag("invite-family-sheet")) {
            // Title row: the section chip, the task and close. Nothing above the form.
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaharaIconChip(Icons.Default.PersonAdd, SaharaTone.Leaf)
                Spacer(Modifier.width(12.dp))
                Text(t("Invite family or a carer", "परिवार या देखभाल करने वाले को बुलाएँ"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, t("Close", "बंद करें"), tint = DoctorCardSub) }
            }
            Spacer(Modifier.height(12.dp))
            if (cloud == null) {
                Text(t("Saved only on this phone. Sign in to share it with family and the doctor.", "सिर्फ़ इस फ़ोन पर सेव। परिवार और डॉक्टर से साझा करने के लिए साइन इन करें।"), color = DoctorCardSub, modifier = Modifier.testTag("invite-needs-account"))
                Spacer(Modifier.height(12.dp))
                SaharaPrimaryButton(t("Sign in", "साइन इन करें"), onClick = { onDismiss(); onSignIn() }, icon = Icons.Default.AccountCircle, navigates = true, modifier = Modifier.fillMaxWidth())
            } else if (!owner || selected.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(12.dp))
                    Text(t("Only the owner can invite.", "सिर्फ़ मालिक बुला सकते हैं।"), color = DoctorCardSub)
                }
            } else {
                // The form scrolls on its own; the send button stays pinned above the keyboard, so it is never below the fold.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).saharaContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The name field is focused on open and the keyboard's Send key sends on WhatsApp.
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    OutlinedTextField(name, { name = it.take(40); code = "" }, label = { Text(t("Name", "नाम")) }, singleLine = true, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (canSend) send(true) }),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedLabelColor = RustText, unfocusedLabelColor = DoctorCardSub),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("invite-name"))
                    // Family member or a nurse / paid carer (Shirin, 6 Nov). The relation chips are for family only.
                    Text(t("Who are they?", "ये कौन हैं?"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("invite-kind")) {
                        SegmentedButton(selected = kind == CareCaregiverKind.FAMILY, onClick = { kind = CareCaregiverKind.FAMILY; code = "" }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.heightIn(min = 52.dp).testTag("invite-kind-family"),
                            icon = { Icon(Icons.Default.People, null, Modifier.size(18.dp)) }) { Text(t("Family member", "परिवार के सदस्य")) }
                        SegmentedButton(selected = kind == CareCaregiverKind.PAID_CARER, onClick = { kind = CareCaregiverKind.PAID_CARER; relation = ""; code = "" }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.heightIn(min = 52.dp).testTag("invite-kind-carer"),
                            icon = { Icon(Icons.Default.MedicalServices, null, Modifier.size(18.dp)) }) { Text(t("Nurse / paid carer", "नर्स / वेतन पर देखभाल करने वाले")) }
                    }
                    if (kind == CareCaregiverKind.FAMILY) SaharaDropdown(t("Relationship", "रिश्ता"), if (relation.isBlank()) "" else careTr(hindi, relation, CareRelationsHindi[relation] ?: relation),
                        CareRelations.map { r -> SaharaDropdownOption(careTr(hindi, r, CareRelationsHindi[r] ?: r), "add-family-relation-$r", relation == r) },
                        tag = "add-family-relation", placeholder = t("Choose", "चुनें"), onPick = { i -> relation = CareRelations[i] })
                    // Access: one two-way switch (view is the default), not two cards.
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("invite-access")) {
                        SegmentedButton(selected = !canAdd, onClick = { canAdd = false; code = "" }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.heightIn(min = 52.dp).testTag("invite-access-view"),
                            icon = { Icon(Icons.Default.Visibility, null, Modifier.size(18.dp)) }) { Text(t("Can view", "सिर्फ़ देख सकें")) }
                        SegmentedButton(selected = canAdd, onClick = { canAdd = true; code = "" }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.heightIn(min = 52.dp).testTag("invite-access-add"),
                            icon = { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)) }) { Text(t("Can view and add", "देख सकें और जोड़ सकें")) }
                    }
                    // The link itself expires in 3 days; the access it gives ends 30 days after it is made (make_invite passes 30 on the server).
                    Text(t("Access lasts 30 days. Invite them again to renew it.", "पहुँच 30 दिन रहती है। बढ़ाने के लिए फिर से बुलाएँ।"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.testTag("invite-access-length"))
                    // Rarely used: the care-team switch waits behind one "+ More options" chip (it opens still off) unless already on.
                    if (!moreOptions && !careTeam) SaharaAddChip(t("More options", "और विकल्प"), { moreOptions = true }, Modifier.testTag("invite-more-options"), tone = SaharaTone.Leaf)
                    else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(careTeam, role = Role.Switch) { careTeam = it; code = "" }.heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Share care-team contacts", "देखभाल टीम के नंबर भी साझा करें"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
                        Switch(checked = careTeam, onCheckedChange = null)
                    }
                    AnimatedVisibility(code.isNotEmpty(), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                        FamilyPill(t("Invite link made · expires in 3 days", "निमंत्रण लिंक बना · 3 दिन में बंद होगा"), FamilyTone.GOOD, Modifier.testTag("invite-ready"))
                    }
                    error?.let { Row(Modifier.fillMaxWidth().background(DoctorStatusRestBg, RoundedCornerShape(12.dp)).padding(12.dp).announce(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = DoctorStatusRest); Spacer(Modifier.width(8.dp)); Text(it, color = DoctorStatusRest, fontWeight = FontWeight.Medium)
                    } }
                }
                Spacer(Modifier.height(12.dp))
                // One main action that names its outcome; other apps is the quieter way.
                SaharaPrimaryButton(if (busy) t("Please wait…", "कृपया रुकें…") else t("Send invite on WhatsApp", "व्हाट्सऐप पर निमंत्रण भेजें"), onClick = { send(true) }, enabled = canSend, busy = busy,
                    tone = SaharaTone.Leaf, icon = Icons.AutoMirrored.Filled.Send, modifier = Modifier.fillMaxWidth().testTag("invite-send-whatsapp"))
                Spacer(Modifier.height(8.dp))
                SaharaSecondaryButton(t("Share link", "लिंक भेजें"), onClick = { send(false) }, enabled = canSend, icon = Icons.Default.Share, tone = SaharaTone.Leaf, modifier = Modifier.fillMaxWidth())
            }
            if (cloud == null || !owner || selected.isBlank()) error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        }
    }
}

/** Asks before joining from an invite link (the email consent stays word for word), then shows the result in the same small dialog. */
@Composable fun PendingJoinDialog(cloud: CareCloud, vm: CareModel, pending: PendingJoin, email: String, onClear: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; val t = careText(); val careHindiNow = careHindi()
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    result?.let { (joined, text) ->
        AlertDialog(onDismissRequest = onClear, containerColor = Paper, modifier = Modifier.testTag("join-result"),
            icon = { Icon(if (joined) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (joined) DoctorStatusGood else DoctorStatusRest) },
            text = { Text(text, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.announce()) },
            confirmButton = { TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("OK", "ठीक है"), fontWeight = FontWeight.Bold) } })
        return
    }
    AlertDialog(onDismissRequest = { if (!busy) onClear() },
        title = { Text(t("Join ${pending.possessive} care?", "क्या आप इस देखभाल से जुड़ना चाहते हैं?")) },
        text = { Text(t("${pending.who.replaceFirstChar { it.uppercase() }} will see your email ($email).", "बुलाने वाले व्यक्ति को आपका ईमेल ($email) दिखेगा।")) },
        confirmButton = { TextButton(onClick = {
            if (busy) return@TextButton
            busy = true
            scope.launch {
                try {
                    val before = vm.people.value.map { it.id }.toSet()
                    cloud.join(pending.code)
                    val added = vm.joinedRefresh(before)
                    val access = if (added != null && vm.canEditPerson(added.id)) t(" · you can read and add", " · आप देख और जोड़ सकते हैं") else t(" · you can read", " · आप देख सकते हैं")
                    result = true to ((added?.name?.let { t("You can now see $it's care", "अब आप $it की देखभाल देख सकते हैं") } ?: t("You can now see ${pending.possessive} care", "अब आप यह देखभाल देख सकते हैं")) + access)
                } catch (e: Exception) { if (e is CancellationException) throw e; result = false to careJoinFailureMessage(e.message, careHindiNow) }
                finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) t("Joining…", "जुड़ रहे हैं…") else t("Join", "जुड़ें")) } },
        dismissButton = { TextButton(onClick = onClear, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं")) } })
}
