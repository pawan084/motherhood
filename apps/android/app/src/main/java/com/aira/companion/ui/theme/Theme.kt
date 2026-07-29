package com.aira.companion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
private fun materialScheme(c: AiraColors) =
    if (c.isDark) {
        darkColorScheme(
            primary = c.plum,
            onPrimary = c.plumDeep,
            primaryContainer = c.lilac,
            onPrimaryContainer = c.plumDeep,
            secondary = c.sageDeep,
            onSecondary = c.ivory,
            secondaryContainer = c.sageMist,
            onSecondaryContainer = c.sageDeep,
            tertiary = c.plumSoft,
            onTertiary = c.ivory,
            background = c.ivory,
            onBackground = c.ink,
            surface = c.paper,
            onSurface = c.ink,
            surfaceVariant = c.ivoryDeep,
            onSurfaceVariant = c.inkMuted,
            outline = c.outlineSoft,
            error = c.urgent,
            onError = c.ivory,
            errorContainer = c.urgentMist,
            onErrorContainer = c.urgent,
        )
    } else {
        lightColorScheme(
            primary = c.plum,
            onPrimary = c.paper,
            primaryContainer = c.lilac,
            onPrimaryContainer = c.plumDeep,
            secondary = c.sageDeep,
            onSecondary = c.paper,
            secondaryContainer = c.sageMist,
            onSecondaryContainer = c.sageDeep,
            tertiary = c.plumSoft,
            onTertiary = c.paper,
            background = c.ivory,
            onBackground = c.ink,
            surface = c.paper,
            onSurface = c.ink,
            surfaceVariant = c.ivoryDeep,
            onSurfaceVariant = c.inkMuted,
            outline = c.outlineSoft,
            error = c.urgent,
            onError = c.paper,
            errorContainer = c.urgentMist,
            onErrorContainer = c.urgent,
        )
    }

/**
 * Follows the system setting.
 *
 * No in-app toggle. Someone who has set their phone to dark has already
 * answered this question, and an app that asks again is one more decision on a
 * screen that exists to reduce them.
 */
@Composable
fun AiraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val colors = if (darkTheme) DarkAiraColors else LightAiraColors
    // Read inside composition; SideEffect's lambda is not composable, so the
    // bar colour has to be resolved before it.
    val systemBars = colors.ivory.toArgb()

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = systemBars
            window.navigationBarColor = systemBars
            WindowCompat.getInsetsController(window, view).apply {
                // Dark surfaces need light icons, or the clock disappears.
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAiraColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme(colors),
            typography = AiraTypography,
            content = content,
        )
    }
}
