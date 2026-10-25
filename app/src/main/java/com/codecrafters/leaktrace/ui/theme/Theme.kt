package com.codecrafters.leaktrace.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Ink,
    secondary = InkSoft,
    onSecondary = Color.White,
    tertiary = Amber,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFF451A03),
    background = Mist,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Ink,
    primaryContainer = TealDark,
    onPrimaryContainer = TealContainer,
    secondary = Color(0xFFB6C4D9),
    onSecondary = Ink,
    tertiary = Color(0xFFFBBF24),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AmberContainer,
    background = Ink,
    onBackground = Color(0xFFE6EBF2),
    surface = Ink,
    onSurface = Color(0xFFE6EBF2),
)

/** Brand colours by default so LeakTrace looks the same everywhere; dynamic colour is opt-in. */
@Composable
fun LeakTraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
