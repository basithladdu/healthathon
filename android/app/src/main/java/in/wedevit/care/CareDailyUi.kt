package `in`.wedevit.care

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.graphics.graphicsLayer
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
@Composable fun TodayCheckInBar(vm: CareModel, onOpen: () -> Unit, onSymptom: () -> Unit = {}, onDistress: (() -> Unit)? = null) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = CheckInMood.today(items, person, dayStart)
    val capturedAccount = remember(person) { vm.accountNamespace }
    val done = today != null
    // Light card, dark text (team feedback 21 Nov: the dark teal felt heavy). Teal stays as the accent on buttons and icons.
    val band = Color(0xFFFDEFEA)
    val haptic = rememberSaharaHaptic()
    // The mood tapped here; the bloom plays once that mood is really saved (it follows the record, so a failed save shows nothing).
    var tapped by remember { mutableStateOf<String?>(null) }
    Surface(shape = RoundedCornerShape(TodayHero.radius), color = band, contentColor = DoctorCardInk, shadowElevation = 3.dp, border = BorderStroke(1.dp, Color(0xFFE0C8BA)), modifier = Modifier.fillMaxWidth().saharaAppear().saharaPulseGlow(TodayHero.dueBand, TodayHero.radius, active = !done).saharaBloom(tapped != null && today?.title == tapped && tapped != CheckInMood.HARD, SaharaTone.Success.solid, TodayHero.radius).testTag("today-checkin")) { Box(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFE8C2), Color(0xFFFBD8DF), Color(0xFFD2EADF))))) {
        Column(Modifier.saharaContentSize().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { HealthCardLabel(HealthCategory.CheckIn, t("Check-in", "रोज़ का हाल"), Modifier.weight(1f)); Text(LocalDate.now().format(careDateFormat("EEE, d MMM", hindi)), fontSize = 13.sp, color = Color(0xFF5E5650)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("How was today?", "आज का दिन कैसा रहा?"), fontFamily = SaharaTitleFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
                // The saved tick pops in once the check-in is stored (it follows the saved record, not a timer).
                AnimatedVisibility(done, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = .6f) + fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)), exit = fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS))) {
                    Surface(shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, TodayHero.doneBand), modifier = Modifier.testTag("today-checkin-saved")) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = TodayHero.doneBand); Spacer(Modifier.width(4.dp))
                            Text(t("Saved", "सहेजा"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TodayHero.doneBand)
                        }
                    }
                }
            }
            // The three moods first: big tinted tiles with a face and the label under it.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CareDailyFlow.moods.forEachIndexed { index, mood ->
                    val look = TodayMoodLook.of(index)
                    TodayMoodButton(checkInMoodLabel(mood, hindi), look, selected = today?.title == mood, enabled = !busy, modifier = Modifier.weight(1f).fillMaxHeight().saharaStagger(index).testTag("today-checkin-$index")) {
                        val change = CareDailyFlow.checkInChange(today, mood)
                        if (change != null && canEditCareContext(vm, person, capturedAccount)) { haptic(); tapped = mood; if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", mood, "") }
                    }
                }
            }
            // Distress question stays on Today (Dr Sujay: the thermometer must not be buried), as a compact Yes/No row.
            if (onDistress != null) DistressQuestion(vm, onYes = onDistress, compact = true)
            // Today's few words open in under the moods once saved, instead of popping in.
            AnimatedVisibility(!today?.detail.isNullOrBlank(), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                Text(today?.detail.orEmpty(), fontSize = 15.sp, lineHeight = 20.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TodayPillAction("🩹", t("Note a symptom", "तबीयत का नोट लिखें"), HealthCategory.Symptoms.ink, onSymptom, Modifier.weight(1f).fillMaxHeight().saharaStagger(0).testTag("today-checkin-symptom"))
                TodayPillAction("📝", if (today?.detail.isNullOrBlank()) t("Add a few words", "कुछ शब्द लिखें") else t("Change the words", "शब्द बदलें"), HealthCategory.Journal.ink, onOpen, Modifier.weight(1f).fillMaxHeight().saharaStagger(1).testTag("today-checkin-words"))
            }
        }
        // A soft bloom of petals by the Saved tick once an okay or good day is saved. A hard day gets only the quiet tick: nothing celebrates it.
        SaharaMoment(today?.title?.takeIf { it == tapped && it != CheckInMood.HARD }) {
            SaharaLottie(SaharaLottieKind.Bloom, Modifier.align(Alignment.TopEnd).padding(end = 8.dp).size(64.dp))
        }
    } }
}

