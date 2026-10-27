package com.bookkeeping.app.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.attachment.AttachmentStore
import com.bookkeeping.app.ui.theme.Mi
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

/** 关闭弹层：先播放退场动画，结束后执行 [after] 再通知外部移除 */
typealias Dismiss = (after: () -> Unit) -> Unit

/**
 * 全屏弹层基座：自绘遮罩与进退场动画，替代系统 Dialog/BottomSheet 的默认外观。
 */
@Composable
fun MiModal(
    onDismissRequest: () -> Unit,
    contentAlignment: Alignment = Alignment.BottomCenter,
    scrimColor: Color = Color.Black.copy(alpha = 0.38f),
    enter: EnterTransition = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(),
    exit: ExitTransition = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
    dismissOnScrim: Boolean = true,
    content: @Composable BoxScope.(dismiss: Dismiss) -> Unit
) {
    val visible = remember { MutableTransitionState(false) }
    var shown by remember { mutableStateOf(false) }
    var pendingAfter by remember { mutableStateOf<(() -> Unit)?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)

    LaunchedEffect(Unit) { visible.targetState = true }
    if (visible.currentState) shown = true
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (shown && visible.isIdle && !visible.currentState) {
            pendingAfter?.invoke()
            currentOnDismiss()
        }
    }
    val dismiss: Dismiss = { after ->
        pendingAfter = after
        visible.targetState = false
    }

    val host = LocalOverlayHost.current
    val entry = remember { OverlayEntry() }
    val body: @Composable () -> Unit = {
        BackHandler { dismiss {} }
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = dismissOnScrim
                        ) { dismiss {} }
                )
            }
            AnimatedVisibility(
                visibleState = visible,
                enter = enter,
                exit = exit,
                modifier = Modifier.align(contentAlignment)
            ) {
                Box { content(dismiss) }
            }
        }
    }
    SideEffect { entry.content = body }
    DisposableEffect(host) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** 弹层宿主：所有 MiModal 在应用根层同一窗口内绘制，系统栏与键盘内边距与页面一致 */
class OverlayHost {
    internal val entries = mutableStateListOf<OverlayEntry>()
}

internal class OverlayEntry {
    var content by mutableStateOf<@Composable () -> Unit>({})
}

val LocalOverlayHost = staticCompositionLocalOf { OverlayHost() }

@Composable
fun OverlayHostView(host: OverlayHost) {
    host.entries.forEach { entry -> key(entry) { entry.content() } }
}

/** HyperOS 风格底部面板：大圆角 + 拖拽条，可下拉关闭 */
@Composable
fun MiSheet(
    onDismissRequest: () -> Unit,
    title: String? = null,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(dismiss: Dismiss) -> Unit
) {
    MiModal(onDismissRequest = onDismissRequest) { dismiss ->
        val scope = rememberCoroutineScope()
        val offsetY = remember { Animatable(0f) }
        val thresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
        Column(
            modifier = Modifier
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Mi.colors.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragStopped = {
                            if (offsetY.value > thresholdPx) dismiss {} else offsetY.animateTo(0f, spring())
                        }
                    )
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Mi.colors.textTertiary)
                )
            }
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Mi.colors.textPrimary, modifier = Modifier.weight(1f))
                    titleAction?.invoke()
                }
            }
            content(dismiss)
        }
    }
}

/** HyperOS 风格对话框：底部悬浮大圆角卡片，双按钮并排 */
@Composable
fun MiDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    confirmStyle: MiButtonStyle = MiButtonStyle.Primary,
    onConfirm: () -> Unit = {},
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    MiModal(
        onDismissRequest = onDismissRequest,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 } + fadeIn(),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200))
    ) { dismiss ->
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Mi.colors.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Mi.colors.textPrimary, textAlign = TextAlign.Center)
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(message, fontSize = 15.sp, color = Mi.colors.textSecondary, textAlign = TextAlign.Center, lineHeight = 22.sp)
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth(), content = content)
            }
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dismissText != null) {
                    MiButton(dismissText, onClick = { dismiss {} }, style = MiButtonStyle.Secondary, modifier = Modifier.weight(1f))
                }
                MiButton(
                    confirmText,
                    onClick = { dismiss(onConfirm) },
                    style = if (confirmStyle == MiButtonStyle.Danger) MiButtonStyle.DangerFilled else confirmStyle,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ───────────────────────── 图片 ─────────────────────────

@Composable
fun rememberAttachmentBitmap(name: String, targetPx: Int): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, name, targetPx) {
        value = AttachmentStore.loadThumbnail(context, name, targetPx)
    }
    return bitmap
}

