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
/** Each group keeps one Sahara section colour everywhere (design lane tokens). The server stores the key. */
private fun communityTone(key: String): SaharaTone = when (key) {
    "teal" -> SaharaTone.Teal; "rose" -> SaharaTone.Rose; "plum" -> SaharaTone.Plum; "sky" -> SaharaTone.Indigo
    "leaf" -> SaharaTone.Leaf; "marigold" -> SaharaTone.Marigold; "gold" -> SaharaTone.Gold; else -> SaharaTone.Rust
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
    SaharaScreenHost(route, Modifier.fillMaxSize().background(SaharaColors.Ground).testTag("community-screen"), label = "community pages",
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

    message?.let { copy -> SaharaDialog(t(copy.en, copy.hi), onDismiss = { message = null }, tone = SaharaTone.Info, confirmLabel = t("Close", "बंद करें"), modifier = Modifier.testTag("community-message-dialog")) }
    if (offlineSaved) SaharaDialog(t("You're offline", "आप ऑफ़लाइन हैं"), onDismiss = { offlineSaved = false },
        text = t("Your words are saved on this phone. They will be sent when you're online and open Community.", "आपके शब्द इस फ़ोन में सुरक्षित हैं। इंटरनेट आने पर समुदाय खोलते ही भेज दिए जाएँगे।"),
        tone = SaharaTone.Warning, icon = Icons.Default.CloudOff, confirmLabel = t("Close", "बंद करें"), modifier = Modifier.testTag("community-offline-dialog"))
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
        SaharaConfirmDialog(if (kind == "post") t("Delete your post?", "अपनी पोस्ट हटाएँ?") else t("Delete your reply?", "अपना जवाब हटाएँ?"),
            confirmLabel = t("Delete", "हटाएँ"), dismissLabel = t("Keep", "रहने दें"), danger = true,
            text = if (queued) t("It hasn't been sent yet. It will be removed from this phone.", "यह अभी भेजा नहीं गया है। इसे इस फ़ोन से हटा दिया जाएगा।")
                else t("It will be removed for everyone. This can't be undone.", "यह सबके लिए हट जाएगा। इसे वापस नहीं लाया जा सकता।"),
            onConfirm = {
                removeTarget = null
                if (queued) { store.remove(id); pending = accountId?.let(store::pending).orEmpty() }
                else call({ it.communityRemoveMine(kind, id) }, onSuccess = { loadFeed(groupId) })
            },
            onDismiss = { removeTarget = null })
    }
}

/** Back arrow and the screen title, like a top app bar. */
@Composable private fun CommunityTitleRow(title: String, onBack: () -> Unit, tone: SaharaTone? = null) {
    val t = careText()
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("community-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = SaharaColors.Ink) }
        Spacer(Modifier.width(4.dp))
        if (tone != null) { Box(Modifier.size(width = 4.dp, height = 24.dp).clip(CircleShape).background(tone.solid)); Spacer(Modifier.width(8.dp)) }
        Text(title, Modifier.weight(1f).a11yHeading(), style = communityTitle(), color = SaharaColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The pinned house rule: short and gentle, at the top of every group. */
@Composable private fun CommunityGuideline() {
    val t = careText()
    Surface(shape = RoundedCornerShape(12.dp), color = SaharaTone.Gold.wash, border = BorderStroke(1.dp, SaharaTone.Gold.ink.copy(alpha = .45f)), modifier = Modifier.fillMaxWidth().testTag("community-guideline")) {
        Row(Modifier.padding(12.dp).a11yCard(), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.VolunteerActivism, null, Modifier.size(24.dp), tint = SaharaTone.Gold.ink)
            Spacer(Modifier.width(12.dp))
            Text(t("Be kind. This isn't medical advice. For symptoms, talk to your care team.", "प्यार से बात करें। यह डॉक्टरी सलाह नहीं है। तकलीफ़ हो तो अपनी देखभाल टीम से बात करें।"),
                style = communityBody(), color = SaharaColors.Ink)
        }
    }
}

@Composable private fun CommunityNotice(copy: CommunityCopy, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val t = careText()
    Surface(shape = RoundedCornerShape(12.dp), color = SaharaColors.Surface, border = BorderStroke(1.dp, SaharaColors.Line), modifier = modifier.fillMaxWidth().testTag("community-notice")) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).announce(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(24.dp), tint = SaharaColors.InkSub)
            Spacer(Modifier.width(12.dp))
            Text(t(copy.en, copy.hi), Modifier.weight(1f).padding(vertical = 8.dp), style = communityBody(), color = SaharaColors.Ink)
            if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Try again", "फिर कोशिश करें"), color = SaharaColors.PrimaryPressed, fontWeight = FontWeight.SemiBold, softWrap = false) }
        }
    }
}

