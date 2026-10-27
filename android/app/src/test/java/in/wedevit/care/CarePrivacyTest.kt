package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// All numbers below are synthetic test values, not real people's details.
class CarePrivacyTest {
    private val person = "22222222-2222-2222-2222-222222222222"
    private fun r(text: String) = CarePrivacy.redact(text)

    @Test fun indianMobileNumbersInCommonFormats() {
        listOf("9876543210", "98765 43210", "98765-43210", "+91 98765 43210", "+91-9876543210", "+919876543210", "91 98765 43210", "09876543210", "0091 9876543210", "987 654 3210")
            .forEach { assertEquals(it, "Call [phone] today", r("Call $it today")) }
        assertEquals("Son: [phone], daughter: [phone].", r("Son: 9876543210, daughter: 8123456789."))
    }
    @Test fun landlinesNeedATrunkPrefixOrCountryCode() {
        assertEquals("Clinic [phone]", r("Clinic 080-2345 6789"))
        assertEquals("Clinic [phone]", r("Clinic 011 23456789"))
        assertEquals("Clinic [phone]", r("Clinic +91 80 2345 6789"))
        assertEquals("Range 1000-1500 2000", r("Range 1000-1500 2000"))
    }
    @Test fun ordinaryClinicalNumbersAreKept() {
        listOf("Pain 7/10 since 2 days", "Morphine 10 mg at 06:30 and 18:30", "BP 120/80, pulse 88", "Date 2026-10-27", "Platelets, count 150000",
            "Weight 54.5 kg", "Took 2 tablets of 500 mg", "Ward 12, bed 4", "Score 12345", "Pin pricks in the feet", "Note id 1730000000000")
            .forEach { assertEquals(it, it, r(it)) }
    }
    @Test fun emails() {
        assertEquals("Write to [email] or [email].", r("Write to sample.family+care@example.com or a_b@mail.example.co.in."))
        assertEquals("Mixed [email] here", r("Mixed 9876543210@example.in here"))
    }
    @Test fun aadhaarLikeTwelveDigitNumbers() {
        assertEquals("Aadhaar [aadhaar]", r("Aadhaar 2345 6789 0123"))
        assertEquals("Aadhaar [aadhaar]", r("Aadhaar 2345-6789-0123"))
        assertEquals("ID [aadhaar].", r("ID 234567890123."))
        // Aadhaar never starts with 0 or 1, and longer digit runs are not Aadhaar.
        assertEquals("Ref 1234 5678 9012", r("Ref 1234 5678 9012"))
        assertEquals("Ref 2345678901234", r("Ref 2345678901234"))
    }
    @Test fun pan() {
        assertEquals("PAN [PAN]", r("PAN ABCPE1234F"))
        assertEquals("pan [PAN]", r("pan abcpe1234f"))
        assertEquals("Code ABCDE1234F", r("Code ABCDE1234F")) // fourth letter is not a PAN holder type
        assertEquals("XABCPE1234F", r("XABCPE1234F"))
    }
    @Test fun addressesEndingInPinCode() {
        assertEquals("Lives at [address]. Pain at night.", r("Lives at Flat 302, Green Apartments, Road No 12, Banjara Hills, Hyderabad 500034. Pain at night."))
        assertEquals("Home: [address]", r("Home: H.No 8-2-293/A, Road 7, Hyderabad - 500 034"))
        assertEquals("[address]", r("#45, 2nd Cross, Indiranagar, Bengaluru, PIN 560038"))
        assertEquals("Indiranagar, [address]", r("Indiranagar, Bengaluru - 560038"))
        assertEquals("Send to [pincode]", r("Send to pincode: 560038"))
        assertEquals("पता [pincode]", r("पता पिन कोड 110001"))
        // Without a PIN code, or a bare ", City 123456", nothing is guessed.
        assertEquals("Flat 302, Green Apartments", r("Flat 302, Green Apartments"))
        assertEquals("Indiranagar, Bengaluru 560038", r("Indiranagar, Bengaluru 560038"))
    }
    @Test fun hindiTextAndNamesAreUntouched() {
        assertEquals("बेटे का नंबर [phone] है, दर्द 6 है", r("बेटे का नंबर 98765 43210 है, दर्द 6 है"))
        assertEquals("Sample Daughter came with me", r("Sample Daughter came with me"))
        assertEquals("", r(""))
    }

    @Test fun symptomCheckinRedactsTypedTextOnly() {
        val note = SymptomNote(symptom = "Pain", rating = 6, observedDate = "2026-10-27", observedTime = "08:00", note = "Call son on 9876543210 if worse", painLocation = "Lower back", author = "Sample Family", painTriggers = listOf("walking"))
        val item = CareItem(id = "a", personId = person, kind = "checkin", title = "Pain · 6", detail = SymptomNoteCodec.encode(note), created = 5, revision = 3)
        val safe = CarePrivacy.entryForUpload(item)
        val decoded = SymptomNoteCodec.decode(safe.detail)!!
        assertEquals("Call son on [phone] if worse", decoded.note)
        assertEquals(SymptomNoteCodec.decode(item.detail)!!.copy(note = decoded.note), decoded)
        assertEquals(item.copy(detail = safe.detail), safe)
        // Nothing to remove: the same instance and bytes go up.
        val clean = item.copy(detail = SymptomNoteCodec.encode(note.copy(note = "Better today")))
        assertSame(clean, CarePrivacy.entryForUpload(clean))
        // Other kinds (medicines, visits with clinic phone numbers) are not changed here.
        val visit = CareItem(id = "v", personId = person, kind = "visit", title = "Clinic", detail = "Call 9876543210", created = 1)
        assertSame(visit, CarePrivacy.entryForUpload(visit))
        val legacy = CareItem(id = "l", personId = person, kind = "checkin", title = "Tired", detail = "Email sample@example.com", created = 1)
        assertEquals("Email [email]", CarePrivacy.entryForUpload(legacy).detail)
    }

