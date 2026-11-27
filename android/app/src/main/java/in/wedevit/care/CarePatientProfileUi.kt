package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

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
    var addingWeight by remember { mutableStateOf(false) }
    val savedHaptic = rememberSaharaHaptic()
    // Saves a profile to the shared record (or creates it), then clears the old phone-only copy.
    fun save(saved: CarePatientProfile, done: () -> Unit) {
        if (!canEditCareContext(vm, person.id, capturedAccount)) return
        val finished = { runCatching { store.remove(person.id) }; savedHaptic(); done() }
        if (sharedItem != null) vm.updateItem(sharedItem.copy(detail = PatientProfileCodec.encode(saved)), finished)
        else vm.saveItem("journal", PatientProfileCodec.TITLE, PatientProfileCodec.encode(saved), done = finished)
    }
    PatientProfileCard(profile, person.name, editable = canEdit, onEdit = { editing = true }, onAddWeight = if (canEdit) ({ addingWeight = true }) else null)
    if (addingWeight && canEdit) AddWeightDialog(profile.weightKg, busy = busy, onDismiss = { addingWeight = false }) { kg ->
        save(CareWeightHistory.addWeight(profile, kg, java.time.LocalDate.now())) { addingWeight = false }
    }
    if (editing && canEdit) PatientProfileSheet(profile, shared = vm.cloud != null, busy = busy, onDismiss = { editing = false }) { saved -> save(saved) { editing = false } }
}

/**
 * Today's weight on one scroll wheel, 1-300 kg in half kilos (owner, 25 Nov: no typed numbers for families). It opens on the last
 * weight, so a small change is a short turn. Not opening the whole profile editor (Dr Sujay, 22 Nov review).
 */
@Composable private fun AddWeightDialog(current: Double?, busy: Boolean, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val t = careText()
    val scale = CarePickerScales.WEIGHT_KG
    val opensOn = current ?: CarePickerScales.WEIGHT_START
    var index by remember { mutableIntStateOf(scale.indexOf(opensOn)) }
    // An unturned wheel saves the last weight exactly (a typed 62.3 is not snapped to 62.5).
    val kg = scale.picked(opensOn, index)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, modifier = Modifier.testTag("patient-profile-add-weight-dialog"),
        title = { Text(t("Add today's weight", "आज का वज़न लिखें")) },
        text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            current?.let { Text(t("Last weight", "पिछला वज़न") + ": ${CarePatientProfileRules.weightText(it)} " + t("kg", "किलो"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.fillMaxWidth()) }
            Text(scale.text(kg) + " " + t("kg", "किलो"), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("patient-profile-add-weight-shown"))
            CareValueWheel(scale.count, index, { scale.text(scale.valueAt(it)) }, { index = it }, t("Weight (kg)", "वज़न (किलो)"), "patient-profile-add-weight-field", Modifier.fillMaxWidth(.6f))
        } },
        confirmButton = { TextButton(onClick = { onSave(kg) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("patient-profile-add-weight-save")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save", "सहेजें"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें")) } })
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
@Composable fun PatientProfileCard(profile: CarePatientProfile?, name: String, editable: Boolean, onEdit: () -> Unit, modifier: Modifier = Modifier, tag: String = "patient-profile-card", onAddWeight: (() -> Unit)? = null) {
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
    val contact = remember(profile.phone, profile.address) { listOf(profile.phone, profile.address).filter(String::isNotBlank).joinToString("  ·  ") }
    val weights = remember(profile) { CareWeightHistory.forCard(profile) }
    // The person's own card (More tab, owner 26 Nov): a centred profile header, then one "Health details" card.
    if (editable) {
        PatientOwnProfileCard(profile, name, listOf(sex, illness).filter(String::isNotBlank).joinToString(" · "), blood, contact, weights, onEdit, onAddWeight, { history = true }, modifier, tag)
        if (history) ProfileWeightHistoryDialog(profile.weightKg, weights) { history = false }
        return
    }
    // Kept short on purpose: the Account tab pins this card above its list, so the screen should fit without scrolling.
    Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.fillMaxWidth().testTag(tag)) {
        // No size animation on the whole card (owner, 24 Nov: cards "keep expanding"); its rows change in place.
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A plain initial on a soft colour: no photo is stored.
                Surface(shape = CircleShape, color = SaharaApricot, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = DoctorAccent) } }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = listOf(sex, illness).filter(String::isNotBlank).joinToString(" · ")
                    if (line.isNotBlank()) Text(line, fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.testTag("$tag-diagnosis"))
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
            // One tap to log today's weight without opening the whole editor.
            if (editable && onAddWeight != null) OutlinedButton(onClick = onAddWeight, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 14.dp), border = BorderStroke(1.dp, DoctorHairline),
                modifier = Modifier.heightIn(min = 48.dp).testTag("patient-profile-add-weight")) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = DoctorAccent); Spacer(Modifier.width(4.dp)); Text(t("Add weight", "वज़न जोड़ें"), color = DoctorAccent, fontWeight = FontWeight.SemiBold)
            }
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
            // The care now (Dr Shirin, 23 Nov): since when, the doctor seen now and the treatment now; only what is filled in.
            if (profile.hasCareNow) ProfileCareNow(profile, Modifier.testTag("$tag-care-now"))
        }
    }
    if (history) ProfileWeightHistoryDialog(profile.weightKg, weights) { history = false }
}

