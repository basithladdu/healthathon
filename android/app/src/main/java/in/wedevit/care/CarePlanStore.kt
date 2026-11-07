package `in`.wedevit.care

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Everything about one person's emergency plan and living will, as kept on this phone. */
data class CarePlanState(
    val ectprs: List<EctprRecord> = emptyList(),
    val amds: List<AmdRecord> = emptyList(),
    val tokens: List<CardToken> = emptyList(),
    val reviews: List<ReviewRequest> = emptyList(),
    val audit: AuditLog = AuditLog.EMPTY,
    val episodes: List<EpisodeLog> = emptyList(),
    val outcomes: List<OutcomeRecord> = emptyList(),
    val incidents: List<IncidentReport> = emptyList(),
) {
    fun encode(): String = JSONObject().put("v", 2)
        .put("ectprs", JSONArray(ectprs.map(PlanJson::encode))).put("amds", JSONArray(amds.map(AmdJson::encode)))
        .put("tokens", JSONArray(tokens.map(PlanJson::encode))).put("reviews", JSONArray(reviews.map(PlanJson::encode)))
        .put("audit", JSONArray(audit.events.map(PlanJson::encode)))
        .put("episodes", JSONArray(episodes.map(PilotJson::encode))).put("outcomes", JSONArray(outcomes.map(PilotJson::encode))).put("incidents", JSONArray(incidents.map(PilotJson::encode))).toString()
    companion object {
        fun decode(json: String): CarePlanState {
            val j = JSONObject(json)
            return CarePlanState(PlanJson.objects(j.optJSONArray("ectprs")).map(PlanJson::decode), PlanJson.objects(j.optJSONArray("amds")).map(AmdJson::decode),
                PlanJson.objects(j.optJSONArray("tokens")).map(PlanJson::token), PlanJson.objects(j.optJSONArray("reviews")).map(PlanJson::review),
                AuditLog.of(PlanJson.objects(j.optJSONArray("audit")).map(PlanJson::audit)),
                PlanJson.objects(j.optJSONArray("episodes")).map(PilotJson::episode), PlanJson.objects(j.optJSONArray("outcomes")).map(PilotJson::outcome), PlanJson.objects(j.optJSONArray("incidents")).map(PilotJson::incident))
        }
    }
}

/**
 * Every change to the plan or the living will goes through here, so each one is checked by the rules and lands in the
 * append-only audit log. Pure: the screens keep the returned state with [CarePlanStore].
 */
object CarePlanBook {
    private fun CarePlanState.log(at: String, actor: String, action: AuditAction, type: AuditRecordType, id: String, detail: String? = null) = copy(audit = audit.append(at, actor, action, type, id, detail))

    fun startPlan(s: CarePlanState, patientId: String, actor: String, now: String, patient: EctprPatient, createdInEd: Boolean = false, clinical: ClinicalDetails = ClinicalDetails()): Pair<CarePlanState, EctprRecord> {
        require(EctprLifecycle.draft(s.ectprs, patientId) == null) { "A draft is already open." }
        val d = EctprLifecycle.newDraft(patientId, actor, now, patient, createdInEd).copy(clinical = clinical)
        return s.copy(ectprs = s.ectprs + d).log(now, actor, AuditAction.CREATE, AuditRecordType.ECTPR, d.id, if (createdInEd) "started in ED" else null) to d
    }

    /** "Goal C + Attempt CPR cannot save without a reason": a draft with blocking issues is refused. */
    fun saveDraft(s: CarePlanState, record: EctprRecord, actor: String, now: String, today: LocalDate): CarePlanState {
        val old = s.ectprs.firstOrNull { it.id == record.id } ?: error("Plan not found.")
        val next = EctprLifecycle.edit(old, now) { record }
        val blockers = EctprRules.saveBlockers(next, today)
        check(blockers.isEmpty()) { blockers.joinToString(" ") { it.message } }
        return s.copy(ectprs = s.ectprs.map { if (it.id == record.id) next else it }).log(now, actor, AuditAction.EDIT, AuditRecordType.ECTPR, record.id)
    }

