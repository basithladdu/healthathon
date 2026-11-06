package `in`.wedevit.care

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** One way to ask for the microphone: [request] runs the action at once when allowed, otherwise asks first. */
@Stable internal class SaharaMicGate(val request: (() -> Unit) -> Unit)

private fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }

/**
 * Sahara's microphone ask: a short dialog saying why (Allow / Not now), then Android's own prompt.
 * After "Don't allow" with no further prompt possible, a dialog opens the app's Settings instead.
 * Recording and consent stay with the caller; this only decides when Android is asked.
 */
@Composable internal fun rememberSaharaMicGate(): SaharaMicGate {
    val context = LocalContext.current; val t = careText()
    val prefs = remember { context.getSharedPreferences("sahara-permissions", Context.MODE_PRIVATE) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var explain by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    fun canAskAgain(): Boolean { val activity = context.findActivity() ?: return true
        return !prefs.getBoolean("mic-asked", false) || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val run = pending; pending = null
        if (granted) run?.invoke() else if (!canAskAgain()) blocked = true
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false; pending = null },
        icon = { Icon(Icons.Default.Mic, null) },
        title = { Text(t("Use the microphone?", "माइक इस्तेमाल करें?")) },
        text = { Text(t("Sahara needs it to record this talk.", "इस बातचीत को रिकॉर्ड करने के लिए सहारा को इसकी ज़रूरत है।")) },
        confirmButton = { TextButton(onClick = { explain = false; prefs.edit().putBoolean("mic-asked", true).apply(); launcher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.testTag("mic-permission-allow")) { Text(t("Allow", "अनुमति दें")) } },
        dismissButton = { TextButton(onClick = { explain = false; pending = null }, modifier = Modifier.testTag("mic-permission-later")) { Text(t("Not now", "अभी नहीं")) } },
        modifier = Modifier.testTag("mic-permission-dialog"))
    if (blocked) AlertDialog(onDismissRequest = { blocked = false },
        icon = { Icon(Icons.Default.MicOff, null) },
        title = { Text(t("Microphone is off", "माइक बंद है")) },
        text = { Text(t("Turn it on in Settings to record.", "रिकॉर्ड करने के लिए सेटिंग्स में चालू करें।")) },
        confirmButton = { TextButton(onClick = { blocked = false; runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, modifier = Modifier.testTag("mic-permission-settings")) { Text(t("Open Settings", "सेटिंग्स खोलें")) } },
        dismissButton = { TextButton(onClick = { blocked = false }) { Text(t("Not now", "अभी नहीं")) } },
        modifier = Modifier.testTag("mic-permission-blocked"))
    return remember(launcher) { SaharaMicGate { run ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) run()
        else { pending = run; if (canAskAgain()) explain = true else blocked = true }
    } }
}