@Composable private fun CommunityHomePage(signedIn: Boolean, home: CommunityHome?, error: CommunityCopy?, loading: Boolean, waiting: Int,
    onBack: () -> Unit, onAccount: () -> Unit, onRetry: () -> Unit, onOpen: (String) -> Unit) {
    val t = careText()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { CommunityTitleRow(t("Community", "समुदाय"), onBack) }
        if (!signedIn) item {
            Surface(shape = RoundedCornerShape(12.dp), color = SaharaColors.Surface, border = BorderStroke(1.dp, SaharaColors.Line), modifier = Modifier.fillMaxWidth().saharaAppear().testTag("community-sign-in")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SaharaTone.Teal.solid), contentAlignment = Alignment.Center) { Icon(Icons.Default.Forum, null, Modifier.size(24.dp), tint = SaharaTone.Teal.onSolid) }
                    Text(t("Talk with families and patients who understand", "उन परिवारों और मरीज़ों से बात करें जो समझते हैं"), style = communityTitle(), color = SaharaColors.Ink)
                    Text(t("Only signed-in members can read and write posts.", "पोस्ट सिर्फ़ साइन इन किए सदस्य पढ़ और लिख सकते हैं।"), style = communityBody(), color = SaharaColors.InkSub)
                    SaharaPrimaryButton(t("Sign in to join", "जुड़ने के लिए साइन इन करें"), onClick = onAccount, modifier = Modifier.fillMaxWidth().testTag("community-sign-in-button"))
                }
            }
        }
        else {
            if (waiting > 0) item {
                CommunityNotice(if (waiting == 1) CommunityCopy("1 post or reply is waiting to send.", "1 पोस्ट या जवाब भेजा जाना बाकी है।")
                    else CommunityCopy("$waiting posts or replies are waiting to send.", "$waiting पोस्ट या जवाब भेजे जाने बाकी हैं।"), onRetry)
            }
            if (error != null) item { CommunityNotice(error, onRetry) }
            if (home == null && error == null && loading) item { SaharaInlineLoading(t("Loading…", "खुल रहा है…")) }
            home?.groups?.let { groups ->
                item { Text(t("Groups", "समूह"), Modifier.a11yHeading().padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink) }
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
    val tone = communityTone(group.tone)
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.saharaAppear().saharaPress(interaction).heightIn(min = 140.dp).clip(RoundedCornerShape(SaharaRadius.Lg)).background(tone.wash)
        .border(1.dp, tone.solid.copy(alpha = .45f), RoundedCornerShape(SaharaRadius.Lg))
        .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick).a11yCard().padding(16.dp).testTag("community-group-${group.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tone.solid), contentAlignment = Alignment.Center) { Icon(communityIcon(group.id), null, Modifier.size(24.dp), tint = tone.onSolid) }
        Text(t(group.nameEn, group.nameHi), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, lineBreak = LineBreak.Heading, hyphens = Hyphens.None), fontWeight = FontWeight.SemiBold, color = tone.ink)
        if (group.aboutEn.isNotBlank()) Text(t(group.aboutEn, group.aboutHi.ifBlank { group.aboutEn }), style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None), color = SaharaColors.InkSub)
        Spacer(Modifier.weight(1f))
        Text(if (group.posts == 1) t("1 post", "1 पोस्ट") else t("${group.posts} posts", "${group.posts} पोस्ट"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tone.ink)
    }
}

@Composable private fun CommunityFeedPage(group: CommunityGroup?, groupId: String, feed: CommunityFeed?, error: CommunityCopy?, loading: Boolean, pending: List<CommunityPending>,
    myName: String?, canModerate: Boolean, justSent: String?, sending: Boolean, onBack: () -> Unit, onRetry: () -> Unit, onWrite: () -> Unit,
    onReact: (CommunityPost, String) -> Unit, onReply: (String, String, () -> Unit) -> Unit, onReport: (String, String) -> Unit,
    onRemove: (String, String) -> Unit, onModerate: (String, String, Boolean) -> Unit) {
    val t = careText()
    val tone = communityTone(group?.tone ?: "rust")
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
            if (feed == null && error == null && loading) item { SaharaInlineLoading(t("Loading…", "खुल रहा है…")) }
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
            containerColor = SaharaColors.Primary, contentColor = SaharaColors.OnPrimary, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 56.dp).testTag("community-write"))
    }
}

