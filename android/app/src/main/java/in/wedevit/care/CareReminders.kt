package `in`.wedevit.care

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.util.UUID

/** One next inexact alarm per chosen slot, persisted privately and reconciled from actual saved records. */
class CareReminderScheduler(private val context: Context, private val account: String) {
    private val prefs = context.getSharedPreferences("care-reminders-${account.ifBlank { "guest" }}", 0)
    private val active = context.getSharedPreferences("care-reminder-active", 0)
    init { require(account.isBlank() || runCatching { UUID.fromString(account) }.isSuccess) }
    @Synchronized fun activate() {
        val previous = active.getString("account", null)
        if (previous != null && previous != account) CareReminderScheduler(context, previous).cancelAll()
        active.edit().putString("account", account).commit()
        createChannel(); reschedule()
    }
    @Synchronized fun deactivate() { cancelAll(); if (active.getString("account", null) == account) active.edit().remove("account").commit() }
    fun enabled() = prefs.getBoolean("enabled", false)
    fun allowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        (Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    @Synchronized fun enable(value: Boolean) { prefs.edit().putBoolean("enabled", value).commit(); if (value) reschedule() else cancelAll() }
    fun option(id: String): ReminderOption? = runCatching {
        val j = JSONObject(prefs.getString("option:$id", null) ?: return null)
        ReminderOption(j.getJSONArray("times").let { a -> (0 until a.length()).map(a::getString) }, j.optString("starts"), j.optString("ends"), j.optBoolean("muted"))
    }.getOrNull()
    @Synchronized fun configure(id: String, option: ReminderOption) {
        require(runCatching { UUID.fromString(id) }.isSuccess)
        val times = CareReminderRules.validTimes(option.times); CareReminderRules.validateDates(option.starts, option.ends)
        check(prefs.edit().putString("option:$id", JSONObject().put("times", JSONArray(times)).put("starts", option.starts).put("ends", option.ends).put("muted", option.muted).toString()).commit())
        cancelVisibleNotifications()
        reschedule()
    }
    @Synchronized fun reconcile(items: List<CareItem>) {
        cancelVisibleNotifications()
        val snapshots = JSONArray()
        items.forEach { item ->
            if (item.kind != "medicine" && item.due <= 0) return@forEach
            val entry = CareEntryCodec.decode(item.detail)
            val complete = entry?.status in setOf("completed", "cancelled") || (item.kind != "medicine" && item.doneOn.isNotBlank())
            snapshots.put(JSONObject().put("id", item.id).put("person", item.personId).put("kind", item.kind).put("title", if (item.kind == "medicine") item.title else "")
                .put("due", item.due).put("dateOnly", CareReminderRules.dateOnly(item)).put("times", JSONArray(CareReminderRules.printedTimes(item.detail)))
                .put("doneOn", item.doneOn).put("finished", complete))
        }
        check(prefs.edit().putString("items", snapshots.toString()).commit())
        val ids = items.map { it.id }.toSet()
        prefs.edit().also { editor -> prefs.all.keys.filter { it.startsWith("option:") && it.removePrefix("option:") !in ids }.forEach(editor::remove) }.commit()
        reschedule()
    }
    private fun plans(): List<CareReminderPlan> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val j = array.getJSONObject(index); val option = option(j.getString("id"))
            if (option?.muted == true) null else CareReminderPlan(j.getString("id"), j.getString("person"), j.getString("kind"), j.optLong("due"), option?.times ?: j.getJSONArray("times").let { a -> (0 until a.length()).map(a::getString) }, j.optString("doneOn"), j.optBoolean("finished"), option?.starts.orEmpty(), option?.ends.orEmpty(), j.optBoolean("dateOnly"))
        }
    }.getOrDefault(emptyList())
    private fun pending(key: String, at: Long, create: Boolean): PendingIntent? = PendingIntent.getBroadcast(context, 0,
        Intent(context, CareReminderReceiver::class.java).apply { data = Uri.Builder().scheme("sahara-reminder").authority(context.packageName).appendPath(account.ifBlank { "guest" }).appendPath(key).build(); putExtra("account", account); putExtra("key", key); putExtra("at", at) },
        (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE)
    private fun cancelAlarms() {
        val alarm = context.getSystemService(AlarmManager::class.java)
        prefs.getStringSet("scheduled", emptySet()).orEmpty().forEach { key -> pending(key, 0, false)?.let { alarm.cancel(it); it.cancel() } }
        prefs.edit().remove("scheduled").remove("occurrences").commit()
    }
    private fun cancelVisibleNotifications() {
        prefs.getStringSet("notificationKeys", emptySet()).orEmpty().forEach { key -> NotificationManagerCompat.from(context).cancel(notificationId(account, key)) }
        prefs.edit().remove("notificationKeys").commit()
    }
    @Synchronized fun cancelAll() { cancelAlarms(); cancelVisibleNotifications() }
    @Synchronized fun reschedule(now: Long = System.currentTimeMillis()) {
        cancelAlarms()
        if (!enabled() || !allowed() || active.getString("account", null) != account) return
        createChannel()
        val next = plans().flatMap { runCatching { CareReminderRules.next(it, now, ZoneId.systemDefault()) }.getOrDefault(emptyList()) }.take(200)
        val saved = JSONArray()
        val keys = mutableSetOf<String>()
        next.forEach { occurrence ->
            try {
                val intent = requireNotNull(pending(occurrence.key, occurrence.at, true))
                val alarm = context.getSystemService(AlarmManager::class.java)
                // On time when Android allows exact alarms; otherwise Android may batch the alert later (the 6-hour delivery window still applies).
                if (exactAllowed()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.at, intent)
                else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.at, intent)
                keys += occurrence.key
                saved.put(JSONObject().put("key", occurrence.key).put("id", occurrence.itemId).put("at", occurrence.at).put("date", occurrence.localDate).put("time", occurrence.time))
            } catch (_: SecurityException) { prefs.edit().putString("error", careWithDeviceLanguage(context) { careTr(careDeviceLanguage(context), "Android blocked reminders. Check notification and battery settings.", "Android ने रिमाइंडर रोक दिए। सूचना और बैटरी सेटिंग जाँचें।") }).commit() }
        }
        prefs.edit().putStringSet("scheduled", keys).putString("occurrences", saved.toString()).commit()
    }
    @Synchronized fun fire(key: String, at: Long, now: Long = System.currentTimeMillis()) {
        val array = runCatching { JSONArray(prefs.getString("occurrences", "[]")) }.getOrElse { JSONArray() }
        val rows = (0 until array.length()).map { array.getJSONObject(it) }
        val expected = rows.firstOrNull { it.optString("key") == key }?.let { CareReminderOccurrence(key, it.getString("id"), it.getLong("at"), it.getString("date"), it.getString("time")) }
        val permitted = enabled() && allowed() && active.getString("account", null) == account
        val deliver = expected != null && CareReminderRules.shouldDeliver(expected, at, now, prefs.getLong("sent:$key", 0), permitted)
        if (deliver) prefs.edit().putLong("sent:$key", at).commit()
        // Medicines due at this same moment share one reminder per person (read before rescheduling moves their slots on).
        val doses = if (deliver && expected != null) MedicineDoseRules.dueTogether(rows.map { DoseOccurrence(it.optString("id"), it.optLong("at"), it.optString("time")) }, snapshotMedicines(), expected.at) else emptyList()
        // Always advance this slot before publishing; repeated broadcasts cannot send duplicate alerts.
        reschedule(now)
        doses.forEach { group -> notifyDoses(group, expected!!.at) }
        if (deliver && doses.none { group -> group.medicines.any { it.itemId == expected?.itemId } }) {
            // Tap: select the person whose activity is due (CareTodayRules), then open Reminders (CareReminderLaunch).
            val launch = PendingIntent.getActivity(context, notificationId(account, key), Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP; putExtra(CareReminderLaunch.EXTRA, true); putExtra(CareTodayRules.OPEN_PERSON_EXTRA, CareTodayRules.personForItem(prefs.getString("items", null), expected?.itemId.orEmpty())) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_sahara).setColor(SAHARA_RUST)
                .setContentTitle(reminderText("Sahara reminder")).setContentText(reminderText("A saved care activity is due. Open Sahara to check the record."))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(launch).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
            try {
                NotificationManagerCompat.from(context).notify(notificationId(account, key), notification)
                prefs.edit().putStringSet("notificationKeys", prefs.getStringSet("notificationKeys", emptySet()).orEmpty() + key).commit()
            } catch (_: SecurityException) { /* A permission changed after the check. Keep the record and let settings show the denial. */ }
        }
    }
    private fun snapshotMedicines(): List<DoseMedicine> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map(array::getJSONObject).filter { it.optString("kind") == "medicine" }.map { DoseMedicine(it.getString("id"), it.getString("person"), it.optString("title"), emptyList()) }
    }.getOrDefault(emptyList())
    /** "Morning · 08:00 · 3 medicines" with the strip photos kept on this phone. The lock screen shows only the generic public text. */
    private fun notifyDoses(group: DoseGroup, at: Long) {
        val groupKey = "dose:${group.personId}:$at"
        if (prefs.getLong("sent:$groupKey", 0) == at) return
        prefs.edit().putLong("sent:$groupKey", at).commit()
        val content = careWithDeviceLanguage(context) { MedicineDoseRules.reminder(group, careDeviceHindi(context)) }
        val photos = runCatching { MedicineStripPhotos(context, account).collage(group.personId, content.itemIds) }.getOrNull()
        val launch = PendingIntent.getActivity(context, notificationId(account, groupKey), Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP; putExtra(CareReminderLaunch.EXTRA, true); putExtra(CareTodayRules.OPEN_PERSON_EXTRA, group.personId) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hidden = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_medicine).setColor(SAHARA_RUST).setContentTitle(content.publicTitle).setContentText(content.publicText).build()
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_medicine).setColor(SAHARA_RUST)
            .setContentTitle(content.title).setContentText(content.text.lineSequence().first())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(hidden).setContentIntent(launch).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (photos != null) builder.setLargeIcon(photos).setStyle(NotificationCompat.BigPictureStyle().bigPicture(photos).setSummaryText(content.text.replace('\n', ' ')))
        else builder.setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
        try {
            NotificationManagerCompat.from(context).notify(notificationId(account, groupKey), builder.build())
            prefs.edit().putStringSet("notificationKeys", prefs.getStringSet("notificationKeys", emptySet()).orEmpty() + groupKey).commit()
        } catch (_: SecurityException) { /* A permission changed after the check. */ }
    }
    /** Android 12+ asks the person to allow exact alarms; earlier versions always allow them. */
    /** Notification text follows the phone's last English/Hindi choice. */
    private fun reminderText(english: String): String = careWithDeviceLanguage(context) { careTr(careDeviceHindi(context), english, ReminderTextHindi[english] ?: english) }
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun scheduledCount(): Int = prefs.getStringSet("scheduled", emptySet()).orEmpty().size
    fun error(): String? = prefs.getString("error", null)
    private fun createChannel() { context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, reminderText("Care reminders"), NotificationManager.IMPORTANCE_DEFAULT).apply { description = reminderText("Reminders for your chosen medicine times and dated care activities. Android may delay alerts."); lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE }) }
    companion object {
        const val CHANNEL = "sahara-care-reminders"
        /** Sahara Rust (#B9472C), the accent Android uses for the small icon and app name in the shade. */
        private const val SAHARA_RUST = 0xFFB9472C.toInt()
        fun active(context: Context): String? = context.getSharedPreferences("care-reminder-active", 0).getString("account", null)
        private fun notificationId(account: String, key: String) = ("$account:$key".hashCode() and Int.MAX_VALUE)
    }
}
/** Set when a reminder notification is tapped; the care screen opens Reminders once and clears it. */
object CareReminderLaunch {
    const val EXTRA = "open_reminders"
    val pending = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun offer(intent: Intent?) { if (intent?.getBooleanExtra(EXTRA, false) == true) { pending.value = true; intent.removeExtra(EXTRA) } }
}
data class ReminderOption(val times: List<String>, val starts: String = "", val ends: String = "", val muted: Boolean = false)

class CareReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.component?.className != javaClass.name) return
        val account = intent.getStringExtra("account") ?: return; val key = intent.getStringExtra("key") ?: return
        runCatching { CareReminderScheduler(context.applicationContext, account).fire(key, intent.getLongExtra("at", 0)) }
    }
}
class CareReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        CareReminderScheduler.active(context)?.let { account -> runCatching { CareReminderScheduler(context.applicationContext, account).reschedule() } }
    }
}
