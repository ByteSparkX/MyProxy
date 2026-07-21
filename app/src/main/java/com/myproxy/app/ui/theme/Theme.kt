package com.myproxy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandCoral,
    background = AppBackgroundLight,
    onBackground = AppTextLight,
    surface = AppSurfaceLight,
    onSurface = AppTextLight,
    surfaceVariant = Color(0xFFF0F2F1),
    onSurfaceVariant = AppMutedLight,
    outline = Color(0xFFC8CECC),
    outlineVariant = AppDividerLight,
    error = BrandCoral,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF36CBBB),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF174A45),
    onPrimaryContainer = Color(0xFFC8FFF7),
    secondary = Color(0xFFFF7D72),
    background = AppBackgroundDark,
    onBackground = AppTextDark,
    surface = AppSurfaceDark,
    onSurface = AppTextDark,
    surfaceVariant = Color(0xFF232A29),
    onSurfaceVariant = AppMutedDark,
    outline = Color(0xFF56615F),
    outlineVariant = AppDividerDark,
    error = Color(0xFFFF7D72),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

@Composable
fun MyProxyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 使用系统深浅色设置，后续页面统一复用该主题入口。
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
