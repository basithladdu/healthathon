package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.compose.runtime.mutableIntStateOf

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
    @Test fun answerSavedByAnotherCardIsReloadedAndNotOverwrittenByTheNextSave() {
        val disk=mutableMapOf<String,String>()
        val store=DoctorPreparationStore({disk[it]},{key,value->disk[key]=value;true})
        store.save("isolated-reload",listOf(DoctorPreparationQuestion("q1","What matters most?"),DoctorPreparationQuestion("q2","Who should join?")))
        val reload=mutableIntStateOf(0)
        compose.setContent { CareTheme { DoctorPreparationAnswers(store,"isolated-reload",true,{true},reload=reload.intValue) } }
        compose.onNodeWithTag("doctor-preparation-unanswered-q1").assertExists()
        // A goal answer kept on leaving the transcript page lands after this card has loaded.
        compose.runOnIdle { store.save("isolated-reload",store.load("isolated-reload").map { if (it.id=="q1") it.copy(answer="Staying at home") else it }); reload.intValue++ }
        compose.onNodeWithTag("doctor-preparation-answer-q1").assertTextEquals("Staying at home")
        compose.onNodeWithTag("doctor-preparation-answer-input-q2").performTextInput("Daughter")
        compose.onNodeWithTag("doctor-preparation-answer-save-q2").performClick()
        compose.runOnIdle { assertEquals(listOf("Staying at home","Daughter"),store.load("isolated-reload").map { it.answer }) }
    }
}