    fun sign(s: CarePlanState, id: String, actor: String, now: String, today: LocalDate): CarePlanState {
        val r = s.ectprs.first { it.id == id }
        val records = EctprLifecycle.activate(s.ectprs, id, now, today)
        val superseded = s.ectprs.filter { it.patientId == r.patientId && it.status == EctprStatus.ACTIVE }.map { it.id }
        var next = s.copy(ectprs = records, tokens = CardTokens.sync(s.tokens, records, r.patientId, now, AmdLifecycle.statusSummary(s.amds, r.patientId)))
            .log(now, actor, AuditAction.SIGN, AuditRecordType.ECTPR, id, "active v${r.version}")
        superseded.forEach { next = next.log(now, actor, AuditAction.REVOKE, AuditRecordType.CARD, it, "superseded by v${r.version}; card tokens revoked") }
        return next.log(now, actor, AuditAction.SHARE, AuditRecordType.CARD, id, "ED card issued for version ${r.version}")
    }

    fun withdraw(s: CarePlanState, id: String, reason: String, by: String, actor: String, now: String): CarePlanState {
        val r = s.ectprs.first { it.id == id }
        val records = EctprLifecycle.revoke(s.ectprs, id, reason, by, now)
        return s.copy(ectprs = records, tokens = CardTokens.sync(s.tokens, records, r.patientId, now)).log(now, actor, AuditAction.REVOKE, AuditRecordType.ECTPR, id, reason)
    }

    /** A new card for the active plan, carrying the living will's current status. The old link stops working. */
    fun reissueCard(s: CarePlanState, patientId: String, actor: String, now: String): CarePlanState {
        val active = EctprLifecycle.active(s.ectprs, patientId) ?: error("There is no active plan.")
        return s.copy(tokens = CardTokens.reissue(s.tokens, active, now, AmdLifecycle.statusSummary(s.amds, patientId))).log(now, actor, AuditAction.SHARE, AuditRecordType.CARD, active.id, "ED card replaced for version ${active.version}")
    }

    fun discard(s: CarePlanState, id: String, actor: String, now: String): CarePlanState =
        s.copy(ectprs = EctprLifecycle.discardDraft(s.ectprs, id)).log(now, actor, AuditAction.EDIT, AuditRecordType.ECTPR, id, "unsigned draft discarded")

    /** A review trigger: logged, and a prefilled draft opens (review-due resets). */
    fun requestReview(s: CarePlanState, patientId: String, trigger: ReviewTrigger, actor: String, now: String, today: LocalDate, note: String = ""): Pair<CarePlanState, EctprRecord?> {
        val active = EctprLifecycle.active(s.ectprs, patientId) ?: error("There is no active plan to review.")
        val request = ReviewRequest(CardTokens.random(8), active.id, trigger, now, actor, note)
        val existing = EctprLifecycle.draft(s.ectprs, patientId)
        val draft = if (existing == null) EctprLifecycle.reviewDraft(s.ectprs, active, actor, now, today) else null
        val next = s.copy(reviews = s.reviews + request, ectprs = s.ectprs + listOfNotNull(draft)).log(now, actor, AuditAction.REVIEW, AuditRecordType.ECTPR, active.id, trigger.name)
        return next to (draft ?: existing)
    }

    fun openReview(s: CarePlanState, ectprId: String): ReviewRequest? {
        val r = s.ectprs.firstOrNull { it.id == ectprId } ?: return null
        val signedAt = r.consultantSignature?.signedAt.orEmpty()
        return s.reviews.filter { it.ectprId == ectprId && it.at >= signedAt }.maxByOrNull { it.at }
    }

    fun viewed(s: CarePlanState, type: AuditRecordType, id: String, actor: String, now: String, detail: String? = null) = s.log(now, actor, AuditAction.VIEW, type, id, detail)
    fun printed(s: CarePlanState, type: AuditRecordType, id: String, actor: String, now: String) = s.log(now, actor, AuditAction.PRINT, type, id)
    fun shared(s: CarePlanState, type: AuditRecordType, id: String, actor: String, now: String, detail: String? = null) = s.log(now, actor, AuditAction.SHARE, type, id, detail)
    fun reminded(s: CarePlanState, id: String, actor: String, now: String, status: String) = s.log(now, actor, AuditAction.SHARE, AuditRecordType.ECTPR, id, "Review reminder: $status")

