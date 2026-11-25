package com.thousandlabs.willofd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette Will of D : bleu nuit Thousand Labs, or de la volonté. */
object Nuit {
    val Fond = Color(0xFF0B1530)
    val Surface = Color(0xFF111D3D)
    val SurfaceHaute = Color(0xFF1A2850)
    val SurfacePlusHaute = Color(0xFF223362)
    val Contour = Color(0xFF34477A)
    val Or = Color(0xFFE8B84A)
    val OrClair = Color(0xFFF6DC95)
    val Texte = Color(0xFFE6EAF4)
    val TexteDiscret = Color(0xFF9DA8C4)
    val Erreur = Color(0xFFE88577)
    val Prive = Color(0xFF2B1E4A)
}

private val Schema = darkColorScheme(
    primary = Nuit.Or,
    onPrimary = Color(0xFF2A1D00),
    primaryContainer = Color(0xFF4A3A10),
    onPrimaryContainer = Nuit.OrClair,
    secondary = Color(0xFF9FB4E8),
    onSecondary = Color(0xFF0E1A3A),
    secondaryContainer = Color(0xFF26386A),
    onSecondaryContainer = Color(0xFFDCE4FA),
    background = Nuit.Fond,
    onBackground = Nuit.Texte,
    surface = Nuit.Fond,
    onSurface = Nuit.Texte,
    surfaceVariant = Nuit.SurfaceHaute,
    onSurfaceVariant = Nuit.TexteDiscret,
    surfaceContainerLowest = Color(0xFF08102A),
    surfaceContainerLow = Nuit.Surface,
    surfaceContainer = Color(0xFF152245),
    surfaceContainerHigh = Nuit.SurfaceHaute,
    surfaceContainerHighest = Nuit.SurfacePlusHaute,
    outline = Nuit.Contour,
    outlineVariant = Color(0xFF26355E),
    error = Nuit.Erreur,
    onError = Color(0xFF2A0C08)
)

@Composable
fun ThemeWill(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Schema, typography = Typography(), content = content)
}
