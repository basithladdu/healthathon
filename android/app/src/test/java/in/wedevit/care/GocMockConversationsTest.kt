package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The fictional mock goals-of-care conversations in docs/goc-mock-conversations, run through the Android matcher. Each has an expected-outcome file
 * shared with the web test (prototype/tests/goc-extract.test.mjs). knownGaps.android lists what this matcher is known to miss, with the reason,
 * so a gap is reported instead of hidden.
 */
class GocMockConversationsTest {
    private val folder = listOf("../../docs/goc-mock-conversations", "../docs/goc-mock-conversations", "docs/goc-mock-conversations").map(::File).firstOrNull { it.isDirectory }

    private val fields = mapOf("cpr" to GocDecision.CPR, "dialysis" to GocDecision.DIALYSIS, "respCeiling" to GocDecision.BREATHING, "icu" to GocDecision.ICU,
        "vasopressors" to GocDecision.VASOPRESSORS, "ng" to GocDecision.FEEDING_NG, "peg" to GocDecision.FEEDING_PEG, "hydration" to GocDecision.HYDRATION,
        "hospitalTransfer" to GocDecision.TRANSFER, "ambulance" to GocDecision.AMBULANCE, "goalOfCare" to GocDecision.GOAL)
    private val guide = mapOf("consent" to GocGuideItem.CONSENT, "patientDetails" to GocGuideItem.PATIENT_DETAILS, "decisionMakers" to GocGuideItem.FAMILY_CONTACT,
        "understanding" to GocGuideItem.UNDERSTANDING, "values" to GocGuideItem.VALUES, "homeCare" to GocGuideItem.HOME_CARE) +
        fields.mapValues { (_, d) -> GocGuideItem.entries.first { it.decision == d } }

    private fun strings(o: JSONObject, key: String): List<String> = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

    @Test fun everyMockConversationGivesItsExpectedSuggestions() {
        val dir = folder ?: error("docs/goc-mock-conversations not found from ${File(".").absolutePath}")
        val expectedFiles = dir.listFiles { f -> f.name.endsWith(".expected.json") }!!.sortedBy { it.name }
        assertTrue("Expected 6 to 8 mock conversations", expectedFiles.size in 6..8)
        val problems = mutableListOf<String>()
        expectedFiles.forEach { file ->
            val expected = JSONObject(file.readText())
            assertTrue(file.name, expected.getBoolean("fictional"))
            val raw = File(dir, expected.getString("transcript")).readText()
            assertTrue(expected.getString("transcript"), raw.startsWith("# FICTIONAL"))
            val lines = GocLines.fromText(raw.lines().filterNot { it.startsWith("#") }.joinToString("\n"))
            val text = GocLines.text(lines)
            val found = GocExtract.extract(text)
            val gaps = expected.optJSONObject("knownGaps")?.optJSONObject("android") ?: JSONObject()
            val name = expected.getString("transcript")
            val decisions = expected.getJSONObject("decisions")
            decisions.keys().forEach { key ->
                val d = fields[key] ?: return@forEach
                if (gaps.has(key)) return@forEach
                val want = decisions.getJSONObject(key)
                val standing = GocLatest.standing(found, d, lines)
                if (standing.suggested.keys != strings(want, "suggested").toSet()) problems += "$name $key suggested ${standing.suggested.keys}, expected ${strings(want, "suggested")}"
                if (want.has("ruledOut") && standing.ruledOut.keys != strings(want, "ruledOut").toSet()) problems += "$name $key ruled out ${standing.ruledOut.keys}, expected ${strings(want, "ruledOut")}"
                if (standing.changedMind != (key in strings(expected, "changedMind"))) problems += "$name $key change of mind ${standing.changedMind}"
                if (standing.conflict != (key in strings(expected, "conflict"))) problems += "$name $key disagreement ${standing.conflict}"
            }
            val covered = GocGuide.covered(text)
            fun status(topic: String) = guide[topic]?.let { GocGuide.status(it, found, covered, emptySet(), emptyMap(), consented = false) }
            strings(expected, "discussed").filterNot { gaps.has(it) }.forEach { topic -> if (status(topic) == GocChecklistStatus.NOT_DISCUSSED) problems += "$name $topic should be discussed" }
            strings(expected, "notDiscussed").filterNot { gaps.has(it) }.forEach { topic -> status(topic)?.let { if (it != GocChecklistStatus.NOT_DISCUSSED) problems += "$name $topic should not be discussed, got $it" } }
            found.hits.forEach { h -> assertTrue("$name: quote not word for word: ${h.quote}", text.replace('\n', ' ').contains(h.quote.removeSuffix("…"))) }
        }
        assertEquals("Mock conversation problems:\n" + problems.joinToString("\n"), 0, problems.size)
    }

