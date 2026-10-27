@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
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
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading(t("Add family", "परिवार जोड़ें"), 28)
            if (cloud == null) {
                Text(t("To let family see ${patient.ifBlank { "this person" }}'s care on their own phone, you first need to sign in. It takes a minute.", "परिवार अपने फ़ोन पर ${patient.ifBlank { "इस व्यक्ति" }} की देखभाल देख सके, इसके लिए पहले साइन इन करें। इसमें बस एक मिनट लगता है।"))
                Button(onClick = { onDismiss(); onSignIn() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text(t("Sign in or create an account", "साइन इन करें या खाता बनाएँ")) }
            } else if (!owner || selected.isBlank()) {
                Text(t("Only the person who set up these records can invite family.", "परिवार को सिर्फ़ वही बुला सकते हैं जिन्होंने ये रिकॉर्ड बनाए हैं।"))
            } else {
                Text(t("Let family see $patient's care on their own phone.", "परिवार अपने फ़ोन पर $patient की देखभाल देख सके।"))
                CareField(name, { name = it.take(40); code = "" }, t("Name", "नाम"), singleLine = true, placeholder = t("For example, Arjun", "जैसे, अर्जुन"))
                Text(t("How are they related?", "उनसे क्या रिश्ता है?"), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CareRelations.forEach { r -> FilterChip(selected = relation == r, onClick = { relation = if (relation == r) "" else r }, modifier = Modifier.heightIn(min = 48.dp), label = { Text(if (hindi) CareRelationsHindi[r] ?: r else r) }) } }
                Text(t("What can they do?", "वे क्या कर सकेंगे?"), style = MaterialTheme.typography.bodyMedium)
                ChoiceRow(t("Can view", "सिर्फ़ देख सकें"), Icons.Default.Visibility, Mint, !canAdd) { canAdd = false; code = "" }
                ChoiceRow(t("Can view and add", "देख सकें और जोड़ सकें"), Icons.Default.Edit, Mint, canAdd) { canAdd = true; code = "" }
                CheckLine(careTeam, { careTeam = it; code = "" }, t("Share care-team contacts", "देखभाल टीम के नंबर भी साझा करें"))
                Text(t("They can read care notes and daily entries. Recordings and unfinished conversations aren't shared. The link works once and expires in 3 days. Their access then lasts 30 days; invite them again to renew it. They will need to sign in, and Your account shows when they have joined.", "वे देखभाल नोट और रोज़ की जानकारी पढ़ सकेंगे। रिकॉर्डिंग और अधूरी बातचीत साझा नहीं होती। लिंक एक ही बार चलता है और 3 दिन में बंद हो जाता है। उसके बाद उनकी पहुँच 30 दिन रहती है; आगे के लिए फिर से निमंत्रण भेजें। उन्हें साइन इन करना होगा, और जुड़ने पर “आपका खाता” में दिखेगा।"), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { send(true) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(if (busy) t("Please wait…", "कृपया रुकें…") else t("Send invite on WhatsApp", "WhatsApp पर निमंत्रण भेजें")) }
                OutlinedButton(onClick = { send(false) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(t("Share another way", "किसी और तरीके से भेजें")) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) }
        }
    }
}

/** Asks before joining from an invite link. */
@Composable fun PendingJoinDialog(cloud: CareCloud, vm: CareModel, pending: PendingJoin, email: String, onClear: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; val t = careText()
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
                    vm.message.value = added?.name?.let { t("You can now see $it's care", "अब आप $it की देखभाल देख सकते हैं") } ?: t("You can now see ${pending.possessive} care", "अब आप यह देखभाल देख सकते हैं")
                } catch (e: Exception) { if (e is CancellationException) throw e; vm.message.value = careJoinFailureMessage(e.message, hindi) }
                finally { busy = false; onClear() }
            }
        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) t("Joining…", "जुड़ रहे हैं…") else t("Join", "जुड़ें")) } },
        dismissButton = { TextButton(onClick = onClear, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं")) } })
}
