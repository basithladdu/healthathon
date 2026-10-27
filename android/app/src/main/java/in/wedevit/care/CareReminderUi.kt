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

@Composable fun CareReminderSettings(vm: CareModel, onClose: () -> Unit) { val t = careText();
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val items by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scheduler.enable(true)
        else { scheduler.enable(false); error = t("Notifications are off. Your care records are still saved. You can allow them in Android Settings.", "सूचनाएँ बंद हैं। आपके देखभाल रिकॉर्ड सहेजे हुए हैं। आप इन्हें Android Settings में चालू कर सकते हैं।") }
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
        item { Row { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }; Heading(t("Reminders", "रिमाइंडर"), 28) } }
        item { SurfaceCard(Sage) {
            Row { Column(Modifier.weight(1f)) { Text(t("Care reminders", "देखभाल रिमाइंडर"), fontWeight = FontWeight.SemiBold); Text(if (enabled && allowed) t("$count next alerts scheduled", "अगले $count अलर्ट तय हैं") else t("Off", "बंद"), style = MaterialTheme.typography.bodySmall) }; Switch(checked = enabled && allowed, onCheckedChange = { turnOn ->
                error = ""
                if (!turnOn) scheduler.enable(false)
                else if (Build.VERSION.SDK_INT >= 33 && !allowed) request.launch(Manifest.permission.POST_NOTIFICATIONS)
                else if (!allowed) error = t("Allow Sahara notifications in Android Settings, then return here.", "Android Settings में सहारा की सूचनाएँ चालू करें, फिर यहाँ लौटें।")
                else scheduler.enable(true)
                revision++
            }) }
            Text(t("Alerts can be delayed by Android, battery settings or a switched-off phone. They are a reminder to check the record, not an urgent-care service.", "Android, बैटरी सेटिंग या बंद फ़ोन की वजह से अलर्ट देर से आ सकते हैं। ये रिकॉर्ड देखने की याद दिलाते हैं, आपातकालीन सेवा नहीं हैं।"), style = MaterialTheme.typography.bodySmall)
            Text(t("Only the active account sends alerts. Medicine times follow the phone’s current time zone; dated visits keep their saved time.", "सिर्फ़ चालू खाता अलर्ट भेजता है। दवा का समय फ़ोन के अभी के समय क्षेत्र से चलता है; तय मुलाक़ातें अपने सहेजे समय पर रहती हैं।"), style = MaterialTheme.typography.bodySmall)
            if (!allowed) TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }.onFailure { error = t("Open Android Settings → Apps → Sahara → Notifications.", "Android Settings → Apps → Sahara → Notifications खोलें।") } }) { Text(t("Open notification settings", "सूचना सेटिंग खोलें")) }
            if (enabled && allowed && !exact && Build.VERSION.SDK_INT >= 31) {
                Text(t("Android may send these alerts late. Allow on-time alerts so they arrive at the set time.", "Android ये सूचनाएँ देर से भेज सकता है। समय पर सूचना की अनुमति दें, ताकि वे तय समय पर आएँ।"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("reminder-exact-off"))
                TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }.onFailure { error = t("Open Android Settings → Apps → Sahara → Alarms & reminders.", "Android सेटिंग्स → ऐप्स → Sahara → अलार्म और रिमाइंडर खोलें।") } }) { Text(t("Allow on-time alerts", "समय पर सूचना की अनुमति दें")) }
            }
        } }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item { Text(t("Choose times from the existing prescription. Sahara does not calculate or change doses. Marking a medicine done completes that whole entry for the day.", "पर्चे में लिखे समय चुनें। सहारा ख़ुराक तय या नहीं बदलता। दवा को 'हो गया' करने से उस दिन की पूरी दवा पूरी मानी जाती है।"), style = MaterialTheme.typography.bodySmall) }
        val today = java.time.LocalDate.now()
        val eligible = items.filter { it.personId == selected && CareReminderRules.listed(it, today) }
        if (eligible.isEmpty()) item { SurfaceCard { Text(t("No dated care activities or medicines yet.", "अभी कोई तारीख़ वाली देखभाल या दवा नहीं है।")); Text(t("Add a medicine or choose a time in Calendar first.", "पहले दवा जोड़ें या कैलेंडर में समय चुनें।"), style = MaterialTheme.typography.bodySmall) } }
        items(eligible, key = { it.id }) { item -> ReminderItemSettings(item, scheduler, revision) { revision++ } }
    }
}

