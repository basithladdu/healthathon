package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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

private val HubBorder = androidx.compose.ui.graphics.Color(0xFFD3C3B6)
private val HubGrey = androidx.compose.ui.graphics.Color(0xFF5E5249)

/**
 * What a doctor does for this patient, in order. Goals of care leads as the one call to action (a white card, green icon and an "Open" button);
 * every other section is one row of a single white grouped list: a muted icon square, the name in ink, an optional grey detail and a chevron.
 * Every row is full width at every text size. [selected] marks the section open beside it in the two-pane layout.
 */
@Composable internal fun DoctorPatientNavigationTiles(onSection:(String)->Unit,modifier:Modifier=Modifier,tagPrefix:String="doctor-shared",meta:Map<String,String> = emptyMap(),tabs:List<DoctorPatientTab> = doctorPatientTabs,selected:String?=null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tabs.firstOrNull()?.let { PatientHubPrimaryRow(it, onSection, tagPrefix, meta[it.section], selected == it.section) }
        val rest = tabs.drop(1)
        if (rest.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = androidx.compose.ui.graphics.Color.White, shadowElevation = 2.dp, border = BorderStroke(1.dp, HubBorder), modifier = Modifier.fillMaxWidth().saharaAppear()) {
            Column {
                rest.forEachIndexed { index, tab ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = SaharaColors.Line, thickness = 1.dp)
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


/** Goals of care: a white card with a green icon, the name, one line and a filled green "Open" pill. The whole card is the button. */
@Composable private fun PatientHubPrimaryRow(tab: DoctorPatientTab, onSection: (String) -> Unit, tagPrefix: String, meta: String?, selected: Boolean) { val t = careText()
    val press = remember { MutableInteractionSource() }
    Surface(onClick = { onSection(tab.section) }, interactionSource = press, shape = RoundedCornerShape(20.dp), color = androidx.compose.ui.graphics.Color.White, contentColor = SaharaColors.Ink, shadowElevation = 2.dp,
        border = if (selected) BorderStroke(2.dp, SaharaColors.Primary) else BorderStroke(1.dp, HubBorder),
        modifier = Modifier.fillMaxWidth().saharaAppear().saharaPress(press).heightIn(min = 72.dp).testTag("$tagPrefix-${tab.section}").semantics { role = Role.Button; semanticsSelected = selected }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(SaharaColors.Primary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(tab.icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t(tab.en, tab.hi), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = SaharaTitleFont, color = SaharaColors.Ink, style = DoctorWholeWords)
                Text(t("Start or continue the discussion", "चर्चा शुरू करें या जारी रखें"), fontSize = 14.sp, lineHeight = 20.sp, color = HubGrey, style = DoctorWholeWords)
                if (!meta.isNullOrBlank()) Text(meta, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = HubGrey, style = DoctorWholeWords, modifier = Modifier.testTag("$tagPrefix-${tab.section}-meta"))
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.heightIn(min = 40.dp).background(SaharaColors.Primary, RoundedCornerShape(50)).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(t("Open", "खोलें"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
            }
        }
    }
}

/** One section in the grouped list: muted tinted icon square, name in ink, optional grey detail, grey chevron. The open one (two-pane) sits on the green wash. */
@Composable internal fun DoctorSectionRow(tab: DoctorPatientTab, onSection: (String) -> Unit, tagPrefix: String, meta: String? = null, selected: Boolean = false) { val t = careText()
    val tone = doctorTabTone(tab.section)
    val press = remember { MutableInteractionSource() }
    Surface(onClick = { onSection(tab.section) }, interactionSource = press, color = if (selected) SaharaColors.PrimaryWash else androidx.compose.ui.graphics.Color.White, contentColor = SaharaColors.Ink,
        modifier = Modifier.fillMaxWidth().saharaPress(press).heightIn(min = 64.dp).testTag("$tagPrefix-${tab.section}").semantics { role = Role.Button; semanticsSelected = selected }) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(tone.wash, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(tab.icon, null, tint = tone.ink, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t(tab.en, tab.hi), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink, style = DoctorWholeWords)
                if (!meta.isNullOrBlank()) Text(meta, fontSize = 14.sp, lineHeight = 18.sp, color = HubGrey, style = DoctorWholeWords, modifier = Modifier.testTag("$tagPrefix-${tab.section}-meta"))
            }
            Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SaharaColors.LineStrong, modifier = Modifier.size(24.dp))
        }
    }
}
