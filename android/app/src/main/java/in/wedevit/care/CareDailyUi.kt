package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ChevronRight
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
        Button(onClick = { open = true }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent, contentColor = androidx.compose.ui.graphics.Color.White), contentPadding = PaddingValues(start = 14.dp, end = 18.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("care-add-button")) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(t("Add", "जोड़ें"), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.testTag("care-add-menu")) {
            careAddKinds.forEach { (kind, label) -> DropdownMenuItem(text = { Text(t(label.first, label.second), fontSize = 15.sp, color = DoctorCardInk) }, leadingIcon = { Icon(careIcon(kind), null, tint = DoctorAccent, modifier = Modifier.size(22.dp)) }, onClick = { open = false; if (kind == "medicine") onScanPrescription() else onAdd(kind) }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-add-$kind")) }
        }
    }
}

/**
 * Today's one main action: "How was today?" with the three moods as big buttons. A tap saves today's check-in (or changes
 * the one already saved) through the same save as the full check-in screen; no extra screen, no Save button. Words, the
 * distress question and a symptom note stay one tap away on the full screen. Shown only to people who may edit.
 */
@Composable fun TodayCheckInBar(vm: CareModel, onOpen: () -> Unit, onSymptom: () -> Unit = {}) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = CheckInMood.today(items, person, dayStart)
    val capturedAccount = remember(person) { vm.accountNamespace }
    val done = today != null
    val band by animateColorAsState(if (done) TodayHero.doneBand else TodayHero.dueBand, SaharaMotion.tween(SaharaMotion.VALUE_MS))
    Surface(shape = RoundedCornerShape(TodayHero.radius), color = band, contentColor = Color.White, modifier = Modifier.fillMaxWidth().saharaAppear().testTag("today-checkin")) {
        Column(Modifier.saharaContentSize().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("How was today?", "आज का दिन कैसा रहा?"), fontFamily = SaharaTitleFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.weight(1f).a11yHeading())
                // The saved tick pops in once the check-in is stored (it follows the saved record, not a timer).
                AnimatedVisibility(done, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = .6f) + fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)), exit = fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS))) {
                    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.testTag("today-checkin-saved")) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = TodayHero.doneBand); Spacer(Modifier.width(4.dp))
                            Text(t("Saved", "सहेजा"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TodayHero.doneBand)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CareDailyFlow.moods.forEachIndexed { index, mood ->
                    TodayMoodButton(checkInMoodLabel(mood, hindi), index / 2f, selected = today?.title == mood, enabled = !busy, ink = band, modifier = Modifier.weight(1f).fillMaxHeight().testTag("today-checkin-$index")) {
                        val change = CareDailyFlow.checkInChange(today, mood)
                        if (change != null && canEditCareContext(vm, person, capturedAccount)) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", mood, "") }
                    }
                }
            }
            today?.detail?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 15.sp, lineHeight = 20.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            // Secondary links: words (and the distress question) on the full check-in screen, and a symptom note.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen, colors = ButtonDefaults.textButtonColors(contentColor = Color.White), contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("today-checkin-words")) {
                    Text(if (today?.detail.isNullOrBlank()) t("Add a few words", "कुछ शब्द लिखें") else t("Change the words", "शब्द बदलें"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp))
                }
                TextButton(onClick = onSymptom, colors = ButtonDefaults.textButtonColors(contentColor = Color.White), contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("today-checkin-symptom")) {
                    Text(t("Note a symptom", "तबीयत का नोट लिखें"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

/** One mood on the Today hero: a small face over its label. Solid white once chosen; a press shrinks it slightly. */
@Composable private fun TodayMoodButton(label: String, face: Float, selected: Boolean, enabled: Boolean, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val fill by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = .14f), SaharaMotion.tween(SaharaMotion.SELECT_MS))
    val text by animateColorAsState(if (selected) ink else Color.White, SaharaMotion.tween(SaharaMotion.SELECT_MS))
    Surface(shape = RoundedCornerShape(TodayHero.buttonRadius), color = fill, border = BorderStroke(2.dp, Color.White.copy(alpha = if (selected) 1f else .7f)),
        modifier = modifier.heightIn(min = 76.dp).saharaPress(press).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, interactionSource = press, indication = androidx.compose.material3.ripple(), onClick = onClick)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CareMoodFace(face, Modifier.size(32.dp).clearAndSetSemantics { })
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = text, textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading, hyphens = androidx.compose.ui.text.style.Hyphens.None))
        }
    }
}

/** The Today hero's colours and shapes; one place so the theme tokens can replace them. */
internal object TodayHero {
    val dueBand get() = SaharaTone.Teal.solid
    val doneBand get() = SaharaTone.Success.solid
    val radius = 12.dp
    val buttonRadius = 12.dp
}