/**
 * The person's own profile (owner, 26 Nov, after a banking-app reference): a large initial in the centre with the edit
 * button at the top right, the name, "sex · illness", the place as a small green line, then a "Health details" card with
 * age, blood group and height in columns and the weight with its trend. Same tags as the compact card, so tests still find it.
 */
@Composable private fun PatientOwnProfileCard(profile: CarePatientProfile, name: String, line: String, blood: String, contact: String, weights: List<CareWeightEntry>,
    onEdit: () -> Unit, onAddWeight: (() -> Unit)?, onHistory: () -> Unit, modifier: Modifier, tag: String) {
    val t = careText()
    Column(modifier.fillMaxWidth().testTag(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Surface(shape = CircleShape, color = SaharaApricot, border = BorderStroke(3.dp, Paper), shadowElevation = 2.dp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(88.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent) }
            }
            Surface(onClick = onEdit, shape = CircleShape, color = Mint, modifier = Modifier.align(Alignment.TopEnd).size(48.dp).testTag("patient-profile-edit").semantics { contentDescription = t("Edit profile", "प्रोफ़ाइल बदलें") }) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, null, Modifier.size(22.dp), tint = DoctorAccent) }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (line.isNotBlank()) Text(line, fontSize = 15.sp, color = DoctorCardSub, textAlign = TextAlign.Center, modifier = Modifier.testTag("$tag-diagnosis"))
            if (contact.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (profile.phone.isNotBlank()) Icons.Default.Phone else Icons.Default.Place, null, Modifier.size(16.dp), tint = DoctorAccent); Spacer(Modifier.width(4.dp))
                Text(contact, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Surface(shape = RoundedCornerShape(HealthStyle.CardRadius), color = Paper, shadowElevation = 1.5.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthCardLabel(HealthCategory.Weight, t("Health details", "सेहत की जानकारी"), Modifier.weight(1f))
                    if (weights.isNotEmpty()) Text(t("Weight history", "वज़न का इतिहास"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onHistory).padding(horizontal = 4.dp, vertical = 12.dp))
                }
                                Row(Modifier.fillMaxWidth()) {
                    ProfileDetail(t("Age", "उम्र"), profile.age?.let { t("$it years", "$it साल") } ?: "—", Modifier.weight(1f))
                    ProfileDetail(t("Blood group", "ख़ून का समूह"), blood.ifBlank { "—" }, Modifier.weight(1f))
                    ProfileDetail(t("Height", "क़द"), profile.heightCm?.let { "$it ${t("cm", "सेमी")}" } ?: "—", Modifier.weight(1f))
                }
                ProfileWeightTile(profile.weightKg, weights, onOpen = onHistory, tag = "$tag-weight")
                if (onAddWeight != null) HealthTintButton(t("Add weight", "वज़न जोड़ें"), HealthCategory.Weight, onAddWeight, Modifier.testTag("patient-profile-add-weight"))
                if (profile.hasCareNow) ProfileCareNow(profile, Modifier.testTag("$tag-care-now"))
            }
        }
    }
}

