package com.chacho.streetbite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CafePrincipal,
    secondary = CafeClaro,
    tertiary = CafeSecundario
)

private val LightColorScheme = lightColorScheme(
    primary = CafePrincipal,
    secondary = CafeClaro,
    tertiary = CafeSecundario,

    background = Fondo,
    surface = Blanco,
    onPrimary = Blanco,
    onSecondary = TextoPrincipal,
    onTertiary = Blanco,
    onBackground = TextoPrincipal,
    onSurface = TextoPrincipal
)

@Composable
fun StreetBiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {

    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}