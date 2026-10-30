package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CareLivingWillTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val now = "2026-10-29T10:00:00Z"
    private val hash = "a".repeat(64)
    private fun declared() = AmdDeclarations(capacity = true, consideredCarefully = true, voluntary = true, understandsNotAppliedIfUnclear = true)
    private fun content() = AmdContent(
        executant = AmdExecutant("Asha Rao", "Female", "1950-01-01", IdType.AADHAAR, "9012", "12 MG Road, Bengaluru"),
        declarations = declared(), conditions = listOf(AmdCondition.TERMINAL), treatments = mapOf(AmdTreatment.VENTILATION to AmdChoice.REFUSE, AmdTreatment.TUBE_FEEDING to AmdChoice.REFUSE, AmdTreatment.CPR to AmdChoice.ACCEPT),
        wishes = AmdWishes(placeOfCare = AmdPlaceOfCare.HOME),
        dhrs = listOf(AmdDhr(id = "d1", name = "Ravi Rao", relation = "Son", phone = "98450 00000", agreed = true), AmdDhr(id = "d2", name = "Lata Rao", relation = "Daughter", phone = "98450 11111", agreed = true)),
        familyPhysician = AmdFamilyPhysician("Dr Menon", "KMC-1"), custodianBody = "BBMP")
    private fun ready() = AmdLifecycle.newDraft("p1", now, content(), "Patient", id = "a1")
    private fun generated() = AmdLifecycle.markGenerated(ready(), "b".repeat(64), "en", now, today)
    private val execution = AmdExecution("2026-10-29", "Bengaluru", false, listOf(AmdWitness("Mohan K", "Bengaluru"), AmdWitness("Fatima S", "Bengaluru")), AmdAttestor(AttestorKind.NOTARY, "R. Shah", "", "Notary 44"), "amd-scan-a1.jpg", "amd-scan-a1.jpg", "image/jpeg", hash, now)
    private val pending = AmdCustodian(AmdCustodianStatus.PENDING, "BBMP")
    private fun ids(c: AmdContent) = AmdRules.validateContent(c, today).map { it.id }
    private fun blocks(e: AmdExecution, c: AmdCustodian? = pending) = AmdRules.blocking(AmdRules.validateExecution(generated(), e, c, today)).map { it.id }
    private fun executed() = AmdLifecycle.execute(listOf(generated()), "a1", execution, pending, now, today)

    // Acceptance: "DOB under 18 blocks an AMD"
    @Test fun l1_underEighteenAndMissingDobBlock() {
        assertTrue("L1_ADULT" in ids(content().copy(executant = content().executant.copy(dob = "2010-01-01"))))
        try { AmdLifecycle.markGenerated(ready().copy(content = content().copy(executant = content().executant.copy(dob = "2010-01-01"))), hash, "en", now, today); fail("generated for a minor") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("adult")) }
        assertTrue("L1_DOB" in ids(content().copy(executant = content().executant.copy(dob = ""))))
        assertFalse("L1_ADULT" in ids(content().copy(executant = content().executant.copy(dob = "2008-10-29"))))
    }

    @Test fun l1_everyDeclarationIsNeeded() {
        val base = content()
        assertTrue("L1_CAPACITY" in ids(base.copy(declarations = declared().copy(capacity = false))))
        assertTrue("L1_VOLUNTARY" in ids(base.copy(declarations = declared().copy(voluntary = false))))
        assertTrue("L1_CONSIDERED" in ids(base.copy(declarations = declared().copy(consideredCarefully = false))))
        assertTrue("L7_UNDERSTOOD" in ids(base.copy(declarations = declared().copy(understandsNotAppliedIfUnclear = false))))
    }

    @Test fun l2_atLeastOneRepresentativeAndWarnBelowTwo() {
        assertEquals(IssueSeverity.BLOCK, AmdRules.validateContent(content().copy(dhrs = listOf(AmdDhr())), today).first { it.id == "L2_DHR_REQUIRED" }.severity)
        assertEquals(IssueSeverity.WARN, AmdRules.validateContent(content().copy(dhrs = content().dhrs.take(1)), today).first { it.id == "L2_DHR_TWO" }.severity)
        assertTrue(AmdRules.blocking(AmdRules.validateContent(content(), today)).isEmpty())
        assertTrue("DHR_SELF" in ids(content().copy(dhrs = listOf(AmdDhr(name = "Smt. Asha Rao", relation = "Self", phone = "1")))))
        assertTrue("DHR_DUPLICATE" in ids(content().copy(dhrs = listOf(AmdDhr(name = "Ravi Rao", relation = "Son", phone = "1"), AmdDhr(name = "ravi rao", relation = "Son", phone = "2")))))
        assertTrue("DHR_PHONE" in ids(content().copy(dhrs = listOf(AmdDhr(name = "Ravi", relation = "Son")))))
        assertTrue("DHR_ADULT" in ids(content().copy(dhrs = listOf(AmdDhr(name = "Ravi", relation = "Son", phone = "1", dob = "2015-01-01")))))
    }

    @Test fun onlyLastFourDigitsAreKept() {
        assertEquals("9012", AmdIds.last4("1234-5678-9012"))
        assertTrue("ID_LAST4" in ids(content().copy(executant = content().executant.copy(idLast4 = "123456789012"))))
        assertTrue("ID_LAST4" in ids(content().copy(dhrs = listOf(AmdDhr(name = "Ravi", relation = "Son", phone = "1", idLast4 = "ABC")))))
        assertFalse(AmdJson.encode(ready()).toString().contains("12345678"))
        // The paper leaves the ID box blank for handwriting and shows only the last four.
        val paper = AmdDocument.plainText(AmdDocument.blocks(ready(), CareLang.EN, hash, today))
        assertTrue(paper.contains("2. ID no.: __________________ (ends 9012)"))
    }

    @Test fun needsACondition_treatmentChoiceIsOptional() {
        assertTrue("CONDITIONS" in ids(content().copy(conditions = emptyList())))
        assertEquals(IssueSeverity.WARN, AmdRules.validateContent(content().copy(treatments = emptyMap()), today).first { it.id == "NO_TREATMENT_CHOICE" }.severity)
    }

    @Test fun cannotAskForAnythingMeantToEndLife() {
        assertTrue("NO_EUTHANASIA" in ids(content().copy(values = "Please give a lethal injection")))
        assertTrue("NO_EUTHANASIA" in ids(content().copy(afterDeath = AmdAfterDeath(lastRites = "help me die"))))
    }

    // Acceptance: "an AMD stays Generated until scan, witnesses and attestor are recorded"
    @Test fun l3_staysPrintedUntilEverythingIsRecorded() {
        val g = generated()
        assertEquals(AmdStatus.GENERATED, g.status); assertNotNull(g.contentSha256); assertEquals(64, g.contentSha256!!.length)
        assertTrue("L3_SCAN" in blocks(execution.copy(scanSha256 = "not-a-hash")))
        assertTrue("L3_SCAN" in blocks(execution.copy(scanKey = "")))
        assertTrue("L3_WITNESS" in blocks(execution.copy(witnesses = execution.witnesses.take(1))))
        assertTrue("WITNESS_SAME" in blocks(execution.copy(witnesses = listOf(execution.witnesses[0], execution.witnesses[0].copy(address = "x")))))
        assertTrue("WITNESS_SELF" in blocks(execution.copy(witnesses = listOf(AmdWitness("Asha Rao"), execution.witnesses[1]))))
        assertTrue("L3_ATTESTOR_KIND" in blocks(execution.copy(attestor = execution.attestor.copy(kind = null))))
        assertTrue("L3_ATTESTOR" in blocks(execution.copy(attestor = execution.attestor.copy(name = ""))))
        assertTrue("ATTESTOR_INDEPENDENT" in blocks(execution.copy(attestor = execution.attestor.copy(name = "Ravi Rao"))))
        assertTrue("SIGNED_FUTURE" in blocks(execution.copy(signedOn = "2030-01-01")))
        assertTrue("SIGNED_BEFORE_PRINT" in blocks(execution.copy(signedOn = "2026-10-01")))
        assertTrue("L5_CUSTODIAN" in blocks(execution, null))
        assertTrue("L5_CUSTODIAN_DETAIL" in blocks(execution, AmdCustodian(AmdCustodianStatus.ACKNOWLEDGED, "BBMP")))
        try { AmdLifecycle.execute(listOf(g), "a1", execution.copy(scanKey = ""), pending, now, today); fail() } catch (_: IllegalStateException) {}
        val e = executed().single()
        assertEquals(AmdStatus.EXECUTED, e.status); assertEquals(hash, e.execution?.scanSha256)
        assertEquals(AmdCustodianStatus.PENDING, e.custodian?.status)
        // A draft cannot jump to Executed.
        assertTrue(AmdRules.validateExecution(ready(), execution, pending, today).any { it.id == "NOT_GENERATED" })
    }

    @Test fun warnsWhenAWitnessIsARepresentative() {
        val issues = AmdRules.validateExecution(generated(), execution.copy(witnesses = listOf(AmdWitness(" ravi  rao "), execution.witnesses[1])), pending, today)
        assertEquals(IssueSeverity.WARN, issues.single { it.id == "L3_WITNESS_IS_DHR" }.severity)
    }

    @Test fun copiesNeedTheirOwnConsent() {
        val e = executed().single()
        assertEquals(listOf(AmdRecipientKind.DHR, AmdRecipientKind.DHR, AmdRecipientKind.FAMILY_PHYSICIAN, AmdRecipientKind.CUSTODIAN), e.copies.map { it.kind })
        val first = e.copies.first()
        try { AmdLifecycle.updateCopy(e, first.id, given = true, now = now, today = today); fail("given without consent") } catch (_: IllegalStateException) {}
        val agreed = AmdLifecycle.updateCopy(e, first.id, consent = true, now = now, today = today)
        assertEquals(now, agreed.copies.first().consentAt); assertFalse(agreed.copies[1].consent)
        val given = AmdLifecycle.updateCopy(agreed, first.id, given = true, method = AmdCopyMethod.POST, now = now, today = today)
        assertEquals(today.toString(), given.copies.first().givenOn); assertEquals(AmdCopyMethod.POST, given.copies.first().method)
        val withdrawn = AmdLifecycle.updateCopy(given, first.id, consent = false, now = now, today = today).copies.first()
        assertFalse(withdrawn.given); assertEquals("", withdrawn.givenOn); assertNull(withdrawn.method)
        try { AmdLifecycle.updateCopy(ready(), "x", consent = true, now = now, today = today); fail() } catch (_: IllegalStateException) {}
    }

    @Test fun custodianCanBePendingThenAcknowledged() {
        val e = executed().single()
        try { AmdLifecycle.updateCustodian(e, AmdCustodian(AmdCustodianStatus.ACKNOWLEDGED, "BBMP"), now); fail() } catch (_: IllegalStateException) {}
        assertEquals("Mr Rao", AmdLifecycle.updateCustodian(e, AmdCustodian(AmdCustodianStatus.ACKNOWLEDGED, "BBMP", "Mr Rao", "2026-10-29"), now).custodian?.officer)
    }

    @Test fun abhaNeedsConsentAndAValidAddress() {
        val e = executed().single()
        assertEquals("ABHA_CONSENT", AmdRules.abhaIssue("name@abdm", false)?.id)
        assertEquals("ABHA_FORMAT", AmdRules.abhaIssue("not an address", true)?.id)
        assertNull(AmdRules.abhaIssue("12-3456-7890-1234", true)); assertNull(AmdRules.abhaIssue("", false))
        assertEquals("name@abdm", AmdLifecycle.updateAbha(e, " name@abdm ", true, now, today).abha?.address)
        assertNull(AmdLifecycle.updateAbha(e, "", false, now, today).abha)
    }

    @Test fun l6_changesAreNewVersionsAndLatestExecutedIsCurrent() {
        var s = CarePlanState(amds = listOf(ready()))
        s = CarePlanBook.generateAmd(s, "a1", hash, "en", "fam", now, today)
        try { AmdLifecycle.update(s.amds.single(), content(), now); fail("a printed version changed") } catch (_: IllegalStateException) {}
        s = CarePlanBook.executeAmd(s, "a1", execution, pending, "fam", now, today)
        val (s2, v2) = CarePlanBook.startAmd(s, "p1", "fam", now)
        assertEquals(2, v2.version); assertNull(v2.execution); assertEquals(content().executant.name, v2.content.executant.name)
        // v1 stays current until v2 is executed.
        assertEquals("a1", AmdLifecycle.current(s2.amds, "p1")?.id)
        var s3 = CarePlanBook.saveAmd(s2, v2.id, v2.content.copy(values = "Prayers at the bedside"), "fam", now)
        s3 = CarePlanBook.generateAmd(s3, v2.id, hash, "en", "fam", now, today)
        s3 = CarePlanBook.executeAmd(s3, v2.id, execution.copy(scanSha256 = "b".repeat(64)), pending, "fam", now, today)
        assertEquals(v2.id, AmdLifecycle.current(s3.amds, "p1")?.id)
        assertEquals(AmdStatus.SUPERSEDED, s3.amds.first { it.id == "a1" }.status)
        assertTrue(s3.audit.verify())
    }

    @Test fun aPrintedVersionIsSetAsideByANewOne() {
        val (records, next) = AmdLifecycle.start(listOf(generated()), "p1", "fam", now)
        assertEquals(AmdStatus.SUPERSEDED, records.first { it.id == "a1" }.status); assertEquals(AmdStatus.DRAFT, next.status); assertEquals(2, next.version)
    }

    @Test fun cancelledInWritingAndEdSummaryCarriesCaveat() {
        assertEquals(NOT_RECORDED.en, AmdLifecycle.edLines(null, null).first.single().en)
        assertTrue(AmdLifecycle.edSummary(emptyList(), "p1").en.endsWith(AmdText.CAVEAT.en))
        assertTrue(AmdLifecycle.edSummary(listOf(generated()), "p1").en.contains("not signed and attested, so it is not valid"))
        val records = executed()
        val summary = AmdLifecycle.edSummary(records, "p1")
        assertTrue(summary.en, summary.en.contains("executed in Sahara")); assertTrue(summary.en.contains("Notary: R. Shah")); assertTrue(summary.en.contains(AmdText.GOVERNS.en)); assertTrue(summary.en.contains(AmdText.CAVEAT.en))
        assertTrue(HindiScan.hasHindi(summary.hi)); assertTrue(AmdLifecycle.governs(records, "p1", null))
        try { AmdLifecycle.revoke(records, "p1", "2026-10-29", false, "", now, today); fail("no written cancellation") } catch (_: IllegalStateException) {}
        val revoked = AmdLifecycle.revoke(records, "p1", "2026-10-29", true, "Changed my mind", now, today)
        assertEquals(AmdStatus.REVOKED, revoked.single().status)
        assertTrue(AmdLifecycle.edSummary(revoked, "p1").en.contains("cancelled in writing")); assertFalse(AmdLifecycle.governs(revoked, "p1", null))
        // A valid paper recorded on the plan's own form also governs.
        assertTrue(AmdLifecycle.governs(emptyList(), "p1", ExistingDirective(DirectiveKind.VALID_AMD, "2020-01-01", "Home", "Notary")))
    }

    @Test fun deletingADraftLeavesSignedVersions() {
        val (kept, gone) = AmdLifecycle.withoutUnsigned(executed() + ready().copy(id = "a2", version = 2), "p1")
        assertEquals(listOf("a1"), kept.map { it.id }); assertEquals(listOf("a2"), gone.map { it.id })
    }

    @Test fun jsonRoundTripInTheWebShape() {
        val e = AmdLifecycle.updateAbha(executed().single(), "name@abdm", true, now, today)
        assertEquals(e, AmdJson.decode(JSONObject(AmdJson.encode(e).toString())))
        assertEquals(ready(), AmdJson.decode(JSONObject(AmdJson.encode(ready()).toString())))
        val j = AmdJson.encode(ready()).getJSONObject("content")
        assertEquals("REFUSE", j.getJSONObject("treatments").getString("VENTILATION")); assertTrue(j.getJSONObject("treatments").isNull("DIALYSIS")); assertTrue(j.getBoolean("comfortCareAlways"))
    }

    @Test fun anEarlierTestBuildsRecordComesBackAsADraftWithoutInventedChoices() {
        val old = JSONObject("""{"id":"o1","patientId":"p1","status":"EXECUTED","createdAt":"$now","updatedAt":"$now","maker":{"name":"Asha","dob":"1950-01-01","address":"Home","idLast4":"9012"},
            "answers":{"situations":["TERMINAL_ILLNESS","ADVANCED_IRREVERSIBLE_DEMENTIA"],"refusedTreatments":["CPR","ICU_ADMISSION"],"organDonation":true},"dhrs":[{"order":1,"name":"Ravi","relation":"Son","phone":"1"}]}""")
        val r = AmdJson.decode(old)
        assertEquals(AmdStatus.DRAFT, r.status); assertEquals(listOf(AmdCondition.TERMINAL), r.content.conditions); assertEquals(mapOf(AmdTreatment.CPR to AmdChoice.REFUSE), r.content.treatments); assertFalse(r.content.afterDeath.organDonor)
        assertEquals("Ravi", r.content.dhrs.single().name)
    }

    @Test fun nothingInventedRemains() {
        assertEquals(setOf("TERMINAL", "END_STAGE", "VEGETATIVE"), AmdCondition.entries.map { it.name }.toSet())
        assertEquals(setOf("CPR", "VENTILATION", "DIALYSIS", "TUBE_FEEDING", "IV_FLUIDS_ANTIBIOTICS", "CHEMOTHERAPY", "RADIOTHERAPY", "SURGERY"), AmdTreatment.entries.map { it.name }.toSet())
        val paper = AmdDocument.plainText(AmdDocument.blocks(ready(), CareLang.EN, hash, today)).lowercase()
        assertFalse(paper.contains("dementia"))
        // Organ donation IS in the Vidhi model (Section 5): it appears only if ticked.
        assertFalse(paper.contains("organ donor"))
        val ticked = AmdDocument.plainText(AmdDocument.blocks(ready().copy(content = content().copy(afterDeath = AmdAfterDeath(organDonor = true))), CareLang.EN, hash, today))
        assertTrue(ticked.contains(VidhiText.ORGAN)); assertTrue(ticked.contains("(or to facilitate organ or body donation)"))
    }

    /** The paper's fixed sentences are word for word the web's prototype/app/amd-pdf.ts and amd-rules.ts. */
    @Test fun vidhiTextIsTheSameAsTheWeb() {
        val dir = listOf("../prototype/app", "../../prototype/app").map(::File).firstOrNull { it.isDirectory } ?: return
        val pdf = File(dir, "amd-pdf.ts").readText(); val rules = File(dir, "amd-rules.ts").readText()
        VidhiText.ALL.forEach { assertTrue(it, pdf.contains(it)) }
        AmdTreatment.entries.forEach { t -> val (refuse, accept) = VidhiText.treatment(t); assertTrue(refuse, rules.contains(refuse)); assertTrue(accept, rules.contains(accept)) }
        assertTrue(rules.contains(AmdText.CAVEAT.en)); assertTrue(rules.contains(AmdText.CAVEAT.hi))
        AmdText.BOARD_PATHWAY.forEach { assertTrue(it.en, rules.contains(it.en)); assertTrue(it.hi, rules.contains(it.hi)) }
    }

    @Test fun thePaperFollowsTheVidhiOrderAndLeavesSignaturesBlank() {
        val text = AmdDocument.plainText(AmdDocument.blocks(ready(), CareLang.EN, hash, today))
        val order = listOf("Section 1: Personal details", "Section 2:", "Section 3 (Optional)", "Section 4:", "Section 5 (Optional)", "Declaration", "Validity of directive", "Signature of the executor", "Signature of witnesses", "Notarisation / Attestation", "Document record")
        val positions = order.map { text.indexOf(it) }
        assertTrue(positions.toString(), positions.all { it >= 0 } && positions == positions.sorted())
        assertTrue(text.contains("There is no reasonable medical probability of recovery from a terminal condition, and"))
        assertTrue(text.contains("I have nominated two (2) designated healthcare representative(s)"))
        assertTrue(text.contains("Content SHA-256: $hash"))
        assertTrue(text.contains("the Competent officer/custodian of BBMP"))
        // Hindi headings sit under the English, which stays the valid text.
        val hindi = AmdDocument.blocks(ready(), CareLang.HI, hash, today).filterIsInstance<AmdBlock.Heading>().first()
        assertEquals("Section 1: Personal details", hindi.text); assertEquals("खंड 1: व्यक्तिगत विवरण", hindi.translated)
    }

    @Test fun boardPathwayHasTheReconsiderationPeriodAndLegalReviewWording() {
        assertTrue(AmdText.RECONSIDERATION.en.contains("30 days")); assertTrue(AmdText.RECONSIDERATION.en.contains("para 322(vi)"))
        assertTrue(AmdText.BOARD_PATHWAY.any { it.en.contains("30-day reconsideration period") })
        assertEquals("Legal review pending", AmdText.LEGAL_PENDING_TITLE.en); assertTrue(AmdText.BOARD_PATHWAY.first().en.contains("(Legal review pending.)"))
    }

    @Test fun sha256OfScan() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AmdLifecycle.sha256("abc".toByteArray()))
        assertEquals(AmdLifecycle.contentHash(ready()), AmdLifecycle.contentHash(ready().copy(updatedAt = "later")))
        assertNotEquals(AmdLifecycle.contentHash(ready()), AmdLifecycle.contentHash(ready().copy(content = content().copy(values = "x"))))
    }

    @Test fun everyLabelHasHindi() {
        (AmdCondition.entries.map(AmdText::condition) + AmdTreatment.entries.map(AmdText::treatment) + AmdTreatment.entries.map(AmdText::treatmentHelp) + AmdPlaceOfCare.entries.map(AmdText::place) + AttestorKind.entries.map(AmdText::attestor) +
            AmdStatus.entries.map(AmdText::status) + AmdRecipientKind.entries.map(AmdText::copyKind) + AmdCopyMethod.entries.map(AmdText::copyMethod) + AmdText.BOARD_PATHWAY + listOf(AmdText.COMFORT, AmdText.TUBE_NOTE, AmdText.CAVEAT, AmdText.PAPER, AmdText.GOVERNS, AmdText.LEGAL_PENDING, AmdText.RECONSIDERATION)).forEach { assertTrue(it.en, HindiScan.hasHindi(it.hi)) }
        (AmdRules.validateContent(AmdContent(executant = AmdExecutant(dob = "2015-01-01", idLast4 = "123456")), today) + AmdRules.validateExecution(ready(), AmdExecution(), null, today)).forEach { assertTrue(it.id, HindiScan.hasHindi(it.messageHi)) }
    }
}