/** One label over its value, like the reference's "Account balance / ₦76,000" columns. */
@Composable private fun ProfileDetail(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 13.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Since when, the doctor now and the treatment now, one quiet row each (wraps rather than cuts, so a doctor reads it all). */
@Composable private fun ProfileCareNow(profile: CarePatientProfile, modifier: Modifier = Modifier) {
    val t = careText()
    val doctor = careDoctorLine(profile)
    val treatment = careTreatmentLine(profile, t)
    Surface(shape = RoundedCornerShape(12.dp), color = Cream, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOfNotNull(
                Triple(Icons.Default.Event, careIllnessSinceLabel(profile.illnessSince, t), careIllnessSinceText(profile.illnessSince, t)).takeIf { profile.illnessSince.isNotBlank() },
                Triple(Icons.Default.LocalHospital, t("Doctor now", "अभी के डॉक्टर"), doctor).takeIf { doctor.isNotBlank() },
                Triple(Icons.Default.Healing, t("Treatment now", "अभी का इलाज"), treatment).takeIf { treatment.isNotBlank() },
            ).forEach { (icon, label, value) ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, null, Modifier.size(18.dp), tint = DoctorAccent); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 13.sp, color = DoctorCardSub)
                        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    }
                }
            }
        }
    }
}

/** Current weight with a trend badge (change since the weight before it) and a small line of the last few weights. A tap opens the history. */
@Composable private fun ProfileWeightTile(kg: Double?, weights: List<CareWeightEntry>, onOpen: () -> Unit, tag: String) {
    val t = careText()
    val trend = remember(weights) { CareWeightHistory.trend(weights) }; val change = remember(weights) { CareWeightHistory.change(weights) }
    val value = kg?.let { "${CarePatientProfileRules.weightText(it)} ${t("kg", "किलो")}" } ?: "—"
    val changeText = when (trend) {
        CareWeightTrend.UP -> "▲ ${CareWeightHistory.amountText(change ?: 0.0)} ${t("kg", "किलो")}"
        CareWeightTrend.DOWN -> "▼ ${CareWeightHistory.amountText(change ?: 0.0)} ${t("kg", "किलो")}"
        CareWeightTrend.STEADY -> t("No change", "कोई बदलाव नहीं")
        null -> ""
    }
    val hindi = careHindi()
    val since = remember(weights, hindi) { weights.takeIf { it.size >= 2 }?.let { it[it.size - 2].day }?.format(careDateFormat("d MMM", hindi)) }
    val open = weights.isNotEmpty()
    Surface(onClick = onOpen, enabled = open, shape = RoundedCornerShape(12.dp), color = SaharaSoftGold, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MonitorWeight, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Weight", "वज़न"), fontSize = 14.sp, color = DoctorCardSub, maxLines = 1); Spacer(Modifier.width(8.dp))
                    // A new weight rolls in rather than jumping.
                    SaharaAnimatedText(value) { Text(it, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1) }
                }
                // The change as a small badge; the date it is measured from follows in quieter text.
                if (changeText.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(50), color = Paper) { Text(changeText, Modifier.padding(horizontal = 8.dp, vertical = 1.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent, maxLines = 1) }
                    since?.let { Text(" " + t("since $it", "$it से"), fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            val points = remember(weights) { CareWeightHistory.recent(weights) }
            if (points.size >= 2) { Spacer(Modifier.width(8.dp)); ProfileWeightSparkline(points, Modifier.width(72.dp).height(32.dp)) }
            if (open) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Weight history", "वज़न का इतिहास"), Modifier.size(24.dp), tint = Rust)
        }
    }
}

/** A tiny line of the last few weights, scaled to their own range (no axis: the history dialog has the numbers). */
@Composable private fun ProfileWeightSparkline(points: List<CareWeightEntry>, modifier: Modifier) {
    val line = Rust
    Canvas(modifier) {
        val low = points.minOf { it.kg }; val high = points.maxOf { it.kg }; val span = (high - low).takeIf { it > 0.0 } ?: 1.0
        val pad = 6.dp.toPx()
        val dots = points.mapIndexed { i, p -> Offset(pad + i * ((size.width - 2 * pad) / (points.size - 1).coerceAtLeast(1)), (size.height - pad) - ((p.kg - low) / span).toFloat() * (size.height - 2 * pad)) }
        // Same look as the distress and symptom charts: a rounded rust line, white-ringed dots, and a ring on the latest.
        dots.zipWithNext().forEach { (a, b) -> drawLine(line, a, b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round) }
        dots.forEach { drawCircle(Color.White, 4.dp.toPx(), it); drawCircle(line, 3.dp.toPx(), it) }
        dots.last().let { drawCircle(Color.White, 6.dp.toPx(), it); drawCircle(line, 6.dp.toPx(), it, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())); drawCircle(line, 3.5.dp.toPx(), it) }
    }
}

