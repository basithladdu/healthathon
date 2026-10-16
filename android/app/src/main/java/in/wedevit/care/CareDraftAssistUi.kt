package `in`.wedevit.care

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun CareSourcePicker(draft: Conversation, onAssign: (Int, CareSourceLine) -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val lines = remember(draft.source) { CareDraftAssist.lines(draft.source) }
    SurfaceCard(Mint) {
        Text("Build the note from their words", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Choose a section, then add the words that belong there.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NoteRules.labels.forEachIndexed { index, label -> FilterChip(selected = section == index, onClick = { section = index }, label = { Text(label) }) }
        }
        if (lines.isEmpty()) Text("Add the conversation first.")
        (if (expanded) lines else lines.take(6)).forEach { line ->
            HorizontalDivider(color = Ink.copy(alpha = .12f))
            Spacer(Modifier.height(8.dp))
            Text("Line ${line.number}", style = MaterialTheme.typography.labelSmall, color = Rust)
            SelectionContainerText(line.text)
            val added = NoteRules.values(draft)[section].lines().any { it.trim() == line.words }
            TextButton(onClick = { onAssign(section, line) }, enabled = !added, modifier = Modifier.testTag("source-add-${line.number}-$section")) {
                if (!added) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
                Text(if (added) "Added to ${NoteRules.labels[section]}" else "Add to ${NoteRules.labels[section]}")
            }
        }
        if (lines.size > 6) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show fewer words" else "Show all ${lines.size} source lines") }
    }
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
