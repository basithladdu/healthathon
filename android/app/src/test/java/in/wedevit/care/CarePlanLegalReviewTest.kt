package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Mirrors prototype/tests/ectpr-legal-review.test.mjs: the legal team's review of the form (Rituparna P, 31 Oct 2026). */
class CarePlanLegalReviewTest {
    private val today = LocalDate.of(2026, 11, 1)
    private val now = "2026-11-01T00:00:00Z"
    private fun base() = EctprLifecycle.newDraft("p", "dr", now, id = "r1")
    private val complete = EctprWithdrawal(true, "2026-11-01", "2026-11-02", "A, B, C", "D, E, F", "Permitted", WithdrawalFinal.TO_BE_PERFORMED, "Stepwise")
    private fun ids(r: EctprRecord) = EctprRules.check(r, today).map { it.id }

    @Test fun goingAheadNeedsTheWholeBoardRecord() {
        val r = base().copy(withdrawal = EctprWithdrawal(applicable = true, finalDecision = WithdrawalFinal.TO_BE_PERFORMED))
        val issue = EctprRules.check(r, today).first { it.id == "W1" }
        assertEquals(IssueSeverity.BLOCK, issue.severity)
        // As on the web it stops signing, not saving.
        assertTrue("W1" in EctprRules.activationBlockers(r, today).map { it.id })
        assertFalse("W1" in EctprRules.saveBlockers(r, today).map { it.id })
        assertFalse("W1" in ids(r.copy(withdrawal = complete)))
        assertTrue("W1" in ids(r.copy(withdrawal = complete.copy(secondaryBoardDate = "not a date"))))
        assertTrue("W1" in ids(r.copy(withdrawal = complete.copy(protocol = " "))))
    }

    @Test fun aRefusalNeedsNoBoardRecordAndAnUntickedSectionIsIgnored() {
        assertFalse("W1" in ids(base().copy(withdrawal = EctprWithdrawal(applicable = true, finalDecision = WithdrawalFinal.FAMILY_REFUSED))))
        val unticked = base().copy(withdrawal = EctprWithdrawal(finalDecision = WithdrawalFinal.TO_BE_PERFORMED))
        assertFalse("W1" in ids(unticked))
        assertTrue(PlanText.withdrawalLines(unticked).isEmpty())
    }

    @Test fun theEmergencyViewIsToldWhenWithdrawalIsRecorded() {
        val r = base().copy(withdrawal = complete)
        assertTrue(EdView.interpret(r, today).flags.any { it.en.contains("medical board") })
        assertTrue(EdView.interpret(r.copy(withdrawal = complete.copy(finalDecision = WithdrawalFinal.BOARD_REFUSED)), today).flags.isEmpty())
        assertTrue(EdView.interpret(base(), today).flags.isEmpty()); assertTrue(EdView.interpret(null, today).flags.isEmpty())
        assertTrue(PlanSpeech.edView(EdView.interpret(r, today), NOT_RECORDED, false).contains("medical board"))
    }

    @Test fun secondaryDecisionMakerIsAskedToSignOnceASurrogateHasSigned() {
        val primary = Signature(SignatureRole.SURROGATE, "Ravi", signedAt = now, surrogateRank = SurrogateRank.PRIMARY)
        val r = base().copy(decisionMakers = DecisionMakers(alternate = DecisionMaker("Asha", "Partner")), signatures = listOf(primary))
        assertEquals(IssueSeverity.WARN, EctprRules.check(r, today).first { it.id == "W2" }.severity)
        assertFalse("W2" in ids(r.copy(signatures = listOf(primary, Signature(SignatureRole.SURROGATE, "Asha", signedAt = now, surrogateRank = SurrogateRank.SECONDARY)))))
        assertFalse("W2" in ids(r.copy(signatures = emptyList())))
        assertFalse("W2" in ids(r.copy(decisionMakers = DecisionMakers())))
    }

