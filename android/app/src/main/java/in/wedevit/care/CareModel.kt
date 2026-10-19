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
    val accountNamespace: String get() = account
    private val db = CareDatabase.get(app, account)
    private val dao = db.care()
    private val reminderScheduler = CareReminderScheduler(app, account)
    private val preferences = app.getSharedPreferences(if (account.isEmpty()) "preferences" else "preferences-$account", 0)
    private val conversationTranscriber = ConversationTranscriber()
    val people = dao.people().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selected = MutableStateFlow(preferences.getString("person", "") ?: "")
    val viewRole = MutableStateFlow(preferences.getString("viewRole", null)?.takeIf { it in listOf("Patient", "Family", "Doctor") }
        ?: if (preferences.getBoolean("clinician", false)) "Doctor" else "Family")
    val clinician = MutableStateFlow(viewRole.value == "Doctor")
    val draft = MutableStateFlow(Conversation(""))
    val message = MutableStateFlow<String?>(null)
    val actionError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    val refreshError = MutableStateFlow<String?>(null)
    val verifiedReviewIds = MutableStateFlow<Set<String>>(emptySet())
    val authenticatedConfirmationIds = MutableStateFlow<Set<String>>(emptySet())
    private val editable = MutableStateFlow<Set<String>>(emptySet())
    private val owned = MutableStateFlow<Set<String>>(emptySet())
    val canEdit = combine(selected, editable) { id, ids -> cloud == null || id in ids }.stateIn(viewModelScope, SharingStarted.Eagerly, cloud == null)
    val isOwner = combine(selected, owned) { id, ids -> cloud == null || id in ids }.stateIn(viewModelScope, SharingStarted.Eagerly, cloud == null)
    val recording = MutableStateFlow(false)
    val amplitude = MutableStateFlow(0f)
    val recordingSeconds = MutableStateFlow(0)
    val transcribingPath = MutableStateFlow<String?>(null)
    val recordings = MutableStateFlow<List<File>>(emptyList())
    val versions = selected.flatMapLatest { dao.versions(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recordingConsents = selected.flatMapLatest { dao.recordingConsents(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val items = selected.flatMapLatest { dao.items(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reports = selected.flatMapLatest { dao.reports(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reportFiles = ReportFiles(app, account)
    val acknowledgements = dao.acknowledgements().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val writes = Channel<Conversation>(Channel.UNLIMITED)
    private val draftCache = mutableMapOf<String, Conversation>()
    private val lock = Mutex()
    private val deletedPeople = mutableSetOf<String>()
    private var recorder: MediaRecorder? = null
    private var recordingDraft: Conversation? = null
    private var recordingFile: File? = null
    private var activeRecordingConsent: RecordingConsent? = null
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
        runCatching { reminderScheduler.activate() }
        viewModelScope.launch { reconcileReminders() }
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
                dao.clearRemovedDrafts(); dao.clearRemovedRecordingConsents()
                removed.forEach { dao.deleteReports(it) }
            }
            withContext(Dispatchers.IO) { removed.forEach { reportFiles.removePerson(it) } }
            editable.value = records.editable; owned.value = records.owned; verifiedReviewIds.value = records.verifiedReviews; authenticatedConfirmationIds.value = records.authenticatedConfirmations
            if (selected.value in removed) { draft.value = Conversation(""); select(records.people.firstOrNull()?.id.orEmpty()) }
        }
        reconcileReminders()
    }
    private fun requireEdit(id: String) { require(cloud == null || id in editable.value) { "You can read these records. Ask the person who invited you to allow changes." } }
    fun select(id: String) {
        if (recording.value) stopRecording()
        selected.value = id; preferences.edit().putString("person", id).apply()
    }
    fun role(value: Boolean) = role(if (value) "Doctor" else "Family")
    fun role(value: String) {
        require(value in listOf("Patient", "Family", "Doctor"))
        viewRole.value = value; clinician.value = value == "Doctor"
        preferences.edit().putString("viewRole", value).putBoolean("clinician", clinician.value).apply()
    }
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
            require(selected.value == person.id && clinician.value) { "Open this person's conversation in the doctor view before saving." }
            requireEdit(person.id)
                val latest = dao.latest(person.id)
                if (cloud != null) {
                    require(reviewed && captured.source.isNotBlank() && NoteRules.values(captured).any { it.isNotBlank() }) { "Check the source and every section before sending for review." }
                    val candidates = cloud.sharedDrafts(person.id)
                    val previousId = preferences.getString("sharedDraft:${person.id}", null)
                    val previous = candidates.firstOrNull { it.id == previousId && it.status != "reviewed" }
                    val saved = cloud.saveSharedDraft(captured, previous?.id ?: newId(), previous?.revision ?: 0, submit = true)
                    preferences.edit().putString("sharedDraft:${person.id}", saved.id).apply()
                    message.value = "Sent for clinician review. Your original draft is retained."
                } else {
                    val version = NoteRules.publish(person, captured, doctor, reviewed, (latest?.version ?: 0) + 1)
                    require(latest?.contentHash != version.contentHash) { "This exact note has already been saved." }
                    dao.insertVersion(version)
                    message.value = "Care note saved"
                }
            done()
        }
    }
    fun confirm(version: CareVersion, name: String, relationship: String, reviewed: Boolean, done: () -> Unit) = action {
        require(selected.value == version.personId) { "Open this person's care note before confirming." }
        val previousConfirmations = dao.confirmations(version.id).filter { cloud == null || it.id in authenticatedConfirmationIds.value }
        val proposed = NoteRules.acknowledge(version, dao.latest(version.personId), name, relationship, reviewed, previousConfirmations)
        val ack = if (cloud != null && dao.confirmation(proposed.id) != null && proposed.id !in authenticatedConfirmationIds.value) proposed.copy(id = newId()) else proposed
        val saved = cloud?.confirm(version.personId, ack) ?: ack
        require(saved.versionId == version.id && saved.contentHash == version.contentHash && saved.relationship == relationship && saved.name.trim().equals(name.trim(), ignoreCase = true)) { "The saved confirmation didn't match this version. Refresh the care note before trying again." }
        val previous = dao.confirmation(saved.id)
        if (previous == null) dao.acknowledge(saved)
        else require(previous == saved) { "This confirmation changed. Refresh the care note before trying again." }
        if (cloud != null) authenticatedConfirmationIds.value += saved.id
        message.value = "Your confirmation is saved"; done()
    }
    fun saveItem(kind: String, title: String, detail: String, due: Long = 0, done: () -> Unit = {}, id: String = newId()) {
        val person = selected.value
        action {
            require(person.isNotBlank() && selected.value == person && title.trim().isNotEmpty()) { "Choose the right person and add a name or a short note." }
            require(people.value.any { it.id == person }) { "This person's records are no longer available." }
            require(title.length <= 200 && detail.length <= 4000) { "Please shorten this entry." }
            if (CareEntryCodec.isStructured(detail)) {
                val entry = requireNotNull(CareEntryCodec.decode(detail)) { "This entry couldn't be read. Open it again." }
                when (entry.type) {
                    "task", "conversation" -> CareWorkflowRules.validate(title, entry)
                    "lab" -> CareLabRules.validate(title, entry, due)
                    "schedule" -> require(due > 0 && entry.category in setOf("Visit", "Test", "Follow-up", "Procedure")) { "Choose a date and type of care." }
                }
            }
            storeItem(CareItem(id = id, personId = person, kind = kind, title = title.trim(), detail = detail.trim(), due = due))
            message.value = "Saved"; done()
        }
    }
    fun saveMedicineList(drafts: List<MedicineDraft>, done: () -> Unit = {}) {
        val person = selected.value
        action {
            require(person.isNotBlank() && selected.value == person) { "Choose who these medicines are for." }
            require(drafts.isNotEmpty()) { "Add at least one medicine." }
            require(drafts.all { it.confirmed }) { "Check and confirm every medicine before saving." }
            val names = drafts.map { it.medicine.medicineName.trim() }
            require(names.all { it.isNotBlank() && it.length <= 200 }) { "Add a medicine name to every entry." }
            require(names.map { it.lowercase() }.distinct().size == names.size) { "Each medicine needs a different name." }
            drafts.forEach { draft ->
                val medicine = draft.medicine
                require(medicine.strength.isNotBlank() || medicine.quantity.isNotBlank() || medicine.form.isNotBlank()) { "Add the prescribed dose for ${medicine.medicineName}." }
                val detail = medicineCareDetail(medicine)
                require(detail.isNotBlank() && detail.length <= 4000) { "Review the dose and directions for ${medicine.medicineName}." }
                val existing = dao.item(draft.id)
                require(existing == null || (existing.personId == person && existing.kind == "medicine")) { "One medicine could not be matched to this person. Start the scan again." }
                storeItem(CareItem(
                    id = draft.id,
                    personId = person,
                    kind = "medicine",
                    title = medicine.medicineName.trim(),
                    detail = detail,
                    created = existing?.created ?: System.currentTimeMillis(),
                    doneOn = existing?.doneOn.orEmpty(),
                    revision = existing?.revision ?: 0,
                ))
            }
            message.value = "${drafts.size} ${if (drafts.size == 1) "medicine" else "medicines"} saved"
            done()
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
    private suspend fun storeItem(item: CareItem) { requireEdit(item.personId); dao.saveItem(cloud?.saveEntry(item) ?: item); reconcileReminders() }
    private suspend fun reconcileReminders() {
        // Alerts are secondary to accepted care records. A platform alert failure
        // must never announce that an already saved medicine/task failed to save.
        runCatching { reminderScheduler.reconcile(dao.allItems()) }
    }
    fun remove(item: CareItem) = action { requireEdit(item.personId); cloud?.removeEntry(item); dao.removeItem(item); reconcileReminders(); message.value = "Removed" }
    fun importReport(uri: android.net.Uri, personId: String) = action(waitForRefresh = true) {
        require(people.value.any { it.id == personId }) { "Choose a person before adding a report." }
        requireEdit(personId)
        val report = try { reportFiles.copyFrom(uri, personId) }
        catch (e: SecurityException) { throw IllegalArgumentException("Couldn't read this file. Choose it again from Files.", e) }
        catch (e: java.io.IOException) { throw IllegalArgumentException("Couldn't add the report. Your original file is unchanged. Try again.", e) }
        try { dao.addReport(report) }
        catch (e: Exception) { withContext(NonCancellable + Dispatchers.IO) { reportFiles.file(report).delete() }; throw e }
        message.value = "Report added"
    }
    fun removeReport(report: CareReport, done: () -> Unit) = action {
        require(report.personId == selected.value && reports.value.any { it.id == report.id }) { "Choose a report for this person." }
        requireEdit(report.personId)
        withContext(Dispatchers.IO) {
            val file = reportFiles.file(report)
            require(!file.exists() || file.delete()) { "Couldn't remove this report. Try again." }
        }
        dao.removeReport(report); message.value = "Report removed"; done()
        CareDocumentReviewStore(getApplication(), account).remove(report)
    }
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
                db.withTransaction { dao.deleteAcknowledgements(id); dao.deleteVersions(id); dao.deleteItems(id); dao.deleteReports(id); dao.deleteRecordingConsents(id); dao.deleteDraft(id); dao.deletePersonRow(id) }
                withContext(Dispatchers.IO) { File(audioRoot(), id).deleteRecursively(); reportFiles.removePerson(id) }
                CareDocumentReviewStore(getApplication(), account).removePerson(id)
            }
            reconcileReminders()
            select(""); message.value = "Records removed"; done()
        }
    }
    fun startRecording(
        participants: String,
        agreedParticipants: Set<String>,
        purposeAccepted: Boolean,
        storageAccepted: Boolean,
        accessAccepted: Boolean,
        transcriptionAccepted: Boolean,
    ) {
        if (recording.value) return
        if (draft.value.personId.isBlank() || !canEdit.value) { message.value = "Choose an editable person before recording."; return }
        val consentAt = System.currentTimeMillis()
        val base = draft.value.copy(consentAt = consentAt)
        val file = File(audioRoot(), "${base.personId}/${newId()}.m4a")
        val consent = try {
            RecordingConsentRules.create(
                personId = base.personId,
                audioPath = file.absolutePath,
                participants = participants,
                agreedParticipants = agreedParticipants,
                purposeAccepted = purposeAccepted,
                storageAccepted = storageAccepted,
                accessAccepted = accessAccepted,
                transcriptionAccepted = transcriptionAccepted,
                now = consentAt,
            )
        } catch (e: IllegalArgumentException) {
            message.value = e.message
            return
        }
        file.parentFile?.mkdirs()
        @Suppress("DEPRECATION")
        val next = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(getApplication()) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC); next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioChannels(1)
            next.setAudioEncodingBitRate(if (transcriptionAccepted) 64000 else 96000)
            next.setAudioSamplingRate(if (transcriptionAccepted) 32000 else 44100)
            // Leave one second for the AAC encoder's final frame/container padding.
            next.setMaxDuration(if (transcriptionAccepted) 299 * 1000 else 90 * 60 * 1000)
            next.setMaxFileSize(if (transcriptionAccepted) ConversationTranscriber.MAX_AUDIO_BYTES else 100L * 1024 * 1024)
            next.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stopRecording() }
            next.setOutputFile(file.absolutePath); next.prepare(); next.start()
            recorder = next; recordingDraft = base; recordingFile = file; activeRecordingConsent = consent; recording.value = true; recordingSeconds.value = 0
            meter = viewModelScope.launch { var ticks = 0; while (isActive) { delay(200); amplitude.value = runCatching { next.maxAmplitude / 32767f }.getOrDefault(0f); ticks++; recordingSeconds.value = ticks / 5 } }
        } catch (_: Exception) { next.release(); file.delete(); activeRecordingConsent = null; message.value = "Couldn't start the microphone. Check permission and try again." }
    }
    fun stopRecording() {
        val active = recorder ?: return
        meter?.cancel(); recording.value = false; amplitude.value = 0f; recorder = null
        try {
            active.stop()
            val base = recordingDraft ?: return
            val current = if (draft.value.personId == base.personId) draft.value else base
            edit(current.copy(audioPath = recordingFile!!.absolutePath, consentAt = base.consentAt))
            activeRecordingConsent?.let { consent -> viewModelScope.launch(Dispatchers.IO) { dao.saveRecordingConsent(consent) } }
            refreshRecordings(base.personId)
            message.value = "Recording saved. Add the words you want in the care note."
        } catch (_: Exception) { recordingFile?.delete(); message.value = "That recording was too short to save. Try again." }
        finally { active.release(); recordingFile = null; recordingDraft = null; activeRecordingConsent = null }
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
            dao.deleteRecordingConsent(path)
            if (draft.value.personId == id && draft.value.audioPath == path) edit(draft.value.copy(audioPath = "", consentAt = 0))
            refreshRecordings(id); message.value = "Recording removed"
        }
    }
    fun transcribeAudio(path: String, consent: RecordingConsent) {
        val personId = selected.value
        action {
            require(BuildConfig.CLOUD_AUDIO_ENABLED) { "Online audio processing is not available in this release. Use on-device dictation or enter the conversation text." }
            requireEdit(personId)
            require(path == consent.audioPath && consent.personId == personId) { "Choose a recording for this person." }
            require(draft.value.personId == personId && draft.value.source.isBlank()) { "Clear the conversation text before creating a new transcript." }
            val service = cloud ?: throw IllegalArgumentException("Sign in to Sahara before sending audio for transcription.")
            transcribingPath.value = path
            try {
                val result = try {
                    conversationTranscriber.transcribe(File(path), consent, service.transcriptionToken())
                } catch (error: ConversationTranscriptionException) {
                    // Only an auth rejection is retried; provider/quota failures must not double-call Gemini.
                    if (error.code != ConversationTranscriptionError.AUTH_REQUIRED) throw error
                    conversationTranscriber.transcribe(File(path), consent, service.transcriptionToken(forceRefresh = true))
                }
                require(result.text.length <= NoteRules.MAX_SOURCE_CHARS) { "This transcript is too long for a shared care note. Keep the recording and enter a shorter selection." }
                require(selected.value == personId && draft.value.personId == personId && draft.value.source.isBlank()) { "The conversation changed while transcription was running. The transcript was not inserted." }
                edit(draft.value.copy(
                    source = result.text,
                    transcriptProvider = result.provider,
                    transcriptModel = result.model,
                    transcribedAt = System.currentTimeMillis(),
                    transcriptAudioPath = path,
                ))
                message.value = "Transcript ready. Check every word before continuing."
            } finally {
                transcribingPath.value = null
            }
        }
    }
    suspend fun rememberTranscriptionConsent(consent: RecordingConsent) {
        val person = selected.value
        requireEdit(person)
        val saved = recordingConsents.value.firstOrNull { it.audioPath == consent.audioPath && it.personId == person }
            ?: throw IllegalArgumentException("Open this person's consented recording again.")
        require(consent.personId == person && consent.participants == saved.participants && consent.recordingConsentAt == saved.recordingConsentAt
            && consent.transcriptionConsentAt > 0 && consent.noticeVersion >= 2) { "Review consent for this exact recording first." }
        require(recordingFiles(person).any { it.absolutePath == consent.audioPath }) { "This recording is no longer available." }
        dao.saveRecordingConsent(consent)
    }
    private fun action(waitForRefresh: Boolean = false, block: suspend () -> Unit) {
        if (busy.value || (refreshing.value && !waitForRefresh)) { message.value = "Please wait for your records to finish loading."; return }
        actionError.value = null
        busy.value = true
        viewModelScope.launch {
            try { if (waitForRefresh) refreshing.first { !it }; block() } catch (e: Exception) {
                if (e is CancellationException) throw e
                val error = if (e is IllegalArgumentException || e is ConversationTranscriptionException) e.message else "Couldn't save. Your entry is still here; please try again."
                actionError.value = error; message.value = error
            }
            finally { busy.value = false }
        }
    }
    override fun onCleared() { recorder?.release(); meter?.cancel(); super.onCleared() }
}
