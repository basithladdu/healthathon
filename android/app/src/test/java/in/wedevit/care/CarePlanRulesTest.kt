package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CarePlanRulesTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val now = "2026-10-29T10:00:00Z"
    private fun base() = EctprLifecycle.newDraft("p1", "dr", now, EctprPatient(name = "Asha Rao", dob = "1950-01-01", uhid = "UH-1"), id = "r1")
    private fun understood(r: EctprRecord = base()) = r.copy(prognosticUnderstanding = PrognosticUnderstanding(true, "Dr Rao", "2026-10-29"), values = PlanValues(patientKnows = true, ownWords = "Home with family"))
    private fun choose(r: EctprRecord = understood(), change: (PlanChoices) -> PlanChoices) = r.copy(choices = change(r.choices))
    private fun ids(r: EctprRecord) = EctprRules.check(r, today).map { it.id }
    private fun saveIds(r: EctprRecord) = EctprRules.saveBlockers(r, today).map { it.id }
    private fun signIds(r: EctprRecord) = EctprRules.activationBlockers(r, today).map { it.id }
    private val consultant = Signature(SignatureRole.TREATING_CONSULTANT, "Dr Meera Iyer", "KMC-12345", now)
    private fun signable(r: EctprRecord = understood()) = r.copy(reviewDue = "2027-01-20", signatures = listOf(consultant))
    private val trial = TrialPlan(3, DurationUnit.DAYS, "2026-11-01 10:00", listOf("Dr Iyer"))

    // V1
    @Test fun v1_allBlankReadsNoPreferenceEverywhereAndStandardCare() {
        val blank = base()
        assertFalse(blank.choices.anyChosen)
        assertTrue(saveIds(blank).isEmpty())
        val active = EctprLifecycle.activate(listOf(signable(blank)), "r1", now, today).single()
        val rows = EdView.rows(active, AmdLifecycle.edSummary(emptyList(), "p1")).associateBy { it.key }
        listOf("TRANSFER", "CPR", "RESP_CEILING", "ICU", "GOAL").forEach { assertEquals(it, NO_PREFERENCE, rows.getValue(it).value) }
        assertTrue(NO_PREFERENCE.en.contains("standard care"))
        // Card lines too.
        assertEquals(NO_PREFERENCE, WalletCard.lines(active).first { it.first.en == "CPR" }.second)
        // Nothing is pre-selected in a new record.
        assertNull(blank.choices.goalOfCare); assertNull(blank.choices.cpr); assertNull(blank.choices.respCeiling); assertNull(blank.choices.hydration)
    }

    // V2 + acceptance "Goal C + Attempt CPR cannot save without a reason"
    @Test fun v2_comfortOrLowCeilingWithAttemptCprNeedsReason() {
        val c = choose { it.copy(goalOfCare = GoalOfCare.C_COMFORT, cpr = Cpr.ATTEMPT) }
        assertTrue("V2" in saveIds(c))
        var s = CarePlanState(ectprs = listOf(base()))
        try { CarePlanBook.saveDraft(s, c, "dr", now, today); fail("saved without a reason") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("CPR")) }
        s = CarePlanBook.saveDraft(s, choose(c) { it.copy(cprConflictReason = "Patient wants CPR despite explanation") }, "dr", now, today)
        assertEquals(Cpr.ATTEMPT, s.ectprs.single().choices.cpr)
        assertTrue("V2" in saveIds(choose { it.copy(respCeiling = 4, cpr = Cpr.ATTEMPT) }))
        assertFalse("V2" in saveIds(choose { it.copy(respCeiling = 5, cpr = Cpr.ATTEMPT) }))
        assertFalse("V2" in saveIds(choose { it.copy(goalOfCare = GoalOfCare.B_CEILING, cpr = Cpr.ATTEMPT) }))
    }

    @Test fun v3_goalAWithLimitNeedsConfirmation() {
        val r = choose { it.copy(goalOfCare = GoalOfCare.A_LIFE, icu = Icu(IcuKind.NO)) }
        assertEquals(IssueSeverity.CONFIRM, EctprRules.check(r, today).first { it.id == "V3" }.severity)
        assertTrue("V3" in signIds(r))
        assertTrue("V3" in signIds(choose { it.copy(goalOfCare = GoalOfCare.A_LIFE, respCeiling = 3) }))
        assertFalse("V3" in signIds(choose(r) { it.copy(goalAConfirmed = true) }))
        assertFalse("V3" in signIds(choose { it.copy(goalOfCare = GoalOfCare.A_LIFE, respCeiling = 5, icu = Icu(IcuKind.YES)) }))
        // As on the web, a confirm stops signing, not saving.
        assertFalse("V3" in saveIds(r))
    }

    @Test fun v4_treatmentLockedUntilUnderstandingRecorded() {
        val r = choose(base()) { it.copy(hydration = Hydration.LIMITED) }
        assertTrue("V4" in saveIds(r))
        assertFalse("V4" in saveIds(r.copy(prognosticUnderstanding = PrognosticUnderstanding(true, "Dr", "2026-10-29"))))
        assertFalse("V4" in saveIds(base()))
    }

    @Test fun v5_surrogateDeclarationBeforeSurrogateEntries() {
        val r = choose(understood().copy(surrogateDeclaration = SurrogateDeclaration(answeringAsSurrogate = true))) { it.copy(goalOfCare = GoalOfCare.B_CEILING) }
        assertTrue("V5" in saveIds(r))
        assertFalse("V5" in saveIds(r.copy(surrogateDeclaration = SurrogateDeclaration(true, true, "2026-10-29"))))
    }

    @Test fun v6_thumbImpressionOrLimitedLiteracyNeedsTwoWitnesses() {
        val thumb = signable().copy(signatures = listOf(consultant, Signature(SignatureRole.PATIENT, "Asha Rao", signedAt = now, thumbImpression = true)))
        assertTrue("V6" in EctprRules.activationBlockers(thumb, today).map { it.id })
        assertFalse("V6" in saveIds(thumb)) // a draft can still be saved
        val one = thumb.copy(signatures = thumb.signatures + Signature(SignatureRole.WITNESS, "Ravi", signedAt = now))
        assertTrue("V6" in EctprRules.activationBlockers(one, today).map { it.id })
        val two = one.copy(signatures = one.signatures + Signature(SignatureRole.WITNESS, "Lata", signedAt = now))
        assertTrue(EctprRules.activationBlockers(two, today).isEmpty())
        assertTrue("V6" in EctprRules.activationBlockers(signable().copy(limitedLiteracy = true), today).map { it.id })
    }

    @Test fun v7_activeOnlyWithConsultantSignatureAndRegistrationNumber() {
        val noReg = signable().copy(signatures = listOf(consultant.copy(registrationNo = "")))
        assertTrue("V7" in EctprRules.activationBlockers(noReg, today).map { it.id })
        try { EctprLifecycle.activate(listOf(noReg), "r1", now, today); fail() } catch (_: IllegalStateException) {}
        val nurseOnly = signable().copy(signatures = listOf(Signature(SignatureRole.NURSE, "Sr Joy", "N-1", now)))
        assertTrue("V7" in EctprRules.activationBlockers(nurseOnly, today).map { it.id })
        assertEquals(EctprStatus.ACTIVE, EctprLifecycle.activate(listOf(signable()), "r1", now, today).single().status)
    }

    @Test fun v8_everyPageFooterCarriesIdentityVersionAndPageCount() {
        val f = EctprRules.pageFooter(signable(), today, 2, 3)
        listOf("Asha Rao", "UHID UH-1", "plan v1", ECTPR_FORM_VERSION, "printed 2026-10-29", "page 2 of 3").forEach { assertTrue(it, f.contains(it)) }
    }

    @Test fun v9_careThatAlwaysContinuesIsFixedAndBilingual() {
        assertEquals(12, CareThatContinues.ITEMS.size) // Sahaara v1.0 §16
        listOf("opioids", "breathlessness", "Sedation", "Mouth care and eye care", "pressure sores", "Bowel", "Bladder", "dignity", "spiritual", "communication").forEach { word -> assertTrue(word, CareThatContinues.ITEMS.any { it.en.contains(word) }) }
        (CareThatContinues.ITEMS + CareThatContinues.TITLE).forEach { assertTrue(it.en, HindiScan.hasHindi(it.hi)) }
    }

    @Test fun c1_noTransferWithIcuOrHighCeilingNeedsReason() {
        val r = choose { it.copy(hospitalTransfer = HospitalTransfer(TransferKind.NO), respCeiling = 4) }
        assertTrue("C1" in signIds(r))
        assertTrue("C1" in signIds(choose { it.copy(hospitalTransfer = HospitalTransfer(TransferKind.NO), icu = Icu(IcuKind.TRIAL, trial)) }))
        assertFalse("C1" in signIds(choose(r) { it.copy(conflictReasons = mapOf("C1" to "Home NIV")) }))
        assertFalse("C1" in signIds(choose { it.copy(hospitalTransfer = HospitalTransfer(TransferKind.NO), respCeiling = 2) }))
        // As on the web, a confirm stops signing, not saving.
        assertFalse("C1" in saveIds(r))
    }

    @Test fun c2_comfortWithOrganSupportSavesOnlyWithReason() {
        listOf<(PlanChoices) -> PlanChoices>({ it.copy(icu = Icu(IcuKind.YES)) }, { it.copy(vasopressors = Vasopressors(VasopressorKind.YES_NO_LIMIT)) }, { it.copy(dialysis = Dialysis(DialysisKind.YES)) }).forEach { f ->
            val r = choose { f(it.copy(goalOfCare = GoalOfCare.C_COMFORT)) }
            assertEquals(IssueSeverity.BLOCK, EctprRules.check(r, today).first { it.id == "C2" }.severity)
            assertFalse("C2" in saveIds(choose(r) { it.copy(conflictReasons = mapOf("C2" to "Reason")) }))
        }
    }

    @Test fun c3_intubationOrPressorsWithoutIcuNeedsReason() {
        assertTrue("C3" in signIds(choose { it.copy(respCeiling = 5, icu = Icu(IcuKind.NO)) }))
        assertTrue("C3" in signIds(choose { it.copy(vasopressors = Vasopressors(VasopressorKind.YES_MAX_AGENTS, maxAgents = 2), icu = Icu(IcuKind.NO)) }))
        assertFalse("C3" in signIds(choose { it.copy(respCeiling = 5, icu = Icu(IcuKind.NO), conflictReasons = mapOf("C3" to "HDU available")) }))
        // As on the web, a confirm stops signing, not saving.
        assertFalse("C3" in saveIds(choose { it.copy(respCeiling = 5, icu = Icu(IcuKind.NO)) }))
    }

    @Test fun c4_everyTrialNeedsDurationReviewTimeAndReviewers() {
        assertTrue("C4" in saveIds(choose { it.copy(icu = Icu(IcuKind.TRIAL, TrialPlan(0, DurationUnit.DAYS, "2026-11-01", listOf("Dr"))) ) }))
        assertTrue("C4" in saveIds(choose { it.copy(icu = Icu(IcuKind.TRIAL, null)) }))
        assertTrue("C4" in saveIds(choose { it.copy(vasopressors = Vasopressors(VasopressorKind.TRIAL, TrialPlan(6, DurationUnit.HOURS, "", listOf("Dr")))) }))
        assertTrue("C4" in saveIds(choose { it.copy(dialysis = Dialysis(DialysisKind.TRIAL, TrialPlan(2, DurationUnit.SESSIONS, "2026-11-01", listOf(" ")))) }))
        assertTrue("C4" in saveIds(choose { it.copy(respCeiling = 4, nivTrial = TrialPlan()) }))
        assertFalse("C4" in saveIds(choose { it.copy(icu = Icu(IcuKind.TRIAL, trial), dialysis = Dialysis(DialysisKind.TRIAL, trial.copy(durationUnit = DurationUnit.SESSIONS)), respCeiling = 4, nivTrial = trial) }))
        assertTrue(trial.complete); assertFalse(trial.copy(reviewers = emptyList()).complete)
    }

    @Test fun c5_icdWithDnacprOrComfortPromptsDeactivation() {
        val r = choose(understood().copy(clinical = ClinicalDetails(implantedDevice = ImplantedDevice.ICD))) { it.copy(goalOfCare = GoalOfCare.C_COMFORT) }
        assertEquals(IssueSeverity.WARN, EctprRules.check(r, today).first { it.id == "C5" }.severity)
        assertFalse("C5" in ids(r.copy(clinical = r.clinical.copy(icdDeactivation = IcdDeactivation(IcdDecision.DEACTIVATE, "2026-10-30")))))
        assertFalse("C5" in ids(choose(understood().copy(clinical = ClinicalDetails(implantedDevice = ImplantedDevice.PACEMAKER))) { it.copy(goalOfCare = GoalOfCare.C_COMFORT) }))
    }

    @Test fun c6_removingExistingTubeIsFlaggedAsWithdrawal() {
        val issue = EctprRules.check(choose { it.copy(feeding = Feeding(removeExistingTube = true)) }, today).first { it.id == "C6" }
        assertEquals(IssueSeverity.WARN, issue.severity)
        assertTrue(issue.message.contains("board"))
    }

    @Test fun c7_fluctuatingCapacityNeedsReassessmentAndShowsOnEdView() {
        val r = understood().copy(capacity = CapacityAssessment(Capacity.FLUCTUATES, "Dr Iyer", "2026-10-29", "Delirium"))
        assertTrue("C7" in saveIds(r))
        val ok = r.copy(capacity = r.capacity.copy(reassessOn = "2026-11-02"))
        assertFalse("C7" in saveIds(ok))
        assertTrue("CAPACITY" in saveIds(understood().copy(capacity = CapacityAssessment(Capacity.LACKS))))
        val row = EdView.rows(signable(ok), NOT_RECORDED).first { it.key == "CAPACITY" }
        assertTrue(row.flag); assertTrue(row.value.en.contains("reassess"))
    }

    @Test fun c8_familyAskedNotToTellPatientWithCapacityNeedsPlan() {
        val r = understood().copy(capacity = CapacityAssessment(Capacity.HAS), values = PlanValues(familyAskedNotToTell = true, ownWords = "x"))
        assertTrue("C8" in saveIds(r))
        assertFalse("C8" in saveIds(r.copy(values = r.values.copy(directQuestionPlan = "Answer honestly if asked"))))
        assertFalse("C8" in saveIds(r.copy(capacity = CapacityAssessment(Capacity.LACKS, "Dr", "2026-10-29", "Coma"))))
    }

    @Test fun l1_underEighteenIsBlocked() {
        assertTrue("L1" in saveIds(base().copy(patient = EctprPatient(dob = "2010-05-05"))))
        assertFalse("L1" in saveIds(base().copy(patient = EctprPatient(dob = "2008-10-29"))))
    }

    @Test fun dnacprNeedsReasonAndDecisionMaker() {
        val r = choose { it.copy(cpr = Cpr.DNACPR) }
        assertTrue(saveIds(r).containsAll(listOf("DNACPR_REASON", "DNACPR_DECIDED_BY")))
        val ok = choose(r) { it.copy(dnacprReason = DnacprReason.FUTILITY, dnacprDecidedBy = DnacprDecidedBy("Dr Iyer", "KMC-1", "2026-10-29 10:00")) }
        assertTrue(saveIds(ok).isEmpty())
    }

    @Test fun idsKeepOnlyTheLastFour() {
        assertEquals("9012", PlanIds.last4("1234 5678 9012"))
        assertTrue(PlanIds.valid("9012")); assertFalse(PlanIds.valid("123456789012")); assertTrue(PlanIds.valid(null))
        assertTrue("ID_LAST4" in saveIds(base().copy(decisionMakers = DecisionMakers(primary = DecisionMaker("Ravi", idType = IdType.AADHAAR, idLast4 = "123456789012")))))
    }

    @Test fun edStartAndBlankValuesAreWarnedNotBlocked() {
        val ed = EctprLifecycle.newDraft("p1", "dr", now, createdInEd = true)
        assertEquals(IssueSeverity.WARN, EctprRules.check(ed, today).first { it.id == "ED_START" }.severity)
        val creep = choose(base().copy(prognosticUnderstanding = PrognosticUnderstanding(true, "Dr", "x"))) { it.copy(hydration = Hydration.FULL) }
        assertTrue("VALUES_BLANK" in EctprRules.warnings(creep, today).map { it.id })
    }

    // Lifecycle: one active version, drafts only edited, reviews make new versions.
    @Test fun oneActiveVersionAndSignedPlansAreNeverEdited() {
        var s = CarePlanState(ectprs = listOf(signable()))
        s = CarePlanBook.sign(s, "r1", "dr", now, today)
        val v1 = s.ectprs.single()
        assertEquals(EctprStatus.ACTIVE, v1.status)
        try { EctprLifecycle.edit(v1, now) { it.copy(reviewDue = "2027-02-01") }; fail() } catch (_: IllegalStateException) {}
        val (afterReview, draft) = CarePlanBook.requestReview(s, "p1", ReviewTrigger.NEW_ADMISSION, "dr", now, today)
        assertNotNull(draft); draft!!
        assertEquals(2, draft.version); assertEquals("r1", draft.supersedes); assertTrue(draft.signatures.isEmpty())
        assertEquals(ReviewRules.defaultReviewDue(today), draft.reviewDue) // review-due resets
        assertFalse(draft.prognosticUnderstanding.confirmed)
        assertEquals(ReviewTrigger.NEW_ADMISSION, CarePlanBook.openReview(afterReview, "r1")?.trigger)
        val confirmed = draft.copy(prognosticUnderstanding = draft.prognosticUnderstanding.copy(confirmed = true), signatures = listOf(consultant))
        s = CarePlanBook.saveDraft(afterReview, confirmed, "dr", now, today)
        s = CarePlanBook.sign(s, draft.id, "dr", now, today)
        assertEquals(1, s.ectprs.count { it.status == EctprStatus.ACTIVE })
        assertEquals(EctprStatus.SUPERSEDED, s.ectprs.first { it.id == "r1" }.status)
        // Superseded QR tokens are revoked; exactly one live token, for v2.
        val live = s.tokens.filter { it.live }
        assertEquals(listOf(draft.id), live.map { it.ectprId })
        val old = s.tokens.first { it.ectprId == "r1" }
        assertEquals(EdKind.REVOKED, EdView.forToken(old.token, s.tokens, s.ectprs, today).kind)
        assertTrue(s.audit.verify())
        assertEquals(listOf(AuditAction.SIGN, AuditAction.SHARE, AuditAction.REVIEW, AuditAction.EDIT, AuditAction.SIGN, AuditAction.REVOKE, AuditAction.SHARE), s.audit.events.map { it.action })
    }

    @Test fun onlyOneDraftAtATime() {
        val (s, _) = CarePlanBook.startPlan(CarePlanState(), "p1", "dr", now, EctprPatient())
        try { CarePlanBook.startPlan(s, "p1", "dr", now, EctprPatient()); fail() } catch (_: IllegalArgumentException) {}
    }

    // ED interpretation + acceptance "Unsigned ECTPRs never reach the ED view; past review dates show OUT OF DATE"
    @Test fun edInterpretationStates() {
        assertEquals(EdKind.NONE, EdView.forPatient(emptyList(), "p1", today).kind)
        val unsigned = EdView.forPatient(listOf(understood()), "p1", today)
        assertEquals(EdKind.UNSIGNED, unsigned.kind); assertFalse(unsigned.showContent); assertTrue(unsigned.standardCareApplies)
        val active = EctprLifecycle.activate(listOf(signable()), "r1", now, today)
        val ok = EdView.forPatient(active, "p1", today)
        assertEquals(EdKind.ACTIVE, ok.kind); assertFalse(ok.standardCareApplies); assertTrue(ok.showContent)
        val late = EdView.forPatient(active, "p1", LocalDate.of(2027, 1, 21))
        assertEquals(EdKind.OUT_OF_DATE, late.kind); assertTrue(late.standardCareApplies); assertTrue(late.banner.en.contains("OUT OF DATE")); assertTrue(late.banner.en.contains("standard care applies"))
        assertEquals(EdView.SHOW_CONTENT_WHEN_OUT_OF_DATE, late.showContent)
        val revoked = EctprLifecycle.revoke(active, "r1", "Patient changed mind", "Dr Iyer", now)
        val r = EdView.forPatient(revoked, "p1", today)
        assertEquals(EdKind.REVOKED, r.kind); assertTrue(r.standardCareApplies); assertFalse(r.showContent)
        listOf(EdKind.NONE, EdKind.UNSIGNED, EdKind.OUT_OF_DATE, EdKind.REVOKED).forEach { k -> assertTrue(listOf(EdView.forPatient(emptyList(), "p1", today), unsigned, late, r).first { it.kind == k }.banner.en.contains("tandard care")) }
    }

    // Acceptance "Revoked QRs show card withdrawn"
    @Test fun revokedCardTokensShowCardWithdrawn() {
        var s = CarePlanBook.sign(CarePlanState(ectprs = listOf(signable())), "r1", "dr", now, today)
        val token = s.tokens.single().token
        assertEquals(EdKind.ACTIVE, EdView.forToken(token, s.tokens, s.ectprs, today).kind)
        s = CarePlanBook.withdraw(s, "r1", "Signed cancellation", "Asha Rao", "dr", now)
        val view = EdView.forToken(token, s.tokens, s.ectprs, today)
        assertEquals(EdKind.REVOKED, view.kind); assertTrue(view.banner.en.contains("CARD WITHDRAWN"))
        assertEquals(EdKind.NONE, EdView.forToken("0".repeat(32), s.tokens, s.ectprs, today).kind)
    }

    @Test fun cardTokenIsARandomUrlWithNoPersonalData() {
        val s = CarePlanBook.sign(CarePlanState(ectprs = listOf(signable())), "r1", "dr", now, today)
        val url = s.tokens.single().url
        assertTrue(url.matches(Regex("^https://sahara\\.wedevit\\.in/ed/[A-Za-z0-9_-]{43}$")))
        listOf("Asha", "UH-1").forEach { assertFalse(url.substringAfterLast('/').contains(it)) }
        assertNotEquals(CardTokens.newToken(), CardTokens.newToken())
        assertEquals(43, CardTokens.newToken().length)
    }

    // ED view order (clinical spec §6)
    @Test fun edQuickViewOrderMatchesClinicalSpec() {
        val rows = EdView.rows(signable(), NOT_RECORDED)
        assertEquals(listOf("TRANSFER", "CPR", "RESP_CEILING", "ICU", "GOAL", "ALLERGIES", "DEVICE", "DECISION_MAKER", "TEAM", "CAPACITY", "AMD", "VERSION"), rows.map { it.key })
        assertEquals(EdView.ORDER, rows.map { it.key })
        assertTrue(IfInDoubt.TITLE.en == "IF IN DOUBT, RESUSCITATE")
        assertEquals(4, IfInDoubt.CONDITIONS.size)
        IfInDoubt.CONDITIONS.forEach { assertTrue(it.en, it.en.startsWith("If ")) } // Sujay, 1 Nov: each line starts with "If"
        assertTrue(IfInDoubt.AFTER.en.startsWith("Then resuscitate"))
        rows.forEach { assertTrue(it.label.en, HindiScan.hasHindi(it.label.hi) || it.label.hi.all { c -> c.isUpperCase() }) }
    }

    @Test fun edRowsShowChoicesInPlainWords() {
        val r = signable(choose { it.copy(hospitalTransfer = HospitalTransfer(TransferKind.ONLY_FOR, listOf(TransferReason.PAIN, TransferReason.BLEEDING)), cpr = Cpr.DNACPR,
            dnacprReason = DnacprReason.BURDEN_OUTWEIGHS_BENEFIT, dnacprDecidedBy = DnacprDecidedBy("Dr Iyer", "KMC-1", "2026-10-29"), respCeiling = 2, icu = Icu(IcuKind.NO), goalOfCare = GoalOfCare.C_COMFORT) })
        val rows = EdView.rows(r, NOT_RECORDED).associateBy { it.key }
        assertTrue(rows.getValue("TRANSFER").value.en.contains("Uncontrolled pain"))
        assertTrue(rows.getValue("CPR").value.en.contains("DNACPR")); assertTrue(rows.getValue("CPR").flag)
        assertTrue(rows.getValue("RESP_CEILING").value.en.startsWith("2 ·"))
        assertTrue(rows.getValue("GOAL").value.en.contains("comfort"))
        assertTrue(rows.getValue("TEAM").value.en.contains("KMC-12345"))
    }

    @Test fun auditLogIsAppendOnlyAndTamperEvident() {
        val log = AuditLog.EMPTY.append(now, "dr", AuditAction.VIEW, AuditRecordType.ECTPR, "r1").append(now, "dr", AuditAction.PRINT, AuditRecordType.ECTPR, "r1")
        assertTrue(log.verify())
        val events = log.events
        assertEquals(2, events.size)
        // Removing or editing an event breaks the chain and is refused on load.
        try { AuditLog.of(events.drop(1)); fail() } catch (_: IllegalStateException) {}
        try { AuditLog.of(listOf(events[0].copy(actor = "someone"), events[1])); fail() } catch (_: IllegalStateException) {}
        assertEquals(2, AuditLog.of(events).events.size)
        // The list handed out is a copy.
        (log.events as MutableList).clear(); assertEquals(2, log.events.size)
    }

    @Test fun reviewRulesAndTriggers() {
        assertEquals(6, ReviewTrigger.entries.size)
        assertEquals("2027-01-21", ReviewRules.defaultReviewDue(today))
        assertFalse(ReviewRules.due(signable().copy(reviewDue = "2026-10-29"), today)) // still in date on the day itself
        assertTrue(ReviewRules.due(signable().copy(reviewDue = "2026-10-28"), today))
        assertTrue(ReviewRules.due(signable().copy(reviewDue = ""), today))
        assertTrue("REVIEW_DUE" in EctprRules.activationBlockers(signable().copy(reviewDue = "2026-10-28"), today).map { it.id })
        assertFalse("REVIEW_DUE" in EctprRules.activationBlockers(signable().copy(reviewDue = "2026-10-29"), today).map { it.id })
        assertFalse(ReviewRules.due(signable(), today))
        ReviewTrigger.entries.forEach { assertTrue(HindiScan.hasHindi(PlanText.trigger(it).hi)) }
    }

    @Test fun jsonMatchesTheWebModelShapeAndRoundTrips() {
        val full = signable(choose(understood().copy(
            decisionMakers = DecisionMakers(DecisionMaker("Ravi", "Son", "98450 00000", IdType.AADHAAR, "9012", AuthorityBasis.NAMED_BY_PATIENT), null, listOf("Lata"), "All agree"),
            capacity = CapacityAssessment(Capacity.FLUCTUATES, "Dr", "2026-10-29", "Delirium", "2026-11-02"),
            existingDirective = ExistingDirective(DirectiveKind.VALID_AMD, "2025-01-01", "Home", "Notary Shah"),
            clinical = ClinicalDetails("Ca lung", "IV", 3, "Penicillin", ImplantedDevice.ICD, IcdDeactivation(IcdDecision.DEACTIVATE, "2026-10-30"), "Morphine", "2026-10-01"),
        )) { it.copy(goalOfCare = GoalOfCare.B_CEILING, cpr = Cpr.DNACPR, dnacprReason = DnacprReason.FUTILITY, dnacprDecidedBy = DnacprDecidedBy("Dr", "R", "t"),
            hospitalTransfer = HospitalTransfer(TransferKind.ONLY_FOR, listOf(TransferReason.SEIZURES)), callBeforeAmbulance = listOf("Palliative 080"), ambulance = AmbulanceOption.SYMPTOM_RELIEF_TRANSPORT,
            respCeiling = 4, maxOxygenLpm = 4.0, ceilingConditions = CeilingConditions(true, 48, 88, ""), icu = Icu(IcuKind.TRIAL, trial),
            vasopressors = Vasopressors(VasopressorKind.YES_MAX_AGENTS, maxAgents = 1), dialysis = Dialysis(DialysisKind.ONLY_IF_REVERSIBLE),
            feeding = Feeding(NgFeeding(NgKind.UP_TO_DAYS, 5), PegFeeding.NO), hydration = Hydration.LIMITED, conflictReasons = mapOf("C1" to "x"), goalAConfirmed = false, nivTrial = trial) })
        val json = PlanJson.encode(full)
        assertEquals(full, PlanJson.decode(JSONObject(json.toString())))
        val c = json.getJSONObject("choices")
        assertEquals("ONLY_FOR", c.getJSONObject("hospitalTransfer").getString("kind"))
        assertEquals("SEIZURES", c.getJSONObject("hospitalTransfer").getJSONArray("reasons").getString(0))
        assertEquals("TRIAL", c.getJSONObject("icu").getString("kind"))
        assertEquals(3, c.getJSONObject("icu").getJSONObject("trial").getInt("durationValue"))
        assertEquals("UP_TO_DAYS", c.getJSONObject("feeding").getJSONObject("ng").getString("kind"))
        assertTrue(c.getJSONObject("feeding").getBoolean("comfortOral"))
        assertEquals("KMC-12345", json.getJSONArray("signatures").getJSONObject(0).getString("registrationNo"))
        // Blank choices are JSON null, never a guessed value (V1).
        val blank = PlanJson.encode(base()).getJSONObject("choices")
        listOf("goalOfCare", "cpr", "hospitalTransfer", "respCeiling", "icu", "hydration").forEach { assertTrue(it, blank.isNull(it)) }
        assertEquals(base(), PlanJson.decode(PlanJson.encode(base())))
    }

    @Test fun webRecordReadsOnAndroid() {
        val web = """{"id":"w1","patientId":"p9","version":2,"formVersion":"Sahara ECTPR v1.0","status":"ACTIVE","createdAt":"2026-10-20","updatedAt":"2026-10-21","createdBy":"web",
            "patient":{"name":"Web Patient","dob":"1960-02-02","sex":"F","address":"","phone":"","uhid":"U9","languagePref":"te"},
            "careTeam":{"oncologist":"","palliativePhysician":"Dr P","palliativePhone24h":"080","extraTeam":[],"localDoctor":""},
            "decisionMakers":{"primary":null,"alternate":null,"familyToInform":[],"familyConsensusNote":""},
            "clinical":{"diagnosis":"","stage":"","ecog":null,"allergies":"","implantedDevice":null,"icdDeactivation":{"decision":null,"date":""},"keyMedicines":"","eligibilityDate":""},
            "capacity":{"status":null,"assessor":"","assessedOn":"","basis":"","reassessOn":""},"existingDirective":{"kind":"NONE"},
            "prognosticUnderstanding":{"confirmed":true,"confirmedBy":"Dr","confirmedOn":"2026-10-20"},"surrogateDeclaration":{"answeringAsSurrogate":false,"confirmed":false,"confirmedOn":""},
            "values":{"patientKnows":true,"ownWords":"","unacceptableOutcomes":[],"familyAskedNotToTell":false,"directQuestionPlan":"","financialConcernDiscussed":null,"socialWorkReferral":null},
            "choices":{"goalOfCare":"C_COMFORT","cpr":"DNACPR","dnacprReason":"PATIENT_REFUSAL","dnacprDiscussedWith":"","dnacprDecidedBy":{"name":"Dr","registrationNo":"R1","decidedAt":"2026-10-20"},"cprConflictReason":"",
              "hospitalTransfer":{"kind":"NO"},"callBeforeAmbulance":[],"ambulance":null,"respCeiling":0,"maxOxygenLpm":null,"ceilingConditions":{"reversibleCauseException":null,"nivMaxHours":null,"spo2Floor":null,"homeNivReason":""},
              "icu":{"kind":"NO"},"vasopressors":{"kind":"NO"},"dialysis":{"kind":"NO"},"feeding":{"comfortOral":true,"ng":{"kind":"NO"},"peg":"NO","removeExistingTube":false},"hydration":"ORAL_SIPS","conflictReasons":{},"goalAConfirmed":false},
            "reviewDue":"2027-01-01","signatures":[{"role":"TREATING_CONSULTANT","name":"Dr K","registrationNo":"KMC-9","signedAt":"2026-10-21T09:00:00Z"}],"limitedLiteracy":false}"""
        val r = PlanJson.decode(JSONObject(web))
        assertEquals(EctprStatus.ACTIVE, r.status); assertEquals(0, r.choices.respCeiling); assertEquals(TransferKind.NO, r.choices.hospitalTransfer?.kind)
        assertEquals(DirectiveKind.NONE, r.existingDirective?.kind); assertNull(r.choices.ambulance)
        assertEquals(EdKind.ACTIVE, EdView.interpret(r, today).kind)
        assertTrue(EctprRules.saveBlockers(r, today).isEmpty())
    }

    @Test fun stateRoundTripsThroughStorageJson() {
        var s = CarePlanBook.sign(CarePlanState(ectprs = listOf(signable())), "r1", "dr", now, today)
        s = CarePlanBook.startAmd(s, "p1", "fam", now, "Asha Rao").first
        val back = CarePlanState.decode(s.encode())
        assertEquals(s.ectprs, back.ectprs); assertEquals(s.amds, back.amds); assertEquals(s.tokens, back.tokens); assertEquals(s.audit.events, back.audit.events)
        assertTrue(back.audit.verify())
    }

    @Test fun everyChoiceHasHindi() {
        val labels = GoalOfCare.entries.flatMap { listOf(PlanText.goal(it), PlanText.goalMeaning(it)) } + Cpr.entries.flatMap { listOf(PlanText.cpr(it), PlanText.cprMeaning(it)) } +
            DnacprReason.entries.map(PlanText::dnacprReason) + TransferKind.entries.flatMap { listOf(PlanText.transfer(it), PlanText.transferMeaning(it)) } + TransferReason.entries.map(PlanText::transferReason) +
            (0..5).flatMap { listOf(PlanText.ceiling(it), PlanText.ceilingMeaning(it)) } + IcuKind.entries.map(PlanText::icu) + VasopressorKind.entries.map(PlanText::vaso) + DialysisKind.entries.map(PlanText::dialysis) +
            NgKind.entries.map(PlanText::ng) + PegFeeding.entries.map(PlanText::peg) + Hydration.entries.flatMap { listOf(PlanText.hydration(it), PlanText.hydrationMeaning(it)) } + AmbulanceOption.entries.map(PlanText::ambulance) +
            Capacity.entries.map(PlanText::capacity) + AuthorityBasis.entries.map(PlanText::authority) + IdType.entries.map(PlanText::idType) + ImplantedDevice.entries.map(PlanText::device) + EctprStatus.entries.map(PlanText::status) +
            IfInDoubt.CONDITIONS + listOf(IfInDoubt.TITLE, IfInDoubt.AFTER, NO_PREFERENCE, NOT_RECORDED, WalletCard.TREAT_LINE)
        labels.forEach { assertTrue(it.en, HindiScan.hasHindi(it.hi) || it.hi == it.en.uppercase() || it.hi.matches(Regex("[A-Z0-9 ·()-]+"))) }
        val issues = EctprRules.check(choose(base().copy(createdInEd = true, patient = EctprPatient(dob = "2015-01-01"), capacity = CapacityAssessment(Capacity.FLUCTUATES), limitedLiteracy = true)) {
            it.copy(goalOfCare = GoalOfCare.C_COMFORT, cpr = Cpr.ATTEMPT, icu = Icu(IcuKind.TRIAL), feeding = Feeding(removeExistingTube = true)) }, today)
        assertTrue(issues.size >= 8)
        issues.forEach { assertTrue(it.id, HindiScan.hasHindi(it.messageHi)) }
    }
}
