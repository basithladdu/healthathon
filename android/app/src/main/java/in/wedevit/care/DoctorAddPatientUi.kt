package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a palliative doctor records when adding a patient (Dr Sujay, 22 Nov: a name alone was not enough).
 * Only the name is required. Age, sex, phone and main diagnosis go into the patient profile; the main caregiver
 * becomes a care-team contact; how they were referred has no field of its own, so it is kept as a dated note.
 */
internal data class DoctorNewPatient(
    val name: String, val age: Int? = null, val sex: String = "", val phone: String = "", val diagnosis: String = "",
    val caregiverName: String = "", val caregiverRelationship: String = "", val caregiverPhone: String = "", val referral: String = "",
) {
    val profile: CarePatientProfile? get() =
        if (age == null && sex.isBlank() && phone.isBlank() && diagnosis.isBlank()) null
        else CarePatientProfile(age = age, sex = sex, illness = diagnosis, phone = phone)

    fun caregiver(personId: String): CareContact? = caregiverName.takeIf { it.isNotBlank() }?.let {
        CareContact(newId(), personId, it, caregiverRelationship.ifBlank { "Main caregiver" }, "caregiver", caregiverPhone)
    }

    /** Saved in English, like every record. */
    val referralNote: String? get() = referral.takeIf { it.isNotBlank() }?.let { "How they were referred: $it" }

    companion object {
        const val REFERRAL_TITLE = "Referral"
        val REFERRALS = listOf("Hospital or oncologist", "Another doctor", "Family or self", "Community or NGO", "Other")
        private val PHONE = Regex("[+0-9 ()-]{7,40}")
        fun phoneOk(text: String) = text.isBlank() || text.trim().matches(PHONE)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DoctorAddPatientContent(busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (DoctorNewPatient) -> Unit) { val t = careText()
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var diagnosis by rememberSaveable { mutableStateOf("") }
    var caregiverName by rememberSaveable { mutableStateOf("") }
    var caregiverRelationship by rememberSaveable { mutableStateOf("") }
    var caregiverPhone by rememberSaveable { mutableStateOf("") }
    var referral by rememberSaveable { mutableStateOf("") }
    var referralOther by rememberSaveable { mutableStateOf("") }
    val ageCheck = CarePatientProfileRules.age(age)
    val phoneBad = !DoctorNewPatient.phoneOk(phone) || !DoctorNewPatient.phoneOk(caregiverPhone)
    val canSave = name.isNotBlank() && ageCheck.second == null && !phoneBad && !busy
    fun referralLabel(v: String) = when (v) {
        "Hospital or oncologist" -> t("Hospital or oncologist", "अस्पताल या कैंसर डॉक्टर")
        "Another doctor" -> t("Another doctor", "दूसरे डॉक्टर")
        "Family or self" -> t("Family or self", "परिवार या ख़ुद")
        "Community or NGO" -> t("Community or NGO", "समुदाय या संस्था")
        else -> t("Other", "अन्य")
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 16.dp).testTag("doctor-add-patient"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading(t("Add a patient", "मरीज़ जोड़ें"), 27)
        Text(t("Only the name is needed now. Add the rest if you have it; you can change it later.", "अभी सिर्फ़ नाम ज़रूरी है। बाकी हो तो जोड़ें; बाद में बदल सकते हैं।"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub)
        CareField(name, { name = it.take(100) }, t("Name *", "नाम *"), singleLine = true)
        CareField(age, { age = it.filter(Char::isDigit).take(3) }, t("Age (years)", "उम्र (साल)"), singleLine = true, supporting = ageCheck.second?.let { t(it.en, it.hi) })
        Text(t("Sex", "लिंग"), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CarePatientProfile.SEX.forEach { v -> FilterChip(selected = sex == v, onClick = { sex = if (sex == v) "" else v }, label = { Text(when (v) { "Female" -> t("Female", "महिला"); "Male" -> t("Male", "पुरुष"); else -> t("Other", "अन्य") }) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        CareField(phone, { phone = it.take(40) }, t("Patient's phone", "मरीज़ का फ़ोन"), singleLine = true, supporting = if (!DoctorNewPatient.phoneOk(phone)) t("Check the phone number.", "फ़ोन नंबर जाँचें।") else null)
        CareField(diagnosis, { diagnosis = it.take(300) }, t("Main diagnosis", "मुख्य बीमारी"), singleLine = true)
        Text(t("Main caregiver (optional)", "मुख्य देखभाल करने वाले (चाहें तो)"), Modifier.a11yHeading().padding(top = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        CareField(caregiverName, { caregiverName = it.take(120) }, t("Caregiver's name", "देखभाल करने वाले का नाम"), singleLine = true)
        CareField(caregiverRelationship, { caregiverRelationship = it.take(80) }, t("Relationship, for example daughter", "रिश्ता, जैसे बेटी"), singleLine = true)
        CareField(caregiverPhone, { caregiverPhone = it.take(40) }, t("Caregiver's phone", "देखभाल करने वाले का फ़ोन"), singleLine = true, supporting = if (!DoctorNewPatient.phoneOk(caregiverPhone)) t("Check the phone number.", "फ़ोन नंबर जाँचें।") else null)
        Text(t("How were they referred?", "उन्हें किसने भेजा?"), Modifier.padding(top = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DoctorNewPatient.REFERRALS.forEach { v -> FilterChip(selected = referral == v, onClick = { referral = if (referral == v) "" else v }, label = { Text(referralLabel(v)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-add-patient-referral-${DoctorNewPatient.REFERRALS.indexOf(v)}")) }
        }
        if (referral == "Other") CareField(referralOther, { referralOther = it.take(200) }, t("Referred by", "किसने भेजा"), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        Button(onClick = {
            val referred = if (referral == "Other") referralOther.trim().ifBlank { "Other" } else referral
            onSave(DoctorNewPatient(name.trim(), ageCheck.first, sex, phone.trim(), diagnosis.trim(), caregiverName.trim(), caregiverRelationship.trim(), caregiverPhone.trim(), referred))
        }, enabled = canSave, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("doctor-add-patient-save")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Add patient", "मरीज़ जोड़ें")) }
        TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back", "वापस")) }
    }
}
