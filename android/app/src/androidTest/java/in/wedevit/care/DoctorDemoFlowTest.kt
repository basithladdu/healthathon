package `in`.wedevit.care

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.compose.ui.semantics.SemanticsActions
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
        // The Patients tab lists the walkthrough people now; a demo record (Asha) opens directly (initialPatientId), as the walkthrough does.
        val record = mutableStateOf<String?>(null)
        try {
            ui.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    CareTheme { key(record.value) { DoctorDemo(onExit = {}, store = store, initialPatientId = record.value) } }
                }
            }
            ui.onNodeWithTag("demo-email").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("demo-password").performScrollTo().assertIsDisplayed()
            screenshot("doctor-login-large-text")
            ui.onNodeWithTag("demo-login").performScrollTo().assertIsDisplayed().performClick()
            ui.onNodeWithTag("demo-home-list").assertExists()
            screenshot("doctor-home-large-text")
            ui.onNodeWithTag("demo-nav-Patients").assertIsDisplayed().performClick()
            ui.onNodeWithTag("walkthrough-patients-list").assertExists()
            ui.onNodeWithTag("doctor-patient-search").assertIsDisplayed()
            ui.onNodeWithTag("demo-nav-Schedule").assertIsDisplayed().performClick()
            ui.onNodeWithTag("doctor-schedule").assertExists()
            screenshot("doctor-schedule-large-text")
            ui.onNodeWithTag("demo-nav-Account").assertIsDisplayed().performClick()
            ui.onNodeWithTag("demo-exit").performScrollTo().assertIsDisplayed()
            screenshot("doctor-account-large-text")
            ui.runOnIdle { record.value = "demo-asha" }
            ui.onNodeWithTag("demo-patient-hub").assertExists()
            listOf("Summary", "Progress", "Medicines", "Conversations", "Visits", "Reports").forEach {
                openRecordSection(it)
            }
            screenshot("doctor-record-large-text")
            openRecordSection("Visits")
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("demo-schedule-visit"))
            ui.onNodeWithTag("demo-schedule-visit").assertIsDisplayed()
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
        // After signing in, Asha's demo record opens directly (initialPatientId): the Patients tab lists the walkthrough people now.
        val record = mutableStateOf<String?>(null)
        try {
            ui.setContent { CareTheme { if (!exited.value) key(record.value) { DoctorDemo(onExit = { exited.value = true }, store = store, initialPatientId = record.value) } } }
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
            ui.onNodeWithTag("demo-home-list").assertExists()
            ui.onNodeWithTag("demo-nav-Patients").performClick()
            ui.onNodeWithTag("walkthrough-patients-list").assertExists()
            screenshot("doctor-patients")
            ui.runOnIdle { record.value = "demo-asha" }
            ui.onNodeWithTag("demo-patient-hub").assertExists()
            screenshot("doctor-demo-patient")
            listOf("Summary", "Progress", "Medicines", "Conversations", "Visits", "Reports").forEach {
                openRecordSection(it)
                screenshot("doctor-record-${it.lowercase().replace(' ', '-')}")
            }
            // Visits are booked and managed from the patient's Visits section (the Schedule tab is the practice calendar now).
            openRecordSection("Visits")
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("demo-schedule-visit"))
            ui.onNodeWithTag("demo-schedule-visit").performClick()
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
            visitAction(booked.id, "Reschedule")
            ui.onNodeWithTag("demo-visit-time").performScrollTo().performClick()
            pickClock(hourIndex = 10, minuteIndex = 0, ampmIndex = 0) // 11:00 AM
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
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("demo-schedule-visit"))
            ui.onNodeWithTag("demo-schedule-visit").performClick()
            ui.onNodeWithTag("demo-visit-date").performScrollTo().performClick()
            pickDate(tomorrow)
            ui.onNodeWithTag("demo-visit-time").performScrollTo().performClick()
            pickClock(hourIndex = 10, minuteIndex = 0, ampmIndex = 0) // 11:00 AM, overlapping the rescheduled visit
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
            ui.onNodeWithTag("demo-exit").performScrollTo().performClick(); ui.onNodeWithTag("account-sign-out-confirm").performClick()
            ui.runOnIdle { assertTrue(exited.value) }
            assertEquals(before, account.all)
        } finally {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private fun visitAction(id: String, label: String) {
        ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("demo-appointment-$id"))
        ui.onNode(hasText(label) and hasAnyAncestor(hasTestTag("demo-appointment-$id"))).performScrollTo().performClick()
    }
    private fun openRecordSection(section: String) {
        if (ui.onAllNodesWithTag("demo-patient-hub").fetchSemanticsNodes().isEmpty()) {
            ui.onNodeWithTag("demo-patient-back").performClick()
        }
        ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-$section"))
        ui.onNodeWithTag("demo-record-$section").performClick()
    }
    /**
     * [CareClockDialog]: three scroll wheels (tags care-clock-hour / -minute / -ampm, rows tagged "<wheel>-<index>"). Scrolling a wheel to
     * an index puts that row in the middle, which is the chosen value (hour index 0 = 1 o'clock, minute index = minutes / 5, AM = 0, PM = 1).
     */
    private fun pickClock(hourIndex: Int, minuteIndex: Int, ampmIndex: Int) {
        ui.onNodeWithTag("care-clock-dialog").assertExists()
        listOf("care-clock-hour" to hourIndex, "care-clock-minute" to minuteIndex, "care-clock-ampm" to ampmIndex).forEach { (wheel, index) ->
            ui.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(wheel))).performScrollToIndex(index)
            ui.waitForIdle()
            ui.onNodeWithTag("$wheel-$index").assertExists()
        }
        ui.onNodeWithTag("care-clock-ok").performClick()
        ui.waitForIdle()
        ui.onNodeWithTag("care-clock-dialog").assertDoesNotExist()
    }

    /**
     * [CareDatePickerDialog]: the Material 3 date picker (calendar only, no keyboard toggle), then OK (care-date-ok). A day cell's text is
     * the full date written the way Material writes it (skeleton "yMMMMEEEEd", the device locale); today's cell starts with
     * "Today, ", so the match is a substring. Moves one month on if [day] is not in the month shown.
     */
    private fun pickDate(day: LocalDate) {
        val locale = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]
        // As Material's CalendarModelImpl formats it: the locale's best pattern for the skeleton, through java.time.
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMMEEEEd")
        val label = java.time.format.DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(java.time.format.DecimalStyle.of(locale)).format(day)
        val cell = hasText(label, substring = true) and hasClickAction()
        if (ui.onAllNodes(cell).fetchSemanticsNodes().isEmpty()) {
            ui.onNode(hasContentDescription(if (day.isBefore(LocalDate.now())) "Change to previous month" else "Change to next month")).performClick()
            ui.waitForIdle()
        }
        ui.onNode(cell).performClick()
        ui.onNodeWithTag("care-date-ok").assertIsEnabled().performClick()
        ui.waitForIdle()
        ui.onNodeWithTag("care-date-ok").assertDoesNotExist()
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
