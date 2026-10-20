package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Stateless fictional catalogues only. No actual account, store, audio or provider. */
class DoctorConversationsUiTest {
    @get:Rule val ui=createComposeRule()
    private val now=DoctorPatientTracking.asOf+86_400_000L
    private fun source(id:String)="[$id] Patient: I do not want a decision invented.\n[$id] Family: We have questions for the next conversation.\n[$id] Patient: Please keep my exact words together."
    private fun version(patient:String,id:String,number:Int)=WorkflowVersion(id,patient,number,
        WorkflowDraft(patient,sourceText=source(id),topics="Fictional discussion $id",questions="Questions remain open."),"Sample reviewer $id",now-number*1000L)
    private fun model(patient:String="demo-leela",latestSource:String?=null):DoctorConversationsModel {
        val latest=version(patient,"$patient-latest",2).let {if(latestSource==null) it else it.copy(draft=it.draft.copy(sourceText=latestSource))}
        return DoctorConversationsData.build(patient,listOf(version(patient,"$patient-earlier",1),latest),
            WorkflowDraft(patient,sourceText="Unfinished fictional draft; never a published discussion.",updatedAt=now-500),now)!!
    }

    @Test fun normalCatalogueSeparatesDraftAndPublishedVersionsAndReadsExactSources() {
        val state=mutableStateOf(model());var continued=0
        render(state,1f) {continued++}
        ui.onNodeWithTag("conversation-draft").assertExists()
        ui.onNodeWithTag("conversation-latest").assertExists()
        ui.onNodeWithTag("conversation-earlier").assertExists()
        screenshot("doctor-conversations-normal")
        val originalDraft=state.value.currentDraft
        ui.onNodeWithTag("workflow-open-conversation").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1,continued);assertEquals(originalDraft,state.value.currentDraft)
        read("demo-leela-latest")
        ui.onNodeWithTag("conversation-source-demo-leela-latest").assertTextEquals(source("demo-leela-latest"))
        read("demo-leela-earlier")
        ui.onNodeWithTag("conversation-source-demo-leela-earlier").assertTextEquals(source("demo-leela-earlier"))
        ui.onNodeWithTag("conversation-source-demo-leela-latest").assertTextEquals(source("demo-leela-latest"))
        read("demo-leela-prior")
        ui.onNodeWithTag("conversation-source-demo-leela-prior").assertTextEquals(state.value.priorSample!!.sourceText)
        ui.onNodeWithText("Date not recorded").assertExists()
        ui.onNodeWithText("Author not recorded · fictional sample").assertExists()
    }

    @Test fun largeTextExactReaderStaysVisibleAndFollowsPatientAndSnapshotChanges() {
        val state=mutableStateOf(model());render(state,1.5f) {}
        read("demo-leela-latest")
        val sourceNode=ui.onNodeWithTag("conversation-source-demo-leela-latest")
        sourceNode.performScrollTo().assertTextEquals(source("demo-leela-latest"))
        val layouts=mutableListOf<TextLayoutResult>()
        sourceNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.single().lineCount>3);assertFalse(layouts.single().multiParagraph.didExceedMaxLines)
        screenshot("doctor-conversations-visible-large")
        ui.onNodeWithText("Close discussion").assertDoesNotExist()
        ui.runOnIdle {state.value=model(latestSource="Changed fictional source, same version identity.")}
        ui.onNodeWithTag("conversation-source-demo-leela-latest").assertTextEquals("Changed fictional source, same version identity.")
        ui.runOnIdle {state.value=model("demo-ravi")}
        ui.onNodeWithTag("conversation-card-demo-leela-latest").assertDoesNotExist()
        read("demo-ravi-latest")
        ui.onNodeWithTag("conversation-source-demo-ravi-latest").assertTextEquals(source("demo-ravi-latest"))
        assertEquals("demo-ravi",state.value.currentDraft!!.patientId)
    }

    @Test fun returningFromIsolatedWorkspaceRefreshesPublishedHistoryWithoutLosingDraft() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val suffix=UUID.randomUUID().toString()
        val demoName="doctor-demo-conversations-$suffix"
        val workflowName="doctor-workflow-conversations-$suffix"
        val demo=DoctorDemoStore(context,preferencesName=demoName)
        val workflow=DoctorWorkflowStore(context,preferencesName=workflowName)
        val draft=WorkflowDraft("demo-leela",sourceText="Fictional unchanged workspace source.",topics="Fictional workspace topic.")
        assertNull(workflow.saveDraft(draft))
        try {
            ui.setContent {CareTheme {DoctorDemo({},demo,workflow)}}
            ui.onNodeWithTag("demo-login").performScrollTo().performClick()
            ui.onNodeWithTag("demo-nav-Patients").performClick()
            ui.onNodeWithTag("demo-patient-search").performTextReplacement("Leela");Espresso.closeSoftKeyboard()
            ui.onNodeWithTag("demo-patient-demo-leela").performClick()
            ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-Conversations"))
            ui.onNodeWithTag("demo-record-Conversations").performClick()
            ui.onNodeWithTag("conversation-latest").assertDoesNotExist()
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("workflow-open-conversation"))
            ui.onNodeWithTag("workflow-open-conversation").performClick()
            ui.runOnIdle {assertNull(workflow.publish("demo-leela","Fictional reviewer",true,true))}
            val published=workflow.versions("demo-leela").single()
            ui.onNodeWithTag("workflow-back").performScrollTo().performClick()
            ui.onNodeWithTag("conversation-latest").assertExists()
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("conversation-source-${published.id}"))
            ui.onNodeWithTag("conversation-source-${published.id}").assertTextEquals(draft.sourceText)
            assertEquals(draft.sourceText,workflow.draft("demo-leela").sourceText)
            assertEquals(draft.topics,workflow.draft("demo-leela").topics)
            assertTrue(workflow.versions("demo-ravi").isEmpty())
        } finally {
            context.getSharedPreferences(demoName,Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(workflowName,Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun render(model:MutableState<DoctorConversationsModel>,scale:Float,onContinue:()->Unit) {
        ui.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                CareTheme {Column(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                    DoctorConversationsContent(model.value,onContinue)
                }}
            }
        }
    }
    private fun read(id:String) {ui.onNodeWithTag("conversation-source-$id").performScrollTo().assertExists()}
    private fun screenshot(name:String) {
        ui.mainClock.advanceTimeBy(500);ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/$name.png")
        target.parentFile!!.mkdirs();val bitmap=instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
