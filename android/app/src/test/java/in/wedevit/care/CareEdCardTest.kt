package `in`.wedevit.care

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** The card server link. A fake transport stands in for the network: these tests never reach it. */
class CareEdCardTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val now = "2026-10-29T10:00:00Z"
    private val consultant = Signature(SignatureRole.TREATING_CONSULTANT, "Dr Meera Iyer", "KMC-12345", now)
    private fun plan() = EctprLifecycle.newDraft("p1", "dr", now, EctprPatient(name = "Asha Rao", dob = "1950-01-01", uhid = "UH-1", address = "12 MG Road", phone = "98450 22222"), id = "r1").copy(
        prognosticUnderstanding = PrognosticUnderstanding(true, "Dr Rao", "2026-10-29"), values = PlanValues(patientKnows = true, ownWords = "Home with family"), reviewDue = "2027-01-20", signatures = listOf(consultant),
        clinical = ClinicalDetails(diagnosis = "Stage IV lung cancer"), decisionMakers = DecisionMakers(DecisionMaker("Ravi Rao", "Son", "9845000000")),
        choices = PlanChoices(goalOfCare = GoalOfCare.B_CEILING, cpr = Cpr.DNACPR, dnacprReason = DnacprReason.FUTILITY, dnacprDecidedBy = DnacprDecidedBy("Dr Iyer", "KMC-1", "2026-10-29 10:00"), hospitalTransfer = HospitalTransfer(TransferKind.ONLY_FOR, listOf(TransferReason.PAIN)),
            respCeiling = 4, icu = Icu(IcuKind.TRIAL, TrialPlan(3, DurationUnit.DAYS, "2026-11-01 10:00", listOf("Dr Iyer"))), feeding = Feeding(NgFeeding(NgKind.UP_TO_DAYS, 5), PegFeeding.NO), hydration = Hydration.LIMITED, vasopressors = Vasopressors(VasopressorKind.NO), dialysis = Dialysis(DialysisKind.ONLY_IF_REVERSIBLE)))
    private fun signed(): CarePlanState {
        val d = plan()
        return CarePlanBook.sign(CarePlanState(ectprs = listOf(d)), d.id, "Doctor", now, today)
    }

    private class Fake(val answer: (String) -> Pair<Int, String>) : EdCardTransport {
        val bodies = mutableListOf<JSONObject>(); val paths = mutableListOf<String>()
        override suspend fun post(path: String, body: String): Pair<Int, String> { paths += path; bodies += JSONObject(body); return answer(body) }
    }
    private val issued: (String) -> Pair<Int, String> = { 201 to """{"status":"issued"}""" }
    private val offline: (String) -> Pair<Int, String> = { throw IOException("no network") }

    @Test fun tokenAndManageKeyMatchTheWebFormat() {
        val t = CardTokens.newToken()
        assertEquals(43, t.length); assertTrue(CardTokens.PATTERN.matches(t)); assertNotEquals(t, CardTokens.newToken())
        val card = signed().tokens.single()
        assertTrue(CardTokens.PATTERN.matches(card.manageKey)); assertNotEquals(card.token, card.manageKey)
        assertEquals(CardServerState.LOCAL_ONLY, card.server); assertTrue(card.pending)
        assertEquals("https://sahara.wedevit.in/ed/${card.token}", card.url)
    }

    @Test fun issueSendsTheSameFieldsAsTheWeb() = runBlocking {
        val card = signed().tokens.single(); val fake = Fake(issued)
        val done = EdCardApi.push(card, fake)
        assertEquals(CardServerState.ISSUED, done.server); assertNull(done.serverMessage); assertFalse(done.pending)
        val body = fake.bodies.single()
        assertEquals(ED_CARD_PATH, fake.paths.single())
        assertEquals(setOf("action", "token", "manageKey", "patientRef", "snapshot"), body.keys().asSequence().toSet())
        assertEquals("issue", body.getString("action")); assertEquals(card.token, body.getString("token")); assertEquals(card.manageKey, body.getString("manageKey"))
        // The reference is opaque: not the person's id, nor their name.
        assertNotEquals("p1", body.getString("patientRef")); assertFalse(body.getString("patientRef").contains("Asha")); assertTrue(body.getString("patientRef").length <= 120)
        assertNull(EdCardSnapshot.problem(body.getJSONObject("snapshot")))
    }

    @Test fun offlineStaysQueuedAndRetriesWithTheSameCard() = runBlocking {
        val card = signed().tokens.single()
        val waiting = EdCardApi.push(card, Fake(offline))
        assertEquals(CardServerState.LOCAL_ONLY, waiting.server); assertEquals(EdCardApi.NETWORK, waiting.serverMessage); assertTrue(waiting.pending)
        val fake = Fake(issued); val done = EdCardApi.push(waiting, fake)
        assertEquals(CardServerState.ISSUED, done.server); assertEquals(card.token, fake.bodies.single().getString("token"))
        // Nothing more to send.
        val idle = Fake(issued); EdCardApi.push(done, idle); assertTrue(idle.bodies.isEmpty())
    }

    @Test fun aServerRefusalIsShownHonestly() = runBlocking {
        val card = signed().tokens.single()
        val refused = EdCardApi.push(card, Fake { 503 to """{"status":"not-set-up","message":"Emergency card links are not set up on the server yet."}""" })
        assertEquals(CardServerState.LOCAL_ONLY, refused.server); assertTrue(refused.serverMessage!!.contains("not set up"))
        val garbage = EdCardApi.push(card, Fake { 502 to "<html>" })
        assertEquals(CardServerState.LOCAL_ONLY, garbage.server); assertEquals("The server did not accept the card.", garbage.serverMessage)
    }

    @Test fun withdrawingAPlanRevokesItsCardOnTheServer() = runBlocking {
        var s = signed(); val live = s.tokens.single().copy(server = CardServerState.ISSUED); s = s.copy(tokens = listOf(live))
        s = CarePlanBook.withdraw(s, "r1", "Changed mind about hospital, Asha", "Dr Iyer", "Doctor", now)
        val card = s.tokens.single()
        assertFalse(card.live); assertEquals(CardServerState.REVOKE_PENDING, card.server); assertEquals("Plan cancelled", card.revokedReason)
        val fake = Fake { 200 to """{"status":"revoked","revokedAt":"$now"}""" }
        val done = EdCardApi.push(card, fake)
        assertEquals(CardServerState.REVOKED, done.server); assertFalse(done.pending)
        val body = fake.bodies.single()
        assertEquals(setOf("action", "token", "manageKey", "reason"), body.keys().asSequence().toSet())
        assertEquals("revoke", body.getString("action")); assertEquals(live.manageKey, body.getString("manageKey"))
        // Free text the family typed never goes to the server as the reason.
        assertFalse(body.getString("reason").contains("Asha"))
    }

    @Test fun supersedingRevokesTheOldCardAndIssuesANewOne() {
        val s = signed()
        val (s2, draft) = CarePlanBook.requestReview(s, "p1", ReviewTrigger.ROUTINE_INTERVAL, "Doctor", now, today)
        val s3 = CarePlanBook.sign(CarePlanBook.saveDraft(s2, draft!!.copy(signatures = listOf(consultant), prognosticUnderstanding = draft.prognosticUnderstanding.copy(confirmed = true)), "Doctor", now, today), draft.id, "Doctor", now, today)
        assertEquals(2, s3.tokens.size)
        val old = s3.tokens.first { it.ectprId == "r1" }; val fresh = s3.tokens.first { it.ectprId == draft.id }
        assertEquals(CardServerState.REVOKE_PENDING, old.server); assertEquals("Plan superseded by a newer version", old.revokedReason)
        assertTrue(fresh.live); assertEquals(2, fresh.version); assertEquals(CardServerState.LOCAL_ONLY, fresh.server)
        assertTrue(s3.tokens.all { it.pending })
    }

    @Test fun revokeTreatsUnknownAsDoneAndKeepsPendingWhenOffline() = runBlocking {
        val card = signed().tokens.single().copy(revokedAt = now, server = CardServerState.REVOKE_PENDING)
        assertEquals(CardServerState.REVOKED, EdCardApi.push(card, Fake { 404 to """{"status":"unknown"}""" }).server)
        assertEquals(CardServerState.REVOKE_PENDING, EdCardApi.push(card, Fake(offline)).server)
        assertEquals(CardServerState.REVOKE_PENDING, EdCardApi.push(card, Fake { 403 to """{"status":"error","message":"Only the device that issued this card can withdraw it."}""" }).server)
    }

    @Test fun aCardWithdrawnWhileUploadingStaysWithdrawn() {
        val card = signed().tokens.single()
        val state = CarePlanState(tokens = listOf(card.copy(revokedAt = now, server = CardServerState.REVOKE_PENDING)))
        val merged = CarePlanCardSync.merge(state, card.copy(server = CardServerState.ISSUED)).tokens.single()
        assertFalse(merged.live); assertEquals(CardServerState.REVOKE_PENDING, merged.server)
    }

    @Test fun cardsMadeBeforeTheServerLinkAreUpgraded() {
        val old = JSONObject("""{"token":"${"a".repeat(32)}","ectprId":"r1","patientId":"p1","createdAt":"$now","revokedAt":null}""")
        val t = PlanJson.token(old)
        assertTrue(CardTokens.PATTERN.matches(t.manageKey)); assertEquals(CardServerState.LOCAL_ONLY, t.server); assertTrue(t.pending)
        assertEquals(CardServerState.REVOKE_PENDING, PlanJson.token(JSONObject(old.toString()).put("revokedAt", now)).server)
        val s = signed()
        assertTrue(CarePlanCardSync.withSnapshots(s.copy(tokens = listOf(t))).tokens.single().snapshot.contains("\"uhid\":\"UH-1\""))
        assertEquals(t.copy(snapshot = "x"), PlanJson.token(JSONObject(PlanJson.encode(t.copy(snapshot = "x")).toString())))
    }

    @Test fun issuableProblemMatchesTheWebRule() {
        val good = EdCardSnapshot.json(signed().ectprs.single(), null)
        assertNull(EdCardSnapshot.problem(good))
        assertEquals("No care plan was sent.", EdCardSnapshot.problem(null))
        assertTrue(EdCardSnapshot.problem(JSONObject(good.toString()).put("status", "DRAFT"))!!.contains("Only the Active"))
        assertTrue(EdCardSnapshot.problem(JSONObject(good.toString()).put("signatures", JSONArray()))!!.contains("registration number"))
        assertTrue(EdCardSnapshot.problem(JSONObject(good.toString()).put("reviewDue", ""))!!.contains("UHID or review date"))
        val noUhid = JSONObject(good.toString()); noUhid.getJSONObject("patient").put("uhid", "")
        assertTrue(EdCardSnapshot.problem(noUhid)!!.contains("UHID or review date"))
    }

    @Test fun aPlanWithoutAUhidCannotBeSigned() {
        val d = plan().copy(patient = plan().patient.copy(uhid = ""))
        assertTrue("V8" in EctprRules.activationBlockers(d, today).map { it.id })
        assertFalse("V8" in EctprRules.saveBlockers(d, today).map { it.id })
    }

    @Test fun snapshotCarriesTheLivingWillStatusNotTheDocument() {
        val amd = AmdStatusSummary(AmdStatus.EXECUTED, "2026-10-01", 2, "Notary: R. Shah")
        val j = EdCardSnapshot.json(signed().ectprs.single(), amd).getJSONObject("amdAtIssue")
        assertEquals("EXECUTED", j.getString("status")); assertEquals(2, j.getInt("version")); assertEquals("Notary: R. Shah", j.getString("attestor"))
        assertTrue(EdCardSnapshot.json(signed().ectprs.single(), null).isNull("amdAtIssue"))
    }

    // ── The snapshot is read by the web: its field names and enums must be the web model's. ──
    private val modelDir = listOf("../prototype/app", "../../prototype/app").map(::File).firstOrNull { it.isDirectory }

    /** Parses a TypeScript object type into its key paths. Optional keys are marked by `?`. */
    private fun tsKeys(body: String, prefix: String, out: MutableMap<String, Boolean>) {
        var i = 0
        while (i < body.length) {
            val m = Regex("\\s*([A-Za-z0-9_]+)(\\?)?\\s*:\\s*").matchAt(body, i) ?: run { i++; return@run null } ?: continue
            val key = prefix + m.groupValues[1]; out[key] = m.groupValues[2] == "?"
            i = m.range.last + 1
            if (body[i] == '{') {
                var depth = 0; var j = i
                do { if (body[j] == '{') depth++ else if (body[j] == '}') depth--; j++ } while (depth > 0)
                tsKeys(body.substring(i + 1, j - 1), "$key.", out); i = j
            } else {
                var depth = 0
                while (i < body.length) { val c = body[i]; if (c == '{' || c == '[' || c == '(') depth++; if (c == '}' || c == ']' || c == ')') depth--; if (c == ';' && depth == 0) break; i++ }
            }
            while (i < body.length && (body[i] == ';' || body[i].isWhitespace())) i++
        }
    }

    private fun model(): String = File(modelDir, "ectpr-model.ts").readText().replace(Regex("/\\*\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")
    private fun tsType(source: String, name: String): String {
        val start = source.indexOf("export type $name = {"); assertTrue(name, start >= 0)
        var i = source.indexOf('{', start); val open = i; var depth = 0
        do { if (source[i] == '{') depth++ else if (source[i] == '}') depth--; i++ } while (depth > 0)
        return source.substring(open + 1, i - 1)
    }
    private fun jsonKeys(j: JSONObject, prefix: String, out: MutableSet<String>) {
        j.keys().forEach { k -> out += prefix + k; (j.opt(k) as? JSONObject)?.let { jsonKeys(it, "$prefix$k.", out) } }
    }
    private fun tsUnion(source: String, name: String): Set<String> {
        val body = Regex("export type $name\\s*=\\s*(.+?);\\s*\\n", RegexOption.DOT_MATCHES_ALL).find(source)!!.groupValues[1]
        return Regex("'([A-Z0-9_]+)'").findAll(body).map { it.groupValues[1] }.toSet()
    }

    @Test fun snapshotFieldNamesAreTheWebModels() {
        if (modelDir == null) return
        val source = model(); val ts = linkedMapOf<String, Boolean>(); tsKeys(tsType(source, "EctprRecord"), "", ts)
        val amdSummary = linkedMapOf<String, Boolean>(); tsKeys(tsType(source, "AmdStatusSummary"), "amdAtIssue.", amdSummary); ts += amdSummary
        val dm = linkedMapOf<String, Boolean>(); tsKeys(tsType(source, "DecisionMaker"), "", dm)
        dm.keys.forEach { ts["decisionMakers.primary.$it"] = false; ts["decisionMakers.alternate.$it"] = true }
        val home = linkedMapOf<String, Boolean>(); tsKeys(tsType(source, "EctprHomeCare"), "", home); home.keys.forEach { ts["homeCare.$it"] = true }
        val boards = linkedMapOf<String, Boolean>(); tsKeys(tsType(source, "EctprWithdrawal"), "", boards); boards.keys.forEach { ts["withdrawal.$it"] = true }
        val full = signed().ectprs.single().copy(homeCare = EctprHomeCare(coordinator = "Sister Joy"), withdrawal = EctprWithdrawal(applicable = true))
        val snapshot = EdCardSnapshot.json(full, AmdStatusSummary(AmdStatus.EXECUTED, "2026-10-01", 1, "Notary: R"))
        val keys = linkedSetOf<String>(); jsonKeys(snapshot, "", keys)
        // The legal review's fields go to the server under the web's names.
        listOf("patient.gender", "decisionMakers.presentAtDiscussion", "decisionMakers.attemptedContact", "values.knowsRecordedWhen", "homeCare.coordinator", "withdrawal.finalDecision").forEach { assertTrue(it, it in keys) }
        // Union members (hospitalTransfer, icu…) carry their own `kind` shape, checked against the model's unions below.
        val unionFields = listOf("choices.hospitalTransfer.", "choices.icu.", "choices.vasopressors.", "choices.dialysis.", "choices.feeding.ng.", "existingDirective.", "choices.conflictReasons.")
        val extra = keys.filter { k -> k !in ts && unionFields.none { k.startsWith(it) } }
        // The one Android-only field. The route stores the JSON as is, and the live API accepted it; the web ignores it.
        assertEquals(listOf("choices.nivTrial"), extra)
        val missing = ts.filter { (k, optional) -> !optional && k !in keys && !k.startsWith("decisionMakers.alternate") }.keys
        assertTrue("Missing from the snapshot: $missing", missing.isEmpty())
    }

    @Test fun snapshotEnumsAreTheWebModels() {
        if (modelDir == null) return
        val s = model()
        assertEquals(tsUnion(s, "GoalOfCare"), GoalOfCare.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "Cpr"), Cpr.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "DnacprReason"), DnacprReason.entries.map { it.name }.toSet())
        assertEquals(tsUnion(s, "TransferReason"), TransferReason.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "PegFeeding"), PegFeeding.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "Hydration"), Hydration.entries.map { it.name }.toSet())
        assertEquals(tsUnion(s, "AmbulanceOption"), AmbulanceOption.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "Capacity"), Capacity.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "AuthorityBasis"), AuthorityBasis.entries.map { it.name }.toSet())
        assertEquals(tsUnion(s, "IdType"), IdType.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "ImplantedDevice"), ImplantedDevice.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "EctprStatus"), EctprStatus.entries.map { it.name }.toSet())
        assertEquals(tsUnion(s, "HospitalTransfer"), TransferKind.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "Icu"), IcuKind.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "Vasopressors"), VasopressorKind.entries.map { it.name }.toSet())
        assertEquals(tsUnion(s, "Dialysis"), DialysisKind.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "NgFeeding"), NgKind.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "AmdStatus"), AmdStatus.entries.map { it.name }.toSet())
        assertEquals(tsUnion(s, "AmdCondition"), AmdCondition.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "AmdTreatment"), AmdTreatment.entries.map { it.name }.toSet()); assertEquals(tsUnion(s, "AmdPlaceOfCare"), AmdPlaceOfCare.entries.map { it.name }.toSet())
        val roles = Regex("role:\\s*([^;]+);").find(s)!!.groupValues[1]; assertEquals(Regex("'([A-Z_]+)'").findAll(roles).map { it.groupValues[1] }.toSet(), SignatureRole.entries.map { it.name }.toSet())
        assertEquals(tsUnion(s, "WithdrawalFinal"), WithdrawalFinal.entries.map { it.name }.toSet())
        val ranks = Regex("surrogateRank\\?:\\s*([^;}]+)").find(s)!!.groupValues[1]; assertEquals(Regex("'([A-Z_]+)'").findAll(ranks).map { it.groupValues[1] }.toSet(), SurrogateRank.entries.map { it.name }.toSet())
        val knows = Regex("knowsRecordedWhen\\?:\\s*([^;}]+)").find(s)!!.groupValues[1]; assertEquals(Regex("'([A-Z_]+)'").findAll(knows).map { it.groupValues[1] }.toSet(), KnowsRecordedWhen.entries.map { it.name }.toSet())
        // The values in an actual snapshot.
        val snap = EdCardSnapshot.json(signed().ectprs.single(), null).getJSONObject("choices")
        assertEquals("ONLY_FOR", snap.getJSONObject("hospitalTransfer").getString("kind")); assertEquals("PAIN", snap.getJSONObject("hospitalTransfer").getJSONArray("reasons").getString(0))
        assertEquals("TRIAL", snap.getJSONObject("icu").getString("kind")); assertEquals("DAYS", snap.getJSONObject("icu").getJSONObject("trial").getString("durationUnit"))
        assertEquals(5, snap.getJSONObject("feeding").getJSONObject("ng").getInt("days")); assertTrue(snap.getJSONObject("feeding").getBoolean("comfortOral"))
    }

    @Test fun alwaysProvidedCareIsExactlyTheWebList() {
        assertEquals(12, CareThatContinues.ITEMS.size) // Sahaara v1.0 §16
        if (modelDir == null) return
        val source = File(modelDir, "ectpr-rules.ts").readText()
        val block = source.substring(source.indexOf("ALWAYS_PROVIDED_CARE"), source.indexOf("];", source.indexOf("ALWAYS_PROVIDED_CARE")))
        assertEquals(Regex("'([^']+)'").findAll(block).map { it.groupValues[1] }.toList(), CareThatContinues.ITEMS.map { it.en })
        val labels = File(modelDir, "ectpr-labels.ts").readText()
        CareThatContinues.ITEMS.forEach { assertTrue(it.hi, labels.contains("'${it.en}': '${it.hi}'")) }
    }

    // ── Offline QR ──
    @Test fun offlineTextIsTheWebSummaryAndFitsAQr() {
        val r = signed().ectprs.single(); val text = EdCardOffline.text(r, null, false, today)
        listOf("SAHARA ECTPR v1 · Sahara ECTPR v1.0", "Signed emergency care plan", "Asha Rao · UHID UH-1", "Diagnosis: Stage IV lung cancer", "Transfer: Only for the reasons ticked", "CPR: Do not attempt CPR (DNACPR)", "ICU: Time-limited ICU trial", "Decision-maker: Ravi Rao (Son) 9845000000", "Signed: Dr Meera Iyer 2026-10-29", WalletCard.TREAT_LINE.en).forEach { assertTrue(it, text.contains(it)) }
        assertFalse("address stays off the card", text.contains("12 MG Road"))
        assertTrue(EdCardOffline.forQr(r, null, false, today).toByteArray().size <= 1100)
        // Unanswered is never read as refusal.
        val blank = r.copy(choices = PlanChoices())
        assertTrue(EdCardOffline.text(blank, null, false, today).contains("CPR: No preference recorded — standard care"))
        val hindi = EdCardOffline.text(r, null, true, today); assertTrue(HindiScan.hasHindi(hindi))
    }

    @Test fun offlineTextCarriesTheLivingWillWithItsCaveat() {
        val text = EdCardOffline.text(signed().ectprs.single(), AmdStatusSummary(AmdStatus.EXECUTED, "2026-10-01", 2, "Notary: R. Shah"), false, today)
        assertTrue(text.contains("The AMD governs over this plan.")); assertTrue(text.contains(AmdText.CAVEAT.en))
    }

    @Test fun anOutOfDatePlanSaysSoOnTheOfflineQr() {
        val r = signed().ectprs.single().copy(reviewDue = "2026-01-01")
        assertTrue(EdCardOffline.text(r, null, false, today).contains("OUT OF DATE"))
    }

    @Test fun aVeryLongHindiSummaryFallsBackToEnglishForTheQr() {
        val r = signed().ectprs.single().copy(patient = plan().patient.copy(name = "अ".repeat(400)))
        val qr = EdCardOffline.forQr(r, null, true, today)
        assertTrue(qr.contains("Signed emergency care plan"))
    }

    // ── Severities ──
    @Test fun c1AndC3WaitUntilSigningAsOnTheWeb() {
        val c1 = plan().let { it.copy(choices = it.choices.copy(hospitalTransfer = HospitalTransfer(TransferKind.NO), respCeiling = 4, icu = null)) }
        assertEquals(IssueSeverity.CONFIRM, EctprRules.check(c1, today).first { it.id == "C1" }.severity)
        assertFalse("C1" in EctprRules.saveBlockers(c1, today).map { it.id }); assertTrue("C1" in EctprRules.activationBlockers(c1, today).map { it.id })
        val homeNiv = c1.copy(choices = c1.choices.copy(ceilingConditions = CeilingConditions(homeNivReason = "Home NIV")))
        assertFalse("C1" in EctprRules.check(homeNiv, today).map { it.id })
        val c3 = plan().let { it.copy(choices = it.choices.copy(respCeiling = 5, icu = Icu(IcuKind.NO))) }
        assertEquals(IssueSeverity.CONFIRM, EctprRules.check(c3, today).first { it.id == "C3" }.severity)
        assertFalse("C3" in EctprRules.saveBlockers(c3, today).map { it.id }); assertTrue("C3" in EctprRules.activationBlockers(c3, today).map { it.id })
    }

    // ── SMS ──
    @Test fun reminderIsOfferedInTheWindowAndUsesAValidMobile() {
        val r = signed().ectprs.single()
        assertFalse(ReviewReminder.due(r, today)); assertTrue(ReviewReminder.due(r.copy(reviewDue = "2026-11-10"), today)); assertTrue(ReviewReminder.due(r.copy(reviewDue = "2026-10-01"), today))
        assertFalse(ReviewReminder.due(r.copy(status = EctprStatus.DRAFT, reviewDue = "2026-10-30"), today))
        assertEquals("+919845000000", ReviewReminder.mobile("098450 00000")); assertEquals("+919845000000", ReviewReminder.mobile("+91 98450 00000")); assertNull(ReviewReminder.mobile("12345")); assertNull(ReviewReminder.mobile(null))
        assertEquals("Ravi Rao" to "+919845000000", ReviewReminder.recipient(r))
        assertEquals("Asha Rao" to "+919845022222", ReviewReminder.recipient(r.copy(decisionMakers = DecisionMakers())))
        assertNull(ReviewReminder.recipient(r.copy(decisionMakers = DecisionMakers(), patient = r.patient.copy(phone = ""))))
    }

    @Test fun smsStatusIsHonest() = runBlocking {
        val r = signed().ectprs.single()
        assertEquals(false, ReviewReminder.configured(Fake { 200 to """{"status":"ok","configured":false}""" }))
        assertEquals(true, ReviewReminder.configured(Fake { 200 to """{"status":"ok","configured":true}""" }))
        assertNull(ReviewReminder.configured(Fake(offline)))
        val fake = Fake { 200 to """{"status":"not-configured","sent":false}""" }
        assertEquals("not-configured", ReviewReminder.send(r, "+919845000000", fake))
        assertEquals(REMINDER_PATH, fake.paths.single())
        val body = fake.bodies.single()
        assertEquals(setOf("action", "phone", "reviewDue", "patientFirstName"), body.keys().asSequence().toSet()); assertEquals("send", body.getString("action")); assertEquals("Asha", body.getString("patientFirstName")); assertEquals("2027-01-20", body.getString("reviewDue"))
        assertEquals("sent", ReviewReminder.send(r, "+919845000000", Fake { 200 to """{"status":"sent","sent":true}""" }))
        assertEquals("too-many", ReviewReminder.send(r, "+919845000000", Fake { 429 to """{"status":"too-many","sent":false}""" }))
        assertEquals("network", ReviewReminder.send(r, "+919845000000", Fake(offline)))
    }

    // ── Pilot ──
    @Test fun pilotIndicatorsMatchTheWeb() {
        val r = signed().ectprs.single().copy(clinical = plan().clinical.copy(eligibilityDate = "2026-10-20"))
        assertEquals(9L, Pilot.reachDays(r)); assertNull(Pilot.reachDays(r.copy(clinical = plan().clinical)))
        val v = Pilot.valuesIndicator(r); assertTrue(v.valuesFilled); assertEquals(11, v.treatmentTotal); assertFalse(v.limitsWithoutValues)
        assertTrue(Pilot.valuesIndicator(r.copy(values = PlanValues())).limitsWithoutValues)
        val e = EpisodeLog("e1", "p1", null, "2026-10-30T10:00:00Z", "2026-10-30T10:08:00Z", 5, true, null, null, false)
        assertEquals(8L, Pilot.retrievalMinutes(e))
        val row = Pilot.row(e, listOf(r)); assertEquals(true, row.withinTarget); assertEquals(false, row.concordant)
        assertEquals(listOf(EpisodeProblem.REASON_NEEDED), Pilot.episodeProblems(e, r)); assertTrue(Pilot.episodeProblems(e.copy(discordanceReason = "Family asked"), r).isEmpty())
        assertEquals(listOf(EpisodeProblem.ACCESS_BEFORE_ARRIVAL), Pilot.episodeProblems(e.copy(firstRecordAccessAt = "2026-10-30T09:00:00Z", discordanceReason = "x"), r))
        assertEquals(listOf(EpisodeProblem.NO_ARRIVAL), Pilot.episodeProblems(e.copy(edArrivalAt = ""), null))
        assertEquals(PilotSummary(1, 1, 1, 1, 0), Pilot.summary(listOf(row)))
        assertTrue(Pilot.row(e.copy(highestSupportDelivered = 3, cprGiven = false), listOf(r)).concordant == true)
        // The plan in force is the newest signature at that moment; a cancelled one is not in force.
        assertEquals("r1", Pilot.planInForceAt(listOf(r), "2026-10-30T10:00:00Z")?.id); assertNull(Pilot.planInForceAt(listOf(r), "2026-10-01T10:00:00Z"))
        assertNull(Pilot.planInForceAt(listOf(r.copy(revokedAt = "2026-10-29T12:00:00Z")), "2026-10-30T10:00:00Z"))
        val o = OutcomeRecord("o1", "p1", PilotPlace.HOME, PilotPlace.HOME, 2, 40, "2026-10-01T10:00:00Z", "2026-10-01T13:30:00Z", -5)
        assertEquals(true, Pilot.placeMatches(o)); assertEquals(3.5, Pilot.hoursToSymptomRx(o)); assertEquals(setOf(OutcomeProblem.DAYS_OUT_OF_RANGE, OutcomeProblem.NEGATIVE_SPEND), Pilot.outcomeProblems(o).toSet())
        assertEquals(listOf(OutcomeProblem.NOTHING_ENTERED), Pilot.outcomeProblems(OutcomeRecord("o", "p", null, null, null, null, null, null, null)))
        assertTrue(Pilot.incidentEmpty(IncidentReport("i", "p", null, false, false, " "))); assertFalse(Pilot.incidentEmpty(IncidentReport("i", "p", null, true, false, "")))
        val log = AuditLog.EMPTY.append("2026-10-30T09:00:00Z", "Doctor", AuditAction.VIEW, AuditRecordType.ECTPR, "r1", "ED Quick View").append("2026-10-30T10:05:00Z", "Family", AuditAction.VIEW, AuditRecordType.CARD, "r1", "wallet card").append("2026-10-30T10:06:00Z", "Doctor", AuditAction.VIEW, AuditRecordType.ECTPR, "r1", "ED Quick View")
        assertEquals("2026-10-30T10:06:00Z", Pilot.firstEdAccessAfter(log.events, "2026-10-30T10:00:00Z", "ED Quick View"))
    }

    @Test fun stateRoundTripWithPilotAndCards() {
        val s = signed().copy(episodes = listOf(EpisodeLog("e1", "p1", "r1", "2026-10-30T10:00:00Z", null, 3, null, null, null, true, "x", "Doctor", now)), outcomes = listOf(OutcomeRecord("o1", "p1", PilotPlace.WARD, null, 1, null, null, null, 500, "Doctor", now)), incidents = listOf(IncidentReport("i1", "p1", "r1", true, false, "note", "Doctor", now)))
        val back = CarePlanState.decode(s.encode())
        assertEquals(s.ectprs, back.ectprs); assertEquals(s.tokens, back.tokens); assertEquals(s.episodes, back.episodes); assertEquals(s.outcomes, back.outcomes); assertEquals(s.incidents, back.incidents); assertEquals(s.audit.events, back.audit.events)
    }

    // ── DPDP ──
    @Test fun noticeSaysWhatTheCodeDoesAndHasHindi() {
        DpdpScope.entries.forEach { scope -> val blocks = Dpdp.notice(scope); assertEquals(5, blocks.size); blocks.forEach { assertTrue(it.body.en, HindiScan.hasHindi(it.body.hi)); assertTrue(it.title.en, HindiScan.hasHindi(it.title.hi)) } }
        assertTrue(Dpdp.notice(DpdpScope.CARE_PLAN)[2].body.en.contains("goes to the Sahara server"))
        assertTrue(Dpdp.notice(DpdpScope.LIVING_WILL)[2].body.en.contains("not sent to any server"))
        Dpdp.notice(DpdpScope.CARE_PLAN).forEach { assertFalse(it.body.en, it.body.en.contains("browser")) }
    }

    @Test fun exportLeavesOutCardCredentials() {
        val s = signed(); val card = s.tokens.single()
        val text = Dpdp.export(s, "p1", java.time.Instant.parse(now)).toString()
        assertFalse(text.contains(card.token)); assertFalse(text.contains(card.manageKey)); assertTrue(text.contains("Sahara personal data export")); assertTrue(text.contains("Asha Rao"))
        assertEquals("local-only", Dpdp.export(s, "p1", java.time.Instant.parse(now)).getJSONArray("cards").getJSONObject(0).getString("server"))
        assertEquals("sahara-my-data-p1-2026-10-29.json", Dpdp.fileName("p1", today))
    }

    @Test fun deletingDraftsKeepsSignedVersionsAndLogsIt() {
        val s = signed(); val (withDraft, _) = CarePlanBook.requestReview(s, "p1", ReviewTrigger.ROUTINE_INTERVAL, "Doctor", now, today)
        val (after, gone) = CarePlanBook.deleteDrafts(withDraft, DpdpScope.CARE_PLAN, "p1", "Doctor", now)
        assertEquals(1, gone); assertEquals(listOf(EctprStatus.ACTIVE), after.ectprs.map { it.status }); assertEquals(AuditAction.DELETE, after.audit.events.last().action); assertTrue(after.audit.verify())
        assertEquals(0, CarePlanBook.deleteDrafts(after, DpdpScope.CARE_PLAN, "p1", "Doctor", now).second)
    }

    // ── Read aloud ──
    @Test fun readAloudSaysNoPreferenceNeverRefusal() {
        val blank = signed().ectprs.single().copy(choices = PlanChoices())
        val step = PlanSpeech.wizardStep(blank, 5, false)
        assertTrue(step.contains(NO_PREFERENCE.en)); assertFalse(step.contains("refused"))
        (0..9).forEach { assertTrue("step $it", PlanSpeech.wizardStep(blank, it, false).isNotBlank()); assertTrue("step $it hindi", HindiScan.hasHindi(PlanSpeech.wizardStep(blank, it, true))) }
        val ed = PlanSpeech.edView(EdView.interpret(blank, today), AmdLifecycle.edSummary(emptyList(), "p1"), false)
        assertTrue(ed.contains(IfInDoubt.TITLE.en)); assertTrue(ed.contains(CareThatContinues.ITEMS.first().en)); assertTrue(ed.contains(AmdText.CAVEAT.en))
        val will = PlanSpeech.livingWill(AmdContent(treatments = mapOf(AmdTreatment.CPR to AmdChoice.REFUSE)), false)
        assertTrue(will.contains(AmdText.NOT_DECIDED.en)); assertTrue(will.contains(AmdText.COMFORT.en))
    }

    @Test fun composedLinesKeepEveryLanguage() {
        val r = signed().ectprs.single()
        val rows = EdView.rows(r, AmdLifecycle.edSummary(emptyList(), "p1")).associateBy { it.key }
        assertTrue(rows.getValue("TRANSFER").value.en.contains("Uncontrolled pain")); assertTrue(rows.getValue("TRANSFER").value.hi.contains("दर्द"))
        assertEquals(rows.getValue("GOAL").value.en, rows.getValue("GOAL").value.local)
    }
}
