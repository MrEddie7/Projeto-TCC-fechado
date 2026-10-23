package com.tcc.veiculotracker.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF07192B),
    primaryContainer = PrimaryLight,
    onPrimaryContainer = OnPrimary,
    secondary = Secondary,
    onSecondary = Color(0xFF052E16),
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = OnSecondary,
    tertiary = Tertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2A2450),
    onTertiaryContainer = Color(0xFFE4E0FF),
    background = DarkBackground,
    onBackground = OnBackground,
    surface = DarkSurface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color(0xFF0F131B),
    surfaceContainerLow = Color(0xFF171C26),
    surfaceContainer = Color(0xFF1B212C),
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF2A3343),
    outline = Divider,
    outlineVariant = Color(0xFF3A4353),
    error = Error,
    onError = OnError,
    errorContainer = Color(0xFF4A171B),
    onErrorContainer = Color(0xFFFECDD3)
)

@Composable
fun SecuritasTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // The prototypes use a fixed dark palette; keeping it fixed also prevents
    // Android 12 dynamic colours from changing the product identity.
    @Suppress("UNUSED_EXPRESSION")
    dynamicColor // mantido na assinatura por compatibilidade; paleta é fixa.
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
