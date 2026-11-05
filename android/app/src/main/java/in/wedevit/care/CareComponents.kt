package `in`.wedevit.care

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ActionCard(color: Color, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Card(onClick = onClick, interactionSource = interaction, modifier = modifier.saharaAppear().saharaPress(interaction), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color), content = content)
}

@Composable fun CareNavigation(selected: Int, clinician: Boolean, onSelect: (Int) -> Unit) {
    // [keys] stay English for test tags; [labels] follow the family's English/Hindi choice.
    val t = careText()
    val keys = listOf("Today", if (clinician) "Talk" else "Docs", "Care", "Nearby", "More")
    val labels = listOf(t("Today", "आज"), if (clinician) t("Talk", "बातचीत") else t("Docs", "कागज़ात"), t("Care", "देखभाल"), t("Nearby", "आसपास"), if (clinician) t("More", "और") else t("Account", "खाता"))
    val routes = listOf(0, if (clinician) 1 else 5, 2, 3, 4)
    val icons = listOf(Icons.Default.WbSunny, if (clinician) Icons.Default.Forum else Icons.Default.FolderOpen, Icons.Default.FavoriteBorder, Icons.Default.Place, if (clinician) Icons.Default.MoreHoriz else Icons.Default.AccountCircle)
    // Material-style bar: a hairline on top, and the selected tab's icon sits in a soft gold pill above a bold label.
    Surface(color = Paper) { Column {
        HorizontalDivider(color = DoctorHairline)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val active = selected == routes[index] || (!clinician && index == 1 && selected == 1)
                val pill by animateColorAsState(if (active) SaharaSoftGold else Color.Transparent, SaharaMotion.tween(SaharaMotion.VALUE_MS), label = "selected tab")
                Column(Modifier.weight(1f).testTag("nav-${keys[index]}").clip(RoundedCornerShape(16.dp))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(routes[index]) }).heightIn(min = 60.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(50)).background(pill), contentAlignment = Alignment.Center) {
                        Icon(icons[index], null, Modifier.size(22.dp), tint = if (active) RustText else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp)); Text(label, fontSize = cappedLabelSize(12f), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) Ink else DoctorCardSub, maxLines = 1)
                }
            }
        }
    } }
}

/** Short labels in fixed-width slots (bottom tabs, step names) grow with the system font only up to 1.3x, so a word like "Nearby" never breaks mid-word; all other text scales fully. */
@Composable fun cappedLabelSize(size: Float): TextUnit { val scale = LocalDensity.current.fontScale; return (size * minOf(scale, 1.3f) / scale).sp }

/** Walkthrough only: shows who is being tried and switches between the ready-made users. Real users have no role menu; their role is computed. */
@Composable fun WalkthroughUserMenu(user: PatientWalkthrough.User, onSwitch: (String) -> Unit, onLeave: () -> Unit) { val t = careText();
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("walkthrough-user-menu").semantics { contentDescription = t("Walkthrough user, currently ${user.name}, ${user.role}", "डेमो उपयोगकर्ता, अभी ${user.name}, ${user.role}") },
            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), colors = ButtonDefaults.textButtonColors(contentColor = Rust)) {
            Text("${user.name} · " + (if (user.role == "Family") t("Family", "परिवार") else t("Patient", "मरीज़")), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PatientWalkthrough.users.forEach { option ->
                DropdownMenuItem(text = { Text("${option.name} — " + (if (option.role == "Family") t("Family", "परिवार") else t("Patient", "मरीज़"))) }, modifier = Modifier.testTag("walkthrough-switch-${option.key}").semantics { selected = option.key == user.key },
                    leadingIcon = { Icon(if (option.role == "Family") Icons.Default.PeopleOutline else Icons.Default.PersonOutline, null) },
                    trailingIcon = { if (option.key == user.key) Icon(Icons.Default.Check, t("Selected", "चुना हुआ"), tint = Rust) },
                    onClick = { expanded = false; onSwitch(option.key) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(t("Leave walkthrough", "डेमो से बाहर जाएँ")) }, modifier = Modifier.testTag("leave-walkthrough"), onClick = { expanded = false; onLeave() })
        }
    }
}

/**
 * The selected care profile stays visible; switching profiles never changes account permissions.
 * The button beside it invites family to this record; a new care profile is added from the name's menu.
 */
