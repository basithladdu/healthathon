package `in`.wedevit.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A kind is chosen by the operation, never guessed from the wording of a legacy message. */
enum class CareFeedbackKind { SAVED, NOTICE, ERROR, COMPLETED }

data class CareFeedback(
    val id: String = newId(),
    val kind: CareFeedbackKind,
    val title: String,
    val message: String,
)

/** Durable until acknowledged, so closing a sheet cannot discard its result. */
object CareFeedbackRules {
    fun append(current: List<CareFeedback>, next: CareFeedback): List<CareFeedback> =
        if (next.kind == CareFeedbackKind.ERROR && current.any {
            it.kind == next.kind && it.title == next.title && it.message == next.message
        }) current else current + next

    fun dismiss(current: List<CareFeedback>, id: String): List<CareFeedback> = current.filterNot { it.id == id }

    fun dialog(current: List<CareFeedback>): CareFeedback? =
        current.firstOrNull { it.kind == CareFeedbackKind.ERROR }
            ?: current.firstOrNull { it.kind == CareFeedbackKind.COMPLETED }

    fun snackbar(current: List<CareFeedback>): CareFeedback? =
        if (dialog(current) != null) null else current.firstOrNull {
            it.kind == CareFeedbackKind.SAVED || it.kind == CareFeedbackKind.NOTICE
        }
}

private data class CareFeedbackVisuals(val feedback: CareFeedback) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val duration: SnackbarDuration = SnackbarDuration.Short
    override val message: String = feedback.message
    override val withDismissAction: Boolean = true
}

/** Use in Scaffold.snackbarHost. An untyped legacy message gets an information icon, not a success tick. */
@Composable fun CareSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { data ->
        val saved = (data.visuals as? CareFeedbackVisuals)?.feedback?.kind == CareFeedbackKind.SAVED
        val background = if (saved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
        val foreground = if (saved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        Snackbar(
            modifier = Modifier.testTag(if (saved) "care-feedback-saved" else "care-feedback-notice"),
            shape = RoundedCornerShape(16.dp), containerColor = background, contentColor = foreground,
            dismissAction = { IconButton(onClick = { data.dismiss() }) { Icon(Icons.Default.Close, "Dismiss message", tint = foreground) } },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (saved) Icons.Default.CheckCircleOutline else Icons.Default.Info, null, Modifier.size(22.dp), tint = foreground)
                Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Compose last, after sheets/dialogs. Failures interrupt snackbars without discarding pending results. */
@Composable fun CareFeedbackPresenter(
    feedback: List<CareFeedback>,
    snackbarHostState: SnackbarHostState,
    onDismiss: (String) -> Unit,
) {
    val modal = CareFeedbackRules.dialog(feedback)
    val snack = CareFeedbackRules.snackbar(feedback)
    LaunchedEffect(snack?.id) {
        snack?.let {
            snackbarHostState.showSnackbar(CareFeedbackVisuals(it))
            onDismiss(it.id)
        }
    }
    modal?.let { CareResultDialog(it) { onDismiss(it.id) } }
}

/** A real Material dialog with a result icon, readable body and one clear dismissal action. */
@Composable fun CareResultDialog(feedback: CareFeedback, onDismiss: () -> Unit) {
    val failed = feedback.kind == CareFeedbackKind.ERROR
    val tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(if (failed) "care-feedback-error-dialog" else "care-feedback-completed-dialog"),
        shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(Modifier.size(54.dp).background(if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (failed) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline, null, Modifier.size(30.dp), tint = tint)
            }
        },
        title = { Text(feedback.title, fontWeight = FontWeight.SemiBold) },
        text = { Text(feedback.message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-feedback-dismiss"), shape = RoundedCornerShape(14.dp)) {
                Text(if (failed) "OK, go back" else "Done")
            }
        },
    )
}
