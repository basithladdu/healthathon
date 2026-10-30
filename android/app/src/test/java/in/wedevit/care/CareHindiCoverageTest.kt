package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Every user-visible string on the family-side main screens must come with Hindi, written inline as
 * `t("English", "हिन्दी")` (or as an English/Hindi literal pair such as `"Son" to "बेटा"`).
 * This reads the screens' source and fails on any English UI text that has no Hindi next to it.
 */
class CareHindiCoverageTest {
    private val screens = mapOf(
        "CareUi.kt" to listOf("CareApp", "Today", "CareList", "CareRow", "More", "AddPerson", "AddPersonContent", "AddCareItem", "Welcome", "Talk", "NoteReader", "AudioPlayback", "Nearby", "CentreCard", "InfoDisclosure", "RawTranscriptPanel"),
        "CentreMap.kt" to listOf("CentreMap"),
        "CentreSuggestion.kt" to listOf("CentreSuggestionSheet", "CentreSuggestionList"),
        "CareDocuments.kt" to listOf("CareDocuments"),
        "CareDocumentReviewUi.kt" to listOf("CareDocumentReview"),
        "CareDocumentAskUi.kt" to listOf("CareDocumentAsk"),
        "MedicineScanner.kt" to listOf("MedicineScanner", "ScannerHeader", "PhotoSelectionCard", "MedicineReviewCard"),
        "CareLabs.kt" to listOf("CareLabs", "LabHistoryCard", "LabHistoryPlot", "LabResultRow", "CareLabEditor"),
        "CareReminderUi.kt" to listOf("CareReminderSettings", "ReminderItemSettings"),
        "FeedbackUi.kt" to listOf("FeedbackExperienceScreen"),
        "CareComponents.kt" to listOf("CareNavigation", "CarePersonSelector", "WalkthroughUserMenu"),
        "CareDailyUi.kt" to listOf("CareAddMenuButton", "CareModuleCard", "CareModuleList", "ConversationRow", "MedicineScanAction", "QuickCheckInCard"),
        "CareSchedule.kt" to listOf("DailyCareShortcuts", "CareDateButton", "CareCalendar", "CalendarEntryCard", "CareScheduleEditor"),
        "CareWorkflow.kt" to listOf("WorkflowSummary", "CareWorkflows", "WorkflowCard", "CareWorkflowEditor"),
        "CareAddFamily.kt" to listOf("AddFamilySheet", "PendingJoinDialog"),
        "CareAccount.kt" to listOf("AccountContent", "CareRoot"),
        "CareSymptomUi.kt" to listOf("CareSymptomDiary", "SymptomNoteCard", "SymptomNoteForm"),
        "CareDistress.kt" to listOf("DistressQuestion", "DistressScreen", "DistressSource", "DistressEntryCard", "DistressHistoryCard"),
        // Doctor recording panels, conversation history and note reader (28 Oct). Store checks are shown through Hindi maps.
        "DoctorRecordStage.kt" to listOf("DoctorRecordStage"),
        "DoctorAttendees.kt" to listOf("DoctorAttendeesList"),
        "DoctorConversationsUi.kt" to listOf("DoctorConversationsContent", "DiscussionReadingCard"),
        "DoctorPreparationQuestions.kt" to listOf("PrepCard", "DoctorPreparationQuestions", "DoctorPreparationAnswers"),
        "DoctorSavedAudioSheet.kt" to listOf("DoctorSavedAudioPage"),
        "DoctorTranscriptGoals.kt" to listOf("DoctorTranscriptGoals", "GoalCard"),
        "DoctorRecordingReviewUi.kt" to listOf("DoctorRecordingReview"),
        "DoctorNoteReaderUi.kt" to listOf("DoctorNoteReaderBody", "NoteReaderTranscript", "NoteReaderGoalCard"),
        "DoctorMedicinesUi.kt" to listOf("MedicineChip"),
        // Goals-of-care sign-off and the optional screening helper (28 Oct).
        "GoalsSignatureUi.kt" to listOf("NoteSignatureCapture", "NoteSignedBy"),
        "GoalsScreening.kt" to listOf("GoalsScreeningHelper"),
        // Emergency plan (ECTPR) wizard, ED Quick View, wallet card and living will (SVK specs, 29 Oct).
        "CarePlanUi.kt" to listOf("CarePlanScreen", "PlanHeader", "IfInDoubtBox", "CareContinuesBox", "CarePlanHome", "WithdrawPlanForm", "EctprWizard", "PatientFields", "FamilyFields", "DecisionMakerFields",
            "CapacityFields", "UnderstandingFields", "ValuesFields", "LockedTreatment", "TreatmentIntro", "GoalAndTransfer", "CprFields", "TrialEditor", "BreathingFields", "OrganSupportFields",
            "FeedingFields", "ConflictFields", "ReviewFields", "SignatureFields", "EdQuickViewPage", "EdRowView", "CardStatus", "WalletCardPage", "CarePlanEntryCard"),
        "CarePlanExtrasUi.kt" to listOf("PlanReadAloud", "LegalPendingBox", "DpdpNoticeCard", "ReviewReminderCard", "PilotLogPage", "EpisodeForm", "OutcomeForm", "IncidentForm", "LivingWillPage", "AmdIdFields", "AmdEditor", "AmdDhrFields", "CustodianFields", "PrintedCard", "ExecutedCard", "AmdCancelForm"),
    )

