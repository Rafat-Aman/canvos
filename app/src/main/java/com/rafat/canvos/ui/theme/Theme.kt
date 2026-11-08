package com.rafat.canvos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.rafat.canvos.data.AppSettings
import com.rafat.canvos.data.ThemeMode

/** Canvas-inspired palette: deep blue brand with clean neutrals. */
val Brand = Color(0xFF0374B5)
val BrandDark = Color(0xFF6CB4F0)
val Success = Color(0xFF0B874B)
val Danger = Color(0xFFE0061F)
val Warning = Color(0xFFFC5E13)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8F7),
    onPrimaryContainer = Color(0xFF00304D),
    secondary = Color(0xFF394B58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8EC),
    onSecondaryContainer = Color(0xFF1C2A33),
    tertiary = Success,
    error = Danger,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1F24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1F24),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF5B6770),
    surfaceContainer = Color(0xFFF3F5F7),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECEFF2),
    outline = Color(0xFFC7CDD1),
    outlineVariant = Color(0xFFE3E7EA),
)

private val Dark = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF00304D),
    primaryContainer = Color(0xFF00476E),
    onPrimaryContainer = Color(0xFFD3E8F7),
    secondary = Color(0xFFB8C7D1),
    onSecondary = Color(0xFF22323C),
    secondaryContainer = Color(0xFF34444F),
    onSecondaryContainer = Color(0xFFDDE6EC),
    tertiary = Color(0xFF5FD39A),
    error = Color(0xFFFF8A8A),
    background = Color(0xFF111417),
    onBackground = Color(0xFFE3E6E9),
    surface = Color(0xFF171B1F),
    onSurface = Color(0xFFE3E6E9),
    surfaceVariant = Color(0xFF22282D),
    onSurfaceVariant = Color(0xFFA9B4BC),
    surfaceContainer = Color(0xFF1C2125),
    surfaceContainerLow = Color(0xFF171B1F),
    surfaceContainerHigh = Color(0xFF242A30),
    outline = Color(0xFF4A545B),
    outlineVariant = Color(0xFF30383E),
)

@Composable
fun isDark(settings: AppSettings): Boolean = when (settings.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CanvosTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val ctx = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
