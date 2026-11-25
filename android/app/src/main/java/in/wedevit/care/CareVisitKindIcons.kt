package `in`.wedevit.care

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

/** What kind of visit it is, read only from words already typed for it (24 Nov clinical meeting: a family meeting is not a map pin). */
enum class CareVisitKind { FAMILY_MEETING, VIDEO, PHONE, HOME, CLINIC, OTHER }

object CareVisitKindRules {
    // Checked in this order: "family meeting at home" is a family meeting, "video call" is video before "call" is phone.
    private val cues: List<Pair<CareVisitKind, List<String>>> = listOf(
        CareVisitKind.FAMILY_MEETING to listOf("family meeting", "family conference", "family discussion", "family meet", "पारिवारिक बैठक", "परिवार की बैठक", "परिवार बैठक"),
        CareVisitKind.VIDEO to listOf("video", "video call", "online", "tele consult", "teleconsult", "teleconsultation", "telemedicine", "वीडियो", "ऑनलाइन"),
        CareVisitKind.PHONE to listOf("phone", "phone call", "call", "telephone", "फ़ोन", "फोन", "कॉल"),
        CareVisitKind.HOME to listOf("home visit", "home", "home care", "at home", "घर", "होम विज़िट", "घर पर"),
        CareVisitKind.CLINIC to listOf("clinic", "hospital", "opd", "outpatient", "oncology", "oncology review", "review", "ward", "क्लिनिक", "अस्पताल", "ओपीडी"),
    )

    /** Words that only name a visit's type. A place made only of these is a type, not an address, so it gets no map pin. */
    private val typeOnlyWords = setOf(
        "family", "meeting", "conference", "discussion", "video", "call", "online", "tele", "teleconsult", "teleconsultation", "telemedicine",
        "phone", "telephone", "home", "visit", "care", "clinic", "review", "oncology", "opd", "outpatient", "follow", "up", "followup", "at", "palliative",
        "पारिवारिक", "परिवार", "की", "बैठक", "वीडियो", "ऑनलाइन", "फ़ोन", "फोन", "कॉल", "घर", "पर", "होम", "विज़िट", "मुलाक़ात", "क्लिनिक",
    )

    private fun spaced(text: String) = " " + text.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim() + " "

    /** The first kind whose words appear in [texts] as whole words; OTHER when none do. */
    fun kind(vararg texts: String): CareVisitKind {
        val text = spaced(texts.joinToString(" "))
        return cues.firstOrNull { (_, words) -> words.any { text.contains(" ${spaced(it).trim()} ") } }?.first ?: CareVisitKind.OTHER
    }

    /** True when [place] only names the visit type ("Family meeting", "Home visit", "Video call"), so it is not a real address. */
    fun isTypeOnly(place: String): Boolean {
        val words = spaced(place).trim().split(' ').filter(String::isNotBlank)
        return words.isNotEmpty() && words.all { it in typeOnlyWords }
    }

    /** True when [place] is a real address line that may carry a location pin. */
    fun isAddress(place: String): Boolean = place.isNotBlank() && !isTypeOnly(place)
}

/** The icon for a visit type: Home, hospital, Groups for a family meeting, Videocam or Call, otherwise a plain calendar event. */
fun CareVisitKind.icon(): ImageVector = when (this) {
    CareVisitKind.HOME -> Icons.Default.Home
    CareVisitKind.CLINIC -> Icons.Default.LocalHospital
    CareVisitKind.FAMILY_MEETING -> Icons.Default.Groups
    CareVisitKind.VIDEO -> Icons.Default.Videocam
    CareVisitKind.PHONE -> Icons.Default.Call
    CareVisitKind.OTHER -> Icons.Default.Event
}

/** The icon for a place line: a pin only for a real address, otherwise the visit type's own icon. */
fun careVisitPlaceIcon(place: String, vararg context: String): ImageVector =
    if (CareVisitKindRules.isAddress(place)) Icons.Default.Place else CareVisitKindRules.kind(place, *context).icon()
