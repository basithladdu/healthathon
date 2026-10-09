package `in`.wedevit.care

import android.app.Application
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.time.LocalDate

data class Centre(val name: String, val city: String, val district: String, val address: String, val phone: String, val services: String, val morphine: String, val url: String)

@OptIn(ExperimentalCoroutinesApi::class)
class CareModel(app: Application) : AndroidViewModel(app) {
    private val db = CareDatabase.get(app)
    private val dao = db.care()
    private val preferences = app.getSharedPreferences("preferences", 0)
    val people = dao.people().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selected = MutableStateFlow(preferences.getString("person", "") ?: "")
    val clinician = MutableStateFlow(preferences.getBoolean("clinician", false))
    val draft = MutableStateFlow(Conversation(""))
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val recording = MutableStateFlow(false)
    val amplitude = MutableStateFlow(0f)
    val recordingSeconds = MutableStateFlow(0)
    val recordings = MutableStateFlow<List<File>>(emptyList())
    val versions = selected.flatMapLatest { dao.versions(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val items = selected.flatMapLatest { dao.items(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val acknowledgements = dao.acknowledgements().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val writes = Channel<Conversation>(Channel.UNLIMITED)
    private val draftCache = mutableMapOf<String, Conversation>()
    private val lock = Mutex()
    private val deletedPeople = mutableSetOf<String>()
    private var recorder: MediaRecorder? = null
    private var recordingDraft: Conversation? = null
    private var recordingFile: File? = null
    private var meter: Job? = null
    val centres: List<Centre> = JSONArray(app.assets.open("karnataka.json").bufferedReader().use { it.readText() }).let { data ->
        (0 until data.length()).map { i -> data.getJSONObject(i).let { c ->
            Centre(c.getString("name"), c.getString("city"), c.optString("district"), c.getString("address"),
                c.optJSONArray("phoneNumbers")?.optString(0) ?: c.getString("phone"), c.getJSONArray("services").let { (0 until it.length()).joinToString(" · ") { j -> it.getString(j) } },
                c.optString("morphine"), c.getString("directoryUrl"))
        } }
    }
    init {
        viewModelScope.launch {
            for (value in writes) try { lock.withLock { if (value.personId !in deletedPeople) withContext(Dispatchers.IO) { dao.saveDraft(value) } } }
            catch (_: Exception) { message.value = "Couldn't save the conversation. Keep this screen open and try again." }
        }
        viewModelScope.launch {
            selected.collectLatest { id ->
                recordings.value = emptyList()
                draft.value = draftCache[id] ?: Conversation("")
                val saved = withContext(Dispatchers.IO) { dao.draft(id) }
                // A person switch must not reload an older draft ahead of queued writes.
                draft.value = draftCache[id] ?: saved ?: Conversation(id)
                recordings.value = withContext(Dispatchers.IO) { recordingFiles(id) }
            }
        }
        viewModelScope.launch {
            people.collect { list -> if (list.isNotEmpty() && list.none { it.id == selected.value }) select(list.first().id) }
        }
    }
    fun select(id: String) {
        if (recording.value) stopRecording()
        selected.value = id; preferences.edit().putString("person", id).apply()
    }
    fun role(value: Boolean) { clinician.value = value; preferences.edit().putBoolean("clinician", value).apply() }
    fun addPerson(name: String, done: () -> Unit = {}) = action {
        require(name.trim().length in 1..100) { "Enter a name, up to 100 characters." }
        val person = Person(name = name.trim()); dao.addPerson(person); select(person.id); done()
    }
    fun edit(value: Conversation) {
        if (value.personId != selected.value || value.personId.isBlank()) return
        draft.value = value.copy(updated = System.currentTimeMillis())
        draftCache[value.personId] = draft.value
        writes.trySend(draft.value)
    }
    fun organise() { edit(NoteRules.organise(draft.value)); message.value = "Your words are ready to review. Empty sections stay empty." }
    fun publish(doctor: String, reviewed: Boolean, done: () -> Unit) {
        val captured = draft.value; val person = people.value.firstOrNull { it.id == captured.personId } ?: return
        action {
            db.withTransaction {
                val latest = dao.latest(person.id)
                val version = NoteRules.publish(person, captured, doctor, reviewed, (latest?.version ?: 0) + 1)
                require(latest?.contentHash != version.contentHash) { "This exact note has already been saved." }
                dao.insertVersion(version)
            }
            message.value = "Care note saved"; done()
        }
    }
    fun confirm(version: CareVersion, name: String, relationship: String, reviewed: Boolean, done: () -> Unit) = action {
        db.withTransaction { dao.acknowledge(NoteRules.acknowledge(version, dao.latest(version.personId), name, relationship, reviewed)) }
        message.value = "Your confirmation is saved"; done()
    }
    fun saveItem(kind: String, title: String, detail: String, due: Long = 0, done: () -> Unit = {}) {
        val person = selected.value
        action {
            require(person.isNotBlank() && title.trim().isNotEmpty()) { "Add a name or a short note." }
            require(title.length <= 200 && detail.length <= 4000) { "Please shorten this entry." }
            dao.saveItem(CareItem(personId = person, kind = kind, title = title.trim(), detail = detail.trim(), due = due))
            message.value = "Saved"; done()
        }
    }
    fun toggle(item: CareItem) = action {
        val today = LocalDate.now().toString()
        val checked = if (item.kind == "medicine") item.doneOn == today else item.doneOn.isNotBlank()
        dao.saveItem(item.copy(doneOn = if (checked) "" else today))
    }
    fun updateItem(item: CareItem, done: () -> Unit) = action {
        require(item.personId == selected.value && item.title.isNotBlank()) { "Choose the right person and enter a name." }
        dao.saveItem(item.copy(title = item.title.trim(), detail = item.detail.trim())); message.value = "Saved"; done()
    }
    fun remove(item: CareItem) = action { dao.removeItem(item); message.value = "Removed" }
    fun removePerson(done: () -> Unit) {
        val id = selected.value
        action {
            require(!recording.value) { "Stop the recording first." }
            // Ignore queued edits for deleted records; an old write must never recreate them.
            lock.withLock {
                deletedPeople.add(id)
                draftCache.remove(id)
                db.withTransaction { dao.deleteAcknowledgements(id); dao.deleteVersions(id); dao.deleteItems(id); dao.deleteDraft(id); dao.deletePersonRow(id) }
                withContext(Dispatchers.IO) { File(getApplication<Application>().filesDir, "recordings/$id").deleteRecursively() }
            }
            select(""); message.value = "Records removed from this phone"; done()
        }
    }
    fun startRecording(consent: Boolean) {
        if (recording.value) return
        if (!consent || draft.value.personId.isBlank()) { message.value = "Ask everyone before recording."; return }
        val base = draft.value.copy(consentAt = System.currentTimeMillis())
        val file = File(getApplication<Application>().filesDir, "recordings/${base.personId}/${newId()}.m4a")
        file.parentFile?.mkdirs()
        @Suppress("DEPRECATION")
        val next = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(getApplication()) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC); next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); next.setAudioEncodingBitRate(96000); next.setAudioSamplingRate(44100)
            next.setMaxDuration(90 * 60 * 1000); next.setMaxFileSize(100L * 1024 * 1024)
            next.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stopRecording() }
            next.setOutputFile(file.absolutePath); next.prepare(); next.start()
            recorder = next; recordingDraft = base; recordingFile = file; recording.value = true; recordingSeconds.value = 0
            meter = viewModelScope.launch { var ticks = 0; while (isActive) { delay(200); amplitude.value = runCatching { next.maxAmplitude / 32767f }.getOrDefault(0f); ticks++; recordingSeconds.value = ticks / 5 } }
        } catch (_: Exception) { next.release(); file.delete(); message.value = "Couldn't start the microphone. Check permission and try again." }
    }
    fun stopRecording() {
        val active = recorder ?: return
        meter?.cancel(); recording.value = false; amplitude.value = 0f; recorder = null
        try {
            active.stop()
            val base = recordingDraft ?: return
            val current = if (draft.value.personId == base.personId) draft.value else base
            edit(current.copy(audioPath = recordingFile!!.absolutePath, consentAt = base.consentAt))
            refreshRecordings(base.personId)
            message.value = "Recording saved. Add the words you want in the care note."
        } catch (_: Exception) { recordingFile?.delete(); message.value = "That recording was too short to save. Try again." }
        finally { active.release(); recordingFile = null; recordingDraft = null }
    }
    private fun recordingFiles(id: String): List<File> = if (id.isBlank()) emptyList() else
        File(getApplication<Application>().filesDir, "recordings/$id").listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    private fun refreshRecordings(id: String) { viewModelScope.launch {
        val files = withContext(Dispatchers.IO) { recordingFiles(id) }
        if (selected.value == id) recordings.value = files
    } }
    fun deleteAudio(path: String) {
        val id = selected.value
        action {
            require(recordings.value.any { it.absolutePath == path }) { "Choose a recording for this person." }
            val removed = withContext(Dispatchers.IO) { File(path).delete() }
            require(removed) { "Couldn't remove the recording. Please try again." }
            if (draft.value.personId == id && draft.value.audioPath == path) edit(draft.value.copy(audioPath = "", consentAt = 0))
            refreshRecordings(id); message.value = "Recording removed"
        }
    }
    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() } catch (e: Exception) { message.value = if (e is IllegalArgumentException) e.message else "Couldn't save. Your entry is still here; please try again." }
            finally { busy.value = false }
        }
    }
    override fun onCleared() { recorder?.release(); meter?.cancel(); super.onCleared() }
}
