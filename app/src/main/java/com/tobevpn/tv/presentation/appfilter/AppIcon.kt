package com.tobevpn.tv.presentation.appfilter

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tobevpn.tv.data.InstalledAppsProvider

@Composable
fun AppIcon(
    packageName: String,
    label: String,
    provider: InstalledAppsProvider,
    size: Dp,
) {
    val density = LocalDensity.current
    val sizePx = remember(size, density) { with(density) { size.toPx().toInt().coerceAtLeast(1) } }
    val cache = remember { IconCache.instance }
    var bitmap by remember(packageName) { mutableStateOf(cache.get(packageName)) }

    LaunchedEffect(packageName, sizePx) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = provider.loadIcon(packageName, sizePx) ?: return@LaunchedEffect
        cache.put(packageName, loaded)
        bitmap = loaded
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = label,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

private class IconCache {
    private val map = LruCache<String, Bitmap>(256)

    fun get(packageName: String): Bitmap? = map.get(packageName)
    fun put(packageName: String, bitmap: Bitmap) {
        map.put(packageName, bitmap)
    }

    companion object {
        val instance = IconCache()
    }
}
