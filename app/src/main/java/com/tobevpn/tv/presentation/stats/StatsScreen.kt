package com.tobevpn.tv.presentation.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.R
import com.tobevpn.tv.data.local.dao.TrafficStat
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.theme.VpnBlue
import com.tobevpn.tv.presentation.theme.VpnGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onLongBack: () -> Unit = onBack,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val totalBytes by viewModel.totalBytes.collectAsStateWithLifecycle()

    val totalSessions = remember(stats) { stats.sumOf { it.sessions } }
    val totalSeconds = remember(stats) { stats.sumOf { it.totalSeconds } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

        val screenPad = (40 * scale).dp
        val gap = (16 * scale).dp
        val smallGap = (8 * scale).dp
        val headlineSize = (26 * scale).sp
        val titleSize = (16 * scale).sp
        val bodySize = (14 * scale).sp
        val labelSize = (12 * scale).sp
        val displaySize = (28 * scale).sp
        val heroTitleSize = (18 * scale).sp
        val heroValueSize = (14 * scale).sp
        val heroLabelSize = (11 * scale).sp
        val heroIconSize = (18 * scale).dp
        val heroCircleSize = (42 * scale).dp
        val heroCircleIconSize = (24 * scale).dp
        val cardCorner = (24 * scale).dp
        val cardPad = (20 * scale).dp
        val rowCardCorner = (14 * scale).dp
        val rowCardPadH = (16 * scale).dp
        val rowCardPadV = (12 * scale).dp
        val rowPadV = (4 * scale).dp
        val chartHeight = (220 * scale).dp
        val chartCardCorner = (20 * scale).dp
        val chipSpacing = (8 * scale).dp
        val periodChipHeight = (34 * scale).dp
        val periodChipPadH = (14 * scale).dp
        val periodChipCorner = (10 * scale).dp
        val heroDividerWidth = (1 * scale).dp
        val heroDividerHeight = (36 * scale).dp
        val borderWidth = (2 * scale).dp
        val backCorner = (8 * scale).dp
        val progressBarHeight = (5 * scale).dp
        val progressBarCorner = (3 * scale).dp
        val headerButtonSize = (44 * scale).dp
        val headerIconSize = (20 * scale).dp
        val headerColor = MaterialTheme.colorScheme.onBackground

        val tightStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                    TvHeaderIconButton(
                        onClick = onBack,
                        onLongClick = onLongBack,
                        modifier = Modifier.size(headerButtonSize),
                        shape = RoundedCornerShape(backCorner),
                        borderWidth = borderWidth,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(headerIconSize),
                            tint = headerColor,
                        )
                    }
                Spacer(modifier = Modifier.width(gap))
                Text(
                    stringResource(R.string.stats_title),
                    fontSize = headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    style = tightStyle,
                )
            }

            Spacer(Modifier.height(smallGap))

            HeroStatsCard(
                totalBytes = totalBytes,
                totalSessions = totalSessions,
                totalSeconds = totalSeconds,
                scale = scale,
                cardCorner = cardCorner,
                cardPad = cardPad,
                heroTitleSize = heroTitleSize,
                displaySize = displaySize,
                heroValueSize = heroValueSize,
                heroLabelSize = heroLabelSize,
                heroIconSize = heroIconSize,
                heroCircleSize = heroCircleSize,
                heroCircleIconSize = heroCircleIconSize,
                heroDividerWidth = heroDividerWidth,
                heroDividerHeight = heroDividerHeight,
                tightStyle = tightStyle,
            )

            Spacer(Modifier.height(smallGap))

            // Period selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(chipSpacing),
            ) {
                StatsPeriod.entries.forEach { p ->
                    StatsPeriodChip(
                        selected = period == p,
                        onClick = { viewModel.setPeriod(p) },
                        text = when (p) {
                            StatsPeriod.DAY -> stringResource(R.string.stats_period_day)
                            StatsPeriod.WEEK -> stringResource(R.string.stats_period_week)
                            StatsPeriod.MONTH -> stringResource(R.string.stats_period_month)
                        },
                        height = periodChipHeight,
                        horizontalPadding = periodChipPadH,
                        corner = periodChipCorner,
                        borderWidth = borderWidth,
                        textSize = bodySize,
                        tightStyle = tightStyle,
                    )
                }
            }

            Spacer(Modifier.height(smallGap))

            if (stats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.stats_no_data),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = bodySize,
                        style = tightStyle,
                    )
                }
            } else {
                EnhancedTrafficChart(
                    stats = stats,
                    period = period,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .padding(vertical = rowPadV),
                    chartCardCorner = chartCardCorner,
                    scale = scale,
                )

                Text(
                    text = when (period) {
                        StatsPeriod.DAY -> stringResource(R.string.stats_today_by_hour)
                        StatsPeriod.WEEK -> stringResource(R.string.stats_week_by_day)
                        StatsPeriod.MONTH -> stringResource(R.string.stats_month_by_week)
                    },
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = rowPadV),
                    style = tightStyle,
                )

                val maxRowBytes = remember(stats) {
                    stats.maxOf { it.totalBytes }.coerceAtLeast(1L)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                ) {
                    items(stats) { stat ->
                        EnhancedStatRow(
                            stat = stat,
                            period = period,
                            maxBytes = maxRowBytes,
                            rowCardCorner = rowCardCorner,
                            rowCardPadH = rowCardPadH,
                            rowCardPadV = rowCardPadV,
                            rowPadV = rowPadV,
                            titleSize = titleSize,
                            bodySize = bodySize,
                            progressBarHeight = progressBarHeight,
                            progressBarCorner = progressBarCorner,
                            tightStyle = tightStyle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsPeriodChip(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    height: androidx.compose.ui.unit.Dp,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    borderWidth: androidx.compose.ui.unit.Dp,
    textSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)
    val containerColor = if (selected) {
        VpnBlue.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(containerColor)
            .then(
                if (isFocused) Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, shape)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = horizontalPadding),
            fontSize = textSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            style = tightStyle,
        )
    }
}

