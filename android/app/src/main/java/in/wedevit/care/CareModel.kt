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

data class Centre(val name: String, val city: String, val district: String, val address: String, val phone: String, val services: String, val morphine: String, val url: String, val id: String = name, val latitude: Double? = null, val longitude: Double? = null,
    val state: String = "Karnataka", val sourceName: String = "Pallium India", val checkedOn: String = "2026-10-02", val verification: String = "directory_listed", val sourceListedMorphine: String = "",
    /** "exact", or "pincode"/"city"/"district" for an approximate area pin; see CentreLocation. */
    val locationPrecision: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
class CareModel(app: Application, val cloud: CareCloud? = null, val demoMode: Boolean = false, val walkthroughRole: String? = null) : AndroidViewModel(app) {
    init { require(!demoMode || cloud == null) { "Doctor examples cannot use a cloud account." } }
    init { require(walkthroughRole == null || (demoMode && cloud == null && walkthroughRole in listOf("Patient", "Family"))) { "The patient walkthrough is local and read as Patient or Family only." } }
    companion object {
        const val DOCTOR_DEMO_STORAGE = "4b68f5f1-36f4-4bd7-96b3-c8eb728951f1"
        private var pendingDemoClose: Job? = null
        private val demoStorageLock = Mutex()
        fun createDoctorDemo(app: Application) = CareModel(app, cloud = null, demoMode = true)
        /** Local patient or family walkthrough over the same offline namespace; its role is never stored. */
        fun createPatientWalkthrough(app: Application, role: String) = CareModel(app, cloud = null, demoMode = true, walkthroughRole = role)
    }
    private val account = cloud?.session?.value?.userId.orEmpty().also {
        require(cloud == null || it.isNotBlank()) { "Sign in again before opening cloud care records." }
    }
    val accountNamespace: String get() = account
    val storageNamespace: String = if (demoMode) DOCTOR_DEMO_STORAGE else account
    val localAudioRecordingAllowed: Boolean get() = DoctorLocalAudioRules.allowed(demoMode,cloud!=null,account,storageNamespace,selected.value)
    private val db = CareDatabase.get(app, storageNamespace)
    private val dao = db.care()
    /** Signed-in writes go here first and are sent in order; see CareSyncQueue. */
    private val sync = cloud?.let { CareSyncQueue.get(app, it, account) }
    val syncWaiting: StateFlow<Int> = sync?.waiting?.stateIn(viewModelScope, SharingStarted.Eagerly, 0) ?: MutableStateFlow(0)
    val syncKept: StateFlow<Int> = sync?.kept?.stateIn(viewModelScope, SharingStarted.Eagerly, 0) ?: MutableStateFlow(0)
    private val connectivity = sync?.let { app.getSystemService(android.net.ConnectivityManager::class.java) }
    private val networkReturned = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) { sendPending() }
    }
    private var demoCloseJob: Job? = null
    private val activeConversationIds = mutableMapOf<String,String>()
    /** Only the isolated Doctor example database is exposed to the fixture importer. */
    suspend fun seedDoctorDemo(block: suspend (CareDao) -> Unit) {
        check(demoMode && cloud == null && account.isBlank() && storageNamespace == DOCTOR_DEMO_STORAGE)
        synchronized(Companion) { pendingDemoClose }?.let { pending ->
            pending.join()
            check(!pending.isCancelled) { "The previous practice draft could not be saved. Reopen Doctor examples after resolving the save error." }
        }
        withContext(Dispatchers.IO) {
            demoStorageLock.withLock { db.withTransaction { block(dao) } }
            val person = selected.value
            if (person.isNotBlank()) {
                draftCache.remove(person)
                draft.value = dao.draft(person) ?: Conversation(person)
            }
        }
    }
    fun closeDoctorDemo(): Job {
        check(demoMode && cloud == null)
        demoCloseJob?.takeUnless { it.isCancelled }?.let { return it }
        stopRecording()
        val audioWrite=recordingSaveJob
        val writer = viewModelScope.coroutineContext[Job]
        // The route may disappear, but its final practice draft must outlive UI disposal.
        return synchronized(Companion) {
            val errors = CoroutineExceptionHandler { _, _ -> message.value = "The practice draft could not be saved. Keep Doctor examples open and try again." }
            CoroutineScope(SupervisorJob() + Dispatchers.IO + errors).launch(start = CoroutineStart.LAZY) {
                awaitDoctorLocalAudioSave(audioWrite) {recordingSaveError.value}
                val snapshots=withContext(Dispatchers.Main.immediate) {
                    val values=draftCache.values.toList() to pendingContextArchives.values.toList()
                    writer?.cancel()
                    values
                }
                writer?.join()
                demoStorageLock.withLock { db.withTransaction { snapshots.second.forEach { db.conversations().archive(it) };snapshots.first.forEach { dao.saveDraft(it) } } }
            }.also { demoCloseJob = it; pendingDemoClose = it; it.start() }
        }
    }
    private val reminderScheduler = if (demoMode) null else CareReminderScheduler(app, account)
    private val preferences = app.getSharedPreferences(if (storageNamespace.isEmpty()) "preferences" else "preferences-$storageNamespace", 0)
    private val conversationTranscriber = ConversationTranscriber()
    /** True once the first list of people has come from the database, so the first frame never shows the welcome screen to someone who has records. */
    val peopleLoaded = MutableStateFlow(false)
    val people = dao.people().onEach { peopleLoaded.value = true }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selected = MutableStateFlow(preferences.getString("person", "") ?: "")
    val viewRole = MutableStateFlow(walkthroughRole ?: CareRoles.forOwner(cloud == null))
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
    /** Server access for a shared person, as loaded at the last refresh. */
    fun canEditPerson(id: String) = cloud == null || id in editable.value
    val canEdit = combine(selected, editable) { id, ids -> cloud == null || id in ids }.stateIn(viewModelScope, SharingStarted.Eagerly, cloud == null)
    val isOwner = combine(selected, owned) { id, ids -> cloud == null || id in ids }.stateIn(viewModelScope, SharingStarted.Eagerly, cloud == null)
    init { // The role is computed, never chosen from a menu: owner (or on-device profile) = Patient, invited member = Family. Older installs may hold a stored Doctor preference; drop it.
        if (walkthroughRole == null) {
            CareRoles.staleKeys(preferences.all).takeIf { it.isNotEmpty() }?.let { keys -> preferences.edit().apply { keys.forEach { remove(it) } }.apply() }
            viewModelScope.launch { isOwner.collect { if (viewRole.value != "Doctor") role(CareRoles.forOwner(it)) } }
        }
    }
    val careContacts = MutableStateFlow<List<CareContact>>(emptyList())
    val careContactsLoading = MutableStateFlow(false)
    val careContactsError = MutableStateFlow<String?>(null)
    val canReadCareTeam = MutableStateFlow(cloud == null)
    private val localContacts = LocalCareContacts(app, if (demoMode) storageNamespace else "")
    val recording = MutableStateFlow(false)
    val recordingSaving = MutableStateFlow(false)
    val recordingSaveError = MutableStateFlow<String?>(null)
    val amplitude = MutableStateFlow(0f)
    val recordingSeconds = MutableStateFlow(0)
    val transcribingPath = MutableStateFlow<String?>(null)
    val recordings = MutableStateFlow<List<File>>(emptyList())
    val versions = selected.flatMapLatest { dao.versions(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recordingConsents = selected.flatMapLatest { dao.recordingConsents(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val medicineHistory = MedicineHistoryRepo(app, cloud, storageNamespace)
    private val medicinePastIds = MutableStateFlow<Set<String>>(emptySet())
    /** Intake reports for the selected person (server copy when signed in), newest first. */
    val medicineEvents = MutableStateFlow<List<MedicineIntakeEvent>>(emptyList())
    private val prescriptionExcludedIds=selected.flatMapLatest { db.prescriptions().excludedIds(it) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    /** Person whose care entries have arrived from the database; empty-state cards wait for it so they never flash before the first rows. */
    val itemsLoadedFor = MutableStateFlow("")
    val allItems = selected.flatMapLatest { person -> dao.items(person).onEach { itemsLoadedFor.value = person }.onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val items = combine(allItems, medicinePastIds,prescriptionExcludedIds) { rows, past,excluded -> rows.filterNot { it.kind == "medicine" && (it.id in past || it.id in excluded) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val conversationArchives=selected.flatMapLatest { db.conversations().archives(it) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val reports = selected.flatMapLatest { dao.reports(it).onStart { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reportFiles = ReportFiles(app, storageNamespace)
    val acknowledgements = dao.acknowledgements().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private data class DraftWrite(val value:Conversation,val archive:DoctorConversationArchive?=null)
    private val writes = Channel<DraftWrite>(Channel.UNLIMITED)
    private val pendingContextArchives=java.util.concurrent.ConcurrentHashMap<String,DoctorConversationArchive>()
    private val draftCache = mutableMapOf<String, Conversation>()
    private val lock = Mutex()
    private val deletedPeople = mutableSetOf<String>()
    private var recorder: MediaRecorder? = null
    private var recordingDraft: Conversation? = null
    private var recordingFile: File? = null
    private var activeRecordingConsent: RecordingConsent? = null
    private var activeRecorderUid: String = ""
    private var meter: Job? = null
    private var recordingSaveJob: Job? = null
    private var pendingRecordingMetadata: Pair<RecordingConsent,DoctorLocalAudioRef>? = null
    /** The 1.5 MB bundled directory is parsed once per process, off the start-up path (warmed below on a background thread). */
    val centres: List<Centre> get() = CareCentreDirectory.bundled(getApplication())
    /** Activating reminders commits preferences and re-sets every alarm; it runs in the background, before any reminder reconcile. */
    private val reminderActivation = viewModelScope.async(Dispatchers.IO) { runCatching { reminderScheduler?.activate() } }
    init {
        viewModelScope.launch(Dispatchers.Default) { centres }
        viewModelScope.launch { reconcileReminders() }
        viewModelScope.launch { prescriptionExcludedIds.collectLatest { reconcileReminders() } }
        if (cloud != null) refresh()
        connectivity?.let { runCatching { it.registerDefaultNetworkCallback(networkReturned) } }
        viewModelScope.launch {
            for (write in writes) try { val value=write.value;lock.withLock { if (value.personId !in deletedPeople) withContext(Dispatchers.IO) {
                val archives=pendingContextArchives.values.filter { it.personId==value.personId }
                db.withTransaction {
                archives.forEach { db.conversations().archive(it) }
                if(activeConversationIds[value.personId]==null || activeConversationIds[value.personId]==value.conversationId)dao.saveDraft(value)
            };archives.forEach { pendingContextArchives.remove(it.id) } } } }
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
                refreshCareContacts(id)
                refreshMedicineHistory(id)
            }
        }
        viewModelScope.launch {
            people.collect { list -> if (list.isNotEmpty() && list.none { it.id == selected.value }) select(list.first().id) }
        }
    }
    /** Every synced entry across this account's people, for the doctor Home and Schedule. */
    fun allCareItems(): kotlinx.coroutines.flow.Flow<List<CareItem>> = dao.everyItem()
    fun refresh() {
        if (cloud == null || refreshing.value || busy.value || recording.value) return
        refreshing.value = true
        viewModelScope.launch {
            try { reload(); refreshError.value = null }
            catch (e: Exception) { if (e is CancellationException) throw e; refreshError.value = e.message ?: "Couldn't refresh. Try again." }
            finally { refreshing.value = false }
        }
    }
    /** After joining someone's care: reload records, then open the newly visible person. */
    suspend fun joinedRefresh(before: Set<String>): Person? { reload(); val added = dao.people().first().firstOrNull { it.id !in before }; if (added != null) select(added.id); return added }
    private suspend fun reload() {
        val service = cloud ?: return
        fun requireSameAccount() = check(careCloudAccountMatches(account, service.session.value?.userId)) {
            "The signed-in account changed. Reopen your care records."
        }
        requireSameAccount()
        sync?.flush()
        val records = service.load()
        requireSameAccount()
        lock.withLock {
            requireSameAccount()
            val removed = dao.people().first().map { it.id }.toSet() - records.people.map { it.id }.toSet()
            removed.forEach { person -> draftCache.remove(person); deletedPeople.add(person);pendingContextArchives.entries.removeAll { it.value.personId==person } }
            db.withTransaction {
                requireSameAccount()
                dao.clearAcknowledgements(); dao.clearVersions(); dao.clearItems(); dao.clearPeople()
                dao.addPeople(records.people); dao.addItems(records.entries); dao.addVersions(records.notes); dao.addAcknowledgements(records.confirmations)
                removed.forEach { sync?.purgePerson(it) }
                sync?.overlay(records.people.map { it.id }.toSet())
                dao.clearRemovedDrafts(); dao.clearRemovedRecordingConsents()
                removed.forEach { dao.deleteReports(it) }
                removed.forEach { db.prescriptions().purgePerson(it);db.conversations().purgePerson(it) }
                requireSameAccount()
            }
            withContext(Dispatchers.IO) { removed.forEach { reportFiles.removePerson(it) } }
            editable.value = records.editable; owned.value = records.owned; verifiedReviewIds.value = records.verifiedReviews; authenticatedConfirmationIds.value = records.authenticatedConfirmations
            if (selected.value in removed) { draft.value = Conversation(""); select(records.people.firstOrNull()?.id.orEmpty()) }
        }
        reconcileReminders()
        refreshCareContacts(selected.value)
        refreshMedicineHistory(selected.value)
    }
    private fun requireEdit(id: String) { require(cloud == null || id in editable.value) { "You can read these records. Ask the person who invited you to allow changes." } }
    fun select(id: String) {
        if (recording.value) stopRecording()
        selected.value = id; preferences.edit().putString("person", id).apply()
        careContacts.value = emptyList(); canReadCareTeam.value = cloud == null; careContactsError.value = null; careContactsLoading.value = false
        val cachedHistory = if (id.isBlank()) MedicineHistorySnapshot() else runCatching { medicineHistory.cached(id) }.getOrDefault(MedicineHistorySnapshot())
        medicinePastIds.value = cachedHistory.pastIds; medicineEvents.value = cachedHistory.events
    }
    private fun sameCareContext(personId: String, expectedAccount: String, ownerOnly: Boolean = false): Boolean =
        careContextMatches(expectedAccount, cloud?.session?.value?.userId.orEmpty(), personId, selected.value,
            people.value.any { it.id == personId } && (cloud == null || personId in if (ownerOnly) owned.value else editable.value)) && account == expectedAccount
    fun refreshCareContacts() { val person = selected.value; viewModelScope.launch { refreshCareContacts(person) } }
    fun refreshMedicineHistory() { val person = selected.value; viewModelScope.launch { refreshMedicineHistory(person) } }
    private suspend fun refreshMedicineHistory(person: String) {
        if (person.isBlank()) return
        try { val snapshot = medicineHistory.load(person); if (selected.value == person && cloud?.session?.value?.userId.orEmpty() == account) { medicinePastIds.value = snapshot.pastIds; medicineEvents.value = snapshot.events; reconcileReminders() } }
        catch (e: Exception) { if (e is CancellationException) throw e /* Keep last-known scoped regimen; never report cloud success. */ }
    }
    private suspend fun refreshCareContacts(personId: String) {
        if (personId.isBlank()) return
        careContactsLoading.value = true; careContactsError.value = null
        try {
            val snapshot = if (cloud == null) CareContactSnapshot(true, withContext(Dispatchers.IO) { localContacts.list(personId) }) else cloud.careContacts(personId).let { it.copy(contacts = CareSyncOps.overlayContacts(it.contacts, sync!!.pending(personId))) }
            if (selected.value == personId && (cloud?.session?.value?.userId.orEmpty() == account)) { careContacts.value = snapshot.contacts; canReadCareTeam.value = snapshot.readable }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (selected.value == personId) { careContacts.value = emptyList(); canReadCareTeam.value = false; careContactsError.value = "Care-team contacts couldn't be loaded. Nothing was uploaded or changed." }
        } finally { if (selected.value == personId) careContactsLoading.value = false }
    }
    fun saveCareContact(contact: CareContact, expectedAccount: String, done: () -> Unit) = action {
        require(sameCareContext(contact.personId, expectedAccount, ownerOnly = true)) { "Only this care profile's owner can manage its contacts." }
        CareContactRules.validate(contact.name, contact.relationship, contact.role, contact.phone)
        val sent = sync == null || sync.submit(CareSyncOps.saveContact(contact)) == CareSyncStatus.SENT
        val saved = if (cloud == null) withContext(Dispatchers.IO) { localContacts.save(contact) } else contact
        if (sameCareContext(contact.personId, expectedAccount, ownerOnly = true)) {
            careContacts.value = careContacts.value.filterNot { it.id == saved.id } + saved; canReadCareTeam.value = true; done()
            if (cloud != null) { if (sent) refreshCareContacts(contact.personId) else message.value = CareSyncText.SAVED_WAITING }
        }
    }
    fun removeCareContact(contact: CareContact, expectedAccount: String, done: () -> Unit) = action {
        require(sameCareContext(contact.personId, expectedAccount, ownerOnly = true)) { "Only this care profile's owner can manage its contacts." }
        val sent = if (sync == null) { withContext(Dispatchers.IO) { localContacts.remove(contact) }; true } else sync.submit(CareSyncOps.removeContact(contact)) == CareSyncStatus.SENT
        if (sameCareContext(contact.personId, expectedAccount, ownerOnly = true)) { careContacts.value = careContacts.value.filterNot { it.id == contact.id }; done(); if (!sent) message.value = CareSyncText.REMOVED_WAITING }
    }
    fun saveDoctorVisit(expectedPersonId: String, expectedAccountId: String, doctorName: String, startsAt: Long, location: String, note: String, onSaved: () -> Unit, id: String = newId(), createdAt: Long = System.currentTimeMillis(), initial: CareItem? = null) = action {
        require(sameCareContext(expectedPersonId, expectedAccountId)) { "Care access changed. Reopen the selected profile." }
        var item = DoctorVisitRules.item(id, expectedPersonId, doctorName, startsAt, location, note).copy(created = createdAt)
        if (initial != null) {
            val current = dao.item(initial.id)
            require(initial.personId == expectedPersonId && current == initial && initial.id == id) { "This visit changed. Reopen it before editing." }
            val old = CareEntryCodec.decode(initial.detail)
            val next = CareEntryCodec.decode(item.detail)!!
            item = item.copy(created = initial.created, revision = initial.revision, doneOn = initial.doneOn, detail = CareEntryCodec.encode(next.copy(status = old?.status ?: "open", completedAt = old?.completedAt ?: 0, completedBy = old?.completedBy.orEmpty())))
        }
        // Persist only to the account-scoped local database; never switch to a different selected person.
        require(item.personId == expectedPersonId)
        val sent = storeItem(item)
        if (sameCareContext(expectedPersonId, expectedAccountId)) { message.value = if (cloud == null) "Visit saved on this device" else if (sent) "Visit saved to shared care. The patient can see it after refreshing." else CareSyncText.SAVED_WAITING; onSaved() }
    }
    fun role(value: Boolean) = role(if (value) "Doctor" else CareRoles.forOwner(isOwner.value))
    fun role(value: String) {
        require(value in listOf("Patient", "Family", "Doctor"))
        if (walkthroughRole != null) return // The walkthrough user is fixed and never written to saved preferences.
        viewRole.value = value; clinician.value = value == "Doctor" // In memory only: the Doctor workspace and tests may set it, but it is never saved.
    }
    fun addPerson(name: String, done: () -> Unit = {}) = action {
        require(name.trim().length in 1..100) { "Enter a name, up to 100 characters." }
        val person = Person(name = name.trim()); cloud?.addPerson(person); dao.addPerson(person)
        editable.value += person.id; owned.value += person.id; select(person.id); done()
    }
    fun edit(value: Conversation) {
        if (value.personId != selected.value || value.personId.isBlank() || (cloud != null && (!careCloudAccountMatches(account,cloud.session.value?.userId) || value.personId !in editable.value))) return
        val previous=draft.value
        var next=value
        var archive:DoctorConversationArchive?=null
        if(previous.personId==value.personId && previous.contextJson.isNotBlank() && previous.source!=value.source) {
            val change=runCatching { DoctorConversationContext.validate(previous.source,previous.contextJson);DoctorConversationContext.sourceChanged(previous,value.source) }
            if(change.isFailure) { message.value="This saved review context is unsupported or damaged. Its original words were kept; do not overwrite it.";return }
            change.onSuccess { (context,keep) ->
                val rebound=org.json.JSONObject(context)
                if(value.transcribedAt>0 && (value.transcribedAt!=previous.transcribedAt || value.transcriptModel!=previous.transcriptModel || value.transcriptProvider!=previous.transcriptProvider)) {
                    rebound.put("transcription",org.json.JSONObject().put("provider",value.transcriptProvider).put("model",value.transcriptModel).put("at",value.transcribedAt).put("sourceHash",DoctorConversationContext.sha256(value.source.toByteArray(Charsets.UTF_8))))
                }
                next=value.copy(contextJson=rebound.toString())
                if(keep)archive=DoctorConversationArchive(newId(),previous.personId,previous.conversationId.ifBlank { newId() },DoctorConversationContext.archiveJson(previous),System.currentTimeMillis())
            }
        }
        draft.value = next.copy(updated = System.currentTimeMillis())
        draftCache[value.personId] = draft.value
        archive?.let { pendingContextArchives[it.id]=it }
        writes.trySend(DraftWrite(draft.value,archive))
    }
    fun organise() { edit(NoteRules.organise(draft.value)); message.value = "Ready to review" }
    fun prepareDoctorConversation() {
        val current=draft.value
        if(current.personId!=selected.value || current.personId.isBlank()) return
        if(editableDoctorContext(current)==null)return
        edit(current.copy(conversationId=current.conversationId.ifBlank { newId() },contextJson=current.contextJson.ifBlank { DoctorConversationContext.initial(current) }))
    }
    private fun sameDoctorConversation(expected:DoctorConversationIdentity):Boolean {
        val current=draft.value
        return doctorConversationIdentityMatches(expected,DoctorConversationIdentity(account,current.personId,current.conversationId,DoctorConversationContext.sha256(current.source.toByteArray(Charsets.UTF_8))),sameCareContext(expected.personId,expected.accountId))
    }
    private fun editableDoctorContext(current:Conversation):org.json.JSONObject? = runCatching {
        val text=current.contextJson.ifBlank { DoctorConversationContext.initial(current) }
        DoctorConversationContext.validate(current.source,text);org.json.JSONObject(text)
    }.getOrElse { message.value="This saved review context is unsupported or damaged. Its original content was kept.";null }
    fun updateDoctorGoal(goal:DoctorGoalContext,expected:DoctorConversationIdentity) {
        if(!sameDoctorConversation(expected)) { message.value="The conversation changed. Reopen it before editing.";return }
        val current=draft.value
        try {
            require(goal.key in DoctorConversationContext.keys && goal.status in DoctorConversationContext.statuses && (goal.origin in DoctorConversationContext.origins || CarriedForward.isOrigin(goal.origin)))
            val context=editableDoctorContext(current) ?: return
            val goals=context.getJSONArray("goals")
            for(i in 0 until goals.length()) if(goals.getJSONObject(i).getString("key")==goal.key) goals.put(i,DoctorConversationContext.goalJson(goal))
            DoctorConversationContext.validate(current.source,context.toString())
            val next=current.copy(conversationId=current.conversationId.ifBlank { newId() },contextJson=context.toString())
            edit(when(goal.key) {
                "patient_priorities"->next.copy(priorities=goal.answer)
                "understanding_questions"->next.copy(questions=goal.answer)
                "participants"->next.copy(participants=goal.answer)
                "next_steps"->next.copy(nextSteps=goal.answer)
                else->next
            })
        } catch(_:Exception) { message.value="The original words or review changed. Check the source before saving this goal." }
    }
    fun startNewDoctorConversation(expected:DoctorConversationIdentity,done:()->Unit={}) {
        if(!sameDoctorConversation(expected)) { message.value="The conversation changed. Nothing was reset.";return }
        val captured=draft.value;val person=captured.personId
        action {
            require(!recording.value && !recordingSaving.value && recordingSaveError.value==null && transcribingPath.value==null && person==selected.value && person.isNotBlank()) { "Finish the current recording or action first." }
            requireEdit(person)
            val fresh=CarriedForward.draftFrom(person,newId(),dao.latest(person))
            lock.withLock {
                db.withTransaction {
                    require(draft.value==captured && sameCareContext(person,account)) { "The conversation or care access changed. Nothing was reset." }
                    if(captured.source.isNotBlank() || NoteRules.values(captured).any(String::isNotBlank) || captured.audioPath.isNotBlank()) {
                        db.conversations().archive(DoctorConversationArchive(newId(),person,captured.conversationId.ifBlank { newId() },DoctorConversationContext.archiveJson(captured),System.currentTimeMillis()))
                    }
                    dao.saveDraft(fresh)
                    require(draft.value==captured && sameCareContext(person,account)) { "The conversation or care access changed. Nothing was reset." }
                }
                activeConversationIds[person]=fresh.conversationId
                draftCache[person]=fresh;draft.value=fresh
            }
            done()
        }
    }
    fun updateDoctorEnglishDraft(text:String,reviewed:Boolean,expected:DoctorConversationIdentity,origin:String="manual") {
        if(!sameDoctorConversation(expected)) { message.value="The conversation changed. Reopen it before editing.";return }
        val current=draft.value
        if((origin !in DoctorConversationContext.origins && !CarriedForward.isOrigin(origin)) || text.length>NoteRules.MAX_SOURCE_CHARS)return
        val context=editableDoctorContext(current) ?: return
        val previous=context.optJSONObject("englishDraft")
        val explicitlyReviewed=reviewed && previous?.optString("text")==text
        context.put("englishDraft",if(text.isBlank()) org.json.JSONObject.NULL else org.json.JSONObject().put("text",text).put("sourceHash",DoctorConversationContext.sha256(current.source.toByteArray(Charsets.UTF_8))).put("origin",origin).put("reviewed",explicitlyReviewed))
        edit(current.copy(conversationId=current.conversationId.ifBlank { newId() },contextJson=context.toString()))
    }
    fun updateDoctorRecorderLabel(label:String,expected:DoctorConversationIdentity) {
        if(!sameDoctorConversation(expected)) { message.value="The conversation changed. Reopen it before editing.";return }
        val current=draft.value
        val context=(editableDoctorContext(current) ?: return).put("recorderLabel",label.take(120))
        edit(current.copy(conversationId=current.conversationId.ifBlank { newId() },contextJson=context.toString()))
    }
    fun saveDoctorSharedDraftForAudience(expected:DoctorConversationIdentity,done:(SharedCareDraft)->Unit) = action {
        require(!recording.value && !recordingSaving.value && recordingSaveError.value==null) { "Finish saving the local recording before sharing." }
        require(sameDoctorConversation(expected)) { "The conversation changed. Reopen it before sharing." }
        val service=cloud ?: error("Conversation-specific family access requires a signed-in shared care account.")
        val captured=draft.value
        require(captured.conversationId.isNotBlank()) { "Start this conversation before choosing its audience." }
        DoctorConversationContext.validate(captured.source,captured.contextJson)
        service.requireConversationCapabilities(account)
        val candidates=service.sharedDrafts(captured.personId,expectedAccountId=account)
        require(sameDoctorConversation(expected) && draft.value==captured) { "The conversation changed. Your local draft was kept." }
        val key="sharedDraft:${captured.personId}:${captured.conversationId}"
        val previous=candidates.firstOrNull { it.id==preferences.getString(key,null) && it.authorId==account && it.status!="reviewed" && it.conversation.conversationId==captured.conversationId }
        val saved=service.saveSharedDraft(captured,previous?.id ?: newId(),previous?.revision ?: 0,submit=false,expectedAccountId=account)
        preferences.edit().putString(key,saved.id).apply()
        if(sameDoctorConversation(expected) && draft.value==captured)done(saved)
        else message.value="The earlier snapshot was saved. Reopen the current conversation before choosing its audience."
    }
    fun attachDoctorRecording(path:String,expected:DoctorConversationIdentity) = action {
        require(sameDoctorConversation(expected)) { "The conversation changed. Attach the recording again." }
        val current=draft.value;val person=current.personId
        require(person==selected.value && person.isNotBlank());requireEdit(person)
        require(recordingFiles(person).any { it.absolutePath==path }) { "Choose a recording for this care profile." }
        val consent=dao.recordingConsents(person).first().firstOrNull { it.audioPath==path && it.recordingConsentAt>0 } ?: error("Recording consent is not available.")
        val conversation=current.conversationId.ifBlank { newId() }
        val old=db.conversations().audioForPath(path,person)
        val ref=old?.takeIf { it.conversationId==conversation } ?: withContext(Dispatchers.IO) {
            DoctorLocalAudioRef(newId(),person,conversation,path,DoctorConversationContext.fileSha256(File(path)),consent.recordingConsentAt,old?.recorderUid.orEmpty(),System.currentTimeMillis()).also { db.conversations().addAudio(it) }
        }
        val context=org.json.JSONObject(current.contextJson.ifBlank { DoctorConversationContext.initial(current) })
        context.put("audioRef",ref.id).put("audioHash",ref.sha256).put("recorderUid",ref.recorderUid.takeIf(String::isNotBlank) ?: org.json.JSONObject.NULL)
        require(sameDoctorConversation(expected) && draft.value==current) { "The conversation changed. Attach the recording again." }
        edit(current.copy(conversationId=conversation,contextJson=context.toString()))
        message.value="Recording linked on this device only. Audio is not shared."
    }
    suspend fun doctorAudioFor(note:CareVersion):File? {
        if(note.personId!=selected.value || versions.value.none { it.id==note.id && it.contentHash==note.contentHash }) return null
        if(runCatching { DoctorConversationContext.validateVersion(note) }.isFailure)return null
        val context=runCatching { org.json.JSONObject(note.contextJson) }.getOrNull() ?: return null
        val id=context.optString("audioRef").takeIf { runCatching { java.util.UUID.fromString(it) }.isSuccess } ?: return null
        val ref=db.conversations().audio(id,note.personId) ?: return null
        if(ref.conversationId!=note.conversationId)return null
        val file=File(ref.localPath)
        return withContext(Dispatchers.IO) {
            val folder=File(audioRoot(),note.personId).canonicalFile
            file.takeIf { it.isFile && it.canonicalFile.parentFile==folder && ref.sha256==context.optString("audioHash") && DoctorConversationContext.fileSha256(it)==ref.sha256 }
        }?.takeIf { note.personId==selected.value && versions.value.any { current -> current.id==note.id && current.contentHash==note.contentHash } && (cloud==null || careCloudAccountMatches(account,cloud.session.value?.userId)) }
    }
    fun publish(doctor: String, reviewed: Boolean, done: () -> Unit) {
        val captured = draft.value; val person = people.value.firstOrNull { it.id == captured.personId } ?: return
        action {
            require(!recording.value && !recordingSaving.value && recordingSaveError.value==null) { "Finish saving the local recording before publishing." }
            require(selected.value == person.id && clinician.value) { "Open this person's conversation in the doctor view before saving." }
            require(sameCareContext(person.id,account)) { "The signed-in account or care access changed. Your draft was kept." }
            requireEdit(person.id)
                val latest = dao.latest(person.id)
                if (cloud != null) {
                    require(reviewed && captured.source.isNotBlank() && NoteRules.values(captured).any { it.isNotBlank() }) { "Check the source and every section before sending for review." }
                    cloud.requireConversationCapabilities(account)
                    val contextual=captured.copy(conversationId=captured.conversationId.ifBlank { newId() },contextJson=captured.contextJson.ifBlank { DoctorConversationContext.initial(captured) })
                    DoctorConversationContext.validate(contextual.source,contextual.contextJson)
                    require(draft.value==captured && sameCareContext(person.id,account)) { "The conversation changed. Review it again before sharing." }
                    edit(contextual)
                    val reviewedSnapshot=draft.value
                    val candidates = cloud.sharedDrafts(person.id,expectedAccountId=account)
                    val draftKey="sharedDraft:${person.id}:${contextual.conversationId}"
                    val previousId = preferences.getString(draftKey, null)
                    val previous = candidates.firstOrNull { it.id == previousId && it.status != "reviewed" && it.conversation.conversationId==contextual.conversationId }
                    require(sameCareContext(person.id,account) && draft.value==reviewedSnapshot) { "The conversation or signed-in account changed. Your draft was kept." }
                    val saved = cloud.saveSharedDraft(contextual, previous?.id ?: newId(), previous?.revision ?: 0, submit = true,expectedAccountId=account)
                    preferences.edit().putString(draftKey, saved.id).apply()
                    message.value = "Sent for clinician review. Your original draft is retained."
                } else {
                    val version = NoteRules.publish(person, captured, doctor, reviewed, (latest?.version ?: 0) + 1)
                    require(latest==null || !DoctorConversationContext.sameContent(latest,version)) { "This exact note has already been saved." }
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
        val write = sync?.let { CareSyncOps.confirm(version.personId, ack) }
        if (sync != null && write != null) {
            val status = sync.submit(write, local = { if (dao.confirmation(ack.id) == null) dao.acknowledge(ack) }, rollback = { dao.deleteAcknowledgement(ack.id) })
            if (status != CareSyncStatus.SENT) { message.value = CareSyncText.CONFIRMED_WAITING; done(); return@action }
        }
        val saved = write?.let { sync?.takeConfirmation(it.opId) } ?: ack
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
                    "schedule" -> require(due > 0 && entry.category in setOf("Visit", "Test", "Follow-up", "Procedure") + TreatmentDayRules.categories) { "Choose a date and type of care." }
                }
            }
            val sent = storeItem(CareItem(id = id, personId = person, kind = kind, title = title.trim(), detail = detail.trim(), due = due))
            message.value = if (sent) "Saved" else CareSyncText.SAVED_WAITING; done()
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
            var allSent = true
            drafts.forEach { draft ->
                val medicine = draft.medicine
                require(medicine.strength.isNotBlank() || medicine.quantity.isNotBlank() || medicine.form.isNotBlank()) { "Add the prescribed dose for ${medicine.medicineName}." }
                val detail = medicineCareDetail(medicine)
                require(detail.isNotBlank() && detail.length <= 4000) { "Review the dose and directions for ${medicine.medicineName}." }
                val existing = dao.item(draft.id)
                require(existing == null || (existing.personId == person && existing.kind == "medicine")) { "One medicine could not be matched to this person. Start the scan again." }
                allSent = storeItem(CareItem(
                    id = draft.id,
                    personId = person,
                    kind = "medicine",
                    title = medicine.medicineName.trim(),
                    detail = detail,
                    created = existing?.created ?: System.currentTimeMillis(),
                    doneOn = existing?.doneOn.orEmpty(),
                    revision = existing?.revision ?: 0,
                )) && allSent
            }
            message.value = if (allSent) "${drafts.size} ${if (drafts.size == 1) "medicine" else "medicines"} saved" else CareSyncText.SAVED_WAITING
            done()
        }
    }
    fun saveSymptomNote(personId: String, id: String, note: SymptomNote, expectedToken: String?, done: () -> Unit = {}) = action {
        require(personId.isNotBlank() && selected.value == personId && people.value.any { it.id == personId }) { "Choose the right person before saving this note." }
        requireEdit(personId)
        SymptomDiaryRules.error(note)?.let { throw IllegalArgumentException(it) }
        val existing = dao.item(id)
        SymptomDiaryRules.saveConflict(existing, personId, expectedToken)?.let { throw IllegalArgumentException(it) }
        val candidate = CareItem(id = id, personId = personId, kind = "checkin", title = "${note.symptom} · ${if (note.symptom == SymptomDiaryRules.MOOD) SymptomDiaryRules.moodSummary(note.moodRatings, false) else SymptomDiaryRules.ratingLabel(note.rating)}",
            detail = SymptomNoteCodec.encode(note), created = existing?.created ?: System.currentTimeMillis(), revision = existing?.revision ?: 0)
        if (note.selfReportedChange.isNotBlank()) require(SymptomDiaryRules.comparison(dao.allItems(), candidate, note)?.previous?.id == note.comparedWithId) { "The previous note changed. Check the comparison and save again." }
        val sent = storeItem(candidate)
        message.value = if (sent) "Symptom note saved" else CareSyncText.SAVED_WAITING; done()
    }
    fun toggle(item: CareItem) = action {
        val today = LocalDate.now().toString()
        val checked = if (item.kind == "medicine") item.doneOn == today else item.doneOn.isNotBlank()
        require(item.personId == selected.value) { "Reopen the selected care profile." }
        val changed = item.copy(doneOn = if (checked) "" else today)
        storeItem(changed)
        if (cloud == null && item.kind == "medicine") {
            try { appendLocalMedicineCheckoff(getApplication(), storageNamespace, item, item.doneOn, changed.doneOn) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                message.value = "Daily check-off saved, but detailed history could not be recorded."
            }
        }
        if (item.kind == "medicine") refreshMedicineHistory(item.personId)
    }
    /** Family/patient report that today's dose was missed. Appended to the intake history (server first when signed in); nothing is overwritten. */
    fun reportMedicineMissed(item: CareItem) = action {
        require(item.kind == "medicine" && item.personId == selected.value && people.value.any { it.id == item.personId }) { "Reopen the selected care profile." }
        requireEdit(item.personId)
        val snapshot = medicineHistory.append(item.personId, MedicineHistoryRules.missedReport(item, viewRole.value, System.currentTimeMillis()))
        if (selected.value == item.personId) medicineEvents.value = snapshot.events
        message.value = "Missed dose recorded · छूटी खुराक दर्ज की गई"
    }
    fun updateItem(item: CareItem, done: () -> Unit) = action {
        require(item.personId == selected.value && item.title.isNotBlank()) { "Choose the right person and enter a name." }
        require(item.title.length <= 200 && item.detail.length <= 4000) { "Please shorten this entry." }
        val sent = storeItem(item.copy(title = item.title.trim(), detail = item.detail.trim())); message.value = if (sent) "Saved" else CareSyncText.SAVED_WAITING; done()
    }
    /** Signed in: kept on the phone first, then sent in order. Returns false while it is still waiting to send. */
    private suspend fun storeItem(item: CareItem): Boolean {
        requireEdit(item.personId)
        val queue = sync ?: run { dao.saveItem(item); reconcileReminders(); return true }
        // Queued writes are redacted before they are queued, so the upload and the phone's copy match (CarePrivacy; saveEntry redacts again on send).
        val shared = CarePrivacy.entryForUpload(item)
        val before = dao.item(shared.id)
        val status = queue.submit(CareSyncOps.saveEntry(shared), local = { dao.saveItem(shared) }, rollback = { if (before == null) dao.removeItem(shared) else dao.saveItem(before); reconcileReminders() })
        reconcileReminders()
        return status == CareSyncStatus.SENT
    }
    /** Sends waiting changes now (network back, app opened, or "Send now"), then pulls the server's copy. */
    fun sendPending() {
        val queue = sync ?: return
        viewModelScope.launch {
            val report = try { queue.flush() } catch (e: Exception) { if (e is CancellationException) throw e; return@launch }
            report.count(CareSyncStatus.SERVER_KEPT).takeIf { it > 0 }?.let { message.value = CareSyncText.serverKept(it) }
            if (report.count(CareSyncStatus.SENT) + report.count(CareSyncStatus.SERVER_KEPT) > 0) refresh()
        }
    }
    private suspend fun reconcileReminders() {
        if (demoMode) return
        // Alerts are secondary to accepted care records. A platform alert failure
        // must never announce that an already saved medicine/task failed to save.
        // Preference reads/commits and up to 200 alarm calls: keep them off the main thread.
        runCatching { withContext(Dispatchers.IO) {
            reminderActivation.await()
            val rows = dao.allItems()
            val past = rows.map { it.personId }.distinct().associateWith { person ->
                (if (person == selected.value) medicinePastIds.value else runCatching { medicineHistory.cached(person).pastIds }.getOrDefault(emptySet())) + db.prescriptions().excludedIds(person).first()
            }
            reminderScheduler?.reconcile(rows.filterNot { it.kind == "medicine" && it.id in past[it.personId].orEmpty() })
        } }
    }
    fun remove(item: CareItem) = action {
        requireEdit(item.personId)
        val queue = sync
        val sent = if (queue == null) { dao.removeItem(item); true } else {
            val before = dao.item(item.id) ?: item
            queue.submit(CareSyncOps.removeEntry(item), local = { dao.removeItem(item) }, rollback = { dao.saveItem(before) }) == CareSyncStatus.SENT
        }
        reconcileReminders(); message.value = if (sent) "Removed" else CareSyncText.REMOVED_WAITING
    }
    fun importReport(uri: android.net.Uri, personId: String) = action {
        require(people.value.any { it.id == personId }) { "Choose a person before adding a report." }
        requireEdit(personId)
        val report = try { reportFiles.copyFrom(uri, personId) }
        catch (e: SecurityException) { throw IllegalArgumentException("Couldn't read this file. Choose it again from Files.", e) }
        catch (e: java.io.IOException) { throw IllegalArgumentException("Couldn't add the report. Your original file is unchanged. Try again.", e) }
        try { dao.addReport(report) }
        catch (e: Exception) { withContext(NonCancellable + Dispatchers.IO) { reportFiles.file(report).delete() }; throw e }
        message.value = "Report added"
    }
    fun renameReport(report: CareReport, name: String, done: () -> Unit) = action {
        require(report.personId == selected.value && reports.value.any { it.id == report.id }) { "Choose a report for this person." }
        requireEdit(report.personId)
        require(dao.renameReport(report.id, report.personId, ReportNameRules.clean(name)) == 1) { "Couldn't rename this report. Try again." }
        message.value = "Report renamed · नाम बदल गया"; done()
    }
    fun removeReport(report: CareReport, done: () -> Unit) = action {
        require(report.personId == selected.value && reports.value.any { it.id == report.id }) { "Choose a report for this person." }
        requireEdit(report.personId)
        withContext(Dispatchers.IO) {
            val file = reportFiles.file(report)
            require(!file.exists() || file.delete()) { "Couldn't remove this report. Try again." }
        }
        dao.removeReport(report); message.value = "Report removed"; done()
        CareDocumentReviewStore(getApplication(), storageNamespace).remove(report)
    }
    /** After the server account is deleted: removes this account's copy on the phone (records, recordings, report files). */
    suspend fun forgetLocalAccountData() = withContext(Dispatchers.IO) {
        check(cloud != null && !demoMode)
        val ids = dao.people().first().map { it.id }
        lock.withLock { db.clearAllTables() }
        audioRoot().deleteRecursively()
        ids.forEach { reportFiles.removePerson(it) }
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
                pendingContextArchives.entries.removeAll { it.value.personId==id }
                db.withTransaction { dao.deleteAcknowledgements(id); dao.deleteVersions(id); dao.deleteItems(id); dao.deleteReports(id); dao.deleteRecordingConsents(id); db.prescriptions().purgePerson(id);db.conversations().purgePerson(id);dao.deleteDraft(id); dao.deletePersonRow(id); sync?.purgePerson(id) }
                withContext(Dispatchers.IO) { File(audioRoot(), id).deleteRecursively(); reportFiles.removePerson(id) }
                CareDocumentReviewStore(getApplication(), storageNamespace).removePerson(id)
                withContext(Dispatchers.IO) { EmergencyCardStore(getApplication(), storageNamespace).removePerson(id) }
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
        expected: DoctorConversationIdentity? = null,
    ) {
        if (recording.value || recordingSaving.value) return
        if(pendingRecordingMetadata!=null) {message.value="Finish saving the previous recording's consent details before recording again.";return}
        if (!localAudioRecordingAllowed || selected.value != draft.value.personId || !canEdit.value || (cloud!=null && cloud.session.value?.userId!=account)) { message.value = "Choose an editable care profile before recording."; return }
        if ((demoMode && (expected==null || expected.conversationId.isBlank())) || (expected!=null && !sameDoctorConversation(expected))) {message.value="The conversation changed. Review consent again before recording.";return}
        if (getApplication<Application>().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {message.value="Microphone permission is required. You can still type the conversation.";return}
        if (demoMode && transcriptionAccepted) {message.value="Test recordings stay on this device. Online transcription is unavailable.";return}
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
            recorder = next; recordingDraft = base; recordingFile = file; activeRecordingConsent = consent; activeRecorderUid=cloud?.session?.value?.userId?.takeIf { it==account }.orEmpty();recording.value = true; recordingSeconds.value = 0
            meter = viewModelScope.launch { var ticks = 0; while (isActive) { delay(200); amplitude.value = runCatching { DoctorLocalAudioRules.level(next.maxAmplitude) }.getOrDefault(0f); ticks++; recordingSeconds.value = ticks / 5 } }
        } catch (_: Exception) { next.release(); file.delete(); activeRecordingConsent = null; message.value = "Couldn't start the microphone. Check permission and try again." }
    }
    fun stopRecording() {
        val active = recorder ?: return
        meter?.cancel(); recording.value = false; amplitude.value = 0f; recorder = null
        try {
            active.stop()
            val base = recordingDraft ?: return
            val file=recordingFile ?: error("The recording file is unavailable.")
            inspectDoctorLocalAudio(file,audioRoot(),base.personId)
            activeRecordingConsent?.let { consent ->
                val ref=DoctorLocalAudioRef(newId(),base.personId,base.conversationId,file.absolutePath,"",consent.recordingConsentAt,activeRecorderUid,System.currentTimeMillis())
                pendingRecordingMetadata=consent to ref
                savePendingRecordingMetadata()
            }
        } catch (_: Exception) {
            recordingDraft?.takeIf { pendingRecordingMetadata?.second?.localPath != recordingFile?.absolutePath }?.let { discardUnfinishedDoctorLocalAudio(recordingFile, audioRoot(), it.personId) }
            message.value = "The recording was too short or could not be saved. Please try again."
        }
        finally { active.release(); recordingFile = null; recordingDraft = null; activeRecordingConsent = null;activeRecorderUid="" }
    }
    fun retrySavingRecording() { if(!recordingSaving.value)savePendingRecordingMetadata() }
    private fun savePendingRecordingMetadata() {
        val pending=pendingRecordingMetadata ?: return
        recordingSaving.value=true;recordingSaveError.value=null
        recordingSaveJob=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val file=File(pending.second.localPath)
                inspectDoctorLocalAudio(file,audioRoot(),pending.first.personId)
                val verified=pending.second.copy(sha256=DoctorConversationContext.fileSha256(file))
                db.withTransaction {
                    persistDoctorLocalAudioMetadata(pending.first,verified,{dao.people().first().any {it.id==pending.first.personId}}) {
                        dao.saveRecordingConsent(pending.first)
                        if(verified.conversationId.isNotBlank())db.conversations().addAudio(verified)
                        dao.attachLocalRecording(verified.personId,verified.conversationId,verified.localPath,verified.consentAt)
                    }
                }
                pendingRecordingMetadata=null
                withContext(Dispatchers.Main.immediate) {
                    val current=draft.value
                    if(DoctorLocalAudioRules.sameDraft(account,if(cloud==null)account else cloud.session.value?.userId.orEmpty(),verified.personId,current.personId,verified.conversationId,current.conversationId) && selected.value==verified.personId && canEdit.value) {
                        edit(current.copy(audioPath=verified.localPath,consentAt=verified.consentAt))
                        refreshRecordings(verified.personId)
                    }
                    message.value="Recording saved on this device. No transcript was generated."
                }
            } catch (_: Exception) {
                recordingSaveError.value="The audio file was kept, but its consent details could not be saved. Retry saving before leaving."
                message.value=recordingSaveError.value
            } finally {recordingSaving.value=false}
        }
    }
    private fun audioRoot() = File(getApplication<Application>().filesDir, if (storageNamespace.isEmpty()) "recordings" else "recordings-$storageNamespace")
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
            val removed = withContext(Dispatchers.IO) {
                File(path).delete().also { deleted -> if (deleted) File(File(path).parentFile, "${File(path).nameWithoutExtension}.offline-transcript.json").delete() }
            }
            require(removed) { "Couldn't remove the recording. Please try again." }
            dao.deleteRecordingConsent(path)
            db.conversations().removeAudioPath(id,path)
            if (draft.value.personId == id && draft.value.audioPath == path) edit(draft.value.copy(audioPath = "", consentAt = 0))
            refreshRecordings(id); message.value = "Recording removed"
        }
    }
    /** A reviewed offline result is inserted only into the same empty person/account draft. */
    fun useOfflineTranscript(path: String, review: OfflineTranscriptReview): Boolean {
        return try {
            val personId = selected.value
            requireEdit(personId)
            require(!recording.value && !busy.value && !refreshing.value) { "Finish the current action first." }
            require(recordingFiles(personId).any { it.absolutePath == path }) { "Choose a recording for this person." }
            val savedConsent = recordingConsents.value.firstOrNull { it.audioPath == path && it.personId == personId }
                ?: error("Open this person's consented recording again.")
            require(savedConsent.recordingConsentAt > 0 && draft.value.personId == personId && draft.value.source.isBlank()) { "The conversation changed. Your text was not replaced." }
            require(review.provider == OfflineSpeechModel.provider && review.model == OfflineSpeechModel.id && review.reviewedAt >= review.createdAt && review.reviewedAt > 0 && review.text.isNotBlank() && review.text.length <= NoteRules.MAX_SOURCE_CHARS) { "Review the transcript first." }
            edit(DoctorConversationContext.acceptedTranscript(draft.value.copy(source = review.text, transcriptProvider = review.provider, transcriptModel = review.model,
                transcribedAt = review.createdAt, transcriptAudioPath = path)))
            message.value = "Reviewed words added. Choose source words for the care note."
            true
        } catch (failure: Exception) { message.value = failure.message ?: "The transcript was not inserted."; false }
    }
    fun transcribeAudio(path: String, consent: RecordingConsent) {
        if (demoMode) { message.value = "Online transcription is unavailable in Doctor examples."; return }
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
                edit(DoctorConversationContext.acceptedTranscript(draft.value.copy(
                    source = result.text,
                    transcriptProvider = result.provider,
                    transcriptModel = result.model,
                    transcribedAt = System.currentTimeMillis(),
                    transcriptAudioPath = path,
                )))
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
    private fun action(block: suspend () -> Unit) {
        // A background refresh (for example on returning to the app) no longer turns a save away: the save waits for it, then runs.
        if (busy.value) { message.value = "Still working on your last change. Please wait a moment."; return } // busy is only set by another action (a save, delete or import), e.g. after a double tap
        actionError.value = null
        busy.value = true
        viewModelScope.launch {
            try { refreshing.first { !it }; block() } catch (e: Exception) {
                if (e is CancellationException) throw e
                val error = if (e is IllegalArgumentException || e is ConversationTranscriptionException) e.message else "Couldn't save. Your entry is still here; please try again."
                actionError.value = error; message.value = error
            }
            finally { busy.value = false }
        }
    }
    override fun onCleared() { stopRecording(); meter?.cancel(); connectivity?.let { runCatching { it.unregisterNetworkCallback(networkReturned) } }; super.onCleared() }
}

/** The patient app's role is derived from the account's relationship to the person, never chosen. Doctors use the Doctor workspace. */
object CareRoles {
    fun forOwner(owner: Boolean) = if (owner) "Patient" else "Family"
    /** Saved role preferences from older installs (a menu choice, possibly Doctor) that must be dropped so the computed role applies. */
    fun staleKeys(all: Map<String, *>): List<String> = listOf("viewRole", "clinician").filter { it in all }
}
