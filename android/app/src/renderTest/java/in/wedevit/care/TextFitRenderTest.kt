package `in`.wedevit.care

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Opt-in (-PSAHARA_RENDER_UI=true) text-fit check for the owner's rule "no word broken across lines".
 * Walks the Practice walkthrough (Rahul as patient, Arjun as family, the doctor demo) at 360dp in English
 * and Hindi at text size 1.0, 1.3 and 2.0, swipes every vertical scroller to the end, and reads every
 * TextLayoutResult on the way. Flags line breaks inside a word or grapheme (TextFitRules), text wider
 * than its box, text cut by its parent, and ellipsized or height-clipped text.
 * Findings go to <sahara.render.dir>/textfit-<run>.tsv with a small jpeg crop per broken text.
 * It reports and never fails on findings, so it cannot block a build; only a broken tour fails.
 * Normal CI does not compile this file (renderTest source set is added only with the flag).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class TextFitRenderTest(private val flow: String, private val hindi: Boolean, private val scale: Float) {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private class Finding(val kind: String, val text: String, val excerpt: String, val tag: String, val screens: MutableSet<String>, var shot: String)

    private val run = "$flow-${if (hindi) "hi" else "en"}-${(scale * 100).toInt()}"
    private val findings = LinkedHashMap<String, Finding>()
    private val tourErrors = mutableListOf<String>()
    private var shots = 0

    @Test fun tour() {
        assertTrue(Build.FINGERPRINT.contains("robolectric", ignoreCase = true))
        compose.waitForIdle()
        runCatching { click(if (hindi) "care-language-hi" else "care-language-en") }
        when (flow) {
            "patient" -> patient()
            "family" -> family()
            else -> doctor()
        }
        write()
    }

    private fun patient() {
        enterWalkthrough("rahul")
        screen("Today") {}
        screen("Check-in") { click("today-checkin-words") }
        back()
        screen("Emergency card") { if (has("emergency-card-entry")) click("emergency-card-entry") else click("today-qr") }
        back()
        screen("Care") { click("nav-Care") }
        screen("Symptoms") { click("nav-Care"); click("daily-symptom-tracker-entry") }
        screen("Symptoms: add") { click("symptom-add") }
        back(); back()
        screen("Medicines") { click("nav-Care"); click("care-module-Medicines-toggle") }
        back()
        screen("Documents") { click("nav-Docs") }
        screen("More") { click("nav-More") }
        screen("More: reminders") { click("more-reminders") }
        back()
        screen("More: privacy") { click("nav-More"); click("more-privacy") }
        back()
        screen("More: care plan") { click("nav-More"); click("more-care-plan-entry") }
        back()
        // Last: MapLibre native code may not load on the JVM; a failure here loses nothing above.
        screen("Nearby") { click("nav-Help"); if (has("permission-not-now")) click("permission-not-now") }
    }

    private fun family() {
        enterWalkthrough("arjun")
        screen("Family: Today") {}
        screen("Family: Care") { click("nav-Care") }
        screen("Family: Docs") { click("nav-Docs") }
        screen("Family: More") { click("nav-More") }
    }

    private fun doctor() {
        click("doctor-workspace-entry"); click("doctor-demo-entry")
        compose.waitUntil(20_000) { has("demo-login") }
        click("demo-login")
        screen("Doctor home") {}
        screen("Doctor patients") { click("demo-nav-Patients") }
        val patientTag = tags("demo-patient-").firstOrNull { it.removePrefix("demo-patient-") !in setOf("back", "detail", "hub", "search") }
        if (patientTag == null) { tourErrors += "Doctor patients: no patient card"; return }
        screen("Patient hub") { click(patientTag) }
        val skip = setOf("doctor-shared-patient-hub", "doctor-shared-patient-id", "doctor-shared-tools")
        val sections = (tags("doctor-tab-") + tags("demo-record-") + tags("doctor-shared-")).filter { it !in skip && !it.endsWith("-meta") }.distinct()
        sections.forEach { tag ->
            screen("Patient hub: ${tag.substringAfterLast('-')}") {
                if (!has(tag)) back()
                click(tag)
            }
            if (tag.endsWith("GoalsOfCare")) {
                // The recorder sits behind the goals-of-care card.
                listOf("goc-open", "doctor-live-capture", "doctor-record-audio").firstOrNull { has(it) }?.let { open ->
                    screen("Recorder") { click(open) }
                    back()
                }
            }
        }
        screen("Doctor schedule") { click("demo-nav-Schedule") }
        screen("Doctor account") { click("demo-nav-Account") }
    }

    private fun enterWalkthrough(user: String) {
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-$user")
        compose.waitUntil(60_000) { has("nav-Today") }
        settle()
    }

    /** Opens one screen, then checks it top to bottom. A screen that cannot be reached is noted, not fatal. */
    private fun screen(name: String, open: () -> Unit) {
        runCatching { open(); settle(); sweep(name) }.onFailure { tourErrors += "$name: ${it.javaClass.simpleName}: ${it.message?.lineSequence()?.firstOrNull()?.take(200)}" }
        write()
    }

    private fun sweep(name: String) {
        scan(name)
        val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        repeat(30) {
            val open = compose.onAllNodes(vertical).fetchSemanticsNodes().filter { n ->
                n.config[SemanticsProperties.VerticalScrollAxisRange].let { it.value() < it.maxValue() - 0.5f }
            }
            if (open.isEmpty()) return
            open.forEach { n -> runCatching { compose.onNode(byId(n.id)).performTouchInput { swipeUp() } }; settle() }
            scan(name)
        }
    }

    private fun scan(name: String) {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        nodes.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            runCatching { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) }
            layouts.forEach { layout -> runCatching { check(name, node, layout) } }
        }
    }

    private fun check(name: String, node: SemanticsNode, layout: TextLayoutResult) {
        val text = layout.layoutInput.text.text
        if (text.isBlank()) return
        val ends = (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
        TextFitRules.splits(text, ends).forEach { split ->
            record(name, node, if (split.kind == TextFitRules.Kind.GRAPHEME) "grapheme-split" else "mid-word", text, split.excerpt)
        }
        if (layout.didOverflowWidth) record(name, node, "overflow-width", text, text)
        val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        if (ellipsized) record(name, node, "ellipsized", text, text)
        else if (layout.didOverflowHeight) record(name, node, "clipped-height", text, text)
        // Cut sideways by a parent (vertical clipping is ordinary scrolling, horizontal scrollers are skipped).
        val bounds = node.boundsInRoot
        if (bounds.width > 0f && bounds.width < node.size.width - 2f && bounds.height > 0f && !insideHorizontalScroller(node)) record(name, node, "clipped-width", text, text)
    }

    private fun record(name: String, node: SemanticsNode, kind: String, text: String, excerpt: String) {
        val key = "$kind|$text"
        val known = findings[key]
        if (known != null) { known.screens += name; return }
        val finding = Finding(kind, text, excerpt, tagOf(node), mutableSetOf(name), "")
        findings[key] = finding
        if (kind != "ellipsized" && kind != "clipped-height" && shots < 60) finding.shot = shoot(node)
    }

    /** A small jpeg of the text's parent (some context, still small); a png from Roborazzi if jpeg encoding is unavailable. */
    private fun shoot(node: SemanticsNode): String {
        val target = node.parent?.takeIf { it.size.height in 1..(node.size.height * 4).coerceAtLeast(240) } ?: node
        val base = "textfit/$run-${++shots}"
        val dir = outDir()
        return runCatching {
            val bitmap = compose.onNode(byId(target.id), useUnmergedTree = true).captureToImage().asAndroidBitmap()
            val small = if (bitmap.width > 720) Bitmap.createScaledBitmap(bitmap, 720, (bitmap.height * 720f / bitmap.width).toInt().coerceAtLeast(1), true) else bitmap
            File(dir, "$base.jpg").apply { parentFile?.mkdirs() }.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 70, it) }
            "$base.jpg"
        }.recoverCatching {
            File(dir, "$base.png").parentFile?.mkdirs()
            compose.onNode(byId(target.id), useUnmergedTree = true).captureRoboImage(File(dir, "$base.png").path)
            "$base.png"
        }.getOrDefault("")
    }

    private fun write() {
        val clean = { s: String -> s.replace('\t', ' ').replace('\n', ' ') }
        val rows = findings.values.joinToString("") { f ->
            listOf(run, flow, if (hindi) "hi" else "en", scale.toString(), f.kind, f.screens.joinToString("; "), f.tag, clean(f.text).take(300), clean(f.excerpt).take(300), f.shot).joinToString("\t") + "\n"
        }
        val errors = tourErrors.joinToString("") { "$run\t$flow\t${if (hindi) "hi" else "en"}\t$scale\ttour-error\t\t\t${clean(it)}\t\t\n" }
        File(outDir(), "textfit-$run.tsv").writeText("run\tflow\tlang\tscale\tkind\tscreens\ttag\ttext\texcerpt\tshot\n$rows$errors")
    }

    private fun tagOf(node: SemanticsNode): String {
        var n: SemanticsNode? = node
        while (n != null) { n.config.getOrNull(SemanticsProperties.TestTag)?.let { return it }; n = n.parent }
        return ""
    }

    private fun insideHorizontalScroller(node: SemanticsNode): Boolean {
        var n: SemanticsNode? = node.parent
        while (n != null) { if (n.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null) return true; n = n.parent }
        return false
    }

    private fun tagged(tag: String) = SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag)
    private fun byId(id: Int) = SemanticsMatcher("id $id") { it.id == id }
    private fun has(tag: String) = compose.onAllNodes(tagged(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun tags(prefix: String) = compose.onAllNodes(SemanticsMatcher("tag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true })
        .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun bring(tag: String) {
        val target = tagged(tag)
        val scrollers = compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().size
        for (index in scrollers - 1 downTo 0) {
            if (runCatching { compose.onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(target) }.isSuccess) break
        }
        runCatching { compose.onAllNodes(target).onFirst().performScrollTo() }
        settle()
    }

    private fun click(tag: String) { bring(tag); compose.onAllNodes(tagged(tag)).onFirst().performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun back() { runCatching { compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }; settle() }
    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    private fun outDir() = File(System.getProperty("sahara.render.dir") ?: "build/outputs/textfit").apply { mkdirs() }
}

// One class per flow, language and text size so Gradle (forkEvery = 1) gives each a fresh JVM and walkthrough seed.
// Narrow a run with --tests 'in.wedevit.care.TextFitPatient*' (or *En100, *Hi200 ...).
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.0f) class TextFitPatientEn100 : TextFitRenderTest("patient", false, 1.0f)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.3f) class TextFitPatientEn130 : TextFitRenderTest("patient", false, 1.3f)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 2.0f) class TextFitPatientEn200 : TextFitRenderTest("patient", false, 2.0f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.0f) class TextFitPatientHi100 : TextFitRenderTest("patient", true, 1.0f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.3f) class TextFitPatientHi130 : TextFitRenderTest("patient", true, 1.3f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 2.0f) class TextFitPatientHi200 : TextFitRenderTest("patient", true, 2.0f)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.0f) class TextFitFamilyEn100 : TextFitRenderTest("family", false, 1.0f)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.3f) class TextFitFamilyEn130 : TextFitRenderTest("family", false, 1.3f)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 2.0f) class TextFitFamilyEn200 : TextFitRenderTest("family", false, 2.0f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.0f) class TextFitFamilyHi100 : TextFitRenderTest("family", true, 1.0f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.3f) class TextFitFamilyHi130 : TextFitRenderTest("family", true, 1.3f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 2.0f) class TextFitFamilyHi200 : TextFitRenderTest("family", true, 2.0f)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.0f) class TextFitDoctorEn100 : TextFitRenderTest("doctor", false, 1.0f)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.3f) class TextFitDoctorEn130 : TextFitRenderTest("doctor", false, 1.3f)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 2.0f) class TextFitDoctorEn200 : TextFitRenderTest("doctor", false, 2.0f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.0f) class TextFitDoctorHi100 : TextFitRenderTest("doctor", true, 1.0f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.3f) class TextFitDoctorHi130 : TextFitRenderTest("doctor", true, 1.3f)
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi", fontScale = 2.0f) class TextFitDoctorHi200 : TextFitRenderTest("doctor", true, 2.0f)
