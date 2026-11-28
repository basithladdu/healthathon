@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

// The Care tab tools hub (25 Nov redesign, owner override: a stock Material page, no tinted boxes, no coloured chips).
/**
 * The Care tab: a plain Material page with a large title. One visual anchor (the last 7 days of check-ins as round days), then every
 * care tool as a grid of round buttons. A tool that is a module (Check-ins, Journal, To do, Visits, Goals of care notes) opens on its
 * own page with a back arrow; the others open their pushed page. "Add" is a floating button that opens one sheet of big rows.
 * A tool whose page is not passed in (the doctor's embedded view) is left out. [initialFilter] opens one module directly; its back
 * goes to [onExit]. [title] overrides the page title.
 */
@Composable fun CareList(vm: CareModel, onAdd: (String) -> Unit, onScanPrescription: () -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, initialFilter: String = "All", onReadNote: (CareVersion) -> Unit = {}, onSymptoms: () -> Unit = {}, onCalendarAt: (Long) -> Unit = { onCalendar() },
    onCommunity: (() -> Unit)? = null, onDevices: (() -> Unit)? = null, onExit: (() -> Unit)? = null, onDocuments: (() -> Unit)? = null, onCarePlan: (() -> Unit)? = null, title: String? = null) {
    val entries by vm.items.collectAsStateWithLifecycle(); val everything by vm.allItems.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(); val target = CareModules.moduleFor(initialFilter)
    val shell = LocalSaharaShell.current
    // Grouped once per change of the entries, not on every recomposition (opening a module, scrolling, a count animating).
    val shown = remember(entries) { entries.filter { !CareEntryCodec.isStructured(it.detail) || isDoctorStructuredVisit(it) } }
    val (currentMeds, pastMeds) = remember(everything, shown) { CareModules.splitMedicines(everything.filter { !CareEntryCodec.isStructured(it.detail) }, shown) }
    val now = remember(entries) { System.currentTimeMillis() }; val (upcoming, pastVisits) = remember(shown, now) { CareModules.splitVisits(shown.filter { it.kind == "visit" }, now) }
    val tasks = remember(shown) { shown.filter { it.kind == "task" } }; val checkins = remember(shown) { shown.filter { it.kind == "checkin" } }
    val journal = remember(shown) { shown.filter { it.kind == "journal" && !SideEffectCodec.isStructured(it.detail) && !PatientProfileCodec.isStructured(it.detail) && !ResearchConsentCodec.isStructured(it.detail) } }
    val selectedPerson by vm.selected.collectAsStateWithLifecycle(); val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle()
    val canAdd by vm.canEdit.collectAsStateWithLifecycle()
    // A tool opens its module on its own page; Back returns to the hub. [initialFilter] can open one directly.
    var openModule by rememberSaveable(target) { mutableStateOf(target) }
    // Opening from Today ("See all" To do) always lands on that module, even if it was closed here before.
    LaunchedEffect(target) { if (target != null) openModule = target }
    // A module opened straight from another page (Today) goes back to that page, not to the hub.
    fun closeModule() { if (target != null && openModule == target && onExit != null) onExit() else openModule = null }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    val t = careText()
    // [name] stays English: it is the module's key and test tag; only [title] is shown. Each module's add action is its page's FAB.
    val tiles = listOf(
        CareModuleTile("Conversations", t("Goals of care notes", "देखभाल के लक्ष्यों के नोट"), "", SaharaLavender, R.drawable.conversation_recent_art, versions.size) {
            CareModuleList("conversations", versions, t("No saved conversations yet.", "अभी कोई सहेजी हुई बातचीत नहीं।"), keyOf = { it.id }) { CareConversationRow(it, onReadNote) }
        },
        CareModuleTile("Medicines", t("Medicines", "दवाइयाँ"), "", SaharaSoftBlue, R.drawable.care_medicines_art, currentMeds.size + pastMeds.size) {
                if (currentMeds.isEmpty() && pastMeds.isEmpty()) QuietLine(t("No medicines yet.", "अभी कोई दवा नहीं।")) else {
                    MedicineModuleList(vm, currentMeds, pastMeds)
                }
                MedicineScanAction(onScanPrescription) { onAdd("medicine") }
                SideEffectsToRaiseCard(vm)
        },
        CareModuleTile("Visits", t("Visits", "मुलाक़ातें"), "", SaharaApricot, R.drawable.conversation_history_art, upcoming.size + pastVisits.size) {
                // How visits get here (22 Nov review): now only the empty page says it, in one line.
                if (upcoming.isEmpty() && pastVisits.isEmpty()) Text(t("Visits your doctor schedules appear here.", "डॉक्टर जो मुलाक़ात तय करते हैं, वह यहाँ दिखती है।"),
                    fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("care-visits-how"))
                CareVisitsModule(vm, upcoming, pastVisits, onCalendarAt)
        },
        CareModuleTile("To do", t("To do", "करने के काम"), "", SaharaSoftGold, R.drawable.care_notes_illustration, tasks.size) {
            CareTodoModule(vm, tasks)
        },
        CareModuleTile("Check-ins", t("Check-ins", "रोज़ का हाल"), "", Mint, R.drawable.wellbeing_checkin_illustration, checkins.size) {
            // One short row per day (22 Nov review); a day opens to show its entries.
            CheckInHistoryModule(checkins, selectedPerson, vm)
        },
        CareModuleTile("Journal", t("Journal", "डायरी"), "", Peach, R.drawable.conversation_record_art, journal.size) {
            CareModuleList("journal", journal, t("No journal notes yet.", "डायरी में अभी कोई नोट नहीं।"), keyOf = { it.id }) { CareRow(it, vm) }
        },
    )
    val opened = tiles.firstOrNull { it.name == openModule }.takeIf { loadedFor == selectedPerson }
    val byName = tiles.associateBy { it.name }
    // The module pages' one main action, as the page's floating button (it replaced the full-width "+ Add" buttons).
    val moduleAdd: Map<String, Triple<String, String, () -> Unit>> = mapOf(
        "Visits" to Triple(t("Add a visit", "मुलाक़ात जोड़ें"), "care-module-add-visit", { onAdd("visit") }),
        "To do" to Triple(t("Add a to-do", "नया काम जोड़ें"), "care-module-add-task", { onAdd("task") }),
        "Journal" to Triple(t("Add a journal entry", "डायरी में लिखें"), "care-module-add-journal", { onAdd("journal") }),
    )
    fun module(name: String, icon: ImageVector = careModuleIcon(name)): QuickActionSpec? = byName[name]?.let { tile ->
        QuickActionSpec(tile.title, icon, tag = "care-module-${tile.key}-toggle") { openModule = name } }
    fun page(label: String, icon: ImageVector, tag: String, open: (() -> Unit)?): QuickActionSpec? = open?.let { QuickActionSpec(label, icon, tag = tag, onClick = it) }
    // Care near me opens the Nearby page through the app shell (CareList has no parameter for it); not in the doctor's embedded view.
    val nearby: (() -> Unit)? = shell?.takeIf { onDocuments != null }?.let { s -> { s.open(3) } }
    val tools = listOfNotNull(
        page(t("Symptoms", "लक्षण"), Icons.Default.MonitorHeart, "symptom-tracker-entry", onSymptoms),
        module("Check-ins", Icons.Default.Mood),
        module("Journal", Icons.Default.EditNote),
        module("To do", Icons.Default.Checklist),
        module("Visits", Icons.Default.Event),
        // The Medicines tab holds the medicines inside the app; the doctor's embedded view (no app shell) keeps them here.
        if (shell == null) module("Medicines") else null,
        page(t("Calendar", "कैलेंडर"), Icons.Default.CalendarMonth, "shortcut-calendar", onCalendar),
        page(t("Documents", "कागज़ात"), Icons.Default.FolderOpen, "care-tool-documents", onDocuments),
        page(t("Lab history", "जाँच रिपोर्टें"), Icons.Default.Science, "shortcut-labs", onLabs),
        module("Conversations", Icons.Default.Forum),
        page(t("Emergency plan", "आपातकालीन योजना"), Icons.Default.QrCode, "care-tool-plan", onCarePlan),
        page(t("Tubes and lines", "नलियाँ और लाइनें"), Icons.Default.Healing, "shortcut-devices", onDevices),
        page(t("Care near me", "पास में देखभाल"), Icons.Default.Place, "shortcut-nearby", nearby),
        page(t("Community", "समुदाय"), Icons.Default.Groups, "shortcut-community", onCommunity),
        page(t("Requests to doctor", "डॉक्टर से अनुरोध"), Icons.Default.Inbox, "shortcut-workflows", onWorkflow),
    )
    // The one visual anchor: the last 7 days, from every saved entry (symptom notes are structured check-ins, so not from [shown]).
    val zone = remember { java.time.ZoneId.systemDefault() }
    val today = remember(entries) { LocalDate.now() }
    val week = remember(entries, selectedPerson, today) { weekMarks(entries, selectedPerson, today, zone) }
    val checkedDays = week.count { it.mood != null }; val symptomDays = week.count { it.symptom }
    val weekLine = listOfNotNull(
        when (checkedDays) { 0 -> t("No check-ins this week", "इस हफ़्ते कोई हाल नहीं लिखा"); 1 -> t("1 check-in this week", "इस हफ़्ते 1 बार हाल लिखा"); else -> t("$checkedDays check-ins this week", "इस हफ़्ते $checkedDays बार हाल लिखा") },
        when (symptomDays) { 0 -> null; 1 -> t("symptoms on 1 day", "1 दिन लक्षण लिखे"); else -> t("symptoms on $symptomDays days", "$symptomDays दिन लक्षण लिखे") },
    ).joinToString(" · ")
    if (addOpen) SaharaActionSheet(t("Add", "जोड़ें"), listOfNotNull(
        SheetAction(t("Note a symptom", "तबीयत का नोट लिखें"), Icons.Default.MonitorHeart, "care-add-symptom") { onSymptoms() },
        SheetAction(t("Add check-in", "आज का हाल लिखें"), careIcon("checkin"), "care-add-checkin") { onAdd("checkin") },
        SheetAction(t("Add note", "नोट जोड़ें"), careIcon("journal"), "care-add-journal") { onAdd("journal") },
        SheetAction(t("Add task", "काम जोड़ें"), careIcon("task"), "care-add-task") { onAdd("task") },
        SheetAction(t("Add visit", "मुलाक़ात जोड़ें"), careIcon("visit"), "care-add-visit") { onAdd("visit") },
        SheetAction(t("Add medicine", "दवा जोड़ें"), careIcon("medicine"), "care-add-medicine") { onScanPrescription() },
        onDocuments?.let { docs -> SheetAction(t("Add document", "कागज़ जोड़ें"), Icons.Default.FolderOpen, "care-add-document") { docs() } },
    ), onDismiss = { addOpen = false })
    // A module slides in from the hub and slides back out on Back (with the predictive back preview), like a native page;
    // the hub keeps its scroll position underneath.
    SaharaScreenHost(opened?.name, Modifier.fillMaxSize(), label = "care-module",
        direction = { from, to -> if (from == null && to != null) SaharaMotion.Direction.Forward else if (from != null && to == null) SaharaMotion.Direction.Back else SaharaMotion.Direction.Switch }) { shownName ->
    val shownTile = shownName?.let { name -> byName[name] }
    if (shownTile != null) {
        // The Medicines tab (route 13) shows the Medicines module as a root page: no back arrow, the bottom bar stays.
        val asTab = shownTile.name == "Medicines" && target == "Medicines" && shell != null
        CareModulePage(shownTile, onBack = if (asTab) null else ({ closeModule() }), add = moduleAdd[shownTile.name]?.takeIf { canAdd }, embedded = shell == null)
    } else SaharaPage(title ?: t("Care", "देखभाल"), modifier = if (shell == null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier, listState = listState, listModifier = Modifier.testTag("care-list"),
        actions = { SaharaPersonAction() },
        fab = if (canAdd) ({ expanded -> ExtendedFloatingActionButton(text = { Text(t("Add", "जोड़ें")) }, icon = { Icon(Icons.Default.Add, null) },
            onClick = { addOpen = true }, expanded = expanded, modifier = Modifier.testTag("care-add-button")) }) else null) {
        item(key = "care-week", contentType = "week") {
            SaharaWeekStrip(week, weekLine, onClick = { if (byName.containsKey("Check-ins")) openModule = "Check-ins" })
        }
        item(key = "care-tools", contentType = "tools") { ToolGrid(tools, Modifier.padding(top = 8.dp)) }
    }
    }
}

/** A module on its own Material page: the module's name in the top bar, a back arrow (tag care-module-back), and its add action as a FAB. */
@Composable private fun CareModulePage(tile: CareModuleTile, onBack: (() -> Unit)?, add: Triple<String, String, () -> Unit>?, embedded: Boolean) {
    // Embedded in the doctor's patient view (no app shell), the page sits below other headers: no status-bar space in its bar.
    SaharaPage(tile.title, modifier = if (embedded) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier, onBack = onBack, backTag = "care-module-back", listModifier = Modifier.testTag("care-list"),
        actions = { if (onBack == null) SaharaPersonAction() },
        fab = if (add != null) ({ expanded -> ExtendedFloatingActionButton(text = { Text(add.first) }, icon = { Icon(Icons.Default.Add, null) },
            onClick = add.third, expanded = expanded, modifier = Modifier.testTag(add.second)) }) else null) {
        item(key = "module-${tile.key}", contentType = "module") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("care-module-${tile.key}"), verticalArrangement = Arrangement.spacedBy(12.dp), content = tile.content)
        }
    }
}
