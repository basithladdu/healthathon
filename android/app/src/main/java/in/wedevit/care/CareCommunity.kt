@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Community for patients, families and carers (owner, 6 Nov 2026: "add an actual thing"). Real groups, posts and replies on
 * Sahara's server (20261106120000_community.sql), for signed-in members only. Authors show by a community name they choose.
 * The group list is server data; the clinicians confirm it (blockers.md). Moderation is by the Sahara team's verifier role.
 */
private enum class CommunityTone(val solid: Color, val wash: Color, val ink: Color) {
    // Vivid section accents from HUMAN_APP_GUIDE.md section 3 (white text on solid >= 5.2:1, ink on wash >= 4.9:1).
    Teal(Color(0xFF0F6E6A), Color(0xFFD3EEEC), Color(0xFF0B5551)),
    Rose(Color(0xFFC2185B), Color(0xFFFCE4EE), Color(0xFF9C1048)),
    Plum(Color(0xFF8A3B78), Color(0xFFF4DDEE), Color(0xFF6E2C60)),
    Indigo(Color(0xFF3F51A3), Color(0xFFE0E4F7), Color(0xFF2F3E84)),
    Leaf(Color(0xFF386D29), Color(0xFFDDEFD5), Color(0xFF2A5420)),
    Marigold(Color(0xFF8F5A00), Color(0xFFFCEBC7), Color(0xFF6E4500)),
    Rust(Color(0xFFB9472C), Color(0xFFFFE3D3), Color(0xFF9E3B23));
    companion object {
        fun of(key: String) = when (key) { "teal" -> Teal; "rose" -> Rose; "plum" -> Plum; "sky" -> Indigo; "leaf" -> Leaf; "marigold", "gold" -> Marigold; else -> Rust }
    }
}

private fun communityIcon(groupId: String): ImageVector = when (groupId) {
    "family-carers" -> Icons.Default.FamilyRestroom
    "living-with-cancer" -> Icons.Default.Favorite
    "grief-loss" -> Icons.Default.Spa
    "ask-care-team" -> Icons.Default.QuestionAnswer
    else -> Icons.Default.Groups
}

/** Body text that never splits a word across lines (owner, 6 Nov). */
@Composable private fun communityBody(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None)
@Composable private fun communityTitle(): TextStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, lineBreak = LineBreak.Heading, hyphens = Hyphens.None)

private const val HOME = "home"

