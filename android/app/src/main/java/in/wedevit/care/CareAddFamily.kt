@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Mint, modifier = Modifier.size(56.dp)) { Box(contentAlignment = Alignment.Center) { Image(saharaImage(R.drawable.quick_family_art, 40.dp), null, Modifier.size(40.dp), contentScale = ContentScale.Fit) } }
                Spacer(Modifier.width(12.dp))
                Heading(t("Add family", "परिवार जोड़ें"), 28)
            }
            if (cloud == null) {
                run { val who = patient.ifBlank { t("this person", "इस व्यक्ति") }; Text(t("To let family see $who's care on their own phone, you first need to sign in. It takes a minute.", "परिवार अपने फ़ोन पर $who की देखभाल देख सके, इसके लिए पहले साइन इन करें। इसमें बस एक मिनट लगता है।"), color = DoctorCardSub) }
                Button(onClick = { onDismiss(); onSignIn() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text(t("Sign in or create an account", "साइन इन करें या खाता बनाएँ")) }
            } else if (!owner || selected.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(12.dp))
                    Text(t("Only the person who set up these records can invite family.", "परिवार को सिर्फ़ वही बुला सकते हैं जिन्होंने ये रिकॉर्ड बनाए हैं।"), color = DoctorCardSub)
                }
            } else {
                Text(t("Let family see $patient's care on their own phone.", "परिवार अपने फ़ोन पर $patient की देखभाल देख सके।"), color = DoctorCardSub)
                InviteSection(t("Who are you inviting?", "आप किसे बुला रहे हैं?")) {
                    CareField(name, { name = it.take(40); code = "" }, t("Name", "नाम"), singleLine = true, placeholder = t("For example, Arjun", "जैसे, अर्जुन"))
                    Text(t("How are they related?", "उनसे क्या रिश्ता है?"), style = MaterialTheme.typography.bodyMedium, color = DoctorCardSub)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { CareRelations.forEach { r ->
                        FilterChip(selected = relation == r, onClick = { relation = if (relation == r) "" else r }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50),
                            label = { Text(careTr(hindi, r, CareRelationsHindi[r] ?: r)) }, leadingIcon = if (relation == r) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                            colors = FilterChipDefaults.filterChipColors(containerColor = Paper, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = Rust))
                    } }
                }
                InviteSection(t("What can they do?", "वे क्या कर सकेंगे?")) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccessOption(t("Can view", "सिर्फ़ देख सकें"), t("Read notes, medicines and check-ins", "नोट, दवाइयाँ और रोज़ का हाल पढ़ सकें"), Icons.Default.Visibility, !canAdd, Modifier.weight(1f).fillMaxHeight()) { canAdd = false; code = "" }
                        AccessOption(t("Can view and add", "देख सकें और जोड़ सकें"), t("Also tick medicines and add check-ins", "दवा पर टिक और रोज़ का हाल भी जोड़ सकें"), Icons.Default.Edit, canAdd, Modifier.weight(1f).fillMaxHeight()) { canAdd = true; code = "" }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).toggleable(careTeam, role = Role.Switch) { careTeam = it; code = "" }.heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Share care-team contacts", "देखभाल टीम के नंबर भी साझा करें"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
                            Text(t("Doctors' and nurses' phone numbers", "डॉक्टर और नर्स के फ़ोन नंबर"), fontSize = 14.sp, color = DoctorCardSub)
                        }
                        Switch(checked = careTeam, onCheckedChange = null)
                    }
                }
                // The invite rules sit behind one tap, so the sheet leads with the form and the send button.
                InfoDisclosure(t("About this", "इसके बारे में"), SaharaSoftBlue) {
                    listOf(
                        t("The link works once and expires in 3 days.", "लिंक एक बार चलता है और 3 दिन में ख़त्म हो जाता है।"),
                        t("Access lasts 30 days. Invite them again to renew it.", "पहुँच 30 दिन रहती है। बढ़ाने के लिए फिर से बुलाएँ।"),
                        t("Recordings and unfinished conversations are not shared.", "रिकॉर्डिंग और अधूरी बातचीत साझा नहीं होतीं।"),
                        t("They sign in to join. Your account shows when they have.", "वे साइन इन करके जुड़ते हैं। आपके खाते में दिखेगा कि वे जुड़ गए।"),
                    ).forEach { Text("• $it", fontSize = 14.sp, color = DoctorCardInk) }
                }
                Button(onClick = { send(true) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("invite-send-whatsapp"), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DoctorStatusGood, contentColor = Color.White)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.width(8.dp)); Text(if (busy) t("Please wait…", "कृपया रुकें…") else t("Send invite on WhatsApp", "WhatsApp पर निमंत्रण भेजें"), fontWeight = FontWeight.SemiBold)
                }
                if (name.isBlank()) Text(t("Type a name to send the invite.", "निमंत्रण भेजने के लिए नाम लिखें।"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.align(Alignment.CenterHorizontally))
                OutlinedButton(onClick = { send(false) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Share another way", "किसी और तरीके से भेजें")) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterHorizontally)) { Text(t("Close", "बंद करें")) }
        }
    }
}

@Composable private fun InviteSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            content()
        }
    }
}

/** One access choice: an icon, a name and one line on what it allows. */
@Composable private fun AccessOption(title: String, detail: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (selected) Mint else Cream, border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) DoctorAccent else DoctorHairline),
        modifier = modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = if (selected) Paper else Mint) { Icon(icon, null, Modifier.padding(7.dp).size(20.dp), tint = DoctorAccent) }
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = DoctorAccent)
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
            Text(detail, fontSize = 14.sp, color = DoctorCardSub)
        }
    }
}

/** Asks before joining from an invite link. */
@Composable fun PendingJoinDialog(cloud: CareCloud, vm: CareModel, pending: PendingJoin, email: String, onClear: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; val t = careText(); val careHindiNow = careHindi()
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
                    vm.message.value = (added?.name?.let { t("You can now see $it's care", "अब आप $it की देखभाल देख सकते हैं") } ?: t("You can now see ${pending.possessive} care", "अब आप यह देखभाल देख सकते हैं")) + access
                } catch (e: Exception) { if (e is CancellationException) throw e; vm.message.value = careJoinFailureMessage(e.message, careHindiNow) }
                finally { busy = false; onClear() }
            }
        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) t("Joining…", "जुड़ रहे हैं…") else t("Join", "जुड़ें")) } },
        dismissButton = { TextButton(onClick = onClear, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं")) } })
}
