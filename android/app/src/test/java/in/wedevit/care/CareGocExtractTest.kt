package `in`.wedevit.care

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CareGocExtractTest {
    @Before @After fun english() { CareLanguageState.current = CareLang.EN }

    private fun options(text: String, d: GocDecision) = GocExtract.extract(text).hitsFor(d).flatMap { it.options }
    private fun assertNoChoice(text: String, d: GocDecision) {
        val found = GocExtract.extract(text)
        assertTrue("$text should mark nothing for $d, got ${found.hitsFor(d)}", found.hitsFor(d).isEmpty())
        assertTrue("$text should count as raised", found.discussed(d))
    }
    private val now = "2026-11-01T10:00:00Z"
    private fun draft() = EctprLifecycle.newDraft("p1", "Dr Rao", now, EctprPatient(name = "Asha Rao", uhid = "UH-1"), id = "r1")

    // English: yes, no, and everything that only looks like one.
    @Test fun english_saysYesAndNo() {
        assertEquals(listOf(DialysisKind.NO.name), options("We do not want dialysis.", GocDecision.DIALYSIS))
        assertEquals(listOf(DialysisKind.YES.name), options("We want dialysis.", GocDecision.DIALYSIS))
        assertEquals(listOf(IcuKind.NO.name), options("He refuses ICU.", GocDecision.ICU))
        assertEquals(listOf(IcuKind.NO.name), options("No ICU.", GocDecision.ICU))
        assertEquals(listOf(DialysisKind.NO.name), options("We don't want dialysis.", GocDecision.DIALYSIS))
    }
    @Test fun english_negationStaysWithItsOwnTopic() {
        val found = GocExtract.extract("We want dialysis, no ICU.")
        assertEquals(listOf(DialysisKind.YES.name), found.hitsFor(GocDecision.DIALYSIS).flatMap { it.options })
        assertEquals(listOf(IcuKind.NO.name), found.hitsFor(GocDecision.ICU).flatMap { it.options })
        val joined = GocExtract.extract("We want dialysis and no ICU.")
        assertEquals(listOf(DialysisKind.YES.name), joined.hitsFor(GocDecision.DIALYSIS).flatMap { it.options })
        assertEquals(listOf(IcuKind.NO.name), joined.hitsFor(GocDecision.ICU).flatMap { it.options })
    }
    @Test fun aQuestionIsNotAChoice_butItsShortAnswerIs() {
        assertNoChoice("Do you want CPR?", GocDecision.CPR)
        assertNoChoice("Do you want CPR", GocDecision.CPR)
        val answered = GocExtract.extract("Do you want CPR? No.").hitsFor(GocDecision.CPR).single()
        assertEquals(listOf(Cpr.DNACPR.name), answered.options)
        assertTrue(answered.quote, answered.quote.contains("Do you want CPR?") && answered.quote.contains("No."))
        assertEquals(listOf(Cpr.ATTEMPT.name), options("Do you want CPR? Yes please.", GocDecision.CPR))
    }
    @Test fun anAnswerMustBeOnlyAnAnswer() {
        assertTrue(GocExtract.extract("We talked about dialysis and no pain.").hitsFor(GocDecision.DIALYSIS).isEmpty())
    }
    @Test fun notSureIsNotNo() {
        assertNoChoice("I am not sure about dialysis.", GocDecision.DIALYSIS)
        assertNoChoice("We don't know about the ventilator yet.", GocDecision.BREATHING)
        assertNoChoice("Some patients choose dialysis.", GocDecision.DIALYSIS)
    }
    @Test fun twoNegativesAreNotRead_andNoObjectionIsYes() {
        assertNoChoice("We do not want to stop dialysis.", GocDecision.DIALYSIS)
        assertEquals(listOf(DialysisKind.YES.name), options("No objection to dialysis.", GocDecision.DIALYSIS))
    }
    @Test fun aBareMentionMarksNoOption() {
        assertNoChoice("The doctor explained the ventilator.", GocDecision.BREATHING)
        assertNoChoice("Dialysis was explained.", GocDecision.DIALYSIS)
    }
    @Test fun wholePhrasesDecideOnTheirOwn_andCanBeNegated() {
        assertEquals(listOf(Cpr.DNACPR.name), options("Do not resuscitate.", GocDecision.CPR))
        assertEquals(listOf(Cpr.DNACPR.name), options("He has a DNR.", GocDecision.CPR))
        assertNoChoice("It is not a DNR.", GocDecision.CPR)
        assertEquals(listOf(TransferKind.NO.name), options("She wants to stay at home.", GocDecision.TRANSFER))
        assertEquals(listOf(GoalOfCare.C_COMFORT.name), options("Comfort care only please.", GocDecision.GOAL))
        assertEquals(listOf(GoalOfCare.A_LIFE.name), options("Please do everything.", GocDecision.GOAL))
    }
    @Test fun qualifiedAndTrialOptions() {
        assertEquals(listOf(DialysisKind.ONLY_IF_REVERSIBLE.name), options("No dialysis unless it is reversible.", GocDecision.DIALYSIS))
        assertEquals(listOf(DialysisKind.TRIAL.name), options("Dialysis for a few days as a trial.", GocDecision.DIALYSIS))
        assertEquals(listOf(TransferKind.ONLY_FOR.name), options("Hospital only for pain.", GocDecision.TRANSFER))
    }
    @Test fun noVentilatorRulesItOutAndMarksNothingElse() {
        val found = GocExtract.extract("No ventilator but BiPAP is okay.")
        assertEquals(setOf("4"), found.suggested(GocDecision.BREATHING).keys)
        assertEquals(setOf("5"), found.ruledOut(GocDecision.BREATHING).keys)
        val alone = GocExtract.extract("No ventilator.")
        assertTrue(alone.suggested(GocDecision.BREATHING).isEmpty())
        assertEquals(setOf("5"), alone.ruledOut(GocDecision.BREATHING).keys)
    }
    @Test fun differentThingsSaidAreBothMarked() {
        val found = GocExtract.extract("I do not want ICU. My son wants ICU.")
        assertEquals(setOf(IcuKind.NO.name, IcuKind.YES.name), found.suggested(GocDecision.ICU).keys)
    }
    @Test fun anAmbiguousYesMarksAllTheOptionsItFits() {
        assertEquals(setOf(VasopressorKind.YES_NO_LIMIT.name, VasopressorKind.YES_MAX_AGENTS.name), GocExtract.extract("We want vasopressors.").suggested(GocDecision.VASOPRESSORS).keys)
        assertEquals(setOf(Hydration.ORAL_SIPS.name), GocExtract.extract("No drip.").suggested(GocDecision.HYDRATION).keys)
    }
    @Test fun aFeedingTubeBelongsToBothTubes() {
        val found = GocExtract.extract("No feeding tube.")
        assertEquals(listOf(NgKind.NO.name), found.hitsFor(GocDecision.FEEDING_NG).flatMap { it.options })
        assertEquals(listOf(PegFeeding.NO.name), found.hitsFor(GocDecision.FEEDING_PEG).flatMap { it.options })
    }
    @Test fun aQuoteIsTheSentenceAsWritten() {
        val hit = GocExtract.extract("We met today. Amma does NOT want Dialysis, please.").hitsFor(GocDecision.DIALYSIS).single()
        assertEquals("Amma does NOT want Dialysis, please.", hit.quote)
    }
    @Test fun values() {
        val found = GocExtract.extract("What matters most is being with family. Being a burden is unacceptable. Is it important to you?")
        assertEquals(setOf(GOC_VALUES_OWN_WORDS, GOC_VALUES_NOT_ACCEPTED), found.suggested(GocDecision.VALUES).keys)
        assertTrue(GocExtract.extract("It is not important to me.").hitsFor(GocDecision.VALUES).isEmpty())
    }

    // Hindi, Devanagari and Roman.
    @Test fun hindi() {
        assertEquals(listOf(DialysisKind.NO.name), options("डायलिसिस नहीं चाहिए।", GocDecision.DIALYSIS))
        assertEquals(listOf(DialysisKind.YES.name), options("डायलिसिस चाहिए।", GocDecision.DIALYSIS))
        assertEquals(listOf(IcuKind.NO.name), options("आईसीयू में मत ले जाइए।", GocDecision.ICU))
        assertEquals(listOf(Cpr.DNACPR.name), options("सीपीआर न करें।", GocDecision.CPR))
        assertEquals(listOf(DialysisKind.NO.name), options("dialysis nahi chahiye", GocDecision.DIALYSIS))
        assertEquals(setOf("5"), GocExtract.extract("क्या आप वेंटिलेटर चाहते हैं? नहीं।").ruledOut(GocDecision.BREATHING).keys)
        assertNoChoice("क्या आप डायलिसिस चाहते हैं?", GocDecision.DIALYSIS)
        assertNoChoice("डायलिसिस के बारे में पता नहीं।", GocDecision.DIALYSIS)
        // A spelling with or without nukta or chandrabindu is the same word.
        assertEquals(listOf(TransferKind.NO.name), options("घर पर ही रहना है।", GocDecision.TRANSFER))
    }
    @Test fun telugu() {
        assertEquals(listOf(DialysisKind.NO.name), options("డయాలసిస్ వద్దు", GocDecision.DIALYSIS))
        assertEquals(listOf(DialysisKind.YES.name), options("డయాలసిస్ కావాలి", GocDecision.DIALYSIS))
        assertEquals(setOf("5"), GocExtract.extract("వెంటిలేటర్ వద్దు").ruledOut(GocDecision.BREATHING).keys)
        assertEquals(listOf(Cpr.DNACPR.name), options("సీపీఆర్ చేయవద్దు", GocDecision.CPR))
        assertEquals(listOf(IcuKind.NO.name), options("ఐసీయూలో పెట్టకండి", GocDecision.ICU))
        assertNoChoice("ఐసీయూ కావాలా?", GocDecision.ICU)
        assertNoChoice("డయాలసిస్ గురించి తెలియదు", GocDecision.DIALYSIS)
    }
    @Test fun kannada() {
        assertEquals(listOf(DialysisKind.NO.name), options("ಡಯಾಲಿಸಿಸ್ ಬೇಡ", GocDecision.DIALYSIS))
        assertEquals(listOf(DialysisKind.YES.name), options("ಡಯಾಲಿಸಿಸ್ ಬೇಕು", GocDecision.DIALYSIS))
        assertEquals(listOf(DialysisKind.NO.name), options("ಡಯಾಲಿಸಿಸ್ ಬೇಕಾಗಿಲ್ಲ", GocDecision.DIALYSIS))
        assertEquals(listOf(Cpr.DNACPR.name), options("ಸಿಪಿಆರ್ ಮಾಡಬೇಡಿ", GocDecision.CPR))
        assertEquals(listOf(IcuKind.NO.name), options("ಐಸಿಯು ಬೇಡ", GocDecision.ICU))
        assertNoChoice("ಡಯಾಲಿಸಿಸ್ ಬೇಕಾ?", GocDecision.DIALYSIS)
        assertNoChoice("ಡಯಾಲಿಸಿಸ್ ಬಗ್ಗೆ ಗೊತ್ತಿಲ್ಲ", GocDecision.DIALYSIS)
    }
    @Test fun urdu() {
        assertEquals(listOf(DialysisKind.NO.name), options("ڈائیلیسس نہیں چاہیے", GocDecision.DIALYSIS))
        assertEquals(listOf(DialysisKind.YES.name), options("ڈائیلیسس چاہیے", GocDecision.DIALYSIS))
        // The same words typed with Arabic letter variants.
        assertEquals(listOf(DialysisKind.NO.name), options("ڈائیلیسس نهيں چاہیے", GocDecision.DIALYSIS))
        assertEquals(setOf("5"), GocExtract.extract("وینٹی لیٹر نہیں").ruledOut(GocDecision.BREATHING).keys)
        assertNoChoice("کیا آپ ڈائیلیسس چاہتے ہیں؟", GocDecision.DIALYSIS)
        assertNoChoice("ڈائیلیسس کے بارے میں معلوم نہیں", GocDecision.DIALYSIS)
    }

    // Rule V1: not discussed stays "No preference recorded"; only a confirmed item changes the plan.
    @Test fun notDiscussed_marksNothing_andChecklistStartsAtIcu() {
        val empty = GocExtract.extract("We spoke about the weather.")
        assertTrue(empty.hits.isEmpty() && empty.mentions.isEmpty())
        assertEquals(GocDecision.ICU, GocPlan.next(empty, emptySet(), emptyMap()))
        GocPlan.ORDER.forEach { assertEquals(GocChecklistStatus.NOT_DISCUSSED, GocPlan.status(it, empty, emptySet(), emptyMap())) }
        assertTrue(GocExtract.extract("").hits.isEmpty())
    }
    @Test fun checklistFollowsTheOrderAskedFor() {
        assertEquals(listOf(GocDecision.ICU, GocDecision.BREATHING, GocDecision.DIALYSIS), GocPlan.ORDER.take(3))
        val afterIcu = GocExtract.extract("We want ICU.")
        assertEquals(GocDecision.BREATHING, GocPlan.next(afterIcu, emptySet(), emptyMap()))
        assertEquals(GocDecision.DIALYSIS, GocPlan.next(afterIcu, setOf(GocDecision.BREATHING), emptyMap()))
    }
    @Test fun everyDecisionOffersItsWholeFormList() {
        assertEquals(IcuKind.entries.size, GocPlan.options(GocDecision.ICU).size)
        assertEquals(6, GocPlan.options(GocDecision.BREATHING).size)
        assertEquals(VasopressorKind.entries.size, GocPlan.options(GocDecision.VASOPRESSORS).size)
        assertEquals(AmbulanceOption.entries.size, GocPlan.options(GocDecision.AMBULANCE).size)
        GocPlan.ORDER.filter { it != GocDecision.VALUES }.forEach { assertNull(GocPlan.current(draft(), it)) }
    }
    @Test fun applyChangesOneTopicOnly_andNullClearsIt() {
        val blank = draft()
        val icu = GocPlan.apply(blank, GocDecision.ICU, IcuKind.NO.name)
        assertEquals(blank.choices.copy(icu = Icu(IcuKind.NO)), icu.choices)
        assertEquals(IcuKind.NO.name, GocPlan.current(icu, GocDecision.ICU))
        assertNull(GocPlan.apply(icu, GocDecision.ICU, null).choices.icu)
        assertEquals(1, GocPlan.apply(blank, GocDecision.DIALYSIS, DialysisKind.TRIAL.name).choices.dialysis?.trial?.let { 1 })
        assertEquals(3, GocPlan.apply(blank, GocDecision.BREATHING, "3").choices.respCeiling)
        GocPlan.ORDER.filter { it != GocDecision.VALUES }.forEach { d -> GocPlan.options(d).forEach { o -> assertEquals(o.id, GocPlan.current(GocPlan.apply(blank, d, o.id), d)) } }
    }
    @Test fun applyValuesAddsTheWords_andChoosesNoTreatment() {
        val r = GocPlan.apply(draft(), GocDecision.VALUES, GOC_VALUES_OWN_WORDS, listOf("Being with family."))
        assertEquals("Being with family.", r.values.ownWords)
        assertFalse(r.choices.anyChosen)
        assertEquals(listOf("A burden."), GocPlan.apply(draft(), GocDecision.VALUES, GOC_VALUES_NOT_ACCEPTED, listOf("A burden.")).values.unacceptableOutcomes)
        assertEquals(draft(), GocPlan.apply(draft(), GocDecision.VALUES, null))
    }
    @Test fun theRulesStillCheckAConfirmedChoice() {
        val understood = draft().copy(prognosticUnderstanding = PrognosticUnderstanding(true, "Dr Rao", "2026-11-01"))
        val r = GocPlan.apply(GocPlan.apply(understood, GocDecision.GOAL, GoalOfCare.C_COMFORT.name), GocDecision.CPR, Cpr.ATTEMPT.name)
        assertTrue("V2" in EctprRules.saveBlockers(r, LocalDate.of(2026, 11, 1)).map { it.id })
    }

    // Doctor side or patient party.
    @Test fun linesAreLabelledOnlyBySide_andCanBeCorrected() {
        val lines = GocLines.fromText("Doctor: Do you want dialysis?\nSon: No.\nWe spoke for a while.")
        assertEquals(GocParty.DOCTOR_SIDE, lines[0].party); assertFalse(lines[0].guessed)
        assertEquals(GocParty.PATIENT_PARTY, lines[1].party); assertFalse(lines[1].guessed)
        assertEquals(GocParty.UNSET, lines[2].party)
        val guessed = GocLines.fromText("Do you want ICU? No thank you.")
        assertEquals(listOf(GocParty.DOCTOR_SIDE, GocParty.PATIENT_PARTY), guessed.map { it.party })
        assertTrue(guessed.all { it.guessed })
        assertEquals(GocParty.DOCTOR_SIDE, GocLines.nextParty(GocParty.UNSET))
        assertEquals(GocParty.PATIENT_PARTY, GocLines.nextParty(GocParty.DOCTOR_SIDE))
        assertEquals(GocParty.UNSET, GocLines.nextParty(GocParty.PATIENT_PARTY))
    }
    @Test fun aQuoteKnowsWhichSideSaidIt() {
        val lines = listOf(GocLine("We can talk about the ICU.", GocParty.DOCTOR_SIDE), GocLine("We do not want ICU.", GocParty.PATIENT_PARTY))
        val hit = GocExtract.extract(GocLines.text(lines)).hitsFor(GocDecision.ICU).single()
        assertEquals(GocParty.PATIENT_PARTY, GocLines.partyAt(lines, hit.start))
    }
    @Test fun keysFromOneAudioPieceAreStable() {
        assertEquals(gocSegmentKey("a", 0) + ":0", GocLines.fromText("One.", gocSegmentKey("a", 0)).single().key)
    }

    // The note: content, no emotion, and the verbatim words kept apart.
    @Test fun theNoteStatesDecisionsAndSaysWhatWasNotDiscussed() {
        val r = GocPlan.apply(draft(), GocDecision.ICU, IcuKind.NO.name)
        val topics = mapOf(GocDecision.ICU to GocTopicRecord(GocDecision.ICU, GocTopicStatus.CONFIRMED, IcuKind.NO.name, "We are so scared, no ICU please."),
            GocDecision.DIALYSIS to GocTopicRecord(GocDecision.DIALYSIS, GocTopicStatus.NOT_DISCUSSED_VERIFIED))
        val note = GocNote.draft(r, topics, "Dr Rao\nSon", false)
        assertTrue(note, note.contains("Decision taken"))
        assertTrue(note, note.contains("Intensive care (ICU): " + PlanText.icu(IcuKind.NO).en))
        assertTrue(note, note.contains("Dialysis: Not discussed (doctor verified)"))
        assertTrue(note, note.contains("Stomach tube (PEG): Not discussed, not yet verified"))
        assertTrue(note, note.contains(NO_PREFERENCE.en))
        assertFalse("A spoken quote must not reach the note", note.contains("scared"))
        assertEquals(GocNoteKind.GENERAL_DISCUSSION, GocNote.kind(listOf(GocTopicRecord(GocDecision.DIALYSIS, GocTopicStatus.NOT_DISCUSSED_VERIFIED))))
    }
    @Test fun soundCaptionsStayOutOfTheNote() {
        assertEquals("Being home.", GocExtract.withoutSoundCaptions("[crying] Being home. (sobs)"))
        val note = GocNote.draft(draft().copy(values = PlanValues(ownWords = "[crying] Being home.")), emptyMap(), "", false)
        assertTrue(note, note.contains("Being home.") && !note.contains("crying"))
    }

    // Saved, and never on the card.
    private fun talk(version: Int = 1) = GocTranscript(listOf(GocLine("We do not want ICU.", GocParty.PATIENT_PARTY), GocLine("Thank you.")), "te", GocTranscript.SOURCE_RECORDED, OfflineWhisperModel.id, "Dr Rao\nSon", 1_793_000_000_000L, version,
        listOf(GocTopicRecord(GocDecision.ICU, GocTopicStatus.CONFIRMED, IcuKind.NO.name, "We do not want ICU.", now), GocTopicRecord(GocDecision.DIALYSIS, GocTopicStatus.NOT_DISCUSSED_VERIFIED, at = now)), "The note.", GocNoteKind.DECISION_TAKEN, now, "Dr Rao", false)

    @Test fun transcriptRoundTripsThroughThePlanJson() {
        val r = draft().copy(gocTranscript = talk())
        assertEquals(r, PlanJson.decode(PlanJson.encode(r)))
        assertEquals(draft(), PlanJson.decode(PlanJson.encode(draft())))
        assertFalse(PlanJson.encode(draft()).has(GOC_TRANSCRIPT_KEY))
    }
    @Test fun transcriptJsonHasTheWebShape() {
        val j = GocTranscriptJson.encode(talk())
        assertEquals("PATIENT_PARTY", j.getJSONArray("lines").getJSONObject(0).getString("speaker"))
        assertTrue(j.getJSONArray("lines").getJSONObject(1).isNull("speaker"))
        assertEquals("icu", j.getJSONArray("accepted").getJSONObject(0).getString("field"))
        assertEquals("NOT_DISCUSSED", j.getJSONObject("topics").getJSONObject("dialysis").getString("status"))
        assertTrue(j.getBoolean("machineDraft"))
        assertEquals("auto", GocTranscriptJson.encode(GocTranscript()).getString("language"))
        // A transcript written on the web has no planVersion: it belongs to the record it sits on.
        val fromWeb = JSONObject().put("lines", org.json.JSONArray()).put("language", "auto").put("note", "n")
        assertEquals(4, GocTranscriptJson.decode(fromWeb, 4)!!.planVersion)
    }
    @Test fun theEmergencyCardNeverCarriesTheTranscript() {
        val signed = draft().copy(gocTranscript = talk(), status = EctprStatus.ACTIVE)
        val snapshot = EdCardSnapshot.json(signed, null)
        assertFalse(snapshot.has(GOC_TRANSCRIPT_KEY))
        assertFalse(snapshot.toString().contains("We do not want ICU"))
        assertFalse(snapshot.toString().contains("The note."))
        // The local record keeps it.
        assertTrue(PlanJson.encode(signed).has(GOC_TRANSCRIPT_KEY))
        val issued = CardTokens.issue(signed, now, null)
        assertFalse(issued.snapshot.contains("We do not want ICU"))
    }

    // The next talk starts from the last one, as a draft.
    @Test fun aReviewCarriesTheNote_notTheWords_andConfirmsNothing() {
        val active = GocPlan.apply(draft(), GocDecision.ICU, IcuKind.NO.name).copy(status = EctprStatus.ACTIVE, gocTranscript = talk(1))
        val review = EctprLifecycle.reviewDraft(listOf(active), active, "Dr Rao", now, LocalDate.of(2026, 11, 1), id = "r2")
        assertEquals(2, review.version)
        assertTrue(review.gocTranscript!!.lines.isEmpty())
        assertEquals("The note.", GocCarry.startingNote(review))
        assertNotNull(GocCarry.previous(review))
        assertTrue(GocCarry.carried(review, GocDecision.ICU, emptyMap()))
        assertFalse(GocCarry.carried(review, GocDecision.DIALYSIS, emptyMap()))
        assertFalse(GocCarry.carried(review, GocDecision.ICU, mapOf(GocDecision.ICU to GocTopicRecord(GocDecision.ICU, GocTopicStatus.CONFIRMED, IcuKind.NO.name))))
        assertFalse("Not a review", GocCarry.carried(active.copy(supersedes = null), GocDecision.ICU, emptyMap()))
    }

    // Long talks.
    @Test fun aLongerWindowStaysWithinWhatWhisperReads() {
        val planner = OfflineSpeechSegmenter(240_000, 448_000)
        val windows = mutableListOf<OfflineSpeechWindow>()
        FloatArray(1_000_000) { .1f }.toList().chunked(OfflineSpeechSegmenter.FRAME_SAMPLES).forEach { planner.accept(it.toFloatArray())?.let(windows::add) }
        planner.finish()?.let(windows::add)
        assertTrue(windows.all { it.samples.size <= 448_000 })
        assertEquals(1_000_000L, windows.last().endSample)
        assertTrue(windows.size in 2..4)
    }
    @Test fun readingProgressRoundTrips_andBadDataIsIgnored() {
        val p = GocSpeechProgress("abc", "te", 448_000, listOf(OfflineTranscriptSegment(0, 28_000, "Words", "te")))
        assertEquals(p, GocSpeechProgress.decode(p.encode()))
        assertNull(GocSpeechProgress.decode("not json"))
        assertTrue(GOC_MAX_RECORDING_SECONDS >= 3600)
    }
    @Test fun theSpeechPackIsPinned() {
        assertEquals(375_485_327L, OfflineWhisperModel.downloadBytes)
        OfflineWhisperModel.files.forEach { f ->
            assertTrue(f.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(OfflineWhisperModel.url(f).startsWith("https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/8f3c18b358db4d1f2fc1eae49d75cd20989e4309/"))
        }
    }
}
