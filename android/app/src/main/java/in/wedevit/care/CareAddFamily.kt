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
    fun send(whatsApp: Boolean) {
        val service = cloud ?: return
        if (busy) return
        busy = true; error = null
        val person = selected; val account = service.session.value?.userId.orEmpty(); val label = CareInviteLink.cleanName(name)
        scope.launch {
            try {
                var invite = code
                if (invite.isEmpty()) {
                    val before = runCatching { service.members(person).size }.getOrDefault(-1)
                    invite = service.invite(person, canAdd, careTeam)
                    require(CareInviteLink.isCode(invite)) { "The invitation didn't look right. Please try again." }
                    if (vm.selected.value == person && service.session.value?.userId == account && vm.isOwner.value) {
                        code = invite
                        if (before >= 0) careInviteStore(context).add(account, CareInviteEntry(person, label, relation, System.currentTimeMillis(), before))
                        onInvited()
                    }
                }
                shareCareInvite(context, CareInviteLink.shareText(patient, invite), whatsApp)
            } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Couldn't create the invitation. Please try again." }
            finally { busy = false }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading("Add family", 28)
            if (cloud == null) {
                Text("To let family see ${patient.ifBlank { "this person" }}'s care on their own phone, you first need to sign in. It takes a minute.")
                Button(onClick = { onDismiss(); onSignIn() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Sign in or create an account") }
            } else if (!owner || selected.isBlank()) {
                Text("Only the person who set up these records can invite family.")
            } else {
                Text("Let family see $patient's care on their own phone.")
                CareField(name, { name = it.take(40); code = "" }, "Name", singleLine = true, placeholder = "For example, Arjun")
                Text("How are they related?", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CareRelations.forEach { r -> FilterChip(selected = relation == r, onClick = { relation = if (relation == r) "" else r }, modifier = Modifier.heightIn(min = 48.dp), label = { Text(r) }) } }
                Text("What can they do?", style = MaterialTheme.typography.bodyMedium)
                ChoiceRow("Can view", Icons.Default.Visibility, Mint, !canAdd) { canAdd = false; code = "" }
                ChoiceRow("Can view and add", Icons.Default.Edit, Mint, canAdd) { canAdd = true; code = "" }
                CheckLine(careTeam, { careTeam = it; code = "" }, "Share care-team contacts")
                Text("They can read care notes and daily entries. Recordings and unfinished conversations aren't shared. The link works once and expires in 3 days. They will need to sign in, and you will see them listed here once they join.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { send(true) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Please wait…" else "Send invite on WhatsApp") }
                OutlinedButton(onClick = { send(false) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share another way") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
        }
    }
}

/** Asks before joining from an invite link. */
@Composable fun PendingJoinDialog(cloud: CareCloud, vm: CareModel, pending: PendingJoin, email: String, onClear: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onClear() },
        title = { Text("Join ${pending.possessive} care?") },
        text = { Text("${pending.who.replaceFirstChar { it.uppercase() }} will see your email ($email).") },
        confirmButton = { TextButton(onClick = {
            if (busy) return@TextButton
            busy = true
            scope.launch {
                try {
                    val before = vm.people.value.map { it.id }.toSet()
                    cloud.join(pending.code)
                    val added = vm.joinedRefresh(before)
                    vm.message.value = "You can now see ${added?.name?.let { "$it's" } ?: pending.possessive} care"
                } catch (e: Exception) { if (e is CancellationException) throw e; vm.message.value = careJoinFailureMessage(e.message) }
                finally { busy = false; onClear() }
            }
        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) "Joining…" else "Join") } },
        dismissButton = { TextButton(onClick = onClear, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not now") } })
}
