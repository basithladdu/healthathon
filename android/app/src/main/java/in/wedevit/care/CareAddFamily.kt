@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var canAdd by rememberSaveable { mutableStateOf(false) }; var careTeam by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val sendPress = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
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
                        careInviteStore(context).logCreated(service, account, person, label, relation, "family", before)
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
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("invite-family-sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Title row: a solid badge, the task, and close. No art or second heading pushing the form down.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = FamilyAccent.Leaf, contentColor = Color.White, modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PersonAdd, null, Modifier.size(22.dp)) } }
                Spacer(Modifier.width(12.dp))
                Text(t("Invite family", "परिवार को बुलाएँ"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, t("Close", "बंद करें"), tint = DoctorCardSub) }
            }
            if (cloud == null) {
                Button(onClick = { onDismiss(); onSignIn() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text(t("Sign in", "साइन इन करें")) }
            } else if (!owner || selected.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(12.dp))
                    Text(t("Only the owner can invite.", "सिर्फ़ मालिक बुला सकते हैं।"), color = DoctorCardSub)
                }
            } else {
                // The name field is focused on open and the keyboard's Send key sends on WhatsApp: no tap on the field, no hunting for the button.
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                OutlinedTextField(name, { name = it.take(40); code = "" }, label = { Text(t("Name", "नाम")) }, singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (canSend) send(true) }),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedLabelColor = RustText, unfocusedLabelColor = DoctorCardSub),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("invite-name"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { CareRelations.forEach { r ->
                    FilterChip(selected = relation == r, onClick = { relation = if (relation == r) "" else r }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50),
                        label = { Text(careTr(hindi, r, CareRelationsHindi[r] ?: r), softWrap = false) }, leadingIcon = if (relation == r) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                        colors = FilterChipDefaults.filterChipColors(containerColor = Paper, selectedContainerColor = FamilyAccent.Leaf, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White))
                } }
                // Access: two choices side by side (view is the default), then the one switch.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccessOption(t("Can view", "सिर्फ़ देख सकें"), Icons.Default.Visibility, !canAdd, Modifier.weight(1f).fillMaxHeight()) { canAdd = false; code = "" }
                    AccessOption(t("Can view and add", "देख सकें और जोड़ सकें"), Icons.Default.Edit, canAdd, Modifier.weight(1f).fillMaxHeight()) { canAdd = true; code = "" }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(careTeam, role = Role.Switch) { careTeam = it; code = "" }.heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Share care-team contacts", "देखभाल टीम के नंबर भी साझा करें"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
                    Switch(checked = careTeam, onCheckedChange = null)
                }
                AnimatedVisibility(code.isNotEmpty(), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                    FamilyPill(t("Invite link made · expires in 3 days", "निमंत्रण लिंक बना · 3 दिन में बंद होगा"), FamilyTone.GOOD, Modifier.testTag("invite-ready"))
                }
                error?.let { Row(Modifier.fillMaxWidth().background(DoctorStatusRestBg, RoundedCornerShape(12.dp)).padding(12.dp).announce(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = DoctorStatusRest); Spacer(Modifier.width(8.dp)); Text(it, color = DoctorStatusRest, fontWeight = FontWeight.Medium)
                } }
                // One main action; other apps is the quieter way.
                Button(onClick = { send(true) }, enabled = canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).saharaPress(sendPress).testTag("invite-send-whatsapp"), shape = RoundedCornerShape(12.dp), interactionSource = sendPress,
                    colors = ButtonDefaults.buttonColors(containerColor = DoctorStatusGood, contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.width(8.dp)); Text(if (busy) t("Please wait…", "कृपया रुकें…") else t("Send on WhatsApp", "व्हाट्सऐप पर भेजें"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                OutlinedButton(onClick = { send(false) }, enabled = canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, if (canSend) DoctorCardInk else DoctorHairline)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Share link", "लिंक भेजें"), fontWeight = FontWeight.SemiBold) }
            }
            if (cloud == null || !owner || selected.isBlank()) error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        }
    }
}

/** One access choice: an icon and a name. */
@Composable private fun AccessOption(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (selected) DoctorStatusGoodBg else Paper, border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) DoctorStatusGood else DoctorHairline),
        modifier = modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = if (selected) DoctorStatusGood else Cream) { Icon(icon, null, Modifier.padding(7.dp).size(20.dp), tint = if (selected) Color.White else DoctorCardSub) }
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = DoctorStatusGood)
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
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
