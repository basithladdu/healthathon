package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Stateless only: no CareModel, database, account, microphone or provider. */
class DoctorConversationLandingTest {
 @get:Rule val ui=createComposeRule()
 @Test fun threeIllustratedCardsDispatchInOrderAtLargeText() {
  val opened=mutableListOf<String>()
  ui.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){CareTheme{Box(Modifier.width(320.dp).fillMaxSize().safeDrawingPadding()){DoctorConversationLanding(true,{opened+="recent"},{opened+="record"},{opened+="history"})}}}}
  // Record is listed first now (the primary card); clicking by tag keeps the dispatch order explicit.
  listOf("doctor-conversation-card-0","doctor-start-conversation","doctor-conversation-card-2").forEach {ui.onNodeWithTag(it).performScrollTo().assertIsDisplayed().performClick()}
  assertEquals(listOf("recent","record","history"),opened)
  // 3 Nov meeting: the cards keep their titles only; the descriptions are gone.
  listOf("Revisit the latest saved discussion, care priorities and questions.","Capture a discussion, then review the draft before saving the note.","Browse earlier conversations by date or doctor, and read archived drafts.").forEach { ui.onNodeWithText(it).assertDoesNotExist() }
  listOf("Recent conversation","Record another conversation (not goals of care)","Conversation history").forEachIndexed { index,copy ->
   ui.onNodeWithText(copy).performScrollTo().assertIsDisplayed()
   val text=ui.onNodeWithTag("doctor-conversation-copy-$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
   val art=ui.onNodeWithTag("doctor-conversation-art-$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
   assertTrue("Illustration must not cover text",text.right<=art.left)
  }
  ui.onNodeWithText("Recent conversation").performScrollTo()
  val instrumentation=InstrumentationRegistry.getInstrumentation();val file=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/doctor-conversation-landing-large.png");file.parentFile!!.mkdirs();val bitmap=instrumentation.uiAutomation.takeScreenshot();file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  ui.onNodeWithText("Continue saved draft").assertDoesNotExist()
  ui.onNodeWithText("All saved conversation versions.").assertDoesNotExist()
 }
 @Test fun readOnlyStillOpensRecentAndHistoryButNotRecord() {
  ui.setContent {CareTheme{DoctorConversationLanding(false,{},{},{})}}
  ui.onNodeWithText("Recent conversation").assertIsEnabled()
  ui.onNodeWithTag("doctor-start-conversation").assertTextContains("Record another conversation (not goals of care)").assertIsNotEnabled()
  ui.onNodeWithText("Conversation history").assertIsEnabled()
 }
 @Test fun landingShowsPreviewsAndRecordIsPrimary() {
  ui.setContent {CareTheme{DoctorConversationLanding(true,{},{},{},recentPreview="Version 2",archivedCount=3)}}
  ui.onNodeWithText("Version 2").assertIsDisplayed()
  ui.onNodeWithText("3 saved conversations").assertIsDisplayed()
  ui.onNodeWithTag("doctor-start-conversation").assertHeightIsAtLeast(172.dp)
 }
}
