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
        // One field: type the question, then the + inside it adds it. Each saved question is its own editable field with Save and Remove.
        compose.onNodeWithTag("doctor-add-preparation-question").assertIsNotEnabled()
        compose.onNodeWithTag("doctor-preparation-question-input").performTextInput("Who should join?")
        compose.onNodeWithTag("doctor-add-preparation-question").performClick()
        val id=compose.runOnIdle { store.load("isolated-test").single().also { assertEquals("Who should join?",it.text) }.id }
        compose.onNodeWithTag("doctor-preparation-question-input-$id").assertTextContains("Who should join?")
        compose.onNodeWithTag("doctor-preparation-save-$id").assertDoesNotExist() // nothing to save until it is edited
        compose.onNodeWithTag("doctor-preparation-question-input-$id").performTextReplacement("What matters?")
        compose.onNodeWithTag("doctor-preparation-save-$id").performClick()
        compose.runOnIdle { assertEquals(listOf("What matters?"),store.load("isolated-test").map { it.text }) }
        compose.onNodeWithTag("doctor-preparation-remove-$id").performClick()
        compose.onNodeWithText("Remove question").performClick()
        compose.runOnIdle { assertEquals(emptyList<DoctorPreparationQuestion>(),store.load("isolated-test")) }
        compose.onNodeWithTag("doctor-preparation-empty",useUnmergedTree=true).assertExists()
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