    @Test fun theWithdrawalStepSitsBeforeReviewDate() {
        assertEquals(5, planStepOf("choices.cpr")); assertEquals(5, planStepOf("clinical.icdDeactivation"))
        assertEquals(6, planStepOf("withdrawal")); assertEquals(7, planStepOf("reviewDue")); assertEquals(8, planStepOf("signatures"))
        val r = base().copy(reviewDue = "2027-01-20", withdrawal = complete)
        assertTrue(PlanSpeech.wizardStep(r, 6, false).contains("Common Cause")); assertTrue(PlanSpeech.wizardStep(r, 6, false).contains("Stepwise"))
        assertTrue(PlanSpeech.wizardStep(r, 7, false).contains("2027-01-20"))
        assertTrue(PlanSpeech.wizardStep(r, 8, false).contains("registration number"))
    }

    @Test fun theSameKeysAsTheWebRoundTrip() {
        val r = base().copy(
            patient = EctprPatient(name = "Asha", gender = "Non-binary"),
            decisionMakers = DecisionMakers(presentAtDiscussion = listOf("Asha (live-in partner)"), attemptedContact = listOf("Ravi (brother)")),
            values = PlanValues(knowsRecordedWhen = KnowsRecordedWhen.AFTER_COUNSELLING),
            homeCare = EctprHomeCare("Weekly", "Sister Joy", "Karunashraya", true, true, "Manipal, 2025"),
            withdrawal = complete,
            signatures = listOf(Signature(SignatureRole.SURROGATE, "Ravi", signedAt = now, surrogateRank = SurrogateRank.SECONDARY)),
        )
        val j = JSONObject(PlanJson.encode(r).toString())
        assertEquals(r, PlanJson.decode(j))
        assertEquals("Non-binary", j.getJSONObject("patient").getString("gender"))
        assertEquals("Ravi (brother)", j.getJSONObject("decisionMakers").getJSONArray("attemptedContact").getString(0))
        assertEquals("Asha (live-in partner)", j.getJSONObject("decisionMakers").getJSONArray("presentAtDiscussion").getString(0))
        assertEquals("AFTER_COUNSELLING", j.getJSONObject("values").getString("knowsRecordedWhen"))
        assertEquals("Manipal, 2025", j.getJSONObject("homeCare").getString("previousAdmissionDetails"))
        assertEquals("TO_BE_PERFORMED", j.getJSONObject("withdrawal").getString("finalDecision"))
        assertEquals("SECONDARY", j.getJSONArray("signatures").getJSONObject(0).getString("surrogateRank"))
        // The secondary decision-maker keeps the JSON key `alternate`.
        assertTrue(PlanJson.encode(base()).getJSONObject("decisionMakers").has("alternate"))
    }

    @Test fun olderRecordsWithoutTheNewFieldsStillRead() {
        val old = JSONObject(PlanJson.encode(base()).toString())
        old.remove("homeCare"); old.remove("withdrawal")
        old.getJSONObject("patient").remove("gender")
        old.getJSONObject("decisionMakers").apply { remove("presentAtDiscussion"); remove("attemptedContact") }
        old.getJSONObject("values").remove("knowsRecordedWhen")
        val r = PlanJson.decode(old)
        assertEquals(base(), r); assertNull(r.homeCare); assertNull(r.withdrawal)
        assertFalse(PlanJson.encode(r).has("homeCare")); assertFalse(PlanJson.encode(r).has("withdrawal"))
        assertTrue(PlanText.homeCareLines(r).isEmpty()); assertTrue(PlanText.withdrawalLines(r).isEmpty()); assertTrue(PlanText.fullRecordLines(r).isEmpty())
        assertEquals("No final decision recorded", PlanText.withdrawalFinal(null).en)
        // An unknown value from a newer version reads as not recorded.
        val odd = JSONObject(old.toString()).put("withdrawal", JSONObject().put("applicable", true).put("finalDecision", "SOMETHING_NEW"))
        assertNull(PlanJson.decode(odd).withdrawal?.finalDecision)
    }