@Composable fun CareCommunityScreen(cloud: CareCloud?, onAccount: () -> Unit, onBack: () -> Unit) {
    val t = careText()
    val context = LocalContext.current
    val store = remember { CommunityStore(context) }
    val scope = rememberCoroutineScope()
    val sessionFlow = remember(cloud) { cloud?.session ?: MutableStateFlow<CareSession?>(null) }
    val accountId = sessionFlow.collectAsState().value?.userId
    // "home", "group:<id>" or "write:<id>"; kept across rotation.
    var route by rememberSaveable { mutableStateOf(HOME) }
    var home by remember(accountId) { mutableStateOf<CommunityHome?>(null) }
    var homeError by remember(accountId) { mutableStateOf<CommunityCopy?>(null) }
    val feeds = remember(accountId) { mutableStateMapOf<String, CommunityFeed>() }
    val feedErrors = remember(accountId) { mutableStateMapOf<String, CommunityCopy>() }
    var pending by remember(accountId) { mutableStateOf(accountId?.let(store::pending).orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var justSent by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<CommunityCopy?>(null) }
    var offlineSaved by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var removeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    val myName = home?.myName ?: feeds.values.firstNotNullOfOrNull { it.myName }
    val canModerate = home?.canModerate == true

    suspend fun flushOutbox() {
        if (cloud == null || accountId == null) return
        store.flush(accountId) { cloud.communitySend(it) }
        pending = store.pending(accountId)
    }
    fun loadHome() {
        if (cloud == null || accountId == null) return
        scope.launch {
            loading = true
            try { flushOutbox(); home = cloud.communityHome(); homeError = null }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { homeError = CommunityRules.serverMessage(error) }
            finally { loading = false }
        }
    }
    fun loadFeed(groupId: String) {
        if (cloud == null || accountId == null) return
        scope.launch {
            loading = true
            try { flushOutbox(); feeds[groupId] = cloud.communityFeed(groupId); feedErrors.remove(groupId) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { feedErrors[groupId] = CommunityRules.serverMessage(error) }
            finally { loading = false }
        }
    }
    fun updatePost(groupId: String, postId: String, change: (CommunityPost) -> CommunityPost) {
        val feed = feeds[groupId] ?: return
        feeds[groupId] = feed.copy(posts = feed.posts.map { if (it.id == postId) change(it) else it })
    }
    /** Sends now; offline, it is kept on this phone and a dialog says so. Any other failure keeps the text where it is. */
    fun send(item: CommunityPending, groupId: String, onDone: () -> Unit) {
        if (cloud == null || accountId == null || sending) return
        scope.launch {
            sending = true
            try {
                if (myName == null) {
                    val chosen = cloud.communitySetName(nameDraft)
                    home = home?.copy(myName = chosen); feeds[groupId]?.let { feeds[groupId] = it.copy(myName = chosen) }
                }
                cloud.communitySend(item)
                justSent = item.id; onDone()
                feeds[groupId] = cloud.communityFeed(groupId)
            } catch (cancel: CancellationException) { throw cancel }
            catch (offline: CareSyncOffline) {
                if (myName != null && store.add(item)) { pending = store.pending(accountId); offlineSaved = true; onDone() }
                else message = CommunityRules.serverMessage(offline)
            } catch (error: Exception) { message = CommunityRules.serverMessage(error) }
            finally { sending = false }
        }
    }
    fun call(action: suspend (CareCloud) -> Unit, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}) {
        if (cloud == null) return
        scope.launch {
            try { action(cloud); onSuccess() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { onFailure(); message = CommunityRules.serverMessage(error) }
        }
    }

    LaunchedEffect(accountId) { if (accountId != null) loadHome() }
    LaunchedEffect(justSent) { if (justSent != null) { delay(2500); justSent = null } }

    val depth = { r: String -> when { r == HOME -> 0; r.startsWith("group:") -> 1; else -> 2 } }
    SaharaScreenHost(route, Modifier.fillMaxSize().background(Cream).testTag("community-screen"), label = "community pages",
        direction = { from, to -> if (depth(to) > depth(from)) SaharaMotion.Direction.Forward else SaharaMotion.Direction.Back }) { shown ->
        when {
            shown == HOME -> {
                BackHandler(onBack = onBack)
                CommunityHomePage(signedIn = cloud != null && accountId != null, home = home, error = homeError, loading = loading, waiting = pending.size,
                    onBack = onBack, onAccount = onAccount, onRetry = ::loadHome, onOpen = { route = "group:$it"; loadFeed(it) })
            }
            shown.startsWith("group:") -> {
                val groupId = shown.removePrefix("group:")
                BackHandler { route = HOME; loadHome() }
                val group = home?.groups?.firstOrNull { it.id == groupId }
                val waitingHere = pending.filter { (it.kind == CommunityPending.POST && it.target == groupId) || (it.kind == CommunityPending.REPLY && feeds[groupId]?.posts?.any { p -> p.id == it.target } == true) }
                CommunityFeedPage(group = group, groupId = groupId, feed = feeds[groupId], error = feedErrors[groupId], loading = loading, pending = waitingHere,
                    myName = myName, canModerate = canModerate || feeds[groupId]?.canModerate == true, justSent = justSent, sending = sending,
                    onBack = { route = HOME; loadHome() }, onRetry = { loadFeed(groupId) }, onWrite = { route = "write:$groupId" },
                    onReact = { post, kind ->
                        val on = if (kind == "helpful") !post.myHelpful else !post.myHugs
                        val toggle = { p: CommunityPost, value: Boolean -> if (kind == "helpful") p.copy(myHelpful = value, helpful = (p.helpful + if (value) 1 else -1).coerceAtLeast(0)) else p.copy(myHugs = value, hugs = (p.hugs + if (value) 1 else -1).coerceAtLeast(0)) }
                        updatePost(groupId, post.id) { toggle(it, on) }
                        call({ it.communityReact(post.id, kind, on) }, onFailure = { updatePost(groupId, post.id) { p -> toggle(p, !on) } })
                    },
                    onReply = { postId, text, done -> accountId?.let { send(CommunityPending(UUID.randomUUID().toString(), it, CommunityPending.REPLY, postId, text.trim(), System.currentTimeMillis()), groupId, done) } },
                    onReport = { kind, id -> reportTarget = kind to id },
                    onRemove = { kind, id -> removeTarget = kind to id },
                    onModerate = { kind, id, hide -> call({ it.communityModerate(kind, id, hide) }, onSuccess = { loadFeed(groupId) }) })
            }
            else -> {
                val groupId = shown.removePrefix("write:")
                BackHandler { route = "group:$groupId" }
                CommunityWritePage(groups = home?.groups.orEmpty(), groupId = groupId, needsName = myName == null, name = nameDraft, onName = { nameDraft = it.take(CommunityRules.NAME_MAX + 10) },
                    text = draft, onText = { draft = it.take(CommunityRules.POST_MAX + 50) }, sending = sending,
                    onGroup = { route = "write:$it" }, onBack = { route = "group:$groupId" },
                    onPost = {
                        val problem = (if (myName == null) CommunityRules.nameError(nameDraft) else null) ?: CommunityRules.postError(draft)
                        if (problem != null) message = problem
                        else accountId?.let { send(CommunityPending(UUID.randomUUID().toString(), it, CommunityPending.POST, groupId, draft.trim(), System.currentTimeMillis()), groupId) { draft = ""; route = "group:$groupId" } }
                    })
            }
        }
    }

    message?.let { copy -> CommunityMessageDialog(copy) { message = null } }
    if (offlineSaved) AlertDialog(onDismissRequest = { offlineSaved = false }, icon = { Icon(Icons.Default.CloudOff, null) },
        title = { Text(t("You're offline", "आप ऑफ़लाइन हैं")) },
        text = { Text(t("Your words are saved on this phone. They will be sent when you're online and open Community.", "आपके शब्द इस फ़ोन में सुरक्षित हैं। इंटरनेट आने पर समुदाय खोलते ही भेज दिए जाएँगे।"), style = communityBody()) },
        confirmButton = { TextButton(onClick = { offlineSaved = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close", "बंद करें"), color = RustText, fontWeight = FontWeight.SemiBold) } },
        modifier = Modifier.testTag("community-offline-dialog"))
    reportTarget?.let { (kind, id) ->
        CommunityReportDialog(kind, onDismiss = { reportTarget = null }, onReport = { reason ->
            reportTarget = null
            val groupId = route.substringAfter(":")
            call({ if (kind == "post") it.communityReport(id, null, reason) else it.communityReport(null, id, reason) }, onSuccess = {
                if (kind == "post") updatePost(groupId, id) { it.copy(reported = true) }
                else feeds[groupId]?.let { feed -> feeds[groupId] = feed.copy(posts = feed.posts.map { p -> p.copy(replies = p.replies.map { r -> if (r.id == id) r.copy(reported = true) else r }) }) }
            })
        })
    }
    removeTarget?.let { (kind, id) ->
        val groupId = route.substringAfter(":")
        val queued = pending.any { it.id == id }
        AlertDialog(onDismissRequest = { removeTarget = null },
            title = { Text(if (kind == "post") t("Delete your post?", "अपनी पोस्ट हटाएँ?") else t("Delete your reply?", "अपना जवाब हटाएँ?")) },
            text = { Text(if (queued) t("It hasn't been sent yet. It will be removed from this phone.", "यह अभी भेजा नहीं गया है। इसे इस फ़ोन से हटा दिया जाएगा।")
                else t("It will be removed for everyone. This can't be undone.", "यह सबके लिए हट जाएगा। इसे वापस नहीं लाया जा सकता।"), style = communityBody()) },
            confirmButton = { TextButton(onClick = {
                removeTarget = null
                if (queued) { store.remove(id); pending = accountId?.let(store::pending).orEmpty() }
                else call({ it.communityRemoveMine(kind, id) }, onSuccess = { loadFeed(groupId) })
            }, modifier = Modifier.heightIn(min = 48.dp).testTag("community-remove-confirm")) { Text(t("Delete", "हटाएँ"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { removeTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Keep", "रहने दें"), color = RustText) } })
    }
}

/** Back arrow and the screen title, like a top app bar. */
@Composable private fun CommunityTitleRow(title: String, onBack: () -> Unit, tone: CommunityTone? = null) {
    val t = careText()
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("community-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = Ink) }
        Spacer(Modifier.width(4.dp))
        if (tone != null) { Box(Modifier.size(width = 4.dp, height = 24.dp).clip(CircleShape).background(tone.solid)); Spacer(Modifier.width(8.dp)) }
        Text(title, Modifier.weight(1f).a11yHeading(), style = communityTitle(), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The pinned house rule: short and gentle, at the top of every group. */
@Composable private fun CommunityGuideline() {
    val t = careText()
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFCEBC7), border = BorderStroke(1.dp, Color(0x598F5A00)), modifier = Modifier.fillMaxWidth().testTag("community-guideline")) {
        Row(Modifier.padding(12.dp).a11yCard(), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.VolunteerActivism, null, Modifier.size(24.dp), tint = Color(0xFF6E4500))
            Spacer(Modifier.width(12.dp))
            Text(t("Be kind. This isn't medical advice. For symptoms, talk to your care team.", "प्यार से बात करें। यह डॉक्टरी सलाह नहीं है। तकलीफ़ हो तो अपनी देखभाल टीम से बात करें।"),
                style = communityBody(), color = DoctorCardInk)
        }
    }
}

@Composable private fun CommunityNotice(copy: CommunityCopy, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val t = careText()
    Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = modifier.fillMaxWidth().testTag("community-notice")) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).announce(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(24.dp), tint = DoctorCardSub)
            Spacer(Modifier.width(12.dp))
            Text(t(copy.en, copy.hi), Modifier.weight(1f).padding(vertical = 8.dp), style = communityBody(), color = DoctorCardInk)
            if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Try again", "फिर कोशिश करें"), color = RustText, fontWeight = FontWeight.SemiBold, softWrap = false) }
        }
    }
}

@Composable private fun CommunityLoadingRow() {
    val t = careText()
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).announce(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), color = Rust, strokeWidth = 2.5.dp)
        Spacer(Modifier.width(12.dp))
        Text(t("Loading…", "खुल रहा है…"), style = communityBody(), color = DoctorCardSub)
    }
}

@Composable private fun CommunityHomePage(signedIn: Boolean, home: CommunityHome?, error: CommunityCopy?, loading: Boolean, waiting: Int,
    onBack: () -> Unit, onAccount: () -> Unit, onRetry: () -> Unit, onOpen: (String) -> Unit) {
    val t = careText()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { CommunityTitleRow(t("Community", "समुदाय"), onBack) }
        if (!signedIn) item {
            Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear().testTag("community-sign-in")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CommunityTone.Teal.solid), contentAlignment = Alignment.Center) { Icon(Icons.Default.Forum, null, Modifier.size(24.dp), tint = Color.White) }
                    Text(t("Talk with families and patients who understand", "उन परिवारों और मरीज़ों से बात करें जो समझते हैं"), style = communityTitle(), color = Ink)
                    Text(t("Only signed-in members can read and write posts.", "पोस्ट सिर्फ़ साइन इन किए सदस्य पढ़ और लिख सकते हैं।"), style = communityBody(), color = DoctorCardSub)
                    CommunityPrimaryButton(t("Sign in to join", "जुड़ने के लिए साइन इन करें"), onClick = onAccount, modifier = Modifier.fillMaxWidth().testTag("community-sign-in-button"))
                }
            }
        }
        else {
            if (waiting > 0) item {
                CommunityNotice(if (waiting == 1) CommunityCopy("1 post or reply is waiting to send.", "1 पोस्ट या जवाब भेजा जाना बाकी है।")
                    else CommunityCopy("$waiting posts or replies are waiting to send.", "$waiting पोस्ट या जवाब भेजे जाने बाकी हैं।"), onRetry)
            }
            if (error != null) item { CommunityNotice(error, onRetry) }
            if (home == null && error == null && loading) item { CommunityLoadingRow() }
            home?.groups?.let { groups ->
                item { Text(t("Groups", "समूह"), Modifier.a11yHeading().padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink) }
                // Two tiles per row; each group keeps its own colour everywhere.
                items(groups.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { group -> CommunityGroupTile(group, Modifier.weight(1f).fillMaxHeight()) { onOpen(group.id) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (groups.isEmpty()) item { CommunityNotice(CommunityCopy("No groups are open right now.", "अभी कोई समूह खुला नहीं है।"), onRetry) }
            }
        }
    }
}

@Composable private fun CommunityGroupTile(group: CommunityGroup, modifier: Modifier, onClick: () -> Unit) {
    val t = careText()
    val tone = CommunityTone.of(group.tone)
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.saharaAppear().saharaPress(interaction).heightIn(min = 140.dp).clip(RoundedCornerShape(16.dp)).background(tone.wash)
        .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick).a11yCard().padding(16.dp).testTag("community-group-${group.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tone.solid), contentAlignment = Alignment.Center) { Icon(communityIcon(group.id), null, Modifier.size(24.dp), tint = Color.White) }
        Text(t(group.nameEn, group.nameHi), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, lineBreak = LineBreak.Heading, hyphens = Hyphens.None), fontWeight = FontWeight.SemiBold, color = tone.ink)
        if (group.aboutEn.isNotBlank()) Text(t(group.aboutEn, group.aboutHi.ifBlank { group.aboutEn }), style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None), color = DoctorCardSub)
        Spacer(Modifier.weight(1f))
        Text(if (group.posts == 1) t("1 post", "1 पोस्ट") else t("${group.posts} posts", "${group.posts} पोस्ट"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tone.ink)
    }
}

