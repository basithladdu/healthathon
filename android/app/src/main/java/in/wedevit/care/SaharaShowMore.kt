package `in`.wedevit.care

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Short history everywhere (owner's phone test, 24 Nov: "history getting too long, on all routes"). A history list shows
 * the newest [SaharaShowMore.INITIAL] rows; each "Show older" adds [SaharaShowMore.STEP] more, and "Show less" folds it back.
 * Lists are expected newest first. The count is saved, so it survives rotation and Back.
 */
internal object SaharaShowMore {
    const val INITIAL = 5
    const val STEP = 10

    /** How many rows show next after one "Show older" tap. */
    fun next(shown: Int, total: Int, step: Int = STEP): Int = (shown + step).coerceAtMost(total).coerceAtLeast(shown)

    /** Rows still hidden. */
    fun remaining(shown: Int, total: Int): Int = (total - shown).coerceAtLeast(0)
}

/** The saved number of rows shown for one history list; [key] resets it (for example the selected person). */
@Composable internal fun rememberShownCount(key: Any? = null, initial: Int = SaharaShowMore.INITIAL): MutableIntState =
    rememberSaveable(key) { mutableIntStateOf(initial) }

/**
 * "Show older (N more)" and, once opened further, "Show less". Draws nothing while the whole list already fits in [initial].
 * [tag] makes the test tags `<tag>-show-older` and `<tag>-show-less`.
 */
@Composable internal fun SaharaShowOlder(total: Int, shown: MutableIntState, modifier: Modifier = Modifier, initial: Int = SaharaShowMore.INITIAL,
    step: Int = SaharaShowMore.STEP, tag: String = "history", onOpenAll: (() -> Unit)? = null) {
    val t = careText()
    if (total <= initial) return
    val count = shown.intValue.coerceAtMost(total)
    val remaining = SaharaShowMore.remaining(count, total)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (remaining > 0) TextButton(onClick = { if (onOpenAll != null) onOpenAll() else shown.intValue = SaharaShowMore.next(count, total, step) }, modifier = Modifier.heightIn(min = 56.dp).testTag("$tag-show-older")) {
            Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(4.dp))
            Text(if (onOpenAll != null) t("See all ($total)", "सभी देखें ($total)") else t("Show older ($remaining more)", "पुराने देखें ($remaining और)"), color = RustText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (count > initial) TextButton(onClick = { shown.intValue = initial }, modifier = Modifier.heightIn(min = 56.dp).testTag("$tag-show-less")) {
            Icon(Icons.Default.ExpandLess, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(4.dp))
            Text(t("Show less", "कम दिखाएँ"), color = RustText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/**
 * A capped history inside a LazyColumn: the first [shown] of [items] (newest first), then the Show older / Show less row.
 * Read [shown] from [rememberShownCount] in the composable that owns the LazyColumn.
 */
internal fun <T> LazyListScope.cappedItems(items: List<T>, shown: MutableIntState, key: ((T) -> Any)? = null, initial: Int = SaharaShowMore.INITIAL,
    step: Int = SaharaShowMore.STEP, tag: String = "history", title: String? = null, searchText: ((T) -> String)? = null, itemContent: @Composable LazyItemScope.(T) -> Unit) {
    // Rows that appear with "Show older" fade in, and the rest glide to their new place, instead of jumping: Sahara's own
    // calm timing (no overshoot), and nothing at all under Remove animations or Battery Saver. One content type per list,
    // so scrolled-away rows are reused for the same kind of row.
    items(items.take(shown.intValue.coerceAtLeast(initial)), key = key, contentType = { "$tag-row" }) { item ->
        val row = this
        androidx.compose.foundation.layout.Box(if (key != null && SaharaMotion.enabled) Modifier.animateItem(
            fadeInSpec = SaharaMotion.tween(SaharaMotion.LIST_ITEM_MS, easing = SaharaMotion.EmphasizedDecelerate),
            placementSpec = SaharaSprings.spatial(IntOffset.VisibilityThreshold),
            fadeOutSpec = SaharaMotion.tween(SaharaMotion.EXIT_MS, easing = SaharaMotion.EmphasizedAccelerate)) else Modifier) { row.itemContent(item) }
    }
    if (items.size > initial) item(key = "$tag-show-more", contentType = "sahara-show-more") {
        var page by rememberSaveable(tag) { androidx.compose.runtime.mutableStateOf(false) }
        SaharaShowOlder(items.size, shown, initial = initial, step = step, tag = tag, onOpenAll = { page = true })
        if (page) SaharaShowAllPage(items, tag, title, searchText, key, { page = false }, itemContent)
    }
}

/** For a history drawn in a Column (`forEach`): the rows to draw now. Put [SaharaShowOlder] after them. */
internal fun <T> List<T>.shownOf(shown: MutableIntState, initial: Int = SaharaShowMore.INITIAL): List<T> = take(shown.intValue.coerceAtLeast(initial))

/**
 * The system Back for the screen this is drawn on, as a tap: it runs exactly what the back gesture would (the screen's
 * own BackHandler, or the care tabs' Back). For screens whose caller gives no back action.
 */
@Composable internal fun saharaSystemBack(): () -> Unit {
    val owner by rememberUpdatedState(LocalOnBackPressedDispatcherOwner.current)
    return { owner?.onBackPressedDispatcher?.onBackPressed() }
}

/** The 56 dp back arrow alone, for a screen's own heading row (56 dp like Sahara's buttons, for older hands). */
@Composable internal fun SaharaBackButton(onBack: () -> Unit, modifier: Modifier = Modifier, tag: String = "screen-back", enabled: Boolean = true) {
    val t = careText()
    IconButton(onClick = onBack, enabled = enabled, modifier = modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp).testTag(tag)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = DoctorCardInk)
    }
}

/**
 * A visible Back at the top-left for a screen without a section band (older patients do not know the back gesture):
 * a 56 dp arrow and, optionally, the screen's name beside it.
 */
@Composable internal fun SaharaBackRow(onBack: () -> Unit, modifier: Modifier = Modifier, title: String? = null, tag: String = "screen-back", enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        SaharaBackButton(onBack, tag = tag, enabled = enabled)
        if (!title.isNullOrBlank()) Text(title, Modifier.weight(1f).padding(start = 4.dp).a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, lineHeight = 26.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * For a card that lists many lines inside itself (all past doses, all replies): "Show all (N)" while folded, "Show less"
 * once open. Draws nothing while [total] fits in [initial]. Test tag: `<tag>-show-all`.
 */
@Composable internal fun SaharaShowAllToggle(total: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, initial: Int = 3, tag: String = "card") {
    val t = careText()
    if (total <= initial) return
    TextButton(onClick = onToggle, modifier = modifier.heightIn(min = 56.dp).testTag("$tag-show-all")) {
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(4.dp))
        Text(if (expanded) t("Show less", "कम दिखाएँ") else t("Show all ($total)", "सभी दिखाएँ ($total)"), color = RustText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/**
 * "See all": the whole list on its own full-screen page (team, 24 Nov), with a back arrow, a title and a search bar at the top that filters by the text
 * the row shows ([searchText], else the row's own text form). The rows are the same composable the list uses. Tags: "$tag-all-page", "$tag-search".
 */
@Composable internal fun <T> SaharaShowAllPage(items: List<T>, tag: String, title: String?, searchText: ((T) -> String)?, key: ((T) -> Any)?, onClose: () -> Unit,
    itemContent: @Composable LazyItemScope.(T) -> Unit) {
    val t = careText()
    var query by rememberSaveable(tag) { androidx.compose.runtime.mutableStateOf("") }
    val shownItems = androidx.compose.runtime.remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items else items.filter { (searchText?.invoke(it) ?: it.toString()).lowercase().contains(q) }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize().testTag("$tag-all-page"), color = Cream) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("$tag-all-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
                    Text(title ?: t("All", "सभी"), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${shownItems.size}", Modifier.padding(end = 12.dp), color = SaharaColors.InkSub)
                }
                androidx.compose.material3.OutlinedTextField(query, { query = it }, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    placeholder = { Text(t("Search", "खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 56.dp).testTag("$tag-search"))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shownItems, key = key) { item -> itemContent(item) }
                    if (shownItems.isEmpty()) item { Text(t("Nothing matches your search.", "खोज से कुछ नहीं मिला।"), color = SaharaColors.InkSub) }
                }
            }
        }
    }
}


/**
 * For a history drawn in a Column (`shownOf(...).forEach { row }`): the same "See all" button as [cappedItems] gives, with [row] used for both the list
 * and the full-screen page that opens (own header, back arrow, search bar). Put it after the rows. [searchText] is what the search matches for a row.
 */
@Composable internal fun <T> SaharaShowOlder(items: List<T>, shown: MutableIntState, modifier: Modifier = Modifier, initial: Int = SaharaShowMore.INITIAL,
    step: Int = SaharaShowMore.STEP, tag: String = "history", title: String? = null, searchText: ((T) -> String)? = null, key: ((T) -> Any)? = null, row: @Composable (T) -> Unit) {
    if (items.size <= initial) return
    var page by rememberSaveable(tag) { androidx.compose.runtime.mutableStateOf(false) }
    SaharaShowOlder(items.size, shown, modifier, initial, step, tag, onOpenAll = { page = true })
    if (page) SaharaShowAllPage(items, tag, title, searchText, key, { page = false }) { item -> row(item) }
}