    @Test fun theEmergencyViewShowsHomeCareAndWithdrawalOnlyWhenRecorded() {
        assertEquals(EdView.ORDER, EdView.rows(base(), NOT_RECORDED).map { it.key })
        val r = base().copy(withdrawal = complete, homeCare = EctprHomeCare(ngoContact = "Karunashraya", ngoConsulted = true, previousAdmission = true),
            decisionMakers = DecisionMakers(DecisionMaker("Ravi", "Brother"), DecisionMaker("Asha", "Partner")), careTeam = CareTeam(oncologist = "Dr Rao"))
        val rows = EdView.rows(r, NOT_RECORDED)
        val keys = rows.map { it.key }
        assertEquals(keys.indexOf("TEAM") + 1, keys.indexOf("HOME_CARE")); assertEquals(keys.indexOf("CAPACITY") - 1, keys.indexOf("WITHDRAWAL"))
        val by = rows.associateBy { it.key }
        assertTrue(by.getValue("HOME_CARE").value.en.contains("The organisation was consulted about this plan")); assertTrue(by.getValue("HOME_CARE").value.en.contains("Admitted before for this condition"))
        assertTrue(by.getValue("WITHDRAWAL").value.en.contains("Withdrawal or withholding to be performed"))
        assertTrue(by.getValue("DECISION_MAKER").value.en.contains("Secondary: Asha"))
        // A doctor who is not an oncologist reads correctly.
        assertTrue(by.getValue("TEAM").value.en.contains("Treating consultant: Dr Rao")); assertFalse(by.getValue("TEAM").value.en.contains("Oncologist"))
        assertTrue(by.getValue("WITHDRAWAL").value.hi.contains("अंतिम फ़ैसला"))
    }

    /** The server's quickViewSnapshot drops these from a scan; the offline QR, which this phone writes, must not carry them either. */
    @Test fun theOfflineQrLeavesOutGenderFamilyListsAndTheBoardRecord() {
        val r = base().copy(patient = EctprPatient(name = "Asha", uhid = "U1", gender = "Non-binary"), withdrawal = complete,
            decisionMakers = DecisionMakers(presentAtDiscussion = listOf("Lata (live-in partner)"), attemptedContact = listOf("Ravi (brother)")))
        val shown = EdCardOffline.text(r, null, false, today)
        listOf("Non-binary", "Lata", "Ravi", "A, B, C", "D, E, F", "Permitted").forEach { assertFalse(it, shown.contains(it)) }
    }

    @Test fun theFullRecordCarriesWhatTheQuickViewLeavesOut() {
        val r = base().copy(patient = EctprPatient(gender = "Non-binary"), values = PlanValues(knowsRecordedWhen = KnowsRecordedWhen.BEFORE_COUNSELLING),
            decisionMakers = DecisionMakers(presentAtDiscussion = listOf("Lata", ""), attemptedContact = listOf("Ravi")))
        val lines = PlanText.fullRecordLines(r).map { it.en }
        assertEquals(listOf("Gender: Non-binary", "Present for the discussion: Lata", "Could not be reached: Ravi", "When was this recorded? Before counselling"), lines)
        assertTrue(PlanSpeech.summary(r, null, false).contains("Could not be reached: Ravi"))
    }

    @Test fun capacityGuidanceIsMarkedAsADraftAndChecksAllFourAbilities() {
        assertTrue(CapacityGuide.NOTE.en.contains("Draft"))
        assertTrue(CapacityGuide.POINTS.any { Regex("understand.*retain.*weigh.*communicate").containsMatchIn(it.en) })
        (CapacityGuide.POINTS + CapacityGuide.TITLE + CapacityGuide.NOTE).forEach { assertTrue(it.en, HindiScan.hasHindi(it.hi)) }
        assertTrue(PlanSpeech.wizardStep(base(), 2, false).contains(CapacityGuide.TITLE.en))
    }

    @Test fun everyNewLabelHasHindi() {
        (WithdrawalFinal.entries.map { PlanText.withdrawalFinal(it) } + PlanText.withdrawalFinal(null) + KnowsRecordedWhen.entries.map(PlanText::knowsRecordedWhen)).forEach { assertTrue(it.en, HindiScan.hasHindi(it.hi)) }
        val r = base().copy(withdrawal = EctprWithdrawal(applicable = true, finalDecision = WithdrawalFinal.TO_BE_PERFORMED), signatures = listOf(Signature(SignatureRole.SURROGATE, "Ravi", signedAt = now)), decisionMakers = DecisionMakers(alternate = DecisionMaker("Asha")))
        EctprRules.check(r, today).filter { it.id == "W1" || it.id == "W2" }.also { assertEquals(2, it.size) }.forEach { assertTrue(it.id, HindiScan.hasHindi(it.messageHi)) }
    }
}
