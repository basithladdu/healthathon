package `in`.wedevit.care

import androidx.compose.foundation.Image
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression (-PSAHARA_RENDER_UI=true): the Today "Symptom tracker" tile passes a vector drawable to
 * saharaImage. BitmapFactory cannot decode a vector, so decodeResource returned null and the Today list
 * threw "decodeResource(...) must not be null" as soon as that row was scrolled into view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaharaImageVectorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun vectorAndBitmapDrawablesBothDraw() {
        compose.setContent {
            Image(assertNotNullPainter(R.drawable.symptom_tracker_illustration), null)
            Image(assertNotNullPainter(R.drawable.nearby_care_illustration), null)
        }
        compose.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun assertNotNullPainter(id: Int) = saharaImage(id, 64.dp).also { assertNotNull(it) }
}