@Composable private fun CommunityPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled && !busy, shape = RoundedCornerShape(50), modifier = modifier.heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)) {
        if (busy) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        else if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
    }
}

@Composable private fun CommunityFeedPage(group: CommunityGroup?, groupId: String, feed: CommunityFeed?, error: CommunityCopy?, loading: Boolean, pending: List<CommunityPending>,
    myName: String?, canModerate: Boolean, justSent: String?, sending: Boolean, onBack: () -> Unit, onRetry: () -> Unit, onWrite: () -> Unit,
    onReact: (CommunityPost, String) -> Unit, onReply: (String, String, () -> Unit) -> Unit, onReport: (String, String) -> Unit,
    onRemove: (String, String) -> Unit, onModerate: (String, String, Boolean) -> Unit) {
    val t = careText()
    val tone = CommunityTone.of(group?.tone ?: "rust")
    var replyingTo by rememberSaveable { mutableStateOf<String?>(null) }
    var replyText by rememberSaveable { mutableStateOf("") }
    var replyError by remember { mutableStateOf<CommunityCopy?>(null) }
    val queuedIds = pending.map { it.id }.toSet()
    val you = myName ?: t("You", "आप")
    // Posts waiting on this phone appear first, marked, so nothing seems lost.
    val waitingPosts = pending.filter { it.kind == CommunityPending.POST }.sortedByDescending { it.savedAt }.map {
        CommunityPost(it.id, groupId, it.body, it.savedAt, you, mine = true, byTeam = false, hidden = false, helpful = 0, hugs = 0, myHelpful = false, myHugs = false, reported = false, reports = 0, replies = emptyList())
    }
    val posts = (waitingPosts + feed?.posts.orEmpty().map { post ->
        val extra = pending.filter { it.kind == CommunityPending.REPLY && it.target == post.id }.sortedBy { it.savedAt }
            .map { CommunityReply(it.id, it.body, it.savedAt, you, mine = true, byTeam = false, hidden = false, reported = false, reports = 0) }
        if (extra.isEmpty()) post else post.copy(replies = (post.replies + extra).distinctBy { it.id })
    }).distinctBy { it.id }
    val now = remember(feed) { System.currentTimeMillis() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("community-feed"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { CommunityTitleRow(group?.let { t(it.nameEn, it.nameHi) } ?: t("Community", "समुदाय"), onBack, tone) }
            item { CommunityGuideline() }
            if (error != null) item { CommunityNotice(error, onRetry) }
            if (feed == null && error == null && loading) item { CommunityLoadingRow() }
            if (feed != null && posts.isEmpty()) item {
                SaharaEmpty(t("No posts here yet", "यहाँ अभी कोई पोस्ट नहीं है"), t("Say hello, or ask what helped others. Your words could help someone today.", "नमस्ते कहें, या पूछें कि दूसरों को किस बात से मदद मिली। आपकी बात आज किसी के काम आ सकती है।"),
                    icon = Icons.Default.Forum, actionLabel = t("Write the first post", "पहली पोस्ट लिखें"), onAction = onWrite, modifier = Modifier.saharaAppear())
            }
            items(posts, key = { it.id }) { post ->
                CommunityPostCard(post, now = now, tone = tone, queued = post.id in queuedIds, queuedIds = queuedIds, justSent = justSent == post.id, canModerate = canModerate,
                    replying = replyingTo == post.id, replyText = if (replyingTo == post.id) replyText else "", replyError = if (replyingTo == post.id) replyError else null, sending = sending,
                    onReact = { onReact(post, it) }, onReplyOpen = { replyingTo = if (replyingTo == post.id) null else post.id; replyText = ""; replyError = null },
                    onReplyText = { replyText = it.take(CommunityRules.REPLY_MAX + 50); replyError = null },
                    onReplySend = {
                        val problem = CommunityRules.replyError(replyText)
                        if (problem != null) replyError = problem
                        else if (myName == null) replyError = CommunityCopy("Write a post first to choose the name others will see.", "दूसरों को दिखने वाला नाम चुनने के लिए पहले एक पोस्ट लिखें।")
                        else onReply(post.id, replyText) { replyingTo = null; replyText = "" }
                    },
                    onReport = onReport, onRemove = onRemove, onModerate = onModerate)
            }
        }
        if (feed != null) ExtendedFloatingActionButton(text = { Text(t("Write a post", "पोस्ट लिखें"), fontSize = 16.sp) }, icon = { Icon(Icons.Default.Edit, null) }, onClick = onWrite,
            containerColor = Rust, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 56.dp).testTag("community-write"))
    }
}

