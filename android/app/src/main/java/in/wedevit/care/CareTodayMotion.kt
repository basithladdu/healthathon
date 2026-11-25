package `in`.wedevit.care

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * Today's and the Care tools' list rows: a row that arrives fades in, one that leaves fades out, and the rows around it
 * glide to their new place instead of jumping. Uses Sahara's timings; nothing moves when motion is off.
 */
internal fun LazyItemScope.saharaTodayItem(modifier: Modifier = Modifier): Modifier =
    if (!SaharaMotion.enabled) modifier
    else modifier.animateItem(
        fadeInSpec = SaharaMotion.tween<Float>(SaharaMotion.LIST_ITEM_MS),
        placementSpec = SaharaMotion.tween<IntOffset>(SaharaMotion.EXPAND_MS, easing = SaharaMotion.Emphasized),
        fadeOutSpec = SaharaMotion.tween<Float>(SaharaMotion.EXIT_MS),
    )
