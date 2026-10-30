package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CareLivingWillTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val now = "2026-10-29T10:00:00Z"
    private val hash = "a".repeat(64)
    private fun ready() = AmdLifecycle.newDraft("p1", now, AmdMaker("Asha Rao", "1950-01-01", idType = IdType.AADHAAR, idLast4 = "9012"), id = "a1").copy(
        soundMindDeclaration = true,
        answers = AmdAnswers(listOf(AmdSituation.TERMINAL_ILLNESS), listOf(AmdTreatment.MECHANICAL_VENTILATION, AmdTreatment.TUBE_FEEDING), PlaceOfCare.HOME),
        dhrs = listOf(Dhr("Ravi Rao", "Son", "98450 00000"), Dhr("Lata Rao", "Daughter", "98450 11111")))
    private val witnesses = listOf(AmdWitness("Mohan K", "Bengaluru", "2026-10-30"), AmdWitness("Fatima S", "Bengaluru", "2026-10-30"))
    private val attestor = AmdAttestor(AttestorKind.NOTARY, "R. Shah", "Notary reg 44", "Bengaluru", "2026-10-30")
    private val scan = AmdScan("amd-scan.jpg", hash, now)
    private fun ids(r: AmdRecord) = AmdRules.generateIssues(r, today).map { it.id }

    // Acceptance: "DOB under 18 blocks an AMD"
    @Test fun l1_underEighteenAndMissingDobBlockGeneration() {
        val minor = ready().copy(maker = ready().maker.copy(dob = "2010-01-01"))
        assertTrue("L1" in ids(minor))
        try { AmdLifecycle.generate(minor, now, today); fail("generated for a minor") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("adult")) }
        assertTrue("L1_DOB" in ids(ready().copy(maker = ready().maker.copy(dob = ""))))
        assertTrue("L1_CAPACITY" in ids(ready().copy(soundMindDeclaration = false)))
        assertFalse("L1" in ids(ready().copy(maker = ready().maker.copy(dob = "2008-10-29"))))
    }

    @Test fun l2_atLeastOneRepresentativeAndWarnBelowTwo() {
        assertEquals(IssueSeverity.BLOCK, AmdRules.generateIssues(ready().copy(dhrs = emptyList()), today).first { it.id == "L2" }.severity)
        assertEquals(IssueSeverity.WARN, AmdRules.generateIssues(ready().copy(dhrs = ready().dhrs.take(1)), today).first { it.id == "L2_TWO" }.severity)
        assertEquals(AmdStatus.GENERATED, AmdLifecycle.generate(ready().copy(dhrs = ready().dhrs.take(1)), now, today).status)
        assertTrue(AmdRules.generateIssues(ready(), today).isEmpty())
    }

    @Test fun idTypeAndLastFourOnly() {
        assertTrue("ID_LAST4" in ids(ready().copy(maker = ready().maker.copy(idLast4 = "123456789012"))))
        assertTrue("ID_LAST4" in ids(ready().copy(dhrs = listOf(Dhr("Ravi", idType = IdType.VOTER_ID, idLast4 = "ABC")))))
        assertEquals("9012", PlanIds.last4("1234-5678-9012"))
        val json = AmdJson.encode(ready()).toString()
        assertFalse(json.contains("12345678"))
    }

    @Test fun needsSituationAndTreatment() {
        assertTrue("SITUATIONS" in ids(ready().copy(answers = AmdAnswers(refusedTreatments = listOf(AmdTreatment.CPR)))))
        assertTrue("TREATMENTS" in ids(ready().copy(answers = AmdAnswers(situations = listOf(AmdSituation.PERMANENT_UNCONSCIOUSNESS)))))
    }

    // Acceptance: "an AMD stays Generated until scan, witnesses and attestor are recorded"
    @Test fun l3_staysGeneratedUntilScanWitnessesAndAttestorRecorded() {
        val generated = AmdLifecycle.generate(ready(), now, today)
        assertEquals(AmdStatus.GENERATED, generated.status)
        val records = listOf(generated)
        fun refused(scan: AmdScan?, w: List<AmdWitness>, a: AmdAttestor?) = AmdRules.executeIssues(generated, scan, w, a).any { it.severity == IssueSeverity.BLOCK }
        assertTrue(refused(null, witnesses, attestor))
        assertTrue(refused(scan.copy(sha256 = "not-a-hash"), witnesses, attestor))
        assertTrue(refused(scan, witnesses.take(1), attestor))
        assertTrue(refused(scan, listOf(witnesses[0], witnesses[0].copy(address = "x")), attestor))
        assertTrue(refused(scan, witnesses, null))
        assertTrue(refused(scan, witnesses, attestor.copy(kind = null)))
        assertTrue(refused(scan, witnesses, attestor.copy(attestedOn = "")))
        try { AmdLifecycle.execute(records, "a1", scan, witnesses.take(1), attestor, now); fail() } catch (_: IllegalStateException) {}
        val executed = AmdLifecycle.execute(records, "a1", scan, witnesses, attestor, now).single()
        assertEquals(AmdStatus.EXECUTED, executed.status); assertEquals(hash, executed.scan?.sha256); assertEquals(now, executed.executedAt)
        // A draft cannot jump to Executed.
        assertTrue(AmdRules.executeIssues(ready(), scan, witnesses, attestor).any { it.id == "NOT_GENERATED" })
    }

    @Test fun warnsWhenAWitnessIsARepresentative() {
        val generated = AmdLifecycle.generate(ready(), now, today)
        val issues = AmdRules.executeIssues(generated, scan, listOf(AmdWitness(" ravi  rao "), witnesses[1]), attestor)
        assertEquals(IssueSeverity.WARN, issues.single { it.id == "L3_WITNESS_IS_DHR" }.severity)
        assertEquals(AmdStatus.EXECUTED, AmdLifecycle.execute(listOf(generated), "a1", scan, listOf(AmdWitness("Ravi Rao"), witnesses[1]), attestor, now).single().status)
    }

    @Test fun l6_changesAreNewVersionsAndLatestExecutedIsCurrent() {
        var s = CarePlanState(amds = listOf(ready()))
        s = CarePlanBook.generateAmd(s, "a1", "fam", now, today)
        s = CarePlanBook.executeAmd(s, "a1", scan, witnesses, attestor, "fam", now)
        val v1 = s.amds.single()
        try { AmdLifecycle.edit(v1, now) { it.copy(answers = it.answers.copy(otherWishes = "x")) }; fail() } catch (_: IllegalStateException) {}
        val (s2, v2) = CarePlanBook.startAmd(s, "p1", "fam", now, AmdMaker())
        assertEquals(2, v2.version); assertEquals("a1", v2.supersedes); assertNull(v2.scan); assertTrue(v2.witnesses.isEmpty()); assertFalse(v2.soundMindDeclaration)
        // v1 stays current until v2 is executed.
        assertEquals("a1", AmdLifecycle.current(s2.amds, "p1")?.id)
        var s3 = CarePlanBook.saveAmd(s2, v2.copy(soundMindDeclaration = true, answers = v2.answers.copy(otherWishes = "Prayers at bedside")), "fam", now)
        s3 = CarePlanBook.generateAmd(s3, v2.id, "fam", now, today)
        s3 = CarePlanBook.executeAmd(s3, v2.id, scan.copy(sha256 = "b".repeat(64)), witnesses, attestor, "fam", now)
        assertEquals(v2.id, AmdLifecycle.current(s3.amds, "p1")?.id)
        assertEquals(AmdStatus.SUPERSEDED, s3.amds.first { it.id == "a1" }.status)
        assertTrue(s3.audit.verify())
    }

    @Test fun editingAGeneratedDocumentReturnsItToDraft() {
        val generated = AmdLifecycle.generate(ready(), now, today)
        val edited = AmdLifecycle.edit(generated, now) { it.copy(answers = it.answers.copy(religiousWishes = "Gita path")) }
        assertEquals(AmdStatus.DRAFT, edited.status); assertNull(edited.generatedAt)
    }

    @Test fun revokeAndEdSummaryCarriesCaveat() {
        assertEquals("No living will recorded", AmdLifecycle.edSummary(emptyList(), "p1").en)
        val generated = AmdLifecycle.generate(ready(), now, today)
        assertTrue(AmdLifecycle.edSummary(listOf(generated), "p1").en.contains("not valid"))
        val executed = AmdLifecycle.execute(listOf(generated), "a1", scan, witnesses, attestor, now)
        val summary = AmdLifecycle.edSummary(executed, "p1")
        assertTrue(summary.en.contains("Executed")); assertTrue(summary.en.contains(AmdText.CAVEAT.en)); assertTrue(summary.en.contains("governs"))
        assertTrue(HindiScan.hasHindi(summary.hi))
        val revoked = AmdLifecycle.revoke(executed, "a1", "Changed my mind", now)
        assertEquals(AmdStatus.REVOKED, revoked.single().status)
        assertEquals("No living will recorded", AmdLifecycle.edSummary(revoked, "p1").en)
        try { AmdLifecycle.revoke(executed, "a1", " ", now); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun jsonRoundTrip() {
        val executed = AmdLifecycle.execute(listOf(AmdLifecycle.generate(ready().copy(answers = ready().answers.copy(organDonation = true)), now, today)), "a1", scan, witnesses, attestor, now).single()
        assertEquals(executed, AmdJson.decode(JSONObject(AmdJson.encode(executed).toString())))
        assertEquals(ready(), AmdJson.decode(AmdJson.encode(ready())))
        assertEquals(1, AmdJson.encode(ready()).getJSONArray("dhrs").getJSONObject(0).getInt("order"))
    }

    @Test fun sha256OfScan() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AmdLifecycle.sha256("abc".toByteArray()))
    }

    @Test fun everyLabelHasHindi() {
        (AmdSituation.entries.map(AmdText::situation) + AmdTreatment.entries.map(AmdText::treatment) + PlaceOfCare.entries.map(AmdText::place) + AttestorKind.entries.map(AmdText::attestor) +
            AmdStatus.entries.map(AmdText::status) + listOf(AmdText.COMFORT, AmdText.TUBE_NOTE, AmdText.CAVEAT, AmdText.PAPER)).forEach { assertTrue(it.en, HindiScan.hasHindi(it.hi)) }
        (AmdRules.generateIssues(AmdLifecycle.newDraft("p", now, AmdMaker(dob = "2015-01-01", idLast4 = "123456")), today) + AmdRules.executeIssues(ready(), null, emptyList(), null)).forEach { assertTrue(it.id, HindiScan.hasHindi(it.messageHi)) }
    }
}
