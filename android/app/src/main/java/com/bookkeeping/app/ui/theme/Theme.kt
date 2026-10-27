package com.bookkeeping.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * HyperOS 风格设计令牌：浅灰底 + 纯白大圆角卡片 + 小米蓝主色，深色模式纯黑底。
 */
@Immutable
data class MiColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    /** 输入框、分段控件、次级按钮的填充色 */
    val fill: Color,
    val pressed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val primary: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val expense: Color,
    val income: Color,
    val danger: Color,
    val warning: Color,
    val switchOff: Color,
    val heroStart: Color,
    val heroEnd: Color
)

val LightMiColors = MiColors(
    isDark = false,
    background = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    fill = Color(0xFFF0F1F4),
    pressed = Color(0x0F000000),
    textPrimary = Color(0xE6000000),
    textSecondary = Color(0x99000000),
    textTertiary = Color(0x4D000000),
    divider = Color(0x0F000000),
    primary = Color(0xFF3482FF),
    primarySoft = Color(0x1A3482FF),
    onPrimary = Color.White,
    expense = Color(0xE6000000),
    income = Color(0xFF16B47A),
    danger = Color(0xFFF2453D),
    warning = Color(0xFFFF9500),
    switchOff = Color(0xFFE2E3E8),
    heroStart = Color(0xFF3B7CFF),
    heroEnd = Color(0xFF6FA8FF)
)

val DarkMiColors = MiColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    fill = Color(0xFF2A2A2D),
    pressed = Color(0x14FFFFFF),
    textPrimary = Color(0xF2FFFFFF),
    textSecondary = Color(0x99FFFFFF),
    textTertiary = Color(0x4DFFFFFF),
    divider = Color(0x14FFFFFF),
    primary = Color(0xFF3E8BFF),
    primarySoft = Color(0x293E8BFF),
    onPrimary = Color.White,
    expense = Color(0xF2FFFFFF),
    income = Color(0xFF2CC98C),
    danger = Color(0xFFFF5A52),
    warning = Color(0xFFFF9F0A),
    switchOff = Color(0xFF3A3A3D),
    heroStart = Color(0xFF1F57D6),
    heroEnd = Color(0xFF3E7BEA)
)

val LocalMiColors = staticCompositionLocalOf { LightMiColors }

object Mi {
    val colors: MiColors
        @Composable get() = LocalMiColors.current
}

/** 金额数字统一使用等宽数字（MiSans 支持 tnum），保证小数点对齐 */
val AmountStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun BookkeepingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val mi = if (darkTheme) DarkMiColors else LightMiColors
    val scheme = if (darkTheme) {
        darkColorScheme(primary = mi.primary, background = mi.background, surface = mi.surface, onSurface = mi.textPrimary)
    } else {
        lightColorScheme(primary = mi.primary, background = mi.background, surface = mi.surface, onSurface = mi.textPrimary)
    }
    CompositionLocalProvider(LocalMiColors provides mi) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
