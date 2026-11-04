package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One bundled CDCI name (NRCeS, CC BY 4.0). Names only: the list holds no strength, form or brand. */
data class MedicineName(val name: String, val snomed: String = "")

/** Offline spelling aid for typing a medicine name. It never proposes a medicine, a strength or a dose. */
object MedicineNames {
    const val ASSET = "medicine-names/cdci-names.tsv"
    const val ATTRIBUTION = "Medicine names: Common Drug Codes for India (CDCI), NRCeS / C-DAC Pune, CC BY 4.0, via OHC Network. Names only; Sahara removed strengths, forms and brands. SNOMED CT is a trademark of SNOMED International."
    @Volatile private var cached: List<MedicineName>? = null

    fun parse(lines: Sequence<String>): List<MedicineName> = lines.mapNotNull { line ->
        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
        val cols = line.split('\t')
        val name = cols[0].trim()
        if (name.length !in 2..120) null else MedicineName(name, cols.getOrNull(1)?.trim()?.takeIf { it.all(Char::isDigit) }.orEmpty())
    }.distinctBy { it.name.lowercase() }.toList()

    fun all(context: Context): List<MedicineName> = cached ?: synchronized(this) {
        cached ?: context.applicationContext.assets.open(ASSET).bufferedReader().useLines { parse(it) }.also { cached = it }
    }

    /** Word-start matches first (shorter names first), then matches inside a word. Under two letters, or once the
     *  typed text is exactly a listed name, nothing is offered. */
    fun search(names: List<MedicineName>, query: String, limit: Int = 6): List<MedicineName> {
        val q = query.trim().lowercase()
        if (q.length < 2 || names.any { it.name.lowercase() == q }) return emptyList()
        val starts = mutableListOf<MedicineName>(); val inside = mutableListOf<MedicineName>()
        names.forEach { entry ->
            val n = entry.name.lowercase()
            when {
                n.startsWith(q) || n.split(' ', '-', '(').any { it.startsWith(q) } -> starts += entry
                n.contains(q) -> inside += entry
            }
        }
        return (starts.sortedWith(compareBy<MedicineName> { !it.name.lowercase().startsWith(q) }.thenBy { it.name.length }.thenBy { it.name.lowercase() }) +
            inside.sortedBy { it.name.length }).take(limit)
    }
}

/** Offline CDCI name suggestions shown under a medicine-name field. Tapping one only fills the name. */
@Composable internal fun MedicineNameSuggestions(query: String, enabled: Boolean = true, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var names by remember { mutableStateOf<List<MedicineName>?>(null) }
    LaunchedEffect(Unit) { names = withContext(Dispatchers.IO) { runCatching { MedicineNames.all(context) }.getOrDefault(emptyList()) } }
    val matches = remember(names, query) { MedicineNames.search(names.orEmpty(), query) }
    if (matches.isEmpty() || !enabled) return
    Column(Modifier.fillMaxWidth().testTag("medicine-name-suggestions"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Solid secondary ink, not faded Ink (pale on Xiaomi phones).
        Text(careBilingual("Spelling from the India drug list", "भारत की दवा सूची से नाम"), fontSize = 14.sp, color = DoctorCardSub)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            matches.forEach { match -> SuggestionChip(onClick = { onPick(match.name) }, label = { Text(match.name, fontSize = 15.sp, color = DoctorCardInk) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp)) }
        }
        Text(careBilingual("Names only. Copy the dose from the prescription", "खुराक पर्चे से ही लिखें"), fontSize = 14.sp, color = DoctorCardSub)
    }
}
