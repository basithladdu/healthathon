@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** A bundled 3D model plus visible region buttons, with a 2D fallback if this WebView cannot render it. Part buttons only zoom; a tap on the model (or "Mark whole part") records the pain. */
@Composable fun CarePainBodyMap(selected: List<String>, points: List<PainBodyPoint3d>, rating: Int?, hindi: Boolean, readOnly: Boolean = false, ratings: Map<String, Int> = emptyMap(), onSpotRating: ((String, Int) -> Unit)? = null, onChange: (List<String>, List<PainBodyPoint3d>) -> Unit) {
    var back by rememberSaveable { mutableStateOf(false) }
    var focus by rememberSaveable { mutableStateOf<String?>(null) }
    var focusNonce by rememberSaveable { mutableIntStateOf(0) }
    val t = { en: String, hi: String -> if (hindi) hi else en }
    // Each marked spot keeps its own rating. Old notes without per-spot ratings fall back to the note rating (read-only history only).
    var active by rememberSaveable { mutableStateOf<String?>(null) }
    val activeSite = active?.takeIf { it in selected } ?: selected.lastOrNull()
    val fallback = if (readOnly && ratings.isEmpty()) rating else null
    val spotRating = { id: String -> ratings[id] ?: fallback }
    val spotColor = { id: String -> spotRating(id)?.let { Color(PainBodyMapRules.heatColor(it)) } ?: Rust }
    val viewerRatings = selected.mapNotNull { id -> spotRating(id)?.let { id to it } }.toMap()
    // The newest tapped or marked spot becomes the active one (it has no rating until the slider moves).
    val change: (List<String>, List<PainBodyPoint3d>) -> Unit = { sites, hits ->
        (sites.firstOrNull { it !in selected } ?: hits.firstOrNull { it !in points }?.site)?.let { active = it }
        onChange(sites, hits)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (readOnly) t("Pick a part to zoom in. Saved locations stay unchanged. Left and right mean the patient's sides.", "ज़ूम करने के लिए हिस्सा चुनें। सहेजी हुई जगह नहीं बदलेगी। बायाँ और दायाँ मरीज़ की तरफ़ से हैं।") else t("Pick a part to zoom in, then tap the model exactly where it hurts. Left and right mean the patient's sides.", "ज़ूम करने के लिए हिस्सा चुनें, फिर मॉडल पर ठीक वहाँ छुएँ जहाँ दर्द है। बायाँ और दायाँ मरीज़ की तरफ़ से हैं।"), color = DoctorCardSub, fontSize = 14.sp, lineHeight = 19.sp)
        LocalPainBodyViewer(selected, points, viewerRatings, hindi, back, readOnly, focus, focusNonce, change)
        if (!readOnly && onSpotRating != null && activeSite != null) Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Box(Modifier.padding(14.dp)) { PainStrengthSlider(ratings[activeSite], PainBodyMapRules.label(activeSite, hindi), hindi) { onSpotRating(activeSite, it) } } }
        Text(if (selected.any { spotRating(it) != null }) "${t("Pain by spot", "जगह के अनुसार दर्द")}: ${selected.joinToString(" · ") { id -> "${PainBodyMapRules.label(id, hindi)} ${spotRating(id)?.let { "$it/10" } ?: t("not rated", "अंक नहीं")}" }}" else if (selected.isEmpty() && rating != null) "${t("Pain", "दर्द")}: $rating/10" else t("Not rated — no pain intensity shown", "अंक नहीं बताया — दर्द की तीव्रता नहीं दिखाई गई"), color = DoctorCardSub, fontSize = 13.sp, modifier = Modifier.testTag("pain-spot-summary"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (readOnly) t("Saved pain areas", "सहेजी गई दर्द की जगह") else t("Where is the pain? (optional)", "दर्द कहाँ है? (वैकल्पिक)"), color = DoctorCardInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!readOnly && selected.isNotEmpty()) TextButton(onClick = { active = null; onChange(emptyList(), emptyList()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-clear")) { Text(t("Clear", "हटाएँ"), color = Rust, fontWeight = FontWeight.SemiBold) }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x14382E29)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PainSideOption(!back, t("Front", "सामने"), Icons.Default.AccessibilityNew, "pain-body-front", Modifier.weight(1f)) { if (back) { back = false; focus = null; focusNonce++ } }
            PainSideOption(back, t("Back", "पीछे"), Icons.Default.RotateRight, "pain-body-back", Modifier.weight(1f)) { if (!back) { back = true; focus = null; focusNonce++ } }
        }
        val tiles = listOf<String?>(null) + PainBodyMapRules.sitesFor(back)
        val perRow = if (LocalDensity.current.fontScale > 1.15f) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { id ->
                        if (id == null) PainPartTile(focus == null, false, t("Full body", "पूरा शरीर"), t("Full body, zoom out", "पूरा शरीर, ज़ूम आउट"), Rust, "pain-body-focus-full", Icons.Default.Person, t("Marked", "चिह्नित"), Modifier.weight(1f).fillMaxHeight()) { focus = null; focusNonce++ }
                        else {
                            val label = PainBodyMapRules.focusLabel(id, back, hindi); val marked = id in selected
                            PainPartTile(focus == id, marked, label, "${t("Zoom to", "ज़ूम करें:")} $label${if (marked) t(", marked as pain", ", दर्द के रूप में चिह्नित") else ""}", spotColor(id), if (readOnly) "pain-body-focus-$id" else "pain-body-site-$id", null, t("Marked", "चिह्नित"), Modifier.weight(1f).fillMaxHeight()) { focus = id; focusNonce++; if (id in selected) active = id }
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val zoomed = focus
        if (!readOnly && zoomed != null && zoomed in PainBodyMapRules.sites) {
            val part = PainBodyMapRules.focusLabel(zoomed, back, hindi)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (zoomed in selected) {
                    Text("$part · ${t("Marked as pain", "दर्द चिह्नित")}", color = DoctorCardInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterVertically))
                    TextButton(onClick = { val next = PainBodyMapRules.unmarked(selected, zoomed); change(next, points.filter { it.site in next }) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-unmark-$zoomed")) { Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = Rust); Spacer(Modifier.width(4.dp)); Text(t("Remove", "हटाएँ"), color = Rust, fontWeight = FontWeight.SemiBold) }
                } else {
                    Text(t("Tap the model where it hurts, or", "मॉडल पर जहाँ दर्द है वहाँ छुएँ, या"), color = DoctorCardSub, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    TextButton(onClick = { val next = PainBodyMapRules.marked(selected, zoomed); change(next, points.filter { it.site in next }) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-mark-$zoomed")) { Text(t("Mark whole $part", "पूरा $part चिह्नित करें"), color = Rust, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        if (selected.isNotEmpty()) Text(selected.joinToString { PainBodyMapRules.label(it, hindi) }, color = DoctorCardInk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("pain-body-selected"))
    }
}

/** One half of the Front/Back segmented toggle. */
@Composable private fun PainSideOption(on: Boolean, label: String, icon: ImageVector, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Rust else Color.Transparent).selectable(on, onClick = onClick, role = Role.RadioButton).padding(horizontal = 12.dp).testTag(tag), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) Color.White else DoctorCardInk); Spacer(Modifier.width(8.dp)); Text(label, color = if (on) Color.White else DoctorCardInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A body-part tile: outlined by default, filled accent when zoomed, with a pain-colour dot and "Marked" label when recorded as pain. */
@Composable private fun PainPartTile(zoomed: Boolean, marked: Boolean, label: String, description: String, markColor: Color, tag: String, icon: ImageVector?, markedWord: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp); val ink = if (zoomed) Color.White else DoctorCardInk
    Box(modifier.heightIn(min = 56.dp).clip(shape).background(if (zoomed) Rust else Paper).border(if (zoomed) 2.dp else 1.5.dp, if (zoomed) Rust else Color(0x66382E29), shape)
        .selectable(zoomed, onClick = onClick, role = Role.RadioButton).semantics(mergeDescendants = true) { contentDescription = description }.testTag(tag).padding(horizontal = 6.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = ink)
            Text(label, color = ink, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (marked) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(10.dp).background(markColor, CircleShape).border(1.5.dp, Color.White, CircleShape)); Text(markedWord, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/** Wong-Baker style pain strength slider: six solid green-to-red blocks. Untouched means no rating: the model stays grey and nothing is stored until the user moves it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PainStrengthSlider(rating: Int?, target: String, hindi: Boolean, onRatingChange: (Int) -> Unit) {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val stops = listOf(Color(PainBodyMapRules.NEUTRAL).copy(alpha = 0.55f)) + PainBodyMapRules.heatStops.map { Color(it) }; val blockWeights = listOf(0.5f, 1f, 2f, 2f, 2f, 2f, 0.5f) // 0 is a neutral cap (no colour); green starts at 1 // block edges sit between integers so the thumb always lies over its own block
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow { Text(t("How strong is the pain?", "दर्द कितना तेज़ है?"), fontWeight = FontWeight.SemiBold); Text(" · $target", fontWeight = FontWeight.SemiBold, color = Rust, modifier = Modifier.testTag("pain-strength-target")) }
        Text(if (rating == null) t("Slide to set. Not rated yet.", "अंक चुनने के लिए खिसकाएँ। अभी अंक नहीं बताया।") else "$rating/10 · ${PainBodyMapRules.strengthWord(rating, hindi)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (rating == null) MaterialTheme.colorScheme.onSurfaceVariant else DoctorCardInk, modifier = Modifier.testTag("pain-strength-value"))
        Slider(value = (rating ?: 0).toFloat(), onValueChange = { onRatingChange(it.roundToInt().coerceIn(0, 10)) }, valueRange = 0f..10f, steps = 9,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pain-strength-slider").semantics { contentDescription = t("Pain strength for $target, from 0 none to 10 red", "$target में दर्द की तीव्रता, 0 कोई नहीं से 10 लाल"); stateDescription = if (rating == null) t("Not rated", "अंक नहीं बताया") else "$rating, ${PainBodyMapRules.strengthWord(rating, hindi)}" },
            track = { Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) { stops.forEachIndexed { i, c -> Box(Modifier.weight(blockWeights[i]).fillMaxHeight().background(c)) } } },
            thumb = { Box(Modifier.size(28.dp).background(if (rating == null) Color.Gray else Color(PainBodyMapRules.heatColor(rating)), CircleShape).border(3.dp, Color.White, CircleShape).border(1.dp, Color.DarkGray, CircleShape)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0 ${PainBodyMapRules.strengthWord(0, hindi)}", style = MaterialTheme.typography.labelSmall); Text("10 ${PainBodyMapRules.strengthWord(10, hindi)}", style = MaterialTheme.typography.labelSmall) }
    }
}

/** The loaded 3D page plus the callbacks of whichever screen currently shows it. */
private class PainViewerHost(val view: WebView, val activity: android.app.Activity?) {
    var ready = false
    var dead = false
    var reused = false
    var onReady: (() -> Unit)? = null
    var onFailed: (() -> Unit)? = null
    var onMessage: ((String?, String) -> Unit)? = null
    fun fail() { dead = true; onFailed?.invoke() }
}

/**
 * One 3D viewer kept per activity. Leaving "Where does it hurt?" (Next, Back, closing a history sheet) only detaches it,
 * so the next visit shows the already loaded model instead of loading three.js and the body model again.
 * A second viewer open at the same time gets its own WebView, destroyed as before. The kept one is destroyed with the activity or under memory pressure.
 */
private object PainViewerHosts {
    private var kept: PainViewerHost? = null
    private var inUse = false
    private val live = mutableListOf<PainViewerHost>()
    private var watched: android.app.Activity? = null
    private var memoryWatch = false

    fun ownerOf(view: WebView): PainViewerHost? = live.firstOrNull { it.view === view }

    fun acquire(context: android.content.Context): PainViewerHost {
        val activity = context.findActivity()
        kept?.let { host ->
            if (!inUse && !host.dead && host.activity === activity) { inUse = true; host.reused = true; return host }
            if (!inUse) drop()
        }
        val host = PainViewerHost(createPainViewer(context), activity).also { live += it }
        if (kept == null && activity != null) { kept = host; inUse = true; watch(activity) }
        return host
    }

    fun release(host: PainViewerHost) {
        host.onReady = null; host.onFailed = null; host.onMessage = null
        if (host === kept && !host.dead) { inUse = false; host.view.onPause(); (host.view.parent as? android.view.ViewGroup)?.removeView(host.view) }
        else { if (host === kept) { inUse = false; kept = null }; destroy(host.view) }
    }

    private fun watch(activity: android.app.Activity) {
        if (!memoryWatch) {
            memoryWatch = true
            activity.application.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) { if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && !inUse) drop() }
                override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit
                @Deprecated("Deprecated in Java") override fun onLowMemory() { if (!inUse) drop() }
            })
        }
        if (watched === activity) return
        watched = activity
        (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                if (kept?.activity === activity) { inUse = false; drop() }
                if (watched === activity) watched = null
            }
        })
    }

    private fun drop() { kept?.let { destroy(it.view) }; kept = null }

    /** Safe to call twice: only a live view is destroyed. */
    private fun destroy(view: WebView) {
        if (!live.removeAll { it.view === view }) return
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "SaharaPainNative")
        view.stopLoading(); view.destroy()
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Builds the locked-down viewer page. Messages and failures go to whichever [PainViewerHost] owns the view at that moment. */
@SuppressLint("SetJavaScriptEnabled")
private fun createPainViewer(context: android.content.Context): WebView =
    WebView(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        val assetLoader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
        webViewClient = object : WebViewClientCompat() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.toString() != PainViewerPolicy.PAGE
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                if (request.method == "GET" && PainViewerPolicy.localResource(request.url.toString())) {
                    assetLoader.shouldInterceptRequest(request.url)?.let { response ->
                        response.responseHeaders = mapOf("Cache-Control" to "no-store")
                        return response
                    }
                }
                return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: androidx.webkit.WebResourceErrorCompat) { if (request.isForMainFrame) view.post { PainViewerHosts.ownerOf(view)?.fail() } }
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean { PainViewerHosts.ownerOf(view)?.fail(); return true }
        }
        // Up/down drags stay with the page so it scrolls; the model keeps only sideways drags (turn) and two-finger pinches (zoom). A tap still reaches the page.
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var downX = 0f; var downY = 0f; var claimed = false
        setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; claimed = false }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> if (!claimed && PainViewerPolicy.viewerKeepsGesture(event.x - downX, event.y - downY, event.pointerCount, slop)) {
                    claimed = true; touched.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { claimed = false; touched.parent?.requestDisallowInterceptTouchEvent(false) }
            }
            false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this, "SaharaPainNative", setOf(PainViewerPolicy.ORIGIN)) { view, message, origin, mainFrame, _ ->
                if (mainFrame && PainViewerPolicy.trustedOrigin(origin.toString())) {
                    val data = message.data.orEmpty()
                    if (data.length <= 180) {
                        val type = runCatching { JSONObject(data).optString("type") }.getOrNull()
                        view.post { PainViewerHosts.ownerOf(view)?.let { owner ->
                            when (type) {
                                "ready" -> { owner.ready = true; owner.onReady?.invoke() }
                                "error" -> owner.fail()
                                else -> owner.onMessage?.invoke(type, data)
                            }
                        } }
                    }
                }
            }
            loadUrl(PainViewerPolicy.PAGE)
        }
    }