/** One status per post at most: hidden, waiting, just posted, Sahara team, open reports (team only), or reported by you. */
@Composable private fun CommunityPill(text: String, color: Color, wash: Color, icon: ImageVector? = null, tag: String = "community-pill") {
    Row(Modifier.clip(RoundedCornerShape(50)).background(wash).padding(horizontal = 10.dp, vertical = 4.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = color); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, softWrap = false)
    }
}

@Composable private fun CommunityStatus(hidden: Boolean, queued: Boolean, justSent: Boolean, byTeam: Boolean, reports: Int, reported: Boolean, canModerate: Boolean) {
    val t = careText()
    when {
        hidden -> CommunityPill(t("Hidden", "छिपी हुई"), DoctorStatusWarn, DoctorStatusWarnBg, Icons.Default.VisibilityOff)
        queued -> CommunityPill(t("Waiting to send", "भेजना बाकी"), DoctorStatusWarn, DoctorStatusWarnBg, Icons.Default.CloudOff)
        justSent -> {
            // The sent tick: pops in once, then the pill stays for a moment.
            val shown = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(shown, enter = fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS), initialScale = .6f)) {
                CommunityPill(t("Posted", "भेज दिया"), DoctorStatusGood, DoctorStatusGoodBg, Icons.Default.CheckCircle, tag = "community-sent-tick")
            }
        }
        byTeam -> CommunityPill(t("Sahara team", "Sahara टीम"), CommunityTone.Indigo.ink, CommunityTone.Indigo.wash)
        canModerate && reports > 0 -> CommunityPill(if (reports == 1) t("1 report", "1 रिपोर्ट") else t("$reports reports", "$reports रिपोर्ट"), DoctorStatusRest, DoctorStatusRestBg, Icons.Default.Flag)
        reported -> CommunityPill(t("Reported", "रिपोर्ट की"), DoctorCardSub, CommunityWell)
    }
}

