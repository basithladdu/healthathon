package `in`.wedevit.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Display data for an actual patient–doctor relationship, not a note's typed reviewer name.
 * No production relationship source currently exists. Do not populate this from demo fixtures.
 * VERIFIED is intentionally unavailable until a trusted verification record can be bound.
 */
data class LinkedDoctorSummary(
    val id: String,
    val name: String,
    val speciality: String? = null,
    val clinic: String? = null,
    val verification: PatientDoctorVerification = PatientDoctorVerification.NOT_VERIFIED,
) {
    init { require(id.isNotBlank() && name.isNotBlank()) }
}

enum class PatientDoctorVerification { NOT_VERIFIED, PENDING }

// Call within the patient/account composition scope when an actual relationship source is added.
@Composable fun PatientDoctorRow(doctor: LinkedDoctorSummary?) {
    var detailsOpen by remember(doctor) { mutableStateOf(false) }
    if (doctor == null) {
        Text("No doctor linked", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("patient-doctor-empty"))
        return
    }
    // The overview deliberately contains only the doctor's name.
    Text(doctor.name, color = Rust, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "View doctor's details") { detailsOpen = true }
            .wrapContentHeight(Alignment.CenterVertically).testTag("patient-doctor-name"))
    if (detailsOpen) AlertDialog(
        modifier = Modifier.testTag("patient-doctor-details"),
        containerColor = Paper,
        onDismissRequest = { detailsOpen = false },
        title = { Text(doctor.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                doctor.speciality?.trim()?.takeIf(String::isNotEmpty)?.let {
                    Column { Text("Speciality", style = MaterialTheme.typography.labelMedium); Text(it) }
                }
                doctor.clinic?.trim()?.takeIf(String::isNotEmpty)?.let {
                    Column { Text("Clinic", style = MaterialTheme.typography.labelMedium); Text(it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (doctor.verification == PatientDoctorVerification.PENDING) Icons.Default.Schedule else Icons.Default.Info,
                        null, tint = Rust, modifier = Modifier.size(22.dp))
                    Column {
                        Text(if (doctor.verification == PatientDoctorVerification.PENDING) "Verification pending" else "Not verified",
                            fontWeight = FontWeight.Medium, modifier = Modifier.testTag("patient-doctor-status"))
                        Text("Sahara has not confirmed this doctor's professional identity.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { detailsOpen = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("patient-doctor-close")) { Text("Close") }
        },
    )
}

/** Shown to the patient or family when they invite a doctor, so the doctor can find them by ID after joining. */
@Composable fun PatientIdForDoctor(personId: String) {
    if (personId.isBlank()) return
    val hindi = doctorUiHindi()
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text((if (hindi) "मरीज़ ID: " else "Patient ID: ") + DoctorConnectedHome.patientCode(personId) +
            (if (hindi) " · जुड़ने के बाद डॉक्टर इस ID से आपको ढूँढ सकते हैं।" else " · after joining, your doctor can find you by this ID."),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("patient-id-for-doctor"))
    }
}
