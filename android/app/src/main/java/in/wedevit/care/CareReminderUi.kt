@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId

@Composable fun CareReminderSettings(vm: CareModel, onClose: () -> Unit) { val t = careText();
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val items by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    // Before Android's prompt: why, Allow / Not now. When Android will no longer ask ("Don't allow" twice), a dialog opens Settings instead.
    var permissionDialog by remember { mutableStateOf("") } // "" | "why" | "settings"
    val prefs = remember { context.getSharedPreferences("care_reminder_permission", android.content.Context.MODE_PRIVATE) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scheduler.enable(true)
        else {
            scheduler.enable(false)
            val activity = nearbyActivity(context)
            if (Build.VERSION.SDK_INT >= 33 && activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) { prefs.edit().putBoolean("blocked", true).apply(); permissionDialog = "settings" }
        }
        revision++
    }
    val openNotificationSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }.onFailure { error = t("Allow notifications in Settings.", "Settings में सूचनाएँ चालू करें।") }; Unit }
    if (permissionDialog == "why") FamilyPermissionDialog(Icons.Default.NotificationsActive, t("Get a gentle reminder when a medicine is due?", "दवा का समय होने पर हल्की याद दिलाएँ?"),
        t("Reminders come at the times you choose, for medicines and visits.", "आपके चुने समय पर दवा और मुलाक़ात की याद आएगी।"),
        t("Turn on reminders", "याद दिलाना चालू करें"), onConfirm = { permissionDialog = ""; request.launch(Manifest.permission.POST_NOTIFICATIONS) }, onDismiss = { permissionDialog = "" })
    if (permissionDialog == "settings") FamilyPermissionDialog(Icons.Default.NotificationsOff, t("Notifications are off", "सूचनाएँ बंद हैं"),
        t("Turn on notifications for Sahara in Settings to get reminders.", "रिमाइंडर पाने के लिए सेटिंग में Sahara की सूचनाएँ चालू करें।"),
        t("Open Settings", "सेटिंग खोलें"), onConfirm = { permissionDialog = ""; openNotificationSettings() }, onDismiss = { permissionDialog = "" })
    DisposableEffect(lifecycle, scheduler) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { scheduler.reschedule(); revision++ } }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onClose)
    val enabled = remember(revision) { scheduler.enabled() }
    val allowed = remember(revision) { scheduler.allowed() }
    val count = remember(revision) { scheduler.scheduledCount() }
    val exact = remember(revision) { scheduler.exactAllowed() }
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("care-reminder-settings"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }; Box(Modifier.weight(1f)) { Heading(t("Reminders", "रिमाइंडर"), 28) }; CareLanguageMenu(compact = true) } }
        item { SurfaceCard(Sage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (enabled && allowed) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null, Modifier.size(24.dp), tint = DoctorCardInk); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(t("Care reminders", "देखभाल रिमाइंडर"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
                    // Status as a chip, not a sentence.
                    Surface(shape = RoundedCornerShape(50), color = if (enabled && allowed) Paper else Cream) { Text(if (enabled && allowed) t("On", "चालू") else t("Off", "बंद"), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (enabled && allowed) DoctorStatusGood else DoctorCardSub) } }
                Switch(checked = enabled && allowed, onCheckedChange = { turnOn ->
                error = ""
                if (!turnOn) scheduler.enable(false)
                else if (Build.VERSION.SDK_INT >= 33 && !allowed && !prefs.getBoolean("blocked", false)) permissionDialog = "why"
                else if (!allowed) permissionDialog = "settings"
                else scheduler.enable(true)
                revision++
            }) }
            if (!allowed) OutlinedButton(onClick = openNotificationSettings, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) { Text(t("Open settings", "सेटिंग खोलें"), fontSize = 15.sp) }
            if (enabled && allowed && !exact && Build.VERSION.SDK_INT >= 31) {
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }.onFailure { error = t("Allow alarms in Settings.", "Settings में अलार्म चालू करें।") } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).testTag("reminder-exact-off")) { Text(t("Allow on-time alerts", "समय पर सूचना की अनुमति दें"), fontSize = 15.sp) }
            }
        } }
        if (error.isNotBlank()) item { Text(error, color = RustText, fontSize = 15.sp, modifier = Modifier.announce()) }
        val today = java.time.LocalDate.now()
        val eligible = items.filter { it.personId == selected && CareReminderRules.listed(it, today) }
        if (eligible.isEmpty()) item { SurfaceCard { Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.NotificationsOff, null, Modifier.size(32.dp), tint = DoctorCardSub)
            Text(t("No reminders yet", "अभी कोई रिमाइंडर नहीं"), fontSize = 16.sp, color = DoctorCardInk, textAlign = TextAlign.Center)
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back", "वापस"), fontSize = 15.sp) }
        } } }
        items(eligible, key = { it.id }) { item -> ReminderItemSettings(item, scheduler, revision, share = if (canEdit && item.kind == "medicine") { times ->
            val detail = runCatching { MedicineEntryText.withTimes(item.detail, times) }.getOrDefault(item.detail)
            if (detail != item.detail && detail.length <= 4000) vm.updateItem(item.copy(detail = detail)) {}
        } else null) { revision++ } }
    }
}