/** Colours of one mood tile: soft tint, strong border and ink (all >= 4.5:1 on the tint). */
internal class TodayMoodLook(val face: Float, val tint: Color, val accent: Color, val ink: Color) {
    companion object {
        private val looks = listOf(
            TodayMoodLook(0f, Color(0xFFFBD3C9), Color(0xFFC0504D), Color(0xFF8E2F2B)),
            TodayMoodLook(.5f, Color(0xFFFBE3B5), Color(0xFFD99A1E), Color(0xFF7A5200)),
            TodayMoodLook(1f, Color(0xFFCBE6D3), Color(0xFF2E7D4F), Color(0xFF1E5B38)))
        fun of(index: Int) = looks[index.coerceIn(0, 2)]
    }
}

/** One mood on the Today card: a big emoji face over its label on a soft tint. Chosen = 2 dp coloured border, check badge and a slight scale. */
@Composable private fun TodayMoodButton(label: String, look: TodayMoodLook, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (selected) 1.04f else 1f, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "mood-scale")
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        Surface(shape = RoundedCornerShape(18.dp), color = if (selected) look.tint else Color.White, border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) look.accent else look.accent.copy(alpha = .6f)),
            modifier = Modifier.fillMaxSize().heightIn(min = 96.dp).saharaPress(press).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, interactionSource = press, indication = androidx.compose.material3.ripple(), onClick = onClick)) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(56.dp).background(if (selected) Color.White.copy(alpha = .7f) else look.tint, CircleShape).saharaSelectBounce(selected, .2f).clearAndSetSemantics { }, contentAlignment = Alignment.Center) { CareMoodFace(look.face, Modifier.size(44.dp)) }
                Spacer(Modifier.height(4.dp))
                SaharaFitText(label, style = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 18.sp), color = look.ink, maxLines = 2, min = 10f, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).background(look.accent, CircleShape).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
        }
    }
}


/** The Today hero's colours and shapes; one place so the theme tokens can replace them. */
internal object TodayHero {
    val dueBand get() = SaharaTone.Teal.solid
    val doneBand get() = SaharaTone.Success.solid
    val radius = 24.dp
    val buttonRadius = 12.dp
}

@Composable fun QuickCheckInCard(vm: CareModel, onAddWords: () -> Unit, onSymptom: () -> Unit, onDistress: () -> Unit = {}) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = CheckInMood.today(items, person, dayStart)
    val capturedAccount = remember(person) { vm.accountNamespace }
    val haptic = rememberSaharaHaptic()
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("today-checkin")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("How was today?", "आज का दिन कैसा रहा?"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CareDailyFlow.moods.forEach { mood ->
                    FilterChip(selected = today?.title == mood, enabled = !busy, onClick = {
                        val change = CareDailyFlow.checkInChange(today, mood)
                        if (change != null && canEditCareContext(vm, person, capturedAccount)) { haptic(); if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", mood, "") }
                    }, label = { Text(checkInMoodLabel(mood, hindi), fontSize = 15.sp, fontWeight = if (today?.title == mood) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        shape = RoundedCornerShape(14.dp), colors = FilterChipDefaults.filterChipColors(containerColor = Cream, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk),
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).testTag("today-checkin-${CareDailyFlow.moods.indexOf(mood)}"))
                }
            }
            AnimatedVisibility(!today?.detail.isNullOrBlank(), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Text(today?.detail.orEmpty(), fontSize = 14.sp, color = DoctorCardInk, maxLines = 2) }
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
    var open by rememberSaveable(key, forceOpen) { mutableStateOf(defaultOpen || forceOpen) }; val t = careText(); val tick = rememberSaharaTick()
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear().testTag("care-module-$key")) { Column {
        val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).saharaPress(press).clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button) { tick(); open = !open }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("care-module-$key-toggle"), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = tint) { Icon(icon, null, Modifier.padding(8.dp).size(22.dp), tint = DoctorAccent) }
            Spacer(Modifier.width(12.dp)); Text(title, fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
            SaharaAnimatedText("$count") { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.background(Cream, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp)) }; Spacer(Modifier.width(6.dp)); SaharaChevron(open, if (open) t("Collapse $title", "$title बंद करें") else t("Expand $title", "$title खोलें"))
        }
        AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    } }
}

@Composable fun QuietLine(text: String) { Text(text, fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }

/** An expand/collapse chevron that turns smoothly (a quarter of a second) instead of swapping icons. */
@Composable fun SaharaChevron(open: Boolean, description: String?, modifier: Modifier = Modifier, tint: Color = DoctorCardSub) {
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, SaharaMotion.tween(SaharaMotion.EXPAND_MS + 40, easing = SaharaMotion.Emphasized), label = "chevron")
    Icon(Icons.Default.KeyboardArrowDown, description, modifier.graphicsLayer { rotationZ = turn }, tint = tint)
}