@Composable private fun LocalPainBodyViewer(selected: List<String>, points: List<PainBodyPoint3d>, ratings: Map<String, Int>, hindi: Boolean, back: Boolean, readOnly: Boolean, focus: String?, focusNonce: Int, onChange: (List<String>, List<PainBodyPoint3d>) -> Unit) {
    val context = LocalContext.current
    val host = remember { PainViewerHosts.acquire(context) }
    var ready by remember { mutableStateOf(host.ready) }
    var failed by remember { mutableStateOf(host.dead || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) }
    val latestSelected by rememberUpdatedState(selected)
    val latestPoints by rememberUpdatedState(points)
    val latestOnChange by rememberUpdatedState(onChange)
    val latestReadOnly by rememberUpdatedState(readOnly)
    DisposableEffect(host) {
        host.onReady = { ready = true }
        host.onFailed = { failed = true }
        if (host.ready) ready = true
        if (host.dead) failed = true
        host.onMessage = { type, data ->
            when (type) {
                "toggle" -> if (!latestReadOnly) PainViewerPolicy.toggledSite(data)?.let { site ->
                    val next = PainBodyMapRules.toggled(latestSelected, site)
                    latestOnChange(next, latestPoints.filter { it.site in next })
                }
                "pick" -> if (!latestReadOnly) PainViewerPolicy.pickedPoint(data)?.let { point ->
                    val next = PainBodyMapRules.sites.keys.filter { it in latestSelected || it == point.site }
                    val markers = (latestPoints.filter { it.site != point.site } + point).sortedBy { it.site }
                    latestOnChange(next, markers)
                }
            }
        }
        onDispose { PainViewerHosts.release(host) }
    }
    val webView = host.view
    val state = JSONObject().put("selected", JSONArray(selected.filter { it in PainBodyMapRules.sites })).put("rating", JSONObject.NULL).put("ratings", PainBodyMapRules.encodeRatings(ratings.filterValues { it in 0..10 })).put("hindi", hindi).put("readOnly", readOnly).put("points", PainBodyMapRules.encodePoints(points)).toString()
    LaunchedEffect(ready, state) {
        if (ready) webView.evaluateJavascript("window.SaharaPainViewer?.setState($state);", null)
    }
    LaunchedEffect(ready, back) {
        if (ready) webView.evaluateJavascript("window.SaharaPainViewer?.setView('${if (back) "back" else "front"}');", null)
    }
    LaunchedEffect(ready, focus, focusNonce) {
        if (!ready) return@LaunchedEffect
        // The id is one of PainBodyMapRules.sites keys, so it is safe to place inside the script string.
        if (focus != null && focus in PainBodyMapRules.sites) webView.evaluateJavascript("window.SaharaPainViewer?.focusRegion('$focus','${if (back) "back" else "front"}');", null)
        // A kept viewer may still be zoomed from its last screen; start this one on the full body like a fresh page.
        else if (focusNonce > 0 || host.reused) webView.evaluateJavascript("window.SaharaPainViewer?.focusFull();", null)
    }
    if (failed) {
        Text(if (readOnly) { if (hindi) "इस फ़ोन पर 3D नहीं खुला। सहेजे हुए हिस्से नीचे नक्शे पर हैं।" else "3D could not open on this phone. Saved areas are shown on the diagram." } else if (hindi) "इस फ़ोन पर 3D नहीं खुला। नीचे नक्शे या बटन से जगह चुनें।" else "3D could not open on this phone. Choose on the map or the buttons below.", style = MaterialTheme.typography.bodySmall)
        PainBodyFallback(selected, ratings, back, hindi, readOnly) { next -> if (!readOnly) onChange(next, points.filter { it.site in next }) }
    } else {
        val frame = RoundedCornerShape(20.dp)
        Box(Modifier.fillMaxWidth().height(440.dp).clip(frame).background(Color(0xFF2A2421)).border(1.dp, Color(0xFF4A403A), frame)) {
        AndroidView(modifier = Modifier.fillMaxSize().testTag("pain-body-3d").semantics {
            contentDescription = if (readOnly) { if (hindi) "सहेजी गई दर्द की जगह का घुमाने वाला नक्शा। जगह बदली नहीं जा सकती।" else "Rotatable saved pain map. Saved locations cannot be changed here." } else if (hindi) "घुमाने वाला दर्द की जगह का नक्शा। नीचे जगह के बटन से भी चुन सकते हैं।" else "Rotatable pain location map. Accessible region buttons are below."
        }, factory = { _ ->
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.onResume()
            webView
        })
        // One calm loading cover above the page (the page's own background and status would otherwise flash in), faded out once the model is drawn.
        AnimatedVisibility(!ready, Modifier.matchParentSize(), enter = EnterTransition.None, exit = fadeOut(tween(260))) {
            Column(Modifier.fillMaxSize().background(Color(0xFF2A2421)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFFF4ECE3).copy(alpha = .8f), strokeWidth = 2.5.dp)
                Text(if (hindi) "शरीर का नक्शा खुल रहा है…" else "Opening body map…", color = Color(0xFFF4ECE3), fontSize = 13.sp)
            }
        }
        }
    }
}

