package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The patient's own profile card (Account tab) with its edit sheet. The profile lives in the shared care record, so family and doctors see it. */
@Composable fun PatientProfileCard(vm: CareModel, person: Person) {
    val context = LocalContext.current
    val canEdit by vm.canEdit.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val capturedAccount = remember(person.id) { vm.accountNamespace }
    val store = remember(vm.storageNamespace) { CarePatientProfileStore(context, vm.storageNamespace) }
    val sharedItem = remember(items, person.id) { PatientProfileCodec.latest(items, person.id) }
    // A phone-only profile saved before sharing existed is still shown until the first shared save replaces it.
    val profile = remember(sharedItem, person.id, store) { sharedItem?.let { PatientProfileCodec.decode(it.detail) } ?: store.loadOrNull(person.id) ?: CarePatientProfile() }
    var editing by remember { mutableStateOf(false) }
    PatientProfileCard(profile, person.name, editable = canEdit, onEdit = { editing = true })
    if (editing && canEdit) PatientProfileSheet(profile, shared = vm.cloud != null, busy = busy, onDismiss = { editing = false }) { saved ->
        if (canEditCareContext(vm, person.id, capturedAccount)) {
            val done = { runCatching { store.remove(person.id) }; editing = false }
            if (sharedItem != null) vm.updateItem(sharedItem.copy(detail = PatientProfileCodec.encode(saved)), done)
            else vm.saveItem("journal", PatientProfileCodec.TITLE, PatientProfileCodec.encode(saved), done = done)
        }
    }
}

/** The doctor's read-only view of the same card; a compact note when nobody has filled the profile in yet. */
@Composable fun DoctorPatientProfileCard(items: List<CareItem>, person: Person, modifier: Modifier = Modifier) {
    val profile = remember(items, person.id) { PatientProfileCodec.latest(items, person.id)?.let { PatientProfileCodec.decode(it.detail) } }
    PatientProfileCard(profile, person.name, editable = false, onEdit = {}, modifier = modifier, tag = "doctor-patient-profile-card")
}

/** One card for both sides. [profile] null means nothing recorded (compact card); [editable] adds the edit controls and "Add ..." prompts. */
@Composable fun PatientProfileCard(profile: CarePatientProfile?, name: String, editable: Boolean, onEdit: () -> Unit, modifier: Modifier = Modifier, tag: String = "patient-profile-card") {
    val t = careText()
    if (profile == null) {
        Surface(shape = RoundedCornerShape(16.dp), color = Cream, border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.fillMaxWidth().testTag(tag)) {
            Text(t("No profile details yet", "अभी प्रोफ़ाइल की जानकारी नहीं"), Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 14.sp, color = DoctorCardSub)
        }
        return
    }
    fun dash(s: String) = s.ifBlank { "—" }
    val sex = when (profile.sex) { "Female" -> t("Female", "महिला"); "Male" -> t("Male", "पुरुष"); "Other" -> t("Other", "अन्य"); else -> "" }
    val blood = if (profile.bloodGroup == "Not known") t("Not known", "पता नहीं") else profile.bloodGroup
    val illness = profile.illness.ifBlank { if (editable) t("Add main illness", "मुख्य बीमारी जोड़ें") else "" }
    val address = profile.address.ifBlank { if (editable) t("Add address", "पता जोड़ें") else "" }
    val hasAllergies = profile.allergies.isNotBlank()
    Surface(shape = RoundedCornerShape(24.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // A plain initial on a soft colour: no photo is stored.
                Surface(shape = RoundedCornerShape(22.dp), color = SaharaApricot, modifier = Modifier.size(96.dp)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 40.sp, color = DoctorAccent) } }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = CircleShape, color = Mint) { Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(Color(0xFF2E9E5B), CircleShape)); Spacer(Modifier.width(6.dp))
                        Text(t("Patient", "मरीज़"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk) } }
                    Text(name, fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (illness.isNotBlank()) Text(illness, fontSize = 14.sp, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTile(t("Age", "उम्र"), profile.age?.let { t("$it yrs", "$it साल") } ?: "—")
                ProfileTile(t("Sex", "लिंग"), dash(sex))
                ProfileTile(t("Weight", "वज़न"), profile.weightKg?.let { "${CarePatientProfileRules.weightText(it)} ${t("kg", "किलो")}" } ?: "—")
                ProfileTile(t("Height", "क़द"), profile.heightCm?.let { "$it ${t("cm", "सेमी")}" } ?: "—")
                ProfileTile(t("Blood group", "ख़ून का समूह"), dash(blood))
            }
            // The patient's own card leaves allergies out; a doctor sees them, tinted so they are hard to miss, only when recorded.
            if (!editable && hasAllergies) Surface(shape = RoundedCornerShape(16.dp), color = if (hasAllergies) Peach.copy(alpha = .5f) else Cream, modifier = Modifier.fillMaxWidth().testTag("$tag-allergies")) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(t("Allergies", "एलर्जी"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (hasAllergies) DoctorStatusWarn else DoctorCardSub)
                    Text(if (hasAllergies) profile.allergies else t("No allergies recorded", "कोई एलर्जी दर्ज नहीं"), fontSize = 15.sp, fontWeight = if (hasAllergies) FontWeight.SemiBold else FontWeight.Normal, color = if (hasAllergies) DoctorStatusWarn else DoctorCardSub)
                }
            }
            if (profile.phone.isNotBlank()) ProfileInfoRow(Icons.Default.Phone, profile.phone)
            if (address.isNotBlank()) Surface(shape = RoundedCornerShape(16.dp), color = Cream, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, Modifier.size(20.dp), tint = Rust); Spacer(Modifier.width(10.dp))
                    Text(address, Modifier.weight(1f), fontSize = 14.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (editable) IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Surface(shape = CircleShape, color = SaharaApricot, modifier = Modifier.size(36.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Edit profile", "प्रोफ़ाइल बदलें"), Modifier.size(18.dp), tint = DoctorAccent) } } }
                }
            }
            if (editable) OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("patient-profile-edit"), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline)) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Edit profile", "प्रोफ़ाइल बदलें"), color = DoctorCardInk)
            }
        }
    }
}