@Composable
fun AttachmentThumb(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    corner: Dp = 14.dp,
    onClick: () -> Unit = {},
    onRemove: (() -> Unit)? = null
) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = rememberAttachmentBitmap(name, px)
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(corner))
                .background(Mi.colors.fill)
                .miClickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = "图片备注", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Mi.colors.textTertiary, modifier = Modifier.size(22.dp))
            }
        }
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** 全屏图片查看：左右滑动、双指缩放、双击放大、单击关闭 */
@Composable
fun ImageViewer(images: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    MiModal(
        onDismissRequest = onDismiss,
        contentAlignment = Alignment.Center,
        scrimColor = Color.Black,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.92f)
    ) { dismiss ->
        val pager = rememberPagerState(initialPage = startIndex) { images.size }
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(images[page], onTap = { dismiss {} })
            }
            Text(
                "${pager.currentPage + 1} / ${images.size}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun ZoomableImage(name: String, onTap: () -> Unit) {
    val bitmap = rememberAttachmentBitmap(name, 1600)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero
                        } else scale = 2.5f
                    }
                )
            }
            .transformable(state, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }
            )
        }
    }
}

// ───────────────────────── 日期 / 月份选择 ─────────────────────────

private val WEEK_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 日历选择面板：保留原时间的时分，仅替换日期 */
@Composable
fun MiDatePickerSheet(initialMillis: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val initial = remember { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    var year by remember { mutableIntStateOf(initial.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(initial.get(Calendar.MONTH)) }
    val today = remember { Calendar.getInstance() }

    fun pick(dismiss: Dismiss, y: Int, m: Int, d: Int) {
        val c = (initial.clone() as Calendar).apply { set(y, m, d) }
        dismiss { onPick(c.timeInMillis) }
    }

    MiSheet(onDismissRequest = onDismiss, title = "选择日期") { dismiss ->
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("今天" to 0, "昨天" to -1, "前天" to -2).forEach { (label, delta) ->
                    MiChip(label, selected = false, onClick = {
                        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, delta) }
                        pick(dismiss, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${year}年${month + 1}月", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Mi.colors.textPrimary, modifier = Modifier.weight(1f))
                MiIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = {
                    if (month == 0) { month = 11; year-- } else month--
                }, background = Mi.colors.fill, size = 34.dp, iconSize = 20.dp)
                Spacer(Modifier.width(10.dp))
                MiIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, onClick = {
                    if (month == 11) { month = 0; year++ } else month++
                }, background = Mi.colors.fill, size = 34.dp, iconSize = 20.dp)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                WEEK_HEADERS.forEach {
                    Text(it, fontSize = 12.sp, color = Mi.colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            val first = Calendar.getInstance().apply { set(year, month, 1) }
            val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            val rows = (lead + days + 6) / 7
            for (r in 0 until rows) {
                Row {
                    for (col in 0 until 7) {
                        val day = r * 7 + col - lead + 1
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..days) {
                                val isSelected = year == initial.get(Calendar.YEAR) && month == initial.get(Calendar.MONTH) && day == initial.get(Calendar.DAY_OF_MONTH)
                                val isToday = year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH) && day == today.get(Calendar.DAY_OF_MONTH)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(if (isSelected) Mi.colors.primary else Color.Transparent)
                                        .miClickable { pick(dismiss, year, month, day) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$day",
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.White
                                            isToday -> Mi.colors.primary
                                            else -> Mi.colors.textPrimary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 年月选择面板 */
@Composable
fun MiMonthPickerSheet(year: Int, month: Int, onDismiss: () -> Unit, onPick: (year: Int, month: Int) -> Unit) {
    var y by remember { mutableIntStateOf(year) }
    MiSheet(onDismissRequest = onDismiss, title = "选择月份") { dismiss ->
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MiIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = { y-- }, background = Mi.colors.fill, size = 36.dp)
                Text("${y}年", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mi.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                MiIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, onClick = { y++ }, background = Mi.colors.fill, size = 36.dp)
            }
            Spacer(Modifier.height(14.dp))
            for (r in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0 until 4) {
                        val m = r * 4 + col
                        val selected = y == year && m == month
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Mi.colors.primary else Mi.colors.fill)
                                .miClickable { dismiss { onPick(y, m) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${m + 1}月",
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else Mi.colors.textPrimary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
