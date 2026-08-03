package com.aira.companion.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AiraLightColors =
    lightColorScheme(
        primary = Plum,
        onPrimary = Paper,
        primaryContainer = Lilac,
        onPrimaryContainer = PlumDeep,
        secondary = SageDeep,
        onSecondary = Paper,
        secondaryContainer = SageMist,
        onSecondaryContainer = SageDeep,
        tertiary = PlumSoft,
        onTertiary = Paper,
        background = Ivory,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = IvoryDeep,
        onSurfaceVariant = InkMuted,
        outline = OutlineSoft,
        error = Urgent,
        onError = Paper,
        errorContainer = UrgentMist,
        onErrorContainer = Urgent,
    )

@Composable
fun AiraTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Ivory.toArgb()
            window.navigationBarColor = Ivory.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = AiraLightColors,
        typography = AiraTypography,
        content = content,
    )
}
