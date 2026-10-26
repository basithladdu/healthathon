@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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

/** A bundled 3D model plus visible region buttons, with a 2D fallback if this WebView cannot render it. */
@Composable fun CarePainBodyMap(selected: List<String>, points: List<PainBodyPoint3d>, rating: Int?, hindi: Boolean, readOnly: Boolean = false, onChange: (List<String>, List<PainBodyPoint3d>) -> Unit) {
    var back by rememberSaveable { mutableStateOf(false) }
    var focus by rememberSaveable { mutableStateOf<String?>(null) }
    var focusNonce by rememberSaveable { mutableIntStateOf(0) }
    val t = { en: String, hi: String -> if (hindi) hi else en }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (readOnly) t("Saved pain areas", "सहेजी गई दर्द की जगह") else t("Where is the pain? (optional)", "दर्द कहाँ है? (वैकल्पिक)"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!back, { if (back) { back = false; focus = null; focusNonce++ } }, label = { Text(t("Front", "सामने")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-front"))
            FilterChip(back, { if (!back) { back = true; focus = null; focusNonce++ } }, label = { Text(t("Back", "पीछे")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-back"))
            if (!readOnly && selected.isNotEmpty()) TextButton(onClick = { onChange(emptyList(), emptyList()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-clear")) { Text(t("Clear", "हटाएँ")) }
        }
        // The model sits above the parts list so a zoom is visible without scrolling.
        Text(if (readOnly) t("Pick a part to zoom in. Saved locations stay unchanged. Left and right mean the patient's sides.", "ज़ूम करने के लिए हिस्सा चुनें। सहेजी हुई जगह नहीं बदलेगी। बायाँ और दायाँ मरीज़ की तरफ़ से हैं।") else t("Pick a part to zoom in, then tap exactly where it hurts. Left and right mean the patient's sides.", "ज़ूम करने के लिए हिस्सा चुनें, फिर ठीक वहाँ छुएँ जहाँ दर्द है। बायाँ और दायाँ मरीज़ की तरफ़ से हैं।"), style = MaterialTheme.typography.bodySmall)
        LocalPainBodyViewer(selected, points, rating, hindi, back, readOnly, focus, focusNonce, onChange)
        Text(if (rating == null) t("Not rated — no pain intensity shown", "अंक नहीं बताया — दर्द की तीव्रता नहीं दिखाई गई") else "${t("Pain", "दर्द")}: $rating/10 · ${t("Colour follows this number", "रंग इसी अंक के अनुसार है")}", style = MaterialTheme.typography.bodySmall)
        // One list per side: a part zooms the model and marks it; tapping the zoomed, marked part again unmarks it.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(focus == null, { focus = null; focusNonce++ }, label = { Text(t("Full body", "पूरा शरीर")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-focus-full"))
            PainBodyMapRules.sitesFor(back).forEach { id ->
                // Selected means marked as hurting; the zoomed part gets a heavier border.
                val marked = if (readOnly) focus == id else id in selected
                FilterChip(marked, {
                    if (!readOnly && (id !in selected || focus == id)) { val next = PainBodyMapRules.toggled(selected, id); onChange(next, points.filter { it.site in next }) }
                    focus = id; focusNonce++
                }, label = { Text(PainBodyMapRules.focusLabel(id, back, hindi)) },
                    border = if (focus == id) BorderStroke(2.dp, Rust) else FilterChipDefaults.filterChipBorder(enabled = true, selected = marked),
                    modifier = Modifier.heightIn(min = 48.dp).testTag(if (readOnly) "pain-body-focus-$id" else "pain-body-site-$id"))
            }
        }
        if (selected.isNotEmpty()) Text(selected.joinToString { PainBodyMapRules.label(it, hindi) }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("pain-body-selected"))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable private fun LocalPainBodyViewer(selected: List<String>, points: List<PainBodyPoint3d>, rating: Int?, hindi: Boolean, back: Boolean, readOnly: Boolean, focus: String?, focusNonce: Int, onChange: (List<String>, List<PainBodyPoint3d>) -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val latestSelected by rememberUpdatedState(selected)
    val latestPoints by rememberUpdatedState(points)
    val latestOnChange by rememberUpdatedState(onChange)
    val latestReadOnly by rememberUpdatedState(readOnly)
    val state = JSONObject().put("selected", JSONArray(selected.filter { it in PainBodyMapRules.sites })).put("rating", rating?.takeIf { it in 0..10 } ?: JSONObject.NULL).put("hindi", hindi).put("readOnly", readOnly).put("points", PainBodyMapRules.encodePoints(points)).toString()
    LaunchedEffect(webView, ready, state) {
        if (ready) webView?.evaluateJavascript("window.SaharaPainViewer?.setState($state);", null)
    }
    LaunchedEffect(webView, ready, back) {
        if (ready) webView?.evaluateJavascript("window.SaharaPainViewer?.setView('${if (back) "back" else "front"}');", null)
    }
    LaunchedEffect(webView, ready, focus, focusNonce) {
        if (!ready) return@LaunchedEffect
        // The id is one of PainBodyMapRules.sites keys, so it is safe to place inside the script string.
        if (focus != null && focus in PainBodyMapRules.sites) webView?.evaluateJavascript("window.SaharaPainViewer?.focusRegion('$focus','${if (back) "back" else "front"}');", null)
        else if (focusNonce > 0) webView?.evaluateJavascript("window.SaharaPainViewer?.focusFull();", null)
    }
    if (failed) {
        Text(if (readOnly) { if (hindi) "इस फ़ोन पर 3D नहीं खुला। सहेजे हुए हिस्से नीचे नक्शे पर हैं।" else "3D could not open on this phone. Saved areas are shown on the diagram." } else if (hindi) "इस फ़ोन पर 3D नहीं खुला। नीचे नक्शे या बटन से जगह चुनें।" else "3D could not open on this phone. Choose on the map or the buttons below.", style = MaterialTheme.typography.bodySmall)
        PainBodyFallback(selected, rating, back, hindi, readOnly) { next -> if (!readOnly) onChange(next, points.filter { it.site in next }) }
    } else {
        if (!ready) Text(if (hindi) "शरीर का नक्शा खुल रहा है…" else "Opening body map…", style = MaterialTheme.typography.bodySmall)
        AndroidView(modifier = Modifier.fillMaxWidth().height(320.dp).testTag("pain-body-3d").semantics {
            contentDescription = if (readOnly) { if (hindi) "सहेजी गई दर्द की जगह का घुमाने वाला नक्शा। जगह बदली नहीं जा सकती।" else "Rotatable saved pain map. Saved locations cannot be changed here." } else if (hindi) "घुमाने वाला दर्द की जगह का नक्शा। नीचे जगह के बटन से भी चुन सकते हैं।" else "Rotatable pain location map. Accessible region buttons are below."
        }, factory = { context ->
            WebView(context).apply {
                webView = this
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
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: androidx.webkit.WebResourceErrorCompat) { if (request.isForMainFrame) view.post { failed = true } }
                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean { failed = true; return true }
                }
                setOnTouchListener { view, event ->
                    view.parent?.requestDisallowInterceptTouchEvent(event.actionMasked !in setOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL))
                    false
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(this, "SaharaPainNative", setOf(PainViewerPolicy.ORIGIN)) { view, message, origin, mainFrame, _ ->
                        if (mainFrame && PainViewerPolicy.trustedOrigin(origin.toString())) {
                            val data = message.data.orEmpty()
                            if (data.length <= 180) {
                                val type = runCatching { JSONObject(data).optString("type") }.getOrNull()
                                view.post {
                                    when (type) {
                                        "ready" -> ready = true
                                        "error" -> failed = true
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
                            }
                        }
                    }
                    loadUrl(PainViewerPolicy.PAGE)
                } else failed = true
            }
        }, onReset = null, onRelease = { view ->
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "SaharaPainNative")
            view.stopLoading(); view.destroy()
            if (webView === view) webView = null
        })
    }
}

@Composable private fun PainBodyFallback(selected: List<String>, rating: Int?, back: Boolean, hindi: Boolean, readOnly: Boolean, onChange: (List<String>) -> Unit) {
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
                drawPath(path, if (region.id in selected) lerp(colors.surfaceVariant, Color(0xFFB33930), (rating ?: 0).coerceIn(0, 10) / 10f) else colors.surfaceVariant)
                drawPath(path, if (region.id in selected) colors.primary else colors.outline, style = Stroke(width = if (region.id in selected) 2.dp.toPx() else 1.dp.toPx()))
            }
        }
        Text(if (back) { if (hindi) "दायाँ" else "Right" } else if (hindi) "बायाँ" else "Left", style = MaterialTheme.typography.labelMedium)
    }
}
