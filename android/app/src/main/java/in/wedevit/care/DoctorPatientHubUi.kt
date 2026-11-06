package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun doctorHubSingleColumn(widthDp: Float, fontScale: Float): Boolean = widthDp < 340f || fontScale >= 1.3f

/** Rows only: the caller owns the patient header, scroll surface, routes and Back behaviour. The earlier practice record keeps its six sections. */
@Composable fun DoctorPatientHub(patient: DoctorDemoPatient, onSection: (String) -> Unit, modifier: Modifier = Modifier, meta: Map<String,String> = emptyMap()) {
    DoctorPatientNavigationTiles(onSection,modifier.testTag("doctor-patient-hub-${patient.id}"),"demo-record",meta,tabs=doctorLegacyHubTabs)
}

/**
 * What a doctor does for this patient, in order: the first section (Goals of care) is the screen's one filled action,
 * the rest one grouped list, one row each with its colour chip, name, a short live line and an arrow.
 * Every row is full width at every text size, so no word is ever split. [selected] marks the section open beside it in the two-pane layout.
 */
@Composable internal fun DoctorPatientNavigationTiles(onSection:(String)->Unit,modifier:Modifier=Modifier,tagPrefix:String="doctor-shared",meta:Map<String,String> = emptyMap(),tabs:List<DoctorPatientTab> = doctorPatientTabs,selected:String?=null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tabs.firstOrNull()?.let { PatientHubPrimaryRow(it, onSection, tagPrefix, meta[it.section], selected == it.section) }
        val rest = tabs.drop(1)
        if (rest.isNotEmpty()) Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, SaharaColors.Edge), modifier = Modifier.fillMaxWidth().saharaAppear()) {
            Column {
                rest.forEachIndexed { index, tab ->
                    if (index > 0) HorizontalDivider(color = SaharaColors.Surface, thickness = 2.dp)
                    Box(Modifier.saharaStagger(index + 1)) { DoctorSectionRow(tab, onSection, tagPrefix, meta[tab.section], selected == tab.section) }
                }
            }
        }
    }
}

/** Each patient section keeps its own colour (the same as the patient app's) on the hub row, the section bar and the Sections sheet. */
internal fun doctorTabTone(section: String): DoctorTone = when (section) {
    "GoalsOfCare" -> SaharaSection.GoalsOfCare
    "Conversations" -> SaharaSection.Conversations
    "CarePlan" -> SaharaSection.Emergency
    "Reports" -> SaharaSection.Documents
    "Progress", "Summary" -> SaharaSection.Symptoms
    "Labs" -> SaharaSection.Labs
    "Visits" -> SaharaSection.Visits
    "Medicines" -> SaharaSection.Medicines
    else -> DoctorTones.Teal
}

/** The first thing a doctor does here: a solid button-like row in the section colour with white text. */
@Composable private fun PatientHubPrimaryRow(tab: DoctorPatientTab, onSection: (String) -> Unit, tagPrefix: String, meta: String?, selected: Boolean) { val t = careText()
    val tone = doctorTabTone(tab.section)
    val press = remember { MutableInteractionSource() }
    Surface(onClick = { onSection(tab.section) }, interactionSource = press, shape = RoundedCornerShape(16.dp), color = tone.solid, contentColor = tone.onSolid, shadowElevation = 2.dp,
        border = if (selected) BorderStroke(3.dp, DoctorCardInk) else null,
        modifier = Modifier.fillMaxWidth().saharaAppear().saharaPulseGlow(tone.solid, 16.dp, active = !selected).saharaPress(press).heightIn(min = 64.dp).testTag("$tagPrefix-${tab.section}").semantics { role = Role.Button; semanticsSelected = selected }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(tab.icon, null, tint = tone.onSolid, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t(tab.en, tab.hi), fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, fontFamily = SaharaTitleFont, color = tone.onSolid, style = DoctorWholeWords)
                if (!meta.isNullOrBlank()) Text(meta, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = tone.onSolid, style = DoctorWholeWords, modifier = Modifier.testTag("$tagPrefix-${tab.section}-meta"))
            }
            Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tone.onSolid, modifier = Modifier.size(22.dp))
        }
    }
}

/** One section in a list: colour chip, name, optional live line, arrow. Selected: tinted with a bar in the section colour. */
@Composable internal fun DoctorSectionRow(tab: DoctorPatientTab, onSection: (String) -> Unit, tagPrefix: String, meta: String? = null, selected: Boolean = false) { val t = careText()
    val tone = doctorTabTone(tab.section)
    val press = remember { MutableInteractionSource() }
    // Every row sits on its section's wash with a solid edge bar in its colour; the open one's bar is twice as wide.
    Surface(onClick = { onSection(tab.section) }, interactionSource = press, color = tone.wash, contentColor = DoctorCardInk,
        modifier = Modifier.fillMaxWidth().saharaPress(press).heightIn(min = 56.dp).testTag("$tagPrefix-${tab.section}").semantics { role = Role.Button; semanticsSelected = selected }) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (selected) 10.dp else 5.dp).fillMaxHeight().background(tone.solid))
            Row(Modifier.weight(1f).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DoctorIconChip(tab.icon, tone, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t(tab.en, tab.hi), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, style = DoctorWholeWords)
                    if (!meta.isNullOrBlank()) Text(meta, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = tone.ink, style = DoctorWholeWords, modifier = Modifier.testTag("$tagPrefix-${tab.section}-meta"))
                }
                Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tone.ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}
