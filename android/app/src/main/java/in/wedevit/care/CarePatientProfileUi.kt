package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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

/**
 * One fixed, compact card for both sides (no sideways scrolling): name, sex · illness, three tiles (age, blood group,
 * height) and a weight tile with its small trend. [profile] null means nothing recorded; [editable] adds "Edit" and "Add ..." prompts.
 */
@Composable fun PatientProfileCard(profile: CarePatientProfile?, name: String, editable: Boolean, onEdit: () -> Unit, modifier: Modifier = Modifier, tag: String = "patient-profile-card") {
    val t = careText()
    if (profile == null) {
        Surface(shape = RoundedCornerShape(12.dp), color = Cream, border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.fillMaxWidth().testTag(tag)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonOutline, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(12.dp))
                Text(t("No profile details yet", "अभी प्रोफ़ाइल की जानकारी नहीं"), fontSize = 14.sp, color = DoctorCardSub)
            }
        }
        return
    }
    var history by remember { mutableStateOf(false) }
    val sex = when (profile.sex) { "Female" -> t("Female", "महिला"); "Male" -> t("Male", "पुरुष"); "Other" -> t("Other", "अन्य"); else -> "" }
    val blood = if (profile.bloodGroup == "Not known") t("Not known", "पता नहीं") else profile.bloodGroup
    val illness = profile.illness.ifBlank { if (editable) t("Add main illness", "मुख्य बीमारी जोड़ें") else "" }
    val contact = listOf(profile.phone, profile.address).filter(String::isNotBlank).joinToString("  ·  ")
    val weights = CareWeightHistory.forCard(profile)
    // Kept short on purpose: the Account tab pins this card above its list, so the screen should fit without scrolling.
    Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A plain initial on a soft colour: no photo is stored.
                Surface(shape = CircleShape, color = SaharaApricot, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = DoctorAccent) } }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = listOf(sex, illness).filter(String::isNotBlank).joinToString(" · ")
                    if (line.isNotBlank()) Text(line, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (editable) OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 12.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.heightIn(min = 48.dp).testTag("patient-profile-edit")) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = DoctorAccent); Spacer(Modifier.width(4.dp)); Text(t("Edit", "बदलें"), color = DoctorAccent, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTile(t("Age", "उम्र"), profile.age?.let { t("$it years", "$it साल") } ?: "—", SaharaSoftBlue, Modifier.weight(1f).fillMaxHeight())
                ProfileTile(t("Blood group", "ख़ून का समूह"), blood.ifBlank { "—" }, Peach.copy(alpha = .6f), Modifier.weight(1f).fillMaxHeight())
                ProfileTile(t("Height", "क़द"), profile.heightCm?.let { "$it ${t("cm", "सेमी")}" } ?: "—", Mint, Modifier.weight(1f).fillMaxHeight())
            }
            ProfileWeightTile(profile.weightKg, weights, onOpen = { history = true }, tag = "$tag-weight")
            // The patient's own card leaves allergies out; a doctor sees them, tinted so they are hard to miss, only when recorded.
            if (!editable && profile.allergies.isNotBlank()) Surface(shape = RoundedCornerShape(12.dp), color = Peach.copy(alpha = .5f), modifier = Modifier.fillMaxWidth().testTag("$tag-allergies")) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, Modifier.size(18.dp), tint = DoctorStatusWarn); Spacer(Modifier.width(8.dp))
                    Text(t("Allergies", "एलर्जी") + ": " + profile.allergies, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorStatusWarn, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (contact.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (profile.phone.isNotBlank()) Icons.Default.Phone else Icons.Default.Place, null, Modifier.size(18.dp), tint = Rust); Spacer(Modifier.width(8.dp))
                Text(contact, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (history) ProfileWeightHistoryDialog(profile.weightKg, weights) { history = false }
}

/** Current weight with a trend badge (change since the weight before it) and a small line of the last few weights. A tap opens the history. */
@Composable private fun ProfileWeightTile(kg: Double?, weights: List<CareWeightEntry>, onOpen: () -> Unit, tag: String) {
    val t = careText()
    val trend = CareWeightHistory.trend(weights); val change = CareWeightHistory.change(weights)
    val value = kg?.let { "${CarePatientProfileRules.weightText(it)} ${t("kg", "किलो")}" } ?: "—"
    val changeText = when (trend) {
        CareWeightTrend.UP -> "▲ ${CareWeightHistory.amountText(change ?: 0.0)} ${t("kg", "किलो")}"
        CareWeightTrend.DOWN -> "▼ ${CareWeightHistory.amountText(change ?: 0.0)} ${t("kg", "किलो")}"
        CareWeightTrend.STEADY -> t("No change", "कोई बदलाव नहीं")
        null -> ""
    }
    val since = weights.takeIf { it.size >= 2 }?.let { it[it.size - 2].day }?.format(careDateFormat("d MMM", careHindi()))
    val open = weights.isNotEmpty()
    Surface(onClick = onOpen, enabled = open, shape = RoundedCornerShape(12.dp), color = SaharaSoftGold, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MonitorWeight, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Weight", "वज़न"), fontSize = 14.sp, color = DoctorCardSub, maxLines = 1); Spacer(Modifier.width(8.dp))
                    Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1)
                }
                // The change as a small badge; the date it is measured from follows in quieter text.
                if (changeText.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(50), color = Paper) { Text(changeText, Modifier.padding(horizontal = 8.dp, vertical = 1.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent, maxLines = 1) }
                    since?.let { Text(" " + t("since $it", "$it से"), fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            val points = CareWeightHistory.recent(weights)
            if (points.size >= 2) { Spacer(Modifier.width(8.dp)); ProfileWeightSparkline(points, Modifier.width(72.dp).height(32.dp)) }
            if (open) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Weight history", "वज़न का इतिहास"), Modifier.size(24.dp), tint = Rust)
        }
    }
}

/** A tiny line of the last few weights, scaled to their own range (no axis: the history dialog has the numbers). */
@Composable private fun ProfileWeightSparkline(points: List<CareWeightEntry>, modifier: Modifier) {
    val line = DoctorAccent
    Canvas(modifier) {
        val low = points.minOf { it.kg }; val high = points.maxOf { it.kg }; val span = (high - low).takeIf { it > 0.0 } ?: 1.0
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        val dots = points.mapIndexed { i, p -> Offset(i * step, (size.height - 4f) - ((p.kg - low) / span).toFloat() * (size.height - 8f)) }
        dots.zipWithNext().forEach { (a, b) -> drawLine(line, a, b, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round) }
        drawCircle(line.copy(alpha = .35f), radius = 2.5.dp.toPx(), center = dots.first())
        drawCircle(line, radius = 3.5.dp.toPx(), center = dots.last())
    }
}

/** Every recorded weight, newest first, with an arrow for the change from the one before. */
@Composable private fun ProfileWeightHistoryDialog(kg: Double?, weights: List<CareWeightEntry>, onDismiss: () -> Unit) {
    val t = careText(); val format = careDateFormat("d MMM yyyy", careHindi())
    val sorted = CareWeightHistory.sorted(weights)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("Weight history", "वज़न का इतिहास")) }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).testTag("patient-profile-weight-history"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The trend at a glance before the list of numbers.
            CareWeightHistory.recent(sorted).takeIf { it.size >= 2 }?.let { points -> Surface(shape = RoundedCornerShape(12.dp), color = SaharaSoftGold, modifier = Modifier.fillMaxWidth()) { ProfileWeightSparkline(points, Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp, vertical = 8.dp)) } }
            CareWeightHistory.changeSinceFirst(sorted)?.let { total ->
                val sign = if (total > 0) "+" else if (total < 0) "−" else ""
                Text(t("Since first", "पहली बार से") + ": $sign${CareWeightHistory.amountText(total)} ${t("kg", "किलो")}", fontWeight = FontWeight.SemiBold, color = DoctorAccent)
            }
            sorted.indices.reversed().forEach { i ->
                val e = sorted[i]; val before = sorted.getOrNull(i - 1)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.day.format(format), Modifier.weight(1f), fontSize = 14.sp, color = DoctorCardSub)
                    Text("${CarePatientProfileRules.weightText(e.kg)} ${t("kg", "किलो")}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    val d = before?.let { kotlin.math.round((e.kg - it.kg) * 10.0) / 10.0 }
                    Text(when { d == null || d == 0.0 -> ""; d > 0 -> " ▲"; else -> " ▼" }, Modifier.widthIn(min = 24.dp), fontSize = 14.sp, color = DoctorAccent)
                }
            }
            if (sorted.isEmpty() && kg != null) Text("${CarePatientProfileRules.weightText(kg)} ${t("kg", "किलो")} · ${t("Date not recorded", "तारीख़ दर्ज नहीं")}", fontSize = 14.sp)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "बंद करें")) } })
}

