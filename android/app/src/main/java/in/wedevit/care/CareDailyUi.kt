@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val careAddKinds = listOf("medicine" to "Add medicine", "visit" to "Add visit", "task" to "Add task", "checkin" to "Add check-in", "journal" to "Add note")

/** Daily-care entry points stay visible; photo scanning is separate from manual medicine entry. */
@Composable fun CareAddMenuButton(onAdd: (String) -> Unit, onScanPrescription: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("care-add-actions"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp), tint = DoctorAccent)
            Text("Add", color = DoctorCardInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            careAddKinds.forEach { (kind, label) ->
                OutlinedButton(onClick = { onAdd(kind) }, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DoctorAccent), contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("care-add-$kind")) {
                    Icon(careIcon(kind), null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                    Text(label.removePrefix("Add ").replaceFirstChar { it.titlecase() }, fontSize = 15.sp)
                }
            }
            OutlinedButton(onClick = onScanPrescription, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DoctorAccent), contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("care-add-scan")) {
                Icon(Icons.Default.DocumentScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Scan prescription", fontSize = 15.sp)
            }
        }
    }
}

/** Pure grouping rules for the Daily care modules. */
internal object CareModules {
    val order = listOf("Conversations", "Medicines", "Visits", "To do", "Check-ins", "Journal")
    const val PREVIEW = 5
    /** Current = what vm.items shows (not moved to Past, not excluded by a prescription); Past = every other medicine of the person. */
    fun splitMedicines(all: List<CareItem>, visible: List<CareItem>): Pair<List<CareItem>, List<CareItem>> { val current = visible.filter { it.kind == "medicine" }; val ids = current.map { it.id }.toSet(); return current to all.filter { it.kind == "medicine" && it.id !in ids } }
    /** Upcoming = undated or due now/later, soonest first; Past = dated before now, newest first. */
    fun splitVisits(visits: List<CareItem>, now: Long): Pair<List<CareItem>, List<CareItem>> = visits.filter { it.due == 0L || it.due >= now }.sortedBy { if (it.due == 0L) Long.MAX_VALUE else it.due } to visits.filter { it.due in 1 until now }.sortedByDescending { it.due }
    fun moduleFor(filter: String): String? = order.firstOrNull { it.equals(filter, true) }
}

/** Section visibility is selected by the page; a section heading never hides its controls. */
@Suppress("UNUSED_PARAMETER")
@Composable fun CareModuleCard(key: String, title: String, icon: ImageVector, tint: Color, count: Int, defaultOpen: Boolean, forceOpen: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("care-module-$key")) { Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp).testTag("care-module-$key-heading"), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = tint) { Icon(icon, null, Modifier.padding(8.dp).size(22.dp), tint = DoctorAccent) }
            Spacer(Modifier.width(12.dp)); Text(title, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f))
            Text("$count", fontSize = 14.sp, color = DoctorCardSub)
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    } }
}

@Composable fun QuietLine(text: String) { Text(text, fontSize = 14.sp, color = DoctorCardFaint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }

/** Bounded pages retain access to every row without expanding or collapsing a section. */
@Composable fun <T> CareModuleList(key: String, items: List<T>, empty: String, label: String? = null, keyOf: (T) -> Any, row: @Composable (T) -> Unit) {
    var page by rememberSaveable(key) { mutableIntStateOf(0) }
    val lastPage = ((items.size - 1) / CareModules.PREVIEW).coerceAtLeast(0)
    val currentPage = page.coerceAtMost(lastPage)
    LaunchedEffect(lastPage) { if (page > lastPage) page = lastPage }
    if (label != null) Text("$label · ${items.size}".uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DoctorCardFaint, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    if (items.isEmpty()) { QuietLine(empty); return }
    val start = currentPage * CareModules.PREVIEW
    items.drop(start).take(CareModules.PREVIEW).forEach { item -> key(keyOf(item)) { row(item) } }
    if (items.size > CareModules.PREVIEW) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${start + 1}–${(start + CareModules.PREVIEW).coerceAtMost(items.size)} of ${items.size}", color = DoctorCardSub, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterVertically).testTag("care-module-$key-page"))
        TextButton(onClick = { page = (currentPage - 1).coerceAtLeast(0) }, enabled = currentPage > 0,
            modifier = Modifier.heightIn(min = 48.dp).testTag("care-module-$key-previous")) { Text("Previous", color = if (currentPage > 0) DoctorAccent else DoctorCardFaint) }
        TextButton(onClick = { page = (currentPage + 1).coerceAtMost(lastPage) }, enabled = currentPage < lastPage,
            modifier = Modifier.heightIn(min = 48.dp).testTag("care-module-$key-next")) { Text("Next", color = if (currentPage < lastPage) DoctorAccent else DoctorCardFaint) }
    }
}

@Composable fun ConversationRow(note: CareVersion, onRead: (CareVersion) -> Unit) {
    Surface(onClick = { onRead(note) }, shape = RoundedCornerShape(16.dp), color = SaharaLavender, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-note-${note.id}")) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("Version ${note.version} · ${date(note.published)}" + note.doctor.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        Text(ReviewWorkflow.label(note), fontSize = 12.sp, color = DoctorCardSub)
    } }
}

@Composable fun MedicineScanAction(onScan: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DocumentScanner, null, tint = DoctorAccent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text("Scan a prescription", fontSize = 14.sp, color = DoctorCardInk)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onScan, modifier = Modifier.heightIn(min = 48.dp).testTag("care-module-medicines-scan")) { Text("Scan & organize", color = DoctorAccent) }
            TextButton(onClick = onManual, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add manually", color = DoctorAccent) }
        }
    }
}
