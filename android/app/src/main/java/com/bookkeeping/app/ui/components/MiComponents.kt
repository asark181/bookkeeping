package com.bookkeeping.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.ui.theme.Mi

// ───────────────────────── 交互修饰 ─────────────────────────

/** HyperOS 风格按压：弹性缩放，无水波纹 */
fun Modifier.miClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** 列表行按压：整行浅灰高亮 */
fun Modifier.miRowClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) Mi.colors.pressed else Color.Transparent, label = "rowPress")
    this
        .background(bg)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

// ───────────────────────── 按钮 ─────────────────────────

enum class MiButtonStyle { Primary, Secondary, Danger, DangerFilled, Soft }

@Composable
fun MiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MiButtonStyle = MiButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 50.dp
) {
    val c = Mi.colors
    val (bg, fg) = when (style) {
        MiButtonStyle.Primary -> c.primary to c.onPrimary
        MiButtonStyle.Secondary -> c.fill to c.textPrimary
        MiButtonStyle.Danger -> c.danger.copy(alpha = 0.1f) to c.danger
        MiButtonStyle.DangerFilled -> c.danger to Color.White
        MiButtonStyle.Soft -> c.primarySoft to c.primary
    }
    Box(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .miClickable(enabled = enabled && !loading, pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** 圆形图标按钮（顶部栏操作等） */
@Composable
fun MiIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Mi.colors.textPrimary,
    background: Color = Color.Transparent,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .miClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 胶囊标签（可选中） */
@Composable
fun MiChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val c = Mi.colors
    val bg by animateColorAsState(if (selected) c.primarySoft else c.fill, label = "chipBg")
    val fg by animateColorAsState(if (selected) c.primary else c.textPrimary, label = "chipFg")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .miClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

// ───────────────────────── 容器 ─────────────────────────

@Composable
fun MiCard(
    modifier: Modifier = Modifier,
    color: Color = Mi.colors.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(color),
        content = content
    )
}

/** 分组标题（卡片上方的小号灰字） */
@Composable
fun MiSectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, color = Mi.colors.textSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun MiDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    Box(
        modifier = modifier
            .padding(start = startIndent)
            .fillMaxWidth()
            .height(0.6.dp)
            .background(Mi.colors.divider)
    )
}

/** HyperOS 设置风格的圆角方形彩色图标 */
@Composable
fun MiIconBadge(icon: ImageVector, color: Color, size: Dp = 34.dp, iconSize: Dp = 19.dp, filled: Boolean = true) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(if (filled) color else color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else color, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun MiListItem(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = Mi.colors.primary,
    summary: String? = null,
    value: String? = null,
    showArrow: Boolean = true,
    titleColor: Color = Mi.colors.textPrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val c = Mi.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.miRowClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = if (summary != null) 14.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            MiIconBadge(icon, iconColor)
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = titleColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Spacer(Modifier.height(3.dp))
                Text(summary, fontSize = 12.sp, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 14.sp, color = c.textSecondary, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (showArrow && onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

// ───────────────────────── 开关 / 分段 ─────────────────────────

@Composable
fun MiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Mi.colors
    val track by animateColorAsState(if (checked) c.primary else c.switchOff, label = "switchTrack")
    val offset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "switchThumb"
    )
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** 分段控件：滑块带弹性动画 */
@Composable
fun MiSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    trackColor: Color = Mi.colors.fill,
    thumbColor: Color = if (Mi.colors.isDark) Color(0xFF48484C) else Color.White,
    selectedTextColor: Color = Mi.colors.textPrimary,
    textColor: Color = Mi.colors.textSecondary
) {
    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .padding(3.dp)
    ) {
        val itemWidth = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            label = "segOffset"
        )
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(itemWidth)
                .fillMaxHeight()
                .shadow(if (Mi.colors.isDark) 0.dp else 1.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(thumbColor)
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) selectedTextColor else textColor
                    )
                }
            }
        }
    }
}

// ───────────────────────── 输入框 ─────────────────────────

@Composable
fun MiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    minHeight: Dp = 52.dp,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp)
) {
    val c = Mi.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.primary),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.fill)
                    .heightIn(min = minHeight)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle, color = c.textTertiary, maxLines = if (singleLine) 1 else 3)
                    }
                    inner()
                }
            }
        }
    )
}

// ───────────────────────── 顶部栏 ─────────────────────────

/** 二级页面顶部栏：返回 + 居中标题 + 右侧操作 */
@Composable
fun MiTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp)
    ) {
        if (onBack != null) {
            MiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBackIos,
                onClick = onBack,
                iconSize = 18.dp,
                modifier = Modifier.align(Alignment.CenterStart),
                contentDescription = "返回"
            )
        }
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Mi.colors.textPrimary,
            modifier = Modifier.align(Alignment.Center)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = actions
        )
    }
}

/** 一级页面大标题（HyperOS 设置风格） */
@Composable
fun MiLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 14.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Mi.colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = Mi.colors.textSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