    private fun CarePlanState.withAmd(next: AmdRecord) = copy(amds = amds.map { if (it.id == next.id) next else it })
    fun startAmd(s: CarePlanState, patientId: String, actor: String, now: String, executantName: String = ""): Pair<CarePlanState, AmdRecord> {
        val existing = AmdLifecycle.latest(s.amds, patientId)
        val (records, d) = AmdLifecycle.start(s.amds, patientId, actor, now, executantName)
        if (existing?.id == d.id) return s to d
        return s.copy(amds = records).log(now, actor, AuditAction.CREATE, AuditRecordType.AMD, d.id, "v${d.version}") to d
    }
    fun saveAmd(s: CarePlanState, id: String, content: AmdContent, actor: String, now: String): CarePlanState =
        s.withAmd(AmdLifecycle.update(s.amds.firstOrNull { it.id == id } ?: error("Advance care planning not found."), content, now)).log(now, actor, AuditAction.EDIT, AuditRecordType.AMD, id)
    fun generateAmd(s: CarePlanState, id: String, pdfSha256: String, lang: String, actor: String, now: String, today: LocalDate): CarePlanState =
        s.withAmd(AmdLifecycle.markGenerated(s.amds.first { it.id == id }, pdfSha256, lang, now, today)).log(now, actor, AuditAction.PRINT, AuditRecordType.AMD, id, "document printed, pdf sha256 $pdfSha256")
    fun executeAmd(s: CarePlanState, id: String, execution: AmdExecution, custodian: AmdCustodian?, actor: String, now: String, today: LocalDate): CarePlanState =
        s.copy(amds = AmdLifecycle.execute(s.amds, id, execution, custodian, now, today)).log(now, actor, AuditAction.UPLOAD, AuditRecordType.AMD, id, "signed scan sha256 ${execution.scanSha256}")
    fun amdCopy(s: CarePlanState, id: String, copyId: String, consent: Boolean?, given: Boolean?, method: AmdCopyMethod?, actor: String, now: String, today: LocalDate): CarePlanState {
        val next = AmdLifecycle.updateCopy(s.amds.first { it.id == id }, copyId, consent, given, method, now, today)
        val copy = next.copies.first { it.id == copyId }
        return s.withAmd(next).log(now, actor, AuditAction.SHARE, AuditRecordType.AMD, id, "copy ${copy.kind.name}: consent ${copy.consent}, given ${copy.given}")
    }
    fun amdCustodian(s: CarePlanState, id: String, custodian: AmdCustodian, actor: String, now: String): CarePlanState =
        s.withAmd(AmdLifecycle.updateCustodian(s.amds.first { it.id == id }, custodian, now)).log(now, actor, AuditAction.EDIT, AuditRecordType.AMD, id, "custodian ${custodian.status.name}")
    fun amdAbha(s: CarePlanState, id: String, address: String, consent: Boolean, actor: String, now: String, today: LocalDate): CarePlanState =
        s.withAmd(AmdLifecycle.updateAbha(s.amds.first { it.id == id }, address, consent, now, today)).log(now, actor, AuditAction.EDIT, AuditRecordType.AMD, id, if (address.isBlank()) "ABHA link removed" else "ABHA link saved")
    fun revokeAmd(s: CarePlanState, patientId: String, revokedOn: String, writtenSigned: Boolean, reason: String, actor: String, now: String, today: LocalDate): CarePlanState =
        s.copy(amds = AmdLifecycle.revoke(s.amds, patientId, revokedOn, writtenSigned, reason, now, today)).log(now, actor, AuditAction.REVOKE, AuditRecordType.AMD, patientId, "cancelled in writing on $revokedOn")

    /** "Delete my local draft": unsigned drafts only. Signed versions and the log stay, and the deletion itself is logged. */
    fun deleteDrafts(s: CarePlanState, scope: DpdpScope, patientId: String, actor: String, now: String): Pair<CarePlanState, Int> = when (scope) {
        DpdpScope.CARE_PLAN -> {
            val gone = s.ectprs.filter { it.patientId == patientId && it.status == EctprStatus.DRAFT }
            (if (gone.isEmpty()) s else s.copy(ectprs = s.ectprs - gone.toSet()).log(now, actor, AuditAction.DELETE, AuditRecordType.ECTPR, patientId, "unsigned draft deleted from this phone")) to gone.size
        }
        DpdpScope.LIVING_WILL -> {
            val (kept, gone) = AmdLifecycle.withoutUnsigned(s.amds, patientId)
            (if (gone.isEmpty()) s else s.copy(amds = kept).log(now, actor, AuditAction.DELETE, AuditRecordType.AMD, patientId, "unsigned draft deleted from this phone")) to gone.size
        }
    }
    fun exported(s: CarePlanState, scope: DpdpScope, patientId: String, actor: String, now: String) = s.log(now, actor, AuditAction.SHARE, if (scope == DpdpScope.CARE_PLAN) AuditRecordType.ECTPR else AuditRecordType.AMD, patientId, "Personal data export downloaded")

