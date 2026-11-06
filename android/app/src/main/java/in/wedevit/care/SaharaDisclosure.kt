package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Progressive disclosure for forms: required and common fields stay open, filled optional fields stay open,
 * and an optional field that is still empty waits behind one compact "+ Add <thing>" chip that opens it in place.
 * The form's column animates its height with saharaContentSize(), so the field grows in rather than jumping.
 */

/** One "+ Add <thing>" chip: outlined, 48 dp tall, a plus in the tone's colour. Tapping it reveals the field inline. */
@Composable fun SaharaAddChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tone: SaharaTone = SaharaTone.Rust) {
    AssistChip(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50),
        label = { Text(text, fontWeight = FontWeight.SemiBold, softWrap = false) },
        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
        border = BorderStroke(1.5.dp, tone.solid),
        colors = AssistChipDefaults.assistChipColors(containerColor = SaharaColors.Surface, labelColor = tone.ink, leadingIconContentColor = tone.solid))
}

/** The hidden optional fields of one form as a wrapping row of "+ Add" chips; [chips] pairs each label with what it opens. Nothing shows when every field is open. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SaharaAddChips(chips: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier, tone: SaharaTone = SaharaTone.Rust) {
    if (chips.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.forEach { (label, open) -> SaharaAddChip(label, open, tone = tone) }
    }
}

/**
 * A long optional note that starts as one line reading "Add a note" ([collapsedLabel]) and grows to [lines] lines,
 * with its real [label], once it is focused or holds text. Same look as CareField.
 */
@Composable fun CareNoteField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, lines: Int = 3, collapsedLabel: String? = null, enabled: Boolean = true) {
    val t = careText()
    var focused by remember { mutableStateOf(false) }
    val open = focused || value.isNotBlank()
    OutlinedTextField(value = value, onValueChange = onValueChange, enabled = enabled,
        label = { Text(if (open) label else collapsedLabel ?: t("Add a note", "नोट जोड़ें")) },
        leadingIcon = if (open) null else { { Icon(Icons.Default.Add, null, Modifier.size(20.dp)) } },
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.saharaContentSize(), shape = RoundedCornerShape(14.dp), minLines = if (open) lines else 1,
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = RustText, unfocusedLabelColor = DoctorCardSub))
}