private val CommunityWell = Color(0xFFF1E9E1)

@Composable private fun CommunityAvatar(name: String, tone: CommunityTone, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(tone.wash), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tone.ink)
    }
}

@Composable private fun CommunityAction(text: String, icon: ImageVector, onClick: () -> Unit, selected: Boolean = false, tone: CommunityTone = CommunityTone.Rust, tag: String = "") {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        border = BorderStroke(1.dp, if (selected) tone.solid else DoctorCardFaint.copy(alpha = .5f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) tone.wash else Paper, contentColor = if (selected) tone.ink else DoctorCardInk),
        modifier = Modifier.heightIn(min = 48.dp).then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

@Composable private fun CommunityPostCard(post: CommunityPost, now: Long, tone: CommunityTone, queued: Boolean, queuedIds: Set<String>, justSent: Boolean, canModerate: Boolean,
    replying: Boolean, replyText: String, replyError: CommunityCopy?, sending: Boolean, onReact: (String) -> Unit, onReplyOpen: () -> Unit, onReplyText: (String) -> Unit,
    onReplySend: () -> Unit, onReport: (String, String) -> Unit, onRemove: (String, String) -> Unit, onModerate: (String, String, Boolean) -> Unit) {
    val t = careText()
    Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear().saharaContentSize().testTag("community-post")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.a11yCard(), verticalAlignment = Alignment.CenterVertically) {
                CommunityAvatar(post.name, tone)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.name, style = MaterialTheme.typography.titleSmall, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val ago = CommunityRules.ago(now, post.createdMs)
                    Text(t(ago.en, ago.hi), style = MaterialTheme.typography.bodySmall, fontSize = 14.sp, color = DoctorCardSub)
                }
                CommunityStatus(post.hidden, queued, justSent, post.byTeam, post.reports, post.reported, canModerate)
            }
            Text(post.body, style = communityBody(), color = Ink)
            if (!queued && !post.hidden) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunityAction(if (post.helpful > 0) t("Helpful ${post.helpful}", "मददगार ${post.helpful}") else t("Helpful", "मददगार"), Icons.Default.ThumbUp, { onReact("helpful") }, post.myHelpful, CommunityTone.Teal, "community-helpful")
                CommunityAction(if (post.hugs > 0) t("Hugs ${post.hugs}", "झप्पी ${post.hugs}") else t("Hugs", "झप्पी"), Icons.Default.Favorite, { onReact("hugs") }, post.myHugs, CommunityTone.Rose, "community-hugs")
                CommunityAction(t("Reply", "जवाब दें"), Icons.AutoMirrored.Filled.Reply, onReplyOpen, replying, tone, "community-reply")
                if (post.mine) CommunityAction(t("Delete", "हटाएँ"), Icons.Default.Delete, { onRemove("post", post.id) }, tag = "community-delete")
                else if (!post.reported) CommunityAction(t("Report", "रिपोर्ट करें"), Icons.Default.Flag, { onReport("post", post.id) }, tag = "community-report")
                if (canModerate) CommunityAction(t("Hide", "छिपाएँ"), Icons.Default.VisibilityOff, { onModerate("post", post.id, true) }, tag = "community-hide")
            } else if (queued) Row { CommunityAction(t("Delete", "हटाएँ"), Icons.Default.Delete, { onRemove("post", post.id) }, tag = "community-delete") }
            else if (canModerate) Row { CommunityAction(t("Show again", "फिर दिखाएँ"), Icons.Default.Visibility, { onModerate("post", post.id, false) }, tag = "community-show") }
            post.replies.forEach { reply -> CommunityReplyRow(reply, now, tone, reply.id in queuedIds, canModerate, onReport, onRemove, onModerate) }
            AnimatedVisibility(replying, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = replyText, onValueChange = onReplyText, modifier = Modifier.fillMaxWidth().testTag("community-reply-field"),
                        label = { Text(t("Your reply", "आपका जवाब")) }, minLines = 2, isError = replyError != null, textStyle = communityBody(),
                        supportingText = { Text(replyError?.let { t(it.en, it.hi) } ?: "${replyText.trim().length} / ${CommunityRules.REPLY_MAX}") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rust, focusedLabelColor = RustText, cursorColor = Rust))
                    CommunityPrimaryButton(t("Send reply", "जवाब भेजें"), onClick = onReplySend, busy = sending, icon = Icons.AutoMirrored.Filled.Send, modifier = Modifier.fillMaxWidth().testTag("community-reply-send"))
                }
            }
        }
    }
}

