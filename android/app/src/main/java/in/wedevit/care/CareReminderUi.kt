package `in`.wedevit.care

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun CareReminderSettings(vm: CareModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val items by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scheduler.enable(true)
        else { scheduler.enable(false); error = "Notifications are off. Your care records are still saved. You can allow them in Android Settings." }
        revision++
    }
    DisposableEffect(lifecycle, scheduler) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { scheduler.reschedule(); revision++ } }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onClose)
    val enabled = remember(revision) { scheduler.enabled() }
    val allowed = remember(revision) { scheduler.allowed() }
    val count = remember(revision) { scheduler.scheduledCount() }
    val exact = remember(revision) { scheduler.exactAllowed() }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("care-reminder-settings"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Heading("Reminders", 28) } }
        item { SurfaceCard(Sage) {
            Row { Column(Modifier.weight(1f)) { Text("Care reminders", fontWeight = FontWeight.SemiBold); Text(if (enabled && allowed) "$count next alerts scheduled" else "Off", style = MaterialTheme.typography.bodySmall) }; Switch(checked = enabled && allowed, onCheckedChange = { turnOn ->
                error = ""
                if (!turnOn) scheduler.enable(false)
                else if (Build.VERSION.SDK_INT >= 33 && !allowed) request.launch(Manifest.permission.POST_NOTIFICATIONS)
                else if (!allowed) error = "Allow Sahara notifications in Android Settings, then return here."
                else scheduler.enable(true)
                revision++
            }) }
            Text("Alerts can be delayed by Android, battery settings or a switched-off phone. They are a reminder to check the record, not an urgent-care service.", style = MaterialTheme.typography.bodySmall)
            Text("Only the active account sends alerts. Medicine times follow the phone’s current time zone; dated visits keep their saved time.", style = MaterialTheme.typography.bodySmall)
            if (!allowed) TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }.onFailure { error = "Open Android Settings → Apps → Sahara → Notifications." } }) { Text("Open notification settings") }
            if (enabled && allowed && !exact && Build.VERSION.SDK_INT >= 31) {
                Text("Android may send these alerts late. Allow on-time alerts so they arrive at the set time. · Android ये सूचनाएँ देर से भेज सकता है। समय पर सूचना की अनुमति दें, ताकि वे तय समय पर आएँ।", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("reminder-exact-off"))
                TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }.onFailure { error = "Open Android Settings → Apps → Sahara → Alarms & reminders. · Android सेटिंग्स → ऐप्स → Sahara → अलार्म और रिमाइंडर खोलें।" } }) { Text("Allow on-time alerts · समय पर सूचना की अनुमति दें") }
            }
        } }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item { Text("Choose times from the existing prescription. Sahara does not calculate or change doses. Marking a medicine done completes that whole entry for the day.", style = MaterialTheme.typography.bodySmall) }
        val today = java.time.LocalDate.now()
        val eligible = items.filter { it.personId == selected && CareReminderRules.listed(it, today) }
        if (eligible.isEmpty()) item { SurfaceCard { Text("No dated care activities or medicines yet."); Text("Add a medicine or choose a time in Calendar first.", style = MaterialTheme.typography.bodySmall) } }
        items(eligible, key = { it.id }) { item -> ReminderItemSettings(item, scheduler, revision) { revision++ } }
    }
}

@Composable private fun ReminderItemSettings(item: CareItem, scheduler: CareReminderScheduler, revision: Int, changed: () -> Unit) {
    val option = remember(item.id, revision) { scheduler.option(item.id) }
    var times by rememberSaveable(item.id) { mutableStateOf((option?.times ?: CareReminderRules.printedTimes(item.detail)).joinToString(", ")) }
    var starts by rememberSaveable(item.id) { mutableStateOf(option?.starts.orEmpty()) }
    var ends by rememberSaveable(item.id) { mutableStateOf(option?.ends.orEmpty()) }
    var error by rememberSaveable(item.id) { mutableStateOf("") }
    val dateOnly = CareReminderRules.dateOnly(item)
    SurfaceCard {
        Row { Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text(when { option?.muted == true -> "Paused"; item.kind == "medicine" -> (option?.times ?: CareReminderRules.printedTimes(item.detail)).joinToString(", ").ifBlank { "No time chosen" }
            dateOnly -> DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.due)) + " · " + option?.times.orEmpty().joinToString(", ").ifBlank { "No alert time chosen · सूचना का समय नहीं चुना" }
            item.due > 0 -> DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.due)); else -> "No time chosen" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("reminder-when-${item.id}")) }
            Switch(checked = option?.muted != true, onCheckedChange = { enabled -> scheduler.configure(item.id, (option ?: ReminderOption(CareReminderRules.printedTimes(item.detail))).copy(muted = !enabled)); changed() }) }
        if (item.kind == "medicine") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareField(times, { times = it; error = "" }, "Times · HH:mm, separated by commas", singleLine = true)
                CareField(starts, { starts = it; error = "" }, "Start date · YYYY-MM-DD (optional)", singleLine = true)
                CareField(ends, { ends = it; error = "" }, "Last date · YYYY-MM-DD (optional)", singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { runCatching {
                    scheduler.configure(item.id, ReminderOption(if (times.isBlank()) emptyList() else times.split(',').map(String::trim), starts.trim(), ends.trim(), option?.muted == true)); changed()
                }.onFailure { error = it.message ?: "Check the times and dates." } }, modifier = Modifier.fillMaxWidth()) { Text("Save reminder times") }
            }
        } else if (dateOnly) {
            // Saved with a date only: no alert until a time on that day is chosen here (never a midnight alert).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareField(times, { times = it; error = "" }, "Alert time on that day · उस दिन सूचना का समय (HH:mm)", singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { runCatching {
                    scheduler.configure(item.id, ReminderOption(if (times.isBlank()) emptyList() else times.split(',').map(String::trim), muted = option?.muted == true)); changed()
                }.onFailure { error = it.message ?: "Use HH:mm, such as 08:30." } }, modifier = Modifier.fillMaxWidth().testTag("reminder-save-${item.id}")) { Text("Save alert time · समय सहेजें") }
            }
        }
    }
}
