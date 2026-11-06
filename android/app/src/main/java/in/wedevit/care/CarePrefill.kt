package `in`.wedevit.care

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

/** The signed-in doctor's own details. Used only to prefill forms; every prefilled field can still be changed. */
data class SignedInDoctor(val name: String, val registration: String = "")

internal object SignedInDoctorRules {
    /**
     * Practice workspace: the Practice doctor. Signed in: the name and registration number the Sahara team approved
     * (healthathon_clinician_requests, status "approved"). A pending, rejected or suspended request is never used.
     */
    fun from(practice: Boolean, profile: CareClinicianProfile?): SignedInDoctor? = when {
        practice -> SignedInDoctor(DoctorDemoData.doctorName, DoctorDemoData.registrationNo)
        profile != null && profile.status == "approved" && profile.name.isNotBlank() -> SignedInDoctor(profile.name.trim(), profile.registration.trim())
        else -> null
    }
}

/** Provided by the care plan screen; null when the doctor is not known (patient or family, offline, or not yet approved). */
internal val LocalSignedInDoctor = compositionLocalOf<SignedInDoctor?> { null }

@Composable internal fun rememberSignedInDoctor(vm: CareModel, clinician: Boolean): SignedInDoctor? {
    val cloud = vm.cloud
    var profile by remember(cloud) { mutableStateOf<CareClinicianProfile?>(null) }
    LaunchedEffect(cloud, clinician) {
        if (clinician && cloud != null) profile = try { cloud.clinicianProfile() } catch (e: Exception) { if (e is CancellationException) throw e; null }
    }
    return SignedInDoctorRules.from(clinician && vm.demoMode && vm.walkthroughRole == null, profile)
}

/**
 * A value the app already knows ([known]), shown filled with a small Change. The text field ([field]) appears only
 * when the value is not known, differs from it, or the person taps Change.
 */
@Composable internal fun PrefilledField(value: String, known: String, label: String, tag: String, field: @Composable () -> Unit) { val t = careText()
    var changing by rememberSaveable(tag) { mutableStateOf(false) }
    if (changing || known.isBlank() || value.trim() != known.trim()) field()
    else Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("$tag-known"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = DoctorCardSub)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        }
        TextButton(onClick = { changing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-change")) { Text(t("Change", "बदलें"), color = RustText) }
    }
}

/** A new plan starts with what the patient profile already holds; blanks stay blank. */
internal object EctprPrefill {
    fun patient(name: String, profile: CarePatientProfile?): EctprPatient =
        EctprPatient(name = name, sex = profile?.sex.orEmpty(), address = profile?.address.orEmpty(), phone = profile?.phone.orEmpty())
    fun clinical(profile: CarePatientProfile?): ClinicalDetails =
        ClinicalDetails(diagnosis = profile?.illness.orEmpty(), allergies = profile?.allergies.orEmpty())
}
