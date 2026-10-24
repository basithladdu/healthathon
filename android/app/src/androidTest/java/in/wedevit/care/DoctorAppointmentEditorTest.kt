package `in`.wedevit.care

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Isolated fictional editor: no account, persistence, network or system setting changes. */
class DoctorAppointmentEditorTest {
    @get:Rule val ui = createComposeRule()

    @Test fun lockedReschedulePreservesIdentityAndEveryExistingValue() {
        val start = LocalDate.now().plusDays(4).atTime(15, 25).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val existing = DoctorDemoAppointment("editor-test-visit", "demo-leela", start, 45, "Video", "Scheduled", "Bring questions")
        var saved: DoctorDemoAppointment? = null
        ui.setContent { CareTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DemoAppointmentEditor(DoctorDemoData.patients, existing.patientId, existing, LocalDate.now(), patientLocked = true, onDismiss = {}, onSave = { saved = it; null })
            }
        } }
        ui.onNodeWithTag("demo-change-patient").assertDoesNotExist()
        ui.onNodeWithText("Patient linked to the conversation request").assertExists()
        ui.onNodeWithTag("demo-save-appointment").performScrollTo().assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(existing, saved) }
    }

    @Test fun patientDropdownAndCompactDateChoiceAreSaved() {
        val preferred = LocalDate.now().plusDays(5)
        var saved: DoctorDemoAppointment? = null
        ui.setContent { CareTheme { DemoAppointmentEditor(DoctorDemoData.patients, "demo-asha", null, preferred, onDismiss = {}, onSave = { saved = it; null }) } }
        ui.onNodeWithTag("demo-change-patient").performClick()
        ui.onNodeWithTag("demo-visit-patient-demo-ravi").performClick()
        ui.onNodeWithTag("demo-visit-day-1").performScrollTo().performClick()
        ui.onNodeWithTag("demo-visit-type-Phone").performScrollTo().performClick()
        ui.onNodeWithTag("demo-save-appointment").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals("demo-ravi", saved?.patientId)
            assertEquals("Phone", saved?.type)
            assertEquals(preferred.plusDays(1).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), saved?.startMillis)
            assertEquals(30, saved?.durationMinutes)
        }
    }
}
