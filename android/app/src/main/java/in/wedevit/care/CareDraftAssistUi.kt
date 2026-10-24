package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun CareSourcePicker(draft: Conversation, sectionIndex: Int = 0, onAssign: (Int, CareSourceLine) -> Unit) {
    val section = sectionIndex.coerceIn(0, NoteRules.labels.lastIndex)
    val lines = remember(draft.source) { CareDraftAssist.lines(draft.source) }
    Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(NoteRules.labels[section], fontFamily = SaharaTitleFont, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.testTag("build-section-heading"))
        Text(NoteRules.values(draft)[section].ifBlank { "Nothing added yet. Leave blank if it was not discussed." }, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
        Text("Add the words from the conversation that belong in this section.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
        if (lines.isEmpty()) Text("Add the conversation first.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
        val picks = remember(draft.source, section) { LocalDraftSuggestions.suggest(draft.source).bySection.getOrNull(section).orEmpty() }
            .filterNot { s -> NoteRules.values(draft)[section].lines().any { it.trim() == s.line.words } }
        if (picks.isNotEmpty()) Surface(shape = RoundedCornerShape(14.dp), color = DoctorStatusWarnBg, modifier = Modifier.fillMaxWidth().testTag("build-suggestions-$section")) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Suggested for this section", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DoctorStatusWarn)
                Text("Matched from the words already in the transcript. Nothing is written for you — check each one before adding it.", fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint)
                picks.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Line ${s.line.number}", fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint)
                        Text(s.quote, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                        TextButton(onClick = { onAssign(section, s.line) }, colors = ButtonDefaults.textButtonColors(contentColor = DoctorAccent), modifier = Modifier.heightIn(min = 48.dp).testTag("suggest-add-${s.line.number}-$section")) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add this", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        lines.forEach { line ->
            val added = NoteRules.values(draft)[section].lines().any { it.trim() == line.words }
            Surface(shape = RoundedCornerShape(14.dp), color = if (added) DoctorStatusGoodBg else DoctorCardConversation, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Line ${line.number}", fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint)
                    SelectionContainerText(line.text)
                    if (added) Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = DoctorStatusGood); Text("Added to ${NoteRules.labels[section]}", fontSize = 13.sp, color = DoctorStatusGood, fontWeight = FontWeight.Medium) }
                    if (!added) TextButton(onClick = { onAssign(section, line) }, colors = ButtonDefaults.textButtonColors(contentColor = DoctorAccent), modifier = Modifier.heightIn(min = 48.dp).testTag("source-add-${line.number}-$section")) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add to ${NoteRules.labels[section]}", fontSize = 14.sp)
                    }
                }
            }
        }
    } }
}

@Composable fun CareSourceLinks(source: String, value: String) {
    val references = remember(source, value) { CareDraftAssist.references(source, value) }
    val unlinked = remember(source, value) { CareDraftAssist.unlinked(source, value) }
    var chosen by remember(source, value) { mutableStateOf<CareSourceReference?>(null) }
    if (references.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        references.distinctBy { it.line.id }.forEach { reference -> AssistChip(onClick = { chosen = reference }, label = { Text("Source line ${reference.line.number}") }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) }) }
    }
    if (unlinked.isNotEmpty()) Text("${unlinked.size} ${if (unlinked.size == 1) "line uses" else "lines use"} edited wording. Check against the original conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    chosen?.let { reference -> AlertDialog(onDismissRequest = { chosen = null }, title = { Text("Original words · line ${reference.line.number}") }, text = { SelectionContainerText(reference.line.text) }, confirmButton = { TextButton(onClick = { chosen = null }) { Text("Close") } }) }
}

@Composable fun CareSectionNav(section: Int, count: Int, onBack: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("build-section-nav"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${section + 1} of $count", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        LinearProgressIndicator(progress = { (section + 1f) / count }, modifier = Modifier.fillMaxWidth(), color = DoctorAccent, trackColor = DoctorHairline)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("build-back")) { Text("Back") }
            Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("build-next")) { Text(if (section == count - 1) "Review note" else "Next") }
        }
    }
}
