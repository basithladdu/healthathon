package `in`.wedevit.care

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner, 6 Nov: "Step 6 of 10", "wallet card" and "ED quick view" confused even the maker. The app's own labels use
 * plain names; the legal form and printed documents keep their wording ("Signature / thumb impression").
 */
class GocPlainLabelsTest {
    private fun source(name: String): String {
        val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }
        return File(dir, name).readText()
    }

    @Test fun screenLabelsUsePlainNames() {
        val plan = source("CarePlanUi.kt")
        assertFalse(plan.contains("t(\"Wallet card\""))
        assertFalse(plan.contains("t(\"ED Quick View\""))
        assertTrue(plan.contains("t(\"Pocket emergency card\""))
        assertTrue(plan.contains("t(\"For the emergency room\""))
        assertTrue(plan.contains("t(\"A one-page summary hospital staff can read fast\""))
        // The audit log keeps its recorded detail, which older entries and the pilot report match on.
        assertTrue(plan.contains("ED_VIEW_DETAIL = \"ED Quick View\""))
    }

    @Test fun planStepsAreNamedAndTheTalkIsItsOwnPage() {
        val plan = source("CarePlanUi.kt")
        assertFalse(plan.contains("t(\"Step \${step + 1} of \${steps.size}\""))
        assertTrue(plan.contains("t(\"Step \${step + 1} · \$name\""))
        assertFalse(plan.contains("startStep"))
        assertTrue(plan.contains("\"goc\" -> {"))
        val goc = source("CareGocUi.kt")
        assertFalse(goc.contains("LocalGocOpenNow"))
        assertFalse(goc.contains("of 5"))
        assertTrue(goc.contains("t(\"Make the full care plan\""))
    }

    @Test fun signaturePadHasNoThumbSwitchButTheFormKeepsItsHeading() {
        val pad = source("CareSignaturePad.kt")
        assertFalse(pad.contains("Switch("))
        assertFalse(pad.contains("goc-signature-thumb"))
        assertFalse(pad.contains("allowThumb"))
        assertTrue(source("CareGocExtract.kt").contains("Bi(\"Signature / thumb impression\""))
    }
}
