package `in`.wedevit.care

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/*
 * Sahara's one on/off switch: Material's Switch in Sahara colours, with the phone's toggle feel. Turning something on
 * and turning it off feel different (ToggleOn / ToggleOff), so an older user can tell by touch which way a reminder went.
 * The haptics follow the phone's own Touch feedback setting.
 */

/**
 * The toggle haptic on its own, for a row that owns the toggle (a `toggleable` row around a Switch whose
 * onCheckedChange is null): call it with the new value inside the row's onValueChange.
 */
@Composable fun rememberSaharaToggleHaptic(): (Boolean) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { on: Boolean -> runCatching { haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff) }; Unit } }
}

/**
 * Material Switch in Sahara colours (sage track when on, a 3:1 [SaharaColors.Edge] outline when off) with ToggleOn /
 * ToggleOff haptics. Pass [onCheckedChange] null when the surrounding row is the toggle, as with Material's Switch, and
 * fire [rememberSaharaToggleHaptic] from that row instead. The [modifier] (test tags, semantics) is passed straight through.
 */
@Composable fun SaharaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null) {
    val feel = rememberSaharaToggleHaptic()
    Switch(checked = checked, onCheckedChange = onCheckedChange?.let { change -> { on: Boolean -> feel(on); change(on) } }, modifier = modifier,
        thumbContent = thumbContent, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = SaharaColors.OnPrimary, checkedTrackColor = SaharaColors.Primary, checkedBorderColor = SaharaColors.Primary,
            checkedIconColor = SaharaColors.Primary,
            uncheckedThumbColor = SaharaColors.Edge, uncheckedTrackColor = SaharaColors.Surface, uncheckedBorderColor = SaharaColors.Edge,
            uncheckedIconColor = SaharaColors.Surface))
}