@Composable
private fun HeroStatsCard(
    totalBytes: Long,
    totalSessions: Int,
    totalSeconds: Long,
    scale: Float,
    cardCorner: androidx.compose.ui.unit.Dp,
    cardPad: androidx.compose.ui.unit.Dp,
    heroTitleSize: androidx.compose.ui.unit.TextUnit,
    displaySize: androidx.compose.ui.unit.TextUnit,
    heroValueSize: androidx.compose.ui.unit.TextUnit,
    heroLabelSize: androidx.compose.ui.unit.TextUnit,
    heroIconSize: androidx.compose.ui.unit.Dp,
    heroCircleSize: androidx.compose.ui.unit.Dp,
    heroCircleIconSize: androidx.compose.ui.unit.Dp,
    heroDividerWidth: androidx.compose.ui.unit.Dp,
    heroDividerHeight: androidx.compose.ui.unit.Dp,
    tightStyle: TextStyle,
) {
    val heroContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val heroLabelColor = heroContentColor.copy(alpha = 0.72f)
    val heroSecondaryLabelColor = heroContentColor.copy(alpha = 0.56f)
    val heroDividerColor = heroContentColor.copy(alpha = 0.16f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            VpnBlue.copy(alpha = 0.35f),
                            VpnGreen.copy(alpha = 0.18f),
                        ),
                    )
                )
                .padding(cardPad),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(heroCircleSize)
                            .clip(CircleShape)
                            .background(VpnBlue.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = heroContentColor,
                            modifier = Modifier.size(heroCircleIconSize),
                        )
                    }
                    Spacer(Modifier.width((12 * scale).dp))
                    Column {
                        Text(
                            stringResource(R.string.stats_total_used),
                            fontSize = heroLabelSize,
                            color = heroLabelColor,
                            style = tightStyle,
                        )
                        Text(
                            text = stringResource(R.string.stats_context_authenticated),
                            fontSize = heroLabelSize,
                            color = heroSecondaryLabelColor,
                            style = tightStyle,
                        )
                    }
                }
                Spacer(Modifier.height((12 * scale).dp))
                Text(
                    text = formatBytes(totalBytes),
                    fontSize = displaySize,
                    fontWeight = FontWeight.Bold,
                    color = heroContentColor,
                    style = tightStyle,
                )
                Spacer(Modifier.height((16 * scale).dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HeroMetric(
                        icon = Icons.Default.BarChart,
                        label = stringResource(R.string.stats_metric_sessions),
                        value = totalSessions.toString(),
                        heroIconSize = heroIconSize,
                        heroValueSize = heroValueSize,
                        heroLabelSize = heroLabelSize,
                        tightStyle = tightStyle,
                        contentColor = heroContentColor,
                        labelColor = heroSecondaryLabelColor,
                    )
                    HeroDivider(heroDividerWidth, heroDividerHeight, heroDividerColor)
                    HeroMetric(
                        icon = Icons.Default.Schedule,
                        label = stringResource(R.string.stats_metric_time),
                        value = formatTime(totalSeconds),
                        heroIconSize = heroIconSize,
                        heroValueSize = heroValueSize,
                        heroLabelSize = heroLabelSize,
                        tightStyle = tightStyle,
                        contentColor = heroContentColor,
                        labelColor = heroSecondaryLabelColor,
                    )
                    HeroDivider(heroDividerWidth, heroDividerHeight, heroDividerColor)
                    HeroMetric(
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        label = stringResource(R.string.stats_metric_avg),
                        value = formatBytes(
                            if (totalSessions > 0) totalBytes / totalSessions else 0
                        ),
                        heroIconSize = heroIconSize,
                        heroValueSize = heroValueSize,
                        heroLabelSize = heroLabelSize,
                        tightStyle = tightStyle,
                        contentColor = heroContentColor,
                        labelColor = heroSecondaryLabelColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    heroIconSize: androidx.compose.ui.unit.Dp,
    heroValueSize: androidx.compose.ui.unit.TextUnit,
    heroLabelSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
    contentColor: Color,
    labelColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(heroIconSize),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = heroValueSize,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            style = tightStyle,
        )
        Text(
            text = label,
            fontSize = heroLabelSize,
            color = labelColor,
            style = tightStyle,
        )
    }
}

