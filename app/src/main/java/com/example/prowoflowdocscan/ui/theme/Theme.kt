package com.example.prowoflowdocscan.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AzureBlue,
    secondary = AzureBlue,
    tertiary = AzureBlue,
    background = DarkGrey,
    surface = DarkGrey,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = AzureBlue,
    secondary = AzureBlue,
    tertiary = AzureBlue,
    background = White,
    surface = LightGrey,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight
)

@Composable
fun ProWoFlowDocScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color off for minimalist look
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}