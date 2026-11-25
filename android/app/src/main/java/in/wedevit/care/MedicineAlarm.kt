package `in`.wedevit.care

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * "Taken" tapped on the alarm screen, waiting for Sahara to save it with the person's own records (the same path as a tick on
 * Today, so it syncs and the whole-day tick updates). Kept on this phone in private storage until saved; works offline.
 * The alarm screen never talks to the server itself, so it can never disturb the app's sign-in.
 */
internal object MedicineAlarmTakenStore {
    data class Taken(val personId: String, val itemId: String, val name: String, val at: Long, val time: String, val tappedAt: Long)

    /** Bumped on every change, so open screens save waiting ticks at once. */
    val revision = MutableStateFlow(0)
    private val lock = Any()
    private fun prefs(context: Context, account: String) = context.applicationContext.getSharedPreferences("medicine-alarm-taken-${account.ifBlank { "guest" }}", 0)

    private fun read(context: Context, account: String): List<Taken> = runCatching {
        val array = JSONArray(prefs(context, account).getString("waiting", "[]"))
        (0 until array.length()).map(array::getJSONObject).map { Taken(it.getString("person"), it.getString("item"), it.optString("name"), it.getLong("at"), it.getString("time"), it.optLong("tapped")) }
    }.getOrDefault(emptyList())

    private fun write(context: Context, account: String, list: List<Taken>) {
        val array = JSONArray(); list.forEach { array.put(JSONObject().put("person", it.personId).put("item", it.itemId).put("name", it.name).put("at", it.at).put("time", it.time).put("tapped", it.tappedAt)) }
        prefs(context, account).edit().putString("waiting", array.toString()).commit()
    }

    fun add(context: Context, alarm: MedicineAlarmRules.Alarm, now: Long = System.currentTimeMillis()) {
        if (alarm.preview) return
        synchronized(lock) {
            val kept = read(context, alarm.account).filterNot { w -> w.at == alarm.at && w.itemId in alarm.itemIds }
            write(context, alarm.account, kept + alarm.medicines.map { (id, name) -> Taken(alarm.personId, id, name, alarm.at, alarm.time, now) })
        }
        revision.value++
    }

    /** Waiting ticks for [personId] (or everyone when blank), without removing them. */
    fun waiting(context: Context, account: String, personId: String = ""): List<Taken> = synchronized(lock) { read(context, account).filter { personId.isBlank() || it.personId == personId } }

    /** Takes one waiting tick out of the store (so two screens never save it twice); [putBack] returns it after a failed save. */
    fun take(context: Context, account: String, taken: Taken): Boolean = synchronized(lock) {
        val all = read(context, account)
        if (taken in all) { write(context, account, all - taken); true } else false
    }
    fun putBack(context: Context, account: String, taken: Taken) = synchronized(lock) { write(context, account, (read(context, account) - taken) + taken) }

    /** Drops ticks older than two days that could not be matched to a dose (the medicine was removed or its time changed). */
    fun prune(context: Context, account: String, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        write(context, account, read(context, account).filter { now - it.at < 2 * 86_400_000L })
    }
}

private val alarmSaveLock = Mutex()

/**
 * Saves every waiting alarm "Taken" for the selected person, as an ordinary dose tick ([recordMedicineDose]). Put on any screen
 * that is open while the app is used (Today, Medicines, Reminders); it does nothing when nothing waits.
 */
@Composable internal fun MedicineAlarmTakenSync(vm: CareModel) {
    val context = LocalContext.current
    val revision by MedicineAlarmTakenStore.revision.collectAsStateWithLifecycle()
    val person by vm.selected.collectAsStateWithLifecycle()
    val items by vm.allItems.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    LaunchedEffect(revision, person, items.size, canEdit) {
        if (!canEdit || person.isBlank()) return@LaunchedEffect
        alarmSaveLock.withLock {
            val account = vm.accountNamespace
            val zone = ZoneId.systemDefault()
            val scheduler = CareReminderScheduler(context.applicationContext, account)
            var saved = false
            MedicineAlarmTakenStore.waiting(context, account, person).forEach { waiting ->
                val item = vm.allItems.value.firstOrNull { it.id == waiting.itemId && it.personId == waiting.personId && it.kind == "medicine" } ?: return@forEach
                val doses = MedicineDoses.forItem(item, scheduler.option(item.id)?.times)
                val dose = doses.firstOrNull { it.timed && it.time == waiting.time } ?: return@forEach
                val day = Instant.ofEpochMilli(waiting.at).atZone(zone).toLocalDate()
                if (!MedicineAlarmTakenStore.take(context, account, waiting)) return@forEach
                if (MedicineDoses.state(item, dose, vm.medicineEvents.value, day, zone) == MedicineDoseState.TAKEN) return@forEach
                try { recordMedicineDose(context, vm, item, doses, dose, true, day); saved = true }
                catch (e: Exception) { if (e is CancellationException) { MedicineAlarmTakenStore.putBack(context, account, waiting); throw e }; MedicineAlarmTakenStore.putBack(context, account, waiting) }
            }
            MedicineAlarmTakenStore.prune(context, account)
            if (saved) runCatching { scheduler.reschedule() }
        }
    }
}