@Composable private fun ProfileTile(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = color, modifier = modifier) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        // The fields scroll; Save stays pinned at the bottom above the keyboard, so it is never a scroll away.
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp).saharaScreenEnter()) {
          Text(t("Edit profile", "प्रोफ़ाइल बदलें"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
          Spacer(Modifier.height(8.dp))
          Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileStep(1, t("About", "परिचय")) {
                ProfileField(age, { age = it.filter(Char::isDigit).take(3) }, t("Age (years)", "उम्र (साल)"), Icons.Default.Cake, KeyboardType.Number)
                Text(t("Sex", "लिंग"), fontSize = 14.sp, color = DoctorCardSub)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CarePatientProfile.SEX.forEach { v -> ProfileChip(sex == v, sexLabel(v), Modifier.weight(1f)) { sex = if (sex == v) "" else v } } }
            }
            ProfileStep(2, t("Body", "शरीर")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, t("Weight (kg)", "वज़न (किलो)"), Icons.Default.MonitorWeight, KeyboardType.Decimal, Modifier.weight(1f))
                    ProfileField(height, { height = it.filter(Char::isDigit).take(3) }, t("Height (cm)", "क़द (सेमी)"), Icons.Default.Height, KeyboardType.Number, Modifier.weight(1f))
                }
                Text(t("Blood group", "ख़ून का समूह"), fontSize = 14.sp, color = DoctorCardSub)
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
            error?.let { Text(t(it.en, it.hi), color = RustText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.announce()) }
          }
          Spacer(Modifier.height(12.dp))
          // One Save; swiping the sheet down or Back cancels.
          SaharaPrimaryButton(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save", "सहेजें"), onClick = {
              val a = CarePatientProfileRules.age(age); val w = CarePatientProfileRules.weight(weight); val h = CarePatientProfileRules.height(height)
              val bad = a.second ?: w.second ?: h.second
              if (bad != null) error = bad
              else onSave(CarePatientProfile(a.first, sex, w.first, h.first, blood, illness.trim(), allergies.trim(), phone.trim(), address.trim(), CareWeightHistory.afterSave(initial, w.first, java.time.LocalDate.now())))
          }, busy = busy, modifier = Modifier.fillMaxWidth().testTag("patient-profile-save"))
        }
    }
}


/** One group of the profile editor: a section header and its fields, no card around them (no card inside the sheet). */
@Composable private fun ProfileStep(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    val tone = when (number) { 1 -> SaharaTone.Teal; 2 -> SaharaTone.Indigo; 3 -> SaharaTone.Plum; else -> SaharaTone.Leaf }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SaharaSectionHeader(title, tone = tone)
        content()
    }
}

/** A rounded text field with a small leading icon. */
@Composable private fun ProfileField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value, onChange, modifier, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = DoctorAccent) },
        singleLine = keyboard != KeyboardType.Text, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper))
}

/** A choice chip that shows a tick and a soft fill when picked. */
@Composable private fun ProfileChip(selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) },
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null, shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(containerColor = Cream, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = DoctorAccent),
        modifier = modifier.heightIn(min = 48.dp))
}
