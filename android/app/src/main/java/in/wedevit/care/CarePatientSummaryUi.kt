package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Next visit from Today: only the day and time are needed (tomorrow 10:00 is filled in). Doctor or hospital and place are optional and
 * stay behind "More details".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun QuickVisitDialog(vm: CareModel, onDismiss: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val capturedPerson = remember { person }; val capturedAccount = remember { vm.accountNamespace }
    val zone = ZoneId.systemDefault()
    var day by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var hour by rememberSaveable { mutableIntStateOf(10) }; var minute by rememberSaveable { mutableIntStateOf(0) }
    var more by rememberSaveable { mutableStateOf(false) }
    var who by rememberSaveable { mutableStateOf("") }; var place by rememberSaveable { mutableStateOf("") }
    val chosen = LocalDate.parse(day)
    LaunchedEffect(person) { if (person != capturedPerson) onDismiss() }
    // Day and time by the calendar and the scroll wheels, never typed (owner, 25 Nov).
    var pickingDay by remember { mutableStateOf(false) }; var pickingTime by remember { mutableStateOf(false) }
    if (pickingDay) CareDatePickerDialog(chosen, t("Day", "दिन"), { day = it.toString(); pickingDay = false }, { pickingDay = false })
    if (pickingTime) CareClockDialog(LocalTime.of(hour, minute), t("Time", "समय"), { hour = it.hour; minute = it.minute; pickingTime = false }, { pickingTime = false })
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(t("Next visit", "अगली मुलाक़ात")) }, text = {
        // No size animation here: an AlertDialog window can't resize smoothly, so animating made the fields clip and the dialog feel slow.
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingDay = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-visit-date")) {
                    Icon(Icons.Default.Event, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Day *", "दिन *") + ": " + chosen.format(careDateFormat("d MMM", careHindi())), maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-visit-time")) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Time *", "समय *") + ": " + LocalTime.of(hour, minute).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), maxLines = 1, softWrap = false)
                }
            }
            TextButton(onClick = { more = !more }, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-visit-more")) {
                Text(t("More details", "और जानकारी"), fontWeight = FontWeight.SemiBold); SaharaChevron(more, null, tint = LocalContentColor.current)
            }
            // The extra fields fade in (no size animation: the dialog window cannot resize smoothly) and leave at once.
            AnimatedVisibility(more, enter = fadeIn(SaharaMotion.tween(SaharaMotion.ENTER_MS)), exit = ExitTransition.None) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(who, { who = it.take(200) }, label = { Text(t("Doctor or hospital (optional)", "डॉक्टर या अस्पताल (चाहें तो)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(place, { place = it.take(500) }, label = { Text(t("Place or note (optional)", "जगह या नोट (चाहें तो)")) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }, confirmButton = { TextButton(enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-visit-save"), onClick = {
        if (!canEditCareContext(vm, capturedPerson, capturedAccount)) { vm.message.value = t("Care access changed. Reopen this person's records before saving.", "देखभाल की अनुमति बदल गई है। सहेजने से पहले इस व्यक्ति के रिकॉर्ड फिर से खोलें।"); onDismiss() }
        else vm.saveItem("visit", who.trim().ifBlank { t("Doctor visit", "डॉक्टर से मुलाक़ात") }, place.trim(), LocalDateTime.of(chosen, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli(), done = onDismiss)
    }) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save visit", "मुलाक़ात सहेजें"), fontWeight = FontWeight.SemiBold) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(t("Cancel", "रद्द करें")) } })
}
