package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable fun DoctorCareTeamSummary(vm: CareModel, personId: String, onOpen: () -> Unit) { val t = careText();
    val contacts by vm.careContacts.collectAsStateWithLifecycle()
    val readable by vm.canReadCareTeam.collectAsStateWithLifecycle()
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = DoctorCardReport), border = BorderStroke(1.dp, DoctorHairline), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("doctor-care-team-summary").semantics(mergeDescendants = true) { contentDescription = t("View care team", "देखभाल टीम देखें") }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(DoctorCardConversation, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, tint = DoctorAccent, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Family & care team", "परिवार और देखभाल टीम"), fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                Text(if (readable) t("${contacts.count { it.personId == personId }} linked contacts", "${contacts.count { it.personId == personId }} जुड़े संपर्क") else t("Contacts aren't shared with this account.", "संपर्क इस खाते के साथ साझा नहीं हैं।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
            }
            Icon(Icons.Default.ChevronRight, null, tint = DoctorAccent)
        }
    }
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
@Composable fun DoctorCareTeamContent(contacts: List<CareContact>, readable: Boolean, loading: Boolean, error: String?, onRefresh: () -> Unit, ownerContent: @Composable () -> Unit = {}) { val t = careText();
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("doctor-care-team-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Family & care team", "परिवार और देखभाल टीम"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, t("Refresh contacts", "संपर्क ताज़ा करें"), tint = DoctorAccent) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DoctorAccent)
        error?.let { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(it, Modifier.weight(1f).announce(), color = MaterialTheme.colorScheme.error); TextButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Refresh contacts", "संपर्क ताज़ा करें"), color = RustText) } } }
        if (!readable && !loading) CareTeamEmpty(Icons.Default.Lock, t("The owner has not shared care-team contacts with this account.", "मालिक ने देखभाल टीम के संपर्क इस खाते से साझा नहीं किए।"))
        if (readable && contacts.isEmpty() && !loading) CareTeamEmpty(Icons.Default.Groups, t("No contacts have been added yet.", "अभी कोई संपर्क नहीं जोड़ा गया।"))
        if (readable && contacts.isNotEmpty()) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline)) { Column {
            contacts.forEachIndexed { index, contact ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = DoctorHairline)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).background(Peach, CircleShape), contentAlignment = Alignment.Center) { Text(contact.name.trim().take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RustText) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(contact.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                        Text(contact.relationship, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                        if (contact.phone.isNotBlank()) Text(contact.phone, fontSize = 14.sp, color = DoctorCardSub)
                    }
                    Surface(color = DoctorCardConversation, shape = RoundedCornerShape(50)) { Text(contact.role.replaceFirstChar { it.uppercase() }, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk) }
                }
            }
        } }
        ownerContent()
    }
}

@Composable private fun CareTeamEmpty(icon: ImageVector, text: String) {
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = DoctorAccent, modifier = Modifier.size(22.dp))
            Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub)
        }
    }
}

@Composable fun CareContactManager(vm: CareModel, personId: String) { val t = careText();
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
        Text(t("Family & care-team contacts", "परिवार और देखभाल टीम के संपर्क"), style = MaterialTheme.typography.titleMedium, color = DoctorCardInk, modifier = Modifier.padding(top = 12.dp).a11yHeading())
        contacts.filter { it.personId == personId }.forEach { contact ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(contact.name, fontSize = 16.sp, color = DoctorCardInk); Text(contact.relationship, fontSize = 14.sp, color = DoctorCardSub) }
                TextButton(onClick = { editing = contact }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Edit", "बदलें")) }
                TextButton(onClick = { removing = contact }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Remove", "हटाएँ"), color = MaterialTheme.colorScheme.error) }
            }
        }
        OutlinedButton(onClick = { editing = CareContact(newId(), personId, "", "", "family") }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(t("Add contact", "संपर्क जोड़ें")) }
    }
    editing?.let { contact ->
        var name by remember(contact) { mutableStateOf(contact.name) }
        var relationship by remember(contact) { mutableStateOf(contact.relationship) }
        var role by remember(contact) { mutableStateOf(contact.role) }
        var phone by remember(contact) { mutableStateOf(contact.phone) }
        AlertDialog(onDismissRequest = { if (!busy) editing = null }, title = { Text(t("Care-team contact", "देखभाल टीम का संपर्क")) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareField(name, { name = it }, "Name", singleLine = true)
                CareField(relationship, { relationship = it }, t("Relationship or care role", "रिश्ता या देखभाल में भूमिका"), singleLine = true)
                CareContactRules.roles.forEach { value -> Row { RadioButton(role == value, { role = value }); Text(value.replaceFirstChar { it.uppercase() }, Modifier.padding(top = 12.dp)) } }
                CareField(phone, { phone = it }, "Phone (optional)", singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            }
        }, confirmButton = { TextButton(onClick = { vm.saveCareContact(contact.copy(name = name, relationship = relationship, role = role, phone = phone), account) { editing = null } }, enabled = !busy && name.isNotBlank() && relationship.isNotBlank()) { Text(t("Save contact", "संपर्क सहेजें")) } }, dismissButton = { TextButton(onClick = { editing = null }, enabled = !busy) { Text(t("Cancel", "रद्द करें")) } })
    }
    removing?.let { contact -> AlertDialog(onDismissRequest = { if (!busy) removing = null }, title = { Text(t("Remove ${contact.name}?", "${contact.name} को हटाएँ?")) }, text = { Text(t("This removes the contact from this care profile. It does not change anyone's account access or sharing permissions.", "इससे संपर्क इस देखभाल प्रोफ़ाइल से हट जाएगा। किसी के खाते की पहुँच या साझा करने की अनुमति नहीं बदलेगी।")) }, confirmButton = { TextButton(onClick = { vm.removeCareContact(contact, account) { removing = null } }, enabled = !busy) { Text(t("Remove contact", "संपर्क हटाएँ"), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { removing = null }, enabled = !busy) { Text(t("Keep contact", "संपर्क रखें")) } }) }
}