/**
 * Snooze pressed outside the alarm screen (its notification's Snooze): the alarm key, so an alarm screen still ringing for it goes
 * quiet and closes. The receiver and the screen run in the same process.
 */
internal object MedicineAlarmSignals {
    val snoozed = MutableSharedFlow<String>(extraBufferCapacity = 16)
}

/**
 * The alarm sound: the phone's own alarm tone on the alarm volume, looping, starting soft and rising over a few seconds (calm, not a
 * jolt), with a slow vibration. [duck] lowers it while the voice speaks. Everything fails quietly: a phone without a tone still speaks.
 */
internal class MedicineAlarmRinger(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var level = 0.25f
    private var ducked = false
    private var started = false
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val rise = object : Runnable { override fun run() { if (level < 1f) { level = (level + .1f).coerceAtMost(1f); apply(); handler.postDelayed(this, 1500) } } }

    /**
     * Starts the tone and vibration; true when a tone is actually playing. The settings URI comes first: Android 7+ serves a copy any
     * app can read, while the actual URI of a tone picked from storage needs a media permission Sahara does not hold. Each one that
     * fails is released and the next is tried.
     */
    fun start(): Boolean {
        if (started) return player != null
        started = true
        val candidates = listOf(
            runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) }.getOrNull(),
            runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) }.getOrNull(),
            runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) }.getOrNull(),
        )
        MedicineAlarmRules.firstWorking(candidates) { uri ->
            val p = MediaPlayer()
            try { p.setAudioAttributes(attributes); p.setDataSource(context, uri); p.isLooping = true; p.prepare(); p.start(); player = p; true }
            catch (_: Exception) { runCatching { p.release() }; false }
        }
        if (player != null) { apply(); handler.postDelayed(rise, 1500) }
        vibrate()
        return player != null
    }

    private fun apply() { val v = if (ducked) level * .15f else level; runCatching { player?.setVolume(v, v) } }
    fun duck(on: Boolean) { ducked = on; apply() }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else context.getSystemService(Vibrator::class.java)
    private fun vibrate() {
        // Needs android.permission.VIBRATE; without it the alarm still rings and speaks.
        try { vibrator()?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 1300), 0), attributes) } catch (_: SecurityException) {} catch (_: RuntimeException) {}
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }; runCatching { player?.release() }; player = null
        try { vibrator()?.cancel() } catch (_: SecurityException) {} catch (_: RuntimeException) {}
        started = false
    }
}

/**
 * The alarm's voice: Android's own text-to-speech on the alarm volume, in the app's language (English words when the phone has
 * no voice for it, as in Simple Mode). [speaking] reports when it starts and stops, so the ring can step back while it talks.
 */
internal class MedicineAlarmVoice(context: Context, private val speaking: (Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var pending: Pair<CareLang, (CareLang) -> String>? = null
    private var count = 0
    private var last = ""

    init {
        engine = TextToSpeech(context.applicationContext) { status -> handler.post {
            if (closed) return@post
            ready = status == TextToSpeech.SUCCESS
            engine?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { handler.post { if (!closed) speaking(true) } }
                override fun onDone(utteranceId: String?) { handler.post { if (!closed && utteranceId == last) speaking(false) } }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { handler.post { if (!closed) speaking(false) } }
                override fun onError(utteranceId: String?, errorCode: Int) { handler.post { if (!closed) speaking(false) } }
            })
            val waiting = pending; pending = null
            if (ready && waiting != null) speakNow(waiting.first, waiting.second)
        } }
    }

    fun say(language: CareLang, text: (CareLang) -> String) {
        if (closed) return
        if (!ready) { pending = language to text; return }
        speakNow(language, text)
    }

    private fun locale(lang: CareLang) = Locale(lang.code, "IN")
    private fun speakNow(language: CareLang, text: (CareLang) -> String) {
        val tts = engine ?: return
        val voice = SimpleModeRules.voiceLanguage(language) { runCatching { tts.isLanguageAvailable(locale(it)) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false) }
        runCatching { tts.setLanguage(locale(voice)) }
        val words = text(voice).trim(); if (words.isEmpty()) return
        val chunks = CareDraftAssist.speechChunks(words, TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1))
        chunks.forEachIndexed { index, chunk ->
            val id = "alarm-${count++}"; if (index == chunks.lastIndex) last = id
            tts.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    fun stop() { pending = null; runCatching { engine?.stop() }; speaking(false) }
    fun close() { closed = true; pending = null; runCatching { engine?.stop(); engine?.shutdown() }; engine = null }
}

/** What the alarm says, in [lang]: "Time for your night medicine, Morphine." Medicine names are spoken as saved. */
internal fun medicineAlarmSpeech(alarm: MedicineAlarmRules.Alarm, lang: CareLang): String {
    val prompt = careTr(lang, MedicineAlarmRules.promptEnglish(alarm.slot), MedicineAlarmRules.promptHindi(alarm.slot))
    val names = MedicineAlarmRules.joinNames(alarm.names, careTr(lang, "and", "और"))
    return if (names.isBlank()) "$prompt." else "$prompt, $names."
}
