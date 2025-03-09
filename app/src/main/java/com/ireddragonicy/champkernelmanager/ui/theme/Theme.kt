package com.ireddragonicy.champkernelmanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ireddragonicy.champkernelmanager.App

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun ChampKernelManagerTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as App }
    
    // Read from observable state
    val darkTheme by app.isDarkTheme
    val useDynamicColors by app.useDynamicColors
    
    val colorScheme = when {
        useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.let { scheme ->
        val customPrimaryColor = app.settings.customPrimaryColor
        if (customPrimaryColor != null && customPrimaryColor != -1) {
            scheme.copy(primary = androidx.compose.ui.graphics.Color(customPrimaryColor))
        } else {
            scheme
        }
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(darkTheme) {
            val window = (context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
            onDispose {}
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}