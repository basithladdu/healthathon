package `in`.wedevit.care

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    lateinit var careModel: CareModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null) { handleInviteIntent(intent); handleReminderIntent(intent) }
        setContent { CareTheme { CareRoot(this) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleInviteIntent(intent); handleReminderIntent(intent) }
    /** A tapped reminder selects the person whose saved activity is due (openToday), then opens Reminders (CareReminderLaunch). */
    private fun handleReminderIntent(intent: Intent?) {
        if (intent?.hasExtra(CareTodayRules.OPEN_PERSON_EXTRA) == true) {
            ViewModelProvider(this)[CareRootModel::class.java].openToday.value = intent.getStringExtra(CareTodayRules.OPEN_PERSON_EXTRA).orEmpty()
            intent.removeExtra(CareTodayRules.OPEN_PERSON_EXTRA)
        }
        CareReminderLaunch.offer(intent)
    }
    private fun handleInviteIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = intent.dataString ?: return
        ViewModelProvider(this)[CareRootModel::class.java].offerInviteLink(link)
        intent.data = null
    }
}