/** One status per post at most: hidden, waiting, just posted, Sahara team, open reports (team only), or reported by you. */
@Composable private fun CommunityStatus(hidden: Boolean, queued: Boolean, justSent: Boolean, byTeam: Boolean, reports: Int, reported: Boolean, canModerate: Boolean) {
    val t = careText()
    when {
        hidden -> SaharaStatusPill(t("Hidden", "छिपी हुई"), SaharaTone.Warning, icon = Icons.Default.VisibilityOff)
        queued -> SaharaStatusPill(t("Waiting to send", "भेजना बाकी"), SaharaTone.Warning, icon = Icons.Default.CloudOff)
        justSent -> {
            // The sent tick: pops in once, then the pill stays for a moment.
            val shown = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(shown, enter = fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS), initialScale = .6f)) {
                SaharaStatusPill(t("Posted", "भेज दिया"), SaharaTone.Success, Modifier.testTag("community-sent-tick"), icon = Icons.Default.CheckCircle)
            }
        }
        byTeam -> SaharaStatusPill(t("Sahara team", "Sahara टीम"), SaharaTone.Indigo)
        canModerate && reports > 0 -> SaharaStatusPill(if (reports == 1) t("1 report", "1 रिपोर्ट") else t("$reports reports", "$reports रिपोर्ट"), SaharaTone.Error, icon = Icons.Default.Flag)
        reported -> SaharaStatusPill(t("Reported", "रिपोर्ट की"), SaharaTone.Neutral)
    }
}


@Composable private fun CommunityAvatar(name: String, tone: SaharaTone, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(tone.wash), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tone.ink)
    }
}

@Composable private fun CommunityAction(text: String, icon: ImageVector, onClick: () -> Unit, selected: Boolean = false, tone: SaharaTone = SaharaTone.Rust, tag: String = "") {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        border = BorderStroke(1.dp, if (selected) tone.solid else SaharaColors.Edge),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) tone.wash else SaharaColors.Surface, contentColor = if (selected) tone.ink else SaharaColors.Ink),
        modifier = Modifier.heightIn(min = 48.dp).then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

@Composable private fun CommunityPostCard(post: CommunityPost, now: Long, tone: SaharaTone, queued: Boolean, queuedIds: Set<String>, justSent: Boolean, canModerate: Boolean,
    replying: Boolean, replyText: String, replyError: CommunityCopy?, sending: Boolean, onReact: (String) -> Unit, onReplyOpen: () -> Unit, onReplyText: (String) -> Unit,
    onReplySend: () -> Unit, onReport: (String, String) -> Unit, onRemove: (String, String) -> Unit, onModerate: (String, String, Boolean) -> Unit) {
    val t = careText()
    Surface(shape = RoundedCornerShape(12.dp), color = SaharaColors.Surface, border = BorderStroke(1.dp, SaharaColors.Line), modifier = Modifier.fillMaxWidth().saharaAppear().saharaContentSize().testTag("community-post")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.a11yCard(), verticalAlignment = Alignment.CenterVertically) {
                CommunityAvatar(post.name, tone)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.name, style = MaterialTheme.typography.titleSmall, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val ago = CommunityRules.ago(now, post.createdMs)
                    Text(t(ago.en, ago.hi), style = MaterialTheme.typography.bodySmall, fontSize = 14.sp, color = SaharaColors.InkSub)
                }
                CommunityStatus(post.hidden, queued, justSent, post.byTeam, post.reports, post.reported, canModerate)
            }
            Text(post.body, style = communityBody(), color = SaharaColors.Ink)
            if (!queued && !post.hidden) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunityAction(if (post.helpful > 0) t("Helpful ${post.helpful}", "मददगार ${post.helpful}") else t("Helpful", "मददगार"), Icons.Default.ThumbUp, { onReact("helpful") }, post.myHelpful, SaharaTone.Teal, "community-helpful")
                CommunityAction(if (post.hugs > 0) t("Hugs ${post.hugs}", "झप्पी ${post.hugs}") else t("Hugs", "झप्पी"), Icons.Default.Favorite, { onReact("hugs") }, post.myHugs, SaharaTone.Rose, "community-hugs")
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
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaharaColors.Primary, focusedLabelColor = SaharaColors.PrimaryPressed, cursorColor = SaharaColors.Primary))
                    SaharaPrimaryButton(t("Send reply", "जवाब भेजें"), onClick = onReplySend, busy = sending, icon = Icons.AutoMirrored.Filled.Send, modifier = Modifier.fillMaxWidth().testTag("community-reply-send"))
                }
            }
        }
    }
}

