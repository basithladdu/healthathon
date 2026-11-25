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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(SaharaSpacing.Sm), verticalArrangement = Arrangement.spacedBy(SaharaSpacing.Xs)) {
        chips.forEach { (label, open) -> SaharaAddChip(label, open, tone = tone) }
    }
}

/**
 * A long optional note that starts as one line reading "Add a note" ([collapsedLabel]) and grows to [lines] lines,
 * with its real [label], once it is focused or holds text. Same look as CareField, including the microphone at its end
 * ([voice], on by default): spoken words are added after what is already there. The microphone shows only where this phone
 * writes speech down on the phone itself (Android 12+ with on-device recognition), the same rule CareField uses.
 */
@Composable fun CareNoteField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, lines: Int = 3, collapsedLabel: String? = null, enabled: Boolean = true, voice: Boolean = true) {
    val t = careText()
    var focused by remember { mutableStateOf(false) }
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    val mic = if (voice) rememberCareFieldMic { words -> latestChange(CareVoiceRules.append(latestValue, words)) } else null
    val heard = mic?.let { m -> when { m.listening && m.partial.isNotBlank() -> m.partial; m.listening -> t("Listening… speak now", "सुन रहे हैं… अब बोलिए"); else -> m.status } }.orEmpty()
    LaunchedEffect(mic?.status) { if (mic != null && mic.status.isNotBlank() && !mic.listening) { kotlinx.coroutines.delay(6000); mic.clearStatus() } }
    val open = focused || value.isNotBlank() || mic?.listening == true
    OutlinedTextField(value = value, onValueChange = onValueChange, enabled = enabled,
        label = { Text(if (open) label else collapsedLabel ?: t("Add a note", "नोट जोड़ें")) },
        leadingIcon = if (open) null else { { Icon(Icons.Default.Add, null, Modifier.size(20.dp)) } },
        trailingIcon = mic?.let { m -> { CareMicButton(m, enabled = enabled) } },
        supportingText = if (heard.isBlank()) null else { { Text(heard, color = RustText, modifier = Modifier.announce()) } },
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.saharaContentSize(), shape = RoundedCornerShape(SaharaRadius.Md), minLines = if (open) lines else 1,
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, unfocusedBorderColor = if (mic?.listening == true) SaharaTone.Rose.solid else MaterialTheme.colorScheme.outline,
            focusedLabelColor = RustText, unfocusedLabelColor = DoctorCardSub))
}