/** Every recorded weight, newest first, with an arrow for the change from the one before. */
@Composable private fun ProfileWeightHistoryDialog(kg: Double?, weights: List<CareWeightEntry>, onDismiss: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val format = remember(hindi) { careDateFormat("d MMM yyyy", hindi) }
    val sorted = remember(weights) { CareWeightHistory.sorted(weights) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("Weight history", "वज़न का इतिहास")) }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).testTag("patient-profile-weight-history"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The trend at a glance before the list of numbers.
            sorted.takeIf { it.size >= 2 }?.let { all ->
                val lo = kotlin.math.floor(all.minOf { it.kg } - 1.0); val hi = kotlin.math.ceil(all.maxOf { it.kg } + 1.0)
                val pts = remember(all) { all.map { ChartPoint(careEpoch(it.day).toDouble(), it.kg) } }
                val kgUnit = t("kg", "किलो")
                CareTimeLineChart(points = pts, yMin = lo, yMax = hi, yTicks = listOf(lo, (lo + hi) / 2.0, hi), yText = { it.toInt().toString() },
                    description = all.joinToString("; ") { it.day.format(format) + ": " + CarePatientProfileRules.weightText(it.kg) + " " + kgUnit },
                    tag = "patient-profile-weight", height = 170.dp, pointText = { CarePatientProfileRules.weightText(it) },
                    latestText = { CarePatientProfileRules.weightText(it) + " " + kgUnit },
                    bands = listOf(ChartBand(lo, hi, Rust, 0.05f)))
            }
            CareWeightHistory.changeSinceFirst(sorted)?.let { total ->
                val sign = if (total > 0) "+" else if (total < 0) "−" else ""
                Text(t("Since first", "पहली बार से") + ": $sign${CareWeightHistory.amountText(total)} ${t("kg", "किलो")}", fontWeight = FontWeight.SemiBold, color = DoctorAccent)
            }
            // The newest five weights, then "Show older" (24 Nov).
            val weightsShown = rememberShownCount()
            val weightRow: @Composable (Int) -> Unit = { i ->
                val e = sorted[i]; val before = sorted.getOrNull(i - 1)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.day.format(format), Modifier.weight(1f), fontSize = 14.sp, color = DoctorCardSub)
                    Text("${CarePatientProfileRules.weightText(e.kg)} ${t("kg", "किलो")}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    val d = before?.let { kotlin.math.round((e.kg - it.kg) * 10.0) / 10.0 }
                    Text(when { d == null || d == 0.0 -> ""; d > 0 -> " ▲"; else -> " ▼" }, Modifier.widthIn(min = 24.dp), fontSize = 14.sp, color = DoctorAccent)
                }
            }
            val weightIdx = sorted.indices.reversed().toList()
            weightIdx.shownOf(weightsShown).forEach { i -> weightRow(i) }
            SaharaShowOlder(weightIdx, weightsShown, tag = "patient-profile-weights", title = t("Weight history", "वज़न का इतिहास"), searchText = { i -> sorted[i].day.format(format) + " " + CarePatientProfileRules.weightText(sorted[i].kg) }, row = weightRow)
            if (sorted.isEmpty() && kg != null) Text("${CarePatientProfileRules.weightText(kg)} ${t("kg", "किलो")} · ${t("Date not recorded", "तारीख़ दर्ज नहीं")}", fontSize = 14.sp)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "बंद करें")) } })
}

