package `in`.wedevit.care

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

/** Component-only fixtures: no CareModel, patient records, account, cloud or doctor assignment. */
class PatientDoctorUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun absentLinkIsHonestNonInteractiveAndDoesNotInventDoctor() {
        ui.setContent { CareTheme { PatientDoctorRow(null) } }
        ui.onNodeWithTag("patient-doctor-empty").assertTextEquals("No doctor linked").assertHasNoClickAction()
        ui.onNodeWithTag("patient-doctor-name").assertDoesNotExist()
        ui.onNodeWithTag("patient-doctor-details").assertDoesNotExist()
        ui.onNodeWithText("Dr Meera", substring = true).assertDoesNotExist()
    }

    @Test fun homeShowsNameOnlyAndDetailsDoNotClaimProfessionalVerification() {
        val doctor = LinkedDoctorSummary("fictional-doctor-a", "Dr Fictional A", "Sample specialty", "Sample clinic")
        ui.setContent { CareTheme { PatientDoctorRow(doctor) } }
        ui.onNodeWithTag("patient-doctor-name").assertTextEquals("Dr Fictional A").assertHasClickAction()
        ui.onNodeWithText("Sample specialty").assertDoesNotExist()
        ui.onNodeWithText("Sample clinic").assertDoesNotExist()
        ui.onNodeWithTag("patient-doctor-name").performClick()
        ui.onNodeWithTag("patient-doctor-details").assertExists()
        ui.onNodeWithTag("patient-doctor-status").assertTextContains("not verified", ignoreCase = true)
        ui.onNodeWithText("Sample specialty").assertIsDisplayed()
        ui.onNodeWithText("Sample clinic").assertIsDisplayed()
        ui.onNodeWithTag("patient-doctor-close").performClick()
        ui.onNodeWithTag("patient-doctor-details").assertDoesNotExist()
    }

    @Test fun replacingPatientLinkClearsOldDetailsAndPendingIsNotVerified() {
        val linked = mutableStateOf<LinkedDoctorSummary?>(LinkedDoctorSummary("fictional-doctor-a", "Dr Fictional A", clinic = "Clinic A"))
        ui.setContent { CareTheme { PatientDoctorRow(linked.value) } }
        ui.onNodeWithTag("patient-doctor-name").performClick()
        ui.onNodeWithText("Clinic A").assertIsDisplayed()
        ui.runOnIdle { linked.value = null }
        ui.onNodeWithTag("patient-doctor-details").assertDoesNotExist()
        ui.onNodeWithTag("patient-doctor-empty").assertIsDisplayed()
        ui.runOnIdle { linked.value = LinkedDoctorSummary("fictional-doctor-b", "Dr Fictional B", clinic = "Clinic B", verification = PatientDoctorVerification.PENDING) }
        ui.onNodeWithTag("patient-doctor-name").assertTextEquals("Dr Fictional B").performClick()
        ui.onNodeWithTag("patient-doctor-status").assertTextContains("pending", ignoreCase = true)
        ui.onNodeWithText("Clinic B").assertIsDisplayed()
        ui.onNodeWithText("Clinic A").assertDoesNotExist()
    }
}
