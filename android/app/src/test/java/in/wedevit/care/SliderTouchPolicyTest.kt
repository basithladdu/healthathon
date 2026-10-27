package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderTouchPolicyTest {
    private val slop = 16f
    @Test fun upDownSwipesBelongToThePageAndSidewaysDragsToTheSlider() {
        assertEquals(SliderTouch.Undecided, SliderTouchPolicy.decide(3f, 4f, slop)) // still a possible tap
        assertEquals(SliderTouch.Page, SliderTouchPolicy.decide(2f, 30f, slop)) // scroll down: score unchanged
        assertEquals(SliderTouch.Page, SliderTouchPolicy.decide(-6f, -18f, slop)) // scroll up
        assertEquals(SliderTouch.Page, SliderTouchPolicy.decide(17f, 20f, slop)) // mostly up/down even past the slop sideways
        assertEquals(SliderTouch.Page, SliderTouchPolicy.decide(16f, 16f, slop)) // exact diagonal: the page decides
        assertEquals(SliderTouch.Slider, SliderTouchPolicy.decide(16f, 3f, slop)) // deliberate sideways drag
        assertEquals(SliderTouch.Slider, SliderTouchPolicy.decide(-20f, 12f, slop))
        assertEquals(SliderTouch.Undecided, SliderTouchPolicy.decide(15f, 0f, slop)) // under the slop: not yet a drag
    }
    @Test fun touchPositionMapsLikeTheMaterialSliderTrack() {
        // 328 wide with a 28 thumb: the thumb centre runs from 14 to 314.
        assertEquals(0f, SliderTouchPolicy.fractionAt(14f, 328f, 28f), 1e-4f)
        assertEquals(0f, SliderTouchPolicy.fractionAt(0f, 328f, 28f), 1e-4f)
        assertEquals(.5f, SliderTouchPolicy.fractionAt(164f, 328f, 28f), 1e-4f)
        assertEquals(1f, SliderTouchPolicy.fractionAt(314f, 328f, 28f), 1e-4f)
        assertEquals(1f, SliderTouchPolicy.fractionAt(400f, 328f, 28f), 1e-4f)
        assertEquals(0f, SliderTouchPolicy.fractionAt(10f, 20f, 28f), 1e-4f) // not laid out yet
    }
}