@Composable fun CarePersonSelector(name: String, people: List<Pair<String, String>>, selectedId: String,
    enabled: Boolean, onSelect: (String) -> Unit, onAdd: () -> Unit, canAdd: Boolean = true,
    onInvite: () -> Unit = {}, canInvite: Boolean = false, inviteLabel: String = "") {
    var expanded by remember { mutableStateOf(false) }; val t = careText()
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("selected-care-profile")
                .clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, role = Role.Button, onClick = { expanded = true })
                .semantics { contentDescription = t("Switch care profile, $name", "देखभाल प्रोफ़ाइल बदलें, $name") }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Peach), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PersonOutline, null, Modifier.size(22.dp), tint = Rust)
                }
                Spacer(Modifier.width(10.dp))
                Text(name, modifier = Modifier.weight(1f, fill = false), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = Rust)
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                people.forEach { (id, personName) ->
                    DropdownMenuItem(text = { Text(personName) }, modifier = Modifier.testTag("person-$id"),
                        leadingIcon = { Icon(if (id == selectedId) Icons.Default.CheckCircle else Icons.Default.PersonOutline, null, tint = if (id == selectedId) Rust else Ink) },
                        onClick = { expanded = false; onSelect(id) })
                }
                if (canAdd) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(t("Add another person", "एक और व्यक्ति जोड़ें")) }, modifier = Modifier.testTag("add-care-person"),
                        leadingIcon = { Icon(Icons.Default.PersonAdd, null, tint = Rust) }, onClick = { expanded = false; onAdd() })
                }
            }
        }
        if (canInvite) OutlinedButton(onClick = onInvite, enabled = enabled, modifier = Modifier.heightIn(min = 44.dp).testTag("invite-family-top"),
            shape = RoundedCornerShape(50), border = BorderStroke(1.dp, if (enabled) Rust.copy(alpha = .6f) else MaterialTheme.colorScheme.outline.copy(alpha = .3f)),
            contentPadding = PaddingValues(horizontal = 14.dp)) {
            Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp), tint = if (enabled) Rust else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
            Spacer(Modifier.width(6.dp)); Text(inviteLabel, color = if (enabled) RustText else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/**
 * The selected person as a compact chip in the top bar ("Rahul · Family ▾"): switch person, add another person and,
 * for the record's owner, invite family. Replaces the name row that sat under the top bar (3 Nov meeting: redundant).
 */
@Composable fun CareTopPersonMenu(name: String, roleLabel: String, people: List<Pair<String, String>>, selectedId: String,
    enabled: Boolean, onSelect: (String) -> Unit, onAdd: () -> Unit, canAdd: Boolean, onInvite: () -> Unit, canInvite: Boolean, inviteLabel: String) {
    var expanded by remember { mutableStateOf(false) }; val t = careText()
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).widthIn(max = 180.dp).testTag("selected-care-profile").semantics { contentDescription = t("Switch care profile, $name", "देखभाल प्रोफ़ाइल बदलें, $name") },
            shape = RoundedCornerShape(50), contentPadding = PaddingValues(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            colors = ButtonDefaults.textButtonColors(containerColor = SaharaApricot, contentColor = RustText, disabledContainerColor = SaharaApricot.copy(alpha = .5f))) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Paper), contentAlignment = Alignment.Center) {
                Text(name.trim().take(1).uppercase(), fontFamily = SaharaTitleFont, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RustText)
            }
            Spacer(Modifier.width(6.dp))
            Text(if (roleLabel.isBlank()) name else "$name · $roleLabel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(2.dp)); Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            people.forEach { (id, personName) ->
                DropdownMenuItem(text = { Text(personName) }, modifier = Modifier.testTag("person-$id"),
                    leadingIcon = { Icon(if (id == selectedId) Icons.Default.CheckCircle else Icons.Default.PersonOutline, null, tint = if (id == selectedId) Rust else Ink) },
                    onClick = { expanded = false; onSelect(id) })
            }
            if (canInvite) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text(inviteLabel) }, modifier = Modifier.testTag("invite-family-top"),
                    leadingIcon = { Icon(Icons.Default.GroupAdd, null, tint = Rust) }, onClick = { expanded = false; onInvite() })
            }
            if (canAdd) {
                if (!canInvite) HorizontalDivider()
                DropdownMenuItem(text = { Text(t("Add another person", "एक और व्यक्ति जोड़ें")) }, modifier = Modifier.testTag("add-care-person"),
                    leadingIcon = { Icon(Icons.Default.PersonAdd, null, tint = Rust) }, onClick = { expanded = false; onAdd() })
            }
        }
    }
}

@Composable fun CareField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1, singleLine: Boolean = false, placeholder: String? = null, supporting: String? = null) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = minLines, singleLine = singleLine,
        placeholder = placeholder?.let { { Text(it, color = DoctorCardFaint) } }, supportingText = supporting?.let { { Text(it, color = DoctorCardSub) } },
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = RustText, unfocusedLabelColor = DoctorCardSub))
}

@Composable fun ChoiceRow(label: String, icon: ImageVector, tint: Color, selected: Boolean, onSelect: () -> Unit) {
    val color by animateColorAsState(if (selected) tint else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "selected mood")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(color).border(if (selected) 2.dp else 1.dp, if (selected) Ink else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(26.dp), tint = Ink); Spacer(Modifier.width(12.dp)); Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink, modifier = Modifier.weight(1f))
        AnimatedVisibility(selected, enter = fadeIn(SaharaMotion.tween(SaharaMotion.SELECT_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.SELECT_MS)), exit = fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS))) { Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = Ink) }
    }
}

@Composable fun DetailPanel(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.saharaAppear()) {
        Column(Modifier.fillMaxWidth().saharaContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(SaharaApricot), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = RustText) }
                Spacer(Modifier.width(12.dp)); Text(title, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont)
            }
            content()
        }
    }
}