@Composable
private fun HeroDivider(
    dividerWidth: androidx.compose.ui.unit.Dp,
    dividerHeight: androidx.compose.ui.unit.Dp,
    color: Color,
) {
    Box(
        modifier = Modifier
            .width(dividerWidth)
            .height(dividerHeight)
            .background(color),
    )
}

@Composable
private fun EnhancedTrafficChart(
    stats: List<TrafficStat>,
    period: StatsPeriod,
    modifier: Modifier = Modifier,
    chartCardCorner: androidx.compose.ui.unit.Dp,
    scale: Float,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Color.hashCode() is not an Android ARGB integer.
    val labelColorArgb = labelColor.toArgb()
    val scaleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val scaleColorArgb = scaleColor.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val weekPrefixFormat = stringResource(R.string.stats_week_short)

    val slots = buildSlots(stats, period)
    val maxBytes = slots.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
    val maxIndex = slots.indexOfFirst { it.totalBytes == maxBytes && maxBytes > 0 }

    val animProgress = remember(stats, period) { Animatable(0f) }
    LaunchedEffect(stats, period) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        )
    }
    val progress = animProgress.value

    val density = LocalDensity.current
    val yLabelWidthPx = with(density) { (36 * scale).dp.toPx() }
    val chartLabelTextSize = (9 * scale).dp

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(chartCardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = (16 * scale).dp,
                        start = (12 * scale).dp,
                        end = (16 * scale).dp,
                        bottom = (28 * scale).dp,
                    ),
            ) {
                if (slots.isEmpty()) return@Canvas

                val chartLeft = yLabelWidthPx
                val chartWidth = size.width - chartLeft
                val chartHeight = size.height
                val usableHeight = chartHeight * 0.88f

                val gridValues = listOf(0f, 0.5f, 1f)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                gridValues.forEach { frac ->
                    val y = chartHeight - usableHeight * frac
                    drawLine(
                        color = gridColor,
                        start = Offset(chartLeft, y),
                        end = Offset(size.width, y),
                        strokeWidth = (1 * scale).dp.toPx(),
                        pathEffect = if (frac == 0f) null else dashEffect,
                    )
                    val bytes = (maxBytes * frac).toLong()
                    val labelText = shortBytes(bytes)
                    drawScaleLabel(labelText, chartLeft - (6 * scale).dp.toPx(), y + (4 * scale).dp.toPx(), scaleColorArgb, chartLabelTextSize.toPx())
                }

                val barCount = slots.size
                val totalSpacing = chartWidth * 0.22f
                val spacing = totalSpacing / (barCount + 1)
                val barWidth = (chartWidth - totalSpacing) / barCount
                val corner = CornerRadius((4 * scale).dp.toPx())

                slots.forEachIndexed { index, slot ->
                    val frac = slot.totalBytes.toFloat() / maxBytes
                    val barHeight = frac * usableHeight * progress
                    val x = chartLeft + spacing + index * (barWidth + spacing)

                    if (barHeight > 0.5f) {
                        val isMax = index == maxIndex
                        val topColor = if (isMax) VpnGreen else VpnBlue
                        val bottomColor = if (isMax) {
                            VpnGreen.copy(alpha = 0.2f)
                        } else {
                            VpnBlue.copy(alpha = 0.18f)
                        }
                        val barTop = chartHeight - barHeight

                        drawRoundRect(
                            color = topColor.copy(alpha = 0.12f),
                            topLeft = Offset(x - (2 * scale).dp.toPx(), barTop - (2 * scale).dp.toPx()),
                            size = Size(barWidth + (4 * scale).dp.toPx(), barHeight + (2 * scale).dp.toPx()),
                            cornerRadius = CornerRadius((6 * scale).dp.toPx()),
                        )

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(topColor, bottomColor),
                                startY = barTop,
                                endY = chartHeight,
                            ),
                            topLeft = Offset(x, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner,
                        )

                        if (isMax && progress > 0.95f) {
                            drawRoundRect(
                                color = VpnGreen,
                                topLeft = Offset(x, barTop),
                                size = Size(barWidth, barHeight),
                                cornerRadius = corner,
                                style = Stroke(width = (1.5 * scale).dp.toPx()),
                            )
                        }
                    }

                    val label = slotLabel(slot, period, index, barCount, weekPrefixFormat)
                    if (label.isNotEmpty()) {
                        drawLabel(
                            label,
                            x + barWidth / 2,
                            chartHeight + (16 * scale).dp.toPx(),
                            labelColorArgb,
                            chartLabelTextSize.toPx(),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawScaleLabel(text: String, x: Float, y: Float, colorArgb: Int, textSize: Float) {
    val paint = android.graphics.Paint().apply {
        color = colorArgb
        this.textSize = textSize
        textAlign = android.graphics.Paint.Align.RIGHT
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun shortBytes(bytes: Long): String {
    return when {
        bytes <= 0 -> "0"
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}K"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}M"
        else -> "%.1fG".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun DrawScope.drawLabel(text: String, x: Float, y: Float, colorArgb: Int, textSize: Float) {
    val paint = android.graphics.Paint().apply {
        color = colorArgb
        this.textSize = textSize
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun buildSlots(stats: List<TrafficStat>, period: StatsPeriod): List<TrafficStat> {
    val cal = Calendar.getInstance(TimeZone.getDefault())

    return when (period) {
        StatsPeriod.DAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis / 1000
            val statsMap = stats.associateBy { it.period }
            (0 until 24).map { hour ->
                val slotTime = dayStart + hour * 3600L
                statsMap[slotTime] ?: TrafficStat(slotTime, 0, 0, 0)
            }
        }
        StatsPeriod.WEEK -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val weekStart = cal.timeInMillis / 1000
            val statsMap = stats.associateBy { it.period }
            (0 until 7).map { day ->
                val slotTime = weekStart + day * 86400L
                statsMap[slotTime] ?: TrafficStat(slotTime, 0, 0, 0)
            }
        }
        StatsPeriod.MONTH -> {
            if (stats.isEmpty()) {
                emptyList()
            } else {
                stats
            }
        }
    }
}

private fun slotLabel(
    slot: TrafficStat,
    period: StatsPeriod,
    index: Int,
    total: Int,
    weekPrefixFormat: String,
): String {
    return when (period) {
        StatsPeriod.DAY -> {
            // Show every 3rd hour
            // The slot index is the local hour. Deriving it from epoch
            // seconds prints UTC hours in non-UTC time zones.
            if (index % 3 == 0) "${index}:00" else ""
        }
        StatsPeriod.WEEK -> {
            val sdf = SimpleDateFormat("EE", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(Date(slot.period * 1000)).replaceFirstChar { it.uppercase() }
        }
        StatsPeriod.MONTH -> {
            weekPrefixFormat.format(index + 1)
        }
    }
}

@Composable
private fun EnhancedStatRow(
    stat: TrafficStat,
    period: StatsPeriod,
    maxBytes: Long,
    rowCardCorner: androidx.compose.ui.unit.Dp,
    rowCardPadH: androidx.compose.ui.unit.Dp,
    rowCardPadV: androidx.compose.ui.unit.Dp,
    rowPadV: androidx.compose.ui.unit.Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    progressBarHeight: androidx.compose.ui.unit.Dp,
    progressBarCorner: androidx.compose.ui.unit.Dp,
    tightStyle: TextStyle,
) {
    val fraction = (stat.totalBytes.toFloat() / maxBytes).coerceIn(0f, 1f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rowPadV),
        shape = RoundedCornerShape(rowCardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rowCardPadH, vertical = rowCardPadV),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatPeriodLabel(stat.period, period),
                        fontSize = titleSize,
                        fontWeight = FontWeight.Medium,
                        style = tightStyle,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.stats_sessions_short, stat.sessions, stat.sessions) +
                            "  •  " + formatTime(stat.totalSeconds),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
                Text(
                    text = formatBytes(stat.totalBytes),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = VpnGreen,
                    textAlign = TextAlign.End,
                    style = tightStyle,
                )
            }
            Spacer(Modifier.height(rowCardPadV))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressBarHeight)
                    .clip(RoundedCornerShape(progressBarCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(progressBarCorner))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(VpnBlue, VpnGreen),
                                )
                            ),
                    )
                }
            }
        }
    }
}

