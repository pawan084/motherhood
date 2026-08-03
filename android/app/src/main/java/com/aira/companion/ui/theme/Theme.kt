package com.aira.companion.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** User appearance preference: system-follow by default, persisted next to
 * the device token. */
object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    var mode by mutableStateOf(SYSTEM)

    fun load(context: Context) {
        mode = context.getSharedPreferences("aira_net", Context.MODE_PRIVATE)
            .getString("appearance", SYSTEM) ?: SYSTEM
    }

    fun save(context: Context, value: String) {
        mode = value
        context.getSharedPreferences("aira_net", Context.MODE_PRIVATE)
            .edit().putString("appearance", value).apply()
    }
}

@Composable
fun AiraTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val dark = when (ThemeMode.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    // The whole palette flips here — every color getter reads this state.
    AiraPalette.dark = dark

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Ivory.toArgb()
            window.navigationBarColor = Ivory.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = Plum, onPrimary = Ivory,
            primaryContainer = Lilac, onPrimaryContainer = PlumDeep,
            secondary = SageDeep, onSecondary = Ivory,
            secondaryContainer = SageMist, onSecondaryContainer = SageDeep,
            tertiary = PlumSoft, onTertiary = Ivory,
            background = Ivory, onBackground = Ink,
            surface = Paper, onSurface = Ink,
            surfaceVariant = IvoryDeep, onSurfaceVariant = InkMuted,
            outline = OutlineSoft,
            error = Urgent, onError = Ivory,
            errorContainer = UrgentMist, onErrorContainer = Urgent,
        )
    } else {
        lightColorScheme(
            primary = Plum, onPrimary = Paper,
            primaryContainer = Lilac, onPrimaryContainer = PlumDeep,
            secondary = SageDeep, onSecondary = Paper,
            secondaryContainer = SageMist, onSecondaryContainer = SageDeep,
            tertiary = PlumSoft, onTertiary = Paper,
            background = Ivory, onBackground = Ink,
            surface = Paper, onSurface = Ink,
            surfaceVariant = IvoryDeep, onSurfaceVariant = InkMuted,
            outline = OutlineSoft,
            error = Urgent, onError = Paper,
            errorContainer = UrgentMist, onErrorContainer = Urgent,
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AiraTypography,
        content = content,
    )
}
