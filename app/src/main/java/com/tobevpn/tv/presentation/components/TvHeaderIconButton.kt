package com.tobevpn.tv.presentation.components

import android.view.ViewConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvHeaderIconButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    borderWidth: Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var activationPressed by remember { mutableStateOf(false) }
    var longClickTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val longPressScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { longPressJob?.cancel() }
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val callback = onLongClick ?: return@onPreviewKeyEvent false
                val isActivationKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter
                if (!isActivationKey) return@onPreviewKeyEvent false

                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (!activationPressed) {
                            activationPressed = true
                            longClickTriggered = false
                            longPressJob?.cancel()
                            longPressJob = longPressScope.launch {
                                delay(ViewConfiguration.getLongPressTimeout().toLong())
                                if (activationPressed && !longClickTriggered) {
                                    longClickTriggered = true
                                    callback()
                                }
                            }
                        } else if (!longClickTriggered) {
                            // A repeated KeyDown is itself proof that the key
                            // is being held; do not wait for another frame.
                            longPressJob?.cancel()
                            longClickTriggered = true
                            callback()
                        }
                        longClickTriggered
                    }

                    KeyEventType.KeyUp -> {
                        val consume = longClickTriggered
                        activationPressed = false
                        longPressJob?.cancel()
                        longPressJob = null
                        consume
                    }

                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused },
        shape = shape,
        color = if (focused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        border = if (focused) {
            BorderStroke(borderWidth, MaterialTheme.colorScheme.onSurface)
        } else {
            null
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
