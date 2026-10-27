package `in`.wedevit.care

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/** Who owns a touch that started on a slider: not decided yet, the slider (sideways drag), or the page (up/down scroll). */
enum class SliderTouch { Undecided, Slider, Page }

object SliderTouchPolicy {
    /** dx/dy are the finger's travel since it went down. Sideways past the touch slop moves the slider; up/down past it leaves the touch to the page scroll. */
    fun decide(dx: Float, dy: Float, touchSlop: Float): SliderTouch = when {
        abs(dx) >= touchSlop && abs(dx) > abs(dy) -> SliderTouch.Slider
        abs(dy) >= touchSlop -> SliderTouch.Page
        else -> SliderTouch.Undecided
    }
    /** Position along a Material 3 slider as 0..1. The thumb centre runs from thumbWidth/2 to width - thumbWidth/2. */
    fun fractionAt(x: Float, width: Float, thumbWidth: Float): Float =
        if (width <= thumbWidth) 0f else ((x - thumbWidth / 2f) / (width - thumbWidth)).coerceIn(0f, 1f)
}

/**
 * Lay this over a Slider (same size, drawn on top) so it takes the slider's touches: a tap or a deliberate sideways
 * drag sets the value, an up/down swipe is left to the page so scrolling never changes the value. The Slider itself
 * keeps its focus, keyboard and TalkBack adjustment because those do not go through touch.
 */
fun Modifier.sliderTouchOverlay(thumbWidth: Dp, onFraction: (Float) -> Unit): Modifier = composed {
    val latest by rememberUpdatedState(onFraction)
    pointerInput(thumbWidth) {
        val thumb = thumbWidth.toPx()
        awaitEachGesture {
            val down = awaitFirstDown()
            var owner = SliderTouch.Undecided
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) {
                    if (owner == SliderTouch.Undecided && !change.isConsumed) { change.consume(); latest(SliderTouchPolicy.fractionAt(down.position.x, size.width.toFloat(), thumb)) }
                    break
                }
                if (owner == SliderTouch.Undecided) {
                    if (change.isConsumed) break
                    val travel = change.position - down.position
                    owner = SliderTouchPolicy.decide(travel.x, travel.y, viewConfiguration.touchSlop)
                    if (owner == SliderTouch.Page) break
                }
                if (owner == SliderTouch.Slider) { change.consume(); latest(SliderTouchPolicy.fractionAt(change.position.x, size.width.toFloat(), thumb)) }
            }
        }
    }
}
