package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * The full-screen spoken alarm for an important medicine (point 10): rings with the phone's alarm tone, says "Time for your night
 * medicine, Morphine" in the app's language (and again every [MedicineAlarmRules.SPEAK_EVERY_MS]), and shows each strip photo large
 * with a big green Taken tick and Snooze. It opens over the lock screen from the alarm notification's full-screen intent.
 *
 * Taken is kept on the phone ([MedicineAlarmTakenStore]) and saved as an ordinary dose tick the moment Sahara is open, so it works
 * offline and syncs later. Back silences the ring; the notification stays until Taken. Unanswered, it snoozes itself after
 * [MedicineAlarmRules.RING_FOR_MS] (a few times) and closes, so the screen never stays lit.
 *
 * Needs, in AndroidManifest.xml: the activity entry (showWhenLocked, turnScreenOn, excludeFromRecents) and the
 * USE_FULL_SCREEN_INTENT and VIBRATE permissions. Without them the alarm is an insistent heads-up notification that opens this screen.
 */
class MedicineAlarmActivity : ComponentActivity() {
    private lateinit var ringer: MedicineAlarmRinger
    private lateinit var voice: MedicineAlarmVoice
    private val handler = Handler(Looper.getMainLooper())
    private var alarm by mutableStateOf<MedicineAlarmRules.Alarm?>(null)
    private var done by mutableStateOf(false)
    private var language = CareLang.EN
    /** When this alarm started ringing, kept across a recreation so the ring does not start over. */
    private var ringStartedAt = 0L
    private val repeatSpeech = object : Runnable { override fun run() { speak(); handler.postDelayed(this, MedicineAlarmRules.SPEAK_EVERY_MS) } }
    /** Nobody answered: the screen may sleep again, the alarm snoozes itself (a few times) and this screen closes. */
    private val ringTimeout = Runnable {
        silence()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val current = alarm
        if (current != null && !current.preview) runCatching { CareReminderScheduler(applicationContext, current.account).autoSnooze(current) }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        if (!BuildConfig.DEBUG && !BuildConfig.ALLOW_SCREENSHOTS) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Recreated (dark mode, display size): the saved alarm and its state win over the intent that first opened this screen.
        val restored = savedInstanceState?.let { MedicineAlarmRules.decode(it.getString(STATE_ALARM)) }
        val first = restored ?: MedicineAlarmRules.decode(intent?.getStringExtra(EXTRA_ALARM)) ?: run { finish(); return }
        ringer = MedicineAlarmRinger(this)
        voice = MedicineAlarmVoice(this) { speaking -> if (!done) ringer.duck(speaking) }
        // After Taken, Back waits for the short "Marked as taken" hand-off to Sahara instead of cancelling it.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { if (done) return; silence(); finish() } })
        // Snooze pressed on the notification while this screen rings: go quiet and close.
        lifecycleScope.launch { MedicineAlarmSignals.snoozed.collect { key -> if (!done && alarm?.key == key) { silence(); finish() } } }
        when {
            restored == null -> open(first, intent?.getBooleanExtra(EXTRA_TAKEN_NOW, false) == true)
            savedInstanceState?.getBoolean(STATE_DONE) == true -> { alarm = first; done = true; language = CareLanguageChoice.of(this, first.account).lang; handOff(first, 0) }
            else -> {
                val started = savedInstanceState?.getLong(STATE_STARTED) ?: 0L
                val left = MedicineAlarmRules.ringLeft(started, System.currentTimeMillis())
                if (left <= 0) { alarm = first; ringTimeout.run() } else open(first, false, started, left)
            }
        }
        CareLanguageState.current // Created outside a Compose snapshot, as in MainActivity.
        setContent {
            CareTheme {
                val shown = alarm ?: return@CareTheme
                val choice = rememberCareLanguage(shown.account)
                SideEffect { language = choice.lang }
                CompositionLocalProvider(LocalCareLanguage provides choice) {
                    CareLanguageDirection(choice) {
                        val photos = remember(shown) { photosFor(this@MedicineAlarmActivity, shown) }
                        MedicineAlarmScreen(shown, photos, done, onTaken = ::taken, onSnooze = ::snooze, onReplay = ::speak)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        alarm?.let { outState.putString(STATE_ALARM, MedicineAlarmRules.encode(it)) }
        outState.putBoolean(STATE_DONE, done); outState.putLong(STATE_STARTED, ringStartedAt)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        val next = MedicineAlarmRules.decode(intent.getStringExtra(EXTRA_ALARM)) ?: return
        open(next, intent.getBooleanExtra(EXTRA_TAKEN_NOW, false))
    }

    private fun open(next: MedicineAlarmRules.Alarm, takenNow: Boolean, startedAt: Long = System.currentTimeMillis(), ringFor: Long = MedicineAlarmRules.RING_FOR_MS) {
        alarm = next; done = false
        language = CareLanguageChoice.of(this, next.account).lang
        if (takenNow) { taken(); return }
        handler.removeCallbacksAndMessages(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ringStartedAt = startedAt
        // The notification goes quiet so this screen's own ring and voice are the only sound, but only once the tone really plays:
        // otherwise the notification's insistent alarm sound stays the ring.
        val ringing = ringer.start()
        if (ringing && !next.preview) runCatching { CareReminderScheduler(applicationContext, next.account).quietAlarm(next) }
        // A short pause so the first words are not lost under the first ring.
        handler.postDelayed(repeatSpeech, 1200)
        handler.postDelayed(ringTimeout, ringFor)
    }

    private fun speak() { val current = alarm ?: return; voice.say(language) { lang -> medicineAlarmSpeech(current, lang) } }

    /** Stops the ring and the voice. Only the repeating words and the timeout are cleared, never the hand-off after Taken. */
    private fun silence() {
        handler.removeCallbacks(repeatSpeech); handler.removeCallbacks(ringTimeout)
        if (::ringer.isInitialized) ringer.stop()
        if (::voice.isInitialized) voice.stop()
    }

    private fun taken() {
        val current = alarm ?: return
        silence(); done = true
        if (!current.preview) {
            MedicineAlarmTakenStore.add(this, current)
            runCatching { CareReminderScheduler(applicationContext, current.account).alarmTaken(current) }
        }
        voice.say(language) { lang -> careTr(lang, "Marked as taken.", "ली हुई दर्ज की गई।") }
        handOff(current, 1600)
    }

    /** Opens Sahara on this person's Today, where the tick is saved with their records (after unlocking, when locked), then closes. */
    private fun handOff(current: MedicineAlarmRules.Alarm, delay: Long) {
        handler.postDelayed({
            if (!current.preview) runCatching { startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP; putExtra(CareTodayRules.OPEN_PERSON_EXTRA, current.personId) }) }
            finish()
        }, delay)
    }

    private fun snooze() {
        val current = alarm ?: return
        silence()
        if (!current.preview) runCatching { CareReminderScheduler(applicationContext, current.account).snooze(current) }
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        silence(); handler.removeCallbacksAndMessages(null)
        if (::voice.isInitialized) voice.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ALARM = "medicine_alarm"
        /** From the notification's Taken button: record and close without ringing. */
        const val EXTRA_TAKEN_NOW = "medicine_alarm_taken"
        private const val STATE_ALARM = "alarm"
        private const val STATE_DONE = "done"
        private const val STATE_STARTED = "ring_started"

        fun intent(context: Context, alarm: MedicineAlarmRules.Alarm, takenNow: Boolean = false): Intent = Intent(context, MedicineAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(EXTRA_ALARM, MedicineAlarmRules.encode(alarm)); putExtra(EXTRA_TAKEN_NOW, takenNow)
        }

        /** The first strip photo of each medicine, kept on this phone. */
        internal fun photosFor(context: Context, alarm: MedicineAlarmRules.Alarm): Map<String, File?> {
            val store = runCatching { MedicineStripPhotos(context.applicationContext, alarm.account) }.getOrNull()
            return alarm.itemIds.associateWith { id -> store?.let { runCatching { it.first(alarm.personId, id) }.getOrNull() } }
        }
    }
}

/**
 * The alarm screen: the part-of-day picture and words at the top with "Say it again", each strip photo large with its name, then a big
 * green Taken tick and Snooze. Text is 20 sp or more on high contrast, nothing has a fixed height (the largest font size wraps, never
 * clips), and everything above Taken and Snooze scrolls, so the photos keep at least 160 dp. After Taken, a tick draws itself and the screen closes.
 */
@Composable internal fun MedicineAlarmScreen(alarm: MedicineAlarmRules.Alarm, photos: Map<String, File?>, done: Boolean, onTaken: () -> Unit, onSnooze: () -> Unit, onReplay: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val slot = alarm.slot
    val tone = doseSlotTone(slot)
    val haptic = rememberSaharaHaptic(); val tick = rememberSaharaTick()
    val minutes = MedicineAlarmRules.SNOOZE_MINUTES
    Surface(color = Cream, modifier = Modifier.fillMaxSize().testTag("medicine-alarm")) {
        SaharaStateSwitch(done, Modifier.fillMaxSize(), label = "medicine-alarm-state") { finished ->
            if (finished) Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                DoseStateMark(MedicineDoseState.TAKEN, 160, modifier = Modifier.testTag("medicine-alarm-done"))
                Spacer(Modifier.height(24.dp))
                Text(t("Marked as taken.", "ली हुई दर्ज की गई।"), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, textAlign = TextAlign.Center, modifier = Modifier.announce())
            } else Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Everything above Taken and Snooze scrolls together, so at the largest font the photos are never squeezed away;
                // Taken and Snooze stay at the bottom.
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val single = alarm.medicines.size == 1
                    // One photo fills what the words leave at an ordinary font size; never smaller than 160 dp, scrolling when needed.
                    val side = (if (single) minOf(maxWidth.value - 18f, maxHeight.value - 240f) else minOf(maxWidth.value - 18f, 260f)).coerceAtLeast(160f).toInt()
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth().saharaAppear(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.matchParentSize().saharaBreathe(cycles = 8, amount = .08f).background(tone.wash, RoundedCornerShape(50)))
                                DoseSlotIcon(slot, 52)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t(MedicineAlarmRules.promptEnglish(slot), MedicineAlarmRules.promptHindi(slot)), fontFamily = SaharaTitleFont, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.a11yHeading().announce().testTag("medicine-alarm-title"))
                                Text(careClockLabel(alarm.time, hindi), fontSize = 22.sp, fontWeight = FontWeight.Medium, color = tone.ink)
                            }
                        }
                        OutlinedButton(onClick = { tick(); onReplay() }, shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, SaharaSection.Medicines.solid),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("medicine-alarm-replay")) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(32.dp), tint = SaharaSection.Medicines.ink); Spacer(Modifier.width(12.dp))
                            Text(t("Say it again", "फिर से सुनाएँ"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = SaharaSection.Medicines.ink)
                        }
                        // The strip photos: one fills the width; several sit one under another, each large enough to match by sight.
                        alarm.medicines.forEach { (id, name) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().semantics { contentDescription = name }.testTag("medicine-alarm-photo-$id")) {
                                Surface(shape = MedicinePhotoShape, color = Paper, border = BorderStroke(3.dp, SaharaSection.Medicines.solid)) { Box(Modifier.padding(6.dp)) { StripThumb(photos[id], side) } }
                                Spacer(Modifier.height(8.dp))
                                Text(name, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                val interaction = remember { MutableInteractionSource() }
                Button(onClick = { haptic(); onTaken() }, interactionSource = interaction, shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaharaTone.Success.solid, contentColor = SaharaTone.Success.onSolid),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp).saharaPress(interaction).testTag("medicine-alarm-taken")) {
                    SaharaDrawnCheck(Modifier.size(56.dp), SaharaTone.Success.onSolid, 6.dp); Spacer(Modifier.width(16.dp))
                    Text(t("Taken", "ली"), fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { tick(); onSnooze() }, shape = RoundedCornerShape(20.dp), border = BorderStroke(2.dp, DoctorCardInk),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("medicine-alarm-snooze")) {
                    Icon(Icons.Default.Snooze, null, Modifier.size(32.dp), tint = DoctorCardInk); Spacer(Modifier.width(12.dp))
                    Text(t("Snooze $minutes minutes", "$minutes मिनट बाद फिर बजाएँ"), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
