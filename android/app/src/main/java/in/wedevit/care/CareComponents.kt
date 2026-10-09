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
import androidx.compose.ui.text.font.FontWeight
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

@Composable fun CareNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Today", "Talk", "Care", "Nearby", "More")
    val icons = listOf(Icons.Default.WbSunny, Icons.Default.Forum, Icons.Default.FavoriteBorder, Icons.Default.Place, Icons.Default.MoreHoriz)
    Surface(color = Paper) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                val color by animateColorAsState(if (active) Gold else Color.Transparent, tween(180), label = "selected tab")
                Column(Modifier.weight(1f).testTag("nav-$label").clip(RoundedCornerShape(16.dp)).selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) }).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(width = 48.dp, height = 32.dp).background(color, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icons[index], null, Modifier.size(23.dp), tint = if (active) Ink else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(4.dp)); Text(label, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = Ink)
                }
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Rust); Spacer(Modifier.width(12.dp)); Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "details arrow")
                Icon(Icons.Default.ExpandMore, if (expanded) "Hide $title" else "Show $title", Modifier.graphicsLayer { rotationZ = rotation })
            }
            AnimatedVisibility(expanded, enter = expandVertically(tween(180)) + fadeIn(tween(180)), exit = shrinkVertically(tween(150)) + fadeOut(tween(150))) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), content = content)
            }
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
