package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** TalkBack contract: the pain body is spoken by part and score, and shared headings, cards, errors and icon buttons keep their semantics. */
class CareA11yTest {
    private val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }
    private fun source(name: String) = File(dir, name).readText()
    private val all by lazy { dir.listFiles { f -> f.extension == "kt" }!!.associate { it.name to it.readText() } }

    @Test fun painBodyIsSpokenByPartNameAndScore() {
        val sites = listOf("left-leg", "head", "chest")
        val ratings = mapOf("head" to 7, "left-leg" to 0)
        assertEquals("Pain marked: Head, 7 of 10, Severe; Chest, not rated; Left leg / foot, 0 of 10, None", PainBodyMapRules.spokenSummary(sites, ratings, false))
        assertEquals("चिह्नित दर्द: सिर, 10 में से 7, तेज़; छाती, अंक नहीं; बायाँ पैर, 10 में से 0, कोई नहीं", PainBodyMapRules.spokenSummary(sites, ratings, true))
        assertEquals("No pain areas marked yet", PainBodyMapRules.spokenSummary(listOf("not-an-area"), emptyMap(), false))
        assertEquals("अभी दर्द की कोई जगह चिह्नित नहीं", PainBodyMapRules.spokenSummary(emptyList(), emptyMap(), true))
        assertEquals("10 of 10, Severe", PainBodyMapRules.spokenScore(12, false))
    }

    @Test fun painBodyViewsCarryTheSpokenSummary() {
        val map = source("CarePainBodyMap.kt")
        // 3D view, 2D fallback diagram and the live summary line all speak the marked parts and scores.
        assertEquals(3, Regex("PainBodyMapRules\\.spokenSummary\\(").findAll(map).count())
        assertTrue(map.contains("PainBodyMapRules.spokenScore(spotRating(id), hindi)"))
        assertTrue(map.contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test fun sharedTitlesAreHeadingsAndCardsMerge() {
        val ui = source("CareUi.kt")
        assertTrue(HindiScan.body(ui, "Heading").contains("a11yHeading()"))
        assertTrue(HindiScan.body(ui, "SectionTitle").contains("a11yHeading()"))
        assertTrue(HindiScan.body(ui, "SurfaceCard").contains("a11yCard()"))
        assertTrue(HindiScan.body(source("CareComponents.kt"), "DetailPanel").contains("a11yHeading()"))
        assertTrue(HindiScan.body(source("CareDailyUi.kt"), "CareModuleCard").contains("a11yHeading()"))
        assertTrue(HindiScan.body(source("SaharaMotion.kt"), "SaharaLoading").contains("announce()"))
    }

    @Test fun errorLinesAreAnnounced() {
        val silent = all.flatMap { (name, text) -> text.lines().withIndex()
            .filter { (_, line) -> Regex("""Text\(\w+, ?color ?= ?MaterialTheme\.colorScheme\.error""").containsMatchIn(line) && "announce()" !in line && "liveRegion" !in line }
            .map { "$name:${it.index + 1}" } }
        assertTrue("Error text without a live region:\n" + silent.joinToString("\n"), silent.isEmpty())
    }

    @Test fun iconButtonsAreNamed() {
        val unnamed = all.flatMap { (name, text) -> Regex("""IconButton\([^{]*\{\s*Icon\([^,()]+(\([^()]*\))?,\s*(null|contentDescription\s*=\s*null)""").findAll(text)
            .filter { m -> "contentDescription" !in m.value.substringBefore("{") }
            .map { "$name:${text.substring(0, it.range.first).count { c -> c == '\n' } + 1}" }.toList() }
        assertTrue("IconButton without a TalkBack name:\n" + unnamed.joinToString("\n"), unnamed.isEmpty())
    }
}
