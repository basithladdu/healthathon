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
import androidx.compose.material3.MaterialTheme
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
@Composable fun DoctorPatientHub(patient: DoctorDemoPatient, onSection: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("doctor-patient-hub-${patient.id}")) {
        val single = doctorHubSingleColumn(maxWidth.value, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PatientHubTile("Overview", "Care story and next steps", "Summary", Icons.Outlined.GridView, Peach.copy(alpha = .7f), wide = true, onSection = onSection)
            if (single) {
                PatientHubTile("Progress", "Patient and family reports", "Progress", Icons.Outlined.Insights, SaharaSoftBlue, wide = true, onSection = onSection)
                PatientHubTile("Medicines", "Reported dose history", "Medicines", Icons.Outlined.Medication, lerpHubGold(), wide = true, onSection = onSection)
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PatientHubTile("Progress", "Reported updates", "Progress", Icons.Outlined.Insights, SaharaSoftBlue, modifier = Modifier.weight(1f), onSection = onSection)
                PatientHubTile("Medicines", "Reported doses", "Medicines", Icons.Outlined.Medication, lerpHubGold(), modifier = Modifier.weight(1f), onSection = onSection)
            }
            PatientHubTile("Conversations", "Discussions and Care Notes", "Conversations", Icons.Outlined.Forum, SaharaLavender, wide = true, onSection = onSection)
            if (single) {
                PatientHubTile("Visits", "Appointments and follow-up", "Visits", Icons.Outlined.CalendarMonth, Peach.copy(alpha = .45f), wide = true, onSection = onSection)
                PatientHubTile("Reports", "Document summaries", "Reports", Icons.Outlined.FolderOpen, Cream, wide = true, onSection = onSection)
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PatientHubTile("Visits", "Appointments", "Visits", Icons.Outlined.CalendarMonth, Peach.copy(alpha = .45f), modifier = Modifier.weight(1f), onSection = onSection)
                PatientHubTile("Reports", "Documents", "Reports", Icons.Outlined.FolderOpen, Cream, modifier = Modifier.weight(1f), onSection = onSection)
            }
        }
    }
}

private fun lerpHubGold() = SaharaSoftGold

@Composable private fun PatientHubTile(title: String, description: String, section: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, wide: Boolean = false, onSection: (String) -> Unit) {
    Surface(onClick = { onSection(section) }, modifier = modifier.fillMaxWidth().heightIn(min = if (wide) 104.dp else 120.dp).testTag("demo-record-$section").semantics { role = Role.Button }, shape = RoundedCornerShape(if (wide) 24.dp else 20.dp), color = color, contentColor = Ink, border = BorderStroke(1.dp, Rust.copy(alpha = .12f))) {
        if (wide) Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Paper.copy(alpha = .75f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Rust, modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .75f), lineHeight = 18.sp)
            }
            Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Rust, modifier = Modifier.size(20.dp))
        } else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Rust, modifier = Modifier.size(25.dp)); Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Rust.copy(alpha = .7f), modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .75f), lineHeight = 17.sp)
        }
    }
}