@Composable private fun ProfileTile(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = color, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // No fixed height and no ellipsis: a long label wraps at its space and the tile grows, so nothing is clipped.
            Text(label, fontSize = 14.sp, color = DoctorCardSub, textAlign = TextAlign.Center)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, textAlign = TextAlign.Center)
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
    var illnessSince by remember { mutableStateOf(initial.illnessSince) }
    var doctorName by remember { mutableStateOf(initial.doctorName) }
    var doctorPlace by remember { mutableStateOf(initial.doctorPlace) }
    var doctorPhone by remember { mutableStateOf(initial.doctorPhone) }
    var doctorLandline by remember { mutableStateOf(initial.doctorLandline) }
    var treatments by remember { mutableStateOf(initial.treatments) }
    var treatmentNote by remember { mutableStateOf(initial.treatmentNote) }
    var showDoctor by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ProfileError?>(null) }
    var showAllergies by remember { mutableStateOf(false) }; var showPhone by remember { mutableStateOf(false) }; var showAddress by remember { mutableStateOf(false) }
    fun sexLabel(v: String) = when (v) { "Female" -> t("Female", "महिला"); "Male" -> t("Male", "पुरुष"); else -> t("Other", "अन्य") }
    SaharaScreenSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        // The fields scroll; Save stays pinned at the bottom above the keyboard, so it is never a scroll away.
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp).saharaScreenEnter()) {
          Text(t("Edit profile", "प्रोफ़ाइल बदलें"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
          Spacer(Modifier.height(8.dp))
          Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).saharaContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileStep(1, t("About", "परिचय")) {
                // Age by tap, not typing (24 Nov): Simple Mode turns on by itself at 60 and above from this age.
                Text(t("Age (years)", "उम्र (साल)"), fontSize = 14.sp, color = DoctorCardSub)
                CareAgePicker(age, { age = it; error = null }, "patient-profile-age", Modifier.fillMaxWidth())
                Text(t("Sex", "लिंग"), fontSize = 14.sp, color = DoctorCardSub)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CarePatientProfile.SEX.forEach { v -> ProfileChip(sex == v, sexLabel(v), Modifier.weight(1f)) { sex = if (sex == v) "" else v } } }
            }
            ProfileStep(2, t("Body", "शरीर")) {
                // Weight and height on scroll wheels, never typed (owner, 25 Nov). The text kept here is what the typed fields held, so the rules still check it.
                CareNumberWheelButton(CarePatientProfileRules.weight(weight).first, { weight = CarePatientProfileRules.weightText(it); error = null }, t("Weight (kg)", "वज़न (किलो)"), t("kg", "किलो"),
                    CarePickerScales.WEIGHT_KG, CarePickerScales.WEIGHT_START, "patient-profile-weight", Modifier.fillMaxWidth(), icon = Icons.Default.MonitorWeight, allowClear = true)
                CareNumberWheelButton(CarePatientProfileRules.height(height).first?.toDouble(), { height = it?.roundToInt()?.toString().orEmpty(); error = null }, t("Height (cm)", "क़द (सेमी)"), t("cm", "सेमी"),
                    CarePickerScales.HEIGHT_CM, CarePickerScales.HEIGHT_START, "patient-profile-height", Modifier.fillMaxWidth(), icon = Icons.Default.Height, allowClear = true)
                SaharaDropdown(t("Blood group", "ख़ून का समूह"), if (blood == "Not known") t("Not known", "पता नहीं") else blood,
                    CarePatientProfile.BLOOD.map { v -> SaharaDropdownOption(if (v == "Not known") t("Not known", "पता नहीं") else v, "patient-profile-blood-$v", blood == v) },
                    tag = "patient-profile-blood", placeholder = t("Choose", "चुनें"), onPick = { i -> blood = CarePatientProfile.BLOOD[i] })
            }
            ProfileStep(3, t("Health", "सेहत")) {
                ProfileField(illness, { illness = it.take(120) }, t("Main illness / diagnosis", "मुख्य बीमारी / निदान"), Icons.Default.MedicalInformation)
                // How long, by tap (24 Nov): Months or Years, then a number. An older typed answer ("Mar 2025") shows under it until a number is tapped.
                Text(t("Since when", "कब से"), fontSize = 14.sp, color = DoctorCardSub)
                CareDurationPicker(illnessSince, { illnessSince = it }, "patient-profile-illness-since", Modifier.fillMaxWidth())
                // The doctor seen now: the fields wait behind one "+ Add" chip until any of them is filled in.
                val doctorOpen = showDoctor || doctorName.isNotBlank() || doctorPlace.isNotBlank() || doctorPhone.isNotBlank() || doctorLandline.isNotBlank()
                AnimatedVisibility(doctorOpen, enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "profile doctor") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileField(doctorName, { doctorName = it.take(100) }, t("Doctor's name", "डॉक्टर का नाम"), Icons.Default.LocalHospital, KeyboardType.Text, Modifier.fillMaxWidth().testTag("patient-profile-doctor-name"), singleLine = true)
                    ProfileField(doctorPlace, { doctorPlace = it.take(120) }, t("Hospital or clinic", "अस्पताल या क्लिनिक"), Icons.Default.Place, KeyboardType.Text, Modifier.fillMaxWidth().testTag("patient-profile-doctor-place"), singleLine = true)
                    CarePhoneField(doctorPhone, { doctorPhone = it }, t("Contact number (doctor or hospital)", "संपर्क नंबर (डॉक्टर या अस्पताल)"), "patient-profile-doctor-phone", Modifier.fillMaxWidth(), leadingIcon = Icons.Default.Phone)
                    CarePhoneField(doctorLandline, { doctorLandline = it }, t("Hospital / landline number (optional)", "अस्पताल / लैंडलाइन नंबर (ज़रूरी नहीं)"), "patient-profile-doctor-landline", Modifier.fillMaxWidth(), leadingIcon = Icons.Default.Call)
                } }
                AnimatedVisibility(!doctorOpen, enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "profile add doctor") {
                    SaharaAddChip(t("Add doctor now", "अभी के डॉक्टर जोड़ें"), { showDoctor = true }, Modifier.testTag("patient-profile-add-doctor"), tone = SaharaTone.Indigo)
                }
                Text(t("Treatment now", "अभी का इलाज"), fontSize = 14.sp, color = DoctorCardSub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("patient-profile-treatments")) {
                    CarePatientProfile.TREATMENTS.forEach { code -> ProfileChip(code in treatments, careTreatmentLabel(code, t)) { treatments = CarePatientProfile.toggleTreatment(treatments, code) } }
                }
                ProfileField(treatmentNote, { treatmentNote = it.take(200) }, t("Anything else (optional)", "कुछ और (ज़रूरी नहीं)"), Icons.Default.Healing, modifier = Modifier.fillMaxWidth().testTag("patient-profile-treatment-note"))
                // Optional fields that are still empty wait behind a "+ Add" chip; anything already filled stays open.
                val allergiesOpen = showAllergies || allergies.isNotBlank()
                AnimatedVisibility(allergiesOpen, enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "profile allergies") {
                    ProfileField(allergies, { allergies = it.take(200) }, t("Allergies (optional)", "एलर्जी (ज़रूरी नहीं)"), Icons.Default.WarningAmber)
                }
                AnimatedVisibility(!allergiesOpen, enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "profile add allergies") {
                    SaharaAddChip(t("Add allergies", "एलर्जी जोड़ें"), { showAllergies = true }, Modifier.testTag("patient-profile-add-allergies"), tone = SaharaTone.Plum)
                }
            }
            ProfileStep(4, t("Contact", "संपर्क")) {
                AnimatedVisibility(showPhone || phone.isNotBlank(), enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "profile phone") {
                    CarePhoneField(phone, { phone = it }, t("Phone", "फ़ोन"), "patient-profile-phone", Modifier.fillMaxWidth(), leadingIcon = Icons.Default.Phone)
                }
                AnimatedVisibility(showAddress || address.isNotBlank(), enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "profile address") {
                    ProfileField(address, { address = it.take(160) }, t("City / address", "शहर / पता"), Icons.Default.Place)
                }
                SaharaAddChips(listOfNotNull(
                    (t("Add phone", "फ़ोन जोड़ें") to { showPhone = true }).takeIf { !showPhone && phone.isBlank() },
                    (t("Add city / address", "शहर / पता जोड़ें") to { showAddress = true }).takeIf { !showAddress && address.isBlank() },
                ), Modifier.testTag("patient-profile-add-contact"), tone = SaharaTone.Leaf)
            }
            OnboardingShowWhen(error) { Text(t(it.en, it.hi), color = RustText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.announce()) }
          }
          Spacer(Modifier.height(12.dp))
          // One Save; swiping the sheet down or Back cancels.
          SaharaPrimaryButton(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save", "सहेजें"), onClick = {
              val a = CarePatientProfileRules.age(age); val w = CarePatientProfileRules.weight(weight); val h = CarePatientProfileRules.height(height)
              val bad = a.second ?: w.second ?: h.second
              if (bad != null) error = bad
              // copy() keeps any field this sheet does not show, so a newer key is never lost on edit.
              else onSave(initial.copy(age = a.first, sex = sex, weightKg = w.first, heightCm = h.first, bloodGroup = blood, illness = illness.trim(), allergies = allergies.trim(), phone = phone.trim(), address = address.trim(),
                  weights = CareWeightHistory.afterSave(initial, w.first, java.time.LocalDate.now()),
                  illnessSince = illnessSince.trim(), doctorName = doctorName.trim(), doctorPlace = doctorPlace.trim(), doctorPhone = doctorPhone.trim(), doctorLandline = doctorLandline.trim(), treatments = treatments, treatmentNote = treatmentNote.trim()))
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
@Composable private fun ProfileField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = keyboard != KeyboardType.Text) {
    OutlinedTextField(value, onChange, modifier, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = DoctorAccent) },
        singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper))
}

