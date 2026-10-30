package `in`.wedevit.care

import android.content.Context
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
) {
    fun encode(): String = JSONObject().put("v", 1)
        .put("ectprs", JSONArray(ectprs.map(PlanJson::encode))).put("amds", JSONArray(amds.map(AmdJson::encode)))
        .put("tokens", JSONArray(tokens.map(PlanJson::encode))).put("reviews", JSONArray(reviews.map(PlanJson::encode)))
        .put("audit", JSONArray(audit.events.map(PlanJson::encode))).toString()
    companion object {
        fun decode(json: String): CarePlanState {
            val j = JSONObject(json)
            return CarePlanState(PlanJson.objects(j.optJSONArray("ectprs")).map(PlanJson::decode), PlanJson.objects(j.optJSONArray("amds")).map(AmdJson::decode),
                PlanJson.objects(j.optJSONArray("tokens")).map(PlanJson::token), PlanJson.objects(j.optJSONArray("reviews")).map(PlanJson::review),
                AuditLog.of(PlanJson.objects(j.optJSONArray("audit")).map(PlanJson::audit)))
        }
    }
}

/**
 * Every change to the plan or the living will goes through here, so each one is checked by the rules and lands in the
 * append-only audit log. Pure: the screens keep the returned state with [CarePlanStore].
 */
object CarePlanBook {
    private fun CarePlanState.log(at: String, actor: String, action: AuditAction, type: AuditRecordType, id: String, detail: String? = null) = copy(audit = audit.append(at, actor, action, type, id, detail))

    fun startPlan(s: CarePlanState, patientId: String, actor: String, now: String, patient: EctprPatient, createdInEd: Boolean = false): Pair<CarePlanState, EctprRecord> {
        require(EctprLifecycle.draft(s.ectprs, patientId) == null) { "A draft is already open." }
        val d = EctprLifecycle.newDraft(patientId, actor, now, patient, createdInEd)
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
        var next = s.copy(ectprs = records, tokens = CardTokens.sync(s.tokens, records, r.patientId, now))
            .log(now, actor, AuditAction.SIGN, AuditRecordType.ECTPR, id, "active v${r.version}")
        superseded.forEach { next = next.log(now, actor, AuditAction.REVOKE, AuditRecordType.CARD, it, "superseded by v${r.version}; card tokens revoked") }
        return next
    }

    fun withdraw(s: CarePlanState, id: String, reason: String, by: String, actor: String, now: String): CarePlanState {
        val r = s.ectprs.first { it.id == id }
        val records = EctprLifecycle.revoke(s.ectprs, id, reason, by, now)
        return s.copy(ectprs = records, tokens = CardTokens.sync(s.tokens, records, r.patientId, now)).log(now, actor, AuditAction.REVOKE, AuditRecordType.ECTPR, id, reason)
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
    fun shared(s: CarePlanState, type: AuditRecordType, id: String, actor: String, now: String) = s.log(now, actor, AuditAction.SHARE, type, id)

    fun startAmd(s: CarePlanState, patientId: String, actor: String, now: String, maker: AmdMaker): Pair<CarePlanState, AmdRecord> {
        AmdLifecycle.working(s.amds, patientId)?.let { return s to it }
        val current = AmdLifecycle.current(s.amds, patientId)
        val d = if (current != null) AmdLifecycle.newVersion(s.amds, current, now) else AmdLifecycle.newDraft(patientId, now, maker)
        return s.copy(amds = s.amds + d).log(now, actor, AuditAction.CREATE, AuditRecordType.AMD, d.id, "v${d.version}") to d
    }
    fun saveAmd(s: CarePlanState, record: AmdRecord, actor: String, now: String): CarePlanState {
        val old = s.amds.firstOrNull { it.id == record.id } ?: error("Living will not found.")
        val next = AmdLifecycle.edit(old, now) { record }
        return s.copy(amds = s.amds.map { if (it.id == record.id) next else it }).log(now, actor, AuditAction.EDIT, AuditRecordType.AMD, record.id)
    }
    fun generateAmd(s: CarePlanState, id: String, actor: String, now: String, today: LocalDate): CarePlanState {
        val next = AmdLifecycle.generate(s.amds.first { it.id == id }, now, today)
        return s.copy(amds = s.amds.map { if (it.id == id) next else it }).log(now, actor, AuditAction.PRINT, AuditRecordType.AMD, id, "document generated")
    }
    fun executeAmd(s: CarePlanState, id: String, scan: AmdScan, witnesses: List<AmdWitness>, attestor: AmdAttestor, actor: String, now: String): CarePlanState =
        s.copy(amds = AmdLifecycle.execute(s.amds, id, scan, witnesses, attestor, now)).log(now, actor, AuditAction.UPLOAD, AuditRecordType.AMD, id, "signed scan sha256 ${scan.sha256}")
    fun revokeAmd(s: CarePlanState, id: String, reason: String, actor: String, now: String): CarePlanState =
        s.copy(amds = AmdLifecycle.revoke(s.amds, id, reason, now)).log(now, actor, AuditAction.REVOKE, AuditRecordType.AMD, id, reason)
}

/** Kept in this phone's private storage the same way as the emergency card (SharedPreferences JSON per account and person). */
internal class CarePlanStore(context: Context, namespace: String) {
    private val safe = namespace.ifBlank { "guest" }.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) }
    private val preferences = context.getSharedPreferences("care-plan-v1-$safe", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "care-plans/$safe")
    fun folder(personId: String) = File(root, personId.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) }).also { it.mkdirs() }
    fun load(personId: String): CarePlanState = if (personId.isBlank()) CarePlanState() else preferences.getString(personId, null)?.let { CarePlanState.decode(it) } ?: CarePlanState()
    /** "Remove this person's records" also removes their plan, living will, scans and PDFs from this phone. */
    fun removePerson(personId: String) { preferences.edit().remove(personId).commit(); File(root, personId).deleteRecursively() }
    fun save(personId: String, state: CarePlanState) { check(preferences.edit().putString(personId, state.encode()).commit()) { "The plan could not be saved on this phone." } }
}
