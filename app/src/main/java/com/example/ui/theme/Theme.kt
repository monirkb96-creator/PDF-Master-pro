package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.core.i18n.StringsBn
import com.example.core.i18n.StringsEn
import com.example.core.i18n.ThemeMode

private val LightColorScheme = lightColorScheme(
  primary = CobaltPrimary,
  onPrimary = Color.White,
  primaryContainer = CobaltContainer,
  onPrimaryContainer = OnCobaltContainer,
  secondary = SkySecondary,
  onSecondary = Color.White,
  secondaryContainer = SkyContainer,
  onSecondaryContainer = OnSkyContainer,
  tertiary = IndigoTertiary,
  onTertiary = Color.White,
  tertiaryContainer = IndigoContainer,
  background = LightBackground,
  onBackground = LightOnSurface,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightOnSurfaceVariant,
  outline = LightOutline,
  error = CrimsonError,
  onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
  primary = DarkPrimary,
  onPrimary = DarkOnPrimary,
  primaryContainer = DarkPrimaryContainer,
  onPrimaryContainer = DarkOnPrimaryContainer,
  secondary = DarkSecondary,
  onSecondary = DarkOnPrimary,
  secondaryContainer = DarkSecondaryContainer,
  onSecondaryContainer = DarkOnSecondaryContainer,
  tertiary = IndigoContainer,
  background = DarkBackground,
  onBackground = DarkOnSurface,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  outline = DarkOutline,
  error = DarkCrimsonError,
  onError = DarkOnPrimary,
)

val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PdfMasterTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  language: AppLanguage = AppLanguage.ENGLISH,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
  val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
  val strings = if (language == AppLanguage.BANGLA) StringsBn else StringsEn
  val typography = appTypography(useBangla = language == AppLanguage.BANGLA)

  CompositionLocalProvider(LocalStrings provides strings) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = typography,
      shapes = AppShapes,
      content = content,
    )
  }
}