    /** Literals that are keys, animation labels or format patterns, never shown as text. */
    private val notShown = setOf("English", "EEE, d MMM", "EEEE, d MMMM", "Care overlay", "Care tab", "care completed", "selected tab", "guest-patient",
        "To do", "Care team", "Lab history", "Choose a valid date and time.", "The observation cannot be in the future.",
        // Filter and category keys matched against saved or directory data; their labels are paired separately.
        "All India", "Home care", "Clinic visits", "Inpatient care", "Care notes", "Not great", "Goals of care {} {} Version {}",
        // Saved into records, file names, errors that are caught, and as-printed lab test names.
        "\\nOriginal prescription references:\\n", "Goals-of-care-v{}.txt", "File unavailable", "Recording unavailable", "What matters", "Red blood cells",
        "Sahara root", "microphone level", "feedback expression", "feedback warmth", "d MMM", "d MMM, HH:mm", "h:mm a", "dd MMM yyyy · HH:mm z", "dd MMM yyyy",
        "https://tiles.openfreemap.org/styles/liberty", "https://www.google.com/maps/search/?api=1&query=",
        // Added by later lanes: a filter key and a kind key (labels paired separately), a licence URL, a map font name and a file-name pattern.
        "Day care", "Lab result", "https://creativecommons.org/licenses/by/4.0/", "Noto Sans Bold", "Scan {}.jpg", "yyyy-MM-dd HH-mm")

    @Test fun everyMainScreenStringHasAHindiPair() {
        val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }
        val missing = screens.flatMap { (file, names) ->
            val source = File(dir, file).readText()
            names.flatMap { name -> HindiScan.missing(HindiScan.body(source, name), notShown).map { "$file $name: $it" } }
        }
        assertTrue("English-only text on main screens (wrap it in t(\"English\", \"हिन्दी\")):\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun scannerCatchesEnglishOnlyText() {
        val code = """@Composable fun X() { val t = careText(); Text("Retry"); Text(t("Save", "सहेजें")); Text(t("Back", "Back")); label = "Medicine name"; testTag("care-add"); Text("${'$'}count") }"""
        assertEquals(listOf("t() without Hindi: Back", "Retry", "Medicine name"), HindiScan.missing(code, emptySet()))
    }

    @Test fun pairsShownOutsideTCalls() {
        careAddKinds.forEach { (_, label) -> assertTrue(label.second, HindiScan.hasHindi(label.second)) }
        assertEquals(CareRelations.toSet(), CareRelationsHindi.keys)
        CareRelationsHindi.values.forEach { assertTrue(it, HindiScan.hasHindi(it)) }
    }

    @Test fun checkInMoodsKeepEnglishValueAndShowHindi() {
        listOf(CheckInMood.GOOD, CheckInMood.OKAY, CheckInMood.HARD).forEach {
            assertEquals(it, checkInMoodLabel(it, false))
            assertTrue(HindiScan.hasHindi(checkInMoodLabel(it, true)))
        }
        assertEquals("Felt tired", checkInMoodLabel("Felt tired", true))
    }

