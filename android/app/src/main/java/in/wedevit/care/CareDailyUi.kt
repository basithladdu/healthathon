package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId

internal val careAddKinds = listOf("medicine" to ("Add medicine" to "दवा जोड़ें"), "visit" to ("Add visit" to "मुलाक़ात जोड़ें"), "task" to ("Add task" to "काम जोड़ें"), "checkin" to ("Add check-in" to "आज का हाल लिखें"), "journal" to ("Add note" to "नोट जोड़ें"))

/** One "+ Add" control that opens a menu of every kind of daily-care entry. Medicine routes to the prescription scanner. */
@Composable fun CareAddMenuButton(onAdd: (String) -> Unit, onScanPrescription: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }; val t = careText()
    Box(modifier) {
        Button(onClick = { open = true }, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = androidx.compose.ui.graphics.Color.White), contentPadding = PaddingValues(start = 14.dp, end = 18.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("care-add-button")) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add", "जोड़ें"), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.testTag("care-add-menu")) {
            careAddKinds.forEach { (kind, label) -> DropdownMenuItem(text = { Text(t(label.first, label.second), fontSize = 15.sp, color = DoctorCardInk) }, leadingIcon = { Icon(careIcon(kind), null, tint = DoctorAccent, modifier = Modifier.size(22.dp)) }, onClick = { open = false; if (kind == "medicine") onScanPrescription() else onAdd(kind) }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-add-$kind")) }
        }
    }
}

/**
 * Today's check-in in one tap: a mood button saves today's check-in (or changes the one already saved) through the same
 * save as the full check-in screen. Words and a symptom note are one more tap away. Shown only to people who may edit.
 */
/** Today's check-in as one slim bar; tapping it opens the full "How was your day?" screen. */
@Composable fun TodayCheckInBar(vm: CareModel, onOpen: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = CheckInMood.today(items, person, dayStart)
    val subtitle = today?.let { val label = checkInMoodLabel(it.title, hindi); t("Saved: $label. Tap to change.", "सहेजा: $label। बदलने के लिए टैप करें।") } ?: t("Tap to check in", "दिन का हाल लिखने के लिए टैप करें")
    ActionCard(Paper, Modifier.fillMaxWidth().testTag("today-checkin"), onOpen) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (today != null) Mint else Peach) { Icon(Icons.Default.WbSunny, null, Modifier.padding(9.dp).size(24.dp), tint = DoctorAccent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("How was today?", "आज का दिन कैसा रहा?"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                Text(subtitle, fontSize = 13.sp, color = DoctorCardSub)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = Rust)
        }
    }
}

@Composable fun QuickCheckInCard(vm: CareModel, onAddWords: () -> Unit, onSymptom: () -> Unit, onDistress: () -> Unit = {}) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = CheckInMood.today(items, person, dayStart)
    val capturedAccount = remember(person) { vm.accountNamespace }
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("today-checkin")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("How was today?", "आज का दिन कैसा रहा?"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            Text(if (today != null) t("Saved for today. Tap another to change it.", "आज के लिए सहेजा। बदलना हो तो दूसरा चुनें।") else t("One tap saves today's check-in.", "एक बार छूने से आज का हाल सहेज जाता है।"), fontSize = 13.sp, color = DoctorCardSub)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CareDailyFlow.moods.forEach { mood ->
                    FilterChip(selected = today?.title == mood, enabled = !busy, onClick = {
                        val change = CareDailyFlow.checkInChange(today, mood)
                        if (change != null && canEditCareContext(vm, person, capturedAccount)) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", mood, "") }
                    }, label = { Text(checkInMoodLabel(mood, hindi), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).testTag("today-checkin-${CareDailyFlow.moods.indexOf(mood)}"))
                }
            }
            today?.detail?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = DoctorCardInk, maxLines = 2) }
            DistressQuestion(vm, onYes = onDistress)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAddWords, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("today-checkin-words")) { Text(if (today?.detail.isNullOrBlank()) t("Add a few words", "कुछ शब्द लिखें") else t("Change the words", "शब्द बदलें"), color = RustText) }
                TextButton(onClick = onSymptom, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("today-symptom-note")) { Text(t("Note a symptom", "तबीयत का नोट लिखें"), color = RustText) }
            }
            Text(if (vm.cloud != null) t("Signed in: check-ins and symptom notes reach the family and doctor you invited.", "साइन इन है: आज का हाल और तबीयत के नोट आपके जोड़े परिवार और डॉक्टर तक पहुँचते हैं।")
                else t("Saved on this phone only. Sign in to share it with your doctor.", "सिर्फ़ इस फ़ोन पर सहेजा। डॉक्टर के साथ साझा करने के लिए साइन इन करें।"), fontSize = 12.sp, color = DoctorCardFaint)
        }
    }
}