@Composable private fun CommunityReplyRow(reply: CommunityReply, now: Long, tone: SaharaTone, queued: Boolean, canModerate: Boolean,
    onReport: (String, String) -> Unit, onRemove: (String, String) -> Unit, onModerate: (String, String, Boolean) -> Unit) {
    val t = careText()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).saharaAppear().testTag("community-reply-row")) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(tone.solid.copy(alpha = .45f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.a11yCard(), verticalAlignment = Alignment.CenterVertically) {
                val ago = CommunityRules.ago(now, reply.createdMs)
                Text(reply.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("  ·  " + t(ago.en, ago.hi), style = MaterialTheme.typography.bodySmall, fontSize = 14.sp, color = SaharaColors.InkSub, maxLines = 1, softWrap = false)
                Spacer(Modifier.weight(1f))
                CommunityStatus(reply.hidden, queued, false, reply.byTeam, reply.reports, reply.reported, canModerate)
            }
            Text(reply.body, style = communityBody(), color = SaharaColors.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reply.mine) TextButton(onClick = { onRemove("reply", reply.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Delete", "हटाएँ"), color = SaharaColors.PrimaryPressed) }
                else if (!reply.reported && !reply.hidden) TextButton(onClick = { onReport("reply", reply.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Report", "रिपोर्ट करें"), color = SaharaColors.PrimaryPressed) }
                if (canModerate && !queued) TextButton(onClick = { onModerate("reply", reply.id, !reply.hidden) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (reply.hidden) t("Show again", "फिर दिखाएँ") else t("Hide", "छिपाएँ"), color = SaharaColors.PrimaryPressed)
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
                        val tone = communityTone(group.tone); val selected = group.id == groupId
                        Row(Modifier.clip(RoundedCornerShape(50)).background(if (selected) tone.solid else SaharaColors.Surface).border(1.5.dp, tone.solid, RoundedCornerShape(50))
                            .selectable(selected = selected, onClick = { onGroup(group.id) }, role = Role.RadioButton).heightIn(min = 48.dp).padding(horizontal = 16.dp).testTag("community-write-group-${group.id}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (selected) { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = tone.onSolid); Spacer(Modifier.width(6.dp)) }
                            Text(t(group.nameEn, group.nameHi), style = MaterialTheme.typography.labelLarge, fontSize = 15.sp, color = if (selected) tone.onSolid else tone.ink, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            if (needsName) item {
                OutlinedTextField(value = name, onValueChange = onName, modifier = Modifier.fillMaxWidth().testTag("community-name-field"), singleLine = true,
                    label = { Text(t("Your name in the community", "समुदाय में आपका नाम")) },
                    supportingText = { Text(t("Others see only this name, never your phone or email.", "दूसरों को सिर्फ़ यही नाम दिखेगा, आपका फ़ोन या ईमेल कभी नहीं।")) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaharaColors.Primary, focusedLabelColor = SaharaColors.PrimaryPressed, cursorColor = SaharaColors.Primary))
            }
            item {
                OutlinedTextField(value = text, onValueChange = onText, modifier = Modifier.fillMaxWidth().testTag("community-post-field"), minLines = 6, textStyle = communityBody(),
                    label = { Text(t("Your post", "आपकी पोस्ट")) },
                    placeholder = { Text(t("Share how you are, or ask what helped others.", "बताएँ आप कैसे हैं, या पूछें कि दूसरों को किस बात से मदद मिली।")) },
                    supportingText = { Text("${text.trim().length} / ${CommunityRules.POST_MAX}") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaharaColors.Primary, focusedLabelColor = SaharaColors.PrimaryPressed, cursorColor = SaharaColors.Primary))
            }
        }
        SaharaPrimaryButton(t("Share post", "पोस्ट भेजें"), onClick = onPost, busy = sending, icon = Icons.AutoMirrored.Filled.Send,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("community-post-send"))
    }
}

@Composable private fun CommunityReportDialog(kind: String, onDismiss: () -> Unit, onReport: (String) -> Unit) {
    val t = careText()
    var reason by rememberSaveable { mutableStateOf(CommunityRules.reportReasons.first().first) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = SaharaColors.Surface, shape = RoundedCornerShape(SaharaRadius.Hero), icon = { SaharaIconChip(Icons.Default.Flag, SaharaTone.Rust, size = 48) },
        title = { Text(if (kind == "post") t("Report this post", "इस पोस्ट की रिपोर्ट करें") else t("Report this reply", "इस जवाब की रिपोर्ट करें")) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(t("The Sahara team will look at it. The writer isn't told who reported.", "Sahara टीम इसे देखेगी। लिखने वाले को नहीं बताया जाएगा कि रिपोर्ट किसने की।"), style = communityBody(), color = SaharaColors.InkSub)
                Spacer(Modifier.height(8.dp))
                CommunityRules.reportReasons.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = reason == key, onClick = { reason = key }, role = Role.RadioButton).testTag("community-report-$key"),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == key, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = SaharaColors.Primary))
                        Spacer(Modifier.width(8.dp))
                        Text(t(label.en, label.hi), style = communityBody(), color = SaharaColors.Ink)
                    }
                }
            }
        },
        confirmButton = { SaharaPrimaryButton(t("Send report", "रिपोर्ट भेजें"), onClick = { onReport(reason) }, modifier = Modifier.testTag("community-report-send")) },
        dismissButton = { SaharaSecondaryButton(t("Cancel", "रद्द करें"), onClick = onDismiss, tone = SaharaTone.Neutral) },
        modifier = Modifier.testTag("community-report-dialog"))
}