@Composable private fun PainBodyFallback(selected: List<String>, ratings: Map<String, Int>, back: Boolean, hindi: Boolean, readOnly: Boolean, onChange: (List<String>) -> Unit) {
    val latestSelected by rememberUpdatedState(selected)
    val latestOnChange by rememberUpdatedState(onChange)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(if (back) { if (hindi) "बायाँ" else "Left" } else if (hindi) "दायाँ" else "Right", style = MaterialTheme.typography.labelMedium)
        Canvas(Modifier.height(300.dp).width(150.dp).testTag("pain-body-map").pointerInput(back, readOnly) {
            if (!readOnly) detectTapGestures { point -> PainBodyMapRules.hit(back, point.x * 100f / size.width, point.y * 200f / size.height)?.let { latestOnChange(PainBodyMapRules.toggled(latestSelected, it)) } }
        }) {
            PainBodyMapRules.regions(back).forEach { region ->
                val path = Path().apply { region.outline.forEachIndexed { index, point ->
                    val x = point.x * size.width / 100f; val y = point.y * size.height / 200f
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }; close() }
                drawPath(path, if (region.id in selected) lerp(colors.surfaceVariant, Color(0xFFB33930), (ratings[region.id] ?: 0).coerceIn(0, 10) / 10f) else colors.surfaceVariant)
                drawPath(path, if (region.id in selected) colors.primary else colors.outline, style = Stroke(width = if (region.id in selected) 2.dp.toPx() else 1.dp.toPx()))
            }
        }
        Text(if (back) { if (hindi) "दायाँ" else "Right" } else if (hindi) "बायाँ" else "Left", style = MaterialTheme.typography.labelMedium)
    }
}
