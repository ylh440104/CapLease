package com.caplease.manager.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Slate900 = Color(0xFF0E1116)
private val Slate800 = Color(0xFF161B22)
private val Slate700 = Color(0xFF1F262E)
private val Slate600 = Color(0xFF2A323C)
private val Steel100 = Color(0xFFE6EDF3)
private val Steel300 = Color(0xFFB6C2CF)
private val Steel500 = Color(0xFF7D8896)
private val AccentBlue = Color(0xFF3B82F6)
private val AccentBlueDim = Color(0xFF1D4ED8)
private val AccentAmber = Color(0xFFD29922)
private val AccentRed = Color(0xFFDA3633)
private val AccentGreen = Color(0xFF2EA043)

private val DarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueDim,
    onPrimaryContainer = Steel100,
    secondary = Steel500,
    onSecondary = Slate900,
    tertiary = AccentAmber,
    onTertiary = Slate900,
    error = AccentRed,
    onError = Color.White,
    background = Slate900,
    onBackground = Steel100,
    surface = Slate800,
    onSurface = Steel100,
    surfaceVariant = Slate700,
    onSurfaceVariant = Steel300,
    outline = Slate600,
    outlineVariant = Slate700
)

private val LightScheme = lightColorScheme(
    primary = AccentBlueDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0B2545),
    secondary = Color(0xFF57606A),
    onSecondary = Color.White,
    tertiary = Color(0xFF9A6700),
    onTertiary = Color.White,
    error = AccentRed,
    onError = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEEF2),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFE4E8EC)
)

val CapabilityDanger: Color @Composable get() = if (isSystemInDarkTheme()) AccentAmber else Color(0xFF9A6700)
val CapabilitySafe: Color @Composable get() = if (isSystemInDarkTheme()) AccentGreen else Color(0xFF1A7F37)
val CapabilityCritical: Color @Composable get() = if (isSystemInDarkTheme()) AccentRed else Color(0xFFCF222E)

@Composable
fun CapLeaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}