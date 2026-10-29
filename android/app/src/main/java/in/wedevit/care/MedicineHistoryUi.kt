@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId

/** Under each medicine in the family's daily list: today's reported status, "Missed today", and that medicine's intake history. */
@Composable internal fun MedicineDoseActions(item: CareItem, vm: CareModel) {
    val events by vm.medicineEvents.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val current by vm.items.collectAsStateWithLifecycle()
    var history by rememberSaveable(item.id) { mutableStateOf(false) }
    var confirmMissed by rememberSaveable(item.id) { mutableStateOf(false) }
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val today = LocalDate.now()
    val outcome = MedicineHistoryRules.todayOutcome(item, events, today, ZoneId.systemDefault())
    if (outcome == "skipped") Text("Reported missed today · आज छूटी बताई गई", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Rust, modifier = Modifier.padding(top = 6.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canEdit && outcome == null && current.any { it.id == item.id }) TextButton(onClick = { confirmMissed = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("care-missed-${item.id}")) {
            Icon(Icons.Default.RemoveCircleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Missed today · आज छूटी")
        }
        TextButton(onClick = { history = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-medicine-history-${item.id}")) {
            Icon(Icons.Default.History, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("History · इतिहास")
        }
    }
    if (confirmMissed) AlertDialog(onDismissRequest = { confirmMissed = false },
        title = { Text("Record a missed dose? · छूटी खुराक दर्ज करें?") },
        text = { Text("${item.title}: this adds a \"missed today\" report to the medicine history${if (vm.cloud != null) ", shared with the people and doctor you invited" else " on this phone"}. Earlier reports stay. Ask the doctor or nurse what to do about a missed dose. · पहले की जानकारी नहीं मिटेगी। छूटी खुराक के बारे में डॉक्टर या नर्स से पूछें।") },
        confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.reportMedicineMissed(item); confirmMissed = false }, enabled = !busy && canEdit) { Text("Record missed · दर्ज करें") } },
        dismissButton = { TextButton(onClick = { confirmMissed = false }) { Text("Cancel · रद्द करें") } })
    if (history) ModalBottomSheet(onDismissRequest = { history = false }, containerColor = Cream) {
        val mine = events.filter { it.medicineId == item.id }
        val now = System.currentTimeMillis()
        LazyColumn(Modifier.fillMaxWidth().testTag("care-medicine-history-sheet"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Heading("${item.title} · इतिहास", 24) }
            item { Text("Taken and missed reports, newest first. A report is what someone recorded, not proof a dose was swallowed. · लेने और छूटने की जानकारी, नई सबसे ऊपर।", fontSize = 13.sp, color = DoctorCardSub) }
            if (mine.isEmpty()) item { Text("No reports yet. Tick the box when the dose is taken, or tap Missed today. · अभी कोई जानकारी नहीं।", fontSize = 15.sp) }
            items(mine, key = { it.id }) { MedicineReportCard(it, mine, now) }
            item { Text(if (vm.cloud != null) "Saved in your account. Family and doctors you share with can see it. · आपके खाते में सेव। जिनके साथ आपने साझा किया, वे देख सकते हैं।" else "Saved only on this phone. Sign in to share it with family and the doctor. · सिर्फ़ इस फ़ोन पर।", fontSize = 12.sp, color = DoctorCardFaint) }
            if (vm.cloud != null) item { TextButton(onClick = vm::refreshMedicineHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh history · इतिहास ताज़ा करें") } }
        }
    }
}
