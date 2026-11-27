@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One row of a [SaharaDropdown]: its words, the test tag it carries, and whether it is the chosen one. */
data class SaharaDropdownOption(val label: String, val tag: String, val selected: Boolean = false)

/**
 * A read-only field that opens a menu (Material 3 exposed dropdown), for a pick from a list that is too long for chips
 * (team decision, 24 Nov: a dropdown, not a wall of capsules). The field carries [tag]; each row carries its own tag. [onPick] gets the row's index.
 */
@Composable fun SaharaDropdown(label: String, valueText: String, options: List<SaharaDropdownOption>, onPick: (Int) -> Unit, tag: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, placeholder: String = "") {
    var open by remember { mutableStateOf(false) }
    val tick = rememberSaharaTick()
    ExposedDropdownMenuBox(expanded = open && enabled, onExpandedChange = { if (enabled) open = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(value = valueText, onValueChange = {}, readOnly = true, enabled = enabled, singleLine = true,
            label = { Text(label, fontSize = 14.sp) }, placeholder = if (placeholder.isNotBlank()) { { Text(placeholder, fontSize = 16.sp) } } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open && enabled) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp), shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().heightIn(min = 56.dp).testTag(tag))
        ExposedDropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }, containerColor = Paper) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o.label, fontSize = 17.sp, fontWeight = if (o.selected) FontWeight.SemiBold else FontWeight.Normal, color = SaharaColors.Ink) },
                    trailingIcon = if (o.selected) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = RustText) } } else null,
                    onClick = { tick(); open = false; onPick(i) }, modifier = Modifier.heightIn(min = 48.dp).testTag(o.tag))
            }
        }
    }
}
