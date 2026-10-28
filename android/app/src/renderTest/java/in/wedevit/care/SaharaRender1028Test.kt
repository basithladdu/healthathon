package `in`.wedevit.care

import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Opt-in host renders for the 28 Oct team meeting (-PSAHARA_RENDER_UI=true only).
 * Uses only the app's own Practice walkthrough data (Rahul, Arjun, the fictional doctor login
 * prefilled by the walkthrough). No account is signed in, no login code is touched, and nothing
 * is sent. Each test starts a fresh Robolectric app, so no real device data exists here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
abstract class SaharaRender1028Test {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val failures = mutableListOf<String>()

    @Before fun hostOnly() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        assertTrue(Build.FINGERPRINT.contains("robolectric", ignoreCase = true))
        compose.waitForIdle()
    }

    fun patientToday() = run {
        enterWalkthrough("rahul")
        step("01-today") {}
        step("02-today-checkin") { scrollTo("today-list", hasTestTag("today-checkin")) }
        step("03-today-medicines") { scrollTo("today-list", hasText("Medicines", substring = true)) }
    }

    fun patientCareAndNote() = run {
        enterWalkthrough("rahul")
        step("04-care") { click("nav-Care") }
        step("05-care-conversations") { openConversations() }
        step("06-care-note-transcript") { openFirstNote() }
        step("07-care-note-reviewed") { scrollAny(hasText("Patient's review", substring = true), hasText("Family's review", substring = true), hasText("confirm", substring = true)) }
    }

    fun patientMedicinesCalendar() = run {
        enterWalkthrough("rahul")
        step("08-medicines") { click("nav-Care"); scrollTo("care-list", hasTestTag("care-module-medicines")) }
        step("09-calendar") { click("nav-Care"); runCatching { compose.onAllNodesWithText("Calendar").onFirst().performScrollTo() }; settle(); compose.onAllNodesWithText("Calendar").onFirst().performClick() }
    }

    fun familyConfirm() = run {
        enterWalkthrough("arjun")
        step("10-family-today") {}
        step("11-family-confirm") { click("nav-Care"); openConversations(); openFirstNote(); scrollAny(hasText("Family's review", substring = true), hasText("Patient's review", substring = true)) }
    }

    fun nearby() = run {
        enterWalkthrough("rahul")
        step("12-nearby") { click("nav-Nearby") }
        step("13-nearby-list") { click("nav-Nearby"); scrollAny(hasTestTag("nearby-list")) }
    }

    fun emergencyCard() = run {
        enterWalkthrough("rahul")
        step("14-emergency-setup") { scrollTo("today-list", hasTestTag("emergency-card-entry")); click("emergency-card-entry") }
        step("15-emergency-qr") {
            compose.onNodeWithTag("emergency-card-make").performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("emergency-card-qr").fetchSemanticsNodes().isNotEmpty() }
            scrollTo("today-list", hasTestTag("emergency-card-qr"))
        }
    }

    fun accountAndMore() = run {
        enterWalkthrough("rahul")
        step("16-more") { click("nav-More") }
        step("17-more-account") { click("nav-More"); scrollAny(hasText("Sign in to share care", substring = true), hasText("Your account and sharing", substring = true)) }
    }

    fun accountWelcome() = run {
        step("18-welcome") {}
        step("19-account-signin") { click("welcome-patient-family"); click("welcome-account") }
    }

    fun doctorWalkthrough() = run {
        click("doctor-workspace-entry"); click("doctor-demo-entry")
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("demo-login").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("demo-login").performScrollTo().performClick()
        step("20-doctor-home") {}
        step("22-doctor-patients") { click("demo-nav-Patients") }
        step("23-doctor-patient-hub") {
            click("demo-nav-Patients")
            compose.onAllNodes(hasText("Rahul", substring = true) and hasClickAction()).onFirst().performClick()
        }
        step("24-doctor-medicines") { compose.onAllNodesWithText("Medicines").onFirst().performClick() }
        step("25-doctor-conversations") { compose.onAllNodesWithText("Conversations").onFirst().performClick() }
        step("26-doctor-recording-review") { compose.onAllNodes(tagPrefix("record-")).onFirst().performScrollTo() }
    }

    fun hindiToday() = run(hindi = true) {
        enterWalkthrough("rahul")
        step("27-hi-today") {}
        step("28-hi-care") { click("nav-Care") }
    }

    /** The language choice is app state that can outlive one Robolectric test, so every test sets it first. */
    private fun run(hindi: Boolean = false, body: () -> Unit) {
        runCatching { click(if (hindi) "care-language-hi" else "care-language-en") }
        runCatching(body).onFailure { failures += "setup: ${it.javaClass.simpleName}: ${it.message?.take(300)}"; dump("setup-failure-${System.nanoTime()}") }
        if (failures.isNotEmpty()) throw AssertionError(failures.joinToString("\n"))
    }

    private fun enterWalkthrough(user: String) {
        click("welcome-patient-family")
        click("patient-walkthrough-entry")
        click("patient-walkthrough-$user")
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("nav-Today").fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    private fun openConversations() {
        scrollTo("care-list", hasTestTag("care-module-conversations"))
        if (compose.onAllNodes(tagPrefix("care-note-")).fetchSemanticsNodes().isEmpty()) {
            runCatching { click("care-module-conversations-toggle") }
        }
        settle()
        runCatching { compose.onAllNodes(tagPrefix("care-note-")).onFirst().performScrollTo() }
    }

    private fun openFirstNote() {
        if (compose.onAllNodes(tagPrefix("care-note-")).fetchSemanticsNodes().isEmpty()) openConversations()
        compose.onAllNodes(tagPrefix("care-note-")).onFirst().performClick()
        settle()
    }

    private fun tagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun click(tag: String) { runCatching { compose.onNodeWithTag(tag).performScrollTo() }; compose.onNodeWithTag(tag).performClick(); settle() }
    private fun scrollTo(list: String, target: SemanticsMatcher) { compose.onNodeWithTag(list).performScrollToNode(target); settle() }

    /** Scrolls whichever scrollable container (including a sheet) holds the first matching target. */
    private fun scrollAny(vararg targets: SemanticsMatcher) {
        val scrollables = compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().size
        for (target in targets) for (index in scrollables - 1 downTo 0) {
            if (runCatching { compose.onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(target) }.isSuccess) { settle(); return }
        }
        error("No scrollable container holds the target")
    }

    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }

    private fun step(name: String, navigate: () -> Unit) {
        runCatching {
            navigate()
            settle()
            captureScreenRoboImage(File(outDir(), "$name.png").absolutePath)
        }.onFailure { failures += "$name: ${it.javaClass.simpleName}: ${it.message?.take(300)}"; dump("$name-failure") }
    }

    private fun dump(name: String) = runCatching {
        File(outDir(), "$name.txt").writeText(compose.onAllNodes(isRoot()).printToString(maxDepth = 60))
    }

    private fun outDir() = File(System.getProperty("sahara.render.dir") ?: "build/outputs/sahara-render-1028").apply { mkdirs() }
}