/** Unsaved text of a new daily-care entry, kept in memory when its sheet is dismissed so reopening it continues where the person left off. Cleared once saved. */
internal object CareAddDrafts {
    data class Draft(val title: String, val detail: String, val due: Long)
    private val drafts = mutableMapOf<String, Draft>()
    fun get(key: String): Draft? = drafts[key]
    fun keep(key: String, draft: Draft) { if (draft.title.isBlank() && draft.detail.isBlank() && draft.due == 0L) drafts.remove(key) else drafts[key] = draft }
    fun clear(key: String) { drafts.remove(key) }
    /** Signing out drops every unsaved entry so it is not kept in memory for the next person using this phone. */
    fun clearAll() { drafts.clear() }
}

/** Pure grouping rules for the Daily care modules. */
internal object CareModules {
    val order = listOf("Conversations", "Medicines", "Visits", "To do", "Check-ins", "Journal")
    const val PREVIEW = 5
    /** Current = what vm.items shows (not moved to Past, not excluded by a prescription); Past = every other medicine of the person. */
    fun splitMedicines(all: List<CareItem>, visible: List<CareItem>): Pair<List<CareItem>, List<CareItem>> { val current = visible.filter { it.kind == "medicine" }; val ids = current.map { it.id }.toSet(); return current to all.filter { it.kind == "medicine" && it.id !in ids } }
    /** Upcoming = undated or due now/later, soonest first; Past = dated before now, newest first. */
    fun splitVisits(visits: List<CareItem>, now: Long): Pair<List<CareItem>, List<CareItem>> = visits.filter { it.due == 0L || it.due >= now }.sortedBy { if (it.due == 0L) Long.MAX_VALUE else it.due } to visits.filter { it.due in 1 until now }.sortedByDescending { it.due }
    fun moduleFor(filter: String): String? = order.firstOrNull { it.equals(filter, true) }
}

@Composable fun CareModuleCard(key: String, title: String, icon: ImageVector, tint: Color, count: Int, defaultOpen: Boolean, forceOpen: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(key, forceOpen) { mutableStateOf(defaultOpen || forceOpen) }; val t = careText()
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear().testTag("care-module-$key")) { Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("care-module-$key-toggle"), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = tint) { Icon(icon, null, Modifier.padding(8.dp).size(22.dp), tint = DoctorAccent) }
            Spacer(Modifier.width(12.dp)); Text(title, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
            SaharaAnimatedText("$count") { Text(it, fontSize = 14.sp, color = DoctorCardSub) }; Spacer(Modifier.width(6.dp)); Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (open) t("Collapse $title", "$title बंद करें") else t("Expand $title", "$title खोलें"), tint = DoctorCardSub)
        }
        AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    } }
}

@Composable fun QuietLine(text: String) { Text(text, fontSize = 14.sp, color = DoctorCardFaint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }

/** Up to CareModules.PREVIEW rows, with "Show all (N)" expanding in place. [label] adds a small sub-group heading. */
@Composable fun <T> CareModuleList(key: String, items: List<T>, empty: String, label: String? = null, keyOf: (T) -> Any, row: @Composable (T) -> Unit) {
    var all by rememberSaveable(key) { mutableStateOf(false) }; val t = careText()
    if (label != null) Text("$label · ${items.size}".uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DoctorCardFaint, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    SaharaStateSwitch(items.isEmpty(), Modifier.fillMaxWidth(), label = "care-module-$key") { isEmpty -> if (isEmpty) QuietLine(empty) else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    (if (all) items else items.take(CareModules.PREVIEW)).forEach { item -> key(keyOf(item)) { row(item) } }
    if (items.size > CareModules.PREVIEW) TextButton(onClick = { all = !all }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-module-$key-more")) { Text(if (all) t("Show fewer", "कम दिखाएँ") else t("Show all (${items.size})", "सभी दिखाएँ (${items.size})"), color = DoctorAccent) }
    } }
}

@Composable fun ConversationRow(note: CareVersion, onRead: (CareVersion) -> Unit) {
    val t = careText()
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = { onRead(note) }, shape = RoundedCornerShape(16.dp), color = SaharaLavender, interactionSource = press, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).saharaPress(press).testTag("care-note-${note.id}")) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(t("Version ${note.version}", "संस्करण ${note.version}") + " · ${date(note.published)}" + note.doctor.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        Text(ReviewWorkflow.label(note), fontSize = 12.sp, color = DoctorCardSub)
    } }
}

