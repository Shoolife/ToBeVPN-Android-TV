package com.tobevpn.tv.presentation.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tobevpn.tv.R
import com.tobevpn.tv.presentation.components.AUTH_QR_GLOW_FRACTION
import com.tobevpn.tv.presentation.components.AuthActionButton
import com.tobevpn.tv.presentation.components.accentedText
import com.tobevpn.tv.presentation.components.AuthQrPanel
import com.tobevpn.tv.presentation.components.AuthScreenCard
import com.tobevpn.tv.presentation.components.QrCode
import com.tobevpn.tv.presentation.navigation.PairingEntry
import com.tobevpn.tv.presentation.rememberTvProportionalScale

private const val GOOGLE_PLAY_URL =
    "https://play.google.com/store/apps/details?id=com.tobevpn.app"

/**
 * First sign-in step: install the phone app, or say why that is not possible.
 * The three buttons pick the pairing route — the Android app path uses a
 * device code, the two others fall back to Telegram.
 */
@Composable
fun MobileAppInstallScreen(
    onContinue: (PairingEntry) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // One scale factor for the whole screen, taken from the screen height via
        // rememberTvProportionalScale, every dimension expressed as (n * scale),
        val scale = rememberTvProportionalScale(maxHeight = maxHeight)

        val horizontalScreenPad = (44 * scale).dp
        val verticalScreenPad = (36 * scale).dp
        val columnGap = (40 * scale).dp
        val gap = (11 * scale).dp
        val qrSize = (maxHeight * 0.60f).coerceAtMost(maxWidth * 0.36f)
        val titleSize = (40 * scale).sp
        val bodySize = (21 * scale).sp
        val hintSize = (18 * scale).sp

        val continueFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            // The node is not attached yet on the frame the screen enters on:
            // requesting focus right away throws and leaves the D-pad dead
            // when returning here from the sign-in screen.
            withFrameNanos { }
            if (runCatching { continueFocusRequester.requestFocus() }.isFailure) {
                withFrameNanos { }
                runCatching { continueFocusRequester.requestFocus() }
            }
        }

        AuthScreenCard(
            scale = scale,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = horizontalScreenPad,
                    vertical = verticalScreenPad,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(columnGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuthQrPanel(
                    size = qrSize,
                    scale = scale,
                    footer = { GooglePlayBadge(scale) },
                    footerInPanel = true,
                ) {
                    QrCode(
                        data = GOOGLE_PLAY_URL,
                        modifier = Modifier
                            .padding(qrSize * 0.05f)
                            .fillMaxSize(),
                        contentDescription = stringResource(
                            R.string.mobile_install_qr_content_description,
                        ),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = accentedText(
                            full = stringResource(R.string.mobile_install_title),
                            accent = stringResource(R.string.mobile_install_title_accent),
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        lineHeight = titleSize * 1.12f,
                    )

                    Spacer(modifier = Modifier.height(gap))

                    Text(
                        text = stringResource(R.string.mobile_install_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = bodySize,
                        lineHeight = bodySize * 1.45f,
                    )

                    Spacer(modifier = Modifier.height(gap))

                    Text(
                        text = stringResource(R.string.mobile_install_choose_action),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = hintSize,
                        lineHeight = hintSize * 1.35f,
                    )

                    Spacer(modifier = Modifier.height(gap))

                    AuthActionButton(
                        text = stringResource(R.string.mobile_install_done),
                        scale = scale,
                        primary = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(continueFocusRequester),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        onClick = {
                            viewModel.completeOnboarding()
                            onContinue(PairingEntry.MOBILE_APP)
                        },
                    )

                    Spacer(modifier = Modifier.height((10 * scale).dp))

                    AuthActionButton(
                        text = stringResource(R.string.mobile_install_iphone),
                        scale = scale,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.PhoneIphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        onClick = {
                            viewModel.completeOnboarding()
                            onContinue(PairingEntry.IPHONE)
                        },
                    )

                    Spacer(modifier = Modifier.height((10 * scale).dp))

                    AuthActionButton(
                        text = stringResource(R.string.mobile_install_no_phone),
                        scale = scale,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        onClick = {
                            viewModel.completeOnboarding()
                            onContinue(PairingEntry.NO_PHONE)
                        },
                    )
                }
            }
        }
    }
}

/** Google Play mark and wordmark under the QR plate. */
@Composable
private fun GooglePlayBadge(scale: Float) {
    // Both lines must stay on one line each: wrapping the wordmark is what
    // made the badge look mangled inside the narrow panel.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((9 * scale).dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_play),
            contentDescription = stringResource(R.string.mobile_install_store_badge),
            modifier = Modifier.size((34 * scale).dp),
        )

        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.mobile_install_store_available_in),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = (15 * scale).sp,
                lineHeight = (17 * scale).sp,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = stringResource(R.string.mobile_install_store_badge),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = (23 * scale).sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = (25 * scale).sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
