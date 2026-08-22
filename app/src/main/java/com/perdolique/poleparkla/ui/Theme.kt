package com.perdolique.poleparkla.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.perdolique.poleparkla.R

object PoleParklaColors {
    val LightCanvas = Color(0xFFF3F5EF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightInk = Color(0xFF111713)
    val LightForest = Color(0xFF174B38)
    val LightSignal = Color(0xFFD8FF63)
    val LightMuted = Color(0xFF647069)
    val LightDanger = Color(0xFFD9564F)

    val DarkCanvas = Color(0xFF0C110E)
    val DarkSurface = Color(0xFF151C18)
    val DarkInk = Color(0xFFF3F6F1)
    val DarkForest = Color(0xFF76D6AA)
    val DarkSignal = Color(0xFFCFF45C)
    val DarkMuted = Color(0xFFA7B2AA)
    val DarkDanger = Color(0xFFFF8077)

    val CameraScrim = Color(0xB8070B09)
}

private val LightColors = lightColorScheme(
    primary = PoleParklaColors.LightForest,
    onPrimary = Color.White,
    primaryContainer = PoleParklaColors.LightSignal,
    onPrimaryContainer = PoleParklaColors.LightInk,
    secondary = PoleParklaColors.LightMuted,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EBE4),
    onSecondaryContainer = PoleParklaColors.LightInk,
    tertiary = Color(0xFF315E78),
    onTertiary = Color.White,
    error = PoleParklaColors.LightDanger,
    onError = Color.White,
    errorContainer = Color(0xFFFFE6E3),
    onErrorContainer = Color(0xFF5A1614),
    background = PoleParklaColors.LightCanvas,
    onBackground = PoleParklaColors.LightInk,
    surface = PoleParklaColors.LightSurface,
    onSurface = PoleParklaColors.LightInk,
    surfaceVariant = Color(0xFFE8ECE6),
    onSurfaceVariant = PoleParklaColors.LightMuted,
    outline = Color(0xFFC9D0C9),
    outlineVariant = Color(0xFFE0E5DF),
    scrim = Color(0xFF070B09),
)

private val DarkColors = darkColorScheme(
    primary = PoleParklaColors.DarkForest,
    onPrimary = Color(0xFF062C1E),
    primaryContainer = PoleParklaColors.DarkSignal,
    onPrimaryContainer = PoleParklaColors.LightInk,
    secondary = PoleParklaColors.DarkMuted,
    onSecondary = Color(0xFF1B241F),
    secondaryContainer = Color(0xFF263129),
    onSecondaryContainer = PoleParklaColors.DarkInk,
    tertiary = Color(0xFF95CBE4),
    onTertiary = Color(0xFF123646),
    error = PoleParklaColors.DarkDanger,
    onError = Color(0xFF4A0D0B),
    errorContainer = Color(0xFF5B2421),
    onErrorContainer = Color(0xFFFFDAD6),
    background = PoleParklaColors.DarkCanvas,
    onBackground = PoleParklaColors.DarkInk,
    surface = PoleParklaColors.DarkSurface,
    onSurface = PoleParklaColors.DarkInk,
    surfaceVariant = Color(0xFF202922),
    onSurfaceVariant = PoleParklaColors.DarkMuted,
    outline = Color(0xFF445149),
    outlineVariant = Color(0xFF2C3730),
    scrim = Color(0xFF070B09),
)

private val Inter = FontFamily(
    Font(R.font.inter_variable, weight = FontWeight.Normal),
    Font(R.font.inter_variable, weight = FontWeight.Medium),
    Font(R.font.inter_variable, weight = FontWeight.SemiBold),
    Font(R.font.inter_variable, weight = FontWeight.Bold),
)

private val PoleParklaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun PoleParklaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = PoleParklaTypography,
        shapes = Shapes(),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
