package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun doctorHubSingleColumn(widthDp: Float, fontScale: Float): Boolean = widthDp < 340f || fontScale >= 1.3f

/** Tiles only: the caller owns the patient header, scroll surface, routes and Back behaviour. */
@Composable fun DoctorPatientHub(patient: DoctorDemoPatient, onSection: (String) -> Unit, modifier: Modifier = Modifier, meta: Map<String,String> = emptyMap()) {
    DoctorPatientNavigationTiles(onSection,modifier.testTag("doctor-patient-hub-${patient.id}"),"demo-record",340f,meta)
}

/** Doctor-only navigation presentation; contains no patient data beyond the caller's supplied counts. */
@Composable internal fun DoctorPatientNavigationTiles(onSection:(String)->Unit,modifier:Modifier=Modifier,tagPrefix:String="doctor-shared",minimumPairedWidth:Float=300f,meta:Map<String,String> = emptyMap()) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val single = maxWidth.value < minimumPairedWidth || LocalDensity.current.fontScale >= 1.3f
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PatientHubTile("Overview", "Care story and next steps", "Summary", Icons.Outlined.GridView, DoctorCardOverview, wide = true, onSection = onSection,tagPrefix=tagPrefix,meta=meta["Summary"])
            if (single) {
                PatientHubTile("Progress", "Patient and family reports", "Progress", Icons.Outlined.Insights, DoctorCardProgress, wide = true, onSection = onSection,tagPrefix=tagPrefix,meta=meta["Progress"])
                PatientHubTile("Medicines", "Medicine list and intake history", "Medicines", Icons.Outlined.Medication, DoctorCardMedicine, wide = true, onSection = onSection,tagPrefix=tagPrefix,meta=meta["Medicines"])
            } else Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PatientHubTile("Progress", "Reported updates", "Progress", Icons.Outlined.Insights, DoctorCardProgress, modifier = Modifier.weight(1f).fillMaxHeight(), onSection = onSection,tagPrefix=tagPrefix,meta=meta["Progress"])
                PatientHubTile("Medicines", "List and intake history", "Medicines", Icons.Outlined.Medication, DoctorCardMedicine, modifier = Modifier.weight(1f).fillMaxHeight(), onSection = onSection,tagPrefix=tagPrefix,meta=meta["Medicines"])
            }
            PatientHubTile("Conversations", "Discussions and Care Notes", "Conversations", Icons.Outlined.Forum, DoctorCardConversation, wide = true, onSection = onSection,tagPrefix=tagPrefix,meta=meta["Conversations"])
            if (single) {
                PatientHubTile("Visits", "Appointments and follow-up", "Visits", Icons.Outlined.CalendarMonth, DoctorCardVisit, wide = true, onSection = onSection,tagPrefix=tagPrefix,meta=meta["Visits"])
                PatientHubTile("Reports", "Documents", "Reports", Icons.Outlined.FolderOpen, DoctorCardReport, wide = true, onSection = onSection,tagPrefix=tagPrefix,meta=meta["Reports"])
            } else Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PatientHubTile("Visits", "Appointments", "Visits", Icons.Outlined.CalendarMonth, DoctorCardVisit, modifier = Modifier.weight(1f).fillMaxHeight(), onSection = onSection,tagPrefix=tagPrefix,meta=meta["Visits"])
                PatientHubTile("Reports", "Documents", "Reports", Icons.Outlined.FolderOpen, DoctorCardReport, modifier = Modifier.weight(1f).fillMaxHeight(), onSection = onSection,tagPrefix=tagPrefix,meta=meta["Reports"])
            }
        }
    }
}

@Composable private fun PatientHubTile(title: String, description: String, section: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, wide: Boolean = false, onSection: (String) -> Unit,tagPrefix:String="demo-record",meta:String?=null) {
    Surface(onClick = { onSection(section) }, modifier = modifier.fillMaxWidth().heightIn(min = if (wide) 96.dp else 124.dp).testTag("$tagPrefix-$section").semantics { role = Role.Button }, shape = RoundedCornerShape(22.dp), color = color, contentColor = DoctorCardInk, border = if(color==DoctorCardReport)BorderStroke(1.dp,DoctorHairline)else null) {
        if (wide) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Paper.copy(alpha = .9f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = DoctorAccent, modifier = Modifier.size(23.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk)
                Text(description, fontSize=13.sp, color = DoctorCardSub, lineHeight = 18.sp)
                if(!meta.isNullOrBlank()) Text(meta, fontSize=12.sp, color = DoctorAccent, lineHeight = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("$tagPrefix-$section-meta"))
            }
            Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = DoctorAccent, modifier = Modifier.size(20.dp))
        } else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = DoctorAccent, modifier = Modifier.size(20.dp)); Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = DoctorAccent, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk)
            Text(description, fontSize=13.sp, color = DoctorCardSub, lineHeight = 18.sp)
            if(!meta.isNullOrBlank()) Text(meta, fontSize=12.sp, color = DoctorAccent, lineHeight = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("$tagPrefix-$section-meta"))
        }
    }
}
