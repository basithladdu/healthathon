package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A quiet "i" for explanation that should not sit on screen all the time (team, 24 Nov). A 20 dp outlined icon in a 48 dp target, with an optional short
 * muted [label]; a tap opens a soft box with [content], a second tap closes it. Closed by default. Never use it for warnings or consent text.
 * [description] is what a screen reader says for the button; [tag] is the button's test tag, and "$tag-box" the box's.
 */
@Composable fun SaharaInfoToggle(description: String, tag: String, modifier: Modifier = Modifier, label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = careText()
    var open by rememberSaveable(tag) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = { open = !open }).padding(end = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description; stateDescription = if (open) t("Expanded", "खुला है") else t("Collapsed", "बंद है") }.testTag(tag),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = SaharaColors.Primary.copy(alpha = .7f)) }
            if (label != null) Text(label, fontSize = 14.sp, color = SaharaColors.InkSub)
        }
        AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            Surface(color = SaharaColors.PrimaryWash, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("$tag-box")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}
