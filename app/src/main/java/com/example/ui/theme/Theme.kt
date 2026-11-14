package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppAccent
import com.example.data.preferences.ThemeMode

@Immutable
data class RentStatusColors(
    val paid: Color,
    val paidContainer: Color,
    val partial: Color,
    val partialContainer: Color,
    val due: Color,
    val dueContainer: Color
)

@Immutable
data class AppThemeExtras(
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val heroSubtleText: Color,
    val heroAccent: Color
)

val LocalRentStatusColors = staticCompositionLocalOf {
    RentStatusColors(
        paid = PaidGreen,
        paidContainer = PaidGreenContainer,
        partial = PartialAmber,
        partialContainer = PartialAmberContainer,
        due = DueCoral,
        dueContainer = DueCoralContainer
    )
}

val LocalAppThemeExtras = staticCompositionLocalOf {
    AppThemeExtras(
        heroGradientStart = Color(0xFF2B3F9E),
        heroGradientEnd = Color(0xFF4C6EF5),
        heroSubtleText = Color(0xFFDBE4FF),
        heroAccent = Color(0xFFFBBF24)
    )
}

private fun lightSchemeFor(accent: AppAccent): ColorScheme = when (accent) {
    AppAccent.ROYAL_INDIGO -> lightColorScheme(
        primary = IndigoPrimary,
        onPrimary = Color.White,
        primaryContainer = IndigoPrimaryContainer,
        onPrimaryContainer = IndigoOnPrimaryContainer,
        secondary = IndigoSecondary,
        onSecondary = Color.White,
        secondaryContainer = IndigoSecondaryContainer,
        onSecondaryContainer = IndigoOnSecondaryContainer,
        tertiary = IndigoTertiary,
        onTertiary = Color.White,
        tertiaryContainer = IndigoTertiaryContainer,
        onTertiaryContainer = IndigoOnTertiaryContainer,
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        error = ErrorRed,
        onError = OnErrorRed,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight
    )
    AppAccent.EMERALD_MINT -> lightColorScheme(
        primary = EmeraldPrimary,
        onPrimary = Color.White,
        primaryContainer = EmeraldPrimaryContainer,
        onPrimaryContainer = EmeraldOnPrimaryContainer,
        secondary = EmeraldSecondary,
        onSecondary = Color.White,
        secondaryContainer = EmeraldSecondaryContainer,
        onSecondaryContainer = EmeraldOnSecondaryContainer,
        tertiary = IndigoTertiary,
        onTertiary = Color.White,
        tertiaryContainer = IndigoTertiaryContainer,
        onTertiaryContainer = IndigoOnTertiaryContainer,
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        error = ErrorRed,
        onError = OnErrorRed,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight
    )
    AppAccent.TERRACOTTA -> lightColorScheme(
        primary = TerracottaPrimary,
        onPrimary = Color.White,
        primaryContainer = TerracottaPrimaryContainer,
        onPrimaryContainer = TerracottaOnPrimaryContainer,
        secondary = TerracottaSecondary,
        onSecondary = Color.White,
        secondaryContainer = TerracottaSecondaryContainer,
        onSecondaryContainer = TerracottaOnSecondaryContainer,
        tertiary = IndigoTertiary,
        onTertiary = Color.White,
        tertiaryContainer = IndigoTertiaryContainer,
        onTertiaryContainer = IndigoOnTertiaryContainer,
        background = Color(0xFFFDF8F5),
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = Color(0xFFF5ECE6),
        onSurfaceVariant = LightOnSurfaceVariant,
        outline = Color(0xFFE2D4CA),
        outlineVariant = Color(0xFFEFE4DC),
        error = ErrorRed,
        onError = OnErrorRed,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight
    )
}

