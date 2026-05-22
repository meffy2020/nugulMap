package com.nugulmap.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NugulLightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    background = Color(0xFFF7F7F2),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE7E7DF),
    onSurfaceVariant = Color(0xFF5B5B55),
    error = Color(0xFFC62828),
)

@Composable
fun NugulMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NugulLightColors,
        content = content,
    )
}
