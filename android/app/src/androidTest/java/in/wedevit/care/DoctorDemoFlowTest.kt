package `in`.wedevit.care

import android.content.Context
import android.view.View
import android.widget.TimePicker
import android.widget.DatePicker
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import java.io.File

/** Fictional local demo only; does not launch CareRoot, authenticate, or call a provider. */
class DoctorDemoFlowTest {
    @get:Rule val ui = createComposeRule()

    @Test fun doctorWorkspaceRemainsNavigableAtLargeTextWithoutChangingSystemSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesName = "doctor-demo-large-text-${UUID.randomUUID()}"
        val store = DoctorDemoStore(context, preferencesName = preferencesName)
        try {
            ui.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    CareTheme { DoctorDemo(onExit = {}, store = store) }
                }
            }
            ui.onNodeWithTag("demo-email").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("demo-password").performScrollTo().assertIsDisplayed()
            screenshot("doctor-login-large-text")
            ui.onNodeWithTag("demo-login").performScrollTo().assertIsDisplayed().performClick()
            screenshot("doctor-home-large-text")
            ui.onNodeWithTag("demo-nav-Patients").assertIsDisplayed().performClick()
            ui.onNodeWithTag("demo-patient-search").performTextInput("Asha")
            ui.onNodeWithTag("demo-patient-demo-asha").assertIsDisplayed().performClick()
            listOf("Summary", "Progress", "Medicines", "Conversations", "Visits", "Reports").forEach {
                openRecordSection(it)
            }
            screenshot("doctor-record-large-text")
            openRecordSection("Visits")
            ui.onNodeWithTag("demo-schedule-visit").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("demo-nav-Schedule").assertIsDisplayed().performClick()
            ui.onNodeWithTag("demo-add-visit").performScrollTo().assertIsDisplayed()
            screenshot("doctor-schedule-large-text")
            ui.onNodeWithTag("demo-nav-Account").assertIsDisplayed().performClick()
            ui.onNodeWithTag("demo-exit").performScrollTo().assertIsDisplayed()
            screenshot("doctor-account-large-text")
        } finally {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun fictionalDoctorCanReviewPatientAndManageVisitsWithoutChangingAccount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesName = "doctor-demo-ui-test-${UUID.randomUUID()}"
        val account = context.getSharedPreferences("care-account-kjnhtakqzboayqorrlue", Context.MODE_PRIVATE)
        val before = account.all.toMap()
        val store = DoctorDemoStore(context, preferencesName = preferencesName)
        // Only this unique test namespace is prepared; production/default demo data is untouched.
        store.appointments().filter { it.status == "Scheduled" }.forEach { assertNull(store.cancel(it.id)) }
        val tomorrow = LocalDate.now().plusDays(1)
        val completeFixture = DoctorDemoAppointment("demo-ui-complete", "demo-asha", tomorrow.atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 30, "Phone")
        assertNull(store.save(completeFixture))
        val exited = mutableStateOf(false)
        try {
            ui.setContent { CareTheme { if (!exited.value) DoctorDemo(onExit = { exited.value = true }, store = store) } }
            ui.onNodeWithTag("demo-preview-disclosure").performScrollTo()
                .assertTextEquals("No account is created and nothing is sent.")
            ui.onNodeWithText("Demo only").assertDoesNotExist()
            ui.onNodeWithContentDescription("Show password").performScrollTo().performClick()
            ui.onNodeWithContentDescription("Hide password").assertIsDisplayed().performClick()
            ui.onNodeWithTag("demo-password").performTextReplacement("incorrect")
            closeSoftKeyboard()
            ui.onNodeWithTag("demo-login").performScrollTo().performClick()
            ui.onNodeWithTag("demo-login-error").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("demo-password").performScrollTo().performTextReplacement("SaharaDemo")
            closeSoftKeyboard()
            screenshot("doctor-login")
            ui.onNodeWithTag("demo-login").performScrollTo().performClick()
            screenshot("doctor-demo-home")
            ui.onNodeWithTag("demo-nav-Patients").performClick()
            screenshot("doctor-patients")
            ui.onNodeWithTag("demo-patient-search").performTextInput("Asha")
            ui.onNodeWithTag("demo-patient-demo-asha").assertIsDisplayed().performClick()
            screenshot("doctor-demo-patient")
            listOf("Summary", "Progress", "Medicines", "Conversations", "Visits", "Reports").forEach {
                openRecordSection(it)
                screenshot("doctor-record-${it.lowercase().replace(' ', '-')}")
            }
            openRecordSection("Visits")
            ui.onNodeWithTag("demo-schedule-visit").performScrollTo().performClick()
            ui.onNodeWithTag("demo-appointment-note").performScrollTo().performTextInput("Fictional UI test visit")
            saveVisit()
            ui.waitForIdle()
            if (store.appointments().none { it.note == "Fictional UI test visit" }) {
                screenshot("doctor-demo-book-failure")
                println(ui.onRoot(useUnmergedTree = true).printToString())
            }
            val booked = store.appointments().single { it.note == "Fictional UI test visit" }
            assertEquals("demo-asha", booked.patientId)
            assertEquals("Scheduled", booked.status)
            ui.onNodeWithTag("demo-nav-Schedule").performClick()
            visitAction(booked.id, "Reschedule")
            ui.onNodeWithTag("demo-visit-time").performScrollTo().performClick()
            onView(isAssignableFrom(TimePicker::class.java)).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
                override fun getDescription() = "Set fictional demo visit time to 11 AM"
                override fun perform(controller: UiController, view: View) { (view as TimePicker).apply { hour = 11; minute = 0 }; controller.loopMainThreadUntilIdle() }
            })
            onView(withId(android.R.id.button1)).perform(click())
            ui.onNodeWithTag("demo-appointment-note").performScrollTo().performTextReplacement("Fictional rescheduled visit")
            saveVisit()
            ui.waitForIdle()
            if (store.appointments().single { it.id == booked.id }.note != "Fictional rescheduled visit") {
                screenshot("doctor-demo-reschedule-failure")
                println(ui.onRoot(useUnmergedTree = true).printToString())
            }
            assertEquals("Fictional rescheduled visit", store.appointments().single { it.id == booked.id }.note)
            assertEquals(booked.startMillis + 3_600_000L, store.appointments().single { it.id == booked.id }.startMillis)
            // Explicitly choose the same day/time to exercise overlap validation through the UI.
            ui.onNodeWithTag("demo-add-visit").performScrollTo().performClick()
            ui.onNodeWithTag("demo-visit-date").performScrollTo().performClick()
            onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
                override fun getDescription() = "Set overlapping fictional demo date"
                override fun perform(controller: UiController, view: View) { (view as DatePicker).updateDate(tomorrow.year, tomorrow.monthValue - 1, tomorrow.dayOfMonth); controller.loopMainThreadUntilIdle() }
            })
            onView(withId(android.R.id.button1)).perform(click())
            ui.onNodeWithTag("demo-visit-time").performScrollTo().performClick()
            onView(isAssignableFrom(TimePicker::class.java)).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
                override fun getDescription() = "Set overlapping fictional demo time"
                override fun perform(controller: UiController, view: View) { (view as TimePicker).apply { hour = 11; minute = 0 }; controller.loopMainThreadUntilIdle() }
            })
            onView(withId(android.R.id.button1)).perform(click())
            saveVisit()
            ui.onNodeWithTag("demo-appointment-error").assertTextEquals("This overlaps another appointment.")
            ui.onNodeWithText("Close", useUnmergedTree = true).performScrollTo().performClick()
            visitAction(booked.id, "Cancel visit")
            ui.onNodeWithTag("demo-confirm-cancel").performClick()
            ui.waitForIdle()
            assertEquals("Cancelled", store.appointments().single { it.id == booked.id }.status)
            visitAction(completeFixture.id, "Mark complete")
            ui.waitForIdle()
            assertEquals("Completed", store.appointments().single { it.id == completeFixture.id }.status)
            assertEquals(store.appointments(), DoctorDemoStore(context, preferencesName = preferencesName).appointments())
            screenshot("doctor-demo-schedule")
            ui.onNodeWithTag("demo-nav-Account").performClick()
            screenshot("doctor-account")
            ui.onNodeWithTag("demo-exit").performScrollTo().performClick()
            ui.runOnIdle { assertTrue(exited.value) }
            assertEquals(before, account.all)
        } finally {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private fun visitAction(id: String, label: String) {
        ui.onNode(hasText(label) and hasAnyAncestor(hasTestTag("demo-appointment-$id"))).performScrollTo().performClick()
    }
    private fun openRecordSection(section: String) {
        if (ui.onAllNodesWithTag("demo-patient-hub").fetchSemanticsNodes().isEmpty()) {
            ui.onNodeWithTag("demo-patient-back").performClick()
        }
        ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-$section"))
        ui.onNodeWithTag("demo-record-$section").performClick()
    }
    private fun saveVisit() {
        closeSoftKeyboard()
        ui.waitForIdle()
        ui.onNodeWithTag("demo-save-appointment").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