    @Test fun everySymptomSaveCheckHasHindi() {
        val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }
        val source = File(dir, "CareSymptomData.kt").readText(); val start = source.indexOf("fun error(")
        val messages = Regex("return \"([^\"]+)\"").findAll(source.substring(start, source.indexOf("\n    fun ", start + 1))).map { it.groupValues[1] }.toSet()
        assertTrue(messages.size >= 10)
        assertEquals(emptySet<String>(), messages - SymptomErrorsHindi.keys)
        SymptomErrorsHindi.values.forEach { assertTrue(it, HindiScan.hasHindi(it)) }
    }

    @Test fun calendarCategoriesKeepEnglishValues() {
        listOf("All", "Medicine", "Visit", "Test", "Follow-up", "Procedure", "Task", "Conversation").forEach {
            assertEquals(it, careCategoryLabel(it, false)); assertTrue(it, HindiScan.hasHindi(careCategoryLabel(it, true)))
        }
    }

    @Test fun hindiChoiceCarriesOverToANewAccount() {
        val device = MemoryPreferences()
        CareLanguageChoice(MemoryPreferences(), device).choose(true)
        assertTrue(CareLanguageChoice(MemoryPreferences(), device).hindi)
        val own = MemoryPreferences().also { it.edit().putBoolean("hindi", false).apply() }
        assertEquals(false, CareLanguageChoice(own, device).hindi)
    }

    @Test fun languageChoiceIsKeptForAccountAndPhone() {
        val own = MemoryPreferences(); val device = MemoryPreferences()
        CareLanguageChoice(own, device).choose(CareLang.UR)
        CareLanguageState.current = CareLang.EN
        assertEquals(CareLang.UR, CareLanguageChoice(own, device).lang)
        assertEquals(CareLang.UR, CareLanguageChoice(MemoryPreferences(), device).lang)
        assertEquals(CareLang.UR, CareLanguageChoice.deviceLanguage(device))
        // A phone that chose Hindi before languages were added stays on Hindi.
        val older = MemoryPreferences().also { it.edit().putBoolean("hindi", true).apply() }
        assertEquals(CareLang.HI, CareLanguageChoice(older, null).lang)
    }

    @Test fun hindiDatesUseHindiNames() {
        assertEquals("सोमवार, 26 अक्तूबर", LocalDate.of(2026, 10, 26).format(careDateFormat("EEEE, d MMMM", true)).replace("अक्टूबर", "अक्तूबर"))
    }
}

/** A small Kotlin-source reader: finds string literals (including those inside `${...}`), skipping comments. */
internal object HindiScan {
    private val devanagari = Regex("[\\u0900-\\u097F]")
    private val uiPosition = Regex("(?:\\b(?:Text|Heading|SectionTitle|Tile|QuietLine|ChoiceRow|DetailPanel)\\(\\s*|\\bIcon\\([^()]*,\\s*|(?:label|placeholder|contentDescription|title|text)\\s*=\\s*|message\\.value\\s*=\\s*|error\\s*=\\s*|notice\\s*=\\s*)$")
    private val pairGap = Regex("\\s*(,|to|else)\\s*")
    fun hasHindi(text: String) = devanagari.containsMatchIn(text)

    /** One function's source, from its declaration to the next top-level declaration. */
    fun body(source: String, name: String): String {
        val start = Regex("^(?:@Composable )?(?:internal |private )?(?:@Composable )?fun (?:<[^>]*> )?$name\\(", RegexOption.MULTILINE).find(source) ?: error("$name not found")
        val end = Regex("^(?:@Composable|internal |private |fun |object |class |/\\*\\*|val |data |@file)", RegexOption.MULTILINE).find(source, start.range.last + 1)
        return source.substring(start.range.first, end?.range?.first ?: source.length)
    }

    data class Literal(val start: Int, val end: Int, val text: String) // end = index after the closing quote

    /** Reads the literal opening at [open]; records it (and nested ones) in [out]; returns the index after its closing quote. */
    private fun readString(code: String, open: Int, out: MutableList<Literal>): Int {
        var i = open + 1; val text = StringBuilder()
        while (i < code.length) {
            val c = code[i]
            when {
                c == '\\' -> { text.append(code, i, minOf(i + 2, code.length)); i += 2 }
                c == '"' -> { out += Literal(open, i + 1, text.toString()); return i + 1 }
                c == '$' && i + 1 < code.length && code[i + 1] == '{' -> { i = readBlock(code, i + 2, out); text.append("{}") }
                else -> { text.append(c); i++ }
            }
        }
        return i
    }

