package `in`.wedevit.care

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(110), label = "card press")
    Card(onClick = onClick, interactionSource = interaction, modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = color), content = content)
}

@Composable fun CareNavigation(selected: Int, clinician: Boolean, onSelect: (Int) -> Unit) {
    // [keys] stay English for test tags; [labels] follow the family's English/Hindi choice.
    val t = careText()
    val keys = listOf("Today", if (clinician) "Talk" else "Docs", "Care", "Nearby", "More")
    val labels = listOf(t("Today", "आज"), if (clinician) t("Talk", "बातचीत") else t("Docs", "कागज़ात"), t("Care", "देखभाल"), t("Nearby", "आसपास"), t("More", "और"))
    val routes = listOf(0, if (clinician) 1 else 5, 2, 3, 4)
    val icons = listOf(Icons.Default.WbSunny, if (clinician) Icons.Default.Forum else Icons.Default.FolderOpen, Icons.Default.FavoriteBorder, Icons.Default.Place, Icons.Default.MoreHoriz)
    val duration = if (ValueAnimator.areAnimatorsEnabled()) 180 else 0
    Surface(color = Paper, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val active = selected == routes[index] || (!clinician && index == 1 && selected == 1)
                val color by animateColorAsState(if (active) Color(0xFFFFE39A) else Color.Transparent, tween(duration), label = "selected tab")
                Column(Modifier.weight(1f).testTag("nav-${keys[index]}").clip(RoundedCornerShape(15.dp)).background(color)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(routes[index]) }).heightIn(min = 58.dp).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icons[index], null, Modifier.size(22.dp), tint = if (active) Ink else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp)); Text(label, fontSize = cappedLabelSize(11f), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = Ink, maxLines = 1)
                }
            }
        }
    }
}

/** Short labels in fixed-width slots (bottom tabs, step names) grow with the system font only up to 1.3x, so a word like "Nearby" never breaks mid-word; all other text scales fully. */
@Composable fun cappedLabelSize(size: Float): TextUnit { val scale = LocalDensity.current.fontScale; return (size * minOf(scale, 1.3f) / scale).sp }

/** Walkthrough only: shows who is being tried and switches between the ready-made users. Real users have no role menu; their role is computed. */
@Composable fun WalkthroughUserMenu(user: PatientWalkthrough.User, onSwitch: (String) -> Unit, onLeave: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("walkthrough-user-menu").semantics { contentDescription = "Walkthrough user, currently ${user.name}, ${user.role}" },
            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), colors = ButtonDefaults.textButtonColors(contentColor = Rust)) {
            Text(user.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PatientWalkthrough.users.forEach { option ->
                DropdownMenuItem(text = { Text("${option.name} — ${option.role}") }, modifier = Modifier.testTag("walkthrough-switch-${option.key}").semantics { selected = option.key == user.key },
                    leadingIcon = { Icon(if (option.role == "Family") Icons.Default.PeopleOutline else Icons.Default.PersonOutline, null) },
                    trailingIcon = { if (option.key == user.key) Icon(Icons.Default.Check, "Selected", tint = Rust) },
                    onClick = { expanded = false; onSwitch(option.key) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Leave walkthrough") }, modifier = Modifier.testTag("leave-walkthrough"), onClick = { expanded = false; onLeave() })
        }
    }
}

/** The selected care profile stays visible; switching profiles never changes account permissions. */
@Composable fun CarePersonSelector(name: String, people: List<Pair<String, String>>, selectedId: String,
    enabled: Boolean, onSelect: (String) -> Unit, onAdd: () -> Unit, canAdd: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }; val t = careText()
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("selected-care-profile")
                .clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = { expanded = true })
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
            }
        }
        if (canAdd) IconButton(onClick = onAdd, enabled = enabled, modifier = Modifier.size(52.dp).testTag("add-care-person").semantics { contentDescription = t("Add person", "व्यक्ति जोड़ें") }) {
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, if (enabled) Rust.copy(alpha = .6f) else MaterialTheme.colorScheme.outline.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp), tint = if (enabled) Rust else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
            }
        }
    }
}

@Composable fun CareField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1, singleLine: Boolean = false, placeholder: String? = null, supporting: String? = null) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = minLines, singleLine = singleLine,
        placeholder = placeholder?.let { { Text(it) } }, supportingText = supporting?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper))
}

@Composable fun ChoiceRow(label: String, icon: ImageVector, tint: Color, selected: Boolean, onSelect: () -> Unit) {
    val color by animateColorAsState(if (selected) tint else Paper, tween(160), label = "selected mood")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color).border(if (selected) 2.dp else 1.dp, if (selected) Ink else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(27.dp)); Spacer(Modifier.width(12.dp)); Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        AnimatedVisibility(selected, enter = fadeIn(tween(140)) + scaleIn(tween(140)), exit = fadeOut(tween(100))) { Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp)) }
    }
}

@Composable fun DetailPanel(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Rust); Spacer(Modifier.width(12.dp)); Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), fontFamily = SaharaTitleFont)
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
