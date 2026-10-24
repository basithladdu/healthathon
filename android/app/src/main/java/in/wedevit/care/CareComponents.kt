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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ScreenEntrance(content: @Composable () -> Unit) {
    val entrance = remember { Animatable(if (ValueAnimator.areAnimatorsEnabled()) 0f else 1f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = 0.65f + entrance.value * 0.35f
        translationY = (1f - entrance.value) * 8.dp.toPx()
    }) { content() }
}

@Composable fun ActionCard(color: Color, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(110), label = "card press")
    Card(onClick = onClick, interactionSource = interaction, modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = color), content = content)
}

@Suppress("UNUSED_PARAMETER") // The same five routes stay available in every display role.
@Composable fun CareNavigation(selected: Int, clinician: Boolean, onSelect: (Int) -> Unit) {
    val labels = listOf("Today", "Symptoms", "Care", "Nearby", "Account")
    val routes = listOf(0, 9, 2, 3, 4)
    val icons = listOf(Icons.Default.WbSunny, Icons.Default.MonitorHeart, Icons.Default.Favorite, Icons.Default.Place, Icons.Default.AccountCircle)
    val duration = if (ValueAnimator.areAnimatorsEnabled()) 180 else 0
    Surface(color = Paper, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val active = selected == routes[index]
                val color by animateColorAsState(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, tween(duration), label = "selected tab")
                Column(Modifier.weight(1f).testTag("nav-$label").clip(RoundedCornerShape(15.dp)).background(color)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(routes[index]) }).heightIn(min = 58.dp).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icons[index], null, Modifier.size(22.dp), tint = if (active) Ink else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp)); Text(label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = Ink)
                }
            }
        }
    }
}

@Composable fun CareRoleMenu(role: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp).testTag("choose-role").semantics { contentDescription = "Choose role, current role $role" },
            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Rust)) {
            Text(role, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(4.dp)); Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp))
        }
        if (expanded && enabled) CareSelectionDialog(
            title = "Choose your view",
            description = "This changes the view, not your access to care records.",
            options = listOf("Patient", "Family", "Doctor").map { option ->
                CareSelectionOption(option, option, "role-$option", when (option) { "Doctor" -> Icons.Default.MedicalServices; "Family" -> Icons.Default.PeopleOutline; else -> Icons.Default.PersonOutline })
            }, selectedId = role, onDismiss = { expanded = false },
            onSelect = { if (enabled) { expanded = false; onSelect(it) } },
        )
    }
}

private data class CareSelectionOption(val id: String, val label: String, val tag: String, val icon: ImageVector)

@Composable private fun CareSelectionDialog(title: String, options: List<CareSelectionOption>, selectedId: String,
    onSelect: (String) -> Unit, onDismiss: () -> Unit, description: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { option ->
                        val chosen = option.id == selectedId
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(option.tag).clip(RoundedCornerShape(14.dp))
                            .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(option.id) }).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(option.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(option.label, Modifier.weight(1f), fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
                            RadioButton(selected = chosen, onClick = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("Close") }
        },
    )
}

/** The selected care profile stays visible; switching profiles never changes account permissions. */
@Composable fun CarePersonSelector(name: String, people: List<Pair<String, String>>, selectedId: String,
    enabled: Boolean, onSelect: (String) -> Unit, onAdd: () -> Unit, canAdd: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("selected-care-profile")
                .clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = { expanded = true })
                .semantics { contentDescription = "Switch care profile, $name" }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Peach), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PersonOutline, null, Modifier.size(22.dp), tint = Rust)
                }
                Spacer(Modifier.width(10.dp))
                Text(name, modifier = Modifier.weight(1f, fill = false), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp), tint = Rust)
            }
            if (expanded && enabled) CareSelectionDialog(
                title = "Choose a care profile",
                options = people.map { (id, personName) -> CareSelectionOption(id, personName, "person-$id", Icons.Default.PersonOutline) },
                selectedId = selectedId, onDismiss = { expanded = false },
                onSelect = { id -> if (enabled && people.any { it.first == id }) { expanded = false; onSelect(id) } },
            )
        }
        if (canAdd) IconButton(onClick = onAdd, enabled = enabled, modifier = Modifier.size(52.dp).testTag("add-care-person").semantics { contentDescription = "Add person" }) {
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, if (enabled) Rust.copy(alpha = .6f) else MaterialTheme.colorScheme.outline.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp), tint = if (enabled) Rust else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
            }
        }
    }
}

@Composable fun CareField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1, singleLine: Boolean = false, placeholder: String? = null, supporting: String? = null, enabled: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onValueChange, enabled = enabled, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = minLines, singleLine = singleLine,
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
