package com.tobevpn.tv.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.tobevpn.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders [data] as a scannable QR code. Shared by the pairing flow and the
 * block-appeal dialog so a TV user can scan a link with their phone instead of
 * trying to open it on a device with no browser.
 */
@Composable
fun QrCode(
    data: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "QR",
) {
    var bitmap by remember(data) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(data) { mutableStateOf(false) }

    LaunchedEffect(data) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val hints = mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 0,
                )
                val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 512, 512, hints)
                val w = matrix.width
                val h = matrix.height
                val pixels = IntArray(w * h) { i ->
                    if (matrix[i % w, i / w]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                }
                Bitmap.createBitmap(pixels, w, h, Bitmap.Config.RGB_565).asImageBitmap()
            }.getOrNull()
        }
        if (result != null) bitmap = result else error = true
    }

    when {
        error -> Text(stringResource(R.string.error_generic), color = Color.Red)
        bitmap == null -> CircularProgressIndicator(color = Color.Black)
        else -> {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
        }
    }
}
