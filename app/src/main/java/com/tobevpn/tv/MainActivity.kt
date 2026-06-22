package com.tobevpn.tv

import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var prefsDataStore: PrefsDataStore

    @Inject
    lateinit var sessionDao: SessionDao

    @Inject
    lateinit var authRepositoryLazy: Lazy<AuthRepository>

    @Inject
    lateinit var connectionManagerLazy: Lazy<VpnConnectionManager>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var splashFinished by mutableStateOf(false)
        var startAuthenticated by mutableStateOf<Boolean?>(null)
        var onboardingNeeded by mutableStateOf<Boolean?>(null)

        lifecycleScope.launch {
            startAuthenticated = withContext(Dispatchers.IO) {
                val authRepository = authRepositoryLazy.get()
                val linked = authRepository.syncDeviceSessionState().getOrNull()
                when (linked) {
                    true -> {
                        authRepository.syncSubscription()
                        true
                    }
                    false -> {
                        connectionManagerLazy.get().stopVpn()
                        authRepository.clearRemoteUnlinkedSession()
                        false
                    }
                    null -> {
                        val fallbackLinked = sessionDao.getSession()?.authState == "AUTHENTICATED"
                        if (fallbackLinked) {
                            runCatching { authRepository.syncSubscription() }
                        }
                        fallbackLinked
                    }
                }
            }
        }
        lifecycleScope.launch {
            val seen = prefsDataStore.onboardingSeen.first()
            onboardingNeeded = !seen
        }

        setContent {
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

            Box(modifier = Modifier.fillMaxSize()) {
                ToBeVPNTvTheme(darkTheme = themeMode == AppThemeMode.DARK) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        if (dataReady) {
                            val navController = rememberNavController()
                            Box(modifier = Modifier.alpha(mainAlpha)) {
                                AppNavHost(
                                    navController = navController,
                                    startFromOnboarding = onboardingNeeded == true,
                                    startAuthenticated = startAuthenticated == true,
                                )
                            }
                            // Updater overlay. UpdateBannerCheck triggers the
                            // automatic 7-day GitHub probe; UpdateBannerHost
                            // renders the modal dialog when state != Idle.
                            // Mounted at activity level so it covers any route.
                            UpdateBannerCheck()
                            UpdateBannerHost(modifier = Modifier.alpha(mainAlpha))
                        }
                    }
                }

                if (!splashFinished) {
                    SplashScreen(
                        darkTheme = themeMode == AppThemeMode.DARK,
                        onFinished = { splashFinished = true },
                    )
                }
            }
        }
    }
}
