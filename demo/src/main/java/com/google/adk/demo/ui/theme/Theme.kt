package com.google.adk.demo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the app runs in a car-like context: wide / landscape windows
 * (anything above compact width) are treated as automotive displays and get
 * the dark anti-glare theme, enlarged typography and larger touch targets.
 * Compact (phone portrait) keeps the original phone look.
 */
val LocalIsCarUi = staticCompositionLocalOf { false }

/** Minimum touch target (dp) for primary interactive controls on the car UI. */
const val CarMinTouchTargetDp = 56

private val CarColors = darkColorScheme(
    primary = CarPrimary,
    onPrimary = CarOnPrimary,
    primaryContainer = CarPrimaryContainer,
    onPrimaryContainer = CarOnPrimaryContainer,
    secondary = CarSecondary,
    onSecondary = CarOnSecondary,
    secondaryContainer = CarSecondaryContainer,
    onSecondaryContainer = CarOnSecondaryContainer,
    tertiary = CarTertiary,
    onTertiary = CarOnTertiary,
    tertiaryContainer = CarTertiaryContainer,
    onTertiaryContainer = CarOnTertiaryContainer,
    error = CarError,
    onError = CarOnError,
    errorContainer = CarErrorContainer,
    onErrorContainer = CarOnErrorContainer,
    background = CarBackground,
    onBackground = CarOnBackground,
    surface = CarSurface,
    onSurface = CarOnSurface,
    surfaceVariant = CarSurfaceVariant,
    onSurfaceVariant = CarOnSurfaceVariant,
    surfaceContainerLowest = CarSurfaceContainerLowest,
    surfaceContainerLow = CarSurfaceContainerLow,
    surfaceContainer = CarSurfaceContainer,
    surfaceContainerHigh = CarSurfaceContainerHigh,
    surfaceContainerHighest = CarSurfaceContainerHighest,
    outline = CarOutline,
    outlineVariant = CarOutlineVariant,
    inverseSurface = CarInverseSurface,
    inverseOnSurface = CarInverseOnSurface,
    inversePrimary = CarInversePrimary,
    scrim = CarScrim,
)

private val PhoneColors = lightColorScheme(
    primary = PhonePrimary,
    onPrimary = PhoneOnPrimary,
    primaryContainer = PhonePrimaryContainer,
    onPrimaryContainer = PhoneOnPrimaryContainer,
    secondary = PhoneSecondary,
    onSecondary = PhoneOnSecondary,
    secondaryContainer = PhoneSecondaryContainer,
    onSecondaryContainer = PhoneOnSecondaryContainer,
    tertiary = PhoneTertiary,
    onTertiary = PhoneOnTertiary,
    tertiaryContainer = PhoneTertiaryContainer,
    onTertiaryContainer = PhoneOnTertiaryContainer,
    error = PhoneError,
    onError = PhoneOnError,
    errorContainer = PhoneErrorContainer,
    onErrorContainer = PhoneOnErrorContainer,
    background = PhoneBackground,
    onBackground = PhoneOnBackground,
    surface = PhoneSurface,
    onSurface = PhoneOnSurface,
    surfaceVariant = PhoneSurfaceVariant,
    onSurfaceVariant = PhoneOnSurfaceVariant,
    outline = PhoneOutline,
    inverseSurface = PhoneInverseSurface,
    inverseOnSurface = PhoneInverseOnSurface,
    inversePrimary = PhoneInversePrimary,
    scrim = PhoneScrim,
)

/**
 * Root theme of the ADK demo app.
 *
 * - Compact width (phone portrait): the original light phone look, unchanged.
 * - Medium / Expanded width (car head unit, landscape): deep-dark anti-glare
 *   scheme + enlarged typography, exposed via [LocalIsCarUi] so screens can
 *   also enlarge interactive touch targets.
 */
@Composable
fun AdkDemoTheme(
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    content: @Composable () -> Unit,
) {
    val isCarUi = windowWidthSizeClass != WindowWidthSizeClass.Compact
    // Preserve the historical behavior on phones (always light) so portrait
    // does not regress; dark palette is reserved for the automotive context.
    val colorScheme = if (isCarUi) CarColors else PhoneColors
    val typography = if (isCarUi) CarTypography else Typography()

    CompositionLocalProvider(LocalIsCarUi provides isCarUi) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
