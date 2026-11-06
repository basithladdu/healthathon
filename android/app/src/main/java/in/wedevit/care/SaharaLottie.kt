package `in`.wedevit.care

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Sahara's small Lottie set (res/raw/lottie_*.json; original, CC0, see android/licenses/lottie/README.md).
 * [loops] is the usual playback; [still] is the frame (0..1) shown when motion is off; [size] the default size.
 * Never on goals-of-care or end-of-life screens.
 */
enum class SaharaLottieKind(@param:RawRes val res: Int, val loops: Boolean, val still: Float, val size: Dp) {
    Success(R.raw.lottie_success, false, 1f, 56.dp),
    Breathe(R.raw.lottie_breathe, true, .5f, 48.dp),
    Heart(R.raw.lottie_heart, true, 0f, 40.dp),
    Pill(R.raw.lottie_pill, true, 0f, 96.dp),
    Document(R.raw.lottie_document, false, 1f, 96.dp),
    MapPin(R.raw.lottie_pin, false, 1f, 96.dp),
    Wave(R.raw.lottie_wave, true, 0f, 64.dp),
    Loading(R.raw.lottie_loading, true, .25f, 64.dp),
    Confetti(R.raw.lottie_confetti, false, .4f, 96.dp),
    Hug(R.raw.lottie_hug, false, 1f, 96.dp),
    Calendar(R.raw.lottie_calendar, false, 1f, 96.dp),
    Offline(R.raw.lottie_offline, true, 1f, 96.dp),
}

/**
 * One decorative Lottie (hidden from TalkBack: the text beside it says what it means). Plays once, or loops calmly when
 * [loop]; [play] false holds it. With "Remove animations" or Battery Saver on it shows the still frame instead.
 * The default size is small; a size in [modifier] wins.
 */
@Composable fun SaharaLottie(kind: SaharaLottieKind, modifier: Modifier = Modifier, loop: Boolean = kind.loops, play: Boolean = true) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(kind.res))
    val motion = remember { SaharaMotion.enabled }
    val progress by animateLottieCompositionAsState(composition, isPlaying = play && motion, restartOnPlay = false,
        iterations = if (loop) LottieConstants.IterateForever else 1)
    LottieAnimation(composition, progress = { if (motion) progress else kind.still },
        modifier = modifier.size(kind.size).testTag("sahara-lottie-${kind.name}").clearAndSetSemantics { })
}
