package `in`.wedevit.care
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
class CareInformationUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun privacyRetainsStorageExceptionsAndBack() {
        var backs = 0
        ui.setContent { CareTheme { CareInformationScreen("privacy") { backs++ } } }
        ui.onNodeWithText("Your care record").assertIsDisplayed()
        ui.onNodeWithTag("care-information-privacy").performScrollToNode(hasText("Files and recordings explicitly uploaded in the doctor's test tools are separate protected uploads."))
        ui.onNodeWithText("Files and recordings explicitly uploaded in the doctor's test tools are separate protected uploads.").assertIsDisplayed()
        ui.onNodeWithTag("care-information-privacy").performScrollToNode(hasText("Read full privacy policy"))
        ui.onNodeWithText("Read full privacy policy").assertHasClickAction()
        ui.onNodeWithTag("care-information-privacy").performScrollToNode(hasContentDescription("Back to Account"))
        ui.onNodeWithContentDescription("Back to Account").performClick()
        ui.runOnIdle { assertEquals(1, backs) }
    }
    @Test fun trackerIsOneTapAndAccountReplacesMore() {
        var destination = -1
        ui.setContent { CareTheme { CareNavigation(0, false) { destination = it } } }
        ui.onNodeWithText("More").assertDoesNotExist()
        ui.onNodeWithTag("nav-Symptoms").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(9, destination) }
        ui.onNodeWithTag("nav-Account").performClick()
        ui.runOnIdle { assertEquals(4, destination) }
        ui.onNodeWithTag("nav-Care").assertIsDisplayed()
    }
}
