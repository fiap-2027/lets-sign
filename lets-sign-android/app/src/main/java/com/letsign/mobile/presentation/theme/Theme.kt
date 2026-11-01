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

// Paleta da versão web do EasySign: roxo (botões/abas), azul-marinho (sidebar), fundo #FAFAFC.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7F56F5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2EEFF),
    onPrimaryContainer = Color(0xFF3B2585),
    secondary = Color(0xFF3D3E80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEDFA),
    onSecondaryContainer = Color(0xFF24254E),
    tertiary = Color(0xFF2E9E6A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECF6ED),
    onTertiaryContainer = Color(0xFF1B5E3F),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF1F2133),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2133),
    surfaceVariant = Color(0xFFF3F3F7),
    onSurfaceVariant = Color(0xFF6B6E80),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFFD9DAE3),
    outlineVariant = Color(0xFFEDEDF2),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFC62828)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9A3FF),
    onPrimary = Color(0xFF2A1670),
    primaryContainer = Color(0xFF4A31A8),
    onPrimaryContainer = Color(0xFFF2EEFF),
    secondary = Color(0xFFC5C6F0),
    onSecondary = Color(0xFF24254E),
    secondaryContainer = Color(0xFF3D3E80),
    onSecondaryContainer = Color(0xFFECEDFA),
    tertiary = Color(0xFF7FD3A8),
    onTertiary = Color(0xFF0D3B26),
    tertiaryContainer = Color(0xFF1B5E3F),
    onTertiaryContainer = Color(0xFFECF6ED),
    background = Color(0xFF16172B),
    onBackground = Color(0xFFE6E6F0),
    surface = Color(0xFF1F2040),
    onSurface = Color(0xFFE6E6F0),
    surfaceVariant = Color(0xFF2A2B4F),
    onSurfaceVariant = Color(0xFFB4B6CC),
    surfaceContainerLowest = Color(0xFF1F2040),
    surfaceContainerLow = Color(0xFF1F2040),
    surfaceContainer = Color(0xFF1F2040),
    surfaceContainerHigh = Color(0xFF1F2040),
    surfaceContainerHighest = Color(0xFF1F2040),
    outline = Color(0xFF4A4C70),
    outlineVariant = Color(0xFF34365A),
    error = Color(0xFFEF5350),
    onError = Color(0xFF1D1D1D),
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = Color(0xFFFFFFFF)
)

/** Cores de status (texto/ícone dos chips) no tom da versão web; o fundo do chip é a mesma cor a 12%. */
object StatusColors {
    val Gray = Color(0xFF5F6470)
    val Amber = Color(0xFFB26A00)
    val Orange = Color(0xFFD9622B)
    val Green = Color(0xFF2F8F7A)
    val Red = Color(0xFFD32F2F)
    val Blue = Color(0xFF5B5FC7)
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
fun EasySignTheme(
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