/** A choice chip that shows a tick and a soft fill when picked. */
@Composable private fun ProfileChip(selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tick = rememberSaharaTick()
    FilterChip(selected = selected, onClick = { tick(); onClick() }, label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) },
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null, shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(containerColor = Cream, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = DoctorAccent),
        modifier = modifier.heightIn(min = 48.dp).saharaSelectBounce(selected, amount = .06f))
}

/**
 * How long the illness has been known, by tap (24 Nov clinical meeting: "months and years, not unformatted text"): Months or
 * Years as two segmented buttons, then the numbers 1 to 12 as round chips; tapping the picked number again clears it. [value] is
 * the stored text ([CareIllnessDuration.encode], e.g. "8 months"); an older typed answer such as "Mar 2025" is shown under the
 * chips as it was saved, and is replaced only when a number is tapped. Used by onboarding ([unitHeight] 56 dp) and the profile.
 * Tags: [tag] on the whole picker, "$tag-months" / "$tag-years" on the units, "$tag-1" to "$tag-12" on the numbers.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun CareDurationPicker(value: String, onChange: (String) -> Unit, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    unitHeight: androidx.compose.ui.unit.Dp = 48.dp, tone: SaharaTone = SaharaTone.Teal) {
    val t = careText()
    val tick = rememberSaharaTick()
    val current = remember(value) { CareIllnessDuration.parse(value) }
    var chosenYears by androidx.compose.runtime.saveable.rememberSaveable(tag) { mutableStateOf(current?.years ?: false) }
    val years = current?.years ?: chosenYears
    Column(modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to t("Months", "महीने"), true to t("Years", "साल")).forEachIndexed { index, (unitYears, label) ->
                SegmentedButton(selected = years == unitYears, enabled = enabled, onClick = {
                    if (years != unitYears) tick()
                    chosenYears = unitYears
                    if (current != null && current.years != unitYears) onChange(CareIllnessDuration(current.amount, unitYears).encode())
                }, shape = SegmentedButtonDefaults.itemShape(index, 2), modifier = Modifier.heightIn(min = unitHeight).testTag("$tag-" + if (unitYears) "years" else "months")) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CareIllnessDuration.AMOUNTS.forEach { n ->
                val on = current != null && current.amount == n
                val spoken = careIllnessSinceText(CareIllnessDuration(n, years).encode(), t)
                val fill by animateColorAsState(if (on) tone.solid else SaharaColors.Surface, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "duration fill")
                val ink by animateColorAsState(if (on) tone.onSolid else SaharaColors.Ink, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "duration ink")
                val edge by animateColorAsState(if (on) tone.solid else SaharaColors.Edge, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "duration edge")
                Surface(checked = on, onCheckedChange = { tick(); chosenYears = years; onChange(if (on) "" else CareIllnessDuration(n, years).encode()) }, enabled = enabled, shape = CircleShape,
                    color = fill, contentColor = ink,
                    border = BorderStroke(if (on) 2.dp else 1.dp, edge),
                    modifier = Modifier.size(48.dp).saharaSelectBounce(on).testTag("$tag-$n").semantics { contentDescription = spoken }) {
                    Box(contentAlignment = Alignment.Center) { Text("$n", fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
                }
            }
        }
        // What is saved, in words: "How long: 8 months", or an older typed answer as it was written.
        OnboardingShowWhen(value.takeIf { it.isNotBlank() }, Modifier.fillMaxWidth()) { shown ->
            Text(careIllnessSinceLabel(shown, t) + ": " + careIllnessSinceText(shown, t), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = DoctorCardInk, modifier = Modifier.fillMaxWidth().testTag("$tag-value"))
        }
    }
}
