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
    private val sections=listOf("Summary","Progress","Medicines","Conversations","Visits","Reports")
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
    @Test fun referenceGridHasTwoWideRowsAndPairedCardsAt320Dp() {
        val opened=mutableListOf<String>()
        ui.setContent {CareTheme {Column(Modifier.width(320.dp).fillMaxSize().verticalScroll(rememberScrollState())){DoctorPatientNavigationTiles({opened+=it})}}}
        fun bounds(section:String)=ui.onNodeWithTag("doctor-shared-$section").fetchSemanticsNode().boundsInRoot
        val overview=bounds("Summary");val progress=bounds("Progress");val medicines=bounds("Medicines");val conversations=bounds("Conversations");val visits=bounds("Visits");val reports=bounds("Reports")
        assertTrue(overview.bottom<progress.top);assertEquals(progress.top,medicines.top,1f);assertTrue(progress.right<medicines.left)
        assertEquals(progress.height,medicines.height,1f);assertEquals(overview.width,conversations.width,1f)
        assertTrue(conversations.top>progress.bottom);assertTrue(visits.top>conversations.bottom)
        assertEquals(visits.top,reports.top,1f);assertEquals(visits.height,reports.height,1f)
        sections.forEach {section->ui.onNodeWithTag("doctor-shared-$section").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()}
        assertEquals(sections,opened)
    }
    @Test fun largeTextUsesFullWidthGrowingRowsAndKeepsAllSixActions() {
        ui.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){CareTheme {Column(Modifier.width(320.dp).fillMaxSize().verticalScroll(rememberScrollState())){DoctorPatientNavigationTiles({})}}}}
        val expectedWidth=ui.onNodeWithTag("doctor-shared-Summary").fetchSemanticsNode().boundsInRoot.width
        sections.forEach {section->
            val node=ui.onNodeWithTag("doctor-shared-$section");node.performScrollTo().assertIsDisplayed().assertHasClickAction()
            val bounds=node.fetchSemanticsNode().boundsInRoot
            assertEquals(expectedWidth,bounds.width,1f)
            val title=if(section=="Summary")"Overview"else section
            ui.onNodeWithText(title).assertIsDisplayed()
        }
    }
}
