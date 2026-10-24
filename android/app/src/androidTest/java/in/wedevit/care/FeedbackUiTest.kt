package `in`.wedevit.care

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** App-experience feedback fixtures only. No health-record, account or external submission. */
class FeedbackUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun ratingTilesStartUnselectedAndSaveOptionalNoteLocally() = isolated { context, scope ->
        var closed = false
        ui.setContent { CareTheme { FeedbackScreen(scope, "test", onClose = { closed = true }) } }
        ui.onNodeWithTag("feedback-save").performScrollTo().assertIsNotEnabled()
        (1..5).forEach { ui.onNodeWithTag("feedback-rating-$it").performScrollTo().assertIsDisplayed().assertIsNotSelected() }
        val store = FeedbackStore(context, scope, "test")
        assertNull(store.latest())
        ui.onNodeWithTag("feedback-rating-5").performScrollTo().performClick()
        ui.onNodeWithTag("feedback-rating-5").assertIsSelected()
        screenshot("feedback-rating-5")
        ui.onNodeWithTag("feedback-note").performScrollTo().performTextInput("Fictional app-interface feedback")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("feedback-save").performScrollTo().assertIsEnabled().performClick()
        ui.onNodeWithText("Saved on this device").assertExists()
        assertEquals(1f, store.latest()!!.score, .001f)
        assertEquals("Fictional app-interface feedback", store.latest()!!.note)
        ui.onNodeWithTag("feedback-rating-1").performScrollTo().performClick()
        ui.onNodeWithTag("feedback-save").performScrollTo().performClick()
        assertEquals(0f, store.latest()!!.score, .001f)
        ui.onNodeWithTag("feedback-close").performScrollTo().performClick()
        ui.runOnIdle { assertTrue(closed) }
    }

    @Test fun unsavedBackOffersKeepEditingOrLeaveWithoutSaving() = isolated { context, scope ->
        var closed = false
        ui.setContent { CareTheme { FeedbackScreen(scope, "test", onClose = { closed = true }) } }
        ui.onNodeWithTag("feedback-note").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("feedback-note").performScrollTo().performTextInput("Unsaved fictional interface note")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("feedback-close").performScrollTo().performClick()
        ui.onNodeWithText("Leave without saving?").assertIsDisplayed()
        ui.onNodeWithText("Keep editing").performClick()
        ui.onNodeWithTag("feedback-note").performScrollTo().assertTextContains("Unsaved fictional interface note")
        ui.runOnIdle { assertFalse(closed) }
        ui.onNodeWithTag("feedback-close").performScrollTo().performClick()
        ui.onNodeWithText("Leave").performClick()
        ui.runOnIdle { assertTrue(closed) }
        assertNull(FeedbackStore(context, scope, "test").latest())
    }

    @Test fun storageIsScopedAndInvalidOrCorruptSaveDoesNotOverwritePreviousData() = isolated { context, scope ->
        val store = FeedbackStore(context, scope, "test", now = { 100 })
        assertNull(store.save(.5f, "Fictional feedback"))
        assertEquals(100L, FeedbackStore(context, scope, "test").latest()!!.savedAt)
        assertNull(FeedbackStore(context, scope, "other-surface").latest())
        assertNull(FeedbackStore(context, "$scope-other", "test").latest())
        assertNotNull(store.save(Float.NaN, "Invalid score"))
        assertNotNull(store.save(1f, "x".repeat(501)))
        assertEquals("Fictional feedback", store.latest()!!.note)
        val preferences = context.getSharedPreferences(preferenceName(scope, "test"), Context.MODE_PRIVATE)
        preferences.edit().putString("latest", "broken-json").commit()
        val reopened = FeedbackStore(context, scope, "test")
        assertNull(reopened.latest())
        assertNotNull(reopened.error())
        assertNotNull(reopened.save(1f, "Must not overwrite unreadable feedback"))
        assertEquals("broken-json", preferences.getString("latest", null))
    }

    private fun isolated(block: (Context, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = "feedback-ui-${UUID.randomUUID()}"
        val account = context.getSharedPreferences("care-account-kjnhtakqzboayqorrlue", Context.MODE_PRIVATE)
        val before = account.all.toMap()
        try { block(context, scope) } finally {
            assertEquals(before, account.all)
            listOf(scope to "test", scope to "other-surface", "$scope-other" to "test").forEach { (accountScope, surface) ->
                context.getSharedPreferences(preferenceName(accountScope, surface), Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }
    private fun preferenceName(scope: String, surface: String): String = "sahara-feedback-v1-" + MessageDigest.getInstance("SHA-256")
        .digest("${scope.length}:$scope:${surface.length}:$surface".toByteArray()).joinToString("") { "%02x".format(it) }
    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(700)
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        target.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