@Composable fun MedicineScanAction(onScan: () -> Unit, onManual: () -> Unit) {
    val t = careText()
    Surface(shape = RoundedCornerShape(20.dp), color = SaharaSoftBlue, modifier = Modifier.fillMaxWidth().testTag("medicine-scan-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Paper) { Icon(Icons.Default.DocumentScanner, null, tint = DoctorAccent, modifier = Modifier.padding(10.dp).size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Scan a prescription", "पर्चा स्कैन करें"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                    Text(t("Read the medicines from a photo, then check each one.", "फ़ोटो से दवाइयाँ पढ़ें, फिर हर एक जाँचें।"), fontSize = 13.sp, color = DoctorCardSub)
                }
            }
            // Wraps instead of squeezing when the screen is narrow or the text is large.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.heightIn(min = 48.dp).testTag("care-module-medicines-scan")) {
                    Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Scan & organize", "स्कैन करके जोड़ें"))
                }
                OutlinedButton(onClick = onManual, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = DoctorAccent); Spacer(Modifier.width(6.dp)); Text(t("Add manually", "ख़ुद लिखें"), color = DoctorAccent)
                }
            }
        }
    }
}

/** One Daily care module: a tile in the grid that opens its own screen. [name] stays English for the test tag. */
internal class CareModuleTile(val name: String, val title: String, val subtitle: String, val tint: Color, @param:DrawableRes val art: Int, val count: Int, val content: @Composable ColumnScope.() -> Unit) {
    val key get() = name.lowercase().replace(' ', '-')
}

/** The six modules as a bento grid: one wide tile, a tall one beside two short ones, then two even ones. */
@Composable internal fun CareModuleGrid(tiles: List<CareModuleTile>, onOpen: (String) -> Unit) {
    val byName = tiles.associateBy { it.name }
    @Composable fun tile(name: String, modifier: Modifier, layout: Int) { byName[name]?.let { CareBentoTile(it, modifier, layout) { onOpen(name) } } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tile("Conversations", Modifier.fillMaxWidth().height(116.dp), BENTO_WIDE)
        Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tile("Medicines", Modifier.weight(1f).fillMaxHeight(), BENTO_TALL)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tile("Visits", Modifier.fillMaxWidth().weight(1f), BENTO_SMALL)
                tile("To do", Modifier.fillMaxWidth().weight(1f), BENTO_SMALL)
            }
        }
        Row(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tile("Check-ins", Modifier.weight(1f).fillMaxHeight(), BENTO_TALL)
            tile("Journal", Modifier.weight(1f).fillMaxHeight(), BENTO_TALL)
        }
    }
}

private const val BENTO_WIDE = 0
private const val BENTO_TALL = 1
private const val BENTO_SMALL = 2

@Composable private fun CareBentoTile(tile: CareModuleTile, modifier: Modifier, layout: Int, onClick: () -> Unit) {
    val t = careText()
    val count = if (tile.count == 0) t("Nothing yet", "अभी कुछ नहीं") else "${tile.count}"
    val label = "${tile.title}, $count"
    ActionCard(tile.tint, modifier.testTag("care-module-${tile.key}-toggle").semantics(mergeDescendants = true) { contentDescription = label }, onClick) {
        @Composable fun art(size: Dp, m: Modifier = Modifier) = Image(saharaImage(tile.art, size), null, m.size(size), contentScale = ContentScale.Fit)
        @Composable fun number() = SaharaAnimatedText(count) { Text(it, fontSize = if (tile.count == 0) 13.sp else 26.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk) }
        @Composable fun title() = Text(tile.title, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 1, modifier = Modifier.a11yHeading())
        when (layout) {
            BENTO_WIDE -> Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { title(); Text(tile.subtitle, fontSize = 13.sp, color = DoctorCardSub); Spacer(Modifier.height(10.dp)); number() }
                art(100.dp)
            }
            BENTO_TALL -> Box(Modifier.fillMaxSize().padding(14.dp)) {
                Column(Modifier.align(Alignment.TopStart)) { title(); Text(tile.subtitle, fontSize = 12.sp, color = DoctorCardSub, maxLines = 2) }
                Box(Modifier.align(Alignment.BottomStart)) { number() }
                art(if (tile.name == "Medicines") 112.dp else 72.dp, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp))
            }
            else -> Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { title(); number() }
                art(60.dp)
            }
        }
    }
}

/** A module on its own screen, with a back arrow to the grid. */
@Composable internal fun CareModuleScreen(tile: CareModuleTile, onBack: () -> Unit) {
    val t = careText()
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("care-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("care-module-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to daily care", "रोज़ की देखभाल पर वापस")) }
            Text(tile.title, fontFamily = SaharaTitleFont, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
            Image(saharaImage(tile.art, 56.dp), null, Modifier.size(56.dp), contentScale = ContentScale.Fit)
        } }
        item { Column(Modifier.testTag("care-module-${tile.key}"), verticalArrangement = Arrangement.spacedBy(10.dp), content = tile.content) }
    }
}
