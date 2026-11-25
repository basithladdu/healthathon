@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * A FilterChip for the records, visits and calendar screens whose selection colour glides (SELECT_MS) instead of snapping,
 * gives the phone's light key tap when pressed, and answers with a small bounce when it becomes the chosen one.
 * Same look as a plain FilterChip unless colours are passed.
 */
@Composable internal fun RecordsChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = FilterChipDefaults.shape,
    leadingIcon: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    selectedContainerColor: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified,
    selectedLabelColor: Color = Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    val selectedFill = selectedContainerColor.recordsOr { scheme.secondaryContainer }
    // A transparent rest colour fades from the chosen colour itself, so the glide never darkens on the way.
    val restFill = containerColor.recordsOr { selectedFill.copy(alpha = 0f) }
    val fill by animateColorAsState(if (selected) selectedFill else restFill, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "records-chip-fill")
    val ink by animateColorAsState(if (selected) selectedLabelColor.recordsOr { scheme.onSecondaryContainer } else labelColor.recordsOr { scheme.onSurfaceVariant },
        SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "records-chip-ink")
    val tick = rememberSaharaTick()
    FilterChip(
        selected = selected,
        onClick = { tick(); onClick() },
        label = label,
        modifier = modifier.saharaSelectBounce(selected, amount = .06f),
        enabled = enabled,
        shape = shape,
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(containerColor = fill, selectedContainerColor = fill, labelColor = ink, selectedLabelColor = ink),
    )
}

private inline fun Color.recordsOr(block: () -> Color): Color = if (this != Color.Unspecified) this else block()