    @Test fun theEndPromptListsWhatWasNotDiscussed() {
        val text = "Doctor: Are you okay with recording this? Patient: Yes. Doctor: Your name? Patient: Abdul."
        val found = GocExtract.extract(text)
        val open = GocGuide.undiscussed(found, GocGuide.covered(text), emptySet(), emptyMap(), consented = true)
        assertTrue(GocGuideItem.CPR in open && GocGuideItem.DIALYSIS in open)
        assertTrue(GocGuideItem.CONSENT !in open && GocGuideItem.PATIENT_DETAILS !in open)
        val verified = mapOf(GocDecision.CPR to GocTopicRecord(GocDecision.CPR, GocTopicStatus.NOT_DISCUSSED_VERIFIED))
        assertTrue(GocGuideItem.CPR !in GocGuide.undiscussed(found, GocGuide.covered(text), emptySet(), verified, consented = true))
        assertEquals(listOf(GocGuideItem.CONSENT, GocGuideItem.PATIENT_DETAILS, GocGuideItem.FAMILY_CONTACT, GocGuideItem.CPR, GocGuideItem.DIALYSIS), GocGuide.ORDER.take(5))
    }

    @Test fun latestStatementWins_perPerson() {
        val lines = GocLines.fromText("Doctor: Do you want CPR?\nPatient: No, no CPR.\nPatient: Actually, I want CPR.\nPatient: No. Do not do CPR.")
        val s = GocLatest.standing(GocExtract.extract(GocLines.text(lines)), GocDecision.CPR, lines)
        assertEquals(setOf(Cpr.DNACPR.name), s.suggested.keys)
        assertTrue(s.changedMind && !s.conflict)
        assertTrue(s.superseded.any { (earlier, _) -> Cpr.ATTEMPT.name in earlier.options })
        val two = GocLines.fromText("Patient: No ICU for me.\nSon: We want ICU.")
        val t = GocLatest.standing(GocExtract.extract(GocLines.text(two)), GocDecision.ICU, two)
        assertTrue(t.conflict && !t.changedMind)
        val ladder = GocExtract.extract("No ventilator. BiPAP is okay.")
        val l = GocLatest.standing(ladder, GocDecision.BREATHING)
        assertEquals(setOf("4"), l.suggested.keys); assertEquals(setOf("5"), l.ruledOut.keys); assertTrue(!l.changedMind)
    }

    @Test fun theNoteFollowsTheLegalFormSections() {
        val r = EctprLifecycle.newDraft("p1", "Dr Rao", "2026-11-04T10:00:00Z", EctprPatient(name = "Asha Rao (fictional)"), id = "r1")
        val note = GocNote.draft(GocPlan.apply(r, GocDecision.CPR, Cpr.DNACPR.name), emptyMap(), "", false, listOf(GocGuide.title(GocGuideItem.DIALYSIS)))
        val at = listOf("SECTION I ", "SECTION II ", "SECTION III ", "SECTION IV ").map { note.indexOf(it) }
        assertTrue(note, at.all { it > 0 } && at.zipWithNext().all { (a, b) -> b > a })
        assertTrue(note, note.contains("Not discussed: Dialysis"))
        assertTrue(note, note.contains(PlanText.cpr(Cpr.DNACPR).en))
    }
}
