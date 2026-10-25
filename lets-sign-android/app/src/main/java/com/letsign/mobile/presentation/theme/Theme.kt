package com.letsign.mobile.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFC2185B),
    primaryContainer = Color(0xFFFCE4EC),
    secondary = Color(0xFF3F51B5),
    secondaryContainer = Color(0xFFE8EAF6),
    onSecondaryContainer = Color(0xFF1A237E),
    tertiary = Color(0xFF009688),
    tertiaryContainer = Color(0xFFE0F2F1),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onSurface = Color(0xFF1D1D1D),
    onSurfaceVariant = Color(0xFF616161),
    onBackground = Color(0xFF1D1D1D),
    error = Color(0xFFD32F2F),
    errorContainer = Color(0xFFFFEBEE),
    onError = Color.White,
    onErrorContainer = Color(0xFFC62828)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF8BBD0),
    primaryContainer = Color(0xFF880E4F),
    secondary = Color(0xFFC5CAE9),
    secondaryContainer = Color(0xFF1A237E),
    onSecondaryContainer = Color(0xFFE8EAF6),
    tertiary = Color(0xFF80CBC4),
    tertiaryContainer = Color(0xFF004D40),
    surface = Color(0xFF1E1E1E),
    background = Color(0xFF121212),
    onPrimary = Color(0xFF1D1D1D),
    onSecondary = Color(0xFF1D1D1D),
    onTertiary = Color(0xFF1D1D1D),
    onSurface = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFFBDBDBD),
    onBackground = Color(0xFFE6E6E6),
    error = Color(0xFFEF5350),
    errorContainer = Color(0xFFB71C1C),
    onError = Color(0xFF1D1D1D),
    onErrorContainer = Color(0xFFFFFFFF)
)

/** Cores fixas de status (chips): saturadas o bastante para texto branco nos dois temas. */
object StatusColors {
    val Gray = Color(0xFF757575)
    val Amber = Color(0xFFB26A00)
    val Orange = Color(0xFFE65100)
    val Green = Color(0xFF2E7D32)
    val Red = Color(0xFFD32F2F)
    val Blue = Color(0xFF1976D2)
}

// Tamanhos padrão do Material 3; só display/headline/titleLarge ficam em negrito.
private val Base = Typography()
val Typography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold)
)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LetsSignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