@Composable private fun ReminderItemSettings(item: CareItem, scheduler: CareReminderScheduler, revision: Int, share: ((List<String>) -> Unit)? = null, changed: () -> Unit) { val t = careText();
    val option = remember(item.id, revision) { scheduler.option(item.id) }
    var times by rememberSaveable(item.id) { mutableStateOf((option?.times ?: CareReminderRules.printedTimes(item.detail)).joinToString(", ")) }
    var starts by rememberSaveable(item.id) { mutableStateOf(option?.starts.orEmpty()) }
    var ends by rememberSaveable(item.id) { mutableStateOf(option?.ends.orEmpty()) }
    var error by rememberSaveable(item.id) { mutableStateOf("") }
    val dateOnly = CareReminderRules.dateOnly(item)
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(careIcon(item.kind), null, Modifier.size(22.dp), tint = RustText); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk); Text(when { option?.muted == true -> t("Paused", "रुका हुआ"); item.kind == "medicine" -> (option?.times ?: CareReminderRules.printedTimes(item.detail)).joinToString(", ").ifBlank { t("No time chosen", "समय नहीं चुना") }
            dateOnly -> careDateFormat("dd MMM yyyy", careHindi()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.due)) + " · " + option?.times.orEmpty().joinToString(", ").ifBlank { t("No alert time chosen", "सूचना का समय नहीं चुना") }
            item.due > 0 -> careDateFormat("dd MMM yyyy · HH:mm z", careHindi()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.due)); else -> t("No time chosen", "समय नहीं चुना") }, fontSize = 14.sp, color = if (option?.muted == true) DoctorStatusWarn else DoctorCardSub, modifier = Modifier.testTag("reminder-when-${item.id}")) }
            Switch(checked = option?.muted != true, onCheckedChange = { enabled -> scheduler.configure(item.id, (option ?: ReminderOption(CareReminderRules.printedTimes(item.detail))).copy(muted = !enabled)); changed() }) }
        if (item.kind == "medicine") {
            val doseTimes = CareReminderRules.printedTimes(item.detail)
            var dates by rememberSaveable(item.id) { mutableStateOf(starts.isNotBlank() || ends.isNotBlank()) }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (doseTimes.isNotEmpty()) {
                    // Each dose time is its own optional reminder; turning a bell off never changes the dose times.
                    val ringing = option?.times ?: doseTimes
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        doseTimes.forEach { time ->
                            val on = time in ringing
                            FilterChip(selected = on, onClick = { runCatching {
                                val next = if (on) ringing - time else (ringing + time).distinct().sorted()
                                scheduler.configure(item.id, ReminderOption(next, option?.starts.orEmpty(), option?.ends.orEmpty(), option?.muted == true)); changed()
                            }.onFailure { error = it.message?.let { m -> t(m, ReminderTextHindi[m] ?: m) } ?: t("Check the times and dates.", "समय और तारीख़ें जाँचें।") } },
                                label = { Text(time, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }, leadingIcon = { Icon(if (on) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardSub,selectedContainerColor = DoctorStatusGoodBg, selectedLabelColor = DoctorCardInk, selectedLeadingIconColor = DoctorStatusGood),
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = time + ": " + if (on) t("Reminder on", "रिमाइंडर चालू") else t("Reminder off", "रिमाइंडर बंद") }.testTag("reminder-dose-${item.id}-$time"))
                        }
                    }
                } else CareField(times, { times = it; error = "" }, t("Time, e.g. 08:30", "समय, जैसे 08:30"), singleLine = true)
                if (dates) {
                    CareField(starts, { starts = it; error = "" }, t("Start date", "शुरू की तारीख़"), singleLine = true)
                    CareField(ends, { ends = it; error = "" }, t("Last date", "आख़िरी तारीख़"), singleLine = true)
                } else TextButton(onClick = { dates = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Start and end dates", "शुरू और आख़िरी तारीख़"), fontSize = 15.sp) }
                if (error.isNotBlank()) Text(error, color = RustText, fontSize = 15.sp, modifier = Modifier.announce())
                if (doseTimes.isEmpty() || dates) Button(onClick = { runCatching {
                    val chosen = if (doseTimes.isNotEmpty()) option?.times ?: doseTimes else if (times.isBlank()) emptyList() else times.split(',').map(String::trim)
                    scheduler.configure(item.id, ReminderOption(chosen, starts.trim(), ends.trim(), option?.muted == true)); if (doseTimes.isEmpty()) share?.invoke(chosen); changed()
                }.onFailure { error = it.message?.let { m -> t(m, ReminderTextHindi[m] ?: m) } ?: t("Check the times and dates.", "समय और तारीख़ें जाँचें।") } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Save", "सहेजें"), fontSize = 16.sp) }
            }
        } else if (dateOnly) {
            // Saved with a date only: no alert until a time on that day is chosen here (never a midnight alert).
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareField(times, { times = it; error = "" }, t("Time, e.g. 08:30", "समय, जैसे 08:30"), singleLine = true)
                if (error.isNotBlank()) Text(error, color = RustText, fontSize = 15.sp, modifier = Modifier.announce())
                Button(onClick = { runCatching {
                    scheduler.configure(item.id, ReminderOption(if (times.isBlank()) emptyList() else times.split(',').map(String::trim), muted = option?.muted == true)); changed()
                }.onFailure { error = it.message?.let { m -> t(m, ReminderTextHindi[m] ?: m) } ?: t("Use HH:mm, such as 08:30.", "HH:mm लिखें, जैसे 08:30।") } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reminder-save-${item.id}")) { Text(t("Save", "सहेजें"), fontSize = 16.sp) }
            }
        }
    }
}