/**
 * The rows of one module group. [label] adds a small sub-group heading. A history ([capped], the default) shows its newest five
 * rows with "Show older" (24 Nov: history kept growing); lists of what is still ahead pass capped = false.
 */
@Composable fun <T> CareModuleList(key: String, items: List<T>, empty: String, label: String? = null, keyOf: (T) -> Any, capped: Boolean = true, row: @Composable (T) -> Unit) {
    val shown = rememberShownCount(key)
    if (label != null) Text("$label · ${saharaCountUp(items.size)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.padding(start = 4.dp, top = 4.dp).a11yHeading())
    SaharaStateSwitch(items.isEmpty(), Modifier.fillMaxWidth(), label = "care-module-$key") { isEmpty -> if (isEmpty) { if (empty.isBlank()) QuietLine(empty) else SaharaCalmEmpty(empty) } else Column(Modifier.saharaContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    (if (capped) items.shownOf(shown) else items).forEachIndexed { i, item -> key(keyOf(item)) { Box(Modifier.saharaStagger(i)) { row(item) } } }
    if (capped) SaharaShowOlder(items.size, shown, tag = "care-module-$key")
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
    Surface(shape = RoundedCornerShape(20.dp), color = SaharaSection.Medicines.wash, border = BorderStroke(1.5.dp, SaharaSection.Medicines.solid), modifier = Modifier.fillMaxWidth().testTag("medicine-scan-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaharaIconChip(Icons.Default.DocumentScanner, SaharaSection.Medicines, size = 44)
                Spacer(Modifier.width(12.dp))
                Text(t("Scan a prescription", "पर्चा स्कैन करें"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.weight(1f))
            }
            // Wraps instead of squeezing when the screen is narrow or the text is large.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = SaharaSection.Medicines.solid, contentColor = SaharaSection.Medicines.onSolid), modifier = Modifier.heightIn(min = 48.dp).testTag("care-module-medicines-scan")) {
                    Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Scan", "स्कैन करें"))
                }
                OutlinedButton(onClick = onManual, shape = RoundedCornerShape(14.dp), border = BorderStroke(2.dp, SaharaSection.Medicines.solid), colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper), modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = SaharaSection.Medicines.ink); Spacer(Modifier.width(6.dp)); Text(t("Add manually", "ख़ुद लिखें"), color = SaharaSection.Medicines.ink)
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
    "Conversations" -> CareToolGroupTone.Planning; "Medicines" -> CareToolGroupTone.Medicines; "Visits" -> CareToolGroupTone.Records
    else -> CareToolGroupTone.Daily // To do, Check-ins, Journal
}

/**
 * One tone per group of care tools (24 Nov meeting: "why some blue, some purple?"), so a colour always means the same kind of tool:
 * daily care (how I feel, symptoms, check-ins, journal, to do), medicines, records (visits, calendar, documents, labs, timeline)
 * and planning (goals of care, emergency plan, tubes and lines, community).
 */
internal object CareToolGroupTone {
    val Daily = SaharaTone.Teal
    val Medicines = SaharaTone.Plum
    val Records = SaharaTone.Indigo
    val Planning = SaharaTone.Marigold
}

/** The module's icon in the All care tools list. */
internal fun careModuleIcon(name: String): ImageVector = when (name) {
    "Conversations" -> Icons.Default.Forum; "Medicines" -> careIcon("medicine"); "Visits" -> careIcon("visit")
    "To do" -> careIcon("task"); "Check-ins" -> careIcon("checkin"); else -> careIcon("journal")
}

/** A module on its own screen, with a back arrow to where it was opened from (All care tools, or Today). */
@Composable internal fun CareModuleScreen(tile: CareModuleTile, onBack: () -> Unit) {
    SaharaBackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().saharaScreenEnter().testTag("care-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The module's header band in its section colour (medicines plum, visits indigo, check-ins teal), white title and back; the art floats gently.
        item { SaharaSectionBand(tile.title, careModuleTone(tile.name), onBack = onBack, backTag = "care-module-back") { SaharaBandArt(tile.art, modifier = Modifier.saharaFloat()) } }
        item { Column(Modifier.testTag("care-module-${tile.key}"), verticalArrangement = Arrangement.spacedBy(12.dp), content = tile.content) }
    }
}

/** A white pill action with a coloured emoji and coloured text, side by side with its twin; 48 dp or taller, soft shadow. */
@Composable private fun TodayPillAction(emoji: String, label: String, ink: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Color.White, contentColor = ink, shadowElevation = 2.dp, border = BorderStroke(1.5.dp, Color(0xFFCDBBAE)), modifier = modifier.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(emoji, Modifier.clearAndSetSemantics { }, fontSize = 18.sp); Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = ink, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