@Composable fun QuickCheckInCard(vm: CareModel, onAddWords: () -> Unit, onSymptom: () -> Unit, onDistress: () -> Unit = {}) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = CheckInMood.today(items, person, dayStart)
    val capturedAccount = remember(person) { vm.accountNamespace }
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("today-checkin")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("How was today?", "आज का दिन कैसा रहा?"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CareDailyFlow.moods.forEach { mood ->
                    FilterChip(selected = today?.title == mood, enabled = !busy, onClick = {
                        val change = CareDailyFlow.checkInChange(today, mood)
                        if (change != null && canEditCareContext(vm, person, capturedAccount)) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", mood, "") }
                    }, label = { Text(checkInMoodLabel(mood, hindi), fontSize = 15.sp, fontWeight = if (today?.title == mood) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        shape = RoundedCornerShape(14.dp), colors = FilterChipDefaults.filterChipColors(containerColor = Cream, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk),
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).testTag("today-checkin-${CareDailyFlow.moods.indexOf(mood)}"))
                }
            }
            today?.detail?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = DoctorCardInk, maxLines = 2) }
            HorizontalDivider(color = DoctorHairline)
            DistressQuestion(vm, onYes = onDistress)
            HorizontalDivider(color = DoctorHairline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAddWords, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("today-checkin-words")) { Text(if (today?.detail.isNullOrBlank()) t("Add a few words", "कुछ शब्द लिखें") else t("Change the words", "शब्द बदलें"), color = RustText) }
                TextButton(onClick = onSymptom, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("today-symptom-note")) { Text(t("Note a symptom", "तबीयत का नोट लिखें"), color = RustText) }
            }
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
            SaharaAnimatedText("$count") { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.background(Cream, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp)) }; Spacer(Modifier.width(6.dp)); Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (open) t("Collapse $title", "$title बंद करें") else t("Expand $title", "$title खोलें"), tint = DoctorCardSub)
        }
        AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    } }
}

@Composable fun QuietLine(text: String) { Text(text, fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }

/** Every row of one module group, always open (a module already has its own screen, so no "Show all" toggle). [label] adds a small sub-group heading. */
@Composable fun <T> CareModuleList(key: String, items: List<T>, empty: String, label: String? = null, keyOf: (T) -> Any, row: @Composable (T) -> Unit) {
    if (label != null) Text("$label · ${items.size}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.padding(start = 4.dp, top = 4.dp).a11yHeading())
    SaharaStateSwitch(items.isEmpty(), Modifier.fillMaxWidth(), label = "care-module-$key") { isEmpty -> if (isEmpty) QuietLine(empty) else Column(Modifier.saharaContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items.forEach { item -> key(keyOf(item)) { row(item) } }
    } }
}

@Composable fun ConversationRow(note: CareVersion, onRead: (CareVersion) -> Unit) {
    val t = careText()
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = { onRead(note) }, shape = RoundedCornerShape(14.dp), color = SaharaLavender, interactionSource = press, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).saharaPress(press).testTag("care-note-${note.id}")) { Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(t("Version ${note.version}", "संस्करण ${note.version}") + " · ${date(note.published)}" + note.doctor.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        Text(ReviewWorkflow.label(note), fontSize = 14.sp, color = DoctorCardSub)
    } }
}

@Composable fun MedicineScanAction(onScan: () -> Unit, onManual: () -> Unit) {
    val t = careText()
    Surface(shape = RoundedCornerShape(20.dp), color = SaharaSoftBlue, modifier = Modifier.fillMaxWidth().testTag("medicine-scan-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Paper) { Icon(Icons.Default.DocumentScanner, null, tint = DoctorAccent, modifier = Modifier.padding(10.dp).size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(t("Scan a prescription", "पर्चा स्कैन करें"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.weight(1f))
            }
            // Wraps instead of squeezing when the screen is narrow or the text is large.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.heightIn(min = 48.dp).testTag("care-module-medicines-scan")) {
                    Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Scan", "स्कैन करें"))
                }
                OutlinedButton(onClick = onManual, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorAccent.copy(alpha = 0.5f)), modifier = Modifier.heightIn(min = 48.dp)) {
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

/** Each Daily care module keeps one section colour everywhere (tile wash, count, icon tile): medicines plum, visits indigo, check-ins teal. */
internal fun careModuleTone(name: String): SaharaTone = when (name) {
    "Conversations" -> SaharaTone.Marigold; "Medicines" -> SaharaTone.Plum; "Visits" -> SaharaTone.Indigo
    "To do" -> SaharaTone.Leaf; "Check-ins" -> SaharaTone.Teal; else -> SaharaTone.Rose
}

/** The module's icon in the All care tools list. */
internal fun careModuleIcon(name: String): ImageVector = when (name) {
    "Conversations" -> Icons.Default.Forum; "Medicines" -> careIcon("medicine"); "Visits" -> careIcon("visit")
    "To do" -> careIcon("task"); "Check-ins" -> careIcon("checkin"); else -> careIcon("journal")
}

/** A module on its own screen, with a back arrow to where it was opened from (All care tools, or Today). */
@Composable internal fun CareModuleScreen(tile: CareModuleTile, onBack: () -> Unit) {
    val t = careText()
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("care-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("care-module-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
            Text(tile.title, fontFamily = SaharaTitleFont, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
            Image(saharaImage(tile.art, 56.dp), null, Modifier.size(56.dp), contentScale = ContentScale.Fit)
        } }
        item { Column(Modifier.testTag("care-module-${tile.key}"), verticalArrangement = Arrangement.spacedBy(12.dp), content = tile.content) }
    }
}
