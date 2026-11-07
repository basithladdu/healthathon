package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Stands in for ModalBottomSheet everywhere: a full screen that slides up, with a close button, and nothing to drag
 * (users found the drag-down sheet confusing and easy to close by accident). Same parameters, so call sites only change the name.
 * [sheetState] and [dragHandle] are accepted and ignored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SaharaScreenSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState? = null,
    containerColor: Color = Cream,
    dragHandle: (@Composable () -> Unit)? = null,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = careText()
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = properties.shouldDismissOnBackPress, dismissOnClickOutside = false)) {
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(shown, enter = slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(180))) {
            Surface(Modifier.fillMaxSize(), color = containerColor) {
                Column(modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismissRequest, modifier = Modifier.testTag("screen-sheet-close")) { Icon(Icons.Default.Close, t("Close", "बंद करें")) }
                    }
                    HorizontalDivider(color = DoctorHairline)
                    Column(Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
        }
    }
}