    fun addEpisode(s: CarePlanState, e: EpisodeLog, actor: String, now: String) = s.copy(episodes = s.episodes + e).log(now, actor, AuditAction.EDIT, AuditRecordType.ECTPR, e.id, "Pilot: ED episode logged")
    fun addOutcome(s: CarePlanState, o: OutcomeRecord, actor: String, now: String) = s.copy(outcomes = s.outcomes + o).log(now, actor, AuditAction.EDIT, AuditRecordType.ECTPR, o.id, "Pilot: outcome recorded")
    fun addIncident(s: CarePlanState, i: IncidentReport, actor: String, now: String) = s.copy(incidents = s.incidents + i).log(now, actor, AuditAction.EDIT, AuditRecordType.ECTPR, i.id, "Pilot: safety incident reported")
}

/** Kept in this phone's private storage the same way as the emergency card (SharedPreferences JSON per account and person). */
internal class CarePlanStore(context: Context, namespace: String) {
    private val safe = namespace.ifBlank { "guest" }.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) }
    private val preferences = context.getSharedPreferences("care-plan-v1-$safe", Context.MODE_PRIVATE)
    private val notices = context.getSharedPreferences("care-plan-notices-v1-$safe", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "care-plans/$safe")
    fun folder(personId: String) = File(root, personId.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) }).also { it.mkdirs() }
    fun people(): List<String> = preferences.all.keys.toList()
    fun load(personId: String): CarePlanState = if (personId.isBlank()) CarePlanState() else preferences.getString(personId, null)?.let { CarePlanState.decode(it) } ?: CarePlanState()
    /** "Remove this person's records" also removes their plan, living will, scans and PDFs from this phone. */
    fun removePerson(personId: String) { preferences.edit().remove(personId).commit(); File(root, personId).deleteRecursively() }
    fun save(personId: String, state: CarePlanState) { check(preferences.edit().putString(personId, state.encode()).commit()) { "The plan could not be saved on this phone." } }
    fun noticeSeen(scope: DpdpScope) = notices.getBoolean(scope.name, false)
    fun markNoticeSeen(scope: DpdpScope) { notices.edit().putBoolean(scope.name, true).apply() }
}

/**
 * The queue for the card server: anything [CardToken.pending] is sent, one at a time, and stays queued when the phone is offline.
 * Runs when the plan screen opens or changes, when the app starts and when the network returns. A repeat is harmless: the server ignores a second issue of a token.
 */
internal object CarePlanCardSync {
    private val mutex = Mutex()
    /** The person whose stored plan just changed, so an open screen reloads it. */
    val changes = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Fills in a card made before the server link, then merges a server answer onto whatever is stored now (the card may have been withdrawn meanwhile). */
    internal fun merge(state: CarePlanState, result: CardToken): CarePlanState = state.copy(tokens = state.tokens.map { t ->
        if (t.token != result.token) t
        else if (!t.live && result.live) t.copy(server = CardServerState.REVOKE_PENDING)
        else result
    })
    internal fun withSnapshots(s: CarePlanState): CarePlanState = s.copy(tokens = s.tokens.map { t ->
        if (t.snapshot.isNotBlank()) t
        else s.ectprs.firstOrNull { it.id == t.ectprId }?.let { t.copy(snapshot = EdCardSnapshot.json(it, AmdLifecycle.statusSummary(s.amds, t.patientId)).toString()) } ?: t
    })

    suspend fun person(store: CarePlanStore, personId: String, transport: EdCardTransport = EdCardHttpTransport()) = mutex.withLock {
        val start = withContext(Dispatchers.IO) { runCatching { withSnapshots(store.load(personId)) }.getOrNull() } ?: return@withLock
        for (card in start.tokens.filter { it.pending }) {
            val result = EdCardApi.push(card, transport)
            if (result == card) continue
            withContext(Dispatchers.IO) { store.save(personId, merge(store.load(personId), result)) }
            changes.tryEmit(personId)
            if (result.serverMessage == EdCardApi.NETWORK) break // Offline: the rest stay queued for when the network returns.
        }
    }

    suspend fun everyone(store: CarePlanStore) { store.people().forEach { runCatching { person(store, it) } } }
}
