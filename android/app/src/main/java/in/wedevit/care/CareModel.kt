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

data class Centre(val name: String, val city: String, val district: String, val address: String, val phone: String, val services: String, val morphine: String, val url: String, val id: String = name, val latitude: Double? = null, val longitude: Double? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class CareModel(app: Application, val cloud: CareCloud? = null) : AndroidViewModel(app) {
    private val account = cloud?.session?.value?.userId.orEmpty()
    private val db = CareDatabase.get(app, account)
    private val dao = db.care()
    private val preferences = app.getSharedPreferences(if (account.isEmpty()) "preferences" else "preferences-$account", 0)
    val people = dao.people().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selected = MutableStateFlow(preferences.getString("person", "") ?: "")
    val clinician = MutableStateFlow(preferences.getBoolean("clinician", false))
    val draft = MutableStateFlow(Conversation(""))
    val message = MutableStateFlow<String?>(null)
    val actionError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    val refreshError = MutableStateFlow<String?>(null)
    private val editable = MutableStateFlow<Set<String>>(emptySet())
    private val owned = MutableStateFlow<Set<String>>(emptySet())
    val canEdit = combine(selected, editable) { id, ids -> cloud == null || id in ids }.stateIn(viewModelScope, SharingStarted.Eagerly, cloud == null)
    val isOwner = combine(selected, owned) { id, ids -> cloud == null || id in ids }.stateIn(viewModelScope, SharingStarted.Eagerly, cloud == null)
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
                c.optString("morphine"), c.getString("directoryUrl"), c.getString("id"),
                c.optDouble("latitude").takeIf { it.isFinite() }, c.optDouble("longitude").takeIf { it.isFinite() })
        } }
    }
    init {
        if (cloud != null) refresh()
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
    fun refresh() {
        if (cloud == null || refreshing.value || busy.value || recording.value) return
        refreshing.value = true
        viewModelScope.launch {
            try { reload(); refreshError.value = null }
            catch (e: Exception) { if (e is CancellationException) throw e; refreshError.value = e.message ?: "Couldn't refresh. Try again." }
            finally { refreshing.value = false }
        }
    }
    private suspend fun reload() {
        val records = cloud?.load() ?: return
        lock.withLock {
            val removed = people.value.map { it.id }.toSet() - records.people.map { it.id }.toSet()
            removed.forEach { draftCache.remove(it); deletedPeople.add(it) }
            db.withTransaction {
                dao.clearAcknowledgements(); dao.clearVersions(); dao.clearItems(); dao.clearPeople()
                dao.addPeople(records.people); dao.addItems(records.entries); dao.addVersions(records.notes); dao.addAcknowledgements(records.confirmations)
                dao.clearRemovedDrafts()
            }
            editable.value = records.editable; owned.value = records.owned
            if (selected.value in removed) { draft.value = Conversation(""); select(records.people.firstOrNull()?.id.orEmpty()) }
        }
    }
    private fun requireEdit(id: String) { require(cloud == null || id in editable.value) { "You can read these records. Ask the person who invited you to allow changes." } }
    fun select(id: String) {
        if (recording.value) stopRecording()
        selected.value = id; preferences.edit().putString("person", id).apply()
    }
    fun role(value: Boolean) { clinician.value = value; preferences.edit().putBoolean("clinician", value).apply() }
    fun addPerson(name: String, done: () -> Unit = {}) = action {
        require(name.trim().length in 1..100) { "Enter a name, up to 100 characters." }
        val person = Person(name = name.trim()); cloud?.addPerson(person); dao.addPerson(person)
        editable.value += person.id; owned.value += person.id; select(person.id); done()
    }
    fun edit(value: Conversation) {
        if (value.personId != selected.value || value.personId.isBlank() || (cloud != null && value.personId !in editable.value)) return
        draft.value = value.copy(updated = System.currentTimeMillis())
        draftCache[value.personId] = draft.value
        writes.trySend(draft.value)
    }
    fun organise() { edit(NoteRules.organise(draft.value)); message.value = "Ready to review" }
    fun publish(doctor: String, reviewed: Boolean, done: () -> Unit) {
        val captured = draft.value; val person = people.value.firstOrNull { it.id == captured.personId } ?: return
        action {
            requireEdit(person.id)
                val latest = dao.latest(person.id)
                val version = NoteRules.publish(person, captured, doctor, reviewed, (latest?.version ?: 0) + 1)
                require(latest?.contentHash != version.contentHash) { "This exact note has already been saved." }
                cloud?.saveNote(version)
                dao.insertVersion(version)
            message.value = "Care note saved"; done()
        }
    }
    fun confirm(version: CareVersion, name: String, relationship: String, reviewed: Boolean, done: () -> Unit) = action {
        val ack = NoteRules.acknowledge(version, dao.latest(version.personId), name, relationship, reviewed)
        cloud?.confirm(version.personId, ack); dao.acknowledge(ack)
        message.value = "Your confirmation is saved"; done()
    }
    fun saveItem(kind: String, title: String, detail: String, due: Long = 0, done: () -> Unit = {}, id: String = newId()) {
        val person = selected.value
        action {
            require(person.isNotBlank() && title.trim().isNotEmpty()) { "Add a name or a short note." }
            require(title.length <= 200 && detail.length <= 4000) { "Please shorten this entry." }
            storeItem(CareItem(id = id, personId = person, kind = kind, title = title.trim(), detail = detail.trim(), due = due))
            message.value = "Saved"; done()
        }
    }
    fun toggle(item: CareItem) = action {
        val today = LocalDate.now().toString()
        val checked = if (item.kind == "medicine") item.doneOn == today else item.doneOn.isNotBlank()
        storeItem(item.copy(doneOn = if (checked) "" else today))
    }
    fun updateItem(item: CareItem, done: () -> Unit) = action {
        require(item.personId == selected.value && item.title.isNotBlank()) { "Choose the right person and enter a name." }
        require(item.title.length <= 200 && item.detail.length <= 4000) { "Please shorten this entry." }
        storeItem(item.copy(title = item.title.trim(), detail = item.detail.trim())); message.value = "Saved"; done()
    }
    private suspend fun storeItem(item: CareItem) { requireEdit(item.personId); dao.saveItem(cloud?.saveEntry(item) ?: item) }
    fun remove(item: CareItem) = action { requireEdit(item.personId); cloud?.removeEntry(item); dao.removeItem(item); message.value = "Removed" }
    fun removePerson(done: () -> Unit) {
        val id = selected.value
        action {
            require(!recording.value) { "Stop the recording first." }
            require(cloud == null || id in owned.value) { "Only the person who added these records can remove them." }
            cloud?.removePerson(id)
            // Ignore queued edits for deleted records; an old write must never recreate them.
            lock.withLock {
                deletedPeople.add(id)
                draftCache.remove(id)
                db.withTransaction { dao.deleteAcknowledgements(id); dao.deleteVersions(id); dao.deleteItems(id); dao.deleteDraft(id); dao.deletePersonRow(id) }
                withContext(Dispatchers.IO) { File(audioRoot(), id).deleteRecursively() }
            }
            select(""); message.value = "Records removed"; done()
        }
    }
    fun startRecording(consent: Boolean) {
        if (recording.value) return
        if (!consent || draft.value.personId.isBlank() || !canEdit.value) { message.value = "Ask everyone before recording."; return }
        val base = draft.value.copy(consentAt = System.currentTimeMillis())
        val file = File(audioRoot(), "${base.personId}/${newId()}.m4a")
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
    private fun audioRoot() = File(getApplication<Application>().filesDir, if (account.isEmpty()) "recordings" else "recordings-$account")
    private fun recordingFiles(id: String): List<File> = if (id.isBlank()) emptyList() else
        File(audioRoot(), id).listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()
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
        if (busy.value || refreshing.value) { message.value = "Please wait for your records to finish loading."; return }
        actionError.value = null
        busy.value = true
        viewModelScope.launch {
            try { block() } catch (e: Exception) {
                if (e is CancellationException) throw e
                val error = if (e is IllegalArgumentException) e.message else "Couldn't save. Your entry is still here; please try again."
                actionError.value = error; message.value = error
            }
            finally { busy.value = false }
        }
    }
    override fun onCleared() { recorder?.release(); meter?.cancel(); super.onCleared() }
}
