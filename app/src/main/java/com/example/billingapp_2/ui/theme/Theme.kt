package com.example.billingapp_2.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


// Define the light color scheme using the colors from Color.kt
private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

// Define the dark color scheme using the colors from Color.kt
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)
private val AppColorScheme = lightColorScheme(
    primary = AppColors.ButtonBlue,
    secondary = AppColors.ButtonGreen,
    tertiary = AppColors.ButtonOrange,
    background = AppColors.ScreenBackground,
    surface = AppColors.CardBackground,
    onPrimary = AppColors.WhiteText,
    onSecondary = AppColors.WhiteText,
    onTertiary = AppColors.WhiteText,
    onBackground = AppColors.PrimaryText,
    onSurface = AppColors.PrimaryText,
    error = AppColors.BalanceText,
    onError = AppColors.WhiteText,
    outline = AppColors.ButtonBorder
)
@Composable
fun BillingAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Determine theme based on system setting
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Use dynamic colors if available (Android 12+)
content: @Composable () -> Unit
)
{
    // Choose the appropriate color scheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Configure system UI (status bar and navigation bar) colors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set the status bar color based on the primary color of the chosen scheme
            window.statusBarColor = colorScheme.primary.toArgb()
            // Adjust system bar icons for visibility based on theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    // Apply the MaterialTheme with the chosen color scheme, typography, and shapes
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Defined in Type.kt, not shown here but assumed standard
        content = content
    )
    }