    private fun readBlock(code: String, from: Int, out: MutableList<Literal>): Int {
        var i = from; var depth = 1
        while (i < code.length) {
            val c = code[i]
            if (c == '"') { i = readString(code, i, out); continue }
            if (c == '{') depth++ else if (c == '}') { depth--; if (depth == 0) return i + 1 }
            i++
        }
        return i
    }

    private fun allLiterals(code: String): List<Literal> {
        val out = mutableListOf<Literal>(); var i = 0
        while (i < code.length) {
            when {
                code.startsWith("//", i) -> i = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                code.startsWith("/*", i) -> i = code.indexOf("*/", i) + 2
                code.startsWith("\"\"\"", i) -> i = code.indexOf("\"\"\"", i + 3) + 3
                code[i] == '\'' -> { var j = i + 1; if (j < code.length && code[j] == '\\') j++; i = if (j + 1 < code.length && code[j + 1] == '\'') j + 2 else i + 1 }
                code[i] == '"' -> i = readString(code, i, out)
                else -> i++
            }
        }
        return out.sortedBy { it.start }
    }

    /** Index of the bracket closing the one at [open], and the top-level commas in between. */
    private fun closing(code: String, open: Int): Pair<Int, List<Int>> {
        var i = open; var depth = 0; val commas = mutableListOf<Int>()
        while (i < code.length) {
            val c = code[i]
            if (c == '"') { i = readString(code, i, mutableListOf()); continue }
            if (c in "([{") depth++ else if (c in ")]}") { depth--; if (depth == 0) return i to commas } else if (c == ',' && depth == 1) commas += i
            i++
        }
        return code.length to commas
    }

    fun missing(code: String, notShown: Set<String>): List<String> {
        val lits = allLiterals(code); val bad = mutableListOf<String>(); val paired = mutableSetOf<Int>()
        Regex("(?<![\\w.])t\\(").findAll(code).forEach { call ->
            val (close, commas) = closing(code, call.range.last)
            if (commas.size != 1) return@forEach
            val first = lits.filter { it.start in call.range.last + 1 until commas[0] }; val second = lits.filter { it.start in commas[0] + 1 until close }
            if (second.any { hasHindi(it.text) } || second.none { Regex("[A-Za-z]{2}").containsMatchIn(it.text) }) paired += first.map { it.start }
            else bad += "t() without Hindi: " + first.joinToString(" ") { it.text }
        }
        lits.forEachIndexed { k, lit ->
            if (lit.start in paired || hasHindi(lit.text) || lit.text in notShown) return@forEachIndexed
            val plain = lit.text.replace(Regex("\\{\\}|\\$[A-Za-z_]\\w*"), "")
            if (!Regex("[A-Za-z]{2}").containsMatchIn(plain)) return@forEachIndexed
            val next = lits.getOrNull(k + 1); val prev = lits.getOrNull(k - 1)
            if (next != null && lit.end <= next.start && hasHindi(next.text) && pairGap.matches(code.substring(lit.end, next.start))) return@forEachIndexed
            if (prev != null && hasHindi(prev.text) && prev.end <= lit.start && pairGap.matches(code.substring(prev.end, lit.start))) return@forEachIndexed
            val words = Regex("[A-Za-z]+[ ,.?!]+[A-Za-z]").containsMatchIn(plain)
            val shownAlone = plain.trim().firstOrNull()?.isUpperCase() == true && uiPosition.containsMatchIn(code.substring(maxOf(0, lit.start - 80), lit.start))
            if (words || shownAlone) bad += lit.text
        }
        return bad
    }
}

/** In-memory SharedPreferences for the language choice test. */
private class MemoryPreferences : android.content.SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { values[key!!] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor { values[key!!] = value; return this }
        override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor { values[key!!] = value; return this }
        override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor { values[key!!] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor { values[key!!] = value; return this }
        override fun remove(key: String?): android.content.SharedPreferences.Editor { values.remove(key); return this }
        override fun clear(): android.content.SharedPreferences.Editor { values.clear(); return this }
        override fun commit() = true
        override fun apply() {}
    }
}
