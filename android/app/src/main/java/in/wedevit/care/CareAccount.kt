@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Intent
import android.app.Application
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
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
}

@Composable fun CareRoot(activity: MainActivity) {
    val root = remember { ViewModelProvider(activity)[CareRootModel::class.java] }
    val cloud = root.cloud
    val session by cloud.session.collectAsStateWithLifecycle()
    key(session?.userId.orEmpty()) {
        val store = remember { root.storeFor(session?.userId.orEmpty()) }
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = store } }
        val model = remember { ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = CareModel(activity.application, cloud.takeIf { session != null }) as T
        })[CareModel::class.java] }
        SideEffect { activity.careModel = model }
        var accountOpen by rememberSaveable { mutableStateOf(false) }
        CareApp(model, onAccount = { accountOpen = true })
        if (accountOpen) AccountSheet(cloud, model, onDismiss = { accountOpen = false })
    }
}

@Composable fun AccountSheet(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit) {
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
    var invite by rememberSaveable(selected) { mutableStateOf("") }
    var members by remember(selected) { mutableStateOf<List<CareMember>>(emptyList()) }
    var removing by remember { mutableStateOf<CareMember?>(null) }
    var signingOut by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        scope.launch {
            try { action() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Couldn't finish. Please try again." }
            finally { busy = false }
        }
    }
    LaunchedEffect(selected, owner) {
        if (session != null && owner && selected.isNotBlank()) try { members = cloud.members(selected) }
        catch (e: Exception) { if (e is CancellationException) throw e; error = "Couldn't load the people you've invited." }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading(if (session == null) if (create) "Create an account" else "Sign in" else "Your account", 28)
            if (session == null) {
                Text("Sign in to share care notes and daily care with people you invite.")
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (create) Text("Use at least 10 characters.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { run {
                    if (create) { if (!cloud.signUp(email, password)) { notice = "Check your email to confirm your account, then sign in."; create = false; password = "" } }
                    else cloud.signIn(email, password)
                } }, enabled = !busy && email.contains('@') && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "Please wait…" else if (create) "Create account" else "Sign in") }
                TextButton(onClick = { create = !create; error = null; notice = null }, enabled = !busy) { Text(if (create) "Already have an account? Sign in" else "Create an account") }
            } else {
                Text(session!!.email)
                OutlinedButton(onClick = { vm.refresh(); onDismiss() }, enabled = !busy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Refresh records") }
                DetailPanel("Join someone's care", Icons.Default.GroupAdd) {
                    Text("The person who invited you will see your email.", style = MaterialTheme.typography.bodySmall)
                    CareField(code, { code = it }, "Invitation code", singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { run { cloud.join(code); vm.refresh(); code = ""; notice = "Joined. Their records will appear in your list." } }, enabled = !busy && code.trim().length == 48) { Text("Join") }
                }
                if (owner && selected.isNotBlank()) DetailPanel("Share ${people.firstOrNull { it.id == selected }?.name.orEmpty()}'s care", Icons.Default.PersonAdd) {
                    Text("Invited people can read the care notes and daily entries. Recordings and unfinished conversations aren't shared.")
                    CheckLine(allowChanges, { allowChanges = it }, "Allow them to add and change records")
                    Button(onClick = { run { invite = cloud.invite(selected, allowChanges) } }, enabled = !busy) { Text("Create invitation") }
                    if (invite.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp)); Text("One person · expires in 3 days", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_care_invitation, context.getString(R.string.app_name), invite)), "Share invitation")) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share invitation") }
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(invite, style = MaterialTheme.typography.bodySmall) }
                    }
                    members.forEach { member ->
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f).padding(top = 12.dp)) { Text(member.email); Text(if (member.canEdit) "Can make changes" else "Can read", style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { removing = member }, enabled = !busy) { Text("Remove") }
                        }
                    }
                }
                TextButton(onClick = { signingOut = true }, enabled = !busy) { Text("Sign out") }
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Stop sharing with this person?") }, text = { Text("They won't be able to read or change these records online. Copies they've already saved cannot be recalled.") }, confirmButton = { TextButton(onClick = { removing = null; run { cloud.removeMember(selected, member.userId); members = cloud.members(selected) } }) { Text("Stop sharing") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep sharing") } }) }
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text("Sign out?") }, confirmButton = { TextButton(onClick = { signingOut = false; run { vm.stopRecording(); cloud.signOut() } }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text("Cancel") } })
}
