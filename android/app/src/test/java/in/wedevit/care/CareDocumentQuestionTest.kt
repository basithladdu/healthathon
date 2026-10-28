package `in`.wedevit.care

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CareDocumentQuestionTest {
    private val discharge = DocumentQuestionSource("report-1", "Discharge Oct.pdf", listOf(
        DocumentPageText(1, "DISCHARGE SUMMARY\n\nDate of Discharge: 08/10/2026", "DISCHARGE SUMMARY\n\nDate of Discharge: 08/10/2026"),
        DocumentPageText(2, "Advice on discharge:\nReview in OPD on 12/11/2026\n   "),
    ), reviewed = true)
    private val lab = DocumentQuestionSource("report-2", "CBC.jpg", listOf(DocumentPageText(1, "Haemoglobin 9.8 g/dL\nRepeat CBC after 2 weeks")), reviewed = false)
    private val question = "When is my next follow-up?"

    private fun reply(request: DocumentQuestionRequest, found: Boolean = true, answer: String = "Your review in OPD is on 12/11/2026.", citations: JSONArray = JSONArray().put(cite("D1", 2, 2))) =
        JSONObject().put("provider", CARE_ASSIST_PROVIDER).put("model", "llama-3.3-70b").put("requiresReview", true).put("sourceHash", request.sourceHash)
            .put("found", found).put("answer", answer).put("citations", citations).toString()
    private fun cite(document: String, page: Int, line: Int) = JSONObject().put("document", document).put("page", page).put("line", line)

    @Test fun promptCarriesNumberedLinesUnderShortReferencesAndNoFileNames() {
        val request = DocumentQuestionRules.build("  $question ", listOf(discharge, lab))
        assertEquals(question, request.question)
        // Blank lines are left out but keep the review screen's line numbers.
        assertEquals(listOf(
            DocumentQuestionLine("D1", 1, 1, "DISCHARGE SUMMARY"), DocumentQuestionLine("D1", 1, 3, "Date of Discharge: 08/10/2026"),
            DocumentQuestionLine("D1", 2, 1, "Advice on discharge:"), DocumentQuestionLine("D1", 2, 2, "Review in OPD on 12/11/2026"),
            DocumentQuestionLine("D2", 1, 1, "Haemoglobin 9.8 g/dL"), DocumentQuestionLine("D2", 1, 2, "Repeat CBC after 2 weeks"),
        ), request.lines)
        val payload = DocumentQuestionRules.payload(request, JSONObject().put("requestId", "id").put("consent", true))
        assertEquals(question, payload.getString("question")); assertEquals(request.sourceHash, payload.getString("sourceHash")); assertTrue(payload.getBoolean("consent"))
        val documents = payload.getJSONArray("documents")
        assertEquals(2, documents.length()); assertEquals("D1", documents.getJSONObject(0).getString("ref")); assertEquals(4, documents.getJSONObject(0).getJSONArray("lines").length())
        val line = documents.getJSONObject(0).getJSONArray("lines").getJSONObject(3)
        assertEquals(listOf(2, 2), listOf(line.getInt("page"), line.getInt("line"))); assertEquals("Review in OPD on 12/11/2026", line.getString("text"))
        val sent = payload.toString()
        assertFalse(sent.contains("Discharge Oct.pdf")); assertFalse(sent.contains("report-1")); assertFalse(sent.contains("CBC.jpg"))
    }

    @Test fun reviewedCorrectionsAreWhatIsSent() {
        val corrected = discharge.copy(pages = listOf(DocumentPageText(1, "Reveiw on 12/11/2O26", "Review on 12/11/2026")))
        assertEquals("Review on 12/11/2026", DocumentQuestionRules.build(question, listOf(corrected)).lines.single().text)
    }

    @Test fun hashChangesWithTheQuestionOrTheText() {
        val first = DocumentQuestionRules.build(question, listOf(discharge)).sourceHash
        assertEquals(first, DocumentQuestionRules.build(question, listOf(discharge)).sourceHash)
        assertNotEquals(first, DocumentQuestionRules.build("Which lab test is due?", listOf(discharge)).sourceHash)
        assertNotEquals(first, DocumentQuestionRules.build(question, listOf(discharge, lab)).sourceHash)
    }

    @Test fun requestLimitsAreChecked() {
        assertThrows(IllegalArgumentException::class.java) { DocumentQuestionRules.build(" ", listOf(discharge)) }
        assertThrows(IllegalArgumentException::class.java) { DocumentQuestionRules.build("x".repeat(301), listOf(discharge)) }
        assertThrows(IllegalArgumentException::class.java) { DocumentQuestionRules.build(question, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { DocumentQuestionRules.build(question, List(6) { discharge.copy(reportId = "r$it") }) }
        assertThrows(IllegalArgumentException::class.java) { DocumentQuestionRules.build(question, listOf(discharge, discharge)) }
        assertThrows(IllegalArgumentException::class.java) { DocumentQuestionRules.build(question, listOf(lab.copy(pages = listOf(DocumentPageText(1, "  \n ")))) ) }
        val long = DocumentQuestionSource("big", "Big.pdf", (1..12).map { DocumentPageText(it, List(20) { "y".repeat(110) }.joinToString("\n")) }, true)
        assertThrows(IllegalArgumentException::class.java) { DocumentQuestionRules.build(question, listOf(long)) }
    }

    @Test fun answerCitesTheDocumentWithThePhonesOwnWords() {
        val request = DocumentQuestionRules.build(question, listOf(discharge, lab))
        val answer = DocumentQuestionRules.parse(reply(request, citations = JSONArray().put(cite("D1", 2, 2)).put(cite("D1", 2, 2))), request)
        assertTrue(answer.found); assertEquals("Your review in OPD is on 12/11/2026.", answer.answer); assertEquals("llama-3.3-70b", answer.model)
        assertEquals(listOf(DocumentCitation("report-1", "Discharge Oct.pdf", 2, 2, "Review in OPD on 12/11/2026", true)), answer.citations)
        val labAnswer = DocumentQuestionRules.parse(reply(request, answer = "Repeat CBC after 2 weeks.", citations = JSONArray().put(cite("D2", 1, 2))), request)
        assertEquals("CBC.jpg", labAnswer.citations.single().reportName); assertFalse(labAnswer.citations.single().reviewed)
    }

    @Test fun notFoundShowsNothingMadeUp() {
        val request = DocumentQuestionRules.build(question, listOf(lab))
        val answer = DocumentQuestionRules.parse(reply(request, found = false, answer = "Probably next week", citations = JSONArray().put(cite("D1", 1, 1))), request)
        assertFalse(answer.found); assertEquals("", answer.answer); assertTrue(answer.citations.isEmpty())
    }

    @Test fun answersThatCannotBeCheckedFailClosed() {
        val request = DocumentQuestionRules.build(question, listOf(discharge, lab))
        fun fails(json: String) = assertThrows(RuntimeException::class.java) { DocumentQuestionRules.parse(json, request) }
        fails(reply(request, citations = JSONArray()))                                   // no citation
        fails(reply(request, citations = JSONArray().put(cite("D1", 1, 2))))              // the blank line was not sent
        fails(reply(request, citations = JSONArray().put(cite("D3", 1, 1))))              // no such document
        fails(reply(request, answer = "On 13/11/2026."))                                  // a number not in the cited line
        fails(reply(request, answer = ""))
        fails(reply(request, answer = "x".repeat(601)))
        fails(JSONObject(reply(request)).put("sourceHash", "0".repeat(64)).toString())    // a different question or text
        fails(JSONObject(reply(request)).put("provider", "other").toString())
        fails(JSONObject(reply(request)).put("requiresReview", false).toString())
        fails(reply(request, citations = JSONArray().put(JSONObject().put("document", "D1").put("page", "2").put("line", 2))))
        fails(reply(request, citations = JSONArray().apply { repeat(7) { put(cite("D1", 2, 2)) } }))
    }

    @Test fun clientSendsOnlyTextToTheAskPathAndNeedsConsent() = runBlocking {
        val request = DocumentQuestionRules.build(question, listOf(discharge))
        var sentPath = ""; var sentBody = ""
        val client = CareRemoteAssist(CareAssistTransport { path, body, _ -> sentPath = path; sentBody = body.orEmpty(); reply(request) })
        try { client.ask(request, "a.b.c", consent = false); fail("Expected consent check") } catch (_: IllegalArgumentException) { }
        assertEquals("", sentPath)
        val answer = client.ask(request, "a.b.c", consent = true)
        assertEquals("/ask", sentPath); assertTrue(answer.found)
        val body = JSONObject(sentBody)
        assertTrue(body.getBoolean("consent")); assertTrue(body.getString("requestId").matches(Regex("[0-9a-f-]{36}")))
        assertEquals(setOf("requestId", "consent", "question", "sourceHash", "documents"), body.keys().asSequence().toSet())
        val bad = CareRemoteAssist(CareAssistTransport { _, _, _ -> reply(request, answer = "On 1/1/2027.") })
        try { bad.ask(request, "a.b.c", consent = true); fail("Expected the unchecked answer to be refused") } catch (error: CareAssistException) { assertEquals("invalid_response", error.code) }
    }

    @Test fun askIsOfferedOnlyWhenTheServerSaysSo() = runBlocking {
        fun server(ready: Boolean, ask: Boolean?) = CareRemoteAssist(CareAssistTransport { _, _, _ ->
            JSONObject().put("provider", CARE_ASSIST_PROVIDER).put("ready", ready).put("label", "Groq").put("model", "m").apply { if (ask != null) put("askReady", ask) }.toString() })
        assertTrue(server(true, true).capabilities("t").askReady)
        assertFalse(server(false, true).capabilities("t").askReady)
        // A server deployed before this feature has no /ask path: show "AI is not set up yet".
        assertFalse(server(true, null).capabilities("t").askReady)
    }
}
