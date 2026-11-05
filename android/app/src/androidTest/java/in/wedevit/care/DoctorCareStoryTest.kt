package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Stateless fictional handover fixtures only; no account/model/database writes. */
class DoctorCareStoryTest {
    @get:Rule val ui=createComposeRule()
    @Test fun actualPersonLatestSourceAndEveryVersionRemainReachable() {
        val person=Person("story-fixture","Nila — fictional")
        fun version(n:Int,priority:String)=NoteRules.publish(person,Conversation(person.id,source="Fictional original source $n",priorities=priority,participants="Fictional participant",topics="Fictional discussion $n",questions="Question $n",nextSteps="Next step $n"),"Fictional reviewer $n",true,n).copy(id="story-$n",published=n*1000L)
        val old=version(1,"Earlier recorded priority");val latest=version(2,"Latest saved priority")
        val foreign=latest.copy(id="foreign",personId="different-person",priorities="Other person's private priority")
        val opened=mutableListOf<String>()
        ui.setContent {CareTheme {DoctorCareStory(person,listOf(old,foreign,latest)){opened+=it.id}}}
        ui.onNodeWithText(person.name).assertExists()
        ui.onNode(hasText("Latest saved priority") and hasAnyAncestor(hasTestTag("doctor-handover-glance"))).assertExists()
        ui.onNodeWithText("Last recorded reviewer").assertExists()
        ui.onNode(hasText(ReviewWorkflow.label(latest)) and hasAnyAncestor(hasTestTag("doctor-handover-glance"))).assertExists()
        ui.onNode(hasText("Clinical assessment") and hasAnyAncestor(hasTestTag("doctor-handover-glance"))).assertExists()
        ui.onNode(hasText("Not recorded") and hasAnyAncestor(hasTestTag("doctor-handover-glance"))).assertExists()
        ui.onNodeWithText("Asha").assertDoesNotExist();ui.onNodeWithText("Dr Ananya",substring=true).assertDoesNotExist()
        ui.onNodeWithText("Other person's private priority").assertDoesNotExist()
        listOf(latest,old).forEach {note->ui.onNodeWithTag("doctor-care-story").performScrollToNode(hasTestTag("doctor-handover-read-${note.id}"));ui.onNodeWithTag("doctor-handover-read-${note.id}").performClick()}
        assertEquals(listOf(latest.id,old.id),opened)
    }
    @Test fun missingNoteNeverInventsReviewerOrClinicalAssessment() {
        ui.setContent {CareTheme {DoctorCareStory(Person("empty-story","Fictional empty record"),emptyList(),{})}}
        ui.onNodeWithText("Fictional empty record").assertExists()
        ui.onAllNodesWithText("Not recorded").assertCountEquals(3)
        ui.onNodeWithText("Cancer",substring=true).assertDoesNotExist()
    }
}