    @Test fun conversationRedactsSourceSectionsAndRebuildsValidContext() {
        val source = "नमस्ते. My son's number is 98765 43210 and email sample@example.com. I want to stay at home."
        val draft = Conversation(person, source = source, priorities = "Stay at home", participants = "Son (9876543210)", nextSteps = "Write to sample@example.com")
        val j = JSONObject(DoctorConversationContext.initial(draft))
        val bytes = source.toByteArray(Charsets.UTF_8)
        fun evidence(quote: String): JSONObject { val start = source.substring(0, source.indexOf(quote)).toByteArray(Charsets.UTF_8).size; return JSONObject().put("start", start).put("end", start + quote.toByteArray(Charsets.UTF_8).size).put("quote", quote) }
        val goal = j.getJSONArray("goals").getJSONObject(0).put("status", "discussed").put("origin", "source").put("answer", "Stay at home; call 98765 43210")
        goal.put("evidence", JSONArray().put(evidence("I want to stay at home.")).put(evidence("number is 98765 43210 and")).put(evidence("98765")))
        j.put("recorderLabel", "Recorder sample@example.com")
        j.put("englishDraft", JSONObject().put("text", "Number 9876543210").put("sourceHash", j.getString("sourceHash")).put("origin", "assisted").put("reviewed", false))
        j.put("transcription", JSONObject().put("provider", "p").put("model", "m").put("at", 1).put("sourceHash", DoctorConversationContext.sha256(bytes)))
        val original = draft.copy(conversationId = "11111111-1111-1111-1111-111111111111", contextJson = j.toString())
        DoctorConversationContext.validate(original.source, original.contextJson)

        val safe = CarePrivacy.conversationForUpload(original)
        assertEquals("नमस्ते. My son's number is [phone] and email [email]. I want to stay at home.", safe.source)
        assertEquals("Son ([phone])", safe.participants); assertEquals("Write to [email]", safe.nextSteps); assertEquals("Stay at home", safe.priorities)
        DoctorConversationContext.validate(safe.source, safe.contextJson)
        val c = JSONObject(safe.contextJson); val ev = c.getJSONArray("goals").getJSONObject(0).getJSONArray("evidence")
        assertEquals("I want to stay at home.", ev.getJSONObject(0).getString("quote"))
        assertEquals("number is [phone] and", ev.getJSONObject(1).getString("quote"))
        assertEquals("[phone]", ev.getJSONObject(2).getString("quote")) // a quote inside a removed detail widens to the token
        assertEquals("Stay at home; call [phone]", c.getJSONArray("goals").getJSONObject(0).getString("answer"))
        assertEquals("Recorder [email]", c.getString("recorderLabel"))
        assertEquals("Number [phone]", c.getJSONObject("englishDraft").getString("text"))
        assertEquals(DoctorConversationContext.sha256(safe.source.toByteArray(Charsets.UTF_8)), c.getJSONObject("transcription").getString("sourceHash"))
        assertFalse(safe.contextJson.contains("98765") || safe.contextJson.contains("example.com"))
        // Identity and local-only fields stay as they were.
        assertEquals(original.conversationId, safe.conversationId); assertEquals(original.audioPath, safe.audioPath)
    }

    @Test fun cleanConversationIsUploadedByteForByte() {
        val draft = Conversation(person, source = "Pain 7/10, morphine 10 mg", priorities = "Comfort")
        val withContext = draft.copy(contextJson = DoctorConversationContext.initial(draft))
        assertSame(withContext, CarePrivacy.conversationForUpload(withContext))
        assertSame(draft, CarePrivacy.conversationForUpload(draft))
        val onlySection = draft.copy(questions = "Call 9876543210")
        assertEquals("Call [phone]", CarePrivacy.conversationForUpload(onlySection).questions)
    }

    @Test fun noticesNameWhatIsRemovedInBothLanguages() {
        listOf(CarePrivacy.SYMPTOM_NOTICE_EN, CarePrivacy.CONVERSATION_NOTICE_EN).forEach { assertTrue(it.contains("phone numbers, emails, Aadhaar, PAN and addresses removed")) }
        listOf(CarePrivacy.SYMPTOM_NOTICE_HI, CarePrivacy.CONVERSATION_NOTICE_HI).forEach { assertTrue(it.contains("फ़ोन नंबर, ईमेल, आधार, पैन और पते")) }
    }
}
