package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun DoctorCareTeamSummary(vm: CareModel, personId: String, onOpen: () -> Unit) {
    val contacts by vm.careContacts.collectAsStateWithLifecycle()
    val readable by vm.canReadCareTeam.collectAsStateWithLifecycle()
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = DoctorCardReport), border = BorderStroke(1.dp, DoctorHairline), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("doctor-care-team-summary")) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Family & care team", fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
        Text(if (readable) "${contacts.count { it.personId == personId }} linked contacts" else "Contacts aren't shared with this account.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
        Text("View care team", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent, modifier = Modifier.heightIn(min = 32.dp))
    } }
}

@Composable fun DoctorCareTeam(vm: CareModel, personId: String) {
    val contacts by vm.careContacts.collectAsStateWithLifecycle()
    val readable by vm.canReadCareTeam.collectAsStateWithLifecycle()
    val loading by vm.careContactsLoading.collectAsStateWithLifecycle()
    val error by vm.careContactsError.collectAsStateWithLifecycle()
    DoctorCareTeamContent(contacts.filter { it.personId == personId }, readable, loading, error, { vm.refreshCareContacts() }) {
        if (vm.cloud == null) CareContactManager(vm, personId)
    }
}

/** Account-free production rendering boundary for accessibility and screenshot fixtures. */
@Composable fun DoctorCareTeamContent(contacts: List<CareContact>, readable: Boolean, loading: Boolean, error: String?, onRefresh: () -> Unit, ownerContent: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("doctor-care-team-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Family & care team", fontFamily = SaharaTitleFont, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
        Text("Only contacts explicitly added to this care profile appear here. Adding a contact does not invite them or give account access.", fontSize = 13.sp, color = DoctorCardSub)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!readable && !loading) Text("The owner has not shared care-team contacts with this account.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
        if (readable && contacts.isEmpty() && !loading) Text("No contacts have been added yet.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
        if (readable) contacts.forEach { contact ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = DoctorCardReport), border = BorderStroke(1.dp, DoctorHairline)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(contact.name, fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                Text("${contact.relationship} · ${contact.role.replaceFirstChar { it.uppercase() }}", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                if (contact.phone.isNotBlank()) Text(contact.phone, fontSize = 13.sp, color = DoctorCardSub)
            } }
        }
        TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh contacts", color = DoctorAccent) }
        ownerContent()
    }
}

@Composable fun CareContactManager(vm: CareModel, personId: String) {
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val contacts by vm.careContacts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.actionError.collectAsStateWithLifecycle()
    val account = remember(personId) { vm.accountNamespace }
    var editing by remember(personId) { mutableStateOf<CareContact?>(null) }
    var removing by remember(personId) { mutableStateOf<CareContact?>(null) }
    if (!owner || selected != personId) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Family & care-team contacts", style = MaterialTheme.typography.titleMedium)
        Text("These contacts stay private unless you explicitly share them below. Contacts do not receive an invitation automatically.", style = MaterialTheme.typography.bodySmall)
        contacts.filter { it.personId == personId }.forEach { contact ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(contact.name); Text(contact.relationship, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { editing = contact }, enabled = !busy) { Text("Edit") }
                TextButton(onClick = { removing = contact }, enabled = !busy) { Text("Remove") }
            }
        }
        OutlinedButton(onClick = { editing = CareContact(newId(), personId, "", "", "family") }, enabled = !busy) { Text("Add contact") }
    }
    editing?.let { contact ->
        var name by remember(contact) { mutableStateOf(contact.name) }
        var relationship by remember(contact) { mutableStateOf(contact.relationship) }
        var role by remember(contact) { mutableStateOf(contact.role) }
        var phone by remember(contact) { mutableStateOf(contact.phone) }
        AlertDialog(onDismissRequest = { if (!busy) editing = null }, title = { Text("Care-team contact") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareField(name, { name = it }, "Name", singleLine = true)
                CareField(relationship, { relationship = it }, "Relationship or care role", singleLine = true)
                CareContactRules.roles.forEach { value -> Row { RadioButton(role == value, { role = value }); Text(value.replaceFirstChar { it.uppercase() }, Modifier.padding(top = 12.dp)) } }
                CareField(phone, { phone = it }, "Phone (optional)", singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = { vm.saveCareContact(contact.copy(name = name, relationship = relationship, role = role, phone = phone), account) { editing = null } }, enabled = !busy && name.isNotBlank() && relationship.isNotBlank()) { Text("Save contact") } }, dismissButton = { TextButton(onClick = { editing = null }, enabled = !busy) { Text("Cancel") } })
    }
    removing?.let { contact -> AlertDialog(onDismissRequest = { if (!busy) removing = null }, title = { Text("Remove ${contact.name}?") }, text = { Text("This removes the contact from this care profile. It does not change anyone's account access or sharing permissions.") }, confirmButton = { TextButton(onClick = { vm.removeCareContact(contact, account) { removing = null } }, enabled = !busy) { Text("Remove contact") } }, dismissButton = { TextButton(onClick = { removing = null }, enabled = !busy) { Text("Keep contact") } }) }
}
