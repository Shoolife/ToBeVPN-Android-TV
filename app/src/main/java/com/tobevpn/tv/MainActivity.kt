package com.tobevpn.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.local.dao.SessionDao
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.domain.model.AppThemeMode
import com.tobevpn.tv.presentation.navigation.AppNavHost
import com.tobevpn.tv.vpn.VpnConnectionManager
import dagger.Lazy
import com.tobevpn.tv.presentation.splash.SplashScreen
import com.tobevpn.tv.presentation.theme.ToBeVPNTvTheme
import com.tobevpn.tv.update.UpdateBannerCheck
import com.tobevpn.tv.update.UpdateBannerHost
import com.tobevpn.tv.update.MandatoryUpdateGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var appNavController: NavHostController? = null
    private var backPressActive = false
    private var longBackHandled = false
    private var suppressTrailingBackEvents = false
    private var suppressActivationKeyEvents = false
    private val longBackRunnable = Runnable {
        if (backPressActive && !longBackHandled) {
            longBackHandled = returnToNavigationRoot()
        }
    }
    private val clearBackSuppressionRunnable = Runnable {
        suppressTrailingBackEvents = false
    }
    private val clearActivationSuppressionRunnable = Runnable {
        suppressActivationKeyEvents = false
    }

    @Inject
    lateinit var prefsDataStore: PrefsDataStore

    @Inject
    lateinit var sessionDao: SessionDao

    @Inject
    lateinit var authRepositoryLazy: Lazy<AuthRepository>

    @Inject
    lateinit var connectionManagerLazy: Lazy<VpnConnectionManager>

    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isActivationKey(event.keyCode) && suppressActivationKeyEvents) {
            // Holding OK/Enter on an on-screen Back button can leave repeat
            // events in flight after Home receives focus. Swallow the entire
            // tail so it cannot activate the connect button.
            extendActivationSuppression()
            return true
        }

        if (
            event.action == KeyEvent.ACTION_DOWN &&
            !isActivationKey(event.keyCode) &&
            suppressActivationKeyEvents
        ) {
            clearActivationSuppression()
        }

        if (event.keyCode != KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && suppressTrailingBackEvents) {
                clearBackSuppression()
            }
            return super.dispatchKeyEvent(event)
        }

        if (suppressTrailingBackEvents && !backPressActive) {
            // Some TV remotes model one physical hold as several independent
            // Back clicks. Keep swallowing them until the stream has been
            // completely quiet, rather than trusting its first ACTION_UP.
            extendBackSuppression()
            return true
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!backPressActive) {
                    backPressActive = true
                    longBackHandled = false
                    window.decorView.postDelayed(
                        longBackRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                }

                // A repeat/long-press flag may arrive before the timer on some
                // remotes, so honour it immediately and cancel the timer.
                if ((event.repeatCount > 0 || event.isLongPress) && !longBackHandled) {
                    window.decorView.removeCallbacks(longBackRunnable)
                    longBackHandled = returnToNavigationRoot()
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                window.decorView.removeCallbacks(longBackRunnable)
                val wasActive = backPressActive
                backPressActive = false

                if (longBackHandled) {
                    longBackHandled = false
                    armBackSuppression()
                    return true
                }

                if (wasActive) {
                    // Raw key events are consumed above; dispatch exactly one
                    // normal Back request for a short click. The navigation-
                    // level callback below handles both key and system Back.
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
            }
        }
        return true
    }

    /**
     * Handles every Back source, including predictive/system Back events that
     * never reach [dispatchKeyEvent]. Once navigation lands on its root, a
     * quiet-period guard prevents the tail of a held remote key from exiting
     * the application.
     */
    private fun handleNavigationBack() {
        if (suppressTrailingBackEvents) {
            extendBackSuppression()
            return
        }

        val navController = appNavController
        if (navController?.previousBackStackEntry != null) {
            navController.popBackStack()
            if (navController.previousBackStackEntry == null) {
                armBackSuppression()
            }
            return
        }

        // Back is intentionally disabled at the graph root. On an
        // authenticated session this is the Home/connect screen, which must
        // stay open for both a short click and a held remote key.
    }

    private fun armBackSuppression() {
        suppressTrailingBackEvents = true
        extendBackSuppression()
    }

    private fun extendBackSuppression() {
        window.decorView.removeCallbacks(clearBackSuppressionRunnable)
        window.decorView.postDelayed(
            clearBackSuppressionRunnable,
            BACK_RELEASE_QUIET_PERIOD_MS,
        )
    }

    private fun clearBackSuppression() {
        window.decorView.removeCallbacks(clearBackSuppressionRunnable)
        suppressTrailingBackEvents = false
    }

    private fun armActivationSuppression() {
        suppressActivationKeyEvents = true
        extendActivationSuppression()
    }

    private fun extendActivationSuppression() {
        window.decorView.removeCallbacks(clearActivationSuppressionRunnable)
        window.decorView.postDelayed(
            clearActivationSuppressionRunnable,
            ACTIVATION_RELEASE_QUIET_PERIOD_MS,
        )
    }

    private fun clearActivationSuppression() {
        window.decorView.removeCallbacks(clearActivationSuppressionRunnable)
        suppressActivationKeyEvents = false
    }

    private fun isActivationKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
            keyCode == KeyEvent.KEYCODE_SPACE
    }

    /**
     * A long Back press is a TV shortcut to the first screen in the current
     * navigation graph. For an authenticated session that is the Home screen.
     * Keeping this at the activity level makes the shortcut consistent across
     * every destination; ordinary Back still pops child screens, while the
     * graph root intentionally ignores it.
     */
    private fun returnToNavigationRoot(): Boolean {
        val navController = appNavController ?: return false
        while (navController.previousBackStackEntry != null) {
            if (!navController.popBackStack()) break
        }
        return true
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(longBackRunnable)
        window.decorView.removeCallbacks(clearBackSuppressionRunnable)
        window.decorView.removeCallbacks(clearActivationSuppressionRunnable)
        appNavController = null
        super.onDestroy()
    }

    private companion object {
        const val BACK_RELEASE_QUIET_PERIOD_MS = 2_500L
        const val ACTIVATION_RELEASE_QUIET_PERIOD_MS = 1_500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var splashFinished by mutableStateOf(false)
        var startAuthenticated by mutableStateOf<Boolean?>(null)
        var onboardingNeeded by mutableStateOf<Boolean?>(null)

        lifecycleScope.launch {
            val authRepository = authRepositoryLazy.get()
            val cachedAuthenticated = withContext(Dispatchers.IO) {
                runCatching {
                    sessionDao.getSession()?.authState == "AUTHENTICATED"
                }.getOrDefault(false)
            }
            // Build navigation from local state immediately. Remote device
            // validation must not leave the TV on a blank screen while offline.
            startAuthenticated = cachedAuthenticated

            withContext(Dispatchers.IO) {
                val linked = authRepository.syncDeviceSessionState().getOrNull()
                when (linked) {
                    true -> {
                        authRepository.syncSubscription()
                    }
                    false -> {
                        connectionManagerLazy.get().stopVpn()
                        authRepository.clearRemoteUnlinkedSession()
                    }
                    null -> {
                        if (cachedAuthenticated) {
                            runCatching { authRepository.syncSubscription() }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            val seen = prefsDataStore.onboardingSeen.first()
            onboardingNeeded = !seen
        }

        setContent {
            val updateRequired by prefsDataStore.observeUpdateRequired()
                .collectAsStateWithLifecycle(initialValue = false)
            val savedThemeMode by prefsDataStore.themeModeOrNull.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val systemThemeMode = if (isSystemInDarkTheme()) AppThemeMode.DARK else AppThemeMode.LIGHT
            val themeMode = savedThemeMode ?: systemThemeMode
            val dataReady = splashFinished && startAuthenticated != null && onboardingNeeded != null
            val mainAlpha by animateFloatAsState(
                targetValue = if (dataReady) 1f else 0f,
                animationSpec = tween(400),
                label = "mainAlpha",
            )

            androidx.compose.runtime.LaunchedEffect(updateRequired) {
                if (updateRequired) {
                    // A server minimum-version block also applies to a tunnel
                    // that was already active when the heartbeat discovered it.
                    connectionManagerLazy.get().stopVpn()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                ToBeVPNTvTheme(darkTheme = themeMode == AppThemeMode.DARK) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        if (dataReady) {
                            val navController = rememberNavController()
                            DisposableEffect(navController) {
                                appNavController = navController
                                onDispose {
                                    if (appNavController === navController) {
                                        appNavController = null
                                    }
                                }
                            }
                            Box(modifier = Modifier.alpha(mainAlpha)) {
                                AppNavHost(
                                    navController = navController,
                                    startFromOnboarding = onboardingNeeded == true,
                                    startAuthenticated = startAuthenticated == true,
                                    onScreenBackActivated = ::armActivationSuppression,
                                )
                            }
                            // Declared after NavHost so this callback owns all
                            // activity-level Back sources and can distinguish
                            // an ordinary click from the tail of a held key.
                            BackHandler(onBack = ::handleNavigationBack)
                            // Updater overlay. UpdateBannerCheck triggers the
                            // automatic 7-day GitHub probe; UpdateBannerHost
                            // renders the modal dialog when state != Idle.
                            // Mounted at activity level so it covers any route.
                            if (BuildConfig.IN_APP_UPDATES_ENABLED) {
                                UpdateBannerCheck()
                                // The forced-update dialog on Home owns this
                                // state and must not be stacked under a second
                                // update dialog.
                                if (!updateRequired) {
                                    UpdateBannerHost(modifier = Modifier.alpha(mainAlpha))
                                }
                            }
                        }
                    }
                }

                if (!splashFinished) {
                    SplashScreen(
                        darkTheme = themeMode == AppThemeMode.DARK,
                        onFinished = { splashFinished = true },
                    )
                }
                if (splashFinished) {
                    MandatoryUpdateGate(
                        updateRequired = updateRequired,
                        onQuit = { finishAffinity() },
                    )
                }
            }
        }
    }
}