@Composable private fun ProfileInfoRow(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Cream, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = Rust); Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), fontSize = 14.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun ProfileTile(label: String, value: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline)) {
        Column(Modifier.widthIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp, color = DoctorCardSub, maxLines = 1)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun PatientProfileSheet(initial: CarePatientProfile, shared: Boolean, busy: Boolean, onDismiss: () -> Unit, onSave: (CarePatientProfile) -> Unit) {
    val t = careText()
    var age by remember { mutableStateOf(initial.age?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(initial.sex) }
    var weight by remember { mutableStateOf(CarePatientProfileRules.weightText(initial.weightKg)) }
    var height by remember { mutableStateOf(initial.heightCm?.toString().orEmpty()) }
    var blood by remember { mutableStateOf(initial.bloodGroup) }
    var illness by remember { mutableStateOf(initial.illness) }
    var allergies by remember { mutableStateOf(initial.allergies) }
    var phone by remember { mutableStateOf(initial.phone) }
    var address by remember { mutableStateOf(initial.address) }
    var error by remember { mutableStateOf<ProfileError?>(null) }
    fun sexLabel(v: String) = when (v) { "Female" -> t("Female", "महिला"); "Male" -> t("Male", "पुरुष"); else -> t("Other", "अन्य") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Edit profile", "प्रोफ़ाइल बदलें"), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            ProfileStep(1, t("About", "परिचय")) {
                ProfileField(age, { age = it.filter(Char::isDigit).take(3) }, t("Age (years)", "उम्र (साल)"), Icons.Default.Cake, KeyboardType.Number)
                Text(t("Sex", "लिंग"), fontSize = 13.sp, color = DoctorCardSub)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CarePatientProfile.SEX.forEach { v -> ProfileChip(sex == v, sexLabel(v), Modifier.weight(1f)) { sex = if (sex == v) "" else v } } }
            }
            ProfileStep(2, t("Body", "शरीर")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, t("Weight (kg)", "वज़न (किलो)"), Icons.Default.MonitorWeight, KeyboardType.Decimal, Modifier.weight(1f))
                    ProfileField(height, { height = it.filter(Char::isDigit).take(3) }, t("Height (cm)", "क़द (सेमी)"), Icons.Default.Height, KeyboardType.Number, Modifier.weight(1f))
                }
                Text(t("Blood group", "ख़ून का समूह"), fontSize = 13.sp, color = DoctorCardSub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { CarePatientProfile.BLOOD.forEach { v -> ProfileChip(blood == v, if (v == "Not known") t("Not known", "पता नहीं") else v) { blood = if (blood == v) "" else v } } }
            }
            ProfileStep(3, t("Health", "सेहत")) {
                ProfileField(illness, { illness = it.take(120) }, t("Main illness / diagnosis", "मुख्य बीमारी / निदान"), Icons.Default.MedicalInformation)
                ProfileField(allergies, { allergies = it.take(200) }, t("Allergies (optional)", "एलर्जी (ज़रूरी नहीं)"), Icons.Default.WarningAmber)
            }
            ProfileStep(4, t("Contact", "संपर्क")) {
                ProfileField(phone, { phone = it.take(20) }, t("Phone", "फ़ोन"), Icons.Default.Phone, KeyboardType.Phone)
                ProfileField(address, { address = it.take(160) }, t("City / address", "शहर / पता"), Icons.Default.Place)
            }
            error?.let { Text(t(it.en, it.hi), color = RustText, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            Text(if (shared) t("Shared with the family and doctors you share this care with.", "उन परिवारजनों और डॉक्टरों के साथ साझा, जिनके साथ आप यह देखभाल साझा करते हैं।") else t("Saved on this phone only.", "सिर्फ़ इसी फ़ोन पर सहेजा जाता है।"), fontSize = 13.sp, color = DoctorCardSub)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(50)) { Text(t("Cancel", "रद्द करें")) }
                Button(onClick = {
                    val a = CarePatientProfileRules.age(age); val w = CarePatientProfileRules.weight(weight); val h = CarePatientProfileRules.height(height)
                    val bad = a.second ?: w.second ?: h.second
                    if (bad != null) error = bad
                    else onSave(CarePatientProfile(a.first, sex, w.first, h.first, blood, illness.trim(), allergies.trim(), phone.trim(), address.trim()))
                }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("patient-profile-save"), shape = RoundedCornerShape(50)) { Text(t("Save", "सहेजें")) }
            }
        }
    }
}


/** One numbered group of the profile editor on its own card. */
@Composable private fun ProfileStep(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = DoctorAccent, modifier = Modifier.size(26.dp)) { Box(contentAlignment = Alignment.Center) { Text("$number", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
                Spacer(Modifier.width(10.dp))
                Text(title, fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            }
            content()
        }
    }
}

/** A rounded text field with a small leading icon. */
@Composable private fun ProfileField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value, onChange, modifier, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = DoctorAccent) },
        singleLine = keyboard != KeyboardType.Text, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Cream, focusedContainerColor = Paper))
}

/** A choice chip that shows a tick and a soft fill when picked. */
@Composable private fun ProfileChip(selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) },
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null, shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(containerColor = Cream, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = DoctorAccent),
        modifier = modifier.heightIn(min = 48.dp))
}
