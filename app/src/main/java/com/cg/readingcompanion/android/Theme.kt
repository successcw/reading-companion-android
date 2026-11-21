package com.cg.readingcompanion.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cream = Color(0xFFF8EFD9)
val Ink = Color(0xFF3E2D22)
val Orange = Color(0xFFD9782E)

private val scheme = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFFFBF2),
    onSurface = Ink,
)

@Composable
fun ReadingCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}
