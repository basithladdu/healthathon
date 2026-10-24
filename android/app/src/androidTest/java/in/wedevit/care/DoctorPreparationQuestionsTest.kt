package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class DoctorPreparationQuestionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun addEditAndConfirmedRemoveStayPreparationOnly() {
        val disk=mutableMapOf<String,String>()
        val store=DoctorPreparationStore({disk[it]},{key,value->disk[key]=value;true})
        compose.setContent { CareTheme { DoctorPreparationQuestions(store,"isolated-test",true,{true},{}) } }
        compose.onNodeWithTag("doctor-add-preparation-question").performClick()
        compose.onNodeWithTag("doctor-preparation-question-input").performTextInput("Who should join?")
        compose.onNodeWithText("Save question").performClick()
        compose.onNodeWithText("Who should join?").assertIsDisplayed()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithTag("doctor-preparation-question-input").performTextReplacement("What matters?")
        compose.onNodeWithText("Save question").performClick()
        compose.onNodeWithText("Remove",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Remove question").performClick()
        compose.runOnIdle { assertEquals(emptyList<DoctorPreparationQuestion>(),store.load("isolated-test")) }
    }
}
