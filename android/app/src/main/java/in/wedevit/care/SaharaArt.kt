package `in`.wedevit.care

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Sahara's small set of flat illustrations (vector, original art, see android/licenses/art/README.md). Never on goals-of-care or end-of-life screens. */
enum class SaharaArtKind(@param:DrawableRes val res: Int) {
    Welcome(R.drawable.art_welcome),
    NoMedicines(R.drawable.art_empty_medicines),
    NoDocuments(R.drawable.art_empty_documents),
    NoSymptoms(R.drawable.art_empty_symptoms),
    NoVisits(R.drawable.art_empty_visits),
    CommunityEmpty(R.drawable.art_empty_community),
    NoPatients(R.drawable.art_empty_patients),
    Offline(R.drawable.art_offline),
    CheckInSaved(R.drawable.art_success_checkin),
    Nearby(R.drawable.art_nearby),
}

/**
 * One decorative illustration (hidden from TalkBack: the text beside it says what it means). Default 160 x 120 dp;
 * a size in [modifier] wins. It fades and settles in once; static when animations or Battery Saver turn motion off.
 */
@Composable fun SaharaIllustration(kind: SaharaArtKind, modifier: Modifier = Modifier) {
    val shown = remember { Animatable(if (SaharaMotion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (shown.value < 1f) shown.animateTo(1f, SaharaMotion.tween(SaharaMotion.ENTER_MS, easing = SaharaMotion.Emphasized)) }
    Image(painterResource(kind.res), contentDescription = null, contentScale = ContentScale.Fit,
        modifier = modifier.size(width = 160.dp, height = 120.dp).testTag("sahara-art-${kind.name}")
            .graphicsLayer { val p = shown.value; alpha = p; scaleX = .94f + .06f * p; scaleY = .94f + .06f * p })
}