private fun formatPeriodLabel(epochSeconds: Long, period: StatsPeriod): String {
    val locale = Locale.getDefault()
    val sdf = when (period) {
        StatsPeriod.DAY -> SimpleDateFormat("HH:00", locale)
        StatsPeriod.WEEK -> SimpleDateFormat("EEEE, dd MMM", locale)
        StatsPeriod.MONTH -> SimpleDateFormat("dd MMM", locale)
    }
    sdf.timeZone = TimeZone.getDefault()
    val label = sdf.format(Date(epochSeconds * 1000))

    return when (period) {
        StatsPeriod.WEEK -> label.replaceFirstChar { it.uppercase() }
        StatsPeriod.MONTH -> {
            val endDate = SimpleDateFormat("dd MMM", locale).apply {
                timeZone = TimeZone.getDefault()
            }.format(Date((epochSeconds + 6 * 86400) * 1000))
            "$label — $endDate"
        }
        else -> label
    }
}

private fun formatBytes(bytes: Long): String {
    val isRu = Locale.getDefault().language == "ru"
    val kb = if (isRu) "КБ" else "KB"
    val mb = if (isRu) "МБ" else "MB"
    val gb = if (isRu) "ГБ" else "GB"
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f %s".format(bytes / 1024.0, kb)
        bytes < 1024 * 1024 * 1024 -> "%.1f %s".format(bytes / (1024.0 * 1024.0), mb)
        else -> "%.2f %s".format(bytes / (1024.0 * 1024.0 * 1024.0), gb)
    }
}

private fun formatTime(seconds: Long): String {
    val isRu = Locale.getDefault().language == "ru"
    val hUnit = if (isRu) "ч" else "h"
    val mUnit = if (isRu) "м" else "m"
    val sUnit = if (isRu) "с" else "s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}${hUnit} ${m}${mUnit}"
        m > 0 -> "${m}${mUnit}"
        else -> "${seconds}${sUnit}"
    }
}