// One class per screen group so Gradle (forkEvery = 1) gives each a fresh JVM and walkthrough seed.
class SaharaRender1028PatientToday : SaharaRender1028Test() { @Test fun render() = patientToday() }
class SaharaRender1028PatientCareAndNote : SaharaRender1028Test() { @Test fun render() = patientCareAndNote() }
class SaharaRender1028PatientMedicinesCalendar : SaharaRender1028Test() { @Test fun render() = patientMedicinesCalendar() }
class SaharaRender1028FamilyConfirm : SaharaRender1028Test() { @Test fun render() = familyConfirm() }
class SaharaRender1028Nearby : SaharaRender1028Test() { @Test fun render() = nearby() }
class SaharaRender1028EmergencyCard : SaharaRender1028Test() { @Test fun render() = emergencyCard() }
class SaharaRender1028AccountAndMore : SaharaRender1028Test() { @Test fun render() = accountAndMore() }
class SaharaRender1028AccountWelcome : SaharaRender1028Test() { @Test fun render() = accountWelcome() }
class SaharaRender1028DoctorWalkthrough : SaharaRender1028Test() { @Test fun render() = doctorWalkthrough() }
@Config(sdk = [35], qualifiers = "hi-rIN-w360dp-h800dp-notnight-xxhdpi") class SaharaRender1028HindiToday : SaharaRender1028Test() { @Test fun render() = hindiToday() }