fun careIcon(kind: String): ImageVector = when (kind) {
    "medicine" -> Icons.Default.Medication
    "visit" -> Icons.Default.Event
    "checkin" -> Icons.Default.WbSunny
    "journal" -> Icons.Default.EditNote
    else -> Icons.Default.Checklist
}

/**
 * Sahara's toasts: a rounded card that floats above the bottom bar, coloured by what happened. A save shows a green tick,
 * a problem shows the warning colour and icon, anything else is plain. Tap the cross to dismiss.
 */
@Composable fun CareToastHost(state: SnackbarHostState, modifier: Modifier = Modifier) = SnackbarHost(state, modifier) { data ->
    val t = careText()
    val kind = CareToastRules.kind(data.visuals.message)
    val fill = when (kind) { CareToastKind.SAVED -> DoctorStatusGood; CareToastKind.PROBLEM -> Color(0xFF7A2E1C); CareToastKind.INFO -> Ink }
    val icon = when (kind) { CareToastKind.SAVED -> Icons.Default.CheckCircle; CareToastKind.PROBLEM -> Icons.Default.ErrorOutline; CareToastKind.INFO -> Icons.Default.Info }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(shown, enter = fadeIn(SaharaMotion.tween(SaharaMotion.ENTER_MS)) + slideInVertically(SaharaMotion.tween(SaharaMotion.ENTER_MS)) { it / 2 }) {
        Surface(shape = RoundedCornerShape(16.dp), color = fill, contentColor = Color.White, shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().testTag("care-toast-${kind.name.lowercase()}").announce()) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp).then(if (kind == CareToastKind.SAVED) Modifier.testTag("sahara-saved-tick") else Modifier), tint = if (kind == CareToastKind.SAVED) Gold else Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(CareToastRules.text(data.visuals.message), Modifier.weight(1f), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = Color.White)
                data.visuals.actionLabel?.let { label -> TextButton(onClick = { data.performAction() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label, color = Gold, fontWeight = FontWeight.Bold) } }
                IconButton(onClick = { data.dismiss() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, t("Dismiss", "बंद करें"), Modifier.size(18.dp), tint = Color.White) }
            }
        }
    }
}

/** A small "?" that opens a short explanation, in place of helper text under a heading. */
@Composable fun CareHelpIcon(title: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }; val t = careText()
    Box(modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = t("What is this?", "यह क्या है?")) { open = true }
        .semantics { contentDescription = t("Help: $title", "मदद: $title") }.testTag("care-help"), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Paper).border(1.5.dp, DoctorAccent, CircleShape), contentAlignment = Alignment.Center) {
            Text("?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RustText)
        }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title, fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium) },
        text = { Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk) },
        confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.testTag("care-help-close")) { Text(t("Got it", "समझ गए")) } }, containerColor = Paper)
}

private val SosRed = Color(0xFFB3261E)
private val SosRedText = Color(0xFF8C1D18)

/** SOS on Today: one large red button for the emergency sheet, and the emergency QR card one tap away beside it. */
@Composable fun CareSosBar(onSos: () -> Unit, onQr: () -> Unit) {
    val t = careText()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onSos, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp).testTag("today-sos")) {
            Icon(Icons.Default.Call, null, Modifier.size(26.dp)); Spacer(Modifier.width(10.dp))
            Text(t("SOS", "SOS मदद"), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        OutlinedButton(onClick = onQr, shape = RoundedCornerShape(20.dp), border = BorderStroke(2.dp, SosRed), colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper, contentColor = SosRedText),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), modifier = Modifier.fillMaxHeight().heightIn(min = 64.dp).testTag("today-qr")) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QrCode, null, Modifier.size(26.dp))
                Text(t("QR card", "QR कार्ड"), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** Up next: one visit as a coloured card with a calendar badge (month and day) and a "Tomorrow / In 3 days" chip. */
@Composable fun CareUpNextCard(item: CareItem, whenLabel: String, tint: Color, onOpen: () -> Unit) {
    val hindi = careHindi(); val t = careText()
    val day = if (item.due > 0) java.time.Instant.ofEpochMilli(item.due).atZone(java.time.ZoneId.systemDefault()) else null
    val detail = if (CareEntryCodec.isStructured(item.detail)) "" else item.detail
    val time = if (day != null && (day.hour != 0 || day.minute != 0)) " · " + day.format(careDateFormat("h:mm a", hindi)) else ""
    ActionCard(tint, Modifier.fillMaxWidth().testTag("up-next-${item.id}"), onOpen) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Paper, shadowElevation = 1.dp, modifier = Modifier.size(width = 62.dp, height = 68.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (day != null) {
                        Text(day.format(careDateFormat("MMM", hindi)).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RustText, maxLines = 1)
                        Text("${day.dayOfMonth}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1)
                    } else Icon(Icons.Default.Event, null, Modifier.size(28.dp), tint = DoctorAccent)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = RoundedCornerShape(50), color = Paper) {
                    Text(whenLabel + time, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RustText, maxLines = 1)
                }
                Text(item.title.ifBlank { t("Visit", "मुलाक़ात") }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(detail, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = DoctorAccent)
        }
    }
}
