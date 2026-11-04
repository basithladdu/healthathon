package `in`.wedevit.care

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises only the explicitly isolated demo; never constructs a default CareModel. */
class DoctorCareDemoPersistenceTest {
    @Test fun seedTwiceAndImmediateReopenPreservePracticeDraftAndHistory() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val models=mutableListOf<CareModel>()
        var original:Conversation?=null;var restored=false
        suspend fun open():CareModel=withContext(Dispatchers.Main){CareModel.createDoctorDemo(app).also{models+=it}}
        try {
            val first=open();DoctorCareDemoData.seed(first,app)
            assertTrue(first.demoMode);assertNull(first.cloud);assertTrue(first.accountNamespace.isBlank())
            assertEquals(CareModel.DOCTOR_DEMO_STORAGE,first.storageNamespace)
            original=withTimeout(15_000){first.draft.first{it.personId==DoctorCareDemoData.PERSON_ID&&it.source.isNotBlank()}}
            val versions=withTimeout(15_000){first.versions.first{it.isNotEmpty()}}
            val history=MedicineHistoryRepo(app,null,first.storageNamespace).load(DoctorCareDemoData.PERSON_ID)
            val contacts=LocalCareContacts(app,first.storageNamespace).list(DoctorCareDemoData.PERSON_ID)
            DoctorCareDemoData.seed(first,app)
            assertEquals(original,first.draft.value)
            val marker="\nQA practice edit — persistence check only."
            withContext(Dispatchers.Main){first.edit(original!!.copy(source=original!!.source+marker))}
            // Deliberately reopen before awaiting close: the shared gate must serialize it.
            val close=withContext(Dispatchers.Main){first.closeDoctorDemo()}
            val second=open();DoctorCareDemoData.seed(second,app);close.join()
            assertFalse(close.isCancelled)
            val reloaded=withTimeout(15_000){second.draft.first{it.personId==DoctorCareDemoData.PERSON_ID&&it.source.endsWith(marker)}}
            assertEquals(original!!.source+marker,reloaded.source)
            assertEquals(versions,withTimeout(15_000){second.versions.first{it.isNotEmpty()}})
            assertEquals(history,MedicineHistoryRepo(app,null,second.storageNamespace).load(DoctorCareDemoData.PERSON_ID))
            assertEquals(contacts,LocalCareContacts(app,second.storageNamespace).list(DoctorCareDemoData.PERSON_ID))
            withContext(Dispatchers.Main){second.edit(original!!)}
            withContext(Dispatchers.Main){second.closeDoctorDemo()}.join()
            val third=open();DoctorCareDemoData.seed(third,app)
            val final=withTimeout(15_000){third.draft.first{it.personId==DoctorCareDemoData.PERSON_ID&&it.source==original!!.source}}
            assertEquals(NoteRules.values(original!!),NoteRules.values(final));restored=true
        } finally {
            models.forEach {withContext(Dispatchers.Main){it.closeDoctorDemo()}.join()}
            if(!restored&&original!=null) {
                val repair=open();DoctorCareDemoData.seed(repair,app)
                withContext(Dispatchers.Main){repair.edit(original!!)}
                withContext(Dispatchers.Main){repair.closeDoctorDemo()}.join()
            }
        }
    }
}

/** Same actual six care components, with demo records and all four Doctor tabs. */
class DoctorCareDemoFlowTest {
    @get:Rule val ui=createComposeRule()
    @Test fun isolatedSampleOpensAllSixActualCareScreensWithoutLeavingPatients() {
        ui.setContent {CareTheme{DoctorDemo(onExit={})}}
        ui.onNodeWithTag("demo-login").performScrollTo().performClick()
        ui.onNodeWithTag("demo-nav-Patients").performClick()
        ui.onNodeWithTag("walkthrough-patients-list").performScrollToNode(hasText("Rahul"))
        ui.onNodeWithText("Rahul").performClick()
        waitFor("doctor-care-demo-hub")
        val routes=listOf("Progress" to "doctor-checkin-history","Labs" to "doctor-labs","Medicines" to "doctor-medicines","Conversations" to "doctor-conversations","Visits" to "doctor-visits","Reports" to "documents-list")
        routes.forEach { (section,tag) ->
            ui.onNodeWithTag("doctor-shared-patient-hub").performScrollToNode(hasTestTag("doctor-shared-$section"))
            ui.onNodeWithTag("doctor-shared-$section").performClick();waitFor(tag)
            if(section=="Progress") ui.onNodeWithTag("doctor-condition-chart").assertDoesNotExist()
            if(section=="Medicines") ui.waitUntil(15_000){ui.onAllNodesWithText("Morphine sulfate 10 mg").fetchSemanticsNodes().isNotEmpty()}
            if(section=="Conversations") {
                ui.onNodeWithTag("doctor-conversation-card-2").performScrollTo().performClick();ui.onNodeWithText("All saved conversation versions.").assertExists();screenshot("doctor-demo-conversation-history-filters");ui.onNodeWithContentDescription("Back to patient care").performClick();waitFor("doctor-conversations")
            }
            if(section=="Reports") {
                ui.waitUntil(15_000){ui.onAllNodesWithText("2 care notes · 4 reports").fetchSemanticsNodes().isNotEmpty()}
                ui.onNode(hasText("Reports") and hasClickAction()).performClick()
                ui.onNodeWithTag("documents-list").performScrollToNode(hasText("Rahul care planning notes.png"))
            }
            if(section=="Visits") ui.waitUntil(15_000){ui.onAllNodesWithText("Dr Vikram Rao",substring=true).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("Test record").assertExists()
            ui.onNodeWithTag("demo-nav-Patients").assertIsSelected()
            screenshot("doctor-demo-${section.lowercase()}")
            if(section=="Visits") {
                val completed=DoctorTestProfiles.fixture(DoctorTestProfiles.profiles.first(),System.currentTimeMillis()).items.first {it.kind=="visit"&&CareEntryCodec.decode(it.detail)?.status=="completed"&&CareEntryCodec.decode(it.detail)?.sender?.contains("Rao")==true}
                val card="doctor-visit-card-${completed.id}"
                ui.onNodeWithTag("doctor-visits").performScrollToNode(hasTestTag(card))
                ui.onAllNodesWithTag(card).onFirst().performClick();waitFor("doctor-visit-details")
                screenshot("doctor-demo-visit-details")
                ui.onNodeWithTag("doctor-visit-details").performScrollToNode(hasText("Discussion linked to this visit"))
                screenshot("doctor-demo-visit-sources")
                ui.onNodeWithContentDescription("Back to patient care").performClick();waitFor("doctor-visits")
            }
            ui.onNodeWithContentDescription("Back to patient care").performClick();waitFor("doctor-care-demo-hub")
        }
        ui.onNodeWithContentDescription("Back to patient care").performClick()
        waitFor("walkthrough-patients-list")
        ui.onNodeWithText("Test workspace").assertExists()
        ui.onNodeWithText("Explore sample care").assertDoesNotExist()
    }
    private fun waitFor(tag:String) {ui.waitUntil(20_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()};ui.waitForIdle()}
    private fun screenshot(name:String) {
        ui.mainClock.advanceTimeBy(600);ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
