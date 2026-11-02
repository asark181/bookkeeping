package com.bookkeeping.app.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.ui.common.formatCentsCompact
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi

data class CategorySlice(
    val category: String,
    val amountCents: Long,
    val color: Color,
    val percentage: Float,
    val count: Int
)

/** 分类环形图：入场扫描动画，选中扇区加粗 */
@Composable
fun DonutChart(
    slices: List<CategorySlice>,
    totalCents: Long,
    centerLabel: String,
    selected: String?,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 26.dp
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val track = Mi.colors.fill
    val selectedSlice = slices.firstOrNull { it.category == selected }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val base = strokeWidth.toPx()
            val inset = base * 0.75f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(base))
            var start = -90f
            val gap = if (slices.size > 1) 2f else 0f
            slices.forEach { s ->
                val sweep = s.percentage * 360f * progress.value
                if (sweep > gap) {
                    val isSel = s.category == selected
                    drawArc(
                        color = if (selected == null || isSel) s.color else s.color.copy(alpha = 0.35f),
                        startAngle = start + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(if (isSel) base * 1.35f else base, cap = StrokeCap.Butt)
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(selectedSlice?.category ?: centerLabel, fontSize = 13.sp, color = Mi.colors.textSecondary)
            Text(
                formatCentsCompact(selectedSlice?.amountCents ?: totalCents),
                style = AmountStyle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Mi.colors.textPrimary
            )
            if (selectedSlice != null) {
                Text(
                    String.format(java.util.Locale.US, "%.1f%%", selectedSlice.percentage * 100),
                    fontSize = 12.sp,
                    color = selectedSlice.color,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

data class BarPoint(val label: String, val tooltip: String, val valueCents: Long)

/** 趋势柱状图：点击柱子查看当期金额 */
@Composable
fun BarChart(
    points: List<BarPoint>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1
) {
    val c = Mi.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val max = (points.maxOfOrNull { it.valueCents } ?: 0L).coerceAtLeast(1L)
    val currentSelected by rememberUpdatedState(selectedIndex)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.isEmpty()) return@detectTapGestures
                        val slot = size.width / points.size.toFloat()
                        val idx = (offset.x / slot).toInt().coerceIn(0, points.lastIndex)
                        onSelect(if (idx == currentSelected) null else idx)
                    }
                }
        ) {
            if (points.isEmpty()) return@Canvas
            val slot = size.width / points.size
            val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            val chartH = size.height
            // 平均线
            val avg = points.sumOf { it.valueCents }.toFloat() / points.size
            val avgY = chartH - chartH * (avg / max) * 0.92f
            if (avg > 0) {
                var x = 0f
                while (x < size.width) {
                    drawLine(c.textTertiary, Offset(x, avgY), Offset(x + 6.dp.toPx(), avgY), strokeWidth = 1.dp.toPx())
                    x += 10.dp.toPx()
                }
            }
            points.forEachIndexed { i, p ->
                val h = if (p.valueCents == 0L) 3.dp.toPx() else (chartH * (p.valueCents.toFloat() / max) * 0.92f * progress.value).coerceAtLeast(3.dp.toPx())
                val left = slot * i + (slot - barW) / 2
                val active = selectedIndex == null || selectedIndex == i
                drawRoundRect(
                    color = if (p.valueCents == 0L) c.fill else if (active) color else color.copy(alpha = 0.3f),
                    topLeft = Offset(left, chartH - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2.4f, barW / 2.4f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, p ->
                Text(
                    if (i % labelEvery == 0 || i == points.lastIndex) p.label else "",
                    fontSize = 10.sp,
                    color = if (i == selectedIndex) c.primary else c.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
