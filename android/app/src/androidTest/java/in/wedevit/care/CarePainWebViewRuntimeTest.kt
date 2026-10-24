package `in`.wedevit.care

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
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
        screenshot("pain-webview-ready")
        ui.onNodeWithTag("pain-body-3d").performTouchInput { click(center) }
        ui.waitUntil(15_000) { points.value.isNotEmpty() }
        ui.runOnIdle {
            assertEquals(1, calls)
            assertTrue(points.value.single().z > 0)
            assertTrue(points.value.single().site in selected.value)
        }
        screenshot("pain-webview-raycast")
        ui.onNodeWithTag("pain-body-back").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-3d").performScrollTo()
        ui.onNodeWithTag("pain-body-3d").performTouchInput { click(center) }
        ui.waitUntil(15_000) { points.value.any { it.z < 0 } }
        val before = ui.runOnIdle { readOnly.value = true; points.value.toList() }
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
