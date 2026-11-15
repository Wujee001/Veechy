package com.lingxi.ai.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Purple = Color(0xFF7C5CFF)
val PurpleDeep = Color(0xFF5B3DF5)
val PurpleSoft = Color(0xFFEDE7FF)
val Blue = Color(0xFF4DA3FF)
val BlueSoft = Color(0xFFE4F0FF)
val Ink = Color(0xFF1E1B33)
val InkSoft = Color(0xFF6B6885)
val LineSoft = Color(0xFFEDEAF7)
val CardBg = Color(0xF5FFFFFF)

private val Scheme = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleSoft,
    onPrimaryContainer = PurpleDeep,
    secondary = Blue,
    onSecondary = Color.White,
    secondaryContainer = BlueSoft,
    onSecondaryContainer = Color(0xFF134C8D),
    background = Color(0xFFF8F6FF),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3F1FB),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFDED9F2),
    outlineVariant = LineSoft,
    error = Color(0xFFE5484D)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun LingXiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}

/**
 * 紫蓝白浅色渐变背景 + 两团柔光，全局统一。
 */
@Composable
fun GradientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFBF7FF),
                            Color(0xFFF0F4FF),
                            Color(0xFFFCFBFF)
                        )
                    )
                )
                val r = size.width * 1.05f
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Purple.copy(alpha = 0.17f), Color.Transparent),
                        center = Offset(size.width * 0.12f, -size.height * 0.02f),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(size.width * 0.12f, -size.height * 0.02f)
                )
                val r2 = size.width * 0.95f
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Blue.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(size.width * 1.02f, size.height * 0.22f),
                        radius = r2
                    ),
                    radius = r2,
                    center = Offset(size.width * 1.02f, size.height * 0.22f)
                )
            },
        content = content
    )
}
