package `in`.wedevit.care

import android.app.Activity
import android.os.SystemClock
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import org.json.JSONTokener

/** Only fictional Compose state and bundled appassets. No account, database or save operation. */
class CarePainWebViewRuntimeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun actualBundledWebGlRaycastReachesNativeAndReadOnlyRotationDoesNotMutate() {
        val selected = mutableStateOf<List<String>>(emptyList())
        val points = mutableStateOf<List<PainBodyPoint3d>>(emptyList())
        val readOnly = mutableStateOf(false)
        var calls = 0
        ui.setContent { CareTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CarePainBodyMap(selected.value, points.value, 7, false, readOnly.value) { sites, hits ->
                selected.value = sites; points.value = hits; calls++
            }
        } } }
        ui.onNodeWithTag("pain-body-3d").performScrollTo()
        waitForViewer("Drag to rotate")
        assertEquals(PainViewerPolicy.PAGE, onMain { viewer().url })
        assertTrue(js("document.querySelector('canvas').width > 0").contains("true"))
        assertTrue("A ready canvas must have real visible height", js("document.querySelector('canvas').getBoundingClientRect().height > 0 && document.getElementById('surface').clientHeight > 0").contains("true"))
        recordDiagnostics("ready-before-first-tap", observePointerEvents = true)
        screenshot("pain-webview-ready")
        ui.onNodeWithTag("pain-body-3d").performTouchInput { click(center) }
        recordDiagnostics("immediately-after-first-tap")
        try {
            ui.waitUntil(15_000) { points.value.isNotEmpty() }
        } finally {
            recordDiagnostics("after-first-pick-wait")
            screenshot("pain-webview-after-first-pick-wait")
        }
        ui.runOnIdle {
            assertEquals(1, calls)
            assertTrue(points.value.single().z > 0)
            assertTrue(points.value.single().site in selected.value)
        }
        screenshot("pain-webview-raycast")
        ui.onNodeWithTag("pain-body-back").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-3d").performScrollTo()
        recordDiagnostics("before-back-tap")
        ui.onNodeWithTag("pain-body-3d").performTouchInput { click(center) }
        try {
            ui.waitUntil(15_000) { points.value.any { it.z < 0 } }
        } finally {
            recordDiagnostics("after-back-pick-wait")
        }
        val before = ui.runOnIdle { readOnly.value = true; points.value.toList() }
        ui.waitForIdle() // Apply Compose's scheduled read-only state before polling the separate WebView event loop.
        waitForViewer("Saved pain map")
        val beforeCalls = ui.runOnIdle { calls }
        ui.onNodeWithTag("pain-body-3d").performTouchInput {
            swipe(centerLeft, centerRight, 400)
            click(center)
        }
        ui.waitForIdle()
        SystemClock.sleep(350)
        ui.runOnIdle { assertEquals(before, points.value); assertEquals(beforeCalls, calls) }
        screenshot("pain-webview-readonly")
    }

    /** Records real layout/bridge/touch evidence; never sends a callback or changes viewer state. */
    private fun recordDiagnostics(stage: String, observePointerEvents: Boolean = false) {
        val result = JSONObject().put("stage", stage).put("uptimeMs", SystemClock.uptimeMillis())
        runCatching {
            result.put("nativeOpeningLabels", ui.onAllNodesWithText("Opening body map…").fetchSemanticsNodes().size)
            result.put("composeBoundsInRoot", ui.onNodeWithTag("pain-body-3d").fetchSemanticsNode().boundsInRoot.toString())
            result.put("native", onMain {
                val web = viewer()
                val location = IntArray(2).also(web::getLocationOnScreen)
                val visible = Rect()
                val hasVisibleRect = web.getGlobalVisibleRect(visible)
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
                JSONObject().put("url", web.url).put("width", web.width).put("height", web.height)
                    .put("x", web.x).put("y", web.y).put("screenX", location[0]).put("screenY", location[1])
                    .put("globalVisibleRect", visible.toShortString()).put("hasVisibleRect", hasVisibleRect)
                    .put("attached", web.isAttachedToWindow).put("shown", web.isShown).put("visibility", web.visibility)
                    .put("windowVisibility", web.windowVisibility).put("hardwareAccelerated", web.isHardwareAccelerated)
                    .put("layerType", web.layerType).put("scrollX", web.scrollX).put("scrollY", web.scrollY)
                    .put("activity", activity.javaClass.name).put("windowFlags", Integer.toHexString(activity.window.attributes.flags))
                    .put("windowHardwareFlag", activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
                    .put("decorHardwareAccelerated", activity.window.decorView.isHardwareAccelerated)
            })
            val script = """
                (() => {
                  const canvas = document.querySelector('canvas');
                  const rect = el => el ? (() => { const r = el.getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height,top:r.top,bottom:r.bottom}; })() : null;
                  if ($observePointerEvents && canvas && !window.__saharaPainPointerAudit) {
                    window.__saharaPainPointerAudit = [];
                    for (const type of ['pointerdown','pointerup','pointercancel','touchstart','touchend']) {
                      canvas.addEventListener(type, event => {
                        const touch = event.changedTouches?.[0];
                        window.__saharaPainPointerAudit.push({type:event.type,x:event.clientX ?? touch?.clientX,y:event.clientY ?? touch?.clientY,isPrimary:event.isPrimary,pointerType:event.pointerType,time:event.timeStamp});
                        if (window.__saharaPainPointerAudit.length > 12) window.__saharaPainPointerAudit.shift();
                      }, {capture:true,passive:true});
                    }
                  }
                  return {url:location.href,origin:location.origin,readyState:document.readyState,visibility:document.visibilityState,
                    bridgeType:typeof window.SaharaPainNative,postMessageType:typeof window.SaharaPainNative?.postMessage,
                    viewerType:typeof window.SaharaPainViewer,status:document.getElementById('status')?.textContent,
                    innerWidth,innerHeight,devicePixelRatio,scrollX,scrollY,body:rect(document.body),surface:rect(document.getElementById('surface')),
                    canvas:canvas?{rect:rect(canvas),width:canvas.width,height:canvas.height,display:getComputedStyle(canvas).display,visibility:getComputedStyle(canvas).visibility}:null,
                    pointerEvents:window.__saharaPainPointerAudit ?? []};
                })()
            """.trimIndent()
            result.put("javascript", JSONTokener(js(script)).nextValue())
        }.onFailure { result.put("diagnosticError", "${it.javaClass.simpleName}: ${it.message}") }
        val line = result.toString()
        Log.i("SaharaPainRuntime", line)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "pain-webview-diagnostics.jsonl").appendText("$line\n")
    }

    private fun waitForViewer(expected: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        var last = ""
        while (SystemClock.uptimeMillis() < deadline) {
            last = js("document.getElementById('status')?.textContent || ''")
            if (last.contains(expected)) return
            if (last.contains("unavailable")) fail("Bundled WebGL viewer reported unavailable: $last")
            SystemClock.sleep(200)
        }
        fail("Bundled viewer did not become ready: $last")
    }
    private fun js(script: String): String {
        val value = AtomicReference<String?>(null)
        onMain { viewer().evaluateJavascript(script) { value.set(it) } }
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (value.get() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        return value.get() ?: error("No WebView JavaScript result")
    }
    private fun viewer(): WebView {
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .mapNotNull { find((it as Activity).window.decorView) }.single()
    }
    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(action()) }
        return result.get()
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(150)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
