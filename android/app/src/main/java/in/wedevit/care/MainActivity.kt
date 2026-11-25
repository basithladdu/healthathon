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
        if (android.os.Build.VERSION.SDK_INT >= 29) { window.isNavigationBarContrastEnforced = false; window.decorView.isForceDarkAllowed = false }
        if (!BuildConfig.DEBUG && !BuildConfig.ALLOW_SCREENSHOTS) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null) { handleInviteIntent(intent); handleReminderIntent(intent) }
        CareLanguageState.current // Created here, outside any Compose snapshot, or reading it during composition can throw.
        // Android 12+: the system splash fades softly into the first frame instead of cutting to it (platform API, no extra library).
        // Off with "Remove animations" or Battery Saver; the animator follows the system animation scale as well.
        SaharaMotion.bind(this)
        if (android.os.Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { view ->
            if (!SaharaMotion.enabled) { view.remove(); return@setOnExitAnimationListener }
            view.iconView?.animate()?.scaleX(0.96f)?.scaleY(0.96f)?.alpha(0f)?.setDuration(180L)
                ?.setInterpolator(android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f))?.start() // SaharaMotion.EmphasizedAccelerate
            view.animate().alpha(0f).setDuration(220L)
                .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)) // SaharaMotion.Emphasized
                .withEndAction { view.remove() }.start()
        }
        // After the system splash (the logo blooming, res/drawable/splash_icon_animated.xml), the first launch gets a short brand moment (CareOnboarding.kt).
        setContent { CareTheme { SaharaBrandMoment { CareRoot(this) } } }
    }
    // "Remove animations" can change while Sahara is in the background, and older phones send no callback for it.
    override fun onResume() { super.onResume(); SaharaMotion.refresh() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleInviteIntent(intent); handleReminderIntent(intent) }
    /** A tapped reminder selects the person whose saved activity is due (openToday), then opens Reminders (CareReminderLaunch). */
    private fun handleReminderIntent(intent: Intent?) {
        if (intent?.hasExtra(CareTodayRules.OPEN_PERSON_EXTRA) == true) {
            ViewModelProvider(this)[CareRootModel::class.java].openToday.value = intent.getStringExtra(CareTodayRules.OPEN_PERSON_EXTRA).orEmpty()
            intent.removeExtra(CareTodayRules.OPEN_PERSON_EXTRA)
        }
        intent?.getIntExtra(CareReminderScheduler.DISMISS_EXTRA, 0)?.takeIf { it != 0 }?.let { androidx.core.app.NotificationManagerCompat.from(this).cancel(it); intent.removeExtra(CareReminderScheduler.DISMISS_EXTRA) }
        CareReminderLaunch.offer(intent)
    }
    private fun handleInviteIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = intent.dataString ?: return
        ViewModelProvider(this)[CareRootModel::class.java].offerInviteLink(link)
        intent.data = null
    }
}
