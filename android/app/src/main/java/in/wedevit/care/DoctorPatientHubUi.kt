package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun doctorHubSingleColumn(widthDp: Float, fontScale: Float): Boolean = widthDp < 340f || fontScale >= 1.3f

/** Tiles only: the caller owns the patient header, scroll surface, routes and Back behaviour. The earlier practice record keeps its six sections. */
@Composable fun DoctorPatientHub(patient: DoctorDemoPatient, onSection: (String) -> Unit, modifier: Modifier = Modifier, meta: Map<String,String> = emptyMap()) {
    DoctorPatientNavigationTiles(onSection,modifier.testTag("doctor-patient-hub-${patient.id}"),"demo-record",340f,meta,tabs=doctorLegacyHubTabs)
}

/**
 * Doctor-only navigation: one wide tile for the first tab (Symptoms & distress), the rest in pairs; a lone last tile is wide.
 * One column when narrow or at large text. [selected] outlines the tab open beside it in the two-pane layout.
 */
@Composable internal fun DoctorPatientNavigationTiles(onSection:(String)->Unit,modifier:Modifier=Modifier,tagPrefix:String="doctor-shared",minimumPairedWidth:Float=300f,meta:Map<String,String> = emptyMap(),tabs:List<DoctorPatientTab> = doctorPatientTabs,selected:String?=null) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val single = maxWidth.value < minimumPairedWidth || LocalDensity.current.fontScale >= 1.3f
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tabs.firstOrNull()?.let { PatientHubTile(it, wide = true, onSection = onSection, tagPrefix = tagPrefix, meta = meta[it.section], selected = selected == it.section) }
            val rest = tabs.drop(1)
            if (single) rest.forEach { PatientHubTile(it, wide = true, onSection = onSection, tagPrefix = tagPrefix, meta = meta[it.section], selected = selected == it.section) }
            else rest.chunked(2).forEach { pair ->
                if (pair.size == 1) PatientHubTile(pair[0], wide = true, onSection = onSection, tagPrefix = tagPrefix, meta = meta[pair[0].section], selected = selected == pair[0].section)
                else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { PatientHubTile(it, modifier = Modifier.weight(1f).fillMaxHeight(), onSection = onSection, tagPrefix = tagPrefix, meta = meta[it.section], selected = selected == it.section) }
                }
            }
        }
    }
}

/** Each patient tab keeps its own colour on the hub tile and the tab row. */
internal fun doctorTabTone(section: String): DoctorTone = when (section) {
    "GoalsOfCare", "Conversations" -> DoctorTones.Rust
    "CarePlan", "Reports" -> DoctorTones.Marigold
    "Progress", "Summary" -> DoctorTones.Teal
    "Labs" -> DoctorTones.Leaf
    "Visits" -> DoctorTones.Indigo
    "Medicines" -> DoctorTones.Plum
    else -> DoctorTones.Indigo
}

@Composable private fun PatientHubTile(tab: DoctorPatientTab, modifier: Modifier = Modifier, wide: Boolean = false, onSection: (String) -> Unit, tagPrefix: String = "demo-record", meta: String? = null, selected: Boolean = false) { val t = careText()
    val title = t(tab.en, tab.hi)
    val tone = doctorTabTone(tab.section)
    val press = remember { MutableInteractionSource() }
    Surface(onClick = { onSection(tab.section) }, interactionSource = press, modifier = modifier.fillMaxWidth().saharaAppear().saharaPress(press).heightIn(min = if (wide) 64.dp else 84.dp).testTag("$tagPrefix-${tab.section}").semantics { role = Role.Button }, shape = RoundedCornerShape(16.dp), color = if (selected) tone.wash else Paper, contentColor = DoctorCardInk,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) tone.solid else DoctorHairline), shadowElevation = if (selected) 0.dp else 1.dp) {
        if (wide) Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DoctorIconChip(tab.icon, tone, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = SaharaTitleFont, color = DoctorCardInk, style = DoctorWholeWords)
                if(!meta.isNullOrBlank()) Text(meta, fontSize=14.sp, color = tone.ink, lineHeight = 18.sp, fontWeight = FontWeight.Medium, style = DoctorWholeWords, modifier = Modifier.testTag("$tagPrefix-${tab.section}-meta"))
            }
            Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tone.ink, modifier = Modifier.size(20.dp))
        } else Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DoctorIconChip(tab.icon, tone, 40.dp)
            Text(title, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = SaharaTitleFont, color = DoctorCardInk, style = DoctorWholeWords)
            if(!meta.isNullOrBlank()) Text(meta, fontSize=14.sp, color = tone.ink, lineHeight = 18.sp, fontWeight = FontWeight.Medium, style = DoctorWholeWords, modifier = Modifier.testTag("$tagPrefix-${tab.section}-meta"))
        }
    }
}
