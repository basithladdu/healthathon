package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Doctor-only presentation fixture; no patient model/account/database or route mutations. */
class DoctorPatientNavigationTilesTest {
    @get:Rule val ui=createComposeRule()
    private val sections=listOf("Progress","Labs","Conversations","Visits","Medicines","Reports")
    private val titles=mapOf("Progress" to "Symptoms & distress","Labs" to "Lab reports","Conversations" to "Goals of care","Visits" to "Visits","Medicines" to "Medicines","Reports" to "Documents")
    @Test fun actualHubLongPersonNameHasReadableLinesAboveItsSubtitle() {
        val person=Person("long-header-fixture","Fictional person with a deliberately long display name")
        ui.setContent {CareTheme {Box(Modifier.width(320.dp).fillMaxSize()){DoctorSharedPatientHub(person,{},deviceLocal=true)}}}
        val title=ui.onNodeWithText("${person.name}'s care")
        val subtitle=ui.onNodeWithText("Device-local care · not synced or shared")
        val layouts=mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue(layouts.single().lineCount>1)
        assertEquals(36.sp,layouts.single().layoutInput.style.lineHeight)
        assertTrue(title.fetchSemanticsNode().boundsInRoot.bottom<=subtitle.fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun symptomsTileIsWideThenTabsPairAt320Dp() {
        val opened=mutableListOf<String>()
        ui.setContent {CareTheme {Column(Modifier.width(320.dp).fillMaxSize().verticalScroll(rememberScrollState())){DoctorPatientNavigationTiles({opened+=it})}}}
        fun bounds(section:String)=ui.onNodeWithTag("doctor-shared-$section").fetchSemanticsNode().boundsInRoot
        val progress=bounds("Progress");val labs=bounds("Labs");val goals=bounds("Conversations");val visits=bounds("Visits");val medicines=bounds("Medicines");val documents=bounds("Reports")
        assertTrue(progress.bottom<labs.top);assertEquals(labs.top,goals.top,1f);assertTrue(labs.right<goals.left);assertEquals(labs.height,goals.height,1f)
        assertTrue(visits.top>labs.bottom);assertEquals(visits.top,medicines.top,1f)
        assertEquals(progress.width,documents.width,1f);assertTrue(documents.top>visits.bottom)
        sections.forEach {section->ui.onNodeWithTag("doctor-shared-$section").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()}
        assertEquals(sections,opened)
        ui.onNodeWithTag("doctor-shared-Summary").assertDoesNotExist()
    }
    @Test fun largeTextUsesFullWidthGrowingRowsAndKeepsAllSixActions() {
        ui.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){CareTheme {Column(Modifier.width(320.dp).fillMaxSize().verticalScroll(rememberScrollState())){DoctorPatientNavigationTiles({})}}}}
        val expectedWidth=ui.onNodeWithTag("doctor-shared-Progress").fetchSemanticsNode().boundsInRoot.width
        sections.forEach {section->
            val node=ui.onNodeWithTag("doctor-shared-$section");node.performScrollTo().assertIsDisplayed().assertHasClickAction()
            val bounds=node.fetchSemanticsNode().boundsInRoot
            assertEquals(expectedWidth,bounds.width,1f)
            ui.onNodeWithText(titles.getValue(section)).assertIsDisplayed()
        }
    }
}
