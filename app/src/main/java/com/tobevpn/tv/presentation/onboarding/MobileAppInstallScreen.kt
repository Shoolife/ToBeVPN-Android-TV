package com.tobevpn.tv.presentation.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tobevpn.tv.R
import com.tobevpn.tv.presentation.components.QrCode
import com.tobevpn.tv.presentation.rememberTvScreenScale

private const val GOOGLE_PLAY_URL =
    "https://play.google.com/store/apps/details?id=com.tobevpn.app"

@Composable
fun MobileAppInstallScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val scale = rememberTvScreenScale(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            largeScreenBaseline = 650f,
        )
        val screenPadding = maxOf(maxWidth * 0.03f, 16.dp)
        val columnSpacing = maxWidth * 0.04f
        val qrSize = min(maxHeight * 0.7f, maxWidth * 0.4f)
        val gap = maxHeight * 0.025f
        val titleSize = (36 * scale).sp
        val bodySize = (19 * scale).sp
        val hintSize = (16 * scale).sp
        val buttonTextSize = (19 * scale).sp
        val buttonCorner = (12 * scale).dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPadding),
            horizontalArrangement = Arrangement.spacedBy(columnSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(qrSize)
                    .clip(RoundedCornerShape(qrSize * 0.06f))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
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
                    text = stringResource(R.string.mobile_install_title),
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

                Spacer(modifier = Modifier.height(gap * 0.8f))

                Text(
                    text = stringResource(R.string.mobile_install_already_installed),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = hintSize,
                    lineHeight = hintSize * 1.35f,
                )

                Spacer(modifier = Modifier.height(gap * 1.4f))

                val focusRequester = remember { FocusRequester() }
                var isFocused by remember { mutableStateOf(false) }
                val buttonScale by animateFloatAsState(
                    targetValue = if (isFocused) 1.035f else 1f,
                    animationSpec = tween(durationMillis = 140),
                    label = "mobileInstallContinueScale",
                )
                LaunchedEffect(Unit) {
                    runCatching { focusRequester.requestFocus() }
                }

                Button(
                    onClick = {
                        viewModel.completeOnboarding()
                        onComplete()
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
                    shape = RoundedCornerShape(buttonCorner),
                    border = if (isFocused) {
                        BorderStroke(
                            width = (2 * scale).dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        null
                    },
                    contentPadding = PaddingValues(
                        horizontal = (26 * scale).dp,
                        vertical = (10 * scale).dp,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.mobile_install_continue),
                        fontSize = buttonTextSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