@Composable private fun CommunityReplyRow(reply: CommunityReply, now: Long, tone: CommunityTone, queued: Boolean, canModerate: Boolean,
    onReport: (String, String) -> Unit, onRemove: (String, String) -> Unit, onModerate: (String, String, Boolean) -> Unit) {
    val t = careText()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).saharaAppear().testTag("community-reply-row")) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(tone.solid.copy(alpha = .45f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.a11yCard(), verticalAlignment = Alignment.CenterVertically) {
                val ago = CommunityRules.ago(now, reply.createdMs)
                Text(reply.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("  ·  " + t(ago.en, ago.hi), style = MaterialTheme.typography.bodySmall, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, softWrap = false)
                Spacer(Modifier.weight(1f))
                CommunityStatus(reply.hidden, queued, false, reply.byTeam, reply.reports, reply.reported, canModerate)
            }
            Text(reply.body, style = communityBody(), color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reply.mine) TextButton(onClick = { onRemove("reply", reply.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Delete", "हटाएँ"), color = RustText) }
                else if (!reply.reported && !reply.hidden) TextButton(onClick = { onReport("reply", reply.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Report", "रिपोर्ट करें"), color = RustText) }
                if (canModerate && !queued) TextButton(onClick = { onModerate("reply", reply.id, !reply.hidden) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (reply.hidden) t("Show again", "फिर दिखाएँ") else t("Hide", "छिपाएँ"), color = RustText)
                }
            }
        }
    }
}

/** Writing a post is one screen: the group (already chosen), your name the first time, your words, and Share post. */
@Composable private fun CommunityWritePage(groups: List<CommunityGroup>, groupId: String, needsName: Boolean, name: String, onName: (String) -> Unit, text: String, onText: (String) -> Unit,
    sending: Boolean, onGroup: (String) -> Unit, onBack: () -> Unit, onPost: () -> Unit) {
    val t = careText()
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("community-write-page"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { CommunityTitleRow(t("Write a post", "पोस्ट लिखें"), onBack) }
            if (groups.size > 1) item {
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group ->
                        val tone = CommunityTone.of(group.tone); val selected = group.id == groupId
                        Row(Modifier.clip(RoundedCornerShape(50)).background(if (selected) tone.solid else Paper).border(1.5.dp, tone.solid, RoundedCornerShape(50))
                            .selectable(selected = selected, onClick = { onGroup(group.id) }, role = Role.RadioButton).heightIn(min = 48.dp).padding(horizontal = 16.dp).testTag("community-write-group-${group.id}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (selected) { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(6.dp)) }
                            Text(t(group.nameEn, group.nameHi), style = MaterialTheme.typography.labelLarge, fontSize = 15.sp, color = if (selected) Color.White else tone.ink, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            if (needsName) item {
                OutlinedTextField(value = name, onValueChange = onName, modifier = Modifier.fillMaxWidth().testTag("community-name-field"), singleLine = true,
                    label = { Text(t("Your name in the community", "समुदाय में आपका नाम")) },
                    supportingText = { Text(t("Others see only this name, never your phone or email.", "दूसरों को सिर्फ़ यही नाम दिखेगा, आपका फ़ोन या ईमेल कभी नहीं।")) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rust, focusedLabelColor = RustText, cursorColor = Rust))
            }
            item {
                OutlinedTextField(value = text, onValueChange = onText, modifier = Modifier.fillMaxWidth().testTag("community-post-field"), minLines = 6, textStyle = communityBody(),
                    label = { Text(t("Your post", "आपकी पोस्ट")) },
                    placeholder = { Text(t("Share how you are, or ask what helped others.", "बताएँ आप कैसे हैं, या पूछें कि दूसरों को किस बात से मदद मिली।")) },
                    supportingText = { Text("${text.trim().length} / ${CommunityRules.POST_MAX}") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rust, focusedLabelColor = RustText, cursorColor = Rust))
            }
        }
        CommunityPrimaryButton(t("Share post", "पोस्ट भेजें"), onClick = onPost, busy = sending, icon = Icons.AutoMirrored.Filled.Send,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("community-post-send"))
    }
}

@Composable private fun CommunityReportDialog(kind: String, onDismiss: () -> Unit, onReport: (String) -> Unit) {
    val t = careText()
    var reason by rememberSaveable { mutableStateOf(CommunityRules.reportReasons.first().first) }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Flag, null) },
        title = { Text(if (kind == "post") t("Report this post", "इस पोस्ट की रिपोर्ट करें") else t("Report this reply", "इस जवाब की रिपोर्ट करें")) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(t("The Sahara team will look at it. The writer isn't told who reported.", "Sahara टीम इसे देखेगी। लिखने वाले को नहीं बताया जाएगा कि रिपोर्ट किसने की।"), style = communityBody(), color = DoctorCardSub)
                Spacer(Modifier.height(8.dp))
                CommunityRules.reportReasons.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = reason == key, onClick = { reason = key }, role = Role.RadioButton).testTag("community-report-$key"),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == key, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Rust))
                        Spacer(Modifier.width(8.dp))
                        Text(t(label.en, label.hi), style = communityBody(), color = Ink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onReport(reason) }, modifier = Modifier.heightIn(min = 48.dp).testTag("community-report-send")) { Text(t("Send report", "रिपोर्ट भेजें"), color = RustText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), color = DoctorCardSub) } },
        modifier = Modifier.testTag("community-report-dialog"))
}

/** Results and errors that matter get a short dialog, closed in one tap (owner, 6 Nov: fewer toasts, more dialogs). */
@Composable private fun CommunityMessageDialog(copy: CommunityCopy, onDismiss: () -> Unit) {
    val t = careText()
    AlertDialog(onDismissRequest = onDismiss,
        text = { Text(t(copy.en, copy.hi), style = communityBody(), color = Ink) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close", "बंद करें"), color = RustText, fontWeight = FontWeight.SemiBold) } },
        modifier = Modifier.testTag("community-message-dialog"))
}
