package `in`.wedevit.care

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** This person's plan folder, given by CarePlanScreen, so recordings go when the person's records go. Null means no recording: typing and pasting still work. */
internal val LocalGocFolder = staticCompositionLocalOf<File?> { null }

private const val GOC_NONE = "__none__"

/** The card in the wizard's treatment step (V4 and V5 already passed there). Opens the talk over the wizard, so a long list can't scroll the recorder away. */
@Composable internal fun GocConversationCard(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    var open by rememberSaveable(d.id) { mutableStateOf(false) }
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> val run = afterGrant; afterGrant = null; if (granted) run?.invoke() }
    fun withMic(run: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) run()
        else { afterGrant = run; permission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    val own = d.gocTranscript?.takeIf { it.planVersion == d.version }
    val previous = GocCarry.previous(d)
    PlanCard {
        Text(t("Goals-of-care conversation", "देखभाल के लक्ष्यों की बातचीत"), fontWeight = FontWeight.Bold)
        Text(t("A checklist for the talk, then record or type it. The words stay on this phone. Nothing reaches the plan until you confirm it, one item at a time.", "बातचीत के लिए सूची, फिर रिकॉर्ड करें या लिखें। शब्द इसी फ़ोन पर रहते हैं। जब तक आप एक-एक करके पक्का न करें, योजना में कुछ नहीं जाता।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        if (own != null) Text(GocNote.kindLabel(own.noteKind).pick(hindi) + " · " + t("Saved with this draft", "इस मसौदे के साथ सहेजा गया"), style = MaterialTheme.typography.bodySmall)
        else if (previous != null) Text(t("An earlier version has a note. It opens as a draft for you to confirm or change.", "पिछले संस्करण में नोट है। यह मसौदे की तरह खुलेगा, ताकि आप पक्का करें या बदलें।"), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-open")) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(t("Open the conversation", "बातचीत खोलें")) }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize().imePadding()) { GocConversationPage(d, update, LocalGocFolder.current, ::withMic, onClose = { open = false }) }
    }
}

/** Beside the signature step: the signer sees the note the conversation left. The verbatim words stay out of it. */
@Composable internal fun GocNoteForSigning(d: EctprRecord) {
    val t = careText(); val hindi = careHindi()
    val g = d.gocTranscript?.takeIf { it.planVersion == d.version && it.note.isNotBlank() } ?: return
    PlanCard {
        Text(t("Goals-of-care note", "देखभाल के लक्ष्यों का नोट"), fontWeight = FontWeight.Bold)
        Text(GocNote.kindLabel(g.noteKind).pick(hindi), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        Text(g.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("goc-signing-note"))
        g.signOff?.let { so -> Text(t("Signed and locked", "साइन होकर बंद") + ": " + so.signers.joinToString(", ") + " · " + so.lockedAt.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
            ?: Text(t("The note is not signed yet.", "नोट पर अभी हस्ताक्षर नहीं हुए।"), style = MaterialTheme.typography.bodySmall, color = RustText)
        Text(t("The word-for-word transcript is kept separately. It is not on the emergency card.", "शब्द-दर-शब्द लिखाई अलग रखी है। यह आपातकालीन कार्ड पर नहीं जाती।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
    }
}

@Composable private fun GocConversationPage(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit, folder: File?, withMic: (() -> Unit) -> Unit, onClose: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current; val scope = rememberCoroutineScope(); val owner = LocalLifecycleOwner.current; val view = LocalView.current
    val recorder = remember(folder, d.patientId) { folder?.let { GocRecorder(context, it, d.patientId, scope) } }
    val engine = remember { GocSpeechEngine(context) }
    val draftFile = remember(folder, d.id) { folder?.let { GocDraftFile(it, d.id) } }
    val restored = remember(d.id) { draftFile?.read() }
    val initial = remember(d.id) { restored?.transcript ?: d.gocTranscript?.takeIf { it.planVersion == d.version } }

    var participants by rememberSaveable(d.id) { mutableStateOf(initial?.participants.orEmpty()) }
    var consentAt by rememberSaveable(d.id) { mutableStateOf(initial?.consentAt ?: 0L) }
    val agreements = remember { mutableStateMapOf<String, Boolean>() }
    var purpose by rememberSaveable { mutableStateOf(false) }; var storage by rememberSaveable { mutableStateOf(false) }; var access by rememberSaveable { mutableStateOf(false) }
    var lines by remember { mutableStateOf(initial?.lines.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note ?: GocCarry.startingNote(d)) }
    var topics by remember { mutableStateOf(initial?.topics.orEmpty().associateBy { it.decision }) }
    var ticked by remember { mutableStateOf(restored?.ticked.orEmpty()) }
    var language by rememberSaveable(d.id) { mutableStateOf(GocSpeechLanguage.entries.firstOrNull { it.code == initial?.language && it.code.isNotEmpty() }?.name ?: GocSpeechLanguage.AUTO.name) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var typed by rememberSaveable { mutableStateOf("") }
    var analysed by remember { mutableStateOf(if (initial?.lines.isNullOrEmpty()) "" else GocLines.text(initial!!.lines)) }
    val pending = remember { mutableStateMapOf<GocDecision, String>() }
    var editing by remember { mutableStateOf(-1) }
    var keepAudio by rememberSaveable { mutableStateOf(false) }; var keepTranscript by rememberSaveable { mutableStateOf(true) }
    var deleting by remember { mutableStateOf<DoctorAudioClip?>(null) }
    var ready by remember { mutableStateOf(false) }; var checking by remember { mutableStateOf(true) }
    var operation by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var task by remember { mutableStateOf<Job?>(null) }
    val progressFlow = remember { MutableStateFlow(0L to 0L) }; val progress by progressFlow.collectAsState()
    var firstTick by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val recorderState = recorder?.state?.collectAsState()?.value ?: GocRecorderState()
    // Guide items with no plan field of their own (consent, patient details, family, understanding, home care), ticked by hand.
    var guideTicked by remember { mutableStateOf(setOf<GocGuideItem>()) }
    var askFinish by remember { mutableStateOf(false) }
    var signOff by remember { mutableStateOf(initial?.signOff) }
    var editSigned by remember { mutableStateOf(false) }
    var transcribedBy by remember { mutableStateOf(initial?.transcribedBy.orEmpty()) }
    // Google Gemini only when the server has a key and everyone agreed to the audio being sent; otherwise Whisper on this phone, then typing.
    var gemini by remember { mutableStateOf(false) }; var geminiOk by rememberSaveable { mutableStateOf(false) }
    var diarizeReady by remember { mutableStateOf<Boolean?>(null) }
    var speakersToName by remember { mutableStateOf<List<String>>(emptyList()) }
    val signStore = remember(d.patientId) { GocSignOffStore(context, "goc-" + d.patientId.filter { it.isLetterOrDigit() }.ifBlank { "guest" }) }

    val names = RecordingConsentRules.names(participants)
    val text = GocLines.text(lines)
    val extraction = remember(analysed) { GocExtract.extract(analysed) }
    val stale = text != analysed
    val busy = operation.isNotBlank()
    val covered = remember(text) { GocGuide.covered(text) }
    val guideTicks = guideTicked + GocGuide.ORDER.filter { it.decision != null && it.decision in ticked }
    val undiscussed = GocGuide.undiscussed(extraction, covered, guideTicks, topics, consentAt > 0)
    val nextItem = undiscussed.firstOrNull()
    val linesNow = if (stale) null else lines
    val noteLocked = signOff != null && !editSigned

    fun build(now: String = "", kept: Boolean = false) = GocTranscript(
        lines = lines.filter { it.text.isNotBlank() }, language = GocSpeechLanguage.valueOf(language).code,
        source = if (model.isNotBlank()) GocTranscript.SOURCE_RECORDED else GocTranscript.SOURCE_TYPED,
        model = model, participants = names.joinToString("\n"), consentAt = consentAt, planVersion = d.version,
        topics = topics.values.sortedBy { it.decision.ordinal }, note = note, noteKind = GocNote.kind(topics.values), reviewedAt = now, reviewedBy = if (now.isBlank()) "" else d.createdBy, audioKept = kept,
        signOff = signOff, transcribedBy = transcribedBy,
    )
    LaunchedEffect(Unit) { try { ready = engine.models.ready() } catch (c: CancellationException) { throw c } catch (_: Exception) { ready = false } finally { checking = false } }
    LaunchedEffect(Unit) { gemini = gocOnlineEngines().gemini }
    // The speaker pack check hashes about 45 MB, so it runs once per screen.
    LaunchedEffect(Unit) { diarizeReady = try { gocDiarizeModelsReady(context) } catch (c: CancellationException) { throw c } catch (_: Exception) { false } }
    // Autosave, so a killed app or a long job loses nothing the doctor already corrected.
    LaunchedEffect(lines, note, topics, ticked, consentAt, participants) {
        if (consentAt > 0 && draftFile != null) { delay(1200); val snapshot = GocDraft(build(), ticked); withContext(Dispatchers.IO) { draftFile.write(snapshot) } }
    }
    DisposableEffect(recorder, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { recorder?.stop(); recorder?.stopPlayback(); task?.cancel() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder?.close(); task?.cancel() }
    }
    val awake = recorderState.recording || busy
    DisposableEffect(awake) { view.keepScreenOn = awake; onDispose { view.keepScreenOn = false } }

    fun run(label: String, action: suspend () -> Unit) {
        if (busy) return
        operation = label; error = ""; progressFlow.value = 0L to 0L; firstTick = null
        task = scope.launch {
            try { action() }
            catch (c: CancellationException) { error = t("Stopped. What was read is kept, and it continues from there.", "रोक दिया। जितना पढ़ा गया वह रखा है, और यहीं से आगे चलेगा।"); throw c }
            catch (e: Exception) { error = e.message ?: t("That did not work. Your recording is safe. Try again, or type the words.", "यह नहीं हुआ। आपकी रिकॉर्डिंग सुरक्षित है। फिर कोशिश करें, या शब्द लिखें।") }
            finally { operation = ""; task = null }
        }
    }
    val readingLabel = t("Reading the recording", "रिकॉर्डिंग पढ़ी जा रही है")
    suspend fun readOnline(clip: DoctorAudioClip): Boolean = try {
        val result = transcribeGocWithGemini(clip.file, GocSpeechLanguage.valueOf(language).code.ifBlank { null }) { share -> progressFlow.value = (share * 1000).toLong() to 1000L }
        val added = result.lines.flatMap { line ->
            // Gemini's speaker roles are the default, marked as a guess the doctor can change on each line.
            val party = when (line.speaker) { "DOCTOR" -> GocParty.DOCTOR_SIDE; "PATIENT_PARTY" -> GocParty.PATIENT_PARTY; else -> GocParty.UNSET }
            GocLines.fromText(line.text, gocSegmentKey(clip.id, (line.startSec * 1000).toLong())).map { it.copy(party = party, guessed = party != GocParty.UNSET) }
        }
        lines = lines + added.filter { new -> lines.none { it.key == new.key } }
        model = result.model; transcribedBy = "Google Gemini (${result.model})"
        true
    } catch (c: CancellationException) { throw c } catch (_: Exception) {
        error = t("Google Gemini did not finish, so this phone is reading the recording instead.", "Google Gemini से लिखाई पूरी नहीं हुई, इसलिए यह फ़ोन रिकॉर्डिंग पढ़ रहा है।"); false
    }
    fun transcribe(clip: DoctorAudioClip) = run(readingLabel) {
        recorder?.stopPlayback()
        if (!(gemini && geminiOk && readOnline(clip))) {
            val segments = engine.transcribe(clip, d.patientId, GocSpeechLanguage.valueOf(language), { done, total -> progressFlow.value = done to total; if (firstTick == null && done > 0) firstTick = SystemClock.elapsedRealtime() to done }) { segment ->
                scope.launch {
                    val key = gocSegmentKey(clip.id, segment.startMs)
                    if (segment.text.isNotBlank() && lines.none { it.key.startsWith("$key:") }) lines = lines + GocLines.fromText(segment.text, key)
                }
            }
            model = OfflineWhisperModel.id; transcribedBy = "Whisper on this phone (${OfflineWhisperModel.id})"
            // Who spoke when, on the phone, after Whisper (never at the same time). The doctor then says once which speaker is the doctor.
            if (diarizeReady == true && segments.isNotEmpty()) {
                progressFlow.value = 0L to 0L; operation = t("Finding who spoke when", "कौन कब बोला, यह ढूँढ रहे हैं")
                val turns = diarizeGocAudio(context, clip.file) { share -> progressFlow.value = (share * 1000).toLong() to 1000L }
                val speakers = gocSpeakersForSegments(segments, turns)
                val byKey = segments.zip(speakers).mapNotNull { (segment, speaker) -> speaker?.let { gocSegmentKey(clip.id, segment.startMs) + ":" to "Speaker ${it + 1}" } }
                lines = lines.map { line -> byKey.firstOrNull { line.key.startsWith(it.first) }?.let { (_, who) -> if (line.party == GocParty.UNSET || line.guessed) line.copy(who = who, guessed = true) else line.copy(who = who) } ?: line }
                speakersToName = byKey.map { it.second }.distinct().sorted()
            }
        }
    }
    fun nameDoctor(who: String?) {
        if (who != null) lines = lines.map { line -> if (line.who.startsWith("Speaker ")) line.copy(party = if (line.who == who) GocParty.DOCTOR_SIDE else GocParty.PATIENT_PARTY, guessed = false) else line }
        speakersToName = emptyList()
    }
    fun confirm(decision: GocDecision, choice: String) {
        val id = choice.takeUnless { it == GOC_NONE }
        val quotes = id?.let { GocLatest.standing(extraction, decision, linesNow).suggested[it] }.orEmpty().map { it.quote }
        update { GocPlan.apply(it, decision, id, quotes) }
        topics = topics + (decision to GocTopicRecord(decision, GocTopicStatus.CONFIRMED, id, quotes.firstOrNull().orEmpty(), planNow()))
        pending.remove(decision)
    }
    fun finish() {
        val kept = keepAudio && recorderState.clips.isNotEmpty()
        val saved = build(planNow(), kept).let { if (keepTranscript) it else it.copy(lines = emptyList()) }
        update { it.copy(gocTranscript = saved) }
        if (!kept) recorderState.clips.forEach { recorder?.delete(it) }
        draftFile?.delete()
        onClose()
    }
    fun tryFinish() { if (undiscussed.isNotEmpty()) askFinish = true else finish() }

    LazyColumn(Modifier.fillMaxSize().testTag("goc-page"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") { PlanHeader(t("Goals-of-care conversation", "देखभाल के लक्ष्यों की बातचीत"), onClose, "goc-header") }
        item(key = "notice") {
            Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(t("Draft. Sahara matches words only: it does not recommend any treatment. A topic not discussed stays as No preference recorded.", "मसौदा। सहारा सिर्फ़ शब्द मिलाता है: यह कोई इलाज नहीं सुझाता। जिस विषय पर बात नहीं हुई वह 'कोई पसंद दर्ज नहीं' ही रहता है।"), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (consentAt <= 0) item(key = "consent") {
            PlanCard {
                Text(t("Before you begin", "शुरू करने से पहले"), fontWeight = FontWeight.Bold)
                Text(t("List everyone present. Each person must agree.", "सभी मौजूद लोगों के नाम लिखें। हर व्यक्ति की सहमति ज़रूरी है।"))
                CareField(participants, { participants = it.take(600) }, t("Names of everyone present", "सभी मौजूद लोगों के नाम"), supporting = t("Separate names with commas or new lines", "नाम कॉमा या नई पंक्ति से अलग करें"))
                names.forEach { name -> CheckLine(agreements[name] == true, { agreements[name] = it }, name + " · " + t("agrees", "सहमत")) }
                CheckLine(purpose, { purpose = it }, t("We understand this is to write down what was discussed, for the doctor to check.", "हम समझते हैं कि यह चर्चा लिखने के लिए है, जिसे डॉक्टर जाँचेंगे।"))
                CheckLine(storage, { storage = it }, if (gemini) t("Audio and words stay on this phone, unless everyone also agrees below to Google Gemini writing the words. The audio is deleted after review unless the doctor keeps it.", "ऑडियो और शब्द इसी फ़ोन पर रहते हैं, जब तक सभी नीचे Google Gemini से लिखवाने के लिए भी राज़ी न हों। जाँच के बाद ऑडियो मिट जाता है, जब तक डॉक्टर उसे न रखें।")
                    else t("Audio and words stay on this phone. Nothing is uploaded. The audio is deleted after review unless the doctor keeps it.", "ऑडियो और शब्द इसी फ़ोन पर रहते हैं। कुछ अपलोड नहीं होता। जाँच के बाद ऑडियो मिट जाता है, जब तक डॉक्टर उसे न रखें।"))
                CheckLine(access, { access = it }, t("People with access to this phone may read its records.", "इस फ़ोन तक पहुँच वाले लोग इसके रिकॉर्ड पढ़ सकते हैं।"))
                Button(onClick = { consentAt = System.currentTimeMillis() }, enabled = names.isNotEmpty() && names.all { agreements[it] == true } && purpose && storage && access, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-consent")) { Text(t("Everyone agrees", "सभी सहमत हैं")) }
            }
        }
        if (consentAt > 0) {
            item(key = "checklist") {
                PlanCard {
                    val done = GocGuide.ORDER.size - undiscussed.size
                    Text(t("Guide for the talk", "बातचीत की गाइड") + " · " + t("$done of ${GocGuide.ORDER.size} covered", "${GocGuide.ORDER.size} में से $done पर बात हुई"), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("goc-guide-title"))
                    nextItem?.let { n ->
                        Surface(color = Mint, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("goc-next-topic")) {
                            Column(Modifier.padding(12.dp)) { Text(t("Next", "अगला") + ": " + GocGuide.title(n).pick(hindi), fontWeight = FontWeight.SemiBold); Text((n.decision?.let { GocPlan.prompt(it) } ?: gocGuidePrompt(n)).pick(hindi), style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    GocGuide.ORDER.forEach { item ->
                        val status = GocGuide.status(item, extraction, covered, guideTicks, topics, consentAt > 0)
                        val decision = item.decision
                        CheckLine(status != GocChecklistStatus.NOT_DISCUSSED, { on ->
                            if (decision != null) ticked = if (on) ticked + decision else ticked - decision else guideTicked = if (on) guideTicked + item else guideTicked - item
                        }, (GocGuide.section(item)?.let { "$it · " } ?: "") + GocGuide.title(item).pick(hindi) + " · " + checklistLabel(status).pick(hindi))
                    }
                    InfoDisclosure(t("How the guide works", "गाइड कैसे काम करती है")) {
                        Text(t("In the order of the form: consent, patient details, family and emergency contact, then CPR, dialysis and the other decisions. A topic ticks itself when the words come up, and you can tick it too. A tick records no choice.", "फ़ॉर्म के क्रम में: सहमति, मरीज़ की जानकारी, परिवार और इमरजेंसी संपर्क, फिर CPR, डायलिसिस और बाक़ी फ़ैसले। शब्द आते ही विषय पर निशान लग जाता है, और आप भी लगा सकते हैं। निशान से कोई फ़ैसला दर्ज नहीं होता।"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "capture") {
                PlanCard {
                    Text(t("Record or type", "रिकॉर्ड करें या लिखें"), fontWeight = FontWeight.Bold)
                    if (recorder != null) {
                        Button(onClick = { if (recorderState.recording) recorder.stop() else withMic { recorder.start(participants, names.toSet(), purpose = true, storage = true, access = true) } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-record")) {
                            Icon(if (recorderState.recording) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp))
                            Text(if (recorderState.recording) t("Stop and save", "रोकें और सहेजें") + " · " + clock(recorderState.seconds) else t("Start recording", "रिकॉर्डिंग शुरू करें"))
                        }
                        if (recorderState.recording) LinearProgressIndicator(progress = { recorderState.level.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Rust)
                        Text(t("Up to 90 minutes in one recording. Keep this screen open: leaving the app stops and saves the part, then record the next part.", "एक रिकॉर्डिंग में 90 मिनट तक। यह स्क्रीन खुली रखें: ऐप से बाहर जाने पर हिस्सा रुककर सेव हो जाता है, फिर अगला हिस्सा रिकॉर्ड करें।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        recorderState.error?.let { Text(it.pick(hindi), color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
                        recorderState.clips.forEachIndexed { index, clip ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { recorder.play(clip) }, enabled = !recorderState.recording && !busy, modifier = Modifier.testTag("goc-play-$index")) { Icon(if (recorderState.playing == clip.id) Icons.Default.Stop else Icons.Default.PlayArrow, t("Play or stop the recording", "रिकॉर्डिंग चलाएँ या रोकें")) }
                                Text(t("Part", "हिस्सा") + " ${index + 1} · ${clip.file.length() / (1024 * 1024)} MB", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { transcribe(clip) }, enabled = ready && !busy && !recorderState.recording, modifier = Modifier.testTag("goc-transcribe-$index")) { Icon(Icons.Default.GraphicEq, null); Spacer(Modifier.width(4.dp)); Text(if (engine.hasProgress(clip)) t("Continue", "आगे पढ़ें") else t("Read", "पढ़ें")) }
                                IconButton(onClick = { deleting = clip }, enabled = !busy && !recorderState.recording) { Icon(Icons.Default.DeleteOutline, t("Delete recording", "रिकॉर्डिंग मिटाएँ")) }
                            }
                        }
                        if (gemini) CheckLine(geminiOk, { geminiOk = it }, t("Audio is sent to Google Gemini to transcribe.", "ऑडियो लिखने के लिए Google Gemini को भेजा जाता है।") + " " + t("Everyone here agrees. Without this tick the audio stays on this phone.", "यहाँ सभी सहमत हैं। यह निशान न हो तो ऑडियो इसी फ़ोन पर रहता है।"))
                        if (diarizeReady == false) OutlinedButton(onClick = { run(t("Downloading the speaker pack", "वक्ता पैक डाउनलोड हो रहा है")) { downloadGocDiarizeModels(context) { done, total -> progressFlow.value = done to total }; diarizeReady = true } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-diarize-download")) {
                            Text(t("Tell speakers apart · speaker pack", "बोलने वालों को अलग करें · वक्ता पैक") + " · ${GOC_DIARIZE_DOWNLOAD_BYTES / 1_000_000} MB")
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GocSpeechLanguage.entries.forEach { choice -> FilterChip(selected = language == choice.name, onClick = { language = choice.name }, enabled = !busy, label = { Text(if (choice == GocSpeechLanguage.AUTO) t("Detect language", "भाषा पहचानें") else choice.label) }, modifier = Modifier.testTag("goc-language-${choice.name}")) }
                        }
                        Text(t("Pick the main language if the words come out wrong. Telugu, Urdu and Kannada are less accurate than English: check every line.", "शब्द ग़लत आएँ तो मुख्य भाषा चुनें। तेलुगु, उर्दू और कन्नड़ में अंग्रेज़ी से कम सटीकता है: हर पंक्ति जाँचें।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        if (!ready) {
                            Text(t("Speech pack · 375 MB · for English, Hindi, Telugu, Urdu and Kannada. Download once, on Wi-Fi if you can. Then it works without internet.", "स्पीच पैक · 375 MB · अंग्रेज़ी, हिन्दी, तेलुगु, उर्दू और कन्नड़ के लिए। एक बार डाउनलोड करें, हो सके तो Wi-Fi पर। फिर बिना इंटरनेट चलेगा।"), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { run(t("Downloading the speech pack", "स्पीच पैक डाउनलोड हो रहा है")) { engine.models.download { done, total -> progressFlow.value = done to total }; ready = true } }, enabled = !checking && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-model-download")) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (checking) t("Checking the speech pack…", "स्पीच पैक जाँच रहे हैं…") else t("Download the speech pack", "स्पीच पैक डाउनलोड करें")) }
                        }
                    }
                    if (busy) {
                        val percent = if (progress.second > 0) (progress.first * 100 / progress.second).coerceIn(0, 100) else 0
                        Text(if (progress.second > 0) "$operation · $percent%" else if (operation == readingLabel) t("Preparing the audio…", "ऑडियो तैयार हो रहा है…") else operation)
                        if (progress.second > 0) LinearProgressIndicator(progress = { (progress.first.toFloat() / progress.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                        val tick = firstTick
                        if (operation == readingLabel && tick != null && progress.first > tick.second) {
                            val n = (SystemClock.elapsedRealtime() - tick.first) * (progress.second - progress.first) / (progress.first - tick.second) / 60_000 + 1
                            Text(t("About $n minutes left. Keep the screen open and the phone charging.", "लगभग $n मिनट बाकी। स्क्रीन खुली और फ़ोन चार्ज पर रखें।"), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { task?.cancel() }, modifier = Modifier.testTag("goc-cancel")) { Text(t("Stop", "रोकें")) }
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("goc-error").announce())
                    if (speakersToName.isNotEmpty()) Surface(color = Mint, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("goc-name-doctor")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t("Which speaker is the doctor?", "डॉक्टर कौन सा वक्ता है?"), fontWeight = FontWeight.SemiBold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                speakersToName.forEach { who -> OutlinedButton(onClick = { nameDoctor(who) }) { Text(who) } }
                                TextButton(onClick = { nameDoctor(null) }) { Text(t("I will mark the lines myself", "मैं ख़ुद पंक्तियाँ चिह्नित करूँगा")) }
                            }
                        }
                    }
                    CareField(typed, { typed = it.take(OfflineSpeechModel.maxOriginalChars) }, t("Type or paste the conversation", "बातचीत लिखें या चिपकाएँ"), minLines = 3)
                    OutlinedButton(onClick = { lines = lines + GocLines.fromText(typed); typed = "" }, enabled = typed.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-add-typed")) { Text(t("Add to the transcript", "लिखाई में जोड़ें")) }
                }
            }
            if (lines.isNotEmpty()) {
                item(key = "transcript-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("Transcript · draft", "लिखाई · मसौदा"), fontWeight = FontWeight.Bold)
                        if (transcribedBy.isNotBlank()) Text(t("Transcribed by", "लिखने वाला") + ": " + transcribedBy, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("goc-transcribed-by"))
                        Text(t("Word for word, kept apart from the note. Check it against the audio. Tap a label to set doctor side or patient party.", "शब्द-दर-शब्द, नोट से अलग रखी। ऑडियो से मिलाएँ। लेबल दबाकर डॉक्टर की ओर या मरीज़ की ओर चुनें।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                    }
                }
                itemsIndexed(lines, key = { index, _ -> "line-$index" }) { index, line ->
                    Column(Modifier.fillMaxWidth().testTag("goc-line-$index")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = line.party != GocParty.UNSET, onClick = { lines = lines.toMutableList().also { it[index] = line.copy(party = GocLines.nextParty(line.party), guessed = false) } }, label = { Text((if (line.who.isNotBlank()) line.who + " · " else "") + GocLines.label(line.party).pick(hindi) + if (line.guessed) " · " + t("guess", "अनुमान") else "") }, modifier = Modifier.testTag("goc-party-$index"))
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { editing = if (editing == index) -1 else index }) { Icon(Icons.Default.Edit, t("Edit this line", "यह पंक्ति बदलें")) }
                        }
                        if (editing == index) CareField(line.text, { v -> lines = lines.toMutableList().also { it[index] = line.copy(text = v) } }, t("Correct this line", "यह पंक्ति सुधारें"))
                        else Text(line.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item(key = "find") {
                    Button(onClick = { analysed = text }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-find")) { Text(if (analysed.isBlank()) t("Find what was discussed", "देखें किस पर बात हुई") else if (stale) t("The transcript changed. Find again", "लिखाई बदली। फिर देखें") else t("Find again", "फिर देखें")) }
                }
            }
            if (lines.isNotEmpty() || note.isNotBlank()) {
                item(key = "review-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("Confirm each item", "हर बिंदु पक्का करें"), fontWeight = FontWeight.Bold)
                        Text(t("Every choice is shown. A mark only means those words were heard. Nothing is selected until you choose it and confirm.", "हर विकल्प दिखाया गया है। निशान का मतलब बस इतना है कि वे शब्द सुने गए। जब तक आप चुनकर पक्का न करें, कुछ नहीं चुना जाता।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                    }
                }
                items(GocPlan.ORDER, key = { "decision-${it.name}" }) { decision ->
                    GocDecisionCard(d, decision, extraction, GocLatest.standing(extraction, decision, linesNow), linesNow, topics, pending[decision], { pending[decision] = it }, { confirm(decision, it) }, { on ->
                        topics = if (on) topics + (decision to GocTopicRecord(decision, GocTopicStatus.NOT_DISCUSSED_VERIFIED, at = planNow())) else topics - decision
                    })
                }
                item(key = "note") {
                    PlanCard {
                        Text(t("Note for the doctor to edit", "डॉक्टर के बदलने के लिए नोट"), fontWeight = FontWeight.Bold)
                        Text(GocNote.kindLabel(GocNote.kind(topics.values)).pick(hindi), style = MaterialTheme.typography.bodySmall)
                        if (note.isNotBlank() && note == GocCarry.startingNote(d)) Text(t("This is the earlier version's note, as a draft. Confirm or change it.", "यह पिछले संस्करण का नोट है, मसौदे की तरह। इसे पक्का करें या बदलें।"), color = RustText, style = MaterialTheme.typography.bodySmall)
                        Text(t("It says what was said and decided. Spoken words and feelings stay in the transcript.", "इसमें वही है जो कहा और तय हुआ। बोले गए शब्द और भावनाएँ लिखाई में रहते हैं।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        OutlinedButton(onClick = { note = GocNote.draft(d, topics, names.joinToString("\n"), hindi, undiscussed.map(GocGuide::title)) }, enabled = !noteLocked, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-build-note")) { Text(t("Build the note from the items above", "ऊपर के बिंदुओं से नोट बनाएँ")) }
                        if (noteLocked) {
                            Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("goc-note-locked"))
                            TextButton(onClick = { editSigned = true }) { Text(t("Change the note (everyone signs again)", "नोट बदलें (सबको फिर साइन करना होगा)")) }
                        } else CareField(note, { note = it.take(8000) }, t("Note", "नोट"), minLines = 5)
                        InfoDisclosure(t("The note in the form's layout, Sections I to IV", "फ़ॉर्म के क्रम में नोट, भाग I से IV")) {
                            GocForm.sections(d, topics).forEach { section ->
                                Text(section.title.pick(hindi), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                                section.rows.forEach { Text(it.pick(hindi), style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if (note.isNotBlank()) {
                            Text(t("Sign the note", "नोट पर हस्ताक्षर"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            signOff?.let { so -> Text(t("Signed and locked", "साइन होकर बंद") + ": " + so.signers.joinToString(", ") + " · " + so.lockedAt.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("goc-signed")) }
                            GocSignOff(noteId = "${d.id}:v${d.version}:goc", noteText = note, store = signStore,
                                onComplete = { record -> signOff = GocSignOffSummary(record.id, record.sealSha256.orEmpty(), record.lockedAt.orEmpty(), record.signatures.map { it.name }); editSigned = false },
                                onInvalidated = { signOff = null })
                        }
                    }
                }
                item(key = "finish") {
                    PlanCard {
                        val open = GocPlan.ORDER.count { it !in topics }
                        if (open > 0) { val n = open; Text(t("$n topics are not confirmed or verified yet. The note will say so.", "$n विषय अभी पक्के या जाँचे नहीं गए। नोट में यह लिखा जाएगा।"), style = MaterialTheme.typography.bodySmall, color = RustText) }
                        if (signOff == null && note.isNotBlank()) Text(t("The note is not signed yet.", "नोट पर अभी हस्ताक्षर नहीं हुए।"), style = MaterialTheme.typography.bodySmall, color = RustText)
                        if (recorderState.clips.isNotEmpty()) CheckLine(keepAudio, { keepAudio = it }, t("Keep the recording on this phone (otherwise it is deleted now)", "रिकॉर्डिंग इस फ़ोन पर रखें (नहीं तो अभी मिट जाएगी)"))
                        CheckLine(keepTranscript, { keepTranscript = it }, t("Save the word-for-word transcript with this draft", "शब्द-दर-शब्द लिखाई इस मसौदे के साथ सहेजें"))
                        Text(t("The transcript is never put on the emergency card or its QR code. Next: review date, then signatures.", "लिखाई कभी आपातकालीन कार्ड या उसके QR कोड पर नहीं जाती। आगे: समीक्षा की तारीख़, फिर हस्ताक्षर।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        Button(onClick = ::tryFinish, enabled = !busy && !recorderState.recording && (text.isNotBlank() || note.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-finish")) { Text(t("Finish review", "जाँच पूरी करें")) }
                    }
                }
            }
        }
    }
    if (askFinish) AlertDialog(onDismissRequest = { askFinish = false }, containerColor = Paper, modifier = Modifier.testTag("goc-not-discussed"),
        title = { Text(t("You haven’t spoken about these things. Are you sure you want to continue?", "इन बातों पर बात नहीं हुई। क्या आप सच में आगे बढ़ना चाहते हैं?")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            undiscussed.forEach { Text("• " + GocGuide.title(it).pick(hindi)) }
            Text(t("They stay No preference recorded, and the note lists them as not discussed.", "ये 'कोई पसंद दर्ज नहीं' रहेंगे, और नोट में 'बात नहीं हुई' लिखा जाएगा।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        } },
        confirmButton = { TextButton(onClick = { askFinish = false; finish() }, modifier = Modifier.testTag("goc-continue-anyway")) { Text(t("Continue anyway", "फिर भी आगे बढ़ें")) } },
        dismissButton = { TextButton(onClick = { askFinish = false }, modifier = Modifier.testTag("goc-go-back")) { Text(t("Go back", "वापस जाएँ")) } })
    deleting?.let { clip -> AlertDialog(onDismissRequest = { deleting = null }, containerColor = Paper, title = { Text(t("Delete this recording?", "यह रिकॉर्डिंग मिटाएँ?")) }, text = { Text(t("The words already in the transcript are kept.", "लिखाई में आ चुके शब्द रहेंगे।")) },
        confirmButton = { TextButton(onClick = { recorder?.delete(clip); deleting = null }) { Text(t("Delete", "मिटाएँ")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("Keep recording", "रिकॉर्डिंग रखें")) } }) }
}

private fun clock(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** A neutral prompt for guide items that are not a decision: what to bring up, never what to choose. */
private fun gocGuidePrompt(item: GocGuideItem): Bi = when (item) {
    GocGuideItem.CONSENT -> Bi("Ask everyone present if they agree to this conversation being recorded and written down.", "पूछें कि क्या सभी मौजूद लोग बातचीत रिकॉर्ड करने और लिखने के लिए राज़ी हैं।")
    GocGuideItem.PATIENT_DETAILS -> Bi("Confirm the patient's name, age and address.", "मरीज़ का नाम, उम्र और पता पक्का करें।")
    GocGuideItem.FAMILY_CONTACT -> Bi("Ask who is present, who decides with the patient, and who to call in an emergency.", "पूछें कि कौन मौजूद है, मरीज़ के साथ कौन फ़ैसला लेगा, और इमरजेंसी में किसे फ़ोन करें।")
    GocGuideItem.UNDERSTANDING -> Bi("Ask what the patient and family understand about the illness.", "पूछें कि मरीज़ और परिवार बीमारी के बारे में क्या समझते हैं।")
    else -> Bi("If care is at home, ask who visits and how often.", "अगर देखभाल घर पर है, तो पूछें कि कौन और कितनी बार आता है।")
}

private fun checklistLabel(s: GocChecklistStatus): Bi = when (s) {
    GocChecklistStatus.NOT_DISCUSSED -> Bi("Not discussed", "बात नहीं हुई")
    GocChecklistStatus.MENTIONED -> Bi("Discussed, no clear choice", "बात हुई, साफ़ फ़ैसला नहीं")
    GocChecklistStatus.SUGGESTED -> Bi("Discussed, needs your check", "बात हुई, आपकी जाँच बाकी")
    GocChecklistStatus.CONFIRMED -> Bi("Confirmed", "पक्का")
    GocChecklistStatus.VERIFIED_NOT_DISCUSSED -> Bi("Not discussed, verified", "बात नहीं हुई, जाँचा गया")
}

@Composable private fun GocDecisionCard(d: EctprRecord, decision: GocDecision, extraction: GocExtraction, standing: GocStanding, lines: List<GocLine>?, topics: Map<GocDecision, GocTopicRecord>, picked: String?, onPick: (String) -> Unit, onConfirm: (String) -> Unit, onVerify: (Boolean) -> Unit) {
    val t = careText(); val hindi = careHindi()
    // Latest statement wins, per person: only what still stands is marked. Earlier words that a later statement replaced are listed below.
    val suggested = standing.suggested
    val ruledOut = standing.ruledOut
    fun said(h: GocHit): String {
        val line = lines?.let { GocLines.lineAt(it, h.at) }
        val who = line?.who?.takeIf(String::isNotBlank) ?: line?.party?.takeIf { it != GocParty.UNSET }?.let { GocLines.label(it).pick(hindi) }
        return (who?.let { "$it: " } ?: "") + "\u201C" + h.quote + "\u201D"
    }
    val topic = topics[decision]
    val options = GocPlan.options(decision)
    val current = GocPlan.current(d, decision)
    val carried = GocCarry.carried(d, decision, topics)
    PlanCard {
        Text(GocPlan.title(decision).pick(hindi), fontWeight = FontWeight.SemiBold, modifier = Modifier.a11yHeading().testTag("goc-decision-${decision.name}"))
        if (decision != GocDecision.VALUES) {
            val now = options.firstOrNull { it.id == current }?.label ?: NO_PREFERENCE
            Text(if (carried) t("From the previous version", "पिछले संस्करण से") + ": " + now.pick(hindi) + ". " + t("Confirm it or change it.", "इसे पक्का करें या बदलें।") else t("Now in the plan", "अभी योजना में") + ": " + now.pick(hindi), style = MaterialTheme.typography.bodySmall, color = if (carried) RustText else DoctorCardSub)
        }
        if (standing.conflict) Text(t("Different people said different things. Both are shown. Check with the patient which one stands.", "अलग-अलग लोगों ने अलग बातें कहीं। दोनों दिखाई गई हैं। मरीज़ से जाँचें कि कौन सी बात सही है।"), style = MaterialTheme.typography.bodySmall, color = RustText, modifier = Modifier.testTag("goc-conflict-${decision.name}"))
        else if (standing.changedMind) Text(t("Changed during the talk. The latest statement is marked.", "बातचीत में बदला। आख़िरी बात पर निशान है।"), style = MaterialTheme.typography.bodySmall, color = RustText, modifier = Modifier.testTag("goc-changed-${decision.name}"))
        if (extraction.hitsFor(decision).isEmpty()) extraction.mentionsFor(decision).firstOrNull()?.let { m ->
            Text(t("Came up, with no clear choice", "बात आई, पर साफ़ फ़ैसला नहीं"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(m.quote, style = MaterialTheme.typography.bodySmall)
        }
        PlanOption(if (decision == GocDecision.VALUES) t("Leave the What matters section as it is", "'क्या ज़रूरी है' हिस्सा जैसा है वैसा रखें") else NO_PREFERENCE.pick(hindi), null, picked == GOC_NONE, "goc-${decision.name}-none") { onPick(GOC_NONE) }
        options.forEach { option ->
            val heard = suggested[option.id].orEmpty()
            // The Values choices only add words that were heard, so they need a quote to add.
            val usable = decision != GocDecision.VALUES || heard.isNotEmpty()
            PlanOption(option.label.pick(hindi), option.meaning?.pick(hindi), picked == option.id, "goc-${decision.name}-${option.id}") { if (usable) onPick(option.id) }
            if (heard.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(start = 12.dp).testTag("goc-suggested-${decision.name}-${option.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Marked: these words were heard", "निशान: ये शब्द सुने गए"), color = RustText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                heard.forEach { h -> Text(said(h), style = MaterialTheme.typography.bodySmall) }
            }
            val refused = ruledOut[option.id].orEmpty()
            if (refused.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(start = 12.dp).testTag("goc-ruledout-${decision.name}-${option.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Marked: they said no to this", "निशान: इसके लिए मना किया"), color = RustText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                refused.forEach { h -> Text(said(h), style = MaterialTheme.typography.bodySmall) }
            }
            if (carried && option.id == current) Text(t("Previous version's choice", "पिछले संस्करण का फ़ैसला"), color = RustText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
        }
        if (standing.superseded.isNotEmpty()) Column(Modifier.fillMaxWidth().testTag("goc-superseded-${decision.name}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t("Said earlier, replaced by a later statement", "पहले कहा, बाद की बात से बदला"), color = DoctorCardSub, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            standing.superseded.forEach { (earlier, _) -> Text(said(earlier), style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough), color = DoctorCardSub) }
        }
        picked?.let { choice -> Button(onClick = { onConfirm(choice) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-confirm-${decision.name}")) { Text(t("Confirm this item", "यह बिंदु पक्का करें")) } }
        if (topic?.status == GocTopicStatus.CONFIRMED) Text(t("Confirmed by you", "आपने पक्का किया"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("goc-confirmed-${decision.name}"))
        if (standing.current.isEmpty() && standing.superseded.isEmpty() && topic?.status != GocTopicStatus.CONFIRMED) {
            val stays = if (decision == GocDecision.VALUES) null else options.firstOrNull { it.id == current }?.label ?: NO_PREFERENCE
            CheckLine(topic?.status == GocTopicStatus.NOT_DISCUSSED_VERIFIED, onVerify, t("Not discussed in this talk. I checked this.", "इस बातचीत में बात नहीं हुई। मैंने जाँच लिया।") + (stays?.let { " " + t("It stays", "यह रहेगा") + ": " + it.pick(hindi) } ?: ""))
        }
    }
}