private fun darkSchemeFor(accent: AppAccent): ColorScheme = when (accent) {
    AppAccent.ROYAL_INDIGO -> darkColorScheme(
        primary = DarkIndigoPrimary,
        onPrimary = DarkIndigoOnPrimary,
        primaryContainer = DarkIndigoPrimaryContainer,
        onPrimaryContainer = DarkIndigoOnPrimaryContainer,
        secondary = PaidGreenDark,
        onSecondary = Color(0xFF022C22),
        secondaryContainer = PaidGreenContainerDark,
        onSecondaryContainer = PaidGreenContainer,
        tertiary = PartialAmberDark,
        onTertiary = Color(0xFF451A03),
        tertiaryContainer = PartialAmberContainerDark,
        onTertiaryContainer = PartialAmberContainer,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = DarkErrorRed,
        onError = DarkOnErrorRed,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer
    )
    AppAccent.EMERALD_MINT -> darkColorScheme(
        primary = DarkEmeraldPrimary,
        onPrimary = DarkEmeraldOnPrimary,
        primaryContainer = DarkEmeraldPrimaryContainer,
        onPrimaryContainer = DarkEmeraldOnPrimaryContainer,
        secondary = Color(0xFF38BDF8),
        onSecondary = Color(0xFF082F49),
        secondaryContainer = Color(0xFF0C4A6E),
        onSecondaryContainer = Color(0xFFE0F2FE),
        tertiary = PartialAmberDark,
        onTertiary = Color(0xFF451A03),
        tertiaryContainer = PartialAmberContainerDark,
        onTertiaryContainer = PartialAmberContainer,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = DarkErrorRed,
        onError = DarkOnErrorRed,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer
    )
    AppAccent.TERRACOTTA -> darkColorScheme(
        primary = DarkTerracottaPrimary,
        onPrimary = DarkTerracottaOnPrimary,
        primaryContainer = DarkTerracottaPrimaryContainer,
        onPrimaryContainer = DarkTerracottaOnPrimaryContainer,
        secondary = Color(0xFF2DD4BF),
        onSecondary = Color(0xFF042F2E),
        secondaryContainer = Color(0xFF134E4A),
        onSecondaryContainer = Color(0xFFCCFBF1),
        tertiary = PartialAmberDark,
        onTertiary = Color(0xFF451A03),
        tertiaryContainer = PartialAmberContainerDark,
        onTertiaryContainer = PartialAmberContainer,
        background = Color(0xFF140E0C),
        onBackground = DarkOnBackground,
        surface = Color(0xFF1F1613),
        onSurface = DarkOnSurface,
        surfaceVariant = Color(0xFF2C201B),
        onSurfaceVariant = Color(0xFFD6C3B8),
        outline = Color(0xFF4A372F),
        outlineVariant = Color(0xFF382822),
        error = DarkErrorRed,
        onError = DarkOnErrorRed,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer
    )
}

private fun extrasFor(accent: AppAccent, dark: Boolean): AppThemeExtras = when (accent) {
    AppAccent.ROYAL_INDIGO -> if (dark) {
        AppThemeExtras(
            heroGradientStart = Color(0xFF1E295E),
            heroGradientEnd = Color(0xFF364FC7),
            heroSubtleText = Color(0xFFDBE4FF),
            heroAccent = Color(0xFFFBBF24)
        )
    } else {
        AppThemeExtras(
            heroGradientStart = Color(0xFF2B3F9E),
            heroGradientEnd = Color(0xFF4C6EF5),
            heroSubtleText = Color(0xFFE0E7FF),
            heroAccent = Color(0xFFFDE047)
        )
    }
    AppAccent.EMERALD_MINT -> if (dark) {
        AppThemeExtras(
            heroGradientStart = Color(0xFF064E3B),
            heroGradientEnd = Color(0xFF059669),
            heroSubtleText = Color(0xFFD1FAE5),
            heroAccent = Color(0xFFFBBF24)
        )
    } else {
        AppThemeExtras(
            heroGradientStart = Color(0xFF047857),
            heroGradientEnd = Color(0xFF10B981),
            heroSubtleText = Color(0xFFD1FAE5),
            heroAccent = Color(0xFFFEF08A)
        )
    }
    AppAccent.TERRACOTTA -> if (dark) {
        AppThemeExtras(
            heroGradientStart = Color(0xFF6B220B),
            heroGradientEnd = Color(0xFFB43B12),
            heroSubtleText = Color(0xFFFFEDD5),
            heroAccent = Color(0xFFFCD34D)
        )
    } else {
        AppThemeExtras(
            heroGradientStart = Color(0xFF9A3412),
            heroGradientEnd = Color(0xFFEA580C),
            heroSubtleText = Color(0xFFFFEDD5),
            heroAccent = Color(0xFFFEF08A)
        )
    }
}

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RentLedgerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appAccent: AppAccent = AppAccent.ROYAL_INDIGO,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) darkSchemeFor(appAccent) else lightSchemeFor(appAccent)
    val extras = extrasFor(appAccent, darkTheme)
    val statusColors = if (darkTheme) {
        RentStatusColors(
            paid = PaidGreenDark,
            paidContainer = PaidGreenContainerDark,
            partial = PartialAmberDark,
            partialContainer = PartialAmberContainerDark,
            due = DueCoralDark,
            dueContainer = DueCoralContainerDark
        )
    } else {
        RentStatusColors(
            paid = PaidGreen,
            paidContainer = PaidGreenContainer,
            partial = PartialAmber,
            partialContainer = PartialAmberContainer,
            due = DueCoral,
            dueContainer = DueCoralContainer
        )
    }

    CompositionLocalProvider(
        LocalRentStatusColors provides statusColors,
        LocalAppThemeExtras provides extras
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
