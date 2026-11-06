package `in`.wedevit.care

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

/** Opt-in (-PSAHARA_RENDER_UI=true): the doctor launchpad, register, patient tiles and tab row, at normal and 2x text, English and Hindi. Fails if any word is split across lines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h1400dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DoctorHomeRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = System.currentTimeMillis()
    private val asha = Person("1a2b3c4d-0000-4000-8000-000000000001", "Asha Verma", 1)
    private val ravi = Person("9f8e7d6c-0000-4000-8000-000000000002", "Ravi Kumar", 2)
    private val meena = Person("5e5e5e5e-0000-4000-8000-000000000003", "Meena Iyer", 3)
    private val timed = CareEntryCodec.encode(CareEntry("schedule", category = "Visit", timed = true, location = "Home visit"))
    private val items = listOf(
        CareItem("v1", asha.id, "visit", "Pain review", timed, now + 3_600_000, "", now),
        CareItem("v2", ravi.id, "visit", "Family meeting", timed, now + 3 * 86_400_000L, "", now),
        CareItem("c1", meena.id, "checkin", "A hard day", "", 0, "", now - 2 * 86_400_000L),
        CareItem("c2", ravi.id, "checkin", "A good day", "", 0, "", now - 60_000),
    )

    private fun render(name: String, fontScale: Float, hindi: Boolean) {
        CareLanguageState.current = if (hindi) CareLang.HI else CareLang.EN
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                CareTheme {
                    LazyColumn(Modifier.fillMaxSize().background(Cream), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        doctorConnectedHomeItems(listOf(asha, ravi, meena), items, now, hindi, {}, {}, {})
                        item { DoctorPatientTabRow("CarePlan", {}) }
                        item { DoctorPatientNavigationTiles({}) }
                        listOf(asha, ravi, meena).forEach { p -> item { DoctorPatientListCard(p.name, DoctorConnectedHome.patientCode(p.id), {}, pill = doctorPatientStatus(p, items, now, hindi)) } }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000); compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/doctor-home", "$name.png").path)
        assertNoSplitWords(name)
    }

    /** A line may only end between words: never a letter at the end of one line and a letter at the start of the next. */
    private fun assertNoSplitWords(name: String) {
        val nodes = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)).fetchSemanticsNodes()
        val broken = nodes.flatMap { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.flatMap { layout ->
                val text = layout.layoutInput.text.text
                (0 until layout.lineCount - 1).mapNotNull { line ->
                    val end = layout.getLineEnd(line)
                    if (end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] !in "-–·/,(" ) text else null
                }
            }
        }
        assertTrue("$name: words split across lines in $broken", broken.isEmpty())
    }

    @Test fun english() = render("doctor-home-en", 1f, false)
    @Test fun englishLargeText() = render("doctor-home-en-2x", 2f, false)
    @Test fun hindiLargeText() = render("doctor-home-hi-1_5x", 1.5f, true)
}
