package com.tobevpn.tv.presentation.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.tobevpn.tv.R
import com.tobevpn.tv.presentation.rememberTvScreenScale

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

        val leftScale = scale * 1.3f
        val horizontalPad = maxWidth * 0.05f
        val verticalPad = maxHeight * 0.03f
        val iconSize = min(maxHeight * 0.25f, maxWidth * 0.14f)
        val gap = maxHeight * 0.018f
        val colSpacing = maxWidth * 0.065f
        val brandingWidth = maxWidth * 0.325f
        val titleSize = (40 * leftScale).sp
        val subtitleSize = (18 * leftScale).sp
        val featureSize = (20 * scale).sp
        val buttonTextSize = (20 * scale).sp
        val btnPadH = (24 * scale).dp
        val btnPadV = (8 * scale).dp

        // Tight text style: remove font padding to eliminate extra space between Text composables
        val tightStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )

        Row(
            modifier = Modifier
                .padding(horizontal = horizontalPad, vertical = verticalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(colSpacing),
        ) {
            // Left — branding
            Column(
                modifier = Modifier.width(brandingWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.onboarding_logo),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )

                Spacer(modifier = Modifier.height(gap))

                Text(
                    text = "ToBeVPN",
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = tightStyle,
                )

                Spacer(modifier = Modifier.height(gap / 2))

                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    fontSize = subtitleSize,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tightStyle,
                )
            }

            // Right — features + button
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                FeatureItem(stringResource(R.string.onboarding_feature_pairing), featureSize, tightStyle)
                Spacer(modifier = Modifier.height(gap))
                FeatureItem(stringResource(R.string.onboarding_feature_devices), featureSize, tightStyle)
                Spacer(modifier = Modifier.height(gap))
                FeatureItem(stringResource(R.string.onboarding_feature_telegram), featureSize, tightStyle)
                Spacer(modifier = Modifier.height(gap))
                FeatureItem(stringResource(R.string.onboarding_feature_stars), featureSize, tightStyle)

                Spacer(modifier = Modifier.height(gap * 2f))

                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
                    shape = RoundedCornerShape((12 * scale).dp),
                    contentPadding = PaddingValues(horizontal = btnPadH, vertical = btnPadV),
                ) {
                    Text(
                        stringResource(R.string.onboarding_start),
                        fontSize = buttonTextSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(text: String, fontSize: androidx.compose.ui.unit.TextUnit, style: TextStyle) {
    Text(
        text = text,
        fontSize = fontSize,
        color = MaterialTheme.colorScheme.onSurface,
        style = style,
        lineHeight = fontSize * 1.1f,
    )
}