@Composable private fun ReminderItemSettings(item: CareItem, scheduler: CareReminderScheduler, revision: Int, changed: () -> Unit) { val t = careText();
    val option = remember(item.id, revision) { scheduler.option(item.id) }
    var times by rememberSaveable(item.id) { mutableStateOf((option?.times ?: CareReminderRules.printedTimes(item.detail)).joinToString(", ")) }
    var starts by rememberSaveable(item.id) { mutableStateOf(option?.starts.orEmpty()) }
    var ends by rememberSaveable(item.id) { mutableStateOf(option?.ends.orEmpty()) }
    var error by rememberSaveable(item.id) { mutableStateOf("") }
    val dateOnly = CareReminderRules.dateOnly(item)
    SurfaceCard {
        Row { Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text(when { option?.muted == true -> t("Paused", "रुका हुआ"); item.kind == "medicine" -> (option?.times ?: CareReminderRules.printedTimes(item.detail)).joinToString(", ").ifBlank { t("No time chosen", "समय नहीं चुना") }
            dateOnly -> DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.due)) + " · " + option?.times.orEmpty().joinToString(", ").ifBlank { t("No alert time chosen", "सूचना का समय नहीं चुना") }
            item.due > 0 -> DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.due)); else -> t("No time chosen", "समय नहीं चुना") }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("reminder-when-${item.id}")) }
            Switch(checked = option?.muted != true, onCheckedChange = { enabled -> scheduler.configure(item.id, (option ?: ReminderOption(CareReminderRules.printedTimes(item.detail))).copy(muted = !enabled)); changed() }) }
        if (item.kind == "medicine") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareField(times, { times = it; error = "" }, t("Times · HH:mm, separated by commas", "समय · HH:mm, कॉमा लगाकर"), singleLine = true)
                CareField(starts, { starts = it; error = "" }, t("Start date · YYYY-MM-DD (optional)", "शुरू की तारीख़ · YYYY-MM-DD (चाहें तो)"), singleLine = true)
                CareField(ends, { ends = it; error = "" }, t("Last date · YYYY-MM-DD (optional)", "आख़िरी तारीख़ · YYYY-MM-DD (चाहें तो)"), singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { runCatching {
                    scheduler.configure(item.id, ReminderOption(if (times.isBlank()) emptyList() else times.split(',').map(String::trim), starts.trim(), ends.trim(), option?.muted == true)); changed()
                }.onFailure { error = it.message ?: t("Check the times and dates.", "समय और तारीख़ें जाँचें।") } }, modifier = Modifier.fillMaxWidth()) { Text(t("Save reminder times", "रिमाइंडर का समय सहेजें")) }
            }
        } else if (dateOnly) {
            // Saved with a date only: no alert until a time on that day is chosen here (never a midnight alert).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareField(times, { times = it; error = "" }, t("Alert time on that day (HH:mm)", "उस दिन सूचना का समय (HH:mm)"), singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { runCatching {
                    scheduler.configure(item.id, ReminderOption(if (times.isBlank()) emptyList() else times.split(',').map(String::trim), muted = option?.muted == true)); changed()
                }.onFailure { error = it.message ?: t("Use HH:mm, such as 08:30.", "HH:mm लिखें, जैसे 08:30।") } }, modifier = Modifier.fillMaxWidth().testTag("reminder-save-${item.id}")) { Text(t("Save alert time", "समय सहेजें")) }
            }
        }
    }
}
