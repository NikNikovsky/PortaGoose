package com.izk.portagoose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color // Crucial for Color.Black, Color.Red, Color.White



private val CustomDarkColorScheme = darkColorScheme(
    primary = Color.Black,
    onPrimary = Color.Red,
    surface = Color.Black,
    onSurface = Color.White,
    background = Color.Black,
    onBackground = Color.White
)

private val CustomLightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.Red,
    surface = Color.White,
    onSurface = Color.Black,
    background = Color.White,
    onBackground = Color.Black
)

@Composable
fun PortaGooseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme =

        CustomLightColorScheme
    // --- Comment out or remove the original dynamic color/dark theme logic below ---
    /*
    when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CustomDarkColorScheme
        else -> CustomLightColorScheme
    }
    */

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}